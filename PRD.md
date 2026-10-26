# PRD: Personal RSS reader for Android

**Owner:** Kingsley
**Status:** Draft v1
**Companion:** `ANTIGRAVITY_BRIEF.md` covers design, flows, motion and stack. This document covers why, what, and how we know it works.

---

## 1. Problem

Kingsley reads several design and tech publications (WIRED, Creative Boom, WALLPAPER*, Creative Review and more). Today that means visiting each site, or saving each one to the phone as its own app. He wants one place to read all of them.

An RSS reader solves this, and existing ones were tried and rejected:

- **Feedly** does exactly the job, but its UI feels off and its folder model is an extra step he doesn't want.
- **Feeder, Inoreader, FocusReader and Read You** were considered. None of them has a UI he likes.

The gap is not functionality. It is that no existing reader looks and feels like the publications he is reading.

## 2. Goal

Build a personal Android RSS reader that:

1. Puts every source he follows into **one stream**, with no per-site apps and no folders.
2. Looks and moves like an **editorial magazine**: image-first, big type, tactile motion.
3. Is simple enough that adding a source takes seconds.

### Success criteria

This is a one-person app, so success is concrete and checkable rather than analytics-driven.

| # | Criterion |
|---|---|
| S1 | From first launch, a new user can pick starter sources and be reading an article in **under 60 seconds** |
| S2 | Pasting a homepage address adds a source correctly for **at least 5 different publication sites** |
| S3 | The Home feed is already populated when the app opens, with no loading wait on a normal day |
| S4 | Kingsley opens this app instead of visiting the individual sites |
| S5 | He prefers the UI to Feedly's (the reason the project exists) |

## 3. Users

**One user: Kingsley.** Android phone. Reads design and tech magazines in short sessions, mostly browsing, sometimes reading a full piece. Cares strongly about visual design.

There are no accounts, no sharing between users, and no multi-user concerns.

## 4. Scope

### In v1
- Follow, mute and unfollow sources
- Add sources from a starter list or by pasting a site address (automatic feed discovery)
- One combined Home feed with source chips as the filter
- In-app article reader with adjustable text size and dark mode
- Excerpt-only handling with a path to the publisher's full page
- Save for later
- Read and unread tracking
- Search across articles from your sources
- Per-source page
- Background refresh and pull to refresh
- Settings: theme, text size, layout, refresh, OPML import and export
- The full set of motion described in the brief

### Later (design so they are easy to add)
- Per-source notifications
- Muted keywords
- Offline reading for saved articles beyond what is cached
- Home-screen widget
- Share button

### Out of scope
- **Folders or categories.** Deliberately excluded.
- **A discovery page** for finding new sources. The starter list and paste-a-link cover this.
- **Social features, comments, accounts, sync across devices.**
- **A backend.** Everything runs on the device.
- **Scraping or republishing paywalled article text.** See requirement FR-12.
- **iOS and web versions.**

## 5. User stories and acceptance criteria

### Getting started
**U1. As a new user, I can pick sources from a starter list so I have something to read right away.**
- Starter list shows two groups (Design, Tech and culture).
- Tapping a source adds it; the button shows the added state.
- Continue is disabled until at least one is picked. Skip leads to an empty Home that prompts me to add sources.

**U2. As a user, I can paste any site's address and have the app find its feed.**
- Accepts a homepage address with or without `https://`.
- Shows searching, found, already-followed and not-found states.
- A not-found message tells me what to try next.

### Reading
**U3. As a user, I see all my sources in one stream, newest first, so I don't visit each site.**
- Home shows a hero and a stream of rows.
- Read articles are visibly dimmed; unread have a dot.

**U4. As a user, I can filter to one source with one tap and go back to All with one tap.**
- Chips filter the feed; the hero is re-chosen from the filtered set.

**U5. As a user, I can open an article and read it comfortably.**
- Title, source, author, date, read time, body.
- I can change text size and switch to dark mode without leaving the article.
- Opening an article marks it read.

**U6. As a user, when a site only shares an excerpt, I can still reach the full article.**
- The Reader shows the excerpt and a clear button to the publisher's page, opened inside the app.
- The note explains that the site only shares an excerpt.

### Saving and finding
**U7. As a user, I can save an article with one gesture or tap and find it later.**
- Swipe a row right past the threshold, or tap Save in the Reader.
- Saved appears in the Saved tab.
- A saved article survives the source being unfollowed or the article dropping out of the feed.

**U8. As a user, I can search my articles.**
- Matches title, excerpt and source name; every word must match.
- Recent searches are listed and removable.
- An empty result explains what search covers.

### Managing sources
**U9. As a user, I can see all my sources and which ones have unread articles.**
- Sources tab lists them by most recent article, with unread counts.
- A source whose feed fails shows a clear problem state.

**U10. As a user, I can mute or unfollow a source from its own page.**
- Unfollow asks for confirmation and tells me saved articles stay.
- Muted sources stop appearing on Home but keep updating.

### Staying fresh
**U11. As a user, new articles are ready when I open the app.**
- Background refresh at the interval I choose, optionally Wi-Fi only.
- Pull to refresh works on Home, with feedback about how many new articles arrived or that I'm up to date.

### Control
**U12. As a user, I can control appearance and data.**
- Theme (Auto, Light, Dark), text size, Home layout.
- Import and export my source list as OPML.

## 6. Functional requirements

Priority: **P0** must ship in v1. **P1** should ship in v1 but can follow quickly.

| ID | Requirement | Priority |
|---|---|---|
| FR-1 | Parse RSS 2.0 and Atom feeds | P0 |
| FR-2 | Feed discovery from a pasted address: direct feed URL, `<link rel="alternate">` tags, then common paths (`/feed`, `/rss`, `/feed.xml`, `/atom.xml`, `/rss.xml`) | P0 |
| FR-3 | Store sources, articles and state locally; the app opens with last-fetched content and no network | P0 |
| FR-4 | Combined Home feed, newest first, filterable by source chip | P0 |
| FR-5 | Hero rule: newest article with an image in the current set; otherwise the newest as a flat type card | P0 |
| FR-6 | Image extraction in order: media tags, image enclosure, first image in content, then the page's `og:image` | P0 |
| FR-7 | Reader with excerpt-only and full-text states | P0 |
| FR-8 | Read and unread tracking; *Mark all as read* in the menu | P0 |
| FR-9 | Save and unsave from the row swipe and the Reader; Saved tab | P0 |
| FR-10 | Search per the rules in the brief | P0 |
| FR-11 | Follow, mute, unfollow with confirm; saved articles kept on unfollow | P0 |
| FR-12 | **Do not scrape or republish article text from publisher pages.** Show only what the feed provides; "Read full article" and "Open site" open the publisher's own page | P0 |
| FR-13 | Broken-feed detection and Sources row state; clear on next success | P0 |
| FR-14 | Background refresh with WorkManager, honouring interval and Wi-Fi-only; refresh on open when stale; pull to refresh | P0 |
| FR-15 | Settings: theme, text size, layout, refresh controls | P0 |
| FR-16 | Starter list with **verified** feed addresses | P0 |
| FR-17 | OPML import and export | P1 |
| FR-18 | Compact layout for Home | P1 |
| FR-19 | Full motion set per the brief, respecting the system's remove-animations setting | P0 for the big three, P1 for the smaller touches |
| FR-20 | Search recents (last 6), removable | P1 |

## 7. Non-functional requirements

| Area | Requirement |
|---|---|
| **Performance** | Cold start to a populated Home from cache in under 2 seconds on a mid-range phone. Scrolling holds 60fps with images. Feed refresh never blocks the UI. |
| **Offline** | Previously fetched articles, images already cached, and saved articles are readable with no connection. Failed fetches show a quiet state, not an error wall. |
| **Battery and data** | Background work uses WorkManager constraints. Wi-Fi-only option respected. No polling loops. |
| **Privacy** | No accounts, no analytics, no tracking SDKs. All data stays on the device. The only network traffic is to publishers' feeds, pages and images. |
| **Accessibility** | Text size steps in the Reader. Contrast meets WCAG AA in both themes. Touch targets are at least 48dp. Screen-reader labels on icon-only buttons. All gestures (swipe to save) have a tappable equivalent in the Reader. Motion turns off with the system setting. |
| **Storage** | Unsaved articles are pruned after a retention window (see open questions). Saved articles are never pruned. |
| **Compatibility** | Android phones only. Minimum version to be set (see open questions). Portrait only for v1. |
| **Language** | English only for v1. |

## 8. Constraints and assumptions

- **A feed holds only recent articles**, usually 10 to 50 per site. There is no archive, and the app does not promise one.
- **WIRED has a metered paywall.** Its feed often gives only excerpts, and opening its articles counts toward the monthly free limit. Excerpt-only is a normal state, not a failure.
- **Background refresh is approximate.** Periodic WorkManager work has a 15-minute minimum and the system decides exact timing.
- **Starter feed addresses are unverified.** Each must be checked before it ships.
- **Sites without a usable feed** cannot be added. The app says so rather than scraping the site.
- **Mockups are reference.** The seven HTML files define the look and motion; they use placeholder images and invented headlines.

## 9. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Feed discovery fails on some sites | The paste-a-link promise breaks | Layered discovery (FR-2), a helpful not-found message, test on 5+ real sites before release |
| Many articles have no image or a poor one | Image-first design looks empty | Flat type card fallback, `og:image` as a last resort, no-image row layout |
| Excerpt-only feeds frustrate reading | The Reader feels thin | Clear excerpt state with one-tap path to the full page in the same session |
| Shared-element and swipe motion is hard to get right in Compose | The signature feel is lost | Build the data layer and static screens first, then motion; ship the big three before the small touches |
| Background refresh runs late | Home isn't fresh on open | Refresh on open when stale, pull to refresh, and the Settings note that sets expectations |
| Dead or changed feed addresses over time | Sources silently stop updating | Broken-feed state on Sources (FR-13) |
| Publisher content rights | Republishing text or images beyond what feeds provide | FR-12: show only what the feed provides, link out for the rest |

## 10. Open questions

| # | Question | Leaning |
|---|---|---|
| Q1 | **In-app site viewing:** Chrome Custom Tabs or a plain WebView? | Custom Tabs, so a WIRED subscriber stays signed in |
| Q2 | **Retention:** how long are unsaved articles kept? | 30 days, saved never pruned |
| Q3 | **Minimum Android version?** | API 26 or higher, which supports the variable font axes |
| Q4 | **Distribution:** install directly on your own phone, or publish to the Play Store? | Own phone for v1. Play Store adds signing, listing, policy and privacy-page work |
| Q5 | **Site icons:** real favicons with initials as fallback, or initials only? | Favicons with initials fallback |
| Q6 | **Broken-feed destination:** what does "Tap to check" open? | Source page with an error banner and Retry |
| Q7 | **App name and icon** | Undecided |

## 11. Milestones

| Milestone | Contents | Done when |
|---|---|---|
| **M1: Data** | Schema, feed fetching and parsing, feed discovery, starter feeds verified | Five real feeds parse; paste-a-link works on five sites |
| **M2: Reading** | Home and Reader with real data, static | You can read real articles from real sources |
| **M3: Full screens** | Sources, Source page, Add sources, Search, Saved, Settings | Every screen in the brief works with real data |
| **M4: Motion** | The big three, then smaller touches | The three big motions work and can be interrupted |
| **M5: Reliability** | Background refresh, offline, error and empty states, accessibility pass | The "done means" list in the brief passes |

## 11b. Definition of v1

All P0 requirements met, the success criteria S1 to S3 verified on a real phone, and the "done means" checklist in `ANTIGRAVITY_BRIEF.md` passing. S4 and S5 are judged by use after release.
