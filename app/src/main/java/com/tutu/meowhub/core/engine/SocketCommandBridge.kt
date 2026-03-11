package com.tutu.meowhub.core.engine

import android.util.Log
import com.tutu.meowhub.core.socket.TutuSocketClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

/**
 * 引擎 action -> Socket JSON 命令的桥接层。
 * 只依赖 TutuSocketClient 和 DeviceInfoCache，不依赖引擎逻辑。
 */
class SocketCommandBridge(
    private val socketClient: TutuSocketClient,
    val deviceCache: DeviceInfoCache
) {
    private val eventCollector = AccessibilityEventCollector()

    // 当前订阅的事件类型列表，重新订阅时复用
    private var subscribedEventTypes: List<String> = emptyList()
    private var subscribedDebounceMs: Int = 500

    val isAccessibilitySubscribed: Boolean get() = eventCollector.isActive

    /**
     * 订阅指定包名的无障碍事件。失败时仅记录日志，不影响后续执行。
     */
    suspend fun subscribeAccessibilityEvents(
        packages: List<String>,
        eventTypes: List<String> = DEFAULT_EVENT_TYPES,
        debounceMs: Int = 500
    ): Boolean {
        subscribedEventTypes = eventTypes
        subscribedDebounceMs = debounceMs

        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "subscribe_accessibility_events")
            put("reqId", reqId)
            putJsonArray("eventTypes") { eventTypes.forEach { add(it) } }
            putJsonArray("packages") { packages.forEach { add(it) } }
            put("debounceMs", debounceMs)
        }
        val resp = socketClient.sendAndWait(cmd, 5000)
        val success = resp?.get("success")?.jsonPrimitive?.booleanOrNull ?: false
        if (success) {
            eventCollector.start(socketClient.messages)
            Log.d(TAG, "Accessibility events subscribed: packages=$packages")
        } else {
            Log.w(TAG, "Accessibility subscription failed: ${resp?.toString()}")
        }
        return success
    }

    /**
     * 取消无障碍事件订阅并停止收集器。
     */
    fun unsubscribeAccessibilityEvents() {
        if (!eventCollector.isActive) return
        eventCollector.stop()
        val cmd = buildJsonObject {
            put("type", "unsubscribe_accessibility_events")
            put("reqId", socketClient.nextReqId())
        }
        socketClient.sendFireAndForget(cmd)
        Log.d(TAG, "Accessibility events unsubscribed")
    }

    /**
     * 取出所有已缓冲的事件并清空，保证不会混入下一步。
     */
    suspend fun collectAccessibilityEvents(): List<AccessibilityEvent> {
        if (!eventCollector.isActive) return emptyList()
        return eventCollector.collectAndClear()
    }

    /**
     * 等待满足条件的无障碍事件，超时返回 null。
     */
    suspend fun waitForAccessibilityEvent(
        timeoutMs: Long,
        predicate: (AccessibilityEvent) -> Boolean = { true }
    ): AccessibilityEvent? {
        if (!eventCollector.isActive) return null
        return eventCollector.waitForEvent(timeoutMs, predicate)
    }

    companion object {
        private const val TAG = "SocketCommandBridge"
        val DEFAULT_EVENT_TYPES = listOf(
            "window_state_changed",
            "window_content_changed",
            "view_clicked",
            "view_scrolled",
            "notification_state_changed"
        )
    }
    fun tap(x: Int, y: Int) {
        val (sw, sh) = deviceCache.screenSize.value
        val down = buildJsonObject {
            put("type", "touch")
            put("action", 0)
            put("x", x)
            put("y", y)
            put("screenWidth", sw)
            put("screenHeight", sh)
        }
        socketClient.sendFireAndForget(down)
        Thread.sleep(50)
        val up = buildJsonObject {
            put("type", "touch")
            put("action", 1)
            put("x", x)
            put("y", y)
            put("screenWidth", sw)
            put("screenHeight", sh)
        }
        socketClient.sendFireAndForget(up)
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300) {
        val cmd = buildJsonObject {
            put("type", "swipe")
            put("x1", x1); put("y1", y1)
            put("x2", x2); put("y2", y2)
            put("durationMs", durationMs)
        }
        socketClient.sendFireAndForget(cmd)
    }

    fun scroll(direction: String) {
        val (sw, sh) = deviceCache.screenSize.value
        val cx = sw / 2
        val (x1, y1, x2, y2) = when (direction) {
            "down" -> listOf(cx, (sh * 0.8).toInt(), cx, (sh * 0.15).toInt())
            "up" -> listOf(cx, (sh * 0.2).toInt(), cx, (sh * 0.85).toInt())
            "left" -> listOf((sw * 0.8).toInt(), sh / 2, (sw * 0.2).toInt(), sh / 2)
            "right" -> listOf((sw * 0.2).toInt(), sh / 2, (sw * 0.8).toInt(), sh / 2)
            else -> listOf(cx, (sh * 0.8).toInt(), cx, (sh * 0.15).toInt())
        }
        swipe(x1, y1, x2, y2, 350)
    }

    fun typeText(text: String) {
        val cmd = buildJsonObject {
            put("type", "text")
            put("text", text)
        }
        socketClient.sendFireAndForget(cmd)
    }

    fun pressHome() {
        socketClient.sendFireAndForget(buildJsonObject {
            put("type", "command")
            put("cmd", "HOME")
        })
    }

    fun pressBack() {
        socketClient.sendFireAndForget(buildJsonObject {
            put("type", "command")
            put("cmd", "BACK")
        })
    }

    fun startApp(appName: String) {
        val packageName = deviceCache.findPackageByName(appName)
        Log.d(TAG, "startApp: appName='$appName' -> packageName='$packageName'")
        val cmd = buildJsonObject {
            put("type", "start_app")
            put("package", packageName)
        }
        socketClient.sendFireAndForget(cmd)
    }

    /**
     * 截图并返回压缩后的 base64 字符串。
     */
    suspend fun takeScreenshot(): String? {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "screenshot")
            put("reqId", reqId)
            put("displayId", 0)
            put("maxSize", 1080)
            put("quality", 80)
        }
        val resp = socketClient.sendAndWait(cmd, 15000) ?: return null
        val rawBase64 = resp["data"]?.jsonPrimitive?.contentOrNull ?: return null
        return rawBase64
    }

    /**
     * 截图 + 压缩（灰度 50% + JPEG 20），用于 AI 分析。
     */
    suspend fun takeCompressedScreenshot(): String? {
        val raw = takeScreenshot() ?: return null
        return ScreenshotCompressor.compress(raw)
    }

    suspend fun getUiNodes(): JsonObject? {
        val cmd = buildJsonObject {
            put("type", "get_ui_nodes")
            put("mode", 2)
        }
        socketClient.sendFireAndForget(cmd)
        val raw = withTimeoutOrNull(15000) {
            socketClient.messages.first { msg ->
                msg["type"]?.jsonPrimitive?.contentOrNull == "ui_nodes"
            }
        } ?: return null

        // 服务端 nodes 字段是 JSON 字符串而非数组，需要解析
        val nodesValue = raw["nodes"] ?: return raw
        return if (nodesValue is JsonPrimitive && nodesValue.isString) {
            try {
                val parsed = Json.parseToJsonElement(nodesValue.content).jsonArray
                buildJsonObject {
                    put("type", "ui_nodes")
                    put("nodes", parsed)
                }
            } catch (e: Exception) {
                Log.w(TAG, "getUiNodes: failed to parse nodes string: ${e.message}")
                raw
            }
        } else {
            raw
        }
    }

    /**
     * 从 UI 节点树中提取所有文本。
     */
    suspend fun readUiText(filter: String = "", exclude: String = ""): String {
        val resp = getUiNodes() ?: return ""
        val texts = mutableListOf<String>()
        val nodeList = resp["nodes"]?.jsonArray
        if (nodeList != null) {
            for (element in nodeList) {
                extractTexts(element.jsonObject, texts)
            }
        } else {
            extractTexts(resp, texts)
        }

        var filtered = texts.filter { it.isNotBlank() }
        if (filter.isNotEmpty()) {
            filtered = filtered.filter { it.contains(filter) }
        }
        if (exclude.isNotEmpty()) {
            val exArr = exclude.split(",").map { it.trim() }
            filtered = filtered.filter { t -> exArr.none { t.contains(it) } }
        }
        return filtered.joinToString(" | ")
    }

    private fun extractTexts(node: JsonObject, texts: MutableList<String>) {
        node["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?.let { texts.add(it) }
        node["children"]?.jsonArray?.forEach { child ->
            extractTexts(child.jsonObject, texts)
        }
    }

    fun clickByText(text: String, index: Int = 0) {
        val cmd = buildJsonObject {
            put("type", "click_by_text")
            put("text", text)
            put("index", index)
        }
        socketClient.sendFireAndForget(cmd)
    }

    fun clickById(id: String, index: Int = 0) {
        val cmd = buildJsonObject {
            put("type", "click_by_id")
            put("id", id)
            put("index", index)
        }
        socketClient.sendFireAndForget(cmd)
    }

    fun acceptCall() {
        socketClient.sendFireAndForget(buildJsonObject {
            put("type", "accept_call")
            put("reqId", socketClient.nextReqId())
        })
    }

    fun endCall() {
        socketClient.sendFireAndForget(buildJsonObject {
            put("type", "end_call")
            put("reqId", socketClient.nextReqId())
        })
    }

    fun makeCall(number: String) {
        socketClient.sendFireAndForget(buildJsonObject {
            put("type", "make_call")
            put("reqId", socketClient.nextReqId())
            put("number", number)
        })
    }

    suspend fun openAudioChannel(mode: String = "telephony"): JsonObject? {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "open_audio_channel")
            put("reqId", reqId)
            put("mode", mode)
        }
        return socketClient.sendAndWait(cmd, 10000)
    }

    suspend fun closeAudioChannel(): JsonObject? {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "close_audio_channel")
            put("reqId", reqId)
        }
        return socketClient.sendAndWait(cmd, 5000)
    }

    suspend fun findElement(
        text: String = "",
        id: String = "",
        className: String = ""
    ): JsonObject? {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "find_element")
            put("reqId", reqId)
            if (text.isNotEmpty()) put("text", text)
            if (id.isNotEmpty()) put("id", id)
            if (className.isNotEmpty()) put("className", className)
        }
        return socketClient.sendAndWait(cmd, 10000)
    }

    suspend fun queryDeviceInfo(queryType: String): String {
        return when (queryType) {
            "apps" -> {
                val apps = deviceCache.installedApps.value
                if (apps.isNotEmpty()) {
                    "已安装第三方 App 共 ${apps.size} 个:\n" +
                        apps.joinToString("\n") {
                            if (it.versionName.isNotEmpty()) "${it.packageName} v${it.versionName}"
                            else it.packageName
                        }
                } else {
                    // 直接调用 socket list_packages
                    val reqId = socketClient.nextReqId()
                    val cmd = buildJsonObject {
                        put("type", "list_packages")
                        put("reqId", reqId)
                        put("thirdPartyOnly", true)
                    }
                    val resp = socketClient.sendAndWait(cmd, 15000)
                    val pkgs = resp?.get("packages")?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?: emptyList()
                    "已安装第三方 App 共 ${pkgs.size} 个:\n${pkgs.joinToString("\n")}"
                }
            }
            "battery" -> {
                val di = deviceCache.deviceInfo.value
                val battery = di?.get("battery")?.jsonObject
                if (battery != null) {
                    val level = battery["level"]?.jsonPrimitive?.intOrNull
                    val charging = battery["charging"]?.jsonPrimitive?.booleanOrNull
                    val status = battery["status"]?.jsonPrimitive?.contentOrNull
                    val plug = battery["plugType"]?.jsonPrimitive?.contentOrNull
                    "电量: ${level ?: "?"}%, 状态: ${status ?: "?"}, 充电: ${charging ?: "?"}, 插头: ${plug ?: "?"}"
                } else "电池信息暂不可用"
            }
            "storage" -> {
                val di = deviceCache.deviceInfo.value
                val storage = di?.get("storage")?.jsonObject
                if (storage != null) {
                    val total = storage["totalMB"]?.jsonPrimitive?.longOrNull
                    val avail = storage["availableMB"]?.jsonPrimitive?.longOrNull
                    "存储: 总计 ${total ?: "?"}MB, 可用 ${avail ?: "?"}MB"
                } else "存储信息暂不可用"
            }
            "wifi", "network" -> {
                val di = deviceCache.deviceInfo.value
                val network = di?.get("network")?.jsonObject
                val parts = mutableListOf<String>()
                val wifi = network?.get("wifi")?.jsonObject
                if (wifi != null) {
                    val connected = wifi["connected"]?.jsonPrimitive?.booleanOrNull
                    val ssid = wifi["ssid"]?.jsonPrimitive?.contentOrNull
                    val rssi = wifi["rssi"]?.jsonPrimitive?.intOrNull
                    parts.add("Wi-Fi: ${if (connected == true) "已连接 $ssid (${rssi}dBm)" else "未连接"}")
                }
                val mobile = network?.get("mobile")?.jsonObject
                if (mobile != null) {
                    val connected = mobile["connected"]?.jsonPrimitive?.booleanOrNull
                    val type = mobile["type"]?.jsonPrimitive?.contentOrNull
                    parts.add("移动网络: ${if (connected == true) "已连接 ($type)" else "未连接"}")
                }
                parts.joinToString("\n").ifEmpty { "网络信息暂不可用" }
            }
            "all" -> {
                val di = deviceCache.deviceInfo.value
                if (di != null) di.toString()
                else "设备信息暂不可用，请先连接"
            }
            else -> "不支持的查询类型: $queryType"
        }
    }

    suspend fun sendSms(destination: String, text: String): JsonObject? {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "send_sms")
            put("reqId", reqId)
            put("destination", destination)
            put("text", text)
        }
        return socketClient.sendAndWait(cmd, 15000)
    }

    suspend fun readSms(limit: Int = 20, unreadOnly: Boolean = false): JsonObject? {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "read_sms")
            put("reqId", reqId)
            put("limit", limit)
            put("unreadOnly", unreadOnly)
        }
        return socketClient.sendAndWait(cmd, 15000)
    }

    suspend fun getAppInfo(packageName: String): JsonObject? {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "get_app_info")
            put("reqId", reqId)
            put("package", packageName)
        }
        return socketClient.sendAndWait(cmd, 10000)
    }

    suspend fun forceStopApp(packageName: String): JsonObject? {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "force_stop_app")
            put("reqId", reqId)
            put("package", packageName)
        }
        return socketClient.sendAndWait(cmd, 10000)
    }

    suspend fun uninstallApp(packageName: String, keepData: Boolean = false): JsonObject? {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "uninstall_app")
            put("reqId", reqId)
            put("package", packageName)
            put("keepData", keepData)
        }
        return socketClient.sendAndWait(cmd, 30000)
    }

    suspend fun installApk(path: String): JsonObject? {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "install_apk")
            put("reqId", reqId)
            put("path", path)
        }
        return socketClient.sendAndWait(cmd, 60000)
    }

    suspend fun clearAppData(packageName: String): JsonObject? {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "clear_app_data")
            put("reqId", reqId)
            put("package", packageName)
        }
        return socketClient.sendAndWait(cmd, 15000)
    }

    suspend fun listPackages(thirdPartyOnly: Boolean = true, includeVersions: Boolean = false): JsonObject? {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "list_packages")
            put("reqId", reqId)
            put("thirdPartyOnly", thirdPartyOnly)
            put("includeVersions", includeVersions)
        }
        return socketClient.sendAndWait(cmd, 15000)
    }

    /**
     * 执行 shell 命令。
     * 响应格式: {"type":"shell_result","reqId":"...","exitCode":0,"stdout":"...","stderr":""}
     * 注意: 响应类型是 shell_result（非 execute_shell_result），无 success 字段，用 exitCode 判断。
     */
    suspend fun executeShell(command: String, timeout: Int = 30): String {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "execute_shell")
            put("reqId", reqId)
            put("command", command)
            put("timeout", timeout)
        }
        val resp = socketClient.sendAndWait(cmd, (timeout + 5) * 1000L) ?: return ""
        return resp["stdout"]?.jsonPrimitive?.contentOrNull
            ?: resp["output"]?.jsonPrimitive?.contentOrNull ?: ""
    }
}
