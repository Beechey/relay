package com.avetero.relay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Ico
import com.avetero.relay.ui.theme.T

@Composable
internal fun PanelBtn(text: String, icon: ImageVector?, onClick: () -> Unit, key: String? = null, style: BtnStyle = BtnStyle.Secondary,
                     quiet: Boolean = false, danger: Boolean = false, enabled: Boolean = true, focusRequester: FocusRequester? = null) =
    FocusSurface({ if (enabled) onClick() }, Modifier.fillMaxWidth().height(if (quiet) 38.dp else 44.dp),
        idle = when { quiet -> Color.Transparent; !enabled -> C.Panel; style == BtnStyle.Primary -> C.Accent; style == BtnStyle.Light -> C.Text; else -> C.Control },
        shape = RoundedCornerShape(10.dp), focusRequester = focusRequester) { f ->
        val color = when {
            f -> C.OnLight
            !enabled -> C.Dim
            danger -> C.Error
            quiet -> C.Muted
            style == BtnStyle.Primary || style == BtnStyle.Light -> C.OnLight
            else -> C.Text
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) Ico(icon, color, 18.dp)
            T(text, if (quiet) 14 else 15, Bold, color, Modifier.weight(1f))
            if (key != null) T(key, 11, color = if (f || style != BtnStyle.Secondary) C.OnLightSub else C.Dim, mono = true)
        }
    }

enum class BtnStyle { Primary, Secondary, Destructive, Light }

@Composable
fun Btn(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: BtnStyle = BtnStyle.Secondary,
    icon: ImageVector? = null, height: Dp = 48.dp, focusRequester: FocusRequester? = null, enabled: Boolean = true,
) = FocusSurface(
    onClick = { if (enabled) onClick() }, modifier = modifier.height(height), focusRequester = focusRequester, shape = RoundedCornerShape(percent = 50), lift = 1.05f,
    idle = when { !enabled -> C.Surface; style == BtnStyle.Primary -> C.Accent; style == BtnStyle.Light -> C.Text; else -> C.Control },
) { f ->
    val color = when {
        f -> C.OnLight
        !enabled -> C.Dim
        style == BtnStyle.Primary || style == BtnStyle.Light -> C.OnLight
        style == BtnStyle.Destructive -> C.Error
        else -> C.Text
    }
    Row(Modifier.align(Alignment.Center).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) Ico(icon, color, 18.dp)
        T(text, 15, if (style == BtnStyle.Secondary && !f) Bold else Heavy, color)
    }
}

@Composable
fun IconBtn(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = C.Text, size: Dp = 48.dp, round: Boolean = false) =
    FocusSurface(onClick, modifier.size(size), idle = C.Control, shape = RoundedCornerShape(if (round) size / 2 else 12.dp), label = label) { f ->
        Ico(icon, if (f) C.OnLight else tint, 20.dp, Modifier.align(Alignment.Center))
    }

@Composable
fun Toggle(on: Boolean, focused: Boolean) = Box(
    Modifier.size(52.dp, 30.dp).clip(RoundedCornerShape(15.dp))
        .background(if (on) C.Accent else if (focused) C.LightTrack else Color(0xFF2A3142)),
) {
    Box(Modifier.offset(x = if (on) 26.dp else 4.dp, y = 4.dp).size(22.dp).clip(RoundedCornerShape(11.dp))
        .background(if (on || focused) C.OnLight else C.Muted))
}

/** A row of mutually exclusive options on one track (content type, filter mode, sort order). */
@Composable
fun <K> SegmentedTabs(options: List<Pair<K, String>>, selected: K, onSelect: (K) -> Unit, small: Boolean = false) =
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Panel).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (k, label) ->
            FocusSurface({ onSelect(k) }, Modifier.height(if (small) 34.dp else 40.dp), selected = selected == k, shape = RoundedCornerShape(9.dp)) { f ->
                T(label, if (small) 13 else 14, Bold, if (f) C.OnLight else if (selected == k) C.Text else C.Muted,
                    Modifier.align(Alignment.Center).padding(horizontal = if (small) 12.dp else 16.dp))
            }
        }
    }
