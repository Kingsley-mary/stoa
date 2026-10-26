package com.signal.reader.ui.sources

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.ArticleRow
import com.signal.reader.ui.components.BackChevron
import com.signal.reader.ui.components.HeroCard
import com.signal.reader.ui.components.TimeUtils
import com.signal.reader.ui.theme.SignalTheme
import kotlinx.coroutines.launch

@Composable
fun SourcePageScreen(
    sourceId: Long,
    repository: FeedRepository,
    onArticleClick: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = SignalTheme.colors
    val coroutineScope = rememberCoroutineScope()

    val source by repository.getSourceByIdFlow(sourceId).collectAsState(initial = null)
    val articles by repository.getArticlesBySourceFlow(sourceId).collectAsState(initial = emptyList())

    var showUnfollowConfirm by remember { mutableStateOf(false) }
    var statusNote by remember { mutableStateOf<String?>(null) }

    val currentSource = source ?: return

    val heroArticle = remember(articles) {
        articles.firstOrNull { !it.imageUrl.isNullOrBlank() } ?: articles.firstOrNull()
    }
    val streamArticles = remember(articles, heroArticle) {
        if (heroArticle != null) articles.filter { it.id != heroArticle.id } else articles
    }

    val unreadCount = remember(articles) {
        articles.count { !it.read }
    }

    fun openWebsite(url: String) {
        if (url.isNotBlank()) {
            try {
                val intent = CustomTabsIntent.Builder().setShowTitle(true).build()
                intent.launchUrl(context, Uri.parse(url))
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Header with Back Chevron
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                BackChevron(color = colors.ink)
            }
        }

        HorizontalDivider(color = colors.line, thickness = 1.dp)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Masthead Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    // 56x56 Initials Square
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.ink),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = getInitials(currentSource.name),
                            color = colors.bg,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }

                    // Source Name
                    Text(
                        text = currentSource.name.uppercase(),
                        style = SignalTheme.typography.screenTitle,
                        color = colors.ink,
                        modifier = Modifier.padding(top = 20.dp, bottom = 14.dp)
                    )

                    // Description
                    if (currentSource.description.isNotBlank()) {
                        Text(
                            text = currentSource.description,
                            color = colors.mute,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }

                    // Broken feed banner with Retry button
                    if (currentSource.isBroken) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.card)
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Feed isn’t loading",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = colors.ink
                                )
                                Text(
                                    text = currentSource.errorState ?: "Unable to fetch updates from this feed.",
                                    fontSize = 13.sp,
                                    color = colors.mute
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(colors.ink)
                                        .clickable {
                                            coroutineScope.launch {
                                                repository.refreshSource(currentSource.id)
                                            }
                                        }
                                        .padding(horizontal = 18.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Retry now",
                                        color = colors.bg,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Action Controls: Following / Mute pills
                    Row(
                        modifier = Modifier.padding(top = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Following / Follow pill
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (currentSource.followed) colors.bg else colors.ink)
                                .border(1.dp, colors.ink, CircleShape)
                                .clickable {
                                    if (currentSource.followed) {
                                        showUnfollowConfirm = true
                                    } else {
                                        coroutineScope.launch {
                                            repository.addSource(
                                                currentSource.name,
                                                currentSource.siteUrl,
                                                currentSource.feedUrl,
                                                currentSource.description
                                            )
                                            statusNote = null
                                        }
                                    }
                                }
                                .padding(horizontal = 22.dp, vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (currentSource.followed) "Following" else "Follow",
                                color = if (currentSource.followed) colors.ink else colors.bg,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }

                        // Mute pill
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (currentSource.muted) colors.ink else colors.bg)
                                .border(1.dp, colors.ink, CircleShape)
                                .clickable {
                                    val newMuted = !currentSource.muted
                                    coroutineScope.launch {
                                        repository.setMuted(currentSource.id, newMuted)
                                        statusNote = if (newMuted) {
                                            "Muted. New articles from this source won’t appear on Home."
                                        } else null
                                    }
                                }
                                .padding(horizontal = 22.dp, vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Mute",
                                color = if (currentSource.muted) colors.bg else colors.ink,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Inline confirmation box for Unfollowing
                    AnimatedVisibility(visible = showUnfollowConfirm) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(
                                    text = "Unfollow ${currentSource.name}? Articles you saved stay in Saved.",
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = colors.ink
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(colors.ink)
                                            .clickable {
                                                coroutineScope.launch {
                                                    repository.unfollowSource(currentSource.id)
                                                    showUnfollowConfirm = false
                                                    statusNote = "You’re not following ${currentSource.name}. New articles won’t appear on Home."
                                                }
                                            }
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "Unfollow",
                                            color = colors.bg,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .border(1.dp, colors.ink, CircleShape)
                                            .clickable { showUnfollowConfirm = false }
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "Cancel",
                                            color = colors.ink,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Status note if any
                    statusNote?.let { note ->
                        Text(
                            text = note,
                            color = colors.mute,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                }
            }

            // Facts Block: In this feed | Unread | Latest | Website
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    HorizontalDivider(color = colors.line, thickness = 1.dp)

                    SourceFactRow(label = "In this feed", value = "${articles.size}")
                    SourceFactRow(label = "Unread", value = "$unreadCount")
                    SourceFactRow(
                        label = "Latest",
                        value = if (articles.isNotEmpty()) TimeUtils.formatRelativeTime(articles.first().publishedTime) else "None"
                    )
                    SourceFactRow(
                        label = "Website",
                        valueContent = {
                            Text(
                                text = currentSource.siteUrl.removePrefix("https://").removePrefix("http://"),
                                color = colors.ink,
                                fontSize = 14.sp,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable { openWebsite(currentSource.siteUrl) }
                            )
                        }
                    )
                }
            }

            // Section Header
            item {
                Text(
                    text = "Latest from this source",
                    color = colors.mute,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 14.dp)
                )
            }

            // Hero article (source name omitted)
            if (heroArticle != null) {
                item(key = "source_hero_${heroArticle.id}") {
                    HeroCard(
                        article = heroArticle,
                        onClick = { onArticleClick(heroArticle.id) },
                        showSourceName = false
                    )
                }
            }

            // Stream rows
            items(streamArticles, key = { it.id }) { article ->
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

            // End of list message
            item {
                HorizontalDivider(color = colors.line, thickness = 1.dp)
                Text(
                    text = "That’s everything in this feed. Older articles aren’t available.",
                    color = colors.mute,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SourceFactRow(
    label: String,
    value: String? = null,
    valueContent: (@Composable () -> Unit)? = null
) {
    val colors = SignalTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = label,
            color = colors.mute,
            fontSize = 14.sp,
            modifier = Modifier.size(width = 130.dp, height = 20.dp)
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
