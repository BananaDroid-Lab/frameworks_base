/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.server.devicepolicy;

import static android.Manifest.permission.BIND_DEVICE_ADMIN;
import static android.Manifest.permission.MANAGE_CA_CERTIFICATES;
import static android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS;
import static android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL;
import static android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL;
import static android.Manifest.permission.QUERY_ADMIN_POLICY;
import static android.Manifest.permission.REQUEST_PASSWORD_COMPLEXITY;
import static android.Manifest.permission.SET_TIME;
import static android.Manifest.permission.SET_TIME_ZONE;
import static android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
import static android.app.ActivityManager.LOCK_TASK_MODE_NONE;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_DEFAULT;
import static android.app.AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_APP_STANDBY;
import static android.app.admin.DeviceAdminInfo.HEADLESS_DEVICE_OWNER_MODE_AFFILIATED;
import static android.app.admin.DeviceAdminReceiver.ACTION_COMPLIANCE_ACKNOWLEDGEMENT_REQUIRED;
import static android.app.admin.DeviceAdminReceiver.EXTRA_TRANSFER_OWNERSHIP_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.ACTION_CHECK_POLICY_COMPLIANCE;
import static android.app.admin.DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED;
import static android.app.admin.DevicePolicyManager.ACTION_MANAGED_PROFILE_PROVISIONED;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_USER;
import static android.app.admin.DevicePolicyManager.ACTION_SYSTEM_UPDATE_POLICY_CHANGED;
import static android.app.admin.DevicePolicyManager.DELEGATION_APP_RESTRICTIONS;
import static android.app.admin.DevicePolicyManager.DELEGATION_BLOCK_UNINSTALL;
import static android.app.admin.DevicePolicyManager.DELEGATION_CERT_INSTALL;
import static android.app.admin.DevicePolicyManager.DELEGATION_CERT_SELECTION;
import static android.app.admin.DevicePolicyManager.DELEGATION_ENABLE_SYSTEM_APP;
import static android.app.admin.DevicePolicyManager.DELEGATION_INSTALL_EXISTING_PACKAGE;
import static android.app.admin.DevicePolicyManager.DELEGATION_KEEP_UNINSTALLED_PACKAGES;
import static android.app.admin.DevicePolicyManager.DELEGATION_NETWORK_LOGGING;
import static android.app.admin.DevicePolicyManager.DELEGATION_PACKAGE_ACCESS;
import static android.app.admin.DevicePolicyManager.DELEGATION_PERMISSION_GRANT;
import static android.app.admin.DevicePolicyManager.DELEGATION_SECURITY_LOGGING;
import static android.app.admin.DevicePolicyManager.DEVICE_OWNER_TYPE_DEFAULT;
import static android.app.admin.DevicePolicyManager.DEVICE_OWNER_TYPE_FINANCED;
import static android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER;
import static android.app.admin.DevicePolicyManager.EXEMPT_FROM_APP_STANDBY;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE;
import static android.app.admin.DevicePolicyManager.EXTRA_RESOURCE_IDS;
import static android.app.admin.DevicePolicyManager.EXTRA_RESOURCE_TYPE;
import static android.app.admin.DevicePolicyManager.EXTRA_RESOURCE_TYPE_DRAWABLE;
import static android.app.admin.DevicePolicyManager.EXTRA_RESOURCE_TYPE_STRING;
import static android.app.admin.DevicePolicyManager.ID_TYPE_BASE_INFO;
import static android.app.admin.DevicePolicyManager.ID_TYPE_IMEI;
import static android.app.admin.DevicePolicyManager.ID_TYPE_INDIVIDUAL_ATTESTATION;
import static android.app.admin.DevicePolicyManager.ID_TYPE_MEID;
import static android.app.admin.DevicePolicyManager.ID_TYPE_SERIAL;
import static android.app.admin.DevicePolicyManager.LEAVE_ALL_SYSTEM_APPS_ENABLED;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_HOME;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO;
import static android.app.admin.DevicePolicyManager.NEARBY_STREAMING_NOT_CONTROLLED_BY_POLICY;
import static android.app.admin.DevicePolicyManager.NON_ORG_OWNED_PROFILE_KEYGUARD_FEATURES_AFFECT_OWNER;
import static android.app.admin.DevicePolicyManager.OPERATION_SAFETY_REASON_NONE;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_LOW;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_MANAGED;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_SOMETHING;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
import static android.app.admin.DevicePolicyManager.PERSONAL_APPS_NOT_SUSPENDED;
import static android.app.admin.DevicePolicyManager.PERSONAL_APPS_SUSPENDED_EXPLICITLY;
import static android.app.admin.DevicePolicyManager.PERSONAL_APPS_SUSPENDED_PROFILE_TIMEOUT;
import static android.app.admin.DevicePolicyManager.PRIVATE_DNS_MODE_OFF;
import static android.app.admin.DevicePolicyManager.PRIVATE_DNS_MODE_OPPORTUNISTIC;
import static android.app.admin.DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;
import static android.app.admin.DevicePolicyManager.PRIVATE_DNS_MODE_UNKNOWN;
import static android.app.admin.DevicePolicyManager.PRIVATE_DNS_SET_ERROR_FAILURE_SETTING;
import static android.app.admin.DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR;
import static android.app.admin.DevicePolicyManager.PROFILE_KEYGUARD_FEATURES_AFFECT_OWNER;
import static android.app.admin.DevicePolicyManager.STATE_USER_SETUP_FINALIZED;
import static android.app.admin.DevicePolicyManager.STATE_USER_UNMANAGED;
import static android.app.admin.DevicePolicyManager.STATUS_ACCOUNTS_NOT_EMPTY;
import static android.app.admin.DevicePolicyManager.STATUS_CANNOT_ADD_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.STATUS_DEVICE_ADMIN_NOT_SUPPORTED;
import static android.app.admin.DevicePolicyManager.STATUS_HAS_DEVICE_OWNER;
import static android.app.admin.DevicePolicyManager.STATUS_HAS_PAIRED;
import static android.app.admin.DevicePolicyManager.STATUS_HEADLESS_SYSTEM_USER_MODE_NOT_SUPPORTED;
import static android.app.admin.DevicePolicyManager.STATUS_MANAGED_USERS_NOT_SUPPORTED;
import static android.app.admin.DevicePolicyManager.STATUS_NONSYSTEM_USER_EXISTS;
import static android.app.admin.DevicePolicyManager.STATUS_NOT_SYSTEM_USER;
import static android.app.admin.DevicePolicyManager.STATUS_OK;
import static android.app.admin.DevicePolicyManager.STATUS_PROVISIONING_NOT_ALLOWED_FOR_NON_DEVELOPER_USERS;
import static android.app.admin.DevicePolicyManager.STATUS_SYSTEM_USER;
import static android.app.admin.DevicePolicyManager.STATUS_USER_HAS_PROFILE_OWNER;
import static android.app.admin.DevicePolicyManager.STATUS_USER_NOT_RUNNING;
import static android.app.admin.DevicePolicyManager.STATUS_USER_SETUP_COMPLETED;
import static android.app.admin.DevicePolicyManager.WIPE_EUICC;
import static android.app.admin.DevicePolicyManager.WIPE_EXTERNAL_STORAGE;
import static android.app.admin.DevicePolicyManager.WIPE_RESET_PROTECTION_DATA;
import static android.app.admin.DevicePolicyManager.WIPE_SILENTLY;
import static android.app.admin.DevicePolicyResources.Strings.Core.LOCATION_CHANGED_MESSAGE;
import static android.app.admin.DevicePolicyResources.Strings.Core.LOCATION_CHANGED_TITLE;
import static android.app.admin.DevicePolicyResources.Strings.Core.NETWORK_LOGGING_MESSAGE;
import static android.app.admin.DevicePolicyResources.Strings.Core.NETWORK_LOGGING_TITLE;
import static android.app.admin.DevicePolicyResources.Strings.Core.NOTIFICATION_WORK_PROFILE_CONTENT_DESCRIPTION;
import static android.app.admin.DevicePolicyResources.Strings.Core.PERSONAL_APP_SUSPENSION_MESSAGE;
import static android.app.admin.DevicePolicyResources.Strings.Core.PERSONAL_APP_SUSPENSION_SOON_MESSAGE;
import static android.app.admin.DevicePolicyResources.Strings.Core.PERSONAL_APP_SUSPENSION_TITLE;
import static android.app.admin.DevicePolicyResources.Strings.Core.PERSONAL_APP_SUSPENSION_TURN_ON_PROFILE;
import static android.app.admin.DevicePolicyResources.Strings.Core.PRINTING_DISABLED_NAMED_ADMIN;
import static android.app.admin.DevicePolicyResources.Strings.Core.WORK_PROFILE_DELETED_FAILED_PASSWORD_ATTEMPTS_MESSAGE;
import static android.app.admin.DevicePolicyResources.Strings.Core.WORK_PROFILE_DELETED_GENERIC_MESSAGE;
import static android.app.admin.DevicePolicyResources.Strings.Core.WORK_PROFILE_DELETED_ORG_OWNED_MESSAGE;
import static android.app.admin.DevicePolicyResources.Strings.Core.WORK_PROFILE_DELETED_TITLE;
import static android.app.admin.ProvisioningException.ERROR_ADMIN_PACKAGE_INSTALLATION_FAILED;
import static android.app.admin.ProvisioningException.ERROR_PRE_CONDITION_FAILED;
import static android.app.admin.ProvisioningException.ERROR_PROFILE_CREATION_FAILED;
import static android.app.admin.ProvisioningException.ERROR_REMOVE_NON_REQUIRED_APPS_FAILED;
import static android.app.admin.ProvisioningException.ERROR_SETTING_PROFILE_OWNER_FAILED;
import static android.app.admin.ProvisioningException.ERROR_SET_DEVICE_OWNER_FAILED;
import static android.app.admin.ProvisioningException.ERROR_STARTING_PROFILE_FAILED;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.GET_META_DATA;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
import static android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.ConnectivityManager.PROFILE_NETWORK_PREFERENCE_DEFAULT;
import static android.net.ConnectivityManager.PROFILE_NETWORK_PREFERENCE_ENTERPRISE;
import static android.net.ConnectivityManager.PROFILE_NETWORK_PREFERENCE_ENTERPRISE_BLOCKING;
import static android.net.ConnectivityManager.PROFILE_NETWORK_PREFERENCE_ENTERPRISE_NO_FALLBACK;
import static android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK;
import static android.provider.DeviceConfig.NAMESPACE_DEVICE_POLICY_MANAGER;
import static android.provider.Settings.Global.PRIVATE_DNS_SPECIFIER;
import static android.provider.Settings.Secure.MANAGED_PROVISIONING_DPC_DOWNLOADED;
import static android.provider.Settings.Secure.USER_SETUP_COMPLETE;
import static android.provider.Telephony.Carriers.DPC_URI;
import static android.provider.Telephony.Carriers.ENFORCE_KEY;
import static android.provider.Telephony.Carriers.ENFORCE_MANAGED_URI;
import static android.provider.Telephony.Carriers.INVALID_APN_ID;
import static android.security.keystore.AttestationUtils.USE_INDIVIDUAL_ATTESTATION;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_ENTRY_POINT_ADB;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_NONE;
import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_DPM_LOCK_NOW;
import static com.android.server.SystemTimeZone.TIME_ZONE_CONFIDENCE_HIGH;
import static com.android.server.am.ActivityManagerService.STOCK_PM_FLAGS;
import static com.android.server.devicepolicy.TransferOwnershipMetadataManager.ADMIN_TYPE_DEVICE_OWNER;
import static com.android.server.devicepolicy.TransferOwnershipMetadataManager.ADMIN_TYPE_PROFILE_OWNER;
import static com.android.server.pm.PackageManagerService.PLATFORM_PACKAGE_NAME;
import static com.android.server.pm.UserManagerInternal.OWNER_TYPE_DEVICE_OWNER;
import static com.android.server.pm.UserManagerInternal.OWNER_TYPE_PROFILE_OWNER;
import static com.android.server.pm.UserManagerInternal.OWNER_TYPE_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE;

import android.Manifest;
import android.Manifest.permission;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.ActivityTaskManager;
import android.app.AlarmManager;
import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.app.AppOpsManager.Mode;
import android.app.BroadcastOptions;
import android.app.IActivityManager;
import android.app.IActivityTaskManager;
import android.app.IApplicationThread;
import android.app.IServiceConnection;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.app.admin.DeviceAdminInfo;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyCache;
import android.app.admin.DevicePolicyDrawableResource;
import android.app.admin.DevicePolicyEventLogger;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyManager.DeviceOwnerType;
import android.app.admin.DevicePolicyManager.DevicePolicyOperation;
import android.app.admin.DevicePolicyManager.OperationSafetyReason;
import android.app.admin.DevicePolicyManager.PasswordComplexity;
import android.app.admin.DevicePolicyManager.PersonalAppsSuspensionReason;
import android.app.admin.DevicePolicyManagerInternal;
import android.app.admin.DevicePolicyManagerLiteInternal;
import android.app.admin.DevicePolicySafetyChecker;
import android.app.admin.DevicePolicyStringResource;
import android.app.admin.DeviceStateCache;
import android.app.admin.FactoryResetProtectionPolicy;
import android.app.admin.FullyManagedDeviceProvisioningParams;
import android.app.admin.IDevicePolicyManager;
import android.app.admin.ManagedProfileProvisioningParams;
import android.app.admin.ManagedSubscriptionsPolicy;
import android.app.admin.NetworkEvent;
import android.app.admin.PackagePolicy;
import android.app.admin.ParcelableGranteeMap;
import android.app.admin.ParcelableResource;
import android.app.admin.PasswordMetrics;
import android.app.admin.PasswordPolicy;
import android.app.admin.PreferentialNetworkServiceConfig;
import android.app.admin.SecurityLog;
import android.app.admin.SecurityLog.SecurityEvent;
import android.app.admin.StartInstallingUpdateCallback;
import android.app.admin.SystemUpdateInfo;
import android.app.admin.SystemUpdatePolicy;
import android.app.admin.UnsafeStateException;
import android.app.admin.WifiSsidPolicy;
import android.app.backup.IBackupManager;
import android.app.compat.CompatChanges;
import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.app.trust.TrustManager;
import android.app.usage.UsageStatsManagerInternal;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledAfter;
import android.compat.annotation.EnabledSince;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.PermissionChecker;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.CrossProfileApps;
import android.content.pm.CrossProfileAppsInternal;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ParceledListSlice;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.content.pm.StringParceledListSlice;
import android.content.pm.UserInfo;
import android.content.pm.UserPackage;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.IAudioService;
import android.net.ConnectivityManager;
import android.net.ConnectivitySettingsManager;
import android.net.IIpConnectivityMetrics;
import android.net.ProfileNetworkPreference;
import android.net.ProxyInfo;
import android.net.Uri;
import android.net.VpnManager;
import android.net.metrics.IpConnectivityLog;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.os.ShellCallback;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.UserManager.UserRestrictionSource;
import android.os.storage.StorageManager;
import android.permission.AdminPermissionControlParams;
import android.permission.IPermissionManager;
import android.permission.PermissionControllerManager;
import android.provider.CalendarContract;
import android.provider.ContactsContract.QuickContact;
import android.provider.ContactsInternal;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.provider.Telephony;
import android.security.AppUriAuthenticationPolicy;
import android.security.IKeyChainAliasCallback;
import android.security.IKeyChainService;
import android.security.KeyChain;
import android.security.KeyChain.KeyChainConnection;
import android.security.KeyStore;
import android.security.keymaster.KeymasterCertificateChain;
import android.security.keystore.AttestationUtils;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.ParcelableKeyGenParameterSpec;
import android.stats.devicepolicy.DevicePolicyEnums;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.DebugUtils;
import android.util.IndentingPrintWriter;
import android.util.IntArray;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;
import android.util.Xml;
import android.view.IWindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.IAccessibilityManager;
import android.view.inputmethod.InputMethodInfo;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.app.LocalePicker;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.net.NetworkUtilsInternal;
import com.android.internal.notification.SystemNotificationChannels;
import com.android.internal.os.BackgroundThread;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.telephony.SmsApplication;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.FunctionalUtils.ThrowingRunnable;
import com.android.internal.util.FunctionalUtils.ThrowingSupplier;
import com.android.internal.util.JournaledFile;
import com.android.internal.util.Preconditions;
import com.android.internal.util.StatLogger;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockSettingsInternal;
import com.android.internal.widget.LockscreenCredential;
import com.android.internal.widget.PasswordValidationError;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.net.module.util.ProxyUtils;
import com.android.server.AlarmManagerInternal;
import com.android.server.LocalServices;
import com.android.server.LockGuard;
import com.android.server.PersistentDataBlockManagerInternal;
import com.android.server.SystemServerInitThreadPool;
import com.android.server.SystemService;
import com.android.server.devicepolicy.ActiveAdmin.TrustAgentInfo;
import com.android.server.inputmethod.InputMethodManagerInternal;
import com.android.server.net.NetworkPolicyManagerInternal;
import com.android.server.pm.DefaultCrossProfileIntentFilter;
import com.android.server.pm.DefaultCrossProfileIntentFiltersUtils;
import com.android.server.pm.RestrictionsSet;
import com.android.server.pm.UserManagerInternal;
import com.android.server.pm.UserManagerInternal.UserRestrictionsListener;
import com.android.server.pm.UserRestrictionsUtils;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.storage.DeviceStorageMonitorInternal;
import com.android.server.uri.NeededUriGrants;
import com.android.server.uri.UriGrantsManagerInternal;
import com.android.server.utils.Slogf;
import com.android.server.wm.ActivityTaskManagerInternal;

import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Implementation of the device policy APIs.
 */
public class DevicePolicyManagerService extends IDevicePolicyManager.Stub {

    protected static final String LOG_TAG = "DevicePolicyManager";

    private static final String ATTRIBUTION_TAG = "DevicePolicyManagerService";

    static final boolean VERBOSE_LOG = false; // DO NOT SUBMIT WITH TRUE

    static final String DEVICE_POLICIES_XML = "device_policies.xml";

    static final String POLICIES_VERSION_XML = "device_policies_version";

    private static final String TRANSFER_OWNERSHIP_PARAMETERS_XML =
            "transfer-ownership-parameters.xml";

    private static final String TAG_TRANSFER_OWNERSHIP_BUNDLE = "transfer-ownership-bundle";

    private static final int REQUEST_EXPIRE_PASSWORD = 5571;

    private static final int REQUEST_PROFILE_OFF_DEADLINE = 5572;

    private static final long MS_PER_DAY = TimeUnit.DAYS.toMillis(1);

    private static final long EXPIRATION_GRACE_PERIOD_MS = 5 * MS_PER_DAY; // 5 days, in ms
    private static final long MANAGED_PROFILE_MAXIMUM_TIME_OFF_THRESHOLD = 3 * MS_PER_DAY;
    /** When to warn the user about the approaching work profile off deadline: 1 day before */
    private static final long MANAGED_PROFILE_OFF_WARNING_PERIOD = 1 * MS_PER_DAY;

    private static final String ACTION_EXPIRED_PASSWORD_NOTIFICATION =
            "com.android.server.ACTION_EXPIRED_PASSWORD_NOTIFICATION";

    /** Broadcast action invoked when the user taps a notification to turn the profile on. */
    @VisibleForTesting
    static final String ACTION_TURN_PROFILE_ON_NOTIFICATION =
            "com.android.server.ACTION_TURN_PROFILE_ON_NOTIFICATION";

    /** Broadcast action for tracking managed profile maximum time off. */
    @VisibleForTesting
    static final String ACTION_PROFILE_OFF_DEADLINE =
            "com.android.server.ACTION_PROFILE_OFF_DEADLINE";

    private static final String CALLED_FROM_PARENT = "calledFromParent";
    private static final String NOT_CALLED_FROM_PARENT = "notCalledFromParent";

    private static final String CREDENTIAL_MANAGEMENT_APP = "credentialManagementApp";
    private static final String NOT_CREDENTIAL_MANAGEMENT_APP = "notCredentialManagementApp";

    private static final String NULL_STRING_ARRAY = "nullStringArray";

    private static final String ALLOW_USER_PROVISIONING_KEY = "ro.config.allowuserprovisioning";

    // Comprehensive list of delegations.
    private static final String DELEGATIONS[] = {
        DELEGATION_CERT_INSTALL,
        DELEGATION_APP_RESTRICTIONS,
        DELEGATION_BLOCK_UNINSTALL,
        DELEGATION_ENABLE_SYSTEM_APP,
        DELEGATION_KEEP_UNINSTALLED_PACKAGES,
        DELEGATION_PACKAGE_ACCESS,
        DELEGATION_PERMISSION_GRANT,
        DELEGATION_INSTALL_EXISTING_PACKAGE,
        DELEGATION_KEEP_UNINSTALLED_PACKAGES,
        DELEGATION_NETWORK_LOGGING,
        DELEGATION_SECURITY_LOGGING,
        DELEGATION_CERT_SELECTION,
    };

    // Subset of delegations that can only be delegated by Device Owner or Profile Owner of a
    // managed profile.
    private static final List<String> DEVICE_OWNER_OR_MANAGED_PROFILE_OWNER_DELEGATIONS =
            Arrays.asList(new String[]{
                    DELEGATION_NETWORK_LOGGING,
            });

    // Subset of delegations that can only be delegated by Device Owner or Profile Owner of an
    // organization-owned and managed profile.
    private static final List<String>
            DEVICE_OWNER_OR_ORGANIZATION_OWNED_MANAGED_PROFILE_OWNER_DELEGATIONS =
            Arrays.asList(new String[]{
                    DELEGATION_SECURITY_LOGGING,
            });

    // Subset of delegations that only one single package within a given user can hold
    private static final List<String> EXCLUSIVE_DELEGATIONS = Arrays.asList(new String[] {
            DELEGATION_NETWORK_LOGGING,
            DELEGATION_SECURITY_LOGGING,
            DELEGATION_CERT_SELECTION,
    });

    /**
     * System property whose value indicates whether the device is fully owned by an organization:
     * it can be either a device owner device, or a device with an organization-owned managed
     * profile.
     *
     * <p>The state is stored as a Boolean string.
     */
    private static final String PROPERTY_ORGANIZATION_OWNED = "ro.organization_owned";

    private static final int STATUS_BAR_DISABLE_MASK =
            StatusBarManager.DISABLE_EXPAND |
            StatusBarManager.DISABLE_NOTIFICATION_ICONS |
            StatusBarManager.DISABLE_NOTIFICATION_ALERTS |
            StatusBarManager.DISABLE_SEARCH;

    private static final int STATUS_BAR_DISABLE2_MASK =
            StatusBarManager.DISABLE2_QUICK_SETTINGS;

    private static final Set<String> SECURE_SETTINGS_ALLOWLIST;
    private static final Set<String> SECURE_SETTINGS_DEVICEOWNER_ALLOWLIST;
    private static final Set<String> GLOBAL_SETTINGS_ALLOWLIST;
    private static final Set<String> GLOBAL_SETTINGS_DEPRECATED;
    private static final Set<String> SYSTEM_SETTINGS_ALLOWLIST;
    private static final Set<Integer> DA_DISALLOWED_POLICIES;
    private static final String AB_DEVICE_KEY = "ro.build.ab_update";
    // The version of the current DevicePolicyManagerService data. This version is used
    // to decide whether an existing policy in the {@link #DEVICE_POLICIES_XML} needs to
    // be upgraded. See {@link PolicyVersionUpgrader} on instructions how to add an upgrade
    // step.
    static final int DPMS_VERSION = 4;

    static {
        SECURE_SETTINGS_ALLOWLIST = new ArraySet<>();
        SECURE_SETTINGS_ALLOWLIST.add(Settings.Secure.DEFAULT_INPUT_METHOD);
        SECURE_SETTINGS_ALLOWLIST.add(Settings.Secure.SKIP_FIRST_USE_HINTS);
        SECURE_SETTINGS_ALLOWLIST.add(Settings.Secure.INSTALL_NON_MARKET_APPS);

        SECURE_SETTINGS_DEVICEOWNER_ALLOWLIST = new ArraySet<>();
        SECURE_SETTINGS_DEVICEOWNER_ALLOWLIST.addAll(SECURE_SETTINGS_ALLOWLIST);
        SECURE_SETTINGS_DEVICEOWNER_ALLOWLIST.add(Settings.Secure.LOCATION_MODE);

        GLOBAL_SETTINGS_ALLOWLIST = new ArraySet<>();
        GLOBAL_SETTINGS_ALLOWLIST.add(Settings.Global.ADB_ENABLED);
        GLOBAL_SETTINGS_ALLOWLIST.add(Settings.Global.ADB_WIFI_ENABLED);
        GLOBAL_SETTINGS_ALLOWLIST.add(Settings.Global.AUTO_TIME);
        GLOBAL_SETTINGS_ALLOWLIST.add(Settings.Global.AUTO_TIME_ZONE);
        GLOBAL_SETTINGS_ALLOWLIST.add(Settings.Global.DATA_ROAMING);
        GLOBAL_SETTINGS_ALLOWLIST.add(Settings.Global.USB_MASS_STORAGE_ENABLED);
        GLOBAL_SETTINGS_ALLOWLIST.add(Settings.Global.WIFI_SLEEP_POLICY);
        GLOBAL_SETTINGS_ALLOWLIST.add(Settings.Global.STAY_ON_WHILE_PLUGGED_IN);
        GLOBAL_SETTINGS_ALLOWLIST.add(Settings.Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN);
        GLOBAL_SETTINGS_ALLOWLIST.add(Settings.Global.PRIVATE_DNS_MODE);
        GLOBAL_SETTINGS_ALLOWLIST.add(Settings.Global.PRIVATE_DNS_SPECIFIER);

        GLOBAL_SETTINGS_DEPRECATED = new ArraySet<>();
        GLOBAL_SETTINGS_DEPRECATED.add(Settings.Global.BLUETOOTH_ON);
        GLOBAL_SETTINGS_DEPRECATED.add(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
        GLOBAL_SETTINGS_DEPRECATED.add(Settings.Global.MODE_RINGER);
        GLOBAL_SETTINGS_DEPRECATED.add(Settings.Global.NETWORK_PREFERENCE);
        GLOBAL_SETTINGS_DEPRECATED.add(Settings.Global.WIFI_ON);

        SYSTEM_SETTINGS_ALLOWLIST = new ArraySet<>();
        SYSTEM_SETTINGS_ALLOWLIST.add(Settings.System.SCREEN_BRIGHTNESS);
        SYSTEM_SETTINGS_ALLOWLIST.add(Settings.System.SCREEN_BRIGHTNESS_FLOAT);
        SYSTEM_SETTINGS_ALLOWLIST.add(Settings.System.SCREEN_BRIGHTNESS_MODE);
        SYSTEM_SETTINGS_ALLOWLIST.add(Settings.System.SCREEN_OFF_TIMEOUT);

        DA_DISALLOWED_POLICIES = new ArraySet<>();
        DA_DISALLOWED_POLICIES.add(DeviceAdminInfo.USES_POLICY_DISABLE_CAMERA);
        DA_DISALLOWED_POLICIES.add(DeviceAdminInfo.USES_POLICY_DISABLE_KEYGUARD_FEATURES);
        DA_DISALLOWED_POLICIES.add(DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD);
        DA_DISALLOWED_POLICIES.add(DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
    }

    /**
     * Keyguard features that when set on a profile affect the profile content or challenge only.
     * These cannot be set on the managed profile's parent DPM instance
     */
    private static final int PROFILE_KEYGUARD_FEATURES_PROFILE_ONLY =
            DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS;

    /** Keyguard features that are allowed to be set on a managed profile */
    private static final int PROFILE_KEYGUARD_FEATURES =
            NON_ORG_OWNED_PROFILE_KEYGUARD_FEATURES_AFFECT_OWNER
                    | PROFILE_KEYGUARD_FEATURES_PROFILE_ONLY;

    private static final int DEVICE_ADMIN_DEACTIVATE_TIMEOUT = 10000;

    /**
     * Minimum timeout in milliseconds after which unlocking with weak auth times out,
     * i.e. the user has to use a strong authentication method like password, PIN or pattern.
     */
    private static final long MINIMUM_STRONG_AUTH_TIMEOUT_MS = TimeUnit.HOURS.toMillis(1);

    /**
     * The amount of ms that a managed kiosk must go without user interaction to be considered
     * unattended.
     */
    private static final int UNATTENDED_MANAGED_KIOSK_MS = 30000;

    /**
     * Strings logged with {@link
     * com.android.internal.logging.nano.MetricsProto.MetricsEvent#PROVISIONING_ENTRY_POINT_ADB},
     * {@link DevicePolicyEnums#PROVISIONING_ENTRY_POINT_ADB},
     * {@link DevicePolicyEnums#SET_NETWORK_LOGGING_ENABLED} and
     * {@link DevicePolicyEnums#RETRIEVE_NETWORK_LOGS}.
     */
    private static final String LOG_TAG_PROFILE_OWNER = "profile-owner";
    private static final String LOG_TAG_DEVICE_OWNER = "device-owner";

    /**
     * For admin apps targeting R+, throw when the app sets password requirement
     * that is not taken into account at given quality. For example when quality is set
     * to {@link android.app.admin.DevicePolicyManager#PASSWORD_QUALITY_UNSPECIFIED}, it doesn't
     * make sense to require certain password length. If the intent is to require a password of
     * certain length having at least NUMERIC quality, the admin should first call
     * {@link android.app.admin.DevicePolicyManager#setPasswordQuality} and only then call
     * {@link android.app.admin.DevicePolicyManager#setPasswordMinimumLength}.
     *
     * <p>Conversely when an admin app targeting R+ lowers password quality, those
     * requirements that stop making sense are reset to default values.
     */
    @ChangeId
    @EnabledAfter(targetSdkVersion = Build.VERSION_CODES.Q)
    private static final long ADMIN_APP_PASSWORD_COMPLEXITY = 123562444L;

    /**
     * Admin apps targeting Android R+ may not use
     * {@link android.app.admin.DevicePolicyManager#setSecureSetting} to change the deprecated
     * {@link android.provider.Settings.Secure#LOCATION_MODE} setting. Instead they should use
     * {@link android.app.admin.DevicePolicyManager#setLocationEnabled}.
     */
    @ChangeId
    @EnabledAfter(targetSdkVersion = Build.VERSION_CODES.Q)
    private static final long USE_SET_LOCATION_ENABLED = 117835097L;

    /**
     * Forces wipeDataNoLock to attempt removing the user or throw an error as
     * opposed to trying to factory reset the device first and only then falling back to user
     * removal.
     */
    @ChangeId
    @EnabledSince(targetSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static final long EXPLICIT_WIPE_BEHAVIOUR = 242193913L;

    // Only add to the end of the list. Do not change or rearrange these values, that will break
    // historical data. Do not use negative numbers or zero, logger only handles positive
    // integers.
    private static final int COPY_ACCOUNT_SUCCEEDED = 1;
    private static final int COPY_ACCOUNT_FAILED = 2;
    private static final int COPY_ACCOUNT_TIMED_OUT = 3;
    private static final int COPY_ACCOUNT_EXCEPTION = 4;

    @IntDef({
            COPY_ACCOUNT_SUCCEEDED,
            COPY_ACCOUNT_FAILED,
            COPY_ACCOUNT_TIMED_OUT,
            COPY_ACCOUNT_EXCEPTION})
    private @interface CopyAccountStatus {}

    /**
     * Mapping of {@link android.app.admin.DevicePolicyManager.ApplicationExemptionConstants} to
     * corresponding app-ops.
     */
    private static final Map<Integer, String> APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS =
            new ArrayMap<>();
    static {
        APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS.put(
                EXEMPT_FROM_APP_STANDBY, OPSTR_SYSTEM_EXEMPT_FROM_APP_STANDBY);
    }

    /**
     * Admin apps targeting Android S+ may not use
     * {@link android.app.admin.DevicePolicyManager#setPasswordQuality} to set password quality
     * on the {@code DevicePolicyManager} instance obtained by calling
     * {@link android.app.admin.DevicePolicyManager#getParentProfileInstance}.
     * Instead, they should use
     * {@link android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity} to set
     * coarse-grained password requirements device-wide.
     */
    @ChangeId
    @EnabledSince(targetSdkVersion = Build.VERSION_CODES.S)
    private static final long PREVENT_SETTING_PASSWORD_QUALITY_ON_PARENT = 165573442L;

    /**
     * For Admin Apps targeting U+
     * If {@link android.security.IKeyChainService#setGrant} is called with an alias with no
     * existing key, throw IllegalArgumentException.
     */
    @ChangeId
    @EnabledSince(targetSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private static final long THROW_EXCEPTION_WHEN_KEY_MISSING = 175101461L;

    private static final String CREDENTIAL_MANAGEMENT_APP_INVALID_ALIAS_MSG =
            "The alias provided must be contained in the aliases specified in the credential "
                    + "management app's authentication policy";
    private static final String NOT_SYSTEM_CALLER_MSG = "Only the system can %s";

    private static final String PERMISSION_BASED_ACCESS_EXPERIMENT_FLAG =
            "enable_permission_based_access";
    private static final String ENABLE_COEXISTENCE_FLAG = "enable_coexistence";
    private static final boolean DEFAULT_VALUE_PERMISSION_BASED_ACCESS_FLAG = false;
    private static final boolean DEFAULT_ENABLE_COEXISTENCE_FLAG = false;

    // TODO(b/258425381) remove the flag after rollout.
    private static final String KEEP_PROFILES_RUNNING_FLAG = "enable_keep_profiles_running";
    private static final boolean DEFAULT_KEEP_PROFILES_RUNNING_FLAG = false;

    private static final String ENABLE_WORK_PROFILE_TELEPHONY_FLAG =
            "enable_work_profile_telephony";
    private static final boolean DEFAULT_WORK_PROFILE_TELEPHONY_FLAG = false;

    // TODO(b/261999445) remove the flag after rollout.
    private static final String HEADLESS_FLAG = "headless";
    private static final boolean DEFAULT_HEADLESS_FLAG = true;

    /**
     * This feature flag is checked once after boot and this value us used until the next reboot to
     * avoid needing to handle the flag changing on the fly.
     */
    private final boolean mKeepProfilesRunning = isKeepProfilesRunningFlagEnabled();

    /**
     * For apps targeting U+
     * Enable multiple admins to coexist on the same device.
     */
    @ChangeId
    @EnabledSince(targetSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    static final long ENABLE_COEXISTENCE_CHANGE = 260560985L;

    final Context mContext;
    final Injector mInjector;
    final PolicyPathProvider mPathProvider;
    final IPackageManager mIPackageManager;
    final IPermissionManager mIPermissionManager;
    final UserManager mUserManager;
    final UserManagerInternal mUserManagerInternal;
    final UsageStatsManagerInternal mUsageStatsManagerInternal;
    final TelephonyManager mTelephonyManager;
    private final LockPatternUtils mLockPatternUtils;
    private final LockSettingsInternal mLockSettingsInternal;
    private final DeviceAdminServiceController mDeviceAdminServiceController;
    private final OverlayPackagesProvider mOverlayPackagesProvider;

    private final DevicePolicyCacheImpl mPolicyCache = new DevicePolicyCacheImpl();
    private final DeviceStateCacheImpl mStateCache = new DeviceStateCacheImpl();
    private final Object mESIDInitilizationLock = new Object();
    private EnterpriseSpecificIdCalculator mEsidCalculator;
    private final Object mSubscriptionsChangedListenerLock = new Object();
    @GuardedBy("mSubscriptionsChangedListenerLock")
    private SubscriptionManager.OnSubscriptionsChangedListener mSubscriptionsChangedListener;

    /**
     * Contains the list of OEM Default Role Holders for Contact-related roles
     * (DIALER, SMS, SYSTEM_CONTACTS)
     */
    private final Set<String> mContactSystemRoleHolders;

    /**
     * Contains (package-user) pairs to remove. An entry (p, u) implies that removal of package p
     * is requested for user u.
     */
    private final Set<UserPackage> mPackagesToRemove = new ArraySet<>();

    final LocalService mLocalService;

    // Stores and loads state on device and profile owners.
    @VisibleForTesting
    final Owners mOwners;

    private final Binder mToken = new Binder();

    /**
     * Whether or not device admin feature is supported. If it isn't return defaults for all
     * public methods, unless the caller has the appropriate permission for a particular method.
     */
    final boolean mHasFeature;

    /**
     * Whether or not this device is a watch.
     */
    final boolean mIsWatch;

    /**
     * Whether or not this device is an automotive.
     */
    private final boolean mIsAutomotive;

    /**
     * Whether this device has the telephony feature.
     */
    final boolean mHasTelephonyFeature;

    private final CertificateMonitor mCertificateMonitor;
    private final SecurityLogMonitor mSecurityLogMonitor;
    private final RemoteBugreportManager mBugreportCollectionManager;

    @GuardedBy("getLockObject()")
    private NetworkLogger mNetworkLogger;

    private final SetupContentObserver mSetupContentObserver;
    private final DevicePolicyConstantsObserver mConstantsObserver;

    private DevicePolicyConstants mConstants;

    /**
     * User to be switched to on {@code logoutUser()}.
     *
     * <p>Only used on devices with headless system user mode
     */
    @GuardedBy("getLockObject()")
    private @UserIdInt int mLogoutUserId = UserHandle.USER_NULL;

    /**
     * User the network logging notification was sent to.
     */
    // Guarded by mHandler
    private @UserIdInt int mNetworkLoggingNotificationUserId = UserHandle.USER_NULL;

    private final DeviceManagementResourcesProvider mDeviceManagementResourcesProvider;
    private final DevicePolicyManagementRoleObserver mDevicePolicyManagementRoleObserver;

    private final DevicePolicyEngine mDevicePolicyEngine;

    private static final boolean ENABLE_LOCK_GUARD = true;

    /**
     * Profile off deadline is not set or more than MANAGED_PROFILE_OFF_WARNING_PERIOD away, or the
     * user is running unlocked, no need for notification.
     */
    private static final int PROFILE_OFF_NOTIFICATION_NONE = 0;
    /**
     * Profile off deadline is closer than MANAGED_PROFILE_OFF_WARNING_PERIOD.
     */
    private static final int PROFILE_OFF_NOTIFICATION_WARNING = 1;
    /**
     * Profile off deadline reached, notify the user that personal apps blocked.
     */
    private static final int PROFILE_OFF_NOTIFICATION_SUSPENDED = 2;

    interface Stats {
        int LOCK_GUARD_GUARD = 0;

        int COUNT = LOCK_GUARD_GUARD + 1;
    }

    private final StatLogger mStatLogger = new StatLogger(new String[] {
            "LockGuard.guard()",
    });

    private final Object mLockDoNoUseDirectly = LockGuard.installNewLock(
            LockGuard.INDEX_DPMS, /* doWtf=*/ true);

    final Object getLockObject() {
        if (ENABLE_LOCK_GUARD) {
            final long start = mStatLogger.getTime();
            LockGuard.guard(LockGuard.INDEX_DPMS);
            mStatLogger.logDurationStat(Stats.LOCK_GUARD_GUARD, start);
        }
        return mLockDoNoUseDirectly;
    }

    /**
     * Check if the current thread holds the DPMS lock, and if not, do a WTF.
     *
     * (Doing this check too much may be costly, so don't call it in a hot path.)
     */
    final void ensureLocked() {
        if (Thread.holdsLock(mLockDoNoUseDirectly)) {
            return;
        }
        Slogf.wtfStack(LOG_TAG, "Not holding DPMS lock.");
    }

    /**
     * Calls wtfStack() if called with the DPMS lock held.
     */
    private void wtfIfInLock() {
        if (Thread.holdsLock(mLockDoNoUseDirectly)) {
            Slogf.wtfStack(LOG_TAG, "Shouldn't be called with DPMS lock held");
        }
    }

    @VisibleForTesting
    final TransferOwnershipMetadataManager mTransferOwnershipMetadataManager;

    @Nullable
    private DevicePolicySafetyChecker mSafetyChecker;

    @GuardedBy("getLockObject()")
    private final ArrayList<Object> mPendingUserCreatedCallbackTokens = new ArrayList<>();

    public static final class Lifecycle extends SystemService {
        private DevicePolicyManagerService mService;

        public Lifecycle(Context context) {
            super(context);
            String dpmsClassName = context.getResources()
                    .getString(R.string.config_deviceSpecificDevicePolicyManagerService);
            if (TextUtils.isEmpty(dpmsClassName)) {
                mService = new DevicePolicyManagerService(context);
            } else {
                try {
                    Class<?> serviceClass = Class.forName(dpmsClassName);
                    Constructor<?> constructor = serviceClass.getConstructor(Context.class);
                    mService = (DevicePolicyManagerService) constructor.newInstance(context);
                } catch (Exception e) {
                    throw new IllegalStateException(
                        "Failed to instantiate DevicePolicyManagerService with class name: "
                        + dpmsClassName, e);
                }
            }
        }

        /** Sets the {@link DevicePolicySafetyChecker}. */
        public void setDevicePolicySafetyChecker(DevicePolicySafetyChecker safetyChecker) {
            mService.setDevicePolicySafetyChecker(safetyChecker);
        }

        @Override
        public void onStart() {
            publishBinderService(Context.DEVICE_POLICY_SERVICE, mService);
        }

        @Override
        public void onBootPhase(int phase) {
            mService.systemReady(phase);
        }

        @Override
        public void onUserStarting(@NonNull TargetUser user) {
            if (user.isPreCreated()) return;
            mService.handleStartUser(user.getUserIdentifier());
        }

        @Override
        public void onUserUnlocking(@NonNull TargetUser user) {
            if (user.isPreCreated()) return;
            mService.handleUnlockUser(user.getUserIdentifier());
        }

        @Override
        public void onUserStopping(@NonNull TargetUser user) {
            if (user.isPreCreated()) return;
            mService.handleStopUser(user.getUserIdentifier());
        }

        @Override
        public void onUserUnlocked(@NonNull TargetUser user) {
            if (user.isPreCreated()) return;
            mService.handleOnUserUnlocked(user.getUserIdentifier());
        }
    }

    @GuardedBy("getLockObject()")
    final SparseArray<DevicePolicyData> mUserData;

    final Handler mHandler;
    final Handler mBackgroundHandler;

    /** Listens only if mHasFeature == true. */
    final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final int userHandle = intent.getIntExtra(Intent.EXTRA_USER_HANDLE,
                    getSendingUserId());

            /*
             * Network logging would ideally be started in setDeviceOwnerSystemPropertyLocked(),
             * however it's too early in the boot process to register with IIpConnectivityMetrics
             * to listen for events.
             */
            if (Intent.ACTION_USER_STARTED.equals(action) && userHandle == UserHandle.USER_SYSTEM) {
                synchronized (getLockObject()) {
                    if (isNetworkLoggingEnabledInternalLocked()) {
                        setNetworkLoggingActiveInternal(true);
                    }
                }
            }
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    && userHandle == mOwners.getDeviceOwnerUserId()) {
                mBugreportCollectionManager.checkForPendingBugreportAfterBoot();

            }
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || ACTION_EXPIRED_PASSWORD_NOTIFICATION.equals(action)) {
                if (VERBOSE_LOG) {
                    Slogf.v(LOG_TAG, "Sending password expiration notifications for action "
                            + action + " for user " + userHandle);
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        handlePasswordExpirationNotification(userHandle);
                    }
                });
            }

            if (Intent.ACTION_USER_ADDED.equals(action)) {
                sendDeviceOwnerUserCommand(DeviceAdminReceiver.ACTION_USER_ADDED, userHandle);
                synchronized (getLockObject()) {
                    // It might take a while for the user to become affiliated. Make security
                    // and network logging unavailable in the meantime.
                    maybePauseDeviceWideLoggingLocked();
                }
            } else if (Intent.ACTION_USER_REMOVED.equals(action)) {
                sendDeviceOwnerUserCommand(DeviceAdminReceiver.ACTION_USER_REMOVED, userHandle);
                synchronized (getLockObject()) {
                    // Check whether the user is affiliated, *before* removing its data.
                    boolean isRemovedUserAffiliated = isUserAffiliatedWithDeviceLocked(userHandle);
                    removeUserData(userHandle);
                    if (!isRemovedUserAffiliated) {
                        // We discard the logs when unaffiliated users are deleted (so that the
                        // device owner cannot retrieve data about that user after it's gone).
                        discardDeviceWideLogsLocked();
                        // Resume logging if all remaining users are affiliated.
                        maybeResumeDeviceWideLoggingLocked();
                    }
                }
            } else if (Intent.ACTION_USER_STARTED.equals(action)) {
                sendDeviceOwnerUserCommand(DeviceAdminReceiver.ACTION_USER_STARTED, userHandle);
                synchronized (getLockObject()) {
                    maybeSendAdminEnabledBroadcastLocked(userHandle);
                    // Reset the policy data
                    mUserData.remove(userHandle);
                }
                handlePackagesChanged(null /* check all admins */, userHandle);
                updatePersonalAppsSuspensionOnUserStart(userHandle);
            } else if (Intent.ACTION_USER_STOPPED.equals(action)) {
                sendDeviceOwnerUserCommand(DeviceAdminReceiver.ACTION_USER_STOPPED, userHandle);
                if (isManagedProfile(userHandle)) {
                    Slogf.d(LOG_TAG, "Managed profile was stopped");
                    updatePersonalAppsSuspension(userHandle, false /* unlocked */);
                }
            } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                sendDeviceOwnerUserCommand(DeviceAdminReceiver.ACTION_USER_SWITCHED, userHandle);
            } else if (Intent.ACTION_USER_UNLOCKED.equals(action)) {
                synchronized (getLockObject()) {
                    maybeSendAdminEnabledBroadcastLocked(userHandle);
                }
                if (isManagedProfile(userHandle)) {
                    Slogf.d(LOG_TAG, "Managed profile became unlocked");
                    final boolean suspended =
                            updatePersonalAppsSuspension(userHandle, true /* unlocked */);
                    triggerPolicyComplianceCheckIfNeeded(userHandle, suspended);
                }
            } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.equals(action)) {
                handlePackagesChanged(null /* check all admins */, userHandle);
            } else if (Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
                handlePackagesChanged(intent.getData().getSchemeSpecificPart(), userHandle);
            } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    handlePackagesChanged(intent.getData().getSchemeSpecificPart(), userHandle);
                } else {
                    handleNewPackageInstalled(intent.getData().getSchemeSpecificPart(), userHandle);
                }
            } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                    && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                handlePackagesChanged(intent.getData().getSchemeSpecificPart(), userHandle);
                removeCredentialManagementApp(intent.getData().getSchemeSpecificPart());
            } else if (Intent.ACTION_MANAGED_PROFILE_ADDED.equals(action)) {
                clearWipeProfileNotification();
            } else if (Intent.ACTION_DATE_CHANGED.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)) {
                // Update freeze period record when clock naturally progresses to the next day
                // (ACTION_DATE_CHANGED), or when manual clock adjustment is made
                // (ACTION_TIME_CHANGED)
                updateSystemUpdateFreezePeriodsRecord(/* saveIfChanged */ true);
                final int userId = getManagedUserId(getMainUserId());
                if (userId >= 0) {
                    updatePersonalAppsSuspension(userId, mUserManager.isUserUnlocked(userId));
                }
            } else if (ACTION_PROFILE_OFF_DEADLINE.equals(action)) {
                Slogf.i(LOG_TAG, "Profile off deadline alarm was triggered");
                final int userId = getManagedUserId(getMainUserId());
                if (userId >= 0) {
                    updatePersonalAppsSuspension(userId, mUserManager.isUserUnlocked(userId));
                } else {
                    Slogf.wtf(LOG_TAG, "Got deadline alarm for nonexistent profile");
                }
            } else if (ACTION_TURN_PROFILE_ON_NOTIFICATION.equals(action)) {
                Slogf.i(LOG_TAG, "requesting to turn on the profile: " + userHandle);
                mUserManager.requestQuietModeEnabled(false, UserHandle.of(userHandle));
            }
        }

        private void sendDeviceOwnerUserCommand(String action, int userHandle) {
            synchronized (getLockObject()) {
                ActiveAdmin deviceOwner = getDeviceOwnerAdminLocked();
                if (deviceOwner != null) {
                    Bundle extras = new Bundle();
                    extras.putParcelable(Intent.EXTRA_USER, UserHandle.of(userHandle));
                    sendAdminCommandLocked(deviceOwner, action, extras, /* result */ null,
                            /* inForeground */ true);
                }
            }
        }
    };

    protected static class RestrictionsListener implements UserRestrictionsListener {
        private final Context mContext;
        private final UserManagerInternal mUserManagerInternal;
        private final DevicePolicyManagerService mDpms;

        public RestrictionsListener(
                Context context,
                UserManagerInternal userManagerInternal,
                DevicePolicyManagerService dpms) {
            mContext = context;
            mUserManagerInternal = userManagerInternal;
            mDpms = dpms;
        }

        @Override
        public void onUserRestrictionsChanged(int userId, Bundle newRestrictions,
                Bundle prevRestrictions) {
            resetCrossProfileIntentFiltersIfNeeded(userId, newRestrictions, prevRestrictions);
            resetUserVpnIfNeeded(userId, newRestrictions, prevRestrictions);
        }

        private void resetUserVpnIfNeeded(
                int userId, Bundle newRestrictions, Bundle prevRestrictions) {
            final boolean newlyEnforced =
                    !prevRestrictions.getBoolean(UserManager.DISALLOW_CONFIG_VPN)
                    && newRestrictions.getBoolean(UserManager.DISALLOW_CONFIG_VPN);
            if (newlyEnforced) {
                mDpms.clearUserConfiguredVpns(userId);
            }
        }

        private void resetCrossProfileIntentFiltersIfNeeded(
                int userId, Bundle newRestrictions, Bundle prevRestrictions) {
            if (UserRestrictionsUtils.restrictionsChanged(prevRestrictions, newRestrictions,
                    UserManager.DISALLOW_SHARE_INTO_MANAGED_PROFILE)) {
                final int parentId = mUserManagerInternal.getProfileParentId(userId);
                if (parentId == userId) {
                    return;
                }

                // Always reset filters on the parent user, which handles cross profile intent
                // filters between the parent and its profiles.
                Slogf.i(LOG_TAG, "Resetting cross-profile intent filters on restriction "
                        + "change");
                mDpms.resetDefaultCrossProfileIntentFilters(parentId);
                mContext.sendBroadcastAsUser(
                        new Intent(DevicePolicyManager.ACTION_DATA_SHARING_RESTRICTION_APPLIED),
                        UserHandle.of(userId));
            }
        }
    }

    private void clearUserConfiguredVpns(int userId) {
        final String adminConfiguredVpnPkg;
        synchronized (getLockObject()) {
            final ActiveAdmin owner = getDeviceOrProfileOwnerAdminLocked(userId);
            if (owner == null) {
                Slogf.wtf(LOG_TAG, "Admin not found");
                return;
            }
            adminConfiguredVpnPkg = owner.mAlwaysOnVpnPackage;
        }

        // Clear always-on configuration if it wasn't set by the admin.
        if (adminConfiguredVpnPkg == null) {
            mInjector.getVpnManager().setAlwaysOnVpnPackageForUser(userId, null, false, null);
        }

        // Clear app authorizations to establish VPNs. When DISALLOW_CONFIG_VPN is enforced apps
        // won't be able to get those authorizations unless it is configured by an admin.
        final List<AppOpsManager.PackageOps> allVpnOps = mInjector.getAppOpsManager()
                .getPackagesForOps(new int[] {AppOpsManager.OP_ACTIVATE_VPN});
        if (allVpnOps == null) {
            return;
        }
        for (AppOpsManager.PackageOps pkgOps : allVpnOps) {
            if (UserHandle.getUserId(pkgOps.getUid()) != userId
                    || pkgOps.getPackageName().equals(adminConfiguredVpnPkg)) {
                continue;
            }
            if (pkgOps.getOps().size() != 1) {
                Slogf.wtf(LOG_TAG, "Unexpected number of ops returned");
                continue;
            }
            final @Mode int mode = pkgOps.getOps().get(0).getMode();
            if (mode == MODE_ALLOWED) {
                Slogf.i(LOG_TAG, String.format("Revoking VPN authorization for package %s uid %d",
                        pkgOps.getPackageName(), pkgOps.getUid()));
                mInjector.getAppOpsManager().setMode(AppOpsManager.OP_ACTIVATE_VPN, pkgOps.getUid(),
                        pkgOps.getPackageName(), MODE_DEFAULT);
            }
        }
    }

    private final class UserLifecycleListener implements UserManagerInternal.UserLifecycleListener {

        @Override
        public void onUserCreated(UserInfo user, Object token) {
            mHandler.post(() -> handleNewUserCreated(user, token));
        }
    }

    private void handlePackagesChanged(@Nullable String packageName, int userHandle) {
        boolean removedAdmin = false;
        if (VERBOSE_LOG) {
            Slogf.d(LOG_TAG, "Handling package changes package " + packageName
                    + " for user " + userHandle);
        }
        DevicePolicyData policy = getUserData(userHandle);
        synchronized (getLockObject()) {
            for (int i = policy.mAdminList.size() - 1; i >= 0; i--) {
                ActiveAdmin aa = policy.mAdminList.get(i);
                try {
                    // If we're checking all packages or if the specific one we're checking matches,
                    // then check if the package and receiver still exist.
                    final String adminPackage = aa.info.getPackageName();
                    if (packageName == null || packageName.equals(adminPackage)) {
                        if (mIPackageManager.getPackageInfo(adminPackage, 0, userHandle) == null
                                || mIPackageManager.getReceiverInfo(aa.info.getComponent(),
                                PackageManager.MATCH_DIRECT_BOOT_AWARE
                                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                                userHandle) == null) {
                            Slogf.e(LOG_TAG, String.format(
                                    "Admin package %s not found for user %d, removing active admin",
                                    packageName, userHandle));
                            removedAdmin = true;
                            policy.mAdminList.remove(i);
                            policy.mAdminMap.remove(aa.info.getComponent());
                            pushActiveAdminPackagesLocked(userHandle);
                            pushMeteredDisabledPackages(userHandle);
                        }
                    }
                } catch (RemoteException re) {
                    // Shouldn't happen.
                    Slogf.wtf(LOG_TAG, "Error handling package changes", re);
                }
            }
            if (removedAdmin) {
                policy.validatePasswordOwner();
            }

            boolean removedDelegate = false;

            // Check if a delegate was removed.
            for (int i = policy.mDelegationMap.size() - 1; i >= 0; i--) {
                final String delegatePackage = policy.mDelegationMap.keyAt(i);
                if (isRemovedPackage(packageName, delegatePackage, userHandle)) {
                    policy.mDelegationMap.removeAt(i);
                    removedDelegate = true;
                }
            }

            // If it's an owner package, we may need to refresh the bound connection.
            final ComponentName owner = getOwnerComponent(userHandle);
            if ((packageName != null) && (owner != null)
                    && (owner.getPackageName().equals(packageName))) {
                startOwnerService(userHandle, "package-broadcast");
            }
            if (isCoexistenceFlagEnabled()) {
                mDevicePolicyEngine.handlePackageChanged(packageName, userHandle);
            }

            // Persist updates if the removed package was an admin or delegate.
            if (removedAdmin || removedDelegate) {
                saveSettingsLocked(policy.mUserId);
            }
        }
        if (removedAdmin) {
            // The removed admin might have disabled camera, so update user restrictions.
            pushUserRestrictions(userHandle);
        }
    }

    private void removeCredentialManagementApp(String packageName) {
        mBackgroundHandler.post(() -> {
            try (KeyChainConnection connection = mInjector.keyChainBind()) {
                IKeyChainService service = connection.getService();
                if (service.hasCredentialManagementApp()
                        && packageName.equals(service.getCredentialManagementAppPackageName())) {
                    service.removeCredentialManagementApp();
                }
            } catch (RemoteException | InterruptedException | IllegalStateException
                    | AssertionError e) {
                Slogf.e(LOG_TAG, "Unable to remove the credential management app", e);
            }
        });
    }

    private boolean isRemovedPackage(String changedPackage, String targetPackage, int userHandle) {
        try {
            return targetPackage != null
                    && (changedPackage == null || changedPackage.equals(targetPackage))
                    && mIPackageManager.getPackageInfo(targetPackage, 0, userHandle) == null;
        } catch (RemoteException e) {
            // Shouldn't happen
            Slogf.wtf(LOG_TAG, "Error checking isRemovedPackage", e);
        }

        return false;
    }

    private void handleNewPackageInstalled(String packageName, int userHandle) {
        // If personal apps were suspended by the admin, suspend the newly installed one.
        if (!getUserData(userHandle).mAppsSuspended) {
            return;
        }
        final String[] packagesToSuspend = { packageName };
        // Check if package is considered not suspendable?
        if (mInjector.getPackageManager(userHandle)
                .getUnsuspendablePackages(packagesToSuspend).length != 0) {
            Slogf.i(LOG_TAG, "Newly installed package is unsuspendable: " + packageName);
            return;
        }
        try {
            mIPackageManager.setPackagesSuspendedAsUser(packagesToSuspend, true /*suspend*/,
                    null, null, null, PLATFORM_PACKAGE_NAME, userHandle);
        } catch (RemoteException ignored) {
            // shouldn't happen.
            Slogf.wtf(LOG_TAG, "Error handling new package installed", ignored);
        }
    }

    public void setDevicePolicySafetyChecker(DevicePolicySafetyChecker safetyChecker) {
        CallerIdentity callerIdentity = getCallerIdentity();
        Preconditions.checkCallAuthorization(mIsAutomotive || isAdb(callerIdentity), "can only set "
                + "DevicePolicySafetyChecker on automotive builds or from ADB (but caller is %s)",
                callerIdentity);
        setDevicePolicySafetyCheckerUnchecked(safetyChecker);
    }

    /**
     * Used by {@code setDevicePolicySafetyChecker()} above and {@link OneTimeSafetyChecker}.
     */
    void setDevicePolicySafetyCheckerUnchecked(DevicePolicySafetyChecker safetyChecker) {
        Slogf.i(LOG_TAG, "Setting DevicePolicySafetyChecker as %s", safetyChecker);
        mSafetyChecker = safetyChecker;
        mInjector.setDevicePolicySafetyChecker(safetyChecker);
    }

    /**
     * Used by {@link OneTimeSafetyChecker} only.
     */
    DevicePolicySafetyChecker getDevicePolicySafetyChecker() {
        return mSafetyChecker;
    }

    /**
     * Checks if it's safe to execute the given {@code operation}.
     *
     * @throws UnsafeStateException if it's not safe to execute the operation.
     */
    private void checkCanExecuteOrThrowUnsafe(@DevicePolicyOperation int operation) {
        int reason = getUnsafeOperationReason(operation);
        if (reason == OPERATION_SAFETY_REASON_NONE) return;

        if (mSafetyChecker == null) {
            // Happens on CTS after it's set just once (by OneTimeSafetyChecker)
            throw new UnsafeStateException(operation, reason);
        }
        // Let mSafetyChecker customize it (for example, by explaining how to retry)
        throw mSafetyChecker.newUnsafeStateException(operation, reason);
    }

    /**
     * Returns whether it's safe to execute the given {@code operation}, and why.
     */
    @OperationSafetyReason
    int getUnsafeOperationReason(@DevicePolicyOperation int operation) {
        return mSafetyChecker == null ? OPERATION_SAFETY_REASON_NONE
                : mSafetyChecker.getUnsafeOperationReason(operation);
    }

    @Override
    public void setNextOperationSafety(@DevicePolicyOperation int operation,
            @OperationSafetyReason int reason) {
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_DEVICE_ADMINS));
        Slogf.i(LOG_TAG, "setNextOperationSafety(%s, %s)",
                DevicePolicyManager.operationToString(operation),
                DevicePolicyManager.operationSafetyReasonToString(reason));
        mSafetyChecker = new OneTimeSafetyChecker(this, operation, reason);
    }

    @Override
    public boolean isSafeOperation(@OperationSafetyReason int reason) {
        if (VERBOSE_LOG) {
            Slogf.v(LOG_TAG, "checking isSafeOperation(%s) using mSafetyChecker %s",
                    DevicePolicyManager.operationSafetyReasonToString(reason), mSafetyChecker);
        }
        return mSafetyChecker == null ? true : mSafetyChecker.isSafeOperation(reason);
    }

    // Used by DevicePolicyManagerServiceShellCommand
    List<OwnerShellData> listAllOwners() {
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_DEVICE_ADMINS));
        return mInjector.binderWithCleanCallingIdentity(() -> {
            SparseArray<DevicePolicyData> userData;

            // Gets the owners of "full users" first (device owner and profile owners)
            List<OwnerShellData> owners = mOwners.listAllOwners();
            synchronized (getLockObject()) {
                for (int i = 0; i < owners.size(); i++) {
                    OwnerShellData owner = owners.get(i);
                    owner.isAffiliated = isUserAffiliatedWithDeviceLocked(owner.userId);
                }
                userData = mUserData;
            }

            // Then the owners of profile users (managed profiles)
            for (int i = 0; i < userData.size(); i++) {
                DevicePolicyData policyData = mUserData.valueAt(i);
                int userId = userData.keyAt(i);
                int parentUserId = mUserManagerInternal.getProfileParentId(userId);
                boolean isProfile = parentUserId != userId;
                if (!isProfile) continue;
                for (int j = 0; j < policyData.mAdminList.size(); j++) {
                    ActiveAdmin admin = policyData.mAdminList.get(j);
                    OwnerShellData owner = OwnerShellData.forManagedProfileOwner(userId,
                            parentUserId, admin.info.getComponent());
                    owners.add(owner);
                }
            }

            return owners;
        });
    }

    /**
     * Unit test will subclass it to inject mocks.
     */
    @VisibleForTesting
    static class Injector {

        public final Context mContext;

        @Nullable private DevicePolicySafetyChecker mSafetyChecker;

        Injector(Context context) {
            mContext = context;
        }

        public boolean hasFeature() {
            return getPackageManager().hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);
        }

        Context createContextAsUser(UserHandle user) throws PackageManager.NameNotFoundException {
            final String packageName = mContext.getPackageName();
            return mContext.createPackageContextAsUser(packageName, 0, user);
        }

        Resources getResources() {
            return mContext.getResources();
        }

        UserManager getUserManager() {
            return UserManager.get(mContext);
        }

        UserManagerInternal getUserManagerInternal() {
            return LocalServices.getService(UserManagerInternal.class);
        }

        PackageManagerInternal getPackageManagerInternal() {
            return LocalServices.getService(PackageManagerInternal.class);
        }

        ActivityTaskManagerInternal getActivityTaskManagerInternal() {
            return LocalServices.getService(ActivityTaskManagerInternal.class);
        }

        @NonNull PermissionControllerManager getPermissionControllerManager(
                @NonNull UserHandle user) {
            if (user.equals(mContext.getUser())) {
                return mContext.getSystemService(PermissionControllerManager.class);
            } else {
                try {
                    return mContext.createPackageContextAsUser(mContext.getPackageName(), 0,
                            user).getSystemService(PermissionControllerManager.class);
                } catch (NameNotFoundException notPossible) {
                    // not possible
                    throw new IllegalStateException(notPossible);
                }
            }
        }

        UsageStatsManagerInternal getUsageStatsManagerInternal() {
            return LocalServices.getService(UsageStatsManagerInternal.class);
        }

        NetworkPolicyManagerInternal getNetworkPolicyManagerInternal() {
            return LocalServices.getService(NetworkPolicyManagerInternal.class);
        }

        NotificationManager getNotificationManager() {
            return mContext.getSystemService(NotificationManager.class);
        }

        IIpConnectivityMetrics getIIpConnectivityMetrics() {
            return (IIpConnectivityMetrics) IIpConnectivityMetrics.Stub.asInterface(
                ServiceManager.getService(IpConnectivityLog.SERVICE_NAME));
        }

        PackageManager getPackageManager() {
            return mContext.getPackageManager();
        }

        PackageManager getPackageManager(int userId) {
            try {
                return createContextAsUser(UserHandle.of(userId)).getPackageManager();
            } catch (NameNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }

        PowerManagerInternal getPowerManagerInternal() {
            return LocalServices.getService(PowerManagerInternal.class);
        }

        TelephonyManager getTelephonyManager() {
            return mContext.getSystemService(TelephonyManager.class);
        }

        TrustManager getTrustManager() {
            return (TrustManager) mContext.getSystemService(Context.TRUST_SERVICE);
        }

        AlarmManager getAlarmManager() {
            return mContext.getSystemService(AlarmManager.class);
        }

        AlarmManagerInternal getAlarmManagerInternal() {
            return LocalServices.getService(AlarmManagerInternal.class);
        }

        ConnectivityManager getConnectivityManager() {
            return mContext.getSystemService(ConnectivityManager.class);
        }

        VpnManager getVpnManager() {
            return mContext.getSystemService(VpnManager.class);
        }

        LocationManager getLocationManager() {
            return mContext.getSystemService(LocationManager.class);
        }

        IWindowManager getIWindowManager() {
            return IWindowManager.Stub
                    .asInterface(ServiceManager.getService(Context.WINDOW_SERVICE));
        }

        IActivityManager getIActivityManager() {
            return ActivityManager.getService();
        }

        IActivityTaskManager getIActivityTaskManager() {
            return ActivityTaskManager.getService();
        }

        ActivityManagerInternal getActivityManagerInternal() {
            return LocalServices.getService(ActivityManagerInternal.class);
        }

        IPackageManager getIPackageManager() {
            return AppGlobals.getPackageManager();
        }

        IPermissionManager getIPermissionManager() {
            return AppGlobals.getPermissionManager();
        }

        IBackupManager getIBackupManager() {
            return IBackupManager.Stub.asInterface(
                    ServiceManager.getService(Context.BACKUP_SERVICE));
        }

        IAudioService getIAudioService() {
            return IAudioService.Stub.asInterface(ServiceManager.getService(Context.AUDIO_SERVICE));
        }

        PersistentDataBlockManagerInternal getPersistentDataBlockManagerInternal() {
            return LocalServices.getService(PersistentDataBlockManagerInternal.class);
        }

        AppOpsManager getAppOpsManager() {
            return mContext.getSystemService(AppOpsManager.class);
        }

        LockSettingsInternal getLockSettingsInternal() {
            return LocalServices.getService(LockSettingsInternal.class);
        }

        CrossProfileApps getCrossProfileApps(@UserIdInt int userId) {
            return mContext.createContextAsUser(UserHandle.of(userId), /* flags= */ 0)
                    .getSystemService(CrossProfileApps.class);
        }

        boolean hasUserSetupCompleted(DevicePolicyData userData) {
            return userData.mUserSetupComplete;
        }

        boolean isBuildDebuggable() {
            return Build.IS_DEBUGGABLE;
        }

        LockPatternUtils newLockPatternUtils() {
            return new LockPatternUtils(mContext);
        }

        EnterpriseSpecificIdCalculator newEnterpriseSpecificIdCalculator() {
            return new EnterpriseSpecificIdCalculator(mContext);
        }

        boolean storageManagerIsFileBasedEncryptionEnabled() {
            return StorageManager.isFileEncrypted();
        }

        Looper getMyLooper() {
            return Looper.myLooper();
        }

        WifiManager getWifiManager() {
            return mContext.getSystemService(WifiManager.class);
        }

        UsbManager getUsbManager() {
            return mContext.getSystemService(UsbManager.class);
        }

        @SuppressWarnings("ResultOfClearIdentityCallNotStoredInVariable")
        long binderClearCallingIdentity() {
            return Binder.clearCallingIdentity();
        }

        void binderRestoreCallingIdentity(long token) {
            Binder.restoreCallingIdentity(token);
        }

        int binderGetCallingUid() {
            return Binder.getCallingUid();
        }

        int binderGetCallingPid() {
            return Binder.getCallingPid();
        }

        UserHandle binderGetCallingUserHandle() {
            return Binder.getCallingUserHandle();
        }

        boolean binderIsCallingUidMyUid() {
            return getCallingUid() == Process.myUid();
        }

        void binderWithCleanCallingIdentity(@NonNull ThrowingRunnable action) {
             Binder.withCleanCallingIdentity(action);
        }

        final <T> T binderWithCleanCallingIdentity(@NonNull ThrowingSupplier<T> action) {
            return Binder.withCleanCallingIdentity(action);
        }

        final int userHandleGetCallingUserId() {
            return UserHandle.getUserId(binderGetCallingUid());
        }

        void powerManagerGoToSleep(long time, int reason, int flags) {
            mContext.getSystemService(PowerManager.class).goToSleep(time, reason, flags);
        }

        void powerManagerReboot(String reason) {
            mContext.getSystemService(PowerManager.class).reboot(reason);
        }

        boolean recoverySystemRebootWipeUserData(boolean shutdown, String reason, boolean force,
                boolean wipeEuicc, boolean wipeExtRequested, boolean wipeResetProtectionData)
                        throws IOException {
            return FactoryResetter.newBuilder(mContext).setSafetyChecker(mSafetyChecker)
                    .setReason(reason).setShutdown(shutdown).setForce(force).setWipeEuicc(wipeEuicc)
                    .setWipeAdoptableStorage(wipeExtRequested)
                    .setWipeFactoryResetProtection(wipeResetProtectionData)
                    .build().factoryReset();
        }

        boolean systemPropertiesGetBoolean(String key, boolean def) {
            return SystemProperties.getBoolean(key, def);
        }

        long systemPropertiesGetLong(String key, long def) {
            return SystemProperties.getLong(key, def);
        }

        String systemPropertiesGet(String key, String def) {
            return SystemProperties.get(key, def);
        }

        String systemPropertiesGet(String key) {
            return SystemProperties.get(key);
        }

        void systemPropertiesSet(String key, String value) {
            SystemProperties.set(key, value);
        }

        boolean userManagerIsHeadlessSystemUserMode() {
            return UserManager.isHeadlessSystemUserMode();
        }

        @SuppressWarnings("AndroidFrameworkPendingIntentMutability")
        PendingIntent pendingIntentGetActivityAsUser(Context context, int requestCode,
                @NonNull Intent intent, int flags, Bundle options, UserHandle user) {
            return PendingIntent.getActivityAsUser(
                    context, requestCode, intent, flags, options, user);
        }

        @SuppressWarnings("AndroidFrameworkPendingIntentMutability")
        PendingIntent pendingIntentGetBroadcast(
                Context context, int requestCode, Intent intent, int flags) {
            return PendingIntent.getBroadcast(context, requestCode, intent, flags);
        }

        void registerContentObserver(Uri uri, boolean notifyForDescendents,
                ContentObserver observer, int userHandle) {
            mContext.getContentResolver().registerContentObserver(uri, notifyForDescendents,
                    observer, userHandle);
        }

        int settingsSecureGetIntForUser(String name, int def, int userHandle) {
            return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    name, def, userHandle);
        }

        String settingsSecureGetStringForUser(String name, int userHandle) {
            return Settings.Secure.getStringForUser(mContext.getContentResolver(), name,
                    userHandle);
        }

        void settingsSecurePutIntForUser(String name, int value, int userHandle) {
            Settings.Secure.putIntForUser(mContext.getContentResolver(),
                    name, value, userHandle);
        }

        void settingsSecurePutStringForUser(String name, String value, int userHandle) {
            Settings.Secure.putStringForUser(mContext.getContentResolver(),
                    name, value, userHandle);
        }

        void settingsGlobalPutStringForUser(String name, String value, int userHandle) {
            Settings.Global.putStringForUser(mContext.getContentResolver(),
                    name, value, userHandle);
        }

        void settingsSecurePutInt(String name, int value) {
            Settings.Secure.putInt(mContext.getContentResolver(), name, value);
        }

        int settingsGlobalGetInt(String name, int def) {
            return Settings.Global.getInt(mContext.getContentResolver(), name, def);
        }

        @Nullable
        String settingsGlobalGetString(String name) {
            return Settings.Global.getString(mContext.getContentResolver(), name);
        }

        void settingsGlobalPutInt(String name, int value) {
            Settings.Global.putInt(mContext.getContentResolver(), name, value);
        }

        void settingsSecurePutString(String name, String value) {
            Settings.Secure.putString(mContext.getContentResolver(), name, value);
        }

        void settingsGlobalPutString(String name, String value) {
            Settings.Global.putString(mContext.getContentResolver(), name, value);
        }

        void settingsSystemPutStringForUser(String name, String value, int userId) {
          Settings.System.putStringForUser(
              mContext.getContentResolver(), name, value, userId);
        }

        void securityLogSetLoggingEnabledProperty(boolean enabled) {
            SecurityLog.setLoggingEnabledProperty(enabled);
        }

        boolean securityLogGetLoggingEnabledProperty() {
            return SecurityLog.getLoggingEnabledProperty();
        }

        boolean securityLogIsLoggingEnabled() {
            return SecurityLog.isLoggingEnabled();
        }

        KeyChainConnection keyChainBind() throws InterruptedException {
            return KeyChain.bind(mContext);
        }

        KeyChainConnection keyChainBindAsUser(UserHandle user) throws InterruptedException {
            return KeyChain.bindAsUser(mContext, user);
        }

        void postOnSystemServerInitThreadPool(Runnable runnable) {
            SystemServerInitThreadPool.submit(runnable, LOG_TAG);
        }

        public TransferOwnershipMetadataManager newTransferOwnershipMetadataManager() {
            return new TransferOwnershipMetadataManager();
        }

        public void runCryptoSelfTest() {
            CryptoTestHelper.runAndLogSelfTest();
        }

        public String[] getPersonalAppsForSuspension(@UserIdInt int userId) {
            return PersonalAppsSuspensionHelper.forUser(mContext, userId)
                    .getPersonalAppsForSuspension();
        }

        public long systemCurrentTimeMillis() {
            return System.currentTimeMillis();
        }

        public boolean isChangeEnabled(long changeId, String packageName, int userId) {
            return CompatChanges.isChangeEnabled(changeId, packageName, UserHandle.of(userId));
        }

        void setDevicePolicySafetyChecker(DevicePolicySafetyChecker safetyChecker) {
            mSafetyChecker = safetyChecker;
        }

        DeviceManagementResourcesProvider getDeviceManagementResourcesProvider() {
            return new DeviceManagementResourcesProvider();
        }
    }

    /**
     * Instantiates the service.
     */
    public DevicePolicyManagerService(Context context) {
        this(new Injector(
                context.createAttributionContext(ATTRIBUTION_TAG)), new PolicyPathProvider() {});
    }

    @VisibleForTesting
    DevicePolicyManagerService(Injector injector, PolicyPathProvider pathProvider) {
        DevicePolicyManager.disableLocalCaches();

        mInjector = injector;
        mPathProvider = pathProvider;
        mContext = Objects.requireNonNull(injector.mContext);
        mHandler = new Handler(Objects.requireNonNull(injector.getMyLooper()));

        mConstantsObserver = new DevicePolicyConstantsObserver(mHandler);
        mConstantsObserver.register();
        mConstants = loadConstants();

        mUserManager = Objects.requireNonNull(injector.getUserManager());
        mUserManagerInternal = Objects.requireNonNull(injector.getUserManagerInternal());
        mUsageStatsManagerInternal = Objects.requireNonNull(
                injector.getUsageStatsManagerInternal());
        mIPackageManager = Objects.requireNonNull(injector.getIPackageManager());
        mIPermissionManager = Objects.requireNonNull(injector.getIPermissionManager());
        mTelephonyManager = Objects.requireNonNull(injector.getTelephonyManager());

        mLocalService = new LocalService();
        mLockPatternUtils = injector.newLockPatternUtils();
        mLockSettingsInternal = injector.getLockSettingsInternal();
        // TODO: why does SecurityLogMonitor need to be created even when mHasFeature == false?
        mSecurityLogMonitor = new SecurityLogMonitor(this);

        mHasFeature = mInjector.hasFeature();
        mIsWatch = mInjector.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_WATCH);
        mHasTelephonyFeature = mInjector.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        mIsAutomotive = mInjector.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
        mBackgroundHandler = BackgroundThread.getHandler();

        // Needed when mHasFeature == false, because it controls the certificate warning text.
        mCertificateMonitor = new CertificateMonitor(this, mInjector, mBackgroundHandler);

        mDeviceAdminServiceController = new DeviceAdminServiceController(this, mConstants);
        mOverlayPackagesProvider = new OverlayPackagesProvider(mContext);
        mTransferOwnershipMetadataManager = mInjector.newTransferOwnershipMetadataManager();
        mBugreportCollectionManager = new RemoteBugreportManager(this, mInjector);

        mDeviceManagementResourcesProvider = mInjector.getDeviceManagementResourcesProvider();
        mDevicePolicyManagementRoleObserver = new DevicePolicyManagementRoleObserver(mContext);
        mDevicePolicyManagementRoleObserver.register();

        // "Lite" interface is available even when the device doesn't have the feature
        LocalServices.addService(DevicePolicyManagerLiteInternal.class, mLocalService);

        // Policy version upgrade must not depend on either mOwners or mUserData, so they are
        // initialized only after performing the upgrade.
        if (mHasFeature) {
            performPolicyVersionUpgrade();
        }
        mUserData = new SparseArray<>();
        mOwners = makeOwners(injector, pathProvider);

        mDevicePolicyEngine = new DevicePolicyEngine(mContext, mDeviceAdminServiceController);

        if (!mHasFeature) {
            // Skip the rest of the initialization
            mSetupContentObserver = null;
            mContactSystemRoleHolders = Collections.emptySet();
            return;
        }

        loadOwners();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        filter.addAction(ACTION_EXPIRED_PASSWORD_NOTIFICATION);
        filter.addAction(ACTION_TURN_PROFILE_ON_NOTIFICATION);
        filter.addAction(ACTION_PROFILE_OFF_DEADLINE);
        filter.addAction(Intent.ACTION_USER_ADDED);
        filter.addAction(Intent.ACTION_USER_REMOVED);
        filter.addAction(Intent.ACTION_USER_STARTED);
        filter.addAction(Intent.ACTION_USER_STOPPED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiverAsUser(mReceiver, UserHandle.ALL, filter, null, mHandler);
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addDataScheme("package");
        mContext.registerReceiverAsUser(mReceiver, UserHandle.ALL, filter, null, mHandler);
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_ADDED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        mContext.registerReceiverAsUser(mReceiver, UserHandle.ALL, filter, null, mHandler);

        LocalServices.addService(DevicePolicyManagerInternal.class, mLocalService);

        mSetupContentObserver = new SetupContentObserver(mHandler);

        mUserManagerInternal.addUserRestrictionsListener(
                new RestrictionsListener(mContext, mUserManagerInternal, this));
        mUserManagerInternal.addUserLifecycleListener(new UserLifecycleListener());

        mDeviceManagementResourcesProvider.load();
        if (isCoexistenceFlagEnabled()) {
            mDevicePolicyEngine.load();
        }

        mContactSystemRoleHolders = fetchOemSystemHolders(/* roleResIds...= */
                com.android.internal.R.string.config_defaultSms,
                com.android.internal.R.string.config_defaultDialer,
                com.android.internal.R.string.config_systemContacts
        );

        // The binder caches are not enabled until the first invalidation.
        invalidateBinderCaches();
    }

    /**
     * Fetch the OEM System Holders for the supplied roleNames
     *
     * @param roleResIds the list of resource ids whose role holders are needed
     * @return the set of packageNames that handle the requested roles
     */
    private @NonNull Set<String> fetchOemSystemHolders(int... roleResIds) {
        Set<String> packageNames = new ArraySet<>();

        for (int roleResId : roleResIds) {
            String packageName = getDefaultRoleHolderPackageName(roleResId);
            if (packageName != null) {
                packageNames.add(packageName);
            }
        }

        return Collections.unmodifiableSet(packageNames);
    }


    private @Nullable String getDefaultRoleHolderPackageName(int resId) {
        String packageNameAndSignature = mContext.getString(resId);

        if (TextUtils.isEmpty(packageNameAndSignature)) {
            return null;
        }

        if (packageNameAndSignature.contains(":")) {
            return packageNameAndSignature.split(":")[0];
        }

        return packageNameAndSignature;
    }

    private Owners makeOwners(Injector injector, PolicyPathProvider pathProvider) {
        return new Owners(
                injector.getUserManager(), injector.getUserManagerInternal(),
                injector.getPackageManagerInternal(),
                injector.getActivityTaskManagerInternal(),
                injector.getActivityManagerInternal(), mStateCache, pathProvider);
    }

    /**
     * Invalidate the binder API caches. The invalidation itself does not require any
     * locking, but this specific call should be protected by getLockObject() to ensure
     * that the invalidation is synchronous with cached queries, for those queries that
     * are served under getLockObject().
     */
    static void invalidateBinderCaches() {
        DevicePolicyManager.invalidateBinderCaches();
    }

    /**
     * Creates and loads the policy data from xml.
     * @param userHandle the user for whom to load the policy data
     * @return
     */
    @NonNull
    DevicePolicyData getUserData(int userHandle) {
        synchronized (getLockObject()) {
            DevicePolicyData policy = mUserData.get(userHandle);
            if (policy == null) {
                policy = new DevicePolicyData(userHandle);
                mUserData.append(userHandle, policy);
                loadSettingsLocked(policy, userHandle);
                if (userHandle == UserHandle.USER_SYSTEM) {
                    mStateCache.setDeviceProvisioned(policy.mUserSetupComplete);
                }
            }
            return policy;
        }
    }

    /**
     * Creates and loads the policy data from xml for data that is shared between
     * various profiles of a user. In contrast to {@link #getUserData(int)}
     * it allows access to data of users other than the calling user.
     *
     * This function should only be used for shared data, e.g. everything regarding
     * passwords and should be removed once multiple screen locks are present.
     * @param userHandle the user for whom to load the policy data
     * @return
     */
    DevicePolicyData getUserDataUnchecked(int userHandle) {
        return mInjector.binderWithCleanCallingIdentity(() -> getUserData(userHandle));
    }

    void removeUserData(int userHandle) {
        final boolean isOrgOwned;
        synchronized (getLockObject()) {
            if (userHandle == UserHandle.USER_SYSTEM) {
                Slogf.w(LOG_TAG, "Tried to remove device policy file for user 0! Ignoring.");
                return;
            }
            updatePasswordQualityCacheForUserGroup(userHandle);
            mPolicyCache.onUserRemoved(userHandle);

            if (isManagedProfile(userHandle)) {
                clearManagedProfileApnUnchecked();
            }
            isOrgOwned = mOwners.isProfileOwnerOfOrganizationOwnedDevice(userHandle);

            mOwners.removeProfileOwner(userHandle);
            mOwners.writeProfileOwner(userHandle);
            pushScreenCapturePolicy(userHandle);

            DevicePolicyData policy = mUserData.get(userHandle);
            if (policy != null) {
                mUserData.remove(userHandle);
            }

            File policyFile =
                    new File(mPathProvider.getUserSystemDirectory(userHandle), DEVICE_POLICIES_XML);
            policyFile.delete();
            Slogf.i(LOG_TAG, "Removed device policy file " + policyFile.getAbsolutePath());
        }
        if (isOrgOwned) {
            final UserInfo primaryUser = mUserManager.getPrimaryUser();
            if (primaryUser != null) {
                clearOrgOwnedProfileOwnerDeviceWidePolicies(primaryUser.id);
            } else {
                Slogf.wtf(LOG_TAG, "Was unable to get primary user.");
            }
        }
    }

    /**
     * Load information about device and profile owners of the device, populating mOwners and
     * pushing owner info to other system services. This is called at a fairly early stage of
     * system server initialiation (via DevicePolicyManagerService's ctor), so care should to
     * be taken to not interact with system services that are initialiated after DPMS.
     * onLockSettingsReady() is a safer place to do initialization work not critical during
     * the first boot stage.
     * Note this only loads the list of owners, and not their actual policy (DevicePolicyData).
     * The policy is normally loaded lazily when it's first accessed. In several occasions
     * the list of owners is necessary for providing callers with aggregated policies across
     * multiple owners, hence the owner list is loaded as part of DPMS's construction here.
     */
    void loadOwners() {
        synchronized (getLockObject()) {
            mOwners.load();
            setDeviceOwnershipSystemPropertyLocked();
            if (mOwners.hasDeviceOwner()) {
                setGlobalSettingDeviceOwnerType(
                        mOwners.getDeviceOwnerType(mOwners.getDeviceOwnerPackageName()));
            }
        }
    }

    /**
     * Creates a new {@link CallerIdentity} object to represent the caller's identity.
     */
    private CallerIdentity getCallerIdentity() {
        return getCallerIdentity(null, null);
    }

    /**
     * Creates a new {@link CallerIdentity} object to represent the caller's identity.
     */
    private CallerIdentity getCallerIdentity(@Nullable String callerPackage) {

        return getCallerIdentity(null, callerPackage);
    }

    /**
     * Creates a new {@link CallerIdentity} object to represent the caller's identity.
     * The component name should be an active admin for the calling user.
     */
    @VisibleForTesting
    CallerIdentity getCallerIdentity(@Nullable ComponentName adminComponent) {
        return getCallerIdentity(adminComponent, null);
    }

    /**
     * Creates a new {@link CallerIdentity} object to represent the caller's identity.
     * If {@code adminComponent} is provided, it's validated against the list of known
     * active admins and caller uid. If {@code callerPackage} is provided, it's validated
     * against the caller uid. If a valid {@code adminComponent} is provided but not
     * {@code callerPackage}, the package name of the {@code adminComponent} is used instead.
     */
    @VisibleForTesting
    CallerIdentity getCallerIdentity(@Nullable ComponentName adminComponent,
            @Nullable String callerPackage) {
        final int callerUid = mInjector.binderGetCallingUid();

        if (callerPackage != null) {
            if (!isCallingFromPackage(callerPackage, callerUid)) {
                throw new SecurityException(
                        String.format("Caller with uid %d is not %s", callerUid, callerPackage));
            }
        }

        if (adminComponent != null) {
            final DevicePolicyData policy = getUserData(UserHandle.getUserId(callerUid));
            ActiveAdmin admin = policy.mAdminMap.get(adminComponent);

            // Throwing combined exception message for both the cases here, because from different
            // security exceptions it could be deduced if particular package is admin package.
            if (admin == null || admin.getUid() != callerUid) {
                throw new SecurityException(String.format(
                        "Admin %s does not exist or is not owned by uid %d", adminComponent,
                        callerUid));
            }
            if (callerPackage != null) {
                Preconditions.checkArgument(callerPackage.equals(adminComponent.getPackageName()));
            } else {
                callerPackage = adminComponent.getPackageName();
            }
        }

        return new CallerIdentity(callerUid, callerPackage, adminComponent);
    }

    /**
     * Checks if the device is in COMP mode, and if so migrates it to managed profile on a
     * corporate owned device.
     */
    @GuardedBy("getLockObject()")
    private void migrateToProfileOnOrganizationOwnedDeviceIfCompLocked() {
        if (VERBOSE_LOG) Slogf.d(LOG_TAG, "Checking whether we need to migrate COMP ");
        final int doUserId = mOwners.getDeviceOwnerUserId();
        if (doUserId == UserHandle.USER_NULL) {
            if (VERBOSE_LOG) Slogf.d(LOG_TAG, "No DO found, skipping migration.");
            return;
        }

        final List<UserInfo> profiles = mUserManager.getProfiles(doUserId);
        if (profiles.size() != 2) {
            if (profiles.size() == 1) {
                if (VERBOSE_LOG) Slogf.d(LOG_TAG, "Profile not found, skipping migration.");
            } else {
                Slogf.wtf(LOG_TAG, "Found " + profiles.size() + " profiles, skipping migration");
            }
            return;
        }

        final int poUserId = getManagedUserId(doUserId);
        if (poUserId < 0) {
            Slogf.wtf(LOG_TAG, "Found DO and a profile, but it is not managed, skipping migration");
            return;
        }

        final ActiveAdmin doAdmin = getDeviceOwnerAdminLocked();
        final ActiveAdmin poAdmin = getProfileOwnerAdminLocked(poUserId);
        if (doAdmin == null || poAdmin == null) {
            Slogf.wtf(LOG_TAG, "Failed to get either PO or DO admin, aborting migration.");
            return;
        }

        final ComponentName doAdminComponent = mOwners.getDeviceOwnerComponent();
        final ComponentName poAdminComponent = mOwners.getProfileOwnerComponent(poUserId);
        if (doAdminComponent == null || poAdminComponent == null) {
            Slogf.wtf(LOG_TAG, "Cannot find PO or DO component name, aborting migration.");
            return;
        }
        if (!doAdminComponent.getPackageName().equals(poAdminComponent.getPackageName())) {
            Slogf.e(LOG_TAG, "DO and PO are different packages, aborting migration.");
            return;
        }

        Slogf.i(LOG_TAG, "Migrating COMP to PO on a corp owned device; primary user: %d; "
                + "profile: %d", doUserId, poUserId);

        Slogf.i(LOG_TAG, "Giving the PO additional power...");
        setProfileOwnerOnOrganizationOwnedDeviceUncheckedLocked(poAdminComponent, poUserId, true);
        Slogf.i(LOG_TAG, "Migrating DO policies to PO...");
        moveDoPoliciesToProfileParentAdminLocked(doAdmin, poAdmin.getParentActiveAdmin());
        migratePersonalAppSuspensionLocked(doUserId, poUserId, poAdmin);
        saveSettingsLocked(poUserId);
        Slogf.i(LOG_TAG, "Clearing the DO...");
        final ComponentName doAdminReceiver = doAdmin.info.getComponent();
        clearDeviceOwnerLocked(doAdmin, doUserId);
        Slogf.i(LOG_TAG, "Removing admin artifacts...");
        removeAdminArtifacts(doAdminReceiver, doUserId);
        Slogf.i(LOG_TAG, "Uninstalling the DO...");
        uninstallOrDisablePackage(doAdminComponent.getPackageName(), doUserId);
        Slogf.i(LOG_TAG, "Migration complete.");

        // Note: KeyChain keys are not removed and will remain accessible for the apps that have
        // been given grants to use them.

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.COMP_TO_ORG_OWNED_PO_MIGRATED)
                .setAdmin(poAdminComponent)
                .write();
    }

    @GuardedBy("getLockObject()")
    private void migratePersonalAppSuspensionLocked(
            int doUserId, int poUserId, ActiveAdmin poAdmin) {
        final PackageManagerInternal pmi = mInjector.getPackageManagerInternal();
        if (!pmi.isSuspendingAnyPackages(PLATFORM_PACKAGE_NAME, doUserId)) {
            Slogf.i(LOG_TAG, "DO is not suspending any apps.");
            return;
        }

        if (getTargetSdk(poAdmin.info.getPackageName(), poUserId) >= Build.VERSION_CODES.R) {
            Slogf.i(LOG_TAG, "PO is targeting R+, keeping personal apps suspended.");
            getUserData(doUserId).mAppsSuspended = true;
            poAdmin.mSuspendPersonalApps = true;
        } else {
            Slogf.i(LOG_TAG, "PO isn't targeting R+, unsuspending personal apps.");
            pmi.unsuspendForSuspendingPackage(PLATFORM_PACKAGE_NAME, doUserId);
        }
    }

    private void uninstallOrDisablePackage(String packageName, @UserIdInt int userId) {
        final ApplicationInfo appInfo;
        try {
            appInfo = mIPackageManager.getApplicationInfo(
                    packageName, MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE, userId);
        } catch (RemoteException e) {
            // Shouldn't happen.
            Slogf.wtf(LOG_TAG, "Error getting application info", e);
            return;
        }
        if (appInfo == null) {
            Slogf.wtf(LOG_TAG, "Failed to get package info for " + packageName);
            return;
        }
        if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            Slogf.i(LOG_TAG, "Package %s is pre-installed, marking disabled until used",
                    packageName);
            mContext.getPackageManager().setApplicationEnabledSetting(packageName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED, /* flags= */ 0);
            return;
        }

        final IIntentSender.Stub mLocalSender = new IIntentSender.Stub() {
            @Override
            public void send(int code, Intent intent, String resolvedType, IBinder allowlistToken,
                    IIntentReceiver finishedReceiver, String requiredPermission, Bundle options) {
                final int status = intent.getIntExtra(
                        PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                if (status == PackageInstaller.STATUS_SUCCESS) {
                    Slogf.i(LOG_TAG, "Package %s uninstalled for user %d", packageName, userId);
                } else {
                    Slogf.e(LOG_TAG, "Failed to uninstall %s; status: %d", packageName, status);
                }
            }
        };

        final PackageInstaller pi = mInjector.getPackageManager(userId).getPackageInstaller();
        pi.uninstall(packageName, /* flags= */ 0, new IntentSender((IIntentSender) mLocalSender));
    }

    @GuardedBy("getLockObject()")
    private void moveDoPoliciesToProfileParentAdminLocked(
            ActiveAdmin doAdmin, ActiveAdmin parentAdmin) {
        // The following policies can be already controlled via parent instance, skip if so.
        if (parentAdmin.mPasswordPolicy.quality == PASSWORD_QUALITY_UNSPECIFIED) {
            parentAdmin.mPasswordPolicy = doAdmin.mPasswordPolicy;
        }
        if (parentAdmin.passwordHistoryLength == ActiveAdmin.DEF_PASSWORD_HISTORY_LENGTH) {
            parentAdmin.passwordHistoryLength = doAdmin.passwordHistoryLength;
        }
        if (parentAdmin.passwordExpirationTimeout == ActiveAdmin.DEF_PASSWORD_HISTORY_LENGTH) {
            parentAdmin.passwordExpirationTimeout = doAdmin.passwordExpirationTimeout;
        }
        if (parentAdmin.maximumFailedPasswordsForWipe
                == ActiveAdmin.DEF_MAXIMUM_FAILED_PASSWORDS_FOR_WIPE) {
            parentAdmin.maximumFailedPasswordsForWipe = doAdmin.maximumFailedPasswordsForWipe;
        }
        if (parentAdmin.maximumTimeToUnlock == ActiveAdmin.DEF_MAXIMUM_TIME_TO_UNLOCK) {
            parentAdmin.maximumTimeToUnlock = doAdmin.maximumTimeToUnlock;
        }
        if (parentAdmin.strongAuthUnlockTimeout
                == DevicePolicyManager.DEFAULT_STRONG_AUTH_TIMEOUT_MS) {
            parentAdmin.strongAuthUnlockTimeout = doAdmin.strongAuthUnlockTimeout;
        }
        parentAdmin.disabledKeyguardFeatures |=
                doAdmin.disabledKeyguardFeatures & PROFILE_KEYGUARD_FEATURES_AFFECT_OWNER;

        parentAdmin.trustAgentInfos.putAll(doAdmin.trustAgentInfos);

        // The following policies weren't available to PO, but will be available after migration.
        parentAdmin.disableCamera = doAdmin.disableCamera;
        parentAdmin.disableScreenCapture = doAdmin.disableScreenCapture;
        parentAdmin.accountTypesWithManagementDisabled.addAll(
                doAdmin.accountTypesWithManagementDisabled);

        moveDoUserRestrictionsToCopeParent(doAdmin, parentAdmin);

        // From Android 11, {@link setAutoTimeRequired} is no longer used. The user restriction
        // {@link UserManager#DISALLOW_CONFIG_DATE_TIME} should be used to enforce auto time
        // settings instead.
        if (doAdmin.requireAutoTime) {
            parentAdmin.ensureUserRestrictions().putBoolean(
                    UserManager.DISALLOW_CONFIG_DATE_TIME, true);
        }
    }

    private void moveDoUserRestrictionsToCopeParent(ActiveAdmin doAdmin, ActiveAdmin parentAdmin) {
        if (doAdmin.userRestrictions == null) {
            return;
        }
        for (final String restriction : doAdmin.userRestrictions.keySet()) {
            if (UserRestrictionsUtils.canProfileOwnerOfOrganizationOwnedDeviceChange(restriction)) {
                parentAdmin.ensureUserRestrictions().putBoolean(
                        restriction, doAdmin.userRestrictions.getBoolean(restriction));
            }
        }
    }

    /**
     * If the device is in Device Owner mode, apply the restriction on adding
     * a managed profile.
     */
    @GuardedBy("getLockObject()")
    private void applyProfileRestrictionsIfDeviceOwnerLocked() {
        final int doUserId = mOwners.getDeviceOwnerUserId();
        if (doUserId == UserHandle.USER_NULL) {
            if (VERBOSE_LOG) Slogf.d(LOG_TAG, "No DO found, skipping application of restriction.");
            return;
        }

        for (UserInfo userInfo : mUserManager.getUsers()) {
            UserHandle userHandle = userInfo.getUserHandle();
            // Based on  CDD : https://source.android.com/compatibility/12/android-12-cdd#95_multi-user_support,
            // creation of clone profile is not allowed in case device owner is set.
            // Enforcing this restriction on setting up of device owner.
            if (!mUserManager.hasUserRestriction(
                    UserManager.DISALLOW_ADD_CLONE_PROFILE, userHandle)) {
                mUserManager.setUserRestriction(UserManager.DISALLOW_ADD_CLONE_PROFILE, true,
                        userHandle);
            }
            // Creation of managed profile is restricted in case device owner is set, enforcing this
            // restriction by setting user level restriction at time of device owner setup.
            if (!mUserManager.hasUserRestriction(
                    UserManager.DISALLOW_ADD_MANAGED_PROFILE, userHandle)) {
                mUserManager.setUserRestriction(UserManager.DISALLOW_ADD_MANAGED_PROFILE, true,
                        userHandle);
            }
        }
    }

    /** Apply default restrictions that haven't been applied to profile owners yet. */
    private void maybeSetDefaultProfileOwnerUserRestrictions() {
        synchronized (getLockObject()) {
            for (final int userId : mOwners.getProfileOwnerKeys()) {
                final ActiveAdmin profileOwner = getProfileOwnerAdminLocked(userId);
                // The following restrictions used to be applied to managed profiles by different
                // means (via Settings or by disabling components). Now they are proper user
                // restrictions so we apply them to managed profile owners. Non-managed secondary
                // users didn't have those restrictions so we skip them to keep existing behavior.
                if (profileOwner == null || !mUserManager.isManagedProfile(userId)) {
                    continue;
                }
                maybeSetDefaultRestrictionsForAdminLocked(userId, profileOwner,
                        UserRestrictionsUtils.getDefaultEnabledForManagedProfiles());
                ensureUnknownSourcesRestrictionForProfileOwnerLocked(
                        userId, profileOwner, false /* newOwner */);
            }
        }
    }

    /**
     * Checks whether {@link UserManager#DISALLOW_INSTALL_UNKNOWN_SOURCES} should be added to the
     * set of restrictions for this profile owner.
     */
    private void ensureUnknownSourcesRestrictionForProfileOwnerLocked(int userId,
            ActiveAdmin profileOwner, boolean newOwner) {
        if (newOwner || mInjector.settingsSecureGetIntForUser(
                Settings.Secure.UNKNOWN_SOURCES_DEFAULT_REVERSED, 0, userId) != 0) {
            profileOwner.ensureUserRestrictions().putBoolean(
                    UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, true);
            saveUserRestrictionsLocked(userId);
            mInjector.settingsSecurePutIntForUser(
                    Settings.Secure.UNKNOWN_SOURCES_DEFAULT_REVERSED, 0, userId);
        }
    }

    /**
     * Apply default restrictions that haven't been applied to a given admin yet.
     */
    private void maybeSetDefaultRestrictionsForAdminLocked(
            int userId, ActiveAdmin admin, Set<String> defaultRestrictions) {
        if (defaultRestrictions.equals(admin.defaultEnabledRestrictionsAlreadySet)) {
            return; // The same set of default restrictions has been already applied.
        }
        Slogf.i(LOG_TAG, "New user restrictions need to be set by default for user " + userId);

        if (VERBOSE_LOG) {
            Slogf.d(LOG_TAG, "Default enabled restrictions: "
                    + defaultRestrictions
                    + ". Restrictions already enabled: "
                    + admin.defaultEnabledRestrictionsAlreadySet);
        }

        final Set<String> restrictionsToSet = new ArraySet<>(defaultRestrictions);
        restrictionsToSet.removeAll(admin.defaultEnabledRestrictionsAlreadySet);
        if (!restrictionsToSet.isEmpty()) {
            for (final String restriction : restrictionsToSet) {
                admin.ensureUserRestrictions().putBoolean(restriction, true);
            }
            admin.defaultEnabledRestrictionsAlreadySet.addAll(restrictionsToSet);
            Slogf.i(LOG_TAG, "Enabled the following restrictions by default: " + restrictionsToSet);
            saveUserRestrictionsLocked(userId);
        }
    }

    private void setDeviceOwnershipSystemPropertyLocked() {
        final boolean deviceProvisioned =
                mInjector.settingsGlobalGetInt(Settings.Global.DEVICE_PROVISIONED, 0) != 0;
        final boolean hasDeviceOwner = mOwners.hasDeviceOwner();
        final boolean hasOrgOwnedProfile = isOrganizationOwnedDeviceWithManagedProfile();
        // If the device is not provisioned and there is currently no management, do not set the
        // read-only system property yet, since device owner / org-owned profile may still be
        // provisioned.
        if (!hasDeviceOwner && !hasOrgOwnedProfile && !deviceProvisioned) {
            return;
        }
        final String value = Boolean.toString(hasDeviceOwner || hasOrgOwnedProfile);
        final String currentVal = mInjector.systemPropertiesGet(PROPERTY_ORGANIZATION_OWNED, null);
        if (TextUtils.isEmpty(currentVal)) {
            Slogf.i(LOG_TAG, "Set ro.organization_owned property to " + value);
            mInjector.systemPropertiesSet(PROPERTY_ORGANIZATION_OWNED, value);
        } else if (!value.equals(currentVal)) {
            Slogf.w(LOG_TAG, "Cannot change existing ro.organization_owned to " + value);
        }
    }

    private void maybeStartSecurityLogMonitorOnActivityManagerReady() {
        synchronized (getLockObject()) {
            if (mInjector.securityLogIsLoggingEnabled()) {
                mSecurityLogMonitor.start(getSecurityLoggingEnabledUser());
                mInjector.runCryptoSelfTest();
                maybePauseDeviceWideLoggingLocked();
            }
        }
    }

    /**
     * Fix left-over restrictions and auto-time policy during COMP -> COPE migration.
     *
     * When a COMP device with requireAutoTime policy set was migrated to an
     * organization-owned profile, a DISALLOW_CONFIG_DATE_TIME restriction is set
     * on user 0 from the DO user, which becomes unremovable by the organization-owned
     * profile owner. Fix this by force removing that restriction. Also revert the
     * parentAdmin.requireAutoTime bit (since the COPE PO cannot unset this bit)
     * and replace it with DISALLOW_CONFIG_DATE_TIME on the correct
     * admin, in line with the deprecation recommendation of setAutoTimeRequired().
     */
    private void fixupAutoTimeRestrictionDuringOrganizationOwnedDeviceMigration() {
        for (UserInfo ui : mUserManager.getUsers()) {
            final int userId = ui.id;
            if (isProfileOwnerOfOrganizationOwnedDevice(userId)) {
                final ActiveAdmin parent = getProfileOwnerAdminLocked(userId).parentAdmin;
                if (parent != null && parent.requireAutoTime) {
                    // Remove deprecated requireAutoTime
                    parent.requireAutoTime = false;
                    saveSettingsLocked(userId);

                    // Remove user restrictions set by the device owner before the upgrade to
                    // Android 11.
                    mUserManagerInternal.setDevicePolicyUserRestrictions(UserHandle.USER_SYSTEM,
                            new Bundle(), new RestrictionsSet(), /* isDeviceOwner */ false);

                    // Apply user restriction to parent active admin instead
                    parent.ensureUserRestrictions().putBoolean(
                            UserManager.DISALLOW_CONFIG_DATE_TIME, true);
                    pushUserRestrictions(userId);
                }
            }
        }
    }

    /**
     * Set an alarm for an upcoming event - expiration warning, expiration, or post-expiration
     * reminders.  Clears alarm if no expirations are configured.
     */
    private void setExpirationAlarmCheckLocked(Context context, int userHandle, boolean parent) {
        final long expiration = getPasswordExpirationLocked(null, userHandle, parent);
        final long now = System.currentTimeMillis();
        final long timeToExpire = expiration - now;
        final long alarmTime;
        if (expiration == 0) {
            // No expirations are currently configured:  Cancel alarm.
            alarmTime = 0;
        } else if (timeToExpire <= 0) {
            // The password has already expired:  Repeat every 24 hours.
            alarmTime = now + MS_PER_DAY;
        } else {
            // Selecting the next alarm time:  Roll forward to the next 24 hour multiple before
            // the expiration time.
            long alarmInterval = timeToExpire % MS_PER_DAY;
            if (alarmInterval == 0) {
                alarmInterval = MS_PER_DAY;
            }
            alarmTime = now + alarmInterval;
        }

        mInjector.binderWithCleanCallingIdentity(() -> {
            int affectedUserHandle = parent ? getProfileParentId(userHandle) : userHandle;
            AlarmManager am = mInjector.getAlarmManager();
            // Broadcast alarms sent by system are immutable
            PendingIntent pi = PendingIntent.getBroadcastAsUser(context, REQUEST_EXPIRE_PASSWORD,
                    new Intent(ACTION_EXPIRED_PASSWORD_NOTIFICATION),
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT
                            | PendingIntent.FLAG_IMMUTABLE,
                    UserHandle.of(affectedUserHandle));
            am.cancel(pi);
            if (alarmTime != 0) {
                am.set(AlarmManager.RTC, alarmTime, pi);
            }
        });
    }

    ActiveAdmin getActiveAdminUncheckedLocked(ComponentName who, int userHandle) {
        ensureLocked();
        ActiveAdmin admin = getUserData(userHandle).mAdminMap.get(who);
        if (admin != null
                && who.getPackageName().equals(admin.info.getActivityInfo().packageName)
                && who.getClassName().equals(admin.info.getActivityInfo().name)) {
            return admin;
        }
        return null;
    }

    ActiveAdmin getActiveAdminUncheckedLocked(ComponentName who, int userHandle, boolean parent) {
        ensureLocked();
        if (parent) {
            Preconditions.checkCallAuthorization(isManagedProfile(userHandle),
                    "You can not call APIs on the parent profile outside a managed profile, "
                            + "userId = %d", userHandle);
        }
        ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
        if (admin != null && parent) {
            admin = admin.getParentActiveAdmin();
        }
        return admin;
    }

    ActiveAdmin getActiveAdminForCallerLocked(ComponentName who, int reqPolicy)
            throws SecurityException {
        return getActiveAdminOrCheckPermissionForCallerLocked(who,
                reqPolicy, /* permission= */ null);
    }

    ActiveAdmin getDeviceOwnerLocked(@UserIdInt int userId) {
        ensureLocked();
        ComponentName doComponent = mOwners.getDeviceOwnerComponent();
        ActiveAdmin doAdmin = getUserData(userId).mAdminMap.get(doComponent);
        return doAdmin;
    }

    ActiveAdmin getProfileOwnerLocked(@UserIdInt int userId) {
        ensureLocked();
        final ComponentName poAdminComponent = mOwners.getProfileOwnerComponent(userId);
        ActiveAdmin poAdmin = getUserData(userId).mAdminMap.get(poAdminComponent);
        return poAdmin;
    }

    @NonNull ActiveAdmin getOrganizationOwnedProfileOwnerLocked(final CallerIdentity caller) {
        Preconditions.checkCallAuthorization(
                mOwners.isProfileOwnerOfOrganizationOwnedDevice(caller.getUserId()),
                "Caller %s is not an admin of an org-owned device",
                caller.getComponentName());
        final ActiveAdmin profileOwner = getProfileOwnerLocked(caller.getUserId());

        return profileOwner;
    }

    ActiveAdmin getProfileOwnerOrDeviceOwnerLocked(@UserIdInt int userId) {
        ensureLocked();
        // Try to find an admin which can use reqPolicy
        final ComponentName poAdminComponent = mOwners.getProfileOwnerComponent(userId);

        if (poAdminComponent != null) {
            return getProfileOwnerLocked(userId);
        }

        return getDeviceOwnerLocked(userId);
    }

    @NonNull ActiveAdmin getParentOfAdminIfRequired(ActiveAdmin admin, boolean parent) {
        Objects.requireNonNull(admin);
        return parent ? admin.getParentActiveAdmin() : admin;
    }

    /**
     * Finds an active admin for the caller then checks {@code permission} if admin check failed.
     *
     * @return an active admin or {@code null} if there is no active admin but
     * {@code permission} is granted
     * @throws SecurityException if caller neither has an active admin nor {@code permission}
     */
    @Nullable
    ActiveAdmin getActiveAdminOrCheckPermissionForCallerLocked(
            ComponentName who,
            int reqPolicy,
            @Nullable String permission) throws SecurityException {
        ensureLocked();
        final CallerIdentity caller = getCallerIdentity();

        ActiveAdmin result = getActiveAdminWithPolicyForUidLocked(who, reqPolicy, caller.getUid());
        if (result != null) {
            return result;
        } else if (permission != null && hasCallingPermission(permission)) {
            return null;
        }

        // Code for handling failure from getActiveAdminWithPolicyForUidLocked to find an admin
        // that satisfies the required policy.
        // Throws a security exception with the right error message.
        if (who != null) {
            final DevicePolicyData policy = getUserData(caller.getUserId());
            ActiveAdmin admin = policy.mAdminMap.get(who);
            final boolean isDeviceOwner = isDeviceOwner(admin.info.getComponent(),
                    caller.getUserId());
            final boolean isProfileOwner = isProfileOwner(admin.info.getComponent(),
                    caller.getUserId());

            if (DA_DISALLOWED_POLICIES.contains(reqPolicy) && !isDeviceOwner && !isProfileOwner) {
                throw new SecurityException("Admin " + admin.info.getComponent()
                        + " is not a device owner or profile owner, so may not use policy: "
                        + admin.info.getTagForPolicy(reqPolicy));
            }
            throw new SecurityException("Admin " + admin.info.getComponent()
                    + " did not specify uses-policy for: "
                    + admin.info.getTagForPolicy(reqPolicy));
        } else {
            throw new SecurityException("No active admin owned by uid "
                    + caller.getUid() + " for policy #" + reqPolicy + (permission == null ? ""
                    : ", which doesn't have " + permission));
        }
    }

    ActiveAdmin getActiveAdminForCallerLocked(@Nullable ComponentName who, int reqPolicy,
            boolean parent) throws SecurityException {
        return getActiveAdminOrCheckPermissionForCallerLocked(
                who, reqPolicy, parent, /* permission= */ null);
    }

    /**
     * Finds an active admin for the caller then checks {@code permission} if admin check failed.
     *
     * @return an active admin or {@code null} if there is no active admin but
     * {@code permission} is granted
     * @throws SecurityException if caller neither has an active admin nor {@code permission}
     */
    @Nullable
    ActiveAdmin getActiveAdminOrCheckPermissionForCallerLocked(
            @Nullable ComponentName who,
            int reqPolicy,
            boolean parent,
            @Nullable String permission) throws SecurityException {
        ensureLocked();
        if (parent) {
            Preconditions.checkCallingUser(isManagedProfile(getCallerIdentity().getUserId()));
        }
        ActiveAdmin admin = getActiveAdminOrCheckPermissionForCallerLocked(
                who, reqPolicy, permission);
        return parent ? admin.getParentActiveAdmin() : admin;
    }

    /**
     * Find the admin for the component and userId bit of the uid, then check
     * the admin's uid matches the uid.
     */
    private ActiveAdmin getActiveAdminForUidLocked(ComponentName who, int uid) {
        ensureLocked();
        final int userId = UserHandle.getUserId(uid);
        final DevicePolicyData policy = getUserData(userId);
        ActiveAdmin admin = policy.mAdminMap.get(who);
        if (admin == null) {
            throw new SecurityException("No active admin " + who + " for UID " + uid);
        }
        if (admin.getUid() != uid) {
            throw new SecurityException("Admin " + who + " is not owned by uid " + uid);
        }
        return admin;
    }

    /**
     * Returns the active admin for the user of the caller as denoted by uid, which implements
     * the {@code reqPolicy}.
     *
     * The {@code who} parameter is used as a hint:
     * If provided, it must be the component name of the active admin for that user and the caller
     * uid must match the uid of the admin.
     * If not provided, iterate over all of the active admins in the DevicePolicyData for that user
     * and return the one with the uid specified as parameter, and has the policy specified.
     */
    @Nullable
    private ActiveAdmin getActiveAdminWithPolicyForUidLocked(ComponentName who, int reqPolicy,
            int uid) {
        ensureLocked();
        // Try to find an admin which can use reqPolicy
        final int userId = UserHandle.getUserId(uid);
        final DevicePolicyData policy = getUserData(userId);
        if (who != null) {
            ActiveAdmin admin = policy.mAdminMap.get(who);
            if (admin == null || admin.getUid() != uid) {
                throw new SecurityException(
                        "Admin " + who + " is not active or not owned by uid " + uid);
            }
            if (isActiveAdminWithPolicyForUserLocked(admin, reqPolicy, userId)) {
                return admin;
            }
        } else {
            for (ActiveAdmin admin : policy.mAdminList) {
                if (admin.getUid() == uid && isActiveAdminWithPolicyForUserLocked(admin, reqPolicy,
                        userId)) {
                    return admin;
                }
            }
        }

        return null;
    }

    @VisibleForTesting
    boolean isActiveAdminWithPolicyForUserLocked(ActiveAdmin admin, int reqPolicy,
            int userId) {
        ensureLocked();
        final boolean ownsDevice = isDeviceOwner(admin.info.getComponent(), userId);
        final boolean ownsProfile = isProfileOwner(admin.info.getComponent(), userId);

        boolean allowedToUsePolicy = ownsDevice || ownsProfile
                || !DA_DISALLOWED_POLICIES.contains(reqPolicy)
                || getTargetSdk(admin.info.getPackageName(), userId) < Build.VERSION_CODES.Q;
        return allowedToUsePolicy && admin.info.usesPolicy(reqPolicy);
    }

    void sendAdminCommandLocked(ActiveAdmin admin, String action) {
        sendAdminCommandLocked(admin, action, null);
    }

    void sendAdminCommandLocked(ActiveAdmin admin, String action, BroadcastReceiver result) {
        sendAdminCommandLocked(admin, action, null, result);
    }

    void sendAdminCommandLocked(ActiveAdmin admin, String action, Bundle adminExtras,
            BroadcastReceiver result) {
        sendAdminCommandLocked(admin, action, adminExtras, result, false);
    }

    /**
     * Send an update to one specific admin, get notified when that admin returns a result.
     *
     * @return whether the broadcast was successfully sent
     */
    boolean sendAdminCommandLocked(ActiveAdmin admin, String action, Bundle adminExtras,
            BroadcastReceiver result, boolean inForeground) {
        Intent intent = new Intent(action);
        intent.setComponent(admin.info.getComponent());
        if (UserManager.isDeviceInDemoMode(mContext)) {
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        }
        if (action.equals(DeviceAdminReceiver.ACTION_PASSWORD_EXPIRING)) {
            intent.putExtra("expiration", admin.passwordExpirationDate);
        }
        if (inForeground) {
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        }
        if (adminExtras != null) {
            intent.putExtras(adminExtras);
        }
        if (mInjector.getPackageManager().queryBroadcastReceiversAsUser(
                intent,
                PackageManager.MATCH_DEBUG_TRIAGED_MISSING,
                admin.getUserHandle()).isEmpty()) {
            return false;
        }

        final BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setBackgroundActivityStartsAllowed(true);

        if (result != null) {
            mContext.sendOrderedBroadcastAsUser(intent, admin.getUserHandle(),
                    null, AppOpsManager.OP_NONE, options.toBundle(),
                    result, mHandler, Activity.RESULT_OK, null, null);
        } else {
            mContext.sendBroadcastAsUser(intent, admin.getUserHandle(), null, options.toBundle());
        }

        return true;
    }

    /**
     * Send an update to all admins of a user that enforce a specified policy.
     */
    void sendAdminCommandLocked(String action, int reqPolicy, int userHandle, Bundle adminExtras) {
        final DevicePolicyData policy = getUserData(userHandle);
        final int count = policy.mAdminList.size();
        for (int i = 0; i < count; i++) {
            final ActiveAdmin admin = policy.mAdminList.get(i);
            if (admin.info.usesPolicy(reqPolicy)) {
                sendAdminCommandLocked(admin, action, adminExtras, null);
            }
        }
    }

    /**
     * Send an update intent to all admins of a user and its profiles. Only send to admins that
     * enforce a specified policy.
     */
    private void sendAdminCommandToSelfAndProfilesLocked(String action, int reqPolicy,
            int userHandle, Bundle adminExtras) {
        int[] profileIds = mUserManager.getProfileIdsWithDisabled(userHandle);
        for (int profileId : profileIds) {
            sendAdminCommandLocked(action, reqPolicy, profileId, adminExtras);
        }
    }

    /**
     * Sends a broadcast to each profile that share the password unlock with the given user id.
     */
    private void sendAdminCommandForLockscreenPoliciesLocked(
            String action, int reqPolicy, int userHandle) {
        final Bundle extras = new Bundle();
        extras.putParcelable(Intent.EXTRA_USER, UserHandle.of(userHandle));
        if (isSeparateProfileChallengeEnabled(userHandle)) {
            sendAdminCommandLocked(action, reqPolicy, userHandle, extras);
        } else {
            sendAdminCommandToSelfAndProfilesLocked(action, reqPolicy, userHandle, extras);
        }
    }

    void removeActiveAdminLocked(final ComponentName adminReceiver, final int userHandle) {
        final ActiveAdmin admin = getActiveAdminUncheckedLocked(adminReceiver, userHandle);
        DevicePolicyData policy = getUserData(userHandle);
        if (admin != null && !policy.mRemovingAdmins.contains(adminReceiver)) {
            policy.mRemovingAdmins.add(adminReceiver);
            sendAdminCommandLocked(admin,
                    DeviceAdminReceiver.ACTION_DEVICE_ADMIN_DISABLED,
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            removeAdminArtifacts(adminReceiver, userHandle);
                            removePackageIfRequired(adminReceiver.getPackageName(), userHandle);
                        }
                    });
        }
    }

    private DeviceAdminInfo findAdmin(final ComponentName adminName, final int userHandle,
            boolean throwForMissingPermission) {
        final ActivityInfo ai = mInjector.binderWithCleanCallingIdentity(() -> {
            try {
                return mIPackageManager.getReceiverInfo(adminName,
                        GET_META_DATA
                        | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_DIRECT_BOOT_AWARE
                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE, userHandle);
            } catch (RemoteException e) {
                // shouldn't happen.
                Slogf.wtf(LOG_TAG, "Error getting receiver info", e);
                return null;
            }
        });
        if (ai == null) {
            throw new IllegalArgumentException("Unknown admin: " + adminName);
        }

        if (!permission.BIND_DEVICE_ADMIN.equals(ai.permission)) {
            final String message = "DeviceAdminReceiver " + adminName + " must be protected with "
                    + permission.BIND_DEVICE_ADMIN;
            Slogf.w(LOG_TAG, message);
            if (throwForMissingPermission &&
                    ai.applicationInfo.targetSdkVersion > Build.VERSION_CODES.M) {
                throw new IllegalArgumentException(message);
            }
        }

        try {
            return new DeviceAdminInfo(mContext, ai);
        } catch (XmlPullParserException | IOException e) {
            Slogf.w(LOG_TAG, "Bad device admin requested for user=" + userHandle + ": " + adminName,
                    e);
            return null;
        }
    }

    private File getPolicyFileDirectory(@UserIdInt int userId) {
        return userId == UserHandle.USER_SYSTEM
                ? mPathProvider.getDataSystemDirectory()
                : mPathProvider.getUserSystemDirectory(userId);
    }

    private JournaledFile makeJournaledFile(@UserIdInt int userId, String fileName) {
        final String base = new File(getPolicyFileDirectory(userId), fileName)
                .getAbsolutePath();
        if (VERBOSE_LOG) Slogf.v(LOG_TAG, "Opening %s", base);
        return new JournaledFile(new File(base), new File(base + ".tmp"));
    }

    private JournaledFile makeJournaledFile(@UserIdInt int userId) {
        return makeJournaledFile(userId, DEVICE_POLICIES_XML);
    }

    /**
     * Persist modified values to disk by calling {@link #saveSettingsLocked} for each
     * affected user ID.
     */
    @GuardedBy("getLockObject()")
    private void saveSettingsForUsersLocked(Set<Integer> affectedUserIds) {
        for (int userId : affectedUserIds) {
            saveSettingsLocked(userId);
        }
    }

    private void saveSettingsLocked(int userHandle) {
        if (DevicePolicyData.store(getUserData(userHandle), makeJournaledFile(userHandle))) {
            sendChangedNotification(userHandle);
        }
        invalidateBinderCaches();
    }

    private void sendChangedNotification(int userHandle) {
        Intent intent = new Intent(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mInjector.binderWithCleanCallingIdentity(() ->
                mContext.sendBroadcastAsUser(intent, new UserHandle(userHandle)));
    }

    private void loadSettingsLocked(DevicePolicyData policy, int userHandle) {
        DevicePolicyData.load(policy,
                makeJournaledFile(userHandle),
                component -> findAdmin(
                        component, userHandle, /* throwForMissingPermission= */ false),
                getOwnerComponent(userHandle));

        policy.validatePasswordOwner();
        updateMaximumTimeToLockLocked(userHandle);
        updateLockTaskPackagesLocked(mContext, policy.mLockTaskPackages, userHandle);
        updateLockTaskFeaturesLocked(policy.mLockTaskFeatures, userHandle);
        if (policy.mStatusBarDisabled) {
            setStatusBarDisabledInternal(policy.mStatusBarDisabled, userHandle);
        }
    }

    static void updateLockTaskPackagesLocked(Context context, List<String> packages, int userId) {
        Binder.withCleanCallingIdentity(() -> {

            String[] packagesArray = null;
            if (!packages.isEmpty()) {
                // When adding packages, we need to include the exempt apps so they can still be
                // launched (ideally we should use a different AM API as these apps don't need to
                // use lock-task mode).
                // They're not added when the packages is empty though, as in that case we're
                // disabling lock-task mode.
                List<String> exemptApps = listPolicyExemptAppsUnchecked(context);
                if (!exemptApps.isEmpty()) {
                    // TODO(b/175377361): add unit test to verify it (cannot be CTS because the
                    //  policy-exempt apps are provided by OEM and the test would have no control
                    //  over it) once tests are migrated to the new infra-structure
                    HashSet<String> updatedPackages = new HashSet<>(packages);
                    updatedPackages.addAll(exemptApps);
                    if (VERBOSE_LOG) {
                        Slogf.v(LOG_TAG, "added %d policy-exempt apps to %d lock task "
                                + "packages. Final list: %s",
                                exemptApps.size(), packages.size(), updatedPackages);
                    }
                    packagesArray = updatedPackages.toArray(new String[updatedPackages.size()]);
                }
            }

            if (packagesArray == null) {
                packagesArray = packages.toArray(new String[packages.size()]);
            }
            try {
                ActivityManager.getService().updateLockTaskPackages(userId, packagesArray);
            } catch (RemoteException e) {
                // Shouldn't happen.
                Slog.wtf(LOG_TAG, "Remote Exception: ", e);
            }
        });
    }

    static void updateLockTaskFeaturesLocked(int flags, int userId) {
        Binder.withCleanCallingIdentity(() -> {
            try {
                ActivityTaskManager.getService().updateLockTaskFeatures(userId, flags);
            } catch (RemoteException e) {
                // Shouldn't happen.
                Slog.wtf(LOG_TAG, "Remote Exception: ", e);
            }
        });
    }

    static void validateQualityConstant(int quality) {
        switch (quality) {
            case PASSWORD_QUALITY_UNSPECIFIED:
            case PASSWORD_QUALITY_BIOMETRIC_WEAK:
            case PASSWORD_QUALITY_SOMETHING:
            case PASSWORD_QUALITY_NUMERIC:
            case PASSWORD_QUALITY_NUMERIC_COMPLEX:
            case PASSWORD_QUALITY_ALPHABETIC:
            case PASSWORD_QUALITY_ALPHANUMERIC:
            case PASSWORD_QUALITY_COMPLEX:
            case PASSWORD_QUALITY_MANAGED:
                return;
        }
        throw new IllegalArgumentException("Invalid quality constant: 0x"
                + Integer.toHexString(quality));
    }

    @VisibleForTesting
    void systemReady(int phase) {
        if (!mHasFeature) {
            return;
        }
        switch (phase) {
            case SystemService.PHASE_LOCK_SETTINGS_READY:
                onLockSettingsReady();
                loadAdminDataAsync();
                mOwners.systemReady();
                if (isWorkProfileTelephonyFlagEnabled()) {
                    applyManagedSubscriptionsPolicyIfRequired();
                }
                break;
            case SystemService.PHASE_ACTIVITY_MANAGER_READY:
                synchronized (getLockObject()) {
                    migrateToProfileOnOrganizationOwnedDeviceIfCompLocked();
                    applyProfileRestrictionsIfDeviceOwnerLocked();
                }
                maybeStartSecurityLogMonitorOnActivityManagerReady();
                break;
            case SystemService.PHASE_BOOT_COMPLETED:
                // Ideally it should be done earlier, but currently it relies on RecoverySystem,
                // which would hang on earlier phases
                factoryResetIfDelayedEarlier();

                ensureDeviceOwnerUserStarted(); // TODO Consider better place to do this.
                break;
        }
    }

    private void applyManagedSubscriptionsPolicyIfRequired() {
        int copeProfileUserId = getOrganizationOwnedProfileUserId();
        // This policy is relevant only for COPE devices.
        if (copeProfileUserId != UserHandle.USER_NULL) {
            unregisterOnSubscriptionsChangedListener();
            int policyType = getManagedSubscriptionsPolicy().getPolicyType();
            if (policyType == ManagedSubscriptionsPolicy.TYPE_ALL_PERSONAL_SUBSCRIPTIONS) {
                final int parentUserId = getProfileParentId(copeProfileUserId);
                // By default, assign all current and future subs to system user on COPE devices.
                registerListenerToAssignSubscriptionsToUser(parentUserId);
            } else if (policyType == ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS) {
                // Add listener to assign all current and future subs to managed profile.
                registerListenerToAssignSubscriptionsToUser(copeProfileUserId);
            }
        }
    }

    private void updatePersonalAppsSuspensionOnUserStart(int userHandle) {
        final int profileUserHandle = getManagedUserId(userHandle);
        if (profileUserHandle >= 0) {
            // Given that the parent user has just started, profile should be locked.
            updatePersonalAppsSuspension(profileUserHandle, false /* unlocked */);
        } else {
            suspendPersonalAppsInternal(userHandle, false);
        }
    }

    private void onLockSettingsReady() {
        synchronized (getLockObject()) {
            fixupAutoTimeRestrictionDuringOrganizationOwnedDeviceMigration();
        }
        getUserData(UserHandle.USER_SYSTEM);
        cleanUpOldUsers();
        maybeSetDefaultProfileOwnerUserRestrictions();
        handleStartUser(UserHandle.USER_SYSTEM);
        maybeLogStart();

        // Register an observer for watching for user setup complete and settings changes.
        mSetupContentObserver.register();
        // Initialize the user setup state, to handle the upgrade case.
        updateUserSetupCompleteAndPaired();

        List<String> packageList;
        synchronized (getLockObject()) {
            packageList = getKeepUninstalledPackagesLocked();
        }
        if (packageList != null) {
            mInjector.getPackageManagerInternal().setKeepUninstalledPackages(packageList);
        }

        synchronized (getLockObject()) {
            ActiveAdmin deviceOwner = getDeviceOwnerAdminLocked();
            if (deviceOwner != null) {
                // Push the force-ephemeral-users policy to the user manager.
                mUserManagerInternal.setForceEphemeralUsers(deviceOwner.forceEphemeralUsers);

                // Update user switcher message to activity manager.
                ActivityManagerInternal activityManagerInternal =
                        mInjector.getActivityManagerInternal();
                activityManagerInternal.setSwitchingFromSystemUserMessage(
                        deviceOwner.startUserSessionMessage);
                activityManagerInternal.setSwitchingToSystemUserMessage(
                        deviceOwner.endUserSessionMessage);
            }

            revertTransferOwnershipIfNecessaryLocked();
        }
        updateUsbDataSignal();
    }

    // TODO(b/230841522) Make it static.
    private class DpmsUpgradeDataProvider implements PolicyUpgraderDataProvider {
        @Override
        public JournaledFile makeDevicePoliciesJournaledFile(int userId) {
            return DevicePolicyManagerService.this.makeJournaledFile(userId, DEVICE_POLICIES_XML);
        }

        @Override
        public JournaledFile makePoliciesVersionJournaledFile(int userId) {
            return DevicePolicyManagerService.this.makeJournaledFile(userId, POLICIES_VERSION_XML);
        }

        @Override
        public Function<ComponentName, DeviceAdminInfo> getAdminInfoSupplier(int userId) {
            return component ->
                    findAdmin(component, userId, /* throwForMissingPermission= */ false);
        }

        @Override
        public int[] getUsersForUpgrade() {
            List<UserInfo> allUsers = mUserManager.getUsers();
            return allUsers.stream().mapToInt(u -> u.id).toArray();
        }

        @Override
        public List<String> getPlatformSuspendedPackages(int userId) {
            PackageManagerInternal pmi = mInjector.getPackageManagerInternal();
            return mInjector.getPackageManager(userId)
                    .getInstalledPackages(PackageManager.PackageInfoFlags.of(
                            MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE))
                    .stream()
                    .map(packageInfo -> packageInfo.packageName)
                    .filter(pkg ->
                            PLATFORM_PACKAGE_NAME.equals(pmi.getSuspendingPackage(pkg, userId))
                    )
                    .collect(Collectors.toList());
        }
    }

    private void performPolicyVersionUpgrade() {
        PolicyVersionUpgrader upgrader = new PolicyVersionUpgrader(
                new DpmsUpgradeDataProvider(), mPathProvider);
        upgrader.upgradePolicy(DPMS_VERSION);
    }

    private void revertTransferOwnershipIfNecessaryLocked() {
        if (!mTransferOwnershipMetadataManager.metadataFileExists()) {
            return;
        }
        Slogf.e(LOG_TAG, "Owner transfer metadata file exists! Reverting transfer.");
        final TransferOwnershipMetadataManager.Metadata metadata =
                mTransferOwnershipMetadataManager.loadMetadataFile();
        // Revert transfer
        if (metadata.adminType.equals(ADMIN_TYPE_PROFILE_OWNER)) {
            transferProfileOwnershipLocked(metadata.targetComponent, metadata.sourceComponent,
                    metadata.userId);
            deleteTransferOwnershipMetadataFileLocked();
            deleteTransferOwnershipBundleLocked(metadata.userId);
        } else if (metadata.adminType.equals(ADMIN_TYPE_DEVICE_OWNER)) {
            transferDeviceOwnershipLocked(metadata.targetComponent, metadata.sourceComponent,
                    metadata.userId);
            deleteTransferOwnershipMetadataFileLocked();
            deleteTransferOwnershipBundleLocked(metadata.userId);
        }
        updateSystemUpdateFreezePeriodsRecord(/* saveIfChanged */ true);
        pushUserControlDisabledPackagesLocked(metadata.userId);
    }

    private void maybeLogStart() {
        if (!SecurityLog.isLoggingEnabled()) {
            return;
        }
        final String verifiedBootState =
                mInjector.systemPropertiesGet("ro.boot.verifiedbootstate");
        final String verityMode = mInjector.systemPropertiesGet("ro.boot.veritymode");
        SecurityLog.writeEvent(SecurityLog.TAG_OS_STARTUP, verifiedBootState, verityMode);
    }

    private void ensureDeviceOwnerUserStarted() {
        final int userId;
        synchronized (getLockObject()) {
            if (!mOwners.hasDeviceOwner()) {
                return;
            }
            userId = mOwners.getDeviceOwnerUserId();
        }
        if (VERBOSE_LOG) {
            Slogf.v(LOG_TAG, "Starting non-system DO user: " + userId);
        }
        if (userId != UserHandle.USER_SYSTEM) {
            try {
                mInjector.getIActivityManager().startUserInBackground(userId);

                // STOPSHIP Prevent the DO user from being killed.

            } catch (RemoteException e) {
                Slogf.w(LOG_TAG, "Exception starting user", e);
            }
        }
    }

    void handleStartUser(int userId) {
        synchronized (getLockObject()) {
            pushScreenCapturePolicy(userId);
            pushUserControlDisabledPackagesLocked(userId);
        }
        pushUserRestrictions(userId);
        // When system user is started (device boot), load cache for all users.
        // This is to mitigate the potential race between loading the cache and keyguard
        // reading the value during user switch, due to onStartUser() being asynchronous.
        updatePasswordQualityCacheForUserGroup(
                userId == UserHandle.USER_SYSTEM ? UserHandle.USER_ALL : userId);
        updatePermissionPolicyCache(userId);
        updateAdminCanGrantSensorsPermissionCache(userId);

        final List<PreferentialNetworkServiceConfig> preferentialNetworkServiceConfigs;
        synchronized (getLockObject()) {
            ActiveAdmin owner = getDeviceOrProfileOwnerAdminLocked(userId);
            preferentialNetworkServiceConfigs = owner != null
                    ? owner.mPreferentialNetworkServiceConfigs
                    : List.of(PreferentialNetworkServiceConfig.DEFAULT);
        }
        updateNetworkPreferenceForUser(userId, preferentialNetworkServiceConfigs);

        startOwnerService(userId, "start-user");
        if (isCoexistenceFlagEnabled()) {
            mDevicePolicyEngine.handleStartUser(userId);
        }
    }

    void pushUserControlDisabledPackagesLocked(int userId) {
        final int targetUserId;
        final ActiveAdmin owner;
        if (getDeviceOwnerUserIdUncheckedLocked() == userId) {
            owner = getDeviceOwnerAdminLocked();
            targetUserId = UserHandle.USER_ALL;
        } else {
            owner = getProfileOwnerAdminLocked(userId);
            targetUserId = userId;
        }

        List<String> protectedPackages = (owner == null || owner.protectedPackages == null)
                ? null : owner.protectedPackages;
        mInjector.binderWithCleanCallingIdentity(() ->
                mInjector.getPackageManagerInternal().setOwnerProtectedPackages(
                        targetUserId, protectedPackages));
    }

    void handleUnlockUser(int userId) {
        startOwnerService(userId, "unlock-user");
        if (isCoexistenceFlagEnabled()) {
            mDevicePolicyEngine.handleUnlockUser(userId);
        }
    }

    void handleOnUserUnlocked(int userId) {
        showNewUserDisclaimerIfNecessary(userId);
    }

    void handleStopUser(int userId) {
        updateNetworkPreferenceForUser(userId, List.of(PreferentialNetworkServiceConfig.DEFAULT));
        mDeviceAdminServiceController.stopServicesForUser(userId, /* actionForLog= */ "stop-user");
        if (isCoexistenceFlagEnabled()) {
            mDevicePolicyEngine.handleStopUser(userId);
        }
    }

    private void startOwnerService(int userId, String actionForLog) {
        final ComponentName owner = getOwnerComponent(userId);
        if (owner != null) {
            mDeviceAdminServiceController.startServiceForAdmin(
                    owner.getPackageName(), userId, actionForLog);
            invalidateBinderCaches();
        }
    }

    private void cleanUpOldUsers() {
        // This is needed in case the broadcast {@link Intent.ACTION_USER_REMOVED} was not handled
        // before reboot
        Set<Integer> usersWithProfileOwners;
        Set<Integer> usersWithData;
        synchronized (getLockObject()) {
            usersWithProfileOwners = mOwners.getProfileOwnerKeys();
            usersWithData = new ArraySet<>();
            for (int i = 0; i < mUserData.size(); i++) {
                usersWithData.add(mUserData.keyAt(i));
            }
        }
        List<UserInfo> allUsers = mUserManager.getUsers();

        Set<Integer> deletedUsers = new ArraySet<>();
        deletedUsers.addAll(usersWithProfileOwners);
        deletedUsers.addAll(usersWithData);
        for (UserInfo userInfo : allUsers) {
            deletedUsers.remove(userInfo.id);
        }
        for (Integer userId : deletedUsers) {
            removeUserData(userId);
        }
    }

    private void handlePasswordExpirationNotification(int userHandle) {
        final Bundle adminExtras = new Bundle();
        adminExtras.putParcelable(Intent.EXTRA_USER, UserHandle.of(userHandle));

        synchronized (getLockObject()) {
            final long now = System.currentTimeMillis();

            List<ActiveAdmin> admins = getActiveAdminsForLockscreenPoliciesLocked(userHandle);
            final int N = admins.size();
            for (int i = 0; i < N; i++) {
                ActiveAdmin admin = admins.get(i);
                if (admin.info.usesPolicy(DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD)
                        && admin.passwordExpirationTimeout > 0L
                        && now >= admin.passwordExpirationDate - EXPIRATION_GRACE_PERIOD_MS
                        && admin.passwordExpirationDate > 0L) {
                    sendAdminCommandLocked(admin,
                            DeviceAdminReceiver.ACTION_PASSWORD_EXPIRING, adminExtras, null);
                }
            }
            setExpirationAlarmCheckLocked(mContext, userHandle, /* parent */ false);
        }
    }

    /**
     * Clean up internal state when the set of installed trusted CA certificates changes.
     *
     * @param userHandle user to check for. This must be a real user and not, for example,
     *        {@link UserHandle#ALL}.
     * @param installedCertificates the full set of certificate authorities currently installed for
     *        {@param userHandle}. After calling this function, {@code mAcceptedCaCertificates} will
     *        correspond to some subset of this.
     */
    protected void onInstalledCertificatesChanged(final UserHandle userHandle,
            final @NonNull Collection<String> installedCertificates) {
        if (!mHasFeature) {
            return;
        }
        Preconditions.checkCallAuthorization(canManageUsers(getCallerIdentity()));

        synchronized (getLockObject()) {
            final DevicePolicyData policy = getUserData(userHandle.getIdentifier());

            boolean changed = false;
            changed |= policy.mAcceptedCaCertificates.retainAll(installedCertificates);
            changed |= policy.mOwnerInstalledCaCerts.retainAll(installedCertificates);
            if (changed) {
                saveSettingsLocked(userHandle.getIdentifier());
            }
        }
    }

    /**
     * Internal method used by {@link CertificateMonitor}.
     */
    protected Set<String> getAcceptedCaCertificates(final UserHandle userHandle) {
        if (!mHasFeature) {
            return Collections.<String> emptySet();
        }
        synchronized (getLockObject()) {
            final DevicePolicyData policy = getUserData(userHandle.getIdentifier());
            return policy.mAcceptedCaCertificates;
        }
    }

    /**
     * @param adminReceiver The admin to add
     * @param refreshing true = update an active admin, no error
     */
    @Override
    public void setActiveAdmin(ComponentName adminReceiver, boolean refreshing, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_DEVICE_ADMINS));
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));

        DevicePolicyData policy = getUserData(userHandle);
        DeviceAdminInfo info = findAdmin(adminReceiver, userHandle,
                /* throwForMissingPermission= */ true);
        synchronized (getLockObject()) {
            checkActiveAdminPrecondition(adminReceiver, info, policy);
            mInjector.binderWithCleanCallingIdentity(() -> {
                final ActiveAdmin existingAdmin
                        = getActiveAdminUncheckedLocked(adminReceiver, userHandle);
                if (!refreshing && existingAdmin != null) {
                    throw new IllegalArgumentException("Admin is already added");
                }
                ActiveAdmin newAdmin = new ActiveAdmin(info, /* parent */ false);
                newAdmin.testOnlyAdmin =
                        (existingAdmin != null) ? existingAdmin.testOnlyAdmin
                                : isPackageTestOnly(adminReceiver.getPackageName(), userHandle);
                policy.mAdminMap.put(adminReceiver, newAdmin);
                int replaceIndex = -1;
                final int N = policy.mAdminList.size();
                for (int i=0; i < N; i++) {
                    ActiveAdmin oldAdmin = policy.mAdminList.get(i);
                    if (oldAdmin.info.getComponent().equals(adminReceiver)) {
                        replaceIndex = i;
                        break;
                    }
                }
                if (replaceIndex == -1) {
                    policy.mAdminList.add(newAdmin);
                    enableIfNecessary(info.getPackageName(), userHandle);
                    mUsageStatsManagerInternal.onActiveAdminAdded(
                            adminReceiver.getPackageName(), userHandle);
                } else {
                    policy.mAdminList.set(replaceIndex, newAdmin);
                }
                saveSettingsLocked(userHandle);
                sendAdminCommandLocked(newAdmin, DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED,
                        /* adminExtras= */ null, /* result= */ null);
            });
        }
    }

    private void loadAdminDataAsync() {
        mInjector.postOnSystemServerInitThreadPool(() -> {
            pushActiveAdminPackages();
            mUsageStatsManagerInternal.onAdminDataAvailable();
            pushAllMeteredRestrictedPackages();
            mInjector.getNetworkPolicyManagerInternal().onAdminDataAvailable();
        });
    }

    private void pushActiveAdminPackages() {
        synchronized (getLockObject()) {
            final List<UserInfo> users = mUserManager.getUsers();
            for (int i = users.size() - 1; i >= 0; --i) {
                final int userId = users.get(i).id;
                mUsageStatsManagerInternal.setActiveAdminApps(
                        getActiveAdminPackagesLocked(userId), userId);
            }
        }
    }

    private void pushAllMeteredRestrictedPackages() {
        synchronized (getLockObject()) {
            final List<UserInfo> users = mUserManager.getUsers();
            for (int i = users.size() - 1; i >= 0; --i) {
                final int userId = users.get(i).id;
                mInjector.getNetworkPolicyManagerInternal().setMeteredRestrictedPackagesAsync(
                        getMeteredDisabledPackages(userId), userId);
            }
        }
    }

    private void pushActiveAdminPackagesLocked(int userId) {
        mUsageStatsManagerInternal.setActiveAdminApps(
                getActiveAdminPackagesLocked(userId), userId);
    }

    private Set<String> getActiveAdminPackagesLocked(int userId) {
        final DevicePolicyData policy = getUserData(userId);
        Set<String> adminPkgs = null;
        for (int i = policy.mAdminList.size() - 1; i >= 0; --i) {
            final String pkgName = policy.mAdminList.get(i).info.getPackageName();
            if (adminPkgs == null) {
                adminPkgs = new ArraySet<>();
            }
            adminPkgs.add(pkgName);
        }
        return adminPkgs;
    }

    private void transferActiveAdminUncheckedLocked(ComponentName incomingReceiver,
            ComponentName outgoingReceiver, int userHandle) {
        final DevicePolicyData policy = getUserData(userHandle);
        if (!policy.mAdminMap.containsKey(outgoingReceiver)
                && policy.mAdminMap.containsKey(incomingReceiver)) {
            // Nothing to transfer - the incoming receiver is already the active admin.
            return;
        }
        final DeviceAdminInfo incomingDeviceInfo = findAdmin(incomingReceiver, userHandle,
            /* throwForMissingPermission= */ true);
        final ActiveAdmin adminToTransfer = policy.mAdminMap.get(outgoingReceiver);
        final int oldAdminUid = adminToTransfer.getUid();

        adminToTransfer.transfer(incomingDeviceInfo);
        policy.mAdminMap.remove(outgoingReceiver);
        policy.mAdminMap.put(incomingReceiver, adminToTransfer);
        if (policy.mPasswordOwner == oldAdminUid) {
            policy.mPasswordOwner = adminToTransfer.getUid();
        }

        saveSettingsLocked(userHandle);
        sendAdminCommandLocked(adminToTransfer, DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED,
                null, null);
    }

    private void checkActiveAdminPrecondition(ComponentName adminReceiver, DeviceAdminInfo info,
            DevicePolicyData policy) {
        if (info == null) {
            throw new IllegalArgumentException("Bad admin: " + adminReceiver);
        }
        if (!info.getActivityInfo().applicationInfo.isInternal()) {
            throw new IllegalArgumentException("Only apps in internal storage can be active admin: "
                    + adminReceiver);
        }
        if (info.getActivityInfo().applicationInfo.isInstantApp()) {
            throw new IllegalArgumentException("Instant apps cannot be device admins: "
                    + adminReceiver);
        }
        if (policy.mRemovingAdmins.contains(adminReceiver)) {
            throw new IllegalArgumentException(
                    "Trying to set an admin which is being removed");
        }
    }

    private void checkAllUsersAreAffiliatedWithDevice() {
        Preconditions.checkCallAuthorization(areAllUsersAffiliatedWithDeviceLocked(),
                "operation not allowed when device has unaffiliated users");
    }

    @Override
    public boolean isAdminActive(ComponentName adminReceiver, int userHandle) {
        if (!mHasFeature) {
            return false;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));

        synchronized (getLockObject()) {
            return getActiveAdminUncheckedLocked(adminReceiver, userHandle) != null;
        }
    }

    @Override
    public boolean isRemovingAdmin(ComponentName adminReceiver, int userHandle) {
        if (!mHasFeature) {
            return false;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));

        synchronized (getLockObject()) {
            DevicePolicyData policyData = getUserData(userHandle);
            return policyData.mRemovingAdmins.contains(adminReceiver);
        }
    }

    @Override
    public boolean hasGrantedPolicy(ComponentName adminReceiver, int policyId, int userHandle) {
        if (!mHasFeature) {
            return false;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        Preconditions.checkCallAuthorization(
                isCallingFromPackage(adminReceiver.getPackageName(), caller.getUid())
                        || isSystemUid(caller));

        synchronized (getLockObject()) {
            ActiveAdmin administrator = getActiveAdminUncheckedLocked(adminReceiver, userHandle);
            if (administrator == null) {
                throw new SecurityException("No active admin " + adminReceiver);
            }
            return administrator.info.usesPolicy(policyId);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ComponentName> getActiveAdmins(int userHandle) {
        if (!mHasFeature) {
            return Collections.EMPTY_LIST;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));

        synchronized (getLockObject()) {
            DevicePolicyData policy = getUserData(userHandle);
            final int N = policy.mAdminList.size();
            if (N <= 0) {
                return null;
            }
            ArrayList<ComponentName> res = new ArrayList<ComponentName>(N);
            for (int i=0; i<N; i++) {
                res.add(policy.mAdminList.get(i).info.getComponent());
            }
            return res;
        }
    }

    @Override
    public boolean packageHasActiveAdmins(String packageName, int userHandle) {
        if (!mHasFeature) {
            return false;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));

        synchronized (getLockObject()) {
            DevicePolicyData policy = getUserData(userHandle);
            final int N = policy.mAdminList.size();
            for (int i=0; i<N; i++) {
                if (policy.mAdminList.get(i).info.getPackageName().equals(packageName)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public void forceRemoveActiveAdmin(ComponentName adminReceiver, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(adminReceiver, "ComponentName is null");
        Preconditions.checkCallAuthorization(isAdb(getCallerIdentity())
                        || hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS),
                "Caller must be shell or hold MANAGE_PROFILE_AND_DEVICE_OWNERS to call "
                        + "forceRemoveActiveAdmin");
        mInjector.binderWithCleanCallingIdentity(() -> {
            boolean isOrgOwnedProfile = false;
            synchronized (getLockObject()) {
                if (!isAdminTestOnlyLocked(adminReceiver, userHandle)) {
                    throw new SecurityException("Attempt to remove non-test admin "
                            + adminReceiver + " " + userHandle);
                }

                // If admin is a device or profile owner tidy that up first.
                if (isDeviceOwner(adminReceiver, userHandle)) {
                    clearDeviceOwnerLocked(getDeviceOwnerAdminLocked(), userHandle);
                }
                if (isProfileOwner(adminReceiver, userHandle)) {
                    isOrgOwnedProfile = isProfileOwnerOfOrganizationOwnedDevice(userHandle);
                    final ActiveAdmin admin = getActiveAdminUncheckedLocked(adminReceiver,
                            userHandle, /* parent */ false);
                    clearProfileOwnerLocked(admin, userHandle);
                }
            }
            // Remove the admin skipping sending the broadcast.
            removeAdminArtifacts(adminReceiver, userHandle);

            // In case of PO on org owned device, clean device-wide policies and restrictions.
            if (isOrgOwnedProfile) {
                final UserHandle parentUser = UserHandle.of(getProfileParentId(userHandle));
                clearOrgOwnedProfileOwnerUserRestrictions(parentUser);
                clearOrgOwnedProfileOwnerDeviceWidePolicies(parentUser.getIdentifier());
            }

            Slogf.i(LOG_TAG, "Admin " + adminReceiver + " removed from user " + userHandle);
        });
    }

    private void clearOrgOwnedProfileOwnerUserRestrictions(UserHandle parentUserHandle) {
        mUserManager.setUserRestriction(
                UserManager.DISALLOW_REMOVE_MANAGED_PROFILE, false, parentUserHandle);
        mUserManager.setUserRestriction(
                UserManager.DISALLOW_ADD_USER, false, parentUserHandle);
    }

    private void clearDeviceOwnerUserRestriction(UserHandle userHandle) {
        if (isHeadlessFlagEnabled()) {
            for (int userId : mUserManagerInternal.getUserIds()) {
                UserHandle user = UserHandle.of(userId);
                // ManagedProvisioning/DPC sets DISALLOW_ADD_USER. Clear to recover to the
                // original state
                if (mUserManager.hasUserRestriction(UserManager.DISALLOW_ADD_USER, user)) {
                    mUserManager.setUserRestriction(UserManager.DISALLOW_ADD_USER,
                            false, user);
                }
                // When a device owner is set, the system automatically restricts adding a
                // managed profile.
                // Remove this restriction when the device owner is cleared.
                if (mUserManager.hasUserRestriction(UserManager.DISALLOW_ADD_MANAGED_PROFILE,
                        user)) {
                    mUserManager.setUserRestriction(UserManager.DISALLOW_ADD_MANAGED_PROFILE,
                            false,
                            user);
                }
                // When a device owner is set, the system automatically restricts adding a
                // clone profile.
                // Remove this restriction when the device owner is cleared.
                if (mUserManager.hasUserRestriction(UserManager.DISALLOW_ADD_CLONE_PROFILE, user)) {
                    mUserManager.setUserRestriction(UserManager.DISALLOW_ADD_CLONE_PROFILE,
                            false, user);
                }
            }
        } else {
            // ManagedProvisioning/DPC sets DISALLOW_ADD_USER. Clear to recover to the original state
            if (mUserManager.hasUserRestriction(UserManager.DISALLOW_ADD_USER, userHandle)) {
                mUserManager.setUserRestriction(UserManager.DISALLOW_ADD_USER, false,
                        userHandle);
            }
            // When a device owner is set, the system automatically restricts adding a
            // managed profile.
            // Remove this restriction when the device owner is cleared.
            if (mUserManager.hasUserRestriction(UserManager.DISALLOW_ADD_MANAGED_PROFILE,
                    userHandle)) {
                mUserManager.setUserRestriction(UserManager.DISALLOW_ADD_MANAGED_PROFILE,
                        false,
                        userHandle);
            }
            // When a device owner is set, the system automatically restricts adding a clone
            // profile.
            // Remove this restriction when the device owner is cleared.
            if (mUserManager.hasUserRestriction(UserManager.DISALLOW_ADD_CLONE_PROFILE,
                    userHandle)) {
                mUserManager.setUserRestriction(UserManager.DISALLOW_ADD_CLONE_PROFILE,
                        false,
                        userHandle);
            }
        }
    }

    /**
     * Return if a given package has testOnly="true", in which case we'll relax certain rules
     * for CTS.
     *
     * DO NOT use this method except in {@link #setActiveAdmin}.  Use {@link #isAdminTestOnlyLocked}
     * to check wehter an active admin is test-only or not.
     *
     * The system allows this flag to be changed when an app is updated, which is not good
     * for us.  So we persist the flag in {@link ActiveAdmin} when an admin is first installed,
     * and used the persisted version in actual checks. (See b/31382361 and b/28928996)
     */
    private boolean isPackageTestOnly(String packageName, int userHandle) {
        final ApplicationInfo ai;
        try {
            ai = mInjector.getIPackageManager().getApplicationInfo(packageName,
                    (PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE), userHandle);
        } catch (RemoteException e) {
            throw new IllegalStateException(e);
        }
        if (ai == null) {
            throw new IllegalStateException("Couldn't find package: "
                    + packageName + " on user " + userHandle);
        }
        return (ai.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0;
    }

    /**
     * See {@link #isPackageTestOnly}.
     */
    private boolean isAdminTestOnlyLocked(ComponentName who, int userHandle) {
        final ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
        return (admin != null) && admin.testOnlyAdmin;
    }

    @Override
    public void removeActiveAdmin(ComponentName adminReceiver, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = hasCallingOrSelfPermission(permission.MANAGE_DEVICE_ADMINS)
                ? getCallerIdentity() : getCallerIdentity(adminReceiver);
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_REMOVE_ACTIVE_ADMIN);
        enforceUserUnlocked(userHandle);

        synchronized (getLockObject()) {
            ActiveAdmin admin = getActiveAdminUncheckedLocked(adminReceiver, userHandle);
            if (admin == null) {
                return;
            }
            // Active device/profile owners must remain active admins.
            if (isDeviceOwner(adminReceiver, userHandle)
                    || isProfileOwner(adminReceiver, userHandle)) {
                Slogf.e(LOG_TAG, "Device/profile owner cannot be removed: component="
                        + adminReceiver);
                return;
            }

            mInjector.binderWithCleanCallingIdentity(() ->
                    removeActiveAdminLocked(adminReceiver, userHandle));
        }
    }

    private boolean canSetPasswordQualityOnParent(String packageName, final CallerIdentity caller) {
        return !mInjector.isChangeEnabled(
                PREVENT_SETTING_PASSWORD_QUALITY_ON_PARENT, packageName, caller.getUserId())
            || isProfileOwnerOfOrganizationOwnedDevice(caller);
    }

    private boolean isPasswordLimitingAdminTargetingP(CallerIdentity caller) {
        if (!caller.hasAdminComponent()) {
            return false;
        }

        synchronized (getLockObject()) {
            return getActiveAdminWithPolicyForUidLocked(
                    caller.getComponentName(), DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD,
                    caller.getUid()) != null;
        }
    }

    private boolean notSupportedOnAutomotive(String method) {
        if (mIsAutomotive) {
            Slogf.i(LOG_TAG, "%s is not supported on automotive builds", method);
            return true;
        }
        return false;
    }

    @Override
    public void setPasswordQuality(ComponentName who, int quality, boolean parent) {
        if (!mHasFeature || notSupportedOnAutomotive("setPasswordQuality")) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        validateQualityConstant(quality);

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller)
                        || isSystemUid(caller) || isPasswordLimitingAdminTargetingP(caller));

        if (parent) {
            Preconditions.checkCallAuthorization(
                    canSetPasswordQualityOnParent(who.getPackageName(), caller),
                    "Profile Owner may not apply password quality requirements device-wide");
        }

        final int userId = caller.getUserId();
        synchronized (getLockObject()) {
            ActiveAdmin ap = getActiveAdminForCallerLocked(
                    who, DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD, parent);

            // If setPasswordQuality is called on the parent, ensure that
            // the primary admin does not have password complexity state (this is an
            // unsupported state).
            if (parent) {
                final ActiveAdmin primaryAdmin = getActiveAdminForCallerLocked(
                        who, DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD, false);
                final boolean hasComplexitySet =
                        primaryAdmin.mPasswordComplexity != PASSWORD_COMPLEXITY_NONE;
                Preconditions.checkState(!hasComplexitySet,
                        "Cannot set password quality when complexity is set on the primary admin."
                        + " Set the primary admin's complexity to NONE first.");
            }
            mInjector.binderWithCleanCallingIdentity(() -> {
                final PasswordPolicy passwordPolicy = ap.mPasswordPolicy;
                if (passwordPolicy.quality != quality) {
                    passwordPolicy.quality = quality;
                    ap.mPasswordComplexity = PASSWORD_COMPLEXITY_NONE;
                    resetInactivePasswordRequirementsIfRPlus(userId, ap);
                    updatePasswordValidityCheckpointLocked(userId, parent);
                    updatePasswordQualityCacheForUserGroup(userId);
                    saveSettingsLocked(userId);
                }
                logPasswordQualitySetIfSecurityLogEnabled(who, userId, parent, passwordPolicy);
            });
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_PASSWORD_QUALITY)
                .setAdmin(who)
                .setInt(quality)
                .setStrings(parent ? CALLED_FROM_PARENT : NOT_CALLED_FROM_PARENT)
                .write();
    }

    private boolean passwordQualityInvocationOrderCheckEnabled(String packageName, int userId) {
        return mInjector.isChangeEnabled(ADMIN_APP_PASSWORD_COMPLEXITY, packageName, userId);
    }

    /**
     * For admins targeting R+ reset various password constraints to default values when quality is
     * set to a value that makes those constraints that have no effect.
     */
    private void resetInactivePasswordRequirementsIfRPlus(int userId, ActiveAdmin admin) {
        if (passwordQualityInvocationOrderCheckEnabled(admin.info.getPackageName(), userId)) {
            final PasswordPolicy policy = admin.mPasswordPolicy;
            if (policy.quality < PASSWORD_QUALITY_NUMERIC) {
                policy.length = PasswordPolicy.DEF_MINIMUM_LENGTH;
            }
            if (policy.quality < PASSWORD_QUALITY_COMPLEX) {
                policy.letters = PasswordPolicy.DEF_MINIMUM_LETTERS;
                policy.upperCase = PasswordPolicy.DEF_MINIMUM_UPPER_CASE;
                policy.lowerCase = PasswordPolicy.DEF_MINIMUM_LOWER_CASE;
                policy.numeric = PasswordPolicy.DEF_MINIMUM_NUMERIC;
                policy.symbols = PasswordPolicy.DEF_MINIMUM_SYMBOLS;
                policy.nonLetter = PasswordPolicy.DEF_MINIMUM_NON_LETTER;
            }
        }
    }

    /**
     * Updates a flag that tells us whether the user's password currently satisfies the
     * requirements set by all of the user's active admins.
     * This should be called whenever the password or the admin policies have changed. The caller
     * is responsible for calling {@link #saveSettingsLocked} to persist the change.
     *
     * @return the set of user IDs that have been affected
     */
    @GuardedBy("getLockObject()")
    private Set<Integer> updatePasswordValidityCheckpointLocked(int userHandle, boolean parent) {
        final ArraySet<Integer> affectedUserIds = new ArraySet<>();
        final int credentialOwner = getCredentialOwner(userHandle, parent);
        DevicePolicyData policy = getUserData(credentialOwner);
        PasswordMetrics metrics = mLockSettingsInternal.getUserPasswordMetrics(credentialOwner);
        // Update the checkpoint only if the user's password metrics is known
        if (metrics != null) {
            final int userToCheck = getProfileParentUserIfRequested(userHandle, parent);
            final boolean newCheckpoint = isPasswordSufficientForUserWithoutCheckpointLocked(
                    metrics, userToCheck);
            if (newCheckpoint != policy.mPasswordValidAtLastCheckpoint) {
                policy.mPasswordValidAtLastCheckpoint = newCheckpoint;
                affectedUserIds.add(credentialOwner);
            }
        }
        return affectedUserIds;
    }

    /**
     * Update password quality values in policy cache for all users in the same user group as
     * the given user. The cached password quality for user X is the aggregated quality among all
     * admins who have influence of user X's screenlock, i.e. it's equivalent to the return value of
     * getPasswordQuality(null, user X, false).
     *
     * Caches for all users in the same user group often need to be updated alltogether because a
     * user's admin policy can affect another's aggregated password quality in some situation.
     * For example a managed profile's policy will affect the parent user if the profile has unified
     * challenge. A profile can also explicitly set a parent password quality which will affect the
     * aggregated password quality of the parent user.
     */
    private void updatePasswordQualityCacheForUserGroup(@UserIdInt int userId) {
        final List<UserInfo> users;
        if (userId == UserHandle.USER_ALL) {
            users = mUserManager.getUsers();
        } else {
            users = mUserManager.getProfiles(userId);
        }
        for (UserInfo userInfo : users) {
            final int currentUserId = userInfo.id;
            mPolicyCache.setPasswordQuality(currentUserId,
                    getPasswordQuality(null, currentUserId, false));
        }
    }

    @Override
    public int getPasswordQuality(ComponentName who, int userHandle, boolean parent) {
        if (!mHasFeature) {
            return PASSWORD_QUALITY_UNSPECIFIED;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        // System caller can query policy for a particular admin.
        Preconditions.checkCallAuthorization(
                who == null || isCallingFromPackage(who.getPackageName(), caller.getUid())
                        || canQueryAdminPolicy(caller));

        synchronized (getLockObject()) {
            int mode = PASSWORD_QUALITY_UNSPECIFIED;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle, parent);
                return admin != null ? admin.mPasswordPolicy.quality : mode;
            }

            // Return the strictest policy across all participating admins.
            List<ActiveAdmin> admins = getActiveAdminsForLockscreenPoliciesLocked(
                    getProfileParentUserIfRequested(userHandle, parent));
            final int N = admins.size();
            for (int i = 0; i < N; i++) {
                ActiveAdmin admin = admins.get(i);
                if (mode < admin.mPasswordPolicy.quality) {
                    mode = admin.mPasswordPolicy.quality;
                }
            }
            return mode;
        }
    }

    private List<ActiveAdmin> getActiveAdminsForLockscreenPoliciesLocked(int userHandle) {
        if (isSeparateProfileChallengeEnabled(userHandle)) {
            // If this user has a separate challenge, only return its restrictions.
            return getUserDataUnchecked(userHandle).mAdminList;
        }
        // If isSeparateProfileChallengeEnabled is false and userHandle points to a managed profile
        // we need to query the parent user who owns the credential.
        return getActiveAdminsForUserAndItsManagedProfilesLocked(getProfileParentId(userHandle),
                (user) -> !mLockPatternUtils.isSeparateProfileChallengeEnabled(user.id));
    }

    /**
     * Get the list of active admins for an affected user:
     * <ul>
     * <li>The active admins associated with the userHandle itself</li>
     * <li>The parent active admins for each managed profile associated with the userHandle</li>
     * </ul>
     *
     * @param userHandle the affected user for whom to get the active admins
     * @return the list of active admins for the affected user
     */
    @GuardedBy("getLockObject()")
    private List<ActiveAdmin> getActiveAdminsForAffectedUserLocked(int userHandle) {
        if (isManagedProfile(userHandle)) {
            return getUserDataUnchecked(userHandle).mAdminList;
        }
        return getActiveAdminsForUserAndItsManagedProfilesLocked(userHandle,
                /* shouldIncludeProfileAdmins */ (user) -> false);
    }

    /**
     * Returns the list of admins on the given user, as well as parent admins for each managed
     * profile associated with the given user. Optionally also include the admin of each managed
     * profile.
     * <p> Should not be called on a profile user.
     */
    @GuardedBy("getLockObject()")
    private List<ActiveAdmin> getActiveAdminsForUserAndItsManagedProfilesLocked(int userHandle,
            Predicate<UserInfo> shouldIncludeProfileAdmins) {
        ArrayList<ActiveAdmin> admins = new ArrayList<>();
        mInjector.binderWithCleanCallingIdentity(() -> {
            for (UserInfo userInfo : mUserManager.getProfiles(userHandle)) {
                DevicePolicyData policy = getUserDataUnchecked(userInfo.id);
                if (userInfo.id == userHandle) {
                    admins.addAll(policy.mAdminList);
                } else if (userInfo.isManagedProfile()) {
                    for (int i = 0; i < policy.mAdminList.size(); i++) {
                        ActiveAdmin admin = policy.mAdminList.get(i);
                        if (admin.hasParentActiveAdmin()) {
                            admins.add(admin.getParentActiveAdmin());
                        }
                        if (shouldIncludeProfileAdmins.test(userInfo)) {
                            admins.add(admin);
                        }
                    }
                }
            }
        });
        return admins;
    }

    private boolean isSeparateProfileChallengeEnabled(int userHandle) {
        return mInjector.binderWithCleanCallingIdentity(() ->
                mLockPatternUtils.isSeparateProfileChallengeEnabled(userHandle));
    }

    @Override
    public void setPasswordMinimumLength(ComponentName who, int length, boolean parent) {
        if (!mHasFeature || notSupportedOnAutomotive("setPasswordMinimumLength")) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final int userId = mInjector.userHandleGetCallingUserId();
        synchronized (getLockObject()) {
            ActiveAdmin ap = getActiveAdminForCallerLocked(
                    who, DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD, parent);
            ensureMinimumQuality(userId, ap, PASSWORD_QUALITY_NUMERIC, "setPasswordMinimumLength");
            final PasswordPolicy passwordPolicy = ap.mPasswordPolicy;
            if (passwordPolicy.length != length) {
                passwordPolicy.length = length;
                updatePasswordValidityCheckpointLocked(userId, parent);
                saveSettingsLocked(userId);
            }
            logPasswordQualitySetIfSecurityLogEnabled(who, userId, parent, passwordPolicy);
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_PASSWORD_MINIMUM_LENGTH)
                .setAdmin(who)
                .setInt(length)
                .write();
    }

    private void ensureMinimumQuality(
            int userId, ActiveAdmin admin, int minimumQuality, String operation) {
        mInjector.binderWithCleanCallingIdentity(() -> {
            // This check will also take care of the case where the password requirements
            // are specified as complexity rather than quality: When a password complexity
            // is set, the quality is reset to "unspecified" which will be below any value
            // of minimumQuality.
            if (admin.mPasswordPolicy.quality < minimumQuality
                    && passwordQualityInvocationOrderCheckEnabled(admin.info.getPackageName(),
                    userId)) {
                throw new IllegalStateException(String.format(
                        "password quality should be at least %d for %s",
                        minimumQuality, operation));
            }
        });
    }

    @Override
    public int getPasswordMinimumLength(ComponentName who, int userHandle, boolean parent) {
        return getStrictestPasswordRequirement(who, userHandle, parent,
                admin -> admin.mPasswordPolicy.length, PASSWORD_QUALITY_NUMERIC);
    }

    @Override
    public void setPasswordHistoryLength(ComponentName who, int length, boolean parent) {
        if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final int userId = mInjector.userHandleGetCallingUserId();
        synchronized (getLockObject()) {
            ActiveAdmin ap = getActiveAdminForCallerLocked(
                    who, DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD, parent);
            if (ap.passwordHistoryLength != length) {
                ap.passwordHistoryLength = length;
                updatePasswordValidityCheckpointLocked(userId, parent);
                saveSettingsLocked(userId);
            }
        }
        if (SecurityLog.isLoggingEnabled()) {
            final int affectedUserId = parent ? getProfileParentId(userId) : userId;
            SecurityLog.writeEvent(SecurityLog.TAG_PASSWORD_HISTORY_LENGTH_SET,
                    who.getPackageName(), userId, affectedUserId, length);
        }
    }

    @Override
    public int getPasswordHistoryLength(ComponentName who, int userHandle, boolean parent) {
        if (!mLockPatternUtils.hasSecureLockScreen()) {
            return 0;
        }
        return getStrictestPasswordRequirement(who, userHandle, parent,
                admin -> admin.passwordHistoryLength, PASSWORD_QUALITY_UNSPECIFIED);
    }

    @Override
    public void setPasswordExpirationTimeout(ComponentName who, long timeout, boolean parent) {
        if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        Preconditions.checkArgumentNonnegative(timeout, "Timeout must be >= 0 ms");
        final int userHandle = mInjector.userHandleGetCallingUserId();
        synchronized (getLockObject()) {
            ActiveAdmin ap = getActiveAdminForCallerLocked(
                    who, DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD, parent);
            // Calling this API automatically bumps the expiration date
            final long expiration = timeout > 0L ? (timeout + System.currentTimeMillis()) : 0L;
            ap.passwordExpirationDate = expiration;
            ap.passwordExpirationTimeout = timeout;
            if (timeout > 0L) {
                Slogf.w(LOG_TAG, "setPasswordExpiration(): password will expire on "
                        + DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT)
                        .format(new Date(expiration)));
            }
            saveSettingsLocked(userHandle);

            // in case this is the first one, set the alarm on the appropriate user.
            setExpirationAlarmCheckLocked(mContext, userHandle, parent);
        }
        if (SecurityLog.isLoggingEnabled()) {
            final int affectedUserId = parent ? getProfileParentId(userHandle) : userHandle;
            SecurityLog.writeEvent(SecurityLog.TAG_PASSWORD_EXPIRATION_SET, who.getPackageName(),
                    userHandle, affectedUserId, timeout);
        }
    }

    /**
     * Return a single admin's expiration cycle time, or the min of all cycle times.
     * Returns 0 if not configured.
     */
    @Override
    public long getPasswordExpirationTimeout(ComponentName who, int userHandle, boolean parent) {
        if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
            return 0L;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));

        synchronized (getLockObject()) {
            long timeout = 0L;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle, parent);
                return admin != null ? admin.passwordExpirationTimeout : timeout;
            }

            // Return the strictest policy across all participating admins.
            List<ActiveAdmin> admins = getActiveAdminsForLockscreenPoliciesLocked(
                    getProfileParentUserIfRequested(userHandle, parent));
            final int N = admins.size();
            for (int i = 0; i < N; i++) {
                ActiveAdmin admin = admins.get(i);
                if (timeout == 0L || (admin.passwordExpirationTimeout != 0L
                        && timeout > admin.passwordExpirationTimeout)) {
                    timeout = admin.passwordExpirationTimeout;
                }
            }
            return timeout;
        }
    }

    @Override
    public boolean addCrossProfileWidgetProvider(ComponentName admin, String packageName) {
        Objects.requireNonNull(admin, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(isProfileOwner(caller));
        List<String> changedProviders = null;

        synchronized (getLockObject()) {
            ActiveAdmin activeAdmin = getProfileOwnerLocked(caller.getUserId());
            if (activeAdmin.crossProfileWidgetProviders == null) {
                activeAdmin.crossProfileWidgetProviders = new ArrayList<>();
            }
            List<String> providers = activeAdmin.crossProfileWidgetProviders;
            if (!providers.contains(packageName)) {
                providers.add(packageName);
                changedProviders = new ArrayList<>(providers);
                saveSettingsLocked(caller.getUserId());
            }
        }

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.ADD_CROSS_PROFILE_WIDGET_PROVIDER)
                .setAdmin(admin)
                .write();

        if (changedProviders != null) {
            mLocalService.notifyCrossProfileProvidersChanged(caller.getUserId(),
                    changedProviders);
            return true;
        }

        return false;
    }

    @Override
    public boolean removeCrossProfileWidgetProvider(ComponentName admin, String packageName) {
        Objects.requireNonNull(admin, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(isProfileOwner(caller));
        List<String> changedProviders = null;

        synchronized (getLockObject()) {
            ActiveAdmin activeAdmin = getProfileOwnerLocked(caller.getUserId());
            if (activeAdmin.crossProfileWidgetProviders == null
                    || activeAdmin.crossProfileWidgetProviders.isEmpty()) {
                return false;
            }
            List<String> providers = activeAdmin.crossProfileWidgetProviders;
            if (providers.remove(packageName)) {
                changedProviders = new ArrayList<>(providers);
                saveSettingsLocked(caller.getUserId());
            }
        }

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.REMOVE_CROSS_PROFILE_WIDGET_PROVIDER)
                .setAdmin(admin)
                .write();

        if (changedProviders != null) {
            mLocalService.notifyCrossProfileProvidersChanged(caller.getUserId(),
                    changedProviders);
            return true;
        }

        return false;
    }

    @Override
    public List<String> getCrossProfileWidgetProviders(ComponentName admin) {
        Objects.requireNonNull(admin, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(isProfileOwner(caller));

        synchronized (getLockObject()) {
            ActiveAdmin activeAdmin = getProfileOwnerLocked(caller.getUserId());
            if (activeAdmin.crossProfileWidgetProviders == null
                    || activeAdmin.crossProfileWidgetProviders.isEmpty()) {
                return null;
            }
            if (mInjector.binderIsCallingUidMyUid()) {
                return new ArrayList<>(activeAdmin.crossProfileWidgetProviders);
            } else {
                return activeAdmin.crossProfileWidgetProviders;
            }
        }
    }

    /**
     * Return a single admin's expiration date/time, or the min (soonest) for all admins.
     * Returns 0 if not configured.
     */
    private long getPasswordExpirationLocked(ComponentName who, int userHandle, boolean parent) {
        long timeout = 0L;

        if (who != null) {
            ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle, parent);
            return admin != null ? admin.passwordExpirationDate : timeout;
        }

        // Return the strictest policy across all participating admins.
        List<ActiveAdmin> admins = getActiveAdminsForLockscreenPoliciesLocked(
                getProfileParentUserIfRequested(userHandle, parent));
        final int N = admins.size();
        for (int i = 0; i < N; i++) {
            ActiveAdmin admin = admins.get(i);
            if (timeout == 0L || (admin.passwordExpirationDate != 0
                    && timeout > admin.passwordExpirationDate)) {
                timeout = admin.passwordExpirationDate;
            }
        }
        return timeout;
    }

    @Override
    public long getPasswordExpiration(ComponentName who, int userHandle, boolean parent) {
        if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
            return 0L;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));

        synchronized (getLockObject()) {
            return getPasswordExpirationLocked(who, userHandle, parent);
        }
    }

    @Override
    public void setPasswordMinimumUpperCase(ComponentName who, int length, boolean parent) {
        if (!mHasFeature || notSupportedOnAutomotive("setPasswordMinimumUpperCase")) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final int userId = mInjector.userHandleGetCallingUserId();
        synchronized (getLockObject()) {
            final ActiveAdmin ap = getActiveAdminForCallerLocked(
                    who, DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD, parent);
            ensureMinimumQuality(
                    userId, ap, PASSWORD_QUALITY_COMPLEX, "setPasswordMinimumUpperCase");
            final PasswordPolicy passwordPolicy = ap.mPasswordPolicy;
            if (passwordPolicy.upperCase != length) {
                passwordPolicy.upperCase = length;
                updatePasswordValidityCheckpointLocked(userId, parent);
                saveSettingsLocked(userId);
            }
            logPasswordQualitySetIfSecurityLogEnabled(who, userId, parent, passwordPolicy);
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_PASSWORD_MINIMUM_UPPER_CASE)
                .setAdmin(who)
                .setInt(length)
                .write();
    }

    @Override
    public int getPasswordMinimumUpperCase(ComponentName who, int userHandle, boolean parent) {
        return getStrictestPasswordRequirement(who, userHandle, parent,
                admin -> admin.mPasswordPolicy.upperCase, PASSWORD_QUALITY_COMPLEX);
    }

    @Override
    public void setPasswordMinimumLowerCase(ComponentName who, int length, boolean parent) {
        if (notSupportedOnAutomotive("setPasswordMinimumLowerCase")) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final int userId = mInjector.userHandleGetCallingUserId();
        synchronized (getLockObject()) {
            ActiveAdmin ap = getActiveAdminForCallerLocked(
                    who, DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD, parent);
            ensureMinimumQuality(
                    userId, ap, PASSWORD_QUALITY_COMPLEX, "setPasswordMinimumLowerCase");
            final PasswordPolicy passwordPolicy = ap.mPasswordPolicy;
            if (passwordPolicy.lowerCase != length) {
                passwordPolicy.lowerCase = length;
                updatePasswordValidityCheckpointLocked(userId, parent);
                saveSettingsLocked(userId);
            }
            logPasswordQualitySetIfSecurityLogEnabled(who, userId, parent, passwordPolicy);
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_PASSWORD_MINIMUM_LOWER_CASE)
                .setAdmin(who)
                .setInt(length)
                .write();
    }

    @Override
    public int getPasswordMinimumLowerCase(ComponentName who, int userHandle, boolean parent) {
        return getStrictestPasswordRequirement(who, userHandle, parent,
                admin -> admin.mPasswordPolicy.lowerCase, PASSWORD_QUALITY_COMPLEX);
    }

    @Override
    public void setPasswordMinimumLetters(ComponentName who, int length, boolean parent) {
        if (!mHasFeature || notSupportedOnAutomotive("setPasswordMinimumLetters")) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final int userId = mInjector.userHandleGetCallingUserId();
        synchronized (getLockObject()) {
            ActiveAdmin ap = getActiveAdminForCallerLocked(
                    who, DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD, parent);
            ensureMinimumQuality(userId, ap, PASSWORD_QUALITY_COMPLEX, "setPasswordMinimumLetters");
            final PasswordPolicy passwordPolicy = ap.mPasswordPolicy;
            if (passwordPolicy.letters != length) {
                passwordPolicy.letters = length;
                updatePasswordValidityCheckpointLocked(userId, parent);
                saveSettingsLocked(userId);
            }
            logPasswordQualitySetIfSecurityLogEnabled(who, userId, parent, passwordPolicy);
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_PASSWORD_MINIMUM_LETTERS)
                .setAdmin(who)
                .setInt(length)
                .write();
    }

    @Override
    public int getPasswordMinimumLetters(ComponentName who, int userHandle, boolean parent) {
        return getStrictestPasswordRequirement(who, userHandle, parent,
                admin -> admin.mPasswordPolicy.letters, PASSWORD_QUALITY_COMPLEX);
    }

    @Override
    public void setPasswordMinimumNumeric(ComponentName who, int length, boolean parent) {
        if (!mHasFeature || notSupportedOnAutomotive("setPasswordMinimumNumeric")) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final int userId = mInjector.userHandleGetCallingUserId();
        synchronized (getLockObject()) {
            ActiveAdmin ap = getActiveAdminForCallerLocked(
                    who, DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD, parent);
            ensureMinimumQuality(userId, ap, PASSWORD_QUALITY_COMPLEX, "setPasswordMinimumNumeric");
            final PasswordPolicy passwordPolicy = ap.mPasswordPolicy;
            if (passwordPolicy.numeric != length) {
                passwordPolicy.numeric = length;
                updatePasswordValidityCheckpointLocked(userId, parent);
                saveSettingsLocked(userId);
            }
            logPasswordQualitySetIfSecurityLogEnabled(who, userId, parent, passwordPolicy);
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_PASSWORD_MINIMUM_NUMERIC)
                .setAdmin(who)
                .setInt(length)
                .write();
    }

    @Override
    public int getPasswordMinimumNumeric(ComponentName who, int userHandle, boolean parent) {
        return getStrictestPasswordRequirement(who, userHandle, parent,
                admin -> admin.mPasswordPolicy.numeric, PASSWORD_QUALITY_COMPLEX);
    }

    @Override
    public void setPasswordMinimumSymbols(ComponentName who, int length, boolean parent) {
        if (!mHasFeature || notSupportedOnAutomotive("setPasswordMinimumSymbols")) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final int userId = mInjector.userHandleGetCallingUserId();
        synchronized (getLockObject()) {
            ActiveAdmin ap = getActiveAdminForCallerLocked(
                    who, DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD, parent);
            ensureMinimumQuality(userId, ap, PASSWORD_QUALITY_COMPLEX, "setPasswordMinimumSymbols");
            final PasswordPolicy passwordPolicy = ap.mPasswordPolicy;
            if (passwordPolicy.symbols != length) {
                ap.mPasswordPolicy.symbols = length;
                updatePasswordValidityCheckpointLocked(userId, parent);
                saveSettingsLocked(userId);
            }
            logPasswordQualitySetIfSecurityLogEnabled(who, userId, parent, passwordPolicy);
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_PASSWORD_MINIMUM_SYMBOLS)
                .setAdmin(who)
                .setInt(length)
                .write();
    }

    @Override
    public int getPasswordMinimumSymbols(ComponentName who, int userHandle, boolean parent) {
        return getStrictestPasswordRequirement(who, userHandle, parent,
                admin -> admin.mPasswordPolicy.symbols, PASSWORD_QUALITY_COMPLEX);
    }

    @Override
    public void setPasswordMinimumNonLetter(ComponentName who, int length, boolean parent) {
        if (!mHasFeature || notSupportedOnAutomotive("setPasswordMinimumNonLetter")) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final int userId = mInjector.userHandleGetCallingUserId();
        synchronized (getLockObject()) {
            ActiveAdmin ap = getActiveAdminForCallerLocked(
                    who, DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD, parent);
            ensureMinimumQuality(
                    userId, ap, PASSWORD_QUALITY_COMPLEX, "setPasswordMinimumNonLetter");
            final PasswordPolicy passwordPolicy = ap.mPasswordPolicy;
            if (passwordPolicy.nonLetter != length) {
                ap.mPasswordPolicy.nonLetter = length;
                updatePasswordValidityCheckpointLocked(userId, parent);
                saveSettingsLocked(userId);
            }
            logPasswordQualitySetIfSecurityLogEnabled(who, userId, parent, passwordPolicy);
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_PASSWORD_MINIMUM_NON_LETTER)
                .setAdmin(who)
                .setInt(length)
                .write();
    }

    @Override
    public int getPasswordMinimumNonLetter(ComponentName who, int userHandle, boolean parent) {
        return getStrictestPasswordRequirement(who, userHandle, parent,
                admin -> admin.mPasswordPolicy.nonLetter, PASSWORD_QUALITY_COMPLEX);
    }

    /**
     * Calculates strictest (maximum) value for a given password property enforced by admin[s].
     */
    private int getStrictestPasswordRequirement(ComponentName who, int userHandle,
            boolean parent, Function<ActiveAdmin, Integer> getter, int minimumPasswordQuality) {
        if (!mHasFeature) {
            return 0;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));

        synchronized (getLockObject()) {
            if (who != null) {
                final ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle, parent);
                return admin != null ? getter.apply(admin) : 0;
            }

            int maxValue = 0;
            final List<ActiveAdmin> admins = getActiveAdminsForLockscreenPoliciesLocked(
                    getProfileParentUserIfRequested(userHandle, parent));
            final int N = admins.size();
            for (int i = 0; i < N; i++) {
                final ActiveAdmin admin = admins.get(i);
                if (!isLimitPasswordAllowed(admin, minimumPasswordQuality)) {
                    continue;
                }
                final Integer adminValue = getter.apply(admin);
                if (adminValue > maxValue) {
                    maxValue = adminValue;
                }
            }
            return maxValue;
        }
    }

    /**
     * Calculates strictest (maximum) value for a given password property enforced by admin[s].
     */
    @Override
    public PasswordMetrics getPasswordMinimumMetrics(@UserIdInt int userHandle,
            boolean deviceWideOnly) {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle)
                && (isSystemUid(caller) || hasCallingOrSelfPermission(
                permission.SET_INITIAL_LOCK)));
        return getPasswordMinimumMetricsUnchecked(userHandle, deviceWideOnly);
    }

    private PasswordMetrics getPasswordMinimumMetricsUnchecked(@UserIdInt int userId) {
        return getPasswordMinimumMetricsUnchecked(userId, false);
    }

    private PasswordMetrics getPasswordMinimumMetricsUnchecked(@UserIdInt int userId,
            boolean deviceWideOnly) {
        if (!mHasFeature) {
            new PasswordMetrics(CREDENTIAL_TYPE_NONE);
        }
        Preconditions.checkArgumentNonnegative(userId, "Invalid userId");
        if (deviceWideOnly) {
            Preconditions.checkArgument(!isManagedProfile(userId));
        }

        ArrayList<PasswordMetrics> adminMetrics = new ArrayList<>();
        final List<ActiveAdmin> admins;
        synchronized (getLockObject()) {
            if (deviceWideOnly) {
                admins = getActiveAdminsForUserAndItsManagedProfilesLocked(userId,
                        /* shouldIncludeProfileAdmins */ (user) -> false);
            } else {
                admins = getActiveAdminsForLockscreenPoliciesLocked(userId);
            }
            for (ActiveAdmin admin : admins) {
                adminMetrics.add(admin.mPasswordPolicy.getMinMetrics());
            }
        }
        return PasswordMetrics.merge(adminMetrics);
    }

    @Override
    public boolean isActivePasswordSufficient(int userHandle, boolean parent) {
        if (!mHasFeature) {
            return true;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        enforceUserUnlocked(userHandle, parent);

        synchronized (getLockObject()) {
            // This API can only be called by an active device admin,
            // so try to retrieve it to check that the caller is one.
            getActiveAdminForCallerLocked(null, DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD, parent);
            int credentialOwner = getCredentialOwner(userHandle, parent);
            DevicePolicyData policy = getUserDataUnchecked(credentialOwner);
            PasswordMetrics metrics = mLockSettingsInternal.getUserPasswordMetrics(credentialOwner);
            final int userToCheck = getProfileParentUserIfRequested(userHandle, parent);
            boolean activePasswordSufficientForUserLocked = isActivePasswordSufficientForUserLocked(
                    policy.mPasswordValidAtLastCheckpoint, metrics, userToCheck);
            return activePasswordSufficientForUserLocked;
        }
    }

    @Override
    public boolean isActivePasswordSufficientForDeviceRequirement() {
        if (!mHasFeature) {
            return true;
        }
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(isProfileOwner(caller));

        final int profileUserId = caller.getUserId();
        Preconditions.checkCallingUser(isManagedProfile(profileUserId));

        // This method is always called on the parent DPM instance to check if its password (i.e.
        // the device password) is sufficient for all explicit password requirement set on it
        // So retrieve the parent user Id to which the device password belongs.
        final int parentUser = getProfileParentId(profileUserId);
        enforceUserUnlocked(parentUser);

        final boolean isSufficient;
        synchronized (getLockObject()) {

            int complexity = getAggregatedPasswordComplexityLocked(parentUser, true);
            PasswordMetrics minMetrics = getPasswordMinimumMetricsUnchecked(parentUser, true);

            PasswordMetrics metrics = mLockSettingsInternal.getUserPasswordMetrics(parentUser);
            final List<PasswordValidationError> passwordValidationErrors =
                    PasswordMetrics.validatePasswordMetrics(minMetrics, complexity, metrics);
            isSufficient = passwordValidationErrors.isEmpty();
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.IS_ACTIVE_PASSWORD_SUFFICIENT_FOR_DEVICE)
                .setStrings(mOwners.getProfileOwnerComponent(caller.getUserId()).getPackageName())
                .write();
        return isSufficient;
    }

    @Override
    public boolean isUsingUnifiedPassword(ComponentName admin) {
        if (!mHasFeature) {
            return true;
        }
        Objects.requireNonNull(admin, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                || isProfileOwner(caller));
        Preconditions.checkCallingUser(isManagedProfile(caller.getUserId()));

        return !isSeparateProfileChallengeEnabled(caller.getUserId());
    }

    @Override
    public boolean isPasswordSufficientAfterProfileUnification(int userHandle, int profileUser) {
        if (!mHasFeature) {
            return true;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        Preconditions.checkCallAuthorization(!isManagedProfile(userHandle),
                "You can not check password sufficiency for a managed profile, userId = %d",
                userHandle);
        enforceUserUnlocked(userHandle);

        synchronized (getLockObject()) {
            PasswordMetrics metrics = mLockSettingsInternal.getUserPasswordMetrics(userHandle);

            // Combine password policies across the user and its profiles. Profile admins are
            // included if the profile is to be unified or currently has unified challenge
            List<ActiveAdmin> admins = getActiveAdminsForUserAndItsManagedProfilesLocked(userHandle,
                    /* shouldIncludeProfileAdmins */ (user) -> user.id == profileUser
                    || !mLockPatternUtils.isSeparateProfileChallengeEnabled(user.id));
            ArrayList<PasswordMetrics> adminMetrics = new ArrayList<>(admins.size());
            int maxRequiredComplexity = PASSWORD_COMPLEXITY_NONE;
            for (ActiveAdmin admin : admins) {
                adminMetrics.add(admin.mPasswordPolicy.getMinMetrics());
                maxRequiredComplexity = Math.max(maxRequiredComplexity, admin.mPasswordComplexity);
            }
            return PasswordMetrics.validatePasswordMetrics(PasswordMetrics.merge(adminMetrics),
                    maxRequiredComplexity, metrics).isEmpty();
        }
    }

    private boolean isActivePasswordSufficientForUserLocked(
            boolean passwordValidAtLastCheckpoint, @Nullable PasswordMetrics metrics,
            int userHandle) {
        if (!mInjector.storageManagerIsFileBasedEncryptionEnabled() && (metrics == null)) {
            // Before user enters their password for the first time after a reboot, return the
            // value of this flag, which tells us whether the password was valid the last time
            // settings were saved.  If DPC changes password requirements on boot so that the
            // current password no longer meets the requirements, this value will be stale until
            // the next time the password is entered.
            return passwordValidAtLastCheckpoint;
        }

        if (metrics == null) {
            // Called on a FBE device when the user password exists but its metrics is unknown.
            // This shouldn't happen since we enforce the user to be unlocked (which would result
            // in the metrics known to the framework on a FBE device) at all call sites.
            throw new IllegalStateException("isActivePasswordSufficient called on FBE-locked user");
        }

        return isPasswordSufficientForUserWithoutCheckpointLocked(metrics, userHandle);
    }

    /**
     * Returns {@code true} if the password represented by the {@code metrics} argument
     * sufficiently fulfills the password requirements for the user corresponding to
     * {@code userId}.
     */
    private boolean isPasswordSufficientForUserWithoutCheckpointLocked(
            @NonNull PasswordMetrics metrics, @UserIdInt int userId) {
        final int complexity = getAggregatedPasswordComplexityLocked(userId);
        PasswordMetrics minMetrics = getPasswordMinimumMetricsUnchecked(userId);
        final List<PasswordValidationError> passwordValidationErrors =
                PasswordMetrics.validatePasswordMetrics(minMetrics, complexity, metrics);
        return passwordValidationErrors.isEmpty();
    }

    @Override
    @PasswordComplexity
    public int getPasswordComplexity(boolean parent) {
        final CallerIdentity caller = getCallerIdentity();
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.GET_USER_PASSWORD_COMPLEXITY_LEVEL)
                .setStrings(parent ? CALLED_FROM_PARENT : NOT_CALLED_FROM_PARENT,
                        mInjector.getPackageManager().getPackagesForUid(caller.getUid()))
                .write();

        enforceUserUnlocked(caller.getUserId());
        if (parent) {
            Preconditions.checkCallAuthorization(
                    isDefaultDeviceOwner(caller) || isProfileOwner(caller) || isSystemUid(caller),
                    "Only profile owner, device owner and system may call this method on parent.");
        } else {
            Preconditions.checkCallAuthorization(
                    hasCallingOrSelfPermission(REQUEST_PASSWORD_COMPLEXITY)
                            || isDefaultDeviceOwner(caller) || isProfileOwner(caller),
                    "Must have " + REQUEST_PASSWORD_COMPLEXITY
                            + " permission, or be a profile owner or device owner.");
        }

        synchronized (getLockObject()) {
            final int credentialOwner = getCredentialOwner(caller.getUserId(), parent);
            PasswordMetrics metrics = mLockSettingsInternal.getUserPasswordMetrics(credentialOwner);
            return metrics == null ? PASSWORD_COMPLEXITY_NONE : metrics.determineComplexity();
        }
    }

    @Override
    public void setRequiredPasswordComplexity(int passwordComplexity, boolean calledOnParent) {
        if (!mHasFeature) {
            return;
        }
        final Set<Integer> allowedModes = Set.of(PASSWORD_COMPLEXITY_NONE, PASSWORD_COMPLEXITY_LOW,
                PASSWORD_COMPLEXITY_MEDIUM, PASSWORD_COMPLEXITY_HIGH);
        Preconditions.checkArgument(allowedModes.contains(passwordComplexity),
                "Provided complexity is not one of the allowed values.");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));
        Preconditions.checkArgument(!calledOnParent || isProfileOwner(caller));

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getParentOfAdminIfRequired(
                    getProfileOwnerOrDeviceOwnerLocked(caller.getUserId()), calledOnParent);
            if (admin.mPasswordComplexity != passwordComplexity) {
                // We require the caller to explicitly clear any password quality requirements set
                // on the parent DPM instance, to avoid the case where password requirements are
                // specified in the form of quality on the parent but complexity on the profile
                // itself.
                if (!calledOnParent) {
                    final boolean hasQualityRequirementsOnParent = admin.hasParentActiveAdmin()
                            && admin.getParentActiveAdmin().mPasswordPolicy.quality
                            != PASSWORD_QUALITY_UNSPECIFIED;
                    Preconditions.checkState(!hasQualityRequirementsOnParent,
                            "Password quality is set on the parent when attempting to set password"
                            + "complexity. Clear the quality by setting the password quality "
                            + "on the parent to PASSWORD_QUALITY_UNSPECIFIED first");
                }

                mInjector.binderWithCleanCallingIdentity(() -> {
                    admin.mPasswordComplexity = passwordComplexity;
                    // Reset the password policy.
                    admin.mPasswordPolicy = new PasswordPolicy();
                    updatePasswordValidityCheckpointLocked(caller.getUserId(), calledOnParent);
                    updatePasswordQualityCacheForUserGroup(caller.getUserId());
                    saveSettingsLocked(caller.getUserId());
                });

                DevicePolicyEventLogger
                        .createEvent(DevicePolicyEnums.SET_PASSWORD_COMPLEXITY)
                        .setAdmin(admin.info.getPackageName())
                        .setInt(passwordComplexity)
                        .setBoolean(calledOnParent)
                        .write();
            }
            logPasswordComplexityRequiredIfSecurityLogEnabled(admin.info.getComponent(),
                    caller.getUserId(), calledOnParent, passwordComplexity);
        }
    }

    private void logPasswordComplexityRequiredIfSecurityLogEnabled(ComponentName who, int userId,
            boolean parent, int complexity) {
        if (SecurityLog.isLoggingEnabled()) {
            final int affectedUserId = parent ? getProfileParentId(userId) : userId;
            SecurityLog.writeEvent(SecurityLog.TAG_PASSWORD_COMPLEXITY_REQUIRED,
                    who.getPackageName(), userId, affectedUserId, complexity);
        }
    }

    private int getAggregatedPasswordComplexityLocked(@UserIdInt int userHandle) {
        return getAggregatedPasswordComplexityLocked(userHandle, false);
    }

    private int getAggregatedPasswordComplexityLocked(@UserIdInt int userHandle,
            boolean deviceWideOnly) {
        ensureLocked();
        final List<ActiveAdmin> admins;
        if (deviceWideOnly) {
            admins = getActiveAdminsForUserAndItsManagedProfilesLocked(userHandle,
                    /* shouldIncludeProfileAdmins */ (user) -> false);
        } else {
            admins = getActiveAdminsForLockscreenPoliciesLocked(userHandle);
        }
        int maxRequiredComplexity = PASSWORD_COMPLEXITY_NONE;
        for (ActiveAdmin admin : admins) {
            maxRequiredComplexity = Math.max(maxRequiredComplexity, admin.mPasswordComplexity);
        }
        return maxRequiredComplexity;
    }

    @Override
    public int getRequiredPasswordComplexity(boolean calledOnParent) {
        if (!mHasFeature) {
            return PASSWORD_COMPLEXITY_NONE;
        }

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));

        Preconditions.checkArgument(!calledOnParent || isProfileOwner(caller));

        synchronized (getLockObject()) {
            final ActiveAdmin requiredAdmin = getParentOfAdminIfRequired(
                    getDeviceOrProfileOwnerAdminLocked(caller.getUserId()), calledOnParent);
            return requiredAdmin.mPasswordComplexity;
        }
    }

    @Override
    public int getAggregatedPasswordComplexityForUser(int userId, boolean deviceWideOnly) {
        if (!mHasFeature) {
            return PASSWORD_COMPLEXITY_NONE;
        }

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userId));

        synchronized (getLockObject()) {
            return getAggregatedPasswordComplexityLocked(userId, deviceWideOnly);
        }
    }


    @Override
    public int getCurrentFailedPasswordAttempts(int userHandle, boolean parent) {
        if (!mLockPatternUtils.hasSecureLockScreen()) {
            return 0;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));

        synchronized (getLockObject()) {
            if (!isSystemUid(caller)) {
                // This API can be called by an active device admin or by keyguard code.
                if (!hasCallingPermission(permission.ACCESS_KEYGUARD_SECURE_STORAGE)) {
                    getActiveAdminForCallerLocked(
                            null, DeviceAdminInfo.USES_POLICY_WATCH_LOGIN, parent);
                }
            }

            DevicePolicyData policy = getUserDataUnchecked(getCredentialOwner(userHandle, parent));

            return policy.mFailedPasswordAttempts;
        }
    }

    @Override
    public void setMaximumFailedPasswordsForWipe(ComponentName who, int num, boolean parent) {
        if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final int userId = mInjector.userHandleGetCallingUserId();
        synchronized (getLockObject()) {
            // This API can only be called by an active device admin,
            // so try to retrieve it to check that the caller is one.
            getActiveAdminForCallerLocked(
                    who, DeviceAdminInfo.USES_POLICY_WIPE_DATA, parent);
            ActiveAdmin ap = getActiveAdminForCallerLocked(
                    who, DeviceAdminInfo.USES_POLICY_WATCH_LOGIN, parent);
            if (ap.maximumFailedPasswordsForWipe != num) {
                ap.maximumFailedPasswordsForWipe = num;
                saveSettingsLocked(userId);
            }
        }
        if (SecurityLog.isLoggingEnabled()) {
            final int affectedUserId = parent ? getProfileParentId(userId) : userId;
            SecurityLog.writeEvent(SecurityLog.TAG_MAX_PASSWORD_ATTEMPTS_SET, who.getPackageName(),
                    userId, affectedUserId, num);
        }
    }

    @Override
    public int getMaximumFailedPasswordsForWipe(ComponentName who, int userHandle, boolean parent) {
        if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
            return 0;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        // System caller can query policy for a particular admin.
        Preconditions.checkCallAuthorization(
                who == null || isCallingFromPackage(who.getPackageName(), caller.getUid())
                        || canQueryAdminPolicy(caller));

        synchronized (getLockObject()) {
            ActiveAdmin admin = (who != null)
                    ? getActiveAdminUncheckedLocked(who, userHandle, parent)
                    : getAdminWithMinimumFailedPasswordsForWipeLocked(userHandle, parent);
            return admin != null ? admin.maximumFailedPasswordsForWipe : 0;
        }
    }

    @Override
    public int getProfileWithMinimumFailedPasswordsForWipe(int userHandle, boolean parent) {
        if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
            return UserHandle.USER_NULL;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getAdminWithMinimumFailedPasswordsForWipeLocked(
                    userHandle, parent);
            return admin != null ? getUserIdToWipeForFailedPasswords(admin) : UserHandle.USER_NULL;
        }
    }

    /**
     * Returns the admin with the strictest policy on maximum failed passwords for:
     * <ul>
     *   <li>this user if it has a separate profile challenge, or
     *   <li>this user and all profiles that don't have their own challenge otherwise.
     * </ul>
     * <p>If the policy for the primary and any other profile are equal, it returns the admin for
     * the primary profile. Policy of a PO on an organization-owned device applies to the primary
     * profile.
     * Returns {@code null} if no participating admin has that policy set.
     */
    private ActiveAdmin getAdminWithMinimumFailedPasswordsForWipeLocked(
            int userHandle, boolean parent) {
        int count = 0;
        ActiveAdmin strictestAdmin = null;

        // Return the strictest policy across all participating admins.
        List<ActiveAdmin> admins = getActiveAdminsForLockscreenPoliciesLocked(
                getProfileParentUserIfRequested(userHandle, parent));
        final int N = admins.size();
        for (int i = 0; i < N; i++) {
            ActiveAdmin admin = admins.get(i);
            if (admin.maximumFailedPasswordsForWipe ==
                    ActiveAdmin.DEF_MAXIMUM_FAILED_PASSWORDS_FOR_WIPE) {
                continue;  // No max number of failed passwords policy set for this profile.
            }

            // We always favor the primary profile if several profiles have the same value set.
            final int userId = getUserIdToWipeForFailedPasswords(admin);
            if (count == 0 ||
                    count > admin.maximumFailedPasswordsForWipe ||
                    (count == admin.maximumFailedPasswordsForWipe &&
                            getUserInfo(userId).isPrimary())) {
                count = admin.maximumFailedPasswordsForWipe;
                strictestAdmin = admin;
            }
        }
        return strictestAdmin;
    }

    private UserInfo getUserInfo(@UserIdInt int userId) {
        return mInjector.binderWithCleanCallingIdentity(() -> mUserManager.getUserInfo(userId));
    }

    private boolean setPasswordPrivileged(@NonNull String password, int flags,
            CallerIdentity caller) {
        // Only allow setting password on an unsecured user
        if (isLockScreenSecureUnchecked(caller.getUserId())) {
            throw new SecurityException("Cannot change current password");
        }
        return resetPasswordInternal(password, 0, null, flags, caller);
    }

    @Override
    public boolean resetPassword(@Nullable String password, int flags) throws RemoteException {
        if (!mLockPatternUtils.hasSecureLockScreen()) {
            Slogf.w(LOG_TAG, "Cannot reset password when the device has no lock screen");
            return false;
        }
        if (password == null) password = "";
        final CallerIdentity caller = getCallerIdentity();
        final int userHandle = caller.getUserId();

        // As of R, only privileged caller holding RESET_PASSWORD can call resetPassword() to
        // set password to an unsecured user.
        if (hasCallingPermission(permission.RESET_PASSWORD)) {
            final boolean result = setPasswordPrivileged(password, flags, caller);
            if (result) {
                DevicePolicyEventLogger
                        .createEvent(DevicePolicyEnums.RESET_PASSWORD)
                        .write();
            }
            return result;
        }

        // If caller has PO (or DO) throw or fail silently depending on its target SDK level.
        if (isDefaultDeviceOwner(caller) || isProfileOwner(caller)) {
            synchronized (getLockObject()) {
                ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
                if (getTargetSdk(admin.info.getPackageName(), userHandle) < Build.VERSION_CODES.O) {
                    Slogf.e(LOG_TAG, "DPC can no longer call resetPassword()");
                    return false;
                }
                throw new SecurityException("Device admin can no longer call resetPassword()");
            }
        }

        // Caller is not DO or PO, could either be unauthorized or Device Admin.
        synchronized (getLockObject()) {
            // Legacy device admin cannot call resetPassword either
            ActiveAdmin admin = getActiveAdminForCallerLocked(
                    null, DeviceAdminInfo.USES_POLICY_RESET_PASSWORD, false);
            Preconditions.checkCallAuthorization(admin != null,
                    "Unauthorized caller cannot call resetPassword.");
            if (getTargetSdk(admin.info.getPackageName(),
                    userHandle) <= android.os.Build.VERSION_CODES.M) {
                Slogf.e(LOG_TAG, "Device admin can no longer call resetPassword()");
                return false;
            }
            throw new SecurityException("Device admin can no longer call resetPassword()");
        }
    }

    private boolean resetPasswordInternal(String password, long tokenHandle, byte[] token,
            int flags, CallerIdentity caller) {
        final int callingUid = caller.getUid();
        final int userHandle = UserHandle.getUserId(callingUid);
        final boolean isPin = PasswordMetrics.isNumericOnly(password);
        synchronized (getLockObject()) {
            final PasswordMetrics minMetrics = getPasswordMinimumMetricsUnchecked(userHandle);
            final List<PasswordValidationError> validationErrors;
            final int complexity = getAggregatedPasswordComplexityLocked(userHandle);
            // TODO: Consider changing validation API to take LockscreenCredential.
            if (password.isEmpty()) {
                validationErrors = PasswordMetrics.validatePasswordMetrics(
                        minMetrics, complexity, new PasswordMetrics(CREDENTIAL_TYPE_NONE));
            } else {
                // TODO(b/120484642): remove getBytes() below
                validationErrors = PasswordMetrics.validatePassword(
                        minMetrics, complexity, isPin, password.getBytes());
            }

            if (!validationErrors.isEmpty()) {
                Slogf.w(LOG_TAG, "Failed to reset password due to constraint violation: %s",
                        validationErrors.get(0));
                return false;
            }
        }

        DevicePolicyData policy = getUserData(userHandle);
        if (policy.mPasswordOwner >= 0 && policy.mPasswordOwner != callingUid) {
            Slogf.w(LOG_TAG, "resetPassword: already set by another uid and not entered by user");
            return false;
        }

        boolean callerIsDeviceOwnerAdmin = isDefaultDeviceOwner(caller);
        boolean doNotAskCredentialsOnBoot =
                (flags & DevicePolicyManager.RESET_PASSWORD_DO_NOT_ASK_CREDENTIALS_ON_BOOT) != 0;
        if (callerIsDeviceOwnerAdmin && doNotAskCredentialsOnBoot) {
            setDoNotAskCredentialsOnBoot();
        }

        // Don't do this with the lock held, because it is going to call
        // back in to the service.
        final long ident = mInjector.binderClearCallingIdentity();
        final LockscreenCredential newCredential;
        if (isPin) {
            newCredential = LockscreenCredential.createPin(password);
        } else {
            newCredential = LockscreenCredential.createPasswordOrNone(password);
        }
        try {
            if (tokenHandle == 0 || token == null) {
                if (!mLockPatternUtils.setLockCredential(newCredential,
                        LockscreenCredential.createNone(), userHandle)) {
                    return false;
                }
            } else {
                if (!mLockPatternUtils.setLockCredentialWithToken(newCredential, tokenHandle,
                        token, userHandle)) {
                    return false;
                }
            }
            boolean requireEntry = (flags & DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY) != 0;
            if (requireEntry) {
                mLockPatternUtils.requireStrongAuth(STRONG_AUTH_REQUIRED_AFTER_DPM_LOCK_NOW,
                        UserHandle.USER_ALL);
            }
            synchronized (getLockObject()) {
                int newOwner = requireEntry ? callingUid : -1;
                if (policy.mPasswordOwner != newOwner) {
                    policy.mPasswordOwner = newOwner;
                    saveSettingsLocked(userHandle);
                }
            }
        } finally {
            mInjector.binderRestoreCallingIdentity(ident);
        }
        return true;
    }

    private boolean isLockScreenSecureUnchecked(int userId) {
        return mInjector.binderWithCleanCallingIdentity(() -> mLockPatternUtils.isSecure(userId));
    }

    private void setDoNotAskCredentialsOnBoot() {
        synchronized (getLockObject()) {
            DevicePolicyData policyData = getUserData(UserHandle.USER_SYSTEM);
            if (!policyData.mDoNotAskCredentialsOnBoot) {
                policyData.mDoNotAskCredentialsOnBoot = true;
                saveSettingsLocked(UserHandle.USER_SYSTEM);
            }
        }
    }

    @Override
    public boolean getDoNotAskCredentialsOnBoot() {
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.QUERY_DO_NOT_ASK_CREDENTIALS_ON_BOOT));
        synchronized (getLockObject()) {
            DevicePolicyData policyData = getUserData(UserHandle.USER_SYSTEM);
            return policyData.mDoNotAskCredentialsOnBoot;
        }
    }

    @Override
    public void setMaximumTimeToLock(ComponentName who, long timeMs, boolean parent) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final int userHandle = mInjector.userHandleGetCallingUserId();
        synchronized (getLockObject()) {
            final ActiveAdmin ap = getActiveAdminForCallerLocked(
                    who, DeviceAdminInfo.USES_POLICY_FORCE_LOCK, parent);
            if (ap.maximumTimeToUnlock != timeMs) {
                ap.maximumTimeToUnlock = timeMs;
                saveSettingsLocked(userHandle);
                updateMaximumTimeToLockLocked(userHandle);
            }
        }
        if (SecurityLog.isLoggingEnabled()) {
            final int affectedUserId = parent ? getProfileParentId(userHandle) : userHandle;
            SecurityLog.writeEvent(SecurityLog.TAG_MAX_SCREEN_LOCK_TIMEOUT_SET,
                    who.getPackageName(), userHandle, affectedUserId, timeMs);
        }
    }

    private void updateMaximumTimeToLockLocked(@UserIdInt int userId) {
        // Update the profile's timeout
        if (isManagedProfile(userId)) {
            updateProfileLockTimeoutLocked(userId);
        }

        mInjector.binderWithCleanCallingIdentity(() -> {
            // Update the device timeout
            final int parentId = getProfileParentId(userId);
            final long timeMs = getMaximumTimeToLockPolicyFromAdmins(
                    getActiveAdminsForLockscreenPoliciesLocked(parentId));

            final DevicePolicyData policy = getUserDataUnchecked(parentId);
            if (policy.mLastMaximumTimeToLock == timeMs) {
                return;
            }
            policy.mLastMaximumTimeToLock = timeMs;

            if (policy.mLastMaximumTimeToLock != Long.MAX_VALUE) {
                // Make sure KEEP_SCREEN_ON is disabled, since that
                // would allow bypassing of the maximum time to lock.
                mInjector.settingsGlobalPutInt(Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0);
            }
            getPowerManagerInternal().setMaximumScreenOffTimeoutFromDeviceAdmin(
                    UserHandle.USER_SYSTEM, timeMs);
        });
    }

    private void updateProfileLockTimeoutLocked(@UserIdInt int userId) {
        final long timeMs;
        if (isSeparateProfileChallengeEnabled(userId)) {
            timeMs = getMaximumTimeToLockPolicyFromAdmins(
                    getActiveAdminsForLockscreenPoliciesLocked(userId));
        } else {
            timeMs = Long.MAX_VALUE;
        }

        final DevicePolicyData policy = getUserDataUnchecked(userId);
        if (policy.mLastMaximumTimeToLock == timeMs) {
            return;
        }
        policy.mLastMaximumTimeToLock = timeMs;

        mInjector.binderWithCleanCallingIdentity(() ->
                getPowerManagerInternal().setMaximumScreenOffTimeoutFromDeviceAdmin(
                        userId, policy.mLastMaximumTimeToLock));
    }

    @Override
    public long getMaximumTimeToLock(ComponentName who, int userHandle, boolean parent) {
        if (!mHasFeature) {
            return 0;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        // System caller can query policy for a particular admin.
        Preconditions.checkCallAuthorization(
                who == null || isCallingFromPackage(who.getPackageName(), caller.getUid())
                        || canQueryAdminPolicy(caller));

        synchronized (getLockObject()) {
            if (who != null) {
                final ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle, parent);
                return admin != null ? admin.maximumTimeToUnlock : 0;
            }
            // Return the strictest policy across all participating admins.
            final List<ActiveAdmin> admins = getActiveAdminsForLockscreenPoliciesLocked(
                    getProfileParentUserIfRequested(userHandle, parent));
            final long timeMs = getMaximumTimeToLockPolicyFromAdmins(admins);
            return timeMs == Long.MAX_VALUE ? 0 : timeMs;
        }
    }

    private long getMaximumTimeToLockPolicyFromAdmins(List<ActiveAdmin> admins) {
        long time = Long.MAX_VALUE;
        for (final ActiveAdmin admin : admins) {
            if (admin.maximumTimeToUnlock > 0 && admin.maximumTimeToUnlock < time) {
                time = admin.maximumTimeToUnlock;
            }
        }
        return time;
    }

    @Override
    public void setRequiredStrongAuthTimeout(ComponentName who, long timeoutMs,
            boolean parent) {
        if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        Preconditions.checkArgument(timeoutMs >= 0, "Timeout must not be a negative number.");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));
        // timeoutMs with value 0 means that the admin doesn't participate
        // timeoutMs is clamped to the interval in case the internal constants change in the future
        final long minimumStrongAuthTimeout = getMinimumStrongAuthTimeoutMs();
        if (timeoutMs != 0 && timeoutMs < minimumStrongAuthTimeout) {
            timeoutMs = minimumStrongAuthTimeout;
        }
        if (timeoutMs > DevicePolicyManager.DEFAULT_STRONG_AUTH_TIMEOUT_MS) {
            timeoutMs = DevicePolicyManager.DEFAULT_STRONG_AUTH_TIMEOUT_MS;
        }

        final int userHandle = caller.getUserId();
        boolean changed = false;
        synchronized (getLockObject()) {
            ActiveAdmin ap = getParentOfAdminIfRequired(
                    getProfileOwnerOrDeviceOwnerLocked(caller.getUserId()), parent);
            if (ap.strongAuthUnlockTimeout != timeoutMs) {
                ap.strongAuthUnlockTimeout = timeoutMs;
                saveSettingsLocked(userHandle);
                changed = true;
            }
        }
        if (changed) {
            mLockSettingsInternal.refreshStrongAuthTimeout(userHandle);
            // Refreshes the parent if profile has unified challenge, since the timeout would
            // also affect the parent user in this case.
            if (isManagedProfile(userHandle) && !isSeparateProfileChallengeEnabled(userHandle)) {
                mLockSettingsInternal.refreshStrongAuthTimeout(getProfileParentId(userHandle));
            }
        }
    }

    /**
     * Return a single admin's strong auth unlock timeout or minimum value (strictest) of all
     * admins if who is null.
     * Returns 0 if not configured for the provided admin.
     */
    @Override
    public long getRequiredStrongAuthTimeout(ComponentName who, int userId, boolean parent) {
        if (!mHasFeature) {
            return DevicePolicyManager.DEFAULT_STRONG_AUTH_TIMEOUT_MS;
        }
        Preconditions.checkArgumentNonnegative(userId, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userId));

        if (!mLockPatternUtils.hasSecureLockScreen()) {
            // No strong auth timeout on devices not supporting the
            // {@link PackageManager#FEATURE_SECURE_LOCK_SCREEN} feature
            return 0;
        }
        synchronized (getLockObject()) {
            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userId, parent);
                return admin != null ? admin.strongAuthUnlockTimeout : 0;
            }

            // Return the strictest policy across all participating admins.
            List<ActiveAdmin> admins = getActiveAdminsForLockscreenPoliciesLocked(
                    getProfileParentUserIfRequested(userId, parent));

            long strongAuthUnlockTimeout = DevicePolicyManager.DEFAULT_STRONG_AUTH_TIMEOUT_MS;
            for (int i = 0; i < admins.size(); i++) {
                final long timeout = admins.get(i).strongAuthUnlockTimeout;
                if (timeout != 0) { // take only participating admins into account
                    strongAuthUnlockTimeout = Math.min(timeout, strongAuthUnlockTimeout);
                }
            }
            return Math.max(strongAuthUnlockTimeout, getMinimumStrongAuthTimeoutMs());
        }
    }

    private long getMinimumStrongAuthTimeoutMs() {
        if (!mInjector.isBuildDebuggable()) {
            return MINIMUM_STRONG_AUTH_TIMEOUT_MS;
        }
        // ideally the property was named persist.sys.min_strong_auth_timeout, but system property
        // name cannot be longer than 31 characters
        return Math.min(mInjector.systemPropertiesGetLong("persist.sys.min_str_auth_timeo",
                MINIMUM_STRONG_AUTH_TIMEOUT_MS),
                MINIMUM_STRONG_AUTH_TIMEOUT_MS);
    }

    @Override
    public void lockNow(int flags, boolean parent) {
        final CallerIdentity caller = getCallerIdentity();

        final int callingUserId = caller.getUserId();
        ComponentName adminComponent = null;
        synchronized (getLockObject()) {
            // Make sure the caller has any active admin with the right policy or
            // the required permission.
            final ActiveAdmin admin = getActiveAdminOrCheckPermissionForCallerLocked(
                    null,
                    DeviceAdminInfo.USES_POLICY_FORCE_LOCK,
                    parent,
                    android.Manifest.permission.LOCK_DEVICE);
            checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_LOCK_NOW);
            final long ident = mInjector.binderClearCallingIdentity();
            try {
                adminComponent = admin == null ? null : admin.info.getComponent();
                if (adminComponent != null) {
                    // For Profile Owners only, callers with only permission not allowed.
                    if ((flags & DevicePolicyManager.FLAG_EVICT_CREDENTIAL_ENCRYPTION_KEY) != 0) {
                        // Evict key
                        Preconditions.checkCallingUser(isManagedProfile(callingUserId));
                        Preconditions.checkArgument(!parent,
                                "Cannot set FLAG_EVICT_CREDENTIAL_ENCRYPTION_KEY for the parent");
                        if (!isProfileOwner(adminComponent, callingUserId)) {
                            throw new SecurityException("Only profile owner admins can set "
                                    + "FLAG_EVICT_CREDENTIAL_ENCRYPTION_KEY");
                        }
                        if (!mInjector.storageManagerIsFileBasedEncryptionEnabled()) {
                            throw new UnsupportedOperationException(
                                    "FLAG_EVICT_CREDENTIAL_ENCRYPTION_KEY only applies to FBE"
                                        + " devices");
                        }
                        mUserManager.evictCredentialEncryptionKey(callingUserId);
                    }
                }

                // Lock all users unless this is a managed profile with a separate challenge
                final int userToLock = (parent || !isSeparateProfileChallengeEnabled(callingUserId)
                        ? UserHandle.USER_ALL : callingUserId);
                mLockPatternUtils.requireStrongAuth(
                        STRONG_AUTH_REQUIRED_AFTER_DPM_LOCK_NOW, userToLock);

                // Require authentication for the device or profile
                if (userToLock == UserHandle.USER_ALL) {
                    if (mIsAutomotive) {
                        if (VERBOSE_LOG) {
                            Slogf.v(LOG_TAG, "lockNow(): not powering off display on automotive"
                                    + " build");
                        }
                    } else {
                        // Power off the display
                        mInjector.powerManagerGoToSleep(SystemClock.uptimeMillis(),
                                PowerManager.GO_TO_SLEEP_REASON_DEVICE_ADMIN, 0);
                    }
                    mInjector.getIWindowManager().lockNow(null);
                } else {
                    mInjector.getTrustManager().setDeviceLockedForUser(userToLock, true);
                }

                if (SecurityLog.isLoggingEnabled() && adminComponent != null) {
                    final int affectedUserId =
                            parent ? getProfileParentId(callingUserId) : callingUserId;
                    SecurityLog.writeEvent(SecurityLog.TAG_REMOTE_LOCK,
                            adminComponent.getPackageName(), callingUserId, affectedUserId);
                }
            } catch (RemoteException e) {
            } finally {
                mInjector.binderRestoreCallingIdentity(ident);
            }
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.LOCK_NOW)
                .setAdmin(adminComponent)
                .setInt(flags)
                .write();
    }

    @Override
    public void enforceCanManageCaCerts(ComponentName who, String callerPackage) {
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        Preconditions.checkCallAuthorization(canManageCaCerts(caller));
    }

    private boolean canManageCaCerts(CallerIdentity caller) {
        return (caller.hasAdminComponent() && (isDefaultDeviceOwner(caller)
                || isProfileOwner(caller)))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_CERT_INSTALL))
                || hasCallingOrSelfPermission(MANAGE_CA_CERTIFICATES);
    }

    @Override
    public boolean approveCaCert(String alias, int userId, boolean approval) {
        Preconditions.checkCallAuthorization(canManageUsers(getCallerIdentity()));

        synchronized (getLockObject()) {
            Set<String> certs = getUserData(userId).mAcceptedCaCertificates;
            boolean changed = (approval ? certs.add(alias) : certs.remove(alias));
            if (!changed) {
                return false;
            }
            saveSettingsLocked(userId);
        }
        mCertificateMonitor.onCertificateApprovalsChanged(userId);
        return true;
    }

    @Override
    public boolean isCaCertApproved(String alias, int userId) {
        Preconditions.checkCallAuthorization(canManageUsers(getCallerIdentity()));

        synchronized (getLockObject()) {
            return getUserData(userId).mAcceptedCaCertificates.contains(alias);
        }
    }

    private Set<Integer> removeCaApprovalsIfNeeded(int userId) {
        final ArraySet<Integer> affectedUserIds = new ArraySet<>();
        for (UserInfo userInfo : mUserManager.getProfiles(userId)) {
            boolean isSecure = mLockPatternUtils.isSecure(userInfo.id);
            if (userInfo.isManagedProfile()){
                isSecure |= mLockPatternUtils.isSecure(getProfileParentId(userInfo.id));
            }
            if (!isSecure) {
                synchronized (getLockObject()) {
                    getUserData(userInfo.id).mAcceptedCaCertificates.clear();
                    affectedUserIds.add(userInfo.id);
                }
                mCertificateMonitor.onCertificateApprovalsChanged(userId);
            }
        }
        return affectedUserIds;
    }

    @Override
    public boolean installCaCert(ComponentName admin, String callerPackage, byte[] certBuffer) {
        if (!mHasFeature) {
            return false;
        }
        final CallerIdentity caller = getCallerIdentity(admin, callerPackage);
        Preconditions.checkCallAuthorization(canManageCaCerts(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_INSTALL_CA_CERT);

        final String alias = mInjector.binderWithCleanCallingIdentity(() -> {
            String installedAlias = mCertificateMonitor.installCaCert(
                    caller.getUserHandle(), certBuffer);
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.INSTALL_CA_CERT)
                    .setAdmin(caller.getPackageName())
                    .setBoolean(/* isDelegate */ admin == null)
                    .write();
            return installedAlias;
        });

        if (alias == null) {
            Slogf.w(LOG_TAG, "Problem installing cert");
            return false;
        }

        synchronized (getLockObject()) {
            getUserData(caller.getUserId()).mOwnerInstalledCaCerts.add(alias);
            saveSettingsLocked(caller.getUserId());
        }
        return true;
    }

    @Override
    public void uninstallCaCerts(ComponentName admin, String callerPackage, String[] aliases) {
        if (!mHasFeature) {
            return;
        }
        final CallerIdentity caller = getCallerIdentity(admin, callerPackage);
        Preconditions.checkCallAuthorization(canManageCaCerts(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_UNINSTALL_CA_CERT);

        mInjector.binderWithCleanCallingIdentity(() -> {
            mCertificateMonitor.uninstallCaCerts(caller.getUserHandle(), aliases);
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.UNINSTALL_CA_CERTS)
                    .setAdmin(caller.getPackageName())
                    .setBoolean(/* isDelegate */ admin == null)
                    .write();
        });

        synchronized (getLockObject()) {
            if (getUserData(caller.getUserId()).mOwnerInstalledCaCerts.removeAll(
                    Arrays.asList(aliases))) {
                saveSettingsLocked(caller.getUserId());
            }
        }
    }

    @Override
    public boolean installKeyPair(ComponentName who, String callerPackage, byte[] privKey,
            byte[] cert, byte[] chain, String alias, boolean requestAccess,
            boolean isUserSelectable) {
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        final boolean isCallerDelegate = isCallerDelegate(caller, DELEGATION_CERT_INSTALL);
        final boolean isCredentialManagementApp = isCredentialManagementApp(caller);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)))
                || (caller.hasPackage() && (isCallerDelegate || isCredentialManagementApp)));
        if (isCredentialManagementApp) {
            Preconditions.checkCallAuthorization(!isUserSelectable, "The credential "
                    + "management app is not allowed to install a user selectable key pair");
            Preconditions.checkCallAuthorization(
                    isAliasInCredentialManagementAppPolicy(caller, alias),
                    CREDENTIAL_MANAGEMENT_APP_INVALID_ALIAS_MSG);
        }
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_INSTALL_KEY_PAIR);

        final long id = mInjector.binderClearCallingIdentity();
        try {
            final KeyChainConnection keyChainConnection =
                    KeyChain.bindAsUser(mContext, caller.getUserHandle());
            try {
                IKeyChainService keyChain = keyChainConnection.getService();
                if (!keyChain.installKeyPair(privKey, cert, chain, alias, KeyStore.UID_SELF)) {
                    logInstallKeyPairFailure(caller, isCredentialManagementApp);
                    return false;
                }
                if (requestAccess) {
                    keyChain.setGrant(caller.getUid(), alias, true);
                }
                keyChain.setUserSelectable(alias, isUserSelectable);
                DevicePolicyEventLogger
                        .createEvent(DevicePolicyEnums.INSTALL_KEY_PAIR)
                        .setAdmin(caller.getPackageName())
                        .setBoolean(/* isDelegate */ isCallerDelegate)
                        .setStrings(isCredentialManagementApp
                                ? CREDENTIAL_MANAGEMENT_APP : NOT_CREDENTIAL_MANAGEMENT_APP)
                        .write();
                return true;
            } catch (RemoteException e) {
                Slogf.e(LOG_TAG, "Installing certificate", e);
            } finally {
                keyChainConnection.close();
            }
        } catch (InterruptedException e) {
            Slogf.w(LOG_TAG, "Interrupted while installing certificate", e);
            Thread.currentThread().interrupt();
        } finally {
            mInjector.binderRestoreCallingIdentity(id);
        }
        logInstallKeyPairFailure(caller, isCredentialManagementApp);
        return false;
    }

    private void logInstallKeyPairFailure(CallerIdentity caller,
            boolean isCredentialManagementApp) {
        if (!isCredentialManagementApp) {
            return;
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.CREDENTIAL_MANAGEMENT_APP_INSTALL_KEY_PAIR_FAILED)
                .setStrings(caller.getPackageName())
                .write();
    }

    @Override
    public boolean removeKeyPair(ComponentName who, String callerPackage, String alias) {
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        final boolean isCallerDelegate = isCallerDelegate(caller, DELEGATION_CERT_INSTALL);
        final boolean isCredentialManagementApp = isCredentialManagementApp(caller);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)))
                || (caller.hasPackage() && (isCallerDelegate || isCredentialManagementApp)));
        if (isCredentialManagementApp) {
            Preconditions.checkCallAuthorization(
                    isAliasInCredentialManagementAppPolicy(caller, alias),
                    CREDENTIAL_MANAGEMENT_APP_INVALID_ALIAS_MSG);
        }
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_REMOVE_KEY_PAIR);

        final long id = Binder.clearCallingIdentity();
        try {
            final KeyChainConnection keyChainConnection =
                    KeyChain.bindAsUser(mContext, caller.getUserHandle());
            try {
                IKeyChainService keyChain = keyChainConnection.getService();
                DevicePolicyEventLogger
                        .createEvent(DevicePolicyEnums.REMOVE_KEY_PAIR)
                        .setAdmin(caller.getPackageName())
                        .setBoolean(/* isDelegate */ isCallerDelegate)
                        .setStrings(isCredentialManagementApp
                                ? CREDENTIAL_MANAGEMENT_APP : NOT_CREDENTIAL_MANAGEMENT_APP)
                        .write();
                return keyChain.removeKeyPair(alias);
            } catch (RemoteException e) {
                Slogf.e(LOG_TAG, "Removing keypair", e);
            } finally {
                keyChainConnection.close();
            }
        } catch (InterruptedException e) {
            Slogf.w(LOG_TAG, "Interrupted while removing keypair", e);
            Thread.currentThread().interrupt();
        } finally {
            Binder.restoreCallingIdentity(id);
        }
        return false;
    }

    @Override
    public boolean hasKeyPair(String callerPackage, String alias) {
        final CallerIdentity caller = getCallerIdentity(callerPackage);
        final boolean isCredentialManagementApp = isCredentialManagementApp(caller);
        Preconditions.checkCallAuthorization(canInstallCertificates(caller)
                || isCredentialManagementApp);
        if (isCredentialManagementApp) {
            Preconditions.checkCallAuthorization(
                    isAliasInCredentialManagementAppPolicy(caller, alias),
                    CREDENTIAL_MANAGEMENT_APP_INVALID_ALIAS_MSG);
        }

        return mInjector.binderWithCleanCallingIdentity(() -> {
            try (KeyChainConnection keyChainConnection =
                         KeyChain.bindAsUser(mContext, caller.getUserHandle())) {
                return keyChainConnection.getService().containsKeyPair(alias);
            } catch (RemoteException e) {
                Slogf.e(LOG_TAG, "Querying keypair", e);
            } catch (InterruptedException e) {
                Slogf.w(LOG_TAG, "Interrupted while querying keypair", e);
                Thread.currentThread().interrupt();
            }
            return false;
        });
    }

    private boolean canInstallCertificates(CallerIdentity caller) {
        return isProfileOwner(caller) || isDefaultDeviceOwner(caller)
                || isCallerDelegate(caller, DELEGATION_CERT_INSTALL);
    }

    private boolean canChooseCertificates(CallerIdentity caller) {
        return isProfileOwner(caller) || isDefaultDeviceOwner(caller)
                || isCallerDelegate(caller, DELEGATION_CERT_SELECTION);
    }

    @Override
    public boolean setKeyGrantToWifiAuth(String callerPackage, String alias, boolean hasGrant) {
        Preconditions.checkStringNotEmpty(alias, "Alias to grant cannot be empty");

        final CallerIdentity caller = getCallerIdentity(callerPackage);
        Preconditions.checkCallAuthorization(canChooseCertificates(caller));
        try {
            return setKeyChainGrantInternal(
                    alias, hasGrant, Process.WIFI_UID, caller.getUserHandle());
        } catch (IllegalArgumentException e) {
            if (mInjector.isChangeEnabled(THROW_EXCEPTION_WHEN_KEY_MISSING, caller.getPackageName(),
                    caller.getUserId())) {
                throw e;
            }
            return false;
        }
    }

    @Override
    public boolean isKeyPairGrantedToWifiAuth(String callerPackage, String alias) {
        Preconditions.checkStringNotEmpty(alias, "Alias to check cannot be empty");

        final CallerIdentity caller = getCallerIdentity(callerPackage);
        Preconditions.checkCallAuthorization(canChooseCertificates(caller));

        return mInjector.binderWithCleanCallingIdentity(() -> {
            try (KeyChainConnection keyChainConnection =
                         KeyChain.bindAsUser(mContext, caller.getUserHandle())) {
                final List<String> result = new ArrayList<>();
                final int[] granteeUids = keyChainConnection.getService().getGrants(alias);

                for (final int uid : granteeUids) {
                    if (uid == Process.WIFI_UID) {
                        return true;
                    }
                }
                return false;
            } catch (RemoteException e) {
                Slogf.e(LOG_TAG, "Querying grant to wifi auth.", e);
                return false;
            }
        });
    }

    @Override
    public boolean setKeyGrantForApp(ComponentName who, String callerPackage, String alias,
            String packageName, boolean hasGrant) {
        Preconditions.checkStringNotEmpty(alias, "Alias to grant cannot be empty");
        Preconditions.checkStringNotEmpty(packageName, "Package to grant to cannot be empty");

        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_CERT_SELECTION)));

        final int granteeUid;
        try {
            ApplicationInfo ai = mInjector.getIPackageManager().getApplicationInfo(
                    packageName, 0, caller.getUserId());
            Preconditions.checkArgument(ai != null,
                    "Provided package %s is not installed", packageName);
            granteeUid = ai.uid;
        } catch (RemoteException e) {
            throw new IllegalStateException("Failure getting grantee uid", e);
        }
        try {
            return setKeyChainGrantInternal(alias, hasGrant, granteeUid, caller.getUserHandle());
        } catch (IllegalArgumentException e) {
            if (mInjector.isChangeEnabled(THROW_EXCEPTION_WHEN_KEY_MISSING, callerPackage,
                    caller.getUserId())) {
                throw e;
            }
            return false;
        }
    }

    private boolean setKeyChainGrantInternal(String alias, boolean hasGrant, int granteeUid,
            UserHandle userHandle) {
        final long id = mInjector.binderClearCallingIdentity();
        try {
            try (KeyChainConnection keyChainConnection =
                         KeyChain.bindAsUser(mContext, userHandle)) {
                IKeyChainService keyChain = keyChainConnection.getService();
                return keyChain.setGrant(granteeUid, alias, hasGrant);
            } catch (RemoteException e) {
                Slogf.e(LOG_TAG, "Setting grant for package.", e);
                return false;
            }
        } catch (InterruptedException e) {
            Slogf.w(LOG_TAG, "Interrupted while setting key grant", e);
            Thread.currentThread().interrupt();
        } finally {
            mInjector.binderRestoreCallingIdentity(id);
        }
        return false;
    }

    @Override
    public ParcelableGranteeMap getKeyPairGrants(String callerPackage, String alias) {
        final CallerIdentity caller = getCallerIdentity(callerPackage);
        Preconditions.checkCallAuthorization(canChooseCertificates(caller));

        final ArrayMap<Integer, Set<String>> result = new ArrayMap<>();
        mInjector.binderWithCleanCallingIdentity(() -> {
            try (KeyChainConnection keyChainConnection =
                         KeyChain.bindAsUser(mContext, caller.getUserHandle())) {
                final int[] granteeUids = keyChainConnection.getService().getGrants(alias);
                final PackageManager pm = mInjector.getPackageManager(caller.getUserId());

                for (final int uid : granteeUids) {
                    final String[] packages = pm.getPackagesForUid(uid);
                    if (packages == null) {
                        Slogf.wtf(LOG_TAG, "No packages found for uid " + uid);
                        continue;
                    }
                    result.put(uid, new ArraySet<String>(packages));
                }
            } catch (RemoteException e) {
                Slogf.e(LOG_TAG, "Querying keypair grants", e);
            } catch (InterruptedException e) {
                Slogf.w(LOG_TAG, "Interrupted while querying keypair grants", e);
                Thread.currentThread().interrupt();
            }
        });
        return new ParcelableGranteeMap(result);
    }

    @VisibleForTesting
    public static int[] translateIdAttestationFlags(
            int idAttestationFlags) {
        Map<Integer, Integer> idTypeToAttestationFlag = new HashMap();
        idTypeToAttestationFlag.put(ID_TYPE_SERIAL, AttestationUtils.ID_TYPE_SERIAL);
        idTypeToAttestationFlag.put(ID_TYPE_IMEI, AttestationUtils.ID_TYPE_IMEI);
        idTypeToAttestationFlag.put(ID_TYPE_MEID, AttestationUtils.ID_TYPE_MEID);
        idTypeToAttestationFlag.put(
                ID_TYPE_INDIVIDUAL_ATTESTATION, USE_INDIVIDUAL_ATTESTATION);

        int numFlagsSet = Integer.bitCount(idAttestationFlags);
        // No flags are set - return null to indicate no device ID attestation information should
        // be included in the attestation record.
        if (numFlagsSet == 0) {
            return null;
        }

        // If the ID_TYPE_BASE_INFO is set, make sure that a non-null array is returned, even if
        // no other flag is set. That will lead to inclusion of general device make data in the
        // attestation record, but no specific device identifiers.
        if ((idAttestationFlags & ID_TYPE_BASE_INFO) != 0) {
            numFlagsSet -= 1;
            idAttestationFlags = idAttestationFlags & (~ID_TYPE_BASE_INFO);
        }

        int[] attestationUtilsFlags = new int[numFlagsSet];
        int i = 0;
        for (Integer idType: idTypeToAttestationFlag.keySet()) {
            if ((idType & idAttestationFlags) != 0) {
                attestationUtilsFlags[i++] = idTypeToAttestationFlag.get(idType);
            }
        }

        return attestationUtilsFlags;
    }

    @Override
    public boolean generateKeyPair(ComponentName who, String callerPackage, String algorithm,
            ParcelableKeyGenParameterSpec parcelableKeySpec, int idAttestationFlags,
            KeymasterCertificateChain attestationChain) {
        // Get attestation flags, if any.
        final int[] attestationUtilsFlags = translateIdAttestationFlags(idAttestationFlags);
        final boolean deviceIdAttestationRequired = attestationUtilsFlags != null;
        KeyGenParameterSpec keySpec = parcelableKeySpec.getSpec();
        final String alias = keySpec.getKeystoreAlias();
        Preconditions.checkStringNotEmpty(alias, "Empty alias provided");
        Preconditions.checkArgument(
                !deviceIdAttestationRequired || keySpec.getAttestationChallenge() != null,
                "Requested Device ID attestation but challenge is empty");

        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        final boolean isCallerDelegate = isCallerDelegate(caller, DELEGATION_CERT_INSTALL);
        final boolean isCredentialManagementApp = isCredentialManagementApp(caller);
        if (deviceIdAttestationRequired && attestationUtilsFlags.length > 0) {
            Preconditions.checkCallAuthorization(hasDeviceIdAccessUnchecked(
                    caller.getPackageName(), caller.getUid()));
            enforceIndividualAttestationSupportedIfRequested(attestationUtilsFlags);
        } else {
            Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                    && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)))
                    || (caller.hasPackage() && (isCallerDelegate || isCredentialManagementApp)));
            if (isCredentialManagementApp) {
                Preconditions.checkCallAuthorization(
                        isAliasInCredentialManagementAppPolicy(caller, alias),
                        CREDENTIAL_MANAGEMENT_APP_INVALID_ALIAS_MSG);
            }
        }

        if (TextUtils.isEmpty(alias)) {
            throw new IllegalArgumentException("Empty alias provided.");
        }
        // As the caller will be granted access to the key, ensure no UID was specified, as
        // it will not have the desired effect.
        if (keySpec.getUid() != KeyStore.UID_SELF) {
            Slogf.e(LOG_TAG, "Only the caller can be granted access to the generated keypair.");
            logGenerateKeyPairFailure(caller, isCredentialManagementApp);
            return false;
        }

        if (deviceIdAttestationRequired) {
            if (keySpec.getAttestationChallenge() == null) {
                throw new IllegalArgumentException(
                        "Requested Device ID attestation but challenge is empty.");
            }
            KeyGenParameterSpec.Builder specBuilder = new KeyGenParameterSpec.Builder(keySpec);
            specBuilder.setAttestationIds(attestationUtilsFlags);
            specBuilder.setDevicePropertiesAttestationIncluded(true);
            keySpec = specBuilder.build();
        }

        final long id = mInjector.binderClearCallingIdentity();
        try {
            try (KeyChainConnection keyChainConnection =
                    KeyChain.bindAsUser(mContext, caller.getUserHandle())) {
                IKeyChainService keyChain = keyChainConnection.getService();

                final int generationResult = keyChain.generateKeyPair(algorithm,
                        new ParcelableKeyGenParameterSpec(keySpec));
                if (generationResult != KeyChain.KEY_GEN_SUCCESS) {
                    Slogf.e(LOG_TAG, "KeyChain failed to generate a keypair, error %d.",
                            generationResult);
                    logGenerateKeyPairFailure(caller, isCredentialManagementApp);
                    switch (generationResult) {
                        case KeyChain.KEY_GEN_STRONGBOX_UNAVAILABLE:
                            throw new ServiceSpecificException(
                                    DevicePolicyManager.KEY_GEN_STRONGBOX_UNAVAILABLE,
                                    String.format("KeyChain error: %d", generationResult));
                        case KeyChain.KEY_ATTESTATION_CANNOT_ATTEST_IDS:
                            throw new UnsupportedOperationException(
                                "Device does not support Device ID attestation.");
                        default:
                            return false;
                    }
                }

                // Set a grant for the caller here so that when the client calls
                // requestPrivateKey, it will be able to get the key from Keystore.
                // Note the use of the calling  UID, since the request for the private
                // key will come from the client's process, so the grant has to be for
                // that UID.
                keyChain.setGrant(caller.getUid(), alias, true);

                try {
                    final List<byte[]> encodedCerts = new ArrayList();
                    final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                    final byte[] certChainBytes = keyChain.getCaCertificates(alias);
                    encodedCerts.add(keyChain.getCertificate(alias));
                    if (certChainBytes != null) {
                        final Collection<X509Certificate> certs =
                                (Collection<X509Certificate>) certFactory.generateCertificates(
                                    new ByteArrayInputStream(certChainBytes));
                        for (X509Certificate cert : certs) {
                            encodedCerts.add(cert.getEncoded());
                        }
                    }

                    attestationChain.shallowCopyFrom(new KeymasterCertificateChain(encodedCerts));
                } catch (CertificateException e) {
                    logGenerateKeyPairFailure(caller, isCredentialManagementApp);
                    Slogf.e(LOG_TAG, "While retrieving certificate chain.", e);
                    return false;
                }

                DevicePolicyEventLogger
                        .createEvent(DevicePolicyEnums.GENERATE_KEY_PAIR)
                        .setAdmin(caller.getPackageName())
                        .setBoolean(/* isDelegate */ isCallerDelegate)
                        .setInt(idAttestationFlags)
                        .setStrings(algorithm, isCredentialManagementApp
                                ? CREDENTIAL_MANAGEMENT_APP : NOT_CREDENTIAL_MANAGEMENT_APP)
                        .write();
                return true;
            }
        } catch (RemoteException e) {
            Slogf.e(LOG_TAG, "KeyChain error while generating a keypair", e);
        } catch (InterruptedException e) {
            Slogf.w(LOG_TAG, "Interrupted while generating keypair", e);
            Thread.currentThread().interrupt();
        } finally {
            mInjector.binderRestoreCallingIdentity(id);
        }
        logGenerateKeyPairFailure(caller, isCredentialManagementApp);
        return false;
    }

    private void logGenerateKeyPairFailure(CallerIdentity caller,
            boolean isCredentialManagementApp) {
        if (!isCredentialManagementApp) {
            return;
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.CREDENTIAL_MANAGEMENT_APP_GENERATE_KEY_PAIR_FAILED)
                .setStrings(caller.getPackageName())
                .write();
    }

    private void enforceIndividualAttestationSupportedIfRequested(int[] attestationUtilsFlags) {
        for (int attestationFlag : attestationUtilsFlags) {
            if (attestationFlag == USE_INDIVIDUAL_ATTESTATION
                    && !mInjector.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_DEVICE_UNIQUE_ATTESTATION)) {
                throw new UnsupportedOperationException("Device Individual attestation is not "
                        + "supported on this device.");
            }
        }
    }

    @Override
    public boolean setKeyPairCertificate(ComponentName who, String callerPackage, String alias,
            byte[] cert, byte[] chain, boolean isUserSelectable) {
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        final boolean isCallerDelegate = isCallerDelegate(caller, DELEGATION_CERT_INSTALL);
        final boolean isCredentialManagementApp = isCredentialManagementApp(caller);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)))
                || (caller.hasPackage() && (isCallerDelegate || isCredentialManagementApp)));
        if (isCredentialManagementApp) {
            Preconditions.checkCallAuthorization(
                    isAliasInCredentialManagementAppPolicy(caller, alias),
                    CREDENTIAL_MANAGEMENT_APP_INVALID_ALIAS_MSG);
        }

        final long id = mInjector.binderClearCallingIdentity();
        try (final KeyChainConnection keyChainConnection =
                KeyChain.bindAsUser(mContext, caller.getUserHandle())) {
            IKeyChainService keyChain = keyChainConnection.getService();
            if (!keyChain.setKeyPairCertificate(alias, cert, chain)) {
                return false;
            }
            keyChain.setUserSelectable(alias, isUserSelectable);
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.SET_KEY_PAIR_CERTIFICATE)
                    .setAdmin(caller.getPackageName())
                    .setBoolean(/* isDelegate */ isCallerDelegate)
                    .setStrings(isCredentialManagementApp
                            ? CREDENTIAL_MANAGEMENT_APP : NOT_CREDENTIAL_MANAGEMENT_APP)
                    .write();
            return true;
        } catch (InterruptedException e) {
            Slogf.w(LOG_TAG, "Interrupted while setting keypair certificate", e);
            Thread.currentThread().interrupt();
        } catch (RemoteException e) {
            Slogf.e(LOG_TAG, "Failed setting keypair certificate", e);
        } finally {
            mInjector.binderRestoreCallingIdentity(id);
        }
        return false;
    }

    @Override
    public void choosePrivateKeyAlias(final int uid, final Uri uri, final String alias,
            final IBinder response) {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(isSystemUid(caller),
                String.format(NOT_SYSTEM_CALLER_MSG, "choose private key alias"));

        // If there is a profile owner, redirect to that; otherwise query the device owner.
        ComponentName aliasChooser = getProfileOwnerAsUser(caller.getUserId());
        if (aliasChooser == null && caller.getUserHandle().isSystem()) {
            synchronized (getLockObject()) {
                final ActiveAdmin deviceOwnerAdmin = getDeviceOwnerAdminLocked();
                if (deviceOwnerAdmin != null) {
                    aliasChooser = deviceOwnerAdmin.info.getComponent();
                }
            }
        }
        if (aliasChooser == null) {
            sendPrivateKeyAliasResponse(null, response);
            return;
        }

        Intent intent = new Intent(DeviceAdminReceiver.ACTION_CHOOSE_PRIVATE_KEY_ALIAS);
        intent.putExtra(DeviceAdminReceiver.EXTRA_CHOOSE_PRIVATE_KEY_SENDER_UID, uid);
        intent.putExtra(DeviceAdminReceiver.EXTRA_CHOOSE_PRIVATE_KEY_URI, uri);
        intent.putExtra(DeviceAdminReceiver.EXTRA_CHOOSE_PRIVATE_KEY_ALIAS, alias);
        intent.putExtra(DeviceAdminReceiver.EXTRA_CHOOSE_PRIVATE_KEY_RESPONSE, response);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        final ComponentName delegateReceiver;
        delegateReceiver = resolveDelegateReceiver(DELEGATION_CERT_SELECTION,
                DeviceAdminReceiver.ACTION_CHOOSE_PRIVATE_KEY_ALIAS, caller.getUserId());

        final boolean isDelegate;
        if (delegateReceiver != null) {
            intent.setComponent(delegateReceiver);
            isDelegate = true;
        } else {
            intent.setComponent(aliasChooser);
            isDelegate = false;
        }

        mInjector.binderWithCleanCallingIdentity(() -> {
            mContext.sendOrderedBroadcastAsUser(intent, caller.getUserHandle(), null,
                    new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String chosenAlias = getResultData();
                    sendPrivateKeyAliasResponse(chosenAlias, response);
                }
            }, null, Activity.RESULT_OK, null, null);
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.CHOOSE_PRIVATE_KEY_ALIAS)
                    .setAdmin(intent.getComponent())
                    .setBoolean(isDelegate)
                    .write();
        });
    }

    private void sendPrivateKeyAliasResponse(final String alias, final IBinder responseBinder) {
        final IKeyChainAliasCallback keyChainAliasResponse =
                IKeyChainAliasCallback.Stub.asInterface(responseBinder);
        // Send the response. It's OK to do this from the main thread because IKeyChainAliasCallback
        // is oneway, which means it won't block if the recipient lives in another process.
        try {
            keyChainAliasResponse.alias(alias);
        } catch (Exception e) {
            // Caller could throw RuntimeException or RemoteException back across processes. Catch
            // everything just to be sure.
            Slogf.e(LOG_TAG, "error while responding to callback", e);
        }
    }

    /**
     * Determine whether DPMS should check if a delegate package is already installed before
     * granting it new delegations via {@link #setDelegatedScopes}.
     */
    private static boolean shouldCheckIfDelegatePackageIsInstalled(String delegatePackage,
            int targetSdk, List<String> scopes) {
        // 1) Never skip is installed check from N.
        if (targetSdk >= Build.VERSION_CODES.N) {
            return true;
        }
        // 2) Skip if DELEGATION_CERT_INSTALL is the only scope being given.
        if (scopes.size() == 1 && scopes.get(0).equals(DELEGATION_CERT_INSTALL)) {
            return false;
        }
        // 3) Skip if all previously granted scopes are being cleared.
        if (scopes.isEmpty()) {
            return false;
        }
        // Otherwise it should check that delegatePackage is installed.
        return true;
    }

    /**
     * Set the scopes of a device owner or profile owner delegate.
     *
     * @param who the device owner or profile owner.
     * @param delegatePackage the name of the delegate package.
     * @param scopeList the list of delegation scopes to be given to the delegate package.
     */
    @Override
    public void setDelegatedScopes(ComponentName who, String delegatePackage,
            List<String> scopeList) throws SecurityException {
        Objects.requireNonNull(who, "ComponentName is null");
        Preconditions.checkStringNotEmpty(delegatePackage, "Delegate package is null or empty");
        Preconditions.checkCollectionElementsNotNull(scopeList, "Scopes");
        final CallerIdentity caller = getCallerIdentity(who);

        // Remove possible duplicates.
        final ArrayList<String> scopes = new ArrayList(new ArraySet(scopeList));
        // Ensure given scopes are valid.
        if (scopes.retainAll(Arrays.asList(DELEGATIONS))) {
            throw new IllegalArgumentException("Unexpected delegation scopes");
        }
        // Retrieve the user ID of the calling process.
        final int userId = caller.getUserId();
        // Ensure calling process is device/profile owner.
        if (!Collections.disjoint(scopes, DEVICE_OWNER_OR_MANAGED_PROFILE_OWNER_DELEGATIONS)) {
            Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                    || (isProfileOwner(caller) && isManagedProfile(caller.getUserId())));
        } else if (!Collections.disjoint(
                scopes, DEVICE_OWNER_OR_ORGANIZATION_OWNED_MANAGED_PROFILE_OWNER_DELEGATIONS)) {
            Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                    || isProfileOwnerOfOrganizationOwnedDevice(caller));
        } else {
            Preconditions.checkCallAuthorization(
                    isDefaultDeviceOwner(caller) || isProfileOwner(caller));
        }

        synchronized (getLockObject()) {
            // Ensure the delegate is installed (skip this for DELEGATION_CERT_INSTALL in pre-N).
            if (shouldCheckIfDelegatePackageIsInstalled(delegatePackage,
                        getTargetSdk(who.getPackageName(), userId), scopes)) {
                // Throw when the delegate package is not installed.
                if (!isPackageInstalledForUser(delegatePackage, userId)) {
                    throw new IllegalArgumentException("Package " + delegatePackage
                            + " is not installed on the current user");
                }
            }

            // Set the new delegate in user policies.
            final DevicePolicyData policy = getUserData(userId);
            List<String> exclusiveScopes = null;
            if (!scopes.isEmpty()) {
                policy.mDelegationMap.put(delegatePackage, new ArrayList<>(scopes));
                exclusiveScopes = new ArrayList<>(scopes);
                exclusiveScopes.retainAll(EXCLUSIVE_DELEGATIONS);
            } else {
                // Remove any delegation info if the given scopes list is empty.
                policy.mDelegationMap.remove(delegatePackage);
            }
            sendDelegationChangedBroadcast(delegatePackage, scopes, userId);

            // If set, remove exclusive scopes from all other delegates
            if (exclusiveScopes != null && !exclusiveScopes.isEmpty()) {
                for (int i = policy.mDelegationMap.size() - 1; i >= 0; --i) {
                    final String currentPackage = policy.mDelegationMap.keyAt(i);
                    final List<String> currentScopes = policy.mDelegationMap.valueAt(i);

                    if (!currentPackage.equals(delegatePackage)) {
                        // Iterate through all other delegates
                        if (currentScopes.removeAll(exclusiveScopes)) {
                            // And if this delegate had some exclusive scopes which are now moved
                            // to the new delegate, notify about its delegation changes.
                            if (currentScopes.isEmpty()) {
                                policy.mDelegationMap.removeAt(i);
                            }
                            sendDelegationChangedBroadcast(currentPackage,
                                    new ArrayList<>(currentScopes), userId);
                        }
                    }
                }
            }
            // Persist updates.
            saveSettingsLocked(userId);
        }
    }

    private void sendDelegationChangedBroadcast(String delegatePackage, ArrayList<String> scopes,
            int userId) {
        // Notify delegate package of updates.
        final Intent intent = new Intent(
                DevicePolicyManager.ACTION_APPLICATION_DELEGATION_SCOPES_CHANGED);
        // Only call receivers registered with Context#registerReceiver (don’t wake delegate).
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        // Limit components this intent resolves to to the delegate package.
        intent.setPackage(delegatePackage);
        // Include the list of delegated scopes as an extra.
        intent.putStringArrayListExtra(DevicePolicyManager.EXTRA_DELEGATION_SCOPES, scopes);
        // Send the broadcast.
        mContext.sendBroadcastAsUser(intent, UserHandle.of(userId));
    }

    /**
     * Get the delegation scopes given to a delegate package by a device owner or profile owner.
     *
     * A DO/PO can get the scopes of any package. A non DO/PO package can get its own scopes by
     * passing in {@code null} as the {@code who} parameter and its own name as the
     * {@code delegatepackage}.
     *
     * @param who the device owner or profile owner, or {@code null} if the caller is
     *            {@code delegatePackage}.
     * @param delegatePackage the name of the delegate package whose scopes are to be retrieved.
     * @return a list of the delegation scopes currently given to {@code delegatePackage}.
     */
    @Override
    @NonNull
    public List<String> getDelegatedScopes(ComponentName who,
            String delegatePackage) throws SecurityException {
        Objects.requireNonNull(delegatePackage, "Delegate package is null");
        final CallerIdentity caller = getCallerIdentity(who);

        // Ensure the caller may call this method:
        // * Either it's a profile owner / device owner, if componentName is provided
        // * Or it's an app querying its own delegation scopes
        if (caller.hasAdminComponent()) {
            Preconditions.checkCallAuthorization(
                    isProfileOwner(caller) || isDefaultDeviceOwner(caller));
        } else {
            Preconditions.checkCallAuthorization(isPackage(caller, delegatePackage),
                    String.format("Caller with uid %d is not %s", caller.getUid(),
                            delegatePackage));
        }
        synchronized (getLockObject()) {
            final DevicePolicyData policy = getUserData(caller.getUserId());
            // Retrieve the scopes assigned to delegatePackage, or null if no scope was given.
            final List<String> scopes = policy.mDelegationMap.get(delegatePackage);
            return scopes == null ? Collections.EMPTY_LIST : scopes;
        }
    }

    /**
     * Get a list of  packages that were given a specific delegation scopes by a device owner or
     * profile owner.
     *
     * @param who the device owner or profile owner.
     * @param scope the scope whose delegates are to be retrieved.
     * @return a list of the delegate packages currently given the {@code scope} delegation.
     */
    @NonNull
    public List<String> getDelegatePackages(ComponentName who, String scope)
            throws SecurityException {
        Objects.requireNonNull(who, "ComponentName is null");
        Objects.requireNonNull(scope, "Scope is null");
        if (!Arrays.asList(DELEGATIONS).contains(scope)) {
            throw new IllegalArgumentException("Unexpected delegation scope: " + scope);
        }

        // Retrieve the user ID of the calling process.
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));
        synchronized (getLockObject()) {
            return getDelegatePackagesInternalLocked(scope, caller.getUserId());
        }
    }

    private List<String> getDelegatePackagesInternalLocked(String scope, int userId) {
        final DevicePolicyData policy = getUserData(userId);

        // Create a list to hold the resulting delegate packages.
        final List<String> delegatePackagesWithScope = new ArrayList<>();
        // Add all delegations containing scope to the result list.
        for (int i = 0; i < policy.mDelegationMap.size(); i++) {
            if (policy.mDelegationMap.valueAt(i).contains(scope)) {
                delegatePackagesWithScope.add(policy.mDelegationMap.keyAt(i));
            }
        }
        return delegatePackagesWithScope;
    }

    /**
     * Return the ComponentName of the receiver that handles the given broadcast action, from
     * the app that holds the given delegation capability. If the app defines multiple receivers
     * with the same intent action filter, will return any one of them nondeterministically.
     *
     * @return ComponentName of the receiver or {@null} if none exists.
     */
    private ComponentName resolveDelegateReceiver(String scope, String action, int userId) {

        final List<String> delegates;
        synchronized (getLockObject()) {
            delegates = getDelegatePackagesInternalLocked(scope, userId);
        }
        if (delegates.size() == 0) {
            return null;
        } else if (delegates.size() > 1) {
            Slogf.wtf(LOG_TAG, "More than one delegate holds " + scope);
            return null;
        }
        final String pkg = delegates.get(0);
        Intent intent = new Intent(action);
        intent.setPackage(pkg);
        final List<ResolveInfo> receivers;
        try {
            receivers = mIPackageManager.queryIntentReceivers(
                    intent, null, 0, userId).getList();
        } catch (RemoteException e) {
            return null;
        }
        final int count = receivers.size();
        if (count >= 1) {
            if (count > 1) {
                Slogf.w(LOG_TAG, pkg + " defines more than one delegate receiver for " + action);
            }
            return receivers.get(0).activityInfo.getComponentName();
        } else {
            return null;
        }
    }

    /**
     * Check whether a caller application has been delegated a given scope via
     * {@link #setDelegatedScopes} to access privileged APIs on the behalf of a profile owner or
     * device owner.
     * <p>
     * This is done by checking that {@code callerPackage} was granted {@code scope} delegation and
     * then comparing the calling UID with the UID of {@code callerPackage} as reported by
     * {@link PackageManager#getPackageUidAsUser}.
     *
     * @param callerPackage the name of the package that is trying to invoke a function in the DPMS.
     * @param scope the delegation scope to be checked.
     * @return {@code true} if the calling process is a delegate of {@code scope}.
     */
    private boolean isCallerDelegate(String callerPackage, int callerUid, String scope) {
        Objects.requireNonNull(callerPackage, "callerPackage is null");
        if (!Arrays.asList(DELEGATIONS).contains(scope)) {
            throw new IllegalArgumentException("Unexpected delegation scope: " + scope);
        }

        // Retrieve the UID and user ID of the calling process.
        final int userId = UserHandle.getUserId(callerUid);
        synchronized (getLockObject()) {
            // Retrieve user policy data.
            final DevicePolicyData policy = getUserData(userId);
            // Retrieve the list of delegation scopes granted to callerPackage.
            final List<String> scopes = policy.mDelegationMap.get(callerPackage);
            // Check callingUid only if callerPackage has the required scope delegation.
            if (scopes != null && scopes.contains(scope)) {
                // Return true if the caller is actually callerPackage.
                return isCallingFromPackage(callerPackage, callerUid);
            }
            return false;
        }
    }

    /**
     * Check whether a caller application has been delegated a given scope via
     * {@link #setDelegatedScopes} to access privileged APIs on the behalf of a profile owner or
     * device owner.
     * <p>
     * This is done by checking that the calling package was granted {@code scope} delegation and
     * then comparing the calling UID with the UID of the calling package as reported by
     * {@link PackageManager#getPackageUidAsUser}.
     *
     * @param caller the calling identity
     * @param scope the delegation scope to be checked.
     * @return {@code true} if the calling process is a delegate of {@code scope}.
     */
    private boolean isCallerDelegate(CallerIdentity caller, String scope) {
        Objects.requireNonNull(caller.getPackageName(), "callerPackage is null");
        Preconditions.checkArgument(Arrays.asList(DELEGATIONS).contains(scope),
                "Unexpected delegation scope: %s", scope);

        synchronized (getLockObject()) {
            // Retrieve user policy data.
            final DevicePolicyData policy = getUserData(caller.getUserId());
            // Retrieve the list of delegation scopes granted to callerPackage.
            final List<String> scopes = policy.mDelegationMap.get(caller.getPackageName());
            // Check callingUid only if callerPackage has the required scope delegation.
            return scopes != null && scopes.contains(scope);
        }
    }

    /**
     * Helper function to preserve delegation behavior pre-O when using the deprecated functions
     * {@code #setCertInstallerPackage} and {@code #setApplicationRestrictionsManagingPackage}.
     */
    private void setDelegatedScopePreO(ComponentName who,
            String delegatePackage, String scope) {
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        // Ensure calling process is device/profile owner.
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));

        synchronized (getLockObject()) {
            final DevicePolicyData policy = getUserData(caller.getUserId());

            if (delegatePackage != null) {
                // Set package as a delegate for scope if it is not already one.
                List<String> scopes = policy.mDelegationMap.get(delegatePackage);
                if (scopes == null) {
                    scopes = new ArrayList<>();
                }
                if (!scopes.contains(scope)) {
                    scopes.add(scope);
                    setDelegatedScopes(who, delegatePackage, scopes);
                }
            }

            // Clear any existing scope delegates.
            for (int i = 0; i < policy.mDelegationMap.size(); i++) {
                final String currentPackage = policy.mDelegationMap.keyAt(i);
                final List<String> currentScopes = policy.mDelegationMap.valueAt(i);

                if (!currentPackage.equals(delegatePackage) && currentScopes.contains(scope)) {
                    final List<String> newScopes = new ArrayList(currentScopes);
                    newScopes.remove(scope);
                    setDelegatedScopes(who, currentPackage, newScopes);
                }
            }
        }
    }

    /**
     * Check whether a caller application is the credential management app, which can access
     * privileged APIs.
     * <p>
     * This is done by checking that the calling package is authorized to perform the app operation
     * {@link android.app.AppOpsManager#OP_MANAGE_CREDENTIALS}.
     *
     * @param caller the calling identity
     * @return {@code true} if the calling process is the credential management app.
     */
    private boolean isCredentialManagementApp(CallerIdentity caller) {
        return mInjector.binderWithCleanCallingIdentity(() -> {
            AppOpsManager appOpsManager = mInjector.getAppOpsManager();
            if (appOpsManager == null) return false;
            return appOpsManager.noteOpNoThrow(AppOpsManager.OP_MANAGE_CREDENTIALS, caller.getUid(),
                    caller.getPackageName(), null, null) == AppOpsManager.MODE_ALLOWED;
        });
    }

    /**
     * If the caller is the credential management app, the alias provided must be contained
     * in the aliases specified in the credential management app's authentication policy.
     */
    private boolean isAliasInCredentialManagementAppPolicy(CallerIdentity caller, String alias) {
        return mInjector.binderWithCleanCallingIdentity(() -> {
            try (KeyChainConnection connection = KeyChain.bindAsUser(mContext,
                    caller.getUserHandle())) {
                // The policy will be null if there is no credential management app
                AppUriAuthenticationPolicy policy =
                        connection.getService().getCredentialManagementAppPolicy();
                return policy != null && !policy.getAppAndUriMappings().isEmpty()
                        && containsAlias(policy, alias);
            } catch (RemoteException | InterruptedException e) {
                return false;
            }
        });
    }

    private static boolean containsAlias(AppUriAuthenticationPolicy policy, String alias) {
        for (Map.Entry<String, Map<Uri, String>> appsToUris :
                policy.getAppAndUriMappings().entrySet()) {
            for (Map.Entry<Uri, String> urisToAliases : appsToUris.getValue().entrySet()) {
                if (urisToAliases.getValue().equals(alias)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void setCertInstallerPackage(ComponentName who, String installerPackage)
            throws SecurityException {
        setDelegatedScopePreO(who, installerPackage, DELEGATION_CERT_INSTALL);
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_CERT_INSTALLER_PACKAGE)
                .setAdmin(who)
                .setStrings(installerPackage)
                .write();
    }

    @Override
    public String getCertInstallerPackage(ComponentName who) throws SecurityException {
        final List<String> delegatePackages = getDelegatePackages(who, DELEGATION_CERT_INSTALL);
        return delegatePackages.size() > 0 ? delegatePackages.get(0) : null;
    }

    /**
     * @return {@code true} if the package is installed and set as always-on, {@code false} if it is
     * not installed and therefore not available.
     *
     * @throws SecurityException if the caller is not a profile or device owner.
     * @throws UnsupportedOperationException if the package does not support being set as always-on.
     */
    @Override
    public boolean setAlwaysOnVpnPackage(ComponentName who, String vpnPackage, boolean lockdown,
            List<String> lockdownAllowlist)
            throws SecurityException {
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_ALWAYS_ON_VPN_PACKAGE);

        if (vpnPackage == null) {
            final String prevVpnPackage;
            synchronized (getLockObject()) {
                prevVpnPackage = getProfileOwnerOrDeviceOwnerLocked(
                        caller.getUserId()).mAlwaysOnVpnPackage;
                // If the admin is clearing VPN package but hasn't configure any VPN previously,
                // ignore it so that it doesn't interfere with user-configured VPNs.
                if (TextUtils.isEmpty(prevVpnPackage)) {
                    return true;
                }
            }
            revokeVpnAuthorizationForPackage(prevVpnPackage, caller.getUserId());
        }

        final int userId = caller.getUserId();
        mInjector.binderWithCleanCallingIdentity(() -> {
            if (vpnPackage != null && !isPackageInstalledForUser(vpnPackage, userId)) {
                Slogf.w(LOG_TAG, "Non-existent VPN package specified: " + vpnPackage);
                throw new ServiceSpecificException(
                        DevicePolicyManager.ERROR_VPN_PACKAGE_NOT_FOUND, vpnPackage);
            }

            if (vpnPackage != null && lockdown && lockdownAllowlist != null) {
                for (String packageName : lockdownAllowlist) {
                    if (!isPackageInstalledForUser(packageName, userId)) {
                        Slogf.w(LOG_TAG, "Non-existent package in VPN allowlist: " + packageName);
                        throw new ServiceSpecificException(
                                DevicePolicyManager.ERROR_VPN_PACKAGE_NOT_FOUND, packageName);
                    }
                }
            }
            // If some package is uninstalled after the check above, it will be ignored by CM.
            if (!mInjector.getVpnManager().setAlwaysOnVpnPackageForUser(
                    userId, vpnPackage, lockdown, lockdownAllowlist)) {
                throw new UnsupportedOperationException();
            }
        });
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_ALWAYS_ON_VPN_PACKAGE)
                .setAdmin(caller.getComponentName())
                .setStrings(vpnPackage)
                .setBoolean(lockdown)
                .setInt(lockdownAllowlist != null ? lockdownAllowlist.size() : 0)
                .write();
        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            if (!TextUtils.equals(vpnPackage, admin.mAlwaysOnVpnPackage)
                    || lockdown != admin.mAlwaysOnVpnLockdown) {
                admin.mAlwaysOnVpnPackage = vpnPackage;
                admin.mAlwaysOnVpnLockdown = lockdown;
                saveSettingsLocked(userId);
            }
        }
        return true;
    }

    private void revokeVpnAuthorizationForPackage(String vpnPackage, int userId) {
        mInjector.binderWithCleanCallingIdentity(() -> {
            try {
                final ApplicationInfo ai = mIPackageManager.getApplicationInfo(
                        vpnPackage, /* flags= */ 0, userId);
                if (ai == null) {
                    Slogf.w(LOG_TAG, "Non-existent VPN package: " + vpnPackage);
                } else {
                    mInjector.getAppOpsManager().setMode(AppOpsManager.OP_ACTIVATE_VPN,
                            ai.uid, vpnPackage, MODE_DEFAULT);
                }
            } catch (RemoteException e) {
                Slogf.e(LOG_TAG, "Can't talk to package managed", e);
            }
        });
    }

    @Override
    public String getAlwaysOnVpnPackage(ComponentName admin) throws SecurityException {
        Objects.requireNonNull(admin, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));

        return mInjector.binderWithCleanCallingIdentity(
                () -> mInjector.getVpnManager().getAlwaysOnVpnPackageForUser(caller.getUserId()));
    }

    @Override
    public String getAlwaysOnVpnPackageForUser(int userHandle) {
        Preconditions.checkCallAuthorization(isSystemUid(getCallerIdentity()),
                String.format(NOT_SYSTEM_CALLER_MSG, "call getAlwaysOnVpnPackageForUser"));
        synchronized (getLockObject()) {
            ActiveAdmin admin = getDeviceOrProfileOwnerAdminLocked(userHandle);
            return admin != null ? admin.mAlwaysOnVpnPackage : null;
        }
    }

    @Override
    public boolean isAlwaysOnVpnLockdownEnabled(ComponentName admin) throws SecurityException {
        final CallerIdentity caller;
        if (hasCallingPermission(PERMISSION_MAINLINE_NETWORK_STACK)) {
            // TODO: CaptivePortalLoginActivity erroneously calls this method with a non-admin
            // ComponentName, so we have to use a separate code path for it:
            // getCallerIdentity(admin) will throw if the admin is not in the known admin list.
            caller = getCallerIdentity();
        } else {
            caller = getCallerIdentity(admin);
            Preconditions.checkCallAuthorization(
                    isDefaultDeviceOwner(caller) || isProfileOwner(caller));
        }

        return mInjector.binderWithCleanCallingIdentity(
                () -> mInjector.getVpnManager().isVpnLockdownEnabled(caller.getUserId()));
    }

    @Override
    public boolean isAlwaysOnVpnLockdownEnabledForUser(int userHandle) {
        Preconditions.checkCallAuthorization(isSystemUid(getCallerIdentity()),
                String.format(NOT_SYSTEM_CALLER_MSG, "call isAlwaysOnVpnLockdownEnabledForUser"));
        synchronized (getLockObject()) {
            ActiveAdmin admin = getDeviceOrProfileOwnerAdminLocked(userHandle);
            return admin != null && admin.mAlwaysOnVpnLockdown;
        }
    }

    @Override
    public List<String> getAlwaysOnVpnLockdownAllowlist(ComponentName admin)
            throws SecurityException {
        Objects.requireNonNull(admin, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));

        return mInjector.binderWithCleanCallingIdentity(
                () -> mInjector.getVpnManager().getVpnLockdownAllowlist(caller.getUserId()));
    }

    private void forceWipeDeviceNoLock(boolean wipeExtRequested, String reason, boolean wipeEuicc,
            boolean wipeResetProtectionData) {
        wtfIfInLock();
        boolean success = false;

        try {
            boolean delayed = !mInjector.recoverySystemRebootWipeUserData(
                    /* shutdown= */ false, reason, /* force= */ true, /* wipeEuicc= */ wipeEuicc,
                    wipeExtRequested, wipeResetProtectionData);
            if (delayed) {
                // Persist the request so the device is automatically factory-reset on next start if
                // the system crashes or reboots before the {@code DevicePolicySafetyChecker} calls
                // its callback.
                Slogf.i(LOG_TAG, "Persisting factory reset request as it could be delayed by %s",
                        mSafetyChecker);
                synchronized (getLockObject()) {
                    DevicePolicyData policy = getUserData(UserHandle.USER_SYSTEM);
                    policy.setDelayedFactoryReset(reason, wipeExtRequested, wipeEuicc,
                            wipeResetProtectionData);
                    saveSettingsLocked(UserHandle.USER_SYSTEM);
                }
            }
            success = true;
        } catch (IOException | SecurityException e) {
            Slogf.w(LOG_TAG, "Failed requesting data wipe", e);
        } finally {
            if (!success) SecurityLog.writeEvent(SecurityLog.TAG_WIPE_FAILURE);
        }
    }

    private void factoryResetIfDelayedEarlier() {
        synchronized (getLockObject()) {
            DevicePolicyData policy = getUserData(UserHandle.USER_SYSTEM);

            if (policy.mFactoryResetFlags == 0) return;

            if (policy.mFactoryResetReason == null) {
                // Shouldn't happen.
                Slogf.e(LOG_TAG, "no persisted reason for factory resetting");
                policy.mFactoryResetReason = "requested before boot";
            }
            FactoryResetter factoryResetter = FactoryResetter.newBuilder(mContext)
                    .setReason(policy.mFactoryResetReason).setForce(true)
                    .setWipeEuicc((policy.mFactoryResetFlags & DevicePolicyData
                            .FACTORY_RESET_FLAG_WIPE_EUICC) != 0)
                    .setWipeAdoptableStorage((policy.mFactoryResetFlags & DevicePolicyData
                            .FACTORY_RESET_FLAG_WIPE_EXTERNAL_STORAGE) != 0)
                    .setWipeFactoryResetProtection((policy.mFactoryResetFlags & DevicePolicyData
                            .FACTORY_RESET_FLAG_WIPE_FACTORY_RESET_PROTECTION) != 0)
                    .build();
            Slogf.i(LOG_TAG, "Factory resetting on boot using " + factoryResetter);
            try {
                if (!factoryResetter.factoryReset()) {
                    // Shouldn't happen because FactoryResetter was created without a
                    // DevicePolicySafetyChecker.
                    Slogf.wtf(LOG_TAG, "Factory reset using " + factoryResetter + " failed.");
                }
            } catch (IOException e) {
                // Shouldn't happen.
                Slogf.wtf(LOG_TAG, "Could not factory reset using " + factoryResetter, e);
            }
        }
    }

    private void forceWipeUser(int userId, String wipeReasonForUser, boolean wipeSilently) {
        boolean success = false;
        try {
            if (getCurrentForegroundUserId() == userId) {
                mInjector.getIActivityManager().switchUser(UserHandle.USER_SYSTEM);
            }

            success = mUserManagerInternal.removeUserEvenWhenDisallowed(userId);
            if (!success) {
                Slogf.w(LOG_TAG, "Couldn't remove user " + userId);
            } else if (isManagedProfile(userId) && !wipeSilently) {
                sendWipeProfileNotification(wipeReasonForUser);
            }
        } catch (RemoteException re) {
            // Shouldn't happen
            Slogf.wtf(LOG_TAG, "Error forcing wipe user", re);
        } finally {
            if (!success) SecurityLog.writeEvent(SecurityLog.TAG_WIPE_FAILURE);
        }
    }

    @Override
    public void wipeDataWithReason(int flags, @NonNull String wipeReasonForUser,
            boolean calledOnParentInstance, boolean factoryReset) {
        if (!mHasFeature && !hasCallingOrSelfPermission(permission.MASTER_CLEAR)) {
            return;
        }
        final CallerIdentity caller = getCallerIdentity();
        boolean calledByProfileOwnerOnOrgOwnedDevice =
                isProfileOwnerOfOrganizationOwnedDevice(caller.getUserId());
        if (calledOnParentInstance) {
            Preconditions.checkCallAuthorization(calledByProfileOwnerOnOrgOwnedDevice,
                    "Wiping the entire device can only be done by a profile owner on "
                            + "organization-owned device.");
        }
        if ((flags & WIPE_RESET_PROTECTION_DATA) != 0) {
            Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                            || calledByProfileOwnerOnOrgOwnedDevice
                            || isFinancedDeviceOwner(caller),
                    "Only device owners or profile owners of organization-owned device can set "
                            + "WIPE_RESET_PROTECTION_DATA");
        }

        final ActiveAdmin admin;
        synchronized (getLockObject()) {
            admin = getActiveAdminWithPolicyForUidLocked(/* who= */ null,
                    DeviceAdminInfo.USES_POLICY_WIPE_DATA, caller.getUid());
        }

        Preconditions.checkCallAuthorization(
                (admin != null) || hasCallingOrSelfPermission(permission.MASTER_CLEAR),
                "No active admin for user %d and caller %d does not hold MASTER_CLEAR permission",
                caller.getUserId(), caller.getUid());
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_WIPE_DATA);

        if (TextUtils.isEmpty(wipeReasonForUser)) {
            wipeReasonForUser = getGenericWipeReason(
                    calledByProfileOwnerOnOrgOwnedDevice, calledOnParentInstance);
        }

        int userId = admin != null ? admin.getUserHandle().getIdentifier()
                : caller.getUserId();
        Slogf.i(LOG_TAG, "wipeDataWithReason(%s): admin=%s, user=%d", wipeReasonForUser, admin,
                userId);
        if (calledByProfileOwnerOnOrgOwnedDevice) {
            // When wipeData is called on the parent instance, it implies wiping the entire device.
            if (calledOnParentInstance) {
                userId = UserHandle.USER_SYSTEM;
            } else {
                // when wipeData is _not_ called on the parent instance, it implies relinquishing
                // control over the device, wiping only the work profile. So the user restriction
                // on profile removal needs to be removed first.
                final UserHandle parentUser = UserHandle.of(getProfileParentId(userId));
                mInjector.binderWithCleanCallingIdentity(
                        () -> clearOrgOwnedProfileOwnerUserRestrictions(parentUser));
            }
        }
        DevicePolicyEventLogger event = DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.WIPE_DATA_WITH_REASON)
                .setInt(flags)
                .setStrings(calledOnParentInstance ? CALLED_FROM_PARENT : NOT_CALLED_FROM_PARENT);

        final String adminName;
        final ComponentName adminComp;
        if (admin != null) {
            adminComp = admin.info.getComponent();
            adminName = adminComp.flattenToShortString();
            event.setAdmin(adminComp);
        } else {
            adminComp = null;
            adminName = mInjector.getPackageManager().getPackagesForUid(caller.getUid())[0];
            Slogf.i(LOG_TAG, "Logging wipeData() event admin as " + adminName);
            event.setAdmin(adminName);
            if (mInjector.userManagerIsHeadlessSystemUserMode()) {
                // On headless system user mode, the call is meant to factory reset the whole
                // device, otherwise the caller could simply remove the current user.
                userId = UserHandle.USER_SYSTEM;
            }
        }
        event.write();

        String internalReason = String.format(
                "DevicePolicyManager.wipeDataWithReason() from %s, organization-owned? %s",
                adminName, calledByProfileOwnerOnOrgOwnedDevice);

        wipeDataNoLock(adminComp, flags, internalReason, wipeReasonForUser, userId,
                calledOnParentInstance, factoryReset);
    }

    private String getGenericWipeReason(
            boolean calledByProfileOwnerOnOrgOwnedDevice, boolean calledOnParentInstance) {
        return calledByProfileOwnerOnOrgOwnedDevice && !calledOnParentInstance
                ? getUpdatableString(
                        WORK_PROFILE_DELETED_ORG_OWNED_MESSAGE,
                        R.string.device_ownership_relinquished)
                : getUpdatableString(
                        WORK_PROFILE_DELETED_GENERIC_MESSAGE,
                        R.string.work_profile_deleted_description_dpm_wipe);
    }

    /**
     * Clears device wide policies enforced by COPE PO when relinquishing the device. This method
     * should be invoked once the admin is gone, so that all methods that rely on calculating
     * aggregate policy (e.g. strong auth timeout) from all admins aren't affected by its policies.
     * This method assumes that there is no other device or profile owners left on the device.
     * Shouldn't be called from binder thread without clearing identity.
     */
    private void clearOrgOwnedProfileOwnerDeviceWidePolicies(@UserIdInt int parentId) {
        Slogf.i(LOG_TAG, "Cleaning up device-wide policies left over from org-owned profile...");
        // Lockscreen message
        mLockPatternUtils.setDeviceOwnerInfo(null);
        // Wifi config lockdown
        mInjector.settingsGlobalPutInt(Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN, 0);
        // Security logging
        if (mInjector.securityLogGetLoggingEnabledProperty()) {
            mSecurityLogMonitor.stop();
            mInjector.securityLogSetLoggingEnabledProperty(false);
        }
        // Network logging
        setNetworkLoggingActiveInternal(false);

        // System update policy.
        final boolean hasSystemUpdatePolicy;
        synchronized (getLockObject()) {
            hasSystemUpdatePolicy = mOwners.getSystemUpdatePolicy() != null;
            if (hasSystemUpdatePolicy) {
                mOwners.clearSystemUpdatePolicy();
                mOwners.writeDeviceOwner();
            }
        }
        if (hasSystemUpdatePolicy) {
            mContext.sendBroadcastAsUser(
                    new Intent(ACTION_SYSTEM_UPDATE_POLICY_CHANGED), UserHandle.SYSTEM);
        }

        // Unsuspend personal apps if needed.
        suspendPersonalAppsInternal(parentId, false);

        // Notify FRP agent, LSS and WindowManager to ensure they don't hold on to stale policies.
        final int frpAgentUid = getFrpManagementAgentUid();
        if (frpAgentUid > 0) {
            notifyResetProtectionPolicyChanged(frpAgentUid);
        }
        mLockSettingsInternal.refreshStrongAuthTimeout(parentId);

        if (isWorkProfileTelephonyFlagEnabled()) {
            clearManagedSubscriptionsPolicy();
            updateTelephonyCrossProfileIntentFilters(parentId, UserHandle.USER_NULL, false);
        }
        Slogf.i(LOG_TAG, "Cleaning up device-wide policies done.");
    }

    private void clearManagedSubscriptionsPolicy() {
        unregisterOnSubscriptionsChangedListener();

        SubscriptionManager subscriptionManager = mContext.getSystemService(
                SubscriptionManager.class);
        //Iterate over all the subscriptions and remove association with any user.
        int[] subscriptionIds = subscriptionManager.getActiveSubscriptionIdList(false);
        for (int subId : subscriptionIds) {
            subscriptionManager.setSubscriptionUserHandle(subId, null);
        }
    }

    private void updateTelephonyCrossProfileIntentFilters(int parentUserId, int profileUserId,
            boolean enableWorkTelephony) {
        try {
            String packageName = mContext.getOpPackageName();
            if (enableWorkTelephony) {
                // Reset call/sms cross profile intent filters to be handled by managed profile.
                for (DefaultCrossProfileIntentFilter filter :
                        DefaultCrossProfileIntentFiltersUtils
                                .getDefaultManagedProfileTelephonyFilters()) {
                    IntentFilter intentFilter = filter.filter.getIntentFilter();
                    if (!mIPackageManager.removeCrossProfileIntentFilter(intentFilter, packageName,
                            profileUserId, parentUserId, filter.flags)) {
                        Slogf.w(LOG_TAG,
                                "Failed to remove cross-profile intent filter: " + intentFilter);
                    }

                    mIPackageManager.addCrossProfileIntentFilter(intentFilter, packageName,
                            parentUserId, profileUserId, PackageManager.SKIP_CURRENT_PROFILE);
                }
            } else {
                mIPackageManager.clearCrossProfileIntentFilters(parentUserId, packageName);
            }
        } catch (RemoteException re) {
            Slogf.wtf(LOG_TAG, "Error updating telephony cross profile intent filters", re);
        }
    }

    /**
     * @param factoryReset null: legacy behaviour, false: attempt to remove user, true: attempt to
     *                     factory reset
     */
    private void wipeDataNoLock(ComponentName admin, int flags, String internalReason,
            @NonNull String wipeReasonForUser, int userId, boolean calledOnParentInstance,
            @Nullable Boolean factoryReset) {
        wtfIfInLock();

        mInjector.binderWithCleanCallingIdentity(() -> {
            // First check whether the admin is allowed to wipe the device/user/profile.
            final String restriction;
            if (userId == UserHandle.USER_SYSTEM) {
                restriction = UserManager.DISALLOW_FACTORY_RESET;
            } else if (isManagedProfile(userId)) {
                restriction = UserManager.DISALLOW_REMOVE_MANAGED_PROFILE;
            } else {
                restriction = UserManager.DISALLOW_REMOVE_USER;
            }
            if (isAdminAffectedByRestriction(admin, restriction, userId)) {
                throw new SecurityException("Cannot wipe data. " + restriction
                        + " restriction is set for user " + userId);
            }

            boolean isSystemUser = userId == UserHandle.USER_SYSTEM;
            boolean wipeDevice;
            if (factoryReset == null || !mInjector.isChangeEnabled(EXPLICIT_WIPE_BEHAVIOUR,
                    admin.getPackageName(),
                    userId)) {
                // Legacy mode
                wipeDevice = isSystemUser;
            } else {
                // Explicit behaviour
                if (factoryReset) {
                    // TODO(b/254031494) Replace with new factory reset permission checks
                    boolean hasPermission = isDeviceOwnerUserId(userId)
                            || (isOrganizationOwnedDeviceWithManagedProfile()
                            && calledOnParentInstance);
                    Preconditions.checkState(hasPermission,
                            "Admin %s does not have permission to factory reset the device.",
                            userId);
                    wipeDevice = true;
                } else {
                    Preconditions.checkCallAuthorization(!isSystemUser,
                            "User %s is a system user and cannot be removed", userId);
                    boolean isLastNonHeadlessUser = getUserInfo(userId).isFull()
                            && mUserManager.getAliveUsers().stream()
                            .filter((it) -> it.getUserHandle().getIdentifier() != userId)
                            .noneMatch(UserInfo::isFull);
                    Preconditions.checkState(!isLastNonHeadlessUser,
                            "Removing user %s would leave the device without any active users. "
                                    + "Consider factory resetting the device instead.",
                            userId);
                    wipeDevice = false;
                }
            }
            if (wipeDevice) {
                forceWipeDeviceNoLock(
                        (flags & WIPE_EXTERNAL_STORAGE) != 0,
                        internalReason,
                        (flags & WIPE_EUICC) != 0,
                        (flags & WIPE_RESET_PROTECTION_DATA) != 0);
            } else {
                forceWipeUser(userId, wipeReasonForUser, (flags & WIPE_SILENTLY) != 0);
            }
        });
    }

    private void sendWipeProfileNotification(String wipeReasonForUser) {
        Notification notification =
                new Notification.Builder(mContext, SystemNotificationChannels.DEVICE_ADMIN)
                        .setSmallIcon(android.R.drawable.stat_sys_warning)
                        .setContentTitle(getWorkProfileDeletedTitle())
                        .setContentText(wipeReasonForUser)
                        .setColor(mContext.getColor(R.color.system_notification_accent_color))
                        .setStyle(new Notification.BigTextStyle().bigText(wipeReasonForUser))
                        .build();
        mInjector.getNotificationManager().notify(SystemMessage.NOTE_PROFILE_WIPED, notification);
    }

    private String getWorkProfileDeletedTitle() {
        return getUpdatableString(WORK_PROFILE_DELETED_TITLE, R.string.work_profile_deleted);
    }

    private void clearWipeProfileNotification() {
        mInjector.getNotificationManager().cancel(SystemMessage.NOTE_PROFILE_WIPED);
    }

    @Override
    public void setFactoryResetProtectionPolicy(ComponentName who,
            @Nullable FactoryResetProtectionPolicy policy) {
        if (!mHasFeature) {
            return;
        }
        Preconditions.checkNotNull(who, "ComponentName is null");
        CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwnerOfOrganizationOwnedDevice(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager
                .OPERATION_SET_FACTORY_RESET_PROTECTION_POLICY);

        final int frpManagementAgentUid = getFrpManagementAgentUidOrThrow();
        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            admin.mFactoryResetProtectionPolicy = policy;
            saveSettingsLocked(caller.getUserId());
        }

        mInjector.binderWithCleanCallingIdentity(
                () -> notifyResetProtectionPolicyChanged(frpManagementAgentUid));

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_FACTORY_RESET_PROTECTION)
                .setAdmin(who)
                .write();
    }

    // Shouldn't be called from binder thread without clearing identity.
    private void notifyResetProtectionPolicyChanged(int frpManagementAgentUid) {
        final Intent intent = new Intent(
                DevicePolicyManager.ACTION_RESET_PROTECTION_POLICY_CHANGED).addFlags(
                Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND | Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcastAsUser(intent,
                UserHandle.getUserHandleForUid(frpManagementAgentUid),
                permission.MANAGE_FACTORY_RESET_PROTECTION);
    }

    @Override
    public FactoryResetProtectionPolicy getFactoryResetProtectionPolicy(
            @Nullable ComponentName who) {
        if (!mHasFeature) {
            return null;
        }

        final CallerIdentity caller = getCallerIdentity(who);
        final int frpManagementAgentUid = getFrpManagementAgentUidOrThrow();
        final ActiveAdmin admin;
        synchronized (getLockObject()) {
            if (who == null) {
                Preconditions.checkCallAuthorization(frpManagementAgentUid == caller.getUid()
                                || hasCallingPermission(permission.MASTER_CLEAR),
                        "Must be called by the FRP management agent on device");
                // TODO(b/261999445): Remove
                if (isHeadlessFlagEnabled()) {
                    admin = getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked();
                } else {
                    admin = getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked(
                            UserHandle.getUserId(frpManagementAgentUid));
                }
            } else {
                Preconditions.checkCallAuthorization(
                        isDefaultDeviceOwner(caller)
                                || isProfileOwnerOfOrganizationOwnedDevice(caller));
                admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            }
        }

        return admin != null ? admin.mFactoryResetProtectionPolicy : null;
    }

    private int getFrpManagementAgentUid() {
        PersistentDataBlockManagerInternal pdb = mInjector.getPersistentDataBlockManagerInternal();
        return pdb != null ? pdb.getAllowedUid() : -1;
    }

    private int getFrpManagementAgentUidOrThrow() {
        int uid = getFrpManagementAgentUid();
        if (uid == -1) {
            throw new UnsupportedOperationException(
                    "The persistent data block service is not supported on this device");
        }
        return uid;
    }

    @Override
    public boolean isFactoryResetProtectionPolicySupported() {
        return getFrpManagementAgentUid() != -1;
    }

    @Override
    public void sendLostModeLocationUpdate(AndroidFuture<Boolean> future) {
        if (!mHasFeature) {
            future.complete(false);
            return;
        }
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.TRIGGER_LOST_MODE));

        synchronized (getLockObject()) {
            // TODO(b/261999445): Remove
            ActiveAdmin admin;
            if (isHeadlessFlagEnabled()) {
                admin = getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked();
            } else {
                admin = getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked(
                        UserHandle.USER_SYSTEM);
            }
            Preconditions.checkState(admin != null,
                    "Lost mode location updates can only be sent on an organization-owned device.");
            mInjector.binderWithCleanCallingIdentity(() -> {
                String[] providers = {LocationManager.FUSED_PROVIDER,
                        LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER};
                tryRetrieveAndSendLocationUpdate(admin, future, providers, /* index= */ 0);
            });
        }
    }

    /** Send lost mode location updates recursively, in order of the list of location providers. */
    private void tryRetrieveAndSendLocationUpdate(ActiveAdmin admin,
            AndroidFuture<Boolean> future, String[] providers, int index) {
        // None of the providers were able to get location, return false
        if (index == providers.length) {
            future.complete(false);
            return;
        }
        if (mInjector.getLocationManager().isProviderEnabled(providers[index])) {
            mInjector.getLocationManager().getCurrentLocation(providers[index],
                    /* cancellationSignal= */ null, mContext.getMainExecutor(), location -> {
                        if (location != null) {
                            mContext.sendBroadcastAsUser(
                                    newLostModeLocationUpdateIntent(admin, location),
                                    admin.getUserHandle());
                            future.complete(true);
                        } else {
                            tryRetrieveAndSendLocationUpdate(admin, future, providers, index + 1);
                        }
                    }
            );
        } else {
           tryRetrieveAndSendLocationUpdate(admin, future, providers, index + 1);
        }
    }

    private Intent newLostModeLocationUpdateIntent(ActiveAdmin admin, Location location) {
        final Intent intent = new Intent(
                DevicePolicyManager.ACTION_LOST_MODE_LOCATION_UPDATE);
        intent.putExtra(DevicePolicyManager.EXTRA_LOST_MODE_LOCATION, location);
        intent.setPackage(admin.info.getPackageName());
        return intent;
    }

    /**
     * Called by a privileged caller holding {@code BIND_DEVICE_ADMIN} permission to retrieve
     * the remove warning for the given device admin.
     */
    @Override
    public void getRemoveWarning(ComponentName comp, final RemoteCallback result, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(BIND_DEVICE_ADMIN));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getActiveAdminUncheckedLocked(comp, userHandle);
            if (admin == null) {
                result.sendResult(null);
                return;
            }
            Intent intent = new Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_DISABLE_REQUESTED);
            intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            intent.setComponent(admin.info.getComponent());
            mContext.sendOrderedBroadcastAsUser(intent, new UserHandle(userHandle),
                    null, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    result.sendResult(getResultExtras(false));
                }
            }, null, Activity.RESULT_OK, null, null);
        }
    }

    @Override
    public void reportPasswordChanged(PasswordMetrics metrics, @UserIdInt int userId) {
        if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
            return;
        }

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(isSystemUid(caller));
        // Managed Profile password can only be changed when it has a separate challenge.
        if (!isSeparateProfileChallengeEnabled(userId)) {
            Preconditions.checkCallAuthorization(!isManagedProfile(userId), "You can "
                    + "not set the active password for a managed profile, userId = %d", userId);
        }

        DevicePolicyData policy = getUserData(userId);
        final ArraySet<Integer> affectedUserIds = new ArraySet<>();

        synchronized (getLockObject()) {
            policy.mFailedPasswordAttempts = 0;
            affectedUserIds.add(userId);
            affectedUserIds.addAll(updatePasswordValidityCheckpointLocked(
                    userId, /* parent */ false));
            affectedUserIds.addAll(updatePasswordExpirationsLocked(userId));
            setExpirationAlarmCheckLocked(mContext, userId, /* parent */ false);

            // Send a broadcast to each profile using this password as its primary unlock.
            sendAdminCommandForLockscreenPoliciesLocked(
                    DeviceAdminReceiver.ACTION_PASSWORD_CHANGED,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD, userId);

            affectedUserIds.addAll(removeCaApprovalsIfNeeded(userId));
            saveSettingsForUsersLocked(affectedUserIds);
        }
        if (mInjector.securityLogIsLoggingEnabled()) {
            SecurityLog.writeEvent(SecurityLog.TAG_PASSWORD_CHANGED,
                    /* complexity */ metrics.determineComplexity(), /*user*/ userId);
        }
    }

    /**
     * Called any time the device password is updated. Resets all password expiration clocks.
     *
     * @return the set of user IDs that have been affected
     */
    private Set<Integer> updatePasswordExpirationsLocked(int userHandle) {
        final ArraySet<Integer> affectedUserIds = new ArraySet<>();
        List<ActiveAdmin> admins = getActiveAdminsForLockscreenPoliciesLocked(userHandle);
        for (int i = 0; i < admins.size(); i++) {
            ActiveAdmin admin = admins.get(i);
            if (admin.info.usesPolicy(DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD)) {
                affectedUserIds.add(admin.getUserHandle().getIdentifier());
                long timeout = admin.passwordExpirationTimeout;
                admin.passwordExpirationDate =
                        timeout > 0L ? (timeout + System.currentTimeMillis()) : 0L;
            }
        }
        return affectedUserIds;
    }

    @Override
    public void reportFailedPasswordAttempt(int userHandle, boolean parent) {
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(BIND_DEVICE_ADMIN));
        if (!isSeparateProfileChallengeEnabled(userHandle)) {
            Preconditions.checkCallAuthorization(!isManagedProfile(userHandle),
                    "You can not report failed password attempt if separate profile challenge is "
                            + "not in place for a managed profile, userId = %d", userHandle);
        }

        boolean wipeData = false;
        ActiveAdmin strictestAdmin = null;
        final long ident = mInjector.binderClearCallingIdentity();
        try {
            synchronized (getLockObject()) {
                DevicePolicyData policy = getUserData(userHandle);
                policy.mFailedPasswordAttempts++;
                saveSettingsLocked(userHandle);
                if (mHasFeature) {
                    strictestAdmin = getAdminWithMinimumFailedPasswordsForWipeLocked(
                            userHandle, /* parent= */ false);
                    int max = strictestAdmin != null
                            ? strictestAdmin.maximumFailedPasswordsForWipe : 0;
                    if (max > 0 && policy.mFailedPasswordAttempts >= max) {
                        wipeData = true;
                    }

                    sendAdminCommandForLockscreenPoliciesLocked(
                            DeviceAdminReceiver.ACTION_PASSWORD_FAILED,
                            DeviceAdminInfo.USES_POLICY_WATCH_LOGIN, userHandle);
                }
            }
        } finally {
            mInjector.binderRestoreCallingIdentity(ident);
        }

        if (wipeData && strictestAdmin != null) {
            final int userId = getUserIdToWipeForFailedPasswords(strictestAdmin);
            Slogf.i(LOG_TAG, "Max failed password attempts policy reached for admin: "
                    + strictestAdmin.info.getComponent().flattenToShortString()
                    + ". Calling wipeData for user " + userId);

            // Attempt to wipe the device/user/profile associated with the admin, as if the
            // admin had called wipeData(). That way we can check whether the admin is actually
            // allowed to wipe the device (e.g. a regular device admin shouldn't be able to wipe the
            // device if the device owner has set DISALLOW_FACTORY_RESET, but the DO should be
            // able to do so).
            // IMPORTANT: Call without holding the lock to prevent deadlock.
            try {
                wipeDataNoLock(strictestAdmin.info.getComponent(),
                        /* flags= */ 0,
                        /* reason= */ "reportFailedPasswordAttempt()",
                        getFailedPasswordAttemptWipeMessage(),
                        userId,
                        /* calledOnParentInstance= */ parent,
                        // factoryReset=null to enable U- behaviour
                        /* factoryReset= */ null);
            } catch (SecurityException e) {
                Slogf.w(LOG_TAG, "Failed to wipe user " + userId
                        + " after max failed password attempts reached.", e);
            }
        }

        if (mInjector.securityLogIsLoggingEnabled()) {
            SecurityLog.writeEvent(SecurityLog.TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT,
                    /* result= */ 0, /* method strength= */ 1);
        }
    }

    private String getFailedPasswordAttemptWipeMessage() {
        return getUpdatableString(
                WORK_PROFILE_DELETED_FAILED_PASSWORD_ATTEMPTS_MESSAGE,
               R.string.work_profile_deleted_reason_maximum_password_failure);
    }

    /**
     * Returns which user should be wiped if this admin's maximum filed password attempts policy is
     * violated.
     */
    private int getUserIdToWipeForFailedPasswords(ActiveAdmin admin) {
        final int userId = admin.getUserHandle().getIdentifier();
        final ComponentName component = admin.info.getComponent();
        return isProfileOwnerOfOrganizationOwnedDevice(component, userId)
                ? getProfileParentId(userId) : userId;
    }

    @Override
    public void reportSuccessfulPasswordAttempt(int userHandle) {
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(BIND_DEVICE_ADMIN));

        synchronized (getLockObject()) {
            DevicePolicyData policy = getUserData(userHandle);
            if (policy.mFailedPasswordAttempts != 0 || policy.mPasswordOwner >= 0) {
                mInjector.binderWithCleanCallingIdentity(() -> {
                    policy.mFailedPasswordAttempts = 0;
                    policy.mPasswordOwner = -1;
                    saveSettingsLocked(userHandle);
                    if (mHasFeature) {
                        sendAdminCommandForLockscreenPoliciesLocked(
                                DeviceAdminReceiver.ACTION_PASSWORD_SUCCEEDED,
                                DeviceAdminInfo.USES_POLICY_WATCH_LOGIN, userHandle);
                    }
                });
            }
        }

        if (mInjector.securityLogIsLoggingEnabled()) {
            SecurityLog.writeEvent(SecurityLog.TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT, /*result*/ 1,
                    /*method strength*/ 1);
        }
    }

    @Override
    public void reportFailedBiometricAttempt(int userHandle) {
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(BIND_DEVICE_ADMIN));

        if (mInjector.securityLogIsLoggingEnabled()) {
            SecurityLog.writeEvent(SecurityLog.TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT, /*result*/ 0,
                    /*method strength*/ 0);
        }
    }

    @Override
    public void reportSuccessfulBiometricAttempt(int userHandle) {
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(BIND_DEVICE_ADMIN));

        if (mInjector.securityLogIsLoggingEnabled()) {
            SecurityLog.writeEvent(SecurityLog.TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT, /*result*/ 1,
                    /*method strength*/ 0);
        }
    }

    @Override
    public void reportKeyguardDismissed(int userHandle) {
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(BIND_DEVICE_ADMIN));

        if (mInjector.securityLogIsLoggingEnabled()) {
            SecurityLog.writeEvent(SecurityLog.TAG_KEYGUARD_DISMISSED);
        }
    }

    @Override
    public void reportKeyguardSecured(int userHandle) {
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(BIND_DEVICE_ADMIN));

        if (mInjector.securityLogIsLoggingEnabled()) {
            SecurityLog.writeEvent(SecurityLog.TAG_KEYGUARD_SECURED);
        }
    }

    @Override
    public ComponentName setGlobalProxy(ComponentName who, String proxySpec,
            String exclusionList) {
        if (!mHasFeature) {
            return null;
        }
        synchronized (getLockObject()) {
            Objects.requireNonNull(who, "ComponentName is null");

            // Only check if system user has set global proxy. We don't allow other users to set it.
            DevicePolicyData policy = getUserData(UserHandle.USER_SYSTEM);
            ActiveAdmin admin = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_SETS_GLOBAL_PROXY);

            // Scan through active admins and find if anyone has already
            // set the global proxy.
            Set<ComponentName> compSet = policy.mAdminMap.keySet();
            for (ComponentName component : compSet) {
                ActiveAdmin ap = policy.mAdminMap.get(component);
                if ((ap.specifiesGlobalProxy) && (!component.equals(who))) {
                    // Another admin already sets the global proxy
                    // Return it to the caller.
                    return component;
                }
            }

            // If the user is not system, don't set the global proxy. Fail silently.
            if (UserHandle.getCallingUserId() != UserHandle.USER_SYSTEM) {
                Slogf.w(LOG_TAG, "Only the owner is allowed to set the global proxy. User "
                        + UserHandle.getCallingUserId() + " is not permitted.");
                return null;
            }
            if (proxySpec == null) {
                admin.specifiesGlobalProxy = false;
                admin.globalProxySpec = null;
                admin.globalProxyExclusionList = null;
            } else {

                admin.specifiesGlobalProxy = true;
                admin.globalProxySpec = proxySpec;
                admin.globalProxyExclusionList = exclusionList;
            }

            // Reset the global proxy accordingly
            // Do this using system permissions, as apps cannot write to secure settings
            mInjector.binderWithCleanCallingIdentity(() -> resetGlobalProxyLocked(policy));
            return null;
        }
    }

    @Override
    public ComponentName getGlobalProxyAdmin(int userHandle) {
        if (!mHasFeature) {
            return null;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                hasFullCrossUsersPermission(caller, userHandle) && canQueryAdminPolicy(caller));

        synchronized (getLockObject()) {
            DevicePolicyData policy = getUserData(UserHandle.USER_SYSTEM);
            // Scan through active admins and find if anyone has already
            // set the global proxy.
            final int N = policy.mAdminList.size();
            for (int i = 0; i < N; i++) {
                ActiveAdmin ap = policy.mAdminList.get(i);
                if (ap.specifiesGlobalProxy) {
                    // Device admin sets the global proxy
                    // Return it to the caller.
                    return ap.info.getComponent();
                }
            }
        }
        // No device admin sets the global proxy.
        return null;
    }

    @Override
    public void setRecommendedGlobalProxy(ComponentName who, ProxyInfo proxyInfo) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        checkAllUsersAreAffiliatedWithDevice();
        mInjector.binderWithCleanCallingIdentity(
                () -> mInjector.getConnectivityManager().setGlobalProxy(proxyInfo));
    }

    private void resetGlobalProxyLocked(DevicePolicyData policy) {
        final int N = policy.mAdminList.size();
        for (int i = 0; i < N; i++) {
            ActiveAdmin ap = policy.mAdminList.get(i);
            if (ap.specifiesGlobalProxy) {
                saveGlobalProxyLocked(ap.globalProxySpec, ap.globalProxyExclusionList);
                return;
            }
        }
        // No device admins defining global proxies - reset global proxy settings to none
        saveGlobalProxyLocked(null, null);
    }

    private void saveGlobalProxyLocked(String proxySpec, String exclusionList) {
        if (exclusionList == null) {
            exclusionList = "";
        }
        if (proxySpec == null) {
            proxySpec = "";
        }
        // Remove white spaces
        proxySpec = proxySpec.trim();
        String data[] = proxySpec.split(":");
        int proxyPort = 8080;
        if (data.length > 1) {
            try {
                proxyPort = Integer.parseInt(data[1]);
            } catch (NumberFormatException e) {}
        }
        exclusionList = exclusionList.trim();

        ProxyInfo proxyProperties = ProxyInfo.buildDirectProxy(data[0], proxyPort,
                ProxyUtils.exclusionStringAsList(exclusionList));
        if (!proxyProperties.isValid()) {
            Slogf.e(LOG_TAG, "Invalid proxy properties, ignoring: " + proxyProperties.toString());
            return;
        }
        mInjector.settingsGlobalPutString(Settings.Global.GLOBAL_HTTP_PROXY_HOST, data[0]);
        mInjector.settingsGlobalPutInt(Settings.Global.GLOBAL_HTTP_PROXY_PORT, proxyPort);
        mInjector.settingsGlobalPutString(Settings.Global.GLOBAL_HTTP_PROXY_EXCLUSION_LIST,
                exclusionList);
    }

    /**
     * Called by an application that is administering the device to request that the storage system
     * be encrypted. Does nothing if the caller is on a secondary user or a managed profile.
     *
     * @return the new total request status (for all admins), or {@link
     *         DevicePolicyManager#ENCRYPTION_STATUS_UNSUPPORTED} if called for a non-system user
     */
    @Override
    public int setStorageEncryption(ComponentName who, boolean encrypt) {
        if (!mHasFeature) {
            return DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final int userHandle = UserHandle.getCallingUserId();
        synchronized (getLockObject()) {
            // Check for permissions
            // Only system user can set storage encryption
            if (userHandle != UserHandle.USER_SYSTEM) {
                Slogf.w(LOG_TAG, "Only owner/system user is allowed to set storage encryption. "
                        + "User " + UserHandle.getCallingUserId() + " is not permitted.");
                return DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED;
            }

            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_ENCRYPTED_STORAGE);

            // Quick exit:  If the filesystem does not support encryption, we can exit early.
            if (!isEncryptionSupported()) {
                return DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED;
            }

            // (1) Record the value for the admin so it's sticky
            if (ap.encryptionRequested != encrypt) {
                ap.encryptionRequested = encrypt;
                saveSettingsLocked(userHandle);
            }

            DevicePolicyData policy = getUserData(UserHandle.USER_SYSTEM);
            // (2) Compute "max" for all admins
            boolean newRequested = false;
            final int N = policy.mAdminList.size();
            for (int i = 0; i < N; i++) {
                newRequested |= policy.mAdminList.get(i).encryptionRequested;
            }

            // Notify OS of new request
            setEncryptionRequested(newRequested);

            // Return the new global request status
            return newRequested
                    ? DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE
                    : DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE;
        }
    }

    /**
     * Get the current storage encryption request status for a given admin, or aggregate of all
     * active admins.
     */
    @Override
    public boolean getStorageEncryption(@Nullable ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return false;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));

        synchronized (getLockObject()) {
            // Check for permissions if a particular caller is specified
            if (caller.hasAdminComponent()) {
                // When checking for a single caller, status is based on caller's request
                ActiveAdmin ap = getActiveAdminUncheckedLocked(who, userHandle);
                return ap != null ? ap.encryptionRequested : false;
            }

            // If no particular caller is specified, return the aggregate set of requests.
            // This is short circuited by returning true on the first hit.
            DevicePolicyData policy = getUserData(userHandle);
            final int N = policy.mAdminList.size();
            for (int i = 0; i < N; i++) {
                if (policy.mAdminList.get(i).encryptionRequested) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Get the current encryption status of the device.
     */
    @Override
    public int getStorageEncryptionStatus(@Nullable String callerPackage, int userHandle) {
        if (!mHasFeature) {
            // Ok to return current status.
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity(callerPackage);
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));


        final ApplicationInfo ai;
        try {
            ai = mIPackageManager.getApplicationInfo(callerPackage, 0, userHandle);
        } catch (RemoteException e) {
            throw new SecurityException(e);
        }

        boolean legacyApp = false;
        if (ai.targetSdkVersion <= Build.VERSION_CODES.M) {
            legacyApp = true;
        }

        final int rawStatus = getEncryptionStatus();
        if ((rawStatus == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER) && legacyApp) {
            return DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE;
        }
        return rawStatus;
    }

    /**
     * Hook to low-levels:  This should report if the filesystem supports encrypted storage.
     */
    private boolean isEncryptionSupported() {
        // Note, this can be implemented as
        //   return getEncryptionStatus() != DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED;
        // But is provided as a separate internal method if there's a faster way to do a
        // simple check for supported-or-not.
        return getEncryptionStatus() != DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED;
    }

    /**
     * Hook to low-levels:  Reporting the current status of encryption.
     * @return Either {@link DevicePolicyManager#ENCRYPTION_STATUS_UNSUPPORTED}
     * or {@link DevicePolicyManager#ENCRYPTION_STATUS_ACTIVE_PER_USER}.
     */
    private int getEncryptionStatus() {
        if (mInjector.storageManagerIsFileBasedEncryptionEnabled()) {
            return DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER;
        } else {
            return DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED;
        }
    }

    /**
     * Hook to low-levels:  If needed, record the new admin setting for encryption.
     */
    private void setEncryptionRequested(boolean encrypt) {
    }

    /**
     * Set whether the screen capture is disabled for the user managed by the specified admin.
     */
    @Override
    public void setScreenCaptureDisabled(ComponentName who, boolean disabled, boolean parent) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        if (parent) {
            Preconditions.checkCallAuthorization(isProfileOwnerOfOrganizationOwnedDevice(caller));
        } else {
            Preconditions.checkCallAuthorization(isProfileOwner(caller)
                    || isDeviceOwner(caller));
        }

        synchronized (getLockObject()) {
            ActiveAdmin ap = getParentOfAdminIfRequired(
                    getProfileOwnerOrDeviceOwnerLocked(caller.getUserId()), parent);
            if (ap.disableScreenCapture != disabled) {
                ap.disableScreenCapture = disabled;
                saveSettingsLocked(caller.getUserId());
                pushScreenCapturePolicy(caller.getUserId());
            }
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_SCREEN_CAPTURE_DISABLED)
                .setAdmin(caller.getComponentName())
                .setBoolean(disabled)
                .write();
    }

    // Push the screen capture policy for a given userId. If screen capture is disabled by the
    // DO or COPE PO on the parent profile, then this takes precedence as screen capture will
    // be disabled device-wide.
    private void pushScreenCapturePolicy(int adminUserId) {
        // Update screen capture device-wide if disabled by the DO or COPE PO on the parent profile.
        // TODO(b/261999445): remove
        ActiveAdmin admin;
        if (isHeadlessFlagEnabled()) {
            admin = getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceParentLocked(
                    mUserManagerInternal.getProfileParentId(adminUserId));
        } else {
            admin = getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceParentLocked(
                    UserHandle.USER_SYSTEM);
        }
        if (admin != null && admin.disableScreenCapture) {
            setScreenCaptureDisabled(UserHandle.USER_ALL);
        } else {
            // Otherwise, update screen capture only for the calling user.
            admin = getProfileOwnerAdminLocked(adminUserId);
            if (admin != null && admin.disableScreenCapture) {
                setScreenCaptureDisabled(adminUserId);
            } else {
                setScreenCaptureDisabled(UserHandle.USER_NULL);
            }
        }
    }

    // Set the latest screen capture policy, overriding any existing ones.
    // userHandle can be one of USER_ALL, USER_NULL or a concrete userId.
    private void setScreenCaptureDisabled(int userHandle) {
        int current = mPolicyCache.getScreenCaptureDisallowedUser();
        if (userHandle == current) {
            return;
        }
        mPolicyCache.setScreenCaptureDisallowedUser(userHandle);
        updateScreenCaptureDisabled();
    }

    /**
     * Returns whether or not screen capture is disabled for a given admin, or disabled for any
     * active admin (if given admin is null).
     */
    @Override
    public boolean getScreenCaptureDisabled(ComponentName who, int userHandle, boolean parent) {
        if (!mHasFeature) {
            return false;
        }
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));

        if (parent) {
            Preconditions.checkCallAuthorization(
                    isProfileOwnerOfOrganizationOwnedDevice(getCallerIdentity().getUserId()));
        }
        return !mPolicyCache.isScreenCaptureAllowed(userHandle);
    }

    private void updateScreenCaptureDisabled() {
        mHandler.post(() -> {
            try {
                mInjector.getIWindowManager().refreshScreenCaptureDisabled();
            } catch (RemoteException e) {
                Slogf.w(LOG_TAG, "Unable to notify WindowManager.", e);
            }
        });
    }

    @Override
    public void setNearbyNotificationStreamingPolicy(int policy) {
        if (!mHasFeature) {
            return;
        }

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            if (admin.mNearbyNotificationStreamingPolicy != policy) {
                admin.mNearbyNotificationStreamingPolicy = policy;
                saveSettingsLocked(caller.getUserId());
            }
        }
    }

    @Override
    public int getNearbyNotificationStreamingPolicy(final int userId) {
        if (!mHasFeature) {
            return NEARBY_STREAMING_NOT_CONTROLLED_BY_POLICY;
        }

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller)
                        || hasCallingOrSelfPermission(permission.READ_NEARBY_STREAMING_POLICY));
        Preconditions.checkCallAuthorization(hasCrossUsersPermission(caller, userId));

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getDeviceOrProfileOwnerAdminLocked(userId);
            return admin != null
                    ? admin.mNearbyNotificationStreamingPolicy
                    : NEARBY_STREAMING_NOT_CONTROLLED_BY_POLICY;
        }
    }

    @Override
    public void setNearbyAppStreamingPolicy(int policy) {
        if (!mHasFeature) {
            return;
        }

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            if (admin.mNearbyAppStreamingPolicy != policy) {
                admin.mNearbyAppStreamingPolicy = policy;
                saveSettingsLocked(caller.getUserId());
            }
        }
    }

    @Override
    public int getNearbyAppStreamingPolicy(final int userId) {
        if (!mHasFeature) {
            return NEARBY_STREAMING_NOT_CONTROLLED_BY_POLICY;
        }

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller)
                        || hasCallingOrSelfPermission(permission.READ_NEARBY_STREAMING_POLICY));
        Preconditions.checkCallAuthorization(hasCrossUsersPermission(caller, userId));

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getDeviceOrProfileOwnerAdminLocked(userId);
            return admin != null
                    ? admin.mNearbyAppStreamingPolicy
                    : NEARBY_STREAMING_NOT_CONTROLLED_BY_POLICY;
        }
    }

    /**
     * Set whether auto time is required by the specified admin (must be device or profile owner).
     */
    @Override
    public void setAutoTimeRequired(ComponentName who, boolean required) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDeviceOwner(caller) || isProfileOwner(caller));

        boolean requireAutoTimeChanged = false;
        synchronized (getLockObject()) {
            Preconditions.checkCallAuthorization(!isManagedProfile(caller.getUserId()),
                    "Managed profile cannot set auto time required");
            ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            if (admin.requireAutoTime != required) {
                admin.requireAutoTime = required;
                saveSettingsLocked(caller.getUserId());
                requireAutoTimeChanged = true;
            }
        }
        // requireAutoTime is now backed by DISALLOW_CONFIG_DATE_TIME restriction, so propagate
        // updated restrictions to the framework.
        if (requireAutoTimeChanged) {
            pushUserRestrictions(caller.getUserId());
        }
        // Turn AUTO_TIME on in settings if it is required
        if (required) {
            mInjector.binderWithCleanCallingIdentity(
                    () -> mInjector.settingsGlobalPutInt(Settings.Global.AUTO_TIME,
                            1 /* AUTO_TIME on */));
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_AUTO_TIME_REQUIRED)
                .setAdmin(who)
                .setBoolean(required)
                .write();
    }

    /**
     * Returns whether or not auto time is required by the device owner or any profile owner.
     */
    @Override
    public boolean getAutoTimeRequired() {
        if (!mHasFeature) {
            return false;
        }
        synchronized (getLockObject()) {
            ActiveAdmin deviceOwner = getDeviceOwnerAdminLocked();
            if (deviceOwner != null && deviceOwner.requireAutoTime) {
                // If the device owner enforces auto time, we don't need to check the PO's
                return true;
            }

            // Now check to see if any profile owner on any user enforces auto time
            for (Integer userId : mOwners.getProfileOwnerKeys()) {
                ActiveAdmin profileOwner = getProfileOwnerAdminLocked(userId);
                if (profileOwner != null && profileOwner.requireAutoTime) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Set whether auto time is enabled on the device.
     */
    @Override
    public void setAutoTimeEnabled(ComponentName who, boolean enabled) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);

        if (isPermissionCheckFlagEnabled()) {
            // The effect of this policy is device-wide.
            enforcePermission(SET_TIME, UserHandle.USER_ALL);
        } else {
            Preconditions.checkCallAuthorization(isProfileOwnerOnUser0(caller)
                    || isProfileOwnerOfOrganizationOwnedDevice(caller) || isDefaultDeviceOwner(
                    caller));
        }
        mInjector.binderWithCleanCallingIdentity(() ->
                mInjector.settingsGlobalPutInt(Settings.Global.AUTO_TIME, enabled ? 1 : 0));

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_AUTO_TIME)
                .setAdmin(caller.getComponentName())
                .setBoolean(enabled)
                .write();
    }

    /**
     * Returns whether auto time is used on the device or not.
     */
    @Override
    public boolean getAutoTimeEnabled(ComponentName who) {
        if (!mHasFeature) {
            return false;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);

        if (isPermissionCheckFlagEnabled()) {
            enforceCanQuery(SET_TIME, UserHandle.USER_ALL);
        } else {
            Preconditions.checkCallAuthorization(isProfileOwnerOnUser0(caller)
                    || isProfileOwnerOfOrganizationOwnedDevice(caller) || isDefaultDeviceOwner(
                    caller));
        }

        return mInjector.settingsGlobalGetInt(Global.AUTO_TIME, 0) > 0;
    }

    /**
     * Set whether auto time zone is enabled on the device.
     */
    @Override
    public void setAutoTimeZoneEnabled(ComponentName who, boolean enabled) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);

        if (isPermissionCheckFlagEnabled()) {
            // The effect of this policy is device-wide.
            enforcePermission(SET_TIME_ZONE, UserHandle.USER_ALL);
        } else {
            Preconditions.checkCallAuthorization(isProfileOwnerOnUser0(caller)
                    || isProfileOwnerOfOrganizationOwnedDevice(caller) || isDefaultDeviceOwner(
                    caller));
        }

        if (isCoexistenceEnabled(caller)) {
            mDevicePolicyEngine.setGlobalPolicy(
                    PolicyDefinition.AUTO_TIMEZONE,
                    // TODO(b/260573124): add correct enforcing admin when permission changes are
                    //  merged.
                    EnforcingAdmin.createEnterpriseEnforcingAdmin(
                            caller.getComponentName(), caller.getUserId()),
                    enabled);
        } else {
            mInjector.binderWithCleanCallingIdentity(() ->
                    mInjector.settingsGlobalPutInt(Global.AUTO_TIME_ZONE, enabled ? 1 : 0));
        }

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_AUTO_TIME_ZONE)
                .setAdmin(caller.getComponentName())
                .setBoolean(enabled)
                .write();
    }

    /**
     * Returns whether auto time zone is used on the device or not.
     */
    @Override
    public boolean getAutoTimeZoneEnabled(ComponentName who) {
        if (!mHasFeature) {
            return false;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);

        if (isPermissionCheckFlagEnabled()) {
            // The effect of this policy is device-wide.
            enforceCanQuery(SET_TIME_ZONE, UserHandle.USER_ALL);
        } else {
            Preconditions.checkCallAuthorization(isProfileOwnerOnUser0(caller)
                    || isProfileOwnerOfOrganizationOwnedDevice(caller) || isDefaultDeviceOwner(
                    caller));
        }

        return mInjector.settingsGlobalGetInt(Global.AUTO_TIME_ZONE, 0) > 0;
    }

    // TODO (b/137101239): remove this method in follow-up CL
    // since it's only used for split system user.
    @Override
    public void setForceEphemeralUsers(ComponentName who, boolean forceEphemeralUsers) {
        throw new UnsupportedOperationException("This method was used by split system user only.");
    }

    // TODO (b/137101239): remove this method in follow-up CL
    // since it's only used for split system user.
    @Override
    public boolean getForceEphemeralUsers(ComponentName who) {
        throw new UnsupportedOperationException("This method was used by split system user only.");
    }

    @Override
    public boolean requestBugreport(ComponentName who) {
        if (!mHasFeature) {
            return false;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        // TODO: If an unaffiliated user is removed, the admin will be able to request a bugreport
        // which could still contain data related to that user. Should we disallow that, e.g. until
        // next boot? Might not be needed given that this still requires user consent.
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        checkAllUsersAreAffiliatedWithDevice();
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_REQUEST_BUGREPORT);

        if (mBugreportCollectionManager.requestBugreport()) {
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.REQUEST_BUGREPORT)
                    .setAdmin(who)
                    .write();

            final long currentTime = System.currentTimeMillis();
            synchronized (getLockObject()) {
                DevicePolicyData policyData = getUserData(UserHandle.USER_SYSTEM);
                if (currentTime > policyData.mLastBugReportRequestTime) {
                    policyData.mLastBugReportRequestTime = currentTime;
                    saveSettingsLocked(UserHandle.USER_SYSTEM);
                }
            }

            return true;
        } else {
            return false;
        }
    }

    void sendDeviceOwnerCommand(String action, Bundle extras) {
        final int deviceOwnerUserId;
        final ComponentName receiverComponent;
        synchronized (getLockObject()) {
            deviceOwnerUserId = mOwners.getDeviceOwnerUserId();
            receiverComponent = mOwners.getDeviceOwnerComponent();
        }
        sendActiveAdminCommand(action, extras, deviceOwnerUserId, receiverComponent,
                /* inForeground */ false);
    }

    void sendDeviceOwnerOrProfileOwnerCommand(String action, Bundle extras, int userId) {
        if (userId == UserHandle.USER_ALL) {
            userId = UserHandle.USER_SYSTEM;
        }
        boolean inForeground = false;
        ComponentName receiverComponent = null;
        if (action.equals(DeviceAdminReceiver.ACTION_NETWORK_LOGS_AVAILABLE)) {
            inForeground = true;
            receiverComponent = resolveDelegateReceiver(DELEGATION_NETWORK_LOGGING, action, userId);
        }
        if (action.equals(DeviceAdminReceiver.ACTION_SECURITY_LOGS_AVAILABLE)) {
            inForeground = true;
            receiverComponent = resolveDelegateReceiver(
                DELEGATION_SECURITY_LOGGING, action, userId);
        }
        if (receiverComponent == null) {
            receiverComponent = getOwnerComponent(userId);
        }
        sendActiveAdminCommand(action, extras, userId, receiverComponent, inForeground);
    }

    private void sendProfileOwnerCommand(String action, Bundle extras, @UserIdInt int userId) {
        sendActiveAdminCommand(action, extras, userId, mOwners.getProfileOwnerComponent(userId),
                /* inForeground */ false);
    }

    private void sendActiveAdminCommand(String action, Bundle extras,
            @UserIdInt int userId, ComponentName receiverComponent, boolean inForeground) {
        final Intent intent = new Intent(action);
        intent.setComponent(receiverComponent);
        if (extras != null) {
            intent.putExtras(extras);
        }
        if (inForeground) {
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        }

        if (VERBOSE_LOG) {
            Slogf.v(LOG_TAG, "sendActiveAdminCommand(): broadcasting " + action + " to "
                    + receiverComponent.flattenToShortString() + " on user " + userId);
        }
        mContext.sendBroadcastAsUser(intent, UserHandle.of(userId));
    }

    private void sendOwnerChangedBroadcast(String broadcast, int userId) {
        final Intent intent = new Intent(broadcast)
                .addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        mContext.sendBroadcastAsUser(intent, UserHandle.of(userId));
    }

    void sendBugreportToDeviceOwner(Uri bugreportUri, String bugreportHash) {
        synchronized (getLockObject()) {
            final Intent intent = new Intent(DeviceAdminReceiver.ACTION_BUGREPORT_SHARE);
            intent.setComponent(mOwners.getDeviceOwnerComponent());
            intent.setDataAndType(bugreportUri, RemoteBugreportManager.BUGREPORT_MIMETYPE);
            intent.putExtra(DeviceAdminReceiver.EXTRA_BUGREPORT_HASH, bugreportHash);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            final UriGrantsManagerInternal ugm = LocalServices
                    .getService(UriGrantsManagerInternal.class);
            final NeededUriGrants needed = ugm.checkGrantUriPermissionFromIntent(intent,
                    Process.SHELL_UID, mOwners.getDeviceOwnerComponent().getPackageName(),
                    mOwners.getDeviceOwnerUserId());
            ugm.grantUriPermissionUncheckedFromIntent(needed, null);

            mContext.sendBroadcastAsUser(intent, UserHandle.of(mOwners.getDeviceOwnerUserId()));
        }
    }

    void setDeviceOwnerRemoteBugreportUriAndHash(String bugreportUri, String bugreportHash) {
        synchronized (getLockObject()) {
            mOwners.setDeviceOwnerRemoteBugreportUriAndHash(bugreportUri, bugreportHash);
        }
    }

    Pair<String, String> getDeviceOwnerRemoteBugreportUriAndHash() {
        synchronized (getLockObject()) {
            final String uri = mOwners.getDeviceOwnerRemoteBugreportUri();
            return uri == null ? null
                    : new Pair<>(uri, mOwners.getDeviceOwnerRemoteBugreportHash());
        }
    }

    /**
     * Disables all device cameras according to the specified admin.
     */
    @Override
    public void setCameraDisabled(ComponentName who, boolean disabled, boolean parent) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        if (parent) {
            Preconditions.checkCallAuthorization(isProfileOwnerOfOrganizationOwnedDevice(caller));
        }
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_CAMERA_DISABLED);

        final int userHandle = caller.getUserId();
        synchronized (getLockObject()) {
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_DISABLE_CAMERA, parent);
            if (ap.disableCamera != disabled) {
                ap.disableCamera = disabled;
                saveSettingsLocked(userHandle);
            }
        }
        // Tell the user manager that the restrictions have changed.
        pushUserRestrictions(userHandle);

        final int affectedUserId = parent ? getProfileParentId(userHandle) : userHandle;
        if (SecurityLog.isLoggingEnabled()) {
            SecurityLog.writeEvent(SecurityLog.TAG_CAMERA_POLICY_SET,
                    who.getPackageName(), userHandle, affectedUserId, disabled ? 1 : 0);
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_CAMERA_DISABLED)
                .setAdmin(caller.getComponentName())
                .setBoolean(disabled)
                .setStrings(parent ? CALLED_FROM_PARENT : NOT_CALLED_FROM_PARENT)
                .write();
    }

    /**
     * Gets whether or not all device cameras are disabled for a given admin, or disabled for any
     * active admins.
     */
    @Override
    public boolean getCameraDisabled(ComponentName who, int userHandle, boolean parent) {
        if (!mHasFeature) {
            return false;
        }

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle)
                || isCameraServerUid(caller));

        if (parent) {
            Preconditions.checkCallAuthorization(
                    isProfileOwnerOfOrganizationOwnedDevice(caller.getUserId()));
        }

        synchronized (getLockObject()) {
            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle, parent);
                return (admin != null) && admin.disableCamera;
            }
            // First, see if DO has set it.  If so, it's device-wide.
            final ActiveAdmin deviceOwner = getDeviceOwnerAdminLocked();
            if (deviceOwner != null && deviceOwner.disableCamera) {
                return true;
            }
            final int affectedUserId = parent ? getProfileParentId(userHandle) : userHandle;
            // Return the strictest policy across all participating admins.
            List<ActiveAdmin> admins = getActiveAdminsForAffectedUserLocked(affectedUserId);
            // Determine whether or not the device camera is disabled for any active admins.
            for (ActiveAdmin admin : admins) {
                if (admin.disableCamera) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public void setKeyguardDisabledFeatures(ComponentName who, int which, boolean parent) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);

        final int userHandle = caller.getUserId();
        synchronized (getLockObject()) {
            ActiveAdmin ap = getActiveAdminForCallerLocked(
                    who, DeviceAdminInfo.USES_POLICY_DISABLE_KEYGUARD_FEATURES, parent);
            if (isManagedProfile(userHandle)) {
                if (parent) {
                    if (isProfileOwnerOfOrganizationOwnedDevice(caller)) {
                        which = which & PROFILE_KEYGUARD_FEATURES_AFFECT_OWNER;
                    } else {
                        which = which & NON_ORG_OWNED_PROFILE_KEYGUARD_FEATURES_AFFECT_OWNER;
                    }
                } else {
                    which = which & PROFILE_KEYGUARD_FEATURES;
                }
            }
            if (ap.disabledKeyguardFeatures != which) {
                ap.disabledKeyguardFeatures = which;
                saveSettingsLocked(userHandle);
            }
        }
        if (SecurityLog.isLoggingEnabled()) {
            final int affectedUserId = parent ? getProfileParentId(userHandle) : userHandle;
            SecurityLog.writeEvent(SecurityLog.TAG_KEYGUARD_DISABLED_FEATURES_SET,
                    who.getPackageName(), userHandle, affectedUserId, which);
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_KEYGUARD_DISABLED_FEATURES)
                .setAdmin(caller.getComponentName())
                .setInt(which)
                .setStrings(parent ? CALLED_FROM_PARENT : NOT_CALLED_FROM_PARENT)
                .write();
    }

    /**
     * Gets the disabled state for features in keyguard for the given admin,
     * or the aggregate of all active admins if who is null.
     * This API is cached: invalidate with invalidateBinderCaches().
     */
    @Override
    public int getKeyguardDisabledFeatures(ComponentName who, int userHandle, boolean parent) {
        if (!mHasFeature) {
            return 0;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        Preconditions.checkCallAuthorization(
                who == null || isCallingFromPackage(who.getPackageName(), caller.getUid())
                        || isSystemUid(caller));

        final long ident = mInjector.binderClearCallingIdentity();
        try {
            synchronized (getLockObject()) {
                if (who != null) {
                    ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle, parent);
                    return (admin != null) ? admin.disabledKeyguardFeatures : 0;
                }

                final List<ActiveAdmin> admins;
                if (!parent && isManagedProfile(userHandle)) {
                    // If we are being asked about a managed profile, just return keyguard features
                    // disabled by admins in the profile.
                    admins = getUserDataUnchecked(userHandle).mAdminList;
                } else {
                    // Otherwise return those set by admins in the user and its profiles.
                    admins = getActiveAdminsForLockscreenPoliciesLocked(
                            getProfileParentUserIfRequested(userHandle, parent));
                }

                int which = DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE;
                final int N = admins.size();
                for (int i = 0; i < N; i++) {
                    ActiveAdmin admin = admins.get(i);
                    int userId = admin.getUserHandle().getIdentifier();
                    boolean isRequestedUser = !parent && (userId == userHandle);
                    if (isRequestedUser || !isManagedProfile(userId)) {
                        // If we are being asked explicitly about this user
                        // return all disabled features even if its a managed profile.
                        which |= admin.disabledKeyguardFeatures;
                    } else {
                        // Otherwise a managed profile is only allowed to disable
                        // some features on the parent user.
                        which |= (admin.disabledKeyguardFeatures
                                & PROFILE_KEYGUARD_FEATURES_AFFECT_OWNER);
                    }
                }
                return which;
            }
        } finally {
            mInjector.binderRestoreCallingIdentity(ident);
        }
    }

    @Override
    public void setKeepUninstalledPackages(ComponentName who, String callerPackage,
            List<String> packageList) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(packageList, "packageList is null");
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                &&  isDefaultDeviceOwner(caller))
                || (caller.hasPackage()
                && isCallerDelegate(caller, DELEGATION_KEEP_UNINSTALLED_PACKAGES)));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_KEEP_UNINSTALLED_PACKAGES);

        synchronized (getLockObject()) {
            // Get the device owner
            ActiveAdmin deviceOwner = getDeviceOwnerAdminLocked();
            // Set list of packages to be kept even if uninstalled.
            deviceOwner.keepUninstalledPackages = packageList;
            // Save settings.
            saveSettingsLocked(caller.getUserId());
            // Notify package manager.
            mInjector.getPackageManagerInternal().setKeepUninstalledPackages(packageList);
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_KEEP_UNINSTALLED_PACKAGES)
                .setAdmin(caller.getPackageName())
                .setBoolean(/* isDelegate */ who == null)
                .setStrings(packageList.toArray(new String[0]))
                .write();
    }

    @Override
    public List<String> getKeepUninstalledPackages(ComponentName who, String callerPackage) {
        if (!mHasFeature) {
            return null;
        }
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                &&  isDefaultDeviceOwner(caller))
                || (caller.hasPackage()
                && isCallerDelegate(caller, DELEGATION_KEEP_UNINSTALLED_PACKAGES)));

        synchronized (getLockObject()) {
            return getKeepUninstalledPackagesLocked();
        }
    }

    private List<String> getKeepUninstalledPackagesLocked() {
        ActiveAdmin deviceOwner = getDeviceOwnerAdminLocked();
        return (deviceOwner != null) ? deviceOwner.keepUninstalledPackages : null;
    }

    /**
     * Logs a warning when the device doesn't have {@code PackageManager.FEATURE_DEVICE_ADMIN}.
     *
     * @param message action that was not executed; should not end with a period because the missing
     * feature will be appended to it.
     */
    private void logMissingFeatureAction(String message) {
        Slogf.w(LOG_TAG, message + " because device does not have the "
                + PackageManager.FEATURE_DEVICE_ADMIN + " feature.");
    }

    @Override
    public boolean setDeviceOwner(ComponentName admin, int userId,
            boolean setProfileOwnerOnCurrentUserIfNecessary) {
        if (!mHasFeature) {
            logMissingFeatureAction("Cannot set " + ComponentName.flattenToShortString(admin)
                    + " as device owner for user " + userId);
            return false;
        }
        Preconditions.checkArgument(admin != null);

        final CallerIdentity caller = getCallerIdentity();

        boolean hasIncompatibleAccountsOrNonAdb =
                !isAdb(caller) || hasIncompatibleAccountsOnAnyUser();

        if (!hasIncompatibleAccountsOrNonAdb) {
            synchronized (getLockObject()) {
                if (!isAdminTestOnlyLocked(admin, userId) && hasAccountsOnAnyUser()) {
                    Slogf.w(LOG_TAG,
                            "Non test-only owner can't be installed with existing accounts.");
                    return false;
                }
            }
        }

        synchronized (getLockObject()) {
            enforceCanSetDeviceOwnerLocked(caller, admin, userId, hasIncompatibleAccountsOrNonAdb);
            Preconditions.checkArgument(isPackageInstalledForUser(admin.getPackageName(), userId),
                    "Invalid component " + admin + " for device owner");
            final ActiveAdmin activeAdmin = getActiveAdminUncheckedLocked(admin, userId);
            Preconditions.checkArgument(activeAdmin != null && !getUserData(
                    userId).mRemovingAdmins.contains(admin), "Not active admin: " + admin);

            // Shutting down backup manager service permanently.
            toggleBackupServiceActive(UserHandle.USER_SYSTEM, /* makeActive= */ false);
            if (isAdb(caller)) {
                // Log device owner provisioning was started using adb.
                MetricsLogger.action(mContext, PROVISIONING_ENTRY_POINT_ADB, LOG_TAG_DEVICE_OWNER);
                DevicePolicyEventLogger
                        .createEvent(DevicePolicyEnums.PROVISIONING_ENTRY_POINT_ADB)
                        .setAdmin(admin)
                        .setStrings(LOG_TAG_DEVICE_OWNER)
                        .write();
            }

            mOwners.setDeviceOwner(admin, userId);
            mOwners.writeDeviceOwner();
            setDeviceOwnershipSystemPropertyLocked();

            //TODO(b/180371154): when provisionFullyManagedDevice is used in tests, remove this
            // hard-coded default value setting.
            if (isAdb(caller)) {
                activeAdmin.mAdminCanGrantSensorsPermissions = true;
                mPolicyCache.setAdminCanGrantSensorsPermissions(true);
                saveSettingsLocked(userId);
            }

            mInjector.binderWithCleanCallingIdentity(() -> {
                // Restrict adding a managed profile when a device owner is set on the device.
                // That is to prevent the co-existence of a managed profile and a device owner
                // on the same device.
                // Instead, the device may be provisioned with an organization-owned managed
                // profile, such that the admin on that managed profile has extended management
                // capabilities that can affect the entire device (but not access private data
                // on the primary profile).
                if (isHeadlessFlagEnabled()) {
                    for (int u : mUserManagerInternal.getUserIds()) {
                        mUserManager.setUserRestriction(
                                UserManager.DISALLOW_ADD_MANAGED_PROFILE, true,
                                UserHandle.of(u));
                        // Restrict adding a clone profile when a device owner is set on the device.
                        // That is to prevent the co-existence of a clone profile and a device owner
                        // on the same device.
                        // CDD for reference : https://source.android.com/compatibility/12/android-12-cdd#95_multi-user_support
                        mUserManager.setUserRestriction(UserManager.DISALLOW_ADD_CLONE_PROFILE,
                                true,
                                UserHandle.of(u));
                    }
                } else {
                    mUserManager.setUserRestriction(UserManager.DISALLOW_ADD_MANAGED_PROFILE,
                            true,
                            UserHandle.of(userId));
                    // Restrict adding a clone profile when a device owner is set on the device.
                    // That is to prevent the co-existence of a clone profile and a device owner
                    // on the same device.
                    // CDD for reference : https://source.android.com/compatibility/12/android-12-cdd#95_multi-user_support
                    mUserManager.setUserRestriction(UserManager.DISALLOW_ADD_CLONE_PROFILE,
                            true,
                            UserHandle.of(userId));
                }
                // TODO Send to system too?
                sendOwnerChangedBroadcast(DevicePolicyManager.ACTION_DEVICE_OWNER_CHANGED, userId);
            });
            mDeviceAdminServiceController.startServiceForAdmin(
                    admin.getPackageName(), userId, "set-device-owner");

            Slogf.i(LOG_TAG, "Device owner set: " + admin + " on user " + userId);
        }

        if (setProfileOwnerOnCurrentUserIfNecessary
                && mInjector.userManagerIsHeadlessSystemUserMode()) {
            int currentForegroundUser;
            synchronized (getLockObject()) {
                currentForegroundUser = getCurrentForegroundUserId();
            }
            Slogf.i(LOG_TAG, "setDeviceOwner(): setting " + admin
                    + " as profile owner on user " + currentForegroundUser);
            // Sets profile owner on current foreground user since
            // the human user will complete the DO setup workflow from there.
            manageUserUnchecked(/* deviceOwner= */ admin, /* profileOwner= */ admin,
                    /* managedUser= */ currentForegroundUser, /* adminExtras= */ null,
                    /* showDisclaimer= */ false);
        }
        return true;
    }

    /**
     * This API is cached: invalidate with invalidateBinderCaches().
     */
    @Override
    public boolean hasDeviceOwner() {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                        || canManageUsers(caller) || isFinancedDeviceOwner(caller)
                        || hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));
        return mOwners.hasDeviceOwner();
    }

    boolean isDeviceOwner(ActiveAdmin admin) {
        return isDeviceOwner(admin.info.getComponent(), admin.getUserHandle().getIdentifier());
    }

    public boolean isDeviceOwner(ComponentName who, int userId) {
        synchronized (getLockObject()) {
            return mOwners.hasDeviceOwner()
                    && mOwners.getDeviceOwnerUserId() == userId
                    && mOwners.getDeviceOwnerComponent().equals(who);
        }
    }

    /**
     * Returns {@code true} <b>only if</b> the caller is the device owner and the device owner type
     * is {@link DevicePolicyManager#DEVICE_OWNER_TYPE_DEFAULT}. {@code false} is returned for the
     * case where the caller is not the device owner, there is no device owner, or the device owner
     * type is not {@link DevicePolicyManager#DEVICE_OWNER_TYPE_DEFAULT}.
     *
     */
    private boolean isDefaultDeviceOwner(CallerIdentity caller) {
        synchronized (getLockObject()) {
            return isDeviceOwnerLocked(caller) && getDeviceOwnerTypeLocked(
                    mOwners.getDeviceOwnerPackageName()) == DEVICE_OWNER_TYPE_DEFAULT;
        }
    }

    /**
     * Returns {@code true} if the provided caller identity is of a device owner.
     * @param caller identity of caller.
     * @return true if {@code identity} is a device owner, false otherwise.
     */
    public boolean isDeviceOwner(CallerIdentity caller) {
        synchronized (getLockObject()) {
            return isDeviceOwnerLocked(caller);
        }
    }

    private boolean isDeviceOwnerLocked(CallerIdentity caller) {
        if (!mOwners.hasDeviceOwner() || mOwners.getDeviceOwnerUserId() != caller.getUserId()) {
            return false;
        }

        if (caller.hasAdminComponent()) {
            return mOwners.getDeviceOwnerComponent().equals(caller.getComponentName());
        } else {
            return isUidDeviceOwnerLocked(caller.getUid());
        }
    }

    private boolean isDeviceOwnerUserId(int userId) {
        synchronized (getLockObject()) {
            return mOwners.getDeviceOwnerComponent() != null
                    && mOwners.getDeviceOwnerUserId() == userId;
        }
    }

    public boolean isProfileOwner(ComponentName who, int userId) {
        final ComponentName profileOwner = mInjector.binderWithCleanCallingIdentity(() ->
                getProfileOwnerAsUser(userId));
        return who != null && who.equals(profileOwner);
    }

    /**
     * Returns {@code true} if the provided caller identity is of a profile owner.
     * @param caller identity of caller.
     * @return true if {@code identity} is a profile owner, false otherwise.
     */
    public boolean isProfileOwner(CallerIdentity caller) {
        synchronized (getLockObject()) {
            final ComponentName profileOwner = mInjector.binderWithCleanCallingIdentity(() ->
                    getProfileOwnerAsUser(caller.getUserId()));
            // No profile owner.
            if (profileOwner == null) {
                return false;
            }
            // The admin ComponentName was specified, check it directly.
            if (caller.hasAdminComponent()) {
                return profileOwner.equals(caller.getComponentName());
            } else {
                return isUidProfileOwnerLocked(caller.getUid());
            }
        }
    }

    /**
     * Checks if the app uid provided is the profile owner. This method should only be called
     * if no componentName is available.
     *
     * @param appUid UID of the caller.
     * @return true if the caller is the profile owner
     */
    private boolean isUidProfileOwnerLocked(int appUid) {
        ensureLocked();

        final int userId = UserHandle.getUserId(appUid);
        final ComponentName profileOwnerComponent = mOwners.getProfileOwnerComponent(userId);
        if (profileOwnerComponent == null) {
            return false;
        }
        for (ActiveAdmin admin : getUserData(userId).mAdminList) {
            final ComponentName currentAdminComponent = admin.info.getComponent();
            if (admin.getUid() == appUid && profileOwnerComponent.equals(currentAdminComponent)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasProfileOwner(int userId) {
        synchronized (getLockObject()) {
            return mOwners.hasProfileOwner(userId);
        }
    }

    /**
     * Returns {@code true} if the provided caller identity is of a profile owner of an organization
     * owned device.
     *
     * @param caller identity of caller
     * @return true if {@code identity} is a profile owner of an organization owned device, false
     * otherwise.
     */
    private boolean isProfileOwnerOfOrganizationOwnedDevice(CallerIdentity caller) {
        return isProfileOwner(caller) && isProfileOwnerOfOrganizationOwnedDevice(
                caller.getUserId());
    }

    private boolean isProfileOwnerOfOrganizationOwnedDevice(int userId) {
        synchronized (getLockObject()) {
            return mOwners.isProfileOwnerOfOrganizationOwnedDevice(userId);
        }
    }

    private boolean isProfileOwnerOfOrganizationOwnedDevice(ComponentName who, int userId) {
        return isProfileOwner(who, userId) && isProfileOwnerOfOrganizationOwnedDevice(userId);
    }

    private boolean isProfileOwnerOnUser0(CallerIdentity caller) {
        return isProfileOwner(caller) && caller.getUserHandle().isSystem();
    }

    private boolean isPackage(CallerIdentity caller, String packageName) {
        return isCallingFromPackage(packageName, caller.getUid());
    }

    @Override
    public ComponentName getDeviceOwnerComponent(boolean callingUserOnly) {
        if (!mHasFeature) {
            return null;
        }
        if (!callingUserOnly) {
            Preconditions.checkCallAuthorization(canManageUsers(getCallerIdentity())
                    || hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));
        }
        synchronized (getLockObject()) {
            if (!mOwners.hasDeviceOwner()) {
                return null;
            }
            if (callingUserOnly && mInjector.userHandleGetCallingUserId() !=
                    mOwners.getDeviceOwnerUserId()) {
                return null;
            }
            return mOwners.getDeviceOwnerComponent();
        }
    }

    private int getDeviceOwnerUserIdUncheckedLocked() {
        return mOwners.hasDeviceOwner() ? mOwners.getDeviceOwnerUserId() : UserHandle.USER_NULL;
    }

    @Override
    public int getDeviceOwnerUserId() {
        if (!mHasFeature) {
            return UserHandle.USER_NULL;
        }
        Preconditions.checkCallAuthorization(canManageUsers(getCallerIdentity()));

        synchronized (getLockObject()) {
            return getDeviceOwnerUserIdUncheckedLocked();
        }
    }

    private @UserIdInt int getMainUserId() {
        UserHandle mainUser = mUserManager.getMainUser();
        if (mainUser == null) {
            Slogf.d(LOG_TAG, "getMainUserId(): no main user, returning USER_SYSTEM");
            return UserHandle.USER_SYSTEM;
        }
        return mainUser.getIdentifier();
    }

    // TODO(b/240562946): Remove api as owner name is not used.
    /**
     * Returns the "name" of the device owner.  It'll work for non-DO users too, but requires
     * MANAGE_USERS.
     */
    @Override
    public String getDeviceOwnerName() {
        if (!mHasFeature) {
            return null;
        }
        Preconditions.checkCallAuthorization(canManageUsers(getCallerIdentity())
                || hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        synchronized (getLockObject()) {
            if (!mOwners.hasDeviceOwner()) {
                return null;
            }
            // TODO This totally ignores the name passed to setDeviceOwner (change for b/20679292)
            // Should setDeviceOwner/ProfileOwner still take a name?
            String deviceOwnerPackage = mOwners.getDeviceOwnerPackageName();
            return getApplicationLabel(deviceOwnerPackage, UserHandle.USER_SYSTEM);
        }
    }

    /** Returns the active device owner or {@code null} if there is no device owner. */
    @VisibleForTesting
    ActiveAdmin getDeviceOwnerAdminLocked() {
        ensureLocked();
        ComponentName component = mOwners.getDeviceOwnerComponent();
        if (component == null) {
            return null;
        }

        DevicePolicyData policy = getUserData(mOwners.getDeviceOwnerUserId());
        final int n = policy.mAdminList.size();
        for (int i = 0; i < n; i++) {
            ActiveAdmin admin = policy.mAdminList.get(i);
            if (component.equals(admin.info.getComponent())) {
                return admin;
            }
        }
        Slogf.wtf(LOG_TAG, "Active admin for device owner not found. component=" + component);
        return null;
    }

    /**
     * @deprecated Use the version which does not take a user id.
     */
    @Deprecated
    ActiveAdmin getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked(int userId) {
        ensureLocked();
        ActiveAdmin admin = getDeviceOwnerAdminLocked();
        if (admin == null) {
            admin = getProfileOwnerOfOrganizationOwnedDeviceLocked(userId);
        }
        return admin;
    }

    ActiveAdmin getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked() {
        ensureLocked();
        ActiveAdmin admin = getDeviceOwnerAdminLocked();
        if (admin == null) {
            admin = getProfileOwnerOfOrganizationOwnedDeviceLocked();
        }
        return admin;
    }

    ActiveAdmin getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceParentLocked(int userId) {
        ensureLocked();
        ActiveAdmin admin = getDeviceOwnerAdminLocked();
        if (admin != null) {
            return admin;
        }
        admin = getProfileOwnerOfOrganizationOwnedDeviceLocked(userId);
        return admin != null ? admin.getParentActiveAdmin() : null;
    }

    @Override
    public void clearDeviceOwner(String packageName) {
        Objects.requireNonNull(packageName, "packageName is null");

        final CallerIdentity caller = getCallerIdentity(packageName);
        synchronized (getLockObject()) {
            final ComponentName deviceOwnerComponent = mOwners.getDeviceOwnerComponent();
            final int deviceOwnerUserId = mOwners.getDeviceOwnerUserId();
            if (!mOwners.hasDeviceOwner()
                    || !deviceOwnerComponent.getPackageName().equals(packageName)
                    || (deviceOwnerUserId != caller.getUserId())) {
                throw new SecurityException(
                        "clearDeviceOwner can only be called by the device owner");
            }
            enforceUserUnlocked(deviceOwnerUserId);
            DevicePolicyData policy = getUserData(deviceOwnerUserId);
            if (policy.mPasswordTokenHandle != 0) {
                mLockPatternUtils.removeEscrowToken(policy.mPasswordTokenHandle, deviceOwnerUserId);
            }

            final ActiveAdmin admin = getDeviceOwnerAdminLocked();
            mInjector.binderWithCleanCallingIdentity(() -> {
                clearDeviceOwnerLocked(admin, deviceOwnerUserId);
                removeActiveAdminLocked(deviceOwnerComponent, deviceOwnerUserId);
                sendOwnerChangedBroadcast(DevicePolicyManager.ACTION_DEVICE_OWNER_CHANGED,
                        deviceOwnerUserId);
            });
            Slogf.i(LOG_TAG, "Device owner removed: " + deviceOwnerComponent);
        }
    }

    private void clearOverrideApnUnchecked() {
        if (!mHasTelephonyFeature) {
            return;
        }
        // Disable Override APNs and remove them from database.
        setOverrideApnsEnabledUnchecked(false);
        final List<ApnSetting> apns = getOverrideApnsUnchecked();
        for (int i = 0; i < apns.size(); i ++) {
            removeOverrideApnUnchecked(apns.get(i).getId());
        }
    }

    private void clearManagedProfileApnUnchecked() {
        if (!mHasTelephonyFeature) {
            return;
        }
        final List<ApnSetting> apns = getOverrideApnsUnchecked();
        for (ApnSetting apn : apns) {
            if (apn.getApnTypeBitmask() == ApnSetting.TYPE_ENTERPRISE) {
                removeOverrideApnUnchecked(apn.getId());
            }
        }
    }

    private void clearDeviceOwnerLocked(ActiveAdmin admin, int userId) {
        String ownersPackage = mOwners.getDeviceOwnerPackageName();
        if (ownersPackage != null) {
            mDeviceAdminServiceController.stopServiceForAdmin(
                    ownersPackage, userId, "clear-device-owner");
        }

        if (admin != null) {
            admin.disableCamera = false;
            admin.userRestrictions = null;
            admin.defaultEnabledRestrictionsAlreadySet.clear();
            admin.forceEphemeralUsers = false;
            admin.isNetworkLoggingEnabled = false;
            admin.requireAutoTime = false;
            mUserManagerInternal.setForceEphemeralUsers(admin.forceEphemeralUsers);
        }
        final DevicePolicyData policyData = getUserData(userId);
        policyData.mCurrentInputMethodSet = false;
        saveSettingsLocked(userId);
        mPolicyCache.onUserRemoved(userId);
        final DevicePolicyData systemPolicyData = getUserData(UserHandle.USER_SYSTEM);
        systemPolicyData.mLastSecurityLogRetrievalTime = -1;
        systemPolicyData.mLastBugReportRequestTime = -1;
        systemPolicyData.mLastNetworkLogsRetrievalTime = -1;
        saveSettingsLocked(UserHandle.USER_SYSTEM);
        clearUserPoliciesLocked(userId);
        clearOverrideApnUnchecked();
        clearApplicationRestrictions(userId);
        mInjector.getPackageManagerInternal().clearBlockUninstallForUser(userId);

        mOwners.clearDeviceOwner();
        mOwners.writeDeviceOwner();

        clearDeviceOwnerUserRestriction(UserHandle.of(userId));
        mInjector.securityLogSetLoggingEnabledProperty(false);
        mSecurityLogMonitor.stop();
        setNetworkLoggingActiveInternal(false);
        deleteTransferOwnershipBundleLocked(userId);
        toggleBackupServiceActive(UserHandle.USER_SYSTEM, true);
        pushUserControlDisabledPackagesLocked(userId);
        setGlobalSettingDeviceOwnerType(DEVICE_OWNER_TYPE_DEFAULT);
    }

    private void clearApplicationRestrictions(int userId) {
        // Changing app restrictions involves disk IO, offload it to the background thread.
        mBackgroundHandler.post(() -> {
            final List<PackageInfo> installedPackageInfos = mInjector.getPackageManager(userId)
                    .getInstalledPackages(MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);
            final UserHandle userHandle = UserHandle.of(userId);
            for (final PackageInfo packageInfo : installedPackageInfos) {
                mInjector.getUserManager().setApplicationRestrictions(
                        packageInfo.packageName, null /* restrictions */, userHandle);
            }
        });
    }

    @Override
    public boolean setProfileOwner(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            logMissingFeatureAction("Cannot set " + ComponentName.flattenToShortString(who)
                    + " as profile owner for user " + userHandle);
            return false;
        }
        Preconditions.checkArgument(who != null);

        final CallerIdentity caller = getCallerIdentity();
        // Cannot be called while holding the lock:
        final boolean hasIncompatibleAccountsOrNonAdb =
                hasIncompatibleAccountsOrNonAdbNoLock(caller, userHandle, who);
        synchronized (getLockObject()) {
            enforceCanSetProfileOwnerLocked(
                    caller, who, userHandle, hasIncompatibleAccountsOrNonAdb);
            final ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
            Preconditions.checkArgument(
                    isPackageInstalledForUser(who.getPackageName(), userHandle)
                            && admin != null
                            && !getUserData(userHandle).mRemovingAdmins.contains(who),
                    "Not active admin: " + who);

            final int parentUserId = getProfileParentId(userHandle);
            // When trying to set a profile owner on a new user, it may be that this user is
            // a profile - but it may not be a managed profile if there's a restriction on the
            // parent to add managed profiles (e.g. if the device has a device owner).
            if (parentUserId != userHandle && mUserManager.hasUserRestriction(
                    UserManager.DISALLOW_ADD_MANAGED_PROFILE,
                    UserHandle.of(parentUserId))) {
                Slogf.i(LOG_TAG, "Cannot set profile owner because of restriction.");
                return false;
            }

            if (isAdb(caller)) {
                // Log profile owner provisioning was started using adb.
                MetricsLogger.action(mContext, PROVISIONING_ENTRY_POINT_ADB, LOG_TAG_PROFILE_OWNER);
                DevicePolicyEventLogger
                        .createEvent(DevicePolicyEnums.PROVISIONING_ENTRY_POINT_ADB)
                        .setAdmin(who)
                        .setStrings(LOG_TAG_PROFILE_OWNER)
                        .write();
            }

            // Shutting down backup manager service permanently.
            toggleBackupServiceActive(userHandle, /* makeActive= */ false);

            mOwners.setProfileOwner(who, userHandle);
            mOwners.writeProfileOwner(userHandle);
            Slogf.i(LOG_TAG, "Profile owner set: " + who + " on user " + userHandle);

            mInjector.binderWithCleanCallingIdentity(() -> {
                if (mUserManager.isManagedProfile(userHandle)) {
                    maybeSetDefaultRestrictionsForAdminLocked(userHandle, admin,
                            UserRestrictionsUtils.getDefaultEnabledForManagedProfiles());
                    ensureUnknownSourcesRestrictionForProfileOwnerLocked(userHandle, admin,
                            true /* newOwner */);
                }
                sendOwnerChangedBroadcast(DevicePolicyManager.ACTION_PROFILE_OWNER_CHANGED,
                        userHandle);
            });
            mDeviceAdminServiceController.startServiceForAdmin(
                    who.getPackageName(), userHandle, "set-profile-owner");
            return true;
        }
    }

    private void toggleBackupServiceActive(int userId, boolean makeActive) {
        long ident = mInjector.binderClearCallingIdentity();
        try {
            if (mInjector.getIBackupManager() != null) {
                mInjector.getIBackupManager()
                        .setBackupServiceActive(userId, makeActive);
            }
        } catch (RemoteException e) {
            throw new IllegalStateException(String.format("Failed %s backup service.",
                    makeActive ? "activating" : "deactivating"), e);
        } finally {
            mInjector.binderRestoreCallingIdentity(ident);
        }

    }

    @Override
    public void clearProfileOwner(ComponentName who) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        final int userId = caller.getUserId();
        Preconditions.checkCallingUser(!isManagedProfile(userId));
        Preconditions.checkCallAuthorization(isProfileOwner(caller));

        enforceUserUnlocked(userId);
        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());

            mInjector.binderWithCleanCallingIdentity(() -> {
                clearProfileOwnerLocked(admin, userId);
                removeActiveAdminLocked(who, userId);
                sendOwnerChangedBroadcast(DevicePolicyManager.ACTION_PROFILE_OWNER_CHANGED,
                        userId);
            });
            Slogf.i(LOG_TAG, "Profile owner " + who + " removed from user " + userId);
        }
    }

    public void clearProfileOwnerLocked(ActiveAdmin admin, int userId) {
        String ownersPackage = mOwners.getProfileOwnerPackage(userId);
        if (ownersPackage != null) {
            mDeviceAdminServiceController.stopServiceForAdmin(
                    ownersPackage, userId, "clear-profile-owner");
        }

        if (admin != null) {
            admin.disableCamera = false;
            admin.userRestrictions = null;
            admin.defaultEnabledRestrictionsAlreadySet.clear();
        }
        final DevicePolicyData policyData = getUserData(userId);
        policyData.mCurrentInputMethodSet = false;
        policyData.mOwnerInstalledCaCerts.clear();
        saveSettingsLocked(userId);
        clearUserPoliciesLocked(userId);
        clearApplicationRestrictions(userId);
        mOwners.removeProfileOwner(userId);
        mOwners.writeProfileOwner(userId);
        deleteTransferOwnershipBundleLocked(userId);
        toggleBackupServiceActive(userId, true);
        applyProfileRestrictionsIfDeviceOwnerLocked();
        setNetworkLoggingActiveInternal(false);
    }

    @Override
    public void setDeviceOwnerLockScreenInfo(ComponentName who, CharSequence info) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwnerOfOrganizationOwnedDevice(caller));

        mInjector.binderWithCleanCallingIdentity(() ->
                mLockPatternUtils.setDeviceOwnerInfo(info != null ? info.toString() : null));

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_DEVICE_OWNER_LOCK_SCREEN_INFO)
                .setAdmin(caller.getComponentName())
                .write();
    }

    @Override
    public CharSequence getDeviceOwnerLockScreenInfo() {
        return mLockPatternUtils.getDeviceOwnerInfo();
    }

    private void clearUserPoliciesLocked(int userId) {
        // Reset some of the user-specific policies.
        final DevicePolicyData policy = getUserData(userId);
        policy.mPermissionPolicy = DevicePolicyManager.PERMISSION_POLICY_PROMPT;
        // Clear delegations.
        policy.mDelegationMap.clear();
        policy.mStatusBarDisabled = false;
        policy.mSecondaryLockscreenEnabled = false;
        policy.mUserProvisioningState = DevicePolicyManager.STATE_USER_UNMANAGED;
        policy.mAffiliationIds.clear();
        policy.mLockTaskPackages.clear();
        updateLockTaskPackagesLocked(mContext, policy.mLockTaskPackages, userId);
        policy.mLockTaskFeatures = DevicePolicyManager.LOCK_TASK_FEATURE_NONE;
        saveSettingsLocked(userId);

        try {
            mIPermissionManager.updatePermissionFlagsForAllApps(
                    PackageManager.FLAG_PERMISSION_POLICY_FIXED,
                    0  /* flagValues */, userId);
            pushUserRestrictions(userId);
        } catch (RemoteException re) {
            // Shouldn't happen.
            Slogf.wtf(LOG_TAG, "Failing in updatePermissionFlagsForAllApps", re);
        }
    }

    @Override
    public boolean hasUserSetupCompleted() {
        return hasUserSetupCompleted(mInjector.userHandleGetCallingUserId());
    }

    // This checks only if the Setup Wizard has run.  Since Wear devices pair before
    // completing Setup Wizard, and pairing involves transferring user data, calling
    // logic may want to check mIsWatch or mPaired in addition to hasUserSetupCompleted().
    private boolean hasUserSetupCompleted(int userHandle) {
        if (!mHasFeature) {
            return true;
        }
        return mInjector.hasUserSetupCompleted(getUserData(userHandle));
    }

    private boolean hasPaired(int userHandle) {
        if (!mHasFeature) {
            return true;
        }
        return getUserData(userHandle).mPaired;
    }

    @Override
    public int getUserProvisioningState(int userHandle) {
        if (!mHasFeature) {
            return DevicePolicyManager.STATE_USER_UNMANAGED;
        }
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(canManageUsers(caller)
                || hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        if (userHandle != caller.getUserId()) {
            Preconditions.checkCallAuthorization(canManageUsers(caller)
                    || hasCallingOrSelfPermission(permission.INTERACT_ACROSS_USERS));
        }

        return getUserData(userHandle).mUserProvisioningState;
    }

    @Override
    public void setUserProvisioningState(int newState, int userId) {
        if (!mHasFeature) {
            logMissingFeatureAction("Cannot set provisioning state " + newState + " for user "
                    + userId);
            return;
        }
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        final CallerIdentity caller = getCallerIdentity();
        final long id = mInjector.binderClearCallingIdentity();
        try {
            int deviceOwnerUserId = mOwners.getDeviceOwnerUserId();
            // NOTE: multiple if statements are nested below so it can log more info on error
            if (userId != deviceOwnerUserId) {
                boolean hasProfileOwner = mOwners.hasProfileOwner(userId);
                if (!hasProfileOwner) {
                    int managedUserId = getManagedUserId(userId);
                    if (managedUserId < 0 && newState != STATE_USER_UNMANAGED) {
                        // No managed device, user or profile, so setting provisioning state makes
                        // no sense.
                        String error = "Not allowed to change provisioning state unless a "
                                + "device or profile owner is set.";
                        Slogf.w(LOG_TAG, "setUserProvisioningState(newState=%d, userId=%d) failed: "
                                + "deviceOwnerId=%d, hasProfileOwner=%b, managedUserId=%d, err=%s",
                                newState, userId, deviceOwnerUserId, hasProfileOwner,
                                managedUserId, error);
                        throw new IllegalStateException(error);
                    }
                }
            }

            synchronized (getLockObject()) {
                boolean transitionCheckNeeded = true;

                // Calling identity/permission checks.
                if (isAdb(caller)) {
                    // ADB shell can only move directly from un-managed to finalized as part of
                    // directly setting profile-owner or device-owner.
                    if (getUserProvisioningState(userId)
                            != DevicePolicyManager.STATE_USER_UNMANAGED
                            || newState != STATE_USER_SETUP_FINALIZED) {
                        throw new IllegalStateException("Not allowed to change provisioning state "
                                + "unless current provisioning state is unmanaged, and new state"
                                + "is finalized.");
                    }
                    transitionCheckNeeded = false;
                }

                final DevicePolicyData policyData = getUserData(userId);
                if (transitionCheckNeeded) {
                    // Optional state transition check for non-ADB case.
                    checkUserProvisioningStateTransition(policyData.mUserProvisioningState,
                            newState);
                }
                policyData.mUserProvisioningState = newState;
                saveSettingsLocked(userId);
            }
        } finally {
            mInjector.binderRestoreCallingIdentity(id);
        }
    }

    private void checkUserProvisioningStateTransition(int currentState, int newState) {
        // Valid transitions for normal use-cases.
        switch (currentState) {
            case DevicePolicyManager.STATE_USER_UNMANAGED:
                // Can move to any state from unmanaged (except itself as an edge case)..
                if (newState != DevicePolicyManager.STATE_USER_UNMANAGED) {
                    return;
                }
                break;
            case DevicePolicyManager.STATE_USER_SETUP_INCOMPLETE:
            case DevicePolicyManager.STATE_USER_SETUP_COMPLETE:
                // Can only move to finalized from these states.
                if (newState == STATE_USER_SETUP_FINALIZED) {
                    return;
                }
                break;
            case DevicePolicyManager.STATE_USER_PROFILE_COMPLETE:
                // Current user has a managed-profile, but current user is not managed, so
                // rather than moving to finalized state, go back to unmanaged once
                // profile provisioning is complete.
                if (newState == DevicePolicyManager.STATE_USER_PROFILE_FINALIZED) {
                    return;
                }
                break;
            case STATE_USER_SETUP_FINALIZED:
                // Cannot transition out of finalized.
                break;
            case DevicePolicyManager.STATE_USER_PROFILE_FINALIZED:
                // Should only move to an unmanaged state after removing the work profile.
                if (newState == DevicePolicyManager.STATE_USER_UNMANAGED) {
                    return;
                }
                break;
        }

        // Didn't meet any of the accepted state transition checks above, throw appropriate error.
        throw new IllegalStateException("Cannot move to user provisioning state [" + newState + "] "
                + "from state [" + currentState + "]");
    }

    @Override
    public void setProfileEnabled(ComponentName who) {
        if (!mHasFeature) {
            logMissingFeatureAction("Cannot enable profile for "
                    + ComponentName.flattenToShortString(who));
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        final int userId = caller.getUserId();
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));
        Preconditions.checkCallingUser(isManagedProfile(userId));

        synchronized (getLockObject()) {
            // Check if the profile is already enabled.
            UserInfo managedProfile = getUserInfo(userId);
            if (managedProfile.isEnabled()) {
                Slogf.e(LOG_TAG,
                        "setProfileEnabled is called when the profile is already enabled");
                return;
            }
            mInjector.binderWithCleanCallingIdentity(() -> {
                mUserManager.setUserEnabled(userId);
                UserInfo parent = mUserManager.getProfileParent(userId);
                Intent intent = new Intent(Intent.ACTION_MANAGED_PROFILE_ADDED);
                intent.putExtra(Intent.EXTRA_USER, new UserHandle(userId));
                UserHandle parentHandle = new UserHandle(parent.id);
                mLocalService.broadcastIntentToManifestReceivers(intent,
                        parentHandle, /* requiresPermission= */ true);
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY |
                        Intent.FLAG_RECEIVER_FOREGROUND);
                mContext.sendBroadcastAsUser(intent, parentHandle);
            });
        }
    }

    @Override
    public void setProfileName(ComponentName who, String profileName) {
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));

        mInjector.binderWithCleanCallingIdentity(() -> {
            mUserManager.setUserName(caller.getUserId(), profileName);
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.SET_PROFILE_NAME)
                    .setAdmin(caller.getComponentName())
                    .write();
        });
    }

    @Override
    public ComponentName getProfileOwnerAsUser(int userId) {
        if (!mHasFeature) {
            return null;
        }
        Preconditions.checkArgumentNonnegative(userId, "Invalid userId");

        CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasCrossUsersPermission(caller, userId)
                || hasFullCrossUsersPermission(caller, userId));

        synchronized (getLockObject()) {
            return mOwners.getProfileOwnerComponent(userId);
        }
    }

    // Returns the active profile owner for this user or null if the current user has no
    // profile owner.
    @VisibleForTesting
    ActiveAdmin getProfileOwnerAdminLocked(int userHandle) {
        ComponentName profileOwner = mOwners.getProfileOwnerComponent(userHandle);
        if (profileOwner == null) {
            return null;
        }
        DevicePolicyData policy = getUserData(userHandle);
        final int n = policy.mAdminList.size();
        for (int i = 0; i < n; i++) {
            ActiveAdmin admin = policy.mAdminList.get(i);
            if (profileOwner.equals(admin.info.getComponent())) {
                return admin;
            }
        }
        return null;
    }

    /**
     * Returns the ActiveAdmin associated with the PO or DO on the given user.
     */
    private @Nullable ActiveAdmin getDeviceOrProfileOwnerAdminLocked(int userHandle) {
        ActiveAdmin admin = getProfileOwnerAdminLocked(userHandle);
        if (admin == null && getDeviceOwnerUserIdUncheckedLocked() == userHandle) {
            admin = getDeviceOwnerAdminLocked();
        }
        return admin;
    }

    @GuardedBy("getLockObject()")
    ActiveAdmin getProfileOwnerOfOrganizationOwnedDeviceLocked(int userHandle) {
        return mInjector.binderWithCleanCallingIdentity(() -> {
            for (UserInfo userInfo : mUserManager.getProfiles(userHandle)) {
                if (userInfo.isManagedProfile()) {
                    if (getProfileOwnerAsUser(userInfo.id) != null
                            && isProfileOwnerOfOrganizationOwnedDevice(userInfo.id)) {
                        ComponentName who = getProfileOwnerAsUser(userInfo.id);
                        return getActiveAdminUncheckedLocked(who, userInfo.id);
                    }
                }
            }
            return null;
        });
    }

    @GuardedBy("getLockObject()")
    ActiveAdmin getProfileOwnerOfOrganizationOwnedDeviceLocked() {
        return mInjector.binderWithCleanCallingIdentity(() -> {
            for (UserInfo userInfo : mUserManager.getUsers()) {
                if (userInfo.isManagedProfile()) {
                    if (getProfileOwnerAsUser(userInfo.id) != null
                            && isProfileOwnerOfOrganizationOwnedDevice(userInfo.id)) {
                        ComponentName who = getProfileOwnerAsUser(userInfo.id);
                        return getActiveAdminUncheckedLocked(who, userInfo.id);
                    }
                }
            }
            return null;
        });
    }

    /**
     * This API is cached: invalidate with invalidateBinderCaches().
     */
    @Override
    public @Nullable ComponentName getProfileOwnerOrDeviceOwnerSupervisionComponent(
            @NonNull UserHandle userHandle) {
        if (!mHasFeature) {
            return null;
        }
        synchronized (getLockObject()) {
            final ComponentName doComponent = mOwners.getDeviceOwnerComponent();
            final ComponentName poComponent =
                    mOwners.getProfileOwnerComponent(userHandle.getIdentifier());
            // Return test only admin if configured to do so.
            // TODO(b/182994391): Replace with more generic solution to override the supervision
            // component.
            if (mConstants.USE_TEST_ADMIN_AS_SUPERVISION_COMPONENT) {
                if (isAdminTestOnlyLocked(doComponent, userHandle.getIdentifier())) {
                    return doComponent;
                } else if (isAdminTestOnlyLocked(poComponent, userHandle.getIdentifier())) {
                    return poComponent;
                }
            }

            // Check profile owner first as that is what most likely is set.
            if (isSupervisionComponentLocked(poComponent)) {
                return poComponent;
            }

            if (isSupervisionComponentLocked(doComponent)) {
                return doComponent;
            }

            return null;
        }
    }

    /**
     * Returns if the specified component is the supervision component.
     */
    @Override
    public boolean isSupervisionComponent(@NonNull ComponentName who) {
        if (!mHasFeature) {
            return false;
        }
        synchronized (getLockObject()) {
            if (mConstants.USE_TEST_ADMIN_AS_SUPERVISION_COMPONENT) {
                final CallerIdentity caller = getCallerIdentity();
                if (isAdminTestOnlyLocked(who, caller.getUserId())) {
                    return true;
                }
            }
            return isSupervisionComponentLocked(who);
        }
    }

    private boolean isSupervisionComponentLocked(@Nullable ComponentName who) {
        if (who == null) {
            return false;
        }

        final String configComponent = mContext.getResources().getString(
                com.android.internal.R.string.config_defaultSupervisionProfileOwnerComponent);
        if (configComponent != null) {
            final ComponentName componentName = ComponentName.unflattenFromString(configComponent);
            if (who.equals(componentName)) {
                return true;
            }
        }

        // Check the system supervision role.
        final String configPackage = mContext.getResources().getString(
                com.android.internal.R.string.config_systemSupervision);

        return who.getPackageName().equals(configPackage);
    }

    // TODO(b/240562946): Remove api as owner name is not used.
    @Override
    public String getProfileOwnerName(int userHandle) {
        if (!mHasFeature) {
            return null;
        }
        Preconditions.checkCallAuthorization(canManageUsers(getCallerIdentity())
                || hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));
        return getProfileOwnerNameUnchecked(userHandle);
    }

    private String getProfileOwnerNameUnchecked(int userHandle) {
        ComponentName profileOwner = getProfileOwnerAsUser(userHandle);
        if (profileOwner == null) {
            return null;
        }
        return getApplicationLabel(profileOwner.getPackageName(), userHandle);
    }

    private @UserIdInt int getOrganizationOwnedProfileUserId() {
        for (UserInfo ui : mUserManagerInternal.getUserInfos()) {
            if (ui.isManagedProfile() && isProfileOwnerOfOrganizationOwnedDevice(ui.id)) {
                return ui.id;
            }
        }
        return UserHandle.USER_NULL;
    }

    /**
     * This API is cached: invalidate with invalidateBinderCaches().
     */
    @Override
    public boolean isOrganizationOwnedDeviceWithManagedProfile() {
        if (!mHasFeature) {
            return false;
        }
        return getOrganizationOwnedProfileUserId() != UserHandle.USER_NULL;
    }

    @Override
    public boolean checkDeviceIdentifierAccess(String packageName, int pid, int uid) {
        final CallerIdentity caller = getCallerIdentity();
        ensureCallerIdentityMatchesIfNotSystem(packageName, pid, uid, caller);

        // Verify that the specified packages matches the provided uid.
        if (!doesPackageMatchUid(packageName, uid)) {
            return false;
        }
        // A device or profile owner must also have the READ_PHONE_STATE permission to access device
        // identifiers. If the package being checked does not have this permission then deny access.
        if (!hasPermission(permission.READ_PHONE_STATE, pid, uid)) {
            return false;
        }
        return hasDeviceIdAccessUnchecked(packageName, uid);
    }

    /**
     * Check if one the following conditions hold:
     * (1) The device has a Device Owner, and one of the following holds:
     *   (1.1) The caller is the Device Owner
     *   (1.2) The caller is another app in the same user as the device owner, AND
     *         The caller is the delegated certificate installer.
     *   (1.3) The caller is a Profile Owner and the calling user is affiliated.
     * (2) The user has a profile owner, AND:
     *   (2.1) The profile owner has been granted access to Device IDs and one of the following
     *         holds:
     *     (2.1.1) The caller is the profile owner.
     *     (2.1.2) The caller is from another app in the same user as the profile owner, AND
     *             the caller is the delegated cert installer.
     *
     *  For the device owner case, simply check that the caller is the device owner or the
     *  delegated certificate installer.
     *
     *  For the profile owner case, first check that the caller is the profile owner or can
     *  manage the DELEGATION_CERT_INSTALL scope.
     *  If that check succeeds, ensure the profile owner was granted access to device
     *  identifiers. The grant is transitive: The delegated cert installer is implicitly allowed
     *  access to device identifiers in this case as part of the delegation.
     */
    @VisibleForTesting
    boolean hasDeviceIdAccessUnchecked(String packageName, int uid) {
        ComponentName deviceOwner = getDeviceOwnerComponent(true);
        if (deviceOwner != null && (deviceOwner.getPackageName().equals(packageName)
                || isCallerDelegate(packageName, uid, DELEGATION_CERT_INSTALL))) {
            return true;
        }
        final int userId = UserHandle.getUserId(uid);
        ComponentName profileOwner = getProfileOwnerAsUser(userId);
        final boolean isCallerProfileOwnerOrDelegate = profileOwner != null
                && (profileOwner.getPackageName().equals(packageName)
                || isCallerDelegate(packageName, uid, DELEGATION_CERT_INSTALL));
        if (isCallerProfileOwnerOrDelegate && (isProfileOwnerOfOrganizationOwnedDevice(userId)
                || isUserAffiliatedWithDevice(userId))) {
            return true;
        }
        return false;
    }

    private boolean doesPackageMatchUid(String packageName, int uid) {
        final int userId = UserHandle.getUserId(uid);
        try {
            ApplicationInfo appInfo = mIPackageManager.getApplicationInfo(packageName, 0, userId);
            // Since this call goes directly to PackageManagerService a NameNotFoundException is not
            // thrown but null data can be returned; if the appInfo for the specified package cannot
            // be found then return false to prevent crashing the app.
            if (appInfo == null) {
                Slogf.w(LOG_TAG, "appInfo could not be found for package %s", packageName);
                return false;
            } else if (uid != appInfo.uid) {
                String message = String.format("Package %s (uid=%d) does not match provided uid %d",
                        packageName, appInfo.uid, uid);
                Slogf.w(LOG_TAG, message);
                throw new SecurityException(message);
            }
        } catch (RemoteException e) {
            // If an exception is caught obtaining the appInfo just return false to prevent crashing
            // apps due to an internal error.
            Slogf.e(LOG_TAG, e, "Exception caught obtaining appInfo for package %s", packageName);
            return false;
        }
        return true;
    }

    private void ensureCallerIdentityMatchesIfNotSystem(String packageName, int pid, int uid,
            CallerIdentity caller) {
        // If the caller is not a system app then it should only be able to check its own device
        // identifier access.
        int callingUid = caller.getUid();
        int callingPid = mInjector.binderGetCallingPid();
        if (UserHandle.getAppId(callingUid) >= Process.FIRST_APPLICATION_UID
                && (callingUid != uid || callingPid != pid)) {
            String message = String.format(
                    "Calling uid %d, pid %d cannot check device identifier access for package %s "
                            + "(uid=%d, pid=%d)", callingUid, callingPid, packageName, uid, pid);
            Slogf.w(LOG_TAG, message);
            throw new SecurityException(message);
        }
    }

    /**
     * Canonical name for a given package.
     */
    private String getApplicationLabel(String packageName, @UserIdInt int userId) {
        return mInjector.binderWithCleanCallingIdentity(() -> {
            final Context userContext;
            try {
                UserHandle userHandle = UserHandle.of(userId);
                userContext = mContext.createPackageContextAsUser(packageName, /* flags= */ 0,
                        userHandle);
            } catch (PackageManager.NameNotFoundException nnfe) {
                Slogf.w(LOG_TAG, nnfe, "%s is not installed for user %d", packageName, userId);
                return null;
            }
            ApplicationInfo appInfo = userContext.getApplicationInfo();
            CharSequence result = null;
            if (appInfo != null) {
                result = appInfo.loadUnsafeLabel(userContext.getPackageManager());
            }
            return result != null ? result.toString() : null;
        });
    }

    /**
     * The profile owner can only be set by adb or an app with the MANAGE_PROFILE_AND_DEVICE_OWNERS
     * permission.
     * The profile owner can only be set before the user setup phase has completed,
     * except for:
     * - SYSTEM_UID
     * - adb unless hasIncompatibleAccountsOrNonAdb is true.
     */
    private void enforceCanSetProfileOwnerLocked(
            CallerIdentity caller, @Nullable ComponentName owner, int userHandle,
            boolean hasIncompatibleAccountsOrNonAdb) {
        UserInfo info = getUserInfo(userHandle);
        if (info == null) {
            // User doesn't exist.
            throw new IllegalArgumentException(
                    "Attempted to set profile owner for invalid userId: " + userHandle);
        }
        if (info.isGuest()) {
            throw new IllegalStateException("Cannot set a profile owner on a guest");
        }
        if (mOwners.hasProfileOwner(userHandle)) {
            throw new IllegalStateException("Trying to set the profile owner, but profile owner "
                    + "is already set.");
        }
        if (mOwners.hasDeviceOwner() && mOwners.getDeviceOwnerUserId() == userHandle) {
            throw new IllegalStateException("Trying to set the profile owner, but the user "
                    + "already has a device owner.");
        }
        if (isAdb(caller)) {
            if ((mIsWatch || hasUserSetupCompleted(userHandle))
                    && hasIncompatibleAccountsOrNonAdb) {
                throw new IllegalStateException("Not allowed to set the profile owner because "
                        + "there are already some accounts on the profile");
            }
            return;
        }
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        if ((mIsWatch || hasUserSetupCompleted(userHandle))) {
            Preconditions.checkState(isSystemUid(caller),
                    "Cannot set the profile owner on a user which is already set-up");

            if (!mIsWatch) {
                if (!isSupervisionComponentLocked(owner)) {
                    throw new IllegalStateException("Unable to set non-default profile owner"
                            + " post-setup " + owner);
                }
            }
        }
    }

    /**
     * The Device owner can only be set by adb or an app with the MANAGE_PROFILE_AND_DEVICE_OWNERS
     * permission.
     */
    private void enforceCanSetDeviceOwnerLocked(
            CallerIdentity caller, @Nullable ComponentName owner, @UserIdInt int deviceOwnerUserId,
            boolean hasIncompatibleAccountsOrNonAdb) {
        if (!isAdb(caller)) {
            Preconditions.checkCallAuthorization(
                    hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));
        }

        final int code = checkDeviceOwnerProvisioningPreConditionLocked(owner,
                /* deviceOwnerUserId= */ deviceOwnerUserId, /* callingUserId*/ caller.getUserId(),
                isAdb(caller), hasIncompatibleAccountsOrNonAdb);
        if (code != STATUS_OK) {
            throw new IllegalStateException(
                    computeProvisioningErrorString(code, deviceOwnerUserId));
        }
    }

    private static String computeProvisioningErrorString(int code, @UserIdInt int userId) {
        switch (code) {
            case STATUS_OK:
                return "OK";
            case STATUS_HAS_DEVICE_OWNER:
                return "Trying to set the device owner, but device owner is already set.";
            case STATUS_USER_HAS_PROFILE_OWNER:
                return "Trying to set the device owner, but the user already has a profile owner.";
            case STATUS_USER_NOT_RUNNING:
                return "User " + userId + " not running.";
            case STATUS_NOT_SYSTEM_USER:
                return "User " + userId + " is not system user.";
            case STATUS_USER_SETUP_COMPLETED:
                return  "Cannot set the device owner if the device is already set-up.";
            case STATUS_NONSYSTEM_USER_EXISTS:
                return "Not allowed to set the device owner because there are already several"
                        + " users on the device.";
            case STATUS_ACCOUNTS_NOT_EMPTY:
                return "Not allowed to set the device owner because there are already some accounts"
                        + " on the device.";
            case STATUS_HAS_PAIRED:
                return "Not allowed to set the device owner because this device has already "
                        + "paired.";
            default:
                return "Unexpected @ProvisioningPreCondition: " + code;
        }

    }

    private void enforceUserUnlocked(int userId) {
        // Since we're doing this operation on behalf of an app, we only
        // want to use the actual "unlocked" state.
        Preconditions.checkState(mUserManager.isUserUnlocked(userId),
                "User must be running and unlocked");
    }

    private void enforceUserUnlocked(@UserIdInt int userId, boolean parent) {
        if (parent) {
            enforceUserUnlocked(getProfileParentId(userId));
        } else {
            enforceUserUnlocked(userId);
        }
    }

    private boolean canManageUsers(CallerIdentity caller) {
        return hasCallingOrSelfPermission(permission.MANAGE_USERS);
    }

    private boolean canQueryAdminPolicy(CallerIdentity caller) {
        return hasCallingOrSelfPermission(permission.QUERY_ADMIN_POLICY);
    }

    private boolean hasPermission(String permission, int pid, int uid) {
        return mContext.checkPermission(permission, pid, uid) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasCallingPermission(String permission) {
        return mContext.checkCallingPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasCallingOrSelfPermission(String permission) {
        return mContext.checkCallingOrSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasPermissionForPreflight(CallerIdentity caller, String permission) {
        final int callingPid = mInjector.binderGetCallingPid();
        final String packageName = mContext.getPackageName();

        return PermissionChecker.checkPermissionForPreflight(mContext, permission, callingPid,
                caller.getUid(), packageName) == PermissionChecker.PERMISSION_GRANTED;
    }

    private boolean hasFullCrossUsersPermission(CallerIdentity caller, int userHandle) {
        return (userHandle == caller.getUserId()) || isSystemUid(caller) || isRootUid(caller)
                || hasCallingOrSelfPermission(permission.INTERACT_ACROSS_USERS_FULL);
    }

    private boolean hasCrossUsersPermission(CallerIdentity caller, int userHandle) {
        return (userHandle == caller.getUserId()) || isSystemUid(caller) || isRootUid(caller)
                || hasCallingOrSelfPermission(permission.INTERACT_ACROSS_USERS);
    }

    private boolean canUserUseLockTaskLocked(int userId) {
        if (isUserAffiliatedWithDeviceLocked(userId)) {
            return true;
        }

        // Unaffiliated profile owners are not allowed to use lock when there is a device owner.
        if (mOwners.hasDeviceOwner()) {
            return false;
        }

        final ComponentName profileOwner = getProfileOwnerAsUser(userId);
        if (profileOwner == null) {
            return false;
        }

        // Managed profiles are not allowed to use lock task
        if (isManagedProfile(userId)) {
            return false;
        }

        return true;
    }

    private void enforceCanCallLockTaskLocked(CallerIdentity caller) {
        Preconditions.checkCallAuthorization(isProfileOwner(caller)
                || isDefaultDeviceOwner(caller) || isFinancedDeviceOwner(caller));

        final int userId =  caller.getUserId();
        if (!canUserUseLockTaskLocked(userId)) {
            throw new SecurityException("User " + userId + " is not allowed to use lock task");
        }
    }

    private boolean isSystemUid(CallerIdentity caller) {
        return UserHandle.isSameApp(caller.getUid(), Process.SYSTEM_UID);
    }

    private boolean isRootUid(CallerIdentity caller) {
        return UserHandle.isSameApp(caller.getUid(), Process.ROOT_UID);
    }

    private boolean isShellUid(CallerIdentity caller) {
        return UserHandle.isSameApp(caller.getUid(), Process.SHELL_UID);
    }

    private boolean isCameraServerUid(CallerIdentity caller) {
        return UserHandle.isSameApp(caller.getUid(), Process.CAMERASERVER_UID);
    }

    private @UserIdInt int getCurrentForegroundUserId() {
        try {
            UserInfo currentUser = mInjector.getIActivityManager().getCurrentUser();
            if (currentUser == null) {
                // TODO(b/206107460): should not happen on production, but it's happening on unit
                // tests that are not properly setting the expectation (because they don't need it)
                Slogf.wtf(LOG_TAG, "getCurrentForegroundUserId(): mInjector.getIActivityManager()"
                        + ".getCurrentUser() returned null, please ignore when running unit tests");
                return ActivityManager.getCurrentUser();
            }
            return currentUser.id;
        } catch (RemoteException e) {
            Slogf.wtf(LOG_TAG, "cannot get current user", e);
        }
        return UserHandle.USER_NULL;
    }

    @Override
    public List<UserHandle> listForegroundAffiliatedUsers() {
        checkIsDeviceOwner(getCallerIdentity());

        return mInjector.binderWithCleanCallingIdentity(() -> {
            int userId = getCurrentForegroundUserId();
            boolean isAffiliated;
            synchronized (getLockObject()) {
                isAffiliated = isUserAffiliatedWithDeviceLocked(userId);
            }

            if (!isAffiliated) return Collections.emptyList();

            List<UserHandle> users = new ArrayList<>(1);
            users.add(UserHandle.of(userId));

            return users;
        });
    }

    protected int getProfileParentId(int userHandle) {
        return mInjector.binderWithCleanCallingIdentity(() -> {
            UserInfo parentUser = mUserManager.getProfileParent(userHandle);
            return parentUser != null ? parentUser.id : userHandle;
        });
    }

    private int getProfileParentUserIfRequested(int userHandle, boolean parent) {
        if (parent) {
            return getProfileParentId(userHandle);
        }

        return userHandle;
    }

    private int getCredentialOwner(final int userHandle, final boolean parent) {
        return mInjector.binderWithCleanCallingIdentity(() -> {
            int effectiveUserHandle = userHandle;
            if (parent) {
                UserInfo parentProfile = mUserManager.getProfileParent(userHandle);
                if (parentProfile != null) {
                    effectiveUserHandle = parentProfile.id;
                }
            }
            return mUserManager.getCredentialOwnerProfile(effectiveUserHandle);
        });
    }

    private boolean isManagedProfile(int userHandle) {
        final UserInfo user = getUserInfo(userHandle);
        return user != null && user.isManagedProfile();
    }

    private void enableIfNecessary(String packageName, int userId) {
        try {
            final ApplicationInfo ai = mIPackageManager.getApplicationInfo(packageName,
                    PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS, userId);
            if (ai.enabledSetting
                    == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
                mIPackageManager.setApplicationEnabledSetting(packageName,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        PackageManager.DONT_KILL_APP, userId, "DevicePolicyManager");
            }
        } catch (RemoteException e) {
        }
    }

    private void dumpPerUserData(IndentingPrintWriter pw) {
        int userCount = mUserData.size();
        for (int i = 0; i < userCount; i++) {
            int userId = mUserData.keyAt(i);
            DevicePolicyData policy = getUserData(userId);
            policy.dump(pw);
            pw.println();

            if (userId == UserHandle.USER_SYSTEM) {
                pw.increaseIndent();
                PersonalAppsSuspensionHelper.forUser(mContext, userId).dump(pw);
                pw.decreaseIndent();
                pw.println();
            } else {
                // pm.getUnsuspendablePackages() will fail if it's called for a different user;
                // as this dump is mostly useful for system user anyways, we can just ignore the
                // others (rather than changing the permission check in the PM method)
                Slogf.d(LOG_TAG, "skipping PersonalAppsSuspensionHelper.dump() for user " + userId);
            }
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        if (!DumpUtils.checkDumpPermission(mContext, LOG_TAG, printWriter)) return;

        try (IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ")) {
            pw.println("Current Device Policy Manager state:");
            pw.increaseIndent();

            dumpImmutableState(pw);
            synchronized (getLockObject()) {
                mOwners.dump(pw);
                pw.println();
                mDeviceAdminServiceController.dump(pw);
                pw.println();
                dumpPerUserData(pw);
                pw.println();
                mConstants.dump(pw);
                pw.println();
                mStatLogger.dump(pw);
                pw.println();
                pw.println("Encryption Status: " + getEncryptionStatusName(getEncryptionStatus()));
                pw.println("Logout user: " + getLogoutUserIdUnchecked());
                pw.println();

                if (mPendingUserCreatedCallbackTokens.isEmpty()) {
                    pw.println("no pending user created callback tokens");
                } else {
                    int size = mPendingUserCreatedCallbackTokens.size();
                    pw.printf("%d pending user created callback token%s\n", size,
                            (size == 1 ? "" : "s"));
                }
                pw.println();
                pw.println("Keep profiles running: " + mKeepProfilesRunning);
                pw.println();

                mPolicyCache.dump(pw);
                pw.println();
                mStateCache.dump(pw);
                pw.println();
            }

            synchronized (mSubscriptionsChangedListenerLock) {
                pw.println("Subscription changed listener : " + mSubscriptionsChangedListener);
            }
            pw.println(
                    "Flag enable_work_profile_telephony : " + isWorkProfileTelephonyFlagEnabled());

            mHandler.post(() -> handleDump(pw));
            dumpResources(pw);
        }
    }

    // Dump state that is guarded by the handler
    private void handleDump(IndentingPrintWriter pw) {
        if (mNetworkLoggingNotificationUserId != UserHandle.USER_NULL) {
            pw.println("mNetworkLoggingNotificationUserId:  " + mNetworkLoggingNotificationUserId);
        }
    }

    private void dumpImmutableState(IndentingPrintWriter pw) {
        pw.println("Immutable state:");
        pw.increaseIndent();
        pw.printf("mHasFeature=%b\n", mHasFeature);
        pw.printf("mIsWatch=%b\n", mIsWatch);
        pw.printf("mIsAutomotive=%b\n", mIsAutomotive);
        pw.printf("mHasTelephonyFeature=%b\n", mHasTelephonyFeature);
        pw.printf("mSafetyChecker=%s\n", mSafetyChecker);
        pw.decreaseIndent();
    }

    private void dumpResources(IndentingPrintWriter pw) {
        mOverlayPackagesProvider.dump(pw);
        pw.println();

        pw.println("Other overlayable app resources");
        pw.increaseIndent();
        dumpResources(pw, mContext, "cross_profile_apps", R.array.cross_profile_apps);
        dumpResources(pw, mContext, "vendor_cross_profile_apps", R.array.vendor_cross_profile_apps);
        dumpResources(pw, mContext, "config_packagesExemptFromSuspension",
                R.array.config_packagesExemptFromSuspension);
        dumpResources(pw, mContext, "policy_exempt_apps", R.array.policy_exempt_apps);
        dumpResources(pw, mContext, "vendor_policy_exempt_apps", R.array.vendor_policy_exempt_apps);
        pw.decreaseIndent();
        pw.println();
    }

    static void dumpResources(IndentingPrintWriter pw, Context context, String resName, int resId) {
        dumpApps(pw, resName, context.getResources().getStringArray(resId));
    }

    static void dumpApps(IndentingPrintWriter pw, String name, String[] apps) {
        dumpApps(pw, name, Arrays.asList(apps));
    }

    static void dumpApps(IndentingPrintWriter pw, String name, List apps) {
        if (apps == null || apps.isEmpty()) {
            pw.printf("%s: empty\n", name);
            return;
        }
        int size = apps.size();
        pw.printf("%s: %d app%s\n", name, size, size == 1 ? "" : "s");
        pw.increaseIndent();
        for (int i = 0; i < size; i++) {
            pw.printf("%d: %s\n", i, apps.get(i));
        }
        pw.decreaseIndent();
    }

    @Override
    public void onShellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err,
            String[] args, ShellCallback callback, ResultReceiver resultReceiver) {
        new DevicePolicyManagerServiceShellCommand(DevicePolicyManagerService.this).exec(
                this, in, out, err, args, callback, resultReceiver);

    }

    private String getEncryptionStatusName(int encryptionStatus) {
        switch (encryptionStatus) {
            case DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER:
                return "per-user";
            case DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED:
                return "unsupported";
            default:
                return "unknown";
        }
    }

    @Override
    public void addPersistentPreferredActivity(ComponentName who, IntentFilter filter,
            ComponentName activity) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isProfileOwner(caller)
                || isDefaultDeviceOwner(caller) || isFinancedDeviceOwner(caller));

        final int userHandle = caller.getUserId();
        if (isCoexistenceEnabled(caller)) {
            mDevicePolicyEngine.setLocalPolicy(
                    PolicyDefinition.PERSISTENT_PREFERRED_ACTIVITY(filter),
                    EnforcingAdmin.createEnterpriseEnforcingAdmin(who, userHandle),
                    activity,
                    userHandle);
        } else {
            synchronized (getLockObject()) {
                long id = mInjector.binderClearCallingIdentity();
                try {
                    mIPackageManager.addPersistentPreferredActivity(filter, activity, userHandle);
                    mIPackageManager.flushPackageRestrictionsAsUser(userHandle);
                } catch (RemoteException re) {
                    // Shouldn't happen
                    Slog.wtf(LOG_TAG, "Error adding persistent preferred activity", re);
                } finally {
                    mInjector.binderRestoreCallingIdentity(id);
                }
            }
        }
        final String activityPackage =
                (activity != null ? activity.getPackageName() : null);
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.ADD_PERSISTENT_PREFERRED_ACTIVITY)
                .setAdmin(who)
                .setStrings(activityPackage, getIntentFilterActions(filter))
                .write();
    }

    @Override
    public void clearPackagePersistentPreferredActivities(ComponentName who, String packageName) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isProfileOwner(caller)
                || isDefaultDeviceOwner(caller) || isFinancedDeviceOwner(caller));

        final int userHandle = caller.getUserId();

        if (isCoexistenceEnabled(caller)) {
            clearPackagePersistentPreferredActivitiesFromPolicyEngine(
                    EnforcingAdmin.createEnterpriseEnforcingAdmin(who, userHandle),
                    packageName,
                    userHandle);
        } else {
            synchronized (getLockObject()) {
                long id = mInjector.binderClearCallingIdentity();
                try {
                    mIPackageManager.clearPackagePersistentPreferredActivities(packageName,
                            userHandle);
                    mIPackageManager.flushPackageRestrictionsAsUser(userHandle);
                } catch (RemoteException re) {
                    // Shouldn't happen
                    Slogf.wtf(
                            LOG_TAG, "Error when clearing package persistent preferred activities",
                            re);
                } finally {
                    mInjector.binderRestoreCallingIdentity(id);
                }
            }
        }
    }

    /**
     * Remove all persistent intent handler preferences associated with the given package that were
     * set by this admin, note that is doesn't remove preferences set by other admins for the same
     * package.
     */
    private void clearPackagePersistentPreferredActivitiesFromPolicyEngine(
            EnforcingAdmin admin, String packageName, int userId) {
        Set<PolicyKey> keys = mDevicePolicyEngine.getLocalPolicyKeysSetByAdmin(
                PolicyDefinition.GENERIC_PERSISTENT_PREFERRED_ACTIVITY,
                admin,
                userId);
        for (PolicyKey key : keys) {
            if (!(key instanceof PersistentPreferredActivityPolicyKey)) {
                throw new IllegalStateException("PolicyKey for PERSISTENT_PREFERRED_ACTIVITY is not"
                        + "of type PersistentPreferredActivityPolicyKey");
            }
            PersistentPreferredActivityPolicyKey parsedKey =
                    (PersistentPreferredActivityPolicyKey) key;
            IntentFilter filter = Objects.requireNonNull(parsedKey.getFilter());

            ComponentName preferredActivity = mDevicePolicyEngine.getLocalPolicySetByAdmin(
                    PolicyDefinition.PERSISTENT_PREFERRED_ACTIVITY(filter),
                    admin,
                    userId);
            if (preferredActivity != null
                    && preferredActivity.getPackageName().equals(packageName)) {
                mDevicePolicyEngine.removeLocalPolicy(
                        PolicyDefinition.PERSISTENT_PREFERRED_ACTIVITY(filter),
                        admin,
                        userId);
            }
        }
    }

    @Override
    public void setDefaultSmsApplication(ComponentName admin, String packageName, boolean parent) {
        Objects.requireNonNull(admin, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                || (parent && isProfileOwnerOfOrganizationOwnedDevice(caller)));
        if (parent) {
            mInjector.binderWithCleanCallingIdentity(() -> enforcePackageIsSystemPackage(
                    packageName, getProfileParentId(mInjector.userHandleGetCallingUserId())));
        }

        mInjector.binderWithCleanCallingIdentity(() ->
                SmsApplication.setDefaultApplication(packageName, mContext));
    }

    @Override
    public boolean setApplicationRestrictionsManagingPackage(ComponentName admin,
            String packageName) {
        try {
            setDelegatedScopePreO(admin, packageName, DELEGATION_APP_RESTRICTIONS);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return true;
    }

    @Override
    public String getApplicationRestrictionsManagingPackage(ComponentName admin) {
        final List<String> delegatePackages = getDelegatePackages(admin,
                DELEGATION_APP_RESTRICTIONS);
        return delegatePackages.size() > 0 ? delegatePackages.get(0) : null;
    }

    @Override
    public boolean isCallerApplicationRestrictionsManagingPackage(String callerPackage) {
        return isCallerDelegate(callerPackage, getCallerIdentity().getUid(),
                DELEGATION_APP_RESTRICTIONS);
    }

    @Override
    public void setApplicationRestrictions(ComponentName who, String callerPackage,
            String packageName, Bundle settings) {
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_APP_RESTRICTIONS)));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_APPLICATION_RESTRICTIONS);

        mInjector.binderWithCleanCallingIdentity(() -> {
            mUserManager.setApplicationRestrictions(packageName, settings,
                    caller.getUserHandle());
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.SET_APPLICATION_RESTRICTIONS)
                    .setAdmin(caller.getPackageName())
                    .setBoolean(/* isDelegate */ who == null)
                    .setStrings(packageName)
                    .write();
        });
    }

    @Override
    public void setTrustAgentConfiguration(ComponentName admin, ComponentName agent,
            PersistableBundle args, boolean parent) {
        if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
            return;
        }
        Objects.requireNonNull(admin, "admin is null");
        Objects.requireNonNull(agent, "agent is null");
        final int userHandle = UserHandle.getCallingUserId();
        synchronized (getLockObject()) {
            ActiveAdmin ap = getActiveAdminForCallerLocked(admin,
                    DeviceAdminInfo.USES_POLICY_DISABLE_KEYGUARD_FEATURES, parent);
            checkCanExecuteOrThrowUnsafe(
                    DevicePolicyManager.OPERATION_SET_TRUST_AGENT_CONFIGURATION);

            ap.trustAgentInfos.put(agent.flattenToString(), new TrustAgentInfo(args));
            saveSettingsLocked(userHandle);
        }
    }

    @Override
    public List<PersistableBundle> getTrustAgentConfiguration(ComponentName admin,
            ComponentName agent, int userHandle, boolean parent) {
        if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
            return null;
        }
        Objects.requireNonNull(agent, "agent null");
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));

        synchronized (getLockObject()) {
            final String componentName = agent.flattenToString();
            if (admin != null) {
                final ActiveAdmin ap = getActiveAdminUncheckedLocked(admin, userHandle, parent);
                if (ap == null) return null;
                TrustAgentInfo trustAgentInfo = ap.trustAgentInfos.get(componentName);
                if (trustAgentInfo == null || trustAgentInfo.options == null) return null;
                List<PersistableBundle> result = new ArrayList<>();
                result.add(trustAgentInfo.options);
                return result;
            }

            // Return strictest policy for this user and profiles that are visible from this user.
            List<PersistableBundle> result = null;
            // Search through all admins that use KEYGUARD_DISABLE_TRUST_AGENTS and keep track
            // of the options. If any admin doesn't have options, discard options for the rest
            // and return null.
            List<ActiveAdmin> admins = getActiveAdminsForLockscreenPoliciesLocked(
                    getProfileParentUserIfRequested(userHandle, parent));
            boolean allAdminsHaveOptions = true;
            final int N = admins.size();
            for (int i = 0; i < N; i++) {
                final ActiveAdmin active = admins.get(i);

                final boolean disablesTrust = (active.disabledKeyguardFeatures
                        & DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS) != 0;
                final TrustAgentInfo info = active.trustAgentInfos.get(componentName);
                if (info != null && info.options != null && !info.options.isEmpty()) {
                    if (disablesTrust) {
                        if (result == null) {
                            result = new ArrayList<>();
                        }
                        result.add(info.options);
                    } else {
                        Slogf.w(LOG_TAG, "Ignoring admin %s because it has trust options but "
                                + "doesn't declare KEYGUARD_DISABLE_TRUST_AGENTS", active.info);
                    }
                } else if (disablesTrust) {
                    allAdminsHaveOptions = false;
                    break;
                }
            }
            return allAdminsHaveOptions ? result : null;
        }
    }

    @Override
    public void setRestrictionsProvider(ComponentName who, ComponentName permissionProvider) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_RESTRICTIONS_PROVIDER);

        synchronized (getLockObject()) {
            int userHandle = caller.getUserId();
            DevicePolicyData userData = getUserData(userHandle);
            userData.mRestrictionsProvider = permissionProvider;
            saveSettingsLocked(userHandle);
        }
    }

    @Override
    public ComponentName getRestrictionsProvider(int userHandle) {
        Preconditions.checkCallAuthorization(isSystemUid(getCallerIdentity()),
                String.format(NOT_SYSTEM_CALLER_MSG, "query the permission provider"));
        synchronized (getLockObject()) {
            DevicePolicyData userData = getUserData(userHandle);
            return userData != null ? userData.mRestrictionsProvider : null;
        }
    }

    @Override
    public void addCrossProfileIntentFilter(ComponentName who, IntentFilter filter, int flags) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));
        int callingUserId = caller.getUserId();
        synchronized (getLockObject()) {
            long id = mInjector.binderClearCallingIdentity();
            try {
                UserInfo parent = mUserManager.getProfileParent(callingUserId);
                if (parent == null) {
                    Slogf.e(LOG_TAG, "Cannot call addCrossProfileIntentFilter if there is no "
                            + "parent");
                    return;
                }
                if ((flags & DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED) != 0) {
                    mIPackageManager.addCrossProfileIntentFilter(
                            filter, who.getPackageName(), callingUserId, parent.id, 0);
                }
                if ((flags & DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT) != 0) {
                    mIPackageManager.addCrossProfileIntentFilter(filter, who.getPackageName(),
                            parent.id, callingUserId, 0);
                }
            } catch (RemoteException re) {
                // Shouldn't happen
                Slogf.wtf(LOG_TAG, "Error adding cross profile intent filter", re);
            } finally {
                mInjector.binderRestoreCallingIdentity(id);
            }
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.ADD_CROSS_PROFILE_INTENT_FILTER)
                .setAdmin(who)
                .setStrings(getIntentFilterActions(filter))
                .setInt(flags)
                .write();
    }

    private static String[] getIntentFilterActions(IntentFilter filter) {
        if (filter == null) {
            return null;
        }
        final int actionsCount = filter.countActions();
        final String[] actions = new String[actionsCount];
        for (int i = 0; i < actionsCount; i++) {
            actions[i] = filter.getAction(i);
        }
        return actions;
    }

    @Override
    public void clearCrossProfileIntentFilters(ComponentName who) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));

        int callingUserId = caller.getUserId();
        synchronized (getLockObject()) {
            long id = mInjector.binderClearCallingIdentity();
            try {
                UserInfo parent = mUserManager.getProfileParent(callingUserId);
                if (parent == null) {
                    Slogf.e(LOG_TAG, "Cannot call clearCrossProfileIntentFilter if there is no "
                            + "parent");
                    return;
                }
                // Removing those that go from the managed profile to the parent.
                mIPackageManager.clearCrossProfileIntentFilters(
                        callingUserId, who.getPackageName());
                // And those that go from the parent to the managed profile.
                // If we want to support multiple managed profiles, we will have to only remove
                // those that have callingUserId as their target.
                mIPackageManager.clearCrossProfileIntentFilters(parent.id, who.getPackageName());
            } catch (RemoteException re) {
                // Shouldn't happen
                Slogf.wtf(LOG_TAG, "Error clearing cross profile intent filters", re);
            } finally {
                mInjector.binderRestoreCallingIdentity(id);
            }
        }
    }

    /**
     * @return true if all packages in enabledPackages are either in the list
     * permittedList or are a system app.
     */
    private boolean checkPackagesInPermittedListOrSystem(List<String> enabledPackages,
            List<String> permittedList, int userIdToCheck) {
        long id = mInjector.binderClearCallingIdentity();
        try {
            // If we have an enabled packages list for a managed profile the packages
            // we should check are installed for the parent user.
            UserInfo user = getUserInfo(userIdToCheck);
            if (user.isManagedProfile()) {
                userIdToCheck = user.profileGroupId;
            }

            for (String enabledPackage : enabledPackages) {
                boolean systemService = false;
                try {
                    ApplicationInfo applicationInfo = mIPackageManager.getApplicationInfo(
                            enabledPackage, PackageManager.MATCH_UNINSTALLED_PACKAGES,
                            userIdToCheck);

                    if (applicationInfo == null) {
                        return false;
                    }

                    systemService = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                } catch (RemoteException e) {
                    Slogf.i(LOG_TAG, "Can't talk to package managed", e);
                }
                if (!systemService && !permittedList.contains(enabledPackage)) {
                    return false;
                }
            }
        } finally {
            mInjector.binderRestoreCallingIdentity(id);
        }
        return true;
    }

    /**
     * Invoke a method in AccessibilityManager ensuring the client is removed.
     */
    private <T> T withAccessibilityManager(
            int userId, Function<AccessibilityManager, T> function) {
        // Not using AccessibilityManager.getInstance because that guesses
        // at the user you require based on callingUid and caches for a given
        // process.
        final IBinder iBinder = ServiceManager.getService(Context.ACCESSIBILITY_SERVICE);
        final IAccessibilityManager service = iBinder == null
                ? null : IAccessibilityManager.Stub.asInterface(iBinder);
        final AccessibilityManager am = new AccessibilityManager(mContext, service, userId);
        try {
            return function.apply(am);
        } finally {
            am.removeClient();
        }
    }

    @Override
    public boolean setPermittedAccessibilityServices(ComponentName who, List<String> packageList) {
        if (!mHasFeature) {
            return false;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDeviceOwner(caller) || isProfileOwner(caller));

        if (packageList != null) {
            int userId = caller.getUserId();
            final List<AccessibilityServiceInfo> enabledServices;
            long id = mInjector.binderClearCallingIdentity();
            try {
                UserInfo user = getUserInfo(userId);
                if (user.isManagedProfile()) {
                    userId = user.profileGroupId;
                }
                enabledServices = withAccessibilityManager(userId,
                        am -> am.getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK));
            } finally {
                mInjector.binderRestoreCallingIdentity(id);
            }

            if (enabledServices != null) {
                List<String> enabledPackages = new ArrayList<>();
                for (AccessibilityServiceInfo service : enabledServices) {
                    enabledPackages.add(service.getResolveInfo().serviceInfo.packageName);
                }
                if (!checkPackagesInPermittedListOrSystem(enabledPackages, packageList,
                        userId)) {
                    Slogf.e(LOG_TAG, "Cannot set permitted accessibility services, "
                            + "because it contains already enabled accesibility services.");
                    return false;
                }
            }
        }

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            admin.permittedAccessiblityServices = packageList;
            saveSettingsLocked(UserHandle.getCallingUserId());
        }
        final String[] packageArray =
                packageList != null ? ((List<String>) packageList).toArray(new String[0]) : null;
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_PERMITTED_ACCESSIBILITY_SERVICES)
                .setAdmin(who)
                .setStrings(packageArray)
                .write();
        return true;
    }

    @Override
    public List<String> getPermittedAccessibilityServices(ComponentName who) {
        if (!mHasFeature) {
            return null;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            return admin.permittedAccessiblityServices;
        }
    }

    @Override
    public List<String> getPermittedAccessibilityServicesForUser(int userId) {
        if (!mHasFeature) {
            return null;
        }
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(canManageUsers(caller) || canQueryAdminPolicy(caller));

        synchronized (getLockObject()) {
            List<String> result = null;
            // If we have multiple profiles we return the intersection of the
            // permitted lists. This can happen in cases where we have a device
            // and profile owner.
            int[] profileIds = mUserManager.getProfileIdsWithDisabled(userId);
            for (int profileId : profileIds) {
                // Just loop though all admins, only device or profiles
                // owners can have permitted lists set.
                DevicePolicyData policy = getUserDataUnchecked(profileId);
                final int N = policy.mAdminList.size();
                for (int j = 0; j < N; j++) {
                    ActiveAdmin admin = policy.mAdminList.get(j);
                    List<String> fromAdmin = admin.permittedAccessiblityServices;
                    if (fromAdmin != null) {
                        if (result == null) {
                            result = new ArrayList<>(fromAdmin);
                        } else {
                            result.retainAll(fromAdmin);
                        }
                    }
                }
            }

            // If we have a permitted list add all system accessibility services.
            if (result != null) {
                long id = mInjector.binderClearCallingIdentity();
                try {
                    UserInfo user = getUserInfo(userId);
                    if (user.isManagedProfile()) {
                        userId = user.profileGroupId;
                    }
                    final List<AccessibilityServiceInfo> installedServices =
                            withAccessibilityManager(userId,
                                    AccessibilityManager::getInstalledAccessibilityServiceList);

                    if (installedServices != null) {
                        for (AccessibilityServiceInfo service : installedServices) {
                            ServiceInfo serviceInfo = service.getResolveInfo().serviceInfo;
                            ApplicationInfo applicationInfo = serviceInfo.applicationInfo;
                            if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                                result.add(serviceInfo.packageName);
                            }
                        }
                    }
                } finally {
                    mInjector.binderRestoreCallingIdentity(id);
                }
            }

            return result;
        }
    }

    @Override
    public boolean isAccessibilityServicePermittedByAdmin(ComponentName who, String packageName,
            int userHandle) {
        if (!mHasFeature) {
            return true;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        Preconditions.checkStringNotEmpty(packageName, "packageName is null");
        Preconditions.checkCallAuthorization(isSystemUid(getCallerIdentity()),
                String.format(NOT_SYSTEM_CALLER_MSG,
                        "query if an accessibility service is disabled by admin"));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
            if (admin == null) {
                return false;
            }
            if (admin.permittedAccessiblityServices == null) {
                return true;
            }
            return checkPackagesInPermittedListOrSystem(Collections.singletonList(packageName),
                    admin.permittedAccessiblityServices, userHandle);
        }
    }

    @Override
    public boolean setPermittedInputMethods(ComponentName who, List<String> packageList,
            boolean calledOnParentInstance) {
        if (!mHasFeature) {
            return false;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        final int userId = getProfileParentUserIfRequested(
                caller.getUserId(), calledOnParentInstance);
        if (calledOnParentInstance) {
            Preconditions.checkCallAuthorization(isProfileOwnerOfOrganizationOwnedDevice(caller));
            Preconditions.checkArgument(packageList == null || packageList.isEmpty(),
                    "Permitted input methods must allow all input methods or only "
                            + "system input methods when called on the parent instance of an "
                            + "organization-owned device");
        } else {
            Preconditions.checkCallAuthorization(
                    isDefaultDeviceOwner(caller) || isProfileOwner(caller));
        }

        if (packageList != null) {
            List<InputMethodInfo> enabledImes = mInjector.binderWithCleanCallingIdentity(() ->
                    InputMethodManagerInternal.get().getEnabledInputMethodListAsUser(userId));
            if (enabledImes != null) {
                List<String> enabledPackages = new ArrayList<String>();
                for (InputMethodInfo ime : enabledImes) {
                    enabledPackages.add(ime.getPackageName());
                }
                if (!checkPackagesInPermittedListOrSystem(enabledPackages, packageList,
                        userId)) {
                    Slogf.e(LOG_TAG, "Cannot set permitted input methods, because the list of "
                            + "permitted input methods excludes an already-enabled input method.");
                    return false;
                }
            }
        }

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getParentOfAdminIfRequired(
                    getProfileOwnerOrDeviceOwnerLocked(caller.getUserId()), calledOnParentInstance);
            admin.permittedInputMethods = packageList;
            saveSettingsLocked(caller.getUserId());
        }

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_PERMITTED_INPUT_METHODS)
                .setAdmin(who)
                .setStrings(getStringArrayForLogging(packageList, calledOnParentInstance))
                .write();
        return true;
    }

    private String[] getStringArrayForLogging(List list, boolean calledOnParentInstance) {
        List<String> stringList = new ArrayList<String>();
        stringList.add(calledOnParentInstance ? CALLED_FROM_PARENT : NOT_CALLED_FROM_PARENT);
        if (list == null) {
            stringList.add(NULL_STRING_ARRAY);
        } else {
            stringList.addAll((List<String>) list);
        }
        return stringList.toArray(new String[0]);
    }

    @Override
    public List<String> getPermittedInputMethods(ComponentName who,
            boolean calledOnParentInstance) {
        if (!mHasFeature) {
            return null;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        if (calledOnParentInstance) {
            Preconditions.checkCallAuthorization(isProfileOwnerOfOrganizationOwnedDevice(caller));
        } else {
            Preconditions.checkCallAuthorization(
                    isDefaultDeviceOwner(caller) || isProfileOwner(caller));
        }

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getParentOfAdminIfRequired(
                    getProfileOwnerOrDeviceOwnerLocked(caller.getUserId()), calledOnParentInstance);
            return admin.permittedInputMethods;
        }
    }

    @Override
    public @Nullable List<String> getPermittedInputMethodsAsUser(@UserIdInt int userId) {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userId));
        Preconditions.checkCallAuthorization(canManageUsers(caller) || canQueryAdminPolicy(caller));
        final long callingIdentity = Binder.clearCallingIdentity();
        try {
            return getPermittedInputMethodsUnchecked(userId);
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    private @Nullable List<String> getPermittedInputMethodsUnchecked(@UserIdInt int userId) {
        synchronized (getLockObject()) {
            List<String> result = null;
            // Only device or profile owners can have permitted lists set.
            List<ActiveAdmin> admins = getActiveAdminsForAffectedUserLocked(userId);
            for (ActiveAdmin admin: admins) {
                List<String> fromAdmin = admin.permittedInputMethods;
                if (fromAdmin != null) {
                    if (result == null) {
                        result = new ArrayList<String>(fromAdmin);
                    } else {
                        result.retainAll(fromAdmin);
                    }
                }
            }

            // If we have a permitted list add all system input methods.
            if (result != null) {
                List<InputMethodInfo> imes = InputMethodManagerInternal
                        .get().getInputMethodListAsUser(userId);
                if (imes != null) {
                    for (InputMethodInfo ime : imes) {
                        ServiceInfo serviceInfo = ime.getServiceInfo();
                        ApplicationInfo applicationInfo = serviceInfo.applicationInfo;
                        if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                            result.add(serviceInfo.packageName);
                        }
                    }
                }
            }
            return result;
        }
    }

    @Override
    public boolean isInputMethodPermittedByAdmin(ComponentName who, String packageName,
            int userHandle, boolean calledOnParentInstance) {
        if (!mHasFeature) {
            return true;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        Preconditions.checkStringNotEmpty(packageName, "packageName is null");
        Preconditions.checkCallAuthorization(isSystemUid(getCallerIdentity()),
                String.format(NOT_SYSTEM_CALLER_MSG,
                        "query if an input method is disabled by admin"));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getParentOfAdminIfRequired(
                    getActiveAdminUncheckedLocked(who, userHandle), calledOnParentInstance);
            if (admin == null) {
                return false;
            }
            if (admin.permittedInputMethods == null) {
                return true;
            }
            return checkPackagesInPermittedListOrSystem(Collections.singletonList(packageName),
                    admin.permittedInputMethods, userHandle);
        }
    }

    @Override
    public boolean setPermittedCrossProfileNotificationListeners(
            ComponentName who, List<String> packageList) {
        if (!mHasFeature) {
            return false;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);

        if (!isManagedProfile(caller.getUserId())) {
            return false;
        }
        Preconditions.checkCallAuthorization(isProfileOwner(caller));
        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            admin.permittedNotificationListeners = packageList;
            saveSettingsLocked(caller.getUserId());
        }
        return true;
    }

    @Override
    public List<String> getPermittedCrossProfileNotificationListeners(ComponentName who) {
        if (!mHasFeature) {
            return null;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDeviceOwner(caller) || isProfileOwner(caller));

        synchronized (getLockObject()) {
            // API contract is to return null if there are no permitted cross-profile notification
            // listeners, including in Device Owner mode.
            ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            return admin.permittedNotificationListeners;
        }
    }

    @Override
    public boolean isNotificationListenerServicePermitted(String packageName, int userId) {
        if (!mHasFeature) {
            return true;
        }

        Preconditions.checkStringNotEmpty(packageName, "packageName is null or empty");
        Preconditions.checkCallAuthorization(isSystemUid(getCallerIdentity()),
                String.format(NOT_SYSTEM_CALLER_MSG,
                        "query if a notification listener service is permitted"));

        synchronized (getLockObject()) {
            ActiveAdmin profileOwner = getProfileOwnerAdminLocked(userId);
            if (profileOwner == null || profileOwner.permittedNotificationListeners == null) {
                return true;
            }
            return checkPackagesInPermittedListOrSystem(Collections.singletonList(packageName),
                    profileOwner.permittedNotificationListeners, userId);

        }
    }

    private void maybeSendAdminEnabledBroadcastLocked(int userHandle) {
        DevicePolicyData policyData = getUserData(userHandle);
        if (policyData.mAdminBroadcastPending) {
            // Send the initialization data to profile owner and delete the data
            ActiveAdmin admin = getProfileOwnerAdminLocked(userHandle);
            boolean clearInitBundle = true;
            if (admin != null) {
                PersistableBundle initBundle = policyData.mInitBundle;
                clearInitBundle = sendAdminCommandLocked(admin,
                        DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED,
                        initBundle == null ? null : new Bundle(initBundle),
                        /* result= */ null ,
                        /* inForeground= */ true);
            }
            if (clearInitBundle) {
                // If there's no admin or we've successfully called the admin, clear the init bundle
                // otherwise, keep it around
                policyData.mInitBundle = null;
                policyData.mAdminBroadcastPending = false;
                saveSettingsLocked(userHandle);
            }
        }
    }

    @Override
    public UserHandle createAndManageUser(ComponentName admin, String name,
            ComponentName profileOwner, PersistableBundle adminExtras, int flags) {
        Objects.requireNonNull(admin, "admin is null");
        Objects.requireNonNull(profileOwner, "profileOwner is null");
        if (!admin.getPackageName().equals(profileOwner.getPackageName())) {
            throw new IllegalArgumentException("profileOwner " + profileOwner + " and admin "
                    + admin + " are not in the same package");
        }
        final CallerIdentity caller = getCallerIdentity(admin);
        // Only allow the system user to use this method
        Preconditions.checkCallAuthorization(caller.getUserHandle().isSystem(),
                "createAndManageUser was called from non-system user");
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_CREATE_AND_MANAGE_USER);

        final boolean ephemeral = (flags & DevicePolicyManager.MAKE_USER_EPHEMERAL) != 0;
        final boolean demo = (flags & DevicePolicyManager.MAKE_USER_DEMO) != 0
                && UserManager.isDeviceInDemoMode(mContext);
        final boolean leaveAllSystemAppsEnabled = (flags & LEAVE_ALL_SYSTEM_APPS_ENABLED) != 0;
        final int targetSdkVersion;

        // Create user.
        UserHandle user = null;
        synchronized (getLockObject()) {
            final long id = mInjector.binderClearCallingIdentity();
            try {
                targetSdkVersion = mInjector.getPackageManagerInternal().getUidTargetSdkVersion(
                        caller.getUid());

                // Return detail error code for checks inside
                // UserManagerService.createUserInternalUnchecked.
                DeviceStorageMonitorInternal deviceStorageMonitorInternal =
                        LocalServices.getService(DeviceStorageMonitorInternal.class);
                if (deviceStorageMonitorInternal.isMemoryLow()) {
                    if (targetSdkVersion >= Build.VERSION_CODES.P) {
                        throw new ServiceSpecificException(
                                UserManager.USER_OPERATION_ERROR_LOW_STORAGE, "low device storage");
                    } else {
                        return null;
                    }
                }

                String userType = demo ? UserManager.USER_TYPE_FULL_DEMO
                        : UserManager.USER_TYPE_FULL_SECONDARY;
                int userInfoFlags = ephemeral ? UserInfo.FLAG_EPHEMERAL : 0;

                if (!mUserManager.canAddMoreUsers(userType)) {
                    if (targetSdkVersion >= Build.VERSION_CODES.P) {
                        throw new ServiceSpecificException(
                                UserManager.USER_OPERATION_ERROR_MAX_USERS, "user limit reached");
                    } else {
                        return null;
                    }
                }

                String[] disallowedPackages = null;
                if (!leaveAllSystemAppsEnabled) {
                    disallowedPackages = mOverlayPackagesProvider.getNonRequiredApps(admin,
                            UserHandle.myUserId(), ACTION_PROVISION_MANAGED_USER).toArray(
                            new String[0]);
                }

                Object token = new Object();
                Slogf.d(LOG_TAG, "Adding new pending token: " + token);
                mPendingUserCreatedCallbackTokens.add(token);
                try {
                    UserInfo userInfo = mUserManagerInternal.createUserEvenWhenDisallowed(name,
                            userType, userInfoFlags, disallowedPackages, token);
                    if (userInfo != null) {
                        user = userInfo.getUserHandle();
                    }
                } catch (UserManager.CheckedUserOperationException e) {
                    Slogf.e(LOG_TAG, "Couldn't createUserEvenWhenDisallowed", e);
                }
            } finally {
                mInjector.binderRestoreCallingIdentity(id);
            }
        } // synchronized

        if (user == null) {
            if (targetSdkVersion >= Build.VERSION_CODES.P) {
                throw new ServiceSpecificException(UserManager.USER_OPERATION_ERROR_UNKNOWN,
                        "failed to create user");
            } else {
                return null;
            }
        }

        final int userHandle = user.getIdentifier();
        final long id = mInjector.binderClearCallingIdentity();
        try {
            maybeInstallDevicePolicyManagementRoleHolderInUser(userHandle);

            manageUserUnchecked(admin, profileOwner, userHandle, adminExtras,
                    /* showDisclaimer= */ true);

            if ((flags & DevicePolicyManager.SKIP_SETUP_WIZARD) != 0) {
                Settings.Secure.putIntForUser(mContext.getContentResolver(),
                        Settings.Secure.USER_SETUP_COMPLETE, 1, userHandle);
            }

            sendProvisioningCompletedBroadcast(
                    userHandle, ACTION_PROVISION_MANAGED_USER, leaveAllSystemAppsEnabled);

            return user;
        } catch (Throwable re) {
            mUserManager.removeUser(userHandle);
            if (targetSdkVersion >= Build.VERSION_CODES.P) {
                throw new ServiceSpecificException(UserManager.USER_OPERATION_ERROR_UNKNOWN,
                        re.getMessage());
            } else {
                return null;
            }
        } finally {
            mInjector.binderRestoreCallingIdentity(id);
        }
    }

    private void sendProvisioningCompletedBroadcast(
            int user, String action, boolean leaveAllSystemAppsEnabled) {
        final Intent intent = new Intent(DevicePolicyManager.ACTION_PROVISIONING_COMPLETED)
                .putExtra(Intent.EXTRA_USER_HANDLE, user)
                .putExtra(Intent.EXTRA_USER, UserHandle.of(user))
                .putExtra(
                        DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                        leaveAllSystemAppsEnabled)
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ACTION,
                        action)
                .setPackage(getManagedProvisioningPackage(mContext))
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcastAsUser(intent, UserHandle.SYSTEM);
    }

    private void manageUserUnchecked(ComponentName admin, ComponentName profileOwner,
            @UserIdInt int userId, @Nullable PersistableBundle adminExtras,
            boolean showDisclaimer) {
        synchronized (getLockObject()) {
            if (VERBOSE_LOG) {
                Slogf.v(LOG_TAG, "manageUserUnchecked(): admin=" + admin + ", po=" + profileOwner
                        + ", userId=" + userId + ", hasAdminExtras=" + (adminExtras != null)
                        + ", showDisclaimer=" + showDisclaimer);
            }
        }
        final String adminPkg = admin.getPackageName();
        mInjector.binderWithCleanCallingIdentity(() -> {
            try {
                // Install the profile owner if not present.
                if (!mIPackageManager.isPackageAvailable(adminPkg, userId)) {
                    mIPackageManager.installExistingPackageAsUser(adminPkg, userId,
                            PackageManager.INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS,
                            PackageManager.INSTALL_REASON_POLICY,
                            /* allowlistedRestrictedPermissions= */ null);
                }
            } catch (RemoteException e) {
                // Does not happen, same process
                Slogf.wtf(LOG_TAG, e, "Failed to install admin package %s for user %d",
                        adminPkg, userId);
            }
        });

        // Set admin.
        setActiveAdmin(profileOwner, /* refreshing= */ true, userId);
        setProfileOwner(profileOwner, userId);

        synchronized (getLockObject()) {
            DevicePolicyData policyData = getUserData(userId);
            policyData.mInitBundle = adminExtras;
            policyData.mAdminBroadcastPending = true;
            policyData.mNewUserDisclaimer = showDisclaimer
                    ? DevicePolicyData.NEW_USER_DISCLAIMER_NEEDED
                    : DevicePolicyData.NEW_USER_DISCLAIMER_NOT_NEEDED;
            saveSettingsLocked(userId);

        }
    }

    private void handleNewUserCreated(UserInfo user, @Nullable Object token) {
        if (VERBOSE_LOG) {
            Slogf.v(LOG_TAG, "handleNewUserCreated(): user=" + user.toFullString()
                    + ", token=" + token);
        }

        final int userId = user.id;
        if (token != null) {
            synchronized (getLockObject()) {
                if (mPendingUserCreatedCallbackTokens.contains(token)) {
                    // Ignore because it was triggered by createAndManageUser()
                    Slogf.d(LOG_TAG, "handleNewUserCreated(): ignoring for user " + userId
                            + " due to token " + token);
                    mPendingUserCreatedCallbackTokens.remove(token);
                    return;
                }
            }
        }

        if (!mOwners.hasDeviceOwner() || !user.isFull() || user.isManagedProfile()
                || user.isGuest()) {
            return;
        }

        if (mInjector.userManagerIsHeadlessSystemUserMode()) {
            ComponentName admin = mOwners.getDeviceOwnerComponent();
            Slogf.i(LOG_TAG, "Automatically setting profile owner (" + admin + ") on new user "
                    + userId);
            manageUserUnchecked(/* deviceOwner= */ admin, /* profileOwner= */ admin,
                    /* managedUser= */ userId, /* adminExtras= */ null, /* showDisclaimer= */ true);
        } else {
            Slogf.i(LOG_TAG, "User %d added on DO mode; setting ShowNewUserDisclaimer", userId);
            setShowNewUserDisclaimer(userId, DevicePolicyData.NEW_USER_DISCLAIMER_NEEDED);
        }
    }

    @Override
    public void acknowledgeNewUserDisclaimer(@UserIdInt int userId) {
        CallerIdentity callerIdentity = getCallerIdentity();
        Preconditions.checkCallAuthorization(canManageUsers(callerIdentity)
                || hasCallingOrSelfPermission(permission.INTERACT_ACROSS_USERS));

        setShowNewUserDisclaimer(userId, DevicePolicyData.NEW_USER_DISCLAIMER_ACKNOWLEDGED);
    }

    private void setShowNewUserDisclaimer(@UserIdInt int userId, String value) {
        Slogf.i(LOG_TAG, "Setting new user disclaimer for user " + userId + " as " + value);
        synchronized (getLockObject()) {
            DevicePolicyData policyData = getUserData(userId);
            policyData.mNewUserDisclaimer = value;
            saveSettingsLocked(userId);
        }
    }

    private void showNewUserDisclaimerIfNecessary(@UserIdInt int userId) {
        boolean mustShow;
        synchronized (getLockObject()) {
            DevicePolicyData policyData = getUserData(userId);
            if (VERBOSE_LOG) {
                Slogf.v(LOG_TAG, "showNewUserDisclaimerIfNecessary(" + userId + "): "
                        + policyData.mNewUserDisclaimer + ")");
            }
            mustShow = DevicePolicyData.NEW_USER_DISCLAIMER_NEEDED
                    .equals(policyData.mNewUserDisclaimer);
        }
        if (!mustShow) return;

        Intent intent = new Intent(DevicePolicyManager.ACTION_SHOW_NEW_USER_DISCLAIMER);

        // TODO(b/172691310): add CTS tests to make sure disclaimer is shown
        Slogf.i(LOG_TAG, "Dispatching ACTION_SHOW_NEW_USER_DISCLAIMER intent");
        mContext.sendBroadcastAsUser(intent, UserHandle.of(userId));
    }

    @Override
    public boolean isNewUserDisclaimerAcknowledged(@UserIdInt int userId) {
        CallerIdentity callerIdentity = getCallerIdentity();
        Preconditions.checkCallAuthorization(canManageUsers(callerIdentity)
                || hasCallingOrSelfPermission(permission.INTERACT_ACROSS_USERS));
        synchronized (getLockObject()) {
            DevicePolicyData policyData = getUserData(userId);
            return policyData.isNewUserDisclaimerAcknowledged();
        }
    }

    @Override
    public boolean removeUser(ComponentName who, UserHandle userHandle) {
        Objects.requireNonNull(who, "ComponentName is null");
        Objects.requireNonNull(userHandle, "UserHandle is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_REMOVE_USER);

        return mInjector.binderWithCleanCallingIdentity(() -> {
            String restriction = isManagedProfile(userHandle.getIdentifier())
                    ? UserManager.DISALLOW_REMOVE_MANAGED_PROFILE
                    : UserManager.DISALLOW_REMOVE_USER;
            if (isAdminAffectedByRestriction(who, restriction, caller.getUserId())) {
                Slogf.w(LOG_TAG, "The device owner cannot remove a user because %s is enabled, and "
                        + "was not set by the device owner", restriction);
                return false;
            }
            return mUserManagerInternal.removeUserEvenWhenDisallowed(userHandle.getIdentifier());
        });
    }

    private boolean isAdminAffectedByRestriction(
            ComponentName admin, String userRestriction, int userId) {
        switch(mUserManager.getUserRestrictionSource(userRestriction, UserHandle.of(userId))) {
            case UserManager.RESTRICTION_NOT_SET:
                return false;
            case UserManager.RESTRICTION_SOURCE_DEVICE_OWNER:
                return !isDeviceOwner(admin, userId);
            case UserManager.RESTRICTION_SOURCE_PROFILE_OWNER:
                return !isProfileOwner(admin, userId);
            default:
                return true;
        }
    }

    @Override
    public boolean switchUser(ComponentName who, UserHandle userHandle) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SWITCH_USER);

        boolean switched = false;
        // Save previous logout user id in case of failure
        int logoutUserId = getLogoutUserIdUnchecked();
        synchronized (getLockObject()) {
            long id = mInjector.binderClearCallingIdentity();
            try {
                int userId = UserHandle.USER_SYSTEM;
                if (userHandle != null) {
                    userId = userHandle.getIdentifier();
                }
                Slogf.i(LOG_TAG, "Switching to user %d (logout user is %d)", userId, logoutUserId);
                setLogoutUserIdLocked(UserHandle.USER_CURRENT);
                switched = mInjector.getIActivityManager().switchUser(userId);
                if (!switched) {
                    Slogf.w(LOG_TAG, "Failed to switch to user %d", userId);
                } else {
                    Slogf.d(LOG_TAG, "Switched");
                }
                return switched;
            } catch (RemoteException e) {
                Slogf.e(LOG_TAG, "Couldn't switch user", e);
                return false;
            } finally {
                mInjector.binderRestoreCallingIdentity(id);
                if (!switched) {
                    setLogoutUserIdLocked(logoutUserId);
                }
            }
        }
    }

    @Override
    public int getLogoutUserId() {
        Preconditions.checkCallAuthorization(canManageUsers(getCallerIdentity())
                || hasCallingOrSelfPermission(permission.INTERACT_ACROSS_USERS));

        return getLogoutUserIdUnchecked();
    }

    private @UserIdInt int getLogoutUserIdUnchecked() {
        synchronized (getLockObject()) {
            return mLogoutUserId;
        }
    }

    private void clearLogoutUser() {
        synchronized (getLockObject()) {
            setLogoutUserIdLocked(UserHandle.USER_NULL);
        }
    }

    @GuardedBy("getLockObject()")
    private void setLogoutUserIdLocked(@UserIdInt int userId) {
        if (userId == UserHandle.USER_CURRENT) {
            userId = getCurrentForegroundUserId();
        }

        Slogf.d(LOG_TAG, "setLogoutUserId(): %d -> %d", mLogoutUserId, userId);
        mLogoutUserId = userId;
    }

    @Override
    public int startUserInBackground(ComponentName who, UserHandle userHandle) {
        Objects.requireNonNull(who, "ComponentName is null");
        Objects.requireNonNull(userHandle, "UserHandle is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_START_USER_IN_BACKGROUND);

        final int userId = userHandle.getIdentifier();
        if (isManagedProfile(userId)) {
            Slogf.w(LOG_TAG, "Managed profile cannot be started in background");
            return UserManager.USER_OPERATION_ERROR_MANAGED_PROFILE;
        }

        final long id = mInjector.binderClearCallingIdentity();
        try {
            if (!mInjector.getActivityManagerInternal().canStartMoreUsers()) {
                Slogf.w(LOG_TAG, "Cannot start user %d, too many users in background", userId);
                return UserManager.USER_OPERATION_ERROR_MAX_RUNNING_USERS;
            }

            Slogf.i(LOG_TAG, "Starting user %d in background", userId);
            if (mInjector.getIActivityManager().startUserInBackground(userId)) {
                return UserManager.USER_OPERATION_SUCCESS;
            } else {
                Slogf.w(LOG_TAG, "failed to start user %d in background", userId);
                return UserManager.USER_OPERATION_ERROR_UNKNOWN;
            }
        } catch (RemoteException e) {
            // Same process, should not happen.
            return UserManager.USER_OPERATION_ERROR_UNKNOWN;
        } finally {
            mInjector.binderRestoreCallingIdentity(id);
        }
    }

    @Override
    public int stopUser(ComponentName who, UserHandle userHandle) {
        Objects.requireNonNull(who, "ComponentName is null");
        Objects.requireNonNull(userHandle, "UserHandle is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_STOP_USER);

        final int userId = userHandle.getIdentifier();
        if (isManagedProfile(userId)) {
            Slogf.w(LOG_TAG, "Managed profile cannot be stopped");
            return UserManager.USER_OPERATION_ERROR_MANAGED_PROFILE;
        }

        return stopUserUnchecked(userId);
    }

    @Override
    public int logoutUser(ComponentName who) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_LOGOUT_USER);

        final int callingUserId = caller.getUserId();
        synchronized (getLockObject()) {
            if (!isUserAffiliatedWithDeviceLocked(callingUserId)) {
                throw new SecurityException("Admin " + who +
                        " is neither the device owner or affiliated user's profile owner.");
            }
        }

        if (isManagedProfile(callingUserId)) {
            Slogf.w(LOG_TAG, "Managed profile cannot be logout");
            return UserManager.USER_OPERATION_ERROR_MANAGED_PROFILE;
        }

        if (callingUserId != mInjector
                .binderWithCleanCallingIdentity(() -> getCurrentForegroundUserId())) {
            Slogf.d(LOG_TAG, "logoutUser(): user %d is in background, just stopping, not switching",
                    callingUserId);
            return stopUserUnchecked(callingUserId);
        }

        return logoutUserUnchecked(/* userIdToStop= */ callingUserId);
    }

    @Override
    public int logoutUserInternal() {
        CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(canManageUsers(caller)
                || hasCallingOrSelfPermission(permission.INTERACT_ACROSS_USERS));

        int currentUserId = getCurrentForegroundUserId();
        if (VERBOSE_LOG) {
            Slogf.v(LOG_TAG, "logout() called by uid %d; current user is %d", caller.getUid(),
                    currentUserId);
        }
        int result = logoutUserUnchecked(currentUserId);
        if (VERBOSE_LOG) {
            Slogf.v(LOG_TAG, "Result of logout(): %d", result);
        }
        return result;
    }

    private int logoutUserUnchecked(@UserIdInt int userIdToStop) {
        int logoutUserId = getLogoutUserIdUnchecked();
        if (logoutUserId == UserHandle.USER_NULL) {
            // Could happen on devices using headless system user mode when called before calling
            // switchUser() or startUserInBackground() first
            Slogf.w(LOG_TAG, "logoutUser(): could not determine which user to switch to");
            return UserManager.USER_OPERATION_ERROR_UNKNOWN;
        }
        final long id = mInjector.binderClearCallingIdentity();
        try {
            Slogf.i(LOG_TAG, "logoutUser(): switching to user %d", logoutUserId);
            if (!mInjector.getIActivityManager().switchUser(logoutUserId)) {
                Slogf.w(LOG_TAG, "Failed to switch to user %d", logoutUserId);
                // This should never happen as target user is determined by getPreviousUserId()
                return UserManager.USER_OPERATION_ERROR_UNKNOWN;
            }
            clearLogoutUser();
        } catch (RemoteException e) {
            // Same process, should not happen.
            return UserManager.USER_OPERATION_ERROR_UNKNOWN;
        } finally {
            mInjector.binderRestoreCallingIdentity(id);
        }

        return stopUserUnchecked(userIdToStop);
    }

    private int stopUserUnchecked(@UserIdInt int userId) {
        Slogf.i(LOG_TAG, "Stopping user %d", userId);
        final long id = mInjector.binderClearCallingIdentity();
        try {
            switch (mInjector.getIActivityManager().stopUser(userId, true /*force*/, null)) {
                case ActivityManager.USER_OP_SUCCESS:
                    return UserManager.USER_OPERATION_SUCCESS;
                case ActivityManager.USER_OP_IS_CURRENT:
                    return UserManager.USER_OPERATION_ERROR_CURRENT_USER;
                default:
                    return UserManager.USER_OPERATION_ERROR_UNKNOWN;
            }
        } catch (RemoteException e) {
            // Same process, should not happen.
            return UserManager.USER_OPERATION_ERROR_UNKNOWN;
        } finally {
            mInjector.binderRestoreCallingIdentity(id);
        }
    }

    @Override
    public List<UserHandle> getSecondaryUsers(ComponentName who) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));

        return mInjector.binderWithCleanCallingIdentity(() -> {
            final List<UserInfo> userInfos = mInjector.getUserManager().getAliveUsers();
            final List<UserHandle> userHandles = new ArrayList<>();
            for (UserInfo userInfo : userInfos) {
                UserHandle userHandle = userInfo.getUserHandle();
                if (!userHandle.isSystem() && !isManagedProfile(userHandle.getIdentifier())) {
                    userHandles.add(userInfo.getUserHandle());
                }
            }
            return userHandles;
        });
    }

    @Override
    public boolean isEphemeralUser(ComponentName who) {
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));

        return mInjector.binderWithCleanCallingIdentity(
                () -> mInjector.getUserManager().isUserEphemeral(caller.getUserId()));
    }

    @Override
    public Bundle getApplicationRestrictions(ComponentName who, String callerPackage,
            String packageName) {
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_APP_RESTRICTIONS)));

        return mInjector.binderWithCleanCallingIdentity(() -> {
            Bundle bundle = mUserManager.getApplicationRestrictions(packageName,
                    caller.getUserHandle());
           // if no restrictions were saved, mUserManager.getApplicationRestrictions
           // returns null, but DPM method should return an empty Bundle as per JavaDoc
           return bundle != null ? bundle : Bundle.EMPTY;
        });
    }

    /**
     * Returns the apps that are non-exempt from some policies (such as suspension), and populates
     * the given set with the apps that are exempt.
     *
     * @param packageNames apps to check
     * @param outputExemptApps will be populate with subset of {@code packageNames} that is exempt
     * from some policy restrictions
     *
     * @return subset of {@code packageNames} that is affected by some policy restrictions.
     */
    private String[] populateNonExemptAndExemptFromPolicyApps(String[] packageNames,
            Set<String> outputExemptApps) {
        Preconditions.checkArgument(outputExemptApps.isEmpty(), "outputExemptApps is not empty");
        List<String> exemptAppsList = listPolicyExemptAppsUnchecked(mContext);
        if (exemptAppsList.isEmpty()) {
            return packageNames;
        }
        // Using a set so contains() is O(1)
        Set<String> exemptApps = new HashSet<>(exemptAppsList);
        List<String> nonExemptApps = new ArrayList<>(packageNames.length);
        for (int i = 0; i < packageNames.length; i++) {
            String app = packageNames[i];
            if (exemptApps.contains(app)) {
                outputExemptApps.add(app);
            } else {
                nonExemptApps.add(app);
            }
        }
        String[] result = new String[nonExemptApps.size()];
        nonExemptApps.toArray(result);
        return result;
    }

    @Override
    public String[] setPackagesSuspended(ComponentName who, String callerPackage,
            String[] packageNames, boolean suspended) {
        Objects.requireNonNull(packageNames, "array of packages cannot be null");
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_PACKAGE_ACCESS)));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_PACKAGES_SUSPENDED);

        // Must remove the exempt apps from the input before calling PM, then add them back to
        // the array returned to the caller
        Set<String> exemptApps = new HashSet<>();
        packageNames = populateNonExemptAndExemptFromPolicyApps(packageNames, exemptApps);

        String[] nonSuspendedPackages = null;
        synchronized (getLockObject()) {
            long id = mInjector.binderClearCallingIdentity();
            try {
                nonSuspendedPackages = mIPackageManager.setPackagesSuspendedAsUser(packageNames,
                        suspended, null, null, null, PLATFORM_PACKAGE_NAME, caller.getUserId());
            } catch (RemoteException re) {
                // Shouldn't happen.
                Slogf.e(LOG_TAG, "Failed talking to the package manager", re);
            } finally {
                mInjector.binderRestoreCallingIdentity(id);
            }
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_PACKAGES_SUSPENDED)
                .setAdmin(caller.getPackageName())
                .setBoolean(/* isDelegate */ who == null)
                .setStrings(packageNames)
                .write();

        if (nonSuspendedPackages == null) {
            Slogf.w(LOG_TAG, "PM failed to suspend packages (%s)", Arrays.toString(packageNames));
            return packageNames;
        }

        ArraySet<String> changed = new ArraySet<>(packageNames);
        if (suspended) {
            // Only save those packages that are actually suspended. If a package is exempt or is
            // unsuspendable, it is skipped.
            changed.removeAll(List.of(nonSuspendedPackages));
        } else {
            // If an admin tries to unsuspend a package that is either exempt or is not
            // suspendable, drop it from the stored list assuming it must be already unsuspended.
            changed.addAll(exemptApps);
        }

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            ArraySet<String> current = new ArraySet<>(admin.suspendedPackages);
            if (suspended) {
                current.addAll(changed);
            } else {
                current.removeAll(changed);
            }
            admin.suspendedPackages = current.isEmpty() ? null : new ArrayList<>(current);
            saveSettingsLocked(caller.getUserId());
        }

        if (exemptApps.isEmpty()) {
            return nonSuspendedPackages;
        }

        String[] result = buildNonSuspendedPackagesUnionArray(nonSuspendedPackages, exemptApps);
        if (VERBOSE_LOG) Slogf.v(LOG_TAG, "Returning %s", Arrays.toString(result));
        return result;
    }

    /**
     * Returns an array containing the union of the given non-suspended packages and
     * exempt apps. Assumes both parameters are non-null and non-empty.
     */
    private String[] buildNonSuspendedPackagesUnionArray(String[] nonSuspendedPackages,
            Set<String> exemptApps) {
        String[] result = new String[nonSuspendedPackages.length + exemptApps.size()];
        int index = 0;
        for (String app : nonSuspendedPackages) {
            result[index++] = app;
        }
        for (String app : exemptApps) {
            result[index++] = app;
        }
        return result;
    }

    @Override
    public boolean isPackageSuspended(ComponentName who, String callerPackage, String packageName) {
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_PACKAGE_ACCESS)));

        synchronized (getLockObject()) {
            long id = mInjector.binderClearCallingIdentity();
            try {
                return mIPackageManager.isPackageSuspendedForUser(packageName, caller.getUserId());
            } catch (RemoteException re) {
                // Shouldn't happen.
                Slogf.e(LOG_TAG, "Failed talking to the package manager", re);
            } finally {
                mInjector.binderRestoreCallingIdentity(id);
            }
            return false;
        }
    }

    @Override
    public List<String> listPolicyExemptApps() {
        CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_DEVICE_ADMINS)
                        || isDefaultDeviceOwner(caller) || isProfileOwner(caller));

        return listPolicyExemptAppsUnchecked(mContext);
    }

    private static List<String> listPolicyExemptAppsUnchecked(Context context) {
        // TODO(b/181238156): decide whether it should only list the apps set by the resources,
        // or also the "critical" apps defined by PersonalAppsSuspensionHelper (like SMS app).
        // If it's the latter, refactor PersonalAppsSuspensionHelper so it (or a superclass) takes
        // the resources on constructor.
        String[] core = context.getResources().getStringArray(R.array.policy_exempt_apps);
        String[] vendor = context.getResources().getStringArray(R.array.vendor_policy_exempt_apps);

        int size = core.length + vendor.length;
        Set<String> apps = new ArraySet<>(size);
        for (String app : core) {
            apps.add(app);
        }
        for (String app : vendor) {
            apps.add(app);
        }

        return new ArrayList<>(apps);
    }

    @Override
    public void setUserRestriction(ComponentName who, String key, boolean enabledFromThisOwner,
            boolean parent) {
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);

        if (!UserRestrictionsUtils.isValidRestriction(key)) {
            return;
        }

        if (parent) {
            Preconditions.checkCallAuthorization(isProfileOwnerOfOrganizationOwnedDevice(caller));
        } else {
            Preconditions.checkCallAuthorization(isDeviceOwner(caller) || isProfileOwner(caller));
        }

        int userHandle = caller.getUserId();
        synchronized (getLockObject()) {
            final ActiveAdmin activeAdmin = getParentOfAdminIfRequired(
                    getProfileOwnerOrDeviceOwnerLocked(userHandle), parent);

            if (isDefaultDeviceOwner(caller)) {
                if (!UserRestrictionsUtils.canDeviceOwnerChange(key)) {
                    throw new SecurityException("Device owner cannot set user restriction " + key);
                }
                Preconditions.checkArgument(!parent,
                        "Cannot use the parent instance in Device Owner mode");
            } else if (isFinancedDeviceOwner(caller)) {
                if (!UserRestrictionsUtils.canFinancedDeviceOwnerChange(key)) {
                    throw new SecurityException("Cannot set user restriction " + key
                            + " when managing a financed device");
                }
                Preconditions.checkArgument(!parent,
                        "Cannot use the parent instance in Financed Device Owner mode");
            } else {
                boolean profileOwnerCanChangeOnItself = !parent
                        && UserRestrictionsUtils.canProfileOwnerChange(key, userHandle);
                boolean orgOwnedProfileOwnerCanChangesGlobally = parent
                        && isProfileOwnerOfOrganizationOwnedDevice(caller)
                        && UserRestrictionsUtils.canProfileOwnerOfOrganizationOwnedDeviceChange(
                        key);

                if (!profileOwnerCanChangeOnItself && !orgOwnedProfileOwnerCanChangesGlobally) {
                    throw new SecurityException("Profile owner cannot set user restriction " + key);
                }
            }
            checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_USER_RESTRICTION);

            // Save the restriction to ActiveAdmin.
            final Bundle restrictions = activeAdmin.ensureUserRestrictions();
            if (enabledFromThisOwner) {
                restrictions.putBoolean(key, true);
            } else {
                restrictions.remove(key);
            }
            saveUserRestrictionsLocked(userHandle);
        }
        final int eventId = enabledFromThisOwner
                ? DevicePolicyEnums.ADD_USER_RESTRICTION
                : DevicePolicyEnums.REMOVE_USER_RESTRICTION;
        DevicePolicyEventLogger
                .createEvent(eventId)
                .setAdmin(caller.getComponentName())
                .setStrings(key, parent ? CALLED_FROM_PARENT : NOT_CALLED_FROM_PARENT)
                .write();
        if (SecurityLog.isLoggingEnabled()) {
            final int eventTag = enabledFromThisOwner
                    ? SecurityLog.TAG_USER_RESTRICTION_ADDED
                    : SecurityLog.TAG_USER_RESTRICTION_REMOVED;
            SecurityLog.writeEvent(eventTag, who.getPackageName(), userHandle, key);
        }
    }

    private void saveUserRestrictionsLocked(int userId) {
        saveSettingsLocked(userId);
        pushUserRestrictions(userId);
        sendChangedNotification(userId);
    }

    /**
     * Pushes the user restrictions originating from a specific user.
     *
     * If called by the profile owner of an organization-owned device, the global and local
     * user restrictions will be an accumulation of the global user restrictions from the profile
     * owner active admin and its parent active admin. The key of the local user restrictions set
     * will be the target user id.
     */
    private void pushUserRestrictions(int originatingUserId) {
        final Bundle global;
        final RestrictionsSet local = new RestrictionsSet();
        final boolean isDeviceOwner;
        synchronized (getLockObject()) {
            isDeviceOwner = mOwners.isDeviceOwnerUserId(originatingUserId);
            if (isDeviceOwner) {
                final ActiveAdmin deviceOwner = getDeviceOwnerAdminLocked();
                if (deviceOwner == null) {
                    return; // Shouldn't happen.
                }
                global = deviceOwner.getGlobalUserRestrictions(OWNER_TYPE_DEVICE_OWNER);
                local.updateRestrictions(originatingUserId, deviceOwner.getLocalUserRestrictions(
                        OWNER_TYPE_DEVICE_OWNER));
            } else {
                final ActiveAdmin profileOwner = getProfileOwnerAdminLocked(originatingUserId);
                if (profileOwner == null) {
                    return;
                }
                global = profileOwner.getGlobalUserRestrictions(OWNER_TYPE_PROFILE_OWNER);
                local.updateRestrictions(originatingUserId, profileOwner.getLocalUserRestrictions(
                        OWNER_TYPE_PROFILE_OWNER));
                // Global (device-wide) and local user restrictions set by the profile owner of an
                // organization-owned device are stored in the parent ActiveAdmin instance.
                if (isProfileOwnerOfOrganizationOwnedDevice(
                        profileOwner.getUserHandle().getIdentifier())) {
                    // The global restrictions set on the parent ActiveAdmin instance need to be
                    // merged with the global restrictions set on the profile owner ActiveAdmin
                    // instance, since both are to be applied device-wide.
                    UserRestrictionsUtils.merge(global,
                            profileOwner.getParentActiveAdmin().getGlobalUserRestrictions(
                                    OWNER_TYPE_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE));
                    // The local restrictions set on the parent ActiveAdmin instance are only to be
                    // applied to the primary user. They therefore need to be added the local
                    // restriction set with the primary user id as the key, in this case the
                    // primary user id is the target user.
                    local.updateRestrictions(
                            getProfileParentId(profileOwner.getUserHandle().getIdentifier()),
                            profileOwner.getParentActiveAdmin().getLocalUserRestrictions(
                                    OWNER_TYPE_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE));
                }
            }
        }
        mUserManagerInternal.setDevicePolicyUserRestrictions(originatingUserId, global, local,
                isDeviceOwner);
    }

    @Override
    public Bundle getUserRestrictions(ComponentName who, boolean parent) {
        if (!mHasFeature) {
            return null;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                || isFinancedDeviceOwner(caller)
                || isProfileOwner(caller)
                || (parent && isProfileOwnerOfOrganizationOwnedDevice(caller)));

        synchronized (getLockObject()) {
            final ActiveAdmin activeAdmin = getParentOfAdminIfRequired(
                    getProfileOwnerOrDeviceOwnerLocked(caller.getUserId()), parent);
            return activeAdmin.userRestrictions;
        }
    }

    @Override
    public boolean setApplicationHidden(ComponentName who, String callerPackage, String packageName,
            boolean hidden, boolean parent) {
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_PACKAGE_ACCESS)));

        List<String> exemptApps = listPolicyExemptAppsUnchecked(mContext);
        if (exemptApps.contains(packageName)) {
            Slogf.d(LOG_TAG, "setApplicationHidden(): ignoring %s as it's on policy-exempt list",
                    packageName);
            return false;
        }

        final int userId = parent ? getProfileParentId(caller.getUserId()) : caller.getUserId();
        boolean result;
        synchronized (getLockObject()) {
            if (parent) {
                Preconditions.checkCallAuthorization(isProfileOwnerOfOrganizationOwnedDevice(
                        caller.getUserId()) && isManagedProfile(caller.getUserId()));
                // Ensure the package provided is a system package, this is to ensure that this
                // API cannot be used to leak if certain non-system package exists in the person
                // profile.
                mInjector.binderWithCleanCallingIdentity(() ->
                        enforcePackageIsSystemPackage(packageName, userId));
            }
            checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_APPLICATION_HIDDEN);

            if (VERBOSE_LOG) {
                Slogf.v(LOG_TAG, "calling pm.setApplicationHiddenSettingAsUser(%s, %b, %d)",
                        packageName, hidden, userId);
            }
            result = mInjector.binderWithCleanCallingIdentity(() -> mIPackageManager
                    .setApplicationHiddenSettingAsUser(packageName, hidden, userId));
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_APPLICATION_HIDDEN)
                .setAdmin(caller.getPackageName())
                .setBoolean(/* isDelegate */ who == null)
                .setStrings(packageName, hidden ? "hidden" : "not_hidden",
                        parent ? CALLED_FROM_PARENT : NOT_CALLED_FROM_PARENT)
                .write();
        return result;
    }

    @Override
    public boolean isApplicationHidden(ComponentName who, String callerPackage,
            String packageName, boolean parent) {
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_PACKAGE_ACCESS)));

        final int userId = parent ? getProfileParentId(caller.getUserId()) : caller.getUserId();
        synchronized (getLockObject()) {
            if (parent) {
                Preconditions.checkCallAuthorization(
                        isProfileOwnerOfOrganizationOwnedDevice(caller.getUserId())
                        && isManagedProfile(caller.getUserId()));
                // Ensure the package provided is a system package.
                mInjector.binderWithCleanCallingIdentity(() ->
                        enforcePackageIsSystemPackage(packageName, userId));
            }

            return mInjector.binderWithCleanCallingIdentity(
                    () -> mIPackageManager.getApplicationHiddenSettingAsUser(packageName, userId));
        }
    }

    private void enforcePackageIsSystemPackage(String packageName, int userId)
            throws RemoteException {
        boolean isSystem;
        try {
            isSystem = isSystemApp(mIPackageManager, packageName, userId);
        } catch (IllegalArgumentException e) {
            isSystem = false;
        }
        if (!isSystem) {
            throw new IllegalArgumentException("The provided package is not a system package");
        }
    }

    @Override
    public void enableSystemApp(ComponentName who, String callerPackage, String packageName) {
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_ENABLE_SYSTEM_APP)));

        final boolean isDemo = isCurrentUserDemo();
        int userId = caller.getUserId();
        long id = mInjector.binderClearCallingIdentity();
        try {
            if (VERBOSE_LOG) {
                Slogf.v(LOG_TAG, "installing " + packageName + " for " + userId);
            }

            Preconditions.checkArgument(isDemo || isSystemApp(mIPackageManager, packageName,
                    getProfileParentId(userId)), "Only system apps can be enabled this way");

            // Install the app.
            mIPackageManager.installExistingPackageAsUser(packageName, userId,
                    PackageManager.INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS,
                    PackageManager.INSTALL_REASON_POLICY, null);
            if (isDemo) {
                // Ensure the app is also ENABLED for demo users.
                mIPackageManager.setApplicationEnabledSetting(packageName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP, userId, "DevicePolicyManager");
            }
        } catch (RemoteException re) {
            // shouldn't happen
            Slogf.wtf(LOG_TAG, "Failed to install " + packageName, re);
        } finally {
            mInjector.binderRestoreCallingIdentity(id);
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.ENABLE_SYSTEM_APP)
                .setAdmin(caller.getPackageName())
                .setBoolean(/* isDelegate */ who == null)
                .setStrings(packageName)
                .write();
    }

    @Override
    public int enableSystemAppWithIntent(ComponentName who, String callerPackage, Intent intent) {
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_ENABLE_SYSTEM_APP)));

        int numberOfAppsInstalled = 0;
        long id = mInjector.binderClearCallingIdentity();
        try {
            final int parentUserId = getProfileParentId(caller.getUserId());
            List<ResolveInfo> activitiesToEnable = mIPackageManager
                    .queryIntentActivities(intent,
                            intent.resolveTypeIfNeeded(mContext.getContentResolver()),
                            PackageManager.MATCH_DIRECT_BOOT_AWARE
                                    | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                            parentUserId)
                    .getList();

            if (VERBOSE_LOG) {
                Slogf.d(LOG_TAG, "Enabling system activities: " + activitiesToEnable);
            }
            if (activitiesToEnable != null) {
                for (ResolveInfo info : activitiesToEnable) {
                    if (info.activityInfo != null) {
                        String packageName = info.activityInfo.packageName;
                        if (isSystemApp(mIPackageManager, packageName, parentUserId)) {
                            numberOfAppsInstalled++;
                            mIPackageManager.installExistingPackageAsUser(packageName,
                                    caller.getUserId(),
                                    PackageManager.INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS,
                                    PackageManager.INSTALL_REASON_POLICY, null);
                        } else {
                            Slogf.d(LOG_TAG, "Not enabling " + packageName + " since is not a"
                                    + " system app");
                        }
                    }
                }
            }
        } catch (RemoteException e) {
            // shouldn't happen
            Slogf.wtf(LOG_TAG, "Failed to resolve intent for: " + intent, e);
            return 0;
        } finally {
            mInjector.binderRestoreCallingIdentity(id);
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.ENABLE_SYSTEM_APP_WITH_INTENT)
                .setAdmin(caller.getPackageName())
                .setBoolean(/* isDelegate */ who == null)
                .setStrings(intent.getAction())
                .write();
        return numberOfAppsInstalled;
    }

    private boolean isSystemApp(IPackageManager pm, String packageName, int userId)
            throws RemoteException {
        ApplicationInfo appInfo = pm.getApplicationInfo(packageName, MATCH_UNINSTALLED_PACKAGES,
                userId);
        if (appInfo == null) {
            throw new IllegalArgumentException("The application " + packageName +
                    " is not present on this device");
        }
        return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    @Override
    public boolean installExistingPackage(ComponentName who, String callerPackage,
            String packageName) {
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)))
                || (caller.hasPackage()
                && isCallerDelegate(caller, DELEGATION_INSTALL_EXISTING_PACKAGE)));

        boolean result;
        synchronized (getLockObject()) {
            Preconditions.checkCallAuthorization(
                    isUserAffiliatedWithDeviceLocked(caller.getUserId()),
                            "Admin %s is neither the device owner or "
                                    + "affiliated user's profile owner.", who);
            final long id = mInjector.binderClearCallingIdentity();
            try {
                if (VERBOSE_LOG) {
                    Slogf.v(LOG_TAG, "installing " + packageName + " for " + caller.getUserId());
                }

                // Install the package.
                result = mIPackageManager.installExistingPackageAsUser(packageName,
                        caller.getUserId(),
                        PackageManager.INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS,
                        PackageManager.INSTALL_REASON_POLICY, null)
                        == PackageManager.INSTALL_SUCCEEDED;
            } catch (RemoteException re) {
                // shouldn't happen
                Slogf.wtf(LOG_TAG, "Error installing package", re);
                return false;
            } finally {
                mInjector.binderRestoreCallingIdentity(id);
            }
        }
        if (result) {
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.INSTALL_EXISTING_PACKAGE)
                    .setAdmin(caller.getPackageName())
                    .setBoolean(/* isDelegate */ who == null)
                    .setStrings(packageName)
                    .write();
        }
        return result;
    }

    @Override
    public void setAccountManagementDisabled(ComponentName who, String accountType,
            boolean disabled, boolean parent) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        synchronized (getLockObject()) {
            /*
             * When called on the parent DPM instance (parent == true), affects active admin
             * selection in two ways:
             * * The ActiveAdmin must be of an org-owned profile owner.
             * * The parent ActiveAdmin instance should be used for managing the restriction.
             */
            final ActiveAdmin ap;
            if (parent) {
                ap = getParentOfAdminIfRequired(getOrganizationOwnedProfileOwnerLocked(caller),
                        parent);
            } else {
                Preconditions.checkCallAuthorization(
                        isDefaultDeviceOwner(caller) || isProfileOwner(caller));
                ap = getParentOfAdminIfRequired(
                        getProfileOwnerOrDeviceOwnerLocked(caller.getUserId()), parent);
            }

            if (disabled) {
                ap.accountTypesWithManagementDisabled.add(accountType);
            } else {
                ap.accountTypesWithManagementDisabled.remove(accountType);
            }
            saveSettingsLocked(UserHandle.getCallingUserId());
        }
    }

    @Override
    public String[] getAccountTypesWithManagementDisabled() {
        return getAccountTypesWithManagementDisabledAsUser(UserHandle.getCallingUserId(), false);
    }

    @Override
    public String[] getAccountTypesWithManagementDisabledAsUser(int userId, boolean parent) {
        if (!mHasFeature) {
            return null;
        }
        Preconditions.checkArgumentNonnegative(userId, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userId));

        synchronized (getLockObject()) {
            final ArraySet<String> resultSet = new ArraySet<>();

            if (!parent) {
                final DevicePolicyData policy = getUserData(userId);
                for (ActiveAdmin admin : policy.mAdminList) {
                    resultSet.addAll(admin.accountTypesWithManagementDisabled);
                }
            }

            // Check if there's a profile owner of an org-owned device and the method is called for
            // the parent user of this profile owner.
            final ActiveAdmin orgOwnedAdmin =
                    getProfileOwnerOfOrganizationOwnedDeviceLocked(userId);
            final boolean shouldGetParentAccounts = orgOwnedAdmin != null && (parent
                    || UserHandle.getUserId(orgOwnedAdmin.getUid()) != userId);
            if (shouldGetParentAccounts) {
                resultSet.addAll(
                        orgOwnedAdmin.getParentActiveAdmin().accountTypesWithManagementDisabled);
            }
            return resultSet.toArray(new String[resultSet.size()]);
        }
    }

    @Override
    public void setUninstallBlocked(ComponentName who, String callerPackage, String packageName,
            boolean uninstallBlocked) {
        final CallerIdentity caller = getCallerIdentity(who, callerPackage);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)
                || isFinancedDeviceOwner(caller)))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_BLOCK_UNINSTALL)));

        if (isCoexistenceEnabled(caller)) {
            // TODO(b/260573124): Add correct enforcing admin when permission changes are
            //  merged, and don't forget to handle delegates! Enterprise admins assume
            //  component name isn't null.
            EnforcingAdmin admin = EnforcingAdmin.createEnterpriseEnforcingAdmin(
                    who != null ? who : new ComponentName(callerPackage, "delegate"),
                    caller.getUserId());
            mDevicePolicyEngine.setLocalPolicy(
                    PolicyDefinition.PACKAGE_UNINSTALL_BLOCKED(packageName),
                    admin,
                    uninstallBlocked,
                    caller.getUserId());
        } else {
            final int userId = caller.getUserId();
            synchronized (getLockObject()) {
                long id = mInjector.binderClearCallingIdentity();
                try {
                    mIPackageManager.setBlockUninstallForUser(
                            packageName, uninstallBlocked, userId);
                } catch (RemoteException re) {
                    // Shouldn't happen.
                    Slogf.e(LOG_TAG, "Failed to setBlockUninstallForUser", re);
                } finally {
                    mInjector.binderRestoreCallingIdentity(id);
                }
            }
            if (uninstallBlocked) {
                final PackageManagerInternal pmi = mInjector.getPackageManagerInternal();
                pmi.removeNonSystemPackageSuspensions(packageName, userId);
                pmi.removeDistractingPackageRestrictions(packageName, userId);
                pmi.flushPackageRestrictions(userId);
            }
        }

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_UNINSTALL_BLOCKED)
                .setAdmin(caller.getPackageName())
                .setBoolean(/* isDelegate */ who == null)
                .setStrings(packageName)
                .write();
    }

    static void setUninstallBlockedUnchecked(
            String packageName, boolean uninstallBlocked, int userId) {
        Binder.withCleanCallingIdentity(() -> {
            try {
                AppGlobals.getPackageManager().setBlockUninstallForUser(
                        packageName, uninstallBlocked, userId);
            } catch (RemoteException re) {
                // Shouldn't happen.
                Slogf.e(LOG_TAG, "Failed to setBlockUninstallForUser", re);
            }
        });
        if (uninstallBlocked) {
            final PackageManagerInternal pmi = LocalServices.getService(
                    PackageManagerInternal.class);
            pmi.removeNonSystemPackageSuspensions(packageName, userId);
            pmi.removeDistractingPackageRestrictions(packageName, userId);
            pmi.flushPackageRestrictions(userId);
        }
    }

    @Override
    public boolean isUninstallBlocked(ComponentName who, String packageName) {
        // This function should return true if and only if the package is blocked by
        // setUninstallBlocked(). It should still return false for other cases of blocks, such as
        // when the package is a system app, or when it is an active device admin.
        final int userId = UserHandle.getCallingUserId();

        synchronized (getLockObject()) {
            //TODO: This is a silly access control check. Remove.
            if (who != null) {
                final CallerIdentity caller = getCallerIdentity(who);
                Preconditions.checkCallAuthorization(
                        isProfileOwner(caller) || isDefaultDeviceOwner(caller)
                                || isFinancedDeviceOwner(caller));
            }
            try {
                return mIPackageManager.getBlockUninstallForUser(packageName, userId);
            } catch (RemoteException re) {
                // Shouldn't happen.
                Slogf.e(LOG_TAG, "Failed to getBlockUninstallForUser", re);
            }
        }
        return false;
    }

    @Override
    public void setCrossProfileCallerIdDisabled(ComponentName who, boolean disabled) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isProfileOwner(caller));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            if (disabled) {
                admin.mManagedProfileCallerIdAccess =
                        new PackagePolicy(PackagePolicy.PACKAGE_POLICY_ALLOWLIST);
            } else {
                admin.mManagedProfileCallerIdAccess =
                        new PackagePolicy(PackagePolicy.PACKAGE_POLICY_BLOCKLIST);
            }
            saveSettingsLocked(caller.getUserId());
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_CROSS_PROFILE_CALLER_ID_DISABLED)
                .setAdmin(who)
                .setBoolean(disabled)
                .write();
    }

    @Override
    public boolean getCrossProfileCallerIdDisabled(ComponentName who) {
        if (!mHasFeature) {
            return false;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isProfileOwner(caller));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            if (admin == null) {
                return false;
            }

            if (admin.mManagedProfileCallerIdAccess == null) {
                return admin.disableCallerId;
            }

            if (admin.mManagedProfileCallerIdAccess.getPolicyType()
                    == PackagePolicy.PACKAGE_POLICY_ALLOWLIST_AND_SYSTEM) {
                Slogf.w(LOG_TAG, "Denying callerId due to PACKAGE_POLICY_SYSTEM policyType");
            }

            return admin.mManagedProfileCallerIdAccess.getPolicyType()
                    != PackagePolicy.PACKAGE_POLICY_BLOCKLIST;
        }
    }

    @Override
    public boolean getCrossProfileCallerIdDisabledForUser(int userId) {
        Preconditions.checkArgumentNonnegative(userId, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasCrossUsersPermission(caller, userId));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerAdminLocked(userId);
            if (admin == null) {
                return false;
            }

            if (admin.mManagedProfileCallerIdAccess == null) {
                return admin.disableCallerId;
            }

            return admin.mManagedProfileCallerIdAccess.getPolicyType()
                    == PackagePolicy.PACKAGE_POLICY_ALLOWLIST;
        }
    }

    @Override
    public void setManagedProfileCallerIdAccessPolicy(PackagePolicy policy) {
        if (!mHasFeature) {
            return;
        }
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization((isProfileOwner(caller)
                && isManagedProfile(caller.getUserId())));
        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            admin.disableCallerId = false;
            admin.mManagedProfileCallerIdAccess = policy;
            saveSettingsLocked(caller.getUserId());
        }
    }

    @Override
    public PackagePolicy getManagedProfileCallerIdAccessPolicy() {
        if (!mHasFeature) {
            return null;
        }
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization((isProfileOwner(caller)
                && isManagedProfile(caller.getUserId())));
        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            return (admin != null) ? admin.mManagedProfileCallerIdAccess : null;
        }
    }

    @Override
    public boolean hasManagedProfileCallerIdAccess(int userId, String packageName) {
        Preconditions.checkArgumentNonnegative(userId, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasCrossUsersPermission(caller, userId));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerAdminLocked(userId);
            if (admin != null) {
                if (admin.mManagedProfileCallerIdAccess == null) {
                    return !admin.disableCallerId;
                }
                return admin.mManagedProfileCallerIdAccess.isPackageAllowed(packageName,
                        mContactSystemRoleHolders);
            }
        }
        return true;
    }

    @Override
    public void setManagedProfileContactsAccessPolicy(PackagePolicy policy) {
        if (!mHasFeature) {
            return;
        }
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization((isProfileOwner(caller)
                && isManagedProfile(caller.getUserId())));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            admin.disableContactsSearch = false;
            admin.mManagedProfileContactsAccess = policy;
            saveSettingsLocked(caller.getUserId());
        }
    }

    @Override
    public PackagePolicy getManagedProfileContactsAccessPolicy() {
        if (!mHasFeature) {
            return null;
        }

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization((isProfileOwner(caller)
                && isManagedProfile(caller.getUserId())));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            return (admin != null) ? admin.mManagedProfileContactsAccess : null;
        }
    }

    @Override
    public boolean hasManagedProfileContactsAccess(int userId, String packageName) {
        Preconditions.checkArgumentNonnegative(userId, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasCrossUsersPermission(caller, userId));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerAdminLocked(userId);
            if (admin != null) {
                if (admin.mManagedProfileContactsAccess == null) {
                    return !admin.disableContactsSearch;
                }

                return admin.mManagedProfileContactsAccess.isPackageAllowed(packageName,
                        mContactSystemRoleHolders);
            }
        }
        return true;
    }

    @Override
    public void setCrossProfileContactsSearchDisabled(ComponentName who, boolean disabled) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isProfileOwner(caller));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            if (disabled) {
                admin.mManagedProfileContactsAccess =
                        new PackagePolicy(PackagePolicy.PACKAGE_POLICY_ALLOWLIST);
            } else {
                admin.mManagedProfileContactsAccess =
                        new PackagePolicy(PackagePolicy.PACKAGE_POLICY_BLOCKLIST);
            }
            saveSettingsLocked(caller.getUserId());
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_CROSS_PROFILE_CONTACTS_SEARCH_DISABLED)
                .setAdmin(who)
                .setBoolean(disabled)
                .write();
    }

    @Override
    public boolean getCrossProfileContactsSearchDisabled(ComponentName who) {
        if (!mHasFeature) {
            return false;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isProfileOwner(caller));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            if (admin == null) {
                return false;
            }
            if (admin.mManagedProfileContactsAccess == null) {
                return admin.disableContactsSearch;
            }
            return admin.mManagedProfileContactsAccess.getPolicyType()
                    != PackagePolicy.PACKAGE_POLICY_BLOCKLIST;
        }
    }

    @Override
    public boolean getCrossProfileContactsSearchDisabledForUser(int userId) {
        Preconditions.checkArgumentNonnegative(userId, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasCrossUsersPermission(caller, userId));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerAdminLocked(userId);
            if (admin == null) {
                return false;
            }

            if (admin.mManagedProfileContactsAccess == null) {
                return admin.disableContactsSearch;
            }
            if (admin.mManagedProfileContactsAccess.getPolicyType()
                    == PackagePolicy.PACKAGE_POLICY_ALLOWLIST_AND_SYSTEM) {
                Slogf.w(LOG_TAG, "Denying contacts due to PACKAGE_POLICY_SYSTEM policyType");
            }
            return admin.mManagedProfileContactsAccess.getPolicyType()
                    != PackagePolicy.PACKAGE_POLICY_BLOCKLIST;
        }
    }

    @Override
    public void startManagedQuickContact(String actualLookupKey, long actualContactId,
            boolean isContactIdIgnored, long actualDirectoryId, Intent originalIntent) {
        final Intent intent = QuickContact.rebuildManagedQuickContactsIntent(actualLookupKey,
                actualContactId, isContactIdIgnored, actualDirectoryId, originalIntent);
        final int callingUserId = UserHandle.getCallingUserId();

        mInjector.binderWithCleanCallingIdentity(() -> {
            synchronized (getLockObject()) {
                final int managedUserId = getManagedUserId(callingUserId);
                if (managedUserId < 0) {
                    return;
                }
                if (isCrossProfileQuickContactDisabled(managedUserId)) {
                    if (VERBOSE_LOG) {
                        Slogf.v(LOG_TAG, "Cross-profile contacts access disabled for user %d",
                                managedUserId);
                    }
                    return;
                }
                ContactsInternal.startQuickContactWithErrorToastForUser(
                        mContext, intent, new UserHandle(managedUserId));
            }
        });
    }

    /**
     * @return true if cross-profile QuickContact is disabled
     */
    private boolean isCrossProfileQuickContactDisabled(@UserIdInt int userId) {
        return getCrossProfileCallerIdDisabledForUser(userId)
                && getCrossProfileContactsSearchDisabledForUser(userId);
    }

    /**
     * @return the user ID of the managed user that is linked to the current user, if any.
     * Otherwise UserHandle.USER_NULL (-10000).
     */
    public int getManagedUserId(@UserIdInt int callingUserId) {
        if (VERBOSE_LOG) Slogf.v(LOG_TAG, "getManagedUserId: callingUserId=%d", callingUserId);

        for (UserInfo ui : mUserManager.getProfiles(callingUserId)) {
            if (ui.id == callingUserId || !ui.isManagedProfile()) {
                continue; // Caller user self, or not a managed profile.  Skip.
            }
            if (VERBOSE_LOG) Slogf.v(LOG_TAG, "Managed user=%d", ui.id);
            return ui.id;
        }
        if (VERBOSE_LOG)  Slogf.v(LOG_TAG, "Managed user not found.");
        return UserHandle.USER_NULL;
    }

    /**
     * Returns the userId of the managed profile on the device.
     * If none exists, return {@link UserHandle#USER_NULL}.
     *
     * We assume there is only one managed profile across all users
     * on the device, which is true for now (HSUM or not) but could
     * change in future.
     */
    private @UserIdInt int getManagedUserId() {
        // On HSUM, there is only one main user and only the main user
        // can have a managed profile (for now). On non-HSUM, only user 0
        // can host the managed profile and user 0 is the main user.
        // So in both cases, we could just get the main user and
        // search for the profile user under it.
        UserHandle mainUser = mUserManager.getMainUser();
        if (mainUser == null) return UserHandle.USER_NULL;
        return getManagedUserId(mainUser.getIdentifier());
    }

    @Override
    public void setBluetoothContactSharingDisabled(ComponentName who, boolean disabled) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            if (admin.disableBluetoothContactSharing != disabled) {
                admin.disableBluetoothContactSharing = disabled;
                saveSettingsLocked(caller.getUserId());
            }
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_BLUETOOTH_CONTACT_SHARING_DISABLED)
                .setAdmin(who)
                .setBoolean(disabled)
                .write();
    }

    @Override
    public boolean getBluetoothContactSharingDisabled(ComponentName who) {
        if (!mHasFeature) {
            return false;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            return admin.disableBluetoothContactSharing;
        }
    }

    @Override
    public boolean getBluetoothContactSharingDisabledForUser(int userId) {
        // TODO: Should there be a check to make sure this relationship is
        // within a profile group?
        // enforceSystemProcess("getCrossProfileCallerIdDisabled can only be called by system");
        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerAdminLocked(userId);
            return (admin != null) ? admin.disableBluetoothContactSharing : false;
        }
    }

    @Override
    public void setSecondaryLockscreenEnabled(ComponentName who, boolean enabled) {
        Objects.requireNonNull(who, "ComponentName is null");

        // Check can set secondary lockscreen enabled
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));
        Preconditions.checkCallAuthorization(!isManagedProfile(caller.getUserId()),
                "User %d is not allowed to call setSecondaryLockscreenEnabled",
                        caller.getUserId());

        synchronized (getLockObject()) {
            // Allow testOnly admins to bypass supervision config requirement.
            Preconditions.checkCallAuthorization(isAdminTestOnlyLocked(who, caller.getUserId())
                    || isSupervisionComponentLocked(caller.getComponentName()), "Admin %s is not "
                    + "the default supervision component", caller.getComponentName());
            DevicePolicyData policy = getUserData(caller.getUserId());
            policy.mSecondaryLockscreenEnabled = enabled;
            saveSettingsLocked(caller.getUserId());
        }
    }

    @Override
    public boolean isSecondaryLockscreenEnabled(@NonNull UserHandle userHandle) {
        synchronized (getLockObject()) {
            return getUserData(userHandle.getIdentifier()).mSecondaryLockscreenEnabled;
        }
    }

    private boolean isManagedProfileOwner(CallerIdentity caller) {
        return isProfileOwner(caller) && isManagedProfile(caller.getUserId());
    }

    @Override
    public void setPreferentialNetworkServiceConfigs(
            List<PreferentialNetworkServiceConfig> preferentialNetworkServiceConfigs) {
        if (!mHasFeature) {
            return;
        }
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization((isProfileOwner(caller)
                        && isManagedProfile(caller.getUserId()))
                        || isDefaultDeviceOwner(caller),
                "Caller is not managed profile owner or device owner;"
                        + " only managed profile owner or device owner may control the preferential"
                        + " network service");
        synchronized (getLockObject()) {
            final ActiveAdmin requiredAdmin = getDeviceOrProfileOwnerAdminLocked(
                    caller.getUserId());
            if (!requiredAdmin.mPreferentialNetworkServiceConfigs.equals(
                    preferentialNetworkServiceConfigs)) {
                requiredAdmin.mPreferentialNetworkServiceConfigs =
                        new ArrayList<>(preferentialNetworkServiceConfigs);
                saveSettingsLocked(caller.getUserId());
            }
        }
        updateNetworkPreferenceForUser(caller.getUserId(), preferentialNetworkServiceConfigs);
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_PREFERENTIAL_NETWORK_SERVICE_ENABLED)
                .setBoolean(preferentialNetworkServiceConfigs
                        .stream().anyMatch(c -> c.isEnabled()))
                .write();
    }

    @Override
    public List<PreferentialNetworkServiceConfig> getPreferentialNetworkServiceConfigs() {
        if (!mHasFeature) {
            return List.of(PreferentialNetworkServiceConfig.DEFAULT);
        }

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization((isProfileOwner(caller)
                        && isManagedProfile(caller.getUserId()))
                        || isDefaultDeviceOwner(caller),
                "Caller is not managed profile owner or device owner;"
                        + " only managed profile owner or device owner may retrieve the "
                        + "preferential network service configurations");
        synchronized (getLockObject()) {
            final ActiveAdmin requiredAdmin = getDeviceOrProfileOwnerAdminLocked(
                    caller.getUserId());
            return requiredAdmin.mPreferentialNetworkServiceConfigs;
        }
    }

    @Override
    public void setLockTaskPackages(ComponentName who, String[] packages)
            throws SecurityException {
        Objects.requireNonNull(who, "ComponentName is null");
        Objects.requireNonNull(packages, "packages is null");
        final CallerIdentity caller = getCallerIdentity(who);

        synchronized (getLockObject()) {
            enforceCanCallLockTaskLocked(caller);
            checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_LOCK_TASK_PACKAGES);
        }

        if (isCoexistenceEnabled(caller)) {
            EnforcingAdmin admin = EnforcingAdmin.createEnterpriseEnforcingAdmin(
                    who, caller.getUserId());
            if (packages.length == 0) {
                mDevicePolicyEngine.removeLocalPolicy(
                        PolicyDefinition.LOCK_TASK,
                        admin,
                        caller.getUserId());
            } else {
                LockTaskPolicy currentPolicy = mDevicePolicyEngine.getLocalPolicySetByAdmin(
                        PolicyDefinition.LOCK_TASK,
                        admin,
                        caller.getUserId());
                LockTaskPolicy policy;
                if (currentPolicy == null) {
                    policy = new LockTaskPolicy(Set.of(packages));
                } else {
                    policy = currentPolicy.clone();
                    policy.setPackages(Set.of(packages));
                }

                mDevicePolicyEngine.setLocalPolicy(
                        PolicyDefinition.LOCK_TASK,
                        EnforcingAdmin.createEnterpriseEnforcingAdmin(who, caller.getUserId()),
                        policy,
                        caller.getUserId());
            }
        } else {
            synchronized (getLockObject()) {
                final int userHandle = caller.getUserId();
                setLockTaskPackagesLocked(userHandle, new ArrayList<>(Arrays.asList(packages)));
            }
        }
    }

    private void setLockTaskPackagesLocked(int userHandle, List<String> packages) {
        DevicePolicyData policy = getUserData(userHandle);
        policy.mLockTaskPackages = packages;

        // Store the settings persistently.
        saveSettingsLocked(userHandle);
        updateLockTaskPackagesLocked(mContext, packages, userHandle);
    }

    @Override
    public String[] getLockTaskPackages(ComponentName who) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        final int userHandle = caller.getUserId();

        synchronized (getLockObject()) {
            enforceCanCallLockTaskLocked(caller);
        }

        if (isCoexistenceEnabled(caller)) {
            LockTaskPolicy policy = mDevicePolicyEngine.getResolvedPolicy(
                    PolicyDefinition.LOCK_TASK, userHandle);
            if (policy == null) {
                return new String[0];
            } else {
                return policy.getPackages().toArray(new String[policy.getPackages().size()]);
            }
        } else {
            synchronized (getLockObject()) {
                final List<String> packages = getUserData(userHandle).mLockTaskPackages;
                return packages.toArray(new String[packages.size()]);
            }
        }
    }

    @Override
    public boolean isLockTaskPermitted(String pkg) {
        // Check policy-exempt apps first, as it doesn't require the lock
        if (listPolicyExemptAppsUnchecked(mContext).contains(pkg)) {
            if (VERBOSE_LOG) {
                Slogf.v(LOG_TAG, "isLockTaskPermitted(%s): returning true for policy-exempt app",
                            pkg);
            }
            return true;
        }

        final int userId = mInjector.userHandleGetCallingUserId();
        // TODO(b/260560985): This is not the right check, as the flag could be enabled but there
        //  could be an admin that hasn't targeted U.
        if (isCoexistenceFlagEnabled()) {
            LockTaskPolicy policy = mDevicePolicyEngine.getResolvedPolicy(
                    PolicyDefinition.LOCK_TASK, userId);
            if (policy == null) {
                return false;
            }
            return policy.getPackages().contains(pkg);
        } else {
            synchronized (getLockObject()) {
                return getUserData(userId).mLockTaskPackages.contains(pkg);
            }
        }
    }

    @Override
    public void setLockTaskFeatures(ComponentName who, int flags) {
        Objects.requireNonNull(who, "ComponentName is null");

        // Throw if Overview is used without Home.
        boolean hasHome = (flags & LOCK_TASK_FEATURE_HOME) != 0;
        boolean hasOverview = (flags & LOCK_TASK_FEATURE_OVERVIEW) != 0;
        Preconditions.checkArgument(hasHome || !hasOverview,
                "Cannot use LOCK_TASK_FEATURE_OVERVIEW without LOCK_TASK_FEATURE_HOME");
        boolean hasNotification = (flags & LOCK_TASK_FEATURE_NOTIFICATIONS) != 0;
        Preconditions.checkArgument(hasHome || !hasNotification,
            "Cannot use LOCK_TASK_FEATURE_NOTIFICATIONS without LOCK_TASK_FEATURE_HOME");

        final CallerIdentity caller = getCallerIdentity(who);
        final int userHandle = caller.getUserId();
        synchronized (getLockObject()) {
            enforceCanCallLockTaskLocked(caller);
            enforceCanSetLockTaskFeaturesOnFinancedDevice(caller, flags);
            checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_LOCK_TASK_FEATURES);
        }
        if (isCoexistenceEnabled(caller)) {
            EnforcingAdmin admin = EnforcingAdmin.createEnterpriseEnforcingAdmin(who, userHandle);
            LockTaskPolicy currentPolicy = mDevicePolicyEngine.getLocalPolicySetByAdmin(
                    PolicyDefinition.LOCK_TASK,
                    admin,
                    caller.getUserId());
            if (currentPolicy == null) {
                throw new IllegalArgumentException("Can't set a lock task flags without setting "
                        + "lock task packages first.");
            }
            LockTaskPolicy policy = currentPolicy.clone();
            policy.setFlags(flags);

            mDevicePolicyEngine.setLocalPolicy(
                    PolicyDefinition.LOCK_TASK,
                    EnforcingAdmin.createEnterpriseEnforcingAdmin(who, userHandle),
                    policy,
                    caller.getUserId());
        } else {
            synchronized (getLockObject()) {
                setLockTaskFeaturesLocked(userHandle, flags);
            }
        }
    }

    private void setLockTaskFeaturesLocked(int userHandle, int flags) {
        DevicePolicyData policy = getUserData(userHandle);
        policy.mLockTaskFeatures = flags;
        saveSettingsLocked(userHandle);
        updateLockTaskFeaturesLocked(flags, userHandle);
    }

    @Override
    public int getLockTaskFeatures(ComponentName who) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        final int userHandle = caller.getUserId();
        synchronized (getLockObject()) {
            enforceCanCallLockTaskLocked(caller);
        }

        if (isCoexistenceEnabled(caller)) {
            LockTaskPolicy policy = mDevicePolicyEngine.getResolvedPolicy(
                    PolicyDefinition.LOCK_TASK, userHandle);
            if (policy == null) {
                // We default on the power button menu, in order to be consistent with pre-P
                // behaviour.
                return DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS;
            }
            return policy.getFlags();
        } else {
            synchronized (getLockObject()) {
                return getUserData(userHandle).mLockTaskFeatures;
            }
        }
    }

    private void maybeClearLockTaskPolicyLocked() {
        mInjector.binderWithCleanCallingIdentity(() -> {
            final List<UserInfo> userInfos = mUserManager.getAliveUsers();
            for (int i = userInfos.size() - 1; i >= 0; i--) {
                int userId = userInfos.get(i).id;
                if (canUserUseLockTaskLocked(userId)) {
                    continue;
                }

                final List<String> lockTaskPackages = getUserData(userId).mLockTaskPackages;
                if (!lockTaskPackages.isEmpty()) {
                    Slogf.d(LOG_TAG,
                            "User id " + userId + " not affiliated. Clearing lock task packages");
                    setLockTaskPackagesLocked(userId, Collections.<String>emptyList());
                }
                final int lockTaskFeatures = getUserData(userId).mLockTaskFeatures;
                if (lockTaskFeatures != DevicePolicyManager.LOCK_TASK_FEATURE_NONE){
                    Slogf.d(LOG_TAG,
                            "User id " + userId + " not affiliated. Clearing lock task features");
                    setLockTaskFeaturesLocked(userId, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
                }
            }
        });
    }

    private void enforceCanSetLockTaskFeaturesOnFinancedDevice(CallerIdentity caller, int flags) {
        int allowedFlags = LOCK_TASK_FEATURE_SYSTEM_INFO | LOCK_TASK_FEATURE_KEYGUARD
                | LOCK_TASK_FEATURE_HOME | LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                | LOCK_TASK_FEATURE_NOTIFICATIONS;

        if (!isFinancedDeviceOwner(caller)) {
            return;
        }

        if ((flags == 0) || ((flags & ~(allowedFlags)) != 0)) {
            throw new SecurityException(
                    "Permitted lock task features when managing a financed device: "
                            + "LOCK_TASK_FEATURE_SYSTEM_INFO, LOCK_TASK_FEATURE_KEYGUARD, "
                            + "LOCK_TASK_FEATURE_HOME, LOCK_TASK_FEATURE_GLOBAL_ACTIONS, "
                            + "or LOCK_TASK_FEATURE_NOTIFICATIONS");
        }
    }

    @Override
    public void notifyLockTaskModeChanged(boolean isEnabled, String pkg, int userHandle) {
        Preconditions.checkCallAuthorization(isSystemUid(getCallerIdentity()),
                String.format(NOT_SYSTEM_CALLER_MSG, "call notifyLockTaskModeChanged"));
        synchronized (getLockObject()) {
            final DevicePolicyData policy = getUserData(userHandle);

            if (policy.mStatusBarDisabled) {
                // Status bar is managed by LockTaskController during LockTask, so we cancel this
                // policy when LockTask starts, and reapply it when LockTask ends
                setStatusBarDisabledInternal(!isEnabled, userHandle);
            }

            Bundle adminExtras = new Bundle();
            adminExtras.putString(DeviceAdminReceiver.EXTRA_LOCK_TASK_PACKAGE, pkg);
            for (ActiveAdmin admin : policy.mAdminList) {
                final boolean ownsDevice = isDeviceOwner(admin.info.getComponent(), userHandle);
                final boolean ownsProfile = isProfileOwner(admin.info.getComponent(), userHandle);
                if (ownsDevice || ownsProfile) {
                    if (isEnabled) {
                        sendAdminCommandLocked(admin, DeviceAdminReceiver.ACTION_LOCK_TASK_ENTERING,
                                adminExtras, null);
                    } else {
                        sendAdminCommandLocked(admin, DeviceAdminReceiver.ACTION_LOCK_TASK_EXITING);
                    }
                    DevicePolicyEventLogger
                            .createEvent(DevicePolicyEnums.SET_LOCKTASK_MODE_ENABLED)
                            .setAdmin(admin.info.getPackageName())
                            .setBoolean(isEnabled)
                            .setStrings(pkg)
                            .write();
                }
            }
        }
    }

    @Override
    public void setGlobalSetting(ComponentName who, String setting, String value) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_GLOBAL_SETTING)
                .setAdmin(who)
                .setStrings(setting, value)
                .write();

        synchronized (getLockObject()) {
            // Some settings are no supported any more. However we do not want to throw a
            // SecurityException to avoid breaking apps.
            if (GLOBAL_SETTINGS_DEPRECATED.contains(setting)) {
                Slogf.i(LOG_TAG, "Global setting no longer supported: %s", setting);
                return;
            }

            if (!GLOBAL_SETTINGS_ALLOWLIST.contains(setting)
                    && !UserManager.isDeviceInDemoMode(mContext)) {
                throw new SecurityException(String.format(
                        "Permission denial: device owners cannot update %1$s", setting));
            }

            if (Settings.Global.STAY_ON_WHILE_PLUGGED_IN.equals(setting)) {
                // ignore if it contradicts an existing policy
                long timeMs = getMaximumTimeToLock(
                        who, mInjector.userHandleGetCallingUserId(), /* parent */ false);
                if (timeMs > 0 && timeMs < Long.MAX_VALUE) {
                    return;
                }
            }

            mInjector.binderWithCleanCallingIdentity(
                    () -> mInjector.settingsGlobalPutString(setting, value));
        }
    }

    @Override
    public void setSystemSetting(ComponentName who, String setting, String value) {
        Objects.requireNonNull(who, "ComponentName is null");
        Preconditions.checkStringNotEmpty(setting, "String setting is null or empty");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_SYSTEM_SETTING);

        synchronized (getLockObject()) {
            if (!SYSTEM_SETTINGS_ALLOWLIST.contains(setting)) {
                throw new SecurityException(String.format(
                        "Permission denial: device owners cannot update %1$s", setting));
            }

            mInjector.binderWithCleanCallingIdentity(() ->
                    mInjector.settingsSystemPutStringForUser(setting, value, caller.getUserId()));
        }
    }

    @Override
    public void setConfiguredNetworksLockdownState(ComponentName who, boolean lockdown) {
        if (!mHasFeature) {
            return;
        }
        Preconditions.checkNotNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwnerOfOrganizationOwnedDevice(caller));

        mInjector.binderWithCleanCallingIdentity(() ->
                mInjector.settingsGlobalPutInt(Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN,
                        lockdown ? 1 : 0));

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.ALLOW_MODIFICATION_OF_ADMIN_CONFIGURED_NETWORKS)
                .setAdmin(caller.getComponentName())
                .setBoolean(lockdown)
                .write();
    }

    @Override
    public boolean hasLockdownAdminConfiguredNetworks(ComponentName who) {
        if (!mHasFeature) {
            return false;
        }
        Preconditions.checkNotNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwnerOfOrganizationOwnedDevice(caller));

        return mInjector.binderWithCleanCallingIdentity(() ->
                mInjector.settingsGlobalGetInt(Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN, 0) > 0);
    }

    @Override
    public void setLocationEnabled(ComponentName who, boolean locationEnabled) {
        Preconditions.checkNotNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));

        UserHandle userHandle = caller.getUserHandle();
        if (mIsAutomotive && !locationEnabled) {
            Slogf.i(LOG_TAG, "setLocationEnabled(%s, %b): ignoring for user %s on automotive build",
                    who.flattenToShortString(), locationEnabled, userHandle);
            return;
        }

        mInjector.binderWithCleanCallingIdentity(() -> {
            boolean wasLocationEnabled = mInjector.getLocationManager().isLocationEnabledForUser(
                    userHandle);
            Slogf.v(LOG_TAG, "calling locationMgr.setLocationEnabledForUser(%b, %s) when it was %b",
                    locationEnabled, userHandle, wasLocationEnabled);
            mInjector.getLocationManager().setLocationEnabledForUser(locationEnabled, userHandle);

            // make a best effort to only show the notification if the admin is actually enabling
            // location. this is subject to race conditions with settings changes, but those are
            // unlikely to realistically interfere
            if (locationEnabled && !wasLocationEnabled) {
                showLocationSettingsEnabledNotification(userHandle);
            }
        });

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_SECURE_SETTING)
                .setAdmin(who)
                .setStrings(Settings.Secure.LOCATION_MODE, Integer.toString(
                        locationEnabled ? Settings.Secure.LOCATION_MODE_ON
                                : Settings.Secure.LOCATION_MODE_OFF))
                .write();
    }

    private void showLocationSettingsEnabledNotification(UserHandle user) {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                .addFlags(FLAG_ACTIVITY_NEW_TASK);
        // Fill the component explicitly to prevent the PendingIntent from being intercepted
        // and fired with crafted target. b/155183624
        ActivityInfo targetInfo = intent.resolveActivityInfo(
                mInjector.getPackageManager(user.getIdentifier()),
                PackageManager.MATCH_SYSTEM_ONLY);
        if (targetInfo != null) {
            intent.setComponent(targetInfo.getComponentName());
        } else {
            Slogf.wtf(LOG_TAG, "Failed to resolve intent for location settings");
        }

        // Simple notification clicks are immutable
        PendingIntent locationSettingsIntent = mInjector.pendingIntentGetActivityAsUser(mContext, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE, null,
                user);
        Notification notification = new Notification.Builder(mContext,
                SystemNotificationChannels.DEVICE_ADMIN)
                .setSmallIcon(R.drawable.ic_info_outline)
                .setContentTitle(getLocationChangedTitle())
                .setContentText(getLocationChangedText())
                .setColor(mContext.getColor(R.color.system_notification_accent_color))
                .setShowWhen(true)
                .setContentIntent(locationSettingsIntent)
                .setAutoCancel(true)
                .build();
        mHandler.post(() -> mInjector.getNotificationManager().notify(
                SystemMessage.NOTE_LOCATION_CHANGED, notification));
    }

    private String getLocationChangedTitle() {
        return getUpdatableString(
                LOCATION_CHANGED_TITLE, R.string.location_changed_notification_title);
    }

    private String getLocationChangedText() {
        return getUpdatableString(
                LOCATION_CHANGED_MESSAGE, R.string.location_changed_notification_text);
    }

    @Override
    public boolean setTime(ComponentName who, long millis) {
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        if (isPermissionCheckFlagEnabled()) {
            // This is a global action.
            enforcePermission(SET_TIME, UserHandle.USER_ALL);
        } else {
            Preconditions.checkCallAuthorization(
                    isDefaultDeviceOwner(caller)
                            || isProfileOwnerOfOrganizationOwnedDevice(caller));
        }

        // Don't allow set time when auto time is on.
        if (mInjector.settingsGlobalGetInt(Global.AUTO_TIME, 0) == 1) {
            return false;
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_TIME)
                .setAdmin(caller.getComponentName())
                .write();
        mInjector.binderWithCleanCallingIdentity(() -> mInjector.getAlarmManager().setTime(millis));
        return true;
    }

    @Override
    public boolean setTimeZone(ComponentName who, String timeZone) {
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        if (isPermissionCheckFlagEnabled()) {
            // This is a global action.
            enforcePermission(SET_TIME_ZONE, UserHandle.USER_ALL);
        } else {
            Preconditions.checkCallAuthorization(
                    isDefaultDeviceOwner(caller)
                            || isProfileOwnerOfOrganizationOwnedDevice(caller));
        }

        // Don't allow set timezone when auto timezone is on.
        if (mInjector.settingsGlobalGetInt(Global.AUTO_TIME_ZONE, 0) == 1) {
            return false;
        }
        String logInfo = "DevicePolicyManager.setTimeZone()";
        mInjector.binderWithCleanCallingIdentity(() ->
                mInjector.getAlarmManagerInternal()
                        .setTimeZone(timeZone, TIME_ZONE_CONFIDENCE_HIGH, logInfo));

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_TIME_ZONE)
                .setAdmin(caller.getComponentName())
                .write();
        return true;
    }

    @Override
    public void setSecureSetting(ComponentName who, String setting, String value) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));

        int callingUserId = caller.getUserId();
        synchronized (getLockObject()) {
            if (isDeviceOwner(who, callingUserId)) {
                if (!SECURE_SETTINGS_DEVICEOWNER_ALLOWLIST.contains(setting)
                        && !isCurrentUserDemo()) {
                    throw new SecurityException(String.format(
                            "Permission denial: Device owners cannot update %1$s", setting));
                }
            } else if (!SECURE_SETTINGS_ALLOWLIST.contains(setting) && !isCurrentUserDemo()) {
                throw new SecurityException(String.format(
                        "Permission denial: Profile owners cannot update %1$s", setting));
            }
            if (setting.equals(Settings.Secure.LOCATION_MODE)
                    && isSetSecureSettingLocationModeCheckEnabled(who.getPackageName(),
                    callingUserId)) {
                throw new UnsupportedOperationException(Settings.Secure.LOCATION_MODE + " is "
                        + "deprecated. Please use setLocationEnabled() instead.");
            }
            if (setting.equals(Settings.Secure.INSTALL_NON_MARKET_APPS)) {
                if (getTargetSdk(who.getPackageName(), callingUserId) >= Build.VERSION_CODES.O) {
                    throw new UnsupportedOperationException(Settings.Secure.INSTALL_NON_MARKET_APPS
                            + " is deprecated. Please use one of the user restrictions "
                            + UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES + " or "
                            + UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY + " instead.");
                }
                if (!mUserManager.isManagedProfile(callingUserId)) {
                    Slogf.e(LOG_TAG, "Ignoring setSecureSetting request for "
                            + setting + ". User restriction "
                            + UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES + " or "
                            + UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY
                            + " should be used instead.");
                } else {
                    try {
                        setUserRestriction(who, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                                (Integer.parseInt(value) == 0) ? true : false, /* parent */ false);
                        DevicePolicyEventLogger
                                .createEvent(DevicePolicyEnums.SET_SECURE_SETTING)
                                .setAdmin(who)
                                .setStrings(setting, value)
                                .write();
                    } catch (NumberFormatException exc) {
                        Slogf.e(LOG_TAG, "Invalid value: " + value + " for setting " + setting);
                    }
                }
                return;
            }
            mInjector.binderWithCleanCallingIdentity(() -> {
                if (Settings.Secure.DEFAULT_INPUT_METHOD.equals(setting)) {
                    final String currentValue = mInjector.settingsSecureGetStringForUser(
                            Settings.Secure.DEFAULT_INPUT_METHOD, callingUserId);
                    if (!TextUtils.equals(currentValue, value)) {
                        // Tell the content observer that the next change will be due to the owner
                        // changing the value. There is a small race condition here that we cannot
                        // avoid: Change notifications are sent asynchronously, so it is possible
                        // that there are prior notifications queued up before the one we are about
                        // to trigger. This is a corner case that will have no impact in practice.
                        mSetupContentObserver.addPendingChangeByOwnerLocked(callingUserId);
                    }
                    getUserData(callingUserId).mCurrentInputMethodSet = true;
                    saveSettingsLocked(callingUserId);
                }
                mInjector.settingsSecurePutStringForUser(setting, value, callingUserId);
                // Notify the user if it's the location mode setting that's been set, to any value
                // other than 'off'.
                if (setting.equals(Settings.Secure.LOCATION_MODE)
                        && (Integer.parseInt(value) != 0)) {
                    showLocationSettingsEnabledNotification(UserHandle.of(callingUserId));
                }
            });
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_SECURE_SETTING)
                .setAdmin(who)
                .setStrings(setting, value)
                .write();
    }

    private boolean isSetSecureSettingLocationModeCheckEnabled(String packageName, int userId) {
        return mInjector.isChangeEnabled(USE_SET_LOCATION_ENABLED, packageName, userId);
    }

    @Override
    public void setMasterVolumeMuted(ComponentName who, boolean on) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_MASTER_VOLUME_MUTED);

        synchronized (getLockObject()) {
            setUserRestriction(who, UserManager.DISALLOW_UNMUTE_DEVICE, on, /* parent */ false);
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.SET_MASTER_VOLUME_MUTED)
                    .setAdmin(who)
                    .setBoolean(on)
                    .write();
        }
    }

    @Override
    public boolean isMasterVolumeMuted(ComponentName who) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));

        synchronized (getLockObject()) {
            AudioManager audioManager =
                    (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            return audioManager.isMasterMute();
        }
    }

    @Override
    public void setUserIcon(ComponentName who, Bitmap icon) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));

        synchronized (getLockObject()) {
            mInjector.binderWithCleanCallingIdentity(
                    () -> mUserManagerInternal.setUserIcon(caller.getUserId(), icon));
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_USER_ICON)
                .setAdmin(who)
                .write();
    }

    @Override
    public boolean setKeyguardDisabled(ComponentName who, boolean disabled) {
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));

        final int userId = caller.getUserId();
        synchronized (getLockObject()) {
            Preconditions.checkCallAuthorization(isUserAffiliatedWithDeviceLocked(userId),
                    String.format(
                            "Admin %s is neither the device owner or affiliated user's profile "
                                    + "owner.", who));
        }
        if (isManagedProfile(userId)) {
            throw new SecurityException("Managed profile cannot disable keyguard");
        }
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_KEYGUARD_DISABLED);

        long ident = mInjector.binderClearCallingIdentity();
        try {
            // disallow disabling the keyguard if a password is currently set
            if (disabled && mLockPatternUtils.isSecure(userId)) {
                return false;
            }
            mLockPatternUtils.setLockScreenDisabled(disabled, userId);
            if (disabled) {
                mInjector
                        .getIWindowManager()
                        .dismissKeyguard(null /* callback */, null /* message */);
            }
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.SET_KEYGUARD_DISABLED)
                    .setAdmin(who)
                    .setBoolean(disabled)
                    .write();
        } catch (RemoteException e) {
            // Same process, does not happen.
        } finally {
            mInjector.binderRestoreCallingIdentity(ident);
        }
        return true;
    }

    @Override
    public boolean setStatusBarDisabled(ComponentName who, boolean disabled) {
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));

        int userId = caller.getUserId();
        synchronized (getLockObject()) {
            Preconditions.checkCallAuthorization(isUserAffiliatedWithDeviceLocked(userId),
                    "Admin " + who
                            + " is neither the device owner or affiliated user's profile owner.");
            if (isManagedProfile(userId)) {
                throw new SecurityException("Managed profile cannot disable status bar");
            }
            checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_STATUS_BAR_DISABLED);

            DevicePolicyData policy = getUserData(userId);
            if (policy.mStatusBarDisabled != disabled) {
                boolean isLockTaskMode = false;
                try {
                    isLockTaskMode = mInjector.getIActivityTaskManager().getLockTaskModeState()
                            != LOCK_TASK_MODE_NONE;
                } catch (RemoteException e) {
                    Slogf.e(LOG_TAG, "Failed to get LockTask mode");
                }
                if (!isLockTaskMode) {
                    if (!setStatusBarDisabledInternal(disabled, userId)) {
                        return false;
                    }
                }
                policy.mStatusBarDisabled = disabled;
                saveSettingsLocked(userId);
            }
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_STATUS_BAR_DISABLED)
                .setAdmin(who)
                .setBoolean(disabled)
                .write();
        return true;
    }

    private boolean setStatusBarDisabledInternal(boolean disabled, int userId) {
        long ident = mInjector.binderClearCallingIdentity();
        try {
            IStatusBarService statusBarService = IStatusBarService.Stub.asInterface(
                    ServiceManager.checkService(Context.STATUS_BAR_SERVICE));
            if (statusBarService != null) {
                int flags1 = disabled ? STATUS_BAR_DISABLE_MASK : StatusBarManager.DISABLE_NONE;
                int flags2 = disabled ? STATUS_BAR_DISABLE2_MASK : StatusBarManager.DISABLE2_NONE;
                statusBarService.disableForUser(flags1, mToken, mContext.getPackageName(), userId);
                statusBarService.disable2ForUser(flags2, mToken, mContext.getPackageName(), userId);
                return true;
            }
        } catch (RemoteException e) {
            Slogf.e(LOG_TAG, "Failed to disable the status bar", e);
        } finally {
            mInjector.binderRestoreCallingIdentity(ident);
        }
        return false;
    }

    /**
     * We need to update the internal state of whether a user has completed setup or a
     * device has paired once. After that, we ignore any changes that reset the
     * Settings.Secure.USER_SETUP_COMPLETE or Settings.Secure.DEVICE_PAIRED change
     * as we don't trust any apps that might try to reset them.
     * <p>
     * Unfortunately, we don't know which user's setup state was changed, so we write all of
     * them.
     */
    void updateUserSetupCompleteAndPaired() {
        List<UserInfo> users = mUserManager.getAliveUsers();
        final int N = users.size();
        for (int i = 0; i < N; i++) {
            int userHandle = users.get(i).id;
            if (mInjector.settingsSecureGetIntForUser(Settings.Secure.USER_SETUP_COMPLETE, 0,
                    userHandle) != 0) {
                DevicePolicyData policy = getUserData(userHandle);
                if (!policy.mUserSetupComplete) {
                    policy.mUserSetupComplete = true;
                    if (userHandle == UserHandle.USER_SYSTEM) {
                        mStateCache.setDeviceProvisioned(true);
                    }
                    synchronized (getLockObject()) {
                        saveSettingsLocked(userHandle);
                    }
                }
            }
            if (mIsWatch && mInjector.settingsSecureGetIntForUser(Settings.Secure.DEVICE_PAIRED, 0,
                    userHandle) != 0) {
                DevicePolicyData policy = getUserData(userHandle);
                if (!policy.mPaired) {
                    policy.mPaired = true;
                    synchronized (getLockObject()) {
                        saveSettingsLocked(userHandle);
                    }
                }
            }
        }
    }

    private class SetupContentObserver extends ContentObserver {
        private final Uri mUserSetupComplete = Settings.Secure.getUriFor(
                Settings.Secure.USER_SETUP_COMPLETE);
        private final Uri mDeviceProvisioned = Settings.Global.getUriFor(
                Settings.Global.DEVICE_PROVISIONED);
        private final Uri mPaired = Settings.Secure.getUriFor(Settings.Secure.DEVICE_PAIRED);
        private final Uri mDefaultImeChanged = Settings.Secure.getUriFor(
                Settings.Secure.DEFAULT_INPUT_METHOD);

        @GuardedBy("getLockObject()")
        private Set<Integer> mUserIdsWithPendingChangesByOwner = new ArraySet<>();

        public SetupContentObserver(Handler handler) {
            super(handler);
        }

        void register() {
            mInjector.registerContentObserver(mUserSetupComplete, false, this, UserHandle.USER_ALL);
            mInjector.registerContentObserver(mDeviceProvisioned, false, this, UserHandle.USER_ALL);
            if (mIsWatch) {
                mInjector.registerContentObserver(mPaired, false, this, UserHandle.USER_ALL);
            }
            mInjector.registerContentObserver(mDefaultImeChanged, false, this, UserHandle.USER_ALL);
        }

        @GuardedBy("getLockObject()")
        private void addPendingChangeByOwnerLocked(int userId) {
            mUserIdsWithPendingChangesByOwner.add(userId);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri, int userId) {
            if (mUserSetupComplete.equals(uri) || (mIsWatch && mPaired.equals(uri))) {
                updateUserSetupCompleteAndPaired();
            } else if (mDeviceProvisioned.equals(uri)) {
                synchronized (getLockObject()) {
                    // Set PROPERTY_DEVICE_OWNER_PRESENT, for the SUW case where setting the property
                    // is delayed until device is marked as provisioned.
                    setDeviceOwnershipSystemPropertyLocked();
                }
            } else if (mDefaultImeChanged.equals(uri)) {
                synchronized (getLockObject()) {
                    if (mUserIdsWithPendingChangesByOwner.contains(userId)) {
                        // This change notification was triggered by the owner changing the current
                        // IME. Ignore it.
                        mUserIdsWithPendingChangesByOwner.remove(userId);
                    } else {
                        // This change notification was triggered by the user manually changing the
                        // current IME.
                        getUserData(userId).mCurrentInputMethodSet = false;
                        saveSettingsLocked(userId);
                    }
                }
            }
        }
    }

    private class DevicePolicyConstantsObserver extends ContentObserver {
        final Uri mConstantsUri =
                Settings.Global.getUriFor(Settings.Global.DEVICE_POLICY_CONSTANTS);

        DevicePolicyConstantsObserver(Handler handler) {
            super(handler);
        }

        void register() {
            mInjector.registerContentObserver(
                    mConstantsUri, /* notifyForDescendents= */ false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri, int userId) {
            mConstants = loadConstants();
            invalidateBinderCaches();

            mInjector.binderWithCleanCallingIdentity(() -> {
                final Intent intent = new Intent(
                        DevicePolicyManager.ACTION_DEVICE_POLICY_CONSTANTS_CHANGED);
                intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
                final List<UserInfo> users = mUserManager.getAliveUsers();
                for (int i = 0; i < users.size(); i++) {
                    mContext.sendBroadcastAsUser(intent, UserHandle.of(users.get(i).id));
                }
            });
        }
    }

    @VisibleForTesting
    final class LocalService extends DevicePolicyManagerInternal
            implements DevicePolicyManagerLiteInternal {
        private List<OnCrossProfileWidgetProvidersChangeListener> mWidgetProviderListeners;

        @Override
        public List<String> getCrossProfileWidgetProviders(int profileId) {
            synchronized (getLockObject()) {
                if (mOwners == null) {
                    return Collections.emptyList();
                }
                ComponentName ownerComponent = mOwners.getProfileOwnerComponent(profileId);
                if (ownerComponent == null) {
                    return Collections.emptyList();
                }

                DevicePolicyData policy = getUserDataUnchecked(profileId);
                ActiveAdmin admin = policy.mAdminMap.get(ownerComponent);

                if (admin == null || admin.crossProfileWidgetProviders == null
                        || admin.crossProfileWidgetProviders.isEmpty()) {
                    return Collections.emptyList();
                }

                return admin.crossProfileWidgetProviders;
            }
        }

        @Override
        public void addOnCrossProfileWidgetProvidersChangeListener(
                OnCrossProfileWidgetProvidersChangeListener listener) {
            synchronized (getLockObject()) {
                if (mWidgetProviderListeners == null) {
                    mWidgetProviderListeners = new ArrayList<>();
                }
                if (!mWidgetProviderListeners.contains(listener)) {
                    mWidgetProviderListeners.add(listener);
                }
            }
        }

        @Override
        public @Nullable ComponentName getProfileOwnerOrDeviceOwnerSupervisionComponent(
                @NonNull UserHandle userHandle) {
            return DevicePolicyManagerService.this.getProfileOwnerOrDeviceOwnerSupervisionComponent(
                    userHandle);
        }

        @Override
        public boolean isActiveDeviceOwner(int uid) {
            return isDefaultDeviceOwner(new CallerIdentity(uid, null, null));
        }

        @Override
        public boolean isActiveProfileOwner(int uid) {
            return isProfileOwner(new CallerIdentity(uid, null, null));
        }

        @Override
        public boolean isActiveSupervisionApp(int uid) {
            if (!isProfileOwner(new CallerIdentity(uid, null, null))) {
                return false;
            }
            synchronized (getLockObject()) {
                final ActiveAdmin admin = getProfileOwnerAdminLocked(UserHandle.getUserId(uid));
                if (admin == null) {
                    return false;
                }

                return isSupervisionComponentLocked(admin.info.getComponent());
            }
        }

        private void notifyCrossProfileProvidersChanged(int userId, List<String> packages) {
            final List<OnCrossProfileWidgetProvidersChangeListener> listeners;
            synchronized (getLockObject()) {
                listeners = new ArrayList<>(mWidgetProviderListeners);
            }
            final int listenerCount = listeners.size();
            for (int i = 0; i < listenerCount; i++) {
                OnCrossProfileWidgetProvidersChangeListener listener = listeners.get(i);
                listener.onCrossProfileWidgetProvidersChanged(userId, packages);
            }
        }

        @Override
        public Intent createShowAdminSupportIntent(int userId, boolean useDefaultIfNoAdmin) {
            // This method is called from AM with its lock held, so don't take the DPMS lock.
            // b/29242568

            if (getEnforcingAdminAndUserDetailsInternal(userId, null) != null
                    || useDefaultIfNoAdmin) {
                return DevicePolicyManagerService.this.createShowAdminSupportIntent(userId);
            }
            return null;
        }

        @Override
        public Intent createUserRestrictionSupportIntent(int userId, String userRestriction) {
            Intent intent = null;
            if (getEnforcingAdminAndUserDetailsInternal(userId, userRestriction) != null) {
                intent = DevicePolicyManagerService.this.createShowAdminSupportIntent(userId);
                intent.putExtra(DevicePolicyManager.EXTRA_RESTRICTION, userRestriction);
            }
            return intent;
        }

        @Override
        public boolean isUserAffiliatedWithDevice(int userId) {
            return DevicePolicyManagerService.this.isUserAffiliatedWithDeviceLocked(userId);
        }

        @Override
        public boolean canSilentlyInstallPackage(String callerPackage, int callerUid) {
            if (callerPackage == null) {
                return false;
            }

            CallerIdentity caller = new CallerIdentity(callerUid, null, null);
            if (isUserAffiliatedWithDevice(UserHandle.getUserId(callerUid))
                    && (isActiveProfileOwner(callerUid)
                    || isDefaultDeviceOwner(caller) || isFinancedDeviceOwner(caller))) {
                // device owner or a profile owner affiliated with the device owner
                return true;
            }
            return false;
        }

        @Override
        public void reportSeparateProfileChallengeChanged(@UserIdInt int userId) {
            mInjector.binderWithCleanCallingIdentity(() -> {
                synchronized (getLockObject()) {
                    updateMaximumTimeToLockLocked(userId);
                    updatePasswordQualityCacheForUserGroup(userId);
                }
            });
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.SEPARATE_PROFILE_CHALLENGE_CHANGED)
                    .setBoolean(isSeparateProfileChallengeEnabled(userId))
                    .write();
            invalidateBinderCaches();
        }

        @Override
        public CharSequence getPrintingDisabledReasonForUser(@UserIdInt int userId) {
            synchronized (getLockObject()) {
                if (!mUserManager.hasUserRestriction(UserManager.DISALLOW_PRINTING,
                        UserHandle.of(userId))) {
                    Slogf.e(LOG_TAG, "printing is enabled for user %d", userId);
                    return null;
                }
                String ownerPackage = mOwners.getProfileOwnerPackage(userId);
                if (ownerPackage == null) {
                    ownerPackage = mOwners.getDeviceOwnerPackageName();
                }
                final String packageName = ownerPackage;
                PackageManager pm = mInjector.getPackageManager();
                PackageInfo packageInfo = mInjector.binderWithCleanCallingIdentity(() -> {
                    try {
                        return pm.getPackageInfo(packageName, 0);
                    } catch (NameNotFoundException e) {
                        Slogf.e(LOG_TAG, "getPackageInfo error", e);
                        return null;
                    }
                });
                if (packageInfo == null) {
                    Slogf.e(LOG_TAG, "packageInfo is inexplicably null");
                    return null;
                }
                ApplicationInfo appInfo = packageInfo.applicationInfo;
                if (appInfo == null) {
                    Slogf.e(LOG_TAG, "appInfo is inexplicably null");
                    return null;
                }
                CharSequence appLabel = pm.getApplicationLabel(appInfo);
                if (appLabel == null) {
                    Slogf.e(LOG_TAG, "appLabel is inexplicably null");
                    return null;
                }
                return getUpdatableString(
                        PRINTING_DISABLED_NAMED_ADMIN,
                        R.string.printing_disabled_by,
                        appLabel);
            }
        }

        @Override
        protected DevicePolicyCache getDevicePolicyCache() {
            return mPolicyCache;
        }

        @Override
        protected DeviceStateCache getDeviceStateCache() {
            return mStateCache;
        }

        @Override
        public List<String> getAllCrossProfilePackages() {
            return DevicePolicyManagerService.this.getAllCrossProfilePackages();
        }

        @Override
        public List<String> getDefaultCrossProfilePackages() {
            return DevicePolicyManagerService.this.getDefaultCrossProfilePackages();
        }

        @Override
        public void broadcastIntentToManifestReceivers(
                Intent intent, UserHandle parentHandle, boolean requiresPermission) {
            Objects.requireNonNull(intent);
            Objects.requireNonNull(parentHandle);
            Slogf.i(LOG_TAG, "Sending %s broadcast to manifest receivers.", intent.getAction());
            broadcastIntentToCrossProfileManifestReceivers(
                    intent, parentHandle, requiresPermission);
            broadcastIntentToDevicePolicyManagerRoleHolder(intent, parentHandle);
        }

        @Override
        public void enforcePermission(String permission, int targetUserId) {
            DevicePolicyManagerService.this.enforcePermission(permission, targetUserId);
        }

        @Override
        public boolean hasPermission(String permission, int targetUserId) {
            return DevicePolicyManagerService.this.hasPermission(permission, targetUserId);
        }

        private void broadcastIntentToCrossProfileManifestReceivers(
                Intent intent, UserHandle userHandle, boolean requiresPermission) {
            final int userId = userHandle.getIdentifier();
            try {
                final List<ResolveInfo> receivers = mIPackageManager.queryIntentReceivers(
                        intent, /* resolvedType= */ null,
                        STOCK_PM_FLAGS, userId).getList();
                for (ResolveInfo receiver : receivers) {
                    final String packageName = receiver.getComponentInfo().packageName;
                    if (checkCrossProfilePackagePermissions(packageName, userId,
                            requiresPermission)
                            || checkModifyQuietModePermission(packageName, userId)) {
                        Slogf.i(LOG_TAG, "Sending %s broadcast to %s.", intent.getAction(),
                                packageName);
                        final Intent packageIntent = new Intent(intent)
                                .setComponent(receiver.getComponentInfo().getComponentName())
                                .addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
                        mContext.sendBroadcastAsUser(packageIntent, userHandle);
                    }
                }
            } catch (RemoteException ex) {
                Slogf.w(LOG_TAG, "Cannot get list of broadcast receivers for %s because: %s.",
                        intent.getAction(), ex);
            }
        }

        private void broadcastIntentToDevicePolicyManagerRoleHolder(
                Intent intent, UserHandle userHandle) {
            final int userId = userHandle.getIdentifier();
            final String packageName = getDevicePolicyManagementRoleHolderPackageName(mContext);
            if (packageName == null) {
                return;
            }
            try {
                final Intent packageIntent = new Intent(intent)
                        .setPackage(packageName);
                final List<ResolveInfo> receivers = mIPackageManager.queryIntentReceivers(
                        packageIntent,
                        /* resolvedType= */ null,
                        STOCK_PM_FLAGS,
                        userId).getList();
                if (receivers.isEmpty()) {
                    return;
                }
                for (ResolveInfo receiver : receivers) {
                    final Intent componentIntent = new Intent(packageIntent)
                            .setComponent(receiver.getComponentInfo().getComponentName())
                            .addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
                    mContext.sendBroadcastAsUser(componentIntent, userHandle);
                }
            } catch (RemoteException ex) {
                Slogf.w(LOG_TAG, "Cannot get list of broadcast receivers for %s because: %s.",
                        intent.getAction(), ex);
            }
        }

        /**
         * Checks whether the package {@code packageName} has the {@code MODIFY_QUIET_MODE}
         * permission granted for the user {@code userId}.
         */
        private boolean checkModifyQuietModePermission(String packageName, @UserIdInt int userId) {
            try {
                final int uid = Objects.requireNonNull(
                        mInjector.getPackageManager().getApplicationInfoAsUser(
                                Objects.requireNonNull(packageName), /* flags= */ 0, userId)).uid;
                return PackageManager.PERMISSION_GRANTED
                        == ActivityManager.checkComponentPermission(
                        android.Manifest.permission.MODIFY_QUIET_MODE, uid, /* owningUid= */
                        -1, /* exported= */ true);
            } catch (NameNotFoundException ex) {
                Slogf.w(LOG_TAG, "Cannot find the package %s to check for permissions.",
                        packageName);
                return false;
            }
        }

        /**
         * Checks whether the package {@code packageName} has the required permissions to receive
         * cross-profile broadcasts on behalf of the user {@code userId}.
         */
        private boolean checkCrossProfilePackagePermissions(String packageName,
                @UserIdInt int userId, boolean requiresPermission) {
            final PackageManagerInternal pmInternal = LocalServices.getService(
                    PackageManagerInternal.class);
            final AndroidPackage androidPackage = pmInternal.getPackage(packageName);
            if (androidPackage == null || !androidPackage.isCrossProfile()) {
                return false;
            }
            if (!requiresPermission) {
                return true;
            }
            if (!isPackageEnabled(packageName, userId)) {
                return false;
            }
            try {
                final CrossProfileAppsInternal crossProfileAppsService = LocalServices.getService(
                        CrossProfileAppsInternal.class);
                return crossProfileAppsService.verifyPackageHasInteractAcrossProfilePermission(
                        packageName, userId);
            } catch (NameNotFoundException ex) {
                Slogf.w(LOG_TAG, "Cannot find the package %s to check for permissions.",
                        packageName);
                return false;
            }
        }

        private boolean isPackageEnabled(String packageName, @UserIdInt int userId) {
            final int callingUid = Binder.getCallingUid();
            final long ident = Binder.clearCallingIdentity();
            try {
                final PackageInfo info = mInjector.getPackageManagerInternal()
                        .getPackageInfo(
                                packageName,
                                MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE,
                                callingUid,
                                userId);
                return info != null && info.applicationInfo.enabled;
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public ComponentName getProfileOwnerAsUser(@UserIdInt int userId) {
            return DevicePolicyManagerService.this.getProfileOwnerAsUser(userId);
        }

        @Override
        public int getDeviceOwnerUserId() {
            return DevicePolicyManagerService.this.getDeviceOwnerUserId();
        }

        @Override
        public boolean isDeviceOrProfileOwnerInCallingUser(String packageName) {
            return isDeviceOwnerInCallingUser(packageName)
                    || isProfileOwnerInCallingUser(packageName);
        }

        private boolean isDeviceOwnerInCallingUser(String packageName) {
            final ComponentName deviceOwnerInCallingUser =
                    DevicePolicyManagerService.this.getDeviceOwnerComponent(
                            /* callingUserOnly= */ true);
            return deviceOwnerInCallingUser != null
                    && packageName.equals(deviceOwnerInCallingUser.getPackageName());
        }

        private boolean isProfileOwnerInCallingUser(String packageName) {
            final ComponentName profileOwnerInCallingUser =
                    getProfileOwnerAsUser(UserHandle.getCallingUserId());
            return profileOwnerInCallingUser != null
                    && packageName.equals(profileOwnerInCallingUser.getPackageName());
        }

        @Override
        public boolean supportsResetOp(int op) {
            return op == AppOpsManager.OP_INTERACT_ACROSS_PROFILES
                    && LocalServices.getService(CrossProfileAppsInternal.class) != null;
        }

        @Override
        public void resetOp(int op, String packageName, @UserIdInt int userId) {
            if (op != AppOpsManager.OP_INTERACT_ACROSS_PROFILES) {
                throw new IllegalArgumentException("Unsupported op for DPM reset: " + op);
            }
            LocalServices.getService(CrossProfileAppsInternal.class)
                    .setInteractAcrossProfilesAppOp(
                            packageName, findInteractAcrossProfilesResetMode(packageName), userId);
        }

        @Override
        public void notifyUnsafeOperationStateChanged(DevicePolicySafetyChecker checker, int reason,
                boolean isSafe) {
            // TODO(b/178494483): use EventLog instead
            // TODO(b/178494483): log metrics?
            if (VERBOSE_LOG) {
                Slogf.v(LOG_TAG, "notifyUnsafeOperationStateChanged(): %s=%b",
                        DevicePolicyManager.operationSafetyReasonToString(reason), isSafe);
            }
            Preconditions.checkArgument(mSafetyChecker == checker,
                    "invalid checker: should be %s, was %s", mSafetyChecker, checker);

            Bundle extras = new Bundle();
            extras.putInt(DeviceAdminReceiver.EXTRA_OPERATION_SAFETY_REASON, reason);
            extras.putBoolean(DeviceAdminReceiver.EXTRA_OPERATION_SAFETY_STATE, isSafe);

            if (mOwners.hasDeviceOwner()) {
                if (VERBOSE_LOG) Slogf.v(LOG_TAG, "Notifying DO");
                sendDeviceOwnerCommand(DeviceAdminReceiver.ACTION_OPERATION_SAFETY_STATE_CHANGED,
                        extras);
            }
            for (int profileOwnerId : mOwners.getProfileOwnerKeys()) {
                if (VERBOSE_LOG) Slogf.v(LOG_TAG, "Notifying PO for user " + profileOwnerId);
                sendProfileOwnerCommand(DeviceAdminReceiver.ACTION_OPERATION_SAFETY_STATE_CHANGED,
                        extras, profileOwnerId);
            }
        }

        @Override
        public boolean isKeepProfilesRunningEnabled() {
            return mKeepProfilesRunning;
        }

        private @Mode int findInteractAcrossProfilesResetMode(String packageName) {
            return getDefaultCrossProfilePackages().contains(packageName)
                    ? AppOpsManager.MODE_ALLOWED
                    : AppOpsManager.opToDefaultMode(AppOpsManager.OP_INTERACT_ACROSS_PROFILES);
        }

        @Override
        public boolean isUserOrganizationManaged(@UserIdInt int userHandle) {
            return getDeviceStateCache().isUserOrganizationManaged(userHandle);
        }
    }

    private Intent createShowAdminSupportIntent(int userId) {
        // This method is called with AMS lock held, so don't take DPMS lock
        final Intent intent = new Intent(Settings.ACTION_SHOW_ADMIN_SUPPORT_DETAILS);
        intent.putExtra(Intent.EXTRA_USER_ID, userId);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /**
     * @param restriction The restriction enforced by admin. It could be any user restriction or
     *                    policy like {@link DevicePolicyManager#POLICY_DISABLE_CAMERA},
     *                    {@link DevicePolicyManager#POLICY_DISABLE_SCREEN_CAPTURE} and  {@link
     *                    DevicePolicyManager#POLICY_SUSPEND_PACKAGES}.
     */
    private Bundle getEnforcingAdminAndUserDetailsInternal(int userId, String restriction) {
        Bundle result = null;

        // For POLICY_SUSPEND_PACKAGES return PO or DO to keep the behavior same as
        // before the bug fix for b/192245204.
        if (restriction == null || DevicePolicyManager.POLICY_SUSPEND_PACKAGES.equals(
                restriction)) {
            ComponentName profileOwner = mOwners.getProfileOwnerComponent(userId);
            if (profileOwner != null) {
                result = new Bundle();
                result.putInt(Intent.EXTRA_USER_ID, userId);
                result.putParcelable(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        profileOwner);
                return result;
            }
            final Pair<Integer, ComponentName> deviceOwner =
                    mOwners.getDeviceOwnerUserIdAndComponent();
            if (deviceOwner != null && deviceOwner.first == userId) {
                result = new Bundle();
                result.putInt(Intent.EXTRA_USER_ID, userId);
                result.putParcelable(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        deviceOwner.second);
                return result;
            }
        } else if (DevicePolicyManager.POLICY_DISABLE_CAMERA.equals(restriction)
                || DevicePolicyManager.POLICY_DISABLE_SCREEN_CAPTURE.equals(restriction)) {
            synchronized (getLockObject()) {
                final DevicePolicyData policy = getUserData(userId);
                final int N = policy.mAdminList.size();
                for (int i = 0; i < N; i++) {
                    final ActiveAdmin admin = policy.mAdminList.get(i);
                    if ((admin.disableCamera &&
                            DevicePolicyManager.POLICY_DISABLE_CAMERA.equals(restriction))
                            || (admin.disableScreenCapture && DevicePolicyManager
                            .POLICY_DISABLE_SCREEN_CAPTURE.equals(restriction))) {
                        result = new Bundle();
                        result.putInt(Intent.EXTRA_USER_ID, userId);
                        result.putParcelable(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                admin.info.getComponent());
                        return result;
                    }
                }
                // For the camera, a device owner on a different user can disable it globally,
                // so we need an additional check.
                if (result == null
                        && DevicePolicyManager.POLICY_DISABLE_CAMERA.equals(restriction)) {
                    final ActiveAdmin admin = getDeviceOwnerAdminLocked();
                    if (admin != null && admin.disableCamera) {
                        result = new Bundle();
                        result.putInt(Intent.EXTRA_USER_ID, mOwners.getDeviceOwnerUserId());
                        result.putParcelable(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                admin.info.getComponent());
                        return result;
                    }
                }
            }
        } else {
            long ident = mInjector.binderClearCallingIdentity();
            try {
                List<UserManager.EnforcingUser> sources = mUserManager
                        .getUserRestrictionSources(restriction, UserHandle.of(userId));
                if (sources == null) {
                    // The restriction is not enforced.
                    return null;
                }
                int sizeBefore = sources.size();
                if (sizeBefore > 1) {
                    Slogf.d(LOG_TAG, "getEnforcingAdminAndUserDetailsInternal(%d, %s): "
                            + "%d sources found, excluding those set by UserManager",
                            userId, restriction, sizeBefore);
                    sources = getDevicePolicySources(sources);
                }
                if (sources.isEmpty()) {
                    // The restriction is not enforced (or is just enforced by the system)
                    return null;
                }

                if (sources.size() > 1) {
                    // In this case, we'll show an admin support dialog that does not
                    // specify the admin.
                    // TODO(b/128928355): if this restriction is enforced by multiple DPCs, return
                    // the admin for the calling user.
                    Slogf.w(LOG_TAG, "getEnforcingAdminAndUserDetailsInternal(%d, %s): multiple "
                            + "sources for restriction %s on user %d", restriction, userId);
                    result = new Bundle();
                    result.putInt(Intent.EXTRA_USER_ID, userId);
                    return result;
                }
                final UserManager.EnforcingUser enforcingUser = sources.get(0);
                final int sourceType = enforcingUser.getUserRestrictionSource();
                final int enforcingUserId = enforcingUser.getUserHandle().getIdentifier();
                if (sourceType == UserManager.RESTRICTION_SOURCE_PROFILE_OWNER) {
                    // Restriction was enforced by PO
                    final ComponentName profileOwner = mOwners.getProfileOwnerComponent(
                            enforcingUserId);
                    if (profileOwner != null) {
                        result = new Bundle();
                        result.putInt(Intent.EXTRA_USER_ID, enforcingUserId);
                        result.putParcelable(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                profileOwner);
                        return result;
                    }
                } else if (sourceType == UserManager.RESTRICTION_SOURCE_DEVICE_OWNER) {
                    // Restriction was enforced by DO
                    final Pair<Integer, ComponentName> deviceOwner =
                            mOwners.getDeviceOwnerUserIdAndComponent();
                    if (deviceOwner != null) {
                        result = new Bundle();
                        result.putInt(Intent.EXTRA_USER_ID, deviceOwner.first);
                        result.putParcelable(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                deviceOwner.second);
                        return result;
                    }
                } else if (sourceType == UserManager.RESTRICTION_SOURCE_SYSTEM) {
                    /*
                     * In this case, the user restriction is enforced by the system.
                     * So we won't show an admin support intent, even if it is also
                     * enforced by a profile/device owner.
                     */
                    return null;
                }
            } finally {
                mInjector.binderRestoreCallingIdentity(ident);
            }
        }
        return null;
    }

    /**
     *  Excludes restrictions imposed by UserManager.
     */
    private List<UserManager.EnforcingUser> getDevicePolicySources(
            List<UserManager.EnforcingUser> sources) {
        int sizeBefore = sources.size();
        List<UserManager.EnforcingUser> realSources = new ArrayList<>(sizeBefore);
        for (int i = 0; i < sizeBefore; i++) {
            UserManager.EnforcingUser source = sources.get(i);
            int type = source.getUserRestrictionSource();
            if (type != UserManager.RESTRICTION_SOURCE_PROFILE_OWNER
                    && type != UserManager.RESTRICTION_SOURCE_DEVICE_OWNER) {
                // TODO(b/128928355): add unit test
                Slogf.d(LOG_TAG, "excluding source of type %s at index %d",
                        userRestrictionSourceToString(type), i);
                continue;
            }
            realSources.add(source);
        }
        return realSources;
    }

    private static String userRestrictionSourceToString(@UserRestrictionSource int source) {
        return DebugUtils.flagsToString(UserManager.class, "RESTRICTION_", source);
    }

    /**
     * @param restriction The restriction enforced by admin. It could be any user restriction or
     *                    policy like {@link DevicePolicyManager#POLICY_DISABLE_CAMERA} and
     *                    {@link DevicePolicyManager#POLICY_DISABLE_SCREEN_CAPTURE}.
     * @return Details of admin and user which enforced the restriction for the userId.
     */
    @Override
    public Bundle getEnforcingAdminAndUserDetails(int userId, String restriction) {
        Preconditions.checkCallAuthorization(isSystemUid(getCallerIdentity()));
        return getEnforcingAdminAndUserDetailsInternal(userId, restriction);
    }

    /**
     * @param restriction The restriction enforced by admin. It could be any user restriction or
     *                    policy like {@link DevicePolicyManager#POLICY_DISABLE_CAMERA} and
     *                    {@link DevicePolicyManager#POLICY_DISABLE_SCREEN_CAPTURE}.
     */
    @Override
    public Intent createAdminSupportIntent(String restriction) {
        Objects.requireNonNull(restriction);
        final CallerIdentity caller = getCallerIdentity();
        final int userId = caller.getUserId();
        Intent intent = null;
        if (getEnforcingAdminAndUserDetailsInternal(userId, restriction) != null) {
            intent = createShowAdminSupportIntent(userId);
            intent.putExtra(DevicePolicyManager.EXTRA_RESTRICTION, restriction);
        }
        return intent;
    }

    /**
     * Returns true if specified admin is allowed to limit passwords and has a
     * {@code mPasswordPolicy.quality} of at least {@code minPasswordQuality}
     */
    private static boolean isLimitPasswordAllowed(ActiveAdmin admin, int minPasswordQuality) {
        if (admin.mPasswordPolicy.quality < minPasswordQuality) {
            return false;
        }
        return admin.info.usesPolicy(DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
    }

    @Override
    public void setCredentialManagerPolicy(PackagePolicy policy) {
        if (!mHasFeature) {
            return;
        }
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(canWriteCredentialManagerPolicy(caller));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            if (Objects.equals(admin.mCredentialManagerPolicy, policy)) {
                return;
            }

            admin.mCredentialManagerPolicy = policy;
            saveSettingsLocked(caller.getUserId());
        }
    }

    private boolean canWriteCredentialManagerPolicy(CallerIdentity caller) {
        return (isProfileOwner(caller) && isManagedProfile(caller.getUserId()))
                        || isDefaultDeviceOwner(caller)
                        || hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS);
    }

    @Override
    public PackagePolicy getCredentialManagerPolicy() {
        if (!mHasFeature) {
            return null;
        }
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                canWriteCredentialManagerPolicy(caller) || canQueryAdminPolicy(caller));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            return (admin != null) ? admin.mCredentialManagerPolicy : null;
        }
    }

    @Override
    public void setSystemUpdatePolicy(ComponentName who, SystemUpdatePolicy policy) {
        if (policy != null) {
            // throws exception if policy type is invalid
            policy.validateType();
            // throws exception if freeze period is invalid
            policy.validateFreezePeriods();
            Pair<LocalDate, LocalDate> record = mOwners.getSystemUpdateFreezePeriodRecord();
            // throws exception if freeze period is incompatible with previous freeze period record
            policy.validateAgainstPreviousFreezePeriod(record.first, record.second,
                    LocalDate.now());
        }
        final CallerIdentity caller = getCallerIdentity(who);

        synchronized (getLockObject()) {
            Preconditions.checkCallAuthorization(isProfileOwnerOfOrganizationOwnedDevice(caller)
                    || isDefaultDeviceOwner(caller));
            checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_SYSTEM_UPDATE_POLICY);

            if (policy == null) {
                mOwners.clearSystemUpdatePolicy();
            } else {
                mOwners.setSystemUpdatePolicy(policy);
                updateSystemUpdateFreezePeriodsRecord(/* saveIfChanged */ false);
            }
            mOwners.writeDeviceOwner();
        }
        mInjector.binderWithCleanCallingIdentity(() -> mContext.sendBroadcastAsUser(
                new Intent(ACTION_SYSTEM_UPDATE_POLICY_CHANGED), UserHandle.SYSTEM));
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_SYSTEM_UPDATE_POLICY)
                .setAdmin(who)
                .setInt(policy != null ? policy.getPolicyType() : 0)
                .write();
    }

    @Override
    public SystemUpdatePolicy getSystemUpdatePolicy() {
        synchronized (getLockObject()) {
            SystemUpdatePolicy policy =  mOwners.getSystemUpdatePolicy();
            if (policy != null && !policy.isValid()) {
                Slogf.w(LOG_TAG, "Stored system update policy is invalid, return null instead.");
                return null;
            }
            return policy;
        }
    }

    private static boolean withinRange(Pair<LocalDate, LocalDate> range, LocalDate date) {
        return (!date.isBefore(range.first) && !date.isAfter(range.second));
    }

    /**
     * keeps track of the last continuous period when the system is under OTA freeze.
     *
     * DPMS keeps track of the previous dates during which OTA was freezed as a result of an
     * system update policy with freeze periods in effect. This is needed to make robust
     * validation on new system update polices, for example to prevent the OTA from being
     * frozen for more than 90 days if the DPC keeps resetting a new 24-hour freeze period
     * on midnight everyday, or having freeze periods closer than 60 days apart by DPC resetting
     * a new freeze period after a few days.
     *
     * @param saveIfChanged whether to persist the result on disk if freeze period record is
     *            updated. This should only be set to {@code false} if there is a guaranteed
     *            mOwners.writeDeviceOwner() later in the control flow to reduce the number of
     *            disk writes. Otherwise you risk inconsistent on-disk state.
     *
     * @see SystemUpdatePolicy#validateAgainstPreviousFreezePeriod
     */
    private void updateSystemUpdateFreezePeriodsRecord(boolean saveIfChanged) {
        Slogf.d(LOG_TAG, "updateSystemUpdateFreezePeriodsRecord");
        synchronized (getLockObject()) {
            final SystemUpdatePolicy policy = mOwners.getSystemUpdatePolicy();
            if (policy == null) {
                return;
            }
            final LocalDate now = LocalDate.now();
            final Pair<LocalDate, LocalDate> currentPeriod = policy.getCurrentFreezePeriod(now);
            if (currentPeriod == null) {
                return;
            }
            final Pair<LocalDate, LocalDate> record = mOwners.getSystemUpdateFreezePeriodRecord();
            final LocalDate start = record.first;
            final LocalDate end = record.second;
            final boolean changed;
            if (end == null || start == null) {
                // Start a new period if there is none at the moment
                changed = mOwners.setSystemUpdateFreezePeriodRecord(now, now);
            } else if (now.equals(end.plusDays(1))) {
                // Extend the existing period
                changed = mOwners.setSystemUpdateFreezePeriodRecord(start, now);
            } else if (now.isAfter(end.plusDays(1))) {
                if (withinRange(currentPeriod, start) && withinRange(currentPeriod, end)) {
                    // The device might be off for some period. If the past freeze record
                    // is within range of the current freeze period, assume the device was off
                    // during the period [end, now] and extend the freeze record to [start, now].
                    changed = mOwners.setSystemUpdateFreezePeriodRecord(start, now);
                } else {
                    changed = mOwners.setSystemUpdateFreezePeriodRecord(now, now);
                }
            } else if (now.isBefore(start)) {
                // Systm clock was adjusted backwards, restart record
                changed = mOwners.setSystemUpdateFreezePeriodRecord(now, now);
            } else /* start <= now <= end */ {
                changed = false;
            }
            if (changed && saveIfChanged) {
                mOwners.writeDeviceOwner();
            }
        }
    }

    @Override
    public void clearSystemUpdatePolicyFreezePeriodRecord() {
        Preconditions.checkCallAuthorization(isAdb(getCallerIdentity())
                        || hasCallingOrSelfPermission(permission.CLEAR_FREEZE_PERIOD),
                "Caller must be shell, or hold CLEAR_FREEZE_PERIOD permission to call "
                        + "clearSystemUpdatePolicyFreezePeriodRecord");
        synchronized (getLockObject()) {
            // Print out current record to help diagnosed CTS failures
            Slogf.i(LOG_TAG, "Clear freeze period record: "
                    + mOwners.getSystemUpdateFreezePeriodRecordAsString());
            if (mOwners.setSystemUpdateFreezePeriodRecord(null, null)) {
                mOwners.writeDeviceOwner();
            }
        }
    }

    /**
     * Checks if any of the packages associated with the UID of the app provided is that
     * of the device owner.
     * @param appUid UID of the app to check.
     * @return {@code true} if any of the packages are the device owner, {@code false} otherwise.
     */
    private boolean isUidDeviceOwnerLocked(int appUid) {
        ensureLocked();
        final String deviceOwnerPackageName = mOwners.getDeviceOwnerComponent()
                .getPackageName();
        try {
            String[] pkgs = mInjector.getIPackageManager().getPackagesForUid(appUid);
            if (pkgs == null) {
                return false;
            }

            for (String pkg : pkgs) {
                if (deviceOwnerPackageName.equals(pkg)) {
                    return true;
                }
            }
        } catch (RemoteException e) {
            return false;
        }
        return false;
    }

    @Override
    public void notifyPendingSystemUpdate(@Nullable SystemUpdateInfo info) {
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.NOTIFY_PENDING_SYSTEM_UPDATE),
                "Only the system update service can broadcast update information");

        if (UserHandle.getCallingUserId() != UserHandle.USER_SYSTEM) {
            Slogf.w(LOG_TAG, "Only the system update service in the system user can broadcast "
                    + "update information.");
            return;
        }

        if (!mOwners.saveSystemUpdateInfo(info)) {
            // Pending system update hasn't changed, don't send duplicate notification.
            return;
        }

        final Intent intent = new Intent(DeviceAdminReceiver.ACTION_NOTIFY_PENDING_SYSTEM_UPDATE)
                .putExtra(DeviceAdminReceiver.EXTRA_SYSTEM_UPDATE_RECEIVED_TIME,
                        info == null ? -1 : info.getReceivedTime());

        mInjector.binderWithCleanCallingIdentity(() -> {
            synchronized (getLockObject()) {
                // Broadcast to device owner first if there is one.
                if (mOwners.hasDeviceOwner()) {
                    final UserHandle deviceOwnerUser =
                            UserHandle.of(mOwners.getDeviceOwnerUserId());
                    intent.setComponent(mOwners.getDeviceOwnerComponent());
                    mContext.sendBroadcastAsUser(intent, deviceOwnerUser);
                }
            }
            // Get running users.
            final int runningUserIds[];
            try {
                runningUserIds = mInjector.getIActivityManager().getRunningUserIds();
            } catch (RemoteException e) {
                // Shouldn't happen.
                Slogf.e(LOG_TAG, "Could not retrieve the list of running users", e);
                return;
            }
            // Send broadcasts to corresponding profile owners if any.
            for (final int userId : runningUserIds) {
                synchronized (getLockObject()) {
                    final ComponentName profileOwnerPackage =
                            mOwners.getProfileOwnerComponent(userId);
                    if (profileOwnerPackage != null) {
                        intent.setComponent(profileOwnerPackage);
                        mContext.sendBroadcastAsUser(intent, UserHandle.of(userId));
                    }
                }
            }
        });
    }

    @Override
    public SystemUpdateInfo getPendingSystemUpdate(ComponentName admin) {
        Objects.requireNonNull(admin, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));

        return mOwners.getSystemUpdateInfo();
    }

    @Override
    public void setPermissionPolicy(ComponentName admin, String callerPackage, int policy) {
        final CallerIdentity caller = getCallerIdentity(admin, callerPackage);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_PERMISSION_GRANT)));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_PERMISSION_POLICY);

        final int forUser = caller.getUserId();
        synchronized (getLockObject()) {
            DevicePolicyData userPolicy = getUserData(forUser);
            if (userPolicy.mPermissionPolicy != policy) {
                userPolicy.mPermissionPolicy = policy;
                mPolicyCache.setPermissionPolicy(forUser, policy);
                saveSettingsLocked(forUser);
            }
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_PERMISSION_POLICY)
                .setAdmin(caller.getPackageName())
                .setInt(policy)
                .setBoolean(/* isDelegate */ admin == null)
                .write();
    }

    private void updatePermissionPolicyCache(int userId) {
        synchronized (getLockObject()) {
            DevicePolicyData userPolicy = getUserData(userId);
            mPolicyCache.setPermissionPolicy(userId, userPolicy.mPermissionPolicy);
        }
    }

    @Override
    public int getPermissionPolicy(ComponentName admin) throws RemoteException {
        int userId = UserHandle.getCallingUserId();
        return mPolicyCache.getPermissionPolicy(userId);
    }

    @Override
    public void setPermissionGrantState(ComponentName admin, String callerPackage,
            String packageName, String permission, int grantState, RemoteCallback callback)
            throws RemoteException {
        Objects.requireNonNull(callback);

        final CallerIdentity caller = getCallerIdentity(admin, callerPackage);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)
                || isFinancedDeviceOwner(caller)))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_PERMISSION_GRANT)));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_PERMISSION_GRANT_STATE);

        synchronized (getLockObject()) {
            if (isFinancedDeviceOwner(caller)) {
                enforcePermissionGrantStateOnFinancedDevice(packageName, permission);
            }
        }
        if (isCoexistenceEnabled(caller)) {
            mDevicePolicyEngine.setLocalPolicy(
                    PolicyDefinition.PERMISSION_GRANT(packageName, permission),
                    // TODO(b/260573124): Add correct enforcing admin when permission changes are
                    //  merged, and don't forget to handle delegates! Enterprise admins assume
                    //  component name isn't null.
                    EnforcingAdmin.createEnterpriseEnforcingAdmin(
                            caller.getComponentName(), caller.getUserId()),
                    grantState,
                    caller.getUserId());
            // TODO: update javadoc to reflect that callback no longer return success/failure
            callback.sendResult(Bundle.EMPTY);
        } else {
            synchronized (getLockObject()) {
            long ident = mInjector.binderClearCallingIdentity();
            try {
                boolean isPostQAdmin = getTargetSdk(caller.getPackageName(), caller.getUserId())
                        >= android.os.Build.VERSION_CODES.Q;
                if (!isPostQAdmin) {
                    // Legacy admins assume that they cannot control pre-M apps
                    if (getTargetSdk(packageName, caller.getUserId())
                            < android.os.Build.VERSION_CODES.M) {
                        callback.sendResult(null);
                        return;
                    }
                }
                if (!isRuntimePermission(permission)) {
                    callback.sendResult(null);
                    return;
                }
                if (grantState == PERMISSION_GRANT_STATE_GRANTED
                        || grantState == DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
                        || grantState == DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT) {
                    AdminPermissionControlParams permissionParams =
                            new AdminPermissionControlParams(packageName, permission,
                                    grantState,
                                    canAdminGrantSensorsPermissions());
                    mInjector.getPermissionControllerManager(caller.getUserHandle())
                            .setRuntimePermissionGrantStateByDeviceAdmin(
                                    caller.getPackageName(),
                                    permissionParams, mContext.getMainExecutor(),
                                    (permissionWasSet) -> {
                                        if (isPostQAdmin && !permissionWasSet) {
                                            callback.sendResult(null);
                                            return;
                                        }

                                        DevicePolicyEventLogger
                                                .createEvent(DevicePolicyEnums
                                                        .SET_PERMISSION_GRANT_STATE)
                                                .setAdmin(caller.getPackageName())
                                                .setStrings(permission)
                                                .setInt(grantState)
                                                .setBoolean(/* isDelegate */ admin == null)
                                                .write();

                                        callback.sendResult(Bundle.EMPTY);
                                    });
                    }
                } catch (SecurityException e) {
                    Slogf.e(LOG_TAG, "Could not set permission grant state", e);

                    callback.sendResult(null);
                } finally {
                    mInjector.binderRestoreCallingIdentity(ident);
                }
            }
        }
    }

    private void enforcePermissionGrantStateOnFinancedDevice(
            String packageName, String permission) {
        if (!Manifest.permission.READ_PHONE_STATE.equals(permission)) {
            throw new SecurityException(permission + " cannot be used when managing a financed"
                    + " device for permission grant state");
        } else if (!mOwners.getDeviceOwnerPackageName().equals(packageName)) {
            throw new SecurityException("Device owner package is the only package that can be used"
                    + " for permission grant state when managing a financed device");
        }
    }

    @Override
    public int getPermissionGrantState(ComponentName admin, String callerPackage,
            String packageName, String permission) throws RemoteException {
        final CallerIdentity caller = getCallerIdentity(admin, callerPackage);
        Preconditions.checkCallAuthorization(isSystemUid(caller) || (caller.hasAdminComponent()
                && (isProfileOwner(caller) || isDefaultDeviceOwner(caller)
                || isFinancedDeviceOwner(caller)))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_PERMISSION_GRANT)));

        synchronized (getLockObject()) {
            if (isFinancedDeviceOwner(caller)) {
                enforcePermissionGrantStateOnFinancedDevice(packageName, permission);
            }
            return mInjector.binderWithCleanCallingIdentity(() -> {
                int granted;
                if (getTargetSdk(caller.getPackageName(), caller.getUserId())
                        < android.os.Build.VERSION_CODES.Q) {
                    // The per-Q behavior was to not check the app-ops state.
                    granted = mIPackageManager.checkPermission(permission, packageName,
                            caller.getUserId());
                } else {
                    try {
                        int uid = mInjector.getPackageManager().getPackageUidAsUser(packageName,
                                caller.getUserId());
                        if (PermissionChecker.checkPermissionForPreflight(mContext, permission,
                                PermissionChecker.PID_UNKNOWN, uid, packageName)
                                        != PermissionChecker.PERMISSION_GRANTED) {
                            granted = PackageManager.PERMISSION_DENIED;
                        } else {
                            granted = PackageManager.PERMISSION_GRANTED;
                        }
                    } catch (NameNotFoundException e) {
                        // Package does not exit
                        return DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
                    }
                }
                int permFlags = mInjector.getPackageManager().getPermissionFlags(
                        permission, packageName, caller.getUserHandle());
                if ((permFlags & PackageManager.FLAG_PERMISSION_POLICY_FIXED)
                        != PackageManager.FLAG_PERMISSION_POLICY_FIXED) {
                    // Not controlled by policy
                    return DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
                } else {
                    // Policy controlled so return result based on permission grant state
                    return granted == PackageManager.PERMISSION_GRANTED
                            ? DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                            : DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
                }
            });
        }
    }

    boolean isPackageInstalledForUser(String packageName, int userHandle) {
        return mInjector.binderWithCleanCallingIdentity(() -> {
            try {
                PackageInfo pi = mInjector.getIPackageManager().getPackageInfo(packageName, 0,
                        userHandle);
                return (pi != null) && (pi.applicationInfo.flags != 0);
            } catch (RemoteException re) {
                throw new RuntimeException("Package manager has died", re);
            }
        });
    }

    private boolean isRuntimePermission(String permissionName) {
        try {
            final PackageManager packageManager = mInjector.getPackageManager();
            PermissionInfo permissionInfo = packageManager.getPermissionInfo(permissionName, 0);
            return (permissionInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE)
                    == PermissionInfo.PROTECTION_DANGEROUS;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public boolean isProvisioningAllowed(String action, String packageName) {
        Objects.requireNonNull(packageName);
        final CallerIdentity caller = getCallerIdentity();
        final long ident = mInjector.binderClearCallingIdentity();
        try {
            final List<String> callerUidPackageNames = Arrays.asList(
                    mInjector.getPackageManager().getPackagesForUid(caller.getUid()));
            Preconditions.checkArgument(callerUidPackageNames.contains(packageName),
                    "Caller uid doesn't match the one for the provided package.");
        } finally {
            mInjector.binderRestoreCallingIdentity(ident);
        }

        return checkProvisioningPreconditionSkipPermission(action, packageName, caller.getUserId())
                == STATUS_OK;
    }

    @Override
    public int checkProvisioningPrecondition(String action, String packageName) {
        Objects.requireNonNull(packageName, "packageName is null");
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        long originalId = mInjector.binderClearCallingIdentity();
        try {
            return checkProvisioningPreconditionSkipPermission(
                    action, packageName, caller.getUserId());
        } finally {
            mInjector.binderRestoreCallingIdentity(originalId);
        }

    }
    private int checkProvisioningPreconditionSkipPermission(String action,
            String packageName, int userId) {
        if (!mHasFeature) {
            logMissingFeatureAction("Cannot check provisioning for action " + action);
            return STATUS_DEVICE_ADMIN_NOT_SUPPORTED;
        }
        if (!isProvisioningAllowed()) {
            return STATUS_PROVISIONING_NOT_ALLOWED_FOR_NON_DEVELOPER_USERS;
        }
        final int code = checkProvisioningPreConditionSkipPermissionNoLog(
                action, packageName, userId);
        if (code != STATUS_OK) {
            Slogf.d(LOG_TAG, "checkProvisioningPreCondition(" + action + ", " + packageName
                    + ") failed: "
                    + computeProvisioningErrorString(code, mInjector.userHandleGetCallingUserId()));
        }
        return code;
    }

    /**
     *  Checks if provisioning is allowed during regular usage (non-developer/CTS). This could
     *  return {@code false} if the device has an overlaid config value set to false. If not set,
     *  the default is true.
     */
    private boolean isProvisioningAllowed() {
        boolean isDeveloperMode = isDeveloperMode(mContext);
        boolean isProvisioningAllowedForNormalUsers = SystemProperties.getBoolean(
                ALLOW_USER_PROVISIONING_KEY, /* defValue= */ true);

        return isDeveloperMode || isProvisioningAllowedForNormalUsers;
    }

    private static boolean isDeveloperMode(Context context) {
        return Global.getInt(context.getContentResolver(), Global.ADB_ENABLED, 0) > 0;
    }

    private int checkProvisioningPreConditionSkipPermissionNoLog(String action,
            String packageName, int userId) {
        if (action != null) {
            switch (action) {
                case DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE:
                    return checkManagedProfileProvisioningPreCondition(packageName, userId);
                case DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE:
                case DevicePolicyManager.ACTION_PROVISION_FINANCED_DEVICE:
                    return checkDeviceOwnerProvisioningPreCondition(userId);
            }
        }
        throw new IllegalArgumentException("Unknown provisioning action " + action);
    }

    /**
     * The device owner can only be set before the setup phase of the primary user has completed,
     * except for adb command if no accounts or additional users are present on the device.
     */
    private int checkDeviceOwnerProvisioningPreConditionLocked(@Nullable ComponentName owner,
            @UserIdInt int deviceOwnerUserId, @UserIdInt int callingUserId, boolean isAdb,
            boolean hasIncompatibleAccountsOrNonAdb) {
        if (mOwners.hasDeviceOwner()) {
            return STATUS_HAS_DEVICE_OWNER;
        }
        if (mOwners.hasProfileOwner(deviceOwnerUserId)) {
            return STATUS_USER_HAS_PROFILE_OWNER;
        }

        if (!mUserManager.isUserRunning(new UserHandle(deviceOwnerUserId))) {
            return STATUS_USER_NOT_RUNNING;
        }
        if (mIsWatch && hasPaired(UserHandle.USER_SYSTEM)) {
            return STATUS_HAS_PAIRED;
        }

        boolean isHeadlessSystemUserMode = mInjector.userManagerIsHeadlessSystemUserMode();

        if (isHeadlessSystemUserMode) {
            if (deviceOwnerUserId != UserHandle.USER_SYSTEM) {
                Slogf.e(LOG_TAG, "In headless system user mode, "
                        + "device owner can only be set on headless system user.");
                return STATUS_NOT_SYSTEM_USER;
            }

            if (owner != null) {
                DeviceAdminInfo adminInfo = findAdmin(
                        owner, deviceOwnerUserId, /* throwForMissingPermission= */ false);

                if (adminInfo.getHeadlessDeviceOwnerMode()
                        != HEADLESS_DEVICE_OWNER_MODE_AFFILIATED) {
                    return STATUS_HEADLESS_SYSTEM_USER_MODE_NOT_SUPPORTED;
                }
            }
        }

        if (isAdb) {
            // If shell command runs after user setup completed check device status. Otherwise, OK.
            if (mIsWatch || hasUserSetupCompleted(UserHandle.USER_SYSTEM)) {
                // DO can be setup only if there are no users which are neither created by default
                // nor marked as FOR_TESTING

                if (nonTestNonPrecreatedUsersExist()) {
                    return STATUS_NONSYSTEM_USER_EXISTS;
                }

                int currentForegroundUser = getCurrentForegroundUserId();
                if (callingUserId != currentForegroundUser
                        && mInjector.userManagerIsHeadlessSystemUserMode()
                        && currentForegroundUser == UserHandle.USER_SYSTEM) {
                    Slogf.wtf(LOG_TAG, "In headless system user mode, "
                            + "current user cannot be system user when setting device owner");
                    return STATUS_SYSTEM_USER;
                }
                if (hasIncompatibleAccountsOrNonAdb) {
                    return STATUS_ACCOUNTS_NOT_EMPTY;
                }
            }
            return STATUS_OK;
        } else {
            // DO has to be user 0
            if (deviceOwnerUserId != UserHandle.USER_SYSTEM) {
                return STATUS_NOT_SYSTEM_USER;
            }
            // Only provision DO before setup wizard completes
            if (hasUserSetupCompleted(UserHandle.USER_SYSTEM)) {
                return STATUS_USER_SETUP_COMPLETED;
            }
            return STATUS_OK;
        }
    }

    /**
     * True if there are any users on the device which were not setup by default (1 usually, 2 for
     * devices with a headless system user) and also are not marked as FOR_TESTING.
     */
    private boolean nonTestNonPrecreatedUsersExist() {
        int allowedUsers = UserManager.isHeadlessSystemUserMode() ? 2 : 1;
        return mUserManagerInternal.getUsers(/* excludeDying= */ true).stream()
                .filter(u -> !u.isForTesting())
                .count() > allowedUsers;
    }

    private int checkDeviceOwnerProvisioningPreCondition(@UserIdInt int callingUserId) {
        synchronized (getLockObject()) {
            final int deviceOwnerUserId = mInjector.userManagerIsHeadlessSystemUserMode()
                    ? UserHandle.USER_SYSTEM
                    : callingUserId;
            Slogf.i(LOG_TAG, "Calling user %d, device owner will be set on user %d",
                    callingUserId, deviceOwnerUserId);
            // hasIncompatibleAccountsOrNonAdb doesn't matter since the caller is not adb.
            return checkDeviceOwnerProvisioningPreConditionLocked(/* owner unknown */ null,
                    deviceOwnerUserId, callingUserId, /* isAdb= */ false,
                    /* hasIncompatibleAccountsOrNonAdb=*/ true);
        }
    }

    private int checkManagedProfileProvisioningPreCondition(String packageName,
            @UserIdInt int callingUserId) {
        if (!hasFeatureManagedUsers()) {
            return STATUS_MANAGED_USERS_NOT_SUPPORTED;
        }
        if (getProfileOwnerAsUser(callingUserId) != null) {
            // Managed user cannot have a managed profile.
            return STATUS_USER_HAS_PROFILE_OWNER;
        }

        final long ident = mInjector.binderClearCallingIdentity();
        try {
            final UserHandle callingUserHandle = UserHandle.of(callingUserId);
            final boolean hasDeviceOwner;
            synchronized (getLockObject()) {
                hasDeviceOwner = getDeviceOwnerAdminLocked() != null;
            }

            final boolean addingProfileRestricted = mUserManager.hasUserRestriction(
                    UserManager.DISALLOW_ADD_MANAGED_PROFILE, callingUserHandle);

            if (mUserManager.getUserInfo(callingUserId).isProfile()) {
                Slogf.i(LOG_TAG, "Calling user %d is a profile, cannot add another.",
                        callingUserId);
                // The check is called from inside a managed profile. A managed profile cannot
                // be provisioned from within another managed profile.
                return STATUS_CANNOT_ADD_MANAGED_PROFILE;
            }

            // If there's a device owner, the restriction on adding a managed profile must be set.
            if (hasDeviceOwner && !addingProfileRestricted) {
                Slogf.wtf(LOG_TAG, "Has a device owner but no restriction on adding a profile.");
            }

            // Do not allow adding a managed profile if there's a restriction.
            if (addingProfileRestricted) {
                Slogf.i(LOG_TAG, "Adding a profile is restricted: User %s Has device owner? %b",
                        callingUserHandle, hasDeviceOwner);
                return STATUS_CANNOT_ADD_MANAGED_PROFILE;
            }

            // Bail out if we are trying to provision a work profile but one already exists.
            if (!mUserManager.canAddMoreManagedProfiles(
                    callingUserId, /* allowedToRemoveOne= */ false)) {
                Slogf.i(LOG_TAG, "Cannot add more managed profiles.");
                return STATUS_CANNOT_ADD_MANAGED_PROFILE;
            }
        } finally {
            mInjector.binderRestoreCallingIdentity(ident);
        }
        return STATUS_OK;
    }

    private void checkIsDeviceOwner(CallerIdentity caller) {
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller), caller.getUid()
                + " is not device owner");
    }

    /**
     * Return device owner or profile owner set on a given user.
     */
    private @Nullable ComponentName getOwnerComponent(int userId) {
        synchronized (getLockObject()) {
            if (mOwners.getDeviceOwnerUserId() == userId) {
                return mOwners.getDeviceOwnerComponent();
            }
            if (mOwners.hasProfileOwner(userId)) {
                return mOwners.getProfileOwnerComponent(userId);
            }
        }
        return null;
    }

    private boolean hasFeatureManagedUsers() {
        try {
            return mIPackageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS, 0);
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public String getWifiMacAddress(ComponentName admin) {
        Objects.requireNonNull(admin, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwnerOfOrganizationOwnedDevice(caller));

        return mInjector.binderWithCleanCallingIdentity(() -> {
            String[] macAddresses = mInjector.getWifiManager().getFactoryMacAddresses();
            if (macAddresses == null) {
                return null;
            }
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.GET_WIFI_MAC_ADDRESS)
                    .setAdmin(caller.getComponentName())
                    .write();
            return macAddresses.length > 0 ? macAddresses[0] : null;
        });
    }

    /**
     * Returns the target sdk version number that the given packageName was built for
     * in the given user.
     */
    private int getTargetSdk(String packageName, int userId) {
        final ApplicationInfo ai;
        try {
            ai = mIPackageManager.getApplicationInfo(packageName, 0, userId);
            return ai == null ? 0 : ai.targetSdkVersion;
        } catch (RemoteException e) {
            // Shouldn't happen
            Slogf.wtf(LOG_TAG, "Error getting application info", e);
            return 0;
        }
    }

    @Override
    public boolean isManagedProfile(ComponentName admin) {
        Objects.requireNonNull(admin, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));

        return isManagedProfile(caller.getUserId());
    }

    @Override
    public void reboot(ComponentName admin) {
        Objects.requireNonNull(admin, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_REBOOT);
        mInjector.binderWithCleanCallingIdentity(() -> {
            // Make sure there are no ongoing calls on the device.
            if (mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                throw new IllegalStateException("Cannot be called with ongoing call on the device");
            }
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.REBOOT)
                    .setAdmin(admin)
                    .write();
            mInjector.powerManagerReboot(PowerManager.REBOOT_REQUESTED_BY_DEVICE_OWNER);
        });
    }

    @Override
    public void setShortSupportMessage(@NonNull ComponentName who, CharSequence message) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        synchronized (getLockObject()) {
            ActiveAdmin admin = getActiveAdminForUidLocked(who, caller.getUid());
            if (!TextUtils.equals(admin.shortSupportMessage, message)) {
                admin.shortSupportMessage = message;
                saveSettingsLocked(caller.getUserId());
            }
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_SHORT_SUPPORT_MESSAGE)
                .setAdmin(who)
                .write();
    }

    @Override
    public CharSequence getShortSupportMessage(@NonNull ComponentName who) {
        if (!mHasFeature) {
            return null;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        synchronized (getLockObject()) {
            ActiveAdmin admin = getActiveAdminForUidLocked(who, caller.getUid());
            return admin.shortSupportMessage;
        }
    }

    @Override
    public void setLongSupportMessage(@NonNull ComponentName who, CharSequence message) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        synchronized (getLockObject()) {
            ActiveAdmin admin = getActiveAdminForUidLocked(who, caller.getUid());
            if (!TextUtils.equals(admin.longSupportMessage, message)) {
                admin.longSupportMessage = message;
                saveSettingsLocked(caller.getUserId());
            }
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_LONG_SUPPORT_MESSAGE)
                .setAdmin(who)
                .write();
    }

    @Override
    public CharSequence getLongSupportMessage(@NonNull ComponentName who) {
        if (!mHasFeature) {
            return null;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        synchronized (getLockObject()) {
            ActiveAdmin admin = getActiveAdminForUidLocked(who, caller.getUid());
            return admin.longSupportMessage;
        }
    }

    @Override
    public CharSequence getShortSupportMessageForUser(@NonNull ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return null;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        Preconditions.checkCallAuthorization(isSystemUid(getCallerIdentity()),
                String.format(NOT_SYSTEM_CALLER_MSG, "query support message for user"));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
            if (admin != null) {
                return admin.shortSupportMessage;
            }
        }
        return null;
    }

    @Override
    public CharSequence getLongSupportMessageForUser(@NonNull ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return null;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        Preconditions.checkCallAuthorization(isSystemUid(getCallerIdentity()),
                String.format(NOT_SYSTEM_CALLER_MSG, "query support message for user"));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
            if (admin != null) {
                return admin.longSupportMessage;
            }
        }
        return null;
    }

    @Override
    public void setOrganizationColor(@NonNull ComponentName who, int color) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallingUser(isManagedProfile(caller.getUserId()));
        Preconditions.checkCallAuthorization(isProfileOwner(caller));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            admin.organizationColor = color;
            saveSettingsLocked(caller.getUserId());
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_ORGANIZATION_COLOR)
                .setAdmin(caller.getComponentName())
                .write();
    }

    @Override
    public void setOrganizationColorForUser(int color, int userId) {
        if (!mHasFeature) {
            return;
        }
        Preconditions.checkArgumentNonnegative(userId, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userId));
        Preconditions.checkCallAuthorization(canManageUsers(caller));
        Preconditions.checkCallAuthorization(isManagedProfile(userId), "You can not "
                + "set organization color outside a managed profile, userId = %d", userId);

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerAdminLocked(userId);
            admin.organizationColor = color;
            saveSettingsLocked(userId);
        }
    }

    @Override
    public int getOrganizationColor(@NonNull ComponentName who) {
        if (!mHasFeature) {
            return ActiveAdmin.DEF_ORGANIZATION_COLOR;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallingUser(isManagedProfile(caller.getUserId()));
        Preconditions.checkCallAuthorization(isProfileOwner(caller));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            return admin.organizationColor;
        }
    }

    @Override
    public int getOrganizationColorForUser(int userHandle) {
        if (!mHasFeature) {
            return ActiveAdmin.DEF_ORGANIZATION_COLOR;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        Preconditions.checkCallAuthorization(isManagedProfile(userHandle), "You can "
                + "not get organization color outside a managed profile, userId = %d", userHandle);

        synchronized (getLockObject()) {
            ActiveAdmin profileOwner = getProfileOwnerAdminLocked(userHandle);
            return (profileOwner != null)
                    ? profileOwner.organizationColor
                    : ActiveAdmin.DEF_ORGANIZATION_COLOR;
        }
    }

    @Override
    public void setOrganizationName(@NonNull ComponentName who, CharSequence text) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDeviceOwner(caller) || isProfileOwner(caller));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            if (!TextUtils.equals(admin.organizationName, text)) {
                admin.organizationName = (text == null || text.length() == 0)
                        ? null : text.toString();
                saveSettingsLocked(caller.getUserId());
            }
        }
    }

    @Override
    public CharSequence getOrganizationName(@NonNull ComponentName who) {
        if (!mHasFeature) {
            return null;
        }
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallingUser(isManagedProfile(caller.getUserId()));
        Preconditions.checkCallAuthorization(isDeviceOwner(caller) || isProfileOwner(caller));

        synchronized (getLockObject()) {
            ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            return admin.organizationName;
        }
    }

    /**
     * This API is cached: invalidate with invalidateBinderCaches().
     */
    @Override
    public CharSequence getDeviceOwnerOrganizationName() {
        if (!mHasFeature) {
            return null;
        }
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                || canManageUsers(caller) || isFinancedDeviceOwner(caller));
        synchronized (getLockObject()) {
            final ActiveAdmin deviceOwnerAdmin = getDeviceOwnerAdminLocked();
            return deviceOwnerAdmin == null ? null : deviceOwnerAdmin.organizationName;
        }
    }

    /**
     * This API is cached: invalidate with invalidateBinderCaches().
     */
    @Override
    public CharSequence getOrganizationNameForUser(int userHandle) {
        if (!mHasFeature) {
            return null;
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasFullCrossUsersPermission(caller, userHandle));
        Preconditions.checkCallAuthorization(canManageUsers(caller));
        Preconditions.checkCallAuthorization(isManagedProfile(userHandle),
                "You can not get organization name outside a managed profile, userId = %d",
                userHandle);

        synchronized (getLockObject()) {
            ActiveAdmin profileOwner = getProfileOwnerAdminLocked(userHandle);
            return (profileOwner != null)
                    ? profileOwner.organizationName
                    : null;
        }
    }

    @Override
    public List<String> setMeteredDataDisabledPackages(ComponentName who, List<String> packageNames) {
        Objects.requireNonNull(who);
        Objects.requireNonNull(packageNames);
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller),
                "Admin %s does not own the profile", caller.getComponentName());

        if (!mHasFeature) {
            return packageNames;
        }
        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            return mInjector.binderWithCleanCallingIdentity(() -> {
                final List<String> excludedPkgs = removeInvalidPkgsForMeteredDataRestriction(
                        caller.getUserId(), packageNames);
                admin.meteredDisabledPackages = packageNames;
                pushMeteredDisabledPackages(caller.getUserId());
                saveSettingsLocked(caller.getUserId());
                return excludedPkgs;
            });
        }
    }

    private List<String> removeInvalidPkgsForMeteredDataRestriction(
            int userId, List<String> pkgNames) {
        final Set<String> activeAdmins = getActiveAdminPackagesLocked(userId);
        final List<String> excludedPkgs = new ArrayList<>();
        for (int i = pkgNames.size() - 1; i >= 0; --i) {
            final String pkgName = pkgNames.get(i);
            // If the package is an active admin, don't restrict it.
            if (activeAdmins.contains(pkgName)) {
                excludedPkgs.add(pkgName);
                continue;
            }
            // If the package doesn't exist, don't restrict it.
            try {
                if (!mInjector.getIPackageManager().isPackageAvailable(pkgName, userId)) {
                    excludedPkgs.add(pkgName);
                }
            } catch (RemoteException e) {
                // Should not happen
            }
        }
        pkgNames.removeAll(excludedPkgs);
        return excludedPkgs;
    }

    @Override
    public List<String> getMeteredDataDisabledPackages(ComponentName who) {
        Objects.requireNonNull(who);

        if (!mHasFeature) {
            return new ArrayList<>();
        }
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller),
                "Admin %s does not own the profile", caller.getComponentName());

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            return admin.meteredDisabledPackages == null
                    ? new ArrayList<>() : admin.meteredDisabledPackages;
        }
    }

    @Override
    public boolean isMeteredDataDisabledPackageForUser(ComponentName who,
            String packageName, int userId) {
        Objects.requireNonNull(who);

        if (!mHasFeature) {
            return false;
        }
        Preconditions.checkCallAuthorization(isSystemUid(getCallerIdentity()),
                String.format(NOT_SYSTEM_CALLER_MSG, "query restricted pkgs for a specific user"));

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userId);
            if (admin != null && admin.meteredDisabledPackages != null) {
                return admin.meteredDisabledPackages.contains(packageName);
            }
        }
        return false;
    }

    @Override
    public void setProfileOwnerOnOrganizationOwnedDevice(ComponentName who, int userId,
            boolean isProfileOwnerOnOrganizationOwnedDevice) {
        if (!mHasFeature) {
            return;
        }
        // As the caller is the system, it must specify the component name of the profile owner
        // as a safety check.
        Objects.requireNonNull(who);

        final CallerIdentity caller = getCallerIdentity();
        // Only adb or system apps with the right permission can mark a profile owner on
        // organization-owned device.
        if (!(isAdb(caller) || hasCallingPermission(permission.MARK_DEVICE_ORGANIZATION_OWNED)
                || hasCallingPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS))) {
            throw new SecurityException(
                    "Only the system can mark a profile owner of organization-owned device.");
        }
        // Only a test admin can be unmarked as a profile owner on an organization-owned device.
        synchronized (getLockObject()) {
            if (!isProfileOwnerOnOrganizationOwnedDevice && !isAdminTestOnlyLocked(who, userId)) {
                throw new SecurityException("Only a test admin can be unmarked as a "
                        + "profile owner of organization-owned device.");
            }
        }

        if (isAdb(caller)) {
            if (hasIncompatibleAccountsOrNonAdbNoLock(caller, userId, who)) {
                throw new SecurityException(
                        "Can only be called from ADB if the device has no accounts.");
            }
        } else {
            if (hasUserSetupCompleted(UserHandle.USER_SYSTEM)) {
                throw new IllegalStateException(
                        "Cannot mark profile owner as managing an organization-owned device after"
                                + " set-up");
            }
        }

        // Grant access under lock.
        synchronized (getLockObject()) {
            setProfileOwnerOnOrganizationOwnedDeviceUncheckedLocked(who, userId,
                    isProfileOwnerOnOrganizationOwnedDevice);
        }
    }

    @GuardedBy("getLockObject()")
    private void setProfileOwnerOnOrganizationOwnedDeviceUncheckedLocked(
            ComponentName who, int userId, boolean isProfileOwnerOnOrganizationOwnedDevice) {
        // Make sure that the user has a profile owner and that the specified
        // component is the profile owner of that user.
        if (!isProfileOwner(who, userId)) {
            throw new IllegalArgumentException(String.format(
                    "Component %s is not a Profile Owner of user %d",
                    who.flattenToString(), userId));
        }

        Slogf.i(LOG_TAG, "%s %s as profile owner on organization-owned device for user %d",
                isProfileOwnerOnOrganizationOwnedDevice ? "Marking" : "Unmarking",
                who.flattenToString(), userId);

        // First, set restriction on removing the profile.
        mInjector.binderWithCleanCallingIdentity(() -> {
            // Clear restriction as user.
            final UserHandle parentUser = mUserManager.getProfileParent(UserHandle.of(userId));
            if (parentUser == null) {
                throw new IllegalStateException(String.format("User %d is not a profile", userId));
            }

            mUserManager.setUserRestriction(UserManager.DISALLOW_REMOVE_MANAGED_PROFILE,
                    isProfileOwnerOnOrganizationOwnedDevice,
                    parentUser);
            mUserManager.setUserRestriction(UserManager.DISALLOW_ADD_USER,
                    isProfileOwnerOnOrganizationOwnedDevice,
                    parentUser);
        });

        // setProfileOwnerOfOrganizationOwnedDevice will trigger writing of the profile owner
        // data, no need to do it manually.
        mOwners.setProfileOwnerOfOrganizationOwnedDevice(userId,
                isProfileOwnerOnOrganizationOwnedDevice);
    }

    private void pushMeteredDisabledPackages(int userId) {
        wtfIfInLock();
        mInjector.getNetworkPolicyManagerInternal().setMeteredRestrictedPackages(
                getMeteredDisabledPackages(userId), userId);
    }

    private Set<String> getMeteredDisabledPackages(int userId) {
        synchronized (getLockObject()) {
            final Set<String> restrictedPkgs = new ArraySet<>();
            final ActiveAdmin admin = getDeviceOrProfileOwnerAdminLocked(userId);
            if (admin != null && admin.meteredDisabledPackages != null) {
                restrictedPkgs.addAll(admin.meteredDisabledPackages);
            }

            return restrictedPkgs;
        }
    }

    @Override
    public void setAffiliationIds(ComponentName admin, List<String> ids) {
        if (!mHasFeature) {
            return;
        }
        if (ids == null) {
            throw new IllegalArgumentException("ids must not be null");
        }
        for (String id : ids) {
            if (TextUtils.isEmpty(id)) {
                throw new IllegalArgumentException("ids must not contain empty string");
            }
        }

        final Set<String> affiliationIds = new ArraySet<>(ids);
        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));
        final int callingUserId = caller.getUserId();

        synchronized (getLockObject()) {
            getUserData(callingUserId).mAffiliationIds = affiliationIds;
            saveSettingsLocked(callingUserId);
            if (callingUserId != UserHandle.USER_SYSTEM && isDeviceOwner(admin, callingUserId)) {
                // Affiliation ids specified by the device owner are additionally stored in
                // UserHandle.USER_SYSTEM's DevicePolicyData.
                getUserData(UserHandle.USER_SYSTEM).mAffiliationIds = affiliationIds;
                saveSettingsLocked(UserHandle.USER_SYSTEM);
            }

            // Affiliation status for any user, not just the calling user, might have changed.
            // The device owner user will still be affiliated after changing its affiliation ids,
            // but as a result of that other users might become affiliated or un-affiliated.
            maybePauseDeviceWideLoggingLocked();
            maybeResumeDeviceWideLoggingLocked();
            maybeClearLockTaskPolicyLocked();
            updateAdminCanGrantSensorsPermissionCache(callingUserId);
        }
    }

    @Override
    public List<String> getAffiliationIds(ComponentName admin) {
        if (!mHasFeature) {
            return Collections.emptyList();
        }

        Objects.requireNonNull(admin);
        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));

        synchronized (getLockObject()) {
            return new ArrayList<String>(getUserData(caller.getUserId()).mAffiliationIds);
        }
    }

    @Override
    public boolean isCallingUserAffiliated() {
        if (!mHasFeature) {
            return false;
        }

        synchronized (getLockObject()) {
            return isUserAffiliatedWithDeviceLocked(mInjector.userHandleGetCallingUserId());
        }
    }

    @Override
    public boolean isAffiliatedUser(@UserIdInt int userId) {
        if (!mHasFeature) {
            return false;
        }
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(hasCrossUsersPermission(caller, userId));

        return isUserAffiliatedWithDevice(userId);
    }

    private boolean isUserAffiliatedWithDevice(@UserIdInt int userId) {
        synchronized (getLockObject()) {
            return isUserAffiliatedWithDeviceLocked(userId);
        }
    }

    private boolean isUserAffiliatedWithDeviceLocked(@UserIdInt int userId) {
        if (!mOwners.hasDeviceOwner()) {
            return false;
        }
        if (userId == UserHandle.USER_SYSTEM) {
            // The system user is always affiliated in a DO device,
            // even if in headless system user mode.
            return true;
        }
        if (userId == mOwners.getDeviceOwnerUserId()) {
            // The user that the DO is installed on is always affiliated with the device.
            return true;
        }

        final ComponentName profileOwner = getProfileOwnerAsUser(userId);
        if (profileOwner == null) {
            return false;
        }

        final Set<String> userAffiliationIds = getUserData(userId).mAffiliationIds;
        final Set<String> deviceAffiliationIds =
                getUserData(UserHandle.USER_SYSTEM).mAffiliationIds;
        for (String id : userAffiliationIds) {
            if (deviceAffiliationIds.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean areAllUsersAffiliatedWithDeviceLocked() {
        return mInjector.binderWithCleanCallingIdentity(() -> {
            final List<UserInfo> userInfos = mUserManager.getAliveUsers();
            for (int i = 0; i < userInfos.size(); i++) {
                int userId = userInfos.get(i).id;
                if (!isUserAffiliatedWithDeviceLocked(userId)) {
                    Slogf.d(LOG_TAG, "User id " + userId + " not affiliated.");
                    return false;
                }
            }
            return true;
        });
    }

    private @UserIdInt int getSecurityLoggingEnabledUser() {
        synchronized (getLockObject()) {
            if (mOwners.hasDeviceOwner()) {
                return UserHandle.USER_ALL;
            }
        }
        return getOrganizationOwnedProfileUserId();
    }

    @Override
    public void setSecurityLoggingEnabled(ComponentName admin, String packageName,
            boolean enabled) {
        if (!mHasFeature) {
            return;
        }
        final CallerIdentity caller = getCallerIdentity(admin, packageName);

        synchronized (getLockObject()) {
            if (admin != null) {
                Preconditions.checkCallAuthorization(
                        isProfileOwnerOfOrganizationOwnedDevice(caller)
                        || isDefaultDeviceOwner(caller));
            } else {
                // A delegate app passes a null admin component, which is expected
                Preconditions.checkCallAuthorization(
                        isCallerDelegate(caller, DELEGATION_SECURITY_LOGGING));
            }

            if (enabled == mInjector.securityLogGetLoggingEnabledProperty()) {
                return;
            }
            mInjector.securityLogSetLoggingEnabledProperty(enabled);
            if (enabled) {
                mSecurityLogMonitor.start(getSecurityLoggingEnabledUser());
                maybePauseDeviceWideLoggingLocked();
            } else {
                mSecurityLogMonitor.stop();
            }
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_SECURITY_LOGGING_ENABLED)
                .setAdmin(admin)
                .setBoolean(enabled)
                .write();
    }

    @Override
    public boolean isSecurityLoggingEnabled(ComponentName admin, String packageName) {
        if (!mHasFeature) {
            return false;
        }

        synchronized (getLockObject()) {
            if (!isSystemUid(getCallerIdentity())) {
                final CallerIdentity caller = getCallerIdentity(admin, packageName);
                if (admin != null) {
                    Preconditions.checkCallAuthorization(
                            isProfileOwnerOfOrganizationOwnedDevice(caller)
                            || isDefaultDeviceOwner(caller));
                } else {
                    // A delegate app passes a null admin component, which is expected
                    Preconditions.checkCallAuthorization(
                            isCallerDelegate(caller, DELEGATION_SECURITY_LOGGING));
                }
            }
            return mInjector.securityLogGetLoggingEnabledProperty();
        }
    }

    private void recordSecurityLogRetrievalTime() {
        synchronized (getLockObject()) {
            final long currentTime = System.currentTimeMillis();
            DevicePolicyData policyData = getUserData(UserHandle.USER_SYSTEM);
            if (currentTime > policyData.mLastSecurityLogRetrievalTime) {
                policyData.mLastSecurityLogRetrievalTime = currentTime;
                saveSettingsLocked(UserHandle.USER_SYSTEM);
            }
        }
    }

    @Override
    public ParceledListSlice<SecurityEvent> retrievePreRebootSecurityLogs(ComponentName admin,
            String packageName) {
        if (!mHasFeature) {
            return null;
        }

        final CallerIdentity caller = getCallerIdentity(admin, packageName);
        if (admin != null) {
            Preconditions.checkCallAuthorization(
                    isProfileOwnerOfOrganizationOwnedDevice(caller)
                    || isDefaultDeviceOwner(caller));
        } else {
            // A delegate app passes a null admin component, which is expected
            Preconditions.checkCallAuthorization(
                    isCallerDelegate(caller, DELEGATION_SECURITY_LOGGING));
        }

        Preconditions.checkCallAuthorization(isOrganizationOwnedDeviceWithManagedProfile()
                || areAllUsersAffiliatedWithDeviceLocked());

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.RETRIEVE_PRE_REBOOT_SECURITY_LOGS)
                .setAdmin(caller.getComponentName())
                .write();

        if (!mContext.getResources().getBoolean(R.bool.config_supportPreRebootSecurityLogs)
                || !mInjector.securityLogGetLoggingEnabledProperty()) {
            return null;
        }

        recordSecurityLogRetrievalTime();
        ArrayList<SecurityEvent> output = new ArrayList<SecurityEvent>();
        try {
            SecurityLog.readPreviousEvents(output);
            int enabledUser = getSecurityLoggingEnabledUser();
            if (enabledUser != UserHandle.USER_ALL) {
                SecurityLog.redactEvents(output, enabledUser);
            }
            return new ParceledListSlice<SecurityEvent>(output);
        } catch (IOException e) {
            Slogf.w(LOG_TAG, "Fail to read previous events" , e);
            return new ParceledListSlice<SecurityEvent>(Collections.<SecurityEvent>emptyList());
        }
    }

    @Override
    public ParceledListSlice<SecurityEvent> retrieveSecurityLogs(ComponentName admin,
            String packageName) {
        if (!mHasFeature) {
            return null;
        }

        final CallerIdentity caller = getCallerIdentity(admin, packageName);
        if (admin != null) {
            Preconditions.checkCallAuthorization(
                    isProfileOwnerOfOrganizationOwnedDevice(caller)
                    || isDefaultDeviceOwner(caller));
        } else {
            // A delegate app passes a null admin component, which is expected
            Preconditions.checkCallAuthorization(
                    isCallerDelegate(caller, DELEGATION_SECURITY_LOGGING));
        }
        Preconditions.checkCallAuthorization(isOrganizationOwnedDeviceWithManagedProfile()
                || areAllUsersAffiliatedWithDeviceLocked());

        if (!mInjector.securityLogGetLoggingEnabledProperty()) {
            return null;
        }

        recordSecurityLogRetrievalTime();

        List<SecurityEvent> logs = mSecurityLogMonitor.retrieveLogs();
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.RETRIEVE_SECURITY_LOGS)
                .setAdmin(caller.getComponentName())
                .write();
        return logs != null ? new ParceledListSlice<SecurityEvent>(logs) : null;
    }

    @Override
    public long forceSecurityLogs() {
        Preconditions.checkCallAuthorization(isAdb(getCallerIdentity())
                        || hasCallingOrSelfPermission(permission.FORCE_DEVICE_POLICY_MANAGER_LOGS),
                "Caller must be shell or hold FORCE_DEVICE_POLICY_MANAGER_LOGS to call "
                        + "forceSecurityLogs");
        if (!mInjector.securityLogGetLoggingEnabledProperty()) {
            throw new IllegalStateException("logging is not available");
        }
        return mSecurityLogMonitor.forceLogs();
    }

    @Override
    public boolean isUninstallInQueue(final String packageName) {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_DEVICE_ADMINS));

        UserPackage packageUserPair = UserPackage.of(caller.getUserId(), packageName);
        synchronized (getLockObject()) {
            return mPackagesToRemove.contains(packageUserPair);
        }
    }

    @Override
    public void uninstallPackageWithActiveAdmins(final String packageName) {
        Preconditions.checkArgument(!TextUtils.isEmpty(packageName));

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_DEVICE_ADMINS));

        final int userId = caller.getUserId();
        enforceUserUnlocked(userId);

        final ComponentName profileOwner = getProfileOwnerAsUser(userId);
        if (profileOwner != null && packageName.equals(profileOwner.getPackageName())) {
            throw new IllegalArgumentException("Cannot uninstall a package with a profile owner");
        }

        final ComponentName deviceOwner = getDeviceOwnerComponent(/* callingUserOnly= */ false);
        if (getDeviceOwnerUserId() == userId && deviceOwner != null
                && packageName.equals(deviceOwner.getPackageName())) {
            throw new IllegalArgumentException("Cannot uninstall a package with a device owner");
        }

        final UserPackage packageUserPair = UserPackage.of(userId, packageName);
        synchronized (getLockObject()) {
            mPackagesToRemove.add(packageUserPair);
        }

        // All active admins on the user.
        final List<ComponentName> allActiveAdmins = getActiveAdmins(userId);

        // Active admins in the target package.
        final List<ComponentName> packageActiveAdmins = new ArrayList<>();
        if (allActiveAdmins != null) {
            for (ComponentName activeAdmin : allActiveAdmins) {
                if (packageName.equals(activeAdmin.getPackageName())) {
                    packageActiveAdmins.add(activeAdmin);
                    removeActiveAdmin(activeAdmin, userId);
                }
            }
        }
        if (packageActiveAdmins.size() == 0) {
            startUninstallIntent(packageName, userId);
        } else {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    for (ComponentName activeAdmin : packageActiveAdmins) {
                        removeAdminArtifacts(activeAdmin, userId);
                    }
                    startUninstallIntent(packageName, userId);
                }
            }, DEVICE_ADMIN_DEACTIVATE_TIMEOUT); // Start uninstall after timeout anyway.
        }
    }

    @Override
    public boolean isDeviceProvisioned() {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(canManageUsers(caller));

        synchronized (getLockObject()) {
            return getUserDataUnchecked(UserHandle.USER_SYSTEM).mUserSetupComplete;
        }
    }

    private boolean isCurrentUserDemo() {
        if (UserManager.isDeviceInDemoMode(mContext)) {
            final int userId = mInjector.userHandleGetCallingUserId();
            return mInjector.binderWithCleanCallingIdentity(
                    () -> mUserManager.getUserInfo(userId).isDemo());
        }
        return false;
    }

    private void removePackageIfRequired(final String packageName, final int userId) {
        if (!packageHasActiveAdmins(packageName, userId)) {
            // Will not do anything if uninstall was not requested or was already started.
            startUninstallIntent(packageName, userId);
        }
    }

    private void startUninstallIntent(final String packageName, final int userId) {
        final UserPackage packageUserPair = UserPackage.of(userId, packageName);
        synchronized (getLockObject()) {
            if (!mPackagesToRemove.contains(packageUserPair)) {
                // Do nothing if uninstall was not requested or was already started.
                return;
            }
            mPackagesToRemove.remove(packageUserPair);
        }
        if (!isPackageInstalledForUser(packageName, userId)) {
            // Package does not exist. Nothing to do.
            return;
        }

        try { // force stop the package before uninstalling
            mInjector.getIActivityManager().forceStopPackage(packageName, userId);
        } catch (RemoteException re) {
            Slogf.e(LOG_TAG, "Failure talking to ActivityManager while force stopping package");
        }
        final Uri packageURI = Uri.parse("package:" + packageName);
        final Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageURI);
        uninstallIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivityAsUser(uninstallIntent, UserHandle.of(userId));
    }

    /**
     * Removes the admin from the policy. Ideally called after the admin's
     * {@link DeviceAdminReceiver#onDisabled(Context, Intent)} has been successfully completed.
     *
     * @param adminReceiver The admin to remove
     * @param userHandle The user for which this admin has to be removed.
     */
    private void removeAdminArtifacts(final ComponentName adminReceiver, final int userHandle) {
        synchronized (getLockObject()) {
            final ActiveAdmin admin = getActiveAdminUncheckedLocked(adminReceiver, userHandle);
            if (admin == null) {
                return;
            }
            final DevicePolicyData policy = getUserData(userHandle);
            final boolean doProxyCleanup = admin.info.usesPolicy(
                    DeviceAdminInfo.USES_POLICY_SETS_GLOBAL_PROXY);
            policy.mAdminList.remove(admin);
            policy.mAdminMap.remove(adminReceiver);
            policy.validatePasswordOwner();
            if (doProxyCleanup) {
                resetGlobalProxyLocked(policy);
            }
            pushActiveAdminPackagesLocked(userHandle);
            saveSettingsLocked(userHandle);
            updateMaximumTimeToLockLocked(userHandle);
            policy.mRemovingAdmins.remove(adminReceiver);
            pushScreenCapturePolicy(userHandle);

            Slogf.i(LOG_TAG, "Device admin " + adminReceiver + " removed from user " + userHandle);
        }
        pushMeteredDisabledPackages(userHandle);
        // The removed admin might have disabled camera, so update user
        // restrictions.
        pushUserRestrictions(userHandle);
    }

    @Override
    public void setDeviceProvisioningConfigApplied() {
        Preconditions.checkCallAuthorization(canManageUsers(getCallerIdentity()));

        synchronized (getLockObject()) {
            DevicePolicyData policy = getUserData(UserHandle.USER_SYSTEM);
            policy.mDeviceProvisioningConfigApplied = true;
            saveSettingsLocked(UserHandle.USER_SYSTEM);
        }
    }

    @Override
    public boolean isDeviceProvisioningConfigApplied() {
        Preconditions.checkCallAuthorization(canManageUsers(getCallerIdentity()));

        synchronized (getLockObject()) {
            final DevicePolicyData policy = getUserData(UserHandle.USER_SYSTEM);
            return policy.mDeviceProvisioningConfigApplied;
        }
    }

    /**
     * Force update internal persistent state from Settings.Secure.USER_SETUP_COMPLETE.
     *
     * It's added for testing only. Please use this API carefully if it's used by other system app
     * and bare in mind Settings.Secure.USER_SETUP_COMPLETE can be modified by user and other system
     * apps.
     */
    @Override
    public void forceUpdateUserSetupComplete(@UserIdInt int userId) {
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        boolean isUserCompleted = mInjector.settingsSecureGetIntForUser(
                Settings.Secure.USER_SETUP_COMPLETE, 0, userId) != 0;
        DevicePolicyData policy = getUserData(userId);
        policy.mUserSetupComplete = isUserCompleted;
        mStateCache.setDeviceProvisioned(isUserCompleted);
        synchronized (getLockObject()) {
            saveSettingsLocked(userId);
        }
    }

    @Override
    public void setBackupServiceEnabled(ComponentName admin, boolean enabled) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(admin, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                || isProfileOwner(caller) || isFinancedDeviceOwner(caller));

        toggleBackupServiceActive(caller.getUserId(), enabled);
    }

    @Override
    public boolean isBackupServiceEnabled(ComponentName admin) {
        if (!mHasFeature) {
            return true;
        }
        Objects.requireNonNull(admin, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                || isProfileOwner(caller) || isFinancedDeviceOwner(caller));

        return mInjector.binderWithCleanCallingIdentity(() -> {
            synchronized (getLockObject()) {
                try {
                    IBackupManager ibm = mInjector.getIBackupManager();
                    return ibm != null && ibm.isBackupServiceActive(caller.getUserId());
                } catch (RemoteException e) {
                    throw new IllegalStateException("Failed requesting backup service state.", e);
                }
            }
        });
    }

    @Override
    public boolean bindDeviceAdminServiceAsUser(
            @NonNull ComponentName admin, @NonNull IApplicationThread caller,
            @Nullable IBinder activtiyToken, @NonNull Intent serviceIntent,
            @NonNull IServiceConnection connection, int flags, @UserIdInt int targetUserId) {
        if (!mHasFeature) {
            return false;
        }
        Objects.requireNonNull(admin);
        Objects.requireNonNull(caller);
        Objects.requireNonNull(serviceIntent);
        Preconditions.checkArgument(
                serviceIntent.getComponent() != null || serviceIntent.getPackage() != null,
                "Service intent must be explicit (with a package name or component): "
                        + serviceIntent);
        Objects.requireNonNull(connection);
        Preconditions.checkArgument(mInjector.userHandleGetCallingUserId() != targetUserId,
                "target user id must be different from the calling user id");

        if (!getBindDeviceAdminTargetUsers(admin).contains(UserHandle.of(targetUserId))) {
            throw new SecurityException("Not allowed to bind to target user id");
        }

        final String targetPackage;
        synchronized (getLockObject()) {
            targetPackage = getOwnerPackageNameForUserLocked(targetUserId);
        }

        final long callingIdentity = mInjector.binderClearCallingIdentity();
        try {
            // Validate and sanitize the incoming service intent.
            final Intent sanitizedIntent =
                    createCrossUserServiceIntent(serviceIntent, targetPackage, targetUserId);
            if (sanitizedIntent == null) {
                // Fail, cannot lookup the target service.
                return false;
            }
            // Ask ActivityManager to bind it. Notice that we are binding the service with the
            // caller app instead of DevicePolicyManagerService.
            return mInjector.getIActivityManager().bindService(
                    caller, activtiyToken, serviceIntent,
                    serviceIntent.resolveTypeIfNeeded(mContext.getContentResolver()),
                    connection, flags, mContext.getOpPackageName(),
                    targetUserId) != 0;
        } catch (RemoteException ex) {
            // Same process, should not happen.
        } finally {
            mInjector.binderRestoreCallingIdentity(callingIdentity);
        }

        // Failed to bind.
        return false;
    }

    @Override
    public @NonNull List<UserHandle> getBindDeviceAdminTargetUsers(@NonNull ComponentName admin) {
        if (!mHasFeature) {
            return Collections.emptyList();
        }
        Objects.requireNonNull(admin);
        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));

        synchronized (getLockObject()) {
            final int callingUserId = caller.getUserId();
            return mInjector.binderWithCleanCallingIdentity(() -> {
                ArrayList<UserHandle> targetUsers = new ArrayList<>();
                if (!isDeviceOwner(admin, callingUserId)) {
                    // Profile owners can only bind to the device owner.
                    if (canUserBindToDeviceOwnerLocked(callingUserId)) {
                        targetUsers.add(UserHandle.of(mOwners.getDeviceOwnerUserId()));
                    }
                } else {
                    // Caller is the device owner: Look for profile owners that it can bind to.
                    final List<UserInfo> userInfos = mUserManager.getAliveUsers();
                    for (int i = 0; i < userInfos.size(); i++) {
                        final int userId = userInfos.get(i).id;
                        if (userId != callingUserId && canUserBindToDeviceOwnerLocked(userId)) {
                            targetUsers.add(UserHandle.of(userId));
                        }
                    }
                }

                return targetUsers;
            });
        }
    }

    private boolean canUserBindToDeviceOwnerLocked(int userId) {
        // There has to be a device owner, under another user id.
        if (!mOwners.hasDeviceOwner() || userId == mOwners.getDeviceOwnerUserId()) {
            return false;
        }

        // The user must have a profile owner that belongs to the same package as the device owner.
        if (!mOwners.hasProfileOwner(userId) || !TextUtils.equals(
                mOwners.getDeviceOwnerPackageName(), mOwners.getProfileOwnerPackage(userId))) {
            return false;
        }

        // The user must be affiliated.
        return isUserAffiliatedWithDeviceLocked(userId);
    }

    /**
     * Return true if a given user has any accounts that'll prevent installing a device or profile
     * owner {@code owner}.
     * - If the user has no accounts, then return false.
     * - Otherwise, if the owner is unknown (== null), or is not test-only, then return true.
     * - Otherwise, if there's any account that does not have ..._ALLOWED, or does have
     *   ..._DISALLOWED, return true.
     * - Otherwise return false.
     *
     * If the caller is *not* ADB, it also returns true.  The returned value shouldn't be used
     * when the caller is not ADB.
     *
     * DO NOT CALL IT WITH THE DPMS LOCK HELD.
     */
    private boolean hasIncompatibleAccountsOrNonAdbNoLock(CallerIdentity caller,
            int userId, @Nullable ComponentName owner) {
        if (!isAdb(caller)) {
            return true;
        }
        wtfIfInLock();

        return mInjector.binderWithCleanCallingIdentity(() -> {
            AccountManager am =
                    mContext.createContextAsUser(UserHandle.of(userId), /* flags= */ 0)
                            .getSystemService(AccountManager.class);
            Account[] accounts = am.getAccounts();
            if (accounts.length == 0) {
                return false;
            }
            synchronized (getLockObject()) {
                if (owner == null || !isAdminTestOnlyLocked(owner, userId)) {
                    Slogf.w(LOG_TAG,
                            "Non test-only owner can't be installed with existing accounts.");
                    return true;
                }
            }

            boolean compatible = !hasIncompatibleAccounts(am, accounts);
            if (compatible) {
                Slogf.w(LOG_TAG, "All accounts are compatible");
            } else {
                Slogf.e(LOG_TAG, "Found incompatible accounts");
            }
            return !compatible;
        });
    }

    private boolean hasIncompatibleAccounts(AccountManager am, Account[] accounts) {
        // TODO(b/244284408): Add test
        final String[] feature_allow =
                { DevicePolicyManager.ACCOUNT_FEATURE_DEVICE_OR_PROFILE_OWNER_ALLOWED };
        final String[] feature_disallow =
                { DevicePolicyManager.ACCOUNT_FEATURE_DEVICE_OR_PROFILE_OWNER_DISALLOWED };

        for (Account account : accounts) {
            if (hasAccountFeatures(am, account, feature_disallow)) {
                Slogf.e(LOG_TAG, "%s has %s", account, feature_disallow[0]);
                return true;
            }
            if (!hasAccountFeatures(am, account, feature_allow)) {
                Slogf.e(LOG_TAG, "%s doesn't have %s", account, feature_allow[0]);
                return true;
            }
        }

        return false;
    }

    private boolean hasAccountFeatures(AccountManager am, Account account, String[] features) {
        try {
            return am.hasFeatures(account, features, null, null).getResult();
        } catch (Exception e) {
            Slogf.w(LOG_TAG, "Failed to get account feature", e);
            return false;
        }
    }

    private boolean isAdb(CallerIdentity caller) {
        return isShellUid(caller) || isRootUid(caller);
    }

    @Override
    public void setNetworkLoggingEnabled(@Nullable ComponentName admin,
            @NonNull String packageName, boolean enabled) {
        if (!mHasFeature) {
            return;
        }
        final CallerIdentity caller = getCallerIdentity(admin, packageName);
        final boolean isManagedProfileOwner = isProfileOwner(caller)
                && isManagedProfile(caller.getUserId());
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                && (isDefaultDeviceOwner(caller) || isManagedProfileOwner))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_NETWORK_LOGGING)));

        synchronized (getLockObject()) {
            if (enabled == isNetworkLoggingEnabledInternalLocked()) {
                // already in the requested state
                return;
            }
            final ActiveAdmin activeAdmin = getDeviceOrProfileOwnerAdminLocked(caller.getUserId());
            activeAdmin.isNetworkLoggingEnabled = enabled;
            if (!enabled) {
                activeAdmin.numNetworkLoggingNotifications = 0;
                activeAdmin.lastNetworkLoggingNotificationTimeMs = 0;
            }
            saveSettingsLocked(caller.getUserId());
            setNetworkLoggingActiveInternal(enabled);

            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.SET_NETWORK_LOGGING_ENABLED)
                    .setAdmin(caller.getPackageName())
                    .setBoolean(/* isDelegate */ admin == null)
                    .setInt(enabled ? 1 : 0)
                    .setStrings(isManagedProfileOwner
                            ? LOG_TAG_PROFILE_OWNER : LOG_TAG_DEVICE_OWNER)
                    .write();
        }
    }

    private void setNetworkLoggingActiveInternal(boolean active) {
        mInjector.binderWithCleanCallingIdentity(() -> {
            boolean shouldSendNotification = false;
            synchronized (getLockObject()) {
                if (active) {
                    if (mNetworkLogger == null) {
                        final int affectedUserId = getNetworkLoggingAffectedUser();
                        mNetworkLogger = new NetworkLogger(this,
                                mInjector.getPackageManagerInternal(),
                                affectedUserId == UserHandle.USER_SYSTEM
                                        ? UserHandle.USER_ALL : affectedUserId);
                    }
                    if (!mNetworkLogger.startNetworkLogging()) {
                        mNetworkLogger = null;
                        Slogf.wtf(LOG_TAG, "Network logging could not be started due to the logging"
                                + " service not being available yet.");
                    }
                    maybePauseDeviceWideLoggingLocked();
                    shouldSendNotification = shouldSendNetworkLoggingNotificationLocked();
                } else {
                    if (mNetworkLogger != null && !mNetworkLogger.stopNetworkLogging()) {
                        Slogf.wtf(LOG_TAG, "Network logging could not be stopped due to the logging"
                                + " service not being available yet.");
                    }
                    mNetworkLogger = null;
                }
            }
            if (active) {
                if (shouldSendNotification) {
                    mHandler.post(() -> handleSendNetworkLoggingNotification());
                }
            } else {
                mHandler.post(() -> handleCancelNetworkLoggingNotification());
            }
        });
    }

    private @UserIdInt int getNetworkLoggingAffectedUser() {
        synchronized (getLockObject()) {
            if (mOwners.hasDeviceOwner()) {
                return mOwners.getDeviceOwnerUserId();
            } else {
                return mInjector.binderWithCleanCallingIdentity(
                        () -> getManagedUserId());
            }
        }
    }

    private ActiveAdmin getNetworkLoggingControllingAdminLocked() {
        int affectedUserId = getNetworkLoggingAffectedUser();
        if (affectedUserId < 0) {
            return null;
        }
        return getDeviceOrProfileOwnerAdminLocked(affectedUserId);
    }

    @Override
    public long forceNetworkLogs() {
        Preconditions.checkCallAuthorization(isAdb(getCallerIdentity())
                || hasCallingOrSelfPermission(permission.FORCE_DEVICE_POLICY_MANAGER_LOGS),
                "Caller must be shell or hold FORCE_DEVICE_POLICY_MANAGER_LOGS to call "
                        + "forceNetworkLogs");
        synchronized (getLockObject()) {
            if (!isNetworkLoggingEnabledInternalLocked()) {
                throw new IllegalStateException("logging is not available");
            }
            if (mNetworkLogger != null) {
                return mInjector.binderWithCleanCallingIdentity(
                        () -> mNetworkLogger.forceBatchFinalization());
            }
            return 0;
        }
    }

    /** Pauses security and network logging if there are unaffiliated users on the device */
    @GuardedBy("getLockObject()")
    private void maybePauseDeviceWideLoggingLocked() {
        if (!areAllUsersAffiliatedWithDeviceLocked()) {
            if (mOwners.hasDeviceOwner()) {
                Slogf.i(LOG_TAG, "There are unaffiliated users, network logging will be "
                        + "paused if enabled.");
                if (mNetworkLogger != null) {
                    mNetworkLogger.pause();
                }
            }
            if (!isOrganizationOwnedDeviceWithManagedProfile()) {
                Slogf.i(LOG_TAG, "Not org-owned managed profile device, security logging will be "
                        + "paused if enabled.");
                mSecurityLogMonitor.pause();
            }
        }
    }

    /** Resumes security and network logging (if they are enabled) if all users are affiliated */
    @GuardedBy("getLockObject()")
    private void maybeResumeDeviceWideLoggingLocked() {
        boolean allUsersAffiliated = areAllUsersAffiliatedWithDeviceLocked();
        boolean orgOwnedProfileDevice = isOrganizationOwnedDeviceWithManagedProfile();
        mInjector.binderWithCleanCallingIdentity(() -> {
            if (allUsersAffiliated || orgOwnedProfileDevice) {
                mSecurityLogMonitor.resume();
            }
            // If there is no device owner, then per-user network logging may be enabled for the
            // managed profile. In which case, all users do not need to be affiliated.
            if (allUsersAffiliated || !mOwners.hasDeviceOwner()) {
                if (mNetworkLogger != null) {
                    mNetworkLogger.resume();
                }
            }
        });
    }

    /** Deletes any security and network logs that might have been collected so far */
    @GuardedBy("getLockObject()")
    private void discardDeviceWideLogsLocked() {
        mSecurityLogMonitor.discardLogs();
        if (mNetworkLogger != null) {
            mNetworkLogger.discardLogs();
        }
        // TODO: We should discard pre-boot security logs here too, as otherwise those
        // logs (which might contain data from the user just removed) will be
        // available after next boot.
    }

    /**
     * This API is cached: invalidate with invalidateBinderCaches().
     */
    @Override
    public boolean isNetworkLoggingEnabled(@Nullable ComponentName admin,
            @NonNull String packageName) {
        if (!mHasFeature) {
            return false;
        }
        final CallerIdentity caller = getCallerIdentity(admin, packageName);
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                &&  (isDefaultDeviceOwner(caller)
                || (isProfileOwner(caller) && isManagedProfile(caller.getUserId()))))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_NETWORK_LOGGING))
                || hasCallingOrSelfPermission(permission.MANAGE_USERS));

        synchronized (getLockObject()) {
            return isNetworkLoggingEnabledInternalLocked();
        }
    }

    private boolean isNetworkLoggingEnabledInternalLocked() {
        ActiveAdmin activeAdmin = getNetworkLoggingControllingAdminLocked();
        return (activeAdmin != null) && activeAdmin.isNetworkLoggingEnabled;
    }

    /*
     * A maximum of 1200 events are returned, and the total marshalled size is in the order of
     * 100kB, so returning a List instead of ParceledListSlice is acceptable.
     * Ideally this would be done with ParceledList, however it only supports homogeneous types.
     *
     * @see NetworkLoggingHandler#MAX_EVENTS_PER_BATCH
     */
    @Override
    public List<NetworkEvent> retrieveNetworkLogs(@Nullable ComponentName admin,
            @NonNull String packageName, long batchToken) {
        if (!mHasFeature) {
            return null;
        }
        final CallerIdentity caller = getCallerIdentity(admin, packageName);
        final boolean isManagedProfileOwner = isProfileOwner(caller)
                && isManagedProfile(caller.getUserId());
        Preconditions.checkCallAuthorization((caller.hasAdminComponent()
                &&  (isDefaultDeviceOwner(caller) || isManagedProfileOwner))
                || (caller.hasPackage() && isCallerDelegate(caller, DELEGATION_NETWORK_LOGGING)));
        if (mOwners.hasDeviceOwner()) {
            checkAllUsersAreAffiliatedWithDevice();
        }

        synchronized (getLockObject()) {
            if (mNetworkLogger == null || !isNetworkLoggingEnabledInternalLocked()) {
                return null;
            }
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.RETRIEVE_NETWORK_LOGS)
                    .setAdmin(caller.getPackageName())
                    .setBoolean(/* isDelegate */ admin == null)
                    .setStrings(isManagedProfileOwner
                            ? LOG_TAG_PROFILE_OWNER : LOG_TAG_DEVICE_OWNER)
                    .write();

            final long currentTime = System.currentTimeMillis();
            DevicePolicyData policyData = getUserData(caller.getUserId());
            if (currentTime > policyData.mLastNetworkLogsRetrievalTime) {
                policyData.mLastNetworkLogsRetrievalTime = currentTime;
                saveSettingsLocked(caller.getUserId());
            }
            return mNetworkLogger.retrieveLogs(batchToken);
        }
    }

    /**
     * Returns whether it's time to post another network logging notification. When returning true,
     * this method has the side-effect of updating the recorded last network logging notification
     * time to now.
     */
    private boolean shouldSendNetworkLoggingNotificationLocked() {
        ensureLocked();
        // Send a network logging notification if the admin is a device owner, not profile owner.
        final ActiveAdmin deviceOwner = getDeviceOwnerAdminLocked();
        if (deviceOwner == null || !deviceOwner.isNetworkLoggingEnabled) {
            return false;
        }
        if (deviceOwner.numNetworkLoggingNotifications
                >= ActiveAdmin.DEF_MAXIMUM_NETWORK_LOGGING_NOTIFICATIONS_SHOWN) {
            return false;
        }
        final long now = System.currentTimeMillis();
        if (now - deviceOwner.lastNetworkLoggingNotificationTimeMs < MS_PER_DAY) {
            return false;
        }
        deviceOwner.numNetworkLoggingNotifications++;
        if (deviceOwner.numNetworkLoggingNotifications
                >= ActiveAdmin.DEF_MAXIMUM_NETWORK_LOGGING_NOTIFICATIONS_SHOWN) {
            deviceOwner.lastNetworkLoggingNotificationTimeMs = 0;
        } else {
            deviceOwner.lastNetworkLoggingNotificationTimeMs = now;
        }
        saveSettingsLocked(deviceOwner.getUserHandle().getIdentifier());
        return true;
    }

    private void handleSendNetworkLoggingNotification() {
        final PackageManagerInternal pm = mInjector.getPackageManagerInternal();
        final Intent intent = new Intent(DevicePolicyManager.ACTION_SHOW_DEVICE_MONITORING_DIALOG);
        intent.setPackage(pm.getSystemUiServiceComponent().getPackageName());
        mNetworkLoggingNotificationUserId = getCurrentForegroundUserId();
        // Simple notification clicks are immutable
        final PendingIntent pendingIntent = PendingIntent.getBroadcastAsUser(mContext, 0, intent,
                PendingIntent.FLAG_IMMUTABLE, UserHandle.CURRENT);

        final String title = getNetworkLoggingTitle();
        final String text = getNetworkLoggingText();
        Notification notification =
                new Notification.Builder(mContext, SystemNotificationChannels.DEVICE_ADMIN)
                .setSmallIcon(R.drawable.ic_info_outline)
                .setContentTitle(title)
                .setContentText(text)
                .setTicker(title)
                .setShowWhen(true)
                .setContentIntent(pendingIntent)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .build();
        Slogf.i(LOG_TAG, "Sending network logging notification to user %d",
                mNetworkLoggingNotificationUserId);
        mInjector.getNotificationManager().notifyAsUser(/* tag= */ null,
                SystemMessage.NOTE_NETWORK_LOGGING, notification,
                UserHandle.of(mNetworkLoggingNotificationUserId));
    }

    private String getNetworkLoggingTitle() {
        return getUpdatableString(
                NETWORK_LOGGING_TITLE, R.string.network_logging_notification_title);
    }

    private String getNetworkLoggingText() {
        return getUpdatableString(
                NETWORK_LOGGING_MESSAGE, R.string.network_logging_notification_text);
    }

    private void handleCancelNetworkLoggingNotification() {
        if (mNetworkLoggingNotificationUserId == UserHandle.USER_NULL) {
            // Happens when setNetworkLoggingActive(false) is called before called with true
            Slogf.d(LOG_TAG, "Not cancelling network logging notification for USER_NULL");
            return;
        }

        Slogf.i(LOG_TAG, "Cancelling network logging notification for user %d",
                mNetworkLoggingNotificationUserId);
        mInjector.getNotificationManager().cancelAsUser(/* tag= */ null,
                SystemMessage.NOTE_NETWORK_LOGGING,
                UserHandle.of(mNetworkLoggingNotificationUserId));
        mNetworkLoggingNotificationUserId = UserHandle.USER_NULL;
    }

    /**
     * Return the package name of owner in a given user.
     */
    private String getOwnerPackageNameForUserLocked(int userId) {
        return mOwners.getDeviceOwnerUserId() == userId
                ? mOwners.getDeviceOwnerPackageName()
                : mOwners.getProfileOwnerPackage(userId);
    }

    /**
     * @param rawIntent Original service intent specified by caller. It must be explicit.
     * @param expectedPackageName The expected package name of the resolved service.
     * @return Intent that have component explicitly set. {@code null} if no service is resolved
     *     with the given intent.
     * @throws SecurityException if the intent is resolved to an invalid service.
     */
    private Intent createCrossUserServiceIntent(
            @NonNull Intent rawIntent, @NonNull String expectedPackageName,
            @UserIdInt int targetUserId) throws RemoteException, SecurityException {
        ResolveInfo info = mIPackageManager.resolveService(
                rawIntent,
                rawIntent.resolveTypeIfNeeded(mContext.getContentResolver()),
                0,  // flags
                targetUserId);
        if (info == null || info.serviceInfo == null) {
            Slogf.e(LOG_TAG, "Fail to look up the service: %s or user %d is not running", rawIntent,
                    targetUserId);
            return null;
        }
        if (!expectedPackageName.equals(info.serviceInfo.packageName)) {
            throw new SecurityException("Only allow to bind service in " + expectedPackageName);
        }
        // STOPSHIP(b/37624960): Remove info.serviceInfo.exported before release.
        if (info.serviceInfo.exported && !BIND_DEVICE_ADMIN.equals(info.serviceInfo.permission)) {
            throw new SecurityException(
                    "Service must be protected by BIND_DEVICE_ADMIN permission");
        }
        // It is the system server to bind the service, it would be extremely dangerous if it
        // can be exploited to bind any service. Set the component explicitly to make sure we
        // do not bind anything accidentally.
        rawIntent.setComponent(info.serviceInfo.getComponentName());
        return rawIntent;
    }

    @Override
    public long getLastSecurityLogRetrievalTime() {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || canManageUsers(caller));
        return getUserData(UserHandle.USER_SYSTEM).mLastSecurityLogRetrievalTime;
     }

    @Override
    public long getLastBugReportRequestTime() {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || canManageUsers(caller));
        return getUserData(UserHandle.USER_SYSTEM).mLastBugReportRequestTime;
     }

    @Override
    public long getLastNetworkLogRetrievalTime() {
        final CallerIdentity caller = getCallerIdentity();

        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                || (isProfileOwner(caller) && isManagedProfile(caller.getUserId()))
                || canManageUsers(caller));
        final int affectedUserId = getNetworkLoggingAffectedUser();
        return affectedUserId >= 0 ? getUserData(affectedUserId).mLastNetworkLogsRetrievalTime : -1;
    }

    @Override
    public boolean setResetPasswordToken(ComponentName admin, byte[] token) {
        if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
            return false;
        }
        if (token == null || token.length < 32) {
            throw new IllegalArgumentException("token must be at least 32-byte long");
        }
        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));

        synchronized (getLockObject()) {
            final int userHandle = caller.getUserId();

            DevicePolicyData policy = getUserData(userHandle);
            return mInjector.binderWithCleanCallingIdentity(() -> {
                if (policy.mPasswordTokenHandle != 0) {
                    mLockPatternUtils.removeEscrowToken(policy.mPasswordTokenHandle, userHandle);
                }
                policy.mPasswordTokenHandle = mLockPatternUtils.addEscrowToken(token,
                        userHandle, /*EscrowTokenStateChangeCallback*/ null);
                saveSettingsLocked(userHandle);
                return policy.mPasswordTokenHandle != 0;
            });
        }
    }

    @Override
    public boolean clearResetPasswordToken(ComponentName admin) {
        if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
            return false;
        }
        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));

        synchronized (getLockObject()) {
            final int userHandle = caller.getUserId();

            DevicePolicyData policy = getUserData(userHandle);
            if (policy.mPasswordTokenHandle != 0) {
                return mInjector.binderWithCleanCallingIdentity(() -> {
                    boolean result = mLockPatternUtils.removeEscrowToken(
                            policy.mPasswordTokenHandle, userHandle);
                    policy.mPasswordTokenHandle = 0;
                    saveSettingsLocked(userHandle);
                    return result;
                });
            }
        }
        return false;
    }

    @Override
    public boolean isResetPasswordTokenActive(ComponentName admin) {
        if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
            return false;
        }
        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));

        synchronized (getLockObject()) {
            return isResetPasswordTokenActiveForUserLocked(caller.getUserId());
        }
    }

    private boolean isResetPasswordTokenActiveForUserLocked(int userHandle) {
        DevicePolicyData policy = getUserData(userHandle);
        if (policy.mPasswordTokenHandle != 0) {
            return mInjector.binderWithCleanCallingIdentity(() ->
                    mLockPatternUtils.isEscrowTokenActive(policy.mPasswordTokenHandle, userHandle));
        }
        return false;
    }

    @Override
    public boolean resetPasswordWithToken(ComponentName admin, String passwordOrNull, byte[] token,
            int flags) {
        if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
            return false;
        }
        Objects.requireNonNull(token);

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));

        synchronized (getLockObject()) {
            DevicePolicyData policy = getUserData(caller.getUserId());
            if (policy.mPasswordTokenHandle != 0) {
                final String password = passwordOrNull != null ? passwordOrNull : "";
                final boolean result = resetPasswordInternal(password, policy.mPasswordTokenHandle,
                        token, flags, caller);
                if (result) {
                    DevicePolicyEventLogger
                            .createEvent(DevicePolicyEnums.RESET_PASSWORD_WITH_TOKEN)
                            .setAdmin(caller.getComponentName())
                            .write();
                }
                return result;
            } else {
                Slogf.w(LOG_TAG, "No saved token handle");
            }
        }
        return false;
    }

    @Override
    public boolean isCurrentInputMethodSetByOwner() {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                || isProfileOwner(caller) || isSystemUid(caller),
                "Only profile owner, device owner and system may call this method.");
        return getUserData(caller.getUserId()).mCurrentInputMethodSet;
    }

    @Override
    public StringParceledListSlice getOwnerInstalledCaCerts(@NonNull UserHandle user) {
        final int userId = user.getIdentifier();
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization((userId == caller.getUserId())
                || isProfileOwner(caller) || isDefaultDeviceOwner(caller)
                || hasFullCrossUsersPermission(caller, userId));

        synchronized (getLockObject()) {
            return new StringParceledListSlice(
                    new ArrayList<>(getUserData(userId).mOwnerInstalledCaCerts));
        }
    }

    @Override
    public void clearApplicationUserData(ComponentName admin, String packageName,
            IPackageDataObserver callback) {
        Objects.requireNonNull(admin, "ComponentName is null");
        Objects.requireNonNull(packageName, "packageName is null");
        Objects.requireNonNull(callback, "callback is null");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_CLEAR_APPLICATION_USER_DATA);

        long ident = mInjector.binderClearCallingIdentity();
        try {
            ActivityManager.getService().clearApplicationUserData(packageName, false, callback,
                    caller.getUserId());
        } catch(RemoteException re) {
            // Same process, should not happen.
        } catch (SecurityException se) {
            // This can happen e.g. for device admin packages, do not throw out the exception,
            // because callers have no means to know beforehand for which packages this might
            // happen. If so, we send back that removal failed.
            Slogf.w(LOG_TAG, "Not allowed to clear application user data for package "
                    + packageName, se);
            try {
                callback.onRemoveCompleted(packageName, false);
            } catch (RemoteException re) {
                // Caller is no longer available, ignore
            }
        } finally {
            mInjector.binderRestoreCallingIdentity(ident);
        }
    }

    @Override
    public void setLogoutEnabled(ComponentName admin, boolean enabled) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(admin, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_LOGOUT_ENABLED);

        synchronized (getLockObject()) {
            ActiveAdmin deviceOwner = getDeviceOwnerAdminLocked();
            if (deviceOwner.isLogoutEnabled == enabled) {
                // already in the requested state
                return;
            }
            deviceOwner.isLogoutEnabled = enabled;
            saveSettingsLocked(caller.getUserId());
        }
    }

    @Override
    public boolean isLogoutEnabled() {
        if (!mHasFeature) {
            return false;
        }
        synchronized (getLockObject()) {
            ActiveAdmin deviceOwner = getDeviceOwnerAdminLocked();
            return (deviceOwner != null) && deviceOwner.isLogoutEnabled;
        }
    }

    @Override
    public List<String> getDisallowedSystemApps(ComponentName admin, int userId,
            String provisioningAction) throws RemoteException {
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        return new ArrayList<>(
                mOverlayPackagesProvider.getNonRequiredApps(admin, userId, provisioningAction));
    }

    @Override
    public void transferOwnership(@NonNull ComponentName admin, @NonNull ComponentName target,
            @Nullable PersistableBundle bundle) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(admin, "ComponentName is null");
        Objects.requireNonNull(target, "Target cannot be null.");
        Preconditions.checkArgument(!admin.equals(target),
                "Provided administrator and target are the same object.");
        Preconditions.checkArgument(!admin.getPackageName().equals(target.getPackageName()),
                "Provided administrator and target have the same package name.");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller));

        final int callingUserId = caller.getUserId();
        final DevicePolicyData policy = getUserData(callingUserId);
        final DeviceAdminInfo incomingDeviceInfo = findAdmin(target, callingUserId,
                /* throwForMissingPermission= */ true);
        checkActiveAdminPrecondition(target, incomingDeviceInfo, policy);
        Preconditions.checkArgument(incomingDeviceInfo.supportsTransferOwnership(),
                "Provided target does not support ownership transfer.");

        final long id = mInjector.binderClearCallingIdentity();
        String ownerType = null;
        try {
            synchronized (getLockObject()) {
                /*
                * We must ensure the whole process is atomic to prevent the device from ending up
                * in an invalid state (e.g. no active admin). This could happen if the device
                * is rebooted or work mode is turned off mid-transfer.
                * In order to guarantee atomicity, we:
                *
                * 1. Save an atomic journal file describing the transfer process
                * 2. Perform the transfer itself
                * 3. Delete the journal file
                *
                * That way if the journal file exists on device boot, we know that the transfer
                * must be reverted back to the original administrator. This logic is implemented in
                * revertTransferOwnershipIfNecessaryLocked.
                * */
                if (bundle == null) {
                    bundle = new PersistableBundle();
                }
                if (isProfileOwner(caller)) {
                    ownerType = ADMIN_TYPE_PROFILE_OWNER;
                    prepareTransfer(admin, target, bundle, callingUserId,
                            ADMIN_TYPE_PROFILE_OWNER);
                    transferProfileOwnershipLocked(admin, target, callingUserId);
                    sendProfileOwnerCommand(DeviceAdminReceiver.ACTION_TRANSFER_OWNERSHIP_COMPLETE,
                            getTransferOwnershipAdminExtras(bundle), callingUserId);
                    postTransfer(DevicePolicyManager.ACTION_PROFILE_OWNER_CHANGED, callingUserId);
                    if (isUserAffiliatedWithDeviceLocked(callingUserId)) {
                        notifyAffiliatedProfileTransferOwnershipComplete(callingUserId);
                    }
                } else if (isDefaultDeviceOwner(caller)) {
                    ownerType = ADMIN_TYPE_DEVICE_OWNER;
                    prepareTransfer(admin, target, bundle, callingUserId,
                            ADMIN_TYPE_DEVICE_OWNER);
                    transferDeviceOwnershipLocked(admin, target, callingUserId);
                    sendDeviceOwnerCommand(DeviceAdminReceiver.ACTION_TRANSFER_OWNERSHIP_COMPLETE,
                            getTransferOwnershipAdminExtras(bundle));
                    postTransfer(DevicePolicyManager.ACTION_DEVICE_OWNER_CHANGED, callingUserId);
                }
            }
        } finally {
            mInjector.binderRestoreCallingIdentity(id);
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.TRANSFER_OWNERSHIP)
                .setAdmin(admin)
                .setStrings(target.getPackageName(), ownerType)
                .write();
    }

    private void prepareTransfer(ComponentName admin, ComponentName target,
            PersistableBundle bundle, int callingUserId, String adminType) {
        saveTransferOwnershipBundleLocked(bundle, callingUserId);
        mTransferOwnershipMetadataManager.saveMetadataFile(
                new TransferOwnershipMetadataManager.Metadata(admin, target,
                        callingUserId, adminType));
    }

    private void postTransfer(String broadcast, int callingUserId) {
        deleteTransferOwnershipMetadataFileLocked();
        sendOwnerChangedBroadcast(broadcast, callingUserId);
    }

    private void notifyAffiliatedProfileTransferOwnershipComplete(int callingUserId) {
        final Bundle extras = new Bundle();
        extras.putParcelable(Intent.EXTRA_USER, UserHandle.of(callingUserId));
        sendDeviceOwnerCommand(
                DeviceAdminReceiver.ACTION_AFFILIATED_PROFILE_TRANSFER_OWNERSHIP_COMPLETE, extras);
    }

    /**
     * Transfers the profile owner for user with id profileOwnerUserId from admin to target.
     */
    private void transferProfileOwnershipLocked(ComponentName admin, ComponentName target,
            int profileOwnerUserId) {
        transferActiveAdminUncheckedLocked(target, admin, profileOwnerUserId);
        mOwners.transferProfileOwner(target, profileOwnerUserId);
        Slogf.i(LOG_TAG, "Profile owner set: " + target + " on user " + profileOwnerUserId);
        mOwners.writeProfileOwner(profileOwnerUserId);
        mDeviceAdminServiceController.startServiceForAdmin(
                target.getPackageName(), profileOwnerUserId, "transfer-profile-owner");
    }

    /**
     * Transfers the device owner for user with id userId from admin to target.
     */
    private void transferDeviceOwnershipLocked(ComponentName admin, ComponentName target, int userId) {
        transferActiveAdminUncheckedLocked(target, admin, userId);
        mOwners.transferDeviceOwnership(target);
        Slogf.i(LOG_TAG, "Device owner set: " + target + " on user " + userId);
        mOwners.writeDeviceOwner();
        mDeviceAdminServiceController.startServiceForAdmin(
                target.getPackageName(), userId, "transfer-device-owner");
    }

    private Bundle getTransferOwnershipAdminExtras(PersistableBundle bundle) {
        Bundle extras = new Bundle();
        if (bundle != null) {
            extras.putParcelable(EXTRA_TRANSFER_OWNERSHIP_ADMIN_EXTRAS_BUNDLE, bundle);
        }
        return extras;
    }

    @Override
    public void setStartUserSessionMessage(
            ComponentName admin, CharSequence startUserSessionMessage) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(admin, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));

        final String startUserSessionMessageString =
                startUserSessionMessage != null ? startUserSessionMessage.toString() : null;

        synchronized (getLockObject()) {
            final ActiveAdmin deviceOwner = getDeviceOwnerAdminLocked();
            if (TextUtils.equals(deviceOwner.startUserSessionMessage, startUserSessionMessage)) {
                return;
            }
            deviceOwner.startUserSessionMessage = startUserSessionMessageString;
            saveSettingsLocked(caller.getUserId());
        }

        mInjector.getActivityManagerInternal()
                .setSwitchingFromSystemUserMessage(startUserSessionMessageString);
    }

    @Override
    public void setEndUserSessionMessage(ComponentName admin, CharSequence endUserSessionMessage) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(admin, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));

        final String endUserSessionMessageString =
                endUserSessionMessage != null ? endUserSessionMessage.toString() : null;

        synchronized (getLockObject()) {
            final ActiveAdmin deviceOwner = getDeviceOwnerAdminLocked();
            if (TextUtils.equals(deviceOwner.endUserSessionMessage, endUserSessionMessage)) {
                return;
            }
            deviceOwner.endUserSessionMessage = endUserSessionMessageString;
            saveSettingsLocked(caller.getUserId());
        }

        mInjector.getActivityManagerInternal()
                .setSwitchingToSystemUserMessage(endUserSessionMessageString);
    }

    @Override
    public String getStartUserSessionMessage(ComponentName admin) {
        if (!mHasFeature) {
            return null;
        }
        Objects.requireNonNull(admin, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));

        synchronized (getLockObject()) {
            final ActiveAdmin deviceOwner = getDeviceOwnerAdminLocked();
            return deviceOwner.startUserSessionMessage;
        }
    }

    @Override
    public String getEndUserSessionMessage(ComponentName admin) {
        if (!mHasFeature) {
            return null;
        }
        Objects.requireNonNull(admin, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));

        synchronized (getLockObject()) {
            final ActiveAdmin deviceOwner = getDeviceOwnerAdminLocked();
            return deviceOwner.endUserSessionMessage;
        }
    }

    private void deleteTransferOwnershipMetadataFileLocked() {
        mTransferOwnershipMetadataManager.deleteMetadataFile();
    }

    @Override
    @Nullable
    public PersistableBundle getTransferOwnershipBundle() {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                isProfileOwner(caller) || isDefaultDeviceOwner(caller));

        synchronized (getLockObject()) {
            final int callingUserId = caller.getUserId();
            final File bundleFile = new File(
                    mPathProvider.getUserSystemDirectory(callingUserId),
                    TRANSFER_OWNERSHIP_PARAMETERS_XML);
            if (!bundleFile.exists()) {
                return null;
            }
            try (FileInputStream stream = new FileInputStream(bundleFile)) {
                TypedXmlPullParser parser = Xml.resolvePullParser(stream);
                parser.next();
                return PersistableBundle.restoreFromXml(parser);
            } catch (IOException | XmlPullParserException | IllegalArgumentException e) {
                Slogf.e(LOG_TAG, "Caught exception while trying to load the "
                        + "owner transfer parameters from file " + bundleFile, e);
                return null;
            }
        }
    }

    @Override
    public int addOverrideApn(@NonNull ComponentName who, @NonNull ApnSetting apnSetting) {
        if (!mHasFeature || !mHasTelephonyFeature) {
            return -1;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        Objects.requireNonNull(apnSetting, "ApnSetting is null in addOverrideApn");
        final CallerIdentity caller = getCallerIdentity(who);
        if (apnSetting.getApnTypeBitmask() == ApnSetting.TYPE_ENTERPRISE) {
            Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                    || isManagedProfileOwner(caller));
        } else {
            Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        }

        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        if (tm != null) {
            return mInjector.binderWithCleanCallingIdentity(
                    () -> tm.addDevicePolicyOverrideApn(mContext, apnSetting));
        } else {
            Slogf.w(LOG_TAG, "TelephonyManager is null when trying to add override apn");
            return INVALID_APN_ID;
        }
    }

    @Override
    public boolean updateOverrideApn(@NonNull ComponentName who, int apnId,
            @NonNull ApnSetting apnSetting) {
        if (!mHasFeature || !mHasTelephonyFeature) {
            return false;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        Objects.requireNonNull(apnSetting, "ApnSetting is null in updateOverrideApn");
        final CallerIdentity caller = getCallerIdentity(who);
        ApnSetting apn = getApnSetting(apnId);
        if (apn != null && apn.getApnTypeBitmask() == ApnSetting.TYPE_ENTERPRISE
                && apnSetting.getApnTypeBitmask() == ApnSetting.TYPE_ENTERPRISE) {
            Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                    || isManagedProfileOwner(caller));
        } else {
            Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        }

        if (apnId < 0) {
            return false;
        }
        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        if (tm != null) {
            return mInjector.binderWithCleanCallingIdentity(
                    () -> tm.modifyDevicePolicyOverrideApn(mContext, apnId, apnSetting));
        } else {
            Slogf.w(LOG_TAG, "TelephonyManager is null when trying to modify override apn");
            return false;
        }
    }

    @Override
    public boolean removeOverrideApn(@NonNull ComponentName who, int apnId) {
        if (!mHasFeature || !mHasTelephonyFeature) {
            return false;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        ApnSetting apn = getApnSetting(apnId);
        if (apn != null && apn.getApnTypeBitmask() == ApnSetting.TYPE_ENTERPRISE) {
            Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                    || isManagedProfileOwner(caller));
        } else {
            Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        }
        return removeOverrideApnUnchecked(apnId);
    }

    private boolean removeOverrideApnUnchecked(int apnId) {
        if(apnId < 0) {
            return false;
        }
        int numDeleted = mInjector.binderWithCleanCallingIdentity(
                () -> mContext.getContentResolver().delete(
                        Uri.withAppendedPath(DPC_URI, Integer.toString(apnId)), null, null));
        return numDeleted > 0;
    }

    private ApnSetting getApnSetting(int apnId) {
        if (apnId < 0) {
            return null;
        }
        ApnSetting apnSetting = null;
        Cursor cursor = mInjector.binderWithCleanCallingIdentity(
                () -> mContext.getContentResolver().query(
                        Uri.withAppendedPath(DPC_URI, Integer.toString(apnId)), null, null, null,
                        Telephony.Carriers.DEFAULT_SORT_ORDER));
        if (cursor != null) {
            while (cursor.moveToNext()) {
                apnSetting = ApnSetting.makeApnSetting(cursor);
                if (apnSetting != null) {
                    break;
                }
            }
            cursor.close();
        }
        return apnSetting;
    }

    @Override
    public List<ApnSetting> getOverrideApns(@NonNull ComponentName who) {
        if (!mHasFeature || !mHasTelephonyFeature) {
            return Collections.emptyList();
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller)
                || isManagedProfileOwner(caller));
        List<ApnSetting> apnSettings = getOverrideApnsUnchecked();
        if (isProfileOwner(caller)) {
            List<ApnSetting> apnSettingList = new ArrayList<>();
            for (ApnSetting apnSetting : apnSettings) {
                if (apnSetting.getApnTypeBitmask() == ApnSetting.TYPE_ENTERPRISE) {
                    apnSettingList.add(apnSetting);
                }
            }
            return apnSettingList;
        } else {
            return apnSettings;
        }
    }

    private List<ApnSetting> getOverrideApnsUnchecked() {
        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        if (tm != null) {
            return mInjector.binderWithCleanCallingIdentity(
                    () -> tm.getDevicePolicyOverrideApns(mContext));
        }
        Slogf.w(LOG_TAG, "TelephonyManager is null when trying to get override apns");
        return Collections.emptyList();
    }

    @Override
    public void setOverrideApnsEnabled(@NonNull ComponentName who, boolean enabled) {
        if (!mHasFeature || !mHasTelephonyFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_OVERRIDE_APNS_ENABLED);

        setOverrideApnsEnabledUnchecked(enabled);
    }

    private void setOverrideApnsEnabledUnchecked(boolean enabled) {
        ContentValues value = new ContentValues();
        value.put(ENFORCE_KEY, enabled);
        mInjector.binderWithCleanCallingIdentity(() -> mContext.getContentResolver().update(
                    ENFORCE_MANAGED_URI, value, null, null));
    }

    @Override
    public boolean isOverrideApnEnabled(@NonNull ComponentName who) {
        if (!mHasFeature || !mHasTelephonyFeature) {
            return false;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));

        Cursor enforceCursor = mInjector.binderWithCleanCallingIdentity(
                () -> mContext.getContentResolver().query(
                        ENFORCE_MANAGED_URI, null, null, null, null));

        if (enforceCursor == null) {
            return false;
        }
        try {
            if (enforceCursor.moveToFirst()) {
                return enforceCursor.getInt(enforceCursor.getColumnIndex(ENFORCE_KEY)) == 1;
            }
        } catch (IllegalArgumentException e) {
            Slogf.e(LOG_TAG, "Cursor returned from ENFORCE_MANAGED_URI doesn't contain "
                    + "correct info.", e);
        } finally {
            enforceCursor.close();
        }
        return false;
    }

    @VisibleForTesting
    void saveTransferOwnershipBundleLocked(PersistableBundle bundle, int userId) {
        final File parametersFile = new File(
                mPathProvider.getUserSystemDirectory(userId),
                TRANSFER_OWNERSHIP_PARAMETERS_XML);
        final AtomicFile atomicFile = new AtomicFile(parametersFile);
        FileOutputStream stream = null;
        try {
            stream = atomicFile.startWrite();
            final TypedXmlSerializer serializer = Xml.resolveSerializer(stream);
            serializer.startDocument(null, true);
            serializer.startTag(null, TAG_TRANSFER_OWNERSHIP_BUNDLE);
            bundle.saveToXml(serializer);
            serializer.endTag(null, TAG_TRANSFER_OWNERSHIP_BUNDLE);
            serializer.endDocument();
            atomicFile.finishWrite(stream);
        } catch (IOException | XmlPullParserException e) {
            Slogf.e(LOG_TAG, "Caught exception while trying to save the "
                    + "owner transfer parameters to file " + parametersFile, e);
            parametersFile.delete();
            atomicFile.failWrite(stream);
        }
    }

    void deleteTransferOwnershipBundleLocked(int userId) {
        final File parametersFile = new File(mPathProvider.getUserSystemDirectory(userId),
                TRANSFER_OWNERSHIP_PARAMETERS_XML);
        parametersFile.delete();
    }

    private void logPasswordQualitySetIfSecurityLogEnabled(ComponentName who, int userId,
            boolean parent, PasswordPolicy passwordPolicy) {
        if (SecurityLog.isLoggingEnabled()) {
            final int affectedUserId = parent ? getProfileParentId(userId) : userId;
            SecurityLog.writeEvent(SecurityLog.TAG_PASSWORD_COMPLEXITY_SET, who.getPackageName(),
                    userId, affectedUserId, passwordPolicy.length, passwordPolicy.quality,
                    passwordPolicy.letters, passwordPolicy.nonLetter, passwordPolicy.numeric,
                    passwordPolicy.upperCase, passwordPolicy.lowerCase, passwordPolicy.symbols);
        }
    }

    private static String getManagedProvisioningPackage(Context context) {
        return context.getResources().getString(R.string.config_managed_provisioning_package);
    }

    private void putPrivateDnsSettings(int mode, @Nullable String host) {
        // Set Private DNS settings using system permissions, as apps cannot write
        // to global settings.
        mInjector.binderWithCleanCallingIdentity(() -> {
            ConnectivitySettingsManager.setPrivateDnsMode(mContext, mode);
            ConnectivitySettingsManager.setPrivateDnsHostname(mContext, host);
        });
    }

    @Override
    public int setGlobalPrivateDns(@NonNull ComponentName who, int mode, String privateDnsHost) {
        if (!mHasFeature) {
            return PRIVATE_DNS_SET_ERROR_FAILURE_SETTING;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        checkAllUsersAreAffiliatedWithDevice();
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_SET_GLOBAL_PRIVATE_DNS);

        switch (mode) {
            case PRIVATE_DNS_MODE_OPPORTUNISTIC:
                if (!TextUtils.isEmpty(privateDnsHost)) {
                    throw new IllegalArgumentException(
                            "Host provided for opportunistic mode, but is not needed.");
                }
                putPrivateDnsSettings(ConnectivitySettingsManager.PRIVATE_DNS_MODE_OPPORTUNISTIC,
                        null);
                return PRIVATE_DNS_SET_NO_ERROR;
            case PRIVATE_DNS_MODE_PROVIDER_HOSTNAME:
                if (TextUtils.isEmpty(privateDnsHost)
                        || !NetworkUtilsInternal.isWeaklyValidatedHostname(privateDnsHost)) {
                    throw new IllegalArgumentException(
                            String.format("Provided hostname %s is not valid", privateDnsHost));
                }

                // Connectivity check will have been performed in the DevicePolicyManager before
                // the call here.
                putPrivateDnsSettings(
                        ConnectivitySettingsManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME,
                        privateDnsHost);
                return PRIVATE_DNS_SET_NO_ERROR;
            default:
                throw new IllegalArgumentException(
                        String.format("Provided mode, %d, is not a valid mode.", mode));
        }
    }

    @Override
    public int getGlobalPrivateDnsMode(@NonNull ComponentName who) {
        if (!mHasFeature) {
            return PRIVATE_DNS_MODE_UNKNOWN;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));

        final int currentMode = ConnectivitySettingsManager.getPrivateDnsMode(mContext);
        switch (currentMode) {
            case ConnectivitySettingsManager.PRIVATE_DNS_MODE_OFF:
                return PRIVATE_DNS_MODE_OFF;
            case ConnectivitySettingsManager.PRIVATE_DNS_MODE_OPPORTUNISTIC:
                return PRIVATE_DNS_MODE_OPPORTUNISTIC;
            case ConnectivitySettingsManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME:
                return PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;
        }

        return PRIVATE_DNS_MODE_UNKNOWN;
    }

    @Override
    public String getGlobalPrivateDnsHost(@NonNull ComponentName who) {
        if (!mHasFeature) {
            return null;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        return mInjector.settingsGlobalGetString(PRIVATE_DNS_SPECIFIER);
    }

    @Override
    public void installUpdateFromFile(ComponentName admin,
            ParcelFileDescriptor updateFileDescriptor, StartInstallingUpdateCallback callback) {
        Objects.requireNonNull(admin, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(admin);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwnerOfOrganizationOwnedDevice(caller));
        checkCanExecuteOrThrowUnsafe(DevicePolicyManager.OPERATION_INSTALL_SYSTEM_UPDATE);

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.INSTALL_SYSTEM_UPDATE)
                .setAdmin(caller.getComponentName())
                .setBoolean(isDeviceAB())
                .write();

        mInjector.binderWithCleanCallingIdentity(() -> {
            UpdateInstaller updateInstaller;
            if (isDeviceAB()) {
                updateInstaller = new AbUpdateInstaller(
                        mContext, updateFileDescriptor, callback, mInjector, mConstants);
            } else {
                updateInstaller = new NonAbUpdateInstaller(
                        mContext, updateFileDescriptor, callback, mInjector, mConstants);
            }
            updateInstaller.startInstallUpdate();
        });
    }

    private boolean isDeviceAB() {
        return "true".equalsIgnoreCase(android.os.SystemProperties
                .get(AB_DEVICE_KEY, ""));
    }

    @Override
    public void setCrossProfileCalendarPackages(ComponentName who, List<String> packageNames) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isProfileOwner(caller));

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            admin.mCrossProfileCalendarPackages = packageNames;
            saveSettingsLocked(caller.getUserId());
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_CROSS_PROFILE_CALENDAR_PACKAGES)
                .setAdmin(who)
                .setStrings(packageNames == null ? null
                        : packageNames.toArray(new String[packageNames.size()]))
                .write();
    }

    @Override
    public List<String> getCrossProfileCalendarPackages(ComponentName who) {
        if (!mHasFeature) {
            return Collections.emptyList();
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isProfileOwner(caller));

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            return admin.mCrossProfileCalendarPackages;
        }
    }

    @Override
    public boolean isPackageAllowedToAccessCalendarForUser(String packageName,
            int userHandle) {
        if (!mHasFeature) {
            return false;
        }
        Preconditions.checkStringNotEmpty(packageName, "Package name is null or empty");
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");

        final CallerIdentity caller = getCallerIdentity();
        final int packageUid = mInjector.binderWithCleanCallingIdentity(() -> {
            try {
                return mInjector.getPackageManager().getPackageUidAsUser(packageName, userHandle);
            } catch (NameNotFoundException e) {
                Slogf.w(LOG_TAG, e,
                        "Couldn't find package %s in user %d", packageName, userHandle);
                return -1;
            }
        });
        if (caller.getUid() != packageUid) {
            Preconditions.checkCallAuthorization(
                    hasCallingOrSelfPermission(permission.INTERACT_ACROSS_USERS)
                            || hasCallingOrSelfPermission(permission.INTERACT_ACROSS_USERS_FULL));
        }

        synchronized (getLockObject()) {
            if (mInjector.settingsSecureGetIntForUser(
                    Settings.Secure.CROSS_PROFILE_CALENDAR_ENABLED, 0, userHandle) == 0) {
                return false;
            }
            final ActiveAdmin admin = getProfileOwnerAdminLocked(userHandle);
            if (admin != null) {
                if (admin.mCrossProfileCalendarPackages == null) {
                    return true;
                }
                return admin.mCrossProfileCalendarPackages.contains(packageName);
            }
        }
        return false;
    }

    @Override
    public List<String> getCrossProfileCalendarPackagesForUser(int userHandle) {
        if (!mHasFeature) {
            return Collections.emptyList();
        }
        Preconditions.checkArgumentNonnegative(userHandle, "Invalid userId");
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.INTERACT_ACROSS_USERS)
                        || hasCallingOrSelfPermission(permission.INTERACT_ACROSS_USERS_FULL));

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerAdminLocked(userHandle);
            if (admin != null) {
                return admin.mCrossProfileCalendarPackages;
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void setCrossProfilePackages(ComponentName who, List<String> packageNames) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(who, "ComponentName is null");
        Objects.requireNonNull(packageNames, "Package names is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isProfileOwner(caller));

        final List<String> previousCrossProfilePackages;
        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            previousCrossProfilePackages = admin.mCrossProfilePackages;
            if (packageNames.equals(previousCrossProfilePackages)) {
                return;
            }
            admin.mCrossProfilePackages = packageNames;
            saveSettingsLocked(caller.getUserId());
        }
        logSetCrossProfilePackages(who, packageNames);
        final CrossProfileApps crossProfileApps = mContext.getSystemService(CrossProfileApps.class);
        mInjector.binderWithCleanCallingIdentity(
                () -> crossProfileApps.resetInteractAcrossProfilesAppOps(
                        previousCrossProfilePackages, new HashSet<>(packageNames)));
    }

    private void logSetCrossProfilePackages(ComponentName who, List<String> packageNames) {
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_CROSS_PROFILE_PACKAGES)
                .setAdmin(who)
                .setStrings(packageNames.toArray(new String[packageNames.size()]))
                .write();
    }

    @Override
    public List<String> getCrossProfilePackages(ComponentName who) {
        if (!mHasFeature) {
            return Collections.emptyList();
        }
        Objects.requireNonNull(who, "ComponentName is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isProfileOwner(caller));

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            return admin.mCrossProfilePackages;
        }
    }

    @Override
    public List<String> getAllCrossProfilePackages() {
        if (!mHasFeature) {
            return Collections.emptyList();
        }
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                isSystemUid(caller) || isRootUid(caller) || hasCallingPermission(
                        permission.INTERACT_ACROSS_USERS) || hasCallingPermission(
                        permission.INTERACT_ACROSS_USERS_FULL) || hasPermissionForPreflight(
                        caller, permission.INTERACT_ACROSS_PROFILES));

        synchronized (getLockObject()) {
            final List<ActiveAdmin> admins = getProfileOwnerAdminsForCurrentProfileGroup();
            final List<String> packages = getCrossProfilePackagesForAdmins(admins);

            packages.addAll(getDefaultCrossProfilePackages());

            return packages;
        }
    }

    private List<String> getCrossProfilePackagesForAdmins(List<ActiveAdmin> admins) {
        final List<String> packages = new ArrayList<>();
        for (int i = 0; i < admins.size(); i++) {
            packages.addAll(admins.get(i).mCrossProfilePackages);
        }
        return packages;
    }

    @Override
    public List<String> getDefaultCrossProfilePackages() {
        Set<String> crossProfilePackages = new HashSet<>();

        Collections.addAll(crossProfilePackages, mContext.getResources()
                .getStringArray(R.array.cross_profile_apps));
        Collections.addAll(crossProfilePackages, mContext.getResources()
                .getStringArray(R.array.vendor_cross_profile_apps));

        return new ArrayList<>(crossProfilePackages);
    }

    private List<ActiveAdmin> getProfileOwnerAdminsForCurrentProfileGroup() {
        synchronized (getLockObject()) {
            final List<ActiveAdmin> admins = new ArrayList<>();
            int[] users = mUserManager.getProfileIdsWithDisabled(
                    mInjector.userHandleGetCallingUserId());
            for (int i = 0; i < users.length; i++) {
                final ComponentName componentName = getProfileOwnerAsUser(users[i]);
                if (componentName != null) {
                    ActiveAdmin admin = getActiveAdminUncheckedLocked(componentName, users[i]);
                    if (admin != null) {
                        admins.add(admin);
                    }
                }
            }
            return admins;
        }
    }

    @Override
    public boolean isManagedKiosk() {
        if (!mHasFeature) {
            return false;
        }
        Preconditions.checkCallAuthorization(canManageUsers(getCallerIdentity())
                || hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        long id = mInjector.binderClearCallingIdentity();
        try {
            return isManagedKioskInternal();
        } catch (RemoteException e) {
            throw new IllegalStateException(e);
        } finally {
            mInjector.binderRestoreCallingIdentity(id);
        }
    }

    private boolean isUnattendedManagedKioskUnchecked() {
        try {
            return isManagedKioskInternal()
                    && getPowerManagerInternal().wasDeviceIdleFor(UNATTENDED_MANAGED_KIOSK_MS);
        } catch (RemoteException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean isUnattendedManagedKiosk() {
        if (!mHasFeature) {
            return false;
        }
        Preconditions.checkCallAuthorization(canManageUsers(getCallerIdentity())
                || hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        return mInjector.binderWithCleanCallingIdentity(() -> isUnattendedManagedKioskUnchecked());
    }

    /**
     * Returns whether the device is currently being used as a publicly-accessible dedicated device.
     * Assumes that feature checks and permission checks have already been performed, and that the
     * calling identity has been cleared.
     */
    private boolean isManagedKioskInternal() throws RemoteException {
        return mOwners.hasDeviceOwner()
                && mInjector.getIActivityManager().getLockTaskModeState()
                        == ActivityManager.LOCK_TASK_MODE_LOCKED
                && !isLockTaskFeatureEnabled(DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO)
                && !deviceHasKeyguard()
                && !inEphemeralUserSession();
    }

    private boolean isLockTaskFeatureEnabled(int lockTaskFeature) throws RemoteException {
        //TODO(b/175285301): Explicitly get the user's identity to check.
        int lockTaskFeatures =
                getUserData(getCurrentForegroundUserId()).mLockTaskFeatures;
        return (lockTaskFeatures & lockTaskFeature) == lockTaskFeature;
    }

    private boolean deviceHasKeyguard() {
        for (UserInfo userInfo : mUserManager.getUsers()) {
            if (mLockPatternUtils.isSecure(userInfo.id)) {
                return true;
            }
        }
        return false;
    }

    private boolean inEphemeralUserSession() {
        for (UserInfo userInfo : mUserManager.getUsers()) {
            if (mInjector.getUserManager().isUserEphemeral(userInfo.id)) {
                return true;
            }
        }
        return false;
    }

    private PowerManagerInternal getPowerManagerInternal() {
        return mInjector.getPowerManagerInternal();
    }

    @Override
    public boolean startViewCalendarEventInManagedProfile(String packageName, long eventId,
            long start, long end, boolean allDay, int flags) {
        if (!mHasFeature) {
            return false;
        }
        Preconditions.checkStringNotEmpty(packageName, "Package name is empty");

        final CallerIdentity caller = getCallerIdentity();
        if (!isCallingFromPackage(packageName, caller.getUid())) {
            throw new SecurityException("Input package name doesn't align with actual "
                    + "calling package.");
        }
        return mInjector.binderWithCleanCallingIdentity(() -> {
            final int workProfileUserId = getManagedUserId(caller.getUserId());
            if (workProfileUserId < 0) {
                return false;
            }
            if (!isPackageAllowedToAccessCalendarForUser(packageName, workProfileUserId)) {
                Slogf.d(LOG_TAG, "Package %s is not allowed to access cross-profile calendar APIs",
                        packageName);
                return false;
            }
            final Intent intent = new Intent(
                    CalendarContract.ACTION_VIEW_MANAGED_PROFILE_CALENDAR_EVENT);
            intent.setPackage(packageName);
            intent.putExtra(CalendarContract.EXTRA_EVENT_ID, eventId);
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start);
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end);
            intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay);
            intent.setFlags(flags);
            try {
                mContext.startActivityAsUser(intent, UserHandle.of(workProfileUserId));
            } catch (ActivityNotFoundException e) {
                Slogf.e(LOG_TAG, "View event activity not found", e);
                return false;
            }
            return true;
        });
    }

    @Override
    public void setApplicationExemptions(String packageName, int[] exemptions) {
        if (!mHasFeature) {
            return;
        }
        Preconditions.checkStringNotEmpty(packageName, "Package name cannot be empty.");
        Objects.requireNonNull(exemptions, "Application exemptions must not be null.");
        Preconditions.checkArgument(areApplicationExemptionsValid(exemptions),
                "Invalid application exemption constant found in application exemptions set.");
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_DEVICE_POLICY_APP_EXEMPTIONS));

        final CallerIdentity caller = getCallerIdentity();
        final ApplicationInfo packageInfo;
        packageInfo = getPackageInfoWithNullCheck(packageName, caller);

        for (Map.Entry<Integer, String> entry :
                APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS.entrySet()) {
            int currentMode = mInjector.getAppOpsManager().unsafeCheckOpNoThrow(
                    entry.getValue(), packageInfo.uid, packageInfo.packageName);
            int newMode = ArrayUtils.contains(exemptions, entry.getKey())
                    ? MODE_ALLOWED : MODE_DEFAULT;
            mInjector.binderWithCleanCallingIdentity(() -> {
                if (currentMode != newMode) {
                    mInjector.getAppOpsManager()
                            .setMode(entry.getValue(),
                                    packageInfo.uid,
                                    packageName,
                                    newMode);
                }
            });
        }
    }

    @Override
    public int[] getApplicationExemptions(String packageName) {
        if (!mHasFeature) {
            return new int[0];
        }
        Preconditions.checkStringNotEmpty(packageName, "Package name cannot be empty.");
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_DEVICE_POLICY_APP_EXEMPTIONS));

        final CallerIdentity caller = getCallerIdentity();
        final ApplicationInfo packageInfo;
        packageInfo = getPackageInfoWithNullCheck(packageName, caller);

        IntArray appliedExemptions = new IntArray(0);
        for (Map.Entry<Integer, String> entry :
                APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS.entrySet()) {
            if (mInjector.getAppOpsManager().unsafeCheckOpNoThrow(
                    entry.getValue(), packageInfo.uid, packageInfo.packageName) == MODE_ALLOWED) {
                appliedExemptions.add(entry.getKey());
            }
        }
        return appliedExemptions.toArray();
    }

    private ApplicationInfo getPackageInfoWithNullCheck(String packageName, CallerIdentity caller) {
        final ApplicationInfo packageInfo =
                mInjector.getPackageManagerInternal().getApplicationInfo(
                        packageName,
                        /* flags= */ 0,
                        caller.getUid(),
                        caller.getUserId());
        if (packageInfo == null) {
            throw new ServiceSpecificException(
                    DevicePolicyManager.ERROR_PACKAGE_NAME_NOT_FOUND,
                    "Package name not found.");
        }
        return packageInfo;
    }

    private boolean areApplicationExemptionsValid(int[] exemptions) {
        for (int exemption : exemptions) {
            if (!APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS.containsKey(exemption)) {
                return false;
            }
        }
        return true;
    }

    private boolean isCallingFromPackage(String packageName, int callingUid) {
        return mInjector.binderWithCleanCallingIdentity(() -> {
            try {
                final int packageUid = mInjector.getPackageManager().getPackageUidAsUser(
                        packageName, UserHandle.getUserId(callingUid));
                return packageUid == callingUid;
            } catch (NameNotFoundException e) {
                Slogf.d(LOG_TAG, "Calling package not found", e);
                return false;
            }
        });
    }

    private DevicePolicyConstants loadConstants() {
        return DevicePolicyConstants.loadFromString(
                mInjector.settingsGlobalGetString(Global.DEVICE_POLICY_CONSTANTS));
    }

    @Override
    public void setUserControlDisabledPackages(ComponentName who, List<String> packages) {
        Objects.requireNonNull(who, "ComponentName is null");
        Objects.requireNonNull(packages, "packages is null");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller) || isProfileOwner(caller)
                || isFinancedDeviceOwner(caller));
        checkCanExecuteOrThrowUnsafe(
                DevicePolicyManager.OPERATION_SET_USER_CONTROL_DISABLED_PACKAGES);

        if (isCoexistenceEnabled(caller)) {
            Binder.withCleanCallingIdentity(() -> {
                if (packages.isEmpty()) {
                    removeUserControlDisabledPackages(caller);
                } else {
                    addUserControlDisabledPackages(caller, new HashSet<>(packages));
                }
            });
        } else {
            synchronized (getLockObject()) {
                ActiveAdmin owner = getDeviceOrProfileOwnerAdminLocked(caller.getUserId());
                if (!Objects.equals(owner.protectedPackages, packages)) {
                    owner.protectedPackages = packages.isEmpty() ? null : packages;
                    saveSettingsLocked(caller.getUserId());
                    pushUserControlDisabledPackagesLocked(caller.getUserId());
                }
            }
        }

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_USER_CONTROL_DISABLED_PACKAGES)
                .setAdmin(who)
                .setStrings(packages.toArray(new String[packages.size()]))
                .write();
    }

    private void addUserControlDisabledPackages(CallerIdentity caller, Set<String> packages) {
        if (isCallerDeviceOwner(caller)) {
            mDevicePolicyEngine.setGlobalPolicy(
                    PolicyDefinition.USER_CONTROLLED_DISABLED_PACKAGES,
                    // TODO(b/260573124): add correct enforcing admin when permission changes are
                    //  merged.
                    EnforcingAdmin.createEnterpriseEnforcingAdmin(
                            caller.getComponentName(), caller.getUserId()),
                    packages);
        } else {
            mDevicePolicyEngine.setLocalPolicy(
                    PolicyDefinition.USER_CONTROLLED_DISABLED_PACKAGES,
                    // TODO(b/260573124): add correct enforcing admin when permission changes are
                    //  merged.
                    EnforcingAdmin.createEnterpriseEnforcingAdmin(
                            caller.getComponentName(), caller.getUserId()),
                    packages,
                    caller.getUserId());
        }
    }

    private void removeUserControlDisabledPackages(CallerIdentity caller) {
        if (isCallerDeviceOwner(caller)) {
            mDevicePolicyEngine.removeGlobalPolicy(
                    PolicyDefinition.USER_CONTROLLED_DISABLED_PACKAGES,
                    // TODO(b/260573124): add correct enforcing admin when permission changes are
                    //  merged.
                    EnforcingAdmin.createEnterpriseEnforcingAdmin(
                            caller.getComponentName(), caller.getUserId()));
        } else {
            mDevicePolicyEngine.removeLocalPolicy(
                    PolicyDefinition.USER_CONTROLLED_DISABLED_PACKAGES,
                    // TODO(b/260573124): add correct enforcing admin when permission changes are
                    //  merged.
                    EnforcingAdmin.createEnterpriseEnforcingAdmin(
                            caller.getComponentName(), caller.getUserId()),
                    caller.getUserId());
        }
    }

    private boolean isCallerDeviceOwner(CallerIdentity caller) {
        synchronized (getLockObject()) {
            return getDeviceOwnerUserIdUncheckedLocked() == caller.getUserId();
        }
    }

    @Override
    public List<String> getUserControlDisabledPackages(ComponentName who) {
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller) || isProfileOwner(caller)
                || isFinancedDeviceOwner(caller));

        if (isCoexistenceEnabled(caller)) {
            // This retrieves the policy for the calling user only, DOs for example can't know
            // what's enforced globally or on another user.
            Set<String> packages = mDevicePolicyEngine.getResolvedPolicy(
                    PolicyDefinition.USER_CONTROLLED_DISABLED_PACKAGES,
                    caller.getUserId());
            return packages == null ? Collections.emptyList() : packages.stream().toList();
        } else {
            synchronized (getLockObject()) {
                ActiveAdmin deviceOwner = getDeviceOrProfileOwnerAdminLocked(caller.getUserId());
                return deviceOwner.protectedPackages != null
                        ? deviceOwner.protectedPackages : Collections.emptyList();
            }
        }
    }

    @Override
    public void setCommonCriteriaModeEnabled(ComponentName who, boolean enabled) {
        Objects.requireNonNull(who, "Admin component name must be provided");
        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwnerOfOrganizationOwnedDevice(caller),
                "Common Criteria mode can only be controlled by a device owner or "
                        + "a profile owner on an organization-owned device.");
        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            admin.mCommonCriteriaMode = enabled;
            saveSettingsLocked(caller.getUserId());
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_COMMON_CRITERIA_MODE)
                .setAdmin(who)
                .setBoolean(enabled)
                .write();
    }

    @Override
    public boolean isCommonCriteriaModeEnabled(ComponentName who) {
        if (who != null) {
            final CallerIdentity caller = getCallerIdentity(who);
            Preconditions.checkCallAuthorization(
                    isDefaultDeviceOwner(caller) || isProfileOwnerOfOrganizationOwnedDevice(caller),
                    "Common Criteria mode can only be controlled by a device owner or "
                            + "a profile owner on an organization-owned device.");

            synchronized (getLockObject()) {
                final ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
                return admin.mCommonCriteriaMode;
            }
        }
        // Return aggregated state if caller is not admin (who == null).
        synchronized (getLockObject()) {
            // Only DO or COPE PO can turn on CC mode, so take a shortcut here and only look at
            // their ActiveAdmin, instead of iterating through all admins.
            ActiveAdmin admin;
            // TODO(b/261999445): remove
            if (isHeadlessFlagEnabled()) {
                admin = getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked();
            } else {
                admin = getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked(
                        UserHandle.USER_SYSTEM);
            }
            return admin != null ? admin.mCommonCriteriaMode : false;
        }
    }

    @Override
    public @PersonalAppsSuspensionReason int getPersonalAppsSuspendedReasons(ComponentName who) {
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        // DO shouldn't be able to use this method.
        Preconditions.checkCallAuthorization(isProfileOwnerOfOrganizationOwnedDevice(caller));

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            final long deadline = admin.mProfileOffDeadline;
            final int result = makeSuspensionReasons(admin.mSuspendPersonalApps,
                    deadline != 0 && mInjector.systemCurrentTimeMillis() > deadline);
            Slogf.d(LOG_TAG, "getPersonalAppsSuspendedReasons user: %d; result: %d",
                    mInjector.userHandleGetCallingUserId(), result);
            return result;
        }
    }

    private @PersonalAppsSuspensionReason int makeSuspensionReasons(
            boolean explicit, boolean timeout) {
        int result = PERSONAL_APPS_NOT_SUSPENDED;
        if (explicit) {
            result |= PERSONAL_APPS_SUSPENDED_EXPLICITLY;
        }
        if (timeout) {
            result |= PERSONAL_APPS_SUSPENDED_PROFILE_TIMEOUT;
        }
        return result;
    }

    @Override
    public void setPersonalAppsSuspended(ComponentName who, boolean suspended) {
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isProfileOwnerOfOrganizationOwnedDevice(caller));
        Preconditions.checkState(canHandleCheckPolicyComplianceIntent(caller));

        final int callingUserId = caller.getUserId();
        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerLocked(callingUserId);
            boolean shouldSaveSettings = false;
            if (admin.mSuspendPersonalApps != suspended) {
                admin.mSuspendPersonalApps = suspended;
                shouldSaveSettings = true;
            }
            if (admin.mProfileOffDeadline != 0) {
                admin.mProfileOffDeadline = 0;
                shouldSaveSettings = true;
            }
            if (shouldSaveSettings) {
                saveSettingsLocked(callingUserId);
            }
        }

        mInjector.binderWithCleanCallingIdentity(() -> updatePersonalAppsSuspension(
                callingUserId, mUserManager.isUserUnlocked(callingUserId)));

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_PERSONAL_APPS_SUSPENDED)
                .setAdmin(caller.getComponentName())
                .setBoolean(suspended)
                .write();
    }

    /** Starts an activity to check policy compliance or request compliance acknowledgement. */
    private void triggerPolicyComplianceCheckIfNeeded(int profileUserId, boolean suspended) {
        synchronized (getLockObject()) {
            final ActiveAdmin profileOwner = getProfileOwnerAdminLocked(profileUserId);
            if (profileOwner == null) {
                Slogf.wtf(LOG_TAG, "Profile owner not found for compliance check");
                return;
            }
            if (suspended) {
                // If suspended, DPC will need to show an activity.
                final Intent intent = new Intent(ACTION_CHECK_POLICY_COMPLIANCE);
                intent.setPackage(profileOwner.info.getPackageName());
                mContext.startActivityAsUser(intent, UserHandle.of(profileUserId));
            } else if (profileOwner.mProfileOffDeadline > 0) {
                // If not suspended, but deadline set, DPC needs to acknowledge compliance so that
                // the deadline can be reset.
                sendAdminCommandLocked(profileOwner, ACTION_COMPLIANCE_ACKNOWLEDGEMENT_REQUIRED,
                        /* adminExtras= */ null, /* receiver= */ null, /* inForeground = */ true);
            }
        }
    }

    /**
     * Checks whether personal apps should be suspended according to the policy and applies the
     * change if needed.
     *
     * @param unlocked whether the profile is currently running unlocked.
     */
    private boolean updatePersonalAppsSuspension(int profileUserId, boolean unlocked) {
        final boolean shouldSuspend;
        synchronized (getLockObject()) {
            final ActiveAdmin profileOwner = getProfileOwnerAdminLocked(profileUserId);
            if (profileOwner != null) {
                final int notificationState =
                        updateProfileOffDeadlineLocked(profileUserId, profileOwner, unlocked);
                final boolean suspendedExplicitly = profileOwner.mSuspendPersonalApps;
                final boolean suspendedByTimeout = profileOwner.mProfileOffDeadline == -1;
                Slogf.d(LOG_TAG,
                        "Personal apps suspended explicitly: %b, by timeout: %b, notification: %d",
                        suspendedExplicitly, suspendedByTimeout, notificationState);
                updateProfileOffDeadlineNotificationLocked(
                        profileUserId, profileOwner, notificationState);
                shouldSuspend = suspendedExplicitly || suspendedByTimeout;
            } else {
                shouldSuspend = false;
            }
        }

        final int parentUserId = getProfileParentId(profileUserId);
        suspendPersonalAppsInternal(parentUserId, shouldSuspend);
        return shouldSuspend;
    }

    /**
     * Checks work profile time off policy, scheduling personal apps suspension via alarm if
     * necessary.
     * @return notification state
     */
    private int updateProfileOffDeadlineLocked(
            int profileUserId, ActiveAdmin profileOwner, boolean unlocked) {
        final long now = mInjector.systemCurrentTimeMillis();
        if (profileOwner.mProfileOffDeadline != 0 && now > profileOwner.mProfileOffDeadline) {
            Slogf.i(LOG_TAG, "Profile off deadline has been reached, unlocked: " + unlocked);
            if (profileOwner.mProfileOffDeadline != -1) {
                // Move the deadline far to the past so that it cannot be rolled back by TZ change.
                profileOwner.mProfileOffDeadline = -1;
                saveSettingsLocked(profileUserId);
            }
            return unlocked ? PROFILE_OFF_NOTIFICATION_NONE : PROFILE_OFF_NOTIFICATION_SUSPENDED;
        }
        boolean shouldSaveSettings = false;
        if (profileOwner.mSuspendPersonalApps) {
            // When explicit suspension is active, deadline shouldn't be set.
            if (profileOwner.mProfileOffDeadline != 0) {
                profileOwner.mProfileOffDeadline = 0;
                shouldSaveSettings = true;
            }
        } else if (profileOwner.mProfileOffDeadline != 0
                && (profileOwner.mProfileMaximumTimeOffMillis == 0)) {
            // There is a deadline but either there is no policy -> clear
            // the deadline.
            Slogf.i(LOG_TAG, "Profile off deadline is reset to zero");
            profileOwner.mProfileOffDeadline = 0;
            shouldSaveSettings = true;
        } else if (profileOwner.mProfileOffDeadline == 0
                && (profileOwner.mProfileMaximumTimeOffMillis != 0 && !unlocked)) {
            // There profile is locked and there is a policy, but the deadline is not set -> set the
            // deadline.
            Slogf.i(LOG_TAG, "Profile off deadline is set.");
            profileOwner.mProfileOffDeadline = now + profileOwner.mProfileMaximumTimeOffMillis;
            shouldSaveSettings = true;
        }

        if (shouldSaveSettings) {
            saveSettingsLocked(profileUserId);
        }

        final long alarmTime;
        final int notificationState;
        if (unlocked || profileOwner.mProfileOffDeadline == 0) {
            alarmTime = 0;
            notificationState = PROFILE_OFF_NOTIFICATION_NONE;
        } else if (profileOwner.mProfileOffDeadline - now < MANAGED_PROFILE_OFF_WARNING_PERIOD) {
            // The deadline is close, upon the alarm personal apps should be suspended.
            alarmTime = profileOwner.mProfileOffDeadline;
            notificationState = PROFILE_OFF_NOTIFICATION_WARNING;
        } else {
            // The deadline is quite far, upon the alarm we should warn the user first, so the
            // alarm is scheduled earlier than the actual deadline.
            alarmTime = profileOwner.mProfileOffDeadline - MANAGED_PROFILE_OFF_WARNING_PERIOD;
            notificationState = PROFILE_OFF_NOTIFICATION_NONE;
        }

        final AlarmManager am = mInjector.getAlarmManager();
        final Intent intent = new Intent(ACTION_PROFILE_OFF_DEADLINE);
        intent.setPackage(mContext.getPackageName());
        // Broadcast alarms sent by system are immutable
        final PendingIntent pi = mInjector.pendingIntentGetBroadcast(
                mContext, REQUEST_PROFILE_OFF_DEADLINE, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE);

        if (alarmTime == 0) {
            Slogf.i(LOG_TAG, "Profile off deadline alarm is removed.");
            am.cancel(pi);
        } else {
            Slogf.i(LOG_TAG, "Profile off deadline alarm is set.");
            am.set(AlarmManager.RTC, alarmTime, pi);
        }

        return notificationState;
    }

    private void suspendPersonalAppsInternal(int userId, boolean suspended) {
        if (getUserData(userId).mAppsSuspended == suspended) {
            return;
        }
        Slogf.i(LOG_TAG, "%s personal apps for user %d", suspended ? "Suspending" : "Unsuspending",
                userId);

        if (suspended) {
            suspendPersonalAppsInPackageManager(userId);
        } else {
            mInjector.getPackageManagerInternal().unsuspendForSuspendingPackage(
                    PLATFORM_PACKAGE_NAME, userId);
        }

        synchronized (getLockObject()) {
            getUserData(userId).mAppsSuspended = suspended;
            saveSettingsLocked(userId);
        }
    }

    private void suspendPersonalAppsInPackageManager(int userId) {
        mInjector.binderWithCleanCallingIdentity(() -> {
            try {
                final String[] appsToSuspend = mInjector.getPersonalAppsForSuspension(userId);
                final String[] failedApps = mIPackageManager.setPackagesSuspendedAsUser(
                        appsToSuspend, true, null, null, null, PLATFORM_PACKAGE_NAME, userId);
                if (!ArrayUtils.isEmpty(failedApps)) {
                    Slogf.wtf(LOG_TAG, "Failed to suspend apps: " + String.join(",", failedApps));
                }
            } catch (RemoteException re) {
                // Shouldn't happen.
                Slogf.e(LOG_TAG, "Failed talking to the package manager", re);
            }
        });
    }

    @GuardedBy("getLockObject()")
    private void updateProfileOffDeadlineNotificationLocked(
            int profileUserId, ActiveAdmin profileOwner, int notificationState) {
        if (notificationState == PROFILE_OFF_NOTIFICATION_NONE) {
            mInjector.getNotificationManager().cancel(SystemMessage.NOTE_PERSONAL_APPS_SUSPENDED);
            return;
        }

        final Intent intent = new Intent(ACTION_TURN_PROFILE_ON_NOTIFICATION);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra(Intent.EXTRA_USER_HANDLE, profileUserId);

        // Simple notification action button clicks are immutable
        final PendingIntent pendingIntent = mInjector.pendingIntentGetBroadcast(mContext,
                0 /* requestCode */, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        final Notification.Action turnProfileOnButton = new Notification.Action.Builder(
                /* icon= */ null, getPersonalAppSuspensionButtonText(), pendingIntent).build();

        final String text;
        final boolean ongoing;
        if (notificationState == PROFILE_OFF_NOTIFICATION_WARNING) {
            // Round to the closest integer number of days.
            final int maxDays = (int)
                    ((profileOwner.mProfileMaximumTimeOffMillis + MS_PER_DAY / 2) / MS_PER_DAY);
            final String date = DateUtils.formatDateTime(
                    mContext, profileOwner.mProfileOffDeadline, DateUtils.FORMAT_SHOW_DATE);
            final String time = DateUtils.formatDateTime(
                    mContext, profileOwner.mProfileOffDeadline, DateUtils.FORMAT_SHOW_TIME);
            text = getPersonalAppSuspensionSoonText(date, time, maxDays);
            ongoing = false;
        } else {
            text = getPersonalAppSuspensionText();
            ongoing = true;
        }
        final int color = mContext.getColor(R.color.personal_apps_suspension_notification_color);
        final Bundle extras = new Bundle();
        // TODO: Create a separate string for this.
        extras.putString(
                Notification.EXTRA_SUBSTITUTE_APP_NAME, getWorkProfileContentDescription());

        final Notification notification =
                new Notification.Builder(mContext, SystemNotificationChannels.DEVICE_ADMIN)
                        .setSmallIcon(R.drawable.ic_corp_badge_no_background)
                        .setOngoing(ongoing)
                        .setAutoCancel(false)
                        .setContentTitle(getPersonalAppSuspensionTitle())
                        .setContentText(text)
                        .setStyle(new Notification.BigTextStyle().bigText(text))
                        .setColor(color)
                        .addAction(turnProfileOnButton)
                        .addExtras(extras)
                        .build();

        mInjector.getNotificationManager().notifyAsUser(
                null, SystemMessage.NOTE_PERSONAL_APPS_SUSPENDED, notification,
                UserHandle.of(getProfileParentId(profileUserId)));
    }

    private String getPersonalAppSuspensionButtonText() {
        return getUpdatableString(
                PERSONAL_APP_SUSPENSION_TURN_ON_PROFILE,
                R.string.personal_apps_suspended_turn_profile_on);
    }

    private String getPersonalAppSuspensionTitle() {
        return getUpdatableString(
                PERSONAL_APP_SUSPENSION_TITLE, R.string.personal_apps_suspension_title);
    }

    private String getPersonalAppSuspensionText() {
        return getUpdatableString(
                PERSONAL_APP_SUSPENSION_MESSAGE, R.string.personal_apps_suspension_text);
    }

    private String getPersonalAppSuspensionSoonText(String date, String time, int maxDays) {
        return getUpdatableString(
                PERSONAL_APP_SUSPENSION_SOON_MESSAGE, R.string.personal_apps_suspension_soon_text,
                date, time, maxDays);
    }

    private String getWorkProfileContentDescription() {
        return getUpdatableString(
                NOTIFICATION_WORK_PROFILE_CONTENT_DESCRIPTION,
                R.string.notification_work_profile_content_description);
    }

    @Override
    public void setManagedProfileMaximumTimeOff(ComponentName who, long timeoutMillis) {
        Objects.requireNonNull(who, "ComponentName is null");
        Preconditions.checkArgumentNonnegative(timeoutMillis, "Timeout must be non-negative.");

        final CallerIdentity caller = getCallerIdentity(who);
        // DO shouldn't be able to use this method.
        Preconditions.checkCallAuthorization(isProfileOwnerOfOrganizationOwnedDevice(caller));
        Preconditions.checkState(canHandleCheckPolicyComplianceIntent(caller));

        final int userId = caller.getUserId();
        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerLocked(userId);

            // Ensure the timeout is long enough to avoid having bad user experience.
            if (timeoutMillis > 0 && timeoutMillis < MANAGED_PROFILE_MAXIMUM_TIME_OFF_THRESHOLD
                    && !isAdminTestOnlyLocked(who, userId)) {
                timeoutMillis = MANAGED_PROFILE_MAXIMUM_TIME_OFF_THRESHOLD;
            }
            if (admin.mProfileMaximumTimeOffMillis == timeoutMillis) {
                return;
            }
            admin.mProfileMaximumTimeOffMillis = timeoutMillis;
            saveSettingsLocked(userId);
        }

        mInjector.binderWithCleanCallingIdentity(
                () -> updatePersonalAppsSuspension(userId, mUserManager.isUserUnlocked()));

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_MANAGED_PROFILE_MAXIMUM_TIME_OFF)
                .setAdmin(caller.getComponentName())
                .setTimePeriod(timeoutMillis)
                .write();
    }

    private boolean canHandleCheckPolicyComplianceIntent(CallerIdentity caller) {
        mInjector.binderWithCleanCallingIdentity(() -> {
            final Intent intent = new Intent(DevicePolicyManager.ACTION_CHECK_POLICY_COMPLIANCE);
            intent.setPackage(caller.getPackageName());
            final List<ResolveInfo> handlers =
                    mInjector.getPackageManager().queryIntentActivitiesAsUser(intent, /* flags= */
                            0, caller.getUserId());
            return !handlers.isEmpty();
        });
        return true;
    }

    @Override
    public long getManagedProfileMaximumTimeOff(ComponentName who) {
        Objects.requireNonNull(who, "ComponentName is null");

        final CallerIdentity caller = getCallerIdentity(who);
        Preconditions.checkCallAuthorization(isProfileOwnerOfOrganizationOwnedDevice(caller));

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            return admin.mProfileMaximumTimeOffMillis;
        }
    }

    @Override
    public void acknowledgeDeviceCompliant() {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(isProfileOwnerOfOrganizationOwnedDevice(caller));
        enforceUserUnlocked(caller.getUserId());

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            if (admin.mProfileOffDeadline > 0) {
                admin.mProfileOffDeadline = 0;
                saveSettingsLocked(caller.getUserId());
            }
        }
    }

    @Override
    public boolean isComplianceAcknowledgementRequired() {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(isProfileOwnerOfOrganizationOwnedDevice(caller));
        enforceUserUnlocked(caller.getUserId());

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            return admin.mProfileOffDeadline != 0;
        }
    }

    @Override
    public boolean canProfileOwnerResetPasswordWhenLocked(int userId) {
        Preconditions.checkCallAuthorization(isSystemUid(getCallerIdentity()),
                String.format(NOT_SYSTEM_CALLER_MSG,
                        "call canProfileOwnerResetPasswordWhenLocked"));
        synchronized (getLockObject()) {
            final ActiveAdmin poAdmin = getProfileOwnerAdminLocked(userId);
            if (poAdmin == null
                    || getEncryptionStatus() != ENCRYPTION_STATUS_ACTIVE_PER_USER
                    || !isResetPasswordTokenActiveForUserLocked(userId)) {
                return false;
            }
            final ApplicationInfo poAppInfo;
            try {
                poAppInfo = mIPackageManager.getApplicationInfo(
                        poAdmin.info.getPackageName(), 0 /* flags */, userId);
            } catch (RemoteException e) {
                Slogf.e(LOG_TAG, "Failed to query PO app info", e);
                return false;
            }
            if (poAppInfo == null) {
                Slogf.wtf(LOG_TAG, "Cannot find AppInfo for profile owner");
                return false;
            }
            if (!poAppInfo.isEncryptionAware()) {
                return false;
            }
            Slogf.d(LOG_TAG, "PO should be able to reset password from direct boot");
            return true;
        }
    }

    @Override
    public String getEnrollmentSpecificId(String callerPackage) {
        if (!mHasFeature) {
            return "";
        }

        final CallerIdentity caller = getCallerIdentity(callerPackage);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwner(caller)
                        || isCallerDelegate(caller, DELEGATION_CERT_INSTALL));

        synchronized (getLockObject()) {
            final ActiveAdmin requiredAdmin = getDeviceOrProfileOwnerAdminLocked(
                    caller.getUserId());
            final String esid = requiredAdmin.mEnrollmentSpecificId;
            return esid != null ? esid : "";
        }
    }

    @Override
    public void setOrganizationIdForUser(
            @NonNull String callerPackage, @NonNull String organizationId, int userId) {
        if (!mHasFeature) {
            return;
        }
        Objects.requireNonNull(callerPackage);

        final CallerIdentity caller = getCallerIdentity(callerPackage);
        // Only the DPC can set this ID.
        Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller) || isProfileOwner(caller),
                "Only a Device Owner or Profile Owner may set the Enterprise ID.");
        // Empty enterprise ID must not be provided in calls to this method.
        Preconditions.checkArgument(!TextUtils.isEmpty(organizationId),
                "Enterprise ID may not be empty.");

        Slogf.i(LOG_TAG, "Setting Enterprise ID to %s for user %d", organizationId, userId);

        synchronized (mESIDInitilizationLock) {
            if (mEsidCalculator == null) {
                mInjector.binderWithCleanCallingIdentity(() -> {
                    mEsidCalculator = mInjector.newEnterpriseSpecificIdCalculator();
                });
            }
        }

        final String ownerPackage;
        synchronized (getLockObject()) {
            final ActiveAdmin owner = getDeviceOrProfileOwnerAdminLocked(userId);
            // As the caller is the system, it must specify the component name of the profile owner
            // as a safety check.
            Preconditions.checkCallAuthorization(
                    owner != null && owner.getUserHandle().getIdentifier() == userId,
                    String.format("The Profile Owner or Device Owner may only set the Enterprise ID"
                            + " on its own user, called on user %d but owner user is %d", userId,
                            owner.getUserHandle().getIdentifier()));
            ownerPackage = owner.info.getPackageName();
            Preconditions.checkState(
                    TextUtils.isEmpty(owner.mOrganizationId) || owner.mOrganizationId.equals(
                            organizationId),
                    "The organization ID has been previously set to a different value and cannot "
                            + "be changed");
            final String dpcPackage = owner.info.getPackageName();
            final String esid = mEsidCalculator.calculateEnterpriseId(dpcPackage,
                    organizationId);
            owner.mOrganizationId = organizationId;
            owner.mEnrollmentSpecificId = esid;
            saveSettingsLocked(userId);
        }

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_ORGANIZATION_ID)
                .setAdmin(ownerPackage)
                .setBoolean(isManagedProfile(userId))
                .write();
    }

    @Override
    public void clearOrganizationIdForUser(int userHandle) {
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        synchronized (getLockObject()) {
            final ActiveAdmin owner = getDeviceOrProfileOwnerAdminLocked(userHandle);
            owner.mOrganizationId = null;
            owner.mEnrollmentSpecificId = null;
            saveSettingsLocked(userHandle);
        }
    }

    @Override
    public UserHandle createAndProvisionManagedProfile(
            @NonNull ManagedProfileProvisioningParams provisioningParams,
            @NonNull String callerPackage) {
        Objects.requireNonNull(provisioningParams, "provisioningParams is null");
        Objects.requireNonNull(callerPackage, "callerPackage is null");

        final ComponentName admin = provisioningParams.getProfileAdminComponentName();
        Objects.requireNonNull(admin, "admin is null");

        final CallerIdentity caller = getCallerIdentity(callerPackage);
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        provisioningParams.logParams(callerPackage);

        UserInfo userInfo = null;
        final long identity = Binder.clearCallingIdentity();
        try {
            final int result = checkProvisioningPreconditionSkipPermission(
                    ACTION_PROVISION_MANAGED_PROFILE, admin.getPackageName(), caller.getUserId());
            if (result != STATUS_OK) {
                throw new ServiceSpecificException(
                        ERROR_PRE_CONDITION_FAILED,
                        "Provisioning preconditions failed with result: " + result);
            }

            final long startTime = SystemClock.elapsedRealtime();

            onCreateAndProvisionManagedProfileStarted(provisioningParams);

            final Set<String> nonRequiredApps = provisioningParams.isLeaveAllSystemAppsEnabled()
                    ? Collections.emptySet()
                    : mOverlayPackagesProvider.getNonRequiredApps(
                            admin, caller.getUserId(), ACTION_PROVISION_MANAGED_PROFILE);
            if (nonRequiredApps.isEmpty()) {
                Slogf.i(LOG_TAG, "No disallowed packages for the managed profile.");
            } else {
                for (String packageName : nonRequiredApps) {
                    Slogf.i(LOG_TAG, "Disallowed package [" + packageName + "]");
                }
            }

            userInfo = mUserManager.createProfileForUserEvenWhenDisallowed(
                    provisioningParams.getProfileName(),
                    UserManager.USER_TYPE_PROFILE_MANAGED,
                    UserInfo.FLAG_DISABLED,
                    caller.getUserId(),
                    nonRequiredApps.toArray(new String[nonRequiredApps.size()]));
            if (userInfo == null) {
                throw new ServiceSpecificException(
                        ERROR_PROFILE_CREATION_FAILED,
                        "Error creating profile, createProfileForUserEvenWhenDisallowed "
                                + "returned null.");
            }
            resetInteractAcrossProfilesAppOps(caller.getUserId());
            logEventDuration(
                    DevicePolicyEnums.PLATFORM_PROVISIONING_CREATE_PROFILE_MS,
                    startTime,
                    callerPackage);

            maybeInstallDevicePolicyManagementRoleHolderInUser(userInfo.id);

            installExistingAdminPackage(userInfo.id, admin.getPackageName());
            if (!enableAdminAndSetProfileOwner(userInfo.id, caller.getUserId(), admin)) {
                throw new ServiceSpecificException(
                        ERROR_SETTING_PROFILE_OWNER_FAILED,
                        "Error setting profile owner.");
            }
            setUserSetupComplete(userInfo.id);

            startUser(userInfo.id, callerPackage);
            maybeMigrateAccount(
                    userInfo.id, caller.getUserId(), provisioningParams.getAccountToMigrate(),
                    provisioningParams.isKeepingAccountOnMigration(), callerPackage);

            if (provisioningParams.isOrganizationOwnedProvisioning()) {
                synchronized (getLockObject()) {
                    setProfileOwnerOnOrganizationOwnedDeviceUncheckedLocked(admin, userInfo.id,
                            true);
                }
            }

            onCreateAndProvisionManagedProfileCompleted(provisioningParams);

            sendProvisioningCompletedBroadcast(
                    userInfo.id,
                    ACTION_PROVISION_MANAGED_PROFILE,
                    provisioningParams.isLeaveAllSystemAppsEnabled());

            return userInfo.getUserHandle();
        } catch (Exception e) {
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.PLATFORM_PROVISIONING_ERROR)
                    .setStrings(callerPackage)
                    .write();
            // In case of any errors during provisioning, remove the newly created profile.
            if (userInfo != null) {
                mUserManager.removeUserEvenWhenDisallowed(userInfo.id);
            }
            throw e;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void finalizeWorkProfileProvisioning(UserHandle managedProfileUser,
            Account migratedAccount) {
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        if (!isManagedProfile(managedProfileUser.getIdentifier())) {
            throw new IllegalStateException("Given user is not a managed profile");
        }
        ComponentName profileOwnerComponent =
                mOwners.getProfileOwnerComponent(managedProfileUser.getIdentifier());
        if (profileOwnerComponent == null) {
            throw new IllegalStateException("There is no profile owner on the given profile");
        }
        Intent primaryProfileSuccessIntent = new Intent(ACTION_MANAGED_PROFILE_PROVISIONED);
        primaryProfileSuccessIntent.setPackage(profileOwnerComponent.getPackageName());
        primaryProfileSuccessIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES
                | Intent.FLAG_RECEIVER_FOREGROUND);
        primaryProfileSuccessIntent.putExtra(Intent.EXTRA_USER, managedProfileUser);

        if (migratedAccount != null) {
            primaryProfileSuccessIntent.putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE,
                    migratedAccount);
        }

        mContext.sendBroadcastAsUser(primaryProfileSuccessIntent,
                UserHandle.of(getProfileParentId(managedProfileUser.getIdentifier())));
    }

    /**
     * Callback called at the beginning of {@link #createAndProvisionManagedProfile(
     * ManagedProfileProvisioningParams, String)} after the relevant prechecks have passed.
     *
     * <p>The logic in this method blocks provisioning.
     *
     * <p>This method is meant to be overridden by OEMs.
     */
    private void onCreateAndProvisionManagedProfileStarted(
            ManagedProfileProvisioningParams provisioningParams) {}

    /**
     * Callback called at the end of {@link #createAndProvisionManagedProfile(
     * ManagedProfileProvisioningParams, String)} after all the other provisioning tasks
     * have completed successfully.
     *
     * <p>The logic in this method blocks provisioning.
     *
     * <p>This method is meant to be overridden by OEMs.
     */
    private void onCreateAndProvisionManagedProfileCompleted(
            ManagedProfileProvisioningParams provisioningParams) {}

    private void maybeInstallDevicePolicyManagementRoleHolderInUser(int targetUserId) {
        String devicePolicyManagerRoleHolderPackageName =
                getDevicePolicyManagementRoleHolderPackageName(mContext);
        if (devicePolicyManagerRoleHolderPackageName == null) {
            Slogf.d(LOG_TAG, "No device policy management role holder specified.");
            return;
        }
        try {
            if (mIPackageManager.isPackageAvailable(
                    devicePolicyManagerRoleHolderPackageName, targetUserId)) {
                Slogf.d(LOG_TAG, "The device policy management role holder "
                        + devicePolicyManagerRoleHolderPackageName + " is already installed in "
                        + "user " + targetUserId);
                return;
            }
            Slogf.d(LOG_TAG, "Installing the device policy management role holder "
                    + devicePolicyManagerRoleHolderPackageName + " in user " + targetUserId);
            mIPackageManager.installExistingPackageAsUser(
                    devicePolicyManagerRoleHolderPackageName,
                    targetUserId,
                    PackageManager.INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS,
                    PackageManager.INSTALL_REASON_POLICY,
                    /* whiteListedPermissions= */ null);
        } catch (RemoteException e) {
            // Does not happen, same process
        }
    }


    private String getDevicePolicyManagementRoleHolderPackageName(Context context) {
        RoleManager roleManager = context.getSystemService(RoleManager.class);

        // Calling identity needs to be cleared as this method is used in the permissions checks.
        return mInjector.binderWithCleanCallingIdentity(() -> {
            List<String> roleHolders =
                    roleManager.getRoleHolders(RoleManager.ROLE_DEVICE_POLICY_MANAGEMENT);
            if (roleHolders.isEmpty()) {
                return null;
            }
            return roleHolders.get(0);
        });
    }

    private boolean isDevicePolicyManagementRoleHolder(CallerIdentity caller) {
        String devicePolicyManagementRoleHolderPackageName =
                getDevicePolicyManagementRoleHolderPackageName(mContext);
        return caller.getPackageName().equals(devicePolicyManagementRoleHolderPackageName);
    }

    private void resetInteractAcrossProfilesAppOps(@UserIdInt int userId) {
        mInjector.getCrossProfileApps(userId).clearInteractAcrossProfilesAppOps();
        pregrantDefaultInteractAcrossProfilesAppOps(userId);
    }

    private void pregrantDefaultInteractAcrossProfilesAppOps(@UserIdInt int userId) {
        final String op =
                AppOpsManager.permissionToOp(Manifest.permission.INTERACT_ACROSS_PROFILES);
        for (String packageName : getConfigurableDefaultCrossProfilePackages(userId)) {
            if (!appOpIsDefaultOrAllowed(userId, op, packageName)) {
                continue;
            }
            mInjector.getCrossProfileApps(userId).setInteractAcrossProfilesAppOp(
                    packageName, MODE_ALLOWED);
        }
    }

    private Set<String> getConfigurableDefaultCrossProfilePackages(@UserIdInt int userId) {
        List<String> defaultPackages = getDefaultCrossProfilePackages();
        return defaultPackages.stream().filter(
                mInjector.getCrossProfileApps(userId)::canConfigureInteractAcrossProfiles).collect(
                Collectors.toSet());
    }

    private boolean appOpIsDefaultOrAllowed(@UserIdInt int userId, String op, String packageName) {
        try {
            final int uid = mContext.createContextAsUser(UserHandle.of(userId), /* flags= */ 0).
                    getPackageManager().getPackageUid(packageName, /* flags= */ 0);
            int mode = mInjector.getAppOpsManager().unsafeCheckOpNoThrow(
                    op, uid, packageName);
            return mode == MODE_ALLOWED || mode == MODE_DEFAULT;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    private void installExistingAdminPackage(int userId, String packageName) {
        try {
            final int status = mContext.getPackageManager().installExistingPackageAsUser(
                    packageName,
                    userId);
            if (status != PackageManager.INSTALL_SUCCEEDED) {
                throw new ServiceSpecificException(
                        ERROR_ADMIN_PACKAGE_INSTALLATION_FAILED,
                        String.format("Failed to install existing package %s for user %d with "
                                        + "result code %d",
                                packageName, userId, status));
            }
        } catch (NameNotFoundException e) {
            throw new ServiceSpecificException(
                    ERROR_ADMIN_PACKAGE_INSTALLATION_FAILED,
                    String.format("Failed to install existing package %s for user %d: %s",
                            packageName, userId, e.getMessage()));
        }
    }

    private boolean enableAdminAndSetProfileOwner(
            @UserIdInt int userId, @UserIdInt int callingUserId, ComponentName adminComponent) {
        enableAndSetActiveAdmin(userId, callingUserId, adminComponent);
        return setProfileOwner(adminComponent, userId);
    }

    private void enableAndSetActiveAdmin(
            @UserIdInt int userId, @UserIdInt int callingUserId, ComponentName adminComponent) {
        final String adminPackage = adminComponent.getPackageName();
        enablePackage(adminPackage, callingUserId);
        setActiveAdmin(adminComponent, /* refreshing= */ true, userId);
    }

    private void enablePackage(String packageName, @UserIdInt int userId) {
        try {
            final int enabledSetting = mIPackageManager.getApplicationEnabledSetting(
                    packageName, userId);
            if (enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                    && enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                mIPackageManager.setApplicationEnabledSetting(
                        packageName,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        // Device policy app may have launched ManagedProvisioning, play nice and
                        // don't kill it as a side-effect of this call.
                        PackageManager.DONT_KILL_APP,
                        userId,
                        mContext.getOpPackageName());
            }
        } catch (RemoteException e) {
            // Shouldn't happen.
            Slogf.wtf(LOG_TAG, "Error setting application enabled", e);
        }
    }

    private void setUserSetupComplete(@UserIdInt int userId) {
        Settings.Secure.putIntForUser(
                mContext.getContentResolver(), USER_SETUP_COMPLETE, 1, userId);
    }

    private void startUser(@UserIdInt int userId, String callerPackage)
            throws IllegalStateException {
        final long startTime = SystemClock.elapsedRealtime();
        final UserUnlockedBlockingReceiver unlockedReceiver = new UserUnlockedBlockingReceiver(
                userId);
        mContext.registerReceiverAsUser(
                unlockedReceiver,
                new UserHandle(userId),
                new IntentFilter(Intent.ACTION_USER_UNLOCKED),
                /* broadcastPermission = */ null,
                /* scheduler= */ null);
        try {
            if (!mInjector.getIActivityManager().startUserInBackground(userId)) {
                throw new ServiceSpecificException(ERROR_STARTING_PROFILE_FAILED,
                        String.format("Unable to start user %d in background", userId));
            }

            if (!unlockedReceiver.waitForUserUnlocked()) {
                throw new ServiceSpecificException(ERROR_STARTING_PROFILE_FAILED,
                        String.format("Timeout whilst waiting for unlock of user %d.", userId));
            }
            logEventDuration(
                    DevicePolicyEnums.PLATFORM_PROVISIONING_START_PROFILE_MS,
                    startTime,
                    callerPackage);
        } catch (RemoteException e) {
            // Shouldn't happen.
            Slogf.wtf(LOG_TAG, "Error starting user", e);
        } finally {
            mContext.unregisterReceiver(unlockedReceiver);
        }
    }

    private void maybeMigrateAccount(
            @UserIdInt int targetUserId, @UserIdInt int sourceUserId, Account accountToMigrate,
            boolean keepAccountMigrated, String callerPackage) {
        final UserHandle sourceUser = UserHandle.of(sourceUserId);
        final UserHandle targetUser = UserHandle.of(targetUserId);
        if (accountToMigrate == null) {
            Slogf.d(LOG_TAG, "No account to migrate.");
            return;
        }
        if (sourceUser.equals(targetUser)) {
            Slogf.w(LOG_TAG, "sourceUser and targetUser are the same, won't migrate account.");
            return;
        }
        copyAccount(targetUser, sourceUser, accountToMigrate, callerPackage);
        if (!keepAccountMigrated) {
            removeAccount(accountToMigrate);
        }
    }

    private void copyAccount(
            UserHandle targetUser, UserHandle sourceUser, Account accountToMigrate,
            String callerPackage) {
        final long startTime = SystemClock.elapsedRealtime();
        try {
            final AccountManager accountManager = mContext.getSystemService(AccountManager.class);
            final boolean copySucceeded = accountManager.copyAccountToUser(
                    accountToMigrate,
                    sourceUser,
                    targetUser,
                    /* callback= */ null, /* handler= */ null)
                    .getResult(60 * 3, TimeUnit.SECONDS);
            if (copySucceeded) {
                logCopyAccountStatus(COPY_ACCOUNT_SUCCEEDED, callerPackage);
                logEventDuration(
                        DevicePolicyEnums.PLATFORM_PROVISIONING_COPY_ACCOUNT_MS,
                        startTime,
                        callerPackage);
            } else {
                logCopyAccountStatus(COPY_ACCOUNT_FAILED, callerPackage);
                Slogf.e(LOG_TAG, "Failed to copy account to " + targetUser);
            }
        } catch (OperationCanceledException e) {
            // Account migration is not considered a critical operation.
            logCopyAccountStatus(COPY_ACCOUNT_TIMED_OUT, callerPackage);
            Slogf.e(LOG_TAG, "Exception copying account to " + targetUser, e);
        } catch (AuthenticatorException | IOException e) {
            logCopyAccountStatus(COPY_ACCOUNT_EXCEPTION, callerPackage);
            Slogf.e(LOG_TAG, "Exception copying account to " + targetUser, e);
        }
    }

    private static void logCopyAccountStatus(@CopyAccountStatus int status, String callerPackage) {
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.PLATFORM_PROVISIONING_COPY_ACCOUNT_STATUS)
                .setInt(status)
                .setStrings(callerPackage)
                .write();
    }

    private void removeAccount(Account account) {
        final AccountManager accountManager =
                mContext.getSystemService(AccountManager.class);
        final AccountManagerFuture<Bundle> bundle = accountManager.removeAccount(account,
                null, null /* callback */, null /* handler */);
        try {
            final Bundle result = bundle.getResult();
            if (result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT, /* default */ false)) {
                Slogf.i(LOG_TAG, "Account removed from the primary user.");
            } else {
                // TODO(174768447): Revisit start activity logic.
                final Intent removeIntent = result.getParcelable(AccountManager.KEY_INTENT, android.content.Intent.class);
                removeIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);
                if (removeIntent != null) {
                    Slogf.i(LOG_TAG, "Starting activity to remove account");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        mContext.startActivity(removeIntent);
                    });
                } else {
                    Slogf.e(LOG_TAG, "Could not remove account from the primary user.");
                }
            }
        } catch (OperationCanceledException | AuthenticatorException | IOException e) {
            Slogf.e(LOG_TAG, "Exception removing account from the primary user.", e);
        }
    }

    @Override
    public void provisionFullyManagedDevice(
            @NonNull FullyManagedDeviceProvisioningParams provisioningParams,
            @NonNull String callerPackage) {
        Objects.requireNonNull(provisioningParams, "provisioningParams is null.");
        Objects.requireNonNull(callerPackage, "callerPackage is null.");

        ComponentName deviceAdmin = provisioningParams.getDeviceAdminComponentName();
        Objects.requireNonNull(deviceAdmin, "admin is null.");
        Objects.requireNonNull(provisioningParams.getOwnerName(), "owner name is null.");

        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS)
                        || (hasCallingOrSelfPermission(permission.PROVISION_DEMO_DEVICE)
                        && provisioningParams.isDemoDevice()));

        provisioningParams.logParams(callerPackage);

        final long identity = Binder.clearCallingIdentity();
        try {
            int result = checkProvisioningPreconditionSkipPermission(
                    ACTION_PROVISION_MANAGED_DEVICE, deviceAdmin.getPackageName(),
                    caller.getUserId());
            if (result != STATUS_OK) {
                throw new ServiceSpecificException(
                        ERROR_PRE_CONDITION_FAILED,
                        "Provisioning preconditions failed with result: " + result);
            }
            onProvisionFullyManagedDeviceStarted(provisioningParams);

            // These properties are global so will apply on all users
            setTimeAndTimezone(provisioningParams.getTimeZone(), provisioningParams.getLocalTime());
            setLocale(provisioningParams.getLocale());

            int deviceOwnerUserId = UserHandle.USER_SYSTEM;
            if (!removeNonRequiredAppsForManagedDevice(
                    deviceOwnerUserId,
                    provisioningParams.isLeaveAllSystemAppsEnabled(),
                    deviceAdmin)) {
                throw new ServiceSpecificException(
                        ERROR_REMOVE_NON_REQUIRED_APPS_FAILED,
                        "PackageManager failed to remove non required apps.");
            }


            if (!setActiveAdminAndDeviceOwner(deviceOwnerUserId, deviceAdmin)) {
                throw new ServiceSpecificException(
                        ERROR_SET_DEVICE_OWNER_FAILED, "Failed to set device owner.");
            }

            disallowAddUser();
            setAdminCanGrantSensorsPermissionForUserUnchecked(
                    deviceOwnerUserId, provisioningParams.canDeviceOwnerGrantSensorsPermissions());
            setDemoDeviceStateUnchecked(deviceOwnerUserId, provisioningParams.isDemoDevice());
            onProvisionFullyManagedDeviceCompleted(provisioningParams);
            sendProvisioningCompletedBroadcast(
                    deviceOwnerUserId,
                    ACTION_PROVISION_MANAGED_DEVICE,
                    provisioningParams.isLeaveAllSystemAppsEnabled());
        } catch (Exception e) {
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.PLATFORM_PROVISIONING_ERROR)
                    .setStrings(callerPackage)
                    .write();
            throw e;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Callback called at the beginning of {@link #provisionFullyManagedDevice(
     * FullyManagedDeviceProvisioningParams, String)} after the relevant prechecks have passed.
     *
     * <p>The logic in this method blocks provisioning.
     *
     * <p>This method is meant to be overridden by OEMs.
     */
    private void onProvisionFullyManagedDeviceStarted(
            FullyManagedDeviceProvisioningParams provisioningParams) {}

    /**
     * Callback called at the end of {@link #provisionFullyManagedDevice(
     * FullyManagedDeviceProvisioningParams, String)} after all the other provisioning tasks
     * have completed successfully.
     *
     * <p>The logic in this method blocks provisioning.
     *
     * <p>This method is meant to be overridden by OEMs.
     */
    private void onProvisionFullyManagedDeviceCompleted(
            FullyManagedDeviceProvisioningParams provisioningParams) {}

    private void setTimeAndTimezone(String timeZone, long localTime) {
        try {
            final AlarmManager alarmManager = mContext.getSystemService(AlarmManager.class);
            if (timeZone != null) {
                alarmManager.setTimeZone(timeZone);
            }
            if (localTime > 0) {
                alarmManager.setTime(localTime);
            }
        } catch (Exception e) {
            // Do not stop provisioning and ignore this error.
            Slogf.e(LOG_TAG, "Alarm manager failed to set the system time/timezone.", e);
        }
    }

    private void setLocale(Locale locale) {
        if (locale == null || locale.equals(Locale.getDefault())) {
            return;
        }
        try {
            // If locale is different from current locale this results in a configuration change,
            // which will trigger the restarting of the activity.
            LocalePicker.updateLocale(locale);
        } catch (Exception e) {
            // Do not stop provisioning and ignore this error.
            Slogf.e(LOG_TAG, "Failed to set the system locale.", e);
        }
    }

    private boolean removeNonRequiredAppsForManagedDevice(
            @UserIdInt int userId, boolean leaveAllSystemAppsEnabled, ComponentName admin) {
        Set<String> packagesToDelete = leaveAllSystemAppsEnabled
                ? Collections.emptySet()
                : mOverlayPackagesProvider.getNonRequiredApps(
                        admin, userId, ACTION_PROVISION_MANAGED_DEVICE);

        removeNonInstalledPackages(packagesToDelete, userId);
        if (packagesToDelete.isEmpty()) {
            Slogf.i(LOG_TAG, "No packages to delete on user " + userId);
            return true;
        }

        NonRequiredPackageDeleteObserver packageDeleteObserver =
                new NonRequiredPackageDeleteObserver(packagesToDelete.size());
        for (String packageName : packagesToDelete) {
            Slogf.i(LOG_TAG, "Deleting package [" + packageName + "] as user " + userId);
            mContext.getPackageManager().deletePackageAsUser(
                    packageName,
                    packageDeleteObserver,
                    PackageManager.DELETE_SYSTEM_APP,
                    userId);
        }
        Slogf.i(LOG_TAG, "Waiting for non required apps to be deleted");
        return packageDeleteObserver.awaitPackagesDeletion();
    }

    private void removeNonInstalledPackages(Set<String> packages, @UserIdInt int userId) {
        final Set<String> toBeRemoved = new HashSet<>();
        for (String packageName : packages) {
            if (!isPackageInstalledForUser(packageName, userId)) {
                toBeRemoved.add(packageName);
            }
        }
        packages.removeAll(toBeRemoved);
    }

    private void disallowAddUser() {
        if (!isHeadlessFlagEnabled() || mIsAutomotive) {
            // Auto still enables adding users due to the communal nature of those devices
            if (mInjector.userManagerIsHeadlessSystemUserMode()) {
                Slogf.i(LOG_TAG, "Not setting DISALLOW_ADD_USER on headless system user mode.");
                return;
            }
        }
        for (UserInfo userInfo : mUserManager.getUsers()) {
            UserHandle userHandle = userInfo.getUserHandle();
            if (!mUserManager.hasUserRestriction(UserManager.DISALLOW_ADD_USER, userHandle)) {
                mUserManager.setUserRestriction(
                        UserManager.DISALLOW_ADD_USER, /* value= */ true, userHandle);
            }
        }
    }

    private boolean setActiveAdminAndDeviceOwner(
            @UserIdInt int userId, ComponentName adminComponent) {
        enableAndSetActiveAdmin(userId, userId, adminComponent);
        // TODO(b/178187130): Directly set DO and remove the check once silent provisioning is no
        //  longer used.
        if (getDeviceOwnerComponent(/* callingUserOnly= */ true) == null) {
            return setDeviceOwner(adminComponent, userId,
                    /* setProfileOwnerOnCurrentUserIfNecessary= */ true);
        }
        return true;
    }

    private static void logEventDuration(int eventId, long startTime, String callerPackage) {
        final long duration = SystemClock.elapsedRealtime() - startTime;
        DevicePolicyEventLogger
                .createEvent(eventId)
                .setTimePeriod(duration)
                .setStrings(callerPackage)
                .write();
    }

    @Override
    public void resetDefaultCrossProfileIntentFilters(@UserIdInt int userId) {
        Preconditions.checkCallAuthorization(
                hasCallingOrSelfPermission(permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        mInjector.binderWithCleanCallingIdentity(() -> {
            try {
                final List<UserInfo> profiles = mUserManager.getProfiles(userId);
                final int numOfProfiles = profiles.size();
                if (numOfProfiles <= 1) {
                    return;
                }

                final String managedProvisioningPackageName = getManagedProvisioningPackage(
                        mContext);
                // Removes cross profile intent filters from the parent to all the profiles.
                mIPackageManager.clearCrossProfileIntentFilters(
                        userId, mContext.getOpPackageName());
                // Setting and resetting default cross profile intent filters was previously handled
                // by Managed Provisioning. For backwards compatibility, clear any intent filters
                // that were set by ManagedProvisioning.
                mIPackageManager.clearCrossProfileIntentFilters(
                        userId, managedProvisioningPackageName);

                // For each profile reset cross profile intent filters
                for (int i = 0; i < numOfProfiles; i++) {
                    UserInfo profile = profiles.get(i);
                    mIPackageManager.clearCrossProfileIntentFilters(
                            profile.id, mContext.getOpPackageName());
                    // Clear any intent filters that were set by ManagedProvisioning.
                    mIPackageManager.clearCrossProfileIntentFilters(
                            profile.id, managedProvisioningPackageName);

                    mUserManagerInternal.setDefaultCrossProfileIntentFilters(userId, profile.id);
                }
            } catch (RemoteException e) {
                // Shouldn't happen.
                Slogf.wtf(LOG_TAG, "Error resetting default cross profile intent filters", e);
            }
        });
    }

    private void setAdminCanGrantSensorsPermissionForUserUnchecked(@UserIdInt int userId,
            boolean canGrant) {
        Slogf.d(LOG_TAG, "setAdminCanGrantSensorsPermissionForUserUnchecked(%d, %b)",
                userId, canGrant);
        synchronized (getLockObject()) {
            ActiveAdmin owner = getDeviceOrProfileOwnerAdminLocked(userId);

            Preconditions.checkState(
                    isDeviceOwner(owner) && owner.getUserHandle().getIdentifier() == userId,
                    "May only be set on a the user of a device owner.");

            owner.mAdminCanGrantSensorsPermissions = canGrant;
            mPolicyCache.setAdminCanGrantSensorsPermissions(canGrant);
            saveSettingsLocked(userId);
        }
    }

    private void setDemoDeviceStateUnchecked(@UserIdInt int userId, boolean isDemoDevice) {
        Slogf.d(LOG_TAG, "setDemoDeviceStateUnchecked(%d, %b)",
                userId, isDemoDevice);
        if (!isDemoDevice) {
            return;
        }
        synchronized (getLockObject()) {
            mInjector.settingsGlobalPutStringForUser(
                    Settings.Global.DEVICE_DEMO_MODE, Integer.toString(/* value= */ 1), userId);
        }

        setUserProvisioningState(STATE_USER_SETUP_FINALIZED, userId);
    }

    private void updateAdminCanGrantSensorsPermissionCache(@UserIdInt int userId) {
        synchronized (getLockObject()) {

            ActiveAdmin owner;
            // If the user is affiliated the device (either a DO itself, or an affiliated PO),
            // use mAdminCanGrantSensorsPermissions from the DO
            if (isUserAffiliatedWithDeviceLocked(userId)) {
                owner = getDeviceOwnerAdminLocked();
            } else {
                owner = getDeviceOrProfileOwnerAdminLocked(userId);
            }
            boolean canGrant = owner != null ? owner.mAdminCanGrantSensorsPermissions : false;
            mPolicyCache.setAdminCanGrantSensorsPermissions(canGrant);
        }
    }

    private void updateNetworkPreferenceForUser(int userId,
            List<PreferentialNetworkServiceConfig> preferentialNetworkServiceConfigs) {
        if (!isManagedProfile(userId) && !isDeviceOwnerUserId(userId)) {
            return;
        }
        List<ProfileNetworkPreference> preferences = new ArrayList<>();
        for (PreferentialNetworkServiceConfig preferentialNetworkServiceConfig :
                preferentialNetworkServiceConfigs) {
            ProfileNetworkPreference.Builder preferenceBuilder =
                    new ProfileNetworkPreference.Builder();
            if (preferentialNetworkServiceConfig.isEnabled()) {
                if (preferentialNetworkServiceConfig.isFallbackToDefaultConnectionAllowed()) {
                    preferenceBuilder.setPreference(PROFILE_NETWORK_PREFERENCE_ENTERPRISE);
                } else if (preferentialNetworkServiceConfig.shouldBlockNonMatchingNetworks()) {
                    preferenceBuilder.setPreference(
                            PROFILE_NETWORK_PREFERENCE_ENTERPRISE_BLOCKING);
                } else {
                    preferenceBuilder.setPreference(
                            PROFILE_NETWORK_PREFERENCE_ENTERPRISE_NO_FALLBACK);
                }
                preferenceBuilder.setIncludedUids(
                        preferentialNetworkServiceConfig.getIncludedUids());
                preferenceBuilder.setExcludedUids(
                        preferentialNetworkServiceConfig.getExcludedUids());
                preferenceBuilder.setPreferenceEnterpriseId(
                        preferentialNetworkServiceConfig.getNetworkId());
            } else {
                preferenceBuilder.setPreference(PROFILE_NETWORK_PREFERENCE_DEFAULT);
            }


            preferences.add(preferenceBuilder.build());
        }
        Slogf.d(LOG_TAG, "updateNetworkPreferenceForUser to " + preferences);
        mInjector.binderWithCleanCallingIdentity(() ->
                mInjector.getConnectivityManager().setProfileNetworkPreferences(
                        UserHandle.of(userId), preferences,
                        null /* executor */, null /* listener */));
    }

    @Override
    public boolean canAdminGrantSensorsPermissions() {
        if (!mHasFeature) {
            return false;
        }

        return mPolicyCache.canAdminGrantSensorsPermissions();
    }

    @Override
    public void setDeviceOwnerType(@NonNull ComponentName admin,
            @DeviceOwnerType int deviceOwnerType) {
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(
                permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        synchronized (getLockObject()) {
            setDeviceOwnerTypeLocked(admin, deviceOwnerType);
        }
    }

    private void setDeviceOwnerTypeLocked(ComponentName admin,
            @DeviceOwnerType int deviceOwnerType) {
        String packageName = admin.getPackageName();
        boolean isAdminTestOnly;

        verifyDeviceOwnerTypePreconditionsLocked(admin);

        isAdminTestOnly = isAdminTestOnlyLocked(admin, mOwners.getDeviceOwnerUserId());
        Preconditions.checkState(isAdminTestOnly
                        || !mOwners.isDeviceOwnerTypeSetForDeviceOwner(packageName),
                "Test only admins can only set the device owner type more than once");

        mOwners.setDeviceOwnerType(packageName, deviceOwnerType, isAdminTestOnly);
        setGlobalSettingDeviceOwnerType(deviceOwnerType);
    }

    // TODO(b/237065504): Allow mainline modules to get the device owner type. This is a workaround
    // to get the device owner type in PermissionController. See HibernationPolicy.kt.
    private void setGlobalSettingDeviceOwnerType(int deviceOwnerType) {
        mInjector.binderWithCleanCallingIdentity(
                () -> mInjector.settingsGlobalPutInt("device_owner_type", deviceOwnerType));
    }

    @Override
    @DeviceOwnerType
    public int getDeviceOwnerType(@NonNull ComponentName admin) {
        synchronized (getLockObject()) {
            verifyDeviceOwnerTypePreconditionsLocked(admin);
            return getDeviceOwnerTypeLocked(admin.getPackageName());
        }
    }

    @DeviceOwnerType
    private int getDeviceOwnerTypeLocked(String packageName) {
        return mOwners.getDeviceOwnerType(packageName);
    }

    /**
     * {@code true} is returned <b>only if</b> the caller is the device owner and the device owner
     * type is {@link DevicePolicyManager#DEVICE_OWNER_TYPE_FINANCED}. {@code false} is returned for
     * the case where the caller is not the device owner, there is no device owner, or the device
     * owner type is not {@link DevicePolicyManager#DEVICE_OWNER_TYPE_FINANCED}.
     */
    private boolean isFinancedDeviceOwner(CallerIdentity caller) {
        synchronized (getLockObject()) {
            return isDeviceOwnerLocked(caller) && getDeviceOwnerTypeLocked(
                    mOwners.getDeviceOwnerPackageName()) == DEVICE_OWNER_TYPE_FINANCED;
        }
    }

    private void verifyDeviceOwnerTypePreconditionsLocked(@NonNull ComponentName admin) {
        Preconditions.checkState(mOwners.hasDeviceOwner(), "there is no device owner");
        Preconditions.checkState(mOwners.getDeviceOwnerComponent().equals(admin),
                "admin is not the device owner");
    }

    @Override
    public void setUsbDataSignalingEnabled(String packageName, boolean enabled) {
        Objects.requireNonNull(packageName, "Admin package name must be provided");
        final CallerIdentity caller = getCallerIdentity(packageName);
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwnerOfOrganizationOwnedDevice(caller),
                "USB data signaling can only be controlled by a device owner or "
                        + "a profile owner on an organization-owned device.");
        Preconditions.checkState(canUsbDataSignalingBeDisabled(),
                "USB data signaling cannot be disabled.");

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            if (admin.mUsbDataSignalingEnabled != enabled) {
                admin.mUsbDataSignalingEnabled = enabled;
                saveSettingsLocked(caller.getUserId());
                updateUsbDataSignal();
            }
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.SET_USB_DATA_SIGNALING)
                .setAdmin(packageName)
                .setBoolean(enabled)
                .write();
    }

    private void updateUsbDataSignal() {
        if (!canUsbDataSignalingBeDisabled()) {
            return;
        }
        final boolean usbEnabled;
        synchronized (getLockObject()) {
            usbEnabled = isUsbDataSignalingEnabledInternalLocked();
        }
        if (!mInjector.binderWithCleanCallingIdentity(
                () -> mInjector.getUsbManager().enableUsbDataSignal(usbEnabled))) {
            Slogf.w(LOG_TAG, "Failed to set usb data signaling state");
        }
    }

    @Override
    public boolean isUsbDataSignalingEnabled(String packageName) {
        final CallerIdentity caller = getCallerIdentity(packageName);
        synchronized (getLockObject()) {
            // If the caller is an admin, return the policy set by itself. Otherwise
            // return the device-wide policy.
            if (isDefaultDeviceOwner(caller) || isProfileOwnerOfOrganizationOwnedDevice(caller)) {
                return getProfileOwnerOrDeviceOwnerLocked(
                        caller.getUserId()).mUsbDataSignalingEnabled;
            } else {
                return isUsbDataSignalingEnabledInternalLocked();
            }
        }
    }

    @Override
    public boolean isUsbDataSignalingEnabledForUser(int userId) {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(isSystemUid(caller));

        synchronized (getLockObject()) {
            return isUsbDataSignalingEnabledInternalLocked();
        }
    }

    private boolean isUsbDataSignalingEnabledInternalLocked() {
        // TODO(b/261999445): remove
        ActiveAdmin admin;
        if (isHeadlessFlagEnabled()) {
            admin = getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked();
        } else {
            admin = getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked(
                    UserHandle.USER_SYSTEM);
        }
        return admin == null || admin.mUsbDataSignalingEnabled;
    }

    @Override
    public boolean canUsbDataSignalingBeDisabled() {
        return mInjector.binderWithCleanCallingIdentity(() ->
                mInjector.getUsbManager() != null
                        && mInjector.getUsbManager().getUsbHalVersion() >= UsbManager.USB_HAL_V1_3
        );
    }

    private void notifyMinimumRequiredWifiSecurityLevelChanged(int level) {
        mInjector.binderWithCleanCallingIdentity(
                () -> mInjector.getWifiManager()
                        .notifyMinimumRequiredWifiSecurityLevelChanged(level));
    }

    private void notifyWifiSsidPolicyChanged(WifiSsidPolicy policy) {
        if (policy == null) {
            // If policy doesn't limit SSIDs, no need to disconnect anything.
            return;
        }
        mInjector.binderWithCleanCallingIdentity(
                () -> mInjector.getWifiManager().notifyWifiSsidPolicyChanged(policy));
    }

    @Override
    public void setMinimumRequiredWifiSecurityLevel(int level) {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwnerOfOrganizationOwnedDevice(caller),
                "Wi-Fi minimum security level can only be controlled by a device owner or "
                        + "a profile owner on an organization-owned device.");

        boolean valueChanged = false;
        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            if (admin.mWifiMinimumSecurityLevel != level) {
                admin.mWifiMinimumSecurityLevel = level;
                saveSettingsLocked(caller.getUserId());
                valueChanged = true;
            }
        }
        if (valueChanged) notifyMinimumRequiredWifiSecurityLevelChanged(level);
    }

    @Override
    public int getMinimumRequiredWifiSecurityLevel() {
        synchronized (getLockObject()) {
            ActiveAdmin admin;
            // TODO(b/261999445): remove
            if (isHeadlessFlagEnabled()) {
                admin = getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked();
            } else {
                admin = getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked(
                        UserHandle.USER_SYSTEM);
            }
            return (admin == null) ? DevicePolicyManager.WIFI_SECURITY_OPEN
                    : admin.mWifiMinimumSecurityLevel;
        }
    }

    @Override
    public WifiSsidPolicy getWifiSsidPolicy() {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwnerOfOrganizationOwnedDevice(caller)
                        || canQueryAdminPolicy(caller),
                "SSID policy can only be retrieved by a device owner or "
                        + "a profile owner on an organization-owned device or "
                        + "an app with the QUERY_ADMIN_POLICY permission.");
        synchronized (getLockObject()) {
            ActiveAdmin admin;
            // TODO(b/261999445): remove
            if (isHeadlessFlagEnabled()) {
                admin = getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked();
            } else {
                admin = getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked(
                        UserHandle.USER_SYSTEM);
            }
            return admin != null ? admin.mWifiSsidPolicy : null;
        }
    }

    @Override
    public void setWifiSsidPolicy(WifiSsidPolicy policy) {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller) || isProfileOwnerOfOrganizationOwnedDevice(caller),
                "SSID denylist can only be controlled by a device owner or "
                        + "a profile owner on an organization-owned device.");

        boolean changed = false;
        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerOrDeviceOwnerLocked(caller.getUserId());
            if (!Objects.equals(policy, admin.mWifiSsidPolicy)) {
                admin.mWifiSsidPolicy = policy;
                changed = true;
            }
            if (changed) saveSettingsLocked(caller.getUserId());
        }
        if (changed) {
            notifyWifiSsidPolicyChanged(policy);
        }
    }

    @Override
    public void setDrawables(@NonNull List<DevicePolicyDrawableResource> drawables) {
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(
                android.Manifest.permission.UPDATE_DEVICE_MANAGEMENT_RESOURCES));

        Objects.requireNonNull(drawables, "drawables must be provided.");

        mInjector.binderWithCleanCallingIdentity(() -> {
            if (mDeviceManagementResourcesProvider.updateDrawables(drawables)) {
                sendDrawableUpdatedBroadcast(
                        drawables.stream().map(s -> s.getDrawableId()).collect(
                                Collectors.toList()));
            }
        });
    }

    @Override
    public void resetDrawables(@NonNull List<String> drawableIds) {
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(
                android.Manifest.permission.UPDATE_DEVICE_MANAGEMENT_RESOURCES));

        Objects.requireNonNull(drawableIds, "drawableIds must be provided.");

        mInjector.binderWithCleanCallingIdentity(() -> {
            if (mDeviceManagementResourcesProvider.removeDrawables(drawableIds)) {
                sendDrawableUpdatedBroadcast(drawableIds);
            }
        });
    }

    @Override
    public ParcelableResource getDrawable(
            String drawableId, String drawableStyle, String drawableSource) {
        return mInjector.binderWithCleanCallingIdentity(() ->
                mDeviceManagementResourcesProvider.getDrawable(
                        drawableId, drawableStyle, drawableSource));
    }

    private void sendDrawableUpdatedBroadcast(List<String> drawableIds) {
        sendResourceUpdatedBroadcast(EXTRA_RESOURCE_TYPE_DRAWABLE, drawableIds);
    }

    @Override
    public void setStrings(@NonNull List<DevicePolicyStringResource> strings) {
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(
                android.Manifest.permission.UPDATE_DEVICE_MANAGEMENT_RESOURCES));

        Objects.requireNonNull(strings, "strings must be provided.");

        mInjector.binderWithCleanCallingIdentity(() -> {
            if (mDeviceManagementResourcesProvider.updateStrings(strings)) {
                sendStringsUpdatedBroadcast(
                        strings.stream().map(s -> s.getStringId()).collect(Collectors.toList()));
            }
        });
    }

    @Override
    public void resetStrings(@NonNull List<String> stringIds) {
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(
                android.Manifest.permission.UPDATE_DEVICE_MANAGEMENT_RESOURCES));

        mInjector.binderWithCleanCallingIdentity(() -> {
            if (mDeviceManagementResourcesProvider.removeStrings(stringIds)) {
                sendStringsUpdatedBroadcast(stringIds);
            }
        });
    }

    @Override
    public ParcelableResource getString(String stringId) {
        return mInjector.binderWithCleanCallingIdentity(() ->
                mDeviceManagementResourcesProvider.getString(stringId));
    }

    private void sendStringsUpdatedBroadcast(List<String> stringIds) {
        sendResourceUpdatedBroadcast(EXTRA_RESOURCE_TYPE_STRING, stringIds);
    }

    private void sendResourceUpdatedBroadcast(int resourceType, List<String> resourceIds) {
        final Intent intent = new Intent(ACTION_DEVICE_POLICY_RESOURCE_UPDATED);
        intent.putExtra(EXTRA_RESOURCE_IDS, resourceIds.toArray(String[]::new));
        intent.putExtra(EXTRA_RESOURCE_TYPE, resourceType);
        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);

        List<UserInfo> users = mUserManager.getAliveUsers();
        for (int i = 0; i < users.size(); i++) {
            UserHandle user = users.get(i).getUserHandle();
            mContext.sendBroadcastAsUser(intent, user);
        }
    }

    private String getUpdatableString(
            String updatableStringId, int defaultStringId, Object... formatArgs) {
        ParcelableResource resource = mDeviceManagementResourcesProvider.getString(
                updatableStringId);
        if (resource == null) {
            return ParcelableResource.loadDefaultString(() ->
                    mContext.getString(defaultStringId, formatArgs));
        }
        return resource.getString(
                mContext, () -> mContext.getString(defaultStringId, formatArgs), formatArgs);
    }

    public boolean isDpcDownloaded() {
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        ContentResolver cr = mContext.getContentResolver();

        return mInjector.binderWithCleanCallingIdentity(() -> Settings.Secure.getIntForUser(
                cr, MANAGED_PROVISIONING_DPC_DOWNLOADED,
                /* def= */ 0, /* userHandle= */ cr.getUserId())
                == 1);
    }

    public void setDpcDownloaded(boolean downloaded) {
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));

        int setTo = downloaded ? 1 : 0;

        mInjector.binderWithCleanCallingIdentity(() -> Settings.Secure.putInt(
                mContext.getContentResolver(), MANAGED_PROVISIONING_DPC_DOWNLOADED, setTo));
    }

    @Override
    public void resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState() {
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_ROLE_HOLDERS));
        setBypassDevicePolicyManagementRoleQualificationStateInternal(
                /* currentRoleHolder= */ null, /* allowBypass= */ false);
    }

    @Override
    public boolean shouldAllowBypassingDevicePolicyManagementRoleQualification() {
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_ROLE_HOLDERS));
        return mInjector.binderWithCleanCallingIdentity(() -> {
            if (getUserData(
                    UserHandle.USER_SYSTEM).mBypassDevicePolicyManagementRoleQualifications) {
                return true;
            }
            return shouldAllowBypassingDevicePolicyManagementRoleQualificationInternal();
        });
    }

    private boolean shouldAllowBypassingDevicePolicyManagementRoleQualificationInternal() {
        if (nonTestNonPrecreatedUsersExist()) {
            return false;
        }


        return !hasIncompatibleAccountsOnAnyUser();
    }

    private boolean hasAccountsOnAnyUser() {
        long callingIdentity = Binder.clearCallingIdentity();
        try {
            for (UserInfo user : mUserManagerInternal.getUsers(/* excludeDying= */ true)) {
                AccountManager am = mContext.createContextAsUser(
                                UserHandle.of(user.id), /* flags= */ 0)
                        .getSystemService(AccountManager.class);
                Account[] accounts = am.getAccounts();
                if (accounts.length != 0) {
                    return true;
                }
            }

            return false;
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    private boolean hasIncompatibleAccountsOnAnyUser() {
        long callingIdentity = Binder.clearCallingIdentity();
        try {
            for (UserInfo user : mUserManagerInternal.getUsers(/* excludeDying= */ true)) {
                AccountManager am = mContext.createContextAsUser(
                        UserHandle.of(user.id), /* flags= */ 0)
                        .getSystemService(AccountManager.class);
                Account[] accounts = am.getAccounts();

                if (hasIncompatibleAccounts(am, accounts)) {
                    return true;
                }
            }

            return false;
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    private void setBypassDevicePolicyManagementRoleQualificationStateInternal(
            String currentRoleHolder, boolean allowBypass) {
        boolean stateChanged = false;
        DevicePolicyData policy = getUserData(UserHandle.USER_SYSTEM);
        if (policy.mBypassDevicePolicyManagementRoleQualifications != allowBypass) {
            policy.mBypassDevicePolicyManagementRoleQualifications = allowBypass;
            stateChanged = true;
        }
        if (!Objects.equals(currentRoleHolder, policy.mCurrentRoleHolder)) {
            policy.mCurrentRoleHolder = currentRoleHolder;
            stateChanged = true;
        }
        if (stateChanged) {
            synchronized (getLockObject()) {
                saveSettingsLocked(UserHandle.USER_SYSTEM);
            }
        }
    }

    private final class DevicePolicyManagementRoleObserver implements OnRoleHoldersChangedListener {
        private RoleManager mRm;
        private final Executor mExecutor;
        private final Context mContext;

        DevicePolicyManagementRoleObserver(@NonNull Context context) {
            mContext = context;
            mExecutor = mContext.getMainExecutor();
            mRm = mContext.getSystemService(RoleManager.class);
        }

        public void register() {
            mRm.addOnRoleHoldersChangedListenerAsUser(mExecutor, this, UserHandle.SYSTEM);
        }

        @Override
        public void onRoleHoldersChanged(@NonNull String roleName, @NonNull UserHandle user) {
            if (!RoleManager.ROLE_DEVICE_POLICY_MANAGEMENT.equals(roleName)) {
                return;
            }
            String newRoleHolder = getRoleHolder();
            if (isDefaultRoleHolder(newRoleHolder)) {
                Slogf.i(LOG_TAG,
                        "onRoleHoldersChanged: Default role holder is set, returning early");
                return;
            }
            if (newRoleHolder == null) {
                Slogf.i(LOG_TAG,
                        "onRoleHoldersChanged: New role holder is null, returning early");
                return;
            }
            if (shouldAllowBypassingDevicePolicyManagementRoleQualificationInternal()) {
                Slogf.w(LOG_TAG,
                        "onRoleHoldersChanged: Updating current role holder to " + newRoleHolder);
                setBypassDevicePolicyManagementRoleQualificationStateInternal(
                        newRoleHolder, /* allowBypass= */ true);
                return;
            }
            DevicePolicyData policy = getUserData(UserHandle.USER_SYSTEM);
            if (!newRoleHolder.equals(policy.mCurrentRoleHolder)) {
                Slogf.w(LOG_TAG,
                        "onRoleHoldersChanged: You can't set a different role holder, role "
                                + "is getting revoked from " + newRoleHolder);
                setBypassDevicePolicyManagementRoleQualificationStateInternal(
                        /* currentRoleHolder= */ null, /* allowBypass= */ false);
                mRm.removeRoleHolderAsUser(
                        RoleManager.ROLE_DEVICE_POLICY_MANAGEMENT,
                        newRoleHolder,
                        /* flags= */ 0,
                        user,
                        mExecutor,
                        successful -> {});
            }
        }

        private String getRoleHolder() {
            return DevicePolicyManagerService.this.getDevicePolicyManagementRoleHolderPackageName(
                    mContext);
        }

        private boolean isDefaultRoleHolder(String packageName) {
            String defaultRoleHolder = getDefaultRoleHolderPackageName();
            if (packageName == null || defaultRoleHolder == null) {
                return false;
            }
            if (!defaultRoleHolder.equals(packageName)) {
                return false;
            }
            return hasSigningCertificate(
                    packageName, getDefaultRoleHolderPackageSignature());
        }

        private boolean hasSigningCertificate(String packageName, String  certificateString) {
            if (packageName == null || certificateString == null) {
                return false;
            }
            byte[] certificate;
            try {
                certificate = new Signature(certificateString).toByteArray();
            } catch (IllegalArgumentException e) {
                Slogf.w(LOG_TAG, "Cannot parse signing certificate: " + certificateString, e);
                return false;
            }
            PackageManager pm = mInjector.getPackageManager();
            return pm.hasSigningCertificate(
                    packageName, certificate, PackageManager.CERT_INPUT_SHA256);
        }

        private String getDefaultRoleHolderPackageName() {
            String[] info = getDefaultRoleHolderPackageNameAndSignature();
            if (info == null) {
                return null;
            }
            return info[0];
        }

        private String getDefaultRoleHolderPackageSignature() {
            String[] info = getDefaultRoleHolderPackageNameAndSignature();
            if (info == null || info.length < 2) {
                return null;
            }
            return info[1];
        }

        private String[] getDefaultRoleHolderPackageNameAndSignature() {
            String packageNameAndSignature = mContext.getString(
                    com.android.internal.R.string.config_devicePolicyManagement);
            if (TextUtils.isEmpty(packageNameAndSignature)) {
                return null;
            }
            if (packageNameAndSignature.contains(":")) {
                return packageNameAndSignature.split(":");
            }
            return new String[]{packageNameAndSignature};
        }
    }

    @Override
    public List<UserHandle> getPolicyManagedProfiles(@NonNull UserHandle user) {
        Preconditions.checkCallAuthorization(hasCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_PROFILE_AND_DEVICE_OWNERS));
        int userId = user.getIdentifier();
        return mInjector.binderWithCleanCallingIdentity(() -> {
            List<UserInfo> userProfiles = mUserManager.getProfiles(userId);
            List<UserHandle> result = new ArrayList<>();
            for (int i = 0; i < userProfiles.size(); i++) {
                UserInfo userInfo = userProfiles.get(i);
                if (userInfo.isManagedProfile() && hasProfileOwner(userInfo.id)) {
                    result.add(new UserHandle(userInfo.id));
                }
            }
            return result;
        });
    }

    // DPC types
    private static final int DEFAULT_DEVICE_OWNER = 0;
    private static final int FINANCED_DEVICE_OWNER = 1;
    private static final int PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE = 2;
    private static final int PROFILE_OWNER_ON_USER_0 = 3;
    private static final int PROFILE_OWNER = 4;

    // Permissions of existing DPC types.
    private static final List<String> DEFAULT_DEVICE_OWNER_PERMISSIONS = List.of(
            MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL,
            MANAGE_DEVICE_POLICY_ACROSS_USERS,
            MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL,
            SET_TIME,
            SET_TIME_ZONE);
    private static final List<String> FINANCED_DEVICE_OWNER_PERMISSIONS = List.of(
            MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL,
            MANAGE_DEVICE_POLICY_ACROSS_USERS,
            MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL);
    private static final List<String> PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE_PERMISSIONS =
            List.of(
                MANAGE_DEVICE_POLICY_ACROSS_USERS,
                MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL,
                SET_TIME,
                SET_TIME_ZONE);
    private static final List<String> PROFILE_OWNER_ON_USER_0_PERMISSIONS  = List.of(
            SET_TIME,
            SET_TIME_ZONE);
    private static final List<String> PROFILE_OWNER_PERMISSIONS  = List.of(
            MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL);

    private static final HashMap<Integer, List<String>> DPC_PERMISSIONS = new HashMap<>();
    {
        DPC_PERMISSIONS.put(DEFAULT_DEVICE_OWNER, DEFAULT_DEVICE_OWNER_PERMISSIONS);
        DPC_PERMISSIONS.put(FINANCED_DEVICE_OWNER, FINANCED_DEVICE_OWNER_PERMISSIONS);
        DPC_PERMISSIONS.put(PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE,
                PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE_PERMISSIONS);
        DPC_PERMISSIONS.put(PROFILE_OWNER_ON_USER_0, PROFILE_OWNER_ON_USER_0_PERMISSIONS);
        DPC_PERMISSIONS.put(PROFILE_OWNER, PROFILE_OWNER_PERMISSIONS);
    }

    //TODO(b/254253251) Fill this map in as new permissions are added for policies.
    private static final HashMap<String, Integer> ACTIVE_ADMIN_POLICIES = new HashMap<>();

    private static final HashMap<String, String> CROSS_USER_PERMISSIONS =
            new HashMap<>();
    {
        // Auto time is intrinsically global so there is no cross-user permission.
        CROSS_USER_PERMISSIONS.put(SET_TIME, null);
        CROSS_USER_PERMISSIONS.put(SET_TIME_ZONE, null);
    }

    /**
     * Checks if the calling process has been granted permission to apply a device policy on a
     * specific user.
     * The given permission will be checked along with its associated cross-user permission if it
     * exists and the target user is different to the calling user.
     *
     * @param permission The name of the permission being checked.
     * @param targetUserId The userId of the user which the caller needs permission to act on.
     * @throws SecurityException if the caller has not been granted the given permission,
     * the associtated cross-user permission if the caller's user is different to the target user.
     */
    private void enforcePermission(String permission, int targetUserId)
            throws SecurityException {
        if (!hasPermission(permission, targetUserId)) {
            throw new SecurityException("Caller does not have the required permissions for "
                    + "this user. Permissions required: {"
                    + permission
                    + ", "
                    + CROSS_USER_PERMISSIONS.get(permission)
                    + "}");
        }
    }

    /**
     * Return whether the calling process has been granted permission to query a device policy on
     * a specific user.
     *
     * @param permission The name of the permission being checked.
     * @param targetUserId The userId of the user which the caller needs permission to act on.
     * @throws SecurityException if the caller has not been granted the given permission,
     * the associatated cross-user permission if the caller's user is different to the target user
     * and if the user has not been granted {@link QUERY_ADMIN_POLICY}.
     */
    private void enforceCanQuery(String permission, int targetUserId) throws SecurityException {
        if (hasPermission(QUERY_ADMIN_POLICY)) {
            return;
        }
        enforcePermission(permission, targetUserId);
    }

    /**
     * Return whether the calling process has been granted permission to apply a device policy on
     * a specific user.
     *
     * @param permission The name of the permission being checked.
     * @param targetUserId The userId of the user which the caller needs permission to act on.
     */
    private boolean hasPermission(String permission, int targetUserId) {
        boolean hasPermissionOnOwnUser = hasPermission(permission);
        boolean hasPermissionOnTargetUser = true;
        if (hasPermissionOnOwnUser & getCallerIdentity().getUserId() != targetUserId) {
            hasPermissionOnTargetUser = hasPermission(CROSS_USER_PERMISSIONS.get(permission));
        }
        return hasPermissionOnOwnUser && hasPermissionOnTargetUser;
    }

    /**
     * Return whether the calling process has been granted the given permission.
     *
     * @param permission The name of the permission being checked.
     */
    private boolean hasPermission(String permission) {
        if (permission == null) {
            return true;
        }

        CallerIdentity caller = getCallerIdentity();

        // Check if the caller holds the permission
        if (mContext.checkCallingOrSelfPermission(permission) == PERMISSION_GRANTED) {
            return true;
        }
        // Check the permissions of DPCs
        if (isDefaultDeviceOwner(caller)) {
            return DPC_PERMISSIONS.get(DEFAULT_DEVICE_OWNER).contains(permission);
        }
        if (isFinancedDeviceOwner(caller)) {
            return DPC_PERMISSIONS.get(FINANCED_DEVICE_OWNER).contains(permission);
        }
        if (isProfileOwnerOfOrganizationOwnedDevice(caller)) {
            return DPC_PERMISSIONS.get(PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE).contains(
                    permission);
        }
        if (isProfileOwnerOnUser0(caller)) {
            return DPC_PERMISSIONS.get(PROFILE_OWNER_ON_USER_0).contains(permission);
        }
        if (isProfileOwner(caller)) {
            return DPC_PERMISSIONS.get(PROFILE_OWNER).contains(permission);
        }
        // Check the permission for the role-holder
        if (isDevicePolicyManagementRoleHolder(caller)) {
            return anyDpcHasPermission(permission, mContext.getUserId());
        }
        // Check if the caller is an active admin that uses a certain policy.
        if (ACTIVE_ADMIN_POLICIES.containsKey(permission)) {
            return getActiveAdminForCallerLocked(
                    null, ACTIVE_ADMIN_POLICIES.get(permission), false) != null;
        }

        return false;
    }

    /**
     * Returns whether there is a DPC on the given user that has been granted the given permission.
     *
     * @param permission The name of the permission being checked.
     * @param userId The id of the user to check.
     */
    private boolean anyDpcHasPermission(String permission, int userId) {
        if (mOwners.isDefaultDeviceOwnerUserId(userId)) {
            return DPC_PERMISSIONS.get(DEFAULT_DEVICE_OWNER).contains(permission);
        }
        if (mOwners.isFinancedDeviceOwnerUserId(userId)) {
            return DPC_PERMISSIONS.get(FINANCED_DEVICE_OWNER).contains(permission);
        }
        if (mOwners.isProfileOwnerOfOrganizationOwnedDevice(userId)) {
            return DPC_PERMISSIONS.get(PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE).contains(
                    permission);
        }
        if (userId == 0 && mOwners.hasProfileOwner(0)) {
            return DPC_PERMISSIONS.get(PROFILE_OWNER_ON_USER_0).contains(permission);
        }
        if (mOwners.hasProfileOwner(userId)) {
            return DPC_PERMISSIONS.get(PROFILE_OWNER).contains(permission);
        }
        return false;
    }

    private boolean isPermissionCheckFlagEnabled() {
        return DeviceConfig.getBoolean(
                NAMESPACE_DEVICE_POLICY_MANAGER,
                PERMISSION_BASED_ACCESS_EXPERIMENT_FLAG,
                DEFAULT_VALUE_PERMISSION_BASED_ACCESS_FLAG);
    }

    // TODO(b/260560985): properly gate coexistence changes
    private boolean isCoexistenceEnabled(CallerIdentity caller) {
        return isCoexistenceFlagEnabled()
                && mInjector.isChangeEnabled(
                        ENABLE_COEXISTENCE_CHANGE, caller.getPackageName(), caller.getUserId());
    }

    private boolean isCoexistenceFlagEnabled() {
        return DeviceConfig.getBoolean(
                NAMESPACE_DEVICE_POLICY_MANAGER,
                ENABLE_COEXISTENCE_FLAG,
                DEFAULT_ENABLE_COEXISTENCE_FLAG);
    }

    private static boolean isKeepProfilesRunningFlagEnabled() {
        return DeviceConfig.getBoolean(
                NAMESPACE_DEVICE_POLICY_MANAGER,
                KEEP_PROFILES_RUNNING_FLAG,
                DEFAULT_KEEP_PROFILES_RUNNING_FLAG);
    }

    private static boolean isWorkProfileTelephonyFlagEnabled() {
        return DeviceConfig.getBoolean(
                NAMESPACE_DEVICE_POLICY_MANAGER,
                ENABLE_WORK_PROFILE_TELEPHONY_FLAG,
                DEFAULT_WORK_PROFILE_TELEPHONY_FLAG);
    }

    @Override
    public void setMtePolicy(int flags) {
        final Set<Integer> allowedModes =
                Set.of(
                        DevicePolicyManager.MTE_NOT_CONTROLLED_BY_POLICY,
                        DevicePolicyManager.MTE_DISABLED,
                        DevicePolicyManager.MTE_ENABLED);
        Preconditions.checkArgument(
                allowedModes.contains(flags), "Provided mode is not one of the allowed values.");
        final CallerIdentity caller = getCallerIdentity();
        if (flags == DevicePolicyManager.MTE_DISABLED) {
            Preconditions.checkCallAuthorization(isDefaultDeviceOwner(caller));
        } else {
            Preconditions.checkCallAuthorization(
                    isDefaultDeviceOwner(caller)
                            || isProfileOwnerOfOrganizationOwnedDevice(caller));
        }
        synchronized (getLockObject()) {
            // TODO(b/261999445): Remove
            ActiveAdmin admin;
            if (isHeadlessFlagEnabled()) {
                admin =
                        getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked();
            } else {
                admin =
                        getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked(
                                UserHandle.USER_SYSTEM);
            }

            if (admin != null) {
                final String memtagProperty = "arm64.memtag.bootctl";
                if (flags == DevicePolicyManager.MTE_ENABLED) {
                    mInjector.systemPropertiesSet(memtagProperty, "memtag");
                } else if (flags == DevicePolicyManager.MTE_DISABLED) {
                    mInjector.systemPropertiesSet(memtagProperty, "memtag-off");
                }
                admin.mtePolicy = flags;
                saveSettingsLocked(caller.getUserId());

                DevicePolicyEventLogger.createEvent(DevicePolicyEnums.SET_MTE_POLICY)
                        .setInt(flags)
                        .setAdmin(admin.info.getPackageName())
                        .write();
            }
        }
    }

    @Override
    public int getMtePolicy() {
        final CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(
                isDefaultDeviceOwner(caller)
                        || isProfileOwnerOfOrganizationOwnedDevice(caller)
                        || isSystemUid(caller));
        synchronized (getLockObject()) {
            // TODO(b/261999445): Remove
            ActiveAdmin admin;
            if (isHeadlessFlagEnabled()) {
                admin =
                        getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked();
            } else {
                admin =
                        getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDeviceLocked(
                                UserHandle.USER_SYSTEM);
            }
            return admin != null
                    ? admin.mtePolicy
                    : DevicePolicyManager.MTE_NOT_CONTROLLED_BY_POLICY;
        }
    }

    private boolean isHeadlessFlagEnabled() {
        return DeviceConfig.getBoolean(
                NAMESPACE_DEVICE_POLICY_MANAGER,
                HEADLESS_FLAG,
                DEFAULT_HEADLESS_FLAG);
    }

    @Override
    public ManagedSubscriptionsPolicy getManagedSubscriptionsPolicy() {
        if (isWorkProfileTelephonyFlagEnabled()) {
            synchronized (getLockObject()) {
                ActiveAdmin admin = getProfileOwnerOfOrganizationOwnedDeviceLocked();
                if (admin != null && admin.mManagedSubscriptionsPolicy != null) {
                    return admin.mManagedSubscriptionsPolicy;
                }
            }
        }
        return new ManagedSubscriptionsPolicy(
                ManagedSubscriptionsPolicy.TYPE_ALL_PERSONAL_SUBSCRIPTIONS);
    }

    @Override
    public void setManagedSubscriptionsPolicy(ManagedSubscriptionsPolicy policy) {
        if (!isWorkProfileTelephonyFlagEnabled()) {
            throw new UnsupportedOperationException("This api is not enabled");
        }
        CallerIdentity caller = getCallerIdentity();
        Preconditions.checkCallAuthorization(isProfileOwnerOfOrganizationOwnedDevice(caller),
                "This policy can only be set by a profile owner on an organization-owned "
                        + "device.");

        synchronized (getLockObject()) {
            final ActiveAdmin admin = getProfileOwnerLocked(caller.getUserId());
            if (hasUserSetupCompleted(UserHandle.USER_SYSTEM) && !isAdminTestOnlyLocked(
                    admin.info.getComponent(), caller.getUserId())) {
                throw new IllegalStateException("Not allowed to apply this policy after setup");
            }
            boolean changed = false;
            if (!Objects.equals(policy, admin.mManagedSubscriptionsPolicy)) {
                admin.mManagedSubscriptionsPolicy = policy;
                changed = true;
            }
            if (changed) {
                saveSettingsLocked(caller.getUserId());
            } else {
                return;
            }
        }

        applyManagedSubscriptionsPolicyIfRequired();

        int policyType = getManagedSubscriptionsPolicy().getPolicyType();
        if (policyType == ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS) {
            final long id = mInjector.binderClearCallingIdentity();
            try {
                int parentUserId = getProfileParentId(caller.getUserId());
                installOemDefaultDialerAndMessagesApp(parentUserId, caller.getUserId());
                updateTelephonyCrossProfileIntentFilters(parentUserId, caller.getUserId(), true);
            } finally {
                mInjector.binderRestoreCallingIdentity(id);
            }
        }
    }

    private void installOemDefaultDialerAndMessagesApp(int sourceUserId, int targetUserId) {
        try {
            UserHandle sourceUserHandle = UserHandle.of(sourceUserId);
            TelecomManager telecomManager = mContext.getSystemService(TelecomManager.class);
            String dialerAppPackage = telecomManager.getDefaultDialerPackage(
                    sourceUserHandle);
            String messagesAppPackage = SmsApplication.getDefaultSmsApplicationAsUser(mContext,
                    true, sourceUserHandle).getPackageName();
            if (dialerAppPackage != null) {
                mIPackageManager.installExistingPackageAsUser(dialerAppPackage, targetUserId,
                        PackageManager.INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS,
                        PackageManager.INSTALL_REASON_POLICY, null);
            } else {
                Slogf.w(LOG_TAG, "Couldn't install dialer app, dialer app package is null");
            }

            if (messagesAppPackage != null) {
                mIPackageManager.installExistingPackageAsUser(messagesAppPackage, targetUserId,
                        PackageManager.INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS,
                        PackageManager.INSTALL_REASON_POLICY, null);
            } else {
                Slogf.w(LOG_TAG, "Couldn't install messages app, messages app package is null");
            }
        } catch (RemoteException re) {
            // shouldn't happen
            Slogf.wtf(LOG_TAG, "Failed to install dialer/messages app", re);
        }
    }

    private void registerListenerToAssignSubscriptionsToUser(int userId) {
        synchronized (mSubscriptionsChangedListenerLock) {
            if (mSubscriptionsChangedListener != null) {
                return;
            }
            SubscriptionManager subscriptionManager = mContext.getSystemService(
                    SubscriptionManager.class);
            // Listener to assign all current and future subs to managed profile.
            mSubscriptionsChangedListener = new SubscriptionManager.OnSubscriptionsChangedListener(
                    mHandler.getLooper()) {
                @Override
                public void onSubscriptionsChanged() {
                    final long id = mInjector.binderClearCallingIdentity();
                    try {
                        int[] subscriptionIds = subscriptionManager.getActiveSubscriptionIdList(
                                false);
                        for (int subId : subscriptionIds) {
                            UserHandle associatedUserHandle =
                                    subscriptionManager.getSubscriptionUserHandle(subId);
                            if (associatedUserHandle == null
                                    || associatedUserHandle.getIdentifier() != userId) {
                                subscriptionManager.setSubscriptionUserHandle(subId,
                                        UserHandle.of(userId));
                            }
                        }
                    } finally {
                        mInjector.binderRestoreCallingIdentity(id);
                    }
                }
            };

            final long id = mInjector.binderClearCallingIdentity();
            try {
                // When listener is added onSubscriptionsChanged gets called immediately for once
                // (even if subscriptions are not changed) and later on when subscriptions changes.
                subscriptionManager.addOnSubscriptionsChangedListener(
                        mSubscriptionsChangedListener.getHandlerExecutor(),
                        mSubscriptionsChangedListener);
            } finally {
                mInjector.binderRestoreCallingIdentity(id);
            }
        }
    }

    private void unregisterOnSubscriptionsChangedListener() {
        synchronized (mSubscriptionsChangedListenerLock) {
            if (mSubscriptionsChangedListener != null) {
                SubscriptionManager subscriptionManager = mContext.getSystemService(
                        SubscriptionManager.class);
                subscriptionManager.removeOnSubscriptionsChangedListener(
                        mSubscriptionsChangedListener);
                mSubscriptionsChangedListener = null;
            }
        }
    }
}
