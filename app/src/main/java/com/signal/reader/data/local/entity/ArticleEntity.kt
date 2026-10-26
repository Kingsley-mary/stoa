package com.signal.reader.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["guid", "sourceId"], unique = true),
        Index(value = ["sourceId"]),
        Index(value = ["publishedTime"]),
        Index(value = ["saved"])
    ]
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceId: Long,
    val sourceName: String,
    val guid: String,
    val title: String,
    val excerpt: String = "",
    val fullContent: String? = null,
    val isExcerptOnly: Boolean = false,
    val imageUrl: String? = null,
    val author: String? = null,
    val publishedTime: Long = 0,
    val read: Boolean = false,
    val saved: Boolean = false,
    val savedTime: Long? = null,
    val url: String = ""
)
