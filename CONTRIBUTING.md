# Contributing to MeowHub

[中文版](#中文版)

Thank you for your interest in contributing to MeowHub! We welcome contributions from everyone, whether it's creating new Skills, improving the codebase, fixing bugs, or improving documentation.

## How to Contribute

### Reporting Bugs

1. Check if the issue already exists in [Issues](https://github.com/zhaojiaqi/MeowHub/issues)
2. Use the **Bug Report** template to create a new issue
3. Include device model, Android version, and steps to reproduce

### Suggesting Features

1. Open a **Feature Request** issue
2. Describe the use case and expected behavior

### Contributing Skills

This is the easiest and most impactful way to contribute! Skills are JSON files that define automation tasks.

**Steps:**

1. Fork the repository
2. Create your Skill JSON in the `skills/` directory
3. Follow the naming convention: `kebab-case.json` (e.g., `auto-send-email.json`)
4. Test your Skill thoroughly on a real device
5. Submit a Pull Request with:
   - A clear description of what the Skill does
   - Which apps/scenarios it targets
   - Test results (device model + Android version)

**Skill Quality Guidelines:**

- Include both `display_name` (Chinese) and `display_name_en` (English)
- Add meaningful `description`, `tags`, and `category`
- Handle edge cases (popups, loading states, app crashes)
- Use `ai_check` before critical operations to verify screen state
- Include `on_fail` handling for important steps
- Set reasonable `timeout` values
- Add `ai_summary` at the end to report results

For detailed documentation, see [Skill Development Guide](docs/skill-development-guide.md).

### Contributing Code

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Follow the existing code style (Kotlin conventions)
4. Write clear commit messages
5. Test on a real device
6. Submit a Pull Request

**Code Style:**

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Keep functions focused and concise
- Add KDoc for public APIs

### Improving Documentation

- Fix typos, improve clarity, add examples
- Translate documentation
- Add diagrams or screenshots

## Development Setup

1. Clone your fork:

```bash
git clone https://github.com/YOUR_USERNAME/MeowHub.git
cd MeowHub
```

2. Set up secrets:

```bash
cp secrets.properties.example secrets.properties
# Edit secrets.properties with your API keys
```

3. Open in Android Studio and sync Gradle

4. Build and run on a device with Android 11+ (for wireless debugging)

## Pull Request Process

1. Update documentation if needed
2. Ensure the build passes: `./gradlew assembleDebug`
3. Fill in the PR template completely
4. A maintainer will review your PR and may request changes

## License

By contributing to MeowHub, you agree that your contributions will be licensed under the [GPL-3.0 License](LICENSE).

---

<a id="中文版"></a>

# 参与 MeowHub 贡献

感谢你有兴趣为 MeowHub 贡献力量！我们欢迎所有人的参与，无论是创建新技能、改进代码、修复 Bug 还是完善文档。

## 如何贡献

### 报告 Bug

1. 先在 [Issues](https://github.com/zhaojiaqi/MeowHub/issues) 中搜索是否已有相同问题
2. 使用 **Bug Report** 模板创建新 Issue
3. 请注明设备型号、Android 版本和复现步骤

### 建议新功能

1. 创建 **Feature Request** Issue
2. 描述使用场景和期望的行为

### 贡献技能（Skill）

这是最简单也最有影响力的贡献方式！技能是定义自动化任务的 JSON 文件。

**步骤：**

1. Fork 本仓库
2. 在 `skills/` 目录下创建你的 Skill JSON 文件
3. 遵循命名规范：`kebab-case.json`（如 `auto-send-email.json`）
4. 在真机上充分测试你的技能
5. 提交 Pull Request，包含：
   - 技能功能的清晰描述
   - 目标应用/场景
   - 测试结果（设备型号 + Android 版本）

**技能质量指南：**

- 同时包含 `display_name`（中文）和 `display_name_en`（英文）
- 添加有意义的 `description`、`tags` 和 `category`
- 处理边界情况（弹窗、加载中、应用崩溃等）
- 在关键操作前使用 `ai_check` 验证屏幕状态
- 为重要步骤添加 `on_fail` 错误处理
- 设置合理的 `timeout` 值
- 在最后添加 `ai_summary` 汇报执行结果

详细文档请查看 [Skill 开发指南](docs/skill-development-guide.md)。

### 贡献代码

1. Fork 本仓库
2. 创建功能分支：`git checkout -b feature/your-feature`
3. 遵循现有代码风格（Kotlin 规范）
4. 编写清晰的提交信息
5. 在真机上测试
6. 提交 Pull Request

**代码规范：**

- 遵循 [Kotlin 编码规范](https://kotlinlang.org/docs/coding-conventions.html)
- 使用有意义的变量和函数命名
- 保持函数功能单一且简洁
- 为公共 API 添加 KDoc 文档

### 完善文档

- 修正错别字、提升表达清晰度、添加示例
- 翻译文档
- 添加图表或截图

## 开发环境搭建

1. 克隆你的 Fork：

```bash
git clone https://github.com/YOUR_USERNAME/MeowHub.git
cd MeowHub
```

2. 配置密钥：

```bash
cp secrets.properties.example secrets.properties
# 编辑 secrets.properties，填入你的 API 密钥
```

3. 用 Android Studio 打开项目并同步 Gradle

4. 在 Android 11+ 设备上编译运行（以支持无线调试）

## Pull Request 流程

1. 如有需要，更新相关文档
2. 确保构建通过：`./gradlew assembleDebug`
3. 完整填写 PR 模板
4. 维护者会审查你的 PR，可能会请求修改

## 许可证

参与 MeowHub 的贡献即表示你同意你的贡献将按照 [GPL-3.0 许可证](LICENSE) 发布。
