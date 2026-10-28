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
import com.avetero.relay.feature.settings.SegmentRow
import com.avetero.relay.feature.settings.SettingRow
import com.avetero.relay.feature.settings.SettingsGroup
import com.avetero.relay.feature.settings.Title
import com.avetero.relay.ui.theme.compact
import com.avetero.relay.util.clock24

/** Board 08: Playback, which also holds what used to be Appearance. */
@Composable
internal fun PlayerSettings(app: AppState) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    var tick by remember { mutableIntStateOf(0) }
    val s = app.store.also { tick }
    val langs = listOf("Default" to "", "English" to "en", "Spanish" to "es", "French" to "fr", "German" to "de", "Italian" to "it", "Portuguese" to "pt", "Dutch" to "nl", "Polish" to "pl", "Turkish" to "tr", "Arabic" to "ar", "Hindi" to "hi")
    Title("Playback")
    SettingsGroup {
        ChoiceRow("Preferred audio language", "Used when a stream has more than one audio track", langs.map { it.first }, langs.firstOrNull { it.second == s.audioLang }?.first ?: "Default") { n -> s.audioLang = langs.first { it.first == n }.second }
        SettingRow("Subtitles", "On by default, in your preferred language", toggle = s.subtitlesOn) { s.subtitlesOn = !s.subtitlesOn; tick++ }
        ChoiceRow("Preferred subtitle language", null, langs.map { it.first }, langs.firstOrNull { it.second == s.subLang }?.first ?: "Default") { n -> s.subLang = langs.first { it.first == n }.second }
        SettingRow("Play the next episode automatically", toggle = s.autoplayNext) { s.autoplayNext = !s.autoplayNext; tick++ }
        if (compact()) SettingRow("Picture-in-picture when leaving", "Keep watching in a floating window when you go home", toggle = s.pipOnLeave) { s.pipOnLeave = !s.pipOnLeave; tick++ }
    }
    SettingsGroup {
        SegmentRow("Skip back", "Left on the remote", listOf("5 s", "10 s", "15 s", "30 s"), "${s.skipBackSec} s") { s.skipBackSec = it.removeSuffix(" s").toInt() }
        SegmentRow("Skip forward", "Right on the remote", listOf("10 s", "15 s", "30 s", "60 s"), "${s.skipFwdSec} s") { s.skipFwdSec = it.removeSuffix(" s").toInt() }
        SegmentRow("Picture", "Fit shows the whole picture; Fill crops to the screen", listOf("Fit", "Fill"), if (s.zoomDefault) "Fill" else "Fit") { s.zoomDefault = it == "Fill" }
        SegmentRow("Buffer", "Bigger rides out slow connections, starts slower. Applies next start.", listOf("Small", "Medium", "Large"), s.buffer) { s.buffer = it }
    }
    SettingsGroup {
        SettingRow("24-hour clock", if (s.clock24) "Times show as 21:30" else "Times show as 9:30 PM", toggle = s.clock24) {
            s.clock24 = !s.clock24; clock24 = s.clock24; app.appearanceTick++; tick++
        }
    }
}
