package com.jaredgantt.tvcompanion;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings pushed over adb (ConfigReceiver), kept in the app's private preferences. The hub token
 *  lives only here and on the machines that push it; never in the repo. */
final class Config {
    static final String HUB = "https://jared-status-hub.fly.dev";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("config", Context.MODE_PRIVATE);
    }

    /** The sidebar page, signed in with the hub's device token (the hub sets its cookie from ?token=). */
    static String sidebarUrl(Context c) {
        String token = prefs(c).getString("hub_token", null);
        return token == null ? null : HUB + "/tv-sidebar?token=" + token;
    }
}
