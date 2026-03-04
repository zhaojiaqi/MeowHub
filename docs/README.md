# MeowHub（喵控）

TUTU 智能控制 · AI 手机控制中心

## 项目简介

MeowHub 是一款 Android 智能控制应用，通过无线 ADB 在设备上启动 TutuGui Server（基于 scrcpy-server），
并通过本地 Socket 协议实现对设备的自动化控制。内置 Skill 引擎支持步骤式自动化任务，结合 AI 能力实现
UI 定位、条件判断等高级操作。

## 核心功能

- **无线 ADB 调试** — mDNS 自动发现、TLS 配对、RSA 密钥持久化
- **TutuGui 服务管理** — 通过 ADB 推送并启动 scrcpy-server，支持进程检测、重启
- **Socket 控制协议** — JSON 行协议，支持触摸、滑动、截图、UI 树获取等 70+ 命令
- **Skill 引擎** — 步骤式自动化：API 调用、等待、事件等待、定时循环、条件分支、UI 定位、AI 分析与操作、无障碍事件订阅
- **技能市场** — 从 MeowHub API 获取 Skill 列表，支持分类、搜索、一键运行
- **悬浮窗控制** — 快捷操作面板 + Skill 执行状态 + 结果浮层展示

## 技术架构

| 层级 | 技术选型 |
|------|---------|
| UI | Jetpack Compose + Material 3 |
| 状态管理 | ViewModel + StateFlow / SharedFlow |
| 导航 | Navigation Compose（底部 Tab） |
| 网络 | Kotlinx Serialization JSON + 自定义 Socket 协议 |
| ADB 协议 | 自实现 ADB v2 协议（TLS、SPAKE2 配对） |
| 原生层 | CMake + C++（BoringSSL SPAKE2） |
| 隐藏 API | org.lsposed.hiddenapibypass |

## 项目结构

```
app/src/main/java/com/tutu/meowhub/
├── MeowApp.kt                    # Application 入口
├── MainActivity.kt               # 主 Activity
├── core/
│   ├── adb/                       # ADB 无线调试协议栈
│   │   ├── AdbClient.kt           # ADB 协议客户端（TLS、Shell）
│   │   ├── AdbProtocol.kt         # 协议常量（v2, maxdata=256KB）
│   │   ├── AdbMessage.kt          # 消息封装与校验
│   │   ├── AdbKey.kt              # RSA 密钥管理与 SSL 上下文
│   │   ├── AdbMdns.kt             # mDNS 服务发现
│   │   ├── AdbPairingClient.kt    # TLS + SPAKE2 配对
│   │   ├── AdbPairingService.kt   # 配对前台服务（通知交互）
│   │   └── TutuGuiServerLauncher.kt # 服务推送与启动
│   ├── auth/                      # Token 认证
│   ├── engine/                    # Skill 执行引擎
│   ├── model/                     # 数据模型
│   ├── network/                   # MeowHub API 客户端
│   ├── repository/                # 数据仓库
│   ├── service/                   # 前台服务基类
│   └── socket/                    # TutuSocketClient（TCP 127.0.0.1:28200）
├── feature/
│   ├── debug/                     # 调试面板（70+ 命令测试）
│   ├── engine/                    # Skill 引擎 ViewModel
│   ├── market/                    # 技能市场
│   ├── myskills/                  # 我的技能
│   ├── navigation/                # 主导航（Tab 切换）
│   ├── overlay/                   # 悬浮窗（快捷操作 + 状态条）
│   └── settings/                  # 设置（ADB 服务控制 + 日志）
└── ui/theme/                      # Material 3 主题
```

## 数据流

```
┌──────────────┐
│  MeowHub App │
│  (Compose)   │
└──────┬───────┘
       │ ADB Protocol (TLS)
       ▼
┌──────────────────────┐
│ ADB Daemon (adbd)    │
│ 127.0.0.1:动态端口    │
└──────┬───────────────┘
       │ shell: app_process
       ▼
┌──────────────────────┐
│ TutuGui Server       │
│ (scrcpy-server.jar)  │
│ 127.0.0.1:28200      │
└──────┬───────────────┘
       │ JSON Socket
       ▼
┌──────────────────────┐
│ TutuSocketClient     │
│ (App 内 Socket 连接)  │
└──────────────────────┘
```

## ADB 无线调试流程

### 首次配对

1. 用户在系统设置中开启「无线调试」
2. App 启动 `AdbPairingService` 前台服务
3. mDNS 发现 `_adb-tls-pairing._tcp` 服务
4. 通知栏弹出配对码输入框
5. `AdbPairingClient` 完成 TLS + SPAKE2 配对
6. RSA 密钥持久化到 SharedPreferences，后续自动连接

### 服务启动

1. mDNS 发现 `_adb-tls-connect._tcp` 获取 ADB 端口
2. `AdbClient` 连接并完成 TLS 握手（协议 v2, maxdata 256KB）
3. 检查设备上 `/data/local/tmp/scrcpy-server.jar` 的 MD5
4. 若不一致，通过 base64 分块 shell 命令推送 JAR
5. 执行 `setsid app_process / com.tutu.guiserver.Server ...` 启动服务
6. 验证进程存在后，`TutuSocketClient` 连接 `127.0.0.1:28200`

## 环境要求

| 要求 | 版本 |
|------|------|
| Android | 9+ (minSdk 28)，推荐 11+ (无线调试) |
| Java | 17 |
| Kotlin | 2.3.10 |
| Android Gradle Plugin | 9.0.1 |
| Gradle | 9.2.1 |
| NDK | 28.x（CMake 3.22.1） |
| compileSdk / targetSdk | 36 |

## 构建

```bash
# Debug 构建
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

## 关键依赖

| 依赖 | 用途 |
|------|------|
| Jetpack Compose BOM | UI 框架 |
| Material 3 | 设计系统 |
| Navigation Compose | 页面导航 |
| Kotlinx Serialization JSON | JSON 序列化 |
| Kotlinx Coroutines | 异步编程 |
| BouncyCastle | TLS 证书 |
| BoringSSL (prefab) | SPAKE2 配对原生库 |
| HiddenApiBypass | 绕过 Android 隐藏 API 限制 |
| Core SplashScreen | 启动屏 |

## 许可

本项目基于 [GPL-3.0](../LICENSE) 许可证开源。
