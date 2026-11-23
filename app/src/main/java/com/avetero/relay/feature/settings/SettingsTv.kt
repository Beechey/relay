package com.avetero.relay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.avetero.relay.BuildConfig
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.update.Updater
import com.avetero.relay.feature.categories.CategoriesTv
import com.avetero.relay.feature.update.UpdateDialog
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.m3.segmentShape
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvHints
import com.avetero.relay.ui.tv.TvSidePanel
import com.avetero.relay.ui.tv.TvSurface

private val sectionIcons = mapOf("playlists" to Icons.AutoMirrored.Rounded.PlaylistPlay, "player" to Icons.Rounded.PlayArrow, "live" to Icons.Rounded.LiveTv,
    "categories" to Icons.Rounded.Category, "updates" to Icons.Rounded.Info)

/** TV: the section list on the left (focus lands on the current one), the section on the right. */
@Composable
internal fun SettingsTv(app: AppState, section: String) {
    val key = sectionKey(section)
    if (key == "categories") return CategoriesTv(app, section.substringAfter(':', "live"))
    LoadAccountInfo(app)
    val here = remember { FocusRequester() }
    LaunchedEffect(Unit) { here.settle() }
    // long lists (languages) pick from a side panel; short ones step through with OK
    var choice by remember { mutableStateOf<Choice?>(null) }
    var panel by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            // ◀ from a section's rows comes back to the open section, not whichever row sits level with it
            Column(Modifier.width(300.dp).fillMaxHeight().focusProperties { onEnter = { here.requestFocus() } }.focusGroup()
                .padding(start = 8.dp, end = 16.dp, top = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp))
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(48.dp).background(cs.secondaryContainer, CircleShape), Alignment.Center) {
                        Text(app.account?.name?.take(1)?.uppercase() ?: "R", style = MaterialTheme.typography.titleLarge, color = cs.onSecondaryContainer)
                    }
                    Column {
                        Text(app.account?.name ?: "Relay", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(accountLine(app), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                sectionNames.forEach { (k, name) ->
                    val on = k == key
                    TvSurface({
                        // categories is a full-screen page: push it so Back returns here
                        if (k == "categories") app.push(Screen.Settings("categories:live")) else if (!on) app.stack[app.stack.lastIndex] = Screen.Settings(k)
                    }, Modifier.fillMaxWidth().height(52.dp), selected = on, shape = RoundedCornerShape(26.dp), focusRequester = if (on) here else null) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(sectionIcons.getValue(k), null, Modifier.size(22.dp))
                            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (k == "updates" && app.update != null) Box(Modifier.size(20.dp).background(cs.primary, CircleShape), Alignment.Center) {
                                Text("1", style = MaterialTheme.typography.labelMedium, color = cs.onPrimary)
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 24.dp))
            }
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(start = 24.dp, end = 48.dp, top = 32.dp, bottom = 72.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val pick = { c: Choice -> choice = c; panel = true }
                        when (key) {
                            "player" -> { SectionTitle("Playback"); playbackGroups(app, phone = false).forEach { TvGroup(it, pick) } }
                            "live" -> {
                                SectionTitle("Live TV and guide")
                                liveGroups(app).forEach { TvGroup(it, pick) }
                                Text(GUIDE_NOTE, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            }
                            "updates" -> TvAbout(app)
                            else -> TvPlaylists(app, pick)
                        }
                    }
                }
            }
        }
        TvHints("◀" to "Sections", "OK" to "Toggle, or step through the choices", modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 27.dp))
        val first = remember { FocusRequester() }
        TvSidePanel(panel, { panel = false }, first, title = choice?.label) {
            choice?.let { c ->
                c.options.forEach { o ->
                    TvSurface({ c.pick(o); panel = false }, Modifier.fillMaxWidth().height(52.dp), focusRequester = if (o == c.current) first else null) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            RadioButton(o == c.current, null)
                            Text(o, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleLarge)

@Composable
private fun TvPlaylists(app: AppState, pick: (Choice) -> Unit) {
    val a = app.account ?: return
    val cs = MaterialTheme.colorScheme
    var deleteAsk by remember { mutableStateOf(false) }
    if (deleteAsk) Confirm("Delete “${a.name}”?", "Relay forgets this playlist's login and saved catalogue. You'll need the login details to add it again.", "Delete",
        { deleteAsk = false; app.remove(a) }, { deleteAsk = false })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Playlists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TvButton("+ Add playlist", { app.push(Screen.Login) })
    }
    Column(Modifier.fillMaxWidth().background(Tok.Container, RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(10.dp).background(if (app.info != null) cs.primary else cs.border, CircleShape))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(a.name, style = MaterialTheme.typography.titleMedium)
                    Text("In use", style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer,
                        modifier = Modifier.background(cs.secondaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Text("${a.server} · Xtream Codes", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            playlistStats(app).forEach { (k, v) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(k, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Text(v, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Refresh now", { app.go(Screen.Sync) }, primary = true)
            TvButton("Delete", { deleteAsk = true })
        }
    }
    app.store.playlists().filter { it != a }.forEach { other ->
        TvSurface({ app.info = null; app.switchTo(other) }, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(20.dp), idle = Tok.Container) {
            Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(10.dp).background(Tok.OutlineVariant, CircleShape))
                Text(other.name, style = MaterialTheme.typography.titleMedium)
                Text("· ${other.server}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Switch to", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    playlistGroups(app).forEachIndexed { i, g ->
        TvGroup(g, pick)
        if (i == 0) Text(PARTS_NOTE, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun TvAbout(app: AppState) {
    val cs = MaterialTheme.colorScheme
    var showDialog by remember { mutableStateOf(false) }
    val (status, check) = rememberUpdateCheck(app)
    val r = app.update
    SectionTitle("About and updates")
    if (!Updater.enabled || r != null) Row(Modifier.fillMaxWidth().background(cs.secondaryContainer, RoundedCornerShape(24.dp)).padding(horizontal = 24.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (r == null) {
                Text("Updates aren't set up in this build", style = MaterialTheme.typography.titleLarge, color = cs.onSecondaryContainer)
                Text("Set updateRepo in gradle.properties (and updateToken in local.properties for a private repo). See docs/06-github-updates.md.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer)
            } else {
                Text("UPDATE AVAILABLE", style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer)
                Text("Version ${r.version}", style = MaterialTheme.typography.titleLarge, color = cs.onSecondaryContainer)
                Text("Released ${r.date} · %.1f MB · You're on ${BuildConfig.VERSION_NAME}".format(r.size / 1e6), style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer)
            }
        }
        if (r != null) TvButton("Download & install", { showDialog = true }, primary = true)
    }
    TvGroup(SettingGroup(null, listOfNotNull(
        if (Updater.enabled && r == null) Action("Check for updates", status ?: "You're on ${BuildConfig.VERSION_NAME}", value = "Check now", onClick = check) else null,
    ) + updateGroup(app).rows + Action("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))) {}
    if (showDialog && r != null) UpdateDialog(app, r) { showDialog = false }
}

@Composable
private fun TvGroup(g: SettingGroup, pick: (Choice) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    if (g.header != null) Text(g.header, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
    g.rows.forEachIndexed { i, r -> TvSettingRow(r, segmentShape(i, g.rows.size), pick) }
}

@Composable
private fun TvSettingRow(r: Setting, shape: Shape, pick: (Choice) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val cs = MaterialTheme.colorScheme
    TvSurface({
        when (r) {
            is Toggle -> r.flip()
            is Choice -> if (r.options.size > 5) pick(r) else r.pick(r.options[(r.options.indexOf(r.current) + 1) % r.options.size])
            is Action -> r.onClick?.invoke()
        }
    }, Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = shape, idle = Tok.Container, interactionSource = src) {
        val sub = if (focused) LocalContentColor.current else cs.onSurfaceVariant
        val accent = if (focused) LocalContentColor.current else cs.primary
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(r.label, style = MaterialTheme.typography.titleMedium, color = if (r is Action && r.danger && !focused) cs.error else LocalContentColor.current)
                    if (r.desc != null) Text(r.desc!!, style = MaterialTheme.typography.bodyMedium, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when (r) {
                    is Toggle -> {
                        if (r.extra != null) Text(r.extra, style = MaterialTheme.typography.bodyMedium, color = sub)
                        Switch(r.on, null)
                    }
                    is Choice -> Text("${r.current} ›", style = MaterialTheme.typography.labelLarge, color = accent)
                    is Action -> (r.button ?: r.value)?.let { Text("$it ›", style = MaterialTheme.typography.labelLarge, color = accent) }
                }
            }
            if (r is Toggle && r.progress != null) {
                if (r.progress < 0) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                else LinearWavyProgressIndicator({ r.progress }, Modifier.fillMaxWidth())
            }
        }
    }
}
