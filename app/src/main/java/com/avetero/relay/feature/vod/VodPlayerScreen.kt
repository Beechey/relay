package com.avetero.relay.feature.vod

import androidx.compose.material.icons.rounded.AspectRatio
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
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && showUi) poke++; false }
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
            AnimatedVisibility(shown, enter = fade.first, exit = fade.second) { Box(Modifier.fillMaxSize().background(cs.scrim.copy(alpha = 0.45f))) }
            // top: back, title, mute, cast, picture in picture
            AnimatedVisibility(shown, Modifier.align(Alignment.TopCenter), enter = fade.first + slideInVertically(Springs.spatial()) { -it / 3 },
                exit = fade.second + slideOutVertically(Springs.spatial()) { -it / 3 }) {
                Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", glass) { app.back() }
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.meta.isNotBlank()) Text(item.meta, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    RoundIcon(if (app.player.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, if (app.player.muted) "Unmute" else "Mute", glass) { app.player.toggleMute() }
                    if (app.player.canCast) Box(Modifier.size(48.dp).clip(CircleShape).background(glass), contentAlignment = Alignment.Center) {
                        // the activity theme (Theme.Relay) supplies the colorPrimary MediaRouteButton requires
                        AndroidView({ ctx -> MediaRouteButton(ctx).also { CastButtonFactory.setUpMediaRouteButton(ctx, it) } }, Modifier.size(48.dp))
                    }
                    val activity = LocalContext.current as? com.avetero.relay.MainActivity
                    RoundIcon(Icons.Rounded.PictureInPictureAlt, "Picture in picture", glass) { activity?.enterPip() }
                }
            }
            // centre: skip back, play/pause, skip forward
            AnimatedVisibility(shown, Modifier.align(Alignment.Center), enter = fade.first + scaleIn(Springs.spatialFast(), 0.9f), exit = fade.second + scaleOut(Springs.effects(), 0.9f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    PhoneSkip(-(back / 1000).toInt(), "Back ${back / 1000} seconds", glass) { p.seekTo((p.currentPosition - back).coerceAtLeast(0)) }
                    FilledIconButton(::toggle, shapes = IconButtonDefaults.shapes(RoundedCornerShape(24.dp), RoundedCornerShape(16.dp)), modifier = Modifier.size(96.dp, 72.dp).focusRequester(playBtn)) {
                        Icon(if (app.player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (app.player.playing) "Pause" else "Play", Modifier.size(30.dp))
                    }
                    PhoneSkip((fwd / 1000).toInt(), "Forward ${fwd / 1000} seconds", glass) { p.seekTo(p.currentPosition + fwd) }
                }
            }
            // bottom: seek bar with times, then the actions
            AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter), enter = fade.first + slideInVertically(Springs.spatial()) { it / 3 },
                exit = fade.second + slideOutVertically(Springs.spatial()) { it / 3 }) {
                Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val time = MaterialTheme.typography.labelMedium
                        Text(fmtDur(preview ?: pos), style = time, color = cs.onSurfaceVariant)
                        val slider = remember { SliderState() }
                        slider.value = if (dur > 0) ((preview ?: pos).toFloat() / dur).coerceIn(0f, 1f) else 0f
                        val colors = SliderDefaults.colors(thumbColor = cs.primary, activeTrackColor = cs.primary, inactiveTrackColor = cs.onSurface.copy(alpha = 0.3f))
                        val drag = remember { MutableInteractionSource() }
                        Slider(state = slider, modifier = Modifier.weight(1f), enabled = dur > 0,
                            onValueChange = { preview = (it * dur).toLong(); poke++ },
                            onValueChangeFinished = { preview?.let { p.seekTo(it) }; preview = null },
                            colors = colors, interactionSource = drag,
                            thumb = { SliderDefaults.Thumb(interactionSource = drag, colors = colors, thumbSize = DpSize(4.dp, 16.dp)) },
                            track = { SliderDefaults.Track(it, Modifier.height(4.dp), colors = colors, drawStopIndicator = null, thumbTrackGapSize = 0.dp) })
                        Text("−" + fmtDur((dur - (preview ?: pos)).coerceAtLeast(0)), style = time, color = cs.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (next.isNotEmpty()) PhoneAction("Next episode", Icons.Rounded.SkipNext, null, ::playNext)
                            if (episodes.size > 1) PhoneAction("Episodes", Icons.Rounded.VideoLibrary, glass) { episodesOpen = true }
                            PhoneAction("Audio, subtitles & quality", Icons.Rounded.Subtitles, glass) { tracksOpen = true }
                            PhoneAction(speedLabel, Icons.Rounded.Speed, glass, ::cycleSpeed)
                            PhoneAction(if (zoom) "Zoom" else "Fit", Icons.Rounded.AspectRatio, glass) { zoom = !zoom }
                        }
                        if (LocalWide.current) Text("Double-tap a side to skip", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
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
                Row(Modifier.width(220.dp).clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).clickable(onClickLabel = "Play now", onClick = ::playNext).padding(12.dp),
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
            AnimatedVisibility(shown, Modifier.align(Alignment.TopCenter), enter = fade.first + slideInVertically(Springs.spatialFast()) { -it / 6 },
                exit = fade.second + slideOutVertically(Springs.effects()) { -it / 6 }) {
                Column(Modifier.fillMaxWidth().height(150.dp).background(glass).padding(start = TvSafe.Horizontal, end = TvSafe.Horizontal, top = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.meta.isNotBlank()) TvText(item.meta, style = type.bodyLarge, color = tcs.onSurfaceVariant, maxLines = 1)
                    TvText(item.title, style = type.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            AnimatedVisibility(upNext != null, Modifier.align(Alignment.TopEnd).padding(top = 296.dp, end = TvSafe.Horizontal),
                enter = fade.first + slideInHorizontally(Springs.spatialFast()) { 40 }, exit = fade.second + slideOutHorizontally(Springs.effects()) { 40 }) {
                val n = next.firstOrNull() ?: return@AnimatedVisibility
                Column(Modifier.width(304.dp).clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer.copy(alpha = 0.94f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvText("Up next in ${(dur - pos) / 1000} s", style = type.labelLarge, color = tcs.onSurfaceVariant)
                    TvText(n.title, style = type.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TvButton("Play now", ::playNext, Modifier.fillMaxWidth(), primary = true)
                }
            }
            AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter), enter = fade.first + slideInVertically(Springs.spatialFast()) { it / 10 },
                exit = fade.second + slideOutVertically(Springs.effects()) { it / 10 }) {
                Column(Modifier.fillMaxWidth().background(cs.surface.copy(alpha = 0.82f)).padding(start = TvSafe.Horizontal, end = TvSafe.Horizontal, top = 32.dp, bottom = TvSafe.Vertical),
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        TvText(fmtDur(preview ?: pos), style = type.titleMedium)
                        ScrubBar(pos, dur, p.bufferedPosition, preview, { preview = it; poke++ }, { p.seekTo(it); preview = null }, nudge = ::nudge, focus = scrubFocus,
                            modifier = Modifier.weight(1f).focusProperties { down = playBtn })
                        TvText("−" + fmtDur((dur - (preview ?: pos)).coerceAtLeast(0)), style = type.titleMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TvSkip(-(back / 1000).toInt(), "Back ${back / 1000} seconds", RoundedCornerShape(28.dp, 10.dp, 10.dp, 28.dp)) { p.seekTo((p.currentPosition - back).coerceAtLeast(0)) }
                            val playing = app.player.playing
                            val src = remember { MutableInteractionSource() }
                            val focused by src.collectIsFocusedAsState()
                            TvSurface(::toggle, Modifier.size(80.dp, 64.dp), shape = RoundedCornerShape(20.dp), idle = tcs.primary, focusRequester = playBtn, interactionSource = src) {
                                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", Modifier.align(Alignment.Center).size(28.dp),
                                    tint = if (focused) tcs.inverseOnSurface else tcs.onPrimary)
                            }
                            // the gap to the actions is wide; point the remote at them explicitly
                            TvSkip((fwd / 1000).toInt(), "Forward ${fwd / 1000} seconds", RoundedCornerShape(10.dp, 28.dp, 28.dp, 10.dp), Modifier.focusProperties { right = firstAction }) {
                                p.seekTo(p.currentPosition + fwd)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TvButton("Audio, subtitles & quality", { tracksOpen = true }, focusRequester = firstAction)
                        TvButton(speedLabel, ::cycleSpeed)
                        TvButton(if (app.player.muted) "Unmute" else "Mute", { app.player.toggleMute() })
                        if (episodes.size > 1) TvButton("Episodes", { episodesOpen = true })
                        if (next.isNotEmpty()) TvButton("Next ▶", ::playNext)
                        TvButton(if (zoom) "Zoom" else "Fit", { zoom = !zoom })
                    }
                    TvHints("◀ ▶" to "Seek, hold to scrub", "OK" to "Play / pause", "▼" to "More", "Back" to "Hide, then exit", modifier = Modifier.align(Alignment.End))
                }
            }
        }

        if (tracksOpen) TracksDialog(app) { tracksOpen = false; poke++ }
        if (episodesOpen) EpisodesDialog(episodes, item, { episodesOpen = false; playEpisode(it) }) { episodesOpen = false; poke++ }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, container: Color, onClick: () -> Unit) =
    FilledIconButton(onClick, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = MaterialTheme.colorScheme.onSurface)) { Icon(icon, label) }

@Composable
private fun PhoneSkip(sec: Int, label: String, container: Color, onClick: () -> Unit) =
    FilledIconButton(onClick, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(64.dp).semantics { contentDescription = label },
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = MaterialTheme.colorScheme.onSurface)) {
        skipIcon(sec)?.let { Icon(it, null, Modifier.size(28.dp)) } ?: Text(skipText(sec), style = MaterialTheme.typography.labelLarge)
    }

/** Material has replay/forward icons for 5, 10 and 30 seconds; other skip lengths fall back to text. */
private fun skipIcon(sec: Int): ImageVector? = when (sec) {
    -5 -> Icons.Rounded.Replay5; -10 -> Icons.Rounded.Replay10; -30 -> Icons.Rounded.Replay30
    5 -> Icons.Rounded.Forward5; 10 -> Icons.Rounded.Forward10; 30 -> Icons.Rounded.Forward30
    else -> null
}

private fun skipText(sec: Int) = if (sec < 0) "−${-sec}" else "+$sec"

/** Pill action: translucent [container], or the primary colour when null. */
@Composable
private fun PhoneAction(text: String, icon: ImageVector, container: Color?, onClick: () -> Unit) =
    Button(onClick, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(48.dp), contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        colors = if (container == null) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = container, contentColor = MaterialTheme.colorScheme.onSurface)) {
        Icon(icon, null, Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.width(ButtonDefaults.IconSpacing)); Text(text)
    }

@Composable
private fun TvSkip(sec: Int, label: String, shape: RoundedCornerShape, modifier: Modifier = Modifier, onClick: () -> Unit) =
    TvSurface(onClick, modifier.size(64.dp, 56.dp).semantics { contentDescription = label }, shape = shape, idle = com.avetero.relay.ui.theme.Tok.ContainerHigh) {
        skipIcon(sec)?.let { androidx.tv.material3.Icon(it, null, Modifier.align(Alignment.Center).size(28.dp)) }
            ?: TvText(skipText(sec), style = TvTheme.typography.titleSmall, modifier = Modifier.align(Alignment.Center))
    }

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
