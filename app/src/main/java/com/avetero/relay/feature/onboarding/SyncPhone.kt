package com.avetero.relay.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.StepState
import com.avetero.relay.ui.m3.segmentShape

@Composable
internal fun SyncPhone(app: AppState, error: String?, retry: () -> Unit, edit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)) {
        if (error == null) LoadingIndicator(Modifier.size(64.dp)) else RelayMark()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Loading ${app.account?.name ?: ""}", style = MaterialTheme.typography.displaySmall)
            Text("Live TV is ready in seconds; movies, series and the guide follow.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val steps = app.syncSteps
            steps.forEachIndexed { i, s ->
                val busy = s.state == StepState.Busy
                Surface(shape = segmentShape(i, steps.size), color = if (busy) cs.surfaceContainerHigh else cs.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val (bg, fg) = when (s.state) {
                                StepState.Done -> cs.primary to cs.onPrimary
                                StepState.Busy -> cs.secondaryContainer to cs.onSecondaryContainer
                                StepState.Failed -> cs.errorContainer to cs.onErrorContainer
                                StepState.Todo -> cs.surfaceContainerHighest to cs.onSurfaceVariant
                            }
                            Box(Modifier.size(32.dp).background(bg, CircleShape), Alignment.Center) {
                                when (s.state) {
                                    StepState.Done -> Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = fg)
                                    StepState.Failed -> Icon(Icons.Rounded.PriorityHigh, null, Modifier.size(18.dp), tint = fg)
                                    else -> Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = fg)
                                }
                            }
                            Text(s.label, style = MaterialTheme.typography.titleMedium, color = if (s.state == StepState.Todo) cs.onSurfaceVariant else cs.onSurface, modifier = Modifier.weight(1f))
                            Text(stepDetail(s), style = MaterialTheme.typography.bodySmall, color = if (s.state == StepState.Failed) cs.error else cs.onSurfaceVariant)
                        }
                        if (busy && s.unit.isNotEmpty()) StepWave(s)
                    }
                }
            }
        }
        if (error != null) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(error, style = MaterialTheme.typography.bodyLarge, color = cs.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(retry, Modifier.height(48.dp)) { Text("Try again") }
                OutlinedButton(edit, Modifier.height(48.dp)) { Text("Edit details") }
            }
        } else Text("Large playlists can take a minute the first time.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
    }
}
