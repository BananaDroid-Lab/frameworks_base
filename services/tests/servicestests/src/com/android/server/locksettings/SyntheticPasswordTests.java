/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.locksettings;

import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_NONE;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PASSWORD;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PASSWORD_OR_PIN;
import static com.android.internal.widget.LockPatternUtils.CURRENT_LSKF_BASED_PROTECTOR_ID_KEY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.PasswordMetrics;
import android.app.PropertyInvalidatedCache;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.widget.LockscreenCredential;
import com.android.internal.widget.VerifyCredentialResponse;
import com.android.server.locksettings.SyntheticPasswordManager.AuthenticationResult;
import com.android.server.locksettings.SyntheticPasswordManager.PasswordData;
import com.android.server.locksettings.SyntheticPasswordManager.SyntheticPassword;

import libcore.util.HexEncoding;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;


/**
 * atest FrameworksServicesTests:SyntheticPasswordTests
 */
@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class SyntheticPasswordTests extends BaseLockSettingsServiceTests {

    public static final byte[] PAYLOAD = new byte[] {1, 2, -1, -2, 55};
    public static final byte[] PAYLOAD2 = new byte[] {2, 3, -2, -3, 44, 1};

    @Before
    public void disableProcessCaches() {
        PropertyInvalidatedCache.disableForTestMode();
    }

    @Test
    public void testNoneLskfBasedProtector() throws RemoteException {
        final int USER_ID = 10;
        MockSyntheticPasswordManager manager = new MockSyntheticPasswordManager(mContext, mStorage,
                mGateKeeperService, mUserManager, mPasswordSlotManager);
        SyntheticPassword sp = manager.newSyntheticPassword(USER_ID);
        assertFalse(lskfGatekeeperHandleExists(USER_ID));
        long protectorId = manager.createLskfBasedProtector(mGateKeeperService,
                LockscreenCredential.createNone(), sp, USER_ID);
        assertFalse(lskfGatekeeperHandleExists(USER_ID));
        assertFalse(manager.hasPasswordData(protectorId, USER_ID));
        assertFalse(manager.hasPasswordMetrics(protectorId, USER_ID));

        AuthenticationResult result = manager.unlockLskfBasedProtector(mGateKeeperService,
                protectorId, LockscreenCredential.createNone(), USER_ID, null);
        assertArrayEquals(result.syntheticPassword.deriveKeyStorePassword(),
                sp.deriveKeyStorePassword());
    }

    @Test
    public void testNonNoneLskfBasedProtector() throws RemoteException {
        final int USER_ID = 10;
        final LockscreenCredential password = newPassword("user-password");
        final LockscreenCredential badPassword = newPassword("bad-password");
        MockSyntheticPasswordManager manager = new MockSyntheticPasswordManager(mContext, mStorage,
                mGateKeeperService, mUserManager, mPasswordSlotManager);
        SyntheticPassword sp = manager.newSyntheticPassword(USER_ID);
        assertFalse(lskfGatekeeperHandleExists(USER_ID));
        long protectorId = manager.createLskfBasedProtector(mGateKeeperService, password, sp,
                USER_ID);
        assertTrue(lskfGatekeeperHandleExists(USER_ID));
        assertTrue(manager.hasPasswordData(protectorId, USER_ID));
        assertTrue(manager.hasPasswordMetrics(protectorId, USER_ID));

        AuthenticationResult result = manager.unlockLskfBasedProtector(mGateKeeperService,
                protectorId, password, USER_ID, null);
        assertArrayEquals(result.syntheticPassword.deriveKeyStorePassword(),
                sp.deriveKeyStorePassword());

        result = manager.unlockLskfBasedProtector(mGateKeeperService, protectorId, badPassword,
                USER_ID, null);
        assertNull(result.syntheticPassword);
    }

    private boolean lskfGatekeeperHandleExists(int userId) throws RemoteException {
        return mGateKeeperService.getSecureUserId(SyntheticPasswordManager.fakeUserId(userId)) != 0;
    }

    private boolean hasSyntheticPassword(int userId) throws RemoteException {
        return mService.getLong(CURRENT_LSKF_BASED_PROTECTOR_ID_KEY, 0, userId) != 0;
    }

    private void initializeCredential(LockscreenCredential password, int userId)
            throws RemoteException {
        assertTrue(mService.setLockCredential(password, nonePassword(), userId));
        assertEquals(CREDENTIAL_TYPE_PASSWORD, mService.getCredentialType(userId));
        assertTrue(mService.isSyntheticPasswordBasedCredential(userId));
    }

    protected void initializeSyntheticPassword(int userId) {
        synchronized (mService.mSpManager) {
            mService.initializeSyntheticPasswordLocked(userId);
        }
    }

    // Tests that the FRP credential is updated when an LSKF-based protector is created for the user
    // that owns the FRP credential, if the device is already provisioned.
    @Test
    public void testFrpCredentialSyncedIfDeviceProvisioned() throws RemoteException {
        setDeviceProvisioned(true);
        initializeSyntheticPassword(PRIMARY_USER_ID);
        verify(mStorage.mPersistentDataBlockManager).setFrpCredentialHandle(any());
    }

    // Tests that the FRP credential is not updated when an LSKF-based protector is created for the
    // user that owns the FRP credential, if the new credential is empty and the device is not yet
    // provisioned.
    @Test
    public void testEmptyFrpCredentialNotSyncedIfDeviceNotProvisioned() throws RemoteException {
        setDeviceProvisioned(false);
        initializeSyntheticPassword(PRIMARY_USER_ID);
        verify(mStorage.mPersistentDataBlockManager, never()).setFrpCredentialHandle(any());
    }

    // Tests that the FRP credential is updated when an LSKF-based protector is created for the user
    // that owns the FRP credential, if the new credential is nonempty and the device is not yet
    // provisioned.
    @Test
    public void testNonEmptyFrpCredentialSyncedIfDeviceNotProvisioned() throws RemoteException {
        setDeviceProvisioned(false);
        initializeSyntheticPassword(PRIMARY_USER_ID);
        verify(mStorage.mPersistentDataBlockManager, never()).setFrpCredentialHandle(any());
        mService.setLockCredential(newPassword("password"), nonePassword(), PRIMARY_USER_ID);
        verify(mStorage.mPersistentDataBlockManager).setFrpCredentialHandle(any());
    }

    @Test
    public void testChangeCredential() throws RemoteException {
        final LockscreenCredential password = newPassword("password");
        final LockscreenCredential newPassword = newPassword("newpassword");

        initializeCredential(password, PRIMARY_USER_ID);
        long sid = mGateKeeperService.getSecureUserId(PRIMARY_USER_ID);
        mService.setLockCredential(newPassword, password, PRIMARY_USER_ID);
        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                newPassword, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());
        assertEquals(sid, mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));
    }

    @Test
    public void testVerifyCredential() throws RemoteException {
        LockscreenCredential password = newPassword("password");
        LockscreenCredential badPassword = newPassword("badpassword");

        initializeCredential(password, PRIMARY_USER_ID);
        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                password, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());
        verify(mActivityManager).unlockUser2(eq(PRIMARY_USER_ID), any());

        assertEquals(VerifyCredentialResponse.RESPONSE_ERROR, mService.verifyCredential(
                badPassword, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());
    }

    @Test
    public void testClearCredential() throws RemoteException {
        LockscreenCredential password = newPassword("password");
        LockscreenCredential badPassword = newPassword("newpassword");

        initializeCredential(password, PRIMARY_USER_ID);
        long sid = mGateKeeperService.getSecureUserId(PRIMARY_USER_ID);
        // clear password
        mService.setLockCredential(nonePassword(), password, PRIMARY_USER_ID);
        assertEquals(0 ,mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));

        // set a new password
        mService.setLockCredential(badPassword, nonePassword(), PRIMARY_USER_ID);
        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                badPassword, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());
        assertNotEquals(sid, mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));
    }

    @Test
    public void testChangeCredentialKeepsAuthSecret() throws RemoteException {
        LockscreenCredential password = newPassword("password");
        LockscreenCredential badPassword = newPassword("new");

        initializeCredential(password, PRIMARY_USER_ID);
        mService.setLockCredential(badPassword, password, PRIMARY_USER_ID);
        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                badPassword, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());

        // Check the same secret was passed each time
        ArgumentCaptor<byte[]> secret = ArgumentCaptor.forClass(byte[].class);
        verify(mAuthSecretService, atLeastOnce()).setPrimaryUserCredential(secret.capture());
        for (byte[] val : secret.getAllValues()) {
          assertArrayEquals(val, secret.getAllValues().get(0));
        }
    }

    @Test
    public void testVerifyPassesPrimaryUserAuthSecret() throws RemoteException {
        LockscreenCredential password = newPassword("password");

        initializeCredential(password, PRIMARY_USER_ID);
        reset(mAuthSecretService);
        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                password, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());
        verify(mAuthSecretService).setPrimaryUserCredential(any(byte[].class));
    }

    @Test
    public void testSecondaryUserDoesNotPassAuthSecret() throws RemoteException {
        LockscreenCredential password = newPassword("password");

        initializeCredential(password, SECONDARY_USER_ID);
        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                password, SECONDARY_USER_ID, 0 /* flags */).getResponseCode());
        verify(mAuthSecretService, never()).setPrimaryUserCredential(any(byte[].class));
    }

    @Test
    public void testUnlockUserKeyIfUnsecuredPassesPrimaryUserAuthSecret() throws RemoteException {
        LockscreenCredential password = newPassword("password");
        initializeCredential(password, PRIMARY_USER_ID);
        mService.setLockCredential(nonePassword(), password, PRIMARY_USER_ID);

        reset(mAuthSecretService);
        mLocalService.unlockUserKeyIfUnsecured(PRIMARY_USER_ID);
        verify(mAuthSecretService).setPrimaryUserCredential(any(byte[].class));
    }

    @Test
    public void testTokenBasedResetPassword() throws RemoteException {
        LockscreenCredential password = newPassword("password");
        LockscreenCredential pattern = newPattern("123654");
        byte[] token = "some-high-entropy-secure-token".getBytes();
        initializeCredential(password, PRIMARY_USER_ID);
        // Disregard any reportPasswordChanged() invocations as part of credential setup.
        flushHandlerTasks();
        reset(mDevicePolicyManager);

        byte[] storageKey = mStorageManager.getUserUnlockToken(PRIMARY_USER_ID);

        assertFalse(mService.hasPendingEscrowToken(PRIMARY_USER_ID));
        long handle = mLocalService.addEscrowToken(token, PRIMARY_USER_ID, null);
        assertFalse(mLocalService.isEscrowTokenActive(handle, PRIMARY_USER_ID));
        assertTrue(mService.hasPendingEscrowToken(PRIMARY_USER_ID));

        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                password, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());
        assertTrue(mLocalService.isEscrowTokenActive(handle, PRIMARY_USER_ID));
        assertFalse(mService.hasPendingEscrowToken(PRIMARY_USER_ID));

        mLocalService.setLockCredentialWithToken(pattern, handle, token, PRIMARY_USER_ID);

        // Verify DPM gets notified about new device lock
        flushHandlerTasks();
        final PasswordMetrics metric = PasswordMetrics.computeForCredential(pattern);
        assertEquals(metric, mService.getUserPasswordMetrics(PRIMARY_USER_ID));
        verify(mDevicePolicyManager).reportPasswordChanged(metric, PRIMARY_USER_ID);

        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                pattern, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());
        assertArrayEquals(storageKey, mStorageManager.getUserUnlockToken(PRIMARY_USER_ID));
    }

    @Test
    public void testTokenBasedClearPassword() throws RemoteException {
        LockscreenCredential password = newPassword("password");
        LockscreenCredential pattern = newPattern("123654");
        byte[] token = "some-high-entropy-secure-token".getBytes();
        initializeCredential(password, PRIMARY_USER_ID);
        byte[] storageKey = mStorageManager.getUserUnlockToken(PRIMARY_USER_ID);

        long handle = mLocalService.addEscrowToken(token, PRIMARY_USER_ID, null);
        assertFalse(mLocalService.isEscrowTokenActive(handle, PRIMARY_USER_ID));

        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                password, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());
        assertTrue(mLocalService.isEscrowTokenActive(handle, PRIMARY_USER_ID));

        mLocalService.setLockCredentialWithToken(nonePassword(), handle, token, PRIMARY_USER_ID);
        flushHandlerTasks(); // flush the unlockUser() call before changing password again
        mLocalService.setLockCredentialWithToken(pattern, handle, token,
                PRIMARY_USER_ID);

        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                pattern, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());
        assertArrayEquals(storageKey, mStorageManager.getUserUnlockToken(PRIMARY_USER_ID));
    }

    @Test
    public void testTokenBasedResetPasswordAfterCredentialChanges() throws RemoteException {
        LockscreenCredential password = newPassword("password");
        LockscreenCredential pattern = newPattern("123654");
        LockscreenCredential newPassword = newPassword("password");
        byte[] token = "some-high-entropy-secure-token".getBytes();
        initializeCredential(password, PRIMARY_USER_ID);
        byte[] storageKey = mStorageManager.getUserUnlockToken(PRIMARY_USER_ID);

        long handle = mLocalService.addEscrowToken(token, PRIMARY_USER_ID, null);
        assertFalse(mLocalService.isEscrowTokenActive(handle, PRIMARY_USER_ID));

        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                password, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());
        assertTrue(mLocalService.isEscrowTokenActive(handle, PRIMARY_USER_ID));

        mService.setLockCredential(pattern, password, PRIMARY_USER_ID);

        mLocalService.setLockCredentialWithToken(newPassword, handle, token, PRIMARY_USER_ID);

        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                newPassword, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());
        assertArrayEquals(storageKey, mStorageManager.getUserUnlockToken(PRIMARY_USER_ID));
    }

    @Test
    public void testEscrowTokenActivatedImmediatelyIfNoUserPasswordNeedsMigration()
            throws RemoteException {
        final byte[] token = "some-high-entropy-secure-token".getBytes();
        long handle = mLocalService.addEscrowToken(token, PRIMARY_USER_ID, null);
        assertTrue(mLocalService.isEscrowTokenActive(handle, PRIMARY_USER_ID));
        assertEquals(0, mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));
        assertTrue(hasSyntheticPassword(PRIMARY_USER_ID));
    }

    @Test
    public void testEscrowTokenActivatedImmediatelyIfNoUserPasswordNoMigration()
            throws RemoteException {
        final byte[] token = "some-high-entropy-secure-token".getBytes();
        // By first setting a password and then clearing it, we enter the state where SP is
        // initialized but the user currently has no password
        initializeCredential(newPassword("password"), PRIMARY_USER_ID);
        assertTrue(mService.setLockCredential(nonePassword(), newPassword("password"),
                PRIMARY_USER_ID));
        assertTrue(mService.isSyntheticPasswordBasedCredential(PRIMARY_USER_ID));

        long handle = mLocalService.addEscrowToken(token, PRIMARY_USER_ID, null);
        assertTrue(mLocalService.isEscrowTokenActive(handle, PRIMARY_USER_ID));
        assertEquals(0, mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));
        assertTrue(hasSyntheticPassword(PRIMARY_USER_ID));
    }

    @Test
    public void testEscrowTokenActivatedLaterWithUserPassword() throws RemoteException {
        byte[] token = "some-high-entropy-secure-token".getBytes();
        LockscreenCredential password = newPassword("password");
        mService.setLockCredential(password, nonePassword(), PRIMARY_USER_ID);

        long handle = mLocalService.addEscrowToken(token, PRIMARY_USER_ID, null);
        // Token not activated immediately since user password exists
        assertFalse(mLocalService.isEscrowTokenActive(handle, PRIMARY_USER_ID));
        // Activate token
        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                password, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());
        // Verify token is activated
        assertTrue(mLocalService.isEscrowTokenActive(handle, PRIMARY_USER_ID));
    }

    @Test
    public void testEscrowTokenCannotBeActivatedOnUnmanagedUser() {
        byte[] token = "some-high-entropy-secure-token".getBytes();
        when(mUserManagerInternal.isDeviceManaged()).thenReturn(false);
        when(mUserManagerInternal.isUserManaged(PRIMARY_USER_ID)).thenReturn(false);
        when(mDeviceStateCache.isDeviceProvisioned()).thenReturn(true);

        try {
            mLocalService.addEscrowToken(token, PRIMARY_USER_ID, null);
            fail("Escrow token should not be possible on unmanaged device");
        } catch (SecurityException expected) { }
    }

    @Test
    public void testActivateMultipleEscrowTokens() throws Exception {
        byte[] token0 = "some-high-entropy-secure-token-0".getBytes();
        byte[] token1 = "some-high-entropy-secure-token-1".getBytes();
        byte[] token2 = "some-high-entropy-secure-token-2".getBytes();

        LockscreenCredential password = newPassword("password");
        LockscreenCredential pattern = newPattern("123654");
        initializeCredential(password, PRIMARY_USER_ID);

        long handle0 = mLocalService.addEscrowToken(token0, PRIMARY_USER_ID, null);
        long handle1 = mLocalService.addEscrowToken(token1, PRIMARY_USER_ID, null);
        long handle2 = mLocalService.addEscrowToken(token2, PRIMARY_USER_ID, null);

        // Activate token
        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                password, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());

        // Verify tokens work
        assertTrue(mLocalService.isEscrowTokenActive(handle0, PRIMARY_USER_ID));
        assertTrue(mLocalService.setLockCredentialWithToken(
                pattern, handle0, token0, PRIMARY_USER_ID));
        assertTrue(mLocalService.isEscrowTokenActive(handle1, PRIMARY_USER_ID));
        assertTrue(mLocalService.setLockCredentialWithToken(
                pattern, handle1, token1, PRIMARY_USER_ID));
        assertTrue(mLocalService.isEscrowTokenActive(handle2, PRIMARY_USER_ID));
        assertTrue(mLocalService.setLockCredentialWithToken(
                pattern, handle2, token2, PRIMARY_USER_ID));
    }

    @Test
    public void testSetLockCredentialWithTokenFailsWithoutLockScreen() throws Exception {
        LockscreenCredential password = newPassword("password");
        LockscreenCredential pattern = newPattern("123654");
        byte[] token = "some-high-entropy-secure-token".getBytes();

        mService.mHasSecureLockScreen = false;
        long handle = mLocalService.addEscrowToken(token, PRIMARY_USER_ID, null);
        assertTrue(mLocalService.isEscrowTokenActive(handle, PRIMARY_USER_ID));

        try {
            mLocalService.setLockCredentialWithToken(password, handle, token, PRIMARY_USER_ID);
            fail("An exception should have been thrown.");
        } catch (UnsupportedOperationException e) {
            // Success - the exception was expected.
        }
        assertEquals(CREDENTIAL_TYPE_NONE, mService.getCredentialType(PRIMARY_USER_ID));

        try {
            mLocalService.setLockCredentialWithToken(pattern, handle, token, PRIMARY_USER_ID);
            fail("An exception should have been thrown.");
        } catch (UnsupportedOperationException e) {
            // Success - the exception was expected.
        }
        assertEquals(CREDENTIAL_TYPE_NONE, mService.getCredentialType(PRIMARY_USER_ID));
    }

    @Test
    public void testGetHashFactorPrimaryUser() throws RemoteException {
        LockscreenCredential password = newPassword("password");
        mService.setLockCredential(password, nonePassword(), PRIMARY_USER_ID);
        byte[] hashFactor = mService.getHashFactor(password, PRIMARY_USER_ID);
        assertNotNull(hashFactor);

        mService.setLockCredential(nonePassword(), password, PRIMARY_USER_ID);
        byte[] newHashFactor = mService.getHashFactor(nonePassword(), PRIMARY_USER_ID);
        assertNotNull(newHashFactor);
        // Hash factor should never change after password change/removal
        assertArrayEquals(hashFactor, newHashFactor);
    }

    @Test
    public void testGetHashFactorManagedProfileUnifiedChallenge() throws RemoteException {
        LockscreenCredential pattern = newPattern("1236");
        mService.setLockCredential(pattern, nonePassword(), PRIMARY_USER_ID);
        mService.setSeparateProfileChallengeEnabled(MANAGED_PROFILE_USER_ID, false, null);
        assertNotNull(mService.getHashFactor(null, MANAGED_PROFILE_USER_ID));
    }

    @Test
    public void testGetHashFactorManagedProfileSeparateChallenge() throws RemoteException {
        LockscreenCredential primaryPassword = newPassword("primary");
        LockscreenCredential profilePassword = newPassword("profile");
        mService.setLockCredential(primaryPassword, nonePassword(), PRIMARY_USER_ID);
        mService.setLockCredential(profilePassword, nonePassword(), MANAGED_PROFILE_USER_ID);
        assertNotNull(
                mService.getHashFactor(profilePassword, MANAGED_PROFILE_USER_ID));
    }

    // Tests stretching of a nonempty LSKF.
    @Test
    public void testStretchLskf_enabled() {
        byte[] actual = mSpManager.stretchLskf(newPin("12345"), createTestPasswordData());
        String expected = "467986710DE8F0D4F4A3668DFF58C9B7E5DB96A79B7CCF415BBD4D7767F8CFFA";
        assertEquals(expected, HexEncoding.encodeToString(actual));
    }

    // Tests the case where stretching is disabled for an empty LSKF.
    @Test
    public void testStretchLskf_disabled() {
        byte[] actual = mSpManager.stretchLskf(nonePassword(), null);
        // "default-password", zero padded
        String expected = "64656661756C742D70617373776F726400000000000000000000000000000000";
        assertEquals(expected, HexEncoding.encodeToString(actual));
    }

    // Tests the legacy case where stretching is enabled for an empty LSKF.
    @Test
    public void testStretchLskf_emptyButEnabled() {
        byte[] actual = mSpManager.stretchLskf(nonePassword(), createTestPasswordData());
        String expected = "9E6DDCC1EC388BB1E1CD54097AF924CA80BCB90993196FA8F6122FF58EB333DE";
        assertEquals(expected, HexEncoding.encodeToString(actual));
    }

    // Tests the forbidden case where stretching is disabled for a nonempty LSKF.
    @Test
    public void testStretchLskf_nonEmptyButDisabled() {
        assertThrows(IllegalArgumentException.class,
                () -> mSpManager.stretchLskf(newPin("12345"), null));
    }

    private PasswordData createTestPasswordData() {
        PasswordData data = new PasswordData();
        // For the unit test, the scrypt parameters have to be constant; the salt can't be random.
        data.scryptLogN = 11;
        data.scryptLogR = 3;
        data.scryptLogP = 1;
        data.salt = "abcdefghijklmnop".getBytes();
        return data;
    }

    @Test
    public void testPasswordData_serializeDeserialize() {
        PasswordData data = new PasswordData();
        data.scryptLogN = 11;
        data.scryptLogR = 22;
        data.scryptLogP = 33;
        data.credentialType = CREDENTIAL_TYPE_PASSWORD;
        data.salt = PAYLOAD;
        data.passwordHandle = PAYLOAD2;

        PasswordData deserialized = PasswordData.fromBytes(data.toBytes());

        assertEquals(11, deserialized.scryptLogN);
        assertEquals(22, deserialized.scryptLogR);
        assertEquals(33, deserialized.scryptLogP);
        assertEquals(CREDENTIAL_TYPE_PASSWORD, deserialized.credentialType);
        assertArrayEquals(PAYLOAD, deserialized.salt);
        assertArrayEquals(PAYLOAD2, deserialized.passwordHandle);
    }

    @Test
    public void testPasswordData_deserialize() {
        // Test that we can deserialize existing PasswordData and don't inadvertently change the
        // wire format.
        byte[] serialized = new byte[] {
                0, 0, 0, 2, /* CREDENTIAL_TYPE_PASSWORD_OR_PIN */
                11, /* scryptLogN */
                22, /* scryptLogR */
                33, /* scryptLogP */
                0, 0, 0, 5, /* salt.length */
                1, 2, -1, -2, 55, /* salt */
                0, 0, 0, 6, /* passwordHandle.length */
                2, 3, -2, -3, 44, 1, /* passwordHandle */
        };
        PasswordData deserialized = PasswordData.fromBytes(serialized);

        assertEquals(11, deserialized.scryptLogN);
        assertEquals(22, deserialized.scryptLogR);
        assertEquals(33, deserialized.scryptLogP);
        assertEquals(CREDENTIAL_TYPE_PASSWORD_OR_PIN, deserialized.credentialType);
        assertArrayEquals(PAYLOAD, deserialized.salt);
        assertArrayEquals(PAYLOAD2, deserialized.passwordHandle);
    }

    @Test
    public void testGsiDisablesAuthSecret() throws RemoteException {
        mGsiService.setIsGsiRunning(true);

        LockscreenCredential password = newPassword("testGsiDisablesAuthSecret-password");

        initializeCredential(password, PRIMARY_USER_ID);
        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                password, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());
        verify(mAuthSecretService, never()).setPrimaryUserCredential(any(byte[].class));
    }

    @Test
    public void testUnlockUserWithToken() throws Exception {
        LockscreenCredential password = newPassword("password");
        byte[] token = "some-high-entropy-secure-token".getBytes();
        initializeCredential(password, PRIMARY_USER_ID);
        // Disregard any reportPasswordChanged() invocations as part of credential setup.
        flushHandlerTasks();
        reset(mDevicePolicyManager);

        long handle = mLocalService.addEscrowToken(token, PRIMARY_USER_ID, null);
        assertEquals(VerifyCredentialResponse.RESPONSE_OK, mService.verifyCredential(
                password, PRIMARY_USER_ID, 0 /* flags */).getResponseCode());
        assertTrue(mLocalService.isEscrowTokenActive(handle, PRIMARY_USER_ID));

        mService.onCleanupUser(PRIMARY_USER_ID);
        assertNull(mLocalService.getUserPasswordMetrics(PRIMARY_USER_ID));

        assertTrue(mLocalService.unlockUserWithToken(handle, token, PRIMARY_USER_ID));
        assertEquals(PasswordMetrics.computeForCredential(password),
                mLocalService.getUserPasswordMetrics(PRIMARY_USER_ID));
    }

    @Test
    public void testPasswordChange_NoOrphanedFilesLeft() throws Exception {
        LockscreenCredential password = newPassword("password");
        initializeCredential(password, PRIMARY_USER_ID);
        assertTrue(mService.setLockCredential(password, password, PRIMARY_USER_ID));
        assertNoOrphanedFilesLeft(PRIMARY_USER_ID);
    }

    @Test
    public void testAddingEscrowToken_NoOrphanedFilesLeft() throws Exception {
        final byte[] token = "some-high-entropy-secure-token".getBytes();
        for (int i = 0; i < 16; i++) {
            long handle = mLocalService.addEscrowToken(token, PRIMARY_USER_ID, null);
            assertTrue(mLocalService.isEscrowTokenActive(handle, PRIMARY_USER_ID));
            mLocalService.removeEscrowToken(handle, PRIMARY_USER_ID);
        }
        assertNoOrphanedFilesLeft(PRIMARY_USER_ID);
    }

    private void assertNoOrphanedFilesLeft(int userId) {
        String lskfProtectorPrefix = String.format("%016x",
                mService.getCurrentLskfBasedProtectorId(userId));
        File directory = mStorage.getSyntheticPasswordDirectoryForUser(userId);
        for (File file : directory.listFiles()) {
            String[] parts = file.getName().split("\\.");
            if (!parts[0].equals(lskfProtectorPrefix) && !parts[0].equals("0000000000000000")) {
                fail("Orphaned state left: " + file.getName());
            }
        }
    }

    @Test
    public void testHexEncodingIsUppercase() {
        final byte[] raw = new byte[] { (byte)0xAB, (byte)0xCD, (byte)0xEF };
        final byte[] expected = new byte[] { 'A', 'B', 'C', 'D', 'E', 'F' };
        assertArrayEquals(expected, SyntheticPasswordManager.bytesToHex(raw));
    }

    // b/62213311
    //TODO: add non-migration work profile case, and unify/un-unify transition.
    //TODO: test token after user resets password
    //TODO: test token based reset after unified work challenge
    //TODO: test clear password after unified work challenge
}
