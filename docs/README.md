# Relay IPTV — implementation guide

A TiviMate-style IPTV player for **Android TV** first, with phones and tablets also supported. It signs in with **Xtream Codes** and covers live TV with an EPG guide, movies, series, search, favourites and settings. Updates come from **GitHub Releases**.

**Design canvas:** https://claude.ai/artifact/XrLDLiYD9C4ic1fQdtZCWd (private; share it from the page's Share menu).
Every screen in these docs links to a board by its number and file, e.g. **04 Live TV — channel browser** (`Main.dc.html`).

## Docs

| Doc | What it covers |
|---|---|
| [01-architecture.md](01-architecture.md) | Packages, state, data flow, storage, caching, threading, focus strategy |
| [02-xtream-api.md](02-xtream-api.md) | Every Xtream endpoint and URL format, with JSON examples and quirks |
| [03-screens.md](03-screens.md) | Boards 01–19: layout, data, D-pad and touch behaviour, acceptance criteria |
| [04-design-system.md](04-design-system.md) | Colour/type tokens, focus rule, Compose theme notes |
| [05-player.md](05-player.md) | Media3 setup, live vs VOD, tracks, resume, zapping |
| [06-github-updates.md](06-github-updates.md) | Self-update from GitHub Releases, signing, CI workflow |
| [07-roadmap.md](07-roadmap.md) | Milestones M0–M6 as checklists, test plan, risks |

## Goals

- It works fully with a TV remote (D-pad + OK + Back), and touch works too.
- Live TV is the core: channel groups, a channel list with now/next, a live preview pane, a fullscreen player with an info bar, and an EPG grid.
- VOD: browse movies and series, open a detail page, resume where you stopped, and play the next episode automatically.
- Several Xtream playlists can be saved, with one active at a time.
- Login details stay on the device, and no backend is involved.
- The app updates itself from GitHub Releases, so it can be sideloaded with no Play Store.

## Stack

| | |
|---|---|
| Language | Kotlin 2.1.0 |
| UI | Jetpack Compose (BOM 2024.12.01), Material3 used only for primitives |
| Playback | AndroidX Media3 1.5.1 (ExoPlayer + HLS + UI) |
| Build | AGP 8.7.3, Gradle 8.11.x, compileSdk/targetSdk 35, **minSdk 26** |
| JDK | **17** to build (the machine's JDK 26 is too new for Gradle 8.11) |
| Networking | `HttpURLConnection` + `org.json` (no extra deps) |
| Tests | JUnit4 + `org.json:json` for JVM unit tests |

## Current status (21 Oct 2026)

- All boards 01–17 are built in Compose under `app/src/main/java/com/avetero/relay/` (see [01-architecture](01-architecture.md) for the layout). It builds, unit tests pass, and it runs in the Android TV emulator.
- Not built yet: xmltv guide download, catch-up, reminders, favourites reorder, the TV guide / appearance / parental / remote settings sections, phone layouts, bundled fonts. Tracked in [07-roadmap](07-roadmap.md).
- Repos: source in private `aveterosystems/relay`, release APKs in public `aveterosystems/relay-releases` (the app's update source).
- Build locally with JDK 17: `./gradlew :app:assembleDebug` (Gradle uses `org.gradle.java.home` from `~/.gradle/gradle.properties`).
- Release signing key lives outside the repo in `~/.relay-signing/` and in the source repo's Actions secrets.
