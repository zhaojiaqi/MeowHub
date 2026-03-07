package com.tutu.meowhub.feature.terminal

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termux.terminal.TerminalSession
import com.tutu.meowhub.BuildConfig
import com.tutu.meowhub.MeowApp
import com.tutu.meowhub.core.engine.SocketCommandBridge
import com.tutu.meowhub.core.terminal.MeowHubBridgeServer
import com.tutu.meowhub.core.terminal.MeowTerminalSessionClient
import com.tutu.meowhub.core.terminal.MeowTermuxInstaller
import com.tutu.meowhub.core.terminal.MeowTermuxService
import com.tutu.meowhub.core.terminal.OpenClawGatewayManager
import com.tutu.meowhub.core.terminal.OpenClawInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    enum class BootstrapState {
        CHECKING,
        NOT_INSTALLED,
        INSTALLING,
        INSTALLED,
        ERROR
    }

    private val _bootstrapState = MutableStateFlow(BootstrapState.CHECKING)
    val bootstrapState: StateFlow<BootstrapState> = _bootstrapState.asStateFlow()

    private val _installProgress = MutableStateFlow("")
    val installProgress: StateFlow<String> = _installProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentSession = MutableStateFlow<TerminalSession?>(null)
    val currentSession: StateFlow<TerminalSession?> = _currentSession.asStateFlow()

    private val _sessionCount = MutableStateFlow(0)
    val sessionCount: StateFlow<Int> = _sessionCount.asStateFlow()

    private val _terminalUpdateTrigger = MutableStateFlow(0L)
    val terminalUpdateTrigger: StateFlow<Long> = _terminalUpdateTrigger.asStateFlow()

    private var sessionRestartCount = 0
    private var lastSessionCreateTime = 0L
    private companion object {
        const val TAG = "TerminalViewModel"
        const val MAX_RESTART_ATTEMPTS = 3
        const val RESTART_COOLDOWN_MS = 2000L
    }

    val termuxService = MeowTermuxService(application)
    val sessionClient = MeowTerminalSessionClient(application).apply {
        onSessionUpdate = {
            _terminalUpdateTrigger.value = System.currentTimeMillis()
        }
        onSessionFinishedCallback = { finishedSession ->
            handleSessionFinished(finishedSession)
        }
        onOutputLineCallback = { line ->
            handleTerminalOutputLine(line)
        }
    }

    private val _sessionList = MutableStateFlow<List<Pair<Int, String>>>(emptyList())
    val sessionList: StateFlow<List<Pair<Int, String>>> = _sessionList.asStateFlow()

    private val _currentSessionIndex = MutableStateFlow(0)
    val currentSessionIndex: StateFlow<Int> = _currentSessionIndex.asStateFlow()

    val openClawInstaller = OpenClawInstaller(application)

    private val bridgeServer: MeowHubBridgeServer by lazy {
        val app = getApplication<MeowApp>()
        val bridge = SocketCommandBridge(app.tutuClient, app.deviceCache)
        MeowHubBridgeServer(bridge, app.tutuClient)
    }

    private val _consoleUrl = MutableStateFlow<String?>(null)
    val consoleUrl: StateFlow<String?> = _consoleUrl.asStateFlow()

    val gatewayManager = OpenClawGatewayManager(application).apply {
        onSessionCreated = {
            refreshSessionList()
            switchToGatewaySessionIfAvailable()
        }
        onFirstHealthy = {
            Log.i(TAG, "Gateway first healthy, merging config now")
            writeOpenClawConfigFromBuildConfig()
            printWelcomeBanner()
        }
    }

    private val _isModelConfigured = MutableStateFlow(BuildConfig.DOUBAO_API_KEY.isNotBlank())
    val isModelConfigured: StateFlow<Boolean> = _isModelConfigured.asStateFlow()

    init {
        Log.i(TAG, "TerminalViewModel init")
        termuxService.setSessionClient(sessionClient)
        checkBootstrap()
    }

    private fun checkBootstrap() {
        viewModelScope.launch {
            Log.i(TAG, "checkBootstrap: start")
            _bootstrapState.value = BootstrapState.CHECKING
            val installed = MeowTermuxInstaller.isInstalled()
            Log.i(TAG, "checkBootstrap: isInstalled=$installed")
            if (installed) {
                _bootstrapState.value = BootstrapState.INSTALLED
                createDefaultSession()
                checkAndAutoInstallOpenClaw()
            } else {
                _bootstrapState.value = BootstrapState.NOT_INSTALLED
                Log.i(TAG, "checkBootstrap: waiting for user to install")
            }
        }
    }

    fun installBootstrap() {
        viewModelScope.launch {
            Log.i(TAG, "installBootstrap: start")
            _bootstrapState.value = BootstrapState.INSTALLING
            _errorMessage.value = null

            val result = MeowTermuxInstaller.install(
                getApplication(),
                onProgress = { msg ->
                    Log.d(TAG, "installBootstrap progress: $msg")
                    _installProgress.value = msg
                }
            )

            if (result.isSuccess) {
                Log.i(TAG, "installBootstrap: SUCCESS")
                _bootstrapState.value = BootstrapState.INSTALLED
                createDefaultSession()
                checkAndAutoInstallOpenClaw()
            } else {
                val err = result.exceptionOrNull()
                Log.e(TAG, "installBootstrap: FAILED", err)
                _bootstrapState.value = BootstrapState.ERROR
                _errorMessage.value = err?.message ?: "Unknown error"
            }
        }
    }

    private fun checkAndAutoInstallOpenClaw() {
        viewModelScope.launch {
            // Wait for init.sh to finish and session to stabilize
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
                    Log.i(TAG, "checkAndAutoInstallOpenClaw: Gateway already running (pre-existing)")
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

    private fun handleTerminalOutputLine(line: String) {
        val previousState = openClawInstaller.state.value
        openClawInstaller.onInstallOutputLine(line)
        val newState = openClawInstaller.state.value

        if (previousState != OpenClawInstaller.State.READY && newState == OpenClawInstaller.State.READY) {
            Log.i(TAG, "handleTerminalOutputLine: install complete, starting gateway")
            viewModelScope.launch {
                printToTerminal("\\033[1;33m>>> Starting OpenClaw Gateway...\\033[0m")
                delay(2000)
                autoStartGatewayIfReady()
            }
        }
    }

    fun installOpenClaw() {
        viewModelScope.launch {
            val session = currentSession.value
            if (session == null) {
                Log.w(TAG, "installOpenClaw: no active session, skipping")
                return@launch
            }
            Log.i(TAG, "installOpenClaw: copying bundled assets (debs, openclaw archive, mcp server, workspace)")
            openClawInstaller.copyDebsFromAssets()
            openClawInstaller.copyOpenClawArchiveFromAssets()
            openClawInstaller.copyMcpServerFromAssets()
            openClawInstaller.copyWorkspaceFilesFromAssets()
            Log.i(TAG, "installOpenClaw: running install in session")
            openClawInstaller.runInstallInSession(session)
            pollForInstallCompletion()
        }
    }

    private fun pollForInstallCompletion() {
        viewModelScope.launch {
            val maxWait = 180_000L
            val interval = 5_000L
            val start = System.currentTimeMillis()
            Log.i(TAG, "pollForInstallCompletion: started polling (max ${maxWait/1000}s)")

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
            Log.w(TAG, "pollForInstallCompletion: timed out after ${maxWait/1000}s")
        }
    }

    fun refreshOpenClawStatus() {
        viewModelScope.launch {
            Log.i(TAG, "refreshOpenClawStatus: checking OpenClaw and gateway status")
            withContext(Dispatchers.IO) {
                openClawInstaller.checkStatus()
                gatewayManager.refreshState()
            }
            _isModelConfigured.value = BuildConfig.DOUBAO_API_KEY.isNotBlank()
                || openClawInstaller.isModelConfigured()
        }
    }

    fun startGateway() {
        viewModelScope.launch {
            Log.i(TAG, "startGateway: manual start")
            openClawInstaller.copyMcpServerFromAssets()
            bridgeServer.start()
            gatewayManager.startGateway(termuxService)
        }
    }

    fun stopGateway() {
        Log.i(TAG, "stopGateway: manual stop")
        gatewayManager.stopGateway()
    }

    private fun writeOpenClawConfigFromBuildConfig() {
        val apiKey = BuildConfig.DOUBAO_API_KEY
        if (apiKey.isBlank()) {
            Log.i(TAG, "writeOpenClawConfigFromBuildConfig: no DOUBAO_API_KEY, merging minimal config")
            openClawInstaller.mergeMinimalConfig()
            _isModelConfigured.value = openClawInstaller.isModelConfigured()
            return
        }
        val baseUrl = BuildConfig.DOUBAO_BASE_URL
        val modelId = BuildConfig.DOUBAO_MODEL_ID
        Log.i(TAG, "writeOpenClawConfigFromBuildConfig: merging config with model=$modelId")
        openClawInstaller.mergeOpenClawConfig(
            apiKey = apiKey,
            baseUrl = baseUrl,
            modelId = modelId
        )
        _isModelConfigured.value = true
    }

    private fun autoStartGatewayIfReady() {
        viewModelScope.launch {
            val installed = openClawInstaller.isOpenClawInstalled()
            Log.i(TAG, "autoStartGatewayIfReady: openClawInstalled=$installed")
            if (installed) {
                openClawInstaller.copyMcpServerFromAssets()
                openClawInstaller.copyWorkspaceFilesFromAssets()
                bridgeServer.start()
                Log.i(TAG, "autoStartGatewayIfReady: bridge server started")
                val healthy = withContext(Dispatchers.IO) { gatewayManager.checkHealth() }
                Log.i(TAG, "autoStartGatewayIfReady: gatewayHealthy=$healthy")
                if (!healthy) {
                    Log.i(TAG, "autoStartGatewayIfReady: starting gateway")
                    gatewayManager.startGateway(termuxService)
                }
            }
        }
    }

    private fun switchToGatewaySessionIfAvailable() {
        val gwSession = gatewayManager.gatewaySession ?: return
        val idx = termuxService.findSessionIndex(gwSession)
        if (idx >= 0) {
            Log.i(TAG, "switchToGatewaySessionIfAvailable: switching to gateway session at index $idx")
            switchSession(idx)
        }
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

    private fun printToTerminal(message: String) {
        try {
            val session = _currentSession.value ?: return
            session.write("printf '\\n$message\\n'\n")
        } catch (e: Exception) {
            Log.w(TAG, "printToTerminal: failed: ${e.message}")
        }
    }

    private fun updateConsoleUrl() {
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch {
            Log.i(TAG, "handleSessionFinished: session finished")
            termuxService.removeSession(finishedSession)
            _sessionCount.value = termuxService.sessionCount
            refreshSessionList()

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

            Log.i(TAG, "handleSessionFinished: restartCount=$sessionRestartCount, " +
                "timeSinceLastCreate=${now - lastSessionCreateTime}ms")

            if (sessionRestartCount >= MAX_RESTART_ATTEMPTS) {
                Log.e(TAG, "Session keeps crashing ($sessionRestartCount times). " +
                    "Falling back to /system/bin/sh.")
                _errorMessage.value = "Shell keeps crashing. Using fallback shell.\n" +
                    "Bootstrap binaries may be incompatible."
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

    override fun onCleared() {
        Log.i(TAG, "onCleared: cleaning up")
        super.onCleared()
        bridgeServer.stop()
        gatewayManager.cleanup()
        termuxService.cleanup()
    }
}
