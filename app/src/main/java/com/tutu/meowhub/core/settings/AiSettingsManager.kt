package com.tutu.meowhub.core.settings

import android.content.Context
import android.content.SharedPreferences
import com.tutu.meowhub.BuildConfig

class AiSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    var useOwnApiKey: Boolean
        get() = prefs.getBoolean(KEY_USE_OWN_API_KEY, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_OWN_API_KEY, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var modelId: String
        get() = prefs.getString(KEY_MODEL_ID, AVAILABLE_MODELS[0]) ?: AVAILABLE_MODELS[0]
        set(value) = prefs.edit().putString(KEY_MODEL_ID, value).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    val isConfigured: Boolean
        get() = useOwnApiKey && apiKey.isNotBlank()

    var tutuAppId: String
        get() = prefs.getString(KEY_TUTU_APP_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TUTU_APP_ID, value.trim()).apply()

    var tutuAppSecret: String
        get() = prefs.getString(KEY_TUTU_APP_SECRET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TUTU_APP_SECRET, value.trim()).apply()

    val isTutuConfigured: Boolean
        get() = tutuAppId.isNotBlank() && tutuAppSecret.isNotBlank()

    // 语音 API 配置
    var speechAppId: String
        get() = prefs.getString(KEY_SPEECH_APP_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SPEECH_APP_ID, value.trim()).apply()

    var speechAccessKey: String
        get() = prefs.getString(KEY_SPEECH_ACCESS_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SPEECH_ACCESS_KEY, value.trim()).apply()

    val isSpeechConfigured: Boolean
        get() = speechAppId.isNotBlank() && speechAccessKey.isNotBlank()

    /** 获取生效的语音 App ID（优先用户配置，fallback BuildConfig） */
    val effectiveSpeechAppId: String
        get() = speechAppId.ifBlank { BuildConfig.DOUBAO_SPEECH_APP_ID }

    /** 获取生效的语音 Access Key（优先用户配置，fallback BuildConfig） */
    val effectiveSpeechAccessKey: String
        get() = speechAccessKey.ifBlank { BuildConfig.DOUBAO_SPEECH_ACCESS_KEY }

    // 语音唤醒配置
    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, value).apply()

    var picovoiceAccessKey: String
        get() = prefs.getString(KEY_PICOVOICE_ACCESS_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PICOVOICE_ACCESS_KEY, value.trim()).apply()

    /** 优先用户配置，fallback BuildConfig */
    val effectivePicovoiceAccessKey: String
        get() = picovoiceAccessKey.ifBlank { BuildConfig.PICOVOICE_ACCESS_KEY }

    companion object {
        private const val KEY_USE_OWN_API_KEY = "use_own_api_key"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL_ID = "model_id"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TUTU_APP_ID = "tutu_app_id"
        private const val KEY_TUTU_APP_SECRET = "tutu_app_secret"
        private const val KEY_SPEECH_APP_ID = "speech_app_id"
        private const val KEY_SPEECH_ACCESS_KEY = "speech_access_key"
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_PICOVOICE_ACCESS_KEY = "picovoice_access_key"
        private const val DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"

        val AVAILABLE_MODELS = listOf(
            "doubao-seed-2-0-lite-260215"
        )
    }
}
