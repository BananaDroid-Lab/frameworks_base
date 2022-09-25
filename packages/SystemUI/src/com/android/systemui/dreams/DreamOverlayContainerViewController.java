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

package com.android.systemui.dreams;

import static com.android.keyguard.BouncerPanelExpansionCalculator.aboutToShowBouncerProgress;
import static com.android.keyguard.BouncerPanelExpansionCalculator.getDreamAlphaScaledExpansion;
import static com.android.keyguard.BouncerPanelExpansionCalculator.getDreamYPositionScaledExpansion;
import static com.android.systemui.doze.util.BurnInHelperKt.getBurnInOffset;
import static com.android.systemui.dreams.complication.ComplicationLayoutParams.POSITION_BOTTOM;
import static com.android.systemui.dreams.complication.ComplicationLayoutParams.POSITION_TOP;

import android.content.res.Resources;
import android.os.Handler;
import android.util.MathUtils;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.R;
import com.android.systemui.animation.Interpolators;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dreams.complication.ComplicationHostViewController;
import com.android.systemui.dreams.dagger.DreamOverlayComponent;
import com.android.systemui.dreams.dagger.DreamOverlayModule;
import com.android.systemui.keyguard.domain.interactor.BouncerCallbackInteractor;
import com.android.systemui.statusbar.BlurUtils;
import com.android.systemui.statusbar.phone.KeyguardBouncer;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.util.ViewController;

import java.util.Arrays;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * View controller for {@link DreamOverlayContainerView}.
 */
@DreamOverlayComponent.DreamOverlayScope
public class DreamOverlayContainerViewController extends ViewController<DreamOverlayContainerView> {
    private final DreamOverlayStatusBarViewController mStatusBarViewController;
    private final StatusBarKeyguardViewManager mStatusBarKeyguardViewManager;
    private final BlurUtils mBlurUtils;

    private final ComplicationHostViewController mComplicationHostViewController;

    // The dream overlay's content view, which is located below the status bar (in z-order) and is
    // the space into which widgets are placed.
    private final ViewGroup mDreamOverlayContentView;

    // The maximum translation offset to apply to the overlay container to avoid screen burn-in.
    private final int mMaxBurnInOffset;

    // The interval in milliseconds between burn-in protection updates.
    private final long mBurnInProtectionUpdateInterval;

    // Amount of time in milliseconds to linear interpolate toward the final jitter offset. Once
    // this time is achieved, the normal jitter algorithm applies in full.
    private final long mMillisUntilFullJitter;

    // Main thread handler used to schedule periodic tasks (e.g. burn-in protection updates).
    private final Handler mHandler;
    private final int mDreamOverlayMaxTranslationY;
    private final BouncerCallbackInteractor mBouncerCallbackInteractor;

    private long mJitterStartTimeMillis;

    private boolean mBouncerAnimating;

    private final KeyguardBouncer.BouncerExpansionCallback mBouncerExpansionCallback =
            new KeyguardBouncer.BouncerExpansionCallback() {

                @Override
                public void onStartingToShow() {
                    mBouncerAnimating = true;
                }

                @Override
                public void onStartingToHide() {
                    mBouncerAnimating = true;
                }

                @Override
                public void onFullyHidden() {
                    mBouncerAnimating = false;
                }

                @Override
                public void onFullyShown() {
                    mBouncerAnimating = false;
                }

                @Override
                public void onExpansionChanged(float bouncerHideAmount) {
                    if (mBouncerAnimating) {
                        updateTransitionState(bouncerHideAmount);
                    }
                }

                @Override
                public void onVisibilityChanged(boolean isVisible) {
                    // The bouncer may be hidden abruptly without triggering onExpansionChanged.
                    // In this case, we should reset the transition state.
                    if (!isVisible) {
                        updateTransitionState(1f);
                    }
                }
            };

    @Inject
    public DreamOverlayContainerViewController(
            DreamOverlayContainerView containerView,
            ComplicationHostViewController complicationHostViewController,
            @Named(DreamOverlayModule.DREAM_OVERLAY_CONTENT_VIEW) ViewGroup contentView,
            DreamOverlayStatusBarViewController statusBarViewController,
            StatusBarKeyguardViewManager statusBarKeyguardViewManager,
            BlurUtils blurUtils,
            @Main Handler handler,
            @Main Resources resources,
            @Named(DreamOverlayModule.MAX_BURN_IN_OFFSET) int maxBurnInOffset,
            @Named(DreamOverlayModule.BURN_IN_PROTECTION_UPDATE_INTERVAL) long
                    burnInProtectionUpdateInterval,
            @Named(DreamOverlayModule.MILLIS_UNTIL_FULL_JITTER) long millisUntilFullJitter,
            BouncerCallbackInteractor bouncerCallbackInteractor) {
        super(containerView);
        mDreamOverlayContentView = contentView;
        mStatusBarViewController = statusBarViewController;
        mStatusBarKeyguardViewManager = statusBarKeyguardViewManager;
        mBlurUtils = blurUtils;

        mComplicationHostViewController = complicationHostViewController;
        mDreamOverlayMaxTranslationY = resources.getDimensionPixelSize(
                R.dimen.dream_overlay_y_offset);
        final View view = mComplicationHostViewController.getView();

        mDreamOverlayContentView.addView(view,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        mHandler = handler;
        mMaxBurnInOffset = maxBurnInOffset;
        mBurnInProtectionUpdateInterval = burnInProtectionUpdateInterval;
        mMillisUntilFullJitter = millisUntilFullJitter;
        mBouncerCallbackInteractor = bouncerCallbackInteractor;
    }

    @Override
    protected void onInit() {
        mStatusBarViewController.init();
        mComplicationHostViewController.init();
    }

    @Override
    protected void onViewAttached() {
        mJitterStartTimeMillis = System.currentTimeMillis();
        mHandler.postDelayed(this::updateBurnInOffsets, mBurnInProtectionUpdateInterval);
        final KeyguardBouncer bouncer = mStatusBarKeyguardViewManager.getBouncer();
        if (bouncer != null) {
            bouncer.addBouncerExpansionCallback(mBouncerExpansionCallback);
        }
        mBouncerCallbackInteractor.addBouncerExpansionCallback(mBouncerExpansionCallback);
    }

    @Override
    protected void onViewDetached() {
        mHandler.removeCallbacks(this::updateBurnInOffsets);
        final KeyguardBouncer bouncer = mStatusBarKeyguardViewManager.getBouncer();
        if (bouncer != null) {
            bouncer.removeBouncerExpansionCallback(mBouncerExpansionCallback);
        }
        mBouncerCallbackInteractor.removeBouncerExpansionCallback(mBouncerExpansionCallback);
    }

    View getContainerView() {
        return mView;
    }

    private void updateBurnInOffsets() {
        // Make sure the offset starts at zero, to avoid a big jump in the overlay when it first
        // appears.
        final long millisSinceStart = System.currentTimeMillis() - mJitterStartTimeMillis;
        final int burnInOffset;
        if (millisSinceStart < mMillisUntilFullJitter) {
            float lerpAmount = (float) millisSinceStart / (float) mMillisUntilFullJitter;
            burnInOffset = Math.round(MathUtils.lerp(0f, mMaxBurnInOffset, lerpAmount));
        } else {
            burnInOffset = mMaxBurnInOffset;
        }

        // These translation values change slowly, and the set translation methods are idempotent,
        // so no translation occurs when the values don't change.
        final int halfBurnInOffset = burnInOffset / 2;
        final int burnInOffsetX = getBurnInOffset(burnInOffset, true) - halfBurnInOffset;
        final int burnInOffsetY = getBurnInOffset(burnInOffset, false) - halfBurnInOffset;
        mView.setTranslationX(burnInOffsetX);
        mView.setTranslationY(burnInOffsetY);

        mHandler.postDelayed(this::updateBurnInOffsets, mBurnInProtectionUpdateInterval);
    }

    private void updateTransitionState(float bouncerHideAmount) {
        for (int position : Arrays.asList(POSITION_TOP, POSITION_BOTTOM)) {
            final float alpha = getAlpha(position, bouncerHideAmount);
            final float translationY = getTranslationY(position, bouncerHideAmount);
            mComplicationHostViewController.getViewsAtPosition(position).forEach(v -> {
                v.setAlpha(alpha);
                v.setTranslationY(translationY);
            });
        }

        mBlurUtils.applyBlur(mView.getViewRootImpl(),
                (int) mBlurUtils.blurRadiusOfRatio(
                        1 - aboutToShowBouncerProgress(bouncerHideAmount)), false);
    }

    private static float getAlpha(int position, float expansion) {
        return Interpolators.LINEAR_OUT_SLOW_IN.getInterpolation(
                position == POSITION_TOP ? getDreamAlphaScaledExpansion(expansion)
                        : aboutToShowBouncerProgress(expansion + 0.03f));
    }

    private float getTranslationY(int position, float expansion) {
        final float fraction = Interpolators.LINEAR_OUT_SLOW_IN.getInterpolation(
                position == POSITION_TOP ? getDreamYPositionScaledExpansion(expansion)
                        : aboutToShowBouncerProgress(expansion + 0.03f));
        return MathUtils.lerp(-mDreamOverlayMaxTranslationY, 0, fraction);
    }
}
