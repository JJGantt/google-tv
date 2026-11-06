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
import android.os.PowerManager;
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
                c = (HttpURLConnection) new URL(Config.HUB + "/api/tv/stream").openConnection();
                c.setRequestProperty("X-Hub-Token", token);
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
                holdAwake();
                startActivity(i);
                return r;
            }
            case "launch": {
                Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(a.getString("package"));
                if (i == null) return r.put("ok", false).put("error", "not installed: " + a.getString("package"));
                holdAwake();
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
            case "media": return media(a.getString("action"), a);
            case "input": {
                // HDMI n is TCL's passthrough input HW(14+n), opened the way the TV's own input picker
                // opens it (bin/tv input).
                int n = a.getInt("hdmi");
                if (n < 1 || n > 4) return r.put("ok", false).put("error", "hdmi is 1-4");
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("content://android.media.tv/passthrough/"
                        + Uri.encode("com.tcl.tvinput/.TvPassThroughService/HW" + (14 + n))))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return r.put("hdmi", n);
            }
            case "guide_refresh": {
                // The shelves changed (data-layer tvGuide.ts). Only a screen already in front redraws;
                // this never brings it up.
                Guide p = Guide.shown;
                if (p != null) p.reload();
                return r.put("redrawn", p != null);
            }
            default: return r.put("ok", false).put("error", "unknown op " + op);
        }
    }

    /** Starting an app is not user activity. On an HDMI input the TV holds the screen on itself, so the
     *  screensaver's idle timer can have run out hours ago, and the instant the app replaces the input
     *  the screensaver starts: two seconds after a video's link arrives, which pauses YouTube before it
     *  plays and leaves its home screen under the screensaver. This lock bridges until the app's player
     *  holds the screen itself, and ON_AFTER_RELEASE restarts the idle timer when it lets go. */
    private void holdAwake() {
        PowerManager.WakeLock wl = getSystemService(PowerManager.class).newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "tvcompanion:open");
        wl.setReferenceCounted(false);
        wl.acquire(30_000);
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

    /** The session `now` reports and `media` drives: the first one playing or paused. */
    private MediaController current() {
        List<MediaController> list = getSystemService(MediaSessionManager.class)
                .getActiveSessions(new ComponentName(this, Listener.class));
        for (MediaController mc : list) {
            PlaybackState st = mc.getPlaybackState();
            if (st != null && (st.getState() == PlaybackState.STATE_PLAYING || st.getState() == PlaybackState.STATE_PAUSED)) return mc;
        }
        return null;
    }

    /** Where playback is now, not at the session's last report: a playing session only updates its
     *  position on a state change, so the elapsed time since then is added. */
    private static long positionMs(PlaybackState st) {
        long p = st.getPosition();
        if (st.getState() == PlaybackState.STATE_PLAYING)
            p += (long) ((android.os.SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        return Math.max(0, p);
    }

    /** Transport controls on whatever is playing, through its media session (the same grant `now`
     *  uses). Skips are a seek from the live position, which every app that reports a position takes,
     *  where fastForward/rewind mean something different in each app. Answers with `now` afterwards. */
    private JSONObject media(String action, JSONObject a) throws Exception {
        MediaController mc = current();
        JSONObject r = new JSONObject().put("ok", true);
        if (mc == null) return r.put("ok", false).put("error", "nothing is playing");
        MediaController.TransportControls t = mc.getTransportControls();
        PlaybackState st = mc.getPlaybackState();
        long pos = positionMs(st);
        switch (action) {
            case "play": t.play(); break;
            case "pause": t.pause(); break;
            case "toggle": if (st.getState() == PlaybackState.STATE_PLAYING) t.pause(); else t.play(); break;
            case "stop": t.stop(); break;
            case "next": t.skipToNext(); break;
            case "previous": t.skipToPrevious(); break;
            case "forward": t.seekTo(pos + (long) (a.optDouble("seconds", 30) * 1000)); break;
            case "back": t.seekTo(Math.max(0, pos - (long) (a.optDouble("seconds", 10) * 1000))); break;
            case "seek": t.seekTo((long) (a.getDouble("seconds") * 1000)); break;
            default: return r.put("ok", false).put("error", "unknown action " + action);
        }
        // The app applies it asynchronously; read back once it has had a moment to.
        Thread.sleep(800);
        return now(r);
    }

    /** What is playing, from the active media sessions; needs the notification-listener grant. */
    private JSONObject now(JSONObject r) throws Exception {
        MediaController mc = current();
        if (mc != null) {
            PlaybackState st = mc.getPlaybackState();
            MediaMetadata md = mc.getMetadata();
            r.put("package", mc.getPackageName());
            r.put("state", st.getState() == PlaybackState.STATE_PLAYING ? "playing" : "paused");
            r.put("position_s", positionMs(st) / 1000);
            if (md != null) {
                r.put("title", md.getString(MediaMetadata.METADATA_KEY_TITLE));
                r.put("artist", md.getString(MediaMetadata.METADATA_KEY_ARTIST));
                long len = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
                if (len > 0) r.put("duration_s", len / 1000);
            }
            return r;
        }
        return r.put("playing", false);
    }

    private void answer(String id, JSONObject result, String token) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(Config.HUB + "/api/tv/result").openConnection();
            c.setRequestProperty("X-Hub-Token", token);
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
