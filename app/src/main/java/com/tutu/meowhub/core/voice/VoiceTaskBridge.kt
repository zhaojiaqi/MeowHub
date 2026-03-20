package com.tutu.meowhub.core.voice

import android.util.Log
import com.tutu.meowhub.core.engine.DeviceInfoCache
import com.tutu.meowhub.core.engine.SocketCommandBridge
import com.tutu.meowhub.feature.chat.ChatAgent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 语音 ↔ TuTu AI 桥接层。
 *
 * - 流式检测 ChatResponse 中的 [TASK:...] / [QUERY:...] 标记
 * - 控制 [VoiceAudioGate] 的缓冲释放或丢弃
 * - 任务转发给 [ChatAgent]，结果通过 ChatRAGText 注入豆包播报
 * - 快速查询直接读 [DeviceInfoCache] / [SocketCommandBridge]
 */
class VoiceTaskBridge(
    private val chatAgent: ChatAgent,
    private val deviceCache: DeviceInfoCache,
    private val bridge: SocketCommandBridge,
    private val socket: DoubaoVoiceSocket,
    private val audioGate: VoiceAudioGate
) {
    companion object {
        private const val TAG = "MeowVoice.Bridge"
        private val TASK_PATTERN = Regex("\\[TASK:(.+?)]")
        private val QUERY_PATTERN = Regex("\\[QUERY:(.+?)]")

        private val TASK_ACK_PHRASES = listOf(
            "收到，马上执行",
            "好的，这就去办",
            "明白，开始操作",
            "没问题，交给我",
            "了解，正在处理",
            "好嘞，马上安排",
            "收到指令，开始执行",
            "OK，这就帮你搞定",
            "好的，稍等一下",
            "马上就好，请稍候"
        )
    }

    enum class TaskState { IDLE, EXECUTING, COMPLETED, FAILED }

    data class TaskStatus(
        val state: TaskState = TaskState.IDLE,
        val currentTask: String? = null,
        val progress: String? = null,
        val lastResult: String? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentTaskJob: Job? = null

    private val _status = MutableStateFlow(TaskStatus())
    val status: StateFlow<TaskStatus> = _status.asStateFlow()

    private val textBuffer = StringBuilder()
    @Volatile private var intentDetected = false
    /** RAG 发出后等待豆包播报，此期间跳过标记解析，防止死循环 */
    @Volatile private var awaitingRAGResponse = false
    /** 保存当前指令轮次的 reply_id，用于 ConversationUpdate 修正上下文 */
    @Volatile private var pendingReplyId: String = ""
    /** 等待闲聊 TTS 结束后再发送的确认话术 */
    @Volatile private var pendingAckText: String? = null

    /**
     * ChatResponse 增量 token 到达 — 流式检测标记。
     * 一旦检测到 [TASK: 或 [QUERY:，立即通知 Gate 丢弃闲聊音频。
     */
    fun onChatToken(token: String) {
        if (awaitingRAGResponse) return
        textBuffer.append(token)
        if (!intentDetected) {
            val text = textBuffer.toString()
            if (text.contains("[TASK:") || text.contains("[QUERY:")) {
                intentDetected = true
                audioGate.discard()
                Log.i(TAG, "Intent detected in stream, audio discarded")
            }
        }
    }

    /**
     * ChatEnded — AI 本轮回复完成，做最终判定。
     * [replyId] 用于后续 ConversationUpdate 修正上下文。
     */
    fun onChatCompleted(fullText: String, replyId: String) {
        if (awaitingRAGResponse) {
            Log.d(TAG, "RAG response round, skipping intent parse")
            awaitingRAGResponse = false
            textBuffer.setLength(0)
            intentDetected = false
            return
        }

        val taskMatch = TASK_PATTERN.find(fullText)
        val queryMatch = QUERY_PATTERN.find(fullText)

        when {
            taskMatch != null -> {
                val taskDesc = taskMatch.groupValues[1].trim()
                Log.i(TAG, "[TASK] $taskDesc")
                pendingReplyId = replyId
                handleTask(taskDesc)
            }
            queryMatch != null -> {
                val queryType = queryMatch.groupValues[1].trim()
                Log.i(TAG, "[QUERY] $queryType")
                pendingReplyId = replyId
                handleQuery(queryType)
            }
            else -> {
                if (!intentDetected) {
                    audioGate.release()
                    Log.d(TAG, "Pure chat — audio released")
                }
            }
        }

        textBuffer.setLength(0)
        intentDetected = false
    }

    /**
     * TTSEnded — 一轮 TTS 播放结束。
     * 如果有排队中的任务确认话术，此时发送。
     */
    fun onTtsEnded() {
        val ack = pendingAckText ?: return
        pendingAckText = null
        socket.sendChatTTSText(ack)
        Log.i(TAG, "Task ACK sent after TTS end: $ack")
    }

    /**
     * 用户打断时重置流式检测状态。
     */
    fun onUserInterrupt() {
        textBuffer.setLength(0)
        intentDetected = false
        awaitingRAGResponse = false
        pendingReplyId = ""
        pendingAckText = null
    }

    // ========================== 任务执行 ==========================

    private fun handleTask(taskDescription: String) {
        val current = _status.value
        if (current.state == TaskState.EXECUTING) {
            Log.i(TAG, "Task rejected (busy): $taskDescription, running: ${current.currentTask}")
            val busyMsg = "正在执行「${current.currentTask}」，请等当前任务完成后再下达新指令"
            sendResultAsRAG("任务繁忙", busyMsg)
            return
        }

        pendingAckText = TASK_ACK_PHRASES.random()
        Log.i(TAG, "Task ACK queued: $pendingAckText (waiting for TTS end)")

        _status.value = TaskStatus(TaskState.EXECUTING, taskDescription, null, null)
        updateSocketContext()

        currentTaskJob = scope.launch {
            try {
                Log.i(TAG, "Task started: $taskDescription")
                chatAgent.chat(
                    userMessage = taskDescription,
                    conversationHistory = emptyList(),
                    onToken = { /* 忽略 ChatAgent 的流式文本 */ },
                    onActionStep = { step ->
                        val label = step.description.ifEmpty { step.actionType }
                        _status.value = _status.value.copy(progress = label)
                        Log.d(TAG, "Task step: $label")
                    },
                    onComplete = { result ->
                        Log.i(TAG, "Task completed: $result")
                        _status.value = TaskStatus(TaskState.COMPLETED, null, null, result)
                        sendResultAsRAG("任务结果", result)
                        updateSocketContext()
                    }
                )
            } catch (e: CancellationException) {
                Log.i(TAG, "Task cancelled: $taskDescription")
                _status.value = TaskStatus(TaskState.IDLE, null, "任务已取消", null)
                updateSocketContext()
            } catch (e: Exception) {
                Log.w(TAG, "Task failed: ${e.message}")
                _status.value = TaskStatus(TaskState.FAILED, null, null, "执行失败: ${e.message}")
                sendResultAsRAG("任务失败", "执行「$taskDescription」时出错: ${e.message}")
                updateSocketContext()
            }
        }
    }

    // ========================== 快速查询 ==========================

    private fun handleQuery(queryType: String) {
        scope.launch {
            try {
                val result = bridge.queryDeviceInfo(queryType)
                Log.i(TAG, "Query result ($queryType): $result")
                sendResultAsRAG("手机$queryType 信息", result)
            } catch (e: Exception) {
                Log.w(TAG, "Query failed ($queryType): ${e.message}")
                sendResultAsRAG("查询失败", "${queryType}信息暂时无法获取")
            }
        }
    }

    // ========================== RAG 注入 ==========================

    private fun sendResultAsRAG(title: String, content: String) {
        awaitingRAGResponse = true
        val ragContent = """
以下是手机操作的执行结果，请用自然、简洁的口语告知用户，不要加任何标记：
$content
""".trimIndent()
        socket.sendChatRAGText(listOf(title to ragContent))

        if (pendingReplyId.isNotEmpty()) {
            socket.sendConversationUpdate(pendingReplyId, content)
            Log.d(TAG, "Context fixed: replyId=$pendingReplyId → natural text")
            pendingReplyId = ""
        }
    }

    // ========================== 状态同步 ==========================

    private fun updateSocketContext() {
        val s = _status.value
        socket.taskStateContext = when (s.state) {
            TaskState.IDLE -> "当前无任务在执行"
            TaskState.EXECUTING -> "当前正在执行：${s.currentTask}${if (s.progress != null) "，进度：${s.progress}" else ""}"
            TaskState.COMPLETED -> "上次任务已完成：${s.lastResult ?: "成功"}"
            TaskState.FAILED -> "上次任务失败：${s.lastResult ?: "未知错误"}"
        }
        socket.deviceContext = deviceCache.buildDeviceContext()
        socket.sendUpdateConfig()
    }

    /**
     * 初始化时设置设备上下文。
     */
    fun initContext() {
        socket.deviceContext = deviceCache.buildDeviceContext()
    }

    fun destroy() {
        currentTaskJob?.cancel()
    }
}
