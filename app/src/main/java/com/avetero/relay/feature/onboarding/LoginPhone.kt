package com.avetero.relay.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The app's mark: an amber tile with an R. */
@Composable
internal fun RelayMark(size: Int = 64) = Box(Modifier.size(size.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape((size * 5 / 16).dp)), Alignment.Center) {
    Text("R", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
}

@Composable
internal fun LoginPhone(f: LoginForm) {
    val cs = MaterialTheme.colorScheme
    var showPass by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.height(64.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (f.canGoBack) IconButton(f::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            RelayMark()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add your playlist", style = MaterialTheme.typography.displaySmall)
                Text("Sign in with the Xtream Codes login from your provider. Live TV, movies, series and the TV guide load by themselves.",
                    style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
            val next = KeyboardOptions(imeAction = ImeAction.Next)
            OutlinedTextField(f.name, { f.name = it }, Modifier.fillMaxWidth(), label = { Text("Playlist name") }, singleLine = true, keyboardOptions = next)
            OutlinedTextField(f.server, { f.server = it }, Modifier.fillMaxWidth(), label = { Text("Server URL") }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                supportingText = { Text("Include the port, e.g. :8080") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next))
            OutlinedTextField(f.user, { f.user = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true, isError = f.error != null, keyboardOptions = next)
            OutlinedTextField(f.pass, { f.pass = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, isError = f.error != null,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { f.submit() }),
                trailingIcon = {
                    IconButton({ showPass = !showPass }) { Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (showPass) "Hide password" else "Show password") }
                })
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What do you watch?", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    watchParts.forEach { (k, label) ->
                        val on = k in f.want
                        FilterChip(on, { f.flip(k) }, { Text(label) },
                            leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null)
                    }
                }
            }
            f.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.error) }
            Button(f::submit, Modifier.fillMaxWidth().height(56.dp), enabled = !f.busy) {
                if (f.busy) { LoadingIndicator(Modifier.size(24.dp)); Spacer(Modifier.size(8.dp)); Text("Signing in…") } else Text("Sign in and load playlist")
            }
            Text("Your login is stored only on this device.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
