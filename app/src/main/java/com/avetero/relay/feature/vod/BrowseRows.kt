package com.avetero.relay.feature.vod

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.PlayItem
import com.avetero.relay.ui.components.Art
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.components.CategoryDrawer
import com.avetero.relay.ui.components.EmptyState
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.Group
import com.avetero.relay.ui.components.Pill
import com.avetero.relay.ui.components.PillBtn
import com.avetero.relay.ui.components.SegmentedTabs
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.Semi
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class Shelf(val title: String, val tiles: List<Tile>)

/**
 * For you on Movies and Series: the focused title fills the hero (artwork and story fetched once it settles),
 * pills jump to a category, and rows run Continue watching, Recently watched, Recently added, then your categories
 * in your own order.
 */
@Composable
internal fun RowScope.BrowseRows(app: AppState, series: Boolean, state: LazyListState, onCategory: (String) -> Unit) {
    val phone = compact()
    val what = if (series) "series" else "movies"
    val cats = if (series) app.seriesCatsVisible else app.vodCatsVisible
    val all = remember(series, app.moviesVisible, app.seriesVisible) {
        if (series) app.seriesVisible.map { Tile(it.id, it.name, it.cover, it.rating, it.year, 0) to it.categoryId }
        else app.moviesVisible.map { Tile(it.id, it.name, it.icon, it.rating, "", it.added) to it.categoryId }
    }
    val byId = remember(all) { all.associate { it.first.id to it.first } }
    val shelves = remember(all, cats, app.resumes, app.recentMovies, app.recentSeries) {
        val byCat = all.groupBy({ it.second }, { it.first })
        val resume = app.resumes.map { if (series) it.seriesId else it.vodId }.filter { it != 0 }.distinct().mapNotNull { byId[it] }
        val recent = (if (series) app.recentSeries else app.recentMovies).mapNotNull { byId[it] }
        val added = if (series) emptyList() else all.asSequence().map { it.first }.sortedByDescending { it.added }.take(30).toList()
        listOf(Shelf("Continue watching", resume), Shelf("Recently watched", recent), Shelf("Recently added", added)).filter { it.tiles.isNotEmpty() } +
            cats.mapNotNull { c -> byCat[c.id]?.takeIf { it.isNotEmpty() }?.let { Shelf(c.name, it.sortedByDescending { t -> t.added }.take(30)) } }
    }
    var hero by remember(series) { mutableStateOf<Tile?>(null) }
    val heroTile = hero ?: shelves.firstOrNull()?.tiles?.firstOrNull()
    val info = remember(series) { mutableStateMapOf<Int, Pair<String, String>>() }
    var drawer by remember { mutableStateOf(false) }
    val firstTile = remember { FocusRequester() }
    var pageFocused by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused, shelves.isNotEmpty()) {
        if (windowFocused && shelves.isNotEmpty()) repeat(15) { if (pageFocused) return@LaunchedEffect; delay(100); runCatching { firstTile.requestFocus() } }
    }
    // fetch artwork and story only once focus rests on a title, so scrolling doesn't fire a request per poster
    LaunchedEffect(heroTile?.id) {
        val t = heroTile ?: return@LaunchedEffect
        if (t.id in info) return@LaunchedEffect
        delay(450)
        info[t.id] = withContext(Dispatchers.IO) {
            runCatching { if (series) app.api.seriesInfo(t.id).let { it.cover to it.plot } else app.api.vodInfo(t.id).let { it.backdrop to it.plot } }.getOrDefault("" to "")
        }
    }
    val side = if (phone) 16.dp else 64.dp
    val open = { t: Tile ->
        if (series) app.series.firstOrNull { it.id == t.id }?.let { app.push(Screen.SeriesDetail(it)) }
        else app.movies.firstOrNull { it.id == t.id }?.let { app.push(Screen.MovieDetail(it)) }
    }
    val pills = remember(cats) { cats.take(5) }
    val groups = remember(cats, all) {
        val counts = all.groupingBy { it.second }.eachCount()
        listOf(Group(ALL, if (series) "All series" else "All movies", all.size)) + cats.map { Group(it.id, it.name, counts[it.id] ?: 0) }.filter { it.count > 0 }
    }

    Column(Modifier.weight(1f).fillMaxSize()) {
        if (!phone) Box(Modifier.fillMaxWidth().height(360.dp)) {
            Crossfade(heroTile?.id, label = "hero") { id ->
                val t = id?.let { byId[it] }
                if (t != null) Art(info[t.id]?.first?.ifEmpty { null } ?: t.image, "", t.id, Modifier.fillMaxSize(), showTitle = false)
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to C.Ground.copy(alpha = .95f), .45f to C.Ground.copy(alpha = .55f), .75f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.4f to Color.Transparent, 1f to C.Ground)))
            if (heroTile != null) Column(Modifier.align(Alignment.BottomStart).padding(start = side, bottom = 10.dp).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (heroTile.rating > 0) Pill("★ %.1f".format(heroTile.rating), bg = C.Accent, color = C.OnLight)
                    if (heroTile.meta.isNotEmpty()) Pill(heroTile.meta)
                }
                T(heroTile.name, 46, Heavy, maxLines = 2, lineHeight = 48)
                info[heroTile.id]?.second?.takeIf { it.isNotBlank() }?.let { T(it, 15, color = C.Soft, maxLines = 2, lineHeight = 22) }
                T(if (series) "OK opens episodes · hold OK for favourites" else "OK opens details · hold OK for favourites", 12, Semi, C.Dim)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().onFocusChanged { pageFocused = it.hasFocus }, state, contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(if (phone) 16.dp else 20.dp)) {
            if (phone && "vod" in app.parts && "series" in app.parts) item(key = "switch") {
                Column(Modifier.padding(start = side, end = side, top = 14.dp)) {
                    SegmentedTabs(listOf(false to "Movies", true to "Series"), series, { if (it != series) app.go(Screen.VodGrid(it)) })
                }
            }
            item(key = "pills") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = side, vertical = 4.dp)) {
                    item { PillBtn("For you", true, {}) }
                    items(pills, key = { it.id }) { c -> PillBtn(c.name, false, { onCategory(c.id) }) }
                    item { PillBtn("All ${"%,d".format(cats.size)} categories", false, { drawer = true }) }
                }
            }
            if (shelves.isEmpty()) item(key = "empty") {
                EmptyState(if (app.vodLoading) "Loading $what…" else "No $what yet", if (app.vodLoading) "Live TV is ready to watch while your $what load." else "Your provider hasn't listed any $what.")
            }
            shelves.forEachIndexed { si, shelf ->
                item(key = "shelf:${shelf.title}:$si") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        T(shelf.title, 19, Heavy, modifier = Modifier.padding(start = side))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(start = side, end = 40.dp, top = 8.dp, bottom = 8.dp)) {
                            items(shelf.tiles, key = { it.id }) { t ->
                                val w = if (phone) 108.dp else 134.dp
                                FocusSurface({ open(t) }, Modifier.size(w, w * 1.5f), shape = RoundedCornerShape(16.dp), lift = 1.08f,
                                    focusRequester = if (si == 0 && t == shelf.tiles.first()) firstTile else null, onFocus = { hero = t },
                                    onLongClick = { app.toggleFav((if (series) "s" else "m") + t.id) }) { _ ->
                                    Art(t.image, t.name, t.id, Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)), 13)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (drawer) CategoryDrawer(if (series) "Series" else "Movies", groups, "", { onCategory(it) }) { drawer = false }
}
