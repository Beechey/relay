package com.avetero.relay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * The only clickable primitive (focus rule, board 17): focused = solid light fill, selected = raised surface.
 * Long-press OK on a remote and long-press touch both call onLongClick.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    idle: Color = Color.Transparent,
    shape: Shape = RoundedCornerShape(12.dp),
    focusRequester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    label: String? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var longPressed by remember { mutableStateOf(false) }
    val bg = when { focused -> C.Text; selected -> C.Raised; else -> idle }
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus?.invoke() }
            .onPreviewKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                when {
                    onLongClick == null || !ok -> false
                    e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 1 -> { longPressed = true; onLongClick(); true }
                    e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 1 -> true
                    e.type == KeyEventType.KeyUp && longPressed -> { longPressed = false; true }
                    else -> false
                }
            }
            .clip(shape)
            .background(bg)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
            .combinedClickable(remember { MutableInteractionSource() }, null, onLongClick = onLongClick, onClick = onClick),
    ) { content(focused) }
}

enum class BtnStyle { Primary, Secondary, Destructive, Light }

@Composable
fun Btn(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: BtnStyle = BtnStyle.Secondary,
    icon: ImageVector? = null, height: Dp = 48.dp, focusRequester: FocusRequester? = null, enabled: Boolean = true,
) = FocusSurface(
    onClick = { if (enabled) onClick() }, modifier = modifier.height(height), focusRequester = focusRequester,
    idle = when { !enabled -> C.Surface; style == BtnStyle.Primary -> C.Accent; style == BtnStyle.Light -> C.Text; else -> C.Control },
) { f ->
    val color = when {
        f -> C.OnLight
        !enabled -> C.Dim
        style == BtnStyle.Primary || style == BtnStyle.Light -> C.OnLight
        style == BtnStyle.Destructive -> C.Error
        else -> C.Text
    }
    Row(Modifier.align(Alignment.Center).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) Ico(icon, color, 18.dp)
        T(text, 15, if (style == BtnStyle.Secondary && !f) Bold else Heavy, color)
    }
}

@Composable
fun IconBtn(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = C.Text, size: Dp = 48.dp, round: Boolean = false) =
    FocusSurface(onClick, modifier.size(size), idle = C.Control, shape = RoundedCornerShape(if (round) size / 2 else 12.dp), label = label) { f ->
        Ico(icon, if (f) C.OnLight else tint, 20.dp, Modifier.align(Alignment.Center))
    }

enum class Tab(val icon: ImageVector, val label: String) {
    Home(Icons.Home, "Home"), Live(Icons.Live, "Live TV"), Guide(Icons.Guide, "TV Guide"), Movies(Icons.Movies, "Movies"),
    Series(Icons.Series, "Series"), Favourites(Icons.Star, "Favourites"), Search(Icons.Search, "Search"), Settings(Icons.Settings, "Settings")
}

/** Nav rail (Rail.dc.html). */
@Composable
fun Rail(active: Tab, onSelect: (Tab) -> Unit) {
    Column(
        Modifier.width(72.dp).fillMaxHeight().background(Color(0xFF080A0F)).padding(vertical = 24.dp).focusRestorer(),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(C.Accent), contentAlignment = Alignment.Center) { T("R", 16, Heavy, C.OnLight) }
        Spacer(Modifier.height(18.dp))
        Tab.entries.forEach { t ->
            if (t == Tab.Settings) Spacer(Modifier.weight(1f))
            FocusSurface({ onSelect(t) }, Modifier.size(48.dp), selected = t == active, label = t.label) { f ->
                Ico(t.icon, when { f -> C.OnLight; t == active -> C.Accent; else -> C.Muted }, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/** Rail + content, the frame of most screens. */
@Composable
fun WithRail(active: Tab, onSelect: (Tab) -> Unit, content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxSize().background(C.Ground)) { Rail(active, onSelect); content() }

data class Group(val id: String, val name: String, val count: Int)

/** The left category column shared by Live TV, Movies and Series. */
@Composable
fun GroupColumn(title: String, groups: List<Group>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(220.dp).fillMaxHeight().background(C.Panel).padding(start = 12.dp, end = 12.dp, top = 28.dp)) {
        Label(title, Modifier.padding(start = 12.dp, bottom = 12.dp))
        LazyColumn(Modifier.focusRestorer(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(groups, key = { it.id }) { g ->
                val on = g.id == selected
                FocusSurface({ onSelect(g.id) }, Modifier.fillMaxWidth().height(44.dp), selected = on, shape = RoundedCornerShape(10.dp)) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(g.name, 15, if (on || f) Bold else FontWeight.Medium, if (f) C.OnLight else if (on) C.Text else C.Soft, Modifier.weight(1f))
                        T("%,d".format(g.count), 13, color = when { f -> C.OnLightSub; on -> C.Accent; else -> C.Dim })
                    }
                }
            }
        }
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) = T(text.uppercase(), 12, Bold, C.Muted, modifier)

@Composable
fun Badge(text: String, bg: Color = C.Control, color: Color = C.Soft, modifier: Modifier = Modifier) =
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) { T(text, 12, Heavy, color) }

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, track: Color = C.Track, bar: Color = C.Accent, height: Dp = 3.dp) =
    Box(modifier.height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(bar))
    }

@Composable
fun KeyHint(key: String, action: String) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(C.Control).padding(horizontal = 7.dp, vertical = 3.dp)) { T(key, 12, Bold) }
    T(action, 12, color = C.Muted)
}

@Composable
fun Toggle(on: Boolean, focused: Boolean) = Box(
    Modifier.size(52.dp, 30.dp).clip(RoundedCornerShape(15.dp))
        .background(if (on) C.Accent else if (focused) C.LightTrack else Color(0xFF2A3142)),
) {
    Box(Modifier.offset(x = if (on) 26.dp else 4.dp, y = 4.dp).size(22.dp).clip(RoundedCornerShape(11.dp))
        .background(if (on || focused) C.OnLight else C.Muted))
}

/** SettingRow.dc.html: label, optional description, and a value with chevron or a toggle. */
@Composable
fun SettingRow(label: String, desc: String? = null, value: String? = null, toggle: Boolean? = null, onClick: () -> Unit) =
    FocusSurface(onClick, Modifier.fillMaxWidth().heightIn(min = 64.dp), idle = C.Surface) { f ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                T(label, 15, Bold, fg(f))
                if (desc != null) T(desc, 13, color = sub(f), maxLines = 2)
            }
            if (toggle != null) Toggle(toggle, f)
            if (value != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                T(value, 14, Bold, sub(f)); Ico(Icons.Chevron, sub(f), 16.dp)
            }
        }
    }

private val tones = listOf(0xFF2B3A4A, 0xFF40322A, 0xFF2E2A44, 0xFF1F3B3A, 0xFF44262C, 0xFF2A3A2A, 0xFF3A2E40, 0xFF33332A).map { Color(it) }
fun tone(seed: Int) = tones[Math.floorMod(seed, tones.size)]

/** Artwork with a tinted, titled placeholder underneath (shown when there is no image or it fails). */
@Composable
fun Art(url: String, title: String, seed: Int, modifier: Modifier = Modifier, titleSize: Int = 13, showTitle: Boolean = true) =
    Box(modifier.clip(RoundedCornerShape(9.dp)).background(tone(seed))) {
        if (showTitle) T(title, titleSize, Heavy, Color.White, Modifier.align(Alignment.BottomStart).padding(10.dp), maxLines = 3)
        if (url.isNotBlank()) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }

/** Channel logo, falling back to a 3-letter badge. */
@Composable
fun Logo(url: String, name: String, focused: Boolean, modifier: Modifier = Modifier) =
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(if (focused) C.LightTrack else Color(0xFF1F2636)), contentAlignment = Alignment.Center) {
        T(name.filter { it.isLetterOrDigit() }.take(3).uppercase(), 11, Heavy, sub(focused))
        if (url.isNotBlank()) AsyncImage(url, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
    }

/** Centered message used for empty/error states (board 19). */
@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) =
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(420.dp).clip(RoundedCornerShape(18.dp)).background(C.Surface).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T(title, 20, Heavy)
            T(body, 14, color = Color(0xFFC9CED8), maxLines = 4, lineHeight = 21)
            if (action != null) Btn(action, onAction, style = BtnStyle.Light, height = 44.dp)
        }
    }
