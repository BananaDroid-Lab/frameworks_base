/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.vcn;

import static android.net.IpSecManager.DIRECTION_IN;
import static android.net.IpSecManager.DIRECTION_OUT;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.vcn.VcnManager.VCN_ERROR_CODE_CONFIG_ERROR;
import static android.net.vcn.VcnManager.VCN_ERROR_CODE_INTERNAL_ERROR;
import static android.net.vcn.VcnManager.VCN_ERROR_CODE_NETWORK_ERROR;

import static com.android.server.vcn.VcnGatewayConnection.VcnChildSessionConfiguration;
import static com.android.server.vcn.VcnGatewayConnection.VcnIkeSession;
import static com.android.server.vcn.VcnGatewayConnection.VcnNetworkAgent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.ipsec.ike.ChildSaProposal;
import android.net.ipsec.ike.exceptions.AuthenticationFailedException;
import android.net.ipsec.ike.exceptions.IkeException;
import android.net.ipsec.ike.exceptions.IkeInternalException;
import android.net.ipsec.ike.exceptions.TemporaryFailureException;
import android.net.vcn.VcnControlPlaneIkeConfig;
import android.net.vcn.VcnManager.VcnErrorCode;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.vcn.util.MtuUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Tests for VcnGatewayConnection.ConnectedState */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class VcnGatewayConnectionConnectedStateTest extends VcnGatewayConnectionTestBase {
    private VcnIkeSession mIkeSession;
    private VcnNetworkAgent mNetworkAgent;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        mNetworkAgent = mock(VcnNetworkAgent.class);
        doReturn(mNetworkAgent)
                .when(mDeps)
                .newNetworkAgent(any(), any(), any(), any(), anyInt(), any(), any(), any(), any());

        mGatewayConnection.setUnderlyingNetwork(TEST_UNDERLYING_NETWORK_RECORD_1);

        mIkeSession = mGatewayConnection.buildIkeSession(TEST_UNDERLYING_NETWORK_RECORD_1.network);
        mGatewayConnection.setIkeSession(mIkeSession);

        mGatewayConnection.transitionTo(mGatewayConnection.mConnectedState);
        mTestLooper.dispatchAll();
    }

    @Test
    public void testEnterStateCreatesNewIkeSession() throws Exception {
        verify(mDeps).newIkeSession(any(), any(), any(), any(), any());
    }

    @Test
    public void testEnterStateDoesNotCancelSafeModeAlarm() {
        verifySafeModeTimeoutAlarmAndGetCallback(false /* expectCanceled */);
    }

    @Test
    public void testNullNetworkDoesNotTriggerDisconnect() throws Exception {
        mGatewayConnection
                .getUnderlyingNetworkTrackerCallback()
                .onSelectedUnderlyingNetworkChanged(null);
        mTestLooper.dispatchAll();

        assertEquals(mGatewayConnection.mConnectedState, mGatewayConnection.getCurrentState());
        verify(mIkeSession, never()).close();
        verifyDisconnectRequestAlarmAndGetCallback(false /* expectCanceled */);
    }

    @Test
    public void testNewNetworkTriggersMigration() throws Exception {
        mGatewayConnection
                .getUnderlyingNetworkTrackerCallback()
                .onSelectedUnderlyingNetworkChanged(TEST_UNDERLYING_NETWORK_RECORD_2);
        mTestLooper.dispatchAll();

        assertEquals(mGatewayConnection.mConnectedState, mGatewayConnection.getCurrentState());
        verify(mIkeSession, never()).close();
        verify(mIkeSession).setNetwork(TEST_UNDERLYING_NETWORK_RECORD_2.network);
    }

    @Test
    public void testSameNetworkDoesNotTriggerMigration() throws Exception {
        mGatewayConnection
                .getUnderlyingNetworkTrackerCallback()
                .onSelectedUnderlyingNetworkChanged(TEST_UNDERLYING_NETWORK_RECORD_1);
        mTestLooper.dispatchAll();

        assertEquals(mGatewayConnection.mConnectedState, mGatewayConnection.getCurrentState());
    }

    @Test
    public void testCreatedTransformsAreApplied() throws Exception {
        for (int direction : new int[] {DIRECTION_IN, DIRECTION_OUT}) {
            getChildSessionCallback().onIpSecTransformCreated(makeDummyIpSecTransform(), direction);
            mTestLooper.dispatchAll();

            verify(mIpSecSvc)
                    .applyTunnelModeTransform(
                            eq(TEST_IPSEC_TUNNEL_RESOURCE_ID), eq(direction), anyInt(), any());
        }

        assertEquals(mGatewayConnection.mConnectedState, mGatewayConnection.getCurrentState());
    }

    @Test
    public void testMigration() throws Exception {
        triggerChildOpened();

        getChildSessionCallback()
                .onIpSecTransformsMigrated(makeDummyIpSecTransform(), makeDummyIpSecTransform());
        mTestLooper.dispatchAll();

        verify(mIpSecSvc, times(2))
                .setNetworkForTunnelInterface(
                        eq(TEST_IPSEC_TUNNEL_RESOURCE_ID),
                        eq(TEST_UNDERLYING_NETWORK_RECORD_1.network),
                        any());

        for (int direction : new int[] {DIRECTION_IN, DIRECTION_OUT}) {
            verify(mIpSecSvc)
                    .applyTunnelModeTransform(
                            eq(TEST_IPSEC_TUNNEL_RESOURCE_ID), eq(direction), anyInt(), any());
        }

        assertEquals(mGatewayConnection.mConnectedState, mGatewayConnection.getCurrentState());

        final List<ChildSaProposal> saProposals =
                ((VcnControlPlaneIkeConfig) mConfig.getControlPlaneConfig())
                        .getChildSessionParams()
                        .getSaProposals();
        final int expectedMtu =
                MtuUtils.getMtu(
                        saProposals,
                        mConfig.getMaxMtu(),
                        TEST_UNDERLYING_NETWORK_RECORD_1.linkProperties.getMtu());
        verify(mNetworkAgent).sendLinkProperties(argThat(lp -> expectedMtu == lp.getMtu()));
    }

    private void triggerChildOpened() {
        triggerChildOpened(Collections.singletonList(TEST_INTERNAL_ADDR), TEST_DNS_ADDR);
    }

    private void triggerChildOpened(List<LinkAddress> internalAddresses, InetAddress dnsAddress) {
        final VcnChildSessionConfiguration mMockChildSessionConfig =
                mock(VcnChildSessionConfiguration.class);
        doReturn(internalAddresses).when(mMockChildSessionConfig).getInternalAddresses();
        doReturn(Collections.singletonList(dnsAddress))
                .when(mMockChildSessionConfig)
                .getInternalDnsServers();

        getChildSessionCallback().onOpened(mMockChildSessionConfig);
    }

    private void triggerValidation(int status) {
        final ArgumentCaptor<Consumer<Integer>> validationCallbackCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(mDeps)
                .newNetworkAgent(
                        any(),
                        any(),
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        any(),
                        validationCallbackCaptor.capture());

        validationCallbackCaptor.getValue().accept(status);
    }

    @Test
    public void testChildOpenedRegistersNetwork() throws Exception {
        // Verify scheduled but not canceled when entering ConnectedState
        verifySafeModeTimeoutAlarmAndGetCallback(false /* expectCanceled */);
        triggerChildOpened();
        mTestLooper.dispatchAll();

        assertEquals(mGatewayConnection.mConnectedState, mGatewayConnection.getCurrentState());

        final ArgumentCaptor<LinkProperties> lpCaptor =
                ArgumentCaptor.forClass(LinkProperties.class);
        final ArgumentCaptor<NetworkCapabilities> ncCaptor =
                ArgumentCaptor.forClass(NetworkCapabilities.class);
        verify(mDeps)
                .newNetworkAgent(
                        eq(mVcnContext),
                        any(String.class),
                        ncCaptor.capture(),
                        lpCaptor.capture(),
                        anyInt(),
                        argThat(nac -> nac.getLegacyType() == ConnectivityManager.TYPE_MOBILE),
                        any(),
                        any(),
                        any());
        verify(mNetworkAgent).register();
        verify(mNetworkAgent).markConnected();

        verify(mIpSecSvc)
                .addAddressToTunnelInterface(
                        eq(TEST_IPSEC_TUNNEL_RESOURCE_ID), eq(TEST_INTERNAL_ADDR), any());

        final LinkProperties lp = lpCaptor.getValue();
        assertEquals(Collections.singletonList(TEST_INTERNAL_ADDR), lp.getLinkAddresses());
        assertEquals(Collections.singletonList(TEST_DNS_ADDR), lp.getDnsServers());

        final NetworkCapabilities nc = ncCaptor.getValue();
        assertTrue(nc.hasTransport(TRANSPORT_CELLULAR));
        assertFalse(nc.hasTransport(TRANSPORT_WIFI));
        for (int cap : mConfig.getAllExposedCapabilities()) {
            assertTrue(nc.hasCapability(cap));
        }

        // Now that Vcn Network is up, notify it as validated and verify the SafeMode alarm is
        // canceled
        triggerValidation(NetworkAgent.VALIDATION_STATUS_VALID);
        verify(mSafeModeTimeoutAlarm).cancel();
        assertFalse(mGatewayConnection.isInSafeMode());
    }

    @Test
    public void testInternalAndDnsAddressesChanged() throws Exception {
        final List<LinkAddress> startingInternalAddrs =
                Arrays.asList(new LinkAddress[] {TEST_INTERNAL_ADDR, TEST_INTERNAL_ADDR_2});
        triggerChildOpened(startingInternalAddrs, TEST_DNS_ADDR);
        mTestLooper.dispatchAll();

        for (LinkAddress addr : startingInternalAddrs) {
            verify(mIpSecSvc)
                    .addAddressToTunnelInterface(
                            eq(TEST_IPSEC_TUNNEL_RESOURCE_ID), eq(addr), any());
        }

        verify(mDeps)
                .newNetworkAgent(
                        any(),
                        any(),
                        any(),
                        argThat(
                                lp ->
                                        startingInternalAddrs.equals(lp.getLinkAddresses())
                                                && Collections.singletonList(TEST_DNS_ADDR)
                                                        .equals(lp.getDnsServers())),
                        anyInt(),
                        any(),
                        any(),
                        any(),
                        any());

        // Trigger another connection event, and verify that the addresses change
        final List<LinkAddress> newInternalAddrs =
                Arrays.asList(new LinkAddress[] {TEST_INTERNAL_ADDR_2, TEST_INTERNAL_ADDR_3});
        triggerChildOpened(newInternalAddrs, TEST_DNS_ADDR_2);
        mTestLooper.dispatchAll();

        // Verify addresses on tunnel network added/removed
        for (LinkAddress addr : newInternalAddrs) {
            verify(mIpSecSvc)
                    .addAddressToTunnelInterface(
                            eq(TEST_IPSEC_TUNNEL_RESOURCE_ID), eq(addr), any());
        }
        verify(mIpSecSvc)
                .removeAddressFromTunnelInterface(
                        eq(TEST_IPSEC_TUNNEL_RESOURCE_ID), eq(TEST_INTERNAL_ADDR), any());

        verify(mNetworkAgent).sendLinkProperties(argThat(
                lp -> newInternalAddrs.equals(lp.getLinkAddresses())
                        && Collections.singletonList(TEST_DNS_ADDR_2).equals(lp.getDnsServers())));

        // Verify that IpSecTunnelInterface only created once
        verify(mIpSecSvc).createTunnelInterface(any(), any(), any(), any(), any());
        verifyNoMoreInteractions(mIpSecSvc);
    }

    @Test
    public void testSuccessfulConnectionExitsSafeMode() throws Exception {
        verifySafeModeTimeoutNotifiesCallbackAndUnregistersNetworkAgent(
                mGatewayConnection.mConnectedState);

        assertTrue(mGatewayConnection.isInSafeMode());
        assertFalse(mGatewayConnection.isQuitting());

        triggerChildOpened();
        mTestLooper.dispatchAll();

        triggerValidation(NetworkAgent.VALIDATION_STATUS_VALID);

        assertFalse(mGatewayConnection.isInSafeMode());
    }

    private Consumer<VcnNetworkAgent> setupNetworkAndGetUnwantedCallback() {
        triggerChildOpened();
        mTestLooper.dispatchAll();

        final ArgumentCaptor<Consumer<VcnNetworkAgent>> unwantedCallbackCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(mDeps)
                .newNetworkAgent(
                        any(),
                        any(),
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        unwantedCallbackCaptor.capture(),
                        any());

        return unwantedCallbackCaptor.getValue();
    }

    @Test
    public void testUnwantedNetworkAgentTriggersTeardown() throws Exception {
        final Consumer<VcnNetworkAgent> unwantedCallback = setupNetworkAndGetUnwantedCallback();

        unwantedCallback.accept(mNetworkAgent);
        mTestLooper.dispatchAll();

        assertTrue(mGatewayConnection.isQuitting());
        assertEquals(mGatewayConnection.mDisconnectingState, mGatewayConnection.getCurrentState());
    }

    @Test
    public void testUnwantedNetworkAgentWithDisconnectedNetworkAgent() throws Exception {
        final Consumer<VcnNetworkAgent> unwantedCallback = setupNetworkAndGetUnwantedCallback();

        mGatewayConnection.setNetworkAgent(null);
        unwantedCallback.accept(mNetworkAgent);
        mTestLooper.dispatchAll();

        // Verify that the call was ignored; the state machine is still running, and the state has
        // not changed.
        assertFalse(mGatewayConnection.isQuitting());
        assertEquals(mGatewayConnection.mConnectedState, mGatewayConnection.getCurrentState());
    }

    @Test
    public void testUnwantedNetworkAgentWithNewNetworkAgent() throws Exception {
        final Consumer<VcnNetworkAgent> unwantedCallback = setupNetworkAndGetUnwantedCallback();
        final VcnNetworkAgent testAgent = mock(VcnNetworkAgent.class);

        mGatewayConnection.setNetworkAgent(testAgent);
        unwantedCallback.accept(mNetworkAgent);
        mTestLooper.dispatchAll();

        assertFalse(mGatewayConnection.isQuitting());
        assertEquals(mGatewayConnection.mConnectedState, mGatewayConnection.getCurrentState());
        assertEquals(testAgent, mGatewayConnection.getNetworkAgent());
    }

    @Test
    public void testChildSessionClosedTriggersDisconnect() throws Exception {
        // Verify scheduled but not canceled when entering ConnectedState
        verifySafeModeTimeoutAlarmAndGetCallback(false /* expectCanceled */);

        getChildSessionCallback().onClosed();
        mTestLooper.dispatchAll();

        assertEquals(mGatewayConnection.mDisconnectingState, mGatewayConnection.getCurrentState());
        verifyTeardownTimeoutAlarmAndGetCallback(false /* expectCanceled */);

        // Since network never validated, verify mSafeModeTimeoutAlarm not canceled
        verifyNoMoreInteractions(mSafeModeTimeoutAlarm);

        // The child session was closed without exception, so verify that the GatewayStatusCallback
        // was not notified
        verifyNoMoreInteractions(mGatewayStatusCallback);
    }

    @Test
    public void testChildSessionClosedExceptionallyNotifiesGatewayStatusCallback()
            throws Exception {
        final IkeInternalException exception = new IkeInternalException(mock(IOException.class));
        getChildSessionCallback().onClosedExceptionally(exception);
        mTestLooper.dispatchAll();

        verify(mGatewayStatusCallback)
                .onGatewayConnectionError(
                        eq(mConfig.getGatewayConnectionName()),
                        eq(VCN_ERROR_CODE_INTERNAL_ERROR),
                        any(),
                        any());
    }

    @Test
    public void testIkeSessionClosedTriggersDisconnect() throws Exception {
        // Verify scheduled but not canceled when entering ConnectedState
        verifySafeModeTimeoutAlarmAndGetCallback(false /* expectCanceled */);

        getIkeSessionCallback().onClosed();
        mTestLooper.dispatchAll();

        assertEquals(mGatewayConnection.mRetryTimeoutState, mGatewayConnection.getCurrentState());
        verify(mIkeSession).close();

        // Since network never validated, verify mSafeModeTimeoutAlarm not canceled
        verifyNoMoreInteractions(mSafeModeTimeoutAlarm);

        // IkeSession closed with no error, so verify that the GatewayStatusCallback was not
        // notified
        verifyNoMoreInteractions(mGatewayStatusCallback);
    }

    private void verifyIkeSessionClosedExceptionalltyNotifiesStatusCallback(
            IkeException cause, @VcnErrorCode int expectedErrorType) {
        getIkeSessionCallback().onClosedExceptionally(cause);
        mTestLooper.dispatchAll();

        verify(mIkeSession).close();

        verify(mGatewayStatusCallback)
                .onGatewayConnectionError(
                        eq(mConfig.getGatewayConnectionName()),
                        eq(expectedErrorType),
                        any(),
                        any());
    }

    @Test
    public void testIkeSessionClosedExceptionallyAuthenticationFailure() throws Exception {
        verifyIkeSessionClosedExceptionalltyNotifiesStatusCallback(
                new AuthenticationFailedException("vcn test"), VCN_ERROR_CODE_CONFIG_ERROR);
    }

    @Test
    public void testIkeSessionClosedExceptionallyDnsFailure() throws Exception {
        verifyIkeSessionClosedExceptionalltyNotifiesStatusCallback(
                new IkeInternalException(new UnknownHostException()), VCN_ERROR_CODE_NETWORK_ERROR);
    }

    @Test
    public void testIkeSessionClosedExceptionallyInternalFailure() throws Exception {
        verifyIkeSessionClosedExceptionalltyNotifiesStatusCallback(
                new TemporaryFailureException("vcn test"), VCN_ERROR_CODE_INTERNAL_ERROR);
    }

    @Test
    public void testTeardown() throws Exception {
        mGatewayConnection.teardownAsynchronously();
        mTestLooper.dispatchAll();

        assertEquals(mGatewayConnection.mDisconnectingState, mGatewayConnection.getCurrentState());
        assertTrue(mGatewayConnection.isQuitting());
    }

    @Test
    public void testNonTeardownDisconnectRequest() throws Exception {
        mGatewayConnection.sendDisconnectRequestedAndAcquireWakelock("TEST", false);
        mTestLooper.dispatchAll();

        assertEquals(mGatewayConnection.mDisconnectingState, mGatewayConnection.getCurrentState());
        assertFalse(mGatewayConnection.isQuitting());
    }
}
