package com.tutu.meowhub.core.auth

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MeowAppAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "MeowAppAuth"
        private const val BASE_URL = "https://tutuai.me/api/meowapp"
        private const val PREF_NAME = "meowapp_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USER_JSON = "user_json"
        private const val KEY_APP_KEY_JSON = "app_key_json"
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT = 30_000
        private const val REFRESH_THRESHOLD_SECS = 86400L // 1 day
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<MeowAppUser?>(null)
    val currentUser: StateFlow<MeowAppUser?> = _currentUser.asStateFlow()

    private val _appKeyData = MutableStateFlow<MeowAppKeyData?>(null)
    val appKeyData: StateFlow<MeowAppKeyData?> = _appKeyData.asStateFlow()

    init {
        restoreState()
    }

    private fun restoreState() {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)
        if (token != null && System.currentTimeMillis() / 1000 < expiresAt) {
            _isLoggedIn.value = true
            prefs.getString(KEY_USER_JSON, null)?.let { userJson ->
                try {
                    _currentUser.value = json.decodeFromString<MeowAppUser>(userJson)
                } catch (_: Exception) {}
            }
            prefs.getString(KEY_APP_KEY_JSON, null)?.let { keyJson ->
                try {
                    _appKeyData.value = json.decodeFromString<MeowAppKeyData>(keyJson)
                } catch (_: Exception) {}
            }
        } else if (token != null) {
            prefs.edit().clear().apply()
        }
    }

    fun getAccessToken(): String? {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)
        if (System.currentTimeMillis() / 1000 >= expiresAt) {
            logout()
            return null
        }
        return token
    }

    private fun needsRefresh(): Boolean {
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)
        val remaining = expiresAt - System.currentTimeMillis() / 1000
        return remaining in 1..REFRESH_THRESHOLD_SECS
    }

    private fun getDeviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    private fun getDeviceInfo(): String = "MeowHub/1.0 ${Build.MODEL} Android ${Build.VERSION.RELEASE}"

    // --- Public API ---

    suspend fun sendCode(target: String, channel: String? = null): Result<MeowAppSendCodeResponse> =
        withContext(Dispatchers.IO) {
            try {
                val body = buildString {
                    append("{\"target\":\"$target\"")
                    if (channel != null) append(",\"channel\":\"$channel\"")
                    append("}")
                }
                val resp = post("$BASE_URL/send_code.php", body, auth = false)
                val parsed = json.decodeFromString<MeowAppSendCodeResponse>(resp)
                if (parsed.success) Result.success(parsed)
                else Result.failure(Exception(parsed.message ?: "发送失败"))
            } catch (e: Exception) {
                Log.e(TAG, "sendCode error", e)
                Result.failure(e)
            }
        }

    suspend fun login(target: String, code: String, channel: String? = null): Result<MeowAppLoginResponse> =
        withContext(Dispatchers.IO) {
            try {
                val deviceId = getDeviceId()
                val deviceInfo = getDeviceInfo()
                val body = buildString {
                    append("{\"target\":\"$target\",\"code\":\"$code\"")
                    if (channel != null) append(",\"channel\":\"$channel\"")
                    append(",\"device_id\":\"$deviceId\",\"device_info\":\"$deviceInfo\"")
                    append("}")
                }
                val resp = post("$BASE_URL/login.php", body, auth = false)
                val parsed = json.decodeFromString<MeowAppLoginResponse>(resp)
                if (parsed.success && parsed.accessToken != null && parsed.user != null) {
                    saveLoginState(parsed.accessToken, parsed.expiresIn, parsed.user)
                    Result.success(parsed)
                } else {
                    Result.failure(Exception(parsed.message ?: "登录失败"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "login error", e)
                Result.failure(e)
            }
        }

    suspend fun refreshToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val resp = post("$BASE_URL/refresh.php", "", auth = true)
            val parsed = json.decodeFromString<MeowAppRefreshResponse>(resp)
            if (parsed.success && parsed.accessToken != null) {
                val expiresAtSec = System.currentTimeMillis() / 1000 + parsed.expiresIn
                prefs.edit()
                    .putString(KEY_ACCESS_TOKEN, parsed.accessToken)
                    .putLong(KEY_EXPIRES_AT, expiresAtSec)
                    .apply()
                Result.success(parsed.accessToken)
            } else {
                Result.failure(Exception("刷新失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshToken error", e)
            Result.failure(e)
        }
    }

    suspend fun fetchProfile(): Result<MeowAppProfile> = withContext(Dispatchers.IO) {
        try {
            autoRefreshIfNeeded()
            val resp = get("$BASE_URL/profile.php")
            val parsed = json.decodeFromString<MeowAppProfile>(resp)
            if (parsed.success && parsed.user != null) {
                _currentUser.value = parsed.user
                prefs.edit().putString(KEY_USER_JSON, json.encodeToString(parsed.user)).apply()
            }
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "fetchProfile error", e)
            Result.failure(e)
        }
    }

    suspend fun fetchCredits(): Result<MeowAppCreditsResponse> = withContext(Dispatchers.IO) {
        try {
            autoRefreshIfNeeded()
            val resp = get("$BASE_URL/credits.php")
            val parsed = json.decodeFromString<MeowAppCreditsResponse>(resp)
            if (parsed.success) {
                _currentUser.value = _currentUser.value?.copy(credits = parsed.credits)
            }
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "fetchCredits error", e)
            Result.failure(e)
        }
    }

    suspend fun fetchOrders(page: Int = 1, pageSize: Int = 20): Result<MeowAppOrdersResponse> =
        withContext(Dispatchers.IO) {
            try {
                autoRefreshIfNeeded()
                val resp = get("$BASE_URL/orders.php?page=$page&page_size=$pageSize")
                val parsed = json.decodeFromString<MeowAppOrdersResponse>(resp)
                Result.success(parsed)
            } catch (e: Exception) {
                Log.e(TAG, "fetchOrders error", e)
                Result.failure(e)
            }
        }

    suspend fun activateAppKey(): Result<MeowAppKeyResponse> = withContext(Dispatchers.IO) {
        try {
            autoRefreshIfNeeded()
            val resp = post("$BASE_URL/app_key.php", "{\"action\":\"activate\"}", auth = true)
            val parsed = json.decodeFromString<MeowAppKeyResponse>(resp)
            if (parsed.success && parsed.data != null) {
                _appKeyData.value = parsed.data
                prefs.edit().putString(KEY_APP_KEY_JSON, json.encodeToString(parsed.data)).apply()
            }
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "activateAppKey error", e)
            Result.failure(e)
        }
    }

    suspend fun queryAppKey(): Result<MeowAppKeyResponse> = withContext(Dispatchers.IO) {
        try {
            autoRefreshIfNeeded()
            val resp = post("$BASE_URL/app_key.php", "{\"action\":\"info\"}", auth = true)
            val parsed = json.decodeFromString<MeowAppKeyResponse>(resp)
            if (parsed.success && parsed.data != null) {
                _appKeyData.value = parsed.data
                prefs.edit().putString(KEY_APP_KEY_JSON, json.encodeToString(parsed.data)).apply()
            }
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "queryAppKey error", e)
            Result.failure(e)
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        _isLoggedIn.value = false
        _currentUser.value = null
        _appKeyData.value = null
        Log.d(TAG, "User logged out")
    }

    // --- Internal ---

    private fun saveLoginState(token: String, expiresIn: Long, user: MeowAppUser) {
        val expiresAtSec = System.currentTimeMillis() / 1000 + expiresIn
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .putLong(KEY_EXPIRES_AT, expiresAtSec)
            .putString(KEY_USER_JSON, json.encodeToString(user))
            .apply()
        _isLoggedIn.value = true
        _currentUser.value = user
        Log.d(TAG, "Logged in: ${user.nickname}, credits=${user.credits}")
    }

    private suspend fun autoRefreshIfNeeded() {
        if (needsRefresh()) {
            Log.d(TAG, "Token near expiry, auto-refreshing")
            refreshToken()
        }
    }

    private fun post(url: String, body: String, auth: Boolean): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            doOutput = body.isNotEmpty()
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            if (auth) {
                val token = getAccessToken()
                    ?: throw Exception("未登录")
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
        if (body.isNotEmpty()) {
            conn.outputStream.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { w -> w.write(body); w.flush() }
            }
        }
        return readResponse(conn)
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            val token = getAccessToken()
                ?: throw Exception("未登录")
            setRequestProperty("Authorization", "Bearer $token")
        }
        return readResponse(conn)
    }

    private fun readResponse(conn: HttpURLConnection): String {
        try {
            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val errBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code == 401) {
                    logout()
                }
                if (errBody.isNotEmpty()) throw ApiException(code, errBody)
                throw ApiException(code, "HTTP $code")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    class ApiException(val httpCode: Int, message: String) : Exception(message) {
        val isInsufficientCredits get() = httpCode == 402
        val isUnauthorized get() = httpCode == 401
    }
}
