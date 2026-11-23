package com.avetero.relay.feature.categories

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Checkbox
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.avetero.relay.app.AppState
import com.avetero.relay.data.model.Category
import com.avetero.relay.data.model.Move
import com.avetero.relay.ui.components.LocalParts
import com.avetero.relay.ui.components.TvEditable
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.m3.segmentShape
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvHints
import com.avetero.relay.ui.tv.TvLift
import com.avetero.relay.ui.tv.TvSidePanel
import com.avetero.relay.ui.tv.TvSurface
import kotlinx.coroutines.delay

/**
 * Settings → Manage categories on TV, full screen. OK ticks; the bar above the list acts on the ticked ones.
 * "Move with remote" lifts them into one block that Up/Down (and Channel up/down) carry, OK drops, Back cancels.
 */
@Composable
fun CategoriesTv(app: AppState, initialType: String) {
    val m = remember(initialType) { CategoriesModel(app, initialType) }
    val cs = MaterialTheme.colorScheme
    var resetAsk by remember { mutableStateOf(false) }
    // side panel: "move" or "hide"; the last one stays for the closing animation
    var panel by remember { mutableStateOf<String?>(null) }
    var panelKind by remember { mutableStateOf("move") }
    val start = remember { FocusRequester() }
    val dropFocus = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { start.settle() }

    BackHandler(m.moving) { m.cancel() }
    // while moving, the remote drives the block wherever focus happens to be (Back is left to BackHandler)
    DisposableEffect(m.moving) {
        if (m.moving) app.keyOverride = { e ->
            if (e.action == KeyEvent.ACTION_DOWN) when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> m.step(Move.Up)
                KeyEvent.KEYCODE_DPAD_DOWN -> m.step(Move.Down)
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> m.step(Move.Up, 10)
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> m.step(Move.Down, 10)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (e.repeatCount == 0) m.drop()
            }
            e.keyCode != KeyEvent.KEYCODE_BACK
        }
        onDispose { app.keyOverride = null }
    }
    // focus stays inside this screen while moving, and comes back to the moved rows afterwards
    LaunchedEffect(m.moving) {
        if (m.moving) runCatching { dropFocus.requestFocus() }
        else if (m.anchor != null) { delay(150); runCatching { rowFocus.requestFocus() }; m.anchor = null }
    }
    // keep the moving block (or the first ticked row after a move) in view
    LaunchedEffect(app.orders[m.type], m.moving) {
        val i = m.shown.indexOfFirst { it.id in m.picked }
        if (i >= 0 && i !in list.layoutInfo.visibleItemsInfo.map { it.index }.dropLast(1)) list.animateScrollToItem((i - 2).coerceAtLeast(0))
    }
    if (resetAsk) ResetConfirm(m) { resetAsk = false }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Manage categories", style = MaterialTheme.typography.headlineMedium)
                val parts = LocalParts.current
                Row(Modifier.background(Tok.Container, RoundedCornerShape(24.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    catTypes.filter { it.first in parts }.forEach { (t, label) ->
                        val on = t == m.type
                        TvSurface({ m.pickType(t) }, Modifier.height(40.dp), selected = on, shape = RoundedCornerShape(20.dp), focusRequester = if (on) start else null) {
                            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("%,d listed · %,d hidden · %s".format(m.shown.size, m.hiddenCount, if (m.custom) "Custom order" else "Provider order"),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            if (m.moving) MoveBanner(m, dropFocus)
            else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(false to "Containing", true to "Not containing").forEach { (ex, label) ->
                            TvSurface({ m.exclude = ex }, Modifier.height(48.dp), selected = m.exclude == ex, idle = Tok.ContainerHigh,
                                shape = if (!ex) RoundedCornerShape(24.dp, 6.dp, 6.dp, 24.dp) else RoundedCornerShape(6.dp, 24.dp, 24.dp, 6.dp)) {
                                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp))
                            }
                        }
                    }
                    FilterBox(m.q, { m.q = it }, Modifier.weight(1f))
                    TvButton("Tick all %,d".format(m.shown.size), m::tickAll)
                }
                TickedBar(m, { panelKind = it; panel = it }, { resetAsk = true })
            }
            LazyColumn(Modifier.weight(1f), list, contentPadding = PaddingValues(top = 4.dp, bottom = 72.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(m.shown, key = { _, c -> c.id }) { i, c ->
                    CatRow(m, c, segmentShape(i, m.shown.size), Modifier.animateItem(), if (c.id == m.anchor) rowFocus else null)
                }
            }
        }
        TvHints(*(if (m.moving) arrayOf("▲▼" to "One row", "CH±" to "One page", "OK" to "Drop", "Back" to "Cancel") else arrayOf("OK" to "Tick", "Back" to "Done")),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 27.dp))
        val first = remember { FocusRequester() }
        TvSidePanel(panel != null, { panel = null }, first, title = if (panelKind == "move") "Move ticked" else "Show or hide ticked") {
            val close = { panel = null }
            if (panelKind == "move") {
                PanelRow("Move with remote", first) { close(); m.startMove() }
                PanelRow("Move to top") { close(); m.move(Move.Top) }
                PanelRow("Move to bottom") { close(); m.move(Move.Bottom) }
            } else {
                PanelRow("Hide ticked", first) { close(); m.setHidden(true) }
                PanelRow("Show ticked") { close(); m.setHidden(false) }
            }
        }
    }
}

@Composable
private fun PanelRow(label: String, focusRequester: FocusRequester? = null, onClick: () -> Unit) =
    TvSurface(onClick, Modifier.fillMaxWidth().height(56.dp), focusRequester = focusRequester) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 20.dp))
    }

/** "2 ticked" and what to do with them; a hint while nothing is ticked. */
@Composable
private fun TickedBar(m: CategoriesModel, open: (String) -> Unit, onReset: () -> Unit) =
    Row(Modifier.fillMaxWidth().height(56.dp).background(Tok.ContainerHighest, RoundedCornerShape(28.dp)).padding(start = 22.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val any = m.picked.isNotEmpty()
        Text(if (any) "${m.picked.size} ticked" else "Tick categories with OK, or filter and tick everything listed",
            style = if (any) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (any) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
        if (any) {
            TvButton("Move ticked", { open("move") })
            TvButton("Show or hide ticked", { open("hide") })
            TvButton("Clear ticks", m::clear)
        }
        if (m.custom) TvButton("Reset order", onReset)
    }

@Composable
private fun MoveBanner(m: CategoriesModel, focus: FocusRequester) {
    val cs = MaterialTheme.colorScheme
    val n = m.picked.size
    Row(Modifier.fillMaxWidth().height(56.dp).focusRequester(focus).focusable().background(cs.primary, RoundedCornerShape(28.dp)).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.Rounded.OpenWith, null, tint = cs.onPrimary)
        Text("Moving $n ${if (n == 1) "category" else "categories"} · now at ${m.range}", style = MaterialTheme.typography.titleMedium, color = cs.onPrimary)
        Spacer(Modifier.weight(1f))
        Text("Hidden categories keep their place but don't show while moving.", style = MaterialTheme.typography.bodyMedium, color = cs.onPrimary)
    }
}

@Composable
private fun CatRow(m: CategoriesModel, c: Category, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier, focusRequester: FocusRequester?) {
    val cs = MaterialTheme.colorScheme
    val ticked = c.id in m.picked
    val hidden = m.isHidden(c)
    val lifted = m.moving && ticked
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    TvSurface({ m.tick(c.id) }, modifier.fillMaxWidth().height(50.dp), shape = shape,
        idle = when { lifted -> cs.primary; ticked -> Tok.ContainerHigh; else -> Tok.Container }, focusRequester = focusRequester, interactionSource = src) {
        val fg = if (lifted && !focused) cs.onPrimary else LocalContentColor.current
        val sub = if (focused || lifted) fg else cs.onSurfaceVariant
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Checkbox(ticked, null)
            Text("#${m.position[c.id] ?: 0}", style = MaterialTheme.typography.bodyMedium, color = sub, modifier = Modifier.width(40.dp))
            Text(c.name, style = MaterialTheme.typography.titleMedium, color = if (hidden && !focused) cs.onSurfaceVariant else fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (hidden) Text("Hidden", style = MaterialTheme.typography.labelLarge, color = sub,
                modifier = Modifier.background(if (focused) cs.inverseOnSurface.copy(alpha = 0.12f) else Tok.ContainerHighest, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
            // a hidden category may not be downloaded at all; no count beats a misleading 0
            val n = m.counts[c.id] ?: 0
            if (n > 0 || !hidden) Text("%,d %s".format(n, m.unit), style = MaterialTheme.typography.bodyMedium, color = sub)
        }
    }
}

/** The filter: highlighted like any focused item; OK starts typing (see [TvEditable]). */
@Composable
private fun FilterBox(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    TvEditable(modifier) { field, hover, editing ->
        val fg = if (hover) cs.inverseOnSurface else cs.onSurface
        BasicTextField(
            value, onChange, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            // Done closes the keyboard and moves on, so results are reachable straight away
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focusManager.moveFocus(FocusDirection.Down) }),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = fg),
            cursorBrush = SolidColor(cs.primary),
            modifier = field.fillMaxWidth(),
            decorationBox = { inner ->
                Row(Modifier.fillMaxWidth().scale(if (hover) TvLift.Row else 1f).height(48.dp)
                    .background(when { hover -> cs.inverseSurface; editing -> Tok.ContainerHighest; else -> Tok.ContainerHigh }, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = if (hover) fg else cs.onSurfaceVariant)
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(if (hover) "Press OK to type" else "Filter, e.g. sky, epl — several, separated by commas",
                            style = MaterialTheme.typography.bodyLarge, color = if (hover) fg else cs.onSurfaceVariant, maxLines = 1)
                        inner()
                    }
                }
            },
        )
    }
}
