package com.avetero.relay.feature.categories

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.data.model.Category
import com.avetero.relay.data.model.Move
import com.avetero.relay.data.model.moveIds
import com.avetero.relay.feature.settings.Title
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.components.Chip
import com.avetero.relay.ui.components.Chips
import com.avetero.relay.ui.components.ConfirmDialog
import com.avetero.relay.ui.components.FilterField
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.KeyHint
import com.avetero.relay.ui.components.Label
import com.avetero.relay.ui.components.PanelBtn
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Ico
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import kotlinx.coroutines.delay

/**
 * Board 20, Settings → Manage categories: order and visibility for Live TV, Movies and Series categories in one place.
 * Tick some (or filter and tick all listed), then move, hide or show them together.
 * TV: actions sit in a panel to the right; "Move with remote" lifts the ticked categories into one block that
 * Up/Down (and Channel up/down) carry through the list, OK drops, Back cancels. Phones get a bottom sheet instead.
 */
@Composable
fun CategoriesScreen(app: AppState, initialType: String, modifier: Modifier) {
    var type by rememberSaveable(initialType) { mutableStateOf(initialType) }
    var q by rememberSaveable { mutableStateOf("") }
    var exclude by rememberSaveable { mutableStateOf(false) }
    var picked by remember(type) { mutableStateOf(emptySet<String>()) }
    // move mode: the order before moving (for Back) and where the ticked ones were
    var moving by remember(type) { mutableStateOf(false) }
    var before by remember(type) { mutableStateOf(emptyList<String>()) }
    var resetAsk by remember { mutableStateOf(false) }
    // focus has to stay inside this screen while moving, and come back to the moved rows afterwards
    val dropFocus = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    var anchor by remember(type) { mutableStateOf<String?>(null) }
    val phone = compact()

    val cats = app.ordered(type)
    val position = remember(cats) { cats.withIndex().associate { (i, c) -> c.id to i + 1 } }
    val counts = remember(type, app.channels, app.movies, app.series) {
        when (type) {
            "vod" -> app.movies.groupingBy { it.categoryId }.eachCount()
            "series" -> app.series.groupingBy { it.categoryId }.eachCount()
            else -> app.channels.groupingBy { it.categoryId }.eachCount()
        }
    }
    val isHidden = { c: Category -> "$type:${c.id}" in app.hidden }
    val shown = remember(cats, q, exclude, moving, app.hidden) {
        if (moving) cats.filterNot(isHidden) else filterCats(cats, q, exclude)
    }
    val pickedCats = cats.filter { it.id in picked }
    val listState = rememberLazyListState()

    fun startMove() {
        picked = picked.filterNot { "$type:$it" in app.hidden }.toSet() // hidden ones keep their slots
        if (picked.isEmpty()) return
        val ids = cats.map { it.id }
        before = ids
        // gather the ticked ones at the first one's place
        val at = ids.indexOfFirst { it in picked }
        val rest = ids.filter { it !in picked }
        val insert = ids.take(at).count { it !in picked }
        app.setCategoryOrder(type, rest.take(insert) + ids.filter { it in picked } + rest.drop(insert))
        moving = true
    }
    fun step(how: Move, times: Int = 1) {
        var ids = app.ordered(type).filterNot(isHidden).map { it.id }
        repeat(times) { ids = moveIds(ids, picked, how) }
        // hidden ones keep their slots: rebuild the full order around them
        val full = app.ordered(type).map { it.id }
        val it = ids.iterator()
        app.setCategoryOrder(type, full.map { id -> if ("$type:$id" in app.hidden) id else it.next() })
    }
    fun finish() { anchor = cats.firstOrNull { it.id in picked }?.id; moving = false }
    fun drop() { finish(); picked = emptySet(); app.message = "Order saved" }
    fun cancel() { app.setCategoryOrder(type, before); finish() }
    fun setHidden(hide: Boolean) {
        app.setCategoriesHidden(picked.map { "$type:$it" }, hide)
        app.message = "${if (hide) "Hid" else "Showing"} ${picked.size} ${if (picked.size == 1) "category" else "categories"}"
    }
    fun move(how: Move) { app.moveCategories(type, picked, how); app.message = if (how == Move.Top) "Moved to the top" else "Moved to the bottom" }

    BackHandler(moving) { cancel() }
    // while moving, the remote drives the block wherever focus happens to be (Back is left to BackHandler)
    DisposableEffect(moving) {
        if (moving) app.keyOverride = { e ->
            if (e.action == KeyEvent.ACTION_DOWN) when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> step(Move.Up)
                KeyEvent.KEYCODE_DPAD_DOWN -> step(Move.Down)
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> step(Move.Up, 10)
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> step(Move.Down, 10)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (e.repeatCount == 0) drop()
            }
            e.keyCode != KeyEvent.KEYCODE_BACK
        }
        onDispose { app.keyOverride = null }
    }
    LaunchedEffect(moving) {
        if (moving) runCatching { dropFocus.requestFocus() }
        else if (anchor != null) { delay(150); runCatching { rowFocus.requestFocus() }; anchor = null }
    }
    // keep the moving block (or the first ticked row after a move) in view
    LaunchedEffect(app.orders[type], moving) {
        val i = shown.indexOfFirst { it.id in picked }
        if (i >= 0 && i !in listState.layoutInfo.visibleItemsInfo.map { it.index }.dropLast(1)) listState.animateScrollToItem((i - 2).coerceAtLeast(0))
    }
    if (resetAsk) ConfirmDialog("Reset to provider order?", "Reset", { app.moveCategories(type, emptySet(), null); picked = emptySet(); resetAsk = false }, { resetAsk = false },
        message = "Your custom order for this type is cleared and categories go back to the order your provider sends.")

    // positions count every category, hidden ones included, so they match the row numbers
    val range = cats.filter { it.id in picked }.mapNotNull { position[it.id] }.let { if (it.isEmpty()) "" else if (it.size == 1) "${it[0]}" else "${it.first()}–${it.last()}" }
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("Manage categories")
            Spacer(Modifier.weight(1f))
            val typeName = when (type) { "vod" -> "Movies"; "series" -> "Series"; else -> "Live TV" }
            T("$typeName · ${"%,d".format(cats.count(isHidden))} hidden · ", 13, color = C.Muted)
            T(if (moving) "Moving ${picked.size}" else if (app.orders[type].isNullOrEmpty()) "Provider order" else "Custom order", 13, Bold, C.Accent)
        }
        if (moving) MoveBanner(picked.size, range)
        else if (phone) {
            TypeTabs(type) { type = it; q = "" }
            FilterField(q, { q = it }, "Filter, e.g. sky, epl")
            ExcludeTabs(exclude) { exclude = it }
        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TypeTabs(type) { type = it; q = "" }
            Box(Modifier.weight(1f)) { FilterField(q, { q = it }, "Filter, e.g. sky, epl — several, separated by commas") }
            ExcludeTabs(exclude) { exclude = it }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Label("#", Modifier.width(if (phone) 26.dp else 34.dp)); Spacer(Modifier.width(22.dp))
                    Label(if (moving) "Category · full list while moving" else "Category · ${"%,d".format(shown.size)} of ${"%,d".format(cats.size)} listed", Modifier.weight(1f))
                    Label(if (type == "live") "Channels" else "Titles")
                }
                LazyColumn(Modifier.weight(1f), listState, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(shown, key = { it.id }) { c ->
                        val on = c.id in picked
                        OrderRow(c, position[c.id] ?: 0, counts[c.id] ?: 0, on, isHidden(c), moving && on, phone, Modifier.animateItem(), if (c.id == anchor) rowFocus else null) {
                            if (!moving) picked = if (on) picked - c.id else picked + c.id
                        }
                    }
                }
                if (!phone) Row(Modifier.padding(bottom = 18.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (moving) T("Hidden categories keep their place but don't show while moving.", 12, color = C.Dim)
                    else { KeyHint("OK", "Tick"); KeyHint("▶", "Actions"); KeyHint("Back", "Done") }
                }
            }
            if (!phone) ActionPanel(
                moving, pickedCats, range, picked.mapNotNull { id -> before.indexOf(id).takeIf { it >= 0 }?.plus(1) }.sorted(), shown.size, !app.orders[type].isNullOrEmpty(),
                onRemote = ::startMove, onTop = { if (moving) { step(Move.Top); drop() } else move(Move.Top) }, onBottom = { move(Move.Bottom) },
                onTickAll = { picked = picked + shown.map { it.id } }, onClear = { picked = emptySet() }, onHide = { setHidden(it) },
                onReset = { resetAsk = true }, onDrop = ::drop, onCancel = ::cancel, dropFocus = dropFocus,
            )
        }
        if (phone && picked.isNotEmpty()) PhoneSheet(pickedCats, shown.size,
            onMove = { how -> app.moveCategories(type, picked, how) }, onTickAll = { picked = picked + shown.map { it.id } },
            onClear = { picked = emptySet() }, onHide = { setHidden(it) }, onReset = { resetAsk = true })
    }
}

@Composable
private fun OrderRow(c: Category, pos: Int, count: Int, ticked: Boolean, hidden: Boolean, lifted: Boolean, phone: Boolean, modifier: Modifier,
                     focusRequester: FocusRequester?, onClick: () -> Unit) =
    FocusSurface(onClick, modifier.fillMaxWidth().height(if (phone) 52.dp else 50.dp).then(if (lifted) Modifier.shadow(12.dp, RoundedCornerShape(10.dp)) else Modifier),
        selected = ticked, idle = if (lifted) C.Accent else Color.Transparent, shape = RoundedCornerShape(10.dp), focusRequester = focusRequester) { f ->
        val onAccent = lifted && !f
        val sub = if (f || onAccent) C.OnLightSub else C.Dim
        Box(Modifier.fillMaxSize().then(if (lifted) Modifier.background(if (f) Color.Transparent else C.Accent) else Modifier))
        Row(Modifier.fillMaxSize().padding(horizontal = if (phone) 12.dp else 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 14.dp)) {
            T("$pos", 13, color = sub, mono = true, modifier = Modifier.width(if (phone) 26.dp else 34.dp))
            if (lifted) Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { Ico(Icons.Grip, C.OnLight, 16.dp) }
            else Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(when { ticked && f -> C.OnLight; ticked -> C.Accent; f -> C.LightTrack; else -> C.Track }), contentAlignment = Alignment.Center) {
                if (ticked) Ico(Icons.Check, if (f) C.Accent else C.OnLight, 14.dp)
            }
            T(c.name, 15, Bold, when { f || onAccent -> C.OnLight; hidden -> C.Muted; else -> C.Text }, Modifier.weight(1f))
            if (hidden) Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (f) C.LightTrack else C.Control).padding(horizontal = 8.dp, vertical = 3.dp)) { T("Hidden", 11, Bold, sub) }
            T("%,d".format(count), 13, color = sub, mono = true)
        }
    }

@Composable
private fun MoveBanner(n: Int, range: String) =
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Accent).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Ico(Icons.Move, C.OnLight, 20.dp)
        T("Moving $n ${if (n == 1) "category" else "categories"} · now at $range", 15, Heavy, C.OnLight, Modifier.weight(1f))
        if (!compact()) listOf("▲▼" to "One row", "CH±" to "One page", "OK" to "Drop", "Back" to "Cancel").forEach { (k, v) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x240B0E14)).padding(horizontal = 7.dp, vertical = 3.dp)) { T(k, 12, Heavy, C.OnLight, mono = true) }
                T(v, 13, color = C.OnLight)
            }
        }
    }

/** The TV panel to the right of the list: what's ticked, then what to do with it. */
@Composable
private fun ActionPanel(
    moving: Boolean, picked: List<Category>, range: String, was: List<Int>, listed: Int, custom: Boolean,
    onRemote: () -> Unit, onTop: () -> Unit, onBottom: () -> Unit, onTickAll: () -> Unit, onClear: () -> Unit, onHide: (Boolean) -> Unit, onReset: () -> Unit,
    onDrop: () -> Unit, onCancel: () -> Unit, dropFocus: FocusRequester,
) = Column(Modifier.width(280.dp).padding(bottom = 20.dp).clip(RoundedCornerShape(16.dp)).background(C.Surface)
    .verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    val any = picked.isNotEmpty()
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        T(if (moving) range else "${picked.size}", 38, Heavy, lineHeight = 40)
        T(if (moving) "new place" else "ticked", 14, color = C.Muted, modifier = Modifier.padding(bottom = 6.dp))
    }
    if (moving) Label("Was")
    if (any) Chips(if (moving) was.take(6).map { "#$it" } else picked.map { it.name }.take(3), if (moving) was.size - 6 else picked.size - 3)
    else T("Tick categories with OK, or filter and tick everything listed.", 13, color = C.Muted, maxLines = 3, lineHeight = 19)
    if (moving) {
        Spacer(Modifier.height(2.dp).fillMaxWidth().background(C.Control))
        PanelBtn("Drop here", Icons.Check, onDrop, key = "OK", style = BtnStyle.Light, focusRequester = dropFocus)
        PanelBtn("Send to top", Icons.Top, onTop)
        PanelBtn("Cancel move", null, onCancel, quiet = true)
    } else {
        Label("Move ticked", Modifier.padding(top = 6.dp))
        PanelBtn("Move with remote", Icons.Move, onRemote, key = "▲▼", style = BtnStyle.Primary, enabled = any)
        PanelBtn("Move to top", Icons.Top, onTop, enabled = any)
        PanelBtn("Move to bottom", Icons.Bottom, onBottom, enabled = any)
        Label("Show or hide ticked", Modifier.padding(top = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { PanelBtn("Hide", Icons.EyeOff, { onHide(true) }, enabled = any) }
            Box(Modifier.weight(1f)) { PanelBtn("Show", Icons.Eye, { onHide(false) }, enabled = any) }
        }
        Spacer(Modifier.height(1.dp).fillMaxWidth().background(C.Control))
        PanelBtn("Tick all ${"%,d".format(listed)} listed", null, onTickAll, quiet = true, enabled = listed > 0)
        PanelBtn("Clear ticks", null, onClear, quiet = true, enabled = any)
        PanelBtn("Reset to provider order", null, onReset, quiet = true, danger = true, enabled = custom)
    }
}

/** Phones: ticking anything raises this sheet with the moves as big targets. */
@Composable
private fun PhoneSheet(picked: List<Category>, listed: Int, onMove: (Move) -> Unit, onTickAll: () -> Unit, onClear: () -> Unit, onHide: (Boolean) -> Unit, onReset: () -> Unit) =
    Column(Modifier.fillMaxWidth().padding(horizontal = 0.dp).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(C.Surface)
        .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            T("${picked.size} ticked", 18, Heavy)
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                picked.take(4).forEach { Chip(it.name) }
            }
            FocusSurface(onClear, Modifier.height(32.dp), shape = RoundedCornerShape(8.dp)) { f -> T("Clear", 14, Bold, if (f) C.OnLight else C.Muted, Modifier.align(Alignment.Center).padding(horizontal = 8.dp)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(Move.Top, Icons.Top, "Top"), Triple(Move.Up, Icons.Up, "Up"), Triple(Move.Down, Icons.Down, "Down"), Triple(Move.Bottom, Icons.Bottom, "Bottom")).forEach { (m, icon, label) ->
                val primary = m == Move.Top
                FocusSurface({ onMove(m) }, Modifier.weight(1f).height(58.dp), idle = if (primary) C.Accent else C.Control, shape = RoundedCornerShape(12.dp)) { f ->
                    val color = if (f || primary) C.OnLight else C.Soft
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Ico(icon, color, 20.dp); T(label, 11, Bold, color)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Hide", { onHide(true) }, Modifier.weight(1f), icon = Icons.EyeOff, height = 44.dp)
            Btn("Show", { onHide(false) }, Modifier.weight(1f), icon = Icons.Eye, height = 44.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusSurface(onTickAll, Modifier.height(32.dp), shape = RoundedCornerShape(8.dp)) { f -> T("Tick all ${"%,d".format(listed)} listed", 14, Bold, if (f) C.OnLight else C.Muted, Modifier.align(Alignment.Center).padding(horizontal = 6.dp)) }
            Spacer(Modifier.weight(1f))
            FocusSurface(onReset, Modifier.height(32.dp), shape = RoundedCornerShape(8.dp)) { f -> T("Reset order", 14, Bold, if (f) C.OnLight else C.Error, Modifier.align(Alignment.Center).padding(horizontal = 6.dp)) }
        }
    }
