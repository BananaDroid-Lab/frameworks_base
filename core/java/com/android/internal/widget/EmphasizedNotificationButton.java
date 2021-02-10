/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.internal.widget;

import android.annotation.Nullable;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.BlendMode;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.view.RemotableViewMethod;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RemoteViews;

import com.android.internal.R;

/**
 * A button implementation for the emphasized notification style.
 *
 * @hide
 */
@RemoteViews.RemoteView
public class EmphasizedNotificationButton extends Button {
    private final RippleDrawable mRipple;
    private final int mStrokeWidth;
    private final int mStrokeColor;
    private boolean mPriority;

    public EmphasizedNotificationButton(Context context) {
        this(context, null);
    }

    public EmphasizedNotificationButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmphasizedNotificationButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public EmphasizedNotificationButton(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        DrawableWrapper background = (DrawableWrapper) getBackground().mutate();
        mRipple = (RippleDrawable) background.getDrawable();
        mStrokeWidth = getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.emphasized_button_stroke_width);
        mStrokeColor = getContext().getColor(com.android.internal.R.color.material_grey_300);
        mRipple.mutate();
    }

    @RemotableViewMethod
    public void setRippleColor(ColorStateList color) {
        mRipple.setColor(color);
        invalidate();
    }

    @RemotableViewMethod
    public void setButtonBackground(ColorStateList color) {
        GradientDrawable inner = (GradientDrawable) mRipple.getDrawable(0);
        inner.setColor(color);
        invalidate();
    }

    @RemotableViewMethod
    public void setHasStroke(boolean hasStroke) {
        GradientDrawable inner = (GradientDrawable) mRipple.getDrawable(0);
        inner.setStroke(hasStroke ? mStrokeWidth : 0, mStrokeColor);
        invalidate();
    }

    /**
     * Sets an image icon which will have its size constrained and will be set to the same color as
     * the text. Must be called after {@link #setTextColor(int)} for the latter to work.
     */
    @RemotableViewMethod(asyncImpl = "setImageIconAsync")
    public void setImageIcon(@Nullable Icon icon) {
        final Drawable drawable = icon == null ? null : icon.loadDrawable(mContext);
        setImageDrawable(drawable);
    }

    /**
     * @hide
     */
    @RemotableViewMethod
    public Runnable setImageIconAsync(@Nullable Icon icon) {
        final Drawable drawable = icon == null ? null : icon.loadDrawable(mContext);
        return () -> setImageDrawable(drawable);
    }

    private void setImageDrawable(Drawable drawable) {
        if (drawable != null) {
            drawable.mutate();
            drawable.setTintList(getTextColors());
            drawable.setTintBlendMode(BlendMode.SRC_IN);
            int iconSize = mContext.getResources().getDimensionPixelSize(
                    R.dimen.notification_actions_icon_drawable_size);
            drawable.setBounds(0, 0, iconSize, iconSize);
        }
        setCompoundDrawablesRelative(drawable, null, null, null);
    }

    /**
     * Changes the LayoutParams.width to WRAP_CONTENT, with the argument representing if this view
     * is a priority over its peers (which affects weight).
     */
    @RemotableViewMethod
    public void setWrapModePriority(boolean priority) {
        mPriority = priority;
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        if (layoutParams instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) layoutParams).weight = 0;
        }
        setLayoutParams(layoutParams);
    }

    /**
     * Sizing this button is a priority compared with its peers.
     */
    public boolean isPriority() {
        return mPriority;
    }
}
