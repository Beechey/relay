package com.avetero.relay.feature.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import com.avetero.relay.ui.components.ConfirmDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.xtream.Xtream
import com.avetero.relay.feature.settings.ChoiceRow
import com.avetero.relay.feature.settings.SettingRow
import com.avetero.relay.feature.settings.SettingsGroup
import com.avetero.relay.feature.settings.Stat
import com.avetero.relay.ui.components.Badge
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import com.avetero.relay.util.fmtDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PlaylistSettings(app: AppState) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    val a = app.account ?: return@Column
    LaunchedEffect(a) { if (app.info == null) app.info = runCatching { withContext(Dispatchers.IO) { Xtream(a).login() } }.getOrNull() }
    var refresh by remember { mutableIntStateOf(0) }
    var deleteAsk by remember { mutableStateOf(false) }
    // one stray OK used to delete the playlist outright
    if (deleteAsk) ConfirmDialog("Delete “${a.name}”?", "Delete", { deleteAsk = false; app.remove(a) }, { deleteAsk = false },
        message = "Relay forgets this playlist's login and saved catalogue. You'll need the login details to add it again.")
    Row(verticalAlignment = Alignment.CenterVertically) {
        T("Playlists", if (compact()) 22 else 30, Heavy, modifier = Modifier.weight(1f))
        Btn("+ Add playlist", { app.push(Screen.Login) }, style = BtnStyle.Primary, height = 44.dp)
    }
    Column(Modifier.clip(RoundedCornerShape(22.dp)).background(C.Glass).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(if (app.info != null) Color(0xFF3DD68C) else C.Dim))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { T(a.name, 17, Heavy); Spacer(Modifier.width(8.dp)); Badge("In use", C.Glass3, C.Text) }
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
            Btn("Refresh now", { app.go(Screen.Sync) }, style = BtnStyle.Primary, height = 42.dp)
            Btn("Delete", { deleteAsk = true }, style = BtnStyle.Destructive, height = 42.dp)
        }
    }
    app.store.playlists().filter { it != a }.forEach { other ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Glass).padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF4A5368)))
            Column(Modifier.weight(1f)) { T(other.name, 16, Bold); T(other.server, 13, color = C.Muted, mono = true) }
            Btn("Switch to", { app.info = null; app.switchTo(other) }, height = 40.dp)
        }
    }
    T("What Relay downloads", 19, Heavy, modifier = Modifier.padding(top = 8.dp))
    SettingsGroup {
        val hiddenLive = app.liveCats.count { "live:${it.id}" in app.hidden }
        listOf(
            Triple("live", "Live TV and guide", "%,d channels".format(app.channels.size) + if (hiddenLive > 0) " · %,d categories hidden".format(hiddenLive) else ""),
            Triple("vod", "Movies", "%,d movies".format(app.movies.size)),
            Triple("series", "Series", "%,d series".format(app.series.size)),
        ).forEach { (type, label, counts) ->
            val on = type in app.parts
            val size = app.store.cacheFile(a, type).length() / 1_048_576.0
            val loading = app.partLoading == type
            val pp = app.partProgress
            val unit = when (type) { "live" -> "channels"; "vod" -> "movies"; else -> "series" }
            SettingRow(label, when {
                loading && pp.reading -> "Reading… %,d %s".format(pp.count, unit)
                loading -> if (pp.bytes > 0) "Downloading… %.1f MB".format(pp.bytes / 1_048_576f) else "Downloading…"
                on -> counts
                type == "live" -> "Off: Live and the Guide go away, and nothing is downloaded"
                else -> "Off: the $label tab and its rows go away, and nothing is downloaded"
            }, toggle = on, progress = if (loading) pp.fraction else null, extra = if (!loading && on && size > 0) (if (size < 1) "%.1f MB" else "%.0f MB").format(size) else null) { app.setPart(type, !on) }
        }
    }
    SettingsGroup {
        var skip by remember { mutableStateOf(!app.store.downloadHidden) }
        SettingRow("Skip hidden categories",
            if (skip) "Hidden categories aren't downloaded. Show one again and it downloads then."
            else "Turn on to stop downloading what you've hidden: refreshing gets faster and uses less space. Applies at the next refresh.", toggle = skip) {
            skip = !skip; app.store.downloadHidden = !skip
        }
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Accent.copy(alpha = .08f)).padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        T("i", 14, Heavy, C.Accent)
        T("Turning one off removes it now. Turning it back on downloads it straight away. At least one stays on.", 13, color = C.Soft, maxLines = 3, lineHeight = 20)
    }
    SettingsGroup {
        ChoiceRow("Refresh the playlist", "Re-download channels, movies and series when the saved copy is older than this", listOf("Every start", "Daily", "Weekly", "Monthly", "Manually"), app.store.refreshEvery) { app.store.refreshEvery = it }
        SettingRow("Clear saved data", "Deletes the saved catalogue and guide, then downloads them again") { app.clearCacheAndReload() }
    }
}
