package com.tutu.miaohub.feature.market

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
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
import com.tutu.miaohub.R

fun checkOverlayPermission(context: Context): Boolean =
    Settings.canDrawOverlays(context)

fun checkNotificationPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else true

fun checkBatteryOptimization(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

fun allPermissionsGranted(context: Context): Boolean =
    checkOverlayPermission(context)
        && checkNotificationPermission(context)
        && checkBatteryOptimization(context)

@Composable
fun PermissionCheckDialog(
    onDismiss: () -> Unit,
    onAllGranted: () -> Unit
) {
    val context = LocalContext.current

    var hasOverlay by remember { mutableStateOf(checkOverlayPermission(context)) }
    var hasNotification by remember { mutableStateOf(checkNotificationPermission(context)) }
    var hasBattery by remember { mutableStateOf(checkBatteryOptimization(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasOverlay = checkOverlayPermission(context)
        hasNotification = checkNotificationPermission(context)
        hasBattery = checkBatteryOptimization(context)
    }

    val allGranted = hasOverlay && hasNotification && hasBattery

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                stringResource(R.string.perm_dialog_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PermissionRow(
                    name = stringResource(R.string.perm_overlay_name),
                    description = stringResource(R.string.perm_overlay_desc),
                    isGranted = hasOverlay,
                    onGrant = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                PermissionRow(
                    name = stringResource(R.string.perm_notification_name),
                    description = stringResource(R.string.perm_notification_desc),
                    isGranted = hasNotification,
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            )
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                PermissionRow(
                    name = stringResource(R.string.perm_battery_name),
                    description = stringResource(R.string.perm_battery_desc),
                    isGranted = hasBattery,
                    onGrant = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
        confirmButton = {
            Button(
                onClick = onAllGranted,
                enabled = allGranted
            ) {
                Text(stringResource(R.string.perm_btn_start))
            }
        }
    )
}

@Composable
private fun PermissionRow(
    name: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(22.dp)
                .padding(top = 2.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
        if (isGranted) {
            Text(
                stringResource(R.string.perm_granted),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
        } else {
            TextButton(
                onClick = onGrant,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    stringResource(R.string.perm_go_enable),
                    fontSize = 13.sp
                )
            }
        }
    }
}
