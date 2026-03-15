# CLAUDE.md — MeowHub

## 项目简介

MeowHub 是一个 Android 应用，让 AI 通过 ADB 协议控制手机——看屏幕截图、理解上下文、执行点击/滑动/输入等操作。核心能力包括：声明式 Skill 引擎、AI 对话式手机操控（ChatAgent）、内嵌 OpenClaw 运行时。

## 构建

```bash
./gradlew assembleDebug        # 构建 debug APK
./gradlew :app:compileDebugKotlin  # 仅编译 Kotlin（快速验证）
./gradlew assembleRelease      # 构建 release APK（含 ProGuard）
```

- Kotlin 2.3+, Java 17, NDK 27, minSdk 28, targetSdk 36
- 不要用 Cursor 内置 JRE 构建（缺少 jlink）

## 模块结构

```
app/                  # 主应用（Jetpack Compose + Material 3）
terminal-emulator/    # Termux 终端模拟器
terminal-view/        # 终端 UI 组件
termux-shared/        # Termux 共享工具类
skills/               # 内置 Skill JSON 文件（19+）
```

## 代码结构 (`app/src/main/java/com/tutu/meowhub/`)

```
core/
  adb/        — ADB v2 协议、无线配对（SPAKE2）、mDNS 发现
  engine/     — SkillEngine（Skill 执行）、ChatAgent（对话式操控）、
                SocketCommandBridge（设备指令）、DeviceInfoCache、AiProvider
  socket/     — TutuSocketClient（TCP JSON-line 协议，端口 28200）
  model/      — 数据模型（Skill、Step、Chat、ConnectionState）
  settings/   — AiSettingsManager（API 配置）、AppToolManager（应用分类映射）
  network/    — MeowHubApiClient（Skill 市场、天气等 HTTP API）
  database/   — Room 数据库（Skill 持久化）
  service/    — 前台服务（悬浮窗、终端、ADB 配对）
  terminal/   — OpenClaw 网关、HTTP Bridge（端口 18790）
  auth/       — TUTU 账号认证

feature/
  chat/       — AI 对话界面 + ChatAgent
  market/     — Skill 市场
  myskills/   — 本地 Skill 管理
  settings/   — 设置（ADB 配对、AI 配置、应用工具）
  overlay/    — 悬浮操作面板
  terminal/   — 终端界面
  engine/     — Skill 执行 UI
```

## 架构要点

- **MVVM + Coroutines + StateFlow**：ViewModel 暴露 StateFlow 给 Compose UI
- **手动单例注入**：`MeowApp` 持有 `tutuClient`、`deviceCache`、`skillEngine`、`aiSettings` 等全局实例
- **AI 操控流程**：用户指令 → AI 分析截图 → 输出 `Action: click/type/open_app/...` → SocketCommandBridge 执行 → 下一轮截图反馈
- **Socket 协议**：JSON-line 格式，`reqId` 匹配请求/响应，`sendAndWait` 异步等待，`sendFireAndForget` 单向发送

## 关键文件

| 文件 | 作用 |
|------|------|
| `MeowApp.kt` | Application 单例，全局依赖 |
| `core/engine/SkillEngine.kt` | Skill 步骤执行引擎（ai_act/ai_check/tap/swipe 等 15+ 步骤类型） |
| `core/engine/SocketCommandBridge.kt` | 设备控制指令（tap/swipe/screenshot/startApp/shell 等） |
| `feature/chat/ChatAgent.kt` | 对话式 AI Agent（解析 Thought+Action 格式，执行设备操作） |
| `core/engine/DeviceInfoCache.kt` | 已安装应用、屏幕尺寸、设备信息缓存 |
| `core/socket/TutuSocketClient.kt` | TCP Socket 客户端（端口 28200） |
| `core/settings/AppToolManager.kt` | 应用分类工具（16 类 200+ 应用包名映射） |
| `local_socket.md` | Socket 协议完整文档（70+ 命令） |

## AI 提示词与动作解析

AI 输出格式为 `Thought: ... Action: action_name(params)`，在 `parseAiResponse()` 中解析。

可用 Action：`click`, `long_press`, `type`, `scroll`, `drag`, `open_app`, `press_home`, `press_back`, `wait`, `query_device_info`, `finished`

`open_app` 使用 `sendAndWait` 等待设备端响应，返回成功/失败结果反馈给 AI。

## 分支与 PR

- 主分支：`MeowClaw`
- PR 目标分支：`MeowClaw`
- 提交信息用中文或英文均可，参考 git log 风格（`feat:` / `fix:` / `chore:`）

## 注意事项

- `secrets.properties` 包含 API 密钥，已 gitignore，不要提交
- `.vscode/`、`build/` 目录不要提交
- 修改 `SocketCommandBridge` 中的命令时注意区分 `sendAndWait`（需要响应）和 `sendFireAndForget`（单向）
- `startApp()` 是 suspend 函数，通过设备端响应判断启动成功/失败
- ChatAgent 和 SkillEngine 有各自独立的 AI 动作执行循环，修改动作逻辑时两处都要同步
