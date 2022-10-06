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

package com.android.server.timedetector;

import static com.android.server.SystemClockTime.TIME_CONFIDENCE_HIGH;
import static com.android.server.SystemClockTime.TIME_CONFIDENCE_LOW;
import static com.android.server.timedetector.TimeDetectorStrategy.originToString;

import android.annotation.CurrentTimeMillisLong;
import android.annotation.ElapsedRealtimeLong;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.time.ExternalTimeSuggestion;
import android.app.time.TimeState;
import android.app.time.UnixEpochTime;
import android.app.timedetector.ManualTimeSuggestion;
import android.app.timedetector.TelephonyTimeSuggestion;
import android.content.Context;
import android.os.Handler;
import android.os.TimestampedValue;
import android.util.IndentingPrintWriter;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.SystemClockTime;
import com.android.server.SystemClockTime.TimeConfidence;
import com.android.server.timezonedetector.ArrayMapWithHistory;
import com.android.server.timezonedetector.ConfigurationChangeListener;
import com.android.server.timezonedetector.ReferenceWithHistory;

import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * The real implementation of {@link TimeDetectorStrategy}.
 *
 * <p>Most public methods are marked synchronized to ensure thread safety around internal state.
 */
public final class TimeDetectorStrategyImpl implements TimeDetectorStrategy {

    private static final boolean DBG = false;
    private static final String LOG_TAG = TimeDetectorService.TAG;

    /** A score value used to indicate "no score", either due to validation failure or age. */
    private static final int TELEPHONY_INVALID_SCORE = -1;
    /** The number of buckets telephony suggestions can be put in by age. */
    private static final int TELEPHONY_BUCKET_COUNT = 24;
    /** Each bucket is this size. All buckets are equally sized. */
    @VisibleForTesting
    static final int TELEPHONY_BUCKET_SIZE_MILLIS = 60 * 60 * 1000;
    /**
     * Telephony and network suggestions older than this value are considered too old to be used.
     */
    @VisibleForTesting
    static final long MAX_SUGGESTION_TIME_AGE_MILLIS =
            TELEPHONY_BUCKET_COUNT * TELEPHONY_BUCKET_SIZE_MILLIS;

    /**
     * CLOCK_PARANOIA: The maximum difference allowed between the expected system clock time and the
     * actual system clock time before a warning is logged. Used to help identify situations where
     * there is something other than this class setting the system clock.
     */
    private static final long SYSTEM_CLOCK_PARANOIA_THRESHOLD_MILLIS = 2 * 1000;

    /**
     * The number of suggestions to keep. These are logged in bug reports to assist when debugging
     * issues with detection.
     */
    private static final int KEEP_SUGGESTION_HISTORY_SIZE = 10;

    @NonNull
    private final Environment mEnvironment;

    @GuardedBy("this")
    @NonNull
    private ConfigurationInternal mCurrentConfigurationInternal;

    // Used to store the last time the system clock state was set automatically. It is used to
    // detect (and log) issues with the realtime clock or whether the clock is being set without
    // going through this strategy code.
    @GuardedBy("this")
    @Nullable
    private TimestampedValue<Long> mLastAutoSystemClockTimeSet;

    /**
     * A mapping from slotIndex to a time suggestion. We typically expect one or two mappings:
     * devices will have a small number of telephony devices and slotIndexs are assumed to be
     * stable.
     */
    @GuardedBy("this")
    private final ArrayMapWithHistory<Integer, TelephonyTimeSuggestion> mSuggestionBySlotIndex =
            new ArrayMapWithHistory<>(KEEP_SUGGESTION_HISTORY_SIZE);

    @GuardedBy("this")
    private final ReferenceWithHistory<NetworkTimeSuggestion> mLastNetworkSuggestion =
            new ReferenceWithHistory<>(KEEP_SUGGESTION_HISTORY_SIZE);

    @GuardedBy("this")
    private final ReferenceWithHistory<GnssTimeSuggestion> mLastGnssSuggestion =
            new ReferenceWithHistory<>(KEEP_SUGGESTION_HISTORY_SIZE);

    @GuardedBy("this")
    private final ReferenceWithHistory<ExternalTimeSuggestion> mLastExternalSuggestion =
            new ReferenceWithHistory<>(KEEP_SUGGESTION_HISTORY_SIZE);

    /**
     * Used by {@link TimeDetectorStrategyImpl} to interact with device configuration / settings
     * / system properties. It can be faked for testing.
     *
     * <p>Note: Because the settings / system properties-derived values can currently be modified
     * independently and from different threads (and processes!), their use is prone to race
     * conditions.
     */
    public interface Environment {

        /**
         * Sets a {@link ConfigurationChangeListener} that will be invoked when there are any
         * changes that could affect the content of {@link ConfigurationInternal}.
         * This is invoked during system server setup.
         */
        void setConfigurationInternalChangeListener(@NonNull ConfigurationChangeListener listener);

        /** Returns the {@link ConfigurationInternal} for the current user. */
        @NonNull ConfigurationInternal getCurrentUserConfigurationInternal();

        /** Acquire a suitable wake lock. Must be followed by {@link #releaseWakeLock()} */
        void acquireWakeLock();

        /** Returns the elapsedRealtimeMillis clock value. */
        @ElapsedRealtimeLong
        long elapsedRealtimeMillis();

        /** Returns the system clock value. */
        @CurrentTimeMillisLong
        long systemClockMillis();

        /** Returns the system clock confidence value. */
        @TimeConfidence int systemClockConfidence();

        /** Sets the device system clock and confidence. The WakeLock must be held. */
        void setSystemClock(
                @CurrentTimeMillisLong long newTimeMillis, @TimeConfidence int confidence,
                @NonNull String logMsg);

        /** Sets the device system clock confidence. The WakeLock must be held. */
        void setSystemClockConfidence(@TimeConfidence int confidence, @NonNull String logMsg);

        /** Release the wake lock acquired by a call to {@link #acquireWakeLock()}. */
        void releaseWakeLock();


        /**
         * Adds a standalone entry to the time debug log.
         */
        void addDebugLogEntry(@NonNull String logMsg);

        /**
         * Dumps the time debug log to the supplied {@link PrintWriter}.
         */
        void dumpDebugLog(PrintWriter printWriter);
    }

    static TimeDetectorStrategy create(
            @NonNull Context context, @NonNull Handler handler,
            @NonNull ServiceConfigAccessor serviceConfigAccessor) {

        TimeDetectorStrategyImpl.Environment environment =
                new EnvironmentImpl(context, handler, serviceConfigAccessor);
        return new TimeDetectorStrategyImpl(environment);
    }

    @VisibleForTesting
    TimeDetectorStrategyImpl(@NonNull Environment environment) {
        mEnvironment = Objects.requireNonNull(environment);

        synchronized (this) {
            mEnvironment.setConfigurationInternalChangeListener(
                    this::handleConfigurationInternalChanged);
            mCurrentConfigurationInternal = mEnvironment.getCurrentUserConfigurationInternal();
        }
    }

    @Override
    public synchronized void suggestExternalTime(@NonNull ExternalTimeSuggestion suggestion) {
        ConfigurationInternal currentUserConfig = mCurrentConfigurationInternal;
        if (DBG) {
            Slog.d(LOG_TAG, "External suggestion received."
                    + " currentUserConfig=" + currentUserConfig
                    + " newSuggestion=" + suggestion);
        }
        Objects.requireNonNull(suggestion);

        final TimestampedValue<Long> newUnixEpochTime = suggestion.getUnixEpochTime();

        if (!validateAutoSuggestionTime(newUnixEpochTime, suggestion)) {
            return;
        }

        mLastExternalSuggestion.set(suggestion);

        String reason = "External time suggestion received: suggestion=" + suggestion;
        doAutoTimeDetection(reason);
    }

    @Override
    public synchronized void suggestGnssTime(@NonNull GnssTimeSuggestion suggestion) {
        ConfigurationInternal currentUserConfig = mCurrentConfigurationInternal;
        if (DBG) {
            Slog.d(LOG_TAG, "GNSS suggestion received."
                    + " currentUserConfig=" + currentUserConfig
                    + " newSuggestion=" + suggestion);
        }
        Objects.requireNonNull(suggestion);

        final TimestampedValue<Long> newUnixEpochTime = suggestion.getUnixEpochTime();

        if (!validateAutoSuggestionTime(newUnixEpochTime, suggestion)) {
            return;
        }

        mLastGnssSuggestion.set(suggestion);

        String reason = "GNSS time suggestion received: suggestion=" + suggestion;
        doAutoTimeDetection(reason);
    }

    @Override
    public synchronized boolean suggestManualTime(
            @UserIdInt int userId, @NonNull ManualTimeSuggestion suggestion) {

        ConfigurationInternal currentUserConfig = mCurrentConfigurationInternal;
        if (currentUserConfig.getUserId() != userId) {
            Slog.w(LOG_TAG, "Manual suggestion received but user != current user, userId=" + userId
                    + " suggestion=" + suggestion);

            // Only listen to changes from the current user.
            return false;
        }

        Objects.requireNonNull(suggestion);

        final TimestampedValue<Long> newUnixEpochTime = suggestion.getUnixEpochTime();

        if (!validateManualSuggestionTime(newUnixEpochTime, suggestion)) {
            return false;
        }

        String cause = "Manual time suggestion received: suggestion=" + suggestion;
        return setSystemClockAndConfidenceIfRequired(ORIGIN_MANUAL, newUnixEpochTime, cause);
    }

    @Override
    public synchronized void suggestNetworkTime(@NonNull NetworkTimeSuggestion suggestion) {
        ConfigurationInternal currentUserConfig = mCurrentConfigurationInternal;
        if (DBG) {
            Slog.d(LOG_TAG, "Network suggestion received."
                    + " currentUserConfig=" + currentUserConfig
                    + " newSuggestion=" + suggestion);
        }
        Objects.requireNonNull(suggestion);

        if (!validateAutoSuggestionTime(suggestion.getUnixEpochTime(), suggestion)) {
            return;
        }

        // The caller submits suggestions with the best available information when there are network
        // changes. The best available information may have been cached and if they were all stored
        // this would lead to duplicates showing up in the suggestion history. The suggestions may
        // be made for different reasons but there is not a significant benefit to storing the same
        // suggestion information again. doAutoTimeDetection() should still be called: this ensures
        // the suggestion and device state are always re-evaluated, which might produce a different
        // detected time if, for example, the age of all suggestions are considered.
        NetworkTimeSuggestion lastNetworkSuggestion = mLastNetworkSuggestion.get();
        if (lastNetworkSuggestion == null || !lastNetworkSuggestion.equals(suggestion)) {
            mLastNetworkSuggestion.set(suggestion);
        }

        // Now perform auto time detection. The new suggestion may be used to modify the system
        // clock.
        String reason = "New network time suggested. timeSuggestion=" + suggestion;
        doAutoTimeDetection(reason);
    }

    @Override
    public synchronized TimeState getTimeState() {
        boolean userShouldConfirmTime = mEnvironment.systemClockConfidence() < TIME_CONFIDENCE_HIGH;
        UnixEpochTime unixEpochTime = new UnixEpochTime(
                mEnvironment.elapsedRealtimeMillis(), mEnvironment.systemClockMillis());
        return new TimeState(unixEpochTime, userShouldConfirmTime);
    }

    @Override
    public synchronized void setTimeState(@NonNull TimeState timeState) {
        Objects.requireNonNull(timeState);

        @TimeConfidence int confidence = timeState.getUserShouldConfirmTime()
                ? TIME_CONFIDENCE_LOW : TIME_CONFIDENCE_HIGH;
        mEnvironment.acquireWakeLock();
        try {
            // The origin is a lie but this method is only used for command line / manual testing
            // to force the device into a specific state.
            @Origin int origin = ORIGIN_MANUAL;
            UnixEpochTime unixEpochTime = timeState.getUnixEpochTime();
            setSystemClockAndConfidenceUnderWakeLock(
                    origin, unixEpochTime.toTimestampedValue(), confidence, "setTimeZoneState()");
        } finally {
            mEnvironment.releaseWakeLock();
        }
    }

    @Override
    public synchronized boolean confirmTime(@NonNull UnixEpochTime confirmationTime) {
        Objects.requireNonNull(confirmationTime);

        @TimeConfidence int newTimeConfidence = TIME_CONFIDENCE_HIGH;
        @TimeConfidence int currentTimeConfidence = mEnvironment.systemClockConfidence();
        boolean timeNeedsConfirmation = currentTimeConfidence < newTimeConfidence;

        // All system clock calculation take place under a wake lock.
        mEnvironment.acquireWakeLock();
        try {
            // Check if the specified time matches the current system clock time (closely
            // enough) to raise the confidence.
            long elapsedRealtimeMillis = mEnvironment.elapsedRealtimeMillis();
            long actualSystemClockMillis = mEnvironment.systemClockMillis();
            long adjustedAutoDetectedUnixEpochMillis =
                    confirmationTime.at(elapsedRealtimeMillis).getUnixEpochTimeMillis();
            long absTimeDifferenceMillis =
                    Math.abs(adjustedAutoDetectedUnixEpochMillis - actualSystemClockMillis);
            int confidenceUpgradeThresholdMillis =
                    mCurrentConfigurationInternal.getSystemClockConfidenceThresholdMillis();
            boolean timeConfirmed = absTimeDifferenceMillis <= confidenceUpgradeThresholdMillis;
            boolean updateConfidenceRequired = timeNeedsConfirmation && timeConfirmed;
            if (updateConfidenceRequired) {
                String logMsg = "Confirm system clock time."
                        + " confirmationTime=" + confirmationTime
                        + " newTimeConfidence=" + newTimeConfidence
                        + " elapsedRealtimeMillis=" + elapsedRealtimeMillis
                        + " (old) actualSystemClockMillis=" + actualSystemClockMillis
                        + " currentTimeConfidence=" + currentTimeConfidence;
                if (DBG) {
                    Slog.d(LOG_TAG, logMsg);
                }

                mEnvironment.setSystemClockConfidence(newTimeConfidence, logMsg);
            }
            return timeConfirmed;
        } finally {
            mEnvironment.releaseWakeLock();
        }
    }

    @Override
    public synchronized void suggestTelephonyTime(@NonNull TelephonyTimeSuggestion timeSuggestion) {
        // Empty time suggestion means that telephony network connectivity has been lost.
        // The passage of time is relentless, and we don't expect our users to use a time machine,
        // so we can continue relying on previous suggestions when we lose connectivity. This is
        // unlike time zone, where a user may lose connectivity when boarding a flight and where we
        // do want to "forget" old signals. Suggestions that are too old are discarded later in the
        // detection algorithm.
        if (timeSuggestion.getUnixEpochTime() == null) {
            return;
        }

        if (!validateAutoSuggestionTime(timeSuggestion.getUnixEpochTime(), timeSuggestion)) {
            return;
        }

        // Perform input filtering and record the validated suggestion against the slotIndex.
        if (!storeTelephonySuggestion(timeSuggestion)) {
            return;
        }

        // Now perform auto time detection. The new suggestion may be used to modify the system
        // clock.
        String reason = "New telephony time suggested. timeSuggestion=" + timeSuggestion;
        doAutoTimeDetection(reason);
    }

    private synchronized void handleConfigurationInternalChanged() {
        ConfigurationInternal currentUserConfig =
                mEnvironment.getCurrentUserConfigurationInternal();
        String logMsg = "handleConfigurationInternalChanged:"
                + " oldConfiguration=" + mCurrentConfigurationInternal
                + ", newConfiguration=" + currentUserConfig;
        addDebugLogEntry(logMsg);
        mCurrentConfigurationInternal = currentUserConfig;

        boolean autoDetectionEnabled =
                mCurrentConfigurationInternal.getAutoDetectionEnabledBehavior();
        // When automatic time detection is enabled we update the system clock instantly if we can.
        // Conversely, when automatic time detection is disabled we leave the clock as it is.
        if (autoDetectionEnabled) {
            String reason = "Auto time zone detection config changed.";
            doAutoTimeDetection(reason);
        } else {
            // CLOCK_PARANOIA: We are losing "control" of the system clock so we cannot predict what
            // it should be in future.
            mLastAutoSystemClockTimeSet = null;
        }
    }

    private void addDebugLogEntry(@NonNull String logMsg) {
        if (DBG) {
            Slog.d(LOG_TAG, logMsg);
        }
        mEnvironment.addDebugLogEntry(logMsg);
    }

    @Override
    public synchronized void dump(@NonNull IndentingPrintWriter ipw, @Nullable String[] args) {
        ipw.println("TimeDetectorStrategy:");
        ipw.increaseIndent(); // level 1

        ipw.println("mLastAutoSystemClockTimeSet=" + mLastAutoSystemClockTimeSet);
        ipw.println("mCurrentConfigurationInternal=" + mCurrentConfigurationInternal);
        ipw.println("[Capabilities=" + mCurrentConfigurationInternal.capabilitiesAndConfig() + "]");
        long elapsedRealtimeMillis = mEnvironment.elapsedRealtimeMillis();
        ipw.printf("mEnvironment.elapsedRealtimeMillis()=%s (%s)\n",
                Duration.ofMillis(elapsedRealtimeMillis), elapsedRealtimeMillis);
        long systemClockMillis = mEnvironment.systemClockMillis();
        ipw.printf("mEnvironment.systemClockMillis()=%s (%s)\n",
                Instant.ofEpochMilli(systemClockMillis), systemClockMillis);
        ipw.println("mEnvironment.systemClockConfidence()=" + mEnvironment.systemClockConfidence());

        ipw.println("Time change log:");
        ipw.increaseIndent(); // level 2
        SystemClockTime.dump(ipw);
        ipw.decreaseIndent(); // level 2

        ipw.println("Telephony suggestion history:");
        ipw.increaseIndent(); // level 2
        mSuggestionBySlotIndex.dump(ipw);
        ipw.decreaseIndent(); // level 2

        ipw.println("Network suggestion history:");
        ipw.increaseIndent(); // level 2
        mLastNetworkSuggestion.dump(ipw);
        ipw.decreaseIndent(); // level 2

        ipw.println("Gnss suggestion history:");
        ipw.increaseIndent(); // level 2
        mLastGnssSuggestion.dump(ipw);
        ipw.decreaseIndent(); // level 2

        ipw.println("External suggestion history:");
        ipw.increaseIndent(); // level 2
        mLastExternalSuggestion.dump(ipw);
        ipw.decreaseIndent(); // level 2

        ipw.decreaseIndent(); // level 1
    }

    @GuardedBy("this")
    private boolean storeTelephonySuggestion(
            @NonNull TelephonyTimeSuggestion suggestion) {
        TimestampedValue<Long> newUnixEpochTime = suggestion.getUnixEpochTime();

        int slotIndex = suggestion.getSlotIndex();
        TelephonyTimeSuggestion previousSuggestion = mSuggestionBySlotIndex.get(slotIndex);
        if (previousSuggestion != null) {
            // We can log / discard suggestions with obvious issues with the reference time clock.
            if (previousSuggestion.getUnixEpochTime() == null
                    || previousSuggestion.getUnixEpochTime().getValue() == null) {
                // This should be impossible given we only store validated suggestions.
                Slog.w(LOG_TAG, "Previous suggestion is null or has a null time."
                        + " previousSuggestion=" + previousSuggestion
                        + ", suggestion=" + suggestion);
                return false;
            }

            long referenceTimeDifference = TimestampedValue.referenceTimeDifference(
                    newUnixEpochTime, previousSuggestion.getUnixEpochTime());
            if (referenceTimeDifference < 0) {
                // The reference time is before the previously received suggestion. Ignore it.
                Slog.w(LOG_TAG, "Out of order telephony suggestion received."
                        + " referenceTimeDifference=" + referenceTimeDifference
                        + " previousSuggestion=" + previousSuggestion
                        + " suggestion=" + suggestion);
                return false;
            }
        }

        // Store the latest suggestion.
        mSuggestionBySlotIndex.put(slotIndex, suggestion);
        return true;
    }

    @GuardedBy("this")
    private boolean validateSuggestionCommon(
            @NonNull TimestampedValue<Long> newUnixEpochTime, @NonNull Object suggestion) {
        if (newUnixEpochTime.getValue() == null) {
            Slog.w(LOG_TAG, "Suggested time value is null. suggestion=" + suggestion);
            return false;
        }

        // We can validate the suggestion against the reference time clock.
        long elapsedRealtimeMillis = mEnvironment.elapsedRealtimeMillis();
        if (elapsedRealtimeMillis < newUnixEpochTime.getReferenceTimeMillis()) {
            // elapsedRealtime clock went backwards?
            Slog.w(LOG_TAG, "New reference time is in the future? Ignoring."
                    + " elapsedRealtimeMillis=" + elapsedRealtimeMillis
                    + ", suggestion=" + suggestion);
            return false;
        }

        if (newUnixEpochTime.getValue()
                > mCurrentConfigurationInternal.getSuggestionUpperBound().toEpochMilli()) {
            // This check won't prevent a device's system clock exceeding Integer.MAX_VALUE Unix
            // seconds through the normal passage of time, but it will stop it jumping above 2038
            // because of a "bad" suggestion. b/204193177
            Slog.w(LOG_TAG, "Suggested value is above max time supported by this device."
                    + " suggestion=" + suggestion);
            return false;
        }
        return true;
    }

    /**
     * Returns {@code true} if an automatic time suggestion time is valid.
     * See also {@link #validateManualSuggestionTime(TimestampedValue, Object)}.
     */
    @GuardedBy("this")
    private boolean validateAutoSuggestionTime(
            @NonNull TimestampedValue<Long> newUnixEpochTime, @NonNull Object suggestion)  {
        Instant lowerBound = mCurrentConfigurationInternal.getAutoSuggestionLowerBound();
        return validateSuggestionCommon(newUnixEpochTime, suggestion)
                && validateSuggestionAgainstLowerBound(newUnixEpochTime, suggestion,
                lowerBound);
    }

    /**
     * Returns {@code true} if a manual time suggestion time is valid.
     * See also {@link #validateAutoSuggestionTime(TimestampedValue, Object)}.
     */
    @GuardedBy("this")
    private boolean validateManualSuggestionTime(
            @NonNull TimestampedValue<Long> newUnixEpochTime, @NonNull Object suggestion)  {
        Instant lowerBound = mCurrentConfigurationInternal.getManualSuggestionLowerBound();

        // Suggestion is definitely wrong if it comes before lower time bound.
        return validateSuggestionCommon(newUnixEpochTime, suggestion)
                && validateSuggestionAgainstLowerBound(newUnixEpochTime, suggestion, lowerBound);
    }

    @GuardedBy("this")
    private boolean validateSuggestionAgainstLowerBound(
            @NonNull TimestampedValue<Long> newUnixEpochTime, @NonNull Object suggestion,
            @NonNull Instant lowerBound) {

        // Suggestion is definitely wrong if it comes before lower time bound.
        if (lowerBound.toEpochMilli() > newUnixEpochTime.getValue()) {
            Slog.w(LOG_TAG, "Suggestion points to time before lower bound, skipping it. "
                    + "suggestion=" + suggestion + ", lower bound=" + lowerBound);
            return false;
        }

        return true;
    }

    @GuardedBy("this")
    private void doAutoTimeDetection(@NonNull String detectionReason) {
        // Try the different origins one at a time.
        int[] originPriorities = mCurrentConfigurationInternal.getAutoOriginPriorities();
        for (int origin : originPriorities) {
            TimestampedValue<Long> newUnixEpochTime = null;
            String cause = null;
            if (origin == ORIGIN_TELEPHONY) {
                TelephonyTimeSuggestion bestTelephonySuggestion = findBestTelephonySuggestion();
                if (bestTelephonySuggestion != null) {
                    newUnixEpochTime = bestTelephonySuggestion.getUnixEpochTime();
                    cause = "Found good telephony suggestion."
                            + ", bestTelephonySuggestion=" + bestTelephonySuggestion
                            + ", detectionReason=" + detectionReason;
                }
            } else if (origin == ORIGIN_NETWORK) {
                NetworkTimeSuggestion networkSuggestion = findLatestValidNetworkSuggestion();
                if (networkSuggestion != null) {
                    newUnixEpochTime = networkSuggestion.getUnixEpochTime();
                    cause = "Found good network suggestion."
                            + ", networkSuggestion=" + networkSuggestion
                            + ", detectionReason=" + detectionReason;
                }
            } else if (origin == ORIGIN_GNSS) {
                GnssTimeSuggestion gnssTimeSuggestion = findLatestValidGnssSuggestion();
                if (gnssTimeSuggestion != null) {
                    newUnixEpochTime = gnssTimeSuggestion.getUnixEpochTime();
                    cause = "Found good gnss suggestion."
                            + ", gnssTimeSuggestion=" + gnssTimeSuggestion
                            + ", detectionReason=" + detectionReason;
                }
            } else if (origin == ORIGIN_EXTERNAL) {
                ExternalTimeSuggestion externalTimeSuggestion = findLatestValidExternalSuggestion();
                if (externalTimeSuggestion != null) {
                    newUnixEpochTime = externalTimeSuggestion.getUnixEpochTime();
                    cause = "Found good external suggestion."
                            + ", externalTimeSuggestion=" + externalTimeSuggestion
                            + ", detectionReason=" + detectionReason;
                }
            } else {
                Slog.w(LOG_TAG, "Unknown or unsupported origin=" + origin
                        + " in " + Arrays.toString(originPriorities)
                        + ": Skipping");
            }

            // Update the system clock if a good suggestion has been found.
            if (newUnixEpochTime != null) {
                if (mCurrentConfigurationInternal.getAutoDetectionEnabledBehavior()) {
                    setSystemClockAndConfidenceIfRequired(origin, newUnixEpochTime, cause);
                } else {
                    // An automatically detected time can be used to raise the confidence in the
                    // current time even if the device is set to only allow user input for the time
                    // itself.
                    upgradeSystemClockConfidenceIfRequired(newUnixEpochTime, cause);
                }
                return;
            }
        }

        if (DBG) {
            Slog.d(LOG_TAG, "Could not determine time: No suggestion found in"
                    + " originPriorities=" + Arrays.toString(originPriorities)
                    + ", detectionReason=" + detectionReason);
        }
    }

    @GuardedBy("this")
    @Nullable
    private TelephonyTimeSuggestion findBestTelephonySuggestion() {
        long elapsedRealtimeMillis = mEnvironment.elapsedRealtimeMillis();

        // Telephony time suggestions are assumed to be derived from NITZ or NITZ-like signals.
        // These have a number of limitations:
        // 1) No guarantee of accuracy ("accuracy of the time information is in the order of
        // minutes") [1]
        // 2) No guarantee of regular signals ("dependent on the handset crossing radio network
        // boundaries") [1]
        //
        // [1] https://en.wikipedia.org/wiki/NITZ
        //
        // Generally, when there are suggestions from multiple slotIndexs they should usually
        // approximately agree. In cases where signals *are* inaccurate we don't want to vacillate
        // between signals from two slotIndexs. However, it is known for NITZ signals to be
        // incorrect occasionally, which means we also don't want to stick forever with one
        // slotIndex. Without cross-referencing across sources (e.g. the current device time, NTP),
        // or doing some kind of statistical analysis of consistency within and across slotIndexs,
        // we can't know which suggestions are more correct.
        //
        // For simplicity, we try to value recency, then consistency of slotIndex.
        //
        // The heuristic works as follows:
        // Recency: The most recent suggestion from each slotIndex is scored. The score is based on
        // a discrete age bucket, i.e. so signals received around the same time will be in the same
        // bucket, thus applying a loose reference time ordering. The suggestion with the highest
        // score is used.
        // Consistency: If there a multiple suggestions with the same score, the suggestion with the
        // lowest slotIndex is always taken.
        //
        // In the trivial case with a single ID this will just mean that the latest received
        // suggestion is used.

        TelephonyTimeSuggestion bestSuggestion = null;
        int bestScore = TELEPHONY_INVALID_SCORE;
        for (int i = 0; i < mSuggestionBySlotIndex.size(); i++) {
            Integer slotIndex = mSuggestionBySlotIndex.keyAt(i);
            TelephonyTimeSuggestion candidateSuggestion = mSuggestionBySlotIndex.valueAt(i);
            if (candidateSuggestion == null) {
                // Unexpected - null suggestions should never be stored.
                Slog.w(LOG_TAG, "Latest suggestion unexpectedly null for slotIndex."
                        + " slotIndex=" + slotIndex);
                continue;
            } else if (candidateSuggestion.getUnixEpochTime() == null) {
                // Unexpected - we do not store empty suggestions.
                Slog.w(LOG_TAG, "Latest suggestion unexpectedly empty. "
                        + " candidateSuggestion=" + candidateSuggestion);
                continue;
            }

            int candidateScore =
                    scoreTelephonySuggestion(elapsedRealtimeMillis, candidateSuggestion);
            if (candidateScore == TELEPHONY_INVALID_SCORE) {
                // Expected: This means the suggestion is obviously invalid or just too old.
                continue;
            }

            // Higher scores are better.
            if (bestSuggestion == null || bestScore < candidateScore) {
                bestSuggestion = candidateSuggestion;
                bestScore = candidateScore;
            } else if (bestScore == candidateScore) {
                // Tie! Use the suggestion with the lowest slotIndex.
                int candidateSlotIndex = candidateSuggestion.getSlotIndex();
                int bestSlotIndex = bestSuggestion.getSlotIndex();
                if (candidateSlotIndex < bestSlotIndex) {
                    bestSuggestion = candidateSuggestion;
                }
            }
        }
        return bestSuggestion;
    }

    private static int scoreTelephonySuggestion(
            long elapsedRealtimeMillis, @NonNull TelephonyTimeSuggestion timeSuggestion) {

        // Validate first.
        TimestampedValue<Long> unixEpochTime = timeSuggestion.getUnixEpochTime();
        if (!validateSuggestionUnixEpochTime(elapsedRealtimeMillis, unixEpochTime)) {
            Slog.w(LOG_TAG, "Existing suggestion found to be invalid"
                    + " elapsedRealtimeMillis=" + elapsedRealtimeMillis
                    + ", timeSuggestion=" + timeSuggestion);
            return TELEPHONY_INVALID_SCORE;
        }

        // The score is based on the age since receipt. Suggestions are bucketed so two
        // suggestions in the same bucket from different slotIndexs are scored the same.
        long ageMillis = elapsedRealtimeMillis - unixEpochTime.getReferenceTimeMillis();

        // Turn the age into a discrete value: 0 <= bucketIndex < TELEPHONY_BUCKET_COUNT.
        int bucketIndex = (int) (ageMillis / TELEPHONY_BUCKET_SIZE_MILLIS);
        if (bucketIndex >= TELEPHONY_BUCKET_COUNT) {
            return TELEPHONY_INVALID_SCORE;
        }

        // We want the lowest bucket index to have the highest score. 0 > score >= BUCKET_COUNT.
        return TELEPHONY_BUCKET_COUNT - bucketIndex;
    }

    /** Returns the latest, valid, network suggestion. Returns {@code null} if there isn't one. */
    @GuardedBy("this")
    @Nullable
    private NetworkTimeSuggestion findLatestValidNetworkSuggestion() {
        NetworkTimeSuggestion networkSuggestion = mLastNetworkSuggestion.get();
        if (networkSuggestion == null) {
            // No network suggestions received. This is normal if there's no connectivity.
            return null;
        }

        TimestampedValue<Long> unixEpochTime = networkSuggestion.getUnixEpochTime();
        long elapsedRealTimeMillis = mEnvironment.elapsedRealtimeMillis();
        if (!validateSuggestionUnixEpochTime(elapsedRealTimeMillis, unixEpochTime)) {
            // The latest suggestion is not valid, usually due to its age.
            return null;
        }

        return networkSuggestion;
    }

    /** Returns the latest, valid, gnss suggestion. Returns {@code null} if there isn't one. */
    @GuardedBy("this")
    @Nullable
    private GnssTimeSuggestion findLatestValidGnssSuggestion() {
        GnssTimeSuggestion gnssTimeSuggestion = mLastGnssSuggestion.get();
        if (gnssTimeSuggestion == null) {
            // No gnss suggestions received. This is normal if there's no gnss signal.
            return null;
        }

        TimestampedValue<Long> unixEpochTime = gnssTimeSuggestion.getUnixEpochTime();
        long elapsedRealTimeMillis = mEnvironment.elapsedRealtimeMillis();
        if (!validateSuggestionUnixEpochTime(elapsedRealTimeMillis, unixEpochTime)) {
            // The latest suggestion is not valid, usually due to its age.
            return null;
        }

        return gnssTimeSuggestion;
    }

    /** Returns the latest, valid, external suggestion. Returns {@code null} if there isn't one. */
    @GuardedBy("this")
    @Nullable
    private ExternalTimeSuggestion findLatestValidExternalSuggestion() {
        ExternalTimeSuggestion externalTimeSuggestion = mLastExternalSuggestion.get();
        if (externalTimeSuggestion == null) {
            // No external suggestions received. This is normal if there's no external signal.
            return null;
        }

        TimestampedValue<Long> unixEpochTime = externalTimeSuggestion.getUnixEpochTime();
        long elapsedRealTimeMillis = mEnvironment.elapsedRealtimeMillis();
        if (!validateSuggestionUnixEpochTime(elapsedRealTimeMillis, unixEpochTime)) {
            // The latest suggestion is not valid, usually due to its age.
            return null;
        }

        return externalTimeSuggestion;
    }

    @GuardedBy("this")
    private boolean setSystemClockAndConfidenceIfRequired(
            @Origin int origin, @NonNull TimestampedValue<Long> time, @NonNull String cause) {

        // Any time set through this class is inherently high confidence. Either it came directly
        // from a user, or it was detected automatically.
        @TimeConfidence final int newTimeConfidence = TIME_CONFIDENCE_HIGH;
        boolean isOriginAutomatic = isOriginAutomatic(origin);
        if (isOriginAutomatic) {
            if (!mCurrentConfigurationInternal.getAutoDetectionEnabledBehavior()) {
                if (DBG) {
                    Slog.d(LOG_TAG,
                            "Auto time detection is not enabled / no confidence update is needed."
                            + " origin=" + originToString(origin)
                            + ", time=" + time
                            + ", cause=" + cause);
                }
                return false;
            }
        } else {
            if (mCurrentConfigurationInternal.getAutoDetectionEnabledBehavior()) {
                if (DBG) {
                    Slog.d(LOG_TAG, "Auto time detection is enabled."
                            + " origin=" + originToString(origin)
                            + ", time=" + time
                            + ", cause=" + cause);
                }
                return false;
            }
        }

        mEnvironment.acquireWakeLock();
        try {
            return setSystemClockAndConfidenceUnderWakeLock(origin, time, newTimeConfidence, cause);
        } finally {
            mEnvironment.releaseWakeLock();
        }
    }

    /**
     * Upgrades the system clock confidence if the current time matches the supplied auto-detected
     * time. The method never changes the system clock and it never lowers the confidence. It only
     * raises the confidence if the supplied time is within the configured threshold of the current
     * system clock time.
     */
    @GuardedBy("this")
    private void upgradeSystemClockConfidenceIfRequired(
            @NonNull TimestampedValue<Long> autoDetectedUnixEpochTime, @NonNull String cause) {
        @TimeConfidence int newTimeConfidence = TIME_CONFIDENCE_HIGH;
        @TimeConfidence int currentTimeConfidence = mEnvironment.systemClockConfidence();
        boolean timeNeedsConfirmation = currentTimeConfidence < newTimeConfidence;
        if (!timeNeedsConfirmation) {
            return;
        }

        // All system clock calculation take place under a wake lock.
        mEnvironment.acquireWakeLock();
        try {
            // Check if the specified time matches the current system clock time (closely
            // enough) to raise the confidence.
            long elapsedRealtimeMillis = mEnvironment.elapsedRealtimeMillis();
            long actualSystemClockMillis = mEnvironment.systemClockMillis();
            long adjustedAutoDetectedUnixEpochMillis = TimeDetectorStrategy.getTimeAt(
                    autoDetectedUnixEpochTime, elapsedRealtimeMillis);
            long absTimeDifferenceMillis =
                    Math.abs(adjustedAutoDetectedUnixEpochMillis - actualSystemClockMillis);
            int confidenceUpgradeThresholdMillis =
                    mCurrentConfigurationInternal.getSystemClockConfidenceThresholdMillis();
            boolean updateConfidenceRequired =
                    absTimeDifferenceMillis <= confidenceUpgradeThresholdMillis;
            if (updateConfidenceRequired) {
                String logMsg = "Upgrade system clock confidence."
                        + " autoDetectedUnixEpochTime=" + autoDetectedUnixEpochTime
                        + " newTimeConfidence=" + newTimeConfidence
                        + " cause=" + cause
                        + " elapsedRealtimeMillis=" + elapsedRealtimeMillis
                        + " (old) actualSystemClockMillis=" + actualSystemClockMillis
                        + " currentTimeConfidence=" + currentTimeConfidence;
                if (DBG) {
                    Slog.d(LOG_TAG, logMsg);
                }

                mEnvironment.setSystemClockConfidence(newTimeConfidence, logMsg);
            }
        } finally {
            mEnvironment.releaseWakeLock();
        }
    }

    private static boolean isOriginAutomatic(@Origin int origin) {
        return origin != ORIGIN_MANUAL;
    }

    @GuardedBy("this")
    private boolean setSystemClockAndConfidenceUnderWakeLock(
            @Origin int origin, @NonNull TimestampedValue<Long> newTime,
            @TimeConfidence int newTimeConfidence, @NonNull String cause) {

        long elapsedRealtimeMillis = mEnvironment.elapsedRealtimeMillis();
        boolean isOriginAutomatic = isOriginAutomatic(origin);
        long actualSystemClockMillis = mEnvironment.systemClockMillis();
        if (isOriginAutomatic) {
            // CLOCK_PARANOIA : Check to see if this class owns the clock or if something else
            // may be setting the clock.
            if (mLastAutoSystemClockTimeSet != null) {
                long expectedTimeMillis = TimeDetectorStrategy.getTimeAt(
                        mLastAutoSystemClockTimeSet, elapsedRealtimeMillis);
                long absSystemClockDifference =
                        Math.abs(expectedTimeMillis - actualSystemClockMillis);
                if (absSystemClockDifference > SYSTEM_CLOCK_PARANOIA_THRESHOLD_MILLIS) {
                    Slog.w(LOG_TAG,
                            "System clock has not tracked elapsed real time clock. A clock may"
                                    + " be inaccurate or something unexpectedly set the system"
                                    + " clock."
                                    + " origin=" + originToString(origin)
                                    + " elapsedRealtimeMillis=" + elapsedRealtimeMillis
                                    + " expectedTimeMillis=" + expectedTimeMillis
                                    + " actualTimeMillis=" + actualSystemClockMillis
                                    + " cause=" + cause);
                }
            }
        }

        // If the new signal would make sufficient difference to the system clock or mean a change
        // in confidence then system state must be updated.

        // Adjust for the time that has elapsed since the signal was received.
        long newSystemClockMillis = TimeDetectorStrategy.getTimeAt(newTime, elapsedRealtimeMillis);
        long absTimeDifference = Math.abs(newSystemClockMillis - actualSystemClockMillis);
        long systemClockUpdateThreshold =
                mCurrentConfigurationInternal.getSystemClockUpdateThresholdMillis();
        boolean updateSystemClockRequired = absTimeDifference >= systemClockUpdateThreshold;

        @TimeConfidence int currentTimeConfidence = mEnvironment.systemClockConfidence();
        boolean updateConfidenceRequired = newTimeConfidence != currentTimeConfidence;

        if (updateSystemClockRequired) {
            String logMsg = "Set system clock & confidence."
                    + " origin=" + originToString(origin)
                    + " newTime=" + newTime
                    + " newTimeConfidence=" + newTimeConfidence
                    + " cause=" + cause
                    + " elapsedRealtimeMillis=" + elapsedRealtimeMillis
                    + " (old) actualSystemClockMillis=" + actualSystemClockMillis
                    + " newSystemClockMillis=" + newSystemClockMillis
                    + " currentTimeConfidence=" + currentTimeConfidence;
            mEnvironment.setSystemClock(newSystemClockMillis, newTimeConfidence, logMsg);
            if (DBG) {
                Slog.d(LOG_TAG, logMsg);
            }

            // CLOCK_PARANOIA : Record the last time this class set the system clock due to an
            // auto-time signal, or clear the record it is being done manually.
            if (isOriginAutomatic(origin)) {
                mLastAutoSystemClockTimeSet = newTime;
            } else {
                mLastAutoSystemClockTimeSet = null;
            }
        } else if (updateConfidenceRequired) {
            // Only the confidence needs updating. This path is separate from a system clock update
            // to deliberately avoid touching the system clock's value when it's not needed. Doing
            // so could introduce inaccuracies or cause unnecessary wear in RTC hardware or
            // associated storage.
            String logMsg = "Set system clock confidence."
                    + " origin=" + originToString(origin)
                    + " newTime=" + newTime
                    + " newTimeConfidence=" + newTimeConfidence
                    + " cause=" + cause
                    + " elapsedRealtimeMillis=" + elapsedRealtimeMillis
                    + " (old) actualSystemClockMillis=" + actualSystemClockMillis
                    + " newSystemClockMillis=" + newSystemClockMillis
                    + " currentTimeConfidence=" + currentTimeConfidence;
            if (DBG) {
                Slog.d(LOG_TAG, logMsg);
            }
            mEnvironment.setSystemClockConfidence(newTimeConfidence, logMsg);
        } else {
            // Neither clock nor confidence need updating.
            if (DBG) {
                Slog.d(LOG_TAG, "Not setting system clock or confidence."
                        + " origin=" + originToString(origin)
                        + " newTime=" + newTime
                        + " newTimeConfidence=" + newTimeConfidence
                        + " cause=" + cause
                        + " elapsedRealtimeMillis=" + elapsedRealtimeMillis
                        + " systemClockUpdateThreshold=" + systemClockUpdateThreshold
                        + " absTimeDifference=" + absTimeDifference
                        + " currentTimeConfidence=" + currentTimeConfidence);
            }
        }
        return true;
    }

    /**
     * Returns the current best telephony suggestion. Not intended for general use: it is used
     * during tests to check strategy behavior.
     */
    @VisibleForTesting
    @Nullable
    public synchronized TelephonyTimeSuggestion findBestTelephonySuggestionForTests() {
        return findBestTelephonySuggestion();
    }

    /**
     * Returns the latest valid network suggestion. Not intended for general use: it is used during
     * tests to check strategy behavior.
     */
    @VisibleForTesting
    @Nullable
    public synchronized NetworkTimeSuggestion findLatestValidNetworkSuggestionForTests() {
        return findLatestValidNetworkSuggestion();
    }

    /**
     * Returns the latest valid gnss suggestion. Not intended for general use: it is used during
     * tests to check strategy behavior.
     */
    @VisibleForTesting
    @Nullable
    public synchronized GnssTimeSuggestion findLatestValidGnssSuggestionForTests() {
        return findLatestValidGnssSuggestion();
    }

    /**
     * Returns the latest valid external suggestion. Not intended for general use: it is used during
     * tests to check strategy behavior.
     */
    @VisibleForTesting
    @Nullable
    public synchronized ExternalTimeSuggestion findLatestValidExternalSuggestionForTests() {
        return findLatestValidExternalSuggestion();
    }
    /**
     * A method used to inspect state during tests. Not intended for general use.
     */
    @VisibleForTesting
    @Nullable
    public synchronized TelephonyTimeSuggestion getLatestTelephonySuggestion(int slotIndex) {
        return mSuggestionBySlotIndex.get(slotIndex);
    }

    /**
     * A method used to inspect state during tests. Not intended for general use.
     */
    @VisibleForTesting
    @Nullable
    public synchronized NetworkTimeSuggestion getLatestNetworkSuggestion() {
        return mLastNetworkSuggestion.get();
    }

    /**
     * A method used to inspect state during tests. Not intended for general use.
     */
    @VisibleForTesting
    @Nullable
    public synchronized GnssTimeSuggestion getLatestGnssSuggestion() {
        return mLastGnssSuggestion.get();
    }

    /**
     * A method used to inspect state during tests. Not intended for general use.
     */
    @VisibleForTesting
    @Nullable
    public synchronized ExternalTimeSuggestion getLatestExternalSuggestion() {
        return mLastExternalSuggestion.get();
    }

    private static boolean validateSuggestionUnixEpochTime(
            long elapsedRealtimeMillis, TimestampedValue<Long> unixEpochTime) {
        long referenceTimeMillis = unixEpochTime.getReferenceTimeMillis();
        if (referenceTimeMillis > elapsedRealtimeMillis) {
            // Future reference times are ignored. They imply the reference time was wrong, or the
            // elapsed realtime clock used to derive it has gone backwards, neither of which are
            // supportable situations.
            return false;
        }

        // Any suggestion > MAX_AGE_MILLIS is treated as too old. Although time is relentless and
        // predictable, the accuracy of the reference time clock may be poor over long periods which
        // would lead to errors creeping in. Also, in edge cases where a bad suggestion has been
        // made and never replaced, it could also mean that the time detection code remains
        // opinionated using a bad invalid suggestion. This caps that edge case at MAX_AGE_MILLIS.
        long ageMillis = elapsedRealtimeMillis - referenceTimeMillis;
        return ageMillis <= MAX_SUGGESTION_TIME_AGE_MILLIS;
    }
}
