package com.signal.reader.ui.reader

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.BackChevron
import com.signal.reader.ui.components.BookmarkIcon
import com.signal.reader.ui.components.TimeUtils
import com.signal.reader.ui.theme.SignalTheme
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    articleId: Long,
    repository: FeedRepository,
    onBack: () -> Unit,
    onOpenSourcePage: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = SignalTheme.colors
    val coroutineScope = rememberCoroutineScope()

    val article by repository.getArticleByIdFlow(articleId).collectAsState(initial = null)

    // Mark as read immediately on open
    LaunchedEffect(articleId) {
        repository.markArticleRead(articleId)
    }

    val listState = rememberLazyListState()

    // Reading progress calculation
    val readingProgress by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems <= 1) 0f
            else {
                val visible = listState.firstVisibleItemIndex.toFloat()
                (visible / (totalItems - 1)).coerceIn(0f, 1f)
            }
        }
    }

    // Header visibility on scroll (hides on scroll down after 90px, returns on scroll up)
    var isHeaderVisible by remember { mutableStateOf(true) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState.firstVisibleItemScrollOffset, listState.firstVisibleItemIndex) {
        val currentOffset = listState.firstVisibleItemIndex * 1000 + listState.firstVisibleItemScrollOffset
        if (currentOffset > previousScrollOffset + 30 && currentOffset > 200) {
            isHeaderVisible = false
        } else if (currentOffset < previousScrollOffset - 10) {
            isHeaderVisible = true
        }
        previousScrollOffset = currentOffset
    }

    // Font size steps: 16, 18, 20, 23
    val fontSizes = listOf(16.sp, 18.sp, 20.sp, 23.sp)
    var fontSizeIndex by remember { mutableIntStateOf(1) }
    var isAaPanelOpen by remember { mutableStateOf(false) }

    val currentArticle = article ?: return

    fun openInCustomTabs(url: String) {
        if (url.isNotBlank()) {
            try {
                val intent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                intent.launchUrl(context, Uri.parse(url))
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top reading-progress bar (sticky 2px ink line)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .statusBarsPadding()
                    .background(colors.line)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(readingProgress)
                        .height(2.dp)
                        .background(colors.ink)
                )
            }

            // Article Content
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Square 1:1 image or flat card with Category Tag pill
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(26.dp))
                            .background(colors.card)
                    ) {
                        if (!currentArticle.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = currentArticle.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Category tag pill
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

                // Title
                item {
                    Text(
                        text = currentArticle.title.uppercase(),
                        style = SignalTheme.typography.readerTitle,
                        color = colors.ink,
                        modifier = Modifier.padding(top = 22.dp, bottom = 22.dp)
                    )
                }

                // Facts DL block
                item {
                    FactsBlock(
                        article = currentArticle,
                        onSourceClick = { onOpenSourcePage(currentArticle.sourceId) }
                    )
                }

                // Body content
                item {
                    Spacer(modifier = Modifier.height(26.dp))
                    ArticleBody(
                        article = currentArticle,
                        fontSize = fontSizes[fontSizeIndex],
                        onReadFullClick = { openInCustomTabs(currentArticle.url) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }

            // Aa Settings Sheet
            AnimatedVisibility(
                visible = isAaPanelOpen,
                enter = slideInVertically(initialOffsetY = { it })
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bg)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Text size",
                        color = colors.mute,
                        fontSize = 13.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(
                            text = "A−",
                            enabled = fontSizeIndex > 0,
                            onClick = { if (fontSizeIndex > 0) fontSizeIndex-- }
                        )
                        PillButton(
                            text = "A+",
                            enabled = fontSizeIndex < fontSizes.size - 1,
                            onClick = { if (fontSizeIndex < fontSizes.size - 1) fontSizeIndex++ }
                        )
                    }
                }
            }

            // Bottom action bar: Save | Aa | Open site ↗
            HorizontalDivider(color = colors.line, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .navigationBarsPadding()
                    .height(56.dp)
            ) {
                // Save button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            coroutineScope.launch {
                                repository.toggleSaved(currentArticle.id)
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
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Save",
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
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { isAaPanelOpen = !isAaPanelOpen },
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
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { openInCustomTabs(currentArticle.url) },
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

        // Back Chevron Header (hides on scroll down, returns on scroll up)
        val headerOffsetY by animateFloatAsState(
            targetValue = if (isHeaderVisible) 0f else -100f,
            animationSpec = tween(280),
            label = "headerScroll"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, headerOffsetY.roundToInt()) }
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.bg.copy(alpha = 0.9f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                // Back chevron (<)
                BackChevron(color = colors.ink)
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
        horizontalArrangement = Arrangement.Start
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

@Composable
private fun ArticleBody(
    article: ArticleEntity,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onReadFullClick: () -> Unit
) {
    val colors = SignalTheme.colors

    val paragraphs = remember(article.fullContent, article.excerpt) {
        val raw = article.fullContent ?: article.excerpt
        val doc = Jsoup.parse(raw)
        val pTags = doc.select("p").map { it.text().trim() }.filter { it.isNotBlank() }
        if (pTags.isNotEmpty()) pTags else raw.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
    }

    if (article.isExcerptOnly) {
        // Excerpt-only state: first two paragraphs + 110px fade + CTA
        val excerptParagraphs = paragraphs.take(2)
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                excerptParagraphs.forEach { text ->
                    Text(
                        text = text,
                        style = SignalTheme.typography.readerBody(fontSize),
                        color = colors.ink,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }

            // 110px gradient fade to background
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

        // CTA: Read full article ↗
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.ink)
                    .clickable { onReadFullClick() }
                    .padding(horizontal = 26.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Read full article",
                        color = colors.bg,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "↗",
                        color = colors.accent,
                        fontSize = 18.sp
                    )
                }
            }

            Text(
                text = "This site only shares an excerpt in its feed. The full article opens inside the app.",
                color = colors.mute,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    } else {
        // Full-text state: show all paragraphs
        Column(modifier = Modifier.fillMaxWidth()) {
            paragraphs.forEach { text ->
                Text(
                    text = text,
                    style = SignalTheme.typography.readerBody(fontSize),
                    color = colors.ink,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
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
            .clip(CircleShape)
            .background(if (enabled) colors.bg else colors.bg.copy(alpha = 0.3f))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) colors.ink else colors.mute,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
