#!/usr/bin/env bash
set -euo pipefail

failures=0
warnings=0

pass() { printf '\033[32mPASS\033[0m %s\n' "$*"; }
fail() { printf '\033[31mFAIL\033[0m %s\n' "$*"; failures=$((failures + 1)); }
warn() { printf '\033[33mWARN\033[0m %s\n' "$*"; warnings=$((warnings + 1)); }

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

printf 'Android iPhone/Tailscale/noVNC toolkit self-test\n'
printf 'Root: %s\n\n' "$ROOT"

required_files=(
  README.md
  README.en.md
  QUICKSTART.zh-CN.md
  GUIDE.zh-CN.md
  RUNBOOK.zh-CN.md
  FILES.md
  ACCEPTANCE.md
  CONTRIBUTING.md
  CHANGELOG.md
  SECURITY.md
  OPEN_SOURCE_CHECKLIST.md
  docs/architecture.md
  docs/troubleshooting.md
  docs/development.md
  docs/assets/demo-success.jpg
  .github/ISSUE_TEMPLATE/bug_report.md
  .github/ISSUE_TEMPLATE/docs_improvement.md
  .github/pull_request_template.md
  scripts/install_macos_tools.sh
  scripts/install_droidvnc_ng.sh
  scripts/configure_droidvnc.sh
  scripts/start_android_novnc_proxy.sh
  scripts/check_android_tailscale.sh
  scripts/check_droidvnc.sh
  scripts/recover_droidvnc_session.sh
  scripts/configure_battery_friendly_persistence.sh
  scripts/harden_remote_control.sh
  scripts/clear_android_lock_credential.sh
  tools/android-novnc-proxy/main.go
)

printf '1) Required active files\n'
for f in "${required_files[@]}"; do
  if [[ -f "$f" ]]; then pass "$f exists"; else fail "$f is missing"; fi
done

printf '\n2) Executable bits\n'
for f in scripts/*.sh; do
  if [[ -x "$f" ]]; then pass "$f is executable"; else fail "$f is not executable"; fi
done

printf '\n3) Shell syntax\n'
if bash -n scripts/*.sh; then
  pass 'active shell scripts parse with bash -n'
else
  fail 'active shell syntax check failed'
fi

printf '\n4) Required commands\n'
for cmd in adb python3; do
  if command -v "$cmd" >/dev/null 2>&1; then
    pass "$cmd found at $(command -v "$cmd")"
  else
    fail "$cmd not found"
  fi
done
if command -v go >/dev/null 2>&1; then
  pass "go found at $(command -v go)"
else
  warn 'go not found; okay if tools/android-novnc-proxy/android-novnc-proxy is already built'
fi

printf '\n5) Help/usage entrypoints\n'
for cmd in \
  './scripts/install_droidvnc_ng.sh --help' \
  './scripts/configure_droidvnc.sh --help' \
  './scripts/check_droidvnc.sh --help' \
  './scripts/recover_droidvnc_session.sh --help' \
  './scripts/configure_battery_friendly_persistence.sh --help' \
  './scripts/harden_remote_control.sh --help' \
  './scripts/clear_android_lock_credential.sh --help' \
  './scripts/start_android_novnc_proxy.sh --help'; do
  if eval "$cmd" >/dev/null; then
    pass "$cmd"
  else
    fail "$cmd failed"
  fi
done

printf '\n6) Documentation content checks\n'
if grep -q '<ANDROID_TAILSCALE_IP>:6080' README.md QUICKSTART.zh-CN.md GUIDE.zh-CN.md RUNBOOK.zh-CN.md; then
  pass 'docs include current noVNC/Tailscale endpoint'
else
  fail 'docs missing current endpoint'
fi
if grep -q 'Screen Capturing' QUICKSTART.zh-CN.md RUNBOOK.zh-CN.md ACCEPTANCE.md; then
  pass 'docs mention Screen Capturing recovery'
else
  fail 'docs missing Screen Capturing recovery'
fi
if grep -q 'configure_battery_friendly_persistence' README.md QUICKSTART.zh-CN.md RUNBOOK.zh-CN.md; then
  pass 'docs mention battery-friendly persistence script'
else
  fail 'docs missing battery-friendly persistence script'
fi
if grep -qi '虚假\|规避\|unauthorized\|未授权' README.md QUICKSTART.zh-CN.md; then
  pass 'safety boundary is documented'
else
  fail 'safety boundary is not documented'
fi

printf '\n7) Proxy build readiness\n'
if [[ -x tools/android-novnc-proxy/android-novnc-proxy ]]; then
  if file tools/android-novnc-proxy/android-novnc-proxy | grep -qi 'aarch64\|arm64\|ARM aarch64'; then
    pass 'existing android-novnc-proxy appears Android/arm64 compatible'
  else
    warn 'existing android-novnc-proxy binary architecture was not recognized as arm64 by file(1)'
  fi
elif command -v go >/dev/null 2>&1; then
  pass 'proxy binary absent but go is available for on-demand build'
else
  fail 'proxy binary absent and go is unavailable'
fi

printf '\n8) Current Android device state\n'
if command -v adb >/dev/null 2>&1; then
  adb start-server >/dev/null || true
  adb_output="$(adb devices -l | sed '1d' | sed '/^$/d' || true)"
  if [[ -z "$adb_output" ]]; then
    warn 'no Android device currently visible to adb; runtime state cannot be checked now'
  else
    printf '%s\n' "$adb_output"
    authorized_count="$(printf '%s\n' "$adb_output" | awk '$2 == "device" { c++ } END { print c+0 }')"
    if (( authorized_count > 0 )); then
      pass "$authorized_count authorized Android device(s) visible"
    else
      warn 'Android device visible but not authorized/ready'
    fi
  fi
fi

printf '\nSummary: %d failure(s), %d warning(s)\n' "$failures" "$warnings"
if (( failures > 0 )); then
  exit 1
fi
