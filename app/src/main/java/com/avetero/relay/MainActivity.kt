package com.avetero.relay

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.avetero.relay.ui.LocalCompact
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.avetero.relay.ui.C
import com.avetero.relay.ui.ExitDialog
import com.avetero.relay.ui.FavouritesScreen
import com.avetero.relay.ui.MoreScreen
import com.avetero.relay.ui.GuideScreen
import com.avetero.relay.ui.HomeScreen
import com.avetero.relay.ui.LivePlayerScreen
import com.avetero.relay.ui.LiveScreen
import com.avetero.relay.ui.LoginScreen
import com.avetero.relay.ui.MovieDetailScreen
import com.avetero.relay.ui.SearchScreen
import com.avetero.relay.ui.SeriesDetailScreen
import com.avetero.relay.ui.SettingsScreen
import com.avetero.relay.ui.SyncScreen
import com.avetero.relay.ui.T
import com.avetero.relay.ui.Bold
import com.avetero.relay.ui.Tab
import com.avetero.relay.ui.UpdateDialog
import com.avetero.relay.ui.VodGridScreen
import com.avetero.relay.ui.VodPlayerScreen
import com.avetero.relay.ui.WithRail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = AppState(applicationContext)
        setContent { DesignScale { App(app, onExit = ::finish) } }
    }

    /** Phones and tablets only: TVs keep fullscreen playback. */
    private val canPip get() = Build.VERSION.SDK_INT >= 26 &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
        !packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    /** The video fills the window while a player is showing; telling Android lets it animate from there. */
    private fun pipParams(autoEnter: Boolean? = null): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        window.decorView.let { v -> if (v.width > 0) b.setSourceRectHint(android.graphics.Rect(0, 0, v.width, v.height)) }
        if (Build.VERSION.SDK_INT >= 31 && autoEnter != null) b.setAutoEnterEnabled(autoEnter).setSeamlessResizeEnabled(true)
        return b.build()
    }

    fun enterPip() {
        if (!canPip || app.player.url == null || isInPictureInPictureMode) return
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    /**
     * Android 12+: let the system enter PiP itself while a player is showing, so the swipe-home
     * gesture animates straight into the floating window.
     */
    fun setAutoPip(enabled: Boolean) {
        if (!canPip || Build.VERSION.SDK_INT < 31) return
        runCatching { setPictureInPictureParams(pipParams(autoEnter = enabled)) }
    }

    // Home, swipe-home or recent apps while a video is playing: shrink it to a floating window.
    // Also on Android 12+, where auto-enter can be skipped (e.g. fullscreen with the bars hidden).
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (app.pipEligible) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        app.inPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        app.player.player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        app.player.player.release()
    }
}

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

@Composable
fun App(app: AppState, onExit: () -> Unit) {
    var exitAsk by remember { mutableStateOf(false) }
    val compact = com.avetero.relay.ui.compact()
    var updatePrompt by remember { mutableStateOf(false) }
    val nav: (Tab) -> Unit = { t ->
        app.go(when (t) {
            Tab.Home -> Screen.Home; Tab.Live -> Screen.Live(); Tab.Guide -> Screen.Guide
            Tab.Movies -> Screen.VodGrid(false); Tab.Series -> Screen.VodGrid(true)
            Tab.Favourites -> Screen.Favourites; Tab.Search -> Screen.Search; Tab.Settings -> Screen.Settings()
            Tab.More -> Screen.More
        })
    }

    // background refresh of a cached catalogue, then a silent update check
    LaunchedEffect(app.account) {
        app.loadLibrary()
        // playlist refresh on the schedule chosen in Settings (every start / daily / weekly / monthly / manually)
        val stale = app.account?.let { a -> app.store.refreshAfterMs()?.let { System.currentTimeMillis() - app.store.cacheTime(a) > it } } ?: false
        if (app.account != null && app.screen == Screen.Home && stale) runCatching { app.sync() }
        if (Updater.enabled && app.store.checkUpdates) runCatching {
            val r = withContext(Dispatchers.IO) { Updater.latest(app.store.betaUpdates) }
            if (r != null && Updater.isNewer(r)) { app.update = r; updatePrompt = r.version != app.store.skippedVersion }
        }
    }

    // stop playback on screens with nowhere to show it (e.g. back from fullscreen to Home): otherwise audio plays on unseen
    LaunchedEffect(app.screen, compact) {
        val showsVideo = app.onPlayerScreen || app.screen is Screen.Live || (app.screen == Screen.Guide && !compact)
        if (!showsVideo && !app.inPip) app.player.stop()
    }

    // full TV guide: once the library is in memory, load it from cache or refresh it (12 h)
    LaunchedEffect(app.account, app.libraryLoading, app.channels.isNotEmpty()) {
        if (app.account != null && !app.libraryLoading && app.channels.isNotEmpty() && app.screen != Screen.Sync) app.refreshGuide()
    }

    val activity = androidx.compose.ui.platform.LocalContext.current as? MainActivity
    val autoPip = app.pipEligible
    LaunchedEffect(autoPip) { activity?.setAutoPip(autoPip) }

    BackHandler {
        when {
            app.stack.size > 1 -> app.back()
            app.screen in listOf(Screen.Guide, Screen.Favourites, Screen.Search) && compact -> app.go(Screen.More)
            app.screen is Screen.Settings && compact -> app.go(Screen.More)
            app.screen !in listOf(Screen.Home, Screen.Login, Screen.Sync) -> app.go(Screen.Home)
            else -> exitAsk = true
        }
    }

    Box(Modifier.fillMaxSize().background(C.Ground)) {
        when (val s = app.screen) {
            Screen.Login -> LoginScreen(app)
            Screen.Sync -> SyncScreen(app)
            Screen.Home -> HomeScreen(app, nav)
            is Screen.Live -> LiveScreen(app, nav, s.group)
            Screen.Guide -> GuideScreen(app, nav)
            is Screen.LivePlayer -> LivePlayerScreen(app, s.channel, s.list)
            is Screen.VodGrid -> VodGridScreen(app, s.series, nav, s.group)
            // details keep the rail / bottom tabs so navigation is always in the same place
            is Screen.MovieDetail -> WithRail(Tab.Movies, nav) { MovieDetailScreen(app, s.vod) }
            is Screen.SeriesDetail -> WithRail(Tab.Series, nav) { SeriesDetailScreen(app, s.series) }
            is Screen.VodPlayer -> VodPlayerScreen(app, s.item, s.next, s.episodes)
            Screen.Search -> SearchScreen(app, nav)
            Screen.Favourites -> FavouritesScreen(app, nav)
            Screen.More -> MoreScreen(app, nav)
            is Screen.Settings -> SettingsScreen(app, s.section, nav)
        }
        // PiP from Live TV's preview: the floating window shows only the video
        if (app.inPip && !app.onPlayerScreen) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            com.avetero.relay.PlayerSurface(app.player.player, Modifier.fillMaxSize())
        }
        app.message?.let { msg ->
            LaunchedEffect(msg) { delay(2_500); app.message = null }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).clip(RoundedCornerShape(12.dp)).background(C.Text).padding(horizontal = 20.dp, vertical = 12.dp)) {
                T(msg, 15, Bold, C.OnLight)
            }
        }
    }
    if (exitAsk) ExitDialog(onExit) { exitAsk = false }
    app.update?.let { if (updatePrompt) UpdateDialog(app, it) { updatePrompt = false } }
}
