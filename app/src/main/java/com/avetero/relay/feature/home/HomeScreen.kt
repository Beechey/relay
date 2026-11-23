package com.avetero.relay.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.PlayItem
import com.avetero.relay.data.model.Resume
import com.avetero.relay.data.model.Series
import com.avetero.relay.data.model.Vod
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.theme.isTv
import com.avetero.relay.util.fmtDur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * For you: what you were watching, what's on your favourite and recent channels, and recent movies and series.
 * Phone (board Main): search bar, Continue watching carousel, Live now list, poster rows.
 * TV (board TvHome): a hero carousel through what you were watching, then rows of cards.
 */
@Composable
fun HomeScreen(app: AppState, @Suppress("UNUSED_PARAMETER") nav: (Tab) -> Unit) {
    val data = rememberHomeData(app)
    if (isTv()) HomeTv(app, data) else HomePhone(app, data)
}

/** Everything For you shows, derived once per change of the underlying lists. */
internal class HomeData(
    val resumes: List<Resume>,
    /** The hero / carousel: only what's still downloaded (a movie or series part that's switched off drops out). */
    val heroItems: List<Resume>,
    val live: List<Channel>,
    val recentMovies: List<Vod>,
    val recentSeries: List<Series>,
    val addedMovies: List<Vod>,
)

@Composable
private fun rememberHomeData(app: AppState): HomeData {
    val heroItems = remember(app.resumes, app.parts) { app.resumes.filter { (it.vodId == 0 || "vod" in app.parts) && (it.seriesId == 0 || "series" in app.parts) } }
    val live = remember(app.favourites, app.recentChannels, app.channels) {
        val favs = app.favourites.mapNotNull { k -> if (k.startsWith("c")) k.drop(1).toIntOrNull()?.let(app.channelById::get) else null }
        (favs + app.channelsIn(AppState.RECENT)).distinctBy { it.id }.take(12)
    }
    val moviesById = remember(app.movies) { app.movies.associateBy { it.id } }
    val recentMovies = remember(app.recentMovies, moviesById) { app.recentMovies.mapNotNull(moviesById::get) }
    val recentSeries = remember(app.recentSeries, app.series) { val byId = app.series.associateBy { it.id }; app.recentSeries.mapNotNull(byId::get) }
    // newest first, leaving out ones already in "Recently watched" so a poster appears once (its shared-element key is unique)
    val addedMovies = remember(app.movies, recentMovies) { val seen = recentMovies.mapTo(HashSet()) { it.id }; app.movies.sortedByDescending { it.added }.asSequence().filter { it.id !in seen }.take(20).toList() }
    return HomeData(app.resumes, heroItems, live, recentMovies, recentSeries, addedMovies)
}

/** Artwork and story for a hero item, fetched from the provider once per title. */
internal data class HeroInfo(val backdrop: String, val plot: String)

internal fun greeting() = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }

/** "E6 · EN - Fringe - S05E06" → "Fringe"; movies keep their own name. */
internal fun heroTitle(r: Resume): String =
    if (r.seriesId != 0) r.meta.removePrefix("Up next · ").substringBefore(" · ").substringAfter(" - ").ifBlank { r.title } else r.title

/** The line under a Continue watching title: episode or year, and how long is left. */
internal fun resumeLine(r: Resume): String =
    r.meta.removePrefix("Up next · ") + if (r.pos > 0 && r.dur > r.pos) " · ${fmtDur(r.dur - r.pos)} left" else ""

internal suspend fun loadInfo(app: AppState, r: Resume): HeroInfo = withContext(Dispatchers.IO) {
    runCatching {
        when {
            r.vodId != 0 -> app.api.vodInfo(r.vodId).let { HeroInfo(it.backdrop, it.plot) }
            r.seriesId != 0 -> app.api.seriesInfo(r.seriesId).let { HeroInfo(it.cover, it.plot) }
            else -> HeroInfo("", "")
        }
    }.getOrDefault(HeroInfo("", ""))
}

/** Movies play straight away from where you stopped; series open on their page, where Resume picks the episode. */
internal fun resume(app: AppState, r: Resume) {
    val vod = app.movies.firstOrNull { it.id == r.vodId }
    if (vod != null) app.push(Screen.VodPlayer(PlayItem(app.api.vodUrl(vod), vod.name, r.meta, vod.icon, r.key, vodId = vod.id)))
    else details(app, r)
}

internal fun details(app: AppState, r: Resume) {
    app.movies.firstOrNull { it.id == r.vodId }?.let { return app.push(Screen.MovieDetail(it)) }
    app.series.firstOrNull { it.id == r.seriesId }?.let { app.push(Screen.SeriesDetail(it)) }
}

/** Takes an item off Continue watching, with Undo putting it back. */
internal fun removeResume(app: AppState, r: Resume) {
    app.removeResume(r.key)
    // upNext keeps it even when barely started, so Undo always brings it back
    app.say("Removed from Continue watching") { app.saveResume(r, upNext = true) }
}

internal fun watchChannel(app: AppState, c: Channel, list: List<Channel>) = app.push(Screen.LivePlayer(c, list))
