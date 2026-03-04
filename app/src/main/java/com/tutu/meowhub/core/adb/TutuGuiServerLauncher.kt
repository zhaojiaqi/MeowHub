package com.tutu.meowhub.core.adb

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private const val TAG = "TutuGuiServerLauncher"

class TutuGuiServerLauncher(private val context: Context) {

    companion object {
        const val DEVICE_PATH = "/data/local/tmp/scrcpy-server.jar"
        const val SERVER_CLASS = "com.tutu.guiserver.Server"
        const val VERSION = "3.3.4"
        const val ASSET_NAME = "scrcpy-server"
        private const val PUSH_CHUNK_SIZE = 48000
    }

    sealed class LaunchState {
        data object Idle : LaunchState()
        data class Pushing(val progress: Int) : LaunchState()
        data object Starting : LaunchState()
        data object Verifying : LaunchState()
        data object Ready : LaunchState()
        data class Failed(val error: String) : LaunchState()
    }

    private fun createAdbClient(
        host: String, port: Int, key: AdbKey,
        onLog: ((String) -> Unit)? = null,
        retries: Int = 3, delayMs: Long = 1000
    ): AdbClient {
        var lastException: Exception? = null
        repeat(retries) { attempt ->
            try {
                val client = AdbClient(host, port, key)
                client.connect()
                return client
            } catch (e: javax.net.ssl.SSLProtocolException) {
                val msg = "ADB connect attempt ${attempt + 1}/$retries failed (SSL), retrying..."
                Log.w(TAG, msg, e)
                onLog?.invoke(msg)
                lastException = e
                Thread.sleep(delayMs)
            } catch (e: Exception) {
                throw e
            }
        }
        throw lastException!!
    }

    private fun execShell(
        host: String, port: Int, key: AdbKey,
        command: String, onLog: ((String) -> Unit)? = null,
        timeoutMs: Int = 30_000
    ): String {
        val sb = StringBuilder()
        createAdbClient(host, port, key, onLog).use { c ->
            c.shellCommand(command, timeoutMs = timeoutMs) { data ->
                sb.append(String(data))
            }
        }
        return sb.toString().trim()
    }

    fun checkRunning(host: String, port: Int, key: AdbKey, onLog: (String) -> Unit): Boolean {
        return try {
            val result = execShell(host, port, key,
                "pgrep -f guiserver.Server && echo running || echo not_found", onLog)
            onLog("Process check: $result")
            "running" in result
        } catch (e: Exception) {
            onLog("Process check failed: ${e.message}")
            false
        }
    }

    fun killAll(host: String, port: Int, key: AdbKey, onLog: (String) -> Unit) {
        onLog("Killing all server processes...")
        try {
            val result = execShell(host, port, key,
                "pkill -f guiserver.Server 2>/dev/null; sleep 1; " +
                "pgrep -f guiserver.Server | xargs kill -9 2>/dev/null; echo done", onLog)
            onLog("Kill result: $result")
        } catch (_: Exception) {}
    }

    suspend fun launch(
        host: String,
        port: Int,
        key: AdbKey,
        onState: (LaunchState) -> Unit,
        onLog: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onLog("[1/4] Connecting to ADB at $host:$port...")

            onLog("Checking for old server processes...")
            val oldRunning = checkRunning(host, port, key, onLog)
            if (oldRunning) {
                onLog("Old process found, killing...")
                killAll(host, port, key, onLog)
            } else {
                onLog("No old process found.")
            }

            onState(LaunchState.Pushing(0))
            val needsPush = checkNeedsPush(host, port, key, onLog)
            if (needsPush) {
                onLog("[2/4] Pushing server JAR...")
                pushServerJar(host, port, key, onState, onLog)
            } else {
                onLog("[2/4] Server JAR already up to date, skip push.")
            }

            onState(LaunchState.Starting)
            onLog("[3/4] Starting server process...")
            startServer(host, port, key, onLog)

            onState(LaunchState.Verifying)
            onLog("[4/4] Verifying server process...")
            val running = verifyServerProcess(host, port, key, onLog)

            if (running) {
                onState(LaunchState.Ready)
                onLog("Server is running!")
                true
            } else {
                onState(LaunchState.Failed("Server process not found after launch"))
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Launch failed", e)
            val message = e.message ?: e.javaClass.simpleName
            onState(LaunchState.Failed(message))
            onLog("Launch failed: $message")
            false
        }
    }

    private fun checkNeedsPush(
        host: String, port: Int, key: AdbKey,
        onLog: (String) -> Unit
    ): Boolean {
        val localMd5 = getAssetMd5() ?: return true

        return try {
            val remoteMd5 = execShell(host, port, key,
                "md5sum $DEVICE_PATH 2>/dev/null | cut -d' ' -f1", onLog)
            val match = remoteMd5.equals(localMd5, ignoreCase = true)
            onLog("MD5 check: local=$localMd5, remote=$remoteMd5, match=$match")
            !match
        } catch (e: Exception) {
            onLog("MD5 check failed: ${e.message}, will push.")
            true
        }
    }

    private fun getAssetMd5(): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            context.assets.open(ASSET_NAME).use { input ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "getAssetMd5 failed", e)
            null
        }
    }

    private fun pushServerJar(
        host: String, port: Int, key: AdbKey,
        onState: (LaunchState) -> Unit, onLog: (String) -> Unit
    ) {
        val assetBytes = context.assets.open(ASSET_NAME).use { it.readBytes() }
        val totalSize = assetBytes.size
        onLog("JAR size: ${totalSize / 1024} KB")

        var offset = 0
        var chunkIndex = 0
        while (offset < totalSize) {
            val end = minOf(offset + PUSH_CHUNK_SIZE, totalSize)
            val chunk = assetBytes.copyOfRange(offset, end)
            val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)

            val op = if (chunkIndex == 0) ">" else ">>"
            val cmd = "echo -n '$b64' | base64 -d $op $DEVICE_PATH"

            execShell(host, port, key, cmd, onLog)

            offset = end
            chunkIndex++
            val pct = (offset * 100) / totalSize
            onState(LaunchState.Pushing(pct))
            if (chunkIndex % 5 == 0 || offset >= totalSize) {
                onLog("Push progress: $pct% ($chunkIndex chunks)")
            }
        }

        val remoteSize = execShell(host, port, key, "wc -c < $DEVICE_PATH", onLog)
        onLog("Push complete. Remote size=$remoteSize bytes, expected=$totalSize")
        if (remoteSize != totalSize.toString()) {
            onLog("WARNING: Size mismatch! Push may be corrupted.")
        }
    }

    private suspend fun startServer(host: String, port: Int, key: AdbKey, onLog: (String) -> Unit) {
        val whoami = try {
            execShell(host, port, key, "id && whoami", onLog)
        } catch (_: Exception) { "unknown" }
        onLog("Shell identity: $whoami")

        val args = "$VERSION video=false audio=false control=false cleanup=false log_level=info"

        val launchCmd = "export CLASSPATH=$DEVICE_PATH && " +
            "setsid app_process / $SERVER_CLASS $args " +
            "</dev/null >/dev/null 2>&1 &"
        onLog("Command: $launchCmd")

        // Send command and don't wait for shell to finish.
        // Set a short socket read timeout so we don't block forever —
        // the background process keeps the shell fd alive, preventing A_CLSE.
        val client = createAdbClient(host, port, key, onLog)
        try {
            client.shellCommand(launchCmd, timeoutMs = 2000) { data ->
                onLog(String(data).trim())
            }
        } catch (_: java.net.SocketTimeoutException) {
            onLog("Launch command sent (shell still open, expected).")
        } finally {
            client.close()
        }
    }

    private suspend fun verifyServerProcess(
        host: String, port: Int, key: AdbKey, onLog: (String) -> Unit
    ): Boolean {
        delay(3000)

        val psResult = try {
            execShell(host, port, key,
                "pgrep -f guiserver.Server && echo running || echo not_found", onLog)
        } catch (e: Exception) { "check failed: ${e.message}" }
        onLog("Process check: $psResult")

        if ("running" in psResult) return true

        val logContent = try {
            execShell(host, port, key,
                "logcat -d -t 30 -s tutu AndroidRuntime 2>/dev/null || echo 'no logs'", onLog)
        } catch (_: Exception) { "" }
        if (logContent.isNotEmpty()) onLog("Server logcat:\n$logContent")
        return false
    }

}
