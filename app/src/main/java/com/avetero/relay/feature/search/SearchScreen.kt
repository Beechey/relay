package com.avetero.relay.feature.search

import androidx.compose.runtime.produceState
import androidx.compose.ui.focus.focusProperties
import com.avetero.relay.ui.components.stopAtEnd
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
import androidx.compose.ui.graphics.Color
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.components.ProgressLine
import com.avetero.relay.ui.components.PillBtn
import com.avetero.relay.ui.components.LivePill
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.components.Art
import com.avetero.relay.data.model.minutesLeft
import com.avetero.relay.data.model.progress
import com.avetero.relay.data.model.current
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.Series
import com.avetero.relay.data.model.Vod
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.FilterField
import com.avetero.relay.ui.components.FocusSurface
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

/** Board 07: the box uses the device's own keyboard (so phone remote apps and voice work); a top result leads, filters narrow the rest. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(app: AppState, nav: (Tab) -> Unit) = WithRail(Tab.Search, nav) {
    var q by app::searchQuery
    // Right from the search box goes into the results, not up to the bar
    val resultsFocus = remember { FocusRequester() }
    var results by remember { mutableStateOf(Triple(emptyList<Channel>(), emptyList<Series>(), emptyList<Vod>())) }
    var cats by remember { mutableStateOf(emptyList<Pair<String, Screen>>()) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var recentTick by remember { mutableIntStateOf(0) }
    val recent = remember(recentTick) { app.store.recentSearches() }
    val box = remember { FocusRequester() }
    // names prepared for matching once per library load (hundreds of thousands of titles), not on every keystroke
    val names by produceState<Triple<List<String>, List<String>, List<String>>?>(null, app.channelsVisible, app.seriesVisible, app.moviesVisible) {
        value = withContext(Dispatchers.Default) { Triple(app.searchKeys(app.channelsVisible) { it.name }, app.searchKeys(app.seriesVisible) { it.name }, app.searchKeys(app.moviesVisible) { it.name }) }
    }
    var searching by remember { mutableStateOf(false) }
    // re-runs when the library finishes loading, not only when the text changes
    LaunchedEffect(q, names) {
        searching = true
        delay(250)
        val n = norm(q.trim())
        cats = if (n.length < 2) emptyList() else withContext(Dispatchers.Default) {
            app.liveCatsVisible.filter { norm(it.name).contains(n) }.map { "Live · ${it.name}" to Screen.Live(it.id) } +
                app.vodCatsVisible.filter { norm(it.name).contains(n) }.map { "Movies · ${it.name}" to Screen.VodGrid(false, it.id) } +
                app.seriesCatsVisible.filter { norm(it.name).contains(n) }.map { "Series · ${it.name}" to Screen.VodGrid(true, it.id) }
        }.take(30)
        results = if (n.length < 2) Triple(emptyList(), emptyList(), emptyList()) else withContext(Dispatchers.Default) {
            // names that start with the query come first
            val (cn, sn, mn) = names ?: return@withContext Triple(emptyList(), emptyList(), emptyList())
            fun <T> List<T>.ranked(keys: List<String>) = indices.filter { keys.getOrNull(it)?.contains(n) == true }
                .sortedBy { if (keys[it].startsWith(n)) 0 else 1 }.take(30).map { this[it] }
            Triple(app.channelsVisible.ranked(cn), app.seriesVisible.ranked(sn), app.moviesVisible.ranked(mn))
        }
        searching = names == null && n.length >= 2
    }
    // remember what was searched once something is opened from it
    val open = { go: () -> Unit -> if (q.trim().length >= 2) { app.store.addRecentSearch(q.trim()); recentTick++ }; go() }
    val (chans, ser, mov) = results
    val n = norm(q.trim())
    val top: Any? = listOf(chans.firstOrNull { norm(it.name).startsWith(n) }, ser.firstOrNull { norm(it.name).startsWith(n) }, mov.firstOrNull { norm(it.name).startsWith(n) }).firstOrNull { it != null }
        ?: chans.firstOrNull() ?: ser.firstOrNull() ?: mov.firstOrNull()
    val showLive = filter == "all" || filter == "live"
    val showMovies = filter == "all" || filter == "movies"
    val showSeries = filter == "all" || filter == "series"

    val phone = compact()
    if (!phone) Column(Modifier.padding(start = 40.dp, bottom = 32.dp).width(360.dp).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(C.Glass).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // opens straight into typing; Done moves down to the results
        val hasResults = results.first.size + results.second.size + results.third.size > 0
        FilterField(q, { q = it }, "Channels, movies, series", Modifier.focusProperties { if (hasResults) right = resultsFocus }, focusRequester = box)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .05f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T("Type the way you like", 15, Heavy)
            T("Press OK for your TV's own keyboard, type from the Fire TV or Google TV app on your phone, or hold the remote's mic button and say it.", 13, Semi, C.Muted, maxLines = 5, lineHeight = 20)
        }
        if (recent.isNotEmpty()) {
            T("Recent", 13, Semi, C.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recent.forEach { r -> PillBtn(r, r.equals(q.trim(), true), { q = r }) }
            }
        }
        Spacer(Modifier.weight(1f))
        if (q.isNotEmpty()) Btn("Clear", { q = "" }, height = 40.dp)
    }

    LazyColumn(Modifier.weight(1f).fillMaxHeight().padding(start = if (phone) 16.dp else 24.dp, end = if (phone) 16.dp else 40.dp), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // phones type with their own keyboard
        if (phone) item { FilterField(q, { q = it }, "Search channels, movies and series", modifier = Modifier.padding(top = 6.dp)) }
        if (phone && q.isEmpty() && recent.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T("Recent", 13, Semi, C.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { recent.forEach { r -> PillBtn(r, false, { q = r }) } }
            }
        }
        if (n.length >= 2 && (chans.size + ser.size + mov.size) > 0) item {
            LazyRow(modifier = Modifier.stopAtEnd(), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                listOf("all" to "All · ${chans.size + ser.size + mov.size}", "live" to "Live · ${chans.size}", "movies" to "Movies · ${mov.size}", "series" to "Series · ${ser.size}")
                    .filter { (k, label) -> k == "all" || !label.endsWith(" 0") }
                    .forEach { (k, label) -> item(key = k) { PillBtn(label, filter == k, { filter = k }, focusRequester = if (k == "all") resultsFocus else null) } }
            }
        }
        if (n.length >= 2 && filter == "all" && top != null) item(key = "top") { TopResult(app, top, chans) { open(it) } }
        if (n.length >= 2 && cats.isEmpty() && chans.isEmpty() && ser.isEmpty() && mov.isEmpty()) item { T(if (app.libraryLoading || app.vodLoading) "Still loading your library…" else if (searching) "Searching…" else "No results for “$q”", 16, Bold, C.Muted) }
        if (showLive && chans.isNotEmpty()) item {
            ResultRow("Channels", chans.size) {
                items(chans, key = { it.id }) { c ->
                    FocusSurface({ open { app.push(Screen.LivePlayer(c, chans)) } }, Modifier.height(64.dp), idle = C.Glass2, shape = RoundedCornerShape(18.dp)) { f ->
                        Row(Modifier.fillMaxHeight().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Logo(c.icon, c.name, f, Modifier.size(48.dp, 34.dp)); T(c.name, 14, Bold, fg(f))
                        }
                    }
                }
            }
        }
        if (showSeries && ser.isNotEmpty()) item {
            ResultRow("Series", ser.size) {
                items(ser, key = { it.id }) { s -> PosterTile(s.cover, s.name, s.id, 108.dp) { open { app.push(Screen.SeriesDetail(s)) } } }
            }
        }
        if (showMovies && mov.isNotEmpty()) item {
            ResultRow("Movies", mov.size) {
                items(mov, key = { it.id }) { m -> PosterTile(m.icon, m.name, m.id, 108.dp) { open { app.push(Screen.MovieDetail(m)) } } }
            }
        }
        if (filter == "all" && cats.isNotEmpty()) item {
            ResultRow("Categories", cats.size) {
                items(cats, key = { it.first }) { (label, screen) -> PillBtn(label, false, { open { app.go(screen) } }) }
            }
        }
    }
}

/** The best match, large: a channel shows what's on now with Watch; a title shows its artwork with Open. */
@Composable
private fun TopResult(app: AppState, top: Any, chans: List<Channel>, open: (() -> Unit) -> Unit) {
    val phone = compact()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Glass).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        val artW = if (phone) 120.dp else 300.dp
        when (top) {
            is Channel -> {
                LaunchedEffect(top.id) { app.loadEpg(top) }
                val now = app.programmesFor(top).current()
                Box(Modifier.size(artW, artW * 9 / 16).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .06f))) {
                    Logo(top.icon, top.name, false, Modifier.align(Alignment.Center).size(artW * .42f, artW * .3f))
                    LivePill(Modifier.align(Alignment.TopStart).padding(10.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(if (top.num > 0) "Top result · channel ${top.num}" else "Top result · channel", 13, Semi, C.Muted)
                    T(top.name, if (phone) 20 else 28, Heavy, maxLines = 2)
                    if (now != null) {
                        T("${now.title} · ${now.minutesLeft()}", 13, Semi, C.Soft)
                        ProgressLine(now.progress(), Modifier.widthIn(max = 360.dp).fillMaxWidth(), C.Glass3)
                    }
                    Btn("Watch", { open { app.push(Screen.LivePlayer(top, chans)) } }, style = BtnStyle.Primary, height = 42.dp, icon = Icons.Play)
                }
            }
            is Series, is Vod -> {
                val (id, name, image) = if (top is Series) Triple(top.id, top.name, top.cover) else (top as Vod).let { Triple(it.id, it.name, it.icon) }
                Art(image, name, id, Modifier.size(artW * .45f, artW * .45f * 1.5f).clip(RoundedCornerShape(14.dp)), showTitle = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(if (top is Series) "Top result · series" else "Top result · movie", 13, Semi, C.Muted)
                    T(name, if (phone) 20 else 28, Heavy, maxLines = 2)
                    Btn(if (top is Series) "Episodes" else "Details", { open { app.push(if (top is Series) Screen.SeriesDetail(top) else Screen.MovieDetail(top as Vod)) } },
                        style = BtnStyle.Primary, height = 42.dp)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(title: String, count: Int, content: LazyListScope.() -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row { T(title, 19, Heavy); T("  $count", 15, Semi, C.Dim) }
        LazyRow(modifier = Modifier.stopAtEnd(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 6.dp), content = content)
    }
