package com.avetero.relay.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.avetero.relay.MainActivity
import com.avetero.relay.data.update.Updater
import com.avetero.relay.feature.favourites.FavouritesScreen
import com.avetero.relay.feature.guide.GuideScreen
import com.avetero.relay.feature.home.HomeScreen
import com.avetero.relay.feature.home.MoreScreen
import com.avetero.relay.feature.live.LivePlayerScreen
import com.avetero.relay.feature.live.LiveScreen
import com.avetero.relay.feature.onboarding.LoginScreen
import com.avetero.relay.feature.onboarding.SyncScreen
import com.avetero.relay.feature.search.SearchScreen
import com.avetero.relay.feature.settings.SettingsScreen
import com.avetero.relay.feature.update.UpdateDialog
import com.avetero.relay.feature.vod.MovieDetailScreen
import com.avetero.relay.feature.vod.SeriesDetailScreen
import com.avetero.relay.feature.vod.VodGridScreen
import com.avetero.relay.feature.vod.VodPlayerScreen
import com.avetero.relay.player.PlayerSurface
import com.avetero.relay.ui.components.ConfirmDialog
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.components.WithRail
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun App(app: AppState, onExit: () -> Unit) {
    var exitAsk by remember { mutableStateOf(false) }
    val compact = compact()
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

    val activity = LocalContext.current as? MainActivity
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
        if (app.inPip && !app.onPlayerScreen) Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerSurface(app.player.player, Modifier.fillMaxSize())
        }
        app.message?.let { msg ->
            LaunchedEffect(msg) { delay(2_500); app.message = null }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).clip(RoundedCornerShape(12.dp)).background(C.Text).padding(horizontal = 20.dp, vertical = 12.dp)) {
                T(msg, 15, Bold, C.OnLight)
            }
        }
    }
    if (exitAsk) ConfirmDialog("Exit Relay IPTV?", "Exit", onExit, { exitAsk = false }, cancel = "Stay", destructive = false)
    app.update?.let { if (updatePrompt) UpdateDialog(app, it) { updatePrompt = false } }
}
