package com.avetero.relay.feature.favourites

import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.AppState.Companion.FAVS
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.Series
import com.avetero.relay.data.model.Vod
import com.avetero.relay.data.model.current
import com.avetero.relay.data.model.progress
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.m3.ChannelLogo
import com.avetero.relay.ui.m3.LargeTitle
import com.avetero.relay.ui.m3.PagerTabs
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.avetero.relay.ui.m3.PosterCard
import com.avetero.relay.ui.m3.RelaySheet
import com.avetero.relay.ui.m3.segmentShape
import com.avetero.relay.ui.theme.LocalWide
import com.avetero.relay.ui.theme.isTv
import kotlinx.coroutines.flow.drop

/** Saved channels, movies and series, in the order they were saved. */
internal class Saved(val chans: List<Channel>, val movies: List<Vod>, val series: List<Series>) {
    val labels = listOf("Channels", "Movies", "Series")
    fun count(tab: Int) = when (tab) { 0 -> chans.size; 1 -> movies.size; else -> series.size }
}

@Composable
internal fun rememberSaved(app: AppState): Saved {
    val chans = remember(app.favourites, app.channels) { app.channelsIn(FAVS) }
    val movies = remember(app.favourites, app.movies) { app.favourites.filter { it.startsWith("m") }.mapNotNull { k -> k.drop(1).toIntOrNull()?.let(app.movieById::get) } }
    val series = remember(app.favourites, app.series) { app.favourites.filter { it.startsWith("s") }.mapNotNull { k -> k.drop(1).toIntOrNull()?.let(app.seriesById::get) } }
    return remember(chans, movies, series) { Saved(chans, movies, series) }
}

/** Takes [key] out of Saved, with Undo on the message. */
internal fun unsave(app: AppState, key: String, name: String) {
    if (!app.isFav(key)) return
    app.toggleFav(key)
    app.say("Removed $name") { if (!app.isFav(key)) app.toggleFav(key) }
}

/** Saved (PhoneSaved / TvSaved boards). Phone: Channels | Movies | Series tabs you can swipe between; press and hold anything to remove it, with Undo. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun FavouritesScreen(app: AppState, nav: (Tab) -> Unit) = if (isTv()) TvSaved(app) else PhoneSaved(app)

@Composable
private fun PhoneSaved(app: AppState) {
    val saved = rememberSaved(app)
    val pager = rememberPagerState { 3 }
    var options by remember { mutableStateOf<Channel?>(null) }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitle("Saved")
        PagerTabs(pager, saved.labels.mapIndexed { i, label -> "$label ${saved.count(i)}" }, Modifier.padding(top = 8.dp, bottom = 16.dp))
        // a key per page so each keeps its own scroll; only the page on screen is composed
        HorizontalPager(pager, Modifier.fillMaxSize().padding(top = 16.dp)) { tab ->
            val list = rememberLazyListState()
            val grid = rememberLazyGridState()
            LaunchedEffect(Unit) { snapshotFlow { app.reselect }.drop(1).collect { if (tab == 0) list.animateScrollToItem(0) else grid.animateScrollToItem(0) } }
            when {
                saved.count(tab) == 0 -> Column(Modifier.fillMaxSize().padding(horizontal = 32.dp).padding(bottom = 80.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.StarOutline, null, Modifier.size(72.dp), tint = cs.primary)
                    Text(if (tab == 0) "No saved channels yet" else if (tab == 1) "No saved movies yet" else "No saved series yet", style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp))
                    Text("Press and hold any channel, movie or series to save it here.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    val (label, dest) = when (tab) { 0 -> "Browse live TV" to Screen.Live(); 1 -> "Browse movies" to Screen.VodGrid(false); else -> "Browse series" to Screen.VodGrid(true) }
                    Button({ app.go(dest) }, Modifier.padding(top = 24.dp)) { Text(label) }
                }
                tab == 0 -> LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(saved.chans, key = { _, c -> c.id }) { i, c ->
                        SavedChannelRow(app, c, saved.chans, segmentShape(i, saved.chans.size), Modifier.animateItem()) { options = c }
                    }
                }
                else -> LazyVerticalGrid(if (LocalWide.current) GridCells.Adaptive(120.dp) else GridCells.Fixed(3), Modifier.fillMaxSize(), grid,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tab == 1) items(saved.movies, key = { it.id }) { m ->
                        PosterCard(m.icon, m.name, m.id, { app.push(Screen.MovieDetail(m)) }, Modifier.animateItem(), sharedKey = "poster-m${m.id}", onLongClick = { unsave(app, "m${m.id}", m.name) })
                    } else items(saved.series, key = { it.id }) { s ->
                        PosterCard(s.cover, s.name, s.id, { app.push(Screen.SeriesDetail(s)) }, Modifier.animateItem(), sharedKey = "poster-s${s.id}", onLongClick = { unsave(app, "s${s.id}", s.name) })
                    }
                }
            }
        }
    }
    options?.let { c ->
        RelaySheet({ options = null }) {
            Text(c.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
            val colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerLow)
            ListItem({ options = null; app.push(Screen.LivePlayer(c, saved.chans)) }, colors = colors) { Text("Watch") }
            ListItem({ options = null; unsave(app, "c${c.id}", c.name) }, leadingContent = { Icon(Icons.Rounded.Delete, null) }, colors = colors) { Text("Remove from Saved") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A saved channel: number, logo, name, what's on with its progress; hold (or ⋮) to remove; no swipe-to-remove, that swipe moves between the tabs. */
@Composable
private fun SavedChannelRow(app: AppState, c: Channel, all: List<Channel>, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier, onOptions: () -> Unit) {
    LaunchedEffect(c.id) { app.loadEpg(c) }
    val now = app.programmesFor(c).current()
    val cs = MaterialTheme.colorScheme
    Row(modifier.clip(shape).fillMaxWidth().height(72.dp).background(cs.surfaceContainer)
        .combinedClickable(onLongClick = { unsave(app, "c${c.id}", c.name) }) { app.push(Screen.LivePlayer(c, all)) }
        .padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (c.num > 0) "${c.num}" else "", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(28.dp))
        ChannelLogo(c.icon, c.name, size = 44.dp, playing = app.liveChannel?.id == c.id)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now?.title ?: "No programme info", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (now != null) LinearWavyProgressIndicator({ now.progress() }, Modifier.fillMaxWidth())
        }
        IconButton(onOptions) { Icon(Icons.Rounded.MoreVert, "Options for ${c.name}", tint = cs.onSurfaceVariant) }
    }
}
