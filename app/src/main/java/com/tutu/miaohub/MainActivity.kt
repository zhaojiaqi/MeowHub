package com.tutu.miaohub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.*
import com.tutu.miaohub.core.service.MiaoOverlayService
import com.tutu.miaohub.feature.debug.DebugScreen
import com.tutu.miaohub.feature.navigation.MainScreen
import com.tutu.miaohub.ui.theme.MiaoHubTheme

class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            MiaoOverlayService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Settings.canDrawOverlays(this)) {
            MiaoOverlayService.start(this)
        }

        setContent {
            MiaoHubTheme {
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

@Composable
fun MainNavigation(onRequestOverlayPermission: () -> Unit) {
    var showDebug by remember { mutableStateOf(false) }

    if (showDebug) {
        DebugScreen(onBack = { showDebug = false })
    } else {
        MainScreen(
            onNavigateDebug = { showDebug = true },
            onRequestOverlayPermission = onRequestOverlayPermission
        )
    }
}
