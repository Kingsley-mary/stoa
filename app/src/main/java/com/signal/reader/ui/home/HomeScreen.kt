package com.signal.reader.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.ArticleRow
import com.signal.reader.ui.components.HeroCard
import com.signal.reader.ui.components.SignalHeader
import com.signal.reader.ui.components.SourceChips
import com.signal.reader.ui.components.StaggeredItem
import com.signal.reader.ui.components.rememberSignalHaptics
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: FeedRepository,
    onArticleClick: (Long) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddSourcesClick: () -> Unit,
    isCompactLayout: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val density = LocalDensity.current
    val haptics = rememberSignalHaptics()
    val coroutineScope = rememberCoroutineScope()

    val sources by repository.getFollowedSourcesFlow().collectAsState(initial = emptyList())
    val allArticles by repository.getHomeStreamFlow().collectAsState(initial = emptyList())

    var selectedSourceId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // First load word animation flag
    var hasAnimatedFirstLoad by remember { mutableStateOf(false) }

    // Chip swap animation state (.out then .in)
    val feedAlpha = remember { Animatable(1f) }
    val feedOffsetY = remember { Animatable(0f) }
    var shouldSlideHero by remember { mutableStateOf(false) }

    // Filter stream by active chip
    val filteredArticles = remember(allArticles, selectedSourceId) {
        if (selectedSourceId == null) {
            allArticles
        } else {
            allArticles.filter { it.sourceId == selectedSourceId }
        }
    }

    // Hero rule: newest article with image in active set; fallback to newest as flat card
    val heroArticle: ArticleEntity? = remember(filteredArticles, isCompactLayout) {
        if (isCompactLayout || filteredArticles.isEmpty()) null
        else filteredArticles.firstOrNull { !it.imageUrl.isNullOrBlank() } ?: filteredArticles.firstOrNull()
    }

    val streamArticles = remember(filteredArticles, heroArticle) {
        if (heroArticle != null) {
            filteredArticles.filter { it.id != heroArticle.id }
        } else {
            filteredArticles
        }
    }

    // Pull-to-refresh pull distance tracking (70px threshold)
    var pullDistance by remember { mutableFloatStateOf(0f) }
    var hasTickFired by remember { mutableStateOf(false) }
    val ptrThresholdPx = 70f * 2.5f

    // Pull to refresh scanline spin animation (@keyframes spin: 700ms linear infinite)
    val scanlineLeftFraction = remember { Animatable(0f) }
    val scanlineWidthFraction = remember { Animatable(0.1f) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (isActive && isRefreshing) {
                // 0% -> 50%: expand from left (0.1 to 1.0)
                scanlineLeftFraction.snapTo(0f)
                scanlineWidthFraction.snapTo(0.1f)
                scanlineWidthFraction.animateTo(1.0f, tween(350, easing = LinearEasing))

                // 50% -> 100%: contract to right (width 1.0 to 0.1, left 0 to 0.9)
                launch {
                    scanlineLeftFraction.animateTo(0.9f, tween(350, easing = LinearEasing))
                }
                scanlineWidthFraction.animateTo(0.1f, tween(350, easing = LinearEasing))
            }
        } else {
            scanlineLeftFraction.snapTo(0f)
            scanlineWidthFraction.snapTo(0f)
        }
    }

    fun triggerRefresh() {
        coroutineScope.launch {
            isRefreshing = true
            pullDistance = 0f
            val newCount = repository.refreshAllFollowedSources()
            delay(400) // minimum visible spin duration
            isRefreshing = false
            toastMessage = if (newCount > 0) "$newCount new articles" else "You’re up to date"
            haptics.pop()
            delay(1800)
            toastMessage = null
        }
    }

    fun onChipSelected(sourceId: Long?) {
        if (sourceId == selectedSourceId) return
        coroutineScope.launch {
            haptics.tick()
            // Feed swap out (.13s out: translateY(6px), opacity 0)
            feedAlpha.animateTo(0f, SignalMotion.easeTween(SignalMotion.FeedSwapOutDuration))
            feedOffsetY.snapTo(6f)
            selectedSourceId = sourceId
            shouldSlideHero = true

            // Feed swap in (.35s in)
            feedOffsetY.animateTo(0f, SignalMotion.easeTween(SignalMotion.FeedSwapInDuration))
            feedAlpha.animateTo(1f, SignalMotion.easeTween(SignalMotion.FeedSwapInDuration))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SignalHeader(
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                onMarkAllAsReadClick = {
                    coroutineScope.launch {
                        haptics.pop()
                        repository.markAllAsRead(selectedSourceId)
                    }
                },
                onLogoClick = { triggerRefresh() }
            )

            // Pull to refresh bar (.ptr / #ptrbar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.bg)
            ) {
                if (isRefreshing) {
                    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
                    val barWidthPx = screenWidthPx * scanlineWidthFraction.value
                    val barLeftPx = screenWidthPx * scanlineLeftFraction.value

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(barLeftPx.roundToInt(), 0) }
                            .fillMaxWidth(scanlineWidthFraction.value)
                            .height(2.dp)
                            .background(colors.ink)
                    )
                } else if (pullDistance > 0f) {
                    val progress = (pullDistance / ptrThresholdPx).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(2.dp)
                            .background(colors.ink)
                    )
                }
            }

            if (sources.isEmpty()) {
                EmptyHomeState(onAddSourcesClick = onAddSourcesClick)
            } else {
                // Source filtering chips with sliding indicator (.chips)
                SourceChips(
                    sources = sources,
                    selectedSourceId = selectedSourceId,
                    onSelectSource = { onChipSelected(it) }
                )

                val listState = rememberLazyListState()

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { triggerRefresh() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(feedAlpha.value)
                            .offset { IntOffset(0, (feedOffsetY.value * density.density).roundToInt()) }
                    ) {
                        // Hero Card
                        if (heroArticle != null) {
                            item(key = "hero_${heroArticle.id}") {
                                HeroCard(
                                    article = heroArticle,
                                    onClick = { onArticleClick(heroArticle.id) },
                                    animateWords = !hasAnimatedFirstLoad,
                                    slideIn = shouldSlideHero,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                                hasAnimatedFirstLoad = true
                            }
                        }

                        // Stream items with 40ms stagger entrance
                        itemsIndexed(streamArticles, key = { _, item -> item.id }) { index, article ->
                            StaggeredItem(index = index + 1) {
                                ArticleRow(
                                    article = article,
                                    onClick = { onArticleClick(article.id) },
                                    onToggleSave = {
                                        coroutineScope.launch {
                                            repository.toggleSaved(article.id)
                                        }
                                    },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }

        // Sliding Toast Pill (#toast)
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = SignalMotion.IntOffsetSpringSpec
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(200)
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 70.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.ink)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = toastMessage ?: "",
                    color = colors.bg,
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EmptyHomeState(
    onAddSourcesClick: () -> Unit
) {
    val colors = SignalTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 56.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "NO SOURCES YET",
            style = SignalTheme.typography.heroHeadline,
            color = colors.ink
        )
        Text(
            text = "Add a few sites and their articles will show up on Home.",
            color = colors.mute,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .clip(CircleShape)
                .background(colors.ink)
                .signalPressScale(0.96f)
                .clickable { onAddSourcesClick() }
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Add sources",
                color = colors.bg,
                style = SignalTheme.typography.pillLabel
            )
        }
    }
}
