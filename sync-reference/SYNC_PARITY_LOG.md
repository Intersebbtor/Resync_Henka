# Sync for Reddit: walkthrough log for Resync parity

What Sync for Reddit v23.06.30 (patched build, logged in) contains and how it looks. Recorded on 2026-11-04 from the
running app over ADB.

- **Device:** Galaxy Z Fold (SM-F971U1) cover screen, 1248×1972 px, density override 360 dpi (**2.25 px = 1 dp**), Android 17,
  dark mode, Material You colors from the wallpaper.
- **Screenshots:** kept locally in `screens/` (git-ignored: they show personal account data), WebP at 50% scale. The UI dumps behind them (view ids, bounds, text) are in the session
  scratchpad, not in this folder.
- **Full settings tree:** [`settings-catalogue.md`](settings-catalogue.md). It lists all 36 screens and ~350 keys with types, defaults and
  list values, extracted from the APK's preference XML. This log covers only what can be seen and done in the app.
- **Current settings values:** [`settings-current-values.md`](settings-current-values.md) is every settings screen as configured on this
  install, so it shows which non-defaults are actually in use.
- **Privacy:** the screenshots show the `ExampleUser` account, its inbox, subscriptions and profile. Don't push this folder to a public repo.
- **Coverage:** recorded in two sessions; the first was cut short by the Digital Wellbeing app timer. Nothing was posted, voted, saved or
  sent. Two settings were briefly changed and then restored: Contextual chips, and Dark mode (Light, then back to Follow system).
  [§ Not yet captured](#not-yet-captured) lists what remains.

---

## 1. Global look and design tokens

Colors were sampled from the dark theme with a Monet palette. Accent colors follow the wallpaper, so treat them as roles, not fixed hex values.

| Role | Sampled | Where |
|---|---|---|
| Window / toolbar background | `#121418` | behind cards, collapsing header, status bar |
| Card / sheet / chip surface | `#1C1D21`–`#1C1D22` | post cards, bottom & top sheets, chips, comments page bg |
| Bottom nav surface | `#27292D` | navigation bar |
| Nav active indicator pill | `#454957` | 64×32 dp pill behind selected icon |
| Neutral flair chip | `#282A2E` | e.g. "Russia/Ukraine" |
| Selected row pill (sort sheet) | `#2D2F3A` | full-width rounded highlight on the current sort |
| Long-pressed comment highlight | `#282B33` | comment bg while its action bar is open |
| FAB | `#394668` | primary-container tone |
| Dialog primary button | `#293040` | "Customize view" (tonal), negative button `#292B2F` |
| Switch track (on) | `#8391B8` | with a check-mark thumb (Material 3 switch) |
| Accent text | ≈`#8A93B0`–`#A0A8C8` | subreddit names, section headers, links, "View more" |
| Title text | `#ECEEF2` | post titles |
| Secondary text | `#C4C6CA` / meta `#9FA0A4` | points/comments, author • age • domain |
| Upvoted | orange (vote pill bg `#6A3915`, text orange) | Slides vote pill, own content's points in the profile |
| Saved | yellow `#ECC940` | bookmark icon |
| Comment depth rails | `#4484F6` → `#9C27B0` → `#4CAF50` → `#FF9800` → `#E91E63`, then repeats | 1–5, then back to blue at 6; same in light theme |
| Swipe action backgrounds | comment: upvote `#FF7500`, save `#EBCA3F`, reply `#3ABF6D`; post: downvote `#9494FF`, hide `#B71C1C` | revealed behind the item while swiping |

**Light theme** (Dark mode → Light, 89, 90): window `#EEEDF3`, cards/chips `#FEFBFF`,
bottom nav `#E1E2ED`, nav pill `#C0C6DC`, FAB `#505E81`, neutral flair `#F1F0F6`. Layout is identical to dark.

**Monet palette in use** (Theme management → Visualise monet palette, 64b). Sync builds every surface from
these tonal palettes. The dark surfaces above map to roughly N1-900 (cards) and A1-700 (FAB); the light ones map to N1-10/N1-50 and A1-600.

| tone | N1 | N2 | A1 | A2 | A3 |
|---|---|---|---|---|---|
| 0 | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` |
| 10 | `#FEFBFF` | `#FEFBFF` | `#FEFBFF` | `#FEFBFF` | `#FFFBFF` |
| 50 | `#F1F0F6` | `#EFF0FB` | `#EEF0FF` | `#EEF0FF` | `#F9ECFF` |
| 100 | `#E3E2E7` | `#E1E2ED` | `#D9E2FF` | `#DCE2F9` | `#EFDBFF` |
| 200 | `#C7C6CB` | `#C5C6D0` | `#B8C6EE` | `#C0C6DC` | `#D5BDEC` |
| 300 | `#ABABB0` | `#A9ABB5` | `#9DABD1` | `#A4ABC1` | `#B9A2D0` |
| 400 | `#909095` | `#8E909A` | `#8390B6` | `#8A90A5` | `#9E88B4` |
| 500 | `#76777C` | `#757780` | `#69769B` | `#70778B` | `#836E99` |
| 600 | `#5E5E63` | `#5C5E67` | `#505E81` | `#575E71` | `#6A567F` |
| 700 | `#46464B` | `#44464F` | `#394668` | `#404659` | `#513F66` |
| 800 | `#2F3035` | `#2E3038` | `#223050` | `#2A3042` | `#3A284E` |
| 900 | `#1A1B20` | `#191B23` | `#0B1A3A` | `#151B2C` | `#241338` |
| 1000 | `#000000` | `#000000` | `#000000` | `#000000` | `#000000` |

**Typography:** the title font is set to **Product Sans** (the typeface picker shows it checked). Font choices: Roboto (regular, light, medium,
bold, slab, slab light, condensed, condensed bold, italic), System, and Product Sans (regular, medium, bold, italic). Title, description and selftext fonts
are each configurable (typeface + relative size). Page titles use a large display style, about 34 sp ("Frontpage", "Inbox", "Settings").

**Measured text sizes** (fitting Roboto to sync-reference screenshots; the reference phone runs at font scale 1.08).
Sync bundles its fonts, so it draws in Roboto whatever the device font is; everything except titles is Roboto.
- Base font size is "Smaller" by default on phones: every sp in the main screens, sheets and menus measures at **0.9×**
  (so 0.972× of plain sp on the reference phone). The settings screen is not scaled. Sizes below are Sync's own sp, before that 0.9.
- Bodies of posts and comments 14 (natural line spacing, ≈1.2em); links 1.25× the text around them, and lines holding one grow taller.
- Feed: card titles 16 on 20 sp lines (Product Sans; Roboto at 16 is as wide), card meta and stats 14, flairs 14,
  List/Compact titles 14 and meta 12 on 1.2em lines, Small cards meta 12. Page header ("Frontpage") 34, header chips 16, nav labels 15.
- Explore rows 14, Explore search 16. Sheets: rows 14, centered titles 20 in a 1.25em box with 16 dp below, 24 dp below the status bar in top sheets.
- Post view: title 16 (Product Sans), meta 14.
- Sync's TextViews set text ≈1.5% wider than Compose at the same size (0.015em of spacing), and Material's own letter spacing isn't there.
- Horizontal rules (`---`): a ≈1.3 dp onSurface line across the text width, a blank line above and below.

**Measurements** (px ÷ 2.25):

| Element | Size |
|---|---|
| Side gutters / card gap | 16 dp |
| Toolbar row | 64 dp; 48 dp icon targets |
| Large collapsing header (title + chip row) | ~156 dp |
| Chips | ~34 dp tall, 8 dp apart; icon + label; sort chip has ▾ |
| Card corner radius | ≈8 dp (cards), ≈10 dp ("smaller cards" rows), ≈16 dp (sheets, top corners) |
| Bottom nav | 80 dp tall, 3 items, label always shown |
| FAB | 56 dp rounded-square, 24 dp from right edge, sits above bottom nav |
| Sheet/menu rows | 48 dp, icon at 16 dp, text at 64 dp |
| Comment indent | 16 dp per depth; rail ≈2 dp wide; avatar ≈22 dp |
| Comment inline action bar | 48 dp, 7 equal-width icon buttons |

**Recurring patterns:**
- **Collapsing large title + chip row** at the top of every top-level list: Frontpage, subreddit, Inbox, Profile, search results.
  Scrolling hides the toolbar, the bottom nav and the FAB, and scrolling up brings them back.
- **Top sheets** drop down from the top edge for sort and "More actions". **Bottom sheets** are used for post, comment and image options,
  Change view, Explore, About and Wiki. Both kinds have a centered title.
- **More actions grid:** 4 columns of circular 56 dp icon buttons with labels below. An active toggle (e.g. Dark mode) gets an accent-filled circle.
  A ⋮ in the corner opens "Action visibility" (checkbox list), "Contextual chips" and "Reset order".
- **Popup menus** (title + checkbox rows) for small option sets.
- **Read state:** titles of visited posts are dimmed in the feed.

---

## 2. Navigation map

```
MainActivity
├─ Bottom nav: Posts | Explore | Inbox          (drawer navigation is an alternative in Settings › General; tablet mode uses a nav rail)
├─ Toolbar: [avatar] ……… Search · Sort · ⋮ More actions
│   └─ avatar → Account card: account list, Add account, Get Sync Ultra, Profile, Moderator, Settings, version
├─ FAB (⋯) → More actions sheet;  long-press → "Recently searched & visited" sheet
├─ Posts tab = current feed (Frontpage / sub / multi / search / profile)
│   ├─ tap card → CommentsActivity (post + comments)
│   ├─ tap media → SingleImageActivity / MultiImageActivity / video player
│   └─ ⋮ on card → Post options sheet
├─ Explore tab = subreddit picker bottom sheet (also the search entry point)
└─ Inbox tab = messages list
Settings = PreferencesActivity (root + 25 subscreens)
```

---

## 3. Screens

### 3.1 Frontpage feed (Posts tab)
 

- **Toolbar:** account avatar (left) and Search, Sort, ⋮ (right).
- **Header:** large title "Frontpage", then a horizontally scrolling chip row: **Hot ▾** (current sort; opens the sort sheet), **Change view**, **Random**.
- **Layout:** on this ~555 dp-wide screen, **Cards** is a 2-column staggered grid.
- **Card anatomy (Cards view):**
  - Full-bleed preview image on top. Link posts get a dark band along the image's bottom edge showing `domain` in bold and the URL path, ellipsized.
  - Title, with a 🔒 icon appended for locked posts.
  - Meta line: **subreddit** (accent) • author • age • domain. The author's flair is shown as a boxed tag after the name (e.g. `SeraBeeves`).
  - Link flair as a rounded chip. It uses the flair's own background color (colored flairs: purple "LGBTQ+", teal "OC", blue "Discussion") or neutral gray.
  - Footer: "10.6k points" over "154 comments" (2 lines, left) and a ⋮ (right).
- **Media badges** sit in a translucent circle centered on the preview: a gallery icon for galleries, "reddit ▶" for v.redd.it.
  Videos **autoplay muted inline** in the feed.
- **Text posts:** a selftext preview under the title (bordered by default; see the customize options): the first paragraph as plain text, then "…" on its own line when there's more.
- The FAB "⋯" appears bottom-right.

### 3.2 Sort sheet (top sheet)
 

- Titled "Subreddit sort". Options: Best, Hot, New, Rising, Top ⌄, Controversial ⌄, Comments. Each row has an icon (star, flame, sun/burst, trend-up,
  bars, trend-down, comment bubbles).
- The current sort gets a rounded highlight pill.
- Tapping ⌄ swaps in a sub-list: header "Top", a "..." back row, then Hour / Day / Week / Month / Year / All.

### 3.3 Change view sheet and the 6 view modes

- The bottom sheet "Change view" has radio rows: List, Compact, Smaller cards, Small cards, Cards, Slides. Buttons: **Cancel** | **Customize view**.
- Selecting a row applies it immediately. The choice is per feed.

| Mode | Look | Shot |
|---|---|---|
| **List** | Full-width rows, square thumbnail flush to the left edge. Title, then sub • author • age • domain, then "points • comments", then plain-text flair. Right column: ⌄ expand + content-type icon (link/image/gallery/play). |  |
| **Compact** | Like List, but the thumbnail is inset and rounded. Type icon top-right, ⌄ bottom-right. |  |
| **Smaller cards** | The Compact row inside a rounded card with 16 dp gutters. |  |
| **Small cards** | 2-column grid. Title on the left, small rounded thumbnail top-right, colored flair chip, points/comments + ⋮. |  |
| **Cards** | Default (§3.1). |  |
| **Slides** | Single-column "Reddit-style" posts. Sub icon + `r/sub` (accent) over author • age • domain, then title, flair, a rounded full-width image with the URL band, and a button row: **[▲ 10.6k ▼]** pill, **[💬 154]** pill, then bookmark and ⋮ on the right. A thick divider separates posts. |  |

### 3.4 Customize view (View type settings)
   

- Large title "View type". The toolbar has a reset icon. Below it: a chip row (Slides, Cards, Small cards, Smaller cards, Compact, List) and a **live preview post**
  ("Example post" by ljdawson 👑, dog photo, upvoted 9999, saved).
- Options for **Slides** (each view mode has its own set; see the catalogue):
  - **Preview options:** Show image / selftext / Twitter previews, Show subreddit icons.
  - **Image options:** Preview full height, Preview full width, Preview before title.
  - **Fonts and sizing:** Title / Description / Selftext font. Each opens a "Select property" sheet → Typeface or Relative size.
  - **Buttons:** Save, Hide, Share, Mod tools, Right aligned voting, Hide all buttons.
  - **Experimental:** thick divider between posts, border around selftext.

### 3.5 More actions (⋮ top sheet, also the FAB sheet)
    

- **Default grid:** Search, Profile, Hide read, About · Submit, Random, Dark mode (toggle), Settings · Swipe mode, Change View, Refresh, Saved.
- **All 24 actions** that "Action visibility" can show: About, Dark overlay, Dark mode, Data saving, Explore, Filters, Friends, Hide read, Inbox, Profile,
  Random, Random nsfw, Recently viewed, Refresh, Saved, Scroll to top, Search, Settings, Submit, Sync, Swipe mode, Toggle account, View, Watched.
- **Contextual chips** puts the header chips (Hot ▾ / Change view / Random) at the top of the sheet.
- The FAB opens the same grid as a bottom sheet.

### 3.6 Account card

- A floating rounded card under the toolbar, not a drawer.
- Current account (large avatar, username, ⊖ remove), then other accounts ("Anonymous" = logged out) and "Add account".
- Then: Get Sync Ultra, Profile, Moderator, Settings, and a footer with the version string.

### 3.7 Post options sheet (⋮ on a card or in the post header)

- Rows: Hide, Mark as read, Share (its own ⋮ opens a share-type picker), Watch, Report, `/u/author`, `/r/sub`, Explore domain (domain), Open in browser,
  Copy…, Filter…, Add to multi. Dividers separate the groups.
- **Copy…** → Copy title / permalink / link / username.
- **Filter…** → Filter /r/sub, Filter /u/author, Filter domain.
- **Share ⋮** → "Include title" switch; Post preview / Post comments / Link.
- In the post header the sheet omits "Mark as read".
- Per the APK, the full sheet also contains Crosspost, Awards, Upvote/Downvote, Save, Edit, Delete, Replies toggle, Flair and Translate.

### 3.8 Post + comments (CommentsActivity)
   

- **Toolbar:** ←, subreddit icon, sub name with the current comment sort beneath it in small text ("Top"), then Search, Sort, ⋮.
- **Header:**
  - Full-width image or link preview, title, and meta "author • age • domain • **98% upvoted**", then flair.
  - The preview follows Settings → Comments → "Post media preview size": Thumbnail, **Cropped height** (default; a 180 dp banner,
    center-cropped, that still opens the media) or Full height.
  - Bottom row: points/comments (left); ▲ ▼ 🔖 ⋮ (right).
- **Selftext rendering:** full markdown, including italics, horizontal rules and numbered lists ("1. item" a line apart). Links are drawn larger in accent color with **superscript footnote numbers**. Below the body, each link gets a
  **preview card** (thumbnail + "(n) URL"). This also applies to links inside comments.
- **Badges:** pinned 📌, mod 🛡, emoji flair, edited "21d*" + "(last edited 1d)".
- **Comment rows** (no card, no divider between nested replies; a thick gap separates top-level threads):
  - Small round avatar (the author's profile picture; "Show profile pictures" in Comments settings), **username**, "859 points • 4h" in gray, then the body.
  - OP gets a tinted pill around the name plus OP / mod / pinned icons. "[score hidden]" is shown when hidden.
  - Blockquotes get a gray bar and a tinted background.
  - Depth: 16 dp indent per level with colored rails (palette in §1). Rails of ancestor levels continue down the left side.
  - "View more (n)" rows appear in accent color, indented to their depth.
- **Tap a comment** → collapses it and its children; a "+4" count pill appears at the right end of the meta line.
- **Long-press a comment** → highlights it and slides in an **inline action bar**: ▲, ▼, 🔖, 👤 profile, ↩ reply, ^ parent (long-clickable), ⋮.
- **Comment ⋮ sheet** "Comment options": Share, Open in browser, Select text, Copy… (text / permalink / username), Report, Tag user, Read aloud.
- **Comment sort sheet:** Top, Best, New, Old, Controversial, Q&A. 
- **⋮ "Comment actions" grid:** Reply, Refresh, Other Discussions, About · View images, Collapse all, Sports mode, Settings · Sort, Search, Recents
  (Translate is also available). 
- **Search** opens a full-height bottom sheet with a "Search comments" field. 
- **FAB ↩** opens the reply composer.
- **State is remembered:** reopening a post restores the scroll position and the collapsed threads.

### 3.9 Composer (reply, message)
 

- Toolbar: ←, "Comment" / "Message", send ➤ (right).
- Messages add Username and Subject fields. The body is a borderless field with the hint "Type a comment".
- **Formatting bar** above the keyboard (scrolls horizontally): preview 👁, emoji, drafts, insert image, spoiler, link, **B**, *I*, ~~S~~, superscript,
  quote, bulleted list, numbered list, promo (insert Sync signature), info, bug report, settings.

### 3.10 Media viewers
- **Image** (31, 32):
  - Fullscreen, deep-zoom (subsampling), dark backdrop. A single tap closes it.
  - Swipe up from the bottom for a translucent sheet with:
    - download ⤓ and close ✕
    - link info: domain, URL, resolution, file size
    - a debug log ("Detected standard image… Deepzoom image loaded")
    - a bottom bar: Comments, Open in browser, Copy, Share
- **Video** (34, 35):
  - ExoPlayer (HLS from v.redd.it). Bottom controls: seek bar, HD toggle, restart ⏮, play/pause, mute, "00:02 / 00:10", download, ⋮.
  - The ⋮ sheet raises the controls and adds playback speed (− 1x +), link info, a debug log, and the same 4-button bottom bar.
- **Gallery** (MultiImageActivity): **broken in patched Sync.** It opens and immediately closes with the toast "Error loading album"
  (33). A MorpheApp post, "Sync for Reddit – Loading Comments and Reddit Galleries fix", was in the search results.

### 3.11 Explore tab (subreddit picker)
  

- A bottom sheet (half height, drag to full) with a rounded "Search…" field and a ⋮.
- **Sections** (collapsible headers with ⌃): **Favorites**, **Subscriptions** (A–Z), **Moderated**, **Top** = most visited, with visit counts
  "Frontpage (272)" and a "Default" tag on the default feed. Feeds, Custom feeds and Follows can be enabled.
- **Rows:** round sub icon, name, ⋮.
- **Row ⋮:** Open in new window, Open in swipe mode, (Un)favorite, About, Set as default, Unsubscribe, Update sort, Update view mode, Reset viewed.
- **Search ⋮:**
  - Section visibility (Favorites, Feeds, Custom feeds, Subscriptions, Most visited, Moderated, Follows)
  - Toggles: Highlight default, Duplicate favs, Expand on open, Remember position, Fast scroller
  - Actions: Reload subscriptions, Reset fav order, Clear search history, Clear top visited
- **Typing a query** (40):
  - Buttons: "Search all" (plus "Search r/<current>" when inside a sub).
  - Results: **Subscribed** matches; **Suggested subreddit results** with subscriber counts ("33.58m subs") and a "Search all subs" link;
    **Actions** "Open u/<q>", "Open r/<q>".
- **Toolbar Search** opens this same sheet with the keyboard up and a "Recently searched & visited" list. Past queries are listed
  as `"voice" in /r/ObsidianMD` (46, 52).

### 3.12 Subreddit feed

- **Header:** sub icon (large, left of title), small "👤 33.6M  📡 0" counts above the big "pics" title.
- **Chips:** Hot ▾, Join, About, Wiki, Flairs ▾, Watch.
- **Pinned posts** show a 📌 after the title. Mod authors get a 🛡 and an emoji flair.
- **About** (42):
  - Bottom sheet: banner, centered icon, name, counts, chips Join / Watch / Favorite.
  - Sidebar markdown in a rounded card. Relative links show their target in parentheses, e.g. "(/wiki/)".
  - ⋮: View wiki, Filter by flair, View rules, Share, Message mods, View mods, Edit flair, Add to multi.
- **Wiki** (43): bottom sheet titled "pics - index", rendered markdown.
- **Flairs** chip: **broken**, shows the toast "Error loading post flairs".
- The "online" count shows 0 because Reddit no longer reports it. Hide it or drop it.

### 3.13 Search results
 

- Large title is the quoted query (`"sync for reddit"`). Chips: **Relevance ▾** and **Options**.
- **Sort:** Relevance, New, Hot, Top, Comments.
- **Options** ("Edit current" sheet): mode chips Search · Subreddit · Custom feed · Search subs · Profile · Domain, an input field, and Cancel / Change.

### 3.14 Inbox tab

- Toolbar: avatar, ✓✓ (mark all read), ⋮. Large title "Inbox". Chips: **All ▾**, New message, Mark all as read.
- **Sections:** All, Unread, DMs, Sent, Comment replies, Post replies, Mentions, Mod, Mod unread.
- **Rows:**
  - A type icon (↩ comment reply, 📄 post reply).
  - "**comment reply** *Post title in italics*".
  - "from **user** via **/r/sub** sent 8d", with user and sub in accent.
  - The body, with links in accent.
  - A ⋮ per row: Reply, Context, Profile.

### 3.15 Profile
  

- **Header:** avatar, "14733 karma", big username.
- **Chips:** Overview ▾, New ▾, About, Message, Trophies, Tag user, Block, Share.
- **Sections:** Overview, Comments, Submitted, Upvoted, Downvoted, Saved, Hidden.
- **Overview** is a 2-column grid mixing **comment cards** (*post title italic*, then user, points (orange when upvoted), age and sub, then the
  comment body) and **post cards** (your own posts show a 👤 "you" marker after the name).
- **About sheet:** link karma, comment karma, joined date; chips Message / Trophies / Tag user; the description.
- **Trophies sheet:** list with trophy icons (14-Year Club, Verified Email…).
- Bug seen: an emoji user flair renders as a raw `https://emoji.redditmedia.com/...` URL in the meta line.

### 3.16 Settings root

- Large title "Settings", a "Search settings" pill, then grouped rows (icon, title, gray summary). Every row and its options are in
  [`settings-catalogue.md`](settings-catalogue.md).

| Group | Entries |
|---|---|
| New | Sync Ultra, Legacy settings (Developer options is hidden) |
| Appearance | Run setup, General, Experimental, Tablet, Animations, Theme management, Dark mode, View type, Comments, Awards |
| Content | Reddit web preferences, Post options, Comment options, Messaging, Media, Link handling, History |
| General | Data options, Filters, Backup |
| Other | Restore purchases, Privacy, Security (biometric lock), Everything else |

### 3.17 Settings subscreens
Screenshots: General, Sync Ultra, Legacy,
Experimental, Tablet, Animations,
Theme management, Dark mode, Comments,
Awards, Reddit web prefs, Post options,
Comment options, Messaging, Media,
Link handling, History, Data options,
Filters, Backup, Privacy,
Security, Everything else. Values: [settings-current-values.md](settings-current-values.md).

- **Shared layout:** ← and a reset icon (gear with arrow) in the toolbar, a large title, accent-colored category headers, and rows with title +
  gray summary. List rows show their current value as the summary. Switches are M3 with a check-mark thumb. Row icons are inconsistent:
  Sync Ultra, Legacy, Tablet, Reddit web prefs, Post options, Media, Data options, Filters, Backup, Privacy, Security and Everything else have
  them; General, Experimental, Animations, Comments, Comment options, Messaging, Link handling and History don't. Awards and Dark mode use
  radio buttons on the left.
- **Info rows:** some screens start with a rounded tooltip card with an icon and ✕ (Theme management, Comments "Comments look a little different
  in V22"). Some categories have a paragraph of gray explanatory text under the header (Tablet, Data options, Backup).
- **Comments** screen has a live preview of a comment, like the View type screen.
- **Sync Ultra:** price cards (€1.09 / month, €10.99 / year (selected), €33.99 lifetime), a "Subscribe to Ultra" button, then feature rows
  (Cloud backup, Paint users, Tag users, Website previews, Translate text, Select text from images, Restore removed comments, Support the dev)
  and terms text. Out of scope for Resync except as features.
- **Theme management:** Monet tooltip; light and dark mini-phone previews side by side; **Automatic** / **Manual selection** buttons; then
  Dark tweaks (Extra colorful, AMOLED black, Dark overlay, highlight/primary/secondary text colors), Light tweaks, and Extra (Visualise monet
  palette, Share theme, Load theme).
- **Dark mode:** radio-style rows Follow system / Light mode, and legacy Follow timed with a time range.
- **Notable non-defaults on this install:** bottom nav with labels, FAB action = More actions, single tap collapses comments, hide AutoMod,
  comment indents colorful, "Show larger links" + inline link sources on, Peek media on, image debug log on, autoplay WiFi-only.

### 3.18 Submit
   

- **Entry:** More actions → Submit opens a bottom sheet "Submit new post": **Text**, **Link**, **Photo**.
- **Form** (SubmitActivity): toolbar ← "Submit" ➤. Title field, then a type-specific part:
  - Text: selftext editor with the same formatting bar as the comment composer.
  - Link: URL field.
  - Photo: **Take photo** / **Choose photo** buttons.
- **Shared footer:** "subreddit" autocomplete field with **Flair** (chip) and **Rules** buttons on the same row, then three switch rows:
  Send replies to my inbox (on), Spoiler, NSFW.

### 3.19 Saved

- More actions → Saved opens your profile with the section chip set to **Saved** (same grid of mixed post and comment cards).

### 3.20 Swipe mode
 

- More actions → Swipe mode opens SwipeActivity: one post per page, swipe **horizontally** for the next/previous post.
- Toolbar: ←, feed name with ▾ (opens the Explore sheet to switch feed) over the sort, then Search, Sort, ⋮.
- Header: sub icon, `r/sub` (accent), author • age • domain • flair (accent), points • comments, then the title.
- Media fills the rest, centered, with a type badge in the middle (open-link or gallery icon).
- Bottom bar: ▲, ▼, 🔖, comments, download, ⋮.

### 3.21 Moderator

- Account card → Moderator opens ModActivity with large title "Moderator" and chips **Modqueue ▾** and **Hot ▾**.
- Sections: Modqueue, Reports, Spam, Edited, Unmoderated, Comments.
- Empty state is a snackbar "This sub has no posts" with **Retry**.

### 3.22 Swipe actions
 

- **Posts** (swipe right-to-left on a card, "extended swipe"): the farther you drag, the next action is armed. The icon shows on a colored
  panel revealed behind the card. On this install: 1st Upvote (↑), 2nd Downvote (↓, `#9494FF`), 3rd Hide (✕, `#B71C1C`).
  Options: Upvote, Downvote, Save, Profile, Share, Hide, Mark as read. "Swipe to hide" is a separate simple mode.
- **Comments** (right-to-left): 1st Upvote (orange `#FF7500`), 2nd Save (yellow `#EBCA3F`), 3rd Reply (green `#3ABF6D`). The panel spans the
  comment's row with the icon near the right edge. Options: Reply, Upvote, Downvote, Save, Profile, Share.
- Releasing back at the start cancels. "Force vibrate on selection" adds haptics as each stage arms.

---

## 4. Behaviors observed

- Feed:
  - Collapsing header, plus auto-hide of the toolbar, bottom nav and FAB on scroll.
  - Visited titles are dimmed.
  - Inline video autoplays muted.
- Comments:
  - Tap collapses; long-press opens the inline action bar.
  - Collapsed state and scroll position persist per post.
- Image viewer: tap to close; swipe up for the info sheet.
- FAB: tap opens More actions; long-press opens recents/search.
- Sort and view choices apply on tap, with no confirm step. View mode is stored per feed.
- Swipe actions: extended right-to-left swipes on posts and comments (§3.22). Swipe mode pages horizontally (§3.20).
- From the APK (not exercised on device), for later:
  - Peek (long-press media preview), volume-key comment navigation, comment navigation bar (off on this install).
  - Comment TTS player, sports/live mode, offline sync.
  - "Reddit Push" notifications through a notification listener.
  - Link handling for 14 reddit, imgur and gfycat hosts.
  - A `sync-settings:` deep link and share-to-Sync.
  - See the catalogue's "Other notable string resources" and "AndroidManifest" sections.

## 5. Broken or degraded in Sync today (parity target is the working behavior)

| Feature | Symptom |
|---|---|
| Reddit galleries | "Error loading album" toast, viewer closes |
| Subreddit post flairs (Flairs chip) | "Error loading post flairs" (Reddit only lists flairs to people allowed to set their own) |
| Random chip / quick action | "Error grabbing random subreddit" (Reddit dropped random communities) |
| Subreddit "online" count | always 0 |
| Emoji user flair in meta line | raw emoji URL shown as text |
| redgifs/gfycat (per NOTES.md) | hard-coded key, likely dead |

---

## 6. Parity checklist vs Resync

Status is based on Resync's README and a quick read of `resync/app/src/main` (enums, screens). Rows touched by the
shell slice (2026-11-04) were built and checked on the device; the rest was not tested.
✅ have · ◐ partial · ❌ missing

| Area | Sync | Resync | |
|---|---|---|---|
| Navigation shell | Bottom nav Posts / Explore / Inbox + avatar account card + FAB (drawer is an option; nav rail in tablet mode) | bottom nav, account card, ⋯ FAB (tap: More actions, long-press: Explore search), hide on scroll | ✅ (no drawer option / rail) |
| Swipe actions on posts/comments | extended 3-stage, configurable, colored panels | extended swipe (72/144/216dp stages), Sync's colors and defaults, haptic ticks, configurable in Settings | ✅ (no swipe-to-hide mode, no forced vibration) |
| Swipe mode (one post per page, horizontal) | yes | More actions → Swipe mode: horizontal pager of Slides-layout posts, loads more near the end, back/search toolbar, post ⋮ sheet (unverified on device) | ◐ (no feed switcher or sort in the toolbar, no media-centered page, no download button) |
| Submit | Text / Link / Photo, flair, rules, spoiler, NSFW, replies toggle | text + link posts, flair picker, community rules sheet, spoiler / NSFW / send-replies toggles (unverified on device) | ◐ (photo posts via Reddit media upload added, unverified) |
| Light theme + Monet palette, AMOLED, extra-colorful tweaks | yes | Sync's sampled Monet palette (light + dark) as the default theme, black, Material You, Dark mode quick toggle | ◐ (no tweaks/manual seed) |
| Mod tools (modqueue, reports, spam, edited, unmoderated) | yes | About ⋮ → Mod tools (only for communities you moderate) opens a Moderator screen: section menu, posts and comments with Approve / Remove / Spam (unverified on device; may need the modposts scope at login) | ◐ (no mod inbox boxes or mod mail, no Comments section) |
| Tablet mode (nav rail, dual pane, tabbed comments) | yes (off here) | Settings › Tablet mode: navigation rail instead of the bottom bar at 600 dp+ (unverified) | ◐ (no dual pane, no tabbed comments; Sync's look not captured) |
| Collapsing large title + chip row | everywhere | feeds (front page: sort, Change view, Random; communities: sort, Join, About, Wiki, Flairs, Watch, Change view), inbox and profile; small title returns on scroll-up. Search results: the quoted query as the title over the Posts tab, chips Relevance ▾ (sort), Options and "Only in r/x" inside a community; the toolbar keeps the search field and hides on scroll (unverified on device) | ✅ |
| Feed view modes | 6 (List, Compact, Smaller cards, Small cards, Cards, Slides), per feed, 2-col staggered grid on wide screens | all 6, per feed, staggered 2 columns from 480dp; list rows expand inline | ✅ (no Customize view screen) |
| Customize view | per-mode toggles, fonts, live preview | — | ❌ |
| Feed sorts | Best/Hot/New/Rising/Top×6/Controversial×6/Comments | all of them in Sync's top sheet; "Comments" shows the feed's newest comments as Sync's cards (italic post title, author, score, age, sub) and opens each in its thread | ✅ |
| Comment sorts | Top/Best/New/Old/Controversial/Q&A | same | ✅ |
| Search sorts | Relevance/New/Hot/Top/Comments + search modes (sub, multi, subs, profile, domain) | Relevance/New/Hot/Top (+ Top: this week)/Comments; Options sheet ("Edit current") with Search · Subreddit · Custom feed · Search subs · Profile · Domain and an input: Search / Search subs re-run the query, the others narrow it to a community, custom feed (user/feed), author or site (unverified on device) | ◐ (Sync's exact Subreddit-mode behavior unconfirmed) |
| More actions grid (configurable, 24 actions) | yes | grid of 15 (Search, Profile, Hide read, About, Submit, Random, Dark mode, Settings, Swipe mode, Change view, Refresh, Saved, Inbox, Filters, Watched); Settings › Action visibility switches each off (unverified on device) | ◐ (10 of Sync's 24 actions missing, no reordering) |
| Post options sheet | ~22 actions incl. Copy…, Filter…, Watch, Explore domain, Add to multi | Sync's layout and order: Hide, Mark as read, Share, Watch (new-comment notifications), Report / u, r, Explore domain (the site's feed), Open in browser / Copy… (title, permalink, link, username), Filter… (community, user, domain; undone in Settings), Edit, Delete | ✅ (Add to multi and Share ⋮ (Include title; Post preview / Post comments / Link) added, unverified) |
| Read-state dimming, Hide read | yes | opened posts dim in feeds (local); Settings › Hide read posts and a More actions toggle leave out posts opened before the feed loaded (unverified on device) | ✅ |
| Comment rails | 5-color cycle, 16 dp indent | Sync's palette, a rail for every ancestor level, 16dp indent | ✅ |
| Comment profile pictures ("Show profile pictures") | yes | authors' avatars from Reddit's account data, fetched in batches as comments load; NSFW ones and missing ones fall back to the initial; setting to turn off | ✅ |
| Tap-to-collapse with "+n" pill | yes | tap folds the replies, "+N" pill; the comment itself stays readable | ✅ |
| Long-press inline comment action bar | 7 buttons | ▲ ▼ 🔖 👤 ↩ ^ ⋮ bar under the highlighted comment | ✅ |
| Comment ⋮: Select text, Tag user, Read aloud, Copy… | yes | Share, Open in browser, Select text, Copy… (text/permalink/username), Report (the community's rules for comments, Reddit's rules, Other; confirmed before sending), Tag user (local tags shown beside names in comments and post meta), Read aloud (text-to-speech, "Stop reading" while it talks), Edit/Delete | ✅ (tags have no colors) |
| Comment actions grid (Other discussions, View images, Collapse all, Sports mode, Recents, Translate) | yes | Reply, Refresh, Collapse all, Settings, Sort, Search, Other discussions (url: search), View images, Translate (Google Translate); prev/next buttons as an opt-in setting (unverified on device) | ◐ (no Recents, Sports mode) |
| In-thread comment search | yes | sheet that filters loaded comments and jumps to one | ✅ |
| Link footnotes + link preview cards in selftext/comments | yes | superscript numbers on off-Reddit links, "(n) url" cards with image thumbnails, larger links, blank line between paragraphs | ✅ |
| "98% upvoted", edited "*" + "(last edited)", OP pill, pinned/mod badges | yes | upvote %, "21d* (last edited 1d)" on posts, rounded OP pill, OP / shield / pin icons, shield after mod authors; "33.6M" counts, no online count | ✅ (comments show "*" only; Sync's comment format unverified) |
| Restore scroll position / collapsed state per post | yes | persisted per post (200 most recent), setting to turn off | ✅ |
| Composer formatting bar | 17 tools incl. preview, emoji, drafts, image, spoiler, numbered list | markdown toolbar plus numbered list, heading, code block, rule, emoji picker (unverified) | ◐ (no preview, drafts, image upload) |
| Image viewer info sheet (download, link info, copy/share/comments) | yes | tap to close, swipe up for the sheet: download, host, URL, original resolution and file size, Comments / Open in browser / Copy / Share; colorful backdrop: a dark, muted average of the image's colors fades in from black (unverified on device); pinch/double-tap zoom up to 20x | ◐ (no debug log, no deep zoom) |
| Video player: HD toggle, speed, download, mute | yes | Sync's bar (seek, HD, restart, play/pause, mute, time, download, ⋮) and ⋮ panel (speed, link info, actions); HD Reddit video quality setting; downloads mux v.redd.it audio; zoomable video: pinch to zoom (up to 20x) and pan, double tap to zoom in / reset, swipe-to-dismiss off while zoomed, drawn on a TextureView (unverified on device) | ◐ (no debug log) |
| Galleries | broken in Sync | gallery viewer | ✅ (better) |
| Explore sheet: Favorites / Subscriptions / Moderated / Most visited (+counts) / Follows, per-sub menu | yes | Favorites, Subscriptions (with icons), Moderated, Top (counts, Default tag, default in accent), opt-in Feeds; Sync's 42 dp rows and 51 dp headers; row ⋮ (favorite, set as default, about, unsubscribe, reset viewed), options ⋮ (section visibility, duplicate favorites, reload, clear) | ◐ (Custom feeds and Follows sections added (follows = your u_ subscriptions); favorites can be moved up and down) |
| Recently searched & visited | yes | toolbar search opens Explore with recent feeds and searches; typing shows Search all (+ "Search r/<sub>"), bolded matches, suggested communities with "Search all subs", Open u/ and r/ | ✅ |
| Subreddit header chips: Join, About, Wiki, Flairs, Watch, Favorite | yes | Join, About, Wiki, Flairs (Reddit's list, or the flairs on its 200 recent posts; picking one searches the community); Random opens one of your communities; About is Sync's sheet (banner, icon, Join / Watch / Favorite, sidebar card, ⋮: wiki, flair filter, rules, share, message mods, moderators; no Edit flair or Add to multi); Watch notifies about each new post (hourly WorkManager check, "Stop watching" on the notification, list and "Check now" in Settings) | ◐ (Sync's Watch sheet options unknown) |
| Wiki viewer | yes | the wiki's front page in a "pics - index" sheet, without Reddit's table of contents | ✅ (subpages, in-app wiki links, page list, table of contents; unverified on device) |
| Inbox sections | 9 (incl. DMs, comment/post replies, mentions, mod) | All, Unread, DMs, Sent, Comment replies, Post replies, Mentions | ✅ (Mod and Mod unread boxes added, unverified) |
| Message notifications ("Periodically check for new messages") | Off / 10 min … 24 h / On launch, default 60 min; Mark read and Reply on the notification | same choices (15 min instead of 10, WorkManager's floor), default 60 min; one notification per unread item, opening its thread or the inbox; Mark read and inline Reply; "Check now" | ◐ (no Reddit Push mirroring) |
| Profile sections | 7 (incl. Downvoted, Hidden) + About/Trophies/Tag/Block/Share | Sync's large header (avatar, karma, name) and chips (section ▾, sort ▾, About, Message, Trophies, Tag user, Block, Share) over the feed grid with comment cards; all 7 sections; About sheet (banner, link/comment karma, cake day, Message / Trophies / Tag user, description) and Trophies sheet | ✅ (Block needs a fresh login for Reddit's "account" permission) |
| User tagging | yes | Tag user from comment ⋮, profile chips and About sheet; label shown next to the name; local only | ✅ (tag colors added, unverified on device) |
| Filters (sub/user/domain/flair/keyword) | yes | sub, user and domain filters via Post ⋮ → Filter…; list with undo in Settings › Filters | ◐ (keyword and flair filters added in Settings › Filters and Post ⋮ → Filter…; no dedicated editor screens) |
| Multis: add to multi, custom feeds section | yes | Explore › Custom feeds section (your multis open as "a+b" feeds); About ⋮ and Post ⋮ → Add to multi (toggle membership); unverified on device | ◐ (no create/edit multi, no Follows section) |
| Settings breadth | ~350 keys / 25 screens | theme, layout, NSFW, spoilers, behavior | ◐ (see catalogue) |
| Typeface choices (Product Sans, Roboto variants…) | yes | Roboto bundled and used everywhere, like Sync's default; all text at Sync's "Smaller" base size | ◐ (Settings › Typeface (Roboto, system sans/serif/monospace) and Base font size (Smallest…Largest) added, unverified on device; Product Sans can't be bundled) |
| Monet theme + theme management | yes | Material You toggle; Settings › Theme color (8 seeds, HSL-tinted palette, not true Monet) and Extra colorful (unverified on device) | ◐ (no theme management screen) |

---

## Not yet captured

These are in the APK, so the catalogue covers their options, but their look isn't recorded:

- **Unfolded inner screen** (2448×1848) and **Tablet mode** (nav rail, dual pane, tabbed comments). This needs the phone unfolded, and Tablet mode
  switched on in Settings › Tablet; it's off on this install.
- Onboarding / "Run setup" and the login flow. These were skipped because they rewrite settings or need credentials.
- Recents, Watched, peek (long-press media), the comment navigation bar, drawer navigation, the comment TTS player, and sports mode.
- Filter editor screens (keyword, domain, sub, user and flair lists) and the Backup flows.
