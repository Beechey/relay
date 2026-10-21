# 07 — Roadmap

Boards are referenced by number from the [canvas](https://claude.ai/artifact/XrLDLiYD9C4ic1fQdtZCWd) and specced in [03-screens](03-screens.md). Each milestone ends with a release build installed on a real Android TV box.

## M0 — Toolchain & build

- [ ] Install JDK 17 (`brew install --cask temurin@17`) and point Gradle at it (`org.gradle.java.home` in `~/.gradle/gradle.properties`, or `JAVA_HOME`)
- [ ] Install Android command-line tools (`brew install --cask android-commandlinetools`), then `sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"`, then `sdkmanager --licenses` (accept the licences yourself)
- [ ] `local.properties` with `sdk.dir=…`
- [ ] Gradle wrapper 8.11.1 (`brew install gradle && gradle wrapper --gradle-version 8.11.1`), and commit `gradlew` + `gradle/wrapper/*`
- [ ] `./gradlew :app:testDebugUnitTest` passes (`XtreamTest`)
- [ ] `./gradlew :app:assembleDebug` produces an APK. Install with `adb connect <tv-ip>` + `adb install`
- [ ] `git init`, `.gitignore` (exists), first commit, create a GitHub repo (public, for updates)
- [ ] Emulator: Android TV (1080p) AVD for quick checks

## M1 — Login, Live TV, player (the MVP)

- [ ] `ui/Theme.kt`: tokens + bundled Manrope / JetBrains Mono fonts. **Board 17** · [04](04-design-system.md)
- [ ] `ui/Components.kt`: `FocusSurface`, `Btn`, `Field`, `Badge`, `Progress`, `KeyHint`, `Rail`. **17**, `Rail.dc.html`
- [ ] `Store.kt`: playlists in prefs, catalog cache files
- [ ] `AppState.kt` + screen stack + `BackHandler` in `MainActivity.kt`
- [ ] `LoginScreen.kt`, including the error states. **01**, **19**
- [ ] `SyncScreen.kt`, live catalog only for now. **02**
- [ ] `LiveScreen.kt`: groups, channels, preview pane, short EPG per visible row. **04**
- [ ] `player/PlayerHost.kt`: single ExoPlayer, preview ↔ fullscreen handoff. [05](05-player.md)
- [ ] `LivePlayerScreen.kt`: info bar, zapping with debounce, error overlay. **06**, **19**
- [ ] Favourites toggle via long-press (simple menu). **18**
- [ ] Tests: `parseCategories`, a `parseChannels` string/int mix, a `parseEpg` base64 edge case, `AppState` group filtering

## M2 — Guide / EPG

- [ ] `Epg.kt`: xmltv streaming parser (`XmlPullParser`) → per-channel lists for −2 h…+24 h → JSON cache. [02](02-xtream-api.md#epg)
- [ ] Sync step "TV guide" running in the background. **02**
- [ ] `GuideScreen.kt`: ruler, rows, now-line, header preview. **05**
- [ ] Catch-up playback via the timeshift URL for `tv_archive` channels. **05**, [05-player](05-player.md#live-vs-vod)
- [ ] Reminders: `AlarmManager` + a heads-up notification "Starting now on …" (opens the channel)
- [ ] Tests: xmltv time parsing with offsets, mapping `epg_channel_id` to stream, short-EPG fallback

## M3 — Movies & Series

- [ ] Add Coil 3 for posters and logos (the only new dependency)
- [ ] `Xtream.kt`: vod/series categories, streams, `get_vod_info`, `get_series_info` (including the `episodes` object-or-array quirk). [02](02-xtream-api.md#movies-vod)
- [ ] Sync: movies + series steps. **02**
- [ ] `VodGridScreen.kt` (shared). **07**, **10**
- [ ] `MovieDetailScreen.kt`. **08**
- [ ] `SeriesDetailScreen.kt`. **11**
- [ ] `VodPlayerScreen.kt`: seek, tracks, resume save, next-episode card. **09**
- [ ] `HomeScreen.kt`: tiles, Continue watching, Recent channels. **03**
- [ ] Tests: VOD URL with `container_extension`, the series episodes quirk, resume / watched thresholds

## M4 — Search, Favourites, Settings

- [ ] `SearchScreen.kt` with an on-screen keyboard and voice. **12**
- [ ] `FavouritesScreen.kt` with Reorder / Remove modes. **13**
- [ ] `SettingsScreen.kt`: playlists (add / edit / switch / delete / refresh), player, TV guide, appearance, parental, remote. **14**, **15**, `SettingRow.dc.html`
- [ ] Dialogs: confirm, picker, PIN, exit, context menu. **18**
- [ ] Parental lock: PIN stored as a salted SHA-256 hash, locked groups hidden until unlocked
- [ ] Wire player settings into `PlayerHost`. [05](05-player.md#settings-mapping)
- [ ] Tests: search normalisation, favourites order persistence

## M5 — GitHub updates

- [ ] `Updater.kt`: release check, semver compare, download with progress, install intent. [06](06-github-updates.md)
- [ ] Manifest: `REQUEST_INSTALL_PACKAGES`, FileProvider, `update_paths.xml`
- [ ] Settings → Updates card + rows, badge on the section. **16**
- [ ] Start-up "Update available" dialog with Skip this version. **18**
- [ ] Release keystore + `.github/workflows/release.yml` + signing config from env
- [ ] Tests: `compareVersions("0.10.0","0.9.0")`, prerelease handling, picking the APK asset
- [ ] End-to-end: install v0.1.0 → publish v0.1.1 → update in-app → settings are kept

## M6 — Polish & TV QA

- [ ] Phone / tablet layout: bottom bar under 600 dp, single-column live list, touch gestures in the player
- [ ] Offline banner and cached start-up. **19**
- [ ] Performance: 10k channels, 20k movies, a 300 MB xmltv. Cold start under 2 s from cache. Check memory on a 1 GB box
- [ ] Accessibility: TalkBack labels on icon buttons (from `aria-label`s in the boards), contrast verified
- [ ] Device matrix: Chromecast with Google TV, Fire TV Stick (Fire OS 7/8), Nvidia Shield, a generic Amlogic box, a phone, a tablet
- [ ] App icon / banner final art

## Test plan

| Layer | How |
|---|---|
| Parsers, URL builders, version compare | JVM unit tests (`app/src/test`), `org.json:json` on the test classpath |
| Screen logic (filtering, focus targets, zap order) | Plain-Kotlin unit tests on `AppState` |
| UI | Manual D-pad pass per board on a TV emulator plus one real box each milestone. A Compose UI test for login and channel zap only (M6) |
| Provider compatibility | At least 2 real Xtream panels (different vendors). Record anonymised JSON responses as test fixtures |
| Updates | The end-to-end checklist in M5 on a real device |

Never commit real provider credentials. Keep test logins in `local.properties` (gitignored) and read them via `BuildConfig` in debug only.

## Known risks

| Risk | Mitigation |
|---|---|
| Panels vary wildly (string/int fields, `[]` vs `{}`, missing EPG) | Defensive parsers + fixtures from several panels ([02 quirks](02-xtream-api.md#general-quirks-checklist)) |
| Huge catalogs / xmltv exhaust memory on cheap boxes | Stream-parse, cache raw JSON on disk, keep only display fields in memory, consider Room if more than 50k rows |
| Provider connection limits (1–2 streams) | Stop playback when leaving Live TV. Show a clear "max connections" error |
| TS streams that ExoPlayer can't parse | Per-channel HLS fallback ([05](05-player.md#live-vs-vod)) |
| Compose D-pad focus edge cases in lazy lists | `focusRestorer`, `BringIntoViewRequester`, test on real remotes early (M1) |
| Lost signing keystore = users must reinstall | Back up the keystore offline + in CI secrets. Never rotate it |
| Fire TV blocks the ACTION_VIEW installer | Fall back to `PackageInstaller` session API |
| GitHub API rate limit | 12 h throttle, silent back-off |
| Legal: the app ships no content or provider | Keep it a neutral player. No bundled playlists or provider links |
