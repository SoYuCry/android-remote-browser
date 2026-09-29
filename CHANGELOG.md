# Changelog

User-visible changes are recorded here. Keep new work under **Unreleased**; move it to a dated release section when a release/tag is published. Companion APK version numbers below do not imply a GitHub release.

## Unreleased

### Added

- Companion 0.2 quick-action page: pair a browser once, wake the phone, open Feishu for five seconds after foreground confirmation, and return to the launcher. Preserve the original automatic screen timeout. ([#2](https://github.com/SoYuCry/android-remote-browser/pull/2))
- Single-use pairing codes, remembered device credentials, individual/global revocation, authenticated action requests, and duplicate/concurrent request protection.
- Accessibility-based foreground/launcher verification, bounded wake time, and explicit failure/interruption status.
- Windows build script and optional parallel installation on port 6081 when an existing APK's signing key is unavailable.
- Device, HTTP, browser-controller, and VNC-handshake regression checks.
- Add experimental Android Companion App that runs the noVNC `:6080` proxy as an Android foreground service with boot receiver and status dashboard.
- Add scripts to prepare noVNC assets, build the Companion APK, and install it through ADB.
- Public README and documentation polish.
- Open-source hygiene files: contributing guide, security notes, release checklist, and examples.
- Runtime scripts for droidVNC-NG, noVNC proxy, recovery, and battery-friendly persistence.

### Changed

- Companion bundles and gzip-compresses noVNC JavaScript and caches static resources using versioned URLs. HTML and API responses remain uncached.
- Companion's root URL opens the quick-action page; full screen control remains available at `/vnc.html` with its existing VNC authentication.
- Chinese and English READMEs explain both entry points, first-time pairing, screen behavior, parallel installation, and connection-speed expectations.

### Fixed

- Report VNC health correctly in the Companion dashboard (`828dbba`).

### Compatibility

- Quick actions require enabling the Companion accessibility service; secure lock screens require manual unlocking.
- The launch-and-return workflow has been tested on Android 15. Other OEM behavior and unattended reboot recovery are not yet verified.
- The new web build requires Node.js and the pinned npm dependencies; the legacy Go proxy is unchanged.
