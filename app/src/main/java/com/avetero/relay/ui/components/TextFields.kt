package com.avetero.relay.ui.components

import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avetero.relay.ui.theme.compact
import kotlinx.coroutines.delay

/**
 * TV: a text box is first only highlighted; OK starts typing (and opens the keyboard), Back, Up/Down or the
 * keyboard's action ends it. Moving past a box with the remote never pops the keyboard. Phones type straight away.
 * [content] gets the modifier for the text field itself, whether the box is highlighted, and whether it's being edited.
 */
@Composable
fun TvEditable(
    modifier: Modifier = Modifier, focusRequester: FocusRequester? = null, startEditing: Boolean = false,
    content: @Composable (field: Modifier, hover: Boolean, editing: Boolean) -> Unit,
) {
    if (compact()) return Box(modifier) { content(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier, false, true) }
    val outer = focusRequester ?: remember { FocusRequester() }
    val inner = remember { FocusRequester() }
    var editing by remember { mutableStateOf(startEditing) }
    var hover by remember { mutableStateOf(false) }
    var innerFocused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(editing) { if (editing) { runCatching { inner.requestFocus() }; keyboard?.show() } }
    fun stop() { editing = false; keyboard?.hide(); runCatching { outer.requestFocus() } }
    val scope = rememberCoroutineScope()
    // leave the box, then move on a frame later, once the box has its focus back (moving at once does nothing)
    fun leave(d: FocusDirection) { stop(); scope.launch { withFrameNanos { }; focusManager.moveFocus(d) } }
    Box(modifier.focusRequester(outer).onFocusChanged { hover = it.isFocused }
        // touch on a TV-sized layout (landscape tablets): a tap starts typing straight away
        .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(pass = PointerEventPass.Initial); editing = true } }
        .onPreviewKeyEvent { e ->
            val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
            if (!editing && ok && e.type == KeyEventType.KeyDown) { editing = true; true } else false
        }
        .focusable()) {
        content(
            Modifier.focusRequester(inner).focusProperties { canFocus = editing }
                .onFocusChanged { if (it.isFocused) innerFocused = true else if (innerFocused) { innerFocused = false; editing = false; keyboard?.hide() } }
                .onPreviewKeyEvent { e ->
                    when (e.key) {
                        // KeyUp too, or the app's own Back handler would see it after editing ends
                        Key.Back -> { if (e.type == KeyEventType.KeyUp) stop(); true }
                        // the remote leaves the box with Up, Down or Right; typing happens on the keyboard, not with the cursor
                        Key.DirectionDown, Key.DirectionUp, Key.DirectionRight -> {
                            if (e.type == KeyEventType.KeyDown) leave(when (e.key) { Key.DirectionDown -> FocusDirection.Down; Key.DirectionUp -> FocusDirection.Up; else -> FocusDirection.Right })
                            true
                        }
                        else -> false
                    }
                },
            hover && !editing, editing,
        )
    }
}
