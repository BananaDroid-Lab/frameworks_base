/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.recents;

import static android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.lang.Integer.MAX_VALUE;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.view.SurfaceControl;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.StaticMockitoSession;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.desktopmode.DesktopMode;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.util.GroupedRecentTaskInfo;
import com.android.wm.shell.util.SplitBounds;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

/**
 * Tests for {@link RecentTasksController}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class RecentTasksControllerTest extends ShellTestCase {

    @Mock
    private Context mContext;
    @Mock
    private TaskStackListenerImpl mTaskStackListener;
    @Mock
    private ShellCommandHandler mShellCommandHandler;

    private ShellTaskOrganizer mShellTaskOrganizer;
    private RecentTasksController mRecentTasksController;
    private ShellInit mShellInit;
    private ShellExecutor mMainExecutor;

    @Before
    public void setUp() {
        mMainExecutor = new TestShellExecutor();
        when(mContext.getPackageManager()).thenReturn(mock(PackageManager.class));
        mShellInit = spy(new ShellInit(mMainExecutor));
        mRecentTasksController = spy(new RecentTasksController(mContext, mShellInit,
                mShellCommandHandler, mTaskStackListener, mMainExecutor));
        mShellTaskOrganizer = new ShellTaskOrganizer(mShellInit, mShellCommandHandler,
                null /* sizeCompatUI */, Optional.empty(), Optional.of(mRecentTasksController),
                mMainExecutor);
        mShellInit.init();
    }

    @Test
    public void instantiateController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), isA(RecentTasksController.class));
    }

    @Test
    public void instantiateController_addDumpCallback() {
        verify(mShellCommandHandler, times(1)).addDumpCallback(any(),
                isA(RecentTasksController.class));
    }

    @Test
    public void testAddRemoveSplitNotifyChange() {
        ActivityManager.RecentTaskInfo t1 = makeTaskInfo(1);
        ActivityManager.RecentTaskInfo t2 = makeTaskInfo(2);
        setRawList(t1, t2);

        mRecentTasksController.addSplitPair(t1.taskId, t2.taskId, mock(SplitBounds.class));
        verify(mRecentTasksController).notifyRecentTasksChanged();

        reset(mRecentTasksController);
        mRecentTasksController.removeSplitPair(t1.taskId);
        verify(mRecentTasksController).notifyRecentTasksChanged();
    }

    @Test
    public void testAddSameSplitBoundsInfoSkipNotifyChange() {
        ActivityManager.RecentTaskInfo t1 = makeTaskInfo(1);
        ActivityManager.RecentTaskInfo t2 = makeTaskInfo(2);
        setRawList(t1, t2);

        // Verify only one update if the split info is the same
        SplitBounds bounds1 = new SplitBounds(new Rect(0, 0, 50, 50),
                new Rect(50, 50, 100, 100), t1.taskId, t2.taskId);
        mRecentTasksController.addSplitPair(t1.taskId, t2.taskId, bounds1);
        SplitBounds bounds2 = new SplitBounds(new Rect(0, 0, 50, 50),
                new Rect(50, 50, 100, 100), t1.taskId, t2.taskId);
        mRecentTasksController.addSplitPair(t1.taskId, t2.taskId, bounds2);
        verify(mRecentTasksController, times(1)).notifyRecentTasksChanged();
    }

    @Test
    public void testGetRecentTasks() {
        ActivityManager.RecentTaskInfo t1 = makeTaskInfo(1);
        ActivityManager.RecentTaskInfo t2 = makeTaskInfo(2);
        ActivityManager.RecentTaskInfo t3 = makeTaskInfo(3);
        setRawList(t1, t2, t3);

        ArrayList<GroupedRecentTaskInfo> recentTasks = mRecentTasksController.getRecentTasks(
                MAX_VALUE, RECENT_IGNORE_UNAVAILABLE, 0);
        assertGroupedTasksListEquals(recentTasks,
                t1.taskId, -1,
                t2.taskId, -1,
                t3.taskId, -1);
    }

    @Test
    public void testGetRecentTasks_withPairs() {
        ActivityManager.RecentTaskInfo t1 = makeTaskInfo(1);
        ActivityManager.RecentTaskInfo t2 = makeTaskInfo(2);
        ActivityManager.RecentTaskInfo t3 = makeTaskInfo(3);
        ActivityManager.RecentTaskInfo t4 = makeTaskInfo(4);
        ActivityManager.RecentTaskInfo t5 = makeTaskInfo(5);
        ActivityManager.RecentTaskInfo t6 = makeTaskInfo(6);
        setRawList(t1, t2, t3, t4, t5, t6);

        // Mark a couple pairs [t2, t4], [t3, t5]
        SplitBounds pair1Bounds = new SplitBounds(new Rect(), new Rect(), 2, 4);
        SplitBounds pair2Bounds = new SplitBounds(new Rect(), new Rect(), 3, 5);

        mRecentTasksController.addSplitPair(t2.taskId, t4.taskId, pair1Bounds);
        mRecentTasksController.addSplitPair(t3.taskId, t5.taskId, pair2Bounds);

        ArrayList<GroupedRecentTaskInfo> recentTasks = mRecentTasksController.getRecentTasks(
                MAX_VALUE, RECENT_IGNORE_UNAVAILABLE, 0);
        assertGroupedTasksListEquals(recentTasks,
                t1.taskId, -1,
                t2.taskId, t4.taskId,
                t3.taskId, t5.taskId,
                t6.taskId, -1);
    }

    @Test
    public void testGetRecentTasks_groupActiveFreeformTasks() {
        StaticMockitoSession mockitoSession = mockitoSession().mockStatic(
                DesktopMode.class).startMocking();
        when(DesktopMode.isActive(any())).thenReturn(true);

        ActivityManager.RecentTaskInfo t1 = makeTaskInfo(1);
        ActivityManager.RecentTaskInfo t2 = makeTaskInfo(2);
        ActivityManager.RecentTaskInfo t3 = makeTaskInfo(3);
        ActivityManager.RecentTaskInfo t4 = makeTaskInfo(4);
        setRawList(t1, t2, t3, t4);

        mRecentTasksController.addActiveFreeformTask(1);
        mRecentTasksController.addActiveFreeformTask(3);

        ArrayList<GroupedRecentTaskInfo> recentTasks = mRecentTasksController.getRecentTasks(
                MAX_VALUE, RECENT_IGNORE_UNAVAILABLE, 0);

        // 2 freeform tasks should be grouped into one, 3 total recents entries
        assertEquals(3, recentTasks.size());
        GroupedRecentTaskInfo freeformGroup = recentTasks.get(0);
        GroupedRecentTaskInfo singleGroup1 = recentTasks.get(1);
        GroupedRecentTaskInfo singleGroup2 = recentTasks.get(2);

        // Check that groups have expected types
        assertEquals(GroupedRecentTaskInfo.TYPE_FREEFORM, freeformGroup.getType());
        assertEquals(GroupedRecentTaskInfo.TYPE_SINGLE, singleGroup1.getType());
        assertEquals(GroupedRecentTaskInfo.TYPE_SINGLE, singleGroup2.getType());

        // Check freeform group entries
        assertEquals(t1, freeformGroup.getAllTaskInfos().get(0));
        assertEquals(t3, freeformGroup.getAllTaskInfos().get(1));

        // Check single entries
        assertEquals(t2, singleGroup1.getTaskInfo1());
        assertEquals(t4, singleGroup2.getTaskInfo1());

        mockitoSession.finishMocking();
    }

    @Test
    public void testRemovedTaskRemovesSplit() {
        ActivityManager.RecentTaskInfo t1 = makeTaskInfo(1);
        ActivityManager.RecentTaskInfo t2 = makeTaskInfo(2);
        ActivityManager.RecentTaskInfo t3 = makeTaskInfo(3);
        setRawList(t1, t2, t3);

        // Add a pair
        SplitBounds pair1Bounds = new SplitBounds(new Rect(), new Rect(), 2, 3);
        mRecentTasksController.addSplitPair(t2.taskId, t3.taskId, pair1Bounds);
        reset(mRecentTasksController);

        // Remove one of the tasks and ensure the pair is removed
        SurfaceControl mockLeash = mock(SurfaceControl.class);
        ActivityManager.RunningTaskInfo rt2 = makeRunningTaskInfo(2);
        mShellTaskOrganizer.onTaskAppeared(rt2, mockLeash);
        mShellTaskOrganizer.onTaskVanished(rt2);

        verify(mRecentTasksController).removeSplitPair(t2.taskId);
    }

    @Test
    public void testTaskWindowingModeChangedNotifiesChange() {
        ActivityManager.RecentTaskInfo t1 = makeTaskInfo(1);
        setRawList(t1);

        // Remove one of the tasks and ensure the pair is removed
        SurfaceControl mockLeash = mock(SurfaceControl.class);
        ActivityManager.RunningTaskInfo rt2Fullscreen = makeRunningTaskInfo(2);
        rt2Fullscreen.configuration.windowConfiguration.setWindowingMode(
                WINDOWING_MODE_FULLSCREEN);
        mShellTaskOrganizer.onTaskAppeared(rt2Fullscreen, mockLeash);

        // Change the windowing mode and ensure the recent tasks change is notified
        ActivityManager.RunningTaskInfo rt2MultiWIndow = makeRunningTaskInfo(2);
        rt2MultiWIndow.configuration.windowConfiguration.setWindowingMode(
                WINDOWING_MODE_MULTI_WINDOW);
        mShellTaskOrganizer.onTaskInfoChanged(rt2MultiWIndow);

        verify(mRecentTasksController).notifyRecentTasksChanged();
    }

    /**
     * Helper to create a task with a given task id.
     */
    private ActivityManager.RecentTaskInfo makeTaskInfo(int taskId) {
        ActivityManager.RecentTaskInfo info = new ActivityManager.RecentTaskInfo();
        info.taskId = taskId;
        return info;
    }

    /**
     * Helper to create a running task with a given task id.
     */
    private ActivityManager.RunningTaskInfo makeRunningTaskInfo(int taskId) {
        ActivityManager.RunningTaskInfo info = new ActivityManager.RunningTaskInfo();
        info.taskId = taskId;
        return info;
    }

    /**
     * Helper to set the raw task list on the controller.
     */
    private ArrayList<ActivityManager.RecentTaskInfo> setRawList(
            ActivityManager.RecentTaskInfo... tasks) {
        ArrayList<ActivityManager.RecentTaskInfo> rawList = new ArrayList<>();
        for (ActivityManager.RecentTaskInfo task : tasks) {
            rawList.add(task);
        }
        doReturn(rawList).when(mRecentTasksController).getRawRecentTasks(anyInt(), anyInt(),
                anyInt());
        return rawList;
    }

    /**
     * Asserts that the recent tasks matches the given task ids.
     *
     * @param expectedTaskIds list of task ids that map to the flattened task ids of the tasks in
     *                        the grouped task list
     */
    private void assertGroupedTasksListEquals(ArrayList<GroupedRecentTaskInfo> recentTasks,
            int... expectedTaskIds) {
        int[] flattenedTaskIds = new int[recentTasks.size() * 2];
        for (int i = 0; i < recentTasks.size(); i++) {
            GroupedRecentTaskInfo pair = recentTasks.get(i);
            int taskId1 = pair.getTaskInfo1().taskId;
            flattenedTaskIds[2 * i] = taskId1;
            flattenedTaskIds[2 * i + 1] = pair.getTaskInfo2() != null
                    ? pair.getTaskInfo2().taskId
                    : -1;

            if (pair.getTaskInfo2() != null) {
                assertNotNull(pair.getSplitBounds());
                int leftTopTaskId = pair.getSplitBounds().leftTopTaskId;
                int bottomRightTaskId = pair.getSplitBounds().rightBottomTaskId;
                // Unclear if pairs are ordered by split position, most likely not.
                assertTrue(leftTopTaskId == taskId1
                        || leftTopTaskId == pair.getTaskInfo2().taskId);
                assertTrue(bottomRightTaskId == taskId1
                        || bottomRightTaskId == pair.getTaskInfo2().taskId);
            } else {
                assertNull(pair.getSplitBounds());
            }
        }
        assertTrue("Expected: " + Arrays.toString(expectedTaskIds)
                        + " Received: " + Arrays.toString(flattenedTaskIds),
                Arrays.equals(flattenedTaskIds, expectedTaskIds));
    }
}
