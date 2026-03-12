package com.tutu.meowhub.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tutu.meowhub.R
import com.tutu.meowhub.core.service.MeowOverlayService
import com.tutu.meowhub.core.terminal.OpenClawGatewayManager
import com.tutu.meowhub.core.terminal.OpenClawInstaller
import com.tutu.meowhub.feature.terminal.TerminalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    adbViewModel: AdbViewModel = viewModel(),
    onRequestOverlayPermission: () -> Unit,
    onNavigateDebug: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val adbState by adbViewModel.uiState.collectAsState()

    var showPairDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }

    var notificationDenied by remember { mutableStateOf(false) }

    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else true

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notificationDenied = false
            showPairDialog = true
        } else {
            notificationDenied = true
        }
    }

    val onPairClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showPairDialog = true
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    if (showPairDialog) {
        AdbPairDialog(
            onDismiss = { showPairDialog = false },
            onStartPairing = { adbViewModel.startPairing() }
        )
    }

    if (showLogDialog) {
        AdbLogSheet(
            logs = adbState.logs,
            onDismiss = { showLogDialog = false },
            onClear = { adbViewModel.clearLogs() }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.settings_section_services),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ServerControlCard(
                state = adbState,
                notificationDenied = notificationDenied || (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU),
                onPairDevice = onPairClick,
                onStartServer = { adbViewModel.startServer() },
                onStopServer = { adbViewModel.stopServer() },
                onRestartServer = { adbViewModel.restartServer() },
                onShowLogs = { showLogDialog = true },
                onRetry = { adbViewModel.resetState() }
            )

            OverlayControlCard(
                hasOverlayPermission = hasOverlayPermission,
                onRequestPermission = onRequestOverlayPermission,
                onStartOverlay = { MeowOverlayService.start(context) },
                onStopOverlay = { MeowOverlayService.stop(context) }
            )

            OpenClawSettingsCard()

            DebugEntryCard(onNavigateDebug = onNavigateDebug)
        }
    }
}

@Composable
private fun ServerControlCard(
    state: AdbViewModel.UiState,
    notificationDenied: Boolean,
    onPairDevice: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onRestartServer: () -> Unit,
    onShowLogs: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.PhonelinkSetup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.adb_server_control), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (state.logs.isNotEmpty()) {
                    TextButton(onClick = onShowLogs) {
                        Text(stringResource(R.string.adb_view_logs))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            ServerStatusIndicator(state)

            Spacer(Modifier.height(12.dp))

            if (state.serverState == ServerState.ERROR) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            state.errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (notificationDenied && !state.hasPairedKey) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.adb_notification_required),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (state.serverState == ServerState.PUSHING) {
                LinearProgressIndicator(
                    progress = { state.pushProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
                Spacer(Modifier.height(4.dp))
            }

            if (state.serverState in listOf(
                    ServerState.CHECKING, ServerState.CONNECTING,
                    ServerState.STARTING, ServerState.WAITING,
                    ServerState.PAIRING_SEARCHING, ServerState.PAIRING_WORKING
                )
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                Spacer(Modifier.height(4.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isWorking = state.serverState !in listOf(
                    ServerState.IDLE, ServerState.RUNNING, ServerState.ERROR
                )

                when {
                    state.serverState == ServerState.RUNNING -> {
                        FilledTonalButton(
                            onClick = onRestartServer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.adb_btn_restart))
                        }
                        OutlinedButton(onClick = onStopServer) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.adb_btn_stop))
                        }
                    }
                    state.serverState == ServerState.ERROR -> {
                        FilledTonalButton(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.adb_btn_retry))
                        }
                        OutlinedButton(
                            onClick = onPairDevice
                        ) {
                            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                    !state.hasPairedKey -> {
                        FilledTonalButton(
                            onClick = onPairDevice,
                            enabled = !isWorking,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.adb_btn_pair))
                        }
                    }
                    else -> {
                        FilledTonalButton(
                            onClick = onStartServer,
                            enabled = !isWorking,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.adb_btn_start))
                        }
                        OutlinedButton(
                            onClick = onPairDevice,
                            enabled = !isWorking
                        ) {
                            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

private typealias ServerState = AdbViewModel.ServerState

@Composable
private fun ServerStatusIndicator(state: AdbViewModel.UiState) {
    val (statusText, statusColor) = when (state.serverState) {
        ServerState.IDLE -> stringResource(R.string.adb_status_idle) to MaterialTheme.colorScheme.onSurfaceVariant
        ServerState.CHECKING -> stringResource(R.string.adb_status_checking) to MaterialTheme.colorScheme.primary
        ServerState.PAIRING_SEARCHING -> stringResource(R.string.adb_status_pair_searching) to MaterialTheme.colorScheme.primary
        ServerState.PAIRING_INPUT -> stringResource(R.string.adb_status_pair_input) to MaterialTheme.colorScheme.primary
        ServerState.PAIRING_WORKING -> stringResource(R.string.adb_status_pair_working) to MaterialTheme.colorScheme.primary
        ServerState.CONNECTING -> stringResource(R.string.adb_status_connecting) to MaterialTheme.colorScheme.primary
        ServerState.PUSHING -> stringResource(R.string.adb_status_pushing, state.pushProgress) to MaterialTheme.colorScheme.tertiary
        ServerState.STARTING -> stringResource(R.string.adb_status_starting) to MaterialTheme.colorScheme.primary
        ServerState.WAITING -> stringResource(R.string.adb_status_waiting) to MaterialTheme.colorScheme.primary
        ServerState.RUNNING -> stringResource(R.string.adb_status_running) to MaterialTheme.colorScheme.primary
        ServerState.ERROR -> stringResource(R.string.adb_status_error) to MaterialTheme.colorScheme.error
    }

    val statusIcon = when (state.serverState) {
        ServerState.RUNNING -> Icons.Filled.CheckCircle
        ServerState.ERROR -> Icons.Filled.Error
        else -> Icons.Filled.Circle
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            statusIcon,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            statusText,
            style = MaterialTheme.typography.bodySmall,
            color = statusColor
        )
    }
}

@Composable
private fun OverlayControlCard(
    hasOverlayPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Widgets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.overlay_control), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))

            if (!hasOverlayPermission) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.overlay_permission_required),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRequestPermission) {
                            Text(stringResource(R.string.btn_grant_permission))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onStartOverlay,
                    enabled = hasOverlayPermission,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_start_overlay))
                }
                OutlinedButton(
                    onClick = onStopOverlay,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_stop_overlay))
                }
            }
        }
    }
}

@Composable
private fun OpenClawSettingsCard() {
    val context = LocalContext.current
    val terminalVm: TerminalViewModel = viewModel()

    // Use safe calls with default states when service is not yet connected
    val openClawState by terminalVm.openClawInstaller?.state?.collectAsState()
        ?: remember { mutableStateOf(OpenClawInstaller.State.NOT_CHECKED) }
    val openClawMessage by terminalVm.openClawInstaller?.statusMessage?.collectAsState()
        ?: remember { mutableStateOf("") }
    val gatewayState by terminalVm.gatewayManager?.state?.collectAsState()
        ?: remember { mutableStateOf(OpenClawGatewayManager.GatewayState.STOPPED) }
    val gatewayMessage by terminalVm.gatewayManager?.statusMessage?.collectAsState()
        ?: remember { mutableStateOf("") }
    val isModelConfigured by terminalVm.isModelConfigured.collectAsState()

    LaunchedEffect(Unit) {
        terminalVm.refreshOpenClawStatus()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("OpenClaw AI Agent", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))

            val statusText = when (openClawState) {
                OpenClawInstaller.State.NOT_CHECKED -> "Not checked"
                OpenClawInstaller.State.CHECKING -> "Checking..."
                OpenClawInstaller.State.NOT_INSTALLED -> "Not installed"
                OpenClawInstaller.State.INSTALLING_NODE -> "Installing Node.js..."
                OpenClawInstaller.State.INSTALLING_OPENCLAW -> "Installing OpenClaw..."
                OpenClawInstaller.State.CONFIGURING -> "Configuring..."
                OpenClawInstaller.State.READY -> "Installed"
                OpenClawInstaller.State.RUNNING -> "Running"
                OpenClawInstaller.State.ERROR -> "Error"
            }

            val gatewayStatusText = when (gatewayState) {
                OpenClawGatewayManager.GatewayState.STOPPED -> "Gateway stopped"
                OpenClawGatewayManager.GatewayState.STARTING -> "Gateway starting..."
                OpenClawGatewayManager.GatewayState.RUNNING -> "Gateway running :18789"
                OpenClawGatewayManager.GatewayState.ERROR -> "Gateway error"
            }

            val statusColor = when {
                gatewayState == OpenClawGatewayManager.GatewayState.RUNNING -> MaterialTheme.colorScheme.primary
                openClawState == OpenClawInstaller.State.ERROR -> MaterialTheme.colorScheme.error
                openClawState == OpenClawInstaller.State.READY -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        gatewayState == OpenClawGatewayManager.GatewayState.RUNNING -> Icons.Filled.CheckCircle
                        openClawState == OpenClawInstaller.State.ERROR -> Icons.Filled.Error
                        else -> Icons.Filled.Circle
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
                    if (openClawState == OpenClawInstaller.State.READY || openClawState == OpenClawInstaller.State.RUNNING) {
                        Text(gatewayStatusText, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            val showNeedConfigHint = !isModelConfigured &&
                (openClawState == OpenClawInstaller.State.READY ||
                 openClawState == OpenClawInstaller.State.RUNNING)

            if (showNeedConfigHint) {
                Spacer(Modifier.height(8.dp))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "尚未配置 AI 模型",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Gateway 已启动，但需要配置 AI 模型才能使用。请打开 Web 控制台进行配置，或登录图图平台自动获取 AI 能力。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW,
                                    Uri.parse("http://127.0.0.1:18789/openclaw"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("打开 Web 控制台配置")
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (openClawState) {
                    OpenClawInstaller.State.NOT_INSTALLED -> {
                        FilledTonalButton(
                            onClick = { terminalVm.installOpenClaw() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Install OpenClaw")
                        }
                    }
                    OpenClawInstaller.State.READY -> {
                        if (gatewayState != OpenClawGatewayManager.GatewayState.RUNNING) {
                            FilledTonalButton(
                                onClick = { terminalVm.startGateway() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Start Gateway")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { terminalVm.stopGateway() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Stop Gateway")
                            }
                        }
                    }
                    OpenClawInstaller.State.INSTALLING_NODE,
                    OpenClawInstaller.State.INSTALLING_OPENCLAW,
                    OpenClawInstaller.State.CONFIGURING -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        OutlinedButton(
                            onClick = { terminalVm.installOpenClaw() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry Install")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugEntryCard(onNavigateDebug: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onNavigateDebug
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.debug_panel), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.debug_panel_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
