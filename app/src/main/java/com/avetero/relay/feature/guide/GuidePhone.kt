package com.avetero.relay.feature.guide

import com.avetero.relay.ui.motion.rememberEntrance
import com.avetero.relay.ui.motion.entrance
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.Move
import com.avetero.relay.data.model.Programme
import com.avetero.relay.data.model.minutesLeft
import com.avetero.relay.data.model.progress
import com.avetero.relay.ui.components.Group
import com.avetero.relay.ui.components.matching
import com.avetero.relay.ui.m3.ChannelLogo
import com.avetero.relay.ui.m3.LargeTitle
import com.avetero.relay.ui.m3.RelaySheet
import com.avetero.relay.ui.m3.PagerTabs
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.avetero.relay.ui.m3.segmentShape
import com.avetero.relay.util.fmtTime
import com.avetero.relay.util.nowSec
import java.util.Calendar

@Composable
internal fun GuidePhone(app: AppState, g: GuideState) {
    val ch = rememberGuideChannels(app, g)
    val list = ch.list
    var sheetFor by remember { mutableStateOf<Channel?>(null) }
    var categories by remember { mutableStateOf(false) }
    // the moment the list shows: now, or the start of the chosen hour
    val point = if (g.shift == 0) nowSec() else g.windowStart

    val rows = rememberLazyListState(app.guideScroll.first, app.guideScroll.second)
    val enter = rememberEntrance(ready = list.isNotEmpty())
    DisposableEffect(Unit) { onDispose { app.guideScroll = rows.firstVisibleItemIndex to rows.firstVisibleItemScrollOffset } }
    val reselect0 = remember { app.reselect }
    LaunchedEffect(app.reselect) { if (app.reselect != reselect0) rows.animateScrollToItem(0) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitle("Guide", Modifier.padding(end = 8.dp)) {
            OutlinedButton({ categories = true }, Modifier.padding(end = 8.dp), contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Text(g.current?.name ?: "All channels", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                Icon(Icons.Rounded.ArrowDropDown, "Choose category", Modifier.size(18.dp))
            }
        }
        TimeChips(g, ch)
        // a sideways swipe moves through time (an hour per swipe) instead of opening the row under the finger
        var swipe by remember { mutableFloatStateOf(0f) }
        LazyColumn(Modifier.fillMaxSize().pointerInput(Unit) {
            detectHorizontalDragGestures(onDragStart = { swipe = 0f }, onDragEnd = { if (swipe < -120f) g.move(1) else if (swipe > 120f) g.move(-1) }) { c, d -> c.consume(); swipe += d }
        }, rows, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(list, key = { _, c -> c.id }) { i, c -> GuideRow(app, c, point, g.shift == 0, i, list.size, Modifier.animateItem().entrance(enter, i)) { sheetFor = c } }
        }
    }

    sheetFor?.let { c -> ChannelSheet(app, c, list, point) { sheetFor = null } }
    if (categories) CategorySheet(g.groups, g.groupId, { g.groupId = it }, g::moveCategory, g::hide) { categories = false }
}


/** Now · the next couple of hours · Tonight · Tomorrow (and wherever a swipe took you). */
@Composable
private fun TimeChips(g: GuideState, ch: GuideChannels) {
    val shifts = remember(g.shift, g.tonightShift, g.tomorrowShift) { (listOf(0, 1, 2, g.shift) + listOfNotNull(g.tonightShift, g.tomorrowShift)).distinct().sorted() }
    val state = rememberLazyListState()
    LaunchedEffect(g.shift) { state.animateScrollToItem((shifts.indexOf(g.shift) - 1).coerceAtLeast(0)) }
    LazyRow(state = state, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(shifts, key = { it }) { s ->
            val label = when (s) { 0 -> "Now"; g.tonightShift -> "Tonight"; g.tomorrowShift -> "Tomorrow"; else -> fmtTime(g.baseStart + s * 3600L) }
            Chip(label, g.shift == s, Modifier.animateItem()) { g.shift = s }
        }
        // the tick only when it's really filtering (a provider without a full guide has nothing to filter by)
        if (ch.canFilter) item(key = "guideOnly") { Chip("Guide only", ch.filtering) { g.guideOnly = !g.guideOnly } }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) =
    FilterChip(selected, onClick, { Text(label) }, modifier,
        leadingIcon = if (selected) { { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null)

/** One channel: what's on at [point] with its progress when live, and what's next. Tap for the schedule. */
@Composable
private fun GuideRow(app: AppState, c: Channel, point: Long, live: Boolean, i: Int, count: Int, modifier: Modifier, onClick: () -> Unit) {
    LaunchedEffect(c.id) { app.loadEpg(c) }
    val progs = rememberProgrammes(app, c)
    val at = remember(progs, point) { progs.at(point) }
    val next = remember(progs, point) { progs.firstOrNull { it.start >= (at?.end ?: point) } }
    Row(modifier.fillMaxWidth().heightIn(min = 88.dp).clip(segmentShape(i, count)).background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(onClickLabel = "Schedule for ${c.name}", onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ChannelLogo(c.icon, c.name, playing = app.liveChannel?.id == c.id)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(listOfNotNull(c.num.takeIf { it > 0 }?.toString(), c.name).joinToString(" · ") + if (c.archive) " · Catch-up" else "",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(at?.title ?: if (progs.isEmpty()) "No guide for this channel" else "Nothing listed", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (at != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (live) {
                    LinearWavyProgressIndicator({ at.progress() }, Modifier.width(120.dp))
                    Text(at.minutesLeft(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Text("${fmtTime(at.start)} – ${fmtTime(at.end)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (app.hasReminder(c, at)) Text("· Reminder set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (next != null) Text("Next ${fmtTime(next.start)} · ${next.title}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A channel's schedule: Watch live, save the channel, then Today/Tomorrow with catch-up, from the start, and reminders. */
@Composable
private fun ChannelSheet(app: AppState, c: Channel, list: List<Channel>, point: Long, onDismiss: () -> Unit) = RelaySheet(onDismiss) {
    val progs = rememberProgrammes(app, c)
    val today = remember { Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis / 1000 }
    val tomorrow = today + 86_400
    val now = nowSec()

    Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ChannelLogo(c.icon, c.name, size = 56.dp, playing = app.liveChannel?.id == c.id)
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(c.num.takeIf { it > 0 }?.let { "Channel $it" }, "Catch-up".takeIf { c.archive }).joinToString(" · ").ifEmpty { "Live" },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Row(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        Button({ onDismiss(); watchLive(app, c, list) }, Modifier.weight(1f).heightIn(min = 48.dp), shape = ButtonGroupDefaults.connectedLeadingButtonShape) { Text("Watch live now") }
        val fav = app.isFav("c${c.id}")
        FilledTonalToggleButton(fav, { app.toggleFav("c${c.id}") }, Modifier.heightIn(min = 48.dp), shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            icon = { Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline, null, Modifier.size(18.dp)) }) { Text(if (fav) "Saved" else "Save") }
    }
    // Today | Tomorrow as tabs you can swipe between; each day's list opens on what's on now (one row of the past above it)
    val days = if (progs.any { it.end > tomorrow }) 2 else 1
    val pager = rememberPagerState(if (point >= tomorrow && days == 2) 1 else 0) { days }
    PagerTabs(pager, listOf("Today", "Tomorrow").take(days), Modifier.padding(bottom = 12.dp), container = MaterialTheme.colorScheme.surfaceContainerLow)
    HorizontalPager(pager, Modifier.weight(1f, fill = false), verticalAlignment = Alignment.Top) { day ->
        val shown = remember(progs, day) { val from = today + day * 86_400L; progs.filter { it.end > from && it.start < from + 86_400 } }
        if (shown.isEmpty()) Text(if (progs.isEmpty()) "No guide for this channel." else "Nothing listed for this day.", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp))
        val state = rememberLazyListState(remember(shown) { (shown.indexOfFirst { it.end > now } - 1).coerceAtLeast(0) })
        LazyColumn(state = state, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(shown, key = { _, p -> p.start }) { i, p -> ScheduleRow(app, c, p, list, i, shown.size, now, onDismiss) }
        }
    }
}

@Composable
private fun ScheduleRow(app: AppState, c: Channel, p: Programme, list: List<Channel>, i: Int, count: Int, now: Long, dismiss: () -> Unit) {
    val onNow = now >= p.start && now < p.end
    val past = p.end <= now
    val mins = (p.end - p.start) / 60
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(segmentShape(i, count)).background(if (onNow) cs.secondaryContainer else cs.surfaceContainer)
        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(fmtTime(p.start), style = MaterialTheme.typography.titleSmall, color = if (onNow) cs.onSecondaryContainer else cs.onSurfaceVariant, modifier = Modifier.width(52.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(when { onNow -> "On now · ${p.minutesLeft()}"; past && c.archive -> "Catch-up · $mins min"; else -> "$mins min" },
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when {
            onNow && c.archive -> Button({ dismiss(); watchCatchup(app, c, p) }) { Text("From the start") }
            onNow -> Button({ dismiss(); watchLive(app, c, list) }) { Text("Watch") }
            past && c.archive -> FilledTonalButton({ dismiss(); watchCatchup(app, c, p) }) { Text("Watch") }
            past -> Unit
            app.hasReminder(c, p) -> FilledTonalButton({ app.toggleReminder(c, p) },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = cs.secondaryContainer, contentColor = cs.onSecondaryContainer)) {
                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)); Text("Reminder set", Modifier.padding(start = 6.dp))
            }
            else -> FilledTonalButton({ app.toggleReminder(c, p) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = cs.surfaceContainerHighest, contentColor = cs.onSurface)) { Text("Remind me") }
        }
    }
}

/** Categories (Guide and Live): tap to show one; press and hold one of the provider's to move or hide it. */
@Composable
fun CategorySheet(groups: List<Group>, current: String, onPick: (String) -> Unit, onMove: (Group, Move?) -> Unit, onHide: (Group) -> Unit, onDismiss: () -> Unit) = RelaySheet(onDismiss) {
    var q by remember { mutableStateOf("") }
    var hiding by remember { mutableStateOf<Group?>(null) }
    val shown = remember(groups, q) { groups.matching(q) }
    Text("Categories", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    if (groups.size > 8) OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), placeholder = { Text("Find a category") }, singleLine = true)
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(shown, key = { _, it -> it.id }) { i, grp ->
            var menu by remember { mutableStateOf(false) }
            val on = grp.id == current
            val cs = MaterialTheme.colorScheme
            Box(Modifier.animateItem()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(segmentShape(i, shown.size)).background(if (on) cs.secondaryContainer else cs.surfaceContainer)
                    .combinedClickable(onLongClick = if (grp.id.startsWith("__")) null else ({ menu = true }), onLongClickLabel = "Move or hide") { onPick(grp.id); onDismiss() }
                    .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (on) Icon(Icons.Rounded.Check, null, tint = cs.onSecondaryContainer)
                    Text(grp.name, style = MaterialTheme.typography.bodyLarge, color = if (on) cs.onSecondaryContainer else cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (grp.count > 0) Text("%,d".format(grp.count), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                DropdownMenu(menu, { menu = false }) {
                    listOf("Move to top" to Move.Top, "Move up one" to Move.Up, "Move down one" to Move.Down, "Move to bottom" to Move.Bottom, "Reorder several…" to null).forEach { (label, m) ->
                        DropdownMenuItem({ Text(label) }, { menu = false; if (m == null) onDismiss(); onMove(grp, m) })
                    }
                    DropdownMenuItem({ Text("Hide category") }, { menu = false; hiding = grp })
                }
            }
        }
    }
    hiding?.let { grp ->
        AlertDialog({ hiding = null }, confirmButton = { TextButton({ onHide(grp); hiding = null }) { Text("Hide") } },
            dismissButton = { TextButton({ hiding = null }) { Text("Cancel") } },
            title = { Text("Hide “${grp.name}”?") }, text = { Text("It won't appear in lists, All, the guide or search. Bring it back in Settings → Manage categories.") })
    }
}
