package com.avetero.relay.feature.vod

import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Replay
import com.avetero.relay.feature.playback.BottomScrim
import com.avetero.relay.feature.playback.PlayPause
import com.avetero.relay.feature.playback.PlayerIcon
import com.avetero.relay.feature.playback.PlayerToolbar
import com.avetero.relay.feature.playback.PlayerTopBar
import com.avetero.relay.feature.playback.ToolbarAction
import com.avetero.relay.feature.playback.TopScrim
import com.avetero.relay.feature.playback.TvControl
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Replay5
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.mediarouter.app.MediaRouteButton
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.PlayItem
import com.avetero.relay.data.model.Resume
import com.avetero.relay.feature.playback.CastingOverlay
import com.avetero.relay.feature.playback.PlayerError
import com.avetero.relay.feature.playback.TracksDialog
import com.avetero.relay.player.FullscreenVideo
import com.avetero.relay.player.PlayerSurface
import com.avetero.relay.ui.m3.Artwork
import com.avetero.relay.ui.motion.Springs
import com.avetero.relay.ui.theme.LocalWide
import com.avetero.relay.ui.theme.isTv
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvHints
import com.avetero.relay.ui.tv.TvSafe
import com.avetero.relay.ui.tv.TvSurface
import com.avetero.relay.util.fmtDur
import com.google.android.gms.cast.framework.CastButtonFactory
import kotlinx.coroutines.delay
import androidx.tv.material3.MaterialTheme as TvTheme
import androidx.tv.material3.Text as TvText

/** Movie / episode player: seek, skip, tracks, speed, fit, episodes and the up-next card (PhoneVodPlayer / TvVodPlayer boards). */
@Composable
fun VodPlayerScreen(app: AppState, item: PlayItem, next: List<PlayItem>, episodes: List<PlayItem> = emptyList()) {
    FullscreenVideo(app.inPip)
    val tv = isTv()
    // the Chromecast while casting, otherwise this device; the effect below restarts when it changes
    val p = app.player.current
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var showUi by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(1f) }
    var zoom by remember { mutableStateOf(app.store.zoomDefault) }
    val back = app.store.skipBackSec * 1000L
    val fwd = app.store.skipFwdSec * 1000L
    var ended by remember { mutableStateOf(false) }
    var seekHint by remember { mutableStateOf<String?>(null) }
    var tracksOpen by remember { mutableStateOf(false) }
    var episodesOpen by remember { mutableStateOf(false) }
    LaunchedEffect(seekHint) { if (seekHint != null) { delay(1_200); seekHint = null } }
    val root = remember { FocusRequester() }
    val playBtn = remember { FocusRequester() }
    val firstAction = remember { FocusRequester() }

    fun save() {
        if (dur <= 0) return
        app.saveResume(Resume(item.resumeKey, item.title, item.meta, item.image, pos, dur, item.vodId, item.seriesId))
        // finished an episode: keep the show in Continue watching with the next one queued up
        val n = next.firstOrNull()
        if (pos > dur * 0.95 && n != null)
            app.saveResume(Resume(n.resumeKey, n.title, "Up next · ${n.meta}", n.image, 0, 1, n.vodId, n.seriesId), upNext = true)
    }
    fun playNext() { save(); if (next.isNotEmpty()) app.stack[app.stack.lastIndex] = Screen.VodPlayer(next.first(), next.drop(1), episodes) }
    fun playEpisode(e: PlayItem) {
        save()
        val i = episodes.indexOf(e)
        app.stack[app.stack.lastIndex] = Screen.VodPlayer(e, episodes.drop(i + 1).take(20), episodes)
    }
    fun toggle() { if (p.isPlaying) p.pause() else p.play() }
    fun cycleSpeed() { speed = listOf(1f, 1.25f, 1.5f, 2f, 0.75f).let { it[(it.indexOf(speed) + 1) % it.size] }; p.setPlaybackSpeed(speed) }
    val speedLabel = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

    LaunchedEffect(item.url, p) {
        ended = false
        app.addRecentVod(item)
        app.player.play(item.url, app.store.resumeAt(app.account, item.resumeKey), app.store.subtitlesOn, item.title)
        var ticks = 0
        while (true) {
            pos = p.currentPosition; dur = p.duration.coerceAtLeast(0)
            if (p.playbackState == Player.STATE_ENDED && !ended) {
                ended = true
                if (next.isNotEmpty() && app.store.autoplayNext) playNext() else { pos = dur; save() }
            }
            if (++ticks % 20 == 0) save() // every 10 s
            delay(500)
        }
    }
    DisposableEffect(item.url) { onDispose { save(); app.player.stop() } }
    LaunchedEffect(poke, app.player.playing) {
        showUi = true
        if (app.player.playing) { delay(5_000); showUi = false; runCatching { root.requestFocus() } }
    }
    // scrubbing: Left/Right (or dragging the bar) move a preview point; the video seeks once, when it settles
    var preview by remember { mutableStateOf<Long?>(null) }
    val scrubFocus = remember { FocusRequester() }
    LaunchedEffect(preview) { val to = preview ?: return@LaunchedEffect; delay(900); p.seekTo(to); preview = null }
    fun nudge(forward: Boolean, repeat: Int) {
        // the longer a key is held, the bigger each step, up to 5 minutes
        val step = ((if (forward) fwd else back) * (1 + repeat / 4)).coerceAtMost(300_000L)
        preview = ((preview ?: p.currentPosition) + if (forward) step else -step).coerceIn(0, dur.coerceAtLeast(0))
        poke++
    }
    LaunchedEffect(showUi) { if (showUi) runCatching { (if (preview != null && tv) scrubFocus else playBtn).requestFocus() } }
    // Back first hides the controls; the next Back leaves the player
    BackHandler(showUi && !app.inPip && !tracksOpen && !episodesOpen) { if (app.player.error != null) app.back() else { showUi = false; runCatching { root.requestFocus() } } }

    val shown = showUi && !app.inPip && app.player.error == null
    val upNext = next.firstOrNull()?.takeIf { !app.inPip && dur > 0 && dur - pos in 1..30_000 }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { e ->
                // TV: with a control focused, Compose would spend Back clearing focus, so a paused player never got past it
                if (tv && e.key == Key.Back && !tracksOpen && !episodesOpen && preview == null) {
                    if (e.type == KeyEventType.KeyUp) { if (showUi && app.player.error == null) { showUi = false; runCatching { root.requestFocus() } } else app.back() }
                    return@onPreviewKeyEvent true
                }
                if (e.type == KeyEventType.KeyDown && showUi) poke++
                false
            }
            .focusRequester(root).focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || showUi) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft, Key.MediaRewind -> { nudge(false, e.nativeKeyEvent.repeatCount); true }
                    Key.DirectionRight, Key.MediaFastForward -> { nudge(true, e.nativeKeyEvent.repeatCount); true }
                    Key.MediaPlayPause -> { toggle(); poke++; true }
                    Key.DirectionCenter, Key.Enter, Key.DirectionUp, Key.DirectionDown -> { poke++; true }
                    else -> false
                }
            }
            // phones: tap shows/hides controls, double-tap left/right skips, drag sideways scrubs
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (showUi) showUi = false else poke++ },
                    onDoubleTap = { o ->
                        val left = o.x < size.width / 2
                        p.seekTo((p.currentPosition + if (left) -back else fwd).coerceAtLeast(0))
                        seekHint = if (left) "−${back / 1000} s" else "+${fwd / 1000} s"
                    },
                )
            }
            .pointerInput(Unit) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { p.seekTo((p.currentPosition + (dx / size.width * 180_000).toLong()).coerceAtLeast(0)); seekHint = null },
                ) { _, d -> dx += d; val secs = (dx / size.width * 180).toInt(); seekHint = (if (secs >= 0) "+" else "−") + "${kotlin.math.abs(secs)} s" }
            },
    ) {
        PlayerSurface(app.player.player, Modifier.fillMaxSize(), zoom)
        app.player.castDevice?.let { CastingOverlay(it, item.title, app.player.castStatus) }
        app.player.error?.let { PlayerError(it, { app.player.retry() }, null, onBack = { app.back() }) }

        val cs = MaterialTheme.colorScheme
        val glass = cs.surface.copy(alpha = 0.6f)
        val fade = fadeIn(Springs.effects()) to fadeOut(Springs.effects())

        if (!tv) {
            AnimatedVisibility(shown, enter = fade.first, exit = fade.second) { Box(Modifier.fillMaxSize()) { TopScrim(0.35f); BottomScrim(0.5f) } }
            // top app bar: back, title, mute, cast, picture in picture
            AnimatedVisibility(shown, Modifier.align(Alignment.TopCenter), enter = fade.first + slideInVertically(Springs.spatial()) { -it / 3 },
                exit = fade.second + slideOutVertically(Springs.spatial()) { -it / 3 }) {
                PlayerTopBar({ app.back() }, item.title, item.meta, Modifier.safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (app.player.canCast) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        // the activity theme (Theme.Relay) supplies the colorPrimary MediaRouteButton requires
                        AndroidView({ ctx -> MediaRouteButton(ctx).also { CastButtonFactory.setUpMediaRouteButton(ctx, it) } }, Modifier.size(48.dp))
                    }
                    val activity = LocalContext.current as? com.avetero.relay.MainActivity
                    PlayerIcon(Icons.Rounded.PictureInPictureAlt, "Picture in picture", { activity?.enterPip() })
                    PlayerIcon(if (app.player.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, if (app.player.muted) "Unmute" else "Mute", { app.player.toggleMute() })
                }
            }
            // centre: skip back, play/pause, skip forward
            AnimatedVisibility(shown, Modifier.align(Alignment.Center), enter = fade.first + scaleIn(Springs.spatialFast(), 0.9f), exit = fade.second + scaleOut(Springs.effects(), 0.9f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    PhoneSkip(-(back / 1000).toInt(), "Back ${back / 1000} seconds") { p.seekTo((p.currentPosition - back).coerceAtLeast(0)) }
                    PlayPause(!app.player.playing, ::toggle, Modifier.focusRequester(playBtn))
                    PhoneSkip((fwd / 1000).toInt(), "Forward ${fwd / 1000} seconds") { p.seekTo(p.currentPosition + fwd) }
                }
            }
            // bottom: seek bar with times, then the actions
            AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter), enter = fade.first + slideInVertically(Springs.spatial()) { it / 3 },
                exit = fade.second + slideOutVertically(Springs.spatial()) { it / 3 }) {
                Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val time = MaterialTheme.typography.labelLarge
                        Text(fmtDur(preview ?: pos), style = time)
                        val slider = remember { SliderState() }
                        slider.value = if (dur > 0) ((preview ?: pos).toFloat() / dur).coerceIn(0f, 1f) else 0f
                        Slider(state = slider, modifier = Modifier.weight(1f), enabled = dur > 0,
                            onValueChange = { preview = (it * dur).toLong(); poke++ },
                            onValueChangeFinished = { preview?.let { p.seekTo(it) }; preview = null })
                        Text("−" + fmtDur((dur - (preview ?: pos)).coerceAtLeast(0)), style = time)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerToolbar(Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState())) {
                            if (next.isNotEmpty()) ToolbarAction("Next episode", Icons.Rounded.SkipNext, ::playNext, primary = true)
                            if (episodes.size > 1) ToolbarAction("Episodes", Icons.Rounded.VideoLibrary, { episodesOpen = true })
                            ToolbarAction("Audio & subtitles", Icons.Rounded.Subtitles, { tracksOpen = true })
                            ToolbarAction(speedLabel, Icons.Rounded.Speed, ::cycleSpeed)
                            ToolbarAction(if (zoom) "Zoom" else "Fit", Icons.Rounded.AspectRatio, { zoom = !zoom })
                        }
                        Spacer(Modifier.weight(1f))
                        if (LocalWide.current) Text("Double-tap a side to skip", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            seekHint?.let {
                Box(Modifier.align(Alignment.Center).offset(y = (-88).dp).clip(CircleShape).background(cs.inverseSurface).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = cs.inverseOnSurface)
                }
            }
            AnimatedVisibility(upNext != null, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 84.dp, end = 24.dp),
                enter = fade.first + slideInHorizontally(Springs.spatial()) { it / 2 }, exit = fade.second + slideOutHorizontally(Springs.spatial()) { it / 2 }) {
                val n = next.firstOrNull() ?: return@AnimatedVisibility
                Row(Modifier.width(240.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).clickable(onClickLabel = "Play now", onClick = ::playNext).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(n.image, n.title, 1, Modifier.size(72.dp, 44.dp).clip(RoundedCornerShape(8.dp)), showTitle = false)
                    Column {
                        Text("Up next in ${(dur - pos) / 1000} s", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        Text(n.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            val tcs = TvTheme.colorScheme
            val type = TvTheme.typography
            AnimatedVisibility(shown, Modifier.fillMaxSize(), enter = fade.first, exit = fade.second) { Box(Modifier.fillMaxSize()) { BottomScrim(0.55f) } }
            AnimatedVisibility(upNext != null, Modifier.align(Alignment.TopEnd).padding(top = TvSafe.Vertical + 16.dp, end = TvSafe.Horizontal),
                enter = fade.first + slideInHorizontally(Springs.spatialFast()) { 40 }, exit = fade.second + slideOutHorizontally(Springs.effects()) { 40 }) {
                val n = next.firstOrNull() ?: return@AnimatedVisibility
                Row(Modifier.width(400.dp).clip(RoundedCornerShape(16.dp)).background(com.avetero.relay.ui.theme.Tok.Container.copy(alpha = 0.94f)).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(n.image, n.title, 1, Modifier.size(112.dp, 63.dp).clip(RoundedCornerShape(8.dp)), showTitle = false)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvText("Up next in ${(dur - pos) / 1000} s", style = type.labelLarge, color = tcs.onSurfaceVariant)
                        TvText(n.title, style = type.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TvButton("Play now", ::playNext, primary = true)
                    }
                }
            }
            AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter), enter = fade.first + slideInVertically(Springs.spatialFast()) { 24 },
                exit = fade.second + slideOutVertically(Springs.effects()) { 24 }) {
                Column(Modifier.fillMaxWidth().padding(start = TvSafe.Horizontal, end = TvSafe.Horizontal, bottom = TvSafe.Vertical), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvText(item.title, style = type.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.meta.isNotBlank()) TvText(item.meta, style = type.bodyLarge, color = tcs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvText(fmtDur(preview ?: pos), style = type.labelLarge, color = tcs.onSurfaceVariant)
                        ScrubBar(pos, dur, p.bufferedPosition, preview, { preview = it; poke++ }, { p.seekTo(it); preview = null }, nudge = ::nudge, focus = scrubFocus,
                            modifier = Modifier.weight(1f).focusProperties { down = playBtn })
                        TvText("−" + fmtDur((dur - (preview ?: pos)).coerceAtLeast(0)), style = type.labelLarge, color = tcs.onSurfaceVariant)
                    }
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvSkip(-(back / 1000).toInt(), "Back ${back / 1000} seconds") { p.seekTo((p.currentPosition - back).coerceAtLeast(0)) }
                        val playing = app.player.playing
                        TvControl(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, ::toggle, primary = true, focusRequester = playBtn, description = if (playing) "Pause" else "Play")
                        TvSkip((fwd / 1000).toInt(), "Forward ${fwd / 1000} seconds", Modifier.focusProperties { right = firstAction }) { p.seekTo(p.currentPosition + fwd) }
                        Spacer(Modifier.width(12.dp))
                        if (next.isNotEmpty()) TvControl(Icons.Rounded.SkipNext, "Next episode", ::playNext, focusRequester = firstAction)
                        if (episodes.size > 1) TvControl(Icons.Rounded.VideoLibrary, "Episodes", { episodesOpen = true })
                        TvControl(Icons.Rounded.Subtitles, "Audio & subtitles", { tracksOpen = true }, focusRequester = firstAction.takeIf { next.isEmpty() })
                        TvControl(Icons.Rounded.Speed, speedLabel, ::cycleSpeed)
                        TvControl(Icons.Rounded.AspectRatio, if (zoom) "Zoom" else "Fit", { zoom = !zoom })
                        TvControl(if (app.player.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, null, { app.player.toggleMute() },
                            description = if (app.player.muted) "Unmute" else "Mute")
                    }
                    TvHints("◀▶" to "Seek, hold to scrub", "Back" to "Hide", modifier = Modifier.align(Alignment.End))
                }
            }
        }

        if (tracksOpen) TracksDialog(app) { tracksOpen = false; poke++ }
        if (episodesOpen) EpisodesDialog(episodes, item, { episodesOpen = false; playEpisode(it) }) { episodesOpen = false; poke++ }
    }
}

@Composable
private fun PhoneSkip(sec: Int, label: String, onClick: () -> Unit) =
    PlayerIcon(Icons.Rounded.Replay, label, onClick, Modifier.semantics { contentDescription = label }, iconSize = 36.dp) {
        skipIcon(sec)?.let { Icon(it, null, Modifier.size(36.dp)) } ?: Text(skipText(sec), style = MaterialTheme.typography.titleMedium)
    }

/** Material has replay/forward icons for 5, 10 and 30 seconds; other skip lengths fall back to text. */
private fun skipIcon(sec: Int): ImageVector? = when (sec) {
    -5 -> Icons.Rounded.Replay5; -10 -> Icons.Rounded.Replay10; -30 -> Icons.Rounded.Replay30
    5 -> Icons.Rounded.Forward5; 10 -> Icons.Rounded.Forward10; 30 -> Icons.Rounded.Forward30
    else -> null
}

private fun skipText(sec: Int) = if (sec < 0) "−${-sec}" else "+$sec"

@Composable
private fun TvSkip(sec: Int, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    TvControl(skipIcon(sec) ?: if (sec < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward, null, onClick, modifier, description = label)

/**
 * TV scrub bar. Focus it, then Left/Right move a preview point (faster the longer you hold), OK jumps there now,
 * Back cancels. The target time shows above the knob.
 */
@Composable
private fun ScrubBar(pos: Long, dur: Long, buffered: Long, preview: Long?, setPreview: (Long?) -> Unit, seek: (Long) -> Unit,
                     nudge: (Boolean, Int) -> Unit, focus: FocusRequester, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val cs = TvTheme.colorScheme
    val shown = preview ?: pos
    val frac = if (dur > 0) (shown.toFloat() / dur).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(
        modifier.height(32.dp).focusRequester(focus).onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                when {
                    e.key == Key.DirectionLeft || e.key == Key.DirectionRight -> { if (e.type == KeyEventType.KeyDown) nudge(e.key == Key.DirectionRight, e.nativeKeyEvent.repeatCount); true }
                    (e.key == Key.DirectionCenter || e.key == Key.Enter) && preview != null -> { if (e.type == KeyEventType.KeyUp) seek(preview); true }
                    e.key == Key.Back && preview != null -> { if (e.type == KeyEventType.KeyUp) setPreview(null); true }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val active = focused || preview != null
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(cs.surfaceVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(if (dur > 0) (buffered.toFloat() / dur).coerceIn(0f, 1f) else 0f).background(cs.onSurface.copy(alpha = .18f)))
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(cs.primary))
        }
        // the one focus look: the knob turns inverse and grows while the bar has focus
        val knob = if (active) DpSize(10.dp, 32.dp) else DpSize(6.dp, 24.dp)
        Box(Modifier.offset(x = maxWidth * frac - knob.width / 2).size(knob).clip(RoundedCornerShape(5.dp)).background(if (active) cs.inverseSurface else cs.primary))
        if (preview != null) Box(Modifier.offset(x = (maxWidth * frac - 40.dp).coerceIn(0.dp, maxWidth - 80.dp), y = (-40).dp).width(80.dp)
            .clip(RoundedCornerShape(8.dp)).background(cs.inverseSurface).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            TvText(fmtDur(preview), style = TvTheme.typography.titleSmall, color = cs.inverseOnSurface)
        }
    }
}
