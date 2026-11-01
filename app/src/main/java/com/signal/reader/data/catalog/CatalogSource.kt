package com.signal.reader.data.catalog

data class CatalogSource(
    val name: String,
    val description: String,
    val siteUrl: String,
    val feedUrl: String,
    val topics: List<String>
) {
    val initials: String
        get() {
            if (name.endsWith("*")) return name[0] + "*"
            val words = name.replace("'", "").replace("’", "").split(Regex("""\s+"""))
                .filter { it.isNotBlank() && !listOf("on", "of", "the", "and").contains(it.lowercase()) }
            return if (words.size > 1) {
                "${words[0][0]}${words[1][0]}".uppercase()
            } else {
                name.take(2).uppercase()
            }
        }
}
