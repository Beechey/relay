package com.avetero.relay.feature.guide

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.AppState.Companion.ALL
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.PlayItem
import com.avetero.relay.data.model.Programme
import com.avetero.relay.data.model.minutesLeft
import com.avetero.relay.data.model.progress
import com.avetero.relay.feature.categories.categoryMover
import com.avetero.relay.feature.live.hideLive
import com.avetero.relay.feature.live.rememberLiveGroups
import com.avetero.relay.player.PlayerSurface
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.components.CategoryButton
import com.avetero.relay.ui.components.CategoryDrawer
import com.avetero.relay.ui.components.Clock
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.LivePill
import com.avetero.relay.ui.components.Logo
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.components.WithRail
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Ico
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.LiveGradient
import com.avetero.relay.ui.theme.Semi
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import com.avetero.relay.ui.theme.fg
import com.avetero.relay.ui.theme.sub
import com.avetero.relay.ui.theme.tone
import com.avetero.relay.util.fmtTime
import com.avetero.relay.util.nowSec
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Redesign board 03: the focused programme is the hero (with Watch live, From the start on catch-up channels,
 * Remind me for later ones), the playing channel floats top right, and the grid opens on channels that have a guide.
 */
@Composable
fun GuideScreen(app: AppState, nav: (Tab) -> Unit) = WithRail(Tab.Guide, nav, behindDock = !compact()) {
    var groupId by rememberSaveable { mutableStateOf(ALL) }
    var drawer by remember { mutableStateOf(false) }
    var guideOnly by rememberSaveable { mutableStateOf(true) }
    val groups = rememberLiveGroups(app)
    val current = groups.firstOrNull { it.id == groupId }
    val all = remember(groupId, app.channels, app.favourites, app.hidden) { app.channelsIn(groupId) }
    val withGuide = remember(all, app.guide) { all.filter { c -> c.epgId.isNotEmpty() && !app.guide[c.epgId].isNullOrEmpty() } }
    // the filter only applies when there is guide data to filter by
    val list = if (guideOnly && withGuide.isNotEmpty()) withGuide else all
    // a 2-hour window you can move through the day the guide covers (2 h back, ~22 h ahead)
    val baseStart = remember { nowSec() / 1800 * 1800 }
    var shift by rememberSaveable { mutableStateOf(0) }
    val windowStart = baseStart + shift * 3600L
    val windowEnd = windowStart + 2 * 3600
    var focused by remember { mutableStateOf<Pair<Channel, Programme?>?>(null) }
    val first = remember { FocusRequester() }
    // Right past the window's end (or Left before its start) moves the window, then focus lands on the programme
    // airing at that time on the same channel
    var pending by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    val pendingFocus = remember { FocusRequester() }
    LaunchedEffect(pending) { if (pending != null) { delay(60); runCatching { pendingFocus.requestFocus() }; pending = null } }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(list.firstOrNull()?.id, windowFocused) { if (windowFocused) { delay(80); runCatching { first.requestFocus() } } }
    val phone = compact()

    val tomorrowShift = remember(baseStart) {
        val cal = Calendar.getInstance().apply { timeInMillis = baseStart * 1000; add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0) }
        ((cal.timeInMillis / 1000 - baseStart) / 3600).toInt().coerceAtMost(22)
    }
    val timeNav: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            GuidePill("◀", shift > -2) { shift = (shift - 2).coerceAtLeast(-2) }
            GuidePill("Now", true, selected = shift == 0) { shift = 0 }
            GuidePill("▶", shift < 22) { shift = (shift + 2).coerceAtMost(22) }
            GuidePill("Tomorrow", tomorrowShift > 2, selected = shift >= tomorrowShift - 1) { shift = tomorrowShift }
            // the tick only when it's really filtering (a provider without a full guide has nothing to filter by)
            val filtering = guideOnly && withGuide.isNotEmpty()
            GuidePill(if (filtering) "Guide only ✓" else "Guide only", withGuide.isNotEmpty(), selected = filtering) { guideOnly = !guideOnly }
        }
    }

    Column(Modifier.weight(1f).fillMaxHeight()) {
        val (fc, fp) = focused ?: (null to null)
        if (phone) Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("TV Guide", 26, Heavy, modifier = Modifier.padding(horizontal = 16.dp))
            CategoryButton(current?.name ?: "All channels", list.size) { drawer = true }
            Box(Modifier.padding(horizontal = 16.dp)) { timeNav() }
            T(listOfNotNull(fc?.name, fp?.let { "${fmtTime(it.start)} – ${fmtTime(it.end)} · ${it.title}" }).joinToString(" · ").ifEmpty { "Tap a programme to watch" }, 13, color = C.Muted, modifier = Modifier.padding(horizontal = 16.dp))
        } else Box(Modifier.fillMaxWidth().height(330.dp)) {
            // the focused channel's colour stands in for programme art, which guides don't carry
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(tone(fc?.id ?: 0).copy(alpha = 1f), C.Ground), radius = 1500f)))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(.25f to C.Ground.copy(alpha = .95f), .7f to C.Ground.copy(alpha = .3f))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.5f to Color.Transparent, 1f to C.Ground)))
            Column(Modifier.padding(start = 64.dp, top = 104.dp).widthIn(max = 700.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val live = fp != null && nowSec() in fp.start until fp.end
                T(listOfNotNull(fc?.name, fp?.let { "${fmtTime(it.start)} – ${fmtTime(it.end)}" }, fp?.takeIf { live }?.minutesLeft()).joinToString(" · "), 13, Semi, C.Muted)
                T(fp?.title ?: fc?.name ?: "TV Guide", 38, Heavy, maxLines = 1)
                T(fp?.description?.ifBlank { null } ?: if (fc != null && fp == null) "No programme information for this channel." else "", 14, color = C.Soft, maxLines = 2, lineHeight = 21)
                if (fc != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Actions(app, fc, fp, list) }
            }
            Column(Modifier.align(Alignment.TopEnd).padding(top = 96.dp, end = 40.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(300.dp, 169.dp).clip(RoundedCornerShape(18.dp)).background(C.Surface)) {
                    if (app.player.url != null) { PlayerSurface(app.player.player, Modifier.fillMaxSize()); LivePill(Modifier.padding(10.dp)) }
                    else T("Nothing playing", 13, Semi, C.Muted, Modifier.align(Alignment.Center))
                }
                timeNav()
            }
            Box(Modifier.align(Alignment.TopEnd).padding(top = 34.dp, end = 40.dp)) { Clock(20) }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val nameW = if (phone) 112.dp else 240.dp
            val gridW = maxWidth - nameW - (if (phone) 0.dp else 24.dp)
            val perSec = gridW / (windowEnd - windowStart).toFloat()
            Column {
                Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (phone) Box(Modifier.width(nameW))
                    else Box(Modifier.width(nameW).padding(start = 40.dp, end = 12.dp)) {
                        FocusSurface({ drawer = true }, Modifier.fillMaxWidth().height(36.dp), idle = C.Glass2, shape = RoundedCornerShape(18.dp), label = "Choose category") { f ->
                            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Ico(Icons.More, if (f) C.OnLight else C.Accent, 15.dp)
                                T(current?.name ?: "All channels", 13, Bold, fg(f), Modifier.weight(1f))
                            }
                        }
                    }
                    (0 until 4).forEach { T(fmtTime(windowStart + it * 1800L) + if (it == 0 && shift != 0) "  " + SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(windowStart * 1000)) else "", if (phone) 11 else 13, Semi, C.Muted, mono = true, modifier = Modifier.width(gridW / 4)) }
                }
                val rows = rememberLazyListState(app.guideScroll.first, app.guideScroll.second)
                DisposableEffect(Unit) { onDispose { app.guideScroll = rows.firstVisibleItemIndex to rows.firstVisibleItemScrollOffset } }
                // phones: a sideways swipe moves through time (an hour per swipe) instead of opening the row under the finger
                var swipe by remember { mutableFloatStateOf(0f) }
                LazyColumn(Modifier.then(if (phone) Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragStart = { swipe = 0f }, onDragEnd = {
                        if (swipe < -120f && shift < 22) shift += 1 else if (swipe > 120f && shift > -2) shift -= 1
                    }) { ch, d -> ch.consume(); swipe += d }
                } else Modifier), rows, contentPadding = PaddingValues(bottom = 32.dp)) {
                    itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                        LaunchedEffect(c.id) { app.loadEpg(c) }
                        val progs = app.programmesFor(c).orEmpty().filter { it.end > windowStart && it.start < windowEnd }.withoutOverlaps()
                        Row(Modifier.height(if (phone) 64.dp else 68.dp)) {
                            Row(Modifier.width(nameW).fillMaxHeight().padding(start = if (phone) 8.dp else 40.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (phone) 6.dp else 12.dp)) {
                                Logo(c.icon, c.name, false, Modifier.size(if (phone) 32.dp else 48.dp, if (phone) 24.dp else 34.dp))
                                Column {
                                    T(c.name, if (phone) 12 else 13, Bold, maxLines = 2)
                                    if (c.archive && !phone) T("Catch-up", 10, Bold, C.Accent)
                                }
                            }
                            Box(Modifier.width(gridW).fillMaxHeight().padding(vertical = 5.dp)) {
                                if (progs.isEmpty()) {
                                    FocusSurface({ watchLive(app, c, list) }, Modifier.fillMaxSize().padding(end = 6.dp), idle = Color(0x0FFFFFFF), shape = RoundedCornerShape(14.dp),
                                        focusRequester = if (i == 0) first else null, onFocus = { focused = c to null }) { f ->
                                        T("Live · no guide for this channel", 13, color = sub(f), modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp))
                                    }
                                }
                                val firstFocus = progs.indexOfFirst { nowSec() in it.start until it.end }.takeIf { it >= 0 } ?: 0
                                progs.forEachIndexed { j, p ->
                                    val s = maxOf(p.start, windowStart); val e = minOf(p.end, windowEnd)
                                    val target = pending?.takeIf { it.first == c.id && it.second in p.start until p.end }
                                    ProgrammeBlock(app, c, p, perSec * (s - windowStart).toFloat(), perSec * (e - s).toFloat() - 6.dp,
                                        when { target != null -> pendingFocus; i == 0 && j == firstFocus && pending == null -> first; else -> null }, { focused = c to p },
                                        onEdge = { right ->
                                            when {
                                                right && p.end >= windowEnd && shift < 22 -> { shift += 1; pending = c.id to maxOf(p.end, windowStart + 3600); true }
                                                !right && p.start <= windowStart && shift > -2 -> { shift -= 1; pending = c.id to (minOf(p.start, windowStart) - 1); true }
                                                else -> false
                                            }
                                        }) { act(app, c, p, list) }
                                }
                            }
                        }
                    }
                }
            }
            if (drawer) CategoryDrawer("TV Guide", groups, groupId, { groupId = it }, onHide = { hideLive(app, it, groupId) { g -> groupId = g } }, onMove = categoryMover(app, "live")) { drawer = false }
            // the now-line only when "now" is inside the window being shown
            if (nowSec() in windowStart until windowEnd) {
                val nowX = nameW + perSec * (nowSec() - windowStart).toFloat()
                Box(Modifier.offset(x = nowX).padding(top = 40.dp).width(2.dp).fillMaxHeight().background(LiveGradient))
            }
        }
    }
}

@Composable
private fun GuidePill(text: String, enabled: Boolean, selected: Boolean = false, onClick: () -> Unit) =
    FocusSurface({ if (enabled) onClick() }, Modifier.height(34.dp).alpha(if (enabled) 1f else .4f), selected = selected, idle = C.Glass2, shape = RoundedCornerShape(17.dp), lift = 1.06f) { f ->
        T(text, 13, Bold, if (f) C.OnLight else if (selected) C.Text else C.Soft, Modifier.align(Alignment.Center).padding(horizontal = 13.dp))
    }

/** One programme: past ones fade, the live one fills with its progress, catch-up and reminders are labelled. */
@Composable
private fun ProgrammeBlock(app: AppState, c: Channel, p: Programme, x: Dp, w: Dp, focusRequester: FocusRequester?, onFocus: () -> Unit,
                           onEdge: (right: Boolean) -> Boolean, onClick: () -> Unit) {
    val now = nowSec()
    val live = now in p.start until p.end
    val past = p.end <= now
    FocusSurface(onClick, Modifier.offset(x = x).width(w).fillMaxHeight().alpha(if (past) .5f else 1f)
        .onPreviewKeyEvent { e -> e.type == KeyEventType.KeyDown && (e.key == Key.DirectionRight || e.key == Key.DirectionLeft) && onEdge(e.key == Key.DirectionRight) },
        idle = Color(0x0FFFFFFF), shape = RoundedCornerShape(14.dp),
        focusRequester = focusRequester, onFocus = onFocus, lift = 1.04f) { f ->
        if (live && !f) Box(Modifier.fillMaxHeight().fillMaxWidth(p.progress()).background(Brush.horizontalGradient(listOf(C.Accent.copy(alpha = .16f), C.Coral.copy(alpha = .26f)))))
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(p.title, 14, Bold, fg(f))
            T(fmtTime(p.start), 11, Semi, sub(f), mono = true)
        }
        val tag = when { app.hasReminder(c, p) -> "REMINDER"; c.archive && !p.start.let { it > now } -> "CATCH-UP"; else -> null }
        if (tag != null && w > 120.dp) T(tag, 9, Heavy, if (f) C.OnLight else C.Accent, Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 10.dp))
    }
}

/** OK on a programme: live → watch; past on a catch-up channel → watch from its start; later → remind me. */
private fun act(app: AppState, c: Channel, p: Programme, list: List<Channel>) {
    val now = nowSec()
    when {
        p.start > now -> app.toggleReminder(c, p)
        p.end <= now && c.archive -> watchCatchup(app, c, p)
        else -> watchLive(app, c, list)
    }
}

@Composable
private fun Actions(app: AppState, c: Channel, p: Programme?, list: List<Channel>) {
    val now = nowSec()
    when {
        p == null || now in p.start until p.end -> {
            Btn("Watch live", { watchLive(app, c, list) }, style = BtnStyle.Primary, icon = Icons.Play, height = 46.dp)
            if (p != null && c.archive) Btn("From the start", { watchCatchup(app, c, p) }, height = 46.dp)
        }
        p.end <= now -> if (c.archive) Btn("Watch from the start", { watchCatchup(app, c, p) }, style = BtnStyle.Primary, icon = Icons.Play, height = 46.dp)
            else T("This channel has no catch-up", 13, Semi, C.Muted)
        else -> {
            Btn(if (app.hasReminder(c, p)) "Reminder set ✓" else "Remind me", { app.toggleReminder(c, p) }, style = BtnStyle.Primary, height = 46.dp)
            Btn("Watch live now", { watchLive(app, c, list) }, height = 46.dp)
        }
    }
}

private fun watchLive(app: AppState, c: Channel, list: List<Channel>) { app.player.play(app.liveUrl(c), title = c.name); app.push(Screen.LivePlayer(c, list)) }

/** Catch-up plays in the movie player, so you can pause and skip; it isn't added to Continue watching. */
private fun watchCatchup(app: AppState, c: Channel, p: Programme) =
    app.push(Screen.VodPlayer(PlayItem(app.catchupUrl(c, p), p.title, "${c.name} · ${fmtTime(p.start)}", c.icon, "cu${c.id}-${p.start}")))

/** Some guides list a channel's programmes twice (several regions); keep the first of any that overlap. */
internal fun List<Programme>.withoutOverlaps(): List<Programme> {
    var end = Long.MIN_VALUE
    return sortedBy { it.start }.filter { p -> (p.start >= end).also { if (it) end = p.end } }
}
