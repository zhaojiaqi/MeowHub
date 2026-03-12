package com.tutu.meowhub.feature.chat

import android.content.Context
import android.util.Log
import com.tutu.meowhub.core.engine.AiProvider
import com.tutu.meowhub.core.engine.DeviceInfoCache
import com.tutu.meowhub.core.engine.SocketCommandBridge
import com.tutu.meowhub.core.model.ActionStep
import com.tutu.meowhub.core.model.ActionStepStatus
import com.tutu.meowhub.core.network.MeowHubApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatAgent(
    private val bridge: SocketCommandBridge,
    private val aiProviderFactory: () -> AiProvider?,
    private val apiClient: MeowHubApiClient,
    private val deviceCache: DeviceInfoCache,
    private val context: Context
) {
    companion object {
        private const val TAG = "ChatAgent"
        private const val MAX_AGENT_STEPS = 15
    }

    enum class AgentState { IDLE, THINKING, EXECUTING, FINISHED }

    private val _agentState = MutableStateFlow(AgentState.IDLE)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _currentActionLabel = MutableStateFlow<String?>(null)
    val currentActionLabel: StateFlow<String?> = _currentActionLabel.asStateFlow()

    @Volatile
    private var shouldStop = false

    fun stop() {
        shouldStop = true
        _agentState.value = AgentState.IDLE
        _currentActionLabel.value = null
        com.tutu.meowhub.MeowApp.instance.updateChatActionLabel(null)
    }

    suspend fun chat(
        userMessage: String,
        conversationHistory: List<AiProvider.AiMessage>,
        availableSkills: List<SkillInfo> = emptyList(),
        onToken: (String) -> Unit,
        onActionStep: (ActionStep) -> Unit,
        onComplete: (String) -> Unit
    ) {
        val ai = aiProviderFactory() ?: run {
            onComplete("AI 未配置，请先登录或配置 API Key")
            return
        }

        shouldStop = false
        _agentState.value = AgentState.THINKING

        val systemPrompt = buildSystemPrompt(availableSkills)
        val history = mutableListOf<AiProvider.AiMessage>()
        history.add(AiProvider.AiMessage("system", systemPrompt))
        history.addAll(conversationHistory)
        history.add(AiProvider.AiMessage("user", userMessage))

        try {
            val fullResponse = StringBuilder()
            val responseText = ai.analyze(
                prompt = userMessage,
                screenshotBase64 = bridge.takeCompressedScreenshot(),
                history = history.dropLast(1),
                onToken = { token ->
                    fullResponse.append(token)
                    onToken(token)
                }
            )

            val finalText = if (fullResponse.isNotEmpty()) fullResponse.toString() else responseText
            Log.d(TAG, "AI response (len=${finalText.length}): ${finalText.take(300)}")

            if (!containsAction(finalText)) {
                Log.d(TAG, "No action detected, pure chat response")
                _agentState.value = AgentState.FINISHED
                _currentActionLabel.value = null
                com.tutu.meowhub.MeowApp.instance.updateChatActionLabel(null)
                onComplete(finalText)
                return
            }

            _agentState.value = AgentState.EXECUTING
            val agentHistory = mutableListOf<String>()
            agentHistory.add(finalText)
            var currentResponse = finalText
            var stepCount = 0

            while (stepCount < MAX_AGENT_STEPS && !shouldStop) {
                stepCount++
                val parsed = parseAiResponse(currentResponse)
                Log.d(TAG, "Parsed action: type=${parsed.actionType}, appName=${parsed.appName}, point=${parsed.point}, content=${parsed.content}")

                if (parsed.actionType == "finished") {
                    _agentState.value = AgentState.FINISHED
                    _currentActionLabel.value = null
                    com.tutu.meowhub.MeowApp.instance.updateChatActionLabel(null)
                    onComplete(parsed.content.ifEmpty { currentResponse })
                    return
                }

                if (parsed.actionType == "run_skill") {
                    val step = ActionStep(stepCount, "run_skill", "运行技能: ${parsed.skill}", ActionStepStatus.RUNNING)
                    onActionStep(step)
                    _currentActionLabel.value = "运行技能: ${parsed.skill}"
                    try {
                        val engine = com.tutu.meowhub.MeowApp.instance.skillEngine
                        engine.runSkill(parsed.skill)
                        onActionStep(step.copy(status = ActionStepStatus.DONE))
                    } catch (e: Exception) {
                        onActionStep(step.copy(status = ActionStepStatus.ERROR, detail = e.message ?: ""))
                    }
                    _agentState.value = AgentState.FINISHED
                    _currentActionLabel.value = null
                    com.tutu.meowhub.MeowApp.instance.updateChatActionLabel(null)
                    onComplete("已执行技能: ${parsed.skill}")
                    return
                }

                val actionDesc = formatActionDescription(parsed)
                val step = ActionStep(stepCount, parsed.actionType, actionDesc, ActionStepStatus.RUNNING)
                onActionStep(step)
                _currentActionLabel.value = actionDesc
                com.tutu.meowhub.MeowApp.instance.updateChatActionLabel(actionDesc)

                try {
                    Log.d(TAG, "Executing action: ${parsed.actionType}")
                    executeAction(parsed)
                    Log.d(TAG, "Action executed successfully: ${parsed.actionType}")
                    onActionStep(step.copy(status = ActionStepStatus.DONE))
                } catch (e: Exception) {
                    Log.w(TAG, "Action failed: ${parsed.actionType} - ${e.message}", e)
                    onActionStep(step.copy(status = ActionStepStatus.ERROR, detail = e.message ?: ""))
                }

                val actionDelay = if (parsed.actionType == "open_app") 3000L else 2000L
                delay(actionDelay)

                if (shouldStop) break

                _agentState.value = AgentState.THINKING
                _currentActionLabel.value = "AI 思考中..."
                com.tutu.meowhub.MeowApp.instance.updateChatActionLabel("AI 思考中...")

                val screenshot = bridge.takeCompressedScreenshot()
                history.add(AiProvider.AiMessage("assistant", currentResponse))

                val stepResponse = StringBuilder()
                currentResponse = ai.analyze(
                    prompt = "继续执行任务，观察当前屏幕状态。如果任务已完成，使用 finished(content='完成说明')。",
                    screenshotBase64 = screenshot,
                    history = history,
                    onToken = { token ->
                        stepResponse.append(token)
                        onToken(token)
                    }
                )
                currentResponse = if (stepResponse.isNotEmpty()) stepResponse.toString() else currentResponse
                agentHistory.add(currentResponse)

                _agentState.value = AgentState.EXECUTING
            }

            _agentState.value = AgentState.FINISHED
            _currentActionLabel.value = null
            com.tutu.meowhub.MeowApp.instance.updateChatActionLabel(null)
            onComplete(currentResponse)

        } catch (e: Exception) {
            Log.e(TAG, "Chat error: ${e.message}", e)
            _agentState.value = AgentState.IDLE
            _currentActionLabel.value = null
            com.tutu.meowhub.MeowApp.instance.updateChatActionLabel(null)
            onComplete("出错了: ${e.message}")
        }
    }

    private fun buildSystemPrompt(availableSkills: List<SkillInfo>): String = buildString {
        appendLine(loadSoulPrompt())
        appendLine()
        appendLine("## 当前设备信息")
        appendLine(deviceCache.buildDeviceContext())
        appendLine()
        appendLine("## 你的能力")

        val apps = deviceCache.installedApps.value
        val appListBlock = if (apps.isNotEmpty()) {
            buildString {
                appendLine()
                appendLine("**已安装应用包名映射（open_app 必须使用 package 列的值）：**")
                apps.forEach { appendLine("  ${it.label} → ${it.packageName}") }
            }
        } else ""

        appendLine("""
你可以通过以下方式帮助用户：

### 1. 直接回答（优先）
以下情况必须直接用文字回复，绝对不要操控手机：
- 知识问答：天气、新闻、百科、翻译、数学计算、编程问题等一切你能凭自身知识回答的问题
- 聊天闲聊：日常对话、情感交流、讲笑话、讲故事
- 建议咨询：推荐餐厅、推荐电影、旅行规划等
- 信息查询：查天气、查汇率、查时间、股票行情等（直接用你的知识回答，不需要打开任何APP）

### 2. 操控手机（仅在用户明确要求时）
只有当用户明确要求你在手机上执行操作时，才使用 Action。例如：
- "打开微信" / "帮我打开抖音" → 操控手机
- "给张三发条消息" → 操控手机
- "截个图" / "返回桌面" → 操控手机
- "明天天气怎么样" → ❌ 不操控手机，直接回答
- "帮我查一下北京到上海的火车" → ❌ 不操控手机，直接回答

操控手机时使用以下格式：

Thought: <你的分析>
Action: <action_name>(<params>)

可用 Action：
- click(point='<point>X Y</point>') — 点击坐标（0-1000 归一化坐标）
- long_press(point='<point>X Y</point>') — 长按
- type(content='要输入的文字') — 输入文本
- scroll(direction='up/down/left/right') — 滑动
- open_app(app_name='包名') — 打开应用，app_name 必须是 Android 包名（如 com.tencent.mm），绝对不能传中文应用名
- press_home() — 按Home键
- press_back() — 按返回键
- wait(seconds=N) — 等待N秒
- finished(content='完成说明') — 任务完成

坐标使用 0-1000 归一化范围，左上角为(0,0)，右下角为(1000,1000)。
$appListBlock
### 3. 调用现有技能
当用户需求匹配现有 Skill 时，可以直接调用：

Thought: 用户需要的功能已有现成 Skill
Action: run_skill(slug='skill-slug')
        """.trimIndent())

        if (availableSkills.isNotEmpty()) {
            appendLine()
            appendLine("## 可用技能列表")
            availableSkills.forEach { skill ->
                appendLine("- `${skill.slug}`: ${skill.displayName} — ${skill.description}")
            }
        }

        appendLine()
        appendLine("## 重要规则")
        appendLine("- 【最重要】判断是否需要操控手机的标准：用户是否明确要求你在手机上做某个操作。如果用户只是提问、咨询、聊天，直接用文字回答，不要使用任何 Action")
        appendLine("- 查天气、查信息、问问题等一切知识类需求，直接回答，不要去操作手机上的浏览器或APP")
        appendLine("- 执行操作时每次只做一个 Action，等待下一轮截图确认结果")
        appendLine("- 发消息的完整流程：打开APP → 找到联系人并点击进入聊天 → 点击输入框 → type输入文字 → 点击发送按钮 → finished。每一步都不能跳过！")
        appendLine("- type 输入文字之前，必须先用 click 点击输入框让它获得焦点，否则输入不会生效")
        appendLine("- 不要在操作进行到一半时就使用 finished，必须完成用户要求的全部步骤（包括点击发送、确认等）才能 finished")
        appendLine("- 任务完成后必须使用 finished(content='...') 告知用户")
        appendLine("- open_app 的 app_name 参数只接受 Android 包名（如 com.tencent.mm），绝对不可以传中文名")
        appendLine("- 始终使用中文回复")
        appendLine()
        appendLine("## 车票/订票场景的回复风格")
        appendLine("- 当用户查询火车、高铁、车票等信息时，你有操控手机的能力，可以帮用户打开 12306 等应用直接订票")
        appendLine("- 因此回复末尾**不要**说「建议你打开铁路12306APP查看实时信息」之类的话")
        appendLine("- 而应主动提示：「如果需要，我可以帮你直接订最近一趟高铁」或类似表述，让用户知道你可以代为操作")
    }

    private fun loadSoulPrompt(): String {
        return try {
            context.assets.open("meowhub-workspace/SOUL.md").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "你是图图 (TuTu)，MeowHub 的 AI 助手。你住在手机里，能看屏幕、能操控手机、能帮用户完成各种任务。性格靠谱、温暖、高效。始终使用中文回复。"
        }
    }

    private fun containsAction(text: String): Boolean {
        val found = Regex("Action:\\s*\\w+\\(", RegexOption.IGNORE_CASE).containsMatchIn(text)
        Log.d(TAG, "containsAction=$found for text: ${text.take(100)}")
        return found
    }

    data class ParsedAction(
        val thought: String = "",
        val actionType: String = "finished",
        val point: Pair<Int, Int>? = null,
        val startPoint: Pair<Int, Int>? = null,
        val endPoint: Pair<Int, Int>? = null,
        val content: String = "",
        val direction: String = "",
        val appName: String = "",
        val skill: String = "",
        val seconds: Int = 1,
        val queryType: String = ""
    )

    private fun parseAiResponse(response: String): ParsedAction {
        var thought = ""
        Regex("Thought:\\s*(.+?)(?=\\s*Action:|$)", RegexOption.DOT_MATCHES_ALL)
            .find(response)?.let { thought = it.groupValues[1].trim() }

        val actionMatch = Regex("Action:\\s*(.+)", RegexOption.DOT_MATCHES_ALL).find(response)
            ?: return ParsedAction(thought = thought, actionType = "finished", content = thought)

        var actionStr = actionMatch.groupValues[1].trim()
        if (!actionStr.endsWith(")")) actionStr += ")"
        val funcMatch = Regex("^(\\w+)\\((.*)\\)$", RegexOption.DOT_MATCHES_ALL).find(actionStr)
            ?: return ParsedAction(thought = thought, actionType = "finished", content = actionStr)

        val actionType = funcMatch.groupValues[1]
        val paramsStr = funcMatch.groupValues[2]
        Log.d(TAG, "parseAiResponse: actionType=$actionType, paramsStr=$paramsStr")

        val points = Regex("(?:point|start_point|end_point)=['\"]?<point>(\\d+)\\s+(\\d+)</point>['\"]?")
            .findAll(paramsStr).toList()
        val point = points.firstOrNull()?.let {
            it.groupValues[1].toInt() to it.groupValues[2].toInt()
        }
        val endPoint = if (points.size >= 2) {
            points[1].groupValues[1].toInt() to points[1].groupValues[2].toInt()
        } else null

        val direction = extractParam(paramsStr, "direction")
        val content = extractParam(paramsStr, "content")
        val appName = extractParam(paramsStr, "app_name")
        val skill = extractParam(paramsStr, "slug")
        val seconds = Regex("seconds=(\\d+)").find(paramsStr)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val queryType = extractParam(paramsStr, "type").ifEmpty { "apps" }

        return ParsedAction(
            thought = thought,
            actionType = actionType,
            point = point,
            startPoint = point,
            endPoint = endPoint,
            content = content,
            direction = direction,
            appName = appName,
            skill = skill,
            seconds = seconds,
            queryType = queryType
        )
    }

    private fun extractParam(paramsStr: String, key: String): String {
        // Match key='value' or key="value"
        val singleQuote = Regex("$key='([^']*)'").find(paramsStr)?.groupValues?.get(1)
        if (singleQuote != null) return singleQuote
        val doubleQuote = Regex("""$key="([^"]*)"""").find(paramsStr)?.groupValues?.get(1)
        if (doubleQuote != null) return doubleQuote
        // Match key=value (unquoted, up to comma or paren)
        val unquoted = Regex("$key=([^,)]+)").find(paramsStr)?.groupValues?.get(1)?.trim()
        if (unquoted != null) return unquoted
        return ""
    }

    private suspend fun executeAction(action: ParsedAction) {
        Log.d(TAG, "executeAction: ${action.actionType}, appName='${action.appName}', point=${action.point}")
        val (sw, sh) = bridge.deviceCache.screenSize.value
        when (action.actionType) {
            "click" -> {
                val (px, py) = action.point ?: run {
                    Log.w(TAG, "click action but no point provided")
                    return
                }
                bridge.tap(px * sw / 1000, py * sh / 1000)
            }
            "long_press" -> {
                val (px, py) = action.point ?: run {
                    Log.w(TAG, "long_press action but no point provided")
                    return
                }
                bridge.tap(px * sw / 1000, py * sh / 1000)
            }
            "type" -> {
                Log.d(TAG, "typing: '${action.content}'")
                bridge.typeText(action.content)
            }
            "scroll" -> {
                val dir = action.direction.ifEmpty { "down" }
                Log.d(TAG, "scrolling: $dir")
                bridge.scroll(dir)
            }
            "drag" -> {
                val sp = action.startPoint ?: return
                val ep = action.endPoint ?: return
                bridge.swipe(
                    sp.first * sw / 1000, sp.second * sh / 1000,
                    ep.first * sw / 1000, ep.second * sh / 1000, 350
                )
            }
            "open_app" -> {
                Log.d(TAG, "open_app: name='${action.appName}'")
                if (action.appName.isNotEmpty()) {
                    bridge.startApp(action.appName)
                } else {
                    Log.w(TAG, "open_app called with empty app_name")
                }
            }
            "press_home" -> bridge.pressHome()
            "press_back" -> bridge.pressBack()
            "wait" -> delay(action.seconds * 1000L)
            "query_device_info" -> bridge.queryDeviceInfo(action.queryType)
            else -> Log.w(TAG, "Unknown action type: ${action.actionType}")
        }
    }

    private fun formatActionDescription(action: ParsedAction): String {
        return when (action.actionType) {
            "click" -> "点击 (${action.point?.first}, ${action.point?.second})"
            "long_press" -> "长按 (${action.point?.first}, ${action.point?.second})"
            "type" -> "输入: ${action.content.take(20)}"
            "scroll" -> "滑动: ${action.direction}"
            "open_app" -> "打开: ${action.appName}"
            "press_home" -> "按主页键"
            "press_back" -> "按返回键"
            "wait" -> "等待 ${action.seconds}s"
            "run_skill" -> "运行技能: ${action.skill}"
            "finished" -> "完成"
            else -> action.actionType
        }
    }

    data class SkillInfo(
        val slug: String,
        val displayName: String,
        val description: String
    )
}
