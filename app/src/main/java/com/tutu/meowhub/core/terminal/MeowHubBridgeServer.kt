package com.tutu.meowhub.core.terminal

import android.util.Log
import com.tutu.meowhub.core.engine.SocketCommandBridge
import com.tutu.meowhub.core.model.ConnectionState
import com.tutu.meowhub.core.socket.TutuSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class MeowHubBridgeServer(
    private val bridge: SocketCommandBridge,
    private val tutuClient: TutuSocketClient,
    private val port: Int = 18790
) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var running = false

    fun start() {
        if (running) {
            Log.d(TAG, "Bridge server already running on :$port")
            return
        }
        scope.launch {
            try {
                val ss = ServerSocket(port, 10, InetAddress.getByName("127.0.0.1"))
                serverSocket = ss
                running = true
                Log.i(TAG, "Bridge server started on 127.0.0.1:$port")

                while (running) {
                    try {
                        val client = ss.accept()
                        scope.launch { handleClient(client) }
                    } catch (e: Exception) {
                        if (running) Log.w(TAG, "Accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start bridge server: ${e.message}", e)
                running = false
            }
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        Log.i(TAG, "Bridge server stopped")
    }

    private suspend fun handleClient(client: Socket) {
        try {
            client.soTimeout = 30000
            val input = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 3) return

            val method = parts[0]
            val path = parts[1]

            val headers = mutableMapOf<String, String>()
            var line: String?
            while (true) {
                line = input.readLine()
                if (line.isNullOrEmpty()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    headers[line.substring(0, colonIdx).trim().lowercase()] =
                        line.substring(colonIdx + 1).trim()
                }
            }

            var body: JsonObject? = null
            if (method == "POST") {
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = input.read(buf, read, contentLength - read)
                        if (n == -1) break
                        read += n
                    }
                    val raw = String(buf, 0, read)
                    if (raw.isNotBlank()) {
                        try {
                            body = Json.parseToJsonElement(raw).jsonObject
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse body: ${e.message}")
                        }
                    }
                }
            }

            val endpoint = path.removePrefix("/api/").split("?")[0]
            val result = routeRequest(method, endpoint, body)
            sendHttpResponse(client, result.first, result.second)
        } catch (e: Exception) {
            Log.w(TAG, "handleClient error: ${e.message}")
            try {
                sendHttpResponse(client, 500, buildJsonObject {
                    put("error", e.message ?: "Internal error")
                })
            } catch (_: Exception) {}
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private suspend fun routeRequest(method: String, endpoint: String, body: JsonObject?): Pair<Int, JsonObject> {
        return try {
            when (endpoint) {
                "status" -> 200 to handleStatus()
                "screenshot" -> requireConnected { handleScreenshot(body) }
                "tap" -> requireConnected { handleTap(body) }
                "swipe" -> requireConnected { handleSwipe(body) }
                "scroll" -> requireConnected { handleScroll(body) }
                "type" -> requireConnected { handleType(body) }
                "press_key" -> requireConnected { handlePressKey(body) }
                "open_app" -> requireConnected { handleOpenApp(body) }
                "get_ui_tree" -> requireConnected { handleGetUiTree() }
                "find_element" -> requireConnected { handleFindElement(body) }
                "click_by_text" -> requireConnected { handleClickByText(body) }
                "execute_shell" -> requireConnected { handleExecuteShell(body) }
                "device_info" -> requireConnected { handleDeviceInfo(body) }
                "read_ui_text" -> requireConnected { handleReadUiText(body) }
                "long_click" -> requireConnected { handleLongClick(body) }
                "list_packages" -> requireConnected { handleListPackages() }
                "accept_call" -> requireConnected { handleAcceptCall() }
                "end_call" -> requireConnected { handleEndCall() }
                "make_call" -> requireConnected { handleMakeCall(body) }
                "open_audio_channel" -> requireConnected { handleOpenAudioChannel(body) }
                "close_audio_channel" -> requireConnected { handleCloseAudioChannel() }
                else -> 404 to buildJsonObject { put("error", "Not found: $endpoint") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Route error [$endpoint]: ${e.message}")
            500 to buildJsonObject { put("error", e.message ?: "Internal error") }
        }
    }

    private fun isConnected(): Boolean =
        tutuClient.connectionState.value == ConnectionState.CONNECTED

    private suspend fun requireConnected(block: suspend () -> JsonObject): Pair<Int, JsonObject> {
        if (!isConnected()) {
            return 503 to buildJsonObject {
                put("error", "MeowHub Socket not connected")
                put("status", "disconnected")
            }
        }
        return 200 to block()
    }

    private fun handleStatus(): JsonObject {
        val state = tutuClient.connectionState.value
        val screenSize = bridge.deviceCache.screenSize.value
        return buildJsonObject {
            put("connected", state == ConnectionState.CONNECTED)
            put("status", state.name.lowercase())
            put("screenWidth", screenSize.first)
            put("screenHeight", screenSize.second)
        }
    }

    private suspend fun handleScreenshot(body: JsonObject?): JsonObject {
        val data = bridge.takeScreenshot()
        return if (data != null) {
            val (sw, sh) = bridge.deviceCache.screenSize.value
            buildJsonObject {
                put("data", data)
                put("image", data)
                put("width", sw)
                put("height", sh)
                put("mimeType", "image/jpeg")
            }
        } else {
            buildJsonObject { put("error", "Screenshot failed") }
        }
    }

    private fun handleTap(body: JsonObject?): JsonObject {
        val x = body?.get("x")?.jsonPrimitive?.intOrNull ?: 0
        val y = body?.get("y")?.jsonPrimitive?.intOrNull ?: 0
        bridge.tap(x, y)
        return buildJsonObject { put("ok", true); put("action", "tap"); put("x", x); put("y", y) }
    }

    private fun handleSwipe(body: JsonObject?): JsonObject {
        val x1 = body?.get("startX")?.jsonPrimitive?.intOrNull
            ?: body?.get("x1")?.jsonPrimitive?.intOrNull ?: 0
        val y1 = body?.get("startY")?.jsonPrimitive?.intOrNull
            ?: body?.get("y1")?.jsonPrimitive?.intOrNull ?: 0
        val x2 = body?.get("endX")?.jsonPrimitive?.intOrNull
            ?: body?.get("x2")?.jsonPrimitive?.intOrNull ?: 0
        val y2 = body?.get("endY")?.jsonPrimitive?.intOrNull
            ?: body?.get("y2")?.jsonPrimitive?.intOrNull ?: 0
        val duration = body?.get("duration")?.jsonPrimitive?.intOrNull ?: 300
        bridge.swipe(x1, y1, x2, y2, duration)
        return buildJsonObject { put("ok", true); put("action", "swipe") }
    }

    private fun handleScroll(body: JsonObject?): JsonObject {
        val direction = body?.get("direction")?.jsonPrimitive?.contentOrNull ?: "down"
        bridge.scroll(direction)
        return buildJsonObject { put("ok", true); put("action", "scroll"); put("direction", direction) }
    }

    private fun handleType(body: JsonObject?): JsonObject {
        val text = body?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
        bridge.typeText(text)
        return buildJsonObject { put("ok", true); put("action", "type") }
    }

    private fun handlePressKey(body: JsonObject?): JsonObject {
        val key = body?.get("key")?.jsonPrimitive?.contentOrNull ?: "home"
        when (key) {
            "home" -> bridge.pressHome()
            "back" -> bridge.pressBack()
            else -> bridge.pressHome()
        }
        return buildJsonObject { put("ok", true); put("action", "press_key"); put("key", key) }
    }

    private fun handleOpenApp(body: JsonObject?): JsonObject {
        val name = body?.get("name")?.jsonPrimitive?.contentOrNull
            ?: body?.get("package")?.jsonPrimitive?.contentOrNull
            ?: body?.get("app_name")?.jsonPrimitive?.contentOrNull ?: ""
        bridge.startApp(name)
        return buildJsonObject { put("ok", true); put("action", "open_app"); put("app", name) }
    }

    private suspend fun handleGetUiTree(): JsonObject {
        return bridge.getUiNodes()
            ?: buildJsonObject { put("error", "UI tree unavailable") }
    }

    private suspend fun handleFindElement(body: JsonObject?): JsonObject {
        val text = body?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
        val id = body?.get("resourceId")?.jsonPrimitive?.contentOrNull
            ?: body?.get("id")?.jsonPrimitive?.contentOrNull ?: ""
        val className = body?.get("className")?.jsonPrimitive?.contentOrNull ?: ""
        return bridge.findElement(text, id, className)
            ?: buildJsonObject { put("error", "Element not found") }
    }

    private fun handleClickByText(body: JsonObject?): JsonObject {
        val text = body?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
        val index = body?.get("index")?.jsonPrimitive?.intOrNull ?: 0
        bridge.clickByText(text, index)
        return buildJsonObject { put("ok", true); put("action", "click_by_text"); put("text", text) }
    }

    private suspend fun handleExecuteShell(body: JsonObject?): JsonObject {
        val command = body?.get("command")?.jsonPrimitive?.contentOrNull ?: ""
        val output = bridge.executeShell(command)
        return buildJsonObject { put("output", output) }
    }

    private suspend fun handleDeviceInfo(body: JsonObject?): JsonObject {
        val type = body?.get("type")?.jsonPrimitive?.contentOrNull ?: "all"
        val info = bridge.queryDeviceInfo(type)
        return buildJsonObject { put("info", info) }
    }

    private suspend fun handleReadUiText(body: JsonObject?): JsonObject {
        val filter = body?.get("filter")?.jsonPrimitive?.contentOrNull ?: ""
        val exclude = body?.get("exclude")?.jsonPrimitive?.contentOrNull ?: ""
        val text = bridge.readUiText(filter, exclude)
        return buildJsonObject { put("text", text) }
    }

    private fun handleLongClick(body: JsonObject?): JsonObject {
        val x = body?.get("x")?.jsonPrimitive?.intOrNull ?: 0
        val y = body?.get("y")?.jsonPrimitive?.intOrNull ?: 0
        bridge.tap(x, y)
        return buildJsonObject { put("ok", true); put("action", "long_click"); put("x", x); put("y", y) }
    }

    private fun handleListPackages(): JsonObject {
        val apps = bridge.deviceCache.installedApps.value
        return buildJsonObject {
            putJsonArray("packages") {
                apps.forEach { app ->
                    add(buildJsonObject {
                        put("label", app.label)
                        put("packageName", app.packageName)
                    })
                }
            }
            put("count", apps.size)
        }
    }

    private fun handleAcceptCall(): JsonObject {
        bridge.acceptCall()
        return buildJsonObject { put("ok", true); put("action", "accept_call") }
    }

    private fun handleEndCall(): JsonObject {
        bridge.endCall()
        return buildJsonObject { put("ok", true); put("action", "end_call") }
    }

    private fun handleMakeCall(body: JsonObject?): JsonObject {
        val number = body?.get("number")?.jsonPrimitive?.contentOrNull ?: ""
        bridge.makeCall(number)
        return buildJsonObject { put("ok", true); put("action", "make_call"); put("number", number) }
    }

    private suspend fun handleOpenAudioChannel(body: JsonObject?): JsonObject {
        val mode = body?.get("mode")?.jsonPrimitive?.contentOrNull ?: "telephony"
        val resp = bridge.openAudioChannel(mode)
        return resp ?: buildJsonObject { put("ok", true); put("action", "open_audio_channel"); put("mode", mode) }
    }

    private suspend fun handleCloseAudioChannel(): JsonObject {
        val resp = bridge.closeAudioChannel()
        return resp ?: buildJsonObject { put("ok", true); put("action", "close_audio_channel") }
    }

    private fun sendHttpResponse(client: Socket, code: Int, json: JsonObject) {
        try {
            val body = json.toString().toByteArray(Charsets.UTF_8)
            val statusText = when (code) {
                200 -> "OK"
                404 -> "Not Found"
                500 -> "Internal Server Error"
                503 -> "Service Unavailable"
                else -> "Unknown"
            }
            val out = client.getOutputStream()
            val header = buildString {
                append("HTTP/1.1 $code $statusText\r\n")
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            out.write(header.toByteArray(Charsets.UTF_8))
            out.write(body)
            out.flush()
        } catch (e: Exception) {
            Log.w(TAG, "sendHttpResponse failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MeowHubBridge"
    }
}
