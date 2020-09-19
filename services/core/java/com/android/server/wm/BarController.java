/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.annotation.NonNull;
import android.graphics.Rect;

/**
 * Controls state/behavior specific to a system bar window.
 */
public class BarController {
    private final int mWindowType;

    private final Rect mContentFrame = new Rect();

    BarController(int windowType) {
        mWindowType = windowType;
    }

    /**
     * Sets the frame within which the bar will display its content.
     *
     * This is used to determine if letterboxes interfere with the display of such content.
     */
    void setContentFrame(Rect frame) {
        mContentFrame.set(frame);
    }

    private Rect getContentFrame(@NonNull WindowState win) {
        final Rect rotatedContentFrame = win.mToken.getFixedRotationBarContentFrame(mWindowType);
        return rotatedContentFrame != null ? rotatedContentFrame : mContentFrame;
    }

    boolean isLightAppearanceAllowed(WindowState win) {
        if (win == null) {
            return true;
        }
        return !win.isLetterboxedOverlappingWith(getContentFrame(win));
    }

    boolean isTransparentAllowed(WindowState win) {
        if (win == null) {
            return true;
        }
        return win.letterboxNotIntersectsOrFullyContains(getContentFrame(win));
    }
}
