package com.tutu.meowhub.feature.debug

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutu.meowhub.MeowApp
import com.tutu.meowhub.R
import com.tutu.meowhub.core.model.ConnectionState
import com.tutu.meowhub.core.model.TutuCommandSpec
import com.tutu.meowhub.core.model.TutuCommands
import com.tutu.meowhub.core.socket.TutuSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive

class DebugViewModel : ViewModel() {

    val client = MeowApp.instance.tutuClient

    val connectionState: StateFlow<ConnectionState> = client.connectionState

    private val _logs = MutableStateFlow<List<TutuSocketClient.LogEntry>>(emptyList())
    val logs: StateFlow<List<TutuSocketClient.LogEntry>> = _logs.asStateFlow()

    private val _screenshotBitmap = MutableStateFlow<Bitmap?>(null)
    val screenshotBitmap: StateFlow<Bitmap?> = _screenshotBitmap.asStateFlow()

    val categories = TutuCommands.allCategories()

    private val maxLogSize = 500

    init {
        viewModelScope.launch {
            client.rawLog.collect { entry ->
                _logs.value = (_logs.value + entry).takeLast(maxLogSize)
            }
        }
        viewModelScope.launch {
            client.messages.collect { msg ->
                val type = msg["type"]?.jsonPrimitive?.content
                if (type == "screenshot_data") {
                    val base64Data = msg["data"]?.jsonPrimitive?.content
                    if (base64Data != null) {
                        decodeAndShowScreenshot(base64Data)
                    }
                }
            }
        }
    }

    private fun decodeAndShowScreenshot(base64Data: String) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                _screenshotBitmap.value = bitmap
            }
        }
    }

    fun dismissScreenshot() {
        _screenshotBitmap.value = null
    }

    fun connect() {
        MeowApp.instance.connectWithAuth()
    }

    fun disconnect() {
        client.disconnect()
    }

    fun executeCommand(spec: TutuCommandSpec, paramValues: Map<String, String>, delaySec: Int = 0) {
        val reqId = if (spec.hasResponse) client.nextReqId() else null
        val jsonObj = TutuCommands.buildJsonForCommand(spec, paramValues, reqId)
        val context = MeowApp.instance

        viewModelScope.launch {
            if (delaySec > 0) {
                val name = context.getString(spec.nameResId)
                client.log(context.getString(R.string.delay_execution_log, delaySec, name))
                delay(delaySec * 1000L)
            }
            if (spec.hasResponse && reqId != null) {
                client.sendAndWait(jsonObj)
            } else {
                client.sendFireAndForget(jsonObj)
            }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
    }
}
