# Open Source Release Checklist

Before publishing:

- [ ] Run `./test/self_test.sh`.
- [ ] Confirm `git status --ignored --short` does not show secrets staged.
- [ ] Confirm `.droidvnc.env`, `.secrets/`, `downloads/`, `.omx/`, and `archive/` are ignored or absent from the release.
- [ ] Rotate the VNC password if it was ever pasted into chat/logs/docs: `./scripts/configure_droidvnc.sh --serial <ANDROID_SERIAL> --port 5900 --scaling 0.6 --rotate-credentials`.
- [ ] Replace examples with your own `<ANDROID_SERIAL>` and `<ANDROID_TAILSCALE_IP>` only in private notes, not public docs.
- [ ] Review `LICENSE` and change it if MIT is not the intended license.
