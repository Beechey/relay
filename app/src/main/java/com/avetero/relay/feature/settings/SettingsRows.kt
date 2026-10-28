package com.avetero.relay.feature.settings

import com.avetero.relay.ui.theme.LiveGradient
import com.avetero.relay.ui.components.ProgressLine
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import com.avetero.relay.ui.theme.Semi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.avetero.relay.ui.components.DialogTitle
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.OptionRow
import com.avetero.relay.ui.components.Panel
import com.avetero.relay.ui.components.SegmentedTabs
import com.avetero.relay.ui.components.Toggle
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Ico
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import com.avetero.relay.ui.theme.fg
import com.avetero.relay.ui.theme.sub

/** Board 08: rows sit in a glass card, split by hairlines. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .09f)), verticalArrangement = Arrangement.spacedBy(1.dp), content = content)

/** Label, optional description, and a value with chevron or a toggle. Goes inside a [SettingsGroup]. */
@Composable
fun SettingRow(label: String, desc: String? = null, value: String? = null, toggle: Boolean? = null, extra: String? = null, progress: Float? = null, onClick: () -> Unit) =
    FocusSurface(onClick, Modifier.fillMaxWidth().heightIn(min = 66.dp), idle = C.Glass, shape = RectangleShape, lift = 1f) { f ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                T(label, 15, Bold, fg(f))
                if (desc != null) T(desc, 13, color = sub(f), maxLines = 2)
                if (progress != null) LoadingBar(progress, f, Modifier.padding(top = 6.dp))
            }
            if (extra != null) T(extra, 12, Semi, sub(f), mono = true)
            if (toggle != null) Toggle(toggle, f)
            if (value != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                T(value, 14, Bold, sub(f)); Ico(Icons.Chevron, sub(f), 16.dp)
            }
        }
    }

/** A setting with a fixed set of choices: shows the current one, opens a picker. */
@Composable
internal fun ChoiceRow(label: String, desc: String?, options: List<String>, current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var shown by remember(current) { mutableStateOf(current) }
    SettingRow(label, desc, value = shown) { open = true }
    if (open) Dialog({ open = false }) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Panel(420, padding = 20.dp, spacing = 6.dp) {
            DialogTitle(label)
            options.forEach { o -> OptionRow(o, o == shown, { shown = o; onPick(o); open = false }, focusRequester = if (o == shown) first else null) }
        }
    }
}

/** A short choice as segmented pills: Left and Right move between them, OK picks. */
@Composable
internal fun SegmentRow(label: String, desc: String?, options: List<String>, current: String, onPick: (String) -> Unit) {
    var shown by remember(current) { mutableStateOf(current) }
    Row(Modifier.fillMaxWidth().heightIn(min = 66.dp).background(C.Glass).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            T(label, 15, Bold)
            if (desc != null) T(desc, 13, color = C.Muted, maxLines = 2)
        }
        SegmentedTabs(options.map { it to it }, shown, { shown = it; onPick(it) }, small = true)
    }
}

@Composable
internal fun Title(text: String) = T(text, if (compact()) 22 else 30, Heavy)

@Composable
internal fun Stat(label: String, value: String, modifier: Modifier) =
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(C.Glass2).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        T(label, 12, color = C.Muted); T(value, 15, Bold)
    }

/** A download's progress; while the size isn't known yet, a short segment sweeps across. */
@Composable
internal fun LoadingBar(fraction: Float, focused: Boolean, modifier: Modifier = Modifier) {
    val track = if (focused) C.LightTrack else C.Glass3
    if (fraction < 0) {
        val x by rememberInfiniteTransition(label = "sweep").animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "x")
        Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(track)) {
            Box(Modifier.fillMaxWidth(0.25f).height(4.dp).graphicsLayer { translationX = (size.width * 4f) * x - size.width }.background(LiveGradient))
        }
    } else ProgressLine(animateFloatAsState(fraction, label = "bar").value, modifier.fillMaxWidth(), track, height = 4.dp)
}
