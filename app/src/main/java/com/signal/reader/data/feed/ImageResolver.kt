package com.signal.reader.data.feed

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

data class MediaCandidate(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val fileSize: Long = 0L,
    val isThumbnail: Boolean = false
)

object ImageResolver {

    private val BANNED_PATH_SEGMENT_REGEX = Regex(
        """(?:^|[/_.-])(avatar|favicon|tracker|1x1|spacer|gravatar|feedburner|doubleclick|emoji|smiley)(?:[/_.-]|$)""",
        RegexOption.IGNORE_CASE
    )

    private val STRIP_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content"
    )

    private val LAZY_IMAGE_ATTRS = listOf(
        "data-promoted-src",
        "data-flickity-lazyload-src",
        "data-flickity-lazyload",
        "data-lazy-src",
        "data-src",
        "data-lazy",
        "data-original",
        "data-actualsrc",
        "data-hi-res-src",
        "data-full-url",
        "data-large-file",
        "data-orig-file"
    )

    private val LAZY_SRCSET_ATTRS = listOf(
        "data-flickity-lazyload-srcset",
        "data-lazy-srcset",
        "data-srcset",
        "srcset"
    )

    fun extractFromHtml(html: String?, baseUrl: String? = null): String? {
        return extractAllFromHtml(html, baseUrl).firstOrNull()
    }

    /**
     * Uses Jsoup DOM parsing (instead of brittle regexes) to extract all valid content image URLs
     * from any HTML snippet, unwrapping <noscript> and inspecting <picture><source> and lazy attrs.
     */
    fun extractAllFromHtml(html: String?, baseUrl: String? = null): List<String> {
        if (html.isNullOrBlank()) return emptyList()
        val results = mutableListOf<String>()

        try {
            val doc = Jsoup.parseBodyFragment(html, baseUrl ?: "")

            // Unwrap any <noscript> tags so lazy-loaded fallback <img> tags become visible
            doc.select("noscript").forEach { noscript ->
                val inner = noscript.html()
                if (inner.contains("<img", ignoreCase = true) || inner.contains("<picture", ignoreCase = true)) {
                    noscript.after(inner)
                }
            }

            // Inspect <picture>, <img>, and <source> in document order
            for (el in doc.select("img, source")) {
                val rawUrl = when (el.tagName().lowercase()) {
                    "source" -> {
                        val type = el.attr("type").lowercase()
                        if (type.isNotEmpty() && !type.startsWith("image/")) null
                        else extractBestFromElementSrcset(el) ?: el.attr("src").takeIf { it.isNotBlank() }
                    }
                    else -> extractBestUrlFromImgElement(el)
                }

                if (!rawUrl.isNullOrBlank()) {
                    val resolved = resolveAndSanitize(rawUrl, baseUrl)
                    if (resolved != null && isLikelyContentImage(resolved) && !results.contains(resolved)) {
                        results.add(resolved)
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore malformed snippet errors
        }

        return results
    }

    fun extractBestUrlFromImgElement(img: Element): String? {
        for (attr in LAZY_IMAGE_ATTRS) {
            val v = img.attr(attr).trim()
            if (isValidRawCandidate(v)) return v
        }
        val fromSrcset = extractBestFromElementSrcset(img)
        if (isValidRawCandidate(fromSrcset)) return fromSrcset

        val src = img.attr("src").trim()
        if (isValidRawCandidate(src)) return src

        return null
    }

    fun extractBestFromElementSrcset(el: Element): String? {
        for (attr in LAZY_SRCSET_ATTRS) {
            val v = el.attr(attr).trim()
            if (v.isNotEmpty()) {
                val best = extractBestUrlFromSrcset(v)
                if (isValidRawCandidate(best)) return best
            }
        }
        return null
    }

    private fun isValidRawCandidate(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.trim().lowercase()
        if (lower.startsWith("data:") || lower.startsWith("about:") || lower.startsWith("javascript:")) return false
        if (lower.endsWith("spacer.gif") || lower.endsWith("blank.gif") || lower.endsWith("1x1.gif") || lower.endsWith("1x1.png")) return false
        return true
    }

    /**
     * Uses Jsoup + JSON-LD fallback to reliably extract og:image / twitter:image from an article page.
     */
    fun extractMetaImage(html: String?, baseUrl: String? = null): String? {
        if (html.isNullOrBlank()) return null

        try {
            val doc = Jsoup.parse(html, baseUrl ?: "")
            val selectors = listOf(
                "meta[property=og:image:secure_url]",
                "meta[property=og:image:url]",
                "meta[property=og:image]",
                "meta[name=og:image]",
                "meta[name=twitter:image:src]",
                "meta[name=twitter:image]",
                "meta[property=twitter:image]",
                "link[rel=image_src]"
            )

            for (selector in selectors) {
                for (meta in doc.select(selector)) {
                    val content = meta.attr("content").ifBlank { meta.attr("href") }.trim()
                    if (content.isNotEmpty()) {
                        val resolved = resolveAndSanitize(content, baseUrl)
                        if (resolved != null && isLikelyContentImage(resolved)) {
                            return resolved
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Fallback: Check JSON-LD "image" / "thumbnailUrl" in <head>
        val jsonLdMatch = Regex(
            """"(?:thumbnailUrl|contentUrl)"\s*:\s*"(https?:[^"]+)"|"image"\s*:\s*(?:\[\s*)?"(https?:[^"]+)"""",
            RegexOption.IGNORE_CASE
        ).find(html)
        if (jsonLdMatch != null) {
            val raw = (jsonLdMatch.groupValues[1].ifBlank { jsonLdMatch.groupValues[2] }).replace("\\/", "/")
            val resolved = resolveAndSanitize(raw, baseUrl)
            if (resolved != null && isLikelyContentImage(resolved)) {
                return resolved
            }
        }

        return null
    }

    fun isLikelyContentImage(url: String, width: Int? = null, height: Int? = null): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase()
        if (lower.startsWith("data:") || lower.startsWith("about:") || lower.startsWith("javascript:")) return false
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
        if (pathOnly.contains("no-logo") || pathOnly.contains("nologo")) return true
        if (BANNED_PATH_SEGMENT_REGEX.containsMatchIn(pathOnly)) return false
        if (Regex("""(?:^|[/_.-])(site-?logo|brand-?logo|header-?logo|footer-?logo|logo)(?:[/_.-]|$)""").containsMatchIn(pathOnly)) return false

        return true
    }

    fun resolveAndSanitize(rawUrl: String?, baseUrl: String? = null): String? {
        if (rawUrl.isNullOrBlank()) return null
        var cleaned = decodeHtmlEntities(rawUrl.trim())
        if (!isValidRawCandidate(cleaned)) return null

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

    fun sanitizeImageUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        var result = decodeHtmlEntities(url.trim())
        if (!isValidRawCandidate(result)) return null

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

    fun extractFirstUrlFromSrcset(srcset: String): String? = extractBestUrlFromSrcset(srcset)

    fun extractBestUrlFromSrcset(srcset: String): String? {
        val decoded = decodeHtmlEntities(srcset).trim()
        if (decoded.isEmpty()) return null

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
            if (!isValidRawCandidate(url)) continue

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

        if (url.contains("img.youtube.com/vi/") || url.contains("i.ytimg.com/vi/")) {
            return url.replace("/hqdefault.jpg", "/maxresdefault.jpg")
                .replace("/mqdefault.jpg", "/maxresdefault.jpg")
                .replace("/default.jpg", "/maxresdefault.jpg")
        }

        if (url.contains("substack-post-media") || url.contains("bucketeer-") || url.contains("image/fetch/")) {
            val substackWidthRegex = Regex("""w_\d+""")
            if (substackWidthRegex.containsMatchIn(url)) {
                url = substackWidthRegex.replace(url, "w_1456")
            }
        }

        if (url.contains("miro.medium.com") || url.contains("cdn-images-1.medium.com")) {
            val mediumWidthRegex = Regex("""/max/\d+/""")
            if (mediumWidthRegex.containsMatchIn(url)) {
                url = mediumWidthRegex.replace(url, "/max/2000/")
            }
        }

        val wpThumbnailRegex = Regex("""-\d{2,4}x\d{2,4}(\.[a-zA-Z]{3,4}(?:\?.*)?)$""", RegexOption.IGNORE_CASE)
        if (wpThumbnailRegex.containsMatchIn(url)) {
            url = wpThumbnailRegex.replace(url, "$1")
        }

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

    fun extractBestImageFromHtml(doc: org.jsoup.nodes.Document): String? {
        val extracted = extractFromHtml(doc.html())
        if (!extracted.isNullOrBlank()) return extracted

        val iframes = doc.select("iframe[src], embed[src]")
        for (iframe in iframes) {
            val src = iframe.attr("src")
            val videoThumb = extractVideoThumbnail(src)
            if (!videoThumb.isNullOrBlank()) {
                return videoThumb
            }
        }

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
        val ytEmbedRegex = Regex("""(?:youtube(?:-nocookie)?\.com/(?:embed/|v/|watch\?v=)|youtu\.be/)([a-zA-Z0-9_-]{11})""", RegexOption.IGNORE_CASE)
        val match = ytEmbedRegex.find(src)
        if (match != null) {
            val videoId = match.groupValues[1]
            return "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        }
        return null
    }

    fun extractOpenGraphImage(doc: org.jsoup.nodes.Document): String? {
        val og = extractMetaImage(doc.html())
        if (!og.isNullOrBlank()) return og
        return null
    }

    fun getEffectiveCoverImage(imageUrl: String?, fullContent: String?, excerpt: String?): String? {
        if (!imageUrl.isNullOrBlank() && imageUrl != "none") return imageUrl
        if (!fullContent.isNullOrBlank()) {
            val extracted = extractBestImageFromHtml(Jsoup.parse(fullContent))
            if (!extracted.isNullOrBlank()) return extracted
        }
        if (!excerpt.isNullOrBlank()) {
            val extracted = extractBestImageFromHtml(Jsoup.parse(excerpt))
            if (!extracted.isNullOrBlank()) return extracted
        }
        return null
    }

    fun extractBestImageUrlFromElement(el: Element): String? {
        return extractBestUrlFromImgElement(el) ?: extractFromHtml(el.outerHtml())
    }

    fun extractHighestResFromSrcset(srcset: String): String? = extractBestUrlFromSrcset(srcset)
}
