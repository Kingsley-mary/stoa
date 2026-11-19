package com.signal.reader.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.signal.reader.data.feed.ImageResolver
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.data.local.entity.SourceEntity
import com.signal.reader.ui.components.BookmarkIcon
import com.signal.reader.ui.components.SourceLogo
import com.signal.reader.ui.components.TimeUtils
import com.signal.reader.ui.components.getSourceInitials
import com.signal.reader.ui.components.rememberSignalHaptics
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale
import com.signal.reader.ui.theme.signalSharedImage
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full-bleed vertical editorial Deck Mode:
 * - TikTok-style VerticalPager snapping with stacked depth parallax
 * - Swipe Right on a card to Save / Unsave (with physical tilt + stamp)
 * - Swipe Left on a card to Mark Read / Unread (auto-advances to next card)
 * - Tap card to launch cubic-bezier shared-element transition into ReaderScreen
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DeckFeedView(
    articles: List<ArticleEntity>,
    sourcesById: Map<Long, SourceEntity>,
    homeReselectTrigger: Int,
    onArticleClick: (Long) -> Unit,
    onToggleSave: (ArticleEntity) -> Unit,
    onToggleRead: (ArticleEntity) -> Unit,
    onRefresh: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val haptics = rememberSignalHaptics()
    val coroutineScope = rememberCoroutineScope()

    if (articles.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(SignalTheme.spacing.xl),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SignalTheme.spacing.md)
            ) {
                Text(
                    text = "ALL CAUGHT UP",
                    style = SignalTheme.typography.heroHeadline,
                    fontSize = 26.sp,
                    color = colors.ink
                )
                Text(
                    text = "No stories match this filter right now.",
                    color = colors.mute,
                    fontSize = 14.sp
                )
                Box(
                    modifier = Modifier
                        .clip(SignalTheme.shapes.pill)
                        .background(colors.ink)
                        .signalPressScale(0.96f) { onRefresh() }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Refresh feeds",
                        color = colors.bg,
                        style = SignalTheme.typography.pillLabel
                    )
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { articles.size })
    var lastHapticPage by remember { mutableIntStateOf(0) }
    var hasSwipedAny by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != lastHapticPage) {
            lastHapticPage = pagerState.currentPage
            hasSwipedAny = true
            haptics.tick()
        }
    }

    LaunchedEffect(homeReselectTrigger) {
        if (homeReselectTrigger > 0 && articles.isNotEmpty()) {
            pagerState.animateScrollToPage(0, animationSpec = SignalMotion.easeTween(360))
        }
    }

    VerticalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        pageSpacing = SignalTheme.spacing.md,
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 6.dp,
            bottom = 14.dp
        ),
        key = { index -> articles.getOrNull(index)?.id ?: index },
        modifier = modifier.fillMaxSize()
    ) { page ->
        val article = articles[page]
        val sourceSiteUrl = sourcesById[article.sourceId]?.siteUrl.orEmpty()
        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            .coerceIn(-1f, 1f)
        val absOffset = abs(pageOffset)
        val deckScale = 1f - (absOffset * 0.06f)
        val deckAlpha = 1f - (absOffset * 0.30f)

        DeckStoryCard(
            article = article,
            sourceSiteUrl = sourceSiteUrl,
            cardIndex = page,
            totalCount = articles.size,
            showGestureHint = page == 0 && !hasSwipedAny,
            pageOffset = pageOffset,
            deckScale = deckScale,
            deckAlpha = deckAlpha,
            onClick = { onArticleClick(article.id) },
            onSwipeSave = {
                hasSwipedAny = true
                onToggleSave(article)
            },
            onSwipeRead = {
                hasSwipedAny = true
                val wasUnread = !article.read
                onToggleRead(article)
                if (wasUnread && page < articles.lastIndex) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            page = (page + 1).coerceAtMost(articles.lastIndex),
                            animationSpec = SignalMotion.easeTween(320)
                        )
                    }
                }
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DeckStoryCard(
    article: ArticleEntity,
    sourceSiteUrl: String,
    cardIndex: Int,
    totalCount: Int,
    showGestureHint: Boolean,
    pageOffset: Float,
    deckScale: Float,
    deckAlpha: Float,
    onClick: () -> Unit,
    onSwipeSave: () -> Unit,
    onSwipeRead: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val colors = SignalTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = rememberSignalHaptics()
    val coroutineScope = rememberCoroutineScope()

    val effectiveImage = remember(article.id, article.imageUrl, article.fullContent, article.excerpt) {
        ImageResolver.getEffectiveCoverImage(article.imageUrl, article.fullContent, article.excerpt)
    }
    val hasImage = !effectiveImage.isNullOrBlank()

    val cleanExcerpt = remember(article.id, article.excerpt) {
        runCatching { Jsoup.parse(article.excerpt).text().trim() }.getOrDefault(article.excerpt.trim())
    }
    val readMinutes = remember(article.id, article.fullContent, article.excerpt) {
        TimeUtils.calculateReadTimeMinutes(article.fullContent ?: article.excerpt)
    }

    val offsetX = remember(article.id) { Animatable(0f) }
    var armedRight by remember(article.id) { mutableStateOf(false) }
    var armedLeft by remember(article.id) { mutableStateOf(false) }
    var hapticFired by remember(article.id) { mutableStateOf(false) }

    val thresholdPx = with(density) { 96.dp.toPx() }
    val limitPx = with(density) { 140.dp.toPx() }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = SignalMotion.PressSpringSpec,
        label = "deckCardPress"
    )

    val swipeRotation = (offsetX.value / with(density) { 42.dp.toPx() }).coerceIn(-7.5f, 7.5f)
    val swipeProgressRight = (offsetX.value / thresholdPx).coerceIn(0f, 1f)
    val swipeProgressLeft = (-offsetX.value / thresholdPx).coerceIn(0f, 1f)

    // Dynamic editorial headline sizing based on title length
    val titleLen = article.title.length
    val headlineFontSize = when {
        titleLen > 105 -> 21.sp
        titleLen > 75 -> 24.sp
        titleLen > 50 -> 27.sp
        else -> 31.sp
    }
    val headlineLineHeight = when {
        titleLen > 105 -> 24.sp
        titleLen > 75 -> 27.sp
        titleLen > 50 -> 30.sp
        else -> 33.sp
    }

    val cardShape = SignalTheme.shapes.hero
    val creamInk = Color(0xFFF6F3EB)
    val creamMute = Color(0xFFD1CBC0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .graphicsLayer {
                val combinedScale = deckScale * pressScale
                scaleX = combinedScale
                scaleY = combinedScale
                alpha = deckAlpha
                rotationZ = swipeRotation
            }
            .clip(cardShape)
            .background(Color(0xFF161513))
            .border(
                width = if (armedRight || armedLeft) 2.dp else 1.dp,
                color = when {
                    armedRight -> colors.accent
                    armedLeft -> creamInk.copy(alpha = 0.85f)
                    else -> Color.White.copy(alpha = 0.10f)
                },
                shape = cardShape
            )
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    coroutineScope.launch {
                        val raw = offsetX.value + delta
                        val damped = when {
                            raw > limitPx -> limitPx + (raw - limitPx) * 0.25f
                            raw < -limitPx -> -limitPx + (raw + limitPx) * 0.25f
                            else -> raw
                        }
                        offsetX.snapTo(damped)

                        val nowRight = damped >= thresholdPx
                        val nowLeft = damped <= -thresholdPx
                        if (nowRight != armedRight || nowLeft != armedLeft) {
                            armedRight = nowRight
                            armedLeft = nowLeft
                            if ((nowRight || nowLeft) && !hapticFired) {
                                haptics.tick()
                                hapticFired = true
                            } else if (!nowRight && !nowLeft) {
                                hapticFired = false
                            }
                        }
                    }
                },
                onDragStopped = {
                    coroutineScope.launch {
                        val fireSave = armedRight
                        val fireRead = armedLeft
                        armedRight = false
                        armedLeft = false
                        hapticFired = false
                        if (fireSave) {
                            haptics.pop()
                            onSwipeSave()
                        } else if (fireRead) {
                            haptics.pop()
                            onSwipeRead()
                        }
                        offsetX.animateTo(0f, SignalMotion.SwipeSpringSpec)
                    }
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptics.tick()
                onClick()
            }
    ) {
        // 1. Full-bleed cover image OR Swiss typographic editorial poster fallback
        if (hasImage) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(effectiveImage)
                    .crossfade(true)
                    .build(),
                contentDescription = article.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Subtle vertical parallax on cover photography while swiping deck
                        scaleX = 1.06f
                        scaleY = 1.06f
                        translationY = pageOffset * 48f
                    }
                    .signalSharedImage(
                        key = "article_image_${article.id}",
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        shape = cardShape
                    )
            )
        } else {
            // Architectural poster fallback for text-only feeds
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF23211D),
                                Color(0xFF151412),
                                Color(0xFF1D1A16)
                            )
                        )
                    )
            ) {
                Text(
                    text = getSourceInitials(article.sourceName),
                    style = SignalTheme.typography.screenTitle,
                    fontSize = 160.sp,
                    lineHeight = 160.sp,
                    color = creamInk.copy(alpha = 0.055f),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 18.dp, bottom = 90.dp)
                )
            }
        }

        // 2. Editorial top & bottom scrim gradients for effortless contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color(0x7310100F),
                        0.22f to Color.Transparent,
                        0.46f to Color(0x2610100F),
                        0.72f to Color(0xCC10100F),
                        1.00f to Color(0xF510100F)
                    )
                )
        )

        // 3. Top floating metadata bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(CircleShape)
                    .background(Color(0x9910100F))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                    .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp)
            ) {
                SourceLogo(
                    siteUrl = sourceSiteUrl,
                    sourceName = article.sourceName,
                    size = 22.dp,
                    shape = CircleShape,
                    fontSize = 9.sp
                )
                Text(
                    text = article.sourceName,
                    color = creamInk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "· ${TimeUtils.formatRelativeTime(article.publishedTime)}",
                    color = creamMute,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (article.saved) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        BookmarkIcon(
                            isFilled = true,
                            color = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x9910100F))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${cardIndex + 1} / $totalCount",
                        color = creamInk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 4. Horizontal swipe stamp overlays (Right = Save, Left = Read)
        if (swipeProgressRight > 0.08f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 22.dp)
                    .alpha(swipeProgressRight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (armedRight) colors.accent else Color(0xCC10100F))
                    .border(1.5.dp, if (armedRight) Color.White else colors.accent, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BookmarkIcon(
                        isFilled = !article.saved,
                        color = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (article.saved) "UNSAVE" else "SAVE",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        if (swipeProgressLeft > 0.08f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 22.dp)
                    .alpha(swipeProgressLeft)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (armedLeft) creamInk else Color(0xCC10100F))
                    .border(1.5.dp, creamInk, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (article.read) "MARK UNREAD" else "MARK READ",
                    color = if (armedLeft) Color(0xFF10100F) else creamInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        }

        // 5. Bottom Editorial Lockup (Status tag, bold uppercase headline, excerpt, action bar)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!article.read) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                    )
                }
                Text(
                    text = if (article.read) "READ · $readMinutes MIN" else "UNREAD · $readMinutes MIN READ",
                    color = if (article.read) creamMute else colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
            }

            Text(
                text = article.title.uppercase(),
                style = SignalTheme.typography.heroHeadline.copy(
                    fontSize = headlineFontSize,
                    lineHeight = headlineLineHeight
                ),
                color = creamInk,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            if (cleanExcerpt.isNotBlank()) {
                Text(
                    text = cleanExcerpt,
                    color = creamMute,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Quick interactive action footer + subtle gesture cue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showGestureHint) "↕ Swipe · → Save · ← Read" else "Tap to read →",
                    color = creamInk.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Save pill
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (article.saved) colors.accent else Color.White.copy(alpha = 0.14f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .signalPressScale(0.92f) {
                                haptics.tick()
                                onSwipeSave()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            BookmarkIcon(
                                isFilled = article.saved,
                                color = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (article.saved) "Saved" else "Save",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Quick Mark Read / Unread pill
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .signalPressScale(0.92f) {
                                haptics.tick()
                                onSwipeRead()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (article.read) "Unread" else "Read ✓",
                            color = creamInk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
