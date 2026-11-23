package com.avetero.relay.app

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.avetero.relay.MainActivity
import com.avetero.relay.data.update.Updater
import com.avetero.relay.feature.favourites.FavouritesScreen
import com.avetero.relay.feature.guide.GuideScreen
import com.avetero.relay.feature.guide.ReminderCard
import com.avetero.relay.feature.home.HomeScreen
import com.avetero.relay.feature.live.LivePlayerScreen
import com.avetero.relay.feature.live.LiveScreen
import com.avetero.relay.feature.live.LiveTvScreen
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
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.motion.LocalSharedScope
import com.avetero.relay.ui.motion.LocalScreenAnim
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.avetero.relay.ui.motion.fadeThrough
import com.avetero.relay.ui.motion.sharedAxisIn
import com.avetero.relay.ui.motion.sharedAxisOut
import com.avetero.relay.ui.motion.tvFade
import com.avetero.relay.ui.theme.isTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun App(app: AppState, onExit: () -> Unit) {
    val tv = isTv()
    var updatePrompt by remember { mutableStateOf(false) }
    val nav: (Tab) -> Unit = { t -> app.go(t.screen(app)) }

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

    // the live channel follows what's on screen, so the phone's mini player knows what it's showing
    LaunchedEffect(app.screen) {
        when (val s = app.screen) { is Screen.LivePlayer -> app.liveChannel = s.channel; is Screen.VodPlayer -> app.liveChannel = null; else -> Unit }
    }

    // stop playback where there's nowhere to show it. Phones keep live TV going in the mini player; TV stops,
    // otherwise audio plays on unseen (e.g. back from fullscreen to For you)
    LaunchedEffect(app.screen, tv) {
        val showsVideo = app.onPlayerScreen || app.screen is Screen.Live || (app.screen == Screen.Guide && tv)
        val keep = !tv && app.liveChannel != null && app.tab != "start"
        if (!showsVideo && !keep && !app.inPip) app.player.stop()
    }

    // full TV guide: once the library is in memory, load it from cache or refresh it (12 h)
    LaunchedEffect(app.account, app.libraryLoading, app.channels.isNotEmpty()) {
        if (app.account != null && !app.libraryLoading && app.channels.isNotEmpty() && app.screen != Screen.Sync) app.refreshGuide()
    }

    val activity = LocalContext.current as? MainActivity
    val autoPip = app.pipEligible
    LaunchedEffect(autoPip) { activity?.setAutoPip(autoPip) }

    // one shared-transition scope over everything, so the phone's mini player (outside the screens) can morph into Live
    SharedTransitionLayout(Modifier.fillMaxSize()) { CompositionLocalProvider(LocalSharedScope provides this) { Box(Modifier.fillMaxSize()) {
        if (tv) TvShell(app, nav, onExit) else PhoneShell(app, nav)
        // PiP from Live TV's preview: the floating window shows only the video
        if (app.inPip && !app.onPlayerScreen) Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerSurface(app.player.player, Modifier.fillMaxSize())
        }
        ReminderCard(app, Modifier.align(Alignment.TopEnd).padding(top = 96.dp, end = 32.dp))
    } } }
    app.update?.let { if (updatePrompt) UpdateDialog(app, it) { updatePrompt = false } }
}

/** The screen a tab opens (phone Library comes back on Movies or Series, whichever you left it on). */
fun Tab.screen(app: AppState): Screen = when (this) {
    Tab.Home -> Screen.Home; Tab.Live -> Screen.Live(); Tab.Guide -> Screen.Guide
    Tab.Movies -> Screen.VodGrid(false); Tab.Series -> Screen.VodGrid(true)
    Tab.Favourites -> Screen.Favourites; Tab.Search -> Screen.Search; Tab.Settings -> Screen.Settings()
}

/** The tab key ("home", "live"…) a [Tab] stands for. */
val Tab.key get() = AppState.tabOf(screenKey) ?: "home"
private val Tab.screenKey: Screen get() = when (this) {
    Tab.Home -> Screen.Home; Tab.Live -> Screen.Live(); Tab.Guide -> Screen.Guide; Tab.Movies -> Screen.VodGrid(false)
    Tab.Series -> Screen.VodGrid(true); Tab.Favourites -> Screen.Favourites; Tab.Search -> Screen.Search; Tab.Settings -> Screen.Settings()
}

/** Every screen, by its key. Each platform's look lives in the feature files; this only routes. */
@Composable
fun ScreenContent(app: AppState, s: Screen, nav: (Tab) -> Unit) {
    val tv = isTv()
    when (s) {
        Screen.Login -> LoginScreen(app)
        Screen.Sync -> SyncScreen(app)
        Screen.Home -> HomeScreen(app, nav)
        is Screen.Live -> if (!tv) LiveScreen(app, nav, s.group) else LiveTvScreen(app, s.group)
        Screen.Guide -> GuideScreen(app, nav)
        is Screen.LivePlayer -> LivePlayerScreen(app, s.channel, s.list)
        is Screen.VodGrid -> VodGridScreen(app, s.series, nav, s.group)
        is Screen.MovieDetail -> MovieDetailScreen(app, s.vod)
        is Screen.SeriesDetail -> SeriesDetailScreen(app, s.series)
        is Screen.VodPlayer -> VodPlayerScreen(app, s.item, s.next, s.episodes)
        Screen.Search -> SearchScreen(app, nav)
        Screen.Favourites -> FavouritesScreen(app, nav)
        is Screen.Settings -> SettingsScreen(app, s.section, nav)
    }
}

/**
 * Navigation 3 over our own per-tab back stacks. Every tab's entries stay decorated, so each keeps its saved
 * state (scroll positions, fields) while you're elsewhere. Phones show For you under another tab's root, so
 * predictive back previews where Back goes.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RelayNavDisplay(app: AppState, nav: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val tv = isTv()
    val holder = rememberSaveableStateHolderNavEntryDecorator<Screen>()
    val decorated = app.stacks.keys.toList().associateWith { k ->
        key(k) {
            val list = app.stacks.getValue(k).toList()
            val entries = remember(list) {
                // identity, not equality: the same title can be open twice, and Screen.toString() can be huge
                list.mapIndexed { i, s -> NavEntry(s, contentKey = "$k/$i/${System.identityHashCode(s)}") {
                    CompositionLocalProvider(LocalScreenAnim provides LocalNavAnimatedContentScope.current) { ScreenContent(app, it, nav) }
                } }
            }
            rememberDecoratedNavEntries(entries, listOf(holder))
        }
    }
    val current = decorated[app.tab] ?: return
    val shown = if (!tv && app.tab != "home" && app.tab != "start") decorated["home"].orEmpty() + current else current
    // container transforms only within a tab: across tabs the same poster can sit on both screens and would fly over
    val lastTab = remember { arrayOf(app.tab) }
    val sameTab = remember(app.tab, app.navKind, current.size) { (lastTab[0] == app.tab).also { lastTab[0] = app.tab } }
    val shared = LocalSharedScope.current ?: return
    CompositionLocalProvider(LocalSharedScope provides shared.takeIf { sameTab }) {
            NavDisplay(
                modifier = modifier,
                entries = shown,
                sharedTransitionScope = shared,
                transitionSpec = { if (tv) tvFade() else if (app.navKind == NavKind.Tab) fadeThrough() else sharedAxisIn() },
                popTransitionSpec = { if (tv) tvFade() else if (app.navKind == NavKind.Tab) fadeThrough() else sharedAxisOut() },
                onBack = { app.back() },
            )
    }
}
