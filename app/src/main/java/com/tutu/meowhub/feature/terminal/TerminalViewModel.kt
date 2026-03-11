package com.tutu.meowhub.feature.terminal

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termux.terminal.TerminalSession
import com.tutu.meowhub.core.service.TerminalForegroundService
import com.tutu.meowhub.core.terminal.MeowTerminalSessionClient
import com.tutu.meowhub.core.terminal.MeowTermuxInstaller
import com.tutu.meowhub.core.terminal.OpenClawGatewayManager
import com.tutu.meowhub.core.terminal.OpenClawInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private val _sessionList = MutableStateFlow<List<Pair<Int, String>>>(emptyList())
    val sessionList: StateFlow<List<Pair<Int, String>>> = _sessionList.asStateFlow()

    private val _currentSessionIndex = MutableStateFlow(0)
    val currentSessionIndex: StateFlow<Int> = _currentSessionIndex.asStateFlow()

    private val _consoleUrl = MutableStateFlow<String?>(null)
    val consoleUrl: StateFlow<String?> = _consoleUrl.asStateFlow()

    private val _isModelConfigured = MutableStateFlow(false)
    val isModelConfigured: StateFlow<Boolean> = _isModelConfigured.asStateFlow()

    private var service: TerminalForegroundService? = null
    private var bound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "onServiceConnected")
            val localBinder = binder as TerminalForegroundService.LocalBinder
            service = localBinder.getService()
            bound = true
            observeServiceState()
            onServiceReady()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "onServiceDisconnected")
            service = null
            bound = false
        }
    }

    val openClawInstaller: OpenClawInstaller?
        get() = service?.openClawInstaller

    val gatewayManager: OpenClawGatewayManager?
        get() = service?.gatewayManager

    companion object {
        private const val TAG = "TerminalViewModel"
    }

    init {
        Log.i(TAG, "TerminalViewModel init")
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
                startAndBindService()
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
                startAndBindService()
            } else {
                val err = result.exceptionOrNull()
                Log.e(TAG, "installBootstrap: FAILED", err)
                _bootstrapState.value = BootstrapState.ERROR
                _errorMessage.value = err?.message ?: "Unknown error"
            }
        }
    }

    private fun startAndBindService() {
        val context = getApplication<Application>()
        Log.i(TAG, "startAndBindService")

        // Start the foreground service
        TerminalForegroundService.start(context)

        // Bind to it
        val intent = Intent(context, TerminalForegroundService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun observeServiceState() {
        val svc = service ?: return

        viewModelScope.launch {
            svc.currentSession.collect { _currentSession.value = it }
        }
        viewModelScope.launch {
            svc.sessionCount.collect { _sessionCount.value = it }
        }
        viewModelScope.launch {
            svc.terminalUpdateTrigger.collect { _terminalUpdateTrigger.value = it }
        }
        viewModelScope.launch {
            svc.sessionList.collect { _sessionList.value = it }
        }
        viewModelScope.launch {
            svc.currentSessionIndex.collect { _currentSessionIndex.value = it }
        }
        viewModelScope.launch {
            svc.consoleUrl.collect { _consoleUrl.value = it }
        }
        viewModelScope.launch {
            svc.isModelConfigured.collect { _isModelConfigured.value = it }
        }
    }

    private fun onServiceReady() {
        val svc = service ?: return
        Log.i(TAG, "onServiceReady: creating default session and checking OpenClaw")
        svc.createDefaultSession()
        svc.checkAndAutoInstallOpenClaw()
    }

    fun createNewSession() {
        service?.createNewSession()
    }

    fun switchSession(index: Int) {
        service?.switchSession(index)
    }

    fun executeCommand(command: String) {
        service?.executeCommand(command)
    }

    fun startGateway() {
        service?.startGateway()
    }

    fun stopGateway() {
        service?.stopGateway()
    }

    fun installOpenClaw() {
        service?.installOpenClaw()
    }

    fun refreshOpenClawStatus() {
        service?.refreshOpenClawStatus()
    }

    override fun onCleared() {
        Log.i(TAG, "onCleared")
        super.onCleared()
        if (bound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.w(TAG, "unbindService failed: ${e.message}")
            }
            bound = false
        }
        // Note: We don't stop the service here - it will be stopped when the app exits
    }
}
