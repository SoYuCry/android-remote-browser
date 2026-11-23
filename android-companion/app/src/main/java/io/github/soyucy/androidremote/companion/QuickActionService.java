package io.github.soyucy.androidremote.companion;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Checks foreground packages and the Huawei USB dialog; never reads text inside Feishu. */
public final class QuickActionService extends AccessibilityService {
    private static volatile QuickActionService instance;
    static QuickActionService current() { return instance; }
    @Override protected void onServiceConnected() { instance = this; TaskLog.write(this, "accessibility_connected", ""); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { QuickActionController.get(this).interrupt(); }
    @Override public void onDestroy() {
        TaskLog.write(this, "accessibility_destroy", "");
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
    boolean isUsbConnectionDialog() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            // Restrict Back recovery to this known system dialog, never arbitrary prompts.
            return "com.android.settings".contentEquals(root.getPackageName() == null ? "" : root.getPackageName())
                    && containsExact(root, "USB 连接方式") && containsExact(root, "仅充电");
        } finally { root.recycle(); }
    }
    private boolean containsExact(AccessibilityNodeInfo root, String text) {
        java.util.List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        boolean found = false;
        for (AccessibilityNodeInfo node : nodes) {
            if (text.contentEquals(node.getText() == null ? "" : node.getText())) found = true;
            node.recycle();
        }
        return found;
    }
}
