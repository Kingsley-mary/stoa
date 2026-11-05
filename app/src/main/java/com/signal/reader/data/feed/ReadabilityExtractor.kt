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

    // Checked sequentially. If a selector matches multiple sibling blocks (like Polygon's
    // .duet--article--article-body-component), ALL matching blocks are combined!
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
        "combx|comment|community|disqus|extra|foot|menu|remark|rss|shoutbox|sidebar|sponsor|ad-break|agegate|pagination|pager|popup|tweet|twitter|nav|navigation|social|share|related|newsletter|subscription|cookie|banner|modal|widget|outbrain|taboola|promo|byline|author-bio|breadcrumbs|tags",
        RegexOption.IGNORE_CASE
    )

    private val OK_MAYBE_ITS_A_CANDIDATE = Regex(
        "and|article|body|column|main|shadow|content|post|entry|story|text|gallery|w-img|body-img|header|heading|hero|media|figure|carousel|slider",
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
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Upgrade-Insecure-Requests", "1")
                .build()

            val primaryUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Safari/537.36"
            val fallbackUa = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36"

            val html = client.newCall(buildReq(primaryUa)).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            } ?: client.newCall(buildReq(fallbackUa)).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            } ?: return@withContext null

            val doc = Jsoup.parse(html, url)

            // 1. Extract lead cover image (og:image / twitter:image) first
            val leadImage = ImageResolver.extractMetaImage(html, url)

            // 2. Unwrap <noscript> tags containing images BEFORE removing <noscript>,
            // because Polygon & Hypebeast put static fallback <img> tags inside <noscript>!
            doc.select("noscript").forEach { noscript ->
                val inner = noscript.html()
                if (inner.contains("<img", ignoreCase = true) || inner.contains("<picture", ignoreCase = true)) {
                    noscript.after(inner)
                }
                noscript.remove()
            }

            // 3. Promote all <picture><source> tags across the entire document
            promotePictureElements(doc, url)

            // 4. Capture dedicated top-level galleries & hero figures (Hypebeast lookbooks, Polygon lead figures, Valnet images)
            val galleryImageUrls = mutableListOf<String>()
            doc.select(
                ".post-body-gallery, .post-gallery, .post-body-image, .post-body-carousel, " +
                ".hypb-gallery, .gallery-carousel, .gallery-slider, .swiper-wrapper, .swiper-slide, " +
                ".flickity-slider, .carousel-cell, .glide__slides, .wp-block-gallery, " +
                "[data-gallery], .c-entry-hero, .heading_image, .featured-image, " +
                ".duet--media--content-image, article header figure, article > figure, .w-img"
            ).forEach { galleryEl ->
                extractImagesFromElement(galleryEl, url).forEach { imgUrl ->
                    if (!galleryImageUrls.contains(imgUrl)) {
                        galleryImageUrls.add(imgUrl)
                    }
                }
            }

            // Also inspect JSON-LD for multi-image arrays ("image": [...]) on Hypebeast/WordPress
            doc.select("script[type=application/ld+json]").forEach { script ->
                val json = script.data()
                val arrayMatches = Regex(""""image"\s*:\s*\[([^\]]+)\]""", RegexOption.IGNORE_CASE).findAll(json)
                for (m in arrayMatches) {
                    Regex(""""(https?:[^"]+)"""").findAll(m.groupValues[1]).forEach { urlMatch ->
                        val raw = urlMatch.groupValues[1].replace("\\/", "/")
                        val clean = ImageResolver.resolveAndSanitize(raw, url)
                        if (clean != null && ImageResolver.isLikelyContentImage(clean) && !galleryImageUrls.contains(clean)) {
                            galleryImageUrls.add(clean)
                        }
                    }
                }
            }

            // If we still have no gallery/lead image in galleryImageUrls, include leadImage (og:image)
            if (galleryImageUrls.isEmpty() && !leadImage.isNullOrBlank()) {
                galleryImageUrls.add(leadImage)
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

            // 5. Remove non-content clutter (never remove <header> or <figure> inside <article>!)
            doc.select(
                "script, style, noscript, svg, form, button, input, select, textarea, " +
                "nav, footer, aside, .sidebar, .comments, #comments, .related-posts, " +
                ".related, .newsletter, .share-buttons, .social-share, .author-box, " +
                ".ad, .ads, .advertisement, .w-author, .article-tags"
            ).remove()

            // 6. Find article body container(s). If a selector matches multiple blocks (e.g. Polygon's
            // .duet--article--article-body-component), combine ALL of them so no paragraphs or images are dropped!
            var bestElement: Element? = null
            for (selector in ARTICLE_BODY_SELECTORS) {
                val matches = doc.select(selector)
                if (matches.isEmpty()) continue
                val combinedTextLen = matches.sumOf { it.text().trim().length }
                if (combinedTextLen >= 120) {
                    bestElement = if (matches.size == 1) {
                        matches.first()
                    } else {
                        val wrapper = doc.createElement("div")
                        matches.forEach { wrapper.appendChild(it.clone()) }
                        wrapper
                    }
                    break
                }
            }
            if (bestElement == null) {
                bestElement = findBestCandidateByScoring(doc)
            }
            if (bestElement == null) return@withContext null

            // 7. Normalize all <img> tags inside bestElement into clean <img src="https://...">
            val inBodyImageUrls = mutableSetOf<String>()
            bestElement.select("img").forEach { img ->
                val resolved = resolveImgElementUrl(img, url)
                if (resolved == null || !ImageResolver.isLikelyContentImage(resolved)) {
                    img.remove()
                } else {
                    img.clearAttributes()
                    img.attr("src", resolved)
                    inBodyImageUrls.add(resolved)
                }
            }

            // 8. Unwrap <picture> tags so ReaderScreen sees standalone <img> tags
            bestElement.select("picture").forEach { pic ->
                val img = pic.selectFirst("img")
                if (img != null) {
                    pic.replaceWith(img)
                } else {
                    pic.remove()
                }
            }

            // 9. Convert YouTube/Vimeo/video iframes into clickable links
            bestElement.select("iframe").forEach { iframe ->
                val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }
                if (src.contains("youtube.com") || src.contains("youtu.be") || src.contains("vimeo.com")) {
                    val cleanSrc = if (src.startsWith("//")) "https:$src" else src
                    iframe.after("<p><a href=\"$cleanSrc\">Watch embedded video</a></p>")
                }
                iframe.remove()
            }

            // 10. Prepend any gallery/lead images not already in bestElement
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

    private fun promotePictureElements(root: Element, baseUrl: String) {
        root.select("picture").forEach { picture ->
            val bestSourceUrl = picture.select("source")
                .mapNotNull { source ->
                    val type = source.attr("type").lowercase()
                    if (type.isNotEmpty() && !type.startsWith("image/")) return@mapNotNull null
                    ImageResolver.extractBestFromElementSrcset(source)
                        ?: source.attr("data-src").takeIf { it.isNotBlank() }
                        ?: source.attr("src").takeIf { it.isNotBlank() }
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
        val rawSrc = ImageResolver.extractBestUrlFromImgElement(img) ?: return null
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
        element.select("[data-flickity-lazyload], [data-src], [data-lazy], [data-bg]").forEach { el ->
            if (el.tagName().lowercase() != "img") {
                val raw = el.attr("data-flickity-lazyload")
                    .ifBlank { el.attr("data-src") }
                    .ifBlank { el.attr("data-lazy") }
                    .ifBlank { el.attr("data-bg") }
                val resolved = ImageResolver.resolveAndSanitize(raw, baseUrl)
                if (resolved != null && ImageResolver.isLikelyContentImage(resolved) && !urls.contains(resolved)) {
                    urls.add(resolved)
                }
            }
        }
        return urls
    }

    private fun findBestCandidateByScoring(doc: Document): Element? {
        val allElements = doc.getAllElements().toList()
        for (node in allElements) {
            val tag = node.tagName().lowercase()
            if (tag == "body" || tag == "html" || tag == "article" || tag == "main") continue
            val matchString = "${node.className()} ${node.id()}"
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
        val matchString = "${element.className()} ${element.id()}"
        if (NEGATIVE_WEIGHT.containsMatchIn(matchString)) score -= 25.0
        if (POSITIVE_WEIGHT.containsMatchIn(matchString)) score += 25.0
        return score
    }
}
