---
title: "TuTu TOOLS"
summary: "MeowHub device control tools and local setup notes"
---

# TOOLS.md - Local Notes

Skills define _how_ tools work. This file is for _your_ specifics — the stuff that's unique to your setup.

## MeowHub Node Host

MeowHub 通过 OpenClaw Node Host 提供 28 个手机控制工具。Node Host 通过 WebSocket 连接到 OpenClaw Gateway，将设备命令注册为原生 node 工具。

### 调用方式

**截图** — 使用 `action: "camera_snap"`（UI 中原生显示图片）：

```
Tool: nodes
Parameters:
  action: "camera_snap"
  node: "MeowHub"
```

**其他命令** — 使用 `action: "invoke"`：

```
Tool: nodes
Parameters:
  action: "invoke"
  node: "MeowHub"
  invokeCommand: "device.tap"
  invokeParamsJson: "{\"x\":540,\"y\":960}"
```

**每次调用都必须设置 `node: "MeowHub"`。**

### 架构

```
OpenClaw AI (你)
    ↓ node.invoke (原生 WebSocket)
MeowHub Node Host (meowhub-node-host.js)
    ↓ HTTP
MeowHub Bridge Server (:18790)
    ↓ SocketCommandBridge
TutuSocketClient
    ↓ JSON-line TCP (:28200)
TutuGui Server
    ↓
截图 / 点击 / 滑动 / UI树 / 应用管理 / 短信 / 通话 / 设备信息
```

### 工具列表

所有工具通过原生 node 命令调用（无需 curl）：

| 命令 | 描述 | 关键参数 |
|------|------|---------|
| `device.screenshot` | 截图 | quality(1-100), maxSize |
| `device.tap` | 点击 | x, y |
| `device.long_click` | 长按 | x, y |
| `device.swipe` | 滑动 | startX, startY, endX, endY, duration(ms) |
| `device.scroll` | 滚动 | direction: up/down/left/right |
| `device.type` | 输入文字 | text |
| `device.press_key` | 按键 | key: home/back/power/recent/enter |
| `device.click_by_text` | 按文字点击 | text, index |
| `device.open_app` | 打开应用 | name(包名) |
| `device.ui_tree` | UI 树 | — |
| `device.find_element` | 查找元素 | text, resourceId, className |
| `device.read_ui_text` | 读取文字 | filter, exclude |
| `device.info` | 设备信息 | type: apps/battery/storage/network/all |
| `device.status` | 连接状态 | — |
| `device.shell` | Shell 命令 | command, timeout(sec) |
| `app.list` | 应用列表 | thirdPartyOnly, includeVersions |
| `app.info` | 应用详情 | package(包名) |
| `app.stop` | 强制停止 | package(包名) |
| `app.uninstall` | 卸载应用 | package, keepData |
| `app.install` | 安装APK | path(设备路径) |
| `app.clear_data` | 清除数据 | package(包名) |
| `sms.send` | 发送短信 | destination, text |
| `sms.read` | 读取短信 | limit, unreadOnly |
| `call.accept` | 接听来电 | — |
| `call.end` | 挂断电话 | — |
| `call.make` | 拨打电话 | number |
| `audio.open` | 开启音频 | mode: telephony/voip |
| `audio.close` | 关闭音频 | — |

### HTTP Bridge (内部使用)

Node Host 内部通过 HTTP Bridge (`http://127.0.0.1:18790`) 转发请求。Bridge 同时被 TutuAI 和 SkillEngine 使用，不可关闭。

### 错误处理

| HTTP 状态码 | 含义 |
|------------|------|
| 200 | 操作成功 |
| 503 | Socket 未连接 — 告诉用户检查 TutuGui Server |
| 500 | 内部错误 — 查看返回的 error 字段 |

### 常用应用包名

**open_app 必须传包名，不能传中文名！** 用户说"打开微信"→ 你传 `com.tencent.mm`，不认识的应用先用 `app.list` 查。

```
com.tencent.mm           — 微信
com.tencent.mobileqq     — QQ
com.ss.android.ugc.aweme — 抖音
com.xingin.xhs           — 小红书
com.taobao.taobao        — 淘宝
com.eg.android.AlipayGphone — 支付宝
com.sankuai.meituan      — 美团
com.autonavi.minimap     — 高德地图
com.netease.cloudmusic   — 网易云音乐
com.sina.weibo           — 微博
com.smile.gifmaker       — 快手
com.android.settings     — 系统设置
com.android.chrome       — Chrome
```

## 注意事项

- **工具通过原生 node 命令调用**，不需要 curl 或脚本
- 优先使用专用命令（`app.list`、`device.info`、`sms.read`）获取结构化数据
- 应用管理操作（卸载、清除数据）是不可逆的，操作前需确认
- 短信发送需用户确认后才能执行

## Cloud Browser (Browserless.io)

由于 Android/Termux 环境没有本地 Chrome 浏览器，OpenClaw 的内置 `browser` 工具无法使用。MeowHub 通过 Browserless.io 云服务提供浏览器能力。

**当 OpenClaw browser 工具报错时**（如 "No supported browser found"），使用云浏览器 API：

```bash
# 获取网页内容
$PREFIX/bin/curl -s -X POST \
  'https://production-sfo.browserless.io/chrome/content?token=2U7NAYZYDRFMq5Kcaefbd50a04bd202480938068f2aadeefc' \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://example.com"}'

# 网页截图
$PREFIX/bin/curl -s -X POST \
  'https://production-sfo.browserless.io/chrome/screenshot?token=2U7NAYZYDRFMq5Kcaefbd50a04bd202480938068f2aadeefc' \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://example.com"}' \
  -o /tmp/screenshot.png
```

详细用法见 `skills/meowhub-browser/SKILL.md`。

## 环境信息

- **设备类型:** Android (通过 MeowHub App 连接)
- **Node Host:** MeowHub Node Host (WebSocket → Gateway :18789)
- **Bridge 服务:** MeowHub HTTP Bridge on 127.0.0.1:18790 (内部使用)
- **AI Gateway:** OpenClaw on 127.0.0.1:18789
- **Socket 后端:** TutuGui Server on 127.0.0.1:28200 (App 内部管理)
- **云浏览器:** Browserless.io (production-sfo)

---

Add whatever helps you do your job. This is your cheat sheet.
