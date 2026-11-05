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
        version = "v1.4.19",
        isCurrent = true,
        summary = "Polygon & Hypebeast Image Resolution, Lazy-Loaded Media, Feed Enclosures, and WAF Negotiation",
        markdownNotes = """
### Polygon & Hypebeast Images & Galleries
- **Placeholder Dimension Tolerance**: Removed small width/height rejection checks so lazy-loaded 1x1 GIF placeholders or aspect ratio tags on Polygon and Hypebeast never drop high-resolution images.
- **Robust Srcset Parsing**: Added descriptor scoring (width `w` and pixel ratio `x`) with comma-safe lookahead splitting to preserve URLs with comma-separated query parameters.
- **Enclosure & Media Tag Upgrades**: Feed parser now captures `rel="enclosure"` image links in RSS and Atom feeds, and accepts `<enclosure>`/`<media:content>` tags with generic MIME types when the file extension is an image.
- **Sequential Container Priority**: Article body extraction uses prioritized selector lookup to ensure inner article containers are never shadowed by outer `<article>` tags.
- **Lookbook & Gallery Extraction**: Top-level galleries, carousel sliders, and JSON-LD image arrays are captured and prepended in reader view.
- **WAF Bot Negotiation**: Automated mobile User-Agent negotiation to bypass CloudFront WAF CAPTCHA challenges during extraction.
""".trimIndent()
    ),
    ReleaseNote(
        version = "v1.4.18",
        isCurrent = false,
        summary = "In-App Update Pop-Up Installer Launch, Polygon Media & Extraction, Hypebeast Galleries, and 256KB Head Crawl",
        markdownNotes = """
### In-App Update Dialog Fix
- **Direct Activity Launch**: UpdateDialog now observes the download reaching 100% and auto-launches the system Package Installer via Activity context (MainActivity), bypassing Android 11–16 Background Activity Launch blocks.
- **Install Update Button**: Added an explicit "Install Update" button fallback inside the update dialog so users can tap to install without getting stranded.
- **Installer Task Affinity**: Removed `FLAG_ACTIVITY_CLEAR_TOP` from package installer intents to prevent system package installer tasks from silently aborting.

### Polygon Media & Article Extraction
- **Word-Boundary Path Filtering**: Replaced substring image filters with path segment regex to ensure gaming/tech articles with keywords like "pixel", "iconic", or WordPress "/uploads/" are never dropped.
- **Comma-Safe Srcset Parsing**: Added lookahead srcset splitting so Polygon query parameters with commas (e.g. `?crop=0,0,100,100`) are parsed accurately without slicing the URL.
- **Picture & Source Promotion**: In readability extraction, `<picture><source srcset="...">` tags are promoted to full image tags and sanitized with boundary checks.
- **Embedded Video Preserved**: Embedded YouTube and Vimeo iframes are converted to native fallback player links instead of being dropped.

### Hypebeast Photo Galleries & Feed Images
- **Separate Lookbook Galleries**: Extracted lookbooks, product sliders, and carousels placed outside text containers (`.post-body-gallery`, `.swiper-wrapper`, `.post-gallery`) and integrated them directly into extracted stories.
- **Clean CDN Headers**: Added clean mobile browser headers (`User-Agent` and `Accept`) and removed hotlink-blocking referrers in Coil image requests.
- **256 KB Head Buffer**: Increased metadata crawler buffer from 32 KB to 256 KB to reliably extract OpenGraph covers past large inlined scripts and styles.
- **Auto-Retry Failed Images**: Reset failed `"none"` image statuses on feed refresh so existing cards immediately re-crawl missing cover images.
""".trimIndent()
    ),
    ReleaseNote(
        version = "v1.4.17",
        isCurrent = false,
        summary = "Polygon & Hypebeast Image Fixes, Spotify & Instagram Embeds, Link Separation, and Settings Markdown",
        markdownNotes = """
### Polygon & Hypebeast Visual Media
- **Polygon Key Art & Hero Covers**: Fixed tracking URL filter that was blacklisting clean artwork filenames ending in `-no-logo.jpg`. Promoted picture element sources and lead feature headers.
- **Hypebeast Image Loading**: Removed custom user-agent suffix from Coil's image loader to prevent CloudFront and Fastly CDN 403 Forbidden blocks.

### Rich Media Embeds & Social Integration
- **Native Spotify Player Cards**: Added Swiss-style Spotify cards for tracks, albums, playlists, and podcast episodes featuring high-resolution cover art, track titles, and a tactile "Play on Spotify ↗" button.
- **Instagram Preview Photos**: Preserved and rendered embedded image previews from Instagram, Twitter, and Bluesky embeds.
- **Paragraph Link Preservation**: Cleanly separated inline hyperlinks within editorial paragraphs from standalone embeds. Normal prose sentences with links are no longer wrapped into embed cards.

### Settings & Polish
- **Rich Markdown in Settings**: Fixed update changelog rendering beneath "Check now" in Settings to use full Markdown typography (headers, bold, bullet points).
""".trimIndent()
    ),
    ReleaseNote(
        version = "v1.4.16",
        isCurrent = false,
        summary = "Creative Boom Deduplication, In-App Updater Lifecycle Fix, Social Embeds & Dynamic Headline Scaling",
        markdownNotes = """
### Source & Article Deduplication
- **Eliminated Duplicate Feeds**: Harmonized canonical URLs between catalog presets and starter feeds (e.g. trailing slash differences for Creative Boom, Designboom, and MIT Technology Review).
- **Automated Startup Merge**: On launch, duplicate sources are automatically identified, merged, and cleaned up in SQLite without losing saved articles or read status.
- **Article Deduplication**: Purges duplicate article entries sharing GUID, URL, or title under merged sources.

### In-App Update Modal Lifecycle Fix
- **Persistent Download Scope**: Downloads now run on UpdateManager's persistent supervisor scope with `NonCancellable` IO dispatching, preventing UI recompositions from cancelling the download job.
- **Unified Dialog Presentation**: Streamlined dialog lifecycle in MainActivity so the update dialog remains mounted without being abruptly torn down during state transitions.
- **Update Error Recovery**: When errors occur, a structured error view with direct browser fallback and retry options prevents the modal from silently closing.

### Rich Native Social Media Embeds
- **Platform-Native Embed Cards**: Social links and blockquotes from **X / Twitter**, **Instagram**, **Bluesky**, **Threads**, **TikTok**, and **Reddit** now render as elegant, tactile cards in reader mode.
- **Author & Post Details**: Displays platform badge, author name, handle, clean post text, and media previews with a one-tap action to open in native apps or browser.

### Dynamic Hero Headline Scaling
- **Autosizing Typography**: Large top hero cards now dynamically scale headline typography based on character length (from 34sp down to 19sp across 5 allowed lines), ensuring long editorial titles fit comfortably without abrupt or jarring cutoffs.
- **Harmonized Animations**: Staggered word-by-word entrance animations adopt the dynamically computed font size and line height.

### Intentional Excerpt Reading Experience
- **Restored User Choice**: Removed automatic readability overrides on excerpt-only articles; reader mode presents the original excerpt alongside the intentional "Extract full article" and "Read on source" action pill.
- **Feed HTML Preservation**: Fixed parser bug that previously discarded HTML and inline media when an article was flagged as excerpt-only.
""".trimIndent()
    ),
    ReleaseNote(
        version = "v1.4.15",
        isCurrent = false,
        summary = "Stale Database Re-Resolution, Auto-Reader Extraction & Android 11 Package Installer",
        markdownNotes = """
### Zero Stale Images (Automatic Re-Resolution)
- **Automatic Startup Image Recovery**: Clears out legacy "none" placeholders left behind by earlier releases, automatically re-resolving cover artwork in the background via live RSS re-parsing and OpenGraph fallbacks.
- **Background Sync on Launch**: App startup automatically launches a non-blocking background refresh across all followed publications, populating missing cover artwork without requiring a manual pull-to-refresh.
- **Logo and SVG Filtering**: Prevents site logos and vector placeholders (e.g. `poly-logo-full-white.svg`) from taking precedence over genuine editorial cover photographs.

### Instant Reader Full Article & Gallery Auto-Extraction
- **Seamless Excerpt Expansion**: Opening excerpt-only stories (such as Polygon game interviews or Hypebeast gallery articles) now automatically triggers full readability extraction in the background—no more scrolling to the bottom to tap an experimental button.
- **Gallery Article Auto-Detection**: Articles indicating "view full gallery" or external galleries automatically load all inline high-res figures and videos into the reader.
- **Offline Persistence**: Extracted full article HTML and newly discovered hero artwork are persisted directly to SQLite for instant and offline reading.

### Android 11 Package Installer Visibility
- **Package Visibility Compliance**: Declared `<queries>` intent filters and permissions for archive installers on Android 11+ (API 30+), ensuring the system package installer is immediately reachable when tapping "Install now".
- **World-Readable Cache Permissions**: Sets universal read permissions on update APKs so system installers never fail with silent sandbox permission errors.
""".trimIndent()
    ),
    ReleaseNote(
        version = "v1.4.14",
        isCurrent = false,
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
