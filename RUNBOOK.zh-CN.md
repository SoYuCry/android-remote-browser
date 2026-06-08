# 安卓远程控制日常运维手册

本手册只覆盖两件事：

1. **手机不关机时，尽量让远控长期保持可用**；
2. **手机关机/重启/掉线后，如何手动把服务重新拉起来**。

当前远控入口：

```text
http://<ANDROID_TAILSCALE_IP>:6080/vnc.html?host=<ANDROID_TAILSCALE_IP>&port=6080&path=websockify&encrypt=0&autoconnect=true
```

当前架构：

```text
iPhone Safari
  -> Tailscale
  -> Android <ANDROID_TAILSCALE_IP>:6080
  -> android-novnc-proxy
  -> droidVNC-NG 127.0.0.1:5900
```

---

## 1. 不关机时：保持长期可用

### 1.1 安卓手机侧

长期运行时，安卓手机建议保持以下状态：

- 不手动清后台；
- 不手动关闭 Tailscale；
- 不手动关闭 droidVNC-NG；
- 不开启省电模式/超级省电模式；
- 尽量不要设置复杂锁屏密码；
- Tailscale 保持 `Connected`；
- droidVNC-NG 页面里：
  - `Screen Capturing = GRANTED`
  - `Input = GRANTED`
  - 底部按钮显示 `STOP`

注意：按钮显示 `STOP` 代表服务正在运行；如果点它，才会停止服务。

### 1.2 不长期接电版配置

如果不希望手机长期插电，使用省电但尽量常驻的配置：

```bash
./scripts/configure_battery_friendly_persistence.sh --serial <ANDROID_SERIAL> --screen-timeout 60000
```

它会：

- 允许屏幕正常熄灭；
- 将屏幕超时设置为 1 分钟；
- 关闭 `stay_on_while_plugged_in`；
- 把 Tailscale 加入 Doze 白名单；
- 把 droidVNC-NG 加入 Doze 白名单；
- 尽量把二者的 App Standby 降到最不受限状态；
- 放开后台/前台服务相关限制；
- 保持 droidVNC-NG 输入辅助功能开启；
- 恢复一次当前 `5900` / `6080` 服务。

执行成功后，预期状态：

```text
screen_off_timeout=60000
stay_on_while_plugged_in=0
user,com.tailscale.ipn,...
user,net.christianbeier.droidvnc_ng,...
LISTEN ... :5900
LISTEN ... :6080
```

这代表：屏幕可以熄灭省电，但 Tailscale/droidVNC 会尽量保持后台可用。

### 1.3 已经通过 ADB 做过的强保活加固

已经执行过：

```bash
./scripts/harden_remote_control.sh --serial <ANDROID_SERIAL> --port 5900 --scaling 0.6
```

它做了这些事：

- 充电/USB 供电时保持唤醒；
- 延长屏幕超时；
- 将 Tailscale 加入 Doze 白名单；
- 将 droidVNC-NG 加入 Doze 白名单；
- 尽量放开后台运行限制；
- 保持 droidVNC-NG 输入辅助功能开启；
- 将 droidVNC-NG 默认配置改为 `Start on Boot`；
- 恢复当前 droidVNC/noVNC 服务。

如果以后 Mac 能连上，可以重复执行这条命令做一次完整加固。但它偏“保活”，不如 `configure_battery_friendly_persistence.sh` 省电。

### 1.4 iPhone 侧

iPhone 侧长期使用时：

- Tailscale 必须是 `Connected`；
- 如果要用 Safari 远控，尽量不要同时打开 Shadowrocket/其他 VPN；
- 如果 Safari 页面卡住，先关闭旧标签页，再重新打开完整链接；
- noVNC 显示模式选择 `Local scale`。

---

## 2. 常见掉线类型和判断

### 2.1 能操作，但画面不刷新

典型原因：

```text
Input = GRANTED
Screen Capturing = DENIED
```

含义：

- 输入通道还在，所以你点/滑可能有效；
- 屏幕采集权限掉了，所以 iPhone 画面不更新。

恢复方法见第 3 节。

### 2.2 Safari 能打开页面，但 Connect 失败

可能原因：

- iPhone Tailscale 没连上；
- iPhone 被 Shadowrocket/其他 VPN 顶掉；
- 安卓 Tailscale 掉线；
- `6080` noVNC 代理被系统杀掉；
- droidVNC-NG 的 `5900` 服务停止。

恢复方法见第 3 节和第 4 节。

### 2.3 Safari 页面完全打不开

可能原因：

- iPhone Tailscale 未连接；
- 安卓 Tailscale 未连接；
- 安卓没电/关机；
- `6080` 代理不在。

---

## 3. 没有 Mac 时：安卓本机手动恢复

这是最重要的手动流程。

### 3.1 恢复 Tailscale

在安卓手机上：

1. 打开 `Tailscale`；
2. 确认左上角 VPN 开关打开；
3. 状态应为 `Connected`；
4. 能看到本机地址 `<ANDROID_TAILSCALE_IP>`。

如果 Tailscale 没连上，先点开关重新连接。

### 3.2 恢复 droidVNC-NG

在安卓手机上：

1. 打开 `droidVNC-NG`；
2. 进入 `Admin Panel`；
3. 查看 `Permissions Dashboard`；
4. 确认：

```text
Screen Capturing = GRANTED
Input = GRANTED
```

5. 如果 `Screen Capturing = DENIED`，点页面底部 `START`；
6. 如果系统弹出屏幕录制/投屏授权，点允许；
7. 确认底部按钮变成 `STOP`。

### 3.3 回到 iPhone 连接

在 iPhone 上：

1. 打开 Tailscale，确认 `Connected`；
2. 关闭 Shadowrocket/其他 VPN；
3. Safari 关闭旧页面；
4. 重新打开：

```text
http://<ANDROID_TAILSCALE_IP>:6080/vnc.html?host=<ANDROID_TAILSCALE_IP>&port=6080&path=websockify&encrypt=0&autoconnect=true
```

5. 点 `Connect`；
6. 输入 VNC 密码。

---

## 4. 有 Mac/ADB 时：一键恢复

如果 Mac 能连 USB，直接运行：

```bash
./scripts/recover_droidvnc_session.sh --serial <ANDROID_SERIAL> --port 5900
```

成功输出应类似：

```text
Screen Capturing: GRANTED
Input: GRANTED
Toggle: STOP
LISTEN ... :5900
LISTEN ... :6080
```

如果要重新做长期运行加固：

```bash
./scripts/harden_remote_control.sh --serial <ANDROID_SERIAL> --port 5900 --scaling 0.6
```

---

## 5. 关机/重启后的恢复手册

如果安卓手机没电关机、重启、系统更新后重启，按下面顺序做。

### 5.1 安卓本机操作

1. 给安卓接电并开机；
2. 解锁手机；
3. 打开 `Tailscale`；
4. 确认 Tailscale 是 `Connected`；
5. 打开 `droidVNC-NG`；
6. 点 `START`；
7. 如果弹出屏幕录制/投屏授权，点允许；
8. 确认：

```text
Screen Capturing = GRANTED
Input = GRANTED
底部按钮 = STOP
```

### 5.2 如果 Safari 仍然连不上

如果重启后 Safari 的 `6080` 链接打不开或 Connect 失败，说明 `android-novnc-proxy` 可能没有自动恢复。

这时有两种选择：

#### 方案 A：有 Mac/ADB

运行：

```bash
./scripts/recover_droidvnc_session.sh --serial <ANDROID_SERIAL> --port 5900
```

#### 方案 B：没有 Mac

只能先保证：

- Tailscale Connected；
- droidVNC-NG 正在运行；
- `Screen Capturing = GRANTED`；
- `Input = GRANTED`。

如果 `6080` 代理已经死掉，而手机没有额外安装自启动代理服务，那么仅靠安卓本机界面无法重新启动这个自定义 `6080` 代理。

要彻底解决“无 Mac 重启 6080 代理”，需要后续再做一个安卓端常驻服务，推荐路线：

- Termux + Termux:Boot；
- 或自制一个 Android 前台服务 App。

---

## 6. 哪些能保证，哪些不能保证

可以尽量保证：

- 手机不断电时，Tailscale/droidVNC 尽量常驻；
- droidVNC-NG 被系统轻微限制的概率降低；
- 充电时尽量不休眠；
- 有 Mac/ADB 时，一条命令恢复。

不能绝对保证：

- Android 永久不收回屏幕采集权限；
- 手机关机重启后无需任何确认就恢复画面采集；
- ADB 启动的 `6080` 代理在系统重启后自动回来。

因此最稳的日常策略是：

```text
安卓不要开超级省电
Tailscale 保持 Connected
droidVNC-NG 保持 STOP/运行状态
Screen Capturing 保持 GRANTED
iPhone 只开 Tailscale，不同时开其他 VPN
```
