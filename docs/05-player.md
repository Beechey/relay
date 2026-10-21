# 05 — Player

Boards: **04** preview pane, **06 Live player** (`Player.dc.html`), **09 Movie / episode player** (`VodPlayer.dc.html`).

## One player, many surfaces

- `PlayerHost` owns **one** `ExoPlayer` for the app's lifetime. It's created in `MainActivity.onCreate`, released in `onDestroy`, paused in `onStop` and resumed in `onStart` (live only).
- The surfaces are `AndroidView { PlayerView(it).apply { useController = false } }` in the preview pane (04, 05 header) and fullscreen (06, 09). On composition, set `view.player = host.player`. In `onRelease`, set it to `null`. Moving preview → fullscreen therefore keeps playing with no rebuffer.
- Use our own Compose controls (info bar, seek bar), never `PlayerView`'s controller.

```kotlin
val player = ExoPlayer.Builder(ctx)
    .setRenderersFactory(DefaultRenderersFactory(ctx)
        .setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
        .setEnableDecoderFallback(true))
    .setLoadControl(loadControlFor(settings.buffer))
    .setMediaSourceFactory(DefaultMediaSourceFactory(
        DefaultHttpDataSource.Factory()
            .setUserAgent("IPTVSmartersPlayer")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000).setReadTimeoutMs(30_000)))
    .build()
```

## Live vs VOD

| | Live | VOD |
|---|---|---|
| URL | `/live/u/p/{id}.ts` or `.m3u8` | `/movie/u/p/{id}.{ext}`, `/series/u/p/{epId}.{ext}` |
| MediaItem | `setLiveConfiguration` default; for `.ts` pass `MimeTypes.VIDEO_MP2T` | `setMimeType` from ext only when unknown (`mkv`, `avi` fine via extractors) |
| Seek | disabled (except catch-up) | ±10/30 s, scrub |
| On error | auto-retry once after 2 s, then error overlay | error overlay |
| Resume | n/a | save every 10 s + on exit |

- **TS vs HLS:** with the default **MPEG-TS** setting, `.ts` gives the fastest zaps. If a TS stream fails with `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` or HTTP 4xx, offer "Switch to HLS" in the error overlay and remember the choice per channel (`Set<Int>` in prefs).
- A live TS stream is effectively endless progressive media. Set `setLiveConfiguration` and don't show duration.
- **Catch-up** uses the timeshift URL from [02](02-xtream-api.md#catch-up--timeshift-url) as VOD (seekable within that window).

## Settings mapping

| Setting (board 15) | Effect |
|---|---|
| Decoder: Hardware / Software | Hardware: default `MediaCodecSelector`. Software: `MediaCodecSelector` filtering to `c2.android.*` / `OMX.google.*` decoders |
| Buffer size: Small / Medium / Large | `DefaultLoadControl.Builder().setBufferDurationsMs(min, max, playback, rebuffer)` with roughly (5/15/1/2 s), (15/30/1.5/3 s), (30/60/2.5/5 s) |
| Live stream format | `.ts` vs `.m3u8` in `Xtream.streamUrl` |
| Default aspect | `PlayerView.resizeMode`: FIT / FILL / ZOOM. Fixed 16:9 or 4:3 → `AspectRatioFrameLayout` with an override |
| Preferred audio language | `trackSelectionParameters.setPreferredAudioLanguage(code)` |
| Subtitles on by default | `setPreferredTextLanguage` + `setIgnoredTextSelectionFlags(0)`, otherwise disable text track type |
| Play next episode automatically | Enables the countdown auto-play in 09 |

## Audio & subtitle tracks

- Build the picker from `player.currentTracks.groups` filtered by `type == C.TRACK_TYPE_AUDIO / TEXT`, labelled with `Format.label ?: Locale(language).displayLanguage`, plus channel count ("5.1").
- Apply the choice with `TrackSelectionOverride(group.mediaTrackGroup, index)`. Subtitles get an "Off" entry (`setTrackTypeDisabled(TEXT, true)`).
- Show the picker as a picker dialog (18) from the Audio / Subtitles buttons in 06 and 09.

## Resume & watched

- Key `m{streamId}` / `e{episodeId}` → `positionMs`, plus `durationMs` and `updatedAt`, as JSON in `SharedPreferences("resume_<playlist>")`.
- Save when `position > 30 s`. At 95 % or more, mark watched (`w{id}=true`) and drop the resume entry.
- Continue watching (03) = the 10 most recently updated entries that aren't watched.

## Next-episode autoplay

- In 09 for episodes, when `duration - position < 20 s`, show the "Next episode in N s" card. Its Play button skips straight to the next episode.
- At 0, when the setting is on, load the next episode, which is the next `episode_num` in the season, then season+1 episode 1. With no next episode, there's no card.
- Back or any seek cancels the countdown.

## Channel zapping

- ▲▼ / CH± in 06 updates the info bar to the target channel at once, and **tunes after a 600 ms debounce**, so rapid presses skip channels without opening each stream.
- Tune = `player.setMediaItem(item); player.prepare(); player.play()` on the same instance. Never create a new player.
- Zap order = the current group's order, and it wraps around at the ends.
- Last channel: remember the previous stream id. The 0 key or a "Back" button swaps between the last two channels.
- Write to recent channels (03) when a channel plays for more than 10 s.

## Preview → fullscreen handoff

1. On 04 the first OK calls `host.play(channel)` while the preview `PlayerView` is attached.
2. A second OK pushes `LivePlayer(channel)`. The preview view detaches (`player = null`) and the fullscreen view attaches the **same** player, so playback continues with no rebuffer.
3. Back pops to 04, where the preview reattaches and focus returns to that channel.
4. Leaving Live TV for Movies or Series stops live playback (`player.stop()`) to free the provider connection. Many accounts allow only 1–2 connections.
