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

package androidx.window.extensions.layout;

import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.window.common.CommonFoldingFeature.COMMON_STATE_FLAT;
import static androidx.window.common.CommonFoldingFeature.COMMON_STATE_HALF_OPENED;
import static androidx.window.util.ExtensionHelper.isZero;
import static androidx.window.util.ExtensionHelper.rotateRectToDisplayRotation;
import static androidx.window.util.ExtensionHelper.transformToWindowSpaceRect;

import android.app.Activity;
import android.app.ActivityClient;
import android.app.Application;
import android.app.WindowConfiguration;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.util.ArrayMap;
import android.window.WindowContext;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiContext;
import androidx.window.common.CommonFoldingFeature;
import androidx.window.common.DeviceStateManagerFoldingFeatureProducer;
import androidx.window.common.EmptyLifecycleCallbacksAdapter;
import androidx.window.common.RawFoldingFeatureProducer;
import androidx.window.util.DataProducer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reference implementation of androidx.window.extensions.layout OEM interface for use with
 * WindowManager Jetpack.
 *
 * NOTE: This version is a work in progress and under active development. It MUST NOT be used in
 * production builds since the interface can still change before reaching stable version.
 * Please refer to {@link androidx.window.sidecar.SampleSidecarImpl} instead.
 */
public class WindowLayoutComponentImpl implements WindowLayoutComponent {
    private static final String TAG = "SampleExtension";

    private final Map<Context, Consumer<WindowLayoutInfo>> mWindowLayoutChangeListeners =
            new ArrayMap<>();

    private final DataProducer<List<CommonFoldingFeature>> mFoldingFeatureProducer;

    private final Map<IBinder, WindowContextConfigListener> mWindowContextConfigListeners =
            new ArrayMap<>();

    public WindowLayoutComponentImpl(@NonNull Context context) {
        ((Application) context.getApplicationContext())
                .registerActivityLifecycleCallbacks(new NotifyOnConfigurationChanged());
        RawFoldingFeatureProducer foldingFeatureProducer = new RawFoldingFeatureProducer(context);
        mFoldingFeatureProducer = new DeviceStateManagerFoldingFeatureProducer(context,
                foldingFeatureProducer);
        mFoldingFeatureProducer.addDataChangedCallback(this::onDisplayFeaturesChanged);
    }

    /**
     * Adds a listener interested in receiving updates to {@link WindowLayoutInfo}
     *
     * @param activity hosting a {@link android.view.Window}
     * @param consumer interested in receiving updates to {@link WindowLayoutInfo}
     */
    @Override
    public void addWindowLayoutInfoListener(@NonNull Activity activity,
            @NonNull Consumer<WindowLayoutInfo> consumer) {
        addWindowLayoutInfoListener((Context) activity, consumer);
    }

    /**
     * Similar to {@link #addWindowLayoutInfoListener(Activity, Consumer)}, but takes a UI Context
     * as a parameter.
     */
    // TODO(b/204073440): Add @Override to hook the API in WM extensions library.
    public void addWindowLayoutInfoListener(@NonNull @UiContext Context context,
            @NonNull Consumer<WindowLayoutInfo> consumer) {
        if (mWindowLayoutChangeListeners.containsKey(context)
                || mWindowLayoutChangeListeners.containsValue(consumer)) {
            // Early return if the listener or consumer has been registered.
            return;
        }
        if (!context.isUiContext()) {
            throw new IllegalArgumentException("Context must be a UI Context, which should be"
                    + " an Activity or a WindowContext");
        }
        mFoldingFeatureProducer.getData((features) -> {
            // Get the WindowLayoutInfo from the activity and pass the value to the layoutConsumer.
            WindowLayoutInfo newWindowLayout = getWindowLayoutInfo(context, features);
            consumer.accept(newWindowLayout);
        });
        mWindowLayoutChangeListeners.put(context, consumer);

        if (context instanceof WindowContext) {
            final IBinder windowContextToken = context.getWindowContextToken();
            final WindowContextConfigListener listener =
                    new WindowContextConfigListener(windowContextToken);
            context.registerComponentCallbacks(listener);
            mWindowContextConfigListeners.put(windowContextToken, listener);
        }
    }

    /**
     * Removes a listener no longer interested in receiving updates.
     *
     * @param consumer no longer interested in receiving updates to {@link WindowLayoutInfo}
     */
    @Override
    public void removeWindowLayoutInfoListener(@NonNull Consumer<WindowLayoutInfo> consumer) {
        for (Context context : mWindowLayoutChangeListeners.keySet()) {
            if (!mWindowLayoutChangeListeners.get(context).equals(consumer)) {
                continue;
            }
            if (context instanceof WindowContext) {
                final IBinder token = context.getWindowContextToken();
                context.unregisterComponentCallbacks(mWindowContextConfigListeners.get(token));
                mWindowContextConfigListeners.remove(token);
            }
            break;
        }
        mWindowLayoutChangeListeners.values().remove(consumer);
    }

    @NonNull
    Set<Context> getContextsListeningForLayoutChanges() {
        return mWindowLayoutChangeListeners.keySet();
    }

    private boolean isListeningForLayoutChanges(IBinder token) {
        for (Context context: getContextsListeningForLayoutChanges()) {
            if (token.equals(Context.getToken(context))) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasListeners() {
        return !mWindowLayoutChangeListeners.isEmpty();
    }

    /**
     * A convenience method to translate from the common feature state to the extensions feature
     * state.  More specifically, translates from {@link CommonFoldingFeature.State} to
     * {@link FoldingFeature#STATE_FLAT} or {@link FoldingFeature#STATE_HALF_OPENED}. If it is not
     * possible to translate, then we will return a {@code null} value.
     *
     * @param state if it matches a value in {@link CommonFoldingFeature.State}, {@code null}
     *              otherwise. @return a {@link FoldingFeature#STATE_FLAT} or
     *              {@link FoldingFeature#STATE_HALF_OPENED} if the given state matches a value in
     *              {@link CommonFoldingFeature.State} and {@code null} otherwise.
     */
    @Nullable
    private Integer convertToExtensionState(int state) {
        if (state == COMMON_STATE_FLAT) {
            return FoldingFeature.STATE_FLAT;
        } else if (state == COMMON_STATE_HALF_OPENED) {
            return FoldingFeature.STATE_HALF_OPENED;
        } else {
            return null;
        }
    }

    private void onDisplayFeaturesChanged(List<CommonFoldingFeature> storedFeatures) {
        for (Context context : getContextsListeningForLayoutChanges()) {
            // Get the WindowLayoutInfo from the activity and pass the value to the layoutConsumer.
            Consumer<WindowLayoutInfo> layoutConsumer = mWindowLayoutChangeListeners.get(context);
            WindowLayoutInfo newWindowLayout = getWindowLayoutInfo(context, storedFeatures);
            layoutConsumer.accept(newWindowLayout);
        }
    }

    /**
     * Translates the {@link DisplayFeature} into a {@link WindowLayoutInfo} when a
     * valid state is found.
     * @param context a proxy for the {@link android.view.Window} that contains the
     * {@link DisplayFeature}.
     */
    private WindowLayoutInfo getWindowLayoutInfo(@NonNull @UiContext Context context,
            List<CommonFoldingFeature> storedFeatures) {
        List<DisplayFeature> displayFeatureList = getDisplayFeatures(context, storedFeatures);
        return new WindowLayoutInfo(displayFeatureList);
    }

    /**
     * Translate from the {@link CommonFoldingFeature} to
     * {@link DisplayFeature} for a given {@link Activity}. If a
     * {@link CommonFoldingFeature} is not valid then it will be omitted.
     *
     * For a {@link FoldingFeature} the bounds are localized into the {@link Activity} window
     * coordinate space and the state is calculated from {@link CommonFoldingFeature#getState()}.
     * The state from {@link #mFoldingFeatureProducer} may not be valid since
     * {@link #mFoldingFeatureProducer} is a general state controller. If the state is not valid,
     * the {@link FoldingFeature} is omitted from the {@link List} of {@link DisplayFeature}. If the
     * bounds are not valid, constructing a {@link FoldingFeature} will throw an
     * {@link IllegalArgumentException} since this can cause negative UI effects down stream.
     *
     * @param context a proxy for the {@link android.view.Window} that contains the
     * {@link DisplayFeature}.
     * are within the {@link android.view.Window} of the {@link Activity}
     */
    private List<DisplayFeature> getDisplayFeatures(
            @NonNull @UiContext Context context, List<CommonFoldingFeature> storedFeatures) {
        List<DisplayFeature> features = new ArrayList<>();
        if (!shouldReportDisplayFeatures(context)) {
            return features;
        }

        int displayId = context.getDisplay().getDisplayId();
        for (CommonFoldingFeature baseFeature : storedFeatures) {
            Integer state = convertToExtensionState(baseFeature.getState());
            if (state == null) {
                continue;
            }
            Rect featureRect = baseFeature.getRect();
            rotateRectToDisplayRotation(displayId, featureRect);
            transformToWindowSpaceRect(context, featureRect);

            if (!isZero(featureRect)) {
                // TODO(b/228641877): Remove guarding when fixed.
                features.add(new FoldingFeature(featureRect, baseFeature.getType(), state));
            }
        }
        return features;
    }

    /**
     * Checks whether display features should be reported for the activity.
     * TODO(b/238948678): Support reporting display features in all windowing modes.
     */
    private boolean shouldReportDisplayFeatures(@NonNull @UiContext Context context) {
        int displayId = context.getDisplay().getDisplayId();
        if (displayId != DEFAULT_DISPLAY) {
            // Display features are not supported on secondary displays.
            return false;
        }
        final int windowingMode;
        if (context instanceof Activity) {
            windowingMode = ActivityClient.getInstance().getTaskWindowingMode(
                    context.getActivityToken());
        } else {
            windowingMode = context.getResources().getConfiguration().windowConfiguration
                    .getWindowingMode();
        }
        if (windowingMode == -1) {
            // If we cannot determine the task windowing mode for any reason, it is likely that we
            // won't be able to determine its position correctly as well. DisplayFeatures' bounds
            // in this case can't be computed correctly, so we should skip.
            return false;
        }
        // It is recommended not to report any display features in multi-window mode, since it
        // won't be possible to synchronize the display feature positions with window movement.
        return !WindowConfiguration.inMultiWindowMode(windowingMode);
    }

    private void onDisplayFeaturesChangedIfListening(@NonNull IBinder token) {
        if (isListeningForLayoutChanges(token)) {
            mFoldingFeatureProducer.getData(
                    WindowLayoutComponentImpl.this::onDisplayFeaturesChanged);
        }
    }

    private final class NotifyOnConfigurationChanged extends EmptyLifecycleCallbacksAdapter {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            super.onActivityCreated(activity, savedInstanceState);
            onDisplayFeaturesChangedIfListening(activity.getActivityToken());
        }

        @Override
        public void onActivityConfigurationChanged(Activity activity) {
            super.onActivityConfigurationChanged(activity);
            onDisplayFeaturesChangedIfListening(activity.getActivityToken());
        }
    }

    private final class WindowContextConfigListener implements ComponentCallbacks {
        final IBinder mToken;

        WindowContextConfigListener(IBinder token) {
            mToken = token;
        }

        @Override
        public void onConfigurationChanged(@NonNull Configuration newConfig) {
            onDisplayFeaturesChangedIfListening(mToken);
        }

        @Override
        public void onLowMemory() {}
    }
}
