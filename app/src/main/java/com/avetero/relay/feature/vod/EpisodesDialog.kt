package com.avetero.relay.feature.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avetero.relay.data.model.PlayItem
import com.avetero.relay.ui.m3.RelaySheet
import com.avetero.relay.ui.m3.segmentShape
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.theme.isTv
import com.avetero.relay.ui.tv.TvSidePanel
import com.avetero.relay.ui.tv.TvSurface
import kotlinx.coroutines.launch

/** Every episode of the show from inside the player, so you can jump several ahead or back. Phone: a sheet; TV: a side panel. */
@Composable
fun EpisodesDialog(episodes: List<PlayItem>, current: PlayItem, onPick: (PlayItem) -> Unit, onDismiss: () -> Unit) {
    val at = remember(episodes, current) { episodes.indexOf(current).coerceAtLeast(0) }
    // several seasons: one chip each that jumps to the season's first episode
    val seasons = remember(episodes) { episodes.map { it.meta }.distinct().map { m -> m to episodes.indexOfFirst { it.meta == m } } }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (at - 2).coerceAtLeast(0))
    val pick = { i: Int -> if (i == at) onDismiss() else onPick(episodes[i]) }
    if (isTv()) TvEpisodes(episodes, at, seasons, list, pick, onDismiss) else PhoneEpisodes(episodes, at, seasons, list, pick, onDismiss)
}

private fun season(meta: String) = meta.substringAfterLast(" · ")

@Composable
private fun PhoneEpisodes(episodes: List<PlayItem>, at: Int, seasons: List<Pair<String, Int>>, list: LazyListState, pick: (Int) -> Unit, onDismiss: () -> Unit) =
    RelaySheet(onDismiss) {
        val cs = MaterialTheme.colorScheme
        val scope = rememberCoroutineScope()
        Text("Episodes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp))
        if (seasons.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            items(seasons.size, key = { seasons[it].first }) { i ->
                val sel = episodes[at].meta == seasons[i].first
                FilterChip(sel, { scope.launch { list.animateScrollToItem(seasons[i].second) } }, { Text(season(seasons[i].first)) },
                    leadingIcon = if (sel) ({ Icon(Icons.Rounded.Check, null) }) else null)
            }
        }
        LazyColumn(Modifier.weight(1f, fill = false), state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(episodes.size, key = { it }) { i ->
                val e = episodes[i]
                val first = i == 0 || episodes[i - 1].meta != e.meta
                val last = i == episodes.lastIndex || episodes[i + 1].meta != e.meta
                if (first) Text(season(e.meta), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = if (i > 0) 14.dp else 0.dp, bottom = 8.dp))
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(groupShape(first, last))
                        .background(if (i == at) cs.secondaryContainer else cs.surfaceContainer).clickable { pick(i) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        color = if (i == at) cs.onSecondaryContainer else cs.onSurface)
                    if (i == at) Text("Playing", style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.padding(start = 12.dp))
                }
            }
            item { Box(Modifier.navigationBarsPadding()) }
        }
    }

@Composable
private fun TvEpisodes(episodes: List<PlayItem>, at: Int, seasons: List<Pair<String, Int>>, list: LazyListState, pick: (Int) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val cs = androidx.tv.material3.MaterialTheme.colorScheme
    val type = androidx.tv.material3.MaterialTheme.typography
    TvSidePanel(true, onDismiss, focus, "Episodes") {
        if (seasons.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            items(seasons.size, key = { seasons[it].first }) { i ->
                TvSurface({ scope.launch { list.animateScrollToItem(seasons[i].second) } }, Modifier.height(40.dp), selected = episodes[at].meta == seasons[i].first, shape = RoundedCornerShape(20.dp)) {
                    androidx.tv.material3.Text(season(seasons[i].first), style = type.labelLarge, modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp))
                }
            }
        }
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(episodes.size, key = { it }) { i ->
                val e = episodes[i]
                val first = i == 0 || episodes[i - 1].meta != e.meta
                val last = i == episodes.lastIndex || episodes[i + 1].meta != e.meta
                if (first) androidx.tv.material3.Text(season(e.meta), style = type.labelLarge, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = if (i > 0) 13.dp else 0.dp, bottom = 6.dp))
                TvSurface({ pick(i) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), selected = i == at, shape = groupShape(first, last), idle = Tok.ContainerHigh,
                    focusRequester = if (i == at) focus else null) {
                    androidx.compose.foundation.layout.Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.tv.material3.Text(e.title, style = type.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (i == at) androidx.tv.material3.Text("Playing", style = type.labelLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }
}

/** Grouped list corners per season: 20 at the season's ends, 4 between. */
private fun groupShape(first: Boolean, last: Boolean) = segmentShape(if (first) 0 else 1, if (first && last) 1 else if (last) 2 else 3)
