package com.jaredgantt.tvcompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** **The TV's end of voice control** (docs/VOICE.md). Holds the hub's /api/tv/stream open; each `cmd`
 *  frame is a tv_commands row, done here on the TV and answered on /api/tv/result. Push only: the
 *  stream is the only way commands arrive, and a dropped stream reconnects with backoff (1s doubling
 *  to a minute), so a hub that is down costs one attempt a minute, not a busy loop.
 *
 *  In standby the stream drops and the backoff climbs. The screen coming on cuts the wait short, so a
 *  command held by the hub while the TV was woken (docs/POWER.md) arrives in seconds, not a minute.
 *
 *  Started at boot (BootReceiver) and by `bin/tv link`; START_STICKY so the system restarts it. */
public class TvLink extends Service {
    static final String TAG = BootReceiver.TAG;
    private Thread loop;
    private volatile boolean running;
    private volatile long backoff = 1000;
    // A top-level class: this d8 (build-tools 34) fails on any nested class javac 21 emits.
    private final ScreenOn screenOn = new ScreenOn(this::retryNow);

    private void retryNow() {
        backoff = 1000;
        if (loop != null) loop.interrupt();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("link", "Link", NotificationManager.IMPORTANCE_MIN));
        startForeground(3, new Notification.Builder(this, "link").setSmallIcon(android.R.drawable.ic_dialog_info).build());
        if (loop == null) {
            running = true;
            registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
            loop = new Thread(this::listen, "tv-link");
            loop.start();
        }
        return START_STICKY;
    }

    private void listen() {
        while (running) {
            String token = Config.prefs(this).getString("hub_token", null);
            if (token == null) { Log.e(TAG, "link: no hub token"); sleep(60_000); continue; }
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(Config.HUB + "/api/tv/stream?token=" + token).openConnection();
                c.setRequestProperty("Accept", "text/event-stream");
                c.setConnectTimeout(15_000);
                // The hub pings every 25s; a minute of nothing is a dead socket.
                c.setReadTimeout(60_000);
                if (c.getResponseCode() != 200) throw new Exception("stream " + c.getResponseCode());
                Log.i(TAG, "link: connected");
                backoff = 1000;
                BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                String event = null, line;
                while (running && (line = in.readLine()) != null) {
                    if (line.startsWith("event:")) event = line.substring(6).trim();
                    else if (line.startsWith("data:") && "cmd".equals(event)) handle(line.substring(5).trim(), token);
                    else if (line.isEmpty()) event = null;
                }
            } catch (Exception e) {
                Log.w(TAG, "link: " + e);
            } finally {
                if (c != null) c.disconnect();
            }
            sleep(backoff);
            backoff = Math.min(backoff * 2, 60_000);
        }
    }

    private void handle(String data, String token) {
        String id = null;
        JSONObject result = new JSONObject();
        try {
            JSONObject cmd = new JSONObject(data);
            id = cmd.getString("id");
            JSONObject args = cmd.optJSONObject("args");
            if (args == null) args = new JSONObject();
            Log.i(TAG, "link: " + cmd.getString("op") + " " + args);
            result = run(cmd.getString("op"), args);
        } catch (Exception e) {
            try { result.put("ok", false).put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) {}
        }
        if (id != null) answer(id, result, token);
    }

    /** One command. Every setter answers with the value the TV reports afterwards, not the one asked for. */
    private JSONObject run(String op, JSONObject a) throws Exception {
        JSONObject r = new JSONObject().put("ok", true);
        switch (op) {
            case "brightness":
                if (a.has("value")) Display.setBacklight(a.getInt("value"));
                return r.put("brightness", Display.backlight());
            case "contrast":
                if (a.has("value")) Display.setContrast(a.getInt("value"));
                return r.put("contrast", Display.contrast());
            case "night":
                Display.setBacklight(0);
                Display.setContrast(60);
                Display.setDynamicContrast(0);
                return r.put("brightness", Display.backlight()).put("contrast", Display.contrast())
                        .put("dynamic_contrast", Display.dynamicContrast());
            case "volume": return r.put("volume", volume(a));
            case "open": {
                // An `intent:` URI is a complete Android TV deep link (the catalog's, from JustWatch),
                // carrying its own package and flags; anything else is a URL to view.
                String url = a.getString("url");
                Intent i = (url.startsWith("intent:") ? Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        : new Intent(Intent.ACTION_VIEW, Uri.parse(url))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (a.has("package")) i.setPackage(a.getString("package"));
                startActivity(i);
                return r;
            }
            case "launch": {
                Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(a.getString("package"));
                if (i == null) return r.put("ok", false).put("error", "not installed: " + a.getString("package"));
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return r;
            }
            case "sidebar": {
                Intent i = new Intent(this, SidebarService.class).setAction(a.optBoolean("on", true) ? "show" : "hide");
                if (a.has("width")) i.putExtra("width", (float) a.getDouble("width"));
                startForegroundService(i);
                return r;
            }
            case "overlay":
                startForegroundService(new Intent(this, OverlayService.class)
                        .putExtra("text", a.getString("text")).putExtra("seconds", a.optInt("seconds", 5)));
                return r;
            case "now": return now(r);
            default: return r.put("ok", false).put("error", "unknown op " + op);
        }
    }

    /** TCL's audio ignores a direct set (`cmd media_session volume --set`), so a target is reached a
     *  step at a time, reading back after each, as `bin/tv volume` does. */
    private int volume(JSONObject a) throws Exception {
        AudioManager am = getSystemService(AudioManager.class);
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (a.has("value")) {
            int target = Math.max(0, Math.min(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), a.getInt("value")));
            while (cur != target) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, target > cur ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
                int next = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (next == cur) throw new Exception("volume stuck at " + cur);
                cur = next;
            }
        }
        return cur;
    }

    /** What is playing, from the active media sessions; needs the notification-listener grant. */
    private JSONObject now(JSONObject r) throws Exception {
        MediaSessionManager m = getSystemService(MediaSessionManager.class);
        List<MediaController> list = m.getActiveSessions(new ComponentName(this, Listener.class));
        for (MediaController mc : list) {
            PlaybackState st = mc.getPlaybackState();
            if (st == null || (st.getState() != PlaybackState.STATE_PLAYING && st.getState() != PlaybackState.STATE_PAUSED)) continue;
            MediaMetadata md = mc.getMetadata();
            r.put("package", mc.getPackageName());
            r.put("state", st.getState() == PlaybackState.STATE_PLAYING ? "playing" : "paused");
            r.put("position_s", st.getPosition() / 1000);
            if (md != null) {
                r.put("title", md.getString(MediaMetadata.METADATA_KEY_TITLE));
                r.put("artist", md.getString(MediaMetadata.METADATA_KEY_ARTIST));
            }
            return r;
        }
        return r.put("playing", false);
    }

    private void answer(String id, JSONObject result, String token) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(Config.HUB + "/api/tv/result?token=" + token).openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(15_000);
            c.setReadTimeout(15_000);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = c.getOutputStream()) {
                o.write(new JSONObject().put("id", id).put("result", result).toString().getBytes(StandardCharsets.UTF_8));
            }
            c.getResponseCode();
        } catch (Exception e) {
            Log.w(TAG, "link: answer failed " + e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    @Override
    public void onDestroy() {
        running = false;
        if (loop != null) unregisterReceiver(screenOn);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
