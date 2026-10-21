package com.avetero.relay.feature.playback

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.avetero.relay.app.AppState
import com.avetero.relay.ui.components.Btn
import com.avetero.relay.ui.components.BtnStyle
import com.avetero.relay.ui.components.DialogTitle
import com.avetero.relay.ui.components.Label
import com.avetero.relay.ui.components.OptionRow
import com.avetero.relay.ui.components.Panel
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.T

/** Per-video choices: quality, audio track and subtitles, as the stream offers them. */
@Composable
fun TracksDialog(app: AppState, onDismiss: () -> Unit) = Dialog(onDismiss) {
    val p = app.player
    p.tracks // recompose when the stream's tracks change
    val sections = listOf(
        "Quality" to p.options(androidx.media3.common.C.TRACK_TYPE_VIDEO),
        "Audio" to p.options(androidx.media3.common.C.TRACK_TYPE_AUDIO),
        "Subtitles" to p.options(androidx.media3.common.C.TRACK_TYPE_TEXT),
    ).filter { it.second.isNotEmpty() }
    var tick by remember { mutableStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Panel(460, Modifier.heightIn(max = 560.dp), padding = 20.dp, spacing = 6.dp, scroll = true) {
        DialogTitle("Audio, subtitles & quality")
        if (sections.isEmpty()) T("This stream has a single quality and audio track, and no subtitles.", 14, color = C.Muted, maxLines = 3, modifier = Modifier.padding(8.dp))
        sections.forEachIndexed { si, (title, opts) ->
            Label(title, Modifier.padding(start = 8.dp, top = if (si > 0) 10.dp else 0.dp))
            opts.forEachIndexed { oi, o ->
                OptionRow(o.label, o.selected, { o.pick(); tick++ }, focusRequester = if (si == 0 && oi == 0) first else null)
            }
        }
        Btn("Done", onDismiss, Modifier.fillMaxWidth().padding(top = 8.dp), style = BtnStyle.Light, height = 44.dp)
    }
}
