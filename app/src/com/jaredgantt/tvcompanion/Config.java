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

    /** The sidebar page. Loaded with the hub token in X-Hub-Token (`token`), never in the URL; the hub
     *  answers that page load with a cookie, which signs in the page's own requests. */
    static final String SIDEBAR = HUB + "/tv-sidebar";

    /** The wall (Hub): the same page the Pi's kiosk shows. */
    static final String WALL = HUB + "/tv";

    static String token(Context c) {
        return prefs(c).getString("hub_token", null);
    }
}
