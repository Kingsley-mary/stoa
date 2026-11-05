package com.signal.reader.data.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

object ReadabilityExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Ordered from most specific article body container to least specific.
    // MUST be queried sequentially so an outer <article> tag doesn't shadow an inner .article-body!
    private val ARTICLE_BODY_SELECTORS = listOf(
        "#article-body",
        ".article-body",
        ".duet--article--article-body-component",
        ".post-body-content",
        ".Hypebeast-post-content",
        "article .entry-content",
        "article .post-content",
        "article .c-entry-content",
        ".entry-content",
        ".post-content",
        ".story-body",
        "[itemprop=articleBody]",
        "article"
    )

    private val UNLIKELY_CANDIDATES = Regex(
        "combx|comment|community|disqus|extra|foot|header|menu|remark|rss|shoutbox|sidebar|sponsor|ad-break|agegate|pagination|pager|popup|tweet|twitter|nav|navigation|social|share|related|newsletter|subscription|cookie|banner|modal|widget|outbrain|taboola|promo|byline|author-bio|breadcrumbs|tags",
        RegexOption.IGNORE_CASE
    )

    private val OK_MAYBE_ITS_A_CANDIDATE = Regex(
        "and|article|body|column|main|shadow|content|post|entry|story|text|gallery|w-img|body-img",
        RegexOption.IGNORE_CASE
    )

    private val POSITIVE_WEIGHT = Regex(
        "article|body|content|entry|hentry|h-entry|main|page|pagination|post|text|blog|story|rich-text|prose|content-block-regular",
        RegexOption.IGNORE_CASE
    )

    private val NEGATIVE_WEIGHT = Regex(
        "hidden|^hid$| hid$| hid |^hid |banner|combx|comment|com-|contact|foot|footer|footnote|masthead|meta|outbrain|promo|related|scroll|share|shoutbox|sidebar|skyscraper|sponsor|shopping|tags|tool|widget|author|byline",
        RegexOption.IGNORE_CASE
    )

    suspend fun extract(url: String): String? = withContext(Dispatchers.IO) {
        try {
            fun buildReq(ua: String) = Request.Builder()
                .url(url)
                .header("User-Agent", ua)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val primaryUa = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36"
            val fallbackUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Safari/537.36"

            val html = client.newCall(buildReq(primaryUa)).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else null
            } ?: client.newCall(buildReq(fallbackUa)).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else null
            } ?: return@withContext null

            val doc = Jsoup.parse(html, url)

            // 1. Promote ALL <picture><source> tags in the entire document first so no image URLs are lost
            promotePictureElements(doc, url)

            // 2. Capture dedicated top-level galleries (Hypebeast lookbooks/sliders, WordPress galleries, Valnet hero images)
            val galleryImageUrls = mutableListOf<String>()
            doc.select(
                ".post-body-gallery, .post-gallery, .post-body-image, .gallery-carousel, " +
                ".gallery-slider, .swiper-wrapper, .swiper-slide, .wp-block-gallery, " +
                "[data-gallery], .c-entry-hero, .heading_image, .w-img"
            ).forEach { galleryEl ->
                extractImagesFromElement(galleryEl, url).forEach { imgUrl ->
                    if (!galleryImageUrls.contains(imgUrl)) {
                        galleryImageUrls.add(imgUrl)
                    }
                }
            }

            // Also extract JSON-LD image arrays if this is a gallery/article page (e.g. Hypebeast lookbooks)
            if (galleryImageUrls.isEmpty()) {
                doc.select("script[type=application/ld+json]").forEach { script ->
                    val json = script.data()
                    Regex(""""(?:contentUrl|url)"\s*:\s*"(https?:[^"]+\.(?:jpg|jpeg|png|webp)[^"]*)"|"image"\s*:\s*\[\s*((?:"https?:[^"]+"\s*,?\s*)+)\]""", RegexOption.IGNORE_CASE)
                        .findAll(json)
                        .forEach { match ->
                            val single = match.groupValues[1]
                            val arrayBlock = match.groupValues[2]
                            if (single.isNotBlank()) {
                                val clean = ImageResolver.resolveAndSanitize(single.replace("\\/", "/"), url)
                                if (clean != null && ImageResolver.isLikelyContentImage(clean) && !galleryImageUrls.contains(clean)) {
                                    galleryImageUrls.add(clean)
                                }
                            }
                            if (arrayBlock.isNotBlank()) {
                                Regex(""""(https?:[^"]+)"""").findAll(arrayBlock).forEach { urlMatch ->
                                    val clean = ImageResolver.resolveAndSanitize(urlMatch.groupValues[1].replace("\\/", "/"), url)
                                    if (clean != null && ImageResolver.isLikelyContentImage(clean) && !galleryImageUrls.contains(clean)) {
                                        galleryImageUrls.add(clean)
                                    }
                                }
                            }
                        }
                }
            }

            // Extract embedded video players defined inside scripts before stripping scripts (e.g. Polygon / AdsNinja / Valnet)
            doc.select("script").forEach { scriptTag ->
                val scriptContent = scriptTag.html()
                if (scriptContent.contains("<video") && scriptContent.contains("</video>")) {
                    val videoMatch = Regex("""<video[^>]*>.*?</video>""", RegexOption.DOT_MATCHES_ALL).find(scriptContent)
                    if (videoMatch != null) {
                        val videoHtml = videoMatch.value
                            .replace("\\\"", "\"")
                            .replace("\\/", "/")
                        scriptTag.before("<div class=\"in-content-video-embed\">$videoHtml</div>")
                    }
                } else if (scriptContent.contains("playlist") && scriptContent.contains(".mp4")) {
                    val urlMatch = Regex(""""url"\s*:\s*"([^"]+\.mp4)"""").find(scriptContent)
                    val posterMatch = Regex(""""thumbnailLink"\s*:\s*"([^"]+)"""").find(scriptContent)
                    val titleMatch = Regex(""""title"\s*:\s*"([^"]+)"""").find(scriptContent)
                    if (urlMatch != null) {
                        val mp4Url = urlMatch.groupValues[1].replace("\\/", "/").replace("&amp;", "&")
                        val posterUrl = posterMatch?.groupValues?.get(1)?.replace("\\/", "/")?.replace("&amp;", "&")
                        val videoTitle = titleMatch?.groupValues?.get(1)?.replace("\\/", "/")
                        val videoTag = buildString {
                            append("<div class=\"in-content-video-embed\"><video controls style=\"width:100%; height:auto; border-radius:8px;\"")
                            if (!posterUrl.isNullOrBlank()) append(" poster=\"$posterUrl\"")
                            if (!videoTitle.isNullOrBlank()) append(" title=\"$videoTitle\"")
                            append("><source src=\"$mp4Url\" type=\"video/mp4\"></video></div>")
                        }
                        scriptTag.before(videoTag)
                    }
                }
            }

            // 3. Remove non-content clutter (keep .w-img, .body-img, figure, picture!)
            doc.select(
                "script, style, noscript, svg, form, button, input, select, textarea, " +
                "nav, footer, header, aside, .sidebar, .comments, #comments, .related-posts, " +
                ".related, .newsletter, .share-buttons, .social-share, .author-box, " +
                ".ad, .ads, .advertisement, .w-author, .article-tags"
            ).remove()

            // 4. Find the article body container by checking selectors in priority order
            var bestElement: Element? = null
            for (selector in ARTICLE_BODY_SELECTORS) {
                val candidate = doc.selectFirst(selector)
                if (candidate != null && candidate.text().trim().length >= 120) {
                    bestElement = candidate
                    break
                }
            }
            if (bestElement == null) {
                bestElement = findBestCandidateByScoring(doc)
            }
            if (bestElement == null) return@withContext null

            // 5. Normalize all <img> tags inside bestElement
            val inBodyImageUrls = mutableSetOf<String>()
            bestElement.select("img").forEach { img ->
                val resolved = resolveImgElementUrl(img, url)
                if (resolved == null || !ImageResolver.isLikelyContentImage(resolved)) {
                    img.remove()
                } else {
                    img.attr("src", resolved)
                    img.removeAttr("srcset")
                    img.removeAttr("data-src")
                    img.removeAttr("data-srcset")
                    img.removeAttr("data-lazy-src")
                    img.removeAttr("data-original")
                    img.removeAttr("loading")
                    inBodyImageUrls.add(resolved)
                }
            }

            // 6. Unwrap <picture> and <figure> wrappers into clean <p><img src="..."/></p> so ReaderScreen's
            // simple HTML tokenizer always sees standalone <img> tags
            bestElement.select("picture").forEach { pic ->
                val img = pic.selectFirst("img")
                if (img != null) {
                    pic.replaceWith(img)
                } else {
                    pic.remove()
                }
            }

            // 7. Convert YouTube/Vimeo iframes into clickable links
            bestElement.select("iframe").forEach { iframe ->
                val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }
                if (src.contains("youtube.com") || src.contains("youtu.be") || src.contains("vimeo.com")) {
                    val cleanSrc = if (src.startsWith("//")) "https:$src" else src
                    iframe.after("<p><a href=\"$cleanSrc\">Watch embedded video</a></p>")
                }
                iframe.remove()
            }

            // 8. Prepend any gallery/hero images not already inside bestElement
            val galleryPrefix = buildString {
                for (gUrl in galleryImageUrls) {
                    if (gUrl !in inBodyImageUrls) {
                        append("<p><img src=\"$gUrl\" /></p>\n")
                    }
                }
            }

            val cleanedHtml = (galleryPrefix + bestElement.html()).trim()
            cleanedHtml.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun extractArticleHtml(url: String): String? = extract(url)

    /**
     * Copies the best URL from <picture><source srcset="..."> onto the child <img> tag's
     * data-promoted-src attribute so placeholder <img src="data:..." width="1" height="1">
     * never shadows the real high-res image!
     */
    private fun promotePictureElements(root: Element, baseUrl: String) {
        root.select("picture").forEach { picture ->
            val bestSourceUrl = picture.select("source")
                .mapNotNull { source ->
                    val type = source.attr("type").lowercase()
                    if (type.isNotEmpty() && !type.startsWith("image/")) return@mapNotNull null
                    val srcset = source.attr("data-srcset")
                        .ifBlank { source.attr("srcset") }
                        .ifBlank { source.attr("data-src") }
                    ImageResolver.extractBestUrlFromSrcset(srcset) ?: srcset.takeIf { it.isNotBlank() }
                }
                .firstOrNull()

            if (bestSourceUrl != null) {
                val resolved = ImageResolver.resolveAndSanitize(bestSourceUrl, baseUrl) ?: bestSourceUrl
                val img = picture.selectFirst("img")
                if (img != null) {
                    img.attr("data-promoted-src", resolved)
                    img.attr("src", resolved)
                } else {
                    picture.appendElement("img")
                        .attr("data-promoted-src", resolved)
                        .attr("src", resolved)
                }
            }
        }
    }

    private fun resolveImgElementUrl(img: Element, baseUrl: String): String? {
        val rawSrc = img.attr("data-promoted-src")
            .ifBlank { img.attr("data-lazy-src") }
            .ifBlank { img.attr("data-src") }
            .ifBlank { img.attr("data-lazy") }
            .ifBlank { img.attr("data-original") }
            .ifBlank { img.attr("data-actualsrc") }
            .ifBlank { img.attr("data-hi-res-src") }
            .ifBlank {
                val srcset = img.attr("data-srcset").ifBlank { img.attr("srcset") }
                ImageResolver.extractBestUrlFromSrcset(srcset) ?: ""
            }
            .ifBlank { img.attr("src") }

        return ImageResolver.resolveAndSanitize(rawSrc, baseUrl)
    }

    private fun extractImagesFromElement(element: Element, baseUrl: String): List<String> {
        val urls = mutableListOf<String>()
        promotePictureElements(element, baseUrl)
        element.select("img").forEach { img ->
            val resolved = resolveImgElementUrl(img, baseUrl)
            if (resolved != null && ImageResolver.isLikelyContentImage(resolved) && !urls.contains(resolved)) {
                urls.add(resolved)
            }
        }
        return urls
    }

    private fun findBestCandidateByScoring(doc: Document): Element? {
        val allElements = doc.getAllElements().toList()
        for (node in allElements) {
            val tag = node.tagName().lowercase()
            if (tag == "body" || tag == "html" || tag == "article" || tag == "main") continue
            val matchString = "${node.className()}${node.id()}"
            if (UNLIKELY_CANDIDATES.containsMatchIn(matchString) &&
                !OK_MAYBE_ITS_A_CANDIDATE.containsMatchIn(matchString)
            ) {
                node.remove()
            }
        }

        val scores = mutableMapOf<Element, Double>()
        val paragraphs = doc.select("p, pre, td, figure, .w-img, .body-img")

        for (p in paragraphs) {
            val text = p.text().trim()
            val hasImg = p.selectFirst("img") != null
            if (text.length < 25 && !hasImg) continue

            val parent = p.parent() ?: continue
            val grandParent = parent.parent()

            if (!scores.containsKey(parent)) {
                scores[parent] = initializeScore(parent)
            }
            if (grandParent != null && !scores.containsKey(grandParent)) {
                scores[grandParent] = initializeScore(grandParent)
            }

            var contentScore = 1.0
            contentScore += text.split(',').size
            contentScore += minOf((text.length / 100).toDouble(), 3.0)
            if (hasImg) contentScore += 4.0

            scores[parent] = (scores[parent] ?: 0.0) + contentScore
            if (grandParent != null) {
                scores[grandParent] = (scores[grandParent] ?: 0.0) + (contentScore / 2.0)
            }
        }

        return scores.maxByOrNull { it.value }?.key ?: doc.body()
    }

    private fun initializeScore(element: Element): Double {
        var score = when (element.tagName().lowercase()) {
            "article" -> 10.0
            "section", "div" -> 5.0
            "pre", "td", "blockquote" -> 3.0
            "address", "ol", "ul", "dl", "dd", "dt", "li", "form" -> -3.0
            "h1", "h2", "h3", "h4", "h5", "h6", "th" -> -5.0
            else -> 0.0
        }
        val matchString = "${element.className()}${element.id()}"
        if (NEGATIVE_WEIGHT.containsMatchIn(matchString)) score -= 25.0
        if (POSITIVE_WEIGHT.containsMatchIn(matchString)) score += 25.0
        return score
    }
}
