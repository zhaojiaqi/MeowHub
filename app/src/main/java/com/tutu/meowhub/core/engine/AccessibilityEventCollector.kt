package com.tutu.meowhub.core.engine

import com.tutu.meowhub.core.socket.TutuSocketClient
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.text.SimpleDateFormat
import java.util.*

data class AccessibilityEvent(
    val eventType: String,
    val timestamp: Long,
    val packageName: String,
    val className: String,
    val activeWindow: String?,
    val text: String?,
    val description: String?,
    val droppedCount: Int?
)

/**
 * 无障碍事件收集器。
 * 监听 TutuSocketClient 的广播消息，将 accessibility_event 存入环形缓冲区，
 * 供 SkillEngine 在 AI 决策时消费。
 */
class AccessibilityEventCollector {

    companion object {
        private const val MAX_BUFFER_SIZE = 10
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        fun formatForPrompt(events: List<AccessibilityEvent>): String {
            if (events.isEmpty()) return ""
            return buildString {
                appendLine("[UI事件]")
                for (e in events) {
                    val time = timeFormat.format(Date(e.timestamp))
                    append("$time ${e.eventType}")
                    e.activeWindow?.let { append(" | $it") }
                        ?: append(" | ${e.className}")
                    e.text?.takeIf { it.isNotEmpty() }?.let { append(" | text=\"$it\"") }
                    e.description?.takeIf { it.isNotEmpty() }?.let { append(" | desc=\"$it\"") }
                    e.droppedCount?.takeIf { it > 0 }?.let { append(" (dropped:$it)") }
                    appendLine()
                }
            }.trimEnd()
        }
    }

    private val buffer = ArrayDeque<AccessibilityEvent>(MAX_BUFFER_SIZE)
    private val mutex = Mutex()
    private var listenJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 用于 waitForEvent 的事件通知通道，容量足够大避免阻塞生产者
    private var eventChannel = Channel<AccessibilityEvent>(Channel.BUFFERED)

    val isActive: Boolean get() = listenJob?.isActive == true

    fun start(messages: SharedFlow<JsonObject>) {
        stop()
        eventChannel = Channel(Channel.BUFFERED)
        listenJob = scope.launch {
            messages.collect { obj ->
                val type = obj["type"]?.jsonPrimitive?.content
                if (type == "accessibility_event") {
                    val event = parseEvent(obj) ?: return@collect
                    mutex.withLock {
                        if (buffer.size >= MAX_BUFFER_SIZE) buffer.removeFirst()
                        buffer.addLast(event)
                    }
                    eventChannel.trySend(event)
                }
            }
        }
    }

    fun stop() {
        listenJob?.cancel()
        listenJob = null
        eventChannel.close()
        // 同步清空缓冲区（stop 通常在协程外调用）
        val cleared = scope.launch {
            mutex.withLock { buffer.clear() }
        }
        runCatching { kotlinx.coroutines.runBlocking { cleared.join() } }
    }

    /**
     * 取出所有已缓冲的事件并清空缓冲区。
     * 保证事件不会混入下一步决策。
     */
    suspend fun collectAndClear(): List<AccessibilityEvent> {
        return mutex.withLock {
            val snapshot = buffer.toList()
            buffer.clear()
            snapshot
        }
    }

    /**
     * 挂起等待满足 [predicate] 的事件，超时返回 null。
     * 先检查缓冲区中已有的事件，再监听新事件。
     */
    suspend fun waitForEvent(
        timeoutMs: Long,
        predicate: (AccessibilityEvent) -> Boolean = { true }
    ): AccessibilityEvent? {
        // 先查缓冲区
        mutex.withLock {
            buffer.lastOrNull { predicate(it) }?.let { return it }
        }

        return try {
            withTimeout(timeoutMs) {
                for (event in eventChannel) {
                    if (predicate(event)) return@withTimeout event
                }
                null
            }
        } catch (_: TimeoutCancellationException) {
            null
        }
    }

    private fun parseEvent(obj: JsonObject): AccessibilityEvent? {
        val eventType = obj["eventType"]?.jsonPrimitive?.content ?: return null
        return AccessibilityEvent(
            eventType = eventType,
            timestamp = obj["timestamp"]?.jsonPrimitive?.long ?: System.currentTimeMillis(),
            packageName = obj["packageName"]?.jsonPrimitive?.content ?: "",
            className = obj["className"]?.jsonPrimitive?.content ?: "",
            activeWindow = obj["activeWindow"]?.jsonPrimitive?.content,
            text = obj["text"]?.jsonPrimitive?.content,
            description = obj["description"]?.jsonPrimitive?.content,
            droppedCount = runCatching { obj["droppedCount"]?.jsonPrimitive?.int }.getOrNull()
        )
    }

    fun destroy() {
        stop()
        scope.cancel()
    }
}
