package com.signal.reader.ui.theme

import android.app.Activity
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
val LocalSignalSpacing = compositionLocalOf { DefaultSignalSpacing }
val LocalSignalShapes = compositionLocalOf { DefaultSignalShapes }
val LocalSignalSizing = compositionLocalOf { DefaultSignalSizing }
val LocalReducedMotion = compositionLocalOf { false }

object SignalTheme {
    val colors: SignalColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSignalColors.current

    val typography: SignalTypography
        get() = SignalTypography

    val spacing: SignalSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSignalSpacing.current

    val shapes: SignalShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalSignalShapes.current

    val sizing: SignalSizing
        @Composable
        @ReadOnlyComposable
        get() = LocalSignalSizing.current

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
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            window.decorView.setBackgroundColor(colors.bg.toArgb())
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

    val materialColorScheme = if (isDark) {
        darkColorScheme(
            background = colors.bg,
            surface = colors.bg,
            onBackground = colors.ink,
            onSurface = colors.ink
        )
    } else {
        lightColorScheme(
            background = colors.bg,
            surface = colors.bg,
            onBackground = colors.ink,
            onSurface = colors.ink
        )
    }

    CompositionLocalProvider(
        LocalSignalColors provides colors,
        LocalSignalSpacing provides DefaultSignalSpacing,
        LocalSignalShapes provides DefaultSignalShapes,
        LocalSignalSizing provides DefaultSignalSizing,
        LocalReducedMotion provides isReducedMotion
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}
