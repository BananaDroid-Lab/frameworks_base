/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.constraintlayout.widget.Barrier
import com.android.systemui.R
import com.android.systemui.util.animation.TransitionLayout

private const val TAG = "MediaViewHolder"

/**
 * Holder class for media player view
 */
class MediaViewHolder constructor(itemView: View) {
    val player = itemView as TransitionLayout

    // Player information
    val albumView = itemView.requireViewById<ImageView>(R.id.album_art)
    val appIcon = itemView.requireViewById<ImageView>(R.id.icon)
    val titleText = itemView.requireViewById<TextView>(R.id.header_title)
    val artistText = itemView.requireViewById<TextView>(R.id.header_artist)

    // Output switcher
    val seamless = itemView.requireViewById<ViewGroup>(R.id.media_seamless)
    val seamlessIcon = itemView.requireViewById<ImageView>(R.id.media_seamless_image)
    val seamlessText = itemView.requireViewById<TextView>(R.id.media_seamless_text)
    val seamlessButton = itemView.requireViewById<View>(R.id.media_seamless_button)

    // Seekbar views
    val seekBar = itemView.requireViewById<SeekBar>(R.id.media_progress_bar)
    open val elapsedTimeView: TextView? = null
    open val totalTimeView: TextView? = null

    // Settings screen
    val longPressText = itemView.requireViewById<TextView>(R.id.remove_text)
    val cancel = itemView.requireViewById<View>(R.id.cancel)
    val cancelText = itemView.requireViewById<TextView>(R.id.cancel_text)
    val dismiss = itemView.requireViewById<ViewGroup>(R.id.dismiss)
    val dismissText = itemView.requireViewById<TextView>(R.id.dismiss_text)
    val settings = itemView.requireViewById<ImageButton>(R.id.settings)

    // Action Buttons
    val actionPlayPause = itemView.requireViewById<ImageButton>(R.id.actionPlayPause)
    val actionNext = itemView.requireViewById<ImageButton>(R.id.actionNext)
    val actionPrev = itemView.requireViewById<ImageButton>(R.id.actionPrev)
    val action0 = itemView.requireViewById<ImageButton>(R.id.action0)
    val action1 = itemView.requireViewById<ImageButton>(R.id.action1)
    val action2 = itemView.requireViewById<ImageButton>(R.id.action2)
    val action3 = itemView.requireViewById<ImageButton>(R.id.action3)
    val action4 = itemView.requireViewById<ImageButton>(R.id.action4)

    val actionsTopBarrier = itemView.requireViewById<Barrier>(R.id.media_action_barrier_top)

    init {
        (player.background as IlluminationDrawable).let {
            it.registerLightSource(seamless)
            it.registerLightSource(cancel)
            it.registerLightSource(dismiss)
            it.registerLightSource(settings)
            it.registerLightSource(actionPlayPause)
            it.registerLightSource(actionNext)
            it.registerLightSource(actionPrev)
            it.registerLightSource(action0)
            it.registerLightSource(action1)
            it.registerLightSource(action2)
            it.registerLightSource(action3)
            it.registerLightSource(action4)
        }
    }

    fun getAction(id: Int): ImageButton {
        return when (id) {
            R.id.actionPlayPause -> actionPlayPause
            R.id.actionNext -> actionNext
            R.id.actionPrev -> actionPrev
            R.id.action0 -> action0
            R.id.action1 -> action1
            R.id.action2 -> action2
            R.id.action3 -> action3
            R.id.action4 -> action4
            else -> {
                throw IllegalArgumentException()
            }
        }
    }

    fun getTransparentActionButtons(): List<ImageButton> {
        return listOf(
                actionNext,
                actionPrev,
                action0,
                action1,
                action2,
                action3,
                action4
        )
    }

    fun marquee(start: Boolean, delay: Long) {
        val longPressTextHandler = longPressText.getHandler()
        if (longPressTextHandler == null) {
            Log.d(TAG, "marquee while longPressText.getHandler() is null", Exception())
            return
        }
        longPressTextHandler.postDelayed({ longPressText.setSelected(start) }, delay)
    }

    companion object {
        /**
         * Creates a MediaViewHolder.
         *
         * @param inflater LayoutInflater to use to inflate the layout.
         * @param parent Parent of inflated view.
         */
        @JvmStatic fun create(
            inflater: LayoutInflater,
            parent: ViewGroup
        ): MediaViewHolder {
            val mediaView = inflater.inflate(R.layout.media_session_view, parent, false)
            mediaView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            // Because this media view (a TransitionLayout) is used to measure and layout the views
            // in various states before being attached to its parent, we can't depend on the default
            // LAYOUT_DIRECTION_INHERIT to correctly resolve the ltr direction.
            mediaView.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            return MediaViewHolder(mediaView).apply {
                // Media playback is in the direction of tape, not time, so it stays LTR
                seekBar.layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
        }

        val controlsIds = setOf(
                R.id.icon,
                R.id.app_name,
                R.id.header_title,
                R.id.header_artist,
                R.id.media_seamless,
                R.id.media_progress_bar,
                R.id.actionPlayPause,
                R.id.actionNext,
                R.id.actionPrev,
                R.id.action0,
                R.id.action1,
                R.id.action2,
                R.id.action3,
                R.id.action4,
                R.id.icon
        )
        val gutsIds = setOf(
                R.id.remove_text,
                R.id.cancel,
                R.id.dismiss,
                R.id.settings
        )
    }
}