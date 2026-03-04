<p align="center">
  <img src="docs/assets/logo.png" alt="MeowHub Logo" width="120" />
</p>

<h1 align="center">MeowHub (喵控)</h1>

<p align="center">
  <b>AI Phone Avatar — Create Your Own Phone Automation Skills with AI</b>
</p>

<p align="center">
  <a href="README_CN.md">中文文档</a> •
  <a href="docs/skill-development-guide.md">Skill Development Guide</a> •
  <a href="skills/">Skill Gallery</a> •
  <a href="CONTRIBUTING.md">Contributing</a>
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

## What is MeowHub?

MeowHub is an open-source Android app that turns your phone into an **AI-powered automation agent**. It uses a built-in **Skill Engine** to execute step-by-step automation tasks — combining wireless ADB control, screen understanding via AI, and accessibility events — all without root access.

**Our vision:** Everyone can create and share their own phone AI avatar skills.

### Key Features

- **Skill Engine** — A declarative JSON-based automation engine supporting 15+ step types: API calls, AI vision analysis, conditional branching, loops, user prompts, and more
- **AI-Powered Actions** — Leverages LLM (Large Language Model) to understand screenshots, locate UI elements, and make intelligent decisions
- **Wireless ADB** — Fully self-contained ADB implementation with mDNS discovery, TLS pairing (SPAKE2), and RSA key persistence — no PC required
- **Skill Marketplace** — Browse, search, and run community-created skills with one tap
- **Overlay Control** — Floating panel for quick actions and real-time skill execution status
- **Open Ecosystem** — Create your own skills in JSON, contribute to the community, and build your personal phone AI avatar

### How It Works

```
┌──────────────────┐
│   MeowHub App    │
│  (Skill Engine)  │
└────────┬─────────┘
         │ ADB Protocol (TLS)
         ▼
┌──────────────────┐
│   ADB Daemon     │
└────────┬─────────┘
         │ shell: app_process
         ▼
┌──────────────────┐      ┌─────────────┐
│  TutuGui Server  │◄────►│  AI Provider │
│ (scrcpy-server)  │      │  (LLM API)   │
└────────┬─────────┘      └─────────────┘
         │ JSON Socket
         ▼
┌──────────────────┐
│ Touch / Swipe /  │
│ Screenshot / UI  │
└──────────────────┘
```

## Screenshots

<!-- TODO: Add screenshots or GIF demos -->

<p align="center">
  <i>Screenshots coming soon...</i>
</p>

## Getting Started

### Prerequisites

| Requirement | Version |
|------------|---------|
| Android    | 9+ (API 28), recommended 11+ for wireless debugging |
| Java       | 17 |
| Kotlin     | 2.3.10 |
| Android Gradle Plugin | 9.0.1 |
| Gradle     | 9.2.1 |
| NDK        | 28.x (CMake 3.22.1) |

### Build

1. **Clone the repository**

```bash
git clone https://github.com/zhaojiaqi/MeowHub.git
cd MeowHub
```

2. **Configure secrets**

Copy the example configuration and fill in your API keys:

```bash
cp secrets.properties.example secrets.properties
```

Edit `secrets.properties` with your credentials:

```properties
DOUBAO_API_KEY=your_api_key_here
DOUBAO_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
DOUBAO_MODEL_ID=your_model_id_here
TUTU_APP_ID=your_app_id_here
TUTU_APP_SECRET=your_app_secret_here
```

> You can obtain a Doubao API key from [Volcengine](https://www.volcengine.com/product/doubao). The AI provider is pluggable — contributions for other LLM providers (OpenAI, Gemini, etc.) are welcome!

3. **Build and install**

```bash
./gradlew assembleDebug
./gradlew installDebug
```

### First Run

1. Enable **Wireless Debugging** in Developer Settings on your Android device
2. Open MeowHub and follow the pairing wizard
3. Once connected, browse the Skill Marketplace or run a skill from "My Skills"

## Skill Ecosystem

MeowHub's power comes from its **extensible Skill system**. Skills are defined in simple JSON files and can automate virtually anything on your phone.

### Built-in Skills (19+)

| Category | Skills |
|----------|--------|
| Social | WeChat Auto Reply, WeChat Moments Like, Check Messages, Add Friend |
| Entertainment | Browse TikTok, TikTok Skip Ads, Browse Xiaohongshu |
| Daily | Check Weather, Daily News, Set Alarm, SMS Summary |
| Shopping | Taobao Search, Meituan Food |
| Tools | Screen Translate, Photo Cleanup, Storage Cleanup, WiFi Diagnose, Phone Health Check, Eye Comfort |

### Create Your Own Skill

Skills are JSON files with a declarative step-by-step structure. Here's a minimal example:

```json
{
  "name": "hello-world",
  "display_name": "Hello World",
  "version": "1.0.0",
  "steps": [
    {
      "id": "check_screen",
      "type": "ai_check",
      "prompt": "Describe what you see on the screen",
      "save_as": "screen_info"
    },
    {
      "id": "report",
      "type": "ai_summary",
      "prompt": "Summarize: ${screen_info}",
      "output": "result"
    }
  ]
}
```

For the complete guide, see **[Skill Development Guide](docs/skill-development-guide.md)**.

### Contributing Skills

We encourage everyone to create and share skills! Submit your skills via Pull Request to the [`skills/`](skills/) directory. See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| State | ViewModel + StateFlow / SharedFlow |
| Navigation | Navigation Compose |
| Network | Kotlinx Serialization JSON + Custom Socket Protocol |
| ADB | Self-implemented ADB v2 (TLS, SPAKE2 Pairing) |
| Native | CMake + C++ (BoringSSL SPAKE2) |
| AI | Pluggable AI Provider (Doubao / Custom) |

## Project Structure

```
app/src/main/java/com/tutu/miaohub/
├── core/
│   ├── adb/          # Wireless ADB protocol stack
│   ├── auth/         # Token authentication
│   ├── engine/       # Skill execution engine (core)
│   ├── model/        # Data models
│   ├── network/      # MeowHub API client
│   ├── repository/   # Data repository
│   ├── service/      # Foreground services
│   └── socket/       # TutuSocketClient (TCP)
├── feature/
│   ├── debug/        # Debug panel
│   ├── engine/       # Skill engine ViewModel
│   ├── market/       # Skill marketplace UI
│   ├── myskills/     # My skills UI
│   ├── navigation/   # Main navigation
│   ├── overlay/      # Floating overlay
│   └── settings/     # Settings & ADB control
└── ui/theme/         # Material 3 theme
```

## Acknowledgements

MeowHub stands on the shoulders of these amazing open-source projects:

- **[scrcpy](https://github.com/Genymobile/scrcpy)** — The brilliant screen mirroring tool that inspired our device control layer. MeowHub's TutuGui Server is built upon scrcpy-server.
- **[Shizuku](https://github.com/RikkaApps/Shizuku)** — Pioneered the approach of using ADB for app-level privilege elevation on Android, which greatly inspired our wireless ADB implementation.

Special thanks to the developers and communities behind these projects. Their work has made MeowHub possible.

## Author

**zivzhao** — [GitHub](https://github.com/zhaojiaqi) · [Email](mailto:zivzhao@icloud.com)

## License

MeowHub is licensed under the **GNU General Public License v3.0** — see the [LICENSE](LICENSE) file for details.

```
Copyright (C) 2025 zivzhao and MeowHub Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

### Help Wanted

We especially welcome contributions that improve the **Skill Engine's core algorithms**:

- **RPA Execution Efficiency** — Optimize step execution flow, reduce unnecessary waits, improve command batching
- **AI Analysis Accuracy** — Better prompt engineering for `ai_check`/`ai_act` steps, reduce hallucinations, improve UI element recognition
- **Token Consumption Optimization** — Balance between AI analysis quality and token cost, implement smarter screenshot strategies, reduce redundant AI calls
- **Error Recovery** — More robust failure handling and retry strategies

These are active areas where the current implementation has room for significant improvement. If you're interested in RPA automation or LLM-powered agents, this is a great project to dive into!

## Star History

<p align="center">
  <a href="https://github.com/zhaojiaqi/MeowHub/stargazers">
    <img src="https://img.shields.io/github/stars/zhaojiaqi/MeowHub?style=for-the-badge" alt="Star History" />
  </a>
</p>

---

<p align="center">
  Made with ❤️ for the open-source community
</p>
