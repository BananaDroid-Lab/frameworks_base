/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.systemui.pip.phone;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;

import static com.android.systemui.pip.PipAnimationController.isOutPipDirection;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityManager;
import android.app.RemoteAction;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.util.Pair;
import android.view.DisplayInfo;
import android.view.IPinnedStackController;
import android.window.WindowContainerTransaction;

import com.android.systemui.Dependency;
import com.android.systemui.UiOffloadThread;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.model.SysUiState;
import com.android.systemui.pip.Pip;
import com.android.systemui.pip.PipBoundsHandler;
import com.android.systemui.pip.PipSurfaceTransactionHelper;
import com.android.systemui.pip.PipTaskOrganizer;
import com.android.systemui.pip.PipUiEventLogger;
import com.android.systemui.shared.recents.IPinnedStackAnimationListener;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.PinnedStackListenerForwarder.PinnedStackListener;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.WindowManagerWrapper;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.DeviceConfigProxy;
import com.android.systemui.util.FloatingContentCoordinator;
import com.android.wm.shell.common.DisplayChangeController;
import com.android.wm.shell.common.DisplayController;

import java.io.PrintWriter;

/**
 * Manages the picture-in-picture (PIP) UI and states for Phones.
 */
@SysUISingleton
public class PipController implements Pip, PipTaskOrganizer.PipTransitionCallback {
    private static final String TAG = "PipController";

    private Context mContext;
    private IActivityManager mActivityManager;
    private Handler mHandler = new Handler();

    private final DisplayInfo mTmpDisplayInfo = new DisplayInfo();
    private final Rect mTmpInsetBounds = new Rect();
    private final Rect mTmpNormalBounds = new Rect();
    protected final Rect mReentryBounds = new Rect();

    private DisplayController mDisplayController;
    private InputConsumerController mInputConsumerController;
    private PipAppOpsListener mAppOpsListener;
    private PipBoundsHandler mPipBoundsHandler;
    private PipMediaController mMediaController;
    private PipTouchHandler mTouchHandler;
    private PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;
    private IPinnedStackAnimationListener mPinnedStackAnimationRecentsListener;
    private boolean mIsInFixedRotation;

    protected PipMenuActivityController mMenuController;
    protected PipTaskOrganizer mPipTaskOrganizer;

    /**
     * Handler for display rotation changes.
     */
    private final DisplayChangeController.OnDisplayChangingListener mRotationController = (
            int displayId, int fromRotation, int toRotation, WindowContainerTransaction t) -> {
        if (!mPipTaskOrganizer.isInPip() || mPipTaskOrganizer.isDeferringEnterPipAnimation()) {
            // Skip if we aren't in PIP or haven't actually entered PIP yet. We still need to update
            // the display layout in the bounds handler in this case.
            mPipBoundsHandler.onDisplayRotationChangedNotInPip(mContext, toRotation);
            return;
        }
        // If there is an animation running (ie. from a shelf offset), then ensure that we calculate
        // the bounds for the next orientation using the destination bounds of the animation
        // TODO: Techincally this should account for movement animation bounds as well
        Rect currentBounds = mPipTaskOrganizer.getCurrentOrAnimatingBounds();
        final boolean changed = mPipBoundsHandler.onDisplayRotationChanged(mContext,
                mTmpNormalBounds, currentBounds, mTmpInsetBounds, displayId, fromRotation,
                toRotation, t);
        if (changed) {
            // If the pip was in the offset zone earlier, adjust the new bounds to the bottom of the
            // movement bounds
            mTouchHandler.adjustBoundsForRotation(mTmpNormalBounds,
                    mPipTaskOrganizer.getLastReportedBounds(), mTmpInsetBounds);

            // The bounds are being applied to a specific snap fraction, so reset any known offsets
            // for the previous orientation before updating the movement bounds.
            // We perform the resets if and only if this callback is due to screen rotation but
            // not during the fixed rotation. In fixed rotation case, app is about to enter PiP
            // and we need the offsets preserved to calculate the destination bounds.
            if (!mIsInFixedRotation) {
                mPipBoundsHandler.setShelfHeight(false, 0);
                mPipBoundsHandler.onImeVisibilityChanged(false, 0);
                mTouchHandler.onShelfVisibilityChanged(false, 0);
                mTouchHandler.onImeVisibilityChanged(false, 0);
            }

            updateMovementBounds(mTmpNormalBounds, true /* fromRotation */,
                    false /* fromImeAdjustment */, false /* fromShelfAdjustment */, t);
        }
    };

    private DisplayController.OnDisplaysChangedListener mFixedRotationListener =
            new DisplayController.OnDisplaysChangedListener() {
                @Override
                public void onFixedRotationStarted(int displayId, int newRotation) {
                    mIsInFixedRotation = true;
                }

                @Override
                public void onFixedRotationFinished(int displayId) {
                    mIsInFixedRotation = false;
                }

                @Override
                public void onDisplayAdded(int displayId) {
                    mPipBoundsHandler.setDisplayLayout(
                            mDisplayController.getDisplayLayout(displayId));
                }
            };

    /**
     * Handler for system task stack changes.
     */
    private final TaskStackChangeListener mTaskStackListener = new TaskStackChangeListener() {
        @Override
        public void onActivityPinned(String packageName, int userId, int taskId, int stackId) {
            mTouchHandler.onActivityPinned();
            mMediaController.onActivityPinned();
            mMenuController.onActivityPinned();
            mAppOpsListener.onActivityPinned(packageName);

            Dependency.get(UiOffloadThread.class).execute(() -> {
                WindowManagerWrapper.getInstance().setPipVisibility(true);
            });
        }

        @Override
        public void onActivityUnpinned() {
            final Pair<ComponentName, Integer> topPipActivityInfo = PipUtils.getTopPipActivity(
                    mContext, mActivityManager);
            final ComponentName topActivity = topPipActivityInfo.first;
            mMenuController.onActivityUnpinned();
            mTouchHandler.onActivityUnpinned(topActivity);
            mAppOpsListener.onActivityUnpinned();

            Dependency.get(UiOffloadThread.class).execute(() -> {
                WindowManagerWrapper.getInstance().setPipVisibility(topActivity != null);
            });
        }

        @Override
        public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
                boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
            if (task.configuration.windowConfiguration.getWindowingMode()
                    != WINDOWING_MODE_PINNED) {
                return;
            }
            mTouchHandler.getMotionHelper().expandLeavePip(clearedTask /* skipAnimation */);
        }
    };

    /**
     * Handler for messages from the PIP controller.
     */
    private class PipControllerPinnedStackListener extends PinnedStackListener {
        @Override
        public void onListenerRegistered(IPinnedStackController controller) {
            mHandler.post(() -> mTouchHandler.setPinnedStackController(controller));
        }

        @Override
        public void onImeVisibilityChanged(boolean imeVisible, int imeHeight) {
            mHandler.post(() -> {
                mPipBoundsHandler.onImeVisibilityChanged(imeVisible, imeHeight);
                mTouchHandler.onImeVisibilityChanged(imeVisible, imeHeight);
            });
        }

        @Override
        public void onMovementBoundsChanged(boolean fromImeAdjustment) {
            mHandler.post(() -> updateMovementBounds(null /* toBounds */,
                    false /* fromRotation */, fromImeAdjustment, false /* fromShelfAdjustment */,
                    null /* windowContainerTransaction */));
        }

        @Override
        public void onActionsChanged(ParceledListSlice<RemoteAction> actions) {
            mHandler.post(() -> mMenuController.setAppActions(actions));
        }

        @Override
        public void onActivityHidden(ComponentName componentName) {
            mHandler.post(() -> mPipBoundsHandler.onResetReentryBounds(componentName));
        }

        @Override
        public void onDisplayInfoChanged(DisplayInfo displayInfo) {
            mHandler.post(() -> mPipBoundsHandler.onDisplayInfoChanged(displayInfo));
        }

        @Override
        public void onConfigurationChanged() {
            mHandler.post(() -> mPipBoundsHandler.onConfigurationChanged(mContext));
        }

        @Override
        public void onAspectRatioChanged(float aspectRatio) {
            mHandler.post(() -> {
                mPipBoundsHandler.onAspectRatioChanged(aspectRatio);
                mTouchHandler.onAspectRatioChanged();
            });
        }
    }

    public ConfigurationController.ConfigurationListener mOverlayChangedListener =
            new ConfigurationController.ConfigurationListener() {
                @Override
                public void onOverlayChanged() {
                    mHandler.post(() -> {
                        mPipBoundsHandler.onOverlayChanged(mContext, mContext.getDisplay());
                        updateMovementBounds(null /* toBounds */,
                                false /* fromRotation */, false /* fromImeAdjustment */,
                                false /* fromShelfAdjustment */,
                                null /* windowContainerTransaction */);
                    });
                }
            };

    public PipController(Context context, BroadcastDispatcher broadcastDispatcher,
            ConfigurationController configController,
            DeviceConfigProxy deviceConfig,
            DisplayController displayController,
            FloatingContentCoordinator floatingContentCoordinator,
            SysUiState sysUiState,
            PipBoundsHandler pipBoundsHandler,
            PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            PipTaskOrganizer pipTaskOrganizer,
            PipUiEventLogger pipUiEventLogger) {
        mContext = context;
        mActivityManager = ActivityManager.getService();

        PackageManager pm = context.getPackageManager();
        boolean supportsPip = pm.hasSystemFeature(FEATURE_PICTURE_IN_PICTURE);
        if (supportsPip) {
            initController(context, broadcastDispatcher, configController, deviceConfig,
                    displayController, floatingContentCoordinator, sysUiState, pipBoundsHandler,
                    pipSurfaceTransactionHelper, pipTaskOrganizer, pipUiEventLogger);
        } else {
            Log.w(TAG, "Device not support PIP feature");
        }
    }

    private void initController(Context context, BroadcastDispatcher broadcastDispatcher,
            ConfigurationController configController,
            DeviceConfigProxy deviceConfig,
            DisplayController displayController,
            FloatingContentCoordinator floatingContentCoordinator,
            SysUiState sysUiState,
            PipBoundsHandler pipBoundsHandler,
            PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            PipTaskOrganizer pipTaskOrganizer,
            PipUiEventLogger pipUiEventLogger) {

        // Ensure that we are the primary user's SystemUI.
        final int processUser = UserManager.get(context).getUserHandle();
        if (processUser != UserHandle.USER_SYSTEM) {
            throw new IllegalStateException("Non-primary Pip component not currently supported.");
        }

        try {
            WindowManagerWrapper.getInstance().addPinnedStackListener(
                    new PipControllerPinnedStackListener());
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to register pinned stack listener", e);
        }
        ActivityManagerWrapper.getInstance().registerTaskStackListener(mTaskStackListener);

        mDisplayController = displayController;
        mPipBoundsHandler = pipBoundsHandler;
        mPipSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        mPipTaskOrganizer = pipTaskOrganizer;
        mPipTaskOrganizer.registerPipTransitionCallback(this);
        mInputConsumerController = InputConsumerController.getPipInputConsumer();
        mMediaController = new PipMediaController(context, mActivityManager, broadcastDispatcher);
        mMenuController = new PipMenuActivityController(context,
                mMediaController, mInputConsumerController, mPipTaskOrganizer);
        mTouchHandler = new PipTouchHandler(context, mActivityManager,
                mMenuController, mInputConsumerController, mPipBoundsHandler, mPipTaskOrganizer,
                floatingContentCoordinator, deviceConfig, sysUiState, pipUiEventLogger);
        mAppOpsListener = new PipAppOpsListener(context, mActivityManager,
                mTouchHandler.getMotionHelper());
        displayController.addDisplayChangingController(mRotationController);
        displayController.addDisplayWindowListener(mFixedRotationListener);

        // Ensure that we have the display info in case we get calls to update the bounds before the
        // listener calls back
        final DisplayInfo displayInfo = new DisplayInfo();
        context.getDisplay().getDisplayInfo(displayInfo);
        mPipBoundsHandler.onDisplayInfoChanged(displayInfo);

        configController.addCallback(mOverlayChangedListener);

        try {
            RootTaskInfo taskInfo = ActivityTaskManager.getService().getRootTaskInfo(
                    WINDOWING_MODE_PINNED, ACTIVITY_TYPE_UNDEFINED);
            if (taskInfo != null) {
                // If SystemUI restart, and it already existed a pinned stack,
                // register the pip input consumer to ensure touch can send to it.
                mInputConsumerController.registerInputConsumer(true /* withSfVsync */);
            }
        } catch (RemoteException | UnsupportedOperationException e) {
            e.printStackTrace();
        }
    }

    /**
     * Updates the PIP per configuration changed.
     */
    public void onConfigurationChanged(Configuration newConfig) {
        mTouchHandler.onConfigurationChanged();
    }

    /**
     * Expands the PIP.
     */
    @Override
    public void expandPip() {
        mTouchHandler.getMotionHelper().expandLeavePip(false /* skipAnimation */);
    }

    /**
     * Hides the PIP menu.
     */
    @Override
    public void hidePipMenu(Runnable onStartCallback, Runnable onEndCallback) {
        mMenuController.hideMenu(onStartCallback, onEndCallback);
    }

    /**
     * Sent from KEYCODE_WINDOW handler in PhoneWindowManager, to request the menu to be shown.
     */
    public void showPictureInPictureMenu() {
        mTouchHandler.showPictureInPictureMenu();
    }

    /**
     * Sets a customized touch gesture that replaces the default one.
     */
    public void setTouchGesture(PipTouchGesture gesture) {
        mTouchHandler.setTouchGesture(gesture);
    }

    /**
     * Sets both shelf visibility and its height.
     */
    @Override
    public void setShelfHeight(boolean visible, int height) {
        mHandler.post(() -> {
            final int shelfHeight = visible ? height : 0;
            final boolean changed = mPipBoundsHandler.setShelfHeight(visible, shelfHeight);
            if (changed) {
                mTouchHandler.onShelfVisibilityChanged(visible, shelfHeight);
                updateMovementBounds(mPipTaskOrganizer.getLastReportedBounds(),
                        false /* fromRotation */, false /* fromImeAdjustment */,
                        true /* fromShelfAdjustment */, null /* windowContainerTransaction */);
            }
        });
    }

    @Override
    public void setPinnedStackAnimationType(int animationType) {
        mHandler.post(() -> mPipTaskOrganizer.setOneShotAnimationType(animationType));
    }

    @Override
    public void setPinnedStackAnimationListener(IPinnedStackAnimationListener listener) {
        mHandler.post(() -> mPinnedStackAnimationRecentsListener = listener);
    }

    @Override
    public void onPipTransitionStarted(ComponentName activity, int direction, Rect pipBounds) {
        if (isOutPipDirection(direction)) {
            // Exiting PIP, save the reentry bounds to restore to when re-entering.
            updateReentryBounds(pipBounds);
            mPipBoundsHandler.onSaveReentryBounds(activity, mReentryBounds);
        }
        // Disable touches while the animation is running
        mTouchHandler.setTouchEnabled(false);
        if (mPinnedStackAnimationRecentsListener != null) {
            try {
                mPinnedStackAnimationRecentsListener.onPinnedStackAnimationStarted();
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to callback recents", e);
            }
        }
    }

    /**
     * Update the bounds used to save the re-entry size and snap fraction when exiting PIP.
     */
    public void updateReentryBounds(Rect bounds) {
        final Rect reentryBounds = mTouchHandler.getUserResizeBounds();
        float snapFraction = mPipBoundsHandler.getSnapFraction(bounds);
        mPipBoundsHandler.applySnapFraction(reentryBounds, snapFraction);
        mReentryBounds.set(reentryBounds);
    }

    @Override
    public void onPipTransitionFinished(ComponentName activity, int direction) {
        onPipTransitionFinishedOrCanceled(direction);
    }

    @Override
    public void onPipTransitionCanceled(ComponentName activity, int direction) {
        onPipTransitionFinishedOrCanceled(direction);
    }

    private void onPipTransitionFinishedOrCanceled(int direction) {
        // Re-enable touches after the animation completes
        mTouchHandler.setTouchEnabled(true);
        mTouchHandler.onPinnedStackAnimationEnded(direction);
        mMenuController.onPinnedStackAnimationEnded();
    }

    private void updateMovementBounds(@Nullable Rect toBounds, boolean fromRotation,
            boolean fromImeAdjustment, boolean fromShelfAdjustment,
            WindowContainerTransaction wct) {
        // Populate inset / normal bounds and DisplayInfo from mPipBoundsHandler before
        // passing to mTouchHandler/mPipTaskOrganizer
        final Rect outBounds = new Rect(toBounds);
        mPipBoundsHandler.onMovementBoundsChanged(mTmpInsetBounds, mTmpNormalBounds,
                outBounds, mTmpDisplayInfo);
        // mTouchHandler would rely on the bounds populated from mPipTaskOrganizer
        mPipTaskOrganizer.onMovementBoundsChanged(outBounds, fromRotation, fromImeAdjustment,
                fromShelfAdjustment, wct);
        mTouchHandler.onMovementBoundsChanged(mTmpInsetBounds, mTmpNormalBounds,
                outBounds, fromImeAdjustment, fromShelfAdjustment,
                mTmpDisplayInfo.rotation);
    }

    @Override
    public void dump(PrintWriter pw) {
        final String innerPrefix = "  ";
        pw.println(TAG);
        mInputConsumerController.dump(pw, innerPrefix);
        mMenuController.dump(pw, innerPrefix);
        mTouchHandler.dump(pw, innerPrefix);
        mPipBoundsHandler.dump(pw, innerPrefix);
        mPipTaskOrganizer.dump(pw, innerPrefix);
    }
}
