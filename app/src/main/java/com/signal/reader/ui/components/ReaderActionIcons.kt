package com.signal.reader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Editorial speaker icon with speaker cone and sound wave arcs.
 */
@Composable
fun SpeakerIcon(
    color: Color,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier.size(16.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sx = w / 24f
        val sy = h / 24f
        val strokeW = 1.6f * sx

        // Speaker cone
        val cone = Path().apply {
            moveTo(3f * sx, 9f * sy)
            lineTo(7f * sx, 9f * sy)
            lineTo(13f * sx, 4.5f * sy)
            lineTo(13f * sx, 19.5f * sy)
            lineTo(7f * sx, 15f * sy)
            lineTo(3f * sx, 15f * sy)
            close()
        }
        drawPath(
            cone,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // First sound arc
        val arc1 = Path().apply {
            moveTo(16.5f * sx, 8.5f * sy)
            cubicTo(18f * sx, 10f * sy, 18f * sx, 14f * sy, 16.5f * sx, 15.5f * sy)
        }
        drawPath(
            arc1,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Outer sound arc (if playing or standard)
        if (isPlaying) {
            val arc2 = Path().apply {
                moveTo(19.5f * sx, 5.5f * sy)
                cubicTo(22.5f * sx, 8.5f * sy, 22.5f * sx, 15.5f * sy, 19.5f * sx, 18.5f * sy)
            }
            drawPath(
                arc2,
                color = color,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Modernist share icon: 3 interconnected nodes.
 */
@Composable
fun ShareIcon(
    color: Color,
    modifier: Modifier = Modifier.size(16.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sx = w / 24f
        val sy = h / 24f
        val strokeW = 1.6f * sx

        val p1 = Offset(18f * sx, 5.5f * sy)   // top right
        val p2 = Offset(6f * sx, 12f * sy)     // left center
        val p3 = Offset(18f * sx, 18.5f * sy)  // bottom right
        val r = 2.8f * sx

        // Connecting lines
        drawLine(
            color = color,
            start = p2,
            end = p1,
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = p2,
            end = p3,
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )

        // Node circles
        drawCircle(color = color, radius = r, center = p1, style = Stroke(width = strokeW))
        drawCircle(color = color, radius = r, center = p2, style = Stroke(width = strokeW))
        drawCircle(color = color, radius = r, center = p3, style = Stroke(width = strokeW))
    }
}

/**
 * Vector Play icon
 */
@Composable
fun PlayIcon(
    color: Color,
    modifier: Modifier = Modifier.size(14.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.15f)
            lineTo(w * 0.85f, h * 0.5f)
            lineTo(w * 0.2f, h * 0.85f)
            close()
        }
        drawPath(path, color = color)
    }
}

/**
 * Vector Pause icon
 */
@Composable
fun PauseIcon(
    color: Color,
    modifier: Modifier = Modifier.size(14.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barW = w * 0.26f
        val gap = w * 0.18f
        val top = h * 0.15f
        val bottom = h * 0.85f

        drawRect(
            color = color,
            topLeft = Offset((w - (barW * 2 + gap)) / 2f, top),
            size = androidx.compose.ui.geometry.Size(barW, bottom - top)
        )
        drawRect(
            color = color,
            topLeft = Offset((w - (barW * 2 + gap)) / 2f + barW + gap, top),
            size = androidx.compose.ui.geometry.Size(barW, bottom - top)
        )
    }
}
