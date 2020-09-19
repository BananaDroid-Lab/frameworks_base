/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.server.hdmi;

import static com.android.server.hdmi.HdmiControlService.INITIATED_BY_ENABLE_CEC;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.ContextWrapper;
import android.hardware.tv.cec.V1_0.SendMessageResult;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.IThermalService;
import android.os.Looper;
import android.os.PowerManager;
import android.os.test.TestLooper;
import android.platform.test.annotations.Presubmit;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

/** Tests for {@link ArcInitiationActionFromAvrTest} */
@SmallTest
@Presubmit
@RunWith(JUnit4.class)
public class ArcInitiationActionFromAvrTest {

    private Context mContextSpy;
    private HdmiCecLocalDeviceAudioSystem mHdmiCecLocalDeviceAudioSystem;
    private HdmiCecController mHdmiCecController;
    private HdmiControlService mHdmiControlService;
    private FakeNativeWrapper mNativeWrapper;
    private ArcInitiationActionFromAvr mAction;

    private TestLooper mTestLooper = new TestLooper();
    private ArrayList<HdmiCecLocalDevice> mLocalDevices = new ArrayList<>();

    @Mock private IPowerManager mIPowerManagerMock;
    @Mock private IThermalService mIThermalServiceMock;
    @Mock private AudioManager mAudioManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mContextSpy = spy(new ContextWrapper(InstrumentationRegistry.getTargetContext()));

        PowerManager powerManager = new PowerManager(mContextSpy, mIPowerManagerMock,
                mIThermalServiceMock, new Handler(mTestLooper.getLooper()));
        when(mContextSpy.getSystemService(Context.POWER_SERVICE)).thenReturn(powerManager);
        when(mContextSpy.getSystemService(PowerManager.class)).thenReturn(powerManager);
        when(mIPowerManagerMock.isInteractive()).thenReturn(true);

        mHdmiControlService =
                new HdmiControlService(mContextSpy) {
                    @Override
                    boolean isPowerStandby() {
                        return false;
                    }

                    @Override
                    void wakeUp() {
                    }

                    @Override
                    PowerManager getPowerManager() {
                        return powerManager;
                    }

                    @Override
                    AudioManager getAudioManager() {
                        return mAudioManager;
                    }

                    @Override
                    boolean isAddressAllocated() {
                        return true;
                    }

                    @Override
                    Looper getServiceLooper() {
                        return mTestLooper.getLooper();
                    }
                };

        mHdmiCecLocalDeviceAudioSystem = new HdmiCecLocalDeviceAudioSystem(mHdmiControlService) {
            @Override
            protected void setPreferredAddress(int addr) {
            }
        };

        mHdmiCecLocalDeviceAudioSystem.init();
        Looper looper = mTestLooper.getLooper();
        mHdmiControlService.setIoLooper(looper);
        mNativeWrapper = new FakeNativeWrapper();
        mHdmiCecController =
                HdmiCecController.createWithNativeWrapper(this.mHdmiControlService, mNativeWrapper);
        mHdmiControlService.setCecController(mHdmiCecController);
        mHdmiControlService.setHdmiMhlController(HdmiMhlControllerStub.create(mHdmiControlService));
        mHdmiControlService.setMessageValidator(new HdmiCecMessageValidator(mHdmiControlService));
        mHdmiControlService.initPortInfo();
        mAction = new ArcInitiationActionFromAvr(mHdmiCecLocalDeviceAudioSystem);

        mLocalDevices.add(mHdmiCecLocalDeviceAudioSystem);
        mHdmiControlService.allocateLogicalAddress(mLocalDevices, INITIATED_BY_ENABLE_CEC);
        mTestLooper.dispatchAll();
    }

    @Test
    public void arcInitiation_initiated() {
        mHdmiCecLocalDeviceAudioSystem.addAndStartAction(mAction);
        mTestLooper.dispatchAll();
        HdmiCecMessage initiateArc = HdmiCecMessageBuilder.buildInitiateArc(
                Constants.ADDR_AUDIO_SYSTEM, Constants.ADDR_TV);

        assertThat(mNativeWrapper.getResultMessages()).contains(initiateArc);

        mHdmiControlService.sendCecCommand(
                HdmiCecMessageBuilder.buildReportArcInitiated(
                        Constants.ADDR_TV,
                        Constants.ADDR_AUDIO_SYSTEM));
        mTestLooper.dispatchAll();

        assertThat(mHdmiCecLocalDeviceAudioSystem.isArcEnabled()).isTrue();
    }

    @Test
    public void arcInitiation_sendFailed() {
        mNativeWrapper.setMessageSendResult(Constants.MESSAGE_INITIATE_ARC, SendMessageResult.NACK);
        mHdmiCecLocalDeviceAudioSystem.addAndStartAction(mAction);
        mTestLooper.dispatchAll();
        HdmiCecMessage initiateArc = HdmiCecMessageBuilder.buildInitiateArc(
                Constants.ADDR_AUDIO_SYSTEM, Constants.ADDR_TV);

        assertThat(mNativeWrapper.getResultMessages()).contains(initiateArc);

        assertThat(mHdmiCecLocalDeviceAudioSystem.isArcEnabled()).isFalse();
    }

    @Test
    public void arcInitiation_terminated() {
        mHdmiCecLocalDeviceAudioSystem.addAndStartAction(mAction);
        mTestLooper.dispatchAll();

        HdmiCecMessage initiateArc = HdmiCecMessageBuilder.buildInitiateArc(
                Constants.ADDR_AUDIO_SYSTEM, Constants.ADDR_TV);

        assertThat(mNativeWrapper.getResultMessages()).contains(initiateArc);

        mHdmiControlService.handleCecCommand(HdmiCecMessageBuilder.buildReportArcTerminated(
                Constants.ADDR_TV,
                Constants.ADDR_AUDIO_SYSTEM));
        mTestLooper.dispatchAll();

        assertThat(mHdmiCecLocalDeviceAudioSystem.isArcEnabled()).isFalse();
    }

    @Test
    public void arcInitiation_abort() {
        mHdmiCecLocalDeviceAudioSystem.addAndStartAction(mAction);
        mTestLooper.dispatchAll();

        HdmiCecMessage initiateArc = HdmiCecMessageBuilder.buildInitiateArc(
                Constants.ADDR_AUDIO_SYSTEM, Constants.ADDR_TV);

        assertThat(mNativeWrapper.getResultMessages()).contains(initiateArc);

        mHdmiControlService.handleCecCommand(
                HdmiCecMessageBuilder.buildFeatureAbortCommand(
                        Constants.ADDR_TV,
                        Constants.ADDR_AUDIO_SYSTEM, Constants.MESSAGE_INITIATE_ARC,
                        Constants.ABORT_REFUSED));
        mTestLooper.dispatchAll();

        assertThat(mHdmiCecLocalDeviceAudioSystem.isArcEnabled()).isFalse();
    }

    //Fail
    @Test
    public void arcInitiation_timeout() {
        mHdmiCecLocalDeviceAudioSystem.addAndStartAction(mAction);
        mTestLooper.dispatchAll();

        HdmiCecMessage initiateArc = HdmiCecMessageBuilder.buildInitiateArc(
                Constants.ADDR_AUDIO_SYSTEM, Constants.ADDR_TV);

        assertThat(mNativeWrapper.getResultMessages()).contains(initiateArc);

        mTestLooper.moveTimeForward(1001);
        mTestLooper.dispatchAll();
        assertThat(mHdmiCecLocalDeviceAudioSystem.isArcEnabled()).isTrue();
    }
}
