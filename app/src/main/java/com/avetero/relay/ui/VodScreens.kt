package com.avetero.relay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.C as M3
import androidx.media3.common.Player
import com.avetero.relay.AppState
import com.avetero.relay.PlayerSurface
import com.avetero.relay.FullscreenVideo
import com.avetero.relay.Episode
import com.avetero.relay.PlayItem
import com.avetero.relay.Resume
import com.avetero.relay.Screen
import com.avetero.relay.Series
import com.avetero.relay.SeriesInfo
import com.avetero.relay.Vod
import com.avetero.relay.VodInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val RESUME = "__resume"
private const val ALL = "__all"
private const val RECENT = "__recent"

/** A poster in the grid; movies and series share it. */
private data class Tile(val id: Int, val name: String, val image: String, val rating: Double, val meta: String, val added: Long)

/** Boards 07 + 10. */
@Composable
fun VodGridScreen(app: AppState, series: Boolean, nav: (Tab) -> Unit, initialGroup: String? = null) = WithRail(if (series) Tab.Series else Tab.Movies, nav) {
    val cats = if (series) app.seriesCatsVisible else app.vodCatsVisible
    val all = remember(series, app.moviesVisible, app.seriesVisible) {
        if (series) app.seriesVisible.map { Tile(it.id, it.name, it.cover, it.rating, it.year, 0) to it.categoryId }
        else app.moviesVisible.map { Tile(it.id, it.name, it.icon, it.rating, "", it.added) to it.categoryId }
    }
    val resumeIds = app.resumes.map { if (series) it.seriesId else it.vodId }.filter { it != 0 }.distinct()
    var groupId by rememberSaveable(series, initialGroup) { mutableStateOf(initialGroup ?: if (resumeIds.isNotEmpty()) RESUME else ALL) }
    var drawer by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    val recentIds = if (series) app.recentSeries else app.recentMovies
    val groups = remember(cats, all, resumeIds, recentIds) {
        val counts = all.groupingBy { it.second }.eachCount()
        listOfNotNull(
            Group(RESUME, "Continue watching", resumeIds.size).takeIf { resumeIds.isNotEmpty() },
            Group(RECENT, "Recently watched", recentIds.size).takeIf { recentIds.isNotEmpty() },
            Group(ALL, if (series) "All series" else "All movies", all.size),
        ) + cats.map { Group(it.id, it.name, counts[it.id] ?: 0) }.filter { it.count > 0 }
    }
    var query by rememberSaveable(series) { mutableStateOf("") }
    val tiles = remember(groupId, sort, all, resumeIds, recentIds, query) {
        val base = when (groupId) {
            RESUME -> resumeIds.mapNotNull { id -> all.firstOrNull { it.first.id == id }?.first }
            RECENT -> recentIds.mapNotNull { id -> all.firstOrNull { it.first.id == id }?.first }
            ALL -> all.map { it.first }
            else -> all.filter { it.second == groupId }.map { it.first }
        }
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        when (sort) { 1 -> base.sortedBy { it.name.lowercase() }; 2 -> base.sortedByDescending { it.rating }; else -> if (groupId == RESUME || groupId == RECENT) base else base.sortedByDescending { it.added } }
    }
    val gridState = rememberLazyGridState()
    val first = remember { FocusRequester() }
    LaunchedEffect(groupId, sort) { gridState.scrollToItem(0) }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    val phone = compact()
    val hideVod: (Group) -> Unit = { g ->
        app.setCategoryHidden((if (series) "series:" else "vod:") + g.id, true)
        if (g.id == groupId) groupId = ALL
        app.message = "Hidden “${g.name}”"
    }
    val moveVod = categoryMover(app, if (series) "series" else "vod")
    if (!phone) GroupColumn("Categories", groups, groupId, { groupId = it }, onHide = hideVod, onMove = moveVod)
    Column(Modifier.weight(1f).fillMaxHeight().padding(start = if (phone) 0.dp else 32.dp, end = if (phone) 0.dp else 32.dp, top = if (phone) 12.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 20.dp)) {
        if (phone) {
            T(if (series) "Series" else "Movies", 26, Heavy, modifier = Modifier.padding(horizontal = 16.dp))
            val current = groups.firstOrNull { it.id == groupId }
            CategoryButton(current?.name ?: "Categories", current?.count ?: 0) { drawer = true }
            if (drawer) CategoryDrawer(if (series) "Series" else "Movies", groups, groupId, { groupId = it }, onHide = hideVod, onMove = moveVod) { drawer = false }
        }
        val sortRow: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Recently added", "A – Z", "Rating").forEachIndexed { i, s ->
                    FocusSurface({ sort = i }, Modifier.height(if (phone) 34.dp else 40.dp), selected = sort == i, idle = C.Control, shape = RoundedCornerShape(10.dp)) { f ->
                        T(s, 13, Bold, fg(f), Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
                    }
                }
            }
        }
        // the category button already shows the count
        val what = if (series) "series" else "movies"
        if (phone) Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterField(query, { query = it }, "Search $what in this category")
            sortRow()
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            T(groups.firstOrNull { it.id == groupId }?.name ?: "", 26, Heavy)
            T("   %,d %s".format(tiles.size, what), 15, Semi, C.Muted, Modifier.weight(1f))
            FilterField(query, { query = it }, "Search $what", Modifier.width(240.dp))
            Spacer(Modifier.width(12.dp))
            sortRow()
        }
        if (tiles.isEmpty()) EmptyState(if (query.isNotBlank()) "No matches" else "Nothing here yet",
            if (query.isNotBlank()) "Nothing in this category matches “${query.trim()}”." else "This category has no $what.")
        LazyVerticalGrid(if (phone) GridCells.Adaptive(108.dp) else GridCells.Fixed(6), Modifier, gridState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = if (phone) 12.dp else 0.dp, end = if (phone) 12.dp else 0.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(tiles, key = { _, t -> t.id }) { i, t ->
                FocusSurface({
                    if (series) app.series.firstOrNull { it.id == t.id }?.let { app.push(Screen.SeriesDetail(it)) }
                    else app.movies.firstOrNull { it.id == t.id }?.let { app.push(Screen.MovieDetail(it)) }
                }, focusRequester = if (i == 0) first else null, onLongClick = { app.toggleFav((if (series) "s" else "m") + t.id) }) { f ->
                    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                            Art(t.image, t.name, t.id, Modifier.fillMaxSize())
                            if (t.rating > 0) Badge("%.1f".format(t.rating), C.Accent, C.OnLight, Modifier.align(Alignment.TopEnd).padding(6.dp))
                        }
                        T(t.name, 13, Bold, fg(f))
                        if (t.meta.isNotEmpty()) T(t.meta, 12, color = sub(f))
                    }
                }
            }
        }
    }
}

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
                Btn(if (resumeAt > 0) "Resume ${fmtDur(resumeAt)}" else "Play", { app.push(Screen.VodPlayer(item)) }, Modifier.fillMaxWidth(), style = BtnStyle.Light, icon = Icons.Play, height = 52.dp)
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
                        Btn(if (resumeAt > 0) "Resume ${fmtDur(resumeAt)}" else "Play", { app.push(Screen.VodPlayer(item)) }, style = BtnStyle.Light, icon = Icons.Play, height = 52.dp, focusRequester = first)
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

/** Board 11. */
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
        LazyColumn(Modifier.fillMaxSize().background(C.Ground).statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
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
                        Btn(if (resumeEp != null) "Resume S${resumeEp.season} · E${resumeEp.num}" else "Play", { target?.let(::play) }, Modifier.weight(1f), style = BtnStyle.Light, icon = Icons.Play, height = 50.dp, enabled = target != null)
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
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons) { n ->
                        FocusSurface({ season = n }, Modifier.height(38.dp), idle = if (n == season) C.Accent else C.Control, shape = RoundedCornerShape(19.dp)) { f ->
                            T("Season $n", 13, Bold, if (f || n == season) C.OnLight else C.Soft, Modifier.align(Alignment.Center).padding(horizontal = 16.dp))
                        }
                    }
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

    Row(Modifier.fillMaxSize().background(C.Ground)) {
        Column(Modifier.width(420.dp).fillMaxHeight().background(C.Panel).padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconBtn(Icons.Back, "Back", { app.back() }, size = 44.dp)
            Art(s.cover, s.name, s.id, Modifier.size(150.dp, 225.dp), showTitle = false)
            T(s.name, 34, Heavy, maxLines = 2, lineHeight = 38)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (s.rating > 0) Badge("%.1f".format(s.rating), C.Accent, C.OnLight)
                T(listOfNotNull(s.year.ifEmpty { null }, "${seasons.size} seasons".takeIf { seasons.isNotEmpty() }, info?.genre?.ifEmpty { null }).joinToString("  ·  "), 13, Semi, C.Soft)
            }
            T(info?.plot?.ifEmpty { null } ?: s.plot, 14, color = Color(0xFFC9CED8), maxLines = 6, lineHeight = 22)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val target = resumeEp ?: eps.firstOrNull()
                Btn(if (resumeEp != null) "Resume S${resumeEp.season} · E${resumeEp.num}" else "Play", { target?.let(::play) }, Modifier.weight(1f), style = BtnStyle.Light, icon = Icons.Play, height = 52.dp, enabled = target != null)
                val fav = app.isFav("s${s.id}")
                IconBtn(if (fav) Icons.StarFilled else Icons.Star, "Favourite", { app.toggleFav("s${s.id}") }, size = 52.dp, tint = if (fav) C.Accent else C.Text)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 40.dp, end = 48.dp, top = 48.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            when {
                error != null -> EmptyState("Couldn't load episodes", error!!)
                info == null -> T("Loading episodes…", 15, color = C.Muted)
                eps.isEmpty() -> EmptyState("No episodes", "Your provider hasn't listed any episodes for this series.")
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(seasons) { n ->
                    FocusSurface({ season = n }, Modifier.height(42.dp), idle = if (n == season) C.Accent else C.Control, shape = RoundedCornerShape(21.dp)) { f ->
                        T("Season $n", 14, Bold, if (f || n == season) C.OnLight else C.Soft, Modifier.align(Alignment.Center).padding(horizontal = 18.dp))
                    }
                }
            }
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(eps.filter { it.season == season }, key = { _, e -> e.id }) { i, e ->
                    val r = app.resumes.firstOrNull { it.key == "e${e.id}" }
                    val watched = app.store.watched(app.account, "e${e.id}")
                    FocusSurface({ play(e) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        focusRequester = if (e == resumeEp || (resumeEp?.season != season && i == 0)) first else null) { f ->
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Box(Modifier.size(136.dp, 76.dp)) {
                                Art(e.image.ifEmpty { s.cover }, "", e.id, Modifier.fillMaxSize(), showTitle = false)
                                val frac = if (watched) 1f else r?.let { it.pos / it.dur.coerceAtLeast(1).toFloat() } ?: 0f
                                if (frac > 0) ProgressLine(frac, Modifier.fillMaxWidth().align(Alignment.BottomStart), Color(0x66000000), height = 4.dp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                T("Episode ${e.num}" + if (e.duration.isNotEmpty()) " · ${e.duration}" else "", 13, Bold, sub(f))
                                T(e.title, 16, Heavy, fg(f))
                                if (e.plot.isNotEmpty()) T(e.plot, 13, color = sub(f))
                            }
                            T(when { watched -> "Watched"; r != null -> fmtDur(r.dur - r.pos) + " left"; else -> "" }, 12, Heavy, sub(f))
                        }
                    }
                }
            }
        }
    }
}

fun fmtDur(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Board 09: movie / episode player with seek, tracks, speed and next-episode card. */
@Composable
fun VodPlayerScreen(app: AppState, item: PlayItem, next: List<PlayItem>, episodes: List<PlayItem> = emptyList()) {
    FullscreenVideo(app.inPip)
    val p = app.player.player
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var showUi by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var audio by remember { mutableStateOf("Default") }
    var subs by remember { mutableStateOf(if (app.store.subtitlesOn) "On" else "Off") }
    var speed by remember { mutableFloatStateOf(1f) }
    var zoom by remember { mutableStateOf(app.store.zoomDefault) }
    val back = app.store.skipBackSec * 1000L
    val fwd = app.store.skipFwdSec * 1000L
    var ended by remember { mutableStateOf(false) }
    var seekHint by remember { mutableStateOf<String?>(null) }
    var tracksOpen by remember { mutableStateOf(false) }
    LaunchedEffect(seekHint) { if (seekHint != null) { delay(1_200); seekHint = null } }
    val root = remember { FocusRequester() }
    val playBtn = remember { FocusRequester() }
    val speedBtn = remember { FocusRequester() }
    val sideBtn = remember { FocusRequester() }

    fun save() {
        if (dur <= 0) return
        app.saveResume(Resume(item.resumeKey, item.title, item.meta, item.image, pos, dur, item.vodId, item.seriesId))
        // finished an episode: keep the show in Continue watching with the next one queued up
        val n = next.firstOrNull()
        if (pos > dur * 0.95 && n != null)
            app.saveResume(Resume(n.resumeKey, n.title, "Up next · ${n.meta}", n.image, 0, 1, n.vodId, n.seriesId), upNext = true)
    }
    fun playNext() { save(); if (next.isNotEmpty()) app.stack[app.stack.lastIndex] = Screen.VodPlayer(next.first(), next.drop(1), episodes) }
    fun playEpisode(e: PlayItem) {
        save()
        val i = episodes.indexOf(e)
        app.stack[app.stack.lastIndex] = Screen.VodPlayer(e, episodes.drop(i + 1).take(20), episodes)
    }
    var episodesOpen by remember { mutableStateOf(false) }

    LaunchedEffect(item.url) {
        ended = false
        app.addRecentVod(item)
        app.player.play(item.url, app.store.resumeAt(app.account, item.resumeKey), app.store.subtitlesOn)
        var ticks = 0
        while (true) {
            pos = p.currentPosition; dur = p.duration.coerceAtLeast(0)
            if (p.playbackState == Player.STATE_ENDED && !ended) {
                ended = true
                if (next.isNotEmpty() && app.store.autoplayNext) playNext() else { pos = dur; save() }
            }
            if (++ticks % 20 == 0) save() // every 10 s
            delay(500)
        }
    }
    DisposableEffect(item.url) { onDispose { save(); app.player.stop() } }
    LaunchedEffect(poke, app.player.playing) {
        showUi = true
        if (app.player.playing) { delay(5_000); showUi = false; runCatching { root.requestFocus() } }
    }
    LaunchedEffect(showUi) { if (showUi) runCatching { playBtn.requestFocus() } }
    // Back first hides the controls; the next Back leaves the player
    androidx.activity.compose.BackHandler(showUi && !app.inPip && !tracksOpen && !episodesOpen) { showUi = false; runCatching { root.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && showUi) poke++; false }
            .focusRequester(root).focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || showUi) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft, Key.MediaRewind -> { p.seekTo((p.currentPosition - back).coerceAtLeast(0)); poke++; true }
                    Key.DirectionRight, Key.MediaFastForward -> { p.seekTo(p.currentPosition + fwd); poke++; true }
                    Key.MediaPlayPause -> { if (p.isPlaying) p.pause() else p.play(); poke++; true }
                    Key.DirectionCenter, Key.Enter, Key.DirectionUp, Key.DirectionDown -> { poke++; true }
                    else -> false
                }
            }
            // phones: tap shows/hides controls, double-tap left/right skips, drag sideways scrubs
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (showUi) showUi = false else poke++ },
                    onDoubleTap = { o ->
                        val left = o.x < size.width / 2
                        p.seekTo((p.currentPosition + if (left) -back else fwd).coerceAtLeast(0))
                        seekHint = if (left) "−${back / 1000} s" else "+${fwd / 1000} s"
                    },
                )
            }
            .pointerInput(Unit) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { p.seekTo((p.currentPosition + (dx / size.width * 180_000).toLong()).coerceAtLeast(0)); seekHint = null },
                ) { _, d -> dx += d; val secs = (dx / size.width * 180).toInt(); seekHint = (if (secs >= 0) "+" else "−") + "${kotlin.math.abs(secs)} s" }
            },
    ) {
        PlayerSurface(p, Modifier.fillMaxSize(), zoom)
        app.player.error?.let { PlayerError(it, { app.player.retry() }, null) }
        seekHint?.let { Badge(it, C.Ground, C.Text, Modifier.align(Alignment.Center)) }
        if (tracksOpen) TracksDialog(app) { tracksOpen = false; poke++ }
        if (episodesOpen) EpisodesDialog(episodes, item, { episodesOpen = false; playEpisode(it) }) { episodesOpen = false; poke++ }

        if (!app.inPip && next.isNotEmpty() && dur > 0 && dur - pos in 1..30_000) {
            Row(Modifier.align(Alignment.BottomEnd).padding(start = 16.dp, end = if (compact()) 16.dp else 40.dp, bottom = if (showUi) 196.dp else 40.dp).widthIn(max = 480.dp)
                .clip(RoundedCornerShape(14.dp)).background(C.Ground).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Art(next.first().image, "", 1, Modifier.size(112.dp, 63.dp), showTitle = false)
                Column(Modifier.weight(1f)) {
                    T("Next episode in ${(dur - pos) / 1000} s", 13, Bold, C.Accent); T(next.first().title, 15, Heavy, maxLines = 2, lineHeight = 20)
                }
                Btn("Play", ::playNext, style = BtnStyle.Light, height = 40.dp)
            }
        }

        AnimatedVisibility(showUi && !app.inPip, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(Modifier.fillMaxWidth().background(Color(0xE60B0E14)).statusBarsPadding().height(if (compact()) 72.dp else 96.dp).padding(horizontal = if (compact()) 16.dp else 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                IconBtn(Icons.Back, "Back", { app.back() }, size = 44.dp)
                Column(Modifier.weight(1f)) { T(item.meta, 13, Semi, C.Muted); T(item.title, 22, Heavy) }
                if (compact()) {
                    val activity = androidx.compose.ui.platform.LocalContext.current as? com.avetero.relay.MainActivity
                    IconBtn(Icons.Pip, "Picture in picture", { activity?.enterPip() }, size = 44.dp)
                }
                Clock(18)
            }
        }
        AnimatedVisibility(showUi && !app.inPip, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            val pad = if (compact()) 16.dp else 40.dp
            Column(Modifier.fillMaxWidth().background(Color(0xE60B0E14)).navigationBarsPadding().padding(start = pad, end = pad, top = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    T(fmtDur(pos), 14, mono = true)
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF2A3142))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(if (dur > 0) (p.bufferedPosition.toFloat() / dur).coerceIn(0f, 1f) else 0f).background(Color(0xFF4A5368)))
                        Box(Modifier.fillMaxHeight().fillMaxWidth(if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f).background(C.Accent))
                    }
                    T("−" + fmtDur((dur - pos).coerceAtLeast(0)), 14, color = C.Muted, mono = true)
                }
                val phone = compact()
                val transport: @Composable () -> Unit = {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBtn(Icons.Back, "Back ${back / 1000} seconds", { p.seekTo((p.currentPosition - back).coerceAtLeast(0)) },
                            if (phone) Modifier else Modifier.focusProperties { left = speedBtn }, size = 52.dp, round = true)
                        FocusSurface({ if (p.isPlaying) p.pause() else p.play() }, Modifier.size(68.dp), idle = C.Accent, shape = RoundedCornerShape(34.dp), focusRequester = playBtn,
                            label = if (app.player.playing) "Pause" else "Play") { _ ->
                            Ico(if (app.player.playing) Icons.Pause else Icons.Play, C.OnLight, 24.dp, Modifier.align(Alignment.Center))
                        }
                        // the gap to the side buttons is wide; point the remote at them explicitly
                        IconBtn(Icons.Chevron, "Forward ${fwd / 1000} seconds", { p.seekTo(p.currentPosition + fwd) },
                            if (phone) Modifier else Modifier.focusProperties { right = sideBtn }, size = 52.dp, round = true)
                    }
                }
                if (phone) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { transport() }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Audio, subtitles & quality", { tracksOpen = true }, height = 40.dp)
                        Btn(if (app.player.muted) "Unmute" else "Mute", { app.player.toggleMute() }, height = 40.dp)
                        Btn("%.2gx".format(speed), { speed = listOf(1f, 1.25f, 1.5f, 2f, 0.75f).let { it[(it.indexOf(speed) + 1) % it.size] }; p.setPlaybackSpeed(speed) }, height = 40.dp)
                        if (episodes.size > 1) Btn("Episodes", { episodesOpen = true }, height = 40.dp)
                        if (next.isNotEmpty()) Btn("Next episode", ::playNext, height = 40.dp)
                        Btn(if (zoom) "Zoom" else "Fit", { zoom = !zoom }, height = 40.dp)
                    }
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Audio, subtitles & quality", { tracksOpen = true }, height = 44.dp)
                        IconBtn(if (app.player.muted) Icons.Muted else Icons.Volume, if (app.player.muted) "Unmute" else "Mute", { app.player.toggleMute() }, size = 44.dp)
                        Btn("%.2gx".format(speed), {
                            speed = listOf(1f, 1.25f, 1.5f, 2f, 0.75f).let { it[(it.indexOf(speed) + 1) % it.size] }; p.setPlaybackSpeed(speed)
                        }, height = 44.dp, focusRequester = speedBtn)
                    }
                    transport()
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        if (episodes.size > 1) Btn("Episodes", { episodesOpen = true }, height = 44.dp, focusRequester = sideBtn)
                        if (next.isNotEmpty()) Btn("Next episode", ::playNext, height = 44.dp, focusRequester = if (episodes.size > 1) null else sideBtn)
                        Btn(if (zoom) "Zoom" else "Fit", { zoom = !zoom }, height = 44.dp, focusRequester = if (episodes.size > 1 || next.isNotEmpty()) null else sideBtn)
                    }
                }
            }
        }
    }
}
