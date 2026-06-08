#!/usr/bin/env bash
set -euo pipefail

serial=""
port="5900"
scaling="0.6"

usage() {
  cat <<'USAGE'
Harden the Android side for long-running droidVNC-NG + Tailscale remote control.

Usage:
  ./scripts/harden_remote_control.sh [options]

Options:
  --serial SERIAL   adb serial. Default: auto-pick one authorized USB device.
  --port PORT       droidVNC port. Default: 5900.
  --scaling FLOAT   droidVNC server scaling. Default: 0.6.
  --help            Show help.

This script applies best-effort persistence settings:
  - keep screen awake while charging/USB is connected;
  - whitelist droidVNC-NG and Tailscale from Doze/app standby where adb allows it;
  - allow background/foreground app ops where supported;
  - keep droidVNC accessibility input enabled;
  - enable droidVNC-NG Start on Boot in the visible admin panel when possible;
  - start/recover droidVNC and restart the :6080 noVNC proxy.

Android's screen-capture/MediaProjection consent may still require manual approval
after reboot or service restart on non-root, non-device-owner devices.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="${2:-}"; shift 2 ;;
    --port) port="${2:-}"; shift 2 ;;
    --scaling) scaling="${2:-}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 2 ;;
  esac
done

adb start-server >/dev/null
if [[ -z "$serial" ]]; then
  devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }' | grep -v ':' || true)
  count=$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l | tr -d ' ')
  if [[ "$count" == "0" ]]; then
    echo "No authorized USB adb device found." >&2
    adb devices -l >&2
    exit 1
  elif [[ "$count" != "1" ]]; then
    echo "Multiple USB adb devices found; pass --serial SERIAL." >&2
    adb devices -l >&2
    exit 1
  fi
  serial=$(printf '%s\n' "$devices" | head -n 1)
fi

droidvnc="net.christianbeier.droidvnc_ng"
tailscale="com.tailscale.ipn"
input_service="$droidvnc/.InputService"
xml="/sdcard/window.xml"

echo "Device: $serial"
for pkg in "$droidvnc" "$tailscale"; do
  if ! adb -s "$serial" shell pm path "$pkg" >/dev/null 2>&1; then
    echo "Warning: package not installed: $pkg" >&2
  fi
done

echo "Keeping phone awake while powered..."
adb -s "$serial" shell input keyevent WAKEUP >/dev/null 2>&1 || true
adb -s "$serial" shell svc power stayon true >/dev/null 2>&1 || true
adb -s "$serial" shell settings put global stay_on_while_plugged_in 15 >/dev/null 2>&1 || true
adb -s "$serial" shell settings put system screen_off_timeout 2147483647 >/dev/null 2>&1 || true

for pkg in "$droidvnc" "$tailscale"; do
  echo "Whitelisting $pkg from idle/background limits where supported..."
  adb -s "$serial" shell cmd deviceidle whitelist +"$pkg" >/dev/null 2>&1 || true
  adb -s "$serial" shell am set-inactive "$pkg" false >/dev/null 2>&1 || true
  for op in RUN_IN_BACKGROUND RUN_ANY_IN_BACKGROUND START_FOREGROUND WAKE_LOCK PROJECT_MEDIA; do
    adb -s "$serial" shell cmd appops set "$pkg" "$op" allow >/dev/null 2>&1 || true
  done
  adb -s "$serial" shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
done

echo "Ensuring droidVNC accessibility input service is enabled..."
current=$(adb -s "$serial" shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')
if [[ -z "$current" || "$current" == "null" ]]; then
  new_value="$input_service"
elif [[ ":$current:" == *":$input_service:"* ]]; then
  new_value="$current"
else
  new_value="$input_service:$current"
fi
adb -s "$serial" shell settings put secure enabled_accessibility_services "$new_value" >/dev/null
adb -s "$serial" shell settings put secure accessibility_enabled 1 >/dev/null

# Re-seed droidVNC defaults with start-on-boot enabled while preserving the password.
if [[ -x ./scripts/configure_droidvnc.sh ]]; then
  echo "Reconfiguring droidVNC defaults for port/scaling and start-on-boot..."
  ./scripts/configure_droidvnc.sh --serial "$serial" --port "$port" --scaling "$scaling" --start-on-boot >/dev/null || true
fi

# Enable Start on Boot in UI if the switch is visible and off.
echo "Opening droidVNC admin panel to enable Start on Boot if visible..."
adb -s "$serial" shell monkey -p "$droidvnc" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
sleep 1
adb -s "$serial" shell uiautomator dump "$xml" >/dev/null 2>&1 || true
ui=$(adb -s "$serial" shell cat "$xml" 2>/dev/null | tr -d '\r' || true)
if grep -q 'resource-id="net.christianbeier.droidvnc_ng:id/settings_start_on_boot"[^>]*checked="false"' <<<"$ui"; then
  echo "Tapping droidVNC Start on Boot switch..."
  bounds=$(UI="$ui" python3 - <<'PY'
import os, re
xml=os.environ.get('UI','')
for node in re.findall(r'<node\b[^>]+>', xml):
    if 'net.christianbeier.droidvnc_ng:id/settings_start_on_boot' in node:
        m=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if m:
            print(' '.join(m.groups()))
        break
PY
)
  if [[ -n "$bounds" ]]; then
    read -r x1 y1 x2 y2 <<<"$bounds"
    adb -s "$serial" shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 )) >/dev/null || true
    sleep 1
  fi
fi

if [[ -x ./scripts/recover_droidvnc_session.sh ]]; then
  echo "Recovering current droidVNC/noVNC session..."
  ./scripts/recover_droidvnc_session.sh --serial "$serial" --port "$port" || true
elif [[ -x ./scripts/start_android_novnc_proxy.sh ]]; then
  ./scripts/start_android_novnc_proxy.sh --serial "$serial" >/dev/null || true
fi

echo
echo "Idle whitelist check:"
adb -s "$serial" shell cmd deviceidle whitelist | grep -E "($droidvnc|$tailscale)" || true

echo
echo "Persistence hardening applied. Remaining non-automatable item: Android may still ask for screen-capture consent after reboot/service restart. If that happens, open droidVNC-NG and tap START/Allow."
