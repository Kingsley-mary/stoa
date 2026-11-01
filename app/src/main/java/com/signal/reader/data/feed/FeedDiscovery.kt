package com.signal.reader.data.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.net.URI

sealed class DiscoveryResult {
    data class Found(
        val name: String,
        val siteUrl: String,
        val feedUrl: String,
        val description: String,
        val parsedFeed: ParsedFeed
    ) : DiscoveryResult()

    data class NotFound(val normalizedUrl: String, val reason: String) : DiscoveryResult()
    data class Error(val message: String) : DiscoveryResult()
}

class FeedDiscovery(
    private val fetcher: FeedFetcher = FeedFetcher()
) {

    suspend fun discover(input: String): DiscoveryResult = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return@withContext DiscoveryResult.NotFound(input, "Please enter a valid website address.")
        }

        // Step 1: Normalise the input (add https:// if missing)
        val normalizedUrl = normalizeUrl(trimmed)
        val uri = try {
            URI.create(normalizedUrl)
        } catch (_: Exception) {
            return@withContext DiscoveryResult.NotFound(normalizedUrl, "Invalid URL format.")
        }

        val baseHost = uri.host ?: return@withContext DiscoveryResult.NotFound(normalizedUrl, "Could not determine website host.")

        // Step 2: Test if the URL returns XML that parses directly as RSS or Atom
        try {
            val (body, _) = fetcher.fetch(normalizedUrl)
            try {
                val parsed = FeedParser.parse(body, normalizedUrl)
                val displayName = parsed.title.ifBlank { baseHost }
                return@withContext DiscoveryResult.Found(
                    name = displayName,
                    siteUrl = parsed.siteUrl.ifBlank { normalizedUrl },
                    feedUrl = normalizedUrl,
                    description = parsed.description,
                    parsedFeed = parsed
                )
            } catch (_: Exception) {
                // Not direct XML, body is likely HTML page; parse <link rel="alternate"> tags
                val htmlResult = findFeedInHtml(body, normalizedUrl, baseHost)
                if (htmlResult != null) return@withContext htmlResult
            }
        } catch (_: Exception) {
            // Failed to fetch normalized URL directly, fall through to try common paths
        }

        // Step 3: If not found on article page, try the site's homepage
        val origin = "${uri.scheme ?: "https"}://${baseHost}"
        if (normalizedUrl.trimEnd('/') != origin.trimEnd('/')) {
            try {
                val (homeBody, _) = fetcher.fetch(origin)
                val homeHtmlResult = findFeedInHtml(homeBody, origin, baseHost)
                if (homeHtmlResult != null) return@withContext homeHtmlResult
            } catch (_: Exception) {
                // Ignore homepage fetch error and continue to common paths
            }
        }

        // Step 4: If none found in HTML, try common paths: /feed, /rss, /feed.xml, /atom.xml, /rss.xml
        val commonPaths = listOf("/feed", "/rss", "/feed.xml", "/atom.xml", "/rss.xml", "/feeds.xml")

        for (path in commonPaths) {
            val candidateUrl = "$origin$path"
            try {
                val (candidateBody, _) = fetcher.fetch(candidateUrl)
                val parsed = FeedParser.parse(candidateBody, candidateUrl)
                val displayName = parsed.title.ifBlank { baseHost }
                return@withContext DiscoveryResult.Found(
                    name = displayName,
                    siteUrl = parsed.siteUrl.ifBlank { origin },
                    feedUrl = candidateUrl,
                    description = parsed.description,
                    parsedFeed = parsed
                )
            } catch (_: Exception) {
                // Keep trying next path
            }
        }

        // Step 5: "Couldn't find a feed on host" state
        DiscoveryResult.NotFound(
            normalizedUrl = normalizedUrl,
            reason = "Couldn’t find a feed on $baseHost. Try the site’s homepage address, or paste the feed link itself if you have it."
        )
    }

    private suspend fun findFeedInHtml(html: String, baseUrl: String, baseHost: String): DiscoveryResult.Found? {
        val doc = Jsoup.parse(html, baseUrl)
        val feedLinks = doc.select("link[rel~=(?i)alternate][type~=(?i)(application/(rss|atom)\\+xml|text/xml)]")

        for (linkElement in feedLinks) {
            val href = linkElement.attr("abs:href").ifBlank { linkElement.attr("href") }
            if (href.isNotBlank()) {
                val resolvedHref = resolveUrl(baseUrl, href)
                try {
                    val (feedBody, _) = fetcher.fetch(resolvedHref)
                    val parsed = FeedParser.parse(feedBody, resolvedHref)
                    val displayName = parsed.title.ifBlank {
                        doc.title().ifBlank { baseHost }
                    }
                    return DiscoveryResult.Found(
                        name = displayName,
                        siteUrl = parsed.siteUrl.ifBlank { baseUrl },
                        feedUrl = resolvedHref,
                        description = parsed.description.ifBlank { doc.select("meta[name=description]").attr("content") },
                        parsedFeed = parsed
                    )
                } catch (_: Exception) {
                    // Try next link tag if any
                }
            }
        }
        return null
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    private fun resolveUrl(base: String, target: String): String {
        return try {
            val baseHttp = base.toHttpUrlOrNull()
            baseHttp?.resolve(target)?.toString() ?: target
        } catch (_: Exception) {
            target
        }
    }
}
