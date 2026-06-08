# Architecture

Android Remote Browser is intentionally small. It avoids public port forwarding and relies on a private tailnet for reachability.

## Data path

```text
Browser UI / touch input
  iPhone Safari
      |
      | HTTP + WebSocket over Tailscale
      v
  Android <ANDROID_TAILSCALE_IP>:6080
      |
      | /vnc.html, noVNC assets
      | /websockify WebSocket
      v
  android-novnc-proxy
      |
      | local TCP
      v
  droidVNC-NG 127.0.0.1:5900
      |
      v
  Android screen capture + accessibility/input service
```

## Components

### droidVNC-NG

Runs on Android and provides the raw VNC server. It owns:

- screen capture through Android MediaProjection;
- input through Android accessibility/input service;
- VNC authentication and port configuration.

### android-novnc-proxy

A small Go binary pushed to `/data/local/tmp/android-novnc-proxy` during setup.

It does two things:

1. serves noVNC static files on `:6080`;
2. converts noVNC WebSocket traffic at `/websockify` into TCP traffic to `127.0.0.1:5900`.

The proxy exists because a browser expects WebSocket transport, while droidVNC-NG exposes raw VNC TCP.

### Tailscale

Provides the private IP used by iPhone Safari. The project assumes the Android phone and iPhone are in the same tailnet.

## Why not expose VNC directly?

VNC and ADB should not be exposed to the public internet. They are powerful remote-control surfaces. Keep them behind a private network and strong credentials.

## Persistence model

The scripts make a best-effort attempt to keep Tailscale and droidVNC-NG out of aggressive Android idle modes. However, Android can still revoke or end MediaProjection sessions, especially after reboot, deep sleep, or OEM battery-management events.

That is why the runbook includes a manual recovery path for `Screen Capturing = DENIED`.
