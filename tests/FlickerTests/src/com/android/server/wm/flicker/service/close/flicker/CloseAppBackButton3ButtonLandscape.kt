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

package com.android.server.wm.flicker.service.close.flicker

import android.tools.common.NavBar
import android.tools.common.Rotation
import android.tools.common.flicker.FlickerConfig
import android.tools.common.flicker.annotation.ExpectedScenarios
import android.tools.common.flicker.annotation.FlickerConfigProvider
import android.tools.common.flicker.config.FlickerConfig
import android.tools.common.flicker.config.FlickerServiceConfig
import android.tools.device.flicker.junit.FlickerServiceJUnit4ClassRunner
import com.android.server.wm.flicker.service.close.scenarios.CloseAppBackButton
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(FlickerServiceJUnit4ClassRunner::class)
class CloseAppBackButton3ButtonLandscape :
    CloseAppBackButton(NavBar.MODE_3BUTTON, Rotation.ROTATION_90) {
    // TODO: Missing CUJ (b/300078127)
    @ExpectedScenarios(["ENTIRE_TRACE"])
    @Test
    override fun closeAppBackButtonTest() = super.closeAppBackButtonTest()

    companion object {
        @JvmStatic
        @FlickerConfigProvider
        fun flickerConfigProvider(): FlickerConfig =
            FlickerConfig().use(FlickerServiceConfig.DEFAULT)
    }
}
