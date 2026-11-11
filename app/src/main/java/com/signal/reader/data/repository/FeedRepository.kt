package com.signal.reader.data.repository

import com.signal.reader.data.feed.ConditionalFetchResult
import com.signal.reader.data.feed.DiscoveryResult
import com.signal.reader.data.feed.FeedDiscovery
import com.signal.reader.data.feed.FeedFetcher
import com.signal.reader.data.feed.ImageResolver
import com.signal.reader.data.feed.ParsedArticle
import com.signal.reader.data.feed.ReadabilityExtractor
import com.signal.reader.data.feed.StarterSource
import com.signal.reader.data.feed.StarterSources
import com.signal.reader.data.local.SignalDatabase
import com.signal.reader.data.local.dao.SourceWithUnreadCount
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.data.local.entity.HighlightEntity
import com.signal.reader.data.local.entity.SourceEntity
import okhttp3.Request
import org.jsoup.Jsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

class FeedRepository(
    private val database: SignalDatabase,
    private val fetcher: FeedFetcher = FeedFetcher(),
    val discovery: FeedDiscovery = FeedDiscovery(fetcher)
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val crawlScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var crawlJob: Job? = null
    private val attemptedUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val sourceDao = database.sourceDao()
    private val articleDao = database.articleDao()
    private val highlightDao = database.highlightDao()

    fun getAllHighlightsFlow(): Flow<List<HighlightEntity>> = highlightDao.getAllHighlightsFlow()

    fun getHighlightsForArticleFlow(articleId: Long): Flow<List<HighlightEntity>> =
        highlightDao.getHighlightsForArticleFlow(articleId)

    suspend fun addHighlight(
        articleId: Long,
        articleTitle: String,
        sourceName: String,
        quoteText: String,
        note: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val cleanQuote = quoteText.trim()
        if (cleanQuote.isBlank()) return@withContext -1L
        highlightDao.insert(
            HighlightEntity(
                articleId = articleId,
                articleTitle = articleTitle,
                sourceName = sourceName,
                quoteText = cleanQuote,
                note = note
            )
        )
    }

    suspend fun deleteHighlight(id: Long) = withContext(Dispatchers.IO) {
        highlightDao.deleteById(id)
    }

    fun getFollowedSourcesFlow(): Flow<List<SourceEntity>> = sourceDao.getFollowedSourcesFlow()

    fun getAllSourcesFlow(): Flow<List<SourceEntity>> = sourceDao.getAllSourcesFlow()

    fun getHomeStreamFlow(): Flow<List<ArticleEntity>> = articleDao.getHomeStreamFlow()

    fun getArticlesBySourceFlow(sourceId: Long): Flow<List<ArticleEntity>> =
        articleDao.getArticlesBySourceFlow(sourceId)

    fun getSavedArticlesFlow(): Flow<List<ArticleEntity>> = articleDao.getSavedArticlesFlow()

    fun getArticleByIdFlow(id: Long): Flow<ArticleEntity?> = articleDao.getArticleByIdFlow(id)

    fun getSourceByIdFlow(id: Long): Flow<SourceEntity?> = sourceDao.getSourceByIdFlow(id)

    fun getUnreadCountsFlow(): Flow<List<SourceWithUnreadCount>> = articleDao.getUnreadCountsFlow()

    suspend fun getSourceById(id: Long): SourceEntity? = withContext(Dispatchers.IO) {
        sourceDao.getSourceById(id)
    }

    suspend fun findArticleByLinkOrGuid(link: String, altLink: String = link): ArticleEntity? = withContext(Dispatchers.IO) {
        articleDao.findArticleByLinkOrGuid(link, altLink)
    }

    suspend fun addSource(
        name: String,
        siteUrl: String,
        feedUrl: String,
        description: String = ""
    ): Long = withContext(Dispatchers.IO) {
        val cleanFeed = feedUrl.trim()
        val altFeed = if (cleanFeed.endsWith("/")) cleanFeed.trimEnd('/') else "$cleanFeed/"
        val existing = sourceDao.getSourceByFeedUrlWithAlt(cleanFeed, altFeed)
            ?: sourceDao.getSourceByName(name.trim())

        if (existing != null) {
            if (!existing.followed) {
                sourceDao.setFollowed(existing.id, true)
            }
            // Fetch newest items asynchronously in the background
            repoScope.launch { refreshSource(existing.id) }
            return@withContext existing.id
        }

        val maxOrder = sourceDao.getAllSources().maxOfOrNull { it.displayOrder } ?: 0
        val entity = SourceEntity(
            name = name.trim(),
            siteUrl = siteUrl.trim(),
            feedUrl = cleanFeed,
            description = description.trim(),
            followed = true,
            displayOrder = maxOrder + 1
        )
        val newId = sourceDao.insert(entity)
        repoScope.launch { refreshSource(newId) }
        newId
    }

    suspend fun addSourcesBatch(
        sources: List<StarterSource>
    ): List<Long> = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext emptyList()
        var currentOrder = sourceDao.getAllSources().maxOfOrNull { it.displayOrder } ?: 0
        val idsToRefresh = mutableListOf<Long>()

        for (item in sources) {
            val cleanFeed = item.feedUrl.trim()
            val altFeed = if (cleanFeed.endsWith("/")) cleanFeed.trimEnd('/') else "$cleanFeed/"
            val existing = sourceDao.getSourceByFeedUrlWithAlt(cleanFeed, altFeed)
                ?: sourceDao.getSourceByName(item.name.trim())

            if (existing != null) {
                if (!existing.followed) {
                    sourceDao.setFollowed(existing.id, true)
                }
                idsToRefresh.add(existing.id)
            } else {
                currentOrder++
                val entity = SourceEntity(
                    name = item.name.trim(),
                    siteUrl = item.siteUrl.trim(),
                    feedUrl = cleanFeed,
                    description = item.description.trim(),
                    followed = true,
                    displayOrder = currentOrder
                )
                val newId = sourceDao.insert(entity)
                idsToRefresh.add(newId)
            }
        }

        // Fetch in background with bounded concurrency (Semaphore(6))
        repoScope.launch {
            val batchSemaphore = Semaphore(6)
            idsToRefresh.forEach { id ->
                launch {
                    batchSemaphore.withPermit {
                        refreshSource(id)
                    }
                }
            }
        }

        idsToRefresh
    }

    suspend fun addCatalogSourcesBatch(
        sources: List<com.signal.reader.data.catalog.CatalogSource>
    ): List<Long> = withContext(Dispatchers.IO) {
        addSourcesBatch(
            sources.map {
                StarterSource(
                    group = it.topics.firstOrNull() ?: "General",
                    name = it.name,
                    description = it.description,
                    siteUrl = it.siteUrl,
                    feedUrl = it.feedUrl
                )
            }
        )
    }

    suspend fun unfollowSource(sourceId: Long) = withContext(Dispatchers.IO) {
        // Keep saved articles, remove unsaved articles and update followed status
        articleDao.deleteUnsavedBySourceId(sourceId)
        sourceDao.setFollowed(sourceId, false)
    }

    suspend fun setFollowed(sourceId: Long, followed: Boolean) = withContext(Dispatchers.IO) {
        sourceDao.setFollowed(sourceId, followed)
        if (!followed) {
            articleDao.deleteUnsavedBySourceId(sourceId)
        }
    }

    suspend fun setMuted(sourceId: Long, muted: Boolean) = withContext(Dispatchers.IO) {
        sourceDao.setMuted(sourceId, muted)
    }

    suspend fun setPinned(sourceId: Long, pinned: Boolean) = withContext(Dispatchers.IO) {
        sourceDao.setPinned(sourceId, pinned)
    }

    suspend fun togglePinned(sourceId: Long): Boolean = withContext(Dispatchers.IO) {
        val s = sourceDao.getSourceById(sourceId) ?: return@withContext false
        val next = !s.isPinned
        sourceDao.setPinned(sourceId, next)
        next
    }

    suspend fun repairSourceFeed(sourceId: Long): Boolean = withContext(Dispatchers.IO) {
        val source = sourceDao.getSourceById(sourceId) ?: return@withContext false
        try {
            val disc = discovery.discover(source.siteUrl.ifBlank { source.feedUrl })
            if (disc is DiscoveryResult.Found) {
                sourceDao.updateFeedUrl(sourceId, disc.feedUrl)
                val newArticles = saveArticles(source.copy(feedUrl = disc.feedUrl), disc.parsedFeed.items)
                sourceDao.recordSuccess(sourceId, System.currentTimeMillis())
                crawlMissingImages()
                return@withContext true
            }
            val refreshed = refreshSource(sourceId)
            val updated = sourceDao.getSourceById(sourceId)
            updated?.isBroken == false || refreshed > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun notifyWidgetUpdate() {
        try {
            com.signal.reader.widget.StoaWidgetProvider.updateAllWidgets(com.signal.reader.SignalApplication.instance)
        } catch (_: Exception) {}
    }

    suspend fun markArticleRead(articleId: Long) = withContext(Dispatchers.IO) {
        articleDao.setRead(articleId, true)
        notifyWidgetUpdate()
    }

    suspend fun toggleRead(articleId: Long): Boolean = withContext(Dispatchers.IO) {
        val article = articleDao.getArticleById(articleId) ?: return@withContext false
        val newRead = !article.read
        articleDao.setRead(articleId, newRead)
        notifyWidgetUpdate()
        newRead
    }

    suspend fun toggleSaved(articleId: Long) = withContext(Dispatchers.IO) {
        val article = articleDao.getArticleById(articleId) ?: return@withContext
        val newSaved = !article.saved
        articleDao.setSaved(articleId, newSaved, if (newSaved) System.currentTimeMillis() else null)
    }

    suspend fun saveOrToggleWebUrl(url: String, title: String): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return@withContext false
        val existing = articleDao.findArticleByLinkOrGuid(cleanUrl, cleanUrl.trimEnd('/'))
        if (existing != null) {
            val nextSaved = !existing.saved
            articleDao.setSaved(existing.id, nextSaved, if (nextSaved) System.currentTimeMillis() else null)
            return@withContext nextSaved
        }
        val host = try {
            java.net.URI(cleanUrl).host?.removePrefix("www.") ?: "Web"
        } catch (_: Exception) {
            "Web"
        }
        val entity = ArticleEntity(
            sourceId = 0L,
            sourceName = host.replaceFirstChar { it.uppercase() },
            guid = cleanUrl,
            title = title.ifBlank { cleanUrl },
            excerpt = "",
            url = cleanUrl,
            publishedTime = System.currentTimeMillis(),
            read = true,
            saved = true,
            savedTime = System.currentTimeMillis()
        )
        articleDao.insertIgnore(entity)
        true
    }

    suspend fun markAllAsRead(sourceId: Long? = null) = withContext(Dispatchers.IO) {
        if (sourceId != null) {
            articleDao.markAllAsReadForSources(listOf(sourceId))
        } else {
            val followed = sourceDao.getFollowedSources().map { it.id }
            articleDao.markAllAsReadForSources(followed)
        }
        notifyWidgetUpdate()
    }

    suspend fun markAllAsReadWithUndo(sourceId: Long? = null): List<Long> = withContext(Dispatchers.IO) {
        val unreadIds = if (sourceId != null) {
            articleDao.getUnreadArticleIdsForSources(listOf(sourceId))
        } else {
            val followed = sourceDao.getFollowedSources().map { it.id }
            if (followed.isEmpty()) emptyList() else articleDao.getUnreadArticleIdsForSources(followed)
        }
        if (unreadIds.isNotEmpty()) {
            if (sourceId != null) {
                articleDao.markAllAsReadForSources(listOf(sourceId))
            } else {
                val followed = sourceDao.getFollowedSources().map { it.id }
                articleDao.markAllAsReadForSources(followed)
            }
            notifyWidgetUpdate()
        }
        unreadIds
    }

    suspend fun undoMarkAllAsRead(articleIds: List<Long>) = withContext(Dispatchers.IO) {
        if (articleIds.isEmpty()) return@withContext
        articleDao.markArticlesUnread(articleIds)
        notifyWidgetUpdate()
    }

    suspend fun refreshSource(sourceId: Long): Int = withContext(Dispatchers.IO) {
        val source = sourceDao.getSourceById(sourceId) ?: return@withContext 0
        try {
            articleDao.resetFailedArticleImages()
            var newEtag: String? = null
            var newLastModified: String? = null
            val existingArticleCount = articleDao.getArticleCountForSource(sourceId)

            val parsedFeed = try {
                if (existingArticleCount > 0 && (!source.etag.isNullOrBlank() || !source.lastModified.isNullOrBlank())) {
                    when (val cond = fetcher.fetchAndParseConditional(source.feedUrl, source.etag, source.lastModified)) {
                        is ConditionalFetchResult.NotModified -> {
                            sourceDao.recordSuccess(sourceId, System.currentTimeMillis())
                            return@withContext 0
                        }
                        is ConditionalFetchResult.Modified -> {
                            newEtag = cond.etag
                            newLastModified = cond.lastModified
                            cond.parsedFeed
                        }
                    }
                } else {
                    when (val cond = fetcher.fetchAndParseConditional(source.feedUrl)) {
                        is ConditionalFetchResult.NotModified -> return@withContext 0
                        is ConditionalFetchResult.Modified -> {
                            newEtag = cond.etag
                            newLastModified = cond.lastModified
                            cond.parsedFeed
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback 1: try flipping protocol http <-> https
                val altProtocolUrl = if (source.feedUrl.startsWith("http://", ignoreCase = true)) {
                    source.feedUrl.replaceFirst("http://", "https://", ignoreCase = true)
                } else if (source.feedUrl.startsWith("https://", ignoreCase = true)) {
                    source.feedUrl.replaceFirst("https://", "http://", ignoreCase = true)
                } else null

                val altResult = if (altProtocolUrl != null) {
                    try {
                        val parsed = fetcher.fetchAndParse(altProtocolUrl)
                        sourceDao.updateFeedUrl(sourceId, altProtocolUrl)
                        parsed
                    } catch (_: Exception) { null }
                } else null

                altResult ?: run {
                    // Fallback 2: rediscover feed from site URL
                    val discovery = FeedDiscovery(fetcher)
                    when (val disc = discovery.discover(source.siteUrl)) {
                        is DiscoveryResult.Found -> {
                            sourceDao.updateFeedUrl(sourceId, disc.feedUrl)
                            disc.parsedFeed
                        }
                        else -> throw e
                    }
                }
            }

            val newArticles = saveArticles(source, parsedFeed.items)
            sourceDao.recordSuccessWithHeaders(sourceId, System.currentTimeMillis(), newEtag, newLastModified)
            if (newArticles.isNotEmpty()) {
                if (source.notificationsEnabled) {
                    try {
                        val notifManager = com.signal.reader.SignalApplication.instance.notificationManager
                        notifManager.postNewArticlesNotification(source, newArticles)
                    } catch (_: Exception) {
                    }
                }
            }
            crawlMissingImages()
            newArticles.size
        } catch (e: Exception) {
            sourceDao.recordFailure(sourceId, e.message ?: "Network or parsing error")
            0
        }
    }

    suspend fun refreshAllFollowedSources(): Int = withContext(Dispatchers.IO) {
        articleDao.resetFailedArticleImages()
        val sources = sourceDao.getFollowedSources()
        var totalNew = 0
        val refreshSemaphore = Semaphore(6)
        coroutineScope {
            val tasks = sources.map { source ->
                async {
                    refreshSemaphore.withPermit {
                        refreshSource(source.id)
                    }
                }
            }
            totalNew = tasks.awaitAll().sum()
        }
        notifyWidgetUpdate()
        crawlMissingImages()
        totalNew
    }

    suspend fun resetFailedArticleImages() = withContext(Dispatchers.IO) {
        articleDao.resetFailedArticleImages()
    }

    suspend fun deduplicateSourcesAndArticles() = withContext(Dispatchers.IO) {
        val allSources = sourceDao.getAllSources()
        // 1. Deduplicate sources by normalized feed URL
        val urlGrouped = allSources.groupBy { it.feedUrl.trim().trimEnd('/').lowercase(Locale.ROOT) }
        for ((_, sources) in urlGrouped) {
            if (sources.size > 1) {
                val primary = sources.first()
                val duplicates = sources.drop(1)
                for (dup in duplicates) {
                    articleDao.reassignArticlesSource(dup.id, primary.id)
                    sourceDao.deleteById(dup.id)
                }
            }
        }

        // 2. Deduplicate sources by name
        val remaining = sourceDao.getAllSources()
        val nameGrouped = remaining.groupBy { it.name.trim().lowercase(Locale.ROOT) }
        for ((_, sources) in nameGrouped) {
            if (sources.size > 1) {
                val primary = sources.first()
                val duplicates = sources.drop(1)
                for (dup in duplicates) {
                    articleDao.reassignArticlesSource(dup.id, primary.id)
                    sourceDao.deleteById(dup.id)
                }
            }
        }

        // 3. Deduplicate articles under the same source
        articleDao.deduplicateArticlesByGuid()
        articleDao.deduplicateArticlesByUrl()
        articleDao.deduplicateArticlesByTitle()
    }

    suspend fun updateArticleFullContent(articleId: Long, fullContent: String, isExcerptOnly: Boolean = false) = withContext(Dispatchers.IO) {
        articleDao.updateFullContent(articleId, fullContent, isExcerptOnly)
    }

    suspend fun updateArticleImage(articleId: Long, imageUrl: String) = withContext(Dispatchers.IO) {
        articleDao.updateImageUrl(articleId, imageUrl)
    }

    private val httpClient = FeedFetcher.createDefaultClient()

    fun crawlMissingImages() {
        // Do NOT cancel an already-active crawlJob; let it finish or launch a new one if idle
        if (crawlJob?.isActive == true) return
        crawlJob = crawlScope.launch {
            try {
                val articlesWithoutImages = articleDao.getArticlesWithoutImages()
                val toCrawl = articlesWithoutImages
                    .filter { it.link.isNotBlank() && !attemptedUrls.contains(it.link) }
                    .take(60)

                if (toCrawl.isEmpty()) return@launch

                val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                toCrawl.map { article ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            attemptedUrls.add(article.link)
                            val ogImage = runCatching { fetchOgImage(article.link) }.getOrNull()
                            if (!ogImage.isNullOrBlank()) {
                                articleDao.updateImageUrl(article.id, ogImage)
                            }
                        }
                    }
                }.awaitAll()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun resolveMissingArticleImages(limit: Int = 60) = withContext(Dispatchers.IO) {
        crawlMissingImages()
    }

    private fun fetchOgImage(articleUrl: String): String? {
        val request = Request.Builder()
            .url(articleUrl)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Safari/537.36"
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Upgrade-Insecure-Requests", "1")
            .build()

        var response = runCatching { httpClient.newCall(request).execute() }.getOrNull()
        if (response != null && (response.code == 403 || response.code == 405)) {
            response.close()
            val fallbackReq = request.newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36"
                )
                .build()
            response = runCatching { httpClient.newCall(fallbackReq).execute() }.getOrNull()
        }

        return response?.use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body ?: return@use null
            val source = body.source()
            val maxBytes = 256 * 1024L
            val buffer = okio.Buffer()
            var totalRead = 0L
            while (totalRead < maxBytes && !source.exhausted()) {
                val read = source.read(buffer, minOf(8192L, maxBytes - totalRead))
                if (read == -1L) break
                totalRead += read
            }
            val html = buffer.readString(Charsets.UTF_8)
            ImageResolver.extractMetaImage(html, articleUrl)
        }
    }

    suspend fun setSourceNotificationsEnabled(sourceId: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        sourceDao.setNotificationsEnabled(sourceId, enabled)
    }

    private suspend fun saveArticles(source: SourceEntity, items: List<ParsedArticle>): List<ArticleEntity> {
        val newArticles = mutableListOf<ArticleEntity>()
        for (item in items) {
            val existing = articleDao.getArticleByGuid(item.guid, source.id)
            if (existing == null) {
                val entity = ArticleEntity(
                    sourceId = source.id,
                    sourceName = source.name,
                    guid = item.guid,
                    title = item.title,
                    excerpt = item.excerpt,
                    fullContent = item.fullContent,
                    isExcerptOnly = item.isExcerptOnly,
                    imageUrl = item.imageUrl,
                    author = item.author,
                    publishedTime = item.publishedTime,
                    read = false,
                    saved = false,
                    url = item.url
                )
                val insertedId = articleDao.insertIgnore(entity)
                if (insertedId > 0) {
                    newArticles.add(entity.copy(id = insertedId))
                }
            } else {
                var updated = existing
                var needsUpdate = false
                if ((existing.imageUrl.isNullOrBlank() || existing.imageUrl == "none") && !item.imageUrl.isNullOrBlank()) {
                    updated = updated.copy(imageUrl = item.imageUrl)
                    needsUpdate = true
                }
                if ((existing.fullContent.isNullOrBlank() || existing.isExcerptOnly != item.isExcerptOnly) && !item.fullContent.isNullOrBlank()) {
                    updated = updated.copy(fullContent = item.fullContent, isExcerptOnly = item.isExcerptOnly)
                    needsUpdate = true
                }
                if (existing.excerpt.isBlank() && item.excerpt.isNotBlank()) {
                    updated = updated.copy(excerpt = item.excerpt)
                    needsUpdate = true
                }
                if (needsUpdate) {
                    articleDao.update(updated)
                }
            }
        }
        return newArticles
    }

    suspend fun searchArticles(query: String, matchingSourceNames: Set<String> = emptySet()): List<ArticleEntity> = withContext(Dispatchers.IO) {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() && it != "&" }
        if (words.isEmpty() && matchingSourceNames.isEmpty()) return@withContext emptyList()

        val all = articleDao.getAllSearchableArticles()
        all.filter { article ->
            val sourceMatch = matchingSourceNames.isNotEmpty() && matchingSourceNames.any { it.equals(article.sourceName, ignoreCase = true) }
            val textMatch = words.isNotEmpty() && words.all { w ->
                val haystack = "${article.title} ${article.excerpt} ${article.sourceName} ${article.author.orEmpty()} ${article.fullContent.orEmpty()}".lowercase()
                haystack.contains(w)
            }
            sourceMatch || textMatch
        }
    }

    suspend fun preExtractRecentArticles(limit: Int = 8) = withContext(Dispatchers.IO) {
        try {
            val recent = articleDao.getAllSearchableArticles()
                .filter { !it.read && it.url.isNotBlank() && (it.isExcerptOnly || (it.fullContent?.length ?: 0) < 650) }
                .take(limit)
            if (recent.isEmpty()) return@withContext
            val sem = Semaphore(2)
            coroutineScope {
                recent.map { article ->
                    async {
                        sem.withPermit {
                            val extracted = runCatching { ReadabilityExtractor.extract(article.url) }.getOrNull()
                            if (!extracted.isNullOrBlank()) {
                                articleDao.updateFullContent(article.id, extracted, false)
                                if (article.imageUrl.isNullOrBlank() || article.imageUrl == "none") {
                                    val firstImg = ImageResolver.extractFromHtml(extracted, article.url)
                                    if (!firstImg.isNullOrBlank()) {
                                        articleDao.updateImageUrl(article.id, firstImg)
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        } catch (_: Exception) {}
    }

    suspend fun pruneOldUnsavedArticles(): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        // Keep at least the 15 most recent articles for every source so slow/dormant feeds are never wiped out
        val sources = sourceDao.getAllSources()
        val protectedIds = mutableListOf<Long>()
        for (source in sources) {
            protectedIds.addAll(articleDao.getRecentArticleIdsForSource(source.id, 15))
        }
        if (protectedIds.isEmpty()) {
            articleDao.pruneOldUnsavedArticles(cutoff)
        } else {
            articleDao.pruneOldUnsavedArticlesExcluding(cutoff, protectedIds)
        }
    }

    suspend fun populateStarterSourcesIfEmpty() = withContext(Dispatchers.IO) {
        val existing = sourceDao.getAllSources()
        if (existing.isEmpty()) {
            // Pick a few default starter sources (Creative Boom, Creative Review, WALLPAPER*, Dezeen, WIRED)
            val defaults = listOf("Creative Boom", "Creative Review", "WALLPAPER*", "Dezeen", "WIRED")
            for (name in defaults) {
                StarterSources.ALL.find { it.name.equals(name, ignoreCase = true) }?.let { s ->
                    addSource(s.name, s.siteUrl, s.feedUrl, s.description)
                }
            }
        }
    }
}
