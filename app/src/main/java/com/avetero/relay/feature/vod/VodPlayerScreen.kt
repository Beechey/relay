package com.avetero.relay.feature.vod

import androidx.compose.ui.unit.coerceIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.focus.onFocusChanged
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.avetero.relay.ui.theme.LiveGradient
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.media3.common.Player
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.PlayItem
import com.avetero.relay.data.model.Resume
import com.avetero.relay.feature.playback.CastingOverlay
import com.avetero.relay.feature.playback.PlayerError
import com.avetero.relay.feature.playback.TracksDialog
import com.avetero.relay.player.FullscreenVideo
import com.avetero.relay.player.PlayerSurface
import com.avetero.relay.ui.components.Art
import com.avetero.relay.ui.components.Badge
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.components.CastButton
import com.avetero.relay.ui.components.Clock
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.IconBtn
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Ico
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.Semi
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import com.avetero.relay.util.fmtDur
import kotlinx.coroutines.delay

/** Board 06: movie / episode player with seek, tracks, speed and next-episode card. */
@Composable
fun VodPlayerScreen(app: AppState, item: PlayItem, next: List<PlayItem>, episodes: List<PlayItem> = emptyList()) {
    FullscreenVideo(app.inPip)
    // the Chromecast while casting, otherwise this device; the effect below restarts when it changes
    val p = app.player.current
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var showUi by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var audio by remember { mutableStateOf("Default") }
    var subs by remember { mutableStateOf(if (app.store.subtitlesOn) "On" else "Off") }
    var speed by remember { mutableFloatStateOf(1f) }
    var zoom by remember { mutableStateOf(app.store.zoomDefault) }
    val back = app.store.skipBackSec * 1000L
    val fwd = app.store.skipFwdSec * 1000L
    var ended by remember { mutableStateOf(false) }
    var seekHint by remember { mutableStateOf<String?>(null) }
    var tracksOpen by remember { mutableStateOf(false) }
    LaunchedEffect(seekHint) { if (seekHint != null) { delay(1_200); seekHint = null } }
    val root = remember { FocusRequester() }
    val playBtn = remember { FocusRequester() }
    val speedBtn = remember { FocusRequester() }
    val sideBtn = remember { FocusRequester() }

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
    var episodesOpen by remember { mutableStateOf(false) }

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
    LaunchedEffect(showUi) { if (showUi) runCatching { (if (preview != null) scrubFocus else playBtn).requestFocus() } }
    // Back first hides the controls; the next Back leaves the player
    BackHandler(showUi && !app.inPip && !tracksOpen && !episodesOpen) { if (app.player.error != null) app.back() else { showUi = false; runCatching { root.requestFocus() } } }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && showUi) poke++; false }
            .focusRequester(root).focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || showUi) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft, Key.MediaRewind -> { nudge(false, e.nativeKeyEvent.repeatCount); true }
                    Key.DirectionRight, Key.MediaFastForward -> { nudge(true, e.nativeKeyEvent.repeatCount); true }
                    Key.MediaPlayPause -> { if (p.isPlaying) p.pause() else p.play(); poke++; true }
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
        seekHint?.let { Badge(it, C.Ground, C.Text, Modifier.align(Alignment.Center)) }
        if (tracksOpen) TracksDialog(app) { tracksOpen = false; poke++ }
        if (episodesOpen) EpisodesDialog(episodes, item, { episodesOpen = false; playEpisode(it) }) { episodesOpen = false; poke++ }

        if (!app.inPip && next.isNotEmpty() && dur > 0 && dur - pos in 1..30_000) {
            Row(Modifier.align(Alignment.BottomEnd).padding(start = 16.dp, end = if (compact()) 16.dp else 40.dp, bottom = if (showUi) 196.dp else 40.dp).widthIn(max = 400.dp)
                .clip(RoundedCornerShape(20.dp)).background(C.Glass).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Art(next.first().image, "", 1, Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(12.dp)), showTitle = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("Up next in ${(dur - pos) / 1000} s", 13, Semi, C.Muted); T(next.first().title, 14, Heavy, maxLines = 2, lineHeight = 18)
                    Btn("Play now", ::playNext, style = BtnStyle.Primary, height = 34.dp)
                }
            }
        }

        AnimatedVisibility(showUi && !app.inPip, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            // board 06: a soft fade, not a bar, so the picture stays visible
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))).statusBarsPadding().height(if (compact()) 72.dp else 120.dp)
                .padding(horizontal = if (compact()) 16.dp else 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (compact()) IconBtn(Icons.Back, "Back", { app.back() }, size = 44.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { T(item.meta, 13, Semi, C.Soft); T(item.title, if (compact()) 18 else 24, Heavy) }
                if (compact()) {
                    val activity = LocalContext.current as? com.avetero.relay.MainActivity
                    IconBtn(Icons.Pip, "Picture in picture", { activity?.enterPip() }, size = 44.dp)
                }
                if (app.player.canCast) {
                    CastButton(size = 44.dp)
                }
                Box(Modifier.clip(RoundedCornerShape(24.dp)).background(C.Glass3).padding(horizontal = 16.dp, vertical = 8.dp)) { Clock(18) }
            }
        }
        AnimatedVisibility(showUi && !app.inPip, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            val pad = if (compact()) 16.dp else 40.dp
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000)))).navigationBarsPadding()
                .padding(start = pad, end = pad, top = 90.dp, bottom = if (compact()) 20.dp else 36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    T(fmtDur(pos), 14, Bold, mono = true)
                    ScrubBar(pos, dur, p.bufferedPosition, preview, { preview = it; poke++ }, { p.seekTo(it); preview = null }, nudge = ::nudge, focus = scrubFocus,
                        modifier = Modifier.weight(1f).focusProperties { down = playBtn })
                    T("−" + fmtDur((dur - (preview ?: pos)).coerceAtLeast(0)), 14, Bold, mono = true)
                }
                val phone = compact()
                val transport: @Composable () -> Unit = {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        SkipBtn("−${back / 1000}", "Back ${back / 1000} seconds", { p.seekTo((p.currentPosition - back).coerceAtLeast(0)) },
                            if (phone) Modifier else Modifier.focusProperties { left = speedBtn })
                        FocusSurface({ if (p.isPlaying) p.pause() else p.play() }, Modifier.size(68.dp), idle = C.Accent, shape = RoundedCornerShape(34.dp), focusRequester = playBtn,
                            label = if (app.player.playing) "Pause" else "Play") { _ ->
                            Ico(if (app.player.playing) Icons.Pause else Icons.Play, C.OnLight, 24.dp, Modifier.align(Alignment.Center))
                        }
                        // the gap to the side buttons is wide; point the remote at them explicitly
                        SkipBtn("+${fwd / 1000}", "Forward ${fwd / 1000} seconds", { p.seekTo(p.currentPosition + fwd) },
                            if (phone) Modifier else Modifier.focusProperties { right = sideBtn })
                    }
                }
                if (phone) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { transport() }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Audio, subtitles & quality", { tracksOpen = true }, height = 40.dp)
                        Btn(if (app.player.muted) "Unmute" else "Mute", { app.player.toggleMute() }, height = 40.dp)
                        Btn("%.2gx".format(speed), { speed = listOf(1f, 1.25f, 1.5f, 2f, 0.75f).let { it[(it.indexOf(speed) + 1) % it.size] }; p.setPlaybackSpeed(speed) }, height = 40.dp)
                        if (episodes.size > 1) Btn("Episodes", { episodesOpen = true }, height = 40.dp)
                        if (next.isNotEmpty()) Btn("Next episode", ::playNext, height = 40.dp)
                        Btn(if (zoom) "Zoom" else "Fit", { zoom = !zoom }, height = 40.dp)
                    }
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Audio, subtitles & quality", { tracksOpen = true }, height = 44.dp)
                        IconBtn(if (app.player.muted) Icons.Muted else Icons.Volume, if (app.player.muted) "Unmute" else "Mute", { app.player.toggleMute() }, size = 44.dp, round = true)
                        Btn("%.2gx".format(speed), {
                            speed = listOf(1f, 1.25f, 1.5f, 2f, 0.75f).let { it[(it.indexOf(speed) + 1) % it.size] }; p.setPlaybackSpeed(speed)
                        }, height = 44.dp, focusRequester = speedBtn)
                    }
                    transport()
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        if (episodes.size > 1) Btn("Episodes", { episodesOpen = true }, height = 44.dp, focusRequester = sideBtn)
                        if (next.isNotEmpty()) Btn("Next ▶", ::playNext, height = 44.dp, focusRequester = if (episodes.size > 1) null else sideBtn)
                        Btn(if (zoom) "Zoom" else "Fit", { zoom = !zoom }, height = 44.dp, focusRequester = if (episodes.size > 1 || next.isNotEmpty()) null else sideBtn)
                    }
                }
            }
        }
    }
}

/** Board 06: skip buttons are round glass pills labelled with the seconds they jump. */
@Composable
private fun SkipBtn(text: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    FocusSurface(onClick, modifier.size(52.dp), idle = C.Glass3, shape = CircleShape, label = label) { f ->
        T(text, 14, Heavy, if (f) C.OnLight else C.Text, Modifier.align(Alignment.Center))
    }

/**
 * Board 06 scrub bar. Remote: focus it, then Left/Right move a preview point (faster the longer you hold), OK jumps
 * there now, Back cancels. Touch: drag along it or tap a point. The target time shows above the knob.
 */
@Composable
private fun ScrubBar(pos: Long, dur: Long, buffered: Long, preview: Long?, setPreview: (Long?) -> Unit, seek: (Long) -> Unit,
                     nudge: (Boolean, Int) -> Unit, focus: FocusRequester, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shown = preview ?: pos
    val frac = if (dur > 0) (shown.toFloat() / dur).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(
        modifier.height(40.dp).focusRequester(focus).onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                when {
                    e.key == Key.DirectionLeft || e.key == Key.DirectionRight -> { if (e.type == KeyEventType.KeyDown) nudge(e.key == Key.DirectionRight, e.nativeKeyEvent.repeatCount); true }
                    (e.key == Key.DirectionCenter || e.key == Key.Enter) && preview != null -> { if (e.type == KeyEventType.KeyUp) seek(preview); true }
                    e.key == Key.Back && preview != null -> { if (e.type == KeyEventType.KeyUp) setPreview(null); true }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(dur) { detectTapGestures { o -> if (dur > 0) seek((o.x / size.width * dur).toLong().coerceIn(0, dur)) } }
            .pointerInput(dur) {
                detectHorizontalDragGestures(onDragEnd = { preview?.let(seek) }, onDragCancel = { setPreview(null) }) { ch, _ ->
                    if (dur > 0) setPreview((ch.position.x / size.width * dur).toLong().coerceIn(0, dur))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val active = focused || preview != null
        Box(Modifier.fillMaxWidth().height(if (active) 8.dp else 6.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = .2f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(if (dur > 0) (buffered.toFloat() / dur).coerceIn(0f, 1f) else 0f).background(Color.White.copy(alpha = .18f)))
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(LiveGradient))
        }
        val knob = if (active) 24.dp else 18.dp
        Box(Modifier.offset(x = maxWidth * frac - knob / 2).size(knob).clip(CircleShape).background(C.Text))
        if (preview != null) Box(Modifier.offset(x = (maxWidth * frac - 36.dp).coerceIn(0.dp, maxWidth - 72.dp), y = (-34).dp).width(72.dp)
            .clip(RoundedCornerShape(10.dp)).background(C.Text).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            T(fmtDur(preview), 13, Bold, C.OnLight, mono = true)
        }
    }
}
