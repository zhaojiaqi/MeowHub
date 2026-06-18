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

## Available Tools (29)

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
| `get_device_info` | `/api/device_info` | POST | type: apps/battery/storage/network/all |

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

### App Management

| Tool | Endpoint | Method | Key Parameters |
|------|----------|--------|---------------|
| `open_app` | `/api/open_app` | POST | **name (必须是包名，如 `com.tencent.mm`)** |
| `list_packages` | `/api/list_packages` | POST | thirdPartyOnly(bool), includeVersions(bool) |
| `get_app_info` | `/api/get_app_info` | POST | package (package name) |
| `force_stop_app` | `/api/force_stop_app` | POST | package (package name) |
| `uninstall_app` | `/api/uninstall_app` | POST | package, keepData(bool) |
| `install_apk` | `/api/install_apk` | POST | path (APK path on device) |
| `clear_app_data` | `/api/clear_app_data` | POST | package (package name) |

### System

| Tool | Endpoint | Method | Key Parameters |
|------|----------|--------|---------------|
| `execute_shell` | `/api/execute_shell` | POST | command, timeout(sec) |

### SMS

| Tool | Endpoint | Method | Key Parameters |
|------|----------|--------|---------------|
| `send_sms` | `/api/send_sms` | POST | destination (phone number), text |
| `read_sms` | `/api/read_sms` | POST | limit(int), unreadOnly(bool) |

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

# Device info (battery, network, storage, memory, display, foreground app)
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/device_info \
  -H 'Content-Type: application/json' \
  -d '{"type":"all"}'

# Execute shell command (response: {"output":"..."})
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/execute_shell \
  -H 'Content-Type: application/json' \
  -d '{"command":"wm size"}'

# List installed packages (third party with versions)
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/list_packages \
  -H 'Content-Type: application/json' \
  -d '{"thirdPartyOnly":true,"includeVersions":true}'

# Get app details
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/get_app_info \
  -H 'Content-Type: application/json' \
  -d '{"package":"com.tencent.mm"}'

# Force stop app
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/force_stop_app \
  -H 'Content-Type: application/json' \
  -d '{"package":"com.example.app"}'

# Uninstall app
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/uninstall_app \
  -H 'Content-Type: application/json' \
  -d '{"package":"com.example.app","keepData":false}'

# Install APK from device path
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/install_apk \
  -H 'Content-Type: application/json' \
  -d '{"path":"/sdcard/Download/app.apk"}'

# Clear app data
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/clear_app_data \
  -H 'Content-Type: application/json' \
  -d '{"package":"com.example.app"}'

# Send SMS
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/send_sms \
  -H 'Content-Type: application/json' \
  -d '{"destination":"13800138000","text":"Hello"}'

# Read SMS (recent 5, unread only)
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/read_sms \
  -H 'Content-Type: application/json' \
  -d '{"limit":5,"unreadOnly":true}'

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

## ⚠️ 应用包名规则（open_app / force_stop_app / get_app_info 等）

**所有涉及应用的 API 必须传入包名（如 `com.tencent.mm`），绝对不能传入中文名或英文显示名（如"微信""WeChat"）。**

### 常用应用包名速查（直接使用，无需查询）

| 用户说 | 你必须传 |
|-------|---------|
| 微信 / WeChat | `com.tencent.mm` |
| QQ | `com.tencent.mobileqq` |
| 抖音 / TikTok | `com.ss.android.ugc.aweme` |
| 小红书 | `com.xingin.xhs` |
| 淘宝 | `com.taobao.taobao` |
| 支付宝 | `com.eg.android.AlipayGphone` |
| 美团 | `com.sankuai.meituan` |
| 京东 | `com.jingdong.app.mall` |
| 拼多多 | `com.xunmeng.pinduoduo` |
| 高德地图 | `com.autonavi.minimap` |
| 百度地图 | `com.baidu.BaiduMap` |
| 哔哩哔哩 / B站 | `tv.danmaku.bili` |
| 网易云音乐 | `com.netease.cloudmusic` |
| QQ音乐 | `com.tencent.qqmusic` |
| 微博 | `com.sina.weibo` |
| 今日头条 | `com.ss.android.article.news` |
| 快手 | `com.smile.gifmaker` |
| 设置 / Settings | `com.android.settings` |
| Chrome | `com.android.chrome` |
| 短信 / Messages | `com.android.mms` |
| 电话 / Dialer | `com.android.dialer` |
| 相机 / Camera | `com.android.camera2` |
| 日历 / Calendar | `com.android.calendar` |
| 时钟 / Clock | `com.android.deskclock` |
| 计算器 | `com.android.calculator2` |
| 文件管理器 | `com.android.documentsui` |

### 不认识的应用 → 先查再开

如果用户提到的应用不在上表中，**必须先调用 `list_packages` 查询包名**，然后再用包名调用 `open_app`：

```bash
# 第一步：查找包名
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/list_packages -H 'Content-Type: application/json' -d '{"thirdPartyOnly":true}'
# 从返回的 packages 列表中找到匹配的包名

# 第二步：用包名打开
$PREFIX/bin/curl -s -X POST http://127.0.0.1:18790/api/open_app -H 'Content-Type: application/json' -d '{"name":"com.xxx.xxx"}'
```

**错误示范** ❌: `{"name":"微信"}` `{"name":"WeChat"}` `{"name":"抖音"}`
**正确示范** ✅: `{"name":"com.tencent.mm"}` `{"name":"com.ss.android.ugc.aweme"}`

## Tips

- Screen coordinates are based on device resolution. Use `curl -s http://127.0.0.1:18790/api/status` to get screen dimensions.
- After typing CJK characters, the input method may need a moment to process.
- For scrolling through feeds (TikTok, Xiaohongshu), use `swipe` from bottom to top.
- When an app isn't responding, try `press_key back` then retry.
- Use `get_ui_tree` when you need precise element positions instead of guessing coordinates.
- For complex flows, break them into small steps and verify each one.
- **Prefer `list_packages` and `get_app_info`** over `device_info` for app-related queries — they return structured data directly from the device.
- **`get_device_info`** returns battery, network, storage, memory, display info and current foreground app — use `device_info` endpoint with `type` parameter.
- **SMS**: Use `read_sms` to check messages, `send_sms` to send. Always confirm with user before sending.
- **App management**: `force_stop_app`, `clear_app_data`, `uninstall_app` are destructive — confirm with user first.
- **Prefer specific API endpoints** (`list_packages`, `get_device_info`, `read_sms`, `get_app_info`) over `execute_shell` when available — they return structured JSON data. Use `execute_shell` only for operations not covered by specific endpoints.
- **SMS**: Use `read_sms` to check messages, `send_sms` to send. Always confirm with user before sending.

## Safety

- **Always screenshot before and after** critical operations
- **Ask before sending** messages, making purchases, or posting content
- **Never** delete system files, format storage, or bypass security
- **When in doubt**, stop and ask the user
