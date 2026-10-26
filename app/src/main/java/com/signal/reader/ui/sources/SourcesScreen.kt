package com.signal.reader.ui.sources

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.data.local.entity.SourceEntity
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.TimeUtils
import com.signal.reader.ui.theme.SignalTheme

@Composable
fun SourcesScreen(
    repository: FeedRepository,
    onSourceClick: (Long) -> Unit,
    onAddSourcesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val sources by repository.getAllSourcesFlow().collectAsState(initial = emptyList())
    val unreadCounts by repository.getUnreadCountsFlow().collectAsState(initial = emptyList())

    val unreadMap = unreadCounts.associate { it.sourceId to it.unreadCount }
    val latestMap = unreadCounts.associate { it.sourceId to it.latestArticleTime }

    // Order by most recent article
    val orderedSources = sources.sortedByDescending { latestMap[it.id] ?: it.lastFetched }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        if (orderedSources.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 56.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "No sources yet",
                    style = SignalTheme.typography.heroHeadline,
                    color = colors.ink
                )
                Text(
                    text = "Add a few sites and their articles will show up on Home.",
                    color = colors.mute,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(CircleShape)
                        .background(colors.ink)
                        .clickable { onAddSourcesClick() }
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "Add sources",
                        color = colors.bg,
                        style = SignalTheme.typography.pillLabel
                    )
                }
            }
        } else {
            // Top title row: "Sources", count line, "Add +" pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Sources",
                        style = SignalTheme.typography.screenTitle,
                        color = colors.ink
                    )
                    Text(
                        text = "${orderedSources.size} sources",
                        color = colors.mute,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.ink)
                        .clickable { onAddSourcesClick() }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = "Add +",
                        color = colors.bg,
                        style = SignalTheme.typography.pillLabel
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(orderedSources, key = { it.id }) { source ->
                    SourceRow(
                        source = source,
                        unreadCount = unreadMap[source.id] ?: 0,
                        latestArticleTime = latestMap[source.id],
                        onClick = { onSourceClick(source.id) }
                    )
                }

                item {
                    HorizontalDivider(color = colors.line, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceEntity,
    unreadCount: Int,
    latestArticleTime: Long?,
    onClick: () -> Unit
) {
    val colors = SignalTheme.colors
    val initials = getInitials(source.name)

    val subtitle = when {
        source.isBroken -> "Feed isn’t loading. Tap to check."
        source.muted -> "Muted"
        latestArticleTime != null && latestArticleTime > 0 -> "Latest ${TimeUtils.formatRelativeTime(latestArticleTime)}"
        else -> "Latest recently"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        HorizontalDivider(color = colors.line, thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .alpha(if (source.muted) 0.5f else 1.0f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 48x48 Initials Square
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.ink),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = colors.bg,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }

            // Info: Name & Subtitle
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = source.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.ink,
                    letterSpacing = (-0.01).sp
                )
                Text(
                    text = subtitle,
                    color = colors.mute,
                    fontSize = 13.sp
                )
            }

            // Right indicator: Broken (!) or Unread count with accent dot
            if (source.isBroken) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.bg)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "!",
                        color = colors.ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            } else if (unreadCount > 0 && !source.muted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                    )
                    Text(
                        text = unreadCount.toString(),
                        color = colors.ink,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

fun getInitials(name: String): String {
    val clean = name.filter { it.isLetter() || it == '*' }
    return clean.take(2).uppercase()
}
