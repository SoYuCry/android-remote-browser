package io.github.soyucy.androidremote.companion;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.UUID;

/**
 * Explicitly armed debug-only test, separate from the production calendar.
 * Starts after five minutes, then runs ten slots at two-minute intervals so the default
 * one-minute screen timeout can expire between actions. Claims persist before dispatch;
 * missed slots are logged rather than replayed, and the alarm is canceled after ten claims.
 * Launch a fresh activity instance (this actually opens Feishu):
 * adb shell am start -f 0x10008000
 * -n io.github.soyucy.androidremote.companion.quick/io.github.soyucy.androidremote.companion.MainActivity
 * --ez diagnostic_test true
 * This short test with USB power does not establish overnight/deep-idle reliability.
 */
final class DiagnosticSchedule {
    static final String ACTION = "diagnostic_alarm";
    static final long INTERVAL = 120_000;
    private static long registered;
    private static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext().getSharedPreferences("diagnostic_schedule", 0);
    }
    static synchronized void arm(Context c) {
        if (!BuildConfig.DEBUG) return;
        SharedPreferences p = prefs(c);
        if (p.getInt("index", 10) < 10) return;
        long start = System.currentTimeMillis() + 300_000;
        String run = UUID.randomUUID().toString();
        if (!p.edit().putLong("start", start).putInt("index", 0).putString("run", run).commit()) return;
        registered = 0;
        TaskLog.write(c, "test_armed", "run=" + run + " start=" + start + " intervalMs=" + INTERVAL + " count=10");
        reconcile(c);
    }
    static synchronized void reconcile(Context c) {
        if (!BuildConfig.DEBUG) return;
        SharedPreferences p = prefs(c);
        int index = p.getInt("index", 10);
        AlarmManager alarms = c.getSystemService(AlarmManager.class);
        PendingIntent alarm = PendingIntent.getBroadcast(c, 7350,
                new Intent(c, ScheduleReceiver.class).setAction(ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long now = System.currentTimeMillis();
        while (index < 10) {
            long due = p.getLong("start", 0) + index * INTERVAL;
            String id = "diagnostic-" + p.getString("run", "") + "-" + (index + 1);
            if (now < due) {
                if (registered != due) {
                    try {
                        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, alarm);
                        registered = due;
                        TaskLog.write(c, "test_scheduled", id + " due=" + due);
                    } catch (SecurityException e) { TaskLog.write(c, "test_schedule_error", e.toString()); }
                }
                return;
            }
            // Persist claim first: no replay following process death.
            if (!p.edit().putInt("index", ++index).commit()) return;
            registered = 0;
            TaskLog.write(c, "test_due", id + " lateMs=" + (now - due)
                    + " accessibility=" + (QuickActionService.current() != null));
            if (now - due >= INTERVAL) {
                TaskLog.write(c, "test_missed", id);
            } else {
                int result = QuickActionController.get(c).start(id);
                TaskLog.write(c, "test_dispatch", id + " status=" + result);
            }
        }
        alarms.cancel(alarm);
        registered = 0;
    }
}
