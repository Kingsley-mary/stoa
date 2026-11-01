package com.signal.reader.data.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

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
            val maxBytes = 5 * 1024 * 1024 // 5 MB cap
            val body = responseBody.byteStream().use { input ->
                val buffer = ByteArray(maxBytes)
                var total = 0
                var read = 0
                while (total < maxBytes && input.read(buffer, total, maxBytes - total).also { read = it } != -1) {
                    total += read
                }
                String(buffer, 0, total, Charsets.UTF_8)
            }
            val contentType = response.header("Content-Type")
            Pair(body, contentType)
        }
    }

    suspend fun fetchAndParse(feedUrl: String): ParsedFeed = withContext(Dispatchers.IO) {
        val (content, _) = fetch(feedUrl)
        FeedParser.parse(content, feedUrl)
    }

    companion object {
        const val USER_AGENT = "StoaReader/1.4 (Android; RSS Reader; +https://github.com/stoa/reader)"

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
