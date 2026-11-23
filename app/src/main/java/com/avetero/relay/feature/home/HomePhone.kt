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
import com.avetero.relay.data.model.minutesLeft
import com.avetero.relay.util.fmtTime
import com.avetero.relay.ui.theme.tone
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.layout.FlowRow

@Composable
internal fun HomePhone(app: AppState, data: HomeData) {
    val list = rememberLazyListState()
    // the For you tab tapped again at its root: back to the top
    val reselect0 = remember { app.reselect }
    LaunchedEffect(app.reselect) { if (app.reselect != reselect0) list.animateScrollToItem(0) }
    val parts = app.parts

    // what's on: your favourite and recent channels, or (new playlists) the first channels that have a guide, so the
    // page never opens bare
    val onNow = remember(data.live, app.channelsVisible, app.guide) {
        data.live.ifEmpty { app.channelsVisible.asSequence().filter { c -> c.epgId.isNotEmpty() && !app.guide[c.epgId].isNullOrEmpty() }.take(4).toList() }
    }
    val featured = app.liveChannel?.takeIf { lc -> onNow.any { it.id == lc.id } } ?: onNow.firstOrNull()
    val others = onNow.filter { it.id != featured?.id }.take(3)

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), list, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        item(key = "search") {
            Column {
                SearchRow(app)
                Text(greeting(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp))
            }
        }

        if (featured != null) item(key = "onnow") {
            // one grouped block: a big card for the channel you were on, then the next few channels
            Column(Modifier.animateItem().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FeaturedLive(app, featured, onNow, last = others.isEmpty(), lastWatched = app.liveChannel?.id == featured.id || data.live.isNotEmpty())
                others.forEachIndexed { i, c -> key(c.id) { LiveRow(app, c, onNow, i, others.size) } }
            }
        } else if (app.libraryLoading) item(key = "loading") {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LoadingIndicator()
                Text("Loading your library…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (data.heroItems.isNotEmpty()) item(key = "continue") {
            Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Continue watching", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                ContinueCarousel(app, data)
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

        if (app.liveCatsVisible.isNotEmpty() || app.vodCatsVisible.isNotEmpty()) item(key = "cats") {
            Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Browse categories", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.liveCatsVisible.take(8).forEach { c ->
                        AssistChip({ app.go(Screen.Live(c.id)) }, { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Rounded.LiveTv, null, Modifier.size(AssistChipDefaults.IconSize)) })
                    }
                    app.vodCatsVisible.take(4).forEach { c ->
                        AssistChip({ app.go(Screen.VodGrid(false, c.id)) }, { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Rounded.Movie, null, Modifier.size(AssistChipDefaults.IconSize)) })
                    }
                }
            }
        }
    }
}

/** The big "on now" card at the top of the group: what's on, how far through, tap to watch. */
@Composable
private fun FeaturedLive(app: AppState, c: Channel, list: List<Channel>, last: Boolean, lastWatched: Boolean) {
    LaunchedEffect(c.id) { app.loadEpg(c) }
    val now = app.programmesFor(c).current()
    val shape = if (last) RoundedCornerShape(28.dp) else RoundedCornerShape(28.dp, 28.dp, 4.dp, 4.dp)
    Box(Modifier.fillMaxWidth().height(208.dp).clip(shape).background(tone(c.id))
        .clickable(onClickLabel = "Watch ${c.name}") { watchChannel(app, c, list) }) {
        // the channel's logo, large and soft, as the card's artwork
        if (c.icon.isNotBlank()) AsyncImage(c.icon, null, Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 20.dp).size(120.dp, 72.dp), contentScale = ContentScale.Fit, alpha = 0.9f)
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("LIVE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onError,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.error).padding(horizontal = 8.dp, vertical = 2.dp))
            if (lastWatched) Text("Last watched", style = MaterialTheme.typography.labelMedium, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(now?.title ?: c.name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (now != null) "${c.name} · until ${fmtTime(now.end)}" else "Live now", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (now != null) LinearWavyProgressIndicator({ now.progress() }, Modifier.fillMaxWidth().padding(top = 6.dp), trackColor = Color.White.copy(alpha = 0.3f))
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

/** A channel on air now, under the featured card: logo, what's on, the channel and time left (grouped list row). */
@Composable
private fun LiveRow(app: AppState, c: Channel, list: List<Channel>, i: Int, count: Int) {
    LaunchedEffect(c.id) { app.loadEpg(c) }
    val now = app.programmesFor(c).current()
    val shape = if (i == count - 1) RoundedCornerShape(4.dp, 4.dp, 28.dp, 28.dp) else RoundedCornerShape(4.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClickLabel = "Watch ${c.name}") { watchChannel(app, c, list) }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ChannelLogo(c.icon, c.name, size = 44.dp, playing = app.liveChannel?.id == c.id)
        Column(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(now?.title ?: c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (now != null) "${c.name} · ${now.minutesLeft()}" else "Live", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PosterRow(title: String, modifier: Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) =
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp), content = content)
    }
