package com.avetero.relay.feature.favourites

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.current
import com.avetero.relay.data.model.progress
import com.avetero.relay.feature.vod.TvEmpty
import com.avetero.relay.ui.tv.TvPivot
import com.avetero.relay.feature.vod.TvPoster
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.m3.ChannelLogo
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvHints
import com.avetero.relay.ui.tv.TvSafe
import com.avetero.relay.ui.tv.TvSurface

/** TvSaved board: Channels / Movies / Series tabs, channels as cards, titles as posters. Hold OK removes (with Undo). */
@Composable
internal fun TvSaved(app: AppState) {
    val saved = rememberSaved(app)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val firstTab = remember { FocusRequester() }
    val firstItem = remember { FocusRequester() }
    LaunchedEffect(Unit) { (if (saved.count(tab) > 0) firstItem else firstTab).settle() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = TvSafe.Horizontal, end = TvSafe.Horizontal, top = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Saved", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold))
                Row(Modifier.clip(RoundedCornerShape(26.dp)).background(Tok.Container).padding(4.dp).focusRestorer(firstTab), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    saved.labels.forEachIndexed { i, label ->
                        TvSurface({ tab = i }, Modifier.height(44.dp), selected = tab == i, shape = RoundedCornerShape(22.dp), focusRequester = if (i == 0) firstTab else null) {
                            Text("$label · ${saved.count(i)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp))
                        }
                    }
                }
            }
            if (saved.count(tab) == 0) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TvEmpty(if (tab == 0) "No saved channels yet" else if (tab == 1) "No saved movies" else "No saved series", "Hold OK on any channel, movie or series to add it here.")
                if (tab == 0) TvButton("Browse live TV", { app.go(Screen.Live()) }, primary = true)
            }
            else TvPivot {
                LazyVerticalGrid(if (tab == 0) GridCells.Fixed(3) else GridCells.Fixed(7), Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (tab) {
                        0 -> items(saved.chans, key = { it.id }) { c -> TvSavedChannel(app, c, saved.chans, if (c == saved.chans.first()) firstItem else null) }
                        1 -> items(saved.movies, key = { it.id }) { m ->
                            TvPoster(m.icon, m.name, m.id, "poster-m${m.id}", { app.push(Screen.MovieDetail(m)) }, onLongClick = { unsave(app, "m${m.id}", m.name) },
                                focusRequester = if (m == saved.movies.first()) firstItem else null)
                        }
                        else -> items(saved.series, key = { it.id }) { s ->
                            TvPoster(s.cover, s.name, s.id, "poster-s${s.id}", { app.push(Screen.SeriesDetail(s)) }, onLongClick = { unsave(app, "s${s.id}", s.name) },
                                focusRequester = if (s == saved.series.first()) firstItem else null)
                        }
                    }
                }
            }
        }
        TvHints("OK" to "Watch", "Hold OK" to "Remove", modifier = Modifier.align(Alignment.BottomEnd).padding(end = TvSafe.Horizontal, bottom = TvSafe.Vertical))
    }
}

@Composable
private fun TvSavedChannel(app: AppState, c: Channel, all: List<Channel>, focusRequester: FocusRequester?) {
    LaunchedEffect(c.id) { app.loadEpg(c) }
    val now = app.programmesFor(c).current()
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    TvSurface({ app.push(Screen.LivePlayer(c, all)) }, Modifier.fillMaxWidth().height(96.dp), onLongClick = { unsave(app, "c${c.id}", c.name) },
        shape = RoundedCornerShape(20.dp), idle = Tok.Container, focusRequester = focusRequester, interactionSource = source) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChannelLogo(c.icon, c.name, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (c.num > 0) "${c.num} · ${c.name}" else c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(now?.title ?: "No programme info", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (focused) Tok.InverseOnSurface else Tok.OnSurfaceVariant)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (focused) Tok.OnSurfaceVariant else Tok.ContainerHighest)) {
                    Box(Modifier.fillMaxWidth(now?.progress() ?: 0f).height(4.dp).background(if (focused) Tok.InverseOnSurface else Tok.Primary))
                }
            }
        }
    }
}
