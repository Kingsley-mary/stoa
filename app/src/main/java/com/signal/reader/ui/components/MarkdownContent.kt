package com.signal.reader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.ui.theme.SignalColors
import com.signal.reader.ui.theme.SignalTheme

@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val lines = remember(markdown) { markdown.trim().lines() }

    Column(modifier = modifier) {
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            when {
                // Heading 1, 2, 3
                line.startsWith("###") -> {
                    Text(
                        text = line.removePrefix("###").trim(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("##") -> {
                    Text(
                        text = line.removePrefix("##").trim(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("#") -> {
                    Text(
                        text = line.removePrefix("#").trim(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                    )
                }
                // Bullet item
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val bulletText = line.substring(2).trim()
                    Row(
                        modifier = Modifier.padding(vertical = 2.5.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "•",
                            color = colors.ink,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = parseInlineMarkdown(bulletText, colors),
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            color = colors.ink
                        )
                    }
                }
                // Standard paragraph
                else -> {
                    Text(
                        text = parseInlineMarkdown(line, colors),
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        color = colors.mute,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

fun parseInlineMarkdown(text: String, colors: SignalColors): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val length = text.length

        while (cursor < length) {
            // Check for **bold**
            if (text.startsWith("**", cursor)) {
                val end = text.indexOf("**", cursor + 2)
                if (end != -1) {
                    val boldContent = text.substring(cursor + 2, end)
                    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.ink))
                    append(boldContent)
                    pop()
                    cursor = end + 2
                    continue
                }
            }

            // Check for *italic*
            if (text[cursor] == '*' && (cursor + 1 < length && text[cursor + 1] != '*')) {
                val end = text.indexOf('*', cursor + 1)
                if (end != -1) {
                    val italicContent = text.substring(cursor + 1, end)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(italicContent)
                    pop()
                    cursor = end + 1
                    continue
                }
            }

            // Check for `code`
            if (text[cursor] == '`') {
                val end = text.indexOf('`', cursor + 1)
                if (end != -1) {
                    val codeContent = text.substring(cursor + 1, end)
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = colors.ink))
                    append(codeContent)
                    pop()
                    cursor = end + 1
                    continue
                }
            }

            // Regular char
            append(text[cursor])
            cursor++
        }
    }
}
