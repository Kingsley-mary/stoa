package com.signal.reader.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

data class ArticleImage(
    val url: String,
    val caption: String? = null,
    val credit: String? = null
)

/**
 * Editorial Fullscreen Lightbox Gallery:
 * Multi-image swipeable gallery with HorizontalPager, gesture-driven pinch-to-zoom,
 * double-tap zoom, caption/credit overlay, backdrop tap-to-dismiss, and ✕ button.
 */
@Composable
fun ImageViewerDialog(
    images: List<ArticleImage>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) return

    val haptics = rememberSignalHaptics()
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, images.size - 1),
        pageCount = { images.size }
    )

    var showControls by remember { mutableStateOf(true) }
    var isZoomedIn by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        isZoomedIn = false
    }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
        ) {
            // Horizontal Pager for swiping between article images
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isZoomedIn,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val articleImg = images[page]
                ZoomableImageItem(
                    image = articleImg,
                    onTapBackdrop = {
                        // Tapping background dismisses dialog
                        haptics.tick()
                        onDismiss()
                    },
                    onToggleControls = {
                        showControls = !showControls
                    },
                    onZoomChanged = { zoomed ->
                        isZoomedIn = zoomed
                    }
                )
            }

            // Top Control Bar: Page Counter + ✕ Dismiss Button
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Counter pill (e.g. 1 / 4) if multiple images
                    if (images.size > 1) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${images.size}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(38.dp))
                    }

                    // Dismiss "✕" Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptics.tick()
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom Caption & Credit Bar
            val currentImg = images.getOrNull(pagerState.currentPage)
            val hasCaption = !currentImg?.caption.isNullOrBlank()
            val hasCredit = !currentImg?.credit.isNullOrBlank()

            if (hasCaption || hasCredit) {
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (hasCaption) {
                                Text(
                                    text = currentImg!!.caption!!,
                                    color = Color(0xFFEEEAE1),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (hasCredit) {
                                Text(
                                    text = currentImg!!.credit!!,
                                    color = Color(0xFF9C978B),
                                    fontSize = 11.sp,
                                    fontStyle = FontStyle.Italic,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImageItem(
    image: ArticleImage,
    onTapBackdrop: () -> Unit,
    onToggleControls: () -> Unit,
    onZoomChanged: (Boolean) -> Unit = {}
) {
    val haptics = rememberSignalHaptics()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    var scale by remember(image.url) { mutableFloatStateOf(1f) }
    var offset by remember(image.url) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1.05f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(image.url) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        haptics.tick()
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            val centerX = screenWidthPx / 2f
                            val centerY = screenHeightPx / 2f
                            offset = Offset(
                                x = (centerX - tapOffset.x) * 1.5f,
                                y = (centerY - tapOffset.y) * 1.5f
                            )
                        }
                    },
                    onTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            onTapBackdrop()
                        }
                    }
                )
            }
            .pointerInput(image.url) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val pointerCount = event.changes.size
                            // Only capture gestures when pinching with 2+ fingers or already zoomed in
                            if (pointerCount >= 2 || scale > 1.05f) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                    scale = newScale

                                    if (newScale <= 1.02f) {
                                        offset = Offset.Zero
                                    } else {
                                        val maxOffsetX = (screenWidthPx * (newScale - 1f)) / 2f
                                        val maxOffsetY = (screenHeightPx * (newScale - 1f)) / 2f

                                        val newX = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                                        val newY = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                        offset = Offset(newX, newY)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = image.url,
            contentDescription = image.caption ?: "Article image",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
    }
}
