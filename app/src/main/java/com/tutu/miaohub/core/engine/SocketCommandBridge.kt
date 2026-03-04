package com.tutu.miaohub.core.engine

import android.util.Log
import com.tutu.miaohub.core.socket.TutuSocketClient
import kotlinx.coroutines.delay
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
    suspend fun tap(x: Int, y: Int) {
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
        delay(50)
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

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300) {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "swipe")
            put("reqId", reqId)
            put("x1", x1); put("y1", y1)
            put("x2", x2); put("y2", y2)
            put("durationMs", durationMs)
        }
        socketClient.sendAndWait(cmd, 10000)
    }

    suspend fun scroll(direction: String) {
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

    suspend fun startApp(appName: String): JsonObject? {
        val packageName = deviceCache.findPackageByName(appName)
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "start_app")
            put("reqId", reqId)
            put("package", packageName)
        }
        val result = socketClient.sendAndWait(cmd, 10000)

        // App 切换时用新包名重新订阅，保证事件源正确
        if (eventCollector.isActive) {
            unsubscribeAccessibilityEvents()
            subscribeAccessibilityEvents(
                packages = listOf(packageName),
                eventTypes = subscribedEventTypes,
                debounceMs = subscribedDebounceMs
            )
        }

        return result
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

    suspend fun clickByText(text: String, index: Int = 0): JsonObject? {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "click_by_text")
            put("reqId", reqId)
            put("text", text)
            put("index", index)
        }
        return socketClient.sendAndWait(cmd, 10000)
    }

    suspend fun clickById(id: String, index: Int = 0): JsonObject? {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "click_by_id")
            put("reqId", reqId)
            put("id", id)
            put("index", index)
        }
        return socketClient.sendAndWait(cmd, 10000)
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
                            if (it.label != it.packageName) "${it.label} (${it.packageName})"
                            else it.packageName
                        }
                } else {
                    val resp = executeShell("pm list packages -3")
                    val pkgs = resp.lines().filter { it.startsWith("package:") }
                        .map { it.removePrefix("package:").trim() }
                    "已安装第三方 App 共 ${pkgs.size} 个:\n${pkgs.joinToString("\n")}"
                }
            }
            "battery" -> {
                val cached = deviceCache.batteryInfo.value
                if (cached != null) cached.toString()
                else executeShell("dumpsys battery")
            }
            "storage" -> executeShell("df -h /data")
            "wifi", "network" -> {
                val di = deviceCache.deviceInfo.value
                val parts = mutableListOf<String>()
                val wifiRaw = try { executeShell("dumpsys wifi | grep mWifiInfo") } catch (_: Exception) { "" }
                if (wifiRaw.isNotBlank()) parts.add("Wi-Fi: $wifiRaw")
                if (di != null) {
                    di["wlanIp"]?.jsonPrimitive?.contentOrNull?.let { parts.add("IP: $it") }
                }
                parts.joinToString("\n")
            }
            "bluetooth" -> {
                try {
                    executeShell("dumpsys bluetooth_manager | grep -E 'name:|address:|state:' | head -30")
                } catch (_: Exception) { "蓝牙信息获取失败" }
            }
            "all" -> {
                val battery = try { executeShell("dumpsys battery") } catch (_: Exception) { "" }
                val storage = try { executeShell("df -h /data") } catch (_: Exception) { "" }
                "=== 电池 ===\n$battery\n\n=== 存储 ===\n$storage"
            }
            else -> "不支持的查询类型: $queryType"
        }
    }

    suspend fun executeShell(command: String): String {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "execute_shell")
            put("reqId", reqId)
            put("command", command)
            put("timeout", 30)
        }
        val resp = socketClient.sendAndWait(cmd, 35000) ?: return ""
        return resp["output"]?.jsonPrimitive?.contentOrNull
            ?: resp["result"]?.jsonPrimitive?.contentOrNull ?: ""
    }
}
