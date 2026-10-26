package com.signal.reader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.data.local.entity.SourceEntity
import com.signal.reader.ui.theme.SignalTheme

@Composable
fun SourceChips(
    sources: List<SourceEntity>,
    selectedSourceId: Long?, // null means "All"
    onSelectSource: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "All" chip
        ChipItem(
            label = "All",
            isSelected = selectedSourceId == null,
            onClick = { onSelectSource(null) }
        )

        // Sources chips
        sources.forEach { source ->
            ChipItem(
                label = source.name,
                isSelected = selectedSourceId == source.id,
                onClick = { onSelectSource(source.id) }
            )
        }
    }
}

@Composable
private fun ChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = SignalTheme.colors

    val bgAnim by animateColorAsState(
        targetValue = if (isSelected) colors.ink else colors.bg,
        animationSpec = if (SignalTheme.reducedMotion) tween(0) else tween(300),
        label = "chipBg"
    )
    val textAnim by animateColorAsState(
        targetValue = if (isSelected) colors.bg else colors.ink,
        animationSpec = if (SignalTheme.reducedMotion) tween(0) else tween(300),
        label = "chipText"
    )

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgAnim)
            .border(width = 1.dp, color = colors.ink, shape = CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textAnim,
            style = SignalTheme.typography.smallLabel,
            fontSize = 13.sp
        )
    }
}
