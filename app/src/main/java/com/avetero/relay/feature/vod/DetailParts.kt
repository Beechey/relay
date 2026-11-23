package com.avetero.relay.feature.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.avetero.relay.data.model.Resume
import com.avetero.relay.ui.m3.Artwork
import com.avetero.relay.ui.motion.sharedBounds
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvLift
import com.avetero.relay.ui.tv.TvSurface

/** The shared-element key a title's poster and its detail hero both use, so one grows into the other. */
internal fun posterKey(series: Boolean, id: Int) = if (series) "poster-s$id" else "poster-m$id"

/** How far through a resume point is, 0..1. */
internal fun Resume.fraction() = (pos / dur.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

/** "42 min left" / "1 h 12 m left" for a resume point (positions are in ms). */
internal fun Resume.left(): String {
    val m = ((dur - pos) / 60_000).coerceAtLeast(1)
    return if (m >= 60) "${m / 60} h ${m % 60} m left" else "$m min left"
}

/** "00:41:12" or "41 min" from the provider, as "41 m" / "1 h 12 m". */
internal fun shortDur(d: String): String {
    val p = d.split(':').mapNotNull { it.trim().toIntOrNull() }
    val mins = when (p.size) { 3 -> p[0] * 60 + p[1]; 2 -> p[0]; else -> return d }
    return if (mins >= 60) "${mins / 60} h ${mins % 60} m" else "$mins m"
}

/** TV rows and grids: the focused item settles about a third of the way in, so the remote always has a fixed place to look. */
private val pivot = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // already comfortably on screen: don't move
        if (offset >= 0 && offset + size <= containerSize && offset <= containerSize * .55f) return 0f
        return offset - containerSize * .3f
    }
}

@Composable
internal fun TvPivot(content: @Composable () -> Unit) = CompositionLocalProvider(LocalBringIntoViewSpec provides pivot, content = content)

/**
 * TV detail: artwork on the right ([left] of the screen onwards, [height] tall) fading into the surface to the
 * left and below, so the text stays readable. The artwork is the shared-element target of the poster ([key]).
 */
@Composable
internal fun TvBackdrop(image: String, seed: Int, key: String, height: Dp, left: Dp, content: @Composable BoxScope.() -> Unit) =
    Box(Modifier.fillMaxSize().background(Tok.Surface)) {
        Box(Modifier.padding(start = left).fillMaxWidth().height(height).sharedBounds(key)) {
            Artwork(image, "", seed, Modifier.fillMaxSize(), showTitle = false)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Tok.Surface, .25f to Tok.Surface.copy(alpha = 0f))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.7f to Color.Transparent, 1f to Tok.Surface)))
        }
        content()
    }

/** TV: a fact (year, length, genre, rating) in a small tonal chip. */
@Composable
internal fun TvFact(text: String) =
    Box(Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).background(Tok.ContainerHigh).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Tok.OnSurface)
    }

@Composable
internal fun TvFacts(facts: List<String>) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { facts.filter { it.isNotBlank() }.take(4).forEach { TvFact(it) } }

/** TV: the round Saved toggle next to Play. */
@Composable
internal fun TvSaveButton(saved: Boolean, onClick: () -> Unit, size: Dp = 56.dp) =
    TvSurface(onClick, Modifier.size(size), shape = RoundedCornerShape(size / 2), idle = Tok.ContainerHigh) {
        Icon(if (saved) Icons.Rounded.Star else Icons.Rounded.StarOutline, if (saved) "Remove from saved" else "Add to saved",
            Modifier.align(Alignment.Center), tint = if (saved) Tok.Primary else androidx.tv.material3.LocalContentColor.current)
    }

/**
 * TV: a 2:3 poster that lifts on focus, title (and optional meta line) underneath; the caption takes the focus fill.
 * [key] is the poster's shared-element key.
 */
@Composable
internal fun TvPoster(
    image: String, title: String, seed: Int, key: String, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp? = null,
    meta: String = "", progress: Float = 0f, onLongClick: (() -> Unit)? = null, focusRequester: FocusRequester? = null, onFocus: (() -> Unit)? = null, caption: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    androidx.compose.runtime.LaunchedEffect(focused) { if (focused) onFocus?.invoke() }
    Column(modifier.then(if (width != null) Modifier.width(width) else Modifier), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSurface(onClick, Modifier.fillMaxWidth().aspectRatio(2f / 3f), onLongClick = onLongClick, lift = TvLift.Card, focusRequester = focusRequester, interactionSource = source) {
            Artwork(image, title, seed, Modifier.fillMaxSize().sharedBounds(key), showTitle = image.isBlank())
            if (progress > 0f) Box(Modifier.align(Alignment.BottomStart).padding(10.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = .3f))) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).background(Tok.Primary))
            }
        }
        if (caption) Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (focused) Tok.InverseOnSurface else Tok.OnSurface,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (focused) Tok.InverseSurface else Color.Transparent).padding(horizontal = 8.dp, vertical = 4.dp))
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = Tok.OnSurfaceVariant, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
    }
}
