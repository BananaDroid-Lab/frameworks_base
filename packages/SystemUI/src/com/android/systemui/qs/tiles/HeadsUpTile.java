/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.android.systemui.qs.tiles;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.service.quicksettings.Tile;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;

import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.SettingObserver;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.util.settings.GlobalSettings;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import javax.inject.Inject;

/** Quick settings tile: Heads up **/
public class HeadsUpTile extends QSTileImpl<BooleanState> {

    private final SettingObserver mSetting;

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_qs_heads_up);

    private static final ComponentName HEADS_UP_SETTINGS_COMPONENT = new ComponentName(
            "com.android.settings", "com.android.settings.Settings$HeadsUpSettingsActivity");

    private static final Intent HEADS_UP_SETTINGS =
            new Intent().setComponent(HEADS_UP_SETTINGS_COMPONENT);

    @Inject
    public HeadsUpTile(
            QSHost host,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            GlobalSettings globalSettings,
            UserTracker userTracker
    ) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);

        mSetting = new SettingObserver(globalSettings, mHandler, Global.HEADS_UP_NOTIFICATIONS_ENABLED,
                userTracker.getUserId()) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                handleRefreshState(value);
            }
        };
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(@Nullable View view) {
        setEnabled(!mState.value);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return HEADS_UP_SETTINGS;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_heads_up_label);
    }

    private void setEnabled(boolean enabled) {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED,
                enabled ? 1 : 0);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {

        if (state.slash == null) {
            state.slash = new SlashState();
        }
        state.icon = mIcon;

        final int value = arg instanceof Integer ? (Integer)arg : mSetting.getValue();
        final boolean headsUp = value != 0;
        state.value = headsUp;
        state.label = mContext.getString(R.string.quick_settings_heads_up_label);
        if (headsUp) {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_heads_up_on);
            state.slash.isSlashed = false;
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_heads_up_off);
            state.slash.isSlashed = true;
            state.state = Tile.STATE_INACTIVE;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.BANANADROID;
    }

    @Override
    public void handleSetListening(boolean listening) {
        // Do nothing
    }
}
