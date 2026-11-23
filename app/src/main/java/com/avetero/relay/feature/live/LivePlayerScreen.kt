package com.avetero.relay.feature.live

import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.avetero.relay.feature.playback.BottomScrim
import com.avetero.relay.feature.playback.PlayerIcon
import com.avetero.relay.feature.playback.PlayPause
import com.avetero.relay.feature.playback.PlayerToolbar
import com.avetero.relay.feature.playback.PlayerTopBar
import com.avetero.relay.feature.playback.ToolbarAction
import com.avetero.relay.feature.playback.TopScrim
import com.avetero.relay.feature.playback.TvControl
import com.avetero.relay.feature.playback.TvProgress
import com.avetero.relay.feature.playback.TvStateBadge
import com.avetero.relay.ui.tv.TvSafe
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.FiberManualRecord
import android.view.KeyEvent.KEYCODE_0
import android.view.KeyEvent.KEYCODE_9
import android.view.KeyEvent.KEYCODE_LAST_CHANNEL
import android.view.KeyEvent.KEYCODE_NUMPAD_0
import android.view.KeyEvent.KEYCODE_NUMPAD_9
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avetero.relay.MainActivity
import com.avetero.relay.app.AppState
import com.avetero.relay.app.AppState.Companion.ALL
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.Programme
import com.avetero.relay.data.model.current
import com.avetero.relay.data.model.upcoming
import com.avetero.relay.feature.playback.CastingOverlay
import com.avetero.relay.feature.playback.PlayerError
import com.avetero.relay.feature.playback.TracksDialog
import com.avetero.relay.player.FullscreenVideo
import com.avetero.relay.player.PlayerSurface
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.m3.ChannelLogo
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.theme.isTv
import com.avetero.relay.ui.tv.TvHints
import com.avetero.relay.ui.tv.TvSurface
import com.avetero.relay.util.fmtTime
import com.avetero.relay.util.nowSec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fullscreen live TV, phone (PhoneLiveFull) and TV (TvPlayer). Up/down, channel keys and phone swipes zap; number
 * keys jump; pause, skip and the scrub bar watch behind live (catch-up channels replay from the archive). TV: ◀▶
 * browse the strip of neighbouring channels, hold ◀ for the channel list, ☰ or hold OK for options.
 */
@Composable
fun LivePlayerScreen(app: AppState, start: Channel, startList: List<Channel>, openList: Boolean = false, startGroup: String = ALL) {
    FullscreenVideo(app.inPip)
    val tv = isTv()
    // the channel list can switch category, so the zapping order follows whatever was picked last
    var list by remember { mutableStateOf(startList) }
    var index by remember { mutableIntStateOf(startList.indexOfFirst { it.id == start.id }.coerceAtLeast(0)) }
    val c = list.getOrElse(index) { start }
    var showInfo by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var tracksOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    // TV: the sliding channel list, the strip, number entry and the previous channel
    var listOpen by remember { mutableStateOf(tv && openList) }
    var listPoke by remember { mutableIntStateOf(0) }
    var group by remember { mutableStateOf(startGroup) }
    var digits by remember { mutableStateOf("") }
    var previous by remember { mutableStateOf<Channel?>(null) }
    val stripFocus = remember { FocusRequester() }
    var stripOn by remember { mutableStateOf(false) }
    // OK brings up the controls row; ◀▶ swaps it for the strip of neighbouring channels until the info hides
    val ctrlFocus = remember { FocusRequester() }
    var ctrlOn by remember { mutableStateOf(false) }
    var browsing by remember { mutableStateOf(false) }
    var okLong by remember { mutableStateOf(false) }
    // live pause: when it was paused, and how far behind live the picture is (catch-up channels can be watched behind)
    var pausedAt by remember { mutableStateOf<Long?>(null) }
    var behind by remember { mutableStateOf(0L) }
    var clock by remember { mutableStateOf(nowSec()) }
    var seekGen by remember { mutableIntStateOf(0) }
    LaunchedEffect(pausedAt, showInfo) { while (pausedAt != null || showInfo) { clock = nowSec(); delay(1_000) } }

    LaunchedEffect(c.id) {
        app.liveChannel = c
        delay(400) // debounce fast zapping
        pausedAt = null; behind = 0
        app.player.play(app.liveUrl(c), title = c.name)
        app.addRecentChannel(c.id)
        app.loadEpg(c)
    }
    // the info stays up while paused
    LaunchedEffect(poke, pausedAt) { showInfo = true; if (pausedAt == null) { delay(5_000); showInfo = false } }
    LaunchedEffect(Unit) { focus.settle() }
    // the strip leaves with the info; give focus back to the player so the keys keep working
    LaunchedEffect(showInfo) { if (!showInfo) { if ((stripOn || ctrlOn) && !listOpen && !menu && !tracksOpen) runCatching { focus.requestFocus() }; stripOn = false; ctrlOn = false; browsing = false } }

    fun go(to: Channel, inList: List<Channel> = list) {
        if (to.id != c.id) previous = c
        list = inList; index = inList.indexOfFirst { it.id == to.id }.coerceAtLeast(0); poke++
    }
    fun zap(d: Int) { if (list.isNotEmpty()) { previous = c; index = Math.floorMod(index + d, list.size); poke++ } }
    fun goLive() { pausedAt = null; behind = 0; app.player.play(app.liveUrl(c), title = c.name); poke++ }
    fun pause() { if (pausedAt == null) { app.player.current.pause(); pausedAt = nowSec(); poke++ } }
    fun resume() {
        val at = pausedAt ?: return
        val gone = nowSec() - at
        pausedAt = null; poke++
        when {
            // a short pause plays on from what's buffered
            gone < 30 -> { app.player.current.play(); behind += gone }
            // longer: catch-up channels pick up from the archive at the moment you paused
            c.archive -> { behind += gone; app.player.play(app.catchupFrom(c, nowSec() - behind), title = c.name) }
            else -> { behind = 0; app.player.play(app.liveUrl(c), title = c.name); app.message = "This channel can't rewind, so you're back at live" }
        }
    }
    // the moment on screen, in epoch seconds
    fun watching() = (pausedAt ?: nowSec()) - behind
    // skips and scrubs gather for half a second, then the archive is asked once
    fun seekTo(t: Long) {
        val b = nowSec() - t
        if (b <= 0 && behind == 0L && pausedAt == null) return // already live
        if (b > 0 && !c.archive) { app.say("This channel has no catch-up"); return }
        pausedAt = null; behind = b.coerceAtLeast(0); poke++; seekGen++
    }
    fun skip(seconds: Int) = seekTo(watching() + seconds)
    LaunchedEffect(seekGen) {
        if (seekGen == 0) return@LaunchedEffect
        delay(500)
        if (behind <= 0) goLive() else app.player.play(app.catchupFrom(c, nowSec() - behind), title = c.name)
    }
    // the Live tab is the bottom of the stack: Back from it goes to For you
    fun leave() { if (app.stack.size > 1) app.back() else app.go(Screen.Home) }
    fun closeList() { listOpen = false; runCatching { focus.requestFocus() } }
    fun focusSoon(f: FocusRequester) { scope.launch { repeat(10) { if (runCatching { f.requestFocus() }.getOrDefault(false)) return@launch; delay(30) } } }
    fun showStrip() { poke++; browsing = true; focusSoon(stripFocus) }
    fun showControls() { poke++; browsing = false; focusSoon(ctrlFocus) }
    // Back closes the channel list; otherwise it leaves Live (the info hides by itself, it doesn't need a press)
    BackHandler(listOpen && !app.inPip && !menu && !tracksOpen) { closeList() }
    // the list tucks itself away when the remote goes quiet
    LaunchedEffect(listOpen, listPoke) { if (listOpen) { delay(8_000); closeList() } }
    // typed channel numbers: wait for a pause, then jump
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(1_500)
        val n = digits.toIntOrNull(); digits = ""
        val inList = list.firstOrNull { it.num == n }
        val any = inList ?: app.channelsVisible.firstOrNull { it.num == n }
        if (any != null) go(any, if (inList != null) list else app.channelsIn(ALL)) else app.message = "No channel $n"
    }

    val epg = app.programmesFor(c)
    val at = (pausedAt ?: clock.coerceAtLeast(nowSec())) - behind
    // paused or behind live: the programme (and how far into it) at the point you're watching
    val now = if (pausedAt == null && behind == 0L) epg.current() else epg?.firstOrNull { at in it.start until it.end }
    val totalBehind = (pausedAt?.let { clock - it } ?: 0L) + behind
    val state = when {
        pausedAt != null -> "Paused · ${dur(totalBehind)} behind"
        behind > 0 -> "${dur(behind)} behind"
        else -> null
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            // TV: Back with a control or the strip focused hides the info (Compose would otherwise spend it clearing focus)
            .onPreviewKeyEvent { e ->
                if (!(tv && e.key == Key.Back && (stripOn || ctrlOn) && !listOpen && !menu && !tracksOpen)) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyUp) showInfo = false
                true
            }
            .focusRequester(focus).focusable()
            .onKeyEvent { e ->
                if (menu || tracksOpen) return@onKeyEvent false
                val code = e.nativeKeyEvent.keyCode
                val center = e.key == Key.DirectionCenter || e.key == Key.Enter
                // OK acts on release, so the row it brings up doesn't receive the release as a click
                if (center && e.type == KeyEventType.KeyUp) {
                    if (!okLong) { if (tv) showControls() else poke++ }
                    okLong = false
                    return@onKeyEvent true
                }
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val digit = when (code) { in KEYCODE_0..KEYCODE_9 -> code - KEYCODE_0; in KEYCODE_NUMPAD_0..KEYCODE_NUMPAD_9 -> code - KEYCODE_NUMPAD_0; else -> -1 }
                val left = e.key == Key.DirectionLeft
                val right = e.key == Key.DirectionRight
                when {
                    digit >= 0 -> { if (digits.length < 5) digits += digit; true }
                    code == KEYCODE_LAST_CHANNEL -> { previous?.let { go(it) }; true }
                    e.key == Key.ChannelUp -> { zap(-1); true }
                    e.key == Key.ChannelDown -> { zap(1); true }
                    // with the list open, the arrows move through it instead of zapping
                    listOpen -> { listPoke++; false }
                    e.key == Key.DirectionUp -> { zap(-1); true }
                    e.key == Key.DirectionDown -> { zap(1); true }
                    // hold ◀: the channel list
                    tv && left && e.nativeKeyEvent.repeatCount > 0 -> { listOpen = true; listPoke++; true }
                    // along the strip, focus moves itself
                    tv && (stripOn || ctrlOn) && (left || right) -> { poke++; false }
                    right && behind > 0 && pausedAt == null -> { goLive(); true }
                    tv && (left || right) -> { showStrip(); true }
                    // hold OK = options (audio, subtitles, quality, favourite)
                    center -> { if (e.nativeKeyEvent.repeatCount > 0 || e.nativeKeyEvent.isLongPress) { okLong = true; menu = true }; true }
                    e.key == Key.Menu -> { menu = true; true }
                    e.key == Key.MediaPlayPause -> { if (pausedAt != null) resume() else pause(); true }
                    e.key == Key.MediaPause -> { pause(); true }
                    e.key == Key.MediaPlay -> { resume(); true }
                    e.key == Key.MediaRewind -> { skip(-10); true }
                    e.key == Key.MediaFastForward && behind > 0 && pausedAt == null -> { goLive(); true }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (showInfo) showInfo = false else poke++ },
                    onLongPress = { menu = true },
                    // double-tap a side to skip ten seconds
                    onDoubleTap = { o -> skip(if (o.x < size.width / 2) -10 else 10) },
                )
            }
            // phones: swipe up for the next channel, down for the previous one
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -120f) zap(1) else if (total > 120f) zap(-1) }) { _, dy -> total += dy }
            },
    ) {
        PlayerSurface(app.player.player, Modifier.fillMaxSize(), zoom = app.store.zoomDefault)
        app.player.castDevice?.let { CastingOverlay(it, listOfNotNull(c.name, now?.title).joinToString(" · "), app.player.castStatus) }
        app.player.error?.let { err ->
            PlayerError(err, onRetry = { app.player.retry() }, onBack = ::leave, onSwitch = {
                app.hls = !app.hls; app.store.hls = app.hls; app.player.play(app.liveUrl(c), title = c.name)
            }, hls = app.hls)
        }
        val visible = showInfo && !app.inPip
        if (!tv) AnimatedVisibility(visible, Modifier.fillMaxSize(), enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()), exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())) {
            PhoneControls(app, c, now, at, state, paused = pausedAt != null, onBack = ::leave, onPlayPause = { if (pausedAt != null) resume() else pause() },
                onZap = ::zap, onSkip = ::skip, onSeek = ::seekTo, onLive = ::goLive, onTracks = { tracksOpen = true }, poke = { poke++ })
        } else {
            val shown = visible && !listOpen && app.player.error == null
            AnimatedVisibility(shown, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize()) { TopScrim(0.25f); BottomScrim() }
            }
            AnimatedVisibility(shown, Modifier.align(Alignment.TopStart), enter = fadeIn() + slideInVertically { -24 }, exit = fadeOut() + slideOutVertically { -24 }) {
                TvTopInfo(c, state)
            }
            AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically { 24 }, exit = fadeOut() + slideOutVertically { 24 }) {
                TvInfo(app, c, now, at, list, index, browsing, stripFocus, ctrlFocus, live = state == null, paused = pausedAt != null,
                    onStrip = { stripOn = it; if (it) poke++ }, onControls = { ctrlOn = it; if (it) poke++ }, onPick = { go(it) }, onMenu = { menu = true },
                    onPlayPause = { if (pausedAt != null) resume() else pause() }, onLive = ::goLive, onSeek = ::seekTo, onTracks = { tracksOpen = true },
                    onChannels = { listOpen = true; listPoke++ }, poke = { poke++ })
            }
            AnimatedVisibility(listOpen && !app.inPip, Modifier.fillMaxSize(), enter = fadeIn() + slideInHorizontally { -it / 6 }, exit = fadeOut() + slideOutHorizontally { -it / 6 }) {
                // Back must reach the list itself: with a row focused, Compose would otherwise spend the first press clearing focus
                ChannelOverlay(app, group, { group = it }, c, { ch, l -> go(ch, l); app.store.lastLiveGroup = group }, { listPoke++ },
                    Modifier.onPreviewKeyEvent { e -> if (e.key == Key.Back) { if (e.type == KeyEventType.KeyUp) closeList(); true } else false })
            }
        }
        if (digits.isNotEmpty()) Text(digits, Modifier.align(Alignment.TopEnd).padding(top = if (tv) 72.dp else 80.dp, end = 48.dp).clip(RoundedCornerShape(16.dp))
            .background(Tok.Container.copy(alpha = 0.9f)).padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.displaySmall)
        if (menu) ChannelMenu(app, c, { menu = false; okLong = false; runCatching { focus.requestFocus() } }, inPlayer = true, onPrevious = previous?.let { p -> { menu = false; go(p) } })
        if (tracksOpen) TracksDialog(app) { tracksOpen = false; runCatching { focus.requestFocus() } }
    }
}

/** 2:14, or 1:02:14 past the hour. */
private fun dur(s: Long) = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)

/** PhoneLiveFull: top app bar, centre transport and the scrub bar with actions, on scrims over the picture. */
@Composable
private fun PhoneControls(
    app: AppState, c: Channel, now: Programme?, at: Long, state: String?, paused: Boolean, onBack: () -> Unit, onPlayPause: () -> Unit,
    onZap: (Int) -> Unit, onSkip: (Int) -> Unit, onSeek: (Long) -> Unit, onLive: () -> Unit, onTracks: () -> Unit, poke: () -> Unit,
) = Box(Modifier.fillMaxSize()) {
    val cs = MaterialTheme.colorScheme
    TopScrim(0.35f)
    BottomScrim(0.5f)
    Box(Modifier.fillMaxSize().displayCutoutPadding()) {
        val next = app.programmesFor(c).upcoming().firstOrNull()
        PlayerTopBar(onBack, now?.title ?: c.name,
            listOfNotNull(c.num.takeIf { it > 0 }?.toString(), c.name, next?.let { "Next ${fmtTime(it.start)} · ${it.title}" }).joinToString(" · "),
            Modifier.align(Alignment.TopStart).padding(horizontal = 12.dp, vertical = 8.dp), leading = { ChannelLogo(c.icon, c.name, size = 40.dp) }) {
            if (state != null) Text(state, Modifier.clip(RoundedCornerShape(8.dp)).background(cs.secondaryContainer).padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer)
            else LiveBadge(style = MaterialTheme.typography.labelLarge)
            if (app.player.canCast) PlayerIcon(Icons.Rounded.Cast, "Cast", rememberCastPicker())
            val activity = LocalContext.current as? MainActivity
            PlayerIcon(Icons.Rounded.PictureInPictureAlt, "Picture in picture", { activity?.enterPip() })
            PlayerIcon(if (app.player.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, if (app.player.muted) "Unmute" else "Mute", { app.player.toggleMute() })
        }
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PlayerIcon(Icons.Rounded.SkipPrevious, "Previous channel", { onZap(-1) }, iconSize = 32.dp)
            PlayerIcon(Icons.Rounded.Replay10, "Back 10 seconds", { onSkip(-10) }, iconSize = 36.dp)
            PlayPause(paused, onPlayPause)
            PlayerIcon(Icons.Rounded.Forward10, "Forward 10 seconds", { onSkip(10) }, iconSize = 36.dp)
            PlayerIcon(Icons.Rounded.SkipNext, "Next channel", { onZap(1) }, iconSize = 32.dp)
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (now != null) Scrub(now, at, c.archive, onSeek, poke)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerToolbar {
                    if (state != null) ToolbarAction("Go live", Icons.Rounded.FiberManualRecord, onLive, primary = true)
                    if (c.archive && now != null) ToolbarAction("From the start", Icons.Rounded.Restore, { onSeek(now.start) })
                    ToolbarAction("Audio & subtitles", Icons.Rounded.Subtitles, onTracks)
                    val fav = app.isFav("c${c.id}")
                    ToolbarAction(if (fav) "Saved" else "Save", if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline, { app.toggleFav("c${c.id}") })
                }
                Spacer(Modifier.weight(1f))
                Text("Swipe ↑↓ to change channel", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The programme's span with the point you're watching. Catch-up channels can drag it (never past live; the time on
 * the left follows the knob); the lighter part is how far live has got.
 */
@Composable
private fun Scrub(p: Programme, at: Long, archive: Boolean, onSeek: (Long) -> Unit, poke: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val span = (p.end - p.start).coerceAtLeast(1).toFloat()
    val live = ((nowSec() - p.start) / span).coerceIn(0f, 1f)
    // seconds into the programme, kept small so a Float holds them exactly
    val slider = remember(p.start, p.end) { SliderState(0f, trackRange = 0f..span) }
    var drag by remember { mutableStateOf<Float?>(null) }
    if (drag == null) slider.value = (at - p.start).toFloat().coerceIn(0f, span)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(fmtTime(drag?.let { p.start + it.toLong() } ?: p.start), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Slider(slider, modifier = Modifier.weight(1f).height(32.dp), enabled = archive,
            onValueChange = { v -> drag = v.coerceAtMost(live * span); slider.value = drag!!; poke() },
            onValueChangeFinished = { drag?.let { onSeek(p.start + it.toLong()) }; drag = null },
            thumb = { Box(Modifier.size(4.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(cs.primary)) },
            track = { s ->
                val watched = s.value / span
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(cs.onSurface.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(live).height(4.dp).background(cs.onSurface.copy(alpha = 0.5f)))
                    Box(Modifier.fillMaxWidth(watched).height(4.dp).background(cs.primary))
                }
            })
        Text(fmtTime(p.end), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    }
}

/** TvPlayer top line: logo, number · name, and LIVE (or how far behind). */
@Composable
private fun TvTopInfo(c: Channel, state: String?) =
    Row(Modifier.padding(start = TvSafe.Horizontal, end = TvSafe.Horizontal, top = TvSafe.Vertical + 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ChannelLogo(c.icon, c.name, size = 48.dp)
        androidx.tv.material3.Text(listOfNotNull(c.num.takeIf { it > 0 }?.toString(), c.name).joinToString(" · "), style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        TvStateBadge(state ?: "LIVE", live = state == null)
        if (c.archive && state == null) TvStateBadge("Catch-up", live = false)
    }

/**
 * TvPlayer's bottom info on the scrim: what's on, its progress, then the controls row (OK) or, while browsing with
 * ◀▶, the strip of neighbouring channels.
 */
@Composable
private fun TvInfo(
    app: AppState, c: Channel, now: Programme?, at: Long, list: List<Channel>, index: Int, browsing: Boolean, stripFocus: FocusRequester, ctrlFocus: FocusRequester,
    live: Boolean, paused: Boolean, onStrip: (Boolean) -> Unit, onControls: (Boolean) -> Unit, onPick: (Channel) -> Unit, onMenu: () -> Unit,
    onPlayPause: () -> Unit, onLive: () -> Unit, onSeek: (Long) -> Unit, onTracks: () -> Unit, onChannels: () -> Unit, poke: () -> Unit,
) {
    val cs = androidx.tv.material3.MaterialTheme.colorScheme
    val ty = androidx.tv.material3.MaterialTheme.typography
    Column(Modifier.fillMaxWidth().padding(start = TvSafe.Horizontal, end = TvSafe.Horizontal, bottom = TvSafe.Vertical), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.tv.material3.Text(now?.title ?: c.name, style = ty.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val next = app.programmesFor(c).upcoming().firstOrNull()
        androidx.tv.material3.Text(listOfNotNull(now?.let { "${fmtTime(it.start)} – ${fmtTime(it.end)}" } ?: "No guide for this channel",
            next?.let { "Next ${fmtTime(it.start)} · ${it.title}" }).joinToString("  ·  "), style = ty.bodyLarge, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (now != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val span = (now.end - now.start).coerceAtLeast(1).toFloat()
            androidx.tv.material3.Text(fmtTime(now.start), style = ty.labelLarge, color = cs.onSurfaceVariant)
            TvProgress((at - now.start) / span, live = (nowSec() - now.start) / span, modifier = Modifier.weight(1f))
            androidx.tv.material3.Text(fmtTime(now.end), style = ty.labelLarge, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.CenterStart) {
            if (browsing) {
                val strip = remember(list, index) { if (list.isEmpty()) emptyList() else (-2..2).map { list[Math.floorMod(index + it, list.size)] }.distinctBy { it.id } }
                Row(Modifier.fillMaxWidth().onFocusChanged { onStrip(it.hasFocus) }.focusGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    strip.forEach { s ->
                        key(s.id) {
                            val on = s.id == c.id
                            TvSurface({ onPick(s) }, Modifier.weight(1f).height(64.dp), onLongClick = onMenu, selected = on, idle = Tok.ContainerHighest.copy(alpha = 0.8f),
                                focusRequester = if (on) stripFocus else null) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ChannelLogo(s.icon, s.name, size = 40.dp, playing = on)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        androidx.tv.material3.Text(listOfNotNull(s.num.takeIf { it > 0 }?.toString(), s.name).joinToString(" · "), style = ty.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        androidx.tv.material3.Text(app.programmesFor(s).current()?.title ?: "Live", style = ty.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.8f))
                                    }
                                }
                            }
                        }
                    }
                }
            } else Row(Modifier.onFocusChanged { onControls(it.hasFocus) }.focusGroup(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                fun act(f: () -> Unit): () -> Unit = { f(); poke() }
                TvControl(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null, act(onPlayPause), primary = true, focusRequester = ctrlFocus,
                    onLongClick = onMenu, description = if (paused) "Play" else "Pause")
                if (!live) TvControl(Icons.Rounded.FiberManualRecord, "Go live", act(onLive), onLongClick = onMenu)
                if (c.archive && now != null) TvControl(Icons.Rounded.Restore, "From the start", act { onSeek(now.start) }, onLongClick = onMenu)
                TvControl(Icons.Rounded.Subtitles, "Audio & subtitles", act(onTracks), onLongClick = onMenu)
                val fav = app.isFav("c${c.id}")
                TvControl(if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline, if (fav) "Saved" else "Save", act { app.toggleFav("c${c.id}") }, onLongClick = onMenu)
                TvControl(Icons.AutoMirrored.Rounded.List, "Channels", onChannels, onLongClick = onMenu)
                TvControl(Icons.Rounded.MoreVert, null, onMenu, description = "More options")
            }
        }
        TvHints("▲▼" to "Channel", "◀▶" to "Browse", "◀ hold" to "All channels", modifier = Modifier.align(Alignment.End))
    }
}
