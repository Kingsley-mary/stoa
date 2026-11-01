package com.signal.reader.ui.onboarding

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.data.catalog.CatalogRepository
import com.signal.reader.data.catalog.CatalogSource
import com.signal.reader.data.feed.DiscoveryResult
import com.signal.reader.data.feed.FeedDiscovery
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.BackChevron
import com.signal.reader.ui.components.SourceLogo
import com.signal.reader.ui.components.getSourceInitials
import com.signal.reader.ui.components.rememberSignalHaptics
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Editorial Interest-based Onboarding Screen matching design/interests-mockup.html:
 * Step 1: "What do you read?" - Wall of large topic chips with 25ms stagger and spring pops.
 * Step 2: "Start with these" - Suggested and More sources scored against picked topics.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsOnboardingScreen(
    repository: FeedRepository,
    onFinish: (List<CatalogSource>, Set<String>) -> Unit,
    onPasteSiteInstead: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = SignalTheme.colors
    val haptics = rememberSignalHaptics()
    val scope = rememberCoroutineScope()

    // Reduced motion check
    val isReducedMotion = remember {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }

    var step by remember { mutableIntStateOf(1) }
    val allCatalogSources = remember { CatalogRepository.getSources(context) }
    val availableTopics = remember { CatalogRepository.getAvailableTopics(context) }

    val pickedTopics = remember { mutableStateListOf<String>() }
    val addedSources = remember { mutableStateListOf<CatalogSource>() }
    val customAddedSources = remember { mutableStateListOf<CatalogSource>() }

    var suggestedSources by remember { mutableStateOf<List<CatalogSource>>(emptyList()) }
    var moreSources by remember { mutableStateOf<List<CatalogSource>>(emptyList()) }

    var showPasteInput by remember { mutableStateOf(false) }
    var pastedUrl by remember { mutableStateOf("") }
    var isSearchingFeed by remember { mutableStateOf(false) }
    var pasteError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Intercept back button when on step 2: return to step 1 keeping picks
    BackHandler(enabled = step == 2) {
        step = 1
    }

    fun proceedToStep2() {
        val (suggested, more) = CatalogRepository.computeSuggestions(
            pickedTopics = pickedTopics.toSet(),
            sources = allCatalogSources
        )
        suggestedSources = suggested
        moreSources = more
        addedSources.clear()
        addedSources.addAll(suggested)
        addedSources.addAll(customAddedSources)
        step = 2
    }

    fun findAndAddCustomFeed() {
        val input = pastedUrl.trim()
        if (input.isBlank()) return
        isSearchingFeed = true
        pasteError = null
        coroutineScope.launch {
            val discovery = FeedDiscovery()
            when (val res = discovery.discover(input)) {
                is DiscoveryResult.Found -> {
                    val newSource = CatalogSource(
                        name = res.name,
                        description = res.description.ifBlank { "Added from link" },
                        siteUrl = res.siteUrl,
                        feedUrl = res.feedUrl,
                        topics = listOf("Custom")
                    )
                    if (!customAddedSources.any { it.feedUrl.equals(res.feedUrl, ignoreCase = true) }) {
                        customAddedSources.add(0, newSource)
                    }
                    if (!addedSources.any { it.feedUrl.equals(res.feedUrl, ignoreCase = true) }) {
                        addedSources.add(newSource)
                    }
                    haptics.pop()
                    pastedUrl = ""
                    isSearchingFeed = false
                }
                is DiscoveryResult.NotFound -> {
                    isSearchingFeed = false
                    pasteError = res.reason.ifBlank {
                        "Couldn't find a feed on this site. Try the site's homepage address, or paste the feed link itself."
                    }
                }
                is DiscoveryResult.Error -> {
                    isSearchingFeed = false
                    pasteError = res.message.ifBlank {
                        "Unable to read this address. Please check your internet connection."
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(57.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (step == 1) {
                Text(
                    text = "STOA",
                    style = SignalTheme.typography.wordmark,
                    fontSize = 17.sp,
                    letterSpacing = 2.sp,
                    color = colors.ink
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .signalPressScale(0.92f) { step = 1 },
                    contentAlignment = Alignment.Center
                ) {
                    BackChevron(color = colors.ink)
                }
            }

            Text(
                text = "Skip",
                color = colors.ink,
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        haptics.tick()
                        onSkip()
                    }
                    .padding(vertical = 4.dp, horizontal = 6.dp)
            )
        }

        // Two-segment progress indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(colors.ink)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(if (step == 2) colors.ink else colors.line)
            )
        }

        // Animated Step Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = step,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (isReducedMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else if (targetState > initialState) {
                        // Slide in from right 24px + fade (.4s ease-out) / Slide out left 24px + fade (.2s)
                        (slideInHorizontally(
                            animationSpec = SignalMotion.easeTween(400),
                            initialOffsetX = { 24 }
                        ) + fadeIn(SignalMotion.easeTween(400))).togetherWith(
                            slideOutHorizontally(
                                animationSpec = SignalMotion.easeTween(200),
                                targetOffsetX = { -24 }
                            ) + fadeOut(SignalMotion.easeTween(200))
                        )
                    } else {
                        // Slide in from left 24px / Slide out right 24px
                        (slideInHorizontally(
                            animationSpec = SignalMotion.easeTween(400),
                            initialOffsetX = { -24 }
                        ) + fadeIn(SignalMotion.easeTween(400))).togetherWith(
                            slideOutHorizontally(
                                animationSpec = SignalMotion.easeTween(200),
                                targetOffsetX = { 24 }
                            ) + fadeOut(SignalMotion.easeTween(200))
                        )
                    }
                },
                label = "stepTransition"
            ) { targetStep ->
                if (targetStep == 1) {
                    // STEP 1: What do you read?
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 28.dp, bottom = 28.dp)
                    ) {
                        Text(
                            text = "WHAT DO YOU READ?",
                            style = SignalTheme.typography.screenTitle,
                            fontSize = 38.sp,
                            lineHeight = 38.sp,
                            color = colors.ink,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Pick a few topics. Stoa will suggest sources to match.",
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = colors.mute,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(28.dp))

                        // Chip wall with 25ms stagger entrance
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            availableTopics.forEachIndexed { index, topic ->
                                val isSelected = pickedTopics.contains(topic)
                                TopicChip(
                                    topic = topic,
                                    isSelected = isSelected,
                                    index = index,
                                    isReducedMotion = isReducedMotion,
                                    onToggle = {
                                        haptics.tick()
                                        if (isSelected) {
                                            pickedTopics.remove(topic)
                                        } else {
                                            pickedTopics.add(topic)
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // STEP 2: Start with these
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 28.dp, bottom = 28.dp)
                    ) {
                        Text(
                            text = "START WITH THESE",
                            style = SignalTheme.typography.screenTitle,
                            fontSize = 38.sp,
                            lineHeight = 38.sp,
                            color = colors.ink,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        val matchedLead = remember(pickedTopics) {
                            val topics = pickedTopics.take(3).joinToString(", ")
                            val extra = if (pickedTopics.size > 3) " and ${pickedTopics.size - 3} more" else ""
                            "Matched to $topics$extra. Tap to add or remove."
                        }
                        Text(
                            text = matchedLead,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = colors.mute,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Suggested for you header with Add all action
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Suggested for you",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.mute
                            )

                            val allSuggestedAdded = suggestedSources.isNotEmpty() && suggestedSources.all { addedSources.contains(it) }
                            Text(
                                text = if (allSuggestedAdded) "Clear all" else "Add all",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier
                                    .clickable {
                                        haptics.tick()
                                        if (allSuggestedAdded) {
                                            addedSources.removeAll(suggestedSources.toSet())
                                        } else {
                                            for (s in suggestedSources) {
                                                if (!addedSources.contains(s)) addedSources.add(s)
                                            }
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }

                        suggestedSources.forEachIndexed { index, source ->
                            val isAdded = addedSources.contains(source)
                            SourceSuggestionRow(
                                source = source,
                                pickedTopics = pickedTopics.toSet(),
                                isAdded = isAdded,
                                index = index,
                                isReducedMotion = isReducedMotion,
                                onToggle = {
                                    haptics.tick()
                                    if (isAdded) {
                                        addedSources.remove(source)
                                    } else {
                                        addedSources.add(source)
                                    }
                                }
                            )
                        }

                        // More you might like
                        if (moreSources.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "More you might like",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.mute
                                )

                                val allMoreAdded = moreSources.all { addedSources.contains(it) }
                                Text(
                                    text = if (allMoreAdded) "Clear" else "Add all",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.ink,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier
                                        .clickable {
                                            haptics.tick()
                                            if (allMoreAdded) {
                                                addedSources.removeAll(moreSources.toSet())
                                            } else {
                                                for (s in moreSources) {
                                                    if (!addedSources.contains(s)) addedSources.add(s)
                                                }
                                            }
                                        }
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                )
                            }

                            moreSources.forEachIndexed { index, source ->
                                val isAdded = addedSources.contains(source)
                                SourceSuggestionRow(
                                    source = source,
                                    pickedTopics = pickedTopics.toSet(),
                                    isAdded = isAdded,
                                    index = suggestedSources.size + index,
                                    isReducedMotion = isReducedMotion,
                                    onToggle = {
                                        haptics.tick()
                                        if (isAdded) {
                                            addedSources.remove(source)
                                        } else {
                                            addedSources.add(source)
                                        }
                                    }
                                )
                            }
                        }

                        // Added from link section
                        if (customAddedSources.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Added from link",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.mute,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )

                            customAddedSources.forEachIndexed { index, source ->
                                val isAdded = addedSources.contains(source)
                                SourceSuggestionRow(
                                    source = source,
                                    pickedTopics = pickedTopics.toSet(),
                                    isAdded = isAdded,
                                    index = index,
                                    isReducedMotion = isReducedMotion,
                                    onToggle = {
                                        haptics.tick()
                                        if (isAdded) {
                                            addedSources.remove(source)
                                        } else {
                                            addedSources.add(source)
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Paste a site address toggle
                        Row(
                            modifier = Modifier
                                .clickable {
                                    haptics.tick()
                                    showPasteInput = !showPasteInput
                                }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (showPasteInput) "Hide site address input" else "Paste a site address instead",
                                fontSize = 14.sp,
                                color = colors.ink,
                                textDecoration = TextDecoration.Underline
                            )
                            Text(
                                text = if (showPasteInput) "▲" else "▼",
                                fontSize = 10.sp,
                                color = colors.mute
                            )
                        }

                        // Inline Paste URL input container
                        AnimatedVisibility(
                            visible = showPasteInput,
                            enter = expandVertically(animationSpec = SignalMotion.easeTween(300)) + fadeIn(),
                            exit = shrinkVertically(animationSpec = SignalMotion.easeTween(200)) + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(CircleShape)
                                        .border(1.dp, colors.ink, CircleShape)
                                        .padding(start = 18.dp, top = 5.dp, end = 5.dp, bottom = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicTextField(
                                        value = pastedUrl,
                                        onValueChange = {
                                            pastedUrl = it
                                            if (pasteError != null) pasteError = null
                                        },
                                        textStyle = TextStyle(
                                            fontFamily = SignalTheme.typography.wordmark.fontFamily,
                                            color = colors.ink,
                                            fontSize = 15.sp
                                        ),
                                        cursorBrush = SolidColor(colors.ink),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Uri,
                                            imeAction = ImeAction.Go
                                        ),
                                        keyboardActions = KeyboardActions(onGo = {
                                            if (pastedUrl.isNotBlank() && !isSearchingFeed) {
                                                haptics.tick()
                                                findAndAddCustomFeed()
                                            }
                                        }),
                                        decorationBox = { innerTextField ->
                                            if (pastedUrl.isEmpty()) {
                                                Text(
                                                    text = "e.g. daringfireball.net",
                                                    color = colors.mute,
                                                    fontSize = 15.sp
                                                )
                                            }
                                            innerTextField()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (pastedUrl.isNotBlank() && !isSearchingFeed) colors.ink else colors.mute.copy(alpha = 0.4f))
                                            .signalPressScale(0.96f) {
                                                if (pastedUrl.isNotBlank() && !isSearchingFeed) {
                                                    haptics.tick()
                                                    findAndAddCustomFeed()
                                                }
                                            }
                                            .padding(horizontal = 18.dp, vertical = 11.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isSearchingFeed) "Looking…" else "Add source",
                                            color = colors.bg,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                if (pasteError != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = pasteError ?: "",
                                        color = colors.mute,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sticky Footer: permanently anchored at the bottom of the Column
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .drawBehind {
                    drawLine(
                        color = colors.line,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            val isEnabled = if (step == 1) pickedTopics.isNotEmpty() else addedSources.isNotEmpty()
            val buttonText = if (step == 1) {
                if (pickedTopics.isEmpty()) "Pick at least one topic"
                else "Continue with ${pickedTopics.size} ${if (pickedTopics.size == 1) "topic" else "topics"}"
            } else {
                val totalAvailable = suggestedSources.size + moreSources.size + customAddedSources.size
                if (addedSources.isEmpty()) "Pick at least one source"
                else if (addedSources.size >= totalAvailable && totalAvailable > 0) "Add all ${addedSources.size} sources"
                else "Add ${addedSources.size} ${if (addedSources.size == 1) "source" else "sources"}"
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(CircleShape)
                    .background(if (isEnabled) colors.ink else colors.card)
                    .border(1.dp, if (isEnabled) colors.ink else colors.line, CircleShape)
                    .then(
                        if (isEnabled) {
                            Modifier.signalPressScale(0.96f) {
                                haptics.pop()
                                if (step == 1) {
                                    proceedToStep2()
                                } else {
                                    onFinish(addedSources.toList(), pickedTopics.toSet())
                                }
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buttonText,
                    color = if (isEnabled) colors.bg else colors.mute,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TopicChip(
    topic: String,
    isSelected: Boolean,
    index: Int,
    isReducedMotion: Boolean,
    onToggle: () -> Unit
) {
    val colors = SignalTheme.colors
    val popScale = remember { Animatable(1.0f) }
    val entranceAlpha = remember { Animatable(if (isReducedMotion) 1f else 0f) }
    val entranceTranslateY = remember { Animatable(if (isReducedMotion) 0f else 14f) }

    LaunchedEffect(Unit) {
        if (!isReducedMotion) {
            delay((index * 25).toLong())
            launch { entranceAlpha.animateTo(1f, SignalMotion.easeTween(350)) }
            launch { entranceTranslateY.animateTo(0f, SignalMotion.easeTween(350)) }
        }
    }

    LaunchedEffect(isSelected) {
        if (isSelected && !isReducedMotion) {
            popScale.snapTo(0.92f)
            popScale.animateTo(1.07f, tween(150, easing = SignalMotion.SpringEasing))
            popScale.animateTo(1.0f, tween(200, easing = SignalMotion.EaseOutQuart))
        }
    }

    Box(
        modifier = Modifier
            .alpha(entranceAlpha.value)
            .scale(popScale.value)
            .clip(CircleShape)
            .background(if (isSelected) colors.ink else Color.Transparent)
            .border(1.dp, colors.ink, CircleShape)
            .signalPressScale(0.95f) { onToggle() }
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = topic,
            color = if (isSelected) colors.bg else colors.ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SourceSuggestionRow(
    source: CatalogSource,
    pickedTopics: Set<String>,
    isAdded: Boolean,
    index: Int,
    isReducedMotion: Boolean,
    onToggle: () -> Unit
) {
    val colors = SignalTheme.colors
    val popScale = remember { Animatable(1.0f) }
    val entranceAlpha = remember { Animatable(if (isReducedMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!isReducedMotion) {
            delay((index.coerceAtMost(10) * 40).toLong())
            entranceAlpha.animateTo(1f, SignalMotion.easeTween(350))
        }
    }

    LaunchedEffect(isAdded) {
        if (isAdded && !isReducedMotion) {
            popScale.snapTo(0.92f)
            popScale.animateTo(1.07f, tween(150, easing = SignalMotion.SpringEasing))
            popScale.animateTo(1.0f, tween(200, easing = SignalMotion.EaseOutQuart))
        }
    }

    val whyText = remember(source, pickedTopics) {
        CatalogRepository.getWhyText(source, pickedTopics)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(entranceAlpha.value)
            .border(width = 0.5.dp, color = colors.line, shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Publication logo (with high-contrast monogram fallback)
        SourceLogo(
            siteUrl = source.siteUrl,
            sourceName = source.name,
            size = 48.dp,
            shape = RoundedCornerShape(14.dp),
            fontSize = 15.sp
        )

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink,
                letterSpacing = (-0.01).sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = source.description,
                fontSize = 13.sp,
                color = colors.ink.copy(alpha = 0.85f),
                lineHeight = 17.sp
            )
            val matched = source.topics.filter { pickedTopics.contains(it) }
            if (matched.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                val firstTwo = matched.take(2).joinToString(", ")
                val extra = if (matched.size > 2) " and ${matched.size - 2} more" else ""
                Text(
                    text = "Because you picked $firstTwo$extra",
                    fontSize = 12.sp,
                    color = colors.mute,
                    lineHeight = 16.sp
                )
            }
        }

        // Add / Added pill button
        Box(
            modifier = Modifier
                .scale(popScale.value)
                .clip(CircleShape)
                .background(if (isAdded) colors.ink else Color.Transparent)
                .border(1.dp, colors.ink, CircleShape)
                .signalPressScale(0.95f) { onToggle() }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isAdded) {
                    Text(
                        text = "✓ ",
                        color = colors.bg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (isAdded) "Added" else "Add",
                    color = if (isAdded) colors.bg else colors.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
