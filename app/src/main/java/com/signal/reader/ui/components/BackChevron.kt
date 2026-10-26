package com.signal.reader.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BackChevron(
    color: Color,
    modifier: Modifier = Modifier.size(12.dp)
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = 1.5f * density
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width, 0f),
            end = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
            strokeWidth = stroke
        )
    }
}
