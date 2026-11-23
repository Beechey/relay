package com.avetero.relay.feature.live

import com.avetero.relay.ui.motion.rememberEntrance
import com.avetero.relay.ui.motion.entrance
import com.avetero.relay.ui.m3.SkeletonRows
import com.avetero.relay.ui.motion.sharedBounds
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.avetero.relay.app.AppState
import com.avetero.relay.app.AppState.Companion.ALL
import com.avetero.relay.app.AppState.Companion.FAVS
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.current
import com.avetero.relay.data.model.upcoming
import com.avetero.relay.feature.categories.categoryMover
import com.avetero.relay.feature.playback.CastingOverlay
import com.avetero.relay.feature.playback.PlayerError
import com.avetero.relay.feature.playback.TracksDialog
import com.avetero.relay.player.PlayerSurface
import com.avetero.relay.feature.guide.CategorySheet
import com.avetero.relay.ui.components.Group
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.m3.ChannelLogo
import com.avetero.relay.ui.m3.LargeTitle
import com.avetero.relay.ui.m3.PagerTabs
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.drop
import com.avetero.relay.ui.m3.segmentShape
import com.avetero.relay.util.fmtTime
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext

/**
 * Phone Live (PhoneLive): the playing channel on top, its programme, the Saved/Cast/Audio/Guide group, category tabs
 * over a pager of the groups' channels (swipe between categories). Tap a channel to play it here, tap it again (or the picture) for fullscreen; ⋮ or a long
 * press opens its options. While casting, the controls for the TV take the buttons' place (PhoneCasting).
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveScreen(app: AppState, nav: (Tab) -> Unit, initialGroup: String? = null) {
    var groupId by rememberSaveable(initialGroup) {
        val last = app.store.lastLiveGroup.takeIf { app.store.liveStart == "Last category" && it.isNotEmpty() }
        mutableStateOf(initialGroup ?: last ?: ALL)
    }
    LaunchedEffect(groupId) { app.store.lastLiveGroup = groupId }
    ResumeLastChannel(app)
    var drawer by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // only the Recent group depends on the recents; rebuilding the list on every play would jump the list
    val recentKey = if (groupId == AppState.RECENT) app.recentChannels else null
    val list = remember(groupId, app.channels, app.favourites, app.hidden, recentKey, query) { channelsMatching(app, groupId, query) }
    var menuFor by remember { mutableStateOf<Channel?>(null) }
    var tracks by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf<Channel?>(null) }
    val playing = rememberPlaying(app, list)
    LaunchedEffect(playing) { if (playing != null) app.liveChannel = playing }
    val groups = rememberLiveGroups(app)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // categories are tabs over a pager of channel lists: swipe the list or tap a tab; the sheet's pick moves the pager
    val pager = rememberPagerState(groups.indexOfFirst { it.id == groupId }.takeIf { it >= 0 } ?: groups.indexOfFirst { it.id == ALL }) { groups.size }
    val latestGroups by rememberUpdatedState(groups)
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { i -> latestGroups.getOrNull(i)?.let { groupId = it.id } } }
    val page = groups.indexOfFirst { it.id == groupId }
    LaunchedEffect(page) { if (page >= 0 && page != pager.settledPage) pager.animateScrollToPage(page) }

    fun pickGroup(id: String) { groupId = id }
    fun play(c: Channel) {
        if (playing != null && playing.id != c.id) previous = playing
        app.player.play(app.liveUrl(c), title = c.name); app.addRecentChannel(c.id); app.liveChannel = c
    }
    val castPicker = if (app.player.canCast) rememberCastPicker() else null
    val cs = MaterialTheme.colorScheme

    // the picture and its controls stay put; only the channels scroll
    Column(Modifier.fillMaxSize().background(cs.surface).statusBarsPadding()) {
        if (playing == null) LargeTitle("Live", actions = {
            AllCategories { drawer = true }
            SearchToggle(searching) { searching = !searching; if (!searching) query = "" }
        })
        else Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).sharedBounds("live-video").background(Color.Black).clickable(onClickLabel = "Watch ${playing.name} fullscreen") {
                app.push(Screen.LivePlayer(playing, list))
            }) {
                // keyed on PiP: the floating window borrows the video surface, so rebuild this one afterwards
                key(app.inPip) { PlayerSurface(app.player.player, Modifier.fillMaxSize()) }
                app.player.castDevice?.let { CastingOverlay(it, nowLine(app, playing), app.player.castStatus) }
                if (app.player.castDevice == null) LiveBadge(Modifier.align(Alignment.TopStart).padding(12.dp))
                app.player.error?.let { err ->
                    PlayerError(err, onRetry = { app.player.retry() }, hls = app.hls, onSwitch = {
                        app.hls = !app.hls; app.store.hls = app.hls; app.player.play(app.liveUrl(playing), title = playing.name)
                    }, onBack = { app.player.stop(); app.liveChannel = null })
                }
            }
            val epg = app.programmesFor(playing)
            val now = epg.current()
            Row(Modifier.padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(now?.title ?: playing.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(playing.num.takeIf { it > 0 }?.toString(), playing.name, now?.let { "until ${fmtTime(it.end)}" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AllCategories { drawer = true }
                SearchToggle(searching) { searching = !searching; if (!searching) query = "" }
            }
            if (app.player.castDevice != null) CastControls(app, castPicker)
            else PlayerActions(app, playing, castPicker, onTracks = { tracks = true })
        }
        if (searching)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp), singleLine = true,
                placeholder = { Text("Search channels in this category") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Clear") } }, shape = RoundedCornerShape(28.dp))
        PagerTabs(pager, remember(groups) { groups.map(::chipLabel) }, scrollable = true)
        // only the page on screen is composed (hundreds of categories); each keeps its own scroll by its key
        HorizontalPager(pager, Modifier.weight(1f), key = { groups[it].id }, verticalAlignment = Alignment.Top) { i ->
            val id = groups[i].id
            // only the Recent group depends on the recents; rebuilding the list on every play would jump the list
            val recent = if (id == AppState.RECENT) app.recentChannels else null
            val shown = remember(id, app.channels, app.favourites, app.hidden, recent, query) { channelsMatching(app, id, query) }
            val listState = rememberLazyListState()
            val enter = rememberEntrance(ready = shown.isNotEmpty())
            LaunchedEffect(listState) { snapshotFlow { app.reselect }.drop(1).collect { listState.animateScrollToItem(0) } }
            LazyColumn(Modifier.fillMaxSize(), listState, contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
                when {
                    shown.isEmpty() && app.libraryLoading -> item(key = "loading") {
                        SkeletonRows(8, 72.dp, Modifier.padding(horizontal = 16.dp))
                    }
                    shown.isEmpty() -> item(key = "empty") {
                        Column(Modifier.fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(when { query.isNotBlank() -> "No matches"; id == FAVS -> "Nothing saved yet"; else -> "No channels here" }, style = MaterialTheme.typography.titleMedium)
                            Text(if (query.isNotBlank()) "No channel here matches “${query.trim()}”." else "Press and hold any channel, or tap ⋮, to save it.",
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                    }
                }
                itemsIndexed(shown, key = { _, c -> c.id }, contentType = { _, _ -> "channel" }) { n, c ->
                    LaunchedEffect(c.id) { app.loadEpg(c) }
                    val on = playing?.id == c.id
                    ChannelRow(app, c, on, segmentShape(n, shown.size), Modifier.animateItem().entrance(enter, n).padding(horizontal = 16.dp).padding(bottom = 2.dp),
                        onClick = {
                            keyboard?.hide(); focusManager.clearFocus()
                            if (on) app.push(Screen.LivePlayer(c, shown)) else play(c)
                        },
                        onMenu = { menuFor = c })
                }
            }
        }
    }

    if (drawer) CategorySheet(groups, groupId, ::pickGroup, categoryMover(app, "live"), { hideLive(app, it, groupId) { g -> pickGroup(g) } }) { drawer = false }
    menuFor?.let { c ->
        ChannelMenu(app, c, { menuFor = null }, onPrevious = previous?.takeIf { playing?.id == c.id }?.let { p -> { menuFor = null; play(p) } },
            onHide = { hideLive(app, Group(c.categoryId, app.liveCats.firstOrNull { it.id == c.categoryId }?.name ?: "", 0), groupId) { g -> pickGroup(g) } })
    }
    if (tracks) TracksDialog(app) { tracks = false }
}

/** Settings → Live TV → Resume the last channel: start it when Live opens with nothing playing. */
@Composable
internal fun ResumeLastChannel(app: AppState) = LaunchedEffect(Unit) {
    if (app.store.resumeLastChannel && app.player.url == null)
        app.recentChannels.firstOrNull()?.let { id -> app.channels.firstOrNull { it.id == id } }?.let { app.player.play(app.liveUrl(it), title = it.name); app.liveChannel = it }
}

/** The live channel whose stream is playing, if any (looked up only when the stream changes). */
@Composable
internal fun rememberPlaying(app: AppState, list: List<Channel>): Channel? = remember(app.player.url, list) {
    val url = app.player.url ?: return@remember null
    list.firstOrNull { app.liveUrl(it) == url } ?: app.channels.firstOrNull { app.liveUrl(it) == url }
}

private fun nowLine(app: AppState, c: Channel) = listOfNotNull(c.name, app.programmesFor(c).current()?.title).joinToString(" · ")

@Composable
private fun SearchToggle(on: Boolean, onClick: () -> Unit) = IconButton(onClick) {
    Icon(if (on) Icons.Rounded.Close else Icons.Rounded.Search, if (on) "Close channel search" else "Search channels")
}

@Composable
internal fun LiveBadge(modifier: Modifier = Modifier, text: String = "LIVE", style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium) =
    Text(text, modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.error).padding(horizontal = 8.dp, vertical = 4.dp),
        style = style, color = MaterialTheme.colorScheme.onError)

/** Saved · Cast · Audio · Guide as one connected button group. */
@Composable
private fun PlayerActions(app: AppState, c: Channel, castPicker: (() -> Unit)?, onTracks: () -> Unit) {
    val fav = app.isFav("c${c.id}")
    data class B(val label: String, val icon: ImageVector, val checked: Boolean, val onClick: () -> Unit)
    val buttons = listOfNotNull(
        B("Saved", if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline, fav) { app.toggleFav("c${c.id}") },
        castPicker?.let { B("Cast", Icons.Rounded.Cast, false, it) },
        B("Audio", Icons.Rounded.Subtitles, false, onTracks),
        B("Guide", Icons.AutoMirrored.Rounded.EventNote, false) { app.go(Screen.Guide) },
    )
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        buttons.forEachIndexed { i, b ->
            ToggleButton(b.checked, { b.onClick() }, Modifier.weight(1f).height(48.dp), shapes = connectedShapes(i, buttons.size),
                colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)) {
                if (i == 0) { Icon(b.icon, null, Modifier.size(18.dp)); androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp)) }
                Text(b.label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun connectedShapes(i: Int, n: Int): ToggleButtonShapes = when (i) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    n - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}

/** PhoneCasting: what the phone is doing for the TV, and its controls. */
@Composable
private fun CastControls(app: AppState, castPicker: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("Your phone is relaying this channel", style = MaterialTheme.typography.titleMedium)
                Text("Keep Relay open on the same Wi-Fi. The screen can turn off.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                val playing = app.player.playing
                val tonal = ToggleButtonDefaults.colors(containerColor = cs.surfaceContainerHighest, contentColor = cs.onSurface)
                ToggleButton(true, { if (playing) app.player.current.pause() else app.player.current.play() }, Modifier.width(64.dp).height(48.dp), shapes = connectedShapes(0, 3)) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play")
                }
                ToggleButton(false, { castPicker?.invoke() }, Modifier.weight(1f).height(48.dp), shapes = connectedShapes(1, 3), colors = tonal) { Text("Volume") }
                ToggleButton(false, { CastContext.getSharedInstance()?.sessionManager?.endCurrentSession(true) }, Modifier.weight(1f).height(48.dp), shapes = connectedShapes(2, 3),
                    colors = ToggleButtonDefaults.colors(containerColor = cs.surfaceContainerHighest, contentColor = cs.error)) { Text("Stop casting") }
            }
        }
        Text("Pick another channel below and it plays on the TV.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

/**
 * The Cast picker without the stock button: an invisible MediaRouteButton stays attached (it only opens its dialog
 * while attached) and our own button clicks it. Connected, the same dialog shows volume and Stop casting.
 */
@Composable
internal fun rememberCastPicker(): () -> Unit {
    val ref = remember { arrayOfNulls<MediaRouteButton>(1) }
    AndroidView({ ctx -> MediaRouteButton(ctx).also { CastButtonFactory.setUpMediaRouteButton(ctx, it); ref[0] = it } }, Modifier.size(1.dp).alpha(0f))
    return remember { { ref[0]?.performClick() } }
}

/** "All categories": the categories sheet (find, move, hide); picking one there moves the tabs. */
@Composable
private fun AllCategories(onClick: () -> Unit) = IconButton(onClick) { Icon(Icons.AutoMirrored.Rounded.List, "All categories") }

/** A group's channels, narrowed by the channel search. */
private fun channelsMatching(app: AppState, groupId: String, query: String) =
    app.channelsIn(groupId).let { l -> if (query.isBlank()) l else l.filter { it.name.contains(query.trim(), ignoreCase = true) } }

/** Phone wording: favourites are "Saved", All channels is "All". */
private fun chipLabel(g: Group) = when (g.id) { FAVS -> "Saved"; ALL -> "All"; else -> g.name }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(app: AppState, c: Channel, on: Boolean, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier, onClick: () -> Unit, onMenu: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val epg = app.programmesFor(c)
    val now = epg.current()
    val next = if (now != null) epg.upcoming().firstOrNull() else null
    Surface(modifier.fillMaxWidth().height(72.dp), shape = shape, color = if (on) cs.secondaryContainer else cs.surfaceContainer) {
        Row(Modifier.fillMaxSize().combinedClickable(onClickLabel = if (on) "Watch fullscreen" else "Play ${c.name}", onLongClick = onMenu, onClick = onClick)
            .padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChannelLogo(c.icon, c.name, playing = on)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(listOfNotNull(c.num.takeIf { it > 0 }?.toString(), c.name).joinToString(" · "), style = MaterialTheme.typography.titleMedium,
                    color = if (on) cs.onSecondaryContainer else cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(now?.title ?: "No programme info", next?.let { "next ${fmtTime(it.start)}" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (app.isFav("c${c.id}")) Icon(Icons.Rounded.Star, "Saved", Modifier.size(16.dp), tint = cs.primary)
            IconButton(onMenu) { Icon(Icons.Rounded.MoreVert, "Options for ${c.name}", tint = cs.onSurfaceVariant) }
        }
    }
}
