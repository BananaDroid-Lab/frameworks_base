/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.stack.data.repository

import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Repository for information about the current notification list. */
@SysUISingleton
class NotificationListRepository @Inject constructor() {
    private val _hasFilteredOutSeenNotifications = MutableStateFlow(false)
    val hasFilteredOutSeenNotifications: StateFlow<Boolean> =
        _hasFilteredOutSeenNotifications.asStateFlow()

    fun setHasFilteredOutSeenNotifications(value: Boolean) {
        _hasFilteredOutSeenNotifications.value = value
    }
}
