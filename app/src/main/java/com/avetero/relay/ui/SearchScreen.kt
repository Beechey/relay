package com.avetero.relay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.avetero.relay.AppState
import com.avetero.relay.Channel
import com.avetero.relay.Screen
import com.avetero.relay.Series
import com.avetero.relay.Vod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Normalizer


/** Board 12: on-screen keyboard and grouped results. */
@Composable
fun SearchScreen(app: AppState, nav: (Tab) -> Unit) = WithRail(Tab.Search, nav) {
    var q by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(Triple(emptyList<Channel>(), emptyList<Series>(), emptyList<Vod>())) }
    var cats by remember { mutableStateOf(emptyList<Pair<String, Screen>>()) }
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }
    LaunchedEffect(q) {
        delay(250)
        val n = norm(q.trim())
        cats = if (n.length < 2) emptyList() else withContext(Dispatchers.Default) {
            app.liveCats.filter { norm(it.name).contains(n) }.map { "Live · ${it.name}" to Screen.Live(it.id) } +
                app.vodCats.filter { norm(it.name).contains(n) }.map { "Movies · ${it.name}" to Screen.VodGrid(false, it.id) } +
                app.seriesCats.filter { norm(it.name).contains(n) }.map { "Series · ${it.name}" to Screen.VodGrid(true, it.id) }
        }.take(30)
        results = if (n.length < 2) Triple(emptyList(), emptyList(), emptyList()) else withContext(Dispatchers.Default) {
            Triple(
                app.channels.filter { norm(it.name).contains(n) }.take(30),
                app.series.filter { norm(it.name).contains(n) }.take(30),
                app.movies.filter { norm(it.name).contains(n) }.take(30),
            )
        }
    }

    val phone = compact()
    if (!phone) Column(Modifier.width(360.dp).fillMaxHeight().background(C.Panel).padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Label("Search everything")
        Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(C.Control).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
            T(q.ifEmpty { "Type with the keys below" }, 22, Bold, if (q.isEmpty()) C.Dim else Color.White)
        }
        LazyVerticalGrid(GridCells.Fixed(6), Modifier.height(212.dp), userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items("ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890".toList()) { ch ->
                FocusSurface({ q += ch.lowercaseChar() }, Modifier.height(44.dp), idle = C.Control, shape = RoundedCornerShape(9.dp),
                    focusRequester = if (ch == 'A') firstKey else null) { f -> T(ch.toString(), 16, Bold, fg(f), Modifier.align(Alignment.Center)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Space" to { q += " " }, "Delete" to { q = q.dropLast(1) }, "Clear" to { q = "" }).forEach { (label, act) ->
                FocusSurface(act, Modifier.weight(1f).height(44.dp), idle = C.Control, shape = RoundedCornerShape(9.dp)) { f -> T(label, 14, Bold, fg(f), Modifier.align(Alignment.Center)) }
            }
        }
    }

    LazyColumn(Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (phone) 16.dp else 32.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { Box(Modifier.height(6.dp)) }
        // phones type with their own keyboard
        if (phone) item { Field("Search channels, movies and series", q, { q = it }) }
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
private fun ResultRow(title: String, count: Int, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row { T(title, 16, Heavy); T("  $count", 16, Semi, C.Muted) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }

@Composable
fun PosterTile(image: String, title: String, seed: Int, width: androidx.compose.ui.unit.Dp = 120.dp, onClick: () -> Unit) =
    FocusSurface(onClick, Modifier.size(width, width * 1.5f), shape = RoundedCornerShape(10.dp)) { f ->
        Art(image, title, seed, Modifier.fillMaxSize().padding(if (f) 5.dp else 0.dp), 12)
    }
