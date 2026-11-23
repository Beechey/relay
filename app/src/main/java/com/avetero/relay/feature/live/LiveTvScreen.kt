package com.avetero.relay.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.avetero.relay.app.AppState
import com.avetero.relay.app.AppState.Companion.ALL
import com.avetero.relay.app.AppState.Companion.FAVS
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.current
import com.avetero.relay.data.model.progress
import com.avetero.relay.data.model.upcoming
import com.avetero.relay.feature.categories.CategoryMenu
import com.avetero.relay.feature.categories.categoryMover
import com.avetero.relay.feature.playback.CastingOverlay
import com.avetero.relay.player.PlayerSurface
import com.avetero.relay.ui.components.Group
import com.avetero.relay.ui.m3.ChannelLogo
import com.avetero.relay.ui.m3.segmentShape
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvHints
import com.avetero.relay.ui.tv.TvLift
import com.avetero.relay.ui.tv.TvSurface
import com.avetero.relay.util.fmtTime
import kotlinx.coroutines.delay

/**
 * TV Live (TvLiveBrowse): groups · channels · preview. OK plays a channel in the preview, OK again (or OK on the
 * preview) goes fullscreen; hold OK or ☰ opens its options; hold OK on a group moves or hides it.
 */
@Composable
fun LiveTvScreen(app: AppState, group: String?) {
    var groupId by rememberSaveable(group) {
        mutableStateOf(group ?: app.store.lastLiveGroup.takeIf { app.store.liveStart == "Last category" && it.isNotEmpty() } ?: ALL)
    }
    LaunchedEffect(groupId) { app.store.lastLiveGroup = groupId }
    ResumeLastChannel(app)
    val recentKey = if (groupId == AppState.RECENT) app.recentChannels else null
    val list = remember(groupId, app.channels, app.favourites, app.hidden, recentKey) { app.channelsIn(groupId) }
    val groups = rememberLiveGroups(app)
    val playing = rememberPlaying(app, list)
    LaunchedEffect(playing) { if (playing != null) app.liveChannel = playing }
    var focusedCh by remember { mutableStateOf<Channel?>(null) }
    var menuFor by remember { mutableStateOf<Channel?>(null) }
    var groupMenu by remember { mutableStateOf<Group?>(null) }
    val listState = rememberLazyListState()
    val playingRow = remember { FocusRequester() }
    val firstRow = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    // on a new group, or coming back from fullscreen: land on the playing channel if it's here, else the first
    LaunchedEffect(groupId, list.isNotEmpty()) {
        val at = list.indexOfFirst { it.id == playing?.id }
        listState.scrollToItem((at - 2).coerceAtLeast(0))
        val target = if (at >= 0) playingRow else firstRow
        // the row may not be laid out yet; stop as soon as it takes focus so it never fights the remote
        repeat(10) { if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect; delay(50) }
    }
    fun open(c: Channel) = app.push(Screen.LivePlayer(c, list))
    val cs = MaterialTheme.colorScheme
    val ty = MaterialTheme.typography

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(232.dp).fillMaxHeight().padding(start = 24.dp, end = 16.dp, top = 27.dp)) {
                Header("Groups")
                val gi = groups.indexOfFirst { it.id == groupId }.coerceAtLeast(0)
                LazyColumn(state = rememberLazyListState(gi), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)) {
                    itemsIndexed(groups, key = { _, g -> g.id }) { _, g ->
                        val on = g.id == groupId
                        TvSurface({ groupId = g.id }, Modifier.fillMaxWidth().height(48.dp), onLongClick = { groupMenu = g }, selected = on, shape = RoundedCornerShape(24.dp)) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(groupLabel(g), style = ty.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("%,d".format(g.count), style = ty.labelLarge, color = LocalContentColor.current.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp, top = 27.dp)) {
                Header("${groups.firstOrNull { it.id == groupId }?.name ?: ""} · ${"%,d".format(list.size)} channels")
                if (list.isEmpty()) Text(
                    when { app.libraryLoading -> "Loading channels…"; groupId == FAVS -> "No favourites yet. Hold OK on any channel to add it."; else -> "No channels here." },
                    style = ty.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                LazyColumn(
                    // focusRestorer: closing a channel's options lands back on that channel
                    Modifier.weight(1f).focusRequester(listFocus).focusRestorer().onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Menu) { focusedCh?.let { menuFor = it }; true } else false
                    },
                    listState, verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 64.dp, start = 8.dp, end = 8.dp),
                ) {
                    itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                        LaunchedEffect(c.id) { app.loadEpg(c) }
                        val isPlaying = c.id == playing?.id
                        val epg = app.programmesFor(c)
                        val now = epg.current()
                        val next = if (now != null) epg.upcoming().firstOrNull() else null
                        TvSurface(
                            { if (isPlaying) open(c) else { app.player.play(app.liveUrl(c), title = c.name); app.addRecentChannel(c.id); app.liveChannel = c } },
                            Modifier.animateItem().fillMaxWidth().height(72.dp).onFocusChanged { if (it.isFocused) focusedCh = c },
                            onLongClick = { menuFor = c }, shape = segmentShape(i, list.size),
                            idle = if (isPlaying) Tok.ContainerHigh else Tok.ContainerLow,
                            focusRequester = when { isPlaying -> playingRow; i == 0 -> firstRow; else -> null },
                        ) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (app.store.showNumbers) Text(if (c.num > 0) "${c.num}" else "", style = ty.labelLarge, color = LocalContentColor.current.copy(alpha = 0.8f), modifier = Modifier.width(32.dp))
                                ChannelLogo(c.icon, c.name, Modifier.width(64.dp), size = 40.dp, playing = isPlaying)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(c.name, style = ty.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(listOfNotNull(now?.title ?: "No programme info", next?.let { "next ${fmtTime(it.start)} ${it.title}" }).joinToString(" · "),
                                        style = ty.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (isPlaying) Text("Preview", style = ty.labelLarge, color = cs.primary)
                            }
                        }
                    }
                }
            }
            Preview(app, focusedCh ?: playing, playing) { open(it) }
        }
        TvHints("OK" to if (playing != null) "Preview, again fullscreen" else "Preview", "◀" to "Groups, then menu", "☰" to "Options",
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 27.dp))
        menuFor?.let { c -> ChannelMenu(app, c, { menuFor = null; runCatching { listFocus.requestFocus() } }) }
    }
    groupMenu?.let { g ->
        val move = categoryMover(app, "live")
        CategoryMenu(g.name, { m -> move(g, m); groupMenu = null },
            onHide = if (g.id == FAVS || g.id == ALL || g.id == AppState.RECENT) null else ({ hideLive(app, g, groupId) { groupId = it }; groupMenu = null })) { groupMenu = null }
    }
}

@Composable
private fun Header(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).height(24.dp))

/** The right column: the preview picture (OK goes fullscreen), then what's on the focused channel. */
@Composable
private fun Preview(app: AppState, c: Channel?, playing: Channel?, onFullscreen: (Channel) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ty = MaterialTheme.typography
    Column(Modifier.width(400.dp).fillMaxHeight().padding(start = 24.dp, end = 48.dp, top = 27.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val shape = RoundedCornerShape(16.dp)
        // only a focus stop when there's something to make fullscreen
        if (playing == null) Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(Tok.ContainerLow), contentAlignment = Alignment.Center) {
            Text("Press OK on a channel to preview", style = ty.bodyMedium, color = cs.onSurfaceVariant)
        } else TvSurface({ onFullscreen(playing) }, Modifier.fillMaxWidth().aspectRatio(16f / 9f), shape = shape, lift = TvLift.Card, idle = Tok.ContainerLow) {
            androidx.compose.runtime.key(app.inPip) { PlayerSurface(app.player.player, Modifier.fillMaxSize().clip(shape)) }
            app.player.castDevice?.let { CastingOverlay(it, playing.name, app.player.castStatus) }
            LiveBadge(Modifier.align(Alignment.TopStart).padding(12.dp), style = MaterialTheme.typography.labelLarge)
            app.player.error?.let { Text(it, style = ty.bodyMedium, color = cs.error, maxLines = 3, modifier = Modifier.align(Alignment.BottomStart).background(Tok.Surface.copy(alpha = 0.8f)).padding(12.dp)) }
        }
        if (c != null) {
            val epg = app.programmesFor(c)
            val now = epg.current()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(now?.title ?: c.name, style = ty.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(c.num.takeIf { it > 0 }?.toString(), c.name, now?.let { "${fmtTime(it.start)} – ${fmtTime(it.end)}" }).joinToString(" · "),
                    style = ty.bodyLarge, color = cs.onSurfaceVariant, maxLines = 1)
            }
            if (now != null) {
                Bar(now.progress(), cs.primary)
                if (now.description.isNotBlank()) Text(now.description, style = ty.bodyLarge, color = cs.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            epg.upcoming().firstOrNull()?.let { n ->
                Text("Next · ${fmtTime(n.start)} ${n.title}", style = ty.bodyLarge, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(shape).background(Tok.Container).padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}
