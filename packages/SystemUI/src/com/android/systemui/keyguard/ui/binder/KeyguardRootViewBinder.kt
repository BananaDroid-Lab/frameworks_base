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

package com.android.systemui.keyguard.ui.binder

import android.annotation.DrawableRes
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.systemui.R
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.Text
import com.android.systemui.common.shared.model.TintedIcon
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.flags.Flags
import com.android.systemui.keyguard.ui.viewmodel.OccludingAppDeviceEntryMessageViewModel
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.temporarydisplay.ViewPriority
import com.android.systemui.temporarydisplay.chipbar.ChipbarCoordinator
import com.android.systemui.temporarydisplay.chipbar.ChipbarInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

/** Bind occludingAppDeviceEntryMessageViewModel to run whenever the keyguard view is attached. */
@ExperimentalCoroutinesApi
object KeyguardRootViewBinder {
    @JvmStatic
    fun bind(
        view: ViewGroup,
        featureFlags: FeatureFlags,
        occludingAppDeviceEntryMessageViewModel: OccludingAppDeviceEntryMessageViewModel,
        chipbarCoordinator: ChipbarCoordinator,
    ) {
        if (featureFlags.isEnabled(Flags.FP_LISTEN_OCCLUDING_APPS)) {
            view.repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    launch {
                        occludingAppDeviceEntryMessageViewModel.message.collect { biometricMessage
                            ->
                            if (biometricMessage?.message != null) {
                                chipbarCoordinator.displayView(
                                    createChipbarInfo(
                                        biometricMessage.message,
                                        R.drawable.ic_lock,
                                    )
                                )
                            } else {
                                chipbarCoordinator.removeView(ID, "occludingAppMsgNull")
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Creates an instance of [ChipbarInfo] that can be sent to [ChipbarCoordinator] for display.
     */
    private fun createChipbarInfo(message: String, @DrawableRes icon: Int): ChipbarInfo {
        return ChipbarInfo(
            startIcon =
                TintedIcon(
                    Icon.Resource(icon, null),
                    ChipbarInfo.DEFAULT_ICON_TINT,
                ),
            text = Text.Loaded(message),
            endItem = null,
            vibrationEffect = null,
            windowTitle = "OccludingAppUnlockMsgChip",
            wakeReason = "OCCLUDING_APP_UNLOCK_MSG_CHIP",
            timeoutMs = 3500,
            id = ID,
            priority = ViewPriority.CRITICAL,
            instanceId = null,
        )
    }

    private const val ID = "occluding_app_device_entry_unlock_msg"
}
