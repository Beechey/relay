package com.avetero.relay.feature.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.avetero.relay.app.AppState
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.Move
import com.avetero.relay.data.model.Programme
import com.avetero.relay.data.model.minutesLeft
import com.avetero.relay.ui.tv.TvPivot
import com.avetero.relay.player.PlayerSurface
import com.avetero.relay.ui.components.Group
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvDialog
import com.avetero.relay.ui.tv.TvHints
import com.avetero.relay.ui.tv.TvSafe
import com.avetero.relay.ui.tv.TvSidePanel
import com.avetero.relay.ui.tv.TvSurface
import com.avetero.relay.util.fmtTime
import com.avetero.relay.util.nowSec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val WINDOW = 2 * 3600L
private val NameW = 200.dp

/**
 * TV guide (board TvGuide): the focused programme on top with the playing channel's preview, the category and time
 * controls, then the 2-hour grid with a now line. Right past the window's end (or Left before its start) moves the
 * window; ⏪⏩ move it 2 hours, Ch± pages the channels, ☰ picks a category. Focus comes back to the programme you left.
 */
@Composable
internal fun GuideTv(app: AppState, g: GuideState) {
    val ch = rememberGuideChannels(app, g)
    val list = ch.list
    val windowStart = g.windowStart
    val windowEnd = windowStart + WINDOW
    var focused by remember { mutableStateOf<Pair<Channel, Programme?>?>(null) }
    var focusedRow by remember { mutableIntStateOf(0) }
    // channel id and a time inside the focused programme, kept across leaving the screen
    var lastFocus by rememberSaveable { mutableStateOf<Pair<Int, Long>?>(null) }
    var panel by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    // a move of the window or a page: focus then lands on the programme at that time on that channel
    var pending by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    val pendingFocus = remember { FocusRequester() }
    var gridFocused by remember { mutableStateOf(false) }
    LaunchedEffect(pending) { if (pending != null) { delay(60); runCatching { pendingFocus.requestFocus() }; pending = null } }
    val rows = rememberLazyListState(app.guideScroll.first, app.guideScroll.second)
    DisposableEffect(Unit) { onDispose { app.guideScroll = rows.firstVisibleItemIndex to rows.firstVisibleItemScrollOffset } }
    val scope = rememberCoroutineScope()

    // on arrival: back to the programme you left; on a category change: the top of the new list
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(list.firstOrNull()?.id, windowFocused) {
        if (!windowFocused) return@LaunchedEffect
        delay(80)
        val back = lastFocus?.takeIf { !arrived && list.any { c -> c.id == it.first } }
        arrived = true
        if (back != null) pending = back
        else { rows.scrollToItem(0); delay(40); runCatching { first.requestFocus() } }
        delay(400)
        if (!gridFocused && !panel) runCatching { first.requestFocus() }
    }

    fun focusAt(row: Int, time: Long) {
        val c = list.getOrNull(row) ?: return
        scope.launch { rows.scrollToItem(row) }
        pending = c.id to time
    }

    TvPivot {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(start = 24.dp, end = TvSafe.Horizontal, top = TvSafe.Vertical), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Header(app, focused)
                Controls(g, ch, list.size) { panel = true }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val gridW = maxWidth - NameW
                    val perSec = gridW / WINDOW.toFloat()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(NameW))
                            (0 until 4).forEach {
                                Text(fmtTime(windowStart + it * 1800L) + if (it == 0 && g.shift != 0) "  " + SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(windowStart * 1000)) else "",
                                    style = MaterialTheme.typography.labelLarge, color = Tok.OnSurfaceVariant, modifier = Modifier.width(gridW / 4))
                            }
                        }
                        LazyColumn(Modifier.onFocusChanged { gridFocused = it.hasFocus }.onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val t = lastFocus?.second ?: maxOf(nowSec(), windowStart)
                            when (e.key) {
                                Key.ChannelUp, Key.PageUp -> { focusAt((focusedRow - 6).coerceAtLeast(0), t); true }
                                Key.ChannelDown, Key.PageDown -> { focusAt((focusedRow + 6).coerceAtMost(list.lastIndex), t); true }
                                Key.MediaRewind, Key.MediaFastForward -> {
                                    val by = if (e.key == Key.MediaRewind) -2 else 2
                                    g.move(by)
                                    focusAt(focusedRow, (t + by * 3600L).coerceIn(g.windowStart, g.windowStart + WINDOW - 1)); true
                                }
                                Key.Menu -> { panel = true; true }
                                else -> false
                            }
                        }, rows, contentPadding = PaddingValues(top = 4.dp, bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                                LaunchedEffect(c.id) { app.loadEpg(c) }
                                val all = rememberProgrammes(app, c)
                                val progs = remember(all, windowStart) { all.filter { it.end > windowStart && it.start < windowEnd } }
                                val target = pending?.takeIf { it.first == c.id }?.let { (_, t) -> progs.indexOfFirst { t >= it.start && t < it.end }.coerceAtLeast(0) }
                                val firstIdx = if (i == 0 && pending == null) progs.indexOfFirst { nowSec() in it.start until it.end }.coerceAtLeast(0) else -1
                                Row(Modifier.height(64.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ChannelCell(c)
                                    Box(Modifier.width(gridW - 4.dp).fillMaxHeight()) {
                                        fun onFocus(p: Programme?) { focused = c to p; focusedRow = i; lastFocus = c.id to (p?.let { maxOf(it.start, windowStart) } ?: maxOf(nowSec(), windowStart)) }
                                        if (progs.isEmpty()) TvSurface({ watchLive(app, c, list) }, Modifier.fillMaxSize().onFocusChanged { if (it.isFocused) onFocus(null) },
                                            idle = Tok.Container, focusRequester = when { target != null -> pendingFocus; i == 0 && pending == null -> first; else -> null }) {
                                            Text("No guide for this channel", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp))
                                        }
                                        progs.forEachIndexed { j, p ->
                                            val s = maxOf(p.start, windowStart); val e = minOf(p.end, windowEnd)
                                            Cell(app, c, p, perSec * (s - windowStart).toFloat(), perSec * (e - s).toFloat() - 4.dp,
                                                when { target == j -> pendingFocus; j == firstIdx -> first; else -> null }, { onFocus(p) },
                                                onEdge = { right ->
                                                    when {
                                                        right && p.end >= windowEnd && g.shift < GuideState.MAX_SHIFT -> { g.move(1); pending = c.id to maxOf(p.end, windowStart + 3600); true }
                                                        !right && p.start <= windowStart && g.shift > GuideState.MIN_SHIFT -> { g.move(-1); pending = c.id to (minOf(p.start, windowStart) - 1); true }
                                                        else -> false
                                                    }
                                                }) { act(app, c, p, list) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // the now line only when "now" is inside the window being shown
                    val now = nowSec()
                    if (now in windowStart until windowEnd) Box(Modifier.offset(x = NameW + perSec * (now - windowStart).toFloat()).padding(top = 24.dp).width(2.dp).fillMaxHeight()
                        .clip(RoundedCornerShape(1.dp)).background(Tok.Primary))
                }
            }
            val (fc, fp) = focused ?: (null to null)
            val fromStart = fc != null && fp != null && fc.archive && nowSec() in fp.start until fp.end
            Box(Modifier.align(Alignment.BottomEnd).padding(end = TvSafe.Horizontal, bottom = TvSafe.Vertical)) {
                if (fromStart) TvHints("OK" to "Watch", "Hold OK" to "From the start", "⏪⏩" to "2 hours", "☰" to "Category")
                else TvHints("OK" to "Watch or remind", "Ch±" to "Page", "⏪⏩" to "2 hours", "☰" to "Category")
            }
        }
    }

    CategoryPanel(g, panel, onPick = { panel = false; scope.launch { rows.scrollToItem(0) } }) { panel = false; pending = lastFocus }
}

/** The focused programme (time, title, two lines of description) and the preview of what's playing. */
@Composable
private fun Header(app: AppState, focused: Pair<Channel, Programme?>?) {
    val (fc, fp) = focused ?: (null to null)
    Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val now = nowSec()
            val status = when {
                fp == null -> null
                now in fp.start until fp.end -> fp.minutesLeft()
                fp.start > now -> ((fp.start - now) / 60).let { m -> if (m >= 60) "starts in ${m / 60} h ${m % 60} min" else "starts in $m min" } + if (app.hasReminder(fc!!, fp)) " · Reminder set" else ""
                fc!!.archive -> "Catch-up"
                else -> "This channel has no catch-up"
            }
            Text(listOfNotNull(fc?.name, fp?.let { "${fmtTime(it.start)} – ${fmtTime(it.end)}" }, status).joinToString(" · "), style = MaterialTheme.typography.bodyLarge, color = Tok.OnSurfaceVariant, maxLines = 1)
            Text(fp?.title ?: fc?.name ?: "TV Guide", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fp?.description?.ifBlank { null } ?: if (fc != null && fp == null) "No programme information for this channel." else "",
                style = MaterialTheme.typography.bodyLarge, color = Tok.OnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 620.dp))
        }
        Box(Modifier.size(284.dp, 160.dp).clip(RoundedCornerShape(16.dp)).background(Tok.ContainerHigh)) {
            if (app.player.url != null) {
                PlayerSurface(app.player.player, Modifier.fillMaxSize())
                Text("Preview" + (app.liveChannel?.name?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(Tok.Surface.copy(alpha = .8f)).padding(horizontal = 10.dp, vertical = 4.dp))
            } else Text("Nothing playing", style = MaterialTheme.typography.labelLarge, color = Tok.OnSurfaceVariant, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** Category button, the joined Earlier / Now / Later / Tomorrow pills, and Guide only. */
@Composable
private fun Controls(g: GuideState, ch: GuideChannels, count: Int, openCategories: () -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        TvSurface(openCategories, Modifier.height(40.dp), idle = Tok.ContainerHigh, shape = RoundedCornerShape(20.dp)) {
            Text("☰  ${g.current?.name ?: "All channels"} · $count", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), maxLines = 1,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp).widthIn(max = 320.dp), overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        Pill("◀", RoundedCornerShape(20.dp, 4.dp, 4.dp, 20.dp), enabled = g.shift > GuideState.MIN_SHIFT) { g.move(-2) }
        Pill("Now", RoundedCornerShape(4.dp), selected = g.shift == 0) { g.shift = 0 }
        Pill("▶", RoundedCornerShape(4.dp), enabled = g.shift < GuideState.MAX_SHIFT) { g.move(2) }
        Pill("Tomorrow", RoundedCornerShape(4.dp, 20.dp, 20.dp, 4.dp), enabled = g.tomorrowShift > 2, selected = g.shift >= g.tomorrowShift - 1) { g.shift = g.tomorrowShift }
        Spacer(Modifier.width(8.dp))
        // the tick only when it's really filtering (a provider without a full guide has nothing to filter by)
        Pill(if (ch.filtering) "Guide only ✓" else "Guide only", RoundedCornerShape(20.dp), enabled = ch.canFilter, selected = ch.filtering) { g.guideOnly = !g.guideOnly }
    }

@Composable
private fun Pill(text: String, shape: Shape, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) =
    TvSurface({ if (enabled) onClick() }, Modifier.height(40.dp).alpha(if (enabled) 1f else .4f), selected = selected, idle = Tok.ContainerHigh, shape = shape) {
        Text(text, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp))
    }

@Composable
private fun ChannelCell(c: Channel) =
    Row(Modifier.width(NameW - 4.dp).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Tok.ContainerLow).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (c.num > 0) Box(Modifier.size(48.dp, 32.dp).clip(RoundedCornerShape(8.dp)).background(Tok.ContainerHighest), Alignment.Center) {
            Text(c.num.toString(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Tok.OnSurfaceVariant, maxLines = 1)
        }
        Column {
            Text(c.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.archive) Text("Catch-up", style = MaterialTheme.typography.labelMedium, color = Tok.Primary)
        }
    }

/** One programme: past ones fade, the airing one is lighter, reminders and catch-up are labelled. */
@Composable
private fun Cell(app: AppState, c: Channel, p: Programme, x: Dp, w: Dp, focusRequester: FocusRequester?, onFocus: () -> Unit,
                 onEdge: (right: Boolean) -> Boolean, onClick: () -> Unit) {
    val now = nowSec()
    val live = now in p.start until p.end
    val past = p.end <= now
    TvSurface(onClick, Modifier.offset(x = x).width(w).fillMaxHeight().alpha(if (past) .5f else 1f)
        .onPreviewKeyEvent { e -> e.type == KeyEventType.KeyDown && (e.key == Key.DirectionRight || e.key == Key.DirectionLeft) && onEdge(e.key == Key.DirectionRight) }
        .onFocusChanged { if (it.isFocused) onFocus() },
        // hold OK on what's airing on a catch-up channel: watch it from the start
        onLongClick = if (live && c.archive) ({ watchCatchup(app, c, p) }) else null,
        idle = if (live) Tok.ContainerHigh else Tok.Container, focusRequester = focusRequester) {
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (live || p.start >= now) "${fmtTime(p.start)} – ${fmtTime(p.end)}" else fmtTime(p.start), style = MaterialTheme.typography.labelLarge,
                color = LocalContentColor.current.copy(alpha = .75f), maxLines = 1)
        }
        val tag = when { app.hasReminder(c, p) -> "Reminder"; c.archive && !live && past -> "Catch-up"; else -> null }
        if (tag != null && w > 140.dp) Text(tag, style = MaterialTheme.typography.labelMedium, color = if (LocalContentColor.current == Tok.InverseOnSurface) Tok.InverseOnSurface else Tok.Primary,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 12.dp))
    }
}

/** Categories in the side panel: OK shows one; hold OK on one of the provider's to move or hide it. */
@Composable
private fun CategoryPanel(g: GuideState, visible: Boolean, onPick: () -> Unit, onDismiss: () -> Unit) {
    var actionsFor by remember { mutableStateOf<Group?>(null) }
    var hiding by remember { mutableStateOf<Group?>(null) }
    val selected = remember { FocusRequester() }
    val firstAction = remember { FocusRequester() }
    LaunchedEffect(visible) { if (!visible) actionsFor = null }
    TvSidePanel(visible, onDismiss, selected, title = actionsFor?.name ?: "Categories") {
        val grp = actionsFor
        if (grp == null) {
            val start = remember(g.groups) { g.groups.indexOfFirst { it.id == g.groupId }.coerceAtLeast(0) }
            LaunchedEffect(Unit) { selected.settle() }
            LazyColumn(state = rememberLazyListState(start), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(g.groups, key = { _, it -> it.id }) { _, it ->
                    val on = it.id == g.groupId
                    TvSurface({ g.groupId = it.id; onPick() }, Modifier.fillMaxWidth().height(48.dp), selected = on, focusRequester = if (on) selected else null,
                        onLongClick = if (it.id.startsWith("__")) null else ({ actionsFor = it })) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(it.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (it.count > 0) Text("%,d".format(it.count), style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(alpha = .75f))
                        }
                    }
                }
            }
            Text("Hold OK on a category to move or hide it", style = MaterialTheme.typography.labelLarge, color = Tok.OnSurfaceVariant)
        } else {
            BackHandler { actionsFor = null }
            LaunchedEffect(grp) { firstAction.settle() }
            listOf("Move to top" to Move.Top, "Move up one" to Move.Up, "Move down one" to Move.Down, "Move to bottom" to Move.Bottom, "Reorder several…" to null).forEachIndexed { i, (label, m) ->
                Action(label, if (i == 0) firstAction else null) { actionsFor = null; if (m == null) onDismiss(); g.moveCategory(grp, m) }
            }
            Action("Hide category", null) { hiding = grp }
        }
    }
    hiding?.let { grp ->
        TvDialog({ hiding = null }) {
            val cancel = remember { FocusRequester() }
            LaunchedEffect(Unit) { cancel.settle() }
            Text("Hide “${grp.name}”?", style = MaterialTheme.typography.headlineSmall)
            Text("It won't appear in lists, All, the guide or search. Bring it back in Settings → Manage categories.", style = MaterialTheme.typography.bodyLarge, color = Tok.OnSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Hide", { g.hide(grp); hiding = null; actionsFor = null })
                TvButton("Cancel", { hiding = null }, primary = true, focusRequester = cancel)
            }
        }
    }
}

@Composable
private fun Action(label: String, focusRequester: FocusRequester?, onClick: () -> Unit) =
    TvSurface(onClick, Modifier.fillMaxWidth().height(48.dp), focusRequester = focusRequester) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp))
    }
