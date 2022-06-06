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

package com.android.wm.shell.unfold;

import android.annotation.NonNull;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.TaskInfo;
import android.util.SparseArray;
import android.view.SurfaceControl;

import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.unfold.ShellUnfoldProgressProvider.UnfoldListener;
import com.android.wm.shell.unfold.animation.UnfoldTaskAnimator;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import dagger.Lazy;

/**
 * Manages fold/unfold animations of tasks on foldable devices.
 * When folding or unfolding a foldable device we play animations that
 * transform task cropping/scaling/rounded corners.
 *
 * This controller manages:
 *  1) Folding/unfolding when Shell transitions disabled
 *  2) Folding when Shell transitions enabled, unfolding is managed by
 *    {@link com.android.wm.shell.unfold.UnfoldTransitionHandler}
 */
public class UnfoldAnimationController implements UnfoldListener {

    private final ShellUnfoldProgressProvider mUnfoldProgressProvider;
    private final Executor mExecutor;
    private final TransactionPool mTransactionPool;
    private final List<UnfoldTaskAnimator> mAnimators;
    private final Lazy<Optional<UnfoldTransitionHandler>> mUnfoldTransitionHandler;

    private final SparseArray<SurfaceControl> mTaskSurfaces = new SparseArray<>();
    private final SparseArray<UnfoldTaskAnimator> mAnimatorsByTaskId = new SparseArray<>();

    public UnfoldAnimationController(@NonNull TransactionPool transactionPool,
            @NonNull ShellUnfoldProgressProvider unfoldProgressProvider,
            @NonNull List<UnfoldTaskAnimator> animators,
            @NonNull Lazy<Optional<UnfoldTransitionHandler>> unfoldTransitionHandler,
            @NonNull Executor executor) {
        mUnfoldProgressProvider = unfoldProgressProvider;
        mUnfoldTransitionHandler = unfoldTransitionHandler;
        mTransactionPool = transactionPool;
        mExecutor = executor;
        mAnimators = animators;
    }

    /**
     * Initializes the controller, starts listening for the external events
     */
    public void init() {
        mUnfoldProgressProvider.addListener(mExecutor, this);

        for (int i = 0; i < mAnimators.size(); i++) {
            final UnfoldTaskAnimator animator = mAnimators.get(i);
            animator.init();
            animator.start();
        }
    }

    /**
     * Called when a task appeared
     * @param taskInfo info for the appeared task
     * @param leash surface leash for the appeared task
     */
    public void onTaskAppeared(RunningTaskInfo taskInfo, SurfaceControl leash) {
        mTaskSurfaces.put(taskInfo.taskId, leash);

        // Find the first matching animator
        for (int i = 0; i < mAnimators.size(); i++) {
            final UnfoldTaskAnimator animator = mAnimators.get(i);
            if (animator.isApplicableTask(taskInfo)) {
                mAnimatorsByTaskId.put(taskInfo.taskId, animator);
                animator.onTaskAppeared(taskInfo, leash);
                break;
            }
        }
    }

    /**
     * Called when task info changed
     * @param taskInfo info for the changed task
     */
    public void onTaskInfoChanged(RunningTaskInfo taskInfo) {
        final UnfoldTaskAnimator animator = mAnimatorsByTaskId.get(taskInfo.taskId);
        final boolean isCurrentlyApplicable = animator != null;

        if (isCurrentlyApplicable) {
            final boolean isApplicable = animator.isApplicableTask(taskInfo);
            if (isApplicable) {
                // Still applicable, send update
                animator.onTaskChanged(taskInfo);
            } else {
                // Became inapplicable
                resetTask(animator, taskInfo);
                animator.onTaskVanished(taskInfo);
                mAnimatorsByTaskId.remove(taskInfo.taskId);
            }
        } else {
            // Find the first matching animator
            for (int i = 0; i < mAnimators.size(); i++) {
                final UnfoldTaskAnimator currentAnimator = mAnimators.get(i);
                if (currentAnimator.isApplicableTask(taskInfo)) {
                    // Became applicable
                    mAnimatorsByTaskId.put(taskInfo.taskId, currentAnimator);

                    SurfaceControl leash = mTaskSurfaces.get(taskInfo.taskId);
                    currentAnimator.onTaskAppeared(taskInfo, leash);
                    break;
                }
            }
        }
    }

    /**
     * Called when a task vanished
     * @param taskInfo info for the vanished task
     */
    public void onTaskVanished(RunningTaskInfo taskInfo) {
        mTaskSurfaces.remove(taskInfo.taskId);

        final UnfoldTaskAnimator animator = mAnimatorsByTaskId.get(taskInfo.taskId);
        final boolean isCurrentlyApplicable = animator != null;

        if (isCurrentlyApplicable) {
            resetTask(animator, taskInfo);
            animator.onTaskVanished(taskInfo);
            mAnimatorsByTaskId.remove(taskInfo.taskId);
        }
    }

    @Override
    public void onStateChangeStarted() {
        if (mUnfoldTransitionHandler.get().get().willHandleTransition()) {
            return;
        }

        SurfaceControl.Transaction transaction = null;
        for (int i = 0; i < mAnimators.size(); i++) {
            final UnfoldTaskAnimator animator = mAnimators.get(i);
            if (animator.hasActiveTasks()) {
                if (transaction == null) transaction = mTransactionPool.acquire();
                animator.prepareStartTransaction(transaction);
            }
        }

        if (transaction != null) {
            transaction.apply();
            mTransactionPool.release(transaction);
        }
    }

    @Override
    public void onStateChangeProgress(float progress) {
        if (mUnfoldTransitionHandler.get().get().willHandleTransition()) {
            return;
        }

        SurfaceControl.Transaction transaction = null;
        for (int i = 0; i < mAnimators.size(); i++) {
            final UnfoldTaskAnimator animator = mAnimators.get(i);
            if (animator.hasActiveTasks()) {
                if (transaction == null) transaction = mTransactionPool.acquire();
                animator.applyAnimationProgress(progress, transaction);
            }
        }

        if (transaction != null) {
            transaction.apply();
            mTransactionPool.release(transaction);
        }
    }

    @Override
    public void onStateChangeFinished() {
        if (mUnfoldTransitionHandler.get().get().willHandleTransition()) {
            return;
        }

        final SurfaceControl.Transaction transaction = mTransactionPool.acquire();

        for (int i = 0; i < mAnimators.size(); i++) {
            final UnfoldTaskAnimator animator = mAnimators.get(i);
            animator.resetAllSurfaces(transaction);
            animator.prepareFinishTransaction(transaction);
        }

        transaction.apply();

        mTransactionPool.release(transaction);
    }

    private void resetTask(UnfoldTaskAnimator animator, TaskInfo taskInfo) {
        final SurfaceControl.Transaction transaction = mTransactionPool.acquire();
        animator.resetSurface(taskInfo, transaction);
        transaction.apply();
        mTransactionPool.release(transaction);
    }
}
