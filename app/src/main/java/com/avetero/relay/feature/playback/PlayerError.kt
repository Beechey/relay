package com.avetero.relay.feature.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.theme.isTv
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvHints

/**
 * "This isn't playing": phone card (PhonePlayerStates) over the video area, TV dialog (TvError). Retry, the other
 * stream format when [onSwitch] is given, and Go back when [onBack] is. On TV focus stays inside; Up/Down still
 * reach the player underneath, so zapping keeps working.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerError(err: String, onRetry: () -> Unit, onSwitch: (() -> Unit)?, hls: Boolean = false, onBack: (() -> Unit)? = null) {
    val other = if (hls) "Try MPEG-TS" else "Try HLS"
    if (!isTv()) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(16.dp).widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                Text("This isn't playing", style = MaterialTheme.typography.titleMedium)
            }
            Text(err, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onRetry) { Text("Retry") }
                if (onSwitch != null) FilledTonalButton(onSwitch) { Text(other) }
                if (onBack != null) TextButton(onBack) { Text("Go back") }
            }
        }
    } else Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { first.settle() }
        val tc = androidx.tv.material3.MaterialTheme.colorScheme
        val ty = androidx.tv.material3.MaterialTheme.typography
        Column(Modifier.width(560.dp).clip(RoundedCornerShape(28.dp)).background(com.avetero.relay.ui.theme.Tok.Container).padding(32.dp)
            .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()
            // Back would otherwise be spent trying to move focus out of the panel
            .onPreviewKeyEvent { e -> if (e.key == Key.Back && onBack != null) { if (e.type == KeyEventType.KeyUp) onBack(); true } else false },
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(tc.error), contentAlignment = Alignment.Center) {
                androidx.tv.material3.Icon(Icons.Rounded.ErrorOutline, null, tint = tc.onError)
            }
            androidx.tv.material3.Text("This isn't playing", style = ty.headlineSmall)
            androidx.tv.material3.Text(err, style = ty.bodyLarge, color = tc.onSurfaceVariant, maxLines = 5)
            Row(Modifier.padding(top = 8.dp).height(56.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TvButton("Retry", onRetry, focusRequester = first)
                if (onSwitch != null) TvButton(other, onSwitch)
                if (onBack != null) TvButton("Go back", onBack)
            }
        }
        TvHints("▲▼" to "Next channel still works", "Back" to "Go back", modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 27.dp))
    }
}
