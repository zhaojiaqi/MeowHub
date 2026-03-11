package com.tutu.meowhub.core.engine

import android.util.Log
import com.tutu.meowhub.core.model.ConnectionState
import com.tutu.meowhub.core.socket.TutuSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

/**
 * Socket 连接成功后自动预获取设备信息并缓存，
 * 供引擎快速查找 APP 包名、屏幕尺寸等。
 */
class DeviceInfoCache(private val socketClient: TutuSocketClient) {

    data class AppInfo(
        val label: String,
        val packageName: String,
        val versionName: String = ""
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _deviceInfo = MutableStateFlow<JsonObject?>(null)
    val deviceInfo: StateFlow<JsonObject?> = _deviceInfo.asStateFlow()

    private val _screenSize = MutableStateFlow(1080 to 2400)
    val screenSize: StateFlow<Pair<Int, Int>> = _screenSize.asStateFlow()

    private val _batteryInfo = MutableStateFlow<JsonObject?>(null)
    val batteryInfo: StateFlow<JsonObject?> = _batteryInfo.asStateFlow()

    private var initialized = false

    /**
     * 监听连接状态，连接成功时自动刷新缓存。
     */
    fun observeConnection() {
        scope.launch {
            socketClient.connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED && !initialized) {
                    refresh()
                }
            }
        }
    }

    suspend fun refresh() {
        initialized = true
        fetchInstalledApps()
        fetchDeviceInfo()
    }

    private suspend fun fetchInstalledApps() {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "list_packages")
            put("reqId", reqId)
            put("thirdPartyOnly", true)
            put("includeVersions", true)
        }
        val resp = socketClient.sendAndWait(cmd, 15000) ?: return
        val success = resp["success"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!success) return

        val packages = resp["packages"]?.jsonArray ?: return
        val apps = packages.mapNotNull { elem ->
            // includeVersions=true: {"package":"com.xx","versionName":"1.0","versionCode":1}
            val obj = elem.jsonObject
            val pkg = obj["package"]?.jsonPrimitive?.contentOrNull
                ?: obj["packageName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            AppInfo(
                label = pkg,
                packageName = pkg,
                versionName = obj["versionName"]?.jsonPrimitive?.contentOrNull ?: ""
            )
        }
        _installedApps.value = apps
    }

    /**
     * get_device_info 返回广播格式 {"type":"device_info","info":"<JSON字符串>"}，
     * 没有 reqId，需要通过 messages flow 监听。
     */
    private suspend fun fetchDeviceInfo() {
        val cmd = buildJsonObject {
            put("type", "get_device_info")
        }
        socketClient.sendFireAndForget(cmd)
        val resp = withTimeoutOrNull(10000) {
            socketClient.messages.first { msg ->
                msg["type"]?.jsonPrimitive?.contentOrNull == "device_info"
            }
        } ?: return

        // info 字段是 JSON 字符串，需要二次解析
        val infoStr = resp["info"]?.jsonPrimitive?.contentOrNull ?: return
        val info = try {
            Json.parseToJsonElement(infoStr).jsonObject
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse device_info.info: ${e.message}")
            return
        }

        _deviceInfo.value = info

        // 提取屏幕尺寸: info.display.width / info.display.height
        val display = info["display"]?.jsonObject
        val w = display?.get("width")?.jsonPrimitive?.intOrNull
        val h = display?.get("height")?.jsonPrimitive?.intOrNull
        if (w != null && h != null && w > 0 && h > 0) {
            _screenSize.value = w to h
        }

        // 提取电池信息
        val battery = info["battery"]?.jsonObject
        if (battery != null) {
            _batteryInfo.value = battery
        }
    }

    /**
     * 根据应用名（中文/英文）模糊匹配包名。
     * 先精确匹配 label，再尝试包含匹配，最后当作包名直接返回。
     */
    fun findPackageByName(appName: String): String {
        val apps = _installedApps.value
        if (apps.isEmpty()) return appName

        apps.find { it.label.equals(appName, ignoreCase = true) }
            ?.let { return it.packageName }

        apps.find { it.label.contains(appName, ignoreCase = true) }
            ?.let { return it.packageName }

        apps.find { it.packageName.contains(appName, ignoreCase = true) }
            ?.let { return it.packageName }

        return appName
    }

    fun buildDeviceContext(): String = buildString {
        val apps = _installedApps.value
        if (apps.isNotEmpty()) {
            appendLine("已安装第三方 App (${apps.size} 个):")
            apps.forEach { app ->
                val display = if (app.versionName.isNotEmpty()) "${app.packageName} v${app.versionName}" else app.packageName
                appendLine("  $display")
            }
        }
        val di = _deviceInfo.value
        if (di != null) {
            val model = di["deviceModel"]?.jsonPrimitive?.contentOrNull
            val androidVer = di["androidVersion"]?.jsonPrimitive?.contentOrNull
            if (model != null) appendLine("设备: $model")
            if (androidVer != null) appendLine("Android: $androidVer")
            val battery = di["battery"]?.jsonObject
            val level = battery?.get("level")?.jsonPrimitive?.intOrNull
            val charging = battery?.get("charging")?.jsonPrimitive?.booleanOrNull
            if (level != null) {
                appendLine("电池: ${level}%${if (charging == true) " (充电中)" else ""}")
            }
        }
        val (w, h) = _screenSize.value
        appendLine("屏幕: ${w}x${h}")
    }

    fun invalidate() {
        initialized = false
    }

    companion object {
        private const val TAG = "DeviceInfoCache"
    }
}
