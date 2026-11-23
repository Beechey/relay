package com.avetero.relay.feature.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.theme.isTv
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.util.nowSec
import kotlinx.coroutines.delay

/**
 * Watches the reminders set in the guide. When one starts, a card offers to watch it; it goes away by itself after
 * 20 s or on Back. Reminders more than 10 minutes late (the app was closed) are dropped quietly.
 */
@Composable
fun ReminderCard(app: AppState, modifier: Modifier = Modifier) {
    var due by remember { mutableStateOf<Pair<Channel, String>?>(null) }
    LaunchedEffect(app.reminders, app.channels) {
        while (true) {
            val now = nowSec()
            for (k in app.reminders) {
                val (id, start, title) = k.split('|', limit = 3).let { Triple(it[0].toIntOrNull(), it.getOrNull(1)?.toLongOrNull() ?: 0, it.getOrElse(2) { "" }) }
                when {
                    start < now - 600 -> app.dropReminder(k)
                    start <= now + 30 -> { app.channels.firstOrNull { it.id == id }?.let { due = it to title }; app.dropReminder(k) }
                }
            }
            delay(20_000)
        }
    }
    val (c, title) = due ?: return
    val watchFocus = remember { FocusRequester() }
    val tv = isTv()
    LaunchedEffect(due) { if (tv) runCatching { watchFocus.requestFocus() }; delay(20_000); due = null }
    BackHandler { due = null }
    val watch = { due = null; app.player.play(app.liveUrl(c), title = c.name); app.push(Screen.LivePlayer(c, listOf(c))) }
    if (tv) {
        Column(modifier.width(420.dp).shadow(24.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(Tok.ContainerHigh).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.tv.material3.Text("Starting now · ${c.name}", style = androidx.tv.material3.MaterialTheme.typography.labelLarge, color = Tok.OnSurfaceVariant)
            androidx.tv.material3.Text(title, style = androidx.tv.material3.MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Watch", watch, primary = true, focusRequester = watchFocus)
                TvButton("Dismiss", { due = null })
            }
        }
    } else Surface(modifier.statusBarsPadding().widthIn(max = 420.dp).padding(start = 16.dp), shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
        Column(Modifier.padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Starting now · ${c.name}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 8.dp).align(androidx.compose.ui.Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ due = null }) { Text("Dismiss") }
                Button(watch) { Text("Watch") }
            }
        }
    }
}
