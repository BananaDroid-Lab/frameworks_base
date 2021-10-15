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

package com.android.server.notification;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.IMPORTANCE_NONE;
import static android.content.pm.PackageManager.FEATURE_WATCH;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.UserHandle.USER_SYSTEM;

import static com.android.server.notification.NotificationManagerService.ACTION_DISABLE_NAS;
import static com.android.server.notification.NotificationManagerService.ACTION_ENABLE_NAS;
import static com.android.server.notification.NotificationManagerService.ACTION_LEARNMORE_NAS;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.app.INotificationManager;
import android.app.IUriGrantsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.StatsManager;
import android.app.admin.DevicePolicyManagerInternal;
import android.app.usage.UsageStatsManagerInternal;
import android.companion.ICompanionDeviceManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutServiceInternal;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.service.notification.NotificationListenerFilter;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.testing.TestableLooper;
import android.testing.TestableLooper.RunWithLooper;
import android.testing.TestablePermissions;
import android.util.ArraySet;
import android.util.AtomicFile;

import androidx.test.InstrumentationRegistry;

import com.android.internal.logging.InstanceIdSequence;
import com.android.internal.logging.InstanceIdSequenceFake;
import com.android.server.DeviceIdleInternal;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.UiServiceTestCase;
import com.android.server.lights.LightsManager;
import com.android.server.lights.LogicalLight;
import com.android.server.notification.NotificationManagerService.NotificationAssistants;
import com.android.server.notification.NotificationManagerService.NotificationListeners;
import com.android.server.statusbar.StatusBarManagerInternal;
import com.android.server.uri.UriGrantsManagerInternal;
import com.android.server.utils.quota.MultiRateLimiter;
import com.android.server.wm.ActivityTaskManagerInternal;
import com.android.server.wm.WindowManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@SmallTest
@RunWith(AndroidTestingRunner.class)
@RunWithLooper
/**
 * Tests that NMS reads/writes the app notification state from Package/PermissionManager when
 * migration is enabled. Because the migration field is read onStart
 * TODO (b/194833441): migrate these tests to NotificationManagerServiceTest when the migration is
 * permanently enabled.
 */
public class NotificationPermissionMigrationTest extends UiServiceTestCase {
    private static final String TEST_CHANNEL_ID = "NotificationManagerServiceTestChannelId";
    private static final int UID_HEADLESS = 1000000;

    private final int mUid = Binder.getCallingUid();
    private TestableNotificationManagerService mService;
    private INotificationManager mBinderService;
    private NotificationManagerInternal mInternalService;
    private ShortcutHelper mShortcutHelper;
    @Mock
    private IPackageManager mPackageManager;
    @Mock
    private PackageManager mPackageManagerClient;
    @Mock
    private WindowManagerInternal mWindowManagerInternal;
    @Mock
    private PermissionHelper mPermissionHelper;
    private TestableContext mContext = spy(getContext());
    private final String PKG = mContext.getPackageName();
    private TestableLooper mTestableLooper;
    @Mock
    private RankingHelper mRankingHelper;
    @Mock private PreferencesHelper mPreferencesHelper;
    AtomicFile mPolicyFile;
    File mFile;
    @Mock
    private NotificationUsageStats mUsageStats;
    @Mock
    private UsageStatsManagerInternal mAppUsageStats;
    @Mock
    private AudioManager mAudioManager;
    @Mock
    private LauncherApps mLauncherApps;
    @Mock
    private ShortcutServiceInternal mShortcutServiceInternal;
    @Mock
    private UserManager mUserManager;
    @Mock
    ActivityManager mActivityManager;
    @Mock
    Resources mResources;
    @Mock
    RankingHandler mRankingHandler;
    @Mock
    ActivityManagerInternal mAmi;
    @Mock
    private Looper mMainLooper;

    @Mock
    IIntentSender pi1;

    private static final int MAX_POST_DELAY = 1000;

    private NotificationChannel mTestNotificationChannel = new NotificationChannel(
            TEST_CHANNEL_ID, TEST_CHANNEL_ID, IMPORTANCE_DEFAULT);

    private static final String VALID_CONVO_SHORTCUT_ID = "shortcut";

    @Mock
    private NotificationListeners mListeners;
    @Mock
    private NotificationListenerFilter mNlf;
    @Mock private NotificationAssistants mAssistants;
    @Mock private ConditionProviders mConditionProviders;
    private ManagedServices.ManagedServiceInfo mListener;
    @Mock private ICompanionDeviceManager mCompanionMgr;
    @Mock SnoozeHelper mSnoozeHelper;
    @Mock GroupHelper mGroupHelper;
    @Mock
    IBinder mPermOwner;
    @Mock
    IActivityManager mAm;
    @Mock
    ActivityTaskManagerInternal mAtm;
    @Mock
    IUriGrantsManager mUgm;
    @Mock
    UriGrantsManagerInternal mUgmInternal;
    @Mock
    AppOpsManager mAppOpsManager;
    @Mock
    private TestableNotificationManagerService.NotificationAssistantAccessGrantedCallback
            mNotificationAssistantAccessGrantedCallback;
    @Mock
    UserManager mUm;
    @Mock
    NotificationHistoryManager mHistoryManager;
    @Mock
    StatsManager mStatsManager;
    @Mock
    AlarmManager mAlarmManager;
    @Mock
    MultiRateLimiter mToastRateLimiter;
    BroadcastReceiver mPackageIntentReceiver;
    BroadcastReceiver mNASIntentReceiver;
    NotificationRecordLoggerFake mNotificationRecordLogger = new NotificationRecordLoggerFake();
    private InstanceIdSequence mNotificationInstanceIdSequence = new InstanceIdSequenceFake(
            1 << 30);
    @Mock
    StatusBarManagerInternal mStatusBar;

    private NotificationManagerService.WorkerHandler mWorkerHandler;

    @Before
    public void setUp() throws Exception {
        // These should be the only difference in setup from NMSTest
        Settings.Secure.putIntForUser(
                getContext().getContentResolver(),
                Settings.Secure.NOTIFICATION_PERMISSION_ENABLED, 1, USER_SYSTEM);
        Settings.Global.putInt(getContext().getContentResolver(),
                Settings.Global.SHOW_NOTIFICATION_CHANNEL_WARNINGS, 1);

        // Shell permissions will override permissions of our app, so add all necessary permissions
        // for this test here:
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                "android.permission.WRITE_DEVICE_CONFIG",
                "android.permission.READ_DEVICE_CONFIG",
                "android.permission.READ_CONTACTS");

        MockitoAnnotations.initMocks(this);

        DeviceIdleInternal deviceIdleInternal = mock(DeviceIdleInternal.class);
        when(deviceIdleInternal.getNotificationAllowlistDuration()).thenReturn(3000L);

        LocalServices.removeServiceForTest(UriGrantsManagerInternal.class);
        LocalServices.addService(UriGrantsManagerInternal.class, mUgmInternal);
        LocalServices.removeServiceForTest(WindowManagerInternal.class);
        LocalServices.addService(WindowManagerInternal.class, mWindowManagerInternal);
        LocalServices.removeServiceForTest(StatusBarManagerInternal.class);
        LocalServices.addService(StatusBarManagerInternal.class, mStatusBar);
        LocalServices.removeServiceForTest(DeviceIdleInternal.class);
        LocalServices.addService(DeviceIdleInternal.class, deviceIdleInternal);
        LocalServices.removeServiceForTest(ActivityManagerInternal.class);
        LocalServices.addService(ActivityManagerInternal.class, mAmi);
        mContext.addMockSystemService(Context.ALARM_SERVICE, mAlarmManager);

        doNothing().when(mContext).sendBroadcastAsUser(any(), any(), any());

        mService = new TestableNotificationManagerService(mContext, mNotificationRecordLogger,
                mNotificationInstanceIdSequence);

        // Use this testable looper.
        mTestableLooper = TestableLooper.get(this);
        // MockPackageManager - default returns ApplicationInfo with matching calling UID
        mContext.setMockPackageManager(mPackageManagerClient);

        when(mPackageManager.getApplicationInfo(anyString(), anyInt(), anyInt()))
                .thenAnswer((Answer<ApplicationInfo>) invocation -> {
                    Object[] args = invocation.getArguments();
                    return getApplicationInfo((String) args[0], mUid);
                });
        when(mPackageManagerClient.getApplicationInfoAsUser(anyString(), anyInt(), anyInt()))
                .thenAnswer((Answer<ApplicationInfo>) invocation -> {
                    Object[] args = invocation.getArguments();
                    return getApplicationInfo((String) args[0], mUid);
                });
        when(mPackageManagerClient.getPackageUidAsUser(any(), anyInt())).thenReturn(mUid);
        final LightsManager mockLightsManager = mock(LightsManager.class);
        when(mockLightsManager.getLight(anyInt())).thenReturn(mock(LogicalLight.class));
        when(mAudioManager.getRingerModeInternal()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        when(mPackageManagerClient.hasSystemFeature(FEATURE_WATCH)).thenReturn(false);
        when(mUgmInternal.newUriPermissionOwner(anyString())).thenReturn(mPermOwner);
        when(mPackageManager.getPackagesForUid(mUid)).thenReturn(new String[]{PKG});
        when(mPackageManagerClient.getPackagesForUid(anyInt())).thenReturn(new String[]{PKG});
        mContext.addMockSystemService(AppOpsManager.class, mock(AppOpsManager.class));

        // write to a test file; the system file isn't readable from tests
        mFile = new File(mContext.getCacheDir(), "test.xml");
        mFile.createNewFile();
        final String preupgradeXml = "<notification-policy></notification-policy>";
        mPolicyFile = new AtomicFile(mFile);
        FileOutputStream fos = mPolicyFile.startWrite();
        fos.write(preupgradeXml.getBytes());
        mPolicyFile.finishWrite(fos);

        // Setup managed services
        when(mNlf.isTypeAllowed(anyInt())).thenReturn(true);
        when(mNlf.isPackageAllowed(any())).thenReturn(true);
        when(mNlf.isPackageAllowed(null)).thenReturn(true);
        when(mListeners.getNotificationListenerFilter(any())).thenReturn(mNlf);
        mListener = mListeners.new ManagedServiceInfo(
                null, new ComponentName(PKG, "test_class"),
                UserHandle.getUserId(mUid), true, null, 0, 123);
        ComponentName defaultComponent = ComponentName.unflattenFromString("config/device");
        ArraySet<ComponentName> components = new ArraySet<>();
        components.add(defaultComponent);
        when(mListeners.getDefaultComponents()).thenReturn(components);
        when(mConditionProviders.getDefaultPackages())
                .thenReturn(new ArraySet<>(Arrays.asList("config")));
        when(mAssistants.getDefaultComponents()).thenReturn(components);
        when(mAssistants.queryPackageForServices(
                anyString(), anyInt(), anyInt())).thenReturn(components);
        when(mListeners.checkServiceTokenLocked(null)).thenReturn(mListener);
        ManagedServices.Config listenerConfig = new ManagedServices.Config();
        listenerConfig.xmlTag = NotificationListeners.TAG_ENABLED_NOTIFICATION_LISTENERS;
        when(mListeners.getConfig()).thenReturn(listenerConfig);
        ManagedServices.Config assistantConfig = new ManagedServices.Config();
        assistantConfig.xmlTag = NotificationAssistants.TAG_ENABLED_NOTIFICATION_ASSISTANTS;
        when(mAssistants.getConfig()).thenReturn(assistantConfig);
        ManagedServices.Config dndConfig = new ManagedServices.Config();
        dndConfig.xmlTag = ConditionProviders.TAG_ENABLED_DND_APPS;
        when(mConditionProviders.getConfig()).thenReturn(dndConfig);

        when(mAssistants.isAdjustmentAllowed(anyString())).thenReturn(true);

        // apps allowed as convos
        mService.setStringArrayResourceValue(PKG_O);

        mWorkerHandler = spy(mService.new WorkerHandler(mTestableLooper.getLooper()));
        mService.init(mWorkerHandler, mRankingHandler, mPackageManager, mPackageManagerClient,
                mockLightsManager, mListeners, mAssistants, mConditionProviders, mCompanionMgr,
                mSnoozeHelper, mUsageStats, mPolicyFile, mActivityManager, mGroupHelper, mAm, mAtm,
                mAppUsageStats, mock(DevicePolicyManagerInternal.class), mUgm, mUgmInternal,
                mAppOpsManager, mUm, mHistoryManager, mStatsManager, mock(TelephonyManager.class),
                mAmi, mToastRateLimiter, mPermissionHelper);
        // Return first true for RoleObserver main-thread check
        when(mMainLooper.isCurrentThread()).thenReturn(true).thenReturn(false);
        mService.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY, mMainLooper);

        mService.setAudioManager(mAudioManager);

        mShortcutHelper = mService.getShortcutHelper();
        mShortcutHelper.setLauncherApps(mLauncherApps);
        mShortcutHelper.setShortcutServiceInternal(mShortcutServiceInternal);
        mShortcutHelper.setUserManager(mUserManager);

        // Capture PackageIntentReceiver
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        ArgumentCaptor<IntentFilter> intentFilterCaptor =
                ArgumentCaptor.forClass(IntentFilter.class);

        verify(mContext, atLeastOnce()).registerReceiverAsUser(broadcastReceiverCaptor.capture(),
                any(), intentFilterCaptor.capture(), any(), any());
        verify(mContext, atLeastOnce()).registerReceiver(broadcastReceiverCaptor.capture(),
                intentFilterCaptor.capture());
        List<BroadcastReceiver> broadcastReceivers = broadcastReceiverCaptor.getAllValues();
        List<IntentFilter> intentFilters = intentFilterCaptor.getAllValues();

        for (int i = 0; i < intentFilters.size(); i++) {
            final IntentFilter filter = intentFilters.get(i);
            if (filter.hasAction(Intent.ACTION_DISTRACTING_PACKAGES_CHANGED)
                    && filter.hasAction(Intent.ACTION_PACKAGES_UNSUSPENDED)
                    && filter.hasAction(Intent.ACTION_PACKAGES_SUSPENDED)) {
                mPackageIntentReceiver = broadcastReceivers.get(i);
            } else if (filter.hasAction(ACTION_ENABLE_NAS)
                    && filter.hasAction(ACTION_DISABLE_NAS)
                    && filter.hasAction(ACTION_LEARNMORE_NAS)) {
                mNASIntentReceiver = broadcastReceivers.get(i);
            }
        }
        assertNotNull("package intent receiver should exist", mPackageIntentReceiver);
        assertNotNull("nas intent receiver should exist", mNASIntentReceiver);

        // Pretend the shortcut exists
        List<ShortcutInfo> shortcutInfos = new ArrayList<>();
        ShortcutInfo info = mock(ShortcutInfo.class);
        when(info.getPackage()).thenReturn(PKG);
        when(info.getId()).thenReturn(VALID_CONVO_SHORTCUT_ID);
        when(info.getUserId()).thenReturn(USER_SYSTEM);
        when(info.isLongLived()).thenReturn(true);
        when(info.isEnabled()).thenReturn(true);
        shortcutInfos.add(info);
        when(mLauncherApps.getShortcuts(any(), any())).thenReturn(shortcutInfos);
        when(mShortcutServiceInternal.isSharingShortcut(anyInt(), anyString(), anyString(),
                anyString(), anyInt(), any())).thenReturn(true);
        when(mUserManager.isUserUnlocked(any(UserHandle.class))).thenReturn(true);

        // Set the testable bubble extractor
        RankingHelper rankingHelper = mService.getRankingHelper();
        BubbleExtractor extractor = rankingHelper.findExtractor(BubbleExtractor.class);
        extractor.setActivityManager(mActivityManager);

        // Tests call directly into the Binder.
        mBinderService = mService.getBinderService();
        mInternalService = mService.getInternalService();

        mBinderService.createNotificationChannels(
                PKG, new ParceledListSlice(Arrays.asList(mTestNotificationChannel)));
        mBinderService.createNotificationChannels(
                PKG_P, new ParceledListSlice(Arrays.asList(mTestNotificationChannel)));
        mBinderService.createNotificationChannels(
                PKG_O, new ParceledListSlice(Arrays.asList(mTestNotificationChannel)));
        assertNotNull(mBinderService.getNotificationChannel(
                PKG, mContext.getUserId(), PKG, TEST_CHANNEL_ID));
        clearInvocations(mRankingHandler);
    }

    @After
    public void tearDown() throws Exception {
        if (mFile != null) mFile.delete();

        try {
            mService.onDestroy();
        } catch (IllegalStateException | IllegalArgumentException e) {
            // can throw if a broadcast receiver was never registered
        }

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().dropShellPermissionIdentity();
        // Remove scheduled messages that would be processed when the test is already done, and
        // could cause issues, for example, messages that remove/cancel shown toasts (this causes
        // problematic interactions with mocks when they're no longer working as expected).
        mWorkerHandler.removeCallbacksAndMessages(null);
    }

    private ApplicationInfo getApplicationInfo(String pkg, int uid) {
        final ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.uid = uid;
        switch (pkg) {
            case PKG_N_MR1:
                applicationInfo.targetSdkVersion = Build.VERSION_CODES.N_MR1;
                break;
            case PKG_O:
                applicationInfo.targetSdkVersion = Build.VERSION_CODES.O;
                break;
            case PKG_P:
                applicationInfo.targetSdkVersion = Build.VERSION_CODES.P;
                break;
            default:
                applicationInfo.targetSdkVersion = Build.VERSION_CODES.CUR_DEVELOPMENT;
                break;
        }
        return applicationInfo;
    }

    public void waitForIdle() {
        mTestableLooper.processAllMessages();
    }

    private NotificationRecord generateNotificationRecord(NotificationChannel channel) {
        return generateNotificationRecord(channel, null);
    }

    private NotificationRecord generateNotificationRecord(NotificationChannel channel,
            Notification.TvExtender extender) {
        if (channel == null) {
            channel = mTestNotificationChannel;
        }
        Notification.Builder nb = new Notification.Builder(mContext, channel.getId())
                .setContentTitle("foo")
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .addAction(new Notification.Action.Builder(null, "test", null).build());
        if (extender != null) {
            nb.extend(extender);
        }
        StatusBarNotification sbn = new StatusBarNotification(PKG, PKG, 8, "tag", mUid, 0,
                nb.build(), UserHandle.getUserHandleForUid(mUid), null, 0);
        return new NotificationRecord(mContext, sbn, channel);
    }

    private void enableInteractAcrossUsers() {
        TestablePermissions perms = mContext.getTestablePermissions();
        perms.setPermission(android.Manifest.permission.INTERACT_ACROSS_USERS, PERMISSION_GRANTED);
    }

    @Test
    public void testAreNotificationsEnabledForPackage() throws Exception {
        mBinderService.areNotificationsEnabledForPackage(mContext.getPackageName(),
                mUid);

        verify(mPermissionHelper).hasPermission(mUid);
    }

    @Test
    public void testAreNotificationsEnabledForPackage_crossUser() throws Exception {
        try {
            mBinderService.areNotificationsEnabledForPackage(mContext.getPackageName(),
                    mUid + UserHandle.PER_USER_RANGE);
            fail("Cannot call cross user without permission");
        } catch (SecurityException e) {
            // pass
        }
        verify(mPermissionHelper, never()).hasPermission(anyInt());

        // cross user, with permission, no problem
        enableInteractAcrossUsers();
        mBinderService.areNotificationsEnabledForPackage(mContext.getPackageName(),
                mUid + UserHandle.PER_USER_RANGE);

        verify(mPermissionHelper).hasPermission(mUid + UserHandle.PER_USER_RANGE);
    }

    @Test
    public void testGetPackageImportance() throws Exception {
        when(mPermissionHelper.hasPermission(mUid)).thenReturn(true);
        assertThat(mBinderService.getPackageImportance(mContext.getPackageName()))
                .isEqualTo(IMPORTANCE_DEFAULT);

        when(mPermissionHelper.hasPermission(mUid)).thenReturn(false);
        assertThat(mBinderService.getPackageImportance(mContext.getPackageName()))
                .isEqualTo(IMPORTANCE_NONE);
    }

    @Test
    public void testEnqueueNotificationInternal_noChannel() throws Exception {
        when(mPermissionHelper.hasPermission(mUid)).thenReturn(false);
        NotificationRecord nr = generateNotificationRecord(
                new NotificationChannel("did not create", "", IMPORTANCE_DEFAULT));

        mBinderService.enqueueNotificationWithTag(PKG, PKG, nr.getSbn().getTag(),
                nr.getSbn().getId(), nr.getSbn().getNotification(), nr.getSbn().getUserId());
        waitForIdle();

        verify(mPermissionHelper).hasPermission(mUid);
        verify(mPermissionHelper, never()).hasPermission(Process.SYSTEM_UID);

        reset(mPermissionHelper);
        when(mPermissionHelper.hasPermission(mUid)).thenReturn(true);

        mBinderService.enqueueNotificationWithTag(PKG, PKG, nr.getSbn().getTag(),
                nr.getSbn().getId(), nr.getSbn().getNotification(), nr.getSbn().getUserId());
        waitForIdle();

        verify(mPermissionHelper).hasPermission(mUid);
        assertThat(mService.mChannelToastsSent).contains(mUid);
    }

    @Test
    public void testSetNotificationsEnabledForPackage_noChange() throws Exception {
        when(mPermissionHelper.hasPermission(mUid)).thenReturn(true);
        mBinderService.setNotificationsEnabledForPackage(mContext.getPackageName(), mUid, true);

        verify(mPermissionHelper, never()).setNotificationPermission(
                anyString(), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    public void testSetNotificationsEnabledForPackage() throws Exception {
        when(mPermissionHelper.hasPermission(mUid)).thenReturn(true);
        mBinderService.setNotificationsEnabledForPackage(mContext.getPackageName(), mUid, false);

        verify(mPermissionHelper, never()).setNotificationPermission(
                mContext.getPackageName(), UserHandle.getUserId(mUid), false, true);
    }
}
