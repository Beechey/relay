package com.avetero.relay.feature.update

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.avetero.relay.BuildConfig
import com.avetero.relay.app.AppState
import com.avetero.relay.data.update.Release
import com.avetero.relay.data.update.Updater
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.theme.isTv
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** GitHub update prompt with download progress: an M3 dialog on phones, a [TvDialog] on TV. */
@Composable
fun UpdateDialog(app: AppState, r: Release, onDismiss: () -> Unit) {
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val install = {
        if (progress == null) {
            progress = 0f; error = null
            scope.launch {
                try {
                    val apk = withContext(Dispatchers.IO) { Updater.download(app.ctx, r) { progress = it } }
                    progress = 1f
                    Updater.install(app.ctx, apk)
                } catch (e: Exception) {
                    error = e.message ?: "Download failed"; progress = null
                }
            }
        }
    }
    val skip = { app.store.skippedVersion = r.version; onDismiss() }
    val notes = r.notes.ifBlank { "Bug fixes and improvements." }
    val meta = "Released ${r.date} · %.1f MB".format(r.size / 1e6)
    if (isTv()) TvDialog(onDismiss) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { first.settle() }
        val cs = androidx.tv.material3.MaterialTheme.colorScheme
        val type = androidx.tv.material3.MaterialTheme.typography
        Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(32.dp), tint = cs.primary)
        Column {
            androidx.tv.material3.Text("Update available", style = type.headlineMedium)
            androidx.tv.material3.Text("Version ${r.version} is ready · $meta", style = type.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        ReleaseNotes(notes)
        Progress(progress, error)
        androidx.tv.material3.Text("Android will ask you to confirm the install. The first time, allow this app to install updates.", style = type.bodyMedium, color = cs.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TvButton("Skip this version", skip)
            TvButton("Later", onDismiss)
            TvButton("Install now", install, primary = true, focusRequester = first)
        }
    } else BasicAlertDialog(onDismiss) {
        val cs = MaterialTheme.colorScheme
        Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Rounded.SystemUpdate, null, Modifier.align(Alignment.CenterHorizontally), tint = cs.primary)
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version ${r.version} is ready", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text("$meta · you're on ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                ReleaseNotes(notes, Modifier.heightIn(max = 200.dp))
                Progress(progress, error)
                Text("Android will ask you to confirm the install. The first time, allow this app to install updates.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(install, Modifier.fillMaxWidth().height(48.dp), enabled = progress == null) { Text("Install now") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(skip) { Text("Skip this version") }
                        TextButton(onDismiss) { Text("Later") }
                    }
                }
            }
        }
    }
}

/** Download progress (wavy, M3 Expressive) or the error that stopped it. */
@Composable
private fun Progress(progress: Float?, error: String?) {
    val cs = MaterialTheme.colorScheme
    val style = if (isTv()) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
    progress?.let { p ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (p >= 1f) "Downloaded" else "Downloading… ${(p * 100).toInt()}%", style = style, color = cs.onSurfaceVariant)
            LinearWavyProgressIndicator({ p }, Modifier.fillMaxWidth())
        }
    }
    error?.let { Text(it, style = style, color = cs.error, maxLines = 2) }
}

/** "**bold**" spans to an AnnotatedString. */
private fun inlineMd(s: String, color: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
    s.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = color)) { append(part) } else append(part)
    }
}

/**
 * Release notes from GitHub, rendered and scrollable (D-pad Up/Down when focused, or touch).
 * shortcut: a small Markdown subset (# headings, "-" or "*" bullets, **bold**), enough for our release notes.
 */
@Composable
fun ReleaseNotes(md: String, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val tv = isTv()
    val body = if (tv) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
    // the first "# Relay IPTV x.y.z" line repeats the dialog title
    val lines = md.trim().lines().let { if (it.firstOrNull()?.startsWith("# ") == true) it.drop(1) else it }.dropWhile { it.isBlank() }
    Column(
        modifier.heightIn(max = 240.dp).fillMaxWidth()
            .background(if (focused) cs.surfaceContainerHighest else cs.surfaceContainer, RoundedCornerShape(16.dp))
            .then(if (tv) Modifier.onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val step = when (e.key) { Key.DirectionDown -> 160f; Key.DirectionUp -> -160f; else -> return@onPreviewKeyEvent false }
                    // past either end, let focus move on to the buttons
                    if ((step > 0 && !scroll.canScrollForward) || (step < 0 && !scroll.canScrollBackward)) return@onPreviewKeyEvent false
                    scope.launch { scroll.animateScrollBy(step) }; true
                }
                .focusable() else Modifier)
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("What's new", style = body.copy(fontWeight = FontWeight.Medium), color = cs.onSurface)
        lines.forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                line.startsWith("#") -> Text(line.trimStart('#').trim(), style = body.copy(fontWeight = FontWeight.Medium), color = cs.onSurface, maxLines = 2)
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = body, color = cs.primary)
                    Text(inlineMd(line.trimStart().drop(2), cs.onSurface), style = body, color = cs.onSurfaceVariant)
                }
                else -> Text(inlineMd(line, cs.onSurface), style = body, color = cs.onSurfaceVariant)
            }
        }
    }
}
