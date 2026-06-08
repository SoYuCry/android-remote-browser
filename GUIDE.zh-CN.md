# iPhone 通过私有网络远程控制安卓手机：完整实施指南

> 本文档记录一套已经实测打通的方案：在安卓手机上运行 VNC 服务和 noVNC WebSocket 代理，iPhone 通过 Tailscale 私有网络访问 Safari 页面，从而直接远程查看并触控安卓手机。
>
> 适用场景：自有设备远程维护、测试、演示、无障碍控制、备用机管理等合法用途。请勿用于规避组织制度、冒充位置/身份、绕过考勤或违反第三方服务规则。

## 1. 最终架构

```text
 iPhone Safari
      │
      │  Tailscale 私有网络
      ▼
 Android / Seeker
      │
      ├─ Tailscale VPN IP: <ANDROID_TAILSCALE_IP>
      ├─ android-novnc-proxy: 6080
      │      └─ /websockify  WebSocket → TCP 转发
      └─ droidVNC-NG: 5900
             └─ Android 屏幕采集 + 触控输入
```

最终访问入口：

```text
http://<ANDROID_TAILSCALE_IP>:6080/vnc.html?host=<ANDROID_TAILSCALE_IP>&port=6080&path=websockify&encrypt=0&autoconnect=true
```

VNC 密码保存在本地 `.droidvnc.env`：

```bash
source .droidvnc.env && echo "$DROIDVNC_PASSWORD"
```

本次实测密码为：

```text
<VNC_PASSWORD>
```

> 分享给别人时建议重新生成密码，不要复用本文示例密码。

## 2. 为什么不是直接用 RealVNC 或 droidVNC 自带网页

本次排障中确认：

- `RealVNC Viewer` 在 iPhone 上即使使用 `<ANDROID_TAILSCALE_IP>::5900`，仍可能报：

  ```text
  the computer's IP address could not be contacted
  ```

- droidVNC-NG 自带的 `5800/vnc.html` 页面可以打开，但其默认 `websockify` WebSocket 入口不可用，点击 Connect 会失败。
- 自建 `android-novnc-proxy` 后，`6080/websockify` 可以正确把 noVNC 的 WebSocket 转发到本机 VNC `127.0.0.1:5900`，最终成功。

关键修复点：

1. 安卓端 droidVNC-NG 提供原始 VNC 服务：`127.0.0.1:5900`。
2. 自建 noVNC 静态页面 + WebSocket 代理：`0.0.0.0:6080`。
3. Safari 访问 6080，而不是 droidVNC 自带 5800。
4. WebSocket 响应头兼容 Safari/noVNC：只在客户端请求时返回 `Sec-WebSocket-Protocol: binary`。

## 3. 组件清单

### Mac 端

用于安装、配置和维护安卓端服务：

- `adb`：Android Debug Bridge
- `go`：用于编译 `android-novnc-proxy`
- 项目脚本：`scripts/*.sh`

### 安卓端

- Tailscale：建立私有网络
- droidVNC-NG：提供安卓屏幕 VNC 服务
- android-novnc-proxy：本项目编译的小型 WebSocket→VNC 代理

### iPhone 端

- Tailscale：加入同一个 tailnet
- Safari：打开 noVNC 页面

## 4. 一次性安装与配置流程

以下命令在 Mac 的项目目录执行：

```bash
cd /path/to/android-remote-control
```

### 4.1 安装 Mac 工具

```bash
./scripts/install_macos_tools.sh
```

如果要编译 noVNC 代理，需要 Go：

```bash
brew install go
```

### 4.2 连接安卓并授权 ADB

1. 安卓开启开发者选项。
2. 开启 USB 调试。
3. USB-C 连接 Mac。
4. 手机上允许 USB 调试授权。
5. 检查：

```bash
adb devices -l
```

应看到类似：

```text
<ANDROID_SERIAL> device ... model:Seeker
```

### 4.3 安装 droidVNC-NG

```bash
./scripts/install_droidvnc_ng.sh --serial <ANDROID_SERIAL>
```

### 4.4 配置 droidVNC-NG

当前推荐服务端缩放为 `0.6`，配合 iPhone Safari 的 `Local scale` 显示效果较好。

```bash
./scripts/configure_droidvnc.sh \
  --serial <ANDROID_SERIAL> \
  --port 5900 \
  --scaling 0.6
```

该脚本会：

- 预置 droidVNC-NG 默认配置；
- 设置 VNC 端口 `5900`；
- 设置随机/持久 VNC 密码；
- 尝试授予屏幕采集 app-op；
- 尝试开启 droidVNC-NG 辅助功能输入服务；
- 通过 Intent 启动 droidVNC-NG 服务。

如果安卓弹出屏幕录制/投屏/辅助功能提示，请在手机上允许。

### 4.5 安装并登录 Tailscale

安卓端安装 Tailscale：

```bash
# 如果已有可用 APK，可直接 adb install。
# 本次使用 F-Droid 通用 APK 安装成功。
adb -s <ANDROID_SERIAL> install -r downloads/tailscale-fdroid-1.96.4.apk
```

然后在安卓上打开 Tailscale，使用同一个账号登录，允许创建 VPN。

iPhone 端从 App Store 安装 Tailscale，登录同一个账号，并确认 VPN Connected。

检查安卓 Tailscale IP：

```bash
./scripts/check_android_tailscale.sh --serial <ANDROID_SERIAL>
```

本次实测：

```text
tun0 <ANDROID_TAILSCALE_IP>
```

### 4.6 编译并启动 noVNC 代理

本项目已经包含代理源码：

```text
tools/android-novnc-proxy/main.go
```

启动/部署：

```bash
./scripts/start_android_novnc_proxy.sh --serial <ANDROID_SERIAL>
```

该脚本会：

- 将 `android-novnc-proxy` 推送到 `/data/local/tmp/`；
- 将 noVNC 静态文件推送到 `/data/local/tmp/novnc`；
- 在安卓上启动 `:6080` 服务；
- 将 `6080/websockify` 转发到 `127.0.0.1:5900`。

成功时应看到：

```text
*:6080 LISTEN
android-novnc-proxy listening on :6080
```

## 5. iPhone 使用流程

### 5.1 网络要求

- iPhone Tailscale 必须 Connected。
- 安卓 Tailscale 必须 Connected。
- iPhone 不要同时启用会顶掉 Tailscale 的 Shadowrocket/其他全局 VPN。
- 如果必须使用代理，请先验证 Tailscale 可用，再考虑分流规则。

### 5.2 打开远控页面

在 iPhone Safari 打开：

```text
http://<ANDROID_TAILSCALE_IP>:6080/vnc.html?host=<ANDROID_TAILSCALE_IP>&port=6080&path=websockify&encrypt=0&autoconnect=true
```

输入 VNC 密码。

### 5.3 显示设置

进入 noVNC 后：

1. 打开 noVNC 设置。
2. 将缩放模式设为：

```text
Local scale
```

本次实测：`droidVNC scaling=0.6` + `noVNC Local scale` 效果较好。

如果画面太大：

```bash
./scripts/configure_droidvnc.sh --serial <ANDROID_SERIAL> --port 5900 --scaling 0.5
./scripts/start_android_novnc_proxy.sh --serial <ANDROID_SERIAL>
```

如果画面太糊，可改成 `0.7`。

## 6. 防锁屏与唤醒

建议保持安卓不要自动锁屏。通过 ADB 设置：

```bash
adb -s <ANDROID_SERIAL> shell svc power stayon true
adb -s <ANDROID_SERIAL> shell settings put system screen_off_timeout 1800000
```

含义：

- 连接电源/USB 时保持唤醒；
- 屏幕超时 30 分钟。

检查：

```bash
adb -s <ANDROID_SERIAL> shell settings get global stay_on_while_plugged_in
adb -s <ANDROID_SERIAL> shell settings get system screen_off_timeout
```

如果锁屏界面黑屏或不可见，建议关闭复杂锁屏密码，或确保远程前设备处于解锁状态。

### 6.1 长久在线/开机自启加固

如果不希望长期插电，优先使用省电常驻配置：

```bash
./scripts/configure_battery_friendly_persistence.sh --serial <ANDROID_SERIAL> --screen-timeout 60000
```

它会允许屏幕正常熄灭，同时把 `Tailscale` 和 `droidVNC-NG` 尽量放出 Doze/App Standby/后台限制。

对这台手机执行：

```bash
./scripts/harden_remote_control.sh --serial <ANDROID_SERIAL> --port 5900 --scaling 0.6
```

它会尽量完成以下事情：

- 充电/USB 供电时保持唤醒；
- 延长屏幕超时；
- 把 `droidVNC-NG` 和 `Tailscale` 加入 Android Doze 白名单；
- 放开二者的后台运行限制；
- 保持 droidVNC 输入辅助功能开启；
- 将 droidVNC 默认配置改为 `Start on Boot`；
- 重启/恢复 `5900` VNC 服务和 `6080` noVNC 代理。

检查白名单：

```bash
adb -s <ANDROID_SERIAL> shell cmd deviceidle whitelist | grep -E 'droidvnc|tailscale'
```

预期能看到：

```text
user,com.tailscale.ipn,...
user,net.christianbeier.droidvnc_ng,...
```

### 6.2 必须知道的系统限制

非 root、非系统应用、非设备所有者模式下，无法保证 100% 永久无确认地恢复屏幕采集权限。

原因是 Android 的屏幕采集使用 `MediaProjection`。Android 官方要求应用在每次新的屏幕采集会话前请求用户授权；会话停止后，旧授权不能无限复用。会话可能因为锁屏、用户停止、另一个投屏会话开始、应用进程被杀等原因结束。

因此可以做到：

- Tailscale 尽量常驻；
- droidVNC 尽量常驻；
- 手机充电时尽量不睡眠；
- 开机后尽量启动 droidVNC；
- 掉线后通过脚本/手动流程快速恢复。

但不能稳定承诺：

- 手机完全关机再开机后，屏幕采集一定无需人工确认；
- Android 杀掉 droidVNC 后，录屏权限一定能静默恢复；
- 锁屏/系统安全策略触发后，远端一定还能看到画面。

如果你需要接近“无人值守 24/7”，建议：

1. 安卓长期接电；
2. 关闭锁屏密码或使用最简单锁屏；
3. 系统设置中关闭 droidVNC-NG / Tailscale 的电池优化；
4. Tailscale 设置为 Always-on VPN；
5. 每次重启后人工确认一次 droidVNC 的屏幕采集授权。

## 7. 断 USB / 跨网络测试顺序

### 7.1 断 USB 测试

1. 确认 iPhone Safari 可控制安卓。
2. 拔掉 USB。
3. 在 iPhone 上继续滑动/点击。
4. 如果仍可控制，说明已经不依赖 USB。

### 7.2 iPhone 蜂窝网络测试

1. 安卓保持 Wi-Fi + Tailscale Connected。
2. iPhone 关闭 Wi-Fi，只用蜂窝数据。
3. 保持 iPhone Tailscale Connected。
4. 打开同一个 6080 链接。
5. 能控制则说明跨网络成功。

### 7.3 安卓换 Wi-Fi 测试

1. 安卓切换到另一个 Wi-Fi。
2. 确认 Tailscale 仍 Connected。
3. iPhone 继续访问 `<ANDROID_TAILSCALE_IP>:6080`。
4. Tailscale IP 通常保持不变。

## 8. 常用维护命令

检查安卓 Tailscale：

```bash
./scripts/check_android_tailscale.sh --serial <ANDROID_SERIAL>
```

检查 droidVNC：

```bash
./scripts/check_droidvnc.sh --serial <ANDROID_SERIAL> --port 5900
```

重启 noVNC 代理：

```bash
./scripts/start_android_novnc_proxy.sh --serial <ANDROID_SERIAL>
```

查看代理日志：

```bash
adb -s <ANDROID_SERIAL> shell 'cat /data/local/tmp/novnc-proxy.log | tail -n 100'
```

一键恢复隔夜后常见问题：

```bash
./scripts/recover_droidvnc_session.sh --serial <ANDROID_SERIAL> --port 5900
```

该脚本会：

- 唤醒安卓并延长亮屏时间；
- 打开 droidVNC-NG 管理页；
- 检查 `Screen Capturing` / `Input`；
- 如果 droidVNC 停止或屏幕采集权限掉了，自动点 `START`；
- 重启 `6080` noVNC 代理；
- 最后输出 `5900` / `6080` 监听状态。

长期运行加固：

```bash
./scripts/harden_remote_control.sh --serial <ANDROID_SERIAL> --port 5900 --scaling 0.6
```

不长期接电、省电常驻：

```bash
./scripts/configure_battery_friendly_persistence.sh --serial <ANDROID_SERIAL> --screen-timeout 60000
```

重启 droidVNC：

```bash
adb -s <ANDROID_SERIAL> shell am force-stop net.christianbeier.droidvnc_ng
./scripts/configure_droidvnc.sh --serial <ANDROID_SERIAL> --port 5900 --scaling 0.6
./scripts/start_android_novnc_proxy.sh --serial <ANDROID_SERIAL>
```

## 9. 故障排查

### Safari 页面打不开

检查：

```bash
./scripts/check_android_tailscale.sh --serial <ANDROID_SERIAL>
adb -s <ANDROID_SERIAL> shell 'ss -ltn | grep 6080'
```

确认 iPhone Tailscale Connected，并且没有被其他 VPN 顶掉。

### Safari 能打开，但 Connect 失败

先运行一键恢复：

```bash
./scripts/recover_droidvnc_session.sh --serial <ANDROID_SERIAL> --port 5900
```

如果脚本输出类似：

```text
Screen Capturing: GRANTED
Input: GRANTED
Toggle: STOP
LISTEN ... :5900
LISTEN ... :6080
```

说明安卓端服务正常。此时在 iPhone 上：

1. 确认 Tailscale 是 `Connected`；
2. 关闭 Safari 当前页面，重新打开完整 `6080` 链接；
3. 如果仍失败，切到 Tailscale 里点安卓设备详情，确认 `<ANDROID_TAILSCALE_IP>` 仍在线；
4. 注意 iPhone 同一时间通常只能有一个系统 VPN，Shadowrocket/其他 VPN 可能顶掉 Tailscale。

检查代理日志：

```bash
adb -s <ANDROID_SERIAL> shell 'cat /data/local/tmp/novnc-proxy.log | tail -n 100'
```

如果看到 iPhone IP 连入后马上断开，可能是代理版本旧。重新部署：

```bash
./scripts/start_android_novnc_proxy.sh --serial <ANDROID_SERIAL>
```

### 能输入密码/能操作，但画面停在锁屏或旧画面

这是 `droidVNC-NG` 的屏幕采集权限掉了。典型状态：

```text
Input: GRANTED
Screen Capturing: DENIED
```

原因是安卓的屏幕采集基于系统 MediaProjection 授权。该授权不等同于永久后台权限；当 droidVNC 服务重启、被系统回收、隔夜进入省电/锁屏状态、或者投屏会话结束时，屏幕采集会话可能失效。失效后输入服务还能工作，所以远端点击仍可能生效，但画面不会继续刷新。

恢复：

```bash
./scripts/recover_droidvnc_session.sh --serial <ANDROID_SERIAL> --port 5900
```

或者手动：

1. 打开安卓上的 `droidVNC-NG Admin Panel`；
2. 找到 `Permissions Dashboard`；
3. 如果 `Screen Capturing = DENIED`，点下方 `START`；
4. 如果系统弹出屏幕录制/投屏授权，点允许；
5. 确认 `Screen Capturing = GRANTED`，并且按钮显示 `STOP`。

降低复发概率：

- 安卓保持充电；
- 关闭 droidVNC-NG 和 Tailscale 的电池优化/后台限制；
- 保持 `stay_on_while_plugged_in=15`；
- 不要手动杀 droidVNC-NG、Tailscale 或清后台；
- 必要时开启 droidVNC-NG 的 `Start on Boot`，但屏幕采集授权仍可能需要人工确认。

### 没有 Mac/ADB 时的手动恢复

如果身边没有 Mac，不能跑脚本，就在安卓手机本机上操作：

1. 打开 `Tailscale`；
2. 确认左上角 VPN 开关是开启状态，状态是 `Connected`；
3. 打开 `droidVNC-NG`；
4. 进入 `Admin Panel`；
5. 看 `Permissions Dashboard`：
   - `Input` 应为 `GRANTED`；
   - `Screen Capturing` 必须为 `GRANTED`；
6. 如果 `Screen Capturing = DENIED`，点页面底部的 `START`；
7. 如果系统弹出“开始录制/投射屏幕/Start now/Allow”，点允许；
8. 确认按钮变成 `STOP`，这代表 droidVNC 服务正在运行；
9. 回到 iPhone，重新打开 `6080` 链接并 Connect。

如果 iPhone 页面能打开但 Connect 失败：

1. 先在 iPhone 的 Tailscale 里确认安卓设备在线；
2. 确认 iPhone 没有被 Shadowrocket/其他 VPN 顶掉 Tailscale；
3. Safari 关闭旧标签页，重新打开完整链接；
4. 如果安卓本机 droidVNC 显示 `STOP` 且 Tailscale 在线，通常就是 iPhone 端 VPN/页面缓存问题。

### 提示密码但没输入就断开

正常。VNC 认证有超时。重新打开页面并快速粘贴密码。

### 页面很大/只能看到一部分

noVNC 设置中选择：

```text
Local scale
```

或降低服务端缩放：

```bash
./scripts/configure_droidvnc.sh --serial <ANDROID_SERIAL> --port 5900 --scaling 0.5
./scripts/start_android_novnc_proxy.sh --serial <ANDROID_SERIAL>
```

### 安卓提示 slow fallback screen capture mode

如果当前可用，建议先选择：

```text
Cancel / Not now
```

避免切换模式导致服务断开。需要性能优化时再处理。

## 10. 安全建议

- 不要把 `5900`、`5800`、`6080` 暴露到公网。
- 只通过 Tailscale/ZeroTier 这类私有网络访问。
- VNC 密码不要提交到公开仓库。
- `.droidvnc.env` 应保持本地私有。
- 不用时可以停止服务：

```bash
adb -s <ANDROID_SERIAL> shell am force-stop net.christianbeier.droidvnc_ng
adb -s <ANDROID_SERIAL> shell 'pidof android-novnc-proxy | xargs -r kill'
```

## 11. 当前实测版本

- Android 设备：Solana Mobile Seeker
- Android：15
- 安卓物理分辨率：`1200x2670`
- Tailscale Android：`1.96.4`
- droidVNC-NG：F-Droid versionCode `59`
- noVNC 代理：本项目 `tools/android-novnc-proxy`
- iPhone：iPhone 14 Pro，Safari
- 成功入口：`http://<ANDROID_TAILSCALE_IP>:6080/vnc.html?...`
