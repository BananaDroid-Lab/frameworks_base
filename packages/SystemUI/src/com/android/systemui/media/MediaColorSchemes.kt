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

package com.android.systemui.media

import com.android.systemui.monet.ColorScheme

/** Returns the surface color for media controls based on the scheme. */
internal fun surfaceFromScheme(scheme: ColorScheme) = scheme.accent2[9] // A2-800

/** Returns the primary accent color for media controls based on the scheme. */
internal fun accentPrimaryFromScheme(scheme: ColorScheme) = scheme.accent1[2] // A1-100

/** Returns the primary text color for media controls based on the scheme. */
internal fun textPrimaryFromScheme(scheme: ColorScheme) = scheme.neutral1[1] // N1-50

/** Returns the inverse of the primary text color for media controls based on the scheme. */
internal fun textPrimaryInverseFromScheme(scheme: ColorScheme) = scheme.neutral1[10] // N1-900

/** Returns the secondary text color for media controls based on the scheme. */
internal fun textSecondaryFromScheme(scheme: ColorScheme) = scheme.neutral2[3] // N2-200

/** Returns the tertiary text color for media controls based on the scheme. */
internal fun textTertiaryFromScheme(scheme: ColorScheme) = scheme.neutral2[5] // N2-400
