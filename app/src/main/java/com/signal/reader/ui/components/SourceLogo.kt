package com.signal.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.signal.reader.ui.theme.SignalTheme

/**
 * High-resolution publication logo with high-contrast editorial monogram fallback.
 * Automatically resolves favicons via Google Favicon Service with 128px resolution.
 */
@Composable
fun SourceLogo(
    siteUrl: String,
    sourceName: String,
    size: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    fontSize: TextUnit = 13.sp,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val context = LocalContext.current

    val domain = remember(siteUrl) {
        siteUrl
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore(":")
            .lowercase()
    }

    val faviconUrl = remember(domain) {
        if (domain.isNotBlank()) {
            "https://www.google.com/s2/favicons?domain=$domain&sz=128"
        } else null
    }

    var isLoaded by remember(faviconUrl) { mutableStateOf(false) }
    var hasError by remember(faviconUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (isLoaded && !hasError) Color.White else colors.ink),
        contentAlignment = Alignment.Center
    ) {
        // High-contrast monogram fallback (visible while loading or if network fails)
        if (!isLoaded || hasError) {
            Text(
                text = getSourceInitials(sourceName),
                color = colors.bg,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSize
            )
        }

        if (!faviconUrl.isNullOrBlank() && !hasError) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(faviconUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "$sourceName logo",
                modifier = Modifier
                    .size(size * 0.72f)
                    .clip(RoundedCornerShape(size * 0.18f)),
                contentScale = ContentScale.Fit,
                onSuccess = { isLoaded = true },
                onError = { hasError = true }
            )
        }
    }
}
