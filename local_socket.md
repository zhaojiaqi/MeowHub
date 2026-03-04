# Local Socket API

scrcpy-server 内置了一个本地 TCP Socket 服务，允许**同设备上的其他 Android APP** 通过 `127.0.0.1:28200` 直接与 scrcpy-server 通信，获得系统级设备控制能力，无需经过互联网。

该服务在 MQTT 模式和 LOCAL 模式下**均默认启动**。

## 连接信息

- 地址: `127.0.0.1`
- 默认端口: `28200` (可通过启动参数 `socket_port=PORT` 自定义)
- 最大并发连接: 10
- 协议: JSON 行协议 (每条消息以 `\n` 结尾)
- 编码: UTF-8

## 快速开始

### Android 客户端连接示例 (Java)

```java
Socket socket = new Socket("127.0.0.1", 28200);
BufferedReader reader = new BufferedReader(
    new InputStreamReader(socket.getInputStream(), "UTF-8"));
OutputStream out = socket.getOutputStream();

// 发送命令
String cmd = "{\"type\":\"get_server_info\",\"reqId\":\"1\"}\n";
out.write(cmd.getBytes("UTF-8"));
out.flush();

// 读取响应
String response = reader.readLine();
JSONObject json = new JSONObject(response);
```

### 使用 adb shell 测试

```bash
# 在 PC 上通过 adb 转发端口后测试
adb forward tcp:28200 tcp:28200

# 发送命令并读取响应
echo '{"type":"get_server_info","reqId":"test-1"}' | nc localhost 28200
```

## 通用协议格式

### 请求格式

所有命令均为一行 JSON，以换行符 `\n` 结尾:

```json
{"type":"<命令类型>", "reqId":"<可选的请求ID>", ...其他参数}\n
```

- `type` (string, **必填**): 命令类型标识
- `reqId` (string, 可选): 请求标识符，响应中会原样返回，用于匹配请求和响应

### 成功响应格式

```json
{"type":"<命令类型>_result", "reqId":"<原请求ID>", "success":true, ...结果数据}\n
```

### 错误响应格式

```json
{"type":"<命令类型>_result", "reqId":"<原请求ID>", "success":false, "error":"错误描述"}\n
```

### 广播消息

以下类型的消息会由服务端**主动推送**给所有已连接的客户端 (不是响应请求):

```json
{"type":"clipboard", "text":"剪贴板内容"}
{"type":"ui_nodes", "nodes":"..."}
{"type":"device_info", "info":"..."}
```

---

## 命令分类速查

| 分类 | 命令数量 | 响应模式 |
|------|---------|---------|
| [输入控制](#1-输入控制) | 6 | 无响应 (fire-and-forget) |
| [系统命令](#2-系统命令) | 3 | 无响应 |
| [截屏与视频](#3-截屏与视频) | 3 | 异步响应 |
| [UI 自动化](#4-ui-自动化) | 6 | 异步响应 |
| [Shell 与文件](#5-shell-与文件操作) | 8 | 异步响应 |
| [应用管理](#6-应用管理) | 6 | 异步响应 |
| [设备控制](#7-设备控制) | 12 | 异步响应 |
| [通信与媒体](#8-通信与媒体) | 8 | 异步响应 |
| [锁屏与安全](#9-锁屏与安全) | 4 | 异步响应 |
| [系统信息](#10-系统信息) | 4 | 异步响应 |
| [网络与外设](#11-网络与外设) | 3 | 异步响应 |
| [通知与媒体](#12-通知与媒体文件操作) | 4 | 异步响应 |
| [服务控制](#13-服务控制) | 3 | 异步响应 |

---

## 1. 输入控制

输入类命令为 fire-and-forget 模式，服务端不返回响应。

### touch - 触摸事件

```json
{
  "type": "touch",
  "action": 0,
  "x": 500,
  "y": 800,
  "pointerId": -1,
  "pressure": 1.0,
  "actionButton": 1,
  "buttons": 1,
  "screenWidth": 1080,
  "screenHeight": 1920
}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| action | int | 是 | - | MotionEvent action: 0=DOWN, 1=UP, 2=MOVE |
| x | int | 是 | - | 触摸 X 坐标 |
| y | int | 是 | - | 触摸 Y 坐标 |
| pointerId | long | 否 | -1 | 指针 ID (-1 为鼠标) |
| pressure | float | 否 | 1.0 | 压力值 |
| actionButton | int | 否 | 自动 | 动作按钮 |
| buttons | int | 否 | 自动 | 按钮状态 |
| screenWidth | int | 否 | 0 | 屏幕宽度 (用于坐标映射) |
| screenHeight | int | 否 | 0 | 屏幕高度 (用于坐标映射) |

### key - 按键事件

```json
{"type": "key", "action": 0, "keycode": 4, "repeat": 0, "metaState": 0}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| action | int | 是 | - | KeyEvent action: 0=DOWN, 1=UP |
| keycode | int | 是 | - | Android KeyEvent keycode |
| repeat | int | 否 | 0 | 重复次数 |
| metaState | int | 否 | 0 | Meta 键状态 |

### text - 文本输入

```json
{"type": "text", "text": "Hello World"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| text | string | 是 | 要输入的文本内容 |

### scroll - 滚动事件

```json
{
  "type": "scroll",
  "x": 540,
  "y": 960,
  "hScroll": 0,
  "vScroll": -3.0,
  "buttons": 0,
  "screenWidth": 1080,
  "screenHeight": 1920
}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| x | int | 是 | - | 滚动位置 X |
| y | int | 是 | - | 滚动位置 Y |
| hScroll | float | 否 | 0 | 水平滚动量 |
| vScroll | float | 否 | 0 | 垂直滚动量 (负值=向下) |
| buttons | int | 否 | 0 | 按钮状态 |
| screenWidth | int | 否 | 0 | 屏幕宽度 |
| screenHeight | int | 否 | 0 | 屏幕高度 |

### command - 系统快捷命令

```json
{"type": "command", "cmd": "HOME"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cmd | string | 是 | 命令名称，见下表 |

可用的 `cmd` 值:

| cmd | 说明 |
|-----|------|
| `HOME` | 按 Home 键 |
| `BACK` | 按返回键 |
| `POWER` | 按电源键 |
| `VOLUME_UP` | 音量加 |
| `VOLUME_DOWN` | 音量减 |
| `MENU` | 按菜单键 |
| `APP_SWITCH` | 最近任务 |
| `EXPAND_NOTIFICATIONS` | 展开通知栏 |
| `EXPAND_SETTINGS` | 展开快速设置 |
| `COLLAPSE_PANELS` | 收起面板 |
| `ROTATE` | 旋转设备 |
| `SCREEN_ON` | 开启屏幕 |
| `SCREEN_OFF` | 关闭屏幕 |

### start_app - 启动应用

```json
{"type": "start_app", "package": "com.example.app"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| package | string | 是 | 应用包名 |

---

## 2. 系统命令

### set_clipboard - 设置剪贴板

```json
{"type": "set_clipboard", "text": "要复制的内容", "paste": false}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| text | string | 是 | - | 剪贴板内容 |
| paste | boolean | 否 | false | 是否同时粘贴 |

### get_clipboard - 获取剪贴板

```json
{"type": "get_clipboard"}
```

服务端通过广播消息返回: `{"type":"clipboard","text":"..."}`

### set_display_power - 设置屏幕电源

```json
{"type": "set_display_power", "on": true}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| on | boolean | 是 | true=亮屏, false=灭屏 |

---

## 3. 截屏与视频

### screenshot - 截屏 (返回图片数据)

```json
{"type": "screenshot", "reqId": "ss-1", "displayId": 0, "maxSize": 1080, "quality": 80}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| reqId | string | 否 | "" | 请求 ID |
| displayId | int | 否 | 0 | 显示器 ID |
| maxSize | int | 否 | 1080 | 最大分辨率 |
| quality | int | 否 | 80 | JPEG 质量 (1-100) |

响应 (通过 Socket 返回 Base64 编码的 JPEG):

```json
{
  "type": "screenshot_data",
  "reqId": "ss-1",
  "size": 123456,
  "encoding": "base64",
  "data": "/9j/4AAQ..."
}
```

### start_video - 启动视频流

```json
{"type": "start_video", "reqId": "v-1", "bitRate": 500000, "maxFps": 30}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| bitRate | int | 否 | 500000 | 视频码率 (bps) |
| maxFps | int | 否 | 30 | 最大帧率 |

响应:

```json
{"type": "start_video_ack", "status": "ok"}
```

### stop_video - 停止视频流

```json
{"type": "stop_video", "reqId": "v-2"}
```

响应:

```json
{"type": "stop_video_ack", "status": "ok"}
```

---

## 4. UI 自动化

### click_by_text - 按文本点击

```json
{"type": "click_by_text", "reqId": "c-1", "text": "确定", "index": 0}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| text | string | 是 | - | 要匹配的文本 |
| index | int | 否 | 0 | 多个匹配时选择第几个 |

响应:

```json
{
  "type": "click_by_text_result",
  "reqId": "c-1",
  "success": true,
  "x": 540,
  "y": 960,
  "matchCount": 1
}
```

### click_by_id - 按资源 ID 点击

```json
{"type": "click_by_id", "reqId": "c-2", "id": "com.example:id/btn_ok", "index": 0}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| id | string | 是 | - | 资源 ID |
| index | int | 否 | 0 | 多个匹配时选择第几个 |

响应格式同 `click_by_text_result`。

### find_element - 查找 UI 元素

```json
{"type": "find_element", "reqId": "f-1", "text": "设置", "id": null, "className": null}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| text | string | 否 | null | 按文本查找 |
| id | string | 否 | null | 按资源 ID 查找 |
| className | string | 否 | null | 按类名查找 |

响应:

```json
{
  "type": "find_element_result",
  "reqId": "f-1",
  "success": true,
  "count": 2,
  "elements": [...]
}
```

### long_click - 长按

```json
{"type": "long_click", "reqId": "lc-1", "x": 500, "y": 800, "durationMs": 800}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| x | int | 是 | - | X 坐标 |
| y | int | 是 | - | Y 坐标 |
| durationMs | int | 否 | 800 | 长按时长 (毫秒) |

响应: `{"type":"long_click_result","reqId":"lc-1","success":true}`

### swipe - 滑动

```json
{"type": "swipe", "reqId": "sw-1", "x1": 500, "y1": 1500, "x2": 500, "y2": 500, "durationMs": 300}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| x1 | int | 是 | - | 起点 X |
| y1 | int | 是 | - | 起点 Y |
| x2 | int | 是 | - | 终点 X |
| y2 | int | 是 | - | 终点 Y |
| durationMs | int | 否 | 300 | 滑动时长 (毫秒) |

响应: `{"type":"swipe_result","reqId":"sw-1","success":true}`

### input_keyevent - 发送按键事件

```json
{"type": "input_keyevent", "reqId": "ke-1", "keycode": "KEYCODE_ENTER"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keycode | string | 是 | Android KeyEvent 名称 |

响应: `{"type":"input_keyevent_result","reqId":"ke-1","success":true}`

---

## 5. Shell 与文件操作

### execute_shell - 执行 Shell 命令

```json
{"type": "execute_shell", "reqId": "sh-1", "command": "ls /sdcard", "timeout": 30}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| command | string | 是 | - | Shell 命令 |
| timeout | int | 否 | 30 | 超时时间 (秒) |

响应:

```json
{
  "type": "shell_result",
  "reqId": "sh-1",
  "exitCode": 0,
  "stdout": "DCIM\nDownload\n...",
  "stderr": ""
}
```

### list_files - 列出文件

```json
{"type": "list_files", "reqId": "lf-1", "path": "/sdcard"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| path | string | 是 | 目录路径 |

响应: `{"type":"list_files_result","reqId":"lf-1",...文件列表数据}`

### read_file - 读取文件

```json
{"type": "read_file", "reqId": "rf-1", "path": "/sdcard/test.txt"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| path | string | 是 | 文件路径 |

响应: `{"type":"read_file_result","reqId":"rf-1",...文件内容数据}`

### write_file - 写入文件

```json
{"type": "write_file", "reqId": "wf-1", "path": "/sdcard/test.txt", "content": "hello"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| path | string | 是 | 文件路径 |
| content | string | 是 | 文件内容 |

响应: `{"type":"write_file_result","reqId":"wf-1","success":true}`

### delete_file - 删除文件

```json
{"type": "delete_file", "reqId": "df-1", "path": "/sdcard/test.txt"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| path | string | 是 | 文件路径 |

响应: `{"type":"delete_file_result","reqId":"df-1","success":true}`

### analyze_storage - 存储分析

```json
{"type": "analyze_storage", "reqId": "as-1"}
```

响应: `{"type":"analyze_storage_result","reqId":"as-1",...存储统计数据}`

### find_large_files - 查找大文件

```json
{"type": "find_large_files", "reqId": "flf-1", "path": "/sdcard", "minSizeMB": 10, "limit": 20}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| path | string | 否 | /sdcard | 搜索路径 |
| minSizeMB | long | 否 | 10 | 最小文件大小 (MB) |
| limit | int | 否 | 20 | 最大返回数量 |

响应: `{"type":"find_large_files_result","reqId":"flf-1",...文件列表}`

### download_file - 下载文件到设备

```json
{"type": "download_file", "reqId": "dl-1", "url": "https://example.com/file.zip", "savePath": "/sdcard/file.zip"}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| url | string | 是 | - | 下载 URL |
| savePath | string | 否 | 自动 | 保存路径 |

响应: `{"type":"download_file_result","reqId":"dl-1","success":true}`

---

## 6. 应用管理

### list_packages - 列出已安装应用

```json
{"type": "list_packages", "reqId": "lp-1", "thirdPartyOnly": true, "includeVersions": true}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| thirdPartyOnly | boolean | 否 | false | 仅第三方应用 |
| includeVersions | boolean | 否 | false | 包含版本信息 |

响应: `{"type":"list_packages_result","reqId":"lp-1",...包列表}`

### get_app_info - 获取应用信息

```json
{"type": "get_app_info", "reqId": "ai-1", "package": "com.example.app"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| package | string | 是 | 应用包名 |

响应: `{"type":"get_app_info_result","reqId":"ai-1",...应用详细信息}`

### force_stop_app - 强制停止应用

```json
{"type": "force_stop_app", "reqId": "fs-1", "package": "com.example.app"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| package | string | 是 | 应用包名 |

响应: `{"type":"force_stop_app_result","reqId":"fs-1","success":true}`

### uninstall_app - 卸载应用

```json
{"type": "uninstall_app", "reqId": "ua-1", "package": "com.example.app", "keepData": false}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| package | string | 是 | - | 应用包名 |
| keepData | boolean | 否 | false | 保留数据 |

响应: `{"type":"uninstall_app_result","reqId":"ua-1","success":true}`

### install_apk - 安装 APK

```json
{"type": "install_apk", "reqId": "ia-1", "path": "/sdcard/app.apk"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| path | string | 是 | 设备上的 APK 路径 |

响应: `{"type":"install_apk_result","reqId":"ia-1","success":true}`

### clear_app_data - 清除应用数据

```json
{"type": "clear_app_data", "reqId": "cd-1", "package": "com.example.app"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| package | string | 是 | 应用包名 |

响应: `{"type":"clear_app_data_result","reqId":"cd-1","success":true}`

---

## 7. 设备控制

### set_brightness - 设置亮度

```json
{"type": "set_brightness", "reqId": "br-1", "value": 128, "mode": "manual"}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| value | int | 是 | - | 亮度值 (0-255) |
| mode | string | 否 | "manual" | "manual" 或 "auto" |

响应: `{"type":"set_brightness_result","reqId":"br-1","success":true}`

### set_volume - 设置音量

```json
{"type": "set_volume", "reqId": "sv-1", "streamType": 3, "value": 10}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| streamType | int | 否 | 3 | 音频流类型 (3=MUSIC) |
| value | int | 是 | - | 音量值 |

响应: `{"type":"set_volume_result","reqId":"sv-1","success":true}`

### get_volume - 获取音量

```json
{"type": "get_volume", "reqId": "gv-1"}
```

响应: `{"type":"get_volume_result","reqId":"gv-1",...各声道音量信息}`

### set_rotation - 设置旋转

```json
{"type": "set_rotation", "reqId": "sr-1", "rotation": 0, "lock": true}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| rotation | int | 否 | 0 | 旋转角度: 0, 1, 2, 3 |
| lock | boolean | 否 | true | 锁定旋转 |

响应: `{"type":"set_rotation_result","reqId":"sr-1","success":true}`

### open_url - 打开 URL

```json
{"type": "open_url", "reqId": "ou-1", "url": "https://www.example.com"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| url | string | 是 | 要打开的 URL |

响应: `{"type":"open_url_result","reqId":"ou-1","success":true}`

### set_wifi - 设置 WiFi 开关

```json
{"type": "set_wifi", "reqId": "sw-1", "enabled": true}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| enabled | boolean | 是 | true=开启, false=关闭 |

响应: `{"type":"set_wifi_result","reqId":"sw-1","success":true}`

### set_bluetooth - 设置蓝牙开关

```json
{"type": "set_bluetooth", "reqId": "sb-1", "enabled": true}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| enabled | boolean | 是 | true=开启, false=关闭 |

响应: `{"type":"set_bluetooth_result","reqId":"sb-1","success":true}`

### read_contacts - 读取联系人

```json
{"type": "read_contacts", "reqId": "rc-1", "limit": 50, "query": "张"}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| limit | int | 否 | 50 | 最大返回数量 |
| query | string | 否 | "" | 搜索关键词 |

响应: `{"type":"read_contacts_result","reqId":"rc-1",...联系人列表}`

### read_call_log - 读取通话记录

```json
{"type": "read_call_log", "reqId": "cl-1", "limit": 20}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| limit | int | 否 | 20 | 最大返回数量 |

响应: `{"type":"read_call_log_result","reqId":"cl-1",...通话记录列表}`

### set_location_mock - 模拟位置

```json
{
  "type": "set_location_mock",
  "reqId": "lm-1",
  "latitude": 39.9042,
  "longitude": 116.4074,
  "accuracy": 1.0,
  "altitude": 0.0
}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| latitude | double | 是 | - | 纬度 |
| longitude | double | 是 | - | 经度 |
| accuracy | float | 否 | 1.0 | 精度 (米) |
| altitude | double | 否 | 0.0 | 海拔 (米) |

响应: `{"type":"set_location_mock_result","reqId":"lm-1","success":true}`

### get_setting / set_setting - 系统设置读写

```json
{"type": "get_setting", "reqId": "gs-1", "table": "system", "key": "screen_brightness"}
{"type": "set_setting", "reqId": "ss-1", "table": "system", "key": "screen_brightness", "value": "128"}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| table | string | 否 | "system" | "system" / "secure" / "global" |
| key | string | 是 | - | 设置项名称 |
| value | string | 是 (set) | - | 设置值 (仅 set_setting) |

响应: `{"type":"get_setting_result","reqId":"gs-1",...}` / `{"type":"set_setting_result","reqId":"ss-1","success":true}`

---

## 8. 通信与媒体

### send_sms - 发送短信

```json
{"type": "send_sms", "reqId": "sms-1", "destination": "13800138000", "text": "你好"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| destination | string | 是 | 目标号码 |
| text | string | 是 | 短信内容 |

响应: `{"type":"send_sms_result","reqId":"sms-1","success":true}`

### read_sms - 读取短信

```json
{"type": "read_sms", "reqId": "rsms-1", "limit": 20, "unreadOnly": false}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| limit | int | 否 | 20 | 最大返回数量 |
| unreadOnly | boolean | 否 | false | 仅未读短信 |

响应: `{"type":"read_sms_result","reqId":"rsms-1",...短信列表}`

### make_call - 拨打电话

```json
{"type": "make_call", "reqId": "mc-1", "number": "13800138000"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| number | string | 是 | 电话号码 |

响应: `{"type":"make_call_result","reqId":"mc-1","success":true}`

### end_call - 挂断电话

```json
{"type": "end_call", "reqId": "ec-1"}
```

响应: `{"type":"end_call_result","reqId":"ec-1","success":true}`

### get_location - 获取位置

```json
{"type": "get_location", "reqId": "gl-1"}
```

响应: `{"type":"get_location_result","reqId":"gl-1",...位置信息}`

### read_notifications - 读取通知

```json
{"type": "read_notifications", "reqId": "rn-1", "limit": 50}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| limit | int | 否 | 50 | 最大返回数量 |

响应: `{"type":"read_notifications_result","reqId":"rn-1",...通知列表}`

### speak_tts - 语音合成

```json
{"type": "speak_tts", "reqId": "tts-1", "text": "你好世界", "language": "zh"}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| text | string | 是 | - | 朗读文本 |
| language | string | 否 | "" | 语言代码 |

响应: `{"type":"speak_tts_result","reqId":"tts-1","success":true}`

### vibrate - 震动

```json
{"type": "vibrate", "reqId": "vb-1", "durationMs": 500}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| durationMs | int | 否 | 500 | 震动时长 (毫秒) |

响应: `{"type":"vibrate_result","reqId":"vb-1","success":true}`

### search_media - 搜索媒体文件

```json
{"type": "search_media", "reqId": "sm-1", "query": "photo", "mediaType": "image", "limit": 20}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| query | string | 否 | "" | 搜索关键词 |
| mediaType | string | 否 | "" | 媒体类型 |
| limit | int | 否 | 20 | 最大返回数量 |

响应: `{"type":"search_media_result","reqId":"sm-1",...媒体列表}`

---

## 9. 锁屏与安全

### get_lock_status - 获取锁屏状态

```json
{"type": "get_lock_status", "reqId": "ls-1"}
```

响应: `{"type":"get_lock_status_result","reqId":"ls-1",...锁屏状态信息}`

### unlock_screen - 解锁屏幕

```json
{"type": "unlock_screen", "reqId": "us-1", "credential": "1234"}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| credential | string | 否 | "" | 解锁密码/PIN |

响应: `{"type":"unlock_screen_result","reqId":"us-1","success":true}`

### set_lock_password - 设置锁屏密码

```json
{
  "type": "set_lock_password",
  "reqId": "slp-1",
  "lockType": "pin",
  "newCredential": "1234",
  "oldCredential": ""
}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| lockType | string | 是 | - | 锁屏类型: "pin", "password", "pattern" |
| newCredential | string | 是 | - | 新密码 |
| oldCredential | string | 否 | "" | 旧密码 |

响应: `{"type":"set_lock_password_result","reqId":"slp-1","success":true}`

### clear_lock_password - 清除锁屏密码

```json
{"type": "clear_lock_password", "reqId": "clp-1", "oldCredential": "1234"}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| oldCredential | string | 否 | "" | 当前密码 |

响应: `{"type":"clear_lock_password_result","reqId":"clp-1","success":true}`

---

## 10. 系统信息

### get_server_info - 获取服务器信息

```json
{"type": "get_server_info", "reqId": "si-1"}
```

响应:

```json
{
  "type": "server_info_result",
  "reqId": "si-1",
  "success": true,
  "serverVersion": "1.3.0",
  "buildDate": "2026-02-26",
  "scrcpyVersion": "3.2",
  "androidVersion": "14",
  "sdkVersion": 34,
  "device": "Xiaomi 14",
  "extensions": ["click_by_text", "execute_shell", ...],
  "extensionCount": 50
}
```

### get_running_processes - 获取运行进程

```json
{"type": "get_running_processes", "reqId": "rp-1", "appsOnly": false}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| appsOnly | boolean | 否 | false | 仅应用进程 |

响应: `{"type":"get_running_processes_result","reqId":"rp-1",...进程列表}`

### get_battery_stats - 获取电池信息

```json
{"type": "get_battery_stats", "reqId": "bs-1"}
```

响应: `{"type":"get_battery_stats_result","reqId":"bs-1",...电池信息}`

### logcat - 获取系统日志

```json
{"type": "logcat", "reqId": "log-1", "filter": "ActivityManager", "lines": 100, "level": "W"}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| filter | string | 否 | "" | 日志过滤标签 |
| lines | int | 否 | 100 | 返回行数 |
| level | string | 否 | "" | 日志级别: V/D/I/W/E |

响应: `{"type":"logcat_result","reqId":"log-1",...日志内容}`

---

## 11. 网络与外设

### get_wifi_list - 获取 WiFi 列表

```json
{"type": "get_wifi_list", "reqId": "wl-1"}
```

响应: `{"type":"get_wifi_list_result","reqId":"wl-1",...WiFi列表}`

### set_airplane_mode - 设置飞行模式

```json
{"type": "set_airplane_mode", "reqId": "am-1", "enabled": true}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| enabled | boolean | 是 | true=开启, false=关闭 |

响应: `{"type":"set_airplane_mode_result","reqId":"am-1","success":true}`

### set_screen_timeout - 设置屏幕超时

```json
{"type": "set_screen_timeout", "reqId": "st-1", "timeoutMs": 60000}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| timeoutMs | int | 是 | 超时时间 (毫秒) |

响应: `{"type":"set_screen_timeout_result","reqId":"st-1","success":true}`

---

## 12. 通知与媒体文件操作

### push_notification - 推送通知

在设备通知栏发送一条通知，使用 BigTextStyle 样式，展开后可查看完整内容。

```json
{"type": "push_notification", "reqId": "pn-1", "title": "标题", "text": "通知正文内容，展开后可查看完整文本"}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | string | 是 | 通知标题 |
| text | string | 是 | 通知内容（展开后显示完整文本） |

响应: `{"type":"push_notification_result","reqId":"pn-1","success":true,"title":"标题","text":"通知正文内容"}`

> 注意：通知以 shell 身份发布，来源显示为系统 Shell 命令通道，无法指定关联包名。

### set_wallpaper - 设置壁纸

```json
{"type": "set_wallpaper", "reqId": "wp-1", "path": "/sdcard/wallpaper.jpg", "which": "home"}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| path | string | 是 | - | 壁纸图片路径 |
| which | string | 否 | "home" | "home" / "lock" / "both" |

响应: `{"type":"set_wallpaper_result","reqId":"wp-1","success":true}`

### take_screenshot_to_file - 截屏保存到文件

```json
{"type": "take_screenshot_to_file", "reqId": "tsf-1", "path": "/sdcard/screenshot.png"}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| path | string | 否 | 自动 | 保存路径 |

响应: `{"type":"take_screenshot_to_file_result","reqId":"tsf-1","success":true}`

### record_screen - 录屏

```json
{"type": "record_screen", "reqId": "rs-1", "path": "/sdcard/record.mp4", "durationSec": 10, "bitRate": 0}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| path | string | 否 | 自动 | 保存路径 |
| durationSec | int | 否 | 10 | 录制时长 (秒) |
| bitRate | int | 否 | 0 | 码率 (0=默认) |

响应: `{"type":"record_screen_result","reqId":"rs-1","success":true}`

---

## 13. 服务控制

### get_ui_nodes - 获取 UI 节点树

```json
{"type": "get_ui_nodes", "mode": 2}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| mode | int | 否 | 0 | 0=精简版，2=完整版（推荐，否则可能找不到元素） |

服务端通过广播消息返回: `{"type":"ui_nodes","nodes":"..."}`

### get_device_info - 获取设备信息

```json
{"type": "get_device_info"}
```

服务端通过广播消息返回: `{"type":"device_info","info":"..."}`

### shutdown - 关闭服务

```json
{"type": "shutdown"}
```

响应:

```json
{"type": "shutdown_ack"}
```

服务端会在 500ms 后退出进程。

---

## 错误处理

当命令执行失败时，响应固定包含以下字段:

```json
{
  "type": "<命令类型>_result",
  "reqId": "<原请求ID>",
  "success": false,
  "error": "错误描述信息"
}
```

常见错误场景:
- JSON 格式错误: 服务端忽略该消息，不返回响应
- 必填参数缺失: 返回 `success:false` 和具体缺失字段说明
- 命令执行异常: 返回 `success:false` 和异常消息
- 未知命令类型: 服务端忽略，不返回响应

## 13. 虚拟屏幕管理

支持运行时动态创建/销毁多个虚拟屏幕，并在指定虚拟屏幕上执行触摸、按键、截图、启动 App 等操作。最多同时管理 5 个虚拟屏幕。

### vd_create - 创建虚拟屏幕

```json
{"type": "vd_create", "reqId": "vd-1", "width": 1080, "height": 1920, "dpi": 420, "systemDecorations": false}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| width | int | 否 | 主屏幕宽度 | 虚拟屏幕宽度（像素） |
| height | int | 否 | 主屏幕高度 | 虚拟屏幕高度（像素） |
| dpi | int | 否 | 主屏幕 DPI | 虚拟屏幕像素密度 |
| systemDecorations | boolean | 否 | false | 是否显示系统装饰（状态栏、导航栏） |

响应:

```json
{"type": "vd_create_result", "reqId": "vd-1", "success": true, "displayId": 5, "width": 1080, "height": 1920, "dpi": 420}
```

> `displayId` 是后续所有虚拟屏幕操作的关键参数，务必保存。

### vd_destroy - 销毁虚拟屏幕

```json
{"type": "vd_destroy", "reqId": "vd-2", "displayId": 5}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| displayId | int | 是 | 要销毁的虚拟屏幕 ID |

响应: `{"type": "vd_destroy_result", "reqId": "vd-2", "success": true, "displayId": 5}`

### vd_list - 列举所有虚拟屏幕

```json
{"type": "vd_list", "reqId": "vd-3"}
```

响应:

```json
{
  "type": "vd_list_result",
  "reqId": "vd-3",
  "success": true,
  "count": 2,
  "displays": [
    {"displayId": 5, "width": 1080, "height": 1920, "dpi": 420, "createdAt": 1740000000000},
    {"displayId": 6, "width": 720, "height": 1280, "dpi": 320, "createdAt": 1740000001000}
  ]
}
```

### vd_start_app - 在虚拟屏幕启动 App

```json
{"type": "vd_start_app", "reqId": "vd-4", "displayId": 5, "package": "com.example.app", "forceStop": false}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| displayId | int | 是 | - | 目标虚拟屏幕 ID |
| package | string | 是 | - | App 包名 |
| forceStop | boolean | 否 | false | 启动前是否强制停止 |

响应: `{"type": "vd_start_app_result", "reqId": "vd-4", "success": true, "displayId": 5, "package": "com.example.app"}`

### vd_screenshot - 虚拟屏幕截图

```json
{"type": "vd_screenshot", "reqId": "vd-5", "displayId": 5, "maxSize": 1080, "quality": 80}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| displayId | int | 否 | 0 | 目标虚拟屏幕 ID |
| maxSize | int | 否 | 1080 | 最大边长（像素） |
| quality | int | 否 | 80 | JPEG 压缩质量 (1-100) |

响应方式与 `screenshot` 命令一致，返回 Base64 编码的 JPEG 数据：

```json
{"type": "screenshot_data", "reqId": "vd-5", "size": 36816, "encoding": "base64", "data": "..."}
```

### vd_touch - 在虚拟屏幕注入触摸事件

```json
{"type": "vd_touch", "reqId": "vd-6", "displayId": 5, "action": 0, "x": 540, "y": 960}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| displayId | int | 是 | - | 目标虚拟屏幕 ID |
| action | int | 是 | - | 触摸动作（0=DOWN, 1=UP, 2=MOVE） |
| x | int | 是 | - | X 坐标 |
| y | int | 是 | - | Y 坐标 |
| pressure | float | 否 | 1.0 | 按压力度 |
| pointerId | long | 否 | 0 | 指针 ID（多点触控） |
| actionButton | int | 否 | 0 | 动作按钮 |
| buttons | int | 否 | 0 | 按钮状态 |

响应: `{"type": "vd_touch_result", "reqId": "vd-6", "success": true}`

**模拟点击示例**（DOWN + UP）:

```json
{"type": "vd_touch", "reqId": "t1", "displayId": 5, "action": 0, "x": 540, "y": 960}
{"type": "vd_touch", "reqId": "t2", "displayId": 5, "action": 1, "x": 540, "y": 960}
```

### vd_key - 在虚拟屏幕注入按键事件

```json
{"type": "vd_key", "reqId": "vd-7", "displayId": 5, "action": 0, "keycode": 4}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| displayId | int | 是 | - | 目标虚拟屏幕 ID |
| action | int | 是 | - | 按键动作（0=DOWN, 1=UP） |
| keycode | int | 是 | - | Android KeyCode |
| repeat | int | 否 | 0 | 重复次数 |
| metaState | int | 否 | 0 | 组合键状态 |

响应: `{"type": "vd_key_result", "reqId": "vd-7", "success": true}`

**模拟按 BACK 键示例**（DOWN + UP）:

```json
{"type": "vd_key", "reqId": "k1", "displayId": 5, "action": 0, "keycode": 4}
{"type": "vd_key", "reqId": "k2", "displayId": 5, "action": 1, "keycode": 4}
```

**常用 KeyCode 参考**: 3=HOME, 4=BACK, 24=VOLUME_UP, 25=VOLUME_DOWN, 26=POWER, 82=MENU

---

### 典型使用流程

```
1. 创建虚拟屏幕        -> vd_create -> 获取 displayId
2. 在虚拟屏幕启动 App   -> vd_start_app (传入 displayId)
3. 对虚拟屏幕截图确认    -> vd_screenshot (传入 displayId)
4. 在虚拟屏幕触摸操作    -> vd_touch (传入 displayId)
5. 在虚拟屏幕按键操作    -> vd_key (传入 displayId)
6. 使用完毕销毁         -> vd_destroy (传入 displayId)
```

---

## 注意事项

1. 服务端绑定 `127.0.0.1`，仅允许同设备 APP 连接，不接受外部网络连接
2. 同一 socket 连接上可能会收到广播消息 (clipboard/ui_nodes/device_info)，客户端应根据 `type` 字段区分是命令响应还是广播
3. 建议使用 `reqId` 关联请求和响应，尤其在并发发送多个命令时
4. 所有异步命令的响应可能不按请求顺序返回
5. 最大并发连接数为 10，超出后新连接会被拒绝
6. 连接断开后服务端自动清理资源，无需发送断开命令
