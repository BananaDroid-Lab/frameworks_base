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

import android.net.MacAddress;
import android.net.wifi.util.SdkLevelUtil;
import android.os.Parcel;

import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit tests for {@link android.net.wifi.SoftApInfo}.
 */
@SmallTest
public class SoftApInfoTest {

    /**
     * Verifies copy constructor.
     */
    @Test
    public void testCopyOperator() throws Exception {
        SoftApInfo info = new SoftApInfo();
        info.setFrequency(2412);
        info.setBandwidth(SoftApInfo.CHANNEL_WIDTH_20MHZ);
        info.setBssid(MacAddress.fromString("aa:bb:cc:dd:ee:ff"));
        info.setWifiStandard(ScanResult.WIFI_STANDARD_LEGACY);


        SoftApInfo copiedInfo = new SoftApInfo(info);

        assertEquals(info, copiedInfo);
        assertEquals(info.hashCode(), copiedInfo.hashCode());
    }

    /**
     * Verifies parcel serialization/deserialization.
     */
    @Test
    public void testParcelOperation() throws Exception {
        SoftApInfo info = new SoftApInfo();
        info.setFrequency(2412);
        info.setBandwidth(SoftApInfo.CHANNEL_WIDTH_20MHZ);
        info.setBssid(MacAddress.fromString("aa:bb:cc:dd:ee:ff"));
        info.setWifiStandard(ScanResult.WIFI_STANDARD_LEGACY);

        Parcel parcelW = Parcel.obtain();
        info.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        SoftApInfo fromParcel = SoftApInfo.CREATOR.createFromParcel(parcelR);

        assertEquals(info, fromParcel);
        assertEquals(info.hashCode(), fromParcel.hashCode());
    }


    /**
     * Verifies the initial value same as expected.
     */
    @Test
    public void testInitialValue() throws Exception {
        SoftApInfo info = new SoftApInfo();
        assertEquals(info.getFrequency(), 0);
        assertEquals(info.getBandwidth(), SoftApInfo.CHANNEL_WIDTH_INVALID);
        if (SdkLevelUtil.isAtLeastS()) {
            assertEquals(info.getBssid(), null);
            assertEquals(info.getWifiStandard(), ScanResult.WIFI_STANDARD_UNKNOWN);
        }
    }

}
