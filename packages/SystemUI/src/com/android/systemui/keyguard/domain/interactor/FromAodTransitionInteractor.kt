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
 * limitations under the License
 */

package com.android.systemui.keyguard.domain.interactor

import android.animation.ValueAnimator
import com.android.app.animation.Interpolators
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.data.repository.KeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.BiometricUnlockModel.Companion.isWakeAndUnlock
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionModeOnCanceled
import com.android.systemui.util.kotlin.Utils.Companion.toTriple
import com.android.systemui.util.kotlin.sample
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@SysUISingleton
class FromAodTransitionInteractor
@Inject
constructor(
    override val transitionRepository: KeyguardTransitionRepository,
    override val transitionInteractor: KeyguardTransitionInteractor,
    @Application private val scope: CoroutineScope,
    private val keyguardInteractor: KeyguardInteractor,
) :
    TransitionInteractor(
        fromState = KeyguardState.AOD,
    ) {

    override fun start() {
        listenForAodToLockscreenOrOccluded()
        listenForAodToGone()
        listenForTransitionToCamera(scope, keyguardInteractor)
    }

    private fun listenForAodToLockscreenOrOccluded() {
        scope.launch {
            keyguardInteractor
                .dozeTransitionTo(DozeStateModel.FINISH)
                .sample(
                    combine(
                        transitionInteractor.startedKeyguardTransitionStep,
                        keyguardInteractor.isKeyguardOccluded,
                        ::Pair
                    ),
                    ::toTriple
                )
                .collect { (_, lastStartedStep, occluded) ->
                    if (lastStartedStep.to == KeyguardState.AOD) {
                        val toState =
                            if (occluded) KeyguardState.OCCLUDED else KeyguardState.LOCKSCREEN
                        val modeOnCanceled =
                            if (
                                toState == KeyguardState.LOCKSCREEN &&
                                    lastStartedStep.from == KeyguardState.LOCKSCREEN
                            ) {
                                TransitionModeOnCanceled.REVERSE
                            } else {
                                TransitionModeOnCanceled.LAST_VALUE
                            }

                        startTransitionTo(
                            toState = toState,
                            modeOnCanceled = modeOnCanceled,
                        )
                    }
                }
        }
    }

    private fun listenForAodToGone() {
        scope.launch {
            keyguardInteractor.biometricUnlockState
                .sample(transitionInteractor.finishedKeyguardState, ::Pair)
                .collect { pair ->
                    val (biometricUnlockState, keyguardState) = pair
                    if (
                        keyguardState == KeyguardState.AOD && isWakeAndUnlock(biometricUnlockState)
                    ) {
                        startTransitionTo(KeyguardState.GONE)
                    }
                }
        }
    }
    override fun getDefaultAnimatorForTransitionsToState(toState: KeyguardState): ValueAnimator {
        return ValueAnimator().apply {
            interpolator = Interpolators.LINEAR
            duration =
                when (toState) {
                    KeyguardState.LOCKSCREEN -> TO_LOCKSCREEN_DURATION
                    else -> DEFAULT_DURATION
                }.inWholeMilliseconds
        }
    }

    companion object {
        val TO_LOCKSCREEN_DURATION = 500.milliseconds
        private val DEFAULT_DURATION = 500.milliseconds
    }
}
