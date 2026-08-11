package io.github.soyucy.androidremote.companion;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout statusList;
    private TextView urlView;
    private TextView noteView;
    private StatusSnapshot lastStatus;
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 3000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CompanionService.start(this);
        requestNotificationPermissionIfNeeded();
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshTask.run();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Android Remote Browser Companion");
        title.setTextSize(22);
        title.setTextColor(Color.rgb(32, 32, 32));
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("守护 noVNC 6080 入口，并检查 Tailscale / droidVNC-NG 状态。不会执行第三方 App 自动化。 ");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(90, 90, 90));
        subtitle.setPadding(0, dp(8), 0, dp(14));
        root.addView(subtitle);

        statusList = new LinearLayout(this);
        statusList.setOrientation(LinearLayout.VERTICAL);
        root.addView(statusList);

        TextView urlLabel = label("Safari URL");
        urlLabel.setPadding(0, dp(18), 0, dp(6));
        root.addView(urlLabel);
        urlView = new TextView(this);
        urlView.setTextSize(14);
        urlView.setTextColor(Color.rgb(45, 45, 45));
        urlView.setTextIsSelectable(true);
        urlView.setPadding(dp(12), dp(10), dp(12), dp(10));
        urlView.setBackgroundColor(Color.rgb(245, 245, 245));
        root.addView(urlView);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.setPadding(0, dp(16), 0, 0);
        root.addView(buttons);
        buttons.addView(button("Restart 6080 Proxy", v -> { CompanionService.restart(this); toast("Restart requested"); handler.postDelayed(this::refresh, 700); }));
        buttons.addView(button("Copy Safari URL", v -> copyUrl()));
        buttons.addView(button("Open droidVNC-NG", v -> openPackage(StatusChecker.DROIDVNC_PACKAGE)));
        buttons.addView(button("Open Tailscale", v -> openPackage(StatusChecker.TAILSCALE_PACKAGE)));
        buttons.addView(button("Open Accessibility Settings", v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        buttons.addView(button("Open Battery Optimization Settings", v -> startActivity(StatusChecker.batterySettingsIntent(this))));
        buttons.addView(button("Refresh", v -> refresh()));

        noteView = new TextView(this);
        noteView.setTextSize(13);
        noteView.setTextColor(Color.rgb(90, 90, 90));
        noteView.setPadding(0, dp(18), 0, 0);
        root.addView(noteView);
        setContentView(scroll);
    }

    private void refresh() {
        lastStatus = StatusChecker.check(this, CompanionService.currentProxy());
        statusList.removeAllViews();
        addRow("Companion proxy :6080", lastStatus.proxyRunning, lastStatus.proxyRunning ? "RUNNING" : "STOPPED");
        addRow("droidVNC-NG installed", lastStatus.droidVncInstalled, lastStatus.droidVncInstalled ? "YES" : "NO");
        addRow("droidVNC 127.0.0.1:5900", lastStatus.vncReachable, lastStatus.vncReachable ? "REACHABLE" : "UNREACHABLE");
        addRow("Tailscale installed", lastStatus.tailscaleInstalled, lastStatus.tailscaleInstalled ? "YES" : "NO");
        addRow("Tailscale IP", lastStatus.tailscaleConnected(), blank(lastStatus.tailscaleIp) ? "NOT DETECTED" : lastStatus.tailscaleIp);
        addRow("Wi-Fi IP", !blank(lastStatus.wifiIp), blank(lastStatus.wifiIp) ? "NOT DETECTED" : lastStatus.wifiIp);
        addRow("Battery optimization", lastStatus.batteryIgnoringOptimizations, lastStatus.batteryIgnoringOptimizations ? "IGNORED" : "MAY LIMIT BACKGROUND");
        urlView.setText(blank(lastStatus.safariUrl) ? "等待 Tailscale 或 Wi-Fi IP..." : lastStatus.safariUrl);
        String note = "说明：Screen Capture / Input / Start on Boot 仍由 droidVNC-NG 和 Android 系统权限控制。" +
                "如果网页能打开但画面黑或不可控，请打开 droidVNC-NG 检查权限面板。";
        if (!blank(lastStatus.proxyLastError) && !lastStatus.proxyRunning) note += "\nProxy error: " + lastStatus.proxyLastError;
        noteView.setText(note);
    }

    private void addRow(String name, boolean ok, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(5), 0, dp(5));
        TextView left = new TextView(this);
        left.setText(name);
        left.setTextSize(15);
        left.setTextColor(Color.rgb(50, 50, 50));
        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView right = new TextView(this);
        right.setText(value);
        right.setTextSize(14);
        right.setGravity(Gravity.END);
        right.setTextColor(ok ? Color.rgb(46, 125, 50) : Color.rgb(198, 40, 40));
        row.addView(right, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        statusList.addView(row);
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        b.setPadding(0, dp(4), 0, dp(4));
        return b;
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(16);
        v.setTextColor(Color.rgb(32, 32, 32));
        return v;
    }

    private void copyUrl() {
        if (lastStatus == null || blank(lastStatus.safariUrl)) {
            toast("No URL available yet");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Safari URL", lastStatus.safariUrl));
        toast("Copied");
    }

    private void openPackage(String pkg) {
        Intent intent = StatusChecker.launchIntent(this, pkg);
        if (intent == null) {
            toast("App not installed: " + pkg);
            return;
        }
        startActivity(intent);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }
}
