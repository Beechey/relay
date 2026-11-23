package com.avetero.relay.feature.search

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.Series
import com.avetero.relay.data.model.Vod
import com.avetero.relay.data.model.current
import com.avetero.relay.data.model.minutesLeft
import com.avetero.relay.feature.vod.TvPivot
import com.avetero.relay.feature.vod.TvPoster
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.components.stopAtEnd
import com.avetero.relay.ui.m3.Artwork
import com.avetero.relay.ui.m3.ChannelLogo
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvHints
import com.avetero.relay.ui.tv.TvLift
import com.avetero.relay.ui.tv.TvRowTitle
import com.avetero.relay.ui.tv.TvSafe
import com.avetero.relay.ui.tv.TvSurface

/** TvSearch board: the box (TV keyboard, phone remote app or voice), filters, the top result with categories beside it, then rows. */
@Composable
internal fun TvSearch(app: AppState, s: SearchState) {
    val q = app.searchQuery
    val box = remember { FocusRequester() }
    // opens straight into typing the first time; coming back from a result leaves the keyboard closed
    val startTyping = remember { q.isEmpty() }
    LaunchedEffect(Unit) { box.settle() }
    // Back clears the search first, then leaves
    BackHandler(q.isNotEmpty()) { app.searchQuery = "" }
    val (chans, ser, mov) = s.results

    Box(Modifier.fillMaxSize()) {
        TvPivot {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = TvSafe.Horizontal, end = TvSafe.Horizontal, top = 32.dp, bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "box") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvSearchField(q, { app.searchQuery = it }, "Channels, movies, series", Modifier.weight(1f), focusRequester = box, startEditing = startTyping, height = 60.dp)
                        if (q.isNotEmpty()) TvButton("Clear", { app.searchQuery = "" })
                    }
                }
                item(key = "how") {
                    Text("OK opens your TV's keyboard · type from the Google TV app on your phone · or hold the mic button and say it",
                        style = MaterialTheme.typography.bodyMedium, color = Tok.OnSurfaceVariant)
                }
                if (s.n.length < 2) {
                    val recent = s.recent
                    if (recent.isNotEmpty()) item(key = "recent") {
                        Column {
                            TvRowTitle("Recent")
                            LazyRow(Modifier.stopAtEnd(), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                                items(recent) { r -> Pill(r, false) { app.searchQuery = r } }
                            }
                        }
                    }
                    return@LazyColumn
                }
                if (s.total > 0) item(key = "filters") {
                    LazyRow(Modifier.stopAtEnd(), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        listOf("all" to "All · ${s.total}", "live" to "Live · ${chans.size}", "movies" to "Movies · ${mov.size}", "series" to "Series · ${ser.size}")
                            .filter { (k, label) -> k == "all" || !label.endsWith(" 0") }
                            .forEach { (k, label) -> item(key = k) { Pill(label, s.filter == k) { s.filter = k } } }
                    }
                }
                if (s.total == 0 && s.cats.isEmpty()) item(key = "none") {
                    Text(if (app.libraryLoading || app.vodLoading) "Still loading your library…" else if (s.searching) "Searching…" else "No results for “${q.trim()}”",
                        style = MaterialTheme.typography.titleMedium, color = Tok.OnSurfaceVariant)
                }
                val top = s.top.takeIf { s.filter == "all" }
                if (top != null || (s.filter == "all" && s.cats.isNotEmpty())) item(key = "top") {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (top != null) TopResult(app, s, top, chans)
                        if (s.cats.isNotEmpty()) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvRowTitle("Categories", Modifier.padding(bottom = 0.dp))
                            s.cats.take(3).forEach { (label, screen) ->
                                TvSurface({ s.open { app.go(screen) } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp), idle = Tok.Container) {
                                    Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 20.dp))
                                }
                            }
                        }
                    }
                }
                if (s.shows("live") && chans.isNotEmpty()) row("Channels", chans.size) {
                    items(chans, key = { it.id }) { c ->
                        val now = app.programmesFor(c).current()
                        TvSurface({ s.open { app.push(Screen.LivePlayer(c, chans)) } }, Modifier.size(196.dp, 92.dp), lift = TvLift.Card, idle = Tok.Container) {
                            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (now != null) Text(now.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (s.shows("movies") && mov.isNotEmpty()) row("Movies", mov.size) {
                    items(mov, key = { it.id }) { m -> TvPoster(m.icon, m.name, m.id, "poster-m${m.id}", { s.open { app.push(Screen.MovieDetail(m)) } }, width = 120.dp) }
                }
                if (s.shows("series") && ser.isNotEmpty()) row("Series", ser.size) {
                    items(ser, key = { it.id }) { x -> TvPoster(x.cover, x.name, x.id, "poster-s${x.id}", { s.open { app.push(Screen.SeriesDetail(x)) } }, width = 120.dp) }
                }
            }
        }
        TvHints("▼" to "Movies ${mov.size} · Series ${ser.size}", "Back" to "Clear, then For you",
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = TvSafe.Horizontal, bottom = TvSafe.Vertical))
    }
}

private fun LazyListScope.row(title: String, count: Int, content: LazyListScope.() -> Unit) = item(key = "row:$title") {
    Column {
        TvRowTitle("$title · $count")
        LazyRow(Modifier.stopAtEnd(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 8.dp), content = content)
    }
}

@Composable
private fun Pill(label: String, on: Boolean, onClick: () -> Unit) =
    TvSurface(onClick, Modifier.height(40.dp), selected = on, shape = RoundedCornerShape(20.dp), idle = Tok.Container) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp))
    }

/** The best match: a channel shows what's on with Watch and In the guide; a title shows its poster with Details. */
@Composable
private fun TopResult(app: AppState, s: SearchState, top: Any, chans: List<Channel>) =
    Row(Modifier.width(420.dp).height(168.dp).clip(RoundedCornerShape(24.dp)).background(Tok.Container).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        when (top) {
            is Channel -> {
                LaunchedEffect(top.id) { app.loadEpg(top) }
                val now = app.programmesFor(top).current()
                ChannelLogo(top.icon, top.name, size = 128.dp)
                TopText(if (top.num > 0) "TOP RESULT · CHANNEL ${top.num}" else "TOP RESULT · CHANNEL", top.name, now?.let { "${it.title} · ${it.minutesLeft()}" }) {
                    TvButton("Watch", { s.open { app.push(Screen.LivePlayer(top, chans)) } }, primary = true)
                    TvButton("In the guide", { s.open { app.go(Screen.Guide) } })
                }
            }
            is Series, is Vod -> {
                val (id, name, image) = if (top is Series) Triple(top.id, top.name, top.cover) else (top as Vod).let { Triple(it.id, it.name, it.icon) }
                Artwork(image, name, id, Modifier.size(85.dp, 128.dp).clip(RoundedCornerShape(16.dp)), showTitle = image.isBlank())
                TopText(if (top is Series) "TOP RESULT · SERIES" else "TOP RESULT · MOVIE", name, null) {
                    TvButton("Details", { s.open { app.push(if (top is Series) Screen.SeriesDetail(top) else Screen.MovieDetail(top as Vod)) } }, primary = true)
                }
            }
        }
    }

@Composable
private fun TopText(eyebrow: String, name: String, sub: String?, buttons: @Composable () -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Tok.Primary)
        Text(name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = Tok.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
    }
