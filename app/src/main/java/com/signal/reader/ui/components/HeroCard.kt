package com.signal.reader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.ui.theme.SignalMotion
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalSharedImage
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HeroCard(
    article: ArticleEntity,
    onClick: () -> Unit,
    showSourceName: Boolean = true,
    animateWords: Boolean = false,
    slideIn: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val hasImage = !article.imageUrl.isNullOrBlank()

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = SignalMotion.PressSpringSpec,
        label = "cardScale"
    )

    val goOffsetX by animateFloatAsState(
        targetValue = if (isPressed) 3f else 0f,
        animationSpec = SignalMotion.PressSpringSpec,
        label = "goOffsetX"
    )
    val goOffsetY by animateFloatAsState(
        targetValue = if (isPressed) -3f else 0f,
        animationSpec = SignalMotion.PressSpringSpec,
        label = "goOffsetY"
    )

    // Slide in from right on chip swap (.hero.slide from translateX(28px), 450ms ease-out)
    val slideAnim = remember { Animatable(if (slideIn) 28f else 0f) }
    val alphaAnim = remember { Animatable(if (slideIn) 0f else 1f) }

    LaunchedEffect(article.id, slideIn) {
        if (slideIn) {
            slideAnim.snapTo(28f)
            alphaAnim.snapTo(0f)
            slideAnim.animateTo(0f, SignalMotion.easeTween(SignalMotion.HeroSlideDuration))
        }
    }
    LaunchedEffect(article.id, slideIn) {
        if (slideIn) {
            alphaAnim.animateTo(1f, SignalMotion.easeTween(SignalMotion.HeroSlideDuration))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset((slideAnim.value * density).roundToInt(), 0) }
            .alpha(alphaAnim.value)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (hasImage) {
            // Standard 4:5 image card (.pic)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
                    .scale(cardScale)
                    .signalSharedImage(
                        key = "article_image_${article.id}",
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        shape = RoundedCornerShape(26.dp)
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.card)
            ) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // ↗ accent button on bottom-right corner (.go)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .offset(x = goOffsetX.dp, y = goOffsetY.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↗",
                        color = Color.White,
                        fontSize = 22.sp
                    )
                }
            }

            // Headline underneath with word-by-word animation support
            HeroHeadlineText(
                title = article.title,
                read = article.read,
                animateWords = animateWords,
                modifier = Modifier.padding(top = 18.dp, bottom = 12.dp)
            )
        } else {
            // Flat type card: 1:1 aspect ratio square card-coloured block (.pic.flat)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .scale(cardScale)
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.card)
                    .padding(22.dp)
            ) {
                HeroHeadlineText(
                    title = article.title,
                    read = article.read,
                    animateWords = animateWords,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(end = 56.dp)
                )

                // ↗ accent button on bottom-right corner (.go)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = goOffsetX.dp, y = goOffsetY.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↗",
                        color = Color.White,
                        fontSize = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.size(12.dp))
        }

        // Meta line: [Unread dot] Source · Time · [Saved]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.alpha(if (article.read) 0.45f else 1.0f)
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

            if (showSourceName) {
                Text(
                    text = article.sourceName,
                    style = SignalTheme.typography.metaSource,
                    color = colors.ink
                )
            }

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
    }
}

/**
 * Headline rendering with word-by-word rising animation on first load
 * matching .w > span animation: wr .6s var(--ease) backwards, delay: i * 35ms + 120ms
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroHeadlineText(
    title: String,
    read: Boolean,
    animateWords: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors

    if (!animateWords || SignalTheme.reducedMotion) {
        Text(
            text = title.uppercase(),
            style = SignalTheme.typography.heroHeadline,
            color = colors.ink,
            maxLines = 4,
            modifier = modifier.alpha(if (read) 0.45f else 1.0f)
        )
    } else {
        val words = remember(title) { title.uppercase().split(" ").filter { it.isNotBlank() } }

        FlowRow(
            modifier = modifier
                .alpha(if (read) 0.45f else 1.0f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            words.forEachIndexed { idx, word ->
                WordRise(word = word, index = idx)
            }
        }
    }
}

@Composable
private fun WordRise(word: String, index: Int) {
    val offsetY = remember { Animatable(32f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val delayMs = index * SignalMotion.WordStaggerDuration + 120
        offsetY.animateTo(
            targetValue = 0f,
            animationSpec = SignalMotion.easeTween(600, delayMillis = delayMs)
        )
    }
    LaunchedEffect(Unit) {
        val delayMs = index * SignalMotion.WordStaggerDuration + 120
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = SignalMotion.easeTween(600, delayMillis = delayMs)
        )
    }

    Box(
        modifier = Modifier.clipToBounds()
    ) {
        Text(
            text = word,
            style = SignalTheme.typography.heroHeadline,
            color = SignalTheme.colors.ink,
            modifier = Modifier
                .offset { IntOffset(0, (offsetY.value * density).roundToInt()) }
                .alpha(alpha.value)
        )
    }
}
