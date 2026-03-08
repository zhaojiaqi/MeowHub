package com.tutu.meowhub.feature.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutu.meowhub.R
import com.tutu.meowhub.core.model.ActionStep
import com.tutu.meowhub.core.model.ActionStepStatus
import com.tutu.meowhub.core.model.ChatMessage
import com.tutu.meowhub.core.model.ChatMessageEntity
import com.tutu.meowhub.ui.theme.*

private val UserBubbleColor = MeowGold
private val AiBubbleColor = MeowCardDark
private val SystemMsgBg = Color(0x33FFFFFF)
private val StepCardBg = Color(0xFF1E1B16)
private val StepDoneColor = MeowGreen
private val StepRunningColor = MeowGold
private val StepErrorColor = MeowRed

@Composable
fun ChatMessageItem(message: ChatMessage) {
    when (message.role) {
        ChatMessageEntity.ROLE_SYSTEM -> SystemMessage(message.content)
        ChatMessageEntity.ROLE_USER -> UserMessage(message.content)
        ChatMessageEntity.ROLE_ASSISTANT -> AiMessage(message)
    }
}

@Composable
private fun SystemMessage(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SystemMsgBg)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun UserMessage(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = 14.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(UserBubbleColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                color = MeowOnSurfaceLight,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MeowGoldDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AiMessage(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Image(
            painter = painterResource(R.drawable.ic_meow_logo_golden),
            contentDescription = "AI",
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.widthIn(max = 280.dp)) {
            if (message.content.isNotEmpty()) {
                MarkdownText(
                    text = message.content,
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 14.dp,
                                topEnd = 14.dp,
                                bottomStart = 4.dp,
                                bottomEnd = 14.dp
                            )
                        )
                        .background(AiBubbleColor)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    color = MeowOnSurfaceDark,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }

            if (message.isStreaming && message.content.isEmpty()) {
                TypingIndicator()
            }

            if (message.actionSteps.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                ActionStepsCard(message.actionSteps)
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val dot1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse, initialStartOffset = StartOffset(0)),
        label = "dot1"
    )
    val dot2 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse, initialStartOffset = StartOffset(200)),
        label = "dot2"
    )
    val dot3 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse, initialStartOffset = StartOffset(400)),
        label = "dot3"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AiBubbleColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(dot1, dot2, dot3).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MeowGold.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun ActionStepsCard(steps: List<ActionStep>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(StepCardBg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        steps.forEach { step ->
            ActionStepRow(step)
        }
    }
}

@Composable
private fun ActionStepRow(step: ActionStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (step.status) {
            ActionStepStatus.DONE -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = StepDoneColor,
                modifier = Modifier.size(14.dp)
            )
            ActionStepStatus.RUNNING -> {
                val transition = rememberInfiniteTransition(label = "step_spin")
                val alpha by transition.animateFloat(
                    initialValue = 0.4f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                    label = "step_alpha"
                )
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(StepRunningColor.copy(alpha = alpha))
                )
            }
            ActionStepStatus.ERROR -> Icon(
                Icons.Filled.Error,
                contentDescription = null,
                tint = StepErrorColor,
                modifier = Modifier.size(14.dp)
            )
            ActionStepStatus.PENDING -> Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            )
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = "Step ${step.index}",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.width(6.dp))

        Text(
            text = step.description,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MarkdownText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier
) {
    val annotated = remember(text) { parseMarkdown(text, color, fontSize) }
    Text(
        text = annotated,
        modifier = modifier,
        fontSize = fontSize,
        lineHeight = lineHeight
    )
}

private fun parseMarkdown(
    source: String,
    baseColor: Color,
    baseFontSize: TextUnit
): AnnotatedString = buildAnnotatedString {
    val lines = source.lines()
    lines.forEachIndexed { lineIdx, rawLine ->
        val line = rawLine.trimEnd()

        when {
            line.startsWith("### ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = baseFontSize * 1.05f, color = baseColor)) {
                    appendInlineMarkdown(line.removePrefix("### "), baseColor)
                }
            }
            line.startsWith("## ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.1f, color = baseColor)) {
                    appendInlineMarkdown(line.removePrefix("## "), baseColor)
                }
            }
            line.startsWith("# ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.2f, color = baseColor)) {
                    appendInlineMarkdown(line.removePrefix("# "), baseColor)
                }
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                withStyle(SpanStyle(color = baseColor)) {
                    append("  •  ")
                    appendInlineMarkdown(line.drop(2), baseColor)
                }
            }
            line.matches(Regex("^\\d+\\.\\s.*")) -> {
                val content = line.replaceFirst(Regex("^\\d+\\.\\s"), "")
                val num = line.substringBefore(".")
                withStyle(SpanStyle(color = baseColor)) {
                    append("  $num.  ")
                    appendInlineMarkdown(content, baseColor)
                }
            }
            else -> {
                withStyle(SpanStyle(color = baseColor)) {
                    appendInlineMarkdown(line, baseColor)
                }
            }
        }

        if (lineIdx < lines.lastIndex) append("\n")
    }
}

private val inlinePattern = Regex(
    """\*\*(.+?)\*\*""" +      // group 1: bold
    """|__(.+?)__""" +          // group 2: bold (underscore)
    """|`(.+?)`""" +            // group 3: inline code
    """|\*(.+?)\*""" +          // group 4: italic
    """|_(.+?)_"""              // group 5: italic (underscore)
)

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String, baseColor: Color) {
    var cursor = 0
    for (match in inlinePattern.findAll(text)) {
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }
        when {
            match.groupValues[1].isNotEmpty() || match.groupValues[2].isNotEmpty() -> {
                val content = match.groupValues[1].ifEmpty { match.groupValues[2] }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(content)
                }
            }
            match.groupValues[3].isNotEmpty() -> {
                withStyle(SpanStyle(
                    background = Color.White.copy(alpha = 0.08f),
                    color = MeowGold,
                    fontWeight = FontWeight.Medium
                )) {
                    append(" ${match.groupValues[3]} ")
                }
            }
            match.groupValues[4].isNotEmpty() || match.groupValues[5].isNotEmpty() -> {
                val content = match.groupValues[4].ifEmpty { match.groupValues[5] }
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(content)
                }
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}
