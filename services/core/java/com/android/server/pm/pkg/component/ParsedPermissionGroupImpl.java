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

package com.android.server.pm.pkg.component;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.pm.pkg.component.ParsedPermissionGroup;
import com.android.internal.util.DataClass;

/**
 * @hide
 */
@DataClass(genGetters = true, genSetters = true, genBuilder = false, genParcelable = true,
        genAidl = false)
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class ParsedPermissionGroupImpl extends ParsedComponentImpl implements
        ParsedPermissionGroup, Parcelable {

    private int requestDetailRes;
    private int backgroundRequestRes;
    private int backgroundRequestDetailRes;
    private int requestRes;
    private int priority;

    public String toString() {
        return "PermissionGroup{"
                + Integer.toHexString(System.identityHashCode(this))
                + " " + getName() + "}";
    }

    public ParsedPermissionGroupImpl() {
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(requestDetailRes);
        dest.writeInt(backgroundRequestRes);
        dest.writeInt(backgroundRequestDetailRes);
        dest.writeInt(requestRes);
        dest.writeInt(priority);
    }

    protected ParsedPermissionGroupImpl(@NonNull Parcel in) {
        super(in);
        this.requestDetailRes = in.readInt();
        this.backgroundRequestRes = in.readInt();
        this.backgroundRequestDetailRes = in.readInt();
        this.requestRes = in.readInt();
        this.priority = in.readInt();
    }



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/frameworks/base/services/core/java/com/android/server/pm/pkg/component/ParsedPermissionGroupImpl.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    public ParsedPermissionGroupImpl(
            int requestDetailRes,
            int backgroundRequestRes,
            int backgroundRequestDetailRes,
            int requestRes,
            int priority) {
        this.requestDetailRes = requestDetailRes;
        this.backgroundRequestRes = backgroundRequestRes;
        this.backgroundRequestDetailRes = backgroundRequestDetailRes;
        this.requestRes = requestRes;
        this.priority = priority;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public int getRequestDetailRes() {
        return requestDetailRes;
    }

    @DataClass.Generated.Member
    public int getBackgroundRequestRes() {
        return backgroundRequestRes;
    }

    @DataClass.Generated.Member
    public int getBackgroundRequestDetailRes() {
        return backgroundRequestDetailRes;
    }

    @DataClass.Generated.Member
    public int getRequestRes() {
        return requestRes;
    }

    @DataClass.Generated.Member
    public int getPriority() {
        return priority;
    }

    @DataClass.Generated.Member
    public @NonNull ParsedPermissionGroupImpl setRequestDetailRes( int value) {
        requestDetailRes = value;
        return this;
    }

    @DataClass.Generated.Member
    public @NonNull ParsedPermissionGroupImpl setBackgroundRequestRes( int value) {
        backgroundRequestRes = value;
        return this;
    }

    @DataClass.Generated.Member
    public @NonNull ParsedPermissionGroupImpl setBackgroundRequestDetailRes( int value) {
        backgroundRequestDetailRes = value;
        return this;
    }

    @DataClass.Generated.Member
    public @NonNull ParsedPermissionGroupImpl setRequestRes( int value) {
        requestRes = value;
        return this;
    }

    @DataClass.Generated.Member
    public @NonNull ParsedPermissionGroupImpl setPriority( int value) {
        priority = value;
        return this;
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<ParsedPermissionGroupImpl> CREATOR
            = new Parcelable.Creator<ParsedPermissionGroupImpl>() {
        @Override
        public ParsedPermissionGroupImpl[] newArray(int size) {
            return new ParsedPermissionGroupImpl[size];
        }

        @Override
        public ParsedPermissionGroupImpl createFromParcel(@NonNull Parcel in) {
            return new ParsedPermissionGroupImpl(in);
        }
    };

    @DataClass.Generated(
            time = 1642132854167L,
            codegenVersion = "1.0.23",
            sourceFile = "frameworks/base/services/core/java/com/android/server/pm/pkg/component/ParsedPermissionGroupImpl.java",
            inputSignatures = "private  int requestDetailRes\nprivate  int backgroundRequestRes\nprivate  int backgroundRequestDetailRes\nprivate  int requestRes\nprivate  int priority\npublic  java.lang.String toString()\npublic @java.lang.Override @com.android.internal.util.DataClass.Generated.Member void writeToParcel(android.os.Parcel,int)\nclass ParsedPermissionGroupImpl extends com.android.server.pm.pkg.component.ParsedComponentImpl implements [com.android.internal.pm.pkg.component.ParsedPermissionGroup, android.os.Parcelable]\n@com.android.internal.util.DataClass(genGetters=true, genSetters=true, genBuilder=false, genParcelable=true, genAidl=false)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
