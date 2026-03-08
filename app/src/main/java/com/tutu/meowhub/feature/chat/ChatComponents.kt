package com.tutu.meowhub.feature.chat

import androidx.compose.animation.core.*
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
private val TableHeaderBg = MeowGoldDark.copy(alpha = 0.35f)
private val TableOddRowBg = StepCardBg
private val TableEvenRowBg = AiBubbleColor

private sealed class ContentBlock {
    data class TextBlock(val text: String) : ContentBlock()
    data class TableBlock(
        val headers: List<String>,
        val rows: List<List<String>>
    ) : ContentBlock()
}

private val separatorLineRegex = Regex("^\\|[\\s:|-]+\\|$")

private fun splitContentBlocks(content: String): List<ContentBlock> {
    val lines = content.lines()
    val blocks = mutableListOf<ContentBlock>()
    val textBuffer = StringBuilder()
    var i = 0

    fun flushText() {
        val t = textBuffer.toString().trimEnd('\n')
        if (t.isNotEmpty()) blocks.add(ContentBlock.TextBlock(t))
        textBuffer.clear()
    }

    while (i < lines.size) {
        val line = lines[i].trimEnd()
        if (line.startsWith("|") && line.endsWith("|") && line.count { it == '|' } >= 3) {
            val tableLines = mutableListOf(line)
            var j = i + 1
            while (j < lines.size) {
                val next = lines[j].trimEnd()
                if (next.startsWith("|") && next.endsWith("|")) {
                    tableLines.add(next)
                    j++
                } else break
            }
            val sepIdx = tableLines.indexOfFirst { separatorLineRegex.matches(it.trim()) }
            if (sepIdx >= 1 && tableLines.size >= 3) {
                flushText()
                val headerLine = tableLines[sepIdx - 1]
                val headers = headerLine.split("|")
                    .drop(1).dropLast(1)
                    .map { it.trim() }
                val dataLines = tableLines.subList(sepIdx + 1, tableLines.size)
                val rows = dataLines.map { rowLine ->
                    rowLine.split("|")
                        .drop(1).dropLast(1)
                        .map { it.trim() }
                }
                if (sepIdx > 1) {
                    for (k in 0 until sepIdx - 1) {
                        textBuffer.appendLine(tableLines[k])
                    }
                    flushText()
                }
                blocks.add(ContentBlock.TableBlock(headers, rows))
                i = j
                continue
            }
        }
        textBuffer.appendLine(line)
        i++
    }
    flushText()
    return blocks
}

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserMessage(text: String) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

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
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        }
                    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiMessage(message: ChatMessage) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

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
                val blocks = remember(message.content) { splitContentBlocks(message.content) }
                val bubbleShape = RoundedCornerShape(
                    topStart = 14.dp,
                    topEnd = 14.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 14.dp
                )

                Column(
                    modifier = Modifier
                        .clip(bubbleShape)
                        .background(AiBubbleColor)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                clipboardManager.setText(AnnotatedString(message.content))
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    blocks.forEach { block ->
                        when (block) {
                            is ContentBlock.TextBlock -> {
                                MarkdownText(
                                    text = block.text,
                                    color = MeowOnSurfaceDark,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                            is ContentBlock.TableBlock -> {
                                MarkdownTableCard(
                                    headers = block.headers,
                                    rows = block.rows
                                )
                            }
                        }
                    }
                }
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
private fun MarkdownTableCard(
    headers: List<String>,
    rows: List<List<String>>
) {
    var showFullScreen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TableOddRowBg)
            .clickable { showFullScreen = true }
    ) {
        Column {
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .padding(bottom = 0.dp)
            ) {
                TableContent(headers, rows, fontSize = 12.sp)
            }
        }

        Icon(
            Icons.Outlined.OpenInFull,
            contentDescription = null,
            tint = MeowGold.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(14.dp)
        )
    }

    if (showFullScreen) {
        TableFullScreenDialog(
            headers = headers,
            rows = rows,
            onDismiss = { showFullScreen = false }
        )
    }
}

@Composable
private fun TableContent(
    headers: List<String>,
    rows: List<List<String>>,
    fontSize: TextUnit
) {
    val colCount = headers.size
    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        Row {
            for (col in 0 until colCount) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(TableHeaderBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = headers.getOrElse(col) { "" },
                        color = Color.White,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        rows.forEachIndexed { rowIdx, cells ->
            val rowBg = if (rowIdx % 2 == 0) TableEvenRowBg else TableOddRowBg
            Row {
                for (col in 0 until colCount) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(rowBg)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cells.getOrElse(col) { "" },
                            color = MeowOnSurfaceDark,
                            fontSize = fontSize,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableFullScreenDialog(
    headers: List<String>,
    rows: List<List<String>>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        val hScroll = rememberScrollState()
        val vScroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(0.5f, 3f)
            scope.launch {
                val newH = (hScroll.value + panChange.x).toInt().coerceIn(0, hScroll.maxValue)
                val newV = (vScroll.value + panChange.y).toInt().coerceIn(0, vScroll.maxValue)
                hScroll.scrollTo(newH)
                vScroll.scrollTo(newV)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MeowSurfaceDark)
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MeowSurfaceContainerDark)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "表格详情",
                    color = MeowOnSurfaceDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MeowOnSurfaceVariantDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state = transformState)
                    .verticalScroll(vScroll)
                    .horizontalScroll(hScroll)
            ) {
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val w = placeable.width
                            val h = placeable.height
                            val scaledW = (w * scale).toInt().coerceAtLeast(1)
                            val scaledH = (h * scale).toInt().coerceAtLeast(1)
                            layout(scaledW, scaledH) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier.graphicsLayer(
                            scaleX = scale,
                            scaleY = scale
                        )
                    ) {
                        TableContent(headers, rows, fontSize = 14.sp)
                    }
                }
            }
        }
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
