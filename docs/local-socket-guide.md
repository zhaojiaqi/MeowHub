# Local Socket 协议使用指南

本文档说明如何通过 Local Socket 连接 scrcpy-server、完成认证、发送命令并接收结果。核心为**协议格式**与**命令用法**，适用于任意客户端实现。

> scrcpy-server 提供本地 TCP 服务，供**同设备上的 Android 应用**通过 `127.0.0.1:28200` 通信，实现设备控制，无需经过互联网。

---

## 1. 连接

### 1.1 连接参数

| 参数 | 值 |
|------|-----|
| 地址 | `127.0.0.1` |
| 端口 | `28200`（可通过 scrcpy-server 启动参数 `socket_port=PORT` 自定义） |
| 协议 | JSON 行协议，每条消息以 `\n` 结尾 |
| 编码 | UTF-8 |
| 最大并发连接 | 10 |

### 1.2 连接步骤

1. 建立 TCP 连接到 `127.0.0.1:28200`
2. 获取输入流和输出流，使用 UTF-8 编码
3. 若需认证：发送认证包后等待 `auth_result`
4. 启动读取循环，按行读取 JSON

**示例（Java）**：

```java
Socket socket = new Socket("127.0.0.1", 28200);
BufferedReader reader = new BufferedReader(
    new InputStreamReader(socket.getInputStream(), "UTF-8"));
OutputStream out = socket.getOutputStream();

// 发送：JSON 行 + \n
String line = "{\"type\":\"get_server_info\",\"reqId\":\"1\"}\n";
out.write(line.getBytes("UTF-8"));
out.flush();

// 接收：按行读取
String response = reader.readLine();
```

---

## 2. 认证

### 2.1 何时需要认证

若服务端启用认证，TCP 连接成功后需先发送认证包，否则后续命令可能被拒绝。

### 2.2 认证请求

连接成功后立即发送（JSON 行 + `\n`）：

```json
{"type":"auth","token":"<token>"}
```

### 2.3 认证响应

服务端返回一行 JSON：

**成功**：

```json
{"type":"auth_result","success":true,"app_name":"应用名","permissions":["perm1","perm2"]}
```

**失败**：

```json
{"type":"auth_result","success":false,"reason":"token_expired"}
```

### 2.4 无 Token 连接

若服务端未启用认证，可跳过认证，连接后直接发送命令。

---

## 3. Token 获取

Token 由外部 API 提供，客户端需自行请求并缓存。

**API**：`https://www.szs.chat/api/app_auth/token.php`（POST，JSON）

**请求体**：

```json
{
  "app_id": "<应用ID>",
  "app_secret": "<应用密钥>",
  "device_id": "<设备ID>",
  "device_info": "<设备型号 / Android 版本>"
}
```

**成功响应**：

```json
{"success":true,"token":"<token>","expires_timestamp":1741234567}
```

---

## 4. 发送命令

### 4.1 通用格式

每条命令为**一行 JSON**，以 `\n` 结尾：

```json
{"type":"<命令类型>","reqId":"<请求ID>",...其他参数}\n
```

- `type`（必填）：命令类型
- `reqId`（有响应命令必填）：请求 ID，用于匹配响应

### 4.2 两类命令

| 类型 | 说明 | 示例 |
|------|------|------|
| **无响应** | 发送后不返回结果，无需 reqId | touch、key、text、command、start_app |
| **有响应** | 需带 reqId，等待对应响应 | execute_shell、screenshot、click_by_text、get_server_info |

### 4.3 发送方式

- **无响应命令**：发送后不等待，继续处理其他逻辑
- **有响应命令**：发送后阻塞或异步等待带相同 `reqId` 的响应行

**reqId 建议**：使用唯一字符串（如 `"req-1"`、`"req-2"` 或 UUID），便于匹配。

---

## 5. 接收结果

### 5.1 响应格式

**成功**：

```json
{"type":"<命令类型>_result","reqId":"<原reqId>","success":true,...结果数据}
```

**失败**：

```json
{"type":"<命令类型>_result","reqId":"<原reqId>","success":false,"error":"错误描述"}
```

### 5.2 响应匹配

1. 持续按行读取 `reader.readLine()`
2. 每行解析为 JSON，检查 `type` 和 `reqId`
3. 若 `reqId` 与某次请求一致，则该行为该请求的响应
4. 有响应命令需设置超时（建议 30 秒）

### 5.3 广播消息（非请求响应）

以下 `type` 为服务端主动推送，不是对某次请求的响应：

| type | 说明 |
|------|------|
| `clipboard` | 剪贴板内容 |
| `ui_nodes` | UI 节点树（由 get_ui_nodes 触发） |
| `device_info` | 设备信息（由 get_device_info 触发） |
| `auth_revoked` | 认证被撤销 |

### 5.4 特殊响应

**截图**：返回 `screenshot_data`（非 `screenshot_result`）：

```json
{"type":"screenshot_data","reqId":"ss-1","size":123456,"encoding":"base64","data":"/9j/4AAQ..."}
```

**execute_shell**：输出可能在 `stdout`、`output` 或 `result` 字段，客户端可兼容多种字段名。

---

## 6. 完整命令列表

### 6.1 输入控制（无响应）

| 命令 | 请求示例 | 参数说明 |
|------|----------|----------|
| **touch** | `{"type":"touch","action":0,"x":500,"y":800}` | action: 0=DOWN, 1=UP, 2=MOVE；x,y 必填；可选 pointerId, pressure, screenWidth, screenHeight |
| **key** | `{"type":"key","action":0,"keycode":4}` | action: 0=DOWN, 1=UP；keycode 必填；可选 repeat, metaState |
| **text** | `{"type":"text","text":"Hello"}` | text 必填 |
| **scroll** | `{"type":"scroll","x":540,"y":960,"hScroll":0,"vScroll":-3.0}` | x,y 必填；vScroll 负值=向下；可选 screenWidth, screenHeight |
| **command** | `{"type":"command","cmd":"HOME"}` | cmd 必填，见下表 |
| **start_app** | `{"type":"start_app","package":"com.android.settings"}` | package 必填 |

**command 的 cmd 取值**：`HOME`、`BACK`、`POWER`、`VOLUME_UP`、`VOLUME_DOWN`、`MENU`、`APP_SWITCH`、`EXPAND_NOTIFICATIONS`、`EXPAND_SETTINGS`、`COLLAPSE_PANELS`、`ROTATE`、`SCREEN_ON`、`SCREEN_OFF`

---

### 6.2 系统命令（无响应）

| 命令 | 请求示例 | 参数说明 |
|------|----------|----------|
| **set_clipboard** | `{"type":"set_clipboard","text":"内容","paste":false}` | text 必填；paste 可选，默认 false |
| **get_clipboard** | `{"type":"get_clipboard"}` | 无参数。通过广播 `clipboard` 返回 |
| **set_display_power** | `{"type":"set_display_power","on":true}` | on 必填，true=亮屏，false=灭屏 |

---

### 6.3 截屏与视频（有响应）

| 命令 | 请求示例 | 参数说明 | 响应 |
|------|----------|----------|------|
| **screenshot** | `{"type":"screenshot","reqId":"ss-1","displayId":0,"maxSize":1080,"quality":80}` | displayId 默认 0；maxSize 默认 1080；quality 默认 80 | `screenshot_data`，含 base64 的 data |
| **start_video** | `{"type":"start_video","reqId":"v-1","bitRate":500000,"maxFps":30}` | bitRate 默认 500000；maxFps 默认 30 | `start_video_ack` |
| **stop_video** | `{"type":"stop_video","reqId":"v-2"}` | 无参数 | `stop_video_ack` |

---

### 6.4 UI 自动化（有响应）

| 命令 | 请求示例 | 参数说明 | 响应 |
|------|----------|----------|------|
| **click_by_text** | `{"type":"click_by_text","reqId":"c-1","text":"确定","index":0}` | text 必填；index 默认 0 | success, x, y, matchCount |
| **click_by_id** | `{"type":"click_by_id","reqId":"c-2","id":"com.example:id/btn","index":0}` | id 必填；index 默认 0 | success, x, y, matchCount |
| **find_element** | `{"type":"find_element","reqId":"f-1","text":"设置","id":"","className":""}` | text/id/className 可选，至少一个 | success, count, elements |
| **long_click** | `{"type":"long_click","reqId":"lc-1","x":500,"y":800,"durationMs":800}` | x,y 必填；durationMs 默认 800 | success |
| **swipe** | `{"type":"swipe","reqId":"sw-1","x1":500,"y1":1500,"x2":500,"y2":500,"durationMs":300}` | x1,y1,x2,y2 必填；durationMs 默认 300 | success |
| **input_keyevent** | `{"type":"input_keyevent","reqId":"ke-1","keycode":"KEYCODE_ENTER"}` | keycode 必填，见 Android KeyEvent 名称 | success |

---

### 6.5 Shell 与文件操作（有响应）

| 命令 | 请求示例 | 参数说明 | 响应 |
|------|----------|----------|------|
| **execute_shell** | `{"type":"execute_shell","reqId":"sh-1","command":"ls /sdcard","timeout":30}` | command 必填；timeout 默认 30 秒 | stdout, stderr, exitCode |
| **list_files** | `{"type":"list_files","reqId":"lf-1","path":"/sdcard"}` | path 必填 | 文件列表 |
| **read_file** | `{"type":"read_file","reqId":"rf-1","path":"/sdcard/test.txt"}` | path 必填 | 文件内容 |
| **write_file** | `{"type":"write_file","reqId":"wf-1","path":"/sdcard/test.txt","content":"hello"}` | path, content 必填 | success |
| **delete_file** | `{"type":"delete_file","reqId":"df-1","path":"/sdcard/test.txt"}` | path 必填 | success |
| **analyze_storage** | `{"type":"analyze_storage","reqId":"as-1"}` | 无参数 | 存储统计 |
| **find_large_files** | `{"type":"find_large_files","reqId":"flf-1","path":"/sdcard","minSizeMB":10,"limit":20}` | path 默认 /sdcard；minSizeMB 默认 10；limit 默认 20 | 文件列表 |
| **download_file** | `{"type":"download_file","reqId":"dl-1","url":"https://...","savePath":"/sdcard/file.zip"}` | url 必填；savePath 可选 | success |

---

### 6.6 应用管理（有响应）

| 命令 | 请求示例 | 参数说明 | 响应 |
|------|----------|----------|------|
| **list_packages** | `{"type":"list_packages","reqId":"lp-1","thirdPartyOnly":true,"includeVersions":true}` | thirdPartyOnly 默认 false；includeVersions 默认 false | 包列表 |
| **get_app_info** | `{"type":"get_app_info","reqId":"ai-1","package":"com.example.app"}` | package 必填 | 应用详情 |
| **force_stop_app** | `{"type":"force_stop_app","reqId":"fs-1","package":"com.example.app"}` | package 必填 | success |
| **uninstall_app** | `{"type":"uninstall_app","reqId":"ua-1","package":"com.example.app","keepData":false}` | package 必填；keepData 默认 false | success |
| **install_apk** | `{"type":"install_apk","reqId":"ia-1","path":"/sdcard/app.apk"}` | path 必填 | success |
| **clear_app_data** | `{"type":"clear_app_data","reqId":"cd-1","package":"com.example.app"}` | package 必填 | success |

---

### 6.7 设备控制（有响应）

| 命令 | 请求示例 | 参数说明 | 响应 |
|------|----------|----------|------|
| **set_brightness** | `{"type":"set_brightness","reqId":"br-1","value":128,"mode":"manual"}` | value 必填(0-255)；mode 默认 manual | success |
| **set_volume** | `{"type":"set_volume","reqId":"sv-1","streamType":3,"value":10}` | value 必填；streamType 默认 3(MUSIC) | success |
| **get_volume** | `{"type":"get_volume","reqId":"gv-1"}` | 无参数 | 各声道音量 |
| **set_rotation** | `{"type":"set_rotation","reqId":"sr-1","rotation":0,"lock":true}` | rotation: 0/1/2/3；lock 默认 true | success |
| **open_url** | `{"type":"open_url","reqId":"ou-1","url":"https://example.com"}` | url 必填 | success |
| **set_wifi** | `{"type":"set_wifi","reqId":"sw-1","enabled":true}` | enabled 必填 | success |
| **set_bluetooth** | `{"type":"set_bluetooth","reqId":"sb-1","enabled":true}` | enabled 必填 | success |
| **read_contacts** | `{"type":"read_contacts","reqId":"rc-1","limit":50,"query":"张"}` | limit 默认 50；query 可选 | 联系人列表 |
| **read_call_log** | `{"type":"read_call_log","reqId":"cl-1","limit":20}` | limit 默认 20 | 通话记录 |
| **set_location_mock** | `{"type":"set_location_mock","reqId":"lm-1","latitude":39.9,"longitude":116.4}` | latitude, longitude 必填；accuracy, altitude 可选 | success |
| **get_setting** | `{"type":"get_setting","reqId":"gs-1","table":"system","key":"screen_brightness"}` | key 必填；table 默认 system | 设置值 |
| **set_setting** | `{"type":"set_setting","reqId":"ss-1","table":"system","key":"screen_brightness","value":"128"}` | key, value 必填；table 默认 system | success |

---

### 6.8 通信与媒体（有响应）

| 命令 | 请求示例 | 参数说明 | 响应 |
|------|----------|----------|------|
| **send_sms** | `{"type":"send_sms","reqId":"sms-1","destination":"13800138000","text":"你好"}` | destination, text 必填 | success |
| **read_sms** | `{"type":"read_sms","reqId":"rsms-1","limit":20,"unreadOnly":false}` | limit 默认 20；unreadOnly 默认 false | 短信列表 |
| **make_call** | `{"type":"make_call","reqId":"mc-1","number":"13800138000"}` | number 必填 | success |
| **end_call** | `{"type":"end_call","reqId":"ec-1"}` | 无参数 | success |
| **get_location** | `{"type":"get_location","reqId":"gl-1"}` | 无参数 | 位置信息 |
| **read_notifications** | `{"type":"read_notifications","reqId":"rn-1","limit":50}` | limit 默认 50 | 通知列表 |
| **speak_tts** | `{"type":"speak_tts","reqId":"tts-1","text":"你好","language":"zh"}` | text 必填；language 可选 | success |
| **vibrate** | `{"type":"vibrate","reqId":"vb-1","durationMs":500}` | durationMs 默认 500 | success |
| **search_media** | `{"type":"search_media","reqId":"sm-1","query":"photo","mediaType":"image","limit":20}` | query, mediaType, limit 可选 | 媒体列表 |

---

### 6.9 锁屏与安全（有响应）

| 命令 | 请求示例 | 参数说明 | 响应 |
|------|----------|----------|------|
| **get_lock_status** | `{"type":"get_lock_status","reqId":"ls-1"}` | 无参数 | 锁屏状态 |
| **unlock_screen** | `{"type":"unlock_screen","reqId":"us-1","credential":"1234"}` | credential 可选 | success |
| **set_lock_password** | `{"type":"set_lock_password","reqId":"slp-1","lockType":"pin","newCredential":"1234","oldCredential":""}` | lockType, newCredential 必填；oldCredential 可选 | success |
| **clear_lock_password** | `{"type":"clear_lock_password","reqId":"clp-1","oldCredential":"1234"}` | oldCredential 可选 | success |

---

### 6.10 系统信息（有响应）

| 命令 | 请求示例 | 参数说明 | 响应 |
|------|----------|----------|------|
| **get_server_info** | `{"type":"get_server_info","reqId":"si-1"}` | 无参数 | serverVersion, device, extensions 等 |
| **get_running_processes** | `{"type":"get_running_processes","reqId":"rp-1","appsOnly":false}` | appsOnly 默认 false | 进程列表 |
| **get_battery_stats** | `{"type":"get_battery_stats","reqId":"bs-1"}` | 无参数 | 电池信息 |
| **logcat** | `{"type":"logcat","reqId":"log-1","filter":"ActivityManager","lines":100,"level":"W"}` | filter, lines, level 可选 | 日志内容 |

---

### 6.11 网络与外设（有响应）

| 命令 | 请求示例 | 参数说明 | 响应 |
|------|----------|----------|------|
| **get_wifi_list** | `{"type":"get_wifi_list","reqId":"wl-1"}` | 无参数 | WiFi 列表 |
| **set_airplane_mode** | `{"type":"set_airplane_mode","reqId":"am-1","enabled":true}` | enabled 必填 | success |
| **set_screen_timeout** | `{"type":"set_screen_timeout","reqId":"st-1","timeoutMs":60000}` | timeoutMs 必填 | success |

---

### 6.12 通知与媒体文件操作（有响应）

| 命令 | 请求示例 | 参数说明 | 响应 |
|------|----------|----------|------|
| **push_notification** | `{"type":"push_notification","reqId":"pn-1","title":"标题","text":"内容"}` | title, text 必填 | success |
| **set_wallpaper** | `{"type":"set_wallpaper","reqId":"wp-1","path":"/sdcard/wallpaper.jpg","which":"home"}` | path 必填；which 默认 home | success |
| **take_screenshot_to_file** | `{"type":"take_screenshot_to_file","reqId":"tsf-1","path":"/sdcard/screenshot.png"}` | path 可选 | success |
| **record_screen** | `{"type":"record_screen","reqId":"rs-1","path":"/sdcard/record.mp4","durationSec":10,"bitRate":0}` | path 可选；durationSec 默认 10；bitRate 默认 0 | success |

---

### 6.13 服务控制

| 命令 | 请求示例 | 参数说明 | 响应 |
|------|----------|----------|------|
| **get_ui_nodes** | `{"type":"get_ui_nodes","mode":2}` | mode 默认 0，2=完整版推荐 | 通过广播 `ui_nodes` 返回 |
| **get_device_info** | `{"type":"get_device_info"}` | 无参数 | 通过广播 `device_info` 返回 |
| **shutdown** | `{"type":"shutdown"}` | 无参数 | `shutdown_ack`，500ms 后服务退出 |

---

### 6.14 虚拟屏幕管理（有响应）

| 命令 | 请求示例 | 参数说明 | 响应 |
|------|----------|----------|------|
| **vd_create** | `{"type":"vd_create","reqId":"vd-1","width":1080,"height":1920,"dpi":420,"systemDecorations":false}` | width/height/dpi 可选；systemDecorations 默认 false | displayId, width, height, dpi |
| **vd_destroy** | `{"type":"vd_destroy","reqId":"vd-2","displayId":5}` | displayId 必填 | success |
| **vd_list** | `{"type":"vd_list","reqId":"vd-3"}` | 无参数 | count, displays |
| **vd_start_app** | `{"type":"vd_start_app","reqId":"vd-4","displayId":5,"package":"com.example.app","forceStop":false}` | displayId, package 必填；forceStop 默认 false | success |
| **vd_screenshot** | `{"type":"vd_screenshot","reqId":"vd-5","displayId":5,"maxSize":1080,"quality":80}` | displayId 默认 0；maxSize 默认 1080；quality 默认 80 | `screenshot_data` |
| **vd_touch** | `{"type":"vd_touch","reqId":"vd-6","displayId":5,"action":0,"x":540,"y":960}` | displayId, action, x, y 必填；action: 0=DOWN, 1=UP, 2=MOVE | success |
| **vd_key** | `{"type":"vd_key","reqId":"vd-7","displayId":5,"action":0,"keycode":4}` | displayId, action, keycode 必填；可选 repeat, metaState | success |

**常用 KeyCode**：3=HOME, 4=BACK, 24=VOLUME_UP, 25=VOLUME_DOWN, 26=POWER, 82=MENU

---

## 7. 命令行测试

在 PC 上通过 adb 端口转发后，可用 `nc` 快速验证：

```bash
# 端口转发
adb forward tcp:28200 tcp:28200

# 获取服务器信息
echo '{"type":"get_server_info","reqId":"test-1"}' | nc localhost 28200

# 执行 Shell
echo '{"type":"execute_shell","reqId":"test-2","command":"ls /sdcard","timeout":30}' | nc localhost 28200

# 按 Home 键（无响应，无返回）
echo '{"type":"command","cmd":"HOME"}' | nc localhost 28200
```

---

## 8. 典型流程

```
1. 建立 TCP 连接 → 127.0.0.1:28200
2. [可选] 发送 {"type":"auth","token":"..."}，等待 auth_result
3. 启动后台线程/协程持续 readLine()，解析 JSON
4. 发送命令：
   - 无响应：直接 write(line + "\n")，flush
   - 有响应：生成 reqId，write 后等待匹配 reqId 的响应（带超时）
5. 根据 type 区分：命令响应 vs 广播消息
```

---

## 9. 常见问题

| 问题 | 可能原因 |
|------|----------|
| 连接失败 | scrcpy-server 未启动或端口被占用 |
| 认证失败 | Token 过期或无效 |
| 命令无响应 | 未带 reqId，或该命令本身为 fire-and-forget |
| 超时 | 命令执行时间过长，或未正确匹配 reqId |
