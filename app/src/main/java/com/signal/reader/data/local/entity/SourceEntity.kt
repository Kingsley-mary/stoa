package com.signal.reader.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sources",
    indices = [
        Index(value = ["feedUrl"], unique = true)
    ]
)
data class SourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val siteUrl: String,
    val feedUrl: String,
    val description: String = "",
    val followed: Boolean = true,
    val muted: Boolean = false,
    val lastFetched: Long = 0,
    val errorState: String? = null,
    val isBroken: Boolean = false,
    val consecutiveFailures: Int = 0,
    val displayOrder: Int = 0,
    val notificationsEnabled: Boolean = true,
    val etag: String? = null,
    val lastModified: String? = null,
    val isPinned: Boolean = false
)
