#!/usr/bin/env bash
set -euo pipefail

serial=""
old=""

usage() {
  cat <<'USAGE'
Clear Android lock-screen credential on an owned, authorized device.

Usage:
  ./scripts/clear_android_lock_credential.sh [--serial SERIAL]
  ANDROID_LOCK_OLD='current-pin-or-password' ./scripts/clear_android_lock_credential.sh [--serial SERIAL]

For safety, the script does not save the credential. Prefer the interactive
prompt so it is not stored in shell history.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="${2:-}"; shift 2 ;;
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

old="${ANDROID_LOCK_OLD:-}"
if [[ -z "$old" ]]; then
  printf 'Current Android PIN/password: ' >&2
  stty -echo 2>/dev/null || true
  IFS= read -r old
  stty echo 2>/dev/null || true
  printf '\n' >&2
fi

if [[ -z "$old" ]]; then
  echo "Empty credential; aborting." >&2
  exit 1
fi

echo "Verifying current credential..."
adb -s "$serial" shell locksettings verify --old "$old" >/dev/null

echo "Clearing lock credential..."
adb -s "$serial" shell locksettings clear --old "$old"

# If credential is cleared, this can switch Swipe -> None on many devices.
adb -s "$serial" shell locksettings set-disabled true >/dev/null 2>&1 || true

echo "Result:"
adb -s "$serial" shell locksettings get-disabled 2>/dev/null || true

echo "Done. Test by locking/unlocking once. If Android still shows Swipe, go to Settings > Security > Screen lock and choose None."
