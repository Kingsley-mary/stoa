package com.signal.reader.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

val LightBg = Color(0xFFEEEAE1)
val LightInk = Color(0xFF141413)
val LightMute = Color(0xFF6B665C)
val LightCard = Color(0xFFD6CFBF)
val LightLine = Color(0x29141413) // 16% ink

val DarkBg = Color(0xFF10100F)
val DarkInk = Color(0xFFF1EDE4)
val DarkMute = Color(0xFF9C978B)
val DarkCard = Color(0xFF242320)
val DarkLine = Color(0x2EF1EDE4) // 18% ink

val SignalAccent = Color(0xFFE4472B)

@Immutable
data class SignalColors(
    val bg: Color,
    val ink: Color,
    val mute: Color,
    val card: Color,
    val line: Color,
    val accent: Color = SignalAccent
)

val SignalLightColors = SignalColors(
    bg = LightBg,
    ink = LightInk,
    mute = LightMute,
    card = LightCard,
    line = LightLine,
    accent = SignalAccent
)

val SignalDarkColors = SignalColors(
    bg = DarkBg,
    ink = DarkInk,
    mute = DarkMute,
    card = DarkCard,
    line = DarkLine,
    accent = SignalAccent
)
