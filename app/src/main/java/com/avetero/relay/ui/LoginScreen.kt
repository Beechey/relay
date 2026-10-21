package com.avetero.relay.ui

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.delay
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avetero.relay.Account
import com.avetero.relay.AppState
import com.avetero.relay.Screen
import com.avetero.relay.StepState
import com.avetero.relay.Xtream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Board 01. */
@Composable
fun LoginScreen(app: AppState) {
    var name by remember { mutableStateOf("My provider") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    // start on the button: a focused text field on TV pops the on-screen keyboard over the form
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    fun submit() {
        if (server.isBlank() || user.isBlank() || pass.isBlank()) { error = "Fill in the server, username and password"; return }
        busy = true; error = null
        val a = Account(name.trim().ifEmpty { "My provider" }, server.trim(), user.trim(), pass)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { Xtream(a).login() }
                app.signIn(a)
            } catch (e: Exception) {
                error = e.message ?: "Couldn't sign in"
            } finally {
                busy = false
            }
        }
    }

    Row(Modifier.fillMaxSize().background(C.Ground).imePadding().padding(horizontal = 64.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(96.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(C.Accent), contentAlignment = Alignment.Center) { T("R", 24, Heavy, C.OnLight) }
            T("Add your playlist", 40, Heavy, maxLines = 2, lineHeight = 44)
            T("Sign in with the Xtream Codes details from your provider. Live TV, movies, series and the TV guide load automatically.", 16, color = C.Soft, maxLines = 4, lineHeight = 26)
            if (app.store.playlists().isNotEmpty()) Btn("Back", { app.back() }, Modifier.width(120.dp), height = 44.dp)
        }
        // scrolls so the focused field stays above the on-screen keyboard
        Column(Modifier.width(520.dp).verticalScroll(rememberScrollState()).clip(RoundedCornerShape(20.dp)).background(Color(0xFF11151F)).padding(36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Field("Playlist name", name, { name = it })
            Field("Server URL", server, { server = it }, hint = "Include the port, e.g. :8080", keyboard = KeyboardType.Uri, mono = true)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Field("Username", user, { user = it }, Modifier.weight(1f), error = error != null)
                Field("Password", pass, { pass = it }, Modifier.weight(1f), password = true, error = error != null)
            }
            if (error != null) T(error!!, 13, Bold, C.Error, maxLines = 2)
            Btn(if (busy) "Signing in…" else "Sign in & load playlist", ::submit, Modifier.fillMaxWidth(), style = BtnStyle.Primary, height = 56.dp, enabled = !busy, focusRequester = first)
            T("Your login is stored only on this device.", 13, color = C.Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
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
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = if (mono) Mono else null),
            cursorBrush = SolidColor(C.Accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp))
                        .background(when { error -> Color(0xFF3A1E24); focused -> Color(0xFF2A3347); else -> C.Control }).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
        if (hint != null) T(hint, 12, color = C.Muted)
    }
}

/** Board 02. */
@Composable
fun SyncScreen(app: AppState) {
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        error = null
        try { app.sync(); app.go(Screen.Home) } catch (e: Exception) { error = e.message ?: "Couldn't load the playlist" }
    }
    val a = app.account
    Box(Modifier.fillMaxSize().background(C.Ground), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(C.Accent), contentAlignment = Alignment.Center) { T("R", 20, Heavy, C.OnLight) }
                Column {
                    T("Loading ${a?.name ?: ""}", 28, Heavy)
                    T(a?.server ?: "", 14, color = C.Muted, mono = true)
                }
            }
            val done = app.syncSteps.count { it.state == StepState.Done || it.state == StepState.Failed }
            ProgressLine(if (app.syncSteps.isEmpty()) 0f else done / app.syncSteps.size.toFloat(), Modifier.fillMaxWidth(), C.Control, height = 6.dp)
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF11151F)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                app.syncSteps.forEach { s ->
                    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val (dot, mark) = when (s.state) {
                            StepState.Done -> Color(0xFF2E8A5B) to "✓"
                            StepState.Busy -> C.Accent to "…"
                            StepState.Failed -> C.Error to "!"
                            StepState.Todo -> Color(0xFF1F2636) to ""
                        }
                        Box(Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(dot), contentAlignment = Alignment.Center) {
                            T(mark, 13, Heavy, if (s.state == StepState.Done) Color.White else C.OnLight)
                        }
                        T(s.label, 15, Semi, if (s.state == StepState.Todo) C.Muted else C.Text, Modifier.weight(1f))
                        T(s.detail, 13, color = if (s.state == StepState.Failed) C.Error else C.Muted, mono = s.state != StepState.Failed)
                    }
                }
            }
            if (error != null) {
                T(error!!, 15, Bold, C.Error, maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Try again", { attempt++ }, style = BtnStyle.Light)
                    Btn("Edit details", { app.push(Screen.Login) })
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Large playlists can take a minute the first time.", 13, color = C.Muted, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
    }
}
