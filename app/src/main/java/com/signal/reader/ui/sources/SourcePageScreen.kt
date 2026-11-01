package com.signal.reader.ui.sources

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.ArticleRow
import com.signal.reader.ui.components.BackChevron
import com.signal.reader.ui.components.BellIcon
import com.signal.reader.ui.components.HeroCard
import com.signal.reader.ui.components.SourceLogo
import com.signal.reader.ui.components.StaggeredItem
import com.signal.reader.ui.components.TimeUtils
import com.signal.reader.ui.components.getSourceInitials
import com.signal.reader.ui.components.rememberSignalHaptics
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTactile
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale
import com.signal.reader.ui.theme.signalTactileClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SourcePageScreen(
    sourceId: Long,
    repository: FeedRepository,
    onArticleClick: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenWebsite: ((url: String, title: String?) -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = SignalTheme.colors
    val density = LocalDensity.current
    val haptics = rememberSignalHaptics()
    val coroutineScope = rememberCoroutineScope()

    val source by repository.getSourceByIdFlow(sourceId).collectAsState(initial = null)
    val articles by repository.getArticlesBySourceFlow(sourceId).collectAsState(initial = emptyList())

    var showUnfollowConfirm by remember { mutableStateOf(false) }
    var statusNote by remember { mutableStateOf<String?>(null) }
    var isRetrying by remember { mutableStateOf(false) }

    val heroArticle = remember(articles) {
        articles.firstOrNull { !it.imageUrl.isNullOrBlank() } ?: articles.firstOrNull()
    }
    val streamArticles = remember(articles, heroArticle) {
        if (heroArticle != null) articles.filter { it.id != heroArticle.id } else articles
    }

    val unreadCount = remember(articles) {
        articles.count { !it.read }
    }

    // Unfold animation for confirm box (.confirm { animation: unfold .35s var(--ease) })
    val unfoldOffsetY = remember { Animatable(-8f) }
    val unfoldScale = remember { Animatable(0.97f) }
    val unfoldAlpha = remember { Animatable(0f) }

    LaunchedEffect(showUnfollowConfirm) {
        if (showUnfollowConfirm) {
            unfoldOffsetY.snapTo(-8f)
            unfoldScale.snapTo(0.97f)
            unfoldAlpha.snapTo(0f)

            coroutineScope.launch { unfoldOffsetY.animateTo(0f, SignalMotion.easeTween(350)) }
            coroutineScope.launch { unfoldScale.animateTo(1f, SignalMotion.easeTween(350)) }
            coroutineScope.launch { unfoldAlpha.animateTo(1f, SignalMotion.easeTween(350)) }
        }
    }

    LaunchedEffect(statusNote) {
        if (statusNote != null) {
            delay(4000L)
            statusNote = null
        }
    }

    LaunchedEffect(source) {
        if (source == null) {
            delay(600)
            if (source == null) {
                onBack()
            }
        }
    }

    fun openWebsite(url: String) {
        if (url.isNotBlank()) {
            if (onOpenWebsite != null) {
                onOpenWebsite(url, source?.name)
            } else {
                try {
                    val intent = CustomTabsIntent.Builder().setShowTitle(true).build()
                    intent.launchUrl(context, Uri.parse(url))
                } catch (_: Exception) {
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Header with Back Chevron and Notification Bell
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .signalTactileClick(
                        targetScale = SignalTactile.IconPressScale,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                BackChevron(color = colors.ink)
            }

            val currentSource = source
            if (currentSource != null && currentSource.followed) {
                val notifManager = remember { (context.applicationContext as com.signal.reader.SignalApplication).notificationManager }
                val isSystemActive = remember(currentSource.id) { notifManager.isChannelEnabledInSystem(currentSource.id) }
                val isAlertsActive = currentSource.notificationsEnabled && isSystemActive

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        coroutineScope.launch {
                            repository.setSourceNotificationsEnabled(currentSource.id, true)
                            haptics.tick()
                            statusNote = "Alerts enabled for ${currentSource.name}"
                            Toast.makeText(context, "Alerts enabled for ${currentSource.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(1.dp, if (isAlertsActive) colors.ink else colors.line, CircleShape)
                        .signalTactileClick(
                            targetScale = SignalTactile.IconPressScale,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.POST_NOTIFICATIONS
                                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    coroutineScope.launch {
                                        val newState = !currentSource.notificationsEnabled
                                        repository.setSourceNotificationsEnabled(currentSource.id, newState)
                                        haptics.tick()
                                        val msg = if (newState) {
                                            "Alerts enabled for ${currentSource.name}"
                                        } else {
                                            "Alerts muted for ${currentSource.name}"
                                        }
                                        statusNote = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    BellIcon(
                        color = if (isAlertsActive) colors.ink else colors.mute,
                        enabled = isAlertsActive,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = colors.line, thickness = 1.dp)

        val currentSource = source
        if (currentSource == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading source...",
                    color = colors.mute,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Masthead Section (.mast)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                    ) {
                        // 56x56 Publication Logo / Monogram (.mono: 16dp radius)
                        SourceLogo(
                            siteUrl = currentSource.siteUrl,
                            sourceName = currentSource.name,
                            size = 56.dp,
                            shape = RoundedCornerShape(16.dp),
                            fontSize = 18.sp
                        )

                        // Source Name H1
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
                                .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Feed isn’t loading",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = "Check the site’s connection or try updating the feed URL.",
                                        fontSize = 13.sp,
                                        color = colors.mute,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isRetrying) colors.mute.copy(alpha = 0.5f) else colors.ink)
                                        .signalPressScale(0.96f) {
                                            if (!isRetrying) {
                                                haptics.tick()
                                                isRetrying = true
                                                coroutineScope.launch {
                                                    repository.refreshSource(currentSource.id)
                                                    isRetrying = false
                                                }
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isRetrying) "Checking…" else "Retry",
                                        color = colors.bg,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Action buttons (.acts: Following/Follow, Mute/Muted)
                    Row(
                        modifier = Modifier.padding(top = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Follow / Following pill
                        val isFollowed = currentSource.followed
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (!isFollowed) colors.ink else colors.bg)
                                .border(1.dp, colors.ink, CircleShape)
                                .signalPressScale(0.96f)
                                .clickable {
                                    haptics.tick()
                                    if (isFollowed) {
                                        showUnfollowConfirm = true
                                    } else {
                                        coroutineScope.launch {
                                            repository.setFollowed(currentSource.id, true)
                                            repository.refreshSource(currentSource.id)
                                            statusNote = null
                                        }
                                    }
                                }
                                .padding(horizontal = 22.dp, vertical = 11.dp)
                        ) {
                            Text(
                                text = if (isFollowed) "Following" else "Follow",
                                color = if (!isFollowed) colors.bg else colors.ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Mute / Muted pill
                        val isMuted = currentSource.muted
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isMuted) colors.ink else colors.bg)
                                .border(1.dp, colors.ink, CircleShape)
                                .signalPressScale(0.96f)
                                .clickable {
                                    haptics.tick()
                                    coroutineScope.launch {
                                        val newMuted = !isMuted
                                        repository.setMuted(currentSource.id, newMuted)
                                        statusNote = if (newMuted) {
                                            "Muted. New articles from this source won’t appear on Home."
                                        } else null
                                    }
                                }
                                .padding(horizontal = 22.dp, vertical = 11.dp)
                        ) {
                            Text(
                                text = if (isMuted) "Muted" else "Mute",
                                color = if (isMuted) colors.bg else colors.ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Unfollow confirmation box (.confirm)
                    if (showUnfollowConfirm) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .offset { IntOffset(0, (unfoldOffsetY.value * density.density).roundToInt()) }
                                .scale(unfoldScale.value)
                                .alpha(unfoldAlpha.value)
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
                                    // Solid Unfollow button
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(colors.ink)
                                            .signalPressScale(0.96f)
                                            .clickable {
                                                haptics.tick()
                                                coroutineScope.launch {
                                                    repository.unfollowSource(currentSource.id)
                                                    showUnfollowConfirm = false
                                                    statusNote = "You’re not following ${currentSource.name}. New articles won’t appear on Home."
                                                }
                                            }
                                            .padding(horizontal = 22.dp, vertical = 11.dp)
                                    ) {
                                        Text(
                                            text = "Unfollow",
                                            color = colors.bg,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    // Cancel button
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .border(1.dp, colors.ink, CircleShape)
                                            .signalPressScale(0.96f)
                                            .clickable {
                                                showUnfollowConfirm = false
                                            }
                                            .padding(horizontal = 22.dp, vertical = 11.dp)
                                    ) {
                                        Text(
                                            text = "Cancel",
                                            color = colors.ink,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Status note (.note)
                    if (statusNote != null) {
                        Text(
                            text = statusNote ?: "",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = colors.mute,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                }
            }

            // Facts List (<dl id="facts">)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    HorizontalDivider(color = colors.line, thickness = 1.dp)

                    FactRow(label = "In this feed", value = "${articles.size} articles")
                    FactRow(label = "Unread", value = unreadCount.toString())
                    FactRow(
                        label = "Latest",
                        value = if (articles.isNotEmpty()) TimeUtils.formatRelativeTime(articles.first().publishedTime) else "None"
                    )
                    FactRow(
                        label = "Website",
                        value = "${currentSource.siteUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.").take(24)} ↗",
                        isLink = true,
                        onClick = { openWebsite(currentSource.siteUrl) }
                    )
                }

                Text(
                    text = "Latest from this source",
                    fontSize = 13.sp,
                    color = colors.mute,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 14.dp)
                )
            }

            // Hero article from this source (if exists)
            if (heroArticle != null) {
                item(key = "source_hero_${heroArticle.id}") {
                    HeroCard(
                        article = heroArticle,
                        onClick = { onArticleClick(heroArticle.id) },
                        showSourceName = false,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }

            // Remaining stream rows from this source with StaggeredItem
            itemsIndexed(streamArticles, key = { _, item -> item.id }) { index, article ->
                StaggeredItem(index = index + 1) {
                    ArticleRow(
                        article = article,
                        onClick = { onArticleClick(article.id) },
                        onToggleSave = {
                            coroutineScope.launch {
                                repository.toggleSaved(article.id)
                            }
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }

            item {
                HorizontalDivider(color = colors.line, thickness = 1.dp)
                Text(
                    text = "That’s everything in this feed. Older articles aren’t available.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = colors.mute,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)
                )
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
        }
    }
}

@Composable
private fun FactRow(
    label: String,
    value: String,
    isLink: Boolean = false,
    onClick: () -> Unit = {}
) {
    val colors = SignalTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isLink, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = colors.mute,
            modifier = Modifier.fillMaxWidth(0.35f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = colors.ink,
            textDecoration = if (isLink) TextDecoration.Underline else TextDecoration.None
        )
    }
    HorizontalDivider(color = colors.line, thickness = 1.dp)
}
