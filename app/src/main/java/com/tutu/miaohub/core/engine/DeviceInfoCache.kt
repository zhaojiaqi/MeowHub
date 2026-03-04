package com.tutu.miaohub.core.engine

import com.tutu.miaohub.core.model.ConnectionState
import com.tutu.miaohub.core.socket.TutuSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        fetchBatteryInfo()
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
            val obj = elem.jsonObject
            val pkg = obj["packageName"]?.jsonPrimitive?.contentOrNull
                ?: obj["package"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            AppInfo(
                label = obj["label"]?.jsonPrimitive?.contentOrNull ?: pkg,
                packageName = pkg,
                versionName = obj["versionName"]?.jsonPrimitive?.contentOrNull ?: ""
            )
        }
        _installedApps.value = apps
    }

    private suspend fun fetchDeviceInfo() {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "get_device_info")
            put("reqId", reqId)
        }
        val resp = socketClient.sendAndWait(cmd, 10000) ?: return

        _deviceInfo.value = resp

        val w = resp["screenWidth"]?.jsonPrimitive?.intOrNull
            ?: resp["width"]?.jsonPrimitive?.intOrNull
        val h = resp["screenHeight"]?.jsonPrimitive?.intOrNull
            ?: resp["height"]?.jsonPrimitive?.intOrNull
        if (w != null && h != null && w > 0 && h > 0) {
            _screenSize.value = w to h
        }
    }

    private suspend fun fetchBatteryInfo() {
        val reqId = socketClient.nextReqId()
        val cmd = buildJsonObject {
            put("type", "get_battery_stats")
            put("reqId", reqId)
        }
        val resp = socketClient.sendAndWait(cmd, 10000)
        if (resp != null) _batteryInfo.value = resp
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
            apps.forEach { appendLine("  ${it.label} (${it.packageName})") }
        }
        val di = _deviceInfo.value
        if (di != null) {
            val model = di["model"]?.jsonPrimitive?.contentOrNull
            val brand = di["brand"]?.jsonPrimitive?.contentOrNull
            val androidVer = di["androidVersion"]?.jsonPrimitive?.contentOrNull
                ?: di["android_version"]?.jsonPrimitive?.contentOrNull
            if (model != null || brand != null) {
                appendLine("设备: ${brand ?: ""} ${model ?: ""}")
            }
            if (androidVer != null) appendLine("Android: $androidVer")
        }
        val (w, h) = _screenSize.value
        appendLine("屏幕: ${w}x${h}")
    }

    fun invalidate() {
        initialized = false
    }
}
