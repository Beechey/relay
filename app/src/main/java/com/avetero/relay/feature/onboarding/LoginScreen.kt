package com.avetero.relay.feature.onboarding

import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.theme.LiveGradient
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.data.store.ALL_PARTS
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avetero.relay.ui.components.Pill
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import com.avetero.relay.app.AppState
import com.avetero.relay.data.model.Account
import com.avetero.relay.data.xtream.Xtream
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.components.Field
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.LogoMark
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Board 09: sign in. shortcut: the "copy from another Relay" list arrives with household sync. */
@Composable
fun LoginScreen(app: AppState) {
    var name by remember { mutableStateOf("My provider") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var want by remember { mutableStateOf(ALL_PARTS) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    // start on the button: a focused text field on TV pops the on-screen keyboard over the form
    LaunchedEffect(Unit) { first.settle() }

    fun submit() {
        if (server.isBlank() || user.isBlank() || pass.isBlank()) { error = "Fill in the server, username and password"; return }
        busy = true; error = null
        val a = Account(name.trim().ifEmpty { "My provider" }, server.trim(), user.trim(), pass)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { Xtream(a).login() }
                // board 11: only what they watch is downloaded, from the very first load
                app.store.setParts(a, want)
                app.signIn(a)
            } catch (e: Exception) {
                error = e.message ?: "Couldn't sign in"
            } finally {
                busy = false
            }
        }
    }

    val phone = compact()
    val intro: @Composable () -> Unit = {
        Column(Modifier.then(if (phone) Modifier.fillMaxWidth() else Modifier.width(500.dp)), verticalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 16.dp)) {
            LogoMark(if (phone) 48.dp else 56.dp)
            T("Add your playlist", if (phone) 34 else 54, Heavy, maxLines = 2, lineHeight = if (phone) 36 else 54)
            T("Sign in with the Xtream Codes login from your provider. Live TV, movies, series and the TV guide load by themselves.", if (phone) 15 else 16, color = C.Soft, maxLines = 4, lineHeight = if (phone) 22 else 26)
            Column(Modifier.padding(top = if (phone) 4.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    "Find the server, username and password your provider sent you",
                    if (phone) "Type them in here" else "Type them here, or from the Fire TV or Google TV app on your phone",
                    "Live TV is ready in seconds; movies, series and the guide follow",
                ).forEachIndexed { n, step ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(C.Glass2)) { T("${n + 1}", 14, Heavy, C.Accent, Modifier.align(Alignment.Center), mono = true) }
                        T(step, 15, color = C.Soft, maxLines = 2, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (app.store.playlists().isNotEmpty()) Btn("Back", { app.back() }, Modifier.padding(top = 8.dp), height = 48.dp)
        }
    }
    val form: @Composable (Modifier) -> Unit = { mod ->
        Column(mod.clip(RoundedCornerShape(30.dp)).background(C.Glass).padding(if (phone) 22.dp else 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Pill("Xtream Codes")
            T("Your provider's login", 22, Heavy)
            Field("Playlist name", name, { name = it })
            Field("Server URL", server, { server = it }, hint = "Include the port, e.g. :8080", keyboard = KeyboardType.Uri, mono = true)
            if (phone) {
                Field("Username", user, { user = it }, error = error != null)
                Field("Password", pass, { pass = it }, password = true, error = error != null)
            } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Field("Username", user, { user = it }, Modifier.weight(1f), error = error != null)
                Field("Password", pass, { pass = it }, Modifier.weight(1f), password = true, error = error != null)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T("What do you watch?", 13, Bold, C.Soft)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("live" to "Live TV", "vod" to "Movies", "series" to "Series").forEach { (k, label) ->
                        // at least one stays on
                        WatchChip(label, k in want) { want = if (k in want) (want - k).ifEmpty { want } else want + k }
                    }
                }
            }
            if (error != null) T(error!!, 13, Bold, C.Error, maxLines = 2)
            Btn(if (busy) "Signing in…" else "Sign in and load playlist", ::submit, Modifier.fillMaxWidth(), style = BtnStyle.Primary, height = 54.dp, enabled = !busy, focusRequester = first)
            T("Your login is stored only on this device.", 13, color = C.Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
    val backdrop = Modifier.welcomeBackdrop()
    if (phone) Column(backdrop.statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        intro(); form(Modifier.fillMaxWidth())
    } else Row(backdrop.imePadding().padding(horizontal = 96.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(72.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        intro()
        // scrolls so the focused field stays above the on-screen keyboard
        form(Modifier.width(520.dp).verticalScroll(rememberScrollState()))
    }
}

/** Board 09's backdrop, shared by sign-in and loading: soft blue and teal light fading into the ground behind the copy. */
internal fun Modifier.welcomeBackdrop() = fillMaxSize().background(C.Ground).drawBehind {
    drawRect(Brush.radialGradient(listOf(Color(0xFF2F5F9C).copy(alpha = .9f), Color.Transparent), Offset(size.width * .75f, size.height * .25f), size.maxDimension * .5f))
    drawRect(Brush.radialGradient(listOf(Color(0xFF123A34).copy(alpha = .9f), Color.Transparent), Offset(size.width * .15f, size.height * .85f), size.maxDimension * .4f))
    drawRect(Brush.horizontalGradient(.25f to C.Ground.copy(alpha = .9f), 1f to C.Ground.copy(alpha = .3f)))
}

/** Board 11: a part of the playlist to download, ticked or not. */
@Composable
private fun WatchChip(label: String, on: Boolean, onClick: () -> Unit) =
    FocusSurface(onClick, Modifier.height(40.dp), idle = if (on) C.Glass3 else C.Glass2, shape = RoundedCornerShape(20.dp), lift = 1.05f, label = label) { f ->
        Row(Modifier.align(Alignment.Center).padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(18.dp).clip(CircleShape).then(
                when { f && on -> Modifier.background(C.OnLight); on -> Modifier.background(LiveGradient); else -> Modifier.background(if (f) C.LightTrack else C.Glass3) }), contentAlignment = Alignment.Center) {
                if (on) T("✓", 11, Heavy, if (f) C.Text else C.OnLight)
            }
            T(label, 14, Bold, when { f -> C.OnLight; on -> C.Text; else -> C.Muted })
        }
    }
