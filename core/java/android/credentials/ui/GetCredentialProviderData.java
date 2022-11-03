/*
 * Copyright 2022 The Android Open Source Project
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

package android.credentials.ui;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.AnnotationValidations;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-provider metadata and entries for the get-credential flow.
 *
 * @hide
 */
public class GetCredentialProviderData extends ProviderData implements Parcelable {
    @NonNull
    private final List<Entry> mCredentialEntries;
    @NonNull
    private final List<Entry> mActionChips;
    @Nullable
    private final Entry mAuthenticationEntry;
    @Nullable
    private final Entry mRemoteEntry;

    public GetCredentialProviderData(
            @NonNull String providerFlattenedComponentName, @NonNull List<Entry> credentialEntries,
            @NonNull List<Entry> actionChips, @Nullable Entry authenticationEntry,
            @Nullable Entry remoteEntry) {
        super(providerFlattenedComponentName);
        mCredentialEntries = credentialEntries;
        mActionChips = actionChips;
        mAuthenticationEntry = authenticationEntry;
        mRemoteEntry = remoteEntry;
    }

    @NonNull
    public List<Entry> getCredentialEntries() {
        return mCredentialEntries;
    }

    @NonNull
    public List<Entry> getActionChips() {
        return mActionChips;
    }

    @Nullable
    public Entry getAuthenticationEntry() {
        return mAuthenticationEntry;
    }

    @Nullable
    public Entry getRemoteEntry() {
        return mRemoteEntry;
    }

    protected GetCredentialProviderData(@NonNull Parcel in) {
        super(in);

        List<Entry> credentialEntries = new ArrayList<>();
        in.readTypedList(credentialEntries, Entry.CREATOR);
        mCredentialEntries = credentialEntries;
        AnnotationValidations.validate(NonNull.class, null, mCredentialEntries);

        List<Entry> actionChips  = new ArrayList<>();
        in.readTypedList(actionChips, Entry.CREATOR);
        mActionChips = actionChips;
        AnnotationValidations.validate(NonNull.class, null, mActionChips);

        Entry authenticationEntry = in.readTypedObject(Entry.CREATOR);
        mAuthenticationEntry = authenticationEntry;

        Entry remoteEntry = in.readTypedObject(Entry.CREATOR);
        mRemoteEntry = remoteEntry;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeTypedList(mCredentialEntries);
        dest.writeTypedList(mActionChips);
        dest.writeTypedObject(mAuthenticationEntry, flags);
        dest.writeTypedObject(mRemoteEntry, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final @NonNull Creator<GetCredentialProviderData> CREATOR =
            new Creator<GetCredentialProviderData>() {
        @Override
        public GetCredentialProviderData createFromParcel(@NonNull Parcel in) {
            return new GetCredentialProviderData(in);
        }

        @Override
        public GetCredentialProviderData[] newArray(int size) {
            return new GetCredentialProviderData[size];
        }
    };

    /**
     * Builder for {@link GetCredentialProviderData}.
     *
     * @hide
     */
    public static class Builder {
        private @NonNull String mProviderFlattenedComponentName;
        private @NonNull List<Entry> mCredentialEntries = new ArrayList<>();
        private @NonNull List<Entry> mActionChips = new ArrayList<>();
        private @Nullable Entry mAuthenticationEntry = null;
        private @Nullable Entry mRemoteEntry = null;

        /** Constructor with required properties. */
        public Builder(@NonNull String providerFlattenedComponentName) {
            mProviderFlattenedComponentName = providerFlattenedComponentName;
        }

        /** Sets the list of save / get credential entries to be displayed to the user. */
        @NonNull
        public Builder setCredentialEntries(@NonNull List<Entry> credentialEntries) {
            mCredentialEntries = credentialEntries;
            return this;
        }

        /** Sets the list of action chips to be displayed to the user. */
        @NonNull
        public Builder setActionChips(@NonNull List<Entry> actionChips) {
            mActionChips = actionChips;
            return this;
        }

        /** Sets the authentication entry to be displayed to the user. */
        @NonNull
        public Builder setAuthenticationEntry(@Nullable Entry authenticationEntry) {
            mAuthenticationEntry = authenticationEntry;
            return this;
        }

        /** Sets the remote entry to be displayed to the user. */
        @NonNull
        public Builder setRemoteEntry(@Nullable Entry remoteEntry) {
            mRemoteEntry = remoteEntry;
            return this;
        }

        /** Builds a {@link GetCredentialProviderData}. */
        @NonNull
        public GetCredentialProviderData build() {
            return new GetCredentialProviderData(mProviderFlattenedComponentName,
                    mCredentialEntries, mActionChips, mAuthenticationEntry, mRemoteEntry);
        }
    }
}
