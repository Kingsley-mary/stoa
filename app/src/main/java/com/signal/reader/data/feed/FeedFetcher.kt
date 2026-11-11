package com.signal.reader.data.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class ConditionalFetchResult {
    object NotModified : ConditionalFetchResult()
    data class Modified(
        val parsedFeed: ParsedFeed,
        val etag: String?,
        val lastModified: String?
    ) : ConditionalFetchResult()
}

class FeedFetcher(
    private val client: OkHttpClient = createDefaultClient()
) {
    suspend fun fetch(url: String): Pair<String, String?> = withContext(Dispatchers.IO) {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            throw IllegalArgumentException("Only HTTP and HTTPS URLs are accepted: $url")
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.9, */*;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP response code ${response.code} for $url")
            }
            val responseBody = response.body ?: throw IOException("Empty response body for $url")
            val body = readCappedBodyString(responseBody)
            val contentType = response.header("Content-Type")
            Pair(body, contentType)
        }
    }

    suspend fun fetchAndParse(feedUrl: String): ParsedFeed = withContext(Dispatchers.IO) {
        val (content, _) = fetch(feedUrl)
        FeedParser.parse(content, feedUrl)
    }

    suspend fun fetchAndParseConditional(
        feedUrl: String,
        etag: String? = null,
        lastModified: String? = null
    ): ConditionalFetchResult = withContext(Dispatchers.IO) {
        if (!feedUrl.startsWith("http://", ignoreCase = true) && !feedUrl.startsWith("https://", ignoreCase = true)) {
            throw IllegalArgumentException("Only HTTP and HTTPS URLs are accepted: $feedUrl")
        }

        val requestBuilder = Request.Builder()
            .url(feedUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.9, */*;q=0.8")

        if (!etag.isNullOrBlank()) {
            requestBuilder.header("If-None-Match", etag)
        }
        if (!lastModified.isNullOrBlank()) {
            requestBuilder.header("If-Modified-Since", lastModified)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 304) {
                return@use ConditionalFetchResult.NotModified
            }
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP response code ${response.code} for $feedUrl")
            }
            val responseBody = response.body ?: throw IOException("Empty response body for $feedUrl")
            val body = readCappedBodyString(responseBody)
            val parsed = FeedParser.parse(body, feedUrl)
            val newEtag = response.header("ETag")
            val newLastModified = response.header("Last-Modified")
            ConditionalFetchResult.Modified(
                parsedFeed = parsed,
                etag = newEtag,
                lastModified = newLastModified
            )
        }
    }

    private fun readCappedBodyString(responseBody: okhttp3.ResponseBody): String {
        val maxBytes = 5 * 1024 * 1024 // 5 MB cap without upfront 5 MB allocation
        return responseBody.byteStream().use { input ->
            val out = ByteArrayOutputStream(32 * 1024)
            val chunk = ByteArray(8192)
            var total = 0
            while (total < maxBytes) {
                val toRead = minOf(chunk.size, maxBytes - total)
                val read = input.read(chunk, 0, toRead)
                if (read == -1) break
                out.write(chunk, 0, read)
                total += read
            }
            out.toString(Charsets.UTF_8.name())
        }
    }

    companion object {
        const val USER_AGENT = "StoaReader/1.5 (Android; RSS Reader; +https://github.com/Kingsley-mary/stoa)"

        fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor { chain ->
                    val request = chain.request()
                    var response = chain.proceed(request)
                    var redirectCount = 0
                    while (response.isRedirect && redirectCount < 5) {
                        val location = response.header("Location") ?: break
                        response.close()
                        redirectCount++
                        val nextUrl = if (location.startsWith("/")) {
                            val original = request.url
                            "${original.scheme}://${original.host}$location"
                        } else location
                        val newRequest = request.newBuilder().url(nextUrl).build()
                        response = chain.proceed(newRequest)
                    }
                    response
                }
                .build()
        }
    }
}
