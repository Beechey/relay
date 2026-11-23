package com.avetero.relay.feature.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.PlayItem
import com.avetero.relay.data.model.Resume
import com.avetero.relay.data.model.Vod
import com.avetero.relay.data.model.VodInfo
import com.avetero.relay.ui.m3.Artwork
import com.avetero.relay.ui.m3.segmentShape
import com.avetero.relay.ui.motion.sharedBounds
import com.avetero.relay.ui.theme.isTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a movie's page needs, worked out once for both layouts. */
internal class MovieDetail(val app: AppState, val vod: Vod, val info: VodInfo?) {
    val key = "m${vod.id}"
    val resume get() = app.resumes.firstOrNull { it.key == key }
    val saved get() = app.isFav(key)
    val item = PlayItem(app.api.vodUrl(vod), vod.name, listOfNotNull(info?.year?.ifEmpty { null }, info?.genre?.ifEmpty { null }).joinToString(" · ").ifEmpty { "Movie" }, vod.icon, key, vodId = vod.id)
    val facts = listOf(info?.year.orEmpty(), info?.duration?.takeIf { it.isNotBlank() }?.let(::shortDur).orEmpty(),
        info?.genre?.split(',', '/')?.first()?.trim().orEmpty(), if (vod.rating > 0) "★ %.1f".format(vod.rating) else "").filter { it.isNotBlank() }
    fun play() = app.push(Screen.VodPlayer(item))
    fun fromStart() { app.saveResume(Resume(key, vod.name, "", vod.icon, 0, 1, vod.id)); app.push(Screen.VodPlayer(item)) }
    fun toggleSaved() = app.toggleFav(key)
    /** Another movie replaces this page, so Back still goes to where you came from. */
    fun open(m: Vod) { app.stack[app.stack.lastIndex] = Screen.MovieDetail(m) }
}

@Composable
fun MovieDetailScreen(app: AppState, vod: Vod) {
    var info by remember(vod.id) { mutableStateOf<VodInfo?>(null) }
    LaunchedEffect(vod.id) { info = runCatching { withContext(Dispatchers.IO) { app.api.vodInfo(vod.id) } }.getOrNull() }
    val d = remember(vod, info) { MovieDetail(app, vod, info) }
    val more = remember(vod, app.moviesVisible) { app.moviesVisible.filter { it.categoryId == vod.categoryId && it.id != vod.id }.take(12) }
    if (isTv()) TvMovieDetail(d, more) else PhoneMovieDetail(d, more)
}

@Composable
private fun PhoneMovieDetail(d: MovieDetail, more: List<Vod>) {
    val vod = d.vod
    val info = d.info
    val resume = d.resume
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.surface)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                Artwork(info?.backdrop?.ifEmpty { null } ?: vod.icon, vod.name, vod.id, Modifier.fillMaxSize(), showTitle = false)
                // the poster you tapped grows into this one (same 2:3 artwork, already loaded)
                Artwork(vod.icon, vod.name, vod.id, Modifier.align(Alignment.BottomStart).offset(x = 20.dp, y = 40.dp).size(104.dp, 156.dp)
                    .sharedBounds(posterKey(false, vod.id), RoundedCornerShape(16.dp)).shadow(12.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)), showTitle = vod.icon.isBlank())
            }
            FlowRow(Modifier.padding(start = 140.dp, end = 20.dp, top = 12.dp).heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d.facts.forEach { PhoneFact(it) }
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(vod.name, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium))
                Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Button(d::play, ButtonShapes(RoundedCornerShape(28.dp, 8.dp, 8.dp, 28.dp), RoundedCornerShape(16.dp)), Modifier.weight(2f).fillMaxHeight()) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (resume != null) "Resume · ${resume.left()}" else "Play", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (resume != null) FilledTonalButton(d::fromStart, ButtonShapes(RoundedCornerShape(8.dp), RoundedCornerShape(16.dp)), Modifier.weight(1f).fillMaxHeight()) {
                        Text("From start", maxLines = 1)
                    }
                    SaveToggle(d.saved, d::toggleSaved, Modifier.width(64.dp).fillMaxHeight())
                }
                if (resume != null) LinearProgressIndicator({ resume.fraction() }, Modifier.fillMaxWidth().offset(y = (-8).dp), drawStopIndicator = {})
                info?.plot?.takeIf { it.isNotBlank() }?.let { ExpandablePlot(it) }
                val people = listOfNotNull(info?.director?.takeIf { it.isNotBlank() }?.let { "Director" to it }, info?.cast?.takeIf { it.isNotBlank() }?.let { "Cast" to it })
                if (people.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    people.forEachIndexed { i, (label, value) ->
                        Row(Modifier.fillMaxWidth().clip(segmentShape(i, people.size)).background(cs.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("$label  ", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (more.isNotEmpty()) {
                Text("More like this", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp))
                HorizontalMultiBrowseCarousel(rememberCarouselState { more.size }, 150.dp, Modifier.fillMaxWidth().height(200.dp), itemSpacing = 8.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp)) { i ->
                    val m = more[i]
                    Artwork(m.icon, m.name, m.id, Modifier.fillMaxSize().maskClip(MaterialTheme.shapes.large).clickable { d.open(m) })
                }
            }
            Spacer(Modifier.height(32.dp))
        }
        IconButton({ d.app.back() }, Modifier.statusBarsPadding().padding(8.dp).size(48.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = cs.surface.copy(alpha = .6f), contentColor = cs.onSurface)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
        }
    }
}

/** Phone: a fact (year, length, genre, rating) as a small tonal chip. */
@Composable
internal fun PhoneFact(text: String) =
    Box(Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

/** The Saved toggle at the end of a joined button group; the star bounces as it fills. */
@Composable
internal fun SaveToggle(saved: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) =
    FilledTonalIconToggleButton(saved, { onToggle() }, modifier, shape = RoundedCornerShape(8.dp, 28.dp, 28.dp, 8.dp)) {
        Icon(if (saved) Icons.Rounded.Star else Icons.Rounded.StarOutline, if (saved) "Remove from saved" else "Add to saved")
    }

/** Plot: four lines, tap for the rest. */
@Composable
internal fun ExpandablePlot(plot: String, lines: Int = 4) {
    var open by rememberSaveable(plot) { mutableStateOf(false) }
    Text(plot, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (open) Int.MAX_VALUE else lines,
        overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { open = !open })
}
