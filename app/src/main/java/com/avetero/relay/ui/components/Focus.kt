package com.avetero.relay.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.avetero.relay.ui.theme.compact


/**
 * A dialog or new screen often can't take focus on its first frame (its window isn't focused yet), and a single
 * request is silently dropped. Keep asking for half a second.
 */
suspend fun FocusRequester.settle() = repeat(10) { runCatching { requestFocus() }; kotlinx.coroutines.delay(50) }

/** For sideways rows: Right on the last item stays put, instead of jumping to whatever is nearest (often the top bar). */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.stopAtEnd() = focusProperties {
    exit = { if (it == androidx.compose.ui.focus.FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default }
}.focusGroup()
