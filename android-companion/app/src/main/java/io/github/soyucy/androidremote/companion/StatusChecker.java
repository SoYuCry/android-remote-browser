package io.github.soyucy.androidremote.companion;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.StrictMode;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Collections;

final class StatusChecker {
    static final String TAILSCALE_PACKAGE = "com.tailscale.ipn";
    static final String DROIDVNC_PACKAGE = "net.christianbeier.droidvnc_ng";

    static StatusSnapshot check(Context context, ProxyServer proxy) {
        StatusSnapshot s = new StatusSnapshot();
        s.tailscaleInstalled = isInstalled(context, TAILSCALE_PACKAGE);
        s.droidVncInstalled = isInstalled(context, DROIDVNC_PACKAGE);
        s.tailscaleIp = findIPv4("tun0");
        s.wifiIp = findIPv4("wlan0");
        s.proxyRunning = proxy != null && proxy.isRunning();
        s.proxyLastError = proxy == null ? "not started" : proxy.getLastError();
        s.vncReachable = canConnect("127.0.0.1", ProxyServer.DEFAULT_VNC_PORT, 800);
        s.batteryIgnoringOptimizations = isIgnoringBatteryOptimizations(context);
        if (s.tailscaleConnected()) {
            s.safariUrl = vncUrl(s.tailscaleIp);
        } else if (s.wifiIp != null && !s.wifiIp.isEmpty()) {
            s.safariUrl = vncUrl(s.wifiIp);
        }
        return s;
    }

    private static String vncUrl(String host) {
        int port = ProxyServer.DEFAULT_HTTP_PORT;
        return "http://" + host + ":" + port + "/vnc.html?host=" + host + "&port=" + port + "&path=websockify&encrypt=0&autoconnect=true";
    }

    static boolean isInstalled(Context context, String pkg) {
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static Intent launchIntent(Context context, String pkg) {
        return context.getPackageManager().getLaunchIntentForPackage(pkg);
    }

    static Intent batterySettingsIntent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            return intent;
        }
        return new Intent(android.provider.Settings.ACTION_SETTINGS);
    }

    private static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    private static boolean canConnect(String host, int port, int timeoutMs) {
        StrictMode.ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
        try {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder(oldPolicy).permitNetwork().build());
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
                return true;
            }
        } catch (Exception e) {
            return false;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private static String findIPv4(String ifaceName) {
        try {
            NetworkInterface nif = NetworkInterface.getByName(ifaceName);
            if (nif == null || !nif.isUp()) return "";
            for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) return addr.getHostAddress();
            }
        } catch (Exception ignored) {}
        return "";
    }
}
