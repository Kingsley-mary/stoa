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
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.9, */*;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP response code ${response.code} for $url")
            }
            val body = response.body?.string() ?: throw IOException("Empty response body for $url")
            val contentType = response.header("Content-Type")
            Pair(body, contentType)
        }
    }

    suspend fun fetchAndParse(feedUrl: String): ParsedFeed = withContext(Dispatchers.IO) {
        val (content, _) = fetch(feedUrl)
        FeedParser.parse(content, feedUrl)
    }

    companion object {
        const val USER_AGENT = "SignalReader/1.2 (Android; RSS Reader; +https://github.com/signal/reader)"

        fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }
}
