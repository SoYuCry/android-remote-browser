package io.github.soyucy.androidremote.companion;

final class StatusSnapshot {
    boolean tailscaleInstalled;
    boolean droidVncInstalled;
    boolean proxyRunning;
    boolean vncReachable;
    boolean batteryIgnoringOptimizations;
    String tailscaleIp = "";
    String wifiIp = "";
    String safariUrl = "";
    String proxyLastError = "";

    boolean tailscaleConnected() { return tailscaleIp != null && tailscaleIp.startsWith("100."); }
}
