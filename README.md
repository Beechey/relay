# Relay IPTV

Relay is an IPTV player for **Android TV, Google TV and Android phones and tablets**. You sign in with the Xtream Codes details from your IPTV provider, and Relay turns that into a fast, remote-friendly TV app: live channels with a TV guide, a movie and series library, search, favourites and continue watching.

Relay doesn't provide any channels or content. It only plays what your own provider gives you.

## What it does

- **Live TV** – browse channel groups, preview a channel in a small player, then go fullscreen. Change channel with Up/Down on the remote, or by swiping on a phone.
- **TV guide** – a timeline of what's on, built from your provider's full guide (XMLTV) and refreshed in the background. Pick a category, and move earlier or later through the day.
- **Movies and series** – poster grids by category, sort by newest, A–Z or rating, detail pages, season and episode lists.
- **Continue watching** – movies and episodes remember where you stopped. When you finish an episode, the next one is queued up.
- **Search** – find channels, movies, series and whole categories from one box.
- **Favourites and hidden categories** – star what you watch and hide what you never want to see.
- **Picture-in-picture** on phones and tablets – leave the app and keep watching in a floating window.
- **Audio, subtitles and quality** – choose the track or video quality per video when the stream offers more than one.
- **Several playlists** – save more than one provider and switch between them.
- **Updates itself** – new versions are offered inside the app.

## Getting it

Download `relay-iptv.apk` from the latest release and install it on your device:

**https://github.com/Beechey/relay/releases/latest/download/relay-iptv.apk**

On Android TV, one easy route is the *Downloader* app: enter the link above and install. Android asks you to allow installs from that app the first time.

After that, Relay checks for updates itself and installs them when you agree.

## Using it

1. Open Relay and enter your provider's **server address** (including the port, e.g. `http://example.com:8080`), **username** and **password**.
2. Relay signs in and downloads your channels, movies, series and TV guide. A large playlist takes a minute the first time; after that the app opens straight away from its saved copy.
3. Your login stays on the device and Relay signs you in automatically next time.

| | TV remote | Phone / tablet |
|---|---|---|
| Select | OK | Tap |
| Options, favourite, hide a category | Hold OK | Press and hold |
| Back | Back | Back gesture or the ← button |
| Change channel (fullscreen) | Up / Down | Swipe up / down |
| Skip in a movie | Left / Right | Double-tap left / right side |

## How it works

Relay talks directly to your provider's Xtream Codes API (`player_api.php`). There's no Relay server in between, and no account or tracking.

- **Catalogue** – channel, movie and series lists are streamed to disk and read back a piece at a time, so even very large playlists don't run out of memory. They're cached and refreshed on the schedule you pick in Settings.
- **TV guide** – the provider's XMLTV file is stream-parsed as it downloads. Relay keeps two hours back to 24 hours ahead for every channel, and falls back to the provider's short "now and next" data where a channel isn't covered.
- **Playback** – AndroidX Media3 (ExoPlayer) plays live MPEG-TS and HLS, and movie and episode files.
- **Updates** – releases are published on this repository's [Releases](https://github.com/Beechey/relay/releases) page. The app checks it on start and installs the new APK through Android's installer. Every release is signed with the same key, so updates install over the previous version and keep your settings. Turn on *Beta updates* in Settings › About and updates to get pre-releases.

## Project layout

The app is organised by feature. Phones use Material 3 Expressive, TVs use Material 3 for TV; each feature folder holds the shared logic plus a phone and a TV layout where they differ.

```
app/src/main/java/com/avetero/relay/
├── MainActivity.kt        picture-in-picture, remote key routing, entry point
├── app/                   AppState (what the app knows), Navigator (one back stack per tab), the phone and TV shells
├── data/
│   ├── model/             plain data: channels, movies, series, programmes, resume points
│   ├── xtream/            Xtream Codes API client, HTTP, JSON parsers
│   ├── epg/               XMLTV guide parser and its cache
│   ├── store/             saved settings, playlists and catalogue cache
│   └── update/            in-app updates from GitHub releases
├── player/                ExoPlayer host, video surface, Chromecast relay
├── ui/
│   ├── theme/             colours, Roboto Flex, TV / phone scaling
│   ├── m3/                phone building blocks (Material 3 Expressive)
│   ├── tv/                TV building blocks (Material 3 for TV)
│   ├── motion/            springs, transitions, shared elements
│   └── components/        small helpers shared by both
├── feature/
│   ├── home/  live/  guide/  vod/  search/  favourites/
│   ├── playback/          player controls, track picker and error card shared by both players
│   ├── categories/        Manage categories (order and hide)
│   ├── settings/          Settings and its sections
│   ├── onboarding/        sign-in and first sync
│   └── update/            the update dialog
└── util/                  date and time formatting
baselineprofile/           records the start-up profile shipped in release builds
```

Unit tests mirror the same packages under `app/src/test`.

## Building from source

Requirements: JDK 17 and the Android SDK (platform 37.1); Gradle and the Android Gradle Plugin come with the wrapper.

```bash
./gradlew :app:assembleDebug        # debug build
./gradlew :app:testDebugUnitTest    # unit tests
```

Release builds are signed from environment variables (`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) with the keystore at `app/release.jks`, which is never committed. Tagging `vX.Y.Z` (or `vX.Y.Z-beta.N`, published as a pre-release) runs the release workflow, which builds, signs and publishes `relay-iptv.apk` on this repository's Releases page.

Built with Kotlin and Jetpack Compose, one layout for TV (remote-first, 1280×720 design grid) and one for phones (bottom tabs, portrait and landscape).

## Contributing

Issues and pull requests are welcome. Please keep changes small and focused, run the unit tests, and check your change on both a phone and a TV (an emulator is fine).

## Licence

Relay is free software under the [GNU General Public License v3.0](LICENSE): you can use, study, share and change it, and anything you distribute that's built from it must be shared under the same licence. Relay bundles the FFmpeg audio decoder (GPL-3.0) and the Roboto Flex font (SIL Open Font License, see `licenses/`).
