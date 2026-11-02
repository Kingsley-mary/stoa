package com.signal.reader.ui.home

import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.signal.reader.data.local.SettingsManager
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
import com.signal.reader.ui.theme.SignalTactile
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPillButton
import com.signal.reader.ui.theme.signalPressScale
import com.signal.reader.ui.theme.signalTactileClick
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
    settingsManager: SettingsManager? = null,
    isCompactLayout: Boolean = false,
    homeReselectTrigger: Int = 0,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = SignalTheme.colors
    val density = LocalDensity.current
    val haptics = rememberSignalHaptics()
    val coroutineScope = rememberCoroutineScope()

    val actualSettingsManager = settingsManager ?: remember {
        (context.applicationContext as com.signal.reader.SignalApplication).settingsManager
    }

    val homeSettings by actualSettingsManager.settingsFlow.collectAsState()
    val isCardDismissed = homeSettings.openByDefaultCardDismissed
    var showDomainSetupDialog by remember { mutableStateOf(false) }

    val domainApprovalState = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val dvm = context.getSystemService(DomainVerificationManager::class.java)
                val userState = dvm?.getDomainVerificationUserState(context.packageName)
                val hostMap = userState?.hostToStateMap
                if (hostMap != null && hostMap.isNotEmpty()) {
                    val total = hostMap.size
                    val approved = hostMap.values.count {
                        it == DomainVerificationUserState.DOMAIN_STATE_VERIFIED ||
                        it == DomainVerificationUserState.DOMAIN_STATE_SELECTED
                    }
                    Triple(approved == total, approved, total)
                } else {
                    Triple(true, 0, 0)
                }
            } catch (_: Exception) {
                Triple(true, 0, 0)
            }
        } else {
            Triple(true, 0, 0)
        }
    }
    val (isAllApproved, approvedCount, totalCount) = domainApprovalState
    val shouldShowDomainCard = !isAllApproved && !isCardDismissed

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

    // Filter stream by active chip and sort Unread first
    val filteredArticles = remember(allArticles, selectedSourceId) {
        val base = if (selectedSourceId == null) {
            allArticles
        } else {
            allArticles.filter { it.sourceId == selectedSourceId }
        }
        base.sortedWith(compareBy<ArticleEntity> { it.read }.thenByDescending { it.publishedTime })
    }

    // Hero rule: newest unread article with image in active set; fallback to newest unread or newest with image
    val heroArticle: ArticleEntity? = remember(filteredArticles, isCompactLayout) {
        if (isCompactLayout || filteredArticles.isEmpty()) null
        else filteredArticles.firstOrNull { !it.read && !it.imageUrl.isNullOrBlank() && it.imageUrl != "none" }
            ?: filteredArticles.firstOrNull { !it.read }
            ?: filteredArticles.firstOrNull { !it.imageUrl.isNullOrBlank() && it.imageUrl != "none" }
            ?: filteredArticles.firstOrNull()
    }

    val streamArticles = remember(filteredArticles, heroArticle) {
        if (heroArticle != null) {
            filteredArticles.filter { it.id != heroArticle.id }
        } else {
            filteredArticles
        }
    }

    val listState = rememberLazyListState()

    val firstReadStreamIndex = remember(streamArticles) {
        streamArticles.indexOfFirst { it.read }
    }

    val targetReadListIndex = remember(shouldShowDomainCard, heroArticle, firstReadStreamIndex) {
        if (firstReadStreamIndex < 0) -1
        else {
            (if (shouldShowDomainCard) 1 else 0) +
            (if (heroArticle != null) 1 else 0) +
            firstReadStreamIndex
        }
    }

    val jumpToReadAction: (() -> Unit)? = remember(targetReadListIndex, listState) {
        if (targetReadListIndex > 0) {
            {
                coroutineScope.launch {
                    haptics.tick()
                    listState.animateScrollToItem(targetReadListIndex)
                }
            }
        } else null
    }

    var isScrollingDown by remember { mutableStateOf(false) }
    var lastItemIndex by remember { mutableIntStateOf(0) }
    var lastScrollOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            val currentIndex = listState.firstVisibleItemIndex
            val currentOffset = listState.firstVisibleItemScrollOffset
            if (currentIndex > lastItemIndex || (currentIndex == lastItemIndex && currentOffset > lastScrollOffset + 10)) {
                isScrollingDown = true
            } else if (currentIndex < lastItemIndex || (currentIndex == lastItemIndex && currentOffset < lastScrollOffset - 10)) {
                isScrollingDown = false
            }
            lastItemIndex = currentIndex
            lastScrollOffset = currentOffset
        } else {
            isScrollingDown = false
        }
    }

    val showJumpToRead by remember(targetReadListIndex, listState, isScrollingDown) {
        derivedStateOf {
            targetReadListIndex > 0 &&
                    listState.firstVisibleItemIndex < targetReadListIndex - 1 &&
                    !isScrollingDown
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

    LaunchedEffect(homeReselectTrigger) {
        if (homeReselectTrigger > 0) {
            haptics.tick()
            listState.animateScrollToItem(0)
            triggerRefresh()
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
                onLogoClick = { triggerRefresh() },
                onJumpToReadClick = jumpToReadAction
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
                        // Open Links by Default Card (Feature 3)
                        if (shouldShowDomainCard) {
                            item(key = "open_by_default_card") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 10.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(colors.card)
                                        .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                                        .clickable { showDomainSetupDialog = true }
                                        .padding(18.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Open links in Stoa",
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.ink,
                                                letterSpacing = (-0.01).sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Tap to open articles from your followed sites by default ($approvedCount of $totalCount approved).",
                                                fontSize = 13.sp,
                                                color = colors.mute,
                                                lineHeight = 18.sp
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .signalPressScale(0.92f) {
                                                    coroutineScope.launch {
                                                        actualSettingsManager.setOpenByDefaultCardDismissed(true)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "✕", fontSize = 14.sp, color = colors.mute)
                                        }
                                    }
                                }
                            }
                        }

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
                            val isFirstRead = article.read && (index == 0 || !streamArticles[index - 1].read)
                            if (isFirstRead) {
                                Column(modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)) {
                                    Text(
                                        text = "EARLIER READ",
                                        style = SignalTheme.typography.metaSource,
                                        color = colors.mute,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                    )
                                    androidx.compose.material3.HorizontalDivider(color = colors.line, thickness = 1.dp)
                                }
                            }
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

        // Floating Jump to Earlier Read Pill (Approach C: discreet bottom-right pill)
        AnimatedVisibility(
            visible = showJumpToRead,
            enter = fadeIn(tween(200)) + slideInVertically(
                initialOffsetY = { it },
                animationSpec = SignalMotion.easeTween(250)
            ),
            exit = fadeOut(tween(150)) + slideOutVertically(
                targetOffsetY = { it },
                animationSpec = SignalMotion.easeTween(200)
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .signalPillButton(
                        backgroundColor = colors.bg,
                        borderColor = colors.ink,
                        onClick = {
                            jumpToReadAction?.invoke()
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "Earlier read",
                        color = colors.ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "↓",
                        color = colors.ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
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

        if (showDomainSetupDialog) {
            AlertDialog(
                onDismissRequest = { showDomainSetupDialog = false },
                containerColor = colors.bg,
                titleContentColor = colors.ink,
                textContentColor = colors.mute,
                shape = RoundedCornerShape(22.dp),
                title = {
                    Text(
                        text = "Open links in Stoa",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                },
                text = {
                    Text(
                        text = "When you tap an article link in another app (like WhatsApp or email), Android can open it directly in Stoa instead of your web browser.\n\nIn Android Settings, toggle 'Open supported links' on.",
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = colors.mute
                    )
                },
                confirmButton = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.ink)
                            .signalPressScale(0.96f) {
                                showDomainSetupDialog = false
                                try {
                                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${context.packageName}"))
                                    } else {
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Open Settings", color = colors.bg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                },
                dismissButton = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .signalPressScale(0.96f) { showDomainSetupDialog = false }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Cancel", color = colors.mute, fontSize = 14.sp)
                    }
                }
            )
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
