#!/usr/bin/env bash
set -euo pipefail

with_tailscale=0

usage() {
  cat <<'USAGE'
Install host-side tools used by the current iPhone -> Tailscale -> Android noVNC flow.

Usage:
  ./scripts/install_macos_tools.sh [--tailscale]

Installs:
  - Android platform-tools / adb
  - Go, used only if android-novnc-proxy must be rebuilt

Options:
  --tailscale   Also install the Tailscale macOS app. Optional; the final iPhone
                flow does not require the Mac to stay online.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tailscale) with_tailscale=1; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This installer targets macOS." >&2
  exit 1
fi

if ! command -v brew >/dev/null 2>&1; then
  cat >&2 <<'MSG'
Homebrew is not installed. Install Homebrew first from https://brew.sh/, then run this script again.
MSG
  exit 1
fi

brew install --cask android-platform-tools
brew install go

if (( with_tailscale )); then
  brew install --cask tailscale || true
fi

printf 'Installed tool locations:\n'
for cmd in adb go; do
  if command -v "$cmd" >/dev/null 2>&1; then
    printf '  %s -> %s\n' "$cmd" "$(command -v "$cmd")"
  else
    printf '  %s -> missing\n' "$cmd"
  fi
done
