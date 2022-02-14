/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.biometrics;

import static android.hardware.biometrics.BiometricAuthenticator.TYPE_CREDENTIAL;
import static android.hardware.biometrics.BiometricAuthenticator.TYPE_FACE;
import static android.hardware.biometrics.BiometricAuthenticator.TYPE_FINGERPRINT;
import static android.hardware.biometrics.BiometricAuthenticator.TYPE_IRIS;
import static android.hardware.biometrics.BiometricAuthenticator.TYPE_NONE;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.app.admin.DevicePolicyManager;
import android.app.trust.ITrustManager;
import android.content.Context;
import android.hardware.SensorPrivacyManager;
import android.hardware.biometrics.BiometricAuthenticator;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.PromptInfo;
import android.os.RemoteException;
import android.util.Pair;
import android.util.Slog;

import com.android.server.biometrics.sensors.LockoutTracker;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import com.android.internal.util.custom.faceunlock.FaceUnlockUtils;

/**
 * Class representing the calling client's request. Additionally, derives/calculates
 * preliminary info that would be useful in helping serve this request. Note that generating
 * the PreAuthInfo should not change any sensor state.
 */
class PreAuthInfo {
    private static final String TAG = "BiometricService/PreAuthInfo";

    static final int AUTHENTICATOR_OK = 1;
    static final int BIOMETRIC_NO_HARDWARE = 2;
    static final int BIOMETRIC_DISABLED_BY_DEVICE_POLICY = 3;
    static final int BIOMETRIC_INSUFFICIENT_STRENGTH = 4;
    static final int BIOMETRIC_INSUFFICIENT_STRENGTH_AFTER_DOWNGRADE = 5;
    static final int BIOMETRIC_HARDWARE_NOT_DETECTED = 6;
    static final int BIOMETRIC_NOT_ENROLLED = 7;
    static final int BIOMETRIC_NOT_ENABLED_FOR_APPS = 8;
    static final int CREDENTIAL_NOT_ENROLLED = 9;
    static final int BIOMETRIC_LOCKOUT_TIMED = 10;
    static final int BIOMETRIC_LOCKOUT_PERMANENT = 11;
    static final int BIOMETRIC_SENSOR_PRIVACY_ENABLED = 12;
    @IntDef({AUTHENTICATOR_OK,
            BIOMETRIC_NO_HARDWARE,
            BIOMETRIC_DISABLED_BY_DEVICE_POLICY,
            BIOMETRIC_INSUFFICIENT_STRENGTH,
            BIOMETRIC_INSUFFICIENT_STRENGTH_AFTER_DOWNGRADE,
            BIOMETRIC_HARDWARE_NOT_DETECTED,
            BIOMETRIC_NOT_ENROLLED,
            BIOMETRIC_NOT_ENABLED_FOR_APPS,
            CREDENTIAL_NOT_ENROLLED,
            BIOMETRIC_LOCKOUT_TIMED,
            BIOMETRIC_LOCKOUT_PERMANENT,
            BIOMETRIC_SENSOR_PRIVACY_ENABLED})
    @Retention(RetentionPolicy.SOURCE)
    @interface AuthenticatorStatus {}

    private final boolean mBiometricRequested;
    private final int mBiometricStrengthRequested;

    final boolean credentialRequested;
    // Sensors that can be used for this request (e.g. strong enough, enrolled, enabled).
    final List<BiometricSensor> eligibleSensors;
    // Sensors that cannot be used for this request. Pair<BiometricSensor, AuthenticatorStatus>
    final List<Pair<BiometricSensor, Integer>> ineligibleSensors;
    final boolean credentialAvailable;
    final boolean confirmationRequested;
    final boolean ignoreEnrollmentState;
    final int userId;
    final Context context;

    static PreAuthInfo create(ITrustManager trustManager,
            DevicePolicyManager devicePolicyManager,
            BiometricService.SettingObserver settingObserver,
            List<BiometricSensor> sensors,
            int userId, PromptInfo promptInfo, String opPackageName,
            boolean checkDevicePolicyManager, Context context)
            throws RemoteException {

        final boolean confirmationRequested = promptInfo.isConfirmationRequested();
        final boolean biometricRequested = Utils.isBiometricRequested(promptInfo);
        final int requestedStrength = Utils.getPublicBiometricStrength(promptInfo);
        final boolean credentialRequested = Utils.isCredentialRequested(promptInfo);

        final boolean credentialAvailable = trustManager.isDeviceSecure(userId,
                context.getAssociatedDisplayId());

        // Assuming that biometric authenticators are listed in priority-order, the rest of this
        // function will attempt to find the first authenticator that's as strong or stronger than
        // the requested strength, available, enrolled, and enabled. The tricky part is returning
        // the correct error. Error strings that are modality-specific should also respect the
        // priority-order.

        final List<BiometricSensor> eligibleSensors = new ArrayList<>();
        final List<Pair<BiometricSensor, Integer>> ineligibleSensors = new ArrayList<>();

        if (biometricRequested) {
            for (BiometricSensor sensor : sensors) {

                @AuthenticatorStatus int status = getStatusForBiometricAuthenticator(
                        devicePolicyManager, settingObserver, sensor, userId, opPackageName,
                        checkDevicePolicyManager, requestedStrength,
                        promptInfo.getAllowedSensorIds(),
                        promptInfo.isIgnoreEnrollmentState(),
                        context);

                Slog.d(TAG, "Package: " + opPackageName
                        + " Sensor ID: " + sensor.id
                        + " Modality: " + sensor.modality
                        + " Status: " + status);

                // A sensor with privacy enabled will still be eligible to
                // authenticate with biometric prompt. This is so the framework can display
                // a sensor privacy error message to users after briefly showing the
                // Biometric Prompt.
                //
                // Note: if only a certain sensor is required and the privacy is enabled,
                // canAuthenticate() will return false.
                if (status == AUTHENTICATOR_OK || status == BIOMETRIC_SENSOR_PRIVACY_ENABLED) {
                    eligibleSensors.add(sensor);
                } else {
                    ineligibleSensors.add(new Pair<>(sensor, status));
                }
            }
        }

        return new PreAuthInfo(biometricRequested, requestedStrength, credentialRequested,
                eligibleSensors, ineligibleSensors, credentialAvailable, confirmationRequested,
                promptInfo.isIgnoreEnrollmentState(), userId, context);
    }

    /**
     * Returns the status of the authenticator, with errors returned in a specific priority order.
     * For example, {@link #BIOMETRIC_INSUFFICIENT_STRENGTH_AFTER_DOWNGRADE} is only returned
     * if it has enrollments, and is enabled for apps.
     *
     * @return @AuthenticatorStatus
     */
    private static @AuthenticatorStatus int getStatusForBiometricAuthenticator(
            DevicePolicyManager devicePolicyManager,
            BiometricService.SettingObserver settingObserver,
            BiometricSensor sensor, int userId, String opPackageName,
            boolean checkDevicePolicyManager, int requestedStrength,
            @NonNull List<Integer> requestedSensorIds,
            boolean ignoreEnrollmentState, Context context) {

        if (!requestedSensorIds.isEmpty() && !requestedSensorIds.contains(sensor.id)) {
            return BIOMETRIC_NO_HARDWARE;
        }

        final boolean wasStrongEnough =
                Utils.isAtLeastStrength(sensor.oemStrength, requestedStrength);
        final boolean isStrongEnough =
                Utils.isAtLeastStrength(sensor.getCurrentStrength(), requestedStrength);

        if (wasStrongEnough && !isStrongEnough) {
            return BIOMETRIC_INSUFFICIENT_STRENGTH_AFTER_DOWNGRADE;
        } else if (!wasStrongEnough) {
            return BIOMETRIC_INSUFFICIENT_STRENGTH;
        }

        try {
            if (!sensor.impl.isHardwareDetected(opPackageName)) {
                return BIOMETRIC_HARDWARE_NOT_DETECTED;
            }

            if (!sensor.impl.hasEnrolledTemplates(userId, opPackageName)
                    && !ignoreEnrollmentState) {
                return BIOMETRIC_NOT_ENROLLED;
            }
            final SensorPrivacyManager sensorPrivacyManager = context
                    .getSystemService(SensorPrivacyManager.class);

            if (sensorPrivacyManager != null && sensor.modality == TYPE_FACE) {
                if (sensorPrivacyManager
                        .isSensorPrivacyEnabled(SensorPrivacyManager.Sensors.CAMERA, userId)) {
                    return BIOMETRIC_SENSOR_PRIVACY_ENABLED;
                }
            }


            final @LockoutTracker.LockoutMode int lockoutMode =
                    sensor.impl.getLockoutModeForUser(userId);
            if (lockoutMode == LockoutTracker.LOCKOUT_TIMED) {
                return BIOMETRIC_LOCKOUT_TIMED;
            } else if (lockoutMode == LockoutTracker.LOCKOUT_PERMANENT) {
                return BIOMETRIC_LOCKOUT_PERMANENT;
            }
        } catch (RemoteException e) {
            return BIOMETRIC_HARDWARE_NOT_DETECTED;
        }

        if (!isEnabledForApp(settingObserver, sensor.modality, userId)) {
            return BIOMETRIC_NOT_ENABLED_FOR_APPS;
        }

        if (checkDevicePolicyManager) {
            if (isBiometricDisabledByDevicePolicy(devicePolicyManager, sensor.modality, userId)) {
                return BIOMETRIC_DISABLED_BY_DEVICE_POLICY;
            }
        }

        return AUTHENTICATOR_OK;
    }

    private static boolean isEnabledForApp(BiometricService.SettingObserver settingObserver,
            @BiometricAuthenticator.Modality int modality, int userId) {
        if (modality == TYPE_FINGERPRINT && FaceUnlockUtils.isFaceUnlockSupported()){
            return true;
        }
        return settingObserver.getEnabledForApps(userId);
    }

    private static boolean isBiometricDisabledByDevicePolicy(
            DevicePolicyManager devicePolicyManager, @BiometricAuthenticator.Modality int modality,
            int effectiveUserId) {
        final int biometricToCheck = mapModalityToDevicePolicyType(modality);
        if (biometricToCheck == DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE) {
            throw new IllegalStateException("Modality unknown to devicePolicyManager: " + modality);
        }
        final int devicePolicyDisabledFeatures =
                devicePolicyManager.getKeyguardDisabledFeatures(null, effectiveUserId);
        final boolean isBiometricDisabled =
                (biometricToCheck & devicePolicyDisabledFeatures) != 0;
        Slog.w(TAG, "isBiometricDisabledByDevicePolicy(" + modality + "," + effectiveUserId
                + ")=" + isBiometricDisabled);
        return isBiometricDisabled;
    }

    /**
     * @param modality one of {@link BiometricAuthenticator#TYPE_FINGERPRINT},
     * {@link BiometricAuthenticator#TYPE_IRIS} or {@link BiometricAuthenticator#TYPE_FACE}
     * @return
     */
    private static int mapModalityToDevicePolicyType(int modality) {
        switch (modality) {
            case TYPE_FINGERPRINT:
                return DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT;
            case TYPE_IRIS:
                return DevicePolicyManager.KEYGUARD_DISABLE_IRIS;
            case TYPE_FACE:
                return DevicePolicyManager.KEYGUARD_DISABLE_FACE;
            default:
                Slog.e(TAG, "Error modality=" + modality);
                return DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE;
        }
    }

    private PreAuthInfo(boolean biometricRequested, int biometricStrengthRequested,
            boolean credentialRequested, List<BiometricSensor> eligibleSensors,
            List<Pair<BiometricSensor, Integer>> ineligibleSensors, boolean credentialAvailable,
            boolean confirmationRequested, boolean ignoreEnrollmentState, int userId,
            Context context) {
        mBiometricRequested = biometricRequested;
        mBiometricStrengthRequested = biometricStrengthRequested;
        this.credentialRequested = credentialRequested;

        this.eligibleSensors = eligibleSensors;
        this.ineligibleSensors = ineligibleSensors;
        this.credentialAvailable = credentialAvailable;
        this.confirmationRequested = confirmationRequested;
        this.ignoreEnrollmentState = ignoreEnrollmentState;
        this.userId = userId;
        this.context = context;
    }

    private Pair<BiometricSensor, Integer> calculateErrorByPriority() {
        // If the caller requested STRONG, and the device contains both STRONG and non-STRONG
        // sensors, prioritize BIOMETRIC_NOT_ENROLLED over the weak sensor's
        // BIOMETRIC_INSUFFICIENT_STRENGTH error. Pretty sure we can always prioritize
        // BIOMETRIC_NOT_ENROLLED over any other error (unless of course its calculation is
        // wrong, in which case we should fix that instead).
        for (Pair<BiometricSensor, Integer> pair : ineligibleSensors) {
            if (pair.second == BIOMETRIC_NOT_ENROLLED) {
                return pair;
            }
        }

        return ineligibleSensors.get(0);
    }

    /**
     * With {@link PreAuthInfo} generated with the requested authenticators from the public API
     * surface, combined with the actual sensor/credential and user/system settings, calculate the
     * internal {@link AuthenticatorStatus} that should be returned to the client. Note that this
     * will need to be converted into the public API constant.
     * @return Pair<Modality, Error> with error being the internal {@link AuthenticatorStatus} code
     */
    private Pair<Integer, Integer> getInternalStatus() {
        @AuthenticatorStatus final int status;
        @BiometricAuthenticator.Modality int modality = TYPE_NONE;

        final SensorPrivacyManager sensorPrivacyManager = context
                .getSystemService(SensorPrivacyManager.class);

        boolean cameraPrivacyEnabled = false;
        if (sensorPrivacyManager != null) {
            cameraPrivacyEnabled = sensorPrivacyManager
                    .isSensorPrivacyEnabled(SensorPrivacyManager.Sensors.CAMERA, userId);
        }

        if (mBiometricRequested && credentialRequested) {
            if (credentialAvailable || !eligibleSensors.isEmpty()) {
                for (BiometricSensor sensor : eligibleSensors) {
                    modality |= sensor.modality;
                }

                if (credentialAvailable) {
                    modality |= TYPE_CREDENTIAL;
                    status = AUTHENTICATOR_OK;
                } else if (modality == TYPE_FACE && cameraPrivacyEnabled) {
                    // If the only modality requested is face, credential is unavailable,
                    // and the face sensor privacy is enabled then return
                    // BIOMETRIC_SENSOR_PRIVACY_ENABLED.
                    //
                    // Note: This sensor will still be eligible for calls to authenticate.
                    status = BIOMETRIC_SENSOR_PRIVACY_ENABLED;
                } else {
                    status = AUTHENTICATOR_OK;
                }
            } else {
                // Pick the first sensor error if it exists
                if (!ineligibleSensors.isEmpty()) {
                    final Pair<BiometricSensor, Integer> pair = calculateErrorByPriority();
                    modality |= pair.first.modality;
                    status = pair.second;
                } else {
                    modality |= TYPE_CREDENTIAL;
                    status = CREDENTIAL_NOT_ENROLLED;
                }
            }
        } else if (mBiometricRequested) {
            if (!eligibleSensors.isEmpty()) {
                for (BiometricSensor sensor : eligibleSensors) {
                    modality |= sensor.modality;
                }
                if (modality == TYPE_FACE && cameraPrivacyEnabled) {
                    // If the only modality requested is face and the privacy is enabled
                    // then return BIOMETRIC_SENSOR_PRIVACY_ENABLED.
                    //
                    // Note: This sensor will still be eligible for calls to authenticate.
                    status = BIOMETRIC_SENSOR_PRIVACY_ENABLED;
                } else {
                    status = AUTHENTICATOR_OK;
                }
            } else {
                // Pick the first sensor error if it exists
                if (!ineligibleSensors.isEmpty()) {
                    final Pair<BiometricSensor, Integer> pair = calculateErrorByPriority();
                    modality |= pair.first.modality;
                    status = pair.second;
                } else {
                    modality |= TYPE_NONE;
                    status = BIOMETRIC_NO_HARDWARE;
                }
            }
        } else if (credentialRequested) {
            modality |= TYPE_CREDENTIAL;
            status = credentialAvailable ? AUTHENTICATOR_OK : CREDENTIAL_NOT_ENROLLED;
        } else {
            // This should not be possible via the public API surface and is here mainly for
            // "correctness". An exception should have been thrown before getting here.
            Slog.e(TAG, "No authenticators requested");
            status = BIOMETRIC_NO_HARDWARE;
        }
        Slog.d(TAG, "getCanAuthenticateInternal Modality: " + modality
                + " AuthenticatorStatus: " + status);

        return new Pair<>(modality, status);
    }

    /**
     * @return public BiometricManager result for the current request.
     */
    @BiometricManager.BiometricError int getCanAuthenticateResult() {
        // TODO: Convert this directly
        return Utils.biometricConstantsToBiometricManager(
                Utils.authenticatorStatusToBiometricConstant(
                        getInternalStatus().second));
    }

    /**
     * For the given request, generate the appropriate reason why authentication cannot be started.
     * Note that for some errors, modality is intentionally cleared.
     * @return Pair<Modality, Error> with modality being filtered if necessary, and error
     * being one of the public {@link android.hardware.biometrics.BiometricConstants} codes.
     */
    Pair<Integer, Integer> getPreAuthenticateStatus() {
        final Pair<Integer, Integer> internalStatus = getInternalStatus();

        final int publicError = Utils.authenticatorStatusToBiometricConstant(internalStatus.second);
        int modality = internalStatus.first;
        switch (internalStatus.second) {
            case AUTHENTICATOR_OK:
            case BIOMETRIC_NO_HARDWARE:
            case BIOMETRIC_INSUFFICIENT_STRENGTH_AFTER_DOWNGRADE:
            case BIOMETRIC_HARDWARE_NOT_DETECTED:
            case BIOMETRIC_NOT_ENROLLED:
            case CREDENTIAL_NOT_ENROLLED:
            case BIOMETRIC_LOCKOUT_TIMED:
            case BIOMETRIC_LOCKOUT_PERMANENT:
            case BIOMETRIC_SENSOR_PRIVACY_ENABLED:
                break;

            case BIOMETRIC_DISABLED_BY_DEVICE_POLICY:
            case BIOMETRIC_INSUFFICIENT_STRENGTH:
            case BIOMETRIC_NOT_ENABLED_FOR_APPS:
            default:
                modality = TYPE_NONE;
                break;
        }

        return new Pair<>(modality, publicError);
    }

    /**
     * @return true if SystemUI should show the credential UI.
     */
    boolean shouldShowCredential() {
        return credentialRequested && credentialAvailable;
    }

    /**
     * @return bitmask representing the modalities that are running or could be running for the
     * current session.
     */
    @BiometricAuthenticator.Modality int getEligibleModalities() {
        @BiometricAuthenticator.Modality int modalities = 0;
        for (BiometricSensor sensor : eligibleSensors) {
            modalities |= sensor.modality;
        }

        if (credentialRequested && credentialAvailable) {
            modalities |= TYPE_CREDENTIAL;
        }
        return modalities;
    }

    int numSensorsWaitingForCookie() {
        int numWaiting = 0;
        for (BiometricSensor sensor : eligibleSensors) {
            if (sensor.getSensorState() == BiometricSensor.STATE_WAITING_FOR_COOKIE) {
                Slog.d(TAG, "Sensor ID: " + sensor.id
                        + " Waiting for cookie: " + sensor.getCookie());
                numWaiting++;
            }
        }
        return numWaiting;
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder(
                "BiometricRequested: " + mBiometricRequested
                        + ", StrengthRequested: " + mBiometricStrengthRequested
                        + ", CredentialRequested: " + credentialRequested);
        string.append(", Eligible:{");
        for (BiometricSensor sensor: eligibleSensors) {
            string.append(sensor.id).append(" ");
        }
        string.append("}");

        string.append(", Ineligible:{");
        for (Pair<BiometricSensor, Integer> ineligible : ineligibleSensors) {
            string.append(ineligible.first).append(":").append(ineligible.second).append(" ");
        }
        string.append("}");

        string.append(", CredentialAvailable: ").append(credentialAvailable);
        string.append(", ");
        return string.toString();
    }
}
