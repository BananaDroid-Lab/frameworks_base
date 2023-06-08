/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.android.systemui.battery.BatteryMeterView.BATTERY_STYLE_CIRCLE;
import static com.android.systemui.battery.BatteryMeterView.BATTERY_STYLE_DOTTED_CIRCLE;
import static com.android.systemui.battery.BatteryMeterView.BATTERY_STYLE_FULL_CIRCLE;

import static com.android.systemui.people.PeopleSpaceUtils.convertDrawableToBitmap;

import android.annotation.UiThread;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaController.PlaybackInfo;
import android.media.session.MediaSession;
import android.media.session.MediaSession.QueueItem;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.media.session.MediaSessionLegacyHelper;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.format.Formatter;
import android.text.format.Formatter.BytesResult;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.DisplayCutout;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.Space;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.graphics.ColorUtils;
import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.policy.SystemBarUtils;
import com.android.internal.util.systemui.qs.QSLayoutUtils;
import com.android.internal.util.ArrayUtils;
import com.android.settingslib.drawable.CircleFramedDrawable;
import com.android.settingslib.net.DataUsageController;
import com.android.settingslib.Utils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.battery.BatteryMeterView;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.statusbar.phone.StatusBarContentInsetsProvider;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.phone.StatusBarIconController.TintedIconManager;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.NetworkTraffic;
import com.android.systemui.statusbar.policy.VariableDateView;
import com.android.systemui.util.LargeScreenUtils;
import com.android.systemui.tuner.TunerService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * View that contains the top-most bits of the QS panel (primarily the status bar with date, time,
 * battery, carrier info and privacy icons) and also contains the {@link QuickQSPanel}.
 */
public class QuickStatusBarHeader extends FrameLayout implements TunerService.Tunable,
        View.OnClickListener, View.OnLongClickListener {

    private static final int CLOCK_POSITION_LEFT = 2;
    private static final int CLOCK_POSITION_HIDE = 3;

    private static final String SHOW_QS_CLOCK =
            "system:" + Settings.System.SHOW_QS_CLOCK;
    private static final String SHOW_QS_DATE =
            "system:" + Settings.System.SHOW_QS_DATE;
    public static final String STATUS_BAR_BATTERY_STYLE =
            "system:" + Settings.System.STATUS_BAR_BATTERY_STYLE;
    public static final String QS_BATTERY_STYLE =
            "system:" + Settings.System.QS_BATTERY_STYLE;
    public static final String QS_BATTERY_LOCATION =
            "system:" + Settings.System.QS_BATTERY_LOCATION;
    private static final String QS_SHOW_BATTERY_PERCENT =
            "system:" + Settings.System.QS_SHOW_BATTERY_PERCENT;
    private static final String QS_SHOW_BATTERY_ESTIMATE =
            "system:" + Settings.System.QS_SHOW_BATTERY_ESTIMATE;

    private static final String QS_HEADER_IMAGE =
            "system:" + Settings.System.QS_HEADER_IMAGE;
    private static final String NETWORK_TRAFFIC_LOCATION =
            "system:" + Settings.System.NETWORK_TRAFFIC_LOCATION;

    private static final String QS_PANEL_STYLE =
            "system:" + Settings.System.QS_PANEL_STYLE;

    private boolean mExpanded;
    private boolean mQsDisabled;

    @Nullable
    private TouchAnimator mAlphaAnimator;
    @Nullable
    private TouchAnimator mTranslationAnimator;
    @Nullable
    private TouchAnimator mIconsAlphaAnimator;
    private TouchAnimator mIconsAlphaAnimatorFixed;

    protected QuickQSPanel mHeaderQsPanel;
    private View mDatePrivacyView;
    private TextClock mClockView;
    private TextClock mOosDate;
    private View mStatusIconsView;
    private View mContainer;

    private ViewGroup mClockContainer;
    private Space mDatePrivacySeparator;
    private View mClockIconsSeparator;
    private boolean mShowClockIconsSeparator;
    private View mRightLayout;
    private View mPrivacyContainer;

    private BatteryMeterView mBatteryRemainingIcon;
    private BatteryMeterView mBatteryIcon;
    private StatusIconContainer mIconContainer;
    private View mPrivacyChip;
    
    // Oplus
    private View mOosClockLayout;
    private View mOosClockContainer;
    private View mOosClockDateContainer;
    private View mSettingsShortcut;
    private TextView mQsDataUsageText;

    // Oplus QS tiles / data usage
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;
    private SubscriptionManager mSubManager;
    private DataUsageController mDataController;

    // QS Header
    private ImageView mQsHeaderImageView;
    private View mQsHeaderLayout;
    private boolean mHeaderImageEnabled;
    private int mHeaderImageValue;
    
    // QS styles
    private int mQsUIStyle;
    private boolean mTintAlpha;

    @Nullable
    private TintedIconManager mTintedIconManager;
    @Nullable
    private QSExpansionPathInterpolator mQSExpansionPathInterpolator;
    private StatusBarContentInsetsProvider mInsetsProvider;

    private int mRoundedCornerPadding = 0;
    private int mStatusBarPaddingTop;
    private int mStatusBarPaddingStart;
    private int mStatusBarPaddingEnd;
    private int mHeaderPaddingLeft;
    private int mHeaderPaddingRight;
    private int mWaterfallTopInset;
    private int mCutOutPaddingLeft;
    private int mCutOutPaddingRight;
    private float mKeyguardExpansionFraction;
    private int mTextColorPrimary = Color.TRANSPARENT;
    private int mTopViewMeasureHeight;

    @NonNull
    private List<String> mRssiIgnoredSlots = List.of();

    private boolean mHasLeftCutout;
    private boolean mHasRightCutout;

    private boolean mUseCombinedQSHeader;
    private boolean mShowDate;

    private final ActivityStarter mActivityStarter;
    private final Vibrator mVibrator;

    private int mStatusBarBatteryStyle, mQSBatteryStyle, mQSBatteryLocation;

    private NetworkTraffic mNetworkTraffic;
    private boolean mShowNetworkTraffic;

    public QuickStatusBarHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
        mActivityStarter = Dependency.get(ActivityStarter.class);
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mSubManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        mDataController = new DataUsageController(context);
    }

    private boolean isWifiConnected() {
        final Network network = mConnectivityManager.getActiveNetwork();
        if (network != null) {
            NetworkCapabilities capabilities = mConnectivityManager.getNetworkCapabilities(network);
                return capabilities != null &&
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } else {
            return false;
        }
    }

    /**
     * How much the view containing the clock and QQS will translate down when QS is fully expanded.
     *
     * This matches the measured height of the view containing the date and privacy icons.
     */
    public int getOffsetTranslation() {
        return mTopViewMeasureHeight;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Context context = getContext();

        mHeaderQsPanel = findViewById(R.id.quick_qs_panel);
        mDatePrivacyView = findViewById(R.id.quick_status_bar_date_privacy);
        mStatusIconsView = findViewById(R.id.quick_qs_status_icons);
        mContainer = findViewById(R.id.qs_container);
        mIconContainer = findViewById(R.id.statusIcons);
        mPrivacyChip = findViewById(R.id.privacy_chip);
        mClockIconsSeparator = findViewById(R.id.separator);
        mRightLayout = findViewById(R.id.rightLayout);
        mPrivacyContainer = findViewById(R.id.privacy_container);

        mClockContainer = findViewById(R.id.clock_container);

        // Tint for the battery icons are handled in setupHost()
        mBatteryRemainingIcon = findViewById(R.id.batteryRemainingIcon);
        mBatteryRemainingIcon.mQS = true;
        mBatteryRemainingIcon.setOnClickListener(this);
        mBatteryRemainingIcon.setOnLongClickListener(this);

        mBatteryIcon = findViewById(R.id.batteryIcon);

	mQsHeaderLayout = findViewById(R.id.layout_header);
        mQsHeaderImageView = findViewById(R.id.qs_header_image_view);
        mQsHeaderImageView.setClipToOutline(true);

        mNetworkTraffic = findViewById(R.id.network_traffic);

        // Oplus
        mOosClockLayout = findViewById(R.id.oos_qs_container);
        mOosClockDateContainer = findViewById(R.id.oos_clock_date_container);
        mOosClockContainer = findViewById(R.id.oos_clock);
        mOosClockContainer.setOnClickListener(this);
        mOosClockContainer.setOnLongClickListener(this);
        mOosDate = findViewById(R.id.oos_date);
        mOosDate.setOnClickListener(this);
        mOosDate.setOnLongClickListener(this);
        mSettingsShortcut = findViewById(R.id.custom_oplus_shortcut);
        mSettingsShortcut.setOnClickListener(this);
        mQsDataUsageText = findViewById(R.id.oos_carrier_label);
        mQsDataUsageText.setOnClickListener(this);

        updateResources();

        mIconsAlphaAnimatorFixed = new TouchAnimator.Builder()
                .addFloat(mBatteryRemainingIcon, "alpha", 0, 1)
                .build();

        updateResources();

        Dependency.get(TunerService.class).addTunable(this,
                SHOW_QS_CLOCK,
                SHOW_QS_DATE,
                STATUS_BAR_BATTERY_STYLE,
                QS_BATTERY_STYLE,
                QS_BATTERY_LOCATION,
                QS_SHOW_BATTERY_PERCENT,
                QS_SHOW_BATTERY_ESTIMATE,
                QS_HEADER_IMAGE,
                QS_PANEL_STYLE,
                NETWORK_TRAFFIC_LOCATION);
    }

    void onAttach(TintedIconManager iconManager,
            QSExpansionPathInterpolator qsExpansionPathInterpolator,
            List<String> rssiIgnoredSlots,
            StatusBarContentInsetsProvider insetsProvider,
            boolean useCombinedQSHeader) {
        mUseCombinedQSHeader = useCombinedQSHeader;
        mTintedIconManager = iconManager;
        mRssiIgnoredSlots = rssiIgnoredSlots;
        mInsetsProvider = insetsProvider;
        int fillColor = Utils.getColorAttrDefaultColor(getContext(),
                android.R.attr.textColorPrimary);

        // Set the correct tint for the status icons so they contrast
        iconManager.setTint(fillColor);

        mQSExpansionPathInterpolator = qsExpansionPathInterpolator;
        updateAnimators();
        setUsageTextAsync();
    }

    public QuickQSPanel getHeaderQsPanel() {
        return mHeaderQsPanel;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mStatusIconsView.getMeasuredHeight() != mTopViewMeasureHeight) {
            mTopViewMeasureHeight = mStatusIconsView.getMeasuredHeight();
            updateAnimators();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateResources();
    }

    @Override
    public void onClick(View v) {
        // Clock view is still there when the panel is not expanded
        // Making sure we get the date action when the user clicks on it
        // but actually is seeing the date
        if (v == mOosClockContainer) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    AlarmClock.ACTION_SHOW_ALARMS), 0);
        } else if (v == mOosDate) {
            Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
            builder.appendPath("time");
            builder.appendPath(Long.toString(System.currentTimeMillis()));
            Intent todayIntent = new Intent(Intent.ACTION_VIEW, builder.build());
            mActivityStarter.postStartActivityDismissingKeyguard(todayIntent, 0);
        } else if (v == mSettingsShortcut) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent("android.settings.SETTINGS"), 0);
        } else if (v == mQsDataUsageText) {
            Intent nIntent = new Intent(Intent.ACTION_MAIN);
            nIntent.setClassName("com.android.settings",
                    "com.android.settings.Settings$DataUsageSummaryActivity");
            mActivityStarter.startActivity(nIntent, true /* dismissShade */);
        } else if (v == mBatteryRemainingIcon) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    Intent.ACTION_POWER_USAGE_SUMMARY), 0);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (v == mOosClockContainer || v == mOosDate) {
            Intent nIntent = new Intent(Intent.ACTION_MAIN);
            nIntent.setClassName("com.android.settings",
                    "com.android.settings.Settings$DateTimeSettingsActivity");
            mActivityStarter.startActivity(nIntent, true /* dismissShade */);
            mVibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            return true;
        } else if (v == mBatteryRemainingIcon) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    Intent.ACTION_POWER_USAGE_SUMMARY), 0);
            return true;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // If using combined headers, only react to touches inside QuickQSPanel
        if (!mUseCombinedQSHeader || event.getY() > mHeaderQsPanel.getTop()) {
            return super.onTouchEvent(event);
        } else {
            return false;
        }
    }

    void updateResources() {
        Resources resources = mContext.getResources();
        boolean largeScreenHeaderActive =
                LargeScreenUtils.shouldUseLargeScreenShadeHeader(resources);

        boolean gone = largeScreenHeaderActive || mUseCombinedQSHeader || mQsDisabled;
        mStatusIconsView.setVisibility(gone ? View.GONE : View.VISIBLE);
        mDatePrivacyView.setVisibility(gone ? View.GONE : View.VISIBLE);

        mRoundedCornerPadding = resources.getDimensionPixelSize(
                R.dimen.rounded_corner_content_padding);

        mStatusBarPaddingStart = resources.getDimensionPixelSize(
                R.dimen.status_bar_padding_start);
        mStatusBarPaddingEnd = resources.getDimensionPixelSize(
                R.dimen.status_bar_padding_end);

        int statusBarHeight = SystemBarUtils.getStatusBarHeight(mContext);

        mStatusBarPaddingTop = resources.getDimensionPixelSize(
                R.dimen.status_bar_padding_top);

       int quickQsOffsetHeight = resources.getDimensionPixelSize(R.dimen.oos_quick_qs_offset_height);

        ViewGroup.LayoutParams datePrivacyLayoutParams = mDatePrivacyView.getLayoutParams();
        datePrivacyLayoutParams.height = Math.max(quickQsOffsetHeight, mDatePrivacyView.getMinimumHeight());
        mDatePrivacyView.setLayoutParams(datePrivacyLayoutParams);

        ViewGroup.LayoutParams statusIconsLayoutParams = mStatusIconsView.getLayoutParams();
        statusIconsLayoutParams.height = Math.max(quickQsOffsetHeight, mStatusIconsView.getMinimumHeight());
        mStatusIconsView.setLayoutParams(statusIconsLayoutParams);

        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        layoutParams.height = mQsDisabled ? mStatusIconsView.getLayoutParams().height : ViewGroup.LayoutParams.WRAP_CONTENT;
        setLayoutParams(layoutParams);

        int textColor = Utils.getColorAttrDefaultColor(mContext, android.R.attr.textColorPrimary);
        if (textColor != mTextColorPrimary) {
            int isCircleBattery = mBatteryIcon.getBatteryStyle();
            int textColorSecondary = Utils.getColorAttrDefaultColor(mContext,
                    (isCircleBattery == 1 || isCircleBattery == 2 || isCircleBattery == 3)
                    ? android.R.attr.textColorHint : android.R.attr.textColorSecondary);
            mTextColorPrimary = textColor;
            if (mTintedIconManager != null) {
                mTintedIconManager.setTint(textColor);
            }
            if ((mBatteryRemainingIcon.getBatteryStyle() == BATTERY_STYLE_CIRCLE)
                    || (mBatteryRemainingIcon.getBatteryStyle() == BATTERY_STYLE_DOTTED_CIRCLE)
                    || (mBatteryRemainingIcon.getBatteryStyle() == BATTERY_STYLE_FULL_CIRCLE)) {
                final float factor = 0.3f;
                textColorSecondary = Color.argb(
                    Color.alpha(textColor),
                    Math.round(Color.red(textColor) * factor),
                    Math.round(Color.green(textColor) * factor),
                    Math.round(Color.blue(textColor) * factor)
                );
            }
            mBatteryRemainingIcon.updateColors(mTextColorPrimary, textColorSecondary,
                    mTextColorPrimary);
            mBatteryIcon.updateColors(mTextColorPrimary, textColorSecondary,
                    mTextColorPrimary);
            mNetworkTraffic.setTint(textColor);
        }

        ViewGroup.MarginLayoutParams headerQsPanelLayoutParams = (ViewGroup.MarginLayoutParams) mHeaderQsPanel.getLayoutParams();
        if (largeScreenHeaderActive || !mUseCombinedQSHeader) {
            quickQsOffsetHeight = resources.getDimensionPixelSize(R.dimen.oplus_qqs_layout_margin_top);
        }
        headerQsPanelLayoutParams.topMargin = quickQsOffsetHeight;
        mHeaderQsPanel.setLayoutParams(headerQsPanelLayoutParams);

        updateQSHeaderImage();

        updateHeadersPadding();
        
        Configuration config = mContext.getResources().getConfiguration();
        boolean isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;

        mOosClockLayout.setVisibility(mQsDisabled || isLandscape ? View.GONE : View.VISIBLE);

        setUsageTextAsync();
    }

    private void updateQSHeaderImage() {
        if (!mHeaderImageEnabled) {
            mQsHeaderLayout.setVisibility(View.GONE);
            return;
        }
        Configuration config = mContext.getResources().getConfiguration();
        if (config.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            boolean mIsNightMode = (mContext.getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            int fadeFilter = ColorUtils.blendARGB(Color.TRANSPARENT, mIsNightMode ?
                Color.BLACK : Color.WHITE, 30 / 100f);
            int resId = getResources().getIdentifier("qs_header_image_" +
                String.valueOf(mHeaderImageValue), "drawable", "com.android.systemui");
	    mQsHeaderImageView.setImageResource(resId);
	    mQsHeaderImageView.setColorFilter(fadeFilter, PorterDuff.Mode.SRC_ATOP);
            mQsHeaderLayout.setVisibility(View.VISIBLE);
	} else {
            mQsHeaderLayout.setVisibility(View.GONE);
	}
    }

    public void updateAnimators() {
        boolean useCombinedQSHeader = mUseCombinedQSHeader;
        if (useCombinedQSHeader) {
            mTranslationAnimator = null;
            return;
        }

       TouchAnimator.Builder alphaAnimatorBuilder = new TouchAnimator.Builder();
                   alphaAnimatorBuilder.addFloat(mQsDataUsageText, "alpha", 0, 1);
                   alphaAnimatorBuilder.addFloat(mIconContainer, "alpha", 1, 0, 1);
            alphaAnimatorBuilder.mListener = new TouchAnimator.ListenerAdapter() {
                @Override
                public void onAnimationAtStart() {
                    mQsDataUsageText.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationAtEnd() {
                    mQsDataUsageText.setVisibility(View.VISIBLE);
                    setUsageTextAsync();
                }

                @Override
                public void onAnimationStarted() {
                    mQsDataUsageText.setVisibility(View.VISIBLE);
                }
            };

        mAlphaAnimator = alphaAnimatorBuilder.build();

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        int rightMargin = layoutParams.rightMargin;
        int halfRightMargin = rightMargin / 2;
        int footerBackgroundSize = mContext.getResources().getDimensionPixelSize(R.dimen.oplus_footer_background_size);
        int rightMarginOffset2 = footerBackgroundSize + 2;
        int carrierTranslateY = footerBackgroundSize - (footerBackgroundSize / 3);
        int oosDateOffset = mContext.getResources().getDimensionPixelSize(R.dimen.oos_date_offset);

        TouchAnimator.Builder translationAnimatorBuilder = new TouchAnimator.Builder();
        translationAnimatorBuilder.addFloat(mHeaderQsPanel, "translationY", 0.0f, mTopViewMeasureHeight)
                .addFloat(mDatePrivacyView, "translationY", halfRightMargin, 30.0f)
                .addFloat(mOosClockLayout, "translationY", 0.0f, 30.0f)
                .addFloat(mOosDate, "translationY", halfRightMargin, rightMarginOffset2)
                .addFloat(mOosDate, "translationX", 0.0f, -oosDateOffset)
                .addFloat(mOosClockContainer, "scaleX", 1.0f, 2.0f)
                .addFloat(mOosClockContainer, "scaleY", 1.0f, 2.0f)
                .addFloat(mOosClockContainer, "translationY", halfRightMargin, carrierTranslateY)
                .addFloat(mOosClockContainer, "translationX", 0.0f, oosDateOffset / 2)
                .addFloat(mSettingsShortcut, "rotation", 0.0f, 180.0f);
        translationAnimatorBuilder.mInterpolator = mQSExpansionPathInterpolator != null ? mQSExpansionPathInterpolator.getYInterpolator() : null;
        translationAnimatorBuilder.mStartDelay = 0.22f;
        mTranslationAnimator = translationAnimatorBuilder.build();
    }

    private void setUsageTextAsync() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                setUsageText();
            }
        });
    }

    private void setUsageText() {
        if (mQsDataUsageText == null) return;
        if (!isWifiConnected()) {
            mDataController.setSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId());
        }

        DataUsageController.DataUsageInfo info = isWifiConnected() ? mDataController.getWifiMonthlyDataUsageInfo() : mDataController.getMonthlyDataUsageInfo();
        boolean showData = info != null && info.usageLevel > 0;
        String suffix = isWifiConnected() ? getWifiSsid() : getSlotCarrierName();
        String dataUsage = suffix + ": " + formatDataUsage(info.usageLevel) + " " + mContext.getResources().getString(R.string.usage_data);

        // Update the UI on the main thread
        mQsDataUsageText.post(new Runnable() {
            @Override
            public void run() {
                if (showData) {
                    mQsDataUsageText.setText(dataUsage);
                } else {
                    mQsDataUsageText.setText(mContext.getResources().getString(R.string.usage_data_unavailable));
                }
            }
        });
    }

    private String getWifiSsid() {
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            return mContext.getResources().getString(R.string.usage_wifi_default_suffix);
        }

        String ssid = wifiInfo.getSSID();
        if (ssid == null || ssid.equals(WifiManager.UNKNOWN_SSID)) {
            return mContext.getResources().getString(R.string.usage_wifi_default_suffix);
        }
        
        return ssid.replace("\"", "");
    }

    private String getSlotCarrierName() {
        CharSequence result = mContext.getResources().getString(R.string.usage_data_default_suffix);
        int subId = mSubManager.getDefaultDataSubscriptionId();
        List<SubscriptionInfo> subInfoList = mSubManager.getActiveSubscriptionInfoList(true);
        if (subInfoList != null) {
            for (SubscriptionInfo subInfo : subInfoList) {
                if (subInfo != null && subId == subInfo.getSubscriptionId()) {
                    result = subInfo.getDisplayName();
                    break;
                }
            }
        }
        
        return result.toString();
    }

    private CharSequence formatDataUsage(long byteValue) {
        final BytesResult res = Formatter.formatBytes(mContext.getResources(), byteValue,
                Formatter.FLAG_IEC_UNITS);
        return BidiFormatter.getInstance().unicodeWrap(mContext.getString(
                com.android.internal.R.string.fileSizeSuffix, res.value, res.units));
    }

    void setChipVisibility(boolean visibility) {
        boolean showBattery = mQSBatteryLocation == 1
                && (mBatteryIcon.getBatteryStyle() != 5
                || mBatteryIcon.getBatteryEstimate() != 0);
        if (showBattery) {
            mBatteryIcon.setVisibility(visibility ? View.GONE : View.VISIBLE);
        }
        mNetworkTraffic.setChipVisibility(visibility);
        if (visibility || showBattery || mShowNetworkTraffic) {
            // Animates the icons and battery indicator from alpha 0 to 1, when the chip is visible
            mIconsAlphaAnimator = mIconsAlphaAnimatorFixed;
            mIconsAlphaAnimator.setPosition(mKeyguardExpansionFraction);
        } else {
            mIconsAlphaAnimator = null;
            mIconContainer.setAlpha(1);
            mBatteryRemainingIcon.setAlpha(1);
        }
    }

    /** */
    public void setExpanded(boolean expanded, QuickQSPanelController quickQSPanelController) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        quickQSPanelController.setExpanded(expanded);
        updateEverything();
        if (expanded && mPrivacyChip != null && mPrivacyChip.getVisibility() == View.VISIBLE) {
            mBatteryRemainingIcon.setAlpha(0);
        }
    }

    /**
     * Animates the inner contents based on the given expansion details.
     *
     * @param forceExpanded whether we should show the state expanded forcibly
     * @param expansionFraction how much the QS panel is expanded/pulled out (up to 1f)
     * @param panelTranslationY how much the panel has physically moved down vertically (required
     *                          for keyguard animations only)
     */
    public void setExpansion(boolean forceExpanded, float expansionFraction,
                             float panelTranslationY) {
        final float keyguardExpansionFraction = forceExpanded ? 1f : expansionFraction;

        if (mAlphaAnimator != null) {
            mAlphaAnimator.setPosition(keyguardExpansionFraction);
        }
        if (mTranslationAnimator != null) {
            mTranslationAnimator.setPosition(keyguardExpansionFraction);
        }
        if (mIconsAlphaAnimator != null && mPrivacyChip != null && mPrivacyChip.getVisibility() == View.GONE) {
            mIconsAlphaAnimator.setPosition(keyguardExpansionFraction);
        }
        // If forceExpanded (we are opening QS from lockscreen), the animators have been set to
        // position = 1f.
        if (forceExpanded) {
            setAlpha(expansionFraction);
        } else {
            setAlpha(1);
        }

        mKeyguardExpansionFraction = keyguardExpansionFraction;
    }

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        mHeaderQsPanel.setDisabledByPolicy(disabled);
        mStatusIconsView.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
        updateResources();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        // Handle padding of the views
        DisplayCutout cutout = insets.getDisplayCutout();

        Pair<Integer, Integer> sbInsets = mInsetsProvider
                .getStatusBarContentInsetsForCurrentRotation();
        boolean hasCornerCutout = mInsetsProvider.currentRotationHasCornerCutout();

        LinearLayout.LayoutParams lpC =
                (LinearLayout.LayoutParams) mClockContainer.getLayoutParams();
        lpC.width = 0;
        lpC.weight = 1f;
        mClockContainer.setLayoutParams(lpC);
        
        LinearLayout.LayoutParams lpR=
                (LinearLayout.LayoutParams) mRightLayout.getLayoutParams();
        lpR.width = 0;
        lpR.weight = 1f;
        mRightLayout.setLayoutParams(lpR);

        LinearLayout.LayoutParams datePrivacySeparatorLayoutParams =
                (LinearLayout.LayoutParams) mDatePrivacySeparator.getLayoutParams();
        LinearLayout.LayoutParams mClockIconsSeparatorLayoutParams =
                (LinearLayout.LayoutParams) mClockIconsSeparator.getLayoutParams();
        if (cutout != null) {
            Rect topCutout = cutout.getBoundingRectTop();
            if (topCutout.isEmpty() || hasCornerCutout) {
                datePrivacySeparatorLayoutParams.width = 0;
                mDatePrivacySeparator.setVisibility(View.GONE);
                mClockIconsSeparatorLayoutParams.width = topCutout.width();
                setSeparatorVisibility(false);
                mShowClockIconsSeparator = false;
                if (sbInsets.first != 0) {
                    mHasLeftCutout = true;
                }
                if (sbInsets.second != 0) {
                    mHasRightCutout = true;
                }
            } else {
                datePrivacySeparatorLayoutParams.width = topCutout.width();
                mDatePrivacySeparator.setVisibility(View.VISIBLE);
                mClockIconsSeparatorLayoutParams.width = topCutout.width();
                mShowClockIconsSeparator = true;
                setSeparatorVisibility(mKeyguardExpansionFraction == 0f);
                mHasLeftCutout = false;
                mHasRightCutout = false;
            }
        }
        mDatePrivacySeparator.setLayoutParams(datePrivacySeparatorLayoutParams);
        mClockIconsSeparator.setLayoutParams(mClockIconsSeparatorLayoutParams);
        mCutOutPaddingLeft = sbInsets.first;
        mCutOutPaddingRight = sbInsets.second;
        mWaterfallTopInset = cutout == null ? 0 : cutout.getWaterfallInsets().top;

        updateHeadersPadding();
        return super.onApplyWindowInsets(insets);
    }

    /**
     * Sets the visibility of the separator between clock and icons.
     *
     * This separator is "visible" when there is a center cutout, to block that space. In that
     * case, the clock and the layout on the right (containing the icons and the battery meter) are
     * set to weight 1 to take the available space.
     * @param visible whether the separator between clock and icons should be visible.
     */
    private void setSeparatorVisibility(boolean visible) {
        int newVisibility = visible ? View.VISIBLE : View.GONE;
        if (mClockIconsSeparator.getVisibility() == newVisibility) return;

        mClockIconsSeparator.setVisibility(visible ? View.VISIBLE : View.GONE);

        LinearLayout.LayoutParams lp =
                (LinearLayout.LayoutParams) mClockContainer.getLayoutParams();
        lp.width = visible ? 0 : WRAP_CONTENT;
        lp.weight = visible ? 1f : 0f;
        mClockContainer.setLayoutParams(lp);

        lp = (LinearLayout.LayoutParams) mRightLayout.getLayoutParams();
        lp.width = visible ? 0 : WRAP_CONTENT;
        lp.weight = visible ? 1f : 0f;
        mRightLayout.setLayoutParams(lp);
    }

    public void updateHeadersPadding() {
        ViewGroup.MarginLayoutParams datePrivacyLayoutParams = (ViewGroup.MarginLayoutParams) mDatePrivacyView.getLayoutParams();
        datePrivacyLayoutParams.setMarginStart(0);
        datePrivacyLayoutParams.setMarginEnd(0);
        mDatePrivacyView.setLayoutParams(datePrivacyLayoutParams);

        ViewGroup.MarginLayoutParams statusIconsLayoutParams = (ViewGroup.MarginLayoutParams) mStatusIconsView.getLayoutParams();
        statusIconsLayoutParams.setMarginStart(0);
        statusIconsLayoutParams.setMarginEnd(0);
        mStatusIconsView.setLayoutParams(statusIconsLayoutParams);

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        int leftMargin = layoutParams.leftMargin;
        int rightMargin = layoutParams.rightMargin;

        int paddingLeft = 0;
        int paddingRight = 0;

        if (mCutOutPaddingLeft > 0) {
            paddingLeft = Math.max(Math.max(mCutOutPaddingLeft, mRoundedCornerPadding) - leftMargin, 0);
        }

        if (mCutOutPaddingRight > 0) {
            paddingRight = Math.max(Math.max(mCutOutPaddingRight, mRoundedCornerPadding) - rightMargin, 0);
        }

        mDatePrivacyView.setPadding(paddingLeft, mWaterfallTopInset, paddingRight, 0);
        mStatusIconsView.setPadding(paddingLeft, mWaterfallTopInset, paddingRight, 0);
    }

    public void updateEverything() {
        post(() -> setClickable(!mExpanded));
    }

    private void setContentMargins(View view, int marginStart, int marginEnd) {
        MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
        lp.setMarginStart(marginStart);
        lp.setMarginEnd(marginEnd);
        view.setLayoutParams(lp);
    }

    /**
     * Scroll the headers away.
     *
     * @param scrollY the scroll of the QSPanel container
     */
    public void setExpandedScrollAmount(int scrollY) {
        mStatusIconsView.setScrollY(scrollY);
        mDatePrivacyView.setScrollY(scrollY);
    }

    private void updateBatteryStyle() {
        int style;
        if (mQSBatteryStyle == -1) {
            style = mStatusBarBatteryStyle;
        } else {
            style = mQSBatteryStyle;
        }
        mBatteryRemainingIcon.setBatteryStyle(style);
        mBatteryIcon.setBatteryStyle(style);
        setChipVisibility(mPrivacyChip.getVisibility() == View.VISIBLE);
    }

    public BatteryMeterView getBatteryMeterView() {
        if (mQSBatteryLocation == 0) {
            return mBatteryRemainingIcon;
        }
        return mBatteryIcon;
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case SHOW_QS_CLOCK:
                boolean showClock =
                        TunerService.parseIntegerSwitch(newValue, true);
                mClockView.setClockVisibleByUser(showClock);
                break;
            case SHOW_QS_DATE:
                mShowDate =
                        TunerService.parseIntegerSwitch(newValue, true);
                mDateContainer.setVisibility(mShowDate ? View.VISIBLE : View.GONE);
                mClockDateView.setVisibility(mShowDate ? View.VISIBLE : View.GONE);
                break;
            case QS_BATTERY_STYLE:
                mQSBatteryStyle =
                        TunerService.parseInteger(newValue, -1);
                updateBatteryStyle();
                break;
            case STATUS_BAR_BATTERY_STYLE:
                mStatusBarBatteryStyle =
                        TunerService.parseInteger(newValue, 0);
                updateBatteryStyle();
                break;
            case QS_BATTERY_LOCATION:
                mQSBatteryLocation =
                        TunerService.parseInteger(newValue, 0);
                if (mQSBatteryLocation == 0) {
                    mBatteryIcon.setVisibility(View.GONE);
                    mBatteryRemainingIcon.setVisibility(View.VISIBLE);
                } else {
                    mBatteryRemainingIcon.setVisibility(View.GONE);
                    mBatteryIcon.setVisibility(View.VISIBLE);
                }
                setChipVisibility(mPrivacyChip.getVisibility() == View.VISIBLE);
                break;
            case QS_SHOW_BATTERY_PERCENT:
                mBatteryRemainingIcon.setBatteryPercent(
                        TunerService.parseInteger(newValue, 2));
                mBatteryIcon.setBatteryPercent(
                        TunerService.parseInteger(newValue, 2));
                break;
            case QS_SHOW_BATTERY_ESTIMATE:
                mBatteryRemainingIcon.setBatteryEstimate(
                        TunerService.parseInteger(newValue, 0));
                mBatteryIcon.setBatteryEstimate(
                        TunerService.parseInteger(newValue, 0));
                setChipVisibility(mPrivacyChip.getVisibility() == View.VISIBLE);
                break;
            case QS_HEADER_IMAGE:
                mHeaderImageValue =
                       TunerService.parseInteger(newValue, 0);
                mHeaderImageEnabled = mHeaderImageValue != 0;
                updateResources();
                break;
            case NETWORK_TRAFFIC_LOCATION:
                mShowNetworkTraffic =
                        TunerService.parseInteger(newValue, 0) == 2;
                setChipVisibility(mPrivacyChip.getVisibility() == View.VISIBLE);
                break;
            case QS_PANEL_STYLE:
                mQsUIStyle =
                       TunerService.parseInteger(newValue, 0);
                mTintAlpha = mQsUIStyle == 1 || mQsUIStyle == 2 || mQsUIStyle == 10;
                updateResources();
                break;
            default:
                break;
        }
    }
}
