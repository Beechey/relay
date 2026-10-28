package com.avetero.relay.feature.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.feature.playback.TracksDialog
import com.avetero.relay.ui.components.Logo
import com.avetero.relay.ui.components.MenuItem
import com.avetero.relay.ui.components.Panel
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.T

/** Board 18: channel long-press menu. */
@Composable
fun ChannelMenu(app: AppState, c: Channel, onDismiss: () -> Unit, inPlayer: Boolean = false, onPrevious: (() -> Unit)? = null) = Dialog(onDismiss) {
    var tracks by remember { mutableStateOf(false) }
    if (tracks) TracksDialog(app) { tracks = false; onDismiss() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Panel(340) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Logo(c.icon, c.name, false, Modifier.size(44.dp, 32.dp))
            Column { T(c.name, 16, Heavy); T(if (c.num > 0) "Channel ${c.num}" else "Channel", 12, color = C.Muted) }
        }
        val fav = app.isFav("c${c.id}")
        MenuItem(if (fav) "Remove from favourites" else "Add to favourites", if (fav) Icons.StarFilled else Icons.Star, { app.toggleFav("c${c.id}"); onDismiss() }, first)
        if (inPlayer) {
            if (onPrevious != null) MenuItem("Previous channel", Icons.Back, onPrevious)
            MenuItem("Audio, subtitles & quality", Icons.Settings, { tracks = true })
            MenuItem(if (app.player.muted) "Unmute" else "Mute", if (app.player.muted) Icons.Volume else Icons.Muted, { app.player.toggleMute(); onDismiss() })
        } else {
            MenuItem("Show in TV guide", Icons.Guide, { onDismiss(); app.go(Screen.Guide) })
            MenuItem("Watch fullscreen", Icons.Play, { onDismiss(); app.push(Screen.LivePlayer(c, app.channelsIn(c.categoryId))) })
        }
    }
}
