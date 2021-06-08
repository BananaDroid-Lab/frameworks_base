/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.wallet.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.service.quickaccesswallet.GetWalletCardsRequest;
import android.service.quickaccesswallet.QuickAccessWalletClient;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.util.settings.SecureSettings;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class QuickAccessWalletControllerTest extends SysuiTestCase {

    @Mock
    private QuickAccessWalletClient mQuickAccessWalletClient;
    @Mock
    private SecureSettings mSecureSettings;
    @Mock
    private QuickAccessWalletClient.OnWalletCardsRetrievedCallback mCardsRetriever;
    @Captor
    private ArgumentCaptor<GetWalletCardsRequest> mRequestCaptor;

    private QuickAccessWalletController mController;
    private TestableLooper mTestableLooper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestableLooper = TestableLooper.get(this);
        when(mQuickAccessWalletClient.isWalletServiceAvailable()).thenReturn(true);
        when(mQuickAccessWalletClient.isWalletFeatureAvailable()).thenReturn(true);
        when(mQuickAccessWalletClient.isWalletFeatureAvailableWhenDeviceLocked()).thenReturn(true);

        mController = new QuickAccessWalletController(
                mContext,
                MoreExecutors.directExecutor(),
                mSecureSettings,
                mQuickAccessWalletClient);
    }

    @Test
    public void walletEnabled() {
        mController.updateWalletPreference();

        assertTrue(mController.isWalletEnabled());
    }

    @Test
    public void walletServiceUnavailable_walletNotEnabled() {
        when(mQuickAccessWalletClient.isWalletServiceAvailable()).thenReturn(false);

        mController.updateWalletPreference();

        assertFalse(mController.isWalletEnabled());
    }

    @Test
    public void walletFeatureUnavailable_walletNotEnabled() {
        when(mQuickAccessWalletClient.isWalletFeatureAvailable()).thenReturn(false);

        mController.updateWalletPreference();

        assertFalse(mController.isWalletEnabled());
    }

    @Test
    public void walletFeatureWhenLockedUnavailable_walletNotEnabled() {
        when(mQuickAccessWalletClient.isWalletFeatureAvailableWhenDeviceLocked()).thenReturn(false);

        mController.updateWalletPreference();

        assertFalse(mController.isWalletEnabled());
    }

    @Test
    public void getWalletClient_NoRecreation_sameClient() {
        assertSame(mQuickAccessWalletClient, mController.getWalletClient());
    }

    @Test
    public void getWalletClient_reCreateClient_notSameClient() {
        mController.reCreateWalletClient();

        assertNotSame(mQuickAccessWalletClient, mController.getWalletClient());
    }

    @Test
    public void queryWalletCards_walletEnabled_queryCards() {
        mController.queryWalletCards(mCardsRetriever);

        verify(mQuickAccessWalletClient)
                .getWalletCards(
                        eq(MoreExecutors.directExecutor()),
                        mRequestCaptor.capture(),
                        eq(mCardsRetriever));

        GetWalletCardsRequest request = mRequestCaptor.getValue();
        assertEquals(1, mRequestCaptor.getValue().getMaxCards());
        assertEquals(
                mContext.getResources().getDimensionPixelSize(R.dimen.wallet_tile_card_view_width),
                request.getCardWidthPx());
        assertEquals(
                mContext.getResources().getDimensionPixelSize(R.dimen.wallet_tile_card_view_height),
                request.getCardHeightPx());
    }
}
