package com.jaredgantt.tvcompanion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Stores settings pushed over adb:
 *    am broadcast -n com.jaredgantt.tvcompanion/.ConfigReceiver --es hub_token <token>
 *  `bin/tv sidebar token` does this from the machine's own copy of the token. */
public class ConfigReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String token = intent.getStringExtra("hub_token");
        if (token != null && !token.isEmpty()) {
            Config.prefs(context).edit().putString("hub_token", token).apply();
            Log.i(BootReceiver.TAG, "config: hub token stored");
        }
    }
}
