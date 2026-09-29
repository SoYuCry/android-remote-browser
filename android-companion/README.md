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

0.2 增加手动触发的快捷操作：唤醒手机 → 打开飞书 → 确认进入前台后停留 5 秒 → 返回并确认桌面。仅确认应用打开/桌面返回，不点击飞书内的按钮，不判断业务结果，不提供定时任务。

## 快捷操作与设备配对

1. 安卓端打开 Companion，允许通知，开启“快捷操作：打开飞书并返回桌面”无障碍服务，并允许忽略电池优化。
2. 点击“复制快捷操作网址”，在已连接同一 Tailscale 网络的 iPhone Safari 中打开 `http://<TAILSCALE_IP>:6080/quick`。
3. 安卓端点击“生成配对码”，在 Safari 输入 6 位配对码。每个码 5 分钟有效、仅用一次，最多尝试 5 次；最多配对 8 个浏览器。
4. Safari 保存设备凭证，以后直接点击“打开飞书一次”。正常浏览模式可跨页面关闭保存；清除网站数据或更换主机/IP/端口需要重新配对。
5. 网页“忘记此设备”会撤销当前凭证；安卓端可撤销所有设备。凭证不放入 URL，手机只保存 SHA-256 摘要。

执行期间最多持有 25 秒亮屏锁，结束或失败即释放，不修改屏幕超时/充电常亮设置。无密码锁屏可尝试自动唤醒和解除；有密码时报告需要手动解锁。无障碍仅检查前台应用包名，不读取聊天文本。重复请求带相同 ID 不重复执行，并发请求被拒绝；服务重启后不会自动重放上次任务。

快捷页不加载 noVNC；完整远控脚本打包成一个文件、支持 gzip，静态资源使用内容版本 URL 缓存，HTML 与 API 状态不缓存。VNC 仍使用原来的 VNC 密码，快捷操作配对不代替 VNC 认证。

## Windows 构建与并行安装

需要 Node.js、JDK 17、Gradle 8.9、Android SDK platform 35 / build-tools 34.0.0。在项目根目录运行：

```powershell
./scripts/build_companion_app.ps1 -Tests
# 旧 APK 签名密钥不可用时，不卸载旧版，改为并行安装：
./scripts/build_companion_app.ps1 -ParallelInstall -Tests
adb install -r android-companion/app/build/outputs/apk/debug/app-debug.apk
```

并行安装版名为“手机快捷操作”，包名加 `.quick`，使用 **6081** 端口。原有 6080 服务保留，新入口为 `http://<TAILSCALE_IP>:6081/quick`。要升级原包名必须使用原签名密钥。

测试 APK 是开发测试用，会重启目标进程；测试后可能需要重新开启无障碍服务：

```powershell
adb install -r android-companion/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w io.github.soyucy.androidremote.companion.quick.test/io.github.soyucy.androidremote.companion.QuickChecks
```

测试覆盖配对凭证持久化/撤销、尝试次数限制、未授权/跨站请求、静态资源缓存与 gzip。`test/quick_http_test.mjs` 可验证真实 HTTP 流程；加 `--live` 会实际打开一次飞书。凭证文件放在仓库外，不提交。

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

在项目根目录先运行 `npm ci`，然后用 Android Studio 打开 `android-companion/`，执行 `assembleDebug`。构建会自动通过 esbuild 生成网页资源。

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
