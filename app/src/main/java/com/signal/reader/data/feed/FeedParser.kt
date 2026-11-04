package com.signal.reader.data.feed

import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object FeedParser {

    private val rfc822Formats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US),
        SimpleDateFormat("dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    )

    fun parse(xmlContent: String, feedUrl: String): ParsedFeed {
        val sanitized = sanitizeXml(xmlContent)
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(sanitized))

        var eventType = parser.eventType
        var rootTag = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                rootTag = parser.name.lowercase(Locale.ROOT)
                break
            }
            eventType = parser.next()
        }

        return when (rootTag) {
            "rss" -> parseRss(parser, feedUrl)
            "feed" -> parseAtom(parser, feedUrl)
            else -> throw IllegalArgumentException("Unknown feed format with root tag <$rootTag>")
        }
    }

    private fun sanitizeXml(raw: String): String {
        // 1. Trim leading/trailing whitespace & Unicode BOMs (\uFEFF, \uFFFE)
        var clean = raw.trim().removePrefix("\uFEFF").removePrefix("\uFFFE").trim()

        // 2. Strip any extraneous prologue text before <?xml or root tag (<rss or <feed)
        val xmlDeclIndex = clean.indexOf("<?xml")
        if (xmlDeclIndex > 0) {
            clean = clean.substring(xmlDeclIndex)
        } else if (xmlDeclIndex == -1) {
            val rssIndex = clean.indexOf("<rss")
            val feedIndex = clean.indexOf("<feed")
            val startIndex = when {
                rssIndex != -1 && feedIndex != -1 -> minOf(rssIndex, feedIndex)
                rssIndex != -1 -> rssIndex
                feedIndex != -1 -> feedIndex
                else -> 0
            }
            if (startIndex > 0) {
                clean = clean.substring(startIndex)
            }
        }

        // 3. Fix unescaped ampersands in URLs or attributes (& not followed by valid XML entities)
        clean = clean.replace(Regex("&(?!(amp|lt|gt|quot|apos|#\\d+|#x[0-9a-fA-F]+);)"), "&amp;")

        return clean
    }

    private fun parseRss(parser: XmlPullParser, feedUrl: String): ParsedFeed {
        var channelTitle = ""
        var channelDescription = ""
        var channelSiteUrl = ""
        val items = mutableListOf<ParsedArticle>()

        var insideChannel = false
        var insideItem = false

        // Item fields
        var title = ""
        var link = ""
        var guid = ""
        var author: String? = null
        var pubDateStr = ""
        var description = ""
        var contentEncoded = ""
        val mediaCandidates = mutableListOf<MediaCandidate>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.lowercase(Locale.ROOT) ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "channel" -> insideChannel = true
                        "item" -> {
                            insideItem = true
                            title = ""
                            link = ""
                            guid = ""
                            author = null
                            pubDateStr = ""
                            description = ""
                            contentEncoded = ""
                            mediaCandidates.clear()
                        }
                        "title" -> {
                            val text = readText(parser)
                            if (insideItem) title = text else if (insideChannel) channelTitle = text
                        }
                        "link" -> {
                            val text = readText(parser)
                            if (insideItem) link = text else if (insideChannel) channelSiteUrl = text
                        }
                        "description" -> {
                            val text = readText(parser)
                            if (insideItem) description = text else if (insideChannel) channelDescription = text
                        }
                        "guid" -> if (insideItem) guid = readText(parser)
                        "creator", "author" -> if (insideItem && author == null) author = readText(parser)
                        "pubdate", "date" -> if (insideItem) pubDateStr = readText(parser)
                        "encoded" -> if (insideItem) contentEncoded = readText(parser)
                        "content" -> {
                            if (insideItem) {
                                val url = parser.getAttributeValue(null, "url")
                                val medium = parser.getAttributeValue(null, "medium")
                                val type = parser.getAttributeValue(null, "type")
                                val width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                                val height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0
                                val fileSize = parser.getAttributeValue(null, "fileSize")?.toLongOrNull() ?: 0L
                                if (!url.isNullOrBlank() && (medium == "image" || type?.startsWith("image/") == true || isImageUrl(url))) {
                                    mediaCandidates.add(MediaCandidate(url, width, height, fileSize, isThumbnail = false))
                                }
                            }
                        }
                        "thumbnail" -> {
                            if (insideItem) {
                                val url = parser.getAttributeValue(null, "url")
                                val width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                                val height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0
                                if (!url.isNullOrBlank()) {
                                    mediaCandidates.add(MediaCandidate(url, width, height, 0L, isThumbnail = true))
                                }
                            }
                        }
                        "enclosure" -> {
                            if (insideItem) {
                                val url = parser.getAttributeValue(null, "url")
                                val type = parser.getAttributeValue(null, "type")
                                val length = parser.getAttributeValue(null, "length")?.toLongOrNull() ?: 0L
                                if (!url.isNullOrBlank() && (type?.startsWith("image/") == true || isImageUrl(url))) {
                                    mediaCandidates.add(MediaCandidate(url, 0, 0, length, isThumbnail = false))
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "item" && insideItem) {
                        val publishedTime = parseDate(pubDateStr)
                        val fullHtml = if (contentEncoded.isNotBlank()) contentEncoded else description
                        val (excerpt, imageUrlFromHtml, isExcerptOnly) = processContent(fullHtml, description)

                        val bestMedia = ImageResolver.selectBestMediaCandidate(mediaCandidates)
                        val finalImageUrl = bestMedia ?: imageUrlFromHtml
                        val finalGuid = guid.ifBlank { link.ifBlank { "${title}_$publishedTime" } }

                        items.add(
                            ParsedArticle(
                                guid = finalGuid,
                                title = cleanTitle(title),
                                url = link.trim(),
                                author = author?.trim(),
                                publishedTime = publishedTime,
                                excerpt = excerpt,
                                fullContent = fullHtml.ifBlank { null },
                                isExcerptOnly = isExcerptOnly,
                                imageUrl = finalImageUrl
                            )
                        )
                        insideItem = false
                    } else if (name == "channel") {
                        insideChannel = false
                    }
                }
            }
            eventType = parser.next()
        }

        return ParsedFeed(
            title = cleanTitle(channelTitle),
            description = Jsoup.parse(channelDescription).text(),
            siteUrl = channelSiteUrl.trim().ifBlank { feedUrl },
            feedUrl = feedUrl,
            items = items
        )
    }

    private fun parseAtom(parser: XmlPullParser, feedUrl: String): ParsedFeed {
        var feedTitle = ""
        var feedDescription = ""
        var feedSiteUrl = ""
        val items = mutableListOf<ParsedArticle>()

        var insideEntry = false

        var title = ""
        var link = ""
        var id = ""
        var author: String? = null
        var updatedStr = ""
        var publishedStr = ""
        var summary = ""
        var content = ""
        val mediaCandidates = mutableListOf<MediaCandidate>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.lowercase(Locale.ROOT) ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "entry" -> {
                            insideEntry = true
                            title = ""
                            link = ""
                            id = ""
                            author = null
                            updatedStr = ""
                            publishedStr = ""
                            summary = ""
                            content = ""
                            mediaCandidates.clear()
                        }
                        "title" -> {
                            val text = readText(parser)
                            if (insideEntry) title = text else feedTitle = text
                        }
                        "subtitle" -> if (!insideEntry) feedDescription = readText(parser)
                        "id" -> if (insideEntry) id = readText(parser)
                        "name" -> if (insideEntry && author == null) author = readText(parser)
                        "published" -> if (insideEntry) publishedStr = readText(parser)
                        "updated" -> if (insideEntry) updatedStr = readText(parser)
                        "summary" -> if (insideEntry) summary = readText(parser)
                        "content" -> {
                            val url = parser.getAttributeValue(null, "url")
                            val medium = parser.getAttributeValue(null, "medium")
                            val type = parser.getAttributeValue(null, "type")
                            val width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                            val height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0
                            val fileSize = parser.getAttributeValue(null, "fileSize")?.toLongOrNull() ?: 0L
                            if (insideEntry && !url.isNullOrBlank() && (medium == "image" || type?.startsWith("image/") == true || isImageUrl(url))) {
                                mediaCandidates.add(MediaCandidate(url, width, height, fileSize, isThumbnail = false))
                            } else if (insideEntry) {
                                content = readText(parser)
                            }
                        }
                        "thumbnail" -> {
                            if (insideEntry) {
                                val url = parser.getAttributeValue(null, "url")
                                val width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                                val height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0
                                if (!url.isNullOrBlank()) {
                                    mediaCandidates.add(MediaCandidate(url, width, height, 0L, isThumbnail = true))
                                }
                            }
                        }
                        "link" -> {
                            val rel = parser.getAttributeValue(null, "rel")
                            val href = parser.getAttributeValue(null, "href")
                            val type = parser.getAttributeValue(null, "type")
                            val length = parser.getAttributeValue(null, "length")?.toLongOrNull() ?: 0L

                            if (insideEntry) {
                                if (rel == "enclosure" && (type?.startsWith("image/") == true || (!href.isNullOrBlank() && isImageUrl(href)))) {
                                    if (!href.isNullOrBlank()) {
                                        mediaCandidates.add(MediaCandidate(href, 0, 0, length, isThumbnail = false))
                                    }
                                } else if (rel == "alternate" || rel == null) {
                                    if (!href.isNullOrBlank()) link = href
                                }
                            } else {
                                if ((rel == "alternate" || rel == null) && !href.isNullOrBlank()) {
                                    feedSiteUrl = href
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "entry" && insideEntry) {
                        val dateStr = publishedStr.ifBlank { updatedStr }
                        val publishedTime = parseDate(dateStr)
                        val fullHtml = if (content.isNotBlank()) content else summary
                        val (excerpt, imageUrlFromHtml, isExcerptOnly) = processContent(fullHtml, summary)

                        val bestMedia = ImageResolver.selectBestMediaCandidate(mediaCandidates)
                        val finalImageUrl = bestMedia ?: imageUrlFromHtml
                        val finalGuid = id.ifBlank { link.ifBlank { "${title}_$publishedTime" } }

                        items.add(
                            ParsedArticle(
                                guid = finalGuid,
                                title = cleanTitle(title),
                                url = link.trim(),
                                author = author?.trim(),
                                publishedTime = publishedTime,
                                excerpt = excerpt,
                                fullContent = fullHtml.ifBlank { null },
                                isExcerptOnly = isExcerptOnly,
                                imageUrl = finalImageUrl
                            )
                        )
                        insideEntry = false
                    }
                }
            }
            eventType = parser.next()
        }

        return ParsedFeed(
            title = cleanTitle(feedTitle),
            description = Jsoup.parse(feedDescription).text(),
            siteUrl = feedSiteUrl.trim().ifBlank { feedUrl },
            feedUrl = feedUrl,
            items = items
        )
    }

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            val event = parser.next()
            when (event) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (depth >= 1) {
                        sb.append(parser.text.orEmpty())
                    }
                }
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString().trim()
    }

    private fun isImageUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".webp") || lower.endsWith(".gif") || lower.contains("/images/") ||
                lower.contains("format=jpg") || lower.contains("format=png") || lower.contains("format=webp")
    }

    private fun cleanTitle(title: String): String {
        return Jsoup.parse(title).text().trim()
    }

    private fun processContent(fullHtml: String, fallbackText: String): Triple<String, String?, Boolean> {
        val doc = Jsoup.parse(fullHtml.ifBlank { fallbackText })
        val highResImg = ImageResolver.extractBestImageFromHtml(doc)
            ?: if (fallbackText.isNotBlank()) ImageResolver.extractBestImageFromHtml(Jsoup.parse(fallbackText)) else null

        val plainText = doc.text().trim()
        val wordCount = plainText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val paragraphCount = doc.select("p").size

        // Full text vs excerpt rule from Brief:
        // Treat an article as full text only if the feed content is clearly article-length (>= 150 words and >= 2 paragraphs)
        // AND doesn't explicitly link out to view the full gallery / full story
        val hasGalleryOrFullLink = fullHtml.contains("view full gallery", ignoreCase = true) ||
                fullHtml.contains("view full story", ignoreCase = true) ||
                fullHtml.contains("read full article", ignoreCase = true) ||
                fullHtml.contains("click here to view full", ignoreCase = true)
        val isExcerptOnly = hasGalleryOrFullLink || wordCount < 150 || (wordCount < 250 && paragraphCount <= 1)

        val excerpt = if (plainText.length > 280) {
            plainText.substring(0, 277).trimEnd() + "…"
        } else {
            plainText
        }

        return Triple(excerpt, highResImg, isExcerptOnly)
    }

    fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        val trimmed = dateStr.trim()
        for (format in rfc822Formats) {
            try {
                val date = format.parse(trimmed)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }
}
