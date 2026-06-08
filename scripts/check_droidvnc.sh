#!/usr/bin/env bash
set -euo pipefail
serial=""
port=""
show_password=0
usage(){ echo "Usage: ./scripts/check_droidvnc.sh [--serial SERIAL] [--port PORT] [--show-password]"; }
while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="${2:-}"; shift 2 ;;
    --port) port="${2:-}"; shift 2 ;;
    --show-password) show_password=1; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 2 ;;
  esac
done
if [[ -f .droidvnc.env ]]; then
  # shellcheck disable=SC1091
  source .droidvnc.env
fi
serial="${serial:-${DROIDVNC_SERIAL:-}}"
port="${port:-${DROIDVNC_PORT:-5901}}"
if [[ -z "$serial" ]]; then
  serial=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')
fi
if [[ -z "$serial" ]]; then echo "No adb device available" >&2; exit 1; fi
pkg="net.christianbeier.droidvnc_ng"
echo "Device: $serial"
echo "Package installed:"
adb -s "$serial" shell pm path "$pkg" || true
echo
echo "Process:"
adb -s "$serial" shell pidof "$pkg" || true
echo
echo "Phone IP candidates:"
adb -s "$serial" shell 'ip -f inet addr show 2>/dev/null | sed -n "s/.*inet \([0-9.]*\)\/.* \([^ ]*\)$/\2 \1/p"' | tr -d '\r' || true
echo
echo "Listening sockets containing $port/5900/5901/5800/6080:"
adb -s "$serial" shell "ss -ltn 2>/dev/null | grep -E '(:$port|:5900|:5901|:5800|:6080)' || true"
echo
echo "VNC credentials file on Mac: .droidvnc.env"
if [[ -n "${DROIDVNC_PASSWORD:-}" ]]; then
  if (( show_password )); then echo "VNC password: $DROIDVNC_PASSWORD"; else echo "VNC password: stored in .droidvnc.env (use --show-password to print)"; fi
fi
