package com.avetero.relay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.avetero.relay.AppState
import com.avetero.relay.Channel
import com.avetero.relay.Release
import com.avetero.relay.Screen
import com.avetero.relay.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun Panel(width: Int, content: @Composable () -> Unit) =
    Column(Modifier.width(width.dp).clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }

@Composable
private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit, focusRequester: FocusRequester? = null) =
    FocusSurface(onClick, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(10.dp), focusRequester = focusRequester) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Ico(icon, fg(f), 18.dp); T(text, 15, Bold, fg(f))
        }
    }

/** Board 18: channel long-press menu. */
@Composable
fun ChannelMenu(app: AppState, c: Channel, onDismiss: () -> Unit) = Dialog(onDismiss) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Panel(340) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Logo(c.icon, c.name, false, Modifier.size(44.dp, 32.dp))
            Column { T(c.name, 16, Heavy); T(if (c.num > 0) "Channel ${c.num}" else "Channel", 12, color = C.Muted) }
        }
        val fav = app.isFav("c${c.id}")
        MenuItem(if (fav) "Remove from favourites" else "Add to favourites", if (fav) Icons.StarFilled else Icons.Star, { app.toggleFav("c${c.id}"); onDismiss() }, first)
        MenuItem("Show in TV guide", Icons.Guide, { onDismiss(); app.go(Screen.Guide) })
        MenuItem("Watch fullscreen", Icons.Play, { onDismiss(); app.push(Screen.LivePlayer(c, app.channelsIn(c.categoryId))) })
    }
}

@Composable
fun ExitDialog(onExit: () -> Unit, onDismiss: () -> Unit) = Dialog(onDismiss) {
    val stay = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { stay.requestFocus() } }
    Panel(340) {
        T("Exit Relay IPTV?", 20, Heavy)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Exit", onExit, Modifier.weight(1f), height = 46.dp)
            Btn("Stay", onDismiss, Modifier.weight(1f), style = BtnStyle.Light, height = 46.dp, focusRequester = stay)
        }
    }
}

/** Board 18: GitHub update prompt with download progress. */
@Composable
fun UpdateDialog(app: AppState, r: Release, onDismiss: () -> Unit) = Dialog(onDismiss) {
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Panel(440) {
        Badge("Update available", C.Accent, C.OnLight)
        T("Version ${r.version} is ready", 26, Heavy)
        T(r.notes.ifBlank { "Bug fixes and improvements." }, 14, color = C.Soft, maxLines = 6, lineHeight = 21)
        progress?.let { p ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(if (p >= 1f) "Downloaded" else "Downloading… ${(p * 100).toInt()}%", 13, Bold)
                ProgressLine(p, Modifier.fillMaxWidth(), height = 6.dp)
            }
        }
        error?.let { T(it, 13, Bold, C.Error, maxLines = 2) }
        T("Android will ask you to confirm the install. The first time, allow this app to install updates.", 12, color = C.Muted, maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Install now", {
                if (progress != null) return@Btn
                progress = 0f; error = null
                scope.launch {
                    try {
                        val apk = withContext(Dispatchers.IO) { Updater.download(app.ctx, r) { progress = it } }
                        progress = 1f
                        Updater.install(app.ctx, apk)
                    } catch (e: Exception) {
                        error = e.message ?: "Download failed"; progress = null
                    }
                }
            }, Modifier.weight(1f), style = BtnStyle.Light, focusRequester = first)
            Btn("Later", onDismiss, Modifier.weight(1f))
        }
        Btn("Skip this version", { app.store.skippedVersion = r.version; onDismiss() }, Modifier.fillMaxWidth(), height = 40.dp)
    }
}
