package com.signal.reader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.signal.reader.data.local.entity.ArticleEntity
import kotlinx.coroutines.flow.Flow

data class SourceWithUnreadCount(
    val sourceId: Long,
    val unreadCount: Int,
    val latestArticleTime: Long
)

@Dao
interface ArticleDao {

    @Query("""
        SELECT a.* FROM articles a
        INNER JOIN sources s ON a.sourceId = s.id
        WHERE s.followed = 1 AND s.muted = 0
        ORDER BY a.publishedTime DESC
    """)
    fun getHomeStreamFlow(): Flow<List<ArticleEntity>>

    @Query("""
        SELECT * FROM articles
        WHERE sourceId = :sourceId
        ORDER BY publishedTime DESC
    """)
    fun getArticlesBySourceFlow(sourceId: Long): Flow<List<ArticleEntity>>

    @Query("""
        SELECT * FROM articles
        WHERE saved = 1
        ORDER BY savedTime DESC, publishedTime DESC
    """)
    fun getSavedArticlesFlow(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    fun getArticleByIdFlow(id: Long): Flow<ArticleEntity?>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun getArticleById(id: Long): ArticleEntity?

    @Query("SELECT * FROM articles WHERE guid = :guid AND sourceId = :sourceId LIMIT 1")
    suspend fun getArticleByGuid(guid: String, sourceId: Long): ArticleEntity?

    @Query("""
        SELECT * FROM articles 
        WHERE url = :link 
           OR guid = :link 
           OR url = :altLink 
           OR guid = :altLink 
        LIMIT 1
    """)
    suspend fun findArticleByLinkOrGuid(link: String, altLink: String = link): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(article: ArticleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(articles: List<ArticleEntity>): List<Long>

    @Update
    suspend fun update(article: ArticleEntity)

    @Query("UPDATE articles SET read = :read WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean)

    @Query("UPDATE articles SET imageUrl = :imageUrl WHERE id = :id")
    suspend fun updateImageUrl(id: Long, imageUrl: String)

    @Query("UPDATE articles SET imageUrl = :imageUrl WHERE id = :id")
    suspend fun updateArticleImage(id: Long, imageUrl: String)

    @Query("UPDATE articles SET imageUrl = NULL WHERE imageUrl = 'none'")
    suspend fun resetFailedArticleImages()

    @Query("UPDATE articles SET fullContent = :fullContent, isExcerptOnly = :isExcerptOnly WHERE id = :id")
    suspend fun updateFullContent(id: Long, fullContent: String, isExcerptOnly: Boolean)

    @Query("SELECT * FROM articles WHERE (imageUrl IS NULL OR imageUrl = '' OR imageUrl = 'none') AND url IS NOT NULL AND url != '' ORDER BY publishedTime DESC LIMIT 150")
    suspend fun getArticlesWithoutImages(): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE (imageUrl IS NULL OR imageUrl = '' OR imageUrl = 'none') AND url IS NOT NULL AND url != '' ORDER BY publishedTime DESC LIMIT :limit")
    suspend fun getArticlesWithoutImage(limit: Int = 150): List<ArticleEntity>

    @Query("UPDATE articles SET read = 1 WHERE sourceId IN (:sourceIds) AND read = 0")
    suspend fun markAllAsReadForSources(sourceIds: List<Long>)

    @Query("UPDATE articles SET read = 1 WHERE read = 0")
    suspend fun markAllAsRead()

    @Query("""
        UPDATE articles 
        SET saved = :saved, 
            savedTime = CASE WHEN :saved = 1 THEN :savedTime ELSE null END 
        WHERE id = :id
    """)
    suspend fun setSaved(id: Long, saved: Boolean, savedTime: Long? = System.currentTimeMillis())

    @Query("DELETE FROM articles WHERE sourceId = :sourceId AND saved = 0")
    suspend fun deleteUnsavedBySourceId(sourceId: Long)

    @Query("SELECT id FROM articles WHERE sourceId = :sourceId ORDER BY publishedTime DESC LIMIT :limit")
    suspend fun getRecentArticleIdsForSource(sourceId: Long, limit: Int = 15): List<Long>

    @Query("DELETE FROM articles WHERE saved = 0 AND publishedTime < :cutoffTimestamp AND id NOT IN (:protectedIds)")
    suspend fun pruneOldUnsavedArticlesExcluding(cutoffTimestamp: Long, protectedIds: List<Long>): Int

    @Query("DELETE FROM articles WHERE saved = 0 AND publishedTime < :cutoffTimestamp")
    suspend fun pruneOldUnsavedArticles(cutoffTimestamp: Long): Int

    @Query("""
        SELECT sourceId, COUNT(CASE WHEN read = 0 THEN 1 END) AS unreadCount,
               COALESCE(MAX(publishedTime), 0) AS latestArticleTime
        FROM articles
        GROUP BY sourceId
    """)
    fun getUnreadCountsFlow(): Flow<List<SourceWithUnreadCount>>

    @Query("SELECT COUNT(*) FROM articles WHERE sourceId = :sourceId")
    suspend fun getArticleCountForSource(sourceId: Long): Int

    @Query("SELECT COUNT(*) FROM articles WHERE sourceId = :sourceId AND read = 0")
    suspend fun getUnreadCountForSource(sourceId: Long): Int

    @Query("SELECT MAX(publishedTime) FROM articles WHERE sourceId = :sourceId")
    suspend fun getLatestArticleTimeForSource(sourceId: Long): Long?

    @Query("""
        SELECT a.* FROM articles a
        LEFT JOIN sources s ON a.sourceId = s.id
        WHERE (s.followed = 1 OR a.saved = 1)
        ORDER BY a.publishedTime DESC
    """)
    suspend fun getAllSearchableArticles(): List<ArticleEntity>

    @Query("UPDATE articles SET sourceId = :targetSourceId WHERE sourceId = :sourceId")
    suspend fun reassignArticlesSource(sourceId: Long, targetSourceId: Long)

    @Query("DELETE FROM articles WHERE id NOT IN (SELECT MIN(id) FROM articles GROUP BY guid, sourceId)")
    suspend fun deduplicateArticlesByGuid()

    @Query("DELETE FROM articles WHERE url IS NOT NULL AND url != '' AND id NOT IN (SELECT MIN(id) FROM articles WHERE url IS NOT NULL AND url != '' GROUP BY url, sourceId)")
    suspend fun deduplicateArticlesByUrl()

    @Query("DELETE FROM articles WHERE id NOT IN (SELECT MIN(id) FROM articles GROUP BY LOWER(title), sourceId)")
    suspend fun deduplicateArticlesByTitle()
}
