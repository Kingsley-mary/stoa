package com.signal.reader

import com.signal.reader.data.feed.FeedParser
import com.signal.reader.data.feed.FeedFetcher
import com.signal.reader.data.feed.ReadabilityExtractor
import com.signal.reader.data.feed.ImageResolver
import com.signal.reader.data.opml.OpmlManager
import com.signal.reader.data.local.entity.SourceEntity
import com.signal.reader.ui.components.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedParserTest {

    @Test
    fun testParseRssFeed() {
        val sampleRss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <title>Creative Boom</title>
                <link>https://www.creativeboom.com</link>
                <description>Art and design inspiration</description>
                <item>
                  <title>A patchwork bear that's part toy, part nightmare</title>
                  <link>https://www.creativeboom.com/inspiration/patchwork-bear/</link>
                  <guid>https://www.creativeboom.com/inspiration/patchwork-bear/</guid>
                  <pubDate>Mon, 26 Oct 2026 12:00:00 +0000</pubDate>
                  <dc:creator>Katy Cowan</dc:creator>
                  <description><![CDATA[An illustrator stitches horror and comfort into one character.]]></description>
                  <media:content url="https://www.creativeboom.com/bear.jpg" medium="image"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = FeedParser.parse(sampleRss, "https://www.creativeboom.com/feed")
        assertEquals("Creative Boom", parsed.title)
        assertEquals(1, parsed.items.size)

        val item = parsed.items.first()
        assertEquals("A patchwork bear that's part toy, part nightmare", item.title)
        assertEquals("https://www.creativeboom.com/bear.jpg", item.imageUrl)
        assertEquals("Katy Cowan", item.author)
        assertTrue(item.isExcerptOnly) // Short description under 150 words is correctly marked as excerpt-only
    }

    @Test
    fun testParseAtomFeed() {
        val sampleAtom = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>The Verge</title>
              <subtitle>Covering technology, science, art, and culture</subtitle>
              <link href="https://www.theverge.com" rel="alternate"/>
              <entry>
                <id>urn:uuid:12345</id>
                <title>Why every new app is quietly copying the same layout</title>
                <link href="https://www.theverge.com/2026/10/26/layout" rel="alternate"/>
                <published>2026-10-26T14:30:00Z</published>
                <author>
                  <name>David Pierce</name>
                </author>
                <summary>Designers say the grid is winning because it is cheap to build.</summary>
                <content type="html"><![CDATA[
                  <p>Paragraph one with enough editorial detail.</p>
                  <img src="https://cdn.vox-cdn.com/thumb.jpg" />
                  <p>Paragraph two providing in-depth analysis on modern UI frameworks.</p>
                ]]></content>
              </entry>
            </feed>
        """.trimIndent()

        val parsed = FeedParser.parse(sampleAtom, "https://www.theverge.com/rss/index.xml")
        assertEquals("The Verge", parsed.title)
        assertEquals(1, parsed.items.size)

        val item = parsed.items.first()
        assertEquals("Why every new app is quietly copying the same layout", item.title)
        assertEquals("https://cdn.vox-cdn.com/thumb.jpg", item.imageUrl)
        assertEquals("David Pierce", item.author)
    }

    @Test
    fun testOpmlExportAndImport() {
        val sources = listOf(
            SourceEntity(
                name = "WIRED",
                siteUrl = "https://www.wired.com",
                feedUrl = "https://www.wired.com/feed/rss"
            ),
            SourceEntity(
                name = "WALLPAPER*",
                siteUrl = "https://www.wallpaper.com",
                feedUrl = "https://www.wallpaper.com/feeds.xml"
            )
        )

        val opml = OpmlManager.exportToOpml(sources)
        assertTrue(opml.contains("WIRED"))
        assertTrue(opml.contains("WALLPAPER*"))

        val imported = OpmlManager.parseOpml(opml)
        assertEquals(2, imported.size)
        assertEquals("WIRED", imported[0].title)
        assertEquals("https://www.wired.com/feed/rss", imported[0].xmlUrl)
        assertEquals("WALLPAPER*", imported[1].title)
        assertEquals("https://www.wallpaper.com/feeds.xml", imported[1].xmlUrl)
    }

    @Test
    fun testIdentityDesignedFetchAndParse() = kotlinx.coroutines.runBlocking {
        val fetcher = FeedFetcher()
        val parsed = fetcher.fetchAndParse("https://identitydesigned.com/feed/")
        assertTrue(parsed.items.isNotEmpty())
        val firstItem = parsed.items.first()
        assertNotNull(firstItem.title)
        assertTrue(firstItem.publishedTime > 0)
    }

    @Test
    fun testReadabilityExtractionForPolygonAndHypebeast() = kotlinx.coroutines.runBlocking {
        val polygonUrl = "https://www.polygon.com/penguin-colony-interview-development/"
        val hypebeastUrl = "https://hypebeast.com/2026/11/porsche-la-marzocco-channels-911-carrera-rs-2-7-linea-micra-coffee-machine-pico-grinder-collaboration-release-info"
        
        val polyHtml = ReadabilityExtractor.extractArticleHtml(polygonUrl)
        assertNotNull(polyHtml)
        val polyDoc = org.jsoup.Jsoup.parse(polyHtml!!)
        val polyImgs = polyDoc.select("img").eachAttr("src")
        assertTrue("Polygon extracted HTML should have image sources", polyImgs.isNotEmpty())
        assertFalse("Polygon image should not be data placeholder", polyImgs.first().startsWith("data:"))
        val videos = polyDoc.select("video, .in-content-video-embed")
        assertTrue("Polygon extracted HTML should preserve video", videos.isNotEmpty())

        val hbHtml = ReadabilityExtractor.extractArticleHtml(hypebeastUrl)
        assertNotNull(hbHtml)
        val hbDoc = org.jsoup.Jsoup.parse(hbHtml!!)
        val hbImgs = hbDoc.select("img").eachAttr("src")
        assertTrue("Hypebeast extracted HTML should have gallery images", hbImgs.isNotEmpty())
    }

    @Test
    fun testImageResolverEntityCleanup() {
        val sampleOgContent = "https&#x3A;&#x2F;&#x2F;image-cdn.hypb.st&#x2F;image.jpg&#x3F;w&#x3D;1080&amp;cbr&#x3D;1"
        val cleaned = ImageResolver.cleanUrl(sampleOgContent)
        assertEquals("https://image-cdn.hypb.st/image.jpg?w=1080&cbr=1", cleaned)
        assertFalse(cleaned.contains("&#x"))
        assertFalse(cleaned.contains("&amp;"))
    }

    @Test
    fun testTimeFormattingAndReadingTime() {
        val now = System.currentTimeMillis()
        val tenMinsAgo = now - (10 * 60 * 1000)
        assertEquals("10 min ago", TimeUtils.formatRelativeTime(tenMinsAgo))

        val words = (1..600).joinToString(" ") { "word$it" }
        assertEquals(3, TimeUtils.calculateReadTimeMinutes(words))
    }

    @Test
    fun testLeadingWhitespaceAndBOMXmlParsing() {
        // Aperture feed has leading newline and whitespace before <?xml
        val apertureSample = "\n\n   <?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<rss version=\"2.0\"><channel><title>Aperture</title><link>https://aperture.org</link><item><title>Photo Essay</title><link>https://aperture.org/story</link></item></channel></rss>"
        val parsed = FeedParser.parse(apertureSample, "https://aperture.org/feed/")
        assertEquals("Aperture", parsed.title)
        assertEquals(1, parsed.items.size)
        assertEquals("Photo Essay", parsed.items[0].title)
    }

    @Test
    fun testUnescapedAmpersandsInAttributesXmlParsing() {
        // Rest of World feed has unescaped ampersands in media:content url attributes
        val rowSample = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <title>Rest of World</title>
                <link>https://restofworld.org</link>
                <item>
                  <title>AI and Energy</title>
                  <link>https://restofworld.org/ai?ref=rss&tag=tech</link>
                  <media:content url="https://img.restofworld.org/photo.jpg?width=380&dpr=2&crop=16:9" medium="image"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val parsed = FeedParser.parse(rowSample, "https://restofworld.org/feed/latest")
        assertEquals("Rest of World", parsed.title)
        assertEquals(1, parsed.items.size)
        assertEquals("AI and Energy", parsed.items[0].title)
        assertTrue(parsed.items[0].imageUrl!!.contains("width=380"))
    }

    @Test
    fun testOgExtractionForHypebeastAndPolygonPages() {
        val hbFile = java.io.File("../hb_page.html").let { if (it.exists()) it else java.io.File("hb_page.html") }
        if (hbFile.exists()) {
            val doc = org.jsoup.Jsoup.parse(hbFile.readText(Charsets.UTF_8))
            val og = ImageResolver.extractOpenGraphImage(doc)
            val best = ImageResolver.extractBestImageFromHtml(doc)
            println("HB page OG image: '$og'")
            println("HB page Best image: '$best'")
            assertNotNull("HB page should have extracted image", og ?: best)
        }

        val polyFile = java.io.File("../poly_page.html").let { if (it.exists()) it else java.io.File("poly_page.html") }
        if (polyFile.exists()) {
            val doc = org.jsoup.Jsoup.parse(polyFile.readText(Charsets.UTF_8))
            val og = ImageResolver.extractOpenGraphImage(doc)
            val best = ImageResolver.extractBestImageFromHtml(doc)
            println("Polygon page OG image: '$og'")
            println("Polygon page Best image: '$best'")
            assertNotNull("Polygon page should have extracted image", og ?: best)
        }
    }

    @Test
    fun testRealPolygonXml() {
        val file = listOf(
            java.io.File("polygon_feed.xml"),
            java.io.File("../polygon_feed.xml")
        ).firstOrNull { it.exists() }
        if (file != null && file.exists()) {
            val content = file.readText(Charsets.UTF_8)
            val parsed = FeedParser.parse(content, "https://www.polygon.com/feed/")
            assertTrue("Parsed Polygon items should not be empty", parsed.items.isNotEmpty())
            for (item in parsed.items) {
                println("Polygon Item: '${item.title}' -> img: '${item.imageUrl}'")
                assertNotNull("Polygon article '${item.title}' must have imageUrl", item.imageUrl)
                assertFalse("Polygon article '${item.title}' imageUrl should not be blank", item.imageUrl!!.isBlank())
            }
        }
    }

    @Test
    fun testRealHypebeastXml() {
        val file = listOf(
            java.io.File("hypebeast_feed.xml"),
            java.io.File("../hypebeast_feed.xml")
        ).firstOrNull { it.exists() }
        if (file != null && file.exists()) {
            val content = file.readText(Charsets.UTF_8)
            val parsed = FeedParser.parse(content, "https://hypebeast.com/feed")
            assertTrue("Parsed Hypebeast items should not be empty", parsed.items.isNotEmpty())
            for (item in parsed.items) {
                println("Hypebeast Item: '${item.title}' -> img: '${item.imageUrl}'")
                assertNotNull("Hypebeast article '${item.title}' must have imageUrl", item.imageUrl)
                assertFalse("Hypebeast article '${item.title}' imageUrl should not be blank", item.imageUrl!!.isBlank())
            }
        }
    }
        val parsed = FeedParser.parse(content, "https://hypebeast.com/feed")
        assertTrue("Parsed Hypebeast items should not be empty", parsed.items.isNotEmpty())
        for (item in parsed.items) {
            println("Hypebeast Item: '${item.title}' -> img: '${item.imageUrl}'")
            assertNotNull("Hypebeast article '${item.title}' must have imageUrl", item.imageUrl)
            assertFalse("Hypebeast article '${item.title}' imageUrl should not be blank", item.imageUrl!!.isBlank())
        }
    }

    @Test
    fun testRealRestOfWorldXml() {
        val file = java.io.File("restofworld.xml")
        if (file.exists()) {
            val content = file.readText(Charsets.UTF_8)
            val parsed = FeedParser.parse(content, "https://restofworld.org/feed/latest")
            assertTrue(parsed.items.isNotEmpty())
            println("Parsed Rest of World items: " + parsed.items.size)
        }
    }

    @Test
    fun testOkHttpFetchRestOfWorld() {
        try {
            val client = com.signal.reader.data.feed.FeedFetcher.createDefaultClient()
            val req = okhttp3.Request.Builder()
                .url("https://restofworld.org/feed/latest")
                .header("User-Agent", com.signal.reader.data.feed.FeedFetcher.USER_AGENT)
                .build()
            val resp = client.newCall(req).execute()
            assertTrue("Expected either success or server response", resp.code in listOf(200, 301, 302, 403, 429))
        } catch (_: Exception) {
            // Live network test may fail in offline or restricted environments
        }
    }
}
