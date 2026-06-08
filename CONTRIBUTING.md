# Contributing

Thanks for helping improve Android Remote Browser.

Good contributions include:

- clearer setup or troubleshooting docs;
- support for more Android/OEM edge cases;
- safer recovery behavior;
- better noVNC/proxy compatibility;
- tests or validation scripts that do not require publishing private device data.

## Ground rules

- Do not submit real VNC passwords, Tailscale IPs, device serial numbers, private logs, APK caches, or generated binaries.
- Use placeholders such as `<ANDROID_SERIAL>` and `<ANDROID_TAILSCALE_IP>` in examples.
- Keep remote-control features scoped to authorized self-owned/administered devices.
- Do not add instructions for evading workplace/app rules, misrepresenting location/presence, or exposing ADB/VNC publicly.

## Before opening a PR

```bash
./test/self_test.sh
git status --ignored --short
```

Review [`OPEN_SOURCE_CHECKLIST.md`](OPEN_SOURCE_CHECKLIST.md) if your change touches docs, credentials, or release packaging.
