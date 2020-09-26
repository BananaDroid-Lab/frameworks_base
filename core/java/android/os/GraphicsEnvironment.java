/*
 * Copyright 2016 The Android Open Source Project
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

package android.os;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import dalvik.system.VMRuntime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GraphicsEnvironment sets up necessary properties for the graphics environment of the
 * application process.
 * GraphicsEnvironment uses a bunch of settings global variables to determine the setup,
 * the change of settings global variables will only take effect before setup() is called,
 * and any subsequent change will not impact the current running processes.
 *
 * @hide
 */
public class GraphicsEnvironment {

    private static final GraphicsEnvironment sInstance = new GraphicsEnvironment();

    /**
     * Returns the shared {@link GraphicsEnvironment} instance.
     */
    public static GraphicsEnvironment getInstance() {
        return sInstance;
    }

    private static final boolean DEBUG = false;
    private static final String TAG = "GraphicsEnvironment";
    private static final String SYSTEM_DRIVER_NAME = "system";
    private static final String SYSTEM_DRIVER_VERSION_NAME = "";
    private static final long SYSTEM_DRIVER_VERSION_CODE = 0;

    // System properties related to updatable graphics drivers.
    private static final String PROPERTY_GFX_DRIVER_PRODUCTION = "ro.gfx.driver.0";
    private static final String PROPERTY_GFX_DRIVER_PRERELEASE = "ro.gfx.driver.1";
    private static final String PROPERTY_GFX_DRIVER_BUILD_TIME = "ro.gfx.driver_build_time";

    // Metadata flags within the <application> tag in the AndroidManifest.xml file.
    private static final String METADATA_DRIVER_BUILD_TIME =
            "com.android.graphics.driver.build_time";
    private static final String METADATA_DEVELOPER_DRIVER_ENABLE =
            "com.android.graphics.developerdriver.enable";
    private static final String METADATA_INJECT_LAYERS_ENABLE =
            "com.android.graphics.injectLayers.enable";

    private static final String UPDATABLE_DRIVER_ALLOWLIST_ALL = "*";
    private static final String UPDATABLE_DRIVER_SPHAL_LIBRARIES_FILENAME = "sphal_libraries.txt";

    // ANGLE related properties.
    private static final String ANGLE_RULES_FILE = "a4a_rules.json";
    private static final String ANGLE_TEMP_RULES = "debug.angle.rules";
    private static final String ACTION_ANGLE_FOR_ANDROID = "android.app.action.ANGLE_FOR_ANDROID";
    private static final String ACTION_ANGLE_FOR_ANDROID_TOAST_MESSAGE =
            "android.app.action.ANGLE_FOR_ANDROID_TOAST_MESSAGE";
    private static final String INTENT_KEY_A4A_TOAST_MESSAGE = "A4A Toast Message";

    private static final int VULKAN_1_0 = 0x00400000;
    private static final int VULKAN_1_1 = 0x00401000;

    // Values for UPDATABLE_DRIVER_ALL_APPS
    // 0: Default (Invalid values fallback to default as well)
    // 1: All apps use updatable production driver
    // 2: All apps use updatable prerelease driver
    // 3: All apps use system graphics driver
    private static final int UPDATABLE_DRIVER_GLOBAL_OPT_IN_DEFAULT = 0;
    private static final int UPDATABLE_DRIVER_GLOBAL_OPT_IN_PRODUCTION_DRIVER = 1;
    private static final int UPDATABLE_DRIVER_GLOBAL_OPT_IN_PRERELEASE_DRIVER = 2;
    private static final int UPDATABLE_DRIVER_GLOBAL_OPT_IN_OFF = 3;

    // Values for GLOBAL_SETTINGS_ANGLE_GL_DRIVER_ALL_ANGLE
    private static final int ANGLE_GL_DRIVER_ALL_ANGLE_ON = 1;
    private static final int ANGLE_GL_DRIVER_ALL_ANGLE_OFF = 0;

    // Values for GLOBAL_SETTINGS_ANGLE_GL_DRIVER_SELECTION_VALUES
    private static final String ANGLE_GL_DRIVER_CHOICE_DEFAULT = "default";
    private static final String ANGLE_GL_DRIVER_CHOICE_ANGLE = "angle";
    private static final String ANGLE_GL_DRIVER_CHOICE_NATIVE = "native";

    private ClassLoader mClassLoader;
    private String mLibrarySearchPaths;
    private String mLibraryPermittedPaths;

    private int mAngleOptInIndex = -1;

    /**
     * Set up GraphicsEnvironment
     */
    public void setup(Context context, Bundle coreSettings) {
        final PackageManager pm = context.getPackageManager();
        final String packageName = context.getPackageName();
        final ApplicationInfo appInfoWithMetaData =
                getAppInfoWithMetadata(context, pm, packageName);

        Trace.traceBegin(Trace.TRACE_TAG_GRAPHICS, "setupGpuLayers");
        setupGpuLayers(context, coreSettings, pm, packageName, appInfoWithMetaData);
        Trace.traceEnd(Trace.TRACE_TAG_GRAPHICS);

        Trace.traceBegin(Trace.TRACE_TAG_GRAPHICS, "setupAngle");
        setupAngle(context, coreSettings, pm, packageName);
        Trace.traceEnd(Trace.TRACE_TAG_GRAPHICS);

        Trace.traceBegin(Trace.TRACE_TAG_GRAPHICS, "chooseDriver");
        if (!chooseDriver(context, coreSettings, pm, packageName, appInfoWithMetaData)) {
            setGpuStats(SYSTEM_DRIVER_NAME, SYSTEM_DRIVER_VERSION_NAME, SYSTEM_DRIVER_VERSION_CODE,
                    SystemProperties.getLong(PROPERTY_GFX_DRIVER_BUILD_TIME, 0), packageName,
                    getVulkanVersion(pm));
        }
        Trace.traceEnd(Trace.TRACE_TAG_GRAPHICS);
    }

    /**
     * Query to determine if ANGLE should be used
     */
    private boolean shouldUseAngle(Context context, Bundle coreSettings,
            String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            Log.v(TAG, "No package name specified, ANGLE should not be used");
            return false;
        }

        final String devOptIn = getDriverForPackage(context, coreSettings, packageName);
        Log.v(TAG, "ANGLE Developer option for '" + packageName + "' "
                + "set to: '" + devOptIn + "'");

        // We only want to use ANGLE if the app is in the allowlist, or the developer has
        // explicitly chosen something other than default driver.
        // The allowlist will be generated by the ANGLE APK at both boot time and
        // ANGLE update time. It will only include apps mentioned in the rules file.
        final boolean allowed = checkAngleAllowlist(context, coreSettings, packageName);
        final boolean requested = devOptIn.equals(ANGLE_GL_DRIVER_CHOICE_ANGLE);

        if (allowed) {
            Log.v(TAG, "ANGLE allowlist includes " + packageName);
        }
        if (requested) {
            Log.v(TAG, "ANGLE developer option for " + packageName + ": " + devOptIn);
        }

        return allowed || requested;
    }

    private int getVulkanVersion(PackageManager pm) {
        // PackageManager doesn't have an API to retrieve the version of a specific feature, and we
        // need to avoid retrieving all system features here and looping through them.
        if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, VULKAN_1_1)) {
            return VULKAN_1_1;
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, VULKAN_1_0)) {
            return VULKAN_1_0;
        }

        return 0;
    }

    /**
     * Check whether application is has set the manifest metadata for layer injection.
     */
    private boolean canInjectLayers(ApplicationInfo ai) {
        return (ai.metaData != null && ai.metaData.getBoolean(METADATA_INJECT_LAYERS_ENABLE)
                && setInjectLayersPrSetDumpable());
    }

    /**
     * Store the class loader for namespace lookup later.
     */
    public void setLayerPaths(ClassLoader classLoader,
                              String searchPaths,
                              String permittedPaths) {
        // We have to store these in the class because they are set up before we
        // have access to the Context to properly set up GraphicsEnvironment
        mClassLoader = classLoader;
        mLibrarySearchPaths = searchPaths;
        mLibraryPermittedPaths = permittedPaths;
    }

    /**
     * Returns the debug layer paths from settings.
     * Returns null if:
     *     1) The application process is not debuggable or layer injection metadata flag is not
     *        true; Or
     *     2) ENABLE_GPU_DEBUG_LAYERS is not true; Or
     *     3) Package name is not equal to GPU_DEBUG_APP.
     */
    public String getDebugLayerPathsFromSettings(
            Bundle coreSettings, IPackageManager pm, String packageName,
            ApplicationInfo ai) {
        if (!debugLayerEnabled(coreSettings, packageName, ai)) {
            return null;
        }
        Log.i(TAG, "GPU debug layers enabled for " + packageName);
        String debugLayerPaths = "";

        // Grab all debug layer apps and add to paths.
        final String gpuDebugLayerApps =
                coreSettings.getString(Settings.Global.GPU_DEBUG_LAYER_APP, "");
        if (!gpuDebugLayerApps.isEmpty()) {
            Log.i(TAG, "GPU debug layer apps: " + gpuDebugLayerApps);
            // If a colon is present, treat this as multiple apps, so Vulkan and GLES
            // layer apps can be provided at the same time.
            final String[] layerApps = gpuDebugLayerApps.split(":");
            for (int i = 0; i < layerApps.length; i++) {
                String paths = getDebugLayerAppPaths(pm, layerApps[i]);
                if (!paths.isEmpty()) {
                    // Append the path so files placed in the app's base directory will
                    // override the external path
                    debugLayerPaths += paths + File.pathSeparator;
                }
            }
        }
        return debugLayerPaths;
    }

    /**
     * Return the debug layer app's on-disk and in-APK lib directories
     */
    private String getDebugLayerAppPaths(IPackageManager pm, String packageName) {
        final ApplicationInfo appInfo;
        try {
            appInfo = pm.getApplicationInfo(packageName, PackageManager.MATCH_ALL,
                    UserHandle.myUserId());
        } catch (RemoteException e) {
            return "";
        }
        if (appInfo == null) {
            Log.w(TAG, "Debug layer app '" + packageName + "' not installed");
        }

        final String abi = chooseAbi(appInfo);
        final StringBuilder sb = new StringBuilder();
        sb.append(appInfo.nativeLibraryDir)
            .append(File.pathSeparator)
            .append(appInfo.sourceDir)
            .append("!/lib/")
            .append(abi);
        final String paths = sb.toString();
        if (DEBUG) Log.v(TAG, "Debug layer app libs: " + paths);

        return paths;
    }

    private boolean debugLayerEnabled(Bundle coreSettings, String packageName, ApplicationInfo ai) {
        // Only enable additional debug functionality if the following conditions are met:
        // 1. App is debuggable or device is rooted or layer injection metadata flag is true
        // 2. ENABLE_GPU_DEBUG_LAYERS is true
        // 3. Package name is equal to GPU_DEBUG_APP
        if (!isDebuggable() && !canInjectLayers(ai)) {
            return false;
        }
        final int enable = coreSettings.getInt(Settings.Global.ENABLE_GPU_DEBUG_LAYERS, 0);
        if (enable == 0) {
            return false;
        }
        final String gpuDebugApp = coreSettings.getString(Settings.Global.GPU_DEBUG_APP, "");
        if (packageName == null
                || (gpuDebugApp.isEmpty() || packageName.isEmpty())
                || !gpuDebugApp.equals(packageName)) {
            return false;
        }
        return true;
    }

    /**
     * Set up layer search paths for all apps
     */
    private void setupGpuLayers(
            Context context, Bundle coreSettings, PackageManager pm, String packageName,
            ApplicationInfo ai) {
        final boolean enabled = debugLayerEnabled(coreSettings, packageName, ai);
        String layerPaths = "";
        if (enabled) {
            layerPaths = mLibraryPermittedPaths;

            final String layers = coreSettings.getString(Settings.Global.GPU_DEBUG_LAYERS);
            Log.i(TAG, "Vulkan debug layer list: " + layers);
            if (layers != null && !layers.isEmpty()) {
                setDebugLayers(layers);
            }

            final String layersGLES =
                    coreSettings.getString(Settings.Global.GPU_DEBUG_LAYERS_GLES);
            Log.i(TAG, "GLES debug layer list: " + layersGLES);
            if (layersGLES != null && !layersGLES.isEmpty()) {
                setDebugLayersGLES(layersGLES);
            }
        }

        // Include the app's lib directory in all cases
        layerPaths += mLibrarySearchPaths;
        setLayerPaths(mClassLoader, layerPaths);
    }

    private static List<String> getGlobalSettingsString(ContentResolver contentResolver,
                                                        Bundle bundle,
                                                        String globalSetting) {
        final List<String> valueList;
        final String settingsValue;

        if (bundle != null) {
            settingsValue = bundle.getString(globalSetting);
        } else {
            settingsValue = Settings.Global.getString(contentResolver, globalSetting);
        }

        if (settingsValue != null) {
            valueList = new ArrayList<>(Arrays.asList(settingsValue.split(",")));
        } else {
            valueList = new ArrayList<>();
        }

        return valueList;
    }

    private static int getPackageIndex(String packageName, List<String> packages) {
        for (int idx = 0; idx < packages.size(); idx++) {
            if (packages.get(idx).equals(packageName)) {
                return idx;
            }
        }

        return -1;
    }

    private static ApplicationInfo getAppInfoWithMetadata(Context context,
                                                          PackageManager pm, String packageName) {
        ApplicationInfo ai;
        try {
            // Get the ApplicationInfo from PackageManager so that metadata fields present.
            ai = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            // Unlikely to fail for applications, but in case of failure, fall back to use the
            // ApplicationInfo from context directly.
            ai = context.getApplicationInfo();
        }
        return ai;
    }

    private String getDriverForPackage(Context context, Bundle bundle, String packageName) {
        final int allUseAngle;
        if (bundle != null) {
            allUseAngle =
                    bundle.getInt(Settings.Global.GLOBAL_SETTINGS_ANGLE_GL_DRIVER_ALL_ANGLE);
        } else {
            ContentResolver contentResolver = context.getContentResolver();
            allUseAngle = Settings.Global.getInt(contentResolver,
                    Settings.Global.GLOBAL_SETTINGS_ANGLE_GL_DRIVER_ALL_ANGLE,
                    ANGLE_GL_DRIVER_ALL_ANGLE_OFF);
        }
        if (allUseAngle == ANGLE_GL_DRIVER_ALL_ANGLE_ON) {
            Log.v(TAG, "Turn on ANGLE for all applications.");
            return ANGLE_GL_DRIVER_CHOICE_ANGLE;
        }

        // Make sure we have a good package name
        if (TextUtils.isEmpty(packageName)) {
            return ANGLE_GL_DRIVER_CHOICE_DEFAULT;
        }

        final ContentResolver contentResolver = context.getContentResolver();
        final List<String> optInPackages =
                getGlobalSettingsString(contentResolver, bundle,
                        Settings.Global.GLOBAL_SETTINGS_ANGLE_GL_DRIVER_SELECTION_PKGS);
        final List<String> optInValues =
                getGlobalSettingsString(contentResolver, bundle,
                        Settings.Global.GLOBAL_SETTINGS_ANGLE_GL_DRIVER_SELECTION_VALUES);

        // Make sure we have good settings to use
        if (optInPackages.size() != optInValues.size()) {
            Log.w(TAG,
                    "Global.Settings values are invalid: "
                        + "number of packages: "
                            + optInPackages.size() + ", "
                        + "number of values: "
                            + optInValues.size());
            return ANGLE_GL_DRIVER_CHOICE_DEFAULT;
        }

        final int pkgIndex = getPackageIndex(packageName, optInPackages);

        if (pkgIndex < 0) {
            return ANGLE_GL_DRIVER_CHOICE_DEFAULT;
        }
        mAngleOptInIndex = pkgIndex;

        return optInValues.get(pkgIndex);
    }

    /**
     * Get the ANGLE package name.
     */
    private String getAnglePackageName(PackageManager pm) {
        final Intent intent = new Intent(ACTION_ANGLE_FOR_ANDROID);

        final List<ResolveInfo> resolveInfos =
                pm.queryIntentActivities(intent, PackageManager.MATCH_SYSTEM_ONLY);
        if (resolveInfos.size() != 1) {
            Log.e(TAG, "Invalid number of ANGLE packages. Required: 1, Found: "
                    + resolveInfos.size());
            for (ResolveInfo resolveInfo : resolveInfos) {
                Log.e(TAG, "Found ANGLE package: " + resolveInfo.activityInfo.packageName);
            }
            return "";
        }

        // Must be exactly 1 ANGLE PKG found to get here.
        return resolveInfos.get(0).activityInfo.packageName;
    }

    /**
     * Check for ANGLE debug package, but only for apps that can load them.
     * An application can load ANGLE debug package if it is a debuggable application, or
     * the device is debuggable.
     */
    private String getAngleDebugPackage(Context context, Bundle coreSettings) {
        if (!isDebuggable()) {
            return "";
        }
        final String debugPackage;

        if (coreSettings != null) {
            debugPackage =
                    coreSettings.getString(Settings.Global.GLOBAL_SETTINGS_ANGLE_DEBUG_PACKAGE);
        } else {
            ContentResolver contentResolver = context.getContentResolver();
            debugPackage = Settings.Global.getString(contentResolver,
                    Settings.Global.GLOBAL_SETTINGS_ANGLE_DEBUG_PACKAGE);
        }
        if (TextUtils.isEmpty(debugPackage)) {
            return "";
        }
        return debugPackage;
    }

    /**
     * Attempt to setup ANGLE with a temporary rules file.
     * True: Temporary rules file was loaded.
     * False: Temporary rules file was *not* loaded.
     */
    private boolean setupAngleWithTempRulesFile(Context context,
                                                String packageName,
                                                String paths,
                                                String devOptIn) {
        /**
         * We only want to load a temp rules file for:
         *  - apps that are marked 'debuggable' in their manifest
         *  - devices that are running a userdebug build (ro.debuggable) or can inject libraries for
         *    debugging (PR_SET_DUMPABLE).
         */
        if (!isDebuggable()) {
            Log.v(TAG, "Skipping loading temporary rules file");
            return false;
        }

        final String angleTempRules = SystemProperties.get(ANGLE_TEMP_RULES);

        if (TextUtils.isEmpty(angleTempRules)) {
            Log.v(TAG, "System property '" + ANGLE_TEMP_RULES + "' is not set or is empty");
            return false;
        }

        Log.i(TAG, "Detected system property " + ANGLE_TEMP_RULES + ": " + angleTempRules);

        final File tempRulesFile = new File(angleTempRules);
        if (tempRulesFile.exists()) {
            Log.i(TAG, angleTempRules + " exists, loading file.");
            try {
                final FileInputStream stream = new FileInputStream(angleTempRules);

                try {
                    final FileDescriptor rulesFd = stream.getFD();
                    final long rulesOffset = 0;
                    final long rulesLength = stream.getChannel().size();
                    Log.i(TAG, "Loaded temporary ANGLE rules from " + angleTempRules);

                    setAngleInfo(paths, packageName, devOptIn, null,
                            rulesFd, rulesOffset, rulesLength);

                    stream.close();

                    // We successfully setup ANGLE, so return with good status
                    return true;
                } catch (IOException e) {
                    Log.w(TAG, "Hit IOException thrown by FileInputStream: " + e);
                }
            } catch (FileNotFoundException e) {
                Log.w(TAG, "Temp ANGLE rules file not found: " + e);
            } catch (SecurityException e) {
                Log.w(TAG, "Temp ANGLE rules file not accessible: " + e);
            }
        }

        return false;
    }

    /**
     * Attempt to setup ANGLE with a rules file loaded from the ANGLE APK.
     * True: APK rules file was loaded.
     * False: APK rules file was *not* loaded.
     */
    private boolean setupAngleRulesApk(String anglePkgName,
            ApplicationInfo angleInfo,
            PackageManager pm,
            String packageName,
            String paths,
            String devOptIn,
            String[] features) {
        // Pass the rules file to loader for ANGLE decisions
        try {
            final AssetManager angleAssets = pm.getResourcesForApplication(angleInfo).getAssets();

            try {
                final AssetFileDescriptor assetsFd = angleAssets.openFd(ANGLE_RULES_FILE);

                setAngleInfo(paths, packageName, devOptIn, features, assetsFd.getFileDescriptor(),
                        assetsFd.getStartOffset(), assetsFd.getLength());

                assetsFd.close();

                return true;
            } catch (IOException e) {
                Log.w(TAG, "Failed to get AssetFileDescriptor for " + ANGLE_RULES_FILE
                        + " from '" + anglePkgName + "': " + e);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to get AssetManager for '" + anglePkgName + "': " + e);
        }

        return false;
    }

    /**
     * Pull ANGLE allowlist from GlobalSettings and compare against current package
     */
    private boolean checkAngleAllowlist(Context context, Bundle bundle, String packageName) {
        final ContentResolver contentResolver = context.getContentResolver();
        final List<String> angleAllowlist =
                getGlobalSettingsString(contentResolver, bundle,
                    Settings.Global.GLOBAL_SETTINGS_ANGLE_ALLOWLIST);

        if (DEBUG) Log.v(TAG, "ANGLE allowlist: " + angleAllowlist);

        return angleAllowlist.contains(packageName);
    }

    /**
     * Pass ANGLE details down to trigger enable logic
     *
     * @param context
     * @param bundle
     * @param pm
     * @param packageName - package name of the application.
     * @return true: ANGLE setup successfully
     *         false: ANGLE not setup (not on allowlist, ANGLE not present, etc.)
     */
    private boolean setupAngle(Context context, Bundle bundle, PackageManager pm,
            String packageName) {

        if (!shouldUseAngle(context, bundle, packageName)) {
            return false;
        }

        ApplicationInfo angleInfo = null;

        // If the developer has specified a debug package over ADB, attempt to find it
        String anglePkgName = getAngleDebugPackage(context, bundle);
        if (!anglePkgName.isEmpty()) {
            Log.i(TAG, "ANGLE debug package enabled: " + anglePkgName);
            try {
                // Note the debug package does not have to be pre-installed
                angleInfo = pm.getApplicationInfo(anglePkgName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "ANGLE debug package '" + anglePkgName + "' not installed");
                return false;
            }
        }

        // Otherwise, check to see if ANGLE is properly installed
        if (angleInfo == null) {
            anglePkgName = getAnglePackageName(pm);
            if (TextUtils.isEmpty(anglePkgName)) {
                Log.w(TAG, "Failed to find ANGLE package.");
                return false;
            }

            Log.i(TAG, "ANGLE package enabled: " + anglePkgName);
            try {
                // Production ANGLE libraries must be pre-installed as a system app
                angleInfo = pm.getApplicationInfo(anglePkgName,
                        PackageManager.MATCH_SYSTEM_ONLY);
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "ANGLE package '" + anglePkgName + "' not installed");
                return false;
            }
        }

        final String abi = chooseAbi(angleInfo);

        // Build a path that includes installed native libs and APK
        final String paths = angleInfo.nativeLibraryDir
                + File.pathSeparator
                + angleInfo.sourceDir
                + "!/lib/"
                + abi;

        if (DEBUG) Log.v(TAG, "ANGLE package libs: " + paths);

        // If the user has set the developer option to something other than default,
        // we need to call setupAngleRulesApk() with the package name and the developer
        // option value (native/angle/other). Then later when we are actually trying to
        // load a driver, GraphicsEnv::getShouldUseAngle() has seen the package name before
        // and can confidently answer yes/no based on the previously set developer
        // option value.
        final String devOptIn = getDriverForPackage(context, bundle, packageName);

        if (setupAngleWithTempRulesFile(context, packageName, paths, devOptIn)) {
            // We setup ANGLE with a temp rules file, so we're done here.
            return true;
        }

        String[] features = getAngleEglFeatures(context, bundle);

        if (setupAngleRulesApk(
                anglePkgName, angleInfo, pm, packageName, paths, devOptIn, features)) {
            // ANGLE with rules is set up from the APK, hence return.
            return true;
        }

        return false;
    }

    /**
     * Determine if the "ANGLE In Use" dialog box should be shown.
     */
    private boolean shouldShowAngleInUseDialogBox(Context context) {
        try {
            ContentResolver contentResolver = context.getContentResolver();
            final int showDialogBox = Settings.Global.getInt(contentResolver,
                    Settings.Global.GLOBAL_SETTINGS_SHOW_ANGLE_IN_USE_DIALOG_BOX);

            return (showDialogBox == 1);
        } catch (Settings.SettingNotFoundException | SecurityException e) {
            // Do nothing and move on
        }

        // No setting, so assume false
        return false;
    }

    /**
     * Determine if ANGLE will be used and setup the environment
     */
    private boolean setupAndUseAngle(Context context, String packageName) {
        // Need to make sure we are evaluating ANGLE usage for the correct circumstances
        if (!setupAngle(context, null, context.getPackageManager(), packageName)) {
            Log.v(TAG, "Package '" + packageName + "' should not use ANGLE");
            return false;
        }

        final boolean useAngle = getShouldUseAngle(packageName);
        Log.v(TAG, "Package '" + packageName + "' should use ANGLE = '" + useAngle + "'");

        return useAngle;
    }

    /**
     * Show the ANGLE in Use Dialog Box
     * @param context
     */
    public void showAngleInUseDialogBox(Context context) {
        final String packageName = context.getPackageName();

        if (shouldShowAngleInUseDialogBox(context) && setupAndUseAngle(context, packageName)) {
            final Intent intent = new Intent(ACTION_ANGLE_FOR_ANDROID_TOAST_MESSAGE);
            String anglePkg = getAnglePackageName(context.getPackageManager());
            intent.setPackage(anglePkg);

            context.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Bundle results = getResultExtras(true);

                    String toastMsg = results.getString(INTENT_KEY_A4A_TOAST_MESSAGE);
                    final Toast toast = Toast.makeText(context, toastMsg, Toast.LENGTH_LONG);
                    toast.show();
                }
            }, null, Activity.RESULT_OK, null, null);
        }
    }

    private String[] getAngleEglFeatures(Context context, Bundle coreSettings) {
        if (mAngleOptInIndex < 0) {
            return null;
        }

        final List<String> featuresLists = getGlobalSettingsString(
                context.getContentResolver(), coreSettings, Settings.Global.ANGLE_EGL_FEATURES);
        if (featuresLists.size() <= mAngleOptInIndex) {
            return null;
        }
        return featuresLists.get(mAngleOptInIndex).split(":");
    }

    /**
     * Return the driver package name to use. Return null for system driver.
     */
    private String chooseDriverInternal(Bundle coreSettings, ApplicationInfo ai) {
        final String productionDriver = SystemProperties.get(PROPERTY_GFX_DRIVER_PRODUCTION);
        final boolean hasProductionDriver = productionDriver != null && !productionDriver.isEmpty();

        final String prereleaseDriver = SystemProperties.get(PROPERTY_GFX_DRIVER_PRERELEASE);
        final boolean hasPrereleaseDriver = prereleaseDriver != null && !prereleaseDriver.isEmpty();

        if (!hasProductionDriver && !hasPrereleaseDriver) {
            Log.v(TAG, "Neither updatable production driver nor prerelease driver is supported.");
            return null;
        }

        // To minimize risk of driver updates crippling the device beyond user repair, never use the
        // updatable drivers for privileged or non-updated system apps. Presumably pre-installed
        // apps were tested thoroughly with the system driver.
        if (ai.isPrivilegedApp() || (ai.isSystemApp() && !ai.isUpdatedSystemApp())) {
            if (DEBUG) {
                Log.v(TAG,
                        "Ignore updatable driver package for privileged/non-updated system app.");
            }
            return null;
        }

        final boolean enablePrereleaseDriver =
                (ai.metaData != null && ai.metaData.getBoolean(METADATA_DEVELOPER_DRIVER_ENABLE))
                || isDebuggable();

        // Priority of updatable driver settings on confliction (Higher priority comes first):
        // 1. UPDATABLE_DRIVER_ALL_APPS
        // 2. UPDATABLE_DRIVER_PRODUCTION_OPT_OUT_APPS
        // 3. UPDATABLE_DRIVER_PRERELEASE_OPT_IN_APPS
        // 4. UPDATABLE_DRIVER_PRODUCTION_OPT_IN_APPS
        // 5. UPDATABLE_DRIVER_PRODUCTION_DENYLIST
        // 6. UPDATABLE_DRIVER_PRODUCTION_ALLOWLIST
        switch (coreSettings.getInt(Settings.Global.UPDATABLE_DRIVER_ALL_APPS, 0)) {
            case UPDATABLE_DRIVER_GLOBAL_OPT_IN_OFF:
                Log.v(TAG, "The updatable driver is turned off on this device.");
                return null;
            case UPDATABLE_DRIVER_GLOBAL_OPT_IN_PRODUCTION_DRIVER:
                Log.v(TAG, "All apps opt in to use updatable production driver.");
                return hasProductionDriver ? productionDriver : null;
            case UPDATABLE_DRIVER_GLOBAL_OPT_IN_PRERELEASE_DRIVER:
                Log.v(TAG, "All apps opt in to use updatable prerelease driver.");
                return hasPrereleaseDriver && enablePrereleaseDriver ? prereleaseDriver : null;
            case UPDATABLE_DRIVER_GLOBAL_OPT_IN_DEFAULT:
            default:
                break;
        }

        final String appPackageName = ai.packageName;
        if (getGlobalSettingsString(null, coreSettings,
                                    Settings.Global.UPDATABLE_DRIVER_PRODUCTION_OPT_OUT_APPS)
                        .contains(appPackageName)) {
            Log.v(TAG, "App opts out for updatable production driver.");
            return null;
        }

        if (getGlobalSettingsString(
                    null, coreSettings, Settings.Global.UPDATABLE_DRIVER_PRERELEASE_OPT_IN_APPS)
                        .contains(appPackageName)) {
            Log.v(TAG, "App opts in for updatable prerelease driver.");
            return hasPrereleaseDriver && enablePrereleaseDriver ? prereleaseDriver : null;
        }

        // Early return here since the rest logic is only for updatable production Driver.
        if (!hasProductionDriver) {
            Log.v(TAG, "Updatable production driver is not supported on the device.");
            return null;
        }

        final boolean isOptIn =
                getGlobalSettingsString(null, coreSettings,
                                        Settings.Global.UPDATABLE_DRIVER_PRODUCTION_OPT_IN_APPS)
                        .contains(appPackageName);
        final List<String> allowlist =
                getGlobalSettingsString(null, coreSettings,
                                        Settings.Global.UPDATABLE_DRIVER_PRODUCTION_ALLOWLIST);
        if (!isOptIn && allowlist.indexOf(UPDATABLE_DRIVER_ALLOWLIST_ALL) != 0
                && !allowlist.contains(appPackageName)) {
            Log.v(TAG, "App is not on the allowlist for updatable production driver.");
            return null;
        }

        // If the application is not opted-in, then check whether it's on the denylist,
        // terminate early if it's on the denylist and fallback to system driver.
        if (!isOptIn
                && getGlobalSettingsString(
                        null, coreSettings, Settings.Global.UPDATABLE_DRIVER_PRODUCTION_DENYLIST)
                           .contains(appPackageName)) {
            Log.v(TAG, "App is on the denylist for updatable production driver.");
            return null;
        }

        return productionDriver;
    }

    /**
     * Choose whether the current process should use the builtin or an updated driver.
     */
    private boolean chooseDriver(
            Context context, Bundle coreSettings, PackageManager pm, String packageName,
            ApplicationInfo ai) {
        final String driverPackageName = chooseDriverInternal(coreSettings, ai);
        if (driverPackageName == null) {
            return false;
        }

        final PackageInfo driverPackageInfo;
        try {
            driverPackageInfo = pm.getPackageInfo(driverPackageName,
                    PackageManager.MATCH_SYSTEM_ONLY | PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "updatable driver package '" + driverPackageName + "' not installed");
            return false;
        }

        // O drivers are restricted to the sphal linker namespace, so don't try to use
        // packages unless they declare they're compatible with that restriction.
        final ApplicationInfo driverAppInfo = driverPackageInfo.applicationInfo;
        if (driverAppInfo.targetSdkVersion < Build.VERSION_CODES.O) {
            if (DEBUG) {
                Log.w(TAG, "updatable driver package is not compatible with O");
            }
            return false;
        }

        final String abi = chooseAbi(driverAppInfo);
        if (abi == null) {
            if (DEBUG) {
                // This is the normal case for the pre-installed empty driver package, don't spam
                if (driverAppInfo.isUpdatedSystemApp()) {
                    Log.w(TAG, "Updatable driver package has no compatible native libraries");
                }
            }
            return false;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append(driverAppInfo.nativeLibraryDir)
          .append(File.pathSeparator);
        sb.append(driverAppInfo.sourceDir)
          .append("!/lib/")
          .append(abi);
        final String paths = sb.toString();
        final String sphalLibraries = getSphalLibraries(context, driverPackageName);
        Log.v(TAG, "Updatable driver package search path: " + paths
                + ", required sphal libraries: " + sphalLibraries);
        setDriverPathAndSphalLibraries(paths, sphalLibraries);

        if (driverAppInfo.metaData == null) {
            throw new NullPointerException("apk's meta-data cannot be null");
        }

        String driverBuildTime = driverAppInfo.metaData.getString(METADATA_DRIVER_BUILD_TIME);
        if (driverBuildTime == null || driverBuildTime.length() <= 1) {
            Log.w(TAG, "com.android.graphics.driver.build_time is not set");
            driverBuildTime = "L0";
        }
        // driver_build_time in the meta-data is in "L<Unix epoch timestamp>" format. e.g. L123456.
        // Long.parseLong will throw if the meta-data "driver_build_time" is not set properly.
        setGpuStats(driverPackageName, driverPackageInfo.versionName, driverAppInfo.longVersionCode,
                Long.parseLong(driverBuildTime.substring(1)), packageName, 0);

        return true;
    }

    private static String chooseAbi(ApplicationInfo ai) {
        final String isa = VMRuntime.getCurrentInstructionSet();
        if (ai.primaryCpuAbi != null &&
                isa.equals(VMRuntime.getInstructionSet(ai.primaryCpuAbi))) {
            return ai.primaryCpuAbi;
        }
        if (ai.secondaryCpuAbi != null &&
                isa.equals(VMRuntime.getInstructionSet(ai.secondaryCpuAbi))) {
            return ai.secondaryCpuAbi;
        }
        return null;
    }

    private String getSphalLibraries(Context context, String driverPackageName) {
        try {
            final Context driverContext =
                    context.createPackageContext(driverPackageName, Context.CONTEXT_RESTRICTED);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(
                    driverContext.getAssets().open(UPDATABLE_DRIVER_SPHAL_LIBRARIES_FILENAME)));
            final ArrayList<String> assetStrings = new ArrayList<>();
            for (String assetString; (assetString = reader.readLine()) != null;) {
                assetStrings.add(assetString);
            }
            return String.join(":", assetStrings);
        } catch (PackageManager.NameNotFoundException e) {
            if (DEBUG) {
                Log.w(TAG, "Driver package '" + driverPackageName + "' not installed");
            }
        } catch (IOException e) {
            if (DEBUG) {
                Log.w(TAG, "Failed to load '" + UPDATABLE_DRIVER_SPHAL_LIBRARIES_FILENAME + "'");
            }
        }
        return "";
    }

    private static native boolean isDebuggable();
    private static native void setLayerPaths(ClassLoader classLoader, String layerPaths);
    private static native void setDebugLayers(String layers);
    private static native void setDebugLayersGLES(String layers);
    private static native void setDriverPathAndSphalLibraries(String path, String sphalLibraries);
    private static native void setGpuStats(String driverPackageName, String driverVersionName,
            long driverVersionCode, long driverBuildTime, String appPackageName, int vulkanVersion);
    private static native void setAngleInfo(String path, String appPackage, String devOptIn,
            String[] features, FileDescriptor rulesFd, long rulesOffset, long rulesLength);
    private static native boolean getShouldUseAngle(String packageName);
    private static native boolean setInjectLayersPrSetDumpable();

    /**
     * Hint for GraphicsEnvironment that an activity is launching on the process.
     * Then the app process is allowed to send stats to GpuStats module.
     */
    public static native void hintActivityLaunch();
}
