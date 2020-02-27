/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.autofill;

import static android.service.autofill.augmented.Helper.logResponse;

import static com.android.server.autofill.Helper.sDebug;
import static com.android.server.autofill.Helper.sVerbose;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.AppGlobals;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.RemoteException;
import android.os.SystemClock;
import android.service.autofill.Dataset;
import android.service.autofill.augmented.AugmentedAutofillService;
import android.service.autofill.augmented.IAugmentedAutofillService;
import android.service.autofill.augmented.IFillCallback;
import android.util.Pair;
import android.util.Slog;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.view.autofill.IAutoFillManagerClient;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.view.inputmethod.InlineSuggestionsResponse;

import com.android.internal.infra.AbstractRemoteService;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.infra.ServiceConnector;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.os.IResultReceiver;
import com.android.internal.util.ArrayUtils;
import com.android.internal.view.IInlineSuggestionsResponseCallback;
import com.android.server.autofill.ui.InlineSuggestionFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

final class RemoteAugmentedAutofillService
        extends ServiceConnector.Impl<IAugmentedAutofillService> {

    private static final String TAG = RemoteAugmentedAutofillService.class.getSimpleName();

    private final int mIdleUnbindTimeoutMs;
    private final int mRequestTimeoutMs;
    private final ComponentName mComponentName;
    private final RemoteAugmentedAutofillServiceCallbacks mCallbacks;

    RemoteAugmentedAutofillService(Context context, ComponentName serviceName,
            int userId, RemoteAugmentedAutofillServiceCallbacks callbacks,
            boolean bindInstantServiceAllowed, boolean verbose, int idleUnbindTimeoutMs,
            int requestTimeoutMs) {
        super(context,
                new Intent(AugmentedAutofillService.SERVICE_INTERFACE).setComponent(serviceName),
                bindInstantServiceAllowed ? Context.BIND_ALLOW_INSTANT : 0,
                userId, IAugmentedAutofillService.Stub::asInterface);
        mIdleUnbindTimeoutMs = idleUnbindTimeoutMs;
        mRequestTimeoutMs = requestTimeoutMs;
        mComponentName = serviceName;
        mCallbacks = callbacks;

        // Bind right away.
        connect();
    }

    @Nullable
    static Pair<ServiceInfo, ComponentName> getComponentName(@NonNull String componentName,
            @UserIdInt int userId, boolean isTemporary) {
        int flags = PackageManager.GET_META_DATA;
        if (!isTemporary) {
            flags |= PackageManager.MATCH_SYSTEM_ONLY;
        }

        final ComponentName serviceComponent;
        ServiceInfo serviceInfo = null;
        try {
            serviceComponent = ComponentName.unflattenFromString(componentName);
            serviceInfo = AppGlobals.getPackageManager().getServiceInfo(serviceComponent, flags,
                    userId);
            if (serviceInfo == null) {
                Slog.e(TAG, "Bad service name for flags " + flags + ": " + componentName);
                return null;
            }
        } catch (Exception e) {
            Slog.e(TAG, "Error getting service info for '" + componentName + "': " + e);
            return null;
        }
        return new Pair<>(serviceInfo, serviceComponent);
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    @Override // from ServiceConnector.Impl
    protected void onServiceConnectionStatusChanged(
            IAugmentedAutofillService service, boolean connected) {
        try {
            if (connected) {
                service.onConnected(sDebug, sVerbose);
            } else {
                service.onDisconnected();
            }
        } catch (Exception e) {
            Slog.w(TAG,
                    "Exception calling onServiceConnectionStatusChanged(" + connected + "): ", e);
        }
    }

    @Override // from AbstractRemoteService
    protected long getAutoDisconnectTimeoutMs() {
        return mIdleUnbindTimeoutMs;
    }

    /**
     * Called by {@link Session} to request augmented autofill.
     */
    public void onRequestAutofillLocked(int sessionId, @NonNull IAutoFillManagerClient client,
            int taskId, @NonNull ComponentName activityComponent, @NonNull AutofillId focusedId,
            @Nullable AutofillValue focusedValue,
            @Nullable InlineSuggestionsRequest inlineSuggestionsRequest,
            @Nullable IInlineSuggestionsResponseCallback inlineSuggestionsCallback,
            @NonNull Runnable onErrorCallback,
            @NonNull RemoteInlineSuggestionRenderService remoteRenderService) {
        long requestTime = SystemClock.elapsedRealtime();
        AtomicReference<ICancellationSignal> cancellationRef = new AtomicReference<>();

        postAsync(service -> {
            AndroidFuture<Void> requestAutofill = new AndroidFuture<>();
            // TODO(b/122728762): set cancellation signal, timeout (from both client and service),
            // cache IAugmentedAutofillManagerClient reference, etc...
            client.getAugmentedAutofillClient(new IResultReceiver.Stub() {
                @Override
                public void send(int resultCode, Bundle resultData) throws RemoteException {
                    final IBinder realClient = resultData
                            .getBinder(AutofillManager.EXTRA_AUGMENTED_AUTOFILL_CLIENT);
                    service.onFillRequest(sessionId, realClient, taskId, activityComponent,
                            focusedId, focusedValue, requestTime, inlineSuggestionsRequest,
                            new IFillCallback.Stub() {
                                @Override
                                public void onSuccess(@Nullable Dataset[] inlineSuggestionsData) {
                                    mCallbacks.resetLastResponse();
                                    maybeRequestShowInlineSuggestions(sessionId,
                                            inlineSuggestionsRequest, inlineSuggestionsData,
                                            focusedId, inlineSuggestionsCallback, client,
                                            onErrorCallback, remoteRenderService);
                                    requestAutofill.complete(null);
                                }

                                @Override
                                public boolean isCompleted() {
                                    return requestAutofill.isDone()
                                            && !requestAutofill.isCancelled();
                                }

                                @Override
                                public void onCancellable(ICancellationSignal cancellation) {
                                    if (requestAutofill.isCancelled()) {
                                        dispatchCancellation(cancellation);
                                    } else {
                                        cancellationRef.set(cancellation);
                                    }
                                }

                                @Override
                                public void cancel() {
                                    requestAutofill.cancel(true);
                                }
                            });
                }
            });
            return requestAutofill;
        }).orTimeout(mRequestTimeoutMs, TimeUnit.MILLISECONDS)
                .whenComplete((res, err) -> {
                    if (err instanceof CancellationException) {
                        dispatchCancellation(cancellationRef.get());
                    } else if (err instanceof TimeoutException) {
                        Slog.w(TAG, "PendingAutofillRequest timed out (" + mRequestTimeoutMs
                                + "ms) for " + RemoteAugmentedAutofillService.this);
                        // NOTE: so far we don't need notify RemoteAugmentedAutofillServiceCallbacks
                        dispatchCancellation(cancellationRef.get());
                        if (mComponentName != null) {
                            logResponse(MetricsEvent.TYPE_ERROR, mComponentName.getPackageName(),
                                    activityComponent, sessionId, mRequestTimeoutMs);
                        }
                    } else if (err != null) {
                        Slog.e(TAG, "exception handling getAugmentedAutofillClient() for "
                                + sessionId + ": ", err);
                    } else {
                        // NOTE: so far we don't need notify RemoteAugmentedAutofillServiceCallbacks
                    }
                });
    }

    void dispatchCancellation(@Nullable ICancellationSignal cancellation) {
        if (cancellation == null) {
            return;
        }
        Handler.getMain().post(() -> {
            try {
                cancellation.cancel();
            } catch (RemoteException e) {
                Slog.e(TAG, "Error requesting a cancellation", e);
            }
        });
    }

    private void maybeRequestShowInlineSuggestions(int sessionId,
            @Nullable InlineSuggestionsRequest request, @Nullable Dataset[] inlineSuggestionsData,
            @NonNull AutofillId focusedId,
            @Nullable IInlineSuggestionsResponseCallback inlineSuggestionsCallback,
            @NonNull IAutoFillManagerClient client, @NonNull Runnable onErrorCallback,
            @NonNull RemoteInlineSuggestionRenderService remoteRenderService) {
        if (ArrayUtils.isEmpty(inlineSuggestionsData) || inlineSuggestionsCallback == null
                || request == null) {
            return;
        }
        mCallbacks.setLastResponse(sessionId);

        final InlineSuggestionsResponse inlineSuggestionsResponse =
                InlineSuggestionFactory.createAugmentedInlineSuggestionsResponse(
                        request, inlineSuggestionsData, focusedId, mContext,
                        dataset -> {
                            mCallbacks.logAugmentedAutofillSelected(sessionId,
                                    dataset.getId());
                            try {
                                client.autofill(sessionId, dataset.getFieldIds(),
                                        dataset.getFieldValues());
                            } catch (RemoteException e) {
                                Slog.w(TAG, "Encounter exception autofilling the values");
                            }
                        }, onErrorCallback, remoteRenderService);

        if (inlineSuggestionsResponse == null) {
            Slog.w(TAG, "InlineSuggestionFactory created null response");
            return;
        }

        try {
            inlineSuggestionsCallback.onInlineSuggestionsResponse(inlineSuggestionsResponse);
        } catch (RemoteException e) {
            Slog.w(TAG, "Exception sending inline suggestions response back to IME.");
        }

        mCallbacks.logAugmentedAutofillShown(sessionId);
    }

    @Override
    public String toString() {
        return "RemoteAugmentedAutofillService["
                + ComponentName.flattenToShortString(mComponentName) + "]";
    }

    /**
     * Called by {@link Session} when it's time to destroy all augmented autofill requests.
     */
    public void onDestroyAutofillWindowsRequest() {
        run((s) -> s.onDestroyAllFillWindowsRequest());
    }

    public interface RemoteAugmentedAutofillServiceCallbacks
            extends AbstractRemoteService.VultureCallback<RemoteAugmentedAutofillService> {
        void resetLastResponse();

        void setLastResponse(int sessionId);

        void logAugmentedAutofillShown(int sessionId);

        void logAugmentedAutofillSelected(int sessionId, @Nullable String suggestionId);
    }
}
