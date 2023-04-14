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

package com.android.systemui.biometrics

import android.graphics.Rect
import android.hardware.biometrics.BiometricOverlayConstants.REASON_AUTH_BP
import android.hardware.biometrics.BiometricOverlayConstants.REASON_AUTH_KEYGUARD
import android.hardware.biometrics.BiometricOverlayConstants.REASON_AUTH_OTHER
import android.hardware.biometrics.BiometricOverlayConstants.REASON_AUTH_SETTINGS
import android.hardware.biometrics.BiometricOverlayConstants.REASON_ENROLL_ENROLLING
import android.hardware.biometrics.BiometricOverlayConstants.ShowReason
import android.hardware.fingerprint.FingerprintManager
import android.hardware.fingerprint.IUdfpsOverlayControllerCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.testing.TestableLooper.RunWithLooper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.Surface.ROTATION_0
import android.view.Surface.Rotation
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.test.filters.SmallTest
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.settingslib.udfps.UdfpsOverlayParams
import com.android.settingslib.udfps.UdfpsUtils
import com.android.systemui.R
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.ActivityLaunchAnimator
import com.android.systemui.dump.DumpManager
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.flags.Flags
import com.android.systemui.keyguard.domain.interactor.AlternateBouncerInteractor
import com.android.systemui.keyguard.domain.interactor.PrimaryBouncerInteractor
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.shade.ShadeExpansionStateManager
import com.android.systemui.statusbar.LockscreenShadeTransitionController
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager
import com.android.systemui.statusbar.phone.SystemUIDialogManager
import com.android.systemui.statusbar.phone.UnlockedScreenOffAnimationController
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.util.settings.SecureSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.junit.MockitoJUnit

private const val REQUEST_ID = 2L

// Dimensions for the current display resolution.
private const val DISPLAY_WIDTH = 1080
private const val DISPLAY_HEIGHT = 1920
private const val SENSOR_WIDTH = 30
private const val SENSOR_HEIGHT = 60

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper(setAsMainLooper = true)
class UdfpsControllerOverlayTest : SysuiTestCase() {

    @JvmField @Rule var rule = MockitoJUnit.rule()

    @Mock private lateinit var fingerprintManager: FingerprintManager
    @Mock private lateinit var inflater: LayoutInflater
    @Mock private lateinit var windowManager: WindowManager
    @Mock private lateinit var accessibilityManager: AccessibilityManager
    @Mock private lateinit var statusBarStateController: StatusBarStateController
    @Mock private lateinit var shadeExpansionStateManager: ShadeExpansionStateManager
    @Mock private lateinit var statusBarKeyguardViewManager: StatusBarKeyguardViewManager
    @Mock private lateinit var keyguardUpdateMonitor: KeyguardUpdateMonitor
    @Mock private lateinit var dialogManager: SystemUIDialogManager
    @Mock private lateinit var dumpManager: DumpManager
    @Mock private lateinit var transitionController: LockscreenShadeTransitionController
    @Mock private lateinit var configurationController: ConfigurationController
    @Mock private lateinit var keyguardStateController: KeyguardStateController
    @Mock private lateinit var unlockedScreenOffAnimationController:
            UnlockedScreenOffAnimationController
    @Mock private lateinit var udfpsDisplayMode: UdfpsDisplayModeProvider
    @Mock private lateinit var secureSettings: SecureSettings
    @Mock private lateinit var controllerCallback: IUdfpsOverlayControllerCallback
    @Mock private lateinit var udfpsController: UdfpsController
    @Mock private lateinit var udfpsView: UdfpsView
    @Mock private lateinit var udfpsKeyguardView: UdfpsKeyguardView
    @Mock private lateinit var activityLaunchAnimator: ActivityLaunchAnimator
    @Mock private lateinit var featureFlags: FeatureFlags
    @Mock private lateinit var primaryBouncerInteractor: PrimaryBouncerInteractor
    @Mock private lateinit var alternateBouncerInteractor: AlternateBouncerInteractor
    @Mock private lateinit var udfpsUtils: UdfpsUtils
    @Captor private lateinit var layoutParamsCaptor: ArgumentCaptor<WindowManager.LayoutParams>

    private val onTouch = { _: View, _: MotionEvent, _: Boolean -> true }
    private var overlayParams: UdfpsOverlayParams = UdfpsOverlayParams()
    private lateinit var controllerOverlay: UdfpsControllerOverlay

    @Before
    fun setup() {
        whenever(inflater.inflate(R.layout.udfps_view, null, false))
            .thenReturn(udfpsView)
        whenever(inflater.inflate(R.layout.udfps_bp_view, null))
            .thenReturn(mock(UdfpsBpView::class.java))
        whenever(inflater.inflate(R.layout.udfps_keyguard_view, null))
            .thenReturn(udfpsKeyguardView)
        whenever(inflater.inflate(R.layout.udfps_fpm_empty_view, null))
            .thenReturn(mock(UdfpsFpmEmptyView::class.java))
    }

    private fun withReason(
        @ShowReason reason: Int,
        isDebuggable: Boolean = false,
        block: () -> Unit
    ) {
        controllerOverlay = UdfpsControllerOverlay(
            context, fingerprintManager, inflater, windowManager, accessibilityManager,
            statusBarStateController, shadeExpansionStateManager, statusBarKeyguardViewManager,
            keyguardUpdateMonitor, dialogManager, dumpManager, transitionController,
            configurationController, keyguardStateController, unlockedScreenOffAnimationController,
            udfpsDisplayMode, secureSettings, REQUEST_ID, reason,
            controllerCallback, onTouch, activityLaunchAnimator, featureFlags,
            primaryBouncerInteractor, alternateBouncerInteractor, isDebuggable, udfpsUtils
        )
        block()
    }

    @Test
    fun showUdfpsOverlay_bp() = withReason(REASON_AUTH_BP) { showUdfpsOverlay() }

    @Test
    fun showUdfpsOverlay_keyguard() = withReason(REASON_AUTH_KEYGUARD) {
        showUdfpsOverlay()
        verify(udfpsKeyguardView).updateSensorLocation(eq(overlayParams.sensorBounds))
    }

    @Test
    fun showUdfpsOverlay_other() = withReason(REASON_AUTH_OTHER) { showUdfpsOverlay() }

    private fun withRotation(@Rotation rotation: Int, block: () -> Unit) {
        // Sensor that's in the top left corner of the display in natural orientation.
        val sensorBounds = Rect(0, 0, SENSOR_WIDTH, SENSOR_HEIGHT)
        val overlayBounds = Rect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT)
        overlayParams = UdfpsOverlayParams(
            sensorBounds,
            overlayBounds,
            DISPLAY_WIDTH,
            DISPLAY_HEIGHT,
            scaleFactor = 1f,
            rotation
        )
        block()
    }

    @Test
    fun showUdfpsOverlay_withRotation0() = withRotation(Surface.ROTATION_0) {
        withReason(REASON_AUTH_BP) {
            controllerOverlay.show(udfpsController, overlayParams)
            verify(windowManager).addView(
                eq(controllerOverlay.overlayView),
                layoutParamsCaptor.capture()
            )

            // ROTATION_0 is the native orientation. Sensor should stay in the top left corner.
            val lp = layoutParamsCaptor.value
            assertThat(lp.x).isEqualTo(0)
            assertThat(lp.y).isEqualTo(0)
            assertThat(lp.width).isEqualTo(SENSOR_WIDTH)
            assertThat(lp.height).isEqualTo(SENSOR_HEIGHT)
        }
    }

    @Test
    fun showUdfpsOverlay_withRotation180() = withRotation(Surface.ROTATION_180) {
        withReason(REASON_AUTH_BP) {
            controllerOverlay.show(udfpsController, overlayParams)
            verify(windowManager).addView(
                eq(controllerOverlay.overlayView),
                layoutParamsCaptor.capture()
            )

            // ROTATION_180 is not supported. Sensor should stay in the top left corner.
            val lp = layoutParamsCaptor.value
            assertThat(lp.x).isEqualTo(0)
            assertThat(lp.y).isEqualTo(0)
            assertThat(lp.width).isEqualTo(SENSOR_WIDTH)
            assertThat(lp.height).isEqualTo(SENSOR_HEIGHT)
        }
    }

    @Test
    fun showUdfpsOverlay_withRotation90() = withRotation(Surface.ROTATION_90) {
        withReason(REASON_AUTH_BP) {
            controllerOverlay.show(udfpsController, overlayParams)
            verify(windowManager).addView(
                eq(controllerOverlay.overlayView),
                layoutParamsCaptor.capture()
            )

            // Sensor should be in the bottom left corner in ROTATION_90.
            val lp = layoutParamsCaptor.value
            assertThat(lp.x).isEqualTo(0)
            assertThat(lp.y).isEqualTo(DISPLAY_WIDTH - SENSOR_WIDTH)
            assertThat(lp.width).isEqualTo(SENSOR_HEIGHT)
            assertThat(lp.height).isEqualTo(SENSOR_WIDTH)
        }
    }

    @Test
    fun showUdfpsOverlay_withRotation270() = withRotation(Surface.ROTATION_270) {
        withReason(REASON_AUTH_BP) {
            controllerOverlay.show(udfpsController, overlayParams)
            verify(windowManager).addView(
                eq(controllerOverlay.overlayView),
                layoutParamsCaptor.capture()
            )

            // Sensor should be in the top right corner in ROTATION_270.
            val lp = layoutParamsCaptor.value
            assertThat(lp.x).isEqualTo(DISPLAY_HEIGHT - SENSOR_HEIGHT)
            assertThat(lp.y).isEqualTo(0)
            assertThat(lp.width).isEqualTo(SENSOR_HEIGHT)
            assertThat(lp.height).isEqualTo(SENSOR_WIDTH)
        }
    }

    private fun showUdfpsOverlay() {
        val didShow = controllerOverlay.show(udfpsController, overlayParams)

        verify(windowManager).addView(eq(controllerOverlay.overlayView), any())
        verify(udfpsView).setUdfpsDisplayModeProvider(eq(udfpsDisplayMode))
        verify(udfpsView).animationViewController = any()
        verify(udfpsView).addView(any())

        assertThat(didShow).isTrue()
        assertThat(controllerOverlay.isShowing).isTrue()
        assertThat(controllerOverlay.isHiding).isFalse()
        assertThat(controllerOverlay.overlayView).isNotNull()
    }

    @Test
    fun hideUdfpsOverlay_bp() = withReason(REASON_AUTH_BP) { hideUdfpsOverlay() }

    @Test
    fun hideUdfpsOverlay_keyguard() = withReason(REASON_AUTH_KEYGUARD) { hideUdfpsOverlay() }

    @Test
    fun hideUdfpsOverlay_settings() = withReason(REASON_AUTH_SETTINGS) { hideUdfpsOverlay() }

    @Test
    fun hideUdfpsOverlay_other() = withReason(REASON_AUTH_OTHER) { hideUdfpsOverlay() }

    private fun hideUdfpsOverlay() {
        val didShow = controllerOverlay.show(udfpsController, overlayParams)
        val view = controllerOverlay.overlayView
        val didHide = controllerOverlay.hide()

        verify(windowManager).removeView(eq(view))

        assertThat(didShow).isTrue()
        assertThat(didHide).isTrue()
        assertThat(controllerOverlay.overlayView).isNull()
        assertThat(controllerOverlay.animationViewController).isNull()
        assertThat(controllerOverlay.isShowing).isFalse()
        assertThat(controllerOverlay.isHiding).isTrue()
    }

    @Test
    fun canNotHide() = withReason(REASON_AUTH_BP) {
        assertThat(controllerOverlay.hide()).isFalse()
    }

    @Test
    fun canNotReshow() = withReason(REASON_AUTH_BP) {
        assertThat(controllerOverlay.show(udfpsController, overlayParams)).isTrue()
        assertThat(controllerOverlay.show(udfpsController, overlayParams)).isFalse()
    }

    @Test
    fun cancels() = withReason(REASON_AUTH_BP) {
        controllerOverlay.cancel()
        verify(controllerCallback).onUserCanceled()
    }

    @Test
    fun unconfigureDisplayOnHide() = withReason(REASON_AUTH_BP) {
        whenever(udfpsView.isDisplayConfigured).thenReturn(true)

        controllerOverlay.show(udfpsController, overlayParams)
        controllerOverlay.hide()
        verify(udfpsView).unconfigureDisplay()
    }

    @Test
    fun matchesRequestIds() = withReason(REASON_AUTH_BP) {
        assertThat(controllerOverlay.matchesRequestId(REQUEST_ID)).isTrue()
        assertThat(controllerOverlay.matchesRequestId(REQUEST_ID + 1)).isFalse()
    }

    @Test
    fun smallOverlayOnEnrollmentWithA11y() = withRotation(ROTATION_0) {
        withReason(REASON_ENROLL_ENROLLING) {
            // When a11y enabled during enrollment
            whenever(accessibilityManager.isTouchExplorationEnabled).thenReturn(true)
            whenever(featureFlags.isEnabled(Flags.UDFPS_NEW_TOUCH_DETECTION)).thenReturn(true)

            controllerOverlay.show(udfpsController, overlayParams)
            verify(windowManager).addView(
                eq(controllerOverlay.overlayView),
                layoutParamsCaptor.capture()
            )

            // Layout params should use sensor bounds
            val lp = layoutParamsCaptor.value
            assertThat(lp.width).isEqualTo(overlayParams.sensorBounds.width())
            assertThat(lp.height).isEqualTo(overlayParams.sensorBounds.height())
        }
    }
}
