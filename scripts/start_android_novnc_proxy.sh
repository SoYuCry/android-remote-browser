#!/usr/bin/env bash
set -euo pipefail
serial=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="${2:-}"; shift 2 ;;
    --help|-h) echo "Usage: ./scripts/start_android_novnc_proxy.sh [--serial SERIAL]"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done
adb start-server >/dev/null
if [[ -z "$serial" ]]; then
  serial=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')
fi
if [[ -z "$serial" ]]; then echo "No authorized adb device found" >&2; exit 1; fi
if [[ ! -x tools/android-novnc-proxy/android-novnc-proxy ]]; then
  if ! command -v go >/dev/null 2>&1; then
    echo "Proxy binary missing and go is not installed. Install go or build manually:" >&2
    echo "  GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -o tools/android-novnc-proxy/android-novnc-proxy tools/android-novnc-proxy/main.go" >&2
    exit 1
  fi
  echo "Building android-novnc-proxy for Android arm64..."
  GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -o tools/android-novnc-proxy/android-novnc-proxy tools/android-novnc-proxy/main.go
fi
if [[ ! -f /tmp/droidvnc-apk/assets/novnc/vnc.html ]]; then
  rm -rf /tmp/droidvnc-apk
  mkdir -p /tmp/droidvnc-apk
  unzip -oq downloads/droidvnc-ng.apk -d /tmp/droidvnc-apk
fi
adb -s "$serial" shell 'mkdir -p /data/local/tmp/novnc'
adb -s "$serial" push tools/android-novnc-proxy/android-novnc-proxy /data/local/tmp/android-novnc-proxy >/dev/null
adb -s "$serial" push /tmp/droidvnc-apk/assets/novnc /data/local/tmp/ >/dev/null
adb -s "$serial" shell 'chmod 755 /data/local/tmp/android-novnc-proxy; pidof android-novnc-proxy | xargs -r kill 2>/dev/null || true; nohup /data/local/tmp/android-novnc-proxy -listen :6080 -static /data/local/tmp/novnc -vnc 127.0.0.1:5900 >/data/local/tmp/novnc-proxy.log 2>&1 &'
sleep 1
adb -s "$serial" shell 'pidof android-novnc-proxy; ss -ltn 2>/dev/null | grep :6080; cat /data/local/tmp/novnc-proxy.log | tail -n 5'
tailscale_ip=$(adb -s "$serial" shell 'ip -f inet addr show 2>/dev/null | sed -n "s/.*inet \(100\.[0-9.]*\)\/.*/\1/p" | head -n 1' | tr -d '\r')
if [[ -n "$tailscale_ip" ]]; then
  echo "Open on iPhone: http://$tailscale_ip:6080/vnc.html?host=$tailscale_ip&port=6080&path=websockify&encrypt=0&autoconnect=true"
else
  echo "Open on iPhone: http://<ANDROID_TAILSCALE_IP>:6080/vnc.html?host=<ANDROID_TAILSCALE_IP>&port=6080&path=websockify&encrypt=0&autoconnect=true"
fi
