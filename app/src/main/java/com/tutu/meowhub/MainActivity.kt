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
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tutu.meowhub.R
import com.tutu.meowhub.core.service.MeowOverlayService
import com.tutu.meowhub.feature.account.AccountScreen
import com.tutu.meowhub.feature.account.LoginScreen
import com.tutu.meowhub.feature.debug.DebugScreen
import com.tutu.meowhub.feature.navigation.MainScreen
import com.tutu.meowhub.feature.settings.AdvancedSettingsScreen
import com.tutu.meowhub.feature.settings.AppToolsScreen
import com.tutu.meowhub.feature.settings.AboutScreen
import com.tutu.meowhub.feature.settings.ServicesScreen
import com.tutu.meowhub.ui.theme.MeowHubTheme
import kotlinx.serialization.Serializable

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

// Navigation routes using type-safe navigation
@Serializable object RouteMain
@Serializable object RouteDebug
@Serializable object RouteLogin
@Serializable object RouteAccount
@Serializable object RouteAdvancedSettings
@Serializable object RouteAppTools
@Serializable object RouteServices
@Serializable object RouteAbout

@Composable
fun MainNavigation(onRequestOverlayPermission: () -> Unit) {
    val navController = rememberNavController()
    var showLoginPrompt by remember { mutableStateOf(false) }
    var loginPromptReason by remember { mutableStateOf("") }
    var showSocketAuthDialog by remember { mutableStateOf(false) }
    var hasShownSocketAuthDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        MeowApp.instance.loginRequiredEvent.collect { reason ->
            loginPromptReason = reason
            showLoginPrompt = true
        }
    }

    LaunchedEffect(Unit) {
        MeowApp.instance.socketAuthRequiredEvent.collect {
            if (!hasShownSocketAuthDialog) {
                hasShownSocketAuthDialog = true
                showSocketAuthDialog = true
            }
        }
    }

    if (showSocketAuthDialog) {
        AlertDialog(
            onDismissRequest = { showSocketAuthDialog = false },
            title = { Text(stringResource(R.string.socket_auth_required_title)) },
            text = { Text(stringResource(R.string.socket_auth_required_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSocketAuthDialog = false
                    navController.navigate(RouteLogin)
                }) {
                    Text(stringResource(R.string.btn_go_login))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSocketAuthDialog = false }) {
                    Text(stringResource(R.string.btn_chat_only))
                }
            }
        )
    }

    if (showLoginPrompt) {
        AlertDialog(
            onDismissRequest = { showLoginPrompt = false },
            title = { Text("需要登录") },
            text = { Text(loginPromptReason) },
            confirmButton = {
                TextButton(onClick = {
                    showLoginPrompt = false
                    navController.navigate(RouteLogin)
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

    NavHost(
        navController = navController,
        startDestination = RouteMain,
        enterTransition = { slideInHorizontally(tween(300)) { it } },
        exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
        popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
        popExitTransition = { slideOutHorizontally(tween(300)) { it } }
    ) {
        composable<RouteMain> {
            MainScreen(
                onNavigateAdvancedSettings = { navController.navigate(RouteAdvancedSettings) },
                onNavigateAppTools = { navController.navigate(RouteAppTools) },
                onNavigateServices = { navController.navigate(RouteServices) },
                onNavigateAbout = { navController.navigate(RouteAbout) },
                onNavigateLogin = { navController.navigate(RouteLogin) },
                onNavigateAccount = { navController.navigate(RouteAccount) },
                onRequestOverlayPermission = onRequestOverlayPermission
            )
        }
        composable<RouteDebug> {
            DebugScreen(onBack = { navController.popBackStack() })
        }
        composable<RouteLogin> {
            LoginScreen(onBack = { navController.popBackStack() })
        }
        composable<RouteAccount> {
            AccountScreen(onBack = { navController.popBackStack() })
        }
        composable<RouteAdvancedSettings> {
            AdvancedSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<RouteAppTools> {
            AppToolsScreen(onBack = { navController.popBackStack() })
        }
        composable<RouteServices> {
            ServicesScreen(
                onRequestOverlayPermission = onRequestOverlayPermission,
                onNavigateDebug = { navController.navigate(RouteDebug) },
                onBack = { navController.popBackStack() }
            )
        }
        composable<RouteAbout> {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
