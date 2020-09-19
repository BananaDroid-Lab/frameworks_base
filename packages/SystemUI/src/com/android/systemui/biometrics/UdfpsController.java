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

package com.android.systemui.biometrics;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.IUdfpsOverlayController;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.MathUtils;
import android.util.Spline;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.BrightnessSynchronizer;
import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.doze.DozeReceiver;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.android.systemui.util.settings.SystemSettings;

import java.io.FileWriter;
import java.io.IOException;

import javax.inject.Inject;

/**
 * Shows and hides the under-display fingerprint sensor (UDFPS) overlay, handles UDFPS touch events,
 * and coordinates triggering of the high-brightness mode (HBM).
 */
class UdfpsController implements DozeReceiver {
    private static final String TAG = "UdfpsController";
    // Gamma approximation for the sRGB color space.
    private static final float DISPLAY_GAMMA = 2.2f;
    private static final long AOD_INTERRUPT_TIMEOUT_MILLIS = 1000;

    private final FingerprintManager mFingerprintManager;
    private final WindowManager mWindowManager;
    private final SystemSettings mSystemSettings;
    private final DelayableExecutor mFgExecutor;
    private final WindowManager.LayoutParams mLayoutParams;
    private final UdfpsView mView;
    // Debugfs path to control the high-brightness mode.
    private final String mHbmPath;
    private final String mHbmEnableCommand;
    private final String mHbmDisableCommand;
    private final boolean mHbmSupported;
    // Brightness in nits in the high-brightness mode.
    private final float mHbmNits;
    // A spline mapping from the device's backlight value, normalized to the range [0, 1.0], to a
    // brightness in nits.
    private final Spline mBacklightToNitsSpline;
    // A spline mapping from a value in nits to a backlight value of a hypothetical panel whose
    // maximum backlight value corresponds to our panel's high-brightness mode.
    // The output is normalized to the range [0, 1.0].
    private Spline mNitsToHbmBacklightSpline;
    // Default non-HBM backlight value normalized to the range [0, 1.0]. Used as a fallback when the
    // actual brightness value cannot be retrieved.
    private final float mDefaultBrightness;
    private boolean mIsOverlayShowing;

    // The fingerprint AOD trigger doesn't provide an ACTION_UP/ACTION_CANCEL event to tell us when
    // to turn off high brightness mode. To get around this limitation, the state of the AOD
    // interrupt is being tracked and a timeout is used as a last resort to turn off high brightness
    // mode.
    private boolean mIsAodInterruptActive;
    @Nullable private Runnable mCancelAodTimeoutAction;

    public class UdfpsOverlayController extends IUdfpsOverlayController.Stub {
        @Override
        public void showUdfpsOverlay() {
            UdfpsController.this.showUdfpsOverlay();
        }

        @Override
        public void hideUdfpsOverlay() {
            UdfpsController.this.hideUdfpsOverlay();
        }

        @Override
        public void setDebugMessage(String message) {
            mView.setDebugMessage(message);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private final UdfpsView.OnTouchListener mOnTouchListener = (v, event) -> {
        UdfpsView view = (UdfpsView) v;
        final boolean isFingerDown = view.isScrimShowing();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                final boolean isValidTouch = view.isValidTouch(event.getX(), event.getY(),
                        event.getPressure());
                if (!isFingerDown && isValidTouch) {
                    onFingerDown((int) event.getX(), (int) event.getY(), event.getTouchMinor(),
                            event.getTouchMajor());
                } else if (isFingerDown && !isValidTouch) {
                    onFingerUp();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isFingerDown) {
                    onFingerUp();
                }
                return true;

            default:
                return false;
        }
    };

    @Inject
    UdfpsController(@NonNull Context context,
            @Main Resources resources,
            LayoutInflater inflater,
            @Nullable FingerprintManager fingerprintManager,
            PowerManager powerManager,
            WindowManager windowManager,
            SystemSettings systemSettings,
            @NonNull StatusBarStateController statusBarStateController,
            @Main DelayableExecutor fgExecutor) {
        // The fingerprint manager is queried for UDFPS before this class is constructed, so the
        // fingerprint manager should never be null.
        mFingerprintManager = checkNotNull(fingerprintManager);
        mWindowManager = windowManager;
        mSystemSettings = systemSettings;
        mFgExecutor = fgExecutor;
        mLayoutParams = createLayoutParams(context);

        mView = (UdfpsView) inflater.inflate(R.layout.udfps_view, null, false);

        mHbmPath = resources.getString(R.string.udfps_hbm_sysfs_path);
        mHbmEnableCommand = resources.getString(R.string.udfps_hbm_enable_command);
        mHbmDisableCommand = resources.getString(R.string.udfps_hbm_disable_command);

        mHbmSupported = !TextUtils.isEmpty(mHbmPath);
        mView.setHbmSupported(mHbmSupported);
        statusBarStateController.addCallback(mView);

        // This range only consists of the minimum and maximum values, which only cover
        // non-high-brightness mode.
        float[] nitsRange = toFloatArray(resources.obtainTypedArray(
                com.android.internal.R.array.config_screenBrightnessNits));

        // The last value of this range corresponds to the high-brightness mode.
        float[] nitsAutoBrightnessValues = toFloatArray(resources.obtainTypedArray(
                com.android.internal.R.array.config_autoBrightnessDisplayValuesNits));

        mHbmNits = nitsAutoBrightnessValues[nitsAutoBrightnessValues.length - 1];
        float[] hbmNitsRange = {nitsRange[0], mHbmNits};

        // This range only consists of the minimum and maximum backlight values, which only apply
        // in non-high-brightness mode.
        float[] normalizedBacklightRange = normalizeBacklightRange(
                resources.getIntArray(
                        com.android.internal.R.array.config_screenBrightnessBacklight));

        mBacklightToNitsSpline = Spline.createSpline(normalizedBacklightRange, nitsRange);
        mNitsToHbmBacklightSpline = Spline.createSpline(hbmNitsRange, normalizedBacklightRange);
        mDefaultBrightness = obtainDefaultBrightness(powerManager);

        // TODO(b/160025856): move to the "dump" method.
        Log.v(TAG, String.format("ctor | mNitsRange: [%f, %f]", nitsRange[0], nitsRange[1]));
        Log.v(TAG, String.format("ctor | mHbmNitsRange: [%f, %f]", hbmNitsRange[0],
                hbmNitsRange[1]));
        Log.v(TAG, String.format("ctor | mNormalizedBacklightRange: [%f, %f]",
                normalizedBacklightRange[0], normalizedBacklightRange[1]));

        mFingerprintManager.setUdfpsOverlayController(new UdfpsOverlayController());
        mIsOverlayShowing = false;
    }

    @Override
    public void dozeTimeTick() {
        mView.dozeTimeTick();
    }

    private void showUdfpsOverlay() {
        mFgExecutor.execute(() -> {
            if (!mIsOverlayShowing) {
                try {
                    Log.v(TAG, "showUdfpsOverlay | adding window");
                    mWindowManager.addView(mView, mLayoutParams);
                    mIsOverlayShowing = true;
                    mView.setOnTouchListener(mOnTouchListener);
                } catch (RuntimeException e) {
                    Log.e(TAG, "showUdfpsOverlay | failed to add window", e);
                }
            } else {
                Log.v(TAG, "showUdfpsOverlay | the overlay is already showing");
            }
        });
    }

    private void hideUdfpsOverlay() {
        mFgExecutor.execute(() -> {
            if (mIsOverlayShowing) {
                Log.v(TAG, "hideUdfpsOverlay | removing window");
                mView.setOnTouchListener(null);
                // Reset the controller back to its starting state.
                onFingerUp();
                mWindowManager.removeView(mView);
                mIsOverlayShowing = false;
            } else {
                Log.v(TAG, "hideUdfpsOverlay | the overlay is already hidden");
            }
        });
    }

    // Returns a value in the range of [0, 255].
    private int computeScrimOpacity() {
        // Backlight setting can be NaN, -1.0f, and [0.0f, 1.0f].
        float backlightSetting = mSystemSettings.getFloatForUser(
                Settings.System.SCREEN_BRIGHTNESS_FLOAT, mDefaultBrightness,
                UserHandle.USER_CURRENT);

        // Constrain the backlight setting to [0.0f, 1.0f].
        float backlightValue = MathUtils.constrain(backlightSetting,
                PowerManager.BRIGHTNESS_MIN,
                PowerManager.BRIGHTNESS_MAX);

        // Interpolate the backlight value to nits.
        float nits = mBacklightToNitsSpline.interpolate(backlightValue);

        // Interpolate nits to a backlight value for a panel with enabled HBM.
        float interpolatedHbmBacklightValue = mNitsToHbmBacklightSpline.interpolate(nits);

        float gammaCorrectedHbmBacklightValue = (float) Math.pow(interpolatedHbmBacklightValue,
                1.0f / DISPLAY_GAMMA);
        float scrimOpacity = PowerManager.BRIGHTNESS_MAX - gammaCorrectedHbmBacklightValue;

        // Interpolate the opacity value from [0.0f, 1.0f] to [0, 255].
        return BrightnessSynchronizer.brightnessFloatToInt(scrimOpacity);
    }

    /**
     * Request fingerprint scan.
     *
     * This is intented to be called in response to a sensor that triggers an AOD interrupt for the
     * fingerprint sensor.
     */
    void onAodInterrupt(int screenX, int screenY) {
        if (mIsAodInterruptActive) {
            return;
        }
        mIsAodInterruptActive = true;
        // Since the sensor that triggers the AOD interrupt doesn't provide ACTION_UP/ACTION_CANCEL,
        // we need to be careful about not letting the screen accidentally remain in high brightness
        // mode. As a mitigation, queue a call to cancel the fingerprint scan.
        mCancelAodTimeoutAction = mFgExecutor.executeDelayed(this::onCancelAodInterrupt,
                AOD_INTERRUPT_TIMEOUT_MILLIS);
        // using a hard-coded value for major and minor until it is available from the sensor
        onFingerDown(screenX, screenY, 13.0f, 13.0f);
    }

    /**
     * Cancel fingerprint scan.
     *
     * This is intented to be called after the fingerprint scan triggered by the AOD interrupt
     * either succeeds or fails.
     */
    void onCancelAodInterrupt() {
        if (!mIsAodInterruptActive) {
            return;
        }
        if (mCancelAodTimeoutAction != null) {
            mCancelAodTimeoutAction.run();
            mCancelAodTimeoutAction = null;
        }
        mIsAodInterruptActive = false;
        onFingerUp();
    }

    private void onFingerDown(int x, int y, float minor, float major) {
        mView.setScrimAlpha(computeScrimOpacity());
        mView.showScrimAndDot();
        try {
            if (mHbmSupported) {
                FileWriter fw = new FileWriter(mHbmPath);
                fw.write(mHbmEnableCommand);
                fw.close();
            }
            mFingerprintManager.onFingerDown(x, y, minor, major);
        } catch (IOException e) {
            mView.hideScrimAndDot();
            Log.e(TAG, "onFingerDown | failed to enable HBM: " + e.getMessage());
        }
    }

    private void onFingerUp() {
        mFingerprintManager.onFingerUp();
        // Hiding the scrim before disabling HBM results in less noticeable flicker.
        mView.hideScrimAndDot();
        if (mHbmSupported) {
            try {
                FileWriter fw = new FileWriter(mHbmPath);
                fw.write(mHbmDisableCommand);
                fw.close();
            } catch (IOException e) {
                mView.showScrimAndDot();
                Log.e(TAG, "onFingerUp | failed to disable HBM: " + e.getMessage());
            }
        }
    }

    private static WindowManager.LayoutParams createLayoutParams(Context context) {
        Point displaySize = new Point();
        context.getDisplay().getRealSize(displaySize);
        // TODO(b/160025856): move to the "dump" method.
        Log.v(TAG, "createLayoutParams | display size: " + displaySize.x + "x"
                + displaySize.y);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                displaySize.x,
                displaySize.y,
                // TODO(b/152419866): Use the UDFPS window type when it becomes available.
                WindowManager.LayoutParams.TYPE_BOOT_PROGRESS,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.setTitle(TAG);
        lp.setFitInsetsTypes(0);
        return lp;
    }

    private static float obtainDefaultBrightness(PowerManager powerManager) {
        if (powerManager == null) {
            Log.e(TAG, "PowerManager is unavailable. Can't obtain default brightness.");
            return 0f;
        }
        return MathUtils.constrain(powerManager.getBrightnessConstraint(
                PowerManager.BRIGHTNESS_CONSTRAINT_TYPE_DEFAULT), PowerManager.BRIGHTNESS_MIN,
                PowerManager.BRIGHTNESS_MAX);
    }

    private static float[] toFloatArray(TypedArray array) {
        final int n = array.length();
        float[] vals = new float[n];
        for (int i = 0; i < n; i++) {
            vals[i] = array.getFloat(i, PowerManager.BRIGHTNESS_OFF_FLOAT);
        }
        array.recycle();
        return vals;
    }

    private static float[] normalizeBacklightRange(int[] backlight) {
        final int n = backlight.length;
        float[] normalizedBacklight = new float[n];
        for (int i = 0; i < n; i++) {
            normalizedBacklight[i] = BrightnessSynchronizer.brightnessIntToFloat(backlight[i]);
        }
        return normalizedBacklight;
    }
}
