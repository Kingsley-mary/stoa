package com.signal.reader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun BellIcon(
    color: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.size(18.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sx = w / 24f
        val sy = h / 24f
        val strokeW = 1.6f * sx

        // Bell dome and body
        val bell = Path().apply {
            moveTo(12f * sx, 3f * sy)
            // Top small handle/loop
            cubicTo(11.2f * sx, 3f * sy, 10.5f * sx, 3.6f * sy, 10.5f * sx, 4.4f * sy)
            lineTo(13.5f * sx, 4.4f * sy)
            cubicTo(13.5f * sx, 3.6f * sy, 12.8f * sx, 3f * sy, 12f * sx, 3f * sy)
            // Bell dome
            moveTo(12f * sx, 5f * sy)
            cubicTo(8f * sx, 5f * sy, 6f * sx, 8f * sy, 6f * sx, 13f * sy)
            lineTo(5f * sx, 16.5f * sy)
            lineTo(19f * sx, 16.5f * sy)
            lineTo(18f * sx, 13f * sy)
            cubicTo(18f * sx, 8f * sy, 16f * sx, 5f * sy, 12f * sx, 5f * sy)
        }

        drawPath(
            bell,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Clapper underneath
        val clapper = Path().apply {
            moveTo(10f * sx, 17.5f * sy)
            cubicTo(10f * sx, 19f * sy, 11f * sx, 20f * sy, 12f * sx, 20f * sy)
            cubicTo(13f * sx, 20f * sy, 14f * sx, 19f * sy, 14f * sx, 17.5f * sy)
        }

        drawPath(
            clapper,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Diagonal slash if muted/disabled
        if (!enabled) {
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(4f * sx, 4f * sy),
                end = androidx.compose.ui.geometry.Offset(20f * sx, 20f * sy),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}
