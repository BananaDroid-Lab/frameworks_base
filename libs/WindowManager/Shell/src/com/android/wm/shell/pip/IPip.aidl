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

package com.android.wm.shell.pip;

import android.app.PictureInPictureParams;
import android.view.SurfaceControl;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;

import com.android.wm.shell.pip.IPipAnimationListener;

/**
 * Interface that is exposed to remote callers to manipulate the Pip feature.
 */
interface IPip {

    /**
     * Notifies that Activity is about to be swiped to home with entering PiP transition and
     * queries the destination bounds for PiP depends on Launcher's rotation and shelf height.
     *
     * @param componentName ComponentName represents the Activity
     * @param activityInfo ActivityInfo tied to the Activity
     * @param pictureInPictureParams PictureInPictureParams tied to the Activity
     * @param launcherRotation Launcher rotation to calculate the PiP destination bounds
     * @param hotseatKeepClearArea Bounds of Hotseat to avoid used to calculate PiP destination
              bounds
     * @return destination bounds the PiP window should land into
     */
    Rect startSwipePipToHome(in ComponentName componentName, in ActivityInfo activityInfo,
                in PictureInPictureParams pictureInPictureParams,
                int launcherRotation, in Rect hotseatKeepClearArea) = 1;

    /**
     * Notifies the swiping Activity to PiP onto home transition is finished
     *
     * @param taskId the Task id that the Activity and overlay are currently in.
     * @param componentName ComponentName represents the Activity
     * @param destinationBounds the destination bounds the PiP window lands into
     * @param overlay an optional overlay to fade out after entering PiP
     */
    oneway void stopSwipePipToHome(int taskId, in ComponentName componentName,
            in Rect destinationBounds, in SurfaceControl overlay) = 2;

    /**
     * Sets listener to get pinned stack animation callbacks.
     */
    oneway void setPipAnimationListener(IPipAnimationListener listener) = 3;

    /**
     * Sets the shelf height and visibility.
     */
    oneway void setShelfHeight(boolean visible, int shelfHeight) = 4;

    /**
     * Sets the next pip animation type to be the alpha animation.
     */
    oneway void setPipAnimationTypeToAlpha() = 5;
}
