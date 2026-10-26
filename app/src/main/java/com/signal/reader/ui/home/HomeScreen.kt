package com.signal.reader.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.ArticleRow
import com.signal.reader.ui.components.HeroCard
import com.signal.reader.ui.components.SignalHeader
import com.signal.reader.ui.components.SourceChips
import com.signal.reader.ui.theme.SignalTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: FeedRepository,
    onArticleClick: (Long) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddSourcesClick: () -> Unit,
    isCompactLayout: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val coroutineScope = rememberCoroutineScope()

    val sources by repository.getFollowedSourcesFlow().collectAsState(initial = emptyList())
    val allArticles by repository.getHomeStreamFlow().collectAsState(initial = emptyList())

    var selectedSourceId by remember { mutableStateOf<Long?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // Filter stream by active chip
    val filteredArticles = remember(allArticles, selectedSourceId) {
        if (selectedSourceId == null) {
            allArticles
        } else {
            allArticles.filter { it.sourceId == selectedSourceId }
        }
    }

    // Hero rule: newest article that has an image in the active set;
    // if none has an image, the newest article as a flat type card.
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

    fun triggerRefresh() {
        coroutineScope.launch {
            isRefreshing = true
            val newCount = repository.refreshAllFollowedSources()
            isRefreshing = false
            toastMessage = if (newCount > 0) "$newCount new articles" else "You’re up to date"
            delay(2000)
            toastMessage = null
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
                        repository.markAllAsRead(selectedSourceId)
                    }
                },
                onLogoClick = { triggerRefresh() }
            )

            if (sources.isEmpty()) {
                // Empty state: no sources followed yet
                EmptyHomeState(onAddSourcesClick = onAddSourcesClick)
            } else {
                // Source filtering chips
                SourceChips(
                    sources = sources,
                    selectedSourceId = selectedSourceId,
                    onSelectSource = { selectedSourceId = it }
                )

                val pullRefreshState = rememberPullToRefreshState()

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { triggerRefresh() },
                    state = pullRefreshState,
                    modifier = Modifier.weight(1f)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Hero Card (if layout is Cards and feed is not empty)
                        if (heroArticle != null) {
                            item(key = "hero_${heroArticle.id}") {
                                HeroCard(
                                    article = heroArticle,
                                    onClick = { onArticleClick(heroArticle.id) }
                                )
                            }
                        }

                        // Stream rows
                        items(
                            items = streamArticles,
                            key = { it.id }
                        ) { article ->
                            ArticleRow(
                                article = article,
                                onClick = { onArticleClick(article.id) },
                                onToggleSave = {
                                    coroutineScope.launch {
                                        repository.toggleSaved(article.id)
                                    }
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }

        // Pull to refresh Toast pill ("N new articles" or "You’re up to date")
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(300)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(300)
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp)
        ) {
            toastMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.ink)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = msg,
                        color = colors.bg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeState(onAddSourcesClick: () -> Unit) {
    val colors = SignalTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "No sources yet",
            style = SignalTheme.typography.heroHeadline,
            color = colors.ink
        )
        Text(
            text = "Add a few sites and their articles will show up on Home.",
            fontSize = 15.sp,
            color = colors.mute,
            lineHeight = 21.sp
        )
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .clip(CircleShape)
                .background(colors.ink)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onAddSourcesClick() }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Add sources",
                color = colors.bg,
                style = SignalTheme.typography.pillLabel
            )
        }
    }
}
