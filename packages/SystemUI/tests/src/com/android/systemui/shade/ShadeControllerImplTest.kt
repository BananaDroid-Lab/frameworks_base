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

package com.android.systemui.shade

import android.testing.AndroidTestingRunner
import android.view.Display
import android.view.WindowManager
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.assist.AssistManager
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.NotificationShadeWindowController
import com.android.systemui.statusbar.notification.row.NotificationGutsManager
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.statusbar.window.StatusBarWindowController
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.mockito.whenever
import com.android.systemui.util.time.FakeSystemClock
import dagger.Lazy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@RunWith(AndroidTestingRunner::class)
@SmallTest
class ShadeControllerImplTest : SysuiTestCase() {
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var keyguardStateController: KeyguardStateController
    @Mock private lateinit var statusBarStateController: StatusBarStateController
    @Mock private lateinit var statusBarKeyguardViewManager: StatusBarKeyguardViewManager
    @Mock private lateinit var statusBarWindowController: StatusBarWindowController
    @Mock private lateinit var deviceProvisionedController: DeviceProvisionedController
    @Mock private lateinit var notificationShadeWindowController: NotificationShadeWindowController
    @Mock private lateinit var windowManager: WindowManager
    @Mock private lateinit var assistManager: AssistManager
    @Mock private lateinit var gutsManager: NotificationGutsManager
    @Mock private lateinit var notificationPanelViewController: NotificationPanelViewController
    @Mock private lateinit var nswvc: NotificationShadeWindowViewController
    @Mock private lateinit var display: Display

    private lateinit var shadeController: ShadeControllerImpl

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        whenever(windowManager.defaultDisplay).thenReturn(display)
        whenever(deviceProvisionedController.isCurrentUserSetup).thenReturn(true)
        shadeController =
            ShadeControllerImpl(
                commandQueue,
                FakeExecutor(FakeSystemClock()),
                keyguardStateController,
                statusBarStateController,
                statusBarKeyguardViewManager,
                statusBarWindowController,
                deviceProvisionedController,
                notificationShadeWindowController,
                windowManager,
                Lazy { assistManager },
                Lazy { gutsManager },
            )
        shadeController.setNotificationShadeWindowViewController(nswvc)
        shadeController.setNotificationPanelViewController(notificationPanelViewController)
    }

    @Test
    fun testDisableNotificationShade() {
        whenever(commandQueue.panelsEnabled()).thenReturn(false)

        // Trying to open it does nothing.
        shadeController.animateExpandShade()
        verify(notificationPanelViewController, never()).expandToNotifications()
        shadeController.animateExpandQs()
        verify(notificationPanelViewController, never()).expand(ArgumentMatchers.anyBoolean())
    }

    @Test
    fun testEnableNotificationShade() {
        whenever(commandQueue.panelsEnabled()).thenReturn(true)

        // Can now be opened.
        shadeController.animateExpandShade()
        verify(notificationPanelViewController).expandToNotifications()
        shadeController.animateExpandQs()
        verify(notificationPanelViewController).expandToQs()
    }

    @Test
    fun cancelExpansionAndCollapseShade_callsCancelCurrentTouch() {
        // GIVEN the shade is tracking a touch
        whenever(notificationPanelViewController.isTracking).thenReturn(true)

        // WHEN cancelExpansionAndCollapseShade is called
        shadeController.cancelExpansionAndCollapseShade()

        // VERIFY that cancelCurrentTouch is called
        verify(nswvc).cancelCurrentTouch()
    }

    @Test
    fun cancelExpansionAndCollapseShade_doesNotCallAnimateCollapseShade_whenCollapsed() {
        // GIVEN the shade is tracking a touch
        whenever(notificationPanelViewController.isTracking).thenReturn(false)

        // WHEN cancelExpansionAndCollapseShade is called
        shadeController.cancelExpansionAndCollapseShade()

        // VERIFY that cancelCurrentTouch is NOT called
        verify(nswvc, never()).cancelCurrentTouch()
    }
}
