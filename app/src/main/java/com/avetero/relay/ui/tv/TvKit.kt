package com.avetero.relay.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.avetero.relay.ui.components.settle

/**
 * TV building blocks (Material 3 for TV, design-spec.md): one focus look everywhere — inverse fill, a small lift,
 * a soft shadow, no outline. Screens lay out inside [TvSafe] (the shell already reserves the drawer's 88dp).
 */
object TvSafe {
    val Horizontal = 48.dp
    val Vertical = 27.dp
    /** Padding for a screen's content next to the drawer. */
    val Content = PaddingValues(start = 40.dp, end = Horizontal, top = Vertical, bottom = Vertical)
}

/** Buttons and list rows lift 1.05; cards and posters 1.1. */
object TvLift { const val Row = 1.05f; const val Card = 1.1f }

/**
 * The standard focusable surface: rows, buttons, cards, cells. [selected] shows the "you are here" tint
 * (secondary container) when not focused.
 */
@Composable
fun TvSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    lift: Float = TvLift.Row,
    idle: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
    focusRequester: FocusRequester? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val base = if (selected) cs.secondaryContainer else idle
    Surface(
        onClick = onClick, onLongClick = onLongClick,
        modifier = modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = base, contentColor = if (selected) cs.onSecondaryContainer else cs.onSurface,
            focusedContainerColor = cs.inverseSurface, focusedContentColor = cs.inverseOnSurface,
            pressedContainerColor = cs.inverseSurface, pressedContentColor = cs.inverseOnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = lift),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
        glow = ClickableSurfaceDefaults.glow(),
        interactionSource = interactionSource,
        content = content,
    )
}

/** Filled (primary) or tonal button with TV focus. */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val cs = MaterialTheme.colorScheme
    androidx.tv.material3.Button(
        onClick = onClick,
        modifier = modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        scale = ButtonDefaults.scale(focusedScale = TvLift.Row),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) cs.primary else cs.surfaceVariant,
            contentColor = if (primary) cs.onPrimary else cs.onSurface,
            focusedContainerColor = cs.inverseSurface, focusedContentColor = cs.inverseOnSurface,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        if (leading != null) { leading(); androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Remote hints along the bottom right: keys drawn as small chips, e.g. TvHints("◀" to "Menu", "☰" to "Options"). */
@Composable
fun TvHints(vararg hints: Pair<String, String>, modifier: Modifier = Modifier) =
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        hints.forEach { (key, label) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(key, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

/**
 * Options, tracks and other menus: a 420dp panel sliding in from the right over the dimmed screen.
 * Back or [onDismiss] closes it; focus lands on [first] when it opens.
 */
@Composable
fun TvSidePanel(visible: Boolean, onDismiss: () -> Unit, first: FocusRequester? = null, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val state = remember { MutableTransitionState(false) }.apply { targetState = visible }
    if (!state.currentState && !state.targetState) return
    BackHandler(visible, onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(state, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
        }
        AnimatedVisibility(state, Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { 40 } + fadeIn(), exit = slideOutHorizontally { 40 } + fadeOut()) {
            Column(
                Modifier.width(420.dp).fillMaxHeight().shadow(24.dp, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)).background(com.avetero.relay.ui.theme.Tok.Container).padding(horizontal = 28.dp, vertical = 32.dp)
                    // the D-pad stays inside the panel until it's closed
                    .focusProperties { onExit = { cancelFocusChange() } }.focusGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (title != null) Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
                content()
            }
        }
    }
    if (first != null) androidx.compose.runtime.LaunchedEffect(visible) { if (visible) first.settle() }
}

/** Centred 560dp dialog (exit, update, confirmations) that scales in. */
@Composable
fun TvDialog(onDismiss: () -> Unit, width: Dp = 560.dp, content: @Composable ColumnScope.() -> Unit) =
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        val state = remember { MutableTransitionState(false) }.apply { targetState = true }
        AnimatedVisibility(state, enter = scaleIn(initialScale = 0.9f) + fadeIn(), exit = scaleOut(targetScale = 0.9f) + fadeOut()) {
            Column(
                Modifier.width(width).clip(RoundedCornerShape(28.dp)).background(com.avetero.relay.ui.theme.Tok.ContainerHigh).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), content = content,
            )
        }
    }

/** A titled horizontal row of focusable items with the standard spacing. */
@Composable
fun TvRowTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(bottom = 12.dp))
