package com.tutu.meowhub.core.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * PCM 流式音频播放器。
 *
 * 播放格式：24kHz / 16bit / 单声道（豆包 TTS 返回格式）。
 * 收到 TTS 数据后直接 write 到 AudioTrack 实现流式播放。
 */
class MeowAudioPlayer {
    companion object {
        private const val TAG = "MeowAudioPlayer"
        private const val SAMPLE_RATE = 24000
        private const val CHANNEL = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    @Volatile private var isPlaying = false

    fun start() {
        if (isPlaying) return
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val bufSize = maxOf(minBuf, SAMPLE_RATE * 2) // 至少 1 秒缓冲
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL)
                    .setEncoding(ENCODING)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        isPlaying = true
        Log.d(TAG, "AudioTrack started, bufSize=$bufSize")
    }

    /**
     * 将 PCM 数据写入播放缓冲区（流式播放）。
     * 线程安全 — AudioTrack.write 内部有同步。
     */
    fun write(pcmData: ByteArray) {
        if (!isPlaying) return
        audioTrack?.write(pcmData, 0, pcmData.size)
    }

    /**
     * 清空缓冲区并暂停播放（被打断时调用）。
     */
    fun clearAndPause() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Throwable) {}
        isPlaying = false
        Log.d(TAG, "Cleared buffer and paused")
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
        } catch (_: Throwable) {}
        audioTrack?.release()
        audioTrack = null
        Log.d(TAG, "AudioTrack stopped and released")
    }
}
