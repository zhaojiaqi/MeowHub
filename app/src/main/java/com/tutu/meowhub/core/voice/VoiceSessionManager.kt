package com.tutu.meowhub.core.voice

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tutu.meowhub.MeowApp
import com.tutu.meowhub.core.engine.DeviceInfoCache
import com.tutu.meowhub.core.engine.SocketCommandBridge
import com.tutu.meowhub.feature.chat.ChatAgent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 语音会话管理器 — 编排 WebSocket + Recorder + Player + AudioGate + TaskBridge。
 *
 * 核心流程：
 * 1. 豆包 TTS 音频到达 → AudioGate 根据 tts_type 决定缓冲/直通
 * 2. 豆包 ChatResponse 文本流式到达 → TaskBridge 实时检测 [TASK]/[QUERY] 标记
 * 3. ChatEnded 时 → 无标记则释放音频播放，有标记则丢弃音频并执行任务/查询
 * 4. 任务/查询结果通过 ChatRAGText 注入豆包，RAG 音频直接播放
 */
class VoiceSessionManager(
    private val context: Context,
    private val appId: String,
    private val accessKey: String
) {
    companion object {
        private const val TAG = "MeowVoice.Session"
        private const val EXIT_DELAY_MS = 1500L
    }

    enum class VoiceState { IDLE, CONNECTING, ACTIVE, STOPPING }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private var socket: DoubaoVoiceSocket? = null
    private var recorder: MeowAudioRecorder? = null
    private var player: MeowAudioPlayer? = null
    private var audioGate: VoiceAudioGate? = null
    private var taskBridge: VoiceTaskBridge? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedSpeakerphoneOn: Boolean = false

    fun startSession() {
        if (_voiceState.value != VoiceState.IDLE) {
            Log.w(TAG, "startSession() called but state=${_voiceState.value}")
            return
        }
        _voiceState.value = VoiceState.CONNECTING
        Log.i(TAG, "Starting voice session...")

        savedAudioMode = audioManager.mode
        savedSpeakerphoneOn = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true

        val app = context.applicationContext as MeowApp
        val deviceCache: DeviceInfoCache = app.deviceCache
        val socketBridge = SocketCommandBridge(app.tutuClient, deviceCache)
        val chatAgent = ChatAgent(
            bridge = socketBridge,
            aiProviderFactory = { app.resolveAiProvider() },
            apiClient = app.apiClient,
            deviceCache = deviceCache,
            context = context
        )

        val newPlayer = MeowAudioPlayer().also { player = it }
        val newGate = VoiceAudioGate(newPlayer).also { audioGate = it }

        val callback = object : DoubaoVoiceSocket.VoiceSocketCallback {
            override fun onReady() {
                Log.i(TAG, "Socket ready, starting recorder & player")
                _voiceState.value = VoiceState.ACTIVE
                newPlayer.start()
                recorder = MeowAudioRecorder { pcmData ->
                    socket?.sendAudio(pcmData)
                }.also { it.start() }

                taskBridge?.initContext()
            }

            override fun onAudioReceived(pcmData: ByteArray) {
                newGate.onAudioData(pcmData)
            }

            override fun onTtsSentenceStart(ttsType: String) {
                newGate.onTtsSentenceStart(ttsType)
            }

            override fun onTtsEnded() {
                newGate.onTtsEnded()
                taskBridge?.onTtsEnded()
            }

            override fun onUserSpeaking() {
                Log.d(TAG, "User speaking — interrupting")
                newPlayer.clearAndPause()
                newPlayer.start()
                newGate.onUserInterrupt()
                taskBridge?.onUserInterrupt()
            }

            override fun onChatToken(token: String) {
                taskBridge?.onChatToken(token)
            }

            override fun onChatCompleted(fullText: String, replyId: String) {
                taskBridge?.onChatCompleted(fullText, replyId)
            }

            override fun onExitDetected() {
                Log.i(TAG, "Exit intent detected, waiting ${EXIT_DELAY_MS}ms")
                mainHandler.postDelayed({
                    if (_voiceState.value == VoiceState.ACTIVE) {
                        stopSession()
                    }
                }, EXIT_DELAY_MS)
            }

            override fun onError(message: String) {
                Log.w(TAG, "Socket error: $message")
            }

            override fun onDisconnected() {
                Log.i(TAG, "Socket disconnected")
                if (_voiceState.value != VoiceState.IDLE) {
                    cleanupInternal()
                }
            }
        }

        val newSocket = DoubaoVoiceSocket(appId, accessKey, callback)
        socket = newSocket

        taskBridge = VoiceTaskBridge(
            chatAgent = chatAgent,
            deviceCache = deviceCache,
            bridge = socketBridge,
            socket = newSocket,
            audioGate = newGate
        )

        newSocket.connect()
    }

    fun stopSession() {
        if (_voiceState.value == VoiceState.IDLE || _voiceState.value == VoiceState.STOPPING) return
        _voiceState.value = VoiceState.STOPPING
        Log.i(TAG, "Stopping voice session...")
        cleanupInternal()
    }

    fun toggle() {
        when (_voiceState.value) {
            VoiceState.IDLE -> startSession()
            VoiceState.ACTIVE, VoiceState.CONNECTING -> stopSession()
            VoiceState.STOPPING -> {}
        }
    }

    private fun cleanupInternal() {
        mainHandler.removeCallbacksAndMessages(null)
        taskBridge?.destroy()
        taskBridge = null
        recorder?.stop()
        recorder = null
        player?.stop()
        player = null
        audioGate = null
        socket?.disconnect()
        socket = null

        audioManager.mode = savedAudioMode
        audioManager.isSpeakerphoneOn = savedSpeakerphoneOn

        _voiceState.value = VoiceState.IDLE
        Log.i(TAG, "Session cleaned up")
    }
}
