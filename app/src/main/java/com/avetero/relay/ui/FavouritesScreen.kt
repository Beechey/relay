package com.avetero.relay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.avetero.relay.AppState
import com.avetero.relay.AppState.Companion.FAVS
import com.avetero.relay.Screen

/** Board 13. Hold OK on an item to remove it. shortcut: no drag-to-reorder yet; order is the order they were added. */
@Composable
fun FavouritesScreen(app: AppState, nav: (Tab) -> Unit) = WithRail(Tab.Favourites, nav) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val chans = app.channelsIn(FAVS)
    val movies = app.favourites.filter { it.startsWith("m") }.mapNotNull { k -> app.movies.firstOrNull { "m${it.id}" == k } }
    val series = app.favourites.filter { it.startsWith("s") }.mapNotNull { k -> app.series.firstOrNull { "s${it.id}" == k } }

    val phone = compact()
    Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (phone) 16.dp else 40.dp, vertical = if (phone) 16.dp else 28.dp), verticalArrangement = Arrangement.spacedBy(if (phone) 16.dp else 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("Favourites", 28, Heavy, modifier = Modifier.weight(1f))
            T(if (phone) "Press and hold to remove" else "Hold OK to remove", 13, color = C.Muted)
        }
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF11151F)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Channels · ${chans.size}", "Movies · ${movies.size}", "Series · ${series.size}").forEachIndexed { i, label ->
                FocusSurface({ tab = i }, Modifier.height(40.dp), selected = tab == i, shape = RoundedCornerShape(9.dp)) { f ->
                    T(label, if (phone) 12 else 14, Bold, if (f) C.OnLight else if (tab == i) C.Text else C.Muted, Modifier.align(Alignment.Center).padding(horizontal = if (phone) 10.dp else 18.dp))
                }
            }
        }
        when (tab) {
            0 -> if (chans.isEmpty()) EmptyState("No favourites yet", "Hold OK on any channel, movie or series to add it here.", "Browse live TV") { app.go(Screen.Live()) }
            else LazyVerticalGrid(if (phone) GridCells.Fixed(1) else GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(chans, key = { it.id }) { c ->
                    LaunchedEffect(c.id) { app.loadEpg(c.id) }
                    val now = app.epg[c.id].current()
                    FocusSurface({ app.push(Screen.LivePlayer(c, chans)) }, Modifier.height(96.dp), idle = C.Surface, shape = RoundedCornerShape(14.dp),
                        onLongClick = { app.toggleFav("c${c.id}") }) { f ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Logo(c.icon, c.name, f, Modifier.size(56.dp, 42.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                T(c.name, 16, Heavy, fg(f))
                                T(now?.title ?: "No programme info", 13, color = sub(f))
                                ProgressLine(now?.progress() ?: 0f, Modifier.fillMaxWidth(), if (f) C.LightTrack else C.Track, if (f) C.OnLight else C.Accent)
                            }
                        }
                    }
                }
            }
            1 -> if (movies.isEmpty()) EmptyState("No favourite movies", "Hold OK on a movie poster to add it.")
            else LazyVerticalGrid(GridCells.Adaptive(if (phone) 104.dp else 130.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(movies, key = { it.id }) { m -> PosterTile(m.icon, m.name, m.id) { app.push(Screen.MovieDetail(m)) } }
            }
            else -> if (series.isEmpty()) EmptyState("No favourite series", "Hold OK on a series poster to add it.")
            else LazyVerticalGrid(GridCells.Adaptive(if (phone) 104.dp else 130.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(series, key = { it.id }) { s -> PosterTile(s.cover, s.name, s.id) { app.push(Screen.SeriesDetail(s)) } }
            }
        }
    }
}
