package io.github.soyucy.androidremote.companion;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Observes only the foreground package; never reads text or clicks inside Feishu. */
public final class QuickActionService extends AccessibilityService {
    private static volatile QuickActionService instance;
    static QuickActionService current() { return instance; }
    @Override protected void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { QuickActionController.get(this).interrupt(); }
    @Override public void onDestroy() {
        if (instance == this) instance = null;
        QuickActionController.get(this).interrupt();
        super.onDestroy();
    }
    String foregroundPackage() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";
        try { return root.getPackageName() == null ? "" : root.getPackageName().toString(); }
        finally { root.recycle(); }
    }
}
