package com.avetero.relay.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.avetero.relay.app.AppState
import com.avetero.relay.data.model.Account
import com.avetero.relay.data.store.ALL_PARTS
import com.avetero.relay.data.xtream.Xtream
import com.avetero.relay.ui.theme.isTv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the sign-in form holds, shared by the phone and TV layouts. */
internal class LoginForm(private val app: AppState, private val scope: CoroutineScope) {
    var name by mutableStateOf("My provider")
    var server by mutableStateOf("")
    var user by mutableStateOf("")
    var pass by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var want by mutableStateOf(ALL_PARTS)
    val canGoBack get() = app.store.playlists().isNotEmpty()

    /** At least one part stays on. */
    fun flip(part: String) { want = if (part in want) (want - part).ifEmpty { want } else want + part }

    fun submit() {
        if (server.isBlank() || user.isBlank() || pass.isBlank()) { error = "Fill in the server, username and password"; return }
        busy = true; error = null
        val a = Account(name.trim().ifEmpty { "My provider" }, server.trim(), user.trim(), pass)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { Xtream(a).login() }
                // only what they watch is downloaded, from the very first load
                app.store.setParts(a, want)
                app.signIn(a)
            } catch (e: Exception) {
                error = e.message ?: "Couldn't sign in"
            } finally {
                busy = false
            }
        }
    }

    fun back() { app.back() }
}

internal val watchParts = listOf("live" to "Live TV", "vod" to "Movies", "series" to "Series")

/** Sign in with an Xtream Codes login. shortcut: the "copy from another Relay" list arrives with household sync. */
@Composable
fun LoginScreen(app: AppState) {
    val scope = rememberCoroutineScope()
    val form = remember { LoginForm(app, scope) }
    if (isTv()) LoginTv(form) else LoginPhone(form)
}
