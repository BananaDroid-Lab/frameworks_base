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

package com.android.systemui.statusbar.notification.stack.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.notification.stack.data.repository.NotificationListRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Interactor for business logic associated with the notification stack. */
@SysUISingleton
class NotificationListInteractor
@Inject
constructor(
    private val notificationListRepository: NotificationListRepository,
) {
    /** Are any already-seen notifications currently filtered out of the shade? */
    val hasFilteredOutSeenNotifications: StateFlow<Boolean>
        get() = notificationListRepository.hasFilteredOutSeenNotifications

    fun setHasFilteredOutSeenNotifications(value: Boolean) {
        notificationListRepository.setHasFilteredOutSeenNotifications(value)
    }
}
