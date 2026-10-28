package com.avetero.relay.feature.playback

import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.ExperimentalComposeUiApi
import com.avetero.relay.ui.components.settle
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

/** Board 19 player error. Focus stays inside the panel; [onBack] leaves the player. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerError(err: String, onRetry: () -> Unit, onSwitch: (() -> Unit)?, hls: Boolean = false, onBack: (() -> Unit)? = null) =
    Box(Modifier.fillMaxSize().background(Color(0xCC05070B)), contentAlignment = Alignment.Center) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { first.settle() }
        Column(Modifier.padding(16.dp).widthIn(max = 460.dp).clip(RoundedCornerShape(24.dp)).background(C.Glass).padding(26.dp)
            .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()
            // Back would otherwise be spent trying to move focus out of the panel
            .onPreviewKeyEvent { e -> if (e.key == Key.Back && onBack != null) { if (e.type == KeyEventType.KeyUp) onBack(); true } else false }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Ico(Icons.Warn, C.Error, 26.dp)
            T("This isn't playing", 20, Heavy)
            T(err, 14, color = C.Soft, maxLines = 4, lineHeight = 21)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Retry", onRetry, style = BtnStyle.Primary, height = 44.dp, focusRequester = first)
                if (onSwitch != null) Btn(if (hls) "Try MPEG-TS" else "Try HLS", onSwitch, height = 44.dp)
                if (onBack != null) Btn("Go back", onBack, height = 44.dp)
            }
        }
    }
