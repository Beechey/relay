package com.avetero.relay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.avetero.relay.AppState
import com.avetero.relay.BuildConfig
import com.avetero.relay.Screen
import com.avetero.relay.Updater
import com.avetero.relay.Xtream
import com.avetero.relay.fmtDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Boards 14–16. shortcut: TV guide, appearance, parental and remote sections from the design aren't built yet (docs M2/M4).
 */
@Composable
fun SettingsScreen(app: AppState, section: String, nav: (Tab) -> Unit) = WithRail(Tab.Settings, nav) {
    val sections = listOf("playlists" to "Playlists", "player" to "Player", "updates" to "Updates & about")
    Column(Modifier.width(260.dp).fillMaxHeight().background(C.Panel).padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T("Settings", 22, Heavy, modifier = Modifier.padding(start = 12.dp, bottom = 18.dp))
        sections.forEach { (key, name) ->
            FocusSurface({ app.stack[app.stack.lastIndex] = Screen.Settings(key) }, Modifier.fillMaxWidth().height(46.dp), selected = key == section, shape = RoundedCornerShape(10.dp)) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(name, 15, if (key == section || f) Bold else Semi, if (f) C.OnLight else if (key == section) C.Text else C.Soft, Modifier.weight(1f))
                    if (key == "updates" && app.update != null) Badge("1", C.Accent, C.OnLight)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        T("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", 12, color = C.Dim, modifier = Modifier.padding(start = 12.dp))
    }
    LazyColumn(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(14.dp)) }
        when (section) {
            "player" -> item { PlayerSettings(app) }
            "updates" -> item { UpdateSettings(app) }
            else -> item { PlaylistSettings(app) }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) =
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(C.Panel).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        T(label, 12, color = C.Muted); T(value, 15, Bold)
    }

@Composable
private fun PlaylistSettings(app: AppState) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    val a = app.account ?: return@Column
    LaunchedEffect(a) { if (app.info == null) app.info = runCatching { withContext(Dispatchers.IO) { Xtream(a).login() } }.getOrNull() }
    var refresh by remember { mutableIntStateOf(0) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        T("Playlists", 24, Heavy, modifier = Modifier.weight(1f))
        Btn("+ Add playlist", { app.push(Screen.Login) }, style = BtnStyle.Primary, height = 44.dp)
    }
    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(C.Surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(if (app.info != null) Color(0xFF3DD68C) else C.Dim))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { T(a.name, 17, Heavy); Spacer(Modifier.width(8.dp)); Badge("In use", C.Text, C.OnLight) }
                T("${a.server} · Xtream Codes", 13, color = C.Muted, mono = true)
            }
        }
        val i = app.info
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Status", i?.status ?: "Checking…", Modifier.weight(1f))
            Stat("Expires", i?.expires?.let(::fmtDate) ?: "—", Modifier.weight(1f))
            Stat("Connections", i?.let { "${it.activeConnections} of ${it.maxConnections}" } ?: "—", Modifier.weight(1f))
            Stat("Last refreshed", app.store.cacheTime(a).takeIf { it > 0 }?.let { fmtDate(it / 1000) } ?: "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Refresh now", { app.go(Screen.Sync) }, style = BtnStyle.Light, height = 42.dp)
            Btn("Delete", { app.remove(a) }, style = BtnStyle.Destructive, height = 42.dp)
        }
    }
    app.store.playlists().filter { it != a }.forEach { other ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface).padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF4A5368)))
            Column(Modifier.weight(1f)) { T(other.name, 16, Bold); T(other.server, 13, color = C.Muted, mono = true) }
            Btn("Switch to", { app.info = null; app.switchTo(other) }, height = 40.dp)
        }
    }
    SettingRow("Refresh playlist on start", "Reload channels, movies and series when the app opens and the saved copy is over 12 hours old", toggle = app.store.refreshOnStart.also { refresh }) {
        app.store.refreshOnStart = !app.store.refreshOnStart; refresh++
    }
}

@Composable
private fun PlayerSettings(app: AppState) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    var tick by remember { mutableIntStateOf(0) }
    val s = app.store.also { tick }
    T("Player", 24, Heavy)
    SettingRow("Buffer size", "Bigger buffers ride out slow connections but start slower. Applies next time the app starts.", value = s.buffer) {
        s.buffer = listOf("Small", "Medium", "Large").let { it[(it.indexOf(s.buffer) + 1) % it.size] }; tick++
    }
    SettingRow("Live stream format", "MPEG-TS works with most providers; try HLS if channels fail", value = if (app.hls) "HLS" else "MPEG-TS") {
        app.hls = !app.hls; s.hls = app.hls; tick++
    }
    SettingRow("Subtitles on by default", toggle = s.subtitlesOn) { s.subtitlesOn = !s.subtitlesOn; tick++ }
    SettingRow("Play next episode automatically", toggle = s.autoplayNext) { s.autoplayNext = !s.autoplayNext; tick++ }
}

@Composable
private fun UpdateSettings(app: AppState) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    var tick by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val s = app.store.also { tick }
    fun check() {
        status = "Checking…"
        scope.launch {
            status = try {
                val r = withContext(Dispatchers.IO) { Updater.latest(s.betaUpdates) }
                app.update = r?.takeIf(Updater::isNewer)
                if (app.update == null) "You're up to date" else null
            } catch (e: Exception) { "Couldn't reach GitHub: ${e.message}" }
        }
    }
    T("Updates & about", 24, Heavy)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val r = app.update
        when {
            !Updater.enabled -> {
                T("Updates aren't set up in this build", 18, Heavy)
                T("Set updateRepo in gradle.properties (and updateToken in local.properties for a private repo). See docs/06-github-updates.md.", 14, color = C.Soft, maxLines = 3)
            }
            r != null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Badge("Update available", C.Accent, C.OnLight); T("Released ${r.date} · %.1f MB".format(r.size / 1e6), 13, color = C.Muted)
                }
                T("Version ${r.version}", 22, Heavy)
                T(r.notes, 14, color = Color(0xFFC9CED8), maxLines = 6, lineHeight = 22)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Download & install", { showDialog = true }, style = BtnStyle.Light, height = 46.dp)
                    T("You're on ${BuildConfig.VERSION_NAME}", 13, color = C.Muted)
                }
            }
            else -> {
                T(status ?: "Version ${BuildConfig.VERSION_NAME}", 18, Heavy)
                Btn("Check for updates", ::check, style = BtnStyle.Light, height = 46.dp)
            }
        }
    }
    SettingRow("Check for updates automatically", "Checks GitHub releases when the app starts", toggle = s.checkUpdates) { s.checkUpdates = !s.checkUpdates; tick++ }
    SettingRow("Include beta releases", "Get pre-release builds before everyone else", toggle = s.betaUpdates) { s.betaUpdates = !s.betaUpdates; tick++ }
    SettingRow("Update source", value = if (Updater.enabled) "github.com/${Updater.repo}" else "Not configured") {}
    app.update?.let { if (showDialog) UpdateDialog(app, it) { showDialog = false } }
}
