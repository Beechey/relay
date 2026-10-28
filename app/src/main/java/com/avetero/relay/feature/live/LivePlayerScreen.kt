package com.avetero.relay.feature.live

import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.util.nowSec
import com.avetero.relay.ui.theme.Semi
import androidx.compose.ui.graphics.Brush
import com.avetero.relay.ui.components.Pill
import com.avetero.relay.ui.components.LivePill
import com.avetero.relay.ui.components.settle
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.avetero.relay.app.AppState.Companion.ALL
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.current
import com.avetero.relay.data.model.progress
import com.avetero.relay.data.model.upcoming
import com.avetero.relay.feature.playback.CastingOverlay
import com.avetero.relay.feature.playback.PlayerError
import com.avetero.relay.feature.playback.TracksDialog
import com.avetero.relay.player.FullscreenVideo
import com.avetero.relay.player.PlayerSurface
import com.avetero.relay.ui.components.Badge
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.CastButton
import com.avetero.relay.ui.components.Clock
import com.avetero.relay.ui.components.IconBtn
import com.avetero.relay.ui.components.KeyHint
import com.avetero.relay.ui.components.Logo
import com.avetero.relay.ui.components.ProgressLine
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import com.avetero.relay.util.fmtTime
import kotlinx.coroutines.delay

/** Board 06: fullscreen live with info bar; up/down and channel keys zap. */
@Composable
fun LivePlayerScreen(app: AppState, start: Channel, startList: List<Channel>, openList: Boolean = false, startGroup: String = ALL) {
    FullscreenVideo(app.inPip)
    val tv = !compact()
    // the channel list can switch category, so the zapping order follows whatever was picked last
    var list by remember { mutableStateOf(startList) }
    var index by remember { mutableIntStateOf(startList.indexOfFirst { it.id == start.id }.coerceAtLeast(0)) }
    val c = list.getOrElse(index) { start }
    var showInfo by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var tracksOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    // TV: the sliding channel list, number entry and the previous channel
    var listOpen by remember { mutableStateOf(tv && openList) }
    var listPoke by remember { mutableIntStateOf(0) }
    var group by remember { mutableStateOf(startGroup) }
    var digits by remember { mutableStateOf("") }
    var previous by remember { mutableStateOf<Channel?>(null) }
    // live pause: when it was paused, and how far behind live the picture is (catch-up channels can be watched behind)
    var pausedAt by remember { mutableStateOf<Long?>(null) }
    var behind by remember { mutableStateOf(0L) }
    var clock by remember { mutableStateOf(nowSec()) }
    LaunchedEffect(pausedAt) { while (pausedAt != null) { clock = nowSec(); delay(1_000) } }

    LaunchedEffect(c.id) {
        delay(400) // debounce fast zapping
        pausedAt = null; behind = 0
        app.player.play(app.liveUrl(c), title = c.name)
        app.addRecentChannel(c.id)
        app.loadEpg(c)
    }
    // the info bar stays up while paused
    LaunchedEffect(poke, pausedAt) { showInfo = true; if (pausedAt == null) { delay(5_000); showInfo = false } }
    LaunchedEffect(Unit) { focus.settle() }

    fun go(to: Channel, inList: List<Channel> = list) {
        if (to.id != c.id) previous = c
        list = inList; index = inList.indexOfFirst { it.id == to.id }.coerceAtLeast(0); poke++
    }
    fun zap(d: Int) { if (list.isNotEmpty()) { previous = c; index = Math.floorMod(index + d, list.size); poke++ } }
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
    fun goLive() { pausedAt = null; behind = 0; app.player.play(app.liveUrl(c), title = c.name); poke++ }
    fun closeList() { listOpen = false; runCatching { focus.requestFocus() } }
    // Back closes the channel list, then the info bar; the next Back leaves the player
    // Back closes the channel list; otherwise it leaves Live (the info bar hides by itself, it doesn't need a press)
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

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val code = e.nativeKeyEvent.keyCode
                val digit = when (code) { in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> code - android.view.KeyEvent.KEYCODE_0
                    in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> code - android.view.KeyEvent.KEYCODE_NUMPAD_0; else -> -1 }
                when {
                    digit >= 0 -> { if (digits.length < 5) digits += digit; true }
                    code == android.view.KeyEvent.KEYCODE_LAST_CHANNEL -> { previous?.let { go(it) }; true }
                    e.key == Key.ChannelUp -> { zap(-1); true }
                    e.key == Key.ChannelDown -> { zap(1); true }
                    // with the list open, the arrows move through it instead of zapping
                    listOpen -> { listPoke++; false }
                    e.key == Key.DirectionUp -> { zap(-1); true }
                    e.key == Key.DirectionDown -> { zap(1); true }
                    tv && e.key == Key.DirectionLeft -> { listOpen = true; listPoke++; true }
                    // hold OK = options (audio, subtitles, quality, favourite); a press opens the channel list on TV
                    e.key == Key.DirectionCenter || e.key == Key.Enter -> {
                        if (e.nativeKeyEvent.repeatCount > 0 || e.nativeKeyEvent.isLongPress) menu = true
                        else if (tv) { listOpen = true; listPoke++; poke++ } else poke++
                        true
                    }
                    e.key == Key.Menu -> { menu = true; true }
                    e.key == Key.MediaPlayPause -> { if (pausedAt != null) resume() else pause(); true }
                    e.key == Key.MediaPause -> { pause(); true }
                    e.key == Key.MediaPlay -> { resume(); true }
                    (e.key == Key.MediaFastForward || e.key == Key.DirectionRight) && behind > 0 && pausedAt == null -> { goLive(); true }
                    else -> false
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { if (showInfo) showInfo = false else poke++ }, onLongPress = { menu = true }) }
            // phones: swipe up for the next channel, down for the previous one
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -120f) zap(1) else if (total > 120f) zap(-1) }) { _, dy -> total += dy }
            },
    ) {
        PlayerSurface(app.player.player, Modifier.fillMaxSize(), zoom = app.store.zoomDefault)
        app.player.castDevice?.let { CastingOverlay(it, c.name, app.player.castStatus) }
        app.player.error?.let { err ->
            PlayerError(err, onRetry = { app.player.retry() }, onSwitch = {
                app.hls = !app.hls; app.store.hls = app.hls; app.player.play(app.liveUrl(c))
            }, hls = app.hls)
        }
        // phones have no Back key on screen: show one with the info bar
        AnimatedVisibility(!tv && showInfo && !app.inPip, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp), enter = fadeIn(), exit = fadeOut()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBtn(Icons.Back, "Back to channels", { app.back() }, size = 48.dp)
                IconBtn(if (pausedAt != null) Icons.Play else Icons.Pause, if (pausedAt != null) "Play" else "Pause", { if (pausedAt != null) resume() else pause() }, size = 48.dp)
                if (behind > 0 && pausedAt == null) Btn("Go live", { goLive() }, style = BtnStyle.Primary, height = 48.dp)
                IconBtn(if (app.player.muted) Icons.Muted else Icons.Volume, if (app.player.muted) "Unmute" else "Mute", { app.player.toggleMute(); poke++ }, size = 48.dp)
                // phones: keep watching in a small floating window
                if (compact()) {
                    val activity = LocalContext.current as? com.avetero.relay.MainActivity
                    IconBtn(Icons.Pip, "Picture in picture", { activity?.enterPip() }, size = 48.dp)
                }
                if (app.player.canCast) {
                    CastButton()
                }
            }
        }
        AnimatedVisibility(showInfo && !app.inPip, Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 40.dp), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.clip(RoundedCornerShape(24.dp)).background(C.Glass).padding(horizontal = 16.dp, vertical = 8.dp)) { Clock(20) }
        }
        AnimatedVisibility((showInfo || listOpen) && !app.inPip, Modifier.align(Alignment.BottomCenter).then(if (tv) Modifier.padding(start = if (listOpen) 584.dp else 24.dp, end = 24.dp, bottom = 24.dp) else Modifier), enter = fadeIn(), exit = fadeOut()) {
            val epg = app.programmesFor(c)
            // paused or behind live: show the programme (and how far into it) at the point you're watching
            val watching = (if (pausedAt != null) pausedAt!! else clock.coerceAtLeast(nowSec())) - behind
            val now = if (pausedAt == null && behind == 0L) epg.current() else epg?.firstOrNull { watching in it.start until it.end }
            val nowProgress = now?.let { ((watching - it.start).toFloat() / (it.end - it.start)).coerceIn(0f, 1f) } ?: 0f
            val pad = if (compact()) 16.dp else 40.dp
            // board 02: LIVE and the channel, the programme large, its times either side of the progress, then the keys
            if (tv) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Glass).padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val away = pausedAt?.let { clock - it }
                    when {
                        away != null -> Pill("Paused · %d:%02d".format(away / 60, away % 60), bg = C.Accent, color = C.OnLight)
                        behind > 0 -> Pill("${(behind / 60).coerceAtLeast(1)} min behind", bg = C.Accent, color = C.OnLight)
                        else -> LivePill()
                    }
                    T(listOfNotNull(c.num.takeIf { it > 0 }?.toString(), c.name).joinToString(" · "), 13, Semi, C.Muted, Modifier.weight(1f))
                    if (app.isFav("c${c.id}")) Pill("★ Favourite")
                    if (c.archive) Pill("Catch-up")
                }
                T(now?.title ?: c.name, 26, Heavy, maxLines = 2, lineHeight = 30)
                if (now != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    T(fmtTime(now.start), 12, Semi, C.Muted, mono = true)
                    ProgressLine(if (pausedAt == null && behind == 0L) now.progress() else nowProgress, Modifier.weight(1f), C.Glass3, height = 4.dp)
                    T(fmtTime(now.end), 12, Semi, C.Muted, mono = true)
                } else T("No guide for this channel", 13, Semi, C.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KeyHint("◀", "Channels"); KeyHint("▲▼", "Zap")
                    if (behind > 0 && pausedAt == null) KeyHint("▶ / ⏩", "Go live") else KeyHint("⏯", if (pausedAt != null) "Play" else "Pause")
                    KeyHint("0–9", "Go to number"); KeyHint("Hold OK", "Favourite, audio, previous")
                }
            } else
            Column(Modifier.fillMaxWidth().then(if (tv) Modifier.clip(RoundedCornerShape(24.dp)).background(C.Glass) else Modifier.background(C.Ground)).navigationBarsPadding().padding(start = pad, end = pad, top = if (compact()) 16.dp else 28.dp, bottom = if (compact()) 16.dp else 32.dp), verticalArrangement = Arrangement.spacedBy(if (compact()) 12.dp else 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (!compact()) T(if (c.num > 0) c.num.toString() else "", 22, color = C.Muted, mono = true)
                    Logo(c.icon, c.name, false, Modifier.size(if (compact()) 48.dp else 64.dp, if (compact()) 34.dp else 44.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T(c.name, 14, Bold, C.Soft)
                        T(now?.title ?: "No programme info", if (compact()) 18 else 26, Heavy, maxLines = 2)
                    }
                    if (!compact() && app.isFav("c${c.id}")) Badge("Favourite")
                    if (!compact() && c.archive) Badge("Catch-up")
                }
                if (now != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    T(fmtTime(now.start), 13, color = C.Muted, mono = true)
                    ProgressLine(if (pausedAt == null && behind == 0L) now.progress() else nowProgress, Modifier.weight(1f), height = 4.dp)
                    T(fmtTime(now.end), 13, color = C.Muted, mono = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val next = epg.upcoming().firstOrNull()
                    T(if (next != null) "Next  ${fmtTime(next.start)} · ${next.title}" else if (compact()) "Swipe ↑↓ to change channel" else "", 14, Bold, C.Muted, Modifier.weight(1f))
                    if (compact()) Btn("Audio & quality", { tracksOpen = true }, height = 40.dp) else Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        KeyHint("OK", "Channels"); KeyHint("▲▼", "Zap"); KeyHint("0–9", "Number"); KeyHint("Hold OK", "Options")
                    }
                }
            }
        }
        AnimatedVisibility(tv && listOpen && !app.inPip, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to C.Ground.copy(alpha = .55f), .55f to Color.Transparent)))
        }
        AnimatedVisibility(listOpen && !app.inPip, Modifier.align(Alignment.TopStart).padding(24.dp), enter = fadeIn() + slideInHorizontally { -it / 3 }, exit = fadeOut() + slideOutHorizontally { -it / 3 }) {
            // Back must reach the list itself: with a row focused, Compose would otherwise spend the first press clearing focus
            ChannelOverlay(app, group, { group = it }, c, { ch, l -> go(ch, l); app.store.lastLiveGroup = group }, { listPoke++ },
                Modifier.onPreviewKeyEvent { e -> if (e.key == Key.Back) { if (e.type == KeyEventType.KeyUp) closeList(); true } else false })
        }
        if (digits.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 40.dp).clip(RoundedCornerShape(18.dp)).background(C.Glass).padding(horizontal = 22.dp, vertical = 10.dp)) {
            T(digits, 44, Heavy, mono = true)
        }
        if (menu) ChannelMenu(app, c, { menu = false; runCatching { focus.requestFocus() } }, inPlayer = true, onPrevious = previous?.let { p -> { menu = false; go(p) } })
        if (tracksOpen) TracksDialog(app) { tracksOpen = false; runCatching { focus.requestFocus() } }
    }
}

/**
 * TV Live tab (redesign board 02): straight into fullscreen on your last channel, with the channel list open so
 * you can pick something else. Phones keep the picture-on-top list (LiveScreen).
 */
@Composable
fun LiveTvScreen(app: AppState, group: String?) {
    val g = group ?: app.store.lastLiveGroup.takeIf { app.store.liveStart == "Last category" && it.isNotEmpty() } ?: ALL
    val list = remember(g, app.channels, app.hidden, app.favourites) { app.channelsIn(g).ifEmpty { app.channelsIn(ALL) } }
    // chosen once the channels are in; later plays mustn't restart the screen
    val start = remember(list.isNotEmpty()) { app.recentChannels.firstNotNullOfOrNull { id -> list.firstOrNull { it.id == id } } ?: list.firstOrNull() }
    if (start == null) Box(Modifier.fillMaxSize().background(C.Ground), contentAlignment = Alignment.Center) {
        T(if (app.libraryLoading) "Loading channels…" else "No channels in your playlist", 18, Bold, C.Muted)
    } else LivePlayerScreen(app, start, list, openList = true, startGroup = g)
}
