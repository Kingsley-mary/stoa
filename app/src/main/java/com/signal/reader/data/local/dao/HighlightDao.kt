package com.signal.reader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.signal.reader.data.local.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {

    @Query("SELECT * FROM highlights ORDER BY createdAt DESC")
    fun getAllHighlightsFlow(): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE articleId = :articleId ORDER BY createdAt ASC")
    fun getHighlightsForArticleFlow(articleId: Long): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(highlight: HighlightEntity): Long

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM highlights WHERE articleId = :articleId AND quoteText = :quoteText")
    suspend fun deleteByQuote(articleId: Long, quoteText: String)
}
