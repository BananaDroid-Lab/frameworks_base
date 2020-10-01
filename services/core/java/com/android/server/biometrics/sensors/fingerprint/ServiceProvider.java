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

package com.android.server.biometrics.sensors.fingerprint;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.fingerprint.Fingerprint;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintSensorPropertiesInternal;
import android.hardware.fingerprint.IFingerprintServiceReceiver;
import android.hardware.fingerprint.IUdfpsOverlayController;
import android.os.IBinder;
import android.view.Surface;

import com.android.server.biometrics.sensors.ClientMonitorCallbackConverter;
import com.android.server.biometrics.sensors.LockoutTracker;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

/**
 * Superset of features/functionalities that HALs provide to the rest of the framework. This is
 * more or less mapped to the public and private APIs that {@link FingerprintManager} provide, and
 * is used at the system server layer to provide easy mapping between request and provider.
 *
 * Note that providers support both single-sensor and multi-sensor HALs. In either case,
 * {@link FingerprintService} must ensure that providers are only requested to perform operations
 * on sensors that they own.
 *
 * For methods other than {@link #containsSensor(int)}, the caller must ensure that the sensorId
 * passed in is supported by the provider. For example,
 * if (serviceProvider.containsSensor(sensorId)) {
 *     serviceProvider.operation(sensorId, ...);
 * }
 *
 * For operations that are supported by some providers but not others, clients are required
 * to check (e.g. via {@link FingerprintManager#getSensorPropertiesInternal()}) to ensure that the
 * code path isn't taken. ServiceProviders will provide a no-op for unsupported operations to
 * fail safely.
 */
@SuppressWarnings("deprecation")
public interface ServiceProvider {
    /**
     * Checks if the specified sensor is owned by this provider.
     */
    boolean containsSensor(int sensorId);

    @NonNull List<FingerprintSensorPropertiesInternal> getSensorProperties();

    void scheduleResetLockout(int sensorId, int userId, @Nullable byte[] hardwareAuthToken);

    void scheduleGenerateChallenge(int sensorId, @NonNull IBinder token,
            @NonNull IFingerprintServiceReceiver receiver, String opPackageName);

    void scheduleRevokeChallenge(int sensorId, @NonNull IBinder token,
            @NonNull String opPackageName);

    void scheduleEnroll(int sensorId, @NonNull IBinder token, byte[] hardwareAuthToken, int userId,
            @NonNull IFingerprintServiceReceiver receiver, @NonNull String opPackageName,
            @Nullable Surface surface);

    void cancelEnrollment(int sensorId, @NonNull IBinder token);

    void scheduleFingerDetect(int sensorId, @NonNull IBinder token, int userId,
            @NonNull ClientMonitorCallbackConverter callback, @NonNull String opPackageName,
            @Nullable Surface surface, int statsClient);

    void scheduleAuthenticate(int sensorId, @NonNull IBinder token, long operationId, int userId,
            int cookie, @NonNull ClientMonitorCallbackConverter callback,
            @NonNull String opPackageName, boolean restricted, int statsClient, boolean isKeyguard);

    void startPreparedClient(int sensorId, int cookie);

    void cancelAuthentication(int sensorId, @NonNull IBinder token);

    void scheduleRemove(int sensorId, @NonNull IBinder token,
            @NonNull IFingerprintServiceReceiver receiver, int fingerId, int userId,
            @NonNull String opPackageName);

    boolean isHardwareDetected(int sensorId);

    void rename(int sensorId, int fingerId, int userId, @NonNull String name);

    @NonNull List<Fingerprint> getEnrolledFingerprints(int sensorId, int userId);

    @LockoutTracker.LockoutMode int getLockoutModeForUser(int sensorId, int userId);

    long getAuthenticatorId(int sensorId, int userId);

    void onFingerDown(int sensorId, int x, int y, float minor, float major);

    void onFingerUp(int sensorId);

    void setUdfpsOverlayController(@NonNull IUdfpsOverlayController controller);

    void dumpProto(int sensorId, @NonNull FileDescriptor fd);

    void dumpInternal(int sensorId, @NonNull PrintWriter pw);
}
