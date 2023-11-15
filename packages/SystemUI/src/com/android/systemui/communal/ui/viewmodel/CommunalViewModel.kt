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

package com.android.systemui.communal.ui.viewmodel

import com.android.systemui.communal.domain.interactor.CommunalInteractor
import com.android.systemui.communal.domain.model.CommunalContentModel
import com.android.systemui.communal.shared.model.CommunalSceneKey
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.media.controls.ui.MediaHost
import com.android.systemui.media.dagger.MediaModule
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@SysUISingleton
class CommunalViewModel
@Inject
constructor(
    private val communalInteractor: CommunalInteractor,
    @Named(MediaModule.COMMUNAL_HUB) val mediaHost: MediaHost,
) {
    val currentScene: StateFlow<CommunalSceneKey> = communalInteractor.desiredScene
    fun onSceneChanged(scene: CommunalSceneKey) {
        communalInteractor.onSceneChanged(scene)
    }

    /** A list of all the communal content to be displayed in the communal hub. */
    val communalContent: Flow<List<CommunalContentModel>> = communalInteractor.communalContent

    /** Delete a widget by id. */
    fun onDeleteWidget(id: Int) = communalInteractor.deleteWidget(id)

    /** Open the widget editor */
    fun onOpenWidgetEditor() = communalInteractor.showWidgetEditor()
}
