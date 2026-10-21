package com.avetero.relay.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.feature.settings.ChoiceRow
import com.avetero.relay.feature.settings.SettingRow
import com.avetero.relay.feature.settings.Title
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.T
import kotlinx.coroutines.launch

@Composable
internal fun GuideSettings(app: AppState) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    val scope = rememberCoroutineScope()
    Title("TV guide")
    SettingRow("Guide data", app.guideStatus.ifEmpty { "Full guide from your provider (XMLTV)" }, value = "Refresh now") {
        scope.launch { app.refreshGuide(force = true) }
    }
    ChoiceRow("Refresh the guide", "Download a fresh guide in the background when the saved one is older than this", listOf("6 hours", "12 hours", "Daily", "Manually"), app.store.guideEvery) { app.store.guideEvery = it }
    T("The guide keeps 2 hours back to 24 hours ahead. Channels your provider doesn't cover use its short now-and-next data.", 13, color = C.Muted, maxLines = 3, lineHeight = 19)
}
