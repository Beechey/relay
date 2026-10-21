package com.avetero.relay.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * TV and landscape tablets: lay out on the boards' 1280x720 canvas at any screen size (Android TV's default 960x540dp makes everything 33% too big).
 * Phones and portrait tablets: natural density with the compact (single-column, bottom bar) layouts.
 */
@Composable
fun DesignScale(content: @Composable () -> Unit) {
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current.density
    val tv = (cfg.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val compact = !tv && (cfg.smallestScreenWidthDp < 600 || cfg.orientation == Configuration.ORIENTATION_PORTRAIT)
    if (compact) CompositionLocalProvider(LocalCompact provides true, content = content)
    else {
        val scale = minOf(cfg.screenWidthDp * density / 1280f, cfg.screenHeightDp * density / 720f)
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f), content = content)
    }
}

/** True on phones (and tablets in portrait): single-column layouts with a bottom tab bar instead of the TV rail. */
val LocalCompact = staticCompositionLocalOf { false }

@Composable
fun compact() = LocalCompact.current
