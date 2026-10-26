# Build brief: personal RSS reader for Android

## 1. What this is

A native Android app for reading design and tech magazines (WIRED, Creative Boom, WALLPAPER*, Creative Review, Dezeen and similar) from one place, without saving each site as its own app.

It is a personal app with one user. It is not a Feedly clone. The two things that matter most:

- **Image-first, editorial look.** Big type, flat colour, hairlines, tall rounded images. Not a utility list.
- **Tactile motion.** Shared-element transitions, swipe gestures and springy press feedback are core, not polish.

The app name is not decided. "Signal" is a placeholder wordmark.

## 2. Source of truth

Seven HTML mockups sit in `/design`:

| File | Screen |
|---|---|
| `home-mockup.html` | Home, plus a working Reader, Saved and Sources. This is the **interactive prototype** |
| `reader-mockup.html` | Article reader |
| `search-mockup.html` | Search |
| `source-page-mockup.html` | A single source's page |
| `sources-mockup.html` | Sources tab |
| `add-sources-mockup.html` | Add sources, including first-launch mode |
| `settings-mockup.html` | Settings |

Rules for using them:

- **The mockups win on visuals and motion.** Open them in a browser at phone width and match them. The HTML is a reference, not code to port.
- **This brief wins on behaviour and rules.** If a mockup and this document disagree, stop and ask.
- **Remove mockup-only parts.** Any strip at the top of a file labelled "Mockup only" is a state switcher for reviewing, not UI. The placeholder images (flat crops of five illustrations) and made-up headlines are stand-ins for real feed content.
- **Every file has a `MOTION TOKENS` comment** at the top of its style block with the durations and easing. Use those numbers.

## 3. Stack (recommended)

| Need | Use |
|---|---|
| Language and UI | Kotlin, Jetpack Compose, Material 3 as a base but heavily restyled |
| Navigation | Navigation Compose |
| Shared-element transitions | Compose `SharedTransitionLayout` (check the current stable API) |
| Local storage | Room |
| Background refresh | WorkManager |
| Networking | OkHttp |
| Feed parsing | A maintained RSS and Atom parser, or `XmlPullParser` |
| HTML parsing (feed discovery) | Jsoup |
| Images | Coil |
| In-app site viewing | Chrome Custom Tabs (see section 9) |

Pick alternatives if you have good reason. Say what you changed and why.

## 4. Design system

### Colour tokens

| Token | Light | Dark | Use |
|---|---|---|---|
| `bg` | `#EEEAE1` | `#10100F` | Screen background |
| `ink` | `#141413` | `#F1EDE4` | Text, solid pills, indicators |
| `mute` | `#6B665C` | `#9C978B` | Secondary text |
| `line` | ink at 16% | ink at 18% | Hairlines |
| `card` | `#D6CFBF` | `#242320` | Image placeholders, flat cards, swipe underlay |
| `accent` | `#E4472B` | `#E4472B` | **Only** the unread dot and the ↗ button |

**The accent is rare on purpose.** Do not use it for links, selected states, or buttons. Selected and active states use `ink` on `bg`.

Dark mode follows the system by default, with a Settings override (Auto, Light, Dark).

### Type

Font: **Archivo** (variable, has a width axis). Bundle it. Fall back to a neutral grotesque sans.

| Role | Size | Weight | Notes |
|---|---|---|---|
| Hero headline | 34 | 800 | Uppercase, width 112%, tight leading (about 0.98), −2% tracking, max 4 lines |
| Screen title ("Sources", "Settings") | 40 | 800 | Same style as hero |
| Reader title | 32 | 800 | Same style |
| Wordmark, initials squares | 16 / 15 | 800 | Width 125% |
| Row headline | 18 | 700 | Sentence case, leading 1.14 |
| Reader body | 18 (16, 18, 20, 23 steps) | 400 | Leading 1.6 |
| Meta (source, time) | 12 | 500 for source, 400 for time | `mute` colour |
| Labels, pills, tab labels | 13–14 | 500 | Sentence case |

**Never use all-caps for small labels.** Uppercase is for big headlines only.

### Shape and layout

- Side gutters are 20px throughout.
- Hero image: 4:5, corner radius 26. Reader image: 1:1, radius 26. Row thumbnail: 88×88, radius 14. Initials square: 48×48 (56 on the Source page), radius 14–16.
- Pills are fully rounded. Outlined pills have a 1px `ink` border. Solid pills are filled `ink` with `bg` text.
- Hairlines are 1px `line`. Rows are separated by a hairline on top, never by cards or shadows.
- No shadows. No gradients except the 110px fade on excerpt-only articles.

## 5. Screens and flow

```
First launch ──► Add sources ──► Home
                                 │
Bottom tabs:   Home │ Saved │ Sources
Header:        Menu (left) · wordmark · Search (right)

Home ──► Article reader ──► Full web page
Home ──► Search ──► Article reader
Saved ──► Article reader
Sources ──► Source page ──► Article reader
Sources ──► Add sources
Article reader ◄──► Source page   (via the Source field)
Menu ──► Settings
```

- **Tab bar** shows on Home, Saved, Sources and the Source page (Sources stays active there). It is hidden on Search, Reader, Add sources and Settings.
- **Entry points to a Source page:** a row in Sources, the source name on a card, and the Source field in the Reader.
- **Back-stack rule:** opening a Source page from the Reader's Source field must return to the existing Source page if one is already in the stack, not push a new one. Otherwise Reader → Source → Reader → Source builds an endless back stack.
- **Menu icon** opens a short list: *Mark all as read* and *Settings*. This list is not drawn in the mockups. Build it as a simple two-row menu in the same style.

## 6. Screen specs

### Home
- **Header:** menu icon, wordmark, search icon, hairline below.
- **Chips:** a horizontal scrolling row: *All*, then each followed source. Chip order matches the Sources tab order. The active chip is a solid `ink` pill that **slides** between chips; the others are outlined.
- **Hero:** one large 4:5 image card with the headline underneath, source and time in `meta`, and a ↗ button (accent) on the image corner.
- **Stream:** rows under the hero. Each row has source and time, a headline, and an 88px thumbnail on the right. Rows with no image drop the thumbnail and use the full width. Read rows are dimmed to about 45%.
- **Unread dot:** a 7px accent dot before the source name. Saved articles show the word "Saved" after the time.
- **Hero rule:** the hero is the **newest article that has an image** within the active chip's set. If none has an image, the newest article becomes a **flat type card**: a square `card`-coloured block with the headline in big type. Re-evaluate the rule every time the chip changes.
- **Swipe to save:** swiping a row right reveals a Save underlay (see section 8).
- **Pull to refresh:** see section 8.
- **Empty state (no sources):** same prompt as Sources' empty state, pointing to Add sources.

### Article reader
- **Header:** back chevron only, plus a 2px reading-progress line along the top edge. The header hides on scroll down and returns on scroll up; the progress line stays.
- **Content:** square rounded image with a category pill in the top-left, then the title, then a label/value block (Source, Author, Published, Read time), then the body with an optional sub-heading.
- **Source** is underlined and opens that Source page.
- **Bottom bar:** *Save* (bookmark icon fills when saved), *Aa* (opens a small panel: A−, A+, a Dark toggle), and *Open site ↗*.
- **Excerpt-only state:** when the feed only carries an excerpt, show the first two paragraphs, a 110px fade to the background, a solid *Read full article ↗* pill, and the note "This site only shares an excerpt in its feed. The full article opens inside the app."
- **Full-text state:** show everything.
- **Opening an article marks it read.**
- **Articles without an image:** show a flat `card`-coloured square in place of the image.

### Search
- **Header:** back chevron, a large bold input (22px, weight 700), and a clear button once there is text. Keyboard opens immediately.
- **Before typing:** "Recent searches" with an × on each row and a "Clear all" link. Keep the last 6. Tapping one runs it.
- **Results:** a count line ("3 results for “bear”"), then the same rows as Home plus a two-line excerpt. Matched words are **underlined**, not coloured.
- **Matching:** case-insensitive, over title, excerpt and source name. **Every word must match.**
- **No results:** "No articles match “…”", then "Search covers recent articles from your sources. Try a shorter word or check the spelling."
- **Enter** saves the query to recents.

### Sources tab
- **Title row:** "Sources", a count line, and a solid **Add +** pill that opens Add sources.
- **Rows:** initials square, name, "Latest 12 min ago", and the unread count with the accent dot on the right. Order by most recent article. Tapping opens that Source page.
- **Muted source:** the row dims, says "Muted", shows no count.
- **Broken feed:** the sub-line reads "Feed isn’t loading. Tap to check." with a circled **!** on the right. The destination of that tap is **not designed**; see section 12.
- **Empty state:** "No sources yet", "Add a few sites and their articles will show up on Home.", and an **Add sources** pill.
- **No remove control here.** Unfollowing lives on the Source page.

### Source page
- **Header:** back chevron only. The Sources tab stays active.
- **Masthead:** initials square (stand-in for the site's icon), the name in big uppercase, and a short description.
- **Controls:** **Following** and **Mute** pills.
  - Tapping Following opens an inline confirm: "Unfollow *Name*? Articles you saved stay in Saved." with *Unfollow* and *Cancel*. Confirming turns the pill into a solid **Follow** and shows "You’re not following *Name*. New articles won’t appear on Home."
  - Mute turns the pill solid and shows "Muted. New articles from this source won’t appear on Home."
- **Facts block:** In this feed (count), Unread, Latest, Website (a link to the site).
- **Articles:** the same hero and rows as Home, using the same hero rule, with the source name left out because the page already says it.
- **List end:** "That’s everything in this feed. Older articles aren’t available."

### Add sources
- **Paste box:** a rounded field with a **Find feed** button. States:
  - Idle.
  - "Looking for a feed…"
  - Found: a card with the name, "Feed found at *host*", and an **Add** pill.
  - Already followed: "*Name* is already in your list."
  - Not found: "Couldn’t find a feed on *host*. Try the site’s homepage address, or paste the feed link itself if you have it."
- **Starter list:** two groups, *Design* and *Tech and culture*, each row with initials, name, a one-line description and an **Add** pill that becomes a solid **Added**. Sources already followed show "Following" instead of a button. Sources added from a paste appear in an "Added from a link" group.
- **Bottom button:** "Done" with nothing picked, "Add *N* and finish" otherwise.
- **First-launch mode:** title "Pick what you read", the subtitle "Choose a few sites to start. You can change this any time in Sources.", a **Skip** link in the header instead of a back chevron (Skip goes to the empty Home), and a bottom button that is disabled and reads "Pick at least one" until something is chosen, then "Continue with *N*".

### Settings
Opens from the menu as a full screen with no tab bar.

| Section | Rows |
|---|---|
| Appearance | Theme (Auto, Light, Dark). Text size (A−, A+, with a live preview line). |
| Home | Layout (Cards, Compact) |
| Refresh | Check in the background (switch). How often (30 min, 1 hr, 3 hr). Wi-Fi only (switch). Note: "These are targets. Android decides the exact timing to save battery, so a check can run later than you set." |
| Your sources | Import sources (OPML). Export sources (OPML). |
| About | Version |

How often and Wi-Fi only grey out when background checking is off.

### Full web page
Not mocked. It is the original article opened inside the app with a back button. See section 9.

## 7. Behaviour and data rules

### Data model (sketch)

- **Source:** id, name, site URL, feed URL, description, followed, muted, last fetched, error state.
- **Article:** id, source id, GUID, title, excerpt, full content (if the feed has it), image URL, author, published time, read, saved, saved time.

### Feeds

- **Parse** RSS 2.0 and Atom.
- **Image, in this order:** media content or thumbnail, an image enclosure, the first image in the content or description, then the page's `og:image` as a last resort.
- **Full text vs excerpt:** treat an article as full text only if the feed content is clearly article-length. Otherwise it is an excerpt-only article.
- **Read time:** words ÷ 200, rounded up.
- **A feed holds only recent articles**, typically 10 to 50. Store what the feed gives. Do not promise or build an archive.

### Feed discovery (paste a link)
1. Normalise the input (add `https://` if missing).
2. If the URL returns XML that parses as RSS or Atom, use it directly.
3. Otherwise load the page and read `<link rel="alternate" type="application/rss+xml">` and `application/atom+xml` tags. Resolve relative URLs.
4. If none, try common paths: `/feed`, `/rss`, `/feed.xml`, `/atom.xml`, `/rss.xml`.
5. Otherwise show the "Couldn’t find a feed" state.

### Read, saved, muted, followed
- **Unread** is the default for new articles. Opening an article marks it read. *Mark all as read* in the menu applies to the current view.
- **Saved articles are kept in full**, even if they drop out of the feed or the source is unfollowed.
- **Unfollowing** removes the source and its unsaved articles. Saved articles stay.
- **Muted** sources keep updating but are excluded from Home and its chips.
- **Broken feed:** mark a source as broken after repeated fetch or parse failures, and show the Sources row state above. Clear it on the next success.

### Paywalls
WIRED uses a metered paywall. Its feed will often give only excerpts, and opening articles counts toward its monthly free limit. Do not promise full-text extraction. Excerpt-only is a normal state, not an error.

### Refresh
- Fetch in the background with WorkManager, honouring the Settings interval and Wi-Fi-only switch. Periodic WorkManager work has a **15-minute minimum** and is not exact.
- Fetch on app open if the last fetch is stale, and on pull to refresh.

## 8. Motion

Everything here is already coded in the mockups. Match it.

### Global rules
- Most motion is **150–250ms**. Content entrances are 350ms. The shared-element move is 500ms.
- Easing: ease-out `cubic-bezier(.2,.8,.2,1)` for movement. Spring `cubic-bezier(.34,1.56,.64,1)` for press feedback, pops and switches.
- **Everything must be interruptible.** Back mid-transition must reverse from the current position, not jump.
- **Respect the system "Remove animations" setting.** All motion off, instant state changes.
- **Haptics** fire only on actions that change something: reaching the swipe threshold, saving, following, muting, toggling a switch, and a successful pull-to-refresh. Use the platform's proper haptic API.

### The big three

| Motion | Spec |
|---|---|
| **Card to Reader** | The tapped image grows into the Reader image. The image stays cover-cropped while its box changes shape, so the **crop animates from the card's 4:5 (or the thumbnail's square) to the Reader's 1:1**. Do not match the ratios to avoid it. Corner radius animates too. The title rises and fades in from 40px below; details and body stagger in after. Back reverses the image move, then the row dims and its unread dot shrinks away. If the source card is scrolled off-screen, or the Reader is scrolled, just fade. Articles with no image fade in without the image move. |
| **Swipe to save** | The row follows the finger to the right and resists past 110px. At **90px** the underlay flips to solid `ink`, the bookmark fills with a pop, and a haptic ticks once. Dropping back under 90px reverses it. On release the row springs back (350ms) and the save toggles; if saved, "Saved" appears in the row's meta line. Swiping a saved row shows "Unsave". Horizontal drags must not trigger the tap, and vertical scrolling must not trigger the swipe. |
| **Chip filtering** | The solid `ink` pill slides to the new chip (300ms). The feed fades out and down (130ms), re-renders, then the rows rise in with a 40ms stagger and the new hero slides in from the right (450ms). |

### Smaller touches

| Where | Motion |
|---|---|
| All buttons, pills, chips | Scale to 96% on press, spring back |
| Cards | Image shrinks to 97% on press; the ↗ nudges up and right; row thumbnail shrinks to 94% |
| Lists, everywhere | Rows rise 14px and fade in, 40ms stagger, capped at the first 11 items |
| Pull to refresh | The header line stretches as you pull, haptic at 70px, then pulses while loading. New articles drop in from above, 40ms apart, and a small "*N* new articles" pill slides down, then away. If nothing is new: "You’re up to date" |
| Unread dot | Shrinks to nothing and collapses its space when the article is read |
| Reader header | Hides on scroll down (after 90px), returns on scroll up |
| Aa panel | Springs up from below |
| Save bookmark | Fills with a bounce |
| Tabs | The black indicator slides between tabs (300ms); the content fades out and in |
| Add / Added | The pill turns solid and pops, with a check mark |
| Found-feed card, confirm box, notes | Unfold downward with a slight scale |
| Switches | The knob slides with a spring |
| Segmented controls | A sliding ink pill behind the options |
| First load only | The hero headline rises in word by word, 35ms apart. Never replay on scroll or return |
| Search | Results stagger in only when the view changes (recent, results, none), **not on every keystroke**. A removed recent search collapses before it is deleted |

## 9. Decisions made and one to confirm

- **No folders. No discovery page. No social features.** Deliberately out.
- **No scope chips in Search.** Typing a source name already filters.
- **Home chips replace a per-source filter page,** but a Source page also exists for per-source controls.
- **To confirm: in-app site viewing.** *Open site* and *Read full article* should open the page inside the app. I recommend **Chrome Custom Tabs**, because they use the user's signed-in Chrome session, so a WIRED subscriber stays signed in. A plain WebView would not. Use Custom Tabs unless there is a reason not to.

## 10. Starter sources

Suggested names only. **Feed URLs are not included and have not been verified.** For each one, find the real feed address, confirm it loads and parses, and keep only ones that work. A wrong address becomes a broken source on day one.

**Design:** Creative Boom, Creative Review, WALLPAPER*, Dezeen, It’s Nice That, Designboom
**Tech and culture:** WIRED, The Verge, Ars Technica, Fast Company

Descriptions are in `add-sources-mockup.html`.

## 11. Later (not in v1)

Per-source notifications (would sit beside Mute), muted keywords, offline reading for saved articles, a home-screen widget, a share button next to Save. Do not build these, but do not make them hard to add.

## 12. Known gaps. Ask before inventing

- **Broken-feed destination.** Tapping a broken source's row ("Tap to check") is not designed. Likely the Source page with an error banner and a Retry button.
- **Full web page** has no custom UI.
- **The header menu list** is described but not drawn.
- **Compact layout** on Home (a Settings option) is not drawn. Treat it as rows only, with no hero, in the same style.
- **Icons.** The initials squares stand in for site icons. Decide whether to use real favicons with the initials as fallback.
- **App name and icon** are not decided.

## 13. Suggested build order

1. Data layer: Room schema, feed fetching and parsing, feed discovery. Test against the real starter feeds.
2. Home and Reader with real data, without motion.
3. Sources, Source page, Add sources, Search, Saved, Settings.
4. Motion: the big three first, then the smaller touches.
5. Background refresh, read and saved persistence, empty and error states.

## 14. Done means

- Each screen matches its mockup in light and dark.
- All three big motions work and can be interrupted mid-way.
- With "Remove animations" on, nothing moves.
- Adding a source by pasting a homepage address works for at least five different sites.
- An excerpt-only article, a no-image article, a broken feed, an empty Home and a zero-results search all show their designed states.
