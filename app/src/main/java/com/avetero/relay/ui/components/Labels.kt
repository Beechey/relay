package com.avetero.relay.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.T
import com.avetero.relay.util.fmtTime
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Chips(names: List<String>, more: Int) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEach { Chip(it) }
        if (more > 0) Chip("+ $more more")
    }

@Composable
internal fun Chip(text: String) =
    Box(Modifier.widthIn(max = 240.dp).clip(RoundedCornerShape(7.dp)).background(C.Control).padding(horizontal = 8.dp, vertical = 5.dp)) { T(text, 12, Bold, C.Soft) }

@Composable
fun Label(text: String, modifier: Modifier = Modifier) = T(text.uppercase(), 12, Bold, C.Muted, modifier)

@Composable
fun Badge(text: String, bg: Color = C.Control, color: Color = C.Soft, modifier: Modifier = Modifier) =
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) { T(text, 12, Heavy, color) }

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, track: Color = C.Track, bar: Color? = null, height: Dp = 3.dp) =
    Box(modifier.height(height).clip(RoundedCornerShape(height)).background(track)) {
        // the live gradient unless a caller needs a flat colour (e.g. dark on the light focus fill)
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).then(if (bar != null) Modifier.background(bar) else Modifier.background(com.avetero.relay.ui.theme.LiveGradient)))
    }

@Composable
fun KeyHint(key: String, action: String) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(C.Control).padding(horizontal = 7.dp, vertical = 3.dp)) { T(key, 12, Bold) }
    T(action, 12, color = C.Muted)
}

/** Centered message used for empty/error states (board 19). */
@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) =
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(16.dp).widthIn(max = 420.dp).clip(RoundedCornerShape(18.dp)).background(C.Surface).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T(title, 20, Heavy)
            T(body, 14, color = Color(0xFFC9CED8), maxLines = 4, lineHeight = 21)
            if (action != null) Btn(action, onAction, style = BtnStyle.Light, height = 44.dp)
        }
    }

@Composable
fun Clock(size: Int = 13, color: Color = C.Text) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() / 1000 } }
    T(fmtTime(now), size, color = color, mono = true)
}

/** A small rounded label on artwork or glass: facts (year, rating), filters, "Continue watching". */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, bg: Color = C.Glass2, color: Color = C.Soft) =
    Box(modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        T(text, 12, Bold, color)
    }

/** Red LIVE marker for anything playing now. */
@Composable
fun LivePill(modifier: Modifier = Modifier) =
    Row(modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(C.Live).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
        T("LIVE", 11, Heavy, Color.White)
    }
