package com.signal.reader.data.repository

import com.signal.reader.data.feed.DiscoveryResult
import com.signal.reader.data.feed.FeedDiscovery
import com.signal.reader.data.feed.FeedFetcher
import com.signal.reader.data.feed.ImageResolver
import com.signal.reader.data.feed.ParsedArticle
import com.signal.reader.data.feed.StarterSource
import com.signal.reader.data.feed.StarterSources
import com.signal.reader.data.local.SignalDatabase
import com.signal.reader.data.local.dao.SourceWithUnreadCount
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.data.local.entity.SourceEntity
import okhttp3.Request
import org.jsoup.Jsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class FeedRepository(
    private val database: SignalDatabase,
    private val fetcher: FeedFetcher = FeedFetcher(),
    val discovery: FeedDiscovery = FeedDiscovery(fetcher)
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sourceDao = database.sourceDao()
    private val articleDao = database.articleDao()

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
        val existing = sourceDao.getSourceByFeedUrl(feedUrl)
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
            name = name,
            siteUrl = siteUrl,
            feedUrl = feedUrl,
            description = description,
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
            val existing = sourceDao.getSourceByFeedUrl(item.feedUrl)
            if (existing != null) {
                if (!existing.followed) {
                    sourceDao.setFollowed(existing.id, true)
                }
                idsToRefresh.add(existing.id)
            } else {
                currentOrder++
                val entity = SourceEntity(
                    name = item.name,
                    siteUrl = item.siteUrl,
                    feedUrl = item.feedUrl,
                    description = item.description,
                    followed = true,
                    displayOrder = currentOrder
                )
                val newId = sourceDao.insert(entity)
                idsToRefresh.add(newId)
            }
        }

        // Fetch in background concurrently
        repoScope.launch {
            idsToRefresh.forEach { id ->
                launch { refreshSource(id) }
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

    private fun notifyWidgetUpdate() {
        try {
            com.signal.reader.widget.StoaWidgetProvider.updateAllWidgets(com.signal.reader.SignalApplication.instance)
        } catch (_: Exception) {}
    }

    suspend fun markArticleRead(articleId: Long) = withContext(Dispatchers.IO) {
        articleDao.setRead(articleId, true)
        notifyWidgetUpdate()
    }

    suspend fun toggleSaved(articleId: Long) = withContext(Dispatchers.IO) {
        val article = articleDao.getArticleById(articleId) ?: return@withContext
        val newSaved = !article.saved
        articleDao.setSaved(articleId, newSaved, if (newSaved) System.currentTimeMillis() else null)
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

    suspend fun refreshSource(sourceId: Long): Int = withContext(Dispatchers.IO) {
        val source = sourceDao.getSourceById(sourceId) ?: return@withContext 0
        try {
            val parsedFeed = try {
                fetcher.fetchAndParse(source.feedUrl)
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
            sourceDao.recordSuccess(sourceId, System.currentTimeMillis())
            if (newArticles.isNotEmpty()) {
                if (source.notificationsEnabled) {
                    try {
                        val notifManager = com.signal.reader.SignalApplication.instance.notificationManager
                        notifManager.postNewArticlesNotification(source, newArticles)
                    } catch (_: Exception) {
                    }
                }
            }
            repoScope.launch {
                resolveMissingArticleImages(15)
            }
            newArticles.size
        } catch (e: Exception) {
            sourceDao.recordFailure(sourceId, e.message ?: "Network or parsing error")
            0
        }
    }

    suspend fun refreshAllFollowedSources(): Int = withContext(Dispatchers.IO) {
        val sources = sourceDao.getFollowedSources()
        var totalNew = 0
        coroutineScope {
            val tasks = sources.map { source ->
                async {
                    refreshSource(source.id)
                }
            }
            totalNew = tasks.awaitAll().sum()
        }
        notifyWidgetUpdate()
        repoScope.launch {
            resolveMissingArticleImages(25)
        }
        totalNew
    }

    suspend fun resetFailedArticleImages() = withContext(Dispatchers.IO) {
        articleDao.resetFailedArticleImages()
    }

    suspend fun updateArticleFullContent(articleId: Long, fullContent: String, isExcerptOnly: Boolean = false) = withContext(Dispatchers.IO) {
        articleDao.updateFullContent(articleId, fullContent, isExcerptOnly)
    }

    suspend fun updateArticleImage(articleId: Long, imageUrl: String) = withContext(Dispatchers.IO) {
        articleDao.updateImageUrl(articleId, imageUrl)
    }

    suspend fun resolveMissingArticleImages(limit: Int = 30) = withContext(Dispatchers.IO) {
        val missing = articleDao.getArticlesWithoutImage(limit)
        if (missing.isEmpty()) return@withContext

        val client = FeedFetcher.createDefaultClient()
        val semaphore = Semaphore(4)
        coroutineScope {
            missing.map { article ->
                async {
                    val url = article.url
                    if (url.isBlank()) {
                        articleDao.updateImageUrl(article.id, "none")
                        return@async
                    }
                    semaphore.withPermit {
                        try {
                            val req = Request.Builder()
                                .url(url)
                                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36 StoaReader/1.4")
                                .build()
                            client.newCall(req).execute().use { response ->
                                if (response.isSuccessful) {
                                    val body = response.body?.string()
                                    if (!body.isNullOrBlank()) {
                                        val doc = Jsoup.parse(body)
                                        val ogImg = ImageResolver.extractOpenGraphImage(doc)
                                            ?: ImageResolver.extractBestImageFromHtml(doc)
                                        if (!ogImg.isNullOrBlank()) {
                                            articleDao.updateImageUrl(article.id, ogImg)
                                        } else {
                                            articleDao.updateImageUrl(article.id, "none")
                                        }
                                    } else {
                                        articleDao.updateImageUrl(article.id, "none")
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // Don't mark as "none" on network exceptions; allow retry next time
                        }
                    }
                }
            }.awaitAll()
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
                val haystack = "${article.title} ${article.excerpt} ${article.sourceName}".lowercase()
                haystack.contains(w)
            }
            sourceMatch || textMatch
        }
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
