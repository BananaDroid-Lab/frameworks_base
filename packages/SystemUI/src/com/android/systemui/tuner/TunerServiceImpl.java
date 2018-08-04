/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.systemui.tuner;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;

import androidx.annotation.WorkerThread;

import com.android.internal.util.ArrayUtils;
import com.android.systemui.DejankUtils;
import com.android.systemui.R;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.demomode.DemoModeController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.util.leak.LeakDetector;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;


/**
 */
@SysUISingleton
public class TunerServiceImpl extends TunerService {

    private static final String TAG = "TunerService";
    private static final String TUNER_VERSION = "sysui_tuner_version";

    private static final int CURRENT_TUNER_VERSION = 4;

    // Things that use the tunable infrastructure but are now real user settings and
    // shouldn't be reset with tuner settings.
    private static final String[] RESET_EXCEPTION_LIST = new String[] {
            Clock.STATUS_BAR_CLOCK_SECONDS,
            QSHost.TILES_SETTING,
            Settings.Secure.DOZE_ALWAYS_ON,
            Settings.Secure.MEDIA_CONTROLS_RESUME,
            Settings.Secure.MEDIA_CONTROLS_RECOMMENDATION
    };

    private final Observer mObserver = new Observer();
    // Map of Uris we listen on to their settings keys.
    private final ArrayMap<Uri, String> mListeningUris = new ArrayMap<>();
    // Map of settings keys to the listener.
    private final ConcurrentHashMap<String, Set<Tunable>> mTunableLookup =
            new ConcurrentHashMap<>();
    // Set of all tunables, used for leak detection.
    private final HashSet<Tunable> mTunables = LeakDetector.ENABLED ? new HashSet<>() : null;
    private final Context mContext;
    private final LeakDetector mLeakDetector;
    private final DemoModeController mDemoModeController;

    private ContentResolver mContentResolver;
    private int mCurrentUser;
    private UserTracker.Callback mCurrentUserTracker;
    private UserTracker mUserTracker;
    private final ComponentName mTunerComponent;

    /**
     */
    @Inject
    public TunerServiceImpl(
            Context context,
            @Main Handler mainHandler,
            LeakDetector leakDetector,
            DemoModeController demoModeController,
            UserTracker userTracker) {
        super(context);
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mLeakDetector = leakDetector;
        mDemoModeController = demoModeController;
        mUserTracker = userTracker;
        mTunerComponent = new ComponentName(mContext, TunerActivity.class);

        for (UserInfo user : UserManager.get(mContext).getUsers()) {
            mCurrentUser = user.getUserHandle().getIdentifier();
            if (getValue(TUNER_VERSION, 0) != CURRENT_TUNER_VERSION) {
                upgradeTuner(getValue(TUNER_VERSION, 0), CURRENT_TUNER_VERSION, mainHandler);
            }
        }

        mCurrentUser = mUserTracker.getUserId();
        mCurrentUserTracker = new UserTracker.Callback() {
            @Override
            public void onUserChanged(int newUser, Context userContext) {
                mCurrentUser = newUser;
                reloadAll();
                reregisterAll();
            }
        };
        mUserTracker.addCallback(mCurrentUserTracker,
                new HandlerExecutor(mainHandler));
        setTunerEnabled(true);
    }

    @Override
    public void destroy() {
        mUserTracker.removeCallback(mCurrentUserTracker);
    }

    private void upgradeTuner(int oldVersion, int newVersion, Handler mainHandler) {
        if (oldVersion < 1) {
            String hideListStr = getValue(StatusBarIconController.ICON_HIDE_LIST);
            if (hideListStr != null) {
                ArraySet<String> iconHideList =
                        StatusBarIconController.getIconHideList(mContext, hideListStr);

                iconHideList.add("rotate");
                iconHideList.add("headset");

                Settings.Secure.putStringForUser(mContentResolver,
                        StatusBarIconController.ICON_HIDE_LIST,
                        TextUtils.join(",", iconHideList), mCurrentUser);
            }
        }
        if (oldVersion < 5) {
            setTunerEnabled(false);
        }
        setValue(TUNER_VERSION, newVersion);
    }

    private boolean isCustomSetting(String key) {
        return isCustomGlobal(key) || isCustomSystem(key) || isCustomSecure(key);
    }

    private boolean isCustomGlobal(String key) {
        return key.startsWith("customglobal:");
    }

    private boolean isCustomSystem(String key) {
        return key.startsWith("customsystem:");
    }

    private boolean isCustomSecure(String key) {
        return key.startsWith("customsecure:");
    }

    private boolean isSystem(String key) {
        return key.startsWith("system:");
    }

    private boolean isGlobal(String key) {
        return key.startsWith("global:");
    }

    private String chomp(String key) {
        return key.replaceFirst("^(customglobal|customsecure|customsystem|system|global):", "");
    }

    @Override
    public String getValue(String setting) {
        if (isCustomGlobal(setting)) {
            return Settings.Global.getString(mContentResolver, chomp(setting));
        } else if (isCustomSecure(setting)) {
            return Settings.Secure.getStringForUser(
                    mContentResolver, chomp(setting), mCurrentUser);
        } else if (isCustomSystem(setting)) {
            return Settings.System.getStringForUser(
                    mContentResolver, chomp(setting), mCurrentUser);
        } else if (isSystem(setting)) {
            return Settings.System.getStringForUser(
                    mContentResolver, chomp(setting), mCurrentUser);
        } else if (isGlobal(setting)) {
            return Settings.Global.getStringForUser(
                    mContentResolver, chomp(setting), mCurrentUser);
        } else {
            return Settings.Secure.getStringForUser(mContentResolver, setting, mCurrentUser);
        }
    }

    @Override
    public void setValue(String setting, String value) {
        if (isCustomGlobal(setting)) {
            Settings.Global.putString(mContentResolver, chomp(setting), value);
        } else if (isCustomSecure(setting)) {
            Settings.Secure.putStringForUser(
                    mContentResolver, chomp(setting), value, mCurrentUser);
        } else if (isCustomSystem(setting)) {
            Settings.System.putStringForUser(
                    mContentResolver, chomp(setting), value, mCurrentUser);
        } else if (isSystem(setting)) {
            Settings.System.putStringForUser(
                    mContentResolver, chomp(setting), value, mCurrentUser);
        } else if (isGlobal(setting)) {
            Settings.Global.putStringForUser(
                    mContentResolver, chomp(setting), value, mCurrentUser);
        } else {
            Settings.Secure.putStringForUser(mContentResolver, setting, value, mCurrentUser);
        }
    }

    @Override
    public int getValue(String setting, int def) {
        if (isCustomGlobal(setting)) {
            return Settings.Global.getInt(mContentResolver, chomp(setting), def);
        } else if (isCustomSecure(setting)) {
            return Settings.Secure.getIntForUser(
                    mContentResolver, chomp(setting), def, mCurrentUser);
        } else if (isCustomSystem(setting)) {
            return Settings.System.getIntForUser(
                    mContentResolver, chomp(setting), def, mCurrentUser);
        } else if (isSystem(setting)) {
            return Settings.System.getIntForUser(
                    mContentResolver, chomp(setting), def, mCurrentUser);
        } else if (isGlobal(setting)) {
            return Settings.Global.getInt(
                    mContentResolver, chomp(setting), def);
        } else {
            return Settings.Secure.getIntForUser(mContentResolver, setting, def, mCurrentUser);
        }
    }

    @Override
    public String getValue(String setting, String def) {
        String ret;
        if (isCustomGlobal(setting)) {
            ret = Settings.Global.getString(mContentResolver, chomp(setting));
        } else if (isCustomSecure(setting)) {
            ret = Settings.Secure.getStringForUser(
                    mContentResolver, chomp(setting), mCurrentUser);
        } else if (isCustomSystem(setting)) {
            ret = Settings.System.getStringForUser(
                    mContentResolver, chomp(setting), mCurrentUser);
        } else if (isSystem(setting)) {
            ret = Settings.System.getStringForUser(
                    mContentResolver, chomp(setting), mCurrentUser);
        } else if (isGlobal(setting)) {
            ret = Settings.Global.getStringForUser(
                    mContentResolver, chomp(setting), mCurrentUser);
        } else {
            ret = Secure.getStringForUser(mContentResolver, setting, mCurrentUser);
        }
        if (ret == null) return def;
        return ret;
    }

    @Override
    public void setValue(String setting, int value) {
        if (isCustomGlobal(setting)) {
            Settings.Global.putInt(mContentResolver, chomp(setting), value);
        } else if (isCustomSecure(setting)) {
            Settings.Secure.putIntForUser(
                    mContentResolver, chomp(setting), value, mCurrentUser);
        } else if (isCustomSystem(setting)) {
            Settings.System.putIntForUser(
                    mContentResolver, chomp(setting), value, mCurrentUser);
        } else if (isSystem(setting)) {
            Settings.System.putIntForUser(mContentResolver, chomp(setting), value, mCurrentUser);
        } else if (isGlobal(setting)) {
            Settings.Global.putInt(mContentResolver, chomp(setting), value);
        } else {
            Settings.Secure.putIntForUser(mContentResolver, setting, value, mCurrentUser);
        }
    }

    @Override
    public void addTunable(Tunable tunable, String... keys) {
        for (String key : keys) {
            addTunable(tunable, key);
        }
    }

    private void addTunable(Tunable tunable, String key) {
        if (!mTunableLookup.containsKey(key)) {
            mTunableLookup.put(key, new ArraySet<Tunable>());
        }
        mTunableLookup.get(key).add(tunable);
        if (LeakDetector.ENABLED) {
            mTunables.add(tunable);
            mLeakDetector.trackCollection(mTunables, "TunerService.mTunables");
        }
        final Uri uri;
        if (isCustomGlobal(key)) {
            uri = Settings.Global.getUriFor(chomp(key));
        } else if (isCustomSecure(key)) {
            uri = Settings.Secure.getUriFor(chomp(key));
        } else if (isCustomSystem(key)) {
            uri = Settings.System.getUriFor(chomp(key));
        } else if (isSystem(key)) {
            uri = Settings.System.getUriFor(chomp(key));
        } else if (isGlobal(key)) {
            uri = Settings.Global.getUriFor(chomp(key));
        } else {
            uri = Settings.Secure.getUriFor(key);
        }
        if (!mListeningUris.containsKey(uri)) {
            mListeningUris.put(uri, key);
            mContentResolver.registerContentObserver(uri, false, mObserver,
                    isCustomGlobal(key) ? UserHandle.USER_ALL : mCurrentUser);
        }
        // Send the first state.
        String value = DejankUtils.whitelistIpcs(() -> getValue(key));
        tunable.onTuningChanged(key, value);
    }

    @Override
    public void removeTunable(Tunable tunable) {
        for (Set<Tunable> list : mTunableLookup.values()) {
            list.remove(tunable);
        }
        if (LeakDetector.ENABLED) {
            mTunables.remove(tunable);
        }
    }

    protected void reregisterAll() {
        if (mListeningUris.size() == 0) {
            return;
        }
        mContentResolver.unregisterContentObserver(mObserver);
        for (Uri uri : mListeningUris.keySet()) {
            String key = mListeningUris.get(uri);
            mContentResolver.registerContentObserver(uri, false, mObserver,
                    isCustomGlobal(key) ? UserHandle.USER_ALL : mCurrentUser);
        }
    }

    private void reloadSetting(Uri uri) {
        String key = mListeningUris.get(uri);
        Set<Tunable> tunables = mTunableLookup.get(key);
        if (tunables == null) {
            return;
        }
        String value = getValue(key);
        for (Tunable tunable : tunables) {
            if (tunable != null) {
                tunable.onTuningChanged(key, value);
            }
        }
    }

    private void reloadAll() {
        for (String key : mTunableLookup.keySet()) {
            if (ArrayUtils.contains(RESET_EXCEPTION_LIST, key) || isCustomSetting(key) || key.startsWith("system:")) {
                continue;
            }
            String value = getValue(key);
            for (Tunable tunable : mTunableLookup.get(key)) {
                if (tunable != null) {
                    tunable.onTuningChanged(key, value);
                }
            }
        }
    }

    @Override
    public void clearAll() {
        clearAllFromUser(mCurrentUser);
    }

    public void clearAllFromUser(int user) {
        // Turn off demo mode
        mDemoModeController.requestFinishDemoMode();
        mDemoModeController.requestSetDemoModeAllowed(false);

        // A couple special cases.
        for (String key : mTunableLookup.keySet()) {
            setValue(key, null);
        }
    }


    @Override
    public void setTunerEnabled(boolean enabled) {
        mUserTracker.getUserContext().getPackageManager().setComponentEnabledSetting(
                mTunerComponent,
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
    }

    @Override
    @WorkerThread
    public boolean isTunerEnabled() {
        return mUserTracker.getUserContext().getPackageManager().getComponentEnabledSetting(
                mTunerComponent) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    @Override
    public void showResetRequest(Runnable onDisabled) {
        SystemUIDialog dialog = new SystemUIDialog(mContext);
        dialog.setShowForAllUsers(true);
        dialog.setMessage(R.string.remove_from_settings_prompt);
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, mContext.getString(R.string.cancel),
                (DialogInterface.OnClickListener) null);
        dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                mContext.getString(R.string.qs_customize_remove), (d, which) -> {
                    // Tell the tuner (in main SysUI process) to clear all its settings.
                    mContext.sendBroadcast(new Intent(TunerService.ACTION_CLEAR));
                    // Disable access to tuner.
                    setTunerEnabled(false);
                    // Make them sit through the warning dialog again.
                    Secure.putInt(mContext.getContentResolver(),
                            TunerFragment.SETTING_SEEN_TUNER_WARNING, 0);
                    if (onDisabled != null) {
                        onDisabled.run();
                    }
                });
        dialog.show();
    }

    private class Observer extends ContentObserver {
        public Observer() {
            super(new Handler(Looper.getMainLooper()));
        }

        @Override
        public void onChange(boolean selfChange, java.util.Collection<Uri> uris,
                int flags, int userId) {
            for (Uri u : uris) {
                String key = mListeningUris.get(u);
                if (userId == mUserTracker.getUserId() || isCustomGlobal(key)) {
                    reloadSetting(u);
                }
            }
        }

    }
}
