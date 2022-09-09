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
 *
 */

package com.android.systemui.power.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.power.data.repository.PowerRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Hosts business logic for interacting with the power system. */
@SysUISingleton
class PowerInteractor
@Inject
constructor(
    repository: PowerRepository,
) {
    /** Whether the screen is on or off. */
    val isInteractive: Flow<Boolean> = repository.isInteractive
}
