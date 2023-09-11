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
package com.android.wm.shell.common.split;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.window.TransitionInfo.FLAG_FIRST_CUSTOM;

import android.annotation.IntDef;

/** Helper utility class of methods and constants that are available to be imported in Launcher. */
public class SplitScreenConstants {

    /**
     * Split position isn't specified normally meaning to use what ever it is currently set to.
     */
    public static final int SPLIT_POSITION_UNDEFINED = -1;

    /**
     * Specifies that a split is positioned at the top half of the screen if
     * in portrait mode or at the left half of the screen if in landscape mode.
     */
    public static final int SPLIT_POSITION_TOP_OR_LEFT = 0;

    /**
     * Specifies that a split is positioned at the bottom half of the screen if
     * in portrait mode or at the right half of the screen if in landscape mode.
     */
    public static final int SPLIT_POSITION_BOTTOM_OR_RIGHT = 1;

    /**
     * Duration used for every split fade-in or fade-out.
     */
    public static final int FADE_DURATION = 133;

    @IntDef(prefix = {"SPLIT_POSITION_"}, value = {
            SPLIT_POSITION_UNDEFINED,
            SPLIT_POSITION_TOP_OR_LEFT,
            SPLIT_POSITION_BOTTOM_OR_RIGHT
    })
    public @interface SplitPosition {
    }

    /** The divider doesn't snap to any target and is freely placeable. */
    public static final int SNAP_TO_NONE = 0;

    /** A snap target positioned near the screen edge for a minimized task */
    public static final int SNAP_TO_MINIMIZE = 1;

    /** If the divider reaches this value, the left/top task should be dismissed. */
    public static final int SNAP_TO_START_AND_DISMISS = 2;

    /** A snap target in the first half of the screen, where the split is roughly 30-70. */
    public static final int SNAP_TO_30_70 = 3;

    /** The 50-50 snap target */
    public static final int SNAP_TO_50_50 = 4;

    /** A snap target in the latter half of the screen, where the split is roughly 70-30. */
    public static final int SNAP_TO_70_30 = 5;

    /** If the divider reaches this value, the right/bottom task should be dismissed. */
    public static final int SNAP_TO_END_AND_DISMISS = 6;

    @IntDef(prefix = { "SNAP_TO_" }, value = {
            SNAP_TO_NONE,
            SNAP_TO_MINIMIZE,
            SNAP_TO_START_AND_DISMISS,
            SNAP_TO_30_70,
            SNAP_TO_50_50,
            SNAP_TO_70_30,
            SNAP_TO_END_AND_DISMISS
    })
    public @interface SnapPosition {}

    public static final int[] CONTROLLED_ACTIVITY_TYPES = {ACTIVITY_TYPE_STANDARD};
    public static final int[] CONTROLLED_WINDOWING_MODES =
            {WINDOWING_MODE_FULLSCREEN, WINDOWING_MODE_UNDEFINED};
    public static final int[] CONTROLLED_WINDOWING_MODES_WHEN_ACTIVE =
            {WINDOWING_MODE_FULLSCREEN, WINDOWING_MODE_UNDEFINED, WINDOWING_MODE_MULTI_WINDOW,
            WINDOWING_MODE_FREEFORM};

    /** Flag applied to a transition change to identify it as a divider bar for animation. */
    public static final int FLAG_IS_DIVIDER_BAR = FLAG_FIRST_CUSTOM;

    public static final String splitPositionToString(@SplitPosition int pos) {
        switch (pos) {
            case SPLIT_POSITION_UNDEFINED:
                return "SPLIT_POSITION_UNDEFINED";
            case SPLIT_POSITION_TOP_OR_LEFT:
                return "SPLIT_POSITION_TOP_OR_LEFT";
            case SPLIT_POSITION_BOTTOM_OR_RIGHT:
                return "SPLIT_POSITION_BOTTOM_OR_RIGHT";
            default:
                return "UNKNOWN";
        }
    }
}
