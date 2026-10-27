package com.signal.reader.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import kotlin.math.roundToInt

/**
 * 40ms staggered item rise animation matching CSS .in token:
 * .in { animation: rise .35s var(--ease) backwards; animation-delay: calc(var(--i,0)*40ms) }
 * @keyframes rise { from { opacity: 0; transform: translateY(14px) } }
 */
@Composable
fun StaggeredItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (SignalTheme.reducedMotion || index > 6) {
        Box(modifier = modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val cappedIndex = index.coerceIn(0, 6)
    val delayMs = cappedIndex * SignalMotion.ContentEnterStagger

    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(14f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = SignalMotion.easeTween(SignalMotion.ContentEnterDuration, delayMillis = delayMs)
        )
    }
    LaunchedEffect(Unit) {
        offsetY.animateTo(
            targetValue = 0f,
            animationSpec = SignalMotion.easeTween(SignalMotion.ContentEnterDuration, delayMillis = delayMs)
        )
    }

    Box(
        modifier = modifier
            .offset { IntOffset(0, (offsetY.value * density.density).roundToInt()) }
            .alpha(alpha.value)
    ) {
        content()
    }
}
