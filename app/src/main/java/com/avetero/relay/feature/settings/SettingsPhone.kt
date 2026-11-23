package com.avetero.relay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avetero.relay.BuildConfig
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.update.Updater
import com.avetero.relay.data.xtream.Xtream
import com.avetero.relay.feature.categories.CategoriesPhone
import com.avetero.relay.feature.update.UpdateDialog
import com.avetero.relay.ui.m3.RelayTopBar
import com.avetero.relay.ui.m3.segmentShape
import com.avetero.relay.util.fmtDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsPhone(app: AppState, section: String) {
    if (section == "home") return SettingsHome(app)
    when (val key = sectionKey(section)) {
        "categories" -> CategoriesPhone(app, section.substringAfter(':', "live"))
        else -> Column(Modifier.fillMaxSize()) {
            RelayTopBar(sectionNames.first { it.first == key }.second, { app.back() }, actions = {
                if (key == "playlists") FilledTonalButton({ app.push(Screen.Login) }, Modifier.padding(end = 8.dp), contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Text("Add", Modifier.padding(start = 8.dp))
                }
            })
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        when (key) {
                            "player" -> playbackGroups(app, phone = true).forEach { PhoneGroup(it) }
                            "live" -> {
                                liveGroups(app).forEach { PhoneGroup(it) }
                                Note(GUIDE_NOTE)
                            }
                            "updates" -> PhoneAbout(app)
                            else -> PhonePlaylists(app)
                        }
                    }
                }
            }
        }
    }
}

/** Fetches the account's status and expiry once, for the playlist cards. */
@Composable
internal fun LoadAccountInfo(app: AppState) {
    val a = app.account
    LaunchedEffect(a) { if (a != null && app.info == null) app.info = runCatching { withContext(Dispatchers.IO) { Xtream(a).login() } }.getOrNull() }
}

internal fun accountLine(app: AppState) =
    app.info?.let { i -> listOfNotNull(i.status, i.expires?.let { "expires ${fmtDate(it)}" }).joinToString(" · ") } ?: "Checking…"

@Composable
private fun SettingsHome(app: AppState) = Column(Modifier.fillMaxSize()) {
    LoadAccountInfo(app)
    val list = rememberLazyListState()
    val start = remember { app.reselect }
    LaunchedEffect(app.reselect) { if (app.reselect != start) list.animateScrollToItem(0) }
    val cs = MaterialTheme.colorScheme
    RelayTopBar("", { app.back() })
    LazyColumn(Modifier.weight(1f), list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 8.dp)) }
        item {
            val a = app.account
            Surface({ app.push(Screen.Settings("playlists")) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Avatar(a?.name ?: "Relay", 56, primary = true)
                    Column(Modifier.weight(1f)) {
                        Text(a?.name ?: "Relay", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(accountLine(app), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                    OutlinedButton({ app.push(Screen.Settings("playlists")) }) { Text("Switch") }
                }
            }
        }
        item {
            val rows = listOf(
                Triple("playlists", Icons.AutoMirrored.Rounded.PlaylistPlay, "Logins, what downloads, refresh"),
                Triple("player", Icons.Rounded.PlayArrow, "Audio, subtitles, skip, buffer, clock"),
                Triple("live", Icons.Rounded.LiveTv, "Start channel, stream format, guide refresh"),
                Triple("categories", Icons.Rounded.Category, "Hide and reorder Live, Movies, Series"),
                Triple("updates", Icons.Rounded.Info, app.update?.let { "Version ${it.version} is ready" } ?: "Version ${BuildConfig.VERSION_NAME}"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                rows.forEachIndexed { i, (key, icon, sub) ->
                    HomeRow(sectionNames.first { it.first == key }.second, sub, icon, segmentShape(i, rows.size), badge = key == "updates" && app.update != null) {
                        app.push(Screen.Settings(if (key == "categories") "categories:live" else key))
                    }
                }
            }
        }
        item {
            Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HomeRow(title: String, sub: String, icon: ImageVector, shape: Shape, badge: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(onClick, Modifier.fillMaxWidth(), shape = shape, color = cs.surfaceContainer) {
        Row(Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(40.dp).background(cs.secondaryContainer, CircleShape), Alignment.Center) { Icon(icon, null, tint = cs.onSecondaryContainer) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            if (badge) Badge { Text("1") }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
internal fun Avatar(name: String, size: Int, primary: Boolean) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(size.dp).background(if (primary) cs.primary else cs.surfaceContainerHighest, CircleShape), Alignment.Center) {
        Text(name.take(1).uppercase(), style = if (size >= 48) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = if (primary) cs.onPrimary else cs.onSurfaceVariant)
    }
}

@Composable
private fun PhonePlaylists(app: AppState) {
    val a = app.account ?: return
    LoadAccountInfo(app)
    val cs = MaterialTheme.colorScheme
    var deleteAsk by remember { mutableStateOf(false) }
    // one stray tap used to delete the playlist outright
    if (deleteAsk) Confirm("Delete “${a.name}”?", "Relay forgets this playlist's login and saved catalogue. You'll need the login details to add it again.", "Delete",
        { deleteAsk = false; app.remove(a) }, { deleteAsk = false })
    Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(a.name, 48, primary = true)
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.server, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.background(cs.secondaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("In use", style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer)
                }
            }
            playlistStats(app).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (l, v) ->
                        Column(Modifier.weight(1f).background(cs.surfaceContainer, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(l, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ app.go(Screen.Sync) }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Refresh now") }
                OutlinedButton({ deleteAsk = true }, Modifier.weight(1f).heightIn(min = 48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cs.error)) { Text("Delete") }
            }
        }
    }
    app.store.playlists().filter { it != a }.forEach { other ->
        Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(other.name, 40, primary = false)
                Column(Modifier.weight(1f)) {
                    Text(other.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(other.server, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton({ app.info = null; app.switchTo(other) }) { Text("Switch to") }
            }
        }
    }
    val groups = playlistGroups(app)
    groups.forEachIndexed { i, g ->
        PhoneGroup(g)
        if (i == 0) Note(PARTS_NOTE)
    }
}

internal fun playlistStats(app: AppState): List<Pair<String, String>> {
    val i = app.info
    return listOf(
        "Status" to (i?.status ?: "Checking…"), "Expires" to (i?.expires?.let(::fmtDate) ?: "—"),
        "Connections" to (i?.let { "${it.activeConnections} of ${it.maxConnections}" } ?: "—"),
        "Last refreshed" to (app.account?.let { app.store.cacheTime(it) }?.takeIf { it > 0 }?.let { fmtDate(it / 1000) } ?: "—"),
    )
}

@Composable
private fun PhoneAbout(app: AppState) {
    val cs = MaterialTheme.colorScheme
    var showDialog by remember { mutableStateOf(false) }
    val (status, check) = rememberUpdateCheck(app)
    val r = app.update
    if (!Updater.enabled || r != null) Surface(shape = RoundedCornerShape(28.dp), color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (r == null) {
                Text("Updates aren't set up in this build", style = MaterialTheme.typography.titleLarge)
                Text("Set updateRepo in gradle.properties (and updateToken in local.properties for a private repo). See docs/06-github-updates.md.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.background(cs.primary, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("Update available", style = MaterialTheme.typography.labelMedium, color = cs.onPrimary)
                    }
                    Text("Released ${r.date} · %.1f MB".format(r.size / 1e6), style = MaterialTheme.typography.bodySmall)
                }
                Text("Version ${r.version}", style = MaterialTheme.typography.headlineMedium)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button({ showDialog = true }, Modifier.heightIn(min = 48.dp)) { Text("Download & install") }
                    Text("You're on ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    PhoneGroup(updateGroup(app))
    if (Updater.enabled && r == null) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (status != null) Text(status, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        OutlinedButton(check) { Text("Check for updates") }
    }
    Text("Relay IPTV · Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (showDialog && r != null) UpdateDialog(app, r) { showDialog = false }
}

@Composable
internal fun Note(text: String) =
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

/** A titled grouped list: 20dp outer corners, 4dp inner, 2dp apart. */
@Composable
internal fun PhoneGroup(g: SettingGroup) = Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    var choosing by remember { mutableStateOf<Choice?>(null) }
    if (g.header != null) Text(g.header, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp))
    g.rows.forEachIndexed { i, r -> PhoneSettingRow(r, segmentShape(i, g.rows.size)) { choosing = it } }
    choosing?.let { c -> ChoiceDialog(c) { choosing = null } }
}

@Composable
private fun GroupedRow(shape: Shape, onClick: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val body: @Composable () -> Unit = { Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
    if (onClick != null) Surface(onClick, Modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, content = body)
    else Surface(Modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, content = body)
}

@Composable
private fun PhoneSettingRow(r: Setting, shape: Shape, onChoose: (Choice) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val click: (() -> Unit)? = when (r) {
        is Toggle -> r.flip
        is Choice -> if (r.segmented) null else ({ onChoose(r) })
        is Action -> if (r.button == null) r.onClick else null
    }
    GroupedRow(shape, click) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(r.label, style = MaterialTheme.typography.titleMedium, color = if (r is Action && r.danger) cs.error else cs.onSurface)
                if (r.desc != null) Text(r.desc!!, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            when (r) {
                is Toggle -> {
                    if (r.extra != null) Text(r.extra, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Switch(r.on, null)
                }
                is Choice -> if (!r.segmented) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.current, style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = cs.primary)
                }
                is Action -> when {
                    r.button != null -> FilledTonalButton({ r.onClick?.invoke() }) { Text(r.button) }
                    r.value != null -> Text(r.value, style = MaterialTheme.typography.labelLarge, color = cs.primary)
                }
            }
        }
        if (r is Toggle && r.progress != null) {
            if (r.progress < 0) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            else LinearWavyProgressIndicator({ r.progress }, Modifier.fillMaxWidth())
        }
        if (r is Choice && r.segmented) Segmented(r.options, r.current, r.pick)
    }
}

/** Connected M3 Expressive toggle buttons, one per option. */
@Composable
internal fun Segmented(options: List<String>, current: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) =
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, o ->
            ToggleButton(o == current, { onPick(o) }, Modifier.weight(1f).heightIn(min = 48.dp),
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text(o, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }

/** Radio list for a longer choice; picking closes it. */
@Composable
private fun ChoiceDialog(c: Choice, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(c.label) },
    text = {
        Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
            c.options.forEach { o ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(o == c.current, role = Role.RadioButton) { c.pick(o); onDismiss() },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RadioButton(o == c.current, null)
                    Text(o, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    },
    confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
)
