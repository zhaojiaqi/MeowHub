# MeowClaw 集成指南

> MeowClaw = MeowHub + OpenClaw — 在 Android 手机上运行的本地 AI Agent，能看屏幕、点按钮、操控任何 APP。

## 目录

- [架构概览](#架构概览)
- [核心组件](#核心组件)
- [环境搭建](#环境搭建)
- [模型配置](#模型配置)
- [踩坑记录](#踩坑记录)
- [调试方法](#调试方法)
- [关键配置文件](#关键配置文件)
- [常见问题 FAQ](#常见问题-faq)

---

## 架构概览

```
┌─────────────────────────────────────────────┐
│                 MeowHub APP                  │
│                                              │
│  ┌──────────┐  ┌────────────┐  ┌──────────┐ │
│  │ Terminal  │  │  OpenClaw   │  │  Bridge  │ │
│  │   View    │←→│  Gateway    │  │  Server  │ │
│  │ (Termux)  │  │  (Node.js)  │  │ (:18790) │ │
│  └──────────┘  └─────┬──────┘  └─────┬────┘ │
│                      │               │       │
│                      │    ┌──────────┘       │
│                      ▼    ▼                  │
│              ┌──────────────────┐            │
│              │  SocketCommand   │            │
│              │     Bridge       │            │
│              └────────┬─────────┘            │
│                       │                      │
│              ┌────────▼─────────┐            │
│              │  TutuSocketClient │           │
│              │   (连接 TutuGui)  │           │
│              └──────────────────┘            │
└─────────────────────────────────────────────┘
         ↕ Socket (JSON-line protocol)
┌─────────────────────────────────────────────┐
│           TutuGui Server (PC 端)             │
│      ADB 转发 → 手机屏幕控制/截图/输入        │
└─────────────────────────────────────────────┘
```

**数据流：**
1. 用户在控制台/聊天界面发送指令
2. OpenClaw Gateway 调用 AI 模型（豆包 Seed 2.0）
3. AI 决定执行动作 → 通过 `exec` 工具调用 `$PREFIX/bin/curl`
4. curl 请求 → Bridge Server (`:18790`) → SocketCommandBridge → TutuSocketClient → TutuGui Server
5. TutuGui 通过 ADB 执行实际的屏幕操作

## 核心组件

| 组件 | 路径 | 职责 |
|------|------|------|
| `MeowTermuxService` | `core/terminal/MeowTermuxService.kt` | Termux 终端会话管理、环境变量配置 |
| `MeowTermuxInstaller` | `core/terminal/MeowTermuxInstaller.kt` | Termux bootstrap 安装 |
| `OpenClawInstaller` | `core/terminal/OpenClawInstaller.kt` | OpenClaw 安装、配置生成、workspace 部署 |
| `OpenClawGatewayManager` | `core/terminal/OpenClawGatewayManager.kt` | Gateway 生命周期管理、健康检查 |
| `MeowHubBridgeServer` | `core/terminal/MeowHubBridgeServer.kt` | HTTP Bridge，将 curl 请求转为 Socket 命令 |
| `TerminalViewModel` | `feature/terminal/TerminalViewModel.kt` | 终端页面逻辑、OpenClaw 生命周期编排 |
| `TerminalScreen` | `feature/terminal/TerminalScreen.kt` | 终端 UI、WebView 控制台、Tab 切换 |

### 依赖模块

| 模块 | 说明 |
|------|------|
| `terminal-emulator` | Termux 终端模拟器库（Java，含 JNI） |
| `terminal-view` | Termux 终端渲染视图（Java） |
| `termux-shared` | Termux 共享工具库（文件操作、Socket 等） |

## 环境搭建

### 前置条件

- Android Studio（推荐 Ladybug 及以上）
- NDK（CMake 会自动处理 JNI 编译）
- ADB 连接的 Android 设备（aarch64）

### 配置 secrets.properties

在项目根目录创建 `secrets.properties`：

```properties
DOUBAO_API_KEY=your-volcengine-api-key
DOUBAO_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
DOUBAO_MODEL_ID=doubao-seed-2-0-lite-260215
TUTU_APP_ID=your-tutu-app-id
TUTU_APP_SECRET=your-tutu-app-secret
```

### 可选模型 ID

| Model ID | 名称 | Context | 输入价格/百万 token | 适用场景 |
|----------|------|---------|-------------------|---------|
| `doubao-seed-2-0-pro-260215` | Pro 旗舰版 | 256K | ¥3.2 | 复杂推理、多步 Agent |
| `doubao-seed-2-0-lite-260215` | Lite 平衡版 | 128K | ¥0.6 | 日常使用（推荐） |
| `doubao-seed-2-0-mini-260215` | Mini 轻量版 | 256K | ¥0.2 | 简单任务、高并发 |

### 构建与安装

```bash
# 正常编译安装
./gradlew installDebug

# 首次启动后，APP 会自动：
# 1. 安装 Termux bootstrap
# 2. 安装 Node.js + OpenClaw
# 3. 部署 workspace（SKILL/AGENTS/IDENTITY 等）
# 4. 启动 Gateway
```

## 模型配置

### 关键：Provider 名必须是 `volcengine`

OpenClaw 内置了火山引擎的 provider 映射，识别的名称是 **`volcengine`**，不是 `doubao`。

**正确的 openclaw.json 配置：**

```json
{
  "agents": {
    "defaults": {
      "model": {
        "primary": "volcengine/doubao-seed-2-0-lite-260215"
      }
    }
  },
  "models": {
    "mode": "merge",
    "providers": {
      "volcengine": {
        "baseUrl": "https://ark.cn-beijing.volces.com/api/v3",
        "apiKey": "your-api-key",
        "api": "openai-completions",
        "models": [
          {
            "id": "doubao-seed-2-0-pro-260215",
            "name": "豆包Seed 2.0 Pro",
            "reasoning": true,
            "input": ["text", "image"],
            "contextWindow": 256000,
            "maxTokens": 128000,
            "compat": {
              "supportsReasoningEffort": true,
              "maxTokensField": "max_tokens"
            }
          }
        ]
      }
    }
  }
}
```

### 模型定义中的 compat 字段

**必须保留**以下 compat 配置，否则功能异常：

| 字段 | 值 | 原因 |
|------|---|------|
| `reasoning: true` | true | 声明模型支持推理 |
| `input: ["text", "image"]` | — | 声明支持图像输入（截图分析） |
| `compat.supportsReasoningEffort` | true | 否则不会发送 reasoning_effort 参数 |
| `compat.maxTokensField` | "max_tokens" | 豆包 API 使用此字段名 |

---

## 踩坑记录

### 1. Provider 名 `doubao` vs `volcengine`

**现象：** 控制台显示 `Fallback active: doubao/doubao-seed-2-0-pro-260215`，AI 走了降级模型。

**原因：** OpenClaw 内置的火山引擎 provider 名是 `volcengine`，不认识 `doubao`。配置中用 `doubao` 作为 provider 名时，OpenClaw 无法正确关联 API key 和 baseUrl。

**解决：** 所有配置中的 provider 前缀从 `doubao/` 改为 `volcengine/`，providers 的 key 也从 `"doubao"` 改为 `"volcengine"`。

### 2. `models.default` 是无效的配置 key

**现象：** Gateway 启动报 `Config invalid; Unrecognized key: "models.default"`，exit code 1。

**原因：** OpenClaw 的 config schema 不支持 `models.default` 字段。虽然官方文档提到 `openclaw config set models.default doubao`，但这个命令实际设置的不是 JSON 配置文件中的字段。

**解决：** 不在 `openclaw.json` 中设置 `models.default`，通过 `agents.defaults.model.primary` 来指定默认模型。

### 3. `agents.defaults.mcp` 是无效的配置 key

**现象：** Gateway 启动报 `Unrecognized key: "mcp"` under `agents.defaults`。

**原因：** OpenClaw 不支持在 `agents.defaults` 下配置 MCP，workspace skills 是通过目录结构自动发现的。

**解决：** 移除所有 `agents.defaults.mcp` 配置。设备控制通过 workspace skill (`meowhub-device`) 实现，不需要 MCP 配置。

### 4. 系统 curl 损坏，AI 调用失败

**现象：** AI 尝试用 curl 调用 Bridge API 时报错：`cannot locate symbol "EVP_MD_CTX_create" referenced by "/system/bin/curl"`。

**原因：**
- Android 系统自带的 `/system/bin/curl` 有 OpenSSL 链接错误
- OpenClaw 的 `exec` 工具通过 node `child_process` 创建子进程，继承的 PATH 可能不包含 Termux bin 目录
- AI 可能使用绝对路径 `/bin/curl` 调用到了系统版本

**解决：**
1. Gateway 启动命令中显式 `export PATH='$PREFIX/bin:...'`
2. SKILL.md 中强制 AI 使用 `$PREFIX/bin/curl` 完整路径
3. 禁止使用裸 `curl`、`/bin/curl`、`/system/bin/curl`

### 5. AI 持续使用 Python 脚本而不是 curl

**现象：** 让 AI "打开微信"，它写了一个 Python urllib 脚本去调用 API，而不是直接用 curl。

**原因：**
- AI 模型的默认偏好（尤其是 Lite 模型）倾向于用 Python
- 之前 curl 失败的历史记录可能影响了 AI 的决策
- SKILL.md 中的指导不够强

**解决：** 在 SKILL.md 中增加强制性指令（中英文双语、使用视觉标记 ❌/✅），明确禁止 Python/wget/脚本，只允许 `$PREFIX/bin/curl`。

### 6. Gateway 日志重复输出

**现象：** logcat 中 `GatewayOutput` 的每行日志都出现两次。

**原因：** `readGatewaySessionOutputToLogcat()` 每次调用都创建新协程，但从不取消旧协程。当 Gateway 崩溃后健康检查触发 `launchGatewaySession` 重启时，旧的 log reader 协程仍在运行。

**解决：** 添加 `logReaderJob` 变量跟踪协程，每次启动前先 `cancel()` 旧的。

### 7. `dist/entry.js` 缺失导致 Gateway 无法启动

**现象：** 清空缓存重装后报 `dist/entry.js missing`。

**原因：** OpenClaw npm 包内部结构变化，入口文件不再是 `dist/entry.js`。安装脚本中的完整性检查逻辑过时。

**解决：** 使用 `openclaw.mjs` 作为入口点检查，而非 `dist/entry.js`。

### 8. `java.lang.NoClassDefFoundError: HttpServer`

**现象：** Bridge Server 用 `com.sun.net.httpserver.HttpServer` 时崩溃。

**原因：** Android 的 Dalvik/ART 运行时不包含 `com.sun.net.httpserver` 包。

**解决：** 用 `java.net.ServerSocket` 手写 HTTP 解析，替代 `HttpServer`。

### 9. Bionic libc 兼容性问题

**现象：** Node.js 在 Android 上运行时，某些系统调用失败（如 `os.cpus()`）。

**原因：** Android 使用 Bionic libc 而非 glibc，缺少部分 POSIX API。

**解决：** 创建 `bionic-compat.js` 补丁文件，通过 `NODE_OPTIONS="-r $HOME/bionic-compat.js"` 在 Node.js 启动时注入，polyfill 缺失的 API。

### 10. Gateway 启动命令中 tee 失败 (exit 127)

**现象：** Gateway 启动时报 `No such file or directory`，exit code 127。

**原因：** 使用 `tee` 命令写日志到 `.meowhub/gateway.log`，但 `.meowhub` 目录不存在，且 Termux 环境中 `tee` 可能不可用。

**解决：** 移除 `tee`，让 OpenClaw 自己管理日志（写入 `$PREFIX/tmp/openclaw-*/`）。

---

## 调试方法

### 1. ADB Logcat 查看日志

```bash
# 查看所有 MeowHub 相关日志
adb logcat | grep "com.tutu.meowhub"

# 只看 Gateway 输出
adb logcat -s GatewayOutput

# 看 OpenClaw 安装过程
adb logcat -s OpenClawInstaller

# 看 Gateway 管理日志
adb logcat -s OpenClawGateway

# 看 Bridge Server 日志
adb logcat -s MeowHubBridge

# 看 Socket 通信日志
adb logcat -s TutuSocket
```

### 2. 查看 OpenClaw 日志文件

```bash
# 找到最新的日志文件
adb shell "run-as com.tutu.meowhub sh -c '\
  find files/usr/tmp -name \"openclaw-*.log\" 2>/dev/null | sort | tail -1'"

# 读取日志（替换路径）
adb shell "run-as com.tutu.meowhub sh -c '\
  tail -100 files/usr/tmp/openclaw-XXXXX/openclaw-2026-03-07.log'"
```

### 3. 查看/修改 OpenClaw 配置

```bash
# 查看当前配置
adb shell "run-as com.tutu.meowhub cat files/home/.openclaw/openclaw.json"

# 推送修改后的配置（热更新，无需重新编译）
adb push /tmp/openclaw.json /data/local/tmp/openclaw.json
adb shell "run-as com.tutu.meowhub cp /data/local/tmp/openclaw.json \
  files/home/.openclaw/openclaw.json"
```

### 4. 测试 Bridge API

```bash
# 检查 Bridge 连接状态
adb shell "run-as com.tutu.meowhub sh -c '\
  export PATH=/data/data/com.tutu.meowhub/files/usr/bin:/system/bin && \
  export LD_LIBRARY_PATH=/data/data/com.tutu.meowhub/files/usr/lib && \
  curl -s http://127.0.0.1:18790/api/status'"

# 测试截图
adb shell "run-as com.tutu.meowhub sh -c '\
  export PATH=/data/data/com.tutu.meowhub/files/usr/bin:/system/bin && \
  export LD_LIBRARY_PATH=/data/data/com.tutu.meowhub/files/usr/lib && \
  curl -s -X POST http://127.0.0.1:18790/api/open_app \
    -H \"Content-Type: application/json\" \
    -d \"{\\\"name\\\":\\\"com.tencent.mm\\\"}\"'"
```

### 5. 测试 Gateway 健康状态

```bash
# 通过端口转发在电脑上访问
adb forward tcp:18789 tcp:18789
curl http://127.0.0.1:18789/health
# 预期: {"ok":true,"status":"live"}
```

### 6. 查看 Node.js 进程环境

```bash
# 查看 OpenClaw 进程的 PATH
adb shell "run-as com.tutu.meowhub sh -c '\
  cat /proc/\$(pgrep -f openclaw.mjs)/environ 2>/dev/null'" | tr '\0' '\n' | grep PATH
```

### 7. 更新 SKILL.md（热更新）

```bash
adb push app/src/main/assets/meowhub-workspace/skills/meowhub-device/SKILL.md \
  /data/local/tmp/SKILL.md
adb shell "run-as com.tutu.meowhub cp /data/local/tmp/SKILL.md \
  files/home/.openclaw/workspace/skills/meowhub-device/SKILL.md"
```

### 8. 强制重启 Gateway

```bash
# 杀掉 Gateway 进程（APP 会自动重启它）
adb shell "run-as com.tutu.meowhub sh -c '\
  pkill -f openclaw.*gateway 2>/dev/null; \
  rm -f files/home/.openclaw/gateway.lock'"
```

---

## 关键配置文件

### 设备上的文件布局

```
/data/data/com.tutu.meowhub/files/
├── usr/                          # Termux prefix
│   ├── bin/                      # 可执行文件（node, curl, bash...）
│   ├── lib/
│   │   └── node_modules/
│   │       └── openclaw/         # OpenClaw 安装目录
│   └── tmp/
│       └── openclaw-XXXXX/       # OpenClaw 运行时日志
│
└── home/                         # Termux home
    ├── bionic-compat.js          # Bionic libc 兼容补丁
    ├── .meowhub/
    │   ├── init.sh               # 终端初始化脚本
    │   └── mcp-server.js         # MCP Server（备用）
    └── .openclaw/
        ├── openclaw.json         # OpenClaw 主配置
        ├── gateway.lock          # Gateway 锁文件
        └── workspace/            # AI Agent 工作空间
            ├── AGENTS.md         # Agent 定义
            ├── IDENTITY.md       # Agent 身份
            ├── SOUL.md           # Agent 灵魂/行为准则
            ├── TOOLS.md          # 工具使用说明
            ├── USER.md           # 用户信息
            └── skills/
                └── meowhub-device/
                    └── SKILL.md  # 设备控制技能定义
```

### APP 内的 assets 布局

```
app/src/main/assets/
├── debs/                         # 预打包的 deb 包
│   ├── nodejs-lts_24.13.0-1_aarch64.deb
│   ├── npm_11.11.0_all.deb
│   ├── c-ares_1.34.6_aarch64.deb
│   └── libsqlite_3.51.2_aarch64.deb
├── openclaw-node-modules.tar.xz  # 预打包的 node_modules
├── meowhub-mcp-server.js         # MCP Server 源码
└── meowhub-workspace/            # workspace 模板
    ├── AGENTS.md
    ├── IDENTITY.md
    ├── SOUL.md
    ├── TOOLS.md
    ├── USER.md
    ├── BOOTSTRAP.md
    └── skills/
        └── meowhub-device/
            └── SKILL.md
```

---

## 常见问题 FAQ

### Q: 为什么 Gateway 一直显示 "Starting..."？
**A:** 首次启动 Gateway 可能需要 30-60 秒。如果超过 2 分钟仍未启动：
1. 检查 logcat 中 `GatewayOutput` 是否有报错
2. 确认 `openclaw.json` 配置有效（无 unrecognized key）
3. 确认 Node.js 已正确安装：`adb shell "run-as com.tutu.meowhub files/usr/bin/node -v"`

### Q: 为什么 AI 回复很慢？
**A:** 检查是否走了 Fallback 模型（控制台底部会显示）。如果是：
1. 确认 provider 名是 `volcengine`（不是 `doubao`）
2. 确认 API key 有效
3. 查看 OpenClaw 日志中是否有 API 错误

### Q: 为什么 AI 不执行设备操作（只回复文字）？
**A:** 检查：
1. Bridge Server 是否正常：`curl http://127.0.0.1:18790/api/status`
2. TutuGui Server 是否已连接（Bridge 返回 `connected: true`）
3. SKILL.md 是否已正确部署到 workspace

### Q: 如何切换模型？
**A:** 修改 `secrets.properties` 中的 `DOUBAO_MODEL_ID`，然后重新编译安装。或者直接修改设备上的 `openclaw.json` 中的 `agents.defaults.model.primary` 字段后重启 APP。

### Q: 如何从零开始重新安装？
**A:** 在 APP 设置中清空缓存，或执行：
```bash
adb shell "pm clear com.tutu.meowhub"
```
然后重新打开 APP，等待自动安装完成。

---

## 版本信息

- **OpenClaw**: 2026.3.2
- **Node.js**: 24.13.0 LTS
- **Termux Bootstrap**: aarch64
- **豆包 Seed 2.0**: Pro / Lite / Mini
- **OpenClaw Provider**: `volcengine`（火山引擎）

---

*Author: zivzhao (ziv@tutuai.me)*
*MeowHub is open source under GPL v3.*
