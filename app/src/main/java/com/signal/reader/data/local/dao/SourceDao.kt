package com.signal.reader.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.signal.reader.data.local.entity.SourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {

    @Query("SELECT * FROM sources ORDER BY displayOrder ASC, id ASC")
    fun getAllSourcesFlow(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE followed = 1 ORDER BY displayOrder ASC, id ASC")
    fun getFollowedSourcesFlow(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE followed = 1 AND muted = 0 ORDER BY displayOrder ASC, id ASC")
    fun getFollowedUnmutedSourcesFlow(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources")
    suspend fun getAllSources(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE followed = 1")
    suspend fun getFollowedSources(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id LIMIT 1")
    fun getSourceByIdFlow(id: Long): Flow<SourceEntity?>

    @Query("SELECT * FROM sources WHERE id = :id LIMIT 1")
    suspend fun getSourceById(id: Long): SourceEntity?

    @Query("SELECT * FROM sources WHERE feedUrl = :feedUrl LIMIT 1")
    suspend fun getSourceByFeedUrl(feedUrl: String): SourceEntity?

    @Query("SELECT * FROM sources WHERE LOWER(feedUrl) = LOWER(:feedUrl) OR LOWER(feedUrl) = LOWER(:altFeedUrl) LIMIT 1")
    suspend fun getSourceByFeedUrlWithAlt(feedUrl: String, altFeedUrl: String): SourceEntity?

    @Query("SELECT * FROM sources WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getSourceByName(name: String): SourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: SourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<SourceEntity>): List<Long>

    @Update
    suspend fun update(source: SourceEntity)

    @Delete
    suspend fun delete(source: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE sources SET followed = :followed WHERE id = :id")
    suspend fun setFollowed(id: Long, followed: Boolean)

    @Query("UPDATE sources SET muted = :muted WHERE id = :id")
    suspend fun setMuted(id: Long, muted: Boolean)

    @Query("UPDATE sources SET notificationsEnabled = :enabled WHERE id = :id")
    suspend fun setNotificationsEnabled(id: Long, enabled: Boolean)

    @Query("SELECT * FROM sources WHERE followed = 1 AND notificationsEnabled = 1")
    suspend fun getNotificationEnabledSources(): List<SourceEntity>

    @Query("UPDATE sources SET feedUrl = :feedUrl WHERE id = :id")
    suspend fun updateFeedUrl(id: Long, feedUrl: String)

    @Query("""
        UPDATE sources 
        SET lastFetched = :timestamp, 
            isBroken = 0, 
            consecutiveFailures = 0, 
            errorState = null 
        WHERE id = :id
    """)
    suspend fun recordSuccess(id: Long, timestamp: Long)

    @Query("""
        UPDATE sources 
        SET consecutiveFailures = consecutiveFailures + 1,
            isBroken = CASE WHEN (consecutiveFailures + 1) >= 3 THEN 1 ELSE isBroken END,
            errorState = :error
        WHERE id = :id
    """)
    suspend fun recordFailure(id: Long, error: String)
}
