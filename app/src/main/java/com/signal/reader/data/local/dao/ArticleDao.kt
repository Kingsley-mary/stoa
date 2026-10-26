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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(article: ArticleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(articles: List<ArticleEntity>): List<Long>

    @Update
    suspend fun update(article: ArticleEntity)

    @Query("UPDATE articles SET read = :read WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean)

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
}
