package com.avetero.relay.feature.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.components.LivePill
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.Semi
import com.avetero.relay.ui.theme.T
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
    val watch = remember { FocusRequester() }
    LaunchedEffect(due) { runCatching { watch.requestFocus() }; delay(20_000); due = null }
    BackHandler { due = null }
    Column(modifier.width(420.dp).clip(RoundedCornerShape(22.dp)).background(C.Glass).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { LivePill(); T("Starting now", 13, Semi, C.Muted) }
        T(title, 22, Heavy, maxLines = 2)
        T(c.name, 13, Semi, C.Soft)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Watch", { due = null; app.player.play(app.liveUrl(c), title = c.name); app.push(Screen.LivePlayer(c, listOf(c))) }, style = BtnStyle.Primary, icon = Icons.Play, height = 44.dp, focusRequester = watch)
            Btn("Dismiss", { due = null }, height = 44.dp)
        }
    }
}
