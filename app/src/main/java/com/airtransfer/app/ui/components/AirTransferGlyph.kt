package com.airtransfer.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airtransfer.app.ui.theme.StitchAccentCyan

@Composable
fun AirTransferGlyph(
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)

        // Outer circle (stroke 2px equivalent, 25% opacity)
        drawCircle(
            color = StitchAccentCyan.copy(alpha = 0.25f),
            radius = w * 0.46f,
            center = center,
            style = Stroke(width = w * 0.04f)
        )

        // Concentric dashed inner circle
        drawCircle(
            color = StitchAccentCyan,
            radius = w * 0.28f,
            center = center,
            style = Stroke(
                width = w * 0.05f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.08f, w * 0.12f), 0f)
            )
        )

        // Center crosshair lines
        val lineStroke = w * 0.06f
        // Vertical line
        drawLine(
            color = Color(0xFFF8FAFC),
            start = Offset(center.x, h * 0.32f),
            end = Offset(center.x, h * 0.68f),
            strokeWidth = lineStroke,
            cap = StrokeCap.Round
        )
        // Horizontal line
        drawLine(
            color = Color(0xFFF8FAFC),
            start = Offset(w * 0.32f, center.y),
            end = Offset(w * 0.68f, center.y),
            strokeWidth = lineStroke,
            cap = StrokeCap.Round
        )

        // Center solid core
        drawCircle(
            color = StitchAccentCyan,
            radius = w * 0.06f,
            center = center
        )
    }
}
