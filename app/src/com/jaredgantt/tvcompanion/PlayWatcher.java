package com.jaredgantt.tvcompanion;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** **The play log** (docs/WATCHING.md § The play log): everything that plays on the TV, whoever started
 *  it, posted to the hub's /api/tv/play, which writes data-layer's tv_plays. Push only: the system calls
 *  back on every session added or removed and on every title and playback-state change, so nothing here
 *  polls.
 *
 *  A play is one title in one app. It is sent as `start` only once the same title has held for
 *  SETTLE_MS, so an ad or a title flashing past while something loads is not logged as something he
 *  watched. Pauses send `update` with how far in it is; a new title, the session going away, or the
 *  screen going off sends `end`. `watched_s` is the furthest position seen, extrapolated from the last
 *  state report the way TvLink.now does.
 *
 *  Needs the notification-listener grant, the same one TvLink.now uses. Posts go out one at a time on
 *  their own thread, in order, each retried for about a quarter of a minute. */
public class PlayWatcher implements MediaSessionManager.OnActiveSessionsChangedListener {
    static final String TAG = BootReceiver.TAG;
    static final long SETTLE_MS = 8000;

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService out = Executors.newSingleThreadExecutor();
    private final List<MediaController> watched = new ArrayList<>();
    private final List<PlayCallback> callbacks = new ArrayList<>();

    // The play being logged (sent as start), and the candidate waiting out SETTLE_MS.
    private String playId, playPkg, playTitle;
    private long playWatchedS;
    private String pendPkg, pendTitle;
    private final Runnable confirm = this::confirm;

    // The current session's latest state, kept so `end` can say how far in it got.
    private PlaybackState lastState;
    private long lastDurationS;
    private String lastArtist;

    PlayWatcher(Context ctx) {
        this.ctx = ctx;
    }

    void start() {
        MediaSessionManager m = ctx.getSystemService(MediaSessionManager.class);
        ComponentName listener = new ComponentName(ctx, Listener.class);
        try {
            m.addOnActiveSessionsChangedListener(this, listener, main);
            onActiveSessionsChanged(m.getActiveSessions(listener));
            Log.i(TAG, "plays: watching");
        } catch (SecurityException e) {
            Log.e(TAG, "plays: no notification-listener grant, nothing is logged: " + e);
        }
    }

    @Override
    public void onActiveSessionsChanged(List<MediaController> list) {
        for (int i = 0; i < watched.size(); i++) watched.get(i).unregisterCallback(callbacks.get(i));
        watched.clear();
        callbacks.clear();
        if (list != null) for (MediaController mc : list) {
            PlayCallback cb = new PlayCallback(this);
            mc.registerCallback(cb, main);
            watched.add(mc);
            callbacks.add(cb);
        }
        evaluate();
    }

    /** Re-reads what is playing; called on every callback. */
    void evaluate() {
        MediaController mc = current();
        if (mc == null) { endPlay(); cancelPending(); return; }
        PlaybackState st = mc.getPlaybackState();
        MediaMetadata md = mc.getMetadata();
        String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String pkg = mc.getPackageName();
        if (title == null || title.isEmpty()) return;
        boolean same = pkg.equals(playPkg) && title.equals(playTitle);
        if (same) {
            noteState(st, md);
            if (st != null && st.getState() == PlaybackState.STATE_PAUSED) send("update");
            return;
        }
        if (pkg.equals(pendPkg) && title.equals(pendTitle)) { noteState(st, md); return; }
        // A different title: the one on before has ended, and this one is a candidate until it settles.
        endPlay();
        pendPkg = pkg;
        pendTitle = title;
        noteState(st, md);
        main.removeCallbacks(confirm);
        main.postDelayed(confirm, SETTLE_MS);
    }

    private void confirm() {
        MediaController mc = current();
        MediaMetadata md = mc == null ? null : mc.getMetadata();
        String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (mc == null || !mc.getPackageName().equals(pendPkg) || pendTitle == null || !pendTitle.equals(title)) { cancelPending(); return; }
        playId = UUID.randomUUID().toString();
        playPkg = pendPkg;
        playTitle = pendTitle;
        playWatchedS = 0;
        cancelPending();
        noteState(mc.getPlaybackState(), md);
        send("start");
    }

    private void cancelPending() {
        main.removeCallbacks(confirm);
        pendPkg = null;
        pendTitle = null;
    }

    /** The screen went off: whatever was on has ended, even if the app keeps its session. */
    void screenOff() {
        endPlay();
        cancelPending();
    }

    private void endPlay() {
        if (playId == null) return;
        send("end");
        playId = null;
        playPkg = null;
        playTitle = null;
    }

    private void noteState(PlaybackState st, MediaMetadata md) {
        if (st != null) lastState = st;
        if (md != null) {
            long len = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            lastDurationS = len > 0 ? len / 1000 : 0;
            lastArtist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        }
        if (playId != null && lastState != null) playWatchedS = Math.max(playWatchedS, positionS(lastState));
    }

    private static long positionS(PlaybackState st) {
        long p = st.getPosition();
        if (st.getState() == PlaybackState.STATE_PLAYING)
            p += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        return Math.max(0, p) / 1000;
    }

    /** The session to log: the first one playing, else the first paused (TvLink.current). */
    private MediaController current() {
        MediaController paused = null;
        for (MediaController mc : watched) {
            PlaybackState st = mc.getPlaybackState();
            if (st == null) continue;
            if (st.getState() == PlaybackState.STATE_PLAYING) return mc;
            if (st.getState() == PlaybackState.STATE_PAUSED && paused == null) paused = mc;
        }
        return paused;
    }

    private void send(String event) {
        if (playId == null) return;
        if (lastState != null) playWatchedS = Math.max(playWatchedS, positionS(lastState));
        final JSONObject body = new JSONObject();
        try {
            body.put("id", playId).put("event", event).put("package", playPkg).put("title", playTitle)
                .put("watched_s", playWatchedS);
            if (lastArtist != null) body.put("artist", lastArtist);
            if (lastDurationS > 0) body.put("duration_s", lastDurationS);
        } catch (Exception e) { Log.e(TAG, "plays: " + e); return; }
        out.execute(() -> post(body));
    }

    private void post(JSONObject body) {
        String token = Config.token(ctx);
        if (token == null) { Log.e(TAG, "plays: no hub token, " + body.optString("event") + " not logged"); return; }
        for (long wait = 1000; ; wait *= 2) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(Config.HUB + "/api/tv/play").openConnection();
                c.setRequestProperty("X-Hub-Token", token);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(15_000);
                c.setReadTimeout(15_000);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream o = c.getOutputStream()) { o.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                int code = c.getResponseCode();
                if (code == 200) return;
                Log.w(TAG, "plays: hub answered " + code);
                if (code == 400) { Log.e(TAG, "plays: refused " + body); return; }
            } catch (Exception e) {
                Log.w(TAG, "plays: post failed " + e);
            } finally {
                if (c != null) c.disconnect();
            }
            if (wait > 8000) { Log.e(TAG, "plays: " + body.optString("event") + " of " + body.optString("title") + " never landed"); return; }
            try { Thread.sleep(wait); } catch (InterruptedException ignored) { return; }
        }
    }
}
