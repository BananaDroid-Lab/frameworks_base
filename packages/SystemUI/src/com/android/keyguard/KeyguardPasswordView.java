/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.keyguard.KeyguardSecurityView.PROMPT_REASON_DEVICE_ADMIN;
import static com.android.keyguard.KeyguardSecurityView.PROMPT_REASON_NONE;
import static com.android.keyguard.KeyguardSecurityView.PROMPT_REASON_PREPARE_FOR_UPDATE;
import static com.android.keyguard.KeyguardSecurityView.PROMPT_REASON_RESTART;
import static com.android.keyguard.KeyguardSecurityView.PROMPT_REASON_TIMEOUT;
import static com.android.keyguard.KeyguardSecurityView.PROMPT_REASON_USER_REQUEST;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.TextView;

import com.android.internal.widget.LockscreenCredential;
import com.android.internal.widget.TextViewInputDisabler;
import com.android.systemui.R;
/**
 * Displays an alphanumeric (latin-1) key entry for the user to enter
 * an unlock password
 */
public class KeyguardPasswordView extends KeyguardAbsKeyInputView {

    private final int mDisappearYTranslation;

    // A delay constant to be used in a workaround for the situation where InputMethodManagerService
    // is not switched to the new user yet.
    // TODO: Remove this by ensuring such a race condition never happens.

    private TextView mPasswordEntry;
    private TextViewInputDisabler mPasswordEntryDisabler;

    private Interpolator mLinearOutSlowInInterpolator;
    private Interpolator mFastOutLinearInInterpolator;

    public KeyguardPasswordView(Context context) {
        this(context, null);
    }

    public KeyguardPasswordView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDisappearYTranslation = getResources().getDimensionPixelSize(
                R.dimen.disappear_y_translation);
        mLinearOutSlowInInterpolator = AnimationUtils.loadInterpolator(
                context, android.R.interpolator.linear_out_slow_in);
        mFastOutLinearInInterpolator = AnimationUtils.loadInterpolator(
                context, android.R.interpolator.fast_out_linear_in);
    }

    @Override
    protected void resetState() {
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.passwordEntry;
    }

    @Override
    protected int getPromptReasonStringRes(int reason) {
        switch (reason) {
            case PROMPT_REASON_RESTART:
                return R.string.kg_prompt_reason_restart_password;
            case PROMPT_REASON_TIMEOUT:
                return R.string.kg_prompt_reason_timeout_password;
            case PROMPT_REASON_DEVICE_ADMIN:
                return R.string.kg_prompt_reason_device_admin;
            case PROMPT_REASON_USER_REQUEST:
                return R.string.kg_prompt_reason_user_request;
            case PROMPT_REASON_PREPARE_FOR_UPDATE:
                return R.string.kg_prompt_reason_timeout_password;
            case PROMPT_REASON_NONE:
                return 0;
            default:
                return R.string.kg_prompt_reason_timeout_password;
        }
    }


    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mPasswordEntry = findViewById(getPasswordTextViewId());
        mPasswordEntryDisabler = new TextViewInputDisabler(mPasswordEntry);
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        // send focus to the password field
        return mPasswordEntry.requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    protected void resetPasswordText(boolean animate, boolean announce) {
        mPasswordEntry.setText("");
    }

    @Override
    protected LockscreenCredential getEnteredCredential() {
        return LockscreenCredential.createPasswordOrNone(mPasswordEntry.getText());
    }

    @Override
    protected void setPasswordEntryEnabled(boolean enabled) {
        mPasswordEntry.setEnabled(enabled);
    }

    @Override
    protected void setPasswordEntryInputEnabled(boolean enabled) {
        mPasswordEntryDisabler.setInputEnabled(enabled);
    }

    @Override
    public int getWrongPasswordStringId() {
        return R.string.kg_wrong_password;
    }

    @Override
    public void startAppearAnimation() {
        setAlpha(0f);
        setTranslationY(0f);
        animate()
                .alpha(1)
                .withLayer()
                .setDuration(300)
                .setInterpolator(mLinearOutSlowInInterpolator);
    }

    @Override
    public boolean startDisappearAnimation(Runnable finishRunnable) {
        animate()
                .alpha(0f)
                .translationY(mDisappearYTranslation)
                .setInterpolator(mFastOutLinearInInterpolator)
                .setDuration(100)
                .withEndAction(finishRunnable);
        return true;
    }

    @Override
    public CharSequence getTitle() {
        return getResources().getString(
                com.android.internal.R.string.keyguard_accessibility_password_unlock);
    }
}
