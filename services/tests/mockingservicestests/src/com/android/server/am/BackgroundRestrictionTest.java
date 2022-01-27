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

package com.android.server.am;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.app.ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_TOP;
import static android.app.ActivityManager.RESTRICTION_LEVEL_ADAPTIVE_BUCKET;
import static android.app.ActivityManager.RESTRICTION_LEVEL_BACKGROUND_RESTRICTED;
import static android.app.ActivityManager.RESTRICTION_LEVEL_EXEMPTED;
import static android.app.ActivityManager.RESTRICTION_LEVEL_RESTRICTED_BUCKET;
import static android.app.usage.UsageStatsManager.REASON_MAIN_FORCED_BY_SYSTEM;
import static android.app.usage.UsageStatsManager.REASON_MAIN_FORCED_BY_USER;
import static android.app.usage.UsageStatsManager.REASON_MAIN_USAGE;
import static android.app.usage.UsageStatsManager.REASON_SUB_FORCED_SYSTEM_FLAG_ABUSE;
import static android.app.usage.UsageStatsManager.REASON_SUB_FORCED_USER_FLAG_INTERACTION;
import static android.app.usage.UsageStatsManager.REASON_SUB_USAGE_USER_INTERACTION;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_EXEMPTED;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_FREQUENT;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_NEVER;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_RESTRICTED;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_WORKING_SET;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.internal.notification.SystemNotificationChannels.ABUSIVE_BACKGROUND_APPS;
import static com.android.server.am.AppBatteryTracker.BATT_DIMEN_BG;
import static com.android.server.am.AppBatteryTracker.BATT_DIMEN_FG;
import static com.android.server.am.AppBatteryTracker.BATT_DIMEN_FGS;
import static com.android.server.am.AppRestrictionController.STOCK_PM_FLAGS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.ActivityManagerInternal;
import android.app.ActivityManagerInternal.AppBackgroundRestrictionListener;
import android.app.ActivityManagerInternal.BindServiceEventListener;
import android.app.ActivityManagerInternal.BroadcastEventListener;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.app.IUidObserver;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.role.RoleManager;
import android.app.usage.AppStandbyInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener;
import android.os.BatteryManagerInternal;
import android.os.BatteryStatsInternal;
import android.os.BatteryUsageStats;
import android.os.Handler;
import android.os.MessageQueue;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UidBatteryConsumer;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.util.Log;
import android.util.Pair;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.AppStateTracker;
import com.android.server.DeviceIdleInternal;
import com.android.server.am.AppBatteryExemptionTracker.AppBatteryExemptionPolicy;
import com.android.server.am.AppBatteryExemptionTracker.UidBatteryStates;
import com.android.server.am.AppBatteryExemptionTracker.UidStateEventWithBattery;
import com.android.server.am.AppBatteryTracker.AppBatteryPolicy;
import com.android.server.am.AppBindServiceEventsTracker.AppBindServiceEventsPolicy;
import com.android.server.am.AppBroadcastEventsTracker.AppBroadcastEventsPolicy;
import com.android.server.am.AppFGSTracker.AppFGSPolicy;
import com.android.server.am.AppMediaSessionTracker.AppMediaSessionPolicy;
import com.android.server.am.AppRestrictionController.NotificationHelper;
import com.android.server.am.AppRestrictionController.UidBatteryUsageProvider;
import com.android.server.am.BaseAppStateTimeEvents.BaseTimeEvent;
import com.android.server.apphibernation.AppHibernationManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.pm.permission.PermissionManagerServiceInternal;
import com.android.server.usage.AppStandbyInternal;
import com.android.server.usage.AppStandbyInternal.AppIdleStateChangeListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.verification.VerificationMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

/**
 * Tests for {@link AppRestrictionController}.
 *
 * Build/Install/Run:
 *  atest FrameworksMockingServicesTests:BackgroundRestrictionTest
 */
@RunWith(AndroidJUnit4.class)
public final class BackgroundRestrictionTest {
    private static final String TAG = BackgroundRestrictionTest.class.getSimpleName();

    private static final int TEST_USER0 = UserHandle.USER_SYSTEM;
    private static final int TEST_USER1 = UserHandle.MIN_SECONDARY_USER_ID;
    private static final int[] TEST_USERS = new int[] {TEST_USER0, TEST_USER1};
    private static final String TEST_PACKAGE_BASE = "test_";
    private static final int TEST_PACKAGE_APPID_BASE = Process.FIRST_APPLICATION_UID;
    private static final int[] TEST_PACKAGE_USER0_UIDS = new int[] {
        UserHandle.getUid(TEST_USER0, TEST_PACKAGE_APPID_BASE + 0),
        UserHandle.getUid(TEST_USER0, TEST_PACKAGE_APPID_BASE + 1),
        UserHandle.getUid(TEST_USER0, TEST_PACKAGE_APPID_BASE + 2),
        UserHandle.getUid(TEST_USER0, TEST_PACKAGE_APPID_BASE + 3),
        UserHandle.getUid(TEST_USER0, TEST_PACKAGE_APPID_BASE + 4),
        UserHandle.getUid(TEST_USER0, TEST_PACKAGE_APPID_BASE + 5),
        UserHandle.getUid(TEST_USER0, TEST_PACKAGE_APPID_BASE + 6),
    };
    private static final int[] TEST_PACKAGE_USER1_UIDS = new int[] {
        UserHandle.getUid(TEST_USER1, TEST_PACKAGE_APPID_BASE + 0),
        UserHandle.getUid(TEST_USER1, TEST_PACKAGE_APPID_BASE + 1),
        UserHandle.getUid(TEST_USER1, TEST_PACKAGE_APPID_BASE + 2),
        UserHandle.getUid(TEST_USER1, TEST_PACKAGE_APPID_BASE + 3),
        UserHandle.getUid(TEST_USER1, TEST_PACKAGE_APPID_BASE + 4),
        UserHandle.getUid(TEST_USER1, TEST_PACKAGE_APPID_BASE + 5),
        UserHandle.getUid(TEST_USER1, TEST_PACKAGE_APPID_BASE + 6),
    };
    private static final int[][] TEST_UIDS = new int[][] {
        TEST_PACKAGE_USER0_UIDS,
        TEST_PACKAGE_USER1_UIDS,
    };
    private static final int[] TEST_STANDBY_BUCKETS = new int[] {
        STANDBY_BUCKET_EXEMPTED,
        STANDBY_BUCKET_ACTIVE,
        STANDBY_BUCKET_WORKING_SET,
        STANDBY_BUCKET_FREQUENT,
        STANDBY_BUCKET_RARE,
        STANDBY_BUCKET_RESTRICTED,
        STANDBY_BUCKET_NEVER,
    };

    private static final int BATTERY_FULL_CHARGE_MAH = 5_000;

    @Mock private ActivityManagerInternal mActivityManagerInternal;
    @Mock private ActivityManagerService mActivityManagerService;
    @Mock private AppOpsManager mAppOpsManager;
    @Mock private AppStandbyInternal mAppStandbyInternal;
    @Mock private AppHibernationManagerInternal mAppHibernationInternal;
    @Mock private AppStateTracker mAppStateTracker;
    @Mock private BatteryManagerInternal mBatteryManagerInternal;
    @Mock private BatteryStatsInternal mBatteryStatsInternal;
    @Mock private DeviceIdleInternal mDeviceIdleInternal;
    @Mock private IActivityManager mIActivityManager;
    @Mock private UserManagerInternal mUserManagerInternal;
    @Mock private PackageManager mPackageManager;
    @Mock private PackageManagerInternal mPackageManagerInternal;
    @Mock private NotificationManager mNotificationManager;
    @Mock private PermissionManagerServiceInternal mPermissionManagerServiceInternal;
    @Mock private MediaSessionManager mMediaSessionManager;
    @Mock private RoleManager mRoleManager;

    private long mCurrentTimeMillis;

    @Captor private ArgumentCaptor<AppStateTracker.BackgroundRestrictedAppListener> mFasListenerCap;
    private AppStateTracker.BackgroundRestrictedAppListener mFasListener;

    @Captor private ArgumentCaptor<AppIdleStateChangeListener> mIdleStateListenerCap;
    private AppIdleStateChangeListener mIdleStateListener;

    @Captor private ArgumentCaptor<IUidObserver> mUidObserversCap;
    private IUidObserver mUidObservers;

    @Captor private ArgumentCaptor<OnActiveSessionsChangedListener> mActiveSessionListenerCap;
    private OnActiveSessionsChangedListener mActiveSessionListener;

    @Captor private ArgumentCaptor<BroadcastEventListener> mBroadcastEventListenerCap;
    private BroadcastEventListener mBroadcastEventListener;

    @Captor private ArgumentCaptor<BindServiceEventListener> mBindServiceEventListenerCap;
    private BindServiceEventListener mBindServiceEventListener;

    private Context mContext = getInstrumentation().getTargetContext();
    private TestBgRestrictionInjector mInjector;
    private AppRestrictionController mBgRestrictionController;
    private AppBatteryTracker mAppBatteryTracker;
    private AppBatteryPolicy mAppBatteryPolicy;
    private AppBatteryExemptionTracker mAppBatteryExemptionTracker;
    private AppBroadcastEventsTracker mAppBroadcastEventsTracker;
    private AppBindServiceEventsTracker mAppBindServiceEventsTracker;
    private AppFGSTracker mAppFGSTracker;
    private AppMediaSessionTracker mAppMediaSessionTracker;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        initController();
    }

    private void initController() throws Exception {
        mInjector = spy(new TestBgRestrictionInjector(mContext));
        mBgRestrictionController = spy(new AppRestrictionController(mInjector,
                    mActivityManagerService));

        doReturn(PROCESS_STATE_FOREGROUND_SERVICE).when(mActivityManagerInternal)
                .getUidProcessState(anyInt());
        doReturn(TEST_USERS).when(mUserManagerInternal).getUserIds();
        for (int userId: TEST_USERS) {
            final ArrayList<AppStandbyInfo> appStandbyInfoList = new ArrayList<>();
            for (int i = 0; i < TEST_STANDBY_BUCKETS.length; i++) {
                final String packageName = TEST_PACKAGE_BASE + i;
                final int uid = UserHandle.getUid(userId, TEST_PACKAGE_APPID_BASE + i);
                appStandbyInfoList.add(new AppStandbyInfo(packageName, TEST_STANDBY_BUCKETS[i]));
                doReturn(uid)
                        .when(mPackageManagerInternal)
                        .getPackageUid(packageName, STOCK_PM_FLAGS, userId);
                doReturn(false)
                        .when(mAppStateTracker)
                        .isAppBackgroundRestricted(uid, packageName);
                doReturn(TEST_STANDBY_BUCKETS[i])
                        .when(mAppStandbyInternal)
                        .getAppStandbyBucket(eq(packageName), eq(userId), anyLong(), anyBoolean());
                doReturn(new String[]{packageName})
                        .when(mPackageManager)
                        .getPackagesForUid(eq(uid));
                doReturn(AppOpsManager.MODE_IGNORED)
                        .when(mAppOpsManager)
                        .checkOpNoThrow(AppOpsManager.OP_ACTIVATE_VPN, uid, packageName);
                doReturn(AppOpsManager.MODE_IGNORED)
                        .when(mAppOpsManager)
                        .checkOpNoThrow(AppOpsManager.OP_ACTIVATE_PLATFORM_VPN, uid, packageName);
                doReturn(PERMISSION_DENIED)
                        .when(mPermissionManagerServiceInternal)
                        .checkUidPermission(uid, ACCESS_BACKGROUND_LOCATION);
                doReturn(PERMISSION_DENIED)
                        .when(mPermissionManagerServiceInternal)
                        .checkPermission(packageName, ACCESS_BACKGROUND_LOCATION, userId);
            }
            doReturn(appStandbyInfoList).when(mAppStandbyInternal).getAppStandbyBuckets(userId);
        }

        doReturn(BATTERY_FULL_CHARGE_MAH * 1000).when(mBatteryManagerInternal)
                .getBatteryFullCharge();

        mBgRestrictionController.onSystemReady();

        verify(mInjector.getAppStateTracker())
                .addBackgroundRestrictedAppListener(mFasListenerCap.capture());
        mFasListener = mFasListenerCap.getValue();
        verify(mInjector.getAppStandbyInternal())
                .addListener(mIdleStateListenerCap.capture());
        mIdleStateListener = mIdleStateListenerCap.getValue();
        verify(mInjector.getIActivityManager())
                .registerUidObserver(mUidObserversCap.capture(),
                    anyInt(), anyInt(), anyString());
        mUidObservers = mUidObserversCap.getValue();
        verify(mAppMediaSessionTracker.mInjector.getMediaSessionManager())
                .addOnActiveSessionsChangedListener(any(), any(), any(),
                        mActiveSessionListenerCap.capture());
        mActiveSessionListener = mActiveSessionListenerCap.getValue();
        verify(mAppBroadcastEventsTracker.mInjector.getActivityManagerInternal())
                .addBroadcastEventListener(mBroadcastEventListenerCap.capture());
        mBroadcastEventListener = mBroadcastEventListenerCap.getValue();
        verify(mAppBindServiceEventsTracker.mInjector.getActivityManagerInternal())
                .addBindServiceEventListener(mBindServiceEventListenerCap.capture());
        mBindServiceEventListener = mBindServiceEventListenerCap.getValue();
    }

    @After
    public void tearDown() {
        mBgRestrictionController.getBackgroundHandlerThread().quitSafely();
    }

    @Test
    public void testInitialLevels() throws Exception {
        final int[] expectedLevels = {
            RESTRICTION_LEVEL_EXEMPTED,
            RESTRICTION_LEVEL_ADAPTIVE_BUCKET,
            RESTRICTION_LEVEL_ADAPTIVE_BUCKET,
            RESTRICTION_LEVEL_ADAPTIVE_BUCKET,
            RESTRICTION_LEVEL_ADAPTIVE_BUCKET,
            RESTRICTION_LEVEL_RESTRICTED_BUCKET,
            RESTRICTION_LEVEL_BACKGROUND_RESTRICTED,
        };
        for (int i = 0; i < TEST_UIDS.length; i++) {
            final int[] uids = TEST_UIDS[i];
            for (int j = 0; j < uids.length; j++) {
                assertEquals(expectedLevels[j],
                        mBgRestrictionController.getRestrictionLevel(uids[j]));
                assertEquals(expectedLevels[j],
                        mBgRestrictionController.getRestrictionLevel(uids[j],
                                TEST_PACKAGE_BASE + j));
            }
        }
    }

    @Test
    public void testTogglingBackgroundRestrict() throws Exception {
        final int testPkgIndex = 2;
        final String testPkgName = TEST_PACKAGE_BASE + testPkgIndex;
        final int testUser = TEST_USER0;
        final int testUid = UserHandle.getUid(testUser, TEST_PACKAGE_APPID_BASE + testPkgIndex);
        final TestAppRestrictionLevelListener listener = new TestAppRestrictionLevelListener();
        final long timeout = 1_000; // ms

        mBgRestrictionController.addAppBackgroundRestrictionListener(listener);

        setBackgroundRestrict(testPkgName, testUid, false, listener);

        // Verify the current settings.
        verifyRestrictionLevel(RESTRICTION_LEVEL_ADAPTIVE_BUCKET, testPkgName, testUid);
        assertEquals(STANDBY_BUCKET_WORKING_SET, mInjector.getAppStandbyInternal()
                .getAppStandbyBucket(testPkgName, testUser, SystemClock.elapsedRealtime(), false));

        // Now toggling ON the background restrict.
        setBackgroundRestrict(testPkgName, testUid, true, listener);

        // We should have been in the background restricted level.
        verifyRestrictionLevel(RESTRICTION_LEVEL_BACKGROUND_RESTRICTED, testPkgName, testUid);

        listener.verify(timeout, testUid, testPkgName, RESTRICTION_LEVEL_BACKGROUND_RESTRICTED);

        // The app should have been put into the restricted standby bucket.
        verify(mInjector.getAppStandbyInternal(), atLeast(1)).restrictApp(
                eq(testPkgName),
                eq(testUser),
                eq(REASON_MAIN_FORCED_BY_USER),
                eq(REASON_SUB_FORCED_USER_FLAG_INTERACTION));

        // Changing to the restricted standby bucket won't make a difference.
        listener.mLatchHolder[0] = new CountDownLatch(1);
        mIdleStateListener.onAppIdleStateChanged(testPkgName, testUser, false,
                STANDBY_BUCKET_RESTRICTED, REASON_MAIN_USAGE);
        waitForIdleHandler(mBgRestrictionController.getBackgroundHandler());
        verifyRestrictionLevel(RESTRICTION_LEVEL_BACKGROUND_RESTRICTED, testPkgName, testUid);
        try {
            listener.verify(timeout, testUid, testPkgName, RESTRICTION_LEVEL_BACKGROUND_RESTRICTED);
            fail("There shouldn't be any level change events");
        } catch (Exception e) {
            // Expected.
        }

        clearInvocations(mInjector.getAppStandbyInternal());

        // Toggling back.
        setBackgroundRestrict(testPkgName, testUid, false, listener);

        // It should have gone back to adaptive level.
        verifyRestrictionLevel(RESTRICTION_LEVEL_ADAPTIVE_BUCKET, testPkgName, testUid);

        // The app standby bucket should be the rare.
        verify(mInjector.getAppStandbyInternal(), atLeast(1)).maybeUnrestrictApp(
                eq(testPkgName),
                eq(testUser),
                eq(REASON_MAIN_FORCED_BY_USER),
                eq(REASON_SUB_FORCED_USER_FLAG_INTERACTION),
                eq(REASON_MAIN_USAGE),
                eq(REASON_SUB_USAGE_USER_INTERACTION));

        listener.verify(timeout, testUid, testPkgName, RESTRICTION_LEVEL_ADAPTIVE_BUCKET);

        clearInvocations(mInjector.getAppStandbyInternal());

        // Now set its UID state active.
        mUidObservers.onUidActive(testUid);

        // Now toggling ON the background restrict.
        setBackgroundRestrict(testPkgName, testUid, true, listener);

        // We should have been in the background restricted level.
        verifyRestrictionLevel(RESTRICTION_LEVEL_BACKGROUND_RESTRICTED, testPkgName, testUid);

        listener.verify(timeout, testUid, testPkgName, RESTRICTION_LEVEL_BACKGROUND_RESTRICTED);

        // The app should have NOT been put into the restricted standby bucket.
        verify(mInjector.getAppStandbyInternal(), never()).restrictApp(
                eq(testPkgName),
                eq(testUser),
                eq(REASON_MAIN_FORCED_BY_USER),
                eq(REASON_SUB_FORCED_USER_FLAG_INTERACTION));

        // Now set its UID to idle.
        mUidObservers.onUidIdle(testUid, false);

        // The app should have been put into the restricted standby bucket because we're idle now.
        verify(mInjector.getAppStandbyInternal(), timeout(timeout).times(1)).restrictApp(
                eq(testPkgName),
                eq(testUser),
                eq(REASON_MAIN_FORCED_BY_USER),
                eq(REASON_SUB_FORCED_USER_FLAG_INTERACTION));
    }

    @Test
    public void testTogglingStandbyBucket() throws Exception {
        final int testPkgIndex = 2;
        final String testPkgName = TEST_PACKAGE_BASE + testPkgIndex;
        final int testUser = TEST_USER0;
        final int testUid = UserHandle.getUid(testUser, TEST_PACKAGE_APPID_BASE + testPkgIndex);
        final TestAppRestrictionLevelListener listener = new TestAppRestrictionLevelListener();
        final long timeout = 1_000; // ms

        mBgRestrictionController.addAppBackgroundRestrictionListener(listener);

        setBackgroundRestrict(testPkgName, testUid, false, listener);

        // Verify the current settings.
        verifyRestrictionLevel(RESTRICTION_LEVEL_ADAPTIVE_BUCKET, testPkgName, testUid);

        for (int bucket: Arrays.asList(STANDBY_BUCKET_ACTIVE, STANDBY_BUCKET_WORKING_SET,
                STANDBY_BUCKET_FREQUENT, STANDBY_BUCKET_RARE)) {
            listener.mLatchHolder[0] = new CountDownLatch(1);
            mIdleStateListener.onAppIdleStateChanged(testPkgName, testUser, false,
                    bucket, REASON_MAIN_USAGE);
            waitForIdleHandler(mBgRestrictionController.getBackgroundHandler());
            verifyRestrictionLevel(RESTRICTION_LEVEL_ADAPTIVE_BUCKET, testPkgName, testUid);

            try {
                listener.verify(timeout, testUid, testPkgName, RESTRICTION_LEVEL_ADAPTIVE_BUCKET);
                fail("There shouldn't be any level change events");
            } catch (Exception e) {
                // Expected.
            }
        }

        // Toggling restricted bucket.
        listener.mLatchHolder[0] = new CountDownLatch(1);
        mIdleStateListener.onAppIdleStateChanged(testPkgName, testUser, false,
                STANDBY_BUCKET_RESTRICTED, REASON_MAIN_USAGE);
        waitForIdleHandler(mBgRestrictionController.getBackgroundHandler());
        verifyRestrictionLevel(RESTRICTION_LEVEL_RESTRICTED_BUCKET, testPkgName, testUid);
        listener.verify(timeout, testUid, testPkgName, RESTRICTION_LEVEL_RESTRICTED_BUCKET);

        // Toggling exempted bucket.
        listener.mLatchHolder[0] = new CountDownLatch(1);
        mIdleStateListener.onAppIdleStateChanged(testPkgName, testUser, false,
                STANDBY_BUCKET_EXEMPTED, REASON_MAIN_FORCED_BY_SYSTEM);
        waitForIdleHandler(mBgRestrictionController.getBackgroundHandler());
        verifyRestrictionLevel(RESTRICTION_LEVEL_EXEMPTED, testPkgName, testUid);
        listener.verify(timeout, testUid, testPkgName, RESTRICTION_LEVEL_EXEMPTED);
    }

    @Test
    public void testBgCurrentDrainMonitor() throws Exception {
        final BatteryUsageStats stats = mock(BatteryUsageStats.class);
        final List<BatteryUsageStats> statsList = Arrays.asList(stats);
        final int testPkgIndex = 2;
        final String testPkgName = TEST_PACKAGE_BASE + testPkgIndex;
        final int testUser = TEST_USER0;
        final int testUid = UserHandle.getUid(testUser,
                TEST_PACKAGE_APPID_BASE + testPkgIndex);
        final int testUid2 = UserHandle.getUid(testUser,
                TEST_PACKAGE_APPID_BASE + testPkgIndex + 1);
        final TestAppRestrictionLevelListener listener = new TestAppRestrictionLevelListener();
        final long timeout =
                AppBatteryTracker.BATTERY_USAGE_STATS_POLLING_INTERVAL_MS_DEBUG * 2;
        final long windowMs = 2_000;
        final float restrictBucketThreshold = 2.0f;
        final float restrictBucketThresholdMah =
                BATTERY_FULL_CHARGE_MAH * restrictBucketThreshold / 100.0f;
        final float bgRestrictedThreshold = 4.0f;
        final float bgRestrictedThresholdMah =
                BATTERY_FULL_CHARGE_MAH * bgRestrictedThreshold / 100.0f;

        DeviceConfigSession<Boolean> bgCurrentDrainMonitor = null;
        DeviceConfigSession<Long> bgCurrentDrainWindow = null;
        DeviceConfigSession<Float> bgCurrentDrainRestrictedBucketThreshold = null;
        DeviceConfigSession<Float> bgCurrentDrainBgRestrictedThreshold = null;

        mBgRestrictionController.addAppBackgroundRestrictionListener(listener);

        setBackgroundRestrict(testPkgName, testUid, false, listener);

        // Verify the current settings.
        verifyRestrictionLevel(RESTRICTION_LEVEL_ADAPTIVE_BUCKET, testPkgName, testUid);

        final double[] zeros = new double[]{0.0f, 0.0f};
        final int[] uids = new int[]{testUid, testUid2};

        try {
            bgCurrentDrainMonitor = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppBatteryPolicy.KEY_BG_CURRENT_DRAIN_MONITOR_ENABLED,
                    DeviceConfig::getBoolean,
                    AppBatteryPolicy.DEFAULT_BG_CURRENT_DRAIN_MONITOR_ENABLED);
            bgCurrentDrainMonitor.set(true);

            bgCurrentDrainWindow = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppBatteryPolicy.KEY_BG_CURRENT_DRAIN_WINDOW,
                    DeviceConfig::getLong,
                    AppBatteryPolicy.DEFAULT_BG_CURRENT_DRAIN_WINDOW_MS);
            bgCurrentDrainWindow.set(windowMs);

            bgCurrentDrainRestrictedBucketThreshold = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppBatteryPolicy.KEY_BG_CURRENT_DRAIN_THRESHOLD_TO_RESTRICTED_BUCKET,
                    DeviceConfig::getFloat,
                    AppBatteryPolicy.DEFAULT_BG_CURRENT_DRAIN_BG_RESTRICTED_THRESHOLD);
            bgCurrentDrainRestrictedBucketThreshold.set(restrictBucketThreshold);

            bgCurrentDrainBgRestrictedThreshold = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppBatteryPolicy.KEY_BG_CURRENT_DRAIN_THRESHOLD_TO_BG_RESTRICTED,
                    DeviceConfig::getFloat,
                    AppBatteryPolicy.DEFAULT_BG_CURRENT_DRAIN_BG_RESTRICTED_THRESHOLD);
            bgCurrentDrainBgRestrictedThreshold.set(bgRestrictedThreshold);

            mCurrentTimeMillis = 10_000L;
            doReturn(mCurrentTimeMillis - windowMs).when(stats).getStatsStartTimestamp();
            doReturn(statsList).when(mBatteryStatsInternal).getBatteryUsageStats(anyObject());

            runTestBgCurrentDrainMonitorOnce(listener, stats, uids,
                    new double[]{restrictBucketThresholdMah - 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    () -> {
                        doReturn(mCurrentTimeMillis).when(stats).getStatsStartTimestamp();
                        mCurrentTimeMillis += windowMs + 1;
                        try {
                            listener.verify(timeout, testUid, testPkgName,
                                    RESTRICTION_LEVEL_ADAPTIVE_BUCKET);
                            fail("There shouldn't be any level change events");
                        } catch (Exception e) {
                            // Expected.
                        }
                    });

            runTestBgCurrentDrainMonitorOnce(listener, stats, uids,
                    new double[]{restrictBucketThresholdMah + 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    () -> {
                        doReturn(mCurrentTimeMillis).when(stats).getStatsStartTimestamp();
                        mCurrentTimeMillis += windowMs + 1;
                        // It should have gone to the restricted bucket.
                        listener.verify(timeout, testUid, testPkgName,
                                RESTRICTION_LEVEL_RESTRICTED_BUCKET);
                        verify(mInjector.getAppStandbyInternal()).restrictApp(
                                eq(testPkgName),
                                eq(testUser),
                                anyInt(), anyInt());
                    });


            runTestBgCurrentDrainMonitorOnce(listener, stats, uids,
                    new double[]{restrictBucketThresholdMah - 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    () -> {
                        doReturn(mCurrentTimeMillis).when(stats).getStatsStartTimestamp();
                        mCurrentTimeMillis += windowMs + 1;
                        // We won't change restriction level until user interactions.
                        try {
                            listener.verify(timeout, testUid, testPkgName,
                                    RESTRICTION_LEVEL_ADAPTIVE_BUCKET);
                            fail("There shouldn't be any level change events");
                        } catch (Exception e) {
                            // Expected.
                        }
                        verify(mInjector.getAppStandbyInternal(), never()).setAppStandbyBucket(
                                eq(testPkgName),
                                eq(STANDBY_BUCKET_RARE),
                                eq(testUser),
                                anyInt(), anyInt());
                    });

            // Trigger user interaction.
            runTestBgCurrentDrainMonitorOnce(listener, stats, uids,
                    new double[]{restrictBucketThresholdMah - 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    () -> {
                        doReturn(mCurrentTimeMillis).when(stats).getStatsStartTimestamp();
                        mCurrentTimeMillis += windowMs + 1;
                        mIdleStateListener.onUserInteractionStarted(testPkgName, testUser);
                        waitForIdleHandler(mBgRestrictionController.getBackgroundHandler());
                        // It should have been back to normal.
                        listener.verify(timeout, testUid, testPkgName,
                                RESTRICTION_LEVEL_ADAPTIVE_BUCKET);
                        verify(mInjector.getAppStandbyInternal(), atLeast(1)).maybeUnrestrictApp(
                                eq(testPkgName),
                                eq(testUser),
                                eq(REASON_MAIN_FORCED_BY_SYSTEM),
                                eq(REASON_SUB_FORCED_SYSTEM_FLAG_ABUSE),
                                eq(REASON_MAIN_USAGE),
                                eq(REASON_SUB_USAGE_USER_INTERACTION));
                    });

            clearInvocations(mInjector.getAppStandbyInternal());

            runTestBgCurrentDrainMonitorOnce(listener, stats, uids,
                    new double[]{restrictBucketThresholdMah + 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    () -> {
                        doReturn(mCurrentTimeMillis).when(stats).getStatsStartTimestamp();
                        mCurrentTimeMillis += windowMs + 1;
                        // It should have gone to the restricted bucket.
                        listener.verify(timeout, testUid, testPkgName,
                                RESTRICTION_LEVEL_RESTRICTED_BUCKET);
                        verify(mInjector.getAppStandbyInternal(), times(1)).restrictApp(
                                eq(testPkgName),
                                eq(testUser),
                                anyInt(), anyInt());
                    });

            clearInvocations(mInjector.getAppStandbyInternal());
            // Drain a bit more, there shouldn't be any level changes.
            runTestBgCurrentDrainMonitorOnce(listener, stats, uids,
                    new double[]{restrictBucketThresholdMah + 2, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    () -> {
                        doReturn(mCurrentTimeMillis).when(stats).getStatsStartTimestamp();
                        mCurrentTimeMillis += windowMs + 1;
                        // We won't change restriction level until user interactions.
                        try {
                            listener.verify(timeout, testUid, testPkgName,
                                    RESTRICTION_LEVEL_ADAPTIVE_BUCKET);
                            fail("There shouldn't be any level change events");
                        } catch (Exception e) {
                            // Expected.
                        }
                        verify(mInjector.getAppStandbyInternal(), never()).setAppStandbyBucket(
                                eq(testPkgName),
                                eq(STANDBY_BUCKET_RARE),
                                eq(testUser),
                                anyInt(), anyInt());
                    });

            // Sleep a while and set a higher drain
            Thread.sleep(windowMs);
            clearInvocations(mInjector.getAppStandbyInternal());
            clearInvocations(mBgRestrictionController);
            runTestBgCurrentDrainMonitorOnce(listener, stats, uids,
                    new double[]{bgRestrictedThresholdMah + 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    () -> {
                        doReturn(mCurrentTimeMillis).when(stats).getStatsStartTimestamp();
                        mCurrentTimeMillis += windowMs + 1;
                        // We won't change restriction level automatically because it needs
                        // user consent.
                        try {
                            listener.verify(timeout, testUid, testPkgName,
                                    RESTRICTION_LEVEL_BACKGROUND_RESTRICTED);
                            fail("There shouldn't be level change event like this");
                        } catch (Exception e) {
                            // Expected.
                        }
                        verify(mInjector.getAppStandbyInternal(), never()).setAppStandbyBucket(
                                eq(testPkgName),
                                eq(STANDBY_BUCKET_RARE),
                                eq(testUser),
                                anyInt(), anyInt());
                        // We should have requested to goto background restricted level.
                        verify(mBgRestrictionController, times(1)).handleRequestBgRestricted(
                                eq(testPkgName),
                                eq(testUid));
                        // Verify we have the notification posted.
                        checkNotificationShown(new String[] {testPkgName}, atLeast(1), true);
                    });

            // Turn ON the FAS for real.
            setBackgroundRestrict(testPkgName, testUid, true, listener);

            // Verify it's background restricted now.
            verifyRestrictionLevel(RESTRICTION_LEVEL_BACKGROUND_RESTRICTED, testPkgName, testUid);
            listener.verify(timeout, testUid, testPkgName, RESTRICTION_LEVEL_BACKGROUND_RESTRICTED);

            // Trigger user interaction.
            mIdleStateListener.onUserInteractionStarted(testPkgName, testUser);
            waitForIdleHandler(mBgRestrictionController.getBackgroundHandler());

            listener.mLatchHolder[0] = new CountDownLatch(1);
            try {
                listener.verify(timeout, testUid, testPkgName,
                        RESTRICTION_LEVEL_ADAPTIVE_BUCKET);
                fail("There shouldn't be level change event like this");
            } catch (Exception e) {
                // Expected.
            }

            // Turn OFF the FAS.
            listener.mLatchHolder[0] = new CountDownLatch(1);
            clearInvocations(mInjector.getAppStandbyInternal());
            clearInvocations(mBgRestrictionController);
            setBackgroundRestrict(testPkgName, testUid, false, listener);

            // It'll go back to restricted bucket because it used to behave poorly.
            listener.verify(timeout, testUid, testPkgName, RESTRICTION_LEVEL_RESTRICTED_BUCKET);
            verifyRestrictionLevel(RESTRICTION_LEVEL_RESTRICTED_BUCKET, testPkgName, testUid);
        } finally {
            closeIfNotNull(bgCurrentDrainMonitor);
            closeIfNotNull(bgCurrentDrainWindow);
            closeIfNotNull(bgCurrentDrainRestrictedBucketThreshold);
            closeIfNotNull(bgCurrentDrainBgRestrictedThreshold);
        }
    }

    @Test
    public void testLongFGSMonitor() throws Exception {
        final int testPkgIndex1 = 1;
        final String testPkgName1 = TEST_PACKAGE_BASE + testPkgIndex1;
        final int testUser1 = TEST_USER0;
        final int testUid1 = UserHandle.getUid(testUser1, TEST_PACKAGE_APPID_BASE + testPkgIndex1);
        final int testPid1 = 1234;

        final int testPkgIndex2 = 2;
        final String testPkgName2 = TEST_PACKAGE_BASE + testPkgIndex2;
        final int testUser2 = TEST_USER0;
        final int testUid2 = UserHandle.getUid(testUser2, TEST_PACKAGE_APPID_BASE + testPkgIndex2);
        final int testPid2 = 1235;

        final long windowMs = 2_000;
        final long thresholdMs = 1_000;
        final long shortMs = 100;

        DeviceConfigSession<Boolean> longRunningFGSMonitor = null;
        DeviceConfigSession<Long> longRunningFGSWindow = null;
        DeviceConfigSession<Long> longRunningFGSThreshold = null;

        try {
            longRunningFGSMonitor = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppFGSPolicy.KEY_BG_FGS_MONITOR_ENABLED,
                    DeviceConfig::getBoolean,
                    AppFGSPolicy.DEFAULT_BG_FGS_MONITOR_ENABLED);
            longRunningFGSMonitor.set(true);

            longRunningFGSWindow = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppFGSPolicy.KEY_BG_FGS_LONG_RUNNING_WINDOW,
                    DeviceConfig::getLong,
                    AppFGSPolicy.DEFAULT_BG_FGS_LONG_RUNNING_WINDOW);
            longRunningFGSWindow.set(windowMs);

            longRunningFGSThreshold = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppFGSPolicy.KEY_BG_FGS_LONG_RUNNING_THRESHOLD,
                    DeviceConfig::getLong,
                    AppFGSPolicy.DEFAULT_BG_FGS_LONG_RUNNING_THRESHOLD);
            longRunningFGSThreshold.set(thresholdMs);

            // Basic case
            mAppFGSTracker.onForegroundServiceStateChanged(testPkgName1, testUid1,
                    testPid1, true);
            // Verify we have the notification, it'll include the summary notification though.
            int notificationId = checkNotificationShown(
                    new String[] {testPkgName1}, timeout(windowMs * 2).times(2), true)[0];

            clearInvocations(mInjector.getNotificationManager());
            // Sleep a while, verify it won't show another notification.
            Thread.sleep(windowMs * 2);
            checkNotificationShown(
                    new String[] {testPkgName1}, timeout(windowMs * 2).times(0), false);

            // Stop this FGS
            mAppFGSTracker.onForegroundServiceStateChanged(testPkgName1, testUid1,
                    testPid1, false);
            checkNotificationGone(testPkgName1, timeout(windowMs), notificationId);

            clearInvocations(mInjector.getNotificationManager());
            // Start another one and stop it.
            mAppFGSTracker.onForegroundServiceStateChanged(testPkgName2, testUid2,
                    testPid2, true);
            Thread.sleep(shortMs);
            mAppFGSTracker.onForegroundServiceStateChanged(testPkgName2, testUid2,
                    testPid2, false);

            // Not long enough, it shouldn't show notification in this case.
            checkNotificationShown(
                    new String[] {testPkgName2}, timeout(windowMs * 2).times(0), false);

            clearInvocations(mInjector.getNotificationManager());
            // Start the FGS again.
            mAppFGSTracker.onForegroundServiceStateChanged(testPkgName2, testUid2,
                    testPid2, true);
            // Verify we have the notification.
            notificationId = checkNotificationShown(
                    new String[] {testPkgName2}, timeout(windowMs * 2).times(2), true)[0];

            // Stop this FGS
            mAppFGSTracker.onForegroundServiceStateChanged(testPkgName2, testUid2,
                    testPid2, false);
            checkNotificationGone(testPkgName2, timeout(windowMs), notificationId);

            // Start over with concurrent cases.
            clearInvocations(mInjector.getNotificationManager());
            mBgRestrictionController.resetRestrictionSettings();
            mAppFGSTracker.onForegroundServiceStateChanged(testPkgName2, testUid2,
                    testPid2, true);
            Thread.sleep(shortMs);
            mAppFGSTracker.onForegroundServiceStateChanged(testPkgName1, testUid1,
                    testPid1, true);

            // Verify we've seen both notifications, and test pkg2 should be shown before test pkg1.
            int[] notificationIds = checkNotificationShown(
                    new String[] {testPkgName2, testPkgName1},
                    timeout(windowMs * 2).times(4), true);

            // Stop both of them.
            mAppFGSTracker.onForegroundServiceStateChanged(testPkgName1, testUid1,
                    testPid1, false);
            checkNotificationGone(testPkgName1, timeout(windowMs), notificationIds[1]);
            clearInvocations(mInjector.getNotificationManager());
            mAppFGSTracker.onForegroundServiceStateChanged(testPkgName2, testUid2,
                    testPid2, false);
            checkNotificationGone(testPkgName2, timeout(windowMs), notificationIds[0]);

            // Test the interlaced case.
            clearInvocations(mInjector.getNotificationManager());
            mBgRestrictionController.resetRestrictionSettings();
            mAppFGSTracker.reset();
            mAppFGSTracker.onForegroundServiceStateChanged(testPkgName1, testUid1,
                    testPid1, true);

            final long initialWaitMs = thresholdMs / 2;
            Thread.sleep(initialWaitMs);

            for (long remaining = thresholdMs - initialWaitMs; remaining > 0;) {
                mAppFGSTracker.onForegroundServiceStateChanged(testPkgName1, testUid1,
                        testPid1, false);
                mAppFGSTracker.onForegroundServiceStateChanged(testPkgName2, testUid2,
                        testPid2, true);
                Thread.sleep(shortMs);
                mAppFGSTracker.onForegroundServiceStateChanged(testPkgName1, testUid1,
                        testPid1, true);
                mAppFGSTracker.onForegroundServiceStateChanged(testPkgName2, testUid2,
                        testPid2, false);
                Thread.sleep(shortMs);
                remaining -= shortMs;
            }

            // Verify test pkg1 got the notification, but not test pkg2.
            notificationId = checkNotificationShown(
                    new String[] {testPkgName1}, timeout(windowMs).times(2), true)[0];

            clearInvocations(mInjector.getNotificationManager());
            // Stop the FGS.
            mAppFGSTracker.onForegroundServiceStateChanged(testPkgName1, testUid1,
                    testPid1, false);
            checkNotificationGone(testPkgName1, timeout(windowMs), notificationId);
        } finally {
            closeIfNotNull(longRunningFGSMonitor);
            closeIfNotNull(longRunningFGSWindow);
            closeIfNotNull(longRunningFGSThreshold);
        }
    }

    @Test
    public void testLongFGSExemptions() throws Exception {
        final int testPkgIndex1 = 1;
        final String testPkgName1 = TEST_PACKAGE_BASE + testPkgIndex1;
        final int testUser1 = TEST_USER0;
        final int testUid1 = UserHandle.getUid(testUser1, TEST_PACKAGE_APPID_BASE + testPkgIndex1);
        final int testPid1 = 1234;

        final int testPkgIndex2 = 2;
        final String testPkgName2 = TEST_PACKAGE_BASE + testPkgIndex2;
        final int testUser2 = TEST_USER0;
        final int testUid2 = UserHandle.getUid(testUser2, TEST_PACKAGE_APPID_BASE + testPkgIndex2);
        final int testPid2 = 1235;

        final long windowMs = 2_000;
        final long thresholdMs = 1_000;

        DeviceConfigSession<Boolean> longRunningFGSMonitor = null;
        DeviceConfigSession<Long> longRunningFGSWindow = null;
        DeviceConfigSession<Long> longRunningFGSThreshold = null;
        DeviceConfigSession<Long> mediaPlaybackFGSThreshold = null;
        DeviceConfigSession<Long> locationFGSThreshold = null;

        doReturn(testPkgName1).when(mInjector).getPackageName(testPid1);
        doReturn(testPkgName2).when(mInjector).getPackageName(testPid2);

        try {
            longRunningFGSMonitor = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppFGSPolicy.KEY_BG_FGS_MONITOR_ENABLED,
                    DeviceConfig::getBoolean,
                    AppFGSPolicy.DEFAULT_BG_FGS_MONITOR_ENABLED);
            longRunningFGSMonitor.set(true);

            longRunningFGSWindow = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppFGSPolicy.KEY_BG_FGS_LONG_RUNNING_WINDOW,
                    DeviceConfig::getLong,
                    AppFGSPolicy.DEFAULT_BG_FGS_LONG_RUNNING_WINDOW);
            longRunningFGSWindow.set(windowMs);

            longRunningFGSThreshold = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppFGSPolicy.KEY_BG_FGS_LONG_RUNNING_THRESHOLD,
                    DeviceConfig::getLong,
                    AppFGSPolicy.DEFAULT_BG_FGS_LONG_RUNNING_THRESHOLD);
            longRunningFGSThreshold.set(thresholdMs);

            mediaPlaybackFGSThreshold = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppFGSPolicy.KEY_BG_FGS_MEDIA_PLAYBACK_THRESHOLD,
                    DeviceConfig::getLong,
                    AppFGSPolicy.DEFAULT_BG_FGS_MEDIA_PLAYBACK_THRESHOLD);
            mediaPlaybackFGSThreshold.set(thresholdMs);

            locationFGSThreshold = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppFGSPolicy.KEY_BG_FGS_LOCATION_THRESHOLD,
                    DeviceConfig::getLong,
                    AppFGSPolicy.DEFAULT_BG_FGS_LOCATION_THRESHOLD);
            locationFGSThreshold.set(thresholdMs);

            // Long-running FGS with type "location", but ran for a very short time.
            runTestLongFGSExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_LOCATION, 0, null, null, null,
                    timeout(windowMs * 2).times(2));

            // Long-running FGS with type "location", and ran for a while.
            // We shouldn't see notifications in this case.
            runTestLongFGSExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_LOCATION, thresholdMs * 2, null, null, null,
                    timeout(windowMs * 2).times(0));

            // Long-running FGS with background location permission.
            runTestLongFGSExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_LOCATION, 0, ACCESS_BACKGROUND_LOCATION, null, null,
                    timeout(windowMs * 2).times(0));

            // Long-running FGS with type "mediaPlayback", but ran for a very short time.
            runTestLongFGSExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, 0, null, null, null,
                    timeout(windowMs * 2).times(2));

            // Long-running FGS with type "mediaPlayback", and ran for a while.
            // We shouldn't see notifications in this case.
            runTestLongFGSExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, thresholdMs * 2, null, null, null,
                    timeout(windowMs * 2).times(0));

            // Long-running FGS with type "camera", and ran for a while.
            // We shouldn't see notifications in this case.
            runTestLongFGSExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_CAMERA, thresholdMs * 2, null, null, null,
                    timeout(windowMs * 2).times(0));

            // Long-running FGS with type "location|mediaPlayback", but ran for a very short time.
            runTestLongFGSExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_LOCATION | FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                    0, null, null, null, timeout(windowMs * 2).times(2));

            // Long-running FGS with type "location|mediaPlayback", and ran for a while.
            // We shouldn't see notifications in this case.
            runTestLongFGSExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_LOCATION | FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                    thresholdMs * 2, null, null, null, timeout(windowMs * 2).times(0));

            // Long-running FGS with a media session starts/stops right away.
            runTestLongFGSExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_NONE, 0, null,
                    List.of(Pair.create(createMediaControllers(
                            new String[] {testPkgName1}, new int[] {testUid1}), 0L)), null,
                    timeout(windowMs * 2).times(2));

            // Long-running FGS with media session, and ran for a while.
            // We shouldn't see notifications in this case.
            runTestLongFGSExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_NONE, thresholdMs * 2, null,
                    List.of(Pair.create(createMediaControllers(new String[] {testPkgName1},
                            new int[] {testUid1}), thresholdMs * 2)), null,
                    timeout(windowMs * 2).times(0));

            // Long-running FGS with 2 media sessions start/stop right away
            runTestLongFGSExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_NONE, 0, null,
                    List.of(Pair.create(createMediaControllers(
                            new String[] {testPkgName1, testPkgName2},
                            new int[] {testUid1, testUid2}), 0L)), null,
                    timeout(windowMs * 2).times(2));

            // Long-running FGS with 2 media sessions start/stop interlaced.
            runTestLongFGSExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_NONE, 0, null,
                    List.of(Pair.create(createMediaControllers(
                                    new String[] {testPkgName1, testPkgName2},
                                    new int[] {testUid1, testUid2}), thresholdMs),
                            Pair.create(createMediaControllers(
                                    new String[] {testPkgName1},
                                    new int[] {testUid1}), thresholdMs / 10),
                            Pair.create(createMediaControllers(
                                    new String[] {testPkgName2},
                                    new int[] {testUid2}), thresholdMs / 10),
                            Pair.create(createMediaControllers(
                                    new String[] {testPkgName1},
                                    new int[] {testUid1}), thresholdMs / 10)
                            ), null,
                    timeout(windowMs * 2).times(0));

            // Long-running FGS with top state for a very short time.
            runTestLongFGSExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_NONE, 0, null, null, List.of(0L),
                    timeout(windowMs * 2).times(2));

            // Long-running FGS with top state for extended time.
            runTestLongFGSExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_NONE, 0, null, null, List.of(0L, windowMs * 2, 0L),
                    timeout(windowMs * 2).times(0));

            // Long-running FGS with top state, on and off frequently.
            runTestLongFGSExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_NONE, 0, null, null,
                    List.of(0L, thresholdMs / 10, thresholdMs / 10, thresholdMs / 10,
                            thresholdMs / 10, thresholdMs / 10, thresholdMs / 10),
                    timeout(windowMs * 2).times(2));
        } finally {
            closeIfNotNull(longRunningFGSMonitor);
            closeIfNotNull(longRunningFGSWindow);
            closeIfNotNull(longRunningFGSThreshold);
            closeIfNotNull(mediaPlaybackFGSThreshold);
            closeIfNotNull(locationFGSThreshold);
        }
    }

    private void resetBgRestrictionController() {
        mBgRestrictionController.resetRestrictionSettings();
        waitForIdleHandler(mBgRestrictionController.getBackgroundHandler());
    }

    private void runTestLongFGSExemptionOnce(String packageName, int uid, int pid,
            int serviceType, long sleepMs, String perm,
            List<Pair<List<MediaController>, Long>> mediaControllers, List<Long> topStateChanges,
            VerificationMode mode) throws Exception {
        runExemptionTestOnce(
                packageName, uid, pid, serviceType, sleepMs, true, perm, mediaControllers,
                topStateChanges, true, true,
                () -> checkNotificationShown(new String[] {packageName}, mode, false)
        );
    }

    private void runExemptionTestOnce(String packageName, int uid, int pid,
            int serviceType, long sleepMs, boolean stopAfterSleep, String perm,
            List<Pair<List<MediaController>, Long>> mediaControllers,
            List<Long> topStateChanges, boolean resetFGSTracker, boolean resetController,
            RunnableWithException r) throws Exception {
        if (resetFGSTracker) {
            mAppFGSTracker.reset();
            mAppMediaSessionTracker.reset();
        }
        if (resetController) {
            resetBgRestrictionController();
        }
        clearInvocations(mInjector.getNotificationManager());

        Thread topStateThread = null;
        if (topStateChanges != null) {
            final CountDownLatch latch = new CountDownLatch(1);
            topStateThread = new Thread(() -> {
                try {
                    latch.await();
                    boolean top = false;
                    for (long l: topStateChanges) {
                        mUidObservers.onUidStateChanged(uid,
                                top ? PROCESS_STATE_TOP : PROCESS_STATE_FOREGROUND_SERVICE,
                                0, 0);
                        top = !top;
                        Thread.sleep(l);
                    }
                    mUidObservers.onUidGone(uid, false);
                } catch (InterruptedException | RemoteException e) {
                }
            });
            topStateThread.start();
            latch.countDown();
        }

        mAppFGSTracker.onForegroundServiceStateChanged(packageName, uid, pid, true);
        if (serviceType != FOREGROUND_SERVICE_TYPE_NONE) {
            mAppFGSTracker.mProcessObserver.onForegroundServicesChanged(pid, uid, serviceType);
            Thread.sleep(sleepMs);
            if (stopAfterSleep) {
                // Stop it now.
                mAppFGSTracker.mProcessObserver.onForegroundServicesChanged(pid, uid,
                        FOREGROUND_SERVICE_TYPE_NONE);
            }
        }

        if (perm != null) {
            doReturn(PERMISSION_GRANTED)
                    .when(mPermissionManagerServiceInternal)
                    .checkPermission(packageName, perm, UserHandle.getUserId(uid));
            doReturn(PERMISSION_GRANTED)
                    .when(mPermissionManagerServiceInternal)
                    .checkUidPermission(uid, ACCESS_BACKGROUND_LOCATION);
        }

        if (mediaControllers != null) {
            for (Pair<List<MediaController>, Long> entry: mediaControllers) {
                mActiveSessionListener.onActiveSessionsChanged(entry.first);
                Thread.sleep(entry.second);
            }
            if (stopAfterSleep) {
                // Stop it now.
                mActiveSessionListener.onActiveSessionsChanged(null);
            }
        }

        r.run();

        // Stop this FGS
        mAppFGSTracker.onForegroundServiceStateChanged(packageName, uid, pid, false);

        if (perm != null) {
            doReturn(PERMISSION_DENIED)
                    .when(mPermissionManagerServiceInternal)
                    .checkPermission(packageName, perm, UserHandle.getUserId(uid));
            doReturn(PERMISSION_DENIED)
                    .when(mPermissionManagerServiceInternal)
                    .checkUidPermission(uid, ACCESS_BACKGROUND_LOCATION);
        }
        if (topStateThread != null) {
            topStateThread.join();
        }
    }

    private List<MediaController> createMediaControllers(String[] packageNames, int[] uids) {
        final ArrayList<MediaController> controllers = new ArrayList<>();
        for (int i = 0; i < packageNames.length; i++) {
            controllers.add(createMediaController(packageNames[i], uids[i]));
        }
        return controllers;
    }

    private MediaController createMediaController(String packageName, int uid) {
        final MediaController controller = mock(MediaController.class);
        final MediaSession.Token token = mock(MediaSession.Token.class);
        doReturn(packageName).when(controller).getPackageName();
        doReturn(token).when(controller).getSessionToken();
        doReturn(uid).when(token).getUid();
        return controller;
    }

    @Test
    public void testBgCurrentDrainMonitorExemptions() throws Exception {
        final BatteryUsageStats stats = mock(BatteryUsageStats.class);
        final List<BatteryUsageStats> statsList = Arrays.asList(stats);
        final int testPkgIndex1 = 1;
        final String testPkgName1 = TEST_PACKAGE_BASE + testPkgIndex1;
        final int testUser = TEST_USER0;
        final int testUid1 = UserHandle.getUid(testUser,
                TEST_PACKAGE_APPID_BASE + testPkgIndex1);
        final int testPid1 = 1234;
        final int testPkgIndex2 = 2;
        final String testPkgName2 = TEST_PACKAGE_BASE + testPkgIndex2;
        final int testUid2 = UserHandle.getUid(testUser,
                TEST_PACKAGE_APPID_BASE + testPkgIndex2);
        final int testPid2 = 1235;
        final TestAppRestrictionLevelListener listener = new TestAppRestrictionLevelListener();
        final long timeout =
                AppBatteryTracker.BATTERY_USAGE_STATS_POLLING_INTERVAL_MS_DEBUG * 2;
        final long windowMs = 2_000;
        final float restrictBucketThreshold = 2.0f;
        final float restrictBucketThresholdMah =
                BATTERY_FULL_CHARGE_MAH * restrictBucketThreshold / 100.0f;
        final float bgRestrictedThreshold = 4.0f;
        final float bgRestrictedThresholdMah =
                BATTERY_FULL_CHARGE_MAH * bgRestrictedThreshold / 100.0f;
        final float restrictBucketHighThreshold = 25.0f;
        final float restrictBucketHighThresholdMah =
                BATTERY_FULL_CHARGE_MAH * restrictBucketHighThreshold / 100.0f;
        final float bgRestrictedHighThreshold = 25.0f;
        final float bgRestrictedHighThresholdMah =
                BATTERY_FULL_CHARGE_MAH * bgRestrictedHighThreshold / 100.0f;
        final long bgMediaPlaybackMinDuration = 1_000L;
        final long bgLocationMinDuration = 1_000L;

        DeviceConfigSession<Boolean> bgCurrentDrainMonitor = null;
        DeviceConfigSession<Long> bgCurrentDrainWindow = null;
        DeviceConfigSession<Float> bgCurrentDrainRestrictedBucketThreshold = null;
        DeviceConfigSession<Float> bgCurrentDrainBgRestrictedThreshold = null;
        DeviceConfigSession<Float> bgCurrentDrainRestrictedBucketHighThreshold = null;
        DeviceConfigSession<Float> bgCurrentDrainBgRestrictedHighThreshold = null;
        DeviceConfigSession<Long> bgMediaPlaybackMinDurationThreshold = null;
        DeviceConfigSession<Long> bgLocationMinDurationThreshold = null;
        DeviceConfigSession<Boolean> bgCurrentDrainEventDurationBasedThresholdEnabled = null;
        DeviceConfigSession<Boolean> bgBatteryExemptionEnabled = null;

        mBgRestrictionController.addAppBackgroundRestrictionListener(listener);

        setBackgroundRestrict(testPkgName1, testUid1, false, listener);

        // Verify the current settings.
        verifyRestrictionLevel(RESTRICTION_LEVEL_ADAPTIVE_BUCKET, testPkgName1, testUid1);

        final double[] zeros = new double[]{0.0f, 0.0f};
        final int[] uids = new int[]{testUid1, testUid2};

        doReturn(testPkgName1).when(mInjector).getPackageName(testPid1);
        doReturn(testPkgName2).when(mInjector).getPackageName(testPid2);

        try {
            bgCurrentDrainMonitor = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppBatteryPolicy.KEY_BG_CURRENT_DRAIN_MONITOR_ENABLED,
                    DeviceConfig::getBoolean,
                    AppBatteryPolicy.DEFAULT_BG_CURRENT_DRAIN_MONITOR_ENABLED);
            bgCurrentDrainMonitor.set(true);

            bgCurrentDrainWindow = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppBatteryPolicy.KEY_BG_CURRENT_DRAIN_WINDOW,
                    DeviceConfig::getLong,
                    AppBatteryPolicy.DEFAULT_BG_CURRENT_DRAIN_WINDOW_MS);
            bgCurrentDrainWindow.set(windowMs);

            bgCurrentDrainRestrictedBucketThreshold = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppBatteryPolicy.KEY_BG_CURRENT_DRAIN_THRESHOLD_TO_RESTRICTED_BUCKET,
                    DeviceConfig::getFloat,
                    AppBatteryPolicy.DEFAULT_BG_CURRENT_DRAIN_BG_RESTRICTED_THRESHOLD);
            bgCurrentDrainRestrictedBucketThreshold.set(restrictBucketThreshold);

            bgCurrentDrainBgRestrictedThreshold = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppBatteryPolicy.KEY_BG_CURRENT_DRAIN_THRESHOLD_TO_BG_RESTRICTED,
                    DeviceConfig::getFloat,
                    AppBatteryPolicy.DEFAULT_BG_CURRENT_DRAIN_BG_RESTRICTED_THRESHOLD);
            bgCurrentDrainBgRestrictedThreshold.set(bgRestrictedThreshold);

            bgCurrentDrainRestrictedBucketHighThreshold = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppBatteryPolicy.KEY_BG_CURRENT_DRAIN_HIGH_THRESHOLD_TO_RESTRICTED_BUCKET,
                    DeviceConfig::getFloat,
                    AppBatteryPolicy.DEFAULT_BG_CURRENT_DRAIN_RESTRICTED_BUCKET_HIGH_THRESHOLD);
            bgCurrentDrainRestrictedBucketHighThreshold.set(restrictBucketHighThreshold);

            bgCurrentDrainBgRestrictedHighThreshold = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppBatteryPolicy.KEY_BG_CURRENT_DRAIN_HIGH_THRESHOLD_TO_BG_RESTRICTED,
                    DeviceConfig::getFloat,
                    AppBatteryPolicy.DEFAULT_BG_CURRENT_DRAIN_BG_RESTRICTED_HIGH_THRESHOLD);
            bgCurrentDrainBgRestrictedHighThreshold.set(bgRestrictedHighThreshold);

            bgMediaPlaybackMinDurationThreshold = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppBatteryPolicy.KEY_BG_CURRENT_DRAIN_MEDIA_PLAYBACK_MIN_DURATION,
                    DeviceConfig::getLong,
                    AppBatteryPolicy.DEFAULT_BG_CURRENT_DRAIN_MEDIA_PLAYBACK_MIN_DURATION);
            bgMediaPlaybackMinDurationThreshold.set(bgMediaPlaybackMinDuration);

            bgLocationMinDurationThreshold = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppBatteryPolicy.KEY_BG_CURRENT_DRAIN_LOCATION_MIN_DURATION,
                    DeviceConfig::getLong,
                    AppBatteryPolicy.DEFAULT_BG_CURRENT_DRAIN_LOCATION_MIN_DURATION);
            bgLocationMinDurationThreshold.set(bgLocationMinDuration);

            bgCurrentDrainEventDurationBasedThresholdEnabled = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppBatteryPolicy.KEY_BG_CURRENT_DRAIN_EVENT_DURATION_BASED_THRESHOLD_ENABLED,
                    DeviceConfig::getBoolean,
                    AppBatteryPolicy
                            .DEFAULT_BG_CURRENT_DRAIN_EVENT_DURATION_BASED_THRESHOLD_ENABLED);
            bgCurrentDrainEventDurationBasedThresholdEnabled.set(true);

            bgBatteryExemptionEnabled = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    AppBatteryExemptionPolicy.KEY_BG_BATTERY_EXEMPTION_ENABLED,
                    DeviceConfig::getBoolean,
                    AppBatteryExemptionPolicy.DEFAULT_BG_BATTERY_EXEMPTION_ENABLED);
            bgBatteryExemptionEnabled.set(false);

            mCurrentTimeMillis = 10_000L;
            doReturn(mCurrentTimeMillis - windowMs).when(stats).getStatsStartTimestamp();
            doReturn(statsList).when(mBatteryStatsInternal).getBatteryUsageStats(anyObject());

            // Run with a media playback service which starts/stops immediately, we should
            // goto the restricted bucket.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, 0, true,
                    null, null, null, listener, stats, uids,
                    new double[]{restrictBucketThresholdMah + 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    false, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, true,
                    null, windowMs, null, null, null);

            // Run with a media playback service with extended time. We should be back to normal.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, bgMediaPlaybackMinDuration * 2, false,
                    null, null, null, listener, stats, uids,
                    new double[]{restrictBucketThresholdMah + 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    true, RESTRICTION_LEVEL_ADAPTIVE_BUCKET, timeout, false,
                    () -> {
                        // A user interaction will bring it back to normal.
                        mIdleStateListener.onUserInteractionStarted(testPkgName1,
                                UserHandle.getUserId(testUid1));
                        waitForIdleHandler(mBgRestrictionController.getBackgroundHandler());
                        // It should have been back to normal.
                        listener.verify(timeout, testUid1, testPkgName1,
                                RESTRICTION_LEVEL_ADAPTIVE_BUCKET);
                        verify(mInjector.getAppStandbyInternal(), times(1)).maybeUnrestrictApp(
                                eq(testPkgName1),
                                eq(UserHandle.getUserId(testUid1)),
                                eq(REASON_MAIN_FORCED_BY_SYSTEM),
                                eq(REASON_SUB_FORCED_SYSTEM_FLAG_ABUSE),
                                eq(REASON_MAIN_USAGE),
                                eq(REASON_SUB_USAGE_USER_INTERACTION));
                    }, windowMs, null, null, null);

            // Start over.
            resetBgRestrictionController();
            setUidBatteryConsumptions(stats, uids, zeros, zeros, zeros);
            mAppBatteryPolicy.reset();

            // Run with a media playback service with extended time, with higher current drain.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, bgMediaPlaybackMinDuration * 2, false,
                    null, null, null, listener, stats, uids,
                    new double[]{restrictBucketHighThresholdMah - 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    true, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, true,
                    null, windowMs, null, null, null);

            // Run with a media playback service with extended time, with even higher current drain.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, bgMediaPlaybackMinDuration * 2, false,
                    null, null, null, listener, stats, uids,
                    new double[]{restrictBucketHighThresholdMah + 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    false, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, false,
                    null, windowMs, null, null, null);

            // Start over.
            resetBgRestrictionController();
            setUidBatteryConsumptions(stats, uids, zeros, zeros, zeros);
            mAppBatteryPolicy.reset();

            // Run with a media session with extended time, with higher current drain.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_NONE, bgMediaPlaybackMinDuration * 2, false, null,
                    List.of(Pair.create(createMediaControllers(new String[] {testPkgName1},
                                new int[] {testUid1}), bgMediaPlaybackMinDuration * 2)),
                    null, listener, stats, uids,
                    new double[]{restrictBucketHighThresholdMah - 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    true, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, true,
                    null, windowMs, null, null, null);

            // Run with a media session with extended time, with even higher current drain.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_NONE, bgMediaPlaybackMinDuration * 2, false, null,
                    List.of(Pair.create(createMediaControllers(new String[] {testPkgName1},
                                new int[] {testUid1}), bgMediaPlaybackMinDuration * 2)),
                    null, listener, stats, uids,
                    new double[]{restrictBucketHighThresholdMah + 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    false, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, false,
                    null, windowMs, null, null, null);

            // Start over.
            resetBgRestrictionController();
            setUidBatteryConsumptions(stats, uids, zeros, zeros, zeros);
            mAppBatteryPolicy.reset();

            // Run with a media session with extended time, with moderate current drain,
            // but it ran on the top when the location service is active.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_NONE, bgMediaPlaybackMinDuration * 2, false, null,
                    List.of(Pair.create(createMediaControllers(new String[] {testPkgName1},
                                new int[] {testUid1}), bgMediaPlaybackMinDuration * 2)),
                    List.of(0L, timeout * 2), listener, stats, uids,
                    new double[]{restrictBucketThresholdMah + 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    false, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, true,
                    null, windowMs, null, null, null);

            // Start over.
            resetBgRestrictionController();
            setUidBatteryConsumptions(stats, uids, zeros, zeros, zeros);
            mAppBatteryPolicy.reset();

            // Run with a location service with extended time, with higher current drain.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_LOCATION, bgMediaPlaybackMinDuration * 2, false,
                    null, null, null, listener, stats, uids,
                    new double[]{restrictBucketHighThresholdMah - 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    true, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, true,
                    null, windowMs, null, null, null);

            // Run with a location service with extended time, with even higher current drain.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_LOCATION, bgMediaPlaybackMinDuration * 2, false,
                    null, null, null, listener, stats, uids,
                    new double[]{restrictBucketHighThresholdMah + 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    false, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, false,
                    null, windowMs, null, null, null);

            // Start over.
            resetBgRestrictionController();
            setUidBatteryConsumptions(stats, uids, zeros, zeros, zeros);
            mAppBatteryPolicy.reset();

            // Run with a location service with extended time, with moderate current drain,
            // but it ran on the top when the location service is active.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_LOCATION, bgMediaPlaybackMinDuration * 2, false,
                    null, null, List.of(0L, timeout * 2), listener, stats, uids,
                    new double[]{restrictBucketThresholdMah + 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    false, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, true,
                    null, windowMs, null, null, null);

            // Start over.
            resetBgRestrictionController();
            setUidBatteryConsumptions(stats, uids, zeros, zeros, zeros);
            mAppBatteryPolicy.reset();

            // Run with bg location permission, with higher current drain.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_NONE, 0, false,
                    ACCESS_BACKGROUND_LOCATION, null, null, listener, stats, uids,
                    new double[]{restrictBucketHighThresholdMah - 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    true, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, true,
                    null, windowMs, null, null, null);

            // Run with bg location permission, with even higher current drain.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_NONE, 0, false,
                    ACCESS_BACKGROUND_LOCATION , null, null, listener, stats, uids,
                    new double[]{restrictBucketHighThresholdMah + 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    false, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, false,
                    null, windowMs, null,  null, null);

            // Now turn off the event duration based feature flag.
            bgCurrentDrainEventDurationBasedThresholdEnabled.set(false);
            // Turn on the battery exemption feature flag.
            bgBatteryExemptionEnabled.set(true);

            // Start over.
            resetBgRestrictionController();
            setUidBatteryConsumptions(stats, uids, zeros, zeros, zeros);
            mAppBatteryPolicy.reset();

            waitForIdleHandler(mBgRestrictionController.getBackgroundHandler());

            // Run with a media playback service which starts/stops immediately, we should
            // goto the restricted bucket.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, 0, true,
                    null, null, null, listener, stats, uids,
                    new double[]{restrictBucketThresholdMah + 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    false, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, true,
                    null, windowMs, null, null, null);

            // Run with a media playback service with extended time. We should be back to normal.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, bgMediaPlaybackMinDuration * 2, false,
                    null, null, null, listener, stats, uids,
                    new double[]{restrictBucketThresholdMah + 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    true, RESTRICTION_LEVEL_ADAPTIVE_BUCKET, timeout, false,
                    () -> {
                        // A user interaction will bring it back to normal.
                        mIdleStateListener.onUserInteractionStarted(testPkgName1,
                                UserHandle.getUserId(testUid1));
                        waitForIdleHandler(mBgRestrictionController.getBackgroundHandler());
                        // It should have been back to normal.
                        listener.verify(timeout, testUid1, testPkgName1,
                                RESTRICTION_LEVEL_ADAPTIVE_BUCKET);
                        verify(mInjector.getAppStandbyInternal(), times(1)).maybeUnrestrictApp(
                                eq(testPkgName1),
                                eq(UserHandle.getUserId(testUid1)),
                                eq(REASON_MAIN_FORCED_BY_SYSTEM),
                                eq(REASON_SUB_FORCED_SYSTEM_FLAG_ABUSE),
                                eq(REASON_MAIN_USAGE),
                                eq(REASON_SUB_USAGE_USER_INTERACTION));
                    }, windowMs, null, null, null);

            // Start over.
            resetBgRestrictionController();
            setUidBatteryConsumptions(stats, uids, zeros, zeros, zeros);
            mAppBatteryPolicy.reset();

            final double[] initialBg = {1, 1}, initialFgs = {1, 1}, initialFg = zeros;

            // Run with a media playback service with extended time, with higher current drain.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, bgMediaPlaybackMinDuration * 2, false,
                    null, null, null, listener, stats, uids,
                    new double[]{restrictBucketHighThresholdMah - 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    true, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, true,
                    null, windowMs, initialBg, initialFgs, initialFg);

            // Run with a media playback service with extended time, with even higher current drain,
            // it still should stay in the current restriction level as we exempt the media
            // playback.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, bgMediaPlaybackMinDuration * 2, false,
                    null, null, null, listener, stats, uids,
                    new double[]{restrictBucketHighThresholdMah + 100, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    true, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, false,
                    null, windowMs, initialBg, initialFgs, initialFg);

            // Start over.
            resetBgRestrictionController();
            setUidBatteryConsumptions(stats, uids, zeros, zeros, zeros);
            mAppBatteryPolicy.reset();

            // Run with a media session with extended time, with higher current drain.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_NONE, bgMediaPlaybackMinDuration * 2, false, null,
                    List.of(Pair.create(createMediaControllers(new String[] {testPkgName1},
                                new int[] {testUid1}), bgMediaPlaybackMinDuration * 2)),
                    null, listener, stats, uids,
                    new double[]{restrictBucketHighThresholdMah - 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    true, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, true,
                    null, windowMs, initialBg, initialFgs, initialFg);

            // Run with a media session with extended time, with even higher current drain.
            // it still should stay in the current restriction level as we exempt the media
            // session.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_NONE, bgMediaPlaybackMinDuration * 2, false, null,
                    List.of(Pair.create(createMediaControllers(new String[] {testPkgName1},
                                new int[] {testUid1}), bgMediaPlaybackMinDuration * 2)),
                    null, listener, stats, uids,
                    new double[]{restrictBucketHighThresholdMah + 100, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    true, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, true,
                    null, windowMs, initialBg, initialFgs, initialFg);

            // Start over.
            resetBgRestrictionController();
            setUidBatteryConsumptions(stats, uids, zeros, zeros, zeros);
            mAppBatteryPolicy.reset();

            // Run with a location service with extended time, with higher current drain.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_LOCATION, bgMediaPlaybackMinDuration * 2, false,
                    null, null, null, listener, stats, uids,
                    new double[]{restrictBucketHighThresholdMah - 1, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    true, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, true,
                    null, windowMs, initialBg, initialFgs, initialFg);

            // Run with a location service with extended time, with even higher current drain.
            // it still should stay in the current restriction level as we exempt the location.
            runTestBgCurrentDrainExemptionOnce(testPkgName1, testUid1, testPid1,
                    FOREGROUND_SERVICE_TYPE_LOCATION, bgMediaPlaybackMinDuration * 2, false,
                    null, null, null, listener, stats, uids,
                    new double[]{restrictBucketHighThresholdMah + 100, 0},
                    new double[]{0, restrictBucketThresholdMah - 1}, zeros,
                    true, RESTRICTION_LEVEL_RESTRICTED_BUCKET, timeout, false,
                    null, windowMs, initialBg, initialFgs, initialFg);
        } finally {
            closeIfNotNull(bgCurrentDrainMonitor);
            closeIfNotNull(bgCurrentDrainWindow);
            closeIfNotNull(bgCurrentDrainRestrictedBucketThreshold);
            closeIfNotNull(bgCurrentDrainBgRestrictedThreshold);
            closeIfNotNull(bgCurrentDrainRestrictedBucketHighThreshold);
            closeIfNotNull(bgCurrentDrainBgRestrictedHighThreshold);
            closeIfNotNull(bgMediaPlaybackMinDurationThreshold);
            closeIfNotNull(bgLocationMinDurationThreshold);
            closeIfNotNull(bgCurrentDrainEventDurationBasedThresholdEnabled);
            closeIfNotNull(bgBatteryExemptionEnabled);
        }
    }

    private void runTestBgCurrentDrainExemptionOnce(String packageName, int uid, int pid,
            int serviceType, long sleepMs, boolean stopAfterSleep, String perm,
            List<Pair<List<MediaController>, Long>> mediaControllers,
            List<Long> topStateChanges, TestAppRestrictionLevelListener listener,
            BatteryUsageStats stats, int[] uids, double[] bg, double[] fgs, double[] fg,
            boolean expectingTimeout, int expectingLevel, long timeout, boolean resetFGSTracker,
            RunnableWithException extraVerifiers, long windowMs,
            double[] initialBg, double[] initialFgs, double[] initialFg) throws Exception {
        listener.mLatchHolder[0] = new CountDownLatch(1);
        if (initialBg != null) {
            doReturn(mCurrentTimeMillis).when(stats).getStatsStartTimestamp();
            mCurrentTimeMillis += windowMs + 1;
            setUidBatteryConsumptions(stats, uids, initialBg, initialFgs, initialFg);
            mAppBatteryExemptionTracker.reset();
            mAppBatteryPolicy.reset();
        }
        runExemptionTestOnce(
                packageName, uid, pid, serviceType, sleepMs, stopAfterSleep,
                perm, mediaControllers, topStateChanges, resetFGSTracker, false,
                () -> {
                    clearInvocations(mInjector.getAppStandbyInternal());
                    clearInvocations(mBgRestrictionController);
                    runTestBgCurrentDrainMonitorOnce(listener, stats, uids, bg, fgs, fg, false,
                            () -> {
                                doReturn(mCurrentTimeMillis).when(stats).getStatsStartTimestamp();
                                mCurrentTimeMillis += windowMs + 1;
                                if (expectingTimeout) {
                                    try {
                                        listener.verify(timeout, uid, packageName, expectingLevel);
                                        fail("There shouldn't be any level change events");
                                    } catch (Exception e) {
                                        // Expected.
                                    }
                                } else {
                                    listener.verify(timeout, uid, packageName, expectingLevel);
                                }
                                if (expectingLevel == RESTRICTION_LEVEL_RESTRICTED_BUCKET) {
                                    verify(mInjector.getAppStandbyInternal(),
                                            expectingTimeout ? never() : atLeast(1)).restrictApp(
                                            eq(packageName),
                                            eq(UserHandle.getUserId(uid)),
                                            anyInt(), anyInt());
                                } else if (expectingLevel
                                         == RESTRICTION_LEVEL_BACKGROUND_RESTRICTED) {
                                    verify(mBgRestrictionController,
                                            expectingTimeout ? never() : atLeast(1))
                                            .handleRequestBgRestricted(eq(packageName), eq(uid));
                                } else {
                                    verify(mInjector.getAppStandbyInternal(),
                                            expectingTimeout ? never() : atLeast(1))
                                            .setAppStandbyBucket(
                                                   eq(packageName),
                                                   eq(STANDBY_BUCKET_RARE),
                                                   eq(UserHandle.getUserId(uid)),
                                                   anyInt(), anyInt());
                                }
                                if (extraVerifiers != null) {
                                    extraVerifiers.run();
                                }
                            }
                    );
                }
        );
    }

    @Test
    public void testExcessiveBroadcasts() throws Exception {
        final long windowMs = 5_000;
        final int threshold = 10;
        runTestExcessiveEvent(AppBroadcastEventsPolicy.KEY_BG_BROADCAST_MONITOR_ENABLED,
                AppBroadcastEventsPolicy.DEFAULT_BG_BROADCAST_MONITOR_ENABLED,
                AppBroadcastEventsPolicy.KEY_BG_BROADCAST_WINDOW,
                AppBroadcastEventsPolicy.DEFAULT_BG_BROADCAST_WINDOW,
                AppBroadcastEventsPolicy.KEY_BG_EX_BROADCAST_THRESHOLD,
                AppBroadcastEventsPolicy.DEFAULT_BG_EX_BROADCAST_THRESHOLD,
                windowMs, threshold, mBroadcastEventListener::onSendingBroadcast,
                mAppBroadcastEventsTracker,
                new long[][] {
                    new long[] {1_000L, 2_000L, 2_000L},
                    new long[] {2_000L, 2_000L, 1_000L},
                },
                new int[][] {
                    new int[] {3, 3, 3},
                    new int[] {3, 3, 4},
                },
                new boolean[] {
                    true,
                    false,
                }
        );
    }

    @Test
    public void testExcessiveBindServices() throws Exception {
        final long windowMs = 5_000;
        final int threshold = 10;
        runTestExcessiveEvent(AppBindServiceEventsPolicy.KEY_BG_BIND_SVC_MONITOR_ENABLED,
                AppBindServiceEventsPolicy.DEFAULT_BG_BIND_SVC_MONITOR_ENABLED,
                AppBindServiceEventsPolicy.KEY_BG_BIND_SVC_WINDOW,
                AppBindServiceEventsPolicy.DEFAULT_BG_BIND_SVC_WINDOW,
                AppBindServiceEventsPolicy.KEY_BG_EX_BIND_SVC_THRESHOLD,
                AppBindServiceEventsPolicy.DEFAULT_BG_EX_BIND_SVC_THRESHOLD,
                windowMs, threshold, mBindServiceEventListener::onBindingService,
                mAppBindServiceEventsTracker,
                new long[][] {
                    new long[] {0L, 2_000L, 4_000L, 1_000L},
                    new long[] {2_000L, 2_000L, 2_000L, 2_000L},
                },
                new int[][] {
                    new int[] {8, 3, 1, 0}, // Will goto restricted bucket.
                    new int[] {3, 3, 3, 3},
                },
                new boolean[] {
                    false,
                    true,
                }
        );
    }

    private void runTestExcessiveEvent(String keyEnable, boolean defaultEnable,
            String keyWindow, long defaultWindow, String keyThreshold, int defaultThreshold,
            long windowMs, int threshold, BiConsumer<String, Integer> eventEmitter,
            BaseAppStateEventsTracker tracker, long[][] waitMs, int[][] events,
            boolean[] expectingTimeout) throws Exception {
        final int testPkgIndex = 1;
        final String testPkgName = TEST_PACKAGE_BASE + testPkgIndex;
        final int testUser = TEST_USER0;
        final int testUid = UserHandle.getUid(testUser, TEST_PACKAGE_APPID_BASE + testPkgIndex);
        final int testPid = 1234;

        final long timeoutMs = 2_000;

        final TestAppRestrictionLevelListener listener = new TestAppRestrictionLevelListener();

        mBgRestrictionController.addAppBackgroundRestrictionListener(listener);
        setBackgroundRestrict(testPkgName, testUid, false, listener);

        DeviceConfigSession<Boolean> enableMonitor = null;
        DeviceConfigSession<Long> eventsWindow = null;
        DeviceConfigSession<Integer> eventsThreshold = null;

        doReturn(testPkgName).when(mInjector).getPackageName(testPid);

        verifyRestrictionLevel(RESTRICTION_LEVEL_ADAPTIVE_BUCKET, testPkgName, testUid);

        try {
            enableMonitor = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    keyEnable,
                    DeviceConfig::getBoolean,
                    defaultEnable);
            enableMonitor.set(true);

            eventsWindow = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    keyWindow,
                    DeviceConfig::getLong,
                    defaultWindow);
            eventsWindow.set(windowMs);

            eventsThreshold = new DeviceConfigSession<>(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    keyThreshold,
                    DeviceConfig::getInt,
                    defaultThreshold);
            eventsThreshold.set(threshold);

            for (int i = 0; i < waitMs.length; i++) {
                resetBgRestrictionController();
                listener.mLatchHolder[0] = new CountDownLatch(1);
                tracker.reset();
                clearInvocations(mInjector.getAppStandbyInternal());
                clearInvocations(mBgRestrictionController);
                for (int j = 0; j < waitMs[i].length; j++) {
                    for (int k = 0; k < events[i][j]; k++) {
                        eventEmitter.accept(testPkgName, testUid);
                    }
                    Thread.sleep(waitMs[i][j]);
                }
                waitForIdleHandler(mBgRestrictionController.getBackgroundHandler());
                if (expectingTimeout[i]) {
                    verifyRestrictionLevel(RESTRICTION_LEVEL_ADAPTIVE_BUCKET, testPkgName, testUid);
                    try {
                        listener.verify(timeoutMs, testUid, testPkgName,
                                RESTRICTION_LEVEL_RESTRICTED_BUCKET);
                        fail("There shouldn't be any level change events");
                    } catch (TimeoutException e) {
                        // expected.
                    }
                } else {
                    verifyRestrictionLevel(RESTRICTION_LEVEL_RESTRICTED_BUCKET,
                            testPkgName, testUid);
                    listener.verify(timeoutMs, testUid, testPkgName,
                            RESTRICTION_LEVEL_RESTRICTED_BUCKET);
                }
            }
        } finally {
            closeIfNotNull(enableMonitor);
            closeIfNotNull(eventsWindow);
            closeIfNotNull(eventsThreshold);
        }
    }

    private int[] checkNotificationShown(String[] packageName, VerificationMode mode,
            boolean verifyNotification) throws Exception {
        final ArgumentCaptor<Integer> notificationIdCaptor =
                ArgumentCaptor.forClass(Integer.class);
        final ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);
        verify(mInjector.getNotificationManager(), mode).notifyAsUser(any(),
                notificationIdCaptor.capture(), notificationCaptor.capture(), any());
        final int[] notificationId = new int[packageName.length];
        if (verifyNotification) {
            for (int i = 0, j = 0; i < packageName.length; j++) {
                final int id = notificationIdCaptor.getAllValues().get(j);
                if (id == NotificationHelper.SUMMARY_NOTIFICATION_ID) {
                    continue;
                }
                final Notification n = notificationCaptor.getAllValues().get(j);
                notificationId[i] = id;
                assertTrue(NotificationHelper.SUMMARY_NOTIFICATION_ID < notificationId[i]);
                assertEquals(NotificationHelper.GROUP_KEY, n.getGroup());
                assertEquals(ABUSIVE_BACKGROUND_APPS, n.getChannelId());
                assertEquals(packageName[i], n.extras.getString(Intent.EXTRA_PACKAGE_NAME));
                i++;
            }
        }
        return notificationId;
    }

    private void checkNotificationGone(String packageName, VerificationMode mode,
            int notificationId) throws Exception {
        final ArgumentCaptor<Integer> notificationIdCaptor =
                ArgumentCaptor.forClass(Integer.class);
        verify(mInjector.getNotificationManager(), mode).cancel(notificationIdCaptor.capture());
        assertEquals(notificationId, notificationIdCaptor.getValue().intValue());
    }

    private void closeIfNotNull(DeviceConfigSession<?> config) throws Exception {
        if (config != null) {
            config.close();
        }
    }

    private interface RunnableWithException {
        void run() throws Exception;
    }

    private void runTestBgCurrentDrainMonitorOnce(TestAppRestrictionLevelListener listener,
            BatteryUsageStats stats, int[] uids, double[] bg, double[] fgs, double[] fg,
            RunnableWithException runnable) throws Exception {
        runTestBgCurrentDrainMonitorOnce(listener, stats, uids, bg, fgs, fg, true, runnable);
    }

    private void runTestBgCurrentDrainMonitorOnce(TestAppRestrictionLevelListener listener,
            BatteryUsageStats stats, int[] uids, double[] bg, double[] fgs, double[] fg,
            boolean resetListener, RunnableWithException runnable) throws Exception {
        if (resetListener) {
            listener.mLatchHolder[0] = new CountDownLatch(1);
        }
        setUidBatteryConsumptions(stats, uids, bg, fgs, fg);
        runnable.run();
    }

    private void setUidBatteryConsumptions(BatteryUsageStats stats, int[] uids, double[] bg,
            double[] fgs, double[] fg) {
        ArrayList<UidBatteryConsumer> consumers = new ArrayList<>();
        for (int i = 0; i < uids.length; i++) {
            consumers.add(mockUidBatteryConsumer(uids[i], bg[i], fgs[i], fg[i]));
        }
        doReturn(consumers).when(stats).getUidBatteryConsumers();
    }

    private UidBatteryConsumer mockUidBatteryConsumer(int uid, double bg, double fgs, double fg) {
        UidBatteryConsumer uidConsumer = mock(UidBatteryConsumer.class);
        doReturn(uid).when(uidConsumer).getUid();
        doReturn(bg).when(uidConsumer).getConsumedPower(eq(BATT_DIMEN_BG));
        doReturn(fgs).when(uidConsumer).getConsumedPower(eq(BATT_DIMEN_FGS));
        doReturn(fg).when(uidConsumer).getConsumedPower(eq(BATT_DIMEN_FG));
        return uidConsumer;
    }

    private void setBackgroundRestrict(String pkgName, int uid, boolean restricted,
            TestAppRestrictionLevelListener listener) throws Exception {
        Log.i(TAG, "Setting background restrict to " + restricted + " for " + pkgName + " " + uid);
        listener.mLatchHolder[0] = new CountDownLatch(1);
        doReturn(restricted).when(mAppStateTracker).isAppBackgroundRestricted(uid, pkgName);
        mFasListener.updateBackgroundRestrictedForUidPackage(uid, pkgName, restricted);
        waitForIdleHandler(mBgRestrictionController.getBackgroundHandler());
    }

    private class TestAppRestrictionLevelListener implements AppBackgroundRestrictionListener {
        private final CountDownLatch[] mLatchHolder = new CountDownLatch[1];
        final int[] mUidHolder = new int[1];
        final String[] mPkgNameHolder = new String[1];
        final int[] mLevelHolder = new int[1];

        @Override
        public void onRestrictionLevelChanged(int uid, String packageName, int newLevel) {
            mUidHolder[0] = uid;
            mPkgNameHolder[0] = packageName;
            mLevelHolder[0] = newLevel;
            mLatchHolder[0].countDown();
        };

        void verify(long timeout, int uid, String pkgName, int level) throws Exception {
            if (!mLatchHolder[0].await(timeout, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException();
            }
            assertEquals(uid, mUidHolder[0]);
            assertEquals(pkgName, mPkgNameHolder[0]);
            assertEquals(level, mLevelHolder[0]);
        }
    }

    private void verifyRestrictionLevel(int level, String pkgName, int uid) {
        assertEquals(level, mBgRestrictionController.getRestrictionLevel(uid));
        assertEquals(level, mBgRestrictionController.getRestrictionLevel(uid, pkgName));
    }

    private void waitForIdleHandler(Handler handler) {
        waitForIdleHandler(handler, Duration.ofSeconds(1));
    }

    private void waitForIdleHandler(Handler handler, Duration timeout) {
        final MessageQueue queue = handler.getLooper().getQueue();
        final CountDownLatch latch = new CountDownLatch(1);
        queue.addIdleHandler(() -> {
            latch.countDown();
            // Remove idle handler
            return false;
        });
        try {
            latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("Interrupted unexpectedly: " + e);
        }
    }

    @Test
    public void testMergeAppStateDurations() throws Exception {
        final BaseAppStateDurations testObj = new BaseAppStateDurations(0, "", 1, "", null) {};
        assertAppStateDurations(null, testObj.add(null, null));
        assertAppStateDurations(new LinkedList<BaseTimeEvent>(), testObj.add(
                null, new LinkedList<BaseTimeEvent>()));
        assertAppStateDurations(new LinkedList<BaseTimeEvent>(), testObj.add(
                new LinkedList<BaseTimeEvent>(), null));
        assertAppStateDurations(createDurations(1), testObj.add(
                createDurations(1), new LinkedList<BaseTimeEvent>()));
        assertAppStateDurations(createDurations(1), testObj.add(
                new LinkedList<BaseTimeEvent>(), createDurations(1)));
        assertAppStateDurations(createDurations(1, 4, 5, 8, 9), testObj.add(
                createDurations(1, 3, 5, 7, 9), createDurations(2, 4, 6, 8, 10)));
        assertAppStateDurations(createDurations(1, 5), testObj.add(
                createDurations(1, 2, 3, 4), createDurations(2, 3, 4, 5)));
        assertAppStateDurations(createDurations(1, 4, 6, 9), testObj.add(
                createDurations(2, 4, 6, 9), createDurations(1, 4, 7, 8)));
        assertAppStateDurations(createDurations(1, 4, 5, 8, 9, 10), testObj.add(
                createDurations(1, 4, 6, 8), createDurations(1, 3, 5, 8, 9, 10)));
    }

    @Test
    public void testSubtractAppStateDurations() throws Exception {
        final BaseAppStateDurations testObj = new BaseAppStateDurations(0, "", 1, "", null) {};
        assertAppStateDurations(null, testObj.subtract(null, null));
        assertAppStateDurations(null, testObj.subtract(null, new LinkedList<BaseTimeEvent>()));
        assertAppStateDurations(new LinkedList<BaseTimeEvent>(), testObj.subtract(
                new LinkedList<BaseTimeEvent>(), null));
        assertAppStateDurations(createDurations(1), testObj.subtract(
                createDurations(1), new LinkedList<BaseTimeEvent>()));
        assertAppStateDurations(new LinkedList<BaseTimeEvent>(), testObj.subtract(
                new LinkedList<BaseTimeEvent>(), createDurations(1)));
        assertAppStateDurations(new LinkedList<BaseTimeEvent>(), testObj.subtract(
                createDurations(1), createDurations(1)));
        assertAppStateDurations(createDurations(1, 2, 5, 6, 9, 10), testObj.subtract(
                createDurations(1, 3, 5, 7, 9), createDurations(2, 4, 6, 8, 10)));
        assertAppStateDurations(createDurations(1, 2, 3, 4), testObj.subtract(
                createDurations(1, 4, 6, 7, 9, 10), createDurations(2, 3, 5, 8, 9, 10)));
        assertAppStateDurations(createDurations(3, 4, 9, 10), testObj.subtract(
                createDurations(1, 4, 6, 8, 9, 10), createDurations(1, 3, 5, 8)));
        assertAppStateDurations(createDurations(1, 2, 3, 4, 5, 6, 7, 8), testObj.subtract(
                createDurations(1, 6, 7, 8), createDurations(2, 3, 4, 5, 8, 10)));
        assertAppStateDurations(createDurations(5, 6), testObj.subtract(
                createDurations(2, 3, 5, 6), createDurations(1, 4, 7, 8)));
        assertAppStateDurations(createDurations(2, 3, 4, 5, 6, 7, 8), testObj.subtract(
                createDurations(1), createDurations(1, 2, 3, 4, 5, 6, 7, 8)));
    }

    private void assertAppStateDurations(LinkedList<BaseTimeEvent> expected,
            LinkedList<BaseTimeEvent> actual) throws Exception {
        assertListEquals(expected, actual);
    }

    private <T> void assertListEquals(LinkedList<T> expected, LinkedList<T> actual) {
        assertEquals(expected == null || expected.isEmpty(), actual == null || actual.isEmpty());
        if (expected != null) {
            if (expected.size() > 0) {
                assertEquals(expected.size(), actual.size());
            }
            while (expected.peek() != null) {
                assertTrue(expected.poll().equals(actual.poll()));
            }
        }
    }

    private LinkedList<BaseTimeEvent> createDurations(long... timestamps) {
        return Arrays.stream(timestamps).mapToObj(BaseTimeEvent::new)
                .collect(LinkedList<BaseTimeEvent>::new, LinkedList<BaseTimeEvent>::add,
                (a, b) -> a.addAll(b));
    }

    private LinkedList<Integer> createIntLinkedList(int[] vals) {
        return Arrays.stream(vals).collect(LinkedList<Integer>::new, LinkedList<Integer>::add,
                (a, b) -> a.addAll(b));
    }

    @Test
    public void testAppStateTimeSlotEvents() throws Exception {
        final long maxTrackingDuration = 5_000L;
        assertAppStateTimeSlotEvents(new int[] {2, 2, 0, 0, 1},
                new long[] {1_500, 1_500, 2_100, 2_999, 5_999}, 5_000);
        assertAppStateTimeSlotEvents(new int[] {2, 2, 0, 0, 1, 1},
                new long[] {1_500, 1_500, 2_100, 2_999, 5_999, 6_000}, 6_000);
        assertAppStateTimeSlotEvents(new int[] {2, 0, 0, 1, 1, 1},
                new long[] {1_500, 1_500, 2_100, 2_999, 5_999, 6_000, 7_000}, 7_000);
        assertMergeAppStateTimeSlotEvents(new int[] {}, new long[] {}, new long[] {}, 0);
        assertMergeAppStateTimeSlotEvents(new int[] {1}, new long[] {}, new long[] {1_500}, 1_000);
        assertMergeAppStateTimeSlotEvents(new int[] {1}, new long[] {1_500}, new long[] {}, 1_000);
        assertMergeAppStateTimeSlotEvents(new int[] {1, 1},
                new long[] {1_500}, new long[] {2_500}, 2_000);
        assertMergeAppStateTimeSlotEvents(new int[] {1, 1},
                new long[] {2_500}, new long[] {1_500}, 2_000);
        assertMergeAppStateTimeSlotEvents(new int[] {1, 2, 1},
                new long[] {1_500, 2_500}, new long[] {2_600, 3_000}, 3_000);
        assertMergeAppStateTimeSlotEvents(new int[] {2, 1, 1},
                new long[] {2_600, 3_500}, new long[] {1_500, 1_600}, 3_000);
        assertMergeAppStateTimeSlotEvents(new int[] {1, 2, 1},
                new long[] {1_500, 3_500}, new long[] {2_600, 2_700}, 3_000);
        assertMergeAppStateTimeSlotEvents(new int[] {1, 2, 1},
                new long[] {2_500, 2_600}, new long[] {1_500, 3_700}, 3_000);
        assertMergeAppStateTimeSlotEvents(new int[] {1, 0, 0, 0, 0, 1},
                new long[] {2_500, 8_600}, new long[] {1_500, 3_700}, 8_000);
    }

    private BaseAppStateTimeSlotEvents createBaseAppStateTimeSlotEvents(
            long slotSize, long maxTrackingDuration, long[] timestamps) {
        final BaseAppStateTimeSlotEvents testObj = new BaseAppStateTimeSlotEvents(
                0, "", 1, slotSize, "", () -> maxTrackingDuration) {};
        for (int i = 0; i < timestamps.length; i++) {
            testObj.addEvent(timestamps[i], 0);
        }
        return testObj;
    }

    private void assertAppStateTimeSlotEvents(int[] expectedEvents, long[] timestamps,
            long expectedCurTimeslot) {
        final BaseAppStateTimeSlotEvents testObj = createBaseAppStateTimeSlotEvents(1_000L,
                5_000L, timestamps);
        assertEquals(expectedCurTimeslot, testObj.getCurrentSlotStartTime(0));
        assertListEquals(createIntLinkedList(expectedEvents), testObj.getRawEvents(0));
    }

    private void assertMergeAppStateTimeSlotEvents(int[] expectedEvents, long[] timestamps1,
            long[] timestamps2, long expectedCurTimeslot) {
        final BaseAppStateTimeSlotEvents testObj1 = createBaseAppStateTimeSlotEvents(1_000L,
                5_000L, timestamps1);
        final BaseAppStateTimeSlotEvents testObj2 = createBaseAppStateTimeSlotEvents(1_000L,
                5_000L, timestamps2);
        testObj1.add(testObj2);
        assertEquals(expectedCurTimeslot, testObj1.getCurrentSlotStartTime(0));
        assertListEquals(createIntLinkedList(expectedEvents), testObj1.getRawEvents(0));
    }

    @Test
    public void testMergeUidBatteryUsage() throws Exception {
        final UidBatteryStates testObj = new UidBatteryStates(0, "", null);
        assertListEquals(null, testObj.add(null, null));
        assertListEquals(new LinkedList<UidStateEventWithBattery>(), testObj.add(
                null, new LinkedList<UidStateEventWithBattery>()));
        assertListEquals(new LinkedList<UidStateEventWithBattery>(), testObj.add(
                new LinkedList<UidStateEventWithBattery>(), null));
        assertListEquals(createUidStateEventWithBatteryList(
                new boolean[] {true}, new long[] {10L}, new double[] {10.0d}),
                testObj.add(createUidStateEventWithBatteryList(
                new boolean[] {true}, new long[] {10L}, new double[] {10.0d}),
                new LinkedList<UidStateEventWithBattery>()));
        assertListEquals(createUidStateEventWithBatteryList(
                new boolean[] {true}, new long[] {10L}, new double[] {10.0d}),
                testObj.add(new LinkedList<UidStateEventWithBattery>(),
                createUidStateEventWithBatteryList(
                new boolean[] {true}, new long[] {10L}, new double[] {10.0d})));
        assertListEquals(createUidStateEventWithBatteryList(
                new boolean[] {true}, new long[] {10L}, new double[] {10.0d}),
                testObj.add(createUidStateEventWithBatteryList(
                new boolean[] {true}, new long[] {11L}, new double[] {11.0d}),
                createUidStateEventWithBatteryList(
                new boolean[] {true}, new long[] {10L}, new double[] {10.0d})));
        assertListEquals(createUidStateEventWithBatteryList(
                new boolean[] {true}, new long[] {10L}, new double[] {10.0d}),
                testObj.add(createUidStateEventWithBatteryList(
                new boolean[] {true, false}, new long[] {11L, 12L}, new double[] {11.0d, 1.0d}),
                createUidStateEventWithBatteryList(
                new boolean[] {true}, new long[] {10L}, new double[] {10.0d})));
        assertListEquals(createUidStateEventWithBatteryList(
                new boolean[] {true}, new long[] {10L}, new double[] {10.0d}),
                testObj.add(createUidStateEventWithBatteryList(
                new boolean[] {true, false, true}, new long[] {11L, 12L, 13L},
                new double[] {11.0d, 1.0d, 13.0d}),
                createUidStateEventWithBatteryList(
                new boolean[] {true}, new long[] {10L}, new double[] {10.0d})));
        assertListEquals(createUidStateEventWithBatteryList(
                new boolean[] {true, false}, new long[] {10L, 13L}, new double[] {10.0d, 3.0d}),
                testObj.add(createUidStateEventWithBatteryList(
                new boolean[] {true, false}, new long[] {11L, 13L}, new double[] {11.0d, 2.0d}),
                createUidStateEventWithBatteryList(
                new boolean[] {true, false}, new long[] {10L, 12L}, new double[] {10.0d, 2.0d})));
        assertListEquals(createUidStateEventWithBatteryList(
                new boolean[] {true, false, true}, new long[] {10L, 13L, 14L},
                new double[] {10.0d, 3.0d, 14.0d}),
                testObj.add(createUidStateEventWithBatteryList(
                new boolean[] {true, false, true}, new long[] {11L, 13L, 14L},
                new double[] {11.0d, 2.0d, 14.0d}),
                createUidStateEventWithBatteryList(
                new boolean[] {true, false}, new long[] {10L, 12L}, new double[] {10.0d, 2.0d})));
        assertListEquals(createUidStateEventWithBatteryList(
                new boolean[] {true, false, true, false, true, false},
                new long[] {10L, 13L, 14L, 17L, 18L, 21L},
                new double[] {10.0d, 3.0d, 14.0d, 3.0d, 18.0d, 3.0d}),
                testObj.add(createUidStateEventWithBatteryList(
                new boolean[] {true, false, true, false, true, false},
                new long[] {11L, 13L, 15L, 17L, 19L, 21L},
                new double[] {11.0d, 2.0d, 15.0d, 2.0d, 19.0d, 2.0d}),
                createUidStateEventWithBatteryList(
                new boolean[] {true, false, true, false, true, false},
                new long[] {10L, 12L, 14L, 16L, 18L, 20L},
                new double[] {10.0d, 2.0d, 14.0d, 2.0d, 18.0d, 2.0d})));
        assertListEquals(createUidStateEventWithBatteryList(
                new boolean[] {true, false, true, false, true, false, true, false, true, false},
                new long[] {10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L},
                new double[] {10.0d, 1.0d, 12.0d, 1.0d, 14.0d, 1.0d, 16.0d, 1.0d, 18.0d, 1.0d}),
                testObj.add(createUidStateEventWithBatteryList(
                new boolean[] {true, false, true, false},
                new long[] {12L, 13L, 16L, 17L},
                new double[] {12.0d, 1.0d, 16.0d, 1.0d}),
                createUidStateEventWithBatteryList(
                new boolean[] {true, false, true, false, true, false},
                new long[] {10L, 11L, 14L, 15L, 18L, 19L},
                new double[] {10.0d, 1.0d, 14.0d, 1.0d, 18.0d, 1.0d})));
        assertListEquals(createUidStateEventWithBatteryList(
                new boolean[] {true, false, true, false},
                new long[] {10L, 14L, 18L, 19L},
                new double[] {10.0d, 4.0d, 18.0d, 1.0d}),
                testObj.add(createUidStateEventWithBatteryList(
                new boolean[] {true, false, true, false},
                new long[] {11L, 12L, 13L, 14L},
                new double[] {11.0d, 1.0d, 13.0d, 1.0d}),
                createUidStateEventWithBatteryList(
                new boolean[] {true, false, true, false, true, false},
                new long[] {10L, 11L, 12L, 13L, 18L, 19L},
                new double[] {10.0d, 1.0d, 12.0d, 1.0d, 18.0d, 1.0d})));
        assertListEquals(createUidStateEventWithBatteryList(
                new boolean[] {true, false, true, false},
                new long[] {10L, 14L, 18L, 19L},
                new double[] {10.0d, 4.0d, 18.0d, 1.0d}),
                testObj.add(createUidStateEventWithBatteryList(
                new boolean[] {true, false, true, false},
                new long[] {10L, 14L, 18L, 19L},
                new double[] {10.0d, 4.0d, 18.0d, 1.0d}),
                createUidStateEventWithBatteryList(
                new boolean[] {true, false, true, false, true, false},
                new long[] {10L, 11L, 12L, 13L, 18L, 19L},
                new double[] {10.0d, 1.0d, 12.0d, 1.0d, 18.0d, 1.0d})));
    }

    private LinkedList<UidStateEventWithBattery> createUidStateEventWithBatteryList(
            boolean[] isStart, long[] timestamps, double[] batteryUsage) {
        final LinkedList<UidStateEventWithBattery> result = new LinkedList<>();
        for (int i = 0; i < isStart.length; i++) {
            result.add(new UidStateEventWithBattery(
                    isStart[i], timestamps[i], batteryUsage[i], null));
        }
        return result;
    }

    private class TestBgRestrictionInjector extends AppRestrictionController.Injector {
        private Context mContext;

        TestBgRestrictionInjector(Context context) {
            super(context);
            mContext = context;
        }

        @Override
        void initAppStateTrackers(AppRestrictionController controller) {
            try {
                mAppBatteryTracker = new AppBatteryTracker(mContext, controller,
                        TestAppBatteryTrackerInjector.class.getDeclaredConstructor(
                                BackgroundRestrictionTest.class),
                        BackgroundRestrictionTest.this);
                controller.addAppStateTracker(mAppBatteryTracker);
                mAppBatteryExemptionTracker = new AppBatteryExemptionTracker(mContext, controller,
                        TestAppBatteryExemptionTrackerInjector.class.getDeclaredConstructor(
                                BackgroundRestrictionTest.class),
                        BackgroundRestrictionTest.this);
                controller.addAppStateTracker(mAppBatteryExemptionTracker);
                mAppFGSTracker = new AppFGSTracker(mContext, controller,
                        TestAppFGSTrackerInjector.class.getDeclaredConstructor(
                                BackgroundRestrictionTest.class),
                        BackgroundRestrictionTest.this);
                controller.addAppStateTracker(mAppFGSTracker);
                mAppMediaSessionTracker = new AppMediaSessionTracker(mContext, controller,
                        TestAppMediaSessionTrackerInjector.class.getDeclaredConstructor(
                                BackgroundRestrictionTest.class),
                        BackgroundRestrictionTest.this);
                controller.addAppStateTracker(mAppMediaSessionTracker);
                mAppBroadcastEventsTracker = new AppBroadcastEventsTracker(mContext, controller,
                        TestAppBroadcastEventsTrackerInjector.class.getDeclaredConstructor(
                                BackgroundRestrictionTest.class),
                        BackgroundRestrictionTest.this);
                controller.addAppStateTracker(mAppBroadcastEventsTracker);
                mAppBindServiceEventsTracker = new AppBindServiceEventsTracker(mContext, controller,
                        TestAppBindServiceEventsTrackerInjector.class.getDeclaredConstructor(
                                BackgroundRestrictionTest.class),
                        BackgroundRestrictionTest.this);
                controller.addAppStateTracker(mAppBindServiceEventsTracker);
            } catch (NoSuchMethodException e) {
                // Won't happen.
            }
        }

        @Override
        ActivityManagerInternal getActivityManagerInternal() {
            return mActivityManagerInternal;
        }

        @Override
        AppRestrictionController getAppRestrictionController() {
            return mBgRestrictionController;
        }

        @Override
        AppOpsManager getAppOpsManager() {
            return mAppOpsManager;
        }

        @Override
        AppStandbyInternal getAppStandbyInternal() {
            return mAppStandbyInternal;
        }

        @Override
        AppHibernationManagerInternal getAppHibernationInternal() {
            return mAppHibernationInternal;
        }

        @Override
        AppStateTracker getAppStateTracker() {
            return mAppStateTracker;
        }

        @Override
        IActivityManager getIActivityManager() {
            return mIActivityManager;
        }

        @Override
        UserManagerInternal getUserManagerInternal() {
            return mUserManagerInternal;
        }

        @Override
        PackageManagerInternal getPackageManagerInternal() {
            return mPackageManagerInternal;
        }

        @Override
        PackageManager getPackageManager() {
            return mPackageManager;
        }

        @Override
        NotificationManager getNotificationManager() {
            return mNotificationManager;
        }

        @Override
        RoleManager getRoleManager() {
            return mRoleManager;
        }

        @Override
        AppFGSTracker getAppFGSTracker() {
            return mAppFGSTracker;
        }

        @Override
        AppMediaSessionTracker getAppMediaSessionTracker() {
            return mAppMediaSessionTracker;
        }

        @Override
        ActivityManagerService getActivityManagerService() {
            return mActivityManagerService;
        }

        @Override
        UidBatteryUsageProvider getUidBatteryUsageProvider() {
            return mAppBatteryTracker;
        }

        @Override
        AppBatteryExemptionTracker getAppBatteryExemptionTracker() {
            return mAppBatteryExemptionTracker;
        }
    }

    private class TestBaseTrackerInjector<T extends BaseAppStatePolicy>
            extends BaseAppStateTracker.Injector<T> {
        @Override
        void onSystemReady() {
            getPolicy().onSystemReady();
        }

        @Override
        ActivityManagerInternal getActivityManagerInternal() {
            return BackgroundRestrictionTest.this.mActivityManagerInternal;
        }

        @Override
        BatteryManagerInternal getBatteryManagerInternal() {
            return BackgroundRestrictionTest.this.mBatteryManagerInternal;
        }

        @Override
        BatteryStatsInternal getBatteryStatsInternal() {
            return BackgroundRestrictionTest.this.mBatteryStatsInternal;
        }

        @Override
        DeviceIdleInternal getDeviceIdleInternal() {
            return BackgroundRestrictionTest.this.mDeviceIdleInternal;
        }

        @Override
        UserManagerInternal getUserManagerInternal() {
            return BackgroundRestrictionTest.this.mUserManagerInternal;
        }

        @Override
        long currentTimeMillis() {
            return BackgroundRestrictionTest.this.mCurrentTimeMillis;
        }

        @Override
        PackageManager getPackageManager() {
            return BackgroundRestrictionTest.this.mPackageManager;
        }

        @Override
        PermissionManagerServiceInternal getPermissionManagerServiceInternal() {
            return BackgroundRestrictionTest.this.mPermissionManagerServiceInternal;
        }

        @Override
        AppOpsManager getAppOpsManager() {
            return BackgroundRestrictionTest.this.mAppOpsManager;
        }

        @Override
        MediaSessionManager getMediaSessionManager() {
            return BackgroundRestrictionTest.this.mMediaSessionManager;
        }

        @Override
        long getServiceStartForegroundTimeout() {
            return 1_000; // ms
        }

        @Override
        RoleManager getRoleManager() {
            return BackgroundRestrictionTest.this.mRoleManager;
        }
    }

    private class TestAppBatteryTrackerInjector extends TestBaseTrackerInjector<AppBatteryPolicy> {
        @Override
        void setPolicy(AppBatteryPolicy policy) {
            super.setPolicy(policy);
            BackgroundRestrictionTest.this.mAppBatteryPolicy = policy;
        }
    }

    private class TestAppBatteryExemptionTrackerInjector
            extends TestBaseTrackerInjector<AppBatteryExemptionPolicy> {
    }

    private class TestAppFGSTrackerInjector extends TestBaseTrackerInjector<AppFGSPolicy> {
    }

    private class TestAppMediaSessionTrackerInjector
            extends TestBaseTrackerInjector<AppMediaSessionPolicy> {
    }

    private class TestAppBroadcastEventsTrackerInjector
            extends TestBaseTrackerInjector<AppBroadcastEventsPolicy> {
        @Override
        void setPolicy(AppBroadcastEventsPolicy policy) {
            super.setPolicy(policy);
            policy.setTimeSlotSize(1_000L);
        }
    }

    private class TestAppBindServiceEventsTrackerInjector
            extends TestBaseTrackerInjector<AppBindServiceEventsPolicy> {
        @Override
        void setPolicy(AppBindServiceEventsPolicy policy) {
            super.setPolicy(policy);
            policy.setTimeSlotSize(1_000L);
        }
    }
}
