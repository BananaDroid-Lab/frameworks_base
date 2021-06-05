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

// TODO(b/169883602): This is purposely a different package from the path so that it can access
// AppSearchImpl methods without having to make methods public. This should be moved into a proper
// package once AppSearchImpl-VisibilityStore's dependencies are refactored.
package com.android.server.appsearch.external.localstorage;

import static android.Manifest.permission.READ_GLOBAL_APP_SEARCH_DATA;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.PackageIdentifier;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.server.appsearch.external.localstorage.util.PrefixUtil;
import com.android.server.appsearch.visibilitystore.VisibilityStore;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Collections;

public class VisibilityStoreTest {

    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();
    private MockPackageManager mMockPackageManager = new MockPackageManager();
    private Context mContext;
    private AppSearchImpl mAppSearchImpl;
    private VisibilityStore mVisibilityStore;
    private int mUid;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        mContext =
                new ContextWrapper(context) {
                    @Override
                    public PackageManager getPackageManager() {
                        return mMockPackageManager.getMockPackageManager();
                    }
                };

        // Give ourselves global query permissions
        mAppSearchImpl =
                AppSearchImpl.create(
                        mTemporaryFolder.newFolder(),
                        mContext,
                        mContext.getUserId(),
                        /*logger=*/ null);

        mUid = mContext.getPackageManager().getPackageUid(mContext.getPackageName(), /*flags=*/ 0);
        mVisibilityStore = mAppSearchImpl.getVisibilityStoreLocked();
    }

    /**
     * Make sure that we don't conflict with any special characters that AppSearchImpl has reserved.
     */
    @Test
    public void testValidPackageName() {
        assertThat(VisibilityStore.PACKAGE_NAME)
                .doesNotContain(String.valueOf(PrefixUtil.PACKAGE_DELIMITER));
        assertThat(VisibilityStore.PACKAGE_NAME)
                .doesNotContain(String.valueOf(PrefixUtil.DATABASE_DELIMITER));
    }

    /**
     * Make sure that we don't conflict with any special characters that AppSearchImpl has reserved.
     */
    @Test
    public void testValidDatabaseName() {
        assertThat(VisibilityStore.DATABASE_NAME)
                .doesNotContain(String.valueOf(PrefixUtil.PACKAGE_DELIMITER));
        assertThat(VisibilityStore.DATABASE_NAME)
                .doesNotContain(String.valueOf(PrefixUtil.DATABASE_DELIMITER));
    }

    @Test
    public void testSetVisibility_platformSurfaceable() throws Exception {
        // Make sure we have global query privileges
        mMockPackageManager.mockCheckPermission(
                READ_GLOBAL_APP_SEARCH_DATA, mContext.getPackageName(), PERMISSION_GRANTED);

        mVisibilityStore.setVisibility(
                "package",
                "database",
                /*schemasNotPlatformSurfaceable=*/ ImmutableSet.of(
                        "prefix/schema1", "prefix/schema2"),
                /*schemasPackageAccessible=*/ Collections.emptyMap());
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package",
                                "database",
                                "prefix/schema1",
                                mContext.getPackageName(),
                                mUid))
                .isFalse();
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package",
                                "database",
                                "prefix/schema2",
                                mContext.getPackageName(),
                                mUid))
                .isFalse();

        // New .setVisibility() call completely overrides previous visibility settings.
        // So "schema2" isn't preserved.
        mVisibilityStore.setVisibility(
                "package",
                "database",
                /*schemasNotPlatformSurfaceable=*/ ImmutableSet.of(
                        "prefix/schema1", "prefix/schema3"),
                /*schemasPackageAccessible=*/ Collections.emptyMap());
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package",
                                "database",
                                "prefix/schema1",
                                mContext.getPackageName(),
                                mUid))
                .isFalse();
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package",
                                "database",
                                "prefix/schema2",
                                mContext.getPackageName(),
                                mUid))
                .isTrue();
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package",
                                "database",
                                "prefix/schema3",
                                mContext.getPackageName(),
                                mUid))
                .isFalse();

        // Everything defaults to visible again.
        mVisibilityStore.setVisibility(
                "package",
                "database",
                /*schemasNotPlatformSurfaceable=*/ Collections.emptySet(),
                /*schemasPackageAccessible=*/ Collections.emptyMap());
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package",
                                "database",
                                "prefix/schema1",
                                mContext.getPackageName(),
                                mUid))
                .isTrue();
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package",
                                "database",
                                "prefix/schema2",
                                mContext.getPackageName(),
                                mUid))
                .isTrue();
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package",
                                "database",
                                "prefix/schema3",
                                mContext.getPackageName(),
                                mUid))
                .isTrue();
    }

    @Test
    public void testSetVisibility_packageAccessible() throws Exception {
        // Values for a "foo" client
        String packageNameFoo = "packageFoo";
        byte[] sha256CertFoo = new byte[] {10};
        int uidFoo = 1;

        // Values for a "bar" client
        String packageNameBar = "packageBar";
        byte[] sha256CertBar = new byte[] {100};
        int uidBar = 2;

        // Can't be the same value as uidFoo nor uidBar
        int uidNotFooOrBar = 3;

        // Make sure none of them have global query privileges
        mMockPackageManager.mockCheckPermission(
                READ_GLOBAL_APP_SEARCH_DATA, packageNameFoo, PERMISSION_DENIED);
        mMockPackageManager.mockCheckPermission(
                READ_GLOBAL_APP_SEARCH_DATA, packageNameBar, PERMISSION_DENIED);

        // By default, a schema isn't package accessible.
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package", "database", "prefix/schemaFoo", packageNameFoo, uidFoo))
                .isFalse();
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package", "database", "prefix/schemaBar", packageNameBar, uidBar))
                .isFalse();

        // Grant package access
        mVisibilityStore.setVisibility(
                "package",
                "database",
                /*schemasNotPlatformSurfaceable=*/ Collections.emptySet(),
                /*schemasPackageAccessible=*/ ImmutableMap.of(
                        "prefix/schemaFoo",
                        ImmutableList.of(new PackageIdentifier(packageNameFoo, sha256CertFoo)),
                        "prefix/schemaBar",
                        ImmutableList.of(new PackageIdentifier(packageNameBar, sha256CertBar))));

        // Should fail if PackageManager doesn't see that it has the proper certificate
        mMockPackageManager.mockGetPackageUidAsUser(packageNameFoo, mContext.getUserId(), uidFoo);
        mMockPackageManager.mockRemoveSigningCertificate(packageNameFoo, sha256CertFoo);
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package", "database", "prefix/schemaFoo", packageNameFoo, uidFoo))
                .isFalse();

        // Should fail if PackageManager doesn't think the package belongs to the uid
        mMockPackageManager.mockGetPackageUidAsUser(
                packageNameFoo, mContext.getUserId(), uidNotFooOrBar);
        mMockPackageManager.mockAddSigningCertificate(packageNameFoo, sha256CertFoo);
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package", "database", "prefix/schemaFoo", packageNameFoo, uidFoo))
                .isFalse();

        // But if uid and certificate match, then we should have access
        mMockPackageManager.mockGetPackageUidAsUser(packageNameFoo, mContext.getUserId(), uidFoo);
        mMockPackageManager.mockAddSigningCertificate(packageNameFoo, sha256CertFoo);
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package", "database", "prefix/schemaFoo", packageNameFoo, uidFoo))
                .isTrue();

        mMockPackageManager.mockGetPackageUidAsUser(packageNameBar, mContext.getUserId(), uidBar);
        mMockPackageManager.mockAddSigningCertificate(packageNameBar, sha256CertBar);
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package", "database", "prefix/schemaBar", packageNameBar, uidBar))
                .isTrue();

        // New .setVisibility() call completely overrides previous visibility settings. So
        // "schemaBar" settings aren't preserved.
        mVisibilityStore.setVisibility(
                "package",
                "database",
                /*schemasNotPlatformSurfaceable=*/ Collections.emptySet(),
                /*schemasPackageAccessible=*/ ImmutableMap.of(
                        "prefix/schemaFoo",
                        ImmutableList.of(new PackageIdentifier(packageNameFoo, sha256CertFoo))));

        mMockPackageManager.mockGetPackageUidAsUser(packageNameFoo, mContext.getUserId(), uidFoo);
        mMockPackageManager.mockAddSigningCertificate(packageNameFoo, sha256CertFoo);
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package", "database", "prefix/schemaFoo", packageNameFoo, uidFoo))
                .isTrue();

        mMockPackageManager.mockGetPackageUidAsUser(packageNameBar, mContext.getUserId(), uidBar);
        mMockPackageManager.mockAddSigningCertificate(packageNameBar, sha256CertBar);
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package", "database", "prefix/schemaBar", packageNameBar, uidBar))
                .isFalse();
    }

    @Test
    public void testIsSchemaSearchableByCaller_packageAccessibilityHandlesNameNotFoundException()
            throws Exception {
        // Values for a "foo" client
        String packageNameFoo = "packageFoo";
        byte[] sha256CertFoo = new byte[] {10};
        int uidFoo = 1;

        // Pretend we can't find the Foo package.
        mMockPackageManager.mockThrowsNameNotFoundException(packageNameFoo);

        // Make sure "foo" doesn't have global query privileges
        mMockPackageManager.mockCheckPermission(
                READ_GLOBAL_APP_SEARCH_DATA, packageNameFoo, PERMISSION_DENIED);

        // Grant package access
        mVisibilityStore.setVisibility(
                "package",
                "database",
                /*schemasNotPlatformSurfaceable=*/ Collections.emptySet(),
                /*schemasPackageAccessible=*/ ImmutableMap.of(
                        "prefix/schemaFoo",
                        ImmutableList.of(new PackageIdentifier(packageNameFoo, sha256CertFoo))));

        // If we can't verify the Foo package that has access, assume it doesn't have access.
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                "package", "database", "prefix/schemaFoo", packageNameFoo, uidFoo))
                .isFalse();
    }

    @Test
    public void testEmptyPrefix() throws Exception {
        // Values for a "foo" client
        String packageNameFoo = "packageFoo";
        byte[] sha256CertFoo = new byte[] {10};
        int uidFoo = 1;

        // Set it up such that the test package has global query privileges, but "foo" doesn't.
        mMockPackageManager.mockCheckPermission(
                READ_GLOBAL_APP_SEARCH_DATA, mContext.getPackageName(), PERMISSION_GRANTED);
        mMockPackageManager.mockCheckPermission(
                READ_GLOBAL_APP_SEARCH_DATA, packageNameFoo, PERMISSION_DENIED);

        mVisibilityStore.setVisibility(
                /*packageName=*/ "",
                /*databaseName=*/ "",
                /*schemasNotPlatformSurfaceable=*/ Collections.emptySet(),
                /*schemasPackageAccessible=*/ ImmutableMap.of(
                        "schema",
                        ImmutableList.of(new PackageIdentifier(packageNameFoo, sha256CertFoo))));

        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                /*packageName=*/ "",
                                /*databaseName=*/ "",
                                "schema",
                                mContext.getPackageName(),
                                mUid))
                .isTrue();

        mMockPackageManager.mockGetPackageUidAsUser(packageNameFoo, mContext.getUserId(), uidFoo);
        mMockPackageManager.mockAddSigningCertificate(packageNameFoo, sha256CertFoo);
        assertThat(
                        mVisibilityStore.isSchemaSearchableByCaller(
                                /*packageName=*/ "",
                                /*databaseName=*/ "",
                                "schema",
                                packageNameFoo,
                                uidFoo))
                .isTrue();
    }
}
