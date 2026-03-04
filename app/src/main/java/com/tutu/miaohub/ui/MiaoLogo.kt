package com.tutu.miaohub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tutu.miaohub.ui.theme.MiaoGold
import com.tutu.miaohub.ui.theme.MiaoOrangeLight

@Composable
fun MiaoLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = Color.White,
    style: LogoStyle = LogoStyle.Stroke,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val sw = s * 0.058f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (style) {
            LogoStyle.Stroke -> {
                drawEarOutlines(s, color, stroke)
                drawFaceOutline(s, color, stroke)
                drawEyes(s, color)
            }
            LogoStyle.Filled -> {
                drawEarOutlines(s, color, stroke)
                drawFaceOutline(s, color, stroke)
                drawEyes(s, color)
            }
            LogoStyle.Gradient -> {
                val brush = Brush.verticalGradient(
                    listOf(MiaoOrangeLight, MiaoGold),
                    startY = 0f,
                    endY = s
                )
                drawEarOutlinesGradient(s, brush, stroke)
                drawFaceOutlineGradient(s, brush, stroke)
                drawEyesGradient(s, brush)
            }
        }
    }
}

enum class LogoStyle { Stroke, Filled, Gradient }

// ---- Face: wide octagon, horizontally stretched ----
// The face is wider than tall, with chamfered corners.
// Proportions derived from the reference image.

private fun facePath(s: Float): Path {
    // Face bounding box
    val fL = s * 0.13f   // left edge
    val fR = s * 0.87f   // right edge
    val fT = s * 0.34f   // top edge
    val fB = s * 0.93f   // bottom edge
    val cx = s * 0.15f   // corner cut x
    val cy = s * 0.13f   // corner cut y

    return Path().apply {
        // Start from top-left after chamfer, go clockwise
        moveTo(fL + cx, fT)          // top-left chamfer end
        lineTo(fR - cx, fT)          // top edge
        lineTo(fR, fT + cy)          // top-right chamfer
        lineTo(fR, fB - cy)          // right edge
        lineTo(fR - cx, fB)          // bottom-right chamfer
        lineTo(fL + cx, fB)          // bottom edge
        lineTo(fL, fB - cy)          // bottom-left chamfer
        lineTo(fL, fT + cy)          // left edge
        close()
    }
}

private fun DrawScope.drawFaceOutline(s: Float, color: Color, stroke: Stroke) {
    drawPath(facePath(s), color = color, style = stroke)
}

private fun DrawScope.drawFaceOutlineGradient(s: Float, brush: Brush, stroke: Stroke) {
    drawPath(facePath(s), brush = brush, style = stroke)
}

// ---- Ears: thick outlines with inner ear cavity ----
// Each ear is drawn as two nested triangles (outer and inner),
// connected to the face at the top corners.

private fun leftEarOuter(s: Float): Path {
    val fL = s * 0.13f
    val fT = s * 0.34f
    val cy = s * 0.13f

    return Path().apply {
        moveTo(fL, fT + cy)                 // bottom-left of ear (face corner)
        lineTo(s * 0.20f, s * 0.05f)        // ear tip (outward)
        lineTo(s * 0.44f, fT)               // bottom-right of ear (meets face top)
    }
}

private fun leftEarInner(s: Float): Path {
    return Path().apply {
        moveTo(s * 0.17f, s * 0.38f)        // inner bottom-left
        lineTo(s * 0.23f, s * 0.15f)        // inner tip
        lineTo(s * 0.38f, s * 0.37f)        // inner bottom-right
    }
}

private fun rightEarOuter(s: Float): Path {
    val fR = s * 0.87f
    val fT = s * 0.34f
    val cy = s * 0.13f

    return Path().apply {
        moveTo(s * 0.56f, fT)               // bottom-left of ear (meets face top)
        lineTo(s * 0.80f, s * 0.05f)        // ear tip (outward)
        lineTo(fR, fT + cy)                 // bottom-right of ear (face corner)
    }
}

private fun rightEarInner(s: Float): Path {
    return Path().apply {
        moveTo(s * 0.62f, s * 0.37f)        // inner bottom-left
        lineTo(s * 0.77f, s * 0.15f)        // inner tip
        lineTo(s * 0.83f, s * 0.38f)        // inner bottom-right
    }
}

private fun DrawScope.drawEarOutlines(s: Float, color: Color, stroke: Stroke) {
    drawPath(leftEarOuter(s), color = color, style = stroke)
    drawPath(leftEarInner(s), color = color, style = stroke)
    drawPath(rightEarOuter(s), color = color, style = stroke)
    drawPath(rightEarInner(s), color = color, style = stroke)
}

private fun DrawScope.drawEarOutlinesGradient(s: Float, brush: Brush, stroke: Stroke) {
    drawPath(leftEarOuter(s), brush = brush, style = stroke)
    drawPath(leftEarInner(s), brush = brush, style = stroke)
    drawPath(rightEarOuter(s), brush = brush, style = stroke)
    drawPath(rightEarInner(s), brush = brush, style = stroke)
}

// ---- Eyes: two solid circles, symmetrical, fairly large ----

private fun DrawScope.drawEyes(s: Float, color: Color) {
    val eyeR = s * 0.085f
    val eyeY = s * 0.63f
    drawCircle(color = color, radius = eyeR, center = Offset(s * 0.37f, eyeY))
    drawCircle(color = color, radius = eyeR, center = Offset(s * 0.63f, eyeY))
}

private fun DrawScope.drawEyesGradient(s: Float, brush: Brush) {
    val eyeR = s * 0.085f
    val eyeY = s * 0.63f
    drawCircle(brush = brush, radius = eyeR, center = Offset(s * 0.37f, eyeY))
    drawCircle(brush = brush, radius = eyeR, center = Offset(s * 0.63f, eyeY))
}
