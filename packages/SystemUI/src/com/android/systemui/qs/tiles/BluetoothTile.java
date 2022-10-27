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

package com.android.systemui.qs.tiles;

import static com.android.systemui.util.PluralMessageFormaterKt.icuMessageFormat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.text.TextUtils;
import android.view.View;
import android.widget.Switch;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settingslib.Utils;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.qs.tiles.dialog.BluetoothDialogFactory;
import com.android.systemui.statusbar.policy.BluetoothController;

import java.util.List;

import javax.inject.Inject;

/** Quick settings tile: Bluetooth **/
public class BluetoothTile extends QSTileImpl<BooleanState> {
    private static final Intent BLUETOOTH_SETTINGS = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);

    private final Handler mHandler;
    private final BluetoothController mController;
    private final BluetoothDialogFactory mBluetoothDialogFactory;

    @Inject
    public BluetoothTile(
            QSHost host,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            BluetoothController bluetoothController,
            BluetoothDialogFactory bluetoothDialogFactory
    ) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mHandler = mainHandler;
        mController = bluetoothController;
        mBluetoothDialogFactory = bluetoothDialogFactory;
        mController.observe(getLifecycle(), mCallback);
    }

    @Override
    public BooleanState newTileState() {
        BooleanState s = new BooleanState();
        s.forceExpandIcon = true;
        return s;
    }

    @Override
    protected void handleClick(@Nullable View view) {
        mHandler.post(() -> mBluetoothDialogFactory.create(true, view));
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
    }

    @Override
    protected void handleSecondaryClick(@Nullable View view) {
        if (!mController.canConfigBluetooth()) {
            mActivityStarter.postStartActivityDismissingKeyguard(
                    new Intent(Settings.ACTION_BLUETOOTH_SETTINGS), 0);
            return;
        }
        if (!mState.value) {
            mController.setBluetoothEnabled(true);
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_bluetooth_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        checkIfRestrictionEnforcedByAdminOnly(state, UserManager.DISALLOW_BLUETOOTH);
        final boolean transientEnabling = arg == ARG_SHOW_TRANSIENT_ENABLING;
        final boolean enabled = transientEnabling || mController.isBluetoothEnabled();
        final boolean connected = mController.isBluetoothConnected();
        final boolean connecting = mController.isBluetoothConnecting();
        state.isTransient = transientEnabling || connecting ||
                mController.getBluetoothState() == BluetoothAdapter.STATE_TURNING_ON;
        state.dualTarget = true;
        state.value = enabled;
        if (state.slash == null) {
            state.slash = new SlashState();
        }
        state.slash.isSlashed = !enabled;
        state.label = mContext.getString(R.string.quick_settings_bluetooth_label);
        state.secondaryLabel = TextUtils.emptyIfNull(
                getSecondaryLabel(enabled, connecting, connected, state.isTransient));
        state.contentDescription = mContext.getString(
                R.string.accessibility_quick_settings_bluetooth);
        state.stateDescription = "";

        if (enabled) {
            if (connected) {
                state.icon = ResourceIcon.get(R.drawable.qs_bluetooth_icon_on);
                if (!TextUtils.isEmpty(mController.getConnectedDeviceName())) {
                    state.label = mController.getConnectedDeviceName();
                }
                state.stateDescription =
                        mContext.getString(R.string.accessibility_bluetooth_name, state.label)
                                + ", " + state.secondaryLabel;
            } else if (state.isTransient) {
                state.icon = ResourceIcon.get(
                        R.drawable.qs_bluetooth_icon_search);
                state.stateDescription = state.secondaryLabel;
            } else {
                state.icon =
                        ResourceIcon.get(R.drawable.qs_bluetooth_icon_off);
                state.stateDescription = mContext.getString(R.string.accessibility_not_connected);
            }
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.icon = ResourceIcon.get(R.drawable.qs_bluetooth_icon_off);
            state.state = Tile.STATE_INACTIVE;
        }

        state.expandedAccessibilityClassName = Switch.class.getName();
    }

    /**
     * Returns the secondary label to use for the given bluetooth connection in the form of the
     * battery level or bluetooth profile name. If the bluetooth is disabled, there's no connected
     * devices, or we can't map the bluetooth class to a profile, this instead returns {@code null}.
     * @param enabled whether bluetooth is enabled
     * @param connecting whether bluetooth is connecting to a device
     * @param connected whether there's a device connected via bluetooth
     * @param isTransient whether bluetooth is currently in a transient state turning on
     */
    @Nullable
    private String getSecondaryLabel(boolean enabled, boolean connecting, boolean connected,
            boolean isTransient) {
        if (connecting) {
            return mContext.getString(R.string.quick_settings_connecting);
        }
        if (isTransient) {
            return mContext.getString(R.string.quick_settings_bluetooth_secondary_label_transient);
        }

        List<CachedBluetoothDevice> connectedDevices = mController.getConnectedDevices();
        if (enabled && connected && !connectedDevices.isEmpty()) {
            if (connectedDevices.size() > 1) {
                return icuMessageFormat(mContext.getResources(),
                        R.string.quick_settings_hotspot_secondary_label_num_devices,
                        connectedDevices.size());
            }

            CachedBluetoothDevice lastDevice = connectedDevices.get(0);
            final int batteryLevel = lastDevice.getBatteryLevel();

            if (batteryLevel > BluetoothDevice.BATTERY_LEVEL_UNKNOWN) {
                return mContext.getString(
                        R.string.quick_settings_bluetooth_secondary_label_battery_level,
                        Utils.formatPercentage(batteryLevel));

            } else {
                final BluetoothClass bluetoothClass = lastDevice.getBtClass();
                if (bluetoothClass != null) {
                    if (lastDevice.isHearingAidDevice()) {
                        return mContext.getString(
                                R.string.quick_settings_bluetooth_secondary_label_hearing_aids);
                    } else if (bluetoothClass.doesClassMatch(BluetoothClass.PROFILE_A2DP)) {
                        return mContext.getString(
                                R.string.quick_settings_bluetooth_secondary_label_audio);
                    } else if (bluetoothClass.doesClassMatch(BluetoothClass.PROFILE_HEADSET)) {
                        return mContext.getString(
                                R.string.quick_settings_bluetooth_secondary_label_headset);
                    } else if (bluetoothClass.doesClassMatch(BluetoothClass.PROFILE_HID)) {
                        return mContext.getString(
                                R.string.quick_settings_bluetooth_secondary_label_input);
                    }
                }
            }
        }

        return null;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_BLUETOOTH;
    }

    @Override
    public boolean isAvailable() {
        return mController.isBluetoothSupported();
    }

    private final BluetoothController.Callback mCallback = new BluetoothController.Callback() {
        @Override
        public void onBluetoothStateChange(boolean enabled) {
            refreshState();
        }

        @Override
        public void onBluetoothDevicesChanged() {
            refreshState();
        }
    };
}
