package com.avetero.relay.feature.vod

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

/** Board 08. */
@Composable
fun MovieDetailScreen(app: AppState, vod: Vod) {
    var info by remember { mutableStateOf<VodInfo?>(null) }
    LaunchedEffect(vod.id) { info = runCatching { withContext(Dispatchers.IO) { app.api.vodInfo(vod.id) } }.getOrNull() }
    val key = "m${vod.id}"
    val resumeAt = app.resumes.firstOrNull { it.key == key }?.pos ?: 0
    val item = PlayItem(app.api.vodUrl(vod), vod.name, listOfNotNull(info?.year?.ifEmpty { null }, info?.genre?.ifEmpty { null }).joinToString(" · ").ifEmpty { "Movie" }, vod.icon, key, vodId = vod.id)
    val more = remember(vod) { app.moviesVisible.filter { it.categoryId == vod.categoryId && it.id != vod.id }.take(12) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

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

    Box(Modifier.fillMaxSize().background(C.Ground)) {
        Box(Modifier.align(Alignment.TopEnd).width(760.dp).height(430.dp)) {
            Art(info?.backdrop ?: "", "", vod.id, Modifier.fillMaxSize(), showTitle = false)
            Box(Modifier.fillMaxSize().background(Color(0x990B0E14)))
        }
        IconBtn(Icons.Back, "Back", { app.back() }, Modifier.padding(start = 6.dp, top = 6.dp), size = 44.dp)
        Column(Modifier.fillMaxSize().padding(start = 56.dp, top = 48.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Art(vod.icon, vod.name, vod.id, Modifier.size(220.dp, 330.dp), 18)
                Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (vod.rating > 0) Badge("%.1f".format(vod.rating), C.Accent, C.OnLight)
                        T(listOfNotNull(info?.year, info?.duration, info?.genre).filter { it.isNotEmpty() }.joinToString("  ·  "), 14, Semi, C.Soft)
                    }
                    T(vod.name, 44, Heavy, maxLines = 2, lineHeight = 48)
                    T(info?.plot ?: "", 15, color = Color(0xFFC9CED8), maxLines = 4, lineHeight = 24)
                    if (!info?.director.isNullOrEmpty()) T("Director  ${info!!.director}", 13, color = C.Muted)
                    if (!info?.cast.isNullOrEmpty()) T("Cast  ${info!!.cast}", 13, color = C.Muted, maxLines = 2)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn(if (resumeAt > 0) "Resume ${fmtDur(resumeAt)}" else "Play", { app.push(Screen.VodPlayer(item)) }, style = BtnStyle.Primary, icon = Icons.Play, height = 52.dp, focusRequester = first)
                        if (resumeAt > 0) Btn("From start", { app.saveResume(Resume(key, vod.name, "", vod.icon, 0, 1, vod.id)); app.push(Screen.VodPlayer(item)) }, height = 52.dp)
                        val fav = app.isFav(key)
                        IconBtn(if (fav) Icons.StarFilled else Icons.Star, if (fav) "Remove from favourites" else "Add to favourites", { app.toggleFav(key) }, size = 52.dp, tint = if (fav) C.Accent else C.Text)
                    }
                    if (resumeAt > 0) ProgressLine(app.resumes.first { it.key == key }.let { it.pos / it.dur.coerceAtLeast(1).toFloat() }, Modifier.width(260.dp), height = 4.dp)
                }
            }
            Spacer(Modifier.weight(1f))
            if (more.isNotEmpty()) {
                T("More like this", 17, Heavy)
                LazyRow(Modifier.padding(top = 12.dp, bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(more, key = { it.id }) { m ->
                        FocusSurface({ app.stack[app.stack.lastIndex] = Screen.MovieDetail(m) }, Modifier.size(120.dp, 180.dp), shape = RoundedCornerShape(10.dp)) { f ->
                            Art(m.icon, m.name, m.id, Modifier.fillMaxSize().padding(if (f) 4.dp else 0.dp))
                        }
                    }
                }
            }
        }
    }
}
