package com.avetero.relay.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.avetero.relay.ui.components.TvEditable
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvHints
import com.avetero.relay.ui.tv.TvLift
import com.avetero.relay.ui.tv.TvSurface

@Composable
internal fun LoginTv(f: LoginForm) {
    val cs = MaterialTheme.colorScheme
    val first = remember { FocusRequester() }
    // start on the button: a focused text field on TV pops the on-screen keyboard over the form
    LaunchedEffect(Unit) { first.settle() }
    Box(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 48.dp), horizontalArrangement = Arrangement.spacedBy(64.dp)) {
            Column(Modifier.width(400.dp).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(56.dp).background(cs.primary, RoundedCornerShape(18.dp)), Alignment.Center) {
                    Text("R", style = MaterialTheme.typography.headlineMedium, color = cs.onPrimary)
                }
                Text("Add your playlist", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 8.dp))
                Text("Sign in with the Xtream Codes login from your provider. Live TV, movies, series and the TV guide load by themselves.",
                    style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                Row(Modifier.padding(top = 12.dp).background(Tok.Container, RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Rounded.PhoneAndroid, null, tint = cs.primary)
                    Text("Type them here, or from the Fire TV or Google TV app on your phone.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                }
                Text("Your login is stored only on this device.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            // scrolls so the focused field stays above the on-screen keyboard
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 48.dp, start = 12.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TvField("Playlist name", f.name, { f.name = it })
                TvField("Server URL", f.server, { f.server = it }, hint = "Include the port, e.g. :8080", keyboard = KeyboardType.Uri)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvField("Username", f.user, { f.user = it }, Modifier.weight(1f), error = f.error != null)
                    TvField("Password", f.pass, { f.pass = it }, Modifier.weight(1f), password = true, error = f.error != null)
                }
                Text("What do you watch?", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    watchParts.forEach { (k, label) ->
                        val on = k in f.want
                        TvSurface({ f.flip(k) }, Modifier.height(40.dp), selected = on, shape = RoundedCornerShape(8.dp)) {
                            Row(Modifier.fillMaxHeight().padding(start = if (on) 10.dp else 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (on) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                                Text(label, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
                f.error?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = cs.error, maxLines = 2) }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton(if (f.busy) "Signing in…" else "Sign in and load playlist", { if (!f.busy) f.submit() }, primary = true, focusRequester = first)
                    if (f.canGoBack) TvButton("Back", f::back)
                }
            }
        }
        TvHints("OK" to "Type with the on-screen keyboard", modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 27.dp))
    }
}

/** A labelled text box: highlighted like any focused item; OK starts typing (see [TvEditable]). */
@Composable
private fun TvField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, hint: String? = null,
    password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, error: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = if (error) cs.error else cs.onSurfaceVariant)
        TvEditable { field, hover, editing ->
            val fg = if (hover) cs.inverseOnSurface else cs.onSurface
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = fg),
                cursorBrush = SolidColor(cs.primary),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, imeAction = ImeAction.Next),
                modifier = field.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().scale(if (hover) TvLift.Row else 1f)
                            .then(if (hover) Modifier.shadow(16.dp, RoundedCornerShape(16.dp)) else Modifier)
                            .height(60.dp).background(when { hover -> cs.inverseSurface; editing -> Tok.ContainerHighest; else -> Tok.ContainerHigh }, RoundedCornerShape(16.dp))
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) Text(if (hover) "Press OK to type" else hint ?: label, style = MaterialTheme.typography.titleMedium, color = if (hover) fg else cs.onSurfaceVariant)
                        inner()
                    }
                },
            )
        }
    }
}
