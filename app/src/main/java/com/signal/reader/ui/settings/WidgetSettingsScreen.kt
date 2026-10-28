package com.signal.reader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.signal.reader.data.local.SettingsManager
import com.signal.reader.data.repository.FeedRepository
import com.signal.reader.ui.components.BackChevron
import com.signal.reader.ui.components.TimeUtils
import com.signal.reader.ui.components.rememberSignalHaptics
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale
import com.signal.reader.widget.StoaWidgetProvider

@Composable
fun WidgetSettingsScreen(
    settingsManager: SettingsManager,
    repository: FeedRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = SignalTheme.colors
    val haptics = rememberSignalHaptics()
    val settings by settingsManager.settingsFlow.collectAsState()
    val articles by repository.getHomeStreamFlow().collectAsState(initial = emptyList())

    val unreadCount = articles.count { !it.read }
    val heroArticle = articles.firstOrNull { !it.read } ?: articles.firstOrNull()

    val currentBg = settings.widgetBackground
    val isSystemDark = isSystemInDarkTheme()

    val effectiveBg = when (currentBg) {
        "system" -> if (isSystemDark) "dark" else "light"
        else -> currentBg
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Sticky Header with Back Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .signalPressScale(0.94f) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                BackChevron(color = colors.ink)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "WIDGET SETTINGS",
                style = SignalTheme.typography.screenTitle,
                fontSize = 18.sp,
                color = colors.ink
            )
        }

        HorizontalDivider(color = colors.line, thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .navigationBarsPadding()
        ) {
            // Live Widget Preview Header
            Text(
                text = "LIVE WIDGET PREVIEW",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = colors.mute
            )
            Spacer(modifier = Modifier.height(10.dp))

            // 4×2 Ratio Live Widget Preview Card with 24dp Rounded Corners
            val previewCardBg = when (effectiveBg) {
                "light" -> Color.White
                "dark" -> Color(0xFF10100F)
                "article_image" -> Color(0xFF10100F)
                else -> Color(0xFFEEEAE1) // paper
            }
            val previewTextColor = when (effectiveBg) {
                "dark", "article_image" -> Color(0xFFF1EDE4)
                else -> Color(0xFF141413)
            }
            val previewMetaColor = when (effectiveBg) {
                "dark", "article_image" -> Color(0xFF9C978B)
                else -> Color(0xFF6B665C)
            }
            val previewBorderColor = when (effectiveBg) {
                "dark", "article_image" -> Color(0x33F1EDE4)
                "light" -> Color(0x1F000000)
                else -> Color(0x29141413)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.95f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(previewCardBg)
                    .border(1.dp, previewBorderColor, RoundedCornerShape(24.dp))
            ) {
                // Background Article Image if article_image mode
                if (effectiveBg == "article_image" && !heroArticle?.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = heroArticle!!.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // High-contrast dark gradient scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x80000000),
                                        Color(0xB3000000),
                                        Color(0xF2000000)
                                    )
                                )
                            )
                    )
                }

                // Widget Content Layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar: STOA + Unread Pill + Settings Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "STOA",
                            color = previewTextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Unread Badge
                            val badgeBg = if (effectiveBg == "dark" || effectiveBg == "article_image") Color(0xFFE4472B) else Color(0xFF141413)
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(badgeBg)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (unreadCount > 0) "$unreadCount unread" else "All read",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Settings gear icon preview
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "⚙",
                                    fontSize = 12.sp,
                                    color = previewTextColor
                                )
                            }
                        }
                    }

                    // Headline
                    Text(
                        text = heroArticle?.title ?: "Select a background style below to preview your home screen widget",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 19.sp,
                        color = previewTextColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Meta Row
                    val metaText = if (heroArticle != null) {
                        "${heroArticle.sourceName.uppercase()} · ${TimeUtils.formatRelativeTime(heroArticle.publishedTime)}"
                    } else "STOA READER"
                    Text(
                        text = metaText,
                        fontSize = 11.sp,
                        color = previewMetaColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Background Style Selector Section
            Text(
                text = "BACKGROUND STYLE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = colors.mute
            )
            Spacer(modifier = Modifier.height(12.dp))

            val options = listOf(
                WidgetStyleOption(
                    id = "paper",
                    title = "Warm Paper",
                    description = "Stoa classic (#EEEAE1) with black typography",
                    previewColor = Color(0xFFEEEAE1),
                    textColor = Color(0xFF141413)
                ),
                WidgetStyleOption(
                    id = "light",
                    title = "Plain White",
                    description = "Crisp #FFFFFF background with high contrast",
                    previewColor = Color(0xFFFFFFFF),
                    textColor = Color(0xFF141413)
                ),
                WidgetStyleOption(
                    id = "dark",
                    title = "Plain Black",
                    description = "Deep #10100F card with cream text & vermilion badge",
                    previewColor = Color(0xFF10100F),
                    textColor = Color(0xFFF1EDE4)
                ),
                WidgetStyleOption(
                    id = "system",
                    title = "Match System (OS)",
                    description = "Automatically adapts to Android dark or light theme",
                    previewColor = if (isSystemDark) Color(0xFF10100F) else Color(0xFFFFFFFF),
                    textColor = if (isSystemDark) Color(0xFFF1EDE4) else Color(0xFF141413)
                ),
                WidgetStyleOption(
                    id = "article_image",
                    title = "Article Hero Photo",
                    description = "Lead photo with dark gradient scrim overlay for maximum clarity",
                    previewColor = Color(0xFF242320),
                    textColor = Color.White
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEach { opt ->
                    val isSelected = currentBg == opt.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.card)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) colors.accent else colors.line,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .signalPressScale(0.98f) {
                                haptics.pop()
                                settingsManager.updateWidgetBackground(opt.id)
                                StoaWidgetProvider.updateAllWidgets(context)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Swatch preview circle
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(opt.previewColor)
                                        .border(1.dp, colors.line, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (opt.id == "article_image") {
                                        Text("🖼", fontSize = 16.sp)
                                    } else {
                                        Text(
                                            "Aa",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = opt.textColor
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = opt.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = opt.description,
                                        fontSize = 12.sp,
                                        color = colors.mute,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            // Selection radio pill
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 6.dp else 1.5.dp,
                                        color = if (isSelected) colors.accent else colors.mute,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Quick Info Card on how to use widget
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.bg)
                    .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "ABOUT STOA WIDGET",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = colors.mute
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Tap the story headline on your home screen to jump directly into the reader.\n• Tap the ⚙ icon in the top right of the widget to quickly change your widget theme anytime.\n• Rounded corners (24dp) dynamically match your home screen grid.",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = colors.ink
                    )
                }
            }
        }
    }
}

private data class WidgetStyleOption(
    val id: String,
    val title: String,
    val description: String,
    val previewColor: Color,
    val textColor: Color
)
