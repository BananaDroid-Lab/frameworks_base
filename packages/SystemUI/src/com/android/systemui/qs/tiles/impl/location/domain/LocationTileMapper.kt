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

package com.android.systemui.qs.tiles.impl.location.domain

import android.content.Context
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.tiles.base.interactor.QSTileDataToStateMapper
import com.android.systemui.qs.tiles.impl.location.domain.model.LocationTileModel
import com.android.systemui.qs.tiles.viewmodel.QSTileConfig
import com.android.systemui.qs.tiles.viewmodel.QSTileState
import com.android.systemui.res.R
import javax.inject.Inject

/** Maps [LocationTileModel] to [QSTileState]. */
class LocationTileMapper @Inject constructor(private val context: Context) :
    QSTileDataToStateMapper<LocationTileModel> {

    override fun map(config: QSTileConfig, data: LocationTileModel): QSTileState =
        QSTileState.build(context, config.uiConfig) {
            val icon =
                Icon.Loaded(
                    context.resources.getDrawable(
                        if (data.isEnabled) {
                            R.drawable.qs_location_icon_on
                        } else {
                            R.drawable.qs_location_icon_off
                        }
                    ),
                    contentDescription = null
                )
            this.icon = { icon }

            this.label = context.resources.getString(R.string.quick_settings_location_label)

            if (data.isEnabled) {
                activationState = QSTileState.ActivationState.ACTIVE
                secondaryLabel = context.resources.getStringArray(R.array.tile_states_location)[2]
            } else {
                activationState = QSTileState.ActivationState.INACTIVE
                secondaryLabel = context.resources.getStringArray(R.array.tile_states_location)[1]
            }
            contentDescription = label
            supportedActions =
                setOf(QSTileState.UserAction.CLICK, QSTileState.UserAction.LONG_CLICK)
        }
}
