package com.tutu.miaohub.core.socket

import android.util.Log
import com.tutu.miaohub.MiaoApp
import com.tutu.miaohub.R
import com.tutu.miaohub.core.model.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class TutuSocketClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 28200
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val messages: SharedFlow<JsonObject> = _messages.asSharedFlow()

    private val _rawLog = MutableSharedFlow<LogEntry>(extraBufferCapacity = 256)
    val rawLog: SharedFlow<LogEntry> = _rawLog.asSharedFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.None)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val reqIdCounter = AtomicLong(0)

    private val ctx get() = MiaoApp.instance

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val direction: Direction,
        val content: String
    )

    enum class Direction { SENT, RECEIVED, SYSTEM }

    sealed class AuthState {
        data object None : AuthState()
        data object Authenticating : AuthState()
        data class Authenticated(
            val appName: String,
            val permissions: List<String>
        ) : AuthState()
        data class Failed(val reason: String) : AuthState()
        data class Revoked(val reason: String) : AuthState()
    }

    fun nextReqId(): String = "miao-${reqIdCounter.incrementAndGet()}"

    fun connect(token: String?, force: Boolean = false) {
        Log.d(TAG, "connect() called, state=${_connectionState.value}, force=$force, hasToken=${token != null}")
        if (_connectionState.value != ConnectionState.DISCONNECTED) {
            if (force) {
                Log.d(TAG, "Force reconnect, disconnecting first...")
                disconnect()
            } else {
                Log.d(TAG, "Already ${_connectionState.value}, skipping connect")
                return
            }
        }
        _connectionState.value = ConnectionState.CONNECTING
        _authState.value = AuthState.None
        log(Direction.SYSTEM, ctx.getString(R.string.connecting_to, host, port))

        scope.launch {
            try {
                Log.d(TAG, "Connecting to $host:$port...")
                val s = Socket(host, port)
                s.receiveBufferSize = 4 * 1024 * 1024
                socket = s
                reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8), 4 * 1024 * 1024)
                outputStream = s.getOutputStream()
                Log.d(TAG, "TCP connected to $host:$port")
                log(Direction.SYSTEM, ctx.getString(R.string.connected_to, host, port))

                if (token != null) {
                    _connectionState.value = ConnectionState.AUTHENTICATING
                    _authState.value = AuthState.Authenticating
                    log(Direction.SYSTEM, ctx.getString(R.string.auth_sending))
                    performAuth(token)
                } else {
                    Log.d(TAG, "No token, connected without auth")
                    _connectionState.value = ConnectionState.CONNECTED
                    startReading()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed to $host:$port", e)
                _connectionState.value = ConnectionState.DISCONNECTED
                log(Direction.SYSTEM, ctx.getString(R.string.connection_failed, e.message ?: ""))
            }
        }
    }

    private suspend fun performAuth(token: String) {
        try {
            Log.d(TAG, "Sending auth packet...")
            val authPacket = """{"type":"auth","token":"$token"}""" + "\n"
            outputStream?.let { out ->
                out.write(authPacket.toByteArray(Charsets.UTF_8))
                out.flush()
                log(Direction.SENT, authPacket.trim())
            } ?: run {
                Log.w(TAG, "Auth failed: outputStream is null")
                _authState.value = AuthState.Failed("not_connected")
                _connectionState.value = ConnectionState.DISCONNECTED
                return
            }

            Log.d(TAG, "Waiting for auth response (timeout=${AUTH_TIMEOUT_MS}ms)...")
            val responseLine = withTimeout(AUTH_TIMEOUT_MS) {
                reader?.readLine()
            }

            if (responseLine == null) {
                Log.w(TAG, "Auth failed: server closed connection")
                _authState.value = AuthState.Failed("connection_closed")
                _connectionState.value = ConnectionState.DISCONNECTED
                runCatching { socket?.close() }
                return
            }

            Log.d(TAG, "Auth response: $responseLine")
            log(Direction.RECEIVED, responseLine)
            val result = json.decodeFromString<JsonObject>(responseLine)
            val type = result["type"]?.jsonPrimitive?.content

            if (type == "auth_result") {
                val success = result["success"]?.jsonPrimitive?.boolean ?: false
                if (success) {
                    val appName = result["app_name"]?.jsonPrimitive?.content ?: ""
                    val permissions = result["permissions"]?.jsonArray
                        ?.map { it.jsonPrimitive.content } ?: emptyList()
                    _authState.value = AuthState.Authenticated(appName, permissions)
                    _connectionState.value = ConnectionState.CONNECTED
                    Log.i(TAG, "Auth success: appName=$appName, permissions=$permissions")
                    log(Direction.SYSTEM, ctx.getString(R.string.auth_success, appName))
                    startReading()
                } else {
                    val reason = result["reason"]?.jsonPrimitive?.content ?: "unknown"
                    Log.w(TAG, "Auth rejected: $reason")
                    _authState.value = AuthState.Failed(reason)
                    _connectionState.value = ConnectionState.DISCONNECTED
                    log(Direction.SYSTEM, ctx.getString(R.string.auth_failed, reason))
                    runCatching { socket?.close() }
                }
            } else {
                Log.w(TAG, "Auth unexpected response type: $type")
                _authState.value = AuthState.Failed("unexpected_response")
                _connectionState.value = ConnectionState.DISCONNECTED
                log(Direction.SYSTEM, ctx.getString(R.string.auth_unexpected_response))
                runCatching { socket?.close() }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Auth timeout after ${AUTH_TIMEOUT_MS}ms")
            _authState.value = AuthState.Failed("auth_timeout")
            _connectionState.value = ConnectionState.DISCONNECTED
            log(Direction.SYSTEM, ctx.getString(R.string.auth_timeout))
            runCatching { socket?.close() }
        } catch (e: Exception) {
            Log.e(TAG, "Auth error", e)
            _authState.value = AuthState.Failed(e.message ?: "unknown")
            _connectionState.value = ConnectionState.DISCONNECTED
            log(Direction.SYSTEM, ctx.getString(R.string.auth_error, e.message ?: ""))
            runCatching { socket?.close() }
        }
    }

    fun disconnect() {
        log(Direction.SYSTEM, ctx.getString(R.string.disconnecting))
        readJob?.cancel()
        readJob = null
        runCatching { socket?.close() }
        socket = null
        reader = null
        outputStream = null
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
        _authState.value = AuthState.None
    }

    fun sendFireAndForget(jsonObj: JsonObject) {
        val line = jsonObj.toString()
        scope.launch {
            try {
                outputStream?.let { out ->
                    out.write((line + "\n").toByteArray(Charsets.UTF_8))
                    out.flush()
                    log(Direction.SENT, line)
                } ?: log(Direction.SYSTEM, ctx.getString(R.string.not_connected_cannot_send))
            } catch (e: Exception) {
                log(Direction.SYSTEM, ctx.getString(R.string.send_failed, e.message ?: ""))
                handleDisconnect()
            }
        }
    }

    suspend fun sendAndWait(jsonObj: JsonObject, timeoutMs: Long = 30000): JsonObject? {
        val reqId = jsonObj["reqId"]?.jsonPrimitive?.content ?: return null
        val deferred = CompletableDeferred<JsonObject>()
        pendingRequests[reqId] = deferred

        val line = jsonObj.toString()
        val sendSuccess = withContext(Dispatchers.IO) {
            try {
                outputStream?.let { out ->
                    out.write((line + "\n").toByteArray(Charsets.UTF_8))
                    out.flush()
                    log(Direction.SENT, line)
                    true
                } ?: run {
                    log(Direction.SYSTEM, ctx.getString(R.string.not_connected_cannot_send))
                    false
                }
            } catch (e: Exception) {
                log(Direction.SYSTEM, ctx.getString(R.string.send_failed, e.message ?: ""))
                handleDisconnect()
                false
            }
        }

        if (!sendSuccess) {
            pendingRequests.remove(reqId)
            return null
        }

        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(reqId)
            log(Direction.SYSTEM, ctx.getString(R.string.request_timeout, reqId))
            null
        }
    }

    private fun startReading() {
        readJob = scope.launch {
            try {
                val r = reader ?: return@launch
                while (isActive) {
                    val line = try {
                        r.readLine()
                    } catch (e: OutOfMemoryError) {
                        log(Direction.SYSTEM, ctx.getString(R.string.read_oom))
                        continue
                    }
                    if (line == null) break
                    if (line.isBlank()) continue
                    log(Direction.RECEIVED, line)

                    val obj = try {
                        json.decodeFromString<JsonObject>(line)
                    } catch (e: Exception) {
                        log(Direction.SYSTEM, ctx.getString(R.string.json_parse_failed, e.message ?: ""))
                        continue
                    }

                    val type = obj["type"]?.jsonPrimitive?.content
                    if (type == "auth_revoked") {
                        val reason = obj["reason"]?.jsonPrimitive?.content ?: "unknown"
                        _authState.value = AuthState.Revoked(reason)
                        log(Direction.SYSTEM, ctx.getString(R.string.auth_revoked, reason))
                        _messages.tryEmit(obj)
                        break
                    }

                    val reqId = obj["reqId"]?.jsonPrimitive?.content
                    if (reqId != null) {
                        pendingRequests.remove(reqId)?.complete(obj)
                    }

                    _messages.tryEmit(obj)
                }
            } catch (e: Exception) {
                if (isActive) {
                    log(Direction.SYSTEM, ctx.getString(R.string.read_error, e.message ?: ""))
                }
            } finally {
                handleDisconnect()
            }
        }
    }

    private fun handleDisconnect() {
        if (_connectionState.value == ConnectionState.DISCONNECTED) return
        Log.d(TAG, "handleDisconnect: connection lost")
        _connectionState.value = ConnectionState.DISCONNECTED
        log(Direction.SYSTEM, ctx.getString(R.string.connection_lost))
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
    }

    fun log(message: String) {
        log(Direction.SYSTEM, message)
    }

    private fun log(direction: Direction, content: String) {
        val truncated = if (content.length > MAX_LOG_CONTENT_LENGTH) {
            content.take(MAX_LOG_CONTENT_LENGTH) + ctx.getString(R.string.log_truncated, content.length)
        } else {
            content
        }
        _rawLog.tryEmit(LogEntry(direction = direction, content = truncated))
    }

    companion object {
        private const val TAG = "TutuSocketClient"
        private const val MAX_LOG_CONTENT_LENGTH = 2000
        private const val AUTH_TIMEOUT_MS = 5000L
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
