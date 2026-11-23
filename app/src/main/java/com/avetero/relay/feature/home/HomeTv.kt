package com.avetero.relay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Carousel
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.rememberCarouselState
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.Resume
import com.avetero.relay.data.model.current
import com.avetero.relay.data.model.progress
import com.avetero.relay.ui.m3.Artwork
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvHints
import com.avetero.relay.ui.tv.TvLift
import com.avetero.relay.ui.tv.TvRowTitle
import com.avetero.relay.ui.tv.TvSafe
import com.avetero.relay.ui.tv.TvSurface
import kotlinx.coroutines.delay

/** TV rows and columns scroll so the focused item sits about a third of the way in (design-spec: fixed pivot). */
internal val TvPivot = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val target = containerSize * 0.3f
        // something bigger than what's left after the pivot just has to fit
        val lead = if (size <= containerSize && target + size > containerSize) containerSize - size else target
        return offset - lead
    }
}

@Composable
internal fun HomeTv(app: AppState, data: HomeData) {
    // focus comes back to whatever you left from (scroll positions are saved by the lazy states)
    var lastFocus by rememberSaveable { mutableStateOf<String?>(null) }
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun req(key: String) = requesters.getOrPut(key) { FocusRequester() }
    fun Modifier.remembered(key: String) = focusRequester(req(key)).onFocusChanged { if (it.isFocused) lastFocus = key }

    // at app start the window may not take focus yet, so keep asking briefly until something on the page has it
    var pageFocused by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused) repeat(20) {
            if (pageFocused) return@LaunchedEffect
            val first = when { data.heroItems.isNotEmpty() -> "hero"; data.live.isNotEmpty() -> "live-${data.live[0].id}"; else -> null }
            val target = lastFocus?.let(requesters::get) ?: first?.let(requesters::get)
            runCatching { target?.requestFocus() }
            delay(100)
        }
    }
    val info = remember { mutableStateMapOf<String, HeroInfo>() }

    CompositionLocalProvider(LocalBringIntoViewSpec provides TvPivot) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().onFocusChanged { pageFocused = it.hasFocus }, rememberLazyListState(),
                contentPadding = PaddingValues(top = TvSafe.Vertical, bottom = 72.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                item(key = "hero") {
                    Box(Modifier.padding(start = 24.dp, end = TvSafe.Horizontal)) {
                        if (data.heroItems.isEmpty()) EmptyHero(app) else Hero(app, data.heroItems.take(4), info, Modifier.remembered("hero"))
                    }
                }
                if (data.live.isNotEmpty()) item(key = "live") {
                    Shelf("Live now") {
                        items(data.live, key = { it.id }) { c -> LiveCard(app, c, data.live, Modifier.remembered("live-${c.id}")) }
                    }
                }
                if (data.resumes.isNotEmpty()) item(key = "continue") {
                    Shelf("Continue watching") {
                        items(data.resumes, key = { it.key }) { r -> ContinueCard(app, r, Modifier.remembered("cw-${r.key}")) }
                    }
                }
                if (data.recentMovies.isNotEmpty()) item(key = "rm") {
                    Shelf("Recently watched movies") {
                        items(data.recentMovies, key = { it.id }) { m -> Poster(m.icon, m.name, m.id, Modifier.remembered("m-${m.id}")) { app.push(Screen.MovieDetail(m)) } }
                    }
                }
                if (data.recentSeries.isNotEmpty()) item(key = "rs") {
                    Shelf("Recently watched series") {
                        items(data.recentSeries, key = { it.id }) { s -> Poster(s.cover, s.name, s.id, Modifier.remembered("s-${s.id}")) { app.push(Screen.SeriesDetail(s)) } }
                    }
                }
                if (data.addedMovies.isNotEmpty()) item(key = "added") {
                    Shelf("Recently added movies") {
                        items(data.addedMovies, key = { it.id }) { m -> Poster(m.icon, m.name, m.id, Modifier.remembered("a-${m.id}")) { app.push(Screen.MovieDetail(m)) } }
                    }
                }
            }
            TvHints("◀" to "Menu", "Hold OK" to "Remove", "Back" to "Exit", modifier = Modifier.align(Alignment.BottomEnd).padding(end = TvSafe.Horizontal, bottom = TvSafe.Vertical))
        }
    }
}

/** The hero: rotates through what you were watching (pausing while you're on it), Resume and Details/Episodes. */
@Composable
private fun Hero(app: AppState, items: List<Resume>, info: MutableMap<String, HeroInfo>, resumeModifier: Modifier) {
    val state = rememberCarouselState()
    Carousel(items.size, Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(28.dp)), state, autoScrollDurationMillis = 9_000,
        carouselIndicator = {
            // the current slide is a wide pill, the rest dots
            Row(Modifier.align(Alignment.BottomEnd).padding(40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (items.size > 1) items.indices.forEach { i ->
                    val on = i == state.activeItemIndex
                    Box(Modifier.size(if (on) 24.dp else 8.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(if (on) Tok.OnSurface else Tok.Outline))
                }
            }
        }) { i ->
        val r = items[i]
        LaunchedEffect(r.key) { if (r.key !in info) info[r.key] = loadInfo(app, r) }
        Box(Modifier.fillMaxSize().background(Tok.SecondaryContainer)) {
            Artwork(info[r.key]?.backdrop?.ifEmpty { null } ?: r.image, "", r.key.hashCode(), Modifier.fillMaxSize(), showTitle = false)
            // fades: left for the words
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Tok.Surface.copy(alpha = .92f), .5f to Tok.Surface.copy(alpha = .6f), 1f to Color.Transparent)))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 48.dp, vertical = 40.dp).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (r.meta.startsWith("Up next")) "UP NEXT" else "CONTINUE WATCHING", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.4.sp), color = Tok.OnSecondaryContainer)
                Text(heroTitle(r), style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Tok.OnSurface)
                Text(resumeLine(r), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), color = Tok.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                info[r.key]?.plot?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Tok.OnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton(if (r.pos > 0) "Resume" else "Play", { resume(app, r) }, resumeModifier, primary = true, leading = { Icon(Icons.Play, null, Modifier.size(18.dp)) })
                    TvButton(if (r.seriesId != 0) "Episodes" else "Details", { details(app, r) })
                }
            }
        }
    }
}

@Composable
private fun EmptyHero(app: AppState) =
    Column(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(28.dp)).background(Tok.SecondaryContainer).padding(horizontal = 48.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)) {
        Text((if (app.libraryLoading) "Loading your library…" else greeting()).uppercase(), style = MaterialTheme.typography.labelLarge, color = Tok.OnSecondaryContainer)
        Text("Ready when you are", style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, fontWeight = FontWeight.SemiBold), color = Tok.OnSurface)
        Text(if ("vod" in app.parts || "series" in app.parts) "Pick a channel below, or browse movies and series." else "Pick a channel below, or open Live TV for the full list.",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), color = Tok.OnSurfaceVariant)
    }

/** A titled row; cards start under the title and scroll out to the screen edge. */
@Composable
private fun Shelf(title: String, content: LazyListScope.() -> Unit) =
    Column {
        TvRowTitle(title, Modifier.padding(start = 24.dp))
        // focused cards grow, so the row keeps room around them
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(start = 24.dp, end = TvSafe.Horizontal, top = 8.dp, bottom = 8.dp), content = content)
    }

/** Live now: the channel, what's on and how far through. */
@Composable
private fun LiveCard(app: AppState, c: Channel, list: List<Channel>, modifier: Modifier) {
    LaunchedEffect(c.id) { app.loadEpg(c) }
    val now = app.programmesFor(c).current()
    TvSurface({ watchChannel(app, c, list) }, modifier.size(200.dp, 112.dp), idle = Tok.Container, lift = TvLift.Card) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(c.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now?.title ?: "Live", style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = .8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Tok.ContainerHighest)) {
                if (now != null) Box(Modifier.fillMaxWidth(now.progress()).fillMaxHeight().background(Tok.Primary))
            }
        }
    }
}

/** Continue watching: 16:9 picture, title and time left; hold OK to remove. */
@Composable
private fun ContinueCard(app: AppState, r: Resume, modifier: Modifier) =
    TvSurface({ details(app, r) }, modifier.width(262.dp), onLongClick = { removeResume(app, r) }, idle = Tok.Container, lift = TvLift.Card) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(12.dp))) {
                Artwork(r.image, r.title, r.key.hashCode(), Modifier.fillMaxSize(), showTitle = false)
                if (r.dur > 1) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = .4f))) {
                    Box(Modifier.fillMaxWidth((r.pos / r.dur.toFloat()).coerceIn(0f, 1f)).fillMaxHeight().background(Tok.Primary))
                }
            }
            Text(heroTitle(r), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
            Text(resumeLine(r), style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(alpha = .8f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }

@Composable
private fun Poster(image: String, title: String, seed: Int, modifier: Modifier, onClick: () -> Unit) =
    TvSurface(onClick, modifier.size(128.dp, 192.dp), lift = TvLift.Card, idle = Tok.Container) {
        Artwork(image, title, seed, Modifier.fillMaxSize())
    }
