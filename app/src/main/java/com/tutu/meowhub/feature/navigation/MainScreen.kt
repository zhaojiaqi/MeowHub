package com.tutu.meowhub.feature.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tutu.meowhub.R
import com.tutu.meowhub.feature.chat.ChatScreen
import com.tutu.meowhub.feature.market.MarketScreen
import com.tutu.meowhub.feature.myskills.MySkillsScreen
import com.tutu.meowhub.feature.terminal.TerminalScreen
import com.tutu.meowhub.feature.settings.AdbSetupGuideDialog
import com.tutu.meowhub.feature.settings.AdbSetupPromptDialog
import com.tutu.meowhub.feature.settings.AdbViewModel
import com.tutu.meowhub.feature.settings.SettingsScreen

enum class MainTab(
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CHAT(R.string.tab_chat, Icons.Filled.Chat, Icons.Outlined.Chat),
    MARKET(R.string.tab_market, Icons.Filled.Store, Icons.Outlined.Store),
    TERMINAL(R.string.tab_terminal, Icons.Filled.Terminal, Icons.Outlined.Terminal),
    MY_SKILLS(R.string.tab_my_skills, Icons.Filled.Person, Icons.Outlined.Person),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MainScreen(
    onNavigateDebug: () -> Unit,
    onNavigateLogin: () -> Unit = {},
    onNavigateAccount: () -> Unit = {},
    onRequestOverlayPermission: () -> Unit
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(MainTab.CHAT) }
    val adbViewModel: AdbViewModel = viewModel()
    val autoResult by adbViewModel.autoConnectResult.collectAsState()

    var showPromptDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var pendingResult by remember { mutableStateOf(AdbViewModel.AutoConnectResult.PENDING) }
    var waitingForReturn by remember { mutableStateOf(false) }
    var needsPairing by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showGuideDialog = true
        }
    }

    LaunchedEffect(autoResult) {
        when (autoResult) {
            AdbViewModel.AutoConnectResult.NO_KEY,
            AdbViewModel.AutoConnectResult.WIRELESS_DEBUG_OFF,
            AdbViewModel.AutoConnectResult.KEY_EXPIRED -> {
                pendingResult = autoResult
                showPromptDialog = true
            }
            else -> {}
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (waitingForReturn) {
            waitingForReturn = false
            if (!needsPairing) {
                adbViewModel.startServer()
            }
        }
    }

    if (showPromptDialog) {
        AdbSetupPromptDialog(
            result = pendingResult,
            onDismiss = {
                showPromptDialog = false
                adbViewModel.dismissAutoConnectResult()
            },
            onConfirm = {
                showPromptDialog = false
                needsPairing = pendingResult == AdbViewModel.AutoConnectResult.NO_KEY
                        || pendingResult == AdbViewModel.AutoConnectResult.KEY_EXPIRED

                if (needsPairing
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    showGuideDialog = true
                }
            }
        )
    }

    if (showGuideDialog) {
        AdbSetupGuideDialog(
            needsPairing = needsPairing,
            onDismiss = {
                showGuideDialog = false
                adbViewModel.dismissAutoConnectResult()
            },
            onGoToSettings = {
                showGuideDialog = false
                waitingForReturn = true
                if (needsPairing) {
                    adbViewModel.startPairing()
                }
                adbViewModel.dismissAutoConnectResult()
            }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = stringResource(tab.labelResId)
                            )
                        },
                        label = { Text(stringResource(tab.labelResId)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(bottom = innerPadding.calculateBottomPadding())),
            label = "main_tab"
        ) { tab ->
            when (tab) {
                MainTab.CHAT -> ChatScreen()
                MainTab.MARKET -> MarketScreen()
                MainTab.TERMINAL -> TerminalScreen()
                MainTab.MY_SKILLS -> MySkillsScreen()
                MainTab.SETTINGS -> SettingsScreen(
                    adbViewModel = adbViewModel,
                    onNavigateDebug = onNavigateDebug,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onNavigateLogin = onNavigateLogin,
                    onNavigateAccount = onNavigateAccount
                )
            }
        }
    }
}
