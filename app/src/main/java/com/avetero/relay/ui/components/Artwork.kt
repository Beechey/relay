package com.avetero.relay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.sub
import com.avetero.relay.ui.theme.tone

/** Artwork with a tinted, titled placeholder underneath (shown when there is no image or it fails). */
@Composable
fun Art(url: String, title: String, seed: Int, modifier: Modifier = Modifier, titleSize: Int = 13, showTitle: Boolean = true) =
    Box(modifier.clip(RoundedCornerShape(9.dp)).background(tone(seed))) {
        if (showTitle) T(title, titleSize, Heavy, Color.White, Modifier.align(Alignment.BottomStart).padding(10.dp), maxLines = 3)
        if (url.isNotBlank()) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }

/** Channel logo, falling back to a 3-letter badge. */
@Composable
fun Logo(url: String, name: String, focused: Boolean, modifier: Modifier = Modifier) =
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(if (focused) C.LightTrack else Color(0xFF1F2636)), contentAlignment = Alignment.Center) {
        T(name.filter { it.isLetterOrDigit() }.take(3).uppercase(), 11, Heavy, sub(focused))
        if (url.isNotBlank()) AsyncImage(url, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
    }

@Composable
fun PosterTile(image: String, title: String, seed: Int, width: Dp = 120.dp, onClick: () -> Unit) =
    FocusSurface(onClick, Modifier.size(width, width * 1.5f), shape = RoundedCornerShape(10.dp)) { f ->
        Art(image, title, seed, Modifier.fillMaxSize().padding(if (f) 5.dp else 0.dp), 12)
    }
