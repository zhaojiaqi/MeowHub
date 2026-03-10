---
title: "TuTu TOOLS"
summary: "MeowHub device control tools and local setup notes"
---

# TOOLS.md - Local Notes

Skills define _how_ tools work. This file is for _your_ specifics — the stuff that's unique to your setup.

## MeowHub HTTP Bridge

MeowHub 通过 HTTP Bridge Server 提供 16 个手机控制工具。Bridge 运行在 `http://127.0.0.1:18790`，桥接 App 内部的 SocketCommandBridge，底层通过 TutuGui Server 与设备交互。

### 架构

```
OpenClaw AI (你)
    ↓ HTTP (curl)
MeowHub Bridge Server (:18790)
    ↓ SocketCommandBridge
TutuSocketClient
    ↓ JSON-line TCP (:28200)
TutuGui Server
    ↓
截图 / 点击 / 滑动 / UI树 / Shell
```

### 调用方式

所有操作通过 `curl` 调用 HTTP Bridge：

```bash
# 检查连接状态（GET）
curl -s http://127.0.0.1:18790/api/status

# 执行操作（POST + JSON body）
curl -s -X POST http://127.0.0.1:18790/api/screenshot \
  -H 'Content-Type: application/json' \
  -d '{"quality":50}'

curl -s -X POST http://127.0.0.1:18790/api/tap \
  -H 'Content-Type: application/json' \
  -d '{"x":540,"y":960}'
```

### 工具列表

| 工具 | 端点 | 方法 | 关键参数 |
|------|------|------|---------|
| 连接状态 | `/api/status` | GET | — |
| 截图 | `/api/screenshot` | POST | quality(1-100), maxSize |
| 点击 | `/api/tap` | POST | x, y |
| 长按 | `/api/long_click` | POST | x, y |
| 滑动 | `/api/swipe` | POST | startX, startY, endX, endY, duration(ms) |
| 滚动 | `/api/scroll` | POST | direction: up/down/left/right |
| 输入文字 | `/api/type` | POST | text |
| 按键 | `/api/press_key` | POST | key: home/back/power/recent/enter |
| 按文字点击 | `/api/click_by_text` | POST | text, index |
| 打开应用 | `/api/open_app` | POST | name(应用名或包名) |
| UI 树 | `/api/get_ui_tree` | POST | — |
| 查找元素 | `/api/find_element` | POST | text, resourceId, className |
| 读取文字 | `/api/read_ui_text` | POST | filter, exclude |
| Shell 命令 | `/api/execute_shell` | POST | command |
| 设备信息 | `/api/device_info` | POST | type: apps/battery/storage/network/bluetooth/all |
| 应用列表 | `/api/list_packages` | POST | — |

### 错误处理

| HTTP 状态码 | 含义 |
|------------|------|
| 200 | 操作成功 |
| 503 | Socket 未连接 — 告诉用户检查 TutuGui Server |
| 500 | 内部错误 — 查看返回的 error 字段 |

### 常用应用包名

```
com.tencent.mm          — 微信
com.tencent.mobileqq    — QQ
com.ss.android.ugc.aweme — 抖音
com.xingin.xhs          — 小红书
com.taobao.taobao       — 淘宝
com.sankuai.meituan     — 美团
com.android.settings    — 系统设置
com.android.chrome      — Chrome
```

## ADB Shell 命令

通过 `execute_shell` 接口，你可以执行任意设备 shell 命令：

```bash
# 获取屏幕分辨率
curl -s -X POST http://127.0.0.1:18790/api/execute_shell \
  -H 'Content-Type: application/json' \
  -d '{"command":"wm size"}'

# 查看电池信息
curl -s -X POST http://127.0.0.1:18790/api/execute_shell \
  -H 'Content-Type: application/json' \
  -d '{"command":"dumpsys battery"}'

# 查看当前 Activity
curl -s -X POST http://127.0.0.1:18790/api/execute_shell \
  -H 'Content-Type: application/json' \
  -d '{"command":"dumpsys activity activities | grep mResumedActivity"}'
```

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
- **Bridge 服务:** MeowHub HTTP Bridge on 127.0.0.1:18790
- **AI Gateway:** OpenClaw on 127.0.0.1:18789
- **Socket 后端:** TutuGui Server on 127.0.0.1:28200 (App 内部管理)
- **云浏览器:** Browserless.io (production-sfo)

---

Add whatever helps you do your job. This is your cheat sheet.
