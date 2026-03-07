---
name: meowhub-device
description: "Control your Android phone via MeowHub — screenshot, tap, swipe, type, open apps, execute shell commands, make/accept/end calls, and control audio channels. Use this skill whenever you need to interact with the phone's screen or perform device operations."
metadata:
  openclaw:
    always: true
    emoji: "=^._.^="
    requires:
      bins: ["curl"]
---

# MeowHub Device Control

You have direct control over an Android phone through the MeowHub system-level protocol. This is real device control — equivalent to physical finger touches, undetectable by any application.

## ⚠️ MANDATORY: Only use $PREFIX/bin/curl — NEVER use Python/wget/scripts

**你必须且只能使用 `$PREFIX/bin/curl` 命令来调用所有 API。这是唯一允许的方式。**

**⚠️ 重要：不要使用 `curl` 或 `/bin/curl` 或 `/system/bin/curl`！系统自带 curl 已损坏。必须使用完整路径 `$PREFIX/bin/curl`。**

严格禁止：
- ❌ 使用 `python3`、`python`、`urllib`、`requests` 或任何 Python 代码
- ❌ 使用 `wget`
- ❌ 编写任何脚本文件（.py, .sh）
- ❌ 使用 `base64` 命令行工具
- ❌ 使用 `/bin/curl` 或 `/system/bin/curl`（系统 curl 已损坏）
- ❌ 使用裸 `curl` 命令（可能解析到错误的路径）

只允许：
- ✅ `$PREFIX/bin/curl`（Termux 预装的 curl，完整路径）

违反此规则的任何工具调用都是错误的。如果你发现自己要写 Python，**停下来，改用 $PREFIX/bin/curl**。

## How It Works

MeowHub provides an HTTP Bridge Server at `http://127.0.0.1:18790`. All device operations go through this bridge via `$PREFIX/bin/curl`.

## Quick Reference (直接复制使用)

```bash
# 截图
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/screenshot -H 'Content-Type: application/json' -d '{"quality":50}'

# 打开应用
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/open_app -H 'Content-Type: application/json' -d '{"name":"com.tencent.mm"}'

# 点击坐标
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/tap -H 'Content-Type: application/json' -d '{"x":540,"y":960}'

# 输入文字
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/type -H 'Content-Type: application/json' -d '{"text":"hello"}'

# 按键
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/press_key -H 'Content-Type: application/json' -d '{"key":"home"}'

# 检查连接
$PREFIX/bin/curl -s http://127.0.0.1:18790/api/status

# 接听来电
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/accept_call -H 'Content-Type: application/json' -d '{}'

# 挂断/拒接电话
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/end_call -H 'Content-Type: application/json' -d '{}'

# 拨打电话
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/make_call -H 'Content-Type: application/json' -d '{"number":"13800138000"}'
```

## Standard Operation Flow (2-3 步完成)

**你运行在手机本机上。截图返回 base64 图片数据，你的视觉模型可以直接分析，不需要任何中间脚本。**

1. **截图 + 分析** — 一步完成。用 `$PREFIX/bin/curl` 调用截图 API，返回的 `image` 字段（base64 JPEG）直接用你的视觉能力分析。
2. **操作** — 根据分析结果用 `$PREFIX/bin/curl` 执行操作（点击、输入等）
3. **验证** — 再用 `$PREFIX/bin/curl` 截图确认结果（可选）

**截图 API 返回格式：**
```json
{"image": "<base64-jpeg>", "data": "<base64-jpeg>", "width": 1080, "height": 2340, "mimeType": "image/jpeg"}
```

## Available Tools (21)

### Connection

| Tool | Endpoint | Method | Description |
|------|----------|--------|-------------|
| `check_connection` | `/api/status` | GET | Check bridge & socket connection status |

### Visual & Information

| Tool | Endpoint | Method | Key Parameters |
|------|----------|--------|---------------|
| `screenshot` | `/api/screenshot` | POST | quality(1-100), maxSize(resolution) |
| `get_ui_tree` | `/api/get_ui_tree` | POST | (none) |
| `find_element` | `/api/find_element` | POST | text, resourceId, className |
| `read_ui_text` | `/api/read_ui_text` | POST | filter, exclude |
| `get_device_info` | `/api/device_info` | POST | type: apps/battery/storage/network/bluetooth/all |
| `list_packages` | `/api/list_packages` | POST | (none) |

### Touch & Input

| Tool | Endpoint | Method | Key Parameters |
|------|----------|--------|---------------|
| `tap` | `/api/tap` | POST | x, y |
| `long_click` | `/api/long_click` | POST | x, y |
| `swipe` | `/api/swipe` | POST | startX, startY, endX, endY, duration(ms) |
| `scroll` | `/api/scroll` | POST | direction: up/down/left/right |
| `type_text` | `/api/type` | POST | text |
| `press_key` | `/api/press_key` | POST | key: home/back/power/recent/volume_up/volume_down/enter |
| `click_by_text` | `/api/click_by_text` | POST | text, index(if multiple) |

### System

| Tool | Endpoint | Method | Key Parameters |
|------|----------|--------|---------------|
| `open_app` | `/api/open_app` | POST | name (app name or package name) |
| `execute_shell` | `/api/execute_shell` | POST | command |

### Call & Audio

| Tool | Endpoint | Method | Key Parameters |
|------|----------|--------|---------------|
| `accept_call` | `/api/accept_call` | POST | (none) |
| `end_call` | `/api/end_call` | POST | (none) |
| `make_call` | `/api/make_call` | POST | number |
| `open_audio_channel` | `/api/open_audio_channel` | POST | mode: telephony(default)/voip |
| `close_audio_channel` | `/api/close_audio_channel` | POST | (none) |

## curl Examples

**所有命令必须使用 `$PREFIX/bin/curl`，不能省略路径！**

```bash
# Check connection
$PREFIX/bin/curl -s http://127.0.0.1:18790/api/status
# Response: {"connected":true,"status":"connected","screenWidth":1080,"screenHeight":2340}

# Screenshot
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/screenshot \
  -H 'Content-Type: application/json' \
  -d '{"quality":50}'

# Tap
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/tap \
  -H 'Content-Type: application/json' \
  -d '{"x":540,"y":960}'

# Swipe (scroll down)
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/swipe \
  -H 'Content-Type: application/json' \
  -d '{"startX":540,"startY":1800,"endX":540,"endY":400,"duration":300}'

# Scroll
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/scroll \
  -H 'Content-Type: application/json' \
  -d '{"direction":"down"}'

# Type text
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/type \
  -H 'Content-Type: application/json' \
  -d '{"text":"你好世界"}'

# Press key
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/press_key \
  -H 'Content-Type: application/json' \
  -d '{"key":"home"}'

# Open app
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/open_app \
  -H 'Content-Type: application/json' \
  -d '{"name":"com.tencent.mm"}'

# Get UI tree
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/get_ui_tree \
  -H 'Content-Type: application/json' -d '{}'

# Find element
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/find_element \
  -H 'Content-Type: application/json' \
  -d '{"text":"搜索"}'

# Click by text
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/click_by_text \
  -H 'Content-Type: application/json' \
  -d '{"text":"确定"}'

# Read visible text
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/read_ui_text \
  -H 'Content-Type: application/json' -d '{}'

# Execute shell command
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/execute_shell \
  -H 'Content-Type: application/json' \
  -d '{"command":"wm size"}'

# Device info
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/device_info \
  -H 'Content-Type: application/json' \
  -d '{"type":"battery"}'

# List packages
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/list_packages \
  -H 'Content-Type: application/json' -d '{}'

# Accept incoming call
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/accept_call \
  -H 'Content-Type: application/json' -d '{}'

# End current call (or reject incoming call)
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/end_call \
  -H 'Content-Type: application/json' -d '{}'

# Make a phone call
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/make_call \
  -H 'Content-Type: application/json' \
  -d '{"number":"13800138000"}'

# Open audio channel (telephony mode for regular calls, voip for VoIP/Android 12+)
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/open_audio_channel \
  -H 'Content-Type: application/json' \
  -d '{"mode":"telephony"}'

# Close audio channel
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/close_audio_channel \
  -H 'Content-Type: application/json' -d '{}'
```

## Error Handling

- **HTTP 503**: MeowHub Socket not connected — wait and retry
- **HTTP 500**: Internal error — check the error message in response body
- **Bridge unreachable**: The MeowHub app may not be running or Bridge Server not started

When you get a 503, tell the user that the MeowHub device connection is not active and they may need to check TutuGui Server status.

## Common App Package Names

```
com.tencent.mm           — WeChat (微信)
com.tencent.mobileqq     — QQ
com.ss.android.ugc.aweme — TikTok/Douyin (抖音)
com.xingin.xhs           — Xiaohongshu (小红书)
com.taobao.taobao        — Taobao (淘宝)
com.sankuai.meituan      — Meituan (美团)
com.android.settings     — Settings (系统设置)
com.android.chrome       — Chrome
com.android.mms          — Messages (短信)
com.android.dialer       — Phone (电话)
com.android.camera2      — Camera (相机)
```

## Tips

- Screen coordinates are based on device resolution. Use `curl -s http://127.0.0.1:18790/api/status` to get screen dimensions.
- After typing CJK characters, the input method may need a moment to process.
- For scrolling through feeds (TikTok, Xiaohongshu), use `swipe` from bottom to top.
- When an app isn't responding, try `press_key back` then retry.
- Use `get_ui_tree` when you need precise element positions instead of guessing coordinates.
- For complex flows, break them into small steps and verify each one.

## Safety

- **Always screenshot before and after** critical operations
- **Ask before sending** messages, making purchases, or posting content
- **Never** delete system files, format storage, or bypass security
- **When in doubt**, stop and ask the user
