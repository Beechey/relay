package com.avetero.relay.feature.home

import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.current
import com.avetero.relay.data.model.progress
import com.avetero.relay.ui.m3.Artwork
import com.avetero.relay.ui.m3.ChannelLogo
import com.avetero.relay.ui.m3.PosterCard
import com.avetero.relay.ui.m3.SectionHeader
import com.avetero.relay.ui.m3.segmentShape

@Composable
internal fun HomePhone(app: AppState, data: HomeData) {
    val list = rememberLazyListState()
    // the For you tab tapped again at its root: back to the top
    val reselect0 = remember { app.reselect }
    LaunchedEffect(app.reselect) { if (app.reselect != reselect0) list.animateScrollToItem(0) }
    val parts = app.parts

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), list, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item(key = "search") { SearchRow(app) }

        if (data.heroItems.isEmpty() && data.live.isEmpty() && data.recentMovies.isEmpty() && data.recentSeries.isEmpty() && data.addedMovies.isEmpty()) item(key = "empty") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (app.libraryLoading) LoadingIndicator()
                Text(if (app.libraryLoading) "Loading your library…" else greeting(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Ready when you are", style = MaterialTheme.typography.headlineSmall)
                Text(if ("vod" in parts || "series" in parts) "Pick a channel in Live, or browse movies and series in Library." else "Open Live for the full list of channels.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (data.heroItems.isNotEmpty()) item(key = "continue") {
            Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Continue watching", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                ContinueCarousel(app, data)
            }
        }

        if (data.live.isNotEmpty()) {
            // one item, so the rows sit 2dp apart as a grouped list rather than spaced like sections
            item(key = "live") {
                Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionHeader("Live now", Modifier.padding(bottom = 6.dp), action = "See all") { app.go(Screen.Live()) }
                    data.live.forEachIndexed { i, c -> key(c.id) { LiveRow(app, c, data.live, i, Modifier) } }
                }
            }
        }

        if (data.recentMovies.isNotEmpty()) item(key = "rm") {
            PosterRow("Recently watched movies", Modifier.animateItem()) {
                items(data.recentMovies, key = { it.id }) { m -> PosterCard(m.icon, m.name, m.id, { app.push(Screen.MovieDetail(m)) }, width = 120.dp, sharedKey = "poster-m${m.id}") }
            }
        }
        if (data.recentSeries.isNotEmpty()) item(key = "rs") {
            PosterRow("Recently watched series", Modifier.animateItem()) {
                items(data.recentSeries, key = { it.id }) { s -> PosterCard(s.cover, s.name, s.id, { app.push(Screen.SeriesDetail(s)) }, width = 120.dp, sharedKey = "poster-s${s.id}") }
            }
        }
        if (data.addedMovies.isNotEmpty()) item(key = "added") {
            PosterRow("Recently added movies", Modifier.animateItem()) {
                items(data.addedMovies, key = { it.id }) { m -> PosterCard(m.icon, m.name, m.id, { app.push(Screen.MovieDetail(m)) }, width = 120.dp, sharedKey = "poster-m${m.id}") }
            }
        }
    }
}

/** Search bar (opens Search) and the account avatar (opens Settings). */
@Composable
private fun SearchRow(app: AppState) =
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f).height(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = "Search") { app.go(Screen.Search) }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Search channels, movies, series", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(role = Role.Button) { app.go(Screen.Settings()) }
            .clearAndSetSemantics { contentDescription = "Settings and playlists"; role = Role.Button }, contentAlignment = Alignment.Center) {
            Text(app.account?.name?.firstOrNull()?.uppercase() ?: "R", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }

/** Continue watching: tap opens the title's page, press and hold takes it off the list (with Undo). */
@Composable
private fun ContinueCarousel(app: AppState, data: HomeData) {
    val items = data.heroItems
    val count by rememberUpdatedState(items.size)
    val state = rememberCarouselState { count }
    HorizontalMultiBrowseCarousel(state, preferredItemWidth = 236.dp, modifier = Modifier.fillMaxWidth().height(220.dp),
        itemSpacing = 8.dp, contentPadding = PaddingValues(horizontal = 16.dp)) { i ->
        val r = items.getOrNull(i) ?: return@HorizontalMultiBrowseCarousel
        Box(Modifier.fillMaxSize().maskClip(RoundedCornerShape(28.dp))
            .combinedClickable(onClickLabel = "Open ${heroTitle(r)}", onLongClickLabel = "Remove", onLongClick = { removeResume(app, r) }) { details(app, r) }) {
            Artwork(r.image, r.title, r.key.hashCode(), Modifier.fillMaxSize(), showTitle = false)
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.32f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(heroTitle(r), style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(resumeLine(r), style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.dur > 1) LinearProgressIndicator({ r.pos / r.dur.toFloat() }, Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.outlineVariant, drawStopIndicator = {})
            }
        }
    }
}

/** A channel on air now: logo, name · programme, how far through (grouped list row). */
@Composable
private fun LiveRow(app: AppState, c: Channel, list: List<Channel>, i: Int, modifier: Modifier) {
    LaunchedEffect(c.id) { app.loadEpg(c) }
    val now = app.programmesFor(c).current()
    Row(modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 80.dp).clip(segmentShape(i, list.size))
        .background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClickLabel = "Watch ${c.name}") { watchChannel(app, c, list) }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ChannelLogo(c.icon, c.name, playing = app.liveChannel?.id == c.id)
        // two lines like Live's rows: the channel, then what's on with its progress
        Column(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now?.title ?: "Live", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (now != null) LinearWavyProgressIndicator({ now.progress() }, Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

@Composable
private fun PosterRow(title: String, modifier: Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) =
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp), content = content)
    }
