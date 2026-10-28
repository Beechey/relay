package com.avetero.relay.feature.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.app.StepState
import com.avetero.relay.app.SyncStep
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.components.ProgressLine
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.LogoMark
import com.avetero.relay.ui.theme.Semi
import com.avetero.relay.ui.theme.T

/** Board 02. */
@Composable
fun SyncScreen(app: AppState) {
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        error = null
        try { app.sync(); app.go(Screen.Home) } catch (e: Exception) { error = e.message ?: "Couldn't load the playlist" }
    }
    val a = app.account
    Box(Modifier.fillMaxSize().background(C.Ground), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(16.dp).widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LogoMark(52.dp)
                Column {
                    T("Loading ${a?.name ?: ""}", 28, Heavy)
                    T(a?.server ?: "", 14, color = C.Muted, mono = true)
                }
            }
            // overall bar: each step counts for its own progress, so it creeps forward instead of jumping by fifths
            val overall = if (app.syncSteps.isEmpty()) 0f else app.syncSteps.sumOf { s ->
                when (s.state) { StepState.Done, StepState.Failed -> 1.0; StepState.Busy -> s.progress.fraction.coerceAtLeast(0f) * 0.9; else -> 0.0 }
            }.toFloat() / app.syncSteps.size
            ProgressLine(animateFloatAsState(overall, label = "overall").value, Modifier.fillMaxWidth(), C.Control, height = 6.dp)
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF11151F)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                app.syncSteps.forEach { s -> SyncRow(s) }
            }
            if (error != null) {
                T(error!!, 15, Bold, C.Error, maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Try again", { attempt++ }, style = BtnStyle.Primary)
                    Btn("Edit details", { app.push(Screen.Login) })
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Large playlists can take a minute the first time.", 13, color = C.Muted, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
    }
}

/** One sync step: status dot, name, a count that ticks up as items are read, and its own progress bar. */
@Composable
private fun SyncRow(s: SyncStep) {
    val p = s.progress
    // the count arrives in jumps of a few hundred; animating towards it makes it tick up smoothly
    val shown by animateIntAsState(p.count, tween(450, easing = LinearEasing), label = "count")
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val (dot, mark) = when (s.state) {
                StepState.Done -> Color(0xFF2E8A5B) to "✓"
                StepState.Busy -> C.Accent to "…"
                StepState.Failed -> C.Error to "!"
                StepState.Todo -> Color(0xFF1F2636) to ""
            }
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(dot), contentAlignment = Alignment.Center) {
                T(mark, 13, Heavy, if (s.state == StepState.Done) Color.White else C.OnLight)
            }
            T(s.label, 15, Semi, if (s.state == StepState.Todo) C.Muted else C.Text, Modifier.weight(1f))
            val detail = when {
                s.state == StepState.Failed || s.unit.isEmpty() -> s.detail
                s.state == StepState.Done && s.label == "TV guide" -> s.detail
                p.count > 0 || s.state == StepState.Done -> "%,d %s".format(shown, s.unit)
                s.state == StepState.Busy && p.bytes > 0 -> "%.1f MB".format(p.bytes / 1_048_576f)
                else -> s.detail
            }
            T(detail, 13, color = if (s.state == StepState.Failed) C.Error else if (s.state == StepState.Busy) C.Text else C.Muted, mono = s.state != StepState.Failed)
        }
        if (s.unit.isNotEmpty()) {
            val bar = if (s.state == StepState.Done) Color(0xFF2E8A5B) else if (s.state == StepState.Failed) C.Error else C.Accent
            when {
                s.state == StepState.Busy && p.fraction < 0 -> {
                    // size unknown: a segment sweeping across
                    val x by rememberInfiniteTransition(label = "sweep").animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "x")
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.Control)) {
                        Box(Modifier.fillMaxWidth(0.25f).height(4.dp).graphicsLayer { translationX = (size.width * 4f) * x - size.width }.background(bar))
                    }
                }
                else -> ProgressLine(animateFloatAsState(when (s.state) { StepState.Done, StepState.Failed -> 1f; StepState.Busy -> p.fraction; else -> 0f }, label = "bar").value,
                    Modifier.fillMaxWidth(), C.Control, bar, height = 4.dp)
            }
        }
    }
}
