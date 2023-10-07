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
 * limitations under the License
 */

package com.android.systemui.shade.data.repository

import android.app.StatusBarManager.DISABLE2_NONE
import android.app.StatusBarManager.DISABLE2_NOTIFICATION_SHADE
import android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS
import android.content.pm.UserInfo
import android.os.UserManager
import androidx.test.filters.SmallTest
import com.android.SysUITestModule
import com.android.TestMocksModule
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.FakeConfigurationRepository
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.flags.FakeFeatureFlagsClassicModule
import com.android.systemui.flags.Flags
import com.android.systemui.keyguard.data.repository.FakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.FakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.DozeTransitionModel
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.StatusBarState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.power.data.repository.FakePowerRepository
import com.android.systemui.power.shared.model.WakeSleepReason
import com.android.systemui.power.shared.model.WakefulnessState
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.ObservableTransitionState
import com.android.systemui.scene.shared.model.SceneKey
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.statusbar.disableflags.data.model.DisableFlagsModel
import com.android.systemui.statusbar.disableflags.data.repository.FakeDisableFlagsRepository
import com.android.systemui.statusbar.phone.DozeParameters
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeUserSetupRepository
import com.android.systemui.statusbar.policy.data.repository.FakeDeviceProvisioningRepository
import com.android.systemui.user.data.model.UserSwitcherSettingsModel
import com.android.systemui.user.data.repository.FakeUserRepository
import com.android.systemui.user.domain.UserDomainLayerModule
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@SmallTest
@OptIn(ExperimentalCoroutinesApi::class)
class ShadeInteractorTest : SysuiTestCase() {

    @Mock private lateinit var dozeParameters: DozeParameters

    private lateinit var testComponent: TestComponent

    private val configurationRepository
        get() = testComponent.configurationRepository
    private val deviceProvisioningRepository
        get() = testComponent.deviceProvisioningRepository
    private val disableFlagsRepository
        get() = testComponent.disableFlagsRepository
    private val keyguardRepository
        get() = testComponent.keyguardRepository
    private val keyguardTransitionRepository
        get() = testComponent.keygaurdTransitionRepository
    private val powerRepository
        get() = testComponent.powerRepository
    private val sceneInteractor
        get() = testComponent.sceneInteractor
    private val shadeRepository
        get() = testComponent.shadeRepository
    private val testScope
        get() = testComponent.testScope
    private val userRepository
        get() = testComponent.userRepository
    private val userSetupRepository
        get() = testComponent.userSetupRepository

    private lateinit var underTest: ShadeInteractor

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        testComponent =
            DaggerShadeInteractorTest_TestComponent.factory()
                .create(
                    test = this,
                    featureFlags =
                        FakeFeatureFlagsClassicModule {
                            set(Flags.FACE_AUTH_REFACTOR, false)
                            set(Flags.FULL_SCREEN_USER_SWITCHER, true)
                        },
                    mocks =
                        TestMocksModule(
                            dozeParameters = dozeParameters,
                        ),
                )
        underTest = testComponent.underTest

        runBlocking {
            val userInfos =
                listOf(
                    UserInfo(
                        /* id= */ 0,
                        /* name= */ "zero",
                        /* iconPath= */ "",
                        /* flags= */ UserInfo.FLAG_PRIMARY or
                            UserInfo.FLAG_ADMIN or
                            UserInfo.FLAG_FULL,
                        UserManager.USER_TYPE_FULL_SYSTEM,
                    ),
                )
            userRepository.setUserInfos(userInfos)
            userRepository.setSelectedUserInfo(userInfos[0])
        }
    }

    @Test
    fun isShadeEnabled_matchesDisableFlagsRepo() =
        testScope.runTest {
            val actual by collectLastValue(underTest.isShadeEnabled)

            disableFlagsRepository.disableFlags.value =
                DisableFlagsModel(disable2 = DISABLE2_NOTIFICATION_SHADE)
            assertThat(actual).isFalse()

            disableFlagsRepository.disableFlags.value = DisableFlagsModel(disable2 = DISABLE2_NONE)

            assertThat(actual).isTrue()
        }

    @Test
    fun isExpandToQsEnabled_deviceNotProvisioned_false() =
        testScope.runTest {
            deviceProvisioningRepository.setDeviceProvisioned(false)

            val actual by collectLastValue(underTest.isExpandToQsEnabled)

            assertThat(actual).isFalse()
        }

    @Test
    fun isExpandToQsEnabled_userNotSetupAndSimpleUserSwitcher_false() =
        testScope.runTest {
            deviceProvisioningRepository.setDeviceProvisioned(true)

            userSetupRepository.setUserSetup(false)
            userRepository.setSettings(UserSwitcherSettingsModel(isSimpleUserSwitcher = true))

            val actual by collectLastValue(underTest.isExpandToQsEnabled)

            assertThat(actual).isFalse()
        }

    @Test
    fun isExpandToQsEnabled_shadeNotEnabled_false() =
        testScope.runTest {
            deviceProvisioningRepository.setDeviceProvisioned(true)
            userSetupRepository.setUserSetup(true)

            disableFlagsRepository.disableFlags.value =
                DisableFlagsModel(
                    disable2 = DISABLE2_NOTIFICATION_SHADE,
                )

            val actual by collectLastValue(underTest.isExpandToQsEnabled)

            assertThat(actual).isFalse()
        }

    @Test
    fun isExpandToQsEnabled_quickSettingsNotEnabled_false() =
        testScope.runTest {
            deviceProvisioningRepository.setDeviceProvisioned(true)
            userSetupRepository.setUserSetup(true)

            disableFlagsRepository.disableFlags.value =
                DisableFlagsModel(
                    disable2 = DISABLE2_QUICK_SETTINGS,
                )
            val actual by collectLastValue(underTest.isExpandToQsEnabled)

            assertThat(actual).isFalse()
        }

    @Test
    fun isExpandToQsEnabled_dozing_false() =
        testScope.runTest {
            deviceProvisioningRepository.setDeviceProvisioned(true)
            userSetupRepository.setUserSetup(true)
            disableFlagsRepository.disableFlags.value =
                DisableFlagsModel(
                    disable2 = DISABLE2_NONE,
                )

            keyguardRepository.setIsDozing(true)

            val actual by collectLastValue(underTest.isExpandToQsEnabled)

            assertThat(actual).isFalse()
        }

    @Test
    fun isExpandToQsEnabled_userSetup_true() =
        testScope.runTest {
            deviceProvisioningRepository.setDeviceProvisioned(true)
            keyguardRepository.setIsDozing(false)
            disableFlagsRepository.disableFlags.value =
                DisableFlagsModel(
                    disable2 = DISABLE2_NONE,
                )

            userSetupRepository.setUserSetup(true)

            val actual by collectLastValue(underTest.isExpandToQsEnabled)

            assertThat(actual).isTrue()
        }

    @Test
    fun isExpandToQsEnabled_notSimpleUserSwitcher_true() =
        testScope.runTest {
            deviceProvisioningRepository.setDeviceProvisioned(true)
            keyguardRepository.setIsDozing(false)
            disableFlagsRepository.disableFlags.value =
                DisableFlagsModel(
                    disable2 = DISABLE2_NONE,
                )

            userRepository.setSettings(UserSwitcherSettingsModel(isSimpleUserSwitcher = false))

            val actual by collectLastValue(underTest.isExpandToQsEnabled)

            assertThat(actual).isTrue()
        }

    @Test
    fun isExpandToQsEnabled_respondsToDozingUpdates() =
        testScope.runTest {
            deviceProvisioningRepository.setDeviceProvisioned(true)
            keyguardRepository.setIsDozing(false)
            disableFlagsRepository.disableFlags.value =
                DisableFlagsModel(
                    disable2 = DISABLE2_NONE,
                )
            userSetupRepository.setUserSetup(true)

            val actual by collectLastValue(underTest.isExpandToQsEnabled)

            assertThat(actual).isTrue()

            // WHEN dozing starts
            keyguardRepository.setIsDozing(true)

            // THEN expand is disabled
            assertThat(actual).isFalse()

            // WHEN dozing stops
            keyguardRepository.setIsDozing(false)

            // THEN expand is enabled
            assertThat(actual).isTrue()
        }

    @Test
    fun isExpandToQsEnabled_respondsToDisableUpdates() =
        testScope.runTest {
            deviceProvisioningRepository.setDeviceProvisioned(true)
            keyguardRepository.setIsDozing(false)
            disableFlagsRepository.disableFlags.value =
                DisableFlagsModel(
                    disable2 = DISABLE2_NONE,
                )
            userSetupRepository.setUserSetup(true)

            val actual by collectLastValue(underTest.isExpandToQsEnabled)

            assertThat(actual).isTrue()

            // WHEN QS is disabled
            disableFlagsRepository.disableFlags.value =
                DisableFlagsModel(
                    disable2 = DISABLE2_QUICK_SETTINGS,
                )
            // THEN expand is disabled
            assertThat(actual).isFalse()

            // WHEN QS is enabled
            disableFlagsRepository.disableFlags.value =
                DisableFlagsModel(
                    disable2 = DISABLE2_NONE,
                )
            // THEN expand is enabled
            assertThat(actual).isTrue()
        }

    @Test
    fun isExpandToQsEnabled_respondsToUserUpdates() =
        testScope.runTest {
            deviceProvisioningRepository.setDeviceProvisioned(true)
            keyguardRepository.setIsDozing(false)
            disableFlagsRepository.disableFlags.value =
                DisableFlagsModel(
                    disable2 = DISABLE2_NONE,
                )
            userSetupRepository.setUserSetup(true)

            val actual by collectLastValue(underTest.isExpandToQsEnabled)

            assertThat(actual).isTrue()

            // WHEN the user is no longer setup
            userSetupRepository.setUserSetup(false)
            userRepository.setSettings(UserSwitcherSettingsModel(isSimpleUserSwitcher = true))

            // THEN expand is disabled
            assertThat(actual).isFalse()

            // WHEN the user is setup again
            userSetupRepository.setUserSetup(true)

            // THEN expand is enabled
            assertThat(actual).isTrue()
        }

    @Test
    fun fullShadeExpansionWhenShadeLocked() =
        testScope.runTest {
            val actual by collectLastValue(underTest.shadeExpansion)

            keyguardRepository.setStatusBarState(StatusBarState.SHADE_LOCKED)
            shadeRepository.setLockscreenShadeExpansion(0.5f)

            assertThat(actual).isEqualTo(1f)
        }

    @Test
    fun fullShadeExpansionWhenStatusBarStateIsNotShadeLocked() =
        testScope.runTest {
            val actual by collectLastValue(underTest.shadeExpansion)

            keyguardRepository.setStatusBarState(StatusBarState.KEYGUARD)

            shadeRepository.setLockscreenShadeExpansion(0.5f)
            assertThat(actual).isEqualTo(0.5f)

            shadeRepository.setLockscreenShadeExpansion(0.8f)
            assertThat(actual).isEqualTo(0.8f)
        }

    @Test
    fun shadeExpansionWhenInSplitShadeAndQsExpanded() =
        testScope.runTest {
            val actual by collectLastValue(underTest.shadeExpansion)

            // WHEN split shade is enabled and QS is expanded
            keyguardRepository.setStatusBarState(StatusBarState.SHADE)
            overrideResource(R.bool.config_use_split_notification_shade, true)
            configurationRepository.onAnyConfigurationChange()
            shadeRepository.setQsExpansion(.5f)
            shadeRepository.setLegacyShadeExpansion(.7f)
            runCurrent()

            // THEN legacy shade expansion is passed through
            assertThat(actual).isEqualTo(.7f)
        }

    @Test
    fun shadeExpansionWhenNotInSplitShadeAndQsExpanded() =
        testScope.runTest {
            val actual by collectLastValue(underTest.shadeExpansion)

            // WHEN split shade is not enabled and QS is expanded
            keyguardRepository.setStatusBarState(StatusBarState.SHADE)
            overrideResource(R.bool.config_use_split_notification_shade, false)
            shadeRepository.setQsExpansion(.5f)
            shadeRepository.setLegacyShadeExpansion(1f)
            runCurrent()

            // THEN shade expansion is zero
            assertThat(actual).isEqualTo(0f)
        }

    @Test
    fun shadeExpansionWhenNotInSplitShadeAndQsCollapsed() =
        testScope.runTest {
            val actual by collectLastValue(underTest.shadeExpansion)

            // WHEN split shade is not enabled and QS is expanded
            keyguardRepository.setStatusBarState(StatusBarState.SHADE)
            shadeRepository.setQsExpansion(0f)
            shadeRepository.setLegacyShadeExpansion(.6f)

            // THEN shade expansion is zero
            assertThat(actual).isEqualTo(.6f)
        }

    @Test
    fun anyExpansion_shadeGreater() =
        testScope.runTest() {
            // WHEN shade is more expanded than QS
            shadeRepository.setLegacyShadeExpansion(.5f)
            shadeRepository.setQsExpansion(0f)
            runCurrent()

            // THEN anyExpansion is .5f
            assertThat(underTest.anyExpansion.value).isEqualTo(.5f)
        }

    @Test
    fun anyExpansion_qsGreater() =
        testScope.runTest() {
            // WHEN qs is more expanded than shade
            shadeRepository.setLegacyShadeExpansion(0f)
            shadeRepository.setQsExpansion(.5f)
            runCurrent()

            // THEN anyExpansion is .5f
            assertThat(underTest.anyExpansion.value).isEqualTo(.5f)
        }

    @Test
    fun expanding_shadeDraggedDown_expandingTrue() =
        testScope.runTest() {
            val actual by collectLastValue(underTest.isAnyExpanding)

            // GIVEN shade and QS collapsed
            shadeRepository.setLegacyShadeExpansion(0f)
            shadeRepository.setQsExpansion(0f)
            runCurrent()

            // WHEN shade partially expanded
            shadeRepository.setLegacyShadeExpansion(.5f)
            runCurrent()

            // THEN anyExpanding is true
            assertThat(actual).isTrue()
        }

    @Test
    fun expanding_qsDraggedDown_expandingTrue() =
        testScope.runTest() {
            val actual by collectLastValue(underTest.isAnyExpanding)

            // GIVEN shade and QS collapsed
            shadeRepository.setLegacyShadeExpansion(0f)
            shadeRepository.setQsExpansion(0f)
            runCurrent()

            // WHEN shade partially expanded
            shadeRepository.setQsExpansion(.5f)
            runCurrent()

            // THEN anyExpanding is true
            assertThat(actual).isTrue()
        }

    @Test
    fun expanding_shadeDraggedUpAndDown() =
        testScope.runTest() {
            val actual by collectLastValue(underTest.isAnyExpanding)

            // WHEN shade starts collapsed then partially expanded
            shadeRepository.setLegacyShadeExpansion(0f)
            shadeRepository.setLegacyShadeExpansion(.5f)
            shadeRepository.setQsExpansion(0f)
            runCurrent()

            // THEN anyExpanding is true
            assertThat(actual).isTrue()

            // WHEN shade dragged up a bit
            shadeRepository.setLegacyShadeExpansion(.2f)
            runCurrent()

            // THEN anyExpanding is still true
            assertThat(actual).isTrue()

            // WHEN shade dragged down a bit
            shadeRepository.setLegacyShadeExpansion(.7f)
            runCurrent()

            // THEN anyExpanding is still true
            assertThat(actual).isTrue()

            // WHEN shade fully expanded
            shadeRepository.setLegacyShadeExpansion(1f)
            runCurrent()

            // THEN anyExpanding is now false
            assertThat(actual).isFalse()

            // WHEN shade dragged up a bit
            shadeRepository.setLegacyShadeExpansion(.7f)
            runCurrent()

            // THEN anyExpanding is still false
            assertThat(actual).isFalse()
        }

    @Test
    fun expanding_shadeDraggedDownThenUp_expandingFalse() =
        testScope.runTest() {
            val actual by collectLastValue(underTest.isAnyExpanding)

            // GIVEN shade starts collapsed
            shadeRepository.setLegacyShadeExpansion(0f)
            shadeRepository.setQsExpansion(0f)
            runCurrent()

            // WHEN shade expands but doesn't complete
            shadeRepository.setLegacyShadeExpansion(.5f)
            runCurrent()
            shadeRepository.setLegacyShadeExpansion(0f)
            runCurrent()

            // THEN anyExpanding is false
            assertThat(actual).isFalse()
        }

    @Test
    fun lockscreenShadeExpansion_idle_onScene() =
        testScope.runTest() {
            // GIVEN an expansion flow based on transitions to and from a scene
            val key = SceneKey.Shade
            val expansion = underTest.sceneBasedExpansion(sceneInteractor, key)
            val expansionAmount by collectLastValue(expansion)

            // WHEN transition state is idle on the scene
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(ObservableTransitionState.Idle(key))
            sceneInteractor.setTransitionState(transitionState)

            // THEN expansion is 1
            assertThat(expansionAmount).isEqualTo(1f)
        }

    @Test
    fun lockscreenShadeExpansion_idle_onDifferentScene() =
        testScope.runTest() {
            // GIVEN an expansion flow based on transitions to and from a scene
            val expansion = underTest.sceneBasedExpansion(sceneInteractor, SceneKey.Shade)
            val expansionAmount by collectLastValue(expansion)

            // WHEN transition state is idle on a different scene
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(SceneKey.Lockscreen)
                )
            sceneInteractor.setTransitionState(transitionState)

            // THEN expansion is 0
            assertThat(expansionAmount).isEqualTo(0f)
        }

    @Test
    fun lockscreenShadeExpansion_transitioning_toScene() =
        testScope.runTest() {
            // GIVEN an expansion flow based on transitions to and from a scene
            val key = SceneKey.QuickSettings
            val expansion = underTest.sceneBasedExpansion(sceneInteractor, key)
            val expansionAmount by collectLastValue(expansion)

            // WHEN transition state is starting to move to the scene
            val progress = MutableStateFlow(0f)
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = SceneKey.Lockscreen,
                        toScene = key,
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            sceneInteractor.setTransitionState(transitionState)

            // THEN expansion is 0
            assertThat(expansionAmount).isEqualTo(0f)

            // WHEN transition state is partially to the scene
            progress.value = .4f

            // THEN expansion matches the progress
            assertThat(expansionAmount).isEqualTo(.4f)

            // WHEN transition completes
            progress.value = 1f

            // THEN expansion is 1
            assertThat(expansionAmount).isEqualTo(1f)
        }

    @Test
    fun lockscreenShadeExpansion_transitioning_fromScene() =
        testScope.runTest() {
            // GIVEN an expansion flow based on transitions to and from a scene
            val key = SceneKey.QuickSettings
            val expansion = underTest.sceneBasedExpansion(sceneInteractor, key)
            val expansionAmount by collectLastValue(expansion)

            // WHEN transition state is starting to move to the scene
            val progress = MutableStateFlow(0f)
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = key,
                        toScene = SceneKey.Lockscreen,
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            sceneInteractor.setTransitionState(transitionState)

            // THEN expansion is 1
            assertThat(expansionAmount).isEqualTo(1f)

            // WHEN transition state is partially to the scene
            progress.value = .4f

            // THEN expansion reflects the progress
            assertThat(expansionAmount).isEqualTo(.6f)

            // WHEN transition completes
            progress.value = 1f

            // THEN expansion is 0
            assertThat(expansionAmount).isEqualTo(0f)
        }

    @Test
    fun lockscreenShadeExpansion_transitioning_toAndFromDifferentScenes() =
        testScope.runTest() {
            // GIVEN an expansion flow based on transitions to and from a scene
            val expansion = underTest.sceneBasedExpansion(sceneInteractor, SceneKey.QuickSettings)
            val expansionAmount by collectLastValue(expansion)

            // WHEN transition state is starting to between different scenes
            val progress = MutableStateFlow(0f)
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = SceneKey.Lockscreen,
                        toScene = SceneKey.Shade,
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            sceneInteractor.setTransitionState(transitionState)

            // THEN expansion is 0
            assertThat(expansionAmount).isEqualTo(0f)

            // WHEN transition state is partially complete
            progress.value = .4f

            // THEN expansion is still 0
            assertThat(expansionAmount).isEqualTo(0f)

            // WHEN transition completes
            progress.value = 1f

            // THEN expansion is still 0
            assertThat(expansionAmount).isEqualTo(0f)
        }

    @Test
    fun userInteractingWithShade_shadeDraggedUpAndDown() =
        testScope.runTest() {
            val actual by collectLastValue(underTest.isUserInteractingWithShade)
            // GIVEN shade collapsed and not tracking input
            shadeRepository.setLegacyShadeExpansion(0f)
            shadeRepository.setLegacyShadeTracking(false)
            runCurrent()

            // THEN user is not interacting
            assertThat(actual).isFalse()

            // WHEN shade tracking starts
            shadeRepository.setLegacyShadeTracking(true)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN shade dragged down halfway
            shadeRepository.setLegacyShadeExpansion(.5f)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN shade fully expanded but tracking is not stopped
            shadeRepository.setLegacyShadeExpansion(1f)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN shade fully collapsed but tracking is not stopped
            shadeRepository.setLegacyShadeExpansion(0f)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN shade dragged halfway and tracking is stopped
            shadeRepository.setLegacyShadeExpansion(.6f)
            shadeRepository.setLegacyShadeTracking(false)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN shade completes expansion stopped
            shadeRepository.setLegacyShadeExpansion(1f)
            runCurrent()

            // THEN user is not interacting
            assertThat(actual).isFalse()
        }

    @Test
    fun userInteractingWithShade_shadeExpanded() =
        testScope.runTest() {
            val actual by collectLastValue(underTest.isUserInteractingWithShade)
            // GIVEN shade collapsed and not tracking input
            shadeRepository.setLegacyShadeExpansion(0f)
            shadeRepository.setLegacyShadeTracking(false)
            runCurrent()

            // THEN user is not interacting
            assertThat(actual).isFalse()

            // WHEN shade tracking starts
            shadeRepository.setLegacyShadeTracking(true)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN shade dragged down halfway
            shadeRepository.setLegacyShadeExpansion(.5f)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN shade fully expanded and tracking is stopped
            shadeRepository.setLegacyShadeExpansion(1f)
            shadeRepository.setLegacyShadeTracking(false)
            runCurrent()

            // THEN user is not interacting
            assertThat(actual).isFalse()
        }

    @Test
    fun userInteractingWithShade_shadePartiallyExpanded() =
        testScope.runTest() {
            val actual by collectLastValue(underTest.isUserInteractingWithShade)
            // GIVEN shade collapsed and not tracking input
            shadeRepository.setLegacyShadeExpansion(0f)
            shadeRepository.setLegacyShadeTracking(false)
            runCurrent()

            // THEN user is not interacting
            assertThat(actual).isFalse()

            // WHEN shade tracking starts
            shadeRepository.setLegacyShadeTracking(true)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN shade partially expanded
            shadeRepository.setLegacyShadeExpansion(.4f)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN tracking is stopped
            shadeRepository.setLegacyShadeTracking(false)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN shade goes back to collapsed
            shadeRepository.setLegacyShadeExpansion(0f)
            runCurrent()

            // THEN user is not interacting
            assertThat(actual).isFalse()
        }

    @Test
    fun userInteractingWithShade_shadeCollapsed() =
        testScope.runTest() {
            val actual by collectLastValue(underTest.isUserInteractingWithShade)
            // GIVEN shade expanded and not tracking input
            shadeRepository.setLegacyShadeExpansion(1f)
            shadeRepository.setLegacyShadeTracking(false)
            runCurrent()

            // THEN user is not interacting
            assertThat(actual).isFalse()

            // WHEN shade tracking starts
            shadeRepository.setLegacyShadeTracking(true)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN shade dragged up halfway
            shadeRepository.setLegacyShadeExpansion(.5f)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN shade fully collapsed and tracking is stopped
            shadeRepository.setLegacyShadeExpansion(0f)
            shadeRepository.setLegacyShadeTracking(false)
            runCurrent()

            // THEN user is not interacting
            assertThat(actual).isFalse()
        }

    @Test
    fun userInteractingWithQs_qsDraggedUpAndDown() =
        testScope.runTest() {
            val actual by collectLastValue(underTest.isUserInteractingWithQs)
            // GIVEN qs collapsed and not tracking input
            shadeRepository.setQsExpansion(0f)
            shadeRepository.setLegacyQsTracking(false)
            runCurrent()

            // THEN user is not interacting
            assertThat(actual).isFalse()

            // WHEN qs tracking starts
            shadeRepository.setLegacyQsTracking(true)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN qs dragged down halfway
            shadeRepository.setQsExpansion(.5f)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN qs fully expanded but tracking is not stopped
            shadeRepository.setQsExpansion(1f)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN qs fully collapsed but tracking is not stopped
            shadeRepository.setQsExpansion(0f)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN qs dragged halfway and tracking is stopped
            shadeRepository.setQsExpansion(.6f)
            shadeRepository.setLegacyQsTracking(false)
            runCurrent()

            // THEN user is interacting
            assertThat(actual).isTrue()

            // WHEN qs completes expansion stopped
            shadeRepository.setQsExpansion(1f)
            runCurrent()

            // THEN user is not interacting
            assertThat(actual).isFalse()
        }
    @Test
    fun userInteracting_idle() =
        testScope.runTest() {
            // GIVEN an interacting flow based on transitions to and from a scene
            val key = SceneKey.Shade
            val interactingFlow = underTest.sceneBasedInteracting(sceneInteractor, key)
            val interacting by collectLastValue(interactingFlow)

            // WHEN transition state is idle
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(ObservableTransitionState.Idle(key))
            sceneInteractor.setTransitionState(transitionState)

            // THEN interacting is false
            assertThat(interacting).isFalse()
        }

    @Test
    fun userInteracting_transitioning_toScene_programmatic() =
        testScope.runTest() {
            // GIVEN an interacting flow based on transitions to and from a scene
            val key = SceneKey.QuickSettings
            val interactingFlow = underTest.sceneBasedInteracting(sceneInteractor, key)
            val interacting by collectLastValue(interactingFlow)

            // WHEN transition state is starting to move to the scene
            val progress = MutableStateFlow(0f)
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = SceneKey.Lockscreen,
                        toScene = key,
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            sceneInteractor.setTransitionState(transitionState)

            // THEN interacting is false
            assertThat(interacting).isFalse()

            // WHEN transition state is partially to the scene
            progress.value = .4f

            // THEN interacting is false
            assertThat(interacting).isFalse()

            // WHEN transition completes
            progress.value = 1f

            // THEN interacting is false
            assertThat(interacting).isFalse()
        }

    @Test
    fun userInteracting_transitioning_toScene_userInputDriven() =
        testScope.runTest() {
            // GIVEN an interacting flow based on transitions to and from a scene
            val key = SceneKey.QuickSettings
            val interactingFlow = underTest.sceneBasedInteracting(sceneInteractor, key)
            val interacting by collectLastValue(interactingFlow)

            // WHEN transition state is starting to move to the scene
            val progress = MutableStateFlow(0f)
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = SceneKey.Lockscreen,
                        toScene = key,
                        progress = progress,
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            sceneInteractor.setTransitionState(transitionState)

            // THEN interacting is true
            assertThat(interacting).isTrue()

            // WHEN transition state is partially to the scene
            progress.value = .4f

            // THEN interacting is true
            assertThat(interacting).isTrue()

            // WHEN transition completes
            progress.value = 1f

            // THEN interacting is true
            assertThat(interacting).isTrue()
        }

    @Test
    fun userInteracting_transitioning_fromScene_programmatic() =
        testScope.runTest() {
            // GIVEN an interacting flow based on transitions to and from a scene
            val key = SceneKey.QuickSettings
            val interactingFlow = underTest.sceneBasedInteracting(sceneInteractor, key)
            val interacting by collectLastValue(interactingFlow)

            // WHEN transition state is starting to move to the scene
            val progress = MutableStateFlow(0f)
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = key,
                        toScene = SceneKey.Lockscreen,
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            sceneInteractor.setTransitionState(transitionState)

            // THEN interacting is false
            assertThat(interacting).isFalse()

            // WHEN transition state is partially to the scene
            progress.value = .4f

            // THEN interacting is false
            assertThat(interacting).isFalse()

            // WHEN transition completes
            progress.value = 1f

            // THEN interacting is false
            assertThat(interacting).isFalse()
        }

    @Test
    fun userInteracting_transitioning_fromScene_userInputDriven() =
        testScope.runTest() {
            // GIVEN an interacting flow based on transitions to and from a scene
            val key = SceneKey.QuickSettings
            val interactingFlow = underTest.sceneBasedInteracting(sceneInteractor, key)
            val interacting by collectLastValue(interactingFlow)

            // WHEN transition state is starting to move to the scene
            val progress = MutableStateFlow(0f)
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = key,
                        toScene = SceneKey.Lockscreen,
                        progress = progress,
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            sceneInteractor.setTransitionState(transitionState)

            // THEN interacting is true
            assertThat(interacting).isTrue()

            // WHEN transition state is partially to the scene
            progress.value = .4f

            // THEN interacting is true
            assertThat(interacting).isTrue()

            // WHEN transition completes
            progress.value = 1f

            // THEN interacting is true
            assertThat(interacting).isTrue()
        }

    @Test
    fun userInteracting_transitioning_toAndFromDifferentScenes() =
        testScope.runTest() {
            // GIVEN an interacting flow based on transitions to and from a scene
            val interactingFlow = underTest.sceneBasedInteracting(sceneInteractor, SceneKey.Shade)
            val interacting by collectLastValue(interactingFlow)

            // WHEN transition state is starting to between different scenes
            val progress = MutableStateFlow(0f)
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = SceneKey.Lockscreen,
                        toScene = SceneKey.QuickSettings,
                        progress = MutableStateFlow(0f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            sceneInteractor.setTransitionState(transitionState)

            // THEN interacting is false
            assertThat(interacting).isFalse()
        }

    @Test
    fun isShadeTouchable_isFalse_whenFrpIsActive() =
        testScope.runTest {
            deviceProvisioningRepository.setFactoryResetProtectionActive(true)
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                )
            )
            val isShadeTouchable by collectLastValue(underTest.isShadeTouchable)
            runCurrent()
            assertThat(isShadeTouchable).isFalse()
        }

    @Test
    fun isShadeTouchable_isFalse_whenDeviceAsleepAndNotPulsing() =
        testScope.runTest {
            powerRepository.updateWakefulness(
                rawState = WakefulnessState.ASLEEP,
                lastWakeReason = WakeSleepReason.POWER_BUTTON,
                lastSleepReason = WakeSleepReason.OTHER,
            )
            // goingToSleep == false
            // TODO: remove?
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = KeyguardState.GONE,
                    to = KeyguardState.LOCKSCREEN,
                    transitionState = TransitionState.STARTED,
                )
            )
            keyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(
                    to = DozeStateModel.DOZE_AOD,
                )
            )
            val isShadeTouchable by collectLastValue(underTest.isShadeTouchable)
            runCurrent()
            assertThat(isShadeTouchable).isFalse()
        }

    @Test
    fun isShadeTouchable_isTrue_whenDeviceAsleepAndPulsing() =
        testScope.runTest {
            powerRepository.updateWakefulness(
                rawState = WakefulnessState.ASLEEP,
                lastWakeReason = WakeSleepReason.POWER_BUTTON,
                lastSleepReason = WakeSleepReason.OTHER,
            )
            // goingToSleep == false
            // TODO: remove?
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = KeyguardState.GONE,
                    to = KeyguardState.LOCKSCREEN,
                    transitionState = TransitionState.STARTED,
                )
            )
            keyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(
                    to = DozeStateModel.DOZE_PULSING,
                )
            )
            val isShadeTouchable by collectLastValue(underTest.isShadeTouchable)
            runCurrent()
            assertThat(isShadeTouchable).isTrue()
        }

    @Test
    fun isShadeTouchable_isFalse_whenStartingToSleepAndNotControlScreenOff() =
        testScope.runTest {
            powerRepository.updateWakefulness(
                rawState = WakefulnessState.STARTING_TO_SLEEP,
                lastWakeReason = WakeSleepReason.POWER_BUTTON,
                lastSleepReason = WakeSleepReason.OTHER,
            )
            // goingToSleep == true
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = KeyguardState.GONE,
                    to = KeyguardState.AOD,
                    transitionState = TransitionState.STARTED,
                )
            )
            whenever(dozeParameters.shouldControlScreenOff()).thenReturn(false)
            val isShadeTouchable by collectLastValue(underTest.isShadeTouchable)
            runCurrent()
            assertThat(isShadeTouchable).isFalse()
        }

    @Test
    fun isShadeTouchable_isTrue_whenStartingToSleepAndControlScreenOff() =
        testScope.runTest {
            powerRepository.updateWakefulness(
                rawState = WakefulnessState.STARTING_TO_SLEEP,
                lastWakeReason = WakeSleepReason.POWER_BUTTON,
                lastSleepReason = WakeSleepReason.OTHER,
            )
            // goingToSleep == true
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = KeyguardState.GONE,
                    to = KeyguardState.AOD,
                    transitionState = TransitionState.STARTED,
                )
            )
            whenever(dozeParameters.shouldControlScreenOff()).thenReturn(true)
            val isShadeTouchable by collectLastValue(underTest.isShadeTouchable)
            runCurrent()
            assertThat(isShadeTouchable).isTrue()
        }

    @Test
    fun isShadeTouchable_isTrue_whenNotAsleep() =
        testScope.runTest {
            powerRepository.updateWakefulness(
                rawState = WakefulnessState.AWAKE,
                lastWakeReason = WakeSleepReason.POWER_BUTTON,
                lastSleepReason = WakeSleepReason.OTHER,
            )
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                )
            )
            val isShadeTouchable by collectLastValue(underTest.isShadeTouchable)
            runCurrent()
            assertThat(isShadeTouchable).isTrue()
        }

    @SysUISingleton
    @Component(
        modules =
            [
                SysUITestModule::class,
                UserDomainLayerModule::class,
            ]
    )
    interface TestComponent {

        val underTest: ShadeInteractor

        val configurationRepository: FakeConfigurationRepository
        val deviceProvisioningRepository: FakeDeviceProvisioningRepository
        val disableFlagsRepository: FakeDisableFlagsRepository
        val keyguardRepository: FakeKeyguardRepository
        val keygaurdTransitionRepository: FakeKeyguardTransitionRepository
        val powerRepository: FakePowerRepository
        val sceneInteractor: SceneInteractor
        val shadeRepository: FakeShadeRepository
        val testScope: TestScope
        val userRepository: FakeUserRepository
        val userSetupRepository: FakeUserSetupRepository

        @Component.Factory
        interface Factory {
            fun create(
                @BindsInstance test: SysuiTestCase,
                featureFlags: FakeFeatureFlagsClassicModule,
                mocks: TestMocksModule,
            ): TestComponent
        }
    }
}
