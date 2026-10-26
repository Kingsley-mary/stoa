package com.signal.reader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.ui.theme.SignalTheme

@Composable
fun HeroCard(
    article: ArticleEntity,
    onClick: () -> Unit,
    showSourceName: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val hasImage = !article.imageUrl.isNullOrBlank()

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "cardScale"
    )

    val goOffsetX by animateFloatAsState(
        targetValue = if (isPressed) 3f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "goOffsetX"
    )
    val goOffsetY by animateFloatAsState(
        targetValue = if (isPressed) -3f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "goOffsetY"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (hasImage) {
            // Standard 4:5 image card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
                    .scale(cardScale)
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.card)
            ) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // ↗ accent button on bottom-right corner
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

            // Headline underneath
            Text(
                text = article.title.uppercase(),
                style = SignalTheme.typography.heroHeadline,
                color = colors.ink,
                maxLines = 4,
                modifier = Modifier
                    .padding(top = 18.dp, bottom = 12.dp)
                    .alpha(if (article.read) 0.45f else 1.0f)
            )
        } else {
            // Flat type card: 1:1 aspect ratio square card-coloured block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .scale(cardScale)
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.card)
                    .padding(22.dp)
            ) {
                Text(
                    text = article.title.uppercase(),
                    style = SignalTheme.typography.heroHeadline,
                    color = colors.ink,
                    maxLines = 4,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(end = 56.dp)
                        .alpha(if (article.read) 0.45f else 1.0f)
                )

                // ↗ accent button on bottom-right corner
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
            modifier = Modifier.alpha(if (article.read) 0.5f else 1.0f)
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
