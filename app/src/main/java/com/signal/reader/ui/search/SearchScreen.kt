package com.signal.reader.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.scale
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
import com.signal.reader.ui.components.StaggeredItem
import com.signal.reader.ui.components.TimeUtils
import com.signal.reader.ui.components.rememberSignalHaptics
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale
import kotlinx.coroutines.delay
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
    val haptics = rememberSignalHaptics()
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
                    .signalPressScale(0.96f)
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

            // #clear button (28x28 circle with ×, pops in)
            if (query.isNotEmpty()) {
                val clearScale = remember { Animatable(0.6f) }
                LaunchedEffect(Unit) {
                    clearScale.animateTo(1f, SignalMotion.PressSpringSpec)
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .scale(clearScale.value)
                        .clip(CircleShape)
                        .border(1.dp, colors.ink, CircleShape)
                        .signalPressScale(0.96f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptics.tick()
                            query = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "×",
                        color = colors.ink,
                        fontSize = 16.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        HorizontalDivider(color = colors.line, thickness = 1.dp)

        if (query.isBlank()) {
            // Recent Searches view
            val recents = settings.recentSearches
            if (recents.isEmpty()) {
                Box(modifier = Modifier.padding(48.dp, 20.dp)) {
                    Text(
                        text = "Searches you make will show up here.",
                        color = colors.mute,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
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
                        modifier = Modifier
                            .signalPressScale(0.96f)
                            .clickable {
                                haptics.tick()
                                settingsManager.clearRecentSearches()
                            }
                    )
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(recents, key = { _, q -> q }) { index, recentQuery ->
                        var isCollapsing by remember { mutableStateOf(false) }

                        AnimatedVisibility(
                            visible = !isCollapsing,
                            exit = shrinkVertically(tween(220, easing = SignalMotion.EaseOutQuart)) + fadeOut(tween(200))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = recentQuery,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.ink,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            haptics.tick()
                                            query = recentQuery
                                        }
                                        .padding(vertical = 14.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .signalPressScale(0.96f)
                                        .clickable {
                                            haptics.tick()
                                            isCollapsing = true
                                            coroutineScope.launch {
                                                delay(220)
                                                settingsManager.removeRecentSearch(recentQuery)
                                            }
                                        },
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
                }
            }
        } else {
            // Search Results view
            if (searchResults.isEmpty()) {
                // Empty search result state (.empty)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "NO ARTICLES MATCH “${query.trim()}”",
                        style = SignalTheme.typography.heroHeadline,
                        color = colors.ink,
                        fontSize = 26.sp,
                        lineHeight = 28.sp
                    )
                    Text(
                        text = "Search covers recent articles from your sources. Try a shorter word or check the spelling.",
                        color = colors.mute,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }
            } else {
                Text(
                    text = "${searchResults.size} ${if (searchResults.size == 1) "result" else "results"} for “${query.trim()}”",
                    color = colors.mute,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(searchResults, key = { _, item -> item.id }) { index, article ->
                        StaggeredItem(index = index) {
                            SearchResultRow(
                                article = article,
                                queryTerms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() },
                                onClick = {
                                    submitSearch(query)
                                    onArticleClick(article.id)
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
    }
}

@Composable
private fun SearchResultRow(
    article: ArticleEntity,
    queryTerms: List<String>,
    onClick: () -> Unit
) {
    val colors = SignalTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val thumbScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = SignalMotion.PressSpringSpec,
        label = "searchThumbScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .signalPressScale(0.985f)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
    ) {
        HorizontalDivider(color = colors.line, thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .alpha(if (article.read) 0.45f else 1.0f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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

                // Highlighted Title
                Text(
                    text = highlightMatches(article.title, queryTerms),
                    style = SignalTheme.typography.rowHeadline,
                    color = colors.ink
                )

                // Highlighted Excerpt (if exists)
                if (article.excerpt.isNotBlank()) {
                    Text(
                        text = highlightMatches(article.excerpt, queryTerms),
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        color = colors.mute,
                        maxLines = 2
                    )
                }
            }

            // Thumbnail (88x88)
            if (!article.imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .scale(thumbScale)
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

/**
 * Renders text with 2px underline mark style for matched search query terms
 * matching HTML mockup: mark { text-decoration: underline; text-decoration-thickness: 2px; text-underline-offset: 3px; }
 */
private fun highlightMatches(text: String, queryTerms: List<String>): androidx.compose.ui.text.AnnotatedString {
    if (queryTerms.isEmpty()) return androidx.compose.ui.text.AnnotatedString(text)

    val lowerText = text.lowercase()
    val matchRanges = mutableListOf<IntRange>()

    for (term in queryTerms) {
        val lowerTerm = term.lowercase()
        var startIndex = 0
        while (startIndex < lowerText.length) {
            val idx = lowerText.indexOf(lowerTerm, startIndex)
            if (idx == -1) break
            matchRanges.add(idx until (idx + lowerTerm.length))
            startIndex = idx + lowerTerm.length
        }
    }

    return buildAnnotatedString {
        for (i in text.indices) {
            val isMatched = matchRanges.any { i in it }
            if (isMatched) {
                withStyle(
                    SpanStyle(
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(text[i])
                }
            } else {
                append(text[i])
            }
        }
    }
}
