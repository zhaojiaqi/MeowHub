<p align="center">
  <img src="docs/assets/logo_banner.png" alt="MeowHub" width="560" />
</p>

<h1 align="center">MeowHub（喵控）</h1>

<p align="center">
  <b>AI 手机分身 — 让每个人都能制作自己的手机 AI 自动化技能</b>
</p>

<p align="center">
  <a href="https://tutuai.me">项目官网</a> •
  <a href="README.md">English</a> •
  <a href="docs/skill-development-guide.md">Skill 开发指南</a> •
  <a href="skills/">技能库</a> •
  <a href="CONTRIBUTING.md">参与贡献</a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="License: GPL v3" /></a>
  <img src="https://img.shields.io/badge/Android-9%2B-green.svg" alt="Android 9+" />
  <img src="https://img.shields.io/badge/Kotlin-2.3-purple.svg" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue.svg" alt="Compose" />
  <a href="https://github.com/zhaojiaqi/MeowHub/stargazers"><img src="https://img.shields.io/github/stars/zhaojiaqi/MeowHub?style=social" alt="Stars" /></a>
  <a href="https://github.com/zhaojiaqi/MeowHub/network/members"><img src="https://img.shields.io/github/forks/zhaojiaqi/MeowHub?style=social" alt="Forks" /></a>
  <a href="https://github.com/zhaojiaqi/MeowHub/issues"><img src="https://img.shields.io/github/issues/zhaojiaqi/MeowHub" alt="Issues" /></a>
</p>

---

## MeowHub 是什么？

MeowHub 是一款开源 Android 应用，可以把你的手机变成一个 **AI 驱动的自动化代理**。你可以把它理解成 **AI 手脚** — 类似 [OpenClaw](https://github.com/openclaw/openclaw)、Phone Agent 等方案，让 AI 能够「看」屏幕、「点」按钮、「滑」操作。不同的是：MeowHub 完全本地运行、开源可审计，并通过声明式 **Skill 引擎** 让你用 JSON 定义和分享自动化技能，无需写代码。

**我们的愿景：** 让每个人都能创建和分享自己的手机 AI 分身技能。

### 核心特性

- **Skill 引擎** — 基于 JSON 的声明式自动化引擎，支持 15+ 种步骤类型：API 调用、AI 视觉分析、条件分支、循环、用户交互等
- **AI 驱动操作** — 利用大语言模型（LLM）理解屏幕截图、定位 UI 元素、做出智能决策
- **无线 ADB** — 完全自包含的 ADB 实现，支持 mDNS 发现、TLS 配对（SPAKE2）、RSA 密钥持久化——无需电脑
- **技能市场** — 浏览、搜索并一键运行社区创建的技能
- **悬浮窗控制** — 浮动面板提供快捷操作和实时技能执行状态
- **开放生态** — 用 JSON 创建自己的技能，贡献到社区，打造你的专属手机 AI 分身

### 工作原理

```
┌──────────────────┐
│   MeowHub App    │
│  （Skill 引擎）   │
└────────┬─────────┘
         │ ADB 协议（TLS）
         ▼
┌──────────────────┐
│   ADB Daemon     │
└────────┬─────────┘
         │ shell: app_process
         ▼
┌──────────────────┐      ┌─────────────┐
│  TutuGui Server  │◄────►│  AI Provider │
│ (scrcpy-server)  │      │ （大模型 API）│
└────────┬─────────┘      └─────────────┘
         │ JSON Socket
         ▼
┌──────────────────┐
│ 触摸 / 滑动 /    │
│ 截图 / UI 树     │
└──────────────────┘
```

## 截图展示

<!-- TODO: 添加截图或 GIF 演示 -->

<p align="center">
  <i>截图即将上传...</i>
</p>

## 快速开始

### 环境要求

| 要求 | 版本 |
|------|------|
| Android | 9+（API 28），推荐 11+ 以支持无线调试 |
| Java | 17 |
| Kotlin | 2.3.10 |
| Android Gradle Plugin | 9.0.1 |
| Gradle | 9.2.1 |
| NDK | 28.x（CMake 3.22.1） |

### 构建步骤

1. **克隆仓库**

```bash
git clone https://github.com/zhaojiaqi/MeowHub.git
cd MeowHub
```

2. **配置密钥**

复制示例配置文件并填入你的 API 密钥：

```bash
cp secrets.properties.example secrets.properties
```

编辑 `secrets.properties`：

```properties
DOUBAO_API_KEY=你的API密钥
DOUBAO_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
DOUBAO_MODEL_ID=你的模型ID
TUTU_APP_ID=你的应用ID
TUTU_APP_SECRET=你的应用密钥
```

> **豆包 API 密钥**：可从[火山引擎](https://www.volcengine.com/product/doubao)获取。AI Provider 采用插件化设计，欢迎贡献其他 LLM 适配（OpenAI、Gemini 等）！
>
> **TUTU_APP_ID / TUTU_APP_SECRET**：用于连接图图智控设备控制服务。请访问[项目官网 tutuai.me](https://tutuai.me)，登录后进入 **个人中心** → **TUTU API KEY**，点击「申请 API KEY」即可获得 App ID 和 App Secret。

3. **编译安装**

```bash
./gradlew assembleDebug
./gradlew installDebug
```

### 首次使用

1. 在 Android 设备的开发者选项中开启 **无线调试**
2. 打开 MeowHub，按照配对向导完成连接
3. 连接成功后，可浏览技能市场或从"我的技能"运行技能

## Skill 生态

MeowHub 的强大来自其 **可扩展的 Skill 系统**。技能通过简单的 JSON 文件定义，可以自动化手机上几乎所有操作。

### 内置技能（19+）

| 分类 | 技能 |
|------|------|
| 社交 | 微信托管自动回复、朋友圈自动点赞、查看微信消息、添加微信好友 |
| 娱乐 | 刷抖音、抖音看剧跳广告、刷小红书 |
| 日常 | 查看天气、每日新闻、设闹钟、短信摘要 |
| 购物 | 淘宝搜索、美团点餐 |
| 工具 | 屏幕翻译、照片清理、存储清理、WiFi 诊断、手机体检、护眼模式 |

### 创建你自己的 Skill

技能是具有声明式步骤结构的 JSON 文件。以下是一个最简示例：

```json
{
  "name": "hello-world",
  "display_name": "你好世界",
  "version": "1.0.0",
  "steps": [
    {
      "id": "check_screen",
      "type": "ai_check",
      "prompt": "描述你在屏幕上看到的内容",
      "save_as": "screen_info"
    },
    {
      "id": "report",
      "type": "ai_summary",
      "prompt": "总结一下：${screen_info}",
      "output": "result"
    }
  ]
}
```

完整开发指南请查看 **[Skill 开发指南](docs/skill-development-guide.md)**。

### 贡献技能

我们鼓励每个人创建和分享技能！通过 Pull Request 将你的技能提交到 [`skills/`](skills/) 目录即可。详见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 技术栈

| 层级 | 技术选型 |
|------|---------|
| UI | Jetpack Compose + Material 3 |
| 状态管理 | ViewModel + StateFlow / SharedFlow |
| 导航 | Navigation Compose |
| 网络 | Kotlinx Serialization JSON + 自定义 Socket 协议 |
| ADB 协议 | 自实现 ADB v2（TLS、SPAKE2 配对） |
| 原生层 | CMake + C++（BoringSSL SPAKE2） |
| AI | 可插拔 AI Provider（豆包 / 自定义） |

## 项目结构

```
app/src/main/java/com/tutu/meowhub/
├── core/
│   ├── adb/          # 无线 ADB 协议栈
│   ├── auth/         # Token 认证
│   ├── engine/       # Skill 执行引擎（核心）
│   ├── model/        # 数据模型
│   ├── network/      # MeowHub API 客户端
│   ├── repository/   # 数据仓库
│   ├── service/      # 前台服务
│   └── socket/       # TutuSocketClient（TCP）
├── feature/
│   ├── debug/        # 调试面板
│   ├── engine/       # Skill 引擎 ViewModel
│   ├── market/       # 技能市场 UI
│   ├── myskills/     # 我的技能 UI
│   ├── navigation/   # 主导航
│   ├── overlay/      # 悬浮窗
│   └── settings/     # 设置与 ADB 控制
└── ui/theme/         # Material 3 主题
```

## 致谢

MeowHub 站在以下优秀开源项目的肩膀上：

- **[scrcpy](https://github.com/Genymobile/scrcpy)** — 杰出的屏幕镜像工具，启发了我们的设备控制层。MeowHub 的 TutuGui Server 基于 scrcpy-server 构建。
- **[Shizuku](https://github.com/RikkaApps/Shizuku)** — 开创了利用 ADB 实现应用级权限提升的方案，极大地启发了我们的无线 ADB 实现。

特别感谢这些项目背后的开发者和社区，他们的工作使 MeowHub 成为可能。

## 作者

**zivzhao** — [项目官网](https://tutuai.me) · [GitHub](https://github.com/zhaojiaqi) · [Email](mailto:zivzhao@icloud.com)

## 许可证

MeowHub 基于 **GNU 通用公共许可证 v3.0** 发布——详见 [LICENSE](LICENSE) 文件。

```
Copyright (C) 2025 zivzhao and MeowHub Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

### 期待你的贡献

我们特别欢迎对 **Skill 引擎核心算法** 的优化贡献：

- **RPA 执行效率** — 优化步骤执行流程、减少不必要的等待、提升命令批处理效率
- **AI 分析准确性** — 改进 `ai_check`/`ai_act` 步骤的提示词工程，减少幻觉，提升 UI 元素识别精度
- **Token 消耗优化** — 在 AI 分析质量与 Token 成本之间取得平衡，实现更智能的截图策略，减少冗余 AI 调用
- **错误恢复** — 更健壮的失败处理和重试策略

这些都是当前实现中仍有显著提升空间的方向。如果你对 RPA 自动化或 LLM 驱动的 Agent 感兴趣，这是一个非常值得深入的项目！

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=zhaojiaqi/MeowHub&type=date&legend=top-left)](https://www.star-history.com/#zhaojiaqi/MeowHub&type=date&legend=top-left)

---

<p align="center">
  Made with ❤️ for the open-source community
</p>
