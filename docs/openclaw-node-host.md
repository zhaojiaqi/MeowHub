# OpenClaw Node Host 改造说明

## 背景

MeowHub 原来通过两种方式向 OpenClaw AI 暴露手机控制能力：

1. **SKILL.md** — 350+ 行文档教 AI 用 `$PREFIX/bin/curl` 调用 HTTP API
2. **MCP Server** (`meowhub-mcp-server.js`) — JSON-RPC stdin/stdout 服务器

两种方式都经过 HTTP Bridge：`AI → curl/MCP → HTTP Bridge (:18790) → SocketCommandBridge → TutuGui`

OpenClaw 的 **Node** 系统提供了原生替代方案：WebSocket 客户端直连 Gateway，注册设备命令为原生工具，AI 通过 `nodes` 工具的 `invoke` action 直接调用。

## 改造后架构

```
OpenClaw AI
    ↓ nodes tool (action=invoke, node=MeowHub)
OpenClaw Gateway (:18789)
    ↓ WebSocket (node.invoke.request / node.invoke.result)
meowhub-node-host.js
    ↓ HTTP POST/GET
MeowHub Bridge Server (:18790)   ← 未改动
    ↓ SocketCommandBridge
TutuGui Server (:28200)
```

## 关键文件

| 文件 | 说明 |
|------|------|
| `app/src/main/assets/meowhub-node-host.js` | Node Host 主脚本 |
| `app/src/main/assets/meowhub-workspace/skills/meowhub-device/SKILL.md` | AI 技能说明（已精简） |
| `app/src/main/assets/meowhub-workspace/TOOLS.md` | 工具备忘录 |
| `app/src/main/java/.../OpenClawInstaller.kt` | 新增 `copyNodeHostFromAssets()` |
| `app/src/main/java/.../OpenClawGatewayManager.kt` | 新增 `launchNodeHost()` / `stopNodeHost()` |
| `app/src/main/java/.../TerminalForegroundService.kt` | 在 `onFirstHealthy` 中启动 node-host |

## OpenClaw Gateway 协议要点

### 1. WebSocket 连接握手

Gateway 地址：`ws://127.0.0.1:18789`

连接后 Gateway 发送 challenge：
```json
{
  "type": "event",
  "event": "connect.challenge",
  "payload": { "nonce": "xxx", "ts": 1234567890 }
}
```

Node 回复 connect 请求：
```json
{
  "type": "req",
  "id": "1",
  "method": "connect",
  "params": {
    "minProtocol": 3,
    "maxProtocol": 3,
    "client": {
      "id": "node-host",
      "displayName": "MeowHub",
      "version": "1.0.0",
      "platform": "android",
      "mode": "node"
    },
    "role": "node",
    "scopes": ["operator.admin"],
    "caps": ["device", "sms", "call", "audio", "app"],
    "commands": ["device.tap", "device.screenshot", ...],
    "auth": {},
    "device": {
      "id": "<sha256-hex>",
      "publicKey": "<base64url-raw-ed25519>",
      "signature": "<base64url>",
      "signedAt": 1234567890000,
      "nonce": "xxx"
    }
  }
}
```

### 2. 设备身份 (Ed25519)

**device.id 的生成方式：**
```
deviceId = SHA256(raw_32byte_ed25519_public_key).hex()
```

这是 Gateway 验证身份的核心。Gateway 会从 `device.publicKey` 推导出 deviceId，与 `device.id` 比对，不匹配则拒绝连接（`DEVICE_AUTH_DEVICE_ID_MISMATCH`）。

**公钥格式：** 发送 32 字节原始 Ed25519 公钥的 base64url 编码（不是 SPKI DER，不是标准 base64）。

从 PEM 提取原始公钥：
```javascript
const ED25519_SPKI_PREFIX = Buffer.from('302a300506032b6570032100', 'hex');

function derivePublicKeyRaw(publicKeyPem) {
  const spki = crypto.createPublicKey(publicKeyPem).export({ type: 'spki', format: 'der' });
  // SPKI = 12 byte prefix + 32 byte raw key
  if (spki.length === ED25519_SPKI_PREFIX.length + 32 &&
      spki.subarray(0, ED25519_SPKI_PREFIX.length).equals(ED25519_SPKI_PREFIX)) {
    return spki.subarray(ED25519_SPKI_PREFIX.length);
  }
  return spki;
}
```

**base64url 编码（非标准 base64）：**
```javascript
function base64UrlEncode(buf) {
  return buf.toString('base64').replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/g, '');
}
```

### 3. 签名 Payload（v3 格式）

```
v3|{deviceId}|{clientId}|{clientMode}|{role}|{scopes}|{signedAtMs}|{token}|{nonce}|{platform}|{deviceFamily}
```

分隔符是 `|`（不是 `:`）。

具体值：
```
v3|<sha256-hex>|node-host|node|node|operator.admin|<timestamp-ms>||<nonce>|android|
```

- `scopes` = `connectParams.scopes.join(",")` — 必须与 connect 请求中的 scopes 数组一致
- `token` = `connectParams.auth.token ?? connectParams.auth.deviceToken ?? null`，通常为空字符串
- `platform` = `connectParams.client.platform`，会被 `toLowerCase()` 处理
- `deviceFamily` = `connectParams.client.deviceFamily`，未设置则为空字符串

签名用 Ed25519 私钥，结果 base64url 编码。

### 4. 命令允许列表 (allowCommands)

Gateway 有 **per-platform 命令白名单**。Android 平台默认只允许：
- `canvas.*`, `camera.list`, `location.get`, `device.info`, `device.status`, `contacts.search` 等标准命令

MeowHub 的自定义命令（`device.tap`, `sms.send` 等）不在默认白名单中。必须在 OpenClaw 配置中添加：

```json
{
  "gateway": {
    "nodes": {
      "allowCommands": [
        "device.screenshot", "device.tap", "device.swipe", ...
      ]
    }
  }
}
```

在 `OpenClawInstaller.kt` 的 `mergeBaseConfig()` 中设置。Gateway 启动时读取配置，connect 握手时过滤命令。

### 5. 接收 invoke 请求

Gateway 发送事件：
```json
{
  "type": "event",
  "event": "node.invoke.request",
  "payload": {
    "id": "invoke-uuid",
    "nodeId": "<sha256-hex>",
    "command": "device.tap",
    "paramsJSON": "{\"x\":540,\"y\":960}",
    "timeoutMs": 30000,
    "idempotencyKey": "uuid"
  }
}
```

### 6. 返回 invoke 结果

```json
{
  "type": "req",
  "id": "2",
  "method": "node.invoke.result",
  "params": {
    "id": "invoke-uuid",
    "nodeId": "<sha256-hex>",
    "ok": true,
    "payloadJSON": "{\"ok\":true,\"action\":\"tap\"}"
  }
}
```

**关键：`nodeId` 必须是 `identity.deviceId`**（SHA256 哈希），不能用自定义名称。与 connect 时的 `device.id` 一致。不匹配会返回 `nodeId mismatch` 错误，导致 invoke 超时。

### 7. Gateway 广播事件（可忽略）

Gateway 会周期性发送以下事件，Node 无需响应：
- `tick` — 心跳广播
- `health` — 健康状态快照
- `presence` — 在线状态
- `agent` — agent 状态更新
- `sessions` — 会话状态

Node 的活性通过 WebSocket 连接本身检测，断开即注销。

## AI 调用方式

AI 通过 `nodes` 工具调用 MeowHub 命令：

```
Tool: nodes
Parameters:
  action: "invoke"
  node: "MeowHub"           ← 匹配 displayName
  invokeCommand: "device.tap"
  invokeParamsJson: "{\"x\":540,\"y\":960}"
```

`node` 参数匹配规则（`resolveNodeIdFromCandidates`）：
1. 精确匹配 `nodeId`（SHA256 哈希）
2. 匹配 `remoteIp`
3. 匹配 `displayName`（大小写不敏感）
4. `nodeId` 前缀匹配（≥6 字符）

所以 `node: "MeowHub"` 能匹配到 `displayName: "MeowHub"` 的节点。

## 命令映射（28 个）

| Node 命令 | Bridge 端点 | HTTP 方法 |
|-----------|------------|-----------|
| `device.screenshot` | `/api/screenshot` | POST |
| `device.tap` | `/api/tap` | POST |
| `device.long_click` | `/api/long_click` | POST |
| `device.swipe` | `/api/swipe` | POST |
| `device.scroll` | `/api/scroll` | POST |
| `device.type` | `/api/type` | POST |
| `device.press_key` | `/api/press_key` | POST |
| `device.click_by_text` | `/api/click_by_text` | POST |
| `device.open_app` | `/api/open_app` | POST |
| `device.ui_tree` | `/api/get_ui_tree` | POST |
| `device.find_element` | `/api/find_element` | POST |
| `device.read_ui_text` | `/api/read_ui_text` | POST |
| `device.info` | `/api/device_info` | POST |
| `device.status` | `/api/status` | GET |
| `device.shell` | `/api/execute_shell` | POST |
| `app.list` | `/api/list_packages` | POST |
| `app.info` | `/api/get_app_info` | POST |
| `app.stop` | `/api/force_stop_app` | POST |
| `app.uninstall` | `/api/uninstall_app` | POST |
| `app.install` | `/api/install_apk` | POST |
| `app.clear_data` | `/api/clear_app_data` | POST |
| `sms.send` | `/api/send_sms` | POST |
| `sms.read` | `/api/read_sms` | POST |
| `call.accept` | `/api/accept_call` | POST |
| `call.end` | `/api/end_call` | POST |
| `call.make` | `/api/make_call` | POST |
| `audio.open` | `/api/open_audio_channel` | POST |
| `audio.close` | `/api/close_audio_channel` | POST |

## 踩坑记录

### 1. device.id 不能自定义
`device.id` 必须是 `SHA256(raw_public_key).hex()`，Gateway 会验证。不能用 "meowhub" 这样的自定义值。

### 2. 签名 payload 分隔符是 `|` 不是 `:`
看 OpenClaw 源码 `buildDeviceAuthPayloadV3`，各字段用 `|` 拼接。

### 3. scopes 必须在 connect params 和签名中一致
Gateway 从 `connectParams.scopes` 读取 scopes 重建签名 payload 来验证。如果 connect 请求不传 scopes（默认 `[]`），但签名用了 `"operator.admin"`，验证会失败（`DEVICE_AUTH_SIGNATURE_INVALID`）。

### 4. 公钥和签名用 base64url 编码
Gateway 内部统一用 base64url（RFC 4648，用 `-_` 替代 `+/`，无 padding）。用标准 base64 会导致签名验证失败。

### 5. 自定义命令需要 allowCommands 配置
Android 平台的默认命令白名单不包含 MeowHub 自定义命令。需在 `openclaw.json` 的 `gateway.nodes.allowCommands` 中添加。否则命令会在 connect 握手时被过滤掉，node.list 显示节点但 commands 为空。

### 6. invoke result 的 nodeId 必须是 deviceId
`node.invoke.result` 的 `nodeId` 字段必须是 `identity.deviceId`（SHA256 哈希），与注册时一致。用其他值会返回 `nodeId mismatch`，导致 invoke 超时。

### 7. displayName 用于 AI 引用节点
AI 通过 `node: "MeowHub"` 引用节点，匹配的是 `connect.client.displayName`。必须设置，否则 AI 只能用 SHA256 哈希引用。

### 8. OpenClaw 预制包需包含 Android native binding
`@snazzah/davey` 包需要 `@snazzah/davey-android-arm64` 的 `.node` 文件。在 macOS 上打包时容易遗漏。从 npm 下载后添加到 tar.xz 中。同时用 `COPYFILE_DISABLE=1` 打包避免 macOS xattr 污染。

## 未改动的部分

- `MeowHubBridgeServer.kt` — HTTP Bridge 继续运行，TutuAI/SkillEngine 仍然使用
- `SocketCommandBridge.kt` — 无变化
- `SkillEngine.kt` — `type:"api"` 步骤直接调用 bridge，不受影响
- `meowhub-mcp-server.js` — 保留但不再作为主要通道
- 技能 JSON 文件 — `type:"api"` 步骤不受影响
