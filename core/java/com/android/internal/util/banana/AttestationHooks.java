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

package com.android.internal.util.banana;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.SystemProperties;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** @hide */
public final class AttestationHooks {
    private static final String TAG = "AttestationHooks";
    private static final boolean DEBUG = false;

    // Use certified properties for GMS to pass SafetyNet / Play Integrity
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_RESTORE = "com.google.android.apps.restore";

    // Use certified properties for Snapchat to prevent send/receive delays
    private static final String PACKAGE_SNAPCHAT = "com.snapchat.android";

    private static final Map<String, Object> sP7Props = new HashMap<>();
    static {
        sP7Props.put("BRAND", "google");
        sP7Props.put("MANUFACTURER", "Google");
        sP7Props.put("DEVICE", "cheetah");
        sP7Props.put("PRODUCT", "cheetah");
        sP7Props.put("MODEL", "Pixel 7 Pro");
        sP7Props.put("FINGERPRINT", "google/cheetah/cheetah:13/TQ2A.230505.002/9891397:user/release-keys");
    }

    private static volatile boolean sIsGms = false;
    private static volatile boolean sIsFinsky = false;

    private AttestationHooks() { }

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

    private static void spoofBuildGms() {
        // Alter model name and fingerprint to avoid hardware attestation enforcement
        setBuildField("FINGERPRINT", "google/marlin/marlin:7.1.2/NJH47F/4146041:user/release-keys");
        setBuildField("PRODUCT", "marlin");
        setBuildField("DEVICE", "marlin");
        setBuildField("MODEL", "Pixel XL");
        setVersionField("DEVICE_INITIAL_SDK_INT", Build.VERSION_CODES.N_MR1);
    }

    public static void initApplicationBeforeOnCreate(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();
        if (packageName.equals(PACKAGE_GMS)
            || packageName.equals(PACKAGE_RESTORE)) {
            if (processName.toLowerCase().contains("unstable")
                || processName.toLowerCase().contains("persistent")
                || processName.toLowerCase().contains("pixelmigrate")
                || processName.toLowerCase().contains("restore")) {
                sIsGms = true;
                dlog("Spoofing build for GMS");
                spoofBuildGms();
            } else {
                dlog("Spoofing Pixel 7 Pro for Google Play Services");
                sP7Props.forEach((k, v) -> setPropValue(k, v));
            }
        }

        if (packageName.equals(PACKAGE_FINSKY)) {
            sIsFinsky = true;
        }

        if (packageName.equals(PACKAGE_SNAPCHAT)) {
            dlog("Spoofing build for: " + packageName);
            spoofBuildGms();
        }
    }

    private static void setPropValue(String key, Object value){
        try {
            dlog("Setting prop " + key + " to " + value.toString());
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static boolean isCallerSafetyNet() {
        return sIsGms && Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet or Play Integrity
        if (isCallerSafetyNet() || sIsFinsky) {
            Log.i(TAG, "Blocked key attestation sIsGms=" + sIsGms + " sIsFinsky=" + sIsFinsky);
            throw new UnsupportedOperationException();
        }
    }

    public static void dlog(String msg) {
      if (DEBUG) Log.d(TAG, msg);
    }
}
