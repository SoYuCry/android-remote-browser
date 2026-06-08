# Android Remote Browser

> 中文主页：[`README.md`](README.md)


<p align="center">
  <strong>Control your own Android phone from iPhone Safari over Tailscale.</strong>
</p>

<p align="center">
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-blue.svg"></a>
  <img alt="Platform: Android" src="https://img.shields.io/badge/Android-droidVNC--NG-3DDC84.svg">
  <img alt="Client: iPhone Safari" src="https://img.shields.io/badge/Client-iPhone%20Safari-black.svg">
  <img alt="Network: Tailscale" src="https://img.shields.io/badge/Network-Tailscale-6f42c1.svg">
</p>

Android Remote Browser is a small, practical toolkit for controlling an Android phone from an iPhone browser through a private Tailscale network. It combines **droidVNC-NG**, **noVNC**, and a lightweight Go WebSocket proxy that runs directly on Android.

```text
iPhone Safari
  -> Tailscale private network
  -> Android <ANDROID_TAILSCALE_IP>:6080
  -> android-novnc-proxy /websockify
  -> droidVNC-NG 127.0.0.1:5900
  -> Android screen + touch input
```

> **Safety boundary**: use this only for devices and actions you own or are explicitly authorized to control. Do not use it to misrepresent location/presence, bypass workplace or app rules, or perform unauthorized actions.

---

## What you get

- Browser-based Android remote control from **iPhone Safari**.
- Private-network access via **Tailscale** instead of exposing VNC/ADB to the internet.
- A tiny Android-side **noVNC WebSocket proxy** for Safari-compatible browser control.
- Scripts for installation, recovery, battery-friendly persistence, and diagnostics.
- Chinese quickstart, full guide, and operations runbook based on a real end-to-end setup.

## What you need

| Side | Requirement |
| --- | --- |
| Android | droidVNC-NG, Tailscale, USB debugging for initial setup |
| iPhone | Tailscale, Safari |
| Mac/Linux host | `adb`, `python3`, `go`, USB access for setup/recovery |

Daily iPhone control does **not** require the setup computer to stay online. The computer is mainly used to install/configure Android services and to recover the proxy if Android reboots or kills it.

## Quick start

See [`QUICKSTART.zh-CN.md`](QUICKSTART.zh-CN.md) for the shortest Chinese setup path.

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

The proxy script prints the browser URL. It has this shape:

```text
http://<ANDROID_TAILSCALE_IP>:6080/vnc.html?host=<ANDROID_TAILSCALE_IP>&port=6080&path=websockify&encrypt=0&autoconnect=true
```

Open that URL in iPhone Safari while both devices are connected to the same Tailscale tailnet.

## How it works

1. **droidVNC-NG** captures and controls the Android screen through VNC on `127.0.0.1:5900`.
2. **android-novnc-proxy** serves noVNC static assets and forwards `/websockify` WebSocket traffic to the local VNC server.
3. **Tailscale** gives the Android phone a private `100.x.y.z` address reachable from your iPhone.
4. **Safari** loads noVNC from `http://<ANDROID_TAILSCALE_IP>:6080/` and sends touch/mouse input back to Android.

For more detail, see [`docs/architecture.md`](docs/architecture.md).

## Daily operations

If the page opens but connection fails, or touch input works while the image is stale:

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

See [`RUNBOOK.zh-CN.md`](RUNBOOK.zh-CN.md) and [`docs/troubleshooting.md`](docs/troubleshooting.md) for the full recovery playbook.

## Important limitation

Android screen capture is controlled by the system MediaProjection permission. On non-root, non-device-owner devices, this permission may need user confirmation again after reboot, sleep, process death, or OEM battery-management events.

This project can keep Tailscale/droidVNC as persistent as Android reasonably allows, but it cannot guarantee permanent unattended screen-capture permission on every phone/OEM build.

## Repository map

| Path | Purpose |
| --- | --- |
| `scripts/` | Setup, recovery, diagnostics, and persistence scripts |
| `tools/android-novnc-proxy/` | Go WebSocket-to-VNC proxy source |
| `docs/` | Architecture, troubleshooting, development notes |
| `QUICKSTART.zh-CN.md` | Short Chinese setup guide |
| `GUIDE.zh-CN.md` | Full Chinese implementation guide |
| `RUNBOOK.zh-CN.md` | Daily operations runbook |
| `FILES.md` | Detailed file inventory |
| `ACCEPTANCE.md` | Verification checklist |

## Core scripts

- `scripts/install_droidvnc_ng.sh` — install droidVNC-NG APK through ADB.
- `scripts/configure_droidvnc.sh` — seed droidVNC settings, password, port, and start the VNC service.
- `scripts/start_android_novnc_proxy.sh` — build/deploy/start the Go noVNC WebSocket proxy on Android.
- `scripts/configure_battery_friendly_persistence.sh` — allow screen sleep while relaxing Android background restrictions for Tailscale/droidVNC.
- `scripts/recover_droidvnc_session.sh` — recover after overnight sleep, stale screen capture, or proxy failure.
- `scripts/check_android_tailscale.sh` / `scripts/check_droidvnc.sh` — inspect runtime state.

## Security notes

Do not publish or commit:

- `.droidvnc.env`
- `.secrets/`
- `downloads/`
- `.omx/`
- generated binary `tools/android-novnc-proxy/android-novnc-proxy`

They are ignored by `.gitignore`. Use [`examples/droidvnc.env.example`](examples/droidvnc.env.example) as the public template.

Never expose ADB (`5555`), VNC (`5900`), or noVNC (`6080`) directly to the public internet. Use a private network such as Tailscale/ZeroTier and rotate VNC credentials if they were ever shared.

## Contributing

Contributions are welcome if they improve clarity, portability, safety, or recovery reliability. Start with [`CONTRIBUTING.md`](CONTRIBUTING.md) and [`docs/development.md`](docs/development.md).

## License

MIT. See [`LICENSE`](LICENSE).
