#!/usr/bin/env bash
set -euo pipefail
serial=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="${2:-}"; shift 2 ;;
    --help|-h) echo "Usage: ./scripts/check_android_tailscale.sh [--serial SERIAL]"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done
adb start-server >/dev/null
if [[ -z "$serial" ]]; then
  serial=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')
fi
if [[ -z "$serial" ]]; then echo "No authorized adb device found" >&2; exit 1; fi

echo "Device: $serial"
echo "Tailscale package:"
adb -s "$serial" shell dumpsys package com.tailscale.ipn 2>/dev/null | grep -E 'versionName|versionCode|Package \[' | sed -n '1,20p' || echo 'not installed'
echo
echo "Tailscale process:"
adb -s "$serial" shell pidof com.tailscale.ipn || true
echo
echo "Android IP interfaces:"
adb -s "$serial" shell 'ip -f inet addr show 2>/dev/null | sed -n "s/.*inet \([0-9.]*\)\/.* \([^ ]*\)$/\2 \1/p"' | tr -d '\r' || true
echo
echo "Likely Tailscale IP(s):"
adb -s "$serial" shell 'ip -f inet addr show 2>/dev/null | sed -n "s/.*inet \(100\.[0-9.]*\)\/.*$/\1/p"' | tr -d '\r' || true
echo
echo "If a 100.x.y.z address appears, open noVNC on iPhone at: http://<that-ip>:6080/vnc.html?host=<that-ip>&port=6080&path=websockify&encrypt=0&autoconnect=true"
