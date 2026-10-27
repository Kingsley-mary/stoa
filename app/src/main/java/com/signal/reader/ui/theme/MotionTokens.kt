package com.signal.reader.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState

object SignalMotion {
    // Exact Bezier Curves from mockups:
    // --ease: cubic-bezier(.2,.8,.2,1)
    val EaseOutQuart: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1.0f)

    // --spring: cubic-bezier(.34,1.56,.64,1)
    val SpringEasing: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)

    // Motion durations in ms
    const val PressDuration = 180
    const val ContentEnterDuration = 350
    const val ContentEnterStagger = 40
    const val FeedSwapOutDuration = 130
    const val FeedSwapInDuration = 350
    const val ChipSlideDuration = 300
    const val TabSlideDuration = 300
    const val TabBarDuration = 300
    const val ChipPillDuration = 300
    const val HeroSlideDuration = 450
    const val SharedElementDuration = 500
    const val SwipeSpringDuration = 350
    const val WordStaggerDuration = 35
    const val PopDuration = 400

    // Spring specs for press feedback and pops
    val SpringSpec = spring<Float>(
        dampingRatio = 0.62f,
        stiffness = 550f
    )

    val IntOffsetSpringSpec = spring<IntOffset>(
        dampingRatio = 0.62f,
        stiffness = 550f
    )

    val PressSpringSpec = spring<Float>(
        dampingRatio = 0.62f,
        stiffness = 550f
    )

    val PopSpringSpec = spring<Float>(
        dampingRatio = 0.5f,
        stiffness = 450f
    )

    val SwipeSpringSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> easeTween(durationMillis: Int, delayMillis: Int = 0) = tween<T>(
        durationMillis = durationMillis,
        delayMillis = delayMillis,
        easing = EaseOutQuart
    )
}

/**
 * Scale on press effect matching mockup:
 * button:active -> scale(.96)
 * thumb:active -> scale(.94)
 * card:active -> scale(.97)
 */
fun Modifier.signalPressScale(
    targetScale: Float = 0.96f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1.0f,
        animationSpec = SignalMotion.PressSpringSpec,
        label = "pressScale"
    )

    this
        .scale(scale)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else Modifier
        )
}
