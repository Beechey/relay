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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.avetero.relay.data.xtream.Xtream
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.LaunchedEffect
import com.avetero.relay.util.fmtDate
import com.avetero.relay.ui.theme.LiveGradient
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.Ico
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import com.avetero.relay.BuildConfig
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.feature.categories.CategoriesScreen
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

/** Board 08: Settings, one section at a time. shortcut: no Parental lock section yet. */
@Composable
fun SettingsScreen(app: AppState, section: String, nav: (Tab) -> Unit) = WithRail(Tab.Settings, nav) {
    val sections = listOf(
        Triple("playlists", "Playlists", Icons.User), Triple("player", "Playback", Icons.Play), Triple("live", "Live TV and guide", Icons.Live),
        Triple("categories", "Manage categories", Icons.List), Triple("updates", "About and updates", Icons.Settings),
    )
    val phone = compact()
    // "categories:vod" opens Categories on Movies; "hidden" and "order" are the old names
    val categories = section == "hidden" || section.startsWith("order") || section.startsWith("categories")
    // Appearance folded into Playback and the guide into Live TV; old links still land
    val current = if (categories) "categories" else when (val k = section.substringBefore(':')) { "appearance" -> "player"; "guide" -> "live"; else -> k }
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
    // land on the current section, like the other pages; stops once anything here has focus
    val navFocus = remember { FocusRequester() }
    var pageFocused by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) { if (windowFocused) repeat(15) { if (pageFocused) return@LaunchedEffect; delay(100); runCatching { navFocus.requestFocus() } } }
    Column(Modifier.onFocusChanged { if (it.hasFocus) pageFocused = true }.padding(start = 64.dp, bottom = 32.dp, top = 8.dp).width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val a = app.account
        LaunchedEffect(a) { if (a != null && app.info == null) app.info = runCatching { withContext(Dispatchers.IO) { Xtream(a).login() } }.getOrNull() }
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(20.dp)).background(C.Glass).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(LiveGradient)) { T(a?.name?.take(1)?.uppercase() ?: "R", 16, Heavy, C.OnLight, Modifier.align(Alignment.Center)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(a?.name ?: "Relay", 15, Heavy)
                T(app.info?.let { i -> listOfNotNull(i.status, i.expires?.let { "expires ${fmtDate(it)}" }).joinToString(" · ") } ?: "Checking…", 12, Semi, C.Muted)
            }
        }
        sections.forEach { (key, name, icon) ->
            val on = key == current
            FocusSurface({ app.stack[app.stack.lastIndex] = Screen.Settings(key) }, Modifier.fillMaxWidth().height(46.dp), idle = if (on) C.Accent.copy(alpha = .14f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp), lift = 1.04f, focusRequester = if (on) navFocus else null) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Ico(icon, if (f) C.OnLight else if (on) C.Accent else C.Muted, 18.dp)
                    T(name, 14, Bold, if (f) C.OnLight else if (on) C.Text else C.Soft, Modifier.weight(1f))
                    if (key == "updates" && app.update != null) Badge("1", C.Accent, C.OnLight)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        T("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", 12, color = C.Dim, modifier = Modifier.padding(start = 14.dp))
    }
    if (categories) { CategoriesScreen(app, catType, Modifier.weight(1f).fillMaxHeight().onFocusChanged { if (it.hasFocus) pageFocused = true }.padding(start = 28.dp, end = 64.dp, top = 8.dp)); return@WithRail }
    LazyColumn(Modifier.weight(1f).fillMaxHeight().onFocusChanged { if (it.hasFocus) pageFocused = true }.padding(start = 28.dp, end = 64.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        item { SectionContent(app, section) }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun SectionContent(app: AppState, section: String) = when (section.substringBefore(':')) {
    "live", "guide" -> LiveSettings(app)
    "player", "appearance" -> PlayerSettings(app)
    "updates" -> UpdateSettings(app)
    else -> PlaylistSettings(app)
}
