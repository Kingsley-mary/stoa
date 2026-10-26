package com.signal.reader.data.opml

import com.signal.reader.data.local.entity.SourceEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import java.io.StringWriter
import java.util.Locale

data class OpmlOutline(
    val title: String,
    val text: String,
    val xmlUrl: String,
    val htmlUrl: String?
)

object OpmlManager {

    fun exportToOpml(sources: List<SourceEntity>): String {
        val writer = StringWriter()
        writer.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        writer.appendLine("""<opml version="2.0">""")
        writer.appendLine("""  <head>""")
        writer.appendLine("""    <title>Signal RSS Subscriptions</title>""")
        writer.appendLine("""    <dateCreated>${java.util.Date()}</dateCreated>""")
        writer.appendLine("""  </head>""")
        writer.appendLine("""  <body>""")

        for (source in sources) {
            val titleEscaped = escapeXml(source.name)
            val xmlUrlEscaped = escapeXml(source.feedUrl)
            val htmlUrlEscaped = escapeXml(source.siteUrl)
            writer.appendLine("""    <outline text="$titleEscaped" title="$titleEscaped" type="rss" xmlUrl="$xmlUrlEscaped" htmlUrl="$htmlUrlEscaped"/>""")
        }

        writer.appendLine("""  </body>""")
        writer.appendLine("""</opml>""")
        return writer.toString()
    }

    fun parseOpml(inputStream: InputStream): List<OpmlOutline> {
        val content = inputStream.bufferedReader().use { it.readText() }
        return parseOpml(content)
    }

    fun parseOpml(xmlContent: String): List<OpmlOutline> {
        val outlines = mutableListOf<OpmlOutline>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlContent))

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("outline", ignoreCase = true)) {
                val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                    ?: parser.getAttributeValue(null, "xmlurl")
                    ?: ""
                if (xmlUrl.isNotBlank()) {
                    val text = parser.getAttributeValue(null, "text") ?: ""
                    val title = parser.getAttributeValue(null, "title") ?: text
                    val htmlUrl = parser.getAttributeValue(null, "htmlUrl")
                        ?: parser.getAttributeValue(null, "htmlurl")

                    outlines.add(
                        OpmlOutline(
                            title = title.ifBlank { "Untitled Source" },
                            text = text.ifBlank { title },
                            xmlUrl = xmlUrl.trim(),
                            htmlUrl = htmlUrl?.trim()
                        )
                    )
                }
            }
            eventType = parser.next()
        }
        return outlines
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
