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

package android.graphics;

import android.annotation.FloatRange;
import android.annotation.NonNull;

import libcore.util.NativeAllocationRegistry;

/**
 * Gainmap represents a mechanism for augmenting an SDR image to produce an HDR one with variable
 * display adjustment capability.
 *
 * It is a combination of a set of metadata describing how to apply the gainmap, as well as either
 * a 1 (such as {@link android.graphics.Bitmap.Config#ALPHA_8} or 3
 * (such as {@link android.graphics.Bitmap.Config#ARGB_8888} with the alpha channel ignored)
 * channel Bitmap that represents the gainmap data itself.
 *
 * When rendering to an {@link android.content.pm.ActivityInfo#COLOR_MODE_HDR} activity, the
 * hardware accelerated {@link Canvas} will automatically apply the gainmap when sufficient
 * HDR headroom is available.
 *
 * <h3>Gainmap Structure</h3>
 *
 * The logical whole of a gainmap'd image consists of a base Bitmap that represents the original
 * image as would be displayed without gainmap support in addition to a gainmap with a second
 * enhancement image. In the case of a JPEG, the base image would be the typical 8-bit SDR image
 * that the format is commonly associated with. The gainmap image is embedded alongside the base
 * image, often at a lower resolution (such as 1/4th), along with some metadata to describe
 * how to apply the gainmap. The gainmap image itself is then a greyscale image representing
 * the transformation to apply onto the base image to reconstruct an HDR rendition of it.
 *
 * As such these "gainmap images" consist of 3 parts - a base {@link Bitmap} with a
 * {@link Bitmap#getGainmap()} that returns an instance of this class which in turn contains
 * the enhancement layer represented as another Bitmap, accessible via {@link #getGainmapContents()}
 *
 * <h3>Applying a gainmap manually</h3>
 *
 * When doing custom rendering such as to an OpenGL ES or Vulkan context, the gainmap is not
 * automatically applied. In such situations, the following steps are appropriate to render the
 * gainmap in combination with the base image.
 *
 * Suppose our display has HDR to SDR ratio of H, and we wish to display an image with gainmap on
 * this display. Let B be the pixel value from the base image in a color space that has the
 * primaries of the base image and a linear transfer function. Let G be the pixel value from the
 * gainmap. Let D be the output pixel in the same color space as B. The value of D is computed
 * as follows:
 *
 * First, let W be a weight parameter determining how much the gainmap will be applied.
 *   W = clamp((log(H)               - log(displayRatioHdr)) /
 *             (log(displayRatioHdr) - log(displayRatioSdr), 0, 1)
 *
 * Next, let L be the gainmap value in log space. We compute this from the value G that was
 * sampled from the texture as follows:
 *   L = mix(log(gainmapRatioMin), log(gainmapRatioMax), pow(G, gainmapGamma))
 *
 * Finally, apply the gainmap to compute D, the displayed pixel. If the base image is SDR then
 * compute:
 *   D = (B + epsilonSdr) * exp(L * W) - epsilonHdr
 * If the base image is HDR then compute:
 *   D = (B + epsilonHdr) * exp(L * (W - 1)) - epsilonSdr
 *
 * In the above math, log() is a natural logarithm and exp() is natural exponentiation.
 */
public final class Gainmap {

    // Use a Holder to allow static initialization of Gainmap in the boot image.
    private static class NoImagePreloadHolder {
        public static final NativeAllocationRegistry sRegistry =
                NativeAllocationRegistry.createMalloced(
                        Gainmap.class.getClassLoader(), nGetFinalizer());
    }

    final long mNativePtr;
    private Bitmap mGainmapContents;

    // called from JNI
    private Gainmap(Bitmap gainmapContents, long nativeGainmap) {
        if (nativeGainmap == 0) {
            throw new RuntimeException("internal error: native gainmap is 0");
        }

        mGainmapContents = gainmapContents;
        mNativePtr = nativeGainmap;

        NoImagePreloadHolder.sRegistry.registerNativeAllocation(this, nativeGainmap);
    }

    /**
     * Creates a gainmap from a given Bitmap. The caller is responsible for setting the various
     * fields to the desired values. The defaults are as follows:
     * <ul>
     *     <li>Ratio min is 1f, 1f, 1f</li>
     *     <li>Ratio max is 2f, 2f, 2f</li>
     *     <li>Gamma is 1f, 1f, 1f</li>
     *     <li>Epsilon SDR is 0f, 0f, 0f</li>
     *     <li>Epsilon HDR is 0f, 0f, 0f</li>
     *     <li>Display ratio SDR is 1f</li>
     *     <li>Display ratio HDR is 2f</li>
     * </ul>
     * It is strongly recommended that at least the ratio max and display ratio HDR are adjusted
     * to better suit the given gainmap contents.
     */
    public Gainmap(@NonNull Bitmap gainmapContents) {
        this(gainmapContents, nCreateEmpty());
    }

    /**
     * @return Returns the image data of the gainmap represented as a Bitmap. This is represented
     * as a Bitmap for broad API compatibility, however certain aspects of the Bitmap are ignored
     * such as {@link Bitmap#getColorSpace()} or {@link Bitmap#getGainmap()} as they are not
     * relevant to the gainmap's enhancement layer.
     */
    @NonNull
    public Bitmap getGainmapContents() {
        return mGainmapContents;
    }

    /**
     * Sets the image data of the gainmap. This is the 1 or 3 channel enhancement layer to apply
     * to the base image. This is represented as a Bitmap for broad API compatibility, however
     * certain aspects of the Bitmap are ignored such as {@link Bitmap#getColorSpace()} or
     * {@link Bitmap#getGainmap()} as they are not relevant to the gainmap's enhancement layer.
     *
     * @param bitmap The non-null bitmap to set as the gainmap's contents
     */
    public void setGainmapContents(@NonNull Bitmap bitmap) {
        // TODO: Validate here or leave native-side?
        if (bitmap.isRecycled()) throw new IllegalArgumentException("Bitmap is recycled");
        nSetBitmap(mNativePtr, bitmap);
        mGainmapContents = bitmap;
    }

    /**
     * Sets the gainmap ratio min. For single-plane gainmaps, r, g, and b should be the same.
     */
    @NonNull
    public void setRatioMin(float r, float g, float b) {
        nSetRatioMin(mNativePtr, r, g, b);
    }

    /**
     * Gets the gainmap ratio max. For single-plane gainmaps, all 3 components should be the
     * same. The components are in r, g, b order.
     */
    @NonNull
    public float[] getRatioMin() {
        float[] ret = new float[3];
        nGetRatioMin(mNativePtr, ret);
        return ret;
    }

    /**
     * Sets the gainmap ratio max. For single-plane gainmaps, r, g, and b should be the same.
     */
    @NonNull
    public void setRatioMax(float r, float g, float b) {
        nSetRatioMax(mNativePtr, r, g, b);
    }

    /**
     * Gets the gainmap ratio max. For single-plane gainmaps, all 3 components should be the
     * same. The components are in r, g, b order.
     */
    @NonNull
    public float[] getRatioMax() {
        float[] ret = new float[3];
        nGetRatioMax(mNativePtr, ret);
        return ret;
    }

    /**
     * Sets the gainmap gamma. For single-plane gainmaps, r, g, and b should be the same.
     */
    @NonNull
    public void setGamma(float r, float g, float b) {
        nSetGamma(mNativePtr, r, g, b);
    }

    /**
     * Gets the gainmap gamma. For single-plane gainmaps, all 3 components should be the
     * same. The components are in r, g, b order.
     */
    @NonNull
    public float[] getGamma() {
        float[] ret = new float[3];
        nGetGamma(mNativePtr, ret);
        return ret;
    }

    /**
     * Sets the sdr epsilon which is used to avoid numerical instability.
     * For single-plane gainmaps, r, g, and b should be the same.
     */
    @NonNull
    public void setEpsilonSdr(float r, float g, float b) {
        nSetEpsilonSdr(mNativePtr, r, g, b);
    }

    /**
     * Gets the sdr epsilon. For single-plane gainmaps, all 3 components should be the
     * same. The components are in r, g, b order.
     */
    @NonNull
    public float[] getEpsilonSdr() {
        float[] ret = new float[3];
        nGetEpsilonSdr(mNativePtr, ret);
        return ret;
    }

    /**
     * Sets the hdr epsilon which is used to avoid numerical instability.
     * For single-plane gainmaps, r, g, and b should be the same.
     */
    @NonNull
    public void setEpsilonHdr(float r, float g, float b) {
        nSetEpsilonHdr(mNativePtr, r, g, b);
    }

    /**
     * Gets the hdr epsilon. For single-plane gainmaps, all 3 components should be the
     * same. The components are in r, g, b order.
     */
    @NonNull
    public float[] getEpsilonHdr() {
        float[] ret = new float[3];
        nGetEpsilonHdr(mNativePtr, ret);
        return ret;
    }

    /**
     * Sets the hdr/sdr ratio at which point the gainmap is fully applied.
     * @param max The hdr/sdr ratio at which the gainmap is fully applied. Must be >= 1.0f
     */
    @NonNull
    public void setDisplayRatioForFullHdr(float max) {
        if (!Float.isFinite(max) || max < 1f) {
            throw new IllegalArgumentException(
                    "setDisplayRatioForFullHdr must be >= 1.0f, got = " + max);
        }
        nSetDisplayRatioHdr(mNativePtr, max);
    }

    /**
     * Gets the hdr/sdr ratio at which point the gainmap is fully applied.
     */
    @NonNull
    public float getDisplayRatioForFullHdr() {
        return nGetDisplayRatioHdr(mNativePtr);
    }

    /**
     * Sets the hdr/sdr ratio below which only the SDR image is displayed.
     * @param min The minimum hdr/sdr ratio at which to begin applying the gainmap. Must be >= 1.0f
     */
    @NonNull
    public void setMinDisplayRatioForHdrTransition(@FloatRange(from = 1.0f) float min) {
        if (!Float.isFinite(min) || min < 1f) {
            throw new IllegalArgumentException(
                    "setMinDisplayRatioForHdrTransition must be >= 1.0f, got = " + min);
        }
        nSetDisplayRatioSdr(mNativePtr, min);
    }

    /**
     * Gets the hdr/sdr ratio below which only the SDR image is displayed.
     */
    @NonNull
    public float getMinDisplayRatioForHdrTransition() {
        return nGetDisplayRatioSdr(mNativePtr);
    }

    private static native long nGetFinalizer();
    private static native long nCreateEmpty();

    private static native void nSetBitmap(long ptr, Bitmap bitmap);

    private static native void nSetRatioMin(long ptr, float r, float g, float b);
    private static native void nGetRatioMin(long ptr, float[] components);

    private static native void nSetRatioMax(long ptr, float r, float g, float b);
    private static native void nGetRatioMax(long ptr, float[] components);

    private static native void nSetGamma(long ptr, float r, float g, float b);
    private static native void nGetGamma(long ptr, float[] components);

    private static native void nSetEpsilonSdr(long ptr, float r, float g, float b);
    private static native void nGetEpsilonSdr(long ptr, float[] components);

    private static native void nSetEpsilonHdr(long ptr, float r, float g, float b);
    private static native void nGetEpsilonHdr(long ptr, float[] components);

    private static native void nSetDisplayRatioHdr(long ptr, float max);
    private static native float nGetDisplayRatioHdr(long ptr);

    private static native void nSetDisplayRatioSdr(long ptr, float min);
    private static native float nGetDisplayRatioSdr(long ptr);
}
