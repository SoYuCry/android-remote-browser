#!/usr/bin/env bash
set -euo pipefail

src_apk="downloads/droidvnc-ng.apk"
out_dir="android-companion/app/src/main/assets/novnc"
tmp_dir="/tmp/android-remote-browser-companion-assets"

if [[ ! -f "$src_apk" ]]; then
  cat >&2 <<MSG
Missing $src_apk.
Run ./scripts/install_droidvnc_ng.sh first, or place droidVNC-NG APK at $src_apk.
MSG
  exit 1
fi

rm -rf "$tmp_dir"
mkdir -p "$tmp_dir" "$out_dir"
/usr/bin/unzip -oq "$src_apk" -d "$tmp_dir"
if [[ ! -d "$tmp_dir/assets/novnc" ]]; then
  echo "droidVNC-NG APK does not contain assets/novnc" >&2
  exit 1
fi
rm -rf "$out_dir"
mkdir -p "$out_dir"
/usr/bin/ditto "$tmp_dir/assets/novnc" "$out_dir"
cat > "$out_dir/README.assets.md" <<'MSG'
These noVNC static assets are extracted from droidVNC-NG APK by scripts/prepare_companion_assets.sh.
Keep upstream license requirements in mind when redistributing APKs or source bundles.
MSG

echo "Prepared noVNC assets in $out_dir"
/usr/bin/find "$out_dir" -maxdepth 2 -type f | /usr/bin/wc -l | /usr/bin/tr -d ' ' | /usr/bin/awk '{print "Files: "$1}'
