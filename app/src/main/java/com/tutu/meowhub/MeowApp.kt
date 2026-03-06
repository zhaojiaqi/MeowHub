package com.tutu.meowhub

import android.app.Application
import android.os.Build
import android.util.Log
import com.tutu.meowhub.BuildConfig
import com.tutu.meowhub.core.auth.MeowAppAuthManager
import com.tutu.meowhub.core.auth.TutuAuthManager
import com.tutu.meowhub.core.database.LocalSkillRepository
import com.tutu.meowhub.core.database.MeowHubDatabase
import com.tutu.meowhub.core.engine.*
import com.tutu.meowhub.core.network.MeowHubApiClient
import com.tutu.meowhub.core.socket.TutuSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

class MeowApp : Application() {

    val tutuClient: TutuSocketClient by lazy { TutuSocketClient() }
    val authManager: TutuAuthManager by lazy { TutuAuthManager(this) }
    val meowAppAuth: MeowAppAuthManager by lazy { MeowAppAuthManager(this) }
    val deviceCache: DeviceInfoCache by lazy {
        DeviceInfoCache(tutuClient).also { it.observeConnection() }
    }
    val apiClient: MeowHubApiClient by lazy { MeowHubApiClient() }

    val database: MeowHubDatabase by lazy { MeowHubDatabase.getInstance(this) }
    val skillRepository: LocalSkillRepository by lazy { LocalSkillRepository(database.skillDao()) }

    fun resolveAiProvider(): AiProvider? {
        if (meowAppAuth.isLoggedIn.value) {
            return MeowAppAiProvider(meowAppAuth)
        }
        if (BuildConfig.DOUBAO_API_KEY.isNotBlank()) {
            return DoubaoAiProvider()
        }
        return null
    }

    val hasAiCapability: Boolean
        get() = meowAppAuth.isLoggedIn.value || BuildConfig.DOUBAO_API_KEY.isNotBlank()

    private val _loginRequiredEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val loginRequiredEvent: SharedFlow<String> = _loginRequiredEvent.asSharedFlow()

    fun requestLogin(reason: String = "需要登录后才能使用 AI 能力") {
        _loginRequiredEvent.tryEmit(reason)
    }

    val skillEngine: SkillEngine by lazy {
        val bridge = SocketCommandBridge(tutuClient, deviceCache)
        SkillEngine(
            bridge = bridge,
            apiClient = apiClient,
            aiProviderFactory = { resolveAiProvider() },
            promptHandler = null
        )
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 28) {
            HiddenApiBypass.setHiddenApiExemptions("")
        }
        instance = this
        deviceCache
        connectWithAuth()
    }

    fun connectWithAuth(force: Boolean = false) {
        Log.d(TAG, "connectWithAuth(force=$force)")
        appScope.launch {
            if (meowAppAuth.isLoggedIn.value) {
                val keyData = meowAppAuth.appKeyData.value
                if (keyData != null && keyData.appId.isNotBlank()) {
                    Log.d(TAG, "Using MeowApp login key: ${keyData.appId}")
                    val result = authManager.getToken(keyData.appId, keyData.appSecret)
                    when (result) {
                        is TutuAuthManager.TokenResult.Success -> {
                            tutuClient.connect(result.token, force = force)
                            return@launch
                        }
                        is TutuAuthManager.TokenResult.Failure -> {
                            Log.w(TAG, "MeowApp key auth failed: ${result.message}, trying activate")
                        }
                    }
                }
                try {
                    val keyResult = meowAppAuth.activateAppKey()
                    val activated = keyResult.getOrNull()
                    if (activated?.success == true && activated.data != null) {
                        Log.d(TAG, "Activated app key: ${activated.data.appId}")
                        val result = authManager.getToken(activated.data.appId, activated.data.appSecret)
                        if (result is TutuAuthManager.TokenResult.Success) {
                            tutuClient.connect(result.token, force = force)
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "App key activation failed: ${e.message}")
                }
            }

            if (APP_ID.isNotBlank() && APP_SECRET.isNotBlank()) {
                val result = authManager.getToken(APP_ID, APP_SECRET)
                Log.d(TAG, "Token result (secrets): ${result::class.simpleName}")
                when (result) {
                    is TutuAuthManager.TokenResult.Success -> {
                        tutuClient.connect(result.token, force = force)
                    }
                    is TutuAuthManager.TokenResult.Failure -> {
                        Log.w(TAG, "Token fetch failed: ${result.message}")
                        tutuClient.log("Token fetch failed: ${result.message}")
                        tutuClient.connect(authManager.getCachedToken(), force = force)
                    }
                }
            } else {
                Log.d(TAG, "No secrets configured and not logged in, skipping connect")
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        tutuClient.destroy()
    }

    companion object {
        private const val TAG = "MeowApp"
        lateinit var instance: MeowApp
            private set

        private val APP_ID = BuildConfig.TUTU_APP_ID
        private val APP_SECRET = BuildConfig.TUTU_APP_SECRET
    }
}
