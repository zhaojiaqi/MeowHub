package com.tutu.meowhub.feature.settings

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tutu.meowhub.MeowApp
import com.tutu.meowhub.core.adb.*
import com.tutu.meowhub.core.model.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.net.ssl.SSLProtocolException
import java.net.ConnectException

private const val TAG = "AdbViewModel"
private const val ADB_PREFS = "adb_prefs"

class AdbViewModel(application: Application) : AndroidViewModel(application) {

    enum class ServerState {
        IDLE,
        CHECKING,
        PAIRING_SEARCHING,
        PAIRING_INPUT,
        PAIRING_WORKING,
        CONNECTING,
        PUSHING,
        STARTING,
        WAITING,
        RUNNING,
        ERROR
    }

    enum class AutoConnectResult {
        PENDING,
        ALREADY_RUNNING,
        NO_KEY,
        CONNECTED,
        WIRELESS_DEBUG_OFF,
        KEY_EXPIRED
    }

    data class UiState(
        val serverState: ServerState = ServerState.IDLE,
        val hasPairedKey: Boolean = false,
        val connectPort: Int = -1,
        val pushProgress: Int = 0,
        val errorMessage: String? = null,
        val logs: List<String> = emptyList()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _autoConnectResult = MutableStateFlow(AutoConnectResult.PENDING)
    val autoConnectResult: StateFlow<AutoConnectResult> = _autoConnectResult.asStateFlow()

    private val context: Context get() = getApplication()

    private var connectMdns: AdbMdns? = null

    private val pairingResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val success = intent.getBooleanExtra(AdbPairingService.EXTRA_SUCCESS, false)
            onPairingComplete(success)
        }
    }

    private val app get() = context.applicationContext as MeowApp

    init {
        val socketConnected = app.tutuClient.connectionState.value == ConnectionState.CONNECTED
        _uiState.value = _uiState.value.copy(
            hasPairedKey = hasStoredKey(),
            serverState = if (socketConnected) ServerState.RUNNING else ServerState.IDLE
        )
        context.registerReceiver(
            pairingResultReceiver,
            IntentFilter(AdbPairingService.ACTION_PAIRING_RESULT),
            Context.RECEIVER_NOT_EXPORTED
        )

        viewModelScope.launch {
            app.tutuClient.connectionState.collect { connState ->
                val current = _uiState.value.serverState
                when (connState) {
                    ConnectionState.CONNECTED -> {
                        if (current != ServerState.RUNNING) {
                            _uiState.value = _uiState.value.copy(serverState = ServerState.RUNNING)
                        }
                    }
                    ConnectionState.DISCONNECTED -> {
                        if (current == ServerState.RUNNING) {
                            _uiState.value = _uiState.value.copy(serverState = ServerState.IDLE)
                        }
                    }
                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            waitForSocketThenAutoConnect()
        }
    }

    private suspend fun waitForSocketThenAutoConnect() {
        val connState = app.tutuClient.connectionState.value
        if (connState == ConnectionState.CONNECTED) {
            Log.d(TAG, "Auto-connect: socket already connected, skip ADB")
            _autoConnectResult.value = AutoConnectResult.ALREADY_RUNNING
            return
        }

        if (connState == ConnectionState.CONNECTING || connState == ConnectionState.AUTHENTICATING) {
            Log.d(TAG, "Auto-connect: waiting for socket connect result...")
            val settled = withTimeoutOrNull(8000L) {
                app.tutuClient.connectionState.first { state ->
                    state == ConnectionState.CONNECTED || state == ConnectionState.DISCONNECTED
                }
            }
            if (settled == ConnectionState.CONNECTED) {
                Log.d(TAG, "Auto-connect: socket connected after waiting, skip ADB")
                _autoConnectResult.value = AutoConnectResult.ALREADY_RUNNING
                return
            }
        }

        Log.d(TAG, "Auto-connect: socket not available, trying ADB flow...")
        tryAutoConnect()
    }

    private suspend fun tryAutoConnect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            _autoConnectResult.value = AutoConnectResult.NO_KEY
            return
        }

        if (!hasStoredKey()) {
            Log.d(TAG, "Auto-connect: no paired key found")
            _autoConnectResult.value = AutoConnectResult.NO_KEY
            return
        }

        Log.d(TAG, "Auto-connect: has key, searching for ADB port...")
        val portResult = withTimeoutOrNull(6000L) {
            suspendMdnsDiscovery()
        }

        if (portResult == null || portResult <= 0) {
            Log.d(TAG, "Auto-connect: mDNS timeout, wireless debugging likely off")
            _autoConnectResult.value = AutoConnectResult.WIRELESS_DEBUG_OFF
            return
        }

        Log.d(TAG, "Auto-connect: found ADB port $portResult, attempting connection...")
        try {
            withContext(Dispatchers.IO) {
                val prefs = context.getSharedPreferences(ADB_PREFS, Context.MODE_PRIVATE)
                val key = AdbKey(PreferenceAdbKeyStore(prefs), "meowhub")
                val client = AdbClient("127.0.0.1", portResult, key)
                client.connect()
                client.close()
            }

            Log.d(TAG, "Auto-connect: ADB connection verified, starting server...")
            _uiState.value = _uiState.value.copy(connectPort = portResult)
            startServer()
            _autoConnectResult.value = AutoConnectResult.CONNECTED
        } catch (e: SSLProtocolException) {
            Log.w(TAG, "Auto-connect: SSL error, key expired", e)
            _uiState.value = _uiState.value.copy(hasPairedKey = false)
            _autoConnectResult.value = AutoConnectResult.KEY_EXPIRED
        } catch (e: Exception) {
            Log.w(TAG, "Auto-connect: ADB connection failed", e)
            _autoConnectResult.value = AutoConnectResult.WIRELESS_DEBUG_OFF
        }
    }

    private suspend fun suspendMdnsDiscovery(): Int {
        val result = kotlinx.coroutines.CompletableDeferred<Int>()
        val mdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { port ->
            if (port > 0) result.complete(port)
        }
        mdns.start()
        try {
            return result.await()
        } finally {
            mdns.stop()
        }
    }

    fun dismissAutoConnectResult() {
        _autoConnectResult.value = AutoConnectResult.PENDING
    }

    private fun hasStoredKey(): Boolean {
        val prefs = context.getSharedPreferences(ADB_PREFS, Context.MODE_PRIVATE)
        return PreferenceAdbKeyStore(prefs).get() != null
    }

    private fun addLog(msg: String) {
        Log.d(TAG, msg)
        _uiState.value = _uiState.value.copy(
            logs = _uiState.value.logs + msg
        )
    }

    fun clearLogs() {
        _uiState.value = _uiState.value.copy(logs = emptyList())
    }

    fun startPairing() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            _uiState.value = _uiState.value.copy(
                serverState = ServerState.ERROR,
                errorMessage = "Wireless debugging requires Android 11+"
            )
            return
        }
        _uiState.value = _uiState.value.copy(serverState = ServerState.PAIRING_SEARCHING)
        addLog("Starting mDNS search for pairing service...")

        val intent = AdbPairingService.startIntent(context)
        context.startForegroundService(intent)
    }

    private fun onPairingComplete(success: Boolean) {
        if (success) {
            _uiState.value = _uiState.value.copy(
                serverState = ServerState.IDLE,
                hasPairedKey = true
            )
            addLog("Pairing completed successfully.")
            startServer()
        } else {
            _uiState.value = _uiState.value.copy(
                serverState = ServerState.ERROR,
                errorMessage = "Pairing failed"
            )
            addLog("Pairing failed.")
        }
    }

    fun startServer() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            _uiState.value = _uiState.value.copy(
                serverState = ServerState.ERROR,
                errorMessage = "Wireless debugging requires Android 11+"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            serverState = ServerState.CHECKING,
            errorMessage = null
        )
        addLog("Searching for wireless debugging port...")

        findAdbPortAndRun { host, port, key ->
            if (isSocketReachable()) {
                addLog("Server is already running and reachable.")
                _uiState.value = _uiState.value.copy(serverState = ServerState.RUNNING)
                triggerSocketConnect()
            } else {
                val launcher = TutuGuiServerLauncher(context)
                app.tutuClient.disconnect()
                doLaunch(host, port, key, launcher)
            }
        }
    }

    fun restartServer() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        _uiState.value = _uiState.value.copy(
            serverState = ServerState.CHECKING,
            errorMessage = null
        )
        addLog("Restarting server...")

        findAdbPortAndRun { host, port, key ->
            val launcher = TutuGuiServerLauncher(context)
            launcher.killAll(host, port, key) { addLog(it) }
            app.tutuClient.disconnect()
            doLaunch(host, port, key, launcher)
        }
    }

    private fun findAdbPortAndRun(action: suspend (String, Int, AdbKey) -> Unit) {
        connectMdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { port ->
            if (port > 0) {
                connectMdns?.stop()
                connectMdns = null
                _uiState.value = _uiState.value.copy(connectPort = port)
                addLog("Found ADB port: $port")

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val prefs = context.getSharedPreferences(ADB_PREFS, Context.MODE_PRIVATE)
                        val key = try {
                            AdbKey(PreferenceAdbKeyStore(prefs), "meowhub")
                        } catch (e: Throwable) {
                            handleError(AdbKeyException(e))
                            return@launch
                        }
                        action("127.0.0.1", port, key)
                    } catch (e: Exception) {
                        handleError(e)
                    }
                }
            }
        }
        connectMdns?.start()
    }

    private suspend fun doLaunch(host: String, port: Int, key: AdbKey, launcher: TutuGuiServerLauncher) {
        val success = launcher.launch(
            host = host,
            port = port,
            key = key,
            onState = { state ->
                val newState = when (state) {
                    is TutuGuiServerLauncher.LaunchState.Idle -> ServerState.IDLE
                    is TutuGuiServerLauncher.LaunchState.Pushing -> {
                        _uiState.value = _uiState.value.copy(pushProgress = state.progress)
                        ServerState.PUSHING
                    }
                    is TutuGuiServerLauncher.LaunchState.Starting -> ServerState.STARTING
                    is TutuGuiServerLauncher.LaunchState.Verifying -> ServerState.WAITING
                    is TutuGuiServerLauncher.LaunchState.Ready -> ServerState.RUNNING
                    is TutuGuiServerLauncher.LaunchState.Failed -> {
                        _uiState.value = _uiState.value.copy(errorMessage = state.error)
                        ServerState.ERROR
                    }
                }
                _uiState.value = _uiState.value.copy(serverState = newState)
            },
            onLog = { msg -> addLog(msg) }
        )

        if (success) triggerSocketConnect()
    }

    private fun isSocketReachable(): Boolean {
        return try {
            java.net.Socket().use { s ->
                s.connect(java.net.InetSocketAddress("127.0.0.1", 28200), 2000)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun triggerSocketConnect() {
        addLog("Server ready. Triggering socket connection...")
        app.connectWithAuth(force = true)
    }

    private fun handleError(e: Throwable) {
        Log.e(TAG, "Error", e)
        val needsRepair = e is SSLProtocolException
        val message = when (e) {
            is AdbKeyException -> "Key store error. Try clearing app data."
            is ConnectException -> "Cannot connect to ADB. Is wireless debugging enabled?"
            is SSLProtocolException -> "Certificate rejected. Please re-pair the device."
            is AdbInvalidPairingCodeException -> "Wrong pairing code."
            else -> e.message ?: e.javaClass.simpleName
        }
        _uiState.value = _uiState.value.copy(
            serverState = ServerState.ERROR,
            errorMessage = message,
            hasPairedKey = if (needsRepair) false else _uiState.value.hasPairedKey
        )
        addLog("Error: $message")
    }

    fun stopServer() {
        addLog("Stopping server via shutdown command...")
        app.tutuClient.sendFireAndForget(
            kotlinx.serialization.json.buildJsonObject { put("type", "shutdown") }
        )
        app.tutuClient.disconnect()
        _uiState.value = _uiState.value.copy(serverState = ServerState.IDLE)
        addLog("Server stopped.")
    }

    fun resetState() {
        _uiState.value = _uiState.value.copy(
            serverState = ServerState.IDLE,
            errorMessage = null
        )
    }

    override fun onCleared() {
        super.onCleared()
        connectMdns?.stop()
        try {
            context.unregisterReceiver(pairingResultReceiver)
        } catch (_: Exception) {}
    }
}
