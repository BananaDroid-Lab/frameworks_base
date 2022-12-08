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

package com.android.systemui.statusbar.pipeline.mobile.data.repository.demo.model

import android.telephony.Annotation.DataActivityType
import com.android.settingslib.SignalIcon

/**
 * Model for the demo commands, ported from [NetworkControllerImpl]
 *
 * Nullable fields represent optional command line arguments
 */
sealed interface FakeNetworkEventModel {
    data class Mobile(
        val level: Int?,
        val dataType: SignalIcon.MobileIconGroup?,
        // Null means the default (chosen by the repository)
        val subId: Int?,
        val carrierId: Int?,
        val inflateStrength: Boolean?,
        @DataActivityType val activity: Int?,
        val carrierNetworkChange: Boolean,
    ) : FakeNetworkEventModel

    data class MobileDisabled(
        // Null means the default (chosen by the repository)
        val subId: Int?
    ) : FakeNetworkEventModel
}
