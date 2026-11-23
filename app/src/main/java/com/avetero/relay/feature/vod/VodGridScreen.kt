package com.avetero.relay.feature.vod

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Move
import com.avetero.relay.feature.categories.categoryMover
import com.avetero.relay.ui.components.Group
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.components.norm
import com.avetero.relay.ui.m3.LargeTitle
import com.avetero.relay.ui.m3.PosterCard
import com.avetero.relay.ui.m3.RelaySheet
import com.avetero.relay.ui.theme.LocalWide
import com.avetero.relay.ui.theme.isTv
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withTimeoutOrNull

internal const val RESUME = "__resume"
internal const val ALL = "__all"
internal const val RECENT = "__recent"
internal const val FOR_YOU = "__foryou"
internal const val SAVED = "__saved"

internal const val HIDE = "Hide category"

internal val SORTS = listOf("Newest", "A – Z", "Rating")

/** A poster in the grid; movies and series share it. */
internal data class Tile(val id: Int, val name: String, val image: String, val rating: Double, val meta: String, val added: Long)

/** Movies or series prepared once per library load: tiles with their category, lookups, and what you're watching. */
internal class Library(
    val series: Boolean, val all: List<Pair<Tile, String>>, val byId: Map<Int, Tile>, val counts: Map<String, Int>,
    val resumeIds: List<Int>, val recentIds: List<Int>, val progress: Map<Int, Float>,
) {
    private val favPrefix = if (series) "s" else "m"

    fun savedIds(favourites: Collection<String>) = favourites.mapNotNull { k -> if (k.startsWith(favPrefix)) k.drop(1).toIntOrNull()?.takeIf(byId::containsKey) else null }

    /** One group's titles, filtered by [query] and sorted ([SORTS]); Continue / Recent / Saved keep their own order on Newest. */
    fun tiles(groupId: String, sort: Int, query: String, favourites: Collection<String>): List<Tile> {
        val base = when (groupId) {
            RESUME -> resumeIds.mapNotNull(byId::get)
            RECENT -> recentIds.mapNotNull(byId::get)
            SAVED -> savedIds(favourites).mapNotNull(byId::get)
            ALL, FOR_YOU -> all.map { it.first }
            else -> all.filter { it.second == groupId }.map { it.first }
        }.let { l -> if (query.isBlank()) l else norm(query.trim()).let { n -> l.filter { norm(it.name).contains(n) } } }
        val ordered = groupId == RESUME || groupId == RECENT || groupId == SAVED
        return when (sort) { 1 -> base.sortedBy { it.name.lowercase() }; 2 -> base.sortedByDescending { it.rating }; else -> if (ordered) base else base.sortedByDescending { it.added } }
    }

    /** Built-in groups (only those with something in them) then the visible categories that have titles. */
    fun groups(cats: List<com.avetero.relay.data.model.Category>, favourites: Collection<String>, tv: Boolean): List<Group> {
        val saved = savedIds(favourites).size
        return listOfNotNull(
            Group(FOR_YOU, "For you", 0).takeIf { tv },
            Group(RESUME, if (tv) "Continue watching" else "Continue", resumeIds.size).takeIf { resumeIds.isNotEmpty() },
            Group(RECENT, if (tv) "Recently watched" else "Recent", recentIds.size).takeIf { recentIds.isNotEmpty() },
            Group(SAVED, "Saved", saved).takeIf { saved > 0 },
            Group(ALL, if (tv) (if (series) "All series" else "All movies") else "All", all.size),
        ) + cats.mapNotNull { c -> counts[c.id]?.takeIf { it > 0 }?.let { Group(c.id, c.name, it) } }
    }
}

@Composable
internal fun rememberLibrary(app: AppState, series: Boolean): Library {
    val all = remember(series, app.moviesVisible, app.seriesVisible) {
        if (series) app.seriesVisible.map { Tile(it.id, it.name, it.cover, it.rating, it.year, 0) to it.categoryId }
        else app.moviesVisible.map { Tile(it.id, it.name, it.icon, it.rating, "", it.added) to it.categoryId }
    }
    val byId = remember(all) { all.associate { it.first.id to it.first } }
    val counts = remember(all) { all.groupingBy { it.second }.eachCount() }
    val resumes = app.resumes
    val resumeIds = remember(series, resumes) { resumes.map { if (series) it.seriesId else it.vodId }.filter { it != 0 }.distinct() }
    // newest resume per title wins (the list is newest first)
    val progress = remember(series, resumes) { resumes.asReversed().filter { (if (series) it.seriesId else it.vodId) != 0 }.associate { (if (series) it.seriesId else it.vodId) to it.fraction() } }
    val recentIds = if (series) app.recentSeries else app.recentMovies
    return remember(all, byId, counts, resumeIds, recentIds, progress) { Library(series, all, byId, counts, resumeIds, recentIds, progress) }
}

/** Opens a title's detail page. */
internal fun openTitle(app: AppState, series: Boolean, id: Int) {
    if (series) app.seriesById[id]?.let { app.push(Screen.SeriesDetail(it)) } else app.movieById[id]?.let { app.push(Screen.MovieDetail(it)) }
}

/** What you can do to a category from Library (hold it): move it, open the bulk reorder, or hide it (with Undo). */
internal fun categoryActions(app: AppState, series: Boolean, g: Group, onHidden: () -> Unit): List<Pair<String, () -> Unit>> {
    val move = categoryMover(app, if (series) "series" else "vod")
    val key = (if (series) "series:" else "vod:") + g.id
    return listOf(
        "Move to top" to { move(g, Move.Top) }, "Move up one" to { move(g, Move.Up) },
        "Move down one" to { move(g, Move.Down) }, "Move to bottom" to { move(g, Move.Bottom) },
        "Reorder several…" to { move(g, null) },
        HIDE to { app.setCategoryHidden(key, true); onHidden(); app.say("Hidden “${g.name}”") { app.setCategoryHidden(key, false) } },
    )
}

/**
 * Library: movies or series as a poster grid by category. Phone: one Library tab with Movies | Series, category
 * chips and a sort split button. TV: a category column (For you first) beside the grid.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun VodGridScreen(app: AppState, series: Boolean, nav: (Tab) -> Unit, initialGroup: String? = null) =
    if (isTv()) TvLibrary(app, series, initialGroup) else PhoneLibrary(app, series, initialGroup)

@Composable
private fun PhoneLibrary(app: AppState, series: Boolean, initialGroup: String?) {
    val lib = rememberLibrary(app, series)
    val cats = if (series) app.seriesCatsVisible else app.vodCatsVisible
    var groupId by rememberSaveable(series, initialGroup) { mutableStateOf(initialGroup ?: ALL) }
    var sort by rememberSaveable(series) { mutableIntStateOf(0) }
    val groups = remember(lib, cats, app.favourites) { lib.groups(cats, app.favourites, tv = false) }
    // a group that emptied (last saved title removed, category hidden) falls back to All
    val current = groups.firstOrNull { it.id == groupId }?.id ?: ALL
    val tiles = remember(lib, current, sort, app.favourites) { lib.tiles(current, sort, "", app.favourites) }
    val grid = rememberLazyGridState()
    val chips = rememberLazyListState()
    // scroll to the top on a new group or sort and when Library is tapped again; not when coming back from a title
    LaunchedEffect(Unit) { snapshotFlow { current to sort }.drop(1).collect { grid.scrollToItem(0) } }
    LaunchedEffect(Unit) { snapshotFlow { app.reselect }.drop(1).collect { grid.animateScrollToItem(0) } }
    LaunchedEffect(current) { groups.indexOfFirst { it.id == current }.takeIf { it > 0 }?.let { chips.animateScrollToItem(it) } }
    var menuFor by remember { mutableStateOf<Group?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val what = if (series) "series" else "movies"

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            LargeTitle("Library") { IconButton({ app.go(Screen.Search) }) { Icon(Icons.Rounded.Search, "Search the library") } }
            if ("vod" in app.parts && "series" in app.parts) Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                ToggleButton(!series, { if (series) app.go(Screen.VodGrid(false)) }, Modifier.weight(1f).heightIn(min = 48.dp), shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()) { Text("Movies") }
                ToggleButton(series, { if (!series) app.go(Screen.VodGrid(true)) }, Modifier.weight(1f).heightIn(min = 48.dp), shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()) { Text("Series") }
            }
            LazyRow(state = chips, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups, key = { it.id }) { g ->
                    val on = g.id == current
                    FilterChip(on, { groupId = g.id }, { Text(g.name) },
                        Modifier.animateItem().then(if (g.id.startsWith("__")) Modifier else Modifier.onLongPress { menuFor = g }),
                        leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null)
                }
            }
            when {
                tiles.isEmpty() && app.vodLoading -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                    LoadingIndicator()
                    Text("Loading $what…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                    Text("Live TV is ready to watch while your $what load.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                tiles.isEmpty() -> Text(if (lib.all.isEmpty()) "Your provider hasn't listed any $what." else "Nothing here yet.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                else -> LazyVerticalGrid(if (LocalWide.current) GridCells.Adaptive(120.dp) else GridCells.Fixed(3), Modifier.fillMaxSize(), grid,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tiles, key = { it.id }) { t ->
                        PosterCard(t.image, t.name, t.id, { openTitle(app, series, t.id) }, Modifier.animateItem(), sharedKey = posterKey(series, t.id),
                            onLongClick = { app.toggleFav((if (series) "s" else "m") + t.id) })
                    }
                }
            }
        }
        if (tiles.isNotEmpty()) SortButton(sort, { sort = it }, Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }

    menuFor?.let { g ->
        RelaySheet({ menuFor = null }) {
            Text(g.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
            categoryActions(app, series, g) { if (g.id == groupId) groupId = ALL }.forEach { (label, act) ->
                ListItem({ menuFor = null; if (label == HIDE) confirm = g.name to act else act() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Text(label) }
            }
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
    confirm?.let { (name, hide) ->
        AlertDialog({ confirm = null }, { TextButton({ confirm = null; hide() }) { Text("Hide") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } },
            title = { Text("Hide “$name”?") },
            text = { Text("It won't appear in lists, All, the guide or search. Bring it back in Settings → Manage categories.") })
    }
}

/** Sort as a split button: the label cycles to the next order, the arrow lists them all. */
@Composable
private fun SortButton(sort: Int, onSort: (Int) -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.TonalLeadingButton({ onSort((sort + 1) % SORTS.size) }, Modifier.heightIn(min = 56.dp),
                shapes = SplitButtonDefaults.leadingButtonShapesFor(56.dp), contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(56.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Sort, null)
                Spacer(Modifier.width(8.dp))
                Text(SORTS[sort])
            }
        },
        trailingButton = {
            Box {
                SplitButtonDefaults.TonalTrailingButton(menu, { menu = it }, Modifier.heightIn(min = 56.dp),
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(56.dp), contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(56.dp)) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Change sort")
                }
                DropdownMenu(menu, { menu = false }) {
                    SORTS.forEachIndexed { i, label ->
                        DropdownMenuItem({ Text(label) }, { onSort(i); menu = false },
                            leadingIcon = { if (i == sort) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) })
                    }
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * Press and hold on a component that already handles taps (a chip): watches the gesture before the component does,
 * and swallows the release after a long press so the tap doesn't fire too.
 */
internal fun Modifier.onLongPress(action: () -> Unit) = pointerInput(action) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation(PointerEventPass.Initial) }
        if (up == null) {
            action()
            do {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                e.changes.forEach { it.consume() }
            } while (e.changes.any { it.pressed })
        }
    }
}
