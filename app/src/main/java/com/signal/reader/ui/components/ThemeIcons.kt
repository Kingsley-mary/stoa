package com.signal.reader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun MoonIcon(
    color: Color,
    modifier: Modifier = Modifier.size(14.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Path matching: M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z scaled to w, h
        val scaleX = w / 24f
        val scaleY = h / 24f

        val path = Path().apply {
            moveTo(20f * scaleX, 14.5f * scaleY)
            // Crescent curve
            cubicTo(19f * scaleX, 19f * scaleY, 14f * scaleX, 22.5f * scaleY, 9.5f * scaleX, 22.5f * scaleY)
            cubicTo(4.5f * scaleX, 22.5f * scaleY, 1.5f * scaleX, 18.5f * scaleY, 1.5f * scaleX, 13.5f * scaleY)
            cubicTo(1.5f * scaleX, 8.5f * scaleY, 5f * scaleX, 4.5f * scaleY, 9.5f * scaleX, 4f * scaleY)
            cubicTo(8.5f * scaleX, 7.5f * scaleY, 9f * scaleX, 12f * scaleY, 13f * scaleX, 15f * scaleY)
            cubicTo(15f * scaleX, 16.5f * scaleY, 18f * scaleX, 16.5f * scaleY, 20f * scaleX, 14.5f * scaleY)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
fun SunIcon(
    color: Color,
    modifier: Modifier = Modifier.size(14.dp)
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.width * 0.22f
        val strokeW = 1.5.dp.toPx()

        // Center circle
        drawCircle(
            color = color,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = strokeW)
        )

        // 8 Rays
        val rayInner = r + 2.5.dp.toPx()
        val rayOuter = rayInner + 2.5.dp.toPx()

        val angles = listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0)
        for (a in angles) {
            val rad = Math.toRadians(a)
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()
            drawLine(
                color = color,
                start = Offset(cx + rayInner * cos, cy + rayInner * sin),
                end = Offset(cx + rayOuter * cos, cy + rayOuter * sin),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}
