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
 * limitations under the License
 */

package com.android.server.pm.dex;

import android.os.Binder;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.server.pm.Installer;
import com.android.server.pm.parsing.PackageInfoUtils;
import com.android.server.pm.pkg.PackageStateInternal;

import java.io.File;

public class ViewCompiler {
    private final Object mInstallLock;
    @GuardedBy("mInstallLock")
    private final Installer mInstaller;

    public ViewCompiler(Object installLock, Installer installer) {
        mInstallLock = installLock;
        mInstaller = installer;
    }

    public boolean compileLayouts(PackageStateInternal ps, String apkPath) {
        try {
            final String packageName = ps.getPackageName();
            File dataDir = PackageInfoUtils.getDataDir(ps, UserHandle.myUserId());
            if (dataDir == null) {
                // The app is not installed on the target user and doesn't have a data dir
                return false;
            }
            final String outDexFile = dataDir.getAbsolutePath() + "/code_cache/compiled_view.dex";
            Log.i("PackageManager", "Compiling layouts in " + packageName + " (" + apkPath +
                ") to " + outDexFile);
            final long callingId = Binder.clearCallingIdentity();
            try {
                synchronized (mInstallLock) {
                    return mInstaller.compileLayouts(apkPath, packageName, outDexFile,
                        ps.getAppId());
                }
            } finally {
                Binder.restoreCallingIdentity(callingId);
            }
        } catch (Throwable e) {
            Log.e("PackageManager", "Failed to compile layouts", e);
            return false;
        }
    }
}
