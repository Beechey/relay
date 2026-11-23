package com.avetero.relay.feature.live

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.current
import com.avetero.relay.feature.playback.TracksDialog
import com.avetero.relay.ui.m3.ChannelLogo
import com.avetero.relay.ui.m3.RelaySheet
import com.avetero.relay.ui.m3.segmentShape
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.theme.isTv
import com.avetero.relay.ui.tv.TvSidePanel
import com.avetero.relay.ui.tv.TvSurface
import com.avetero.relay.util.fmtTime

private class MenuAction(val label: String, val icon: ImageVector, val danger: Boolean = false, val run: () -> Unit)

/**
 * A channel's options: phone bottom sheet (PhoneChannelSheet), TV side panel (TvChannelMenu). [inPlayer] adds the
 * playback items (previous channel, tracks, mute); outside the player they show for the channel that's playing.
 * [onHide] (phone Live) hides the channel's category, with Undo.
 */
@Composable
fun ChannelMenu(app: AppState, c: Channel, onDismiss: () -> Unit, inPlayer: Boolean = false, onPrevious: (() -> Unit)? = null, onHide: (() -> Unit)? = null) {
    var tracks by remember { mutableStateOf(false) }
    if (tracks) TracksDialog(app) { tracks = false; onDismiss() }
    val fav = app.isFav("c${c.id}")
    val playback = inPlayer || app.liveChannel?.id == c.id
    val tv = isTv()
    val items = buildList {
        add(MenuAction(if (fav) (if (tv) "Remove from favourites" else "Remove from Saved") else (if (tv) "Add to favourites" else "Add to Saved"),
            if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline) { app.toggleFav("c${c.id}"); onDismiss() })
        if (!inPlayer) add(MenuAction("Watch fullscreen", Icons.Rounded.Fullscreen) { onDismiss(); app.push(Screen.LivePlayer(c, app.channelsIn(c.categoryId))) })
        if (!tv || !inPlayer) add(MenuAction("Show in TV guide", Icons.AutoMirrored.Rounded.EventNote) { onDismiss(); app.go(Screen.Guide) })
        if (onPrevious != null) add(MenuAction("Previous channel", Icons.AutoMirrored.Rounded.ArrowBack, run = onPrevious))
        if (playback) {
            add(MenuAction("Audio, subtitles & quality", Icons.Rounded.Subtitles) { tracks = true })
            add(MenuAction(if (app.player.muted) "Unmute" else "Mute", if (app.player.muted) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff) { app.player.toggleMute(); onDismiss() })
        }
        if (tv && inPlayer) add(MenuAction("Show in TV guide", Icons.AutoMirrored.Rounded.EventNote) { onDismiss(); app.go(Screen.Guide) })
        if (onHide != null) {
            val cat = app.liveCats.firstOrNull { it.id == c.categoryId }?.name
            if (cat != null) add(MenuAction("Hide “$cat” category", Icons.Rounded.VisibilityOff, danger = true) { onDismiss(); onHide() })
        }
    }
    val now = app.programmesFor(c).current()
    val sub = listOfNotNull(if (c.num > 0) "Channel ${c.num}" else "Channel", now?.let { "${it.title} until ${fmtTime(it.end)}" }).joinToString(" · ")
    if (tv) TvMenu(c, sub, items, onDismiss) else PhoneMenu(c, sub, items, onDismiss)
}

@Composable
private fun PhoneMenu(c: Channel, sub: String, items: List<MenuAction>, onDismiss: () -> Unit) = RelaySheet(onDismiss) {
    Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ChannelLogo(c.icon, c.name)
        Column {
            Text(c.name, style = MaterialTheme.typography.titleLarge)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { i, it ->
            val color = if (it.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            Surface(Modifier.fillMaxWidth().height(56.dp), shape = segmentShape(i, items.size), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxSize().clickable(onClick = it.run).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(it.icon, null, tint = color)
                    Text(it.label, style = MaterialTheme.typography.bodyLarge, color = color)
                }
            }
        }
    }
}

@Composable
private fun TvMenu(c: Channel, sub: String, items: List<MenuAction>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    TvSidePanel(true, onDismiss, first) {
        Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChannelLogo(c.icon, c.name, Modifier.width(72.dp), size = 48.dp)
            Column {
                androidx.tv.material3.Text(c.name, style = androidx.tv.material3.MaterialTheme.typography.titleLarge)
                androidx.tv.material3.Text(sub, style = androidx.tv.material3.MaterialTheme.typography.bodyMedium, color = androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items.forEachIndexed { i, it ->
                TvSurface(it.run, Modifier.fillMaxWidth().height(56.dp), shape = segmentShape(i, items.size),
                    idle = Tok.ContainerHigh,
                    focusRequester = if (i == 0) first else null) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        androidx.tv.material3.Icon(it.icon, null, Modifier.size(24.dp))
                        androidx.tv.material3.Text(it.label, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        com.avetero.relay.ui.tv.TvHints("☰ / hold OK" to "Opens this", "Back" to "Close")
    }
}
