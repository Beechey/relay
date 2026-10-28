package com.avetero.relay.feature.vod

import com.avetero.relay.ui.components.stopAtEnd
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import com.avetero.relay.ui.components.Pill
import com.avetero.relay.ui.components.PillBtn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Episode
import com.avetero.relay.data.model.PlayItem
import com.avetero.relay.data.model.Series
import com.avetero.relay.data.model.SeriesInfo
import com.avetero.relay.ui.components.Art
import com.avetero.relay.ui.components.Badge
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.components.EmptyState
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.IconBtn
import com.avetero.relay.ui.components.ProgressLine
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.Semi
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import com.avetero.relay.ui.theme.fg
import com.avetero.relay.ui.theme.sub
import com.avetero.relay.util.fmtDur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Board 05: details and episodes on one screen. */
@Composable
fun SeriesDetailScreen(app: AppState, s: Series) {
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s.id) {
        runCatching { withContext(Dispatchers.IO) { app.api.seriesInfo(s.id) } }.onSuccess { info = it }.onFailure { error = it.message }
    }
    val eps = info?.episodes.orEmpty()
    val seasons = eps.map { it.season }.distinct()
    val lastWatched = app.resumes.firstOrNull { it.seriesId == s.id }
    val resumeEp = eps.firstOrNull { "e${it.id}" == lastWatched?.key }
    var season by remember(info) { mutableIntStateOf(resumeEp?.season ?: seasons.firstOrNull() ?: 1) }
    val first = remember { FocusRequester() }
    LaunchedEffect(info) { if (info != null) runCatching { first.requestFocus() } }

    fun item(e: Episode) = PlayItem(app.api.episodeUrl(e), "E${e.num} · ${e.title}", "${s.name} · Season ${e.season}", e.image.ifEmpty { s.cover }, "e${e.id}", seriesId = s.id)
    fun play(e: Episode) {
        val i = eps.indexOf(e)
        app.push(Screen.VodPlayer(item(e), eps.drop(i + 1).take(20).map(::item), eps.map(::item)))
    }

    if (compact()) {
        LazyColumn(Modifier.fillMaxSize().background(C.Ground).statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBtn(Icons.Back, "Back", { app.back() }, size = 44.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Art(s.cover, s.name, s.id, Modifier.size(96.dp, 144.dp), showTitle = false)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            T(s.name, 22, Heavy, maxLines = 3, lineHeight = 26)
                            if (s.rating > 0) Badge("%.1f".format(s.rating), C.Accent, C.OnLight)
                            T(listOfNotNull(s.year.ifEmpty { null }, "${seasons.size} seasons".takeIf { seasons.isNotEmpty() }, info?.genre?.ifEmpty { null }).joinToString(" · "), 13, Semi, C.Soft, maxLines = 3)
                        }
                    }
                    val target = resumeEp ?: eps.firstOrNull()
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn(if (resumeEp != null) "Resume S${resumeEp.season} · E${resumeEp.num}" else "Play", { target?.let(::play) }, Modifier.weight(1f), style = BtnStyle.Primary, icon = Icons.Play, height = 50.dp, enabled = target != null)
                        val fav = app.isFav("s${s.id}")
                        IconBtn(if (fav) Icons.StarFilled else Icons.Star, "Favourite", { app.toggleFav("s${s.id}") }, size = 50.dp, tint = if (fav) C.Accent else C.Text)
                    }
                    T(info?.plot?.ifEmpty { null } ?: s.plot, 14, color = Color(0xFFC9CED8), maxLines = 8, lineHeight = 22)
                    when {
                        error != null -> T("Couldn't load episodes: $error", 14, Bold, C.Error, maxLines = 3)
                        info == null -> T("Loading episodes…", 14, color = C.Muted)
                        eps.isEmpty() -> T("No episodes listed for this series.", 14, color = C.Muted)
                    }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons) { n -> PillBtn("Season $n", n == season, { season = n }) }
                }
            }
            items(eps.filter { it.season == season }, key = { it.id }) { e ->
                val r = app.resumes.firstOrNull { it.key == "e${e.id}" }
                val watched = app.store.watched(app.account, "e${e.id}")
                FocusSurface({ play(e) }, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), shape = RoundedCornerShape(12.dp)) { f ->
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(112.dp, 63.dp)) {
                            Art(e.image.ifEmpty { s.cover }, "", e.id, Modifier.fillMaxSize(), showTitle = false)
                            val frac = if (watched) 1f else r?.let { it.pos / it.dur.coerceAtLeast(1).toFloat() } ?: 0f
                            if (frac > 0) ProgressLine(frac, Modifier.fillMaxWidth().align(Alignment.BottomStart), Color(0x66000000), height = 4.dp)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            T(listOfNotNull("E${e.num}", e.duration.ifEmpty { null }, "Watched".takeIf { watched }).joinToString(" · "), 12, Bold, sub(f))
                            T(e.title, 15, Heavy, fg(f), maxLines = 2)
                        }
                    }
                }
            }
        }
        return
    }

    val target = resumeEp ?: eps.firstOrNull()
    val inSeason = eps.filter { it.season == season }
    val upNext = resumeEp?.let { r -> eps.getOrNull(eps.indexOf(r) + 1) }
    val strip = rememberLazyListState()
    // the strip starts where you are
    LaunchedEffect(season, info) { inSeason.indexOf(resumeEp).takeIf { it > 0 }?.let { strip.scrollToItem((it - 1).coerceAtLeast(0)) } }
    DetailBackdrop(info?.cover?.ifEmpty { null } ?: s.cover, s.id) {
        Column(Modifier.padding(start = 64.dp, top = 104.dp).width(600.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FactPills(s.rating, listOf(s.year, if (seasons.size == 1) "1 season" else if (seasons.isNotEmpty()) "${seasons.size} seasons" else "", info?.genre?.split(',', '/')?.first()?.trim().orEmpty()))
            T(s.name, 52, Heavy, maxLines = 2, lineHeight = 52)
            T(info?.plot?.ifEmpty { null } ?: s.plot, 15, color = C.Soft, maxLines = 3, lineHeight = 23)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Btn(if (resumeEp != null) "Resume S${resumeEp.season} · E${resumeEp.num}" else "Play", { target?.let(::play) }, style = BtnStyle.Primary, icon = Icons.Play, height = 48.dp,
                    enabled = target != null, focusRequester = first)
                val fav = app.isFav("s${s.id}")
                IconBtn(if (fav) Icons.StarFilled else Icons.Star, if (fav) "Remove from favourites" else "Add to favourites", { app.toggleFav("s${s.id}") }, size = 48.dp, tint = if (fav) C.Accent else C.Text, round = true)
            }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                error != null -> T("Couldn't load episodes: $error", 14, Bold, C.Error, maxLines = 2, modifier = Modifier.padding(start = 64.dp))
                info == null -> T("Loading episodes…", 14, color = C.Muted, modifier = Modifier.padding(start = 64.dp))
                eps.isEmpty() -> T("Your provider hasn't listed any episodes for this series.", 14, color = C.Muted, modifier = Modifier.padding(start = 64.dp))
            }
            if (eps.isNotEmpty()) LazyRow(modifier = Modifier.stopAtEnd(), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                items(seasons) { n -> PillBtn("Season $n", n == season, { season = n }) }
                item {
                    val watched = inSeason.count { app.store.watched(app.account, "e${it.id}") }
                    T("${inSeason.size} episodes" + if (watched > 0) " · $watched watched" else "", 13, Semi, C.Muted, Modifier.padding(start = 6.dp))
                }
            }
            LazyRow(modifier = Modifier.stopAtEnd(), state = strip, horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(start = 64.dp, end = 40.dp, top = 6.dp, bottom = 6.dp)) {
                items(inSeason, key = { it.id }) { e ->
                    val r = app.resumes.firstOrNull { it.key == "e${e.id}" }
                    val watched = app.store.watched(app.account, "e${e.id}")
                    FocusSurface({ play(e) }, Modifier.width(250.dp), shape = RoundedCornerShape(18.dp), idle = Color.Transparent, lift = 1.06f) { f ->
                        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(14.dp))) {
                                Art(e.image.ifEmpty { s.cover }, "", e.id, Modifier.fillMaxSize(), showTitle = false)
                                if (e.duration.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x8C000000)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                                    T(shortDur(e.duration), 11, Heavy, Color.White)
                                }
                                if (watched) Pill("Watched", Modifier.align(Alignment.TopStart).padding(8.dp), bg = Color(0x8C000000), color = Color.White)
                            }
                            T("E${e.num} · ${e.title}", 14, Heavy, fg(f), Modifier.padding(horizontal = 4.dp))
                            when {
                                r != null && !watched -> ProgressLine(r.pos / r.dur.coerceAtLeast(1).toFloat(), Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), if (f) C.LightTrack else C.Glass3, if (f) C.OnLight else null)
                                else -> T(when { watched -> "Watched"; e == upNext -> "Up next"; else -> e.plot.ifEmpty { " " } }, 12, Semi, sub(f), Modifier.padding(horizontal = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
