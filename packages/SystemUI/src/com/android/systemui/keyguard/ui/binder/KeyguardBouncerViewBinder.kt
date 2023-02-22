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

package com.android.systemui.keyguard.ui.binder

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.window.OnBackAnimationCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.keyguard.KeyguardSecurityContainerController
import com.android.keyguard.KeyguardSecurityModel
import com.android.keyguard.KeyguardSecurityView
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.dagger.KeyguardBouncerComponent
import com.android.settingslib.Utils
import com.android.systemui.keyguard.data.BouncerViewDelegate
import com.android.systemui.keyguard.shared.constants.KeyguardBouncerConstants.EXPANSION_VISIBLE
import com.android.systemui.keyguard.ui.viewmodel.KeyguardBouncerViewModel
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.plugins.ActivityStarter
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Binds the bouncer container to its view model. */
object KeyguardBouncerViewBinder {
    @JvmStatic
    fun bind(
        view: ViewGroup,
        viewModel: KeyguardBouncerViewModel,
        componentFactory: KeyguardBouncerComponent.Factory
    ) {
        // Builds the KeyguardSecurityContainerController from bouncer view group.
        val securityContainerController: KeyguardSecurityContainerController =
            componentFactory.create(view).securityContainerController
        securityContainerController.init()
        val delegate =
            object : BouncerViewDelegate {
                override fun isFullScreenBouncer(): Boolean {
                    val mode = securityContainerController.currentSecurityMode
                    return mode == KeyguardSecurityModel.SecurityMode.SimPin ||
                        mode == KeyguardSecurityModel.SecurityMode.SimPuk
                }

                override fun getBackCallback(): OnBackAnimationCallback {
                    return securityContainerController.backCallback
                }

                override fun shouldDismissOnMenuPressed(): Boolean {
                    return securityContainerController.shouldEnableMenuKey()
                }

                override fun interceptMediaKey(event: KeyEvent?): Boolean {
                    return securityContainerController.interceptMediaKey(event)
                }

                override fun dispatchBackKeyEventPreIme(): Boolean {
                    return securityContainerController.dispatchBackKeyEventPreIme()
                }

                override fun showNextSecurityScreenOrFinish(): Boolean {
                    return securityContainerController.dismiss(
                        KeyguardUpdateMonitor.getCurrentUser()
                    )
                }

                override fun resume() {
                    securityContainerController.showPrimarySecurityScreen(/* isTurningOff= */ false)
                    securityContainerController.onResume(KeyguardSecurityView.SCREEN_ON)
                }

                override fun setDismissAction(
                    onDismissAction: ActivityStarter.OnDismissAction?,
                    cancelAction: Runnable?
                ) {
                    securityContainerController.setOnDismissAction(onDismissAction, cancelAction)
                }

                override fun willDismissWithActions(): Boolean {
                    return securityContainerController.hasDismissActions()
                }
            }
        view.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                try {
                    viewModel.setBouncerViewDelegate(delegate)
                    launch {
                        viewModel.show.collect {
                            // Reset Security Container entirely.
                            securityContainerController.reinflateViewFlipper()
                            securityContainerController.showPromptReason(it.promptReason)
                            it.errorMessage?.let { errorMessage ->
                                securityContainerController.showMessage(
                                    errorMessage,
                                    Utils.getColorError(view.context)
                                )
                            }
                            securityContainerController.showPrimarySecurityScreen(
                                /* turningOff= */ false
                            )
                            securityContainerController.appear()
                            securityContainerController.onResume(KeyguardSecurityView.SCREEN_ON)
                        }
                    }

                    launch {
                        viewModel.hide.collect {
                            securityContainerController.cancelDismissAction()
                            securityContainerController.reset()
                        }
                    }

                    launch {
                        viewModel.startingToHide.collect {
                            securityContainerController.onStartingToHide()
                        }
                    }

                    launch {
                        viewModel.startDisappearAnimation.collect {
                            securityContainerController.startDisappearAnimation(it)
                        }
                    }

                    launch {
                        viewModel.bouncerExpansionAmount.collect { expansion ->
                            securityContainerController.setExpansion(expansion)
                        }
                    }

                    launch {
                        viewModel.bouncerExpansionAmount
                            .filter { it == EXPANSION_VISIBLE }
                            .collect {
                                securityContainerController.onResume(KeyguardSecurityView.SCREEN_ON)
                                view.announceForAccessibility(securityContainerController.title)
                            }
                    }

                    launch {
                        viewModel.isBouncerVisible.collect { isVisible ->
                            view.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
                            securityContainerController.onBouncerVisibilityChanged(isVisible)
                        }
                    }

                    launch {
                        viewModel.isBouncerVisible
                            .filter { !it }
                            .collect { securityContainerController.onPause() }
                    }

                    launch {
                        viewModel.isInteractable.collect { isInteractable ->
                            securityContainerController.setInteractable(isInteractable)
                        }
                    }

                    launch {
                        viewModel.keyguardPosition.collect { position ->
                            securityContainerController.updateKeyguardPosition(position)
                        }
                    }

                    launch {
                        viewModel.updateResources.collect {
                            securityContainerController.updateResources()
                            viewModel.notifyUpdateResources()
                        }
                    }

                    launch {
                        viewModel.bouncerShowMessage.collect {
                            securityContainerController.showMessage(it.message, it.colorStateList)
                            viewModel.onMessageShown()
                        }
                    }

                    launch {
                        viewModel.keyguardAuthenticated.collect {
                            securityContainerController.finish(
                                it,
                                KeyguardUpdateMonitor.getCurrentUser()
                            )
                            viewModel.notifyKeyguardAuthenticated()
                        }
                    }

                    launch {
                        viewModel
                            .observeOnIsBackButtonEnabled { view.systemUiVisibility }
                            .collect { view.systemUiVisibility = it }
                    }

                    launch {
                        viewModel.shouldUpdateSideFps.collect {
                            viewModel.updateSideFpsVisibility()
                        }
                    }

                    launch {
                        viewModel.sideFpsShowing.collect {
                            securityContainerController.updateSideFpsVisibility(it)
                        }
                    }
                    awaitCancellation()
                } finally {
                    viewModel.setBouncerViewDelegate(null)
                }
            }
        }
    }
}
