/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.wm.flicker.launch

import android.os.SystemClock
import android.platform.test.annotations.Postsubmit
import android.tools.device.apphelpers.CameraAppHelper
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.device.flicker.junit.FlickerParametersRunnerFactory
import android.tools.device.flicker.legacy.FlickerBuilder
import android.tools.device.flicker.legacy.FlickerTest
import android.tools.device.flicker.legacy.FlickerTestFactory
import android.view.KeyEvent
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.setRotation
import android.tools.device.flicker.rules.RemoveAllTasksButHomeRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test cold launching camera from launcher by double pressing power button
 *
 * To run this test: `atest FlickerTests:OpenCameraOnDoubleClickPowerButton`
 *
 * Actions:
 * ```
 *     Make sure no apps are running on the device
 *     Launch an app [testApp] and wait animation to complete
 * ```
 * Notes:
 * ```
 *     1. Some default assertions (e.g., nav bar, status bar and screen covered)
 *        are inherited [OpenAppTransition]
 *     2. Part of the test setup occurs automatically via
 *        [com.android.server.wm.flicker.TransitionRunnerWithRules],
 *        including configuring navigation mode, initial orientation and ensuring no
 *        apps are running before setup
 * ```
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OpenCameraOnDoubleClickPowerButton(flicker: FlickerTest) :
    OpenAppFromLauncherTransition(flicker) {
    private val cameraApp = CameraAppHelper(instrumentation)
    override val testApp: StandardAppHelper
        get() = cameraApp

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                RemoveAllTasksButHomeRule.removeAllTasksButHome()
                this.setRotation(flicker.scenario.startRotation)
            }
            transitions {
                device.pressKeyCode(KeyEvent.KEYCODE_POWER)
                SystemClock.sleep(100)
                device.pressKeyCode(KeyEvent.KEYCODE_POWER)
                wmHelper.StateSyncBuilder().withWindowSurfaceAppeared(cameraApp).waitForAndVerify()
            }
            teardown { RemoveAllTasksButHomeRule.removeAllTasksButHome() }
        }

    @Postsubmit @Test override fun appLayerBecomesVisible() = super.appLayerBecomesVisible()

    @Postsubmit @Test override fun appWindowAsTopWindowAtEnd() = super.appWindowAsTopWindowAtEnd()

    @Postsubmit @Test override fun appWindowBecomesTopWindow() = super.appWindowBecomesTopWindow()

    @Postsubmit @Test override fun appWindowBecomesVisible() = super.appWindowBecomesVisible()

    @Postsubmit @Test override fun appLayerReplacesLauncher() = super.appLayerReplacesLauncher()

    @Postsubmit @Test override fun appWindowIsTopWindowAtEnd() = super.appWindowIsTopWindowAtEnd()

    @Postsubmit
    @Test
    override fun appWindowReplacesLauncherAsTopWindow() =
        super.appWindowReplacesLauncherAsTopWindow()

    @Postsubmit @Test override fun focusChanges() = super.focusChanges()

    @Postsubmit @Test override fun entireScreenCovered() = super.entireScreenCovered()

    @Postsubmit
    @Test
    override fun navBarLayerIsVisibleAtStartAndEnd() = super.navBarLayerIsVisibleAtStartAndEnd()

    @Postsubmit
    @Test
    override fun navBarLayerPositionAtStartAndEnd() = super.navBarLayerPositionAtStartAndEnd()

    @Postsubmit
    @Test
    override fun navBarWindowIsAlwaysVisible() = super.navBarWindowIsAlwaysVisible()

    @Postsubmit
    @Test
    override fun statusBarLayerIsVisibleAtStartAndEnd() =
        super.statusBarLayerIsVisibleAtStartAndEnd()

    @Postsubmit
    @Test
    override fun statusBarLayerPositionAtStartAndEnd() = super.statusBarLayerPositionAtStartAndEnd()

    @Postsubmit
    @Test
    override fun statusBarWindowIsAlwaysVisible() = super.statusBarWindowIsAlwaysVisible()

    @Postsubmit
    @Test
    override fun taskBarLayerIsVisibleAtStartAndEnd() = super.taskBarLayerIsVisibleAtStartAndEnd()

    @Postsubmit
    @Test
    override fun taskBarWindowIsAlwaysVisible() = super.taskBarWindowIsAlwaysVisible()

    @Postsubmit
    @Test
    override fun visibleLayersShownMoreThanOneConsecutiveEntry() =
        super.visibleLayersShownMoreThanOneConsecutiveEntry()

    @Postsubmit
    @Test
    override fun visibleWindowsShownMoreThanOneConsecutiveEntry() =
        super.visibleWindowsShownMoreThanOneConsecutiveEntry()

    @Postsubmit
    @Test
    override fun navBarWindowIsVisibleAtStartAndEnd() {
        super.navBarWindowIsVisibleAtStartAndEnd()
    }

    companion object {
        /**
         * Creates the test configurations.
         *
         * See [FlickerTestFactory.nonRotationTests] for configuring screen orientation and
         * navigation modes.
         */
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerTest> {
            return FlickerTestFactory.nonRotationTests()
        }
    }
}
