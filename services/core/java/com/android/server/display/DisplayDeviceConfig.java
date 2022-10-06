/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.display;

import android.annotation.NonNull;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.hardware.display.DisplayManagerInternal;
import android.hardware.display.DisplayManagerInternal.RefreshRateLimitation;
import android.os.Environment;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.MathUtils;
import android.util.Pair;
import android.util.Slog;
import android.util.Spline;
import android.view.DisplayAddress;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.display.BrightnessSynchronizer;
import com.android.server.display.config.AutoBrightness;
import com.android.server.display.config.BrightnessThresholds;
import com.android.server.display.config.BrightnessThrottlingMap;
import com.android.server.display.config.BrightnessThrottlingPoint;
import com.android.server.display.config.Density;
import com.android.server.display.config.DisplayConfiguration;
import com.android.server.display.config.DisplayQuirks;
import com.android.server.display.config.HbmTiming;
import com.android.server.display.config.HighBrightnessMode;
import com.android.server.display.config.NitsMap;
import com.android.server.display.config.Point;
import com.android.server.display.config.RefreshRateRange;
import com.android.server.display.config.SdrHdrRatioMap;
import com.android.server.display.config.SdrHdrRatioPoint;
import com.android.server.display.config.SensorDetails;
import com.android.server.display.config.ThermalStatus;
import com.android.server.display.config.ThermalThrottling;
import com.android.server.display.config.ThresholdPoint;
import com.android.server.display.config.XmlParser;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.xml.datatype.DatatypeConfigurationException;

/**
 * Reads and stores display-specific configurations. File format:
 * <pre>
 *  {@code
 *    <displayConfiguration>
 *      <densityMapping>
 *        <density>
 *          <height>480</height>
 *          <width>720</width>
 *          <density>120</density>
 *        </density>
 *        <density>
 *          <height>720</height>
 *          <width>1280</width>
 *          <density>213</density>
 *        </density>
 *        <density>
 *          <height>1080</height>
 *          <width>1920</width>
 *          <density>320</density>
 *        </density>
 *        <density>
 *          <height>2160</height>
 *          <width>3840</width>
 *          <density>640</density>
 *        </density>
 *      </densityMapping>
 *
 *      <screenBrightnessMap>
 *        <point>
 *          <value>0.0</value>
 *          <nits>2.0</nits>
 *        </point>
 *        <point>
 *          <value>0.62</value>
 *          <nits>500.0</nits>
 *        </point>
 *        <point>
 *          <value>1.0</value>
 *          <nits>800.0</nits>
 *        </point>
 *      </screenBrightnessMap>
 *
 *      <screenBrightnessDefault>0.65</screenBrightnessDefault>
 *
 *      <thermalThrottling>
 *        <brightnessThrottlingMap>
 *          <brightnessThrottlingPoint>
 *            <thermalStatus>severe</thermalStatus>
 *            <brightness>0.1</brightness>
 *          </brightnessThrottlingPoint>
 *          <brightnessThrottlingPoint>
 *            <thermalStatus>critical</thermalStatus>
 *            <brightness>0.01</brightness>
 *          </brightnessThrottlingPoint>
 *        </brightnessThrottlingMap>
 *      </thermalThrottling>
 *
 *      <highBrightnessMode enabled="true">
 *        <transitionPoint>0.62</transitionPoint>
 *        <minimumLux>10000</minimumLux>
 *        <timing>
 *          <timeWindowSecs>1800</timeWindowSecs> // Window in which we restrict HBM.
 *          <timeMaxSecs>300</timeMaxSecs>        // Maximum time of HBM allowed in that window.
 *          <timeMinSecs>60</timeMinSecs>         // Minimum time remaining required to switch
 *        </timing>                               //   HBM on for.
 *        <refreshRate>
 *          <minimum>120</minimum>
 *          <maximum>120</maximum>
 *        </refreshRate>
 *        <thermalStatusLimit>light</thermalStatusLimit>
 *        <allowInLowPowerMode>false</allowInLowPowerMode>
 *      </highBrightnessMode>
 *
 *      <quirks>
 *       <quirk>canSetBrightnessViaHwc</quirk>
 *      </quirks>
 *
 *      <autoBrightness>
 *          <brighteningLightDebounceMillis>
 *              2000
 *          </brighteningLightDebounceMillis>
 *          <darkeningLightDebounceMillis>
 *              1000
 *          </darkeningLightDebounceMillis>
 *          <displayBrightnessMapping>
 *              <displayBrightnessPoint>
 *                  <lux>50</lux>
 *                  <nits>45.32</nits>
 *              </displayBrightnessPoint>
 *              <displayBrightnessPoint>
 *                  <lux>80</lux>
 *                  <nits>75.43</nits>
 *              </displayBrightnessPoint>
 *          </displayBrightnessMapping>
 *      </autoBrightness>
 *
 *      <screenBrightnessRampFastDecrease>0.01</screenBrightnessRampFastDecrease>
 *      <screenBrightnessRampFastIncrease>0.02</screenBrightnessRampFastIncrease>
 *      <screenBrightnessRampSlowDecrease>0.03</screenBrightnessRampSlowDecrease>
 *      <screenBrightnessRampSlowIncrease>0.04</screenBrightnessRampSlowIncrease>
 *
 *      <screenBrightnessRampIncreaseMaxMillis>2000</screenBrightnessRampIncreaseMaxMillis>
 *      <screenBrightnessRampDecreaseMaxMillis>3000</screenBrightnessRampDecreaseMaxMillis>
 *
 *      <lightSensor>
 *        <type>android.sensor.light</type>
 *        <name>1234 Ambient Light Sensor</name>
 *      </lightSensor>
 *      <proxSensor>
 *        <type>android.sensor.proximity</type>
 *        <name>1234 Proximity Sensor</name>
 *      </proxSensor>
 *
 *      <ambientLightHorizonLong>10001</ambientLightHorizonLong>
 *      <ambientLightHorizonShort>2001</ambientLightHorizonShort>
 *
 *     <ambientBrightnessChangeThresholds>  // Thresholds for lux changes
 *         <brighteningThresholds>
 *             // Minimum change needed in ambient brightness to brighten screen.
 *             <minimum>10</minimum>
 *             // Percentage increase of lux needed to increase the screen brightness at a lux range
 *             // above the specified threshold.
 *             <brightnessThresholdPoints>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0</threshold><percentage>13</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>100</threshold><percentage>14</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>200</threshold><percentage>15</percentage>
 *                 </brightnessThresholdPoint>
 *             </brightnessThresholdPoints>
 *         </brighteningThresholds>
 *         <darkeningThresholds>
 *             // Minimum change needed in ambient brightness to darken screen.
 *             <minimum>30</minimum>
 *             // Percentage increase of lux needed to decrease the screen brightness at a lux range
 *             // above the specified threshold.
 *             <brightnessThresholdPoints>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0</threshold><percentage>15</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>300</threshold><percentage>16</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>400</threshold><percentage>17</percentage>
 *                 </brightnessThresholdPoint>
 *             </brightnessThresholdPoints>
 *         </darkeningThresholds>
 *     </ambientBrightnessChangeThresholds>
 *     <displayBrightnessChangeThresholds>   // Thresholds for screen brightness changes
 *         <brighteningThresholds>
 *             // Minimum change needed in screen brightness to brighten screen.
 *             <minimum>0.1</minimum>
 *             // Percentage increase of screen brightness needed to increase the screen brightness
 *             // at a lux range above the specified threshold.
 *             <brightnessThresholdPoints>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0</threshold>
 *                     <percentage>9</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0.10</threshold>
 *                     <percentage>10</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0.20</threshold>
 *                     <percentage>11</percentage>
 *                 </brightnessThresholdPoint>
 *             </brightnessThresholdPoints>
 *         </brighteningThresholds>
 *         <darkeningThresholds>
 *             // Minimum change needed in screen brightness to darken screen.
 *             <minimum>0.3</minimum>
 *             // Percentage increase of screen brightness needed to decrease the screen brightness
 *             // at a lux range above the specified threshold.
 *             <brightnessThresholdPoints>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0</threshold><percentage>11</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0.11</threshold><percentage>12</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0.21</threshold><percentage>13</percentage>
 *                 </brightnessThresholdPoint>
 *             </brightnessThresholdPoints>
 *         </darkeningThresholds>
 *     </displayBrightnessChangeThresholds>
 *     <ambientBrightnessChangeThresholdsIdle>   // Thresholds for lux changes in idle mode
 *         <brighteningThresholds>
 *             // Minimum change needed in ambient brightness to brighten screen in idle mode
 *             <minimum>20</minimum>
 *             // Percentage increase of lux needed to increase the screen brightness at a lux range
 *             // above the specified threshold whilst in idle mode.
 *             <brightnessThresholdPoints>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0</threshold><percentage>21</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>500</threshold><percentage>22</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>600</threshold><percentage>23</percentage>
 *                 </brightnessThresholdPoint>
 *             </brightnessThresholdPoints>
 *         </brighteningThresholds>
 *         <darkeningThresholds>
 *             // Minimum change needed in ambient brightness to darken screen in idle mode
 *             <minimum>40</minimum>
 *             // Percentage increase of lux needed to decrease the screen brightness at a lux range
 *             // above the specified threshold whilst in idle mode.
 *             <brightnessThresholdPoints>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0</threshold><percentage>23</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>700</threshold><percentage>24</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>800</threshold><percentage>25</percentage>
 *                 </brightnessThresholdPoint>
 *             </brightnessThresholdPoints>
 *         </darkeningThresholds>
 *     </ambientBrightnessChangeThresholdsIdle>
 *     <displayBrightnessChangeThresholdsIdle>    // Thresholds for idle screen brightness changes
 *         <brighteningThresholds>
 *             // Minimum change needed in screen brightness to brighten screen in idle mode
 *             <minimum>0.2</minimum>
 *             // Percentage increase of screen brightness needed to increase the screen brightness
 *             // at a lux range above the specified threshold whilst in idle mode
 *             <brightnessThresholdPoints>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0</threshold><percentage>17</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0.12</threshold><percentage>18</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0.22</threshold><percentage>19</percentage>
 *                 </brightnessThresholdPoint>
 *             </brightnessThresholdPoints>
 *         </brighteningThresholds>
 *         <darkeningThresholds>
 *             // Minimum change needed in screen brightness to darken screen in idle mode
 *             <minimum>0.4</minimum>
 *             // Percentage increase of screen brightness needed to decrease the screen brightness
 *             // at a lux range above the specified threshold whilst in idle mode
 *             <brightnessThresholdPoints>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0</threshold><percentage>19</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0.13</threshold><percentage>20</percentage>
 *                 </brightnessThresholdPoint>
 *                 <brightnessThresholdPoint>
 *                     <threshold>0.23</threshold><percentage>21</percentage>
 *                 </brightnessThresholdPoint>
 *             </brightnessThresholdPoints>
 *         </darkeningThresholds>
 *     </displayBrightnessChangeThresholdsIdle>
 *    </displayConfiguration>
 *  }
 *  </pre>
 */
public class DisplayDeviceConfig {
    private static final String TAG = "DisplayDeviceConfig";
    private static final boolean DEBUG = false;

    public static final float HIGH_BRIGHTNESS_MODE_UNSUPPORTED = Float.NaN;

    public static final String QUIRK_CAN_SET_BRIGHTNESS_VIA_HWC = "canSetBrightnessViaHwc";

    private static final float BRIGHTNESS_DEFAULT = 0.5f;
    private static final String ETC_DIR = "etc";
    private static final String DISPLAY_CONFIG_DIR = "displayconfig";
    private static final String CONFIG_FILE_FORMAT = "display_%s.xml";
    private static final String DEFAULT_CONFIG_FILE = "default.xml";
    private static final String DEFAULT_CONFIG_FILE_WITH_UIMODE_FORMAT = "default_%s.xml";
    private static final String PORT_SUFFIX_FORMAT = "port_%d";
    private static final String STABLE_ID_SUFFIX_FORMAT = "id_%d";
    private static final String NO_SUFFIX_FORMAT = "%d";
    private static final long STABLE_FLAG = 1L << 62;

    private static final float[] DEFAULT_AMBIENT_THRESHOLD_LEVELS = new float[]{0f};
    private static final float[] DEFAULT_AMBIENT_BRIGHTENING_THRESHOLDS = new float[]{100f};
    private static final float[] DEFAULT_AMBIENT_DARKENING_THRESHOLDS = new float[]{200f};
    private static final float[] DEFAULT_SCREEN_THRESHOLD_LEVELS = new float[]{0f};
    private static final float[] DEFAULT_SCREEN_BRIGHTENING_THRESHOLDS = new float[]{100f};
    private static final float[] DEFAULT_SCREEN_DARKENING_THRESHOLDS = new float[]{200f};

    private static final int INTERPOLATION_DEFAULT = 0;
    private static final int INTERPOLATION_LINEAR = 1;

    // Float.NaN (used as invalid for brightness) cannot be stored in config.xml
    // so -2 is used instead
    private static final float INVALID_BRIGHTNESS_IN_CONFIG = -2f;

    private static final float NITS_INVALID = -1;

    // Length of the ambient light horizon used to calculate the long term estimate of ambient
    // light.
    private static final int AMBIENT_LIGHT_LONG_HORIZON_MILLIS = 10000;

    // Length of the ambient light horizon used to calculate short-term estimate of ambient light.
    private static final int AMBIENT_LIGHT_SHORT_HORIZON_MILLIS = 2000;

    // Invalid value of AutoBrightness brightening and darkening light debounce
    private static final int INVALID_AUTO_BRIGHTNESS_LIGHT_DEBOUNCE = -1;

    @VisibleForTesting
    static final float HDR_PERCENT_OF_SCREEN_REQUIRED_DEFAULT = 0.5f;

    private final Context mContext;

    // The details of the ambient light sensor associated with this display.
    private final SensorData mAmbientLightSensor = new SensorData();

    // The details of the proximity sensor associated with this display.
    private final SensorData mProximitySensor = new SensorData();

    private final List<RefreshRateLimitation> mRefreshRateLimitations =
            new ArrayList<>(2 /*initialCapacity*/);

    // Nits and backlight values that are loaded from either the display device config file, or
    // config.xml. These are the raw values and just used for the dumpsys
    private float[] mRawNits;
    private float[] mRawBacklight;
    private int mInterpolationType;

    // These arrays are calculated from the raw arrays, but clamped to contain values equal to and
    // between mBacklightMinimum and mBacklightMaximum. These three arrays should all be the same
    // length
    // Nits array that is used to store the entire range of nits values that the device supports
    private float[] mNits;
    // Backlight array holds the values that the HAL uses to display the corresponding nits values
    private float[] mBacklight;
    // Purely an array that covers the ranges of values 0.0 - 1.0, indicating the system brightness
    // for the corresponding values above
    private float[] mBrightness;


    /**
     * Array of desired screen brightness in nits corresponding to the lux values
     * in the mBrightnessLevelsLux array. The display brightness is defined as the
     * measured brightness of an all-white image. The brightness values must be non-negative and
     * non-decreasing. This must be overridden in platform specific overlays
     */
    private float[] mBrightnessLevelsNits;

    /**
     * Array of light sensor lux values to define our levels for auto backlight
     * brightness support.

     * The N + 1 entries of this array define N control points defined in mBrightnessLevelsNits,
     * with first value always being 0 lux

     * The control points must be strictly increasing.  Each control point
     * corresponds to an entry in the brightness backlight values arrays.
     * For example, if lux == level[1] (second element of the levels array)
     * then the brightness will be determined by value[0] (first element
     * of the brightness values array).
     *
     * Spline interpolation is used to determine the auto-brightness
     * backlight values for lux levels between these control points.
     *
     */
    private float[] mBrightnessLevelsLux;

    private float mBacklightMinimum = Float.NaN;
    private float mBacklightMaximum = Float.NaN;
    private float mBrightnessDefault = Float.NaN;
    private float mBrightnessRampFastDecrease = Float.NaN;
    private float mBrightnessRampFastIncrease = Float.NaN;
    private float mBrightnessRampSlowDecrease = Float.NaN;
    private float mBrightnessRampSlowIncrease = Float.NaN;
    private long mBrightnessRampDecreaseMaxMillis = 0;
    private long mBrightnessRampIncreaseMaxMillis = 0;
    private int mAmbientHorizonLong = AMBIENT_LIGHT_LONG_HORIZON_MILLIS;
    private int mAmbientHorizonShort = AMBIENT_LIGHT_SHORT_HORIZON_MILLIS;
    private float mScreenBrighteningMinThreshold = 0.0f;     // Retain behaviour as though there is
    private float mScreenBrighteningMinThresholdIdle = 0.0f; // no minimum threshold for change in
    private float mScreenDarkeningMinThreshold = 0.0f;       // screen brightness or ambient
    private float mScreenDarkeningMinThresholdIdle = 0.0f;   // brightness.
    private float mAmbientLuxBrighteningMinThreshold = 0.0f;
    private float mAmbientLuxBrighteningMinThresholdIdle = 0.0f;
    private float mAmbientLuxDarkeningMinThreshold = 0.0f;
    private float mAmbientLuxDarkeningMinThresholdIdle = 0.0f;

    // Screen brightness thresholds levels & percentages
    private float[] mScreenBrighteningLevels = DEFAULT_SCREEN_THRESHOLD_LEVELS;
    private float[] mScreenBrighteningPercentages = DEFAULT_SCREEN_BRIGHTENING_THRESHOLDS;
    private float[] mScreenDarkeningLevels = DEFAULT_SCREEN_THRESHOLD_LEVELS;
    private float[] mScreenDarkeningPercentages = DEFAULT_SCREEN_DARKENING_THRESHOLDS;

    // Screen brightness thresholds levels & percentages for idle mode
    private float[] mScreenBrighteningLevelsIdle = DEFAULT_SCREEN_THRESHOLD_LEVELS;
    private float[] mScreenBrighteningPercentagesIdle = DEFAULT_SCREEN_BRIGHTENING_THRESHOLDS;
    private float[] mScreenDarkeningLevelsIdle = DEFAULT_SCREEN_THRESHOLD_LEVELS;
    private float[] mScreenDarkeningPercentagesIdle = DEFAULT_SCREEN_DARKENING_THRESHOLDS;

    // Ambient brightness thresholds levels & percentages
    private float[] mAmbientBrighteningLevels = DEFAULT_AMBIENT_THRESHOLD_LEVELS;
    private float[] mAmbientBrighteningPercentages = DEFAULT_AMBIENT_BRIGHTENING_THRESHOLDS;
    private float[] mAmbientDarkeningLevels = DEFAULT_AMBIENT_THRESHOLD_LEVELS;
    private float[] mAmbientDarkeningPercentages = DEFAULT_AMBIENT_DARKENING_THRESHOLDS;

    // Ambient brightness thresholds levels & percentages for idle mode
    private float[] mAmbientBrighteningLevelsIdle = DEFAULT_AMBIENT_THRESHOLD_LEVELS;
    private float[] mAmbientBrighteningPercentagesIdle = DEFAULT_AMBIENT_BRIGHTENING_THRESHOLDS;
    private float[] mAmbientDarkeningLevelsIdle = DEFAULT_AMBIENT_THRESHOLD_LEVELS;
    private float[] mAmbientDarkeningPercentagesIdle = DEFAULT_AMBIENT_DARKENING_THRESHOLDS;

    private Spline mBrightnessToBacklightSpline;
    private Spline mBacklightToBrightnessSpline;
    private Spline mBacklightToNitsSpline;
    private Spline mNitsToBacklightSpline;
    private List<String> mQuirks;
    private boolean mIsHighBrightnessModeEnabled = false;
    private HighBrightnessModeData mHbmData;
    private DensityMapping mDensityMapping;
    private String mLoadedFrom = null;
    private Spline mSdrToHdrRatioSpline;

    // Represents the auto-brightness brightening light debounce.
    private long mAutoBrightnessBrighteningLightDebounce =
            INVALID_AUTO_BRIGHTNESS_LIGHT_DEBOUNCE;

    // Represents the auto-brightness darkening light debounce.
    private long mAutoBrightnessDarkeningLightDebounce =
            INVALID_AUTO_BRIGHTNESS_LIGHT_DEBOUNCE;

    // Brightness Throttling data may be updated via the DeviceConfig. Here we store the original
    // data, which comes from the ddc, and the current one, which may be the DeviceConfig
    // overwritten value.
    private BrightnessThrottlingData mBrightnessThrottlingData;
    private BrightnessThrottlingData mOriginalBrightnessThrottlingData;

    @VisibleForTesting
    DisplayDeviceConfig(Context context) {
        mContext = context;
    }

    /**
     * Creates an instance for the specified display. Tries to find a file with identifier in the
     * following priority order:
     * <ol>
     *     <li>physicalDisplayId</li>
     *     <li>physicalDisplayId without a stable flag (old system)</li>
     *     <li>portId</li>
     * </ol>
     *
     * @param physicalDisplayId The display ID for which to load the configuration.
     * @return A configuration instance for the specified display.
     */
    public static DisplayDeviceConfig create(Context context, long physicalDisplayId,
            boolean isFirstDisplay) {
        final DisplayDeviceConfig config = createWithoutDefaultValues(context, physicalDisplayId,
                isFirstDisplay);

        config.copyUninitializedValuesFromSecondaryConfig(loadDefaultConfigurationXml(context));
        return config;
    }

    /**
     * Creates an instance using global values since no display device config xml exists. Uses
     * values from config or PowerManager.
     *
     * @param context      The context from which the DisplayDeviceConfig is to be constructed.
     * @param useConfigXml A flag indicating if values are to be loaded from the configuration file,
     *                     or the default values.
     * @return A configuration instance.
     */
    public static DisplayDeviceConfig create(Context context, boolean useConfigXml) {
        final DisplayDeviceConfig config;
        if (useConfigXml) {
            config = getConfigFromGlobalXml(context);
        } else {
            config = getConfigFromPmValues(context);
        }
        return config;
    }

    private static DisplayDeviceConfig createWithoutDefaultValues(Context context,
            long physicalDisplayId, boolean isFirstDisplay) {
        DisplayDeviceConfig config;

        config = loadConfigFromDirectory(context, Environment.getProductDirectory(),
                physicalDisplayId);
        if (config != null) {
            return config;
        }

        config = loadConfigFromDirectory(context, Environment.getVendorDirectory(),
                physicalDisplayId);
        if (config != null) {
            return config;
        }

        // If no config can be loaded from any ddc xml at all,
        // prepare a whole config using the global config.xml.
        // Guaranteed not null
        return create(context, isFirstDisplay);
    }

    private static DisplayConfiguration loadDefaultConfigurationXml(Context context) {
        List<File> defaultXmlLocations = new ArrayList<>();
        defaultXmlLocations.add(Environment.buildPath(Environment.getProductDirectory(),
                ETC_DIR, DISPLAY_CONFIG_DIR, DEFAULT_CONFIG_FILE));
        defaultXmlLocations.add(Environment.buildPath(Environment.getVendorDirectory(),
                ETC_DIR, DISPLAY_CONFIG_DIR, DEFAULT_CONFIG_FILE));

        // Read config_defaultUiModeType directly because UiModeManager hasn't started yet.
        final int uiModeType = context.getResources()
                .getInteger(com.android.internal.R.integer.config_defaultUiModeType);
        final String uiModeTypeStr = Configuration.getUiModeTypeString(uiModeType);
        if (uiModeTypeStr != null) {
            defaultXmlLocations.add(Environment.buildPath(Environment.getRootDirectory(),
                    ETC_DIR, DISPLAY_CONFIG_DIR,
                    String.format(DEFAULT_CONFIG_FILE_WITH_UIMODE_FORMAT, uiModeTypeStr)));
        }
        defaultXmlLocations.add(Environment.buildPath(Environment.getRootDirectory(),
                ETC_DIR, DISPLAY_CONFIG_DIR, DEFAULT_CONFIG_FILE));

        final File configFile = getFirstExistingFile(defaultXmlLocations);
        if (configFile == null) {
            // Display configuration files aren't required to exist.
            return null;
        }

        DisplayConfiguration defaultConfig = null;

        try (InputStream in = new BufferedInputStream(new FileInputStream(configFile))) {
            defaultConfig = XmlParser.read(in);
            if (defaultConfig == null) {
                Slog.i(TAG, "Default DisplayDeviceConfig file is null");
            }
        } catch (IOException | DatatypeConfigurationException | XmlPullParserException e) {
            Slog.e(TAG, "Encountered an error while reading/parsing display config file: "
                    + configFile, e);
        }

        return defaultConfig;
    }

    private static File getFirstExistingFile(Collection<File> files) {
        for (File file : files) {
            if (file.exists() && file.isFile()) {
                return file;
            }
        }
        return null;
    }

    private static DisplayDeviceConfig loadConfigFromDirectory(Context context,
            File baseDirectory, long physicalDisplayId) {
        DisplayDeviceConfig config;
        // Create config using filename from physical ID (including "stable" bit).
        config = getConfigFromSuffix(context, baseDirectory, STABLE_ID_SUFFIX_FORMAT,
                physicalDisplayId);
        if (config != null) {
            return config;
        }

        // Create config using filename from physical ID (excluding "stable" bit).
        final long withoutStableFlag = physicalDisplayId & ~STABLE_FLAG;
        config = getConfigFromSuffix(context, baseDirectory, NO_SUFFIX_FORMAT, withoutStableFlag);
        if (config != null) {
            return config;
        }

        // Create config using filename from port ID.
        final DisplayAddress.Physical physicalAddress =
                DisplayAddress.fromPhysicalDisplayId(physicalDisplayId);
        int port = physicalAddress.getPort();
        config = getConfigFromSuffix(context, baseDirectory, PORT_SUFFIX_FORMAT, port);
        return config;
    }

    void setBrightnessThrottlingData(BrightnessThrottlingData brightnessThrottlingData) {
        mBrightnessThrottlingData = brightnessThrottlingData;
    }

    /**
     * Return the brightness mapping nits array.
     *
     * @return The brightness mapping nits array.
     */
    public float[] getNits() {
        return mNits;
    }

    /**
     * Return the brightness mapping backlight array.
     *
     * @return The backlight mapping value array.
     */
    public float[] getBacklight() {
        return mBacklight;
    }

    /**
     * Calculates the backlight value, as recognised by the HAL, from the brightness value given
     * that the rest of the system deals with.
     *
     * @param brightness value on the framework scale of 0-1
     * @return backlight value on the HAL scale of 0-1
     */
    public float getBacklightFromBrightness(float brightness) {
        return mBrightnessToBacklightSpline.interpolate(brightness);
    }

    /**
     * Calculates the nits value for the specified backlight value if a mapping exists.
     *
     * @return The mapped nits or 0 if no mapping exits.
     */
    public float getNitsFromBacklight(float backlight) {
        if (mBacklightToNitsSpline == null) {
            Slog.wtf(TAG, "requesting nits when no mapping exists.");
            return NITS_INVALID;
        }
        backlight = Math.max(backlight, mBacklightMinimum);
        return mBacklightToNitsSpline.interpolate(backlight);
    }

    /**
     * Calculate the HDR brightness for the specified SDR brightenss.
     *
     * @return the HDR brightness or BRIGHTNESS_INVALID when no mapping exists.
     */
    public float getHdrBrightnessFromSdr(float brightness) {
        if (mSdrToHdrRatioSpline == null) {
            return PowerManager.BRIGHTNESS_INVALID;
        }

        float backlight = getBacklightFromBrightness(brightness);
        float nits = getNitsFromBacklight(backlight);
        if (nits == NITS_INVALID) {
            return PowerManager.BRIGHTNESS_INVALID;
        }

        float ratio = mSdrToHdrRatioSpline.interpolate(nits);
        float hdrNits = nits * ratio;
        if (mNitsToBacklightSpline == null) {
            return PowerManager.BRIGHTNESS_INVALID;
        }

        float hdrBacklight = mNitsToBacklightSpline.interpolate(hdrNits);
        hdrBacklight = Math.max(mBacklightMinimum, Math.min(mBacklightMaximum, hdrBacklight));
        float hdrBrightness = mBacklightToBrightnessSpline.interpolate(hdrBacklight);

        if (DEBUG) {
            Slog.d(TAG, "getHdrBrightnessFromSdr: sdr brightness " + brightness
                    + " backlight " + backlight
                    + " nits " + nits
                    + " ratio " + ratio
                    + " hdrNits " + hdrNits
                    + " hdrBacklight " + hdrBacklight
                    + " hdrBrightness " + hdrBrightness
            );
        }
        return hdrBrightness;
    }

    /**
     * Return an array of equal length to backlight and nits, that covers the entire system
     * brightness range of 0.0-1.0.
     *
     * @return brightness array
     */
    public float[] getBrightness() {
        return mBrightness;
    }

    /**
     * Return the default brightness on a scale of 0.0f - 1.0f
     *
     * @return default brightness
     */
    public float getBrightnessDefault() {
        return mBrightnessDefault;
    }

    public float getBrightnessRampFastDecrease() {
        return mBrightnessRampFastDecrease;
    }

    public float getBrightnessRampFastIncrease() {
        return mBrightnessRampFastIncrease;
    }

    public float getBrightnessRampSlowDecrease() {
        return mBrightnessRampSlowDecrease;
    }

    public float getBrightnessRampSlowIncrease() {
        return mBrightnessRampSlowIncrease;
    }

    public long getBrightnessRampDecreaseMaxMillis() {
        return mBrightnessRampDecreaseMaxMillis;
    }

    public long getBrightnessRampIncreaseMaxMillis() {
        return mBrightnessRampIncreaseMaxMillis;
    }

    public int getAmbientHorizonLong() {
        return mAmbientHorizonLong;
    }

    public int getAmbientHorizonShort() {
        return mAmbientHorizonShort;
    }

    /**
     * The minimum value for the screen brightness increase to actually occur.
     * @return float value in brightness scale of 0 - 1.
     */
    public float getScreenBrighteningMinThreshold() {
        return mScreenBrighteningMinThreshold;
    }

    /**
     * The minimum value for the screen brightness decrease to actually occur.
     * @return float value in brightness scale of 0 - 1.
     */
    public float getScreenDarkeningMinThreshold() {
        return mScreenDarkeningMinThreshold;
    }

    /**
     * The minimum value for the screen brightness increase to actually occur while in idle screen
     * brightness mode.
     * @return float value in brightness scale of 0 - 1.
     */
    public float getScreenBrighteningMinThresholdIdle() {
        return mScreenBrighteningMinThresholdIdle;
    }

    /**
     * The minimum value for the screen brightness decrease to actually occur while in idle screen
     * brightness mode.
     * @return float value in brightness scale of 0 - 1.
     */
    public float getScreenDarkeningMinThresholdIdle() {
        return mScreenDarkeningMinThresholdIdle;
    }

    /**
     * The minimum value for the ambient lux increase for a screen brightness change to actually
     * occur.
     * @return float value in lux.
     */
    public float getAmbientLuxBrighteningMinThreshold() {
        return mAmbientLuxBrighteningMinThreshold;
    }

    /**
     * The minimum value for the ambient lux decrease for a screen brightness change to actually
     * occur.
     * @return float value in lux.
     */
    public float getAmbientLuxDarkeningMinThreshold() {
        return mAmbientLuxDarkeningMinThreshold;
    }

    /**
     * The minimum value for the ambient lux increase for a screen brightness change to actually
     * occur while in idle screen brightness mode.
     * @return float value in lux.
     */
    public float getAmbientLuxBrighteningMinThresholdIdle() {
        return mAmbientLuxBrighteningMinThresholdIdle;
    }

    /**
     * The minimum value for the ambient lux decrease for a screen brightness change to actually
     * occur while in idle screen brightness mode.
     * @return float value in lux.
     */
    public float getAmbientLuxDarkeningMinThresholdIdle() {
        return mAmbientLuxDarkeningMinThresholdIdle;
    }

    /**
     * The array that describes the range of screen brightness that each threshold percentage
     * applies within.
     *
     * The (zero-based) index is calculated as follows
     * value = current screen brightness value
     * level = mScreenBrighteningLevels
     *
     * condition                       return
     * value < level[0]                = 0.0f
     * level[n] <= value < level[n+1]  = mScreenBrighteningPercentages[n]
     * level[MAX] <= value             = mScreenBrighteningPercentages[MAX]
     *
     * @return the screen brightness levels between 0.0 and 1.0 for which each
     * mScreenBrighteningPercentages applies
     */
    public float[] getScreenBrighteningLevels() {
        return mScreenBrighteningLevels;
    }

    /**
     * The array that describes the screen brightening threshold percentage change at each screen
     * brightness level described in mScreenBrighteningLevels.
     *
     * @return the percentages between 0 and 100 of brightness increase required in order for the
     * screen brightness to change
     */
    public float[] getScreenBrighteningPercentages() {
        return mScreenBrighteningPercentages;
    }

    /**
     * The array that describes the range of screen brightness that each threshold percentage
     * applies within.
     *
     * The (zero-based) index is calculated as follows
     * value = current screen brightness value
     * level = mScreenDarkeningLevels
     *
     * condition                       return
     * value < level[0]                = 0.0f
     * level[n] <= value < level[n+1]  = mScreenDarkeningPercentages[n]
     * level[MAX] <= value             = mScreenDarkeningPercentages[MAX]
     *
     * @return the screen brightness levels between 0.0 and 1.0 for which each
     * mScreenDarkeningPercentages applies
     */
    public float[] getScreenDarkeningLevels() {
        return mScreenDarkeningLevels;
    }

    /**
     * The array that describes the screen darkening threshold percentage change at each screen
     * brightness level described in mScreenDarkeningLevels.
     *
     * @return the percentages between 0 and 100 of brightness decrease required in order for the
     * screen brightness to change
     */
    public float[] getScreenDarkeningPercentages() {
        return mScreenDarkeningPercentages;
    }

    /**
     * The array that describes the range of ambient brightness that each threshold
     * percentage applies within.
     *
     * The (zero-based) index is calculated as follows
     * value = current ambient brightness value
     * level = mAmbientBrighteningLevels
     *
     * condition                       return
     * value < level[0]                = 0.0f
     * level[n] <= value < level[n+1]  = mAmbientBrighteningPercentages[n]
     * level[MAX] <= value             = mAmbientBrighteningPercentages[MAX]
     *
     * @return the ambient brightness levels from 0 lux upwards for which each
     * mAmbientBrighteningPercentages applies
     */
    public float[] getAmbientBrighteningLevels() {
        return mAmbientBrighteningLevels;
    }

    /**
     * The array that describes the ambient brightening threshold percentage change at each ambient
     * brightness level described in mAmbientBrighteningLevels.
     *
     * @return the percentages between 0 and 100 of brightness increase required in order for the
     * screen brightness to change
     */
    public float[] getAmbientBrighteningPercentages() {
        return mAmbientBrighteningPercentages;
    }

    /**
     * The array that describes the range of ambient brightness that each threshold percentage
     * applies within.
     *
     * The (zero-based) index is calculated as follows
     * value = current ambient brightness value
     * level = mAmbientDarkeningLevels
     *
     * condition                       return
     * value < level[0]                = 0.0f
     * level[n] <= value < level[n+1]  = mAmbientDarkeningPercentages[n]
     * level[MAX] <= value             = mAmbientDarkeningPercentages[MAX]
     *
     * @return the ambient brightness levels from 0 lux upwards for which each
     * mAmbientDarkeningPercentages applies
     */
    public float[] getAmbientDarkeningLevels() {
        return mAmbientDarkeningLevels;
    }

    /**
     * The array that describes the ambient darkening threshold percentage change at each ambient
     * brightness level described in mAmbientDarkeningLevels.
     *
     * @return the percentages between 0 and 100 of brightness decrease required in order for the
     * screen brightness to change
     */
    public float[] getAmbientDarkeningPercentages() {
        return mAmbientDarkeningPercentages;
    }

    /**
     * The array that describes the range of screen brightness that each threshold percentage
     * applies within whilst in idle screen brightness mode.
     *
     * The (zero-based) index is calculated as follows
     * value = current screen brightness value
     * level = mScreenBrighteningLevelsIdle
     *
     * condition                       return
     * value < level[0]                = 0.0f
     * level[n] <= value < level[n+1]  = mScreenBrighteningPercentagesIdle[n]
     * level[MAX] <= value             = mScreenBrighteningPercentagesIdle[MAX]
     *
     * @return the screen brightness levels between 0.0 and 1.0 for which each
     * mScreenBrighteningPercentagesIdle applies
     */
    public float[] getScreenBrighteningLevelsIdle() {
        return mScreenBrighteningLevelsIdle;
    }

    /**
     * The array that describes the screen brightening threshold percentage change at each screen
     * brightness level described in mScreenBrighteningLevelsIdle.
     *
     * @return the percentages between 0 and 100 of brightness increase required in order for the
     * screen brightness to change while in idle mode.
     */
    public float[] getScreenBrighteningPercentagesIdle() {
        return mScreenBrighteningPercentagesIdle;
    }

    /**
     * The array that describes the range of screen brightness that each threshold percentage
     * applies within whilst in idle screen brightness mode.
     *
     * The (zero-based) index is calculated as follows
     * value = current screen brightness value
     * level = mScreenDarkeningLevelsIdle
     *
     * condition                       return
     * value < level[0]                = 0.0f
     * level[n] <= value < level[n+1]  = mScreenDarkeningPercentagesIdle[n]
     * level[MAX] <= value             = mScreenDarkeningPercentagesIdle[MAX]
     *
     * @return the screen brightness levels between 0.0 and 1.0 for which each
     * mScreenDarkeningPercentagesIdle applies
     */
    public float[] getScreenDarkeningLevelsIdle() {
        return mScreenDarkeningLevelsIdle;
    }

    /**
     * The array that describes the screen darkening threshold percentage change at each screen
     * brightness level described in mScreenDarkeningLevelsIdle.
     *
     * @return the percentages between 0 and 100 of brightness decrease required in order for the
     * screen brightness to change while in idle mode.
     */
    public float[] getScreenDarkeningPercentagesIdle() {
        return mScreenDarkeningPercentagesIdle;
    }

    /**
     * The array that describes the range of ambient brightness that each threshold percentage
     * applies within whilst in idle screen brightness mode.
     *
     * The (zero-based) index is calculated as follows
     * value = current ambient brightness value
     * level = mAmbientBrighteningLevelsIdle
     *
     * condition                       return
     * value < level[0]                = 0.0f
     * level[n] <= value < level[n+1]  = mAmbientBrighteningPercentagesIdle[n]
     * level[MAX] <= value             = mAmbientBrighteningPercentagesIdle[MAX]
     *
     * @return the ambient brightness levels from 0 lux upwards for which each
     * mAmbientBrighteningPercentagesIdle applies
     */
    public float[] getAmbientBrighteningLevelsIdle() {
        return mAmbientBrighteningLevelsIdle;
    }

    /**
     * The array that describes the ambient brightness threshold percentage change whilst in
     * idle screen brightness mode at each ambient brightness level described in
     * mAmbientBrighteningLevelsIdle.
     *
     * @return the percentages between 0 and 100 of ambient brightness increase required in order
     * for the screen brightness to change
     */
    public float[] getAmbientBrighteningPercentagesIdle() {
        return mAmbientBrighteningPercentagesIdle;
    }

    /**
     * The array that describes the range of ambient brightness that each threshold percentage
     * applies within whilst in idle screen brightness mode.
     *
     * The (zero-based) index is calculated as follows
     * value = current ambient brightness value
     * level = mAmbientDarkeningLevelsIdle
     *
     * condition                       return
     * value < level[0]                = 0.0f
     * level[n] <= value < level[n+1]  = mAmbientDarkeningPercentagesIdle[n]
     * level[MAX] <= value             = mAmbientDarkeningPercentagesIdle[MAX]
     *
     * @return the ambient brightness levels from 0 lux upwards for which each
     * mAmbientDarkeningPercentagesIdle applies
     */
    public float[] getAmbientDarkeningLevelsIdle() {
        return mAmbientDarkeningLevelsIdle;
    }

    /**
     * The array that describes the ambient brightness threshold percentage change whilst in
     * idle screen brightness mode at each ambient brightness level described in
     * mAmbientDarkeningLevelsIdle.
     *
     * @return the percentages between 0 and 100 of ambient brightness decrease required in order
     * for the screen brightness to change
     */
    public float[] getAmbientDarkeningPercentagesIdle() {
        return mAmbientDarkeningPercentagesIdle;
    }

    SensorData getAmbientLightSensor() {
        return mAmbientLightSensor;
    }

    SensorData getProximitySensor() {
        return mProximitySensor;
    }

    /**
     * @param quirkValue The quirk to test.
     * @return {@code true} if the specified quirk is present in this configuration, {@code false}
     * otherwise.
     */
    public boolean hasQuirk(String quirkValue) {
        return mQuirks != null && mQuirks.contains(quirkValue);
    }

    /**
     * @return high brightness mode configuration data for the display.
     */
    public HighBrightnessModeData getHighBrightnessModeData() {
        if (!mIsHighBrightnessModeEnabled || mHbmData == null) {
            return null;
        }

        HighBrightnessModeData hbmData = new HighBrightnessModeData();
        mHbmData.copyTo(hbmData);
        return hbmData;
    }

    public List<RefreshRateLimitation> getRefreshRateLimitations() {
        return mRefreshRateLimitations;
    }

    public DensityMapping getDensityMapping() {
        return mDensityMapping;
    }

    /**
     * @return brightness throttling data configuration data for the display.
     */
    public BrightnessThrottlingData getBrightnessThrottlingData() {
        return BrightnessThrottlingData.create(mBrightnessThrottlingData);
    }

    /**
     * @return Auto brightness darkening light debounce
     */
    public long getAutoBrightnessDarkeningLightDebounce() {
        return mAutoBrightnessDarkeningLightDebounce;
    }

    /**
     * @return Auto brightness brightening light debounce
     */
    public long getAutoBrightnessBrighteningLightDebounce() {
        return mAutoBrightnessBrighteningLightDebounce;
    }

    /**
     * @return Auto brightness brightening ambient lux levels
     */
    public float[] getAutoBrightnessBrighteningLevelsLux() {
        return mBrightnessLevelsLux;
    }

    /**
     * @return Auto brightness brightening nits levels
     */
    public float[] getAutoBrightnessBrighteningLevelsNits() {
        return mBrightnessLevelsNits;
    }

    @Override
    public String toString() {
        return "DisplayDeviceConfig{"
                + "mLoadedFrom=" + mLoadedFrom
                + ", mBacklight=" + Arrays.toString(mBacklight)
                + ", mNits=" + Arrays.toString(mNits)
                + ", mRawBacklight=" + Arrays.toString(mRawBacklight)
                + ", mRawNits=" + Arrays.toString(mRawNits)
                + ", mInterpolationType=" + mInterpolationType
                + ", mBrightness=" + Arrays.toString(mBrightness)
                + ", mBrightnessToBacklightSpline=" + mBrightnessToBacklightSpline
                + ", mBacklightToBrightnessSpline=" + mBacklightToBrightnessSpline
                + ", mNitsToBacklightSpline=" + mNitsToBacklightSpline
                + ", mBacklightMinimum=" + mBacklightMinimum
                + ", mBacklightMaximum=" + mBacklightMaximum
                + ", mBrightnessDefault=" + mBrightnessDefault
                + ", mQuirks=" + mQuirks
                + ", isHbmEnabled=" + mIsHighBrightnessModeEnabled
                + ", mHbmData=" + mHbmData
                + ", mSdrToHdrRatioSpline=" + mSdrToHdrRatioSpline
                + ", mBrightnessThrottlingData=" + mBrightnessThrottlingData
                + ", mOriginalBrightnessThrottlingData=" + mOriginalBrightnessThrottlingData
                + "\n"
                + ", mBrightnessRampFastDecrease=" + mBrightnessRampFastDecrease
                + ", mBrightnessRampFastIncrease=" + mBrightnessRampFastIncrease
                + ", mBrightnessRampSlowDecrease=" + mBrightnessRampSlowDecrease
                + ", mBrightnessRampSlowIncrease=" + mBrightnessRampSlowIncrease
                + ", mBrightnessRampDecreaseMaxMillis=" + mBrightnessRampDecreaseMaxMillis
                + ", mBrightnessRampIncreaseMaxMillis=" + mBrightnessRampIncreaseMaxMillis
                + "\n"
                + ", mAmbientHorizonLong=" + mAmbientHorizonLong
                + ", mAmbientHorizonShort=" + mAmbientHorizonShort
                + "\n"
                + ", mScreenDarkeningMinThreshold=" + mScreenDarkeningMinThreshold
                + ", mScreenDarkeningMinThresholdIdle=" + mScreenDarkeningMinThresholdIdle
                + ", mScreenBrighteningMinThreshold=" + mScreenBrighteningMinThreshold
                + ", mScreenBrighteningMinThresholdIdle=" + mScreenBrighteningMinThresholdIdle
                + ", mAmbientLuxDarkeningMinThreshold=" + mAmbientLuxDarkeningMinThreshold
                + ", mAmbientLuxDarkeningMinThresholdIdle=" + mAmbientLuxDarkeningMinThresholdIdle
                + ", mAmbientLuxBrighteningMinThreshold=" + mAmbientLuxBrighteningMinThreshold
                + ", mAmbientLuxBrighteningMinThresholdIdle="
                + mAmbientLuxBrighteningMinThresholdIdle
                + "\n"
                + ", mScreenBrighteningLevels=" + Arrays.toString(
                mScreenBrighteningLevels)
                + ", mScreenBrighteningPercentages=" + Arrays.toString(
                mScreenBrighteningPercentages)
                + ", mScreenDarkeningLevels=" + Arrays.toString(
                mScreenDarkeningLevels)
                + ", mScreenDarkeningPercentages=" + Arrays.toString(
                mScreenDarkeningPercentages)
                + ", mAmbientBrighteningLevels=" + Arrays.toString(
                mAmbientBrighteningLevels)
                + ", mAmbientBrighteningPercentages=" + Arrays.toString(
                mAmbientBrighteningPercentages)
                + ", mAmbientDarkeningLevels=" + Arrays.toString(
                mAmbientDarkeningLevels)
                + ", mAmbientDarkeningPercentages=" + Arrays.toString(
                mAmbientDarkeningPercentages)
                + "\n"
                + ", mAmbientBrighteningLevelsIdle=" + Arrays.toString(
                mAmbientBrighteningLevelsIdle)
                + ", mAmbientBrighteningPercentagesIdle=" + Arrays.toString(
                mAmbientBrighteningPercentagesIdle)
                + ", mAmbientDarkeningLevelsIdle=" + Arrays.toString(
                mAmbientDarkeningLevelsIdle)
                + ", mAmbientDarkeningPercentagesIdle=" + Arrays.toString(
                mAmbientDarkeningPercentagesIdle)
                + ", mScreenBrighteningLevelsIdle=" + Arrays.toString(
                mScreenBrighteningLevelsIdle)
                + ", mScreenBrighteningPercentagesIdle=" + Arrays.toString(
                mScreenBrighteningPercentagesIdle)
                + ", mScreenDarkeningLevelsIdle=" + Arrays.toString(
                mScreenDarkeningLevelsIdle)
                + ", mScreenDarkeningPercentagesIdle=" + Arrays.toString(
                mScreenDarkeningPercentagesIdle)
                + "\n"
                + ", mAmbientLightSensor=" + mAmbientLightSensor
                + ", mProximitySensor=" + mProximitySensor
                + ", mRefreshRateLimitations= " + Arrays.toString(mRefreshRateLimitations.toArray())
                + ", mDensityMapping= " + mDensityMapping
                + ", mAutoBrightnessBrighteningLightDebounce= "
                + mAutoBrightnessBrighteningLightDebounce
                + ", mAutoBrightnessDarkeningLightDebounce= "
                + mAutoBrightnessDarkeningLightDebounce
                + ", mBrightnessLevelsLux= " + Arrays.toString(mBrightnessLevelsLux)
                + ", mBrightnessLevelsNits= " + Arrays.toString(mBrightnessLevelsNits)
                + "}";
    }

    private static DisplayDeviceConfig getConfigFromSuffix(Context context, File baseDirectory,
            String suffixFormat, long idNumber) {

        final String suffix = String.format(Locale.ROOT, suffixFormat, idNumber);
        final String filename = String.format(Locale.ROOT, CONFIG_FILE_FORMAT, suffix);
        final File filePath = Environment.buildPath(
                baseDirectory, ETC_DIR, DISPLAY_CONFIG_DIR, filename);
        final DisplayDeviceConfig config = new DisplayDeviceConfig(context);
        if (config.initFromFile(filePath)) {
            return config;
        }
        return null;
    }

    private static DisplayDeviceConfig getConfigFromGlobalXml(Context context) {
        DisplayDeviceConfig config = new DisplayDeviceConfig(context);
        config.initFromGlobalXml();
        return config;
    }

    private static DisplayDeviceConfig getConfigFromPmValues(Context context) {
        DisplayDeviceConfig config = new DisplayDeviceConfig(context);
        config.initFromDefaultValues();
        return config;
    }

    @VisibleForTesting
    boolean initFromFile(File configFile) {
        if (!configFile.exists()) {
            // Display configuration files aren't required to exist.
            return false;
        }

        if (!configFile.isFile()) {
            Slog.e(TAG, "Display configuration is not a file: " + configFile + ", skipping");
            return false;
        }

        try (InputStream in = new BufferedInputStream(new FileInputStream(configFile))) {
            final DisplayConfiguration config = XmlParser.read(in);
            if (config != null) {
                loadDensityMapping(config);
                loadBrightnessDefaultFromDdcXml(config);
                loadBrightnessConstraintsFromConfigXml();
                loadBrightnessMap(config);
                loadBrightnessThrottlingMap(config);
                loadHighBrightnessModeData(config);
                loadQuirks(config);
                loadBrightnessRamps(config);
                loadAmbientLightSensorFromDdc(config);
                loadProxSensorFromDdc(config);
                loadAmbientHorizonFromDdc(config);
                loadBrightnessChangeThresholds(config);
                loadAutoBrightnessConfigValues(config);
            } else {
                Slog.w(TAG, "DisplayDeviceConfig file is null");
            }
        } catch (IOException | DatatypeConfigurationException | XmlPullParserException e) {
            Slog.e(TAG, "Encountered an error while reading/parsing display config file: "
                    + configFile, e);
        }
        mLoadedFrom = configFile.toString();
        return true;
    }

    private void initFromGlobalXml() {
        // If no ddc exists, use config.xml
        loadBrightnessDefaultFromConfigXml();
        loadBrightnessConstraintsFromConfigXml();
        loadBrightnessMapFromConfigXml();
        loadBrightnessRampsFromConfigXml();
        loadAmbientLightSensorFromConfigXml();
        loadBrightnessChangeThresholdsFromXml();
        setProxSensorUnspecified();
        loadAutoBrightnessConfigsFromConfigXml();
        mLoadedFrom = "<config.xml>";
    }

    private void initFromDefaultValues() {
        // Set all to basic values
        mLoadedFrom = "Static values";
        mBacklightMinimum = PowerManager.BRIGHTNESS_MIN;
        mBacklightMaximum = PowerManager.BRIGHTNESS_MAX;
        mBrightnessDefault = BRIGHTNESS_DEFAULT;
        mBrightnessRampFastDecrease = PowerManager.BRIGHTNESS_MAX;
        mBrightnessRampFastIncrease = PowerManager.BRIGHTNESS_MAX;
        mBrightnessRampSlowDecrease = PowerManager.BRIGHTNESS_MAX;
        mBrightnessRampSlowIncrease = PowerManager.BRIGHTNESS_MAX;
        mBrightnessRampDecreaseMaxMillis = 0;
        mBrightnessRampIncreaseMaxMillis = 0;
        setSimpleMappingStrategyValues();
        loadAmbientLightSensorFromConfigXml();
        setProxSensorUnspecified();
    }

    private void copyUninitializedValuesFromSecondaryConfig(DisplayConfiguration defaultConfig) {
        if (defaultConfig == null) {
            return;
        }

        if (mDensityMapping == null) {
            loadDensityMapping(defaultConfig);
        }
    }

    private void loadDensityMapping(DisplayConfiguration config) {
        if (config.getDensityMapping() == null) {
            return;
        }

        final List<Density> entriesFromXml = config.getDensityMapping().getDensity();

        final DensityMapping.Entry[] entries =
                new DensityMapping.Entry[entriesFromXml.size()];
        for (int i = 0; i < entriesFromXml.size(); i++) {
            final Density density = entriesFromXml.get(i);
            entries[i] = new DensityMapping.Entry(
                    density.getWidth().intValue(),
                    density.getHeight().intValue(),
                    density.getDensity().intValue());
        }
        mDensityMapping = DensityMapping.createByOwning(entries);
    }

    private void loadBrightnessDefaultFromDdcXml(DisplayConfiguration config) {
        // Default brightness values are stored in the displayDeviceConfig file,
        // Or we fallback standard values if not.
        // Priority 1: Value in the displayDeviceConfig
        // Priority 2: Value in the config.xml (float)
        // Priority 3: Value in the config.xml (int)
        if (config != null) {
            BigDecimal configBrightnessDefault = config.getScreenBrightnessDefault();
            if (configBrightnessDefault != null) {
                mBrightnessDefault = configBrightnessDefault.floatValue();
            } else {
                loadBrightnessDefaultFromConfigXml();
            }
        }
    }

    private void loadBrightnessDefaultFromConfigXml() {
        // Priority 1: Value in the config.xml (float)
        // Priority 2: Value in the config.xml (int)
        final float def = mContext.getResources().getFloat(com.android.internal.R.dimen
                .config_screenBrightnessSettingDefaultFloat);
        if (def == INVALID_BRIGHTNESS_IN_CONFIG) {
            mBrightnessDefault = BrightnessSynchronizer.brightnessIntToFloat(
                    mContext.getResources().getInteger(com.android.internal.R.integer
                            .config_screenBrightnessSettingDefault));
        } else {
            mBrightnessDefault = def;
        }
    }

    private void loadBrightnessConstraintsFromConfigXml() {
        // TODO(b/175373898) add constraints (min / max) to ddc.
        final float min = mContext.getResources().getFloat(com.android.internal.R.dimen
                .config_screenBrightnessSettingMinimumFloat);
        final float max = mContext.getResources().getFloat(com.android.internal.R.dimen
                .config_screenBrightnessSettingMaximumFloat);
        if (min == INVALID_BRIGHTNESS_IN_CONFIG || max == INVALID_BRIGHTNESS_IN_CONFIG) {
            mBacklightMinimum = BrightnessSynchronizer.brightnessIntToFloat(
                    mContext.getResources().getInteger(com.android.internal.R.integer
                            .config_screenBrightnessSettingMinimum));
            mBacklightMaximum = BrightnessSynchronizer.brightnessIntToFloat(
                    mContext.getResources().getInteger(com.android.internal.R.integer
                            .config_screenBrightnessSettingMaximum));
        } else {
            mBacklightMinimum = min;
            mBacklightMaximum = max;
        }
    }

    private void loadBrightnessMap(DisplayConfiguration config) {
        final NitsMap map = config.getScreenBrightnessMap();
        // Map may not exist in display device config
        if (map == null) {
            loadBrightnessMapFromConfigXml();
            return;
        }

        // Use the (preferred) display device config mapping
        final List<Point> points = map.getPoint();
        final int size = points.size();

        float[] nits = new float[size];
        float[] backlight = new float[size];

        mInterpolationType = convertInterpolationType(map.getInterpolation());
        int i = 0;
        for (Point point : points) {
            nits[i] = point.getNits().floatValue();
            backlight[i] = point.getValue().floatValue();
            if (i > 0) {
                if (nits[i] < nits[i - 1]) {
                    Slog.e(TAG, "screenBrightnessMap must be non-decreasing, ignoring rest "
                            + " of configuration. Nits: " + nits[i] + " < " + nits[i - 1]);
                    return;
                }

                if (backlight[i] < backlight[i - 1]) {
                    Slog.e(TAG, "screenBrightnessMap must be non-decreasing, ignoring rest "
                            + " of configuration. Value: " + backlight[i] + " < "
                            + backlight[i - 1]);
                    return;
                }
            }
            ++i;
        }
        mRawNits = nits;
        mRawBacklight = backlight;
        constrainNitsAndBacklightArrays();
    }

    private Spline loadSdrHdrRatioMap(HighBrightnessMode hbmConfig) {
        final SdrHdrRatioMap sdrHdrRatioMap = hbmConfig.getSdrHdrRatioMap_all();

        if (sdrHdrRatioMap == null) {
            return null;
        }

        final List<SdrHdrRatioPoint> points = sdrHdrRatioMap.getPoint();
        final int size = points.size();
        if (size <= 0) {
            return null;
        }

        float[] nits = new float[size];
        float[] ratios = new float[size];

        int i = 0;
        for (SdrHdrRatioPoint point : points) {
            nits[i] = point.getSdrNits().floatValue();
            if (i > 0) {
                if (nits[i] < nits[i - 1]) {
                    Slog.e(TAG, "sdrHdrRatioMap must be non-decreasing, ignoring rest "
                            + " of configuration. nits: " + nits[i] + " < "
                            + nits[i - 1]);
                    return null;
                }
            }
            ratios[i] = point.getHdrRatio().floatValue();
            ++i;
        }

        return Spline.createSpline(nits, ratios);
    }

    private void loadBrightnessThrottlingMap(DisplayConfiguration config) {
        final ThermalThrottling throttlingConfig = config.getThermalThrottling();
        if (throttlingConfig == null) {
            Slog.i(TAG, "no thermal throttling config found");
            return;
        }

        final BrightnessThrottlingMap map = throttlingConfig.getBrightnessThrottlingMap();
        if (map == null) {
            Slog.i(TAG, "no brightness throttling map found");
            return;
        }

        final List<BrightnessThrottlingPoint> points = map.getBrightnessThrottlingPoint();
        // At least 1 point is guaranteed by the display device config schema
        List<BrightnessThrottlingData.ThrottlingLevel> throttlingLevels =
                new ArrayList<>(points.size());

        boolean badConfig = false;
        for (BrightnessThrottlingPoint point : points) {
            ThermalStatus status = point.getThermalStatus();
            if (!thermalStatusIsValid(status)) {
                badConfig = true;
                break;
            }

            throttlingLevels.add(new BrightnessThrottlingData.ThrottlingLevel(
                    convertThermalStatus(status), point.getBrightness().floatValue()));
        }

        if (!badConfig) {
            mBrightnessThrottlingData = BrightnessThrottlingData.create(throttlingLevels);
            mOriginalBrightnessThrottlingData = mBrightnessThrottlingData;
        }
    }

    private void loadAutoBrightnessConfigValues(DisplayConfiguration config) {
        loadAutoBrightnessBrighteningLightDebounce(config.getAutoBrightness());
        loadAutoBrightnessDarkeningLightDebounce(config.getAutoBrightness());
        loadAutoBrightnessDisplayBrightnessMapping(config.getAutoBrightness());
    }

    /**
     * Loads the auto-brightness brightening light debounce. Internally, this takes care of loading
     * the value from the display config, and if not present, falls back to config.xml.
     */
    private void loadAutoBrightnessBrighteningLightDebounce(AutoBrightness autoBrightnessConfig) {
        if (autoBrightnessConfig == null
                || autoBrightnessConfig.getBrighteningLightDebounceMillis() == null) {
            mAutoBrightnessBrighteningLightDebounce = mContext.getResources().getInteger(
                    com.android.internal.R.integer.config_autoBrightnessBrighteningLightDebounce);
        } else {
            mAutoBrightnessBrighteningLightDebounce =
                    autoBrightnessConfig.getBrighteningLightDebounceMillis().intValue();
        }
    }

    /**
     * Loads the auto-brightness darkening light debounce. Internally, this takes care of loading
     * the value from the display config, and if not present, falls back to config.xml.
     */
    private void loadAutoBrightnessDarkeningLightDebounce(AutoBrightness autoBrightnessConfig) {
        if (autoBrightnessConfig == null
                || autoBrightnessConfig.getDarkeningLightDebounceMillis() == null) {
            mAutoBrightnessDarkeningLightDebounce = mContext.getResources().getInteger(
                    com.android.internal.R.integer.config_autoBrightnessDarkeningLightDebounce);
        } else {
            mAutoBrightnessDarkeningLightDebounce =
                    autoBrightnessConfig.getDarkeningLightDebounceMillis().intValue();
        }
    }

    /**
     * Loads the auto-brightness display brightness mappings. Internally, this takes care of
     * loading the value from the display config, and if not present, falls back to config.xml.
     */
    private void loadAutoBrightnessDisplayBrightnessMapping(AutoBrightness autoBrightnessConfig) {
        if (autoBrightnessConfig == null
                || autoBrightnessConfig.getDisplayBrightnessMapping() == null) {
            mBrightnessLevelsNits = getFloatArray(mContext.getResources()
                    .obtainTypedArray(com.android.internal.R.array
                            .config_autoBrightnessDisplayValuesNits), PowerManager
                    .BRIGHTNESS_OFF_FLOAT);
            mBrightnessLevelsLux = getLuxLevels(mContext.getResources()
                    .getIntArray(com.android.internal.R.array
                            .config_autoBrightnessLevels));
        } else {
            final int size = autoBrightnessConfig.getDisplayBrightnessMapping()
                    .getDisplayBrightnessPoint().size();
            mBrightnessLevelsNits = new float[size];
            // The first control point is implicit and always at 0 lux.
            mBrightnessLevelsLux = new float[size + 1];
            for (int i = 0; i < size; i++) {
                mBrightnessLevelsNits[i] = autoBrightnessConfig.getDisplayBrightnessMapping()
                        .getDisplayBrightnessPoint().get(i).getNits().floatValue();
                mBrightnessLevelsLux[i + 1] = autoBrightnessConfig.getDisplayBrightnessMapping()
                        .getDisplayBrightnessPoint().get(i).getLux().floatValue();
            }
        }
    }

    private void loadBrightnessMapFromConfigXml() {
        // Use the config.xml mapping
        final Resources res = mContext.getResources();
        final float[] sysNits = BrightnessMappingStrategy.getFloatArray(res.obtainTypedArray(
                com.android.internal.R.array.config_screenBrightnessNits));
        final int[] sysBrightness = res.getIntArray(
                com.android.internal.R.array.config_screenBrightnessBacklight);
        final float[] sysBrightnessFloat = new float[sysBrightness.length];

        for (int i = 0; i < sysBrightness.length; i++) {
            sysBrightnessFloat[i] = BrightnessSynchronizer.brightnessIntToFloat(
                    sysBrightness[i]);
        }

        // These arrays are allowed to be empty, we set null values so that
        // BrightnessMappingStrategy will create a SimpleMappingStrategy instead.
        if (sysBrightnessFloat.length == 0 || sysNits.length == 0) {
            setSimpleMappingStrategyValues();
            return;
        }

        mRawNits = sysNits;
        mRawBacklight = sysBrightnessFloat;
        constrainNitsAndBacklightArrays();
    }

    private void setSimpleMappingStrategyValues() {
        // No translation from backlight to brightness should occur if we are using a
        // SimpleMappingStrategy (ie they should be the same) so the splines are
        // set to be linear, between 0.0 and 1.0
        mNits = null;
        mBacklight = null;
        float[] simpleMappingStrategyArray = new float[]{0.0f, 1.0f};
        mBrightnessToBacklightSpline = Spline.createSpline(simpleMappingStrategyArray,
                simpleMappingStrategyArray);
        mBacklightToBrightnessSpline = Spline.createSpline(simpleMappingStrategyArray,
                simpleMappingStrategyArray);
    }

    /**
     * Change the nits and backlight arrays, so that they cover only the allowed backlight values
     * Use the brightness minimum and maximum values to clamp these arrays.
     */
    private void constrainNitsAndBacklightArrays() {
        if (mRawBacklight[0] > mBacklightMinimum
                || mRawBacklight[mRawBacklight.length - 1] < mBacklightMaximum
                || mBacklightMinimum > mBacklightMaximum) {
            throw new IllegalStateException("Min or max values are invalid"
                    + "; raw min=" + mRawBacklight[0]
                    + "; raw max=" + mRawBacklight[mRawBacklight.length - 1]
                    + "; backlight min=" + mBacklightMinimum
                    + "; backlight max=" + mBacklightMaximum);
        }

        float[] newNits = new float[mRawBacklight.length];
        float[] newBacklight = new float[mRawBacklight.length];
        // Find the starting index of the clamped arrays. This may be less than the min so
        // we'll need to clamp this value still when actually doing the remapping.
        int newStart = 0;
        for (int i = 0; i < mRawBacklight.length - 1; i++) {
            if (mRawBacklight[i + 1] > mBacklightMinimum) {
                newStart = i;
                break;
            }
        }

        boolean isLastValue = false;
        int newIndex = 0;
        for (int i = newStart; i < mRawBacklight.length && !isLastValue; i++) {
            newIndex = i - newStart;
            final float newBacklightVal;
            final float newNitsVal;
            isLastValue = mRawBacklight[i] >= mBacklightMaximum
                    || i >= mRawBacklight.length - 1;
            // Clamp beginning and end to valid backlight values.
            if (newIndex == 0) {
                newBacklightVal = MathUtils.max(mRawBacklight[i], mBacklightMinimum);
                newNitsVal = rawBacklightToNits(i, newBacklightVal);
            } else if (isLastValue) {
                newBacklightVal = MathUtils.min(mRawBacklight[i], mBacklightMaximum);
                newNitsVal = rawBacklightToNits(i - 1, newBacklightVal);
            } else {
                newBacklightVal = mRawBacklight[i];
                newNitsVal = mRawNits[i];
            }
            newBacklight[newIndex] = newBacklightVal;
            newNits[newIndex] = newNitsVal;
        }
        mBacklight = Arrays.copyOf(newBacklight, newIndex + 1);
        mNits = Arrays.copyOf(newNits, newIndex + 1);
        createBacklightConversionSplines();
    }

    private float rawBacklightToNits(int i, float backlight) {
        return MathUtils.map(mRawBacklight[i], mRawBacklight[i + 1],
                mRawNits[i], mRawNits[i + 1], backlight);
    }

    // This method creates a brightness spline that is of equal length with proportional increments
    // to the backlight spline. The values of this array range from 0.0f to 1.0f instead of the
    // potential constrained range that the backlight array covers
    // These splines are used to convert from the system brightness value to the HAL backlight
    // value
    private void createBacklightConversionSplines() {
        mBrightness = new float[mBacklight.length];
        for (int i = 0; i < mBrightness.length; i++) {
            mBrightness[i] = MathUtils.map(mBacklight[0],
                    mBacklight[mBacklight.length - 1],
                    PowerManager.BRIGHTNESS_MIN, PowerManager.BRIGHTNESS_MAX, mBacklight[i]);
        }
        mBrightnessToBacklightSpline = mInterpolationType == INTERPOLATION_LINEAR
                ? Spline.createLinearSpline(mBrightness, mBacklight)
                : Spline.createSpline(mBrightness, mBacklight);
        mBacklightToBrightnessSpline = mInterpolationType == INTERPOLATION_LINEAR
                ? Spline.createLinearSpline(mBacklight, mBrightness)
                : Spline.createSpline(mBacklight, mBrightness);
        mBacklightToNitsSpline = mInterpolationType == INTERPOLATION_LINEAR
                ? Spline.createLinearSpline(mBacklight, mNits)
                : Spline.createSpline(mBacklight, mNits);
        mNitsToBacklightSpline = mInterpolationType == INTERPOLATION_LINEAR
                ? Spline.createLinearSpline(mNits, mBacklight)
                : Spline.createSpline(mNits, mBacklight);
    }

    private void loadQuirks(DisplayConfiguration config) {
        final DisplayQuirks quirks = config.getQuirks();
        if (quirks != null) {
            mQuirks = new ArrayList<>(quirks.getQuirk());
        }
    }

    private void loadHighBrightnessModeData(DisplayConfiguration config) {
        final HighBrightnessMode hbm = config.getHighBrightnessMode();
        if (hbm != null) {
            mIsHighBrightnessModeEnabled = hbm.getEnabled();
            mHbmData = new HighBrightnessModeData();
            mHbmData.minimumLux = hbm.getMinimumLux_all().floatValue();
            float transitionPointBacklightScale = hbm.getTransitionPoint_all().floatValue();
            if (transitionPointBacklightScale >= mBacklightMaximum) {
                throw new IllegalArgumentException("HBM transition point invalid. "
                        + mHbmData.transitionPoint + " is not less than "
                        + mBacklightMaximum);
            }
            mHbmData.transitionPoint =
                    mBacklightToBrightnessSpline.interpolate(transitionPointBacklightScale);
            final HbmTiming hbmTiming = hbm.getTiming_all();
            mHbmData.timeWindowMillis = hbmTiming.getTimeWindowSecs_all().longValue() * 1000;
            mHbmData.timeMaxMillis = hbmTiming.getTimeMaxSecs_all().longValue() * 1000;
            mHbmData.timeMinMillis = hbmTiming.getTimeMinSecs_all().longValue() * 1000;
            mHbmData.thermalStatusLimit = convertThermalStatus(hbm.getThermalStatusLimit_all());
            mHbmData.allowInLowPowerMode = hbm.getAllowInLowPowerMode_all();
            final RefreshRateRange rr = hbm.getRefreshRate_all();
            if (rr != null) {
                final float min = rr.getMinimum().floatValue();
                final float max = rr.getMaximum().floatValue();
                mRefreshRateLimitations.add(new RefreshRateLimitation(
                        DisplayManagerInternal.REFRESH_RATE_LIMIT_HIGH_BRIGHTNESS_MODE, min, max));
            }
            BigDecimal minHdrPctOfScreen = hbm.getMinimumHdrPercentOfScreen_all();
            if (minHdrPctOfScreen != null) {
                mHbmData.minimumHdrPercentOfScreen = minHdrPctOfScreen.floatValue();
                if (mHbmData.minimumHdrPercentOfScreen > 1
                        || mHbmData.minimumHdrPercentOfScreen < 0) {
                    Slog.w(TAG, "Invalid minimum HDR percent of screen: "
                            + String.valueOf(mHbmData.minimumHdrPercentOfScreen));
                    mHbmData.minimumHdrPercentOfScreen = HDR_PERCENT_OF_SCREEN_REQUIRED_DEFAULT;
                }
            } else {
                mHbmData.minimumHdrPercentOfScreen = HDR_PERCENT_OF_SCREEN_REQUIRED_DEFAULT;
            }

            mSdrToHdrRatioSpline = loadSdrHdrRatioMap(hbm);
        }
    }

    private void loadBrightnessRamps(DisplayConfiguration config) {
        // Priority 1: Value in the display device config (float)
        // Priority 2: Value in the config.xml (int)
        final BigDecimal fastDownDecimal = config.getScreenBrightnessRampFastDecrease();
        final BigDecimal fastUpDecimal = config.getScreenBrightnessRampFastIncrease();
        final BigDecimal slowDownDecimal = config.getScreenBrightnessRampSlowDecrease();
        final BigDecimal slowUpDecimal = config.getScreenBrightnessRampSlowIncrease();

        if (fastDownDecimal != null && fastUpDecimal != null && slowDownDecimal != null
                && slowUpDecimal != null) {
            mBrightnessRampFastDecrease = fastDownDecimal.floatValue();
            mBrightnessRampFastIncrease = fastUpDecimal.floatValue();
            mBrightnessRampSlowDecrease = slowDownDecimal.floatValue();
            mBrightnessRampSlowIncrease = slowUpDecimal.floatValue();
        } else {
            if (fastDownDecimal != null || fastUpDecimal != null || slowDownDecimal != null
                    || slowUpDecimal != null) {
                Slog.w(TAG, "Per display brightness ramp values ignored because not all "
                        + "values are present in display device config");
            }
            loadBrightnessRampsFromConfigXml();
        }

        final BigInteger increaseMax = config.getScreenBrightnessRampIncreaseMaxMillis();
        if (increaseMax != null) {
            mBrightnessRampIncreaseMaxMillis = increaseMax.intValue();
        }
        final BigInteger decreaseMax = config.getScreenBrightnessRampDecreaseMaxMillis();
        if (decreaseMax != null) {
            mBrightnessRampDecreaseMaxMillis = decreaseMax.intValue();
        }
    }

    private void loadBrightnessRampsFromConfigXml() {
        mBrightnessRampFastIncrease = BrightnessSynchronizer.brightnessIntToFloat(
                mContext.getResources().getInteger(R.integer.config_brightness_ramp_rate_fast));
        mBrightnessRampSlowIncrease = BrightnessSynchronizer.brightnessIntToFloat(
                mContext.getResources().getInteger(R.integer.config_brightness_ramp_rate_slow));
        // config.xml uses the same values for both increasing and decreasing brightness
        // transitions so we assign them to the same values here.
        mBrightnessRampFastDecrease = mBrightnessRampFastIncrease;
        mBrightnessRampSlowDecrease = mBrightnessRampSlowIncrease;
    }

    private void loadAmbientLightSensorFromConfigXml() {
        mAmbientLightSensor.name = "";
        mAmbientLightSensor.type = mContext.getResources().getString(
                com.android.internal.R.string.config_displayLightSensorType);
    }

    private void loadAutoBrightnessConfigsFromConfigXml() {
        loadAutoBrightnessDisplayBrightnessMapping(null /*AutoBrightnessConfig*/);
    }

    private void loadAmbientLightSensorFromDdc(DisplayConfiguration config) {
        final SensorDetails sensorDetails = config.getLightSensor();
        if (sensorDetails != null) {
            mAmbientLightSensor.type = sensorDetails.getType();
            mAmbientLightSensor.name = sensorDetails.getName();
            final RefreshRateRange rr = sensorDetails.getRefreshRate();
            if (rr != null) {
                mAmbientLightSensor.minRefreshRate = rr.getMinimum().floatValue();
                mAmbientLightSensor.maxRefreshRate = rr.getMaximum().floatValue();
            }
        } else {
            loadAmbientLightSensorFromConfigXml();
        }
    }

    private void setProxSensorUnspecified() {
        mProximitySensor.name = "";
        mProximitySensor.type = "";
    }

    private void loadProxSensorFromDdc(DisplayConfiguration config) {
        SensorDetails sensorDetails = config.getProxSensor();
        if (sensorDetails != null) {
            mProximitySensor.name = sensorDetails.getName();
            mProximitySensor.type = sensorDetails.getType();
            final RefreshRateRange rr = sensorDetails.getRefreshRate();
            if (rr != null) {
                mProximitySensor.minRefreshRate = rr.getMinimum().floatValue();
                mProximitySensor.maxRefreshRate = rr.getMaximum().floatValue();
            }
        } else {
            setProxSensorUnspecified();
        }
    }

    private void loadBrightnessChangeThresholdsFromXml() {
        loadBrightnessChangeThresholds(/* config= */ null);
    }

    private void loadBrightnessChangeThresholds(DisplayConfiguration config) {
        loadDisplayBrightnessThresholds(config);
        loadAmbientBrightnessThresholds(config);
        loadDisplayBrightnessThresholdsIdle(config);
        loadAmbientBrightnessThresholdsIdle(config);
    }

    private void loadDisplayBrightnessThresholds(DisplayConfiguration config) {
        BrightnessThresholds brighteningScreen = null;
        BrightnessThresholds darkeningScreen = null;
        if (config != null && config.getDisplayBrightnessChangeThresholds() != null) {
            brighteningScreen =
                    config.getDisplayBrightnessChangeThresholds().getBrighteningThresholds();
            darkeningScreen =
                    config.getDisplayBrightnessChangeThresholds().getDarkeningThresholds();

        }

        // Screen bright/darkening threshold levels for active mode
        Pair<float[], float[]> screenBrighteningPair = getBrightnessLevelAndPercentage(
                brighteningScreen,
                com.android.internal.R.array.config_screenThresholdLevels,
                com.android.internal.R.array.config_screenBrighteningThresholds,
                DEFAULT_SCREEN_THRESHOLD_LEVELS, DEFAULT_SCREEN_BRIGHTENING_THRESHOLDS,
                /* potentialOldBrightnessScale= */ true);

        mScreenBrighteningLevels = screenBrighteningPair.first;
        mScreenBrighteningPercentages = screenBrighteningPair.second;

        Pair<float[], float[]> screenDarkeningPair = getBrightnessLevelAndPercentage(
                darkeningScreen,
                com.android.internal.R.array.config_screenThresholdLevels,
                com.android.internal.R.array.config_screenDarkeningThresholds,
                DEFAULT_SCREEN_THRESHOLD_LEVELS, DEFAULT_SCREEN_DARKENING_THRESHOLDS,
                /* potentialOldBrightnessScale= */ true);
        mScreenDarkeningLevels = screenDarkeningPair.first;
        mScreenDarkeningPercentages = screenDarkeningPair.second;

        // Screen bright/darkening threshold minimums for active mode
        if (brighteningScreen != null && brighteningScreen.getMinimum() != null) {
            mScreenBrighteningMinThreshold = brighteningScreen.getMinimum().floatValue();
        }
        if (darkeningScreen != null && darkeningScreen.getMinimum() != null) {
            mScreenDarkeningMinThreshold = darkeningScreen.getMinimum().floatValue();
        }
    }

    private void loadAmbientBrightnessThresholds(DisplayConfiguration config) {
        // Ambient Brightness Threshold Levels
        BrightnessThresholds brighteningAmbientLux = null;
        BrightnessThresholds darkeningAmbientLux = null;
        if (config != null && config.getAmbientBrightnessChangeThresholds() != null) {
            brighteningAmbientLux =
                    config.getAmbientBrightnessChangeThresholds().getBrighteningThresholds();
            darkeningAmbientLux =
                    config.getAmbientBrightnessChangeThresholds().getDarkeningThresholds();
        }

        // Ambient bright/darkening threshold levels for active mode
        Pair<float[], float[]> ambientBrighteningPair = getBrightnessLevelAndPercentage(
                brighteningAmbientLux,
                com.android.internal.R.array.config_ambientThresholdLevels,
                com.android.internal.R.array.config_ambientBrighteningThresholds,
                DEFAULT_AMBIENT_THRESHOLD_LEVELS, DEFAULT_AMBIENT_BRIGHTENING_THRESHOLDS);
        mAmbientBrighteningLevels = ambientBrighteningPair.first;
        mAmbientBrighteningPercentages = ambientBrighteningPair.second;

        Pair<float[], float[]> ambientDarkeningPair = getBrightnessLevelAndPercentage(
                darkeningAmbientLux,
                com.android.internal.R.array.config_ambientThresholdLevels,
                com.android.internal.R.array.config_ambientDarkeningThresholds,
                DEFAULT_AMBIENT_THRESHOLD_LEVELS, DEFAULT_AMBIENT_DARKENING_THRESHOLDS);
        mAmbientDarkeningLevels = ambientDarkeningPair.first;
        mAmbientDarkeningPercentages = ambientDarkeningPair.second;

        // Ambient bright/darkening threshold minimums for active/idle mode
        if (brighteningAmbientLux != null && brighteningAmbientLux.getMinimum() != null) {
            mAmbientLuxBrighteningMinThreshold =
                    brighteningAmbientLux.getMinimum().floatValue();
        }

        if (darkeningAmbientLux != null && darkeningAmbientLux.getMinimum() != null) {
            mAmbientLuxDarkeningMinThreshold = darkeningAmbientLux.getMinimum().floatValue();
        }
    }

    private void loadDisplayBrightnessThresholdsIdle(DisplayConfiguration config) {
        BrightnessThresholds brighteningScreenIdle = null;
        BrightnessThresholds darkeningScreenIdle = null;
        if (config != null && config.getDisplayBrightnessChangeThresholdsIdle() != null) {
            brighteningScreenIdle =
                    config.getDisplayBrightnessChangeThresholdsIdle().getBrighteningThresholds();
            darkeningScreenIdle =
                    config.getDisplayBrightnessChangeThresholdsIdle().getDarkeningThresholds();
        }

        Pair<float[], float[]> screenBrighteningPair = getBrightnessLevelAndPercentage(
                brighteningScreenIdle,
                com.android.internal.R.array.config_screenThresholdLevels,
                com.android.internal.R.array.config_screenBrighteningThresholds,
                DEFAULT_SCREEN_THRESHOLD_LEVELS, DEFAULT_SCREEN_BRIGHTENING_THRESHOLDS,
                /* potentialOldBrightnessScale= */ true);
        mScreenBrighteningLevelsIdle = screenBrighteningPair.first;
        mScreenBrighteningPercentagesIdle = screenBrighteningPair.second;

        Pair<float[], float[]> screenDarkeningPair = getBrightnessLevelAndPercentage(
                darkeningScreenIdle,
                com.android.internal.R.array.config_screenThresholdLevels,
                com.android.internal.R.array.config_screenDarkeningThresholds,
                DEFAULT_SCREEN_THRESHOLD_LEVELS, DEFAULT_SCREEN_DARKENING_THRESHOLDS,
                /* potentialOldBrightnessScale= */ true);
        mScreenDarkeningLevelsIdle = screenDarkeningPair.first;
        mScreenDarkeningPercentagesIdle = screenDarkeningPair.second;

        if (brighteningScreenIdle != null
                && brighteningScreenIdle.getMinimum() != null) {
            mScreenBrighteningMinThresholdIdle =
                    brighteningScreenIdle.getMinimum().floatValue();
        }
        if (darkeningScreenIdle != null && darkeningScreenIdle.getMinimum() != null) {
            mScreenDarkeningMinThresholdIdle =
                    darkeningScreenIdle.getMinimum().floatValue();
        }
    }

    private void loadAmbientBrightnessThresholdsIdle(DisplayConfiguration config) {
        BrightnessThresholds brighteningAmbientLuxIdle = null;
        BrightnessThresholds darkeningAmbientLuxIdle = null;
        if (config != null && config.getAmbientBrightnessChangeThresholdsIdle() != null) {
            brighteningAmbientLuxIdle =
                    config.getAmbientBrightnessChangeThresholdsIdle().getBrighteningThresholds();
            darkeningAmbientLuxIdle =
                    config.getAmbientBrightnessChangeThresholdsIdle().getDarkeningThresholds();
        }

        Pair<float[], float[]> ambientBrighteningPair = getBrightnessLevelAndPercentage(
                brighteningAmbientLuxIdle,
                com.android.internal.R.array.config_ambientThresholdLevels,
                com.android.internal.R.array.config_ambientBrighteningThresholds,
                DEFAULT_AMBIENT_THRESHOLD_LEVELS, DEFAULT_AMBIENT_BRIGHTENING_THRESHOLDS);
        mAmbientBrighteningLevelsIdle = ambientBrighteningPair.first;
        mAmbientBrighteningPercentagesIdle = ambientBrighteningPair.second;

        Pair<float[], float[]> ambientDarkeningPair = getBrightnessLevelAndPercentage(
                darkeningAmbientLuxIdle,
                com.android.internal.R.array.config_ambientThresholdLevels,
                com.android.internal.R.array.config_ambientDarkeningThresholds,
                DEFAULT_AMBIENT_THRESHOLD_LEVELS, DEFAULT_AMBIENT_DARKENING_THRESHOLDS);
        mAmbientDarkeningLevelsIdle = ambientDarkeningPair.first;
        mAmbientDarkeningPercentagesIdle = ambientDarkeningPair.second;

        if (brighteningAmbientLuxIdle != null
                && brighteningAmbientLuxIdle.getMinimum() != null) {
            mAmbientLuxBrighteningMinThresholdIdle =
                    brighteningAmbientLuxIdle.getMinimum().floatValue();
        }

        if (darkeningAmbientLuxIdle != null && darkeningAmbientLuxIdle.getMinimum() != null) {
            mAmbientLuxDarkeningMinThresholdIdle =
                    darkeningAmbientLuxIdle.getMinimum().floatValue();
        }
    }

    private Pair<float[], float[]> getBrightnessLevelAndPercentage(BrightnessThresholds thresholds,
            int configFallbackThreshold, int configFallbackPercentage, float[] defaultLevels,
            float[] defaultPercentage) {
        return getBrightnessLevelAndPercentage(thresholds, configFallbackThreshold,
                configFallbackPercentage, defaultLevels, defaultPercentage, false);
    }

    // Returns two float arrays, one of the brightness levels and one of the corresponding threshold
    // percentages for brightness levels at or above the lux value.
    // Historically, config.xml would have an array for brightness levels that was 1 shorter than
    // the levels array. Now we prepend a 0 to this array so they can be treated the same in the
    // rest of the framework. Values were also defined in different units (permille vs percent).
    private Pair<float[], float[]> getBrightnessLevelAndPercentage(BrightnessThresholds thresholds,
            int configFallbackThreshold, int configFallbackPermille,
            float[] defaultLevels, float[] defaultPercentage,
            boolean potentialOldBrightnessScale) {
        if (thresholds != null
                && thresholds.getBrightnessThresholdPoints() != null
                && thresholds.getBrightnessThresholdPoints()
                        .getBrightnessThresholdPoint().size() != 0) {

            // The level and percentages arrays are equal length in the ddc (new system)
            List<ThresholdPoint> points =
                    thresholds.getBrightnessThresholdPoints().getBrightnessThresholdPoint();
            final int size = points.size();

            float[] thresholdLevels = new float[size];
            float[] thresholdPercentages = new float[size];

            int i = 0;
            for (ThresholdPoint point : points) {
                thresholdLevels[i] = point.getThreshold().floatValue();
                thresholdPercentages[i] = point.getPercentage().floatValue();
                i++;
            }
            return new Pair<>(thresholdLevels, thresholdPercentages);
        } else {
            // The level and percentages arrays are unequal length in config.xml (old system)
            // We prefix the array with a 0 value to ensure they can be handled consistently
            // with the new system.

            // Load levels array
            int[] configThresholdArray = mContext.getResources().getIntArray(
                    configFallbackThreshold);
            int configThresholdsSize;
            if (configThresholdArray == null || configThresholdArray.length == 0) {
                configThresholdsSize = 1;
            } else {
                configThresholdsSize = configThresholdArray.length + 1;
            }


            // Load percentage array
            int[] configPermille = mContext.getResources().getIntArray(
                    configFallbackPermille);

            // Ensure lengths match up
            boolean emptyArray = configPermille == null || configPermille.length == 0;
            if (emptyArray && configThresholdsSize == 1) {
                return new Pair<>(defaultLevels, defaultPercentage);
            }
            if (emptyArray || configPermille.length != configThresholdsSize) {
                throw new IllegalArgumentException(
                        "Brightness threshold arrays do not align in length");
            }

            // Calculate levels array
            float[] configThresholdWithZeroPrefixed = new float[configThresholdsSize];
            // Start at 1, so that 0 index value is 0.0f (default)
            for (int i = 1; i < configThresholdsSize; i++) {
                configThresholdWithZeroPrefixed[i] = (float) configThresholdArray[i - 1];
            }
            if (potentialOldBrightnessScale) {
                configThresholdWithZeroPrefixed =
                        constraintInRangeIfNeeded(configThresholdWithZeroPrefixed);
            }

            // Calculate percentages array
            float[] configPercentage = new float[configThresholdsSize];
            for (int i = 0; i < configPermille.length; i++) {
                configPercentage[i] = configPermille[i] / 10.0f;
            }            return new Pair<>(configThresholdWithZeroPrefixed, configPercentage);
        }
    }

    /**
     * This check is due to historical reasons, where screen thresholdLevels used to be
     * integer values in the range of [0-255], but then was changed to be float values from [0,1].
     * To accommodate both the possibilities, we first check if all the thresholdLevels are in
     * [0,1], and if not, we divide all the levels with 255 to bring them down to the same scale.
     */
    private float[] constraintInRangeIfNeeded(float[] thresholdLevels) {
        if (isAllInRange(thresholdLevels, /* minValueInclusive= */ 0.0f,
                /* maxValueInclusive= */ 1.0f)) {
            return thresholdLevels;
        }

        Slog.w(TAG, "Detected screen thresholdLevels on a deprecated brightness scale");
        float[] thresholdLevelsScaled = new float[thresholdLevels.length];
        for (int index = 0; thresholdLevels.length > index; ++index) {
            thresholdLevelsScaled[index] = thresholdLevels[index] / 255.0f;
        }
        return thresholdLevelsScaled;
    }

    private boolean isAllInRange(float[] configArray, float minValueInclusive,
            float maxValueInclusive) {
        for (float v : configArray) {
            if (v < minValueInclusive || v > maxValueInclusive) {
                return false;
            }
        }
        return true;
    }

    private boolean thermalStatusIsValid(ThermalStatus value) {
        if (value == null) {
            return false;
        }

        switch (value) {
            case none:
            case light:
            case moderate:
            case severe:
            case critical:
            case emergency:
            case shutdown:
                return true;
            default:
                return false;
        }
    }

    private @PowerManager.ThermalStatus int convertThermalStatus(ThermalStatus value) {
        if (value == null) {
            return PowerManager.THERMAL_STATUS_NONE;
        }
        switch (value) {
            case none:
                return PowerManager.THERMAL_STATUS_NONE;
            case light:
                return PowerManager.THERMAL_STATUS_LIGHT;
            case moderate:
                return PowerManager.THERMAL_STATUS_MODERATE;
            case severe:
                return PowerManager.THERMAL_STATUS_SEVERE;
            case critical:
                return PowerManager.THERMAL_STATUS_CRITICAL;
            case emergency:
                return PowerManager.THERMAL_STATUS_EMERGENCY;
            case shutdown:
                return PowerManager.THERMAL_STATUS_SHUTDOWN;
            default:
                Slog.wtf(TAG, "Unexpected Thermal Status: " + value);
                return PowerManager.THERMAL_STATUS_NONE;
        }
    }

    private int convertInterpolationType(String value) {
        if (TextUtils.isEmpty(value)) {
            return INTERPOLATION_DEFAULT;
        }

        if ("linear".equals(value)) {
            return INTERPOLATION_LINEAR;
        }

        Slog.wtf(TAG, "Unexpected Interpolation Type: " + value);
        return INTERPOLATION_DEFAULT;
    }

    private void loadAmbientHorizonFromDdc(DisplayConfiguration config) {
        final BigInteger configLongHorizon = config.getAmbientLightHorizonLong();
        if (configLongHorizon != null) {
            mAmbientHorizonLong = configLongHorizon.intValue();
        }
        final BigInteger configShortHorizon = config.getAmbientLightHorizonShort();
        if (configShortHorizon != null) {
            mAmbientHorizonShort = configShortHorizon.intValue();
        }
    }

    /**
     * Extracts a float array from the specified {@link TypedArray}.
     *
     * @param array The array to convert.
     * @return the given array as a float array.
     */
    public static float[] getFloatArray(TypedArray array, float defaultValue) {
        final int n = array.length();
        float[] vals = new float[n];
        for (int i = 0; i < n; i++) {
            vals[i] = array.getFloat(i, defaultValue);
        }
        array.recycle();
        return vals;
    }

    private static float[] getLuxLevels(int[] lux) {
        // The first control point is implicit and always at 0 lux.
        float[] levels = new float[lux.length + 1];
        for (int i = 0; i < lux.length; i++) {
            levels[i + 1] = (float) lux[i];
        }
        return levels;
    }

    static class SensorData {
        public String type;
        public String name;
        public float minRefreshRate = 0.0f;
        public float maxRefreshRate = Float.POSITIVE_INFINITY;

        @Override
        public String toString() {
            return "Sensor{"
                    + "type: " + type
                    + ", name: " + name
                    + ", refreshRateRange: [" + minRefreshRate + ", " + maxRefreshRate + "]"
                    + "} ";
        }

        /**
         * @return True if the sensor matches both the specified name and type, or one if only one
         * is specified (not-empty). Always returns false if both parameters are null or empty.
         */
        public boolean matches(String sensorName, String sensorType) {
            final boolean isNameSpecified = !TextUtils.isEmpty(sensorName);
            final boolean isTypeSpecified = !TextUtils.isEmpty(sensorType);
            return (isNameSpecified || isTypeSpecified)
                    && (!isNameSpecified || sensorName.equals(name))
                    && (!isTypeSpecified || sensorType.equals(type));
        }
    }

    /**
     * Container for high brightness mode configuration data.
     */
    static class HighBrightnessModeData {
        /** Minimum lux needed to enter high brightness mode */
        public float minimumLux;

        /** Brightness level at which we transition from normal to high-brightness. */
        public float transitionPoint;

        /** Enable HBM only if the thermal status is not higher than this. */
        public @PowerManager.ThermalStatus int thermalStatusLimit;

        /** Whether HBM is allowed when {@code Settings.Global.LOW_POWER_MODE} is active. */
        public boolean allowInLowPowerMode;

        /** Time window for HBM. */
        public long timeWindowMillis;

        /** Maximum time HBM is allowed to be during in a {@code timeWindowMillis}. */
        public long timeMaxMillis;

        /** Minimum time that HBM can be on before being enabled. */
        public long timeMinMillis;

        /** Minimum HDR video size to enter high brightness mode */
        public float minimumHdrPercentOfScreen;

        HighBrightnessModeData() {}

        HighBrightnessModeData(float minimumLux, float transitionPoint, long timeWindowMillis,
                long timeMaxMillis, long timeMinMillis,
                @PowerManager.ThermalStatus int thermalStatusLimit, boolean allowInLowPowerMode,
                float minimumHdrPercentOfScreen) {
            this.minimumLux = minimumLux;
            this.transitionPoint = transitionPoint;
            this.timeWindowMillis = timeWindowMillis;
            this.timeMaxMillis = timeMaxMillis;
            this.timeMinMillis = timeMinMillis;
            this.thermalStatusLimit = thermalStatusLimit;
            this.allowInLowPowerMode = allowInLowPowerMode;
            this.minimumHdrPercentOfScreen = minimumHdrPercentOfScreen;
        }

        /**
         * Copies the HBM data to the specified parameter instance.
         * @param other the instance to copy data to.
         */
        public void copyTo(@NonNull HighBrightnessModeData other) {
            other.minimumLux = minimumLux;
            other.timeWindowMillis = timeWindowMillis;
            other.timeMaxMillis = timeMaxMillis;
            other.timeMinMillis = timeMinMillis;
            other.transitionPoint = transitionPoint;
            other.thermalStatusLimit = thermalStatusLimit;
            other.allowInLowPowerMode = allowInLowPowerMode;
            other.minimumHdrPercentOfScreen = minimumHdrPercentOfScreen;
        }

        @Override
        public String toString() {
            return "HBM{"
                    + "minLux: " + minimumLux
                    + ", transition: " + transitionPoint
                    + ", timeWindow: " + timeWindowMillis + "ms"
                    + ", timeMax: " + timeMaxMillis + "ms"
                    + ", timeMin: " + timeMinMillis + "ms"
                    + ", thermalStatusLimit: " + thermalStatusLimit
                    + ", allowInLowPowerMode: " + allowInLowPowerMode
                    + ", minimumHdrPercentOfScreen: " + minimumHdrPercentOfScreen
                    + "} ";
        }
    }

    /**
     * Container for brightness throttling data.
     */
    public static class BrightnessThrottlingData {
        public List<ThrottlingLevel> throttlingLevels;

        static class ThrottlingLevel {
            public @PowerManager.ThermalStatus int thermalStatus;
            public float brightness;

            ThrottlingLevel(@PowerManager.ThermalStatus int thermalStatus, float brightness) {
                this.thermalStatus = thermalStatus;
                this.brightness = brightness;
            }

            @Override
            public String toString() {
                return "[" + thermalStatus + "," + brightness + "]";
            }

            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof ThrottlingLevel)) {
                    return false;
                }
                ThrottlingLevel otherThrottlingLevel = (ThrottlingLevel) obj;

                return otherThrottlingLevel.thermalStatus == this.thermalStatus
                        && otherThrottlingLevel.brightness == this.brightness;
            }

            @Override
            public int hashCode() {
                int result = 1;
                result = 31 * result + thermalStatus;
                result = 31 * result + Float.hashCode(brightness);
                return result;
            }
        }


        /**
         * Creates multiple teperature based throttling levels of brightness
         */
        public static BrightnessThrottlingData create(List<ThrottlingLevel> throttlingLevels) {
            if (throttlingLevels == null || throttlingLevels.size() == 0) {
                Slog.e(TAG, "BrightnessThrottlingData received null or empty throttling levels");
                return null;
            }

            ThrottlingLevel prevLevel = throttlingLevels.get(0);
            final int numLevels = throttlingLevels.size();
            for (int i = 1; i < numLevels; i++) {
                ThrottlingLevel thisLevel = throttlingLevels.get(i);

                if (thisLevel.thermalStatus <= prevLevel.thermalStatus) {
                    Slog.e(TAG, "brightnessThrottlingMap must be strictly increasing, ignoring "
                            + "configuration. ThermalStatus " + thisLevel.thermalStatus + " <= "
                            + prevLevel.thermalStatus);
                    return null;
                }

                if (thisLevel.brightness >= prevLevel.brightness) {
                    Slog.e(TAG, "brightnessThrottlingMap must be strictly decreasing, ignoring "
                            + "configuration. Brightness " + thisLevel.brightness + " >= "
                            + thisLevel.brightness);
                    return null;
                }

                prevLevel = thisLevel;
            }

            for (ThrottlingLevel level : throttlingLevels) {
                // Non-negative brightness values are enforced by device config schema
                if (level.brightness > PowerManager.BRIGHTNESS_MAX) {
                    Slog.e(TAG, "brightnessThrottlingMap contains a brightness value exceeding "
                            + "system max. Brightness " + level.brightness + " > "
                            + PowerManager.BRIGHTNESS_MAX);
                    return null;
                }
            }

            return new BrightnessThrottlingData(throttlingLevels);
        }

        static public BrightnessThrottlingData create(BrightnessThrottlingData other) {
            if (other == null) {
                return null;
            }

            return BrightnessThrottlingData.create(other.throttlingLevels);
        }


        @Override
        public String toString() {
            return "BrightnessThrottlingData{"
                    + "throttlingLevels:" + throttlingLevels
                    + "} ";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof BrightnessThrottlingData)) {
                return false;
            }

            BrightnessThrottlingData otherBrightnessThrottlingData = (BrightnessThrottlingData) obj;
            return throttlingLevels.equals(otherBrightnessThrottlingData.throttlingLevels);
        }

        @Override
        public int hashCode() {
            return throttlingLevels.hashCode();
        }

        private BrightnessThrottlingData(List<ThrottlingLevel> inLevels) {
            throttlingLevels = new ArrayList<>(inLevels.size());
            for (ThrottlingLevel level : inLevels) {
                throttlingLevels.add(new ThrottlingLevel(level.thermalStatus, level.brightness));
            }
        }
    }
}
