package com.avetero.relay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.avetero.relay.AppState
import com.avetero.relay.PlayerSurface
import com.avetero.relay.AppState.Companion.ALL
import com.avetero.relay.AppState.Companion.FAVS
import com.avetero.relay.Channel
import com.avetero.relay.Programme
import com.avetero.relay.Screen
import com.avetero.relay.fmtTime

/**
 * Board 05. Rows come from the short EPG per channel, two hours from the last half hour.
 * shortcut: short EPG only (~8 programmes per channel); add the xmltv parser (docs M2) for scrolling further ahead.
 */
@Composable
fun GuideScreen(app: AppState, nav: (Tab) -> Unit) = WithRail(Tab.Guide, nav) {
    val favMode = app.favourites.any { it.startsWith("c") }
    val list = remember(app.channels, app.favourites) { app.channelsIn(if (favMode) FAVS else ALL) }
    val windowStart = remember { nowSec() / 1800 * 1800 }
    val windowEnd = windowStart + 2 * 3600
    var focused by remember { mutableStateOf<Pair<Channel, Programme?>?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(list) { runCatching { first.requestFocus() } }

    Column(Modifier.weight(1f).fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().height(200.dp).padding(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Box(Modifier.size(270.dp, 152.dp).clip(RoundedCornerShape(12.dp)).background(C.Control)) {
                if (app.player.url != null) PlayerSurface(app.player.player, Modifier.fillMaxSize())
            }
            val (c, p) = focused ?: (null to null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(listOfNotNull(c?.name, p?.let { "${fmtTime(it.start)} – ${fmtTime(it.end)}" }).joinToString(" · "), 13, Bold, C.Accent)
                T(p?.title ?: c?.name ?: "TV Guide", 28, Heavy)
                T(p?.description ?: "", 14, color = C.Soft, maxLines = 3, lineHeight = 21)
                if (c?.archive == true) Badge("Catch-up")
            }
            Clock(22)
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gridW = maxWidth - 240.dp
            val perSec = gridW / (windowEnd - windowStart).toFloat()
            Column {
                Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(if (favMode) "Favourites" else "All channels", 14, Bold, modifier = Modifier.width(240.dp).padding(start = 40.dp))
                    (0 until 4).forEach { T(fmtTime(windowStart + it * 1800L), 13, color = C.Muted, mono = true, modifier = Modifier.width(gridW / 4)) }
                }
                LazyColumn() {
                    itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                        LaunchedEffect(c.id) { app.loadEpg(c.id) }
                        val progs = app.epg[c.id].orEmpty().filter { it.end > windowStart && it.start < windowEnd }
                        Row(Modifier.height(72.dp)) {
                            Row(Modifier.width(240.dp).fillMaxHeight().padding(start = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Logo(c.icon, c.name, false, Modifier.size(44.dp, 32.dp))
                                T(c.name, 14, Bold)
                            }
                            Box(Modifier.width(gridW).fillMaxHeight().padding(vertical = 5.dp)) {
                                val open = { app.player.play(app.liveUrl(c)); app.push(Screen.LivePlayer(c, list)) }
                                if (progs.isEmpty()) {
                                    FocusSurface(open, Modifier.fillMaxSize().padding(end = 4.dp), idle = Color(0xFF11151F), shape = RoundedCornerShape(8.dp),
                                        focusRequester = if (i == 0) first else null, onFocus = { focused = c to null }) { f ->
                                        T("No programme info", 13, color = sub(f), modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp))
                                    }
                                }
                                progs.forEachIndexed { j, p ->
                                    val s = maxOf(p.start, windowStart); val e = minOf(p.end, windowEnd)
                                    val live = nowSec() in p.start until p.end
                                    FocusSurface(open, Modifier.offset(x = perSec * (s - windowStart).toFloat()).width(perSec * (e - s).toFloat() - 4.dp).fillMaxHeight(),
                                        idle = if (live) C.Control else Color(0xFF11151F), shape = RoundedCornerShape(8.dp),
                                        focusRequester = if (i == 0 && j == 0) first else null, onFocus = { focused = c to p }) { f ->
                                        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            T(p.title, 14, Bold, fg(f)); T(fmtTime(p.start), 12, color = sub(f), mono = true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val nowX = 240.dp + perSec * (nowSec() - windowStart).toFloat()
            Box(Modifier.offset(x = nowX).width(2.dp).fillMaxHeight().background(C.Accent))
        }
    }
}
