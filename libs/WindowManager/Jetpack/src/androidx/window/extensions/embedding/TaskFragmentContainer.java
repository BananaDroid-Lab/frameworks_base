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

package androidx.window.extensions.embedding;

import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.WindowConfiguration.WindowingMode;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.window.TaskFragmentInfo;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side container for a stack of activities. Corresponds to an instance of TaskFragment
 * on the server side.
 */
class TaskFragmentContainer {
    private static final int APPEAR_EMPTY_TIMEOUT_MS = 3000;

    @NonNull
    private final SplitController mController;

    /**
     * Client-created token that uniquely identifies the task fragment container instance.
     */
    @NonNull
    private final IBinder mToken;

    /** Parent leaf Task. */
    @NonNull
    private final TaskContainer mTaskContainer;

    /**
     * Server-provided task fragment information.
     */
    @VisibleForTesting
    TaskFragmentInfo mInfo;

    /**
     * Activities that are being reparented or being started to this container, but haven't been
     * added to {@link #mInfo} yet.
     */
    private final ArrayList<Activity> mPendingAppearedActivities = new ArrayList<>();

    /** Containers that are dependent on this one and should be completely destroyed on exit. */
    private final List<TaskFragmentContainer> mContainersToFinishOnExit =
            new ArrayList<>();

    /** Individual associated activities in different containers that should be finished on exit. */
    private final List<Activity> mActivitiesToFinishOnExit = new ArrayList<>();

    /** Indicates whether the container was cleaned up after the last activity was removed. */
    private boolean mIsFinished;

    /**
     * Bounds that were requested last via {@link android.window.WindowContainerTransaction}.
     */
    private final Rect mLastRequestedBounds = new Rect();

    /**
     * Windowing mode that was requested last via {@link android.window.WindowContainerTransaction}.
     */
    @WindowingMode
    private int mLastRequestedWindowingMode = WINDOWING_MODE_UNDEFINED;

    /**
     * When the TaskFragment has appeared in server, but is empty, we should remove the TaskFragment
     * if it is still empty after the timeout.
     */
    @VisibleForTesting
    @Nullable
    Runnable mAppearEmptyTimeout;

    /**
     * Creates a container with an existing activity that will be re-parented to it in a window
     * container transaction.
     */
    TaskFragmentContainer(@Nullable Activity activity, @NonNull TaskContainer taskContainer,
            @NonNull SplitController controller) {
        mController = controller;
        mToken = new Binder("TaskFragmentContainer");
        mTaskContainer = taskContainer;
        taskContainer.mContainers.add(this);
        if (activity != null) {
            addPendingAppearedActivity(activity);
        }
    }

    /**
     * Returns the client-created token that uniquely identifies this container.
     */
    @NonNull
    IBinder getTaskFragmentToken() {
        return mToken;
    }

    /** List of activities that belong to this container and live in this process. */
    @NonNull
    List<Activity> collectActivities() {
        // Add the re-parenting activity, in case the server has not yet reported the task
        // fragment info update with it placed in this container. We still want to apply rules
        // in this intermediate state.
        List<Activity> allActivities = new ArrayList<>();
        if (!mPendingAppearedActivities.isEmpty()) {
            allActivities.addAll(mPendingAppearedActivities);
        }
        // Add activities reported from the server.
        if (mInfo == null) {
            return allActivities;
        }
        for (IBinder token : mInfo.getActivities()) {
            Activity activity = mController.getActivity(token);
            if (activity != null && !activity.isFinishing() && !allActivities.contains(activity)) {
                allActivities.add(activity);
            }
        }
        return allActivities;
    }

    /**
     * Checks if the count of activities from the same process in task fragment info corresponds to
     * the ones created and available on the client side.
     */
    boolean taskInfoActivityCountMatchesCreated() {
        if (mInfo == null) {
            return false;
        }
        return mPendingAppearedActivities.isEmpty()
                && mInfo.getActivities().size() == collectActivities().size();
    }

    ActivityStack toActivityStack() {
        return new ActivityStack(collectActivities(), isEmpty());
    }

    void addPendingAppearedActivity(@NonNull Activity pendingAppearedActivity) {
        if (hasActivity(pendingAppearedActivity.getActivityToken())) {
            return;
        }
        // Remove the pending activity from other TaskFragments.
        mTaskContainer.cleanupPendingAppearedActivity(pendingAppearedActivity);
        mPendingAppearedActivities.add(pendingAppearedActivity);
    }

    void removePendingAppearedActivity(@NonNull Activity pendingAppearedActivity) {
        mPendingAppearedActivities.remove(pendingAppearedActivity);
    }

    boolean hasActivity(@NonNull IBinder token) {
        if (mInfo != null && mInfo.getActivities().contains(token)) {
            return true;
        }
        for (Activity activity : mPendingAppearedActivities) {
            if (activity.getActivityToken().equals(token)) {
                return true;
            }
        }
        return false;
    }

    int getRunningActivityCount() {
        int count = mPendingAppearedActivities.size();
        if (mInfo != null) {
            count += mInfo.getRunningActivityCount();
        }
        return count;
    }

    /** Whether we are waiting for the TaskFragment to appear and become non-empty. */
    boolean isWaitingActivityAppear() {
        return !mIsFinished && (mInfo == null || mAppearEmptyTimeout != null);
    }

    @Nullable
    TaskFragmentInfo getInfo() {
        return mInfo;
    }

    void setInfo(@NonNull TaskFragmentInfo info) {
        if (!mIsFinished && mInfo == null && info.isEmpty()) {
            // onTaskFragmentAppeared with empty info. We will remove the TaskFragment if it is
            // still empty after timeout.
            mAppearEmptyTimeout = () -> {
                mAppearEmptyTimeout = null;
                mController.onTaskFragmentAppearEmptyTimeout(this);
            };
            mController.getHandler().postDelayed(mAppearEmptyTimeout, APPEAR_EMPTY_TIMEOUT_MS);
        } else if (mAppearEmptyTimeout != null && !info.isEmpty()) {
            mController.getHandler().removeCallbacks(mAppearEmptyTimeout);
            mAppearEmptyTimeout = null;
        }

        mInfo = info;
        if (mInfo == null || mPendingAppearedActivities.isEmpty()) {
            return;
        }
        // Cleanup activities that were being re-parented
        List<IBinder> infoActivities = mInfo.getActivities();
        for (int i = mPendingAppearedActivities.size() - 1; i >= 0; --i) {
            final Activity activity = mPendingAppearedActivities.get(i);
            if (infoActivities.contains(activity.getActivityToken())) {
                mPendingAppearedActivities.remove(i);
            }
        }
    }

    @Nullable
    Activity getTopNonFinishingActivity() {
        List<Activity> activities = collectActivities();
        if (activities.isEmpty()) {
            return null;
        }
        int i = activities.size() - 1;
        while (i >= 0 && activities.get(i).isFinishing()) {
            i--;
        }
        return i >= 0 ? activities.get(i) : null;
    }

    boolean isEmpty() {
        return mPendingAppearedActivities.isEmpty() && (mInfo == null || mInfo.isEmpty());
    }

    /**
     * Adds a container that should be finished when this container is finished.
     */
    void addContainerToFinishOnExit(@NonNull TaskFragmentContainer containerToFinish) {
        mContainersToFinishOnExit.add(containerToFinish);
    }

    /**
     * Removes a container that should be finished when this container is finished.
     */
    void removeContainerToFinishOnExit(@NonNull TaskFragmentContainer containerToRemove) {
        mContainersToFinishOnExit.remove(containerToRemove);
    }

    /**
     * Adds an activity that should be finished when this container is finished.
     */
    void addActivityToFinishOnExit(@NonNull Activity activityToFinish) {
        mActivitiesToFinishOnExit.add(activityToFinish);
    }

    /**
     * Removes an activity that should be finished when this container is finished.
     */
    void removeActivityToFinishOnExit(@NonNull Activity activityToRemove) {
        mActivitiesToFinishOnExit.remove(activityToRemove);
    }

    /** Removes all dependencies that should be finished when this container is finished. */
    void resetDependencies() {
        mContainersToFinishOnExit.clear();
        mActivitiesToFinishOnExit.clear();
    }

    /**
     * Removes all activities that belong to this process and finishes other containers/activities
     * configured to finish together.
     */
    void finish(boolean shouldFinishDependent, @NonNull SplitPresenter presenter,
            @NonNull WindowContainerTransaction wct, @NonNull SplitController controller) {
        if (!mIsFinished) {
            mIsFinished = true;
            if (mAppearEmptyTimeout != null) {
                mController.getHandler().removeCallbacks(mAppearEmptyTimeout);
                mAppearEmptyTimeout = null;
            }
            finishActivities(shouldFinishDependent, presenter, wct, controller);
        }

        if (mInfo == null) {
            // Defer removal the container and wait until TaskFragment appeared.
            return;
        }

        // Cleanup the visuals
        presenter.deleteTaskFragment(wct, getTaskFragmentToken());
        // Cleanup the records
        controller.removeContainer(this);
        // Clean up task fragment information
        mInfo = null;
    }

    private void finishActivities(boolean shouldFinishDependent, @NonNull SplitPresenter presenter,
            @NonNull WindowContainerTransaction wct, @NonNull SplitController controller) {
        // Finish own activities
        for (Activity activity : collectActivities()) {
            if (!activity.isFinishing()) {
                activity.finish();
            }
        }

        if (!shouldFinishDependent) {
            return;
        }

        // Finish dependent containers
        for (TaskFragmentContainer container : mContainersToFinishOnExit) {
            if (container.mIsFinished
                    || controller.shouldRetainAssociatedContainer(this, container)) {
                continue;
            }
            container.finish(true /* shouldFinishDependent */, presenter,
                    wct, controller);
        }
        mContainersToFinishOnExit.clear();

        // Finish associated activities
        for (Activity activity : mActivitiesToFinishOnExit) {
            if (activity.isFinishing()
                    || controller.shouldRetainAssociatedActivity(this, activity)) {
                continue;
            }
            activity.finish();
        }
        mActivitiesToFinishOnExit.clear();
    }

    boolean isFinished() {
        return mIsFinished;
    }

    /**
     * Checks if last requested bounds are equal to the provided value.
     */
    boolean areLastRequestedBoundsEqual(@Nullable Rect bounds) {
        return (bounds == null && mLastRequestedBounds.isEmpty())
                || mLastRequestedBounds.equals(bounds);
    }

    /**
     * Updates the last requested bounds.
     */
    void setLastRequestedBounds(@Nullable Rect bounds) {
        if (bounds == null) {
            mLastRequestedBounds.setEmpty();
        } else {
            mLastRequestedBounds.set(bounds);
        }
    }

    /**
     * Checks if last requested windowing mode is equal to the provided value.
     */
    boolean isLastRequestedWindowingModeEqual(@WindowingMode int windowingMode) {
        return mLastRequestedWindowingMode == windowingMode;
    }

    /**
     * Updates the last requested windowing mode.
     */
    void setLastRequestedWindowingMode(@WindowingMode int windowingModes) {
        mLastRequestedWindowingMode = windowingModes;
    }

    /** Gets the parent leaf Task id. */
    int getTaskId() {
        return mTaskContainer.getTaskId();
    }

    /** Gets the parent Task. */
    @NonNull
    TaskContainer getTaskContainer() {
        return mTaskContainer;
    }

    @Override
    public String toString() {
        return toString(true /* includeContainersToFinishOnExit */);
    }

    /**
     * @return string for this TaskFragmentContainer and includes containers to finish on exit
     * based on {@code includeContainersToFinishOnExit}. If containers to finish on exit are always
     * included in the string, then calling {@link #toString()} on a container that mutually
     * finishes with another container would cause a stack overflow.
     */
    private String toString(boolean includeContainersToFinishOnExit) {
        return "TaskFragmentContainer{"
                + " parentTaskId=" + getTaskId()
                + " token=" + mToken
                + " topNonFinishingActivity=" + getTopNonFinishingActivity()
                + " runningActivityCount=" + getRunningActivityCount()
                + " isFinished=" + mIsFinished
                + " lastRequestedBounds=" + mLastRequestedBounds
                + " pendingAppearedActivities=" + mPendingAppearedActivities
                + (includeContainersToFinishOnExit ? " containersToFinishOnExit="
                + containersToFinishOnExitToString() : "")
                + " activitiesToFinishOnExit=" + mActivitiesToFinishOnExit
                + " info=" + mInfo
                + "}";
    }

    private String containersToFinishOnExitToString() {
        StringBuilder sb = new StringBuilder("[");
        Iterator<TaskFragmentContainer> containerIterator = mContainersToFinishOnExit.iterator();
        while (containerIterator.hasNext()) {
            sb.append(containerIterator.next().toString(
                    false /* includeContainersToFinishOnExit */));
            if (containerIterator.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.append("]").toString();
    }
}
