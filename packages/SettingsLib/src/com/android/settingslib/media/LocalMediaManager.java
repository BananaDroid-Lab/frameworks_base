/*
 * Copyright 2018 The Android Open Source Project
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
package com.android.settingslib.media;

import android.app.Notification;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.IntDef;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * LocalMediaManager provide interface to get MediaDevice list and transfer media to MediaDevice.
 */
public class LocalMediaManager implements BluetoothCallback {
    private static final Comparator<MediaDevice> COMPARATOR = Comparator.naturalOrder();
    private static final String TAG = "LocalMediaManager";
    private static final int MAX_DISCONNECTED_DEVICE_NUM = 5;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({MediaDeviceState.STATE_CONNECTED,
            MediaDeviceState.STATE_CONNECTING,
            MediaDeviceState.STATE_DISCONNECTED,
            MediaDeviceState.STATE_CONNECTING_FAILED})
    public @interface MediaDeviceState {
        int STATE_CONNECTED = 0;
        int STATE_CONNECTING = 1;
        int STATE_DISCONNECTED = 2;
        int STATE_CONNECTING_FAILED = 3;
    }

    private final Collection<DeviceCallback> mCallbacks = new CopyOnWriteArrayList<>();
    @VisibleForTesting
    final MediaDeviceCallback mMediaDeviceCallback = new MediaDeviceCallback();

    private Context mContext;
    private LocalBluetoothManager mLocalBluetoothManager;
    private InfoMediaManager mInfoMediaManager;
    private String mPackageName;
    private MediaDevice mOnTransferBluetoothDevice;

    @VisibleForTesting
    List<MediaDevice> mMediaDevices = new CopyOnWriteArrayList<>();
    @VisibleForTesting
    List<MediaDevice> mDisconnectedMediaDevices = new CopyOnWriteArrayList<>();
    @VisibleForTesting
    MediaDevice mPhoneDevice;
    @VisibleForTesting
    MediaDevice mCurrentConnectedDevice;
    @VisibleForTesting
    DeviceAttributeChangeCallback mDeviceAttributeChangeCallback =
            new DeviceAttributeChangeCallback();
    @VisibleForTesting
    BluetoothAdapter mBluetoothAdapter;

    /**
     * Register to start receiving callbacks for MediaDevice events.
     */
    public void registerCallback(DeviceCallback callback) {
        mCallbacks.add(callback);
    }

    /**
     * Unregister to stop receiving callbacks for MediaDevice events
     */
    public void unregisterCallback(DeviceCallback callback) {
        mCallbacks.remove(callback);
    }

    /**
     * Creates a LocalMediaManager with references to given managers.
     *
     * It will obtain a {@link LocalBluetoothManager} by calling
     * {@link LocalBluetoothManager#getInstance} and create an {@link InfoMediaManager} passing
     * that bluetooth manager.
     *
     * It will use {@link BluetoothAdapter#getDefaultAdapter()] for setting the bluetooth adapter.
     */
    public LocalMediaManager(Context context, String packageName, Notification notification) {
        mContext = context;
        mPackageName = packageName;
        mLocalBluetoothManager =
                LocalBluetoothManager.getInstance(context, /* onInitCallback= */ null);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mLocalBluetoothManager == null) {
            Log.e(TAG, "Bluetooth is not supported on this device");
            return;
        }

        mInfoMediaManager =
                new InfoMediaManager(context, packageName, notification, mLocalBluetoothManager);
    }

    /**
     * Creates a LocalMediaManager with references to given managers.
     *
     * It will use {@link BluetoothAdapter#getDefaultAdapter()] for setting the bluetooth adapter.
     */
    public LocalMediaManager(Context context, LocalBluetoothManager localBluetoothManager,
            InfoMediaManager infoMediaManager, String packageName) {
        mContext = context;
        mLocalBluetoothManager = localBluetoothManager;
        mInfoMediaManager = infoMediaManager;
        mPackageName = packageName;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * Connect the MediaDevice to transfer media
     * @param connectDevice the MediaDevice
     */
    public void connectDevice(MediaDevice connectDevice) {
        final MediaDevice device = getMediaDeviceById(mMediaDevices, connectDevice.getId());
        if (device instanceof BluetoothMediaDevice) {
            final CachedBluetoothDevice cachedDevice =
                    ((BluetoothMediaDevice) device).getCachedDevice();
            if (!cachedDevice.isConnected() && !cachedDevice.isBusy()) {
                mOnTransferBluetoothDevice = connectDevice;
                cachedDevice.connect();
                return;
            }
        }

        if (device == mCurrentConnectedDevice) {
            Log.d(TAG, "connectDevice() this device all ready connected! : " + device.getName());
            return;
        }

        if (mCurrentConnectedDevice != null) {
            mCurrentConnectedDevice.disconnect();
        }

        device.setState(MediaDeviceState.STATE_CONNECTING);
        if (TextUtils.isEmpty(mPackageName)) {
            mInfoMediaManager.connectDeviceWithoutPackageName(device);
        } else {
            device.connect();
        }
    }

    void dispatchSelectedDeviceStateChanged(MediaDevice device, @MediaDeviceState int state) {
        for (DeviceCallback callback : getCallbacks()) {
            callback.onSelectedDeviceStateChanged(device, state);
        }
    }

    /**
     * Start scan connected MediaDevice
     */
    public void startScan() {
        mMediaDevices.clear();
        mInfoMediaManager.registerCallback(mMediaDeviceCallback);
        mInfoMediaManager.startScan();
    }

    void dispatchDeviceListUpdate() {
        //TODO(b/149260820): Use new rule to rank device once device type api is ready.
        for (DeviceCallback callback : getCallbacks()) {
            callback.onDeviceListUpdate(new ArrayList<>(mMediaDevices));
        }
    }

    void dispatchDeviceAttributesChanged() {
        for (DeviceCallback callback : getCallbacks()) {
            callback.onDeviceAttributesChanged();
        }
    }

    void dispatchOnRequestFailed(int reason) {
        for (DeviceCallback callback : getCallbacks()) {
            callback.onRequestFailed(reason);
        }
    }

    /**
     * Stop scan MediaDevice
     */
    public void stopScan() {
        mInfoMediaManager.unregisterCallback(mMediaDeviceCallback);
        mInfoMediaManager.stopScan();
        unRegisterDeviceAttributeChangeCallback();
    }

    /**
     * Find the MediaDevice through id.
     *
     * @param devices the list of MediaDevice
     * @param id the unique id of MediaDevice
     * @return MediaDevice
     */
    public MediaDevice getMediaDeviceById(List<MediaDevice> devices, String id) {
        for (MediaDevice mediaDevice : devices) {
            if (TextUtils.equals(mediaDevice.getId(), id)) {
                return mediaDevice;
            }
        }
        Log.i(TAG, "getMediaDeviceById() can't found device");
        return null;
    }

    /**
     * Find the MediaDevice from all media devices by id.
     *
     * @param id the unique id of MediaDevice
     * @return MediaDevice
     */
    public MediaDevice getMediaDeviceById(String id) {
        for (MediaDevice mediaDevice : mMediaDevices) {
            if (TextUtils.equals(mediaDevice.getId(), id)) {
                return mediaDevice;
            }
        }
        Log.i(TAG, "Unable to find device " + id);
        return null;
    }

    /**
     * Find the current connected MediaDevice.
     *
     * @return MediaDevice
     */
    public MediaDevice getCurrentConnectedDevice() {
        return mCurrentConnectedDevice;
    }

    /**
     * Find the active MediaDevice.
     *
     * @param type the media device type.
     * @return MediaDevice list
     */
    public List<MediaDevice> getActiveMediaDevice(@MediaDevice.MediaDeviceType int type) {
        final List<MediaDevice> devices = new ArrayList<>();
        for (MediaDevice device : mMediaDevices) {
            if (type == device.mType && device.getClientPackageName() != null) {
                devices.add(device);
            }
        }
        return devices;
    }

    /**
     * Add a MediaDevice to let it play current media.
     *
     * @param device MediaDevice
     * @return If add device successful return {@code true}, otherwise return {@code false}
     */
    public boolean addDeviceToPlayMedia(MediaDevice device) {
        return mInfoMediaManager.addDeviceToPlayMedia(device);
    }

    /**
     * Remove a {@code device} from current media.
     *
     * @param device MediaDevice
     * @return If device stop successful return {@code true}, otherwise return {@code false}
     */
    public boolean removeDeviceFromPlayMedia(MediaDevice device) {
        return mInfoMediaManager.removeDeviceFromPlayMedia(device);
    }

    /**
     * Get the MediaDevice list that can be added to current media.
     *
     * @return list of MediaDevice
     */
    public List<MediaDevice> getSelectableMediaDevice() {
        return mInfoMediaManager.getSelectableMediaDevice();
    }

    /**
     * Release session to stop playing media on MediaDevice.
     */
    public boolean releaseSession() {
        return mInfoMediaManager.releaseSession();
    }

    /**
     * Get the MediaDevice list that has been selected to current media.
     *
     * @return list of MediaDevice
     */
    public List<MediaDevice> getSelectedMediaDevice() {
        return mInfoMediaManager.getSelectedMediaDevice();
    }

    /**
     * Adjust the volume of session.
     *
     * @param volume the value of volume
     */
    public void adjustSessionVolume(int volume) {
        mInfoMediaManager.adjustSessionVolume(volume);
    }

    /**
     * Gets the maximum volume of the {@link android.media.RoutingSessionInfo}.
     *
     * @return  maximum volume of the session, and return -1 if not found.
     */
    public int getSessionVolumeMax() {
        return mInfoMediaManager.getSessionVolumeMax();
    }

    /**
     * Gets the current volume of the {@link android.media.RoutingSessionInfo}.
     *
     * @return current volume of the session, and return -1 if not found.
     */
    public int getSessionVolume() {
        return mInfoMediaManager.getSessionVolume();
    }

    /**
     * Gets the user-visible name of the {@link android.media.RoutingSessionInfo}.
     *
     * @return current name of the session, and return {@code null} if not found.
     */
    public CharSequence getSessionName() {
        return mInfoMediaManager.getSessionName();
    }

    private MediaDevice updateCurrentConnectedDevice() {
        MediaDevice phoneMediaDevice = null;
        for (MediaDevice device : mMediaDevices) {
            if (device instanceof  BluetoothMediaDevice) {
                if (isActiveDevice(((BluetoothMediaDevice) device).getCachedDevice())) {
                    return device;
                }
            } else if (device instanceof PhoneMediaDevice) {
                phoneMediaDevice = device;
            }
        }
        return mMediaDevices.contains(phoneMediaDevice) ? phoneMediaDevice : null;
    }

    private boolean isActiveDevice(CachedBluetoothDevice device) {
        return device.isActiveDevice(BluetoothProfile.A2DP)
                || device.isActiveDevice(BluetoothProfile.HEARING_AID);
    }

    private Collection<DeviceCallback> getCallbacks() {
        return new CopyOnWriteArrayList<>(mCallbacks);
    }

    class MediaDeviceCallback implements MediaManager.MediaDeviceCallback {
        @Override
        public void onDeviceAdded(MediaDevice device) {
            if (!mMediaDevices.contains(device)) {
                mMediaDevices.add(device);
                dispatchDeviceListUpdate();
            }
        }

        @Override
        public void onDeviceListAdded(List<MediaDevice> devices) {
            mMediaDevices.clear();
            mMediaDevices.addAll(devices);
            mMediaDevices.addAll(buildDisconnectedBluetoothDevice());

            final MediaDevice infoMediaDevice = mInfoMediaManager.getCurrentConnectedDevice();
            mCurrentConnectedDevice = infoMediaDevice != null
                    ? infoMediaDevice : updateCurrentConnectedDevice();
            dispatchDeviceListUpdate();
            if (mOnTransferBluetoothDevice != null && mOnTransferBluetoothDevice.isConnected()) {
                connectDevice(mOnTransferBluetoothDevice);
                mOnTransferBluetoothDevice = null;
            }
        }

        private List<MediaDevice> buildDisconnectedBluetoothDevice() {
            final List<BluetoothDevice> bluetoothDevices =
                    mBluetoothAdapter.getMostRecentlyConnectedDevices();
            final CachedBluetoothDeviceManager cachedDeviceManager =
                    mLocalBluetoothManager.getCachedDeviceManager();

            final List<CachedBluetoothDevice> cachedBluetoothDeviceList = new ArrayList<>();
            int deviceCount = 0;
            for (BluetoothDevice device : bluetoothDevices) {
                final CachedBluetoothDevice cachedDevice =
                        cachedDeviceManager.findDevice(device);
                if (cachedDevice != null) {
                    if (cachedDevice.getBondState() == BluetoothDevice.BOND_BONDED
                            && !cachedDevice.isConnected()) {
                        deviceCount++;
                        cachedBluetoothDeviceList.add(cachedDevice);
                        if (deviceCount >= MAX_DISCONNECTED_DEVICE_NUM) {
                            break;
                        }
                    }
                }
            }

            unRegisterDeviceAttributeChangeCallback();
            mDisconnectedMediaDevices.clear();
            for (CachedBluetoothDevice cachedDevice : cachedBluetoothDeviceList) {
                final MediaDevice mediaDevice = new BluetoothMediaDevice(mContext,
                        cachedDevice,
                        null, null, mPackageName);
                if (!mMediaDevices.contains(mediaDevice)) {
                    cachedDevice.registerCallback(mDeviceAttributeChangeCallback);
                    mDisconnectedMediaDevices.add(mediaDevice);
                }
            }
            return new ArrayList<>(mDisconnectedMediaDevices);
        }

        @Override
        public void onDeviceRemoved(MediaDevice device) {
            if (mMediaDevices.contains(device)) {
                mMediaDevices.remove(device);
                dispatchDeviceListUpdate();
            }
        }

        @Override
        public void onDeviceListRemoved(List<MediaDevice> devices) {
            mMediaDevices.removeAll(devices);
            dispatchDeviceListUpdate();
        }

        @Override
        public void onConnectedDeviceChanged(String id) {
            MediaDevice connectDevice = getMediaDeviceById(mMediaDevices, id);
            connectDevice = connectDevice != null
                    ? connectDevice : updateCurrentConnectedDevice();
            if (connectDevice != null) {
                connectDevice.setState(MediaDeviceState.STATE_CONNECTED);
            }
            if (connectDevice == mCurrentConnectedDevice) {
                Log.d(TAG, "onConnectedDeviceChanged() this device all ready connected!");
                return;
            }
            mCurrentConnectedDevice = connectDevice;
            dispatchSelectedDeviceStateChanged(mCurrentConnectedDevice,
                    MediaDeviceState.STATE_CONNECTED);
        }

        @Override
        public void onDeviceAttributesChanged() {
            dispatchDeviceAttributesChanged();
        }

        @Override
        public void onRequestFailed(int reason) {
            for (MediaDevice device : mMediaDevices) {
                if (device.getState() == MediaDeviceState.STATE_CONNECTING) {
                    device.setState(MediaDeviceState.STATE_CONNECTING_FAILED);
                }
            }
            dispatchOnRequestFailed(reason);
        }
    }

    private void unRegisterDeviceAttributeChangeCallback() {
        for (MediaDevice device : mDisconnectedMediaDevices) {
            ((BluetoothMediaDevice) device).getCachedDevice()
                    .unregisterCallback(mDeviceAttributeChangeCallback);
        }
    }

    /**
     * Callback for notifying device information updating
     */
    public interface DeviceCallback {
        /**
         * Callback for notifying device list updated.
         *
         * @param devices MediaDevice list
         */
        default void onDeviceListUpdate(List<MediaDevice> devices) {};

        /**
         * Callback for notifying the connected device is changed.
         *
         * @param device the changed connected MediaDevice
         * @param state the current MediaDevice state, the possible values are:
         * {@link MediaDeviceState#STATE_CONNECTED},
         * {@link MediaDeviceState#STATE_CONNECTING},
         * {@link MediaDeviceState#STATE_DISCONNECTED}
         */
        default void onSelectedDeviceStateChanged(MediaDevice device,
                @MediaDeviceState int state) {};

        /**
         * Callback for notifying the device attributes is changed.
         */
        default void onDeviceAttributesChanged() {};

        /**
         * Callback for notifying that transferring is failed.
         *
         * @param reason the reason that the request has failed. Can be one of followings:
         * {@link android.media.MediaRoute2ProviderService#REASON_UNKNOWN_ERROR},
         * {@link android.media.MediaRoute2ProviderService#REASON_REJECTED},
         * {@link android.media.MediaRoute2ProviderService#REASON_NETWORK_ERROR},
         * {@link android.media.MediaRoute2ProviderService#REASON_ROUTE_NOT_AVAILABLE},
         * {@link android.media.MediaRoute2ProviderService#REASON_INVALID_COMMAND},
         */
        default void onRequestFailed(int reason){};
    }

    /**
     * This callback is for update {@link BluetoothMediaDevice} summary when
     * {@link CachedBluetoothDevice} connection state is changed.
     */
    @VisibleForTesting
    class DeviceAttributeChangeCallback implements CachedBluetoothDevice.Callback {

        @Override
        public void onDeviceAttributesChanged() {
            dispatchDeviceAttributesChanged();
        }
    }
}
