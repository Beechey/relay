package com.avetero.relay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.compact

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
    // phones are touch-first: no initial focus, so nothing looks selected that the user didn't tap
    val autoFocus = focusRequester != null && !compact()
    Box(
        modifier
            .then(if (autoFocus) Modifier.focusRequester(focusRequester!!) else Modifier)
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
