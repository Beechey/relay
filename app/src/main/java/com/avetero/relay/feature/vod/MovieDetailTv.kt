package com.avetero.relay.feature.vod

import com.avetero.relay.ui.tv.TvPivot
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.avetero.relay.data.model.Vod
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.components.stopAtEnd
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvHints
import com.avetero.relay.ui.tv.TvSafe
import com.avetero.relay.util.fmtDur

/** TvMovieDetail board: artwork top right, the story and Play on the left, More like this along the bottom. */
@Composable
internal fun TvMovieDetail(d: MovieDetail, more: List<Vod>) {
    val vod = d.vod
    val info = d.info
    val resume = d.resume
    val first = remember { FocusRequester() }
    LaunchedEffect(vod.id) { first.settle() }
    val category = remember(vod.categoryId, d.app.vodCats) { d.app.vodCats.firstOrNull { it.id == vod.categoryId }?.name }
    val start = 40.dp

    TvBackdrop(info?.backdrop?.ifEmpty { null } ?: vod.icon, vod.id, posterKey(false, vod.id), 470.dp, 432.dp) {
        Column(Modifier.padding(start = start, top = 56.dp).width(560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(listOfNotNull("MOVIE", category?.uppercase()).joinToString(" · "), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Tok.Primary)
            Text(vod.name, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 2)
            TvFacts(d.facts)
            info?.plot?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Tok.OnSurfaceVariant, maxLines = 3) }
            val people = listOfNotNull(info?.director?.takeIf { it.isNotBlank() }?.let { "Director  $it" }, info?.cast?.takeIf { it.isNotBlank() }?.let { "With $it" })
            if (people.isNotEmpty()) Text(people.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Tok.OnSurfaceVariant, maxLines = 1)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TvButton(if (resume != null) "Resume ${fmtDur(resume.pos)}" else "Play", d::play, primary = true, focusRequester = first,
                    leading = { Icon(Icons.Rounded.PlayArrow, null) })
                if (resume != null) TvButton("From the start", d::fromStart)
                TvSaveButton(d.saved, d::toggleSaved)
            }
            if (resume != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(300.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Tok.ContainerHighest)) {
                    Box(Modifier.fillMaxWidth(resume.fraction()).height(4.dp).background(Tok.Primary))
                }
                Text(resume.left(), style = MaterialTheme.typography.labelLarge, color = Tok.OnSurfaceVariant)
            }
        }
        if (more.isNotEmpty()) Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("More like this", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = start))
            TvPivot {
                LazyRow(Modifier.stopAtEnd(), contentPadding = PaddingValues(start = start, end = TvSafe.Horizontal, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(more, key = { it.id }) { m -> TvPoster(m.icon, m.name, m.id, posterKey(false, m.id), { d.open(m) }, width = 96.dp, caption = false) }
                }
            }
        }
        TvHints("Back" to "Movies", "▼" to "More like this", modifier = Modifier.align(Alignment.BottomEnd).padding(end = TvSafe.Horizontal, bottom = TvSafe.Vertical))
    }
}
