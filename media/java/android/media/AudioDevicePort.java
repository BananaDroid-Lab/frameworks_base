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

package android.media;

import android.annotation.NonNull;
import android.compat.annotation.UnsupportedAppUsage;
import android.os.Build;

import java.util.Arrays;
import java.util.List;

/**
 * The AudioDevicePort is a specialized type of AudioPort
 * describing an input (e.g microphone) or output device (e.g speaker)
 * of the system.
 * An AudioDevicePort is an AudioPort controlled by the audio HAL, almost always a physical
 * device at the boundary of the audio system.
 * In addition to base audio port attributes, the device descriptor contains:
 * - the device type (e.g AudioManager.DEVICE_OUT_SPEAKER)
 * - the device address (e.g MAC adddress for AD2P sink).
 * @see AudioPort
 * @hide
 */

public class AudioDevicePort extends AudioPort {

    private final int mType;
    private final String mAddress;
    private final int[] mEncapsulationModes;
    private final int[] mEncapsulationMetadataTypes;

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    AudioDevicePort(AudioHandle handle, String deviceName,
            int[] samplingRates, int[] channelMasks, int[] channelIndexMasks,
            int[] formats, AudioGain[] gains, int type, String address, int[] encapsulationModes,
            @AudioTrack.EncapsulationMetadataType int[] encapsulationMetadataTypes) {
        super(handle,
             (AudioManager.isInputDevice(type) == true)  ?
                        AudioPort.ROLE_SOURCE : AudioPort.ROLE_SINK,
             deviceName, samplingRates, channelMasks, channelIndexMasks, formats, gains);
        mType = type;
        mAddress = address;
        mEncapsulationModes = encapsulationModes;
        mEncapsulationMetadataTypes = encapsulationMetadataTypes;
    }

    AudioDevicePort(AudioHandle handle, String deviceName, List<AudioProfile> profiles,
            AudioGain[] gains, int type, String address, int[] encapsulationModes,
              @AudioTrack.EncapsulationMetadataType int[] encapsulationMetadataTypes) {
        super(handle,
                AudioManager.isInputDevice(type) ? AudioPort.ROLE_SOURCE : AudioPort.ROLE_SINK,
                deviceName, profiles, gains);
        mType = type;
        mAddress = address;
        mEncapsulationModes = encapsulationModes;
        mEncapsulationMetadataTypes = encapsulationMetadataTypes;
    }

    /**
     * Get the device type (e.g AudioManager.DEVICE_OUT_SPEAKER)
     */
    @UnsupportedAppUsage
    public int type() {
        return mType;
    }

    /**
     * Get the device address. Address format varies with the device type.
     * - USB devices ({@link AudioManager#DEVICE_OUT_USB_DEVICE},
     * {@link AudioManager#DEVICE_IN_USB_DEVICE}) use an address composed of the ALSA card number
     * and device number: "card=2;device=1"
     * - Bluetooth devices ({@link AudioManager#DEVICE_OUT_BLUETOOTH_SCO},
     * {@link AudioManager#DEVICE_OUT_BLUETOOTH_SCO},
     * {@link AudioManager#DEVICE_OUT_BLUETOOTH_A2DP}),
     * {@link AudioManager#DEVICE_OUT_BLE_HEADSET}, {@link AudioManager#DEVICE_OUT_BLE_SPEAKER})
     * use the MAC address of the bluetooth device in the form "00:11:22:AA:BB:CC" as reported by
     * {@link BluetoothDevice#getAddress()}.
     * - Deivces that do not have an address will indicate an empty string "".
     */
    public String address() {
        return mAddress;
    }

    /**
     * Get supported encapsulation modes.
     */
    public @NonNull @AudioTrack.EncapsulationMode int[] encapsulationModes() {
        if (mEncapsulationModes == null) {
            return new int[0];
        }
        return Arrays.stream(mEncapsulationModes).boxed()
                .filter(mode -> mode != AudioTrack.ENCAPSULATION_MODE_HANDLE)
                .mapToInt(Integer::intValue).toArray();
    }

    /**
     * Get supported encapsulation metadata types.
     */
    public @NonNull @AudioTrack.EncapsulationMetadataType int[] encapsulationMetadataTypes() {
        if (mEncapsulationMetadataTypes == null) {
            return new int[0];
        }
        int[] encapsulationMetadataTypes = new int[mEncapsulationMetadataTypes.length];
        System.arraycopy(mEncapsulationMetadataTypes, 0,
                         encapsulationMetadataTypes, 0, mEncapsulationMetadataTypes.length);
        return encapsulationMetadataTypes;
    }

    /**
     * Build a specific configuration of this audio device port for use by methods
     * like AudioManager.connectAudioPatch().
     */
    public AudioDevicePortConfig buildConfig(int samplingRate, int channelMask, int format,
                                          AudioGainConfig gain) {
        return new AudioDevicePortConfig(this, samplingRate, channelMask, format, gain);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof AudioDevicePort)) {
            return false;
        }
        AudioDevicePort other = (AudioDevicePort)o;
        if (mType != other.type()) {
            return false;
        }
        if (mAddress == null && other.address() != null) {
            return false;
        }
        if (!mAddress.equals(other.address())) {
            return false;
        }
        return super.equals(o);
    }

    @Override
    public String toString() {
        String type = (mRole == ROLE_SOURCE ?
                            AudioSystem.getInputDeviceName(mType) :
                            AudioSystem.getOutputDeviceName(mType));
        return "{" + super.toString()
                + ", mType: " + type
                + ", mAddress: " + mAddress
                + "}";
    }
}
