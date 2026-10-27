package com.signal.reader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.data.local.entity.SourceEntity
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale
import kotlin.math.roundToInt

@Composable
fun SourceChips(
    sources: List<SourceEntity>,
    selectedSourceId: Long?, // null means "All"
    onSelectSource: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    // Store measured (x, width) for each chip key (-1 for "All", source.id for sources)
    val chipBounds = remember { mutableStateMapOf<Long, Pair<Float, Float>>() }

    val activeKey = selectedSourceId ?: -1L
    val activeBounds = chipBounds[activeKey] ?: Pair(0f, 0f)

    val animatedLeft by animateFloatAsState(
        targetValue = activeBounds.first,
        animationSpec = if (SignalTheme.reducedMotion) tween(0) else SignalMotion.easeTween(SignalMotion.ChipPillDuration),
        label = "chipPillLeft"
    )
    val animatedWidth by animateFloatAsState(
        targetValue = activeBounds.second,
        animationSpec = if (SignalTheme.reducedMotion) tween(0) else SignalMotion.easeTween(SignalMotion.ChipPillDuration),
        label = "chipPillWidth"
    )

    // Auto-scroll selected chip into view
    LaunchedEffect(selectedSourceId, activeBounds) {
        if (activeBounds.second > 0f) {
            val targetScroll = (activeBounds.first - 100f).coerceAtLeast(0f)
            scrollState.animateScrollTo(targetScroll.roundToInt())
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Sliding indicator pill (.ind)
        if (animatedWidth > 0f) {
            Box(
                modifier = Modifier
                    .offset(x = with(density) { animatedLeft.toDp() })
                    .width(with(density) { animatedWidth.toDp() })
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(colors.ink)
            )
        }

        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "All" chip
            ChipItem(
                label = "All",
                isSelected = selectedSourceId == null,
                onClick = { onSelectSource(null) },
                onPositioned = { x, w ->
                    chipBounds[-1L] = Pair(x, w)
                }
            )

            // Sources chips
            sources.forEach { source ->
                ChipItem(
                    label = source.name,
                    isSelected = selectedSourceId == source.id,
                    onClick = { onSelectSource(source.id) },
                    onPositioned = { x, w ->
                        chipBounds[source.id] = Pair(x, w)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPositioned: (Float, Float) -> Unit
) {
    val colors = SignalTheme.colors

    Box(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                val x = coordinates.positionInParent().x
                val w = coordinates.size.width.toFloat()
                onPositioned(x, w)
            }
            .clip(CircleShape)
            .border(width = 1.dp, color = colors.ink, shape = CircleShape)
            .signalPressScale(0.96f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) colors.bg else colors.ink,
            style = SignalTheme.typography.smallLabel,
            fontSize = 13.sp
        )
    }
}
