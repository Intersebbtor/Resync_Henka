# Sync for Reddit v23.06.30: settings catalogue

Source: the apktool decode of `com.laurencedawson.reddit_sync` (versionName `v23.06.30-13:39`, versionCode 23033). Only resources were decoded (no smali). All UI text is **inlined literally** in `res/xml/cat_*.xml`, so few `@string` references exist. `@array`, `@integer` and `@bool` references are resolved below. A value that differs on `values-sw600dp` (tablets) is shown next to the phone value.

## At a glance

- **36 preference XML screens** (`cat_*.xml`). That is the root, 25 screens linked directly from root, 6 view-type screens behind "View type", and 4 onboarding screens with no root link.
- **358 preference rows**, counted as rows with a key plus the keyless PushPreference. 350 distinct keys, because some keys are shared between view-type screens.
  - By control: switch 219, action 88, list 32, radio 10, color override 6, color 1, slider 1, custom toggle 1.
  - 270 store a value (switch/list/radio/color/slider/push). 88 are action rows that open a dialog, picker or sub-list, or run a command.
  - 6 are hidden (`isPreferenceVisible=false` on the row or its category). 17 are Ultra-related (key prefix `ultra_`, or they sit on the Ultra screen / in an `ultra*` category).
- Root links point to screens through `app:preference_ref="@integer/X"`. The integer-to-XML mapping below is **inferred from the names** (for example `GENERAL` → `cat_general.xml`), because the code is not decoded. Unused integers: `ROOT`=0 and `CACHE`=35. `CACHE` has no XML file and no root entry.

Rows per screen: `cat_ultra.xml` 13, `cat_developer.xml` 13, `cat_legacy.xml` 3, `cat_general.xml` 22, `cat_experimental.xml` 7, `cat_tablet.xml` 7, `cat_animations.xml` 9, `cat_monet_advanced.xml` 16, `cat_dark.xml` 5, `cat_post_slide_view.xml` 18, `cat_post_card_view.xml` 15, `cat_post_small_card_view.xml` 7, `cat_post_smaller_card_view.xml` 5, `cat_post_compact_view.xml` 6, `cat_post_list_view.xml` 6, `cat_comment_view_customization.xml` 28, `cat_awards.xml` 3, `cat_reddit_web_preferences.xml` 7, `cat_posts.xml` 20, `cat_comments.xml` 19, `cat_messaging.xml` 5, `cat_images.xml` 24, `cat_links.xml` 22, `cat_history.xml` 13, `cat_data.xml` 9, `cat_filters.xml` 17, `cat_backup.xml` 6, `cat_purchases.xml` 0, `cat_analytics.xml` 5, `cat_security.xml` 5, `cat_other.xml` 16, `cat_onboarding_dark.xml` 3, `cat_onboarding_purchases.xml` 0, `cat_onboarding_settings.xml` 3, `cat_onboarding_tablet.xml` 1.

## Legend: control types

| Control (used in tables) | Class (`com.laurencedawson.reddit_sync.ui.preferences.…`) | Notes |
|---|---|---|
| switch | `defaults.SyncCheckBoxPreference` | Rendered with `MonetSwitch` (layout `preference_material_switch`). Boolean. |
| list | `defaults.SyncListPreference` | Single-choice list. Values are **strings**, even when numeric. |
| radio | `defaults.SyncRadioButtonPreference` | Boolean per row. Rows in a group are mutually exclusive (handled in code). |
| action | `defaults.SyncPreference` | Plain clickable row: opens a dialog, picker or sub-screen, or runs a command. Stores nothing by itself. |
| color | `defaults.ColorPreference` | Color picker (`ColorView` swatch). |
| color override | `defaults.monet.MonetDarkColorOverridePreference` | Per-mode color override swatch. |
| slider | `custom.SliderPreference` | `MonetSlider` (Material Slider). Range comes from its layout. |
| custom toggle | `PushPreference` | Toggle plus a notification-access setup row. |
| (widgets) | `HeaderPreference`, `defaults.CategoryHeaderPreference`, `CategoryInfoHeaderPreference`, `custom.DividerPreference`, tooltips, `custom.ViewChipGroundPreference`, `custom.ViewTypePreviewPreference`, `custom.ThemePreviewPreference`, `custom.SearchPreference`, `custom.PurchasesPreference`, `custom.ultra.Ultra*Preference` | No stored value. Shown as "Widget" bullets or "Info text" quotes, in screen order. Header and divider elements are omitted. |

Ultra marking: **[Ultra]** means the key starts with `ultra_`, or the row is on the Sync Ultra screen or in an `ultra*` category. No XML attribute marks paid gating explicitly, so visibility is toggled in code. The remote-config defaults (`res/xml/remote_config_defaults.xml`) include the flags `ultra`, `ultra_backup`, `ultra_translate`, `ultra_restore` and `4266fa` (all `false`) plus the JSON lists `patreon_backers` and `ultra_testers`.

## Root screen (`cat_root.xml`)

The header title is "Settings". At the top of the screen are a settings search box (`SearchPreference`) and a tooltip (`RootLegacyTooltipPreference`). Then come five categories of `RootPreference` links:

| Root category | Entry | Summary | preference_ref | Target |
|---|---|---|---|---|
| New | Sync Ultra | Website previews, cloud backup, translation & more | `ULTRA` (28) | `cat_ultra.xml` |
| New | Developer options | Bleep bloop | `DEVELOPER_OPTIONS` (23) | `cat_developer.xml` |
| New | Legacy settings | Restore the old look and feel of Sync and more | `LEGACY` (36) | `cat_legacy.xml` |
| Appearance | Run setup | Configure basic settings in Sync | `SETUP` (34) | onboarding flow (inferred) |
| Appearance | General | Toolbar, navigation style, gestures & more | `GENERAL` (5) | `cat_general.xml` |
| Appearance | Experimental | Beta experiments | `EXPERIMENTS` (31) | `cat_experimental.xml` |
| Appearance | Tablet | Setup options for tablet mode & foldable devices | `TABLET` (30) | `cat_tablet.xml` |
| Appearance | Animations | Activity transitions & view animations | `ANIMATIONS` (13) | `cat_animations.xml` |
| Appearance | Theme management | Customize the app theme | `MONET_ADVANCED` (32) | `cat_monet_advanced.xml` |
| Appearance | Dark mode | Light, dark or auto | `DARK_MODE` (24) | `cat_dark.xml` |
| Appearance | View type | Customize how posts look | `POST_VIEW_OPTIONS` (14) | 6 × `cat_post_*_view.xml` |
| Appearance | Comments | Customize how comments look | `COMMENT_VIEW_OPTIONS` (26) | `cat_comment_view_customization.xml` |
| Appearance | Awards | Customize how awards are shown | `AWARDS` (27) | `cat_awards.xml` |
| Content | Reddit web preferences | Configure reddit.com preferences | `WEB_PREFERENCES` (22) | `cat_reddit_web_preferences.xml` |
| Content | Post options | Sorting, flair, swipe actions & more | `POSTS` (8) | `cat_posts.xml` |
| Content | Comment options | Navigation, swipe actions & more | `COMMENTS` (3) | `cat_comments.xml` |
| Content | Messaging | Message interval & push notifications | `MESSAGING` (6) | `cat_messaging.xml` |
| Content | Media | Media interaction, download setup & more | `IMAGES` (10) | `cat_images.xml` |
| Content | Link handling | Configure which sites open in Sync, default browser & link styles | `LINKS` (7) | `cat_links.xml` |
| Content | History | Marking as read, search & clear history data | `HISTORY` (18) | `cat_history.xml` |
| General | Data options | Data saving, autoplay & preloading | `DATA` (12) | `cat_data.xml` |
| General | Filters | Post filters, advanced & content filters | `FILTERS` (11) | `cat_filters.xml` |
| General | Backup | Setup & backup / restore | `BACKUP` (17) | `cat_backup.xml` |
| Other | Restore purchases | Restore any previously made purchases | `PURCHASES` (33) | `cat_purchases.xml` |
| Other | Privacy | Privacy policy & crashlytics | `ANALYTICS` (19) | `cat_analytics.xml` |
| Other | Security | Biometric app lock | `SECURITY` (20) | `cat_security.xml` |
| Other | Everything else | App info, licenses & credits | `OTHER` (2) | `cat_other.xml` |

---

## Root › New (category key `ultra_wrapper`)

### Sync Ultra (`cat_ultra.xml`)

_Root summary: "Website previews, cloud backup, translation & more". preference_ref `ULTRA` = 28._

- Widget `UltraSignupPreference`: Ultra sign-up card: description, monthly / yearly / lifetime purchase options, "Subscribe to Ultra" button.

- Widget `UltraManagePreference`: "Manage subscription" button (for subscribers).

#### Cloud services (category key `ultra_cloud_section`)

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Paint users **[Ultra]** | `ultra_paint` | action | — | _Highlight users with custom colors and built-in automatic cloud-sync_<br>The profile header chip "Paint user" also opens it. Cloud-synced (the app ships a Firebase Realtime DB URL). |
| Tag users **[Ultra]** | `ultra_tag` | action | — | _Tag users with custom text and built-in automatic cloud-sync_ |
| Settings cloud backup **[Ultra]** | `ultra_cloud` | action | — | _Backup and restore settings per-account to the cloud_ |

#### Enhancements (category key `ultra_enhancements`)

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Website previews **[Ultra]** | `ultra_website_previews` | switch | `true` | _Show website previews in comments (and when long pressing links)_ |

#### Perks (category key `ultra_perks`; header key `ultra_perks_title`; header hidden)

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Cloud backup **[Ultra]** | `ultra_disabled_cloud` | action | — | _Backup setting to the cloud and share across devices_<br>Perk teaser for non-subscribers. |
| Paint users **[Ultra]** | `ultra_disabled_paint` | action | — | _Tag users with custom colors and automatically sync across devices_<br>Perk teaser for non-subscribers. XML sets `defaultValue="true"` on this action row, which does nothing. |
| Tag users **[Ultra]** | `ultra_disabled_tag` | action | — | _Tag users with custom tags and automatically sync across devices_<br>Perk teaser for non-subscribers. XML sets `defaultValue="true"` on this action row, which does nothing. |
| Website previews **[Ultra]** | `ultra_disabled_website_previews` | action | — | _Show rich site previews in comments and when long pressing links_<br>Perk teaser for non-subscribers. |
| Translate text **[Ultra]** | `ultra_translate` | action | — | _Translate comments & selftext (powered by Google)_<br>Uses on-device ML Kit translate + langid (manifest meta-data). |
| Select text from images **[Ultra]** | `ultra_select_text` | action | — | _Quickly pull text from images using machine learning_<br>Uses ML Kit OCR. The image viewer shows "Extract text". |
| Restore removed comments **[Ultra]** | `ultra_removed` | action | — | _Accessible from the comment more menu_<br>Restores removed comments from the comment "more" menu. |
| Support the dev! **[Ultra]** | `ultra_support` | action | — | _Help keep /u/ljdawson caffeinated_ |

- Widget `UltraRestorePreference`: "Restore subscription" button.

- Widget `UltraResetPreference`: "Reset subscription locally (debugging)" button.

#### Terms and conditions

> Info text: "Monthly and yearly subscriptions will automatically renew and can be cancelled any time."

> Info text: "Cloud backups are available for the duration of your subscription and will be automatically removed when your subscription expires. Backups can also be manually removed from the backups page of the settings."

#### Privacy (**whole category hidden**)

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Incognito Mode **[Ultra]** _(hidden)_ | `preview_sites` | switch | `false` | _Clear all recents, history, searches, visited subreddits and drafts on exit..._<br>The whole category is hidden. Key `preview_sites` does not match the title "Incognito Mode" (probably reused). |

### Developer options (`cat_developer.xml`)

_Root summary: "Bleep bloop". preference_ref `DEVELOPER_OPTIONS` = 23._

#### Debug

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| FCM ID | `fcm` | action | — | Shows the Firebase Cloud Messaging token. |

#### Checks (category key `checks`)

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Ultra cache status | `cache_status` | action | — | Diagnostic. |
| Ultra status | `ultra_status` | action | — | Diagnostic. |
| Installing package status | `install_status` | action | — | Diagnostic (installer package). |

#### Options

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Show autoplay video region | `developer_video` | switch | `false` |  |

#### Tests

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Discord join demo | `discord_demo` | action | — | Test screen. |
| Material dialogs demo | `dialogs_demo` | action | — | Opens TestDialogActivity (alert/selection/input/progress dialog demos). |
| Markdown tests | `markdown_tests` | action | — | Opens TestMarkdownActivity (emotes, inline GIFs, image flair, many awards). |

#### Schedule WorkManager workers

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Messaging worker | `messaging` | action | — | Schedules a WorkManager worker immediately. |
| Mod messaging worker | `mod_messaging` | action | — | Schedules a WorkManager worker. |
| Check subreddits worker | `subs` | action | — | Schedules a WorkManager worker. |
| Check watched subreddits worker | `watched` | action | — | Schedules a WorkManager worker (watched-subreddit notifications). |
| Clear cache worker | `cache` | action | — | Schedules a WorkManager worker. |

### Legacy settings (`cat_legacy.xml`)

_Root summary: "Restore the old look and feel of Sync and more". preference_ref `LEGACY` = 36._

#### Old settings

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| View old subreddits | `legacy_subs` | action | — | Shows the subreddit list from the old app version. |

#### Restore look and feel

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Restore comments to older style | `restore_legacy_comments` | action | — | _Rollback all comment styling options to look like v19_<br>Bulk-resets the comment styling prefs to the v19 look. |
| Restore entire app to older style | `restore_legacy` | action | — | _Rollback all settings to look like v19_<br>Bulk-resets all prefs to the v19 look. |

## Root › Appearance

### Run setup (runs the onboarding flow)

_Root summary: "Configure basic settings in Sync". preference_ref `SETUP` = 34._

No `cat_*.xml` belongs to this entry. It most likely starts `OnboardingActivity`, which uses the `cat_onboarding_*.xml` screens (see "Other / onboarding screens"). This is inferred from names, since smali was not decoded.

### General (`cat_general.xml`)

_Root summary: "Toolbar, navigation style, gestures & more". preference_ref `GENERAL` = 5._

#### Toolbar

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Expanded Toolbar | `toolbar_expanded` | switch | `true` | _Show a large toolbar / header at the top of posts_ |
| Autohide Toolbar | `toolbar_autohide_preference` | switch | `true` | _Hide when scrolling_ |
| Toolbar quick return | `posts_quick_scroll` | switch | `true` | _Tap the Toolbar to return to the top_ |
| Subreddit icons | `subreddit_icons` | switch | `true` | _Show sub icons in the toolbar_ |
| Transparent status | `transparent_status` | switch | `false` | _Draw behind the status for an edge-to-edge experience_ |

#### Drawer navigation (category key `drawer`)

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Use drawer navigation | `use_drawer` | switch | `false` | Alternative to bottom navigation. Onboarding offers "Bottom navigation (new)" or "Drawer navigation (classic)". |

#### Bottom navigation (category key `bottom`)

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Use bottom navigation | `navigation_bottom` | switch | `true` | Alternative to drawer navigation. |
| Autohide bottom navigation | `navigation_bottom_hide` | switch | `true` | _Hide when scrolling_ |
| Show labels | `navigation_bottom_labels_new` | switch | `true` |  |

#### Floating Action Button

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Show FAB | `main_fab_display` | switch | `true` |  |
| Autohide FAB | `main_fab_hide` | switch | `true` | _Hide on scroll_ |
| FAB action | `main_fab_action` | list | `2` (More actions) | Options: Submit → `0`; Hide Read → `1`; More actions → `2` |

#### General

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Base font size | `fontsize_preference` | list | `1` (Smaller) on phones; `2` (Small) on sw600dp (`@integer/default_font_size`) | Options: Smallest → `0`; Smaller → `1`; Small → `2`; Medium → `3`; Large → `4`; Larger → `5`; Largest → `6`; (no label) → `7`<br>`sizes` has 7 labels but `sizes_values` has 8 values; value `7` has no label. Phone default `1` = "Smaller". |
| Show top visited subs icon shortcuts | `icon_shotcuts` | switch | `true` | Key misspelled `icon_shotcuts` in the original. Implies dynamic launcher shortcuts (the manifest has no static shortcuts). |
| Show changelogs after update | `changelog_preference` | switch | `true` |  |
| Show a reminder to update when available | `update_reminder` | switch | `true` |  |
| Confirm exit | `exit_preference` | switch | `true` |  |
| Click back to open the left drawer | `drawer_back_preference` | switch | `false` | _This overrides confirm exit!_ |
| Launcher icon | `icon_preference` | action | — | Launcher icon picker. Backed by manifest activity-aliases `HomeActivity.Default` (enabled), `.DefaultDark`, `.Legacy`, `.Fancy`, `.Pride`, `.Blm`. |

#### Gestures

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Swipe to return | `swipe_hide` | switch | `true` |  |
| Dim behind activity | `swipe_dim` | switch | `true` |  |
| Swipe to return sensitivity | `swipe_sens` | list | `1.0` (Default) | Options: Minimum → `0.1`; Reduced sensitivity → `0.5`; Default → `1.0`; Increased sensitivity → `2.5`; Maximum → `5.0` |

### Experimental (`cat_experimental.xml`)

_Root summary: "Beta experiments". preference_ref `EXPERIMENTS` = 31._

#### Toggles

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Snap scrolling | `snap` | switch | `true` | _Snap bottom navigation elements to open or closed when finger is lifted_ |
| Enhanced autoplay | `enhanced_autoplay` | switch | `false` | _Grab higher quality autoplay videos with audio for Gfycat & Redgifs_ |
| Show actions | `navigation_bottom_actions` | switch | `false` | _Show More Actions in the bottom navigation bar. Bottom navigation must be enabled to use this._<br>Needs `navigation_bottom`. |
| Sliding panel style drawer | `use_panel` | switch | `false` | _Replace the default Android drawer with a Discord inspired sliding panel. Drawer navigation must be enabled to use this._<br>Needs `use_drawer`. |
| Highlight stickied posts | `sticky_highlight` | switch | `false` | _Color stickied posts with the accent of your theme_ |
| Fixed height video | `fixed_height_video` | switch | `false` | _Force videos to play inline with a fixed height_ |
| AMOLED post dividers | `amoled_divider` | switch | `false` | _Add dividers between posts only when using AMOLED black mode_ |

### Tablet (`cat_tablet.xml`)

_Root summary: "Setup options for tablet mode & foldable devices". preference_ref `TABLET` = 30._

#### Setup

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Tablet mode | `tablet_mode` | switch | `false` on phones; `true` on sw600dp (`@bool/tablet`) | Its info text says it enables dual pane and tabbed comments. |

> Info text: "Tablet mode is an optimised interface for large screen devices featuring the new navigation rail. When enabled you will be able to use dual pane mode and tabbed comments. Note: Although you can enable this on any device it is only recommended for tablets or large foldable devices."

#### Options (category key `tablet_options`)

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Dual pane (landscape) | `tablet_dual_pane_landscape` | switch | `true` | _Show comments next to posts in landscape_ |
| Dual pane (portrait) | `tablet_dual_pane_portrait` | switch | `false` |  |
| Tabbed comments | `tablet_tabs` | switch | `true` | _Load comments as tabs in dual pane mode_ |

#### Advanced (category key `tablet_options_2`)

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Navigation rail item position | `rail_gravity` | list | `0` (Top) | Options: Top → `0`; Middle → `1`; Bottom → `2` |
| Advanced insets | `tablet_insets` | switch | `false` | _Resize the app to respect additional window insets_ |
| Foldable display support | `tablet_foldable` | switch | `false` | _Automatically adjust the UI depending on fold state_ |

### Animations (`cat_animations.xml`)

_Root summary: "Activity transitions & view animations". preference_ref `ANIMATIONS` = 13._

#### Transitions

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Image transitions | `animate_transition_image` | switch | `true` |  |
| Comment transitions | `animate_comments_transition` | switch | `true` |  |

#### Animations

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Enter / exit animations | `animate_enter_exit` | switch | `true` |  |
| Post hide animations | `animate_hide_posts` | switch | `true` |  |
| Comments collapsing animation | `animate_comment_collapse` | switch | `true` |  |
| Comments more animation | `animate_comment_more` | switch | `true` |  |
| Comments action animation | `animate_comment_action` | switch | `true` |  |
| Comments insert animation | `animate_comment_insert` | switch | `true` |  |
| Comments navigation animation | `animate_comment_nav` | switch | `true` |  |

### Theme management (`cat_monet_advanced.xml`)

_Root summary: "Customize the app theme". preference_ref `MONET_ADVANCED` = 32._

- Widget `MonetIntroTooltipPreference`: Intro tooltip for the Monet theme engine.

- Widget `ThemePreviewPreference`: Live theme preview with two buttons, "Automatic" and "Manual selection". Categories `automatic` (empty in XML, filled at runtime) and `manual` follow.

_Category `automatic` (no header)_

_(empty in XML; filled at runtime)_

#### Manual setup (category key `manual`)

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Theme base color | `monet_manual_theme_color` | color | — | Used in "Manual selection" mode (category key `manual`). No XML default. |
| Color intensity | `color_intensity` | action | — | Non-selectable label row (`selectable=false`) for the slider below. |
| _(no title)_ | `monet_color_intensity` | slider | — | MonetSlider from layout `preference_slider.xml`: **min 0.0, max 2.0, step 0.2**. No XML default. |

#### Dark tweaks

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Extra colorful dark mode | `monet_boost_dark_color` | switch | `false` | _Add a little extra oomph to dark mode_ |
| AMOLED black | `force_amoled` | switch | `false` | _Use a much darker palette in dark mode_ |
| Dark overlay | `dark_overlay` | switch | `false` | _Decrease the screen brightness by 60% in dark mode_ |
| Highlight color | `monet_override_dark_link_color` | color override | — | Color override swatch (layout `preference_color_override`). No XML default (unset means the theme color). |
| Primary text color | `monet_override_dark_primary_text_color` | color override | — | Color override swatch (layout `preference_color_override`). No XML default (unset means the theme color). |
| Secondary text color | `monet_override_dark_secondary_text_color` | color override | — | Color override swatch (layout `preference_color_override`). No XML default (unset means the theme color). |

#### Light tweaks

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Extra colorful light mode | `monet_boost_light_color` | switch | `false` | _Add a little extra oomph to light mode_ |
| Highlight color | `monet_override_light_link_color` | color override | — | Color override swatch (layout `preference_color_override`). No XML default (unset means the theme color). |
| Primary text color | `monet_override_light_primary_text_color` | color override | — | Color override swatch (layout `preference_color_override`). No XML default (unset means the theme color). |
| Secondary text color | `monet_override_light_secondary_text_color` | color override | — | Color override swatch (layout `preference_color_override`). No XML default (unset means the theme color). |

#### Extra

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Visualise monet palette | `palette` | action | — | Same as the menu item "View palette". |
| Share theme | `share` | action | — | Exports the theme as text (layout `fragment_export_theme`). |
| Load theme | `load` | action | — | Imports a theme. |

### Dark mode (`cat_dark.xml`)

_Root summary: "Light, dark or auto". preference_ref `DARK_MODE` = 24._

#### Automatic

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Follow system | `night_mode_follow` | radio | `true` | Radio group with `night_mode_force_off`, `night_mode_force_on`, `night_mode_auto`: exactly one is on. |

#### Manual

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Light mode | `night_mode_force_off` | radio | `false` |  |
| Dark mode | `night_mode_force_on` | radio | `false` |  |

#### Legacy

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Follow timed | `night_mode_auto` | radio | `false` | Legacy timed dark mode. Uses `dark_auto_picker`. |
| Timed range | `dark_auto_picker` | action | — | Opens the start/end time-range picker (layout `timerange_picker_dialog`). |

### View type (6 view-type screens)

_Root summary: "Customize how posts look". preference_ref `POST_VIEW_OPTIONS` = 14._

Each view-type screen starts with the same chip selector (`ViewChipGroundPreference`). Selecting a chip shows that type's screen. Below they follow the chip order. The `ui_mode` ids are list=0, compact=1, smaller cards=2, small cards=3, cards=4, slides=5 (comments preview = 7).

#### View type: Slides (`cat_post_slide_view.xml`, ui_mode 5)

- Widget `ViewChipGroundPreference`: View-type chip selector: Slides, Cards, Small cards, Smaller cards, Compact, List (switches between the view-type screens).

- Widget `ViewTypePreviewPreference`: Live preview of the post/comment layout (`ui_mode=5`).

##### Preview options

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Show image previews | `slide_image_previews` | switch | `true` |  |
| Show selftext previews | `slide_selftext_previews` | switch | `true` |  |
| Show Twitter previews | `slide_twitter_previews` | switch | `true` |  |
| Show subreddit icons | `slide_subreddit_thumbnail` | switch | `true` | _Shown on Frontpage, All, search etc_ |

##### Image options

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Preview full height | `slide_image_full_height` | switch | `true` |  |
| Preview full width | `slide_full_width` | switch | `false` |  |
| Preview before title | `slide_image_top` | switch | `false` |  |

##### Fonts and sizing

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Title font | `slide_title` | action | — | Opens the font picker (typeface + size, see Appendix A). |
| Description font | `slide_description` | action | — | Opens the font picker (typeface + size, see Appendix A). |
| Selftext font | `slide_selftext` | action | — | Opens the font picker (typeface + size, see Appendix A). |

##### Buttons

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Save | `slide_buttons_save` | switch | `true` |  |
| Hide | `slide_buttons_hide` | switch | `false` |  |
| Share | `slide_buttons_share` | switch | `false` |  |
| Mod tools | `slide_buttons_mod` | switch | `true` | _This is only shown for subs you moderate_ |
| Right aligned voting | `slide_button_vote_right` | switch | `false` |  |
| Hide all buttons | `slide_hide_all_buttons` | switch | `false` |  |

##### Experimental options

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Show a thick divider between posts | `slide_thick_divider` | switch | `true` | _Recommended as this improves post viewability_ |
| Show a border around selftext | `slide_selftext_border` | switch | `true` |  |

#### View type: Cards (`cat_post_card_view.xml`, ui_mode 4)

- Widget `ViewChipGroundPreference`: View-type chip selector: Slides, Cards, Small cards, Smaller cards, Compact, List (switches between the view-type screens).

- Widget `ViewTypePreviewPreference`: Live preview of the post/comment layout (`ui_mode=4`).

##### Image options

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Image above title | `card_image_above` | switch | `true` |  |
| Full height | `card_image_full_height` | switch | `false` |  |
| Text preview labels _(hidden)_ | `card_text_labels` | switch | `false` |  |

##### Selftext options

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Show selftext previews | `card_selftext_previews` | switch | `true` |  |
| Show selftext expand icon _(hidden)_ | `card_selftext_expand` | switch | `true` |  |

##### Card options

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Full width cards | `card_full_width` | switch | `false` | _Single column only_ |
| Columns (portrait) | `post_columns_portrait` | list | `1` on phones; `2` on sw600dp (`@string/post_columns_portrait`) | Options: `1`, `2`, `3`, `4`, `5`<br>Same array for entries and values. |
| Columns (landscape) | `post_columns_landscape` | list | `1` on phones; `3` on sw600dp (`@string/post_columns_landscape`) | Options: `1`, `2`, `3`, `4`, `5`<br>Same array for entries and values. |

##### Fonts and sizing

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Title font | `card_title` | action | — | Opens the font picker (typeface + size, see Appendix A). |
| Description font | `card_description` | action | — | Opens the font picker (typeface + size, see Appendix A). |
| Selftext font | `card_selftext` | action | — | Opens the font picker (typeface + size, see Appendix A). |

##### Buttons

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Save | `card_buttons_save` | switch | `true` |  |
| Hide | `card_buttons_hide` | switch | `false` |  |
| Share | `card_buttons_share` | switch | `false` |  |
| Mod tools | `card_buttons_mod` | switch | `true` | _This is only shown for subs you moderate_ |

#### View type: Small cards (`cat_post_small_card_view.xml`, ui_mode 3)

- Widget `ViewChipGroundPreference`: View-type chip selector: Slides, Cards, Small cards, Smaller cards, Compact, List (switches between the view-type screens).

- Widget `ViewTypePreviewPreference`: Live preview of the post/comment layout (`ui_mode=3`).

##### Image options

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Align thumbnail left | `small_cards_thumbnail_align_left` | switch | `false` |  |

##### Fonts and sizing

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Title font | `small_card_title` | action | — | Opens the font picker (typeface + size, see Appendix A). |
| Description font | `small_card_description` | action | — | Opens the font picker (typeface + size, see Appendix A). |

##### Buttons

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Save | `small_card_buttons_save` | switch | `true` |  |
| Hide | `small_card_buttons_hide` | switch | `false` |  |
| Share | `small_card_buttons_share` | switch | `false` |  |
| Mod tools | `small_card_buttons_mod` | switch | `true` | _This is only shown for subs you moderate_ |

#### View type: Smaller cards (`cat_post_smaller_card_view.xml`, ui_mode 2)

- Widget `ViewChipGroundPreference`: View-type chip selector: Slides, Cards, Small cards, Smaller cards, Compact, List (switches between the view-type screens).

- Widget `ViewTypePreviewPreference`: Live preview of the post/comment layout (`ui_mode=2`).

##### Image options

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Type indicators | `type_indicators` | switch | `true` |  |
| Align thumbnail right | `thumbnail_align` | switch | `false` |  |

##### Fonts and sizing

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Title font | `smaller_card_title` | action | — | Opens the font picker (typeface + size, see Appendix A). |
| Limit title to a single line | `compact_single_lines` | switch | `false` |  |
| Description font | `smaller_card_description` | action | — | Opens the font picker (typeface + size, see Appendix A). |

#### View type: Compact (`cat_post_compact_view.xml`, ui_mode 1)

- Widget `ViewChipGroundPreference`: View-type chip selector: Slides, Cards, Small cards, Smaller cards, Compact, List (switches between the view-type screens).

- Widget `ViewTypePreviewPreference`: Live preview of the post/comment layout (`ui_mode=1`).

##### Image options

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Thumbnails | `compact_thumbnails` | switch | `true` |  |
| Type indicators | `type_indicators` | switch | `true` |  |
| Align thumbnail right | `thumbnail_align` | switch | `false` |  |

##### Fonts and sizing

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Title font | `compact_title` | action | — | Opens the font picker (typeface + size, see Appendix A). |
| Limit title to a single line | `compact_single_lines` | switch | `false` |  |
| Description font | `compact_description` | action | — | Opens the font picker (typeface + size, see Appendix A). |

#### View type: List (`cat_post_list_view.xml`, ui_mode 0)

- Widget `ViewChipGroundPreference`: View-type chip selector: Slides, Cards, Small cards, Smaller cards, Compact, List (switches between the view-type screens).

- Widget `ViewTypePreviewPreference`: Live preview of the post/comment layout (`ui_mode=0`).

##### Image options

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Thumbnails | `compact_thumbnails` | switch | `true` |  |
| Type indicators | `type_indicators` | switch | `true` |  |
| Align thumbnail right | `thumbnail_align` | switch | `false` |  |

##### Fonts and sizing

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Title font | `list_title` | action | — | Opens the font picker (typeface + size, see Appendix A). |
| Limit title to a single line | `compact_single_lines` | switch | `false` |  |
| Description font | `list_description` | action | — | Opens the font picker (typeface + size, see Appendix A). |

### Comments (`cat_comment_view_customization.xml`)

_Root summary: "Customize how comments look". preference_ref `COMMENT_VIEW_OPTIONS` = 26._

- Widget `CommentsLegacyTooltipPreference`: Tooltip card (about restoring the legacy comment style).

- Widget `ViewTypePreviewPreference`: Live preview of the post/comment layout (`ui_mode=7`).

#### Fonts and sizing

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Comment description font | `comments_description` | action | — | Opens the font picker (typeface + size, see Appendix A). |
| Comment body font | `comments_body` | action | — | Opens the font picker (typeface + size, see Appendix A). |
| Comment body font line space multiplier | `comments_body_space` | list | `1x` | _The space between each line_<br>Options: 0.5x → `0.5`; 0.6x → `0.6`; 0.7x → `0.7`; 0.8x → `0.8`; 0.9x → `0.9`; 0.95x → `0.95`; 1x → `1`; 1.05x → `1.05`; 1.1x → `1.1`; 1.2x → `1.2`; 1.3x → `1.3`; 1.4x → `1.4`; 1.5x → `1.5`; 1.6x → `1.6`; 1.7x → `1.7`; 1.8x → `1.8`; 1.9x → `1.9`; 2.0x → `2.0`<br>XML default `1x` is an entry *label*, and matches no entryValue (`1`). Code probably handles it. |

#### Post media preview size

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Thumbnail | `media_preview_thumbnail` | switch | — | One-of group: `media_preview_thumbnail` / `media_preview_cropped` / `media_preview_full`. No XML default. |
| Cropped height | `media_preview_cropped` | switch | — |  |
| Full height | `media_preview_full` | switch | — |  |

#### Style

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Use double width comment indents _(hidden)_ | `comments_row_double` | switch | `false` |  |
| Show colorful comment indents | `comments_colored_preference` | switch | `false` |  |
| Padding style | `comment_padding` | list | `1` (Material (medium padding)) | Options: Material (max padding) → `0`; Material (medium padding) → `1`; Material (minimum padding) → `2`; Sync Alpha → `3`; Sync classic → `4` |
| Show colorful comment quotes _(hidden)_ | `comments_colored_quotes` | switch | `false` |  |

#### View tweaks

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Show emotes pictures | `comments_emotes` | switch | `true` |  |
| Show animated image comments | `comments_animated_images` | switch | `true` |  |
| Show profile pictures | `comments_author_profile_pic` | switch | `false` |  |
| Highlight new comments with a dot | `comments_highlight_new` | switch | `true` |  |
| Highlight new comment backgrounds | `comments_highlight_new_background` | switch | `false` |  |
| Show user flair | `comment_flair_preference` | switch | `true` |  |
| Show a dagger (†) on controversial comments | `comments_controversial` | switch | `true` |  |
| Comment dividers | `comments_dividers_` | switch | `false` | _Show dividers between comments_<br>The key has a trailing underscore. |
| Top level comment padding | `comments_top_level_padding` | switch | `true` | _Show padding between top level comments_ |
| Left handed mode | `comments_reverse_actions` | switch | `false` | _Reverse comment actions_ |
| Show scrollbar | `comments_scrollbar` | switch | `false` |  |
| Delimit scores with a bullet point | `delimit_preference` | switch | `false` |  |
| Hide AutoMod comments | `comments_hide_automod` | switch | `false` | _Also hide other similar bots_ |
| Always show comment actions | `comments_always_show_bar` | switch | `false` |  |

#### Highlighting

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Highlight OP | `highlight_op` | switch | `true` |  |
| Highlight yourself | `highlight_yourself` | switch | `true` |  |
| Highlight friends | `highlight_friends` | switch | `true` |  |
| Highlight new accounts | `highlight_new_accounts` | switch | `false` |  |

### Awards (`cat_awards.xml`)

_Root summary: "Customize how awards are shown". preference_ref `AWARDS` = 27._

#### Display

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Show modern awards | `awards_modern` | radio | — | Radio group: `awards_modern` / `awards_minimal` / `awards_none`. No XML default, so code decides (onboarding "Show reddit awards" defaults on). |
| Show minimal awards | `awards_minimal` | radio | — |  |
| Hide awards | `awards_none` | radio | — |  |

## Root › Content

### Reddit web preferences (`cat_reddit_web_preferences.xml`)

_Root summary: "Configure reddit.com preferences". preference_ref `WEB_PREFERENCES` = 22._

_Screen root has key `web_preferences_root` and `enabled=false`; it is enabled at runtime once the data loads. **Every row on this screen is a reddit.com account preference.** They are stored server-side and read/written through the Reddit API (presumably `/api/v1/me/prefs`), so none has a local default._

> Info text: "Reddit web preferences change the view of the desktop website but can also impact certain parts of the app (such as showing thumbnails)."

#### Privacy

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Online presence | `show_presence` | switch | — | _Show other users if you're online or not..._ |
| Track clicks | `allow_clicktracking` | switch | — | _Allow Reddit to log my outbound clicks for personalization_ |

#### Media

> Info text: "Your media preference on the desktop website will also affect the thumbnails and previews shown in app."

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Show thumbnails next to links | `media_on` | switch | — | _This is the recommended setting for showing all thumbnails, previews and expanded content_<br>One-of group: `media_on` / `media_off` / `media_subreddit`. Mirrors the reddit.com account pref `media`. |
| Don't show thumbnails next to links | `media_off` | switch | — | _Completely disables all thumbnails and affects full height previews_ |
| Show thumbnails based on that subreddit's media preferences | `media_subreddit` | switch | — | _Show thumbnails and expanded content depending on what a particular subreddit suggests_ |

#### Link options

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Compress the link display | `link_compress` | switch | — | _Hide thumbnails on desktop to optimise space. Not recommended for use in-app as this will stop thumbnails and previews from showing._ |

#### Content options

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| NSFW search | `content_nsfw_search` | switch | — | _include not safe for work (NSFW) search results in searches_ |

### Post options (`cat_posts.xml`)

_Root summary: "Sorting, flair, swipe actions & more". preference_ref `POSTS` = 8._

#### General

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Default post sort | `post_sort_preference` | list | `sort_0` (What's Hot) | Options: What's Hot → `sort_0`; New → `sort_1`; Rising → `sort_2`; Top - Hour → `sort_3_0`; Top - Day → `sort_3_1`; Top - Week → `sort_3_2`; Top - Month → `sort_3_3`; Top - Year → `sort_3_4`; Top - All → `sort_3_5`; Controversial - Hour → `sort_4_0`; Controversial - Day → `sort_4_1`; Controversial - Week → `sort_4_2`; Controversial - Month → `sort_4_3`; Controversial - Year → `sort_4_4`; Controversial - All → `sort_4_5`; Comments → `sort_6` |
| Return to your default sub before exiting | `posts_home` | switch | `true` |  |

#### Flair

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Show modern post flair | `flair_modern` | switch | — | One-of group of switches: `flair_modern` / `flair_minimal` / `flair_none`. No XML default (onboarding "Show reddit post flair" defaults on). |
| Show minimal post flair | `flair_minimal` | switch | — |  |
| Hide post flair | `flair_none` | switch | — |  |

#### Swipe actions

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Swipe to hide | `swipe_hide_posts_new` | switch | `false` |  |
| Extended swipe posts for additional options | `post_swipe_actions` | switch | `true` |  |
| First swipe action | `post_navigation_first` | list | `0` (Upvote) | Options: Upvote → `0`; Downvote → `1`; Save → `2`; Profile → `3`; Share → `4`; Hide → `5`; Mark as read → `6` |
| Second swipe action | `post_navigation_second` | list | `1` (Downvote) | Options: Upvote → `0`; Downvote → `1`; Save → `2`; Profile → `3`; Share → `4`; Hide → `5`; Mark as read → `6` |
| Third swipe action | `post_navigation_third` | list | `5` (Hide) | Options: Upvote → `0`; Downvote → `1`; Save → `2`; Profile → `3`; Share → `4`; Hide → `5`; Mark as read → `6` |
| Force vibrate on selection | `post_swipe_actions_vibrate` | switch | `false` | _Override your system preference_ |

#### View tweaks

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Show post author | `posts_show_author` | switch | `true` |  |
| Show scrollbar | `posts_scrollbar` | switch | `false` |  |
| Display new comment count | `posts_display_new` | switch | `false` |  |
| Display short scores | `post_scores_short` | switch | `true` |  |
| Display if a post is OC | `post_oc` | switch | `true` |  |
| Display page dividers | `post_dividers` | switch | `false` |  |

#### Hiding

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Sync hidden posts to reddit | `posts_hide_reddit` | switch | `true` |  |

#### Legacy

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Long press cards to open comments in the background | `posts_comments_new_activity` | switch | `false` | _Ideal for keeping track of multiple posts_ |
| Long press selftext to select text | `post_long_press` | switch | `false` |  |

### Comment options (`cat_comments.xml`)

_Root summary: "Navigation, swipe actions & more". preference_ref `COMMENTS` = 3._

#### Navigation

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Display navigation bar | `md2_comment_nav_bar` | switch | `false` |  |
| Autohide navigation bar | `md2_comment_nav_bar_hide` | switch | `true` | _Hide when scrolling_ |
| Volume key navigation | `comments_volume_nav` | switch | `false` |  |
| Navigation bar position | `comment_navigation_position` | list | `1` (Center) | Options: Left → `0`; Center → `1`; Right → `2` |
| Show reply fab | `comments_reply_fab` | switch | `true` | _Only shown when the navigation bar is disabled_ |
| Autohide FAB | `comments_reply_fab_autohide` | switch | `true` | _Hide on scroll_ |

#### Swipe actions

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Swipe comments for additional options | `comment_swipe_actions` | switch | `true` |  |
| First swipe action | `comment_navigation_first` | list | `1` (Upvote) | Options: Reply → `0`; Upvote → `1`; Downvote → `2`; Save → `3`; Profile → `4`; Share → `5` |
| Second swipe action | `comment_navigation_second` | list | `3` (Save) | Options: Reply → `0`; Upvote → `1`; Downvote → `2`; Save → `3`; Profile → `4`; Share → `5` |
| Third swipe action | `comment_navigation_third` | list | `0` (Reply) | Options: Reply → `0`; Upvote → `1`; Downvote → `2`; Save → `3`; Profile → `4`; Share → `5` |
| Force vibrate on selection | `comment_swipe_actions_vibrate` | switch | `false` | _Override your system preference_ |

#### General

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Initial comment count | `comments_load` | list | `75` | _The number of comments to load_<br>Options: 50 → `50`; 75 → `75`; 100 → `100`; 200 → `200`; All → `0`<br>Value `0` means All. |
| Default comment sort | `comment_sort_preference` | list | `6` (Suggested) | Options: Top → `0`; Best → `1`; New → `2`; Old → `3`; Controversial → `4`; Q&A → `5`; Suggested → `6` |
| View parent button | `comments_parent_button` | list | `2` (Show parent) | _Hide, show or change the behaviour_<br>Options: Hide → `0`; Show root → `1`; Show parent → `2` |
| Live comments refresh rate | `live_comments_refresh` | list | `15` (Every 15 seconds) | _How often comments are refreshed in live mode_<br>Options: Every 2 seconds → `2`; Every 5 seconds → `5`; Every 10 seconds → `10`; Every 15 seconds → `15`; Every 20 seconds → `20`; Every 25 seconds → `25`; Every 30 seconds → `30`; Every 45 seconds → `45`; Every 60 seconds → `60`<br>For live/sports threads (layout `view_sports`: Stop / Refresh). |
| Remember comment position | `comments_remember_pos` | switch | `true` |  |
| Collapse all threads by default | `comments_collapse_all_default` | switch | `false` |  |
| Single click to hide comment | `comments_single` | switch | `false` | _Reverse the comment click actions_ |
| Collapse parent | `collapse_parent` | switch | `false` | _Hide the parent when hiding children_ |

### Messaging (`cat_messaging.xml`)

_Root summary: "Message interval & push notifications". preference_ref `MESSAGING` = 6._

#### Basic setup

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Periodically check for new messages | `message_preference_new` | list | `3` (Every 60 minutes) | Options: Off → `0`; Every 10 minutes → `1`; Every 30 minutes → `2`; Every 60 minutes → `3`; Every 3 hours → `4`; Every 6 hours → `5`; Every 12 hours → `6`; Every 24 hours → `7`; On launch → `8` |
| Check for mod mail | `message_mod_mail` | switch | `false` |  |

#### Advanced

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Reddit Push notifications | — | custom toggle | — | No key in XML. Custom widget with a toggle and a "Configure notification access" row. Its text: "This requires the official Reddit app to be installed. When the official app receives a notification Sync will also receive the notification." It is implemented with a NotificationListenerService (`AllNotificationService`). |

#### Notification options (header key `notification_options`)

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Notification sound | `message_sound` | switch | `true` |  |
| Notification LED | `message_led` | switch | `true` |  |

### Media (`cat_images.xml`)

_Root summary: "Media interaction, download setup & more". preference_ref `IMAGES` = 10._

#### General

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Inline image previews | `inline_image_previews` | switch | `true` | _Small image previews in comments and selftext_ |
| Tap to close | `quick_dismiss_preference` | switch | `true` |  |
| Swipe to close | `swipe_dismiss_preference` | switch | `true` |  |
| Zoom into tall images | `zoom_tall` | switch | `true` |  |
| Use colorful backgrounds | `image_colorful_background` | switch | `true` |  |
| Always show the download button | `viewer_show_save` | switch | `false` |  |
| Force auto rotate | `view_auto_rotate` | switch | `false` |  |

#### Configure downloads

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Change download directory | `directory_media` | action | — | Folder picker. |
| Add .nomedia files to subfolders | `save_nomedia` | switch | `false` |  |
| Download to subfolders | `save_folders_` | switch | `false` | The key has a trailing underscore. |

#### Peeking

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Peek media | `peek` | switch | `true` | _Long press to preview media_ |
| Shown options when peeking | `peek_options` | switch | `true` | _Peeking is also known as long pressing_ |
| Mute when peeking | `peek_mute` | switch | `false` |  |

#### Video / GIF playback

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Display seek bar | `gif_seek` | switch | `true` |  |
| Display speed controls | `gif_speed` | switch | `true` |  |
| Mute when opening | `video_mute` | switch | `false` |  |

#### Deepzoom

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Gallery DPI | `deepzoom_dpi` | list | `200` (200 dpi) | _Adjust the initial load quality_<br>Options: 200 dpi → `200`; 250 dpi → `250`; 300 dpi → `300`; 350 dpi → `350`; 400 dpi → `400`; auto dpi → `-1` |
| Max zoom | `deepzoom_max` | list | `30` (30x) | _Adjust how far the deepzoom can zoom_<br>Options: 30x → `30`; 50x → `50`; 100x → `100` |

#### Advanced

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Zoomable video | `zoom_videos` | switch | `true` | _May cause video banding / distortion_ |
| Limit download size | `limit_image_size` | switch | `true` | _Limit image loading to 50MB_ |
| Show image debug options | `debug_image` | switch | `true` | _Display a detail image download debug log_<br>Defaults to `true` although it is a debug option. |
| Media cache duration | `cache_preference` | list | `1` (Empty cache every day) | _How frequently the media cache is emptied_<br>Options: Empty cache every time the app closes → `0`; Empty cache every day → `1`; Empty cache every other day → `2`; Empty cache every third day → `3` |
| Manually clear the media cache | `cache_manual` | action | — | _Remove all images irrespective of age_ |
| Cache streaming videos | `cache_streaming_videos` | switch | `true` | _Cache streaming content locally_ |

### Link handling (`cat_links.xml`)

_Root summary: "Configure which sites open in Sync, default browser & link styles". preference_ref `LINKS` = 7._

#### Links opened in app

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Images | `appimages_preference` | switch | `true` |  |
| v.redd.it videos | `reddit_video_preference` | switch | `true` |  |
| YouTube videos | `youtubeembed_preference` | switch | `true` |  |
| Gfycat videos | `gfycat_preference` | switch | `true` |  |
| RedGifs videos | `redgifs_preference` | switch | `true` |  |
| Streamable videos | `streamable_preference` | switch | `true` |  |
| Mixtape.moe videos | `mixtape_preference` | switch | `false` |  |
| Vid.me videos | `vidme_preference` | switch | `true` |  |
| Imgur galleries | `album_preference` | switch | `true` |  |
| Tumblr posts | `tumblr_post_preference` | switch | `true` |  |

#### Browser settings

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Default browser | `default_browser` | list | — | Options: Chrome Custom Tabs (and other supported browsers) → `0`; Internal browser → `1`; Open externally → `2`<br>No XML default, so code decides. |
| Bypass external domains | `bypass_browser` | action | — | _Always open externally_<br>Opens BypassActivity, a list of domains to always open externally ("Add domain"). |

#### Enhancements

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Bypass AMP links | `bypass_amp` | switch | `true` | _When clicked, AMP links will give you the option to bypass_ |

#### Inline linking

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Tap usernames to open | `inline_usernames` | switch | `false` |  |
| Tap subreddits to open | `inline_subreddits` | switch | `false` |  |
| Tap domains to open | `inline_domains` | switch | `false` |  |

#### Link styles

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Inline link sources | `inline_link_sources` | switch | `true` |  |
| Show larger links | `large_link_sources` | switch | `true` |  |
| Expanded comment links | `comments_links_expanded` | switch | `false` |  |
| Expanded selftext links | `posts_links_expanded` | switch | `false` |  |

#### Experimental

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Incognito custom tabs | `incognito_tabs` | switch | — | _This is an experimental feature and requires you to manually enable incognito tabs in Chrome. If you do not enable the flag manually in Chrome the links will open like normal. Be warned this is a little buggy..._<br>No XML default. |
| How do I set Chrome flags? | `help` | action | — | Help link about Chrome flags. |

### History (`cat_history.xml`)

_Root summary: "Marking as read, search & clear history data". preference_ref `HISTORY` = 18._

#### Marking as read

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Mark posts as read | `history_mark_read` | switch | `true` |  |
| Remember viewed across devices | `history_mark_read_reddit` | switch | `true` | _This is a reddit gold feature_<br>**[Reddit Premium/Gold]**: reddit-side gating, not Ultra. |
| Mark posts as read on scroll | `history_mark_read_on_scroll` | switch | `false` |  |
| Mark as read after peek | `viewed_peek` | switch | `false` |  |
| Dim images when viewed | `dim_images` | switch | `fa4lse` | XML default is the typo `fa4lse`. It is not a valid boolean, so it effectively resolves to `false`. |

#### Search

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Record recently searched and visited | `record_history` | switch | `true` |  |

#### Top subreddits

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Record top visited subreddits locally | `record_top_subreddits` | switch | `true` |  |

#### Clear history

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Clear all | `history_all` | action | — |  |
| Clear recents | `history_recents_clear` | action | — |  |
| Clear viewed subreddits | `history_subs_clear` | action | — |  |
| Clear visited subreddit counts | `history_visited_clear` | action | — |  |
| Clear drafts | `drafts_clear` | action | — |  |
| Clear search history | `search_clear` | action | — |  |

## Root › General

### Data options (`cat_data.xml`)

_Root summary: "Data saving, autoplay & preloading". preference_ref `DATA` = 12._

#### Data saving

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Data saving mode | `data_saving` | list | `1` (Automagic (default)) | _Disable loading all thumbnails, card previews and attempt to load smaller resolution videos_<br>Options: Never → `0`; Automagic (default) → `1`; Aggressive → `4`<br>The values skip 2 and 3. |
| Preload Chrome Custom Tabs | `cct_preload_` | switch | `false` | The key has a trailing underscore. |
| Cache comments | `cached_comments` | switch | `false` |  |
| HD Reddit video quality | `reddit_video_quality_new` | list | `2` (Never) | Options: Always → `0`; WiFi only → `1`; Never → `2` |

#### Autoplay setup

> Info text: "GIFs and videos can automatically play in dynamic and full height card modes. By default autoplay is only enabled when on WiFi to save data."

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Autoplay | `autoplay` | list | `1` (WiFi only) | Options: Always → `0`; WiFi only → `1`; Never → `2` |

#### Image preloading (advanced)

> Info text: "When images are preloaded, Sync attempts to grab the image before it needs to be shown on screen to save time and battery. Disabling this setting will not stop images from loading. If you are still having issues with slow loading, try the data saving mode."

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Preload full images | `images_preload` | list | `0` (Always) | Options: Always → `0`; WiFi only → `1`; Never → `2` |
| Preload preview images | `preview_preload` | list | `0` (Always) | Options: Always → `0`; WiFi only → `1`; Never → `2` |
| Preload thumbnails | `thumbs_preload` | list | `0` (Always) | Options: Always → `0`; WiFi only → `1`; Never → `2` |
| Preload GIFs | `gifs_preload` | list | `1` (WiFi only) | Options: Always → `0`; WiFi only → `1`; Never → `2` |

### Filters (`cat_filters.xml`)

_Root summary: "Post filters, advanced & content filters". preference_ref `FILTERS` = 11._

#### Post filters

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Keyword filters | `filters_keywords` | action | — | _Hide posts by title keywords_<br>Opens FilterActivity, a list editor ("Add Filter", "Remove all"). XML sets `defaultValue="true"` on this action row, which does nothing. |
| Domain filters | `filters_domains` | action | — | _Hide posts by domain_<br>Same editor as Keyword filters (meaningless `defaultValue="true"` here too). |
| Subreddit filters | `filters_subreddits` | action | — | _Hide posts by subreddit_<br>Same editor as Keyword filters (meaningless `defaultValue="true"` here too). |
| User filters | `filters_user` | action | — | _Hide posts by users_<br>Same editor as Keyword filters (meaningless `defaultValue="true"` here too). |
| Flair filters | `filters_flair` | action | — | _Hide posts by flair (user and link)_<br>Same editor as Keyword filters (meaningless `defaultValue="true"` here too). |

#### Advanced

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Hide NSFW posts | `nsfw_preference` | switch | `true` |  |
| Selectively hide NSFW previews | `selective_nsfw_previews_preference` | switch | `true` | _Only hide previews on frontpage and all_ |
| Hide all NSFW previews | `nsfw_previews_preference` | switch | `false` |  |
| Hide NSFW previews from the recents app list | `nsfw_recents` | switch | `false` |  |
| Mute NSFW content | `nsfw_mute` | switch | `false` | _Videos will mute when opened if the preview is hidden_ |

#### Content filters

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Show Link posts | `new_filters_content_link` | switch | `true` |  |
| Show Image posts | `new_filters_content_image` | switch | `true` |  |
| Show GIF posts | `new_filters_content_gif` | switch | `true` |  |
| Show Album posts | `new_filters_content_album` | switch | `true` |  |
| Show Video posts | `new_filters_content_video` | switch | `true` |  |
| Show text posts | `new_filters_content_text` | switch | `true` |  |
| Show NSFW posts | `new_filters_content_nsfw` | switch | `true` |  |

### Backup (`cat_backup.xml`)

_Root summary: "Setup & backup / restore". preference_ref `BACKUP` = 17._

#### Setup

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Change backup directory | `backup_dir` | action | — | Folder picker. |

#### Backup and restore

> Info text: "Each of your accounts has its own settings. You will need to backup settings for each account on this device."

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Backup now | `backup_all` | action | — |  |
| Restore from backup | `backup_restore` | action | — |  |

#### Cloud backup and restore (category key `ultra_backup`; **[Ultra]**)

> Info text: "With Sync Ultra you can save your account settings to the cloud and sync across devices with ease."

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Cloud backup now **[Ultra]** | `ultra_backup_all` | action | — | _Create a backup for your current account_ |
| Restore from cloud backup **[Ultra]** | `ultra_backup_restore` | action | — |  |
| Remove cloud backup **[Ultra]** | `ultra_backup_delete` | action | — |  |

## Root › Other

### Restore purchases (`cat_purchases.xml`)

_Root summary: "Restore any previously made purchases". preference_ref `PURCHASES` = 33._

- Widget `PurchasesPreference`: Purchases card (app icon preview + "Restore" button).

### Privacy (`cat_analytics.xml`)

_Root summary: "Privacy policy & crashlytics". preference_ref `ANALYTICS` = 19._

#### Help

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Privacy policy | `licenses_privacy` | action | — |  |

#### Actions

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Crashlytics | `tracking_fabric` | switch | `true` | _Help diagnose issues within the app_ |
| Delete Firebase installation ID | `firebase_delete` | action | — | _Used for crashlytics, purchase validation and remote config_ |
| Revoke GDPR consent for ads | `revoke` | action | — | _For European users using the free version_<br>Ads consent (free version). |

#### Extended privacy (**whole category hidden**)

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Log out on exit **[Ultra]** _(hidden)_ | `ultra_default_to_logged_out` | switch | `false` | _Default to logged out on exit (handy if you don't want to leave your alt signed in...)_<br>The whole category ("Extended privacy") is hidden. |

### Security (`cat_security.xml`)

_Root summary: "Biometric app lock". preference_ref `SECURITY` = 20._

#### Locks

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Biometric login lock | `biometric_start` | switch | `false` | _Prompt when opening the app_ |
| Biometric account lock | `biometric` | switch | `false` | _Prompt when changing account_ |
| Hide username in account picker | `hide_username` | switch | `false` | _Conceal your identity_ |

#### Other

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| DNS over HTTPS | `doh` | switch | `false` | _Use Google DNS resolution for increased data security. May also help if you're having trouble connecting to Reddit_ |
| Incognito keyboard | `incognito_keyboard` | switch | `false` | _Use an incognito keyboard for searches_ |

### Everything else (`cat_other.xml`)

_Root summary: "App info, licenses & credits". preference_ref `OTHER` = 2._

#### App info

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| _(no title)_ | `about_preference` | action | — | No title in XML. Set at runtime (app info/version). |
| Help and support | `feedback_preference` | action | — |  |
| Rate app! | `rate_preference` | action | — |  |
| Licenses | `licenses_preference` | action | — |  |

#### Credits

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Developer | `credit_dev` | action | — | _/u/ljdawson_ |
| Patreon backers | `backers` | action | — | The list comes from remote-config `patreon_backers`. |
| Mod | `mod_0` | action | — |  |
| Mod | `mod_1` | action | — |  |
| Mod | `mod_2` | action | — |  |
| Mod | `mod_3` | action | — |  |
| Mod | `mod_4` | action | — |  |
| Mod | `mod_5` | action | — |  |
| Mod | `mod_6` | action | — |  |
| Icon design | `credit_icon` | action | — | _/u/deadsalas_ |
| Icon design | `credit_icon_2` | action | — | _/u/kimbalim_ |
| Pupper | `pupper` | action | — | _Minnie_ |

## Other / onboarding screens (not linked from `cat_root.xml`)

These screens have no RootPreference pointing at them. Judging by the layouts `viewholder_inner_*` and `fragment_onboarding_*`, the onboarding wizard (`OnboardingActivity`, also launched by **Run setup**) has these pages: a landing page ("Welcome to Sync for Reddit"), toolbar style (expanded vs compact preview), navigation style ("Bottom navigation (new)" / "Drawer navigation (classic)", "Finish setup"), view type, dark mode, settings, tablet, notifications ("Setup"), purchases, terms, and a final page. The resources do not reveal the page order. The four XML-backed pages:

### `cat_onboarding_dark.xml`

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Follow system | `onboarding_dark_automatic` | radio | — | _Automatically change between dark and light mode_<br>Radio group: `onboarding_dark_automatic` / `onboarding_dark_light` / `onboarding_dark_dark`. No XML default. |
| Light mode | `onboarding_dark_light` | radio | — |  |
| Dark mode | `onboarding_dark_dark` | radio | — |  |

### `cat_onboarding_purchases.xml`

- Widget `PurchasesPreference`: Purchases card (app icon preview + "Restore" button).

### `cat_onboarding_settings.xml`

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Show reddit post flair | `onboarding_flair` | switch | `true` | _Display the new style of reddit flair_ |
| Show reddit awards | `onboarding_awards` | switch | `true` | _Display the new style of reddit awards_ |
| Crashlytics | `onboarding_crashlytics` | switch | `true` | _Report crashes automatically_ |

> Info text: "All of the above settings and more can be changed at any point at a later time so don't worry if you're not sure now."

### `cat_onboarding_tablet.xml`

| Title | Key | Control | Default | Summary / options / notes |
|---|---|---|---|---|
| Tablet mode | `onboarding_tablet` | switch | — | No XML default (the main pref `tablet_mode` defaults to `@bool/tablet`). |

## Appendix A: font picker arrays (used by the "… font" action rows)

The XML never references these arrays. They are the only typeface and size arrays in the APK and match the "Title font", "Description font", "Selftext font" and "Comment body font" rows. Fonts bundled in `assets/`: Roboto (Regular/Light/Medium/Bold/Italic), Roboto Slab (+Light), Roboto Condensed (+Bold), Roboto Mono, Product Sans Regular, Google Sans (Medium/Bold/Italic).

- `typeface`: Roboto → `9`; Roboto light → `1`; Roboto medium → `2`; Roboto bold → `3`; Roboto slab → `4`; Roboto slab light → `5`; Roboto condensed → `6`; Roboto condensed bold → `7`; Roboto italic → `8`; System → `10`; Product sans → `11`; Product sans medium → `12`; Product sans bold → `13`; Product sans italic → `14`; Roboto mono → `15`
- `typeface_sizes`: Regular → `5`; Medium → `1`; Large → `2`; Extra large → `3`; Huge → `4`; Header → `6`

## Appendix B: arrays with no preference-XML reference

- `post_sort_frontpage`: What's Hot → `sort_0`; New → `sort_1`; Rising → `sort_2`; Top - Hour → `sort_3_0`; Top - Day → `sort_3_1`; Top - Week → `sort_3_2`; Top - Month → `sort_3_3`; Top - Year → `sort_3_4`; Top - All → `sort_3_5`; Controversial - Hour → `sort_4_0`; Controversial - Day → `sort_4_1`; Controversial - Week → `sort_4_2`; Controversial - Month → `sort_4_3`; Controversial - Year → `sort_4_4`; Controversial - All → `sort_4_5`; Gilded → `sort_5`; Comments → `sort_6`; Best → `sort_999`
- `slide_thumbnail_size`: Large → `0`; Medium → `1`; Small → `2`
  - `post_sort_frontpage` is `post_sort` plus **Gilded** (`sort_5`) and **Best** (`sort_999`). It is probably the frontpage sort menu, which suggests the sort values are shared with the in-feed Sort menu.
- `crypto_fingerprint_fallback_*` and `hide_fingerprint_instantly_prefixes` are AndroidX biometric device quirk lists, not settings.

## Appendix C: keys shared across screens

- `compact_single_lines`: `cat_post_compact_view.xml`, `cat_post_list_view.xml`, `cat_post_smaller_card_view.xml`
- `compact_thumbnails`: `cat_post_compact_view.xml`, `cat_post_list_view.xml`
- `thumbnail_align`: `cat_post_compact_view.xml`, `cat_post_list_view.xml`, `cat_post_smaller_card_view.xml`
- `type_indicators`: `cat_post_compact_view.xml`, `cat_post_list_view.xml`, `cat_post_smaller_card_view.xml`

Also note the legacy theme-engine keys in `strings.xml`: `primary_color_key`, `accent_color_key`, `window_color_key`, `content_color_key`, `highlight_color_key`, `primary_text_color_key`, `secondary_text_color_key`, `sticky_text_color_key`, each with a `*_night` variant. They come from the pre-Monet custom theme system, which has no current settings rows.

## Other notable string resources (feature names that preferences do not cover)

`strings.xml` is nearly empty: 317 lines, almost all library strings. The app inlines its UI text in layouts and menus, so this section draws on `res/menu`, `res/layout` (text and view ids) and `strings.xml`.

**Post "more" bottom sheet** (`dialog_bottom_post_more` ids): Crosspost, Awards, Upvote, Downvote, Save, Hide, Edit, Delete, Replies (send replies to inbox), Flair, Mark as read, Share, Watch, Report, Profile, Subreddit, Explore, External (open in browser), Copy, Translate, Filter, Add to multi.

**Post mod sheet** (`dialog_bottom_post_mod`): Lock, Spam, Remove, Approve, Distinguish, Sticky, NSFW, Spoiler, Flair, Ban, Removal reasons.

**Comment mod contextual menu** (`comments_mod_contextual`): Approve, Remove, Distinguish comment, Ban user, Report Reasons. **Ban screen** (`BanActivity`): Duration, Permanent, Note (to include in ban PM, not visible to the user), Submit.

**Comment row action bar** (`holder_comment`): Upvote, Downvote, Save, Mod, Profile, Reply, Parent, More. **Comment navigation bar** (`view_comments_navigation`): Collapse, Previous, Next, Reply. **Comment search** bottom sheet. **Comment text-to-speech player** (`view_tts`): Stop, Previous, Play, Next, More. **Live/sports thread bar** (`view_sports`): Stop, Refresh.

**Post card/slide buttons** (`holder_card_generic_buttons`): Reply, Upvote, Downvote, Save, Mod, Share, Hide, More, Expand, Stats. **Fullscreen swipe-view buttons** (`fragment_swipe_buttons`, SwipeActivity): Upvote, Downvote, Save, Comments, Download, More.

**Swipe actions.** Posts (`post_swipe`): Upvote, Downvote, Save, Profile, Share, Hide, Mark as read. Comments (`comment_swipe`): Reply, Upvote, Downvote, Save, Profile, Share. Other gestures: swipe to return/close, tap to close, peek (long-press preview), volume-key comment navigation, extended swipe, long-press card to open comments in background.

**Main navigation** (`main_bottom_actions`, `main_rail`): Posts, Explore, Inbox, Actions, plus Settings on the tablet navigation rail. The FAB actions are Submit, Hide Read and More actions.

**Toolbar menus.** Feed (`new_main`): Search, Sort, New message, Mark all as read, More actions, Submit. Comments: Search, Sort, More actions. Image viewer (`image`): View Grid, Download, plus "Extract text" (OCR) in `fragment_image`. Theme screen (`settings`): Share, View palette, Reset, Setup. Filters: Add Filter, Remove all. Bypass list: Add domain. Webview: overflow menu. Tasker: Done.

**Header chips.** Frontpage: Hot (sort), Change view, Top growing, Random. Subreddit: Hot, Join, About, Wiki, Flairs. Multi/friends: Hot, About. Profile: Hot, About, Message, Trophies, Paint user, Follow, Friend, Block, Share. Domain/search/seen: Hot, Options. Watching: Clear all. Messaging: New message, Mark all as read. Search also has "Search all subs" and "Search subs".

**Editor and submit.** Formatting menu (`style_menu`): Cut, Copy, Paste, Bold, Italic, Strikethrough, Quote, Link. Composer: "Type a comment", Send. Insert or upload image ("Choose photo" / "Take photo", with image cropper). Submit form (`fragment_submit`): Title, URL, Suggest title, Choose photo / Take photo, Flair, NSFW, Spoiler, Send replies to my inbox. Drafts list (`fragment_drafts`).

**Account and other screens.** The account picker has Add account, Upgrade, Ultra, Profile, Mod, Settings and Info entries, and can hide the username. Also present: Recents, Saved, Wiki, Rules, Sidebar, Go-to subreddit / custom feed input, Mod queue (`ModActivity`; "Reports"), Messaging (`MessagingActivity`), Award picker (`fragment_award`), Patreon backers, Theme export, Meme/image bottom sheet, Markdown table viewer (`fragment_table`), Link/site preview bottom sheet (Ultra website previews), and Ultra paint/tag managers.

**Offline sync** (`dialog_sync`): "Sync content for offline viewing" with Pages (default 5). The **Tasker/Locale plugin** (`tasker` layout, `tasker.EditActivity`/`FireReceiver`) syncs a Subreddit with Pages and an optional "Sync comments".

**Notifications.** "Reddit Push notifications" mirrors the official app's notifications through a notification listener. FCM's default channel id is `sync_ultra`. Other notification texts: ExoPlayer "Downloads" channel (`exo_download_notification_channel_name`) and FCM fallback "Miscellaneous". The receivers indicate notification actions: Mark message as read, Reply to message (`MessageReplyReceiver`), Cancel watch (`CancelWatchReceiver`), and download Retry / Delete / Share / Cancel. A watched-subreddit worker exists ("Check watched subreddits worker").

**User-visible messages in `strings.xml`.** "Your filters have blocked posts from loading". "NSFW filter enabled (Settings > Filters)". "Restore position?". "This sub has no posts", "This user has no posts", "Private sub". "This is an archived post…". "Viewing a single thread" / "View post". Subscribe / Unsubscribe. Message mods. Reports. Hidden / Removed / Restore. "Link copied to clipboard". Errors: offline, too many requests, invalid token, must be logged in.

**Monetisation.** AdMob and Amazon (APS/DTB) ads, including native ads and gallery ads labelled "Sponsored". `RemoveAdsActivity`. GDPR consent revoke. PiracyChecker/LVL licence check. Play Billing 5.1.0. Ultra subscriptions (monthly / yearly / lifetime).

**Launcher icons** (activity-aliases): Default, DefaultDark, Legacy, Fancy, Pride, BLM.

## AndroidManifest.xml

**App activities** (short names, package `com.laurencedawson.reddit_sync.ui.activities` unless noted):
HomeActivity (target of the launcher activity-aliases; not exported itself; `noHistory`, splash theme), MainActivity, CommentsActivity, CasualActivity, CasualGalleryActivity, SwipeActivity, PreferencesActivity (exported, `sync-settings:` scheme), PreferencesSearchActivity, ModActivity, MessagingActivity, WebViewActivity, OnboardingActivity, RemoveAdsActivity, media.SingleImageActivity, media.MultiImageActivity, CustomYouTubeVideoActivity, FilterActivity, BypassActivity, BanActivity, SubmitActivity (share target), IntentActivity (label "Sync link handler"), `tasker.EditActivity` (Locale/Tasker plugin), TestActivity, TestDialogActivity, TestMarkdownActivity. That is 25 activities.

The launcher activity-aliases of HomeActivity are Default (enabled), DefaultDark, Legacy, Fancy, Pride and Blm. All except Default are disabled and get switched by the "Launcher icon" preference.

Third-party activities: AdMob AdActivity / OutOfContextTestingActivity, Amazon AdActivity / DTBInterstitialActivity / DTBAdActivity / ApsInterstitialActivity, Android-Image-Cropper `CropImageActivity`, PiracyChecker `LicenseActivity`, AndroidX `DeviceCredentialHandlerActivity` (biometric), GMS `GoogleApiActivity`, Play Core activities, Billing `ProxyBillingActivity`.

**App shortcuts.** None static: there is no `android.app.shortcuts` meta-data and no shortcuts XML. The "Show top visited subs icon shortcuts" pref (`icon_shotcuts`) implies dynamic shortcuts created in code.

**Widgets.** None. There is no `APPWIDGET_UPDATE` receiver and no `appwidget-provider` XML.

**App receivers** (package `…reddit_sync.receiver` unless noted): MessageReceiver (BOOT_COMPLETED), WatchReceiver (BOOT_COMPLETED), MarkMessageAsReadReceiver, MessageReplyReceiver, CancelWatchReceiver, ProfileReceiver, ThemeReceiver, MonetReceiver, SystemMonetThemeReceiver, ImageDownloadErrorReceiver, DownloadSyncReceiver, download.CancelledReceiver, download.ShareReceiver, download.RetryReceiver, download.DeleteReceiver, `service.SettingChangeReceiver`, SubmitCommentBroadcastReceiver, and `tasker.FireReceiver`. The last is exported, handles `com.twofortyfouram.locale.intent.action.FIRE_SETTING`, and runs in the `:background` process.

**App services and providers.** `service.SyncService` (offline sync). `service.AllNotificationService`, a NotificationListenerService behind "Reddit Push notifications". `service.MyFirebaseMessagingService` (FCM; default channel `sync_ultra`). `provider.RedditProvider` (not exported). The FileProvider `…reddit_sync.provider` for sharing images. Background jobs run on WorkManager (multiprocess).

**Intent filters, schemes and hosts handled:**
- `IntentActivity`, VIEW + BROWSABLE. Its `<data>` entries merge, so every listed scheme pairs with every listed host:
  - schemes: `sync`, `http`, `https`
  - hosts: `reddit.com`, `www.reddit.com`, `m.reddit.com`, `old.reddit.com`, `np.reddit.com`, `amp.reddit.com`, `redd.it`, `v.redd.it`, `imgur.com`, `i.imgur.com`, `gfycat.com`, `i.reddituploads.com`, `g.redditmedia.com`, `i.redditmedia.com`
- `IntentActivity`, VIEW + BROWSABLE: `https://www.google.com/amp/s/amp.reddit.com/r/…` and `…/amp/s/amp.reddit.com/comments/…` (Google AMP wrapper links).
- `IntentActivity`, SEND `text/plain` (share a link into Sync).
- `SubmitActivity`, SEND `text/plain` and SEND `image/*` (share to create a post).
- `PreferencesActivity`, VIEW + BROWSABLE, scheme `sync-settings` (deep link into settings).
- `tasker.EditActivity`: `com.twofortyfouram.locale.intent.action.EDIT_SETTING`.
- `<queries>`: Custom Tabs service, YouTube API service, GET_CONTENT `image/*`, IMAGE_CAPTURE, `https` browsers, Play Billing.

**Notable permissions and meta-data.** USE_BIOMETRIC / USE_FINGERPRINT (app locks). POST_NOTIFICATIONS. RECEIVE_BOOT_COMPLETED. FOREGROUND_SERVICE. VIBRATE (swipe haptics). BILLING. CHECK_LICENSE. AD_ID. ML Kit dependencies `ocr,langid` plus NL Translate (Ultra translate and select-text). `allowBackup=false`, so backup is the app's own feature only. `largeHeap=true`.
