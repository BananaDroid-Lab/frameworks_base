/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.net.wifi;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.net.MacAddress;
import android.net.wifi.util.SdkLevelUtil;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.Preconditions;

import java.util.Objects;

/**
 * A class representing information about SoftAp.
 * {@see WifiManager}
 *
 * @hide
 */
@SystemApi
public final class SoftApInfo implements Parcelable {

    /**
     * AP Channel bandwidth is invalid.
     *
     * @see #getBandwidth()
     */
    public static final int CHANNEL_WIDTH_INVALID = 0;

    /**
     * AP Channel bandwidth is 20 MHZ but no HT.
     *
     * @see #getBandwidth()
     */
    public static final int CHANNEL_WIDTH_20MHZ_NOHT = 1;

    /**
     * AP Channel bandwidth is 20 MHZ.
     *
     * @see #getBandwidth()
     */
    public static final int CHANNEL_WIDTH_20MHZ = 2;

    /**
     * AP Channel bandwidth is 40 MHZ.
     *
     * @see #getBandwidth()
     */
    public static final int CHANNEL_WIDTH_40MHZ = 3;

    /**
     * AP Channel bandwidth is 80 MHZ.
     *
     * @see #getBandwidth()
     */
    public static final int CHANNEL_WIDTH_80MHZ = 4;

    /**
     * AP Channel bandwidth is 160 MHZ, but 80MHZ + 80MHZ.
     *
     * @see #getBandwidth()
     */
    public static final int CHANNEL_WIDTH_80MHZ_PLUS_MHZ = 5;

    /**
     * AP Channel bandwidth is 160 MHZ.
     *
     * @see #getBandwidth()
     */
    public static final int CHANNEL_WIDTH_160MHZ = 6;

    /** The frequency which AP resides on.  */
    private int mFrequency = 0;

    @WifiAnnotations.Bandwidth
    private int mBandwidth = CHANNEL_WIDTH_INVALID;

    /** The MAC Address which AP resides on. */
    @Nullable
    private MacAddress mBssid;

    /**
     * The operational mode of the AP.
     */
    private @WifiAnnotations.WifiStandard int mWifiStandard = ScanResult.WIFI_STANDARD_UNKNOWN;

    /**
     * Get the frequency which AP resides on.
     */
    public int getFrequency() {
        return mFrequency;
    }

    /**
     * Set the frequency which AP resides on.
     * @hide
     */
    public void setFrequency(int freq) {
        mFrequency = freq;
    }

    /**
     * Get AP Channel bandwidth.
     *
     * @return One of {@link #CHANNEL_WIDTH_20MHZ}, {@link #CHANNEL_WIDTH_40MHZ},
     * {@link #CHANNEL_WIDTH_80MHZ}, {@link #CHANNEL_WIDTH_160MHZ},
     * {@link #CHANNEL_WIDTH_80MHZ_PLUS_MHZ} or {@link #CHANNEL_WIDTH_INVALID}.
     */
    @WifiAnnotations.Bandwidth
    public int getBandwidth() {
        return mBandwidth;
    }

    /**
     * Set AP Channel bandwidth.
     * @hide
     */
    public void setBandwidth(@WifiAnnotations.Bandwidth int bandwidth) {
        mBandwidth = bandwidth;
    }

    /**
     * Get the MAC address (BSSID) of the AP. Null when AP disabled.
     */
    @Nullable
    public MacAddress getBssid() {
        if (!SdkLevelUtil.isAtLeastS()) {
            throw new UnsupportedOperationException();
        }
        return mBssid;
    }

    /**
      * Set the MAC address which AP resides on.
      * <p>
      * <li>If not set, defaults to null.</li>
      * @param bssid BSSID, The caller is responsible for avoiding collisions.
      * @throws IllegalArgumentException when the given BSSID is the all-zero or broadcast MAC
      *                                  address.
      *
      * @hide
      */
    public void setBssid(@Nullable MacAddress bssid) {
        if (bssid != null) {
            Preconditions.checkArgument(!bssid.equals(WifiManager.ALL_ZEROS_MAC_ADDRESS));
            Preconditions.checkArgument(!bssid.equals(MacAddress.BROADCAST_ADDRESS));
        }
        mBssid = bssid;
    }

    /**
     * Set the operational mode of the AP.
     *
     * @param wifiStandard values from {@link ScanResult}'s {@code WIFI_STANDARD_}
     * @hide
     */
    public void setWifiStandard(@WifiAnnotations.WifiStandard int wifiStandard) {
        mWifiStandard = wifiStandard;
    }

    /**
     * Get the operational mode of the AP.
     * @return valid values from {@link ScanResult}'s {@code WIFI_STANDARD_}
     */
    public @WifiAnnotations.WifiStandard int getWifiStandard() {
        if (!SdkLevelUtil.isAtLeastS()) {
            throw new UnsupportedOperationException();
        }
        return mWifiStandard;
    }

    /**
     * @hide
     */
    public SoftApInfo(@Nullable SoftApInfo source) {
        if (source != null) {
            mFrequency = source.mFrequency;
            mBandwidth = source.mBandwidth;
            mBssid = source.mBssid;
            mWifiStandard = source.mWifiStandard;
        }
    }

    /**
     * @hide
     */
    public SoftApInfo() {
    }

    @Override
    /** Implement the Parcelable interface. */
    public int describeContents() {
        return 0;
    }

    @Override
    /** Implement the Parcelable interface */
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mFrequency);
        dest.writeInt(mBandwidth);
        dest.writeParcelable(mBssid, flags);
        dest.writeInt(mWifiStandard);
    }

    @NonNull
    /** Implement the Parcelable interface */
    public static final Creator<SoftApInfo> CREATOR = new Creator<SoftApInfo>() {
        public SoftApInfo createFromParcel(Parcel in) {
            SoftApInfo info = new SoftApInfo();
            info.mFrequency = in.readInt();
            info.mBandwidth = in.readInt();
            info.mBssid = in.readParcelable(MacAddress.class.getClassLoader());
            info.mWifiStandard = in.readInt();
            return info;
        }

        public SoftApInfo[] newArray(int size) {
            return new SoftApInfo[size];
        }
    };

    @NonNull
    @Override
    public String toString() {
        StringBuilder sbuf = new StringBuilder();
        sbuf.append("SoftApInfo{");
        sbuf.append("bandwidth= ").append(mBandwidth);
        sbuf.append(", frequency= ").append(mFrequency);
        if (mBssid != null) sbuf.append(",bssid=").append(mBssid.toString());
        sbuf.append(", wifiStandard= ").append(mWifiStandard);
        sbuf.append("}");
        return sbuf.toString();
    }

    @Override
    public boolean equals(@NonNull Object o) {
        if (this == o) return true;
        if (!(o instanceof SoftApInfo)) return false;
        SoftApInfo softApInfo = (SoftApInfo) o;
        return mFrequency == softApInfo.mFrequency
                && mBandwidth == softApInfo.mBandwidth
                && Objects.equals(mBssid, softApInfo.mBssid)
                && mWifiStandard == softApInfo.mWifiStandard;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mFrequency, mBandwidth, mBssid, mWifiStandard);
    }
}
