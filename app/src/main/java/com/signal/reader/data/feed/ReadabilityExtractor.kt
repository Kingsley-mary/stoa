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

            // Strip non-content elements
            doc.select("script, style, noscript, header, footer, nav, aside, iframe, .ad, .advertisement, .social-share, .cookie-banner, .newsletter-signup").remove()

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
