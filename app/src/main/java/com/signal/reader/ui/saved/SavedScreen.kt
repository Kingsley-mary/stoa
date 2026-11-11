package com.signal.reader.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.data.local.entity.HighlightEntity
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.ArticleRow
import com.signal.reader.ui.components.ShareStorySheet
import com.signal.reader.ui.components.StaggeredItem
import com.signal.reader.ui.components.TimeUtils
import com.signal.reader.ui.components.rememberSignalHaptics
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale
import kotlinx.coroutines.launch

@Composable
fun SavedScreen(
    repository: FeedRepository,
    onArticleClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val haptics = rememberSignalHaptics()
    val coroutineScope = rememberCoroutineScope()
    val savedArticles by repository.getSavedArticlesFlow().collectAsState(initial = emptyList())
    val highlights by repository.getAllHighlightsFlow().collectAsState(initial = emptyList())

    // 0 = Stories, 1 = Highlights
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    // 0 = All, 1 = Unread, 2 = Read
    var readFilter by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var highlightToShare by remember { mutableStateOf<HighlightEntity?>(null) }

    val filteredArticles = remember(savedArticles, readFilter, searchQuery) {
        val byRead = when (readFilter) {
            1 -> savedArticles.filter { !it.read }
            2 -> savedArticles.filter { it.read }
            else -> savedArticles
        }
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            byRead
        } else {
            byRead.filter {
                it.title.lowercase().contains(q) ||
                    it.sourceName.lowercase().contains(q) ||
                    it.excerpt.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
    ) {
        StaggeredItem(index = 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "SAVED",
                    style = SignalTheme.typography.screenTitle,
                    color = colors.ink
                )

                // Stories / Highlights Segmented Toggle
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(1.dp, colors.line, CircleShape)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        "Stories (${savedArticles.size})",
                        "Highlights (${highlights.size})"
                    )
                    tabs.forEachIndexed { idx, label ->
                        val active = selectedSection == idx
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (active) colors.ink else colors.bg)
                                .clickable {
                                    haptics.tick()
                                    selectedSection = idx
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (active) colors.bg else colors.mute,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        if (selectedSection == 0) {
            // Stories Section
            if (savedArticles.isEmpty()) {
                StaggeredItem(index = 1) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                        Text(
                            text = "Nothing saved yet. Swipe any story row to the right to bookmark it for offline reading.",
                            color = colors.mute,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )
                    }
                }
            } else {
                // Filter & Search bar row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterLabels = listOf("All", "Unread", "Read")
                    filterLabels.forEachIndexed { idx, label ->
                        val selected = readFilter == idx
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) colors.ink else colors.card)
                                .border(1.dp, if (selected) colors.ink else colors.line, CircleShape)
                                .clickable {
                                    haptics.tick()
                                    readFilter = idx
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selected) colors.bg else colors.ink
                            )
                        }
                    }

                    // Compact filter search box
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(colors.card)
                            .border(1.dp, colors.line, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = colors.ink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(colors.ink),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Filter saved…",
                                        color = colors.mute,
                                        fontSize = 12.sp
                                    )
                                }
                                inner()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (filteredArticles.isEmpty()) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp)) {
                        Text(
                            text = "No saved stories match your filter.",
                            color = colors.mute,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(filteredArticles, key = { _, item -> item.id }) { index, article ->
                            StaggeredItem(index = index + 1) {
                                ArticleRow(
                                    article = article,
                                    onClick = { onArticleClick(article.id) },
                                    onToggleSave = {
                                        coroutineScope.launch {
                                            repository.toggleSaved(article.id)
                                        }
                                    },
                                    onToggleRead = {
                                        coroutineScope.launch {
                                            repository.toggleRead(article.id)
                                        }
                                    }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        } else {
            // Highlights Section
            if (highlights.isEmpty()) {
                StaggeredItem(index = 1) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                        Text(
                            text = "No highlights yet. Select any passage while reading an article and tap “Highlight” to save it here.",
                            color = colors.mute,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(highlights, key = { _, item -> "hl_${item.id}" }) { index, hl ->
                        StaggeredItem(index = index + 1) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onArticleClick(hl.articleId) }
                            ) {
                                HorizontalDivider(color = colors.line, thickness = 1.dp)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(colors.accent)
                                        )
                                        Text(
                                            text = "“${hl.quoteText}”",
                                            color = colors.ink,
                                            fontSize = 16.sp,
                                            fontStyle = FontStyle.Italic,
                                            lineHeight = 24.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${hl.sourceName.uppercase()} · ${TimeUtils.formatRelativeTime(hl.createdAt)}",
                                                style = SignalTheme.typography.metaSource,
                                                color = colors.mute,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = hl.articleTitle,
                                                color = colors.ink,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .border(1.dp, colors.line, CircleShape)
                                                    .signalPressScale(0.95f)
                                                    .clickable {
                                                        haptics.tick()
                                                        highlightToShare = hl
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = "Share card",
                                                    color = colors.ink,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        haptics.tick()
                                                        coroutineScope.launch {
                                                            repository.deleteHighlight(hl.id)
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "✕",
                                                    color = colors.mute,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        HorizontalDivider(color = colors.line, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    val activeHighlight = highlightToShare
    if (activeHighlight != null) {
        val highlightArticle = remember(activeHighlight, savedArticles) {
            savedArticles.firstOrNull { it.id == activeHighlight.articleId }
                ?: com.signal.reader.data.local.entity.ArticleEntity(
                    id = activeHighlight.articleId,
                    sourceId = 0L,
                    sourceName = activeHighlight.sourceName,
                    title = activeHighlight.articleTitle,
                    excerpt = activeHighlight.quoteText,
                    fullContent = activeHighlight.quoteText,
                    url = "",
                    imageUrl = null,
                    author = null,
                    publishedTime = activeHighlight.createdAt,
                    guid = "highlight_${activeHighlight.id}"
                )
        }
        ShareStorySheet(
            article = highlightArticle,
            paragraphs = listOf(activeHighlight.quoteText),
            initialQuote = activeHighlight.quoteText,
            onDismiss = { highlightToShare = null }
        )
    }
}
