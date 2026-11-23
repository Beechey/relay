package com.avetero.relay.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.avetero.relay.app.AppState
import com.avetero.relay.app.StepState
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvHints

@Composable
internal fun SyncTv(app: AppState, error: String?, retry: () -> Unit, edit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    BackHandler(onBack = edit)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.width(640.dp).align(Alignment.TopCenter).padding(top = 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Loading ${app.account?.name ?: ""}", style = MaterialTheme.typography.displaySmall)
            Text("Live TV is ready in seconds; movies, series and the guide follow. Large playlists can take a minute the first time.",
                style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val steps = app.syncSteps
                steps.forEachIndexed { i, s ->
                    val shape = when {
                        steps.size == 1 -> RoundedCornerShape(24.dp)
                        i == 0 -> RoundedCornerShape(24.dp, 24.dp, 6.dp, 6.dp)
                        i == steps.lastIndex -> RoundedCornerShape(6.dp, 6.dp, 24.dp, 24.dp)
                        else -> RoundedCornerShape(6.dp)
                    }
                    val busy = s.state == StepState.Busy
                    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).background(Tok.Container, shape).padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        val (bg, fg) = when (s.state) {
                            StepState.Done -> cs.primary to cs.onPrimary
                            StepState.Busy -> cs.secondaryContainer to cs.onSecondaryContainer
                            StepState.Failed -> cs.errorContainer to cs.onErrorContainer
                            StepState.Todo -> Tok.ContainerHighest to cs.onSurfaceVariant
                        }
                        Box(Modifier.size(36.dp).background(bg, RoundedCornerShape(if (busy) 12.dp else 18.dp)), Alignment.Center) {
                            Text(when (s.state) { StepState.Done -> "✓"; StepState.Failed -> "!"; else -> "${i + 1}" }, style = MaterialTheme.typography.titleMedium, color = fg)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), color = if (s.state == StepState.Todo) cs.onSurfaceVariant else cs.onSurface)
                                Text(stepDetail(s).ifEmpty { if (s.state == StepState.Todo) "Waiting" else "" }, style = MaterialTheme.typography.bodyLarge,
                                    color = if (s.state == StepState.Failed) cs.error else cs.onSurfaceVariant)
                            }
                            if (busy && s.unit.isNotEmpty()) StepWave(s)
                        }
                    }
                }
            }
            if (error != null) {
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { first.settle() }
                Text(error, style = MaterialTheme.typography.bodyLarge, color = cs.error, maxLines = 3, modifier = Modifier.padding(top = 14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton("Try again", retry, primary = true, focusRequester = first)
                    TvButton("Edit details", edit)
                }
            }
        }
        TvHints("Back" to "Cancel and edit details", modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 27.dp))
    }
}
