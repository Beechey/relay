# 01 — Architecture

Keep it a single `:app` module with flat packages. Add layers only when a file outgrows them.

## Package layout

```
com.avetero.relay
├── MainActivity.kt          // setContent, back handling, holds AppState
├── AppState.kt              // screen stack, active playlist, catalog, favourites
├── Xtream.kt                // API client + parsers (exists)
├── Store.kt                 // SharedPreferences + JSON cache files
├── Updater.kt               // GitHub release check + APK install
├── player/
│   └── PlayerHost.kt        // one ExoPlayer, attach/detach to surfaces
├── ui/
│   ├── Theme.kt             // tokens from 04-design-system.md
│   ├── Components.kt        // FocusSurface, buttons, rows, toggles, rail
│   ├── LoginScreen.kt       // 01
│   ├── SyncScreen.kt        // 02
│   ├── HomeScreen.kt        // 03
│   ├── LiveScreen.kt        // 04
│   ├── GuideScreen.kt       // 05
│   ├── LivePlayerScreen.kt  // 06
│   ├── VodGridScreen.kt     // 07 + 10 (movies/series share one grid)
│   ├── MovieDetailScreen.kt // 08
│   ├── VodPlayerScreen.kt   // 09
│   ├── SeriesDetailScreen.kt// 11
│   ├── SearchScreen.kt      // 12
│   ├── FavouritesScreen.kt  // 13
│   ├── SettingsScreen.kt    // 14–16
│   └── Dialogs.kt           // 18, 19
```

## Navigation

- Use a hand-rolled screen stack, `mutableStateListOf<Screen>()`, with `Screen` as a sealed class (`Home`, `Live(groupId)`, `Guide`, `LivePlayer(channel)`, `MovieDetail(id)`, …). Do **not** add navigation-compose. Nothing here needs deep links or saved-state graphs.
- `BackHandler` pops the stack. Back on the root screen (Home) asks "Exit?" with dialog 18.
- The nav rail (board `Rail.dc.html`) **replaces** the stack root rather than pushing onto it.

## State

- `AppState` is a plain class of `mutableStateOf` fields, created once in `MainActivity` and passed down. A single activity covers the whole lifecycle, and `android:configChanges` stops recreation, so a ViewModel isn't needed.
- Catalog: `categories`, `channels`, `vodCategories`, `movies`, `seriesCategories`, `series` are `List`s. Build index maps (`channelsByCategory`) once after load.
- EPG: `mutableStateMapOf<Int, List<Programme>>()` keyed by stream id and filled lazily (see caching).
- Each screen keeps UI-only state (focused index, scroll) with `remember` / `rememberLazyListState`.

## Data flow

```
Login ─► Xtream.login() ─► Store.savePlaylist()
     └─► Sync: categories+streams for live/vod/series in parallel ─► Store.writeCache() ─► AppState
Home/Live/… read AppState ─► visible rows trigger EPG fetch ─► AppState.epg
Play ─► PlayerHost.play(url) ─► surface in preview pane or fullscreen
```

## Storage

| What | Where |
|---|---|
| Playlists (name, server, user, pass), active id | `SharedPreferences("playlists")` as a JSON array string |
| Settings (decoder, buffer, format, toggles) | `SharedPreferences("settings")` |
| Favourites (channel/movie/series ids, order) | `SharedPreferences("favs_<playlistId>")` |
| Resume positions | `SharedPreferences("resume_<playlistId>")`: key `m<id>` / `e<id>` → ms, plus a recent list for "Continue watching" |
| Recent channels | `SharedPreferences`: last 12 stream ids |
| Catalog cache | `filesDir/cache_<playlistId>/{live,vod,series}.json`: the raw API responses |

App-private storage holds the password in plain text, as TiviMate does. `shortcut: plain prefs, move to EncryptedSharedPreferences/Keystore if we ever store anything beyond provider logins.` Only add Room if search or EPG queries become slow (more than 50k rows).

## Caching

- **Catalog.** On start, render from the cache files at once, then refresh in the background when the cache is older than the "Refresh playlist" setting (default: on start). A failed refresh keeps the cache and shows a snackbar instead of an error screen.
- **Short EPG.** Fetch `get_short_epg&limit=6` for each channel as it scrolls into view. Keep results in memory with a fetched-at time and refetch once `now > last programme end` or after 30 minutes.
- **Full guide (05).** Download `xmltv.php` on the "Refresh TV guide every" interval (default 12 h). Stream-parse it with `XmlPullParser` into a compact per-channel list for −2 h…+24 h, and write that to `filesDir/epg_<playlistId>.json`. Fall back to short EPG when xmltv fails or is too large.

## Threading

- Coroutines only: `rememberCoroutineScope()` / `LaunchedEffect` in UI, `withContext(Dispatchers.IO)` around every `Xtream` call (they block).
- Sync runs the 3 catalog fetches at once (`async`). Cap EPG fetches at 4 in flight with a `Semaphore(4)`, because panels rate-limit.
- Never parse large JSON on Main. `get_vod_streams` can be over 20 MB.

## Error handling

| Failure | Behaviour |
|---|---|
| Login: bad creds / not Xtream / expired / network | Inline error under the form (board 01, input error state in 17); nothing is saved |
| Sync step fails | That step goes red in board 02 with Retry and Skip; the others continue |
| Stream won't play | Player error overlay (board 19): Retry, Switch format (TS↔HLS), Back |
| EPG missing | Row shows "No programme info". Never block the UI |
| Update check fails | Silent unless the check was manual, in which case toast "Couldn't reach GitHub" |

Every IOException message is user-facing. `Xtream.kt` already throws readable messages.

## Focus / D-pad strategy

- One reusable `FocusSurface` composable ([04](04-design-system.md#compose-notes)) renders idle, selected and focused states and is the only clickable primitive. That one composable keeps the focus rule consistent.
- Columns (rail → groups → channels → preview) are focus groups (`Modifier.focusGroup()`). Moving left or right enters the **last focused** child with `focusRestorer()`, so returning to channels lands on the same row.
- On first composition, request focus for the item that makes sense: the playing channel in Live, the first poster in grids, the server field in Login.
- `LazyColumn`/`LazyVerticalGrid` with D-pad: keep the focused item scrolled into view with `BringIntoViewRequester`, and avoid `animateItem` on lists of 1000+ items.
- Long-press OK = context menu (board 18) via `combinedClickable(onLongClick=…)`.
- Remote keys: CHANNEL_UP/DOWN zap, MEDIA_PLAY_PAUSE, MEDIA_FAST_FORWARD/REWIND, GUIDE opens 05, MENU opens the context menu, SEARCH opens 12.
- **Touch:** each `FocusSurface` is also a tap target (48 dp minimum). The rail stays visible on tablets, and on phones (width under 600 dp) it becomes a bottom bar (Home, Live, Movies, Series, More). Swipe up/down in the player zaps channels.
