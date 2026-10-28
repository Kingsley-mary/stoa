package com.signal.reader.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeUtils {

    fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        if (diff < 0) return "Just now"

        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        if (minutes < 1) return "Just now"
        if (minutes < 60) return "$minutes min ago"

        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        if (hours == 1L) return "1 hr ago"
        if (hours < 24) return "$hours hr ago"

        val days = TimeUnit.MILLISECONDS.toDays(diff)
        if (days == 1L) return "Yesterday"
        if (days < 7) return "$days days ago"

        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.US)
        return dateFormat.format(Date(timestamp))
    }

    fun formatArticleDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.US)
        return dateFormat.format(Date(timestamp))
    }

    fun calculateReadTimeMinutes(content: String?): Int {
        if (content.isNullOrBlank()) return 1
        val words = content.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        return maxOf(1, Math.ceil(words / 200.0).toInt())
    }

    fun formatReadTime(content: String?, excerpt: String? = null): String {
        val textToCount = if (!content.isNullOrBlank()) content else (excerpt ?: "")
        val minutes = calculateReadTimeMinutes(textToCount)
        return "$minutes min read"
    }
}
