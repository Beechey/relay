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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.items
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

/** Boards 14–16: Settings, one section at a time. shortcut: no parental PIN section yet. */
@Composable
fun SettingsScreen(app: AppState, section: String, nav: (Tab) -> Unit) = WithRail(Tab.Settings, nav) {
    val sections = listOf(
        "playlists" to "Playlists", "live" to "Live TV", "guide" to "TV guide", "player" to "Player",
        "appearance" to "Appearance", "hidden" to "Hidden categories", "updates" to "Updates & about",
    )
    val phone = compact()
    if (phone) Column(Modifier.weight(1f).fillMaxHeight()) {
        T("Settings", 26, Heavy, modifier = Modifier.padding(16.dp))
        GroupChips(sections.map { Group(it.first, it.second, 0) }, section) { app.stack[app.stack.lastIndex] = Screen.Settings(it) }
        if (section == "hidden") { CategoryManager(app, Modifier.weight(1f).padding(horizontal = 16.dp)); return@WithRail }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Spacer(Modifier.height(4.dp)) }
            item { SectionContent(app, section) }
            item { T("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", 12, color = C.Dim) }
        }
        return@WithRail
    }
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
    if (section == "hidden") { CategoryManager(app, Modifier.weight(1f).fillMaxHeight().padding(start = 40.dp, end = 40.dp, top = 28.dp)); return@WithRail }
    LazyColumn(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(14.dp)) }
        item { SectionContent(app, section) }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun SectionContent(app: AppState, section: String) = when (section) {
    "live" -> LiveSettings(app)
    "guide" -> GuideSettings(app)
    "player" -> PlayerSettings(app)
    "appearance" -> AppearanceSettings(app)
    "updates" -> UpdateSettings(app)
    else -> PlaylistSettings(app)
}

/** A setting with a fixed set of choices: shows the current one, opens a picker. */
@Composable
private fun ChoiceRow(label: String, desc: String?, options: List<String>, current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var shown by remember(current) { mutableStateOf(current) }
    SettingRow(label, desc, value = shown) { open = true }
    if (open) androidx.compose.ui.window.Dialog({ open = false }) {
        val first = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T(label, 18, Heavy, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
            options.forEach { o ->
                FocusSurface({ shown = o; onPick(o); open = false }, Modifier.fillMaxWidth().height(48.dp), selected = o == shown, shape = RoundedCornerShape(10.dp),
                    focusRequester = if (o == shown) first else null) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(o, 15, Bold, fg(f), Modifier.weight(1f))
                        if (o == shown) T("✓", 15, Heavy, if (f) C.OnLight else C.Accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) = T(text, if (compact()) 20 else 24, Heavy)

@Composable
private fun LiveSettings(app: AppState) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    var tick by remember { mutableIntStateOf(0) }
    val s = app.store.also { tick }
    Title("Live TV")
    ChoiceRow("Open Live TV on", null, listOf("All channels", "Last category"), s.liveStart) { s.liveStart = it }
    SettingRow("Resume the last channel", "Start playing the channel you watched last when you open Live TV", toggle = s.resumeLastChannel) { s.resumeLastChannel = !s.resumeLastChannel; tick++ }
    SettingRow("Show channel numbers", toggle = s.showNumbers) { s.showNumbers = !s.showNumbers; tick++ }
    SettingRow("Live stream format", "MPEG-TS works with most providers; try HLS if channels fail", value = if (app.hls) "HLS" else "MPEG-TS") {
        app.hls = !app.hls; s.hls = app.hls; tick++
    }
}

@Composable
private fun GuideSettings(app: AppState) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    val scope = rememberCoroutineScope()
    Title("TV guide")
    SettingRow("Guide data", app.guideStatus.ifEmpty { "Full guide from your provider (XMLTV)" }, value = "Refresh now") {
        scope.launch { app.refreshGuide(force = true) }
    }
    ChoiceRow("Refresh the guide", "Download a fresh guide in the background when the saved one is older than this", listOf("6 hours", "12 hours", "Daily", "Manually"), app.store.guideEvery) { app.store.guideEvery = it }
    T("The guide keeps 2 hours back to 24 hours ahead. Channels your provider doesn't cover use its short now-and-next data.", 13, color = C.Muted, maxLines = 3, lineHeight = 19)
}

@Composable
private fun AppearanceSettings(app: AppState) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    var tick by remember { mutableIntStateOf(0) }
    val s = app.store.also { tick }
    Title("Appearance")
    SettingRow("24-hour clock", if (s.clock24) "Times show as 21:30" else "Times show as 9:30 PM", toggle = s.clock24) {
        s.clock24 = !s.clock24; com.avetero.relay.clock24 = s.clock24; app.appearanceTick++; tick++
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
        T("Playlists", if (compact()) 20 else 24, Heavy, modifier = Modifier.weight(1f))
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
        val stats = listOf(
            "Status" to (i?.status ?: "Checking…"), "Expires" to (i?.expires?.let(::fmtDate) ?: "—"),
            "Connections" to (i?.let { "${it.activeConnections} of ${it.maxConnections}" } ?: "—"),
            "Last refreshed" to (app.store.cacheTime(a).takeIf { it > 0 }?.let { fmtDate(it / 1000) } ?: "—"),
        )
        stats.chunked(if (compact()) 2 else 4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { (l, v) -> Stat(l, v, Modifier.weight(1f)) } }
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
    ChoiceRow("Refresh the playlist", "Re-download channels, movies and series when the saved copy is older than this", listOf("Every start", "Daily", "Weekly", "Monthly", "Manually"), app.store.refreshEvery) { app.store.refreshEvery = it }
    SettingRow("Clear saved data", "Deletes the saved catalogue and guide, then downloads them again") { app.clearCacheAndReload() }
}

@Composable
private fun PlayerSettings(app: AppState) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    var tick by remember { mutableIntStateOf(0) }
    val s = app.store.also { tick }
    val langs = listOf("Default" to "", "English" to "en", "Spanish" to "es", "French" to "fr", "German" to "de", "Italian" to "it", "Portuguese" to "pt", "Dutch" to "nl", "Polish" to "pl", "Turkish" to "tr", "Arabic" to "ar", "Hindi" to "hi")
    Title("Player")
    ChoiceRow("Preferred audio language", "Used when a stream has more than one audio track", langs.map { it.first }, langs.firstOrNull { it.second == s.audioLang }?.first ?: "Default") { n -> s.audioLang = langs.first { it.first == n }.second }
    SettingRow("Subtitles on by default", toggle = s.subtitlesOn) { s.subtitlesOn = !s.subtitlesOn; tick++ }
    ChoiceRow("Preferred subtitle language", null, langs.map { it.first }, langs.firstOrNull { it.second == s.subLang }?.first ?: "Default") { n -> s.subLang = langs.first { it.first == n }.second }
    ChoiceRow("Skip back", "Movies and episodes", listOf("5 s", "10 s", "15 s", "30 s"), "${s.skipBackSec} s") { s.skipBackSec = it.removeSuffix(" s").toInt() }
    ChoiceRow("Skip forward", "Movies and episodes", listOf("10 s", "15 s", "30 s", "60 s"), "${s.skipFwdSec} s") { s.skipFwdSec = it.removeSuffix(" s").toInt() }
    SettingRow("Zoom to fill the screen", "Crop the picture instead of showing black bars", toggle = s.zoomDefault) { s.zoomDefault = !s.zoomDefault; tick++ }
    SettingRow("Play next episode automatically", toggle = s.autoplayNext) { s.autoplayNext = !s.autoplayNext; tick++ }
    if (compact()) SettingRow("Picture-in-picture when leaving", "Keep watching in a floating window when you go home", toggle = s.pipOnLeave) { s.pipOnLeave = !s.pipOnLeave; tick++ }
    ChoiceRow("Buffer size", "Bigger buffers ride out slow connections but start slower. Applies next time the app starts.", listOf("Small", "Medium", "Large"), s.buffer) { s.buffer = it }
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
    Title("Updates & about")
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
                ReleaseNotes(r.notes)
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

/**
 * Settings → Hidden categories: every category with an on/off switch, filterable, with bulk
 * "Hide all listed" / "Show all listed" for whatever the filter matches. Terms are comma-separated, and
 * "Not containing" flips the match: e.g. "UK|, US|" + Not containing + Hide all listed keeps only UK and US.
 */
@Composable
private fun CategoryManager(app: AppState, modifier: Modifier) = Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    var type by rememberSaveable { mutableStateOf("live") }
    var q by rememberSaveable { mutableStateOf("") }
    val cats = when (type) { "vod" -> app.vodCats; "series" -> app.seriesCats; else -> app.liveCats }
    var exclude by rememberSaveable { mutableStateOf(false) }
    // comma-separated terms; "not containing" lists what matches none of them (keep UK| and US|, hide the rest)
    val terms = remember(q) { q.split(',').map { norm(it.trim()) }.filter { it.isNotEmpty() } }
    val shown = remember(cats, terms, exclude) {
        if (terms.isEmpty()) cats else cats.filter { c -> norm(c.name).let { n -> terms.any { n.contains(it) } } != exclude }
    }
    val hiddenHere = cats.count { "$type:${it.id}" in app.hidden }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Title("Hidden categories")
        Spacer(Modifier.weight(1f))
        T("${"%,d".format(app.hidden.size)} hidden", 13, color = C.Muted)
    }
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF11151F)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("live" to "Live TV", "vod" to "Movies", "series" to "Series").forEach { (k, label) ->
            FocusSurface({ type = k }, Modifier.height(40.dp), selected = type == k, shape = RoundedCornerShape(9.dp)) { f ->
                T(label, 14, Bold, if (f) C.OnLight else if (type == k) C.Text else C.Muted, Modifier.align(Alignment.Center).padding(horizontal = 16.dp))
            }
        }
    }
    FilterField(q, { q = it }, "Filter, e.g. AR| — several: UK|, US|")
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF11151F)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(false to "Containing", true to "Not containing").forEach { (v, label) ->
            FocusSurface({ exclude = v }, Modifier.height(36.dp), selected = exclude == v, shape = RoundedCornerShape(9.dp)) { f ->
                T(label, 13, Bold, if (f) C.OnLight else if (exclude == v) C.Text else C.Muted, Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Btn("Hide all listed", { app.setCategoriesHidden(shown.map { "$type:${it.id}" }, true) }, height = 40.dp, style = BtnStyle.Destructive, enabled = shown.isNotEmpty())
        Btn("Show all listed", { app.setCategoriesHidden(shown.map { "$type:${it.id}" }, false) }, height = 40.dp, enabled = shown.isNotEmpty())
        T("${"%,d".format(shown.size)} listed · ${"%,d".format(hiddenHere)} hidden here", 12, color = C.Dim)
    }
    LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(shown, key = { it.id }) { c ->
            val key = "$type:${c.id}"
            val visible = key !in app.hidden
            FocusSurface({ app.setCategoryHidden(key, visible) }, Modifier.fillMaxWidth().height(56.dp), idle = C.Surface, shape = RoundedCornerShape(12.dp)) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    T(c.name, 15, Bold, if (visible) fg(f) else sub(f), Modifier.weight(1f))
                    T(if (visible) "Shown" else "Hidden", 13, Bold, sub(f))
                    Toggle(visible, f)
                }
            }
        }
    }
}
