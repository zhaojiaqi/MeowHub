package com.tutu.meowhub.core.voice

import android.util.Log

/**
 * 音频闸门 — 控制豆包 TTS 音频的缓冲、释放与丢弃。
 *
 * 闲聊音频(tts_type=default)先缓冲，等待文本意图判定后再决定播放或丢弃；
 * RAG/自定义TTS音频直接播放，不走缓冲。
 *
 * 状态机：PASSTHROUGH ←→ SUSPENDED → DISCARDING → PASSTHROUGH
 */
class VoiceAudioGate(private val player: MeowAudioPlayer) {

    companion object {
        private const val TAG = "MeowVoice.Gate"
    }

    enum class State {
        /** 音频直通播放（RAG 音频 / 已确认的闲聊） */
        PASSTHROUGH,
        /** 闲聊音频挂起缓冲中，等待文本意图判定 */
        SUSPENDED,
        /** 确认为 TASK/QUERY，丢弃本轮所有闲聊音频 */
        DISCARDING
    }

    @Volatile
    var state: State = State.PASSTHROUGH
        private set

    private val buffer = mutableListOf<ByteArray>()
    private var bufferedBytes = 0

    /**
     * TTSSentenceStart 到达时调用。
     * 根据 tts_type 决定本轮音频走缓冲还是直通。
     */
    fun onTtsSentenceStart(ttsType: String) {
        when (ttsType) {
            "external_rag", "chat_tts_text", "network" -> {
                state = State.PASSTHROUGH
                Log.d(TAG, "TTS start: tts_type=$ttsType → PASSTHROUGH (direct play)")
            }
            else -> {
                state = State.SUSPENDED
                buffer.clear()
                bufferedBytes = 0
                Log.d(TAG, "TTS start: tts_type=$ttsType → SUSPENDED (buffering)")
            }
        }
    }

    /**
     * TTSResponse 音频数据到达时调用。
     */
    fun onAudioData(pcmData: ByteArray) {
        when (state) {
            State.PASSTHROUGH -> player.write(pcmData)
            State.SUSPENDED -> {
                buffer.add(pcmData)
                bufferedBytes += pcmData.size
            }
            State.DISCARDING -> { /* 丢弃 */ }
        }
    }

    /**
     * 文本确认为纯闲聊（无 TASK/QUERY 标记）— 释放缓冲并恢复直通。
     */
    fun release() {
        val chunks = buffer.size
        Log.d(TAG, "RELEASE: flushing $chunks chunks (${bufferedBytes}B) to player")
        buffer.forEach { player.write(it) }
        buffer.clear()
        bufferedBytes = 0
        state = State.PASSTHROUGH
    }

    /**
     * 文本检测到 TASK/QUERY 标记 — 丢弃缓冲，后续音频也丢弃直到本轮结束。
     */
    fun discard() {
        Log.d(TAG, "DISCARD: dropping ${buffer.size} chunks (${bufferedBytes}B)")
        buffer.clear()
        bufferedBytes = 0
        state = State.DISCARDING
    }

    /**
     * TTSEnded 到达 — 一轮 TTS 结束，重置为直通。
     */
    fun onTtsEnded() {
        if (state != State.PASSTHROUGH) {
            Log.d(TAG, "TTS ended: $state → PASSTHROUGH (reset)")
        }
        buffer.clear()
        bufferedBytes = 0
        state = State.PASSTHROUGH
    }

    /**
     * 用户打断时清空缓冲（ASRInfo 触发）。
     */
    fun onUserInterrupt() {
        buffer.clear()
        bufferedBytes = 0
        state = State.PASSTHROUGH
    }
}
