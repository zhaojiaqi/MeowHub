package com.tutu.meowhub.core.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log

/**
 * 麦克风 PCM 录音器。
 *
 * 采集格式：16kHz / 16bit / 单声道（豆包 ASR 要求）。
 * 每 20ms 采集一包（640 bytes = 16000 × 2 × 0.02）。
 */
class MeowAudioRecorder(
    private val onAudioData: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "MeowAudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        /** 20ms 一包 */
        private const val FRAME_SIZE = SAMPLE_RATE * 2 * 20 / 1000 // 640 bytes
    }

    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    @Volatile private var isRecording = false

    // 音频处理效果
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val bufSize = maxOf(minBuf, FRAME_SIZE * 4)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufSize
        )
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord init failed")
            audioRecord?.release()
            audioRecord = null
            return
        }

        // 挂载回声消除 / 噪声抑制 / 自动增益
        val sessionId = audioRecord!!.audioSessionId
        attachAudioEffects(sessionId)

        isRecording = true
        audioRecord?.startRecording()

        recordThread = Thread({
            Log.d(TAG, "Record thread started")
            val buffer = ByteArray(FRAME_SIZE)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: -1
                if (read > 0) {
                    onAudioData(buffer.copyOf(read))
                }
            }
            Log.d(TAG, "Record thread ended")
        }, "MeowAudioRecorder").also { it.start() }
    }

    fun stop() {
        isRecording = false
        try {
            recordThread?.join(500)
        } catch (_: InterruptedException) {}
        recordThread = null
        releaseAudioEffects()
        try {
            audioRecord?.stop()
        } catch (_: Throwable) {}
        audioRecord?.release()
        audioRecord = null
    }

    private fun attachAudioEffects(sessionId: Int) {
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(sessionId)?.also {
                it.enabled = true
                Log.i(TAG, "AcousticEchoCanceler created, enabled=${it.enabled}")
            }
        } else {
            Log.w(TAG, "AcousticEchoCanceler not available on this device")
        }

        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(sessionId)?.also {
                it.enabled = true
                Log.i(TAG, "NoiseSuppressor created, enabled=${it.enabled}")
            }
        } else {
            Log.w(TAG, "NoiseSuppressor not available on this device")
        }

        if (AutomaticGainControl.isAvailable()) {
            gainControl = AutomaticGainControl.create(sessionId)?.also {
                it.enabled = true
                Log.i(TAG, "AutomaticGainControl created, enabled=${it.enabled}")
            }
        } else {
            Log.w(TAG, "AutomaticGainControl not available on this device")
        }
    }

    private fun releaseAudioEffects() {
        echoCanceler?.release()
        echoCanceler = null
        noiseSuppressor?.release()
        noiseSuppressor = null
        gainControl?.release()
        gainControl = null
    }
}
