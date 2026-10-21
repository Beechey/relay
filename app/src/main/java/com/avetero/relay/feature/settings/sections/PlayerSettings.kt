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
import com.avetero.relay.ui.theme.compact

@Composable
internal fun PlayerSettings(app: AppState) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
