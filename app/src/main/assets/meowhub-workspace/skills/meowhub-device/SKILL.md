---
name: meowhub-device
description: "Control your Android phone via MeowHub — screenshot, tap, swipe, type, open apps, execute shell commands, make/accept/end calls, and control audio channels. Use the nodes tool with action=invoke and node=MeowHub."
metadata:
  openclaw:
    always: true
    emoji: "=^._.^="
---

# MeowHub Device Control

You have direct control over an Android phone through MeowHub. All 28 device commands are registered on the **MeowHub** node.

## How to invoke commands

Use the `nodes` tool with `action: "invoke"`, `node: "MeowHub"`, and the command name:

```
Tool: nodes
Parameters:
  action: "invoke"
  node: "MeowHub"
  invokeCommand: "device.tap"
  invokeParamsJson: "{\"x\":540,\"y\":960}"
```

**Always set `node: "MeowHub"`** — this is required for every invocation.

## Available Commands

### Screen & UI

| Command | Description | Key Parameters |
|---------|-------------|---------------|
| `device.screenshot` | Take a screenshot (base64 JPEG) | quality(1-100), maxSize |
| `device.ui_tree` | Get UI accessibility tree | — |
| `device.find_element` | Find elements by text/ID | text, resourceId, className |
| `device.read_ui_text` | Read visible text on screen | filter, exclude |
| `device.info` | Get device info | type: apps/battery/storage/network/all |
| `device.status` | Check connection status | — |

### Touch & Input

| Command | Description | Key Parameters |
|---------|-------------|---------------|
| `device.tap` | Tap at coordinates | x, y |
| `device.long_click` | Long press at coordinates | x, y, duration(ms) |
| `device.swipe` | Swipe gesture | startX, startY, endX, endY, duration(ms) |
| `device.scroll` | Scroll in direction | direction: up/down/left/right |
| `device.type` | Type text into focused field | text |
| `device.press_key` | Press system key | key: home/back/power/recent/enter/volume_up/volume_down |
| `device.click_by_text` | Click element by text | text, index |
| `device.open_app` | Open app by **package name** | name (must be package name) |

### App Management

| Command | Description | Key Parameters |
|---------|-------------|---------------|
| `app.list` | List installed apps | thirdPartyOnly, includeVersions |
| `app.info` | Get app details | package |
| `app.stop` | Force stop app | package |
| `app.uninstall` | Uninstall app | package, keepData |
| `app.install` | Install APK | path (device path) |
| `app.clear_data` | Clear app data | package |

### System

| Command | Description | Key Parameters |
|---------|-------------|---------------|
| `device.shell` | Execute shell command | command, timeout(sec) |

### SMS

| Command | Description | Key Parameters |
|---------|-------------|---------------|
| `sms.send` | Send SMS | destination, text |
| `sms.read` | Read SMS messages | limit, unreadOnly |

### Call & Audio

| Command | Description | Key Parameters |
|---------|-------------|---------------|
| `call.accept` | Accept incoming call | — |
| `call.end` | End/reject call | — |
| `call.make` | Make a phone call | number |
| `audio.open` | Open audio channel | mode: telephony/voip |
| `audio.close` | Close audio channel | — |

## Standard Operation Flow

1. **Screenshot + Analyze** — use `device.screenshot` to capture screen, analyze visually
2. **Act** — tap, swipe, type based on what you see
3. **Verify** — screenshot again to confirm (optional)

## ⚠️ 应用包名规则

**所有涉及应用的命令必须传包名（如 `com.tencent.mm`），不能传中文名或英文名。**

### 常用应用包名速查

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

如果用户提到的应用不在上表中，先用 `app.list` 查包名，再用 `device.open_app` 打开。

## Tips

- Use `device.status` to get screen dimensions for coordinate-based operations
- After typing CJK characters, give the input method a moment to process
- For scrolling feeds, use `device.swipe` from bottom to top
- Use `device.ui_tree` for precise element positions instead of guessing
- Prefer `app.list` and `app.info` over `device.info` for app queries
- SMS sending requires user confirmation
- App management operations (uninstall, clear data) are destructive — confirm first

## Safety

- **Always screenshot before and after** critical operations
- **Ask before sending** messages, making purchases, or posting content
- **Never** delete system files, format storage, or bypass security
- **When in doubt**, stop and ask the user
