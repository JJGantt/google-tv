package com.jaredgantt.tvcompanion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;

/** Replaces the picks list (Picks) with one pushed over adb, base64 so no shell quoting touches it:
 *    am broadcast -n com.jaredgantt.tvcompanion/.PicksReceiver --es json_b64 <base64 JSON array>
 *  `bin/tv picks <file>` does this. Anything that does not parse as a JSON array is refused, leaving
 *  the old list in place. */
public class PicksReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            byte[] json = Base64.decode(intent.getStringExtra("json_b64"), Base64.DEFAULT);
            int n = new JSONArray(new String(json, "UTF-8")).length();
            File tmp = new File(context.getFilesDir(), Picks.FILE + ".tmp");
            try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(json); }
            if (!tmp.renameTo(new File(context.getFilesDir(), Picks.FILE))) throw new Exception("rename failed");
            setResultData("stored " + n);
            Log.i(BootReceiver.TAG, "picks: stored " + n);
        } catch (Exception e) {
            setResultData("refused: " + e.getMessage());
            Log.e(BootReceiver.TAG, "picks: refused: " + e);
        }
    }
}
