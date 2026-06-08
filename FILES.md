# 文件清单

## 主教程文件

- `README.md`：中文项目主页和最常用命令。
- `README.en.md`：英文项目主页。
- `QUICKSTART.zh-CN.md`：最短可执行流程。
- `GUIDE.zh-CN.md`：完整实施指南，可分享。
- `RUNBOOK.zh-CN.md`：日常运维和故障恢复。
- `ACCEPTANCE.md`：验收标准。
- `FILES.md`：本文件。
- `CONTRIBUTING.md`：贡献说明和安全边界。
- `CHANGELOG.md`：版本变化记录。
- `SECURITY.md`：安全说明。
- `docs/architecture.md`：英文架构说明。
- `docs/troubleshooting.md`：英文排障说明。
- `docs/development.md`：开发和发布卫生说明。
- `docs/assets/demo-success.jpg`：配置成功演示图。
- `.github/ISSUE_TEMPLATE/`：GitHub issue 模板。
- `.github/pull_request_template.md`：PR 模板。

## 当前主线脚本

这些脚本属于当前 iPhone → Tailscale → Android noVNC/droidVNC 方案。

- `scripts/install_macos_tools.sh`：可选，给新 Mac 安装 `adb` 等基础工具；历史上也安装 `scrcpy`。
- `scripts/install_droidvnc_ng.sh`：通过 ADB 安装 droidVNC-NG APK。
- `scripts/configure_droidvnc.sh`：写入 droidVNC 配置、端口、密码、缩放，并启动 `5900` VNC 服务。
- `scripts/start_android_novnc_proxy.sh`：把 `android-novnc-proxy` 和 noVNC 静态资源推到安卓，并启动 `6080` 网页代理。
- `scripts/check_android_tailscale.sh`：检查安卓 Tailscale/tun0 状态和 `100.x` 地址。
- `scripts/check_droidvnc.sh`：检查 droidVNC 进程、IP、`5900/5800/6080` 监听和本地密码文件。
- `scripts/recover_droidvnc_session.sh`：隔夜/掉线/屏幕采集权限丢失时的一键恢复。
- `scripts/configure_battery_friendly_persistence.sh`：省电常驻配置；允许屏幕熄灭，同时尽量放开 Tailscale/droidVNC 后台限制。
- `scripts/harden_remote_control.sh`：强保活配置；偏向长久在线/接电场景，不是日常省电首选。
- `scripts/clear_android_lock_credential.sh`：清除自有安卓设备锁屏密码；需要当前 PIN/密码。

## 代理程序

- `tools/android-novnc-proxy/main.go`：自制 noVNC 静态文件服务 + WebSocket 到 VNC TCP 转发器。
- `tools/android-novnc-proxy/android-novnc-proxy`：运行时自动编译生成的 Android/arm64 可执行文件；不应提交。

## 本机私有/生成文件

这些不要发到公开仓库：

- `.droidvnc.env`：VNC 密码、端口、serial。
- `.secrets/droidvnc-defaults.json`：写给 droidVNC 的默认配置，含密码。
- `downloads/*.apk`：本次下载的安装包缓存。
- `.omx/`：本地运行状态/日志。

## 历史归档

- `archive/legacy-usb-scrcpy/`：早期 Mac USB/scrcpy、无线 ADB、Mac 远程桌面桥接方案。它们不是当前 iPhone 直连安卓教程的必要步骤，只保留作参考。
