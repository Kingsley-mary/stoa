package com.signal.reader.data.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

object ReadabilityExtractor {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun extractArticleHtml(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val html = response.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html, url)

            // 1. Extract embedded video players defined inside scripts before stripping scripts (e.g. Polygon / AdsNinja / Valnet)
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
                            append("<div class=\"in-content-video-embed\"><video controls style=\"width:100%; height:auto; border-radius:8px;\"");
                            if (!posterUrl.isNullOrBlank()) append(" poster=\"$posterUrl\"")
                            if (!videoTitle.isNullOrBlank()) append(" title=\"$videoTitle\"")
                            append("><source src=\"$mp4Url\" type=\"video/mp4\"></video></div>")
                        }
                        scriptTag.before(videoTag)
                    }
                }
            }

            // 2. Expand JavaScript galleries (e.g. Hypebeast, WordPress Flickity galleries) into native figure/img tags
            doc.select("[data-images]").forEach { galleryEl ->
                val dataImages = galleryEl.attr("data-images")
                if (dataImages.isNotBlank()) {
                    val urlRegex = Regex("""https?:\\?/\\?/[^"'\s\\]+\.(?:jpg|jpeg|png|webp)(?:\?[^"'\s\\]*)?""", RegexOption.IGNORE_CASE)
                    val matches = urlRegex.findAll(dataImages)
                        .map { it.value.replace("\\/", "/").replace("&amp;", "&") }
                        .filter { ImageResolver.isLikelyContentImage(it) }
                        .distinct()
                        .toList()
                    for (imgUrl in matches.take(30)) {
                        galleryEl.append("<figure><img src=\"$imgUrl\" alt=\"Gallery Image\" /></figure>")
                    }
                }
            }

            // 3. Capture dedicated top-level article galleries (Hypebeast lookbooks, sliders, carousels)
            // BEFORE narrowing down to the single text container
            val galleryImageUrls = mutableListOf<String>()
            doc.select(
                ".post-body-gallery, .post-gallery, .post-body-image, .gallery-carousel, " +
                ".gallery-slider, .swiper-wrapper, .wp-block-gallery, [data-gallery], figure.gallery-item"
            ).forEach { galleryEl ->
                // Promote any <picture><source> inside the gallery first
                galleryEl.select("picture").forEach { picture ->
                    val bestSourceUrl = picture.select("source")
                        .mapNotNull { source ->
                            val srcset = source.attr("data-srcset").ifBlank { source.attr("srcset") }
                            ImageResolver.extractBestUrlFromSrcset(srcset)
                        }
                        .firstOrNull()
                    val img = picture.selectFirst("img")
                    if (bestSourceUrl != null) {
                        val resolved = ImageResolver.resolveAndSanitize(bestSourceUrl, url) ?: bestSourceUrl
                        if (img != null) {
                            val existingSrc = img.attr("src").trim()
                            if (existingSrc.isEmpty() || existingSrc.startsWith("data:")) {
                                img.attr("src", resolved)
                            }
                        } else {
                            picture.appendElement("img").attr("src", resolved)
                        }
                    }
                }

                galleryEl.select("img").forEach { img ->
                    val rawSrc = img.attr("data-lazy-src")
                        .ifBlank { img.attr("data-src") }
                        .ifBlank { img.attr("data-lazy") }
                        .ifBlank { img.attr("data-original") }
                        .ifBlank {
                            val srcset = img.attr("data-srcset").ifBlank { img.attr("srcset") }
                            ImageResolver.extractBestUrlFromSrcset(srcset) ?: ""
                        }
                        .ifBlank { img.attr("src") }

                    val resolved = ImageResolver.resolveAndSanitize(rawSrc, url)
                    if (resolved != null && ImageResolver.isLikelyContentImage(resolved)) {
                        if (!galleryImageUrls.contains(resolved)) {
                            galleryImageUrls.add(resolved)
                        }
                    }
                }
            }

            // 4. Capture lead hero cover image before stripping header/clutter
            val leadOgImage = ImageResolver.extractOpenGraphImage(doc)
            val leadFeatureEl = doc.selectFirst("figure[data-is-feature-img='true'], .article-header figure, header figure, [data-is-feature-img], .c-entry-hero, [data-hero-image], .lead-img, figure.wp-block-image")
            val leadFeatureImg = leadFeatureEl?.selectFirst("img")?.let { ImageResolver.extractBestImageUrlFromElement(it) }
            val bgHeaderMatch = Regex("""background-image:\s*url\(['"]?([^'"\)]+)['"]?\)""", RegexOption.IGNORE_CASE)
                .find(doc.select("header[style*=background-image], div[style*=background-image]").attr("style"))
            val bgHeaderUrl = bgHeaderMatch?.groupValues?.get(1)?.let { ImageResolver.cleanUrl(it) }?.takeIf { ImageResolver.isLikelyContentImage(it) }
            val effectiveLeadCover = leadFeatureImg ?: leadOgImage ?: bgHeaderUrl

            // 5. Strip clutter elements
            doc.select("script, style, noscript, header, footer, nav, aside, .advertisement, .social-share, .cookie-banner, .newsletter-signup").remove()

            // 6. Find best content candidate
            val candidateSelectors = listOf(
                "article",
                ".duet--article--article-body-component",
                "[itemprop='articleBody']",
                ".article__body",
                ".article-body",
                ".entry-content",
                ".post-content",
                ".story-body",
                ".article-content",
                ".body-content",
                "main"
            )

            var bestElement = candidateSelectors
                .mapNotNull { doc.selectFirst(it) }
                .firstOrNull { it.select("p").size >= 2 }

            if (bestElement == null) {
                bestElement = doc.body().select("div, section")
                    .maxByOrNull { it.select("p").sumOf { p -> p.text().length } }
            }

            if (bestElement != null) {
                // Promote all <picture> elements so <source srcset> URL is copied into <img src="...">
                bestElement.select("picture").forEach { picture ->
                    val bestSourceUrl = picture.select("source")
                        .mapNotNull { source ->
                            val type = source.attr("type").lowercase()
                            if (type.isNotEmpty() && !type.startsWith("image/")) return@mapNotNull null
                            val srcset = source.attr("data-srcset").ifBlank { source.attr("srcset") }
                            ImageResolver.extractBestUrlFromSrcset(srcset)
                        }
                        .firstOrNull()

                    val img = picture.selectFirst("img")
                    if (bestSourceUrl != null) {
                        val resolved = ImageResolver.resolveAndSanitize(bestSourceUrl, url) ?: bestSourceUrl
                        if (img != null) {
                            val existingSrc = img.attr("src").trim()
                            if (existingSrc.isEmpty() || existingSrc.startsWith("data:")) {
                                img.attr("src", resolved)
                            }
                        } else {
                            picture.appendElement("img").attr("src", resolved)
                        }
                    }
                }

                // Clean and sanitize <img> tags
                bestElement.select("img").forEach { img ->
                    val width = img.attr("width").toIntOrNull()
                    val height = img.attr("height").toIntOrNull()

                    val rawSrc = img.attr("data-lazy-src")
                        .ifBlank { img.attr("data-src") }
                        .ifBlank { img.attr("data-original") }
                        .ifBlank { img.attr("data-actualsrc") }
                        .ifBlank { img.attr("data-hi-res-src") }
                        .ifBlank {
                            val srcset = img.attr("data-srcset").ifBlank { img.attr("srcset") }
                            ImageResolver.extractBestUrlFromSrcset(srcset) ?: ""
                        }
                        .ifBlank { img.attr("src") }

                    val resolved = ImageResolver.resolveAndSanitize(rawSrc, url)
                    if (resolved == null || !ImageResolver.isLikelyContentImage(resolved, width, height)) {
                        img.remove()
                    } else {
                        img.attr("src", resolved)
                        img.removeAttr("srcset")
                        img.removeAttr("data-src")
                        img.removeAttr("data-srcset")
                        img.removeAttr("data-lazy-src")
                        img.removeAttr("loading")
                    }
                }

                // For video embeds (iframe), before removing iframe tags, convert YouTube/Vimeo iframes into a link block
                bestElement.select("iframe").forEach { iframe ->
                    val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }
                    if (src.contains("youtube.com") || src.contains("youtu.be") || src.contains("vimeo.com")) {
                        val cleanSrc = if (src.startsWith("//")) "https:$src" else src
                        iframe.after("<p><a href=\"$cleanSrc\">Watch embedded video</a></p>")
                    }
                    iframe.remove()
                }

                val inBodyUrls = bestElement.select("img")
                    .mapNotNull { it.attr("src").takeIf { s -> s.isNotBlank() } }
                    .toSet()

                val galleryHtmlPrefix = buildString {
                    for (gUrl in galleryImageUrls) {
                        if (gUrl !in inBodyUrls) {
                            append("<p><img src=\"$gUrl\" /></p>\n")
                        }
                    }
                }

                // If a lead cover image was discovered outside bestElement, prepend it as a lead figure
                val finalLeadImage = effectiveLeadCover
                    ?: galleryImageUrls.firstOrNull()
                    ?: inBodyUrls.firstOrNull()

                if (!finalLeadImage.isNullOrBlank()) {
                    val cleanLead = ImageResolver.resolveHighRes(finalLeadImage) ?: finalLeadImage
                    val leadBase = cleanLead.substringBefore("?").trimEnd('/')
                    if (inBodyUrls.none { it.contains(leadBase) } && galleryImageUrls.none { it.contains(leadBase) }) {
                        bestElement.prepend("<figure class=\"lead-hero-figure\"><img src=\"$cleanLead\" alt=\"Lead Article Cover\" /></figure>")
                    }
                }

                val finalHtml = (galleryHtmlPrefix + bestElement.html()).trim()
                return@withContext finalHtml.takeIf { it.isNotBlank() }
            }

            null
        } catch (_: Exception) {
            null
        }
    }
}
