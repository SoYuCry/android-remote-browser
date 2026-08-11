# Android Remote Browser Companion

Companion 是 Android Remote Browser 的 Android 端守护 App。它把原来需要 ADB 启动的 `6080` noVNC browser proxy 做成 Android 前台服务，目标是解决手机没电关机/重启后 Safari 入口无法自动恢复的问题。

## 功能范围

第一版只做远程控制链路守护：

- 开机自启前台服务；
- 在 Android 本机监听 `:6080`；
- 内置 noVNC 静态页面；
- WebSocket `/websockify` 转发到 droidVNC-NG `127.0.0.1:5900`；
- 首页显示 Tailscale / droidVNC / proxy 状态；
- 一键重启 proxy；
- 一键复制 Safari URL；
- 一键打开 Tailscale、droidVNC-NG、无障碍设置、电池优化设置。

不包含第三方 App 定时打开、自动点击、业务 App 自动化等能力。

## 架构

```text
iPhone Safari
  -> Tailscale
  -> Companion App :6080
  -> /websockify
  -> droidVNC-NG 127.0.0.1:5900
```

## 构建

先准备 noVNC assets：

```bash
cd /Users/liuc/Documents/Projects/Android
./scripts/prepare_companion_assets.sh
```

然后用 Android Studio 打开 `android-companion/`，执行 `assembleDebug`。

如果本机安装了 JDK + Gradle，也可以：

```bash
./scripts/build_companion_app.sh
```

安装到已授权 USB Debugging 的 Android：

```bash
./scripts/install_companion_app.sh --serial <ANDROID_SERIAL>
```

## 安装后的设置

1. 打开 Companion App，允许通知权限。
2. 如果电池优化状态不是 `IGNORED`，点 `Open Battery Optimization Settings` 并允许忽略电池优化。
3. 确认 droidVNC-NG 正在运行，`127.0.0.1:5900` 显示 `REACHABLE`。
4. 确认 Tailscale 显示 `100.x.x.x` IP。
5. 复制 Safari URL，在 iPhone Safari 打开。

## 第三方资源

Companion App 使用从 droidVNC-NG APK 提取的 noVNC 静态资源。重新分发 APK 或源码包时，请保留上游许可说明。见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。

## 重启后的预期

手机重启后：

- Tailscale 由 Tailscale App 自己恢复；
- droidVNC-NG 由 droidVNC-NG 自己恢复；
- Companion 由 BootReceiver 自动恢复 `6080` proxy。

如果 Safari 页面能打开但黑屏/不可控，问题通常在 droidVNC-NG 的 Screen Capture 或 Input 权限，不在 Companion。
