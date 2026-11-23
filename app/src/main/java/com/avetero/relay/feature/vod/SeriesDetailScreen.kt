package com.avetero.relay.feature.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.avetero.relay.ui.m3.PagerTabs
import kotlin.math.roundToInt
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Episode
import com.avetero.relay.data.model.PlayItem
import com.avetero.relay.data.model.Resume
import com.avetero.relay.data.model.Series
import com.avetero.relay.data.model.SeriesInfo
import com.avetero.relay.ui.m3.Artwork
import com.avetero.relay.ui.m3.RelayTopBar
import com.avetero.relay.ui.m3.segmentShape
import com.avetero.relay.ui.motion.sharedBounds
import com.avetero.relay.ui.theme.isTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a series page needs, worked out once per load for both layouts. */
internal class SeriesDetail(val app: AppState, val s: Series, val info: SeriesInfo?, val error: String?, resumes: List<Resume>) {
    val eps = info?.episodes.orEmpty()
    val seasons = eps.map { it.season }.distinct()
    val progress: Map<String, Resume> = resumes.filter { it.seriesId == s.id }.associateBy { it.key }
    private val last = resumes.firstOrNull { it.seriesId == s.id }
    /** The episode you're in the middle of, or the one queued after the last you finished. */
    val resumeEp = eps.firstOrNull { "e${it.id}" == last?.key }
    val resumeAt = last?.takeIf { resumeEp != null }
    val upNext = resumeEp?.let { r -> eps.getOrNull(eps.indexOf(r) + 1) }
    val target = resumeEp ?: eps.firstOrNull()
    val watched: Set<Int> = eps.filter { app.store.watched(app.account, "e${it.id}") }.map { it.id }.toSet()
    val saved get() = app.isFav("s${s.id}")
    val playLabel = when {
        resumeEp == null -> "Play"
        (resumeAt?.pos ?: 0) > 0 -> "Resume S${resumeEp.season} · E${resumeEp.num}"
        else -> "Up next · S${resumeEp.season} E${resumeEp.num}"
    }
    val facts = listOf(s.year, if (seasons.size == 1) "1 season" else if (seasons.isNotEmpty()) "${seasons.size} seasons" else "",
        if (s.rating > 0) "★ %.1f".format(s.rating) else "").filter { it.isNotBlank() }
    val plot = info?.plot?.ifEmpty { null } ?: s.plot

    fun item(e: Episode) = PlayItem(app.api.episodeUrl(e), "E${e.num} · ${e.title}", "${s.name} · Season ${e.season}", e.image.ifEmpty { s.cover }, "e${e.id}", seriesId = s.id)
    fun play(e: Episode) {
        val i = eps.indexOf(e)
        app.push(Screen.VodPlayer(item(e), eps.drop(i + 1).take(20).map(::item), eps.map(::item)))
    }
    fun toggleSaved() = app.toggleFav("s${s.id}")
    fun fraction(e: Episode) = if (e.id in watched) 1f else progress["e${e.id}"]?.takeIf { it.pos > 0 }?.fraction() ?: 0f
    /** "Watched · 48 m", "Up next · 22 min left", "49 m". */
    fun meta(e: Episode): String {
        val r = progress["e${e.id}"]?.takeIf { it.pos > 0 }
        val dur = e.duration.takeIf { it.isNotBlank() }?.let(::shortDur)
        return when {
            e.id in watched -> listOfNotNull("Watched", dur).joinToString(" · ")
            r != null -> listOfNotNull("Up next".takeIf { e == resumeEp }, r.left()).joinToString(" · ")
            e == resumeEp || e == upNext && resumeEp?.id in watched -> listOfNotNull("Up next", dur).joinToString(" · ")
            else -> dur ?: e.plot.ifEmpty { " " }
        }
    }
}

@Composable
fun SeriesDetailScreen(app: AppState, series: Series) {
    var info by remember(series.id) { mutableStateOf<SeriesInfo?>(null) }
    var error by remember(series.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(series.id) {
        runCatching { withContext(Dispatchers.IO) { app.api.seriesInfo(series.id) } }.onSuccess { info = it }.onFailure { error = it.message ?: "network error" }
    }
    val d = remember(series, info, error, app.resumes) { SeriesDetail(app, series, info, error, app.resumes) }
    var season by remember(info) { mutableIntStateOf(d.resumeEp?.season ?: d.seasons.firstOrNull() ?: 1) }
    if (isTv()) TvSeriesDetail(d, season, { season = it }, remember(d, season) { d.eps.filter { it.season == season } }) else PhoneSeriesDetail(d, season) { season = it }
}

@Composable
private fun PhoneSeriesDetail(d: SeriesDetail, season: Int, setSeason: (Int) -> Unit) {
    val s = d.s
    val cs = MaterialTheme.colorScheme
    val collapse = remember { CollapseHeader() }
    Column(Modifier.fillMaxSize().background(cs.surface)) {
        RelayTopBar(s.name, { d.app.back() }, actions = {
            IconToggleButton(d.saved, { d.toggleSaved() }) {
                Icon(if (d.saved) Icons.Rounded.Star else Icons.Rounded.StarOutline, if (d.saved) "Remove from saved" else "Add to saved", tint = if (d.saved) cs.primary else cs.onSurfaceVariant)
            }
        })
        Column(Modifier.fillMaxSize().nestedScroll(collapse)) {
            // the header scrolls away as the episodes scroll up and comes back when they're at the top; the season tabs stay
            Column(Modifier.clipToBounds().layout { m, c ->
                val p = m.measure(c.copy(maxHeight = Constraints.Infinity))
                collapse.max = p.height
                val y = collapse.offset.roundToInt()
                layout(p.width, (p.height + y).coerceAtLeast(0)) { p.place(0, y) }
            }.padding(top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(s.cover, s.name, s.id, Modifier.size(72.dp, 108.dp).sharedBounds(posterKey(true, s.id), RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)), showTitle = s.cover.isBlank())
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(d.facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        if (d.plot.isNotBlank()) ExpandablePlot(d.plot, lines = 2)
                    }
                }
                Button({ d.target?.let(d::play) }, ButtonDefaults.shapes(), Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp), enabled = d.target != null) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(d.playLabel)
                }
                when {
                    d.error != null -> Text("Couldn't load episodes: ${d.error}", style = MaterialTheme.typography.bodyMedium, color = cs.error, modifier = Modifier.padding(horizontal = 20.dp))
                    d.info == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    d.eps.isEmpty() -> Text("No episodes listed for this series.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
            if (d.seasons.isNotEmpty()) Seasons(d, season, setSeason)
        }
    }
}

/** Season tabs over a pager of episode lists: swipe or tap between seasons; each list keeps its scroll. */
@Composable
private fun Seasons(d: SeriesDetail, season: Int, setSeason: (Int) -> Unit) {
    val s = d.s
    val cs = MaterialTheme.colorScheme
    val pager = rememberPagerState(d.seasons.indexOf(season).coerceAtLeast(0)) { d.seasons.size }
    LaunchedEffect(pager, d.seasons) { snapshotFlow { pager.currentPage }.collect { d.seasons.getOrNull(it)?.let(setSeason) } }
    PagerTabs(pager, d.seasons.map { "Season $it" }, scrollable = true)
    HorizontalPager(pager, Modifier.fillMaxSize(), key = { d.seasons[it] }, verticalAlignment = Alignment.Top) { page ->
        val inSeason = remember(d, page) { d.eps.filter { it.season == d.seasons[page] } }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item(key = "count") {
                Text(if (inSeason.size == 1) "1 episode" else "${inSeason.size} episodes", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
            }
            itemsIndexed(inSeason, key = { _, e -> e.id }) { i, e ->
                val next = e == d.resumeEp
                Row(Modifier.animateItem().padding(horizontal = 16.dp).fillMaxWidth().height(76.dp).clip(segmentShape(i, inSeason.size))
                    .background(if (next) cs.secondaryContainer else cs.surfaceContainer).clickable { d.play(e) }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(104.dp, 58.dp).clip(RoundedCornerShape(12.dp))) {
                        Artwork(e.image.ifEmpty { s.cover }, "", e.id, Modifier.fillMaxSize(), showTitle = false)
                        val f = d.fraction(e)
                        if (f > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(f).height(4.dp).background(cs.primary))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("E${e.num} · ${e.title}", style = MaterialTheme.typography.titleMedium, color = if (next) cs.onSecondaryContainer else cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(d.meta(e), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Lets a header scroll away ahead of the list under it (scrolling up) and return once the list is back at its top. */
private class CollapseHeader : NestedScrollConnection {
    var max = 0
    var offset by mutableFloatStateOf(0f)
    override fun onPreScroll(available: Offset, source: NestedScrollSource) = take(available.y.coerceAtMost(0f))
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = take(available.y.coerceAtLeast(0f))
    private fun take(dy: Float): Offset {
        val o = (offset + dy).coerceIn(-max.toFloat(), 0f)
        val used = o - offset
        offset = o
        return Offset(0f, used)
    }
}
