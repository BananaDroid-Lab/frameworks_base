/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import static android.app.admin.DevicePolicyResources.Strings.SystemUi.STATUS_BAR_WORK_ICON_ACCESSIBILITY;

import android.annotation.Nullable;
import android.app.ActivityTaskManager;
import android.app.AlarmManager;
import android.app.AlarmManager.AlarmClockInfo;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.media.AudioManager;
import android.nfc.NfcAdapter;
import android.net.ConnectivityManager;
import android.net.INetworkPolicyListener;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkPolicyManager;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.service.notification.ZenModeConfig;
import android.telecom.TelecomManager;
import android.text.format.DateFormat;
import android.util.ArraySet;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;

import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dagger.qualifiers.DisplayId;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dagger.qualifiers.UiBackground;
import com.android.systemui.display.domain.interactor.ConnectedDisplayInteractor;
import com.android.systemui.qs.tiles.DndTile;
import com.android.systemui.qs.tiles.RotationLockTile;
import com.android.systemui.screenrecord.RecordingController;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.CastController;
import com.android.systemui.statusbar.policy.CastController.CastDevice;
import com.android.systemui.statusbar.policy.DataSaverController;
import com.android.systemui.statusbar.policy.DataSaverController.Listener;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.DeviceProvisionedController.DeviceProvisionedListener;
import com.android.systemui.statusbar.policy.HotspotController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.statusbar.policy.NextAlarmController;
import com.android.systemui.statusbar.policy.RotationLockController;
import com.android.systemui.statusbar.policy.RotationLockController.RotationLockControllerCallback;
import com.android.systemui.statusbar.policy.SensorPrivacyController;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.statusbar.policy.ZenModeController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.RingerModeTracker;
import com.android.systemui.util.kotlin.JavaAdapter;
import com.android.systemui.util.time.DateFormatUtil;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * This class contains all of the policy about which icons are installed in the status bar at boot
 * time. It goes through the normal API for icons, even though it probably strictly doesn't need to.
 */
public class PhoneStatusBarPolicy
        implements BluetoothController.Callback,
                CommandQueue.Callbacks,
                RotationLockControllerCallback,
                Listener,
                ZenModeController.Callback,
                DeviceProvisionedListener,
                KeyguardStateController.Callback,
                RecordingController.RecordingStateChangeCallback,
                TunerService.Tunable {
    private static final String TAG = "PhoneStatusBarPolicy";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final String BLUETOOTH_SHOW_BATTERY =
            "system:" + Settings.System.BLUETOOTH_SHOW_BATTERY;
    private static final String NETWORK_TRAFFIC_LOCATION =
            Settings.Secure.NETWORK_TRAFFIC_LOCATION;

    private final String mSlotCast;
    private final String mSlotHotspot;
    private final String mSlotBluetooth;
    private final String mSlotTty;
    private final String mSlotZen;
    private final String mSlotMute;
    private final String mSlotVibrate;
    private final String mSlotAlarmClock;
    private final String mSlotManagedProfile;
    private final String mSlotRotate;
    private final String mSlotHeadset;
    private final String mSlotDataSaver;
    private final String mSlotSensorsOff;
    private final String mSlotScreenRecord;
    private final String mSlotConnectedDisplay;
    private final String mSlotNfc;
    private final String mSlotFirewall;
    private final String mSlotNetworkTraffic;
    private final int mDisplayId;
    private final SharedPreferences mSharedPreferences;
    private final DateFormatUtil mDateFormatUtil;
    private final JavaAdapter mJavaAdapter;
    private final ConnectedDisplayInteractor mConnectedDisplayInteractor;
    private final TelecomManager mTelecomManager;

    private final Handler mHandler;
    private final CastController mCast;
    private final HotspotController mHotspot;
    private final NextAlarmController mNextAlarmController;
    private final AlarmManager mAlarmManager;
    private final UserInfoController mUserInfoController;
    private final UserManager mUserManager;
    private final UserTracker mUserTracker;
    private final DevicePolicyManager mDevicePolicyManager;
    private final StatusBarIconController mIconController;
    private final CommandQueue mCommandQueue;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final Resources mResources;
    private final RotationLockController mRotationLockController;
    private final DataSaverController mDataSaver;
    private final ZenModeController mZenController;
    private final DeviceProvisionedController mProvisionedController;
    private final KeyguardStateController mKeyguardStateController;
    private final Executor mMainExecutor;
    private final Executor mUiBgExecutor;
    private final SensorPrivacyController mSensorPrivacyController;
    private final RecordingController mRecordingController;
    private final RingerModeTracker mRingerModeTracker;
    private final ConnectivityManager mConnectivityManager;
    private final NetworkPolicyManager mNetworkPolicyManager;

    private boolean mZenVisible;
    private boolean mVibrateVisible;
    private boolean mMuteVisible;
    private boolean mCurrentUserSetup;

    private boolean mManagedProfileIconVisible = false;
    private boolean mFirewallVisible = false;

    private int mLastResumedActivityUid = -1;

    private BluetoothController mBluetooth;
    private AlarmManager.AlarmClockInfo mNextAlarm;

    private boolean mNfcVisible;
    private NfcAdapter mAdapter;
    private final Context mContext;

    private boolean mShowBluetoothBattery;
    private boolean mHideBluetooth;

    private boolean mShowNetworkTraffic;

    @Inject
    public PhoneStatusBarPolicy(Context context, StatusBarIconController iconController,
            CommandQueue commandQueue, BroadcastDispatcher broadcastDispatcher,
            @Main Executor mainExecutor, @UiBackground Executor uiBgExecutor, @Main Looper looper,
            @Main Resources resources, CastController castController,
            HotspotController hotspotController, BluetoothController bluetoothController,
            NextAlarmController nextAlarmController, UserInfoController userInfoController,
            RotationLockController rotationLockController, DataSaverController dataSaverController,
            ZenModeController zenModeController,
            DeviceProvisionedController deviceProvisionedController,
            KeyguardStateController keyguardStateController,
            SensorPrivacyController sensorPrivacyController, AlarmManager alarmManager,
            UserManager userManager, UserTracker userTracker,
            DevicePolicyManager devicePolicyManager, RecordingController recordingController,
            @Nullable TelecomManager telecomManager, @DisplayId int displayId,
            @Main SharedPreferences sharedPreferences, DateFormatUtil dateFormatUtil,
            RingerModeTracker ringerModeTracker,
            ConnectedDisplayInteractor connectedDisplayInteractor,
            JavaAdapter javaAdapter
    ) {
        mContext = context;
        mIconController = iconController;
        mCommandQueue = commandQueue;
        mConnectedDisplayInteractor = connectedDisplayInteractor;
        mBroadcastDispatcher = broadcastDispatcher;
        mHandler = new Handler(looper);
        mResources = resources;
        mCast = castController;
        mHotspot = hotspotController;
        mBluetooth = bluetoothController;
        mNextAlarmController = nextAlarmController;
        mAlarmManager = alarmManager;
        mUserInfoController = userInfoController;
        mUserManager = userManager;
        mUserTracker = userTracker;
        mDevicePolicyManager = devicePolicyManager;
        mRotationLockController = rotationLockController;
        mDataSaver = dataSaverController;
        mZenController = zenModeController;
        mProvisionedController = deviceProvisionedController;
        mKeyguardStateController = keyguardStateController;
        mSensorPrivacyController = sensorPrivacyController;
        mRecordingController = recordingController;
        mMainExecutor = mainExecutor;
        mUiBgExecutor = uiBgExecutor;
        mTelecomManager = telecomManager;
        mRingerModeTracker = ringerModeTracker;
        mJavaAdapter = javaAdapter;
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mNetworkPolicyManager = context.getSystemService(NetworkPolicyManager.class);

        mSlotCast = resources.getString(com.android.internal.R.string.status_bar_cast);
        mSlotConnectedDisplay = resources.getString(
                com.android.internal.R.string.status_bar_connected_display);
        mSlotHotspot = resources.getString(com.android.internal.R.string.status_bar_hotspot);
        mSlotBluetooth = resources.getString(com.android.internal.R.string.status_bar_bluetooth);
        mSlotTty = resources.getString(com.android.internal.R.string.status_bar_tty);
        mSlotZen = resources.getString(com.android.internal.R.string.status_bar_zen);
        mSlotMute = resources.getString(com.android.internal.R.string.status_bar_mute);
        mSlotVibrate = resources.getString(com.android.internal.R.string.status_bar_volume);
        mSlotAlarmClock = resources.getString(com.android.internal.R.string.status_bar_alarm_clock);
        mSlotManagedProfile = resources.getString(
                com.android.internal.R.string.status_bar_managed_profile);
        mSlotRotate = resources.getString(com.android.internal.R.string.status_bar_rotate);
        mSlotHeadset = resources.getString(com.android.internal.R.string.status_bar_headset);
        mSlotDataSaver = resources.getString(com.android.internal.R.string.status_bar_data_saver);
        mSlotSensorsOff = resources.getString(com.android.internal.R.string.status_bar_sensors_off);
        mSlotScreenRecord = resources.getString(
                com.android.internal.R.string.status_bar_screen_record);
        mSlotNfc = resources.getString(com.android.internal.R.string.status_bar_nfc);
        mSlotFirewall = resources.getString(R.string.status_bar_firewall_slot);
        mSlotNetworkTraffic = resources.getString(com.android.internal.R.string.status_bar_network_traffic);
        mCurrentUserSetup = mProvisionedController.isDeviceProvisioned();

        mDisplayId = displayId;
        mSharedPreferences = sharedPreferences;
        mDateFormatUtil = dateFormatUtil;

        Dependency.get(TunerService.class).addTunable(this,
                BLUETOOTH_SHOW_BATTERY,
                NETWORK_TRAFFIC_LOCATION,
                StatusBarIconController.ICON_HIDE_LIST);
    }

    /** Initialize the object after construction. */
    public void init() {
        // listen for broadcasts
        IntentFilter filter = new IntentFilter();

        filter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        filter.addAction(Intent.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelecomManager.ACTION_CURRENT_TTY_MODE_CHANGED);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED);
        filter.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        mBroadcastDispatcher.registerReceiverWithHandler(mIntentReceiver, filter, mHandler);
        Observer<Integer> observer = ringer -> mHandler.post(this::updateVolumeZen);

        mRingerModeTracker.getRingerMode().observeForever(observer);
        mRingerModeTracker.getRingerModeInternal().observeForever(observer);

        // listen for user / profile change.
        mUserTracker.addCallback(mUserSwitchListener, mMainExecutor);

        mNetworkPolicyManager.registerListener(mNetworkPolicyListener);

        // TTY status
        updateTTY();

        // bluetooth status
        updateBluetooth();

        // Alarm clock
        mIconController.setIcon(mSlotAlarmClock, R.drawable.stat_sys_alarm, null);
        mIconController.setIconVisibility(mSlotAlarmClock, false);

        // zen
        mIconController.setIcon(mSlotZen, R.drawable.stat_sys_dnd, null);
        mIconController.setIconVisibility(mSlotZen, false);

        // vibrate
        mIconController.setIcon(mSlotVibrate, R.drawable.stat_sys_ringer_vibrate,
                mResources.getString(R.string.accessibility_ringer_vibrate));
        mIconController.setIconVisibility(mSlotVibrate, false);
        // mute
        mIconController.setIcon(mSlotMute, R.drawable.stat_sys_ringer_silent,
                mResources.getString(R.string.accessibility_ringer_silent));
        mIconController.setIconVisibility(mSlotMute, false);
        updateVolumeZen();

        // cast
        mIconController.setIcon(mSlotCast, R.drawable.stat_sys_cast, null);
        mIconController.setIconVisibility(mSlotCast, false);

        // connected display
        mIconController.setIcon(mSlotConnectedDisplay, R.drawable.stat_sys_connected_display, null);
        mIconController.setIconVisibility(mSlotConnectedDisplay, false);

        // hotspot
        mIconController.setIcon(mSlotHotspot, R.drawable.stat_sys_hotspot,
                mResources.getString(R.string.accessibility_status_bar_hotspot));
        mIconController.setIconVisibility(mSlotHotspot, mHotspot.isHotspotEnabled());

        // managed profile
        updateManagedProfile();

        // data saver
        mIconController.setIcon(mSlotDataSaver, R.drawable.stat_sys_data_saver,
                mResources.getString(R.string.accessibility_data_saver_on));
        mIconController.setIconVisibility(mSlotDataSaver, false);


        // sensors off
        mIconController.setIcon(mSlotSensorsOff, R.drawable.stat_sys_sensors_off,
                mResources.getString(R.string.accessibility_sensors_off_active));
        mIconController.setIconVisibility(mSlotSensorsOff,
                mSensorPrivacyController.isSensorPrivacyEnabled());

        // screen record
        mIconController.setIcon(mSlotScreenRecord, R.drawable.stat_sys_screen_record, null);
        mIconController.setIconVisibility(mSlotScreenRecord, false);

        mIconController.setIcon(mSlotNfc, R.drawable.stat_sys_nfc,
                mResources.getString(R.string.accessibility_status_bar_nfc));

        mIconController.setIconVisibility(mSlotNfc, false);
        updateNfc();

        // firewall
        mIconController.setIcon(mSlotFirewall, R.drawable.stat_sys_firewall, null);
        mIconController.setIconVisibility(mSlotFirewall, mFirewallVisible);

        // network traffic
        mShowNetworkTraffic = Settings.Secure.getIntForUser(mContext.getContentResolver(),
            NETWORK_TRAFFIC_LOCATION, 0, UserHandle.USER_CURRENT) == 1;
        updateNetworkTraffic();

        mRotationLockController.addCallback(this);
        mBluetooth.addCallback(this);
        mProvisionedController.addCallback(this);
        mCurrentUserSetup = mProvisionedController.isCurrentUserSetup();
        mZenController.addCallback(this);
        mCast.addCallback(mCastCallback);
        mHotspot.addCallback(mHotspotCallback);
        mNextAlarmController.addCallback(mNextAlarmCallback);
        mDataSaver.addCallback(this);
        mKeyguardStateController.addCallback(this);
        mSensorPrivacyController.addCallback(mSensorPrivacyListener);
        mRecordingController.addCallback(this);
        mJavaAdapter.alwaysCollectFlow(mConnectedDisplayInteractor.getConnectedDisplayState(),
                this::onConnectedDisplayAvailabilityChanged);

        mCommandQueue.addCallback(this);

        // Get initial user setup state
        onUserSetupChanged();
    }

    private String getManagedProfileAccessibilityString() {
        return mDevicePolicyManager.getResources().getString(
                STATUS_BAR_WORK_ICON_ACCESSIBILITY,
                () -> mResources.getString(R.string.accessibility_managed_profile));
    }

    @Override
    public void onZenChanged(int zen) {
        updateVolumeZen();
    }

    @Override
    public void onConfigChanged(ZenModeConfig config) {
        updateVolumeZen();
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case BLUETOOTH_SHOW_BATTERY:
                mShowBluetoothBattery =
                        TunerService.parseIntegerSwitch(newValue, true);
                updateBluetooth();
                break;
            case NETWORK_TRAFFIC_LOCATION:
                mShowNetworkTraffic =
                        TunerService.parseInteger(newValue, 0) == 1;
                updateNetworkTraffic();
                break;
            case StatusBarIconController.ICON_HIDE_LIST:
                ArraySet<String> hideList = StatusBarIconController.getIconHideList(mContext, newValue);
                boolean hideBluetooth = hideList.contains(mSlotBluetooth);
                if (hideBluetooth != mHideBluetooth) {
                    mHideBluetooth = hideBluetooth;
                    updateBluetooth();
                }
                break;
            default:
                break;
        }
    }

    private void updateAlarm() {
        final AlarmClockInfo alarm = mAlarmManager.getNextAlarmClock(mUserTracker.getUserId());
        final boolean hasAlarm = alarm != null && alarm.getTriggerTime() > 0;
        int zen = mZenController.getZen();
        final boolean zenNone = zen == Global.ZEN_MODE_NO_INTERRUPTIONS;
        mIconController.setIcon(mSlotAlarmClock, zenNone ? R.drawable.stat_sys_alarm_dim
                : R.drawable.stat_sys_alarm, buildAlarmContentDescription());
        mIconController.setIconVisibility(mSlotAlarmClock, mCurrentUserSetup && hasAlarm);
    }

    private String buildAlarmContentDescription() {
        if (mNextAlarm == null) {
            return mResources.getString(R.string.status_bar_alarm);
        }

        String skeleton = mDateFormatUtil.is24HourFormat() ? "EHm" : "Ehma";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        String dateString = DateFormat.format(pattern, mNextAlarm.getTriggerTime()).toString();

        return mResources.getString(R.string.accessibility_quick_settings_alarm, dateString);
    }

    private NfcAdapter getAdapter() {
        if (mAdapter == null) {
            try {
                mAdapter = NfcAdapter.getNfcAdapter(mContext);
            } catch (UnsupportedOperationException e) {
                mAdapter = null;
            }
        }
        return mAdapter;
    }

    private final void updateNfc() {
        mNfcVisible =  getAdapter() != null && getAdapter().isEnabled();
        if (mNfcVisible) {
            mIconController.setIconVisibility(mSlotNfc, true);
        } else {
            mIconController.setIconVisibility(mSlotNfc, false);
        }
    }

    private final void updateVolumeZen() {
        boolean zenVisible = false;
        int zenIconId = 0;
        String zenDescription = null;

        boolean vibrateVisible = false;
        boolean muteVisible = false;
        int zen = mZenController.getZen();

        if (DndTile.isVisible(mSharedPreferences) || DndTile.isCombinedIcon(mSharedPreferences)) {
            zenVisible = zen != Global.ZEN_MODE_OFF;
            zenIconId = R.drawable.stat_sys_dnd;
            zenDescription = mResources.getString(R.string.quick_settings_dnd_label);
        } else if (zen == Global.ZEN_MODE_NO_INTERRUPTIONS) {
            zenVisible = true;
            zenIconId = R.drawable.stat_sys_dnd;
            zenDescription = mResources.getString(R.string.interruption_level_none);
        } else if (zen == Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS) {
            zenVisible = true;
            zenIconId = R.drawable.stat_sys_dnd;
            zenDescription = mResources.getString(R.string.interruption_level_priority);
        }

        if (!ZenModeConfig.isZenOverridingRinger(zen, mZenController.getConsolidatedPolicy())) {
            final Integer ringerModeInternal =
                    mRingerModeTracker.getRingerModeInternal().getValue();
            if (ringerModeInternal != null) {
                if (ringerModeInternal == AudioManager.RINGER_MODE_VIBRATE) {
                    vibrateVisible = true;
                } else if (ringerModeInternal == AudioManager.RINGER_MODE_SILENT) {
                    muteVisible = true;
                }
            }
        }

        if (zenVisible) {
            mIconController.setIcon(mSlotZen, zenIconId, zenDescription);
        }
        if (zenVisible != mZenVisible) {
            mIconController.setIconVisibility(mSlotZen, zenVisible);
            mZenVisible = zenVisible;
        }

        if (vibrateVisible != mVibrateVisible) {
            mIconController.setIconVisibility(mSlotVibrate, vibrateVisible);
            mVibrateVisible = vibrateVisible;
        }

        if (muteVisible != mMuteVisible) {
            mIconController.setIconVisibility(mSlotMute, muteVisible);
            mMuteVisible = muteVisible;
        }

        updateAlarm();
    }

    @Override
    public void onBluetoothDevicesChanged() {
        updateBluetooth();
    }

    @Override
    public void onBluetoothStateChange(boolean enabled) {
        updateBluetooth();
    }

    private final void updateBluetooth() {
        String contentDescription =
                mResources.getString(R.string.accessibility_quick_settings_bluetooth_on);
        boolean bluetoothVisible = false;
        int batteryLevel = -1;
        if (mBluetooth != null) {
            if (mBluetooth.isBluetoothConnected()
                    && (mBluetooth.isBluetoothAudioActive()
                    || !mBluetooth.isBluetoothAudioProfileOnly())) {
                bluetoothVisible = mBluetooth.isBluetoothEnabled();
                batteryLevel = mShowBluetoothBattery ? mBluetooth.getBatteryLevel() : -1;
                contentDescription = mResources.getString(
                        R.string.accessibility_bluetooth_connected);
            }
        }

        mIconController.setBluetoothIcon(mSlotBluetooth,
                new BluetoothIconState(!mHideBluetooth && bluetoothVisible, batteryLevel, contentDescription));
    }

    private final void updateNetworkTraffic() {
        mIconController.setNetworkTraffic(mSlotNetworkTraffic, new NetworkTrafficState(mShowNetworkTraffic));
        mIconController.setIconVisibility(mSlotNetworkTraffic, mShowNetworkTraffic);
    }

    private final void updateTTY() {
        if (mTelecomManager == null) {
            updateTTY(TelecomManager.TTY_MODE_OFF);
        } else {
            updateTTY(mTelecomManager.getCurrentTtyMode());
        }
    }

    private final void updateTTY(int currentTtyMode) {
        boolean enabled = currentTtyMode != TelecomManager.TTY_MODE_OFF;

        if (DEBUG) Log.v(TAG, "updateTTY: enabled: " + enabled);

        if (enabled) {
            // TTY is on
            if (DEBUG) Log.v(TAG, "updateTTY: set TTY on");
            mIconController.setIcon(mSlotTty, R.drawable.stat_sys_tty_mode,
                    mResources.getString(R.string.accessibility_tty_enabled));
            mIconController.setIconVisibility(mSlotTty, true);
        } else {
            // TTY is off
            if (DEBUG) Log.v(TAG, "updateTTY: set TTY off");
            mIconController.setIconVisibility(mSlotTty, false);
        }
    }

    private void updateCast() {
        boolean isCasting = false;
        for (CastDevice device : mCast.getCastDevices()) {
            if (device.state == CastDevice.STATE_CONNECTING
                    || device.state == CastDevice.STATE_CONNECTED) {
                isCasting = true;
                break;
            }
        }
        if (DEBUG) Log.v(TAG, "updateCast: isCasting: " + isCasting);
        mHandler.removeCallbacks(mRemoveCastIconRunnable);
        if (isCasting && !mRecordingController.isRecording()) { // screen record has its own icon
            mIconController.setIcon(mSlotCast, R.drawable.stat_sys_cast,
                    mResources.getString(R.string.accessibility_casting));
            mIconController.setIconVisibility(mSlotCast, true);
        } else {
            // don't turn off the screen-record icon for a few seconds, just to make sure the user
            // has seen it
            if (DEBUG) Log.v(TAG, "updateCast: hiding icon in 3 sec...");
            mHandler.postDelayed(mRemoveCastIconRunnable, 3000);
        }
    }

    private void updateManagedProfile() {
        // getLastResumedActivityUserId needs to acquire the AM lock, which may be contended in
        // some cases. Since it doesn't really matter here whether it's updated in this frame
        // or in the next one, we call this method from our UI offload thread.
        mUiBgExecutor.execute(() -> {
            final int userId;
            try {
                userId = ActivityTaskManager.getService().getLastResumedActivityUserId();
                boolean isManagedProfile = mUserManager.isManagedProfile(userId);
                String accessibilityString = getManagedProfileAccessibilityString();
                mMainExecutor.execute(() -> {
                    final boolean showIcon;
                    if (isManagedProfile && (!mKeyguardStateController.isShowing()
                            || mKeyguardStateController.isOccluded())) {
                        showIcon = true;
                        mIconController.setIcon(mSlotManagedProfile,
                                R.drawable.stat_sys_managed_profile_status,
                                accessibilityString);
                    } else {
                        showIcon = false;
                    }
                    if (mManagedProfileIconVisible != showIcon) {
                        mIconController.setIconVisibility(mSlotManagedProfile, showIcon);
                        mManagedProfileIconVisible = showIcon;
                    }
                });
            } catch (RemoteException e) {
                Log.w(TAG, "updateManagedProfile: ", e);
            }
        });
    }

    private void updateFirewall() {
        mUiBgExecutor.execute(() -> {
            try {
                final int uid = ActivityTaskManager.getService().getLastResumedActivityUid();
                if (mLastResumedActivityUid != uid) {
                    mLastResumedActivityUid = uid;
                    try {
                        mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
                    } catch (IllegalArgumentException e) {
                        // Ignore
                    }
                    mConnectivityManager.registerDefaultNetworkCallbackForUid(uid, mNetworkCallback,
                            mHandler);
                }
                final boolean isRestricted =
                        mNetworkPolicyManager.isUidNetworkingBlocked(uid, false /*meteredNetwork*/);
                boolean isLauncher = false;
                List<ResolveInfo> homeActivities =
                        mContext.getPackageManager().queryIntentActivitiesAsUser(
                                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                                        .addCategory(Intent.CATEGORY_DEFAULT),
                                PackageManager.ResolveInfoFlags.of(0), UserHandle.getUserId(uid));
                for (ResolveInfo homeActivity : homeActivities) {
                    if (uid == homeActivity.activityInfo.applicationInfo.uid) {
                        isLauncher = true;
                        break;
                    }
                }
                final boolean finalIsLauncher = isLauncher;
                mHandler.post(() -> {
                    final boolean showIcon;
                    if (!finalIsLauncher && isRestricted && (!mKeyguardStateController.isShowing()
                            || mKeyguardStateController.isOccluded())) {
                        showIcon = true;
                        mIconController.setIcon(mSlotFirewall, R.drawable.stat_sys_firewall, null);
                    } else {
                        showIcon = false;
                    }
                    if (mFirewallVisible != showIcon) {
                        mIconController.setIconVisibility(mSlotFirewall, showIcon);
                        mFirewallVisible = showIcon;
                    }
                });
            } catch (RemoteException e) {
                Log.w(TAG, "updateFirewall: ", e);
            }
        });
    }

    private final ConnectivityManager.NetworkCallback mNetworkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(@NonNull Network network,
                        @NonNull NetworkCapabilities networkCapabilities) {
                    mHandler.post(() -> updateFirewall());
                }
            };

    private final INetworkPolicyListener mNetworkPolicyListener =
            new NetworkPolicyManager.Listener() {
        @Override
        public void onUidPoliciesChanged(int uid, int uidPolicies) {
            mHandler.post(() -> updateFirewall());
        }
    };

    private final UserTracker.Callback mUserSwitchListener =
            new UserTracker.Callback() {
                @Override
                public void onUserChanging(int newUser, Context userContext) {
                    mHandler.post(() -> mUserInfoController.reloadUserInfo());
                }

                @Override
                public void onUserChanged(int newUser, Context userContext) {
                    mHandler.post(() -> {
                        updateAlarm();
                        updateManagedProfile();
                        onUserSetupChanged();
                    });
                }
            };

    private final HotspotController.Callback mHotspotCallback = new HotspotController.Callback() {
        @Override
        public void onHotspotChanged(boolean enabled, int numDevices) {
            mIconController.setIconVisibility(mSlotHotspot, enabled);
        }
    };

    private final CastController.Callback mCastCallback = new CastController.Callback() {
        @Override
        public void onCastDevicesChanged() {
            updateCast();
        }
    };

    private final NextAlarmController.NextAlarmChangeCallback mNextAlarmCallback =
            new NextAlarmController.NextAlarmChangeCallback() {
                @Override
                public void onNextAlarmChanged(AlarmManager.AlarmClockInfo nextAlarm) {
                    mNextAlarm = nextAlarm;
                    updateAlarm();
                }
            };

    private final SensorPrivacyController.OnSensorPrivacyChangedListener mSensorPrivacyListener =
            new SensorPrivacyController.OnSensorPrivacyChangedListener() {
                @Override
                public void onSensorPrivacyChanged(boolean enabled) {
                    mHandler.post(() -> {
                        mIconController.setIconVisibility(mSlotSensorsOff, enabled);
                    });
                }
            };

    @Override
    public void appTransitionStarting(int displayId, long startTime, long duration,
            boolean forced) {
        if (mDisplayId == displayId) {
            updateManagedProfile();
            updateFirewall();
        }
    }

    @Override
    public void appTransitionFinished(int displayId) {
        if (mDisplayId == displayId) {
            updateManagedProfile();
        }
    }

    @Override
    public void onKeyguardShowingChanged() {
        updateManagedProfile();
        updateFirewall();
    }

    @Override
    public void onUserSetupChanged() {
        boolean userSetup = mProvisionedController.isCurrentUserSetup();
        if (mCurrentUserSetup == userSetup) return;
        mCurrentUserSetup = userSetup;
        updateAlarm();
    }

    @Override
    public void onRotationLockStateChanged(boolean rotationLocked, boolean affordanceVisible) {
        boolean portrait = RotationLockTile.isCurrentOrientationLockPortrait(
                mRotationLockController, mResources);
        if (rotationLocked) {
            if (portrait) {
                mIconController.setIcon(mSlotRotate, R.drawable.stat_sys_rotate_portrait,
                        mResources.getString(R.string.accessibility_rotation_lock_on_portrait));
            } else {
                mIconController.setIcon(mSlotRotate, R.drawable.stat_sys_rotate_landscape,
                        mResources.getString(R.string.accessibility_rotation_lock_on_landscape));
            }
            mIconController.setIconVisibility(mSlotRotate, true);
        } else {
            mIconController.setIconVisibility(mSlotRotate, false);
        }
    }

    private void updateHeadsetPlug(Intent intent) {
        boolean connected = intent.getIntExtra("state", 0) != 0;
        boolean hasMic = intent.getIntExtra("microphone", 0) != 0;
        if (connected) {
            String contentDescription = mResources.getString(hasMic
                    ? R.string.accessibility_status_bar_headset
                    : R.string.accessibility_status_bar_headphones);
            mIconController.setIcon(mSlotHeadset, hasMic ? R.drawable.stat_sys_headset_mic
                    : R.drawable.stat_sys_headset, contentDescription);
            mIconController.setIconVisibility(mSlotHeadset, true);
        } else {
            mIconController.setIconVisibility(mSlotHeadset, false);
        }
    }

    @Override
    public void onDataSaverChanged(boolean isDataSaving) {
        mIconController.setIconVisibility(mSlotDataSaver, isDataSaving);
    }

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case Intent.ACTION_SIM_STATE_CHANGED:
                    // Avoid rebroadcast because SysUI is direct boot aware.
                    if (intent.getBooleanExtra(Intent.EXTRA_REBROADCAST_ON_UNLOCK, false)) {
                        break;
                    }
                    break;
                case TelecomManager.ACTION_CURRENT_TTY_MODE_CHANGED:
                    updateTTY(intent.getIntExtra(TelecomManager.EXTRA_CURRENT_TTY_MODE,
                            TelecomManager.TTY_MODE_OFF));
                    break;
                case Intent.ACTION_MANAGED_PROFILE_AVAILABLE:
                case Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE:
                case Intent.ACTION_MANAGED_PROFILE_REMOVED:
                    updateManagedProfile();
                    break;
                case AudioManager.ACTION_HEADSET_PLUG:
                    updateHeadsetPlug(intent);
                    break;
                case NfcAdapter.ACTION_ADAPTER_STATE_CHANGED:
                    updateNfc();
                    break;

            }
        }
    };

    private Runnable mRemoveCastIconRunnable = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.v(TAG, "updateCast: hiding icon NOW");
            mIconController.setIconVisibility(mSlotCast, false);
        }
    };

    // Screen Recording
    @Override
    public void onCountdown(long millisUntilFinished) {
        if (DEBUG) Log.d(TAG, "screenrecord: countdown " + millisUntilFinished);
        int countdown = (int) Math.floorDiv(millisUntilFinished + 500, 1000);
        int resourceId = R.drawable.stat_sys_screen_record;
        String description = Integer.toString(countdown);
        switch (countdown) {
            case 1:
                resourceId = R.drawable.stat_sys_screen_record_1;
                break;
            case 2:
                resourceId = R.drawable.stat_sys_screen_record_2;
                break;
            case 3:
                resourceId = R.drawable.stat_sys_screen_record_3;
                break;
        }
        mIconController.setIcon(mSlotScreenRecord, resourceId, description);
        mIconController.setIconVisibility(mSlotScreenRecord, true);
        // Set as assertive so talkback will announce the countdown
        mIconController.setIconAccessibilityLiveRegion(mSlotScreenRecord,
                View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
    }

    @Override
    public void onCountdownEnd() {
        if (DEBUG) Log.d(TAG, "screenrecord: hiding icon during countdown");
        mHandler.post(() -> mIconController.setIconVisibility(mSlotScreenRecord, false));
        // Reset talkback priority
        mHandler.post(() -> mIconController.setIconAccessibilityLiveRegion(mSlotScreenRecord,
                View.ACCESSIBILITY_LIVE_REGION_NONE));
    }

    @Override
    public void onRecordingStart() {
        if (DEBUG) Log.d(TAG, "screenrecord: showing icon");
        mIconController.setIcon(mSlotScreenRecord,
                R.drawable.stat_sys_screen_record,
                mResources.getString(R.string.screenrecord_ongoing_screen_only));
        mHandler.post(() -> mIconController.setIconVisibility(mSlotScreenRecord, true));
    }

    @Override
    public void onRecordingEnd() {
        // Ensure this is on the main thread
        if (DEBUG) Log.d(TAG, "screenrecord: hiding icon");
        mHandler.post(() -> mIconController.setIconVisibility(mSlotScreenRecord, false));
    }

    private void onConnectedDisplayAvailabilityChanged(ConnectedDisplayInteractor.State state) {
        boolean visible = state != ConnectedDisplayInteractor.State.DISCONNECTED;

        if (DEBUG) {
            Log.d(TAG, "connected_display: " + (visible ? "showing" : "hiding") + " icon");
        }

        mIconController.setIconVisibility(mSlotConnectedDisplay, visible);
    }

    public static class BluetoothIconState {
        public boolean visible;
        public int batteryLevel;
        public String contentDescription;

        public BluetoothIconState(boolean visible, int batteryLevel, String contentDescription) {
            this.visible = visible;
            this.batteryLevel = batteryLevel;
            this.contentDescription = contentDescription;
        }

        @Override
        public String toString() {
            return "BluetoothIconState(visible=" + visible + " batteryLevel=" + batteryLevel + ")";
        }
    }

    public static class NetworkTrafficState {
        public boolean visible;

        public NetworkTrafficState(boolean visible) {
            this.visible = visible;
        }

        @Override
        public String toString() {
            return "NetworkTrafficState(visible=" + visible + ")";
        }
    }
}
