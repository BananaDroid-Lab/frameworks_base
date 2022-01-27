package com.android.server.usage;

import android.annotation.CurrentTimeMillisLong;
import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.usage.AppStandbyInfo;
import android.app.usage.UsageStatsManager.ForcedReasons;
import android.app.usage.UsageStatsManager.StandbyBuckets;
import android.content.Context;
import android.util.IndentingPrintWriter;

import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;

public interface AppStandbyInternal {
    /**
     * TODO AppStandbyController should probably be a binder service, and then we shouldn't need
     * this method.
     */
    static AppStandbyInternal newAppStandbyController(ClassLoader loader, Context context) {
        try {
            final Class<?> clazz = Class.forName("com.android.server.usage.AppStandbyController",
                    true, loader);
            final Constructor<?> ctor = clazz.getConstructor(Context.class);
            return (AppStandbyInternal) ctor.newInstance(context);
        } catch (NoSuchMethodException | InstantiationException
                | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            throw new RuntimeException("Unable to instantiate AppStandbyController!", e);
        }
    }

    /**
     * Listener interface for notifications that an app's idle state changed.
     */
    abstract static class AppIdleStateChangeListener {

        /** Callback to inform listeners that the idle state has changed to a new bucket. */
        public abstract void onAppIdleStateChanged(String packageName, @UserIdInt int userId,
                boolean idle, int bucket, int reason);

        /**
         * Callback to inform listeners that the parole state has changed. This means apps are
         * allowed to do work even if they're idle or in a low bucket.
         */
        public void onParoleStateChanged(boolean isParoleOn) {
            // No-op by default
        }

        /**
         * Optional callback to inform the listener that the app has transitioned into
         * an active state due to user interaction.
         */
        public void onUserInteractionStarted(String packageName, @UserIdInt int userId) {
            // No-op by default
        }
    }

    void onBootPhase(int phase);

    void postCheckIdleStates(int userId);

    /**
     * We send a different message to check idle states once, otherwise we would end up
     * scheduling a series of repeating checkIdleStates each time we fired off one.
     */
    void postOneTimeCheckIdleStates();

    void setLastJobRunTime(String packageName, int userId, long elapsedRealtime);

    long getTimeSinceLastJobRun(String packageName, int userId);

    void setEstimatedLaunchTime(String packageName, int userId,
            @CurrentTimeMillisLong long launchTimeMs);

    /**
     * Returns the saved estimated launch time for the app. Will return {@code Long#MAX_VALUE} if no
     * value is saved.
     */
    @CurrentTimeMillisLong
    long getEstimatedLaunchTime(String packageName, int userId);

    /**
     * Returns the time (in milliseconds) since the app was last interacted with by the user.
     * This can be larger than the current elapsedRealtime, in case it happened before boot or
     * a really large value if the app was never interacted with.
     */
    long getTimeSinceLastUsedByUser(String packageName, int userId);

    void onUserRemoved(int userId);

    void addListener(AppIdleStateChangeListener listener);

    void removeListener(AppIdleStateChangeListener listener);

    int getAppId(String packageName);

    /**
     * @see #isAppIdleFiltered(String, int, int, long)
     */
    boolean isAppIdleFiltered(String packageName, int userId, long elapsedRealtime,
            boolean shouldObfuscateInstantApps);

    /**
     * Checks if an app has been idle for a while and filters out apps that are excluded.
     * It returns false if the current system state allows all apps to be considered active.
     * This happens if the device is plugged in or otherwise temporarily allowed to make exceptions.
     * Called by interface impls.
     */
    boolean isAppIdleFiltered(String packageName, int appId, int userId,
            long elapsedRealtime);

    /**
     * @return true if currently app idle parole mode is on.
     */
    boolean isInParole();

    int[] getIdleUidsForUser(int userId);

    void setAppIdleAsync(String packageName, boolean idle, int userId);

    @StandbyBuckets
    int getAppStandbyBucket(String packageName, int userId,
            long elapsedRealtime, boolean shouldObfuscateInstantApps);

    List<AppStandbyInfo> getAppStandbyBuckets(int userId);

    /**
     * Changes an app's standby bucket to the provided value. The caller can only set the standby
     * bucket for a different app than itself.
     * If attempting to automatically place an app in the RESTRICTED bucket, use
     * {@link #restrictApp(String, int, int)} instead.
     */
    void setAppStandbyBucket(@NonNull String packageName, int bucket, int userId, int callingUid,
            int callingPid);

    /**
     * Changes the app standby bucket for multiple apps at once.
     */
    void setAppStandbyBuckets(@NonNull List<AppStandbyInfo> appBuckets, int userId, int callingUid,
            int callingPid);

    /**
     * Put the specified app in the
     * {@link android.app.usage.UsageStatsManager#STANDBY_BUCKET_RESTRICTED}
     * bucket. If it has been used by the user recently, the restriction will delayed until an
     * appropriate time.
     *
     * @param restrictReason The restrictReason for restricting the app. Should be one of the
     *                       UsageStatsManager.REASON_SUB_FORCED_SYSTEM_FLAG_* reasons.
     */
    void restrictApp(@NonNull String packageName, int userId,
            @ForcedReasons int restrictReason);

    /**
     * Put the specified app in the
     * {@link android.app.usage.UsageStatsManager#STANDBY_BUCKET_RESTRICTED}
     * bucket. If it has been used by the user recently, the restriction will delayed
     * until an appropriate time. This should only be used in cases where
     * {@link #restrictApp(String, int, int)} is not sufficient.
     *
     * @param mainReason     The main reason for restricting the app. Must be either {@link
     *                       android.app.usage.UsageStatsManager#REASON_MAIN_FORCED_BY_SYSTEM} or
     *                       {@link android.app.usage.UsageStatsManager#REASON_MAIN_FORCED_BY_USER}.
     *                       Calls providing any other value will be ignored.
     * @param restrictReason The restrictReason for restricting the app. Should be one of the
     *                       UsageStatsManager.REASON_SUB_FORCED_SYSTEM_FLAG_* reasons.
     */
    void restrictApp(@NonNull String packageName, int userId, int mainReason,
            @ForcedReasons int restrictReason);

    /**
     * Unrestrict an app if there is no other reason to restrict it.
     *
     * <p>
     * The {@code prevMainReasonRestrict} and {@code prevSubReasonRestrict} are the previous
     * reasons of why it was restricted, but the caller knows that these conditions are not true
     * anymore; therefore if there is no other reasons to restrict it (as there could bemultiple
     * reasons to restrict it), lift the restriction.
     * </p>
     *
     * @param packageName            The package name of the app.
     * @param userId                 The user id that this app runs in.
     * @param prevMainReasonRestrict The main reason that why it was restricted, must be either
     *                               {@link android.app.usage.UsageStatsManager#REASON_MAIN_FORCED_BY_SYSTEM}
     *                               or {@link android.app.usage.UsageStatsManager#REASON_MAIN_FORCED_BY_USER}.
     * @param prevSubReasonRestrict  The subreason that why it was restricted before.
     * @param mainReasonUnrestrict   The main reason that why it could be unrestricted now.
     * @param subReasonUnrestrict    The subreason that why it could be unrestricted now.
     */
    void maybeUnrestrictApp(@NonNull String packageName, int userId, int prevMainReasonRestrict,
            int prevSubReasonRestrict, int mainReasonUnrestrict, int subReasonUnrestrict);

    void addActiveDeviceAdmin(String adminPkg, int userId);

    void setActiveAdminApps(Set<String> adminPkgs, int userId);

    void onAdminDataAvailable();

    void clearCarrierPrivilegedApps();

    void flushToDisk();

    void initializeDefaultsForSystemApps(int userId);

    void postReportContentProviderUsage(String name, String packageName, int userId);

    void postReportSyncScheduled(String packageName, int userId, boolean exempted);

    void postReportExemptedSyncStart(String packageName, int userId);

    void dumpUsers(IndentingPrintWriter idpw, int[] userIds, List<String> pkgs);

    void dumpState(String[] args, PrintWriter pw);

    boolean isAppIdleEnabled();
}
