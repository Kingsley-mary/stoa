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
        // Design & Architecture
        StarterSource(
            group = "Design & Architecture",
            name = "WALLPAPER*",
            description = "Design, interiors, fashion and architecture",
            siteUrl = "https://www.wallpaper.com",
            feedUrl = "https://www.wallpaper.com/feeds.xml"
        ),
        StarterSource(
            group = "Design & Architecture",
            name = "Dezeen",
            description = "Architecture, interiors and design magazine",
            siteUrl = "https://www.dezeen.com",
            feedUrl = "https://www.dezeen.com/feed/"
        ),
        StarterSource(
            group = "Design & Architecture",
            name = "Design Milk",
            description = "Modern design, interiors, architecture and art",
            siteUrl = "https://design-milk.com",
            feedUrl = "https://design-milk.com/feed/"
        ),
        StarterSource(
            group = "Design & Architecture",
            name = "Sight Unseen",
            description = "Contemporary furniture, visual arts and ideas",
            siteUrl = "https://www.sightunseen.com",
            feedUrl = "https://www.sightunseen.com/feed/"
        ),
        StarterSource(
            group = "Design & Architecture",
            name = "ArchDaily",
            description = "The world's most visited architecture publication",
            siteUrl = "https://www.archdaily.com",
            feedUrl = "https://www.archdaily.com/rss.xml"
        ),
        StarterSource(
            group = "Design & Architecture",
            name = "Designboom",
            description = "Architecture, industrial design and installations",
            siteUrl = "https://www.designboom.com",
            feedUrl = "https://www.designboom.com/feed"
        ),
        StarterSource(
            group = "Design & Architecture",
            name = "Creative Boom",
            description = "Illustration, visual arts and studio practice",
            siteUrl = "https://www.creativeboom.com",
            feedUrl = "https://www.creativeboom.com/feed"
        ),
        StarterSource(
            group = "Design & Architecture",
            name = "Creative Review",
            description = "Branding, graphic design and visual culture",
            siteUrl = "https://www.creativereview.co.uk",
            feedUrl = "https://www.creativereview.co.uk/feed"
        ),
        StarterSource(
            group = "Design & Architecture",
            name = "It’s Nice That",
            description = "Art, typography and creative visual culture",
            siteUrl = "https://www.itsnicethat.com",
            feedUrl = "https://feeds2.feedburner.com/itsnicethat/SlXC"
        ),
        StarterSource(
            group = "Design & Architecture",
            name = "swissmiss",
            description = "Design curation and visual inspiration",
            siteUrl = "https://www.swiss-miss.com",
            feedUrl = "https://www.swiss-miss.com/feed"
        ),

        // Art & Photography
        StarterSource(
            group = "Art & Photography",
            name = "Colossal",
            description = "Visual art, sculpture, photography and craft",
            siteUrl = "https://www.thisiscolossal.com",
            feedUrl = "https://www.thisiscolossal.com/feed/"
        ),
        StarterSource(
            group = "Art & Photography",
            name = "Aperture",
            description = "Fine art photography, photo essays and theory",
            siteUrl = "https://aperture.org",
            feedUrl = "https://aperture.org/feed/"
        ),
        StarterSource(
            group = "Art & Photography",
            name = "Craig Mod",
            description = "Photography, walking in Japan and book craft",
            siteUrl = "https://craigmod.com",
            feedUrl = "https://craigmod.com/index.xml"
        ),

        // Tech & Indie Web
        StarterSource(
            group = "Tech & Indie Web",
            name = "WIRED",
            description = "Technology, science and culture",
            siteUrl = "https://www.wired.com",
            feedUrl = "https://www.wired.com/feed/rss"
        ),
        StarterSource(
            group = "Tech & Indie Web",
            name = "The Verge",
            description = "Hardware, apps, digital culture and policy",
            siteUrl = "https://www.theverge.com",
            feedUrl = "https://www.theverge.com/rss/index.xml"
        ),
        StarterSource(
            group = "Tech & Indie Web",
            name = "Ars Technica",
            description = "Technical deep dives, policy and analysis",
            siteUrl = "https://arstechnica.com",
            feedUrl = "https://feeds.arstechnica.com/arstechnica/index"
        ),
        StarterSource(
            group = "Tech & Indie Web",
            name = "Daring Fireball",
            description = "Apple, software craft and media commentary",
            siteUrl = "https://daringfireball.net",
            feedUrl = "https://daringfireball.net/feeds/main"
        ),
        StarterSource(
            group = "Tech & Indie Web",
            name = "Rest of World",
            description = "Global tech reporting beyond Silicon Valley",
            siteUrl = "https://restofworld.org",
            feedUrl = "https://restofworld.org/feed/latest"
        ),
        StarterSource(
            group = "Tech & Indie Web",
            name = "Simon Willison",
            description = "Web architecture, LLMs and open source",
            siteUrl = "https://simonwillison.net",
            feedUrl = "https://simonwillison.net/atom/entries/"
        ),
        StarterSource(
            group = "Tech & Indie Web",
            name = "Hacker News",
            description = "Curated links in computing and technology",
            siteUrl = "https://news.ycombinator.com",
            feedUrl = "https://news.ycombinator.com/rss"
        ),
        StarterSource(
            group = "Tech & Indie Web",
            name = "Fast Company",
            description = "Innovation, design and modern leadership",
            siteUrl = "https://www.fastcompany.com",
            feedUrl = "https://www.fastcompany.com/rss"
        ),

        // Ideas & Science
        StarterSource(
            group = "Ideas & Science",
            name = "Quanta Magazine",
            description = "Mathematics, physics and biological research",
            siteUrl = "https://www.quantamagazine.org",
            feedUrl = "https://www.quantamagazine.org/feed/"
        ),
        StarterSource(
            group = "Ideas & Science",
            name = "Nautilus",
            description = "Science connected to philosophy and culture",
            siteUrl = "https://nautil.us",
            feedUrl = "https://nautil.us/feed/"
        ),
        StarterSource(
            group = "Ideas & Science",
            name = "Aeon",
            description = "Longform essays in philosophy and society",
            siteUrl = "https://aeon.co",
            feedUrl = "https://aeon.co/feed.rss"
        ),
        StarterSource(
            group = "Ideas & Science",
            name = "MIT Technology Review",
            description = "Authoritative analysis on emerging tech",
            siteUrl = "https://www.technologyreview.com",
            feedUrl = "https://www.technologyreview.com/feed"
        ),
        StarterSource(
            group = "Ideas & Science",
            name = "Literary Hub",
            description = "Contemporary fiction, critique and craft",
            siteUrl = "https://lithub.com",
            feedUrl = "https://lithub.com/feed/"
        ),
        StarterSource(
            group = "Ideas & Science",
            name = "kottke.org",
            description = "Art, technology, science and visual curiosities",
            siteUrl = "https://kottke.org",
            feedUrl = "https://feeds.kottke.org/main"
        )
    )
}
