package com.avetero.relay.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * TV: lay out on the designs' 1280x720 canvas at any screen size (Android TV's default 960x540dp makes everything
 * 33% too big). Phones and tablets: natural density, touch UI (tablets and landscape get a rail, see [LocalWide]).
 */
@Composable
fun DesignScale(content: @Composable () -> Unit) {
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current.density
    val tv = (cfg.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    if (!tv) CompositionLocalProvider(LocalCompact provides true, LocalWide provides (cfg.screenWidthDp >= 600)) { RelayTheme(false, content) }
    else {
        val scale = minOf(cfg.screenWidthDp * density / 1280f, cfg.screenHeightDp * density / 720f)
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) { RelayTheme(true, content) }
    }
}

/** True on phones and tablets (touch UI); false on TV (remote UI). */
val LocalCompact = staticCompositionLocalOf { false }

/** Phones in landscape and tablets: 600dp or wider, so navigation moves to a side rail. */
val LocalWide = staticCompositionLocalOf { false }

@Composable
fun compact() = LocalCompact.current

@Composable
fun isTv() = !LocalCompact.current
