package com.avetero.relay.feature.live

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
import com.avetero.relay.feature.playback.PlayerError
import com.avetero.relay.feature.playback.TracksDialog
import com.avetero.relay.player.FullscreenVideo
import com.avetero.relay.player.PlayerSurface
import com.avetero.relay.ui.components.Badge
import com.avetero.relay.ui.components.Btn
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
fun LivePlayerScreen(app: AppState, start: Channel, list: List<Channel>) {
    FullscreenVideo(app.inPip)
    var index by remember { mutableIntStateOf(list.indexOfFirst { it.id == start.id }.coerceAtLeast(0)) }
    val c = list.getOrElse(index) { start }
    var showInfo by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var tracksOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(c.id) {
        delay(400) // debounce fast zapping
        app.player.play(app.liveUrl(c))
        app.addRecentChannel(c.id)
        app.loadEpg(c)
    }
    LaunchedEffect(poke) { showInfo = true; delay(5_000); showInfo = false }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun zap(d: Int) { if (list.isNotEmpty()) { index = Math.floorMod(index + d, list.size); poke++ } }
    // Back first hides the info bar; the next Back leaves the player
    BackHandler(showInfo && !app.inPip && !menu && !tracksOpen) { showInfo = false }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { zap(-1); true }
                    Key.DirectionDown, Key.ChannelDown -> { zap(1); true }
                    // hold OK = options (audio, subtitles, quality, favourite); a press shows the info bar
                    Key.DirectionCenter, Key.Enter -> { if (e.nativeKeyEvent.repeatCount > 0 || e.nativeKeyEvent.isLongPress) menu = true else poke++; true }
                    Key.Menu -> { menu = true; true }
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
        app.player.error?.let { err ->
            PlayerError(err, onRetry = { app.player.retry() }, onSwitch = {
                app.hls = !app.hls; app.store.hls = app.hls; app.player.play(app.liveUrl(c))
            }, hls = app.hls)
        }
        // phones have no Back key on screen: show one with the info bar
        AnimatedVisibility(showInfo && !app.inPip, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp), enter = fadeIn(), exit = fadeOut()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBtn(Icons.Back, "Back to channels", { app.back() }, size = 48.dp)
                IconBtn(if (app.player.muted) Icons.Muted else Icons.Volume, if (app.player.muted) "Unmute" else "Mute", { app.player.toggleMute(); poke++ }, size = 48.dp)
                // phones: keep watching in a small floating window
                if (compact()) {
                    val activity = LocalContext.current as? com.avetero.relay.MainActivity
                    IconBtn(Icons.Pip, "Picture in picture", { activity?.enterPip() }, size = 48.dp)
                }
            }
        }
        AnimatedVisibility(showInfo && !app.inPip, Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 40.dp), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(C.Ground).padding(horizontal = 12.dp, vertical = 6.dp)) { Clock(22) }
        }
        AnimatedVisibility(showInfo && !app.inPip, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            val epg = app.programmesFor(c)
            val now = epg.current()
            val pad = if (compact()) 16.dp else 40.dp
            Column(Modifier.fillMaxWidth().background(C.Ground).navigationBarsPadding().padding(start = pad, end = pad, top = if (compact()) 16.dp else 28.dp, bottom = if (compact()) 16.dp else 32.dp), verticalArrangement = Arrangement.spacedBy(if (compact()) 12.dp else 18.dp)) {
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
                    ProgressLine(now.progress(), Modifier.weight(1f), height = 4.dp)
                    T(fmtTime(now.end), 13, color = C.Muted, mono = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val next = epg.upcoming().firstOrNull()
                    T(if (next != null) "Next  ${fmtTime(next.start)} · ${next.title}" else if (compact()) "Swipe ↑↓ to change channel" else "", 14, Bold, C.Muted, Modifier.weight(1f))
                    if (compact()) Btn("Audio & quality", { tracksOpen = true }, height = 40.dp) else Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) { KeyHint("▲▼", "Channels"); KeyHint("Hold OK", "Mute, audio & quality"); KeyHint("Back", "Channel list") }
                }
            }
        }
        if (menu) ChannelMenu(app, c, { menu = false; runCatching { focus.requestFocus() } }, inPlayer = true)
        if (tracksOpen) TracksDialog(app) { tracksOpen = false; runCatching { focus.requestFocus() } }
    }
}
