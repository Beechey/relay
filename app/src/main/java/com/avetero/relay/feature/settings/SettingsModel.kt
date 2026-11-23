package com.avetero.relay.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.avetero.relay.app.AppState
import com.avetero.relay.util.clock24
import kotlinx.coroutines.launch

/**
 * Every setting, described once and drawn by the phone (M3 grouped lists) and TV (focus rows) renderers.
 * Store values aren't snapshot state, so each section bumps a tick after a change to redraw.
 */
internal sealed interface Setting { val label: String; val desc: String? }

internal data class Toggle(override val label: String, override val desc: String?, val on: Boolean, val progress: Float? = null, val extra: String? = null, val flip: () -> Unit) : Setting

/** A fixed set of choices. Short ones show as connected buttons on phones; TV steps through them with OK. */
internal data class Choice(override val label: String, override val desc: String?, val options: List<String>, val current: String, val segmented: Boolean, val pick: (String) -> Unit) : Setting

/** A row that does something: shows [value] (or a tonal [button]) and runs [onClick]. */
internal data class Action(override val label: String, override val desc: String?, val value: String? = null, val button: String? = null, val danger: Boolean = false, val onClick: (() -> Unit)? = null) : Setting

internal class SettingGroup(val header: String?, val rows: List<Setting>)

internal val sectionNames = listOf("playlists" to "Playlists", "player" to "Playback", "live" to "Live TV and guide", "categories" to "Manage categories", "updates" to "About and updates")

/** "appearance" folded into Playback and "guide" into Live TV; "hidden"/"order" are old names for categories. */
internal fun sectionKey(section: String) = when (val k = section.substringBefore(':')) {
    "hidden", "order" -> "categories"
    "appearance" -> "player"
    "guide" -> "live"
    "home", "" -> "playlists"
    else -> k
}

private val langs = listOf("Default" to "", "English" to "en", "Spanish" to "es", "French" to "fr", "German" to "de", "Italian" to "it", "Portuguese" to "pt", "Dutch" to "nl", "Polish" to "pl", "Turkish" to "tr", "Arabic" to "ar", "Hindi" to "hi")

@Composable
internal fun playbackGroups(app: AppState, phone: Boolean): List<SettingGroup> {
    var tick by remember { mutableIntStateOf(0) }
    val s = app.store.also { tick }
    fun lang(code: String) = langs.firstOrNull { it.second == code }?.first ?: "Default"
    fun code(name: String) = langs.first { it.first == name }.second
    return listOf(
        SettingGroup("Audio and subtitles", listOf(
            Choice("Preferred audio language", "Used when a stream has more than one audio track", langs.map { it.first }, lang(s.audioLang), false) { s.audioLang = code(it); tick++ },
            Toggle("Subtitles", "On by default, in your preferred language", s.subtitlesOn) { s.subtitlesOn = !s.subtitlesOn; tick++ },
            Choice("Preferred subtitle language", null, langs.map { it.first }, lang(s.subLang), false) { s.subLang = code(it); tick++ },
        )),
        SettingGroup("Watching", listOfNotNull(
            Toggle("Play the next episode automatically", null, s.autoplayNext) { s.autoplayNext = !s.autoplayNext; tick++ },
            if (phone) Toggle("Picture-in-picture when leaving", "Keep watching in a floating window when you go home", s.pipOnLeave) { s.pipOnLeave = !s.pipOnLeave; tick++ } else null,
            Choice("Skip back", if (phone) "Double-tap the left side" else "Left on the remote", listOf("5 s", "10 s", "15 s", "30 s"), "${s.skipBackSec} s", true) { s.skipBackSec = it.removeSuffix(" s").toInt(); tick++ },
            Choice("Skip forward", if (phone) "Double-tap the right side" else "Right on the remote", listOf("10 s", "15 s", "30 s", "60 s"), "${s.skipFwdSec} s", true) { s.skipFwdSec = it.removeSuffix(" s").toInt(); tick++ },
            Choice("Picture", "Fit shows the whole picture; Fill crops to the screen", listOf("Fit", "Fill"), if (s.zoomDefault) "Fill" else "Fit", true) { s.zoomDefault = it == "Fill"; tick++ },
            Choice("Buffer", "Bigger rides out slow connections, starts slower. Applies next start.", listOf("Small", "Medium", "Large"), s.buffer, true) { s.buffer = it; tick++ },
        )),
        SettingGroup("Clock", listOf(
            Toggle("24-hour clock", if (s.clock24) "Times show as 21:30" else "Times show as 9:30 PM", s.clock24) { s.clock24 = !s.clock24; clock24 = s.clock24; app.appearanceTick++; tick++ },
        )),
    )
}

@Composable
internal fun liveGroups(app: AppState): List<SettingGroup> {
    var tick by remember { mutableIntStateOf(0) }
    val s = app.store.also { tick }
    val scope = rememberCoroutineScope()
    return listOf(
        SettingGroup("Live TV", listOf(
            Choice("Open Live TV on", null, listOf("All channels", "Last category"), s.liveStart, true) { s.liveStart = it; tick++ },
            Toggle("Resume the last channel", "Start playing the channel you watched last when you open Live TV", s.resumeLastChannel) { s.resumeLastChannel = !s.resumeLastChannel; tick++ },
            Toggle("Show channel numbers", null, s.showNumbers) { s.showNumbers = !s.showNumbers; tick++ },
            Choice("Live stream format", "MPEG-TS works with most providers; try HLS if channels fail", listOf("MPEG-TS", "HLS"), if (app.hls) "HLS" else "MPEG-TS", true) { app.hls = it == "HLS"; s.hls = app.hls },
        )),
        SettingGroup("TV guide", listOf(
            Action("Guide data", app.guideStatus.ifEmpty { "Full guide from your provider (XMLTV)" }, button = "Refresh now") { scope.launch { app.refreshGuide(force = true) } },
            Choice("Refresh the guide", "In the background, when the saved one is older than this", listOf("6 hours", "12 hours", "Daily", "Manually"), s.guideEvery, true) { s.guideEvery = it; tick++ },
        )),
    )
}

internal const val GUIDE_NOTE = "The guide keeps 2 hours back to 24 hours ahead. Channels your provider doesn't cover use its short now-and-next data."
internal const val PARTS_NOTE = "Turning one off removes it now. Turning it back on downloads it straight away. At least one stays on."

/** Playlists: what downloads, and the saved copy. The playlist cards themselves are drawn per platform. */
@Composable
internal fun playlistGroups(app: AppState): List<SettingGroup> {
    val a = app.account ?: return emptyList()
    var tick by remember { mutableIntStateOf(0) }
    val s = app.store.also { tick }
    val hiddenLive = app.liveCats.count { "live:${it.id}" in app.hidden }
    val parts = listOf(
        Triple("live", "Live TV and guide", "%,d channels".format(app.channels.size) + if (hiddenLive > 0) " · %,d categories hidden".format(hiddenLive) else ""),
        Triple("vod", "Movies", "%,d movies".format(app.movies.size)),
        Triple("series", "Series", "%,d series".format(app.series.size)),
    ).map { (type, label, counts) ->
        val on = type in app.parts
        val size = s.cacheFile(a, type).length() / 1_048_576.0
        val loading = app.partLoading == type
        val pp = app.partProgress
        val unit = when (type) { "live" -> "channels"; "vod" -> "movies"; else -> "series" }
        Toggle(label, when {
            loading && pp.reading -> "Reading… %,d %s".format(pp.count, unit)
            loading -> if (pp.bytes > 0) "Downloading… %.1f MB".format(pp.bytes / 1_048_576f) else "Downloading…"
            on -> counts
            type == "live" -> "Off: Live and the Guide go away, and nothing is downloaded"
            else -> "Off: the $label tab and its rows go away, and nothing is downloaded"
        }, on, progress = if (loading) pp.fraction else null, extra = if (!loading && on && size > 0) (if (size < 1) "%.1f MB" else "%.0f MB").format(size) else null) { app.setPart(type, !on) }
    }
    val skip = !s.downloadHidden
    return listOf(
        SettingGroup("What Relay downloads", parts + Toggle("Skip hidden categories",
            if (skip) "Hidden categories aren't downloaded. Show one again and it downloads then."
            else "Turn on to stop downloading what you've hidden: refreshing gets faster and uses less space. Applies at the next refresh.", skip) { s.downloadHidden = skip; tick++ }),
        SettingGroup("Saved copy", listOf(
            Choice("Refresh the playlist", "Re-download channels, movies and series when the saved copy is older than this", listOf("Every start", "Daily", "Weekly", "Monthly", "Manually"), s.refreshEvery, false) { s.refreshEvery = it; tick++ },
            Action("Clear saved data", "Deletes the saved catalogue and guide, then downloads them again", danger = true) { app.clearCacheAndReload() },
        )),
    )
}

@Composable
internal fun updateGroup(app: AppState): SettingGroup {
    var tick by remember { mutableIntStateOf(0) }
    val s = app.store.also { tick }
    return SettingGroup(null, listOf(
        Toggle("Check for updates automatically", "Checks GitHub releases when the app starts", s.checkUpdates) { s.checkUpdates = !s.checkUpdates; tick++ },
        Toggle("Include beta releases", "Get pre-release builds before everyone else", s.betaUpdates) { s.betaUpdates = !s.betaUpdates; tick++ },
        Action("Update source", if (com.avetero.relay.data.update.Updater.enabled) "github.com/${com.avetero.relay.data.update.Updater.repo}" else "Not configured"),
    ))
}

/** "Check for updates": the status line to show (null while an update is waiting) and the action. */
@Composable
internal fun rememberUpdateCheck(app: AppState): Pair<String?, () -> Unit> {
    var status by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    return status to {
        status = "Checking…"
        scope.launch {
            status = try {
                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.avetero.relay.data.update.Updater.latest(app.store.betaUpdates) }
                app.update = r?.takeIf(com.avetero.relay.data.update.Updater::isNewer)
                if (app.update == null) "You're up to date" else null
            } catch (e: Exception) { "Couldn't reach GitHub: ${e.message}" }
        }
    }
}
