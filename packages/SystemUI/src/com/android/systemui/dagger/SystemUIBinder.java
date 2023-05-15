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

import com.android.systemui.CoreStartable;
import com.android.systemui.keyguard.dagger.KeyguardModule;
import com.android.systemui.recents.RecentsModule;
import com.android.systemui.statusbar.dagger.CentralSurfacesModule;

import com.statix.android.systemui.StatixServices;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

import dagger.Module;

/**
 * SystemUI objects that are injectable should go here.
 */
@Module(includes = {
        RecentsModule.class,
        CentralSurfacesModule.class,
        KeyguardModule.class,
})
public abstract class SystemUIBinder {

    /**
     * Inject into StatixServices.
     */
    @Binds
    @IntoMap
    @ClassKey(StatixServices.class)
    public abstract CoreStartable bindStatixServices(StatixServices sysui);
}
