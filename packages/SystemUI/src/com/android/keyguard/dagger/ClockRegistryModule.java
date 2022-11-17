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

package com.android.keyguard.dagger;

import android.content.Context;
import android.os.Handler;
import android.os.UserHandle;

import com.android.systemui.R;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Application;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.flags.Flags;
import com.android.systemui.shared.clocks.ClockRegistry;
import com.android.systemui.shared.clocks.DefaultClockProvider;
import com.android.systemui.shared.plugins.PluginManager;

import dagger.Module;
import dagger.Provides;

/** Dagger Module for clocks. */
@Module
public abstract class ClockRegistryModule {
    /** Provide the ClockRegistry as a singleton so that it is not instantiated more than once. */
    @Provides
    @SysUISingleton
    public static ClockRegistry getClockRegistry(
            @Application Context context,
            PluginManager pluginManager,
            @Main Handler handler,
            DefaultClockProvider defaultClockProvider,
            FeatureFlags featureFlags) {
        return new ClockRegistry(
                context,
                pluginManager,
                handler,
                featureFlags.isEnabled(Flags.LOCKSCREEN_CUSTOM_CLOCKS),
                UserHandle.USER_ALL,
                defaultClockProvider,
                context.getString(R.string.lockscreen_clock_id_fallback));
    }
}
