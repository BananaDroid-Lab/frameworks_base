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

package com.android.systemui.keyguard.ui.viewmodel

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.ui.KeyguardTransitionAnimationFlow
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow

@SysUISingleton
class OffToLockscreenTransitionViewModel
@Inject
constructor(
    interactor: KeyguardTransitionInteractor,
) {

    private val transitionAnimation =
        KeyguardTransitionAnimationFlow(
            transitionDuration = 250.milliseconds,
            transitionFlow = interactor.offToLockscreenTransition
        )

    val shortcutsAlpha: Flow<Float> =
        transitionAnimation.createFlow(
            duration = 250.milliseconds,
            onStep = { it },
            onCancel = { 0f },
        )
}
