package com.avetero.relay.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.Series
import com.avetero.relay.data.model.Vod
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.FilterField
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.Label
import com.avetero.relay.ui.components.Logo
import com.avetero.relay.ui.components.PosterTile
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.components.WithRail
import com.avetero.relay.ui.components.norm
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Semi
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import com.avetero.relay.ui.theme.fg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Board 12: search box and grouped results. The box uses the device's own keyboard, so phone remote apps and voice work. */
@Composable
fun SearchScreen(app: AppState, nav: (Tab) -> Unit) = WithRail(Tab.Search, nav) {
    var q by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(Triple(emptyList<Channel>(), emptyList<Series>(), emptyList<Vod>())) }
    var cats by remember { mutableStateOf(emptyList<Pair<String, Screen>>()) }
    val box = remember { FocusRequester() }
    // re-runs when the library finishes loading, not only when the text changes
    LaunchedEffect(q, app.channelsVisible, app.moviesVisible, app.seriesVisible) {
        delay(250)
        val n = norm(q.trim())
        cats = if (n.length < 2) emptyList() else withContext(Dispatchers.Default) {
            app.liveCatsVisible.filter { norm(it.name).contains(n) }.map { "Live · ${it.name}" to Screen.Live(it.id) } +
                app.vodCatsVisible.filter { norm(it.name).contains(n) }.map { "Movies · ${it.name}" to Screen.VodGrid(false, it.id) } +
                app.seriesCatsVisible.filter { norm(it.name).contains(n) }.map { "Series · ${it.name}" to Screen.VodGrid(true, it.id) }
        }.take(30)
        results = if (n.length < 2) Triple(emptyList(), emptyList(), emptyList()) else withContext(Dispatchers.Default) {
            Triple(
                app.channelsVisible.filter { norm(it.name).contains(n) }.take(30),
                app.seriesVisible.filter { norm(it.name).contains(n) }.take(30),
                app.moviesVisible.filter { norm(it.name).contains(n) }.take(30),
            )
        }
    }

    val phone = compact()
    if (!phone) Column(Modifier.width(360.dp).fillMaxHeight().background(C.Panel).padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Label("Search everything")
        // opens straight into typing; Done moves down to the results
        FilterField(q, { q = it }, "Channels, movies, series", focusRequester = box)
        T("Type with your TV's keyboard, from the Fire TV or Google TV app on your phone, or by voice with the remote's mic button.", 13, color = C.Muted, maxLines = 4, lineHeight = 19)
        if (q.isNotEmpty()) Btn("Clear", { q = "" }, height = 40.dp)
    }

    LazyColumn(Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (phone) 16.dp else 32.dp), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { Box(Modifier.height(6.dp)) }
        // phones type with their own keyboard
        if (phone) item { FilterField(q, { q = it }, "Search channels, movies and series") }
        val (chans, ser, mov) = results
        if (cats.isNotEmpty()) item {
            ResultRow("Categories", cats.size) {
                items(cats, key = { it.first }) { (label, screen) ->
                    FocusSurface({ app.go(screen) }, Modifier.height(48.dp), idle = C.Surface, shape = RoundedCornerShape(24.dp)) { f ->
                        T(label, 14, Bold, fg(f), Modifier.align(Alignment.Center).padding(horizontal = 18.dp))
                    }
                }
            }
        }
        if (q.length >= 2 && cats.isEmpty() && chans.isEmpty() && ser.isEmpty() && mov.isEmpty()) item { T("No results for “$q”", 16, Bold, C.Muted) }
        if (chans.isNotEmpty()) item {
            ResultRow("Channels", chans.size) {
                items(chans, key = { it.id }) { c ->
                    FocusSurface({ app.push(Screen.LivePlayer(c, chans)) }, Modifier.height(64.dp), idle = C.Surface) { f ->
                        Row(Modifier.fillMaxHeight().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Logo(c.icon, c.name, f, Modifier.size(40.dp, 30.dp)); T(c.name, 14, Bold, fg(f))
                        }
                    }
                }
            }
        }
        if (ser.isNotEmpty()) item {
            ResultRow("Series", ser.size) {
                items(ser, key = { it.id }) { s -> PosterTile(s.cover, s.name, s.id) { app.push(Screen.SeriesDetail(s)) } }
            }
        }
        if (mov.isNotEmpty()) item {
            ResultRow("Movies", mov.size) {
                items(mov, key = { it.id }) { m -> PosterTile(m.icon, m.name, m.id) { app.push(Screen.MovieDetail(m)) } }
            }
        }
    }
}

@Composable
private fun ResultRow(title: String, count: Int, content: LazyListScope.() -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row { T(title, 16, Heavy); T("  $count", 16, Semi, C.Muted) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
