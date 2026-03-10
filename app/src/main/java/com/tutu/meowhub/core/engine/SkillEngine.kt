package com.tutu.meowhub.core.engine

import android.util.Log
import com.tutu.meowhub.core.model.MeowSkillDetail
import com.tutu.meowhub.core.model.SkillStep
import com.tutu.meowhub.core.model.StepBranch
import com.tutu.meowhub.core.network.MeowHubApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*

/**
 * MeowHub Skill 执行引擎。
 *
 * 设计原则：
 * - 引擎本身不持有任何 UI / Android 依赖
 * - 通过接口（AiProvider、PromptHandler）与外部交互
 * - 通过 StateFlow/SharedFlow 暴露状态，由上层 ViewModel 消费
 */
class SkillEngine(
    private val bridge: SocketCommandBridge,
    private val apiClient: MeowHubApiClient,
    private val aiProviderFactory: (() -> AiProvider?)? = null,
    @Volatile private var promptHandler: PromptHandler? = null,
    private val toolsContextProvider: (() -> String)? = null
) {
    private val aiProvider: AiProvider? get() = aiProviderFactory?.invoke()
    fun setPromptHandler(handler: PromptHandler?) {
        promptHandler = handler
    }
    // ── 状态 ──

    enum class EngineState { IDLE, LOADING, RUNNING, PAUSED, STOPPED, FINISHED, ERROR }
    enum class StepStatus { PENDING, ACTIVE, DONE, ERROR, SKIP }

    data class EngineLog(
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String,
        val message: String,
        val data: String? = null
    )

    data class RunResult(
        val status: String,
        val completedSteps: Int,
        val totalSteps: Int,
        val tokensUsed: Int,
        val errorMessage: String? = null,
        val summaryText: String? = null
    )

    private val _state = MutableStateFlow(EngineState.IDLE)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(-1)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    private val _currentStepLabel = MutableStateFlow("")
    val currentStepLabel: StateFlow<String> = _currentStepLabel.asStateFlow()

    private val _stepStatuses = MutableStateFlow<Map<Int, StepStatus>>(emptyMap())
    val stepStatuses: StateFlow<Map<Int, StepStatus>> = _stepStatuses.asStateFlow()

    private val _logs = MutableSharedFlow<EngineLog>(extraBufferCapacity = 256)
    val logs: SharedFlow<EngineLog> = _logs.asSharedFlow()

    private val _currentSkill = MutableStateFlow<MeowSkillDetail?>(null)
    val currentSkill: StateFlow<MeowSkillDetail?> = _currentSkill.asStateFlow()

    private val _runResult = MutableStateFlow<RunResult?>(null)
    val runResult: StateFlow<RunResult?> = _runResult.asStateFlow()

    @Volatile private var shouldStop = false
    @Volatile private var isPaused = false

    // ── 用户输入回调接口 ──

    interface PromptHandler {
        suspend fun promptUser(step: SkillStep): Map<String, String>
    }

    // ── 控制方法 ──

    fun pause() {
        if (_state.value == EngineState.RUNNING) {
            isPaused = true
            _state.value = EngineState.PAUSED
        }
    }

    fun resume() {
        if (_state.value == EngineState.PAUSED) {
            isPaused = false
            _state.value = EngineState.RUNNING
        }
    }

    fun stop() {
        shouldStop = true
        isPaused = false
        bridge.unsubscribeAccessibilityEvents()
        _state.value = EngineState.STOPPED
    }

    fun reset() {
        shouldStop = false
        isPaused = false
        _state.value = EngineState.IDLE
        _currentStepIndex.value = -1
        _currentStepLabel.value = ""
        _stepStatuses.value = emptyMap()
        _currentSkill.value = null
        _runResult.value = null
    }

    // ── 执行入口 ──

    suspend fun runLocalSkill(skill: MeowSkillDetail) {
        reset()
        _state.value = EngineState.LOADING
        log("STEP", "▶ 加载本地 Skill: ${skill.slug}")

        if (skill.steps.isEmpty()) {
            log("ERR", "Skill 没有步骤")
            _state.value = EngineState.ERROR
            _runResult.value = RunResult("failed", 0, 0, 0, "Skill 没有步骤")
            return
        }

        _currentSkill.value = skill
        _state.value = EngineState.RUNNING
        shouldStop = false

        runSkillInternal(skill)
    }

    suspend fun runInstruction(instruction: String) {
        reset()
        _state.value = EngineState.LOADING
        log("STEP", "▶ 执行指令: ${instruction.take(50)}")

        val syntheticSkill = MeowSkillDetail(
            slug = "_instruction",
            displayName = instruction.take(30),
            steps = listOf(
                SkillStep(
                    id = "ai_act_instruction",
                    type = "ai_act",
                    prompt = instruction,
                    maxLoops = 20,
                    label = instruction.take(30)
                )
            )
        )

        _currentSkill.value = syntheticSkill
        _state.value = EngineState.RUNNING
        shouldStop = false

        runSkillInternal(syntheticSkill)
    }

    suspend fun runSkill(slug: String) {
        reset()
        _state.value = EngineState.LOADING
        log("STEP", "▶ 加载 Skill: $slug")

        val skill: MeowSkillDetail
        try {
            val result = apiClient.fetchSkillDetail(slug)
            skill = result.getOrThrow()
        } catch (e: Exception) {
            log("ERR", "加载失败: ${e.message}")
            _state.value = EngineState.ERROR
            _runResult.value = RunResult("failed", 0, 0, 0, e.message)
            return
        }

        if (skill.steps.isEmpty()) {
            log("ERR", "Skill 没有步骤")
            _state.value = EngineState.ERROR
            _runResult.value = RunResult("failed", 0, 0, 0, "Skill 没有步骤")
            return
        }

        _currentSkill.value = skill
        _state.value = EngineState.RUNNING
        shouldStop = false

        runSkillInternal(skill)
    }

    private suspend fun runSkillInternal(skill: MeowSkillDetail) {
        val context = mutableMapOf<String, Any?>()
        var tokensUsed = 0
        var completedSteps = 0
        var totalIterations = 0
        val aiOutputs = mutableListOf<String>()

        log("STEP", "▶ Skill \"${skill.displayName}\" 开始, ${skill.steps.size} 步骤")

        val targetPkg = extractInitialTargetPackage(skill)
        if (targetPkg != null) {
            val subscribed = bridge.subscribeAccessibilityEvents(
                packages = listOf(targetPkg)
            )
            if (subscribed) {
                log("EVENT", "已订阅无障碍事件: $targetPkg")
            }
        }

        try {
            var i = 0
            while (i < skill.steps.size) {
                totalIterations++
                if (totalIterations > MAX_ITERATIONS) {
                    log("ERR", "超过最大迭代次数 ($MAX_ITERATIONS)，自动停止")
                    shouldStop = true
                    break
                }
                if (shouldStop) break

                while (isPaused && !shouldStop) delay(300)
                if (shouldStop) break

                val step = skill.steps[i]
                _currentStepIndex.value = i
                _currentStepLabel.value = stepDisplayLabel(step)
                updateStepStatus(i, StepStatus.ACTIVE)
                log("STEP", "── [$i] ${step.id} (${step.type}) iter=$totalIterations")

                try {
                    val jumpTarget = executeStep(step, context, skill, i, aiOutputs) { tokens ->
                        tokensUsed += tokens
                    }

                    updateStepStatus(i, StepStatus.DONE)

                    val nextStep = step.nextStep.ifEmpty { jumpTarget ?: "" }
                    if (nextStep.isNotEmpty() && nextStep != "next") {
                        if (nextStep == "end") {
                            log("JUMP", "next_step → end")
                            break
                        }
                        val targetIdx = skill.steps.indexOfFirst { it.id == nextStep }
                        if (targetIdx >= 0 && targetIdx != i + 1) {
                            log("JUMP", "→ \"$nextStep\" (idx=$targetIdx)")
                            if (targetIdx > i) {
                                for (j in (i + 1) until targetIdx) updateStepStatus(j, StepStatus.SKIP)
                            }
                            i = targetIdx
                            completedSteps = i
                            continue
                        }
                    }

                } catch (e: Exception) {
                    val handled = handleStepError(step, e, context, i, skill)
                    if (!handled) {
                        updateStepStatus(i, StepStatus.ERROR)
                        log("ERR", "步骤 ${step.id} 失败: ${e.message}")
                    }
                }

                completedSteps = i + 1
                i++
                delay(STEP_DELAY_MS)
            }

            val status = if (shouldStop) "aborted" else "success"
            val summary = if (!shouldStop) buildSummaryText(aiOutputs, skill) else null
            log("STEP", "Skill 完成: $completedSteps/${skill.steps.size} 步骤, $tokensUsed tokens")
            _currentStepLabel.value = if (shouldStop) "已停止" else "执行完成"
            _state.value = if (shouldStop) EngineState.STOPPED else EngineState.FINISHED
            _runResult.value = RunResult(status, completedSteps, skill.steps.size, tokensUsed, summaryText = summary)

        } catch (e: Exception) {
            log("ERR", "Skill 异常: ${e.message}")
            _state.value = EngineState.ERROR
            _runResult.value = RunResult("failed", completedSteps, skill.steps.size, tokensUsed, e.message)
        } finally {
            bridge.unsubscribeAccessibilityEvents()
        }
    }

    // ── 步骤执行（返回跳转目标 id 或 null） ──

    private suspend fun executeStep(
        step: SkillStep,
        context: MutableMap<String, Any?>,
        skill: MeowSkillDetail,
        stepIndex: Int,
        aiOutputs: MutableList<String>,
        onTokens: (Int) -> Unit
    ): String? {
        return when (step.type) {
            "api" -> executeApiStep(step, context)
            "wait" -> { delay(step.duration.coerceAtLeast(100)); null }
            "wait_until_changed" -> { executeWaitUntilChanged(step); null }
            "wait_for_event" -> { executeWaitForEvent(step, context); null }
            "loop" -> { executeLoop(step, context, skill, aiOutputs, onTokens); null }
            "set_var" -> { executeSetVar(step, context); null }
            "condition" -> executeCondition(step, context, skill)
            "ui_locate" -> { executeUiLocate(step); null }
            "prompt_user" -> { executePromptUser(step, context); null }
            "ai_check" -> executeAiCheck(step, context, skill, aiOutputs, onTokens)
            "ai_act" -> { executeAiAct(step, context, aiOutputs, onTokens); null }
            "ai_summary" -> { executeAiSummary(step, context, aiOutputs, onTokens); null }
            "run_skill" -> { executeRunSkill(step, context, aiOutputs, onTokens); null }
            else -> { log("STEP", "未知步骤类型: ${step.type}，跳过"); null }
        }
    }

    // ── api 步骤 ──

    private suspend fun executeApiStep(
        step: SkillStep,
        context: MutableMap<String, Any?>
    ): String? {
        val action = step.action
        val params = step.params
        log("API", "action=$action", params?.toString())

        val result: String? = when (action) {
            "open_app" -> {
                val appName = params?.get("app_name")?.jsonPrimitive?.contentOrNull
                    ?: params?.get("appName")?.jsonPrimitive?.contentOrNull ?: ""
                bridge.startApp(interpolateVars(appName, context))
                null
            }
            "press_home" -> { bridge.pressHome(); null }
            "press_back" -> { bridge.pressBack(); null }
            "screenshot" -> { bridge.takeScreenshot(); null }
            "click" -> {
                val x = params?.get("x")?.jsonPrimitive?.intOrNull ?: 0
                val y = params?.get("y")?.jsonPrimitive?.intOrNull ?: 0
                bridge.tap(x, y)
                null
            }
            "type" -> {
                val text = params?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
                bridge.typeText(interpolateVars(text, context))
                null
            }
            "scroll" -> {
                val dir = params?.get("direction")?.jsonPrimitive?.contentOrNull ?: "down"
                bridge.scroll(dir)
                null
            }
            "query_device_info" -> {
                val qType = params?.get("type")?.jsonPrimitive?.contentOrNull ?: "apps"
                bridge.queryDeviceInfo(qType)
            }
            "read_ui_text" -> {
                val filter = params?.get("filter")?.jsonPrimitive?.contentOrNull ?: ""
                val exclude = params?.get("exclude")?.jsonPrimitive?.contentOrNull ?: ""
                bridge.readUiText(filter, exclude)
            }
            "subscribe_events" -> {
                val pkg = params?.get("package")?.jsonPrimitive?.contentOrNull?.let { interpolateVars(it, context) } ?: ""
                val packages = if (pkg.isNotEmpty()) listOf(pkg) else emptyList()
                bridge.subscribeAccessibilityEvents(packages)
                log("EVENT", "subscribe_events(${pkg.ifEmpty { "all" }})")
                null
            }
            "unsubscribe_events" -> {
                bridge.unsubscribeAccessibilityEvents()
                log("EVENT", "unsubscribe_events()")
                null
            }
            else -> {
                log("API", "未知 action: $action")
                null
            }
        }

        if (step.saveAs.isNotEmpty() && result != null) {
            context[step.saveAs] = result
            log("VAR", "save_as: ${step.saveAs}", result.take(200))
        }

        return null
    }

    // ── set_var ──

    private fun executeSetVar(step: SkillStep, context: MutableMap<String, Any?>) {
        val varName = step.varName
        if (varName.isEmpty()) return

        val oldVal = context[varName]
        val rawValue = step.value

        when (step.op) {
            "increment" -> {
                val inc = rawValue?.jsonPrimitive?.intOrNull ?: 1
                context[varName] = ((context[varName] as? Number)?.toInt() ?: 0) + inc
            }
            "decrement" -> {
                val dec = rawValue?.jsonPrimitive?.intOrNull ?: 1
                context[varName] = ((context[varName] as? Number)?.toInt() ?: 0) - dec
            }
            "append" -> {
                val str = rawValue?.jsonPrimitive?.contentOrNull?.let { interpolateVars(it, context) } ?: ""
                val existing = context[varName]
                val list = if (existing is MutableList<*>) {
                    @Suppress("UNCHECKED_CAST")
                    existing as MutableList<Any?>
                } else {
                    mutableListOf()
                }
                list.add(str)
                context[varName] = list
            }
            else -> {
                if (rawValue is JsonArray) {
                    context[varName] = mutableListOf<Any?>()
                } else {
                    val strVal = rawValue?.jsonPrimitive?.contentOrNull?.let { interpolateVars(it, context) }
                    if (strVal != null && strVal.isEmpty()) {
                        context[varName] = ""
                    } else {
                        val numVal = strVal?.toDoubleOrNull()
                        context[varName] = if (numVal != null && numVal == numVal.toLong().toDouble()) {
                            numVal.toLong()
                        } else numVal ?: strVal
                    }
                }
            }
        }

        log("VAR", "$varName: $oldVal → ${context[varName]} (op=${step.op.ifEmpty { "assign" }})")
    }

    // ── condition ──

    private fun executeCondition(
        step: SkillStep,
        context: MutableMap<String, Any?>,
        skill: MeowSkillDetail
    ): String? {
        val expr = step.expression
        val condResult = evaluateExpression(expr, context)
        log("COND", "\"$expr\" → $condResult")

        return if (condResult && step.goto.isNotEmpty()) {
            log("JUMP", "true → goto \"${step.goto}\"")
            step.goto
        } else if (!condResult && step.skipTo.isNotEmpty()) {
            log("JUMP", "false → skip_to \"${step.skipTo}\"")
            step.skipTo
        } else {
            null
        }
    }

    private fun evaluateExpression(expr: String, context: Map<String, Any?>): Boolean {
        return try {
            // 先处理 JS fallback 模式: (expr || default) → 解析 expr，如果为 0/null 则用 default
            var preprocessed = expr.replace(
                Regex("\\(\\s*(?:parseInt|Number)\\s*\\([^)]*\\)\\s*\\|\\|\\s*([^)]+)\\)")
            ) { m ->
                val fallback = m.groupValues[1].trim()
                val inner = m.value
                    .removePrefix("(").removeSuffix(")")
                    .split("||")[0].trim()
                val innerCleaned = inner
                    .replace(Regex("parseInt\\s*\\(\\s*([^,)]+)(?:,\\s*\\d+)?\\s*\\)")) { it.groupValues[1].trim() }
                    .replace(Regex("Number\\s*\\(\\s*([^)]+)\\s*\\)")) { it.groupValues[1].trim() }
                val resolved = resolveVariable(innerCleaned, context)
                val num = resolved.toDoubleOrNull()
                if (num == null || num == 0.0) fallback else resolved
            }

            // Handle && and || by splitting into sub-expressions
            if (preprocessed.contains("&&")) {
                return preprocessed.split("&&").all { evaluateExpression(it.trim(), context) }
            }
            if (preprocessed.contains("||")) {
                return preprocessed.split("||").any { evaluateExpression(it.trim(), context) }
            }

            // Strip JS functions: parseInt(...) → inner value, .toString() etc.
            var cleaned = preprocessed
                .replace(Regex("parseInt\\s*\\(\\s*([^,)]+)(?:,\\s*\\d+)?\\s*\\)")) { it.groupValues[1].trim() }
                .replace(Regex("Number\\s*\\(\\s*([^)]+)\\s*\\)")) { it.groupValues[1].trim() }
                .replace("===", "==")
                .replace("!==", "!=")

            // Resolve variables
            val resolved = cleaned.replace(Regex("[a-zA-Z_][a-zA-Z0-9_.]*")) { match ->
                resolveVariable(match.value, context)
            }

            // Evaluate % (modulo) first — replace "a % b" with result
            val withModulo = resolved.replace(Regex("(\\d+(?:\\.\\d+)?)\\s*%\\s*(\\d+(?:\\.\\d+)?)")) { m ->
                val a = m.groupValues[1].toDouble()
                val b = m.groupValues[2].toDouble()
                if (b == 0.0) "0" else (a.toLong() % b.toLong()).toString()
            }

            when {
                withModulo.contains(">=") -> {
                    val (l, r) = withModulo.split(">=", limit = 2).map { it.trim().toDoubleOrNull() ?: 0.0 }
                    l >= r
                }
                withModulo.contains("<=") -> {
                    val (l, r) = withModulo.split("<=", limit = 2).map { it.trim().toDoubleOrNull() ?: 0.0 }
                    l <= r
                }
                withModulo.contains("!=") -> {
                    val (l, r) = withModulo.split("!=", limit = 2).map { it.trim().removeSurrounding("\"") }
                    l != r
                }
                withModulo.contains("==") -> {
                    val (l, r) = withModulo.split("==", limit = 2).map { it.trim().removeSurrounding("\"") }
                    l == r
                }
                withModulo.contains(">") -> {
                    val (l, r) = withModulo.split(">", limit = 2).map { it.trim().toDoubleOrNull() ?: 0.0 }
                    l > r
                }
                withModulo.contains("<") -> {
                    val (l, r) = withModulo.split("<", limit = 2).map { it.trim().toDoubleOrNull() ?: 0.0 }
                    l < r
                }
                withModulo.trim() == "true" -> true
                withModulo.trim() == "false" -> false
                withModulo.trim().toDoubleOrNull()?.let { it != 0.0 } == true -> true
                else -> false
            }
        } catch (e: Exception) {
            log("ERR", "表达式求值失败: $expr - ${e.message}")
            false
        }
    }

    private fun resolveVariable(path: String, context: Map<String, Any?>): String {
        if (path == "true" || path == "false") return path
        val keys = path.split(".")
        var value: Any? = context
        for (k in keys) {
            value = when (value) {
                is Map<*, *> -> value[k]
                else -> null
            }
        }
        return when (value) {
            is Number -> value.toString()
            is String -> value.toDoubleOrNull()?.toString() ?: "\"$value\""
            is List<*> -> value.size.toString()
            null -> "0"
            else -> value.toString()
        }
    }

    // ── ui_locate ──

    private suspend fun executeUiLocate(step: SkillStep) {
        val hasAdvancedQuery = step.description.isNotEmpty() || step.descExact.isNotEmpty()
                || step.textExact.isNotEmpty() || step.excludeText.isNotEmpty()
                || step.excludeDesc.isNotEmpty() || step.index > 0

        if (!hasAdvancedQuery) {
            if (step.text.isNotEmpty()) {
                val action = step.action.ifEmpty { "click" }
                if (action == "click") {
                    bridge.clickByText(step.text, step.index)
                } else {
                    bridge.findElement(text = step.text)
                }
                return
            } else if (step.resourceId.isNotEmpty()) {
                bridge.clickById(step.resourceId, step.index)
                return
            } else if (step.className.isNotEmpty()) {
                bridge.findElement(className = step.className)
                return
            }
        }

        val resp = bridge.getUiNodes()
        if (resp == null) throw Exception("UI nodes unavailable")

        val nodeList = resp["nodes"]?.jsonArray
            ?: resp["children"]?.jsonArray
            ?: throw Exception("UI nodes: no nodes/children array in response")

        val cachedScreen = bridge.deviceCache.screenSize.value
        log("API", "ui_locate getUiNodes → count=${nodeList.size}, screen=${cachedScreen.first}x${cachedScreen.second}")

        val matches = mutableListOf<JsonObject>()
        for (element in nodeList) {
            val node = element.jsonObject
            if (matchUiNode(node, step)) matches.add(node)
        }

        val matchIndex = step.index.coerceAtLeast(0)
        val found = matches.getOrNull(matchIndex)
        if (found == null) {
            val sample = nodeList.take(5).joinToString(" | ") { n ->
                val o = n.jsonObject
                val t = o["text"]?.jsonPrimitive?.contentOrNull ?: ""
                val d = o["desc"]?.jsonPrimitive?.contentOrNull
                    ?: o["contentDescription"]?.jsonPrimitive?.contentOrNull ?: ""
                val c = o["cls"]?.jsonPrimitive?.contentOrNull
                    ?: o["className"]?.jsonPrimitive?.contentOrNull ?: ""
                "\"$t\" desc=\"$d\" cls=$c"
            }
            log("ERR", "ui_locate NOT FOUND (${matches.size} matches). First 5: $sample")
            throw Exception("UI node not found: text=${step.text} desc=${step.description} descExact=${step.descExact}")
        }

        val nodeText = found["text"]?.jsonPrimitive?.contentOrNull
            ?: found["contentDescription"]?.jsonPrimitive?.contentOrNull ?: ""
        val rawBounds = found["bounds"]?.jsonArray ?: found["b"]?.jsonArray
        val rawCenter = found["center"]?.jsonArray ?: found["c"]?.jsonArray
        log("API", "ui_locate FOUND[$matchIndex/${matches.size}]: \"$nodeText\" bounds=$rawBounds center=$rawCenter")

        if (step.action == "click") {
            val center = found["center"]?.jsonArray ?: found["c"]?.jsonArray
            val bounds = found["bounds"]?.jsonArray ?: found["b"]?.jsonArray
            var cx: Int? = null
            var cy: Int? = null

            if (center != null && center.size >= 2) {
                cx = center[0].jsonPrimitive.int
                cy = center[1].jsonPrimitive.int
            } else if (bounds != null && bounds.size >= 4) {
                val l = bounds[0].jsonPrimitive.int
                val t = bounds[1].jsonPrimitive.int
                val r = bounds[2].jsonPrimitive.int
                val b = bounds[3].jsonPrimitive.int
                cx = (l + r) / 2
                cy = (t + b) / 2
            }

            if (cx != null && cy != null) {
                log("API", "ui_locate → tap($cx, $cy)")
                bridge.tap(cx, cy)
            } else {
                log("ERR", "ui_locate: no coordinates for click, keys=${found.keys}")
            }
        }
    }

    private fun matchUiNode(node: JsonObject, step: SkillStep): Boolean {
        val nodeText = node["text"]?.jsonPrimitive?.contentOrNull ?: ""
        val nodeDesc = node["desc"]?.jsonPrimitive?.contentOrNull
            ?: node["contentDescription"]?.jsonPrimitive?.contentOrNull ?: ""
        val nodeId = node["id"]?.jsonPrimitive?.contentOrNull
            ?: node["resourceId"]?.jsonPrimitive?.contentOrNull ?: ""
        val nodeClass = node["cls"]?.jsonPrimitive?.contentOrNull
            ?: node["className"]?.jsonPrimitive?.contentOrNull ?: ""

        val hasQuery = step.text.isNotEmpty() || step.textExact.isNotEmpty()
                || step.resourceId.isNotEmpty() || step.className.isNotEmpty()
                || step.description.isNotEmpty() || step.descExact.isNotEmpty()
        if (!hasQuery) return false

        if (step.text.isNotEmpty() && !nodeText.contains(step.text)) return false
        if (step.textExact.isNotEmpty() && nodeText != step.textExact) return false
        if (step.resourceId.isNotEmpty() && !nodeId.contains(step.resourceId)) return false
        if (step.className.isNotEmpty() && !nodeClass.contains(step.className)) return false
        if (step.description.isNotEmpty() && !nodeDesc.contains(step.description)) return false
        if (step.descExact.isNotEmpty() && nodeDesc != step.descExact) return false
        if (step.excludeText.isNotEmpty() && nodeText.contains(step.excludeText)) return false
        if (step.excludeDesc.isNotEmpty() && nodeDesc.contains(step.excludeDesc)) return false
        return true
    }

    // ── prompt_user ──

    private suspend fun executePromptUser(step: SkillStep, context: MutableMap<String, Any?>) {
        val handler = promptHandler
        if (handler == null) {
            log("INPUT", "无 PromptHandler，使用默认值")
            val defaults = mutableMapOf<String, String>()
            step.fields.forEach { f ->
                defaults[f.key] = f.default.ifEmpty {
                    step.defaultValues?.get(f.key)?.jsonPrimitive?.contentOrNull ?: ""
                }
            }
            context.putAll(defaults)
            context["input"] = defaults
            return
        }

        val values = handler.promptUser(step)
        log("INPUT", "用户输入:", values.toString())
        context.putAll(values)
        context["input"] = values
    }

    // ── ai_check ──

    private suspend fun executeAiCheck(
        step: SkillStep,
        context: MutableMap<String, Any?>,
        skill: MeowSkillDetail,
        aiOutputs: MutableList<String>,
        onTokens: (Int) -> Unit
    ): String? {
        val ai = aiProvider ?: run {
            log("ERR", "AI 未配置，跳过 ai_check")
            return null
        }

        var prompt = interpolateVars(step.prompt, context)
        if (step.branches.isNotEmpty()) {
            prompt = buildBranchPrompt(prompt, step)
        }
        prompt += buildEventsContext()

        val screenshot = bridge.takeCompressedScreenshot()
        val aiText = ai.analyze(prompt, screenshot)
        onTokens(400)
        aiOutputs.add(aiText)

        if (step.saveAs.isNotEmpty()) {
            var saveVal = aiText
            val fcm = Regex("finished\\s*\\(\\s*content\\s*=\\s*['\"](.+?)['\"]\\s*\\)", RegexOption.DOT_MATCHES_ALL).find(saveVal)
            if (fcm != null) {
                saveVal = fcm.groupValues[1].trim()
            } else {
                val cleaned = saveVal
                    .replace(Regex("^Thought:[\\s\\S]*?Action:\\s*", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("^finished\\s*\\(.*\\)\\s*", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (cleaned.isNotEmpty()) saveVal = cleaned
            }
            context[step.saveAs] = saveVal
            log("VAR", "ai_check save_as: ${step.saveAs} = \"${saveVal.take(50)}\"")
        }

        if (step.branches.isNotEmpty()) {
            val matched = matchBranch(step, aiText)
            val target = matched?.goto ?: step.defaultBranch?.goto ?: "next"
            val label = matched?.label ?: step.defaultBranch?.label ?: "默认"
            log("BRANCH", "matched=${matched?.match ?: "none"} → target=$target label=$label")

            return when (target) {
                "next" -> null
                "end" -> {
                    shouldStop = true
                    null
                }
                else -> target
            }
        }

        return null
    }

    // ── ai_act ──

    private data class ActHistoryEntry(val action: String, val result: String)

    private suspend fun executeAiAct(
        step: SkillStep,
        context: MutableMap<String, Any?>,
        aiOutputs: MutableList<String>,
        onTokens: (Int) -> Unit
    ) {
        val ai = aiProvider ?: run {
            log("ERR", "AI 未配置，跳过 ai_act")
            return
        }

        val maxLoops = step.maxLoops.coerceIn(1, 30)
        var loopCount = 0
        val actHistory = mutableListOf<ActHistoryEntry>()

        while (loopCount < maxLoops && !shouldStop) {
            while (isPaused && !shouldStop) delay(300)

            loopCount++
            log("STEP", "ai_act loop $loopCount/$maxLoops")

            var prompt = interpolateVars(step.prompt, context) + buildEventsContext()
            prompt += AI_ACT_FORMAT_INSTRUCTION
            toolsContextProvider?.invoke()?.let { toolsCtx ->
                if (toolsCtx.isNotBlank()) prompt += toolsCtx
            }
            if (actHistory.isNotEmpty()) {
                val recent = actHistory.takeLast(10)
                val offset = actHistory.size - recent.size
                prompt += "\n\n--- 你之前在本次任务中已执行的操作记录 ---\n"
                if (offset > 0) prompt += "(前${offset}步已省略)\n"
                recent.forEachIndexed { i, h ->
                    prompt += "第${offset + i + 1}步: ${h.action} → 结果: ${h.result}\n"
                }
                prompt += "--- 请基于以上记录继续操作，不要重复已完成的步骤 ---\n"
            }

            val screenshot = bridge.takeCompressedScreenshot()
            log("AI", "ai_act screenshot=${if (screenshot != null) "${screenshot.length} chars" else "NULL"}")
            val aiText = ai.analyze(prompt, screenshot)
            onTokens(600)
            aiOutputs.add(aiText)

            log("AI", "ai_act raw response (${aiText.length} chars): ${aiText.take(200)}")

            val parsed = parseAiResponse(aiText)
            log("AI", "ai_act parsed → action=${parsed.actionType}, point=${parsed.point}, content=\"${parsed.content.take(50)}\"")

            actHistory.add(ActHistoryEntry(
                action = "${parsed.actionType}(${parsed.content.take(30)})",
                result = if (parsed.actionType == "finished") "完成" else parsed.thought.take(50)
            ))

            if (parsed.actionType == "finished") {
                log("STEP", "ai_act finished after $loopCount loops")
                break
            }

            executeAiAction(parsed)
            smartWaitAfterAction(parsed)
        }
    }

    // ── ai_summary ──

    private suspend fun executeAiSummary(
        step: SkillStep,
        context: MutableMap<String, Any?>,
        aiOutputs: MutableList<String>,
        onTokens: (Int) -> Unit
    ) {
        val ai = aiProvider ?: run {
            log("ERR", "AI 未配置，跳过 ai_summary")
            return
        }

        val prompt = interpolateVars(step.prompt, context) + buildEventsContext()
        val screenshot = bridge.takeCompressedScreenshot()
        val aiText = ai.analyze(prompt, screenshot)
        onTokens(400)
        aiOutputs.add(aiText)

        if (step.output == "data") {
            context["_summaryData"] = aiText
        }
    }

    // ── run_skill (子 Skill) ──

    private suspend fun executeRunSkill(
        step: SkillStep,
        context: MutableMap<String, Any?>,
        aiOutputs: MutableList<String>,
        onTokens: (Int) -> Unit
    ) {
        val subSlug = step.skill
        if (subSlug.isEmpty()) return

        log("STEP", "运行子 Skill: $subSlug")
        val subSkill = apiClient.fetchSkillDetail(subSlug).getOrThrow()

        for (subStep in subSkill.steps) {
            if (shouldStop) break
            while (isPaused && !shouldStop) delay(300)
            executeStep(subStep, context, subSkill, 0, aiOutputs, onTokens)
        }
    }

    // ── AI 响应解析 & 动作执行 ──

    data class ParsedAiAction(
        val thought: String = "",
        val actionType: String = "finished",
        val point: Pair<Int, Int>? = null,
        val startPoint: Pair<Int, Int>? = null,
        val endPoint: Pair<Int, Int>? = null,
        val text: String = "",
        val direction: String = "",
        val appName: String = "",
        val content: String = "",
        val question: String = "",
        val seconds: Int = 1,
        val queryType: String = ""
    )

    private fun parseAiResponse(response: String): ParsedAiAction {
        var thought = ""
        val thoughtMatch = Regex("Thought:\\s*(.+?)(?=\\s*Action:|$)", RegexOption.DOT_MATCHES_ALL)
            .find(response)
        if (thoughtMatch != null) thought = thoughtMatch.groupValues[1].trim()

        val actionMatch = Regex("Action:\\s*(.+)", RegexOption.DOT_MATCHES_ALL).find(response)
        if (actionMatch == null) {
            return ParsedAiAction(thought = thought, actionType = "finished", content = thought)
        }

        var actionStr = actionMatch.groupValues[1].trim()
        if (!actionStr.endsWith(")")) actionStr += ")"
        val funcMatch = Regex("^(\\w+)\\((.*)\\)$", RegexOption.DOT_MATCHES_ALL).find(actionStr)
            ?: return ParsedAiAction(thought = thought, actionType = "finished", content = actionStr)

        val actionType = funcMatch.groupValues[1]
        val paramsStr = funcMatch.groupValues[2]

        val points = Regex("(?:point|start_point|end_point)='<point>(\\d+)\\s+(\\d+)</point>'?")
            .findAll(paramsStr).toList()

        val point = points.firstOrNull()?.let {
            it.groupValues[1].toInt() to it.groupValues[2].toInt()
        }
        val endPoint = if (points.size >= 2) {
            points[1].groupValues[1].toInt() to points[1].groupValues[2].toInt()
        } else null

        val direction = Regex("direction='(\\w+)'").find(paramsStr)?.groupValues?.get(1) ?: ""
        val content = Regex("content='([^']*)'").find(paramsStr)?.groupValues?.get(1) ?: ""
        val appName = Regex("(?:app_name|package)='([^']*)'").find(paramsStr)?.groupValues?.get(1) ?: ""
        val question = Regex("question='([^']*)'").find(paramsStr)?.groupValues?.get(1) ?: ""
        val seconds = Regex("seconds=(\\d+)").find(paramsStr)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val queryType = Regex("type='([^']*)'").find(paramsStr)?.groupValues?.get(1) ?: "apps"

        return ParsedAiAction(
            thought = thought,
            actionType = actionType,
            point = point,
            startPoint = point,
            endPoint = endPoint,
            text = content,
            direction = direction,
            appName = appName,
            content = content,
            question = question,
            seconds = seconds,
            queryType = queryType
        )
    }

    private suspend fun executeAiAction(action: ParsedAiAction) {
        val (sw, sh) = bridge.deviceCache.screenSize.value
        log("AI", "executeAiAction: ${action.actionType}, screen=${sw}x${sh}, point=${action.point}, dir=${action.direction}")
        when (action.actionType) {
            "click" -> {
                val (px, py) = action.point ?: return
                val x = px * sw / 1000
                val y = py * sh / 1000
                log("AI", "click: ($px,$py)/1000 → tap($x,$y)")
                bridge.tap(x, y)
            }
            "long_press" -> {
                val (px, py) = action.point ?: return
                val x = px * sw / 1000
                val y = py * sh / 1000
                log("AI", "long_press: ($px,$py)/1000 → tap($x,$y)")
                bridge.tap(x, y)
            }
            "type" -> bridge.typeText(action.content)
            "scroll" -> {
                val dir = action.direction.ifEmpty { "down" }
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
            "open_app" -> bridge.startApp(action.appName)
            "press_home" -> bridge.pressHome()
            "press_back" -> bridge.pressBack()
            "wait" -> { /* smartWaitAfterAction handles the delay */ }
            "query_device_info" -> bridge.queryDeviceInfo(action.queryType)
            "finished" -> { /* handled by caller */ }
            else -> log("API", "未知 AI action: ${action.actionType}")
        }
    }

    // ── 分支匹配 ──

    private fun matchBranch(step: SkillStep, aiText: String): StepBranch? {
        if (step.branches.isEmpty()) return null

        if (step.branchMode == "json") {
            val jsonMatch = Regex("\\{[\\s\\S]*?\"status\"\\s*:\\s*\"([^\"]+)\"[\\s\\S]*?\\}")
                .find(aiText)
            if (jsonMatch != null) {
                val status = jsonMatch.groupValues[1].lowercase()
                return step.branches.find { it.match.lowercase() == status }
            }
            return null
        }

        val text = aiText.lowercase()
        return step.branches.find { text.contains(it.match.lowercase()) }
    }

    private fun buildBranchPrompt(basePrompt: String, step: SkillStep): String = buildString {
        append(basePrompt)
        append("\n\n")
        if (step.branchMode == "json") {
            append("请在回复末尾输出JSON判断结果，格式 {\"status\":\"值\"}，值必须是以下之一：\n")
        } else {
            append("【重要】请在回复中必须包含以下关键词之一来表示你的判断结果：\n")
        }
        step.branches.forEach { br ->
            append("- \"${br.match}\" 表示 ${br.label.ifEmpty { br.match }}\n")
        }
        if (step.branchMode != "json") {
            append("请确保回复中包含且仅包含上述关键词之一。")
        }
    }

    // ── 错误处理 ──

    private suspend fun handleStepError(
        step: SkillStep,
        error: Exception,
        context: MutableMap<String, Any?>,
        stepIndex: Int,
        skill: MeowSkillDetail
    ): Boolean {
        val failStrategy = when (val onFail = step.onFail) {
            is JsonPrimitive -> onFail.contentOrNull
            is JsonObject -> onFail["strategy"]?.jsonPrimitive?.contentOrNull
            else -> null
        }

        return when (failStrategy) {
            "skip_with_message" -> {
                updateStepStatus(stepIndex, StepStatus.SKIP)
                log("STEP", "跳过失败步骤: ${step.failMessage.ifEmpty { error.message ?: "" }}")
                true
            }
            "continue" -> {
                updateStepStatus(stepIndex, StepStatus.DONE)
                true
            }
            "retry" -> {
                log("STEP", "重试步骤 ${step.id}")
                true
            }
            "ai_recover" -> {
                val ai = aiProvider
                if (ai != null) {
                    log("STEP", "AI 恢复中...")
                    val screenshot = bridge.takeCompressedScreenshot()
                    val eventsCtx = buildEventsContext()
                    val prompt = "步骤 \"${step.id}\" 执行失败: ${error.message}。请观察当前屏幕，尝试修复操作。$eventsCtx"
                    val aiText = ai.analyze(prompt, screenshot)
                    val parsed = parseAiResponse(aiText)
                    if (parsed.actionType != "finished") {
                        executeAiAction(parsed)
                    }
                    updateStepStatus(stepIndex, StepStatus.DONE)
                    true
                } else false
            }
            else -> false
        }
    }

    // ── 无障碍事件辅助 ──

    /**
     * 从 Skill 步骤中提取第一个 open_app 的目标包名。
     */
    private fun extractInitialTargetPackage(skill: MeowSkillDetail): String? {
        for (step in skill.steps) {
            if (step.type == "api" && step.action == "open_app") {
                val appName = step.params?.get("app_name")?.jsonPrimitive?.contentOrNull
                    ?: step.params?.get("appName")?.jsonPrimitive?.contentOrNull
                if (!appName.isNullOrEmpty()) {
                    return bridge.deviceCache.findPackageByName(appName)
                }
            }
        }
        return null
    }

    /**
     * 收集缓冲区中的无障碍事件并格式化为 prompt 片段。
     * 无事件时返回空字符串。
     */
    private suspend fun buildEventsContext(): String {
        val events = bridge.collectAccessibilityEvents()
        if (events.isEmpty()) return ""
        val formatted = AccessibilityEventCollector.formatForPrompt(events)
        log("EVENT", "注入 ${events.size} 条事件到 AI 上下文")
        return "\n\n$formatted"
    }

    /**
     * 操作后智能等待：根据动作类型动态调整等待时间。
     * 有无障碍订阅时用事件驱动提前结束等待，否则 fallback 到固定延时。
     */
    private suspend fun smartWaitAfterAction(action: ParsedAiAction) {
        val (baseDelayMs, eventTimeoutMs) = when (action.actionType) {
            "wait" -> 0L to (action.seconds * 1000L)
            "open_app" -> 300L to 3000L
            "press_home", "press_back" -> 300L to 1500L
            "type" -> 800L to 2000L
            else -> 300L to 2000L
        }

        if (baseDelayMs > 0) delay(baseDelayMs)

        if (bridge.isAccessibilitySubscribed) {
            val event = bridge.waitForAccessibilityEvent(eventTimeoutMs) {
                it.eventType in WAIT_EVENT_TYPES
            }
            if (event != null) {
                log("EVENT", "UI变化: ${event.eventType} ${event.activeWindow ?: event.className}")
            }
            delay(200)
        } else {
            delay(if (action.actionType == "wait") action.seconds * 1000L else 700L)
        }
    }

    /**
     * 事件驱动版 wait_until_changed，带 fallback。
     */
    private suspend fun executeWaitUntilChanged(step: SkillStep) {
        val timeout = step.timeout.coerceIn(500, 10000)
        if (bridge.isAccessibilitySubscribed) {
            val event = bridge.waitForAccessibilityEvent(timeout) {
                it.eventType in listOf("window_state_changed", "window_content_changed")
            }
            if (event != null) {
                log("EVENT", "画面变化: ${event.eventType} ${event.activeWindow ?: event.className}")
                delay(step.stableMs.coerceAtLeast(200))
            } else {
                log("EVENT", "等待超时(${timeout}ms)，继续执行")
            }
        } else {
            delay(timeout)
        }
    }

    private suspend fun executeWaitForEvent(step: SkillStep, context: MutableMap<String, Any?>) {
        val eventTypes = step.eventTypes
        val timeout = step.timeout.coerceIn(1000, 600000)
        log("EVENT", "wait_for_event: types=$eventTypes, timeout=${timeout}ms")

        if (bridge.isAccessibilitySubscribed) {
            val predicate: (AccessibilityEvent) -> Boolean =
                if (eventTypes.isNotEmpty()) { e -> e.eventType in eventTypes } else { _ -> true }
            val event = bridge.waitForAccessibilityEvent(timeout, predicate)
            if (event != null) {
                log("EVENT", "收到事件: ${event.eventType} | ${event.activeWindow ?: event.className} | text=\"${event.text ?: ""}\"")
                if (step.saveAs.isNotEmpty()) {
                    context[step.saveAs] = mapOf(
                        "eventType" to event.eventType,
                        "text" to (event.text ?: ""),
                        "packageName" to event.packageName,
                        "className" to event.className,
                        "activeWindow" to (event.activeWindow ?: "")
                    )
                }
            } else {
                log("EVENT", "wait_for_event 超时(${timeout}ms)")
                if (step.saveAs.isNotEmpty()) context[step.saveAs] = ""
            }
        } else {
            log("EVENT", "无订阅，fallback delay(${timeout}ms)")
            delay(timeout)
            if (step.saveAs.isNotEmpty()) context[step.saveAs] = ""
        }
    }

    private suspend fun executeLoop(
        step: SkillStep,
        context: MutableMap<String, Any?>,
        skill: MeowSkillDetail,
        aiOutputs: MutableList<String>,
        onTokens: (Int) -> Unit
    ) {
        val maxDuration = step.maxDuration.coerceIn(1000, 3600000)
        val maxIter = step.maxIterations.coerceIn(1, 500)
        val loopSteps = step.loopSteps
        val startTime = System.currentTimeMillis()
        var iteration = 0

        log("STEP", "loop \"${step.id}\": max_duration=${maxDuration}ms, max_iter=$maxIter, steps=${loopSteps.size}")

        while (iteration < maxIter &&
            System.currentTimeMillis() - startTime < maxDuration &&
            !shouldStop
        ) {
            iteration++
            while (isPaused && !shouldStop) delay(300)
            if (shouldStop) break

            log("STEP", "loop iter $iteration/$maxIter, elapsed=${System.currentTimeMillis() - startTime}ms")

            var breakLoop = false
            var si = 0
            while (si < loopSteps.size) {
                if (shouldStop || breakLoop) break
                val subStep = loopSteps[si]
                log("STEP", "  loop>${subStep.id} (${subStep.type})")
                try {
                    val jumpTarget = executeStep(subStep, context, skill, 0, aiOutputs, onTokens)
                    if (jumpTarget == "break" || subStep.nextStep == "break") {
                        breakLoop = true
                        break
                    }
                    if (jumpTarget != null && jumpTarget != "next") {
                        val targetIdx = loopSteps.indexOfFirst { it.id == jumpTarget }
                        if (targetIdx >= 0) {
                            si = targetIdx
                            continue
                        }
                    }
                } catch (e: Exception) {
                    val failStrategy = when (val onFail = subStep.onFail) {
                        is JsonPrimitive -> onFail.contentOrNull
                        is JsonObject -> onFail["strategy"]?.jsonPrimitive?.contentOrNull
                        else -> null
                    }
                    if (failStrategy == "continue") {
                        log("STEP", "loop>${subStep.id} failed (continue): ${e.message}")
                    } else {
                        log("ERR", "loop>${subStep.id} error: ${e.message}")
                    }
                }
                si++
            }
            if (breakLoop) break
        }
        log("STEP", "loop \"${step.id}\" ended: $iteration iters, ${System.currentTimeMillis() - startTime}ms elapsed")
    }

    companion object {
        private const val MAX_ITERATIONS = 200
        private const val STEP_DELAY_MS = 500L
        private val WAIT_EVENT_TYPES = listOf(
            "window_state_changed",
            "window_content_changed",
            "notification_state_changed"
        )

        private const val AI_ACT_FORMAT_INSTRUCTION = """

你必须严格按以下格式回复，不要使用其他格式：

Thought: <你的分析思考>
Action: <action_name>(<params>)

可用 Action：
- open_app(package='包名') — 打开应用（优先使用此方式打开应用）
- click(point='<point>X Y</point>') — 点击坐标（0-1000 归一化坐标）
- long_press(point='<point>X Y</point>') — 长按
- type(content='要输入的文字') — 输入文本
- scroll(direction='up/down/left/right') — 滑动
- press_home() — 按Home键
- press_back() — 按返回键
- wait(seconds=N) — 等待N秒
- finished(content='完成说明') — 任务完成

示例：
Thought: 我看到了「赞」按钮在屏幕右侧中间位置
Action: click(point='<point>890 450</point>')

重要：坐标使用 0-1000 归一化范围，左上角为(0,0)，右下角为(1000,1000)。
"""
    }

    // ── 工具方法 ──

    private fun interpolateVars(text: String, context: Map<String, Any?>): String {
        if (text.isEmpty()) return text
        return text.replace(Regex("\\$\\{([\\w.]+)\\}")) { match ->
            val path = match.groupValues[1]
            val keys = path.split(".")
            var value: Any? = context
            for (k in keys) {
                value = when (value) {
                    is Map<*, *> -> value[k]
                    else -> return@replace match.value
                }
            }
            when (value) {
                null -> match.value
                is List<*> -> value.mapIndexed { i, v -> "${i + 1}. $v" }.joinToString("\n")
                else -> value.toString()
            }
        }
    }

    private fun updateStepStatus(index: Int, status: StepStatus) {
        _stepStatuses.value = _stepStatuses.value.toMutableMap().apply { put(index, status) }
    }

    private fun log(tag: String, message: String, data: String? = null) {
        Log.d("SkillEngine", "[$tag] $message${if (data != null) " | $data" else ""}")
        _logs.tryEmit(EngineLog(tag = tag, message = message, data = data))
    }

    private fun stepDisplayLabel(step: SkillStep): String {
        val typeLabel = when (step.type) {
            "api" -> step.action.ifEmpty { "API" }
            "wait" -> "等待"
            "wait_until_changed" -> "等待画面变化"
            "wait_for_event" -> "等待事件"
            "loop" -> "定时循环"
            "set_var" -> "设置变量"
            "condition" -> "条件判断"
            "ui_locate" -> "定位元素"
            "prompt_user" -> step.title.ifEmpty { "等待用户输入" }
            "ai_check" -> "AI 分析"
            "ai_act" -> step.label.ifEmpty { "AI 操作" }
            "ai_summary" -> "AI 总结"
            "run_skill" -> "运行子 Skill"
            else -> step.type
        }
        return step.label.ifEmpty { typeLabel }
    }

    private fun buildSummaryText(aiOutputs: List<String>, skill: MeowSkillDetail): String {
        for (i in aiOutputs.indices.reversed()) {
            val text = aiOutputs[i]
            val finMatch = Regex("finished\\s*\\(\\s*content\\s*=\\s*['\"]([\\s\\S]*?)['\"]\\s*\\)").find(text)
            if (finMatch != null) return finMatch.groupValues[1].trim()
        }

        for (i in aiOutputs.indices.reversed()) {
            val clean = aiOutputs[i]
                .replace(Regex("```[\\s\\S]*?```"), "")
                .replace(Regex("Thought:\\s*```[\\s\\S]*?```"), "")
                .replace(Regex("Action:\\s*.+$", RegexOption.MULTILINE), "")
                .replace(Regex("Thought:\\s*"), "")
                .trim()
            if (clean.length > 10) return clean.take(200)
        }

        return "${skill.displayName} 已完成所有步骤。"
    }

}
