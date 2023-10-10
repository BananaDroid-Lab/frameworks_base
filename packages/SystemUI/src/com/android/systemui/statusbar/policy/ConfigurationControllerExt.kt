/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.statusbar.policy

import com.android.systemui.common.coroutine.ConflatedCallbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow

/**
 * A [Flow] that emits whenever screen density or font scale has changed.
 *
 * @see ConfigurationController.ConfigurationListener.onDensityOrFontScaleChanged
 */
val ConfigurationController.onDensityOrFontScaleChanged: Flow<Unit>
    get() =
        ConflatedCallbackFlow.conflatedCallbackFlow {
            val listener =
                object : ConfigurationController.ConfigurationListener {
                    override fun onDensityOrFontScaleChanged() {
                        trySend(Unit)
                    }
                }
            addCallback(listener)
            awaitClose { removeCallback(listener) }
        }
