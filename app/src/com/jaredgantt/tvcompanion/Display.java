package com.jaredgantt.tvcompanion;

import android.os.IBinder;
import android.os.Parcel;

/** TCL's picture settings through its display service (docs/TCL-DISPLAY-SERVICE.md). Callable from an
 *  ordinary app: verified on this TV. Codes shift if a firmware update adds methods; re-derive then. */
final class Display {
    private static final int SET_CONTRAST = 31, GET_CONTRAST = 32;
    private static final int SET_DYNAMIC_CONTRAST = 47, GET_DYNAMIC_CONTRAST = 48;
    private static final int SET_BACKLIGHT = 100, GET_BACKLIGHT = 101;
    private static final int WINDOW = 0, CURRENT_MODE = 0, EXEC_SAVE = 2;

    static int backlight() throws Exception { return call(GET_BACKLIGHT); }
    static int contrast() throws Exception { return call(GET_CONTRAST, WINDOW); }
    static int dynamicContrast() throws Exception { return call(GET_DYNAMIC_CONTRAST, WINDOW); }

    static void setBacklight(int v) throws Exception { check(call(SET_BACKLIGHT, CURRENT_MODE, range(v, 100), EXEC_SAVE)); }
    static void setContrast(int v) throws Exception { check(call(SET_CONTRAST, WINDOW, CURRENT_MODE, range(v, 100), EXEC_SAVE)); }
    static void setDynamicContrast(int v) throws Exception { check(call(SET_DYNAMIC_CONTRAST, WINDOW, CURRENT_MODE, range(v, 2), EXEC_SAVE)); }

    private static int range(int v, int hi) {
        if (v < 0 || v > hi) throw new IllegalArgumentException("0-" + hi);
        return v;
    }

    private static void check(int accepted) throws Exception {
        if (accepted == 0) throw new Exception("the TV refused it");
    }

    /** One ITvDisplayManagerService transaction with int arguments; returns the int result. */
    static int call(int code, int... args) throws Exception {
        IBinder b = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "tcl_tv_display");
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

    private Display() {}
}
