package com.jaredgantt.tvcompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/** Draws text over whatever is on screen, including HDMI inputs:
 *    am start-foreground-service -n com.jaredgantt.tvcompanion/.OverlayService --es text "..." --ei seconds 5
 *  Needs SYSTEM_ALERT_WINDOW, granted with appops over adb. */
public class OverlayService extends Service {
    private TextView view;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("overlay", "Overlay", NotificationManager.IMPORTANCE_MIN));
        startForeground(1, new Notification.Builder(this, "overlay").setSmallIcon(android.R.drawable.ic_dialog_info).build());

        String text = intent != null ? intent.getStringExtra("text") : null;
        int seconds = intent != null ? intent.getIntExtra("seconds", 5) : 5;
        WindowManager wm = getSystemService(WindowManager.class);
        if (view != null) wm.removeView(view);
        view = new TextView(this);
        view.setText(text != null ? text : "");
        view.setTextSize(28);
        view.setTextColor(Color.WHITE);
        view.setBackgroundColor(0xCC000000);
        view.setPadding(40, 24, 40, 24);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = 60;
        lp.y = 60;
        wm.addView(view, lp);
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::stopSelf, seconds * 1000L);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (view != null) getSystemService(WindowManager.class).removeView(view);
        view = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
