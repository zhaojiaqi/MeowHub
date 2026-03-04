package com.tutu.miaohub.feature.debug

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tutu.miaohub.R
import com.tutu.miaohub.core.model.ConnectionState
import com.tutu.miaohub.core.model.ParamType
import com.tutu.miaohub.core.model.TutuCommandSpec
import com.tutu.miaohub.core.model.TutuCommands
import com.tutu.miaohub.core.socket.TutuSocketClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: DebugViewModel = viewModel(),
    onBack: (() -> Unit)? = null
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val screenshotBitmap by viewModel.screenshotBitmap.collectAsState()
    var showLogPanel by remember { mutableStateOf(true) }
    var commandDialog by remember { mutableStateOf<TutuCommandSpec?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TUTU Debug", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        ConnectionIndicator(connectionState)
                    }
                },
                actions = {
                    when (connectionState) {
                        ConnectionState.DISCONNECTED -> {
                            FilledTonalButton(onClick = { viewModel.connect() }) {
                                Icon(Icons.Default.Cable, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.btn_connect))
                            }
                        }
                        ConnectionState.CONNECTING, ConnectionState.AUTHENTICATING -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                        ConnectionState.CONNECTED -> {
                            OutlinedButton(onClick = { viewModel.disconnect() }) {
                                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.btn_disconnect))
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
            )
        },
        bottomBar = {
            BottomLogBar(
                logs = logs,
                expanded = showLogPanel,
                onToggle = { showLogPanel = !showLogPanel },
                onClear = { viewModel.clearLogs() }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.categories) { category ->
                CategoryCard(
                    category = category,
                    enabled = connectionState == ConnectionState.CONNECTED,
                    onCommandClick = { spec -> commandDialog = spec }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    commandDialog?.let { spec ->
        CommandDialog(
            spec = spec,
            onDismiss = { commandDialog = null },
            onExecute = { params, delaySec ->
                viewModel.executeCommand(spec, params, delaySec)
                commandDialog = null
            }
        )
    }

    screenshotBitmap?.let { bitmap ->
        ScreenshotPreviewDialog(
            bitmap = bitmap,
            onDismiss = { viewModel.dismissScreenshot() }
        )
    }
}

@Composable
private fun ConnectionIndicator(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
        ConnectionState.CONNECTING, ConnectionState.AUTHENTICATING -> MaterialTheme.colorScheme.tertiary
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error
    }
    val label = when (state) {
        ConnectionState.CONNECTED -> stringResource(R.string.label_connected)
        ConnectionState.CONNECTING -> stringResource(R.string.status_connecting)
        ConnectionState.AUTHENTICATING -> stringResource(R.string.status_authenticating)
        ConnectionState.DISCONNECTED -> stringResource(R.string.status_disconnected)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun CategoryCard(
    category: TutuCommands.CommandCategory,
    enabled: Boolean,
    onCommandClick: (TutuCommandSpec) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(category.nameResId),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.command_count, category.commands.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    category.commands.forEach { spec ->
                        CommandChip(
                            spec = spec,
                            enabled = enabled,
                            onClick = { onCommandClick(spec) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandChip(
    spec: TutuCommandSpec,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(spec.nameResId),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = spec.type,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = stringResource(R.string.btn_execute),
            modifier = Modifier.size(20.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun CommandDialog(
    spec: TutuCommandSpec,
    onDismiss: () -> Unit,
    onExecute: (Map<String, String>, Int) -> Unit
) {
    val paramValues = remember {
        mutableStateMapOf<String, String>().apply {
            spec.params.forEach { p -> put(p.key, p.defaultValue) }
        }
    }
    var delaySec by remember { mutableStateOf(if (spec.needsDelay) "5" else "0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(spec.nameResId), fontWeight = FontWeight.Bold)
                Text(
                    spec.type,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (spec.params.isEmpty() && !spec.needsDelay) {
                    Text(stringResource(R.string.no_params_hint))
                }
                spec.params.forEach { param ->
                    OutlinedTextField(
                        value = paramValues[param.key] ?: "",
                        onValueChange = { paramValues[param.key] = it },
                        label = {
                            Text(
                                "${stringResource(param.labelResId)}${if (param.required) " *" else ""}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when (param.paramType) {
                                ParamType.INT, ParamType.LONG -> KeyboardType.Number
                                ParamType.FLOAT, ParamType.DOUBLE -> KeyboardType.Decimal
                                else -> KeyboardType.Text
                            }
                        )
                    )
                }
                if (spec.needsDelay) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    OutlinedTextField(
                        value = delaySec,
                        onValueChange = { delaySec = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.delay_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text(stringResource(R.string.delay_hint)) }
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onExecute(paramValues.toMap(), delaySec.toIntOrNull() ?: 0) }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (spec.needsDelay && (delaySec.toIntOrNull() ?: 0) > 0) stringResource(R.string.btn_delayed_send) else stringResource(R.string.btn_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
private fun BottomLogBar(
    logs: List<TutuSocketClient.LogEntry>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClear: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.label_log), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.log_count, logs.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                if (logs.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.btn_clear), modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Icon(
                    if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs) { entry ->
                    LogEntryRow(entry, dateFormat)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(
    entry: TutuSocketClient.LogEntry,
    dateFormat: SimpleDateFormat
) {
    val (icon, color) = when (entry.direction) {
        TutuSocketClient.Direction.SENT -> Icons.Default.ArrowUpward to MaterialTheme.colorScheme.primary
        TutuSocketClient.Direction.RECEIVED -> Icons.Default.ArrowDownward to MaterialTheme.colorScheme.tertiary
        TutuSocketClient.Direction.SYSTEM -> Icons.Default.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(
            dateFormat.format(Date(entry.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Spacer(Modifier.width(4.dp))
        Text(
            entry.content,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = color,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun ScreenshotPreviewDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f))
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.screenshot_preview),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
            )

            FilledTonalIconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.btn_close))
            }

            Text(
                "${bitmap.width} x ${bitmap.height}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }
}
