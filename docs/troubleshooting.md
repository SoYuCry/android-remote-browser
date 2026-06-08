# Troubleshooting

## Safari page does not open

Check:

```bash
./scripts/check_android_tailscale.sh --serial <ANDROID_SERIAL>
./scripts/check_droidvnc.sh --serial <ANDROID_SERIAL> --port 5900
```

Expected:

- Android has a Tailscale `100.x.y.z` address;
- `:6080` is listening;
- iPhone Tailscale is connected;
- no other iPhone VPN has replaced Tailscale.

## Page opens, but Connect fails

Restart the proxy and re-open the printed URL:

```bash
./scripts/start_android_novnc_proxy.sh --serial <ANDROID_SERIAL>
```

If that is not enough:

```bash
./scripts/recover_droidvnc_session.sh --serial <ANDROID_SERIAL> --port 5900
```

## Touch works, but the image is stale

This usually means Android input permission is alive, but screen capture is not.

On Android, open droidVNC-NG and check:

```text
Input = GRANTED
Screen Capturing = DENIED
```

Tap `START`, approve the screen-capture prompt, and confirm the main button changes to `STOP`.

## Screen is too large on iPhone

In noVNC settings, choose:

```text
Local scale
```

You can also lower droidVNC server-side scaling:

```bash
./scripts/configure_droidvnc.sh --serial <ANDROID_SERIAL> --port 5900 --scaling 0.5
./scripts/start_android_novnc_proxy.sh --serial <ANDROID_SERIAL>
```

## Battery drains too quickly

Use the battery-friendly profile:

```bash
./scripts/configure_battery_friendly_persistence.sh --serial <ANDROID_SERIAL> --screen-timeout 60000
```

This allows the Android screen to sleep after one minute while keeping Tailscale/droidVNC as persistent as Android reasonably allows.

## Need to rotate credentials

If the VNC password was ever pasted into logs, chat, or public docs:

```bash
./scripts/configure_droidvnc.sh \
  --serial <ANDROID_SERIAL> \
  --port 5900 \
  --scaling 0.6 \
  --rotate-credentials
```

Then restart the proxy and reconnect from iPhone.
