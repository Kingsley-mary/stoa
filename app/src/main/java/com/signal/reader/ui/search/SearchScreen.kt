package com.signal.reader.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.signal.reader.data.local.SettingsManager
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.BackChevron
import com.signal.reader.ui.components.TimeUtils
import com.signal.reader.ui.theme.SignalTheme
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    repository: FeedRepository,
    settingsManager: SettingsManager,
    onArticleClick: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val coroutineScope = rememberCoroutineScope()
    val settings by settingsManager.settingsFlow.collectAsState()

    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ArticleEntity>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            searchResults = repository.searchArticles(query)
        } else {
            searchResults = emptyList()
        }
    }

    fun submitSearch(q: String) {
        if (q.isNotBlank()) {
            settingsManager.addRecentSearch(q)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
    ) {
        // Search Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                BackChevron(color = colors.ink)
            }

            BasicTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = TextStyle(
                    color = colors.ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.01).sp
                ),
                cursorBrush = SolidColor(colors.ink),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch(query) }),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search articles",
                            color = colors.mute,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )

            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            query = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "×",
                        color = colors.ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }
        }

        HorizontalDivider(color = colors.line, thickness = 1.dp)

        if (query.isBlank()) {
            // Before typing: Recent searches (up to 6)
            if (settings.recentSearches.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent searches",
                        color = colors.mute,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Clear all",
                        color = colors.ink,
                        fontSize = 13.sp,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { settingsManager.clearRecentSearches() }
                    )
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(settings.recentSearches) { recent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    query = recent
                                    submitSearch(recent)
                                }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = recent,
                                color = colors.ink,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { settingsManager.removeRecentSearch(recent) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "×",
                                    color = colors.mute,
                                    fontSize = 20.sp
                                )
                            }
                        }
                        HorizontalDivider(color = colors.line, thickness = 1.dp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "Searches you make will show up here.",
                        color = colors.mute,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            // Results or Zero Results
            if (searchResults.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "No articles match “$query”",
                        style = SignalTheme.typography.heroHeadline,
                        color = colors.ink
                    )
                    Text(
                        text = "Search covers recent articles from your sources. Try a shorter word or check the spelling.",
                        color = colors.mute,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }
            } else {
                val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = "${searchResults.size} ${if (searchResults.size == 1) "result" else "results"} for “$query”",
                            color = colors.mute,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )
                    }

                    items(searchResults, key = { it.id }) { article ->
                        SearchResultRow(
                            article = article,
                            searchWords = words,
                            onClick = {
                                submitSearch(query)
                                onArticleClick(article.id)
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
}

@Composable
private fun SearchResultRow(
    article: ArticleEntity,
    searchWords: List<String>,
    onClick: () -> Unit
) {
    val colors = SignalTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        HorizontalDivider(color = colors.line, thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .alpha(if (article.read) 0.45f else 1.0f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Meta
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                        text = article.sourceName,
                        style = SignalTheme.typography.metaSource,
                        color = colors.ink
                    )
                    Text(
                        text = TimeUtils.formatRelativeTime(article.publishedTime),
                        style = SignalTheme.typography.metaTime,
                        color = colors.mute
                    )
                }

                // Title with matched words underlined
                Text(
                    text = highlightUnderline(article.title, searchWords),
                    style = SignalTheme.typography.rowHeadline,
                    color = colors.ink
                )

                // Excerpt with matched words underlined
                if (article.excerpt.isNotBlank()) {
                    Text(
                        text = highlightUnderline(article.excerpt, searchWords),
                        color = colors.mute,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        maxLines = 2
                    )
                }
            }

            // Thumbnail
            if (!article.imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.card)
                ) {
                    AsyncImage(
                        model = article.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// Helper to underline matched search words per brief: "Matched words are underlined, not coloured"
private fun highlightUnderline(text: String, words: List<String>): androidx.compose.ui.text.AnnotatedString {
    if (words.isEmpty() || text.isBlank()) return androidx.compose.ui.text.AnnotatedString(text)

    val lowerText = text.lowercase()
    val ranges = mutableListOf<IntRange>()

    for (word in words) {
        var startIndex = 0
        while (startIndex < lowerText.length) {
            val found = lowerText.indexOf(word, startIndex)
            if (found >= 0) {
                ranges.add(found until (found + word.length))
                startIndex = found + word.length
            } else {
                break
            }
        }
    }

    val sortedRanges = ranges.sortedBy { it.first }

    return buildAnnotatedString {
        var currentIdx = 0
        for (range in sortedRanges) {
            if (range.first >= currentIdx) {
                append(text.substring(currentIdx, range.first))
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(text.substring(range.first, minOf(range.last + 1, text.length)))
                }
                currentIdx = minOf(range.last + 1, text.length)
            }
        }
        if (currentIdx < text.length) {
            append(text.substring(currentIdx))
        }
    }
}
