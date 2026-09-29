package io.github.soyucy.androidremote.companion;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

final class QuickActionController {
    static final String FEISHU = "com.ss.android.lark";
    private static QuickActionController instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> requests = new LinkedHashMap<>();
    private boolean running;
    private String state = "idle", message = "准备就绪", requestId = "";
    private PowerManager.WakeLock wakeLock;
    private WakeActivity wakeActivity;
    private long deadline;

    static synchronized QuickActionController get(Context context) {
        if (instance == null) instance = new QuickActionController(context.getApplicationContext());
        return instance;
    }
    private QuickActionController(Context context) {
        this.context = context;
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("quick_action", 0);
        requestId = prefs.getString("requestId", "");
        state = prefs.getString("state", "idle");
        message = prefs.getString("message", "准备就绪");
        if (!requestId.isEmpty()) requests.put(requestId, true);
        if (prefs.getBoolean("running", false)) {
            state = "failed";
            message = "上次操作因服务重启中断，未确认返回桌面";
            save();
        }
    }
    synchronized JSONObject status() {
        JSONObject value = new JSONObject();
        try {
            value.put("running", running).put("state", state).put("message", message)
                    .put("requestId", requestId).put("accessibility", QuickActionService.current() != null)
                    .put("installed", StatusChecker.isInstalled(context, FEISHU));
        } catch (Exception e) { throw new IllegalStateException(e); }
        return value;
    }
    synchronized int start(String id) {
        if (requests.containsKey(id)) return 200;
        if (running) return 409;
        if (QuickActionService.current() == null || !StatusChecker.isInstalled(context, FEISHU)) return 412;
        requestId = id;
        requests.put(id, true);
        if (requests.size() > 64) requests.remove(requests.keySet().iterator().next());
        running = true;
        update("waking", "正在唤醒手机");
        handler.post(this::begin);
        return 202;
    }
    private void begin() {
        try {
            QuickActionService service = QuickActionService.current();
            if (service == null) { fail("快捷操作权限已关闭"); return; }
            // The activity wakes the display. This timed lock only keeps it on during this task.
            PowerManager pm = context.getSystemService(PowerManager.class);
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "RemoteBrowser:QuickAction");
            wakeLock.acquire(25_000);
            handler.postDelayed(() -> fail("操作超时，未确认返回桌面"), 22_000);
            service.startActivity(new Intent(service, WakeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (Exception e) { fail("无法唤醒手机，请检查后台运行权限"); }
    }
    synchronized boolean isRunning() { return running; }
    void attach(WakeActivity activity) { wakeActivity = activity; }
    void launchFeishu() {
        if (!isRunning()) return;
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(FEISHU);
            if (intent == null || QuickActionService.current() == null) { fail("无法启动飞书"); return; }
            update("opening", "正在打开飞书");
            QuickActionService.current().startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            deadline = SystemClock.elapsedRealtime() + 8_000;
            handler.postDelayed(this::checkOpened, 250);
        } catch (Exception e) { fail("启动飞书被系统阻止"); }
    }
    private void checkOpened() {
        if (!isRunning()) return;
        QuickActionService service = QuickActionService.current();
        if (service == null) { fail("快捷操作权限已关闭"); return; }
        if (FEISHU.equals(service.foregroundPackage())) {
            update("waiting", "飞书已打开，5 秒后返回桌面");
            handler.postDelayed(this::goHome, 5_000);
        } else if (SystemClock.elapsedRealtime() >= deadline) fail("未确认飞书进入前台，请用完整远控检查");
        else handler.postDelayed(this::checkOpened, 250);
    }
    private void goHome() {
        if (!isRunning()) return;
        update("returning", "正在返回桌面");
        if (wakeActivity != null) { wakeActivity.finish(); wakeActivity = null; }
        QuickActionService service = QuickActionService.current();
        if (service == null || !service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) { fail("返回桌面失败"); return; }
        deadline = SystemClock.elapsedRealtime() + 4_000;
        handler.postDelayed(this::checkHome, 300);
    }
    private void checkHome() {
        if (!isRunning()) return;
        QuickActionService service = QuickActionService.current();
        ResolveInfo home = context.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (home != null && service != null && home.activityInfo.packageName.equals(service.foregroundPackage())) {
            finish("done", "飞书已打开并返回桌面，屏幕将按原设置自动熄灭");
        } else if (SystemClock.elapsedRealtime() >= deadline) fail("已发送返回指令，但未确认桌面出现");
        else handler.postDelayed(this::checkHome, 250);
    }
    void interrupt() { handler.post(() -> fail("快捷操作服务中断，未确认返回桌面")); }
    void fail(String reason) { if (isRunning()) finish("failed", reason); }
    private synchronized void update(String next, String text) { state = next; message = text; save(); }
    private synchronized void finish(String next, String text) {
        handler.removeCallbacksAndMessages(null);
        if (wakeActivity != null) { wakeActivity.finish(); wakeActivity = null; }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        running = false;
        update(next, text);
    }
    private void save() {
        prefs.edit().putBoolean("running", running).putString("requestId", requestId).putString("state", state).putString("message", message).commit();
    }
}
