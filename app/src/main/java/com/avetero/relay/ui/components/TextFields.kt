package com.avetero.relay.ui.components

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
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Ico
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.Mono
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import kotlinx.coroutines.delay

/** Single-line filter box used to find a category. */
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
    fun stop() { editing = false; runCatching { outer.requestFocus() } }
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
                .onFocusChanged { if (it.isFocused) innerFocused = true else if (innerFocused) { innerFocused = false; editing = false } }
                .onPreviewKeyEvent { e ->
                    when (e.key) {
                        // KeyUp too, or the app's own Back handler would see it after editing ends
                        Key.Back -> { if (e.type == KeyEventType.KeyUp) stop(); true }
                        Key.DirectionDown, Key.DirectionUp -> {
                            if (e.type == KeyEventType.KeyDown) { stop(); focusManager.moveFocus(if (e.key == Key.DirectionDown) FocusDirection.Down else FocusDirection.Up) }
                            true
                        }
                        else -> false
                    }
                },
            hover && !editing, editing,
        )
    }
}

@Composable
fun FilterField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null) =
    TvEditable(modifier.fillMaxWidth(), focusRequester, startEditing = focusRequester != null) { field, hover, editing ->
        var focused by remember { mutableStateOf(false) }
        val focusManager = LocalFocusManager.current
        BasicTextField(
            value, onChange, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.moveFocus(FocusDirection.Down) }),
            textStyle = TextStyle(color = if (hover) C.OnLight else Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(C.Accent),
            modifier = field.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(when { hover -> C.Text; focused && editing -> Color(0xFF2A3347); else -> C.Control }).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(Icons.Search, if (hover) C.OnLight else C.Muted, 16.dp)
                    Box(Modifier.weight(1f)) { if (value.isEmpty()) T(placeholder, 14, color = if (hover) C.OnLight else C.Dim); inner() }
                }
            },
        )
    }

@Composable
fun Field(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, hint: String? = null,
    password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, mono: Boolean = false, error: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val bring = remember { BringIntoViewRequester() }
    val imeVisible = WindowInsets.isImeVisible
    // re-scroll once the keyboard has resized the window, so the field ends up visible
    LaunchedEffect(focused, imeVisible) { if (focused) { delay(200); bring.bringIntoView() } }
    Column(modifier.bringIntoViewRequester(bring), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T(label, 13, Bold, if (error) C.Error else C.Soft)
        TvEditable(Modifier.onFocusChanged { if (it.hasFocus) focused = true }) { field, hover, _ ->
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(color = if (hover) C.OnLight else Color.White, fontSize = 16.sp, fontFamily = if (mono) Mono else null),
            cursorBrush = SolidColor(C.Accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, imeAction = ImeAction.Next),
            modifier = field.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp))
                        .background(when { hover -> C.Text; error -> Color(0xFF3A1E24); focused -> Color(0xFF2A3347); else -> C.Control }).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (hover && value.isEmpty()) T("Press OK to type", 14, color = C.OnLight)
                    inner()
                }
            },
        )
        }
        if (hint != null) T(hint, 12, color = C.Muted)
    }
}
