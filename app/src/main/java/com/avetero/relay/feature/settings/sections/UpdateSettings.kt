package com.avetero.relay.feature.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.avetero.relay.BuildConfig
import com.avetero.relay.app.AppState
import com.avetero.relay.data.update.Updater
import com.avetero.relay.feature.settings.SettingRow
import com.avetero.relay.feature.settings.Title
import com.avetero.relay.feature.update.ReleaseNotes
import com.avetero.relay.feature.update.UpdateDialog
import com.avetero.relay.ui.components.Badge
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun UpdateSettings(app: AppState) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
