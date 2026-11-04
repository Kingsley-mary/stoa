package com.signal.reader.data.feed

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

data class MediaCandidate(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val fileSize: Long = 0L,
    val isThumbnail: Boolean = false
)

object ImageResolver {

    private val trackingKeywords = listOf(
        "1x1", "pixel", "tracker", "beacon", "stat.gif", "analytics",
        "feedburner", "avatar", "badge", "emoji", "site-logo", "brand-logo",
        "header-logo", "footer-logo", "placeholder", "spacer", "blank.gif", "transparent.gif"
    )

    private val wpThumbnailRegex = Regex("""-\d{2,4}x\d{2,4}(\.[a-zA-Z]{3,4}(?:\?.*)?)$""", RegexOption.IGNORE_CASE)
    private val substackWidthRegex = Regex("""w_\d+""")
    private val mediumWidthRegex = Regex("""/max/\d+/""")

    /**
     * Unescapes HTML hex & named entities often present in meta tags and URLs.
     */
    fun cleanUrl(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw.trim().trim('"', '\'')
        return s.replace("&amp;", "&")
            .replace("&#x3A;", ":", ignoreCase = true)
            .replace("&#x2F;", "/", ignoreCase = true)
            .replace("&#x3F;", "?", ignoreCase = true)
            .replace("&#x3D;", "=", ignoreCase = true)
            .replace("&#x20;", " ", ignoreCase = true)
            .replace("&#x25;", "%", ignoreCase = true)
            .replace("&#38;", "&")
            .replace("&#58;", ":")
            .replace("&#47;", "/")
            .replace("&#63;", "?")
            .replace("&#61;", "=")
            .trim()
    }

    /**
     * Upgrades an image URL from low-res/thumbnail to its full-resolution counterpart.
     */
    fun resolveHighRes(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        var url = cleanUrl(rawUrl)

        // Ignore tracking pixels and data URIs
        if (url.startsWith("data:", ignoreCase = true) || isTrackingUrl(url)) {
            return null
        }

        // YouTube thumbnails: upgrade hqdefault -> maxresdefault
        if (url.contains("img.youtube.com/vi/") || url.contains("i.ytimg.com/vi/")) {
            return url.replace("/hqdefault.jpg", "/maxresdefault.jpg")
                .replace("/mqdefault.jpg", "/maxresdefault.jpg")
                .replace("/default.jpg", "/maxresdefault.jpg")
        }

        // Substack: upgrade image fetch width to 1456
        if (url.contains("substack-post-media") || url.contains("bucketeer-") || url.contains("image/fetch/")) {
            if (substackWidthRegex.containsMatchIn(url)) {
                url = substackWidthRegex.replace(url, "w_1456")
            }
        }

        // Medium: upgrade max width to 2000
        if (url.contains("miro.medium.com") || url.contains("cdn-images-1.medium.com")) {
            if (mediumWidthRegex.containsMatchIn(url)) {
                url = mediumWidthRegex.replace(url, "/max/2000/")
            }
        }

        // WordPress / Jetpack / Photon thumbnail strip: e.g. -300x200.jpg -> .jpg
        if (wpThumbnailRegex.containsMatchIn(url)) {
            url = wpThumbnailRegex.replace(url, "$1")
        }

        // WordPress Photon CDN: upgrade constrained width
        if (url.contains("i0.wp.com") || url.contains("i1.wp.com") || url.contains("i2.wp.com")) {
            url = url.replace(Regex("""([?&])w=\d+"""), "$1w=1600")
                .replace(Regex("""([?&])resize=\d+,\d+"""), "$1resize=1600,1067")
        }

        // Creative Boom CDN: upgrade medium/small to large webp
        if (url.contains("media.creativeboom.com")) {
            url = url.replace("-medium.webp", "-large.webp")
                .replace("-small.webp", "-large.webp")
        }

        return url
    }

    /**
     * Selects the highest resolution media item from RSS/Atom tags.
     */
    fun selectBestMediaCandidate(candidates: List<MediaCandidate>): String? {
        if (candidates.isEmpty()) return null

        val valid = candidates
            .map { it.copy(url = it.url.replace("&amp;", "&").trim('"', '\'')) }
            .filter { !isTrackingUrl(it.url) }
        if (valid.isEmpty()) return null

        // Priority: non-thumbnail candidates with largest width > height > filesize
        val sorted = valid.sortedWith(
            compareByDescending<MediaCandidate> { !it.isThumbnail }
                .thenByDescending { it.width }
                .thenByDescending { it.height }
                .thenByDescending { it.fileSize }
        )

        return resolveHighRes(sorted.first().url)
    }

    private val ytEmbedRegex = Regex("""(?:youtube(?:-nocookie)?\.com/(?:embed/|v/|watch\?v=)|youtu\.be/)([a-zA-Z0-9_-]{11})""", RegexOption.IGNORE_CASE)

    /**
     * Extracts the highest quality image from an HTML Document, inspecting picture/srcset,
     * img tags, embedded video posters (e.g. YouTube, HTML5), and gallery containers.
     */
    fun extractBestImageFromHtml(doc: Document): String? {
        // 1. Picture source tags
        val pictureSources = doc.select("picture source[srcset]")
        for (source in pictureSources) {
            val srcset = source.attr("srcset")
            val bestUrl = extractHighestResFromSrcset(srcset)
            if (!bestUrl.isNullOrBlank() && !isTrackingUrl(bestUrl)) {
                return resolveHighRes(bestUrl)
            }
        }

        // 2. Standard <img> elements
        val imgElements = doc.select("img")
        for (el in imgElements) {
            val candidate = extractBestImageUrlFromElement(el)
            if (candidate != null && !isTrackingUrl(candidate)) {
                return resolveHighRes(candidate)
            }
        }

        // 3. Embedded video iframes / objects (e.g. YouTube trailers/interviews)
        val iframes = doc.select("iframe[src], embed[src]")
        for (iframe in iframes) {
            val src = iframe.attr("src")
            val videoThumb = extractVideoThumbnail(src)
            if (!videoThumb.isNullOrBlank()) {
                return videoThumb
            }
        }

        // 4. HTML5 video poster tags
        val videoPosters = doc.select("video[poster]")
        for (v in videoPosters) {
            val poster = cleanUrl(v.attr("poster"))
            if (poster.isNotBlank() && !poster.startsWith("data:") && !isTrackingUrl(poster)) {
                return resolveHighRes(poster)
            }
        }

        // 5. Gallery containers with data-images or JSON (e.g. Hypebeast, WordPress galleries)
        val galleryElements = doc.select("[data-images]")
        for (gallery in galleryElements) {
            val dataImages = gallery.attr("data-images")
            val urlRegex = Regex("""https?:\\?/\\?/[^"'\s\\]+\.(?:jpg|jpeg|png|webp)(?:\?[^"'\s\\]*)?""", RegexOption.IGNORE_CASE)
            val match = urlRegex.find(dataImages)
            if (match != null) {
                val galleryUrl = cleanUrl(match.value.replace("\\/", "/"))
                if (!isTrackingUrl(galleryUrl)) {
                    return resolveHighRes(galleryUrl)
                }
            }
        }

        return null
    }

    /**
     * Extracts a high-res video thumbnail from video embeds like YouTube.
     */
    fun extractVideoThumbnail(src: String?): String? {
        if (src.isNullOrBlank()) return null
        val match = ytEmbedRegex.find(src)
        if (match != null) {
            val videoId = match.groupValues[1]
            return "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        }
        return null
    }

    /**
     * Extracts OpenGraph or Twitter Card image from HTML document.
     */
    fun extractOpenGraphImage(doc: Document): String? {
        val ogSelectors = listOf(
            "meta[property=og:image:secure_url]",
            "meta[property=og:image]",
            "meta[name=og:image]",
            "meta[name=twitter:image]",
            "meta[name=twitter:image:src]",
            "meta[property=twitter:image]"
        )

        for (selector in ogSelectors) {
            val raw = doc.select(selector).attr("content").trim()
            val content = cleanUrl(raw)
            if (content.isNotBlank() && !content.startsWith("data:") && !isTrackingUrl(content)) {
                return resolveHighRes(content)
            }
        }
        return null
    }

    /**
     * Resolves the effective cover image: returns imageUrl if present,
     * or falls back to extracting the first valid content image from fullContent or excerpt HTML.
     */
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

    /**
     * Extracts high-resolution URL from an <img> element, prioritizing srcset, data-orig-file, and data-src.
     */
    fun extractBestImageUrlFromElement(el: Element): String? {
        // Check srcset / data-srcset first
        val srcset = el.attr("srcset").ifBlank { el.attr("data-srcset") }
        if (srcset.isNotBlank()) {
            val bestFromSrcset = extractHighestResFromSrcset(srcset)
            if (!bestFromSrcset.isNullOrBlank()) {
                return bestFromSrcset
            }
        }

        // Check if inside a <picture> element with <source srcset>
        val parentPicture = el.parent()?.takeIf { it.tagName().equals("picture", ignoreCase = true) }
        if (parentPicture != null) {
            for (source in parentPicture.select("source[srcset]")) {
                val best = extractHighestResFromSrcset(source.attr("srcset"))
                if (!best.isNullOrBlank() && !isTrackingUrl(best)) {
                    return best
                }
            }
        }

        // Check high-res data attributes common in modern CMSs (WordPress, Vox/Polygon, Hypebeast, Substack)
        val candidateAttrs = listOf(
            "data-orig-file",
            "data-large-file",
            "data-full-url",
            "data-src-large",
            "data-img-url",
            "data-flickity-lazyload-src",
            "data-src",
            "data-lazy-src",
            "abs:src",
            "src"
        )

        for (attr in candidateAttrs) {
            val value = cleanUrl(el.attr(attr))
            if (value.isNotBlank() && !value.startsWith("data:") && !isTrackingUrl(value)) {
                return value
            }
        }

        return null
    }

    /**
     * Parses an HTML srcset string (e.g. "url1 400w, url2 1200w, url3 2048w") and picks the URL with largest width.
     */
    fun extractHighestResFromSrcset(srcset: String): String? {
        try {
            val entries = srcset.split(",")
            var maxW = 0
            var bestUrl: String? = null

            for (entry in entries) {
                val parts = entry.trim().split(Regex("""\s+"""))
                if (parts.isNotEmpty()) {
                    val candidateUrl = cleanUrl(parts[0])
                    if (isTrackingUrl(candidateUrl)) continue

                    val descriptor = if (parts.size > 1) parts[1].lowercase(Locale.ROOT) else ""
                    val width = descriptor.removeSuffix("w").toIntOrNull() ?: 0

                    if (width > maxW) {
                        maxW = width
                        bestUrl = candidateUrl
                    } else if (bestUrl == null) {
                        bestUrl = candidateUrl
                    }
                }
            }
            return bestUrl
        } catch (_: Exception) {
            return null
        }
    }

    fun isTrackingUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        // Explicitly allow clean artwork that indicates absence of watermark logos (e.g. Polygon key art ending in -no-logo.jpg)
        if (lower.contains("no-logo") || lower.contains("nologo")) return false
        if (lower.endsWith(".svg") || lower.contains(".svg?") || lower.contains("/svg/")) return true
        if (trackingKeywords.any { lower.contains(it) }) return true
        // Standalone logos or icons (e.g. /logo.png, _logo.jpg, /icon.png, favicon) without false-positive matching on words like iconic or monologue
        if (Regex("""(?:^|[/_-])(?:site-?logo|brand-?logo|favicon|apple-touch-icon)(?:[/._-]|\.[a-z]{3,4})""").containsMatchIn(lower)) return true
        if (Regex("""(?:^|[/_])logo\.(?:png|jpe?g|gif|webp)""").containsMatchIn(lower)) return true
        if (Regex("""(?:^|[/_])icon\.(?:png|jpe?g|gif|webp)""").containsMatchIn(lower)) return true
        return false
    }
}
