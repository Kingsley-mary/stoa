package com.signal.reader.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors

    val logoScale = remember { Animatable(0.92f) }
    val contentAlpha = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Fade in glow and content
        glowAlpha.animateTo(0.45f, tween(600, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        logoScale.animateTo(1.0f, SignalMotion.PressSpringSpec)
        delay(850)
        // Smooth exit
        contentAlpha.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        glowAlpha.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF10100F)),
        contentAlignment = Alignment.Center
    ) {
        // Ambient Gradient Glow Orb
        Box(
            modifier = Modifier
                .size(340.dp)
                .alpha(glowAlpha.value)
                .blur(64.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFD97706).copy(alpha = 0.5f),
                            Color(0xFFB45309).copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Center Content: Badge, Brand, Subtitle
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(contentAlpha.value)
                .scale(logoScale.value)
        ) {
            // Signal Monogram Icon Badge (76x76dp)
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF181816))
                    .border(1.dp, Color(0xFF2E2E2A), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Editorial Geometric "S"
                Canvas(modifier = Modifier.size(36.dp)) {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        // Clean geometric "S" matching Signal's icon
                        moveTo(w * 0.15f, h * 0.15f)
                        lineTo(w * 0.85f, h * 0.15f)
                        lineTo(w * 0.85f, h * 0.35f)
                        lineTo(w * 0.38f, h * 0.35f)
                        lineTo(w * 0.38f, h * 0.50f)
                        lineTo(w * 0.85f, h * 0.50f)
                        lineTo(w * 0.85f, h * 0.85f)
                        lineTo(w * 0.15f, h * 0.85f)
                        lineTo(w * 0.15f, h * 0.65f)
                        lineTo(w * 0.62f, h * 0.65f)
                        lineTo(w * 0.62f, h * 0.50f)
                        lineTo(w * 0.15f, h * 0.50f)
                        close()
                    }
                    drawPath(path, color = Color(0xFFEEEAE1), style = Fill)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Brand Wordmark
            Text(
                text = "SIGNAL",
                style = SignalTheme.typography.wordmark,
                color = Color(0xFFEEEAE1),
                fontSize = 24.sp,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Personal RSS & Editorial Reader",
                color = Color(0xFF8E8B82),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}
