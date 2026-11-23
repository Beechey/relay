package com.avetero.relay.player

import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/**
 * The video, with a loading indicator over it while the stream is buffering (shown after a short moment, so quick
 * hiccups don't flash it). Every player, preview and the mini player use this, so they all show it.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(player: Player, modifier: Modifier = Modifier, zoom: Boolean = false) = BoxWithConstraints(modifier) {
    AndroidView(
        factory = {
            PlayerView(it).apply {
                useController = false
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                setShutterBackgroundColor(Color.BLACK)
                this.player = player
            }
        },
        update = { it.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize(),
    )
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(buffering) { if (buffering) { delay(400); show = true } else show = false }
    // fits small surfaces too (the mini player): at most 56dp, at most half the height
    val size = minOf(56.dp, maxHeight * 0.5f)
    AnimatedVisibility(show, Modifier.align(Alignment.Center), enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.8f)) {
        ContainedLoadingIndicator(Modifier.size(size))
    }
}
