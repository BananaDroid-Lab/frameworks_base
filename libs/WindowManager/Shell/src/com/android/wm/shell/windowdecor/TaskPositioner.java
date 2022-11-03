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

package com.android.wm.shell.windowdecor;

import android.annotation.IntDef;
import android.graphics.PointF;
import android.graphics.Rect;
import android.window.WindowContainerTransaction;

import com.android.wm.shell.ShellTaskOrganizer;

class TaskPositioner implements DragResizeCallback {

    @IntDef({CTRL_TYPE_UNDEFINED, CTRL_TYPE_LEFT, CTRL_TYPE_RIGHT, CTRL_TYPE_TOP, CTRL_TYPE_BOTTOM})
    @interface CtrlType {}

    static final int CTRL_TYPE_UNDEFINED = 0;
    static final int CTRL_TYPE_LEFT = 1;
    static final int CTRL_TYPE_RIGHT = 2;
    static final int CTRL_TYPE_TOP = 4;
    static final int CTRL_TYPE_BOTTOM = 8;

    private final ShellTaskOrganizer mTaskOrganizer;
    private final WindowDecoration mWindowDecoration;

    private final Rect mTaskBoundsAtDragStart = new Rect();
    private final PointF mResizeStartPoint = new PointF();
    private final Rect mResizeTaskBounds = new Rect();

    private int mCtrlType;
    private DragStartListener mDragStartListener;

    TaskPositioner(ShellTaskOrganizer taskOrganizer, WindowDecoration windowDecoration,
            DragStartListener dragStartListener) {
        mTaskOrganizer = taskOrganizer;
        mWindowDecoration = windowDecoration;
        mDragStartListener = dragStartListener;
    }

    @Override
    public void onDragResizeStart(int ctrlType, float x, float y) {
        mDragStartListener.onDragStart(mWindowDecoration.mTaskInfo.taskId);
        mCtrlType = ctrlType;

        mTaskBoundsAtDragStart.set(
                mWindowDecoration.mTaskInfo.configuration.windowConfiguration.getBounds());
        mResizeStartPoint.set(x, y);
    }

    @Override
    public void onDragResizeMove(float x, float y) {
        changeBounds(x, y);
    }

    @Override
    public void onDragResizeEnd(float x, float y) {
        changeBounds(x, y);

        mCtrlType = 0;
        mTaskBoundsAtDragStart.setEmpty();
        mResizeStartPoint.set(0, 0);
    }

    private void changeBounds(float x, float y) {
        float deltaX = x - mResizeStartPoint.x;
        mResizeTaskBounds.set(mTaskBoundsAtDragStart);
        if ((mCtrlType & CTRL_TYPE_LEFT) != 0) {
            mResizeTaskBounds.left += deltaX;
        }
        if ((mCtrlType & CTRL_TYPE_RIGHT) != 0) {
            mResizeTaskBounds.right += deltaX;
        }
        float deltaY = y - mResizeStartPoint.y;
        if ((mCtrlType & CTRL_TYPE_TOP) != 0) {
            mResizeTaskBounds.top += deltaY;
        }
        if ((mCtrlType & CTRL_TYPE_BOTTOM) != 0) {
            mResizeTaskBounds.bottom += deltaY;
        }
        if (mCtrlType == 0) {
            mResizeTaskBounds.offset((int) deltaX, (int) deltaY);
        }

        if (!mResizeTaskBounds.isEmpty()) {
            final WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.setBounds(mWindowDecoration.mTaskInfo.token, mResizeTaskBounds);
            mTaskOrganizer.applyTransaction(wct);
        }
    }

    interface DragStartListener {
        /**
         * Inform the implementing class that a drag resize has started
         * @param taskId id of this positioner's {@link WindowDecoration}
         */
        void onDragStart(int taskId);
    }
}
