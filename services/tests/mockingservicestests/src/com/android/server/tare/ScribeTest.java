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

package com.android.server.tare;


import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.UserHandle;
import android.util.Log;
import android.util.SparseArrayMap;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalServices;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for various Scribe behavior, including reading and writing correctly from file.
 *
 * atest FrameworksServicesTests:ScribeTest
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ScribeTest {
    private static final String TAG = "ScribeTest";

    private static final int TEST_USER_ID = 27;
    private static final String TEST_PACKAGE = "com.android.test";

    private MockitoSession mMockingSession;
    private Scribe mScribeUnderTest;
    private File mTestFileDir;
    private final List<PackageInfo> mInstalledPackages = new ArrayList<>();

    @Mock
    private InternalResourceService mIrs;

    private Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    @Before
    public void setUp() throws Exception {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .strictness(Strictness.LENIENT)
                .mockStatic(LocalServices.class)
                .startMocking();
        when(mIrs.getLock()).thenReturn(new Object());
        when(mIrs.isEnabled()).thenReturn(true);
        when(mIrs.getInstalledPackages()).thenReturn(mInstalledPackages);
        mTestFileDir = new File(getContext().getFilesDir(), "scribe_test");
        //noinspection ResultOfMethodCallIgnored
        mTestFileDir.mkdirs();
        Log.d(TAG, "Saving data to '" + mTestFileDir + "'");
        mScribeUnderTest = new Scribe(mIrs, mTestFileDir);

        addInstalledPackage(TEST_USER_ID, TEST_PACKAGE);
    }

    @After
    public void tearDown() throws Exception {
        mScribeUnderTest.tearDownLocked();
        if (mTestFileDir.exists() && !mTestFileDir.delete()) {
            Log.w(TAG, "Failed to delete test file directory");
        }
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @Test
    public void testWriteHighLevelStateToDisk() {
        long lastReclamationTime = System.currentTimeMillis();
        long remainingConsumableCakes = 2000L;
        long consumptionLimit = 500_000L;
        when(mIrs.getConsumptionLimitLocked()).thenReturn(consumptionLimit);

        Ledger ledger = mScribeUnderTest.getLedgerLocked(TEST_USER_ID, TEST_PACKAGE);
        ledger.recordTransaction(new Ledger.Transaction(0, 1000L, 1, null, 2000, 0));
        // Negative ledger balance shouldn't affect the total circulation value.
        ledger = mScribeUnderTest.getLedgerLocked(TEST_USER_ID + 1, TEST_PACKAGE);
        ledger.recordTransaction(new Ledger.Transaction(0, 1000L, 1, null, -5000, 3000));
        mScribeUnderTest.setLastReclamationTimeLocked(lastReclamationTime);
        mScribeUnderTest.setConsumptionLimitLocked(consumptionLimit);
        mScribeUnderTest.adjustRemainingConsumableCakesLocked(
                remainingConsumableCakes - consumptionLimit);

        assertEquals(lastReclamationTime, mScribeUnderTest.getLastReclamationTimeLocked());
        assertEquals(remainingConsumableCakes,
                mScribeUnderTest.getRemainingConsumableCakesLocked());
        assertEquals(consumptionLimit, mScribeUnderTest.getSatiatedConsumptionLimitLocked());

        mScribeUnderTest.writeImmediatelyForTesting();
        mScribeUnderTest.loadFromDiskLocked();

        assertEquals(lastReclamationTime, mScribeUnderTest.getLastReclamationTimeLocked());
        assertEquals(remainingConsumableCakes,
                mScribeUnderTest.getRemainingConsumableCakesLocked());
        assertEquals(consumptionLimit, mScribeUnderTest.getSatiatedConsumptionLimitLocked());
    }

    @Test
    public void testWritingEmptyLedgerToDisk() {
        final Ledger ogLedger = mScribeUnderTest.getLedgerLocked(TEST_USER_ID, TEST_PACKAGE);
        mScribeUnderTest.writeImmediatelyForTesting();

        mScribeUnderTest.loadFromDiskLocked();
        assertLedgersEqual(ogLedger, mScribeUnderTest.getLedgerLocked(TEST_USER_ID, TEST_PACKAGE));
    }

    @Test
    public void testWritingPopulatedLedgerToDisk() {
        final Ledger ogLedger = mScribeUnderTest.getLedgerLocked(TEST_USER_ID, TEST_PACKAGE);
        ogLedger.recordTransaction(new Ledger.Transaction(0, 1000, 1, null, 51, 0));
        ogLedger.recordTransaction(new Ledger.Transaction(1500, 2000, 2, "green", 52, -1));
        ogLedger.recordTransaction(new Ledger.Transaction(2500, 3000, 3, "blue", 3, 12));
        mScribeUnderTest.writeImmediatelyForTesting();

        mScribeUnderTest.loadFromDiskLocked();
        assertLedgersEqual(ogLedger, mScribeUnderTest.getLedgerLocked(TEST_USER_ID, TEST_PACKAGE));
    }

    @Test
    public void testWritingMultipleLedgersToDisk() {
        final SparseArrayMap<String, Ledger> ledgers = new SparseArrayMap<>();
        final int numUsers = 3;
        final int numLedgers = 5;
        for (int u = 0; u < numUsers; ++u) {
            final int userId = TEST_USER_ID + u;
            for (int l = 0; l < numLedgers; ++l) {
                final String pkgName = TEST_PACKAGE + l;
                addInstalledPackage(userId, pkgName);
                final Ledger ledger = mScribeUnderTest.getLedgerLocked(userId, pkgName);
                ledger.recordTransaction(new Ledger.Transaction(
                        0, 1000L * u + l, 1, null, -51L * u + l, 50));
                ledger.recordTransaction(new Ledger.Transaction(
                        1500L * u + l, 2000L * u + l, 2 * u + l, "green" + u + l, 52L * u + l, 0));
                ledger.recordTransaction(new Ledger.Transaction(
                        2500L * u + l, 3000L * u + l, 3 * u + l, "blue" + u + l, 3L * u + l, 0));
                ledgers.add(userId, pkgName, ledger);
            }
        }
        mScribeUnderTest.writeImmediatelyForTesting();

        mScribeUnderTest.loadFromDiskLocked();
        ledgers.forEach((userId, pkgName, ledger)
                -> assertLedgersEqual(ledger, mScribeUnderTest.getLedgerLocked(userId, pkgName)));
    }

    @Test
    public void testDiscardLedgerFromDisk() {
        final Ledger ogLedger = mScribeUnderTest.getLedgerLocked(TEST_USER_ID, TEST_PACKAGE);
        ogLedger.recordTransaction(new Ledger.Transaction(0, 1000, 1, null, 51, 1));
        ogLedger.recordTransaction(new Ledger.Transaction(1500, 2000, 2, "green", 52, 0));
        ogLedger.recordTransaction(new Ledger.Transaction(2500, 3000, 3, "blue", 3, 1));
        mScribeUnderTest.writeImmediatelyForTesting();

        mScribeUnderTest.loadFromDiskLocked();
        assertLedgersEqual(ogLedger, mScribeUnderTest.getLedgerLocked(TEST_USER_ID, TEST_PACKAGE));

        mScribeUnderTest.discardLedgerLocked(TEST_USER_ID, TEST_PACKAGE);
        mScribeUnderTest.writeImmediatelyForTesting();

        // Make sure there's no more saved ledger.
        mScribeUnderTest.loadFromDiskLocked();
        assertLedgersEqual(new Ledger(),
                mScribeUnderTest.getLedgerLocked(TEST_USER_ID, TEST_PACKAGE));
    }

    @Test
    public void testLoadingMissingPackageFromDisk() {
        final String pkgName = TEST_PACKAGE + ".uninstalled";
        final Ledger ogLedger = mScribeUnderTest.getLedgerLocked(TEST_USER_ID, pkgName);
        ogLedger.recordTransaction(new Ledger.Transaction(0, 1000, 1, null, 51, 1));
        ogLedger.recordTransaction(new Ledger.Transaction(1500, 2000, 2, "green", 52, 2));
        ogLedger.recordTransaction(new Ledger.Transaction(2500, 3000, 3, "blue", 3, 3));
        mScribeUnderTest.writeImmediatelyForTesting();

        // Package isn't installed, so make sure it's not saved to memory after loading.
        mScribeUnderTest.loadFromDiskLocked();
        assertLedgersEqual(new Ledger(), mScribeUnderTest.getLedgerLocked(TEST_USER_ID, pkgName));
    }

    @Test
    public void testLoadingMissingUserFromDisk() {
        final int userId = TEST_USER_ID + 1;
        final Ledger ogLedger = mScribeUnderTest.getLedgerLocked(userId, TEST_PACKAGE);
        ogLedger.recordTransaction(new Ledger.Transaction(0, 1000, 1, null, 51, 0));
        ogLedger.recordTransaction(new Ledger.Transaction(1500, 2000, 2, "green", 52, 1));
        ogLedger.recordTransaction(new Ledger.Transaction(2500, 3000, 3, "blue", 3, 3));
        mScribeUnderTest.writeImmediatelyForTesting();

        // User doesn't show up with any packages, so make sure nothing is saved after loading.
        mScribeUnderTest.loadFromDiskLocked();
        assertLedgersEqual(new Ledger(), mScribeUnderTest.getLedgerLocked(userId, TEST_PACKAGE));
    }

    @Test
    public void testChangingConsumable() {
        assertEquals(0, mScribeUnderTest.getSatiatedConsumptionLimitLocked());
        assertEquals(0, mScribeUnderTest.getRemainingConsumableCakesLocked());

        // Limit increased, so remaining value should be adjusted as well
        mScribeUnderTest.setConsumptionLimitLocked(1000);
        assertEquals(1000, mScribeUnderTest.getSatiatedConsumptionLimitLocked());
        assertEquals(1000, mScribeUnderTest.getRemainingConsumableCakesLocked());

        // Limit decreased below remaining, so remaining value should be adjusted as well
        mScribeUnderTest.setConsumptionLimitLocked(500);
        assertEquals(500, mScribeUnderTest.getSatiatedConsumptionLimitLocked());
        assertEquals(500, mScribeUnderTest.getRemainingConsumableCakesLocked());

        mScribeUnderTest.adjustRemainingConsumableCakesLocked(-100);
        assertEquals(500, mScribeUnderTest.getSatiatedConsumptionLimitLocked());
        assertEquals(400, mScribeUnderTest.getRemainingConsumableCakesLocked());

        // Limit increased, so remaining value should be adjusted by the difference as well
        mScribeUnderTest.setConsumptionLimitLocked(1000);
        assertEquals(1000, mScribeUnderTest.getSatiatedConsumptionLimitLocked());
        assertEquals(900, mScribeUnderTest.getRemainingConsumableCakesLocked());


        // Limit decreased, but above remaining, so remaining value should left alone
        mScribeUnderTest.setConsumptionLimitLocked(950);
        assertEquals(950, mScribeUnderTest.getSatiatedConsumptionLimitLocked());
        assertEquals(900, mScribeUnderTest.getRemainingConsumableCakesLocked());
    }

    private void assertLedgersEqual(Ledger expected, Ledger actual) {
        if (expected == null) {
            assertNull(actual);
            return;
        }
        assertNotNull(actual);
        assertEquals(expected.getCurrentBalance(), actual.getCurrentBalance());
        List<Ledger.Transaction> expectedTransactions = expected.getTransactions();
        List<Ledger.Transaction> actualTransactions = actual.getTransactions();
        assertEquals(expectedTransactions.size(), actualTransactions.size());
        for (int i = 0; i < expectedTransactions.size(); ++i) {
            assertTransactionsEqual(expectedTransactions.get(i), actualTransactions.get(i));
        }
    }

    private void assertTransactionsEqual(Ledger.Transaction expected, Ledger.Transaction actual) {
        if (expected == null) {
            assertNull(actual);
            return;
        }
        assertNotNull(actual);
        assertEquals(expected.startTimeMs, actual.startTimeMs);
        assertEquals(expected.endTimeMs, actual.endTimeMs);
        assertEquals(expected.eventId, actual.eventId);
        assertEquals(expected.tag, actual.tag);
        assertEquals(expected.delta, actual.delta);
        assertEquals(expected.ctp, actual.ctp);
    }

    private void addInstalledPackage(int userId, String pkgName) {
        PackageInfo pkgInfo = new PackageInfo();
        pkgInfo.packageName = pkgName;
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.uid = UserHandle.getUid(userId, Math.abs(pkgName.hashCode()));
        pkgInfo.applicationInfo = applicationInfo;
        mInstalledPackages.add(pkgInfo);
    }
}
