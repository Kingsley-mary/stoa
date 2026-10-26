package com.signal.reader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.ui.theme.SignalTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ArticleRow(
    article: ArticleEntity,
    onClick: () -> Unit,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val offsetX = remember { Animatable(0f) }
    var isArmed by remember { mutableStateOf(false) }
    var hasHapticFired by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val thumbScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
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

    val thresholdPx = 90f * 2.5f // standard ~90dp
    val resistanceLimit = 110f * 2.5f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(124.dp)
            .background(underlayBg)
    ) {
        // Underlay: reveals Save / Unsave bookmark
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BookmarkIcon(
                isFilled = isArmed || article.saved,
                color = underlayContentColor,
                modifier = Modifier
                    .size(20.dp)
                    .scale(if (isArmed) 1.25f else 1f)
            )
            Text(
                text = if (article.saved) "Unsave" else "Save",
                color = underlayContentColor,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }

        // Foreground: article row content that slides rightward
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(colors.bg)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            val newRaw = (offsetX.value + delta).coerceAtLeast(0f)
                            val damped = if (newRaw > resistanceLimit) {
                                resistanceLimit + (newRaw - resistanceLimit) * 0.2f
                            } else {
                                newRaw
                            }
                            offsetX.snapTo(damped)

                            val nowArmed = damped >= thresholdPx
                            if (nowArmed != isArmed) {
                                isArmed = nowArmed
                                if (nowArmed && !hasHapticFired) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    hasHapticFired = true
                                } else if (!nowArmed) {
                                    hasHapticFired = false
                                }
                            }
                        }
                    },
                    onDragStopped = {
                        coroutineScope.launch {
                            if (isArmed) {
                                onToggleSave()
                            }
                            isArmed = false
                            hasHapticFired = false
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    if (offsetX.value < 10f) {
                        onClick()
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                HorizontalDivider(color = colors.line, thickness = 1.dp)

                Row(
                    modifier = Modifier
                        .fillMaxSize()
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AnimatedVisibility(
                                visible = !article.read,
                                exit = shrinkHorizontally(animationSpec = tween(250))
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
                                color = colors.ink
                            )

                            Text(
                                text = TimeUtils.formatRelativeTime(article.publishedTime),
                                style = SignalTheme.typography.metaTime,
                                color = colors.mute
                            )

                            if (article.saved) {
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
                            color = colors.ink,
                            maxLines = 2
                        )
                    }

                    // 88x88 Thumbnail (if article has an image)
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
    }
}

@Composable
fun BookmarkIcon(
    isFilled: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.25f, size.height * 0.125f)
            lineTo(size.width * 0.75f, size.height * 0.125f)
            lineTo(size.width * 0.75f, size.height * 0.875f)
            lineTo(size.width * 0.5f, size.height * 0.7f)
            lineTo(size.width * 0.25f, size.height * 0.875f)
            close()
        }
        if (isFilled) {
            drawPath(path = path, color = color)
        } else {
            drawPath(
                path = path,
                color = color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.6f * density,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
    }
}
