package com.avetero.relay.feature.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.avetero.relay.app.AppState
import com.avetero.relay.app.AppState.Companion.ALL
import com.avetero.relay.app.AppState.Companion.FAVS
import com.avetero.relay.ui.components.Group

/** Favourites, All channels, then the provider's live categories that have channels. Shared by Live TV and the Guide. */
@Composable
fun rememberLiveGroups(app: AppState): List<Group> = remember(app.liveCatsVisible, app.channels, app.favourites, app.recentChannels) {
    val counts = app.channels.groupingBy { it.categoryId }.eachCount()
    listOfNotNull(Group(FAVS, "Favourites", app.favourites.count { it.startsWith("c") }),
        Group(AppState.RECENT, "Recent", app.recentChannels.size).takeIf { app.recentChannels.isNotEmpty() },
        Group(ALL, "All channels", app.channelsVisible.size)) +
        app.liveCatsVisible.map { Group(it.id, it.name, counts[it.id] ?: 0) }.filter { it.count > 0 }
}

/** Hides a live category; if it was the one showing, falls back to All channels. */
fun hideLive(app: AppState, g: Group, current: String, setGroup: (String) -> Unit) {
    app.setCategoryHidden("live:${g.id}", true)
    if (g.id == current) setGroup(ALL)
    app.say("Hidden “${g.name}”") { app.setCategoryHidden("live:${g.id}", false) }
}
