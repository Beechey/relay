package com.avetero.relay.ui.m3

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.avetero.relay.ui.motion.sharedBounds
import com.avetero.relay.ui.theme.tone

/**
 * Phone building blocks (Material 3 Expressive, design-spec.md). Screens draw edge to edge: a screen with a
 * [RelayTopBar] gets the status bar handled; others add `Modifier.statusBarsPadding()` themselves.
 */

/** Pushed screens: back arrow + title (titleLarge), optional actions. Pass a scroll behaviour to collapse on scroll. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}, scroll: TopAppBarScrollBehavior? = null, transparent: Boolean = false) =
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        actions = actions,
        scrollBehavior = scroll,
        colors = if (transparent) TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer)
        else TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
    )

/** Root screens: a large title (headlineMedium) under the status bar, with optional trailing actions. */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) =
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        actions()
    }

/** A section heading (titleLarge) with an optional text action ("See all"). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) =
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onAction) { Text(action) }
    }

/** Grouped (segmented) list corners: 20 on the outside of the group, 4 between items. Items sit 2dp apart. */
fun segmentShape(index: Int, count: Int): RoundedCornerShape = when {
    count == 1 -> RoundedCornerShape(20.dp)
    index == 0 -> RoundedCornerShape(20.dp, 20.dp, 4.dp, 4.dp)
    index == count - 1 -> RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp)
    else -> RoundedCornerShape(4.dp)
}

/** Artwork with a tinted placeholder (titled) under it, for when there's no image or it fails. */
@Composable
fun Artwork(url: String, title: String, seed: Int, modifier: Modifier = Modifier, showTitle: Boolean = true) =
    Box(modifier.background(tone(seed))) {
        if (showTitle) Text(title, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 3,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
        if (url.isNotBlank()) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }

/**
 * A 2:3 poster with its title underneath. [sharedKey] (e.g. "poster-m42") lets it grow into the detail page's hero
 * when the detail uses the same key.
 */
@Composable
fun PosterCard(url: String, title: String, seed: Int, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp? = null, sharedKey: Any? = null, onLongClick: (() -> Unit)? = null) =
    Column(modifier.then(if (width != null) Modifier.width(width) else Modifier)) {
        Artwork(url, title, seed,
            (if (sharedKey != null) Modifier.sharedBounds(sharedKey) else Modifier).fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp))
                .combinedClickableCompat(onClick, onLongClick), showTitle = url.isBlank())
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }

/** Channel logo: rounded square, morphing to a circle while it's the one playing (spatial spring). */
@Composable
fun ChannelLogo(url: String, name: String, modifier: Modifier = Modifier, size: Dp = 48.dp, playing: Boolean = false) {
    val r by animateDpAsState(if (playing) size / 2 else 12.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "logo")
    Box(modifier.size(size).clip(RoundedCornerShape(r)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Text(name.filter { it.isLetterOrDigit() }.take(3).uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url.isNotBlank()) AsyncImage(url, null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
    }
}

/** Standard modal bottom sheet: drag handle, 28dp top corners, surfaceContainerLow, springs up. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelaySheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        content = content,
    )

@Composable
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    if (onLongClick == null) clickable(onClick = onClick)
    else this.then(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick))
