package com.avetero.relay.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.avetero.relay.data.model.Move
import com.avetero.relay.feature.categories.CategoryMenu
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Ico
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import com.avetero.relay.ui.theme.fg
import com.avetero.relay.ui.theme.sub
import java.text.Normalizer
import kotlinx.coroutines.delay

/** Phone: a horizontal strip of category chips replacing the TV group column. */
@Composable
fun GroupChips(groups: List<Group>, selected: String, onSelect: (String) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) { groups.indexOfFirst { it.id == selected }.takeIf { it >= 0 }?.let { state.scrollToItem(it) } }
    LazyRow(Modifier.fillMaxWidth(), state, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(groups, key = { it.id }) { g ->
            val on = g.id == selected
            FocusSurface({ onSelect(g.id) }, Modifier.height(36.dp), idle = if (on) C.Accent else C.Control, shape = RoundedCornerShape(18.dp)) { f ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(g.name, 13, Bold, if (f || on) C.OnLight else C.Soft)
                    if (g.count > 0) T("%,d".format(g.count), 12, color = if (f || on) C.OnLightSub else C.Dim)
                }
            }
        }
    }
}

data class Group(val id: String, val name: String, val count: Int)

/** Lowercase, accents stripped, so "Telé" matches "tele". */
fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

fun List<Group>.matching(q: String): List<Group> = if (q.isBlank()) this else norm(q.trim()).let { n -> filter { norm(it.name).contains(n) } }

/** The scrolling list of categories, shared by the TV column and the phone drawer. */
@Composable
fun CategoryList(groups: List<Group>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, focusSelected: Boolean = false,
                 onHide: ((Group) -> Unit)? = null, onMove: ((Group, Move?) -> Unit)? = null) {
    var confirm by remember { mutableStateOf<Group?>(null) }
    var menuFor by remember { mutableStateOf<Group?>(null) }
    confirm?.let { g ->
        ConfirmDialog("Hide “${g.name}”?", "Hide", { onHide?.invoke(g); confirm = null }, { confirm = null },
            message = "It won't appear in lists, All, the guide or search. Bring it back in Settings → Manage categories.")
    }
    menuFor?.let { g -> CategoryMenu(g.name, { m -> menuFor = null; onMove?.invoke(g, m) }, onHide?.let { { menuFor = null; confirm = g } }) { menuFor = null } }
    if (groups.isEmpty()) T("No categories match", 14, color = C.Muted, modifier = Modifier.padding(12.dp))
    val state = rememberLazyListState()
    val selectedFocus = remember { FocusRequester() }
    // TV: start on the current category so the remote has somewhere to begin
    LaunchedEffect(focusSelected) {
        if (focusSelected) {
            groups.indexOfFirst { it.id == selected }.takeIf { it >= 0 }?.let { state.scrollToItem(it) }
            delay(100)
            runCatching { selectedFocus.requestFocus() }
        }
    }
    // bottom room so the last category clears the screen edge / navigation bar
    LazyColumn(modifier, state, contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(groups, key = { it.id }) { g ->
            val on = g.id == selected
            // the current category gets an amber tint (white stays reserved for focus)
            FocusSurface({ onSelect(g.id) }, Modifier.fillMaxWidth().height(46.dp), idle = if (on) C.Accent.copy(alpha = .14f) else Color.Transparent, shape = RoundedCornerShape(23.dp), lift = 1.04f,
                focusRequester = if (on && focusSelected) selectedFocus else null,
                // Favourites / All / Continue watching are built in and can't be hidden
                onLongClick = when {
                    g.id.startsWith("__") -> null
                    onMove != null -> ({ menuFor = g })
                    onHide != null -> ({ confirm = g })
                    else -> null
                }) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(g.name, 14, if (on || f) Bold else FontWeight.SemiBold, if (f) C.OnLight else if (on) C.Text else C.Soft, Modifier.weight(1f))
                    if (g.count > 0) T("%,d".format(g.count), 13, color = when { f -> C.OnLightSub; on -> C.Accent; else -> C.Dim })
                }
            }
        }
    }
}

/** TV: the left category column shared by Live TV, Movies and Series, with a search toggle. */
@Composable
fun GroupColumn(title: String, groups: List<Group>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, onHide: ((Group) -> Unit)? = null,
                onMove: ((Group, Move?) -> Unit)? = null) {
    var searching by remember { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    val field = remember { FocusRequester() }
    // the field only appears on request: a focused text field on TV pops the keyboard
    LaunchedEffect(searching) { if (searching) runCatching { field.requestFocus() } }
    // a floating glass panel, like the Live channel list
    Column(modifier.padding(start = 24.dp, bottom = 24.dp).width(270.dp).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(C.Glass)
        .padding(start = 10.dp, end = 10.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            T(title, 18, Heavy, modifier = Modifier.weight(1f))
            IconBtn(Icons.Search, if (searching) "Close category search" else "Search categories", { searching = !searching; if (!searching) q = "" }, size = 36.dp,
                tint = if (searching) C.Accent else C.Muted)
        }
        if (searching) FilterField(q, { q = it }, "Find a category", focusRequester = field)
        CategoryList(remember(groups, q) { groups.matching(q) }, selected, onSelect, Modifier.weight(1f), onHide = onHide, onMove = onMove)
        if (onHide != null) T(if (onMove != null) "Hold OK to move or hide" else "Hold OK to hide", 11, color = C.Dim, modifier = Modifier.padding(start = 12.dp, bottom = 12.dp))
    }
}

/** Phone: the button that shows the current category and opens the drawer. */
@Composable
fun CategoryButton(name: String, count: Int, onClick: () -> Unit) =
    FocusSurface(onClick, Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp), idle = C.Glass2, shape = RoundedCornerShape(24.dp), label = "Choose category") { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Ico(Icons.More, if (f) C.OnLight else C.Accent, 20.dp)
            T(name, 15, Bold, fg(f), Modifier.weight(1f))
            T("%,d".format(count), 13, color = sub(f))
            Ico(Icons.Chevron, sub(f), 16.dp)
        }
    }

/** Phone: categories in a panel that slides in from the left, with search. Tap outside or Back to close. */
@Composable
fun CategoryDrawer(title: String, groups: List<Group>, selected: String, onSelect: (String) -> Unit, onHide: ((Group) -> Unit)? = null,
                   onMove: ((Group, Move?) -> Unit)? = null, onDismiss: () -> Unit) =
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        var q by remember { mutableStateOf("") }
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        // a glass panel floating over a dimmed screen
        Row(Modifier.fillMaxSize().background(Color(0x99000000))) {
            AnimatedVisibility(shown, enter = slideInHorizontally { -it }) {
                Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(16.dp).width(330.dp).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(C.Glass)
                    .padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(title, 20, Heavy, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        IconBtn(Icons.Back, "Close", onDismiss, size = 40.dp)
                    }
                    FilterField(q, { q = it }, "Find a category")
                    CategoryList(remember(groups, q) { groups.matching(q) }, selected, { onSelect(it); onDismiss() }, Modifier.weight(1f), focusSelected = !compact(), onHide = onHide, onMove = onMove)
                    if (onHide != null) T((if (compact()) "Press and hold a category to " else "Hold OK on a category to ") + if (onMove != null) "move or hide it" else "hide it", 12, color = C.Dim, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().combinedClickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
        }
    }
