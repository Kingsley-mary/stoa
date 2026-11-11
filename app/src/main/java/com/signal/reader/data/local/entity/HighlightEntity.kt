package com.signal.reader.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "highlights",
    indices = [
        Index(value = ["articleId"]),
        Index(value = ["createdAt"])
    ]
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val articleId: Long,
    val articleTitle: String,
    val sourceName: String,
    val quoteText: String,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
