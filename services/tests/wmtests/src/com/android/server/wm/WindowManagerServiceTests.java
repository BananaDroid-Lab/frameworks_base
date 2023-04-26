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

package com.android.server.wm;

import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_FOCUS;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.FLAG_OWN_FOCUS;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static android.view.WindowManager.LayoutParams.INPUT_FEATURE_SPY;
import static android.view.WindowManager.LayoutParams.INVALID_WINDOW_TYPE;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_TOAST;
import static android.window.DisplayAreaOrganizer.FEATURE_VENDOR_FIRST;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.wm.LetterboxConfiguration.LETTERBOX_BACKGROUND_APP_COLOR_BACKGROUND;
import static com.android.server.wm.LetterboxConfiguration.LETTERBOX_BACKGROUND_APP_COLOR_BACKGROUND_FLOATING;
import static com.android.server.wm.LetterboxConfiguration.LETTERBOX_BACKGROUND_SOLID_COLOR;
import static com.android.server.wm.LetterboxConfiguration.LETTERBOX_BACKGROUND_WALLPAPER;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.InputConfig;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;
import android.util.DisplayMetrics;
import android.util.MergedConfiguration;
import android.view.IWindow;
import android.view.IWindowSessionCallback;
import android.view.InputChannel;
import android.view.InsetsSourceControl;
import android.view.InsetsState;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.window.ClientWindowFrames;
import android.window.ScreenCapture;
import android.window.WindowContainerToken;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

/**
 * Build/Install/Run:
 * atest WmTests:WindowManagerServiceTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class WindowManagerServiceTests extends WindowTestsBase {

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ADD_TRUSTED_DISPLAY);

    @Test
    public void testIsRequestedOrientationMapped() {
        mWm.setOrientationRequestPolicy(/* isIgnoreOrientationRequestDisabled*/ true,
                /* fromOrientations */ new int[]{1}, /* toOrientations */ new int[]{2});
        assertThat(mWm.mapOrientationRequest(1)).isEqualTo(2);
        assertThat(mWm.mapOrientationRequest(3)).isEqualTo(3);

        // Mapping disabled
        mWm.setOrientationRequestPolicy(/* isIgnoreOrientationRequestDisabled*/ false,
                /* fromOrientations */ null, /* toOrientations */ null);
        assertThat(mWm.mapOrientationRequest(1)).isEqualTo(1);
        assertThat(mWm.mapOrientationRequest(3)).isEqualTo(3);
    }

    @Test
    public void testAddWindowToken() {
        IBinder token = mock(IBinder.class);
        mWm.addWindowToken(token, TYPE_TOAST, mDisplayContent.getDisplayId(), null /* options */);

        WindowToken windowToken = mWm.mRoot.getWindowToken(token);
        assertFalse(windowToken.mRoundedCornerOverlay);
        assertFalse(windowToken.isFromClient());
    }

    @Test
    public void testTaskFocusChange_rootTaskNotHomeType_focusChanges() throws RemoteException {
        DisplayContent display = createNewDisplay();
        // Current focused window
        Task focusedRootTask = createTask(
                display, WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
        Task focusedTask = createTaskInRootTask(focusedRootTask, 0 /* userId */);
        WindowState focusedWindow = createAppWindow(focusedTask, TYPE_APPLICATION, "App Window");
        mDisplayContent.mCurrentFocus = focusedWindow;
        // Tapped task
        Task tappedRootTask = createTask(
                display, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        Task tappedTask = createTaskInRootTask(tappedRootTask, 0 /* userId */);
        spyOn(mWm.mAtmService);

        mWm.handleTaskFocusChange(tappedTask, null /* window */);

        verify(mWm.mAtmService).setFocusedTask(tappedTask.mTaskId, null);
    }

    @Test
    public void testTaskFocusChange_rootTaskHomeTypeWithSameTaskDisplayArea_focusDoesNotChange()
            throws RemoteException {
        DisplayContent display = createNewDisplay();
        // Current focused window
        Task focusedRootTask = createTask(
                display, WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
        Task focusedTask = createTaskInRootTask(focusedRootTask, 0 /* userId */);
        WindowState focusedWindow = createAppWindow(focusedTask, TYPE_APPLICATION, "App Window");
        mDisplayContent.mCurrentFocus = focusedWindow;
        // Tapped home task
        Task tappedRootTask = createTask(
                display, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME);
        Task tappedTask = createTaskInRootTask(tappedRootTask, 0 /* userId */);
        spyOn(mWm.mAtmService);

        mWm.handleTaskFocusChange(tappedTask, null /* window */);

        verify(mWm.mAtmService, never()).setFocusedTask(tappedTask.mTaskId, null);
    }

    @Test
    public void testTaskFocusChange_rootTaskHomeTypeWithDifferentTaskDisplayArea_focusChanges()
            throws RemoteException {
        final DisplayContent display = createNewDisplay();
        final TaskDisplayArea secondTda = createTaskDisplayArea(
                display, mWm, "Tapped TDA", FEATURE_VENDOR_FIRST);
        // Current focused window
        Task focusedRootTask = createTask(
                display, WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
        Task focusedTask = createTaskInRootTask(focusedRootTask, 0 /* userId */);
        WindowState focusedWindow = createAppWindow(focusedTask, TYPE_APPLICATION, "App Window");
        mDisplayContent.mCurrentFocus = focusedWindow;
        // Tapped home task on another task display area
        Task tappedRootTask = createTask(secondTda, WINDOWING_MODE_FULLSCREEN,
                ACTIVITY_TYPE_STANDARD);
        Task tappedTask = createTaskInRootTask(tappedRootTask, 0 /* userId */);
        spyOn(mWm.mAtmService);

        mWm.handleTaskFocusChange(tappedTask, null /* window */);

        verify(mWm.mAtmService).setFocusedTask(tappedTask.mTaskId, null);
    }

    @Test
    public void testDismissKeyguardCanWakeUp() {
        doReturn(true).when(mWm).checkCallingPermission(anyString(), anyString());
        doReturn(true).when(mWm.mAtmService.mKeyguardController).isShowingDream();
        doNothing().when(mWm.mAtmService.mTaskSupervisor).wakeUp(anyString());
        mWm.dismissKeyguard(null, "test-dismiss-keyguard");
        verify(mWm.mAtmService.mTaskSupervisor).wakeUp(anyString());
    }

    @Test
    public void testRelayoutExitingWindow() {
        final WindowState win = createWindow(null, TYPE_BASE_APPLICATION, "appWin");
        final WindowSurfaceController surfaceController = mock(WindowSurfaceController.class);
        win.mWinAnimator.mSurfaceController = surfaceController;
        win.mWinAnimator.mDrawState = WindowStateAnimator.HAS_DRAWN;
        doReturn(true).when(surfaceController).hasSurface();
        spyOn(win.mTransitionController);
        doReturn(true).when(win.mTransitionController).isShellTransitionsEnabled();
        doReturn(true).when(win.mTransitionController).inTransition(
                eq(win.mActivityRecord));
        win.mViewVisibility = View.VISIBLE;
        win.mHasSurface = true;
        win.mActivityRecord.mAppStopped = true;
        mWm.mWindowMap.put(win.mClient.asBinder(), win);
        spyOn(mWm.mWindowPlacerLocked);
        // Skip unnecessary operations of relayout.
        doNothing().when(mWm.mWindowPlacerLocked).performSurfacePlacement(anyBoolean());
        final int w = 100;
        final int h = 200;
        final ClientWindowFrames outFrames = new ClientWindowFrames();
        final MergedConfiguration outConfig = new MergedConfiguration();
        final SurfaceControl outSurfaceControl = new SurfaceControl();
        final InsetsState outInsetsState = new InsetsState();
        final InsetsSourceControl.Array outControls = new InsetsSourceControl.Array();
        final Bundle outBundle = new Bundle();
        mWm.relayoutWindow(win.mSession, win.mClient, win.mAttrs, w, h, View.GONE, 0, 0, 0,
                outFrames, outConfig, outSurfaceControl, outInsetsState, outControls, outBundle);
        // The window is in transition, so its destruction is deferred.
        assertTrue(win.mAnimatingExit);
        assertFalse(win.mDestroying);
        assertTrue(win.mTransitionController.mAnimatingExitWindows.contains(win));

        win.mAnimatingExit = false;
        win.mViewVisibility = View.VISIBLE;
        win.mActivityRecord.setVisibleRequested(false);
        win.mActivityRecord.setVisible(false);
        mWm.relayoutWindow(win.mSession, win.mClient, win.mAttrs, w, h, View.GONE, 0, 0, 0,
                outFrames, outConfig, outSurfaceControl, outInsetsState, outControls, outBundle);
        // Because the window is already invisible, it doesn't need to apply exiting animation
        // and WMS#tryStartExitingAnimation() will destroy the surface directly.
        assertFalse(win.mAnimatingExit);
        assertFalse(win.mHasSurface);
        assertNull(win.mWinAnimator.mSurfaceController);

        doReturn(mSystemServicesTestRule.mTransaction).when(SurfaceControl::getGlobalTransaction);
        // Invisible requested activity should not get the last config even if its view is visible.
        mWm.relayoutWindow(win.mSession, win.mClient, win.mAttrs, w, h, View.VISIBLE, 0, 0, 0,
                outFrames, outConfig, outSurfaceControl, outInsetsState, outControls, outBundle);
        assertEquals(0, outConfig.getMergedConfiguration().densityDpi);
        // Non activity window can still get the last config.
        win.mActivityRecord = null;
        win.fillClientWindowFramesAndConfiguration(outFrames, outConfig,
                false /* useLatestConfig */, true /* relayoutVisible */);
        assertEquals(win.getConfiguration().densityDpi,
                outConfig.getMergedConfiguration().densityDpi);
    }

    @Test
    public void testRelayout_firstLayout_dwpcHelperCalledWithCorrectFlags() {
        // When doing the first layout, the initial flags should be reported as changed to
        // keepActivityOnWindowFlagsChanged.
        testRelayoutFlagChanges(
                /*firstRelayout=*/ true,
                /*startFlags=*/ FLAG_SECURE,
                /*startPrivateFlags=*/ PRIVATE_FLAG_TRUSTED_OVERLAY,
                /*newFlags=*/ FLAG_SECURE,
                /*newPrivateFlags=*/ PRIVATE_FLAG_TRUSTED_OVERLAY,
                /*expectedChangedFlags=*/ FLAG_SECURE,
                /*expectedChangedPrivateFlags=*/ PRIVATE_FLAG_TRUSTED_OVERLAY,
                /*expectedFlagsValue=*/ FLAG_SECURE,
                /*expectedPrivateFlagsValue=*/ PRIVATE_FLAG_TRUSTED_OVERLAY);
    }

    @Test
    public void testRelayout_secondLayoutFlagAdded_dwpcHelperCalledWithCorrectFlags() {
        testRelayoutFlagChanges(
                /*firstRelayout=*/ false,
                /*startFlags=*/ 0,
                /*startPrivateFlags=*/ 0,
                /*newFlags=*/ FLAG_SECURE,
                /*newPrivateFlags=*/ 0,
                /*expectedChangedFlags=*/ FLAG_SECURE,
                /*expectedChangedPrivateFlags=*/ 0,
                /*expectedFlagsValue=*/ FLAG_SECURE,
                /*expectedPrivateFlagsValue=*/ 0);
    }

    @Test
    public void testRelayout_secondLayoutMultipleFlagsAddOne_dwpcHelperCalledWithCorrectFlags() {
        testRelayoutFlagChanges(
                /*firstRelayout=*/ false,
                /*startFlags=*/ FLAG_NOT_FOCUSABLE,
                /*startPrivateFlags=*/ 0,
                /*newFlags=*/ FLAG_SECURE | FLAG_NOT_FOCUSABLE,
                /*newPrivateFlags=*/ 0,
                /*expectedChangedFlags=*/ FLAG_SECURE,
                /*expectedChangedPrivateFlags=*/ 0,
                /*expectedFlagsValue=*/ FLAG_SECURE | FLAG_NOT_FOCUSABLE,
                /*expectedPrivateFlagsValue=*/ 0);
    }

    @Test
    public void testRelayout_secondLayoutPrivateFlagAdded_dwpcHelperCalledWithCorrectFlags() {
        testRelayoutFlagChanges(
                /*firstRelayout=*/ false,
                /*startFlags=*/ 0,
                /*startPrivateFlags=*/ 0,
                /*newFlags=*/ 0,
                /*newPrivateFlags=*/ PRIVATE_FLAG_TRUSTED_OVERLAY,
                /*expectedChangedFlags=*/ 0,
                /*expectedChangedPrivateFlags=*/ PRIVATE_FLAG_TRUSTED_OVERLAY,
                /*expectedFlagsValue=*/ 0,
                /*expectedPrivateFlagsValue=*/ PRIVATE_FLAG_TRUSTED_OVERLAY);
    }

    @Test
    public void testRelayout_secondLayoutFlagsRemoved_dwpcHelperCalledWithCorrectFlags() {
        testRelayoutFlagChanges(
                /*firstRelayout=*/ false,
                /*startFlags=*/ FLAG_SECURE,
                /*startPrivateFlags=*/ PRIVATE_FLAG_TRUSTED_OVERLAY,
                /*newFlags=*/ 0,
                /*newPrivateFlags=*/ 0,
                /*expectedChangedFlags=*/ FLAG_SECURE,
                /*expectedChangedPrivateFlags=*/ PRIVATE_FLAG_TRUSTED_OVERLAY,
                /*expectedFlagsValue=*/ 0,
                /*expectedPrivateFlagsValue=*/ 0);
    }

    // Helper method to test relayout of a window, either for the initial layout, or a subsequent
    // one, and makes sure that the flags and private flags changes and final values are properly
    // reported to mDwpcHelper.keepActivityOnWindowFlagsChanged.
    private void testRelayoutFlagChanges(boolean firstRelayout, int startFlags,
            int startPrivateFlags, int newFlags, int newPrivateFlags, int expectedChangedFlags,
            int expectedChangedPrivateFlags, int expectedFlagsValue,
            int expectedPrivateFlagsValue) {
        final WindowState win = createWindow(null, TYPE_BASE_APPLICATION, "appWin");
        win.mRelayoutCalled = !firstRelayout;
        mWm.mWindowMap.put(win.mClient.asBinder(), win);
        spyOn(mDisplayContent.mDwpcHelper);
        when(mDisplayContent.mDwpcHelper.hasController()).thenReturn(true);

        win.mAttrs.flags = startFlags;
        win.mAttrs.privateFlags = startPrivateFlags;

        LayoutParams newParams = new LayoutParams();
        newParams.copyFrom(win.mAttrs);
        newParams.flags = newFlags;
        newParams.privateFlags = newPrivateFlags;

        int seq = 1;
        if (!firstRelayout) {
            win.mRelayoutSeq = 1;
            seq = 2;
        }
        mWm.relayoutWindow(win.mSession, win.mClient, newParams, 100, 200, View.VISIBLE, 0, seq,
                0, new ClientWindowFrames(), new MergedConfiguration(),
                new SurfaceControl(), new InsetsState(), new InsetsSourceControl.Array(),
                new Bundle());

        ArgumentCaptor<Integer> changedFlags = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> changedPrivateFlags = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> flagsValue = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> privateFlagsValue = ArgumentCaptor.forClass(Integer.class);

        verify(mDisplayContent.mDwpcHelper).keepActivityOnWindowFlagsChanged(
                any(ActivityInfo.class), changedFlags.capture(), changedPrivateFlags.capture(),
                flagsValue.capture(), privateFlagsValue.capture());

        assertThat(changedFlags.getValue()).isEqualTo(expectedChangedFlags);
        assertThat(changedPrivateFlags.getValue()).isEqualTo(expectedChangedPrivateFlags);
        assertThat(flagsValue.getValue()).isEqualTo(expectedFlagsValue);
        assertThat(privateFlagsValue.getValue()).isEqualTo(expectedPrivateFlagsValue);
    }

    @Test
    public void testMoveWindowTokenToDisplay_NullToken_DoNothing() {
        mWm.moveWindowTokenToDisplay(null, mDisplayContent.getDisplayId());

        verify(mDisplayContent, never()).reParentWindowToken(any());
    }

    @Test
    public void testMoveWindowTokenToDisplay_SameDisplay_DoNothing() {
        final WindowToken windowToken = createTestWindowToken(TYPE_INPUT_METHOD_DIALOG,
                mDisplayContent);

        mWm.moveWindowTokenToDisplay(windowToken.token, mDisplayContent.getDisplayId());

        verify(mDisplayContent, never()).reParentWindowToken(any());
    }

    @Test
    public void testMoveWindowTokenToDisplay_DifferentDisplay_DoMoveDisplay() {
        final WindowToken windowToken = createTestWindowToken(TYPE_INPUT_METHOD_DIALOG,
                mDisplayContent);

        mWm.moveWindowTokenToDisplay(windowToken.token, DEFAULT_DISPLAY);

        assertThat(windowToken.getDisplayContent()).isEqualTo(mDefaultDisplay);
    }

    @Test
    public void testAttachWindowContextToWindowToken_InvalidToken_EarlyReturn() {
        spyOn(mWm.mWindowContextListenerController);

        mWm.attachWindowContextToWindowToken(new Binder(), new Binder());

        verify(mWm.mWindowContextListenerController, never()).getWindowType(any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAttachWindowContextToWindowToken_InvalidWindowType_ThrowException() {
        spyOn(mWm.mWindowContextListenerController);

        final WindowToken windowToken = createTestWindowToken(TYPE_INPUT_METHOD, mDefaultDisplay);
        doReturn(INVALID_WINDOW_TYPE).when(mWm.mWindowContextListenerController)
                .getWindowType(any());

        mWm.attachWindowContextToWindowToken(new Binder(), windowToken.token);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAttachWindowContextToWindowToken_DifferentWindowType_ThrowException() {
        spyOn(mWm.mWindowContextListenerController);

        final WindowToken windowToken = createTestWindowToken(TYPE_INPUT_METHOD, mDefaultDisplay);
        doReturn(TYPE_APPLICATION).when(mWm.mWindowContextListenerController)
                .getWindowType(any());

        mWm.attachWindowContextToWindowToken(new Binder(), windowToken.token);
    }

    @Test
    public void testAttachWindowContextToWindowToken_CallerNotValid_EarlyReturn() {
        spyOn(mWm.mWindowContextListenerController);

        final WindowToken windowToken = createTestWindowToken(TYPE_INPUT_METHOD, mDefaultDisplay);
        doReturn(TYPE_INPUT_METHOD).when(mWm.mWindowContextListenerController)
                .getWindowType(any());
        doReturn(false).when(mWm.mWindowContextListenerController)
                .assertCallerCanModifyListener(any(), anyBoolean(), anyInt());

        mWm.attachWindowContextToWindowToken(new Binder(), windowToken.token);

        verify(mWm.mWindowContextListenerController, never()).registerWindowContainerListener(
                any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    public void testAttachWindowContextToWindowToken_CallerValid_DoRegister() {
        spyOn(mWm.mWindowContextListenerController);

        final WindowToken windowToken = createTestWindowToken(TYPE_INPUT_METHOD, mDefaultDisplay);
        doReturn(TYPE_INPUT_METHOD).when(mWm.mWindowContextListenerController)
                .getWindowType(any());
        doReturn(true).when(mWm.mWindowContextListenerController)
                .assertCallerCanModifyListener(any(), anyBoolean(), anyInt());

        final IBinder clientToken = new Binder();
        mWm.attachWindowContextToWindowToken(clientToken, windowToken.token);

        verify(mWm.mWindowContextListenerController).registerWindowContainerListener(
                eq(clientToken), eq(windowToken), anyInt(), eq(TYPE_INPUT_METHOD),
                eq(windowToken.mOptions));
    }

    @Test
    public void testAddWindowWithSubWindowTypeByWindowContext() {
        spyOn(mWm.mWindowContextListenerController);

        final WindowToken windowToken = createTestWindowToken(TYPE_INPUT_METHOD, mDefaultDisplay);
        final Session session = new Session(mWm, new IWindowSessionCallback.Stub() {
            @Override
            public void onAnimatorScaleChanged(float v) throws RemoteException {
            }
        });
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                TYPE_APPLICATION_ATTACHED_DIALOG);
        params.token = windowToken.token;
        final IBinder windowContextToken = new Binder();
        params.setWindowContextToken(windowContextToken);
        doReturn(true).when(mWm.mWindowContextListenerController)
                .hasListener(eq(windowContextToken));
        doReturn(TYPE_INPUT_METHOD).when(mWm.mWindowContextListenerController)
                .getWindowType(eq(windowContextToken));

        mWm.addWindow(session, new TestIWindow(), params, View.VISIBLE, DEFAULT_DISPLAY,
                UserHandle.USER_SYSTEM, WindowInsets.Type.defaultVisible(), null, new InsetsState(),
                new InsetsSourceControl.Array(), new Rect(), new float[1]);

        verify(mWm.mWindowContextListenerController, never()).registerWindowContainerListener(any(),
                any(), anyInt(), anyInt(), any());
    }

    @Test
    public void testSetInTouchMode_instrumentedProcessGetPermissionToSwitchTouchMode() {
        // Disable global touch mode (config_perDisplayFocusEnabled set to true)
        Resources mockResources = mock(Resources.class);
        spyOn(mContext);
        when(mContext.getResources()).thenReturn(mockResources);
        doReturn(true).when(mockResources).getBoolean(
                com.android.internal.R.bool.config_perDisplayFocusEnabled);

        // Get current touch mode state and setup WMS to run setInTouchMode
        boolean currentTouchMode = mWm.isInTouchMode(DEFAULT_DISPLAY);
        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        doReturn(false).when(mWm).checkCallingPermission(anyString(), anyString(), anyBoolean());
        when(mWm.mAtmService.instrumentationSourceHasPermission(callingPid,
                android.Manifest.permission.MODIFY_TOUCH_MODE_STATE)).thenReturn(true);

        mWm.setInTouchMode(!currentTouchMode, DEFAULT_DISPLAY);

        verify(mWm.mInputManager).setInTouchMode(
                !currentTouchMode, callingPid, callingUid, /* hasPermission= */ true,
                DEFAULT_DISPLAY);
    }

    @Test
    public void testSetInTouchMode_nonInstrumentedProcessDontGetPermissionToSwitchTouchMode() {
        // Disable global touch mode (config_perDisplayFocusEnabled set to true)
        Resources mockResources = mock(Resources.class);
        spyOn(mContext);
        when(mContext.getResources()).thenReturn(mockResources);
        doReturn(true).when(mockResources).getBoolean(
                com.android.internal.R.bool.config_perDisplayFocusEnabled);

        // Get current touch mode state and setup WMS to run setInTouchMode
        boolean currentTouchMode = mWm.isInTouchMode(DEFAULT_DISPLAY);
        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        doReturn(false).when(mWm).checkCallingPermission(anyString(), anyString(), anyBoolean());
        when(mWm.mAtmService.instrumentationSourceHasPermission(callingPid,
                android.Manifest.permission.MODIFY_TOUCH_MODE_STATE)).thenReturn(false);

        mWm.setInTouchMode(!currentTouchMode, DEFAULT_DISPLAY);

        verify(mWm.mInputManager).setInTouchMode(
                !currentTouchMode, callingPid, callingUid, /* hasPermission= */ false,
                DEFAULT_DISPLAY);
    }

    @Test
    public void testSetInTouchMode_multiDisplay_globalTouchModeUpdate() {
        // Create one extra display
        final VirtualDisplay virtualDisplay = createVirtualDisplay(/* ownFocus= */ false);
        final VirtualDisplay virtualDisplayOwnTouchMode =
                createVirtualDisplay(/* ownFocus= */ true);
        final int numberOfDisplays = mWm.mRoot.mChildren.size();
        assertThat(numberOfDisplays).isAtLeast(3);
        final int numberOfGlobalTouchModeDisplays = (int) mWm.mRoot.mChildren.stream()
                        .filter(d -> (d.getDisplay().getFlags() & FLAG_OWN_FOCUS) == 0)
                        .count();
        assertThat(numberOfGlobalTouchModeDisplays).isAtLeast(2);

        // Enable global touch mode (config_perDisplayFocusEnabled set to false)
        Resources mockResources = mock(Resources.class);
        spyOn(mContext);
        when(mContext.getResources()).thenReturn(mockResources);
        doReturn(false).when(mockResources).getBoolean(
                com.android.internal.R.bool.config_perDisplayFocusEnabled);

        // Get current touch mode state and setup WMS to run setInTouchMode
        boolean currentTouchMode = mWm.isInTouchMode(DEFAULT_DISPLAY);
        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        doReturn(false).when(mWm).checkCallingPermission(anyString(), anyString(), anyBoolean());
        when(mWm.mAtmService.instrumentationSourceHasPermission(callingPid,
                android.Manifest.permission.MODIFY_TOUCH_MODE_STATE)).thenReturn(true);

        mWm.setInTouchMode(!currentTouchMode, DEFAULT_DISPLAY);

        verify(mWm.mInputManager, times(numberOfGlobalTouchModeDisplays)).setInTouchMode(
                eq(!currentTouchMode), eq(callingPid), eq(callingUid),
                /* hasPermission= */ eq(true), /* displayId= */ anyInt());
    }

    @Test
    public void testSetInTouchMode_multiDisplay_perDisplayFocus_singleDisplayTouchModeUpdate() {
        // Create one extra display
        final VirtualDisplay virtualDisplay = createVirtualDisplay(/* ownFocus= */ false);
        final int numberOfDisplays = mWm.mRoot.mChildren.size();
        assertThat(numberOfDisplays).isAtLeast(2);

        // Disable global touch mode (config_perDisplayFocusEnabled set to true)
        Resources mockResources = mock(Resources.class);
        spyOn(mContext);
        when(mContext.getResources()).thenReturn(mockResources);
        doReturn(true).when(mockResources).getBoolean(
                com.android.internal.R.bool.config_perDisplayFocusEnabled);

        // Get current touch mode state and setup WMS to run setInTouchMode
        boolean currentTouchMode = mWm.isInTouchMode(DEFAULT_DISPLAY);
        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        doReturn(false).when(mWm).checkCallingPermission(anyString(), anyString(), anyBoolean());
        when(mWm.mAtmService.instrumentationSourceHasPermission(callingPid,
                android.Manifest.permission.MODIFY_TOUCH_MODE_STATE)).thenReturn(true);

        mWm.setInTouchMode(!currentTouchMode, virtualDisplay.getDisplay().getDisplayId());

        // Ensure that new display touch mode state has changed.
        verify(mWm.mInputManager).setInTouchMode(
                !currentTouchMode, callingPid, callingUid, /* hasPermission= */ true,
                virtualDisplay.getDisplay().getDisplayId());
    }

    @Test
    public void testSetInTouchMode_multiDisplay_ownTouchMode_singleDisplayTouchModeUpdate() {
        // Create one extra display
        final VirtualDisplay virtualDisplay = createVirtualDisplay(/* ownFocus= */ true);
        final int numberOfDisplays = mWm.mRoot.mChildren.size();
        assertThat(numberOfDisplays).isAtLeast(2);

        // Enable global touch mode (config_perDisplayFocusEnabled set to false)
        Resources mockResources = mock(Resources.class);
        spyOn(mContext);
        when(mContext.getResources()).thenReturn(mockResources);
        doReturn(false).when(mockResources).getBoolean(
                com.android.internal.R.bool.config_perDisplayFocusEnabled);

        // Get current touch mode state and setup WMS to run setInTouchMode
        boolean currentTouchMode = mWm.isInTouchMode(DEFAULT_DISPLAY);
        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        doReturn(false).when(mWm).checkCallingPermission(anyString(), anyString(), anyBoolean());
        when(mWm.mAtmService.instrumentationSourceHasPermission(callingPid,
                android.Manifest.permission.MODIFY_TOUCH_MODE_STATE)).thenReturn(true);

        mWm.setInTouchMode(!currentTouchMode, virtualDisplay.getDisplay().getDisplayId());

        // Ensure that new display touch mode state has changed.
        verify(mWm.mInputManager).setInTouchMode(
                !currentTouchMode, callingPid, callingUid, /* hasPermission= */ true,
                virtualDisplay.getDisplay().getDisplayId());
    }

    @Test
    public void testSetInTouchModeOnAllDisplays_perDisplayFocusDisabled() {
        testSetInTouchModeOnAllDisplays(/* perDisplayFocusEnabled= */ false);
    }

    @Test
    public void testSetInTouchModeOnAllDisplays_perDisplayFocusEnabled() {
        testSetInTouchModeOnAllDisplays(/* perDisplayFocusEnabled= */ true);
    }

    private void testSetInTouchModeOnAllDisplays(boolean perDisplayFocusEnabled) {
        // Create a couple of extra displays.
        // setInTouchModeOnAllDisplays should ignore the ownFocus setting.
        final VirtualDisplay virtualDisplay = createVirtualDisplay(/* ownFocus= */ false);
        final VirtualDisplay virtualDisplayOwnFocus = createVirtualDisplay(/* ownFocus= */ true);

        // Enable or disable global touch mode (config_perDisplayFocusEnabled setting).
        // setInTouchModeOnAllDisplays should ignore this value.
        Resources mockResources = mock(Resources.class);
        spyOn(mContext);
        when(mContext.getResources()).thenReturn(mockResources);
        doReturn(perDisplayFocusEnabled).when(mockResources).getBoolean(
                com.android.internal.R.bool.config_perDisplayFocusEnabled);

        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        doReturn(false).when(mWm).checkCallingPermission(anyString(), anyString(), anyBoolean());
        when(mWm.mAtmService.instrumentationSourceHasPermission(callingPid,
                android.Manifest.permission.MODIFY_TOUCH_MODE_STATE)).thenReturn(true);

        for (boolean inTouchMode : new boolean[]{true, false}) {
            mWm.setInTouchModeOnAllDisplays(inTouchMode);
            for (int i = 0; i < mWm.mRoot.mChildren.size(); ++i) {
                DisplayContent dc = mWm.mRoot.mChildren.get(i);
                // All displays that are not already in the desired touch mode are requested to
                // change their touch mode.
                if (dc.isInTouchMode() != inTouchMode) {
                    verify(mWm.mInputManager).setInTouchMode(
                            true, callingPid, callingUid, /* hasPermission= */ true,
                            dc.getDisplay().getDisplayId());
                }
            }
        }
    }

    private VirtualDisplay createVirtualDisplay(boolean ownFocus) {
        // Create virtual display
        Point surfaceSize = new Point(
                mDefaultDisplay.getDefaultTaskDisplayArea().getBounds().width(),
                mDefaultDisplay.getDefaultTaskDisplayArea().getBounds().height());
        int flags = VIRTUAL_DISPLAY_FLAG_PUBLIC;
        if (ownFocus) {
            flags |= VIRTUAL_DISPLAY_FLAG_OWN_FOCUS | VIRTUAL_DISPLAY_FLAG_TRUSTED;
        }
        VirtualDisplay virtualDisplay = mWm.mDisplayManager.createVirtualDisplay("VirtualDisplay",
                surfaceSize.x, surfaceSize.y, DisplayMetrics.DENSITY_140, new Surface(), flags);
        final int displayId = virtualDisplay.getDisplay().getDisplayId();
        mWm.mRoot.onDisplayAdded(displayId);

        // Ensure that virtual display was properly created and stored in WRC
        assertThat(mWm.mRoot.getDisplayContent(
                virtualDisplay.getDisplay().getDisplayId())).isNotNull();

        return virtualDisplay;
    }

    @Test
    public void testGetTaskWindowContainerTokenForLaunchCookie_nullCookie() {
        WindowContainerToken wct = mWm.getTaskWindowContainerTokenForLaunchCookie(null);
        assertThat(wct).isNull();
    }

    @Test
    public void testGetTaskWindowContainerTokenForLaunchCookie_invalidCookie() {
        Binder cookie = new Binder("test cookie");
        WindowContainerToken wct = mWm.getTaskWindowContainerTokenForLaunchCookie(cookie);
        assertThat(wct).isNull();

        final ActivityRecord testActivity = new ActivityBuilder(mAtm)
                .setCreateTask(true)
                .build();

        wct = mWm.getTaskWindowContainerTokenForLaunchCookie(cookie);
        assertThat(wct).isNull();
    }

    @Test
    public void testGetTaskWindowContainerTokenForLaunchCookie_validCookie() {
        final Binder cookie = new Binder("ginger cookie");
        final WindowContainerToken launchRootTask = mock(WindowContainerToken.class);
        setupActivityWithLaunchCookie(cookie, launchRootTask);

        WindowContainerToken wct = mWm.getTaskWindowContainerTokenForLaunchCookie(cookie);
        assertThat(wct).isEqualTo(launchRootTask);
    }

    @Test
    public void testGetTaskWindowContainerTokenForLaunchCookie_multipleCookies() {
        final Binder cookie1 = new Binder("ginger cookie");
        final WindowContainerToken launchRootTask1 = mock(WindowContainerToken.class);
        setupActivityWithLaunchCookie(cookie1, launchRootTask1);

        setupActivityWithLaunchCookie(new Binder("choc chip cookie"),
                mock(WindowContainerToken.class));

        setupActivityWithLaunchCookie(new Binder("peanut butter cookie"),
                mock(WindowContainerToken.class));

        WindowContainerToken wct = mWm.getTaskWindowContainerTokenForLaunchCookie(cookie1);
        assertThat(wct).isEqualTo(launchRootTask1);
    }

    @Test
    public void testGetTaskWindowContainerTokenForLaunchCookie_multipleCookies_noneValid() {
        setupActivityWithLaunchCookie(new Binder("ginger cookie"),
                mock(WindowContainerToken.class));

        setupActivityWithLaunchCookie(new Binder("choc chip cookie"),
                mock(WindowContainerToken.class));

        setupActivityWithLaunchCookie(new Binder("peanut butter cookie"),
                mock(WindowContainerToken.class));

        WindowContainerToken wct = mWm.getTaskWindowContainerTokenForLaunchCookie(
                new Binder("some other cookie"));
        assertThat(wct).isNull();
    }

    @Test
    public void testisLetterboxBackgroundMultiColored() {
        assertThat(setupLetterboxConfigurationWithBackgroundType(
                LETTERBOX_BACKGROUND_APP_COLOR_BACKGROUND_FLOATING)).isTrue();
        assertThat(setupLetterboxConfigurationWithBackgroundType(
                LETTERBOX_BACKGROUND_APP_COLOR_BACKGROUND)).isTrue();
        assertThat(setupLetterboxConfigurationWithBackgroundType(
                LETTERBOX_BACKGROUND_WALLPAPER)).isTrue();
        assertThat(setupLetterboxConfigurationWithBackgroundType(
                LETTERBOX_BACKGROUND_SOLID_COLOR)).isFalse();
    }

    @Test
    public void testCaptureDisplay() {
        Rect displayBounds = new Rect(0, 0, 100, 200);
        spyOn(mDisplayContent);
        when(mDisplayContent.getBounds()).thenReturn(displayBounds);

        // Null captureArgs
        ScreenCapture.LayerCaptureArgs resultingArgs =
                mWm.getCaptureArgs(DEFAULT_DISPLAY, null /* captureArgs */);
        assertEquals(displayBounds, resultingArgs.mSourceCrop);

        // Non null captureArgs, didn't set rect
        ScreenCapture.CaptureArgs captureArgs = new ScreenCapture.CaptureArgs.Builder<>().build();
        resultingArgs = mWm.getCaptureArgs(DEFAULT_DISPLAY, captureArgs);
        assertEquals(displayBounds, resultingArgs.mSourceCrop);

        // Non null captureArgs, invalid rect
        captureArgs = new ScreenCapture.CaptureArgs.Builder<>()
                .setSourceCrop(new Rect(0, 0, -1, -1))
                .build();
        resultingArgs = mWm.getCaptureArgs(DEFAULT_DISPLAY, captureArgs);
        assertEquals(displayBounds, resultingArgs.mSourceCrop);

        // Non null captureArgs, null rect
        captureArgs = new ScreenCapture.CaptureArgs.Builder<>()
                .setSourceCrop(null)
                .build();
        resultingArgs = mWm.getCaptureArgs(DEFAULT_DISPLAY, captureArgs);
        assertEquals(displayBounds, resultingArgs.mSourceCrop);

        // Non null captureArgs, valid rect
        Rect validRect = new Rect(0, 0, 10, 50);
        captureArgs = new ScreenCapture.CaptureArgs.Builder<>()
                .setSourceCrop(validRect)
                .build();
        resultingArgs = mWm.getCaptureArgs(DEFAULT_DISPLAY, captureArgs);
        assertEquals(validRect, resultingArgs.mSourceCrop);
    }

    @Test
    public void testGrantInputChannel_sanitizeSpyWindowForApplications() {
        final Session session = mock(Session.class);
        final int callingUid = Process.FIRST_APPLICATION_UID;
        final int callingPid = 1234;
        final SurfaceControl surfaceControl = mock(SurfaceControl.class);
        final IWindow window = mock(IWindow.class);
        final IBinder windowToken = mock(IBinder.class);
        when(window.asBinder()).thenReturn(windowToken);
        final IBinder focusGrantToken = mock(IBinder.class);

        final InputChannel inputChannel = new InputChannel();
        assertThrows(IllegalArgumentException.class, () ->
                mWm.grantInputChannel(session, callingUid, callingPid, DEFAULT_DISPLAY,
                        surfaceControl, window, null /* hostInputToken */, FLAG_NOT_FOCUSABLE,
                        PRIVATE_FLAG_TRUSTED_OVERLAY, INPUT_FEATURE_SPY, TYPE_APPLICATION,
                        null /* windowToken */, focusGrantToken, "TestInputChannel",
                        inputChannel));
    }

    @Test
    public void testGrantInputChannel_allowSpyWindowForInputMonitorPermission() {
        final Session session = mock(Session.class);
        final int callingUid = Process.SYSTEM_UID;
        final int callingPid = 1234;
        final SurfaceControl surfaceControl = mock(SurfaceControl.class);
        final IWindow window = mock(IWindow.class);
        final IBinder windowToken = mock(IBinder.class);
        when(window.asBinder()).thenReturn(windowToken);
        final IBinder focusGrantToken = mock(IBinder.class);

        final InputChannel inputChannel = new InputChannel();
        mWm.grantInputChannel(session, callingUid, callingPid, DEFAULT_DISPLAY, surfaceControl,
                window, null /* hostInputToken */, FLAG_NOT_FOCUSABLE, PRIVATE_FLAG_TRUSTED_OVERLAY,
                INPUT_FEATURE_SPY, TYPE_APPLICATION, null /* windowToken */, focusGrantToken,
                "TestInputChannel", inputChannel);

        verify(mTransaction).setInputWindowInfo(
                eq(surfaceControl),
                argThat(h -> (h.inputConfig & InputConfig.SPY) == InputConfig.SPY));
    }

    @Test
    public void testUpdateInputChannel_sanitizeSpyWindowForApplications() {
        final Session session = mock(Session.class);
        final int callingUid = Process.FIRST_APPLICATION_UID;
        final int callingPid = 1234;
        final SurfaceControl surfaceControl = mock(SurfaceControl.class);
        final IWindow window = mock(IWindow.class);
        final IBinder windowToken = mock(IBinder.class);
        when(window.asBinder()).thenReturn(windowToken);
        final IBinder focusGrantToken = mock(IBinder.class);

        final InputChannel inputChannel = new InputChannel();
        mWm.grantInputChannel(session, callingUid, callingPid, DEFAULT_DISPLAY, surfaceControl,
                window, null /* hostInputToken */, FLAG_NOT_FOCUSABLE, PRIVATE_FLAG_TRUSTED_OVERLAY,
                0 /* inputFeatures */, TYPE_APPLICATION, null /* windowToken */, focusGrantToken,
                "TestInputChannel", inputChannel);
        verify(mTransaction).setInputWindowInfo(
                eq(surfaceControl),
                argThat(h -> (h.inputConfig & InputConfig.SPY) == 0));

        assertThrows(IllegalArgumentException.class, () ->
                mWm.updateInputChannel(inputChannel.getToken(), DEFAULT_DISPLAY, surfaceControl,
                        FLAG_NOT_FOCUSABLE, PRIVATE_FLAG_TRUSTED_OVERLAY, INPUT_FEATURE_SPY,
                        null /* region */));
    }

    @Test
    public void testUpdateInputChannel_allowSpyWindowForInputMonitorPermission() {
        final Session session = mock(Session.class);
        final int callingUid = Process.SYSTEM_UID;
        final int callingPid = 1234;
        final SurfaceControl surfaceControl = mock(SurfaceControl.class);
        final IWindow window = mock(IWindow.class);
        final IBinder windowToken = mock(IBinder.class);
        when(window.asBinder()).thenReturn(windowToken);
        final IBinder focusGrantToken = mock(IBinder.class);

        final InputChannel inputChannel = new InputChannel();
        mWm.grantInputChannel(session, callingUid, callingPid, DEFAULT_DISPLAY, surfaceControl,
                window, null /* hostInputToken */, FLAG_NOT_FOCUSABLE, PRIVATE_FLAG_TRUSTED_OVERLAY,
                0 /* inputFeatures */, TYPE_APPLICATION, null /* windowToken */, focusGrantToken,
                "TestInputChannel", inputChannel);
        verify(mTransaction).setInputWindowInfo(
                eq(surfaceControl),
                argThat(h -> (h.inputConfig & InputConfig.SPY) == 0));

        mWm.updateInputChannel(inputChannel.getToken(), DEFAULT_DISPLAY, surfaceControl,
                FLAG_NOT_FOCUSABLE, PRIVATE_FLAG_TRUSTED_OVERLAY, INPUT_FEATURE_SPY,
                null /* region */);
        verify(mTransaction).setInputWindowInfo(
                eq(surfaceControl),
                argThat(h -> (h.inputConfig & InputConfig.SPY) == InputConfig.SPY));
    }

    private void setupActivityWithLaunchCookie(IBinder launchCookie, WindowContainerToken wct) {
        final WindowContainer.RemoteToken remoteToken = mock(WindowContainer.RemoteToken.class);
        when(remoteToken.toWindowContainerToken()).thenReturn(wct);
        final ActivityRecord testActivity = new ActivityBuilder(mAtm)
                .setCreateTask(true)
                .build();
        testActivity.mLaunchCookie = launchCookie;
        testActivity.getTask().mRemoteToken = remoteToken;
    }

    private boolean setupLetterboxConfigurationWithBackgroundType(
            @LetterboxConfiguration.LetterboxBackgroundType int letterboxBackgroundType) {
        mWm.mLetterboxConfiguration.setLetterboxBackgroundType(letterboxBackgroundType);
        return mWm.isLetterboxBackgroundMultiColored();
    }
}
