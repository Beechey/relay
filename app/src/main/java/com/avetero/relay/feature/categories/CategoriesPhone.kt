package com.avetero.relay.feature.categories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.avetero.relay.app.AppState
import com.avetero.relay.data.model.Category
import com.avetero.relay.data.model.Move
import com.avetero.relay.feature.settings.Confirm
import com.avetero.relay.feature.settings.Segmented
import com.avetero.relay.ui.components.LocalParts
import com.avetero.relay.ui.m3.RelaySheet
import com.avetero.relay.ui.m3.RelayTopBar
import com.avetero.relay.ui.m3.segmentShape

/** Settings → Manage categories on phones: a pushed screen. */
@Composable
fun CategoriesPhone(app: AppState, initialType: String) {
    val m = remember(initialType) { CategoriesModel(app, initialType) }
    var resetAsk by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        // the same large, collapsing title as the other settings pages
        val scroll = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        com.avetero.relay.ui.m3.RelayLargeTopBar("Categories", { app.back() }, scroll, actions = { TextButton({ resetAsk = true }, enabled = m.custom) { Text("Reset order") } })
        CategoriesBody(m, Modifier.weight(1f).nestedScroll(scroll.nestedScrollConnection))
    }
    if (resetAsk) ResetConfirm(m) { resetAsk = false }
}

/** The same, as a sheet over Live TV, Movies or Series. */
@Composable
fun CategoriesSheet(app: AppState, initialType: String, onDismiss: () -> Unit) = RelaySheet(onDismiss) {
    val m = remember(initialType) { CategoriesModel(app, initialType) }
    var resetAsk by remember { mutableStateOf(false) }
    Row(Modifier.padding(start = 24.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Categories", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton({ resetAsk = true }, enabled = m.custom) { Text("Reset order") }
    }
    CategoriesBody(m, Modifier.fillMaxHeight(0.85f))
    if (resetAsk) ResetConfirm(m) { resetAsk = false }
}

@Composable
internal fun ResetConfirm(m: CategoriesModel, onDone: () -> Unit) =
    Confirm("Reset to provider order?", "Your custom order for this type is cleared and categories go back to the order your provider sends.", "Reset",
        { m.reset(); onDone() }, onDone)

@Composable
private fun CategoriesBody(m: CategoriesModel, modifier: Modifier) = Box(modifier.fillMaxWidth()) {
    val cs = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    // drag to reorder: the order while dragging, the dragged row and how far it is from its slot
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOrder by remember { mutableStateOf<List<Category>?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    fun dragMove() {
        val id = dragging ?: return
        val order = dragOrder ?: return
        val items = list.layoutInfo.visibleItemsInfo
        val cur = items.firstOrNull { it.key == id } ?: return
        val mid = (cur.offset + dragDy + cur.size / 2).toInt()
        val target = items.firstOrNull { it.key != id && it.key is String && mid in it.offset until it.offset + it.size } ?: return
        val from = order.indexOfFirst { it.id == id }
        val to = order.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return
        dragOrder = order.toMutableList().apply { add(to, removeAt(from)) }
        // the row now lives in the target's slot; keep it under the finger
        dragDy += cur.offset - target.offset
    }
    fun dragEnd() {
        dragOrder?.let { o -> if (o.map { it.id } != m.shown.map { it.id }) m.reorderShown(o.map { it.id }) }
        dragging = null; dragOrder = null; dragDy = 0f
    }
    val rows = dragOrder ?: m.shown
    Column(Modifier.fillMaxSize()) {
        val parts = LocalParts.current
        Segmented(catTypes.filter { it.first in parts }.map { it.second }, catTypes.first { it.first == m.type }.second,
            { n -> m.pickType(catTypes.first { it.second == n }.first) }, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        TextField(m.q, { m.q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
            placeholder = { Text("Filter, e.g. sky, epl") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (m.q.isNotEmpty()) IconButton({ m.q = "" }) { Icon(Icons.Rounded.Close, "Clear filter") } },
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh))
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to "Containing", true to "Not containing").forEach { (ex, label) ->
                FilterChip(m.exclude == ex, { m.exclude = ex }, { Text(label) },
                    leadingIcon = if (m.exclude == ex) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null)
            }
            Spacer(Modifier.weight(1f))
            TextButton(m::tickAll, enabled = rows.isNotEmpty()) { Text("Tick all") }
        }
        Text("%,d shown · %,d hidden · %s".format(rows.size, m.hiddenCount, if (m.custom) "custom order" else "provider order"),
            style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        // shortcut: no auto-scroll while dragging past the edge; To top / To bottom cover long moves
        LazyColumn(Modifier.weight(1f), list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (m.picked.isEmpty()) 24.dp else 96.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(rows, key = { _, c -> c.id }) { i, c ->
                val lifted = c.id == dragging
                val hidden = m.isHidden(c)
                val ticked = c.id in m.picked
                Surface({ m.tick(c.id) },
                    (if (lifted) Modifier.zIndex(1f).graphicsLayer { translationY = dragDy } else Modifier.animateItem()).fillMaxWidth().height(56.dp),
                    shape = if (lifted) RoundedCornerShape(16.dp) else segmentShape(i, rows.size),
                    color = if (lifted) cs.surfaceContainerHighest else cs.surfaceContainerHigh, shadowElevation = if (lifted) 6.dp else 0.dp) {
                    Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Checkbox(ticked, { m.tick(c.id) })
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, color = if (hidden) cs.outline else cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        // a hidden category may not be downloaded at all; no count beats a misleading 0
                        val n = m.counts[c.id] ?: 0
                        if (n > 0 || !hidden) Text("%,d".format(n), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        IconButton({ m.setHidden(!hidden, c) }) {
                            Icon(if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (hidden) "Show ${c.name}" else "Hide ${c.name}",
                                tint = if (hidden) cs.outline else cs.onSurfaceVariant)
                        }
                        Box(Modifier.size(48.dp).pointerInput(c.id) {
                            detectVerticalDragGestures(
                                onDragStart = { dragging = c.id; dragOrder = m.shown; dragDy = 0f },
                                onDragEnd = { dragEnd() }, onDragCancel = { dragEnd() },
                            ) { change, dy -> change.consume(); dragDy += dy; dragMove() }
                        }, Alignment.Center) { Icon(Icons.Rounded.DragHandle, "Drag to reorder ${c.name}", tint = cs.outline) }
                    }
                }
            }
        }
    }
    AnimatedVisibility(m.picked.isNotEmpty(), Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        TickedBar(m)
    }
}

/** What to do with the ticked categories: a floating toolbar. */
@Composable
private fun TickedBar(m: CategoriesModel) {
    val allHidden = m.pickedCats.all(m::isHidden)
    var menu by remember { mutableStateOf(false) }
    Surface(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp).fillMaxWidth().height(64.dp), shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 6.dp) {
        Row(Modifier.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${m.picked.size} ticked", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
            val small = PaddingValues(horizontal = 8.dp)
            TextButton({ m.move(Move.Top) }, contentPadding = small) { Text("To top") }
            TextButton({ m.move(Move.Bottom) }, contentPadding = small) { Text("To bottom") }
            Button({ m.setHidden(!allHidden) }, contentPadding = PaddingValues(horizontal = 16.dp)) { Text(if (allHidden) "Show" else "Hide") }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Move up one") }, { menu = false; m.move(Move.Up) })
                    DropdownMenuItem({ Text("Move down one") }, { menu = false; m.move(Move.Down) })
                    DropdownMenuItem({ Text(if (allHidden) "Hide ticked" else "Show ticked") }, { menu = false; m.setHidden(allHidden) })
                    DropdownMenuItem({ Text("Clear ticks") }, { menu = false; m.clear() })
                }
            }
        }
    }
}
