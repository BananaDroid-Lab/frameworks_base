/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.wmshell;

import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.pm.PackageManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.TestableContext;

import androidx.test.runner.AndroidJUnit4;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.keyguard.ScreenLifecycle;
import com.android.systemui.model.SysUiState;
import com.android.systemui.navigationbar.NavigationModeController;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.tracing.ProtoTracer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.onehanded.OneHanded;
import com.android.wm.shell.onehanded.OneHandedGestureHandler;
import com.android.wm.shell.onehanded.OneHandedTransitionCallback;
import com.android.wm.shell.pip.Pip;
import com.android.wm.shell.pip.phone.PipTouchHandler;
import com.android.wm.shell.splitscreen.SplitScreen;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WMShellTest extends SysuiTestCase {
    InputConsumerController mInputConsumerController;
    WMShell mWMShell;

    @Mock CommandQueue mCommandQueue;
    @Mock ConfigurationController mConfigurationController;
    @Mock KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    @Mock ActivityManagerWrapper mActivityManagerWrapper;
    @Mock DisplayImeController mDisplayImeController;
    @Mock InputConsumerController mMockInputConsumerController;
    @Mock NavigationModeController mNavigationModeController;
    @Mock ScreenLifecycle mScreenLifecycle;
    @Mock SysUiState mSysUiState;
    @Mock Pip mPip;
    @Mock PipTouchHandler mPipTouchHandler;
    @Mock SplitScreen mSplitScreen;
    @Mock OneHanded mOneHanded;
    @Mock ShellTaskOrganizer mTaskOrganizer;
    @Mock ProtoTracer mProtoTracer;
    @Mock PackageManager mMockPackageManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mInputConsumerController = InputConsumerController.getPipInputConsumer();

        mWMShell = new WMShell(mContext, mCommandQueue, mConfigurationController,
                mInputConsumerController, mKeyguardUpdateMonitor, mActivityManagerWrapper,
                mDisplayImeController, mNavigationModeController, mScreenLifecycle, mSysUiState,
                Optional.of(mPip), Optional.of(mSplitScreen), Optional.of(mOneHanded),
                mTaskOrganizer, mProtoTracer);

        when(mPip.getPipTouchHandler()).thenReturn(mPipTouchHandler);

    }

    @Test
    public void start_startsMonitorDisplays() {
        mWMShell.start();

        verify(mDisplayImeController).startMonitorDisplays();
    }

    @Test
    public void initPip_registersCommandQueueCallback() {
        mWMShell.initPip(mPip);

        verify(mCommandQueue).addCallback(any(CommandQueue.Callbacks.class));
    }

    @Test
    public void nonPipDevice_shouldNotInitPip() {
        final TestableContext nonPipContext = getNonPipFeatureContext();
        final WMShell nonPipWMShell = new WMShell(nonPipContext, mCommandQueue,
                mConfigurationController, mMockInputConsumerController, mKeyguardUpdateMonitor,
                mActivityManagerWrapper, mDisplayImeController, mNavigationModeController,
                mScreenLifecycle, mSysUiState, Optional.of(mPip), Optional.of(mSplitScreen),
                Optional.of(mOneHanded), mTaskOrganizer, mProtoTracer);
        nonPipWMShell.initPip(mPip);

        verify(mCommandQueue, never()).addCallback(any());
        verify(mKeyguardUpdateMonitor, never()).registerCallback(any());
        verify(mConfigurationController, never()).addCallback(any());
        verify(mSysUiState, never()).addCallback(any());
        verify(mActivityManagerWrapper, never()).registerTaskStackListener(any());
        verify(mMockInputConsumerController, never()).setInputListener(any());
        verify(mMockInputConsumerController, never()).setRegistrationListener(any());
        verify(mPip, never()).registerSessionListenerForCurrentUser();
    }

    @Test
    public void initSplitScreen_registersCallbacks() {
        mWMShell.initSplitScreen(mSplitScreen);

        verify(mKeyguardUpdateMonitor).registerCallback(any(KeyguardUpdateMonitorCallback.class));
        verify(mActivityManagerWrapper).registerTaskStackListener(
                any(TaskStackChangeListener.class));
    }

    @Test
    public void initOneHanded_registersCallbacks() {
        mWMShell.initOneHanded(mOneHanded);

        verify(mKeyguardUpdateMonitor).registerCallback(any(KeyguardUpdateMonitorCallback.class));
        verify(mCommandQueue).addCallback(any(CommandQueue.Callbacks.class));
        verify(mScreenLifecycle).addObserver(any(ScreenLifecycle.Observer.class));
        verify(mNavigationModeController).addListener(
                any(NavigationModeController.ModeChangedListener.class));
        verify(mActivityManagerWrapper).registerTaskStackListener(
                any(TaskStackChangeListener.class));

        verify(mOneHanded).registerGestureCallback(any(
                OneHandedGestureHandler.OneHandedGestureEventCallback.class));
        verify(mOneHanded).registerTransitionCallback(any(OneHandedTransitionCallback.class));
    }

    TestableContext getNonPipFeatureContext() {
        TestableContext spiedContext = spy(mContext);
        when(mMockPackageManager.hasSystemFeature(FEATURE_PICTURE_IN_PICTURE)).thenReturn(false);
        when(spiedContext.getPackageManager()).thenReturn(mMockPackageManager);
        return spiedContext;
    }
}
