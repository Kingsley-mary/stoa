package com.signal.reader.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standardized editorial spacing scale for Stoa.
 * Anchored around the 20.dp horizontal screen gutter.
 */
@Immutable
data class SignalSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 6.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val screenGutter: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp
)

/**
 * Standardized corner radii and shapes across cards, pills, dialogs, and hero media.
 */
@Immutable
data class SignalShapes(
    val pill: Shape = CircleShape,
    val input: RoundedCornerShape = RoundedCornerShape(12.dp),
    val cardSm: RoundedCornerShape = RoundedCornerShape(14.dp),
    val card: RoundedCornerShape = RoundedCornerShape(18.dp),
    val dialog: RoundedCornerShape = RoundedCornerShape(22.dp),
    val hero: RoundedCornerShape = RoundedCornerShape(26.dp)
)

/**
 * Standardized component dimensions, touch targets, and hairline strokes.
 */
@Immutable
data class SignalSizing(
    val dividerThickness: Dp = 1.dp,
    val borderWidth: Dp = 1.dp,
    val iconButtonSm: Dp = 28.dp,
    val iconButton: Dp = 36.dp,
    val iconButtonLg: Dp = 38.dp,
    val sourceLogoRow: Dp = 48.dp,
    val sourceLogoMasthead: Dp = 56.dp,
    val pillPaddingH: Dp = 16.dp,
    val pillPaddingV: Dp = 10.dp
)

val DefaultSignalSpacing = SignalSpacing()
val DefaultSignalShapes = SignalShapes()
val DefaultSignalSizing = SignalSizing()
