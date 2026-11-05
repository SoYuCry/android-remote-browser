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

/** Bounded, device-local diagnostics; never logs credentials or app content. */
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
