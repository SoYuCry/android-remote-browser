#!/usr/bin/env bash
set -euo pipefail

serial=""
port="5900"
start_proxy=1
preserve_power=0

usage() {
  cat <<'USAGE'
Recover a droidVNC-NG + android-novnc-proxy session after screen capture,
WebSocket proxy, or overnight lock/Doze issues.

Usage:
  ./scripts/recover_droidvnc_session.sh [options]

Options:
  --serial SERIAL      adb serial. Default: auto-pick one authorized USB device.
  --port PORT          droidVNC VNC port. Default: 5900.
  --no-proxy           Do not restart android-novnc-proxy.
  --preserve-power     Do not change stay-awake/screen-timeout settings.
  --help               Show help.

What it does:
  1. wakes the phone and keeps screen on while plugged in;
  2. opens droidVNC-NG admin panel;
  3. if Screen Capturing is DENIED or the server is stopped, taps START;
  4. taps common Android screen-capture consent buttons when visible;
  5. restarts/verifies the noVNC proxy on :6080.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="${2:-}"; shift 2 ;;
    --port) port="${2:-}"; shift 2 ;;
    --no-proxy) start_proxy=0; shift ;;
    --preserve-power) preserve_power=1; shift ;;
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

pkg="net.christianbeier.droidvnc_ng"
xml="/sdcard/window.xml"

echo "Device: $serial"
adb -s "$serial" shell input keyevent WAKEUP >/dev/null 2>&1 || true
if (( preserve_power == 0 )); then
  adb -s "$serial" shell settings put global stay_on_while_plugged_in 15 >/dev/null 2>&1 || true
  adb -s "$serial" shell settings put system screen_off_timeout 1800000 >/dev/null 2>&1 || true
fi

if ! adb -s "$serial" shell pm path "$pkg" >/dev/null 2>&1; then
  echo "droidVNC-NG is not installed." >&2
  exit 1
fi

# Open admin panel so permission/toggle state is inspectable and tappable.
adb -s "$serial" shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
sleep 1

dump_ui() {
  adb -s "$serial" shell uiautomator dump "$xml" >/dev/null 2>&1 || true
  adb -s "$serial" shell cat "$xml" 2>/dev/null | tr -d '\r'
}

tap_node() {
  local pattern="$1"
  local ui bounds x y
  ui=$(dump_ui)
  bounds=$(UI="$ui" python3 - "$pattern" <<'PY'
import os, re, sys, html
pattern=sys.argv[1]
xml=os.environ.get('UI', '')
for node in re.findall(r'<node\b[^>]+>', xml):
    text=html.unescape(re.search(r'text="([^"]*)"', node).group(1) if re.search(r'text="([^"]*)"', node) else '')
    rid=html.unescape(re.search(r'resource-id="([^"]*)"', node).group(1) if re.search(r'resource-id="([^"]*)"', node) else '')
    desc=html.unescape(re.search(r'content-desc="([^"]*)"', node).group(1) if re.search(r'content-desc="([^"]*)"', node) else '')
    if re.search(pattern, text) or re.search(pattern, rid) or re.search(pattern, desc):
        m=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if m:
            print(' '.join(m.groups()))
            break
PY
)
  [[ -z "$bounds" ]] && return 1
  read -r x1 y1 x2 y2 <<<"$bounds"
  x=$(( (x1 + x2) / 2 ))
  y=$(( (y1 + y2) / 2 ))
  adb -s "$serial" shell input tap "$x" "$y" >/dev/null
  return 0
}

ui=$(dump_ui)
if grep -q 'permission_status_screen_capturing[^>]*text="DENIED"' <<<"$ui"; then
  echo "Screen Capturing: DENIED -> requesting capture permission..."
  tap_node 'permission_status_screen_capturing|Screen Capturing' || true
  sleep 1
fi

ui=$(dump_ui)
if grep -q 'resource-id="net.christianbeier.droidvnc_ng:id/toggle"[^>]*text="START"' <<<"$ui"; then
  echo "droidVNC server is stopped -> tapping START..."
  tap_node '^START$|net\.christianbeier\.droidvnc_ng:id/toggle' || true
  sleep 1
fi

# MediaProjection consent dialogs vary by Android/OEM/language. Try common positive buttons.
for _ in 1 2 3 4 5; do
  ui=$(dump_ui)
  if grep -Eqi 'Start now|立即开始|开始|允许|Allow|OK' <<<"$ui"; then
    tap_node 'Start now|立即开始|允许|Allow|^OK$|开始' || true
    sleep 1
  else
    break
  fi
done

if (( start_proxy )); then
  if [[ -x ./scripts/start_android_novnc_proxy.sh ]]; then
    ./scripts/start_android_novnc_proxy.sh --serial "$serial" >/dev/null || true
  else
    echo "Warning: ./scripts/start_android_novnc_proxy.sh not found; skipping proxy restart." >&2
  fi
fi

sleep 1
ui=$(dump_ui)
status=$(UI="$ui" python3 - <<'PY'
import os, re, html
xml=os.environ.get('UI', '')
vals={}
for node in re.findall(r'<node\b[^>]+>', xml):
    rid=html.unescape(re.search(r'resource-id="([^"]*)"', node).group(1) if re.search(r'resource-id="([^"]*)"', node) else '')
    text=html.unescape(re.search(r'text="([^"]*)"', node).group(1) if re.search(r'text="([^"]*)"', node) else '')
    if rid.endswith('permission_status_screen_capturing'):
        vals['Screen Capturing']=text
    if rid.endswith('permission_status_input'):
        vals['Input']=text
    if rid.endswith('toggle'):
        vals['Toggle']=text
for k in ['Screen Capturing','Input','Toggle']:
    print(f'{k}: {vals.get(k, "UNKNOWN")}')
PY
)
printf '%s\n' "$status"

echo "Listeners:"
adb -s "$serial" shell "ss -ltn | grep -E ':(5800|$port|6080)' || true" 2>/dev/null || true

echo
tailscale_ip=$(adb -s "$serial" shell 'ip -f inet addr show 2>/dev/null | sed -n "s/.*inet \(100\.[0-9.]*\)\/.*/\1/p" | head -n 1' | tr -d '\r')
if [[ -n "$tailscale_ip" ]]; then
  echo "Safari URL: http://$tailscale_ip:6080/vnc.html?host=$tailscale_ip&port=6080&path=websockify&encrypt=0&autoconnect=true"
else
  echo "Safari URL: http://<ANDROID_TAILSCALE_IP>:6080/vnc.html?host=<ANDROID_TAILSCALE_IP>&port=6080&path=websockify&encrypt=0&autoconnect=true"
fi
