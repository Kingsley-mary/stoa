package com.signal.reader

import com.signal.reader.data.catalog.CatalogRepository
import com.signal.reader.data.catalog.CatalogSource
import com.signal.reader.data.feed.ImageResolver
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FeatureTests {

    @Test
    fun testCatalogAssetParsedAndTopicsHaveAtLeastThreeSources() {
        val catalogFile = File("src/main/assets/catalog.json")
        assertTrue("catalog.json must exist in assets", catalogFile.exists())

        val jsonText = catalogFile.readText()
        val sourceBlockRegex = """\{\s*"name":\s*"([^"]+)",\s*"description":\s*"([^"]*)",\s*"siteUrl":\s*"([^"]+)",\s*"feedUrl":\s*"([^"]+)",\s*"topics":\s*\[([^\]]+)\]\s*\}""".toRegex()
        val matches = sourceBlockRegex.findAll(jsonText).toList()
        assertTrue("Catalog should contain at least 40 sources, found ${matches.size}", matches.size >= 40)

        val topicCounts = mutableMapOf<String, Int>()
        for (m in matches) {
            val topicsStr = m.groupValues[5]
            val topics = topicsStr.split(",").map { it.trim().removeSurrounding("\"") }
            for (t in topics) {
                topicCounts[t] = (topicCounts[t] ?: 0) + 1
            }
        }

        val requiredTopics = listOf(
            "Design", "Architecture", "Typography", "Illustration", "Photography",
            "Fashion", "Interiors", "Art", "Branding", "Tech",
            "AI", "Gadgets", "Games", "Film", "Music",
            "Science", "Culture", "Business", "Finance", "Politics"
        )

        for (topic in requiredTopics) {
            val count = topicCounts[topic] ?: 0
            assertTrue("Topic '$topic' must have at least 3 verified sources, but has $count", count >= 3)
        }
    }

    @Test
    fun testSuggestionScoringAlgorithm() {
        val testSources = listOf(
            CatalogSource("Source A", "Desc", "https://a.com", "https://a.com/rss", listOf("Tech", "AI", "Science")),
            CatalogSource("Source B", "Desc", "https://b.com", "https://b.com/rss", listOf("Tech")),
            CatalogSource("Source C", "Desc", "https://c.com", "https://c.com/rss", listOf("Design", "Art")),
            CatalogSource("Source D", "Desc", "https://d.com", "https://d.com/rss", listOf("Finance", "Business", "Tech")),
            CatalogSource("Source E", "Desc", "https://e.com", "https://e.com/rss", listOf("Politics"))
        )

        val picked = setOf("Tech", "AI", "Business")
        val (suggested, more) = CatalogRepository.computeSuggestions(picked, testSources)

        // Source A has 2 matches (Tech, AI)
        // Source D has 2 matches (Tech, Business)
        // Source B has 1 match (Tech)
        assertTrue(suggested.contains(testSources[0])) // Source A
        assertTrue(suggested.contains(testSources[3])) // Source D
        assertTrue(suggested.size <= 8)
        assertTrue(more.size <= 6)
    }

    @Test
    fun testWhyTextFormatting() {
        val source = CatalogSource("Verge", "Desc", "https://theverge.com", "https://theverge.com/rss", listOf("Tech", "Gadgets", "AI", "Games"))
        
        val pickedTwo = setOf("Tech", "AI")
        val whyTwo = CatalogRepository.getWhyText(source, pickedTwo)
        assertTrue(whyTwo.contains("Because you picked"))
        assertTrue(whyTwo.contains("Tech") && whyTwo.contains("AI"))
        assertFalse(whyTwo.contains("and"))

        val pickedFour = setOf("Tech", "Gadgets", "AI", "Games")
        val whyFour = CatalogRepository.getWhyText(source, pickedFour)
        assertTrue(whyFour.contains("and 2 more"))
    }

    @Test
    fun testImageResolverUpgrades() {
        // WordPress thumbnail stripping
        val wpThumb = "https://example.com/wp-content/uploads/2026/photo-768x512.jpg"
        assertEquals("https://example.com/wp-content/uploads/2026/photo.jpg", ImageResolver.resolveHighRes(wpThumb))

        // Substack width upgrade
        val substack = "https://substackcdn.com/image/fetch/w_360,c_limit,f_auto,q_auto:good/https://img.jpg"
        assertEquals("https://substackcdn.com/image/fetch/w_1456,c_limit,f_auto,q_auto:good/https://img.jpg", ImageResolver.resolveHighRes(substack))

        // Medium max width upgrade
        val medium = "https://miro.medium.com/max/400/1*abc123.jpeg"
        assertEquals("https://miro.medium.com/max/2000/1*abc123.jpeg", ImageResolver.resolveHighRes(medium))

        // YouTube thumbnail upgrade
        val yt = "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", ImageResolver.resolveHighRes(yt))
    }

    @Test
    fun testHtmlSrcsetExtraction() {
        val html = """
            <div>
              <p>Hello world</p>
              <img src="fallback-300.jpg" srcset="photo-400.jpg 400w, photo-1200.jpg 1200w, photo-2048.jpg 2048w, photo-800.jpg 800w" alt="Hero"/>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, "https://example.com")
        val best = ImageResolver.extractBestImageFromHtml(doc)
        assertEquals("photo-2048.jpg", best)
    }

    @Test
    fun testSharedTextUrlExtraction() {
        val sharedText = "Check this great article on Verge: https://www.theverge.com/2026/10/31/sample-story?utm_source=twitter and tell me what you think!"
        val urlRegex = """https?://[^\s<>"{}|\\^`]+""".toRegex()
        val extracted = urlRegex.find(sharedText)?.value
        assertNotNull(extracted)
        assertEquals("https://www.theverge.com/2026/10/31/sample-story?utm_source=twitter", extracted)
    }

    @Test
    fun testFeedSchemeNormalization() {
        fun convertScheme(uriStr: String): String {
            return when {
                uriStr.startsWith("feed:https://", ignoreCase = true) -> uriStr.substring(5)
                uriStr.startsWith("feed:http://", ignoreCase = true) -> uriStr.substring(5)
                uriStr.startsWith("feed://", ignoreCase = true) -> "https://" + uriStr.substring(7)
                uriStr.startsWith("feeds:https://", ignoreCase = true) -> uriStr.substring(6)
                uriStr.startsWith("feeds:http://", ignoreCase = true) -> uriStr.substring(6)
                uriStr.startsWith("feeds://", ignoreCase = true) -> "https://" + uriStr.substring(8)
                else -> uriStr.replaceFirst("^(feed|feeds):/*".toRegex(RegexOption.IGNORE_CASE), "https://")
            }
        }

        assertEquals("https://example.com/rss.xml", convertScheme("feed:https://example.com/rss.xml"))
        assertEquals("https://example.com/feed", convertScheme("feed://example.com/feed"))
        assertEquals("https://example.com/atom.xml", convertScheme("feeds:https://example.com/atom.xml"))
        assertEquals("https://example.com/feed", convertScheme("feeds://example.com/feed"))
    }

    @Test
    fun testUpdateVersionComparison() {
        // Newer versions
        assertTrue(com.signal.reader.data.update.UpdateManager.isVersionStringNewer("1.4.1", "1.4"))
        assertTrue(com.signal.reader.data.update.UpdateManager.isVersionStringNewer("v1.4.1", "1.4"))
        assertTrue(com.signal.reader.data.update.UpdateManager.isVersionStringNewer("1.5.0", "1.4.1"))
        assertTrue(com.signal.reader.data.update.UpdateManager.isVersionStringNewer("2.0", "1.9.9"))
        assertTrue(com.signal.reader.data.update.UpdateManager.isVersionStringNewer("1.4.10", "1.4.9"))

        // Same or older versions
        assertFalse(com.signal.reader.data.update.UpdateManager.isVersionStringNewer("1.4.1", "1.4.1"))
        assertFalse(com.signal.reader.data.update.UpdateManager.isVersionStringNewer("v1.4.1", "1.4.1"))
        assertFalse(com.signal.reader.data.update.UpdateManager.isVersionStringNewer("1.4", "1.4.1"))
        assertFalse(com.signal.reader.data.update.UpdateManager.isVersionStringNewer("1.4", "1.4"))
    }
}
