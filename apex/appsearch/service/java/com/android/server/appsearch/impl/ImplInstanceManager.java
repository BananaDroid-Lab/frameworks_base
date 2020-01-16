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

package com.android.server.appsearch.impl;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.Context;
import android.util.SparseArray;

/**
 * Manages the lifecycle of instances of {@link AppSearchImpl}.
 *
 * <p>These instances are managed per unique device-user.
 */
public final class ImplInstanceManager {
    private static final SparseArray<AppSearchImpl> sInstances = new SparseArray<>();

    /**
     * Gets an instance of AppSearchImpl for the given user.
     *
     * <p>If no AppSearchImpl instance exists for this user, Icing will be initialized and one will
     * be created.
     *
     * @param context The Android context
     * @param userId The multi-user userId of the device user calling AppSearch
     * @return An initialized {@link AppSearchImpl} for this user
     */
    @NonNull
    public static AppSearchImpl getInstance(@NonNull Context context, @UserIdInt int userId) {
        AppSearchImpl instance = sInstances.get(userId);
        if (instance == null) {
            synchronized (ImplInstanceManager.class) {
                instance = sInstances.get(userId);
                if (instance == null) {
                    instance = new AppSearchImpl(context, userId);
                    sInstances.put(userId, instance);
                }
            }
        }
        return instance;
    }
}
