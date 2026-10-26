package com.signal.reader.ui.sources

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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.data.feed.DiscoveryResult
import com.signal.reader.data.feed.StarterSource
import com.signal.reader.data.feed.StarterSources
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.BackChevron
import com.signal.reader.ui.theme.SignalTheme
import kotlinx.coroutines.launch

@Composable
fun AddSourcesScreen(
    isFirstLaunch: Boolean,
    repository: FeedRepository,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val coroutineScope = rememberCoroutineScope()

    val existingSources by repository.getAllSourcesFlow().collectAsState(initial = emptyList())
    val followedUrls = remember(existingSources) {
        existingSources.filter { it.followed }.map { it.feedUrl.lowercase() }.toSet()
    }

    var pastedUrl by remember { mutableStateOf("") }
    var isSearchingFeed by remember { mutableStateOf(false) }
    var discoveryResult by remember { mutableStateOf<DiscoveryResult?>(null) }

    // Track selected starter sources to add
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
            // Add all selected starter sources
            for (source in selectedStarter) {
                repository.addSource(source.name, source.siteUrl, source.feedUrl, source.description)
            }
            // Add all custom discovered sources
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
                    modifier = Modifier.clickable { onSkip() }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onFinish() },
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
            // Title & Subtitle
            item {
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

            // Paste URL Box
            item {
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
                            .clickable { findFeed() }
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

            // Discovery results banner
            item {
                when (val result = discoveryResult) {
                    is DiscoveryResult.Found -> {
                        val isAlreadyFollowed = followedUrls.contains(result.feedUrl.lowercase())
                        val isAlreadyAdded = customAdded.any { it.feedUrl.equals(result.feedUrl, true) }

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
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = colors.ink
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
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isAlreadyAdded) colors.ink else colors.bg)
                                            .border(1.dp, colors.ink, CircleShape)
                                            .clickable {
                                                if (isAlreadyAdded) customAdded.remove(result)
                                                else customAdded.add(result)
                                            }
                                            .padding(horizontal = 18.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = if (isAlreadyAdded) "Added" else "Add",
                                            color = if (isAlreadyAdded) colors.bg else colors.ink,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp
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

            // "Added from a link" section
            if (customAdded.isNotEmpty()) {
                item {
                    Text(
                        text = "Added from a link",
                        color = colors.mute,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 10.dp)
                    )
                }
                items(customAdded) { custom ->
                    StarterRow(
                        name = custom.name,
                        description = custom.description.ifBlank { "Added from a link" },
                        isFollowing = false,
                        isAdded = true,
                        onToggle = { customAdded.remove(custom) }
                    )
                }
            }

            // Design Group
            item {
                Text(
                    text = "Design",
                    color = colors.mute,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 10.dp)
                )
            }
            items(StarterSources.ALL.filter { it.group == "Design" }) { source ->
                val isFollowing = followedUrls.contains(source.feedUrl.lowercase())
                val isAdded = selectedStarter.contains(source)
                StarterRow(
                    name = source.name,
                    description = source.description,
                    isFollowing = isFollowing,
                    isAdded = isAdded,
                    onToggle = {
                        if (isAdded) selectedStarter.remove(source)
                        else selectedStarter.add(source)
                    }
                )
            }

            // Tech and culture Group
            item {
                Text(
                    text = "Tech and culture",
                    color = colors.mute,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 10.dp)
                )
            }
            items(StarterSources.ALL.filter { it.group == "Tech and culture" }) { source ->
                val isFollowing = followedUrls.contains(source.feedUrl.lowercase())
                val isAdded = selectedStarter.contains(source)
                StarterRow(
                    name = source.name,
                    description = source.description,
                    isFollowing = isFollowing,
                    isAdded = isAdded,
                    onToggle = {
                        if (isAdded) selectedStarter.remove(source)
                        else selectedStarter.add(source)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Bottom CTA Button
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
                    .clickable(enabled = isButtonEnabled) { finishAndSave() }
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
            // Initials Box
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.ink),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getInitials(name),
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

            // Action: Following / Add / Added
            if (isFollowing) {
                Text(
                    text = "Following",
                    color = colors.mute,
                    fontSize = 13.sp
                )
            } else {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isAdded) colors.ink else colors.bg)
                        .border(1.dp, colors.ink, CircleShape)
                        .clickable { onToggle() }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isAdded) "Added" else "Add",
                        color = if (isAdded) colors.bg else colors.ink,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
