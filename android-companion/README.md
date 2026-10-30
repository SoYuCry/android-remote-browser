# Android Remote Browser Companion

Companion 是 Android Remote Browser 的 Android 端守护 App。它把原来需要 ADB 启动的 `6080` noVNC browser proxy 做成 Android 前台服务，目标是解决手机没电关机/重启后 Safari 入口无法自动恢复的问题。

## 功能范围

第一版只做远程控制链路守护：

- 开机自启前台服务；
- 在 Android 本机监听 `:6080`；
- 内置 noVNC 静态页面；
- WebSocket `/websockify` 转发到 droidVNC-NG `127.0.0.1:5900`；
- 安卓 Companion 页面显示 Tailscale / droidVNC / proxy 状态；
- 一键重启 proxy；
- 一键复制 Safari URL；
- 一键打开 Tailscale、droidVNC-NG、无障碍设置、电池优化设置。

快捷操作：唤醒手机 → 打开飞书 → 确认进入前台后停留 5 秒 → 返回并确认桌面。仅确认应用打开/桌面返回，不点击飞书内的按钮，不判断业务结果。

## 工作日自动任务（0.3）

Android 的授权页面可能仍显示系统名称“闹钟和提醒”，用于允许按时启动任务。

在安卓端点击“开启工作日自动任务”，开启“定时执行权限”，并保持快捷操作无障碍服务开启。每天按 **北京时间（Asia/Shanghai）中国大陆工作日 08:35–08:50、17:50–18:05** 两个时段内各随机选择一个时间，分别自动执行一次上述流程；不需要 iPhone 在线，也不需要浏览器连接。

- 默认关闭，主动开启后生效；安卓 Companion 页面显示下次执行时间、最近结果和关闭按钮。
- 下次随机时间持久保存，重启或修改设备时区不会重新抽取；重启后恢复定时计划。升级双时段版本时会优先补上尚未错过的上午计划。
- 当天已到计划时间但仍在窗口内可恢复执行；超过对应时段的结束时间则跳过，不补执行。首次在窗口内开启时从剩余时间中随机选择。
- 每个时段最多自动尝试一次（每天最多两次），失败不自动重试；启动前短暂等待无障碍服务恢复不算执行。手动快捷操作不受日期限制。
- 按中国大陆官方日历排除放假日，周末调休上班日照常执行。当前内置 2026 年日历；后续年份需随版本更新，未收录的年份暂停自动任务并显示待更新，不退回按星期猜测。公司自行安排的假期不包含在内。修改系统时钟可能造成跳过，以免重复执行。
- 不改自动熄屏设置。关机、强制停止应用或撤销权限会影响调度；再次打开应用会恢复计划，但不会在窗口外补执行。

时间策略测试可在 Windows 运行 `./test/schedule_test.ps1`，覆盖工作日、随机区间、窗口内启用、周末、每个时段防重复、上午错过后不在下午补执行、时钟回拨与设备时区独立性。该测试不触发真实手机操作；实际定时唤醒需在设备上验证。

日历来源：[国务院办公厅关于2026年部分节假日安排的通知](https://www.gov.cn/zhengce/content/202511/content_7047090.htm)。日历内置，执行时无需联网；调休日期依据通知维护，不按固定公式推算。

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
