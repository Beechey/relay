package com.avetero.relay.ui.components

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/** The standard Cast icon. It stays hidden until a Chromecast is on the network, and opens the device picker. */
@Composable
fun CastButton(modifier: Modifier = Modifier, size: Dp = 48.dp) = AndroidView(
    // MediaRouteButton needs a MediaRouter theme even though the rest of the app is Compose
    factory = { ctx -> MediaRouteButton(ContextThemeWrapper(ctx, androidx.mediarouter.R.style.Theme_MediaRouter)).also { CastButtonFactory.setUpMediaRouteButton(ctx, it) } },
    modifier = modifier.size(size),
)
