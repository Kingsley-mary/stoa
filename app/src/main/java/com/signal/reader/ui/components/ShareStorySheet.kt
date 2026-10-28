package com.signal.reader.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPressScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareStorySheet(
    article: ArticleEntity,
    paragraphs: List<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = SignalTheme.colors
    val haptics = rememberSignalHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Check clipboard for recent text copied from article
    val clipboardText = remember {
        try {
            val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipManager?.primaryClip?.getItemAt(0)?.text?.toString()
            if (!item.isNullOrBlank() && item.length > 5) item.trim() else null
        } catch (_: Exception) {
            null
        }
    }

    var selectedQuote by remember {
        mutableStateOf(
            clipboardText
                ?: paragraphs.firstOrNull { it.length > 30 }
                ?: article.excerpt.ifBlank { article.title }
        )
    }

    var showQuoteEditor by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // Header: STOA · SHARE STORY
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "STOA · SHARE STORY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = colors.mute
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.ink)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = colors.line, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Option 1: Share Link Action Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.card)
                    .signalPressScale(0.98f) {
                        haptics.tick()
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, article.title)
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "${article.title}\n\nVia ${article.sourceName} on Stoa\n${article.url}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share article link"))
                        onDismiss()
                    }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.ink),
                        contentAlignment = Alignment.Center
                    ) {
                        ShareIcon(color = colors.bg, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Share article link",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink
                        )
                        Text(
                            text = article.url,
                            fontSize = 12.sp,
                            color = colors.mute,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text("↗", fontSize = 16.sp, color = colors.ink, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Option 2: Typographic Quote Card Generator
            Text(
                text = "TYPOGRAPHIC QUOTE CARD",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = colors.mute
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Quote Card Live Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.bg)
                    .border(1.5.dp, colors.line, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STOA · ${article.sourceName.uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = colors.mute
                        )
                        Text("1080×1350", fontSize = 10.sp, color = colors.mute)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "“",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = colors.accent
                    )

                    Text(
                        text = selectedQuote,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontFamily = FontFamily.Serif,
                        color = colors.ink,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = colors.line.copy(alpha = 0.5f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = article.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!article.author.isNullOrBlank()) {
                        Text(
                            text = "by ${article.author}",
                            fontSize = 11.sp,
                            color = colors.mute
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Use Copied Text button if available
            if (!clipboardText.isNullOrBlank() && clipboardText != selectedQuote) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .border(1.dp, colors.accent, CircleShape)
                        .background(colors.accent.copy(alpha = 0.08f))
                        .signalPressScale(0.96f) {
                            haptics.tick()
                            selectedQuote = clipboardText
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Use copied text: \"${clipboardText.take(30)}...\"",
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Paragraph excerpt chips to easily select passages
            if (paragraphs.isNotEmpty()) {
                Text(
                    text = "Or choose passage from article:",
                    fontSize = 12.sp,
                    color = colors.mute
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    paragraphs.take(4).forEachIndexed { index, pText ->
                        val isSelected = selectedQuote == pText
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) colors.card else Color.Transparent)
                                .border(1.dp, if (isSelected) colors.ink else colors.line, RoundedCornerShape(10.dp))
                                .clickable {
                                    haptics.tick()
                                    selectedQuote = pText
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "¶ ${pText.take(90)}...",
                                fontSize = 12.sp,
                                color = colors.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Toggle custom quote text editing
            if (!showQuoteEditor) {
                Text(
                    text = "Edit quote text ✎",
                    fontSize = 12.sp,
                    color = colors.mute,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { showQuoteEditor = true }
                        .padding(vertical = 4.dp)
                )
            } else {
                OutlinedTextField(
                    value = selectedQuote,
                    onValueChange = { selectedQuote = it },
                    label = { Text("Custom Quote Text", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.ink,
                        unfocusedBorderColor = colors.line,
                        focusedTextColor = colors.ink,
                        unfocusedTextColor = colors.ink
                    )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Vermilion CTA: Generate and Share Quote Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(colors.accent)
                    .signalPressScale(0.96f) {
                        haptics.pop()
                        QuoteCardGenerator.generateAndShare(
                            context = context,
                            quote = selectedQuote,
                            sourceName = article.sourceName,
                            articleTitle = article.title,
                            author = article.author,
                            isDark = colors.bg == com.signal.reader.ui.theme.DarkBg
                        )
                        onDismiss()
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Share Quote Card Image ↗",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
