package com.jaredgantt.tvcompanion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/** At boot: starts voice control's link to the hub (TvLink), and turns wireless debugging back on, so remote control survives a reboot
 *  with no one at the TV. Needs WRITE_SECURE_SETTINGS, granted once over adb. */
public class BootReceiver extends BroadcastReceiver {
    static final String TAG = "TvCompanion";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            boolean ok = Settings.Global.putInt(context.getContentResolver(), "adb_wifi_enabled", 1);
            Log.i(TAG, "boot: adb_wifi_enabled=1 -> " + ok);
        } catch (SecurityException e) {
            Log.e(TAG, "boot: WRITE_SECURE_SETTINGS not granted", e);
        }
        // Voice control's connection to the hub (docs/VOICE.md).
        context.startForegroundService(new Intent(context, TvLink.class));
    }
}
