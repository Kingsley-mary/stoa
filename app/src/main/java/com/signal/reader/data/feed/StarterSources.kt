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
        // Architecture
        StarterSource(
            group = "Architecture",
            name = "ArchDaily",
            description = "The world's most visited architecture publication",
            siteUrl = "https://www.archdaily.com",
            feedUrl = "https://www.archdaily.com/rss.xml"
        ),
        StarterSource(
            group = "Architecture",
            name = "Dezeen",
            description = "Architecture, interiors and modern spaces magazine",
            siteUrl = "https://www.dezeen.com",
            feedUrl = "https://www.dezeen.com/feed/"
        ),
        StarterSource(
            group = "Architecture",
            name = "Designboom",
            description = "Architecture, installations and spatial design",
            siteUrl = "https://www.designboom.com",
            feedUrl = "https://www.designboom.com/feed"
        ),
        StarterSource(
            group = "Architecture",
            name = "Failed Architecture",
            description = "Critical architecture, urbanism and spatial politics",
            siteUrl = "https://failedarchitecture.com",
            feedUrl = "https://failedarchitecture.com/feed/"
        ),
        StarterSource(
            group = "Architecture",
            name = "BLDGBLOG",
            description = "Architectural conjecture, landscape and urban futures",
            siteUrl = "https://www.bldgblog.com",
            feedUrl = "https://www.bldgblog.com/feed/"
        ),

        // Product & Industrial Design
        StarterSource(
            group = "Product Design",
            name = "Yanko Design",
            description = "Industrial design, concept ideas and modern products",
            siteUrl = "https://www.yankodesign.com",
            feedUrl = "https://www.yankodesign.com/feed/"
        ),
        StarterSource(
            group = "Product Design",
            name = "UX Collective",
            description = "User experience, product thinking and design craft",
            siteUrl = "https://uxdesign.cc",
            feedUrl = "https://uxdesign.cc/feed"
        ),
        StarterSource(
            group = "Product Design",
            name = "Fast Company Design",
            description = "How design impacts products, business and society",
            siteUrl = "https://www.fastcompany.com/section/design",
            feedUrl = "https://www.fastcompany.com/section/design/rss"
        ),
        StarterSource(
            group = "Product Design",
            name = "Sidebar",
            description = "The 5 best design links curated every day",
            siteUrl = "https://sidebar.io",
            feedUrl = "https://sidebar.io/feed.xml"
        ),
        StarterSource(
            group = "Product Design",
            name = "Design Milk",
            description = "Modern furniture, industrial design and decor",
            siteUrl = "https://design-milk.com",
            feedUrl = "https://design-milk.com/feed/"
        ),
        StarterSource(
            group = "Product Design",
            name = "Sight Unseen",
            description = "Contemporary furniture, objects and studio craft",
            siteUrl = "https://www.sightunseen.com",
            feedUrl = "https://www.sightunseen.com/feed/"
        ),
        StarterSource(
            group = "Product Design",
            name = "swissmiss",
            description = "Visual design curation, tools and inspiration",
            siteUrl = "https://www.swiss-miss.com",
            feedUrl = "https://feeds2.feedburner.com/Swissmiss"
        ),
        StarterSource(
            group = "Product Design",
            name = "Abduzeedo",
            description = "Design, 3D art, typography and visual inspiration",
            siteUrl = "https://abduzeedo.com",
            feedUrl = "https://abduzeedo.com/rss.xml"
        ),

        // Visual Art & Culture
        StarterSource(
            group = "Visual Art & Design",
            name = "WALLPAPER*",
            description = "Design, architecture, interiors and global style",
            siteUrl = "https://www.wallpaper.com",
            feedUrl = "https://www.wallpaper.com/feeds.xml"
        ),
        StarterSource(
            group = "Visual Art & Design",
            name = "Booooooom",
            description = "Contemporary art, emerging artists and creative culture",
            siteUrl = "https://www.booooooom.com",
            feedUrl = "https://www.booooooom.com/feed/"
        ),
        StarterSource(
            group = "Visual Art & Design",
            name = "Colossal",
            description = "Visual art, sculpture, photography and craft",
            siteUrl = "https://www.thisiscolossal.com",
            feedUrl = "https://www.thisiscolossal.com/feed/"
        ),
        StarterSource(
            group = "Visual Art & Design",
            name = "Hyperallergic",
            description = "Playful, radical and independent art perspectives",
            siteUrl = "https://hyperallergic.com",
            feedUrl = "https://hyperallergic.com/feed/"
        ),
        StarterSource(
            group = "Visual Art & Design",
            name = "The Art Newspaper",
            description = "Global visual art news, exhibitions and market reports",
            siteUrl = "https://www.theartnewspaper.com",
            feedUrl = "https://www.theartnewspaper.com/rss.xml"
        ),
        StarterSource(
            group = "Visual Art & Design",
            name = "Juxtapoz",
            description = "Underground art, painting, murals and street culture",
            siteUrl = "https://www.juxtapoz.com",
            feedUrl = "https://www.juxtapoz.com/feed/"
        ),
        StarterSource(
            group = "Visual Art & Design",
            name = "Creative Boom",
            description = "Illustration, studio practice and visual arts",
            siteUrl = "https://www.creativeboom.com",
            feedUrl = "https://www.creativeboom.com/feed"
        ),
        StarterSource(
            group = "Visual Art & Design",
            name = "Creative Review",
            description = "Branding, graphic design and visual culture",
            siteUrl = "https://www.creativereview.co.uk",
            feedUrl = "https://www.creativereview.co.uk/feed"
        ),
        StarterSource(
            group = "Visual Art & Design",
            name = "It’s Nice That",
            description = "Art, typography and creative visual inspiration",
            siteUrl = "https://www.itsnicethat.com",
            feedUrl = "https://feeds2.feedburner.com/itsnicethat/SlXC"
        ),
        StarterSource(
            group = "Visual Art & Design",
            name = "Aperture",
            description = "Fine art photography, photo essays and theory",
            siteUrl = "https://aperture.org",
            feedUrl = "https://aperture.org/feed/"
        ),
        StarterSource(
            group = "Visual Art & Design",
            name = "Craig Mod",
            description = "Photography, walking in Japan and book craft",
            siteUrl = "https://craigmod.com",
            feedUrl = "https://craigmod.com/index.xml"
        ),
        StarterSource(
            group = "Visual Art & Design",
            name = "Identity Designed",
            description = "Brand identities, visual systems and design studios",
            siteUrl = "https://identitydesigned.com",
            feedUrl = "https://identitydesigned.com/feed/"
        ),

        // Science & Ideas
        StarterSource(
            group = "Science",
            name = "Quanta Magazine",
            description = "Illuminating mathematics, physics and biology",
            siteUrl = "https://www.quantamagazine.org",
            feedUrl = "https://www.quantamagazine.org/feed/"
        ),
        StarterSource(
            group = "Science",
            name = "Nautilus",
            description = "Science connected to philosophy and culture",
            siteUrl = "https://nautil.us",
            feedUrl = "https://nautil.us/feed/"
        ),
        StarterSource(
            group = "Science",
            name = "Undark Magazine",
            description = "Explores the intersection of science and society",
            siteUrl = "https://undark.org",
            feedUrl = "https://undark.org/feed/"
        ),
        StarterSource(
            group = "Science",
            name = "Ars Technica Science",
            description = "Spaceflight, climate, biology and fundamental physics",
            siteUrl = "https://arstechnica.com/science/",
            feedUrl = "https://feeds.arstechnica.com/arstechnica/science"
        ),
        StarterSource(
            group = "Science",
            name = "Phys.org",
            description = "Daily discoveries in physics, space and materials",
            siteUrl = "https://phys.org",
            feedUrl = "https://phys.org/rss-feed/"
        ),
        StarterSource(
            group = "Science",
            name = "New Scientist",
            description = "International news on science and technology",
            siteUrl = "https://www.newscientist.com",
            feedUrl = "https://www.newscientist.com/feed/home/"
        ),
        StarterSource(
            group = "Science",
            name = "MIT Technology Review",
            description = "Authoritative analysis on emerging technologies",
            siteUrl = "https://www.technologyreview.com",
            feedUrl = "https://www.technologyreview.com/feed"
        ),
        StarterSource(
            group = "Science",
            name = "Aeon",
            description = "Longform essays in science, philosophy and humanity",
            siteUrl = "https://aeon.co",
            feedUrl = "https://aeon.co/feed.rss"
        ),

        // Technology & Culture
        StarterSource(
            group = "Tech & Culture",
            name = "Rest of World",
            description = "Global tech reporting beyond Silicon Valley",
            siteUrl = "https://restofworld.org",
            feedUrl = "https://restofworld.org/feed/latest"
        ),
        StarterSource(
            group = "Tech & Culture",
            name = "WIRED",
            description = "Technology, science, business and culture",
            siteUrl = "https://www.wired.com",
            feedUrl = "https://www.wired.com/feed/rss"
        ),
        StarterSource(
            group = "Tech & Culture",
            name = "The Verge",
            description = "Hardware, apps, digital culture and policy",
            siteUrl = "https://www.theverge.com",
            feedUrl = "https://www.theverge.com/rss/index.xml"
        ),
        StarterSource(
            group = "Tech & Culture",
            name = "Daring Fireball",
            description = "Apple, software craft and media commentary",
            siteUrl = "https://daringfireball.net",
            feedUrl = "https://daringfireball.net/feeds/main"
        ),
        StarterSource(
            group = "Tech & Culture",
            name = "Simon Willison",
            description = "Web architecture, LLMs and open source craft",
            siteUrl = "https://simonwillison.net",
            feedUrl = "https://simonwillison.net/atom/entries/"
        ),
        StarterSource(
            group = "Tech & Culture",
            name = "Hacker News",
            description = "Curated links in computing, science and technology",
            siteUrl = "https://news.ycombinator.com",
            feedUrl = "https://news.ycombinator.com/rss"
        ),
        StarterSource(
            group = "Tech & Culture",
            name = "kottke.org",
            description = "Art, technology, science and visual curiosities",
            siteUrl = "https://kottke.org",
            feedUrl = "https://feeds.kottke.org/main"
        ),
        StarterSource(
            group = "Tech & Culture",
            name = "Literary Hub",
            description = "Contemporary fiction, critique and literary craft",
            siteUrl = "https://lithub.com",
            feedUrl = "https://lithub.com/feed/"
        ),

        // Gaming
        StarterSource(
            group = "Gaming",
            name = "Polygon",
            description = "Video game culture, longform reviews and gaming craft",
            siteUrl = "https://www.polygon.com",
            feedUrl = "https://www.polygon.com/feed/"
        ),
        StarterSource(
            group = "Gaming",
            name = "Eurogamer",
            description = "In-depth reviews, retrospectives and critical reporting",
            siteUrl = "https://www.eurogamer.net",
            feedUrl = "https://www.eurogamer.net/feed"
        ),
        StarterSource(
            group = "Gaming",
            name = "Rock Paper Shotgun",
            description = "PC gaming, indie discoveries and passionate critique",
            siteUrl = "https://www.rockpapershotgun.com",
            feedUrl = "https://www.rockpapershotgun.com/feed"
        ),
        StarterSource(
            group = "Gaming",
            name = "Game Developer",
            description = "Game design, engineering craft and industry post-mortems",
            siteUrl = "https://www.gamedeveloper.com",
            feedUrl = "https://www.gamedeveloper.com/rss.xml"
        ),
        StarterSource(
            group = "Gaming",
            name = "Aftermath",
            description = "Worker-owned independent gaming and internet culture",
            siteUrl = "https://aftermath.site",
            feedUrl = "https://aftermath.site/rss/"
        ),
        StarterSource(
            group = "Gaming",
            name = "Kotaku",
            description = "Video games, industry news and gaming culture",
            siteUrl = "https://kotaku.com",
            feedUrl = "https://kotaku.com/feed"
        ),
        StarterSource(
            group = "Gaming",
            name = "TouchArcade",
            description = "Handheld, mobile and indie game coverage",
            siteUrl = "https://toucharcade.com",
            feedUrl = "https://toucharcade.com/feed/"
        ),

        // Finance & Markets
        StarterSource(
            group = "Finance",
            name = "Business Insider",
            description = "Business news, financial markets, technology and global trends",
            siteUrl = "https://www.businessinsider.com",
            feedUrl = "https://www.businessinsider.com/rss"
        ),
        StarterSource(
            group = "Finance",
            name = "MarketWatch",
            description = "Financial market news, real-time stock updates and economic analysis",
            siteUrl = "https://www.marketwatch.com",
            feedUrl = "https://feeds.content.dowjones.io/public/rss/mw_topstories"
        ),
        StarterSource(
            group = "Finance",
            name = "WSJ Markets",
            description = "Wall Street Journal market reports, stocks, bonds and currencies",
            siteUrl = "https://www.wsj.com",
            feedUrl = "https://feeds.a.dj.com/rss/RSSMarketsMain.xml"
        ),
        StarterSource(
            group = "Finance",
            name = "Forbes",
            description = "Business strategy, entrepreneurs, wealth, finance and investing",
            siteUrl = "https://www.forbes.com",
            feedUrl = "https://www.forbes.com/business/feed/"
        ),
        StarterSource(
            group = "Finance",
            name = "Fortune",
            description = "Fortune 500 business insights, global economics and corporate leaders",
            siteUrl = "https://fortune.com",
            feedUrl = "https://fortune.com/feed/"
        ),

        // Politics & Global Affairs
        StarterSource(
            group = "Politics",
            name = "Politico",
            description = "Political news, elections, legislation and government policy",
            siteUrl = "https://www.politico.com",
            feedUrl = "https://rss.politico.com/politics-news.xml"
        ),
        StarterSource(
            group = "Politics",
            name = "The Hill",
            description = "Congressional politics, policy debates, campaigns and Capitol Hill",
            siteUrl = "https://thehill.com",
            feedUrl = "https://thehill.com/feed/"
        ),
        StarterSource(
            group = "Politics",
            name = "The Atlantic",
            description = "In-depth political essays, cultural commentary, ideas and science",
            siteUrl = "https://www.theatlantic.com",
            feedUrl = "https://www.theatlantic.com/feed/all/"
        ),
        StarterSource(
            group = "Politics",
            name = "NPR Politics",
            description = "National political reporting, elections, Washington and public policy",
            siteUrl = "https://www.npr.org",
            feedUrl = "https://feeds.npr.org/1014/rss.xml"
        ),
        StarterSource(
            group = "Politics",
            name = "Associated Press News",
            description = "Fast, unbiased global and political news coverage from the AP",
            siteUrl = "https://apnews.com",
            feedUrl = "https://news.google.com/rss/headlines/section/topic/POLITICS?hl=en-US&gl=US&ceid=US:en"
        )
    )
}
