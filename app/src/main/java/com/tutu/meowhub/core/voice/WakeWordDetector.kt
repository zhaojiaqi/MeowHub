package com.tutu.meowhub.core.voice

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 唤醒词检测器 — 使用 Picovoice Porcupine 持续监听麦克风，检测到"图图"后回调。
 */
class WakeWordDetector(
    private val context: Context,
    private val accessKey: String,
    private val onWakeWordDetected: () -> Unit
) {
    companion object {
        private const val TAG = "WakeWordDetector"
        private const val KEYWORD_PATH = "图图_zh_android_v4_0_0.ppn"
        private const val MODEL_PATH = "porcupine_params_zh.pv"
        private const val SENSITIVITY = 0.7f
    }

    private var porcupineManager: PorcupineManager? = null
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    fun start() {
        if (_isListening.value) {
            Log.w(TAG, "Already listening, ignoring start()")
            return
        }
        if (accessKey.isBlank()) {
            Log.w(TAG, "Access key is blank, cannot start")
            return
        }

        try {
            val callback = PorcupineManagerCallback { keywordIndex ->
                Log.i(TAG, "Wake word detected! keywordIndex=$keywordIndex")
                onWakeWordDetected()
            }

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(KEYWORD_PATH)
                .setModelPath(MODEL_PATH)
                .setSensitivity(SENSITIVITY)
                .build(context, callback)

            porcupineManager?.start()
            _isListening.value = true
            Log.i(TAG, "Wake word detection started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake word detection", e)
            porcupineManager = null
            _isListening.value = false
        }
    }

    fun stop() {
        try {
            porcupineManager?.stop()
            _isListening.value = false
            Log.i(TAG, "Wake word detection stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake word detection", e)
        }
    }

    fun destroy() {
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying wake word detector", e)
        }
        porcupineManager = null
        _isListening.value = false
        Log.i(TAG, "Wake word detector destroyed")
    }
}
