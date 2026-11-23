package com.avetero.relay.feature.vod

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.avetero.relay.app.AppState
import com.avetero.relay.ui.components.stopAtEnd
import com.avetero.relay.ui.m3.Artwork
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvRowTitle
import com.avetero.relay.ui.tv.TvSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class Shelf(val title: String, val tiles: List<Tile>)

/**
 * TV For you on Movies and Series: the focused title fills the hero (artwork and story fetched once focus rests),
 * then rows: Continue watching, Recently watched, Recently added, then your categories in your own order.
 * Coming back from a title returns to its poster.
 */
@Composable
internal fun BrowseRows(app: AppState, series: Boolean, lib: Library, state: LazyListState) {
    val what = if (series) "series" else "movies"
    val cats = if (series) app.seriesCatsVisible else app.vodCatsVisible
    val shelves = remember(lib, cats) {
        val byCat = lib.all.groupBy({ it.second }, { it.first })
        val resume = lib.resumeIds.mapNotNull(lib.byId::get)
        val recent = lib.recentIds.mapNotNull(lib.byId::get)
        val added = if (series) emptyList() else lib.all.asSequence().map { it.first }.sortedByDescending { it.added }.take(30).toList()
        listOf(Shelf("Continue watching", resume), Shelf("Recently watched", recent), Shelf("Recently added", added)).filter { it.tiles.isNotEmpty() } +
            cats.mapNotNull { c -> byCat[c.id]?.takeIf { it.isNotEmpty() }?.let { Shelf(c.name, it.sortedByDescending { t -> t.added }.take(30)) } }
    }
    var hero by remember(series) { mutableStateOf<Tile?>(null) }
    val heroTile = hero ?: shelves.firstOrNull()?.tiles?.firstOrNull()
    val info = remember(series) { mutableStateMapOf<Int, Pair<String, String>>() }
    val firstTile = remember { FocusRequester() }
    val restore = remember(series) { app.browseFocus[series]?.takeIf { (si, ti) -> si < shelves.size && ti < shelves[si].tiles.size } }
    val restoreTile = remember { FocusRequester() }
    var pageFocused by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused, shelves.isNotEmpty()) {
        if (!windowFocused || shelves.isEmpty()) return@LaunchedEffect
        if (restore != null) runCatching { state.scrollToItem(restore.first) }
        repeat(15) { if (pageFocused) return@LaunchedEffect; delay(100); runCatching { (if (restore != null) restoreTile else firstTile).requestFocus() } }
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
    val side = 16.dp

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            Crossfade(heroTile?.id, label = "hero") { id ->
                val t = id?.let { lib.byId[it] }
                if (t != null) Artwork(info[t.id]?.first?.ifEmpty { null } ?: t.image, "", t.id, Modifier.fillMaxSize(), showTitle = false)
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Tok.Surface.copy(alpha = .95f), .45f to Tok.Surface.copy(alpha = .55f), .75f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.4f to Color.Transparent, 1f to Tok.Surface)))
            if (heroTile != null) Column(Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(start = side, bottom = 8.dp).widthIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (heroTile.rating > 0) TvFact("★ %.1f".format(heroTile.rating))
                    if (heroTile.meta.isNotEmpty()) TvFact(heroTile.meta)
                }
                Text(heroTile.name, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 2)
                info[heroTile.id]?.second?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Tok.OnSurfaceVariant, maxLines = 2) }
            }
        }
        if (shelves.isEmpty()) Box(Modifier.padding(start = side, top = 16.dp)) {
            TvEmpty(if (app.vodLoading) "Loading $what…" else "No $what yet", if (app.vodLoading) "Live TV is ready to watch while your $what load." else "Your provider hasn't listed any $what.")
        }
        TvPivot {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().onFocusChanged { pageFocused = it.hasFocus }, state,
                contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                shelves.forEachIndexed { si, shelf ->
                    item(key = "shelf:${shelf.title}:$si") {
                        Column {
                            TvRowTitle(shelf.title, Modifier.padding(start = side))
                            val rowState = rememberLazyListState(if (restore?.first == si) (restore.second - 1).coerceAtLeast(0) else 0)
                            LazyRow(Modifier.stopAtEnd(), rowState, contentPadding = PaddingValues(start = side, end = TvSafe.Horizontal, top = 8.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                itemsIndexed(shelf.tiles, key = { _, t -> t.id }) { ti, t ->
                                    TvPoster(t.image, t.name, t.id, posterKey(series, t.id), { app.browseFocus[series] = si to ti; openTitle(app, series, t.id) }, width = 134.dp,
                                        progress = lib.progress[t.id] ?: 0f,
                                        onLongClick = { app.toggleFav((if (series) "s" else "m") + t.id) },
                                        focusRequester = when { restore == si to ti -> restoreTile; si == 0 && ti == 0 -> firstTile; else -> null },
                                        onFocus = { hero = t; app.browseFocus[series] = si to ti }, caption = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
