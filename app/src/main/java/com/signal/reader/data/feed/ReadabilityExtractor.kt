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
                        .filter { !ImageResolver.isTrackingUrl(it) }
                        .distinct()
                        .toList()
                    for (imgUrl in matches.take(30)) {
                        galleryEl.append("<figure><img src=\"$imgUrl\" alt=\"Gallery Image\" /></figure>")
                    }
                }
            }

            // 3. Promote lazy loaded image attributes (data-src, data-img-url, data-orig-file, srcset) to primary src
            doc.select("img").forEach { img ->
                val bestUrl = ImageResolver.extractBestImageUrlFromElement(img)
                if (!bestUrl.isNullOrBlank()) {
                    img.attr("src", bestUrl)
                }
            }

            // 4. Strip non-content iframes, keeping YouTube, Vimeo, Twitch, and direct video embeds
            doc.select("iframe").forEach { iframe ->
                val src = iframe.attr("src").lowercase()
                val isVideo = src.contains("youtube") || src.contains("youtu.be") ||
                              src.contains("vimeo") || src.contains("player.vimeo") ||
                              src.contains("twitch") || src.contains("embed")
                if (!isVideo) {
                    iframe.remove()
                }
            }

            // Strip clutter elements
            doc.select("script, style, noscript, header, footer, nav, aside, .ad, .advertisement, .social-share, .cookie-banner, .newsletter-signup").remove()

            // Find best content candidate
            val candidateSelectors = listOf(
                "article",
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

            var bestContainer = candidateSelectors
                .mapNotNull { doc.selectFirst(it) }
                .firstOrNull { it.select("p").size >= 2 }

            if (bestContainer == null) {
                // Heuristic: container with the most paragraph text
                bestContainer = doc.body().select("div, section")
                    .maxByOrNull { it.select("p").sumOf { p -> p.text().length } }
            }

            bestContainer?.html()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
