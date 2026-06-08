# 快速启动：iPhone 通过 Tailscale 控制安卓

## 1. 一次性准备

### Android

1. 安装并登录 Tailscale。
2. 安装 droidVNC-NG。
3. 开启 USB 调试，连接 Mac 并授权 ADB。
4. droidVNC-NG 里确认：
   - `Screen Capturing = GRANTED`
   - `Input = GRANTED`
   - 底部按钮显示 `STOP`。

### iPhone

1. 安装并登录 Tailscale。
2. 确认能看到安卓设备 `<ANDROID_TAILSCALE_IP>`。
3. 关闭 Shadowrocket/其他会顶掉 Tailscale 的 VPN。

## 2. Mac 上初始化/恢复

进入目录：

```bash
cd /path/to/android-remote-control
```

安装 droidVNC-NG，如果手机还没装：

```bash
./scripts/install_droidvnc_ng.sh --serial <ANDROID_SERIAL>
```

配置并启动 droidVNC-NG：

```bash
./scripts/configure_droidvnc.sh --serial <ANDROID_SERIAL> --port 5900 --scaling 0.6 --start-on-boot
```

启动安卓端 noVNC 代理：

```bash
./scripts/start_android_novnc_proxy.sh --serial <ANDROID_SERIAL>
```

应用省电常驻配置：

```bash
./scripts/configure_battery_friendly_persistence.sh --serial <ANDROID_SERIAL> --screen-timeout 60000
```

## 3. iPhone 连接

Safari 打开：

```text
http://<ANDROID_TAILSCALE_IP>:6080/vnc.html?host=<ANDROID_TAILSCALE_IP>&port=6080&path=websockify&encrypt=0&autoconnect=true
```

点 `Connect`，输入 `.droidvnc.env` 里的 VNC 密码。

noVNC 显示模式推荐：

```text
Local scale
```

## 4. 日常恢复

隔夜后如果 Connect 失败、画面不刷新、或者只停在旧画面，有 Mac/ADB 时运行：

```bash
./scripts/recover_droidvnc_session.sh --serial <ANDROID_SERIAL> --port 5900
```

没有 Mac 时，在安卓本机操作：

1. 打开 Tailscale，确认 `Connected`；
2. 打开 droidVNC-NG；
3. 如果 `Screen Capturing = DENIED`，点 `START`；
4. 系统弹出屏幕采集授权时点允许；
5. 确认底部按钮显示 `STOP`；
6. 回 iPhone Safari 重新 Connect。

## 5. 重要限制

Android 的屏幕采集权限不是永久后台权限。手机重启、droidVNC 被系统回收、锁屏/省电策略触发后，`Screen Capturing` 可能需要重新点 `START / Allow`。
