package com.avetero.relay.feature.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.app.StepState
import com.avetero.relay.app.SyncStep
import com.avetero.relay.ui.theme.isTv

/** Loading after sign-in: one row per step. On failure: Try again or Edit details. */
@Composable
fun SyncScreen(app: AppState) {
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        error = null
        try { app.sync(); app.go(Screen.Home) } catch (e: Exception) { error = e.message ?: "Couldn't load the playlist" }
    }
    val retry = { attempt++; Unit }
    val edit = { app.push(Screen.Login) }
    if (isTv()) SyncTv(app, error, retry, edit) else SyncPhone(app, error, retry, edit)
}

/** The step's right-hand text: a count that ticks up as items are read, the bytes so far, or its status. */
@Composable
internal fun stepDetail(s: SyncStep): String {
    val p = s.progress
    // the count arrives in jumps of a few hundred; animating towards it makes it tick up smoothly
    val shown by animateIntAsState(p.count, tween(450, easing = LinearEasing), label = "count")
    return when {
        s.state == StepState.Failed || s.unit.isEmpty() -> s.detail
        s.state == StepState.Done && s.label == "TV guide" -> s.detail
        p.count > 0 || s.state == StepState.Done -> "%,d %s".format(shown, s.unit)
        s.state == StepState.Busy && p.bytes > 0 -> "%.1f MB".format(p.bytes / 1_048_576f)
        else -> s.detail
    }
}

/** Wavy progress for the running step; sweeps while the size isn't known. */
@Composable
internal fun StepWave(s: SyncStep) {
    val f = s.progress.fraction
    if (f < 0) LinearWavyProgressIndicator(Modifier.fillMaxWidth()) else LinearWavyProgressIndicator({ f }, Modifier.fillMaxWidth())
}
