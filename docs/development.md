# Development

## Run checks

```bash
./test/self_test.sh
```

The self-test verifies active scripts, docs, and the proxy build path. It may print the currently connected ADB device if one is attached; do not paste private device identifiers into public issues.

## Build the proxy manually

```bash
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 \
  go build -o tools/android-novnc-proxy/android-novnc-proxy \
  tools/android-novnc-proxy/main.go
```

The generated binary is ignored by Git and should not be committed.

## Release hygiene

Before publishing changes:

```bash
./test/self_test.sh
git status --ignored --short
```

Confirm ignored local files such as `.droidvnc.env`, `.secrets/`, `downloads/`, and `.omx/` are not staged.

## Style

- Prefer clear shell scripts with `set -euo pipefail`.
- Keep examples device-neutral: use `<ANDROID_SERIAL>` and `<ANDROID_TAILSCALE_IP>`.
- Never add real IPs, serial numbers, passwords, or generated APK/binary artifacts to docs.
- If a script prints credentials, require an explicit opt-in flag.
