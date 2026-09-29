package io.github.soyucy.androidremote.companion;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;

/** A short-lived visible activity dismisses only an unsecured keyguard. */
public final class WakeActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        QuickActionController controller = QuickActionController.get(this);
        if (!controller.isRunning()) { finish(); return; }
        controller.attach(this);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TextView text = new TextView(this);
        text.setText("正在打开飞书，完成后自动返回桌面…");
        text.setTextSize(20);
        text.setPadding(32, 64, 32, 32);
        setContentView(text);
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard.isKeyguardSecure()) { controller.fail("手机设置了锁屏密码，请先手动解锁"); return; }
        if (keyguard.isKeyguardLocked()) keyguard.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
            @Override public void onDismissSucceeded() { controller.launchFeishu(); }
            @Override public void onDismissError() { controller.fail("无法解除锁屏，请先手动解锁"); }
            @Override public void onDismissCancelled() { controller.fail("解锁已取消"); }
        });
        else controller.launchFeishu();
    }
}
