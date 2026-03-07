# MeowHub Skill Development Guide

[中文版](#中文版)

This guide covers everything you need to create Skills for MeowHub — from basic structure to advanced patterns.

## Table of Contents

- [Quick Start](#quick-start)
- [Skill JSON Structure](#skill-json-structure)
- [Step Types Reference](#step-types-reference)
- [Variable System](#variable-system)
- [Error Handling](#error-handling)
- [Best Practices](#best-practices)
- [Examples](#examples)

---

## Quick Start

A Skill is a JSON file that defines an automation task. Here's a minimal example:

```json
{
  "name": "hello-world",
  "display_name": "Hello World",
  "display_name_en": "Hello World",
  "version": "1.0.0",
  "author": "your-name",
  "description": "A minimal skill example",
  "icon": "star",
  "category": "tools",
  "tags": ["example"],
  "estimated_time": "5s",
  "estimated_tokens": 200,
  "steps": [
    {
      "id": "check",
      "type": "ai_check",
      "prompt": "Describe what you see on the screen in one sentence.",
      "save_as": "screen_desc"
    },
    {
      "id": "report",
      "type": "ai_summary",
      "prompt": "Report: ${screen_desc}",
      "output": "result"
    }
  ]
}
```

## Skill JSON Structure

### Top-Level Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Unique identifier, kebab-case (e.g., `wechat-auto-reply`) |
| `display_name` | string | Yes | Display name in Chinese |
| `display_name_en` | string | Yes | Display name in English |
| `version` | string | Yes | Semantic version (e.g., `1.0.0`) |
| `author` | string | Yes | Author name |
| `description` | string | Yes | Description in Chinese |
| `description_en` | string | Recommended | Description in English |
| `icon` | string | No | Icon name (e.g., `message-circle`, `play-circle`) |
| `category` | string | Yes | One of: `social`, `entertainment`, `daily`, `shopping`, `system`, `tools`, `productivity` |
| `tags` | string[] | Recommended | Searchable tags |
| `estimated_time` | string | Recommended | Estimated execution time (e.g., `15s`, `5m`) |
| `estimated_tokens` | int | Recommended | Estimated AI token usage |
| `requires` | object | No | Dependencies: `{ "apps": ["com.package.name"] }` |
| `config` | object | No | Reserved for future configuration |
| `steps` | Step[] | Yes | Array of execution steps |

### Step Object

Every step has an `id` (unique within the Skill) and a `type` that determines its behavior.

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique step identifier |
| `type` | string | Step type (see reference below) |
| `label` | string | Display label during execution |
| `save_as` | string | Save step result to a variable |
| `next_step` | string | Override default flow: jump to step ID, `"end"` to finish |
| `on_fail` | object/string | Error handling strategy |

---

## Step Types Reference

### `api` — Execute Device Commands

Sends commands to the device via Socket bridge.

```json
{
  "id": "open_app",
  "type": "api",
  "action": "open_app",
  "params": { "app_name": "com.tencent.mm" }
}
```

**Available actions:**

| Action | Params | Description |
|--------|--------|-------------|
| `open_app` | `{ "app_name": "package.name" }` | Launch an app |
| `press_home` | — | Press Home button |
| `press_back` | — | Press Back button |
| `screenshot` | — | Take a screenshot |
| `click` | `{ "x": 540, "y": 960 }` | Tap at coordinates |
| `type` | `{ "text": "hello" }` | Input text |
| `scroll` | `{ "direction": "down" }` | Scroll (up/down/left/right) |
| `query_device_info` | `{ "type": "all_apps" }` | Query device info |
| `read_ui_text` | `{ "filter": "", "exclude": "" }` | Read UI text content |
| `subscribe_events` | `{ "package": "com.xxx" }` | Subscribe to accessibility events |
| `unsubscribe_events` | — | Unsubscribe from events |
| `accept_call` | — | Accept incoming ringing call |
| `end_call` | — | End active call or reject incoming call |
| `make_call` | `{ "number": "13800138000" }` | Dial a phone number |
| `open_audio_channel` | `{ "mode": "telephony" }` | Open bidirectional audio channel (telephony/voip) |
| `close_audio_channel` | — | Close the audio channel |

### `wait` — Delay

Pauses execution for a specified duration.

```json
{
  "id": "pause",
  "type": "wait",
  "duration": 2000
}
```

| Field | Type | Description |
|-------|------|-------------|
| `duration` | long | Delay in milliseconds (minimum 100ms) |

### `wait_until_changed` — Wait for Screen Change

Waits until the screen content changes (via accessibility events or timeout).

```json
{
  "id": "wait_load",
  "type": "wait_until_changed",
  "timeout": 5000,
  "stable_ms": 800
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `timeout` | long | — | Max wait time in ms (500–10000) |
| `stable_ms` | long | 600 | Additional settle time after change detected |

### `wait_for_event` — Wait for Accessibility Event

Waits for specific accessibility events (e.g., notifications).

```json
{
  "id": "wait_notify",
  "type": "wait_for_event",
  "event_types": ["notification_state_changed"],
  "timeout": 120000,
  "save_as": "notify"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `event_types` | string[] | Event types to listen for |
| `timeout` | long | Max wait time in ms (1000–600000) |

**Available event types:** `window_state_changed`, `window_content_changed`, `notification_state_changed`

Saved event object: `{ eventType, text, packageName, className, activeWindow }`

### `set_var` — Variable Operations

Set, increment, decrement, or append to variables.

```json
{
  "id": "init_counter",
  "type": "set_var",
  "var": "_count",
  "value": "0"
}
```

**Operations (via `op` field):**

| Operation | Description | Example |
|-----------|-------------|---------|
| (empty/assign) | Assign value | `"value": "hello"` |
| `increment` | Add to number | `"op": "increment", "value": "1"` |
| `decrement` | Subtract from number | `"op": "decrement", "value": "1"` |
| `append` | Append to list | `"op": "append", "value": "item"` |

To initialize an empty list, use `"value": []` (JSON array).

### `condition` — Conditional Jump

Evaluates an expression and jumps to a target step.

```json
{
  "id": "check_done",
  "type": "condition",
  "expression": "_i >= _total",
  "goto": "summary",
  "skip_to": "next_item"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `expression` | string | Boolean expression to evaluate |
| `goto` | string | Step ID to jump to if expression is **true** |
| `skip_to` | string | Step ID to jump to if expression is **false** |

**Supported expression operators:** `==`, `!=`, `>`, `<`, `>=`, `<=`, `&&`, `||`, `%` (modulo)

Variables are resolved automatically. Examples:
- `_i >= _total`
- `_count > 0 && _status == "ready"`
- `true` (always jump)

### `prompt_user` — User Input Dialog

Shows a dialog to collect user input before continuing.

```json
{
  "id": "settings",
  "type": "prompt_user",
  "title": "Settings",
  "fields": [
    {
      "key": "count",
      "label": "How many?",
      "type": "select",
      "options": ["3", "5", "10"],
      "default": "5"
    },
    {
      "key": "name",
      "label": "Your name",
      "type": "text",
      "placeholder": "Enter name",
      "default": ""
    }
  ],
  "timeout": 60,
  "timeout_action": "use_default"
}
```

User input is saved to `context["input"]` and also to individual keys. Access via `${input.count}` or `${count}`.

| Field Type | Description |
|------------|-------------|
| `text` | Free text input |
| `select` | Dropdown selection from `options` |

### `ui_locate` — Find and Interact with UI Elements

Locates UI elements by text, resource ID, class name, or content description.

```json
{
  "id": "click_send",
  "type": "ui_locate",
  "text": "发送",
  "action": "click"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `text` | string | Match elements containing this text |
| `text_exact` | string | Match elements with exact text |
| `resource_id` | string | Match by resource ID (partial match) |
| `class_name` | string | Match by class name (partial match) |
| `description` | string | Match by content description (partial match) |
| `desc_exact` | string | Match by exact content description |
| `exclude_text` | string | Exclude elements containing this text |
| `exclude_desc` | string | Exclude elements with this description |
| `index` | int | Select Nth match (0-based) |
| `action` | string | `"click"` to tap the found element |

### `ai_check` — AI Screenshot Analysis

Takes a screenshot and asks the AI to analyze it. Supports branching based on AI response.

```json
{
  "id": "check_state",
  "type": "ai_check",
  "prompt": "Is the app on the home screen? Reply '正常' or '异常'",
  "save_as": "check_result",
  "branch_mode": "keyword",
  "branches": [
    { "match": "正常", "goto": "next_step", "label": "Normal" },
    { "match": "异常", "goto": "handle_error", "label": "Error" }
  ],
  "default_branch": { "goto": "next_step", "label": "Default" }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `prompt` | string | Prompt sent to AI along with screenshot |
| `save_as` | string | Save AI response to variable |
| `branch_mode` | string | `"keyword"` (default) or `"json"` |
| `branches` | Branch[] | Branching rules based on AI response |
| `default_branch` | Branch | Fallback branch if no match |

**Branch object:** `{ "match": "keyword", "goto": "step_id", "label": "description" }`

- **keyword mode:** Checks if AI response contains the `match` string (case-insensitive)
- **json mode:** Parses AI response for `{"status": "value"}` and matches against branches

### `ai_act` — AI Autonomous Action

The most powerful step type. AI analyzes the screenshot and executes actions autonomously in a loop until it calls `finished()`.

```json
{
  "id": "auto_reply",
  "type": "ai_act",
  "prompt": "Find the input box, type 'Hello', and press Send.",
  "max_loops": 8
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `prompt` | string | — | Task description for the AI |
| `max_loops` | int | 8 | Maximum action iterations (1–30) |

The AI uses a Thought/Action format with these available actions:
- `click(point='<point>X Y</point>')` — Tap at normalized coordinates (0–1000)
- `long_press(point='<point>X Y</point>')` — Long press
- `type(content='text')` — Input text
- `scroll(direction='up/down/left/right')` — Scroll
- `press_home()` / `press_back()` — System buttons
- `wait(seconds=N)` — Wait
- `finished(content='summary')` — Mark task as done

The engine maintains action history and feeds it back to the AI to prevent repetitive actions.

### `ai_summary` — AI Summary/Report

Takes a screenshot and generates a text summary. Typically used as the final step to report results.

```json
{
  "id": "report",
  "type": "ai_summary",
  "prompt": "Summarize what was accomplished: ${_log}",
  "output": "result"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `prompt` | string | Summary prompt (supports variable interpolation) |
| `output` | string | `"result"` for final display, `"data"` for internal use |

### `loop` — Loop Execution

Executes a set of sub-steps repeatedly with time and iteration limits.

```json
{
  "id": "main_loop",
  "type": "loop",
  "label": "Processing...",
  "max_duration": 300000,
  "max_iterations": 50,
  "loop_steps": [
    { "id": "step1", "type": "..." },
    { "id": "step2", "type": "..." }
  ]
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `max_duration` | long | 600000 | Max loop duration in ms (1s–1h) |
| `max_iterations` | int | 50 | Max iterations (1–500) |
| `loop_steps` | Step[] | — | Steps to execute in each iteration |

Loop steps can use `next_step: "break"` or return `"break"` to exit the loop early.

### `run_skill` — Execute Sub-Skill

Runs another Skill as a sub-task (fetched from the API).

```json
{
  "id": "run_sub",
  "type": "run_skill",
  "skill": "check-weather"
}
```

---

## Variable System

### Setting Variables

- **`set_var` step:** Explicitly set/modify variables
- **`save_as` field:** Save any step's result to a variable
- **`prompt_user` step:** User input saved to `input.{key}` and individual keys

### Reading Variables

Use `${variable_name}` syntax in any `prompt`, `value`, or text field:

```
"prompt": "You are playing the role of ${input.role}. Reply count: ${_reply_count}"
```

Nested access with dot notation: `${input.duration}`, `${notify.text}`

Lists are auto-formatted as numbered items when interpolated.

### Variable Scope

All variables share a single flat context within a Skill execution. Convention:
- `_prefixed` for internal/counter variables
- `input.xxx` for user-provided values
- Step `save_as` names for step results

---

## Error Handling

### `on_fail` Strategies

Add error handling to any step via the `on_fail` field:

```json
{
  "id": "risky_step",
  "type": "api",
  "action": "open_app",
  "params": { "app_name": "com.example" },
  "on_fail": {
    "strategy": "ai_recover",
    "prompt": "The app failed to open. Try an alternative.",
    "max_attempts": 1
  }
}
```

| Strategy | Description |
|----------|-------------|
| `"skip_with_message"` | Skip step, log `fail_message`, continue |
| `"continue"` | Mark step as done despite error, continue |
| `"retry"` | Retry the step |
| `"ai_recover"` | AI analyzes the screen and attempts recovery |

---

## Best Practices

### 1. Always Verify Screen State

Before performing critical actions, use `ai_check` to verify the app is in the expected state:

```json
{
  "id": "verify_state",
  "type": "ai_check",
  "prompt": "Is the app showing the home screen with no popups?",
  "branches": [
    { "match": "yes", "goto": "proceed" },
    { "match": "no", "goto": "handle_popup" }
  ]
}
```

### 2. Handle Popups

Chinese apps frequently show popups (update prompts, permission requests, ads). Always add popup handling:

```json
{
  "id": "handle_popup",
  "type": "ai_act",
  "prompt": "Close any popups on screen (tap Cancel/Later/Deny/Close)",
  "max_loops": 3
}
```

### 3. Use `wait_until_changed` After Navigation

After opening an app or navigating, wait for the screen to stabilize:

```json
{
  "id": "wait_load",
  "type": "wait_until_changed",
  "timeout": 5000,
  "stable_ms": 800
}
```

### 4. Add Summary at the End

Always include an `ai_summary` step to report what was accomplished:

```json
{
  "id": "summary",
  "type": "ai_summary",
  "prompt": "Report what was done: processed ${_count} items...",
  "output": "result"
}
```

### 5. Minimize Token Usage

- Use `ui_locate` (0 tokens) instead of `ai_act` for simple element interactions
- Use `condition` (0 tokens) instead of `ai_check` for variable-based decisions
- Keep `ai_act` `max_loops` as low as possible
- Write clear, concise prompts to reduce AI confusion and retries

### 6. Go Home When Done

Return to the home screen at the end of your Skill to leave the phone in a clean state:

```json
{
  "id": "go_home",
  "type": "api",
  "action": "press_home"
}
```

---

## Examples

Browse the full collection of official Skills in the [`skills/`](../skills/) directory. Some highlights:

- **[check-weather.json](../skills/check-weather.json)** — Multi-path Skill with fallback logic (widget → app → browser)
- **[wechat-auto-reply.json](../skills/wechat-auto-reply.json)** — Complex event-driven loop with AI replies
- **[browse-tiktok.json](../skills/browse-tiktok.json)** — Counter-based loop with user input and AI analysis

---

<a id="中文版"></a>

# MeowHub Skill 开发指南

本指南详细介绍如何为 MeowHub 创建 Skill——从基础结构到高级模式。

## 目录

- [快速开始](#快速开始)
- [Skill JSON 结构](#skill-json-结构)
- [步骤类型参考](#步骤类型参考)
- [变量系统](#变量系统-1)
- [错误处理](#错误处理-1)
- [最佳实践](#最佳实践-1)
- [示例](#示例-1)

---

## 快速开始

Skill 是一个定义自动化任务的 JSON 文件。最简示例：

```json
{
  "name": "hello-world",
  "display_name": "你好世界",
  "display_name_en": "Hello World",
  "version": "1.0.0",
  "author": "your-name",
  "description": "最简技能示例",
  "icon": "star",
  "category": "tools",
  "tags": ["示例"],
  "estimated_time": "5s",
  "estimated_tokens": 200,
  "steps": [
    {
      "id": "check",
      "type": "ai_check",
      "prompt": "用一句话描述你在屏幕上看到的内容",
      "save_as": "screen_desc"
    },
    {
      "id": "report",
      "type": "ai_summary",
      "prompt": "报告：${screen_desc}",
      "output": "result"
    }
  ]
}
```

## Skill JSON 结构

### 顶层字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 唯一标识符，kebab-case 格式（如 `wechat-auto-reply`） |
| `display_name` | string | 是 | 中文显示名称 |
| `display_name_en` | string | 是 | 英文显示名称 |
| `version` | string | 是 | 语义化版本号（如 `1.0.0`） |
| `author` | string | 是 | 作者名称 |
| `description` | string | 是 | 中文描述 |
| `description_en` | string | 建议 | 英文描述 |
| `icon` | string | 否 | 图标名称（如 `message-circle`、`play-circle`） |
| `category` | string | 是 | 分类：`social`、`entertainment`、`daily`、`shopping`、`system`、`tools`、`productivity` |
| `tags` | string[] | 建议 | 搜索标签 |
| `estimated_time` | string | 建议 | 预估执行时间（如 `15s`、`5m`） |
| `estimated_tokens` | int | 建议 | 预估 AI Token 消耗 |
| `requires` | object | 否 | 依赖：`{ "apps": ["com.package.name"] }` |
| `steps` | Step[] | 是 | 执行步骤数组 |

---

## 步骤类型参考

### `api` — 执行设备命令

通过 Socket 桥接发送设备控制命令。

```json
{
  "id": "open_wechat",
  "type": "api",
  "action": "open_app",
  "params": { "app_name": "com.tencent.mm" }
}
```

**可用的 action：**

| Action | 参数 | 说明 |
|--------|------|------|
| `open_app` | `{ "app_name": "包名" }` | 启动应用 |
| `press_home` | — | 按 Home 键 |
| `press_back` | — | 按返回键 |
| `screenshot` | — | 截图 |
| `click` | `{ "x": 540, "y": 960 }` | 点击坐标 |
| `type` | `{ "text": "内容" }` | 输入文本 |
| `scroll` | `{ "direction": "down" }` | 滑动（up/down/left/right） |
| `query_device_info` | `{ "type": "all_apps" }` | 查询设备信息 |
| `read_ui_text` | `{ "filter": "", "exclude": "" }` | 读取 UI 文本 |
| `subscribe_events` | `{ "package": "com.xxx" }` | 订阅无障碍事件 |
| `unsubscribe_events` | — | 取消事件订阅 |
| `accept_call` | — | 接听来电 |
| `end_call` | — | 挂断通话/拒接来电 |
| `make_call` | `{ "number": "13800138000" }` | 拨打电话 |
| `open_audio_channel` | `{ "mode": "telephony" }` | 开启双向音频通道（telephony/voip） |
| `close_audio_channel` | — | 关闭音频通道 |

### `wait` — 延时等待

```json
{ "id": "pause", "type": "wait", "duration": 2000 }
```

`duration`：毫秒数（最小 100ms）。

### `wait_until_changed` — 等待画面变化

等待屏幕内容发生变化（通过无障碍事件或超时）。

```json
{ "id": "wait_load", "type": "wait_until_changed", "timeout": 5000, "stable_ms": 800 }
```

### `wait_for_event` — 等待无障碍事件

等待特定的无障碍事件（如通知）。

```json
{
  "id": "wait_notify",
  "type": "wait_for_event",
  "event_types": ["notification_state_changed"],
  "timeout": 120000,
  "save_as": "notify"
}
```

保存的事件对象：`{ eventType, text, packageName, className, activeWindow }`

### `set_var` — 变量操作

```json
{ "id": "init", "type": "set_var", "var": "_count", "value": "0" }
{ "id": "inc", "type": "set_var", "var": "_count", "op": "increment", "value": "1" }
{ "id": "add", "type": "set_var", "var": "_log", "op": "append", "value": "新记录" }
```

| 操作（op） | 说明 |
|-----------|------|
| 空/assign | 赋值 |
| `increment` | 数字加 |
| `decrement` | 数字减 |
| `append` | 追加到列表 |

初始化空列表：`"value": []`

### `condition` — 条件跳转

```json
{
  "id": "check_done",
  "type": "condition",
  "expression": "_i >= _total",
  "goto": "summary",
  "skip_to": "next_item"
}
```

`goto`：表达式为 **true** 时跳转；`skip_to`：为 **false** 时跳转。

支持的运算符：`==`、`!=`、`>`、`<`、`>=`、`<=`、`&&`、`||`、`%`

### `prompt_user` — 用户输入

弹出对话框收集用户输入。

```json
{
  "id": "settings",
  "type": "prompt_user",
  "title": "设置",
  "fields": [
    { "key": "count", "label": "数量", "type": "select", "options": ["3", "5", "10"], "default": "5" }
  ],
  "timeout": 60,
  "timeout_action": "use_default"
}
```

用户输入通过 `${input.count}` 访问。

### `ui_locate` — UI 元素定位（0 Token）

通过文本、资源 ID、类名等定位 UI 元素并交互，**不消耗 AI Token**。

```json
{ "id": "click_send", "type": "ui_locate", "text": "发送", "action": "click" }
```

支持的匹配字段：`text`、`text_exact`、`resource_id`、`class_name`、`description`、`desc_exact`、`exclude_text`、`exclude_desc`、`index`

### `ai_check` — AI 截图分析

截图后让 AI 分析并根据回答分支跳转。

```json
{
  "id": "check_state",
  "type": "ai_check",
  "prompt": "当前界面状态如何？回复包含'正常'或'异常'",
  "branches": [
    { "match": "正常", "goto": "proceed", "label": "正常" },
    { "match": "异常", "goto": "fix", "label": "异常" }
  ],
  "default_branch": { "goto": "proceed", "label": "默认" }
}
```

分支模式：`keyword`（默认，包含匹配）或 `json`（解析 `{"status":"值"}`）

### `ai_act` — AI 自主操作

最强大的步骤类型。AI 分析截图并自主执行操作，循环直到调用 `finished()`。

```json
{
  "id": "auto_reply",
  "type": "ai_act",
  "prompt": "找到输入框，输入'你好'，然后点击发送按钮",
  "max_loops": 8
}
```

AI 使用 Thought/Action 格式，可用动作：
- `click(point='<point>X Y</point>')` — 点击（0-1000 归一化坐标）
- `type(content='文本')` — 输入
- `scroll(direction='方向')` — 滑动
- `press_home()` / `press_back()` — 系统按键
- `finished(content='完成说明')` — 标记完成

引擎自动维护操作历史，防止 AI 重复已完成的步骤。

### `ai_summary` — AI 总结

截图并生成文本总结，通常用于最后一步汇报结果。

```json
{ "id": "report", "type": "ai_summary", "prompt": "总结完成情况：${_log}", "output": "result" }
```

### `loop` — 循环执行

重复执行一组子步骤，支持时间和次数限制。

```json
{
  "id": "main_loop",
  "type": "loop",
  "max_duration": 300000,
  "max_iterations": 50,
  "loop_steps": [ ... ]
}
```

循环内的步骤可以用 `next_step: "break"` 退出循环。

### `run_skill` — 嵌套执行子 Skill

```json
{ "id": "sub", "type": "run_skill", "skill": "check-weather" }
```

---

## 变量系统

### 设置变量

- `set_var` 步骤：显式设置/修改
- `save_as` 字段：保存任何步骤的结果
- `prompt_user` 步骤：用户输入保存到 `input.{key}`

### 读取变量

在 `prompt`、`value` 等文本字段中使用 `${变量名}`：

```
"prompt": "你扮演「${input.role}」角色，已回复 ${_reply_count} 条"
```

支持点号嵌套访问：`${input.duration}`、`${notify.text}`

列表变量插值时自动格式化为编号列表。

---

## 错误处理

通过 `on_fail` 字段添加错误处理：

| 策略 | 说明 |
|------|------|
| `skip_with_message` | 跳过步骤，记录 `fail_message`，继续 |
| `continue` | 标记为完成，继续 |
| `retry` | 重试步骤 |
| `ai_recover` | AI 分析屏幕并尝试恢复 |

---

## 最佳实践

1. **操作前验证状态** — 用 `ai_check` 确认界面状态再操作
2. **处理弹窗** — 中国 App 经常弹窗，务必添加弹窗处理逻辑
3. **导航后等待** — 打开 App 或跳转后用 `wait_until_changed` 等待加载
4. **结尾添加总结** — 用 `ai_summary` 汇报执行结果
5. **节省 Token** — 能用 `ui_locate`（0 Token）的不用 `ai_act`，能用 `condition` 的不用 `ai_check`
6. **回到桌面** — Skill 结束时按 Home 键回到桌面

---

## 示例

浏览 [`skills/`](../skills/) 目录查看全部官方 Skill。推荐重点参考：

- **[check-weather.json](../skills/check-weather.json)** — 多路径 Skill，展示 fallback 逻辑（Widget → App → 浏览器）
- **[wechat-auto-reply.json](../skills/wechat-auto-reply.json)** — 复杂的事件驱动循环 + AI 回复
- **[browse-tiktok.json](../skills/browse-tiktok.json)** — 计数循环 + 用户输入 + AI 分析
