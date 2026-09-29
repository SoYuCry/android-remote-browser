# Android Remote Browser

**极其推荐：用数据线连接安卓手机，开启 USB Debugging，然后直接让 AI Agent 根据本教程完成配置、检查与日常恢复操作。** 这样最省心，也最适合不熟悉 ADB、Tailscale、VNC/noVNC 的用户。

> English version: [`README.en.md`](README.en.md)

<p align="center">
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-blue.svg"></a>
  <img alt="Android" src="https://img.shields.io/badge/Android-droidVNC--NG-3DDC84.svg">
  <img alt="Client" src="https://img.shields.io/badge/Client-iPhone%20Safari-black.svg">
  <img alt="Network" src="https://img.shields.io/badge/Network-Tailscale-6f42c1.svg">
</p>

Android Remote Browser 是一套让 **iPhone Safari 通过 Tailscale 私有网络操作自有 Android 手机** 的开源工具与教程。Android Companion 提供快捷操作和完整远控两个入口，也保留了原来的 Go 代理部署方式。日常使用无需电脑保持在线或 USB 常接。

## 选择使用方式

| 需求 | 入口 | 使用方式 |
| --- | --- | --- |
| 打开飞书一下，然后返回桌面 | `/quick` | 首次配对并记住浏览器，以后一键执行 |
| 查看手机画面、自由点击和滑动 | `/vnc.html` | 连接 droidVNC-NG，使用原来的 VNC 密码 |

快捷操作流程：**唤醒手机 → 打开飞书 → 确认进入前台后停留 5 秒 → 返回桌面**。不需要加载完整远控画面，不返回截图，保持原来的自动熄屏设置。

只使用快捷操作时，需要 Companion、Tailscale 和飞书；完整画面远控还需要 droidVNC-NG。

### 快捷操作上手

1. 安装并打开 Android Companion，两台手机连接同一 Tailscale 网络。
2. 安卓端开启“快捷操作：打开飞书并返回桌面”无障碍服务，并允许后台运行。
3. 点击“复制快捷操作网址”，在 iPhone Safari 中打开。
4. 安卓端点击“生成配对码”，在网页输入 6 位数字。以后使用同一浏览器直接点击“打开飞书一次”。

默认地址为 `http://<ANDROID_TAILSCALE_IP>:6080/quick`。如果旧 APK 签名不同，可安装独立的“手机快捷操作”并行版，地址改为 **6081** 端口，原有 6080 远控保留。

配对码 5 分钟有效且只能使用一次；网页可忘记当前设备，安卓端可撤销全部配对。清除浏览器数据或更换访问地址后需要重新配对。快捷操作配对与 VNC 密码相互独立。

安装、Windows 构建和权限配置见 [Companion 使用说明](android-companion/README.md)。已有版本的功能变化见 [CHANGELOG](CHANGELOG.md)。

## 完整远控链路

```text
iPhone Safari
  -> Tailscale 私有网络
  -> Android <ANDROID_TAILSCALE_IP>:6080
  -> Companion / Go proxy /websockify
  -> droidVNC-NG 127.0.0.1:5900
  -> Android 画面与触控输入
```

最终访问地址形如：

```text
http://<ANDROID_TAILSCALE_IP>:6080/vnc.html?host=<ANDROID_TAILSCALE_IP>&port=6080&path=websockify&encrypt=0&autoconnect=true
```

> **使用边界**：仅用于你拥有或被明确授权管理的设备，例如测试、维护、辅助操作、演示和自用远程管理。不要用于虚假定位、虚假到岗、绕过单位/应用规则或任何未授权操作。

## 效果演示

下面是配置成功后的实际效果：左侧 Android 运行 `droidVNC-NG`，右侧 iPhone Safari 通过 noVNC 看到同一台 Android，并可以远程点击/滑动。

<p align="center">
  <img src="docs/assets/demo-success.jpg" alt="iPhone Safari 通过 noVNC 远程控制 Android 的成功演示" width="720">
</p>

## 这个项目解决什么问题

从 iPhone 远程控制 Android 听起来简单，但实际会碰到几个坑：

- iOS 上的 VNC 客户端不一定能稳定连接 Android VNC 服务；
- droidVNC-NG 自带网页入口在某些环境下不够适配 Safari/noVNC；
- 直接把 VNC 或 ADB 暴露到公网非常危险；
- Android 的屏幕采集权限可能在锁屏、休眠、重启或进程被杀后失效；
- 普通用户很容易卡在 ADB、Tailscale、端口、权限、noVNC 参数这些细节上。

本项目提供一条已经实测走通的路线：

- 用 **Tailscale** 建立私有网络；
- 用 **droidVNC-NG** 在 Android 上提供 VNC 服务；
- 用 **android-novnc-proxy** 在 Android 上提供 Safari 可访问的 noVNC 页面；
- 用脚本完成安装、配置、检查、省电常驻和故障恢复。

## 准备条件

| 位置 | 需要准备 |
| --- | --- |
| Android | droidVNC-NG、Tailscale、USB Debugging 初始授权 |
| iPhone | Tailscale、Safari |
| Mac / Linux 配置机（Go 代理路线） | `adb`、`python3`、`go`、一根能传数据的 USB 线 |
| Companion 构建机（支持 Windows） | Node.js、JDK 17、Gradle 8.9、Android SDK、ADB |

日常从 iPhone 控制 Android 时，配置机不需要一直在线；它主要用于初始安装、配置和必要时恢复服务。

## 快速开始

完整中文步骤见：[`QUICKSTART.zh-CN.md`](QUICKSTART.zh-CN.md)。

以下是原有 Go 代理路线；使用快捷操作请按上方 Companion 流程安装，不必另外启动 Go 代理：

```bash
# 1. 安装 droidVNC-NG 到已授权 USB Debugging 的 Android 设备
./scripts/install_droidvnc_ng.sh --serial <ANDROID_SERIAL>

# 2. 配置并启动 Android 上的 VNC 服务，端口 5900
./scripts/configure_droidvnc.sh \
  --serial <ANDROID_SERIAL> \
  --port 5900 \
  --scaling 0.6 \
  --start-on-boot

# 3. 编译/部署/启动 Android 上的 noVNC 代理，端口 6080
./scripts/start_android_novnc_proxy.sh --serial <ANDROID_SERIAL>

# 4. 配置省电常驻：允许屏幕 60 秒后熄灭，同时尽量保活 Tailscale/droidVNC
./scripts/configure_battery_friendly_persistence.sh \
  --serial <ANDROID_SERIAL> \
  --screen-timeout 60000
```

`start_android_novnc_proxy.sh` 会自动打印 iPhone Safari 应该打开的 URL。

## 免手输 VNC 密码

noVNC 支持把 VNC 密码放进 URL 参数。确认连接成功后，可以把下面这种链接保存成 iPhone Safari 书签：

```text
http://<ANDROID_TAILSCALE_IP>:6080/vnc.html?host=<ANDROID_TAILSCALE_IP>&port=6080&path=websockify&encrypt=0&autoconnect=true&password=<VNC_PASSWORD>
```

这样打开书签后通常会自动连接，不需要每次手动输入 VNC 密码。

注意：

- 这个链接等同于包含密码，不要截图、公开分享或提交到仓库；
- 只建议保存在你自己的 iPhone / 私有密码管理器 / 私有备忘录里；
- 如果这个链接泄露，立刻轮换 VNC 密码：

```bash
./scripts/configure_droidvnc.sh \
  --serial <ANDROID_SERIAL> \
  --port 5900 \
  --scaling 0.6 \
  --rotate-credentials
```


## Android Companion App（实验）

如果希望手机没电关机/重启后不再依赖 Mac 手动恢复 `6080`，可以使用实验性的 Android Companion App。它把 noVNC browser proxy 做成 Android 前台服务，并提供 Tailscale / droidVNC / 端口状态面板。

源码在：[`android-companion/`](android-companion/)

常用命令：

```bash
./scripts/prepare_companion_assets.sh
./scripts/build_companion_app.sh
./scripts/install_companion_app.sh --serial <ANDROID_SERIAL>
```

0.2 版增加配对后手动触发的快捷操作：唤醒手机、打开飞书 5 秒并返回桌面，保留原有自动熄屏设置；不执行应用内点击或判断业务结果。完整远控同时提供脚本合并、压缩和版本化缓存。配置、签名不同时的并行安装方式见 [`android-companion/README.md`](android-companion/README.md)。

Windows 并行版构建与安装：

```powershell
./scripts/build_companion_app.ps1 -ParallelInstall
adb install -r android-companion/app/build/outputs/apk/debug/app-debug.apk
```

### 连接速度

快捷页约 6 KB，不加载 noVNC，也不等待 VNC 密码握手。Companion 的完整远控页面将主脚本合并并 gzip 压缩到约 53 KB，使用版本化静态资源缓存，减少重复下载。这些优化不影响旧 Go 代理，实际等待时间仍取决于 Tailscale 网络路径和手机状态。

## 日常恢复

如果隔夜后出现以下情况：

- Safari 页面能打开但 Connect 失败；
- 可以远程点击，但画面不刷新；
- 画面停在锁屏或旧画面；
- noVNC 代理被系统杀掉；

有 ADB/配置机时，直接运行：

```bash
./scripts/recover_droidvnc_session.sh --serial <ANDROID_SERIAL> --port 5900
```

没有配置机时，在 Android 本机手动恢复：

1. 打开 Tailscale，确认状态是 `Connected`；
2. 打开 droidVNC-NG；
3. 确认 `Input = GRANTED`；
4. 如果 `Screen Capturing = DENIED`，点 `START`；
5. 系统弹出屏幕采集授权时点允许；
6. 确认按钮变成 `STOP`，这代表服务正在运行；
7. 回到 iPhone Safari 重新打开 noVNC 链接。

详见：[`RUNBOOK.zh-CN.md`](RUNBOOK.zh-CN.md) 和 [`docs/troubleshooting.md`](docs/troubleshooting.md)。

## 必须知道的限制

- 快捷操作需要无障碍服务保持开启；无密码锁屏可尝试自动唤醒，有密码时需手动解锁。
- “已返回桌面”只确认应用打开与返回流程，不代表飞书内的业务结果。
- Android 15 真机已验证唤醒、返回桌面和自动熄屏；其他机型及无人值守重启恢复仍需验证。快捷操作失败时可从页面进入完整远控检查。

Android 的屏幕采集由系统 `MediaProjection` 权限控制。对于非 root、非设备所有者模式的普通手机，这个权限在重启、深度休眠、进程被杀或某些厂商省电策略触发后，可能需要用户再次确认。

也就是说，本项目可以尽量让 Tailscale 和 droidVNC-NG 常驻，但不能保证所有 Android 机型都能永久无人值守地保持 `Screen Capturing = GRANTED`。

## 项目结构

| 路径 | 说明 |
| --- | --- |
| `scripts/` | 安装、配置、恢复、检查、省电常驻脚本 |
| `tools/android-novnc-proxy/` | Go 写的 WebSocket-to-VNC 代理源码 |
| `android-companion/` | Android 快捷操作、设备配对、noVNC 前台服务 |
| `docs/` | 架构、排障、开发说明和演示图 |
| `QUICKSTART.zh-CN.md` | 最短中文启动流程 |
| `GUIDE.zh-CN.md` | 完整中文实施指南 |
| `RUNBOOK.zh-CN.md` | 日常运维手册 |
| `README.en.md` | 英文 README |
| `FILES.md` | 文件清单 |
| `ACCEPTANCE.md` | 验收清单 |
| `CHANGELOG.md` | 面向使用者的变更记录 |

## 核心脚本

- `scripts/install_droidvnc_ng.sh`：通过 ADB 安装 droidVNC-NG。
- `scripts/configure_droidvnc.sh`：写入 droidVNC 配置、密码、端口和缩放，并启动 VNC 服务。
- `scripts/start_android_novnc_proxy.sh`：编译/部署/启动 Android 上的 noVNC WebSocket 代理。
- `scripts/configure_battery_friendly_persistence.sh`：允许屏幕熄灭，同时尽量放开 Tailscale/droidVNC 的后台限制。
- `scripts/recover_droidvnc_session.sh`：隔夜、画面冻结、屏幕采集权限丢失或代理异常时恢复。
- `scripts/check_android_tailscale.sh` / `scripts/check_droidvnc.sh`：检查运行状态。

## 安全说明

不要提交或公开：

- `.droidvnc.env`
- `.secrets/`
- `downloads/`
- `.omx/`
- 生成的二进制文件 `tools/android-novnc-proxy/android-novnc-proxy`

这些都已经被 `.gitignore` 忽略。公开模板见：[`examples/droidvnc.env.example`](examples/droidvnc.env.example)。

不要把 ADB `5555`、VNC `5900` 或 noVNC `6080` 直接暴露到公网。请使用 Tailscale/ZeroTier 这类私有网络，并定期轮换 VNC 密码。

## 参与贡献

欢迎改进文档、兼容性、恢复流程和不同 Android 机型的经验。贡献前请看：[`CONTRIBUTING.md`](CONTRIBUTING.md) 与 [`docs/development.md`](docs/development.md)。

## 参考项目

[DailyTask](https://github.com/AndroidCoderPeng/DailyTask)

## License

MIT. See [`LICENSE`](LICENSE).
