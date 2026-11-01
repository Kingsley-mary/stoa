package com.signal.reader.data.catalog

import android.content.Context
import org.json.JSONArray
import java.io.InputStreamReader

object CatalogRepository {
    private var cachedSources: List<CatalogSource>? = null

    val ALL_TOPICS: List<String> = listOf(
        "Design", "Architecture", "Typography", "Illustration", "Photography",
        "Fashion", "Interiors", "Art", "Branding", "Tech", "AI", "Gadgets",
        "Games", "Film", "Music", "Science", "Culture", "Business", "Finance", "Politics"
    )

    fun getSources(context: Context): List<CatalogSource> {
        cachedSources?.let { return it }

        return try {
            val jsonString = context.assets.open("catalog.json").use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).readText()
            }
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<CatalogSource>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val topicsArray = obj.getJSONArray("topics")
                val topics = mutableListOf<String>()
                for (j in 0 until topicsArray.length()) {
                    topics.add(topicsArray.getString(j))
                }
                list.add(
                    CatalogSource(
                        name = obj.getString("name"),
                        description = obj.getString("description"),
                        siteUrl = obj.getString("siteUrl"),
                        feedUrl = obj.getString("feedUrl"),
                        topics = topics
                    )
                )
            }
            cachedSources = list
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Filters available topics to only those with at least 3 verified working sources.
     * Dropped topics are automatically excluded from the chip wall.
     */
    fun getAvailableTopics(context: Context): List<String> {
        val sources = getSources(context)
        return ALL_TOPICS.filter { topic ->
            sources.count { it.topics.contains(topic) } >= 3
        }
    }

    /**
     * Suggestions algorithm matching interests-mockup.html:
     * Pre-add highest-scoring source for each picked topic not already covered, capped at 8.
     * Show under "Suggested for you", then up to 6 under "More you might like".
     */
    fun computeSuggestions(
        pickedTopics: Set<String>,
        sources: List<CatalogSource>
    ): Pair<List<CatalogSource>, List<CatalogSource>> {
        if (pickedTopics.isEmpty()) return Pair(emptyList(), emptyList())

        fun score(c: CatalogSource): Int = c.topics.count { pickedTopics.contains(it) }

        val pool = sources.filter { score(it) > 0 }.sortedByDescending { score(it) }
        val pre = mutableListOf<CatalogSource>()

        for (topic in pickedTopics) {
            if (pre.any { it.topics.contains(topic) }) continue
            val candidate = pool.find { it.topics.contains(topic) && !pre.contains(it) }
            if (candidate != null) {
                pre.add(candidate)
            }
        }

        val suggested = pre.sortedByDescending { score(it) }.take(8)
        val more = pool.filter { !suggested.contains(it) }.take(6)

        return Pair(suggested, more)
    }

    fun getWhyText(source: CatalogSource, pickedTopics: Set<String>): String {
        val matches = source.topics.filter { pickedTopics.contains(it) }
        if (matches.isEmpty()) return source.description
        val firstTwo = matches.take(2).joinToString(", ")
        val extra = if (matches.size > 2) " and ${matches.size - 2} more" else ""
        return "Because you picked $firstTwo$extra"
    }
}
