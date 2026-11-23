package com.avetero.relay.feature.vod

import com.avetero.relay.ui.tv.TvPivot
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.avetero.relay.data.model.Episode
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.components.stopAtEnd
import com.avetero.relay.ui.m3.Artwork
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvHints
import com.avetero.relay.ui.tv.TvLift
import com.avetero.relay.ui.tv.TvSafe
import com.avetero.relay.ui.tv.TvSurface

/** TvSeriesDetail board: details and Resume up top, season tabs and the episode strip below. */
@Composable
internal fun TvSeriesDetail(d: SeriesDetail, season: Int, setSeason: (Int) -> Unit, inSeason: List<Episode>) {
    val s = d.s
    val first = remember { FocusRequester() }
    LaunchedEffect(d.info != null) { if (d.info != null) first.settle() }
    val strip = rememberLazyListState()
    // the strip starts where you are
    LaunchedEffect(season, d.info) { inSeason.indexOf(d.resumeEp).takeIf { it > 0 }?.let { strip.scrollToItem((it - 1).coerceAtLeast(0)) } }
    val start = 40.dp

    TvBackdrop(d.info?.cover?.ifEmpty { null } ?: s.cover, s.id, posterKey(true, s.id), 330.dp, 532.dp) {
        Column(Modifier.padding(start = start, top = 48.dp).width(620.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text((listOf("SERIES") + d.facts.drop(if (s.year.isNotBlank()) 1 else 0).map { it.uppercase() }).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Tok.Primary)
            Text(s.name, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 2)
            if (d.plot.isNotBlank()) Text(d.plot, style = MaterialTheme.typography.bodyLarge, color = Tok.OnSurfaceVariant, maxLines = 2)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TvButton(d.playLabel, { d.target?.let(d::play) }, primary = true, focusRequester = first, leading = { Icon(Icons.Rounded.PlayArrow, null) })
                TvSaveButton(d.saved, d::toggleSaved, 52.dp)
            }
        }
        Column(Modifier.padding(top = 344.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                d.error != null -> Text("Couldn't load episodes: ${d.error}", style = MaterialTheme.typography.bodyLarge, color = Tok.Error, modifier = Modifier.padding(start = start))
                d.info == null -> Text("Loading episodes…", style = MaterialTheme.typography.bodyLarge, color = Tok.OnSurfaceVariant, modifier = Modifier.padding(start = start))
                d.eps.isEmpty() -> Text("Your provider hasn't listed any episodes for this series.", style = MaterialTheme.typography.bodyLarge, color = Tok.OnSurfaceVariant, modifier = Modifier.padding(start = start))
                else -> LazyRow(Modifier.stopAtEnd(), contentPadding = PaddingValues(horizontal = start), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    items(d.seasons) { n ->
                        TvSurface({ setSeason(n) }, Modifier.height(40.dp), selected = n == season, shape = RoundedCornerShape(20.dp)) {
                            Text("Season $n", style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp))
                        }
                    }
                    item {
                        val watched = inSeason.count { it.id in d.watched }
                        Text("${inSeason.size} episodes" + if (watched > 0) " · $watched watched" else "", style = MaterialTheme.typography.labelLarge, color = Tok.OnSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            TvPivot {
                LazyRow(Modifier.stopAtEnd(), strip, contentPadding = PaddingValues(start = start, end = TvSafe.Horizontal, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(inSeason, key = { it.id }) { e -> TvEpisode(d, e) }
                }
            }
        }
        TvHints("▲" to "Seasons", "OK" to "Play", "Back" to "Series", modifier = Modifier.align(Alignment.BottomEnd).padding(end = TvSafe.Horizontal, bottom = TvSafe.Vertical))
    }
}

@Composable
private fun TvEpisode(d: SeriesDetail, e: Episode) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val tag = when { e.id in d.watched -> "Watched"; e == d.resumeEp || (e == d.upNext && d.resumeEp?.id in d.watched) -> "Up next"; else -> null }
    Column(Modifier.width(232.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSurface({ d.play(e) }, Modifier.fillMaxWidth().height(132.dp), lift = TvLift.Card, interactionSource = source) {
            Artwork(e.image.ifEmpty { d.s.cover }, "", e.id, Modifier.fillMaxSize(), showTitle = false)
            if (tag != null) Box(Modifier.padding(10.dp).clip(RoundedCornerShape(8.dp)).background(if (tag == "Up next") Tok.Primary else Tok.Surface.copy(alpha = .8f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(tag, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = if (tag == "Up next") Tok.OnPrimary else Tok.OnSurface)
            }
            val f = d.fraction(e)
            if (f > 0f) Box(Modifier.align(Alignment.BottomStart).padding(10.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = .25f))) {
                Box(Modifier.fillMaxWidth(f).height(4.dp).background(Tok.Primary))
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (focused) Tok.InverseSurface else Color.Transparent).padding(horizontal = 10.dp, vertical = 6.dp)) {
            val fg = if (focused) Tok.InverseOnSurface else Tok.OnSurface
            Text("E${e.num} · ${e.title}", style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(d.meta(e), style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = .85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
