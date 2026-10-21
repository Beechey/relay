package com.avetero.relay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import com.avetero.relay.ui.C
import com.avetero.relay.ui.ExitDialog
import com.avetero.relay.ui.FavouritesScreen
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = AppState(applicationContext)
        setContent { App(app, onExit = ::finish) }
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

@Composable
fun App(app: AppState, onExit: () -> Unit) {
    var exitAsk by remember { mutableStateOf(false) }
    var updatePrompt by remember { mutableStateOf(false) }
    val nav: (Tab) -> Unit = { t ->
        app.go(when (t) {
            Tab.Home -> Screen.Home; Tab.Live -> Screen.Live; Tab.Guide -> Screen.Guide
            Tab.Movies -> Screen.VodGrid(false); Tab.Series -> Screen.VodGrid(true)
            Tab.Favourites -> Screen.Favourites; Tab.Search -> Screen.Search; Tab.Settings -> Screen.Settings()
        })
    }

    // background refresh of a cached catalogue, then a silent update check
    LaunchedEffect(app.account) {
        if (app.account != null && app.screen == Screen.Home && app.store.refreshOnStart) runCatching { app.sync() }
        if (Updater.enabled && app.store.checkUpdates) runCatching {
            val r = withContext(Dispatchers.IO) { Updater.latest(app.store.betaUpdates) }
            if (r != null && Updater.isNewer(r)) { app.update = r; updatePrompt = r.version != app.store.skippedVersion }
        }
    }

    BackHandler {
        when {
            app.stack.size > 1 -> app.back()
            app.screen !in listOf(Screen.Home, Screen.Login, Screen.Sync) -> app.go(Screen.Home)
            else -> exitAsk = true
        }
    }

    Box(Modifier.fillMaxSize().background(C.Ground)) {
        when (val s = app.screen) {
            Screen.Login -> LoginScreen(app)
            Screen.Sync -> SyncScreen(app)
            Screen.Home -> HomeScreen(app, nav)
            Screen.Live -> LiveScreen(app, nav)
            Screen.Guide -> GuideScreen(app, nav)
            is Screen.LivePlayer -> LivePlayerScreen(app, s.channel, s.list)
            is Screen.VodGrid -> VodGridScreen(app, s.series, nav)
            is Screen.MovieDetail -> MovieDetailScreen(app, s.vod)
            is Screen.SeriesDetail -> SeriesDetailScreen(app, s.series)
            is Screen.VodPlayer -> VodPlayerScreen(app, s.item, s.next)
            Screen.Search -> SearchScreen(app, nav)
            Screen.Favourites -> FavouritesScreen(app, nav)
            is Screen.Settings -> SettingsScreen(app, s.section, nav)
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
