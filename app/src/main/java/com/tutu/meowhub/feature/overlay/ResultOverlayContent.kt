package com.tutu.meowhub.feature.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutu.meowhub.core.engine.SkillEngine
import com.tutu.meowhub.ui.theme.MeowGold
import com.tutu.meowhub.ui.theme.MeowGreen
import com.tutu.meowhub.ui.theme.MeowRed

private val CardBg = Color(0xFF2A2518)
private val TextPrimary = Color(0xFFF5EDD8)
private val TextSecondary = Color(0xFFB8AD9A)
private val DividerColor = Color(0xFF362F24)

@Composable
fun ResultOverlayContent(
    skillName: String,
    result: SkillEngine.RunResult,
    onDismiss: () -> Unit
) {
    val isSuccess = result.status == "success"
    val isAborted = result.status == "aborted"
    val statusIcon = when {
        isSuccess -> Icons.Outlined.CheckCircle
        isAborted -> Icons.Outlined.Cancel
        else -> Icons.Outlined.Error
    }
    val statusColor = when {
        isSuccess -> MeowGreen
        isAborted -> MeowGold
        else -> MeowRed
    }
    val statusText = when {
        isSuccess -> "执行完成"
        isAborted -> "已停止"
        else -> "执行出错"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = 28.dp, vertical = 60.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = skillName,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("步骤", "${result.completedSteps}/${result.totalSteps}")
                    StatItem("Tokens", "${result.tokensUsed}")
                    StatItem("状态", statusText)
                }

                if (!result.errorMessage.isNullOrEmpty() && !isSuccess) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = result.errorMessage,
                        color = MeowRed.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                Spacer(Modifier.height(14.dp))

                if (!result.summaryText.isNullOrEmpty()) {
                    Text(
                        text = "执行结果",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = result.summaryText,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                } else {
                    Text(
                        text = if (isSuccess) "所有步骤已完成。"
                        else if (isAborted) "任务被手动停止。"
                        else "任务执行过程中出现错误。",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeowGold,
                        contentColor = Color(0xFF2C2518)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "我知道了",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}
