# 03 — Screens

One section per board on the [design canvas](https://claude.ai/artifact/XrLDLiYD9C4ic1fQdtZCWd). The boards are drawn at 1280×720, which maps 1:1 to **dp** on a 1080p TV (Android TV reports 960×540 dp at xhdpi, so build to the canvas proportions using `BoxWithConstraints` rather than hard pixels; see [04](04-design-system.md#sizing)).

Common to every screen with a rail: `Rail.dc.html` is on the left (72 dp), D-pad Left from the first column enters the rail, and Right leaves it. The rail items are Home, Live TV, Guide, Movies, Series, Favourites, Search, with Settings pinned to the bottom.

Key legend: **OK** = DPAD_CENTER/ENTER, **Back** = BACK/ESC, **◀▶▲▼** = D-pad.

---

## 01 Add Xtream playlist — `Login.dc.html`

**Purpose:** first run, and also opened from Settings → "+ Add playlist".
**Layout:** left is the brand mark, headline, explainer and an *Xtream Codes / M3U URL* tab switch. Right is a form card with Playlist name, Server URL (+ hint "Include the port"), Username and Password side by side, the primary button "Sign in & load playlist", and the privacy note.
**Data:** `Xtream(account).login()`.
**D-pad:** focus starts on Server URL. ▲▼ move between fields, and ◀▶ moves between Username and Password. OK on a field opens the IME. OK on the button submits.
**Touch:** standard text fields. IME action Next/Done.
**States:** idle, submitting (button shows a spinner and the fields are disabled), error (the field turns into the error input from board 17, with the message below the button: "Wrong username or password" / "That doesn't look like an Xtream server" / "Account is Expired" / network error).
**Accept:** a valid login saves the playlist and goes to 02. An invalid one saves nothing. Server input without a scheme gets `http://`. Trailing slashes are fine. The M3U tab can show "Coming soon" (M3U is out of scope for v1).

## 02 Loading playlist — `Sync.dc.html`

**Purpose:** first load and manual refresh of a playlist.
**Layout:** a title with the server, an overall progress bar, and a step list (Signing in → Live channels → Movies → Series → TV guide). Each step shows a status dot (done ✓ green / busy amber / todo grey) and a count. A Cancel button.
**Data:** live/vod/series catalog calls in parallel, then xmltv. Counts come from list sizes, and "Active · expires …" from `user_info`.
**D-pad:** only Cancel (and Retry/Skip on a failed step) is focusable.
**States:** step failed → red dot, error text, Retry / Skip. When everything is done, go to 03 automatically.
**Accept:** the TV guide step runs after the catalogs and doesn't block entry. If it's still running, go to Home and finish in the background.

## 03 Home — `Home.dc.html`

**Purpose:** landing hub. Rail item: Home.
**Layout:** header with playlist name + expiry, greeting and clock. Three big tiles (Live TV / Movies / Series with counts), with the focused tile filled. A "Continue watching" row (16:9 stills with a progress bar, title, meta). A "Recent channels" row (logo + name chips) with an "All live TV" link.
**Data:** catalog counts, resume store (most recent 10), recent channels (12).
**D-pad:** ▲▼ move between rows, ◀▶ within a row. OK on a tile opens 04/07/10, on a resume card opens the player at the saved position, on a channel opens 06.
**States:** no resume history → hide that row. No recents → hide that row.
**Accept:** the greeting follows the time of day (morning/afternoon/evening). The clock updates every minute.

## 04 Live TV — channel browser — `Main.dc.html`

**Purpose:** TiviMate's main screen. Rail item: Live TV.
**Layout:** four columns: rail | **Groups** (220 dp: Favourites, All channels, then provider categories, each with a count) | **Channels** (number, logo, name, time left, now title, progress bar) | **Preview** (live video 16:9 with a LIVE badge and resolution chip, NOW time + title + description, the next 3 programmes, and a key-hint strip "OK Watch · Hold OK Favourite · ◀ Groups").
**Data:** categories, `channelsByCategory`, favourites, short EPG for visible rows, `PlayerHost` for the preview.
**D-pad:**
- Groups: ▲▼ changes the highlighted group and **reloads the channel list right away** (no OK needed). ▶ goes to channels.
- Channels: ▲▼ moves focus and the preview pane info follows. **OK once** plays in the preview. **OK on the channel already previewing** goes fullscreen (06). Hold OK → context menu (18). ◀ → groups.
- CHANNEL_UP/DOWN anywhere: move focus by a page.
**Touch:** tap a channel to preview, tap again or tap the preview to go fullscreen, long-press for the menu.
**States:** loading skeleton rows (use `hint-placeholder` style blocks). Empty group → empty state (19). No EPG → "No programme info".
**Accept:** the selected group is a raised fill and the focused channel a light fill, with **no outlines or side bars**. The preview starts within about 1.5 s on a good stream. Returning from fullscreen restores focus to the same channel.

## 05 TV Guide (EPG) — `Guide.dc.html`

**Purpose:** timeline grid. Rail item: Guide, or the GUIDE remote key.
**Layout:** header (preview video, focused programme channel + time, title, description, chips Catch-up / HD, and a primary "Remind me" button), time ruler (30-min slots, date label), rows = channel column (number, logo, name) + programme cells sized by duration, a vertical "now" line in the accent colour.
**Data:** xmltv cache (or short EPG fallback), channels in the current group (it remembers the group from 04).
**D-pad:** ◀▶ moves between programmes (scrolling the ruler past the edges), ▲▼ moves between channels and keeps the focused time position. OK on a current programme plays fullscreen. OK on a past programme with `tv_archive` plays catch-up. OK on a future one toggles the reminder. Back returns. Hold OK → menu.
**Touch:** two-axis scroll, tap to focus, tap again to act.
**States:** focused cell = light fill. Airing now = `#1A2030`. Others = `#11151F`. No data for a channel → one full-width cell "No programme info".
**Accept:** the grid covers −2 h…+24 h. The now line moves every minute. Scrolling 500 channels stays at 60 fps (use `LazyColumn` rows + a shared horizontal scroll state).

## 06 Live player + info bar — `Player.dc.html`

**Purpose:** fullscreen live.
**Layout:** video full screen, clock chip top right, bottom info bar: channel number, logo, channel name, programme title, chips (resolution, audio, subtitles), a start–end progress bar, "Next 21:00 · …", and buttons Audio / Subtitles / Favourite / **Channel list** (primary).
**D-pad:** the info bar shows on any key and hides after 5 s. ▲▼ / CH± zap (show the bar for the new channel, with a 600 ms debounce before tuning). OK toggles the bar, and when it's visible, buttons are focusable with ◀▶. ◀ opens a slim channel-list overlay of the current group (TiviMate behaviour). Back hides the bar first, then returns to 04.
**Touch:** tap toggles the bar, swipe ▲▼ zaps, swipe ◀ opens the list.
**States:** buffering spinner centred. Stream error → overlay (19).
**Accept:** zapping back and forth between two channels doesn't leak players. See [05-player](05-player.md#channel-zapping).

## 07 Movies — `Movies.dc.html`

**Purpose:** VOD grid. Rail item: Movies.
**Layout:** rail | categories (Continue watching, All movies, Recently added, provider categories with counts) | grid header (category title + count, sort buttons Recently added / A–Z / Rating) | 6-column poster grid (2:3 poster, rating badge, title overlay, year · runtime).
**Data:** vod categories + streams. Posters load from `stream_icon`.
**D-pad:** groups behave as in 04 (▲▼ switches the grid right away). The grid is 2D, and OK opens 08. Sort buttons sit above the grid.
**States:** focused poster card = light fill around the poster. Loading = grey poster placeholders. Broken image = tone block + title (as drawn).
**Accept:** a grid of 8,000 items scrolls smoothly (`LazyVerticalGrid`, image size limited to the cell). Sort is applied in memory.

> Image loading needs a library. Add **Coil 3** (`io.coil-kt.coil3:coil-compose`) here. It's the one dependency worth adding for poster/logo caching.

## 08 Movie detail — `MovieDetail.dc.html`

**Layout:** backdrop top right, poster, rating badge, year · runtime · genres · certificate, title, plot, director/cast, buttons **Resume 1:12:40** (primary light) / From start / Trailer / ☆ favourite, a watched progress bar, and a "More like this" row (same category).
**Data:** `get_vod_info`, resume store.
**D-pad:** focus starts on Resume (or Play if there's no resume). ◀▶ moves across the buttons. ▼ goes to More like this. Back returns to the grid with focus restored.
**States:** no resume → the primary button reads "Play" and "From start" is hidden. No trailer → hide that button. Info `[]` → show only what the list item had.
**Accept:** Resume seeks to the saved position minus 5 s.

## 09 Movie / episode player — `VodPlayer.dc.html`

**Layout:** top bar (Back, series · season, episode title, clock), bottom: seek bar (buffered + played + thumb), elapsed / −remaining, left buttons Audio · Subtitles · Speed, centre −10 / Play-Pause / +30, right Episodes / Fit-Zoom. A "Next episode in 12 s" card with a Play button (episodes only).
**D-pad:** ◀▶ when controls are hidden = seek −10/+30 and show the bar. OK = play/pause. ▲ shows controls. While the seek bar is focused, ◀▶ scrubs and holding accelerates. Back hides controls first, then saves the position and exits.
**Accept:** the position is saved every 10 s and on exit. More than 95 % watched → marked watched and removed from Continue watching. Next-episode card at 20 s before the end (or the credits); the countdown auto-plays when the setting is on.

## 10 Series — `Series.dc.html`

Same component as 07 (`VodGridScreen(kind = Series)`): series categories, poster = `cover`, meta "N seasons · year". OK opens 11. Rail item: Series.

## 11 Series detail — seasons & episodes — `SeriesDetail.dc.html`

**Layout:** left panel (poster, title, rating, years, season count, genre, plot, **Resume S2 · E5** primary, ☆), right: season pills (selected = accent fill) and an episode list (16:9 still with a progress strip, "Episode N · 52 min", title, one-line plot, state "Watched" / "13 min left").
**Data:** `get_series_info`, resume store.
**D-pad:** focus starts on Resume. ▶ goes to the pills, and ▼ to the episodes. Moving between pills switches the season right away. OK on an episode plays it in 09.
**Accept:** opens on the season of the last watched episode, with the focused episode = the next unwatched one.

## 12 Search — `Search.dc.html`

**Layout:** rail | keyboard panel (query field, 6×6 A–Z 0–9 grid, Space / Delete / Clear, voice tip) | results grouped as Channels (chips), Series (posters), Movies (posters), each with a count.
**Data:** in-memory filter over channels, movies and series names (case-insensitive and accent-insensitive with `Normalizer`), debounced 250 ms, at most 30 per group.
**D-pad:** the on-screen keyboard is the default on TV. ▶ from the keyboard goes to results. The SEARCH remote key or mic → `RecognizerIntent` if available.
**Touch:** tapping the field opens the system IME, and the on-screen keyboard is hidden when there's a touchscreen.
**States:** empty query → recent searches. No results → empty state (19).

## 13 Favourites — `Favourites.dc.html`

**Layout:** title, Reorder / Remove buttons, tabs Channels · Movies · Series (with counts), 3-column cards (channel: logo, name, now title, progress).
**D-pad:** OK plays or opens. In Reorder mode, OK picks up the item, the D-pad moves it and OK drops it. In Remove mode, OK removes it (with a 3 s undo snackbar).
**Accept:** the order persists per playlist, and the Favourites group in 04 uses the same order.

## 14 Settings — playlists — `Settings.dc.html` (section = playlists)

**Layout:** rail | section list (Playlists, Player, TV guide, Appearance, Parental control, Remote & buttons, Updates & about with a badge when an update exists, and the version at the bottom) | content: "+ Add playlist", the active playlist card (green dot, name, "In use", server, a stats grid Status / Expires / Connections / Last refreshed, buttons Refresh now / Edit / Delete), other playlists with "Switch to", then rows "Refresh playlist on start" (toggle) and "Refresh TV guide every" (value).
**Rows** use the `SettingRow.dc.html` component: label, optional description, then either a toggle or a value with ›. OK on a value row opens a picker dialog (18).
**Accept:** Delete asks for confirmation (18) and removes that playlist's caches. Switching playlist reloads the catalog from its cache.

## 15 Settings — player — `SettingsPlayer.dc.html`

Rows: Decoder (Hardware / Software), Buffer size (Small / Medium / Large), Live stream format (MPEG-TS / HLS), Default aspect ratio (Fit / Fill / Zoom / 16:9 / 4:3), Preferred audio language, Subtitles on by default, Play next episode automatically. See [05-player](05-player.md#settings-mapping) for the effect of each.

Other sections that aren't drawn yet use the same `SettingRow` list:
- **TV guide:** xmltv URL override, refresh interval, time offset (−12…+12 h), days to keep.
- **Appearance:** accent colour (the 4 swatches from the design tweak), clock 12/24 h, show channel numbers.
- **Parental control:** PIN set/change, locked groups (multi-select), lock adult categories automatically.
- **Remote & buttons:** CH± direction, OK behaviour (preview first vs fullscreen at once), long-press action.

## 16 Settings — updates (GitHub) — `SettingsUpdates.dc.html`

**Layout:** an update card (badge "Update available", release date + size, "Version 0.2.0", bulleted release notes, **Download & install** / Release notes, "You're on 0.1.0"), then rows: Check for updates automatically, Include beta releases, Update source `github.com/OWNER/REPO`.
**States:** up to date → card reads "You're up to date" with **Check now**. Downloading → progress bar in the card. Failed → error text + Retry.
**Accept:** see [06-github-updates](06-github-updates.md).

## 17 Components — `Components.dc.html`

Reference sheet, not a screen: colour swatches, type scale, the focus rule (Idle / Selected / Focused), Buttons (primary, secondary, destructive, icon) × (default, focused, disabled), inputs (default, focused, error), toggles, tab switch, season pills, badges (LIVE, rating, HD, Catch-up, In use), progress, key hint. Each item maps to a composable in `ui/Components.kt` ([04](04-design-system.md#component-inventory)).

## 18 Dialogs & menus — `Dialogs.dc.html`

- **Channel context menu** (Hold OK on a channel or programme): Add to / Remove from favourites, Play in preview, Open guide here, Catch-up (if archive), Hide channel, Lock channel.
- **Confirm dialog:** title, body, a destructive primary and Cancel, with focus defaulting to **Cancel**.
- **Picker dialog:** a single-choice list for value rows (e.g. Buffer size), with a radio dot on the current value.
- **PIN entry:** 4 digits, number-pad grid for D-pad, used by parental control.
- **Exit app:** "Exit Relay IPTV?" on Back at Home.
- **Update available** (auto-check on start): version, notes, Install / Later / Skip this version.

All dialogs are centred cards on a `#0B0E14` scrim at 80 %. They trap focus, and Back closes them.

## 19 Errors & empty states — `States.dc.html`

| State | Where | Content / actions |
|---|---|---|
| Login failed | 01 | Inline error input + message |
| Server unreachable | 02, refresh | "Can't reach provider.example" · Retry · Use cached data |
| Account expired | 02/03 | "Your subscription ended on …" · Edit playlist |
| Stream error | 06, 09, preview | "This channel isn't playing" + reason · Retry · Switch to HLS/TS · Back |
| Max connections | player | "Too many devices are using this account" |
| Empty group / favourites | 04, 13 | Icon + one line + a hint ("Hold OK on any channel to add it") |
| No search results | 12 | "Nothing matches 'xyz'" |
| No EPG | 04, 05 | Muted "No programme info" in place of the title |
| Offline | anywhere | Top banner "You're offline. Showing saved channels." |
