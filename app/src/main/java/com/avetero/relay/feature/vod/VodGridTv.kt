package com.avetero.relay.feature.vod

import com.avetero.relay.ui.tv.TvPivot
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.avetero.relay.app.AppState
import com.avetero.relay.feature.search.TvSearchField
import com.avetero.relay.ui.components.Group
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvDialog
import com.avetero.relay.ui.tv.TvHints
import com.avetero.relay.ui.tv.TvSafe
import com.avetero.relay.ui.tv.TvSidePanel
import com.avetero.relay.ui.tv.TvSurface
import kotlinx.coroutines.flow.drop

/** TV Movies / Series (TvMovies board): the category column, then For you rows or the category's poster grid. */
@Composable
internal fun TvLibrary(app: AppState, series: Boolean, initialGroup: String?) {
    val lib = rememberLibrary(app, series)
    val cats = if (series) app.seriesCatsVisible else app.vodCatsVisible
    var groupId by rememberSaveable(series, initialGroup) { mutableStateOf(initialGroup ?: FOR_YOU) }
    val groups = remember(lib, cats, app.favourites) { lib.groups(cats, app.favourites, tv = true) }
    val current = groups.firstOrNull { it.id == groupId }?.id ?: ALL
    var menuFor by remember { mutableStateOf<Group?>(null) }
    var shownMenu by remember { mutableStateOf<Group?>(null) }
    if (menuFor != null) shownMenu = menuFor
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val rows = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            TvCategoryColumn(if (series) "Series" else "Movies", groups, current, { groupId = it }) { menuFor = it }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (current == FOR_YOU) BrowseRows(app, series, lib, rows)
                else TvGrid(app, lib, current)
            }
        }
        TvHints("◀" to "Categories, then menu", "Hold OK" to "Save", modifier = Modifier.align(Alignment.BottomEnd).padding(end = TvSafe.Horizontal, bottom = TvSafe.Vertical))
        val first = remember { FocusRequester() }
        TvSidePanel(menuFor != null, { menuFor = null }, first, title = shownMenu?.name) {
            shownMenu?.let { g ->
                categoryActions(app, series, g) { if (g.id == groupId) groupId = ALL }.forEachIndexed { i, (label, act) ->
                    TvSurface({ menuFor = null; if (label == HIDE) confirm = g.name to act else act() }, Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(26.dp), focusRequester = if (i == 0) first else null) {
                        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }
    confirm?.let { (name, hide) ->
        TvDialog({ confirm = null }) {
            val ok = remember { FocusRequester() }
            LaunchedEffect(Unit) { ok.settle() }
            Text("Hide “$name”?", style = MaterialTheme.typography.headlineSmall)
            Text("It won't appear in lists, All, the guide or search. Bring it back in Settings → Manage categories.", style = MaterialTheme.typography.bodyLarge, color = Tok.OnSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                TvButton("Cancel", { confirm = null })
                TvButton("Hide", { confirm = null; hide() }, primary = true, focusRequester = ok)
            }
        }
    }
}

/** The 260dp column of categories with their counts; the current one is tinted. Hold OK on a category for its menu. */
@Composable
private fun TvCategoryColumn(title: String, groups: List<Group>, selected: String, onSelect: (String) -> Unit, onMenu: (Group) -> Unit) {
    val state = rememberLazyListState()
    Column(Modifier.width(260.dp).fillMaxHeight().padding(start = 8.dp, end = 16.dp, top = 32.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 16.dp, bottom = 16.dp))
        TvPivot {
            LazyColumn(Modifier.weight(1f).focusRestorer(), state, contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(groups, key = { it.id }) { g ->
                    val on = g.id == selected
                    TvSurface({ onSelect(g.id) }, Modifier.fillMaxWidth().height(48.dp), selected = on, shape = RoundedCornerShape(24.dp),
                        onLongClick = if (g.id.startsWith("__")) null else ({ onMenu(g) })) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(g.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (g.count > 0) Text("%,d".format(g.count), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

/** One category's posters: search within it, three sort buttons, a 6-wide grid. Focus comes back to the poster you opened. */
@Composable
private fun TvGrid(app: AppState, lib: Library, groupId: String) {
    val series = lib.series
    var sort by rememberSaveable(series) { mutableIntStateOf(0) }
    var query by rememberSaveable(series) { mutableStateOf("") }
    val tiles = remember(lib, groupId, sort, query, app.favourites) { lib.tiles(groupId, sort, query, app.favourites) }
    val grid = rememberLazyGridState()
    var lastOpened by rememberSaveable(series) { mutableIntStateOf(-1) }
    val back = remember { FocusRequester() }
    val firstPoster = remember { FocusRequester() }
    LaunchedEffect(Unit) { snapshotFlow { Triple(groupId, sort, query) }.drop(1).collect { grid.scrollToItem(0); lastOpened = -1 } }
    LaunchedEffect(Unit) { (if (tiles.any { it.id == lastOpened }) back else firstPoster).settle() }
    val what = if (series) "series" else "movies"

    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = TvSafe.Horizontal, top = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvSearchField(query, { query = it }, "Search $what in this category", Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("Recently added", "A – Z", "Rating").forEachIndexed { i, label ->
                    val shape = when (i) { 0 -> RoundedCornerShape(24.dp, 6.dp, 6.dp, 24.dp); 2 -> RoundedCornerShape(6.dp, 24.dp, 24.dp, 6.dp); else -> RoundedCornerShape(6.dp) }
                    TvSurface({ sort = i }, Modifier.height(48.dp), selected = sort == i, shape = shape, idle = Tok.ContainerHigh) {
                        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp))
                    }
                }
            }
        }
        when {
            tiles.isEmpty() && app.vodLoading -> TvEmpty("Loading $what…", "Live TV is ready to watch while your $what load.")
            tiles.isEmpty() && query.isNotBlank() -> TvEmpty("No matches", "Nothing in this category matches “${query.trim()}”.")
            tiles.isEmpty() -> TvEmpty("Nothing here yet", "This category has no $what.")
        }
        TvPivot {
            LazyVerticalGrid(GridCells.Fixed(6), Modifier.fillMaxSize(), grid, contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(tiles, key = { _, t -> t.id }) { i, t ->
                    val meta = listOfNotNull(t.meta.ifEmpty { null }, if (t.rating > 0) "★ %.1f".format(t.rating) else null).joinToString(" · ")
                    TvPoster(t.image, t.name, t.id, posterKey(series, t.id), { lastOpened = t.id; openTitle(app, series, t.id) }, meta = meta,
                        progress = lib.progress[t.id] ?: 0f, onLongClick = { app.toggleFav((if (series) "s" else "m") + t.id) },
                        focusRequester = when { t.id == lastOpened -> back; i == 0 -> firstPoster; else -> null })
                }
            }
        }
    }
}

@Composable
internal fun TvEmpty(title: String, body: String) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(body, style = MaterialTheme.typography.bodyLarge, color = Tok.OnSurfaceVariant)
}
