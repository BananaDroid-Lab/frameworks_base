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

import android.platform.test.annotations.RequiresDevice
import com.android.server.wm.flicker.FlickerParametersRunnerFactory
import com.android.server.wm.flicker.FlickerTestParameter
import com.android.server.wm.flicker.FlickerTestParameterFactory
import com.android.server.wm.flicker.dsl.FlickerBuilder
import com.android.server.wm.flicker.helpers.CameraAppHelper
import com.android.server.wm.flicker.helpers.isShellTransitionsEnabled
import org.junit.Assume
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test launching an app after cold opening camera
 *
 * To run this test: `atest FlickerTests:OpenAppAfterCameraTest`
 *
 * Notes:
 * Some default assertions are inherited [OpenAppTransition]
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
open class OpenAppAfterCameraTest(
    testSpec: FlickerTestParameter
) : OpenAppFromLauncherTransition(testSpec) {
    @Before
    open fun before() {
        Assume.assumeFalse(isShellTransitionsEnabled)
    }

    private val cameraApp = CameraAppHelper(instrumentation) /** {@inheritDoc} */
    override val transition: FlickerBuilder.() -> Unit
        get() = {
            super.transition(this)
            setup {
                tapl.setExpectedRotationCheckEnabled(false)
                // 1. Open camera - cold -> close it first
                cameraApp.exit(wmHelper)
                cameraApp.launchViaIntent(wmHelper)
                // 2. Press home button (button nav mode) / swipe up to home (gesture nav mode)
                tapl.goHome()
            }
            teardown {
                testApp.exit(wmHelper)
            }
            transitions {
                testApp.launchViaIntent(wmHelper)
            }
        }

    companion object {
        /**
         * Creates the test configurations.
         *
         * See [FlickerTestParameterFactory.getConfigNonRotationTests] for configuring
         * repetitions, screen orientation and navigation modes.
         */
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerTestParameter> {
            return FlickerTestParameterFactory.getInstance()
                    .getConfigNonRotationTests()
        }
    }
}
