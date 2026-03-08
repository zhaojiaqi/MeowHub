# MeowHub v1.0 Alpha — 首版发布说明

> **下载：** [tutu-alpha.apk](https://github.com/zhaojiaqi/MeowHub/releases/latest)  
> **系统要求：** Android 9+（推荐 Android 11+ 以使用无线调试）

---

## 这是什么？

MeowHub（图图智控）首版 Alpha，把你的手机变成 **AI 驱动的自动化代理**。本版本在**无需电脑、无需 Termux、无需自己装 Node/npm** 的前提下，提供：

- **内置完整 OpenClaw（MeowClaw）** — 一键安装，开箱即用  
- **登录即获 AI 能力** — 使用官网账号登录后，直接获得对话、联网搜索、手机控制等 AI 能力，无需自备 API Key  
- **TUTU AI 直接对话** — 在 App 内与图图 AI 自然对话，提问、查天气、打开 App、运行技能，响应速度比 OpenClaw 控制台对话**快约 5 倍**  
- **系统级 ADB 控制** — 基于 ADB 协议，对目标 App 透明、不可被检测，一次配对长期有效  

---

## 本版本主要功能

| 功能 | 说明 |
|------|------|
| **TUTU AI 对话** | 首页「对话」标签：自然语言提问、联网搜索、控制手机、运行技能，流式输出 |
| **OpenClaw / MeowClaw** | 内置完整 OpenClaw 运行时，一键安装 Node、npm、workspace 与网关，无需手机端手动配置 |
| **登录获取 AI** | 在 [tutuai.me](https://tutuai.me) 注册/登录，在 App 内登录后即可使用图图 AI，无需单独配置豆包等 API |
| **技能市场** | 浏览、搜索、一键运行社区技能（微信、抖音、天气、翻译等） |
| **Skill 引擎** | 声明式 JSON 技能，支持 AI 视觉分析、点击、滑动、打开 App 等 15+ 步骤类型 |
| **内置终端 + 控制台** | 查看 OpenClaw 日志，或打开网页控制台与 MeowClaw 对话 |
| **悬浮窗** | 快捷操作与实时执行状态，执行任务时显示当前 AI 动作说明 |

---

## 使用前准备

1. **设备**：Android 9+ 手机，建议 11+ 并开启**无线调试**  
2. **配对**：首次打开 MeowHub，按引导完成与设备的配对（ADB 无线调试）  
3. **AI 能力二选一**：  
   - **推荐**：在 [tutuai.me](https://tutuai.me) 注册并登录，在 App 内登录后即获得 AI 对话与控制能力  
   - **自建**：在 `secrets.properties` 中配置豆包 API Key 后自行编译安装，可本地使用豆包模型  

---

## 注意事项（Alpha）

- 本版本为 **Alpha**，可能存在未完善功能或偶发问题，欢迎在 [Issues](https://github.com/zhaojiaqi/MeowHub/issues) 反馈  
- 技能执行依赖设备已配对且 TUTU 服务连接正常，请确保网络与权限设置正确  
- 开源协议：GPL v3，详见 [LICENSE](https://github.com/zhaojiaqi/MeowHub/blob/main/LICENSE)  

---

## 相关链接

- **官网 / 登录**：[tutuai.me](https://tutuai.me)  
- **项目仓库**：[GitHub - MeowHub](https://github.com/zhaojiaqi/MeowHub)  
- **中文说明**：[README_CN.md](https://github.com/zhaojiaqi/MeowHub/blob/main/README_CN.md)  
- **Skill 开发指南**：[docs/skill-development-guide.md](https://github.com/zhaojiaqi/MeowHub/blob/main/docs/skill-development-guide.md)  

感谢使用 MeowHub，让 AI 真正「动手」操作你的手机。
