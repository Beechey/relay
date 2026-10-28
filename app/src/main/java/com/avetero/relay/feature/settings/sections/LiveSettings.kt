package com.avetero.relay.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.feature.settings.SegmentRow
import com.avetero.relay.feature.settings.SettingRow
import com.avetero.relay.feature.settings.SettingsGroup
import com.avetero.relay.feature.settings.Title
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.T
import kotlinx.coroutines.launch

/** Board 08: Live TV and the guide, merged. */
@Composable
internal fun LiveSettings(app: AppState) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    var tick by remember { mutableIntStateOf(0) }
    val s = app.store.also { tick }
    val scope = rememberCoroutineScope()
    Title("Live TV and guide")
    SettingsGroup {
        SegmentRow("Open Live TV on", null, listOf("All channels", "Last category"), s.liveStart) { s.liveStart = it }
        SettingRow("Resume the last channel", "Start playing the channel you watched last when you open Live TV", toggle = s.resumeLastChannel) { s.resumeLastChannel = !s.resumeLastChannel; tick++ }
        SettingRow("Show channel numbers", toggle = s.showNumbers) { s.showNumbers = !s.showNumbers; tick++ }
        SegmentRow("Live stream format", "MPEG-TS works with most providers; try HLS if channels fail", listOf("MPEG-TS", "HLS"), if (app.hls) "HLS" else "MPEG-TS") {
            app.hls = it == "HLS"; s.hls = app.hls
        }
    }
    SettingsGroup {
        SettingRow("Guide data", app.guideStatus.ifEmpty { "Full guide from your provider (XMLTV)" }, value = "Refresh now") {
            scope.launch { app.refreshGuide(force = true) }
        }
        SegmentRow("Refresh the guide", "In the background, when the saved one is older than this", listOf("6 hours", "12 hours", "Daily", "Manually"), s.guideEvery) { s.guideEvery = it }
    }
    T("The guide keeps 2 hours back to 24 hours ahead. Channels your provider doesn't cover use its short now-and-next data.", 13, color = C.Muted, maxLines = 3, lineHeight = 19, modifier = Modifier.padding(horizontal = 8.dp))
}
