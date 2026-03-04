package com.tutu.miaohub

import android.app.Application
import android.os.Build
import android.util.Log
import com.tutu.miaohub.BuildConfig
import com.tutu.miaohub.core.auth.TutuAuthManager
import com.tutu.miaohub.core.database.LocalSkillRepository
import com.tutu.miaohub.core.database.MeowHubDatabase
import com.tutu.miaohub.core.engine.*
import com.tutu.miaohub.core.network.MiaoHubApiClient
import com.tutu.miaohub.core.socket.TutuSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

class MiaoApp : Application() {

    val tutuClient: TutuSocketClient by lazy { TutuSocketClient() }
    val authManager: TutuAuthManager by lazy { TutuAuthManager(this) }
    val deviceCache: DeviceInfoCache by lazy {
        DeviceInfoCache(tutuClient).also { it.observeConnection() }
    }
    val apiClient: MiaoHubApiClient by lazy { MiaoHubApiClient() }

    val database: MeowHubDatabase by lazy { MeowHubDatabase.getInstance(this) }
    val skillRepository: LocalSkillRepository by lazy { LocalSkillRepository(database.skillDao()) }

    val skillEngine: SkillEngine by lazy {
        val bridge = SocketCommandBridge(tutuClient, deviceCache)
        val aiProvider = DoubaoAiProvider()
        SkillEngine(
            bridge = bridge,
            apiClient = apiClient,
            aiProvider = aiProvider,
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
            val result = authManager.getToken(APP_ID, APP_SECRET)
            Log.d(TAG, "Token result: ${result::class.simpleName}")
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
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        tutuClient.destroy()
    }

    companion object {
        private const val TAG = "MiaoApp"
        lateinit var instance: MiaoApp
            private set

        private val APP_ID = BuildConfig.TUTU_APP_ID
        private val APP_SECRET = BuildConfig.TUTU_APP_SECRET
    }
}
