/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.flicker.appcompat

import android.platform.test.annotations.Postsubmit
import android.tools.device.flicker.junit.FlickerParametersRunnerFactory
import android.tools.device.flicker.legacy.FlickerBuilder
import android.tools.device.flicker.legacy.FlickerTest
import android.tools.device.helpers.WindowUtils
import androidx.test.filters.RequiresDevice
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test restarting app in size compat mode.
 *
 * To run this test: `atest WMShellFlickerTests:RestartAppInSizeCompatModeTest`
 *
 * Actions:
 * ```
 *     Rotate app to opposite orientation to trigger size compat mode
 *     Press restart button and wait for letterboxed app to resize
 * ```
 *
 * Notes:
 * ```
 *     Some default assertions (e.g., nav bar, status bar and screen covered)
 *     are inherited [BaseTest]
 * ```
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
class RestartAppInSizeCompatModeTest(flicker: FlickerTest) : BaseAppCompat(flicker) {

    /** {@inheritDoc} */
    override val transition: FlickerBuilder.() -> Unit
        get() = {
            super.transition(this)
            transitions { letterboxApp.clickRestart(wmHelper) }
            teardown { letterboxApp.exit(wmHelper) }
        }

    @Postsubmit @Test fun appVisibleAtStartAndEnd() = assertLetterboxAppVisibleAtStartAndEnd()

    @Postsubmit
    @Test
    fun appLayerVisibilityChanges() {
        flicker.assertLayers {
            this.isVisible(letterboxApp)
                .then()
                .isInvisible(letterboxApp)
                .then()
                .isVisible(letterboxApp)
        }
    }

    @Postsubmit
    @Test
    fun letterboxedAppHasRoundedCorners() = assertLetterboxAppAtStartHasRoundedCorners()

    /** Checks that the visible region of [letterboxApp] is still within display bounds */
    @Postsubmit
    @Test
    fun appWindowRemainInsideVisibleBounds() {
        val displayBounds = WindowUtils.getDisplayBounds(flicker.scenario.endRotation)
        flicker.assertLayersEnd { visibleRegion(letterboxApp).coversAtMost(displayBounds) }
    }
}
