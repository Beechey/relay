package com.avetero.relay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.avetero.relay.AppState
import com.avetero.relay.Channel
import com.avetero.relay.PlayItem
import com.avetero.relay.Release
import com.avetero.relay.Screen
import com.avetero.relay.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun Panel(width: Int, content: @Composable () -> Unit) {
    // a menu opened by holding OK must not take that same press (its repeats and release) as a click on its first item
    var armed by remember { mutableStateOf(false) }
    Column(Modifier.widthIn(max = width.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface)
        .onPreviewKeyEvent { e ->
            val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
            if (ok && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) armed = true
            ok && !armed
        }
        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit, focusRequester: FocusRequester? = null) =
    FocusSurface(onClick, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(10.dp), focusRequester = focusRequester) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Ico(icon, fg(f), 18.dp); T(text, 15, Bold, fg(f))
        }
    }

/** Board 18: channel long-press menu. */
@Composable
fun ChannelMenu(app: AppState, c: Channel, onDismiss: () -> Unit, inPlayer: Boolean = false) = Dialog(onDismiss) {
    var tracks by remember { mutableStateOf(false) }
    if (tracks) TracksDialog(app) { tracks = false; onDismiss() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Panel(340) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Logo(c.icon, c.name, false, Modifier.size(44.dp, 32.dp))
            Column { T(c.name, 16, Heavy); T(if (c.num > 0) "Channel ${c.num}" else "Channel", 12, color = C.Muted) }
        }
        val fav = app.isFav("c${c.id}")
        MenuItem(if (fav) "Remove from favourites" else "Add to favourites", if (fav) Icons.StarFilled else Icons.Star, { app.toggleFav("c${c.id}"); onDismiss() }, first)
        if (inPlayer) {
            MenuItem("Audio, subtitles & quality", Icons.Settings, { tracks = true })
        } else {
            MenuItem("Show in TV guide", Icons.Guide, { onDismiss(); app.go(Screen.Guide) })
            MenuItem("Watch fullscreen", Icons.Play, { onDismiss(); app.push(Screen.LivePlayer(c, app.channelsIn(c.categoryId))) })
        }
    }
}

@Composable
fun ExitDialog(onExit: () -> Unit, onDismiss: () -> Unit) = Dialog(onDismiss) {
    val stay = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { stay.requestFocus() } }
    Panel(340) {
        T("Exit Relay IPTV?", 20, Heavy)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Exit", onExit, Modifier.weight(1f), height = 46.dp)
            Btn("Stay", onDismiss, Modifier.weight(1f), style = BtnStyle.Light, height = 46.dp, focusRequester = stay)
        }
    }
}

/** Board 18: GitHub update prompt with download progress. */
@Composable
fun UpdateDialog(app: AppState, r: Release, onDismiss: () -> Unit) = Dialog(onDismiss) {
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Panel(440) {
        Badge("Update available", C.Accent, C.OnLight)
        T("Version ${r.version} is ready", 26, Heavy)
        ReleaseNotes(r.notes.ifBlank { "Bug fixes and improvements." })
        progress?.let { p ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(if (p >= 1f) "Downloaded" else "Downloading… ${(p * 100).toInt()}%", 13, Bold)
                ProgressLine(p, Modifier.fillMaxWidth(), height = 6.dp)
            }
        }
        error?.let { T(it, 13, Bold, C.Error, maxLines = 2) }
        T("Android will ask you to confirm the install. The first time, allow this app to install updates.", 12, color = C.Muted, maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Install now", {
                if (progress != null) return@Btn
                progress = 0f; error = null
                scope.launch {
                    try {
                        val apk = withContext(Dispatchers.IO) { Updater.download(app.ctx, r) { progress = it } }
                        progress = 1f
                        Updater.install(app.ctx, apk)
                    } catch (e: Exception) {
                        error = e.message ?: "Download failed"; progress = null
                    }
                }
            }, Modifier.weight(1f), style = BtnStyle.Light, focusRequester = first)
            Btn("Later", onDismiss, Modifier.weight(1f))
        }
        Btn("Skip this version", { app.store.skippedVersion = r.version; onDismiss() }, Modifier.fillMaxWidth(), height = 40.dp)
    }
}

/** "**bold**" spans to an AnnotatedString. */
private fun inlineMd(s: String) = androidx.compose.ui.text.buildAnnotatedString {
    s.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = Heavy, color = C.Text)) { append(part) } else append(part)
    }
}

/**
 * Release notes from GitHub, rendered and scrollable (D-pad Up/Down when focused, or touch).
 * shortcut: a small Markdown subset (# headings, "-" or "*" bullets, **bold**), enough for our release notes.
 */
@Composable
fun ReleaseNotes(md: String, modifier: Modifier = Modifier) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    // the first "# Relay IPTV x.y.z" line repeats the dialog title
    val lines = md.trim().lines().let { if (it.firstOrNull()?.startsWith("# ") == true) it.drop(1) else it }.dropWhile { it.isBlank() }
    Column(
        modifier.heightIn(max = 240.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (focused) C.Raised else C.Panel)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = when (e.key) { Key.DirectionDown -> 160f; Key.DirectionUp -> -160f; else -> return@onPreviewKeyEvent false }
                // past either end, let focus move on to the buttons
                if ((step > 0 && !scroll.canScrollForward) || (step < 0 && !scroll.canScrollBackward)) return@onPreviewKeyEvent false
                scope.launch { scroll.animateScrollBy(step) }; true
            }
            .focusable()
            .verticalScroll(scroll)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                line.startsWith("#") -> T(line.trimStart('#').trim(), 15, Heavy, maxLines = 2)
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("•", 14, Heavy, C.Accent)
                    androidx.compose.material3.Text(inlineMd(line.trimStart().drop(2)), color = C.Soft, fontSize = 14.sp, lineHeight = 21.sp)
                }
                else -> androidx.compose.material3.Text(inlineMd(line), color = C.Soft, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}

/** Per-video choices: quality, audio track and subtitles, as the stream offers them. */
@Composable
fun TracksDialog(app: AppState, onDismiss: () -> Unit) = Dialog(onDismiss) {
    val p = app.player
    p.tracks // recompose when the stream's tracks change
    val sections = listOf(
        "Quality" to p.options(androidx.media3.common.C.TRACK_TYPE_VIDEO),
        "Audio" to p.options(androidx.media3.common.C.TRACK_TYPE_AUDIO),
        "Subtitles" to p.options(androidx.media3.common.C.TRACK_TYPE_TEXT),
    ).filter { it.second.isNotEmpty() }
    var tick by remember { mutableStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(20.dp)).background(C.Surface).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T("Audio, subtitles & quality", 18, Heavy, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
        if (sections.isEmpty()) T("This stream has a single quality and audio track, and no subtitles.", 14, color = C.Muted, maxLines = 3, modifier = Modifier.padding(8.dp))
        sections.forEachIndexed { si, (title, opts) ->
            Label(title, Modifier.padding(start = 8.dp, top = if (si > 0) 10.dp else 0.dp))
            opts.forEachIndexed { oi, o ->
                FocusSurface({ o.pick(); tick++ }, Modifier.fillMaxWidth().height(46.dp), selected = o.selected, shape = RoundedCornerShape(10.dp),
                    focusRequester = if (si == 0 && oi == 0) first else null) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(o.label, 15, Bold, fg(f), Modifier.weight(1f))
                        if (o.selected) T("✓", 15, Heavy, if (f) C.OnLight else C.Accent)
                    }
                }
            }
        }
        Btn("Done", onDismiss, Modifier.fillMaxWidth().padding(top = 8.dp), style = BtnStyle.Light, height = 44.dp)
    }
}

/** Every episode of the show from inside the player, so you can jump several ahead or back. */
@Composable
fun EpisodesDialog(episodes: List<PlayItem>, current: PlayItem, onPick: (PlayItem) -> Unit, onDismiss: () -> Unit) = Dialog(onDismiss) {
    val at = episodes.indexOf(current).coerceAtLeast(0)
    val list = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (at - 2).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T("Episodes", 18, Heavy, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
        // several seasons: one button each that jumps to the season's first episode
        val seasons = episodes.map { it.meta }.distinct()
        if (seasons.size > 1) {
            val scope = rememberCoroutineScope()
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                items(seasons.size) { i ->
                    val first = episodes.indexOfFirst { it.meta == seasons[i] }
                    FocusSurface({ scope.launch { list.scrollToItem(first) } }, Modifier.height(40.dp), selected = episodes[at].meta == seasons[i], idle = C.Control, shape = RoundedCornerShape(20.dp)) { f ->
                        T(seasons[i].substringAfterLast(" · "), 14, Bold, fg(f), Modifier.align(Alignment.Center).padding(horizontal = 16.dp))
                    }
                }
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f, fill = false), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(episodes.size) { i ->
                val e = episodes[i]
                val season = e.meta.substringAfterLast(" · ")
                if (i == 0 || episodes[i - 1].meta != e.meta) Label(season, Modifier.padding(start = 8.dp, top = if (i > 0) 10.dp else 0.dp))
                FocusSurface({ if (i == at) onDismiss() else onPick(e) }, Modifier.fillMaxWidth().height(52.dp), selected = i == at, shape = RoundedCornerShape(10.dp),
                    focusRequester = if (i == at) focus else null) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(e.title, 15, Bold, fg(f), Modifier.weight(1f))
                        if (i == at) T("Playing", 13, Heavy, if (f) C.OnLight else C.Accent)
                    }
                }
            }
        }
        Btn("Close", onDismiss, Modifier.fillMaxWidth().padding(top = 8.dp), style = BtnStyle.Light, height = 44.dp)
    }
}
