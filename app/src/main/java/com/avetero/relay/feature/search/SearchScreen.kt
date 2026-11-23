package com.avetero.relay.feature.search

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.Series
import com.avetero.relay.data.model.Vod
import com.avetero.relay.data.model.current
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.components.norm
import com.avetero.relay.ui.m3.Artwork
import com.avetero.relay.ui.m3.ChannelLogo
import com.avetero.relay.ui.m3.PosterCard
import com.avetero.relay.ui.m3.segmentShape
import com.avetero.relay.ui.theme.isTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Live search over the whole library, shared by both layouts. The query lives in [AppState.searchQuery]. */
internal class SearchState(val app: AppState, filterState: androidx.compose.runtime.MutableState<String>) {
    var results by mutableStateOf(Triple(emptyList<Channel>(), emptyList<Series>(), emptyList<Vod>()))
    var cats by mutableStateOf(emptyList<Pair<String, Screen>>())
    var searching by mutableStateOf(false)
    var filter by filterState
    private var recentTick by mutableIntStateOf(0)
    val recent: List<String> get() { recentTick; return app.store.recentSearches() }
    val chans get() = results.first
    val series get() = results.second
    val movies get() = results.third
    val n get() = norm(app.searchQuery.trim())
    val total get() = chans.size + series.size + movies.size
    /** Names that start with the query lead: a channel, then a series, then a movie. */
    val top: Any? get() {
        val n = n
        return listOf(chans.firstOrNull { norm(it.name).startsWith(n) }, series.firstOrNull { norm(it.name).startsWith(n) }, movies.firstOrNull { norm(it.name).startsWith(n) })
            .firstOrNull { it != null } ?: chans.firstOrNull() ?: series.firstOrNull() ?: movies.firstOrNull()
    }
    fun shows(kind: String) = filter == "all" || filter == kind
    /** Remember what was searched once something is opened from it. */
    fun open(go: () -> Unit) {
        val q = app.searchQuery.trim()
        if (q.length >= 2) { app.store.addRecentSearch(q); recentTick++ }
        go()
    }
}

@Composable
internal fun rememberSearch(app: AppState): SearchState {
    val filter = rememberSaveable { mutableStateOf("all") }
    val s = remember { SearchState(app, filter) }
    // names prepared for matching once per library load (hundreds of thousands of titles), not on every keystroke
    val names by produceState<Triple<List<String>, List<String>, List<String>>?>(null, app.channels, app.series, app.movies) {
        value = withContext(Dispatchers.Default) { app.allSearchKeys() }
    }
    // re-runs when the library finishes loading, not only when the text changes
    LaunchedEffect(app.searchQuery, names, app.hidden) {
        s.searching = true
        delay(250)
        val n = norm(app.searchQuery.trim())
        s.cats = if (n.length < 2) emptyList() else withContext(Dispatchers.Default) {
            app.liveCatsVisible.filter { norm(it.name).contains(n) }.map { "Live · ${it.name}" to Screen.Live(it.id) } +
                app.vodCatsVisible.filter { norm(it.name).contains(n) }.map { "Movies · ${it.name}" to Screen.VodGrid(false, it.id) } +
                app.seriesCatsVisible.filter { norm(it.name).contains(n) }.map { "Series · ${it.name}" to Screen.VodGrid(true, it.id) }
        }.take(30)
        s.results = if (n.length < 2) Triple(emptyList(), emptyList(), emptyList()) else withContext(Dispatchers.Default) {
            // names that start with the query come first
            val (cn, sn, mn) = names ?: return@withContext Triple(emptyList(), emptyList(), emptyList())
            val hidden = app.hidden
            fun <T> List<T>.ranked(keys: List<String>, type: String, cat: (T) -> String) = indices
                .filter { keys.getOrNull(it)?.contains(n) == true && "$type:${cat(this[it])}" !in hidden }
                .sortedBy { if (keys[it].startsWith(n)) 0 else 1 }.take(30).map { this[it] }
            Triple(app.channels.ranked(cn, "live") { it.categoryId }, app.series.ranked(sn, "series") { it.categoryId }, app.movies.ranked(mn, "vod") { it.categoryId })
        }
        s.searching = names == null && n.length >= 2
        // a filter whose type has no matches any more goes back to All
        if (s.filter != "all" && when (s.filter) { "live" -> s.chans; "movies" -> s.movies; "series" -> s.series; else -> s.cats }.isEmpty()) s.filter = "all"
    }
    return s
}

/** Search (PhoneSearch / TvSearch boards). Phone: Material 3 full-screen search; TV: a search box using the TV's own keyboard, results in rows. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SearchScreen(app: AppState, nav: (Tab) -> Unit) {
    val s = rememberSearch(app)
    if (isTv()) TvSearch(app, s) else PhoneSearch(app, s)
}

@Composable
private fun PhoneSearch(app: AppState, s: SearchState) {
    val bar = rememberSearchBarState()
    val text = rememberTextFieldState(app.searchQuery)
    val scope = rememberCoroutineScope()
    var opened by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { snapshotFlow { text.text.toString() }.collect { app.searchQuery = it } }
    LaunchedEffect(Unit) { bar.animateToExpanded() }
    // closing the search view (Back, the arrow) leaves Search; opening a result closes it without leaving
    LaunchedEffect(Unit) {
        snapshotFlow { bar.currentValue }.collect { v ->
            if (v == SearchBarValue.Expanded) opened = true
            else if (opened && !leaving) app.back()
        }
    }
    val go = { action: () -> Unit -> leaving = true; scope.launch { bar.snapTo(0f); s.open(action) }; Unit }
    val field = @Composable {
        SearchBarDefaults.InputField(text, bar, { /* results are live */ },
            placeholder = { Text("Search channels, movies and series") },
            leadingIcon = {
                if (bar.currentValue == SearchBarValue.Expanded) IconButton({ scope.launch { bar.animateToCollapsed() } }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close search") }
                else Icon(Icons.Rounded.Search, null)
            },
            trailingIcon = { if (text.text.isNotEmpty()) IconButton({ text.setTextAndPlaceCursorAtEnd("") }) { Icon(Icons.Rounded.Close, "Clear") } })
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SearchBar(bar, field, Modifier.fillMaxWidth())
    }
    ExpandedFullScreenSearchBar(bar, field) {
        PhoneResults(app, s, go) { text.setTextAndPlaceCursorAtEnd(it) }
    }
}

@Composable
private fun PhoneResults(app: AppState, s: SearchState, go: (() -> Unit) -> Unit, setQuery: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val q = app.searchQuery.trim()
    val (chans, ser, mov) = s.results
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (s.n.length < 2) {
            val recent = s.recent
            if (recent.isNotEmpty()) item(key = "recent") {
                Column {
                    Text("Recent", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
                    recent.forEach { r ->
                        ListItem({ setQuery(r) }, leadingContent = { Icon(Icons.Rounded.History, null) },
                            colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerHigh)) { Text(r) }
                    }
                }
            }
            return@LazyColumn
        }
        if (s.total + s.cats.size > 0) item(key = "chips") {
            LazyRow(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All", "live" to "Channels ${chans.size}", "movies" to "Movies ${mov.size}", "series" to "Series ${ser.size}", "cats" to "Categories ${s.cats.size}")
                    // a type with no matches loses its chip
                    .filter { (k, label) -> k == "all" || !label.endsWith(" 0") }
                    .forEach { (k, label) -> item(key = k) {
                        val on = s.filter == k
                        FilterChip(on, { s.filter = k }, { Text(label) },
                            leadingIcon = if (on) { { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null)
                    } }
            }
        }
        if (s.filter == "all") s.top?.let { t -> item(key = "top") { PhoneTopResult(app, t, chans, go) } }
        if (s.total == 0 && s.cats.isEmpty()) item(key = "none") {
            if (s.searching || app.libraryLoading || app.vodLoading) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            else Text("No results for “$q”", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        }
        if (s.shows("live") && chans.isNotEmpty()) section("Channels", chans.size) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val shown = if (s.filter == "all") chans.take(5) else chans
                shown.forEachIndexed { i, c ->
                    LaunchedEffect(c.id) { app.loadEpg(c) }
                    Row(Modifier.fillMaxWidth().height(72.dp).clip(segmentShape(i, shown.size)).background(cs.surfaceContainer).clickable { go { app.push(Screen.LivePlayer(c, chans)) } }
                        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ChannelLogo(c.icon, c.name, size = 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            app.programmesFor(c).current()?.let { Text(it.title, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
        if (s.shows("series") && ser.isNotEmpty()) section("Series", ser.size) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ser, key = { it.id }) { x -> PosterCard(x.cover, x.name, x.id, { go { app.push(Screen.SeriesDetail(x)) } }, width = 84.dp, sharedKey = "poster-s${x.id}") }
            }
        }
        if (s.shows("movies") && mov.isNotEmpty()) section("Movies", mov.size) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(mov, key = { it.id }) { m -> PosterCard(m.icon, m.name, m.id, { go { app.push(Screen.MovieDetail(m)) } }, width = 84.dp, sharedKey = "poster-m${m.id}") }
            }
        }
        if (s.shows("cats") && s.cats.isNotEmpty()) section("Categories", s.cats.size) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                s.cats.forEachIndexed { i, (label, screen) ->
                    Box(Modifier.fillMaxWidth().height(56.dp).clip(segmentShape(i, s.cats.size)).background(cs.surfaceContainer).clickable { go { app.go(screen) } }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart) { Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

private fun LazyListScope.section(title: String, count: Int, content: @Composable () -> Unit) = item(key = "s:$title") {
    Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$title · $count", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        content()
    }
}

/** The best match, large: a channel with what's on and Watch; a title with its poster and Details. */
@Composable
private fun PhoneTopResult(app: AppState, top: Any, chans: List<Channel>, go: (() -> Unit) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHighest).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val (label, name, sub) = when (top) {
            is Channel -> {
                LaunchedEffect(top.id) { app.loadEpg(top) }
                ChannelLogo(top.icon, top.name, size = 64.dp)
                Triple(if (top.num > 0) "Top result · channel ${top.num}" else "Top result · channel", top.name, app.programmesFor(top).current()?.let { "Now · ${it.title}" })
            }
            is Series -> { Artwork(top.cover, top.name, top.id, Modifier.size(64.dp, 96.dp).clip(RoundedCornerShape(12.dp)), showTitle = false); Triple("Top result · series", top.name, top.year.ifEmpty { null }) }
            else -> { val v = top as Vod; Artwork(v.icon, v.name, v.id, Modifier.size(64.dp, 96.dp).clip(RoundedCornerShape(12.dp)), showTitle = false); Triple("Top result · movie", v.name, null) }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.primary)
            Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button({
            go {
                when (top) {
                    is Channel -> app.push(Screen.LivePlayer(top, chans))
                    is Series -> app.push(Screen.SeriesDetail(top))
                    else -> app.push(Screen.MovieDetail(top as Vod))
                }
            }
        }, Modifier.height(48.dp)) { Text(if (top is Channel) "Watch" else "Details") }
    }
}
