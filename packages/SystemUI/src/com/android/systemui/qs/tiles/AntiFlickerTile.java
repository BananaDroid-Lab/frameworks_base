/*
 * Copyright (C) 2020-2021 The LineageOS Project
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

import static com.android.internal.lineage.hardware.LiveDisplayManager.FEATURE_ANTI_FLICKER;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.internal.lineage.hardware.LiveDisplayManager;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import javax.inject.Inject;


public class AntiFlickerTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "anti_flicker";

    private boolean mAntiFlickerAvailable = true;
    private boolean mReceiverRegistered;

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_qs_anti_flicker);

    private static final Intent DISPLAY_SETTINGS = new Intent("android.settings.DISPLAY_SETTINGS");

    private final LiveDisplayManager mLiveDisplay;

    @Inject
    public AntiFlickerTile(
            QSHost host,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mLiveDisplay = LiveDisplayManager.getInstance(mContext);
        if (!updateConfig()) {
            mContext.registerReceiver(mReceiver,
                    new IntentFilter("lineageos.intent.action.INITIALIZE_LIVEDISPLAY"));
            mReceiverRegistered = true;
        }
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        unregisterReceiver();
    }

    private void unregisterReceiver() {
        if (mReceiverRegistered) {
            mContext.unregisterReceiver(mReceiver);
            mReceiverRegistered = false;
        }
    }

    private boolean updateConfig() {
        if (mLiveDisplay.getConfig() != null) {
            mAntiFlickerAvailable = mLiveDisplay.getConfig().hasFeature(FEATURE_ANTI_FLICKER);
            if (!isAvailable()) {
                mHost.removeTile(getTileSpec());
            }
            return true;
        }
        return false;
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(@Nullable View view) {
        setEnabled(!mLiveDisplay.isAntiFlickerEnabled());
        refreshState();
    }

    private void setEnabled(boolean enabled) {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.DISPLAY_ANTI_FLICKER, enabled ? 1 : 0);
    }

    @Override
    public Intent getLongClickIntent() {
        return DISPLAY_SETTINGS;
    }

    @Override
    public boolean isAvailable() {
        return mAntiFlickerAvailable;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = mLiveDisplay.isAntiFlickerEnabled();
        state.icon = mIcon;
        state.contentDescription = mContext.getString(R.string.quick_settings_anti_flicker);
        state.state = (state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        state.label = getTileLabel();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_anti_flicker);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.BANANADROID;
    }

    @Override
    public void handleSetListening(boolean listening) {}

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateConfig();
            unregisterReceiver();
        }
    };
}
