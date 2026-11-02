package com.jaredgantt.tvcompanion;

import android.os.IBinder;
import android.os.Parcel;

/** TCL's picture settings through its display service (docs/TCL-DISPLAY-SERVICE.md). Callable from an
 *  ordinary app: verified on this TV. The codes are the AIDL's method order, so they hold only for the
 *  jar with JAR_SHA256 (the same pin bin/tv carries); any other refuses rather than change some other
 *  setting. */
final class Display {
    private static final int SET_CONTRAST = 31, GET_CONTRAST = 32;
    private static final int SET_DYNAMIC_CONTRAST = 47, GET_DYNAMIC_CONTRAST = 48;
    private static final int SET_BACKLIGHT = 100, GET_BACKLIGHT = 101;
    private static final int WINDOW = 0, CURRENT_MODE = 0, EXEC_SAVE = 2;
    private static final String JAR = "/system_ext/framework/com.tcl.tv.display.jar";
    private static final String JAR_SHA256 = "c14f5ea2d1374ab5a6abeae0c6bbfa15a6a0129a12aad7908332c62fc44a4600";
    private static volatile boolean jarChecked;

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
        checkJar();
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

    private static void checkJar() throws Exception {
        if (jarChecked) return;
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream in = new java.io.FileInputStream(JAR)) {
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
        }
        StringBuilder hex = new StringBuilder();
        for (byte x : md.digest()) hex.append(String.format("%02x", x));
        if (!hex.toString().equals(JAR_SHA256))
            throw new Exception("display service changed (firmware update?): transaction codes need re-deriving");
        jarChecked = true;
    }

    private Display() {}
}
