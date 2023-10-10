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
package com.android.systemui.statusbar.notification.icon.ui.viewmodel

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.flags.FeatureFlagsClassic
import com.android.systemui.flags.Flags
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.statusbar.notification.domain.interactor.NotificationsKeyguardInteractor
import com.android.systemui.statusbar.phone.DozeParameters
import com.android.systemui.statusbar.phone.ScreenOffAnimationController
import com.android.systemui.util.kotlin.pairwise
import com.android.systemui.util.kotlin.sample
import com.android.systemui.util.ui.AnimatableEvent
import com.android.systemui.util.ui.AnimatedValue
import com.android.systemui.util.ui.toAnimatedValueFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** View-model for the row of notification icons displayed on the always-on display. */
@SysUISingleton
class NotificationIconContainerAlwaysOnDisplayViewModel
@Inject
constructor(
    private val deviceEntryInteractor: DeviceEntryInteractor,
    private val dozeParameters: DozeParameters,
    private val featureFlags: FeatureFlagsClassic,
    keyguardInteractor: KeyguardInteractor,
    keyguardTransitionInteractor: KeyguardTransitionInteractor,
    private val notificationsKeyguardInteractor: NotificationsKeyguardInteractor,
    screenOffAnimationController: ScreenOffAnimationController,
    shadeInteractor: ShadeInteractor,
) : NotificationIconContainerViewModel {

    private val onDozeAnimationComplete = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val onVisAnimationComplete = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val animationsEnabled: Flow<Boolean> =
        combine(
            shadeInteractor.isShadeTouchable,
            keyguardInteractor.isKeyguardVisible,
        ) { panelTouchesEnabled, isKeyguardVisible ->
            panelTouchesEnabled && isKeyguardVisible
        }

    override val isDozing: Flow<AnimatedValue<Boolean>> =
        keyguardTransitionInteractor.startedKeyguardTransitionStep
            // Determine if we're dozing based on the most recent transition
            .map { step: TransitionStep ->
                val isDozing = step.to == KeyguardState.AOD || step.to == KeyguardState.DOZING
                isDozing to step
            }
            // Only emit changes based on whether we've started or stopped dozing
            .distinctUntilChanged { (wasDozing, _), (isDozing, _) -> wasDozing != isDozing }
            // Determine whether we need to animate
            .map { (isDozing, step) ->
                val animate = step.to == KeyguardState.AOD || step.from == KeyguardState.AOD
                AnimatableEvent(isDozing, animate)
            }
            .distinctUntilChanged()
            .toAnimatedValueFlow(completionEvents = onDozeAnimationComplete)

    override val isVisible: Flow<AnimatedValue<Boolean>> =
        combine(
                keyguardTransitionInteractor.finishedKeyguardState.map { it != KeyguardState.GONE },
                deviceEntryInteractor.isBypassEnabled,
                areNotifsFullyHiddenAnimated(),
                isPulseExpandingAnimated(),
            ) {
                onKeyguard: Boolean,
                bypassEnabled: Boolean,
                (notifsFullyHidden: Boolean, isAnimatingHide: Boolean),
                (pulseExpanding: Boolean, isAnimatingPulse: Boolean),
                ->
                val isAnimating = isAnimatingHide || isAnimatingPulse
                when {
                    // Hide the AOD icons if we're not in the KEYGUARD state unless the screen off
                    // animation is playing, in which case we want them to be visible if we're
                    // animating in the AOD UI and will be switching to KEYGUARD shortly.
                    !onKeyguard && !screenOffAnimationController.shouldShowAodIconsWhenShade() ->
                        AnimatedValue(false, isAnimating = false)
                    // If we're bypassing, then we're visible
                    bypassEnabled -> AnimatedValue(true, isAnimating)
                    // If we are pulsing (and not bypassing), then we are hidden
                    pulseExpanding -> AnimatedValue(false, isAnimating)
                    // If notifs are fully gone, then we're visible
                    notifsFullyHidden -> AnimatedValue(true, isAnimating)
                    // Otherwise, we're hidden
                    else -> AnimatedValue(false, isAnimating)
                }
            }
            .distinctUntilChanged()

    override fun completeDozeAnimation() {
        onDozeAnimationComplete.tryEmit(Unit)
    }

    override fun completeVisibilityAnimation() {
        onVisAnimationComplete.tryEmit(Unit)
    }

    /** Is there an expanded pulse, are we animating in response? */
    private fun isPulseExpandingAnimated(): Flow<AnimatedValue<Boolean>> {
        return notificationsKeyguardInteractor.isPulseExpanding
            .pairwise(initialValue = null)
            // If pulsing changes, start animating, unless it's the first emission
            .map { (prev, expanding) ->
                AnimatableEvent(expanding!!, startAnimating = prev != null)
            }
            .toAnimatedValueFlow(completionEvents = onVisAnimationComplete)
    }

    /** Are notifications completely hidden from view, are we animating in response? */
    private fun areNotifsFullyHiddenAnimated(): Flow<AnimatedValue<Boolean>> {
        return notificationsKeyguardInteractor.areNotificationsFullyHidden
            .pairwise(initialValue = null)
            .sample(deviceEntryInteractor.isBypassEnabled) { (prev, fullyHidden), bypassEnabled ->
                val animate =
                    when {
                        // Don't animate for the first value
                        prev == null -> false
                        // Always animate if bypass is enabled.
                        bypassEnabled -> true
                        // If we're not bypassing and we're not going to AOD, then we're not
                        // animating.
                        !dozeParameters.alwaysOn -> false
                        // Don't animate when going to AOD if the display needs blanking.
                        dozeParameters.displayNeedsBlanking -> false
                        // We only want the appear animations to happen when the notifications
                        // get fully hidden, since otherwise the un-hide animation overlaps.
                        featureFlags.isEnabled(Flags.NEW_AOD_TRANSITION) -> true
                        else -> fullyHidden!!
                    }
                AnimatableEvent(fullyHidden!!, animate)
            }
            .toAnimatedValueFlow(completionEvents = onVisAnimationComplete)
    }
}
