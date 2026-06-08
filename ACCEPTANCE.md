# 验收清单

这套方案验收目标：iPhone 可以通过 Tailscale 私有网络，在 Safari/noVNC 中远程查看并控制自有安卓手机。

## 1. 文件结构验收

运行：

```bash
./test/self_test.sh
```

必须满足：

- 主文档存在：`README.md`、`QUICKSTART.zh-CN.md`、`GUIDE.zh-CN.md`、`RUNBOOK.zh-CN.md`、`FILES.md`；
- 当前主线脚本存在且可执行；
- shell 脚本语法检查通过；
- `tools/android-novnc-proxy/main.go` 存在；如果没有二进制，`start_android_novnc_proxy.sh` 能用 Go 自动编译。

## 2. 安卓端服务验收

ADB 连接安卓后运行：

```bash
./scripts/check_android_tailscale.sh --serial <ANDROID_SERIAL>
./scripts/check_droidvnc.sh --serial <ANDROID_SERIAL> --port 5900
```

必须满足：

- Android Tailscale 有 `<ANDROID_TAILSCALE_IP>`；
- droidVNC-NG 进程存在；
- `5900` 正在监听；
- `6080` 正在监听。

## 3. iPhone 远控验收

iPhone Safari 打开：

```text
http://<ANDROID_TAILSCALE_IP>:6080/vnc.html?host=<ANDROID_TAILSCALE_IP>&port=6080&path=websockify&encrypt=0&autoconnect=true
```

必须满足：

- 页面能打开；
- 点 `Connect` 后能输入 VNC 密码；
- 能看到安卓画面；
- iPhone 上滑动/点击，安卓实际响应；
- noVNC 设置 `Local scale` 后显示比例可用。

## 4. 省电常驻验收

运行：

```bash
./scripts/configure_battery_friendly_persistence.sh --serial <ANDROID_SERIAL> --screen-timeout 60000
```

必须满足：

- `screen_off_timeout=60000`；
- `stay_on_while_plugged_in=0`；
- Tailscale/droidVNC 在 Doze 白名单；
- 屏幕约 1 分钟无操作后会自动熄灭；
- 熄屏不应立即杀掉 Tailscale/droidVNC。

## 5. 恢复验收

运行：

```bash
./scripts/recover_droidvnc_session.sh --serial <ANDROID_SERIAL> --port 5900
```

成功输出应包含：

```text
LISTEN ... :5900
LISTEN ... :6080
```

如果安卓当前显示 droidVNC 管理页，理想输出还应包含：

```text
Screen Capturing: GRANTED
Input: GRANTED
Toggle: STOP
```

## 6. 已知不能绝对保证的点

Android 的 `Screen Capturing`/MediaProjection 授权可能在重启、系统回收、锁屏/省电策略后失效。失效后需要在安卓本机打开 droidVNC-NG，点 `START` 并允许屏幕采集。
