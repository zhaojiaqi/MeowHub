package com.tutu.meowhub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.Crossfade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.tutu.meowhub.core.service.MeowOverlayService
import com.tutu.meowhub.feature.account.AccountScreen
import com.tutu.meowhub.feature.account.LoginScreen
import com.tutu.meowhub.feature.debug.DebugScreen
import com.tutu.meowhub.feature.navigation.MainScreen
import com.tutu.meowhub.feature.settings.AdvancedSettingsScreen
import com.tutu.meowhub.ui.theme.MeowHubTheme

class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            MeowOverlayService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Settings.canDrawOverlays(this)) {
            MeowOverlayService.start(this)
        }

        setContent {
            MeowHubTheme {
                MainNavigation(
                    onRequestOverlayPermission = { requestOverlayPermission() }
                )
            }
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }
}

private enum class AppScreen { MAIN, DEBUG, LOGIN, ACCOUNT, ADVANCED_SETTINGS }

@Composable
fun MainNavigation(onRequestOverlayPermission: () -> Unit) {
    var currentScreen by remember { mutableStateOf(AppScreen.MAIN) }
    var showLoginPrompt by remember { mutableStateOf(false) }
    var loginPromptReason by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        MeowApp.instance.loginRequiredEvent.collect { reason ->
            loginPromptReason = reason
            showLoginPrompt = true
        }
    }

    if (showLoginPrompt) {
        AlertDialog(
            onDismissRequest = { showLoginPrompt = false },
            title = { Text("需要登录") },
            text = { Text(loginPromptReason) },
            confirmButton = {
                TextButton(onClick = {
                    showLoginPrompt = false
                    currentScreen = AppScreen.LOGIN
                }) {
                    Text("去登录")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginPrompt = false }) {
                    Text("取消")
                }
            }
        )
    }

    Crossfade(targetState = currentScreen, label = "app_screen") { screen ->
        when (screen) {
            AppScreen.MAIN -> MainScreen(
                onNavigateDebug = { currentScreen = AppScreen.DEBUG },
                onNavigateAdvancedSettings = { currentScreen = AppScreen.ADVANCED_SETTINGS },
                onNavigateLogin = { currentScreen = AppScreen.LOGIN },
                onNavigateAccount = { currentScreen = AppScreen.ACCOUNT },
                onRequestOverlayPermission = onRequestOverlayPermission
            )
            AppScreen.DEBUG -> DebugScreen(onBack = { currentScreen = AppScreen.MAIN })
            AppScreen.LOGIN -> LoginScreen(onBack = { currentScreen = AppScreen.MAIN })
            AppScreen.ACCOUNT -> AccountScreen(onBack = { currentScreen = AppScreen.MAIN })
            AppScreen.ADVANCED_SETTINGS -> AdvancedSettingsScreen(onBack = { currentScreen = AppScreen.MAIN })
        }
    }
}
