package com.signal.reader.data.feed

data class ParsedFeed(
    val title: String,
    val description: String,
    val siteUrl: String,
    val feedUrl: String,
    val items: List<ParsedArticle>
)

data class ParsedArticle(
    val guid: String,
    val title: String,
    val url: String,
    val author: String?,
    val publishedTime: Long,
    val excerpt: String,
    val fullContent: String?,
    val isExcerptOnly: Boolean,
    val imageUrl: String?
)
