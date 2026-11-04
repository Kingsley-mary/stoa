package com.signal.reader.ui.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.signal.reader.data.feed.ImageResolver
import com.signal.reader.data.feed.ReadabilityExtractor
import com.signal.reader.data.local.SettingsManager
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.ArticleImage
import com.signal.reader.ui.components.ArticleRow
import com.signal.reader.ui.components.BackChevron
import com.signal.reader.ui.components.BookmarkIcon
import com.signal.reader.ui.components.ImageViewerDialog
import com.signal.reader.ui.components.MoonIcon
import com.signal.reader.ui.components.PauseIcon
import com.signal.reader.ui.components.PlayIcon
import com.signal.reader.ui.components.QuoteCardGenerator
import com.signal.reader.ui.components.ShareIcon
import com.signal.reader.ui.components.ShareStorySheet
import com.signal.reader.ui.components.SpeakerIcon
import com.signal.reader.ui.components.StaggeredItem
import com.signal.reader.ui.components.SunIcon
import com.signal.reader.ui.components.TimeUtils
import com.signal.reader.ui.components.rememberSignalHaptics
import com.signal.reader.ui.reader.ArticleAudioPlayer
import com.signal.reader.ui.reader.AudioPlaybackStatus
import com.signal.reader.ui.theme.SignalAccent
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTactile
import com.signal.reader.ui.theme.signalTactileClick
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale
import com.signal.reader.ui.theme.signalSharedImage
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ReaderScreen(
    articleId: Long,
    repository: FeedRepository,
    settingsManager: SettingsManager,
    initialShareQuote: String? = null,
    onQuoteShared: (() -> Unit)? = null,
    onBack: () -> Unit,
    onOpenSourcePage: (Long) -> Unit,
    onOpenArticle: ((Long) -> Unit)? = null,
    onOpenWebUrl: ((url: String, title: String?) -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val colors = SignalTheme.colors
    val haptics = rememberSignalHaptics()
    val coroutineScope = rememberCoroutineScope()
    val settings by settingsManager.settingsFlow.collectAsState()

    val article by repository.getArticleByIdFlow(articleId).collectAsState(initial = null)
    val homeArticles by repository.getHomeStreamFlow().collectAsState(initial = emptyList())
    val relatedArticles = remember(homeArticles, articleId) {
        homeArticles.filter { it.id != articleId && !it.read }.take(3)
    }

    // Mark as read immediately on open
    LaunchedEffect(articleId) {
        repository.markArticleRead(articleId)
    }

    val listState = rememberLazyListState()

    // Smooth reading progress calculation across all items and scroll offset
    val readingProgress by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo
            val total = layoutInfo.totalItemsCount
            if (visible.isEmpty() || total <= 1) {
                0f
            } else {
                val lastItem = visible.last()
                if (lastItem.index == total - 1) {
                    val viewportBottom = layoutInfo.viewportEndOffset
                    val itemBottom = lastItem.offset + lastItem.size
                    if (itemBottom <= viewportBottom) 1f
                    else {
                        val progress = 1f - (itemBottom - viewportBottom).toFloat() / lastItem.size.toFloat()
                        progress.coerceIn(0.85f, 1f)
                    }
                } else {
                    val firstItem = visible.first()
                    val itemFraction = if (firstItem.size > 0) {
                        listState.firstVisibleItemScrollOffset.toFloat() / firstItem.size.toFloat()
                    } else 0f
                    val currentPos = firstItem.index.toFloat() + itemFraction
                    (currentPos / (total - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
                }
            }
        }
    }

    // Font size steps: 16, 18, 20, 23
    val fontSizes = listOf(16.sp, 18.sp, 20.sp, 23.sp)
    val currentFontSizeIndex = settings.textSizeIndex.coerceIn(0, fontSizes.size - 1)
    val currentFontSize = fontSizes[currentFontSizeIndex]
    var isAaPanelOpen by remember { mutableStateOf(false) }

    // Multi-image gallery state
    var activeGalleryIndex by remember { mutableStateOf<Int?>(null) }

    // Title entrance animation (titleUp from translateY(40px), 500ms ease-out, delay 120ms)
    val titleOffsetY = remember { Animatable(40f) }
    val titleAlpha = remember { Animatable(0f) }

    LaunchedEffect(articleId) {
        titleOffsetY.animateTo(0f, SignalMotion.easeTween(SignalMotion.SharedElementDuration, delayMillis = 120))
    }
    LaunchedEffect(articleId) {
        titleAlpha.animateTo(1f, SignalMotion.easeTween(SignalMotion.SharedElementDuration, delayMillis = 120))
    }

    // Bookmark pop animation on save (.rf .bump svg { animation: pop .45s var(--spring) })
    val bookmarkPop = remember { Animatable(1.0f) }

    val currentArticle = article ?: return

    fun openWeb(url: String, title: String? = null) {
        if (url.isNotBlank()) {
            if (onOpenWebUrl != null) {
                onOpenWebUrl(url, title)
            } else {
                try {
                    val intent = CustomTabsIntent.Builder()
                        .setShowTitle(true)
                        .build()
                    intent.launchUrl(context, Uri.parse(url))
                } catch (_: Exception) {
                }
            }
        }
    }

    val isSerif = settings.fontFamily == "serif"

    var extractedHtml by remember { mutableStateOf<String?>(null) }
    var isExtracting by remember { mutableStateOf(false) }
    var showShareSheet by remember(initialShareQuote) { mutableStateOf(!initialShareQuote.isNullOrBlank()) }
    var pendingShareQuote by remember(initialShareQuote) { mutableStateOf(initialShareQuote) }

    val audioPlayer = remember { ArticleAudioPlayer(context) }
    val audioState by audioPlayer.playbackState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.destroy()
        }
    }

    val effectiveHeroImage = remember(currentArticle.imageUrl, currentArticle.fullContent, currentArticle.excerpt, extractedHtml) {
        val candidate = currentArticle.imageUrl.takeIf { !it.isNullOrBlank() && it != "none" }
        candidate ?: ImageResolver.getEffectiveCoverImage(
            null,
            extractedHtml ?: currentArticle.fullContent,
            currentArticle.excerpt
        )
    }



    val blocks = remember(currentArticle.fullContent, currentArticle.excerpt, extractedHtml, effectiveHeroImage) {
        val raw = extractedHtml ?: currentArticle.fullContent ?: currentArticle.excerpt
        parseContentBlocks(raw, heroImageUrl = effectiveHeroImage)
    }

    val paragraphTexts = remember(blocks) {
        blocks.filterIsInstance<ContentBlock.Paragraph>().map { it.text }
    }

    val isExcerpt = remember(currentArticle.isExcerptOnly, currentArticle.fullContent, currentArticle.excerpt, extractedHtml) {
        extractedHtml == null && (currentArticle.isExcerptOnly || (currentArticle.fullContent == null && (currentArticle.excerpt.length < 500 || currentArticle.excerpt.split(" ").size < 90)))
    }

    val allArticleImages = remember(currentArticle, blocks, effectiveHeroImage) {
        val list = mutableListOf<ArticleImage>()
        if (!effectiveHeroImage.isNullOrBlank()) {
            list.add(ArticleImage(url = effectiveHeroImage, caption = currentArticle.title, credit = currentArticle.sourceName))
        }
        for (b in blocks) {
            if (b is ContentBlock.Image && b.url.isNotBlank() && list.none { it.url == b.url }) {
                list.add(ArticleImage(url = b.url, caption = b.caption, credit = currentArticle.sourceName))
            }
        }
        list
    }

    val showStickyTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 1 || (listState.firstVisibleItemIndex == 1 && listState.firstVisibleItemScrollOffset > 180)
        }
    }

    val view = LocalView.current
    val customTextToolbar = remember(view) {
        CustomTextToolbar(view) { quoteText ->
            pendingShareQuote = quoteText
            showShareSheet = true
        }
    }

    CompositionLocalProvider(LocalTextToolbar provides customTextToolbar) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.bg)
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sticky top header bar with BackChevron and reading progress bar matching design/reader-mockup.html
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .statusBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .signalPressScale(0.94f) { onBack() },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BackChevron(color = colors.ink)
                        }

                        AnimatedVisibility(
                            visible = showStickyTitle,
                            enter = fadeIn(tween(220)) + slideInHorizontally { 30 },
                            exit = fadeOut(tween(180)) + slideOutHorizontally { -20 },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = currentArticle.title,
                                color = colors.ink,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }

                        if (!showStickyTitle) {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        // Right-hand actions: Listen (Audio TTS) & Quote Card Share
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Listen / Audio button
                            val isAudioActive = audioState.status != AudioPlaybackStatus.IDLE
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isAudioActive) colors.accent else Color.Transparent)
                                    .signalPressScale(0.92f) {
                                        haptics.tick()
                                        val textToSpeak = if (!extractedHtml.isNullOrBlank()) {
                                            extractedHtml!!
                                        } else if (!currentArticle.fullContent.isNullOrBlank()) {
                                            currentArticle.fullContent!!
                                        } else {
                                            currentArticle.excerpt.ifBlank { currentArticle.title }
                                        }
                                        audioPlayer.togglePlayPause(
                                            currentArticle.title,
                                            textToSpeak
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                SpeakerIcon(
                                    color = if (isAudioActive) Color.White else colors.ink,
                                    isPlaying = audioState.status == AudioPlaybackStatus.PLAYING,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Share button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .signalPressScale(0.92f) {
                                        haptics.tick()
                                        showShareSheet = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                ShareIcon(
                                    color = colors.ink,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                    // Bottom border line with reading progress indicator (#prog)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(colors.line)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(readingProgress.coerceIn(0f, 1f))
                                .height(2.dp)
                                .background(colors.ink)
                        )
                    }
                }
            }

            // Article Content
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp)
            ) {
                item(key = "spacer_top") {
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Square 1:1 image with 26dp radius and Category Tag pill (.r-pic .tag)
                item(key = "hero_pic") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .signalSharedImage(
                                key = "article_image_${currentArticle.id}",
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                shape = RoundedCornerShape(26.dp)
                            )
                            .clip(RoundedCornerShape(26.dp))
                            .background(colors.card)
                            .then(
                                if (!effectiveHeroImage.isNullOrBlank()) {
                                    Modifier.clickable {
                                        activeGalleryIndex = 0
                                    }
                                } else Modifier
                            )
                    ) {
                        if (!effectiveHeroImage.isNullOrBlank()) {
                            AsyncImage(
                                model = effectiveHeroImage,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Category tag pill (.tag)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(14.dp)
                                .clip(CircleShape)
                                .background(colors.bg)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = currentArticle.sourceName,
                                color = colors.ink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Title with titleUp entrance animation (#rtitle)
                item(key = "title") {
                    SelectionContainer {
                        Text(
                            text = currentArticle.title.uppercase(),
                            style = SignalTheme.typography.readerTitle,
                            color = colors.ink,
                            modifier = Modifier
                                .padding(top = 22.dp, bottom = 22.dp)
                                .offset { IntOffset(0, (titleOffsetY.value * density).roundToInt()) }
                                .alpha(titleAlpha.value)
                        )
                    }
                }

                // Facts DL block (#rdl) with staggered entrance
                item(key = "facts") {
                    FactsBlock(
                        article = currentArticle,
                        onSourceClick = { onOpenSourcePage(currentArticle.sourceId) }
                    )
                }

                item(key = "spacer_body") {
                    Spacer(modifier = Modifier.height(26.dp))
                }

                // Sequential content blocks (paragraphs, inline images, headings, quotes, lists)
                val blocksToRender = if (isExcerpt) blocks.take(3) else blocks
                itemsIndexed(blocksToRender, key = { idx, _ -> "content_block_$idx" }) { idx, block ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        SelectionContainer {
                            RenderContentBlock(
                                block = block,
                                fontSize = currentFontSize,
                                isSerif = isSerif,
                                articleUrl = currentArticle.url,
                                onImageClick = { url, _ ->
                                    val clickedIdx = allArticleImages.indexOfFirst { it.url == url }.takeIf { it >= 0 } ?: 0
                                    activeGalleryIndex = clickedIdx
                                },
                                onLinkClick = { url ->
                                    openWeb(url, null)
                                },
                                onVideoClick = { videoUrl ->
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        openWeb(videoUrl, null)
                                    }
                                }
                            )
                        }

                        // 110dp smooth gradient overlay directly fading the end of excerpt text
                        if (isExcerpt && idx == blocksToRender.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, colors.bg)
                                        )
                                    )
                            )
                        }
                    }
                }

                // Excerpt-only CTA (#cta)
                if (isExcerpt) {
                    item(key = "excerpt_cta") {
                        ExcerptCta(
                            onReadFullClick = { openWeb(currentArticle.url, currentArticle.title) },
                            onExtractClick = {
                                coroutineScope.launch {
                                    isExtracting = true
                                    val fullHtml = ReadabilityExtractor.extractArticleHtml(currentArticle.url)
                                    if (!fullHtml.isNullOrBlank()) {
                                        extractedHtml = fullHtml
                                        try {
                                            val repo = com.signal.reader.SignalApplication.instance.repository
                                            repo.updateArticleFullContent(currentArticle.id, fullHtml, false)
                                            if (currentArticle.imageUrl.isNullOrBlank() || currentArticle.imageUrl == "none") {
                                                val doc = org.jsoup.Jsoup.parse(fullHtml)
                                                val newImg = ImageResolver.extractOpenGraphImage(doc) ?: ImageResolver.extractBestImageFromHtml(doc)
                                                if (!newImg.isNullOrBlank()) {
                                                    repo.updateArticleImage(currentArticle.id, newImg)
                                                }
                                            }
                                        } catch (_: Exception) {}
                                        haptics.pop()
                                    } else {
                                        haptics.tick()
                                        android.widget.Toast.makeText(context, "Could not extract full text. Opening site...", android.widget.Toast.LENGTH_SHORT).show()
                                        openWeb(currentArticle.url, currentArticle.title)
                                    }
                                    isExtracting = false
                                }
                            },
                            isExtracting = isExtracting
                        )
                    }
                }

                // Related articles from followed sources (#related) matching updated reader-mockup.html
                if (relatedArticles.isNotEmpty()) {
                    item(key = "related_header") {
                        Spacer(modifier = Modifier.height(36.dp))
                        Text(
                            text = "Related from your sources",
                            color = colors.mute,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    itemsIndexed(relatedArticles, key = { _, item -> "related_${item.id}" }) { _, related ->
                        ArticleRow(
                            article = related,
                            onClick = { onOpenArticle?.invoke(related.id) },
                            onToggleSave = {
                                coroutineScope.launch {
                                    repository.toggleSaved(related.id)
                                }
                            }
                        )
                    }
                }

                item(key = "spacer_bottom") {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }

            // Aa Settings Sheet (.sheet)
            AnimatedVisibility(
                visible = isAaPanelOpen,
                enter = expandVertically(
                    animationSpec = SignalMotion.easeTween(280),
                    expandFrom = Alignment.Bottom
                ) + fadeIn(SignalMotion.easeTween(200)),
                exit = shrinkVertically(
                    animationSpec = SignalMotion.easeTween(220),
                    shrinkTowards = Alignment.Bottom
                ) + fadeOut(SignalMotion.easeTween(180))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bg)
                ) {
                    HorizontalDivider(color = colors.line, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Text size",
                            color = colors.mute,
                            fontSize = 13.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PillButton(
                                text = "A−",
                                enabled = currentFontSizeIndex > 0,
                                onClick = {
                                    haptics.tick()
                                    if (currentFontSizeIndex > 0) {
                                        settingsManager.updateTextSizeIndex(currentFontSizeIndex - 1)
                                    }
                                }
                            )
                            PillButton(
                                text = "A+",
                                enabled = currentFontSizeIndex < fontSizes.size - 1,
                                onClick = {
                                    haptics.tick()
                                    if (currentFontSizeIndex < fontSizes.size - 1) {
                                        settingsManager.updateTextSizeIndex(currentFontSizeIndex + 1)
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = colors.line.copy(alpha = 0.5f), thickness = 1.dp)

                    // Display & Font Row: single button for Theme, and single button for Typeface
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Display & Font",
                            color = colors.mute,
                            fontSize = 13.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Single Theme Switcher Button
                            val isSystemDark = isSystemInDarkTheme()
                            val isDarkActive = when (settings.theme) {
                                "dark" -> true
                                "light" -> false
                                else -> isSystemDark
                            }

                            AaPillButton(
                                onClick = {
                                    haptics.tick()
                                    settingsManager.updateTheme(if (isDarkActive) "light" else "dark")
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    if (isDarkActive) {
                                        MoonIcon(color = SignalTheme.colors.ink, modifier = Modifier.size(13.dp))
                                        Text(
                                            text = "Dark",
                                            color = SignalTheme.colors.ink,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else {
                                        SunIcon(color = SignalTheme.colors.ink, modifier = Modifier.size(13.dp))
                                        Text(
                                            text = "Light",
                                            color = SignalTheme.colors.ink,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // 2. Single Typeface Switcher Button (switches between Sans and Serif)
                            AaPillButton(
                                onClick = {
                                    haptics.tick()
                                    settingsManager.updateFontFamily(if (isSerif) "sans" else "serif")
                                }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isSerif) "Aa Serif" else "Aa Sans",
                                        color = SignalTheme.colors.ink,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = if (isSerif) FontFamily.Serif else SignalTheme.typography.screenTitle.fontFamily
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom action bar (.rf): Save | Aa | Open site ↗
            HorizontalDivider(color = colors.line, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .navigationBarsPadding()
                    .height(62.dp)
                    .padding(vertical = 4.dp)
            ) {
                // Save button with pop bounce animation
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .signalPressScale(0.96f) {
                            coroutineScope.launch {
                                val willBeSaved = !currentArticle.saved
                                repository.toggleSaved(currentArticle.id)
                                if (willBeSaved) {
                                    haptics.tick()
                                    bookmarkPop.snapTo(0.6f)
                                    bookmarkPop.animateTo(1.0f, SignalMotion.PopSpringSpec)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BookmarkIcon(
                            isFilled = currentArticle.saved,
                            color = if (currentArticle.saved) colors.ink else colors.mute,
                            modifier = Modifier
                                .size(16.dp)
                                .scale(bookmarkPop.value)
                        )
                        Text(
                            text = if (currentArticle.saved) "Saved" else "Save",
                            color = if (currentArticle.saved) colors.ink else colors.mute,
                            fontWeight = if (currentArticle.saved) FontWeight.Medium else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }

                // Aa button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .signalPressScale(0.96f) {
                            haptics.tick()
                            isAaPanelOpen = !isAaPanelOpen
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aa",
                        color = if (isAaPanelOpen) colors.ink else colors.mute,
                        fontWeight = if (isAaPanelOpen) FontWeight.Medium else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }

                // Open site ↗
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .signalPressScale(0.96f) {
                            openWeb(currentArticle.url, currentArticle.title)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Open site ↗",
                        color = colors.mute,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Floating Braun Audio Player Dock (when playing/paused)
        val isDarkActive = colors.bg == com.signal.reader.ui.theme.DarkBg
        AnimatedVisibility(
            visible = audioState.status != AudioPlaybackStatus.IDLE,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 74.dp, start = 16.dp, end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(if (isDarkActive) colors.card else colors.ink)
                    .border(1.dp, colors.line, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Play/Pause button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                                .signalPressScale(0.92f) {
                                    haptics.tick()
                                    val textToSpeak = if (!extractedHtml.isNullOrBlank()) {
                                        extractedHtml!!
                                    } else if (!currentArticle.fullContent.isNullOrBlank()) {
                                        currentArticle.fullContent!!
                                    } else {
                                        currentArticle.excerpt.ifBlank { currentArticle.title }
                                    }
                                    audioPlayer.togglePlayPause(
                                        currentArticle.title,
                                        textToSpeak
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (audioState.status == AudioPlaybackStatus.PLAYING) {
                                PauseIcon(color = Color.White, modifier = Modifier.size(13.dp))
                            } else {
                                PlayIcon(color = Color.White, modifier = Modifier.size(13.dp))
                            }
                        }

                        Text(
                            text = if (audioState.status == AudioPlaybackStatus.PLAYING) "Reading aloud..." else "Paused",
                            color = if (isDarkActive) colors.ink else colors.bg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Speed button
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.bg.copy(alpha = 0.2f))
                                .clickable {
                                    haptics.tick()
                                    audioPlayer.cycleSpeed()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${audioState.speed}x",
                                color = if (isDarkActive) colors.ink else colors.bg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Close button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable {
                                    haptics.tick()
                                    audioPlayer.stop()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                color = if (isDarkActive) colors.mute else colors.bg.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Fullscreen Pinch-to-Zoom Lightbox Gallery
        activeGalleryIndex?.let { initialIndex ->
            ImageViewerDialog(
                images = allArticleImages,
                initialIndex = initialIndex,
                onDismiss = { activeGalleryIndex = null }
            )
        }

        // Share Story Sheet (with link sharing & quote card generator)
        if (showShareSheet) {
            ShareStorySheet(
                article = currentArticle,
                paragraphs = paragraphTexts,
                initialQuote = pendingShareQuote,
                onDismiss = {
                    showShareSheet = false
                    pendingShareQuote = null
                    onQuoteShared?.invoke()
                }
            )
        }
    }
    }
}

@Composable
private fun FactsBlock(
    article: ArticleEntity,
    onSourceClick: () -> Unit
) {
    val colors = SignalTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        HorizontalDivider(color = colors.line, thickness = 1.dp)

        FactRow(
            label = "Source",
            valueContent = {
                Text(
                    text = article.sourceName,
                    color = colors.ink,
                    fontSize = 14.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onSourceClick() }
                )
            }
        )

        if (!article.author.isNullOrBlank()) {
            FactRow(
                label = "Author",
                value = article.author
            )
        }

        FactRow(
            label = "Published",
            value = TimeUtils.formatArticleDate(article.publishedTime)
        )

        FactRow(
            label = "Read time",
            value = "${TimeUtils.calculateReadTimeMinutes(article.fullContent ?: article.excerpt)} min"
        )
    }
}

@Composable
private fun FactRow(
    label: String,
    value: String? = null,
    valueContent: (@Composable () -> Unit)? = null
) {
    val colors = SignalTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colors.mute,
            fontSize = 14.sp,
            modifier = Modifier.size(width = 104.dp, height = 20.dp)
        )
        if (valueContent != null) {
            valueContent()
        } else {
            Text(
                text = value ?: "",
                color = colors.ink,
                fontSize = 14.sp
            )
        }
    }
    HorizontalDivider(color = colors.line, thickness = 1.dp)
}

private enum class VideoProvider {
    YOUTUBE,
    VIMEO,
    HTML5,
    GENERIC
}

private enum class SocialPlatform(val displayName: String, val badge: String) {
    TWITTER("X / Twitter", "𝕏"),
    INSTAGRAM("Instagram", "Instagram"),
    BLUESKY("Bluesky", "🦋 Bluesky"),
    THREADS("Threads", "Threads"),
    TIKTOK("TikTok", "TikTok"),
    REDDIT("Reddit", "Reddit"),
    GENERIC("Social", "Social")
}

private sealed class ContentBlock {
    data class Paragraph(val text: String, val html: String = text) : ContentBlock()
    data class Heading(val text: String, val level: Int) : ContentBlock()
    data class Image(val url: String, val caption: String? = null) : ContentBlock()
    data class Video(
        val videoUrl: String,
        val posterUrl: String? = null,
        val title: String? = null,
        val provider: VideoProvider = VideoProvider.GENERIC,
        val videoId: String? = null
    ) : ContentBlock()
    data class SocialEmbed(
        val platform: SocialPlatform,
        val author: String? = null,
        val handle: String? = null,
        val text: String,
        val postUrl: String,
        val mediaUrl: String? = null
    ) : ContentBlock()
    data class Quote(val text: String) : ContentBlock()
    data class BulletList(val items: List<String>, val htmlItems: List<String> = items) : ContentBlock()
}

private fun extractYouTubeVideoId(url: String): String? {
    val regex = Regex("""(?:youtu\.be/|youtube(?:-nocookie)?\.com/(?:embed/|v/|watch\?v=|watch\?.+&v=|shorts/))([\w-]{11})""")
    return regex.find(url)?.groupValues?.get(1)
}

private fun extractVimeoVideoId(url: String): String? {
    val regex = Regex("""vimeo\.com/(?:video/)?(\d+)""")
    return regex.find(url)?.groupValues?.get(1)
}

private fun extractSocialEmbedFromBlockquote(el: org.jsoup.nodes.Element): ContentBlock.SocialEmbed? {
    val classes = el.classNames().map { it.lowercase(Locale.ROOT) }
    val allText = el.text().trim()
    val links = el.select("a[href]")
    val lastLink = links.lastOrNull()?.attr("href")?.trim().orEmpty()

    // 1. Twitter / X embed: class contains "twitter-tweet" or link contains twitter.com/x.com
    val twitterLink = links.firstOrNull { it.attr("href").contains("twitter.com") || it.attr("href").contains("x.com") }?.attr("href")?.trim()
    if (classes.any { it.contains("twitter") } || !twitterLink.isNullOrBlank()) {
        val postUrl = twitterLink ?: lastLink
        val pText = el.select("p").text().trim().ifBlank { allText }
        val authorMatch = Regex("""[—–-]\s*([^(]+)\s*(?:\((@[^)]+)\))?""").find(allText)
        val authorName = authorMatch?.groupValues?.get(1)?.trim()
        val authorHandle = authorMatch?.groupValues?.getOrNull(2)?.trim()
        val cleanText = pText.substringBefore("—").substringBefore("–").trim()
        return ContentBlock.SocialEmbed(
            platform = SocialPlatform.TWITTER,
            author = authorName,
            handle = authorHandle,
            text = cleanText.ifBlank { allText },
            postUrl = postUrl
        )
    }

    // 2. Instagram: class "instagram-media" or instagram.com link
    val igLink = links.firstOrNull { it.attr("href").contains("instagram.com") }?.attr("href")?.trim()
    if (classes.any { it.contains("instagram") } || !igLink.isNullOrBlank()) {
        val postUrl = igLink ?: lastLink
        return ContentBlock.SocialEmbed(
            platform = SocialPlatform.INSTAGRAM,
            author = "Instagram",
            handle = null,
            text = el.select("p").firstOrNull()?.text()?.trim() ?: allText.take(180),
            postUrl = postUrl
        )
    }

    // 3. Bluesky: class contains "bluesky" or bsky.app link
    val bskyLink = links.firstOrNull { it.attr("href").contains("bsky.app") }?.attr("href")?.trim()
    if (classes.any { it.contains("bluesky") } || !bskyLink.isNullOrBlank()) {
        val postUrl = bskyLink ?: lastLink
        val pText = el.select("p").text().trim().ifBlank { allText }
        return ContentBlock.SocialEmbed(
            platform = SocialPlatform.BLUESKY,
            author = "Bluesky",
            handle = null,
            text = pText,
            postUrl = postUrl
        )
    }

    // 4. Threads: threads.net link
    val threadsLink = links.firstOrNull { it.attr("href").contains("threads.net") }?.attr("href")?.trim()
    if (classes.any { it.contains("threads") } || !threadsLink.isNullOrBlank()) {
        val postUrl = threadsLink ?: lastLink
        return ContentBlock.SocialEmbed(
            platform = SocialPlatform.THREADS,
            author = "Threads",
            handle = null,
            text = el.select("p").text().trim().ifBlank { allText },
            postUrl = postUrl
        )
    }

    // 5. TikTok: tiktok.com link
    val tiktokLink = links.firstOrNull { it.attr("href").contains("tiktok.com") }?.attr("href")?.trim()
    if (classes.any { it.contains("tiktok") } || !tiktokLink.isNullOrBlank()) {
        val postUrl = tiktokLink ?: lastLink
        return ContentBlock.SocialEmbed(
            platform = SocialPlatform.TIKTOK,
            author = "TikTok",
            handle = null,
            text = el.select("p").text().trim().ifBlank { allText },
            postUrl = postUrl
        )
    }

    // 6. Reddit: reddit.com link
    val redditLink = links.firstOrNull { it.attr("href").contains("reddit.com") }?.attr("href")?.trim()
    if (classes.any { it.contains("reddit") } || !redditLink.isNullOrBlank()) {
        val postUrl = redditLink ?: lastLink
        return ContentBlock.SocialEmbed(
            platform = SocialPlatform.REDDIT,
            author = "Reddit",
            handle = null,
            text = allText,
            postUrl = postUrl
        )
    }

    return null
}

private fun extractSocialEmbedFromLink(url: String, fallbackText: String): ContentBlock.SocialEmbed? {
    val clean = url.trim()
    return when {
        clean.contains("twitter.com/") || clean.contains("x.com/") -> {
            val handleMatch = Regex("""(?:twitter\.com|x\.com)/([^/]+)/status/(\d+)""").find(clean)
            val handle = handleMatch?.groupValues?.get(1)?.let { "@$it" }
            ContentBlock.SocialEmbed(
                platform = SocialPlatform.TWITTER,
                author = handle ?: "X / Twitter Post",
                handle = handle,
                text = fallbackText.takeIf { it != clean } ?: "View post on X",
                postUrl = clean
            )
        }
        clean.contains("instagram.com/p/") || clean.contains("instagram.com/reel/") -> {
            ContentBlock.SocialEmbed(
                platform = SocialPlatform.INSTAGRAM,
                author = "Instagram",
                handle = null,
                text = fallbackText.takeIf { it != clean } ?: "View post on Instagram",
                postUrl = clean
            )
        }
        clean.contains("bsky.app/profile/") -> {
            val handleMatch = Regex("""bsky\.app/profile/([^/]+)/post/""").find(clean)
            val handle = handleMatch?.groupValues?.get(1)?.let { "@$it" }
            ContentBlock.SocialEmbed(
                platform = SocialPlatform.BLUESKY,
                author = handle ?: "Bluesky Post",
                handle = handle,
                text = fallbackText.takeIf { it != clean } ?: "View post on Bluesky",
                postUrl = clean
            )
        }
        clean.contains("threads.net/@") -> {
            ContentBlock.SocialEmbed(
                platform = SocialPlatform.THREADS,
                author = "Threads Post",
                handle = null,
                text = fallbackText.takeIf { it != clean } ?: "View post on Threads",
                postUrl = clean
            )
        }
        clean.contains("tiktok.com/@") -> {
            ContentBlock.SocialEmbed(
                platform = SocialPlatform.TIKTOK,
                author = "TikTok Video",
                handle = null,
                text = fallbackText.takeIf { it != clean } ?: "Watch video on TikTok",
                postUrl = clean
            )
        }
        clean.contains("reddit.com/r/") -> {
            val subMatch = Regex("""reddit\.com/r/([^/]+)""").find(clean)
            val sub = subMatch?.groupValues?.get(1)?.let { "r/$it" }
            ContentBlock.SocialEmbed(
                platform = SocialPlatform.REDDIT,
                author = sub ?: "Reddit Post",
                handle = sub,
                text = fallbackText.takeIf { it != clean } ?: "View discussion on Reddit",
                postUrl = clean
            )
        }
        else -> null
    }
}

private fun parseContentBlocks(html: String, heroImageUrl: String? = null): List<ContentBlock> {
    val doc = Jsoup.parse(html)
    val blocks = mutableListOf<ContentBlock>()
    val seenImages = mutableSetOf<String>()
    heroImageUrl?.let { seenImages.add(it.trim()) }

    val elements = doc.body().select("p, h1, h2, h3, h4, figure, img, blockquote, ul, ol, iframe, video, .in-content-video-embed")
    val processedElements = mutableSetOf<org.jsoup.nodes.Element>()

    for (el in elements) {
        if (el in processedElements) continue

        when {
            el.hasClass("in-content-video-embed") -> {
                val video = el.selectFirst("video")
                if (video != null) {
                    val src = video.attr("src").ifBlank { video.selectFirst("source")?.attr("src") }?.trim()
                    val poster = video.attr("poster").trim().ifBlank { null }
                    val title = video.attr("title").trim().ifBlank { null }
                    if (!src.isNullOrBlank()) {
                        blocks.add(
                            ContentBlock.Video(
                                videoUrl = src,
                                posterUrl = poster,
                                title = title,
                                provider = VideoProvider.HTML5
                            )
                        )
                    }
                }
                processedElements.addAll(el.getAllElements())
            }
            el.tagName().lowercase() in listOf("h1", "h2", "h3", "h4") -> {
                val text = el.text().trim()
                if (text.isNotBlank()) {
                    val level = el.tagName().removePrefix("h").toIntOrNull() ?: 2
                    blocks.add(ContentBlock.Heading(text, level))
                    processedElements.add(el)
                }
            }
            el.tagName().equals("iframe", ignoreCase = true) -> {
                val src = el.attr("src").ifBlank { el.attr("data-src") }.trim()
                val title = el.attr("title").trim().ifBlank { null }
                val ytId = extractYouTubeVideoId(src)
                if (ytId != null) {
                    blocks.add(
                        ContentBlock.Video(
                            videoUrl = "https://www.youtube.com/watch?v=$ytId",
                            posterUrl = "https://img.youtube.com/vi/$ytId/hqdefault.jpg",
                            title = title,
                            provider = VideoProvider.YOUTUBE,
                            videoId = ytId
                        )
                    )
                    processedElements.add(el)
                } else {
                    val vimeoId = extractVimeoVideoId(src)
                    if (vimeoId != null) {
                        blocks.add(
                            ContentBlock.Video(
                                videoUrl = "https://vimeo.com/$vimeoId",
                                posterUrl = null,
                                title = title,
                                provider = VideoProvider.VIMEO,
                                videoId = vimeoId
                            )
                        )
                        processedElements.add(el)
                    } else if (src.isNotBlank() && (src.contains("embed") || src.contains("video") || src.contains("player"))) {
                        blocks.add(
                            ContentBlock.Video(
                                videoUrl = src,
                                posterUrl = null,
                                title = title,
                                provider = VideoProvider.GENERIC
                            )
                        )
                        processedElements.add(el)
                    }
                }
            }
            el.tagName().equals("video", ignoreCase = true) -> {
                val src = el.attr("src").ifBlank { el.selectFirst("source")?.attr("src") }?.trim()
                val poster = el.attr("poster").trim().ifBlank { null }
                val title = el.attr("title").trim().ifBlank { null }
                if (!src.isNullOrBlank()) {
                    blocks.add(
                        ContentBlock.Video(
                            videoUrl = src,
                            posterUrl = poster,
                            title = title,
                            provider = VideoProvider.HTML5
                        )
                    )
                    processedElements.addAll(el.getAllElements())
                }
            }
            el.tagName().equals("figure", ignoreCase = true) -> {
                val iframe = el.selectFirst("iframe")
                val video = el.selectFirst("video")
                val img = el.selectFirst("img")
                if (iframe != null) {
                    val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }.trim()
                    val title = iframe.attr("title").trim().ifBlank { null }
                    val ytId = extractYouTubeVideoId(src)
                    if (ytId != null) {
                        blocks.add(
                            ContentBlock.Video(
                                videoUrl = "https://www.youtube.com/watch?v=$ytId",
                                posterUrl = "https://img.youtube.com/vi/$ytId/hqdefault.jpg",
                                title = title,
                                provider = VideoProvider.YOUTUBE,
                                videoId = ytId
                            )
                        )
                    } else {
                        val vimeoId = extractVimeoVideoId(src)
                        if (vimeoId != null) {
                            blocks.add(
                                ContentBlock.Video(
                                    videoUrl = "https://vimeo.com/$vimeoId",
                                    posterUrl = null,
                                    title = title,
                                    provider = VideoProvider.VIMEO,
                                    videoId = vimeoId
                                )
                            )
                        } else if (src.isNotBlank() && (src.contains("embed") || src.contains("video") || src.contains("player"))) {
                            blocks.add(
                                ContentBlock.Video(
                                    videoUrl = src,
                                    posterUrl = null,
                                    title = title,
                                    provider = VideoProvider.GENERIC
                                )
                            )
                        }
                    }
                } else if (video != null) {
                    val src = video.attr("src").ifBlank { video.selectFirst("source")?.attr("src") }?.trim()
                    val poster = video.attr("poster").trim().ifBlank { null }
                    val title = video.attr("title").trim().ifBlank { null }
                    if (!src.isNullOrBlank()) {
                        blocks.add(
                            ContentBlock.Video(
                                videoUrl = src,
                                posterUrl = poster,
                                title = title,
                                provider = VideoProvider.HTML5
                            )
                        )
                    }
                } else if (img != null) {
                    val src = ImageResolver.extractBestImageUrlFromElement(img) ?: img.attr("src").ifBlank { img.attr("data-src") }.trim()
                    val caption = el.selectFirst("figcaption, .image-caption, .caption, small")?.text()?.trim()
                    if (!src.isNullOrBlank() && !src.startsWith("data:") && seenImages.add(src)) {
                        blocks.add(ContentBlock.Image(src, caption?.ifBlank { img.attr("alt").trim() }?.ifBlank { null }))
                    }
                }
                processedElements.addAll(el.getAllElements())
            }
            el.tagName().equals("p", ignoreCase = true) -> {
                val iframe = el.selectFirst("iframe")
                val video = el.selectFirst("video")
                val aTag = el.selectFirst("a[href]")
                val href = aTag?.attr("href")?.trim().orEmpty()
                val ytIdLink = extractYouTubeVideoId(href)
                val vimeoIdLink = extractVimeoVideoId(href)

                if (iframe != null) {
                    val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }.trim()
                    val title = iframe.attr("title").trim().ifBlank { null }
                    val ytId = extractYouTubeVideoId(src)
                    if (ytId != null) {
                        blocks.add(
                            ContentBlock.Video(
                                videoUrl = "https://www.youtube.com/watch?v=$ytId",
                                posterUrl = "https://img.youtube.com/vi/$ytId/hqdefault.jpg",
                                title = title,
                                provider = VideoProvider.YOUTUBE,
                                videoId = ytId
                            )
                        )
                        processedElements.addAll(el.getAllElements())
                    } else {
                        val vimeoId = extractVimeoVideoId(src)
                        if (vimeoId != null) {
                            blocks.add(
                                ContentBlock.Video(
                                    videoUrl = "https://vimeo.com/$vimeoId",
                                    posterUrl = null,
                                    title = title,
                                    provider = VideoProvider.VIMEO,
                                    videoId = vimeoId
                                )
                            )
                            processedElements.addAll(el.getAllElements())
                        }
                    }
                } else if (video != null) {
                    val src = video.attr("src").ifBlank { video.selectFirst("source")?.attr("src") }?.trim()
                    val poster = video.attr("poster").trim().ifBlank { null }
                    val title = video.attr("title").trim().ifBlank { null }
                    if (!src.isNullOrBlank()) {
                        blocks.add(
                            ContentBlock.Video(
                                videoUrl = src,
                                posterUrl = poster,
                                title = title,
                                provider = VideoProvider.HTML5
                            )
                        )
                        processedElements.addAll(el.getAllElements())
                    }
                } else if (ytIdLink != null && el.text().trim().length < 80) {
                    blocks.add(
                        ContentBlock.Video(
                            videoUrl = "https://www.youtube.com/watch?v=$ytIdLink",
                            posterUrl = "https://img.youtube.com/vi/$ytIdLink/hqdefault.jpg",
                            title = aTag?.text()?.trim()?.ifBlank { null },
                            provider = VideoProvider.YOUTUBE,
                            videoId = ytIdLink
                        )
                    )
                    processedElements.addAll(el.getAllElements())
                } else if (vimeoIdLink != null && el.text().trim().length < 80) {
                    blocks.add(
                        ContentBlock.Video(
                            videoUrl = "https://vimeo.com/$vimeoIdLink",
                            posterUrl = null,
                            title = aTag?.text()?.trim()?.ifBlank { null },
                            provider = VideoProvider.VIMEO,
                            videoId = vimeoIdLink
                        )
                    )
                    processedElements.addAll(el.getAllElements())
                } else {
                    val text = el.text().trim()
                    val socialLinkEmbed = if (href.isNotBlank() && (text.length < 120 || text == href || href.contains("/status/") || href.contains("/p/"))) {
                        extractSocialEmbedFromLink(href, text)
                    } else null

                    if (socialLinkEmbed != null) {
                        blocks.add(socialLinkEmbed)
                        processedElements.addAll(el.getAllElements())
                    } else {
                        val img = el.selectFirst("img")
                        val realSrc = img?.let { ImageResolver.extractBestImageUrlFromElement(it) ?: it.attr("src").ifBlank { it.attr("data-src") }.trim() }
                        if (img != null && text.length < 5) {
                            val alt = img.attr("alt").trim().ifBlank { img.attr("title").trim() }
                            if (!realSrc.isNullOrBlank() && !realSrc.startsWith("data:") && seenImages.add(realSrc)) {
                                blocks.add(ContentBlock.Image(realSrc, alt.ifBlank { null }))
                            }
                        } else {
                            if (text.isNotBlank()) {
                                blocks.add(ContentBlock.Paragraph(text = text, html = el.html()))
                            }
                            if (img != null && !realSrc.isNullOrBlank() && !realSrc.startsWith("data:") && seenImages.add(realSrc)) {
                                val alt = img.attr("alt").trim()
                                blocks.add(ContentBlock.Image(realSrc, alt.ifBlank { null }))
                            }
                        }
                        processedElements.add(el)
                    }
                }
            }
            el.tagName().equals("img", ignoreCase = true) -> {
                if (el.parent()?.tagName()?.lowercase() !in listOf("figure", "p")) {
                    val src = ImageResolver.extractBestImageUrlFromElement(el) ?: el.attr("src").ifBlank { el.attr("data-src") }.trim()
                    val alt = el.attr("alt").trim()
                    if (src.isNotBlank() && !src.startsWith("data:") && seenImages.add(src)) {
                        blocks.add(ContentBlock.Image(src, alt.ifBlank { null }))
                    }
                    processedElements.add(el)
                }
            }
            el.tagName().equals("blockquote", ignoreCase = true) -> {
                val socialEmbed = extractSocialEmbedFromBlockquote(el)
                if (socialEmbed != null) {
                    blocks.add(socialEmbed)
                    processedElements.addAll(el.getAllElements())
                } else {
                    val text = el.text().trim()
                    if (text.isNotBlank()) {
                        blocks.add(ContentBlock.Quote(text))
                        processedElements.addAll(el.getAllElements())
                    }
                }
            }
            el.tagName().lowercase() in listOf("ul", "ol") -> {
                val listItems = el.select("li").map { it.text().trim() }.filter { it.isNotBlank() }
                val htmlItems = el.select("li").map { it.html().trim() }.filter { it.isNotBlank() }
                if (listItems.isNotEmpty()) {
                    blocks.add(ContentBlock.BulletList(items = listItems, htmlItems = htmlItems))
                    processedElements.addAll(el.getAllElements())
                }
            }
        }
    }

    if (blocks.isEmpty()) {
        val rawText = doc.text().trim()
        if (rawText.isNotBlank()) {
            val fallbackParagraphs = rawText.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
            fallbackParagraphs.forEach { blocks.add(ContentBlock.Paragraph(text = it, html = it)) }
        }
    }

    return blocks
}

private fun appendHtmlNode(
    builder: AnnotatedString.Builder,
    node: org.jsoup.nodes.Node,
    inkColor: Color,
    onLinkClick: (String) -> Unit
) {
    when (node) {
        is org.jsoup.nodes.TextNode -> {
            builder.append(node.text())
        }
        is org.jsoup.nodes.Element -> {
            when (node.tagName().lowercase()) {
                "a" -> {
                    val href = node.attr("href").trim()
                    val linkText = node.text()
                    if (href.isNotBlank()) {
                        builder.withLink(
                            LinkAnnotation.Url(
                                url = href,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = inkColor,
                                        textDecoration = TextDecoration.Underline,
                                        fontWeight = FontWeight.Medium
                                    )
                                ),
                                linkInteractionListener = { annotation ->
                                    val target = (annotation as? LinkAnnotation.Url)?.url ?: href
                                    onLinkClick(target)
                                }
                            )
                        ) {
                            append(linkText)
                        }
                    } else {
                        builder.append(linkText)
                    }
                }
                "b", "strong" -> {
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        node.childNodes().forEach { appendHtmlNode(builder, it, inkColor, onLinkClick) }
                    }
                }
                "i", "em" -> {
                    builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        node.childNodes().forEach { appendHtmlNode(builder, it, inkColor, onLinkClick) }
                    }
                }
                else -> {
                    node.childNodes().forEach { appendHtmlNode(builder, it, inkColor, onLinkClick) }
                }
            }
        }
    }
}

@Composable
private fun RenderContentBlock(
    block: ContentBlock,
    fontSize: androidx.compose.ui.unit.TextUnit,
    isSerif: Boolean = false,
    articleUrl: String? = null,
    onImageClick: (url: String, caption: String?) -> Unit,
    onLinkClick: (url: String) -> Unit,
    onVideoClick: (url: String) -> Unit
) {
    val colors = SignalTheme.colors

    when (block) {
        is ContentBlock.Paragraph -> {
            val annotated = remember(block.html, colors.ink) {
                try {
                    val doc = Jsoup.parseBodyFragment(block.html)
                    val pEl = doc.body().selectFirst("p") ?: doc.body()
                    buildAnnotatedString {
                        pEl.childNodes().forEach { node ->
                            appendHtmlNode(this, node, colors.ink, onLinkClick)
                        }
                    }
                } catch (_: Exception) {
                    AnnotatedString(block.text)
                }
            }

            Text(
                text = annotated,
                style = SignalTheme.typography.readerBody(fontSize, isSerif),
                color = colors.ink,
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }
        is ContentBlock.Image -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.card)
                        .clickable { onImageClick(block.url, block.caption) }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(block.url)
                            .crossfade(true)
                            .build(),
                        contentDescription = block.caption ?: "Inline article image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        contentScale = ContentScale.FillWidth
                    )
                }
                if (!block.caption.isNullOrBlank()) {
                    Text(
                        text = block.caption,
                        color = colors.mute,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
                    )
                }
            }
        }
        is ContentBlock.Video -> {
            VideoCardBlock(
                block = block,
                articleUrl = articleUrl,
                onVideoClick = onVideoClick
            )
        }
        is ContentBlock.Heading -> {
            Text(
                text = block.text,
                style = SignalTheme.typography.screenTitle,
                fontFamily = if (isSerif) FontFamily.Serif else SignalTheme.typography.screenTitle.fontFamily,
                fontSize = if (block.level <= 2) 20.sp else 18.sp,
                color = colors.ink,
                modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
            )
        }
        is ContentBlock.Quote -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(32.dp)
                        .background(colors.ink)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = block.text,
                    style = SignalTheme.typography.readerBody(fontSize, isSerif),
                    fontStyle = FontStyle.Italic,
                    color = colors.ink,
                    lineHeight = 24.sp
                )
            }
        }
        is ContentBlock.SocialEmbed -> {
            SocialEmbedCard(block = block)
        }
        is ContentBlock.BulletList -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                block.htmlItems.forEach { htmlItem ->
                    val annotated = remember(htmlItem, colors.ink) {
                        try {
                            val doc = Jsoup.parseBodyFragment(htmlItem)
                            val liEl = doc.body().selectFirst("li") ?: doc.body()
                            buildAnnotatedString {
                                liEl.childNodes().forEach { node ->
                                    appendHtmlNode(this, node, colors.ink, onLinkClick)
                                }
                            }
                        } catch (_: Exception) {
                            AnnotatedString(htmlItem)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("—", color = colors.mute, fontSize = fontSize)
                        Text(
                            text = annotated,
                            style = SignalTheme.typography.readerBody(fontSize, isSerif),
                            color = colors.ink
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialEmbedCard(
    block: ContentBlock.SocialEmbed,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = SignalTheme.colors
    val haptics = rememberSignalHaptics()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp))
            .signalTactileClick(
                targetScale = SignalTactile.ButtonPressScale,
                onClick = {
                    haptics.pop()
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(block.postUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            )
            .padding(16.dp)
    ) {
        // Top header row: Platform badge + author + external indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Platform pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.ink)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = block.platform.badge,
                        color = colors.bg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!block.author.isNullOrBlank()) {
                    Text(
                        text = block.author,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!block.handle.isNullOrBlank()) {
                    Text(
                        text = block.handle,
                        fontSize = 13.sp,
                        color = colors.mute,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = "↗",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent
            )
        }

        if (block.text.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = block.text,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = colors.ink
            )
        }

        if (!block.mediaUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.card)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(block.mediaUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Social embed media preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "View on ${block.platform.displayName}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.mute
        )
    }
}

@Composable
private fun InAppVideoPlayer(
    block: ContentBlock.Video,
    articleUrl: String? = null,
    onClose: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (block.provider == VideoProvider.YOUTUBE) {
        val ytId = block.videoId ?: extractYouTubeVideoId(block.videoUrl)
        if (ytId != null) {
            YouTubePlayerCard(
                videoId = ytId,
                articleUrl = articleUrl,
                onClose = onClose,
                onOpenExternal = onOpenExternal,
                modifier = modifier
            )
            return
        }
    }

    WebViewPlayerCard(
        block = block,
        articleUrl = articleUrl,
        onClose = onClose,
        onOpenExternal = onOpenExternal,
        modifier = modifier
    )
}

@Composable
private fun YouTubePlayerCard(
    videoId: String,
    articleUrl: String?,
    onClose: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val context = LocalContext.current
    var playerViewRef by remember { mutableStateOf<YouTubePlayerView?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<PlayerConstants.PlayerError?>(null) }
    var customFullscreenView by remember { mutableStateOf<View?>(null) }
    var exitFullscreenCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    val origin = remember(articleUrl) {
        runCatching {
            val uri = Uri.parse(articleUrl)
            if (!uri.host.isNullOrBlank() && uri.scheme != null) {
                "${uri.scheme}://${uri.host}"
            } else null
        }.getOrNull() ?: "https://${context.packageName}"
    }

    DisposableEffect(videoId) {
        onDispose {
            try {
                playerViewRef?.release()
            } catch (_: Exception) {}
            playerViewRef = null
            customFullscreenView = null
            exitFullscreenCallback = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp))
    ) {
        if (playbackError != null) {
            // Editorial Fallback Card for restricted/copyright-blocked videos (e.g. VEVO / music labels)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF161514))
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    PlayIcon(color = SignalAccent, modifier = Modifier.size(12.dp))
                    Text(
                        text = "YOUTUBE",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                Text(
                    text = "This video requires playback directly on YouTube.",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(bottom = 14.dp, start = 12.dp, end = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SignalAccent)
                        .clickable { onOpenExternal() }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Watch on YouTube",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "↗",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    val playerOptions = IFramePlayerOptions.Builder()
                        .controls(1)
                        .fullscreen(1)
                        .rel(0)
                        .origin(origin)
                        .build()

                    YouTubePlayerView(ctx).apply {
                        enableAutomaticInitialization = false
                        addFullscreenListener(object : FullscreenListener {
                            override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                                customFullscreenView = fullscreenView
                                exitFullscreenCallback = exitFullscreen
                            }

                            override fun onExitFullscreen() {
                                customFullscreenView = null
                                exitFullscreenCallback = null
                            }
                        })
                        initialize(
                            youTubePlayerListener = object : AbstractYouTubePlayerListener() {
                                override fun onReady(youTubePlayer: YouTubePlayer) {
                                    isBuffering = false
                                    youTubePlayer.loadVideo(videoId, 0f)
                                }

                                override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                                    if (state == PlayerConstants.PlayerState.PLAYING) {
                                        isBuffering = false
                                    }
                                }

                                override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                                    playbackError = error
                                }
                            },
                            handleNetworkEvents = true,
                            playerOptions = playerOptions,
                            videoId = videoId
                        )
                        playerViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Buffering indicator
            if (isBuffering) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = SignalAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // Action pills: Open External ↗ and Close ✕
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.70f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                    .clickable { onOpenExternal() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↗",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.70f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Fullscreen Overlay Dialog
        if (customFullscreenView != null) {
            Dialog(
                onDismissRequest = {
                    exitFullscreenCallback?.invoke()
                    customFullscreenView = null
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { customFullscreenView!! },
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            .clickable {
                                exitFullscreenCallback?.invoke()
                                customFullscreenView = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewPlayerCard(
    block: ContentBlock.Video,
    articleUrl: String? = null,
    onClose: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var customFullscreenView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val (baseUrl, htmlContent) = remember(block, articleUrl) {
        val articleOrigin = runCatching {
            val uri = Uri.parse(articleUrl)
            if (!uri.host.isNullOrBlank() && uri.scheme != null) {
                "${uri.scheme}://${uri.host}"
            } else null
        }.getOrNull() ?: "https://stoa.app"

        when (block.provider) {
            VideoProvider.YOUTUBE -> {
                val ytId = block.videoId ?: extractYouTubeVideoId(block.videoUrl)
                val embedSrc = if (ytId != null) {
                    "https://www.youtube-nocookie.com/embed/$ytId?autoplay=1&playsinline=1&enablejsapi=1&rel=0&origin=$articleOrigin"
                } else {
                    block.videoUrl
                }
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <meta name="referrer" content="strict-origin-when-cross-origin">
                        <style>
                            * { margin:0; padding:0; box-sizing:border-box; background-color:#000; }
                            html, body { width:100%; height:100%; overflow:hidden; background-color:#000; }
                            iframe { width:100%; height:100%; border:0; display:block; }
                        </style>
                    </head>
                    <body>
                        <iframe 
                            src="$embedSrc" 
                            frameborder="0"
                            referrerpolicy="strict-origin-when-cross-origin"
                            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                            allowfullscreen>
                        </iframe>
                    </body>
                    </html>
                """.trimIndent()
                Pair(articleOrigin, html)
            }
            VideoProvider.VIMEO -> {
                val vimeoId = block.videoId ?: extractVimeoVideoId(block.videoUrl)
                val embedSrc = if (vimeoId != null) {
                    "https://player.vimeo.com/video/$vimeoId?autoplay=1&playsinline=1&title=0&byline=0&portrait=0"
                } else {
                    block.videoUrl
                }
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <meta name="referrer" content="strict-origin-when-cross-origin">
                        <style>
                            * { margin:0; padding:0; box-sizing:border-box; background-color:#000; }
                            html, body { width:100%; height:100%; overflow:hidden; background-color:#000; }
                            iframe { width:100%; height:100%; border:0; display:block; }
                        </style>
                    </head>
                    <body>
                        <iframe 
                            src="$embedSrc" 
                            frameborder="0"
                            referrerpolicy="strict-origin-when-cross-origin"
                            allow="autoplay; fullscreen; picture-in-picture" 
                            allowfullscreen>
                        </iframe>
                    </body>
                    </html>
                """.trimIndent()
                Pair("https://vimeo.com", html)
            }
            VideoProvider.HTML5 -> {
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                            * { margin:0; padding:0; box-sizing:border-box; background-color:#000; }
                            html, body { width:100%; height:100%; overflow:hidden; display:flex; align-items:center; justify-content:center; background-color:#000; }
                            video { width:100%; height:100%; object-fit:contain; }
                        </style>
                    </head>
                    <body>
                        <video controls autoplay playsinline src="${block.videoUrl}"></video>
                    </body>
                    </html>
                """.trimIndent()
                Pair("https://stoa.app", html)
            }
            VideoProvider.GENERIC -> {
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <meta name="referrer" content="strict-origin-when-cross-origin">
                        <style>
                            * { margin:0; padding:0; box-sizing:border-box; background-color:#000; }
                            html, body { width:100%; height:100%; overflow:hidden; background-color:#000; }
                            iframe { width:100%; height:100%; border:0; display:block; }
                        </style>
                    </head>
                    <body>
                        <iframe 
                            src="${block.videoUrl}" 
                            frameborder="0"
                            referrerpolicy="strict-origin-when-cross-origin"
                            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                            allowfullscreen>
                        </iframe>
                    </body>
                    </html>
                """.trimIndent()
                Pair(articleOrigin, html)
            }
        }
    }

    DisposableEffect(block.videoUrl) {
        onDispose {
            try {
                webViewRef?.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    onPause()
                    destroy()
                }
            } catch (_: Exception) {}
            webViewRef = null
            customViewCallback?.onCustomViewHidden()
            customFullscreenView = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp))
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadsImagesAutomatically = true
                        setSupportZoom(false)
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = userAgentString.replace("; wv", "")
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            customFullscreenView = view
                            customViewCallback = callback
                        }

                        override fun onHideCustomView() {
                            customViewCallback?.onCustomViewHidden()
                            customFullscreenView = null
                            customViewCallback = null
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isBuffering = false
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val uri = request?.url ?: return false
                            val urlStr = uri.toString()
                            if (urlStr.contains("youtube.com/watch") || urlStr.contains("youtu.be/")) {
                                onOpenExternal()
                                return true
                            }
                            return false
                        }
                    }

                    loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering indicator
        if (isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = SignalAccent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Action pills: Open External ↗ and Close ✕
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.70f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                    .clickable { onOpenExternal() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↗",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.70f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Fullscreen Overlay Dialog
        if (customFullscreenView != null) {
            Dialog(
                onDismissRequest = {
                    customViewCallback?.onCustomViewHidden()
                    customFullscreenView = null
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { customFullscreenView!! },
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            .clickable {
                                customViewCallback?.onCustomViewHidden()
                                customFullscreenView = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCardBlock(
    block: ContentBlock.Video,
    articleUrl: String? = null,
    onVideoClick: (url: String) -> Unit
) {
    val colors = SignalTheme.colors
    val haptics = rememberSignalHaptics()
    var isPlaying by rememberSaveable { mutableStateOf(false) }

    val providerBadgeText = when (block.provider) {
        VideoProvider.YOUTUBE -> "YOUTUBE"
        VideoProvider.VIMEO -> "VIMEO"
        VideoProvider.HTML5 -> "VIDEO"
        VideoProvider.GENERIC -> "VIDEO"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
    ) {
        if (isPlaying) {
            InAppVideoPlayer(
                block = block,
                articleUrl = articleUrl,
                onClose = {
                    haptics.tick()
                    isPlaying = false
                },
                onOpenExternal = {
                    haptics.pop()
                    onVideoClick(block.videoUrl)
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.card)
                    .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                    .clickable {
                        haptics.pop()
                        isPlaying = true
                    }
            ) {
                // Poster thumbnail if available
                if (!block.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(block.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = block.title ?: "Video preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Sleek modernist dark card background if no poster
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF22201D),
                                        Color(0xFF2C2A26),
                                        Color(0xFF161514)
                                    )
                                )
                            )
                    )
                }

                // Dark gradient scrim overlay for contrast & cinematic editorial look
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.40f),
                                    Color.Black.copy(alpha = 0.20f),
                                    Color.Black.copy(alpha = 0.70f)
                                )
                            )
                        )
                )

                // Top-left provider pill badge (e.g., "▶ YOUTUBE")
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PlayIcon(
                            color = SignalAccent,
                            modifier = Modifier.size(9.dp)
                        )
                        Text(
                            text = providerBadgeText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Center Vermilion Play Capsule ("▶ WATCH VIDEO")
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(SignalAccent)
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlayIcon(
                            color = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "WATCH VIDEO",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
            }
        }

        // Caption / Title below card if available
        if (!block.title.isNullOrBlank()) {
            Text(
                text = block.title,
                color = colors.mute,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun ExcerptCta(
    onReadFullClick: () -> Unit,
    onExtractClick: () -> Unit,
    isExtracting: Boolean = false
) {
    val colors = SignalTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.ink)
                    .signalPressScale(0.96f) { onReadFullClick() }
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Open site",
                        color = colors.bg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "↗",
                        color = colors.accent,
                        fontSize = 16.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .border(1.dp, colors.ink, CircleShape)
                    .signalPressScale(0.96f) {
                        if (!isExtracting) onExtractClick()
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isExtracting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = colors.ink,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Extract in Stoa",
                        color = colors.ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Text(
            text = "This site only shares an excerpt in its feed. You can extract the full story clean or open the site.",
            color = colors.mute,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun AaPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = SignalTheme.colors
    Box(
        modifier = modifier
            .alpha(if (enabled) 1.0f else 0.35f)
            .signalTactileClick(
                targetScale = SignalTactile.ButtonPressScale,
                enabled = enabled,
                onClick = onClick
            )
            .border(1.dp, colors.ink, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun PillButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AaPillButton(
        onClick = onClick,
        enabled = enabled
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = SignalTheme.colors.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
