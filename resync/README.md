# Resync

A from-scratch Reddit client for Android, modeled on the behavior and feel of Sync for Reddit
(discontinued June 2023). No Sync code or assets are used. The decompiled app served only as a reference
for which endpoints it called and how it behaved (see `../NOTES.md`).

Kotlin, Jetpack Compose (Material 3), Navigation 3, OkHttp, Coil 3, kotlinx.serialization.

## Reddit API access

Reddit no longer issues new API keys, so Resync asks for a client ID on first launch, the same
approach Boost for Reddit takes:

- **Client ID**: your own "installed app" ID, or the `App ID: ...` line Reddit emails you after you
  authorize RedReader once (paste the whole line).
- **Redirect URI**: defaults to `redreader://rr_oauth_redir`. It must match the app the ID belongs to.
- **User agent**: defaults to `org.quantumbadger.redreader/1.25.1`.

Logged out, Resync uses app-only tokens (`installed_client` grant). Logging in uses Reddit's OAuth
page in a WebView and intercepts the redirect, so no intent filter is needed for any redirect scheme.

## Build

Gradle needs a JDK it supports (Android Studio's bundled JBR works):

```bash
JAVA_HOME=~/.local/share/AndroidStudio/android-studio-rabbit1-linux/android-studio/jbr ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk` (package `io.github.radixtrator.resync.debug`).

Debug builds have a "browse bundled demo data" option on the setup screen, backed by synthetic
JSON in `app/src/debug/assets/demo/` (generated; the unit tests use the same files).

## Layout

```
data/          ApiConfig, AccountStore (multiple accounts), Settings (JSON in SharedPreferences),
               RedditClient (OAuth + HTTP), RedditApi (endpoints), Parser, CommentTree, DemoWrites
ui/navigation  Routes, Navigator (push/pop, in-app link routing, Custom Tabs, media dispatch)
ui/common      ListingViewModel + PagedList/PagedGrid (shared paging), the six post layouts, actions sheet,
               markdown HTML rendering with tappable spoilers, swipe-to-dismiss / slide-back gestures
ui/home        Sync's shell: bottom nav (Posts/Explore/Inbox), Explore sheet, account card, More actions FAB
ui/feed        Feed with large header and chips, sort sheet (time ranges), subreddit info sheet
ui/post        Post + threaded comments: rails, collapse, load more, single-thread view, jump buttons
ui/profile     User profile: karma, tabs (overview/posts/comments/saved/upvoted), sorting
ui/inbox       Inbox/unread/messages/sent, mark read, reply
ui/search      Post and community search, optionally scoped to a subreddit
ui/compose     Markdown editor (reply, edit, private message) with formatting toolbar; post submission
ui/media       Zoomable image/gallery viewer, Media3 video player (HLS/mp4, looping GIF clips)
ui/settings    Theme (system/light/dark/black), Material You, layout, NSFW, spoilers, behavior
```

## Features

- Front page, popular, all, subreddits and multis, with every sort (Top/Controversial by hour…all)
- Sync's six feed layouts (List, Compact, Smaller cards, Small cards, Cards, Slides), chosen per feed;
  cards tile into two columns on wide screens; read posts are dimmed
- Explore like Sync's: favorites, subscriptions and moderated communities with icons, most visited with counts,
  recently searched & visited, a default feed to open at launch, and a ⋮ per row
- Sync-style navigation: bottom nav, Explore sheet to jump between feeds, account switcher under the avatar,
  More actions grid (toolbar ⋮ or the ⋯ FAB), toolbar/nav/FAB hide on scroll
- Threaded comments, Sync-style: a rail for every ancestor level, tap to fold replies (with a "+N" pill),
  long-press for the inline action bar (vote, save, profile, reply, go to parent, options), comment sort
  sheet, in-thread search, collapse all, load more and "continue this thread" in-app
- Off-Reddit links in posts and comments get footnote numbers and a preview card each; reopening a post
  returns to where you left off, with the same threads folded
- Sync's extended swipe on posts and comments: drag left, further for the next of three configurable
  actions (defaults: upvote/downvote/hide on posts, upvote/save/reply on comments)
- Voting, saving, hiding, deleting, sharing; reddit links (posts, comments, subs, users,
  i.redd.it, v.redd.it, .gifv) open in-app, everything else in Custom Tabs
- Media: zoomable image and gallery viewer, animated GIFs, v.redd.it/mp4 video, swipe up or down to close
- Profiles, inbox (with unread badge), search, community info with join/leave and sidebar
- Writing: replies, edits, private messages, text and link posts, markdown toolbar
- Multiple accounts with a switcher, plus logged-out browsing
- Settings: black (AMOLED) theme, Material You, NSFW hide/blur/show, spoiler blur, default
  comment sort, muted videos, slide-to-go-back, in-app browser

Not yet: image/video uploads when posting, notifications, offline caching, widgets, moderation tools,
and anything verified against the live API (only demo data has been exercised).
