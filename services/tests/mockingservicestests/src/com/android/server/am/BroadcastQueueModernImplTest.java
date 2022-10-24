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

import static com.android.server.am.BroadcastProcessQueue.insertIntoRunnableList;
import static com.android.server.am.BroadcastProcessQueue.removeFromRunnableList;
import static com.android.server.am.BroadcastQueueTest.CLASS_GREEN;
import static com.android.server.am.BroadcastQueueTest.PACKAGE_BLUE;
import static com.android.server.am.BroadcastQueueTest.PACKAGE_GREEN;
import static com.android.server.am.BroadcastQueueTest.PACKAGE_RED;
import static com.android.server.am.BroadcastQueueTest.PACKAGE_YELLOW;
import static com.android.server.am.BroadcastQueueTest.getUidForPackage;
import static com.android.server.am.BroadcastQueueTest.makeManifestReceiver;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;

import android.annotation.NonNull;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.BroadcastOptions;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.BundleMerger;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class BroadcastQueueModernImplTest {
    private static final int TEST_UID = android.os.Process.FIRST_APPLICATION_UID;
    private static final int TEST_UID2 = android.os.Process.FIRST_APPLICATION_UID + 1;

    @Mock ActivityManagerService mAms;
    @Mock ProcessRecord mProcess;

    @Mock BroadcastProcessQueue mQueue1;
    @Mock BroadcastProcessQueue mQueue2;
    @Mock BroadcastProcessQueue mQueue3;
    @Mock BroadcastProcessQueue mQueue4;

    HandlerThread mHandlerThread;

    BroadcastConstants mConstants;
    BroadcastQueueModernImpl mImpl;

    BroadcastProcessQueue mHead;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mHandlerThread = new HandlerThread(getClass().getSimpleName());
        mHandlerThread.start();

        mConstants = new BroadcastConstants(Settings.Global.BROADCAST_FG_CONSTANTS);
        mImpl = new BroadcastQueueModernImpl(mAms, mHandlerThread.getThreadHandler(),
                mConstants, mConstants);

        doReturn(1L).when(mQueue1).getRunnableAt();
        doReturn(2L).when(mQueue2).getRunnableAt();
        doReturn(3L).when(mQueue3).getRunnableAt();
        doReturn(4L).when(mQueue4).getRunnableAt();
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quit();
    }

    private static void assertOrphan(BroadcastProcessQueue queue) {
        assertNull(queue.runnableAtNext);
        assertNull(queue.runnableAtPrev);
    }

    private static void assertRunnableList(@NonNull List<BroadcastProcessQueue> expected,
            @NonNull BroadcastProcessQueue actualHead) {
        BroadcastProcessQueue test = actualHead;
        final int N = expected.size();
        for (int i = 0; i < N; i++) {
            final BroadcastProcessQueue expectedPrev = (i > 0) ? expected.get(i - 1) : null;
            final BroadcastProcessQueue expectedTest = expected.get(i);
            final BroadcastProcessQueue expectedNext = (i < N - 1) ? expected.get(i + 1) : null;

            assertEquals("prev", expectedPrev, test.runnableAtPrev);
            assertEquals("test", expectedTest, test);
            assertEquals("next", expectedNext, test.runnableAtNext);

            test = test.runnableAtNext;
        }
        if (N == 0) {
            assertNull(actualHead);
        }
    }

    private BroadcastRecord makeBroadcastRecord(Intent intent) {
        return makeBroadcastRecord(intent, BroadcastOptions.makeBasic(),
                List.of(makeManifestReceiver(PACKAGE_GREEN, CLASS_GREEN)), false);
    }

    private BroadcastRecord makeOrderedBroadcastRecord(Intent intent) {
        return makeBroadcastRecord(intent, BroadcastOptions.makeBasic(),
                List.of(makeManifestReceiver(PACKAGE_GREEN, CLASS_GREEN)), true);
    }

    private BroadcastRecord makeBroadcastRecord(Intent intent, BroadcastOptions options) {
        return makeBroadcastRecord(intent, options,
                List.of(makeManifestReceiver(PACKAGE_GREEN, CLASS_GREEN)), false);
    }

    private BroadcastRecord makeBroadcastRecord(Intent intent, BroadcastOptions options,
            List receivers, boolean ordered) {
        return new BroadcastRecord(mImpl, intent, mProcess, PACKAGE_RED, null, 21, 42, false, null,
                null, null, null, AppOpsManager.OP_NONE, options, receivers, null, null,
                Activity.RESULT_OK, null, null, ordered, false, false, UserHandle.USER_SYSTEM,
                false, null, false, null);
    }

    @Test
    public void testRunnableList_Simple() {
        assertRunnableList(List.of(), mHead);

        mHead = insertIntoRunnableList(mHead, mQueue1);
        assertRunnableList(List.of(mQueue1), mHead);

        mHead = removeFromRunnableList(mHead, mQueue1);
        assertRunnableList(List.of(), mHead);
    }

    @Test
    public void testRunnableList_InsertLast() {
        mHead = insertIntoRunnableList(mHead, mQueue1);
        mHead = insertIntoRunnableList(mHead, mQueue2);
        mHead = insertIntoRunnableList(mHead, mQueue3);
        mHead = insertIntoRunnableList(mHead, mQueue4);
        assertRunnableList(List.of(mQueue1, mQueue2, mQueue3, mQueue4), mHead);
    }

    @Test
    public void testRunnableList_InsertFirst() {
        mHead = insertIntoRunnableList(mHead, mQueue4);
        mHead = insertIntoRunnableList(mHead, mQueue3);
        mHead = insertIntoRunnableList(mHead, mQueue2);
        mHead = insertIntoRunnableList(mHead, mQueue1);
        assertRunnableList(List.of(mQueue1, mQueue2, mQueue3, mQueue4), mHead);
    }

    @Test
    public void testRunnableList_InsertMiddle() {
        mHead = insertIntoRunnableList(mHead, mQueue1);
        mHead = insertIntoRunnableList(mHead, mQueue3);
        mHead = insertIntoRunnableList(mHead, mQueue2);
        assertRunnableList(List.of(mQueue1, mQueue2, mQueue3), mHead);
    }

    @Test
    public void testRunnableList_Remove() {
        mHead = insertIntoRunnableList(mHead, mQueue1);
        mHead = insertIntoRunnableList(mHead, mQueue2);
        mHead = insertIntoRunnableList(mHead, mQueue3);
        mHead = insertIntoRunnableList(mHead, mQueue4);

        mHead = removeFromRunnableList(mHead, mQueue3);
        assertRunnableList(List.of(mQueue1, mQueue2, mQueue4), mHead);

        mHead = removeFromRunnableList(mHead, mQueue1);
        assertRunnableList(List.of(mQueue2, mQueue4), mHead);

        mHead = removeFromRunnableList(mHead, mQueue4);
        assertRunnableList(List.of(mQueue2), mHead);

        mHead = removeFromRunnableList(mHead, mQueue2);
        assertRunnableList(List.of(), mHead);

        // Verify all links cleaned up during removal
        assertOrphan(mQueue1);
        assertOrphan(mQueue2);
        assertOrphan(mQueue3);
        assertOrphan(mQueue4);
    }

    @Test
    public void testProcessQueue_Complex() {
        BroadcastProcessQueue red = mImpl.getOrCreateProcessQueue(PACKAGE_RED, TEST_UID);
        BroadcastProcessQueue green = mImpl.getOrCreateProcessQueue(PACKAGE_GREEN, TEST_UID);
        BroadcastProcessQueue blue = mImpl.getOrCreateProcessQueue(PACKAGE_BLUE, TEST_UID);

        assertEquals(PACKAGE_RED, red.processName);
        assertEquals(PACKAGE_GREEN, green.processName);
        assertEquals(PACKAGE_BLUE, blue.processName);

        // Verify that removing middle queue works
        mImpl.removeProcessQueue(PACKAGE_GREEN, TEST_UID);
        assertEquals(red, mImpl.getProcessQueue(PACKAGE_RED, TEST_UID));
        assertNull(mImpl.getProcessQueue(PACKAGE_GREEN, TEST_UID));
        assertEquals(blue, mImpl.getProcessQueue(PACKAGE_BLUE, TEST_UID));
        assertNull(mImpl.getProcessQueue(PACKAGE_YELLOW, TEST_UID));

        // Verify that removing head queue works
        mImpl.removeProcessQueue(PACKAGE_RED, TEST_UID);
        assertNull(mImpl.getProcessQueue(PACKAGE_RED, TEST_UID));
        assertNull(mImpl.getProcessQueue(PACKAGE_GREEN, TEST_UID));
        assertEquals(blue, mImpl.getProcessQueue(PACKAGE_BLUE, TEST_UID));
        assertNull(mImpl.getProcessQueue(PACKAGE_YELLOW, TEST_UID));

        // Verify that removing last queue works
        mImpl.removeProcessQueue(PACKAGE_BLUE, TEST_UID);
        assertNull(mImpl.getProcessQueue(PACKAGE_RED, TEST_UID));
        assertNull(mImpl.getProcessQueue(PACKAGE_GREEN, TEST_UID));
        assertNull(mImpl.getProcessQueue(PACKAGE_BLUE, TEST_UID));
        assertNull(mImpl.getProcessQueue(PACKAGE_YELLOW, TEST_UID));

        // Verify that removing missing doesn't crash
        mImpl.removeProcessQueue(PACKAGE_YELLOW, TEST_UID);

        // Verify that we can start all over again safely
        BroadcastProcessQueue yellow = mImpl.getOrCreateProcessQueue(PACKAGE_YELLOW, TEST_UID);
        assertEquals(yellow, mImpl.getProcessQueue(PACKAGE_YELLOW, TEST_UID));
    }

    /**
     * Empty queue isn't runnable.
     */
    @Test
    public void testRunnableAt_Empty() {
        final BroadcastProcessQueue queue = new BroadcastProcessQueue(mConstants,
                PACKAGE_GREEN, getUidForPackage(PACKAGE_GREEN));
        assertFalse(queue.isRunnable());
        assertEquals(Long.MAX_VALUE, queue.getRunnableAt());
        assertEquals(ProcessList.SCHED_GROUP_UNDEFINED, queue.getPreferredSchedulingGroupLocked());
    }

    /**
     * Queue with a "normal" broadcast is runnable at different times depending
     * on process cached state; when cached it's delayed by some amount.
     */
    @Test
    public void testRunnableAt_Normal() {
        final BroadcastProcessQueue queue = new BroadcastProcessQueue(mConstants,
                PACKAGE_GREEN, getUidForPackage(PACKAGE_GREEN));

        final Intent airplane = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        final BroadcastRecord airplaneRecord = makeBroadcastRecord(airplane);
        queue.enqueueOrReplaceBroadcast(airplaneRecord, 0, 0);

        queue.setProcessCached(false);
        final long notCachedRunnableAt = queue.getRunnableAt();
        queue.setProcessCached(true);
        final long cachedRunnableAt = queue.getRunnableAt();
        assertThat(cachedRunnableAt).isGreaterThan(notCachedRunnableAt);
        assertEquals(ProcessList.SCHED_GROUP_BACKGROUND, queue.getPreferredSchedulingGroupLocked());
    }

    /**
     * Queue with foreground broadcast is always runnable immediately,
     * regardless of process cached state.
     */
    @Test
    public void testRunnableAt_Foreground() {
        final BroadcastProcessQueue queue = new BroadcastProcessQueue(mConstants,
                PACKAGE_GREEN, getUidForPackage(PACKAGE_GREEN));

        // enqueue a bg-priority broadcast then a fg-priority one
        final Intent timezone = new Intent(Intent.ACTION_TIMEZONE_CHANGED);
        final BroadcastRecord timezoneRecord = makeBroadcastRecord(timezone);
        queue.enqueueOrReplaceBroadcast(timezoneRecord, 0, 0);

        final Intent airplane = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        airplane.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        final BroadcastRecord airplaneRecord = makeBroadcastRecord(airplane);
        queue.enqueueOrReplaceBroadcast(airplaneRecord, 0, 0);

        // verify that:
        // (a) the queue is immediately runnable by existence of a fg-priority broadcast
        // (b) the next one up is the fg-priority broadcast despite its later enqueue time
        queue.setProcessCached(false);
        assertTrue(queue.isRunnable());
        assertThat(queue.getRunnableAt()).isAtMost(airplaneRecord.enqueueClockTime);
        assertEquals(ProcessList.SCHED_GROUP_DEFAULT, queue.getPreferredSchedulingGroupLocked());
        assertEquals(queue.peekNextBroadcastRecord(), airplaneRecord);

        queue.setProcessCached(true);
        assertTrue(queue.isRunnable());
        assertThat(queue.getRunnableAt()).isAtMost(airplaneRecord.enqueueClockTime);
        assertEquals(ProcessList.SCHED_GROUP_DEFAULT, queue.getPreferredSchedulingGroupLocked());
        assertEquals(queue.peekNextBroadcastRecord(), airplaneRecord);
    }

    /**
     * Queue with ordered broadcast is runnable only once we've made enough
     * progress on earlier blocking items.
     */
    @Test
    public void testRunnableAt_Ordered() {
        final BroadcastProcessQueue queue = new BroadcastProcessQueue(mConstants,
                PACKAGE_GREEN, getUidForPackage(PACKAGE_GREEN));

        final Intent airplane = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        final BroadcastRecord airplaneRecord = makeBroadcastRecord(airplane, null,
                List.of(makeManifestReceiver(PACKAGE_GREEN, CLASS_GREEN),
                        makeManifestReceiver(PACKAGE_GREEN, CLASS_GREEN)), true);
        queue.enqueueOrReplaceBroadcast(airplaneRecord, 1, 1);

        assertFalse(queue.isRunnable());
        assertEquals(BroadcastProcessQueue.REASON_BLOCKED, queue.getRunnableAtReason());

        // Bumping past barrier makes us now runnable
        airplaneRecord.terminalCount++;
        queue.invalidateRunnableAt();
        assertTrue(queue.isRunnable());
        assertNotEquals(BroadcastProcessQueue.REASON_BLOCKED, queue.getRunnableAtReason());
    }

    /**
     * Queue with too many pending broadcasts is runnable.
     */
    @Test
    public void testRunnableAt_Huge() {
        BroadcastProcessQueue queue = new BroadcastProcessQueue(mConstants,
                PACKAGE_GREEN, getUidForPackage(PACKAGE_GREEN));

        final Intent airplane = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        final BroadcastRecord airplaneRecord = makeBroadcastRecord(airplane);
        queue.enqueueOrReplaceBroadcast(airplaneRecord, 0, 0);

        mConstants.MAX_PENDING_BROADCASTS = 128;
        queue.invalidateRunnableAt();
        assertThat(queue.getRunnableAt()).isGreaterThan(airplaneRecord.enqueueTime);
        assertEquals(BroadcastProcessQueue.REASON_NORMAL, queue.getRunnableAtReason());

        mConstants.MAX_PENDING_BROADCASTS = 1;
        queue.invalidateRunnableAt();
        assertThat(queue.getRunnableAt()).isAtMost(airplaneRecord.enqueueTime);
        assertEquals(BroadcastProcessQueue.REASON_MAX_PENDING, queue.getRunnableAtReason());
    }

    /**
     * Verify that sending a broadcast that removes any matching pending
     * broadcasts is applied as expected.
     */
    @Test
    public void testRemoveMatchingFilter() {
        final Intent screenOn = new Intent(Intent.ACTION_SCREEN_ON);
        final BroadcastOptions optionsOn = BroadcastOptions.makeBasic();
        optionsOn.setRemoveMatchingFilter(new IntentFilter(Intent.ACTION_SCREEN_OFF));

        final Intent screenOff = new Intent(Intent.ACTION_SCREEN_OFF);
        final BroadcastOptions optionsOff = BroadcastOptions.makeBasic();
        optionsOff.setRemoveMatchingFilter(new IntentFilter(Intent.ACTION_SCREEN_ON));

        // Halt all processing so that we get a consistent view
        mHandlerThread.getLooper().getQueue().postSyncBarrier();

        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(screenOn, optionsOn));
        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(screenOff, optionsOff));
        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(screenOn, optionsOn));
        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(screenOff, optionsOff));

        // While we're here, give our health check some test coverage
        mImpl.checkHealthLocked();

        // Marching through the queue we should only have one SCREEN_OFF
        // broadcast, since that's the last state we dispatched
        final BroadcastProcessQueue queue = mImpl.getProcessQueue(PACKAGE_GREEN,
                getUidForPackage(PACKAGE_GREEN));
        queue.makeActiveNextPending();
        assertEquals(Intent.ACTION_SCREEN_OFF, queue.getActive().intent.getAction());
        assertTrue(queue.isEmpty());
    }

    /**
     * Verify that sending a broadcast with DELIVERY_GROUP_POLICY_MOST_RECENT works as expected.
     */
    @Test
    public void testDeliveryGroupPolicy_mostRecent() {
        final Intent timeTick = new Intent(Intent.ACTION_TIME_TICK);
        final BroadcastOptions optionsTimeTick = BroadcastOptions.makeBasic();
        optionsTimeTick.setDeliveryGroupPolicy(BroadcastOptions.DELIVERY_GROUP_POLICY_MOST_RECENT);

        final Intent musicVolumeChanged = new Intent(AudioManager.VOLUME_CHANGED_ACTION);
        musicVolumeChanged.putExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE,
                AudioManager.STREAM_MUSIC);
        final BroadcastOptions optionsMusicVolumeChanged = BroadcastOptions.makeBasic();
        optionsMusicVolumeChanged.setDeliveryGroupPolicy(
                BroadcastOptions.DELIVERY_GROUP_POLICY_MOST_RECENT);
        optionsMusicVolumeChanged.setDeliveryGroupKey("audio",
                String.valueOf(AudioManager.STREAM_MUSIC));

        final Intent alarmVolumeChanged = new Intent(AudioManager.VOLUME_CHANGED_ACTION);
        alarmVolumeChanged.putExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE,
                AudioManager.STREAM_ALARM);
        final BroadcastOptions optionsAlarmVolumeChanged = BroadcastOptions.makeBasic();
        optionsAlarmVolumeChanged.setDeliveryGroupPolicy(
                BroadcastOptions.DELIVERY_GROUP_POLICY_MOST_RECENT);
        optionsAlarmVolumeChanged.setDeliveryGroupKey("audio",
                String.valueOf(AudioManager.STREAM_ALARM));

        // Halt all processing so that we get a consistent view
        mHandlerThread.getLooper().getQueue().postSyncBarrier();

        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(timeTick, optionsTimeTick));
        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(musicVolumeChanged,
                optionsMusicVolumeChanged));
        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(alarmVolumeChanged,
                optionsAlarmVolumeChanged));
        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(musicVolumeChanged,
                optionsMusicVolumeChanged));

        final BroadcastProcessQueue queue = mImpl.getProcessQueue(PACKAGE_GREEN,
                getUidForPackage(PACKAGE_GREEN));
        // Verify that the older musicVolumeChanged has been removed.
        verifyPendingRecords(queue,
                List.of(timeTick, alarmVolumeChanged, musicVolumeChanged));

        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(timeTick, optionsTimeTick));
        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(alarmVolumeChanged,
                optionsAlarmVolumeChanged));
        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(musicVolumeChanged,
                optionsMusicVolumeChanged));
        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(alarmVolumeChanged,
                optionsAlarmVolumeChanged));
        // Verify that the older alarmVolumeChanged has been removed.
        verifyPendingRecords(queue,
                List.of(timeTick, musicVolumeChanged, alarmVolumeChanged));

        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(timeTick, optionsTimeTick));
        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(musicVolumeChanged,
                optionsMusicVolumeChanged));
        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(alarmVolumeChanged,
                optionsAlarmVolumeChanged));
        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(timeTick, optionsTimeTick));
        // Verify that the older timeTick has been removed.
        verifyPendingRecords(queue,
                List.of(musicVolumeChanged, alarmVolumeChanged, timeTick));
    }

    /**
     * Verify that sending a broadcast with DELIVERY_GROUP_POLICY_MERGED works as expected.
     */
    @Test
    public void testDeliveryGroupPolicy_merged() {
        final BundleMerger extrasMerger = new BundleMerger();
        extrasMerger.setMergeStrategy(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST,
                BundleMerger.STRATEGY_ARRAY_APPEND);

        final Intent packageChangedForUid = createPackageChangedIntent(TEST_UID,
                List.of("com.testuid.component1"));
        final BroadcastOptions optionsPackageChangedForUid = BroadcastOptions.makeBasic();
        optionsPackageChangedForUid.setDeliveryGroupPolicy(
                BroadcastOptions.DELIVERY_GROUP_POLICY_MERGED);
        optionsPackageChangedForUid.setDeliveryGroupKey("package", String.valueOf(TEST_UID));
        optionsPackageChangedForUid.setDeliveryGroupExtrasMerger(extrasMerger);

        final Intent secondPackageChangedForUid = createPackageChangedIntent(TEST_UID,
                List.of("com.testuid.component2", "com.testuid.component3"));

        final Intent packageChangedForUid2 = createPackageChangedIntent(TEST_UID2,
                List.of("com.testuid2.component1"));
        final BroadcastOptions optionsPackageChangedForUid2 = BroadcastOptions.makeBasic();
        optionsPackageChangedForUid.setDeliveryGroupPolicy(
                BroadcastOptions.DELIVERY_GROUP_POLICY_MERGED);
        optionsPackageChangedForUid.setDeliveryGroupKey("package", String.valueOf(TEST_UID2));
        optionsPackageChangedForUid.setDeliveryGroupExtrasMerger(extrasMerger);

        // Halt all processing so that we get a consistent view
        mHandlerThread.getLooper().getQueue().postSyncBarrier();

        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(packageChangedForUid,
                optionsPackageChangedForUid));
        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(packageChangedForUid2,
                optionsPackageChangedForUid2));
        mImpl.enqueueBroadcastLocked(makeBroadcastRecord(secondPackageChangedForUid,
                optionsPackageChangedForUid));

        final BroadcastProcessQueue queue = mImpl.getProcessQueue(PACKAGE_GREEN,
                getUidForPackage(PACKAGE_GREEN));
        final Intent expectedPackageChangedForUid = createPackageChangedIntent(TEST_UID,
                List.of("com.testuid.component2", "com.testuid.component3",
                        "com.testuid.component1"));
        // Verify that packageChangedForUid and secondPackageChangedForUid broadcasts
        // have been merged.
        verifyPendingRecords(queue, List.of(packageChangedForUid2, expectedPackageChangedForUid));
    }

    private Intent createPackageChangedIntent(int uid, List<String> componentNameList) {
        final Intent packageChangedIntent = new Intent(Intent.ACTION_PACKAGE_CHANGED);
        packageChangedIntent.putExtra(Intent.EXTRA_UID, uid);
        packageChangedIntent.putExtra(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST,
                componentNameList.toArray());
        return packageChangedIntent;
    }

    private void verifyPendingRecords(BroadcastProcessQueue queue,
            List<Intent> intents) {
        for (int i = 0; i < intents.size(); i++) {
            queue.makeActiveNextPending();
            final Intent actualIntent = queue.getActive().intent;
            final Intent expectedIntent = intents.get(i);
            final String errMsg = "actual=" + actualIntent + ", expected=" + expectedIntent
                    + ", actual_extras=" + actualIntent.getExtras()
                    + ", expected_extras=" + expectedIntent.getExtras();
            assertTrue(errMsg, actualIntent.filterEquals(expectedIntent));
            assertBundleEquals(expectedIntent.getExtras(), actualIntent.getExtras());
        }
        assertTrue(queue.isEmpty());
    }

    private void assertBundleEquals(Bundle expected, Bundle actual) {
        final String errMsg = "expected=" + expected + ", actual=" + actual;
        if (expected == actual) {
            return;
        } else if (expected == null || actual == null) {
            fail(errMsg);
        }
        if (!expected.keySet().equals(actual.keySet())) {
            fail(errMsg);
        }
        for (String key : expected.keySet()) {
            final Object expectedValue = expected.get(key);
            final Object actualValue = actual.get(key);
            if (expectedValue == actualValue) {
                continue;
            } else if (expectedValue == null || actualValue == null) {
                fail(errMsg);
            }
            assertEquals(errMsg, expectedValue.getClass(), actualValue.getClass());
            if (expectedValue.getClass().isArray()) {
                assertEquals(errMsg, Array.getLength(expectedValue), Array.getLength(actualValue));
                for (int i = 0; i < Array.getLength(expectedValue); ++i) {
                    assertEquals(errMsg, Array.get(expectedValue, i), Array.get(actualValue, i));
                }
            } else if (expectedValue instanceof ArrayList) {
                final ArrayList<?> expectedList = (ArrayList<?>) expectedValue;
                final ArrayList<?> actualList = (ArrayList<?>) actualValue;
                assertEquals(errMsg, expectedList.size(), actualList.size());
                for (int i = 0; i < expectedList.size(); ++i) {
                    assertEquals(errMsg, expectedList.get(i), actualList.get(i));
                }
            } else {
                assertEquals(errMsg, expectedValue, actualValue);
            }
        }
    }
}
