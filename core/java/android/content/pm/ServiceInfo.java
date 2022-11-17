/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.content.pm;

import android.Manifest;
import android.annotation.IntDef;
import android.annotation.RequiresPermission;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Printer;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Information you can retrieve about a particular application
 * service. This corresponds to information collected from the
 * AndroidManifest.xml's &lt;service&gt; tags.
 */
public class ServiceInfo extends ComponentInfo
        implements Parcelable {
    /**
     * Optional name of a permission required to be able to access this
     * Service.  From the "permission" attribute.
     */
    public String permission;

    /**
     * Bit in {@link #flags}: If set, the service will automatically be
     * stopped by the system if the user removes a task that is rooted
     * in one of the application's activities.  Set from the
     * {@link android.R.attr#stopWithTask} attribute.
     */
    public static final int FLAG_STOP_WITH_TASK = 0x0001;

    /**
     * Bit in {@link #flags}: If set, the service will run in its own
     * isolated process.  Set from the
     * {@link android.R.attr#isolatedProcess} attribute.
     */
    public static final int FLAG_ISOLATED_PROCESS = 0x0002;

    /**
     * Bit in {@link #flags}: If set, the service can be bound and run in the
     * calling application's package, rather than the package in which it is
     * declared.  Set from {@link android.R.attr#externalService} attribute.
     */
    public static final int FLAG_EXTERNAL_SERVICE = 0x0004;

    /**
     * Bit in {@link #flags}: If set, the service (which must be isolated)
     * will be spawned from an Application Zygote, instead of the regular Zygote.
     * The Application Zygote will pre-initialize the application's class loader,
     * and call a static callback into the application to allow it to perform
     * application-specific preloads (such as loading a shared library). Therefore,
     * spawning from the Application Zygote will typically reduce the service
     * launch time and reduce its memory usage. The downside of using this flag
     * is that you will have an additional process (the app zygote itself) that
     * is taking up memory. Whether actual memory usage is improved therefore
     * strongly depends on the number of isolated services that an application
     * starts, and how much memory those services save by preloading. Therefore,
     * it is recommended to measure memory usage under typical workloads to
     * determine whether it makes sense to use this flag.
     */
    public static final int FLAG_USE_APP_ZYGOTE = 0x0008;

    /**
     * Bit in {@link #flags} indicating if the service is visible to ephemeral applications.
     * @hide
     */
    public static final int FLAG_VISIBLE_TO_INSTANT_APP = 0x100000;

    /**
     * Bit in {@link #flags}: If set, a single instance of the service will
     * run for all users on the device.  Set from the
     * {@link android.R.attr#singleUser} attribute.
     */
    public static final int FLAG_SINGLE_USER = 0x40000000;

    /**
     * Options that have been set in the service declaration in the
     * manifest.
     * These include:
     * {@link #FLAG_STOP_WITH_TASK}, {@link #FLAG_ISOLATED_PROCESS},
     * {@link #FLAG_SINGLE_USER}.
     */
    public int flags;

    /**
     * The default foreground service type if not been set in manifest file.
     *
     * <p>Apps targeting API level {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE} and
     * later should NOT use this type,
     * calling {@link android.app.Service#startForeground(int, android.app.Notification, int)} with
     * this type will get a {@link android.app.ForegroundServiceTypeNotAllowedException}.</p>
     *
     * @deprecated Do not use.
     */
    @Deprecated
    public static final int FOREGROUND_SERVICE_TYPE_NONE = 0;

    /**
     * Constant corresponding to <code>dataSync</code> in
     * the {@link android.R.attr#foregroundServiceType} attribute.
     * Data(photo, file, account) upload/download, backup/restore, import/export, fetch,
     * transfer over network between device and cloud.
     *
     * <p>Apps targeting API level {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE} and
     * later should NOT use this type:
     * calling {@link android.app.Service#startForeground(int, android.app.Notification, int)} with
     * this type on devices running {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE} is still
     * allowed, but calling it with this type on devices running future platform releases may get a
     * {@link android.app.ForegroundServiceTypeNotAllowedException}.</p>
     *
     * @deprecated Use {@link android.app.job.JobInfo.Builder} data transfer APIs instead.
     */
    @RequiresPermission(
            value = Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC,
            conditional = true
    )
    @Deprecated
    public static final int FOREGROUND_SERVICE_TYPE_DATA_SYNC = 1 << 0;

    /**
     * Constant corresponding to <code>mediaPlayback</code> in
     * the {@link android.R.attr#foregroundServiceType} attribute.
     * Music, video, news or other media playback.
     *
     * <p>Starting foreground service with this type from apps targeting API level
     * {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE} and later, will require permission
     * {@link android.Manifest.permission#FOREGROUND_SERVICE_MEDIA_PLAYBACK}.
     */
    @RequiresPermission(
            value = Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK,
            conditional = true
    )
    public static final int FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK = 1 << 1;

    /**
     * Constant corresponding to <code>phoneCall</code> in
     * the {@link android.R.attr#foregroundServiceType} attribute.
     * Ongoing operations related to phone calls, video conferencing,
     * or similar interactive communication.
     *
     * <p>Starting foreground service with this type from apps targeting API level
     * {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE} and later, will require permission
     * {@link android.Manifest.permission#FOREGROUND_SERVICE_PHONE_CALL} and
     * {@link android.Manifest.permission#MANAGE_OWN_CALLS}.
     */
    @RequiresPermission(
            allOf = {
                Manifest.permission.FOREGROUND_SERVICE_PHONE_CALL,
            },
            anyOf = {
                Manifest.permission.MANAGE_OWN_CALLS,
            },
            conditional = true
    )
    public static final int FOREGROUND_SERVICE_TYPE_PHONE_CALL = 1 << 2;

    /**
     * Constant corresponding to <code>location</code> in
     * the {@link android.R.attr#foregroundServiceType} attribute.
     * GPS, map, navigation location update.
     *
     * <p>Starting foreground service with this type from apps targeting API level
     * {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE} and later, will require permission
     * {@link android.Manifest.permission#FOREGROUND_SERVICE_LOCATION} and one of the
     * following permissions:
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION},
     * {@link android.Manifest.permission#ACCESS_FINE_LOCATION}.
     */
    @RequiresPermission(
            allOf = {
                Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            },
            anyOf = {
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            },
            conditional = true
    )
    public static final int FOREGROUND_SERVICE_TYPE_LOCATION = 1 << 3;

    /**
     * Constant corresponding to <code>connectedDevice</code> in
     * the {@link android.R.attr#foregroundServiceType} attribute.
     * Auto, bluetooth, TV or other devices connection, monitoring and interaction.
     *
     * <p>Starting foreground service with this type from apps targeting API level
     * {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE} and later, will require permission
     * {@link android.Manifest.permission#FOREGROUND_SERVICE_CONNECTED_DEVICE} and one of the
     * following permissions:
     * {@link android.Manifest.permission#BLUETOOTH_CONNECT},
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE},
     * {@link android.Manifest.permission#CHANGE_WIFI_STATE},
     * {@link android.Manifest.permission#CHANGE_WIFI_MULTICAST_STATE},
     * {@link android.Manifest.permission#NFC},
     * {@link android.Manifest.permission#TRANSMIT_IR},
     * or has been granted the access to one of the attached USB devices/accessories.
     */
    @RequiresPermission(
            allOf = {
                Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE,
            },
            anyOf = {
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.CHANGE_NETWORK_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
                Manifest.permission.NFC,
                Manifest.permission.TRANSMIT_IR,
            },
            conditional = true
    )
    public static final int FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE = 1 << 4;

    /**
     * Constant corresponding to {@code mediaProjection} in
     * the {@link android.R.attr#foregroundServiceType} attribute.
     * Managing a media projection session, e.g for screen recording or taking screenshots.
     *
     * <p>Starting foreground service with this type from apps targeting API level
     * {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE} and later, will require permission
     * {@link android.Manifest.permission#FOREGROUND_SERVICE_MEDIA_PROJECTION}, and the user must
     * have allowed the screen capture request from this app.
     */
    @RequiresPermission(
            value = Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION,
            conditional = true
    )
    public static final int FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION = 1 << 5;

    /**
     * Constant corresponding to {@code camera} in
     * the {@link android.R.attr#foregroundServiceType} attribute.
     * Use the camera device or record video.
     * For apps with <code>targetSdkVersion</code> {@link android.os.Build.VERSION_CODES#R} and
     * above, a foreground service will not be able to access the camera if this type is not
     * specified in the manifest and in
     * {@link android.app.Service#startForeground(int, android.app.Notification, int)}.
     *
     * <p>Starting foreground service with this type from apps targeting API level
     * {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE} and later, will require permission
     * {@link android.Manifest.permission#FOREGROUND_SERVICE_CAMERA} and
     * {@link android.Manifest.permission#CAMERA}.
     */
    @RequiresPermission(
            allOf = {
                Manifest.permission.FOREGROUND_SERVICE_CAMERA,
            },
            anyOf = {
                Manifest.permission.CAMERA,
            },
            conditional = true
    )
    public static final int FOREGROUND_SERVICE_TYPE_CAMERA = 1 << 6;

    /**
     * Constant corresponding to {@code microphone} in
     * the {@link android.R.attr#foregroundServiceType} attribute.
     * Use the microphone device or record audio.
     * For apps with <code>targetSdkVersion</code> {@link android.os.Build.VERSION_CODES#R} and
     * above, a foreground service will not be able to access the microphone if this type is not
     * specified in the manifest and in
     * {@link android.app.Service#startForeground(int, android.app.Notification, int)}.
     *
     * <p>Starting foreground service with this type from apps targeting API level
     * {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE} and later, will require permission
     * {@link android.Manifest.permission#FOREGROUND_SERVICE_MICROPHONE} and one of the following
     * permissions:
     * {@link android.Manifest.permission#CAPTURE_AUDIO_OUTPUT},
     * {@link android.Manifest.permission#RECORD_AUDIO}.
     */
    @RequiresPermission(
            allOf = {
                Manifest.permission.FOREGROUND_SERVICE_MICROPHONE,
            },
            anyOf = {
                Manifest.permission.CAPTURE_AUDIO_OUTPUT,
                Manifest.permission.RECORD_AUDIO,
            },
            conditional = true
    )
    public static final int FOREGROUND_SERVICE_TYPE_MICROPHONE = 1 << 7;

    /**
     * Constant corresponding to {@code health} in
     * the {@link android.R.attr#foregroundServiceType} attribute.
     * Health, wellness and fitness.
     *
     * <p>The caller app is required to have the permissions
     * {@link android.Manifest.permission#FOREGROUND_SERVICE_HEALTH} and one of the following
     * permissions:
     * {@link android.Manifest.permission#ACTIVITY_RECOGNITION},
     * {@link android.Manifest.permission#BODY_SENSORS},
     * {@link android.Manifest.permission#HIGH_SAMPLING_RATE_SENSORS}.
     */
    @RequiresPermission(
            allOf = {
                Manifest.permission.FOREGROUND_SERVICE_HEALTH,
            },
            anyOf = {
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.HIGH_SAMPLING_RATE_SENSORS,
            },
            conditional = true
    )
    public static final int FOREGROUND_SERVICE_TYPE_HEALTH = 1 << 8;

    /**
     * Constant corresponding to {@code remoteMessaging} in
     * the {@link android.R.attr#foregroundServiceType} attribute.
     * Messaging use cases which host local server to relay messages across devices.
     */
    @RequiresPermission(
            value = Manifest.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING,
            conditional = true
    )
    public static final int FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING = 1 << 9;

    /**
     * Constant corresponding to {@code systemExempted} in
     * the {@link android.R.attr#foregroundServiceType} attribute.
     * The system exmpted foreground service use cases.
     *
     * <p class="note">Note, apps are allowed to use this type only in the following cases:
     * <ul>
     *   <li>App has a UID &lt; {@link android.os.Process#FIRST_APPLICATION_UID}</li>
     *   <li>App is on Doze allowlist</li>
     *   <li>Device is running in <a href="https://android.googlesource.com/platform/frameworks/base/+/master/packages/SystemUI/docs/demo_mode.md">Demo Mode</a></li>
     *   <li><a href="https://source.android.com/devices/tech/admin/provision">Device owner app</a><li>
     *   <li><a href="https://source.android.com/devices/tech/admin/managed-profiles">Profile owner apps</a><li>
     *   <li>Persistent apps</li>
     *   <li><a href="https://source.android.com/docs/core/connect/carrier">Carrier privileged apps</a></li>
     *   <li>Apps that have the {@code android.app.role.RoleManager#ROLE_EMERGENCY} role</li>
     *   <li>Headless system apps</li>
     *   <li><a href="{@docRoot}guide/topics/admin/device-admin">Device admin apps</a></li>
     *   <li>Active VPN apps</li>
     *   <li>Apps holding {@link Manifest.permission#SCHEDULE_EXACT_ALARM} or
     *       {@link Manifest.permission#USE_EXACT_ALARM} permission.</li>
     * </ul>
     * </p>
     */
    @RequiresPermission(
            value = Manifest.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED,
            conditional = true
    )
    public static final int FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED = 1 << 10;

    /**
     * Foreground service type corresponding to {@code shortService} in
     * the {@link android.R.attr#foregroundServiceType} attribute.
     *
     * TODO Implement it
     *
     * TODO Expand the javadoc
     *
     * This type is not associated with specific use cases unlike other types, but this has
     * unique restrictions.
     * <ul>
     *     <li>Has a timeout
     *     <li>Cannot start other foreground services from this
     *     <li>
     * </ul>
     *
     * @see Service#onTimeout
     *
     * @hide
     */
    public static final int FOREGROUND_SERVICE_TYPE_SHORT_SERVICE = 1 << 11;

    /**
     * Constant corresponding to {@code specialUse} in
     * the {@link android.R.attr#foregroundServiceType} attribute.
     * Use cases that can't be categorized into any other foreground service types, but also
     * can't use {@link android.app.job.JobInfo.Builder} APIs.
     *
     * <p>The use of this foreground service type may be restricted. Additionally, apps must declare
     * a service-level {@link PackageManager#PROPERTY_SPECIAL_USE_FGS_SUBTYPE &lt;property&gt;} in
     * {@code AndroidManifest.xml} as a hint of what the exact use case here is.
     * Here is an example:
     * <pre>
     *  &lt;uses-permission
     *      android:name="android.permissions.FOREGROUND_SERVICE_SPECIAL_USE"
     *  /&gt;
     *  &lt;service
     *      android:name=".MySpecialForegroundService"
     *      android:foregroundServiceType="specialUse"&gt;
     *      &lt;property
     *          android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
     *          android:value="foo"
     *      /&gt;
     * &lt;/service&gt;
     * </pre>
     *
     * In a future release of Android, if the above foreground service type {@code foo} is supported
     * by the platform, to offer the backward compatibility, the app could specify
     * the {@code android:maxSdkVersion} attribute in the &lt;uses-permission&gt; section,
     * and also add the foreground service type {@code foo} into
     * the {@code android:foregroundServiceType}, therefore the same app could be installed
     * in both platforms.
     * <pre>
     *  &lt;uses-permission
     *      android:name="android.permissions.FOREGROUND_SERVICE_SPECIAL_USE"
     *      android:maxSdkVersion="last_sdk_version_without_type_foo"
     *  /&gt;
     *  &lt;service
     *      android:name=".MySpecialForegroundService"
     *      android:foregroundServiceType="specialUse|foo"&gt;
     *      &lt;property
     *          android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE""
     *          android:value="foo"
     *      /&gt;
     * &lt;/service&gt;
     * </pre>
     */
    @RequiresPermission(
            value = Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
            conditional = true
    )
    public static final int FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 1 << 30;

    /**
     * The max index being used in the definition of foreground service types.
     *
     * @hide
     */
    public static final int FOREGROUND_SERVICE_TYPES_MAX_INDEX = 30;

    /**
     * A special value indicates to use all types set in manifest file.
     */
    public static final int FOREGROUND_SERVICE_TYPE_MANIFEST = -1;

    /**
     * The set of flags for foreground service type.
     * The foreground service type is set in {@link android.R.attr#foregroundServiceType}
     * attribute.
     * @hide
     */
    @IntDef(flag = true, prefix = { "FOREGROUND_SERVICE_TYPE_" }, value = {
            FOREGROUND_SERVICE_TYPE_MANIFEST,
            FOREGROUND_SERVICE_TYPE_NONE,
            FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            FOREGROUND_SERVICE_TYPE_PHONE_CALL,
            FOREGROUND_SERVICE_TYPE_LOCATION,
            FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            FOREGROUND_SERVICE_TYPE_CAMERA,
            FOREGROUND_SERVICE_TYPE_MICROPHONE,
            FOREGROUND_SERVICE_TYPE_HEALTH,
            FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING,
            FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
            FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ForegroundServiceType {}

    /**
     * The type of foreground service, set in
     * {@link android.R.attr#foregroundServiceType} attribute by ORing flags in
     * {@link ForegroundServiceType}
     * @hide
     */
    public @ForegroundServiceType int mForegroundServiceType = FOREGROUND_SERVICE_TYPE_NONE;

    public ServiceInfo() {
    }

    public ServiceInfo(ServiceInfo orig) {
        super(orig);
        permission = orig.permission;
        flags = orig.flags;
        mForegroundServiceType = orig.mForegroundServiceType;
    }

    /**
     * Return foreground service type specified in the manifest..
     * @return foreground service type specified in the manifest.
     */
    public @ForegroundServiceType int getForegroundServiceType() {
        return mForegroundServiceType;
    }

    public void dump(Printer pw, String prefix) {
        dump(pw, prefix, DUMP_FLAG_ALL);
    }

    /** @hide */
    void dump(Printer pw, String prefix, int dumpFlags) {
        super.dumpFront(pw, prefix);
        pw.println(prefix + "permission=" + permission);
        pw.println(prefix + "flags=0x" + Integer.toHexString(flags));
        super.dumpBack(pw, prefix, dumpFlags);
    }

    public String toString() {
        return "ServiceInfo{"
            + Integer.toHexString(System.identityHashCode(this))
            + " " + name + "}";
    }

    /**
     * @return The label for the given foreground service type.
     *
     * @hide
     */
    public static String foregroundServiceTypeToLabel(@ForegroundServiceType int type) {
        switch (type) {
            case FOREGROUND_SERVICE_TYPE_MANIFEST:
                return "manifest";
            case FOREGROUND_SERVICE_TYPE_NONE:
                return "none";
            case FOREGROUND_SERVICE_TYPE_DATA_SYNC:
                return "dataSync";
            case FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK:
                return "mediaPlayback";
            case FOREGROUND_SERVICE_TYPE_PHONE_CALL:
                return "phoneCall";
            case FOREGROUND_SERVICE_TYPE_LOCATION:
                return "location";
            case FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE:
                return "connectedDevice";
            case FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION:
                return "mediaProjection";
            case FOREGROUND_SERVICE_TYPE_CAMERA:
                return "camera";
            case FOREGROUND_SERVICE_TYPE_MICROPHONE:
                return "microphone";
            case FOREGROUND_SERVICE_TYPE_HEALTH:
                return "health";
            case FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING:
                return "remoteMessaging";
            case FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED:
                return "systemExempted";
            case FOREGROUND_SERVICE_TYPE_SHORT_SERVICE:
                return "shortService";
            case FOREGROUND_SERVICE_TYPE_SPECIAL_USE:
                return "specialUse";
            default:
                return "unknown";
        }
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int parcelableFlags) {
        super.writeToParcel(dest, parcelableFlags);
        dest.writeString8(permission);
        dest.writeInt(flags);
        dest.writeInt(mForegroundServiceType);
    }

    public static final @android.annotation.NonNull Creator<ServiceInfo> CREATOR =
        new Creator<ServiceInfo>() {
        public ServiceInfo createFromParcel(Parcel source) {
            return new ServiceInfo(source);
        }
        public ServiceInfo[] newArray(int size) {
            return new ServiceInfo[size];
        }
    };

    private ServiceInfo(Parcel source) {
        super(source);
        permission = source.readString8();
        flags = source.readInt();
        mForegroundServiceType = source.readInt();
    }
}
