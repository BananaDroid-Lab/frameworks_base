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
 *
 */

package com.android.systemui.keyguard.ui.viewmodel

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.domain.interactor.BiometricMessage
import com.android.systemui.keyguard.domain.interactor.OccludingAppDeviceEntryInteractor
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

/** Shows authentication messages over occcluding apps over the lockscreen. */
@ExperimentalCoroutinesApi
@SysUISingleton
class OccludingAppDeviceEntryMessageViewModel
@Inject
constructor(
    interactor: OccludingAppDeviceEntryInteractor,
) {
    val message: Flow<BiometricMessage?> = interactor.message
}
