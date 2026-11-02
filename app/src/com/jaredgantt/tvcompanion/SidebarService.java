package com.jaredgantt.tvcompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** The session sidebar: the hub's /tv-sidebar page in a see-through strip down the right of the
 *  screen, over whatever is playing (HDMI included). Not focusable and not touchable, so the remote
 *  keeps driving the app underneath.
 *    am start-foreground-service -n com.jaredgantt.tvcompanion/.SidebarService -a show [--ef width 0.3]
 *    am start-foreground-service -n com.jaredgantt.tvcompanion/.SidebarService -a hide
 *  The hub URL and token come from ConfigReceiver. */
public class SidebarService extends Service {
    private WebView view;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("sidebar", "Sidebar", NotificationManager.IMPORTANCE_MIN));
        startForeground(2, new Notification.Builder(this, "sidebar").setSmallIcon(android.R.drawable.ic_dialog_info).build());

        String action = intent != null ? intent.getAction() : null;
        if ("hide".equals(action)) { stopSelf(); return START_NOT_STICKY; }

        String token = Config.token(this);
        if (token == null) {
            Log.e(BootReceiver.TAG, "sidebar: no hub token configured");
            stopSelf();
            return START_NOT_STICKY;
        }
        float frac = intent != null ? intent.getFloatExtra("width", 0.3f) : 0.3f;
        WindowManager wm = getSystemService(WindowManager.class);
        int screenW = wm.getCurrentWindowMetrics().getBounds().width();
        if (view == null) {
            view = new WebView(this);
            view.setBackgroundColor(Color.TRANSPARENT);
            WebSettings s = view.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            view.setWebViewClient(new WebViewClient());
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    (int) (screenW * frac), WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            wm.addView(view, lp);
            view.loadUrl(Config.SIDEBAR, java.util.Collections.singletonMap("X-Hub-Token", token));
            Log.i(BootReceiver.TAG, "sidebar: shown");
        } else {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) view.getLayoutParams();
            lp.width = (int) (screenW * frac);
            wm.updateViewLayout(view, lp);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (view != null) {
            getSystemService(WindowManager.class).removeView(view);
            view.destroy();
            view = null;
            Log.i(BootReceiver.TAG, "sidebar: hidden");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
