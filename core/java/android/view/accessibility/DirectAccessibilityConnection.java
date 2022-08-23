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

package android.view.accessibility;

import android.accessibilityservice.IAccessibilityServiceConnection;
import android.graphics.Matrix;
import android.graphics.Region;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.view.MagnificationSpec;

/**
 * Minimal {@link IAccessibilityServiceConnection} implementation that interacts
 * with the {@link android.view.AccessibilityInteractionController} of a
 * {@link android.view.ViewRootImpl}.
 *
 * <p>
 * Uses {@link android.view.ViewRootImpl}'s {@link IAccessibilityServiceConnection} that wraps
 * {@link android.view.AccessibilityInteractionController} within the app process, so that no
 * interprocess communication is performed.
 * </p>
 *
 * <p>
 * Only the following methods are supported:
 * <li>{@link #findAccessibilityNodeInfoByAccessibilityId}</li>
 * <li>{@link #findAccessibilityNodeInfosByText}</li>
 * <li>{@link #findAccessibilityNodeInfosByViewId}</li>
 * <li>{@link #findFocus}</li>
 * <li>{@link #focusSearch}</li>
 * <li>{@link #performAccessibilityAction}</li>
 * </p>
 *
 * <p>
 * Other methods are no-ops and return default values.
 * </p>
 */
class DirectAccessibilityConnection extends IAccessibilityServiceConnection.Default {
    private final IAccessibilityInteractionConnection mAccessibilityInteractionConnection;

    // Fetch all views, but do not use prefetching/cache since this "connection" does not
    // receive cache invalidation events (as it is not linked to an AccessibilityService).
    private static final int FETCH_FLAGS =
            AccessibilityNodeInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityNodeInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
    private static final MagnificationSpec MAGNIFICATION_SPEC = new MagnificationSpec();
    private static final int PID = Process.myPid();
    private static final Region INTERACTIVE_REGION = null;
    private static final float[] TRANSFORM_MATRIX = new float[9];

    static {
        Matrix.IDENTITY_MATRIX.getValues(TRANSFORM_MATRIX);
    }

    DirectAccessibilityConnection(
            IAccessibilityInteractionConnection accessibilityInteractionConnection) {
        mAccessibilityInteractionConnection = accessibilityInteractionConnection;
    }

    @Override
    public String[] findAccessibilityNodeInfoByAccessibilityId(int accessibilityWindowId,
            long accessibilityNodeId, int interactionId,
            IAccessibilityInteractionConnectionCallback callback, int flags, long threadId,
            Bundle arguments) throws RemoteException {
        mAccessibilityInteractionConnection.findAccessibilityNodeInfoByAccessibilityId(
                accessibilityNodeId, INTERACTIVE_REGION, interactionId, callback, FETCH_FLAGS, PID,
                threadId, MAGNIFICATION_SPEC, TRANSFORM_MATRIX, arguments);
        return new String[0];
    }

    @Override
    public String[] findAccessibilityNodeInfosByText(int accessibilityWindowId,
            long accessibilityNodeId, String text, int interactionId,
            IAccessibilityInteractionConnectionCallback callback, long threadId)
            throws RemoteException {
        mAccessibilityInteractionConnection.findAccessibilityNodeInfosByText(accessibilityNodeId,
                text, INTERACTIVE_REGION, interactionId, callback, FETCH_FLAGS, PID, threadId,
                MAGNIFICATION_SPEC, TRANSFORM_MATRIX);
        return new String[0];
    }

    @Override
    public String[] findAccessibilityNodeInfosByViewId(int accessibilityWindowId,
            long accessibilityNodeId, String viewId, int interactionId,
            IAccessibilityInteractionConnectionCallback callback, long threadId)
            throws RemoteException {
        mAccessibilityInteractionConnection.findAccessibilityNodeInfosByViewId(accessibilityNodeId,
                viewId, INTERACTIVE_REGION, interactionId, callback, FETCH_FLAGS, PID, threadId,
                MAGNIFICATION_SPEC, TRANSFORM_MATRIX);
        return new String[0];
    }

    @Override
    public String[] findFocus(int accessibilityWindowId, long accessibilityNodeId, int focusType,
            int interactionId, IAccessibilityInteractionConnectionCallback callback, long threadId)
            throws RemoteException {
        mAccessibilityInteractionConnection.findFocus(accessibilityNodeId, focusType,
                INTERACTIVE_REGION, interactionId, callback, FETCH_FLAGS, PID, threadId,
                MAGNIFICATION_SPEC, TRANSFORM_MATRIX);
        return new String[0];
    }

    @Override
    public String[] focusSearch(int accessibilityWindowId, long accessibilityNodeId, int direction,
            int interactionId, IAccessibilityInteractionConnectionCallback callback, long threadId)
            throws RemoteException {
        mAccessibilityInteractionConnection.focusSearch(accessibilityNodeId, direction,
                INTERACTIVE_REGION, interactionId, callback, FETCH_FLAGS, PID, threadId,
                MAGNIFICATION_SPEC, TRANSFORM_MATRIX);
        return new String[0];
    }

    @Override
    public boolean performAccessibilityAction(int accessibilityWindowId, long accessibilityNodeId,
            int action, Bundle arguments, int interactionId,
            IAccessibilityInteractionConnectionCallback callback, long threadId)
            throws RemoteException {
        mAccessibilityInteractionConnection.performAccessibilityAction(accessibilityNodeId, action,
                arguments, interactionId, callback, FETCH_FLAGS, PID, threadId);
        return true;
    }
}
