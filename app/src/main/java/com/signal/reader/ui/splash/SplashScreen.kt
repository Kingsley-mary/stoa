package com.signal.reader.ui.splash

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.R
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Editorial Starter Splash Screen.
 * Displays a heavily frosted, blurred architectural background with a standalone Signal
 * brand mark (no container square or box) and wide-tracked editorial typography.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgScale = remember { Animatable(1.08f) }
    val contentAlpha = remember { Animatable(0f) }
    val contentScale = remember { Animatable(0.92f) }

    LaunchedEffect(Unit) {
        // Slow cinematic subtle drift on the blurred background
        launch {
            bgScale.animateTo(1.0f, tween(1600, easing = LinearOutSlowInEasing))
        }
        // Smooth fade and spring scale for the standalone mark and typography
        launch {
            contentAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }
        launch {
            contentScale.animateTo(1.0f, SignalMotion.PressSpringSpec)
        }

        delay(1200)

        // Smooth exit fade
        contentAlpha.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF10100F)),
        contentAlignment = Alignment.Center
    ) {
        // Blurred Architectural Editorial Background Image (pre-blurred 7KB WebP)
        val imageModifier = Modifier
            .fillMaxSize()
            .scale(bgScale.value)
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(16.dp)
                } else Modifier
            )

        Image(
            painter = painterResource(id = R.drawable.splash_bg),
            contentDescription = null,
            modifier = imageModifier,
            contentScale = ContentScale.Crop
        )

        // Frosted Scrim Overlay for deep luxury contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xB310100F),
                            Color(0x8010100F),
                            Color(0xD910100F)
                        )
                    )
                )
        )

        // Center Content: Standalone Mark (NO black square/box) + Wordmark + Tagline
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(contentAlpha.value)
                .scale(contentScale.value)
        ) {
            // Standalone Signal Mark (freely floating, no enclosing box or square)
            StandaloneSignalMark(
                size = 58.dp,
                color = Color(0xFFF6F3EB),
                modifier = Modifier.padding(bottom = 22.dp)
            )

            // Brand Wordmark
            Text(
                text = "STOA",
                style = SignalTheme.typography.wordmark,
                color = Color(0xFFF6F3EB),
                fontSize = 26.sp,
                letterSpacing = 5.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle
            Text(
                text = "Personal RSS & Editorial Reader",
                color = Color(0xFFB5B0A4),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Pure geometric Signal mark without any enclosing square, rectangle, or container.
 */
@Composable
private fun StandaloneSignalMark(
    size: Dp = 58.dp,
    color: Color = Color(0xFFF6F3EB),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Clean geometric "S" mark with balanced strokes
        val path = Path().apply {
            moveTo(w * 0.14f, h * 0.12f)
            lineTo(w * 0.86f, h * 0.12f)
            lineTo(w * 0.86f, h * 0.34f)
            lineTo(w * 0.38f, h * 0.34f)
            lineTo(w * 0.38f, h * 0.50f)
            lineTo(w * 0.86f, h * 0.50f)
            lineTo(w * 0.86f, h * 0.88f)
            lineTo(w * 0.14f, h * 0.88f)
            lineTo(w * 0.14f, h * 0.66f)
            lineTo(w * 0.62f, h * 0.66f)
            lineTo(w * 0.62f, h * 0.50f)
            lineTo(w * 0.14f, h * 0.50f)
            close()
        }

        drawPath(path, color = color, style = Fill)
    }
}
