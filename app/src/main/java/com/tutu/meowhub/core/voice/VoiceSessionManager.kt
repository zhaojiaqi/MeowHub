package com.tutu.meowhub.core.voice

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 语音会话管理器 — 编排 WebSocket + Recorder + Player 的完整生命周期。
 *
 * 暴露 [voiceState] 供 UI 层观察当前语音状态。
 */
class VoiceSessionManager(
    private val context: Context,
    private val appId: String,
    private val accessKey: String
) {
    companion object {
        private const val TAG = "VoiceSessionMgr"
    }

    enum class VoiceState { IDLE, CONNECTING, ACTIVE, STOPPING }

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private var socket: DoubaoVoiceSocket? = null
    private var recorder: MeowAudioRecorder? = null
    private var player: MeowAudioPlayer? = null

    // AudioManager 模式恢复
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedSpeakerphoneOn: Boolean = false

    /**
     * 开始语音会话：连接 WebSocket → Ready 后启动录音和播放。
     */
    fun startSession() {
        if (_voiceState.value != VoiceState.IDLE) {
            Log.w(TAG, "startSession() called but state=${_voiceState.value}")
            return
        }
        _voiceState.value = VoiceState.CONNECTING
        Log.i(TAG, "Starting voice session...")

        // 切换到 VOIP 通信模式，使 AEC 生效
        savedAudioMode = audioManager.mode
        savedSpeakerphoneOn = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        Log.i(TAG, "AudioManager: mode=MODE_IN_COMMUNICATION, speakerphone=ON")

        val callback = object : DoubaoVoiceSocket.VoiceSocketCallback {
            override fun onReady() {
                Log.i(TAG, "Socket ready, starting recorder & player")
                _voiceState.value = VoiceState.ACTIVE

                // 启动播放器
                player = MeowAudioPlayer().also { it.start() }

                // 启动录音（回调中将 PCM 数据发送给 WebSocket）
                recorder = MeowAudioRecorder { pcmData ->
                    socket?.sendAudio(pcmData)
                }.also { it.start() }
            }

            override fun onAudioReceived(pcmData: ByteArray) {
                player?.write(pcmData)
            }

            override fun onUserSpeaking() {
                Log.d(TAG, "User speaking — interrupting playback")
                // 打断播放，但继续录音
                player?.clearAndPause()
                // 重新启动播放器以便接收新的 TTS
                player?.start()
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

        socket = DoubaoVoiceSocket(appId, accessKey, callback).also { it.connect() }
    }

    /**
     * 停止语音会话：停止录音 → 停止播放 → 断开 WebSocket。
     */
    fun stopSession() {
        if (_voiceState.value == VoiceState.IDLE || _voiceState.value == VoiceState.STOPPING) return
        _voiceState.value = VoiceState.STOPPING
        Log.i(TAG, "Stopping voice session...")
        cleanupInternal()
    }

    /**
     * 切换语音状态（用于双击触发）。
     */
    fun toggle() {
        when (_voiceState.value) {
            VoiceState.IDLE -> startSession()
            VoiceState.ACTIVE, VoiceState.CONNECTING -> stopSession()
            VoiceState.STOPPING -> {} // 等待停止完成
        }
    }

    private fun cleanupInternal() {
        recorder?.stop()
        recorder = null
        player?.stop()
        player = null
        socket?.disconnect()
        socket = null

        // 恢复 AudioManager 模式
        audioManager.mode = savedAudioMode
        audioManager.isSpeakerphoneOn = savedSpeakerphoneOn
        Log.i(TAG, "AudioManager restored: mode=$savedAudioMode, speakerphone=$savedSpeakerphoneOn")

        _voiceState.value = VoiceState.IDLE
        Log.i(TAG, "Session cleaned up, state=IDLE")
    }
}
