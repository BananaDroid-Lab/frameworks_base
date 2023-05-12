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

package com.android.wm.shell.taskview;

import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.graphics.Rect;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.transition.Transitions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class TaskViewTransitionsTest extends ShellTestCase {

    @Mock
    Transitions mTransitions;
    @Mock
    TaskViewTaskController mTaskViewTaskController;
    @Mock
    ActivityManager.RunningTaskInfo mTaskInfo;
    @Mock
    WindowContainerToken mToken;
    @Mock
    TaskViewTaskController mTaskViewTaskController2;
    @Mock
    ActivityManager.RunningTaskInfo mTaskInfo2;
    @Mock
    WindowContainerToken mToken2;

    TaskViewTransitions mTaskViewTransitions;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        if (Transitions.ENABLE_SHELL_TRANSITIONS) {
            doReturn(true).when(mTransitions).isRegistered();
        }

        mTaskInfo = new ActivityManager.RunningTaskInfo();
        mTaskInfo.token = mToken;
        mTaskInfo.taskId = 314;
        mTaskInfo.taskDescription = mock(ActivityManager.TaskDescription.class);
        when(mTaskViewTaskController.getTaskInfo()).thenReturn(mTaskInfo);

        mTaskInfo2 = new ActivityManager.RunningTaskInfo();
        mTaskInfo2.token = mToken2;
        mTaskInfo2.taskId = 315;
        mTaskInfo2.taskDescription = mock(ActivityManager.TaskDescription.class);
        when(mTaskViewTaskController2.getTaskInfo()).thenReturn(mTaskInfo2);

        mTaskViewTransitions = spy(new TaskViewTransitions(mTransitions));
        mTaskViewTransitions.addTaskView(mTaskViewTaskController);
    }

    @Test
    public void testSetTaskBounds_taskNotVisible_noTransaction() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, false);
        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 100, 100));

        assertThat(mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_CHANGE))
                .isNull();
    }

    @Test
    public void testSetTaskBounds_taskVisible_boundsChangeTransaction() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, true);

        // Consume the pending transaction from visibility change
        TaskViewTransitions.PendingTransition pending =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pending).isNotNull();
        mTaskViewTransitions.startAnimation(pending.mClaimed,
                mock(TransitionInfo.class),
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));
        // Verify it was consumed
        TaskViewTransitions.PendingTransition pending2 =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pending2).isNull();

        // Test that set bounds creates a new transaction
        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 100, 100));
        assertThat(mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_CHANGE))
                .isNotNull();
    }

    @Test
    public void testSetTaskBounds_taskVisibleWithPendingOpen_noTransaction() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, true);

        TaskViewTransitions.PendingTransition pending =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pending).isNotNull();

        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 100, 100));
        assertThat(mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_CHANGE))
                .isNull();
    }

    @Test
    public void testSetTaskBounds_taskVisibleWithPendingChange_transition() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, true);

        // Consume the pending transition from visibility change
        TaskViewTransitions.PendingTransition pending =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pending).isNotNull();
        mTaskViewTransitions.startAnimation(pending.mClaimed,
                mock(TransitionInfo.class),
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));
        // Verify it was consumed
        TaskViewTransitions.PendingTransition checkPending =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(checkPending).isNull();

        // Test that set bounds creates a new transition
        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 100, 100));
        assertThat(mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_CHANGE))
                .isNotNull();

        // Test that set bounds again (with different bounds) creates another transition
        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 300, 200));
        List<TaskViewTransitions.PendingTransition> pendingList =
                mTaskViewTransitions.findAllPending(mTaskViewTaskController)
                        .stream()
                        .filter(pendingTransition -> pendingTransition.mType == TRANSIT_CHANGE)
                        .toList();
        assertThat(pendingList.size()).isEqualTo(2);
    }

    @Test
    public void testSetTaskBounds_sameBounds_noTransaction() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, true);

        // Consume the pending transaction from visibility change
        TaskViewTransitions.PendingTransition pending =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pending).isNotNull();
        mTaskViewTransitions.startAnimation(pending.mClaimed,
                mock(TransitionInfo.class),
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));
        // Verify it was consumed
        TaskViewTransitions.PendingTransition pending2 =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pending2).isNull();

        // Test that set bounds creates a new transaction
        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 100, 100));
        TaskViewTransitions.PendingTransition pendingBounds =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_CHANGE);
        assertThat(pendingBounds).isNotNull();

        // Test that setting same bounds with in-flight transition doesn't cause another one
        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 100, 100));
        List<TaskViewTransitions.PendingTransition> pendingList =
                mTaskViewTransitions.findAllPending(mTaskViewTaskController)
                        .stream()
                        .filter(pendingTransition -> pendingTransition.mType == TRANSIT_CHANGE)
                        .toList();
        assertThat(pendingList.size()).isEqualTo(1);

        // Consume the pending bounds transaction
        mTaskViewTransitions.startAnimation(pendingBounds.mClaimed,
                mock(TransitionInfo.class),
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));
        // Verify it was consumed
        TaskViewTransitions.PendingTransition pendingBounds1 =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_CHANGE);
        assertThat(pendingBounds1).isNull();

        // Test that setting the same bounds doesn't creates a new transaction
        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 100, 100));
        TaskViewTransitions.PendingTransition pendingBounds2 =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_CHANGE);
        assertThat(pendingBounds2).isNull();
    }


    @Test
    public void testSetTaskBounds_taskVisibleWithDifferentTaskViewPendingChange_transition() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        mTaskViewTransitions.addTaskView(mTaskViewTaskController2);

        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, true);

        // Consume the pending transition from visibility change
        TaskViewTransitions.PendingTransition pending =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pending).isNotNull();
        mTaskViewTransitions.startAnimation(pending.mClaimed,
                mock(TransitionInfo.class),
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));
        // Verify it was consumed
        TaskViewTransitions.PendingTransition checkPending =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(checkPending).isNull();

        // Set the second taskview as visible & check that it has a pending transition
        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController2, true);
        TaskViewTransitions.PendingTransition pending2 =
                mTaskViewTransitions.findPending(mTaskViewTaskController2, TRANSIT_TO_FRONT);
        assertThat(pending2).isNotNull();

        // Test that set bounds on the first taskview will create a new transition
        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 100, 100));
        assertThat(mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_CHANGE))
                .isNotNull();
    }

    @Test
    public void testSetTaskVisibility_taskRemoved_noNPE() {
        mTaskViewTransitions.removeTaskView(mTaskViewTaskController);

        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, false);
    }

    @Test
    public void testSetTaskBounds_taskRemoved_noNPE() {
        mTaskViewTransitions.removeTaskView(mTaskViewTaskController);

        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 100, 100));
    }
}
