package com.signal.reader.data.repository

import com.signal.reader.data.feed.FeedDiscovery
import com.signal.reader.data.feed.FeedFetcher
import com.signal.reader.data.feed.ParsedArticle
import com.signal.reader.data.feed.StarterSources
import com.signal.reader.data.local.SignalDatabase
import com.signal.reader.data.local.dao.SourceWithUnreadCount
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.data.local.entity.SourceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class FeedRepository(
    private val database: SignalDatabase,
    private val fetcher: FeedFetcher = FeedFetcher(),
    val discovery: FeedDiscovery = FeedDiscovery(fetcher)
) {
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
            // Fetch newest items immediately
            refreshSource(existing.id)
            return@withContext existing.id
        }

        val entity = SourceEntity(
            name = name,
            siteUrl = siteUrl,
            feedUrl = feedUrl,
            description = description,
            followed = true,
            displayOrder = (sourceDao.getAllSources().maxOfOrNull { it.displayOrder } ?: 0) + 1
        )
        val newId = sourceDao.insert(entity)
        refreshSource(newId)
        newId
    }

    suspend fun unfollowSource(sourceId: Long) = withContext(Dispatchers.IO) {
        // Keep saved articles, remove unsaved articles and the source
        articleDao.deleteUnsavedBySourceId(sourceId)
        sourceDao.deleteById(sourceId)
    }

    suspend fun setMuted(sourceId: Long, muted: Boolean) = withContext(Dispatchers.IO) {
        sourceDao.setMuted(sourceId, muted)
    }

    suspend fun markArticleRead(articleId: Long) = withContext(Dispatchers.IO) {
        articleDao.setRead(articleId, true)
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
    }

    suspend fun refreshSource(sourceId: Long): Int = withContext(Dispatchers.IO) {
        val source = sourceDao.getSourceById(sourceId) ?: return@withContext 0
        try {
            val parsedFeed = fetcher.fetchAndParse(source.feedUrl)
            val newArticlesCount = saveArticles(source, parsedFeed.items)
            sourceDao.recordSuccess(sourceId, System.currentTimeMillis())
            newArticlesCount
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
        totalNew
    }

    private suspend fun saveArticles(source: SourceEntity, items: List<ParsedArticle>): Int {
        var newCount = 0
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
                if (insertedId > 0) newCount++
            }
        }
        return newCount
    }

    suspend fun searchArticles(query: String): List<ArticleEntity> = withContext(Dispatchers.IO) {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return@withContext emptyList()

        val all = articleDao.getAllSearchableArticles()
        // Search rule: matching case-insensitive, over title, excerpt, and source name. Every word must match.
        all.filter { article ->
            val haystack = "${article.title} ${article.excerpt} ${article.sourceName}".lowercase()
            words.all { w -> haystack.contains(w) }
        }
    }

    suspend fun pruneOldUnsavedArticles(): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        articleDao.pruneOldUnsavedArticles(cutoff)
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
