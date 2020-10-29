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
 * limitations under the License.
 */
package com.android.server.alarm;

import static android.app.AlarmManager.ELAPSED_REALTIME;
import static android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP;
import static android.app.AlarmManager.FLAG_ALLOW_WHILE_IDLE;
import static android.app.AlarmManager.FLAG_ALLOW_WHILE_IDLE_UNRESTRICTED;
import static android.app.AlarmManager.FLAG_IDLE_UNTIL;
import static android.app.AlarmManager.FLAG_STANDALONE;
import static android.app.AlarmManager.FLAG_WAKE_FROM_IDLE;
import static android.app.AlarmManager.RTC;
import static android.app.AlarmManager.RTC_WAKEUP;
import static android.app.AlarmManager.WINDOW_EXACT;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_FREQUENT;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_RESTRICTED;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_WORKING_SET;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doCallRealMethod;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;
import static com.android.server.alarm.AlarmManagerService.ACTIVE_INDEX;
import static com.android.server.alarm.AlarmManagerService.AlarmHandler.APP_STANDBY_BUCKET_CHANGED;
import static com.android.server.alarm.AlarmManagerService.AlarmHandler.CHARGING_STATUS_CHANGED;
import static com.android.server.alarm.AlarmManagerService.AlarmHandler.REMOVE_FOR_CANCELED;
import static com.android.server.alarm.AlarmManagerService.Constants.KEY_ALLOW_WHILE_IDLE_LONG_TIME;
import static com.android.server.alarm.AlarmManagerService.Constants.KEY_ALLOW_WHILE_IDLE_SHORT_TIME;
import static com.android.server.alarm.AlarmManagerService.Constants.KEY_ALLOW_WHILE_IDLE_WHITELIST_DURATION;
import static com.android.server.alarm.AlarmManagerService.Constants.KEY_LISTENER_TIMEOUT;
import static com.android.server.alarm.AlarmManagerService.Constants.KEY_MAX_INTERVAL;
import static com.android.server.alarm.AlarmManagerService.Constants.KEY_MIN_FUTURITY;
import static com.android.server.alarm.AlarmManagerService.Constants.KEY_MIN_INTERVAL;
import static com.android.server.alarm.AlarmManagerService.IS_WAKEUP_MASK;
import static com.android.server.alarm.AlarmManagerService.TIME_CHANGED_MASK;
import static com.android.server.alarm.AlarmManagerService.WORKING_INDEX;
import static com.android.server.alarm.Constants.TEST_CALLING_PACKAGE;
import static com.android.server.alarm.Constants.TEST_CALLING_UID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.IActivityManager;
import android.app.IAlarmCompleteListener;
import android.app.IAlarmListener;
import android.app.PendingIntent;
import android.app.usage.UsageStatsManagerInternal;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.runner.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.MockedVoidMethod;
import com.android.internal.annotations.GuardedBy;
import com.android.server.AlarmManagerInternal;
import com.android.server.AppStateTracker;
import com.android.server.AppStateTrackerImpl;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.usage.AppStandbyInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Executor;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class AlarmManagerServiceTest {
    private static final String TAG = AlarmManagerServiceTest.class.getSimpleName();
    private static final int SYSTEM_UI_UID = 12345;
    private static final int TEST_CALLING_USER = UserHandle.getUserId(TEST_CALLING_UID);

    private long mAppStandbyWindow;
    private AlarmManagerService mService;
    private AppStandbyInternal.AppIdleStateChangeListener mAppStandbyListener;
    private AlarmManagerService.ChargingReceiver mChargingReceiver;
    @Mock
    private Context mMockContext;
    @Mock
    private IActivityManager mIActivityManager;
    @Mock
    private UsageStatsManagerInternal mUsageStatsManagerInternal;
    @Mock
    private AppStandbyInternal mAppStandbyInternal;
    @Mock
    private ActivityManagerInternal mActivityManagerInternal;
    @Mock
    private AppStateTrackerImpl mAppStateTracker;
    @Mock
    private AlarmManagerService.ClockReceiver mClockReceiver;
    @Mock
    private PowerManager.WakeLock mWakeLock;
    @Mock
    DeviceConfig.Properties mDeviceConfigProperties;
    HashSet<String> mDeviceConfigKeys = new HashSet<>();

    private MockitoSession mMockingSession;
    private Injector mInjector;
    private volatile long mNowElapsedTest;
    private volatile long mNowRtcTest;
    @GuardedBy("mTestTimer")
    private TestTimer mTestTimer = new TestTimer();

    static class TestTimer {
        private long mElapsed;
        boolean mExpired;
        private int mType;
        private int mFlags; // Flags used to decide what needs to be evaluated.

        synchronized long getElapsed() {
            return mElapsed;
        }

        synchronized void set(int type, long millisElapsed) {
            mType = type;
            mElapsed = millisElapsed;
        }

        synchronized int getType() {
            return mType;
        }

        synchronized int getFlags() {
            return mFlags;
        }

        synchronized void expire() throws InterruptedException {
            expire(IS_WAKEUP_MASK); // Default: evaluate eligibility of all alarms
        }

        synchronized void expire(int flags) throws InterruptedException {
            mFlags = flags;
            mExpired = true;
            notifyAll();
            // Now wait for the alarm thread to finish execution.
            wait();
        }
    }

    public class Injector extends AlarmManagerService.Injector {

        Injector(Context context) {
            super(context);
        }

        @Override
        void init() {
            // Do nothing.
        }

        @Override
        int waitForAlarm() {
            synchronized (mTestTimer) {
                mTestTimer.notifyAll();
                if (!mTestTimer.mExpired) {
                    try {
                        mTestTimer.wait();
                    } catch (InterruptedException ie) {
                        Log.e(TAG, "Wait interrupted!", ie);
                        return 0;
                    }
                }
                mTestTimer.mExpired = false;
            }
            return mTestTimer.getFlags();
        }

        @Override
        void setKernelTimezone(int minutesWest) {
            // Do nothing.
        }

        @Override
        void setAlarm(int type, long millis) {
            mTestTimer.set(type, millis);
        }

        @Override
        void setKernelTime(long millis) {
        }

        @Override
        int getSystemUiUid() {
            return SYSTEM_UI_UID;
        }

        @Override
        boolean isAlarmDriverPresent() {
            // Pretend the driver is present, so code does not fall back to handler
            return true;
        }

        @Override
        long getElapsedRealtime() {
            return mNowElapsedTest;
        }

        @Override
        long getCurrentTimeMillis() {
            return mNowRtcTest;
        }

        @Override
        AlarmManagerService.ClockReceiver getClockReceiver(AlarmManagerService service) {
            return mClockReceiver;
        }

        @Override
        PowerManager.WakeLock getAlarmWakeLock() {
            return mWakeLock;
        }

        @Override
        void registerDeviceConfigListener(DeviceConfig.OnPropertiesChangedListener listener) {
            // Do nothing.
            // The tests become flaky with an error message of
            // "IllegalStateException: Querying activity state off main thread is not allowed."
            // when AlarmManager calls DeviceConfig.addOnPropertiesChangedListener().
        }
    }

    @Before
    public final void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .spyStatic(ActivityManager.class)
                .spyStatic(DeviceConfig.class)
                .mockStatic(LocalServices.class)
                .spyStatic(Looper.class)
                .mockStatic(Settings.Global.class)
                .mockStatic(ServiceManager.class)
                .spyStatic(UserHandle.class)
                .strictness(Strictness.WARN)
                .startMocking();
        doReturn(mIActivityManager).when(ActivityManager::getService);
        doReturn(mActivityManagerInternal).when(
                () -> LocalServices.getService(ActivityManagerInternal.class));
        doReturn(mAppStateTracker).when(() -> LocalServices.getService(AppStateTracker.class));
        doReturn(mAppStandbyInternal).when(
                () -> LocalServices.getService(AppStandbyInternal.class));
        doReturn(mUsageStatsManagerInternal).when(
                () -> LocalServices.getService(UsageStatsManagerInternal.class));
        doCallRealMethod().when((MockedVoidMethod) () ->
                LocalServices.addService(eq(AlarmManagerInternal.class), any()));
        doCallRealMethod().when(() -> LocalServices.getService(AlarmManagerInternal.class));
        doReturn(false).when(() -> UserHandle.isCore(TEST_CALLING_UID));
        when(mUsageStatsManagerInternal.getAppStandbyBucket(eq(TEST_CALLING_PACKAGE),
                eq(TEST_CALLING_USER), anyLong())).thenReturn(STANDBY_BUCKET_ACTIVE);
        doReturn(Looper.getMainLooper()).when(Looper::myLooper);

        doReturn(mDeviceConfigKeys).when(mDeviceConfigProperties).getKeyset();
        when(mDeviceConfigProperties.getLong(anyString(), anyLong()))
                .thenAnswer((Answer<Long>) invocationOnMock -> {
                    Object[] args = invocationOnMock.getArguments();
                    return (Long) args[1];
                });
        when(mDeviceConfigProperties.getInt(anyString(), anyInt()))
                .thenAnswer((Answer<Integer>) invocationOnMock -> {
                    Object[] args = invocationOnMock.getArguments();
                    return (Integer) args[1];
                });
        doAnswer((Answer<Void>) invocationOnMock -> null)
                .when(() -> DeviceConfig.addOnPropertiesChangedListener(
                        anyString(), any(Executor.class),
                        any(DeviceConfig.OnPropertiesChangedListener.class)));
        doReturn(mDeviceConfigProperties).when(
                () -> DeviceConfig.getProperties(
                        eq(DeviceConfig.NAMESPACE_ALARM_MANAGER), ArgumentMatchers.<String>any()));

        mInjector = new Injector(mMockContext);
        mService = new AlarmManagerService(mMockContext, mInjector);
        spyOn(mService);

        mService.onStart();
        spyOn(mService.mHandler);
        // Stubbing the handler. Test should simulate any handling of messages synchronously.
        doReturn(true).when(mService.mHandler).sendMessageAtTime(any(Message.class), anyLong());

        assertEquals(mService.mSystemUiUid, SYSTEM_UI_UID);
        assertEquals(mService.mClockReceiver, mClockReceiver);
        assertEquals(mService.mWakeLock, mWakeLock);

        // Other boot phases don't matter
        mService.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);
        mAppStandbyWindow = mService.mConstants.APP_STANDBY_WINDOW;
        ArgumentCaptor<AppStandbyInternal.AppIdleStateChangeListener> captor =
                ArgumentCaptor.forClass(AppStandbyInternal.AppIdleStateChangeListener.class);
        verify(mAppStandbyInternal).addListener(captor.capture());
        mAppStandbyListener = captor.getValue();

        ArgumentCaptor<AlarmManagerService.ChargingReceiver> chargingReceiverCaptor =
                ArgumentCaptor.forClass(AlarmManagerService.ChargingReceiver.class);
        verify(mMockContext).registerReceiver(chargingReceiverCaptor.capture(),
                argThat((filter) -> filter.hasAction(BatteryManager.ACTION_CHARGING)
                        && filter.hasAction(BatteryManager.ACTION_DISCHARGING)));
        mChargingReceiver = chargingReceiverCaptor.getValue();

        setTestableQuotas();
    }

    private void setTestAlarm(int type, long triggerTime, PendingIntent operation) {
        setTestAlarm(type, triggerTime, operation, 0, FLAG_STANDALONE, TEST_CALLING_UID);
    }

    private void setRepeatingTestAlarm(int type, long firstTrigger, long interval,
            PendingIntent pi) {
        setTestAlarm(type, firstTrigger, pi, interval, FLAG_STANDALONE, TEST_CALLING_UID);
    }

    private void setIdleUntilAlarm(int type, long triggerTime, PendingIntent pi) {
        setTestAlarm(type, triggerTime, pi, 0, FLAG_IDLE_UNTIL, TEST_CALLING_UID);
    }

    private void setWakeFromIdle(int type, long triggerTime, PendingIntent pi) {
        // Note: Only alarm clock alarms are allowed to include this flag in the actual service.
        // But this is a unit test so we'll only test the flag for granularity and convenience.
        setTestAlarm(type, triggerTime, pi, 0, FLAG_WAKE_FROM_IDLE | FLAG_STANDALONE,
                TEST_CALLING_UID);
    }

    private void setAllowWhileIdleAlarm(int type, long triggerTime, PendingIntent pi,
            boolean unrestricted) {
        final int flags = unrestricted ? FLAG_ALLOW_WHILE_IDLE_UNRESTRICTED : FLAG_ALLOW_WHILE_IDLE;
        setTestAlarm(type, triggerTime, pi, 0, flags, TEST_CALLING_UID);
    }

    private void setTestAlarm(int type, long triggerTime, PendingIntent operation, long interval,
            int flags, int callingUid) {
        mService.setImpl(type, triggerTime, WINDOW_EXACT, interval, operation, null, "test", flags,
                null, null, callingUid, TEST_CALLING_PACKAGE);
    }

    private void setTestAlarmWithListener(int type, long triggerTime, IAlarmListener listener) {
        mService.setImpl(type, triggerTime, WINDOW_EXACT, 0, null, listener, "test",
                FLAG_STANDALONE, null, null, TEST_CALLING_UID, TEST_CALLING_PACKAGE);
    }


    private PendingIntent getNewMockPendingIntent() {
        return getNewMockPendingIntent(TEST_CALLING_UID, TEST_CALLING_PACKAGE);
    }

    private PendingIntent getNewMockPendingIntent(int creatorUid, String creatorPackage) {
        final PendingIntent mockPi = mock(PendingIntent.class, Answers.RETURNS_DEEP_STUBS);
        when(mockPi.getCreatorUid()).thenReturn(creatorUid);
        when(mockPi.getCreatorPackage()).thenReturn(creatorPackage);
        return mockPi;
    }

    private void setDeviceConfigLong(String key, long val) {
        mDeviceConfigKeys.add(key);
        doReturn(val).when(mDeviceConfigProperties).getLong(eq(key), anyLong());
        mService.mConstants.onPropertiesChanged(mDeviceConfigProperties);
    }

    /**
     * Lowers quotas to make testing feasible. Careful while calling as this will replace any
     * existing settings for the calling test.
     */
    private void setTestableQuotas() {
        setDeviceConfigLong(KEY_MIN_FUTURITY, 0);
        setDeviceConfigLong(KEY_MIN_INTERVAL, 0);
        mDeviceConfigKeys.add(mService.mConstants.KEYS_APP_STANDBY_QUOTAS[ACTIVE_INDEX]);
        mDeviceConfigKeys.add(mService.mConstants.KEYS_APP_STANDBY_QUOTAS[WORKING_INDEX]);
        doReturn(8).when(mDeviceConfigProperties)
                .getInt(eq(mService.mConstants.KEYS_APP_STANDBY_QUOTAS[ACTIVE_INDEX]), anyInt());
        doReturn(5).when(mDeviceConfigProperties)
                .getInt(eq(mService.mConstants.KEYS_APP_STANDBY_QUOTAS[WORKING_INDEX]), anyInt());

        mService.mConstants.onPropertiesChanged(mDeviceConfigProperties);
    }

    @Test
    public void singleElapsedAlarmSet() {
        final long triggerTime = mNowElapsedTest + 5000;
        final PendingIntent alarmPi = getNewMockPendingIntent();
        setTestAlarm(ELAPSED_REALTIME_WAKEUP, triggerTime, alarmPi);
        assertEquals(triggerTime, mTestTimer.getElapsed());
    }

    @Test
    public void singleRtcAlarmSet() {
        mNowElapsedTest = 54;
        mNowRtcTest = 1243;     // arbitrary values of time
        final long triggerRtc = mNowRtcTest + 5000;
        final PendingIntent alarmPi = getNewMockPendingIntent();
        setTestAlarm(RTC_WAKEUP, triggerRtc, alarmPi);
        final long triggerElapsed = triggerRtc - (mNowRtcTest - mNowElapsedTest);
        assertEquals(triggerElapsed, mTestTimer.getElapsed());
    }

    @Test
    public void timeChangeMovesRtcAlarm() throws Exception {
        mNowElapsedTest = 42;
        mNowRtcTest = 4123;     // arbitrary values of time
        final long triggerRtc = mNowRtcTest + 5000;
        final PendingIntent alarmPi = getNewMockPendingIntent();
        setTestAlarm(RTC_WAKEUP, triggerRtc, alarmPi);
        final long triggerElapsed1 = mTestTimer.getElapsed();
        final long timeDelta = -123;
        mNowRtcTest += timeDelta;
        mTestTimer.expire(TIME_CHANGED_MASK);
        final long triggerElapsed2 = mTestTimer.getElapsed();
        assertEquals("Invalid movement of triggerElapsed following time change", triggerElapsed2,
                triggerElapsed1 - timeDelta);
    }

    @Test
    public void testSingleAlarmExpiration() throws Exception {
        final long triggerTime = mNowElapsedTest + 5000;
        final PendingIntent alarmPi = getNewMockPendingIntent();
        setTestAlarm(ELAPSED_REALTIME_WAKEUP, triggerTime, alarmPi);

        mNowElapsedTest = mTestTimer.getElapsed();
        mTestTimer.expire();

        final ArgumentCaptor<PendingIntent.OnFinished> onFinishedCaptor =
                ArgumentCaptor.forClass(PendingIntent.OnFinished.class);
        verify(alarmPi).send(eq(mMockContext), eq(0), any(Intent.class),
                onFinishedCaptor.capture(), any(Handler.class), isNull(), any());
        verify(mWakeLock).acquire();
        onFinishedCaptor.getValue().onSendFinished(alarmPi, null, 0, null, null);
        verify(mWakeLock).release();
    }

    @Test
    public void testUpdateConstants() {
        setDeviceConfigLong(KEY_MIN_FUTURITY, 5);
        setDeviceConfigLong(KEY_MIN_INTERVAL, 10);
        setDeviceConfigLong(KEY_MAX_INTERVAL, 15);
        setDeviceConfigLong(KEY_ALLOW_WHILE_IDLE_SHORT_TIME, 20);
        setDeviceConfigLong(KEY_ALLOW_WHILE_IDLE_LONG_TIME, 25);
        setDeviceConfigLong(KEY_ALLOW_WHILE_IDLE_WHITELIST_DURATION, 30);
        setDeviceConfigLong(KEY_LISTENER_TIMEOUT, 35);
        assertEquals(5, mService.mConstants.MIN_FUTURITY);
        assertEquals(10, mService.mConstants.MIN_INTERVAL);
        assertEquals(15, mService.mConstants.MAX_INTERVAL);
        assertEquals(20, mService.mConstants.ALLOW_WHILE_IDLE_SHORT_TIME);
        assertEquals(25, mService.mConstants.ALLOW_WHILE_IDLE_LONG_TIME);
        assertEquals(30, mService.mConstants.ALLOW_WHILE_IDLE_WHITELIST_DURATION);
        assertEquals(35, mService.mConstants.LISTENER_TIMEOUT);
    }

    @Test
    public void testMinFuturity() {
        setDeviceConfigLong(KEY_MIN_FUTURITY, 10L);
        assertEquals(10, mService.mConstants.MIN_FUTURITY);
        final long triggerTime = mNowElapsedTest + 1;
        final long expectedTriggerTime = mNowElapsedTest + mService.mConstants.MIN_FUTURITY;
        setTestAlarm(ELAPSED_REALTIME_WAKEUP, triggerTime, getNewMockPendingIntent());
        assertEquals(expectedTriggerTime, mTestTimer.getElapsed());
    }

    @Test
    public void testMinFuturityCoreUid() {
        setDeviceConfigLong(KEY_MIN_FUTURITY, 10L);
        assertEquals(10, mService.mConstants.MIN_FUTURITY);
        final long triggerTime = mNowElapsedTest + 1;
        doReturn(true).when(() -> UserHandle.isCore(TEST_CALLING_UID));
        final long expectedTriggerTime = triggerTime;
        setTestAlarm(ELAPSED_REALTIME_WAKEUP, triggerTime, getNewMockPendingIntent());
        assertEquals(expectedTriggerTime, mTestTimer.getElapsed());
    }

    @Test
    public void testEarliestAlarmSet() {
        final PendingIntent pi6 = getNewMockPendingIntent();
        final PendingIntent pi8 = getNewMockPendingIntent();
        final PendingIntent pi9 = getNewMockPendingIntent();

        setTestAlarm(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 8, pi8);
        assertEquals(mNowElapsedTest + 8, mTestTimer.getElapsed());

        setTestAlarm(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 9, pi9);
        assertEquals(mNowElapsedTest + 8, mTestTimer.getElapsed());

        setTestAlarm(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 6, pi6);
        assertEquals(mNowElapsedTest + 6, mTestTimer.getElapsed());

        mService.removeLocked(pi6, null);
        assertEquals(mNowElapsedTest + 8, mTestTimer.getElapsed());

        mService.removeLocked(pi8, null);
        assertEquals(mNowElapsedTest + 9, mTestTimer.getElapsed());
    }

    private void testQuotasDeferralOnSet(int standbyBucket) throws Exception {
        final int quota = mService.getQuotaForBucketLocked(standbyBucket);
        when(mUsageStatsManagerInternal.getAppStandbyBucket(eq(TEST_CALLING_PACKAGE), anyInt(),
                anyLong())).thenReturn(standbyBucket);
        final long firstTrigger = mNowElapsedTest + 10;
        for (int i = 0; i < quota; i++) {
            setTestAlarm(ELAPSED_REALTIME_WAKEUP, firstTrigger + i,
                    getNewMockPendingIntent());
            mNowElapsedTest = mTestTimer.getElapsed();
            mTestTimer.expire();
        }
        // This one should get deferred on set
        setTestAlarm(ELAPSED_REALTIME_WAKEUP, firstTrigger + quota,
                getNewMockPendingIntent());
        final long expectedNextTrigger = firstTrigger + mAppStandbyWindow;
        assertEquals("Incorrect next alarm trigger", expectedNextTrigger, mTestTimer.getElapsed());
    }

    private void testQuotasDeferralOnExpiration(int standbyBucket) throws Exception {
        final int quota = mService.getQuotaForBucketLocked(standbyBucket);
        when(mUsageStatsManagerInternal.getAppStandbyBucket(eq(TEST_CALLING_PACKAGE), anyInt(),
                anyLong())).thenReturn(standbyBucket);
        final long firstTrigger = mNowElapsedTest + 10;
        for (int i = 0; i < quota; i++) {
            setTestAlarm(ELAPSED_REALTIME_WAKEUP, firstTrigger + i,
                    getNewMockPendingIntent());
        }
        // This one should get deferred after the latest alarm expires
        setTestAlarm(ELAPSED_REALTIME_WAKEUP, firstTrigger + quota,
                getNewMockPendingIntent());
        for (int i = 0; i < quota; i++) {
            mNowElapsedTest = mTestTimer.getElapsed();
            mTestTimer.expire();
        }
        final long expectedNextTrigger = firstTrigger + mAppStandbyWindow;
        assertEquals("Incorrect next alarm trigger", expectedNextTrigger, mTestTimer.getElapsed());
    }

    private void testQuotasNoDeferral(int standbyBucket) throws Exception {
        final int quota = mService.getQuotaForBucketLocked(standbyBucket);
        when(mUsageStatsManagerInternal.getAppStandbyBucket(eq(TEST_CALLING_PACKAGE), anyInt(),
                anyLong())).thenReturn(standbyBucket);
        final long firstTrigger = mNowElapsedTest + 10;
        for (int i = 0; i < quota; i++) {
            setTestAlarm(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 10 + i,
                    getNewMockPendingIntent());
        }
        // This delivery time maintains the quota invariant. Should not be deferred.
        final long expectedNextTrigger = firstTrigger + mAppStandbyWindow + 5;
        setTestAlarm(ELAPSED_REALTIME_WAKEUP, expectedNextTrigger, getNewMockPendingIntent());
        for (int i = 0; i < quota; i++) {
            mNowElapsedTest = mTestTimer.getElapsed();
            mTestTimer.expire();
        }
        assertEquals("Incorrect next alarm trigger", expectedNextTrigger, mTestTimer.getElapsed());
    }

    @Test
    public void testActiveQuota_deferredOnSet() throws Exception {
        testQuotasDeferralOnSet(STANDBY_BUCKET_ACTIVE);
    }

    @Test
    public void testActiveQuota_deferredOnExpiration() throws Exception {
        testQuotasDeferralOnExpiration(STANDBY_BUCKET_ACTIVE);
    }

    @Test
    public void testActiveQuota_notDeferred() throws Exception {
        testQuotasNoDeferral(STANDBY_BUCKET_ACTIVE);
    }

    @Test
    public void testWorkingQuota_deferredOnSet() throws Exception {
        testQuotasDeferralOnSet(STANDBY_BUCKET_WORKING_SET);
    }

    @Test
    public void testWorkingQuota_deferredOnExpiration() throws Exception {
        testQuotasDeferralOnExpiration(STANDBY_BUCKET_WORKING_SET);
    }

    @Test
    public void testWorkingQuota_notDeferred() throws Exception {
        testQuotasNoDeferral(STANDBY_BUCKET_WORKING_SET);
    }

    @Test
    public void testFrequentQuota_deferredOnSet() throws Exception {
        testQuotasDeferralOnSet(STANDBY_BUCKET_FREQUENT);
    }

    @Test
    public void testFrequentQuota_deferredOnExpiration() throws Exception {
        testQuotasDeferralOnExpiration(STANDBY_BUCKET_FREQUENT);
    }

    @Test
    public void testFrequentQuota_notDeferred() throws Exception {
        testQuotasNoDeferral(STANDBY_BUCKET_FREQUENT);
    }

    @Test
    public void testRareQuota_deferredOnSet() throws Exception {
        testQuotasDeferralOnSet(STANDBY_BUCKET_RARE);
    }

    @Test
    public void testRareQuota_deferredOnExpiration() throws Exception {
        testQuotasDeferralOnExpiration(STANDBY_BUCKET_RARE);
    }

    @Test
    public void testRareQuota_notDeferred() throws Exception {
        testQuotasNoDeferral(STANDBY_BUCKET_RARE);
    }

    @Test
    public void testRestrictedBucketAlarmsDeferredOnSet() throws Exception {
        when(mUsageStatsManagerInternal
                .getAppStandbyBucket(eq(TEST_CALLING_PACKAGE), anyInt(), anyLong()))
                .thenReturn(STANDBY_BUCKET_RESTRICTED);
        // This one should go off
        final long firstTrigger = mNowElapsedTest + 10;
        setTestAlarm(ELAPSED_REALTIME_WAKEUP, firstTrigger, getNewMockPendingIntent());
        mNowElapsedTest = mTestTimer.getElapsed();
        mTestTimer.expire();

        // This one should get deferred on set
        setTestAlarm(ELAPSED_REALTIME_WAKEUP, firstTrigger + 1, getNewMockPendingIntent());
        final long expectedNextTrigger =
                firstTrigger + mService.mConstants.APP_STANDBY_RESTRICTED_WINDOW;
        assertEquals("Incorrect next alarm trigger", expectedNextTrigger, mTestTimer.getElapsed());
    }

    @Test
    public void testRestrictedBucketAlarmsDeferredOnExpiration() throws Exception {
        when(mUsageStatsManagerInternal
                .getAppStandbyBucket(eq(TEST_CALLING_PACKAGE), anyInt(), anyLong()))
                .thenReturn(STANDBY_BUCKET_RESTRICTED);
        // This one should go off
        final long firstTrigger = mNowElapsedTest + 10;
        setTestAlarm(ELAPSED_REALTIME_WAKEUP, firstTrigger, getNewMockPendingIntent());

        // This one should get deferred after the latest alarm expires
        setTestAlarm(ELAPSED_REALTIME_WAKEUP, firstTrigger + 1, getNewMockPendingIntent());

        mNowElapsedTest = mTestTimer.getElapsed();
        mTestTimer.expire();
        final long expectedNextTrigger =
                firstTrigger + mService.mConstants.APP_STANDBY_RESTRICTED_WINDOW;
        assertEquals("Incorrect next alarm trigger", expectedNextTrigger, mTestTimer.getElapsed());
    }

    private void assertAndHandleBucketChanged(int bucket) {
        when(mUsageStatsManagerInternal.getAppStandbyBucket(eq(TEST_CALLING_PACKAGE), anyInt(),
                anyLong())).thenReturn(bucket);
        mAppStandbyListener.onAppIdleStateChanged(TEST_CALLING_PACKAGE,
                UserHandle.getUserId(TEST_CALLING_UID), false, bucket, 0);
        assertAndHandleMessageSync(APP_STANDBY_BUCKET_CHANGED);
    }

    private void assertAndHandleMessageSync(int what) {
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mService.mHandler, atLeastOnce()).sendMessage(messageCaptor.capture());
        final Message lastMessage = messageCaptor.getValue();
        assertEquals("Unexpected message send to handler", lastMessage.what,
                what);
        mService.mHandler.handleMessage(lastMessage);
    }

    @Test
    public void testQuotaDowngrade() throws Exception {
        final int workingQuota = mService.getQuotaForBucketLocked(STANDBY_BUCKET_WORKING_SET);
        when(mUsageStatsManagerInternal.getAppStandbyBucket(eq(TEST_CALLING_PACKAGE), anyInt(),
                anyLong())).thenReturn(STANDBY_BUCKET_WORKING_SET);

        final long firstTrigger = mNowElapsedTest + 10;
        for (int i = 0; i < workingQuota; i++) {
            setTestAlarm(ELAPSED_REALTIME_WAKEUP, firstTrigger + i, getNewMockPendingIntent());
        }
        // No deferrals now.
        for (int i = 0; i < workingQuota - 1; i++) {
            mNowElapsedTest = mTestTimer.getElapsed();
            assertEquals(firstTrigger + i, mNowElapsedTest);
            mTestTimer.expire();
        }
        // The next upcoming alarm in queue should also be set as expected.
        assertEquals(firstTrigger + workingQuota - 1, mTestTimer.getElapsed());
        // Downgrading the bucket now
        assertAndHandleBucketChanged(STANDBY_BUCKET_RARE);
        final int rareQuota = mService.getQuotaForBucketLocked(STANDBY_BUCKET_RARE);
        // The last alarm should now be deferred.
        final long expectedNextTrigger = (firstTrigger + workingQuota - 1 - rareQuota)
                + mAppStandbyWindow;
        assertEquals("Incorrect next alarm trigger", expectedNextTrigger, mTestTimer.getElapsed());
    }

    @Test
    public void testQuotaUpgrade() throws Exception {
        final int frequentQuota = mService.getQuotaForBucketLocked(STANDBY_BUCKET_FREQUENT);
        when(mUsageStatsManagerInternal.getAppStandbyBucket(eq(TEST_CALLING_PACKAGE), anyInt(),
                anyLong())).thenReturn(STANDBY_BUCKET_FREQUENT);

        final long firstTrigger = mNowElapsedTest + 10;
        for (int i = 0; i < frequentQuota + 1; i++) {
            setTestAlarm(ELAPSED_REALTIME_WAKEUP, firstTrigger + i, getNewMockPendingIntent());
            if (i < frequentQuota) {
                mNowElapsedTest = mTestTimer.getElapsed();
                mTestTimer.expire();
            }
        }
        // The last alarm should be deferred due to exceeding the quota
        final long deferredTrigger = firstTrigger + mAppStandbyWindow;
        assertEquals(deferredTrigger, mTestTimer.getElapsed());

        // Upgrading the bucket now
        assertAndHandleBucketChanged(STANDBY_BUCKET_ACTIVE);
        // The last alarm should now be rescheduled to go as per original expectations
        final long originalTrigger = firstTrigger + frequentQuota;
        assertEquals("Incorrect next alarm trigger", originalTrigger, mTestTimer.getElapsed());
    }

    private void assertAndHandleParoleChanged(boolean parole) {
        mChargingReceiver.onReceive(mMockContext,
                new Intent(parole ? BatteryManager.ACTION_CHARGING
                        : BatteryManager.ACTION_DISCHARGING));
        assertAndHandleMessageSync(CHARGING_STATUS_CHANGED);
    }

    @Test
    public void testCharging() throws Exception {
        final int workingQuota = mService.getQuotaForBucketLocked(STANDBY_BUCKET_WORKING_SET);
        when(mUsageStatsManagerInternal.getAppStandbyBucket(eq(TEST_CALLING_PACKAGE), anyInt(),
                anyLong())).thenReturn(STANDBY_BUCKET_WORKING_SET);

        final long firstTrigger = mNowElapsedTest + 10;
        final int totalAlarms = workingQuota + 10;
        for (int i = 0; i < totalAlarms; i++) {
            setTestAlarm(ELAPSED_REALTIME_WAKEUP, firstTrigger + i, getNewMockPendingIntent());
        }
        // Use up the quota, no deferrals expected.
        for (int i = 0; i < workingQuota; i++) {
            mNowElapsedTest = mTestTimer.getElapsed();
            assertEquals(firstTrigger + i, mNowElapsedTest);
            mTestTimer.expire();
        }
        // Any subsequent alarms in queue should all be deferred
        assertEquals(firstTrigger + mAppStandbyWindow, mTestTimer.getElapsed());
        // Paroling now
        assertAndHandleParoleChanged(true);

        // Subsequent alarms should now go off as per original expectations.
        for (int i = 0; i < 5; i++) {
            mNowElapsedTest = mTestTimer.getElapsed();
            assertEquals(firstTrigger + workingQuota + i, mNowElapsedTest);
            mTestTimer.expire();
        }
        // Come out of parole
        assertAndHandleParoleChanged(false);

        // Subsequent alarms should again get deferred
        final long expectedNextTrigger = (firstTrigger + 5) + mAppStandbyWindow;
        assertEquals("Incorrect next alarm trigger", expectedNextTrigger, mTestTimer.getElapsed());
    }

    @Test
    public void testAlarmRestrictedInBatterySaver() throws Exception {
        final ArgumentCaptor<AppStateTrackerImpl.Listener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(AppStateTrackerImpl.Listener.class);
        verify(mAppStateTracker).addListener(listenerArgumentCaptor.capture());

        final PendingIntent alarmPi = getNewMockPendingIntent();
        when(mAppStateTracker.areAlarmsRestricted(TEST_CALLING_UID, TEST_CALLING_PACKAGE,
                false)).thenReturn(true);
        setTestAlarm(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 2, alarmPi);
        assertEquals(mNowElapsedTest + 2, mTestTimer.getElapsed());

        final SparseArray<ArrayList<Alarm>> restrictedAlarms =
                mService.mPendingBackgroundAlarms;
        assertNull(restrictedAlarms.get(TEST_CALLING_UID));

        mNowElapsedTest = mTestTimer.getElapsed();
        mTestTimer.expire();
        assertNotNull(restrictedAlarms.get(TEST_CALLING_UID));

        listenerArgumentCaptor.getValue().unblockAlarmsForUid(TEST_CALLING_UID);
        verify(alarmPi).send(eq(mMockContext), eq(0), any(Intent.class), any(),
                any(Handler.class), isNull(), any());
        assertNull(restrictedAlarms.get(TEST_CALLING_UID));
    }

    @Test
    public void alarmsRemovedOnAppStartModeDisabled() {
        final ArgumentCaptor<AppStateTrackerImpl.Listener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(AppStateTrackerImpl.Listener.class);
        verify(mAppStateTracker).addListener(listenerArgumentCaptor.capture());
        final AppStateTrackerImpl.Listener listener = listenerArgumentCaptor.getValue();

        final PendingIntent alarmPi1 = getNewMockPendingIntent();
        final PendingIntent alarmPi2 = getNewMockPendingIntent();

        setTestAlarm(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 2, alarmPi1);
        setTestAlarm(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 4, alarmPi2);

        assertEquals(2, mService.mAlarmsPerUid.get(TEST_CALLING_UID));

        when(mActivityManagerInternal.isAppStartModeDisabled(TEST_CALLING_UID,
                TEST_CALLING_PACKAGE)).thenReturn(true);
        listener.removeAlarmsForUid(TEST_CALLING_UID);
        assertEquals(0, mService.mAlarmsPerUid.get(TEST_CALLING_UID));
    }

    @Test
    public void sendsTimeTickOnInteractive() {
        final ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        // Stubbing so the handler doesn't actually run the runnable.
        doReturn(true).when(mService.mHandler).post(runnableCaptor.capture());
        // change interactive state: false -> true
        mService.interactiveStateChangedLocked(false);
        mService.interactiveStateChangedLocked(true);
        runnableCaptor.getValue().run();
        verify(mMockContext).sendBroadcastAsUser(mService.mTimeTickIntent, UserHandle.ALL);
    }

    @Test
    public void alarmCountKeyedOnCallingUid() {
        final int mockCreatorUid = 431412;
        setTestAlarm(ELAPSED_REALTIME, mNowElapsedTest + 5,
                getNewMockPendingIntent(mockCreatorUid, TEST_CALLING_PACKAGE));
        assertEquals(1, mService.mAlarmsPerUid.get(TEST_CALLING_UID));
        assertEquals(-1, mService.mAlarmsPerUid.get(mockCreatorUid, -1));
    }

    @Test
    public void alarmCountOnSetPi() {
        final int numAlarms = 103;
        final int[] types = {RTC_WAKEUP, RTC, ELAPSED_REALTIME_WAKEUP, ELAPSED_REALTIME};
        for (int i = 1; i <= numAlarms; i++) {
            setTestAlarm(types[i % 4], mNowElapsedTest + i, getNewMockPendingIntent());
            assertEquals(i, mService.mAlarmsPerUid.get(TEST_CALLING_UID));
        }
    }

    @Test
    public void alarmCountOnSetListener() {
        final int numAlarms = 103;
        final int[] types = {RTC_WAKEUP, RTC, ELAPSED_REALTIME_WAKEUP, ELAPSED_REALTIME};
        for (int i = 1; i <= numAlarms; i++) {
            setTestAlarmWithListener(types[i % 4], mNowElapsedTest + i, new IAlarmListener.Stub() {
                @Override
                public void doAlarm(IAlarmCompleteListener callback) throws RemoteException {
                }
            });
            assertEquals(i, mService.mAlarmsPerUid.get(TEST_CALLING_UID));
        }
    }

    @Test
    public void alarmCountOnExpirationPi() throws InterruptedException {
        final int numAlarms = 8; // This test is slow
        for (int i = 0; i < numAlarms; i++) {
            setTestAlarm(ELAPSED_REALTIME, mNowElapsedTest + i + 10, getNewMockPendingIntent());
        }
        int expired = 0;
        while (expired < numAlarms) {
            mNowElapsedTest = mTestTimer.getElapsed();
            mTestTimer.expire();
            expired++;
            assertEquals(numAlarms - expired, mService.mAlarmsPerUid.get(TEST_CALLING_UID, 0));
        }
    }

    @Test
    public void alarmCountOnExpirationListener() throws InterruptedException {
        final int numAlarms = 8; // This test is slow
        for (int i = 0; i < numAlarms; i++) {
            setTestAlarmWithListener(ELAPSED_REALTIME, mNowElapsedTest + i + 10,
                    new IAlarmListener.Stub() {
                        @Override
                        public void doAlarm(IAlarmCompleteListener callback)
                                throws RemoteException {
                        }
                    });
        }
        int expired = 0;
        while (expired < numAlarms) {
            mNowElapsedTest = mTestTimer.getElapsed();
            mTestTimer.expire();
            expired++;
            assertEquals(numAlarms - expired, mService.mAlarmsPerUid.get(TEST_CALLING_UID, 0));
        }
    }

    @Test
    public void alarmCountOnExceptionWhileSendingPi() throws Exception {
        final int numAlarms = 5; // This test is slow
        for (int i = 0; i < numAlarms; i++) {
            final PendingIntent pi = getNewMockPendingIntent();
            doThrow(PendingIntent.CanceledException.class).when(pi).send(eq(mMockContext), eq(0),
                    any(), any(), any(), any(), any());
            setTestAlarm(ELAPSED_REALTIME, mNowElapsedTest + i + 10, pi);
        }
        int expired = 0;
        while (expired < numAlarms) {
            mNowElapsedTest = mTestTimer.getElapsed();
            mTestTimer.expire();
            expired++;
            assertEquals(numAlarms - expired, mService.mAlarmsPerUid.get(TEST_CALLING_UID, 0));
        }
    }

    @Test
    public void alarmCountOnExceptionWhileCallingListener() throws Exception {
        final int numAlarms = 5; // This test is slow
        for (int i = 0; i < numAlarms; i++) {
            final IAlarmListener listener = new IAlarmListener.Stub() {
                @Override
                public void doAlarm(IAlarmCompleteListener callback) throws RemoteException {
                    throw new RemoteException("For testing behavior on exception");
                }
            };
            setTestAlarmWithListener(ELAPSED_REALTIME, mNowElapsedTest + i + 10, listener);
        }
        int expired = 0;
        while (expired < numAlarms) {
            mNowElapsedTest = mTestTimer.getElapsed();
            mTestTimer.expire();
            expired++;
            assertEquals(numAlarms - expired, mService.mAlarmsPerUid.get(TEST_CALLING_UID, 0));
        }
    }

    @Test
    public void alarmCountForRepeatingAlarms() throws Exception {
        final long interval = 1231;
        final long firstTrigger = mNowElapsedTest + 321;
        final PendingIntent pi = getNewMockPendingIntent();
        setRepeatingTestAlarm(ELAPSED_REALTIME, firstTrigger, interval, pi);
        assertEquals(1, mService.mAlarmsPerUid.get(TEST_CALLING_UID));

        for (int i = 0; i < 5; i++) {
            mNowElapsedTest = mTestTimer.getElapsed();
            mTestTimer.expire();
            assertEquals(1, mService.mAlarmsPerUid.get(TEST_CALLING_UID));
        }
        doThrow(PendingIntent.CanceledException.class).when(pi).send(eq(mMockContext), eq(0),
                any(), any(), any(), any(), any());
        mNowElapsedTest = mTestTimer.getElapsed();
        mTestTimer.expire();
        assertEquals(-1, mService.mAlarmsPerUid.get(TEST_CALLING_UID, -1));
    }

    @Test
    public void alarmCountOnUidRemoved() {
        final int numAlarms = 10;
        for (int i = 0; i < numAlarms; i++) {
            setTestAlarm(ELAPSED_REALTIME, mNowElapsedTest + i + 10, getNewMockPendingIntent());
        }
        assertEquals(numAlarms, mService.mAlarmsPerUid.get(TEST_CALLING_UID));
        mService.removeLocked(TEST_CALLING_UID);
        assertEquals(0, mService.mAlarmsPerUid.get(TEST_CALLING_UID, 0));
    }

    @Test
    public void alarmCountOnPackageRemoved() {
        final int numAlarms = 10;
        for (int i = 0; i < numAlarms; i++) {
            setTestAlarm(ELAPSED_REALTIME, mNowElapsedTest + i + 10, getNewMockPendingIntent());
        }
        assertEquals(numAlarms, mService.mAlarmsPerUid.get(TEST_CALLING_UID));
        mService.removeLocked(TEST_CALLING_PACKAGE);
        assertEquals(0, mService.mAlarmsPerUid.get(TEST_CALLING_UID, 0));
    }

    @Test
    public void alarmCountOnUserRemoved() {
        final int mockUserId = 15;
        final int numAlarms = 10;
        for (int i = 0; i < numAlarms; i++) {
            int mockUid = UserHandle.getUid(mockUserId, 1234 + i);
            setTestAlarm(ELAPSED_REALTIME, mNowElapsedTest + i + 10,
                    getNewMockPendingIntent(mockUid, TEST_CALLING_PACKAGE), 0, FLAG_STANDALONE,
                    mockUid);
        }
        assertEquals(numAlarms, mService.mAlarmsPerUid.size());
        mService.removeUserLocked(mockUserId);
        assertEquals(0, mService.mAlarmsPerUid.size());
    }

    @Test
    public void alarmCountOnAlarmRemoved() {
        final int numAlarms = 10;
        final PendingIntent[] pis = new PendingIntent[numAlarms];
        for (int i = 0; i < numAlarms; i++) {
            pis[i] = getNewMockPendingIntent();
            setTestAlarm(ELAPSED_REALTIME, mNowElapsedTest + i + 5, pis[i]);
        }
        assertEquals(numAlarms, mService.mAlarmsPerUid.get(TEST_CALLING_UID));
        for (int i = 0; i < numAlarms; i++) {
            mService.removeLocked(pis[i], null);
            assertEquals(numAlarms - i - 1, mService.mAlarmsPerUid.get(TEST_CALLING_UID, 0));
        }
    }

    @Test
    public void alarmTypes() throws Exception {
        final int[] typesToSet = {ELAPSED_REALTIME_WAKEUP, ELAPSED_REALTIME, RTC_WAKEUP, RTC};
        final int[] typesExpected = {ELAPSED_REALTIME_WAKEUP, ELAPSED_REALTIME,
                ELAPSED_REALTIME_WAKEUP, ELAPSED_REALTIME};
        assertAlarmTypeConversion(typesToSet, typesExpected);
    }

    private void assertAlarmTypeConversion(int[] typesToSet, int[] typesExpected) throws Exception {
        for (int i = 0; i < typesToSet.length; i++) {
            setTestAlarm(typesToSet[i], 1234, getNewMockPendingIntent());
            final int typeSet = mTestTimer.getType();
            assertEquals("Alarm of type " + typesToSet[i] + " was set to type " + typeSet,
                    typesExpected[i], typeSet);
            mNowElapsedTest = mTestTimer.getElapsed();
            mTestTimer.expire();
        }
    }

    @Test
    public void alarmCountOnInvalidSet() {
        setTestAlarm(ELAPSED_REALTIME, mNowElapsedTest + 12345, null);
        assertEquals(-1, mService.mAlarmsPerUid.get(TEST_CALLING_UID, -1));
    }

    @Test
    public void alarmCountOnRemoveForCanceled() {
        final AlarmManagerInternal ami = LocalServices.getService(AlarmManagerInternal.class);
        final PendingIntent pi = getNewMockPendingIntent();
        setTestAlarm(ELAPSED_REALTIME, mNowElapsedTest + 12345, pi);
        assertEquals(1, mService.mAlarmsPerUid.get(TEST_CALLING_UID));
        ami.remove(pi);
        assertAndHandleMessageSync(REMOVE_FOR_CANCELED);
        assertEquals(0, mService.mAlarmsPerUid.get(TEST_CALLING_UID));
    }

    @Test
    public void alarmCountOnListenerBinderDied() {
        final int numAlarms = 10;
        final IAlarmListener[] listeners = new IAlarmListener[numAlarms];
        for (int i = 0; i < numAlarms; i++) {
            listeners[i] = new IAlarmListener.Stub() {
                @Override
                public void doAlarm(IAlarmCompleteListener callback) throws RemoteException {
                }
            };
            setTestAlarmWithListener(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + i, listeners[i]);
        }
        assertEquals(numAlarms, mService.mAlarmsPerUid.get(TEST_CALLING_UID));
        for (int i = 0; i < numAlarms; i++) {
            mService.mListenerDeathRecipient.binderDied(listeners[i].asBinder());
            assertEquals(numAlarms - i - 1, mService.mAlarmsPerUid.get(TEST_CALLING_UID));
        }
    }

    @Test
    public void nonWakeupAlarmsDeferred() throws Exception {
        final int numAlarms = 10;
        final PendingIntent[] pis = new PendingIntent[numAlarms];
        for (int i = 0; i < numAlarms; i++) {
            pis[i] = getNewMockPendingIntent();
            setTestAlarm(ELAPSED_REALTIME, mNowElapsedTest + i + 5, pis[i]);
        }
        doReturn(true).when(mService).checkAllowNonWakeupDelayLocked(anyLong());
        // Advance time past all expirations.
        mNowElapsedTest += numAlarms + 5;
        mTestTimer.expire();
        assertEquals(numAlarms, mService.mPendingNonWakeupAlarms.size());

        // These alarms should be sent on interactive state change to true
        mService.interactiveStateChangedLocked(false);
        mService.interactiveStateChangedLocked(true);

        for (int i = 0; i < numAlarms; i++) {
            verify(pis[i]).send(eq(mMockContext), eq(0), any(Intent.class), any(),
                    any(Handler.class), isNull(), any());
        }
    }

    @Test
    public void alarmCountOnPendingNonWakeupAlarmsRemoved() throws Exception {
        final int numAlarms = 10;
        final PendingIntent[] pis = new PendingIntent[numAlarms];
        for (int i = 0; i < numAlarms; i++) {
            pis[i] = getNewMockPendingIntent();
            setTestAlarm(ELAPSED_REALTIME, mNowElapsedTest + i + 5, pis[i]);
        }
        doReturn(true).when(mService).checkAllowNonWakeupDelayLocked(anyLong());
        // Advance time past all expirations.
        mNowElapsedTest += numAlarms + 5;
        mTestTimer.expire();
        assertEquals(numAlarms, mService.mPendingNonWakeupAlarms.size());
        for (int i = 0; i < numAlarms; i++) {
            mService.removeLocked(pis[i], null);
            assertEquals(numAlarms - i - 1, mService.mAlarmsPerUid.get(TEST_CALLING_UID, 0));
        }
    }

    @Test
    public void singleIdleUntil() {
        doReturn(0).when(mService).fuzzForDuration(anyLong());

        final PendingIntent idleUntilPi6 = getNewMockPendingIntent();
        setIdleUntilAlarm(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 6, idleUntilPi6);

        assertTrue(mService.mPendingIdleUntil.matches(idleUntilPi6, null));
        assertEquals(mNowElapsedTest + 6, mTestTimer.getElapsed());
        assertEquals(mNowElapsedTest + 6, mService.mPendingIdleUntil.getWhenElapsed());

        final PendingIntent idleUntilPi2 = getNewMockPendingIntent();
        setIdleUntilAlarm(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 2, idleUntilPi2);

        // The same mPendingIdleUntil should get updated, even with a different PendingIntent.
        assertTrue(mService.mPendingIdleUntil.matches(idleUntilPi2, null));
        assertEquals(mNowElapsedTest + 2, mTestTimer.getElapsed());
        assertEquals(1, mService.mAlarmStore.size());

        final PendingIntent idleUntilPi10 = getNewMockPendingIntent();
        setIdleUntilAlarm(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 10, idleUntilPi10);

        // The same thing should happen even when the new alarm is in farther in the future.
        assertTrue(mService.mPendingIdleUntil.matches(idleUntilPi10, null));
        assertEquals(mNowElapsedTest + 10, mTestTimer.getElapsed());
        assertEquals(1, mService.mAlarmStore.size());
    }

    @Test
    public void nextWakeFromIdle() throws Exception {
        assertNull(mService.mNextWakeFromIdle);

        final PendingIntent wakeFromIdle6 = getNewMockPendingIntent();
        final long trigger6 = mNowElapsedTest + 6;
        setWakeFromIdle(ELAPSED_REALTIME_WAKEUP, trigger6, wakeFromIdle6);

        assertTrue(mService.mNextWakeFromIdle.matches(wakeFromIdle6, null));
        assertEquals(trigger6, mService.mNextWakeFromIdle.getWhenElapsed());
        assertEquals(trigger6, mTestTimer.getElapsed());

        final PendingIntent wakeFromIdle10 = getNewMockPendingIntent();
        final long trigger10 = mNowElapsedTest + 10;
        setWakeFromIdle(ELAPSED_REALTIME_WAKEUP, trigger10, wakeFromIdle10);

        // mNextWakeFromIdle should not get updated.
        assertTrue(mService.mNextWakeFromIdle.matches(wakeFromIdle6, null));
        assertEquals(trigger6, mTestTimer.getElapsed());
        assertEquals(trigger6, mService.mNextWakeFromIdle.getWhenElapsed());

        final PendingIntent wakeFromIdle3 = getNewMockPendingIntent();
        final long trigger3 = mNowElapsedTest + 3;
        setWakeFromIdle(ELAPSED_REALTIME_WAKEUP, trigger3, wakeFromIdle3);

        // mNextWakeFromIdle should always reflect the next earliest wake_from_idle alarm.
        assertTrue(mService.mNextWakeFromIdle.matches(wakeFromIdle3, null));
        assertEquals(trigger3, mTestTimer.getElapsed());
        assertEquals(trigger3, mService.mNextWakeFromIdle.getWhenElapsed());

        mNowElapsedTest = trigger3;
        mTestTimer.expire();

        assertTrue(mService.mNextWakeFromIdle.matches(wakeFromIdle6, null));
        assertEquals(trigger6, mTestTimer.getElapsed());
        assertEquals(trigger6, mService.mNextWakeFromIdle.getWhenElapsed());

        mService.removeLocked(wakeFromIdle6, null);

        assertTrue(mService.mNextWakeFromIdle.matches(wakeFromIdle10, null));
        assertEquals(trigger10, mTestTimer.getElapsed());
        assertEquals(trigger10, mService.mNextWakeFromIdle.getWhenElapsed());

        mService.removeLocked(wakeFromIdle10, null);
        assertNull(mService.mNextWakeFromIdle);
    }

    @Test
    public void idleUntilBeforeWakeFromIdle() {
        doReturn(0).when(mService).fuzzForDuration(anyLong());

        final PendingIntent idleUntilPi = getNewMockPendingIntent();
        final long requestedIdleUntil = mNowElapsedTest + 10;
        setIdleUntilAlarm(ELAPSED_REALTIME_WAKEUP, requestedIdleUntil, idleUntilPi);

        assertEquals(requestedIdleUntil, mService.mPendingIdleUntil.getWhenElapsed());

        final PendingIntent wakeFromIdle5 = getNewMockPendingIntent();
        setWakeFromIdle(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 5, wakeFromIdle5);
        assertEquals(mNowElapsedTest + 5, mService.mPendingIdleUntil.getWhenElapsed());

        final PendingIntent wakeFromIdle8 = getNewMockPendingIntent();
        setWakeFromIdle(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 8, wakeFromIdle8);
        assertEquals(mNowElapsedTest + 5, mService.mPendingIdleUntil.getWhenElapsed());

        final PendingIntent wakeFromIdle12 = getNewMockPendingIntent();
        setWakeFromIdle(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 12, wakeFromIdle12);
        assertEquals(mNowElapsedTest + 5, mService.mPendingIdleUntil.getWhenElapsed());

        mService.removeLocked(wakeFromIdle5, null);
        assertEquals(mNowElapsedTest + 8, mService.mPendingIdleUntil.getWhenElapsed());

        mService.removeLocked(wakeFromIdle8, null);
        assertEquals(requestedIdleUntil, mService.mPendingIdleUntil.getWhenElapsed());

        mService.removeLocked(idleUntilPi, null);
        assertNull(mService.mPendingIdleUntil);

        setIdleUntilAlarm(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 15, idleUntilPi);
        assertEquals(mNowElapsedTest + 12, mService.mPendingIdleUntil.getWhenElapsed());
    }

    @Test
    public void allowWhileIdleAlarmsWhileDeviceIdle() throws Exception {
        doReturn(0).when(mService).fuzzForDuration(anyLong());

        final long awiDelayForTest = 23;
        setDeviceConfigLong(KEY_ALLOW_WHILE_IDLE_LONG_TIME, awiDelayForTest);
        setDeviceConfigLong(KEY_ALLOW_WHILE_IDLE_SHORT_TIME, 0);

        setIdleUntilAlarm(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 1000,
                getNewMockPendingIntent());
        assertNotNull(mService.mPendingIdleUntil);

        final long seedTrigger = mNowElapsedTest + 3;
        final int numAlarms = 10;
        final PendingIntent[] pis = new PendingIntent[numAlarms];
        for (int i = 0; i < numAlarms; i++) {
            pis[i] = getNewMockPendingIntent();
            setAllowWhileIdleAlarm(ELAPSED_REALTIME_WAKEUP, seedTrigger + i * i, pis[i], false);
        }

        long lastAwiDispatch = -1;
        int i = 0;
        while (i < numAlarms) {
            final long nextDispatch = (lastAwiDispatch >= 0) ? (lastAwiDispatch + awiDelayForTest)
                    : (seedTrigger + i * i);
            assertEquals("Wrong allow-while-idle dispatch", nextDispatch, mTestTimer.getElapsed());

            mNowElapsedTest = nextDispatch;
            mTestTimer.expire();

            while (i < numAlarms && (seedTrigger + i * i) <= nextDispatch) {
                verify(pis[i]).send(eq(mMockContext), eq(0), any(Intent.class), any(),
                        any(Handler.class), isNull(), any());
                i++;
            }
            Log.d(TAG, "Dispatched alarms upto " + i + " at " + nextDispatch);
            lastAwiDispatch = nextDispatch;
        }
    }

    @Test
    public void allowWhileIdleUnrestricted() throws Exception {
        doReturn(0).when(mService).fuzzForDuration(anyLong());

        final long awiDelayForTest = 127;
        setDeviceConfigLong(KEY_ALLOW_WHILE_IDLE_LONG_TIME, awiDelayForTest);
        setDeviceConfigLong(KEY_ALLOW_WHILE_IDLE_SHORT_TIME, 0);

        setIdleUntilAlarm(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 1000,
                getNewMockPendingIntent());
        assertNotNull(mService.mPendingIdleUntil);

        final long seedTrigger = mNowElapsedTest + 3;
        for (int i = 1; i <= 5; i++) {
            setAllowWhileIdleAlarm(ELAPSED_REALTIME_WAKEUP, seedTrigger + i * i,
                    getNewMockPendingIntent(), true);
        }
        for (int i = 1; i <= 5; i++) {
            final long nextTrigger = mTestTimer.getElapsed();
            assertEquals("Wrong trigger for alarm " + i, seedTrigger + i * i, nextTrigger);
            mNowElapsedTest = nextTrigger;
            mTestTimer.expire();
        }
    }

    @Test
    public void deviceIdleThrottling() throws Exception {
        doReturn(0).when(mService).fuzzForDuration(anyLong());

        final long deviceIdleUntil = mNowElapsedTest + 1234;
        setIdleUntilAlarm(ELAPSED_REALTIME_WAKEUP, deviceIdleUntil, getNewMockPendingIntent());

        assertEquals(deviceIdleUntil, mTestTimer.getElapsed());

        final int numAlarms = 10;
        final PendingIntent[] pis = new PendingIntent[numAlarms];
        for (int i = 0; i < numAlarms; i++) {
            setTestAlarm(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + i + 1,
                    pis[i] = getNewMockPendingIntent());
            assertEquals(deviceIdleUntil, mTestTimer.getElapsed());
        }

        mNowElapsedTest = mTestTimer.getElapsed();
        mTestTimer.expire();
        for (int i = 0; i < numAlarms; i++) {
            verify(pis[i]).send(eq(mMockContext), eq(0), any(Intent.class), any(),
                    any(Handler.class), isNull(), any());
        }
    }

    @Test
    public void dispatchOrder() throws Exception {
        doReturn(0).when(mService).fuzzForDuration(anyLong());

        final long deviceIdleUntil = mNowElapsedTest + 1234;
        final PendingIntent idleUntilPi = getNewMockPendingIntent();
        setIdleUntilAlarm(ELAPSED_REALTIME_WAKEUP, deviceIdleUntil, idleUntilPi);

        assertEquals(deviceIdleUntil, mTestTimer.getElapsed());

        final PendingIntent pi5wakeup = getNewMockPendingIntent();
        final PendingIntent pi4wakeupPackage = getNewMockPendingIntent();
        final PendingIntent pi2nonWakeup = getNewMockPendingIntent(57, "test.different.package");

        setTestAlarm(ELAPSED_REALTIME_WAKEUP, mNowElapsedTest + 5, pi5wakeup);
        setTestAlarm(ELAPSED_REALTIME, mNowElapsedTest + 4, pi4wakeupPackage);
        setTestAlarm(ELAPSED_REALTIME, mNowElapsedTest + 2, pi2nonWakeup);

        mNowElapsedTest = deviceIdleUntil;
        mTestTimer.expire();

        // The order of the alarms in delivery list should be:
        // IdleUntil, all alarms of a package with any wakeup alarms, then the rest.
        // Within a package, alarms should be ordered by requested delivery time.
        final PendingIntent[] expectedOrder = new PendingIntent[]{
                idleUntilPi, pi4wakeupPackage, pi5wakeup, pi2nonWakeup};

        ArgumentCaptor<ArrayList<Alarm>> listCaptor = ArgumentCaptor.forClass(ArrayList.class);
        verify(mService).deliverAlarmsLocked(listCaptor.capture(), anyLong());
        final ArrayList<Alarm> deliveryList = listCaptor.getValue();

        assertEquals(expectedOrder.length, deliveryList.size());
        for (int i = 0; i < expectedOrder.length; i++) {
            assertTrue("Unexpected alarm: " + deliveryList.get(i) + " at pos: " + i,
                    deliveryList.get(i).matches(expectedOrder[i], null));
        }
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
        LocalServices.removeServiceForTest(AlarmManagerInternal.class);
    }
}
