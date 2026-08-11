#!/usr/bin/env bash
set -euo pipefail

# Prefer Homebrew-installed Android build tools when present.
if [[ -d /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ]]; then
  export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
  export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
fi
if [[ -d /opt/homebrew/share/android-commandlinetools ]]; then
  export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
  export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
fi
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
serial=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="${2:-}"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done
./scripts/build_companion_app.sh
apk="android-companion/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$apk" ]]; then
  echo "APK not found: $apk" >&2
  exit 1
fi
if [[ -n "$serial" ]]; then
  adb -s "$serial" install -r "$apk"
  adb -s "$serial" shell monkey -p io.github.soyucy.androidremote.companion -c android.intent.category.LAUNCHER 1 >/dev/null
else
  adb install -r "$apk"
  adb shell monkey -p io.github.soyucy.androidremote.companion -c android.intent.category.LAUNCHER 1 >/dev/null
fi
