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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.current
import com.avetero.relay.data.model.minutesLeft
import com.avetero.relay.data.model.progress
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.Logo
import com.avetero.relay.ui.components.ProgressLine
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.Ico
import com.avetero.relay.ui.theme.Semi
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.fg
import com.avetero.relay.ui.theme.sub
import kotlinx.coroutines.delay

/**
 * Redesign board 02: the glass channel list that slides over live TV. Category pills along the top, then the
 * channels of the chosen category with what's on. It opens on the playing channel.
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
    LaunchedEffect(groupId) {
        if (at >= 0) state.scrollToItem((at - 2).coerceAtLeast(0))
        delay(60)
        runCatching { (if (at >= 0) playingRow else firstRow).requestFocus() }
    }
    Column(modifier.width(540.dp).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(C.Glass).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("Channels", 22, Heavy, modifier = Modifier.weight(1f))
            T("Hold OK · options", 12, Semi, C.Muted)
        }
        val gi = groups.indexOfFirst { it.id == groupId }.coerceAtLeast(0)
        val pills = rememberLazyListState(initialFirstVisibleItemIndex = (gi - 1).coerceAtLeast(0))
        LazyRow(state = pills, horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            itemsIndexed(groups, key = { _, g -> g.id }) { _, g ->
                val on = g.id == groupId
                FocusSurface({ onGroup(g.id); onPoke() }, Modifier.height(34.dp), selected = on, idle = C.Glass2, shape = RoundedCornerShape(17.dp), onFocus = onPoke, lift = 1.06f) { f ->
                    T(if (g.id == AppState.FAVS) "★ ${g.name}" else g.name + if (on) " · ${"%,d".format(g.count)}" else "", 13, Bold,
                        if (f) C.OnLight else if (on) C.Text else C.Soft, Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
                }
            }
        }
        if (list.isEmpty()) T(if (groupId == AppState.FAVS) "No favourites yet. Hold OK on a channel to add it." else "No channels here.", 14, color = C.Muted, maxLines = 2)
        LazyColumn(Modifier.weight(1f), state, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                LaunchedEffect(c.id) { app.loadEpg(c) }
                val now = app.programmesFor(c).current()
                val isPlaying = c.id == playing?.id
                FocusSurface({ onPick(c, list); onPoke() }, Modifier.fillMaxWidth().height(70.dp), selected = isPlaying, shape = RoundedCornerShape(18.dp),
                    focusRequester = when { isPlaying -> playingRow; i == 0 -> firstRow; else -> null }, onFocus = onPoke) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        T(if (c.num > 0) "${c.num}" else "", 13, Bold, if (isPlaying && !f) C.Accent else sub(f), mono = true, modifier = Modifier.width(40.dp))
                        Logo(c.icon, c.name, f, Modifier.size(56.dp, 40.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            T(c.name, 15, Heavy, fg(f))
                            T(when { isPlaying && now == null -> "Playing"; now != null -> "${now.title} · ${now.minutesLeft()}"; else -> "Live" }, 12, Semi, sub(f))
                            if (now != null) ProgressLine(now.progress(), Modifier.fillMaxWidth(), if (f) C.LightTrack else C.Glass3, if (f) C.OnLight else null, height = 3.dp)
                        }
                        if (app.isFav("c${c.id}")) Ico(Icons.StarFilled, if (f) C.OnLight else C.Accent, 14.dp)
                    }
                }
            }
        }
    }
}
