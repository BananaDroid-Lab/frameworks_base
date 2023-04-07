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

package com.android.keyguard;

import static com.android.keyguard.KeyguardAbsKeyInputView.MINIMUM_PASSWORD_LENGTH_BEFORE_REPORT;

import android.os.AsyncTask;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.internal.util.LatencyTracker;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternUtils.RequestThrottledException;
import com.android.internal.widget.LockscreenCredential;
import com.android.keyguard.PasswordTextView.QuickUnlockListener;
import com.android.keyguard.KeyguardSecurityModel.SecurityMode;
import com.android.systemui.R;
import com.android.systemui.classifier.FalsingCollector;
import com.android.systemui.statusbar.policy.DevicePostureController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class KeyguardPinViewController
        extends KeyguardPinBasedInputViewController<KeyguardPINView> {
    private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private final DevicePostureController mPostureController;
    private final DevicePostureController.Callback mPostureCallback = posture ->
            mView.onDevicePostureChanged(posture);

    private int userId = KeyguardUpdateMonitor.getCurrentUser();

    private LockPatternUtils mLockPatternUtils;

    private KeyguardSecurityCallback mKeyguardSecurityCallback;

    private boolean mScramblePin;

    private List<Integer> mNumbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 0);
    private final List<Integer> mDefaultNumbers = List.of(mNumbers.toArray(new Integer[0]));

    protected KeyguardPinViewController(KeyguardPINView view,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            SecurityMode securityMode, LockPatternUtils lockPatternUtils,
            KeyguardSecurityCallback keyguardSecurityCallback,
            KeyguardMessageAreaController.Factory messageAreaControllerFactory,
            LatencyTracker latencyTracker, LiftToActivateListener liftToActivateListener,
            EmergencyButtonController emergencyButtonController,
            FalsingCollector falsingCollector,
            DevicePostureController postureController) {
        super(view, keyguardUpdateMonitor, securityMode, lockPatternUtils, keyguardSecurityCallback,
                messageAreaControllerFactory, latencyTracker, liftToActivateListener,
                emergencyButtonController, falsingCollector);
        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        mPostureController = postureController;
        mLockPatternUtils = lockPatternUtils;
        mKeyguardSecurityCallback = keyguardSecurityCallback;
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();

        View cancelBtn = mView.findViewById(R.id.cancel_button);
        if (cancelBtn != null) {
            cancelBtn.setOnClickListener(view -> {
                mKeyguardSecurityCallback.reset();
                mKeyguardSecurityCallback.onCancelClicked();
            });
        }
        mPostureController.addCallback(mPostureCallback);
    }

    private void updatePinScrambling() {
        boolean scramblePin = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.LOCKSCREEN_PIN_SCRAMBLE_LAYOUT, 0,
                UserHandle.USER_CURRENT) == 1;

        if (scramblePin || scramblePin != mScramblePin) {
            mScramblePin = scramblePin;
            if (scramblePin) {
                Collections.shuffle(mNumbers);
            } else {
                mNumbers = new ArrayList<>(mDefaultNumbers);
            }

            // get all children who are NumPadKey's
            ConstraintLayout container = (ConstraintLayout) mView.findViewById(R.id.pin_container);

            List<NumPadKey> views = new ArrayList<NumPadKey>();
            for (int i = 0; i < container.getChildCount(); i++) {
                View view = container.getChildAt(i);
                if (view.getClass() == NumPadKey.class) {
                    views.add((NumPadKey) view);
                }
            }

            // reset the digits in the views
            for (int i = 0; i < mNumbers.size(); i++) {
                NumPadKey view = views.get(i);
                view.setDigit(mNumbers.get(i));
            }
        }
    }

    private void updateQuickUnlock() {
        boolean quickUnlock = (Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.LOCKSCREEN_QUICK_UNLOCK_CONTROL, 0, UserHandle.USER_CURRENT) == 1);

        if (quickUnlock) {
            mPasswordEntry.setQuickUnlockListener(new QuickUnlockListener() {
                public void onValidateQuickUnlock(String password) {
                    if (password != null && password.length() == mLockPatternUtils.getCredentialLength(userId)) {
                        validateQuickUnlock(mLockPatternUtils, password, userId);
                    }
                }
            });
        } else {
            mPasswordEntry.setQuickUnlockListener(null);
        }
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mPostureController.removeCallback(mPostureCallback);
    }

    @Override
    public void startAppearAnimation() {
        updatePinScrambling();
        updateQuickUnlock();
        mView.startAppearAnimation();
    }

    @Override
    public boolean startDisappearAnimation(Runnable finishRunnable) {
        return mView.startDisappearAnimation(
                mKeyguardUpdateMonitor.needsSlowUnlockTransition(), finishRunnable);
    }

    private AsyncTask<?, ?, ?> validateQuickUnlock(final LockPatternUtils utils,
            final String password,
            final int userId) {
        AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {

            @Override
            protected Boolean doInBackground(Void... args) {
                try {
                    return utils.checkCredential(
                           LockscreenCredential.createPinOrNone(password),
                                                userId, null);
                } catch (RequestThrottledException ex) {
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean result) {
                runQuickUnlock(result);
            }
        };
        task.execute();
        return task;
    }

    private void runQuickUnlock(Boolean matched) {
        if (matched) {
            mPasswordEntry.setEnabled(false);
            mKeyguardSecurityCallback.reportUnlockAttempt(userId, true, 0);
            mKeyguardSecurityCallback.dismiss(true, userId, SecurityMode.PIN);
            mView.resetPasswordText(true, true);
        }
    }
}
