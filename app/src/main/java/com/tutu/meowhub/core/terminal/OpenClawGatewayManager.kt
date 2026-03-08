package com.tutu.meowhub.core.terminal

import android.content.Context
import android.util.Log
import com.termux.shared.termux.TermuxConstants
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class OpenClawGatewayManager(private val context: Context) {

    enum class GatewayState {
        STOPPED,
        STARTING,
        RUNNING,
        ERROR
    }

    private val _state = MutableStateFlow(GatewayState.STOPPED)
    val state: StateFlow<GatewayState> = _state.asStateFlow()

    private val _statusMessage = MutableStateFlow("Gateway not running")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    var onFirstHealthy: (() -> Unit)? = null
    var onSessionCreated: (() -> Unit)? = null

    private var healthCheckJob: Job? = null
    private var logReaderJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH
    private val home = TermuxConstants.TERMUX_HOME_DIR_PATH

    fun checkHealth(): Boolean {
        return try {
            val url = URL("http://127.0.0.1:18789/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            val healthy = code == 200
            Log.d(TAG, "checkHealth: code=$code, healthy=$healthy")
            healthy
        } catch (e: Exception) {
            Log.d(TAG, "checkHealth: false (${e.javaClass.simpleName}: ${e.message})")
            false
        }
    }

    fun syncRunningState() {
        if (_state.value != GatewayState.RUNNING) {
            _state.value = GatewayState.RUNNING
            _statusMessage.value = "OpenClaw Gateway running on :18789"
            Log.i(TAG, "syncRunningState: synced to RUNNING (pre-existing gateway)")
        }
    }

    fun refreshState() {
        val healthy = checkHealth()
        if (healthy) {
            _state.value = GatewayState.RUNNING
            _statusMessage.value = "OpenClaw Gateway running on :18789"
        } else if (_state.value == GatewayState.RUNNING) {
            _state.value = GatewayState.STOPPED
            _statusMessage.value = "Gateway stopped"
        }
    }

    fun startGateway(termuxService: MeowTermuxService) {
        if (_state.value == GatewayState.RUNNING) {
            Log.d(TAG, "startGateway: already running, skip")
            return
        }

        Log.i(TAG, "startGateway: starting")
        _state.value = GatewayState.STARTING
        _statusMessage.value = "Starting OpenClaw Gateway..."

        val openclawEntry = File("$prefix/lib/node_modules/openclaw/openclaw.mjs")
        val nodeBin = File("$prefix/bin/node")

        Log.i(TAG, "startGateway: checking files:" +
            " openclaw.mjs=${openclawEntry.exists()}" +
            " node=${nodeBin.exists()}")

        if (!openclawEntry.exists()) {
            Log.w(TAG, "startGateway: openclaw entry not found at ${openclawEntry.absolutePath}")
            _state.value = GatewayState.ERROR
            _statusMessage.value = "OpenClaw not installed"
            return
        }

        stopStaleGateway(termuxService)
    }

    private fun stopStaleGateway(termuxService: MeowTermuxService) {
        Log.i(TAG, "stopStaleGateway: stopping any existing gateway before launching")
        _statusMessage.value = "Cleaning up stale gateway..."

        scope.launch {
            // 1) Try HTTP shutdown (works across UIDs)
            try {
                val url = URL("http://127.0.0.1:18789/api/shutdown")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "POST"
                val code = conn.responseCode
                conn.disconnect()
                Log.i(TAG, "stopStaleGateway: HTTP /api/shutdown code=$code")
            } catch (e: Exception) {
                Log.d(TAG, "stopStaleGateway: HTTP shutdown failed: ${e.message}")
            }

            // 2) pkill + lock cleanup
            try {
                Runtime.getRuntime().exec(arrayOf(
                    "/system/bin/sh", "-c",
                    "pkill -f 'openclaw.*gateway' 2>/dev/null; " +
                    "rm -f $home/.openclaw/gateway.lock 2>/dev/null; " +
                    "true"
                )).waitFor()
                Log.i(TAG, "stopStaleGateway: pkill + lock cleanup done")
            } catch (e: Exception) {
                Log.w(TAG, "stopStaleGateway: cleanup failed: ${e.message}")
            }

            // 4) Wait and verify port is free
            delay(2000)
            val stillRunning = checkHealth()
            if (stillRunning) {
                Log.w(TAG, "stopStaleGateway: port 18789 still occupied by stale process, cannot clear")
                _statusMessage.value = "Port 18789 occupied by stale process. Restart device to clear."
                _state.value = GatewayState.ERROR
                return@launch
            }

            withContext(Dispatchers.Main) {
                launchGatewaySession(termuxService)
                startHealthCheck(termuxService)
            }
        }
    }

    fun stopGateway() {
        Log.i(TAG, "stopGateway: stopping")
        healthCheckJob?.cancel()
        _state.value = GatewayState.STOPPED
        _statusMessage.value = "Gateway stopped"

        scope.launch {
            try {
                Runtime.getRuntime().exec(arrayOf(
                    "/system/bin/sh", "-c",
                    "pkill -f 'openclaw gateway' 2>/dev/null || true"
                ))
                Log.i(TAG, "stopGateway: pkill sent")
            } catch (e: Exception) {
                Log.w(TAG, "stopGateway: pkill failed: ${e.message}")
            }
        }
    }

    private fun startHealthCheck(termuxService: MeowTermuxService) {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            // Initial wait for gateway startup (60-90 seconds on Android)
            delay(5000)
            var consecutiveFailures = 0
            var initialStartup = true
            val maxStartupWait = 120_000L // 2 minutes
            val startTime = System.currentTimeMillis()

            while (isActive) {
                val healthy = checkHealth()

                if (healthy) {
                    if (_state.value != GatewayState.RUNNING) {
                        _state.value = GatewayState.RUNNING
                        _statusMessage.value = "OpenClaw Gateway running on :18789"
                        Log.i(TAG, "Gateway health check passed")
                        onFirstHealthy?.invoke()
                    }
                    consecutiveFailures = 0
                    initialStartup = false
                } else {
                    consecutiveFailures++

                    if (initialStartup) {
                        val elapsed = System.currentTimeMillis() - startTime
                        if (elapsed > maxStartupWait) {
                            _state.value = GatewayState.ERROR
                            _statusMessage.value = "Gateway failed to start within ${maxStartupWait / 1000}s"
                            initialStartup = false
                        } else {
                            _statusMessage.value = "Waiting for Gateway to start... (${elapsed / 1000}s)"
                        }
                    } else if (consecutiveFailures >= 3) {
                        Log.w(TAG, "Gateway health check failed $consecutiveFailures times, attempting restart")
                        _state.value = GatewayState.STARTING
                        _statusMessage.value = "Gateway crashed, restarting..."

                        try {
                            launchGatewaySession(termuxService)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to restart gateway", e)
                        }
                        consecutiveFailures = 0
                    }
                }

                delay(if (initialStartup) 5000 else 15000)
            }
        }
    }

    var gatewaySession: TerminalSession? = null
        private set

    private fun launchGatewaySession(termuxService: MeowTermuxService) {
        val openclawEntry = "$prefix/lib/node_modules/openclaw/openclaw.mjs"
        Log.i(TAG, "launchGatewaySession: node=$prefix/bin/node entry=$openclawEntry")

        val session = termuxService.createSession()
        gatewaySession = session
        onSessionCreated?.invoke()

        val cmd = buildString {
            append("export PATH='$prefix/bin:$prefix/bin/applets:/system/bin' && ")
            append("export LD_LIBRARY_PATH='$prefix/lib' && ")
            append("export NODE_OPTIONS=\"-r \$HOME/bionic-compat.js\" && ")
            append("export SHARP_IGNORE_GLOBAL_LIBVIPS=1 && ")
            append("echo '[gateway] starting node process...' && ")
            append("exec $prefix/bin/node $openclawEntry gateway --allow-unconfigured --auth none --port 18789 --verbose")
        }
        Log.i(TAG, "launchGatewaySession: writing command to session (after init.sh)")
        scope.launch {
            delay(2000)
            session.write("$cmd\n")
        }

        readGatewaySessionOutputToLogcat(session)
    }

    private fun readGatewaySessionOutputToLogcat(session: TerminalSession) {
        logReaderJob?.cancel()
        logReaderJob = scope.launch {
            var lastRow = -1
            while (isActive) {
                delay(2000)
                try {
                    val emulator = session.emulator ?: continue
                    val screen = emulator.screen ?: continue
                    val curRow = emulator.cursorRow
                    if (curRow <= lastRow) continue

                    val start = if (lastRow < 0) maxOf(0, curRow - 10) else lastRow + 1
                    for (row in start..curRow) {
                        val line = screen.getSelectedText(0, row, emulator.mColumns, row + 1)?.trim()
                        if (!line.isNullOrEmpty()) {
                            Log.i("GatewayOutput", line)
                        }
                    }
                    lastRow = curRow
                } catch (_: Exception) {}
            }
        }
    }

    fun cleanup() {
        healthCheckJob?.cancel()
        logReaderJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val TAG = "OpenClawGateway"
    }
}
