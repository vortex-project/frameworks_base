/*
 * Copyright (C) 2016 The CyanogenMod Project
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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

import android.service.quicksettings.Tile;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.statusbar.policy.KeyguardMonitor;

/** Quick settings tile: Caffeine **/
public class CaffeineTile extends QSTileImpl<BooleanState> {

    private final PowerManager.WakeLock mWakeLock;
    private final Receiver mReceiver = new Receiver();
    private final KeyguardMonitor mKeyguard;

    public CaffeineTile(QSHost host) {
        super(host);
        mWakeLock = ((PowerManager) mContext.getSystemService(Context.POWER_SERVICE)).newWakeLock(
                PowerManager.FULL_WAKE_LOCK, "CaffeineTile");
        mReceiver.init();
        mKeyguard = Dependency.get(KeyguardMonitor.class);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mReceiver.destroy();
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    @Override
    public void handleSetListening(boolean listening) {
    }

    @Override
    public void handleClick() {
        if (mKeyguard.isSecure() && mKeyguard.isShowing()) {
            Dependency.get(ActivityStarter.class).postQSRunnableDismissingKeyguard(() -> {
                mHost.openPanels();
                onClick();
            });
            return;
        }
        onClick();
    }

    private void onClick(){
        // toggle
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        } else {
            mWakeLock.acquire();
        }
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public void handleLongClick() {
        handleClick();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_caffeine_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.CUSTOM_QUICK_TILES;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = mWakeLock.isHeld();
        state.label = mContext.getString(R.string.quick_settings_caffeine_label);
        if (state.value) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_caffeine_on);
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_caffeine_on);
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_caffeine_off);
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_caffeine_off);
            state.state = Tile.STATE_INACTIVE;
        }
    }

    private final class Receiver extends BroadcastReceiver {
        public void init() {
            // Register for Intent broadcasts for...
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            mContext.registerReceiver(this, filter, null, mHandler);
        }

        public void destroy() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                // disable caffeine if user force off (power button)
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
                refreshState();
            }
        }
    }
}