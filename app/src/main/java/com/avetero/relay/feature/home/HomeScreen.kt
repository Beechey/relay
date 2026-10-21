package com.avetero.relay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.current
import com.avetero.relay.ui.components.Art
import com.avetero.relay.ui.components.Clock
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.Logo
import com.avetero.relay.ui.components.ProgressLine
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.components.WithRail
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Ico
import com.avetero.relay.ui.theme.Semi
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import com.avetero.relay.ui.theme.fg
import com.avetero.relay.ui.theme.sub
import com.avetero.relay.util.fmtDate
import com.avetero.relay.util.fmtDur
import java.util.Calendar

/** Board 03. */
@Composable
fun HomeScreen(app: AppState, nav: (Tab) -> Unit) = WithRail(Tab.Home, nav) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val recent = app.channelsIn(AppState.RECENT)
    val recentMovies = remember(app.recentMovies, app.movies) { app.recentMovies.mapNotNull { id -> app.movies.firstOrNull { it.id == id } } }
    val recentSeries = remember(app.recentSeries, app.series) { app.recentSeries.mapNotNull { id -> app.series.firstOrNull { it.id == id } } }
    val phone = compact()
    Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (phone) 16.dp else 40.dp, vertical = if (phone) 16.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(if (phone) 20.dp else 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(listOfNotNull(app.account?.name, app.info?.expires?.let { "expires " + fmtDate(it) }).joinToString(" · "), 13, Semi, C.Muted)
                val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                T(if (app.libraryLoading) "Loading your library…" else when (h) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }, 30, Heavy)
            }
            if (!phone) Clock(22)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 20.dp)) {
            listOf(
                Triple(Tab.Live, "%,d channels".format(app.channelsVisible.size), Screen.Live()),
                Triple(Tab.Movies, "%,d titles".format(app.moviesVisible.size), Screen.VodGrid(false)),
                Triple(Tab.Series, "%,d shows".format(app.seriesVisible.size), Screen.VodGrid(true)),
            ).forEachIndexed { i, (tab, count, screen) ->
                FocusSurface({ app.go(screen) }, Modifier.weight(1f).height(if (phone) 112.dp else 150.dp), idle = C.Surface, shape = RoundedCornerShape(18.dp),
                    focusRequester = if (i == 0) first else null) { f ->
                    Column(Modifier.fillMaxSize().padding(if (phone) 14.dp else 22.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Ico(tab.icon, if (f) C.OnLight else C.Accent, if (phone) 24.dp else 30.dp)
                        if (phone) Column {
                            T(tab.label, 17, Heavy, fg(f)); T(count, 12, Semi, sub(f))
                        } else Row(verticalAlignment = Alignment.Bottom) {
                            T(tab.label, 24, Heavy, fg(f), Modifier.weight(1f))
                            T(count, 14, Semi, sub(f))
                        }
                    }
                }
            }
        }
        if (app.resumes.isNotEmpty()) HomeRow("Continue watching", if (phone) "Press and hold to remove" else "Hold OK to remove") {
            items(app.resumes, key = { it.key }) { r ->
                HomeCard(r.title, r.meta + if (r.pos > 0 && r.dur > r.pos) " · ${fmtDur(r.dur - r.pos)} left" else "", {
                    val vod = app.movies.firstOrNull { it.id == r.vodId }
                    val ser = app.series.firstOrNull { it.id == r.seriesId }
                    when {
                        vod != null -> app.push(Screen.MovieDetail(vod))
                        ser != null -> app.push(Screen.SeriesDetail(ser))
                    }
                }, onLongClick = { app.removeResume(r.key) }) {
                    Art(r.image, r.title, r.key.hashCode(), Modifier.fillMaxSize(), showTitle = false)
                    ProgressLine(r.pos / r.dur.coerceAtLeast(1).toFloat(), Modifier.fillMaxWidth().align(Alignment.BottomStart), height = 4.dp)
                }
            }
        }
        if (recent.isNotEmpty()) HomeRow("Recent channels") {
            items(recent, key = { it.id }) { c ->
                LaunchedEffect(c.id) { app.loadEpg(c) }
                HomeCard(c.name, app.programmesFor(c).current()?.title ?: "Live", { app.push(Screen.LivePlayer(c, recent)) }) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)).background(C.Control), contentAlignment = Alignment.Center) {
                        Logo(c.icon, c.name, false, Modifier.size(if (compact()) 110.dp else 136.dp, if (compact()) 72.dp else 90.dp))
                    }
                }
            }
        }
        if (recentMovies.isNotEmpty()) HomeRow("Recently watched movies") {
            items(recentMovies, key = { it.id }) { m ->
                HomeCard(m.name, if (m.rating > 0) "★ %.1f".format(m.rating) else "Movie", { app.push(Screen.MovieDetail(m)) }) {
                    Art(m.icon, m.name, m.id, Modifier.fillMaxSize(), showTitle = false)
                }
            }
        }
        if (recentSeries.isNotEmpty()) HomeRow("Recently watched series") {
            items(recentSeries, key = { it.id }) { s ->
                HomeCard(s.name, listOfNotNull(s.year.ifEmpty { null }, if (s.rating > 0) "★ %.1f".format(s.rating) else null).joinToString(" · ").ifEmpty { "Series" }, { app.push(Screen.SeriesDetail(s)) }) {
                    Art(s.cover, s.name, s.id, Modifier.fillMaxSize(), showTitle = false)
                }
            }
        }
    }
}

/** A titled, horizontally scrolling row on Home. */
@Composable
private fun HomeRow(title: String, hint: String? = null, content: LazyListScope.() -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            T(title, 18, Heavy, modifier = Modifier.weight(1f))
            if (hint != null) T(hint, 12, color = C.Dim)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }

/** Every Home card has the same shape: a 16:9 picture, a title and one line under it. */
@Composable
private fun HomeCard(title: String, subtitle: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, picture: @Composable BoxScope.() -> Unit) {
    val phone = compact()
    FocusSurface(onClick, Modifier.width(if (phone) 220.dp else 272.dp), onLongClick = onLongClick) { f ->
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(if (phone) 118.dp else 146.dp), content = picture)
            T(title, 15, Bold, fg(f))
            T(subtitle, 13, color = sub(f))
        }
    }
}
