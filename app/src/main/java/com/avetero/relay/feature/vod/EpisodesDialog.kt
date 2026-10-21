package com.avetero.relay.feature.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.avetero.relay.data.model.PlayItem
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.components.DialogTitle
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.Label
import com.avetero.relay.ui.components.OptionRow
import com.avetero.relay.ui.components.Panel
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.fg
import kotlinx.coroutines.launch

/** Every episode of the show from inside the player, so you can jump several ahead or back. */
@Composable
fun EpisodesDialog(episodes: List<PlayItem>, current: PlayItem, onPick: (PlayItem) -> Unit, onDismiss: () -> Unit) = Dialog(onDismiss) {
    val at = episodes.indexOf(current).coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (at - 2).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Panel(520, Modifier.heightIn(max = 560.dp), padding = 20.dp, spacing = 6.dp) {
        DialogTitle("Episodes")
        // several seasons: one button each that jumps to the season's first episode
        val seasons = episodes.map { it.meta }.distinct()
        if (seasons.size > 1) {
            val scope = rememberCoroutineScope()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                items(seasons.size) { i ->
                    val first = episodes.indexOfFirst { it.meta == seasons[i] }
                    FocusSurface({ scope.launch { list.scrollToItem(first) } }, Modifier.height(40.dp), selected = episodes[at].meta == seasons[i], idle = C.Control, shape = RoundedCornerShape(20.dp)) { f ->
                        T(seasons[i].substringAfterLast(" · "), 14, Bold, fg(f), Modifier.align(Alignment.Center).padding(horizontal = 16.dp))
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f, fill = false), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(episodes.size) { i ->
                val e = episodes[i]
                val season = e.meta.substringAfterLast(" · ")
                if (i == 0 || episodes[i - 1].meta != e.meta) Label(season, Modifier.padding(start = 8.dp, top = if (i > 0) 10.dp else 0.dp))
                OptionRow(e.title, i == at, { if (i == at) onDismiss() else onPick(e) }, trailing = if (i == at) "Playing" else null, focusRequester = if (i == at) focus else null)
            }
        }
        Btn("Close", onDismiss, Modifier.fillMaxWidth().padding(top = 8.dp), style = BtnStyle.Light, height = 44.dp)
    }
}
