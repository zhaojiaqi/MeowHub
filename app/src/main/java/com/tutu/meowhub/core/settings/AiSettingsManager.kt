package com.tutu.meowhub.core.settings

import android.content.Context
import android.content.SharedPreferences

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

    companion object {
        private const val KEY_USE_OWN_API_KEY = "use_own_api_key"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL_ID = "model_id"
        private const val KEY_BASE_URL = "base_url"
        private const val DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"

        val AVAILABLE_MODELS = listOf(
            "doubao-seed-2-0-lite-260215"
        )
    }
}
