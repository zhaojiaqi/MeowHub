package com.tutu.meowhub.feature.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tutu.meowhub.MeowApp
import com.tutu.meowhub.R
import com.tutu.meowhub.core.model.ConnectionState
import com.tutu.meowhub.ui.theme.*

private val ChatBg = MeowSurfaceDark
private val HeaderBg = MeowSurfaceContainerDark
private val BorderColor = Color(0xFF3A3526)
private val InputBg = MeowCardDark
private val TextPrimary = MeowOnSurfaceDark
private val TextSecondary = MeowOnSurfaceVariantDark
private val QuickBtnBg = MeowCardDark

@Composable
fun ChatScreen() {
    val viewModel: ChatViewModel = viewModel()
    val messages by viewModel.messages.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val agentState by viewModel.agentState.collectAsState()
    val connectionState by MeowApp.instance.tutuClient.connectionState.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatBg)
    ) {
        ChatHeader(
            connectionState = connectionState,
            onNewChat = { viewModel.startNewChat() }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    WelcomeContent()
                }
            }
            items(messages, key = { it.id }) { message ->
                ChatMessageItem(message)
            }
        }

        if (connectionState == ConnectionState.CONNECTED) {
            QuickActionsRow(
                isProcessing = isProcessing,
                onQuickAction = { text ->
                    viewModel.sendMessage(text)
                }
            )
        }

        ChatInputBar(
            inputText = inputText,
            onInputChange = { inputText = it },
            isProcessing = isProcessing,
            onSend = {
                if (inputText.isNotBlank()) {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                }
            },
            onStop = { viewModel.stopAgent() }
        )
    }
}

@Composable
private fun ChatHeader(
    connectionState: ConnectionState,
    onNewChat: () -> Unit
) {
    Column {
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(HeaderBg)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeaderBg)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_title),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when (connectionState) {
                        ConnectionState.CONNECTED -> MeowGreen
                        ConnectionState.CONNECTING, ConnectionState.AUTHENTICATING -> MeowOrange
                        ConnectionState.DISCONNECTED -> MeowRed
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when (connectionState) {
                            ConnectionState.CONNECTED -> stringResource(R.string.status_tutu_connected)
                            ConnectionState.CONNECTING -> stringResource(R.string.status_connecting)
                            ConnectionState.AUTHENTICATING -> stringResource(R.string.status_authenticating)
                            ConnectionState.DISCONNECTED -> stringResource(R.string.status_disconnected)
                        },
                        color = statusColor,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(InputBg)
                    .clickable(onClick = onNewChat)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.chat_new),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
        HorizontalDivider(color = BorderColor, thickness = 1.dp)
    }
}

@Composable
private fun WelcomeContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_meow_logo_golden),
            contentDescription = "TuTu",
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.chat_welcome_title),
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.chat_welcome_desc),
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun QuickActionsRow(
    isProcessing: Boolean,
    onQuickAction: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        QuickActionChip(Icons.Outlined.CameraAlt, stringResource(R.string.chat_quick_screenshot), !isProcessing) {
            onQuickAction("截个图看看当前屏幕")
        }
        QuickActionChip(Icons.Outlined.Chat, stringResource(R.string.chat_quick_wechat), !isProcessing) {
            onQuickAction("打开微信")
        }
        QuickActionChip(Icons.Outlined.MusicNote, stringResource(R.string.chat_quick_tiktok), !isProcessing) {
            onQuickAction("打开抖音")
        }
        QuickActionChip(Icons.Outlined.Home, stringResource(R.string.chat_quick_home), !isProcessing) {
            onQuickAction("返回桌面")
        }
        QuickActionChip(Icons.Outlined.Settings, stringResource(R.string.chat_quick_settings), !isProcessing) {
            onQuickAction("打开设置")
        }
    }
}

@Composable
private fun RowScope.QuickActionChip(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) QuickBtnBg else QuickBtnBg.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MeowGold else MeowGold.copy(alpha = 0.4f),
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.5f),
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isProcessing: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    HorizontalDivider(color = BorderColor, thickness = 1.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp, max = 120.dp),
            placeholder = {
                Text(
                    stringResource(R.string.chat_input_placeholder),
                    color = TextSecondary.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = InputBg,
                unfocusedContainerColor = InputBg,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = MeowGold,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            maxLines = 4,
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
        )

        Spacer(Modifier.width(8.dp))

        if (isProcessing) {
            IconButton(
                onClick = onStop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MeowRed)
            ) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = "Stop",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            val sendBg = if (inputText.isNotBlank()) {
                Brush.linearGradient(listOf(MeowGold, MeowOrange))
            } else {
                Brush.linearGradient(listOf(MeowGold.copy(alpha = 0.3f), MeowOrange.copy(alpha = 0.3f)))
            }
            IconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank(),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(sendBg)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
