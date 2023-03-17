/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.devicestate;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.settingslib.devicestate.DeviceStateRotationLockSettingsManager.SettableDeviceState;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DeviceStateRotationLockSettingsManagerTest {

    @Rule public Expect mExpect = Expect.create();

    @Mock private Context mMockContext;
    @Mock private Resources mMockResources;

    private DeviceStateRotationLockSettingsManager mManager;
    private int mNumSettingsChanges = 0;
    private final ContentObserver mContentObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange) {
            mNumSettingsChanges++;
        }
    };
    private final FakeSecureSettings mFakeSecureSettings = new FakeSecureSettings();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = InstrumentationRegistry.getTargetContext();
        when(mMockContext.getApplicationContext()).thenReturn(mMockContext);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockContext.getContentResolver()).thenReturn(context.getContentResolver());
        when(mMockResources.getStringArray(R.array.config_perDeviceStateRotationLockDefaults))
                .thenReturn(new String[]{"0:1", "1:0:2", "2:2"});
        when(mMockResources.getIntArray(R.array.config_foldedDeviceStates))
                .thenReturn(new int[]{0});
        when(mMockResources.getIntArray(R.array.config_halfFoldedDeviceStates))
                .thenReturn(new int[]{1});
        when(mMockResources.getIntArray(R.array.config_openDeviceStates))
                .thenReturn(new int[]{2});
        mFakeSecureSettings.registerContentObserver(
                Settings.Secure.DEVICE_STATE_ROTATION_LOCK,
                /* notifyForDescendents= */ false, //NOTYPO
                mContentObserver,
                UserHandle.USER_CURRENT);
        mManager = new DeviceStateRotationLockSettingsManager(mMockContext, mFakeSecureSettings);
    }

    @Test
    public void initialization_settingsAreChangedOnce() {
        assertThat(mNumSettingsChanges).isEqualTo(1);
    }

    @Test
    public void updateSetting_multipleTimes_sameValue_settingsAreChangedOnlyOnce() {
        mNumSettingsChanges = 0;

        mManager.updateSetting(/* deviceState= */ 1, /* rotationLocked= */ true);
        mManager.updateSetting(/* deviceState= */ 1, /* rotationLocked= */ true);
        mManager.updateSetting(/* deviceState= */ 1, /* rotationLocked= */ true);

        assertThat(mNumSettingsChanges).isEqualTo(1);
    }

    @Test
    public void updateSetting_multipleTimes_differentValues_settingsAreChangedMultipleTimes() {
        mNumSettingsChanges = 0;

        mManager.updateSetting(/* deviceState= */ 1, /* rotationLocked= */ true);
        mManager.updateSetting(/* deviceState= */ 1, /* rotationLocked= */ false);
        mManager.updateSetting(/* deviceState= */ 1, /* rotationLocked= */ true);

        assertThat(mNumSettingsChanges).isEqualTo(3);
    }

    @Test
    public void getSettableDeviceStates_returnsExpectedValuesInOriginalOrder() {
        when(mMockResources.getStringArray(
                R.array.config_perDeviceStateRotationLockDefaults)).thenReturn(
                new String[]{"2:1", "1:0:1", "0:2"});

        List<SettableDeviceState> settableDeviceStates =
                DeviceStateRotationLockSettingsManager.getInstance(
                        mMockContext).getSettableDeviceStates();

        assertThat(settableDeviceStates).containsExactly(
                new SettableDeviceState(/* deviceState= */ 2, /* isSettable= */ true),
                new SettableDeviceState(/* deviceState= */ 1, /* isSettable= */ false),
                new SettableDeviceState(/* deviceState= */ 0, /* isSettable= */ true)
        ).inOrder();
    }

    @Test
    public void persistedInvalidIgnoredState_returnsDefaults() {
        when(mMockResources.getStringArray(
                R.array.config_perDeviceStateRotationLockDefaults)).thenReturn(
                new String[]{"0:1", "1:0:2", "2:2"});
        // Here 2 has IGNORED, and in the defaults 1 has IGNORED.
        persistSettings("0:2:2:0:1:2");
        DeviceStateRotationLockSettingsManager manager =
                new DeviceStateRotationLockSettingsManager(mMockContext, mFakeSecureSettings);

        mExpect.that(manager.getRotationLockSetting(0)).isEqualTo(1);
        mExpect.that(manager.getRotationLockSetting(1)).isEqualTo(2);
        mExpect.that(manager.getRotationLockSetting(2)).isEqualTo(2);
    }

    @Test
    public void persistedValidValues_returnsPersistedValues() {
        when(mMockResources.getStringArray(
                R.array.config_perDeviceStateRotationLockDefaults)).thenReturn(
                new String[]{"0:1", "1:0:2", "2:2"});
        persistSettings("0:2:1:0:2:1");
        DeviceStateRotationLockSettingsManager manager =
                new DeviceStateRotationLockSettingsManager(mMockContext, mFakeSecureSettings);

        mExpect.that(manager.getRotationLockSetting(0)).isEqualTo(2);
        mExpect.that(manager.getRotationLockSetting(1)).isEqualTo(1);
        mExpect.that(manager.getRotationLockSetting(2)).isEqualTo(1);
    }

    private void persistSettings(String value) {
        mFakeSecureSettings.putStringForUser(
                Settings.Secure.DEVICE_STATE_ROTATION_LOCK,
                value,
                UserHandle.USER_CURRENT);
    }
}
