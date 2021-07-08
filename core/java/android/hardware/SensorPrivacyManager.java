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

package android.hardware;

import android.Manifest;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.annotation.TestApi;
import android.annotation.UserIdInt;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.service.SensorPrivacyIndividualEnabledSensorProto;
import android.util.ArrayMap;
import android.util.Pair;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;

/**
 * This class provides information about the microphone and camera toggles.
 */
@SystemService(Context.SENSOR_PRIVACY_SERVICE)
public final class SensorPrivacyManager {

    /**
     * Unique Id of this manager to identify to the service
     * @hide
     */
    private IBinder token = new Binder();

    /**
     * An extra containing a sensor
     * @hide
     */
    public static final String EXTRA_SENSOR = SensorPrivacyManager.class.getName()
            + ".extra.sensor";

    /**
     * An extra indicating if all sensors are affected
     * @hide
     */
    public static final String EXTRA_ALL_SENSORS = SensorPrivacyManager.class.getName()
            + ".extra.all_sensors";

    private final SparseArray<Boolean> mToggleSupportCache = new SparseArray<>();

    /**
     * Individual sensors not listed in {@link Sensors}
     */
    public static class Sensors {

        private Sensors() {}

        /**
         * Constant for the microphone
         */
        public static final int MICROPHONE = SensorPrivacyIndividualEnabledSensorProto.MICROPHONE;

        /**
         * Constant for the camera
         */
        public static final int CAMERA = SensorPrivacyIndividualEnabledSensorProto.CAMERA;

        /**
         * Individual sensors not listed in {@link Sensors}
         *
         * @hide
         */
        @IntDef(value = {
                MICROPHONE,
                CAMERA
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface Sensor {}
    }

    /**
     * A class implementing this interface can register with the {@link
     * android.hardware.SensorPrivacyManager} to receive notification when the sensor privacy
     * state changes.
     *
     * @hide
     */
    @SystemApi
    public interface OnSensorPrivacyChangedListener {
        /**
         * Callback invoked when the sensor privacy state changes.
         *
         * @param sensor the sensor whose state is changing
         * @param enabled true if sensor privacy is enabled, false otherwise.
         */
        void onSensorPrivacyChanged(int sensor, boolean enabled);
    }

    private static final Object sInstanceLock = new Object();

    @GuardedBy("sInstanceLock")
    private static SensorPrivacyManager sInstance;

    @NonNull
    private final Context mContext;

    @NonNull
    private final ISensorPrivacyManager mService;

    @NonNull
    private final ArrayMap<OnAllSensorPrivacyChangedListener, ISensorPrivacyListener> mListeners;

    @NonNull
    private final ArrayMap<Pair<OnSensorPrivacyChangedListener, Integer>, ISensorPrivacyListener>
            mIndividualListeners;

    /**
     * Private constructor to ensure only a single instance is created.
     */
    private SensorPrivacyManager(Context context, ISensorPrivacyManager service) {
        mContext = context;
        mService = service;
        mListeners = new ArrayMap<>();
        mIndividualListeners = new ArrayMap<>();
    }

    /**
     * Returns the single instance of the SensorPrivacyManager.
     *
     * @hide
     */
    public static SensorPrivacyManager getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                try {
                    IBinder b = ServiceManager.getServiceOrThrow(Context.SENSOR_PRIVACY_SERVICE);
                    ISensorPrivacyManager service = ISensorPrivacyManager.Stub.asInterface(b);
                    sInstance = new SensorPrivacyManager(context, service);
                } catch (ServiceManager.ServiceNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }
            return sInstance;
        }
    }

    /**
     * Checks if the given toggle is supported on this device
     * @param sensor The sensor to check
     * @return whether the toggle for the sensor is supported on this device.
     */
    public boolean supportsSensorToggle(@Sensors.Sensor int sensor) {
        try {
            Boolean val = mToggleSupportCache.get(sensor);
            if (val == null) {
                val = mService.supportsSensorToggle(sensor);
                mToggleSupportCache.put(sensor, val);
            }
            return val;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Registers a new listener to receive notification when the state of sensor privacy
     * changes.
     *
     * @param sensor the sensor to listen to changes to
     * @param listener the OnSensorPrivacyChangedListener to be notified when the state of sensor
     *                 privacy changes.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.OBSERVE_SENSOR_PRIVACY)
    public void addSensorPrivacyListener(@Sensors.Sensor int sensor,
            @NonNull OnSensorPrivacyChangedListener listener) {
        addSensorPrivacyListener(sensor, mContext.getUserId(), mContext.getMainExecutor(),
                listener);
    }

    /**
     * Registers a new listener to receive notification when the state of sensor privacy
     * changes.
     *
     * @param sensor the sensor to listen to changes to
     * @param userId the user's id
     * @param listener the OnSensorPrivacyChangedListener to be notified when the state of sensor
     *                 privacy changes.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.OBSERVE_SENSOR_PRIVACY)
    public void addSensorPrivacyListener(@Sensors.Sensor int sensor, @UserIdInt int userId,
            @NonNull OnSensorPrivacyChangedListener listener) {
        addSensorPrivacyListener(sensor, userId, mContext.getMainExecutor(), listener);
    }

    /**
     * Registers a new listener to receive notification when the state of sensor privacy
     * changes.
     *
     * @param sensor the sensor to listen to changes to
     * @param executor the executor to dispatch the callback on
     * @param listener the OnSensorPrivacyChangedListener to be notified when the state of sensor
     *                 privacy changes.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.OBSERVE_SENSOR_PRIVACY)
    public void addSensorPrivacyListener(@Sensors.Sensor int sensor, @NonNull Executor executor,
            @NonNull OnSensorPrivacyChangedListener listener) {
        addSensorPrivacyListener(sensor, mContext.getUserId(), executor, listener);
    }

    /**
     * Registers a new listener to receive notification when the state of sensor privacy
     * changes.
     *
     * @param sensor the sensor to listen to changes to
     * @param executor the executor to dispatch the callback on
     * @param userId the user's id
     * @param listener the OnSensorPrivacyChangedListener to be notified when the state of sensor
     *                 privacy changes.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.OBSERVE_SENSOR_PRIVACY)
    public void addSensorPrivacyListener(@Sensors.Sensor int sensor, @UserIdInt int userId,
            @NonNull Executor executor, @NonNull OnSensorPrivacyChangedListener listener) {
        synchronized (mIndividualListeners) {
            ISensorPrivacyListener iListener = mIndividualListeners.get(listener);
            if (iListener == null) {
                iListener = new ISensorPrivacyListener.Stub() {
                    @Override
                    public void onSensorPrivacyChanged(boolean enabled) {
                        executor.execute(() -> listener.onSensorPrivacyChanged(sensor, enabled));
                    }
                };
                mIndividualListeners.put(new Pair<>(listener, sensor), iListener);
            }

            try {
                mService.addIndividualSensorPrivacyListener(userId, sensor,
                        iListener);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * Unregisters the specified listener from receiving notifications when the state of any sensor
     * privacy changes.
     *
     * @param listener the OnSensorPrivacyChangedListener to be unregistered from notifications when
     *                 sensor privacy changes.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.OBSERVE_SENSOR_PRIVACY)
    public void removeSensorPrivacyListener(@Sensors.Sensor int sensor,
            @NonNull OnSensorPrivacyChangedListener listener) {
        synchronized (mListeners) {
            for (int i = 0; i < mIndividualListeners.size(); i++) {
                Pair<OnSensorPrivacyChangedListener, Integer> pair = mIndividualListeners.keyAt(i);
                if (pair.second == sensor && pair.first.equals(listener)) {
                    try {
                        mService.removeIndividualSensorPrivacyListener(sensor,
                                mIndividualListeners.valueAt(i));
                    } catch (RemoteException e) {
                        throw e.rethrowFromSystemServer();
                    }
                    mIndividualListeners.removeAt(i--);
                }
            }
        }
    }

    /**
     * Returns whether sensor privacy is currently enabled for a specific sensor.
     *
     * @return true if sensor privacy is currently enabled, false otherwise.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.OBSERVE_SENSOR_PRIVACY)
    public boolean isSensorPrivacyEnabled(@Sensors.Sensor int sensor) {
        return isSensorPrivacyEnabled(sensor, mContext.getUserId());
    }

    /**
     * Returns whether sensor privacy is currently enabled for a specific sensor.
     *
     * @return true if sensor privacy is currently enabled, false otherwise.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.OBSERVE_SENSOR_PRIVACY)
    public boolean isSensorPrivacyEnabled(@Sensors.Sensor int sensor, @UserIdInt int userId) {
        try {
            return mService.isIndividualSensorPrivacyEnabled(userId, sensor);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets sensor privacy to the specified state for an individual sensor.
     *
     * @param sensor the sensor which to change the state for
     * @param enable the state to which sensor privacy should be set.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Manifest.permission.MANAGE_SENSOR_PRIVACY)
    public void setSensorPrivacy(@Sensors.Sensor int sensor, boolean enable) {
        setSensorPrivacy(sensor, enable, mContext.getUserId());
    }

    /**
     * Sets sensor privacy to the specified state for an individual sensor.
     *
     * @param sensor the sensor which to change the state for
     * @param enable the state to which sensor privacy should be set.
     * @param userId the user's id
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.MANAGE_SENSOR_PRIVACY)
    public void setSensorPrivacy(@Sensors.Sensor int sensor, boolean enable,
            @UserIdInt int userId) {
        try {
            mService.setIndividualSensorPrivacy(userId, sensor, enable);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets sensor privacy to the specified state for an individual sensor for the profile group of
     * context's user.
     *
     * @param sensor the sensor which to change the state for
     * @param enable the state to which sensor privacy should be set.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Manifest.permission.MANAGE_SENSOR_PRIVACY)
    public void setSensorPrivacyForProfileGroup(@Sensors.Sensor int sensor,
            boolean enable) {
        setSensorPrivacyForProfileGroup(sensor, enable, mContext.getUserId());
    }

    /**
     * Sets sensor privacy to the specified state for an individual sensor for the profile group of
     * context's user.
     *
     * @param sensor the sensor which to change the state for
     * @param enable the state to which sensor privacy should be set.
     * @param userId the user's id
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.MANAGE_SENSOR_PRIVACY)
    public void setSensorPrivacyForProfileGroup(@Sensors.Sensor int sensor,
            boolean enable, @UserIdInt int userId) {
        try {
            mService.setIndividualSensorPrivacyForProfileGroup(userId, sensor,
                    enable);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Don't show dialogs to turn off sensor privacy for this package.
     *
     * @param packageName Package name not to show dialogs for
     * @param suppress Whether to suppress or re-enable.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.MANAGE_SENSOR_PRIVACY)
    public void suppressSensorPrivacyReminders(@NonNull String packageName,
            boolean suppress) {
        suppressSensorPrivacyReminders(packageName, suppress, mContext.getUserId());
    }

    /**
     * Don't show dialogs to turn off sensor privacy for this package.
     *
     * @param packageName Package name not to show dialogs for
     * @param suppress Whether to suppress or re-enable.
     * @param userId the user's id
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.MANAGE_SENSOR_PRIVACY)
    public void suppressSensorPrivacyReminders(@NonNull String packageName,
            boolean suppress, @UserIdInt int userId) {
        try {
            mService.suppressIndividualSensorPrivacyReminders(userId, packageName,
                    token, suppress);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * A class implementing this interface can register with the {@link
     * android.hardware.SensorPrivacyManager} to receive notification when the all-sensor privacy
     * state changes.
     *
     * @hide
     */
    public interface OnAllSensorPrivacyChangedListener {
        /**
         * Callback invoked when the sensor privacy state changes.
         *
         * @param enabled true if sensor privacy is enabled, false otherwise.
         */
        void onAllSensorPrivacyChanged(boolean enabled);
    }

    /**
     * Sets all-sensor privacy to the specified state.
     *
     * @param enable the state to which sensor privacy should be set.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.MANAGE_SENSOR_PRIVACY)
    public void setAllSensorPrivacy(boolean enable) {
        try {
            mService.setSensorPrivacy(enable);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Registers a new listener to receive notification when the state of all-sensor privacy
     * changes.
     *
     * @param listener the OnSensorPrivacyChangedListener to be notified when the state of
     *                 all-sensor privacy changes.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.OBSERVE_SENSOR_PRIVACY)
    public void addAllSensorPrivacyListener(
            @NonNull final OnAllSensorPrivacyChangedListener listener) {
        synchronized (mListeners) {
            ISensorPrivacyListener iListener = mListeners.get(listener);
            if (iListener == null) {
                iListener = new ISensorPrivacyListener.Stub() {
                    @Override
                    public void onSensorPrivacyChanged(boolean enabled) {
                        listener.onAllSensorPrivacyChanged(enabled);
                    }
                };
                mListeners.put(listener, iListener);
            }

            try {
                mService.addSensorPrivacyListener(iListener);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * Unregisters the specified listener from receiving notifications when the state of all-sensor
     * privacy changes.
     *
     * @param listener the OnAllSensorPrivacyChangedListener to be unregistered from notifications
     *                 when all-sensor privacy changes.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.OBSERVE_SENSOR_PRIVACY)
    public void removeAllSensorPrivacyListener(
            @NonNull OnAllSensorPrivacyChangedListener listener) {
        synchronized (mListeners) {
            ISensorPrivacyListener iListener = mListeners.get(listener);
            if (iListener != null) {
                mListeners.remove(iListener);
                try {
                    mService.removeSensorPrivacyListener(iListener);
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
        }
    }

    /**
     * Returns whether all-sensor privacy is currently enabled.
     *
     * @return true if all-sensor privacy is currently enabled, false otherwise.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.OBSERVE_SENSOR_PRIVACY)
    public boolean isAllSensorPrivacyEnabled() {
        try {
            return mService.isSensorPrivacyEnabled();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
