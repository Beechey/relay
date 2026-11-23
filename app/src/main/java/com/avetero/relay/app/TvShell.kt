package com.avetero.relay.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvDialog
import com.avetero.relay.ui.components.LocalParts
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.components.shownWith
import kotlinx.coroutines.delay

private val tvTabs = listOf(
    Tab.Home to Icons.Rounded.Home, Tab.Live to Icons.Rounded.LiveTv, Tab.Guide to Icons.AutoMirrored.Rounded.EventNote,
    Tab.Movies to Icons.Rounded.Movie, Tab.Series to Icons.Rounded.Tv, Tab.Favourites to Icons.Rounded.Star,
    Tab.Search to Icons.Rounded.Search, Tab.Settings to Icons.Rounded.Settings,
)

/** Width of the collapsed drawer; screens lay out from here (the drawer floats over them when it opens). */
val TvRailWidth = 88.dp

/**
 * TV frame (Material 3 for TV): a navigation drawer collapsed to icons on the left that opens when it takes focus
 * (Left from the start of any row). Back: up the tab's history, then to the drawer, then For you, then the exit
 * question. Players and onboarding are full screen with no drawer.
 */
@Composable
fun TvShell(app: AppState, nav: (Tab) -> Unit, onExit: () -> Unit) {
    val parts = LocalParts.current
    val showNav = app.tab != "start" && !app.onPlayerScreen
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val here = remember { FocusRequester() }
    var exitAsk by remember { mutableStateOf(false) }

    BackHandler(showNav && app.stack.size == 1) {
        when {
            drawer.currentValue == DrawerValue.Closed -> runCatching { here.requestFocus() }
            app.tab != "home" -> nav(Tab.Home)
            else -> exitAsk = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        scrimBrush = SolidColor(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
        drawerContent = { _ ->
            if (showNav) Column(
                Modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 27.dp).selectableGroup()
                    // entering the drawer lands on the current tab, not whichever item sits level with the old focus
                    .focusProperties { onEnter = { here.requestFocus() } }.focusGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.padding(start = 8.dp, bottom = 20.dp).size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary), Alignment.Center) {
                    Text("R", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
                tvTabs.filter { it.first.shownWith(parts) }.forEach { (t, icon) ->
                    val on = app.tab == t.key
                    NavigationDrawerItem(
                        selected = on,
                        onClick = { nav(t); drawer.setValue(DrawerValue.Closed) },
                        leadingContent = { Icon(icon, null) },
                        modifier = if (on) Modifier.focusRequester(here) else Modifier,
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        ),
                    ) { Text(t.label, maxLines = 1) }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(start = if (showNav) TvRailWidth else 0.dp)) {
            RelayNavDisplay(app, nav, Modifier.fillMaxSize())
        }
    }

    // messages: an inverse-surface pill at the bottom, gone after 2.5 s
    Box(Modifier.fillMaxSize(), Alignment.BottomCenter) {
        val msg = app.message
        LaunchedEffect(msg) { if (msg != null) { delay(2_500); if (app.message == msg) app.message = null } }
        AnimatedVisibility(msg != null, enter = slideInVertically { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut()) {
            Box(Modifier.padding(bottom = 40.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.inverseSurface).padding(horizontal = 24.dp, vertical = 14.dp)) {
                Text(msg.orEmpty(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
    if (exitAsk) TvDialog({ exitAsk = false }) {
        val stay = remember { FocusRequester() }
        Text("Exit Relay?", style = MaterialTheme.typography.headlineSmall)
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Stay", { exitAsk = false }, primary = true, focusRequester = stay)
            TvButton("Exit", onExit)
        }
        LaunchedEffect(Unit) { stay.settle() }
    }
}
