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

package com.android.server.credentials;

import android.Manifest;
import android.annotation.Nullable;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.credentials.CredentialOption;
import android.credentials.CredentialProviderInfo;
import android.credentials.GetCredentialException;
import android.credentials.GetCredentialRequest;
import android.credentials.GetCredentialResponse;
import android.credentials.IGetCredentialCallback;
import android.credentials.IPrepareGetCredentialCallback;
import android.credentials.PrepareGetCredentialResponseInternal;
import android.credentials.ui.GetCredentialProviderData;
import android.credentials.ui.ProviderData;
import android.credentials.ui.RequestInfo;
import android.os.CancellationSignal;
import android.os.RemoteException;
import android.service.credentials.CallingAppInfo;
import android.service.credentials.PermissionUtils;
import android.util.Log;

import com.android.server.credentials.metrics.ProviderStatusForMetrics;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central session for a single prepareGetCredentials request. This class listens to the
 * responses from providers, and the UX app, and updates the provider(S) state.
 */
public class PrepareGetRequestSession extends RequestSession<GetCredentialRequest,
        IGetCredentialCallback, GetCredentialResponse>
        implements ProviderSession.ProviderInternalCallback<GetCredentialResponse> {
    private static final String TAG = "GetRequestSession";

    private final IPrepareGetCredentialCallback mPrepareGetCredentialCallback;
    private boolean mIsInitialQuery = true;

    public PrepareGetRequestSession(Context context, int userId, int callingUid,
            IPrepareGetCredentialCallback prepareGetCredentialCallback,
            IGetCredentialCallback getCredCallback, GetCredentialRequest request,
            CallingAppInfo callingAppInfo, CancellationSignal cancellationSignal,
            long startedTimestamp) {
        super(context, userId, callingUid, request, getCredCallback, RequestInfo.TYPE_GET,
                callingAppInfo, cancellationSignal, startedTimestamp);
        int numTypes = (request.getCredentialOptions().stream()
                .map(CredentialOption::getType).collect(
                        Collectors.toSet())).size(); // Dedupe type strings
        mRequestSessionMetric.collectGetFlowInitialMetricInfo(numTypes);
        mPrepareGetCredentialCallback = prepareGetCredentialCallback;
    }

    /**
     * Creates a new provider session, and adds it list of providers that are contributing to
     * this session.
     *
     * @return the provider session created within this request session, for the given provider
     * info.
     */
    @Override
    @Nullable
    public ProviderSession initiateProviderSession(CredentialProviderInfo providerInfo,
            RemoteCredentialService remoteCredentialService) {
        ProviderGetSession providerGetSession = ProviderGetSession
                .createNewSession(mContext, mUserId, providerInfo,
                        this, remoteCredentialService);
        if (providerGetSession != null) {
            Log.i(TAG, "In startProviderSession - provider session created and being added");
            mProviders.put(providerGetSession.getComponentName().flattenToString(),
                    providerGetSession);
        }
        return providerGetSession;
    }

    @Override
    protected void launchUiWithProviderData(ArrayList<ProviderData> providerDataList) {
        mRequestSessionMetric.collectUiCallStartTime(System.nanoTime());
        try {
            mClientCallback.onPendingIntent(mCredentialManagerUi.createPendingIntent(
                    RequestInfo.newGetRequestInfo(
                            mRequestId, mClientRequest, mClientAppInfo.getPackageName()),
                    providerDataList));
        } catch (RemoteException e) {
            mRequestSessionMetric.collectUiReturnedFinalPhase(/*uiReturned=*/ false);
            respondToClientWithErrorAndFinish(
                    GetCredentialException.TYPE_UNKNOWN, "Unable to instantiate selector");
        }
    }

    @Override
    protected void invokeClientCallbackSuccess(GetCredentialResponse response)
            throws RemoteException {
        mClientCallback.onResponse(response);
    }

    @Override
    protected void invokeClientCallbackError(String errorType, String errorMsg)
            throws RemoteException {
        mClientCallback.onError(errorType, errorMsg);
    }

    @Override
    public void onFinalResponseReceived(ComponentName componentName,
            @Nullable GetCredentialResponse response) {
        Log.i(TAG, "onFinalCredentialReceived from: " + componentName.flattenToString());
        mRequestSessionMetric.collectUiResponseData(/*uiReturned=*/ true, System.nanoTime());
        mRequestSessionMetric.collectChosenMetricViaCandidateTransfer(mProviders.get(
                componentName.flattenToString()).mProviderSessionMetric
                .getCandidatePhasePerProviderMetric());
        if (response != null) {
            mRequestSessionMetric.collectChosenProviderStatus(
                    ProviderStatusForMetrics.FINAL_SUCCESS.getMetricCode());
            respondToClientWithResponseAndFinish(response);
        } else {
            mRequestSessionMetric.collectChosenProviderStatus(
                    ProviderStatusForMetrics.FINAL_FAILURE.getMetricCode());
            respondToClientWithErrorAndFinish(GetCredentialException.TYPE_NO_CREDENTIAL,
                    "Invalid response from provider");
        }
    }

    //TODO: Try moving the three error & response methods below to RequestSession to be shared
    // between get & create.
    @Override
    public void onFinalErrorReceived(ComponentName componentName, String errorType,
            String message) {
        respondToClientWithErrorAndFinish(errorType, message);
    }

    @Override
    public void onUiCancellation(boolean isUserCancellation) {
        if (isUserCancellation) {
            respondToClientWithErrorAndFinish(GetCredentialException.TYPE_USER_CANCELED,
                    "User cancelled the selector");
        } else {
            respondToClientWithErrorAndFinish(GetCredentialException.TYPE_INTERRUPTED,
                    "The UI was interrupted - please try again.");
        }
    }

    @Override
    public void onUiSelectorInvocationFailure() {
        respondToClientWithErrorAndFinish(GetCredentialException.TYPE_NO_CREDENTIAL,
                "No credentials available.");
    }

    @Override
    public void onProviderStatusChanged(ProviderSession.Status status,
            ComponentName componentName) {
        Log.i(TAG, "in onStatusChanged with status: " + status);
        // Auth entry was selected, and it did not have any underlying credentials
        if (status == ProviderSession.Status.NO_CREDENTIALS_FROM_AUTH_ENTRY) {
            handleEmptyAuthenticationSelection(componentName);
            return;
        }
        // For any other status, we check if all providers are done and then invoke UI if needed
        if (!isAnyProviderPending()) {
            // If all provider responses have been received, we can either need the UI,
            // or we need to respond with error. The only other case is the entry being
            // selected after the UI has been invoked which has a separate code path.
            if (mIsInitialQuery) {
                // First time in this state. UI shouldn't be invoked because developer wants to
                // punt it for later
                boolean hasQueryCandidatePermission = PermissionUtils.hasPermission(
                        mContext,
                        mClientAppInfo.getPackageName(),
                        Manifest.permission.CREDENTIAL_MANAGER_QUERY_CANDIDATE_CREDENTIALS);
                if (isUiInvocationNeeded()) {
                    ArrayList<ProviderData> providerData = getProviderDataForUi();
                    if (!providerData.isEmpty()) {
                        constructPendingResponseAndInvokeCallback(hasQueryCandidatePermission,
                                getCredentialResultTypes(hasQueryCandidatePermission),
                                hasAuthenticationResults(providerData, hasQueryCandidatePermission),
                                hasRemoteResults(providerData, hasQueryCandidatePermission),
                                getUiIntent());
                    } else {
                        constructEmptyPendingResponseAndInvokeCallback(hasQueryCandidatePermission);
                    }
                } else {
                    constructEmptyPendingResponseAndInvokeCallback(hasQueryCandidatePermission);
                }
                mIsInitialQuery = false;
            } else {
                // Not the first time. This could be a result of a user selection leading to a UI
                // invocation again.
                if (isUiInvocationNeeded()) {
                    getProviderDataAndInitiateUi();
                } else {
                    respondToClientWithErrorAndFinish(GetCredentialException.TYPE_NO_CREDENTIAL,
                            "No credentials available");
                }
            }
        }
    }

    private void constructPendingResponseAndInvokeCallback(boolean hasPermission,
            Set<String> credentialTypes,
            boolean hasAuthenticationResults, boolean hasRemoteResults, PendingIntent uiIntent) {
        try {
            mPrepareGetCredentialCallback.onResponse(
                    new PrepareGetCredentialResponseInternal(
                            hasPermission,
                            credentialTypes, hasAuthenticationResults, hasRemoteResults, uiIntent));
        } catch (RemoteException e) {
            Log.e(TAG, "EXCEPTION while mPendingCallback.onResponse", e);
        }
    }

    private void constructEmptyPendingResponseAndInvokeCallback(
            boolean hasQueryCandidatePermission) {
        try {
            mPrepareGetCredentialCallback.onResponse(
                    new PrepareGetCredentialResponseInternal(
                            hasQueryCandidatePermission,
                            /*credentialResultTypes=*/ null,
                            /*hasAuthenticationResults=*/false,
                            /*hasRemoteResults=*/ false,
                            /*pendingIntent=*/ null));
        } catch (RemoteException e) {
            Log.e(TAG, "EXCEPTION while mPendingCallback.onResponse", e);
        }
    }

    private boolean hasRemoteResults(ArrayList<ProviderData> providerData,
            boolean hasQueryCandidatePermission) {
        if (!hasQueryCandidatePermission) {
            return false;
        }
        return providerData.stream()
                .map(data -> (GetCredentialProviderData) data)
                .anyMatch(getCredentialProviderData ->
                        getCredentialProviderData.getRemoteEntry() != null);
    }

    private boolean hasAuthenticationResults(ArrayList<ProviderData> providerData,
            boolean hasQueryCandidatePermission) {
        if (!hasQueryCandidatePermission) {
            return false;
        }
        return providerData.stream()
                .map(data -> (GetCredentialProviderData) data)
                .anyMatch(getCredentialProviderData ->
                        !getCredentialProviderData.getAuthenticationEntries().isEmpty());
    }

    @Nullable
    private Set<String> getCredentialResultTypes(boolean hasQueryCandidatePermission) {
        if (!hasQueryCandidatePermission) {
            return null;
        }
        return mProviders.values().stream()
                .map(session -> (ProviderGetSession) session)
                .flatMap(providerGetSession -> providerGetSession
                        .getCredentialEntryTypes().stream())
                .collect(Collectors.toSet());
    }

    private PendingIntent getUiIntent() {
        ArrayList<ProviderData> providerDataList = new ArrayList<>();
        for (ProviderSession session : mProviders.values()) {
            Log.i(TAG, "preparing data for : " + session.getComponentName());
            ProviderData providerData = session.prepareUiData();
            if (providerData != null) {
                Log.i(TAG, "Provider data is not null");
                providerDataList.add(providerData);
            }
        }
        if (!providerDataList.isEmpty()) {
            return mCredentialManagerUi.createPendingIntent(
                    RequestInfo.newGetRequestInfo(
                            mRequestId, mClientRequest, mClientAppInfo.getPackageName()),
                    providerDataList);
        } else {
            return null;
        }
    }

    private void handleEmptyAuthenticationSelection(ComponentName componentName) {
        // Update auth entry statuses across different provider sessions
        mProviders.keySet().forEach(key -> {
            ProviderGetSession session = (ProviderGetSession) mProviders.get(key);
            if (!session.mComponentName.equals(componentName)) {
                session.updateAuthEntriesStatusFromAnotherSession();
            }
        });

        // Invoke UI since it needs to show a snackbar if last auth entry, or a status on each
        // auth entries along with other valid entries
        getProviderDataAndInitiateUi();

        // Respond to client if all auth entries are empty and nothing else to show on the UI
        if (providerDataContainsEmptyAuthEntriesOnly()) {
            respondToClientWithErrorAndFinish(GetCredentialException.TYPE_NO_CREDENTIAL,
                    "No credentials available");
        }
    }

    private boolean providerDataContainsEmptyAuthEntriesOnly() {
        for (String key : mProviders.keySet()) {
            ProviderGetSession session = (ProviderGetSession) mProviders.get(key);
            if (!session.containsEmptyAuthEntriesOnly()) {
                return false;
            }
        }
        return true;
    }
}
