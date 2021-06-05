/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.expectThrows;

import android.apex.ApexInfo;
import android.apex.ApexSessionInfo;
import android.apex.ApexSessionParams;
import android.apex.IApexService;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.pm.parsing.PackageParser2;
import com.android.server.pm.parsing.TestPackageParser2;
import com.android.server.pm.parsing.pkg.AndroidPackage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)

public class ApexManagerTest {
    private static final String TEST_APEX_PKG = "com.android.apex.test";
    private static final String TEST_APEX_FILE_NAME = "apex.test.apex";
    private static final int TEST_SESSION_ID = 99999999;
    private static final int[] TEST_CHILD_SESSION_ID = {8888, 7777};
    private ApexManager mApexManager;
    private Context mContext;
    private PackageParser2 mPackageParser2;

    private IApexService mApexService = mock(IApexService.class);

    @Before
    public void setUp() throws RemoteException {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        ApexManager.ApexManagerImpl managerImpl = spy(new ApexManager.ApexManagerImpl());
        doReturn(mApexService).when(managerImpl).waitForApexService();
        mApexManager = managerImpl;
        mPackageParser2 = new TestPackageParser2();
    }

    @Test
    public void testGetPackageInfo_setFlagsMatchActivePackage() throws RemoteException {
        when(mApexService.getAllPackages()).thenReturn(createApexInfoForTestPkg(true, false));
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());
        final PackageInfo activePkgPi = mApexManager.getPackageInfo(TEST_APEX_PKG,
                ApexManager.MATCH_ACTIVE_PACKAGE);

        assertThat(activePkgPi).isNotNull();
        assertThat(activePkgPi.packageName).contains(TEST_APEX_PKG);

        final PackageInfo factoryPkgPi = mApexManager.getPackageInfo(TEST_APEX_PKG,
                ApexManager.MATCH_FACTORY_PACKAGE);

        assertThat(factoryPkgPi).isNull();
    }

    @Test
    public void testGetPackageInfo_setFlagsMatchFactoryPackage() throws RemoteException {
        when(mApexService.getAllPackages()).thenReturn(createApexInfoForTestPkg(false, true));
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());
        PackageInfo factoryPkgPi = mApexManager.getPackageInfo(TEST_APEX_PKG,
                ApexManager.MATCH_FACTORY_PACKAGE);

        assertThat(factoryPkgPi).isNotNull();
        assertThat(factoryPkgPi.packageName).contains(TEST_APEX_PKG);

        final PackageInfo activePkgPi = mApexManager.getPackageInfo(TEST_APEX_PKG,
                ApexManager.MATCH_ACTIVE_PACKAGE);

        assertThat(activePkgPi).isNull();
    }

    @Test
    public void testGetPackageInfo_setFlagsNone() throws RemoteException {
        when(mApexService.getAllPackages()).thenReturn(createApexInfoForTestPkg(false, true));
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        assertThat(mApexManager.getPackageInfo(TEST_APEX_PKG, 0)).isNull();
    }

    @Test
    public void testGetActivePackages() throws RemoteException {
        when(mApexService.getAllPackages()).thenReturn(createApexInfoForTestPkg(true, true));
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        assertThat(mApexManager.getActivePackages()).isNotEmpty();
    }

    @Test
    public void testGetActivePackages_noneActivePackages() throws RemoteException {
        when(mApexService.getAllPackages()).thenReturn(createApexInfoForTestPkg(false, true));
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        assertThat(mApexManager.getActivePackages()).isEmpty();
    }

    @Test
    public void testGetFactoryPackages() throws RemoteException {
        when(mApexService.getAllPackages()).thenReturn(createApexInfoForTestPkg(false, true));
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        assertThat(mApexManager.getFactoryPackages()).isNotEmpty();
    }

    @Test
    public void testGetFactoryPackages_noneFactoryPackages() throws RemoteException {
        when(mApexService.getAllPackages()).thenReturn(createApexInfoForTestPkg(true, false));
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        assertThat(mApexManager.getFactoryPackages()).isEmpty();
    }

    @Test
    public void testGetInactivePackages() throws RemoteException {
        when(mApexService.getAllPackages()).thenReturn(createApexInfoForTestPkg(false, true));
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        assertThat(mApexManager.getInactivePackages()).isNotEmpty();
    }

    @Test
    public void testGetInactivePackages_noneInactivePackages() throws RemoteException {
        when(mApexService.getAllPackages()).thenReturn(createApexInfoForTestPkg(true, false));
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        assertThat(mApexManager.getInactivePackages()).isEmpty();
    }

    @Test
    public void testIsApexPackage() throws RemoteException {
        when(mApexService.getAllPackages()).thenReturn(createApexInfoForTestPkg(false, true));
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        assertThat(mApexManager.isApexPackage(TEST_APEX_PKG)).isTrue();
    }

    @Test
    public void testIsApexSupported() {
        assertThat(mApexManager.isApexSupported()).isTrue();
    }

    @Test
    public void testGetStagedSessionInfo() throws RemoteException {
        when(mApexService.getStagedSessionInfo(anyInt())).thenReturn(
                getFakeStagedSessionInfo());

        mApexManager.getStagedSessionInfo(TEST_SESSION_ID);
        verify(mApexService, times(1)).getStagedSessionInfo(TEST_SESSION_ID);
    }

    @Test
    public void testGetStagedSessionInfo_unKnownStagedSessionId() throws RemoteException {
        when(mApexService.getStagedSessionInfo(anyInt())).thenReturn(
                getFakeUnknownSessionInfo());

        assertThat(mApexManager.getStagedSessionInfo(TEST_SESSION_ID)).isNull();
    }

    @Test
    public void testSubmitStagedSession_throwPackageManagerException() throws RemoteException {
        doAnswer(invocation -> {
            throw new Exception();
        }).when(mApexService).submitStagedSession(any(), any());

        assertThrows(PackageManagerException.class,
                () -> mApexManager.submitStagedSession(testParamsWithChildren()));
    }

    @Test
    public void testSubmitStagedSession_throwRunTimeException() throws RemoteException {
        doThrow(RemoteException.class).when(mApexService).submitStagedSession(any(), any());

        assertThrows(RuntimeException.class,
                () -> mApexManager.submitStagedSession(testParamsWithChildren()));
    }

    @Test
    public void testMarkStagedSessionReady_throwPackageManagerException() throws RemoteException {
        doAnswer(invocation -> {
            throw new Exception();
        }).when(mApexService).markStagedSessionReady(anyInt());

        assertThrows(PackageManagerException.class,
                () -> mApexManager.markStagedSessionReady(TEST_SESSION_ID));
    }

    @Test
    public void testMarkStagedSessionReady_throwRunTimeException() throws RemoteException {
        doThrow(RemoteException.class).when(mApexService).markStagedSessionReady(anyInt());

        assertThrows(RuntimeException.class,
                () -> mApexManager.markStagedSessionReady(TEST_SESSION_ID));
    }

    @Test
    public void testRevertActiveSessions_remoteException() throws RemoteException {
        doThrow(RemoteException.class).when(mApexService).revertActiveSessions();

        try {
            assertThat(mApexManager.revertActiveSessions()).isFalse();
        } catch (Exception e) {
            throw new AssertionError("ApexManager should not raise Exception");
        }
    }

    @Test
    public void testMarkStagedSessionSuccessful_throwRemoteException() throws RemoteException {
        doThrow(RemoteException.class).when(mApexService).markStagedSessionSuccessful(anyInt());

        assertThrows(RuntimeException.class,
                () -> mApexManager.markStagedSessionSuccessful(TEST_SESSION_ID));
    }

    @Test
    public void testUninstallApex_throwException_returnFalse() throws RemoteException {
        doAnswer(invocation -> {
            throw new Exception();
        }).when(mApexService).unstagePackages(any());

        assertThat(mApexManager.uninstallApex(TEST_APEX_PKG)).isFalse();
    }

    @Test
    public void testReportErrorWithApkInApex() throws RemoteException {
        when(mApexService.getActivePackages()).thenReturn(createApexInfoForTestPkg(true, true));
        final ApexManager.ActiveApexInfo activeApex = mApexManager.getActiveApexInfos().get(0);
        assertThat(activeApex.apexModuleName).isEqualTo(TEST_APEX_PKG);

        when(mApexService.getAllPackages()).thenReturn(createApexInfoForTestPkg(true, true));
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        assertThat(mApexManager.isApkInApexInstallSuccess(activeApex.apexModuleName)).isTrue();
        mApexManager.reportErrorWithApkInApex(activeApex.apexDirectory.getAbsolutePath());
        assertThat(mApexManager.isApkInApexInstallSuccess(activeApex.apexModuleName)).isFalse();
    }

    /**
     * registerApkInApex method checks if the prefix of base apk path contains the apex package
     * name. When an apex package name is a prefix of another apex package name, e.g,
     * com.android.media and com.android.mediaprovider, then we need to ensure apk inside apex
     * mediaprovider does not get registered under apex media.
     */
    @Test
    public void testRegisterApkInApexDoesNotRegisterSimilarPrefix() throws RemoteException {
        when(mApexService.getActivePackages()).thenReturn(createApexInfoForTestPkg(true, true));
        final ApexManager.ActiveApexInfo activeApex = mApexManager.getActiveApexInfos().get(0);
        assertThat(activeApex.apexModuleName).isEqualTo(TEST_APEX_PKG);

        AndroidPackage fakeApkInApex = mock(AndroidPackage.class);
        when(fakeApkInApex.getBaseApkPath()).thenReturn("/apex/" + TEST_APEX_PKG + "randomSuffix");
        when(fakeApkInApex.getPackageName()).thenReturn("randomPackageName");

        when(mApexService.getAllPackages()).thenReturn(createApexInfoForTestPkg(true, true));
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        assertThat(mApexManager.getApksInApex(activeApex.apexModuleName)).isEmpty();
        mApexManager.registerApkInApex(fakeApkInApex);
        assertThat(mApexManager.getApksInApex(activeApex.apexModuleName)).isEmpty();
    }

    @Test
    public void testInstallPackageFailsToInstallNewApex() throws Exception {
        when(mApexService.getAllPackages()).thenReturn(createApexInfoForTestPkg(true, false));
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        File apex = extractResource("test.apex_rebootless_v1", "test.rebootless_apex_v1.apex");
        PackageManagerException e = expectThrows(PackageManagerException.class,
                () -> mApexManager.installPackage(apex, mPackageParser2));
        assertThat(e).hasMessageThat().contains("It is forbidden to install new APEX packages");
    }

    @Test
    public void testInstallPackageDowngrade() throws Exception {
        File activeApex = extractResource("test.apex_rebootless_v2",
                "test.rebootless_apex_v2.apex");
        ApexInfo activeApexInfo = createApexInfo("test.apex_rebootless", 2, /* isActive= */ true,
                /* isFactory= */ false, activeApex);
        when(mApexService.getAllPackages()).thenReturn(new ApexInfo[]{activeApexInfo});
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        File installedApex = extractResource("test.apex_rebootless_v1",
                "test.rebootless_apex_v1.apex");
        PackageManagerException e = expectThrows(PackageManagerException.class,
                () -> mApexManager.installPackage(installedApex, mPackageParser2));
        assertThat(e).hasMessageThat().contains(
                "Downgrade of APEX package test.apex.rebootless is not allowed");
    }

    @Test
    public void testInstallPackage() throws Exception {
        ApexInfo activeApexInfo = createApexInfo("test.apex_rebootless", 1, /* isActive= */ true,
                /* isFactory= */ false, extractResource("test.apex_rebootless_v1",
                  "test.rebootless_apex_v1.apex"));
        when(mApexService.getAllPackages()).thenReturn(new ApexInfo[]{activeApexInfo});
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        File finalApex = extractResource("test.rebootles_apex_v2", "test.rebootless_apex_v2.apex");
        ApexInfo newApexInfo = createApexInfo("test.apex_rebootless", 2, /* isActive= */ true,
                /* isFactory= */ false, finalApex);
        when(mApexService.installAndActivatePackage(anyString())).thenReturn(newApexInfo);

        File installedApex = extractResource("installed", "test.rebootless_apex_v2.apex");
        mApexManager.installPackage(installedApex, mPackageParser2);

        PackageInfo newInfo = mApexManager.getPackageInfo("test.apex.rebootless",
                ApexManager.MATCH_ACTIVE_PACKAGE);
        assertThat(newInfo.applicationInfo.sourceDir).isEqualTo(finalApex.getAbsolutePath());
        assertThat(newInfo.applicationInfo.longVersionCode).isEqualTo(2);
    }

    @Test
    public void testInstallPackageBinderCallFails() throws Exception {
        ApexInfo activeApexInfo = createApexInfo("test.apex_rebootless", 1, /* isActive= */ true,
                /* isFactory= */ false, extractResource("test.apex_rebootless_v1",
                  "test.rebootless_apex_v1.apex"));
        when(mApexService.getAllPackages()).thenReturn(new ApexInfo[]{activeApexInfo});
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        when(mApexService.installAndActivatePackage(anyString())).thenThrow(
                new RuntimeException("install failed :("));

        File installedApex = extractResource("test.apex_rebootless_v1",
                "test.rebootless_apex_v1.apex");
        assertThrows(PackageManagerException.class,
                () -> mApexManager.installPackage(installedApex, mPackageParser2));
    }

    @Test
    public void testInstallPackageSignedWithWrongCertificate() throws Exception {
        File activeApex = extractResource("shim_v1", "com.android.apex.cts.shim.apex");
        ApexInfo activeApexInfo = createApexInfo("com.android.apex.cts.shim", 1,
                /* isActive= */ true, /* isFactory= */ false, activeApex);
        when(mApexService.getAllPackages()).thenReturn(new ApexInfo[]{activeApexInfo});
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        File installedApex = extractResource("shim_different_certificate",
                "com.android.apex.cts.shim.v2_different_certificate.apex");
        PackageManagerException e = expectThrows(PackageManagerException.class,
                () -> mApexManager.installPackage(installedApex, mPackageParser2));
        assertThat(e).hasMessageThat().contains("APK container signature of ");
        assertThat(e).hasMessageThat().contains(
                "is not compatible with currently installed on device");
    }

    @Test
    public void testInstallPackageUnsignedApexContainer() throws Exception {
        File activeApex = extractResource("shim_v1", "com.android.apex.cts.shim.apex");
        ApexInfo activeApexInfo = createApexInfo("com.android.apex.cts.shim", 1,
                /* isActive= */ true, /* isFactory= */ false, activeApex);
        when(mApexService.getAllPackages()).thenReturn(new ApexInfo[]{activeApexInfo});
        mApexManager.scanApexPackagesTraced(mPackageParser2,
                ParallelPackageParser.makeExecutorService());

        File installedApex = extractResource("shim_unsigned_apk_container",
                "com.android.apex.cts.shim.v2_unsigned_apk_container.apex");
        PackageManagerException e = expectThrows(PackageManagerException.class,
                () -> mApexManager.installPackage(installedApex, mPackageParser2));
        assertThat(e).hasMessageThat().contains("Failed to collect certificates from ");
    }

    private ApexInfo[] createApexInfoForTestPkg(boolean isActive, boolean isFactory) {
        File apexFile = extractResource(TEST_APEX_PKG,  TEST_APEX_FILE_NAME);
        ApexInfo apexInfo = new ApexInfo();
        apexInfo.isActive = isActive;
        apexInfo.isFactory = isFactory;
        apexInfo.moduleName = TEST_APEX_PKG;
        apexInfo.modulePath = apexFile.getPath();
        apexInfo.versionCode = 191000070;
        apexInfo.preinstalledModulePath = apexFile.getPath();

        return new ApexInfo[]{apexInfo};
    }

    private ApexInfo createApexInfo(String moduleName, int versionCode, boolean isActive,
            boolean isFactory, File apexFile) {
        ApexInfo apexInfo = new ApexInfo();
        apexInfo.moduleName = moduleName;
        apexInfo.versionCode = versionCode;
        apexInfo.isActive = isActive;
        apexInfo.isFactory = isFactory;
        apexInfo.modulePath = apexFile.getPath();
        return apexInfo;
    }

    private ApexSessionInfo getFakeStagedSessionInfo() {
        ApexSessionInfo stagedSessionInfo = new ApexSessionInfo();
        stagedSessionInfo.sessionId = TEST_SESSION_ID;
        stagedSessionInfo.isStaged = true;

        return stagedSessionInfo;
    }

    private ApexSessionInfo getFakeUnknownSessionInfo() {
        ApexSessionInfo stagedSessionInfo = new ApexSessionInfo();
        stagedSessionInfo.sessionId = TEST_SESSION_ID;
        stagedSessionInfo.isUnknown = true;

        return stagedSessionInfo;
    }

    private static ApexSessionParams testParamsWithChildren() {
        ApexSessionParams params = new ApexSessionParams();
        params.sessionId = TEST_SESSION_ID;
        params.childSessionIds = TEST_CHILD_SESSION_ID;
        return params;
    }

    // Extracts the binary data from a resource and writes it to a temp file
    private static File extractResource(String baseName, String fullResourceName) {
        File file;
        try {
            file = File.createTempFile(baseName, ".apex");
        } catch (IOException e) {
            throw new AssertionError("CreateTempFile IOException" + e);
        }

        try (
                InputStream in = ApexManager.class.getClassLoader()
                        .getResourceAsStream(fullResourceName);
                OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + fullResourceName);
            }
            byte[] buf = new byte[65536];
            int chunkSize;
            while ((chunkSize = in.read(buf)) != -1) {
                out.write(buf, 0, chunkSize);
            }
            return file;
        } catch (IOException e) {
            throw new AssertionError("Exception while converting stream to file" + e);
        }
    }
}
