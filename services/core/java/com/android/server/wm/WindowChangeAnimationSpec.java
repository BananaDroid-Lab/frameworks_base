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
 * limitations under the License.
 */

package com.android.server.wm;

import static com.android.server.wm.AnimationAdapter.STATUS_BAR_TRANSITION_DURATION;
import static com.android.server.wm.AnimationSpecProto.WINDOW;
import static com.android.server.wm.WindowAnimationSpecProto.ANIMATION;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.proto.ProtoOutputStream;
import android.view.DisplayInfo;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ClipRectAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.Transformation;
import android.view.animation.TranslateAnimation;

import com.android.server.wm.LocalAnimationAdapter.AnimationSpec;

import java.io.PrintWriter;

/**
 * Animation spec for changing window animations.
 */
public class WindowChangeAnimationSpec implements AnimationSpec {

    private final ThreadLocal<TmpValues> mThreadLocalTmps = ThreadLocal.withInitial(TmpValues::new);
    private final boolean mIsAppAnimation;
    private final Rect mStartBounds;
    private final Rect mEndBounds;
    private final Rect mTmpRect = new Rect();

    private Animation mAnimation;
    private final boolean mIsThumbnail;

    static final int ANIMATION_DURATION = AppTransition.DEFAULT_APP_TRANSITION_DURATION;

    public WindowChangeAnimationSpec(Rect startBounds, Rect endBounds, DisplayInfo displayInfo,
            float durationScale, boolean isAppAnimation, boolean isThumbnail) {
        mStartBounds = new Rect(startBounds);
        mEndBounds = new Rect(endBounds);
        mIsAppAnimation = isAppAnimation;
        mIsThumbnail = isThumbnail;
        createBoundsInterpolator((int) (ANIMATION_DURATION * durationScale), displayInfo);
    }

    @Override
    public boolean getShowWallpaper() {
        return false;
    }

    @Override
    public long getDuration() {
        return mAnimation.getDuration();
    }

    /**
     * This animator behaves slightly differently depending on whether the window is growing
     * or shrinking:
     * If growing, it will do a clip-reveal after quicker fade-out/scale of the smaller (old)
     * snapshot.
     * If shrinking, it will shrink the old snapshot into place followed by a quicker
     * fade-out of the bigger (old) snapshot while leaving the (new) window in the same place.
     * @param duration
     * @param displayInfo
     */
    private void createBoundsInterpolator(long duration, DisplayInfo displayInfo) {
        boolean growing = mEndBounds.width() - mStartBounds.width()
                + mEndBounds.height() - mStartBounds.height() >= 0;
        float scalePart = 0.7f;
        long scalePeriod = (long) (duration * scalePart);
        float startScaleX = scalePart * ((float) mStartBounds.width()) / mEndBounds.width()
                + (1.f - scalePart);
        float startScaleY = scalePart * ((float) mStartBounds.height()) / mEndBounds.height()
                + (1.f - scalePart);
        float myScaleX = 1f / (((float) mStartBounds.width()) / mEndBounds.width());
        float myScaleY = 1f / (((float) mStartBounds.height()) / mEndBounds.height());
        final AnimationSet animSet = new AnimationSet(true);
        if (mIsThumbnail) {
            final Animation alphaAnim = new AlphaAnimation(1.f, 0.f);
            if (!growing) {
                alphaAnim.setStartOffset(scalePeriod);
                alphaAnim.setDuration(duration - scalePeriod);
            } else {
                alphaAnim.setDuration(scalePeriod);
            }
            animSet.addAnimation(alphaAnim);

            final Animation scaleAnim;
            if (growing) {
                float endScaleX = 1.f / startScaleX;
                float endScaleY = 1.f / startScaleY;
                scaleAnim = new ScaleAnimation(endScaleX, endScaleX, endScaleY, endScaleY);
                scaleAnim.setDuration(duration);
            } else {
                scaleAnim = new ScaleAnimation(1, myScaleX, 1, myScaleY);
                scaleAnim.setDuration(scalePeriod);
            }
            animSet.addAnimation(scaleAnim);

            if (!growing) {
                final Animation translateAnim = new TranslateAnimation(mStartBounds.left,
                        mEndBounds.left, mStartBounds.top, mEndBounds.top);
                translateAnim.setDuration(scalePeriod);
                animSet.addAnimation(translateAnim);

                Rect startClip = new Rect(mStartBounds);
                Rect endClip = new Rect(mEndBounds);
                startClip.offsetTo(0, 0);
                endClip.offsetTo(0, 0);
                final Animation clipAnim = new ClipRectAnimation(startClip, endClip);
                clipAnim.setDuration(duration);
                animSet.addAnimation(clipAnim);
            }
        } else {
            if (growing) {
                final Animation scaleAnim = new ScaleAnimation(startScaleX, 1, startScaleY, 1);
                scaleAnim.setDuration(scalePeriod);
                animSet.addAnimation(scaleAnim);

                final Animation translateAnim = new TranslateAnimation(mStartBounds.left,
                        mEndBounds.left, mStartBounds.top, mEndBounds.top);
                translateAnim.setDuration(duration);
                animSet.addAnimation(translateAnim);

                Rect startClip = new Rect(mStartBounds);
                Rect endClip = new Rect(mEndBounds);
                startClip.offsetTo(0, 0);
                endClip.offsetTo(0, 0);
                final Animation clipAnim = new ClipRectAnimation(startClip, endClip);
                clipAnim.setDuration(duration);
                animSet.addAnimation(clipAnim);
            } else {
                // no visual effect, but keeps animator busy. it otherwise just jumps to last frame
                final Animation alphaAnim = new AlphaAnimation(1.f, 1.f);
                alphaAnim.setDuration(duration);
                animSet.addAnimation(alphaAnim);
            }

        }
        mAnimation = animSet;
        mAnimation.initialize(mStartBounds.width(), mStartBounds.height(),
                mEndBounds.width(), mEndBounds.height());
    }

    @Override
    public void apply(Transaction t, SurfaceControl leash, long currentPlayTime) {
        final TmpValues tmp = mThreadLocalTmps.get();
        mAnimation.getTransformation(currentPlayTime, tmp.mTransformation);
        if (mIsThumbnail) {
            t.setAlpha(leash, tmp.mTransformation.getAlpha());
        }
        final Matrix matrix = tmp.mTransformation.getMatrix();
        t.setMatrix(leash, matrix, tmp.mFloats);
        final Rect clipRect = tmp.mTransformation.getClipRect();
        // The following applies an inverse scale to the clip-rect so that it crops "after" the
        // scale instead of before.
        tmp.mVecs[1] = tmp.mVecs[2] = 0;
        tmp.mVecs[0] = tmp.mVecs[3] = 1;
        matrix.mapVectors(tmp.mVecs);
        tmp.mVecs[0] = 1.f / tmp.mVecs[0];
        tmp.mVecs[3] = 1.f / tmp.mVecs[3];
        mTmpRect.left = (int) (clipRect.left * tmp.mVecs[0] + 0.5f);
        mTmpRect.right = (int) (clipRect.right * tmp.mVecs[0] + 0.5f);
        mTmpRect.top = (int) (clipRect.top * tmp.mVecs[3] + 0.5f);
        mTmpRect.bottom = (int) (clipRect.bottom * tmp.mVecs[3] + 0.5f);
        t.setWindowCrop(leash, mTmpRect);
    }

    @Override
    public long calculateStatusBarTransitionStartTime() {
        long uptime = SystemClock.uptimeMillis();
        return Math.max(uptime, uptime + ((long) (((float) mAnimation.getDuration()) * 0.99f))
                - STATUS_BAR_TRANSITION_DURATION);
    }

    @Override
    public boolean canSkipFirstFrame() {
        return false;
    }

    @Override
    public boolean needsEarlyWakeup() {
        return mIsAppAnimation;
    }

    @Override
    public void dump(PrintWriter pw, String prefix) {
        pw.print(prefix); pw.println(mAnimation.getDuration());
    }

    @Override
    public void dumpDebugInner(ProtoOutputStream proto) {
        final long token = proto.start(WINDOW);
        proto.write(ANIMATION, mAnimation.toString());
        proto.end(token);
    }

    private static class TmpValues {
        final Transformation mTransformation = new Transformation();
        final float[] mFloats = new float[9];
        final float[] mVecs = new float[4];
    }
}
