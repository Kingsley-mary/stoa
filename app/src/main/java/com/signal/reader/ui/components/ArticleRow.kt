package com.signal.reader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.signal.reader.data.feed.ImageResolver
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale
import com.signal.reader.ui.theme.signalSharedImage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ArticleRow(
    article: ArticleEntity,
    onClick: () -> Unit,
    onToggleSave: () -> Unit,
    onToggleRead: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val haptics = rememberSignalHaptics()
    val coroutineScope = rememberCoroutineScope()

    val offsetX = remember { Animatable(0f) }
    var isArmedRight by remember { mutableStateOf(false) }
    var isArmedLeft by remember { mutableStateOf(false) }
    var hasHapticFired by remember { mutableStateOf(false) }
    val isArmed = isArmedRight || isArmedLeft

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val thumbScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = SignalMotion.PressSpringSpec,
        label = "thumbScale"
    )

    val underlayBg by animateColorAsState(
        targetValue = if (isArmed) colors.ink else colors.card,
        animationSpec = tween(150),
        label = "underlayBg"
    )

    val underlayContentColor by animateColorAsState(
        targetValue = if (isArmed) colors.bg else colors.ink,
        animationSpec = tween(150),
        label = "underlayContent"
    )

    val thresholdPx = 90f * 2.5f
    val resistanceLimit = 110f * 2.5f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(underlayBg)
    ) {
        // Left Underlay (shown when swiping right): reveals Save / Unsave bookmark
        if (offsetX.value >= 0f) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BookmarkIcon(
                    isFilled = isArmedRight || article.saved,
                    color = underlayContentColor,
                    modifier = Modifier
                        .size(20.dp)
                        .scale(if (isArmedRight) 1.25f else 1f)
                )
                Text(
                    text = if (article.saved) "Unsave" else "Save",
                    color = underlayContentColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }

        // Right Underlay (shown when swiping left): reveals Mark read / Mark unread
        if (offsetX.value < 0f) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (article.read) "Mark unread" else "Mark read",
                    color = underlayContentColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .scale(if (isArmedLeft) 1.3f else 1f)
                        .clip(CircleShape)
                        .background(if (article.read) colors.accent else underlayContentColor)
                )
            }
        }

        // Foreground (.fg): article row content that slides horizontally
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(colors.bg)
                .signalPressScale(0.985f)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            val newRaw = offsetX.value + delta
                            val damped = when {
                                newRaw > resistanceLimit -> resistanceLimit + (newRaw - resistanceLimit) * 0.3f
                                newRaw < -resistanceLimit -> -resistanceLimit + (newRaw + resistanceLimit) * 0.3f
                                else -> newRaw
                            }
                            offsetX.snapTo(damped)

                            val nowArmedRight = damped >= thresholdPx
                            val nowArmedLeft = damped <= -thresholdPx
                            if (nowArmedRight != isArmedRight || nowArmedLeft != isArmedLeft) {
                                isArmedRight = nowArmedRight
                                isArmedLeft = nowArmedLeft
                                if ((nowArmedRight || nowArmedLeft) && !hasHapticFired) {
                                    haptics.tick()
                                    hasHapticFired = true
                                } else if (!nowArmedRight && !nowArmedLeft) {
                                    hasHapticFired = false
                                }
                            }
                        }
                    },
                    onDragStopped = {
                        coroutineScope.launch {
                            if (isArmedRight) {
                                onToggleSave()
                            } else if (isArmedLeft) {
                                if (onToggleRead != null) {
                                    onToggleRead()
                                } else {
                                    com.signal.reader.SignalApplication.instance.repository.toggleRead(article.id)
                                }
                            }
                            isArmedRight = false
                            isArmedLeft = false
                            hasHapticFired = false
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = SignalMotion.SwipeSpringSpec
                            )
                        }
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    if (kotlin.math.abs(offsetX.value) < 10f) {
                        onClick()
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = colors.line, thickness = 1.dp)

                val effectiveImage = remember(article) {
                    ImageResolver.getEffectiveCoverImage(article.imageUrl, article.fullContent, article.excerpt)
                }

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
                        // Meta line: [Unread dot] Source · Time · [Saved]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnimatedVisibility(
                                visible = !article.read,
                                exit = shrinkHorizontally(animationSpec = SignalMotion.easeTween(250))
                            ) {
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
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Text(
                                text = "·",
                                style = SignalTheme.typography.metaTime,
                                color = colors.mute
                            )

                            Text(
                                text = TimeUtils.formatRelativeTime(article.publishedTime),
                                style = SignalTheme.typography.metaTime,
                                color = colors.mute
                            )

                            if (article.saved) {
                                Text(
                                    text = "·",
                                    style = SignalTheme.typography.metaTime,
                                    color = colors.mute
                                )
                                Text(
                                    text = "Saved",
                                    style = SignalTheme.typography.metaSource,
                                    color = colors.ink
                                )
                            }
                        }

                        // Headline
                        Text(
                            text = article.title,
                            style = SignalTheme.typography.rowHeadline,
                            color = colors.ink
                        )

                        // Sub-headline line: x min read
                        Text(
                            text = TimeUtils.formatReadTime(article.fullContent, article.excerpt),
                            style = SignalTheme.typography.metaTime,
                            color = colors.mute
                        )
                    }

                    // 88x88 Thumbnail (if article has an image or extracted image from content)
                    if (!effectiveImage.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .scale(thumbScale)
                                .signalSharedImage(
                                    key = "article_image_${article.id}",
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.card)
                        ) {
                            AsyncImage(
                                model = effectiveImage,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
