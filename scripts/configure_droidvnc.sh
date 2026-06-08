#!/usr/bin/env bash
set -euo pipefail

serial=""
port=5901
scaling="0.7"
force_clear=0
fallback_capture=0
start_on_boot=0
rotate_credentials=0

usage() {
  cat <<'USAGE'
Configure and start droidVNC-NG on the connected Android phone for direct
control from iPhone/VNC clients over LAN or private VPN.

Usage:
  ./scripts/configure_droidvnc.sh [options]

Options:
  --serial SERIAL       adb serial. Default: auto-pick one USB/authorized device.
  --port PORT           VNC listening port. Default: 5901.
  --scaling FLOAT       Server-side scaling 0.1-1.0. Default: 0.7.
  --force-clear         Clear droidVNC-NG app data before pre-seeding defaults.
  --fallback-capture    Use droidVNC fallback capture mode. Default: fast MediaProjection mode.
  --start-on-boot      Ask droidVNC-NG to start after Android boot.
  --rotate-credentials Regenerate VNC password and droidVNC Intent API key.
  --help                Show help.

Outputs credentials to .droidvnc.env (chmod 600). Keep that file private.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="${2:-}"; shift 2 ;;
    --port) port="${2:-}"; shift 2 ;;
    --scaling) scaling="${2:-}"; shift 2 ;;
    --force-clear) force_clear=1; shift ;;
    --fallback-capture) fallback_capture=1; shift ;;
    --start-on-boot) start_on_boot=1; shift ;;
    --rotate-credentials) rotate_credentials=1; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 2 ;;
  esac
done

pkg="net.christianbeier.droidvnc_ng"
service="net.christianbeier.droidvnc_ng/.MainService"
input_service="net.christianbeier.droidvnc_ng/.InputService"

for cmd in adb python3; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing $cmd" >&2
    exit 1
  fi
done
adb start-server >/dev/null

if [[ -z "$serial" ]]; then
  devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }' | sed '/^$/d')
  # Prefer USB serial, because wireless ADB can restart while configuring.
  usb_devices=$(printf '%s\n' "$devices" | grep -v ':' || true)
  if [[ -n "$usb_devices" ]]; then devices="$usb_devices"; fi
  count=$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l | tr -d ' ')
  if [[ "$count" == "0" ]]; then
    echo "No authorized adb device found." >&2
    adb devices -l >&2
    exit 1
  elif [[ "$count" != "1" ]]; then
    echo "Multiple devices found; pass --serial SERIAL:" >&2
    adb devices -l >&2
    exit 1
  fi
  serial=$(printf '%s\n' "$devices" | head -n 1)
fi

if ! adb -s "$serial" shell pm path "$pkg" >/dev/null 2>&1; then
  echo "droidVNC-NG is not installed on $serial. Install it first:" >&2
  echo "  adb -s $serial install -r downloads/droidvnc-ng.apk" >&2
  exit 1
fi

if (( force_clear )); then
  echo "Clearing existing droidVNC-NG app data..."
  adb -s "$serial" shell pm clear "$pkg" >/dev/null
fi

mkdir -p .secrets
chmod 700 .secrets
if [[ -f .droidvnc.env && $rotate_credentials -eq 0 ]]; then
  # shellcheck disable=SC1091
  source .droidvnc.env
fi
DROIDVNC_PASSWORD="${DROIDVNC_PASSWORD:-$(python3 - <<'PY'
import secrets,string
alphabet=string.ascii_letters+string.digits
print('vnc-'+''.join(secrets.choice(alphabet) for _ in range(18)))
PY
)}"
DROIDVNC_ACCESS_KEY="${DROIDVNC_ACCESS_KEY:-$(python3 - <<'PY'
import secrets,string
alphabet=string.ascii_letters+string.digits
print('key-'+''.join(secrets.choice(alphabet) for _ in range(24)))
PY
)}"
cat > .droidvnc.env <<ENV
DROIDVNC_SERIAL='$serial'
DROIDVNC_PORT='$port'
DROIDVNC_SCALING='$scaling'
DROIDVNC_PASSWORD='$DROIDVNC_PASSWORD'
DROIDVNC_ACCESS_KEY='$DROIDVNC_ACCESS_KEY'
ENV
chmod 600 .droidvnc.env

start_on_boot_json="$([[ $start_on_boot -eq 1 ]] && echo true || echo false)"
cat > .secrets/droidvnc-defaults.json <<JSON
{
  "port": $port,
  "scaling": $scaling,
  "viewOnly": false,
  "showPointers": true,
  "fileTransfer": false,
  "password": "$DROIDVNC_PASSWORD",
  "accessKey": "$DROIDVNC_ACCESS_KEY",
  "startOnBoot": $start_on_boot_json,
  "startOnBootDelay": 0
}
JSON

remote_dir="/sdcard/Android/data/$pkg/files"
adb -s "$serial" shell "mkdir -p '$remote_dir'"
adb -s "$serial" push .secrets/droidvnc-defaults.json "$remote_dir/defaults.json" >/dev/null

echo "Granting screen-capture app-op (PROJECT_MEDIA) if supported..."
adb -s "$serial" shell cmd appops set "$pkg" PROJECT_MEDIA allow >/dev/null 2>&1 || true

echo "Enabling droidVNC-NG accessibility input service via adb settings..."
current=$(adb -s "$serial" shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')
if [[ -z "$current" || "$current" == "null" ]]; then
  new_value="$input_service"
elif [[ ":$current:" == *":$input_service:"* ]]; then
  new_value="$current"
else
  new_value="$input_service:$current"
fi
adb -s "$serial" shell settings put secure enabled_accessibility_services "$new_value"
adb -s "$serial" shell settings put secure accessibility_enabled 1

echo "Starting droidVNC-NG service on port $port..."
adb -s "$serial" shell am start-foreground-service \
  -n "$service" \
  -a net.christianbeier.droidvnc_ng.ACTION_START \
  --es net.christianbeier.droidvnc_ng.EXTRA_ACCESS_KEY "$DROIDVNC_ACCESS_KEY" \
  --ei net.christianbeier.droidvnc_ng.EXTRA_PORT "$port" \
  --es net.christianbeier.droidvnc_ng.EXTRA_PASSWORD "$DROIDVNC_PASSWORD" \
  --ef net.christianbeier.droidvnc_ng.EXTRA_SCALING "$scaling" \
  --ez net.christianbeier.droidvnc_ng.EXTRA_VIEW_ONLY false \
  --ez net.christianbeier.droidvnc_ng.EXTRA_SHOW_POINTERS true \
  --ez net.christianbeier.droidvnc_ng.EXTRA_FILE_TRANSFER false \
  --ez net.christianbeier.droidvnc_ng.EXTRA_FALLBACK_SCREEN_CAPTURE "$([[ $fallback_capture -eq 1 ]] && echo true || echo false)" >/dev/null || true

sleep 2
phone_ip=$(adb -s "$serial" shell 'ip route get 8.8.8.8 2>/dev/null | sed -n "s/.* src \([0-9.]*\).*/\1/p" | head -n 1' | tr -d '\r')
[[ -z "$phone_ip" ]] && phone_ip=$(adb -s "$serial" shell 'ip -f inet addr show wlan0 2>/dev/null | sed -n "s/.*inet \([0-9.]*\).*/\1/p" | head -n 1' | tr -d '\r')

echo
echo "droidVNC-NG configured. Credentials saved in .droidvnc.env"
echo "VNC endpoint candidate: ${phone_ip:-<phone-ip>}:$port"
echo "VNC password: $DROIDVNC_PASSWORD"
echo
echo "If Android shows a screen-capture or accessibility prompt, approve it."
echo "Check server with: ./scripts/check_droidvnc.sh --serial '$serial'"
