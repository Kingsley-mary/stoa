package com.signal.reader.ui.theme

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

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

object SignalTactile {
    // Standard tactile scale design tokens
    const val ButtonPressScale = 0.96f
    const val SmallButtonPressScale = 0.94f
    const val CardPressScale = 0.985f
    const val IconPressScale = 0.92f

    val PressSpring = spring<Float>(
        dampingRatio = 0.62f,
        stiffness = 550f
    )
}

/**
 * Consistent tactile press animation with spring physics and subtle haptic tick.
 */
fun Modifier.signalTactileClick(
    targetScale: Float = SignalTactile.ButtonPressScale,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isPressed) {
        if (isPressed && enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) targetScale else 1.0f,
        animationSpec = SignalTactile.PressSpring,
        label = "tactileScale"
    )

    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Design token for Stoa's tactile pill buttons.
 * Guarantees correct modifier ordering: scales whole element on press with spring physics,
 * renders background shape, and applies single crisp 1dp hairline border on top.
 * Prevents double-hairline artifacts and hairline disappearing during press.
 */
fun Modifier.signalPillButton(
    backgroundColor: Color,
    borderColor: Color,
    targetScale: Float = SignalTactile.ButtonPressScale,
    shape: Shape = CircleShape,
    borderWidth: Dp = 1.dp,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = this
    .signalTactileClick(
        targetScale = targetScale,
        enabled = enabled,
        onClick = onClick
    )
    .background(backgroundColor, shape)
    .border(borderWidth, borderColor, shape)

/**
 * Scale on press effect matching mockup with tactile haptic tick.
 */
fun Modifier.signalPressScale(
    targetScale: Float = SignalTactile.ButtonPressScale,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isPressed) {
        if (isPressed && onClick != null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1.0f,
        animationSpec = SignalTactile.PressSpring,
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

/**
 * Shared element image transition matching mockup:
 * shared element (card to Reader) .5s ease-out, crop animates from 4:5 to 1:1
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.signalSharedImage(
    key: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    shape: Shape
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) return this
    with(sharedTransitionScope) {
        return this@signalSharedImage.sharedElement(
            state = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = { _, _ ->
                tween(
                    durationMillis = SignalMotion.SharedElementDuration,
                    easing = SignalMotion.EaseOutQuart
                )
            },
            clipInOverlayDuringTransition = OverlayClip(shape)
        )
    }
}
