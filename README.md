# iPhone 通过 Tailscale 远程控制安卓手机

这套目录记录并自动化了当前已经实测成功的方案：

```text
iPhone Safari
  -> Tailscale 私有网络
  -> Android <ANDROID_TAILSCALE_IP>:6080
  -> android-novnc-proxy
  -> droidVNC-NG 127.0.0.1:5900
  -> Android 触控输入
```

当前成功入口：

```text
http://<ANDROID_TAILSCALE_IP>:6080/vnc.html?host=<ANDROID_TAILSCALE_IP>&port=6080&path=websockify&encrypt=0&autoconnect=true
```

> 仅用于你有权控制的自有设备、测试、维护、辅助操作。不要用于虚假定位、虚假到岗、规避单位/应用规则或任何未授权操作。

## 该看哪个文件

- [`QUICKSTART.zh-CN.md`](QUICKSTART.zh-CN.md)：最短安装/恢复流程。
- [`GUIDE.zh-CN.md`](GUIDE.zh-CN.md)：完整专业实施指南，可分享给别人。
- [`RUNBOOK.zh-CN.md`](RUNBOOK.zh-CN.md)：日常运维、隔夜、掉线、关机后恢复。
- [`FILES.md`](FILES.md)：本目录每个脚本/文件是干什么的。
- [`ACCEPTANCE.md`](ACCEPTANCE.md)：验收清单。

## 最常用命令

省电常驻配置，允许屏幕 1 分钟后正常熄灭：

```bash
./scripts/configure_battery_friendly_persistence.sh --serial <ANDROID_SERIAL> --screen-timeout 60000
```

隔夜/画面冻结/Connect 失败时，一键恢复：

```bash
./scripts/recover_droidvnc_session.sh --serial <ANDROID_SERIAL> --port 5900
```

重新部署 noVNC 代理：

```bash
./scripts/start_android_novnc_proxy.sh --serial <ANDROID_SERIAL>
```

检查状态：

```bash
./scripts/check_android_tailscale.sh --serial <ANDROID_SERIAL>
./scripts/check_droidvnc.sh --serial <ANDROID_SERIAL> --port 5900
```

## 当前必要组件

Mac/ADB 侧：

- `adb`
- `bash`
- `python3`
- `go`，用于按需编译 `tools/android-novnc-proxy/android-novnc-proxy`

Android 侧：

- Tailscale
- droidVNC-NG
- USB 调试授权，仅用于安装、配置、恢复；日常 iPhone 控制不依赖 USB。

iPhone 侧：

- Tailscale
- Safari

## 目录整理说明

当前主线只保留 iPhone + Tailscale + Android VNC/noVNC 方案。

早期探索过的 Mac USB/scrcpy、无线 ADB、Mac 远程桌面桥接方案已经移到：

```text
archive/legacy-usb-scrcpy/
```

这些不是当前教程的必要步骤，只作为历史参考保留。
