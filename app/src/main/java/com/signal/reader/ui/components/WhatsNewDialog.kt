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
        version = "v1.4.14",
        isCurrent = true,
        summary = "Polygon & Hypebeast Media Engine, Test Alert ANR Fix & Auto-Refresh",
        markdownNotes = """
### Test Alert Non-Blocking Fix
- **Instant Diagnostics**: Dispatches test publication notifications asynchronously on background IO threads, completely resolving the UI freeze / ANR on Android 11+.

### Hypebeast Galleries & Clean Cover Resolution
- **Gallery Extraction Engine**: Parses JavaScript carousel galleries (`[data-images]`) into native editorial figures, displaying full photo galleries inline in the reader.
- **Hex Entity Sanitization**: Resolves Hypebeast OpenGraph and Twitter card cover artwork by unescaping HTML hex entities (`&#x3A;`, `&#x2F;`, `&#x3F;`, `&amp;`).
- **Retroactive Cover & Excerpt Backfill**: Pull-to-refresh now backfills missing covers, excerpts, and full text for existing saved articles.

### Polygon Media & In-Content Video Player
- **Embedded Story Videos**: In-content video players (such as Polygon / AdsNinja story trailers) are preserved and rendered as native, responsive video players in the reader.
- **Lazy-Load Image Promotion**: Promotes lazy loaded `data-img-url`, `data-src`, `data-orig-file`, and `<picture>` sources to full-res images, bypassing dummy base64 placeholders.
- **Captions & Figcaption Support**: Added support for `.image-caption`, `small`, and custom caption tags across modern CMS layouts.

### Identity Designed & Dormant Publication Auto-Refresh
- **Instant Source Auto-Refresh**: Navigating to any source with 0 articles automatically triggers a live sync.
- **Archival Protection**: The 15 newest articles for every followed source are protected indefinitely regardless of publication year.
""".trimIndent()
    ),
    ReleaseNote(
        version = "v1.4.13",
        isCurrent = false,
        summary = "Reliable In-App Installer, CDATA Image Engine & Notification Diagnostics",
        markdownNotes = """
### In-App Package Installer & Browser Fallback
- **Reliable Package Installer**: Downloaded APKs are now stored in accessible app cache with explicit installer URI permissions, eliminating package installer denials on Android 12–15.
- **Browser Download Fallback**: Added a direct browser download fallback to update prompts and error sheets, guaranteeing you can always install updates seamlessly.
- **Never Left Wondering**: Error states now remain visible on screen with actionable diagnostics instead of silently closing.

### Robust CDATA Image & HTML Entity Engine
- **Full CDATA & Chunk Parsing**: Re-engineered XML pull parser's text extraction to gather multiple buffers and CDATA sections without skipping tags or dropping images (fixes Hypebeast, etc.).
- **URL Entity Sanitization**: Automatically strips stray HTML entities (e.g. `&amp;`) from image URLs before passing to Coil.
- **Continuous Cover Backfilling**: Every feed refresh continuously triggers background cover resolution for all articles missing artwork.

### Publication Alert Diagnostics & Permission
- **Runtime Notification Permission**: Added automatic runtime notification permission prompts on Android 13+ devices.
- **Send Test Alert**: Dedicated diagnostic action in Settings > Notifications to immediately verify system channels, sounds, and lock screen presentation.
- **Background Battery Optimization Shortcut**: One-tap access to Android's battery optimization settings to prevent background sync from being slept by OEM battery managers.
""".trimIndent()
    ),
    ReleaseNote(
        version = "v1.4.12",
        isCurrent = false,
        summary = "Cover Photo Engine, Slow Feed Protection & Canonical Feeds",
        markdownNotes = """
### Cover Photo Resolution Engine
- **Unblocked Scraper Queue**: Upgraded background OpenGraph scraper to 4 concurrent workers with sentinel tracking, eliminating head-of-line blocking so all articles get their cover photos.
- **Image Backfill on Refresh**: Existing articles in your database are automatically updated with fresh cover images on pull-to-refresh.

### Slow & Dormant Feed Protection
- **Archival Retention**: Stoa now protects the 15 most recent articles of every publication from the 30-day cleanup, ensuring seasonal or slow-publishing publications (like Identity Designed) are never wiped out.
- **Directory Search**: Added Identity Designed directly into the Visual Art & Design directory.

### Feed Reliability
- **Direct Polygon Feed**: Migrated Polygon’s catalog feed directly to `/feed/` to bypass intermediate redirect timeouts.
""".trimIndent()
    ),
    ReleaseNote(
        version = "v1.4.11",
        isCurrent = false,
        summary = "Publication Notification Icons, Quote Share & Button Tokens",
        markdownNotes = """
### Publication Notification Icons
- **Source Logos on Alerts**: Notifications automatically resolve and display the publication's high-resolution icon (`setLargeIcon`), with fallback monograms.
- **Hero Image Integration**: Cover photos expand smoothly with `BigPictureStyle` on single article alerts.

### Text Selection Quote Share
- **Floating Action Bar**: Highlighting text in any article features **Share quote** alongside Copy and Select all, directly opening the Quote Card Sheet.

### Source Alerts Beside Mute
- **Streamlined Placement**: Dedicated alert toggle pill right beside the Mute button on source pages.
""".trimIndent()
    ),
    ReleaseNote(
        version = "v1.4.10",
        isCurrent = false,
        summary = "Highlight Quote Share, Alerts Beside Mute & Markdown Changelogs",
        markdownNotes = """
### Text Selection Quote Share
- Highlighting text in any article now features **Share quote** in the floating action bar.

### Markdown Changelogs & What's New
- Update notes render clean headings, bullet points, and typography.
- Added dedicated **What’s new in Stoa** view in Settings.
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
