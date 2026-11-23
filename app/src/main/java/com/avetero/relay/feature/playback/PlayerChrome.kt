package com.avetero.relay.feature.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.IconButtonDefaults as TvIconButtonDefaults
import com.avetero.relay.ui.theme.Tok
import com.avetero.relay.ui.tv.TvLift
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.MaterialTheme as TvTheme
import androidx.tv.material3.Text as TvText

/*
 * Shared chrome for the live and movie players: the scrims the controls sit on, the phone top bar and toolbar,
 * and the TV control buttons (icon + label, the one TV focus look).
 */

/** Top scrim: a short fade from the surface into the picture, so the title bar reads over anything. */
@Composable
fun BoxScope.TopScrim(fraction: Float = 0.3f, alpha: Float = 0.7f) =
    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(fraction)
        .background(Brush.verticalGradient(listOf(Tok.Surface.copy(alpha = alpha), Color.Transparent))))

/** Bottom scrim: transparent at the top to ~85% surface at the bottom edge, under the info and controls. */
@Composable
fun BoxScope.BottomScrim(fraction: Float = 0.45f, alpha: Float = 0.85f) =
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(fraction)
        .background(Brush.verticalGradient(listOf(Color.Transparent, Tok.Surface.copy(alpha = alpha * 0.75f), Tok.Surface.copy(alpha = alpha)))))

/** Phone player top app bar: back, title over subtitle, then [actions] (48dp icon buttons). */
@Composable
fun PlayerTopBar(onBack: () -> Unit, title: String, subtitle: String?, modifier: Modifier = Modifier, leading: @Composable RowScope.() -> Unit = {},
                 actions: @Composable RowScope.() -> Unit) =
    Row(modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayerIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }

/** A 48dp standard icon button that morphs its shape when pressed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerIcon(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp = 24.dp,
               content: (@Composable () -> Unit)? = null) =
    IconButton(onClick, shapes = IconButtonDefaults.shapes(), modifier = modifier.size(if (iconSize > 24.dp) 64.dp else 48.dp),
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        content?.invoke() ?: Icon(icon, label, Modifier.size(iconSize))
    }

/** The big Expressive play/pause: a filled circle that squares off while pressed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayPause(paused: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    FilledIconButton(onClick, shapes = IconButtonDefaults.shapes(CircleShape, RoundedCornerShape(24.dp)), modifier = modifier.size(88.dp)) {
        Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (paused) "Play" else "Pause", Modifier.size(40.dp))
    }

/** The bottom action row: a floating toolbar of labelled actions. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerToolbar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    HorizontalFloatingToolbar(expanded = true, modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)),
        content = content)

/** A labelled toolbar action; [primary] fills it with the accent (Go live, Next episode). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolbarAction(text: String, icon: ImageVector, onClick: () -> Unit, primary: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    TextButton(onClick, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(48.dp), contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        colors = if (primary) ButtonDefaults.buttonColors() else ButtonDefaults.textButtonColors(contentColor = cs.onSurface)) {
        Icon(icon, null, Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.width(ButtonDefaults.IconSpacing)); Text(text, maxLines = 1)
    }
}

/**
 * TV player control: icon with an optional label (pill), or icon only (circle). Tonal on the scrim, [primary] in the
 * accent; focused it takes the one TV focus look.
 */
@Composable
fun TvControl(
    icon: ImageVector, label: String?, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false,
    focusRequester: FocusRequester? = null, onLongClick: (() -> Unit)? = null, description: String? = label,
) {
    val cs = TvTheme.colorScheme
    val container = if (primary) cs.primary else Tok.ContainerHighest.copy(alpha = 0.8f)
    val content = if (primary) cs.onPrimary else cs.onSurface
    val m = modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    if (label == null) androidx.tv.material3.IconButton(onClick, m.size(56.dp), onLongClick = onLongClick, scale = TvIconButtonDefaults.scale(focusedScale = TvLift.Row),
        colors = TvIconButtonDefaults.colors(containerColor = container, contentColor = content, focusedContainerColor = cs.inverseSurface, focusedContentColor = cs.inverseOnSurface)) {
        TvIcon(icon, description, Modifier.align(Alignment.Center).size(28.dp))
    } else Button(onClick, m, onLongClick = onLongClick, scale = TvButtonDefaults.scale(focusedScale = TvLift.Row),
        colors = TvButtonDefaults.colors(containerColor = container, contentColor = content, focusedContainerColor = cs.inverseSurface, focusedContentColor = cs.inverseOnSurface),
        contentPadding = PaddingValues(start = 20.dp, end = 24.dp, top = 16.dp, bottom = 16.dp)) {
        TvIcon(icon, null, Modifier.size(24.dp)); Spacer(Modifier.width(10.dp))
        TvText(label, style = TvTheme.typography.titleMedium, maxLines = 1)
    }
}

/** A 6dp rounded progress track: [live] (lighter) is how far the broadcast has got, [fraction] where you are. */
@Composable
fun TvProgress(fraction: Float, live: Float = 0f, modifier: Modifier = Modifier) {
    val cs = TvTheme.colorScheme
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(cs.onSurface.copy(alpha = 0.2f))) {
        if (live > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(live.coerceIn(0f, 1f)).background(cs.onSurface.copy(alpha = 0.35f)))
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(CircleShape).background(cs.primary))
    }
}

/** The LIVE / Catch-up / Paused tag next to a channel name. Red only while at live. */
@Composable
fun TvStateBadge(text: String, live: Boolean) {
    val cs = TvTheme.colorScheme
    TvText(text, Modifier.clip(RoundedCornerShape(8.dp)).background(if (live) cs.error else cs.secondaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
        style = TvTheme.typography.labelLarge, color = if (live) cs.onError else cs.onSecondaryContainer)
}
