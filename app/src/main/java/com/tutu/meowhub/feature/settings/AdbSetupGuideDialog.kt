package com.tutu.meowhub.feature.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutu.meowhub.R

@Composable
fun AdbSetupPromptDialog(
    result: AdbViewModel.AutoConnectResult,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val (title, message, icon, confirmText) = when (result) {
        AdbViewModel.AutoConnectResult.NO_KEY -> PromptConfig(
            title = stringResource(R.string.adb_setup_title_first_time),
            message = stringResource(R.string.adb_setup_msg_first_time),
            icon = Icons.Filled.PhonelinkSetup,
            confirmText = stringResource(R.string.adb_setup_btn_setup_now)
        )
        AdbViewModel.AutoConnectResult.WIRELESS_DEBUG_OFF -> PromptConfig(
            title = stringResource(R.string.adb_setup_title_wireless_off),
            message = stringResource(R.string.adb_setup_msg_wireless_off),
            icon = Icons.Filled.WifiOff,
            confirmText = stringResource(R.string.adb_setup_btn_enable_now)
        )
        AdbViewModel.AutoConnectResult.KEY_EXPIRED -> PromptConfig(
            title = stringResource(R.string.adb_setup_title_key_expired),
            message = stringResource(R.string.adb_setup_msg_key_expired),
            icon = Icons.Filled.Wifi,
            confirmText = stringResource(R.string.adb_setup_btn_repair_now)
        )
        else -> return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.adb_setup_btn_later))
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(confirmText)
            }
        }
    )
}

private data class PromptConfig(
    val title: String,
    val message: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val confirmText: String
)

@Composable
fun AdbSetupGuideDialog(
    needsPairing: Boolean,
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                stringResource(R.string.adb_guide_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.adb_guide_step1_enable),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (needsPairing) {
                    Text(
                        stringResource(R.string.adb_guide_step2_pair),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.adb_guide_step3_code),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            stringResource(R.string.adb_guide_notification_tip),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.adb_guide_step_return),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        stringResource(R.string.adb_guide_note),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.adb_setup_btn_later))
            }
        },
        confirmButton = {
            Button(onClick = {
                onGoToSettings()
                openDeveloperOptions(context)
            }) {
                Text(stringResource(R.string.adb_guide_btn_go_settings))
            }
        }
    )
}

private fun openDeveloperOptions(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (_: Exception) {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
