/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.pm.pkg;

import static java.util.Collections.emptyList;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;

import com.android.internal.util.DataClass;
import com.android.server.pm.PackageSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * For use by {@link PackageSetting} to maintain functionality that used to exist in
 * {@link PackageParser.Package}.
 *
 * It is assumed that anything inside the package was not cached or written to disk, so none of
 * these fields are either. They must be set on every boot from other state on the device.
 *
 * These fields are also not copied into any cloned PackageSetting, to preserve the old behavior
 * where they would be lost implicitly by re-generating the package object.
 */
@DataClass(genSetters = true, genConstructor = false, genBuilder = false)
public class PackageStateUnserialized {

    private boolean hiddenUntilInstalled;

    @NonNull
    private List<SharedLibraryInfo> usesLibraryInfos = emptyList();

    @NonNull
    private List<String> usesLibraryFiles = emptyList();

    private boolean updatedSystemApp;

    @NonNull
    private volatile long[] lastPackageUsageTimeInMills;

    @Nullable
    private String overrideSeInfo;

    private long[] lazyInitLastPackageUsageTimeInMills() {
        return new long[PackageManager.NOTIFY_PACKAGE_USE_REASONS_COUNT];
    }

    public PackageStateUnserialized setLastPackageUsageTimeInMills(int reason, long time) {
        if (reason < 0) {
            return this;
        }
        if (reason >= PackageManager.NOTIFY_PACKAGE_USE_REASONS_COUNT) {
            return this;
        }
        getLastPackageUsageTimeInMills()[reason] = time;
        return this;
    }

    public long getLatestPackageUseTimeInMills() {
        long latestUse = 0L;
        for (long use : getLastPackageUsageTimeInMills()) {
            latestUse = Math.max(latestUse, use);
        }
        return latestUse;
    }

    public long getLatestForegroundPackageUseTimeInMills() {
        int[] foregroundReasons = {
                PackageManager.NOTIFY_PACKAGE_USE_ACTIVITY,
                PackageManager.NOTIFY_PACKAGE_USE_FOREGROUND_SERVICE
        };

        long latestUse = 0L;
        for (int reason : foregroundReasons) {
            latestUse = Math.max(latestUse, getLastPackageUsageTimeInMills()[reason]);
        }
        return latestUse;
    }

    public void updateFrom(PackageStateUnserialized other) {
        this.hiddenUntilInstalled = other.hiddenUntilInstalled;

        if (!other.usesLibraryInfos.isEmpty()) {
            this.usesLibraryInfos = new ArrayList<>(other.usesLibraryInfos);
        }

        if (!other.usesLibraryFiles.isEmpty()) {
            this.usesLibraryFiles = new ArrayList<>(other.usesLibraryFiles);
        }

        this.updatedSystemApp = other.updatedSystemApp;
        this.lastPackageUsageTimeInMills = other.lastPackageUsageTimeInMills;
        this.overrideSeInfo = other.overrideSeInfo;
    }

    public @NonNull List<SharedLibraryInfo> getNonNativeUsesLibraryInfos() {
        return getUsesLibraryInfos().stream()
                .filter((l) -> !l.isNative()).collect(Collectors.toList());
    }


    // Code below generated by codegen v1.0.14.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/frameworks/base/services/core/java/com/android/server/pm/pkg/PackageStateUnserialized.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    public boolean isHiddenUntilInstalled() {
        return hiddenUntilInstalled;
    }

    @DataClass.Generated.Member
    public @NonNull List<SharedLibraryInfo> getUsesLibraryInfos() {
        return usesLibraryInfos;
    }

    @DataClass.Generated.Member
    public @NonNull List<String> getUsesLibraryFiles() {
        return usesLibraryFiles;
    }

    @DataClass.Generated.Member
    public boolean isUpdatedSystemApp() {
        return updatedSystemApp;
    }

    @DataClass.Generated.Member
    public @NonNull long[] getLastPackageUsageTimeInMills() {
        long[] _lastPackageUsageTimeInMills = lastPackageUsageTimeInMills;
        if (_lastPackageUsageTimeInMills == null) {
            synchronized(this) {
                _lastPackageUsageTimeInMills = lastPackageUsageTimeInMills;
                if (_lastPackageUsageTimeInMills == null) {
                    _lastPackageUsageTimeInMills = lastPackageUsageTimeInMills = lazyInitLastPackageUsageTimeInMills();
                }
            }
        }
        return _lastPackageUsageTimeInMills;
    }

    @DataClass.Generated.Member
    public @Nullable String getOverrideSeInfo() {
        return overrideSeInfo;
    }

    @DataClass.Generated.Member
    public PackageStateUnserialized setHiddenUntilInstalled(boolean value) {
        hiddenUntilInstalled = value;
        return this;
    }

    @DataClass.Generated.Member
    public PackageStateUnserialized setUsesLibraryInfos(@NonNull List<SharedLibraryInfo> value) {
        usesLibraryInfos = value;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, usesLibraryInfos);
        return this;
    }

    @DataClass.Generated.Member
    public PackageStateUnserialized setUsesLibraryFiles(@NonNull List<String> value) {
        usesLibraryFiles = value;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, usesLibraryFiles);
        return this;
    }

    @DataClass.Generated.Member
    public PackageStateUnserialized setUpdatedSystemApp(boolean value) {
        updatedSystemApp = value;
        return this;
    }

    @DataClass.Generated.Member
    public PackageStateUnserialized setLastPackageUsageTimeInMills(@NonNull long... value) {
        lastPackageUsageTimeInMills = value;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, lastPackageUsageTimeInMills);
        return this;
    }

    @DataClass.Generated.Member
    public PackageStateUnserialized setOverrideSeInfo(@Nullable String value) {
        overrideSeInfo = value;
        return this;
    }

    @DataClass.Generated(
            time = 1580422870209L,
            codegenVersion = "1.0.14",
            sourceFile = "frameworks/base/services/core/java/com/android/server/pm/pkg/PackageStateUnserialized.java",
            inputSignatures = "private  boolean hiddenUntilInstalled\nprivate @android.annotation.NonNull java.util.List<android.content.pm.SharedLibraryInfo> usesLibraryInfos\nprivate @android.annotation.NonNull java.util.List<java.lang.String> usesLibraryFiles\nprivate  boolean updatedSystemApp\nprivate volatile @android.annotation.NonNull long[] lastPackageUsageTimeInMills\n @android.annotation.Nullable java.lang.String overrideSeInfo\nprivate  long[] lazyInitLastPackageUsageTimeInMills()\npublic  com.android.server.pm.pkg.PackageStateUnserialized setLastPackageUsageTimeInMills(int,long)\npublic  long getLatestPackageUseTimeInMills()\npublic  long getLatestForegroundPackageUseTimeInMills()\nclass PackageStateUnserialized extends java.lang.Object implements []\n@com.android.internal.util.DataClass(genSetters=true, genConstructor=false, genBuilder=false)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
