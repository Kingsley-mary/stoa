package com.signal.reader.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.content.FileProvider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
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

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = rememberSignalHaptics()
    var isSavingImage by remember { mutableStateOf(false) }
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

                    // Action pills: Save, Share, Dismiss "✕"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val activeImageUrl = images.getOrNull(pagerState.currentPage)?.url.orEmpty()

                        // Save to Photos
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable(enabled = !isSavingImage && activeImageUrl.isNotBlank()) {
                                    haptics.tick()
                                    isSavingImage = true
                                    coroutineScope.launch {
                                        val ok = saveImageToGallery(context, activeImageUrl)
                                        isSavingImage = false
                                        Toast.makeText(
                                            context,
                                            if (ok) "Saved to Photos" else "Could not save image",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                .padding(horizontal = 13.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSavingImage) "…" else "Save",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Share image
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable(enabled = activeImageUrl.isNotBlank()) {
                                    haptics.tick()
                                    coroutineScope.launch {
                                        val ok = shareImageFromUrl(context, activeImageUrl)
                                        if (!ok) {
                                            Toast.makeText(context, "Could not share image", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 13.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Share",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
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

private suspend fun fetchImageBytes(imageUrl: String): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
    try {
        val conn = URL(imageUrl).openConnection().apply {
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
            )
            connectTimeout = 12_000
            readTimeout = 15_000
        }
        val contentType = conn.contentType?.substringBefore(";")?.trim()?.lowercase() ?: "image/jpeg"
        val bytes = conn.getInputStream().use { it.readBytes() }
        if (bytes.isEmpty()) null else Pair(bytes, contentType)
    } catch (_: Exception) {
        null
    }
}

private suspend fun saveImageToGallery(context: Context, imageUrl: String): Boolean = withContext(Dispatchers.IO) {
    val fetched = fetchImageBytes(imageUrl) ?: return@withContext false
    val (bytes, mime) = fetched
    val ext = when {
        mime.contains("png") -> "png"
        mime.contains("webp") -> "webp"
        mime.contains("gif") -> "gif"
        else -> "jpg"
    }
    val fileName = "stoa_${System.currentTimeMillis()}.$ext"
    try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Stoa")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        true
    } catch (_: Exception) {
        false
    }
}

private suspend fun shareImageFromUrl(context: Context, imageUrl: String): Boolean = withContext(Dispatchers.IO) {
    val fetched = fetchImageBytes(imageUrl) ?: return@withContext false
    val (bytes, mime) = fetched
    val ext = when {
        mime.contains("png") -> "png"
        mime.contains("webp") -> "webp"
        mime.contains("gif") -> "gif"
        else -> "jpg"
    }
    try {
        val shareDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = File(shareDir, "stoa_share_${System.currentTimeMillis()}.$ext")
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(intent, "Share image"))
        }
        true
    } catch (_: Exception) {
        false
    }
}
