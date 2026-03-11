# MeowHub v1.1.0 — 更新日志

> **下载：** [GitHub Releases](https://github.com/zhaojiaqi/MeowHub/releases/latest)
> **系统要求：** Android 9+（推荐 Android 11+ 以使用无线调试）

---

## 核心升级：设备控制服务全面增强

本版本升级 TutuGui Server（scrcpy-server）至 0312 版本，支持命令数从 27 个扩展至 **36 个**，补全了应用管理、短信、Shell 执行等关键能力。同时对 APP 端进行了全链路适配和多项体验优化。

---

## 新增功能

### 设备控制 — 新增 11 个 Socket 命令

| 命令 | 说明 |
|------|------|
| `list_packages` | 获取已安装应用列表（支持筛选第三方、含版本号） |
| `get_app_info` | 获取应用详细信息（版本、安装时间、数据大小等） |
| `force_stop_app` | 强制停止应用 |
| `uninstall_app` | 卸载应用（可选保留数据） |
| `install_apk` | 安装设备上的 APK 文件 |
| `clear_app_data` | 清除应用数据 |
| `send_sms` | 发送短信 |
| `read_sms` | 读取短信（支持条数限制、仅未读） |
| `get_device_info` | 获取完整设备状态（电池/网络/存储/内存/屏幕/前台应用） |
| `execute_shell` | 执行任意 Shell 命令（支持超时控制） |
| `call_state_event` | 通话状态实时广播（来电/去电/接通/挂断） |

### HTTP Bridge — 新增 7 个端点

`/api/send_sms`、`/api/read_sms`、`/api/get_app_info`、`/api/force_stop_app`、`/api/uninstall_app`、`/api/install_apk`、`/api/clear_app_data`

OpenClaw AI 现在可以通过 HTTP Bridge 直接管理应用、收发短信，无需依赖 Shell 命令。

### 导航重构

- 使用 Jetpack Navigation Compose 重构全局导航，修复返回逻辑异常
- 页面切换动画更流畅

### AI 能力增强

- 高级设置新增模型检测功能，支持验证 API 配置是否有效
- 登录用户自动使用 TutuAI 代理服务启动 OpenClaw，无需手动配置
- MeowAppAiProvider 切换至 Responses API，提升对话质量
- 优化 AI 提示词，常用应用直接映射包名（微信→`com.tencent.mm`），减少无效查询

### 聊天体验

- AI 消息支持 Markdown 渲染
- 表格支持全屏缩放查看、长按复制
- 发送消息后自动隐藏键盘

### 设置与配置

- 新增高级设置页面，支持自定义 API Key 配置
- 设置页添加开源声明卡片
- ADB 配对前增加设置须知弹窗
- 通知权限改为跳转系统设置页面

---

## 修复

- **DeviceInfoCache**：适配新版 `get_device_info` 广播格式（`info` 为 JSON 字符串需二次解析），适配 `list_packages` 新响应格式（`package` 字段替代 `packageName`，无 `label` 字段）
- **SocketCommandBridge**：`queryDeviceInfo` 所有查询类型改用结构化 API 数据源，不再依赖已移除的旧接口
- **控制台 WebView 白屏**：修复 WebView 无法加载的问题
- **终端多语言**：终端屏幕适配多语言，默认英文
- **Socket 未连接提示**：未连接时弹框提示登录，可选仅聊天或去登录
- **Gradle 兼容性**：修复 Gradle DSL 弃用语法警告，禁用 ExpiredTargetSdkVersion lint

---

## 提示词与文档

- OpenClaw SKILL.md 工具数从 21 扩展至 **29**
- 新增应用包名速查表（26 个常用应用），AI 不再传中文名调用 `open_app`
- 新增调用倾向指引：优先使用 `list_packages`/`get_app_info` 等结构化 API，`execute_shell` 作为补充
- 新增 SMS、应用管理完整 curl 示例

---

## 技术变更

- **scrcpy-server** 升级至 0312 版本（148KB → 支持 36 命令）
- **TutuCommands** 精简为 9 个分类，移除设备端未实现的命令定义
- **DeviceInfoCache** 移除独立的 `fetchBatteryInfo()`，电池信息统一从 `get_device_info` 获取
- 大型二进制资源迁移至 Git LFS
- 同步更新 OpenClaw 至 2026.3.8 版本

---

## 相关链接

- **官网 / 登录**：[tutuai.me](https://tutuai.me)
- **项目仓库**：[GitHub - MeowHub](https://github.com/zhaojiaqi/MeowHub)
- **Skill 开发指南**：[docs/skill-development-guide.md](https://github.com/zhaojiaqi/MeowHub/blob/main/docs/skill-development-guide.md)
