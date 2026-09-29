# Security Policy

This toolkit is intended for lawful control of Android devices you own or are authorized to administer.

Do not publish local runtime files such as:

- `.droidvnc.env`
- `.secrets/`
- `downloads/`
- `.omx/`

Never expose Android Debug Bridge (`adb tcpip 5555`), VNC (`5900`), or noVNC (`6080`) directly to the public internet. Use a private network such as Tailscale/ZeroTier and strong VNC credentials.

The same applies to the parallel-install port `6081` and the quick-action API. HTTP is intended to travel over your private Tailscale connection; direct LAN HTTP does not provide transport encryption. Restrict tailnet access to trusted devices. Quick actions require a locally generated, short-lived pairing code and then a random 256-bit bearer credential; only its SHA-256 digest is stored on Android. Credentials belong in browser storage / authorization headers, never URLs or logs. Browser requests require a custom header, exact same-origin checks when an Origin is supplied, and no cross-origin access is enabled.

Pairing authorizes only the fixed Feishu launch-and-return workflow and its status endpoint. It does not expose arbitrary commands, app names, coordinates, chat content, or VNC credentials. Revoke devices in Companion or use “forget this device” on the quick page. Clearing browser data alone does not revoke the corresponding Android-side credential.
