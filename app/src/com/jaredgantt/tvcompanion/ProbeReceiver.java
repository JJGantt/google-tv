package com.jaredgantt.tvcompanion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Parcel;
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
            IBinder b = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "tcl_tv_display");
            if (intent.hasExtra("backlight")) {
                Log.i(TAG, "probe: setBacklight -> " + call(b, 100, 0, intent.getIntExtra("backlight", 0), 2));
            }
            Log.i(TAG, "probe: getBacklight=" + call(b, 101));
        } catch (Throwable e) {
            Log.e(TAG, "probe: tcl_tv_display", e);
        }
    }

    /** One ITvDisplayManagerService transaction with int arguments; returns the int result. */
    static int call(IBinder b, int code, int... args) throws Exception {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("com.tcl.tv.display.ITvDisplayManagerService");
            for (int a : args) data.writeInt(a);
            b.transact(code, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
