package com.tutu.meowhub.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.termux.terminal.TerminalSession
import com.tutu.meowhub.BuildConfig
import com.tutu.meowhub.MainActivity
import com.tutu.meowhub.MeowApp
import com.tutu.meowhub.R
import com.tutu.meowhub.core.engine.SocketCommandBridge
import com.tutu.meowhub.core.terminal.MeowHubBridgeServer
import com.tutu.meowhub.core.terminal.MeowTerminalSessionClient
import com.tutu.meowhub.core.terminal.MeowTermuxService
import com.tutu.meowhub.core.terminal.OpenClawGatewayManager
import com.tutu.meowhub.core.terminal.OpenClawInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerminalForegroundService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var termuxService: MeowTermuxService
        private set
    lateinit var openClawInstaller: OpenClawInstaller
        private set
    lateinit var gatewayManager: OpenClawGatewayManager
        private set

    private lateinit var bridgeServer: MeowHubBridgeServer
    private lateinit var sessionClient: MeowTerminalSessionClient

    private val _currentSession = MutableStateFlow<TerminalSession?>(null)
    val currentSession: StateFlow<TerminalSession?> = _currentSession.asStateFlow()

    private val _sessionCount = MutableStateFlow(0)
    val sessionCount: StateFlow<Int> = _sessionCount.asStateFlow()

    private val _terminalUpdateTrigger = MutableStateFlow(0L)
    val terminalUpdateTrigger: StateFlow<Long> = _terminalUpdateTrigger.asStateFlow()

    private val _sessionList = MutableStateFlow<List<Pair<Int, String>>>(emptyList())
    val sessionList: StateFlow<List<Pair<Int, String>>> = _sessionList.asStateFlow()

    private val _currentSessionIndex = MutableStateFlow(0)
    val currentSessionIndex: StateFlow<Int> = _currentSessionIndex.asStateFlow()

    private val _consoleUrl = MutableStateFlow<String?>(null)
    val consoleUrl: StateFlow<String?> = _consoleUrl.asStateFlow()

    private val _isModelConfigured = MutableStateFlow(false)
    val isModelConfigured: StateFlow<Boolean> = _isModelConfigured.asStateFlow()

    private var sessionRestartCount = 0
    private var lastSessionCreateTime = 0L

    var onSessionUpdate: (() -> Unit)? = null
    var onSessionFinished: ((TerminalSession) -> Unit)? = null
    var onOutputLine: ((String) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): TerminalForegroundService = this@TerminalForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        initializeComponents()
    }

    private fun initializeComponents() {
        val app = applicationContext as MeowApp

        sessionClient = MeowTerminalSessionClient(this).apply {
            onSessionUpdate = {
                _terminalUpdateTrigger.value = System.currentTimeMillis()
                this@TerminalForegroundService.onSessionUpdate?.invoke()
            }
            onSessionFinishedCallback = { finishedSession ->
                handleSessionFinished(finishedSession)
            }
            onOutputLineCallback = { line ->
                handleTerminalOutputLine(line)
            }
        }

        termuxService = MeowTermuxService(this).apply {
            setSessionClient(sessionClient)
        }

        openClawInstaller = OpenClawInstaller(this)

        val bridge = SocketCommandBridge(app.tutuClient, app.deviceCache)
        bridgeServer = MeowHubBridgeServer(bridge, app.tutuClient)

        gatewayManager = OpenClawGatewayManager(this).apply {
            onSessionCreated = {
                refreshSessionList()
                switchToGatewaySessionIfAvailable()
            }
            onFirstHealthy = {
                Log.i(TAG, "Gateway first healthy")
                printWelcomeBanner()
            }
        }

        updateModelConfiguredState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "onBind")
        return binder
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: cleaning up")
        bridgeServer.stop()
        gatewayManager.cleanup()
        termuxService.cleanup()
        super.onDestroy()
    }

    fun createDefaultSession() {
        lastSessionCreateTime = System.currentTimeMillis()
        Log.i(TAG, "createDefaultSession")
        val session = termuxService.createSession()
        _currentSession.value = session
        _sessionCount.value = termuxService.sessionCount
        refreshSessionList()
    }

    fun createNewSession() {
        Log.i(TAG, "createNewSession")
        val session = termuxService.createSession()
        _currentSession.value = session
        _sessionCount.value = termuxService.sessionCount
        refreshSessionList()
    }

    fun switchSession(index: Int) {
        Log.d(TAG, "switchSession: index=$index")
        termuxService.switchToSession(index)
        _currentSession.value = termuxService.currentSession
        _currentSessionIndex.value = index
    }

    private fun refreshSessionList() {
        _sessionList.value = termuxService.getSessionLabels(gatewayManager.gatewaySession)
        _currentSessionIndex.value = termuxService.getCurrentSessionIndex()
    }

    fun executeCommand(command: String) {
        Log.d(TAG, "executeCommand: $command")
        termuxService.executeCommand(command)
    }

    fun startGateway() {
        serviceScope.launch {
            Log.i(TAG, "startGateway: manual start")
            openClawInstaller.copyMcpServerFromAssets()
            writeOpenClawConfigFromBuildConfig()
            bridgeServer.start()
            gatewayManager.startGateway(termuxService)
        }
    }

    fun stopGateway() {
        Log.i(TAG, "stopGateway: manual stop")
        gatewayManager.stopGateway()
    }

    fun installOpenClaw() {
        serviceScope.launch {
            val session = currentSession.value
            if (session == null) {
                Log.w(TAG, "installOpenClaw: no active session, skipping")
                return@launch
            }
            Log.i(TAG, "installOpenClaw: copying bundled assets")
            openClawInstaller.copyDebsFromAssets()
            openClawInstaller.copyOpenClawArchiveFromAssets()
            openClawInstaller.copyMcpServerFromAssets()
            openClawInstaller.copyWorkspaceFilesFromAssets()
            Log.i(TAG, "installOpenClaw: running install in session")
            openClawInstaller.runInstallInSession(session)
            pollForInstallCompletion()
        }
    }

    fun refreshOpenClawStatus() {
        serviceScope.launch {
            Log.i(TAG, "refreshOpenClawStatus: checking status")
            withContext(Dispatchers.IO) {
                openClawInstaller.checkStatus()
                gatewayManager.refreshState()
            }
            updateModelConfiguredState()
        }
    }

    fun checkAndAutoInstallOpenClaw() {
        serviceScope.launch {
            Log.i(TAG, "checkAndAutoInstallOpenClaw: waiting for session to stabilize...")
            delay(3000)

            Log.i(TAG, "checkAndAutoInstallOpenClaw: checking OpenClaw status")
            openClawInstaller.checkStatus()
            val clawState = openClawInstaller.state.value
            Log.i(TAG, "checkAndAutoInstallOpenClaw: state=$clawState")

            when (clawState) {
                OpenClawInstaller.State.NOT_INSTALLED -> {
                    Log.i(TAG, "checkAndAutoInstallOpenClaw: OpenClaw not installed, starting auto-install")
                    installOpenClaw()
                }
                OpenClawInstaller.State.READY -> {
                    Log.i(TAG, "checkAndAutoInstallOpenClaw: OpenClaw ready, starting gateway")
                    autoStartGatewayIfReady()
                }
                OpenClawInstaller.State.RUNNING -> {
                    Log.i(TAG, "checkAndAutoInstallOpenClaw: Gateway already running")
                    gatewayManager.syncRunningState()
                    openClawInstaller.copyWorkspaceFilesFromAssets()
                    bridgeServer.start()
                    writeOpenClawConfigFromBuildConfig()
                    updateConsoleUrl()
                }
                else -> {
                    Log.i(TAG, "checkAndAutoInstallOpenClaw: state=$clawState, no action")
                }
            }
        }
    }

    private fun updateModelConfiguredState() {
        val app = applicationContext as MeowApp
        _isModelConfigured.value = BuildConfig.DOUBAO_API_KEY.isNotBlank()
                || app.aiSettings.isConfigured
                || app.meowAppAuth.getAccessToken() != null
                || openClawInstaller.isModelConfigured()
    }

    private fun handleTerminalOutputLine(line: String) {
        val previousState = openClawInstaller.state.value
        openClawInstaller.onInstallOutputLine(line)
        val newState = openClawInstaller.state.value

        onOutputLine?.invoke(line)

        if (previousState != OpenClawInstaller.State.READY && newState == OpenClawInstaller.State.READY) {
            Log.i(TAG, "handleTerminalOutputLine: install complete, starting gateway")
            serviceScope.launch {
                printToTerminal("\\033[1;33m>>> Starting OpenClaw Gateway...\\033[0m")
                delay(2000)
                autoStartGatewayIfReady()
            }
        }
    }

    private fun pollForInstallCompletion() {
        serviceScope.launch {
            val maxWait = 180_000L
            val interval = 5_000L
            val start = System.currentTimeMillis()
            Log.i(TAG, "pollForInstallCompletion: started polling (max ${maxWait / 1000}s)")

            while (System.currentTimeMillis() - start < maxWait) {
                delay(interval)
                if (openClawInstaller.state.value == OpenClawInstaller.State.READY) {
                    Log.i(TAG, "pollForInstallCompletion: install already detected via terminal output")
                    return@launch
                }
                val installed = withContext(Dispatchers.IO) { openClawInstaller.isOpenClawInstalled() }
                if (installed) {
                    Log.i(TAG, "pollForInstallCompletion: openclaw files detected, triggering gateway start")
                    openClawInstaller.markReady()
                    printToTerminal("\\033[1;33m>>> Starting OpenClaw Gateway...\\033[0m")
                    delay(2000)
                    autoStartGatewayIfReady()
                    return@launch
                }
            }
            Log.w(TAG, "pollForInstallCompletion: timed out after ${maxWait / 1000}s")
        }
    }

    private fun autoStartGatewayIfReady() {
        serviceScope.launch {
            val installed = openClawInstaller.isOpenClawInstalled()
            Log.i(TAG, "autoStartGatewayIfReady: openClawInstalled=$installed")
            if (installed) {
                openClawInstaller.copyMcpServerFromAssets()
                openClawInstaller.copyWorkspaceFilesFromAssets()
                writeOpenClawConfigFromBuildConfig()
                bridgeServer.start()
                Log.i(TAG, "autoStartGatewayIfReady: bridge server started, config written")
                val healthy = withContext(Dispatchers.IO) { gatewayManager.checkHealth() }
                Log.i(TAG, "autoStartGatewayIfReady: gatewayHealthy=$healthy")
                if (!healthy) {
                    Log.i(TAG, "autoStartGatewayIfReady: starting gateway")
                    gatewayManager.startGateway(termuxService)
                }
            }
        }
    }

    private fun writeOpenClawConfigFromBuildConfig() {
        val app = applicationContext as MeowApp
        val aiSettings = app.aiSettings

        if (aiSettings.isConfigured) {
            Log.i(TAG, "writeOpenClawConfigFromBuildConfig: using user AI settings")
            openClawInstaller.mergeOpenClawConfig(
                apiKey = aiSettings.apiKey,
                baseUrl = aiSettings.baseUrl,
                modelId = aiSettings.modelId
            )
            _isModelConfigured.value = true
            return
        }

        val accessToken = app.meowAppAuth.getAccessToken()
        if (accessToken != null) {
            Log.i(TAG, "writeOpenClawConfigFromBuildConfig: using TutuAI")
            openClawInstaller.mergeTutuAiConfig(accessToken)
            _isModelConfigured.value = true
            return
        }

        val apiKey = BuildConfig.DOUBAO_API_KEY
        if (apiKey.isNotBlank()) {
            val baseUrl = BuildConfig.DOUBAO_BASE_URL
            val modelId = BuildConfig.DOUBAO_MODEL_ID
            Log.i(TAG, "writeOpenClawConfigFromBuildConfig: using build config")
            openClawInstaller.mergeOpenClawConfig(
                apiKey = apiKey,
                baseUrl = baseUrl,
                modelId = modelId
            )
            _isModelConfigured.value = true
            return
        }

        Log.i(TAG, "writeOpenClawConfigFromBuildConfig: no API config available")
        openClawInstaller.mergeMinimalConfig()
        _isModelConfigured.value = openClawInstaller.isModelConfigured()
    }

    private fun switchToGatewaySessionIfAvailable() {
        val gwSession = gatewayManager.gatewaySession ?: return
        val idx = termuxService.findSessionIndex(gwSession)
        if (idx >= 0) {
            Log.i(TAG, "switchToGatewaySessionIfAvailable: switching to gateway session at index $idx")
            switchSession(idx)
        }
    }

    private fun printToTerminal(message: String) {
        try {
            val session = _currentSession.value ?: return
            session.write("printf '\\n$message\\n'\n")
        } catch (e: Exception) {
            Log.w(TAG, "printToTerminal: failed: ${e.message}")
        }
    }

    private fun updateConsoleUrl() {
        serviceScope.launch(Dispatchers.IO) {
            var token = openClawInstaller.getGatewayToken()
            if (token == null) {
                delay(3000)
                token = openClawInstaller.getGatewayToken()
            }
            val baseUrl = "http://127.0.0.1:18789/openclaw"
            val url = if (token != null) "$baseUrl/?token=$token" else baseUrl
            _consoleUrl.value = url
            Log.i(TAG, "updateConsoleUrl: $url (token=${token != null})")
        }
    }

    private fun printWelcomeBanner() {
        updateConsoleUrl()
        val baseUrl = "http://127.0.0.1:18789/openclaw"

        val gwSession = gatewayManager.gatewaySession
        val targetSession = gwSession ?: _currentSession.value ?: return
        try {
            targetSession.write(buildString {
                append("printf '\\n")
                append("\\033[1;33m")
                append("  ======================================\\n")
                append("    🐱 MeowHub OpenClaw Gateway\\n")
                append("  ======================================\\n")
                append("\\033[0m\\n")
                append("\\033[1;32m  ✓ Gateway 服务已启动\\033[0m\\n")
                append("\\033[0;36m  ● 端口: 18789\\033[0m\\n")
                append("\\033[0;36m  ● 控制台: $baseUrl\\033[0m\\n")
                if (!_isModelConfigured.value) {
                    append("\\033[1;33m  ⚠ AI 模型未配置，请在控制台中配置\\033[0m\\n")
                } else {
                    append("\\033[1;32m  ✓ AI 模型已配置\\033[0m\\n")
                }
                append("\\n")
                append("\\033[0;90m  提示: 在终端页面顶部切换「控制台」标签可直接访问\\033[0m\\n")
                append("'\n")
            })
        } catch (e: Exception) {
            Log.w(TAG, "printWelcomeBanner: failed: ${e.message}")
        }
    }

    private fun handleSessionFinished(finishedSession: TerminalSession) {
        serviceScope.launch {
            Log.i(TAG, "handleSessionFinished: session finished")
            termuxService.removeSession(finishedSession)
            _sessionCount.value = termuxService.sessionCount
            refreshSessionList()

            onSessionFinished?.invoke(finishedSession)

            if (termuxService.sessionCount > 0) {
                _currentSession.value = termuxService.currentSession
                Log.i(TAG, "handleSessionFinished: switched to existing session")
                return@launch
            }

            val now = System.currentTimeMillis()
            if (now - lastSessionCreateTime < RESTART_COOLDOWN_MS) {
                sessionRestartCount++
            } else {
                sessionRestartCount = 0
            }

            Log.i(TAG, "handleSessionFinished: restartCount=$sessionRestartCount")

            if (sessionRestartCount >= MAX_RESTART_ATTEMPTS) {
                Log.e(TAG, "Session keeps crashing. Falling back to /system/bin/sh.")
                val fallbackSession = termuxService.createSession(
                    executable = "/system/bin/sh"
                )
                _currentSession.value = fallbackSession
                _sessionCount.value = termuxService.sessionCount
                sessionRestartCount = 0
            } else {
                createDefaultSession()
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.terminal_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.terminal_notification_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TerminalForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeowHub Terminal")
            .setContentText(getString(R.string.terminal_notification_content))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.terminal_notification_stop), stopIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "TerminalService"
        const val CHANNEL_ID = "meow_terminal"
        const val NOTIFICATION_ID = 1002
        const val ACTION_STOP = "com.tutu.meowhub.STOP_TERMINAL"

        private const val MAX_RESTART_ATTEMPTS = 3
        private const val RESTART_COOLDOWN_MS = 2000L

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, TerminalForegroundService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TerminalForegroundService::class.java))
        }
    }
}
