package io.github.soyucy.androidremote.companion;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** One persisted random appointment and at most one automatic attempt per weekday window. */
final class TaskScheduler {
    private static TaskScheduler instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final AlarmManager alarms;
    private String error = "";
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ScheduleWindow.ZONE);
    static synchronized TaskScheduler get(Context c) {
        if (instance == null) instance = new TaskScheduler(c.getApplicationContext());
        return instance;
    }
    private TaskScheduler(Context c) {
        context = c;
        prefs = c.createDeviceProtectedStorageContext().getSharedPreferences("weekday_schedule", 0);
        alarms = c.getSystemService(AlarmManager.class);
    }
    synchronized boolean enabled() { return prefs.getBoolean("enabled", false); }
    boolean permitted() { return Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms(); }
    synchronized void setEnabled(boolean value) {
        if (!prefs.edit().putBoolean("enabled", value).commit()) { error = "保存设置失败"; return; }
        reconcile(false);
    }
    private PendingIntent alarm() {
        return PendingIntent.getBroadcast(context, 1745, new Intent(context, ScheduleReceiver.class).setAction("weekday_alarm"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    synchronized void reconcile(boolean allowRun) {
        error = "";
        if (!enabled()) { alarms.cancel(alarm()); return; }
        if (!permitted()) { error = "请开启定时执行权限"; return; }
        long now = System.currentTimeMillis();
        long due = prefs.getLong("due", 0);
        String handled = ScheduleWindow.normalize(prefs.getString("handled", ""));
        if (due != 0 && (!ScheduleWindow.inWindow(due) || ScheduleWindow.key(due).compareTo(handled) <= 0)) due = 0;
        // Upgrade once: retain an existing afternoon appointment unless a morning window comes first.
        if (!prefs.getBoolean("two_windows", false)) {
            long candidate = ScheduleWindow.next(now, handled, new SecureRandom());
            if (due == 0 || (candidate < due && !ScheduleWindow.key(candidate).equals(ScheduleWindow.key(due)))) due = candidate;
            if (!prefs.edit().putBoolean("two_windows", true).putString("handled", handled)
                    .putLong("due", due).commit()) { error = "保存任务状态失败"; return; }
        }
        if (due != 0 && now >= due) {
            String day = ScheduleWindow.key(due);
            if (!ScheduleWindow.sameWindow(due, now)) {
                if (!prefs.edit().putString("handled", day).putLong("due", 0)
                        .putString("last", label(day) + " 已错过窗口，未补执行").commit()) { error = "保存任务状态失败"; return; }
                handled = day;
                due = 0;
            } else if (allowRun) {
                // A cold process may receive the alarm before accessibility has rebound.
                // Wait only inside today's window; no action has been claimed or launched yet.
                if (QuickActionService.current() == null) {
                    error = "等待快捷操作无障碍服务；窗口结束后跳过";
                    try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                            Math.min(now + 60_000, ScheduleWindow.end(due)), alarm()); }
                    catch (SecurityException e) { error = "请开启定时执行权限"; }
                    return;
                }
                // Claim before launching: process death cannot replay a partially completed action.
                if (!prefs.edit().putString("handled", day).putLong("due", 0)
                        .putString("last", label(day) + " 已触发；若无后续结果，任务可能被系统中断").commit()) { error = "保存任务状态失败"; return; }
                int result = QuickActionController.get(context).start("schedule-" + day);
                if (result != 202) record("schedule-" + day, result == 409 ? "失败：已有操作正在执行" : "失败：无障碍服务不可用或飞书未安装");
                handled = day;
                due = 0;
            }
        }
        if (due == 0) {
            due = ScheduleWindow.next(now, handled, new SecureRandom());
            if (!prefs.edit().putLong("due", due).commit()) { error = "保存执行时间失败"; return; }
        }
        try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Math.max(due, now + 1000), alarm()); }
        catch (SecurityException e) { error = "请开启定时执行权限"; }
    }
    synchronized void record(String id, String result) {
        if (!id.startsWith("schedule-")) return;
        if (!ScheduleWindow.normalize(id.substring(9)).equals(ScheduleWindow.normalize(prefs.getString("handled", "")))) return;
        prefs.edit().putString("last", label(id.substring(9)) + " " + result).commit();
    }
    private static String label(String key) {
        String normalized = ScheduleWindow.normalize(key);
        return normalized.substring(0, 10) + (normalized.endsWith("/0") ? " 上午" : " 下午");
    }
    synchronized String description() {
        String next = prefs.getLong("due", 0) == 0 ? "待安排" : FORMAT.format(Instant.ofEpochMilli(prefs.getLong("due", 0)));
        return (enabled() ? "已开启" : "已关闭") + " · 北京时间周一至周五 08:35–08:50、17:50–18:05，每个时段随机一次\n"
                + (enabled() ? "下次：" + next + "\n" : "")
                + (!error.isEmpty() ? error + "\n" : enabled() && !permitted() ? "请开启定时执行权限\n" : "")
                + "最近结果：" + prefs.getString("last", "暂无") + "\n按星期执行，不排除法定节假日；错过窗口不补执行。";
    }
}
