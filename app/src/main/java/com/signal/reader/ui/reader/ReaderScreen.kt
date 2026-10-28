package com.signal.reader.ui.reader

import android.content.Intent
import android.net.Uri
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale
import com.signal.reader.ui.theme.signalSharedImage
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ReaderScreen(
    articleId: Long,
    repository: FeedRepository,
    settingsManager: SettingsManager,
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
    var showShareSheet by remember { mutableStateOf(false) }

    val audioPlayer = remember { ArticleAudioPlayer(context) }
    val audioState by audioPlayer.playbackState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.destroy()
        }
    }

    val blocks = remember(currentArticle.fullContent, currentArticle.excerpt, extractedHtml, currentArticle.imageUrl) {
        val raw = extractedHtml ?: currentArticle.fullContent ?: currentArticle.excerpt
        parseContentBlocks(raw, heroImageUrl = currentArticle.imageUrl)
    }

    val paragraphTexts = remember(blocks) {
        blocks.filterIsInstance<ContentBlock.Paragraph>().map { it.text }
    }

    val isExcerpt = remember(currentArticle.isExcerptOnly, currentArticle.fullContent, currentArticle.excerpt, extractedHtml) {
        extractedHtml == null && (currentArticle.isExcerptOnly || (currentArticle.fullContent == null && (currentArticle.excerpt.length < 500 || currentArticle.excerpt.split(" ").size < 90)))
    }

    val allArticleImages = remember(currentArticle, blocks) {
        val list = mutableListOf<ArticleImage>()
        if (!currentArticle.imageUrl.isNullOrBlank()) {
            list.add(ArticleImage(url = currentArticle.imageUrl, caption = currentArticle.title, credit = currentArticle.sourceName))
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
                                if (!currentArticle.imageUrl.isNullOrBlank()) {
                                    Modifier.clickable {
                                        activeGalleryIndex = 0
                                    }
                                } else Modifier
                            )
                    ) {
                        if (!currentArticle.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = currentArticle.imageUrl,
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

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .border(1.dp, colors.ink.copy(alpha = 0.7f), CircleShape)
                                    .signalPressScale(0.96f) {
                                        haptics.tick()
                                        settingsManager.updateTheme(if (isDarkActive) "light" else "dark")
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isDarkActive) {
                                        MoonIcon(color = colors.ink, modifier = Modifier.size(13.dp))
                                        Text(
                                            text = "Dark",
                                            color = colors.ink,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else {
                                        SunIcon(color = colors.ink, modifier = Modifier.size(13.dp))
                                        Text(
                                            text = "Light",
                                            color = colors.ink,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // 2. Single Typeface Switcher Button (switches between Sans and Serif)
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .border(1.dp, colors.ink.copy(alpha = 0.7f), CircleShape)
                                    .signalPressScale(0.96f) {
                                        haptics.tick()
                                        settingsManager.updateFontFamily(if (isSerif) "sans" else "serif")
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (isSerif) "Aa Serif" else "Aa Sans",
                                        color = colors.ink,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = if (isSerif) FontFamily.Serif else SignalTheme.typography.screenTitle.fontFamily
                                    )
                                    Text(
                                        text = "⟲",
                                        color = colors.mute,
                                        fontSize = 11.sp
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
                onDismiss = { showShareSheet = false }
            )
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

private sealed class ContentBlock {
    data class Paragraph(val text: String, val html: String = text) : ContentBlock()
    data class Heading(val text: String, val level: Int) : ContentBlock()
    data class Image(val url: String, val caption: String? = null) : ContentBlock()
    data class Video(
        val videoUrl: String,
        val posterUrl: String? = null,
        val title: String? = null,
        val provider: VideoProvider = VideoProvider.GENERIC
    ) : ContentBlock()
    data class Quote(val text: String) : ContentBlock()
    data class BulletList(val items: List<String>, val htmlItems: List<String> = items) : ContentBlock()
}

private fun extractYouTubeVideoId(url: String): String? {
    val regex = Regex("""(?:youtu\.be/|youtube\.com/(?:embed/|v/|watch\?v=|watch\?.+&v=))([\w-]{11})""")
    return regex.find(url)?.groupValues?.get(1)
}

private fun extractVimeoVideoId(url: String): String? {
    val regex = Regex("""vimeo\.com/(?:video/)?(\d+)""")
    return regex.find(url)?.groupValues?.get(1)
}

private fun parseContentBlocks(html: String, heroImageUrl: String? = null): List<ContentBlock> {
    val doc = Jsoup.parse(html)
    val blocks = mutableListOf<ContentBlock>()
    val seenImages = mutableSetOf<String>()
    heroImageUrl?.let { seenImages.add(it.trim()) }

    val elements = doc.body().select("p, h1, h2, h3, h4, figure, img, blockquote, ul, ol, iframe, video")
    val processedElements = mutableSetOf<org.jsoup.nodes.Element>()

    for (el in elements) {
        if (el in processedElements) continue

        when (el.tagName().lowercase()) {
            "h1", "h2", "h3", "h4" -> {
                val text = el.text().trim()
                if (text.isNotBlank()) {
                    val level = el.tagName().removePrefix("h").toIntOrNull() ?: 2
                    blocks.add(ContentBlock.Heading(text, level))
                    processedElements.add(el)
                }
            }
            "iframe" -> {
                val src = el.attr("src").ifBlank { el.attr("data-src") }.trim()
                val title = el.attr("title").trim().ifBlank { null }
                val ytId = extractYouTubeVideoId(src)
                if (ytId != null) {
                    blocks.add(
                        ContentBlock.Video(
                            videoUrl = "https://www.youtube.com/watch?v=$ytId",
                            posterUrl = "https://img.youtube.com/vi/$ytId/hqdefault.jpg",
                            title = title,
                            provider = VideoProvider.YOUTUBE
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
                                provider = VideoProvider.VIMEO
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
            "video" -> {
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
            "figure" -> {
                val img = el.selectFirst("img")
                val src = img?.attr("src")?.ifBlank { img.attr("data-src") }?.trim()
                val caption = el.selectFirst("figcaption")?.text()?.trim()
                if (!src.isNullOrBlank() && !src.startsWith("data:") && seenImages.add(src)) {
                    blocks.add(ContentBlock.Image(src, caption?.ifBlank { img.attr("alt").trim() }?.ifBlank { null }))
                }
                processedElements.addAll(el.getAllElements())
            }
            "p" -> {
                val iframe = el.selectFirst("iframe")
                val video = el.selectFirst("video")
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
                                provider = VideoProvider.YOUTUBE
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
                                    provider = VideoProvider.VIMEO
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
                } else {
                    val img = el.selectFirst("img")
                    val text = el.text().trim()
                    if (img != null && text.length < 5) {
                        val src = img.attr("src").ifBlank { img.attr("data-src") }.trim()
                        val alt = img.attr("alt").trim().ifBlank { img.attr("title").trim() }
                        if (src.isNotBlank() && !src.startsWith("data:") && seenImages.add(src)) {
                            blocks.add(ContentBlock.Image(src, alt.ifBlank { null }))
                        }
                    } else {
                        if (text.isNotBlank()) {
                            blocks.add(ContentBlock.Paragraph(text = text, html = el.html()))
                        }
                        if (img != null) {
                            val src = img.attr("src").ifBlank { img.attr("data-src") }.trim()
                            val alt = img.attr("alt").trim()
                            if (src.isNotBlank() && !src.startsWith("data:") && seenImages.add(src)) {
                                blocks.add(ContentBlock.Image(src, alt.ifBlank { null }))
                            }
                        }
                    }
                    processedElements.add(el)
                }
            }
            "img" -> {
                if (el.parent()?.tagName()?.lowercase() !in listOf("figure", "p")) {
                    val src = el.attr("src").ifBlank { el.attr("data-src") }.trim()
                    val alt = el.attr("alt").trim()
                    if (src.isNotBlank() && !src.startsWith("data:") && seenImages.add(src)) {
                        blocks.add(ContentBlock.Image(src, alt.ifBlank { null }))
                    }
                    processedElements.add(el)
                }
            }
            "blockquote" -> {
                val text = el.text().trim()
                if (text.isNotBlank()) {
                    blocks.add(ContentBlock.Quote(text))
                    processedElements.addAll(el.getAllElements())
                }
            }
            "ul", "ol" -> {
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
private fun VideoCardBlock(
    block: ContentBlock.Video,
    onVideoClick: (url: String) -> Unit
) {
    val colors = SignalTheme.colors
    val haptics = rememberSignalHaptics()

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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.card)
                .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                .clickable {
                    haptics.pop()
                    onVideoClick(block.videoUrl)
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
private fun PillButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = SignalTheme.colors

    Box(
        modifier = Modifier
            .alpha(if (enabled) 1.0f else 0.35f)
            .clip(CircleShape)
            .background(colors.bg)
            .border(1.dp, colors.ink, CircleShape)
            .signalPressScale(if (enabled) 0.96f else 1.0f) {
                if (enabled) onClick()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = colors.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
