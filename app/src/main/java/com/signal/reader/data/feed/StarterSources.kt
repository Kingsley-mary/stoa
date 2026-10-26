package com.signal.reader.data.feed

data class StarterSource(
    val group: String,
    val name: String,
    val description: String,
    val siteUrl: String,
    val feedUrl: String
)

object StarterSources {
    val ALL: List<StarterSource> = listOf(
        // Design group
        StarterSource(
            group = "Design",
            name = "Creative Boom",
            description = "Illustration, design and photography",
            siteUrl = "https://www.creativeboom.com",
            feedUrl = "https://www.creativeboom.com/feed"
        ),
        StarterSource(
            group = "Design",
            name = "Creative Review",
            description = "Branding, advertising and visual culture",
            siteUrl = "https://www.creativereview.co.uk",
            feedUrl = "https://www.creativereview.co.uk/feed"
        ),
        StarterSource(
            group = "Design",
            name = "WALLPAPER*",
            description = "Design, interiors, fashion and travel",
            siteUrl = "https://www.wallpaper.com",
            feedUrl = "https://www.wallpaper.com/feeds.xml"
        ),
        StarterSource(
            group = "Design",
            name = "Dezeen",
            description = "Architecture and design news",
            siteUrl = "https://www.dezeen.com",
            feedUrl = "https://www.dezeen.com/feed/"
        ),
        StarterSource(
            group = "Design",
            name = "It’s Nice That",
            description = "Art, graphics and creative work",
            siteUrl = "https://www.itsnicethat.com",
            feedUrl = "https://feeds2.feedburner.com/itsnicethat/SlXC"
        ),
        StarterSource(
            group = "Design",
            name = "Designboom",
            description = "Design, art and architecture",
            siteUrl = "https://www.designboom.com",
            feedUrl = "https://www.designboom.com/feed"
        ),

        // Tech and culture group
        StarterSource(
            group = "Tech and culture",
            name = "WIRED",
            description = "Technology, science and culture",
            siteUrl = "https://www.wired.com",
            feedUrl = "https://www.wired.com/feed/rss"
        ),
        StarterSource(
            group = "Tech and culture",
            name = "The Verge",
            description = "Gadgets, apps and the internet",
            siteUrl = "https://www.theverge.com",
            feedUrl = "https://www.theverge.com/rss/index.xml"
        ),
        StarterSource(
            group = "Tech and culture",
            name = "Ars Technica",
            description = "In-depth technology reporting",
            siteUrl = "https://arstechnica.com",
            feedUrl = "https://feeds.arstechnica.com/arstechnica/index"
        ),
        StarterSource(
            group = "Tech and culture",
            name = "Fast Company",
            description = "Business, design and innovation",
            siteUrl = "https://www.fastcompany.com",
            feedUrl = "https://www.fastcompany.com/rss"
        )
    )
}
