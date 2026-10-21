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
import com.avetero.relay.feature.settings.SettingRow
import com.avetero.relay.feature.settings.Title
import com.avetero.relay.util.clock24

@Composable
internal fun AppearanceSettings(app: AppState) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    var tick by remember { mutableIntStateOf(0) }
    val s = app.store.also { tick }
    Title("Appearance")
    SettingRow("24-hour clock", if (s.clock24) "Times show as 21:30" else "Times show as 9:30 PM", toggle = s.clock24) {
        s.clock24 = !s.clock24; clock24 = s.clock24; app.appearanceTick++; tick++
    }
}
