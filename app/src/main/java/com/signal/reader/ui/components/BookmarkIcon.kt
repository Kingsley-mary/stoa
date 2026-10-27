package com.signal.reader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun BookmarkIcon(
    isFilled: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.25f, size.height * 0.125f)
            lineTo(size.width * 0.75f, size.height * 0.125f)
            lineTo(size.width * 0.75f, size.height * 0.875f)
            lineTo(size.width * 0.5f, size.height * 0.7f)
            lineTo(size.width * 0.25f, size.height * 0.875f)
            close()
        }
        if (isFilled) {
            drawPath(path = path, color = color)
        } else {
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = 1.6f * density,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
