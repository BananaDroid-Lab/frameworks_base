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
package com.android.server.appsearch;

import android.app.appsearch.IAppSearchManager;
import android.content.Context;
import android.os.Binder;
import android.os.UserHandle;

import com.android.internal.infra.AndroidFuture;
import com.android.internal.util.Preconditions;
import com.android.server.SystemService;
import com.android.server.appsearch.impl.AppSearchImpl;
import com.android.server.appsearch.impl.ImplInstanceManager;

import com.google.android.icing.proto.SchemaProto;

/**
 * TODO(b/142567528): add comments when implement this class
 */
public class AppSearchManagerService extends SystemService {

    public AppSearchManagerService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        publishBinderService(Context.APP_SEARCH_SERVICE, new Stub());
    }

    private class Stub extends IAppSearchManager.Stub {
        @Override
        public void setSchema(byte[] schemaBytes, AndroidFuture callback) {
            Preconditions.checkNotNull(schemaBytes);
            Preconditions.checkNotNull(callback);
            int callingUid = Binder.getCallingUidOrThrow();
            int callingUserId = UserHandle.getUserId(callingUid);
            long callingIdentity = Binder.clearCallingIdentity();
            try {
                SchemaProto schema = SchemaProto.parseFrom(schemaBytes);
                AppSearchImpl impl = ImplInstanceManager.getInstance(getContext(), callingUserId);
                impl.setSchema(callingUid, schema);
                callback.complete(null);
            } catch (Throwable t) {
                callback.completeExceptionally(t);
            } finally {
                Binder.restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void put(byte[] documentBytes, AndroidFuture callback) {
            try {
                throw new UnsupportedOperationException("Put document not yet implemented");
            } catch (Throwable t) {
                callback.completeExceptionally(t);
            }
        }
    }
}
