#!/usr/bin/env bash
set -euo pipefail
serial=""
version_code="59"
usage(){ cat <<'USAGE'
Download and install droidVNC-NG from F-Droid onto an authorized Android device.

Usage:
  ./scripts/install_droidvnc_ng.sh [--serial SERIAL]
USAGE
}
while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="${2:-}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 2 ;;
  esac
done
mkdir -p downloads
apk="downloads/droidvnc-ng.apk"
url="https://f-droid.org/repo/net.christianbeier.droidvnc_ng_${version_code}.apk"
if [[ ! -s "$apk" ]]; then
  curl -L --fail --retry 3 -o "$apk" "$url"
fi
adb start-server >/dev/null
if [[ -z "$serial" ]]; then
  serial=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')
fi
if [[ -z "$serial" ]]; then echo "No authorized adb device found" >&2; exit 1; fi
adb -s "$serial" install -r "$apk"
adb -s "$serial" shell monkey -p net.christianbeier.droidvnc_ng -c android.intent.category.LAUNCHER 1 >/dev/null || true
echo "Installed droidVNC-NG on $serial"
