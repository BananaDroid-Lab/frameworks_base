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
 * limitations under the License.
 */

package com.android.systemui.biometrics

import android.os.Handler
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.keyguard.KeyguardSecurityModel
import com.android.systemui.bouncer.data.repository.KeyguardBouncerRepository
import com.android.systemui.bouncer.data.repository.KeyguardBouncerRepositoryImpl
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor
import com.android.systemui.bouncer.domain.interactor.PrimaryBouncerCallbackInteractor
import com.android.systemui.bouncer.domain.interactor.PrimaryBouncerInteractor
import com.android.systemui.bouncer.shared.constants.KeyguardBouncerConstants
import com.android.systemui.bouncer.ui.BouncerView
import com.android.systemui.classifier.FalsingCollector
import com.android.systemui.keyguard.DismissCallbackRegistry
import com.android.systemui.keyguard.data.repository.BiometricSettingsRepository
import com.android.systemui.keyguard.data.repository.FakeTrustRepository
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.statusbar.StatusBarState
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.util.mockito.whenever
import com.android.systemui.util.time.FakeSystemClock
import com.android.systemui.util.time.SystemClock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
@SmallTest
@TestableLooper.RunWithLooper
@kotlinx.coroutines.ExperimentalCoroutinesApi
class UdfpsKeyguardViewLegacyControllerWithCoroutinesTest :
    UdfpsKeyguardViewLegacyControllerBaseTest() {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var keyguardBouncerRepository: KeyguardBouncerRepository

    @Mock private lateinit var bouncerLogger: TableLogBuffer

    @Before
    override fun setUp() {
        allowTestableLooperAsMainThread() // repeatWhenAttached requires the main thread
        MockitoAnnotations.initMocks(this)
        keyguardBouncerRepository =
            KeyguardBouncerRepositoryImpl(
                FakeSystemClock(),
                testScope.backgroundScope,
                bouncerLogger,
            )
        super.setUp()
    }

    override fun createUdfpsKeyguardViewController(): UdfpsKeyguardViewControllerLegacy {
        mPrimaryBouncerInteractor =
            PrimaryBouncerInteractor(
                keyguardBouncerRepository,
                mock(BouncerView::class.java),
                mock(Handler::class.java),
                mKeyguardStateController,
                mock(KeyguardSecurityModel::class.java),
                mock(PrimaryBouncerCallbackInteractor::class.java),
                mock(FalsingCollector::class.java),
                mock(DismissCallbackRegistry::class.java),
                context,
                mKeyguardUpdateMonitor,
                FakeTrustRepository(),
                testScope.backgroundScope,
            )
        mAlternateBouncerInteractor =
            AlternateBouncerInteractor(
                mock(StatusBarStateController::class.java),
                mock(KeyguardStateController::class.java),
                keyguardBouncerRepository,
                mock(BiometricSettingsRepository::class.java),
                mock(SystemClock::class.java),
                mKeyguardUpdateMonitor,
            )
        return createUdfpsKeyguardViewController(
            /* useModernBouncer */ true, /* useExpandedOverlay */
            false
        )
    }

    @Test
    fun bouncerExpansionChange_fadeIn() =
        testScope.runTest {
            // GIVEN view is attached
            mController.onViewAttached()
            captureKeyguardStateControllerCallback()
            Mockito.reset(mView)

            // WHEN status bar expansion is 0
            val job = mController.listenForBouncerExpansion(this)
            keyguardBouncerRepository.setPrimaryShow(true)
            keyguardBouncerRepository.setPanelExpansion(KeyguardBouncerConstants.EXPANSION_VISIBLE)
            runCurrent()

            // THEN alpha is 0
            verify(mView).unpausedAlpha = 0

            job.cancel()
        }

    @Test
    fun bouncerExpansionChange_pauseAuth() =
        testScope.runTest {
            // GIVEN view is attached + on the keyguard
            mController.onViewAttached()
            captureStatusBarStateListeners()
            sendStatusBarStateChanged(StatusBarState.KEYGUARD)
            Mockito.reset(mView)

            // WHEN panelViewExpansion changes to hide
            whenever(mView.unpausedAlpha).thenReturn(0)
            val job = mController.listenForBouncerExpansion(this)
            keyguardBouncerRepository.setPrimaryShow(true)
            keyguardBouncerRepository.setPanelExpansion(KeyguardBouncerConstants.EXPANSION_VISIBLE)
            runCurrent()

            // THEN pause auth is updated to PAUSE
            verify(mView, Mockito.atLeastOnce()).setPauseAuth(true)

            job.cancel()
        }

    @Test
    fun bouncerExpansionChange_unpauseAuth() =
        testScope.runTest {
            // GIVEN view is attached + on the keyguard + panel expansion is 0f
            mController.onViewAttached()
            captureStatusBarStateListeners()
            sendStatusBarStateChanged(StatusBarState.KEYGUARD)
            Mockito.reset(mView)

            // WHEN panelViewExpansion changes to expanded
            whenever(mView.unpausedAlpha).thenReturn(255)
            val job = mController.listenForBouncerExpansion(this)
            keyguardBouncerRepository.setPrimaryShow(true)
            keyguardBouncerRepository.setPanelExpansion(KeyguardBouncerConstants.EXPANSION_HIDDEN)
            runCurrent()

            // THEN pause auth is updated to NOT pause
            verify(mView, Mockito.atLeastOnce()).setPauseAuth(false)

            job.cancel()
        }

    @Test
    fun shadeLocked_showAlternateBouncer_unpauseAuth() =
        testScope.runTest {
            // GIVEN view is attached + on the SHADE_LOCKED (udfps view not showing)
            mController.onViewAttached()
            captureStatusBarStateListeners()
            sendStatusBarStateChanged(StatusBarState.SHADE_LOCKED)

            // WHEN alternate bouncer is requested
            val job = mController.listenForAlternateBouncerVisibility(this)
            keyguardBouncerRepository.setAlternateVisible(true)
            runCurrent()

            // THEN udfps view will animate in & pause auth is updated to NOT pause
            verify(mView).animateInUdfpsBouncer(any())
            assertFalse(mController.shouldPauseAuth())

            job.cancel()
        }

    /** After migration to MODERN_BOUNCER, replaces UdfpsKeyguardViewControllerTest version */
    @Test
    fun shouldPauseAuthBouncerShowing() =
        testScope.runTest {
            // GIVEN view attached and we're on the keyguard
            mController.onViewAttached()
            captureStatusBarStateListeners()
            sendStatusBarStateChanged(StatusBarState.KEYGUARD)

            // WHEN the bouncer expansion is VISIBLE
            val job = mController.listenForBouncerExpansion(this)
            keyguardBouncerRepository.setPrimaryShow(true)
            keyguardBouncerRepository.setPanelExpansion(KeyguardBouncerConstants.EXPANSION_VISIBLE)
            runCurrent()

            // THEN UDFPS shouldPauseAuth == true
            assertTrue(mController.shouldPauseAuth())

            job.cancel()
        }

    @Test
    fun fadeFromDialogSuggestedAlpha() =
        testScope.runTest {
            // GIVEN view is attached and status bar expansion is 1f
            mController.onViewAttached()
            captureStatusBarStateListeners()
            val job = mController.listenForBouncerExpansion(this)
            keyguardBouncerRepository.setPrimaryShow(true)
            keyguardBouncerRepository.setPanelExpansion(KeyguardBouncerConstants.EXPANSION_HIDDEN)
            runCurrent()
            Mockito.reset(mView)

            // WHEN dialog suggested alpha is .6f
            whenever(mView.dialogSuggestedAlpha).thenReturn(.6f)
            sendStatusBarStateChanged(StatusBarState.KEYGUARD)

            // THEN alpha is updated based on dialog suggested alpha
            verify(mView).unpausedAlpha = (.6f * 255).toInt()

            job.cancel()
        }

    @Test
    fun transitionToFullShadeProgress() =
        testScope.runTest {
            // GIVEN view is attached and status bar expansion is 1f
            mController.onViewAttached()
            val job = mController.listenForBouncerExpansion(this)
            keyguardBouncerRepository.setPrimaryShow(true)
            keyguardBouncerRepository.setPanelExpansion(KeyguardBouncerConstants.EXPANSION_HIDDEN)
            runCurrent()
            Mockito.reset(mView)
            whenever(mView.dialogSuggestedAlpha).thenReturn(1f)

            // WHEN we're transitioning to the full shade
            val transitionProgress = .6f
            mController.setTransitionToFullShadeProgress(transitionProgress)

            // THEN alpha is between 0 and 255
            verify(mView).unpausedAlpha = ((1f - transitionProgress) * 255).toInt()

            job.cancel()
        }
}
