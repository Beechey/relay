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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.avetero.relay.app.AppState
import com.avetero.relay.data.update.Release
import com.avetero.relay.data.update.Updater
import com.avetero.relay.ui.components.Badge
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.components.Panel
import com.avetero.relay.ui.components.ProgressLine
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Board 18: GitHub update prompt with download progress. */
@Composable
fun UpdateDialog(app: AppState, r: Release, onDismiss: () -> Unit) = Dialog(onDismiss) {
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Panel(440) {
        Badge("Update available", C.Accent, C.OnLight)
        T("Version ${r.version} is ready", 26, Heavy)
        ReleaseNotes(r.notes.ifBlank { "Bug fixes and improvements." })
        progress?.let { p ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(if (p >= 1f) "Downloaded" else "Downloading… ${(p * 100).toInt()}%", 13, Bold)
                ProgressLine(p, Modifier.fillMaxWidth(), height = 6.dp)
            }
        }
        error?.let { T(it, 13, Bold, C.Error, maxLines = 2) }
        T("Android will ask you to confirm the install. The first time, allow this app to install updates.", 12, color = C.Muted, maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Install now", {
                if (progress != null) return@Btn
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
            }, Modifier.weight(1f), style = BtnStyle.Primary, focusRequester = first)
            Btn("Later", onDismiss, Modifier.weight(1f))
        }
        Btn("Skip this version", { app.store.skippedVersion = r.version; onDismiss() }, Modifier.fillMaxWidth(), height = 40.dp)
    }
}

/** "**bold**" spans to an AnnotatedString. */
private fun inlineMd(s: String) = buildAnnotatedString {
    s.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = Heavy, color = C.Text)) { append(part) } else append(part)
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
    // the first "# Relay IPTV x.y.z" line repeats the dialog title
    val lines = md.trim().lines().let { if (it.firstOrNull()?.startsWith("# ") == true) it.drop(1) else it }.dropWhile { it.isBlank() }
    Column(
        modifier.heightIn(max = 240.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (focused) C.Raised else C.Panel)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = when (e.key) { Key.DirectionDown -> 160f; Key.DirectionUp -> -160f; else -> return@onPreviewKeyEvent false }
                // past either end, let focus move on to the buttons
                if ((step > 0 && !scroll.canScrollForward) || (step < 0 && !scroll.canScrollBackward)) return@onPreviewKeyEvent false
                scope.launch { scroll.animateScrollBy(step) }; true
            }
            .focusable()
            .verticalScroll(scroll)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                line.startsWith("#") -> T(line.trimStart('#').trim(), 15, Heavy, maxLines = 2)
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("•", 14, Heavy, C.Accent)
                    Text(inlineMd(line.trimStart().drop(2)), color = C.Soft, fontSize = 14.sp, lineHeight = 21.sp)
                }
                else -> Text(inlineMd(line), color = C.Soft, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}
