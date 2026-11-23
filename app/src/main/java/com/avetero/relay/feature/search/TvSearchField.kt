package com.avetero.relay.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.avetero.relay.ui.components.TvEditable
import com.avetero.relay.ui.theme.RobotoFlex
import com.avetero.relay.ui.theme.Tok

/**
 * TV search box: highlighted first, OK opens the TV's own keyboard (so the phone remote app and voice work too);
 * Done or Down moves on to the results. Focus takes the standard inverse fill.
 */
@Composable
internal fun TvSearchField(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null, startEditing: Boolean = false, height: Dp = 48.dp, trailing: (@Composable () -> Unit)? = null,
) = TvEditable(modifier.fillMaxWidth(), focusRequester, startEditing) { field, hover, _ ->
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val fg = if (hover) Tok.InverseOnSurface else Tok.OnSurface
    BasicTextField(
        value, onChange, field.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focusManager.moveFocus(FocusDirection.Down) }),
        textStyle = TextStyle(color = fg, fontSize = if (height > 52.dp) 20.sp else 16.sp, fontFamily = RobotoFlex),
        cursorBrush = SolidColor(Tok.Primary),
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(if (hover) Tok.InverseSurface else Tok.ContainerHigh).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Search, null, tint = if (hover) Tok.InverseOnSurface else Tok.OnSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(if (hover) "Press OK to type" else placeholder, style = MaterialTheme.typography.bodyLarge, color = if (hover) Tok.InverseOnSurface else Tok.OnSurfaceVariant)
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}
