package com.avetero.relay.feature.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Ico
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.T

/** Board 19 player error. */
@Composable
fun PlayerError(err: String, onRetry: () -> Unit, onSwitch: (() -> Unit)?, hls: Boolean = false) =
    Box(Modifier.fillMaxSize().background(Color(0xCC05070B)), contentAlignment = Alignment.Center) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Column(Modifier.padding(16.dp).widthIn(max = 420.dp).clip(RoundedCornerShape(18.dp)).background(C.Surface).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Ico(Icons.Warn, C.Error, 26.dp)
            T("This isn't playing", 20, Heavy)
            T(err, 14, color = C.Soft, maxLines = 4, lineHeight = 21)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Retry", onRetry, style = BtnStyle.Light, height = 44.dp, focusRequester = first)
                if (onSwitch != null) Btn(if (hls) "Try MPEG-TS" else "Try HLS", onSwitch, height = 44.dp)
            }
        }
    }
