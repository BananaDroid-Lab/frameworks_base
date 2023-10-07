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
package com.android.server.pm;

import static android.os.UserManager.DISALLOW_OUTGOING_CALLS;
import static android.os.UserManager.DISALLOW_SMS;
import static android.os.UserManager.DISALLOW_USER_SWITCH;
import static android.os.UserManager.USER_TYPE_FULL_SECONDARY;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.annotation.UserIdInt;
import android.app.ActivityManagerInternal;
import android.content.Context;
import android.content.pm.PackageManagerInternal;
import android.content.pm.UserInfo;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import androidx.test.annotation.UiThreadTest;

import com.android.internal.widget.LockSettingsInternal;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.LocalServices;
import com.android.server.am.UserState;
import com.android.server.pm.UserManagerService.UserData;
import com.android.server.storage.DeviceStorageMonitorInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;

/**
 * Run as {@code atest FrameworksMockingServicesTests:com.android.server.pm.UserManagerServiceTest}
 */
public final class UserManagerServiceTest {

    private static final String TAG = UserManagerServiceTest.class.getSimpleName();

    /**
     * Id for a simple user (that doesn't have profiles).
     */
    private static final int USER_ID = 600;

    /**
     * Id for another simple user.
     */
    private static final int OTHER_USER_ID = 666;

    /**
     * Id for a user that has one profile (whose id is {@link #PROFILE_USER_ID}.
     *
     * <p>You can use {@link #addDefaultProfileAndParent()} to add both of this user to the service.
     */
    private static final int PARENT_USER_ID = 642;

    /**
     * Id for a profile whose parent is {@link #PARENTUSER_ID}.
     *
     * <p>You can use {@link #addDefaultProfileAndParent()} to add both of this user to the service.
     */
    private static final int PROFILE_USER_ID = 643;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .spyStatic(UserManager.class)
            .spyStatic(LocalServices.class)
            .spyStatic(SystemProperties.class)
            .mockStatic(Settings.Global.class)
            .build();

    private final Object mPackagesLock = new Object();
    private final Context mRealContext = androidx.test.InstrumentationRegistry.getInstrumentation()
            .getTargetContext();
    private final SparseArray<UserData> mUsers = new SparseArray<>();

    private File mTestDir;

    private Context mSpiedContext;

    private @Mock PackageManagerService mMockPms;
    private @Mock UserDataPreparer mMockUserDataPreparer;
    private @Mock ActivityManagerInternal mActivityManagerInternal;
    private @Mock DeviceStorageMonitorInternal mDeviceStorageMonitorInternal;
    private @Mock StorageManager mStorageManager;
    private @Mock LockSettingsInternal mLockSettingsInternal;
    private @Mock PackageManagerInternal mPackageManagerInternal;

    /**
     * Reference to the {@link UserManagerService} being tested.
     */
    private UserManagerService mUms;

    /**
     * Reference to the {@link UserManagerInternal} being tested.
     */
    private UserManagerInternal mUmi;

    @Before
    @UiThreadTest // Needed to initialize main handler
    public void setFixtures() {
        mSpiedContext = spy(mRealContext);

        // Called when WatchedUserStates is constructed
        doNothing().when(() -> UserManager.invalidateIsUserUnlockedCache());

        // Called when creating new users
        when(mDeviceStorageMonitorInternal.isMemoryLow()).thenReturn(false);
        mockGetLocalService(DeviceStorageMonitorInternal.class, mDeviceStorageMonitorInternal);
        when(mSpiedContext.getSystemService(StorageManager.class)).thenReturn(mStorageManager);
        mockGetLocalService(LockSettingsInternal.class, mLockSettingsInternal);
        mockGetLocalService(PackageManagerInternal.class, mPackageManagerInternal);
        doNothing().when(mSpiedContext).sendBroadcastAsUser(any(), any(), any());

        // Must construct UserManagerService in the UiThread
        mTestDir = new File(mRealContext.getDataDir(), "umstest");
        mTestDir.mkdirs();
        mUms = new UserManagerService(mSpiedContext, mMockPms, mMockUserDataPreparer,
                mPackagesLock, mTestDir, mUsers);
        mUmi = LocalServices.getService(UserManagerInternal.class);
        assertWithMessage("LocalServices.getService(UserManagerInternal.class)").that(mUmi)
                .isNotNull();
    }

    @After
    public void tearDown() {
        // LocalServices follows the "Highlander rule" - There can be only one!
        LocalServices.removeServiceForTest(UserManagerInternal.class);

        // Clean up test dir to remove persisted user files.
        deleteRecursive(mTestDir);
        mUsers.clear();
    }

    @Test
    public void testGetCurrentUserId_amInternalNotReady() {
        mockGetLocalService(ActivityManagerInternal.class, null);

        assertWithMessage("getCurrentUserId()").that(mUms.getCurrentUserId())
                .isEqualTo(UserHandle.USER_NULL);
    }

    @Test
    public void testGetCurrentAndTargetUserIds() {
        mockCurrentAndTargetUser(USER_ID, OTHER_USER_ID);

        assertWithMessage("getCurrentAndTargetUserIds()")
                .that(mUms.getCurrentAndTargetUserIds())
                .isEqualTo(new Pair<>(USER_ID, OTHER_USER_ID));
    }

    @Test
    public void testGetCurrentUserId() {
        mockCurrentUser(USER_ID);

        assertWithMessage("getCurrentUserId()").that(mUms.getCurrentUserId())
                .isEqualTo(USER_ID);
    }

    @Test
    public void testIsCurrentUserOrRunningProfileOfCurrentUser_currentUser() {
        mockCurrentUser(USER_ID);

        assertWithMessage("isCurrentUserOrRunningProfileOfCurrentUser(%s)", USER_ID)
                .that(mUms.isCurrentUserOrRunningProfileOfCurrentUser(USER_ID)).isTrue();
    }

    @Test
    public void testIsCurrentUserOrRunningProfileOfCurrentUser_notCurrentUser() {
        mockCurrentUser(OTHER_USER_ID);

        assertWithMessage("isCurrentUserOrRunningProfileOfCurrentUser(%s)", USER_ID)
                .that(mUms.isCurrentUserOrRunningProfileOfCurrentUser(USER_ID)).isFalse();
    }

    @Test
    public void testIsCurrentUserOrRunningProfileOfCurrentUser_startedProfileOfCurrentUser() {
        addDefaultProfileAndParent();
        startDefaultProfile();
        mockCurrentUser(PARENT_USER_ID);

        assertWithMessage("isCurrentUserOrRunningProfileOfCurrentUser(%s)", PROFILE_USER_ID)
                .that(mUms.isCurrentUserOrRunningProfileOfCurrentUser(PROFILE_USER_ID)).isTrue();
    }

    @Test
    public void testIsCurrentUserOrRunningProfileOfCurrentUser_stoppedProfileOfCurrentUser() {
        addDefaultProfileAndParent();
        stopDefaultProfile();
        mockCurrentUser(PARENT_USER_ID);

        assertWithMessage("isCurrentUserOrRunningProfileOfCurrentUser(%s)", PROFILE_USER_ID)
                .that(mUms.isCurrentUserOrRunningProfileOfCurrentUser(PROFILE_USER_ID)).isFalse();
    }

    @Test
    public void testIsCurrentUserOrRunningProfileOfCurrentUser_profileOfNonCurrentUSer() {
        addDefaultProfileAndParent();
        mockCurrentUser(OTHER_USER_ID);

        assertWithMessage("isCurrentUserOrRunningProfileOfCurrentUser(%s)", PROFILE_USER_ID)
                .that(mUms.isCurrentUserOrRunningProfileOfCurrentUser(PROFILE_USER_ID)).isFalse();
    }

    @Test
    public void testIsUserRunning_StartedUserShouldReturnTrue() {
        addUser(USER_ID);
        startUser(USER_ID);

        assertWithMessage("isUserRunning(%s)", USER_ID)
                .that(mUms.isUserRunning(USER_ID)).isTrue();
    }

    @Test
    public void testIsUserRunning_StoppedUserShouldReturnFalse() {
        addUser(USER_ID);
        stopUser(USER_ID);

        assertWithMessage("isUserRunning(%s)", USER_ID)
                .that(mUms.isUserRunning(USER_ID)).isFalse();
    }

    @Test
    public void testIsUserRunning_CurrentUserStartedWorkProfileShouldReturnTrue() {
        addDefaultProfileAndParent();
        startDefaultProfile();

        assertWithMessage("isUserRunning(%s)", PROFILE_USER_ID)
                .that(mUms.isUserRunning(PROFILE_USER_ID)).isTrue();
    }

    @Test
    public void testIsUserRunning_CurrentUserStoppedWorkProfileShouldReturnFalse() {
        addDefaultProfileAndParent();
        stopDefaultProfile();

        assertWithMessage("isUserRunning(%s)", PROFILE_USER_ID)
                .that(mUms.isUserRunning(PROFILE_USER_ID)).isFalse();
    }

    @Test
    public void testSetBootUser_SuppliedUserIsSwitchable() throws Exception {
        addUser(USER_ID);
        addUser(OTHER_USER_ID);

        mUms.setBootUser(OTHER_USER_ID);

        assertWithMessage("getBootUser")
                .that(mUmi.getBootUser(/* waitUntilSet= */ false)).isEqualTo(OTHER_USER_ID);
    }

    @Test
    public void testSetBootUser_NotHeadless_SuppliedUserIsNotSwitchable() throws Exception {
        setSystemUserHeadless(false);
        addUser(USER_ID);
        addUser(OTHER_USER_ID);
        addDefaultProfileAndParent();

        mUms.setBootUser(PROFILE_USER_ID);

        assertWithMessage("getBootUser")
                .that(mUmi.getBootUser(/* waitUntilSet= */ false))
                .isEqualTo(UserHandle.USER_SYSTEM);
    }

    @Test
    public void testSetBootUser_Headless_SuppliedUserIsNotSwitchable() throws Exception {
        setSystemUserHeadless(true);
        addUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        addUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 2_000_000L);
        addDefaultProfileAndParent();

        mUms.setBootUser(PROFILE_USER_ID);
        // Boot user not switchable so return most recently in foreground.
        assertWithMessage("getBootUser")
                .that(mUmi.getBootUser(/* waitUntilSet= */ false)).isEqualTo(OTHER_USER_ID);
    }

    @Test
    public void testGetBootUser_NotHeadless_ReturnsSystemUser() throws Exception {
        setSystemUserHeadless(false);
        addUser(USER_ID);
        addUser(OTHER_USER_ID);

        assertWithMessage("getBootUser")
                .that(mUmi.getBootUser(/* waitUntilSet= */ false))
                .isEqualTo(UserHandle.USER_SYSTEM);
    }

    @Test
    public void testGetBootUser_Headless_ReturnsMostRecentlyInForeground() throws Exception {
        setSystemUserHeadless(true);
        addUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);

        addUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 2_000_000L);

        assertWithMessage("getBootUser")
                .that(mUmi.getBootUser(/* waitUntilSet= */ false)).isEqualTo(OTHER_USER_ID);
    }

    @Test
    public void testGetBootUser_Headless_ThrowsIfOnlySystemUserExists() throws Exception {
        setSystemUserHeadless(true);
        removeNonSystemUsers();

        assertThrows(UserManager.CheckedUserOperationException.class,
                () -> mUmi.getBootUser(/* waitUntilSet= */ false));
    }

    @Test
    public void testGetPreviousFullUserToEnterForeground() throws Exception {
        addUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        addUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 2_000_000L);

        assertWithMessage("getPreviousFullUserToEnterForeground")
                .that(mUms.getPreviousFullUserToEnterForeground())
                .isEqualTo(OTHER_USER_ID);
    }

    @Test
    public void testGetPreviousFullUserToEnterForeground_SkipsCurrentUser() throws Exception {
        addUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        addUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 2_000_000L);

        mockCurrentUser(OTHER_USER_ID);
        assertWithMessage("getPreviousFullUserToEnterForeground should skip current user")
                .that(mUms.getPreviousFullUserToEnterForeground())
                .isEqualTo(USER_ID);
    }

    @Test
    public void testGetPreviousFullUserToEnterForeground_SkipsNonFullUsers() throws Exception {
        addUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        addUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 2_000_000L);

        mUsers.get(OTHER_USER_ID).info.flags &= ~UserInfo.FLAG_FULL;
        assertWithMessage("getPreviousFullUserToEnterForeground should skip non-full users")
                .that(mUms.getPreviousFullUserToEnterForeground())
                .isEqualTo(USER_ID);
    }

    @Test
    public void testGetPreviousFullUserToEnterForeground_SkipsPartialUsers() throws Exception {
        addUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        addUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 2_000_000L);

        mUsers.get(OTHER_USER_ID).info.partial = true;
        assertWithMessage("getPreviousFullUserToEnterForeground should skip partial users")
                .that(mUms.getPreviousFullUserToEnterForeground())
                .isEqualTo(USER_ID);
    }

    @Test
    public void testGetPreviousFullUserToEnterForeground_SkipsDisabledUsers() throws Exception {
        addUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        addUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 2_000_000L);

        mUsers.get(OTHER_USER_ID).info.flags |= UserInfo.FLAG_DISABLED;
        assertWithMessage("getPreviousFullUserToEnterForeground should skip disabled users")
                .that(mUms.getPreviousFullUserToEnterForeground())
                .isEqualTo(USER_ID);
    }

    @Test
    public void testGetPreviousFullUserToEnterForeground_SkipsRemovingUsers() throws Exception {
        addUser(USER_ID);
        setLastForegroundTime(USER_ID, 1_000_000L);
        addUser(OTHER_USER_ID);
        setLastForegroundTime(OTHER_USER_ID, 2_000_000L);

        mUms.addRemovingUserId(OTHER_USER_ID);
        assertWithMessage("getPreviousFullUserToEnterForeground should skip removing users")
                .that(mUms.getPreviousFullUserToEnterForeground())
                .isEqualTo(USER_ID);
    }

    @Test
    public void assertIsUserSwitcherEnabledOnMultiUserSettings() throws Exception {
        resetUserSwitcherEnabled();

        mockUserSwitcherEnabled(false);
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isFalse();

        mockUserSwitcherEnabled(true);
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isTrue();
    }

    @Test
    public void assertIsUserSwitcherEnabledOnMaxSupportedUsers()  throws Exception {
        resetUserSwitcherEnabled();

        mockMaxSupportedUsers(/* maxUsers= */ 1);
        assertThat(UserManager.supportsMultipleUsers()).isFalse();
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isFalse();

        mockMaxSupportedUsers(/* maxUsers= */ 8);
        assertThat(UserManager.supportsMultipleUsers()).isTrue();
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isTrue();
    }

    @Test
    public void assertIsUserSwitcherEnabled()  throws Exception {
        resetUserSwitcherEnabled();

        mockMaxSupportedUsers(/* maxUsers= */ 8);
        assertThat(mUms.isUserSwitcherEnabled(true, USER_ID)).isTrue();

        mockUserSwitcherEnabled(false);
        assertThat(mUms.isUserSwitcherEnabled(true, USER_ID)).isFalse();

        mockUserSwitcherEnabled(true);
        assertThat(mUms.isUserSwitcherEnabled(false, USER_ID)).isTrue();

        mUms.setUserRestriction(DISALLOW_USER_SWITCH, true, USER_ID);
        assertThat(mUms.isUserSwitcherEnabled(false, USER_ID)).isFalse();

        mUms.setUserRestriction(DISALLOW_USER_SWITCH, false, USER_ID);
        mockMaxSupportedUsers(1);
        assertThat(mUms.isUserSwitcherEnabled(true, USER_ID)).isFalse();
    }

    @Test
    public void assertIsUserSwitcherEnabledOnShowMultiuserUI()  throws Exception {
        resetUserSwitcherEnabled();

        mockShowMultiuserUI(/* show= */ false);
        assertThat(UserManager.supportsMultipleUsers()).isFalse();
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isFalse();

        mockShowMultiuserUI(/* show= */ true);
        assertThat(UserManager.supportsMultipleUsers()).isTrue();
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isTrue();
    }

    @Test
    public void assertIsUserSwitcherEnabledOnUserRestrictions() throws Exception {
        resetUserSwitcherEnabled();

        mUms.setUserRestriction(DISALLOW_USER_SWITCH, true, USER_ID);
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isFalse();

        mUms.setUserRestriction(DISALLOW_USER_SWITCH, false, USER_ID);
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isTrue();
    }

    @Test
    public void assertIsUserSwitcherEnabledOnDemoMode() throws Exception {
        resetUserSwitcherEnabled();

        mockDeviceDemoMode(/* enabled= */ true);
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isFalse();

        mockDeviceDemoMode(/* enabled= */ false);
        assertThat(mUms.isUserSwitcherEnabled(USER_ID)).isTrue();
    }

    @Test
    public void testMainUser_hasNoCallsOrSMSRestrictionsByDefault() {
        // Remove the main user so we can add another one
        for (int i = 0; i < mUsers.size(); i++) {
            UserData userData = mUsers.valueAt(i);
            if (userData.info.isMain()) {
                mUsers.delete(i);
                break;
            }
        }
        UserInfo mainUser = mUms.createUserWithThrow("main user", USER_TYPE_FULL_SECONDARY,
                UserInfo.FLAG_FULL | UserInfo.FLAG_MAIN);

        assertThat(mUms.hasUserRestriction(DISALLOW_OUTGOING_CALLS, mainUser.id))
                .isFalse();
        assertThat(mUms.hasUserRestriction(DISALLOW_SMS, mainUser.id))
                .isFalse();
    }

    @Test
    public void testCreateUserWithLongName_TruncatesName() {
        UserInfo user = mUms.createUserWithThrow(generateLongString(), USER_TYPE_FULL_SECONDARY, 0);
        assertThat(user.name.length()).isEqualTo(500);
        UserInfo user1 = mUms.createUserWithThrow("Test", USER_TYPE_FULL_SECONDARY, 0);
        assertThat(user1.name.length()).isEqualTo(4);
    }

    private String generateLongString() {
        String partialString = "Test Name Test Name Test Name Test Name Test Name Test Name Test "
                + "Name Test Name Test Name Test Name "; //String of length 100
        StringBuilder resultString = new StringBuilder();
        for (int i = 0; i < 660; i++) {
            resultString.append(partialString);
        }
        return resultString.toString();
    }

    private void removeNonSystemUsers() {
        for (UserInfo user : mUms.getUsers(true)) {
            if (!user.getUserHandle().isSystem()) {
                mUms.removeUserInfo(user.id);
            }
        }
    }

    private void resetUserSwitcherEnabled() {
        mUms.putUserInfo(new UserInfo(USER_ID, "Test User", 0));
        mUms.setUserRestriction(DISALLOW_USER_SWITCH, false, USER_ID);
        mockUserSwitcherEnabled(/* enabled= */ true);
        mockDeviceDemoMode(/* enabled= */ false);
        mockMaxSupportedUsers(/* maxUsers= */ 8);
        mockShowMultiuserUI(/* show= */ true);
    }

    private void mockUserSwitcherEnabled(boolean enabled) {
        doReturn(enabled ? 1 : 0).when(() -> Settings.Global.getInt(
                any(), eq(android.provider.Settings.Global.USER_SWITCHER_ENABLED), anyInt()));
    }

    private void mockDeviceDemoMode(boolean enabled) {
        doReturn(enabled ? 1 : 0).when(() -> Settings.Global.getInt(
                any(), eq(android.provider.Settings.Global.DEVICE_DEMO_MODE), anyInt()));
    }

    private void mockMaxSupportedUsers(int maxUsers) {
        doReturn(maxUsers).when(() ->
                SystemProperties.getInt(eq("fw.max_users"), anyInt()));
    }

    private void mockShowMultiuserUI(boolean show) {
        doReturn(show).when(() ->
                SystemProperties.getBoolean(eq("fw.show_multiuserui"), anyBoolean()));
    }

    private void mockCurrentUser(@UserIdInt int userId) {
        mockGetLocalService(ActivityManagerInternal.class, mActivityManagerInternal);

        when(mActivityManagerInternal.getCurrentUserId()).thenReturn(userId);
    }

    private void mockCurrentAndTargetUser(@UserIdInt int currentUserId,
            @UserIdInt int targetUserId) {
        mockGetLocalService(ActivityManagerInternal.class, mActivityManagerInternal);

        when(mActivityManagerInternal.getCurrentAndTargetUserIds())
                .thenReturn(new Pair<>(currentUserId, targetUserId));
    }

    private <T> void mockGetLocalService(Class<T> serviceClass, T service) {
        doReturn(service).when(() -> LocalServices.getService(serviceClass));
    }

    private void addDefaultProfileAndParent() {
        addUser(PARENT_USER_ID);
        addProfile(PROFILE_USER_ID, PARENT_USER_ID);
    }

    private void addProfile(@UserIdInt int profileId, @UserIdInt int parentId) {
        TestUserData profileData = new TestUserData(profileId);
        profileData.info.flags = UserInfo.FLAG_PROFILE;
        profileData.info.profileGroupId = parentId;

        addUserData(profileData);
    }

    private void addUser(@UserIdInt int userId) {
        TestUserData userData = new TestUserData(userId);
        userData.info.flags = UserInfo.FLAG_FULL;
        addUserData(userData);
    }

    private void startDefaultProfile() {
        startUser(PROFILE_USER_ID);
    }

    private void stopDefaultProfile() {
        stopUser(PROFILE_USER_ID);
    }

    private void startUser(@UserIdInt int userId) {
        setUserState(userId, UserState.STATE_RUNNING_UNLOCKED);
    }

    private void stopUser(@UserIdInt int userId) {
        setUserState(userId, UserState.STATE_STOPPING);
    }

    private void setUserState(@UserIdInt int userId, int userState) {
        mUmi.setUserState(userId, userState);
    }

    private void addUserData(TestUserData userData) {
        Log.d(TAG, "Adding " + userData);
        mUsers.put(userData.info.id, userData);
    }

    private void setSystemUserHeadless(boolean headless) {
        UserData systemUser = mUsers.get(UserHandle.USER_SYSTEM);
        if (headless) {
            systemUser.info.flags &= ~UserInfo.FLAG_FULL;
            systemUser.info.userType = UserManager.USER_TYPE_SYSTEM_HEADLESS;
        } else {
            systemUser.info.flags |= UserInfo.FLAG_FULL;
            systemUser.info.userType = UserManager.USER_TYPE_FULL_SYSTEM;
        }
        doReturn(headless).when(() -> UserManager.isHeadlessSystemUserMode());
    }

    private void setLastForegroundTime(@UserIdInt int userId, long timeMillis) {
        UserData userData = mUsers.get(userId);
        userData.mLastEnteredForegroundTimeMillis = timeMillis;
    }

    public boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            for (File item : file.listFiles()) {
                boolean success = deleteRecursive(item);
                if (!success) {
                    return false;
                }
            }
        }
        return file.delete();
    }

    private static final class TestUserData extends UserData {

        @SuppressWarnings("deprecation")
        TestUserData(@UserIdInt int userId) {
            info = new UserInfo();
            info.id = userId;
        }

        @Override
        public String toString() {
            return "TestUserData[" + info.toFullString() + "]";
        }
    }
}
