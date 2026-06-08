#!/usr/bin/env bash
set -euo pipefail

serial=""
screen_timeout_ms="60000" # 1 minute: battery-friendly, not always-on

usage() {
  cat <<'USAGE'
Configure Android for battery-friendly remote-control persistence.

Goal:
  Let the screen turn off to save battery, while keeping Tailscale and
  droidVNC-NG as persistent as Android/ADB reasonably allows.

Usage:
  ./scripts/configure_battery_friendly_persistence.sh [options]

Options:
  --serial SERIAL          adb serial. Default: auto-pick one authorized USB device.
  --screen-timeout MS      screen timeout in ms. Default: 60000 (1 min).
  --help                   Show help.

This does NOT promise permanent MediaProjection/screen-capture permission.
If Android revokes Screen Capturing, reopen droidVNC-NG and tap START/Allow.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="${2:-}"; shift 2 ;;
    --screen-timeout) screen_timeout_ms="${2:-}"; shift 2 ;;
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

echo "Device: $serial"

# Battery-friendly display policy: do not force always-on, allow screen to sleep.
echo "Configuring screen to sleep normally after ${screen_timeout_ms}ms..."
adb -s "$serial" shell svc power stayon false >/dev/null 2>&1 || true
adb -s "$serial" shell settings put global stay_on_while_plugged_in 0 >/dev/null 2>&1 || true
adb -s "$serial" shell settings put system screen_off_timeout "$screen_timeout_ms" >/dev/null 2>&1 || true

# Keep network/control apps out of the most aggressive Android idle paths.
for pkg in "$tailscale" "$droidvnc"; do
  if ! adb -s "$serial" shell pm path "$pkg" >/dev/null 2>&1; then
    echo "Warning: package not installed: $pkg" >&2
    continue
  fi

  echo "Relaxing idle/background limits for $pkg..."
  adb -s "$serial" shell cmd deviceidle whitelist +"$pkg" >/dev/null 2>&1 || true
  adb -s "$serial" shell am set-inactive "$pkg" false >/dev/null 2>&1 || true
  adb -s "$serial" shell am set-standby-bucket "$pkg" active >/dev/null 2>&1 || true

  # AppOps vary by Android version/OEM; ignore unsupported ones.
  for op in RUN_IN_BACKGROUND RUN_ANY_IN_BACKGROUND START_FOREGROUND WAKE_LOCK PROJECT_MEDIA; do
    adb -s "$serial" shell cmd appops set "$pkg" "$op" allow >/dev/null 2>&1 || true
  done

  adb -s "$serial" shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  adb -s "$serial" shell monkey -p "$pkg" 1 >/dev/null 2>&1 || true
  sleep 0.2
done

# Keep droidVNC input channel enabled. This is separate from screen capture.
echo "Ensuring droidVNC accessibility input service remains enabled..."
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

# Best-effort start/recover current services without forcing screen always-on.
if [[ -x ./scripts/recover_droidvnc_session.sh ]]; then
  echo "Recovering current session once after policy changes..."
  ./scripts/recover_droidvnc_session.sh --serial "$serial" --port 5900 --preserve-power >/dev/null || true
elif [[ -x ./scripts/start_android_novnc_proxy.sh ]]; then
  ./scripts/start_android_novnc_proxy.sh --serial "$serial" >/dev/null || true
fi

echo
echo "Status:"
echo "screen_off_timeout=$(adb -s "$serial" shell settings get system screen_off_timeout | tr -d '\r')"
echo "stay_on_while_plugged_in=$(adb -s "$serial" shell settings get global stay_on_while_plugged_in | tr -d '\r')"
echo
echo "Idle whitelist:"
adb -s "$serial" shell cmd deviceidle whitelist | grep -E "($tailscale|$droidvnc)" || true

echo
echo "Standby buckets:"
for pkg in "$tailscale" "$droidvnc"; do
  printf '%s: ' "$pkg"
  adb -s "$serial" shell am get-standby-bucket "$pkg" 2>/dev/null | tr -d '\r' || true
done

echo
echo "Listeners:"
adb -s "$serial" shell "ss -ltn | grep -E ':(5900|6080)' || true" 2>/dev/null || true

echo
echo "Done. Screen may sleep to save battery; if visual feed freezes later, manually reopen droidVNC-NG and grant Screen Capturing again."
