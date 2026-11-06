package com.jaredgantt.tvcompanion;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** The picks screen (docs/PICKS.md): titled shelves of thumbnails, one row each, read from the hub's
 *  /api/tv/picks whenever the screen comes up and again on TvLink's `picks_refresh`. Each card opens
 *  its item: an `intent:` deep link as is, any other URL viewed in the item's package. The corner badge
 *  is that package's own app icon, so the screen needs no artwork of its own. */
public class Picks extends Activity {
    /** The screen while it is in front, for TvLink's refresh. */
    static volatile Picks shown;
    private static final int CARD_W = 400;
    private LinearLayout shelves;
    private final Map<String, Drawable> icons = new HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101010);
        shelves = new LinearLayout(this);
        shelves.setOrientation(LinearLayout.VERTICAL);
        shelves.setPadding(0, 32, 0, 48);
        scroll.addView(shelves);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        shown = this;
        reload();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (shown == this) shown = null;
    }

    void reload() {
        new Thread(() -> {
            try {
                JSONArray list = new JSONObject(get(Config.HUB + "/api/tv/picks")).getJSONArray("shelves");
                runOnUiThread(() -> draw(list));
            } catch (Exception e) {
                Log.e(BootReceiver.TAG, "picks: load: " + e);
            }
        }, "picks-load").start();
    }

    private String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestProperty("X-Hub-Token", String.valueOf(Config.token(this)));
            c.setConnectTimeout(10_000);
            c.setReadTimeout(15_000);
            if (c.getResponseCode() != 200) throw new Exception(url + " " + c.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }

    /** Redraws every shelf, keeping focus on the card that had it when it is still there. */
    private void draw(JSONArray list) {
        View f = getCurrentFocus();
        Object focusedId = f != null ? f.getTag() : null;
        shelves.removeAllViews();
        View refocus = null, first = null;
        for (int s = 0; s < list.length(); s++) {
            JSONObject shelf = list.optJSONObject(s);
            JSONArray items = shelf.optJSONArray("items");
            if (items == null || items.length() == 0) continue;
            TextView title = new TextView(this);
            title.setText(shelf.optString("title"));
            title.setTextColor(Color.WHITE);
            title.setTextSize(22);
            title.setPadding(64, 24, 64, 8);
            shelves.addView(title);
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipToPadding(false);
            LinearLayout row = new LinearLayout(this);
            row.setPadding(48, 8, 48, 8);
            hs.addView(row);
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null) continue;
                View card = card(it);
                row.addView(card);
                if (first == null) first = card;
                if (it.optString("id").equals(focusedId)) refocus = card;
            }
            shelves.addView(hs);
        }
        if (refocus == null) refocus = first;
        if (refocus != null) refocus.requestFocus();
    }

    private View card(JSONObject it) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(8, 8, 8, 12);
        card.setFocusable(true);
        card.setTag(it.optString("id"));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12);
        bg.setColor(Color.TRANSPARENT);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(CARD_W + 16, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(8, 8, 8, 8);
        card.setLayoutParams(lp);

        FrameLayout thumb = new FrameLayout(this);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(0xFF2A2A2A);
        thumb.addView(img, new FrameLayout.LayoutParams(CARD_W, CARD_W * 9 / 16));
        Drawable icon = icon(it.optString("package"));
        if (icon != null) {
            ImageView badge = new ImageView(this);
            badge.setImageDrawable(icon);
            badge.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(56, 56, Gravity.TOP | Gravity.END);
            bp.setMargins(0, 10, 10, 0);
            thumb.addView(badge, bp);
        }
        card.addView(thumb);

        TextView title = new TextView(this);
        title.setText(it.optString("title"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(4, 12, 4, 0);
        card.addView(title);

        card.setOnFocusChangeListener((v, focused) -> {
            bg.setColor(focused ? 0xFF3A3A3A : Color.TRANSPARENT);
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(120).start();
        });
        card.setOnClickListener(v -> open(it));

        String image = it.optString("image");
        if (!image.isEmpty() && !"null".equals(image)) new Thread(() -> {
            Bitmap bm = fetch(image);
            if (bm != null) runOnUiThread(() -> img.setImageBitmap(bm));
        }, "picks-image").start();
        return card;
    }

    /** The app's own launcher icon; null when the package is unknown or not installed. */
    private Drawable icon(String pkg) {
        if (pkg.isEmpty() || "null".equals(pkg)) return null;
        if (!icons.containsKey(pkg)) {
            Drawable d = null;
            try { d = getPackageManager().getApplicationIcon(pkg); } catch (Exception e) {
                Log.e(BootReceiver.TAG, "picks: no icon for " + pkg + ": " + e);
            }
            icons.put(pkg, d);
        }
        return icons.get(pkg);
    }

    private static Bitmap fetch(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10_000);
            c.setReadTimeout(15_000);
            try (InputStream in = c.getInputStream()) { return BitmapFactory.decodeStream(in); }
        } catch (Exception e) {
            Log.e(BootReceiver.TAG, "picks: image " + url + ": " + e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void open(JSONObject it) {
        try {
            String url = it.getString("url");
            Intent i = url.startsWith("intent:") ? Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            String pkg = it.optString("package");
            if (i.getPackage() == null && i.getComponent() == null && !pkg.isEmpty() && !"null".equals(pkg)) i.setPackage(pkg);
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.e(BootReceiver.TAG, "picks: open " + it + ": " + e);
        }
    }
}
