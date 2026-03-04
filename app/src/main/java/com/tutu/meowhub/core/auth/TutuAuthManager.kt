package com.tutu.meowhub.core.auth

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class TutuAuthManager(private val context: Context) {

    companion object {
        private const val TOKEN_API = "https://www.szs.chat/api/app_auth/token.php"
        private const val PREF_NAME = "tutu_auth"
        private const val KEY_TOKEN = "token"
        private const val KEY_EXPIRES = "expires_timestamp"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getCachedToken(): String? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES, 0)
        if (System.currentTimeMillis() / 1000 >= expiresAt) {
            prefs.edit().clear().apply()
            return null
        }
        return token
    }

    suspend fun getToken(appId: String, appSecret: String): TokenResult {
        val cached = getCachedToken()
        if (cached != null) return TokenResult.Success(cached)

        return fetchToken(appId, appSecret)
    }

    private suspend fun fetchToken(appId: String, appSecret: String): TokenResult =
        withContext(Dispatchers.IO) {
            try {
                val deviceId = Settings.Secure.getString(
                    context.contentResolver, Settings.Secure.ANDROID_ID
                ) ?: "unknown"
                val deviceInfo = "${Build.MODEL} / Android ${Build.VERSION.RELEASE}"

                val requestBody = buildString {
                    append("{")
                    append("\"app_id\":\"$appId\",")
                    append("\"app_secret\":\"$appSecret\",")
                    append("\"device_id\":\"$deviceId\",")
                    append("\"device_info\":\"$deviceInfo\"")
                    append("}")
                }

                val url = URL(TOKEN_API)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                }

                conn.outputStream.use { os ->
                    OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                        writer.write(requestBody)
                        writer.flush()
                    }
                }

                val responseCode = conn.responseCode
                val responseBody = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                }
                conn.disconnect()

                val result = json.decodeFromString<JsonObject>(responseBody)

                if (result["success"]?.jsonPrimitive?.boolean == true) {
                    val token = result["token"]!!.jsonPrimitive.content
                    val expiresTs = result["expires_timestamp"]!!.jsonPrimitive.long

                    prefs.edit()
                        .putString(KEY_TOKEN, token)
                        .putLong(KEY_EXPIRES, expiresTs)
                        .apply()

                    TokenResult.Success(token)
                } else {
                    val message = result["message"]?.jsonPrimitive?.content ?: "Unknown error"
                    TokenResult.Failure(message)
                }
            } catch (e: Exception) {
                TokenResult.Failure(e.message ?: "Network error")
            }
        }

    fun clearToken() {
        prefs.edit().clear().apply()
    }

    sealed class TokenResult {
        data class Success(val token: String) : TokenResult()
        data class Failure(val message: String) : TokenResult()
    }
}
