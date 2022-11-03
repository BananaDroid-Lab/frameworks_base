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

import android.content.ComponentName;
import android.content.Intent;
import android.os.Parcel;
import android.os.ResultReceiver;

import java.util.ArrayList;

/**
 * Helpers for generating the intents and related extras parameters to launch the UI activities.
 *
 * @hide
 */
public class IntentFactory {
    /** Generate a new launch intent to the . */
    public static Intent newIntent(
            RequestInfo requestInfo,
            ArrayList<ProviderData> enabledProviderDataList,
            ArrayList<DisabledProviderData> disabledProviderDataList,
            ResultReceiver resultReceiver) {
        Intent intent = new Intent();
        // TODO: define these as proper config strings.
        String activityName = "com.android.credentialmanager/.CredentialSelectorActivity";
        intent.setComponent(ComponentName.unflattenFromString(activityName));

        intent.putParcelableArrayListExtra(
                ProviderData.EXTRA_ENABLED_PROVIDER_DATA_LIST, enabledProviderDataList);
        intent.putParcelableArrayListExtra(
                ProviderData.EXTRA_DISABLED_PROVIDER_DATA_LIST, disabledProviderDataList);
        intent.putExtra(RequestInfo.EXTRA_REQUEST_INFO, requestInfo);
        intent.putExtra(Constants.EXTRA_RESULT_RECEIVER,
                toIpcFriendlyResultReceiver(resultReceiver));

        return intent;
    }

    /**
    * Convert an instance of a "locally-defined" ResultReceiver to an instance of
    * {@link android.os.ResultReceiver} itself, which the receiving process will be able to
    * unmarshall.
    */
    private static <T extends ResultReceiver> ResultReceiver toIpcFriendlyResultReceiver(
            T resultReceiver) {
        final Parcel parcel = Parcel.obtain();
        resultReceiver.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        final ResultReceiver ipcFriendly = ResultReceiver.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        return ipcFriendly;
    }

    private IntentFactory() {}
}
