package com.avetero.relay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.dp
import com.avetero.relay.AppState
import com.avetero.relay.PlayerSurface
import com.avetero.relay.FullscreenVideo
import com.avetero.relay.AppState.Companion.ALL
import com.avetero.relay.AppState.Companion.FAVS
import com.avetero.relay.Channel
import com.avetero.relay.Programme
import com.avetero.relay.Screen
import com.avetero.relay.fmtTime
import kotlinx.coroutines.delay

@Composable
fun Clock(size: Int = 13, color: Color = C.Text) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() / 1000 } }
    T(fmtTime(now), size, color = color, mono = true)
}

fun nowSec() = System.currentTimeMillis() / 1000
fun List<Programme>?.current() = this?.firstOrNull { nowSec() in it.start until it.end }
fun List<Programme>?.upcoming() = this?.filter { it.start >= nowSec() }.orEmpty()
fun Programme.progress() = ((nowSec() - start).toFloat() / (end - start)).coerceIn(0f, 1f)
fun Programme.minutesLeft() = ((end - nowSec()) / 60).let { if (it >= 60) "${it / 60} h ${it % 60} min left" else "$it min left" }

/** Board 04: groups · channels · preview. OK plays in preview, OK again goes fullscreen, hold OK opens the menu. Phone: video on top, chips, one column. */
@Composable
fun LiveScreen(app: AppState, nav: (Tab) -> Unit, initialGroup: String? = null) = WithRail(Tab.Live, nav) {
    var groupId by rememberSaveable(initialGroup) {
        val last = app.store.lastLiveGroup.takeIf { app.store.liveStart == "Last category" && it.isNotEmpty() }
        mutableStateOf(initialGroup ?: last ?: ALL)
    }
    LaunchedEffect(groupId) { app.store.lastLiveGroup = groupId }
    // Settings → Live TV → Resume the last channel
    LaunchedEffect(Unit) {
        if (app.store.resumeLastChannel && app.player.url == null)
            app.store.recentChannels(app.account).firstOrNull()?.let { id -> app.channels.firstOrNull { it.id == id } }?.let { app.player.play(app.liveUrl(it)) }
    }
    var drawer by remember { mutableStateOf(false) }
    val list = remember(groupId, app.channels, app.favourites, app.hidden) { app.channelsIn(groupId) }
    var focusedCh by remember { mutableStateOf<Channel?>(null) }
    var menuFor by remember { mutableStateOf<Channel?>(null) }
    val playing = list.firstOrNull { app.player.url == app.liveUrl(it) } ?: app.channels.firstOrNull { app.player.url == app.liveUrl(it) }
    val firstRow = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val phone = compact()

    val groups = rememberLiveGroups(app)
    LaunchedEffect(groupId) { listState.scrollToItem(0); if (!phone) runCatching { firstRow.requestFocus() } }

    val channelList: @Composable (Modifier) -> Unit = { mod ->
        Column(mod) {
            if (list.isEmpty()) EmptyState(if (groupId == FAVS) "No favourites yet" else "No channels here", "Hold OK (or press and hold) on any channel to add it to Favourites.")
            LazyColumn(state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                    LaunchedEffect(c.id) { app.loadEpg(c) }
                    val now = app.programmesFor(c).current()
                    FocusSurface(
                        onClick = {
                            if (playing?.id == c.id) app.push(Screen.LivePlayer(c, list))
                            else { app.player.play(app.liveUrl(c)); app.store.addRecent(app.account, c.id) }
                        },
                        modifier = Modifier.fillMaxWidth().height(if (phone) 64.dp else 68.dp),
                        selected = playing?.id == c.id,
                        focusRequester = if (i == 0) firstRow else null,
                        onFocus = { focusedCh = c },
                        onLongClick = { menuFor = c },
                    ) { f ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 14.dp)) {
                            if (!phone && app.store.showNumbers) T(if (c.num > 0) c.num.toString() else "", 13, color = sub(f), mono = true, modifier = Modifier.width(40.dp))
                            Logo(c.icon, c.name, f, Modifier.size(48.dp, 36.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row {
                                    T(c.name, 15, Bold, fg(f), Modifier.weight(1f))
                                    if (app.isFav("c${c.id}")) Ico(Icons.StarFilled, if (f) C.OnLight else C.Accent, 14.dp)
                                    if (now != null) T("  " + now.minutesLeft(), 13, color = sub(f))
                                }
                                T(now?.title ?: "No programme info", 13, color = sub(f))
                                ProgressLine(now?.progress() ?: 0f, Modifier.fillMaxWidth(), if (f) C.LightTrack else C.Track, if (f) C.OnLight else C.Accent)
                            }
                        }
                    }
                }
            }
        }
    }

    if (phone) Column(Modifier.weight(1f).fillMaxHeight()) {
        if (playing != null) {
            FocusSurface({ app.push(Screen.LivePlayer(playing, list)) }, Modifier.fillMaxWidth().aspectRatio(16f / 9f), idle = Color.Black, shape = RoundedCornerShape(0.dp), label = "Watch fullscreen") { _ ->
                // keyed on PiP: the floating window borrows the video surface, so rebuild this one afterwards
                androidx.compose.runtime.key(app.inPip) { PlayerSurface(app.player.player, Modifier.fillMaxSize()) }
                Badge("LIVE", C.Live, Color.White, Modifier.align(Alignment.TopStart).padding(10.dp))
                Badge("Tap for fullscreen", C.Ground, C.Soft, Modifier.align(Alignment.BottomEnd).padding(10.dp))
                app.player.error?.let { T("Can't play: $it", 13, Bold, C.Error, Modifier.align(Alignment.BottomStart).padding(10.dp)) }
            }
            val now = app.programmesFor(playing).current()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                T(playing.name, 13, Bold, C.Accent)
                T(now?.title ?: "No programme info", 17, Heavy)
            }
        } else T("Live TV", 26, Heavy, modifier = Modifier.padding(16.dp))
        val current = groups.firstOrNull { it.id == groupId }
        CategoryButton(current?.name ?: "Categories", current?.count ?: 0) { drawer = true }
        if (drawer) CategoryDrawer("Live TV", groups, groupId, { groupId = it }, onHide = { hideLive(app, it, groupId) { g -> groupId = g } }) { drawer = false }
        channelList(Modifier.weight(1f).padding(horizontal = 8.dp).padding(top = 10.dp))
    } else {
        GroupColumn("Groups", groups, groupId, { groupId = it }, onHide = { hideLive(app, it, groupId) { g -> groupId = g } })
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp).padding(top = 28.dp)) {
            Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Label("${groups.firstOrNull { it.id == groupId }?.name ?: ""} · ${list.size} channels", Modifier.weight(1f))
                Clock(color = C.Muted)
            }
            channelList(Modifier.fillMaxSize())
        }
        Preview(app, focusedCh ?: playing, playing) { c -> app.push(Screen.LivePlayer(c, list)) }
    }

    menuFor?.let { c -> ChannelMenu(app, c, { menuFor = null }) }
}

@Composable
private fun Preview(app: AppState, c: Channel?, playing: Channel?, onFullscreen: (Channel) -> Unit) {
    Column(Modifier.width(440.dp).fillMaxHeight().padding(start = 8.dp, end = 28.dp, top = 28.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // the preview itself is a button: OK or a click goes fullscreen
        // only a focus stop when there's something to make fullscreen
        if (playing == null) Box(Modifier.size(404.dp, 227.dp).clip(RoundedCornerShape(14.dp)).background(C.Control)) {
            T("Press OK on a channel to preview", 13, color = C.Muted, modifier = Modifier.align(Alignment.Center))
        } else FocusSurface({ onFullscreen(playing) }, Modifier.size(404.dp, 227.dp), idle = C.Control, shape = RoundedCornerShape(14.dp), label = "Watch fullscreen") { f ->
            if (playing != null) androidx.compose.runtime.key(app.inPip) { PlayerSurface(app.player.player, Modifier.fillMaxSize().padding(if (f) 4.dp else 0.dp).clip(RoundedCornerShape(11.dp))) }
            else T("Press OK on a channel to preview", 13, color = sub(f), modifier = Modifier.align(Alignment.Center))
            if (playing != null) Badge("LIVE", C.Live, Color.White, Modifier.align(Alignment.TopStart).padding(12.dp))
            if (playing != null) Badge("Fullscreen", if (f) C.Text else C.Ground, if (f) C.OnLight else C.Soft, Modifier.align(Alignment.BottomEnd).padding(12.dp))
            app.player.error?.let { T("Can't play: $it", 13, Bold, C.Error, Modifier.align(Alignment.BottomStart).padding(12.dp)) }
        }
        if (c != null) {
            val list = app.programmesFor(c)
            val now = list.current()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(c.name.uppercase() + (now?.let { " · NOW · ${fmtTime(it.start)} – ${fmtTime(it.end)}" } ?: ""), 13, Bold, C.Accent)
                T(now?.title ?: "No programme info", 26, Heavy, maxLines = 2, lineHeight = 31)
                if (now != null) T(now.description, 14, color = C.Soft, maxLines = 4, lineHeight = 21)
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                list.upcoming().take(3).forEach { p ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        T(fmtTime(p.start), 14, color = C.Muted, mono = true, modifier = Modifier.width(48.dp)); T(p.title, 14, Semi)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) { KeyHint("OK", if (playing != null) "OK again: fullscreen" else "Preview"); KeyHint("Hold OK", "Favourite"); KeyHint("◀", "Groups") }
    }
}

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
        app.store.addRecent(app.account, c.id)
        app.loadEpg(c)
    }
    LaunchedEffect(poke) { showInfo = true; delay(5_000); showInfo = false }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun zap(d: Int) { if (list.isNotEmpty()) { index = Math.floorMod(index + d, list.size); poke++ } }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { zap(-1); true }
                    Key.DirectionDown, Key.ChannelDown -> { zap(1); true }
                    Key.DirectionCenter, Key.Enter -> { poke++; true }
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
                // phones: keep watching in a small floating window
                if (compact()) {
                    val activity = androidx.compose.ui.platform.LocalContext.current as? com.avetero.relay.MainActivity
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
                    if (compact()) Btn("Audio & quality", { tracksOpen = true }, height = 40.dp) else Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) { KeyHint("▲▼", "Channels"); KeyHint("Hold OK", "Audio & quality"); KeyHint("Back", "Channel list") }
                }
            }
        }
        if (menu) ChannelMenu(app, c, { menu = false; runCatching { focus.requestFocus() } }, inPlayer = true)
        if (tracksOpen) TracksDialog(app) { tracksOpen = false; runCatching { focus.requestFocus() } }
    }
}

/** Board 19 player error. */
@Composable
fun PlayerError(err: String, onRetry: () -> Unit, onSwitch: (() -> Unit)?, hls: Boolean = false) =
    Box(Modifier.fillMaxSize().background(Color(0xCC05070B)), contentAlignment = Alignment.Center) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Column(Modifier.padding(16.dp).widthIn(max = 420.dp).clip(RoundedCornerShape(18.dp)).background(C.Surface).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Ico(Icons.Warn, C.Error, 26.dp)
            T("This isn't playing", 20, Heavy)
            T("The stream stopped or the format isn't supported ($err).", 14, color = C.Soft, maxLines = 3, lineHeight = 21)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Retry", onRetry, style = BtnStyle.Light, height = 44.dp, focusRequester = first)
                if (onSwitch != null) Btn(if (hls) "Try MPEG-TS" else "Try HLS", onSwitch, height = 44.dp)
            }
        }
    }

/** Favourites, All channels, then the provider's live categories that have channels. Shared by Live TV and the Guide. */
@Composable
fun rememberLiveGroups(app: AppState): List<Group> = remember(app.liveCatsVisible, app.channels, app.favourites) {
    val counts = app.channels.groupingBy { it.categoryId }.eachCount()
    listOf(Group(FAVS, "Favourites", app.favourites.count { it.startsWith("c") }), Group(ALL, "All channels", app.channelsVisible.size)) +
        app.liveCatsVisible.map { Group(it.id, it.name, counts[it.id] ?: 0) }.filter { it.count > 0 }
}

/** Hides a live category; if it was the one showing, falls back to All channels. */
fun hideLive(app: AppState, g: Group, current: String, setGroup: (String) -> Unit) {
    app.setCategoryHidden("live:${g.id}", true)
    if (g.id == current) setGroup(ALL)
    app.message = "Hidden “${g.name}”"
}
