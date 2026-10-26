package com.signal.reader.ui.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalSignalColors = compositionLocalOf { SignalLightColors }
val LocalReducedMotion = compositionLocalOf { false }

object SignalTheme {
    val colors: SignalColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSignalColors.current

    val typography: SignalTypography
        get() = SignalTypography

    val reducedMotion: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalReducedMotion.current
}

@Composable
fun SignalTheme(
    themePreference: String = "auto", // "auto", "light", "dark"
    content: @Composable () -> Unit
) {
    val isDark = when (themePreference) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colors = if (isDark) SignalDarkColors else SignalLightColors

    val context = LocalContext.current
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colors.bg.toArgb()
            window.navigationBarColor = colors.bg.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    // Check system animator duration scale (Reduced motion setting)
    val animatorScale = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
    } catch (_: Exception) {
        1.0f
    }
    val isReducedMotion = animatorScale == 0.0f

    CompositionLocalProvider(
        LocalSignalColors provides colors,
        LocalReducedMotion provides isReducedMotion
    ) {
        MaterialTheme(
            content = content
        )
    }
}
