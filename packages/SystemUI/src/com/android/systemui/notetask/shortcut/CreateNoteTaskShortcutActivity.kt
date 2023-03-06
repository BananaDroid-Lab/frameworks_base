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

package com.android.systemui.notetask.shortcut

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.android.systemui.R
import javax.inject.Inject

/**
 * Activity responsible for create a shortcut for notes action. If the shortcut is enabled, a new
 * shortcut will appear in the widget picker. If the shortcut is selected, the Activity here will be
 * launched, creating a new shortcut for [CreateNoteTaskShortcutActivity], and will finish.
 *
 * @see <a
 *   href="https://developer.android.com/develop/ui/views/launch/shortcuts/creating-shortcuts#custom-pinned">Creating
 *   a custom shortcut activity</a>
 */
class CreateNoteTaskShortcutActivity
@Inject
constructor(
    private val roleManager: RoleManager,
) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent =
            createShortcutIntent(
                id = SHORTCUT_ID,
                shortLabel = getString(R.string.note_task_button_label),
                intent = LaunchNoteTaskActivity.newIntent(context = this),
                iconResource = R.drawable.ic_note_task_shortcut_widget,
            )
        setResult(Activity.RESULT_OK, intent)

        finish()
    }

    private fun createShortcutIntent(
        id: String,
        shortLabel: String,
        intent: Intent,
        @DrawableRes iconResource: Int,
    ): Intent {
        val extras = PersistableBundle()

        roleManager.getRoleHoldersAsUser(RoleManager.ROLE_NOTES, user).firstOrNull()?.let { name ->
            extras.putString(EXTRA_SHORTCUT_BADGE_OVERRIDE_PACKAGE, name)
        }

        val shortcutInfo =
            ShortcutInfoCompat.Builder(this, id)
                .setIntent(intent)
                .setShortLabel(shortLabel)
                .setLongLived(true)
                .setIcon(IconCompat.createWithResource(this, iconResource))
                .setExtras(extras)
                .build()

        return ShortcutManagerCompat.createShortcutResultIntent(
            this,
            shortcutInfo,
        )
    }

    private companion object {
        private const val SHORTCUT_ID = "note-task-shortcut-id"

        /**
         * Shortcut extra which can point to a package name and can be used to indicate an alternate
         * badge info. Launcher only reads this if the shortcut comes from a system app.
         *
         * Duplicated from [com.android.launcher3.icons.IconCache].
         *
         * @see com.android.launcher3.icons.IconCache.EXTRA_SHORTCUT_BADGE_OVERRIDE_PACKAGE
         */
        private const val EXTRA_SHORTCUT_BADGE_OVERRIDE_PACKAGE =
            "extra_shortcut_badge_override_package"
    }
}
