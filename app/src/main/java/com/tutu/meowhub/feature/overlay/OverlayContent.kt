package com.tutu.meowhub.feature.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutu.meowhub.MeowApp
import com.tutu.meowhub.R
import com.tutu.meowhub.core.engine.SkillEngine
import com.tutu.meowhub.core.model.ConnectionState
import com.tutu.meowhub.core.socket.TutuSocketClient
import com.tutu.meowhub.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val BubbleGradient = listOf(MeowGold, MeowOrange)
private val PanelSurface = Color(0xFF2A2518)
private val PanelCard = Color(0xFF362F24)
private val PanelText = Color(0xFFF5EDD8)
private val PanelTextDim = Color(0xFFB8AD9A)

private val SkillActiveGlow1 = Color(0xFF4FC3F7)
private val SkillActiveGlow2 = Color(0xFF7C4DFF)
private val SkillActiveGlow3 = Color(0xFF00E5FF)
private val SkillPausedGlow = Color(0xFFFFB74D)

@Composable
fun OverlayContent(
    client: TutuSocketClient,
    onDragUpdate: (Float, Float) -> Unit,
    onClose: () -> Unit,
    onRequestFocus: () -> Unit = {},
    onReleaseFocus: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val connectionState by client.connectionState.collectAsState()
    val scope = rememberCoroutineScope()

    val engine = MeowApp.instance.skillEngine
    val engineState by engine.state.collectAsState()
    val currentSkill by engine.currentSkill.collectAsState()
    val currentStepIndex by engine.currentStepIndex.collectAsState()
    val currentStepLabel by engine.currentStepLabel.collectAsState()

    val isSkillActive = engineState == SkillEngine.EngineState.RUNNING ||
            engineState == SkillEngine.EngineState.PAUSED ||
            engineState == SkillEngine.EngineState.LOADING

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (expanded) {
            ExpandedPanel(
                connectionState = connectionState,
                onCollapse = {
                    expanded = false
                    onReleaseFocus()
                },
                onRequestFocus = onRequestFocus,
                onSendInstruction = { instruction ->
                    expanded = false
                    onReleaseFocus()
                    scope.launch {
                        engine.runInstruction(instruction)
                    }
                }
            )
        } else {
            FloatingBubble(
                connectionState = connectionState,
                isSkillActive = isSkillActive,
                isPaused = engineState == SkillEngine.EngineState.PAUSED,
                onTap = { expanded = true },
                onDrag = onDragUpdate
            )
        }

        if (isSkillActive && !expanded) {
            Spacer(Modifier.height(4.dp))
            SkillStatusBar(
                engineState = engineState,
                skillName = currentSkill?.displayName ?: "Skill",
                stepLabel = currentStepLabel,
                currentStep = currentStepIndex,
                totalSteps = currentSkill?.steps?.size ?: 0,
                onPauseResume = {
                    if (engineState == SkillEngine.EngineState.PAUSED) engine.resume()
                    else engine.pause()
                },
                onStop = { engine.stop() }
            )
        }

    }
}

@Composable
private fun FloatingBubble(
    connectionState: ConnectionState,
    isSkillActive: Boolean,
    isPaused: Boolean,
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val statusColor = when (connectionState) {
        ConnectionState.CONNECTED -> MeowGreen
        ConnectionState.CONNECTING, ConnectionState.AUTHENTICATING -> MeowOrange
        ConnectionState.DISCONNECTED -> MeowRed
    }

    val infiniteTransition = rememberInfiniteTransition(label = "skillGlow")
    val glowAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowAngle"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val glowBrush = if (isSkillActive) {
        val colors = if (isPaused) listOf(SkillPausedGlow, MeowOrange, SkillPausedGlow)
        else listOf(SkillActiveGlow1, SkillActiveGlow2, SkillActiveGlow3, SkillActiveGlow1)
        val rad = Math.toRadians(glowAngle.toDouble())
        Brush.linearGradient(
            colors = colors.map { it.copy(alpha = glowAlpha) },
            start = Offset(
                (26 + 26 * cos(rad)).toFloat(),
                (26 + 26 * sin(rad)).toFloat()
            ),
            end = Offset(
                (26 - 26 * cos(rad)).toFloat(),
                (26 - 26 * sin(rad)).toFloat()
            )
        )
    } else null

    Box(
        modifier = Modifier
            .size(52.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.linearGradient(BubbleGradient))
            .then(
                if (glowBrush != null) Modifier.border(3.dp, glowBrush, CircleShape)
                else Modifier.border(2.5.dp, statusColor.copy(alpha = 0.85f), CircleShape)
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_meow_logo),
            contentDescription = "MeowHub",
            modifier = Modifier.size(34.dp)
        )
    }
}

/**
 * 紧凑的 Skill 执行状态条，显示在悬浮球下方。
 * 主体区域不拦截触摸事件（通过 WindowManager FLAG_NOT_TOUCHABLE 在 Service 层控制），
 * 仅暂停/停止按钮可响应点击。
 */
private val StepLabelGreen = Color(0xFF66DD6A)

@Composable
private fun SkillStatusBar(
    engineState: SkillEngine.EngineState,
    skillName: String,
    stepLabel: String,
    currentStep: Int,
    totalSteps: Int,
    onPauseResume: () -> Unit,
    onStop: () -> Unit
) {
    val progress = if (totalSteps > 0) (currentStep + 1).toFloat() / totalSteps else 0f
    val isPaused = engineState == SkillEngine.EngineState.PAUSED

    Row(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC1E1E1E))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = skillName,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (stepLabel.isNotEmpty()) {
                Text(
                    text = stepLabel,
                    color = StepLabelGreen,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (isPaused) SkillPausedGlow else SkillActiveGlow1,
                    trackColor = Color.White.copy(alpha = 0.15f),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${currentStep + 1}/$totalSteps",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 8.sp
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onPauseResume),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                contentDescription = if (isPaused) "Resume" else "Pause",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(14.dp)
            )
        }

        Spacer(Modifier.width(4.dp))

        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MeowRed.copy(alpha = 0.4f))
                .clickable(onClick = onStop),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Stop,
                contentDescription = "Stop",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun ExpandedPanel(
    connectionState: ConnectionState,
    onCollapse: () -> Unit,
    onRequestFocus: () -> Unit,
    onSendInstruction: (String) -> Unit
) {
    var instruction by remember { mutableStateOf("") }
    val isConnected = connectionState == ConnectionState.CONNECTED

    Card(
        modifier = Modifier
            .width(300.dp)
            .wrapContentHeight(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PanelSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
    ) {
        Column {
            PanelHeader(
                connectionState = connectionState,
                onCollapse = onCollapse
            )

            HorizontalDivider(color = PanelCard, thickness = 1.dp)

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) onRequestFocus() },
                    placeholder = {
                        Text(
                            stringResource(R.string.instruction_hint),
                            fontSize = 13.sp
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (instruction.isNotBlank()) {
                                    onSendInstruction(instruction.trim())
                                }
                            },
                            enabled = instruction.isNotBlank() && isConnected
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = stringResource(R.string.instruction_send),
                                tint = if (instruction.isNotBlank() && isConnected)
                                    MeowGold else PanelTextDim
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PanelText,
                        unfocusedTextColor = PanelText,
                        cursorColor = MeowGold,
                        focusedBorderColor = MeowGold,
                        unfocusedBorderColor = PanelCard
                    ),
                    shape = RoundedCornerShape(14.dp),
                    minLines = 1,
                    maxLines = 3
                )
            }
        }
    }
}

@Composable
private fun PanelHeader(
    connectionState: ConnectionState,
    onCollapse: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCollapse)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_meow_logo_golden),
            contentDescription = "MeowHub",
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(6.dp))
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "MeowHub",
                color = PanelText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
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
                    when (connectionState) {
                        ConnectionState.CONNECTED -> stringResource(R.string.status_tutu_connected)
                        ConnectionState.CONNECTING -> stringResource(R.string.status_connecting)
                        ConnectionState.AUTHENTICATING -> stringResource(R.string.status_authenticating)
                        ConnectionState.DISCONNECTED -> stringResource(R.string.status_disconnected)
                    },
                    color = statusColor,
                    fontSize = 10.sp
                )
            }
        }
    }
}

