/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.dagger;

import android.content.BroadcastReceiver;

import com.android.systemui.GuestResetOrExitSessionReceiver;
import com.android.systemui.media.dialog.MediaOutputDialogReceiver;
import com.android.systemui.people.widget.PeopleSpaceWidgetPinnedReceiver;
import com.android.systemui.people.widget.PeopleSpaceWidgetProvider;
import com.android.systemui.screenshot.ActionProxyReceiver;
import com.android.systemui.screenshot.DeleteScreenshotReceiver;
import com.android.systemui.screenshot.LensScreenshotReceiver;
import com.android.systemui.screenshot.SmartActionsReceiver;
import com.android.systemui.volume.VolumePanelDialogReceiver;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/**
 * BroadcastReceivers that are injectable should go here.
 */
@Module
public abstract class DefaultBroadcastReceiverBinder {
    /**
     *
     */
    @Binds
    @IntoMap
    @ClassKey(ActionProxyReceiver.class)
    public abstract BroadcastReceiver bindActionProxyReceiver(
            ActionProxyReceiver broadcastReceiver);

    /**
     *
     */
    @Binds
    @IntoMap
    @ClassKey(DeleteScreenshotReceiver.class)
    public abstract BroadcastReceiver bindDeleteScreenshotReceiver(
            DeleteScreenshotReceiver broadcastReceiver);

    /**
     *
     */
    @Binds
    @IntoMap
    @ClassKey(LensScreenshotReceiver.class)
    public abstract BroadcastReceiver bindLensScreenshotReceiver(
            LensScreenshotReceiver broadcastReceiver);

    /**
     *
     */
    @Binds
    @IntoMap
    @ClassKey(SmartActionsReceiver.class)
    public abstract BroadcastReceiver bindSmartActionsReceiver(
            SmartActionsReceiver broadcastReceiver);

    /**
     *
     */
    @Binds
    @IntoMap
    @ClassKey(MediaOutputDialogReceiver.class)
    public abstract BroadcastReceiver bindMediaOutputDialogReceiver(
            MediaOutputDialogReceiver broadcastReceiver);

    /**
     *
     */
    @Binds
    @IntoMap
    @ClassKey(VolumePanelDialogReceiver.class)
    public abstract BroadcastReceiver bindVolumePanelDialogReceiver(
            VolumePanelDialogReceiver broadcastReceiver);

    /**
     *
     */
    @Binds
    @IntoMap
    @ClassKey(PeopleSpaceWidgetPinnedReceiver.class)
    public abstract BroadcastReceiver bindPeopleSpaceWidgetPinnedReceiver(
            PeopleSpaceWidgetPinnedReceiver broadcastReceiver);

    /**
     *
     */
    @Binds
    @IntoMap
    @ClassKey(PeopleSpaceWidgetProvider.class)
    public abstract BroadcastReceiver bindPeopleSpaceWidgetProvider(
            PeopleSpaceWidgetProvider broadcastReceiver);

    /**
     *
     */
    @Binds
    @IntoMap
    @ClassKey(GuestResetOrExitSessionReceiver.class)
    public abstract BroadcastReceiver bindGuestResetOrExitSessionReceiver(
            GuestResetOrExitSessionReceiver broadcastReceiver);
}
