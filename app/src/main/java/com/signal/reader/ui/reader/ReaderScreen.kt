package com.signal.reader.ui.reader

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.signal.reader.data.local.SettingsManager
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.BackChevron
import com.signal.reader.ui.components.BookmarkIcon
import com.signal.reader.ui.components.StaggeredItem
import com.signal.reader.ui.components.TimeUtils
import com.signal.reader.ui.components.rememberSignalHaptics
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

    // Mark as read immediately on open
    LaunchedEffect(articleId) {
        repository.markArticleRead(articleId)
    }

    val listState = rememberLazyListState()

    // Reading progress calculation (tracks scroll from 0 to 1)
    val readingProgress by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems <= 1) 0f
            else {
                val firstIdx = listState.firstVisibleItemIndex.toFloat()
                val offset = listState.firstVisibleItemScrollOffset.toFloat()
                val progress = (firstIdx / (totalItems - 1)).coerceIn(0f, 1f)
                progress
            }
        }
    }

    // Header visibility on scroll with derivedStateOf to prevent scroll lag
    val isHeaderVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 || listState.firstVisibleItemScrollOffset < 120
        }
    }

    // Font size steps: 16, 18, 20, 23
    val fontSizes = listOf(16.sp, 18.sp, 20.sp, 23.sp)
    val currentFontSizeIndex = settings.textSizeIndex.coerceIn(0, fontSizes.size - 1)
    val currentFontSize = fontSizes[currentFontSizeIndex]
    var isAaPanelOpen by remember { mutableStateOf(false) }

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
            // Top reading-progress bar (sticky 2px ink line at header bottom: #prog)
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

                // Square 1:1 image with 26dp radius and Category Tag pill (.r-pic .tag)
                item {
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
                item {
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
                item {
                    FactsBlock(
                        article = currentArticle,
                        onSourceClick = { onOpenSourcePage(currentArticle.sourceId) }
                    )
                }

                // Body content (#rtext)
                item {
                    Spacer(modifier = Modifier.height(26.dp))
                    ArticleBody(
                        article = currentArticle,
                        fontSize = currentFontSize,
                        onReadFullClick = { openInCustomTabs(currentArticle.url) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }

            // Aa Settings Sheet (.sheet)
            AnimatedVisibility(
                visible = isAaPanelOpen,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = SignalMotion.easeTween(300)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = SignalMotion.easeTween(250)
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth().background(colors.bg)) {
                    HorizontalDivider(color = colors.line, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
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

                            // Theme toggle button matching mockup (#theme)
                            val isDarkTheme = settings.theme == "dark"
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isDarkTheme) colors.ink else colors.bg)
                                    .border(1.dp, colors.ink, CircleShape)
                                    .signalPressScale(0.96f) {
                                        haptics.tick()
                                        settingsManager.updateTheme(if (isDarkTheme) "light" else "dark")
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isDarkTheme) "Light" else "Dark",
                                    color = if (isDarkTheme) colors.bg else colors.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
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
                    .height(56.dp)
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
                            openInCustomTabs(currentArticle.url)
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

        // Back Chevron Header (hides on scroll down, returns on scroll up)
        val headerOffsetY by animateFloatAsState(
            targetValue = if (isHeaderVisible) 0f else -100f,
            animationSpec = SignalMotion.easeTween(280),
            label = "headerScroll"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (headerOffsetY * density).roundToInt()) }
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.bg.copy(alpha = 0.9f))
                    .signalPressScale(0.96f) { onBack() },
                contentAlignment = Alignment.Center
            ) {
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

private sealed class ContentBlock {
    data class Paragraph(val text: String) : ContentBlock()
    data class Heading(val text: String, val level: Int) : ContentBlock()
    data class Image(val url: String, val caption: String? = null) : ContentBlock()
    data class Quote(val text: String) : ContentBlock()
    data class BulletList(val items: List<String>) : ContentBlock()
}

private fun parseContentBlocks(html: String, heroImageUrl: String? = null): List<ContentBlock> {
    val doc = Jsoup.parse(html)
    val blocks = mutableListOf<ContentBlock>()
    val seenImages = mutableSetOf<String>()
    heroImageUrl?.let { seenImages.add(it.trim()) }

    val elements = doc.body().select("p, h1, h2, h3, h4, figure, img, blockquote, ul, ol")
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
                        blocks.add(ContentBlock.Paragraph(text))
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
                if (listItems.isNotEmpty()) {
                    blocks.add(ContentBlock.BulletList(listItems))
                    processedElements.addAll(el.getAllElements())
                }
            }
        }
    }

    if (blocks.isEmpty()) {
        val rawText = doc.text().trim()
        if (rawText.isNotBlank()) {
            val fallbackParagraphs = rawText.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
            fallbackParagraphs.forEach { blocks.add(ContentBlock.Paragraph(it)) }
        }
    }

    return blocks
}

@Composable
private fun RenderContentBlock(
    block: ContentBlock,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    val colors = SignalTheme.colors

    when (block) {
        is ContentBlock.Paragraph -> {
            Text(
                text = block.text,
                style = SignalTheme.typography.readerBody(fontSize),
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
        is ContentBlock.Heading -> {
            Text(
                text = block.text,
                style = SignalTheme.typography.screenTitle,
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
                    style = SignalTheme.typography.readerBody(fontSize),
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
                block.items.forEach { item ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("—", color = colors.mute, fontSize = fontSize)
                        Text(
                            text = item,
                            style = SignalTheme.typography.readerBody(fontSize),
                            color = colors.ink
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleBody(
    article: ArticleEntity,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onReadFullClick: () -> Unit
) {
    val colors = SignalTheme.colors

    val blocks = remember(article.fullContent, article.excerpt, article.imageUrl) {
        val raw = article.fullContent ?: article.excerpt
        parseContentBlocks(raw, heroImageUrl = article.imageUrl)
    }

    if (article.isExcerptOnly) {
        val excerptBlocks = blocks.take(3)
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                excerptBlocks.forEach { block ->
                    RenderContentBlock(block = block, fontSize = fontSize)
                }
            }

            // 110px gradient fade to background (#body.cut::after)
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

        // CTA: Read full article ↗ (#cta)
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
                    .signalPressScale(0.96f) { onReadFullClick() }
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
        // Full-text state: render all sequential blocks (paragraphs, inline images, headings, quotes)
        Column(modifier = Modifier.fillMaxWidth()) {
            blocks.forEach { block ->
                RenderContentBlock(block = block, fontSize = fontSize)
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
            .border(1.dp, if (enabled) colors.ink else colors.line, CircleShape)
            .signalPressScale(0.96f) { onClick() }
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
