package com.signal.reader

import com.signal.reader.data.feed.FeedParser
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
        val client = com.signal.reader.data.feed.FeedFetcher.createDefaultClient()
        val req = okhttp3.Request.Builder()
            .url("https://restofworld.org/feed/latest")
            .header("User-Agent", com.signal.reader.data.feed.FeedFetcher.USER_AGENT)
            .build()
        val resp = client.newCall(req).execute()
        assertEquals(200, resp.code)
    }
}
