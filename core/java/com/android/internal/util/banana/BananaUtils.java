/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static android.view.DisplayCutout.BOUNDS_POSITION_LEFT;
import static android.view.DisplayCutout.BOUNDS_POSITION_RIGHT;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.IActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.PowerManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.input.InputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.DisplayInfo;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Surface;

import com.android.internal.statusbar.IStatusBarService;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

public class BananaUtils {

    public static void switchScreenOff(Context ctx) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm!= null) {
            pm.goToSleep(SystemClock.uptimeMillis());
        }
    }

    private static final String TAG = "BananaUtils";

    private static final boolean DEBUG = false;

    private static final int NO_CUTOUT = -1;

    private static OverlayManager mOverlayService;

    public static boolean isChineseLanguage() {
       return Resources.getSystem().getConfiguration().locale.getLanguage().startsWith(
               Locale.CHINESE.getLanguage());
    }

    public static boolean isAppInstalled(Context context, String appUri) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(appUri, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAvailableApp(String packageName, Context context) {
       Context mContext = context;
       final PackageManager pm = mContext.getPackageManager();
       try {
           pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
           int enabled = pm.getApplicationEnabledSetting(packageName);
           return enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
               enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
       } catch (NameNotFoundException e) {
           return false;
       }
    }

    public static boolean isPackageInstalled(Context context, String pkg, boolean ignoreState) {
        if (pkg != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(pkg, 0);
                if (!pi.applicationInfo.enabled && !ignoreState) {
                    return false;
                }
            } catch (NameNotFoundException e) {
                return false;
            }
        }

        return true;
    }

    public static boolean isPackageInstalled(Context context, String pkg) {
        return isPackageInstalled(context, pkg, true);
    }

    public static void killForegroundApp() {
        final IStatusBarService service = IStatusBarService.Stub.asInterface(
                        ServiceManager.getService("statusbar"));
        if (service != null) {
            try {
                service.killForegroundApp();
            } catch (RemoteException e) {
                // do nothing.
            }
        }
    }

    public static boolean deviceSupportsFlashLight(Context context) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(
                Context.CAMERA_SERVICE);
        try {
            String[] ids = cameraManager.getCameraIdList();
            for (String id : ids) {
                CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                Boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
                if (flashAvailable != null
                        && flashAvailable
                        && lensFacing != null
                        && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    return true;
                }
            }
        } catch (CameraAccessException e) {
            // Ignore
        }
        return false;
    }

    public static boolean isWifiOnly(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        Network[] networks = cm.getAllNetworks();

        for (Network network : networks) {
            NetworkCapabilities netCaps = cm.getNetworkCapabilities(network);
            if (netCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return false;
            }
        }

        return true;
    }

    public static boolean deviceHasFlashlight(Context ctx) {
        return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    public static void toggleCameraFlash() {
        FireActions.toggleCameraFlash();
    }

    public static void sendKeycode(int keycode) {
        long when = SystemClock.uptimeMillis();
        final KeyEvent evDown = new KeyEvent(when, when, KeyEvent.ACTION_DOWN, keycode, 0,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);
        final KeyEvent evUp = KeyEvent.changeAction(evDown, KeyEvent.ACTION_UP);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                InputManager.getInstance().injectInputEvent(evDown,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        });
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputManager.getInstance().injectInputEvent(evUp,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        }, 20);
    }

    /**
     * Keep FireAction methods below this point.
     * Place calls to methods above this point.
     */
    private static final class FireActions {
        private static IStatusBarService mStatusBarService = null;
        private static IStatusBarService getStatusBarService() {
            synchronized (FireActions.class) {
                if (mStatusBarService == null) {
                    mStatusBarService = IStatusBarService.Stub.asInterface(
                            ServiceManager.getService("statusbar"));
                }
                return mStatusBarService;
            }
        }

        public static void toggleCameraFlash() {
            IStatusBarService service = getStatusBarService();
            if (service != null) {
                try {
                    service.toggleCameraFlash();
                } catch (RemoteException e) {
                    // do nothing.
                }
            }
        }
    }

    /* returns whether the device has a centered display cutout or not. */
    public static boolean hasCenteredCutout(Context context) {
        Display display = context.getDisplay();
        DisplayCutout cutout = display.getCutout();
        if (cutout != null) {
            Point realSize = new Point();
            display.getRealSize(realSize);

            switch (display.getRotation()) {
                case Surface.ROTATION_0: {
                    Rect rect = cutout.getBoundingRectTop();
                    return !(rect.left <= 0 || rect.right >= realSize.x);
                }
                case Surface.ROTATION_90: {
                    Rect rect = cutout.getBoundingRectLeft();
                    return !(rect.top <= 0 || rect.bottom >= realSize.y);
                }
                case Surface.ROTATION_180: {
                    Rect rect = cutout.getBoundingRectBottom();
                    return !(rect.left <= 0 || rect.right >= realSize.x);
                }
                case Surface.ROTATION_270: {
                    Rect rect = cutout.getBoundingRectRight();
                    return !(rect.top <= 0 || rect.bottom >= realSize.y);
                }
            }
        }
        return false;
    }

    /**
     * Returns whether the device is voice-capable (meaning, it is also a phone).
     */
    public static boolean isVoiceCapable(Context context) {
        TelephonyManager telephony =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephony != null && telephony.isVoiceCapable();
    }

    public static int getCutoutType(Context context) {
        final DisplayInfo info = new DisplayInfo();
        context.getDisplay().getDisplayInfo(info);
        final DisplayCutout cutout = info.displayCutout;
        if (cutout == null) {
            if (DEBUG) Log.v(TAG, "noCutout");
            return NO_CUTOUT;
        }
        final Point displaySize = new Point();
        context.getDisplay().getRealSize(displaySize);
        List<Rect> cutOutBounds = cutout.getBoundingRects();
        if (cutOutBounds != null) {
            for (Rect cutOutRect : cutOutBounds) {
                if (DEBUG) Log.v(TAG, "cutout left= " + cutOutRect.left);
                if (DEBUG) Log.v(TAG, "cutout right= " + cutOutRect.right);
                if (cutOutRect.left == 0 && cutOutRect.right > 0) {  //cutout is located on top left
                    if (DEBUG) Log.v(TAG, "cutout position= " + BOUNDS_POSITION_LEFT);
                    return BOUNDS_POSITION_LEFT;
                } else if (cutOutRect.right == displaySize.x && (displaySize.x - cutOutRect.left) > 0) {  //cutout is located on top right
                    if (DEBUG) Log.v(TAG, "cutout position= " + BOUNDS_POSITION_RIGHT);
                    return BOUNDS_POSITION_RIGHT;
                }
            }
        }
        return NO_CUTOUT;
    }

    // Method to detect whether an overlay is enabled or not
    public static boolean isThemeEnabled(String packageName) {
        mOverlayService = new OverlayManager();
        try {
            List<OverlayInfo> infos = mOverlayService.getOverlayInfosForTarget("android",
                    UserHandle.myUserId());
            for (int i = 0, size = infos.size(); i < size; i++) {
                if (infos.get(i).packageName.equals(packageName)) {
                    return infos.get(i).isEnabled();
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static class OverlayManager {
        private final IOverlayManager mService;

        public OverlayManager() {
            mService = IOverlayManager.Stub.asInterface(
                    ServiceManager.getService(Context.OVERLAY_SERVICE));
        }

        public void setEnabled(String pkg, boolean enabled, int userId)
                throws RemoteException {
            mService.setEnabled(pkg, enabled, userId);
        }

        public List<OverlayInfo> getOverlayInfosForTarget(String target, int userId)
                throws RemoteException {
            return mService.getOverlayInfosForTarget(target, userId);
        }
    }

    public static void restartSystemUi(Context context) {
        new RestartSystemUiTask(context).execute();
    }

    public static void showSystemUiRestartDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.systemui_restart_title)
                .setMessage(R.string.systemui_restart_message)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        restartSystemUi(context);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static class RestartSystemUiTask extends AsyncTask<Void, Void, Void> {
        private Context mContext;

        public RestartSystemUiTask(Context context) {
            super();
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                ActivityManager am =
                        (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                IActivityManager ams = ActivityManager.getService();
                for (ActivityManager.RunningAppProcessInfo app: am.getRunningAppProcesses()) {
                    if ("com.android.systemui".equals(app.processName)) {
                        ams.killApplicationProcess(app.processName, app.uid);
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

   public static void showSettingsRestartDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_restart_title)
                .setMessage(R.string.settings_restart_message)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        restartSettings(context);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void restartSettings(Context context) {
        new restartSettingsTask(context).execute();
    }

    private static class restartSettingsTask extends AsyncTask<Void, Void, Void> {
        private WeakReference<Context> mContext;

        public restartSettingsTask(Context context) {
            super();
            mContext = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                ActivityManager am =
                        (ActivityManager) mContext.get().getSystemService(Context.ACTIVITY_SERVICE);
                IActivityManager ams = ActivityManager.getService();
                for (ActivityManager.RunningAppProcessInfo app: am.getRunningAppProcesses()) {
                    if ("com.android.settings".equals(app.processName)) {
                    	ams.killApplicationProcess(app.processName, app.uid);
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static class QSLayoutUtils {

        public static boolean getQSTileLabelHide(Context context) {
            return Settings.System.getIntForUser(context.getContentResolver(),
                    Settings.System.QS_TILE_LABEL_HIDE,
                    0, UserHandle.USER_CURRENT) == 1;
        }

        public static boolean getQSTileVerticalLayout(Context context) {
            return Settings.System.getIntForUser(context.getContentResolver(),
                    Settings.System.QS_TILE_VERTICAL_LAYOUT,
                    0, UserHandle.USER_CURRENT) == 1;
        }

        public static boolean updateLayout(Context context) {
            final IOverlayManager overlayManager = IOverlayManager.Stub.asInterface(ServiceManager.getService(
                    Context.OVERLAY_SERVICE));
            final int layout_qs = Settings.System.getIntForUser(context.getContentResolver(),
                    Settings.System.QS_LAYOUT,
                    42, UserHandle.USER_CURRENT);
            final int layout_qqs = Settings.System.getIntForUser(context.getContentResolver(),
                    Settings.System.QQS_LAYOUT,
                    22, UserHandle.USER_CURRENT);
            final int row_qs = layout_qs / 10;
            final int col_qs = layout_qs % 10;
            final int row_qqs = layout_qqs / 10;
            for (int i = 0; i < 2; ++i) {
                String pkgName;
                if (i == 0) {
                    pkgName = String.format("com.bananadroid.qs.portrait.layout_%sx%s", Integer.toString(row_qs), Integer.toString(col_qs));
                } else {
                    pkgName = String.format("com.bananadroid.qqs.portrait.layout_%sx%s", Integer.toString(row_qqs), Integer.toString(col_qs));
                }
                try {
                    overlayManager.setEnabledExclusiveInCategory(pkgName, UserHandle.USER_CURRENT);
                } catch (RemoteException re) {
                    return false;
                }
            }
            return true;
        }
    }
}
