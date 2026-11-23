package com.avetero.relay.feature.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.avetero.relay.app.AppState
import com.avetero.relay.player.PlayerHost
import com.avetero.relay.ui.m3.RelaySheet
import com.avetero.relay.ui.m3.PagerTabs
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.avetero.relay.ui.m3.segmentShape
import com.avetero.relay.ui.theme.isTv
import com.avetero.relay.ui.tv.TvSidePanel
import com.avetero.relay.ui.tv.TvSurface

/** Per-video choices: quality, audio track and subtitles, as the stream offers them. Phone: a sheet; TV: a side panel. */
@Composable
fun TracksDialog(app: AppState, onDismiss: () -> Unit) {
    val p = app.player
    p.tracks // recompose when the stream's tracks change
    var tick by remember { mutableIntStateOf(0) }
    tick // re-read the selections after a pick
    val sections = listOf(
        "Audio" to p.options(C.TRACK_TYPE_AUDIO),
        "Subtitles" to p.options(C.TRACK_TYPE_TEXT),
        "Quality" to p.options(C.TRACK_TYPE_VIDEO),
    ).filter { it.second.isNotEmpty() }
    val pick: (PlayerHost.TrackOption) -> Unit = { it.pick(); tick++ }
    if (isTv()) TvTracks(sections, pick, onDismiss) else PhoneTracks(sections, pick, onDismiss)
}

private const val EMPTY = "This stream has a single quality and audio track, and no subtitles."

/** "English · stereo" → "English" over "stereo". */
private fun split(label: String) = label.substringBefore(" · ") to label.substringAfter(" · ", "").ifEmpty { null }

@Composable
private fun PhoneTracks(sections: List<Pair<String, List<PlayerHost.TrackOption>>>, pick: (PlayerHost.TrackOption) -> Unit, onDismiss: () -> Unit) =
    RelaySheet(onDismiss) {
        val cs = MaterialTheme.colorScheme
        Text("Audio, subtitles & quality", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp))
        if (sections.isEmpty()) Text(EMPTY, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
        else {
            // Audio | Subtitles | Quality as tabs you can swipe between; dragging down still closes the sheet
            val pager = rememberPagerState { sections.size }
            if (sections.size > 1) PagerTabs(pager, sections.map { it.first }, Modifier.padding(bottom = 16.dp), container = cs.surfaceContainerLow)
            HorizontalPager(pager, Modifier.weight(1f, fill = false), key = { sections[it].first }, verticalAlignment = Alignment.Top) { at ->
                val opts = sections[at].second
                LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(opts, key = { i, o -> "$i-${o.label}" }) { i, o ->
                        val (label, sub) = split(o.label)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = if (sub != null) 72.dp else 56.dp).clip(segmentShape(i, opts.size))
                                .background(if (o.selected) cs.secondaryContainer else cs.surfaceContainer)
                                .selectable(o.selected, role = Role.RadioButton) { pick(o) }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            RadioButton(o.selected, null, colors = RadioButtonDefaults.colors(selectedColor = cs.primary, unselectedColor = cs.outline))
                            Column {
                                Text(label, style = MaterialTheme.typography.titleMedium)
                                if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp), horizontalArrangement = Arrangement.End) {
            Button(onDismiss, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(48.dp)) { Text("Done") }
        }
    }

@Composable
private fun TvTracks(sections: List<Pair<String, List<PlayerHost.TrackOption>>>, pick: (PlayerHost.TrackOption) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val cs = androidx.tv.material3.MaterialTheme.colorScheme
    val type = androidx.tv.material3.MaterialTheme.typography
    TvSidePanel(true, onDismiss, first, "Audio, subtitles & quality") {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (sections.isEmpty()) item { androidx.tv.material3.Text(EMPTY, style = type.bodyLarge, color = cs.onSurfaceVariant) }
            sections.forEachIndexed { si, (title, opts) ->
                item(key = "h$si") {
                    androidx.tv.material3.Text(title, style = type.labelLarge, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = if (si > 0) 13.dp else 0.dp, bottom = 6.dp))
                }
                itemsIndexed(opts, key = { oi, o -> "$si-$oi-${o.label}" }) { oi, o ->
                    TvSurface({ pick(o) }, Modifier.fillMaxWidth().height(48.dp), shape = segmentShape(oi, opts.size), idle = com.avetero.relay.ui.theme.Tok.ContainerHigh,
                        focusRequester = if (si == 0 && oi == 0) first else null) {
                        Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Radio(o.selected)
                            androidx.tv.material3.Text(o.label, style = type.titleMedium)
                        }
                    }
                }
            }
        }
        // with nothing to pick, Done is the only stop for focus
        Spacer(Modifier.height(8.dp))
        com.avetero.relay.ui.tv.TvButton("Done", onDismiss, Modifier.fillMaxWidth(), primary = true, focusRequester = if (sections.isEmpty()) first else null)
    }
}

/** A radio dot drawn in the row's content colour (it flips with focus) and amber when chosen and unfocused. */
@Composable
private fun Radio(selected: Boolean) {
    val content = androidx.tv.material3.LocalContentColor.current
    val focused = content == androidx.tv.material3.MaterialTheme.colorScheme.inverseOnSurface
    val ring = when { focused -> content; selected -> androidx.tv.material3.MaterialTheme.colorScheme.primary; else -> androidx.tv.material3.MaterialTheme.colorScheme.border }
    Box(Modifier.size(20.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (selected) ring else Color.Transparent))
    }
}
