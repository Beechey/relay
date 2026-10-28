package com.avetero.relay.feature.vod

import com.avetero.relay.ui.components.settle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.feature.categories.categoryMover
import com.avetero.relay.ui.components.Art
import com.avetero.relay.ui.components.Badge
import com.avetero.relay.ui.components.CategoryButton
import com.avetero.relay.ui.components.CategoryDrawer
import com.avetero.relay.ui.components.EmptyState
import com.avetero.relay.ui.components.FilterField
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.Group
import com.avetero.relay.ui.components.GroupColumn
import com.avetero.relay.ui.components.SegmentedTabs
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.components.WithRail
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Semi
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import com.avetero.relay.ui.theme.fg
import com.avetero.relay.ui.theme.sub

private const val RESUME = "__resume"

internal const val ALL = "__all"

internal const val RECENT = "__recent"

internal const val FOR_YOU = "__foryou"

/** A poster in the grid; movies and series share it. */
internal data class Tile(val id: Int, val name: String, val image: String, val rating: Double, val meta: String, val added: Long)

/**
 * Movies and Series (redesign board 04). They open on For you: a hero that follows the focused title, then rows by
 * category. Picking a category opens its full grid with search and sort.
 */
@Composable
fun VodGridScreen(app: AppState, series: Boolean, nav: (Tab) -> Unit, initialGroup: String? = null) {
    var groupId by rememberSaveable(series, initialGroup) { mutableStateOf(initialGroup ?: FOR_YOU) }
    val rows = androidx.compose.foundation.lazy.rememberLazyListState()
    val browsing = groupId == FOR_YOU
    val phone = compact()
    WithRail(if (series) Tab.Series else Tab.Movies, nav, behindDock = browsing && !phone) {
        if (browsing) BrowseRows(app, series, rows) { groupId = it } else VodGrid(app, series, groupId) { groupId = it }
    }
}

/** Boards 07 + 10: one category as a poster grid, with the category list beside it on TV. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.VodGrid(app: AppState, series: Boolean, groupId: String, setGroup: (String) -> Unit) {
    val cats = if (series) app.seriesCatsVisible else app.vodCatsVisible
    val all = remember(series, app.moviesVisible, app.seriesVisible) {
        if (series) app.seriesVisible.map { Tile(it.id, it.name, it.cover, it.rating, it.year, 0) to it.categoryId }
        else app.moviesVisible.map { Tile(it.id, it.name, it.icon, it.rating, "", it.added) to it.categoryId }
    }
    val resumeIds = app.resumes.map { if (series) it.seriesId else it.vodId }.filter { it != 0 }.distinct()
    var drawer by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    val recentIds = if (series) app.recentSeries else app.recentMovies
    val groups = remember(cats, all, resumeIds, recentIds) {
        val counts = all.groupingBy { it.second }.eachCount()
        listOfNotNull(
            Group(FOR_YOU, "For you", 0),
            Group(RESUME, "Continue watching", resumeIds.size).takeIf { resumeIds.isNotEmpty() },
            Group(RECENT, "Recently watched", recentIds.size).takeIf { recentIds.isNotEmpty() },
            Group(ALL, if (series) "All series" else "All movies", all.size),
        ) + cats.map { Group(it.id, it.name, counts[it.id] ?: 0) }.filter { it.count > 0 }
    }
    var query by rememberSaveable(series) { mutableStateOf("") }
    val tiles = remember(groupId, sort, all, resumeIds, recentIds, query) {
        val base = when (groupId) {
            RESUME -> resumeIds.mapNotNull { id -> all.firstOrNull { it.first.id == id }?.first }
            RECENT -> recentIds.mapNotNull { id -> all.firstOrNull { it.first.id == id }?.first }
            ALL -> all.map { it.first }
            else -> all.filter { it.second == groupId }.map { it.first }
        }
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        when (sort) { 1 -> base.sortedBy { it.name.lowercase() }; 2 -> base.sortedByDescending { it.rating }; else -> if (groupId == RESUME || groupId == RECENT) base else base.sortedByDescending { it.added } }
    }
    val gridState = rememberLazyGridState()
    val first = remember { FocusRequester() }
    LaunchedEffect(groupId, sort) { gridState.scrollToItem(0) }
    LaunchedEffect(Unit) { first.settle() }

    val phone = compact()
    val hideVod: (Group) -> Unit = { g ->
        app.setCategoryHidden((if (series) "series:" else "vod:") + g.id, true)
        if (g.id == groupId) setGroup(ALL)
        app.message = "Hidden “${g.name}”"
    }
    val moveVod = categoryMover(app, if (series) "series" else "vod")
    if (!phone) GroupColumn("Categories", groups, groupId, setGroup, onHide = hideVod, onMove = moveVod)
    Column(Modifier.weight(1f).fillMaxHeight().padding(start = if (phone) 0.dp else 32.dp, end = if (phone) 0.dp else 32.dp, top = if (phone) 12.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 20.dp)) {
        if (phone) {
            T(if (series) "Series" else "Movies", 26, Heavy, modifier = Modifier.padding(horizontal = 16.dp))
            val current = groups.firstOrNull { it.id == groupId }
            CategoryButton(current?.name ?: "Categories", current?.count ?: 0) { drawer = true }
            if (drawer) CategoryDrawer(if (series) "Series" else "Movies", groups, groupId, setGroup, onHide = hideVod, onMove = moveVod) { drawer = false }
        }
        val sortRow: @Composable () -> Unit = {
            SegmentedTabs(listOf(0 to "Recently added", 1 to "A – Z", 2 to "Rating"), sort, { sort = it }, small = true)
        }
        // the category button already shows the count
        val what = if (series) "series" else "movies"
        if (phone) Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterField(query, { query = it }, "Search $what in this category")
            sortRow()
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            T(groups.firstOrNull { it.id == groupId }?.name ?: "", 26, Heavy)
            T("   %,d %s".format(tiles.size, what), 15, Semi, C.Muted, Modifier.weight(1f))
            FilterField(query, { query = it }, "Search $what", Modifier.width(240.dp))
            Spacer(Modifier.width(12.dp))
            sortRow()
        }
        if (tiles.isEmpty() && app.vodLoading) EmptyState("Loading $what…", "Live TV is ready to watch while your ${what} load.")
        else if (tiles.isEmpty()) EmptyState(if (query.isNotBlank()) "No matches" else "Nothing here yet",
            if (query.isNotBlank()) "Nothing in this category matches “${query.trim()}”." else "This category has no $what.")
        LazyVerticalGrid(if (phone) GridCells.Adaptive(108.dp) else GridCells.Fixed(6), Modifier, gridState,
            contentPadding = PaddingValues(start = if (phone) 12.dp else 0.dp, end = if (phone) 12.dp else 0.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(tiles, key = { _, t -> t.id }) { i, t ->
                FocusSurface({
                    if (series) app.series.firstOrNull { it.id == t.id }?.let { app.push(Screen.SeriesDetail(it)) }
                    else app.movies.firstOrNull { it.id == t.id }?.let { app.push(Screen.MovieDetail(it)) }
                }, focusRequester = if (i == 0) first else null, onLongClick = { app.toggleFav((if (series) "s" else "m") + t.id) }) { f ->
                    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                            Art(t.image, t.name, t.id, Modifier.fillMaxSize())
                            if (t.rating > 0) Badge("%.1f".format(t.rating), C.Accent, C.OnLight, Modifier.align(Alignment.TopEnd).padding(6.dp))
                        }
                        T(t.name, 13, Bold, fg(f))
                        if (t.meta.isNotEmpty()) T(t.meta, 12, color = sub(f))
                    }
                }
            }
        }
    }
}
