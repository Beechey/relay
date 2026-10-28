package com.avetero.relay.feature.vod

import com.avetero.relay.ui.components.stopAtEnd
import com.avetero.relay.ui.components.settle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.PaddingValues
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.PlayItem
import com.avetero.relay.data.model.Resume
import com.avetero.relay.data.model.Vod
import com.avetero.relay.data.model.VodInfo
import com.avetero.relay.ui.components.Art
import com.avetero.relay.ui.components.Badge
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.IconBtn
import com.avetero.relay.ui.components.PosterTile
import com.avetero.relay.ui.components.ProgressLine
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.Semi
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import com.avetero.relay.util.fmtDur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Board 05's layout for movies, with More like this in place of episodes. */
@Composable
fun MovieDetailScreen(app: AppState, vod: Vod) {
    var info by remember { mutableStateOf<VodInfo?>(null) }
    LaunchedEffect(vod.id) { info = runCatching { withContext(Dispatchers.IO) { app.api.vodInfo(vod.id) } }.getOrNull() }
    val key = "m${vod.id}"
    val resumeAt = app.resumes.firstOrNull { it.key == key }?.pos ?: 0
    val item = PlayItem(app.api.vodUrl(vod), vod.name, listOfNotNull(info?.year?.ifEmpty { null }, info?.genre?.ifEmpty { null }).joinToString(" · ").ifEmpty { "Movie" }, vod.icon, key, vodId = vod.id)
    val more = remember(vod) { app.moviesVisible.filter { it.categoryId == vod.categoryId && it.id != vod.id }.take(12) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.settle() }

    if (compact()) {
        Column(Modifier.fillMaxSize().background(C.Ground).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                Art(info?.backdrop?.ifEmpty { null } ?: vod.icon, "", vod.id, Modifier.fillMaxSize(), showTitle = false)
                IconBtn(Icons.Back, "Back", { app.back() }, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp), size = 44.dp)
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Art(vod.icon, vod.name, vod.id, Modifier.size(96.dp, 144.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        T(vod.name, 22, Heavy, maxLines = 3, lineHeight = 26)
                        if (vod.rating > 0) Badge("%.1f".format(vod.rating), C.Accent, C.OnLight)
                        T(listOfNotNull(info?.year, info?.duration, info?.genre).filter { it.isNotEmpty() }.joinToString(" · "), 13, Semi, C.Soft, maxLines = 3)
                    }
                }
                Btn(if (resumeAt > 0) "Resume ${fmtDur(resumeAt)}" else "Play", { app.push(Screen.VodPlayer(item)) }, Modifier.fillMaxWidth(), style = BtnStyle.Primary, icon = Icons.Play, height = 52.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (resumeAt > 0) Btn("From start", { app.saveResume(Resume(key, vod.name, "", vod.icon, 0, 1, vod.id)); app.push(Screen.VodPlayer(item)) }, Modifier.weight(1f), height = 48.dp)
                    val fav = app.isFav(key)
                    Btn(if (fav) "Favourited" else "Favourite", { app.toggleFav(key) }, Modifier.weight(1f), icon = if (fav) Icons.StarFilled else Icons.Star, height = 48.dp)
                }
                if (resumeAt > 0) ProgressLine(app.resumes.first { it.key == key }.let { it.pos / it.dur.coerceAtLeast(1).toFloat() }, Modifier.fillMaxWidth(), height = 4.dp)
                T(info?.plot ?: "", 15, color = Color(0xFFC9CED8), maxLines = 12, lineHeight = 23)
                if (!info?.director.isNullOrEmpty()) T("Director  ${info!!.director}", 13, color = C.Muted, maxLines = 2)
                if (!info?.cast.isNullOrEmpty()) T("Cast  ${info!!.cast}", 13, color = C.Muted, maxLines = 4)
                if (more.isNotEmpty()) {
                    T("More like this", 17, Heavy)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(more, key = { it.id }) { m -> PosterTile(m.icon, m.name, m.id, 100.dp) { app.stack[app.stack.lastIndex] = Screen.MovieDetail(m) } }
                    }
                }
            }
        }
        return
    }

    val resume = app.resumes.firstOrNull { it.key == key }
    DetailBackdrop(info?.backdrop?.ifEmpty { null } ?: vod.icon, vod.id) {
        Column(Modifier.padding(start = 64.dp, top = 104.dp).width(600.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FactPills(vod.rating, listOf(info?.year.orEmpty(), info?.duration?.let(::shortDur).orEmpty(), info?.genre?.split(',', '/')?.first()?.trim().orEmpty()))
            T(vod.name, 52, Heavy, maxLines = 2, lineHeight = 52)
            T(info?.plot ?: "", 15, color = C.Soft, maxLines = 3, lineHeight = 23)
            if (!info?.cast.isNullOrEmpty()) T("With ${info!!.cast}", 13, color = C.Muted, maxLines = 1)
            if (resume != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProgressLine(resume.pos / resume.dur.coerceAtLeast(1).toFloat(), Modifier.width(300.dp), C.Glass3)
                T(fmtDur(resume.dur - resume.pos) + " left", 13, Semi, C.Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Btn(if (resumeAt > 0) "Resume" else "Play", { app.push(Screen.VodPlayer(item)) }, style = BtnStyle.Primary, icon = Icons.Play, height = 48.dp, focusRequester = first)
                if (resumeAt > 0) Btn("From the start", { app.saveResume(Resume(key, vod.name, "", vod.icon, 0, 1, vod.id)); app.push(Screen.VodPlayer(item)) }, height = 48.dp)
                val fav = app.isFav(key)
                IconBtn(if (fav) Icons.StarFilled else Icons.Star, if (fav) "Remove from favourites" else "Add to favourites", { app.toggleFav(key) }, size = 48.dp, tint = if (fav) C.Accent else C.Text, round = true)
            }
        }
        if (more.isNotEmpty()) Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("More like this", 19, Heavy, modifier = Modifier.padding(start = 64.dp))
            LazyRow(modifier = Modifier.stopAtEnd(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(start = 64.dp, end = 40.dp, top = 8.dp, bottom = 8.dp)) {
                items(more, key = { it.id }) { m ->
                    FocusSurface({ app.stack[app.stack.lastIndex] = Screen.MovieDetail(m) }, Modifier.size(120.dp, 180.dp), shape = RoundedCornerShape(16.dp), lift = 1.08f) { _ ->
                        Art(m.icon, m.name, m.id, Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                    }
                }
            }
        }
    }
}
