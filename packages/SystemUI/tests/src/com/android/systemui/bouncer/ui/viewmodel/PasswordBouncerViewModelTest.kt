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

package com.android.systemui.bouncer.ui.viewmodel

import androidx.test.filters.SmallTest
import com.android.systemui.res.R
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.model.AuthenticationMethodModel
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.scene.SceneTestUtils
import com.android.systemui.scene.shared.model.SceneKey
import com.android.systemui.scene.shared.model.SceneModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class PasswordBouncerViewModelTest : SysuiTestCase() {

    private val utils = SceneTestUtils(this)
    private val testScope = utils.testScope
    private val authenticationInteractor =
        utils.authenticationInteractor(
            repository = utils.authenticationRepository(),
        )
    private val sceneInteractor = utils.sceneInteractor()
    private val bouncerInteractor =
        utils.bouncerInteractor(
            authenticationInteractor = authenticationInteractor,
            sceneInteractor = sceneInteractor,
        )
    private val bouncerViewModel =
        utils.bouncerViewModel(
            bouncerInteractor = bouncerInteractor,
            authenticationInteractor = authenticationInteractor,
        )
    private val underTest =
        PasswordBouncerViewModel(
            applicationScope = testScope.backgroundScope,
            interactor = bouncerInteractor,
            isInputEnabled = MutableStateFlow(true).asStateFlow(),
        )

    @Before
    fun setUp() {
        overrideResource(R.string.keyguard_enter_your_password, ENTER_YOUR_PASSWORD)
        overrideResource(R.string.kg_wrong_password, WRONG_PASSWORD)
    }

    @Test
    fun onShown() =
        testScope.runTest {
            val currentScene by collectLastValue(sceneInteractor.desiredScene)
            val message by collectLastValue(bouncerViewModel.message)
            val password by collectLastValue(underTest.password)
            utils.authenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Password
            )
            utils.authenticationRepository.setUnlocked(false)
            sceneInteractor.changeScene(SceneModel(SceneKey.Bouncer), "reason")
            sceneInteractor.onSceneChanged(SceneModel(SceneKey.Bouncer), "reason")
            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Bouncer))

            underTest.onShown()

            assertThat(message?.text).isEqualTo(ENTER_YOUR_PASSWORD)
            assertThat(password).isEqualTo("")
            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Bouncer))
        }

    @Test
    fun onPasswordInputChanged() =
        testScope.runTest {
            val currentScene by collectLastValue(sceneInteractor.desiredScene)
            val message by collectLastValue(bouncerViewModel.message)
            val password by collectLastValue(underTest.password)
            utils.authenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Password
            )
            utils.authenticationRepository.setUnlocked(false)
            sceneInteractor.changeScene(SceneModel(SceneKey.Bouncer), "reason")
            sceneInteractor.onSceneChanged(SceneModel(SceneKey.Bouncer), "reason")
            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Bouncer))
            underTest.onShown()
            runCurrent()

            underTest.onPasswordInputChanged("password")

            assertThat(message?.text).isEmpty()
            assertThat(password).isEqualTo("password")
            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Bouncer))
        }

    @Test
    fun onAuthenticateKeyPressed_whenCorrect() =
        testScope.runTest {
            val currentScene by collectLastValue(sceneInteractor.desiredScene)
            utils.authenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Password
            )
            utils.authenticationRepository.setUnlocked(false)
            sceneInteractor.changeScene(SceneModel(SceneKey.Bouncer), "reason")
            sceneInteractor.onSceneChanged(SceneModel(SceneKey.Bouncer), "reason")
            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Bouncer))
            underTest.onShown()
            underTest.onPasswordInputChanged("password")

            underTest.onAuthenticateKeyPressed()

            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Gone))
        }

    @Test
    fun onAuthenticateKeyPressed_whenWrong() =
        testScope.runTest {
            val currentScene by collectLastValue(sceneInteractor.desiredScene)
            val message by collectLastValue(bouncerViewModel.message)
            val password by collectLastValue(underTest.password)
            utils.authenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Password
            )
            utils.authenticationRepository.setUnlocked(false)
            sceneInteractor.changeScene(SceneModel(SceneKey.Bouncer), "reason")
            sceneInteractor.onSceneChanged(SceneModel(SceneKey.Bouncer), "reason")
            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Bouncer))
            underTest.onShown()
            underTest.onPasswordInputChanged("wrong")

            underTest.onAuthenticateKeyPressed()

            assertThat(password).isEqualTo("")
            assertThat(message?.text).isEqualTo(WRONG_PASSWORD)
            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Bouncer))
        }

    @Test
    fun onAuthenticateKeyPressed_whenEmpty() =
        testScope.runTest {
            val currentScene by collectLastValue(sceneInteractor.desiredScene)
            val message by collectLastValue(bouncerViewModel.message)
            val password by collectLastValue(underTest.password)
            utils.authenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Password
            )
            utils.authenticationRepository.setUnlocked(false)
            sceneInteractor.changeScene(SceneModel(SceneKey.Bouncer), "reason")
            sceneInteractor.onSceneChanged(SceneModel(SceneKey.Bouncer), "reason")
            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Bouncer))
            underTest.onShown()
            // Enter nothing.

            underTest.onAuthenticateKeyPressed()

            assertThat(password).isEqualTo("")
            assertThat(message?.text).isEqualTo(ENTER_YOUR_PASSWORD)
            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Bouncer))
        }

    @Test
    fun onAuthenticateKeyPressed_correctAfterWrong() =
        testScope.runTest {
            val currentScene by collectLastValue(sceneInteractor.desiredScene)
            val message by collectLastValue(bouncerViewModel.message)
            val password by collectLastValue(underTest.password)
            utils.authenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Password
            )
            utils.authenticationRepository.setUnlocked(false)
            sceneInteractor.changeScene(SceneModel(SceneKey.Bouncer), "reason")
            sceneInteractor.onSceneChanged(SceneModel(SceneKey.Bouncer), "reason")
            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Bouncer))
            underTest.onShown()
            underTest.onPasswordInputChanged("wrong")
            underTest.onAuthenticateKeyPressed()
            assertThat(password).isEqualTo("")
            assertThat(message?.text).isEqualTo(WRONG_PASSWORD)
            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Bouncer))

            // Enter the correct password:
            underTest.onPasswordInputChanged("password")
            assertThat(message?.text).isEmpty()

            underTest.onAuthenticateKeyPressed()

            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Gone))
        }

    @Test
    fun onShown_againAfterSceneChange_resetsPassword() =
        testScope.runTest {
            val currentScene by collectLastValue(sceneInteractor.desiredScene)
            val password by collectLastValue(underTest.password)
            utils.authenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Password
            )
            utils.authenticationRepository.setUnlocked(false)
            sceneInteractor.changeScene(SceneModel(SceneKey.Bouncer), "reason")
            sceneInteractor.onSceneChanged(SceneModel(SceneKey.Bouncer), "reason")
            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Bouncer))
            underTest.onShown()

            // The user types a password.
            underTest.onPasswordInputChanged("password")
            assertThat(password).isEqualTo("password")

            // The user doesn't confirm the password, but navigates back to the lockscreen instead.
            sceneInteractor.changeScene(SceneModel(SceneKey.Lockscreen), "reason")
            sceneInteractor.onSceneChanged(SceneModel(SceneKey.Lockscreen), "reason")
            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Lockscreen))

            // The user navigates to the bouncer again.
            sceneInteractor.changeScene(SceneModel(SceneKey.Bouncer), "reason")
            sceneInteractor.onSceneChanged(SceneModel(SceneKey.Bouncer), "reason")
            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Bouncer))

            underTest.onShown()

            // Ensure the previously-entered password is not shown.
            assertThat(password).isEmpty()
            assertThat(currentScene).isEqualTo(SceneModel(SceneKey.Bouncer))
        }

    companion object {
        private const val ENTER_YOUR_PASSWORD = "Enter your password"
        private const val WRONG_PASSWORD = "Wrong password"
    }
}
