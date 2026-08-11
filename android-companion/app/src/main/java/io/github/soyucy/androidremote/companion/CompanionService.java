package io.github.soyucy.androidremote.companion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public final class CompanionService extends Service {
    static final String ACTION_START = "io.github.soyucy.androidremote.companion.START";
    static final String ACTION_STOP = "io.github.soyucy.androidremote.companion.STOP";
    static final String ACTION_RESTART = "io.github.soyucy.androidremote.companion.RESTART";
    private static final String CHANNEL_ID = "remote_browser_service";
    private static final int NOTIFICATION_ID = 6080;

    private static CompanionService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ProxyServer proxy;
    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            ensureProxy();
            updateNotification();
            handler.postDelayed(this, 60_000);
        }
    };

    static void start(Context context) {
        Intent intent = new Intent(context, CompanionService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    static void stop(Context context) {
        context.startService(new Intent(context, CompanionService.class).setAction(ACTION_STOP));
    }

    static void restart(Context context) {
        Intent intent = new Intent(context, CompanionService.class).setAction(ACTION_RESTART);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    static ProxyServer currentProxy() {
        return instance == null ? null : instance.proxy;
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification(StatusChecker.check(this, proxy)));
        ensureProxy();
        handler.post(watchdog);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopProxy();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_RESTART.equals(action)) {
            stopProxy();
            ensureProxy();
        } else {
            ensureProxy();
        }
        updateNotification();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopProxy();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    private synchronized void ensureProxy() {
        try {
            if (proxy == null) {
                proxy = new ProxyServer(getAssets(), ProxyServer.DEFAULT_HTTP_PORT, ProxyServer.DEFAULT_VNC_HOST, ProxyServer.DEFAULT_VNC_PORT);
            }
            if (!proxy.isRunning()) proxy.start();
        } catch (Exception ignored) {
            // ProxyServer keeps lastError for user-visible diagnostics when available.
        }
    }

    private synchronized void stopProxy() {
        if (proxy != null) {
            proxy.close();
            proxy = null;
        }
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(StatusChecker.check(this, proxy)));
    }

    private Notification buildNotification(StatusSnapshot status) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text;
        if (status.proxyRunning && status.vncReachable && status.tailscaleConnected()) {
            text = "Running: " + status.tailscaleIp + ":6080";
        } else if (!status.proxyRunning) {
            text = "Proxy stopped; watchdog will retry";
        } else if (!status.vncReachable) {
            text = "Proxy running; droidVNC 5900 unreachable";
        } else {
            text = "Proxy running; Tailscale IP not detected";
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("Remote Browser Companion")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(io.github.soyucy.androidremote.companion.R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(channel);
    }
}
