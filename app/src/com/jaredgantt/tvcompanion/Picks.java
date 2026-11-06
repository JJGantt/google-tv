package com.jaredgantt.tvcompanion;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

/** The picks screen: a grid of thumbnails and titles chosen by a session (`bin/tv picks <file>`),
 *  each opening its video or show in the app it belongs to. The list is `picks.json` in the app's
 *  files dir, written by PicksReceiver: a JSON array of {title, image, url, package?}. `url` is either
 *  an `intent:` deep link (the catalog's) or a URL viewed in `package` (a YouTube watch URL in the
 *  YouTube app). Read fresh every time the screen comes up, so a new list shows on the next open. */
public class Picks extends Activity {
    static final String FILE = "picks.json";
    private static final int COLUMNS = 4;
    private GridLayout grid;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101010);
        grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        grid.setPadding(48, 48, 48, 48);
        scroll.addView(grid);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        grid.removeAllViews();
        JSONArray items;
        try {
            items = new JSONArray(new String(Files.readAllBytes(new File(getFilesDir(), FILE).toPath()), "UTF-8"));
        } catch (Exception e) {
            Log.e(BootReceiver.TAG, "picks: no list: " + e);
            return;
        }
        int cardW = (getResources().getDisplayMetrics().widthPixels - 96) / COLUMNS - 32;
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it != null) grid.addView(card(it, cardW));
        }
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    private View card(JSONObject it, int w) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(8, 8, 8, 16);
        card.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12);
        bg.setColor(Color.TRANSPARENT);
        card.setBackground(bg);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = w + 16;
        lp.setMargins(8, 8, 8, 24);
        card.setLayoutParams(lp);

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(0xFF2A2A2A);
        img.setClipToOutline(true);
        card.addView(img, new LinearLayout.LayoutParams(w, w * 9 / 16));

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
        if (!image.isEmpty()) new Thread(() -> {
            Bitmap bm = fetch(image);
            if (bm != null) runOnUiThread(() -> img.setImageBitmap(bm));
        }, "picks-image").start();
        return card;
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
            if (it.has("package")) i.setPackage(it.getString("package"));
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.e(BootReceiver.TAG, "picks: open " + it + ": " + e);
        }
    }
}
