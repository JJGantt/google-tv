package com.jaredgantt.tvcompanion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/** Read-only capability probes, triggered over adb:
 *    am broadcast -n com.jaredgantt.tvcompanion/.ProbeReceiver
 *  Results go to logcat under TvCompanion. */
public class ProbeReceiver extends BroadcastReceiver {
    static final String TAG = BootReceiver.TAG;

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Log.i(TAG, "probe: adb_wifi_enabled=" + Settings.Global.getInt(context.getContentResolver(), "adb_wifi_enabled", -1));
            Log.i(TAG, "probe: canDrawOverlays=" + Settings.canDrawOverlays(context));
        } catch (Exception e) {
            Log.e(TAG, "probe: settings", e);
        }
        try {
            if (intent.hasExtra("backlight")) {
                Display.setBacklight(intent.getIntExtra("backlight", 0));
                Log.i(TAG, "probe: setBacklight accepted");
            }
            Log.i(TAG, "probe: getBacklight=" + Display.backlight());
        } catch (Throwable e) {
            Log.e(TAG, "probe: tcl_tv_display", e);
        }
    }
}
