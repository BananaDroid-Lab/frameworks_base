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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.icu.text.NumberFormat;

import com.android.settingslib.Utils;
import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.util.ViewController;

import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

/**
 * Controller for an AnimatableClockView.
 */
public class AnimatableClockController extends ViewController<AnimatableClockView> {
    private static final int FORMAT_NUMBER = 1234567890;

    private final StatusBarStateController mStatusBarStateController;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final int mDozingColor = Color.WHITE;
    private int mLockScreenColor;

    private boolean mIsDozing;
    private Locale mLocale;

    private final NumberFormat mBurmeseNf = NumberFormat.getInstance(Locale.forLanguageTag("my"));
    private final String mBurmeseNumerals;
    private final float mBurmeseLineSpacing;
    private final float mDefaultLineSpacing;

    public AnimatableClockController(
            AnimatableClockView view,
            StatusBarStateController statusBarStateController,
            BroadcastDispatcher broadcastDispatcher) {
        super(view);
        mStatusBarStateController = statusBarStateController;
        mIsDozing = mStatusBarStateController.isDozing();
        mBroadcastDispatcher = broadcastDispatcher;

        mBurmeseNumerals = mBurmeseNf.format(FORMAT_NUMBER);
        mBurmeseLineSpacing = getContext().getResources().getFloat(
                R.dimen.keyguard_clock_line_spacing_scale_burmese);
        mDefaultLineSpacing = getContext().getResources().getFloat(
                R.dimen.keyguard_clock_line_spacing_scale);
    }

    private BroadcastReceiver mLocaleBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateLocale();
        }
    };

    @Override
    protected void onViewAttached() {
        updateLocale();
        mBroadcastDispatcher.registerReceiver(mLocaleBroadcastReceiver,
                new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
        mStatusBarStateController.addCallback(mStatusBarStateListener);
        mIsDozing = mStatusBarStateController.isDozing();
        refreshTime();
        initColors();
    }

    @Override
    protected void onViewDetached() {
        mBroadcastDispatcher.unregisterReceiver(mLocaleBroadcastReceiver);
        mStatusBarStateController.removeCallback(mStatusBarStateListener);
    }

    /**
     * Updates the time for the view.
     */
    public void refreshTime() {
        mView.refreshTime();
    }

    /**
     * Updates the timezone for the view.
     */
    public void onTimeZoneChanged(TimeZone timeZone) {
        mView.onTimeZoneChanged(timeZone);
    }

    /**
     * Trigger a time format update
     */
    public void refreshFormat() {
        mView.refreshFormat();
    }

    private void updateLocale() {
        Locale currLocale = Locale.getDefault();
        if (!Objects.equals(currLocale, mLocale)) {
            mLocale = currLocale;
            NumberFormat nf = NumberFormat.getInstance(mLocale);
            if (nf.format(FORMAT_NUMBER).equals(mBurmeseNumerals)) {
                mView.setLineSpacingScale(mBurmeseLineSpacing);
            } else {
                mView.setLineSpacingScale(mDefaultLineSpacing);
            }
        }
    }

    private void initColors() {
        mLockScreenColor = Utils.getColorAttrDefaultColor(getContext(),
                com.android.systemui.R.attr.wallpaperTextColor);
        mView.setColors(mDozingColor, mLockScreenColor);
        mView.animateDoze(mIsDozing, false);
    }

    private final StatusBarStateController.StateListener mStatusBarStateListener =
            new StatusBarStateController.StateListener() {
                @Override
                public void onDozingChanged(boolean isDozing) {
                    mIsDozing = isDozing;
                    mView.animateDoze(mIsDozing, true);
                }
            };
}
