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

if [[ ! -d android-companion ]]; then
  echo "android-companion/ not found" >&2
  exit 1
fi

if [[ ! -f android-companion/app/src/main/assets/novnc/app/ui.js ]]; then
  ./scripts/prepare_companion_assets.sh
fi
npm ci --no-audit --no-fund
cd android-companion
if [[ -x ./gradlew ]]; then
  ./gradlew assembleDebug
elif command -v gradle >/dev/null 2>&1; then
  gradle assembleDebug
else
  cat >&2 <<MSG
Gradle is not installed and android-companion/gradlew is not present.
Open android-companion/ in Android Studio, or install Gradle/JDK and run this script again.
MSG
  exit 1
fi
