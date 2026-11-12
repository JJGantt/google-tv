package com.jaredgantt.tvcompanion;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.util.Collections;

/** The hub's wall (/tv, the page the Pi's kiosk shows) full screen, as its own launcher entry.
 *
 *  The wall is laid out for a 1920x1080 CSS canvas. A WebView's CSS pixel is a density-independent
 *  pixel, and this TV runs 1920x1080 at density 2, so a full-screen WebView is only 960 CSS pixels
 *  wide and the wall's phone scaling (wall.js) would kick in. So the WebView is laid out at exactly
 *  1920x1080 CSS pixels and the view itself is scaled to fill the screen.
 *
 *  Signed in like the sidebar: the device token in X-Hub-Token on the first load, which the hub
 *  answers with its device cookie; every later request, and the wall's own moves between walls
 *  (wallmode.js), ride that cookie.
 *    am start -n com.jaredgantt.tvcompanion/.Hub          (bin/tv launch hub) */
public class Hub extends Activity {
    private static final int W = 1920, H = 1080;
    private static final long RETRY_MIN_MS = 5_000, RETRY_MAX_MS = 5 * 60_000;
    final Handler main = new Handler(Looper.getMainLooper());
    FrameLayout root;
    WebView view;
    long retryMs = RETRY_MIN_MS;
    final Runnable retry = this::load;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // A wall is for leaving up: no screensaver over it while it is in front.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> fit());
        setContentView(root);
        create();
    }

    void create() {
        float d = getResources().getDisplayMetrics().density;
        view = new WebView(this);
        view.setBackgroundColor(Color.BLACK);
        view.setFocusable(false);
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);   // the wall's alarm and timer tones
        view.setWebViewClient(new Client(this));
        root.addView(view, new FrameLayout.LayoutParams(Math.round(W * d), Math.round(H * d)));
        view.setPivotX(0);
        view.setPivotY(0);
        fit();
        load();
    }

    /** Static, holding its Hub, rather than anonymous or inner: the bare-SDK d8 build crashes on a
     *  class that captures its outer instance (build.sh). */
    static final class Client extends WebViewClient {
        private final Hub h;

        Client(Hub h) {
            this.h = h;
        }

        @Override
        public void onPageFinished(WebView v, String url) {
            h.retryMs = RETRY_MIN_MS;
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
            if (!req.isForMainFrame()) return;
            // A hub deploy drops it for about 13 s; back off from there so a long outage costs little.
            Log.e(BootReceiver.TAG, String.format("hub: load failed (%s), retrying in %ds", err.getDescription(), h.retryMs / 1000));
            h.main.removeCallbacks(h.retry);
            h.main.postDelayed(h.retry, h.retryMs);
            h.retryMs = Math.min(h.retryMs * 2, RETRY_MAX_MS);
        }

        @Override
        public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail detail) {
            // The renderer died (killed for memory, or crashed). The view is unusable now; replace it.
            Log.e(BootReceiver.TAG, String.format("hub: renderer gone (crash=%b), reloading", detail.didCrash()));
            h.root.removeView(h.view);
            h.view.destroy();
            h.create();
            return true;
        }
    }

    /** Scales the 1920x1080-CSS-pixel view to cover the screen, letterboxed if the aspect differs. */
    private void fit() {
        if (view == null || root.getWidth() == 0) return;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        float k = Math.min(root.getWidth() / (float) lp.width, root.getHeight() / (float) lp.height);
        view.setScaleX(k);
        view.setScaleY(k);
        view.setTranslationX((root.getWidth() - lp.width * k) / 2);
        view.setTranslationY((root.getHeight() - lp.height * k) / 2);
    }

    void load() {
        String token = Config.token(this);
        if (token == null) {
            Log.e(BootReceiver.TAG, "hub: no hub token configured");
            return;
        }
        view.loadUrl(Config.WALL, Collections.singletonMap("X-Hub-Token", token));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view != null) view.onResume();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (view != null) view.onPause();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(retry);
        if (view != null) view.destroy();
        super.onDestroy();
    }
}
