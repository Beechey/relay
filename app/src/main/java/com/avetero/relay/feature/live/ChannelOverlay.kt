package com.avetero.relay.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.avetero.relay.app.AppState
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.current
import com.avetero.relay.data.model.minutesLeft
import com.avetero.relay.data.model.progress
import com.avetero.relay.ui.components.stopAtEnd
import com.avetero.relay.ui.m3.ChannelLogo
import com.avetero.relay.ui.m3.segmentShape
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvHints
import com.avetero.relay.ui.tv.TvSurface
import kotlinx.coroutines.delay

/**
 * TvChannelOverlay: the channel list over live TV. Group chips along the top (◀▶ on a channel changes group), then
 * the group's channels with what's on. It opens on the playing channel.
 */
@Composable
fun ChannelOverlay(
    app: AppState, groupId: String, onGroup: (String) -> Unit, playing: Channel?, onPick: (Channel, List<Channel>) -> Unit,
    onPoke: () -> Unit, modifier: Modifier = Modifier,
) {
    val groups = rememberLiveGroups(app)
    val list = remember(groupId, app.channels, app.favourites, app.hidden, app.recentChannels) { app.channelsIn(groupId) }
    val state = rememberLazyListState()
    val playingRow = remember { FocusRequester() }
    val firstRow = remember { FocusRequester() }
    val at = list.indexOfFirst { it.id == playing?.id }
    val gi = groups.indexOfFirst { it.id == groupId }.coerceAtLeast(0)
    LaunchedEffect(groupId) {
        state.scrollToItem(if (at >= 0) (at - 2).coerceAtLeast(0) else 0)
        delay(60)
        runCatching { (if (at >= 0) playingRow else firstRow).requestFocus() }
    }
    val cs = MaterialTheme.colorScheme
    val ty = MaterialTheme.typography
    Box(Modifier.fillMaxSize()) {
        Column(modifier.padding(start = 48.dp, top = 27.dp, bottom = 27.dp).width(540.dp).fillMaxHeight().clip(RoundedCornerShape(28.dp))
            .background(Tok.Container.copy(alpha = 0.94f)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Channels", style = ty.headlineSmall, modifier = Modifier.weight(1f))
                TvHints("Hold OK" to "Options")
            }
            val pills = rememberLazyListState(initialFirstVisibleItemIndex = (gi - 1).coerceAtLeast(0))
            LazyRow(Modifier.stopAtEnd(), pills, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                itemsIndexed(groups, key = { _, g -> g.id }) { _, g ->
                    val on = g.id == groupId
                    TvSurface({ onGroup(g.id); onPoke() }, Modifier.height(40.dp).onFocusChanged { if (it.isFocused) onPoke() }, selected = on,
                        shape = RoundedCornerShape(8.dp), idle = Tok.ContainerHigh) {
                        Text(groupLabel(g) + if (on) " · ${"%,d".format(g.count)}" else "", style = ty.labelLarge,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp))
                    }
                }
            }
            if (list.isEmpty()) Text(if (groupId == AppState.FAVS) "No favourites yet. Hold OK on a channel to add it." else "No channels here.",
                style = ty.bodyLarge, color = cs.onSurfaceVariant)
            LazyColumn(
                Modifier.weight(1f).onKeyEvent { e ->
                    // ◀▶ on a channel moves to the neighbouring group
                    if (e.type != KeyEventType.KeyDown || groups.isEmpty()) return@onKeyEvent false
                    val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> return@onKeyEvent false }
                    onGroup(groups[Math.floorMod(gi + d, groups.size)].id); onPoke(); true
                },
                state, verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
            ) {
                itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                    LaunchedEffect(c.id) { app.loadEpg(c) }
                    val now = app.programmesFor(c).current()
                    val isPlaying = c.id == playing?.id
                    TvSurface({ onPick(c, list); onPoke() }, Modifier.fillMaxWidth().height(72.dp).onFocusChanged { if (it.isFocused) onPoke() },
                        selected = isPlaying, shape = segmentShape(i, list.size), idle = Tok.ContainerHigh,
                        focusRequester = when { isPlaying -> playingRow; i == 0 -> firstRow; else -> null }) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (c.num > 0) "${c.num}" else "", style = ty.labelLarge, modifier = Modifier.width(36.dp),
                                color = if (isPlaying && LocalContentColor.current != cs.inverseOnSurface) cs.primary else LocalContentColor.current.copy(alpha = 0.8f))
                            ChannelLogo(c.icon, c.name, Modifier.width(64.dp), size = 40.dp, playing = isPlaying)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(c.name, style = ty.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (now != null) "${now.title} · ${now.minutesLeft()}" else "Live", style = ty.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = LocalContentColor.current.copy(alpha = 0.8f))
                                if (now != null) Bar(now.progress(), LocalContentColor.current)
                            }
                            if (app.isFav("c${c.id}")) Icon(Icons.Rounded.Star, "Favourite", Modifier.size(20.dp), tint = cs.primary)
                        }
                    }
                }
            }
        }
        TvHints("▲▼" to "Channel", "◀▶" to "Group", "OK" to "Watch", "Back" to "Close",
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 27.dp).clip(RoundedCornerShape(12.dp))
                .background(Tok.Surface.copy(alpha = 0.7f)).padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/** Chip and row labels: Favourites gets its star. */
internal fun groupLabel(g: com.avetero.relay.ui.components.Group) = if (g.id == AppState.FAVS) "★ ${g.name}" else g.name

/** A 4dp on-air bar: track at 25% of [color], filled with [color]. */
@Composable
internal fun Bar(fraction: Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
    }
