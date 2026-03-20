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
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 豆包实时语音 WebSocket 管理器。
 *
 * 状态机：IDLE → CONNECTING → SENT_START_CONNECTION → SENT_START_SESSION → READY → IDLE
 *
 * 通过 [VoiceSocketCallback] 回调通知外部关键事件。
 */
class DoubaoVoiceSocket(
    private val appId: String,
    private val accessKey: String,
    private val callback: VoiceSocketCallback
) {
    companion object {
        private const val TAG = "DoubaoVoiceSocket"
        private const val URL = "wss://openspeech.bytedance.com/api/v3/realtime/dialogue"
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_BASE_DELAY_MS = 1_000L
    }

    interface VoiceSocketCallback {
        /** Session 就绪，可以发送音频 */
        fun onReady()
        /** 收到 TTS 音频（PCM 24kHz 16bit mono） */
        fun onAudioReceived(pcmData: ByteArray)
        /** ASRInfo - 用户开始说话，应打断播放 */
        fun onUserSpeaking()
        /** 豆包识别到用户退出意图（如"再见"、"结束对话"），客户端应结束语音会话 */
        fun onExitDetected()
        /** 错误 */
        fun onError(message: String)
        /** 连接断开 */
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

        val request = Request.Builder()
            .url(URL)
            .addHeader("X-Api-App-ID", appId)
            .addHeader("X-Api-Access-Key", accessKey)
            .addHeader("X-Api-Resource-Id", "volc.speech.dialog")
            .addHeader("X-Api-App-Key", "PlgvMymc7f3tQnJ6")
            .addHeader("X-Api-Connect-Id", UUID.randomUUID().toString())
            .build()

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
                put("bot_name", "MeowHub助手")
                put("system_role", "你是 MeowHub 智能助手，名字叫图图，通过语音帮助用户操控手机。回答要简洁自然，像朋友聊天一样。")
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
        Log.d(TAG, "Sent StartSession, sid=$sessionId, dialogId=$dialogId, payload=$req")
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
            webSocket.close(1000, null)
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
                // AI 回复增量文本：累积到 buffer
                try {
                    val obj = JSONObject(json ?: "{}")
                    val content = obj.optString("content", "")
                    if (content.isNotEmpty()) {
                        aiResponseBuffer.append(content)
                    }
                } catch (_: Throwable) {}
            }
            RealtimeProtocol.SVR_CHAT_ENDED -> {
                // AI 回复完成，打印完整回复
                val fullReply = aiResponseBuffer.toString()
                if (fullReply.isNotEmpty()) {
                    Log.i(TAG, "[AI] $fullReply")
                }
                aiResponseBuffer.setLength(0)
            }
            RealtimeProtocol.SVR_TTS_SENTENCE_START -> {
                Log.d(TAG, "[TTS] 开始播放")
            }
            RealtimeProtocol.SVR_TTS_ENDED -> {
                Log.d(TAG, "[TTS] 播放结束, payload=$json")
                try {
                    val obj = JSONObject(json ?: "{}")
                    val statusCode = obj.optString("status_code", "")
                    if (statusCode == "20000002") {
                        Log.i(TAG, "[EXIT] 豆包识别到用户退出意图，准备结束会话")
                        callback.onExitDetected()
                    }
                } catch (_: Throwable) {}
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
