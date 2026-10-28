package com.avetero.relay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Ico
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.fg

/**
 * The one confirm dialog: a question, an optional explanation, the action and Cancel. Focus starts on Cancel,
 * so a stray OK never confirms something destructive.
 */
@Composable
fun ConfirmDialog(
    title: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    message: String? = null, cancel: String = "Cancel", destructive: Boolean = true,
) = Dialog(onDismiss) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
    Panel(400) {
        T(title, 20, Heavy, maxLines = 3)
        if (message != null) T(message, 14, color = C.Soft, maxLines = 4, lineHeight = 21)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(confirm, onConfirm, Modifier.weight(1f), style = if (destructive) BtnStyle.Destructive else BtnStyle.Secondary, height = 46.dp)
            Btn(cancel, onDismiss, Modifier.weight(1f), style = BtnStyle.Primary, height = 46.dp, focusRequester = cancelFocus)
        }
    }
}

/** The surface every dialog and menu sits on. */
@Composable
fun Panel(width: Int, modifier: Modifier = Modifier, padding: Dp = 24.dp, spacing: Dp = 14.dp, scroll: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    // a menu opened by holding OK must not take that same press (its repeats and release) as a click on its first item
    var armed by remember { mutableStateOf(false) }
    Column(modifier.widthIn(max = width.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface)
        .onPreviewKeyEvent { e ->
            val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
            if (ok && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) armed = true
            ok && !armed
        }
        .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        .padding(padding), verticalArrangement = Arrangement.spacedBy(spacing)) { content() }
}

/** A row in a hold-OK menu. */
@Composable
fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit, focusRequester: FocusRequester? = null) =
    FocusSurface(onClick, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(10.dp), focusRequester = focusRequester) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Ico(icon, fg(f), 18.dp); T(text, 15, Bold, fg(f))
        }
    }

/** Title of a list dialog (track picker, choices, episodes). */
@Composable
fun DialogTitle(text: String) = T(text, 18, Heavy, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))

/** One choice in a list dialog: the name, and a tick on the current one. */
@Composable
fun OptionRow(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: String? = null, focusRequester: FocusRequester? = null) =
    FocusSurface(onClick, modifier.fillMaxWidth().height(48.dp), selected = selected, shape = RoundedCornerShape(10.dp), focusRequester = focusRequester) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            T(text, 15, Bold, fg(f), Modifier.weight(1f))
            if (trailing != null) T(trailing, 13, Heavy, if (f) C.OnLight else C.Accent)
            else if (selected) T("✓", 15, Heavy, if (f) C.OnLight else C.Accent)
        }
    }
