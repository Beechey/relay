package com.avetero.relay.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.AppState.Companion.ALL
import com.avetero.relay.app.AppState.Companion.FAVS
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.current
import com.avetero.relay.data.model.minutesLeft
import com.avetero.relay.data.model.progress
import com.avetero.relay.data.model.upcoming
import com.avetero.relay.feature.categories.categoryMover
import com.avetero.relay.feature.playback.CastingOverlay
import com.avetero.relay.player.PlayerSurface
import com.avetero.relay.ui.components.Badge
import com.avetero.relay.ui.components.CategoryButton
import com.avetero.relay.ui.components.CategoryDrawer
import com.avetero.relay.ui.components.Clock
import com.avetero.relay.ui.components.EmptyState
import com.avetero.relay.ui.components.FilterField
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.GroupColumn
import com.avetero.relay.ui.components.KeyHint
import com.avetero.relay.ui.components.Label
import com.avetero.relay.ui.components.Logo
import com.avetero.relay.ui.components.ProgressLine
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.components.WithRail
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Ico
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.Semi
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import com.avetero.relay.ui.theme.fg
import com.avetero.relay.ui.theme.sub
import com.avetero.relay.util.fmtTime

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
            app.recentChannels.firstOrNull()?.let { id -> app.channels.firstOrNull { it.id == id } }?.let { app.player.play(app.liveUrl(it)) }
    }
    var drawer by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // only the Recent group depends on the recents; rebuilding the list on every play would drop focus
    val recentKey = if (groupId == AppState.RECENT) app.recentChannels else null
    val list = remember(groupId, app.channels, app.favourites, app.hidden, recentKey, query) {
        app.channelsIn(groupId).let { l -> if (query.isBlank()) l else l.filter { it.name.contains(query.trim(), ignoreCase = true) } }
    }
    var focusedCh by remember { mutableStateOf<Channel?>(null) }
    var menuFor by remember { mutableStateOf<Channel?>(null) }
    val playing = list.firstOrNull { app.player.url == app.liveUrl(it) } ?: app.channels.firstOrNull { app.player.url == app.liveUrl(it) }
    val firstRow = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val phone = compact()

    val groups = rememberLiveGroups(app)
    LaunchedEffect(groupId) { listState.scrollToItem(0); if (!phone) runCatching { firstRow.requestFocus() } }

    val channelList: @Composable (Modifier) -> Unit = { mod ->
        Column(mod, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterField(query, { query = it }, "Search channels in this category", if (phone) Modifier.padding(horizontal = 12.dp) else Modifier)
            if (list.isEmpty() && query.isNotBlank()) EmptyState("No matches", "No channel here matches “${query.trim()}”.")
            else if (list.isEmpty()) EmptyState(if (groupId == FAVS) "No favourites yet" else "No channels here", "Hold OK (or press and hold) on any channel to add it to Favourites.")
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                    LaunchedEffect(c.id) { app.loadEpg(c) }
                    val now = app.programmesFor(c).current()
                    FocusSurface(
                        onClick = {
                            if (playing?.id == c.id) app.push(Screen.LivePlayer(c, list))
                            else { app.player.play(app.liveUrl(c)); app.addRecentChannel(c.id) }
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
                                ProgressLine(now?.progress() ?: 0f, Modifier.fillMaxWidth(), if (f) C.LightTrack else C.Track, if (f) C.OnLight else null)
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
                key(app.inPip) { PlayerSurface(app.player.player, Modifier.fillMaxSize()) }
                app.player.castDevice?.let { CastingOverlay(it, playing.name, app.player.castStatus) }
                Badge("LIVE", C.Live, Color.White, Modifier.align(Alignment.TopStart).padding(10.dp))
                Badge("Tap for fullscreen", C.Ground, C.Soft, Modifier.align(Alignment.BottomEnd).padding(10.dp))
                app.player.error?.let { T(it, 13, Bold, C.Error, maxLines = 3, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) }
            }
            val now = app.programmesFor(playing).current()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                T(playing.name, 13, Bold, C.Accent)
                T(now?.title ?: "No programme info", 17, Heavy)
            }
        } else T("Live TV", 26, Heavy, modifier = Modifier.padding(16.dp))
        val current = groups.firstOrNull { it.id == groupId }
        CategoryButton(current?.name ?: "Categories", current?.count ?: 0) { drawer = true }
        if (drawer) CategoryDrawer("Live TV", groups, groupId, { groupId = it }, onHide = { hideLive(app, it, groupId) { g -> groupId = g } }, onMove = categoryMover(app, "live")) { drawer = false }
        channelList(Modifier.weight(1f).padding(horizontal = 8.dp).padding(top = 10.dp))
    } else {
        GroupColumn("Groups", groups, groupId, { groupId = it }, onHide = { hideLive(app, it, groupId) { g -> groupId = g } }, onMove = categoryMover(app, "live"))
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
            if (playing != null) key(app.inPip) { PlayerSurface(app.player.player, Modifier.fillMaxSize().padding(if (f) 4.dp else 0.dp).clip(RoundedCornerShape(11.dp))) }
            else T("Press OK on a channel to preview", 13, color = sub(f), modifier = Modifier.align(Alignment.Center))
            if (playing != null) app.player.castDevice?.let { CastingOverlay(it, playing.name, app.player.castStatus) }
            if (playing != null) Badge("LIVE", C.Live, Color.White, Modifier.align(Alignment.TopStart).padding(12.dp))
            if (playing != null) Badge("Fullscreen", if (f) C.Text else C.Ground, if (f) C.OnLight else C.Soft, Modifier.align(Alignment.BottomEnd).padding(12.dp))
            app.player.error?.let { T(it, 13, Bold, C.Error, maxLines = 3, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) }
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
