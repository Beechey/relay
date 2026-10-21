package com.avetero.relay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.avetero.relay.ui.components.DialogTitle
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.OptionRow
import com.avetero.relay.ui.components.Panel
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

@Composable
internal fun Title(text: String) = T(text, if (compact()) 20 else 24, Heavy)

@Composable
internal fun Stat(label: String, value: String, modifier: Modifier) =
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(C.Panel).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        T(label, 12, color = C.Muted); T(value, 15, Bold)
    }
