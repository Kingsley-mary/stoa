package com.signal.reader.ui.sources

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.data.feed.DiscoveryResult
import com.signal.reader.data.feed.StarterSource
import com.signal.reader.data.feed.StarterSources
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.BackChevron
import com.signal.reader.ui.components.getSourceInitials
import com.signal.reader.ui.components.rememberSignalHaptics
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AddSourcesScreen(
    isFirstLaunch: Boolean,
    repository: FeedRepository,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val haptics = rememberSignalHaptics()
    val coroutineScope = rememberCoroutineScope()

    val existingSources by repository.getAllSourcesFlow().collectAsState(initial = emptyList())
    val followedUrls = remember(existingSources) {
        existingSources.filter { it.followed }.map { it.feedUrl.lowercase() }.toSet()
    }

    var pastedUrl by remember { mutableStateOf("") }
    var isSearchingFeed by remember { mutableStateOf(false) }
    var discoveryResult by remember { mutableStateOf<DiscoveryResult?>(null) }

    val selectedStarter = remember { mutableStateListOf<StarterSource>() }
    val customAdded = remember { mutableStateListOf<DiscoveryResult.Found>() }

    fun findFeed() {
        if (pastedUrl.isBlank()) return
        coroutineScope.launch {
            isSearchingFeed = true
            discoveryResult = repository.discovery.discover(pastedUrl)
            isSearchingFeed = false
        }
    }

    fun finishAndSave() {
        coroutineScope.launch {
            haptics.pop()
            for (source in selectedStarter) {
                repository.addSource(source.name, source.siteUrl, source.feedUrl, source.description)
            }
            for (custom in customAdded) {
                repository.addSource(custom.name, custom.siteUrl, custom.feedUrl, custom.description)
            }
            onFinish()
        }
    }

    val totalSelected = selectedStarter.size + customAdded.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isFirstLaunch) {
                Text(
                    text = "Signal",
                    style = SignalTheme.typography.wordmark,
                    color = colors.ink
                )
                Text(
                    text = "Skip",
                    color = colors.ink,
                    fontSize = 14.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .signalPressScale(0.96f) {
                            haptics.tick()
                            onSkip()
                        }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .signalPressScale(0.96f) { onFinish() },
                    contentAlignment = Alignment.Center
                ) {
                    BackChevron(color = colors.ink)
                }
                Spacer(modifier = Modifier.size(28.dp))
            }
        }

        HorizontalDivider(color = colors.line, thickness = 1.dp)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Title & Subtitle with entrance animation
            item {
                StaggeredItem(index = 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 20.dp)
                    ) {
                        Text(
                            text = if (isFirstLaunch) "Pick what you read" else "Add sources",
                            style = SignalTheme.typography.screenTitle,
                            color = colors.ink
                        )
                        Text(
                            text = if (isFirstLaunch)
                                "Choose a few sites to start. You can change this any time in Sources."
                            else
                                "Pick from the list, or paste a site’s address and the app will look for its feed.",
                            color = colors.mute,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                }
            }

            // Paste URL Box
            item {
                StaggeredItem(index = 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(CircleShape)
                            .border(1.dp, colors.ink, CircleShape)
                            .padding(start = 20.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = pastedUrl,
                            onValueChange = { pastedUrl = it },
                            textStyle = TextStyle(
                                fontFamily = SignalTheme.typography.wordmark.fontFamily,
                                color = colors.ink,
                                fontSize = 16.sp
                            ),
                            cursorBrush = SolidColor(colors.ink),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(onGo = { findFeed() }),
                            decorationBox = { innerTextField ->
                                if (pastedUrl.isEmpty()) {
                                    Text(
                                        text = "Paste a site address",
                                        color = colors.mute,
                                        fontSize = 16.sp
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.ink)
                                .signalPressScale(0.96f) { findFeed() }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSearchingFeed) "Looking…" else "Find feed",
                                color = colors.bg,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Discovery Result Card with Unfold Animation
            item {
                when (val result = discoveryResult) {
                    is DiscoveryResult.Found -> {
                        val isAlreadyFollowed = followedUrls.contains(result.feedUrl.lowercase())
                        val isAlreadyAdded = customAdded.any { it.feedUrl.equals(result.feedUrl, true) }

                        AnimatedVisibility(
                            visible = true,
                            enter = expandVertically(
                                animationSpec = SignalMotion.easeTween(350)
                            ) + fadeIn(animationSpec = SignalMotion.easeTween(350))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(colors.ink),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = getSourceInitials(result.name),
                                            color = colors.bg,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = colors.ink,
                                            letterSpacing = (-0.01).sp
                                        )
                                        Text(
                                            text = if (isAlreadyFollowed) "${result.name} is already in your list." else "Feed found",
                                            fontSize = 13.sp,
                                            color = colors.mute
                                        )
                                    }

                                    if (isAlreadyFollowed) {
                                        Text(
                                            text = "Following",
                                            color = colors.mute,
                                            fontSize = 13.sp
                                        )
                                    } else {
                                        AddTogglePill(
                                            isAdded = isAlreadyAdded,
                                            onToggle = {
                                                haptics.tick()
                                                if (isAlreadyAdded) customAdded.remove(result)
                                                else customAdded.add(result)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is DiscoveryResult.NotFound -> {
                        Text(
                            text = result.reason,
                            color = colors.mute,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                    }
                    else -> {}
                }
            }

            // Custom added section
            if (customAdded.isNotEmpty()) {
                item {
                    Text(
                        text = "Added from a link",
                        color = colors.mute,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 10.dp)
                    )
                }
                itemsIndexed(customAdded) { idx, custom ->
                    StaggeredItem(index = idx + 2) {
                        StarterRow(
                            name = custom.name,
                            description = custom.description.ifBlank { "Added from a link" },
                            isFollowing = false,
                            isAdded = true,
                            onToggle = {
                                haptics.tick()
                                customAdded.remove(custom)
                            }
                        )
                    }
                }
            }

            // Dynamic Starter Sources by Group
            val groupedSources = StarterSources.ALL.groupBy { it.group }
            var staggeredItemIndex = 3
            groupedSources.forEach { (groupName, sourcesInGroup) ->
                item(key = "header_$groupName") {
                    Text(
                        text = groupName,
                        color = colors.mute,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 10.dp)
                    )
                }
                itemsIndexed(sourcesInGroup, key = { _, s -> s.feedUrl }) { idx, source ->
                    val isFollowing = followedUrls.contains(source.feedUrl.lowercase())
                    val isAdded = selectedStarter.contains(source)
                    StaggeredItem(index = (staggeredItemIndex + idx).coerceAtMost(10)) {
                        StarterRow(
                            name = source.name,
                            description = source.description,
                            isFollowing = isFollowing,
                            isAdded = isAdded,
                            onToggle = {
                                haptics.tick()
                                if (isAdded) selectedStarter.remove(source)
                                else selectedStarter.add(source)
                            }
                        )
                    }
                }
                staggeredItemIndex += sourcesInGroup.size
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Bottom CTA Button with press scale & transition
        HorizontalDivider(color = colors.line, thickness = 1.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            val isButtonEnabled = if (isFirstLaunch) totalSelected > 0 else true
            val buttonText = when {
                isFirstLaunch && totalSelected == 0 -> "Pick at least one"
                isFirstLaunch -> "Continue with $totalSelected"
                totalSelected == 0 -> "Done"
                else -> "Add $totalSelected and finish"
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(if (isButtonEnabled) colors.ink else colors.bg)
                    .border(1.dp, if (isButtonEnabled) colors.ink else colors.line, CircleShape)
                    .signalPressScale(0.96f) {
                        if (isButtonEnabled) finishAndSave()
                    }
                    .padding(vertical = 17.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buttonText,
                    color = if (isButtonEnabled) colors.bg else colors.mute,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 40ms staggered rise and fade in animation matching mockup (.in class)
 */
@Composable
fun StaggeredItem(
    index: Int,
    content: @Composable () -> Unit
) {
    if (SignalTheme.reducedMotion) {
        content()
        return
    }

    val cappedIndex = index.coerceAtMost(10)
    val delayMs = cappedIndex * SignalMotion.ContentEnterStagger

    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(14f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = SignalMotion.easeTween(SignalMotion.ContentEnterDuration, delayMillis = delayMs)
        )
    }
    LaunchedEffect(Unit) {
        offsetY.animateTo(
            targetValue = 0f,
            animationSpec = SignalMotion.easeTween(SignalMotion.ContentEnterDuration, delayMillis = delayMs)
        )
    }

    Box(
        modifier = Modifier
            .alpha(alpha.value)
            .offset { IntOffset(0, (offsetY.value * density).roundToInt()) }
    ) {
        content()
    }
}

/**
 * Add / Added pill with popping spring animation on toggle (.pill.just)
 */
@Composable
fun AddTogglePill(
    isAdded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors

    var triggerPop by remember { mutableStateOf(false) }
    val popScale = remember { Animatable(1.0f) }

    LaunchedEffect(isAdded) {
        if (isAdded) {
            popScale.snapTo(0.6f)
            popScale.animateTo(
                targetValue = 1.0f,
                animationSpec = SignalMotion.PopSpringSpec
            )
        }
    }

    Box(
        modifier = modifier
            .scale(popScale.value)
            .clip(CircleShape)
            .background(if (isAdded) colors.ink else colors.bg)
            .border(1.dp, colors.ink, CircleShape)
            .signalPressScale(0.96f) { onToggle() }
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isAdded) "✓ Added" else "Add",
            color = if (isAdded) colors.bg else colors.ink,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun StarterRow(
    name: String,
    description: String,
    isFollowing: Boolean,
    isAdded: Boolean,
    onToggle: () -> Unit
) {
    val colors = SignalTheme.colors

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.line, thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 48x48 Initials Square (.mono)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.ink),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getSourceInitials(name),
                    color = colors.bg,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }

            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = colors.ink,
                    letterSpacing = (-0.01).sp
                )
                Text(
                    text = description,
                    color = colors.mute,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }

            // Action
            if (isFollowing) {
                Text(
                    text = "Following",
                    color = colors.mute,
                    fontSize = 13.sp
                )
            } else {
                AddTogglePill(
                    isAdded = isAdded,
                    onToggle = onToggle
                )
            }
        }
    }
}
