package com.jaredgantt.tvcompanion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Runs `then` each time the screen turns on or off, whichever action TvLink registers it for. */
public class ScreenOn extends BroadcastReceiver {
    private final Runnable then;

    public ScreenOn(Runnable then) {
        this.then = then;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        then.run();
    }
}
