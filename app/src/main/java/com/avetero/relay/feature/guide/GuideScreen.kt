package com.avetero.relay.feature.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.avetero.relay.app.AppState
import com.avetero.relay.app.AppState.Companion.ALL
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.Move
import com.avetero.relay.data.model.PlayItem
import com.avetero.relay.data.model.Programme
import com.avetero.relay.feature.categories.categoryMover
import com.avetero.relay.feature.live.hideLive
import com.avetero.relay.feature.live.rememberLiveGroups
import com.avetero.relay.ui.components.Group
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.theme.isTv
import com.avetero.relay.util.fmtTime
import com.avetero.relay.util.nowSec
import java.util.Calendar

/**
 * The TV guide. Phone (boards PhoneGuide, PhoneGuideSheet): what's on now (or at a chosen time) as a list, a channel's
 * schedule in a sheet. TV (board TvGuide): a 2-hour EPG grid you can move through the day, with a preview.
 */
@Composable
fun GuideScreen(app: AppState, @Suppress("UNUSED_PARAMETER") nav: (Tab) -> Unit) {
    val groups = rememberLiveGroups(app)
    val groupId = rememberSaveable { mutableStateOf(ALL) }
    val guideOnly = rememberSaveable { mutableStateOf(true) }
    val shift = rememberSaveable { mutableIntStateOf(0) }
    val baseStart = remember { nowSec() / 1800 * 1800 }
    val g = remember(groups) { GuideState(app, groups, groupId, guideOnly, shift, baseStart) }
    if (isTv()) GuideTv(app, g) else GuidePhone(app, g)
}

/** What both layouts share: the category, the Guide-only filter, and the time shift. */
internal class GuideState(
    private val app: AppState, val groups: List<Group>,
    groupId: MutableState<String>, guideOnly: MutableState<Boolean>, shift: MutableIntState,
    /** The half hour the screen opened in; [shift] counts hours from here. */
    val baseStart: Long,
) {
    var groupId by groupId
    var guideOnly by guideOnly
    /** Hours from [baseStart]: 2 h back to 22 h ahead, the day the guide covers. */
    var shift by shift
    val windowStart get() = baseStart + shift * 3600L
    val current get() = groups.firstOrNull { it.id == groupId }
    /** Shift for tomorrow 08:00. */
    val tomorrowShift = run {
        val cal = Calendar.getInstance().apply { timeInMillis = baseStart * 1000; add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0) }
        ((cal.timeInMillis / 1000 - baseStart) / 3600).toInt().coerceAtMost(MAX_SHIFT)
    }
    /** Shift for tonight 20:00, or null once it's past. */
    val tonightShift = run {
        val cal = Calendar.getInstance().apply { timeInMillis = baseStart * 1000; set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0) }
        ((cal.timeInMillis / 1000 - baseStart) / 3600).toInt().takeIf { it > 3 }
    }

    fun move(by: Int) { shift = (shift + by).coerceIn(MIN_SHIFT, MAX_SHIFT) }
    fun hide(g: Group) = hideLive(app, g, groupId) { groupId = it }
    fun moveCategory(g: Group, m: Move?) = categoryMover(app, "live")(g, m)

    companion object { const val MIN_SHIFT = -2; const val MAX_SHIFT = 22 }
}

/** The channels to list: the category's, narrowed to ones with a guide when the filter is on and there's a guide to filter by. */
internal class GuideChannels(val list: List<Channel>, val canFilter: Boolean, val filtering: Boolean)

@Composable
internal fun rememberGuideChannels(app: AppState, g: GuideState): GuideChannels {
    val all = remember(g.groupId, app.channels, app.favourites, app.hidden, app.recentChannels) { app.channelsIn(g.groupId) }
    val withGuide = remember(all, app.guide) { all.filter { c -> c.epgId.isNotEmpty() && !app.guide[c.epgId].isNullOrEmpty() } }
    // the filter (and its tick) only when there is guide data to filter by
    val filtering = g.guideOnly && withGuide.isNotEmpty()
    return GuideChannels(if (filtering) withGuide else all, withGuide.isNotEmpty(), filtering)
}

/** A channel's programmes, sorted and de-duplicated, remembered per change of its guide. */
@Composable
internal fun rememberProgrammes(app: AppState, c: Channel): List<Programme> {
    val raw = app.programmesFor(c)
    return remember(raw) { raw.orEmpty().withoutOverlaps() }
}

/** OK / tap on a programme: live → watch; past on a catch-up channel → watch from its start; later → remind me. */
internal fun act(app: AppState, c: Channel, p: Programme, list: List<Channel>) {
    val now = nowSec()
    when {
        p.start > now -> app.toggleReminder(c, p)
        p.end <= now && c.archive -> watchCatchup(app, c, p)
        else -> watchLive(app, c, list)
    }
}

internal fun watchLive(app: AppState, c: Channel, list: List<Channel>) { app.player.play(app.liveUrl(c), title = c.name); app.push(Screen.LivePlayer(c, list)) }

/** Catch-up plays in the movie player, so you can pause and skip; it isn't added to Continue watching. */
internal fun watchCatchup(app: AppState, c: Channel, p: Programme) =
    app.push(Screen.VodPlayer(PlayItem(app.catchupUrl(c, p), p.title, "${c.name} · ${fmtTime(p.start)}", c.icon, "cu${c.id}-${p.start}")))

/** Some guides list a channel's programmes twice (several regions); keep the first of any that overlap. */
internal fun List<Programme>.withoutOverlaps(): List<Programme> {
    var end = Long.MIN_VALUE
    return sortedBy { it.start }.filter { p -> (p.start >= end).also { if (it) end = p.end } }
}

/** The programme airing at [t] in a sorted list. */
internal fun List<Programme>.at(t: Long): Programme? = firstOrNull { t >= it.start && t < it.end }
