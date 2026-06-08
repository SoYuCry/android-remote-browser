# Android Remote Browser

Use an iPhone browser to view and control your own Android phone over a private Tailscale network.

This project packages a working Android-side VNC/noVNC setup around **droidVNC-NG**, **Tailscale**, and a small Go WebSocket proxy so the controller can simply be **Safari on iPhone**.

```text
iPhone Safari
  -> Tailscale private network
  -> Android <ANDROID_TAILSCALE_IP>:6080
  -> android-novnc-proxy /websockify
  -> droidVNC-NG 127.0.0.1:5900
  -> Android screen + touch input
```

Example noVNC URL:

```text
http://<ANDROID_TAILSCALE_IP>:6080/vnc.html?host=<ANDROID_TAILSCALE_IP>&port=6080&path=websockify&encrypt=0&autoconnect=true
```

> Use this only for devices and actions you own or are authorized to control. Do not use it to misrepresent location/presence, bypass workplace or app rules, or perform unauthorized actions.

## Why this exists

Remote Android control from iPhone sounds simple, but in practice several pieces do not line up cleanly:

- many iOS VNC clients are picky about Android VNC servers;
- droidVNC-NG's built-in browser page may not expose a Safari-compatible WebSocket endpoint in every setup;
- exposing VNC/ADB directly to the internet is unsafe;
- Android screen-capture permissions can expire after sleep, reboot, or process death.

This repo provides a repeatable, private-network workflow that was tested end-to-end with:

- iPhone Safari as the controller;
- Tailscale as the private network;
- droidVNC-NG as the Android VNC server;
- a lightweight `android-novnc-proxy` serving noVNC on port `6080`.

## Quick start

See [`QUICKSTART.zh-CN.md`](QUICKSTART.zh-CN.md) for the short Chinese setup guide.

Minimal flow:

```bash
# 1. Install droidVNC-NG onto an authorized Android device
./scripts/install_droidvnc_ng.sh --serial <ANDROID_SERIAL>

# 2. Configure and start droidVNC-NG on Android port 5900
./scripts/configure_droidvnc.sh \
  --serial <ANDROID_SERIAL> \
  --port 5900 \
  --scaling 0.6 \
  --start-on-boot

# 3. Build/deploy/start the Android noVNC proxy on port 6080
./scripts/start_android_novnc_proxy.sh --serial <ANDROID_SERIAL>

# 4. Prefer battery-friendly persistence: screen may sleep after 60s
./scripts/configure_battery_friendly_persistence.sh \
  --serial <ANDROID_SERIAL> \
  --screen-timeout 60000
```

Then open the printed Safari URL on your iPhone while both devices are connected to the same Tailscale tailnet.

## Daily recovery

If the page opens but the connection fails, or touch input works while the image is stale, run:

```bash
./scripts/recover_droidvnc_session.sh --serial <ANDROID_SERIAL> --port 5900
```

If you do not have ADB/Mac access, recover directly on the Android phone:

1. Open Tailscale and confirm it is `Connected`.
2. Open droidVNC-NG.
3. Confirm `Input = GRANTED`.
4. If `Screen Capturing = DENIED`, tap `START` and approve the Android screen-capture prompt.
5. Confirm the main droidVNC-NG button shows `STOP` — that means the server is running.
6. Reopen the noVNC URL on iPhone Safari.

## Important limitation

Android screen capture is controlled by the system MediaProjection permission. On non-root, non-device-owner devices, this permission may need user confirmation again after reboot, sleep, process death, or other system events.

This project can keep Tailscale/droidVNC as persistent as Android reasonably allows, but it cannot guarantee permanent unattended screen-capture permission on every phone/OEM build.

## Documentation

- [`QUICKSTART.zh-CN.md`](QUICKSTART.zh-CN.md) — shortest setup and recovery path.
- [`GUIDE.zh-CN.md`](GUIDE.zh-CN.md) — full implementation guide.
- [`RUNBOOK.zh-CN.md`](RUNBOOK.zh-CN.md) — day-to-day operations and troubleshooting.
- [`FILES.md`](FILES.md) — what each script/file does.
- [`ACCEPTANCE.md`](ACCEPTANCE.md) — verification checklist.
- [`SECURITY.md`](SECURITY.md) — security notes for public/private use.

## Scripts

Core scripts:

- `scripts/install_droidvnc_ng.sh` — install droidVNC-NG APK through ADB.
- `scripts/configure_droidvnc.sh` — seed droidVNC settings, password, port, and start the VNC service.
- `scripts/start_android_novnc_proxy.sh` — build/deploy/start the Go noVNC WebSocket proxy on Android.
- `scripts/configure_battery_friendly_persistence.sh` — allow screen sleep while relaxing Android background restrictions for Tailscale/droidVNC.
- `scripts/recover_droidvnc_session.sh` — recover after overnight sleep, stale screen capture, or proxy failure.
- `scripts/check_android_tailscale.sh` / `scripts/check_droidvnc.sh` — inspect runtime state.

## Local secrets and generated files

Do not publish these:

- `.droidvnc.env`
- `.secrets/`
- `downloads/`
- `.omx/`
- generated binary `tools/android-novnc-proxy/android-novnc-proxy`

They are ignored by `.gitignore`. Use [`examples/droidvnc.env.example`](examples/droidvnc.env.example) as the public template.

## License

MIT. See [`LICENSE`](LICENSE).
