package com.avetero.relay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.avetero.relay.AppState
import com.avetero.relay.Screen
import com.avetero.relay.fmtDate
import java.util.Calendar

/** Board 03. */
@Composable
fun HomeScreen(app: AppState, nav: (Tab) -> Unit) = WithRail(Tab.Home, nav) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val recent = app.store.recentChannels(app.account).mapNotNull { id -> app.channels.firstOrNull { it.id == id } }
    Column(Modifier.weight(1f).fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(listOfNotNull(app.account?.name, app.info?.expires?.let { "expires " + fmtDate(it) }).joinToString(" · "), 13, Semi, C.Muted)
                val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                T(when (h) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }, 30, Heavy)
            }
            Clock(22)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf(
                Triple(Tab.Live, "%,d channels".format(app.channels.size), Screen.Live),
                Triple(Tab.Movies, "%,d titles".format(app.movies.size), Screen.VodGrid(false)),
                Triple(Tab.Series, "%,d shows".format(app.series.size), Screen.VodGrid(true)),
            ).forEachIndexed { i, (tab, count, screen) ->
                FocusSurface({ app.go(screen) }, Modifier.weight(1f).height(150.dp), idle = C.Surface, shape = RoundedCornerShape(18.dp),
                    focusRequester = if (i == 0) first else null) { f ->
                    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Ico(tab.icon, if (f) C.OnLight else C.Accent, 30.dp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            T(tab.label, 24, Heavy, fg(f), Modifier.weight(1f))
                            T(count, 14, Semi, sub(f))
                        }
                    }
                }
            }
        }
        if (app.resumes.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            T("Continue watching", 18, Heavy)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(app.resumes, key = { it.key }) { r ->
                    FocusSurface({
                        val vod = app.movies.firstOrNull { it.id == r.vodId }
                        val ser = app.series.firstOrNull { it.id == r.seriesId }
                        when {
                            vod != null -> app.push(Screen.MovieDetail(vod))
                            ser != null -> app.push(Screen.SeriesDetail(ser))
                        }
                    }, Modifier.width(272.dp)) { f ->
                        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.fillMaxWidth().height(146.dp)) {
                                Art(r.image, r.title, r.key.hashCode(), Modifier.fillMaxSize(), showTitle = false)
                                ProgressLine(r.pos / r.dur.coerceAtLeast(1).toFloat(), Modifier.fillMaxWidth().align(Alignment.BottomStart), height = 4.dp)
                            }
                            T(r.title, 15, Bold, fg(f))
                            T(r.meta, 13, color = sub(f))
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            T("Recent channels", 18, Heavy)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recent, key = { it.id }) { c ->
                    FocusSurface({ app.push(Screen.LivePlayer(c, recent)) }, Modifier.width(170.dp).height(64.dp), idle = C.Surface) { f ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Logo(c.icon, c.name, f, Modifier.size(40.dp, 30.dp))
                            T(c.name, 14, Bold, fg(f))
                        }
                    }
                }
            }
        }
    }
}
