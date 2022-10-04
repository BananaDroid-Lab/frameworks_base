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

package com.android.wm.shell.flicker.pip

import android.platform.test.annotations.Presubmit
import android.view.Surface
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.FlickerParametersRunnerFactory
import com.android.server.wm.flicker.FlickerTestParameter
import com.android.server.wm.flicker.FlickerTestParameterFactory
import com.android.server.wm.flicker.dsl.FlickerBuilder
import com.android.wm.shell.flicker.Direction
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test Pip movement with Launcher shelf height change (decrease).
 *
 * To run this test: `atest WMShellFlickerTests:MovePipDownShelfHeightChangeTest`
 *
 * Actions:
 *     Launch [pipApp] in pip mode
 *     Launch [testApp]
 *     Press home
 *     Check if pip window moves up (visually)
 *
 * Notes:
 *     1. Some default assertions (e.g., nav bar, status bar and screen covered)
 *        are inherited [PipTransition]
 *     2. Part of the test setup occurs automatically via
 *        [com.android.server.wm.flicker.TransitionRunnerWithRules],
 *        including configuring navigation mode, initial orientation and ensuring no
 *        apps are running before setup
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
open class MovePipUpShelfHeightChangeTest(
    testSpec: FlickerTestParameter
) : MovePipShelfHeightTransition(testSpec) {
    /**
     * Defines the transition used to run the test
     */
    override val transition: FlickerBuilder.() -> Unit
        get() = buildTransition() {
            setup {
                testApp.launchViaIntent(wmHelper)
            }
            transitions {
                tapl.pressHome()
            }
            teardown {
                testApp.exit(wmHelper)
            }
        }

    /**
     * Checks that the visible region of [pipApp] window always moves up during the animation.
     */
    @Presubmit
    @Test
    fun pipWindowMovesUp() = pipWindowMoves(Direction.UP)

    /**
     * Checks that the visible region of [pipApp] layer always moves up during the animation.
     */
    @Presubmit
    @Test
    fun pipLayerMovesUp() = pipLayerMoves(Direction.UP)

    companion object {
        /**
         * Creates the test configurations.
         *
         * See [FlickerTestParameterFactory.getConfigNonRotationTests] for configuring
         * repetitions, screen orientation and navigation modes.
         */
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): List<FlickerTestParameter> {
            return FlickerTestParameterFactory.getInstance().getConfigNonRotationTests(
                supportedRotations = listOf(Surface.ROTATION_0)
            )
        }
    }
}
