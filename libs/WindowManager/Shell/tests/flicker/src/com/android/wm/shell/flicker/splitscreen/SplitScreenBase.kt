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

package com.android.wm.shell.flicker.splitscreen

import android.content.Context
import com.android.server.wm.flicker.FlickerTestParameter
import com.android.server.wm.flicker.dsl.FlickerBuilder
import com.android.server.wm.flicker.helpers.setRotation
import com.android.wm.shell.flicker.BaseTest

abstract class SplitScreenBase(testSpec: FlickerTestParameter) : BaseTest(testSpec) {
    protected val context: Context = instrumentation.context
    protected val primaryApp = SplitScreenUtils.getPrimary(instrumentation)
    protected val secondaryApp = SplitScreenUtils.getSecondary(instrumentation)

    /** {@inheritDoc} */
    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                tapl.setEnableRotation(true)
                setRotation(testSpec.startRotation)
                tapl.setExpectedRotation(testSpec.startRotation)
                tapl.workspace.switchToOverview().dismissAllTasks()
            }
            teardown {
                primaryApp.exit(wmHelper)
                secondaryApp.exit(wmHelper)
            }
        }
}
