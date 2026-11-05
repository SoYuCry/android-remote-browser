package io.github.soyucy.androidremote.companion;

import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.json.JSONObject;

/**
 * Bounded, device-local diagnostics; never logs credentials or app content.
 * Device-protected files/task-events.jsonl rotates at ~512 KiB into one previous file.
 * Timestamps are UTC; elapsedMs and pid help identify clock changes and process restarts.
 * A killed process may not emit a destroy event. Heartbeats are emitted every five minutes.
 * alarm_received/schedule_due distinguish missing delivery from late processing;
 * screen_confirmed/screen_timeout distinguish actual screen-on from foreground switches;
 * action_state/action_failure include final outcomes and the failure foreground package.
 * A package name alone does not identify a particular system dialog.
 * Export a debug parallel installation through authorized ADB:
 * adb shell run-as io.github.soyucy.androidremote.companion.quick cat
 * /data/user_de/0/io.github.soyucy.androidremote.companion.quick/files/task-events.jsonl
 */
final class TaskLog {
    static synchronized void write(Context context, String event, String detail) {
        try {
            JSONObject entry = new JSONObject().put("time", Instant.now().toString())
                    .put("elapsedMs", SystemClock.elapsedRealtime()).put("pid", Process.myPid())
                    .put("event", event).put("detail", detail);
            File dir = context.createDeviceProtectedStorageContext().getFilesDir();
            File file = new File(dir, "task-events.jsonl");
            if (file.length() > 512 * 1024) {
                File previous = new File(dir, "task-events.previous.jsonl");
                if (previous.exists() && !previous.delete()) return;
                if (!file.renameTo(previous)) return;
            }
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write((entry + "\n").getBytes(StandardCharsets.UTF_8));
            }
            Log.i("RemoteTask", entry.toString());
        } catch (Exception e) { Log.w("RemoteTask", "Cannot persist diagnostics", e); }
    }
}
