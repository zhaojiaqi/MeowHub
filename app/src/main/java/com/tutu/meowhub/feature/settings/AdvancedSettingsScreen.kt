package com.tutu.meowhub.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutu.meowhub.R
import com.tutu.meowhub.core.engine.DoubaoAiProvider
import com.tutu.meowhub.core.settings.AiSettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { AiSettingsManager(context) }
    val scope = rememberCoroutineScope()

    var useOwnKey by remember { mutableStateOf(settingsManager.useOwnApiKey) }
    var apiKey by remember { mutableStateOf(settingsManager.apiKey) }
    var selectedModel by remember { mutableStateOf(settingsManager.modelId) }
    var baseUrl by remember { mutableStateOf(settingsManager.baseUrl) }
    var showApiKey by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    var tutuAppId by remember { mutableStateOf(settingsManager.tutuAppId) }
    var tutuAppSecret by remember { mutableStateOf(settingsManager.tutuAppSecret) }
    var showTutuSecret by remember { mutableStateOf(false) }

    var testState by remember { mutableStateOf<TestState>(TestState.Idle) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.advanced_settings_title),
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.ai_api_settings),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.use_own_api_key),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                stringResource(R.string.use_own_api_key_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useOwnKey,
                            onCheckedChange = {
                                useOwnKey = it
                                settingsManager.useOwnApiKey = it
                            }
                        )
                    }

                    if (useOwnKey) {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                settingsManager.apiKey = it
                            },
                            label = { Text(stringResource(R.string.api_key_label)) },
                            placeholder = { Text(stringResource(R.string.api_key_placeholder)) },
                            visualTransformation = if (showApiKey)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            trailingIcon = {
                                TextButton(onClick = { showApiKey = !showApiKey }) {
                                    Text(
                                        if (showApiKey) stringResource(R.string.hide)
                                        else stringResource(R.string.show)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        ExposedDropdownMenuBox(
                            expanded = modelDropdownExpanded,
                            onExpandedChange = { modelDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedModel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.model_id_label)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = modelDropdownExpanded,
                                onDismissRequest = { modelDropdownExpanded = false }
                            ) {
                                AiSettingsManager.AVAILABLE_MODELS.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model) },
                                        onClick = {
                                            selectedModel = model
                                            settingsManager.modelId = model
                                            modelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = {
                                baseUrl = it
                                settingsManager.baseUrl = it
                            },
                            label = { Text(stringResource(R.string.base_url_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                testState = TestState.Testing
                                scope.launch {
                                    testState = try {
                                        val provider = DoubaoAiProvider(
                                            apiKey = apiKey.trim(),
                                            baseUrl = baseUrl.trim(),
                                            modelId = selectedModel.trim()
                                        )
                                        val reply = provider.analyze(
                                            prompt = "Say hi in one word.",
                                            screenshotBase64 = null,
                                            uiNodesJson = null,
                                            history = emptyList(),
                                            onToken = null
                                        )
                                        if (reply.isNotBlank()) TestState.Success(reply.take(100))
                                        else TestState.Error("Empty response")
                                    } catch (e: Exception) {
                                        TestState.Error(e.message ?: "Unknown error")
                                    }
                                }
                            },
                            enabled = apiKey.isNotBlank() && selectedModel.isNotBlank()
                                    && testState !is TestState.Testing,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (testState is TestState.Testing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.test_api))
                        }

                        when (val state = testState) {
                            is TestState.Success -> {
                                Spacer(Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.test_api_success, state.reply),
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            is TestState.Error -> {
                                Spacer(Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.test_api_fail, state.message),
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.tutu_connection_title),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        stringResource(R.string.tutu_connection_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = tutuAppId,
                        onValueChange = {
                            tutuAppId = it
                            settingsManager.tutuAppId = it
                        },
                        label = { Text(stringResource(R.string.tutu_app_id_label)) },
                        placeholder = { Text(stringResource(R.string.tutu_app_id_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tutuAppSecret,
                        onValueChange = {
                            tutuAppSecret = it
                            settingsManager.tutuAppSecret = it
                        },
                        label = { Text(stringResource(R.string.tutu_app_secret_label)) },
                        placeholder = { Text(stringResource(R.string.tutu_app_secret_placeholder)) },
                        visualTransformation = if (showTutuSecret)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showTutuSecret = !showTutuSecret }) {
                                Text(
                                    if (showTutuSecret) stringResource(R.string.hide)
                                    else stringResource(R.string.show)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

private sealed class TestState {
    data object Idle : TestState()
    data object Testing : TestState()
    data class Success(val reply: String) : TestState()
    data class Error(val message: String) : TestState()
}
