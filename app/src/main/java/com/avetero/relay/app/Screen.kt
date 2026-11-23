package com.avetero.relay.app

import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.PlayItem
import com.avetero.relay.data.model.Series
import com.avetero.relay.data.model.Vod

sealed interface Screen {
    data object Login : Screen
    data object Sync : Screen
    data object Home : Screen
    data class Live(val group: String? = null) : Screen
    data object Guide : Screen
    data class LivePlayer(val channel: Channel, val list: List<Channel>) : Screen
    data class VodGrid(val series: Boolean, val group: String? = null) : Screen
    data class MovieDetail(val vod: Vod) : Screen
    data class SeriesDetail(val series: Series) : Screen
    data class VodPlayer(val item: PlayItem, val next: List<PlayItem> = emptyList(), val episodes: List<PlayItem> = emptyList()) : Screen
    data object Search : Screen
    data object Favourites : Screen
    data class Settings(val section: String = "home") : Screen
}
