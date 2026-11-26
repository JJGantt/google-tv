package com.jaredgantt.tvcompanion;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;

/** One media session's changes, handed to the PlayWatcher (a top-level class: this d8 fails on any
 *  nested class javac emits, build.sh). */
public class PlayCallback extends MediaController.Callback {
    private final PlayWatcher w;

    PlayCallback(PlayWatcher w) {
        this.w = w;
    }

    @Override public void onMetadataChanged(MediaMetadata m) { w.evaluate(); }
    @Override public void onPlaybackStateChanged(PlaybackState s) { w.evaluate(); }
    @Override public void onSessionDestroyed() { w.evaluate(); }
}
