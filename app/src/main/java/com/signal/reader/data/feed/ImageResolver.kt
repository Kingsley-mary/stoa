package com.signal.reader.data.feed

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.Locale

data class MediaCandidate(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val fileSize: Long = 0L,
    val isThumbnail: Boolean = false
)

object ImageResolver {

    // Match banned tokens ONLY on path/filename word boundaries so slugs like "pixel", "silicon", "iconic", or "uploads" are never blocked
    private val BANNED_PATH_SEGMENT_REGEX = Regex(
        """(?:^|[/_.-])(avatar|favicon|tracker|1x1|spacer|gravatar|feedburner|doubleclick|emoji|smiley|site-?logo|brand-?logo|header-?logo|footer-?logo)(?:[/_.-]|$)""",
        RegexOption.IGNORE_CASE
    )

    // Only strip analytics/tracking parameters; preserve CDN image sizing/cropping params (w, h, crop, fit, resize, quality)
    private val STRIP_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content"
    )

    private val IMG_TAG_REGEX = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val SOURCE_TAG_REGEX = Regex("""<source\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val ATTR_WIDTH_REGEX = Regex("""\bwidth\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE)
    private val ATTR_HEIGHT_REGEX = Regex("""\bheight\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE)
    private val ytEmbedRegex = Regex("""(?:youtube(?:-nocookie)?\.com/(?:embed/|v/|watch\?v=)|youtu\.be/)([a-zA-Z0-9_-]{11})""", RegexOption.IGNORE_CASE)

    fun extractFromHtml(html: String?, baseUrl: String? = null): String? {
        return extractAllFromHtml(html, baseUrl).firstOrNull()
    }

    fun extractAllFromHtml(html: String?, baseUrl: String? = null): List<String> {
        if (html.isNullOrBlank()) return emptyList()
        val results = mutableListOf<String>()

        for (match in IMG_TAG_REGEX.findAll(html)) {
            val tag = match.value
            val width = ATTR_WIDTH_REGEX.find(tag)?.groupValues?.get(1)?.toIntOrNull()
            val height = ATTR_HEIGHT_REGEX.find(tag)?.groupValues?.get(1)?.toIntOrNull()

            val rawUrl = extractAttr(tag, "data-lazy-src")
                ?: extractAttr(tag, "data-src")
                ?: extractAttr(tag, "data-original")
                ?: extractAttr(tag, "data-actualsrc")
                ?: extractAttr(tag, "data-hi-res-src")
                ?: extractAttr(tag, "data-srcset")?.let { extractBestUrlFromSrcset(it) }
                ?: extractAttr(tag, "srcset")?.let { extractBestUrlFromSrcset(it) }
                ?: extractAttr(tag, "src")?.takeUnless { it.trim().startsWith("data:") }

            if (rawUrl != null) {
                val resolved = resolveAndSanitize(rawUrl, baseUrl)
                if (resolved != null && isLikelyContentImage(resolved, width, height)) {
                    if (!results.contains(resolved)) {
                        results.add(resolved)
                    }
                }
            }
        }

        // Also check <source srcset="..."> inside <picture> tags (used by Polygon)
        for (match in SOURCE_TAG_REGEX.findAll(html)) {
            val tag = match.value
            val type = extractAttr(tag, "type")?.lowercase()
            if (type != null && !type.startsWith("image/")) continue

            val srcset = extractAttr(tag, "data-srcset") ?: extractAttr(tag, "srcset") ?: continue
            val rawUrl = extractBestUrlFromSrcset(srcset)
            if (rawUrl != null) {
                val resolved = resolveAndSanitize(rawUrl, baseUrl)
                if (resolved != null && isLikelyContentImage(resolved)) {
                    if (!results.contains(resolved)) {
                        results.add(resolved)
                    }
                }
            }
        }

        return results
    }

    fun extractMetaImage(html: String?, baseUrl: String? = null): String? {
        if (html.isNullOrBlank()) return null

        val metaKeys = listOf(
            "og:image:secure_url",
            "og:image:url",
            "og:image",
            "twitter:image:src",
            "twitter:image"
        )

        val metaTags = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html)
        for (key in metaKeys) {
            for (match in metaTags) {
                val tag = match.value
                val prop = extractAttr(tag, "property")?.lowercase()
                    ?: extractAttr(tag, "name")?.lowercase()
                    ?: extractAttr(tag, "itemprop")?.lowercase()
                if (prop == key) {
                    val content = extractAttr(tag, "content")
                    if (!content.isNullOrBlank()) {
                        val resolved = resolveAndSanitize(content, baseUrl)
                        if (resolved != null && isLikelyContentImage(resolved)) {
                            return resolved
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * Filters out tracking pixels, tiny icons, avatars, data URIs, and SVGs.
     * NOTE: Only reject small width/height (1..48) if the URL itself looks like an icon/pixel/avatar
     * and does NOT come from a lazy-load/srcset attribute, because sites like Polygon & Hypebeast
     * put width="1" height="1" or width="16" height="9" on lazy-loaded <img> placeholders!
     */
    fun isLikelyContentImage(url: String, width: Int? = null, height: Int? = null): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.startsWith("data:") || lower.startsWith("about:")) return false
        if (lower.endsWith(".svg") || lower.contains(".svg?")) return false
        if (lower.endsWith(".gif") && (lower.contains("spacer") || lower.contains("blank") || lower.contains("pixel") || lower.contains("1x1"))) {
            return false
        }

        if (lower.contains("doubleclick.net") ||
            lower.contains("feedburner.com") ||
            lower.contains("gravatar.com") ||
            lower.contains("s.w.org/images/core/emoji") ||
            lower.contains("wp-includes/images/smilies")
        ) {
            return false
        }

        val pathOnly = lower.substringBefore('?')
        // Explicitly allow clean artwork that indicates absence of watermark logos (e.g. Polygon key art ending in -no-logo.jpg)
        if (pathOnly.contains("no-logo") || pathOnly.contains("nologo")) return true
        if (BANNED_PATH_SEGMENT_REGEX.containsMatchIn(pathOnly)) return false
        if (Regex("""(?:^|[/_])(?:logo|icon)\.(?:png|jpe?g|gif|webp)""").containsMatchIn(pathOnly)) return false

        return true
    }

    fun resolveAndSanitize(rawUrl: String?, baseUrl: String? = null): String? {
        if (rawUrl.isNullOrBlank()) return null
        var cleaned = decodeHtmlEntities(rawUrl.trim())
        if (cleaned.startsWith("data:")) return null

        cleaned = when {
            cleaned.startsWith("//") -> "https:$cleaned"
            cleaned.startsWith("http://") || cleaned.startsWith("https://") -> cleaned
            !baseUrl.isNullOrBlank() -> {
                try {
                    URI(baseUrl).resolve(cleaned).toString()
                } catch (_: Exception) {
                    cleaned
                }
            }
            else -> cleaned
        }

        return sanitizeImageUrl(cleaned)
    }

    fun sanitizeImageUrl(url: String): String {
        var result = decodeHtmlEntities(url.trim())
        if (result.startsWith("//")) {
            result = "https:$result"
        } else if (result.startsWith("http://")) {
            result = "https://" + result.removePrefix("http://")
        }

        val qIndex = result.indexOf('?')
        if (qIndex != -1) {
            val base = result.substring(0, qIndex)
            val query = result.substring(qIndex + 1)
            val keptParams = query.split('&').filter { param ->
                val key = param.substringBefore('=').lowercase()
                key.isNotEmpty() && key !in STRIP_PARAMS
            }
            result = if (keptParams.isEmpty()) base else "$base?${keptParams.joinToString("&")}"
        }

        return result
    }

    fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&#038;", "&")
            .replace("&#38;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#x3A;", ":", ignoreCase = true)
            .replace("&#x2F;", "/", ignoreCase = true)
            .replace("&#x3F;", "?", ignoreCase = true)
            .replace("&#x3D;", "=", ignoreCase = true)
            .replace("&#x20;", " ", ignoreCase = true)
            .replace("&#x25;", "%", ignoreCase = true)
            .replace("&#58;", ":")
            .replace("&#47;", "/")
            .replace("&#63;", "?")
            .replace("&#61;", "=")
    }

    private fun extractAttr(tag: String, attrName: String): String? {
        val regex = Regex("""\b$attrName\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>"']+))""", RegexOption.IGNORE_CASE)
        val match = regex.find(tag) ?: return null
        val value = match.groups[1]?.value ?: match.groups[2]?.value ?: match.groups[3]?.value
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun extractFirstUrlFromSrcset(srcset: String): String? = extractBestUrlFromSrcset(srcset)

    /**
     * Safely parses a srcset attribute even when individual URLs contain commas in query params
     * or when descriptors use 1x/2x or 1200w.
     */
    fun extractBestUrlFromSrcset(srcset: String): String? {
        val decoded = decodeHtmlEntities(srcset).trim()
        if (decoded.isEmpty()) return null

        // Split on commas that are followed by whitespace and a URL (http://, https://, //, or /)
        // or standard relative image paths if no absolute/rooted URLs are present
        val entries = if (decoded.contains("://") || decoded.contains("//")) {
            decoded.split(Regex(""",\s*(?=https?://|//|/)"""))
        } else {
            decoded.split(Regex(""",\s*(?=https?://|//|/|[^\s,]+(?:\s+\d+[wx]|\s*$))"""))
        }
        var bestUrl: String? = null
        var maxScore = -1

        for (entry in entries) {
            val trimmedEntry = entry.trim().trimEnd(',')
            if (trimmedEntry.isEmpty()) continue
            val parts = trimmedEntry.split(Regex("""\s+"""))
            val url = parts.firstOrNull()?.trim()?.trimEnd(',') ?: continue
            if (url.isEmpty() || url.startsWith("data:") || url.startsWith("about:")) continue

            val descriptor = parts.getOrNull(1)?.lowercase() ?: ""
            val score = when {
                descriptor.endsWith("w") -> descriptor.dropLast(1).toIntOrNull() ?: 0
                descriptor.endsWith("x") -> ((descriptor.dropLast(1).toFloatOrNull() ?: 1f) * 1000).toInt()
                else -> 1
            }

            if (score >= maxScore) {
                maxScore = score
                bestUrl = url
            }
        }
        return bestUrl
    }

    fun cleanUrl(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return decodeHtmlEntities(raw.trim().trim('"', '\''))
    }

    fun isTrackingUrl(url: String): Boolean = !isLikelyContentImage(url)

    fun resolveHighRes(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        var url = resolveAndSanitize(rawUrl) ?: cleanUrl(rawUrl)
        if (!isLikelyContentImage(url)) return null

        // YouTube thumbnails: upgrade hqdefault -> maxresdefault
        if (url.contains("img.youtube.com/vi/") || url.contains("i.ytimg.com/vi/")) {
            return url.replace("/hqdefault.jpg", "/maxresdefault.jpg")
                .replace("/mqdefault.jpg", "/maxresdefault.jpg")
                .replace("/default.jpg", "/maxresdefault.jpg")
        }

        // Substack: upgrade image fetch width to 1456
        if (url.contains("substack-post-media") || url.contains("bucketeer-") || url.contains("image/fetch/")) {
            val substackWidthRegex = Regex("""w_\d+""")
            if (substackWidthRegex.containsMatchIn(url)) {
                url = substackWidthRegex.replace(url, "w_1456")
            }
        }

        // Medium: upgrade max width to 2000
        if (url.contains("miro.medium.com") || url.contains("cdn-images-1.medium.com")) {
            val mediumWidthRegex = Regex("""/max/\d+/""")
            if (mediumWidthRegex.containsMatchIn(url)) {
                url = mediumWidthRegex.replace(url, "/max/2000/")
            }
        }

        // WordPress / Jetpack / Photon thumbnail strip: e.g. -300x200.jpg -> .jpg (only if not an aspect crop query)
        val wpThumbnailRegex = Regex("""-\d{2,4}x\d{2,4}(\.[a-zA-Z]{3,4}(?:\?.*)?)$""", RegexOption.IGNORE_CASE)
        if (wpThumbnailRegex.containsMatchIn(url)) {
            url = wpThumbnailRegex.replace(url, "$1")
        }

        // Creative Boom CDN: upgrade medium/small to large webp
        if (url.contains("media.creativeboom.com")) {
            url = url.replace("-medium.webp", "-large.webp")
                .replace("-small.webp", "-large.webp")
        }

        return url
    }

    fun selectBestMediaCandidate(candidates: List<MediaCandidate>): String? {
        if (candidates.isEmpty()) return null

        val valid = candidates
            .map { it.copy(url = cleanUrl(it.url)) }
            .filter { isLikelyContentImage(it.url) }
        if (valid.isEmpty()) return null

        val sorted = valid.sortedWith(
            compareByDescending<MediaCandidate> { !it.isThumbnail }
                .thenByDescending { it.width }
                .thenByDescending { it.height }
                .thenByDescending { it.fileSize }
        )

        return resolveHighRes(sorted.first().url)
    }

    fun extractBestImageFromHtml(doc: Document): String? {
        val extracted = extractFromHtml(doc.html())
        if (!extracted.isNullOrBlank()) return extracted

        // Embedded video iframes / objects
        val iframes = doc.select("iframe[src], embed[src]")
        for (iframe in iframes) {
            val src = iframe.attr("src")
            val videoThumb = extractVideoThumbnail(src)
            if (!videoThumb.isNullOrBlank()) {
                return videoThumb
            }
        }

        // HTML5 video poster tags
        val videoPosters = doc.select("video[poster]")
        for (v in videoPosters) {
            val poster = cleanUrl(v.attr("poster"))
            if (poster.isNotBlank() && !poster.startsWith("data:") && isLikelyContentImage(poster)) {
                return resolveHighRes(poster)
            }
        }

        return null
    }

    fun extractVideoThumbnail(src: String?): String? {
        if (src.isNullOrBlank()) return null
        val match = ytEmbedRegex.find(src)
        if (match != null) {
            val videoId = match.groupValues[1]
            return "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        }
        return null
    }

    fun extractOpenGraphImage(doc: Document): String? {
        val og = extractMetaImage(doc.html())
        if (!og.isNullOrBlank()) return og
        return null
    }

    fun getEffectiveCoverImage(imageUrl: String?, fullContent: String?, excerpt: String?): String? {
        if (!imageUrl.isNullOrBlank() && imageUrl != "none") return imageUrl
        if (!fullContent.isNullOrBlank()) {
            val extracted = extractBestImageFromHtml(org.jsoup.Jsoup.parse(fullContent))
            if (!extracted.isNullOrBlank()) return extracted
        }
        if (!excerpt.isNullOrBlank()) {
            val extracted = extractBestImageFromHtml(org.jsoup.Jsoup.parse(excerpt))
            if (!extracted.isNullOrBlank()) return extracted
        }
        return null
    }

    fun extractBestImageUrlFromElement(el: Element): String? {
        val fromHtml = extractFromHtml(el.outerHtml())
        if (!fromHtml.isNullOrBlank()) return fromHtml

        val parentPicture = el.parent()?.takeIf { it.tagName().equals("picture", ignoreCase = true) }
        if (parentPicture != null) {
            for (source in parentPicture.select("source")) {
                val srcset = source.attr("data-srcset").ifBlank { source.attr("srcset") }
                val best = extractBestUrlFromSrcset(srcset)
                if (!best.isNullOrBlank() && isLikelyContentImage(best)) {
                    return resolveAndSanitize(best) ?: best
                }
            }
        }
        return null
    }

    fun extractHighestResFromSrcset(srcset: String): String? = extractBestUrlFromSrcset(srcset)
}
