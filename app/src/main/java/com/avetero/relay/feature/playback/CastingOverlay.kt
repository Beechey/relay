package com.avetero.relay.feature.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avetero.relay.ui.components.Label
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.T

/** Covers the video area while a Chromecast is playing, so the screen says where the picture went. */
@Composable
fun CastingOverlay(device: String, title: String, status: String? = null) =
    Box(Modifier.fillMaxSize().background(C.Ground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("Casting to $device")
            T(title, 24, Heavy, maxLines = 2)
            T(status ?: "Use the controls here, or the Cast notification, to pause and seek.", 14, color = if (status != null) C.Accent else C.Muted)
        }
    }
