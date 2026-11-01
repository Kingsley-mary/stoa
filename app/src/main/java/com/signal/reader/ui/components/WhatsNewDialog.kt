package com.signal.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signal.reader.ui.theme.SignalTheme
import com.signal.reader.ui.theme.signalPillButton

data class ReleaseNote(
    val version: String,
    val isCurrent: Boolean = false,
    val summary: String,
    val markdownNotes: String
)

val STOA_RECENT_RELEASES = listOf(
    ReleaseNote(
        version = "v1.4.10",
        isCurrent = true,
        summary = "Highlight Quote Share, Alerts Beside Mute & Button Tokens",
        markdownNotes = """
### Text Selection Quote Share
- **Floating Action Bar**: Highlighting text in any article now features **Share quote** alongside Copy and Select all.
- **Instant Card Generator**: Directly launches Stoa's Quote Card Sheet pre-filled with your highlighted passage.

### Source Alerts Beside Mute
- **Streamlined Placement**: Relocated notification toggle from header to a dedicated pill right beside the Mute button.
- **Clear Active State**: Solid black pill when active; outline pill when muted.

### Tactile Button Tokens
- **Hairline Protection**: Unified design token prevents hairlines from disappearing on press and eliminates double borders.

### Markdown Changelogs & What's New
- **Rich Formatting**: Update notes render clean headings, bullet points, and typography instead of raw Markdown.
- **Release History**: Dedicated **What’s new in Stoa** view in Settings.
""".trimIndent()
    ),
    ReleaseNote(
        version = "v1.4.9",
        isCurrent = false,
        summary = "Per-Source Notification Channels & Publication Alerts",
        markdownNotes = """
### Per-Source Notification Channels
- **Android System Integration**: Channels grouped neatly under "Publications" in Android Settings.
- **Publication Alerts Manager**: Centralized alerts modal in Settings with bulk controls and deep links to system channels.
- **Silent Launch Checks**: Background update checks run silently without interrupting your reading.
""".trimIndent()
    ),
    ReleaseNote(
        version = "v1.4.8",
        isCurrent = false,
        summary = "Cover Image Engine, Android 16 Links & Earlier Read Pill",
        markdownNotes = """
### Cover Image Resolution Engine
- **Creative Boom & High-Res CDN**: Upgraded asset resolvers for crystal-clear cover photos.
- **YouTube Video Posters**: Automatic thumbnail extraction from embed iframes.

### Android 16 Web Links
- Removed `autoVerify` constraint so links from your followed publications open reliably in Stoa.

### Earlier Read Floating Pill
- Discreet bottom-right pill that auto-hides on scroll down and returns when pausing or scrolling up.
""".trimIndent()
    )
)

@Composable
fun WhatsNewDialog(
    onDismiss: () -> Unit
) {
    val colors = SignalTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bg,
        titleContentColor = colors.ink,
        textContentColor = colors.mute,
        shape = RoundedCornerShape(22.dp),
        title = {
            Column {
                Text(
                    text = "WHAT'S NEW",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = colors.mute
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Recent Updates",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                    fontFamily = FontFamily.Serif
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                items(STOA_RECENT_RELEASES.size) { index ->
                    val release = STOA_RECENT_RELEASES[index]

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = release.version,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink
                            )

                            if (release.isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(colors.ink)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Current",
                                        color = colors.bg,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Text(
                            text = release.summary,
                            fontSize = 13.sp,
                            color = colors.mute,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.card)
                                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            MarkdownContent(markdown = release.markdownNotes)
                        }
                    }

                    if (index < STOA_RECENT_RELEASES.size - 1) {
                        HorizontalDivider(color = colors.line, thickness = 1.dp)
                    }
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .signalPillButton(
                        backgroundColor = colors.ink,
                        borderColor = colors.ink,
                        onClick = onDismiss
                    )
                    .padding(horizontal = 22.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Done",
                    color = colors.bg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}
