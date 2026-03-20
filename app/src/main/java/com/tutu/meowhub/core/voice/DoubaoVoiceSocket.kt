package com.tutu.meowhub.core.voice

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 语音 WebSocket 连接配置 */
sealed class VoiceConnectionConfig {
    /** 用户自有 Key，直连豆包官方 */
    data class Direct(val appId: String, val accessKey: String) : VoiceConnectionConfig()
    /** TutuAI 中转代理，用 MeowApp access_token 认证 */
    data class Proxy(val accessToken: String) : VoiceConnectionConfig()
}

/**
 * 豆包实时语音 WebSocket 管理器。
 *
 * 状态机：IDLE → CONNECTING → SENT_START_CONNECTION → SENT_START_SESSION → READY → IDLE
 *
 * 支持两种连接模式：
 * - [VoiceConnectionConfig.Direct]：直连豆包官方 WebSocket
 * - [VoiceConnectionConfig.Proxy]：通过 TutuAI 中转代理连接
 *
 * 通过 [VoiceSocketCallback] 回调通知外部关键事件。
 */
class DoubaoVoiceSocket(
    private val config: VoiceConnectionConfig,
    private val callback: VoiceSocketCallback
) {
    companion object {
        private const val TAG = "MeowVoice.Socket"
        private const val DIRECT_URL = "wss://openspeech.bytedance.com/api/v3/realtime/dialogue"
        private const val PROXY_URL = "wss://tutuai.me/ws/doubao-realtime"
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_BASE_DELAY_MS = 1_000L
    }

    interface VoiceSocketCallback {
        fun onReady()
        /** TTS 音频数据到达（PCM 24kHz 16bit mono） */
        fun onAudioReceived(pcmData: ByteArray)
        /** TTSSentenceStart — 携带 tts_type 供 AudioGate 判断 */
        fun onTtsSentenceStart(ttsType: String)
        /** TTSEnded — 一轮 TTS 结束 */
        fun onTtsEnded()
        /** ASRInfo — 用户开始说话 */
        fun onUserSpeaking()
        /** ChatResponse 增量文本（流式），供 Bridge 实时检测标记 */
        fun onChatToken(token: String)
        /** ChatEnded — AI 本轮回复完成，传出完整文本和 replyId（用于 ConversationUpdate） */
        fun onChatCompleted(fullText: String, replyId: String)
        /** 豆包识别到用户退出意图 */
        fun onExitDetected()
        fun onError(message: String)
        fun onDisconnected()
    }

    private enum class Phase { IDLE, CONNECTING, SENT_START_CONNECTION, SENT_START_SESSION, READY }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sendExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    @Volatile private var phase: Phase = Phase.IDLE
    @Volatile private var isStopped = true
    private var hasOpened = false
    private var reconnectAttempts = 0
    private var connectTimeoutRunnable: Runnable? = null
    private var sessionId: String = ""
    private val aiResponseBuffer = StringBuilder()
    private var currentReplyId: String = ""

    /** 外部注入的设备上下文和任务状态，用于构建 system_role */
    var deviceContext: String = ""
    var taskStateContext: String = "当前无任务在执行"

    val isReady: Boolean get() = phase == Phase.READY

    /**
     * 建立 WebSocket 连接，开始握手流程。
     */
    @Synchronized
    fun connect() {
        if (phase != Phase.IDLE) {
            Log.w(TAG, "connect() called but phase=$phase, ignoring")
            return
        }
        isStopped = false
        hasOpened = false
        reconnectAttempts = 0
        sessionId = UUID.randomUUID().toString()
        phase = Phase.CONNECTING

        client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

        val request = when (config) {
            is VoiceConnectionConfig.Direct -> {
                Log.d(TAG, "Connecting DIRECT to Doubao")
                Request.Builder()
                    .url(DIRECT_URL)
                    .addHeader("X-Api-App-ID", config.appId)
                    .addHeader("X-Api-Access-Key", config.accessKey)
                    .addHeader("X-Api-Resource-Id", "volc.speech.dialog")
                    .addHeader("X-Api-App-Key", "PlgvMymc7f3tQnJ6")
                    .addHeader("X-Api-Connect-Id", UUID.randomUUID().toString())
                    .build()
            }
            is VoiceConnectionConfig.Proxy -> {
                Log.d(TAG, "Connecting via TutuAI PROXY")
                Request.Builder()
                    .url("$PROXY_URL?api_key=${config.accessToken}")
                    .addHeader("X-Api-Connect-Id", UUID.randomUUID().toString())
                    .build()
            }
        }

        webSocket = client!!.newWebSocket(request, socketListener)
        scheduleConnectTimeout()
    }

    /**
     * 发送 PCM 音频数据（16kHz 16bit mono）。
     * 线程安全，内部通过单线程 Executor 串行发送。
     */
    fun sendAudio(pcmData: ByteArray) {
        if (isStopped || phase != Phase.READY) return
        sendExecutor.submit {
            try {
                if (phase != Phase.READY || webSocket == null) return@submit
                val compressed = RealtimeProtocol.gzipCompress(pcmData)
                val baos = ByteArrayOutputStream()
                baos.write(
                    RealtimeProtocol.generateHeader(
                        RealtimeProtocol.PROTOCOL_VERSION,
                        RealtimeProtocol.CLIENT_AUDIO_ONLY_REQUEST,
                        RealtimeProtocol.MSG_WITH_EVENT,
                        RealtimeProtocol.NO_SERIALIZATION,
                        RealtimeProtocol.GZIP
                    )
                )
                baos.write(RealtimeProtocol.intToBytes(RealtimeProtocol.EVT_AUDIO_TASK))
                val sid = sessionId.toByteArray()
                baos.write(RealtimeProtocol.intToBytes(sid.size))
                baos.write(sid)
                baos.write(RealtimeProtocol.intToBytes(compressed.size))
                baos.write(compressed)
                webSocket?.send(baos.toByteArray().toByteString())
            } catch (e: Exception) {
                Log.w(TAG, "sendAudio error: ${e.message}")
            }
        }
    }

    /**
     * 优雅断开：发送 FinishSession + FinishConnection 后关闭 WebSocket。
     */
    @Synchronized
    fun disconnect() {
        Log.d(TAG, "disconnect()")
        isStopped = true
        cancelConnectTimeout()
        if (phase == Phase.READY) {
            try {
                sendFinishSession()
                sendFinishConnection()
            } catch (e: Throwable) {
                Log.w(TAG, "Error sending finish events: ${e.message}")
            }
        }
        phase = Phase.IDLE
        try {
            webSocket?.close(1000, null)
        } catch (_: Throwable) {}
        client?.connectionPool?.evictAll()
        webSocket = null
    }

    // ========================== 内部：发送协议帧 ==========================

    private fun sendJsonEvent(eventId: Int, payload: JSONObject, includeSid: Boolean = true) {
        try {
            val compressed = RealtimeProtocol.gzipCompress(payload.toString().toByteArray())
            val baos = ByteArrayOutputStream()
            baos.write(
                RealtimeProtocol.generateHeader(
                    RealtimeProtocol.PROTOCOL_VERSION,
                    RealtimeProtocol.CLIENT_FULL_REQUEST,
                    RealtimeProtocol.MSG_WITH_EVENT,
                    RealtimeProtocol.JSON_SERIAL,
                    RealtimeProtocol.GZIP
                )
            )
            baos.write(RealtimeProtocol.intToBytes(eventId))
            if (includeSid) {
                val sid = sessionId.toByteArray()
                baos.write(RealtimeProtocol.intToBytes(sid.size))
                baos.write(sid)
            }
            baos.write(RealtimeProtocol.intToBytes(compressed.size))
            baos.write(compressed)
            webSocket?.send(baos.toByteArray().toByteString())
            Log.d(TAG, "sendJsonEvent event=$eventId ok")
        } catch (e: Exception) {
            Log.w(TAG, "sendJsonEvent error: ${e.message}")
        }
    }

    private fun sendStartConnection() {
        val payload = RealtimeProtocol.gzipCompress("{}".toByteArray())
        val baos = ByteArrayOutputStream()
        baos.write(
            RealtimeProtocol.generateHeader(
                RealtimeProtocol.PROTOCOL_VERSION,
                RealtimeProtocol.CLIENT_FULL_REQUEST,
                RealtimeProtocol.MSG_WITH_EVENT,
                RealtimeProtocol.JSON_SERIAL,
                RealtimeProtocol.GZIP
            )
        )
        baos.write(RealtimeProtocol.intToBytes(RealtimeProtocol.EVT_START_CONNECTION))
        baos.write(RealtimeProtocol.intToBytes(payload.size))
        baos.write(payload)
        webSocket?.send(baos.toByteArray().toByteString())
        phase = Phase.SENT_START_CONNECTION
        Log.d(TAG, "Sent StartConnection")
    }

    private fun buildSystemRole(): String = """
你是 MeowHub 智能助手，名字叫图图，连接着用户的手机，可以帮用户操控手机。

规则：
1. 当用户要求操控手机（打开应用、发消息、查快递、设闹钟等），只回复任务标记：
   [TASK:具体任务描述]
   不要加任何闲聊，只返回标记。

2. 当用户询问手机状态（电量、存储、WiFi、已装应用等），只回复查询标记：
   [QUERY:battery] 或 [QUERY:storage] 或 [QUERY:wifi] 或 [QUERY:apps]

3. 其他闲聊正常对话，不加任何标记。说话风格简洁自然，像朋友聊天。

4. 当前有任务在执行时，用户问进度就用语言回答当前状态，不加标记。

5. 当收到外部知识（RAG内容）时，用自然口语简洁地告知用户结果，绝对不要输出[TASK]或[QUERY]标记，也不要复述原文，用你自己的话总结。

当前手机状态：${deviceContext.ifEmpty { "未知" }}
${taskStateContext}
""".trimIndent()

    private fun sendStartSession() {
        val dialogId = UUID.randomUUID().toString()
        val req = JSONObject().apply {
            put("asr", JSONObject().apply {
                put("extra", JSONObject().apply {
                    put("end_smooth_window_ms", 1500)
                    put("enable_custom_vad", true)
                    put("enable_asr_twopass", true)
                })
            })
            put("tts", JSONObject().apply {
                put("speaker", "zh_female_vv_jupiter_bigtts")
                put("audio_config", JSONObject().apply {
                    put("channel", 1)
                    put("format", "pcm_s16le")
                    put("sample_rate", 24000)
                })
            })
            put("dialog", JSONObject().apply {
                put("bot_name", "图图")
                put("system_role", buildSystemRole())
                put("speaking_style", "你的说话风格简洁明了，语速适中，语调自然亲切。")
                put("dialog_id", dialogId)
                put("extra", JSONObject().apply {
                    put("strict_audit", false)
                    put("input_mod", "keep_alive")
                    put("enable_music", true)
                    put("enable_user_query_exit", true)
                    put("model", "1.2.1.1")
                })
            })
        }
        sendJsonEvent(RealtimeProtocol.EVT_START_SESSION, req, includeSid = true)
        phase = Phase.SENT_START_SESSION
        Log.d(TAG, "Sent StartSession, sid=$sessionId, dialogId=$dialogId")
    }

    private fun sendSayHello(content: String) {
        try {
            val payload = JSONObject().apply {
                put("content", content)
            }
            sendJsonEvent(RealtimeProtocol.EVT_SAY_HELLO, payload, includeSid = true)
            Log.i(TAG, "sendSayHello: $content")
        } catch (e: Exception) {
            Log.w(TAG, "sendSayHello error: ${e.message}")
        }
    }

    private fun sendFinishSession() {
        sendJsonEvent(RealtimeProtocol.EVT_FINISH_SESSION, JSONObject(), includeSid = true)
        Log.d(TAG, "Sent FinishSession")
    }

    private fun sendFinishConnection() {
        sendJsonEvent(RealtimeProtocol.EVT_FINISH_CONNECTION, JSONObject(), includeSid = false)
        Log.d(TAG, "Sent FinishConnection")
    }

    // ========================== 公开：业务事件发送 ==========================

    /**
     * 发送外部 RAG 文本，豆包会总结后用语音播报。
     * 必须在 ASREnded 之后调用。
     */
    /**
     * 指定文本直接 TTS 合成播放（事件 500）。
     * 按协议拆成首包（start+content）和尾包（end）两帧发送。
     * 必须在收到 ASREnded 之后调用。
     */
    fun sendChatTTSText(text: String) {
        if (phase != Phase.READY) return
        val startPayload = JSONObject().apply {
            put("start", true)
            put("content", text)
            put("end", false)
        }
        sendJsonEvent(RealtimeProtocol.EVT_CHAT_TTS_TEXT, startPayload, includeSid = true)
        val endPayload = JSONObject().apply {
            put("start", false)
            put("content", "")
            put("end", true)
        }
        sendJsonEvent(RealtimeProtocol.EVT_CHAT_TTS_TEXT, endPayload, includeSid = true)
        Log.i(TAG, "[TTS] Sent ChatTTSText: $text")
    }

    fun sendChatRAGText(ragItems: List<Pair<String, String>>) {
        if (phase != Phase.READY) return
        val arr = JSONArray()
        ragItems.forEach { (title, content) ->
            arr.put(JSONObject().apply {
                put("title", title)
                put("content", content)
            })
        }
        val payload = JSONObject().apply {
            put("external_rag", arr.toString())
        }
        sendJsonEvent(RealtimeProtocol.EVT_CHAT_RAG_TEXT, payload, includeSid = true)
        Log.i(TAG, "[RAG] Sent ChatRAGText: ${ragItems.size} items")
    }

    /**
     * 通话中动态更新配置（system_role 等）。
     */
    fun sendUpdateConfig() {
        if (phase != Phase.READY) return
        val payload = JSONObject().apply {
            put("dialog", JSONObject().apply {
                put("system_role", buildSystemRole())
            })
        }
        sendJsonEvent(RealtimeProtocol.EVT_UPDATE_CONFIG, payload, includeSid = true)
        Log.d(TAG, "[Config] Sent UpdateConfig")
    }

    /**
     * 用 ConversationUpdate 修改指定 reply_id 对应的模型回复文本，
     * 使豆包上下文记录为自然语言而非 [TASK:...] 标记。
     */
    fun sendConversationUpdate(itemId: String, newText: String) {
        if (phase != Phase.READY) return
        val payload = JSONObject().apply {
            put("items", JSONArray().apply {
                put(JSONObject().apply {
                    put("item_id", itemId)
                    put("text", newText)
                })
            })
        }
        sendJsonEvent(RealtimeProtocol.EVT_CONVERSATION_UPDATE, payload, includeSid = true)
        Log.d(TAG, "[Context] Sent ConversationUpdate: itemId=$itemId")
    }

    // ========================== 内部：连接管理 ==========================

    private fun scheduleConnectTimeout() {
        cancelConnectTimeout()
        connectTimeoutRunnable = Runnable {
            if (!hasOpened && !isStopped) {
                Log.w(TAG, "Connect timeout (${CONNECT_TIMEOUT_MS}ms)")
                try { webSocket?.close(1001, "connect-timeout") } catch (_: Throwable) {}
                scheduleReconnect("timeout")
            }
        }
        mainHandler.postDelayed(connectTimeoutRunnable!!, CONNECT_TIMEOUT_MS)
    }

    private fun cancelConnectTimeout() {
        connectTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        connectTimeoutRunnable = null
    }

    private fun scheduleReconnect(trigger: String) {
        if (isStopped) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts reached ($MAX_RECONNECT_ATTEMPTS), trigger=$trigger")
            callback.onError("连接失败，已重试 $MAX_RECONNECT_ATTEMPTS 次")
            phase = Phase.IDLE
            callback.onDisconnected()
            return
        }
        reconnectAttempts++
        val delay = (RECONNECT_BASE_DELAY_MS shl (reconnectAttempts - 1)).coerceAtMost(10_000)
        Log.d(TAG, "Reconnect #$reconnectAttempts in ${delay}ms, trigger=$trigger")
        mainHandler.postDelayed({
            if (isStopped) return@postDelayed
            phase = Phase.IDLE
            connect()
        }, delay)
    }

    // ========================== WebSocket 监听器 ==========================

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            Log.d(TAG, "WebSocket opened, logId=${response.header("X-Tt-Logid")}")
            hasOpened = true
            reconnectAttempts = 0
            cancelConnectTimeout()
            try {
                sendStartConnection()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send StartConnection: ${e.message}")
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            try {
                val res = bytes.toByteArray()
                val parsed = RealtimeProtocol.parseResponse(res) ?: return

                // TTS 音频数据（SERVER_ACK + NO_SERIALIZATION = 二进制 PCM）
                if (parsed.messageType == RealtimeProtocol.SERVER_ACK
                    && parsed.payloadBytes != null
                    && parsed.serializationMethod == RealtimeProtocol.NO_SERIALIZATION
                ) {
                    if (!isStopped) {
                        callback.onAudioReceived(parsed.payloadBytes)
                    }
                    return
                }

                // 服务端完整响应
                if (parsed.messageType == RealtimeProtocol.SERVER_FULL_RESPONSE) {
                    handleServerResponse(parsed)
                    return
                }

                // 错误响应
                if (parsed.messageType == RealtimeProtocol.SERVER_ERROR_RESPONSE) {
                    val errorMsg = try {
                        val obj = JSONObject(parsed.payloadJson ?: "{}")
                        obj.optString("error", parsed.payloadJson ?: "Unknown error")
                    } catch (_: Throwable) {
                        parsed.payloadJson ?: "Unknown error"
                    }
                    Log.w(TAG, "SERVER_ERROR: code=${parsed.errorCode}, msg=$errorMsg")
                    callback.onError("服务端错误(${parsed.errorCode}): $errorMsg")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse binary message: ${e.message}")
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "onMessage(text): $text")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code / $reason")
            webSocket.close(code, null)

            val proxyError = when (code) {
                4001 -> "认证信息缺失，请重新登录"
                4002 -> "积分不足，请充值后再使用语音助手"
                4003 -> "登录已过期，请重新登录"
                4004 -> "单次通话已达10分钟上限，已自动断开"
                4005 -> "语音服务暂时不可用，请稍后重试"
                else -> null
            }
            if (proxyError != null) {
                Log.w(TAG, "Proxy close code=$code: $proxyError")
                isStopped = true
                phase = Phase.IDLE
                callback.onError(proxyError)
                callback.onDisconnected()
                return
            }

            if (!isStopped) {
                scheduleReconnect("onClosing")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            Log.w(TAG, "WebSocket failure: ${t.message}")
            if (!isStopped) {
                callback.onError("连接失败: ${t.message}")
                try { webSocket.close(1000, null) } catch (_: Throwable) {}
                scheduleReconnect("onFailure")
            }
        }
    }

    // ========================== 服务端事件处理 ==========================

    private fun handleServerResponse(parsed: RealtimeProtocol.ParsedResponse) {
        val event = parsed.event
        val json = parsed.payloadJson
        Log.d(TAG, "ServerEvent: ${RealtimeProtocol.eventName(event)}($event), json=$json")

        // 握手阶段处理
        if (phase == Phase.SENT_START_CONNECTION && event == RealtimeProtocol.SVR_CONNECTION_STARTED) {
            Log.d(TAG, "ConnectionStarted, sending StartSession")
            sendStartSession()
            return
        }
        if (phase == Phase.SENT_START_SESSION && event == RealtimeProtocol.SVR_SESSION_STARTED) {
            phase = Phase.READY
            Log.i(TAG, "Session READY, sid=$sessionId")
            sendSayHello("喵，我在呢！")
            callback.onReady()
            return
        }

        // 事件分发
        when (event) {
            RealtimeProtocol.SVR_CONNECTION_FAILED -> {
                Log.w(TAG, "ConnectionFailed: $json")
                callback.onError("连接建立失败: $json")
            }
            RealtimeProtocol.SVR_SESSION_FAILED -> {
                Log.w(TAG, "SessionFailed: $json")
                callback.onError("会话创建失败: $json")
            }
            RealtimeProtocol.SVR_ASR_INFO -> {
                // 用户开始说话（VAD 检测到语音活动）→ 打断播放
                Log.d(TAG, "[VAD] 用户开始说话，打断AI播放")
                // 用户打断时，如果有未完成的AI回复也打印出来
                val partial = aiResponseBuffer.toString()
                if (partial.isNotEmpty()) {
                    Log.i(TAG, "[AI](被打断) $partial")
                    aiResponseBuffer.setLength(0)
                }
                callback.onUserSpeaking()
            }
            RealtimeProtocol.SVR_ASR_RESPONSE -> {
                // ASR 增量识别：只打印 final 结果，跳过 interim
                try {
                    val obj = JSONObject(json ?: "{}")
                    val results = obj.optJSONArray("results")
                    val first = results?.optJSONObject(0)
                    if (first != null) {
                        val text = first.optString("text", "")
                        val isInterim = first.optBoolean("is_interim", true)
                        if (!isInterim && text.isNotEmpty()) {
                            Log.i(TAG, "[用户] $text")
                        }
                    }
                } catch (_: Throwable) {}
            }
            RealtimeProtocol.SVR_ASR_ENDED -> {
                Log.d(TAG, "[ASR] 用户说话结束")
            }
            RealtimeProtocol.SVR_CHAT_RESPONSE -> {
                try {
                    val obj = JSONObject(json ?: "{}")
                    val replyId = obj.optString("reply_id", "")
                    if (replyId.isNotEmpty()) currentReplyId = replyId
                    val content = obj.optString("content", "")
                    if (content.isNotEmpty()) {
                        aiResponseBuffer.append(content)
                        callback.onChatToken(content)
                    }
                } catch (_: Throwable) {}
            }
            RealtimeProtocol.SVR_CHAT_ENDED -> {
                val fullReply = aiResponseBuffer.toString()
                val replyId = currentReplyId
                if (fullReply.isNotEmpty()) {
                    Log.i(TAG, "[AI] $fullReply")
                }
                aiResponseBuffer.setLength(0)
                currentReplyId = ""
                callback.onChatCompleted(fullReply, replyId)
            }
            RealtimeProtocol.SVR_TTS_SENTENCE_START -> {
                val ttsType = try {
                    JSONObject(json ?: "{}").optString("tts_type", "default")
                } catch (_: Throwable) { "default" }
                Log.d(TAG, "[TTS] SentenceStart tts_type=$ttsType")
                callback.onTtsSentenceStart(ttsType)
            }
            RealtimeProtocol.SVR_TTS_ENDED -> {
                Log.d(TAG, "[TTS] Ended, payload=$json")
                callback.onTtsEnded()
                try {
                    val obj = JSONObject(json ?: "{}")
                    if (obj.optString("status_code", "") == "20000002") {
                        Log.i(TAG, "[EXIT] 豆包识别到用户退出意图")
                        callback.onExitDetected()
                    }
                } catch (_: Throwable) {}
            }
            RealtimeProtocol.SVR_CONFIG_UPDATED -> {
                Log.d(TAG, "[Config] UpdateConfig ACK")
            }
            RealtimeProtocol.SVR_CONVERSATION_CREATED -> {
                Log.d(TAG, "[Context] ConversationCreate ACK")
            }
            RealtimeProtocol.SVR_CONVERSATION_UPDATED -> {
                Log.d(TAG, "[Context] ConversationUpdate ACK: $json")
            }
            RealtimeProtocol.SVR_DIALOG_ERROR -> {
                val obj = try { JSONObject(json ?: "{}") } catch (_: Throwable) { JSONObject() }
                val statusCode = obj.optString("status_code", "unknown")
                val message = obj.optString("message", "未知错误")
                Log.w(TAG, "DialogError($statusCode): $message")
                callback.onError("对话错误($statusCode): $message")
            }
            RealtimeProtocol.SVR_USAGE_RESPONSE -> {
                try {
                    val obj = JSONObject(json ?: "{}")
                    val usage = obj.optJSONObject("usage")
                    if (usage != null) {
                        val inputText = usage.optLong("input_text_tokens")
                        val inputAudio = usage.optLong("input_audio_tokens")
                        val cachedText = usage.optLong("cached_text_tokens")
                        val cachedAudio = usage.optLong("cached_audio_tokens")
                        val outputText = usage.optLong("output_text_tokens")
                        val outputAudio = usage.optLong("output_audio_tokens")
                        Log.d(TAG, "[Usage] input(text=$inputText, audio=$inputAudio) " +
                            "cached(text=$cachedText, audio=$cachedAudio) " +
                            "output(text=$outputText, audio=$outputAudio)")
                    }
                } catch (_: Throwable) {}
            }
            RealtimeProtocol.SVR_SESSION_FINISHED -> {
                Log.d(TAG, "SessionFinished")
            }
            RealtimeProtocol.SVR_CONNECTION_FINISHED -> {
                Log.d(TAG, "ConnectionFinished")
            }
            // TTS_SENTENCE_END, TTS_RESPONSE 等高频事件不打日志
            else -> {}
        }
    }
}
