# Security Policy

This toolkit is intended for lawful control of Android devices you own or are authorized to administer.

Do not publish local runtime files such as:

- `.droidvnc.env`
- `.secrets/`
- `downloads/`
- `.omx/`

Never expose Android Debug Bridge (`adb tcpip 5555`), VNC (`5900`), or noVNC (`6080`) directly to the public internet. Use a private network such as Tailscale/ZeroTier and strong VNC credentials.
