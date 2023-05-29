/*
 * Copyright (C) 2020 The Pixel Experience Project
 *               2021-2022 crDroid Android Project
 *               2023 RisingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.banana;

import android.app.Application;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PixelPropsUtils {

    private static final String TAG = PixelPropsUtils.class.getSimpleName();
    private static final String DEVICE = "ro.product.device";
    private static final boolean DEBUG = false;

    private static final Map<String, Object> propsToChangePixel5;
    private static final Map<String, Object> propsToChangePixel7Pro;
    private static final Map<String, Object> propsToChangePixelXL;
    private static final Map<String, Object> propsToChangeROG1;
    private static final Map<String, Object> propsToChangeROG3;
    private static final Map<String, Object> propsToChangeXP5;
    private static final Map<String, Object> propsToChangeOP8P;
    private static final Map<String, Object> propsToChangeOP9R;
    private static final Map<String, Object> propsToChange11T;
    private static final Map<String, Object> propsToChangeF4;
    private static final Map<String, Object> propsToChangeK30U;
    private static final Map<String, ArrayList<String>> propsToKeep;

    // Packages to Spoof as Pixel 7 Pro
    private static final String[] packagesToChangePixel7Pro = {
            "com.google.android.apps.googleassistant",
            "com.google.android.apps.wallpaper",
            "com.google.android.apps.privacy.wildlife",
            "com.google.android.apps.subscriptions.red",
            "com.google.android.dialer",
            "com.google.android.messaging",
            "com.google.android.googlequicksearchbox",
            "com.google.android.inputmethod.latin"
    };

    // Extra Packages to Spoof
    private static final String[] extraPackagesToChange = {
            "com.android.chrome",
            "com.breel.wallpapers20",
            "com.nhs.online.nhsonline",
            "com.netflix.mediaclient",
            "com.nothing.smartcenter"
    };

    // Packages to Keep with original device
    private static final String[] packagesToKeep = {
            "com.google.android.mtcl83",
            "com.google.android.ultracvm",
            "com.google.android.apps.recorder",
            "com.google.android.apps.wearables.maestro.companion"
    };

    // Packages to Spoof as ROG Phone 1
    private static final String[] packagesToChangeROG1 = {
            "com.madfingergames.legends"
    };

    // Packages to Spoof as ROG Phone 3
    private static final String[] packagesToChangeROG3 = {
            "com.pearlabyss.blackdesertm",
            "com.pearlabyss.blackdesertm.gl"
    };

    // Packages to Spoof as Redmi K30 Ultra
    private static final String[] packagesToChangeK30U = {
            "com.pubg.imobile"
    };

    // Packages to Spoof as Xperia 5
    private static final String[] packagesToChangeXP5 = {
            "com.activision.callofduty.shooter",
            "com.garena.game.codm",
            "com.tencent.tmgp.kr.codm",
            "com.vng.codmvn"
    };

    // Packages to Spoof as OnePlus 8 Pro
    private static final String[] packagesToChangeOP8P = {
            "com.netease.lztgglobal",
            "com.pubg.krmobile",
            "com.rekoo.pubgm",
            "com.riotgames.league.wildrift",
            "com.riotgames.league.wildrifttw",
            "com.riotgames.league.wildriftvn",
            "com.tencent.ig",
            "com.tencent.tmgp.pubgmhd",
            "com.vng.pubgmobile"
    };

    // Packages to Spoof as OnePlus 9R
    private static final String[] packagesToChangeOP9R = {
            "com.epicgames.fortnite",
            "com.epicgames.portal"
    };

    // Packages to Spoof as Mi 11T
    private static final String[] packagesToChange11T = {
            "com.ea.gp.apexlegendsmobilefps",
            "com.levelinfinite.hotta.gp",
            "com.mobile.legends",
            "com.supercell.clashofclans",
            "com.tencent.tmgp.sgame",
            "com.vng.mlbbvn"
    };

    // Packages to Spoof as POCO F4
    private static final String[] packagesToChangeF4 = {
            "com.dts.freefiremax",
            "com.dts.freefireth"
    };

    // Codenames for currently supported Pixels by Google
    private static final String[] pixelCodenames = {
            "cheetah",
            "panther",
            "bluejay",
            "oriole",
            "raven",
            "barbet",
            "redfin",
            "bramble",
            "sunfish",
            "coral",
            "flame"
    };

    private static volatile boolean sIsGms = false;
    private static volatile boolean sIsFinsky = false;
    private static volatile boolean sNeedsWASpoof = false;

    static {
        propsToKeep = new HashMap<>();
        propsToKeep.put("com.google.android.settings.intelligence", new ArrayList<>(Collections.singletonList("FINGERPRINT")));
        propsToChangePixel7Pro = new HashMap<>();
        propsToChangePixel7Pro.put("BRAND", "google");
        propsToChangePixel7Pro.put("MANUFACTURER", "Google");
        propsToChangePixel7Pro.put("DEVICE", "cheetah");
        propsToChangePixel7Pro.put("PRODUCT", "cheetah");
        propsToChangePixel7Pro.put("MODEL", "Pixel 7 Pro");
        propsToChangePixel7Pro.put("FINGERPRINT", "google/cheetah/cheetah:13/TQ2A.230405.003.E1/9802792:user/release-keys");
        propsToChangePixel5 = new HashMap<>();
        propsToChangePixel5.put("BRAND", "google");
        propsToChangePixel5.put("MANUFACTURER", "Google");
        propsToChangePixel5.put("DEVICE", "redfin");
        propsToChangePixel5.put("PRODUCT", "redfin");
        propsToChangePixel5.put("MODEL", "Pixel 5");
        propsToChangePixel5.put("FINGERPRINT", "google/redfin/redfin:13/TQ2A.230405.003/9719927:user/release-keys");
        propsToChangePixelXL = new HashMap<>();
        propsToChangePixelXL.put("BRAND", "google");
        propsToChangePixelXL.put("MANUFACTURER", "Google");
        propsToChangePixelXL.put("DEVICE", "marlin");
        propsToChangePixelXL.put("PRODUCT", "marlin");
        propsToChangePixelXL.put("MODEL", "Pixel XL");
        propsToChangePixelXL.put("FINGERPRINT", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");
        propsToChangeROG1 = new HashMap<>();
        propsToChangeROG1.put("MODEL", "ASUS_Z01QD");
        propsToChangeROG1.put("MANUFACTURER", "asus");
        propsToChangeROG3 = new HashMap<>();
        propsToChangeROG3.put("MODEL", "ASUS_I003D");
        propsToChangeROG3.put("MANUFACTURER", "asus");
        propsToChangeXP5 = new HashMap<>();
        propsToChangeXP5.put("MODEL", "SO-52A");
        propsToChangeXP5.put("MANUFACTURER", "Sony");
        propsToChangeOP8P = new HashMap<>();
        propsToChangeOP8P.put("MODEL", "IN2020");
        propsToChangeOP8P.put("MANUFACTURER", "OnePlus");
        propsToChangeOP9R = new HashMap<>();
        propsToChangeOP9R.put("MODEL", "LE2101");
        propsToChangeOP9R.put("MANUFACTURER", "OnePlus");
        propsToChange11T = new HashMap<>();
        propsToChange11T.put("MODEL", "21081111RG");
        propsToChange11T.put("MANUFACTURER", "Xiaomi");
        propsToChangeF4 = new HashMap<>();
        propsToChangeF4.put("MODEL", "22021211RG");
        propsToChangeF4.put("MANUFACTURER", "Xiaomi");
        propsToChangeK30U = new HashMap<>();
        propsToChangeK30U.put("MODEL", "M2006J10C");
        propsToChangeK30U.put("MANUFACTURER", "Xiaomi");
    }
    
    public static void setProps(String packageName) {
    	String pkgName = packageName.toLowerCase();
        if (pkgName == null || pkgName.isEmpty()
            || List.of("camera", "youtube", "euicc" , "ar.core").stream().anyMatch(pkgName::contains)
            || Arrays.asList(packagesToKeep).contains(pkgName)) {
            return;
        }
        Map<String, Object> propsToChange = new HashMap<>();
        sIsFinsky = pkgName.contains("com.android.vending");
        sNeedsWASpoof = List.of("pixelmigrate", "restore", "snapchat").stream().anyMatch(pkgName::contains);
        if (pkgName.contains("com.google.android.gms")) {
            final String processName = Application.getProcessName().toLowerCase();
            sIsGms = List.of(".persistent", ".unstable").stream().anyMatch(processName::contains);
        }
        if (sNeedsWASpoof || sIsGms) {
             spoofBuildGms();
        }
        if (pkgName.startsWith("com.google.")
                || Arrays.asList(extraPackagesToChange).contains(pkgName)) {

            boolean isPixelDevice = Arrays.asList(pixelCodenames).contains(SystemProperties.get(DEVICE));
                
            if (pkgName.contains("com.google.android.apps.photos")) {
                if (SystemProperties.getBoolean("persist.sys.pixelprops.gphotos", false)) {
                    propsToChange.putAll(propsToChangePixelXL);
                } else {
                    if (isPixelDevice) return;
                    propsToChange.putAll(propsToChangePixel5);
                }
            } else if (pkgName.contains("netflix") && 
                        !SystemProperties.getBoolean("persist.sys.pixelprops.netflix", false)) {
                    if (DEBUG) Log.d(TAG, "Netflix spoofing disabled by system prop");
                    return;
            } else {
                if (Arrays.asList(packagesToChangePixel7Pro).contains(pkgName)) {
                    propsToChange.putAll(propsToChangePixel7Pro);
                } else {
                    boolean spoofALl = SystemProperties.getBoolean("persist.sys.pixelprops.spoofAll", false);
                    if (!spoofALl) return;
                    propsToChange.putAll(propsToChangePixelXL);
                }
            }

            if (DEBUG) Log.d(TAG, "Defining props for: " + pkgName);
            for (Map.Entry<String, Object> prop : propsToChange.entrySet()) {
                String key = prop.getKey();
                Object value = prop.getValue();
                if (propsToKeep.containsKey(pkgName) && propsToKeep.get(pkgName).contains(key)) {
                    if (DEBUG) Log.d(TAG, "Not defining " + key + " prop for: " + pkgName);
                    continue;
                }
                if (DEBUG) Log.d(TAG, "Defining " + key + " prop for: " + pkgName);
                setPropValue(key, value);
            }
            // Set proper indexing fingerprint
            if (pkgName.contains("settings.intelligence")) {
                setBuildField("FINGERPRINT", Build.VERSION.INCREMENTAL);
            }
        } else {

            if (!SystemProperties.getBoolean("persist.sys.pixelprops.games", false))
                return;

            if (Arrays.asList(packagesToChangeROG1).contains(pkgName)) {
                if (DEBUG) Log.d(TAG, "Defining props for: " + pkgName);
                for (Map.Entry<String, Object> prop : propsToChangeROG1.entrySet()) {
                    String key = prop.getKey();
                    Object value = prop.getValue();
                    setPropValue(key, value);
                }
            } else if (Arrays.asList(packagesToChangeROG3).contains(pkgName)) {
                if (DEBUG) Log.d(TAG, "Defining props for: " + pkgName);
                for (Map.Entry<String, Object> prop : propsToChangeROG3.entrySet()) {
                    String key = prop.getKey();
                    Object value = prop.getValue();
                    setPropValue(key, value);
                }
            } else if (Arrays.asList(packagesToChangeXP5).contains(pkgName)) {
                if (DEBUG) Log.d(TAG, "Defining props for: " + pkgName);
                for (Map.Entry<String, Object> prop : propsToChangeXP5.entrySet()) {
                    String key = prop.getKey();
                    Object value = prop.getValue();
                    setPropValue(key, value);
                }
            } else if (Arrays.asList(packagesToChangeOP8P).contains(pkgName)) {
                if (DEBUG) Log.d(TAG, "Defining props for: " + pkgName);
                for (Map.Entry<String, Object> prop : propsToChangeOP8P.entrySet()) {
                    String key = prop.getKey();
                    Object value = prop.getValue();
                    setPropValue(key, value);
                }
            } else if (Arrays.asList(packagesToChangeOP9R).contains(pkgName)) {
                if (DEBUG) Log.d(TAG, "Defining props for: " + pkgName);
                for (Map.Entry<String, Object> prop : propsToChangeOP9R.entrySet()) {
                    String key = prop.getKey();
                    Object value = prop.getValue();
                    setPropValue(key, value);
                }
            } else if (Arrays.asList(packagesToChangeK30U).contains(packageName)) {
                if (DEBUG) Log.d(TAG, "Defining props for: " + packageName);
                for (Map.Entry<String, Object> prop : propsToChangeK30U.entrySet()) {
                    String key = prop.getKey();
                    Object value = prop.getValue();
                    setPropValue(key, value);
                }
            } else if (Arrays.asList(packagesToChange11T).contains(pkgName)) {
                if (DEBUG) Log.d(TAG, "Defining props for: " + pkgName);
                for (Map.Entry<String, Object> prop : propsToChange11T.entrySet()) {
                    String key = prop.getKey();
                    Object value = prop.getValue();
                    setPropValue(key, value);
                }
            } else if (Arrays.asList(packagesToChangeF4).contains(pkgName)) {
                if (DEBUG) Log.d(TAG, "Defining props for: " + pkgName);
                for (Map.Entry<String, Object> prop : propsToChangeF4.entrySet()) {
                    String key = prop.getKey();
                    Object value = prop.getValue();
                    setPropValue(key, value);
                }
            }
        }
    }

    private static void setPropValue(String key, Object value) {
        try {
            if (DEBUG) Log.d(TAG, "Defining prop " + key + " to " + value.toString());
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static void setBuildField(String key, String value) {
        try {
            // Unlock
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            field.set(null, value);

            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void setVersionField(String key, Integer value) {
        try {
            // Unlock
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            field.set(null, value);

            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void setVersionFieldString(String key, String value) {
        try {
            // Unlock
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            field.set(null, value);

            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void spoofBuildGms() {
        // Alter most build properties for cts profile match checks
        setBuildField("BRAND", "google");
        setBuildField("PRODUCT", "marlin");
        setBuildField("MODEL", "Pixel XL");
    	setBuildField("MANUFACTURER", "Google");
        setBuildField("DEVICE", "marlin");
        setBuildField("FINGERPRINT", "google/marlin/marlin:7.1.2/NJH47F/4146041:user/release-keys");
        setBuildField("ID", "NJH47F");
        setBuildField("TYPE", "user");
        setBuildField("TAGS", "release-keys");
        setVersionField("DEVICE_INITIAL_SDK_INT", Build.VERSION_CODES.N_MR1);
        setVersionFieldString("SECURITY_PATCH", "2017-08-05");
    }

    private static boolean isCallerSafetyNet() {
        return sIsGms && Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().toLowerCase().contains("droidguard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet & PlayIntegrity
        if (isCallerSafetyNet() || sIsFinsky) {
            throw new UnsupportedOperationException();
        }
    }
}
