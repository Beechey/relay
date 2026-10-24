package com.avetero.relay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.avetero.relay.ui.theme.C
import com.google.android.gms.cast.framework.CastButtonFactory

/** The standard Cast icon on the same tile as the other player buttons; it opens the Chromecast picker. */
@Composable
fun CastButton(modifier: Modifier = Modifier, size: Dp = 48.dp) =
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(C.Control), contentAlignment = Alignment.Center) {
        // the activity theme (Theme.Relay) supplies the colorPrimary MediaRouteButton requires
        AndroidView({ ctx -> MediaRouteButton(ctx).also { CastButtonFactory.setUpMediaRouteButton(ctx, it) } }, Modifier.size(size))
    }
