---
name: Bug report
about: Report setup, connection, or recovery problems
title: "[Bug]: "
labels: bug
assignees: ""
---

## What happened?

Describe the issue.

## Environment

- Android version / device model:
- iPhone iOS version:
- Tailscale status on both devices:
- droidVNC-NG version if known:

## Checks run

```bash
./scripts/check_android_tailscale.sh --serial <ANDROID_SERIAL>
./scripts/check_droidvnc.sh --serial <ANDROID_SERIAL> --port 5900
```

Paste sanitized output only. Remove real serials, IPs, passwords, and private tailnet names.

## Expected behavior

What did you expect to happen?
