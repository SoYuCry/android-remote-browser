package io.github.soyucy.androidremote.companion;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.WindowManager;
import android.widget.TextView;

/** Wait for a visible, resumed wake window before handing off to Feishu. */
public final class WakeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long deadline;
    private boolean launched, dismissRequested;

    static boolean screenOn(Context context) {
        Display display = context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        return context.getSystemService(PowerManager.class).isInteractive()
                && display != null && display.getState() == Display.STATE_ON;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        QuickActionController controller = QuickActionController.get(this);
        if (!controller.isRunning()) { finish(); return; }
        controller.attach(this);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TextView text = new TextView(this);
        text.setText("正在唤醒屏幕，随后打开飞书…");
        text.setTextSize(20);
        text.setPadding(32, 64, 32, 32);
        setContentView(text);
        deadline = SystemClock.elapsedRealtime() + 4_000;
        if (getSystemService(KeyguardManager.class).isKeyguardSecure())
            controller.fail("手机设置了锁屏密码，请先手动解锁");
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacksAndMessages(null);
        handler.post(this::awaitScreen);
    }

    private void awaitScreen() {
        QuickActionController controller = QuickActionController.get(this);
        if (launched || isFinishing() || !controller.isRunning()) return;
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard.isKeyguardLocked() && !dismissRequested) {
            dismissRequested = true;
            keyguard.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissError() { controller.fail("无法解除锁屏，请先手动解锁"); }
                @Override public void onDismissCancelled() { controller.fail("解锁已取消"); }
            });
        }
        if (hasWindowFocus() && screenOn(this) && !keyguard.isKeyguardLocked()) {
            launched = true;
            TaskLog.write(this, "screen_confirmed", "interactive=true display=ON focus=true");
            controller.launchFeishu();
        } else if (SystemClock.elapsedRealtime() >= deadline) {
            TaskLog.write(this, "screen_timeout", "screenOn=" + screenOn(this)
                    + " focus=" + hasWindowFocus() + " locked=" + keyguard.isKeyguardLocked());
            controller.fail("未确认屏幕亮起，已停止打开飞书");
        } else handler.postDelayed(this::awaitScreen, 100);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}