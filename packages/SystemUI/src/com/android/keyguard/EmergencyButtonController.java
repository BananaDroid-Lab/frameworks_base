/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.keyguard;

import static com.android.systemui.DejankUtils.whitelistIpcs;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.dagger.KeyguardBouncerScope;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.shade.ShadeController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;
import com.android.systemui.user.domain.interactor.SelectedUserInteractor;
import com.android.systemui.util.EmergencyDialerConstants;
import com.android.systemui.util.ViewController;

import java.util.concurrent.Executor;

import javax.inject.Inject;

/** View Controller for {@link com.android.keyguard.EmergencyButton}. */
@KeyguardBouncerScope
public class EmergencyButtonController extends ViewController<EmergencyButton> {
    static final String LOG_TAG = "EmergencyButton";
    private final ConfigurationController mConfigurationController;
    private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private final TelephonyManager mTelephonyManager;
    private final PowerManager mPowerManager;
    private final ActivityTaskManager mActivityTaskManager;
    private ShadeController mShadeController;
    private final TelecomManager mTelecomManager;
    private final MetricsLogger mMetricsLogger;

    private EmergencyButtonCallback mEmergencyButtonCallback;
    private LockPatternUtils mLockPatternUtils;
    private Executor mMainExecutor;
    private Executor mBackgroundExecutor;
    private SelectedUserInteractor mSelectedUserInteractor;

    private final KeyguardUpdateMonitorCallback mInfoCallback =
            new KeyguardUpdateMonitorCallback() {
        @Override
        public void onSimStateChanged(int subId, int slotId, int simState) {
            updateEmergencyCallButton();
        }

        @Override
        public void onPhoneStateChanged(int phoneState) {
            updateEmergencyCallButton();
        }
    };

    private final ConfigurationListener mConfigurationListener = new ConfigurationListener() {
        @Override
        public void onConfigChanged(Configuration newConfig) {
            updateEmergencyCallButton();
        }
    };

    @VisibleForTesting
    public EmergencyButtonController(@Nullable EmergencyButton view,
            ConfigurationController configurationController,
            KeyguardUpdateMonitor keyguardUpdateMonitor, TelephonyManager telephonyManager,
            PowerManager powerManager, ActivityTaskManager activityTaskManager,
            ShadeController shadeController,
            @Nullable TelecomManager telecomManager, MetricsLogger metricsLogger,
            LockPatternUtils lockPatternUtils,
            Executor mainExecutor, Executor backgroundExecutor,
            SelectedUserInteractor selectedUserInteractor) {
        super(view);
        mConfigurationController = configurationController;
        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        mTelephonyManager = telephonyManager;
        mPowerManager = powerManager;
        mActivityTaskManager = activityTaskManager;
        mShadeController = shadeController;
        mTelecomManager = telecomManager;
        mMetricsLogger = metricsLogger;
        mLockPatternUtils = lockPatternUtils;
        mMainExecutor = mainExecutor;
        mBackgroundExecutor = backgroundExecutor;
        mSelectedUserInteractor = selectedUserInteractor;
    }

    @Override
    protected void onInit() {
        whitelistIpcs(this::updateEmergencyCallButton);
    }

    @Override
    protected void onViewAttached() {
        mKeyguardUpdateMonitor.registerCallback(mInfoCallback);
        mConfigurationController.addCallback(mConfigurationListener);
        mView.setOnClickListener(v -> takeEmergencyCallAction());
    }

    @Override
    protected void onViewDetached() {
        mKeyguardUpdateMonitor.removeCallback(mInfoCallback);
        mConfigurationController.removeCallback(mConfigurationListener);
    }

    /**
     * Updates the visibility of the emergency button.
     *
     * This method runs binder calls in a background thread.
     */
    @VisibleForTesting
    @SuppressLint("MissingPermission")
    public void updateEmergencyCallButton() {
        if (mView != null) {
            // Run in bg thread to avoid throttling the main thread with binder call.
            mBackgroundExecutor.execute(() -> {
                boolean isInCall = mTelecomManager != null && mTelecomManager.isInCall();
                boolean isSecure = mLockPatternUtils
                        .isSecure(mSelectedUserInteractor.getSelectedUserId());
                mMainExecutor.execute(() -> mView.updateEmergencyCallButton(
                        /* isInCall= */ isInCall,
                        /* hasTelephonyRadio= */ getContext().getPackageManager()
                                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
                        /* simLocked= */ mKeyguardUpdateMonitor.isSimPinVoiceSecure(),
                        /* isSecure= */ isSecure));
            });
        }
    }

    public void setEmergencyButtonCallback(EmergencyButtonCallback callback) {
        mEmergencyButtonCallback = callback;
    }
    /**
     * Shows the emergency dialer or returns the user to the existing call.
     */
    @SuppressLint("MissingPermission")
    public void takeEmergencyCallAction() {
        mMetricsLogger.action(MetricsEvent.ACTION_EMERGENCY_CALL);
        if (mPowerManager != null) {
            mPowerManager.userActivity(SystemClock.uptimeMillis(), true);
        }
        mActivityTaskManager.stopSystemLockTaskMode();
        mShadeController.collapseShade(false);
        // Run in bg thread to avoid throttling the main thread with binder call.
        mBackgroundExecutor.execute(() -> {
            boolean isInCall = mTelecomManager != null && mTelecomManager.isInCall();
            mMainExecutor.execute(() -> {
                if (isInCall) {
                    mTelecomManager.showInCallScreen(false);
                    if (mEmergencyButtonCallback != null) {
                        mEmergencyButtonCallback.onEmergencyButtonClickedWhenInCall();
                    }
                } else {
                    mKeyguardUpdateMonitor.reportEmergencyCallAction(true /* bypassHandler */);
                    if (mTelecomManager == null) {
                        Log.wtf(LOG_TAG, "TelecomManager was null, cannot launch emergency dialer");
                        return;
                    }
                    Intent emergencyDialIntent =
                            mTelecomManager.createLaunchEmergencyDialerIntent(null /* number*/)
                                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                                            | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    .putExtra(EmergencyDialerConstants.EXTRA_ENTRY_TYPE,
                                            EmergencyDialerConstants.ENTRY_TYPE_LOCKSCREEN_BUTTON);

                    getContext().startActivityAsUser(emergencyDialIntent,
                            ActivityOptions.makeCustomAnimation(getContext(), 0, 0).toBundle(),
                            new UserHandle(mSelectedUserInteractor.getSelectedUserId()));
                }
            });
        });
    }

    /** */
    public interface EmergencyButtonCallback {
        /** */
        void onEmergencyButtonClickedWhenInCall();
    }

    /** Injectable Factory for creating {@link EmergencyButtonController}. */
    public static class Factory {
        private final ConfigurationController mConfigurationController;
        private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
        private final TelephonyManager mTelephonyManager;
        private final PowerManager mPowerManager;
        private final ActivityTaskManager mActivityTaskManager;
        private ShadeController mShadeController;
        @Nullable
        private final TelecomManager mTelecomManager;
        private final MetricsLogger mMetricsLogger;
        private final LockPatternUtils mLockPatternUtils;
        private final Executor mMainExecutor;
        private final Executor mBackgroundExecutor;
        private final SelectedUserInteractor mSelectedUserInteractor;

        @Inject
        public Factory(ConfigurationController configurationController,
                KeyguardUpdateMonitor keyguardUpdateMonitor, TelephonyManager telephonyManager,
                PowerManager powerManager, ActivityTaskManager activityTaskManager,
                ShadeController shadeController,
                @Nullable TelecomManager telecomManager, MetricsLogger metricsLogger,
                LockPatternUtils lockPatternUtils,
                @Main Executor mainExecutor,
                @Background Executor backgroundExecutor,
                SelectedUserInteractor selectedUserInteractor) {

            mConfigurationController = configurationController;
            mKeyguardUpdateMonitor = keyguardUpdateMonitor;
            mTelephonyManager = telephonyManager;
            mPowerManager = powerManager;
            mActivityTaskManager = activityTaskManager;
            mShadeController = shadeController;
            mTelecomManager = telecomManager;
            mMetricsLogger = metricsLogger;
            mLockPatternUtils = lockPatternUtils;
            mMainExecutor = mainExecutor;
            mBackgroundExecutor = backgroundExecutor;
            mSelectedUserInteractor = selectedUserInteractor;
        }

        /** Construct an {@link com.android.keyguard.EmergencyButtonController}. */
        public EmergencyButtonController create(EmergencyButton view) {
            return new EmergencyButtonController(view, mConfigurationController,
                    mKeyguardUpdateMonitor, mTelephonyManager, mPowerManager, mActivityTaskManager,
                    mShadeController, mTelecomManager, mMetricsLogger, mLockPatternUtils,
                    mMainExecutor, mBackgroundExecutor, mSelectedUserInteractor);
        }
    }
}
