package com.avetero.relay.feature.home

import com.avetero.relay.ui.components.stopAtEnd
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.PlayItem
import com.avetero.relay.data.model.Resume
import com.avetero.relay.data.model.current
import com.avetero.relay.data.model.progress
import com.avetero.relay.ui.components.Art
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.components.Clock
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.IconBtn
import com.avetero.relay.ui.components.LivePill
import com.avetero.relay.ui.components.Logo
import com.avetero.relay.ui.components.Pill
import com.avetero.relay.ui.components.ProgressLine
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.components.WithRail
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.Semi
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import com.avetero.relay.ui.theme.fg
import com.avetero.relay.ui.theme.sub
import com.avetero.relay.ui.theme.tone
import com.avetero.relay.util.fmtDur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Artwork and story for a hero item, fetched from the provider once per title. */
private data class HeroInfo(val backdrop: String, val plot: String)

/**
 * For you (redesign board 01): a hero that rotates through what you were watching, then "Live now" on your
 * favourite and recent channels, then your recent movies and series.
 */
@Composable
fun HomeScreen(app: AppState, nav: (Tab) -> Unit) {
    val scroll = rememberScrollState()
    // For you sits behind the bar, where the normal Down search finds nothing; point Down back at the page
    val entry = remember { FocusRequester() }
    WithRail(Tab.Home, nav, behindDock = true, dockHidden = scroll.value > 200, pageEntry = entry) { ForYou(app, scroll, entry) }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ForYou(app: AppState, scroll: androidx.compose.foundation.ScrollState, entry: FocusRequester) {
    val phone = compact()
    // only what's still downloaded: a movie or series part that's switched off drops out
    val heroItems = app.resumes.filter { (it.vodId == 0 || "vod" in app.parts) && (it.seriesId == 0 || "series" in app.parts) }.take(4)
    var index by remember { mutableIntStateOf(0) }
    var heroFocused by remember { mutableStateOf(false) }
    // the hero sits under the dock; whenever it has focus, show it whole rather than half scrolled
    LaunchedEffect(heroFocused) { if (heroFocused) scroll.animateScrollTo(0) }
    val info = remember { mutableStateMapOf<String, HeroInfo>() }
    val resumeBtn = remember { FocusRequester() }
    val liveFirst = remember { FocusRequester() }
    val r = heroItems.getOrNull(index % heroItems.size.coerceAtLeast(1))

    // rotate while nobody is using the hero, so the buttons never change under you
    LaunchedEffect(index, heroFocused, heroItems.size) { if (!heroFocused && heroItems.size > 1) { delay(9_000); index = (index + 1) % heroItems.size } }
    LaunchedEffect(r?.key) { r?.let { item -> if (item.key !in info) info[item.key] = loadInfo(app, item) } }
    // at app start the window may not take focus yet, so keep asking briefly until something on the page has it
    var pageFocused by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused) repeat(20) { if (pageFocused) return@LaunchedEffect; delay(100); runCatching { (if (heroItems.isNotEmpty()) resumeBtn else liveFirst).requestFocus() } }
    }

    val live = remember(app.favourites, app.recentChannels, app.channels) {
        val favs = app.favourites.mapNotNull { k -> if (k.startsWith("c")) app.channels.firstOrNull { it.id == k.drop(1).toIntOrNull() } else null }
        (favs + app.channelsIn(AppState.RECENT)).distinctBy { it.id }.take(12)
    }
    val recentMovies = remember(app.recentMovies, app.movies) { app.recentMovies.mapNotNull { id -> app.movies.firstOrNull { it.id == id } } }
    val recentSeries = remember(app.recentSeries, app.series) { app.recentSeries.mapNotNull { id -> app.series.firstOrNull { it.id == id } } }
    val side = if (phone) 20.dp else 64.dp

    Box(Modifier.weight(1f).fillMaxSize()) {
        Column(Modifier.fillMaxSize().onFocusChanged { pageFocused = it.hasFocus }.focusRequester(entry).focusRestorer { FocusRequester.Default }.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(if (phone) 20.dp else 26.dp)) {
            // ---- hero
            Box(Modifier.fillMaxWidth().height(if (phone) 440.dp else 470.dp).onFocusChanged { heroFocused = it.hasFocus }) {
                Crossfade(r?.key, label = "hero") { key ->
                    val item = heroItems.firstOrNull { it.key == key }
                    Box(Modifier.fillMaxSize()) {
                        if (item != null) Art(info[item.key]?.backdrop?.ifEmpty { null } ?: item.image, "", item.key.hashCode(), Modifier.fillMaxSize(), showTitle = false)
                        else Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF2F5F9C), C.Ground), radius = 1400f)))
                    }
                }
                // fades: left for the words, bottom into the rows
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to C.Ground.copy(alpha = if (phone) .55f else .94f), .45f to C.Ground.copy(alpha = .55f), .75f to Color.Transparent)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.45f to Color.Transparent, 1f to C.Ground)))
                Column(Modifier.align(if (phone) Alignment.BottomStart else Alignment.CenterStart).padding(start = side, end = side, bottom = if (phone) 18.dp else 30.dp, top = if (phone) 0.dp else 70.dp)
                    .widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (r == null) {
                        Pill(if (app.libraryLoading) "Loading your library…" else greeting())
                        T("Ready when you are", if (phone) 34 else 56, Heavy, lineHeight = if (phone) 36 else 58)
                        T(if ("vod" in app.parts || "series" in app.parts) "Pick a channel below, or browse movies and series." else "Pick a channel below, or open Live for the full list.", 15, color = C.Soft, maxLines = 2)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pill(if (r.meta.startsWith("Up next")) "Up next" else "Continue watching"); Pill(r.meta.removePrefix("Up next · ").substringAfterLast(" · ")) }
                        T(heroTitle(r), if (phone) 36 else 60, Heavy, maxLines = 2, lineHeight = if (phone) 38 else 62)
                        info[r.key]?.plot?.takeIf { it.isNotBlank() }?.let { T(it, 15, color = C.Soft, maxLines = if (phone) 2 else 3, lineHeight = 22) }
                        if (r.dur > 1) Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            ProgressLine(r.pos / r.dur.toFloat(), Modifier.fillMaxWidth(), C.Glass3, height = 4.dp)
                            Row { T(r.title.substringBefore(" · ").takeIf { r.seriesId != 0 } ?: "", 13, Semi, C.Muted, Modifier.weight(1f)); T(if (r.pos > 0) "${fmtDur(r.dur - r.pos)} left" else "Not started", 13, Semi, C.Muted) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Btn(if (r.pos > 0) "Resume" else "Play", { resume(app, r) }, style = BtnStyle.Primary, icon = Icons.Play, height = 50.dp, focusRequester = resumeBtn)
                            Btn(if (r.seriesId != 0) "All episodes" else "Details", { details(app, r) }, height = 50.dp)
                        }
                        if (heroItems.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            heroItems.indices.forEach { i -> Box(Modifier.height(6.dp).width(if (i == index % heroItems.size) 22.dp else 6.dp).clip(RoundedCornerShape(3.dp)).background(if (i == index % heroItems.size) C.Text else C.Glass3)) }
                        }
                    }
                }
                if (phone) Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconBtn(Icons.Star, "Favourites", { app.go(Screen.Favourites) }, size = 42.dp)
                    IconBtn(Icons.Settings, "Settings", { app.go(Screen.Settings()) }, size = 42.dp)
                }
            }

            // ---- rows
            if (live.isNotEmpty()) Shelf(side, "Live now", "Favourite and recent channels") {
                items(live, key = { it.id }) { c -> LiveCard(app, c, live, if (c == live.first()) liveFirst else null) }
            }
            if (heroItems.isNotEmpty()) Shelf(side, "Continue watching", if (phone) "Press and hold to remove" else "Hold OK to remove") {
                items(app.resumes, key = { it.key }) { item ->
                    WideCard(item.title, item.meta.removePrefix("Up next · ") + if (item.pos > 0 && item.dur > item.pos) " · ${fmtDur(item.dur - item.pos)} left" else "",
                        { details(app, item) }, onLongClick = { app.removeResume(item.key) }) {
                        Art(item.image, item.title, item.key.hashCode(), Modifier.fillMaxSize(), showTitle = false)
                        if (item.dur > 1) ProgressLine(item.pos / item.dur.toFloat(), Modifier.fillMaxWidth().align(Alignment.BottomStart), Color(0x66000000), height = 4.dp)
                    }
                }
            }
            if (recentMovies.isNotEmpty()) Shelf(side, "Recently watched movies") {
                items(recentMovies, key = { it.id }) { m -> Poster(m.icon, m.name, m.id) { app.push(Screen.MovieDetail(m)) } }
            }
            if (recentSeries.isNotEmpty()) Shelf(side, "Recently watched series") {
                items(recentSeries, key = { it.id }) { s -> Poster(s.cover, s.name, s.id) { app.push(Screen.SeriesDetail(s)) } }
            }
            Spacer(Modifier.height(if (phone) 16.dp else 40.dp))
        }
        if (!phone && scroll.value <= 200) Box(Modifier.align(Alignment.TopEnd).padding(top = 38.dp, end = 40.dp)) { Clock(20) }
    }
}

private fun greeting() = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }

/** "E6 · EN - Fringe - S05E06" → "Fringe"; movies keep their own name. */
private fun heroTitle(r: Resume): String =
    if (r.seriesId != 0) r.meta.removePrefix("Up next · ").substringBefore(" · ").substringAfter(" - ").ifBlank { r.title } else r.title

private suspend fun loadInfo(app: AppState, r: Resume): HeroInfo = withContext(Dispatchers.IO) {
    runCatching {
        when {
            r.vodId != 0 -> app.api.vodInfo(r.vodId).let { HeroInfo(it.backdrop, it.plot) }
            r.seriesId != 0 -> app.api.seriesInfo(r.seriesId).let { HeroInfo(it.cover, it.plot) }
            else -> HeroInfo("", "")
        }
    }.getOrDefault(HeroInfo("", ""))
}

/** Movies play straight away from where you stopped; series open on their page, where Resume picks the episode. */
private fun resume(app: AppState, r: Resume) {
    val vod = app.movies.firstOrNull { it.id == r.vodId }
    if (vod != null) app.push(Screen.VodPlayer(PlayItem(app.api.vodUrl(vod), vod.name, r.meta, vod.icon, r.key, vodId = vod.id)))
    else details(app, r)
}

private fun details(app: AppState, r: Resume) {
    app.movies.firstOrNull { it.id == r.vodId }?.let { return app.push(Screen.MovieDetail(it)) }
    app.series.firstOrNull { it.id == r.seriesId }?.let { app.push(Screen.SeriesDetail(it)) }
}

/** A titled, sideways-scrolling row; cards start under the title and scroll out to the screen edge. */
@Composable
private fun Shelf(side: Dp, title: String, hint: String? = null, content: LazyListScope.() -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(start = side, end = 40.dp), verticalAlignment = Alignment.Bottom) {
            T(title, 19, Heavy, modifier = Modifier.weight(1f))
            if (hint != null) T(hint, 12, Semi, C.Dim)
        }
        // focused cards grow, so the row keeps room around them
        LazyRow(modifier = Modifier.stopAtEnd(), horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(start = side, end = 40.dp, top = 8.dp, bottom = 8.dp), content = content)
    }

/** "Live now": the channel, what's on and how far through, over the channel's colour. */
@Composable
private fun LiveCard(app: AppState, c: Channel, list: List<Channel>, focusRequester: FocusRequester?) {
    LaunchedEffect(c.id) { app.loadEpg(c) }
    val now = app.programmesFor(c).current()
    val phone = compact()
    FocusSurface({ app.push(Screen.LivePlayer(c, list)) }, Modifier.size(if (phone) 230.dp else 262.dp, if (phone) 128.dp else 148.dp),
        idle = tone(c.id), shape = RoundedCornerShape(18.dp), focusRequester = focusRequester, lift = 1.08f) { f ->
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.25f to Color.Transparent, 1f to Color(0xD9000000))))
        LivePill(Modifier.align(Alignment.TopStart).padding(10.dp))
        Logo(c.icon, c.name, false, Modifier.align(Alignment.TopEnd).padding(10.dp).size(54.dp, 36.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            T(now?.title ?: c.name, 14, Heavy, if (f) C.OnLight else C.Text)
            T(if (now != null) c.name else "Live", 12, Semi, if (f) C.OnLightSub else C.Soft)
            if (now != null) ProgressLine(now.progress(), Modifier.width(if (phone) 200.dp else 232.dp), if (f) C.LightTrack else C.Glass3, if (f) C.OnLight else null, height = 3.dp)
        }
    }
}

/** Continue watching: a 16:9 picture with the title and how long is left under it. */
@Composable
private fun WideCard(title: String, subtitle: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, picture: @Composable BoxScope.() -> Unit) {
    val phone = compact()
    FocusSurface(onClick, Modifier.width(if (phone) 220.dp else 262.dp), onLongClick = onLongClick, shape = RoundedCornerShape(18.dp), lift = 1.06f) { f ->
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(if (phone) 118.dp else 140.dp).clip(RoundedCornerShape(14.dp)), content = picture)
            T(title, 14, Bold, fg(f))
            T(subtitle, 12, color = sub(f))
        }
    }
}

@Composable
private fun Poster(image: String, title: String, seed: Int, onClick: () -> Unit) {
    val w = if (compact()) 112.dp else 134.dp
    FocusSurface(onClick, Modifier.size(w, w * 1.5f), shape = RoundedCornerShape(16.dp), lift = 1.08f) { _ ->
        Art(image, title, seed, Modifier.fillMaxSize(), 13)
    }
}
