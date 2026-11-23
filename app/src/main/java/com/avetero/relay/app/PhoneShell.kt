package com.avetero.relay.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avetero.relay.player.PlayerSurface
import com.avetero.relay.ui.components.LocalParts
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.theme.LocalWide

/** Phone navigation: Library covers Movies and Series; Search and Settings open from For you's search bar. */
private class PhoneDest(val label: String, val on: ImageVector, val off: ImageVector, val tabs: Set<String>, val go: (AppState) -> Screen, val part: String? = null)

private val dests = listOf(
    PhoneDest("For you", Icons.Rounded.Home, Icons.Outlined.Home, setOf("home", "search", "settings"), { Screen.Home }),
    PhoneDest("Live", Icons.Rounded.LiveTv, Icons.Outlined.LiveTv, setOf("live"), { Screen.Live() }, "live"),
    PhoneDest("Guide", Icons.AutoMirrored.Rounded.EventNote, Icons.AutoMirrored.Outlined.EventNote, setOf("guide"), { Screen.Guide }, "live"),
    PhoneDest("Library", Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary, setOf("movies", "series"), { Screen.VodGrid("vod" !in it.parts) }, "lib"),
    PhoneDest("Saved", Icons.Rounded.Star, Icons.Outlined.StarOutline, setOf("saved"), { Screen.Favourites }),
)

/**
 * Phone and tablet frame: Material 3 Expressive short navigation bar (a rail at 600dp+), the mini player above it
 * while live TV keeps playing, and snackbars for messages (with Undo when an action offers one).
 * Screens draw edge to edge and handle the status bar themselves.
 */
@Composable
fun PhoneShell(app: AppState, nav: (Tab) -> Unit) {
    val parts = LocalParts.current
    val shown = dests.filter { d -> when (d.part) { null -> true; "lib" -> "vod" in parts || "series" in parts; else -> d.part in parts } }
    val showNav = app.tab != "start" && !app.onPlayerScreen
    val wide = LocalWide.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(app.message) {
        val msg = app.message ?: return@LaunchedEffect
        val undo = app.messageUndo
        app.messageUndo = null
        val r = snackbar.showSnackbar(msg, actionLabel = if (undo != null) "Undo" else null, duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) undo?.invoke()
        if (app.message == msg) app.message = null
    }
    val pick: (PhoneDest) -> Unit = { d -> app.go(d.go(app)) }
    val selected: (PhoneDest) -> Boolean = { d -> app.tab in d.tabs }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                AnimatedVisibility(showMini(app), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { MiniPlayer(app) }
                AnimatedVisibility(showNav && !wide, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    ShortNavigationBar {
                        shown.forEach { d ->
                            ShortNavigationBarItem(
                                selected = selected(d), onClick = { pick(d) },
                                icon = { Icon(if (selected(d)) d.on else d.off, contentDescription = null) },
                                label = { Text(d.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        Row(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            if (showNav && wide) NavigationRail(Modifier.padding(top = 8.dp)) {
                Spacer(Modifier.height(24.dp))
                shown.forEach { d ->
                    NavigationRailItem(selected = selected(d), onClick = { pick(d) }, icon = { Icon(if (selected(d)) d.on else d.off, null) }, label = { Text(d.label) })
                }
            }
            RelayNavDisplay(app, nav, Modifier.weight(1f).fillMaxSize())
        }
    }
}

private fun showMini(app: AppState) =
    app.liveChannel != null && app.player.active && !app.onPlayerScreen && app.screen !is Screen.Live && app.tab != "start" && !app.inPip

/** Live TV keeps playing here when you browse elsewhere; tap to go back to it full screen. */
@Composable
private fun MiniPlayer(app: AppState) {
    val c = app.liveChannel ?: return
    val now = app.programmesFor(c)?.firstOrNull { val t = System.currentTimeMillis() / 1000; it.start <= t && t < it.end }
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(64.dp).clip(RoundedCornerShape(32.dp))
            .clickable(onClickLabel = "Open ${c.name}") { app.push(Screen.LivePlayer(c, app.channelsIn(c.categoryId))) },
        color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(32.dp), shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(width = 85.dp, height = 48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
                PlayerSurface(app.player.player, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(now?.title?.let { "Live · $it" } ?: "Live", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val playing = app.player.playing
            IconButton({ if (playing) app.player.player.pause() else app.player.player.play() }) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play")
            }
            IconButton({ app.liveChannel = null; app.player.stop() }) { Icon(Icons.Rounded.Close, "Close player") }
            Spacer(Modifier.width(0.dp))
        }
    }
}
