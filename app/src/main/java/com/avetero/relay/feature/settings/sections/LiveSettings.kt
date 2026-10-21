package com.avetero.relay.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.feature.settings.ChoiceRow
import com.avetero.relay.feature.settings.SettingRow
import com.avetero.relay.feature.settings.Title

@Composable
internal fun LiveSettings(app: AppState) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
