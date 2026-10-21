package com.avetero.relay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avetero.relay.BuildConfig
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.feature.categories.CategoriesScreen
import com.avetero.relay.feature.settings.sections.AppearanceSettings
import com.avetero.relay.feature.settings.sections.GuideSettings
import com.avetero.relay.feature.settings.sections.LiveSettings
import com.avetero.relay.feature.settings.sections.PlayerSettings
import com.avetero.relay.feature.settings.sections.PlaylistSettings
import com.avetero.relay.feature.settings.sections.UpdateSettings
import com.avetero.relay.ui.components.Badge
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.Group
import com.avetero.relay.ui.components.GroupChips
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.components.WithRail
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Semi
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact

/** Boards 14–16: Settings, one section at a time. shortcut: no parental PIN section yet. */
@Composable
fun SettingsScreen(app: AppState, section: String, nav: (Tab) -> Unit) = WithRail(Tab.Settings, nav) {
    val sections = listOf(
        "playlists" to "Playlists", "live" to "Live TV", "guide" to "TV guide", "player" to "Player",
        "appearance" to "Appearance", "categories" to "Manage categories", "updates" to "Updates & about",
    )
    val phone = compact()
    // "categories:vod" opens Categories on Movies; "hidden" and "order" are the old names
    val categories = section == "hidden" || section.startsWith("order") || section.startsWith("categories")
    val current = if (categories) "categories" else section.substringBefore(':')
    val catType = section.substringAfter(':', "live")
    if (phone) Column(Modifier.weight(1f).fillMaxHeight()) {
        T("Settings", 26, Heavy, modifier = Modifier.padding(16.dp))
        GroupChips(sections.map { Group(it.first, it.second, 0) }, current) { app.stack[app.stack.lastIndex] = Screen.Settings(it) }
        if (categories) { CategoriesScreen(app, catType, Modifier.weight(1f).padding(horizontal = 16.dp)); return@WithRail }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Spacer(Modifier.height(4.dp)) }
            item { SectionContent(app, section) }
            item { T("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", 12, color = C.Dim) }
        }
        return@WithRail
    }
    Column(Modifier.width(260.dp).fillMaxHeight().background(C.Panel).padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T("Settings", 22, Heavy, modifier = Modifier.padding(start = 12.dp, bottom = 18.dp))
        sections.forEach { (key, name) ->
            val on = key == current
            FocusSurface({ app.stack[app.stack.lastIndex] = Screen.Settings(key) }, Modifier.fillMaxWidth().height(46.dp), selected = on, shape = RoundedCornerShape(10.dp)) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(name, 15, if (on || f) Bold else Semi, if (f) C.OnLight else if (on) C.Text else C.Soft, Modifier.weight(1f))
                    if (key == "updates" && app.update != null) Badge("1", C.Accent, C.OnLight)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        T("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", 12, color = C.Dim, modifier = Modifier.padding(start = 12.dp))
    }
    if (categories) { CategoriesScreen(app, catType, Modifier.weight(1f).fillMaxHeight().padding(start = 32.dp, end = 32.dp, top = 26.dp)); return@WithRail }
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
