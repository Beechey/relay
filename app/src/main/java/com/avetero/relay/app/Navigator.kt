package com.avetero.relay.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

enum class NavKind { Push, Pop, Tab }

/** Where you are: one back stack per tab, the tab you're on, and how you got there (for the transition). */
class Navigator {
    /** One history per tab, kept while you're on another tab; "start" holds Login/Sync before there's a library. */
    val stacks = mutableStateMapOf<String, SnapshotStateList<Screen>>()
    var tab by mutableStateOf("start"); private set
    val stack: SnapshotStateList<Screen> get() = stacks.getValue(tab)
    val screen get() = stack.last()
    /** How the last navigation happened, so the transition can match it (fade through for tabs, slide for push/pop). */
    var navKind by mutableStateOf(NavKind.Tab); private set
    /** Bumped when the current tab is picked again at its root: screens scroll back to the top. */
    var reselect by mutableIntStateOf(0); private set
    /** Phone Library remembers whether it was on Movies or Series. */
    var librarySeries by mutableStateOf(false)

    init { reset() }

    /** Back to a single "start" stack (sign-in), forgetting every tab's history. */
    fun reset() { stacks.clear(); stacks["start"] = mutableStateListOf(Screen.Login); tab = "start" }

    fun push(s: Screen) { navKind = NavKind.Push; stack += s }

    /** Up the tab's history, then from another tab's root to For you. False when there's nowhere left to go. */
    fun back(): Boolean {
        val st = stack
        if (st.size > 1) { navKind = NavKind.Pop; st.removeAt(st.lastIndex); return true }
        if (tab != "home" && tab != "start" && "home" in stacks) { navKind = NavKind.Pop; tab = "home"; return true }
        return false
    }

    /**
     * Tab navigation. Another tab comes back as you left it (unless [s] asks for something specific, e.g. a channel
     * group); the current tab again goes back to its root, or at the root scrolls to the top ([reselect]).
     */
    fun go(s: Screen) {
        val k = tabOf(s) ?: return push(s)
        if (k == "start") { stacks.clear(); stacks["start"] = mutableStateListOf(s); navKind = NavKind.Tab; tab = k; return }
        stacks.remove("start")
        val st = stacks[k]
        navKind = NavKind.Tab
        when {
            st == null -> stacks[k] = mutableStateListOf(s)
            k == tab && st.size > 1 -> { navKind = NavKind.Pop; st.removeRange(1, st.size); if (s != rootScreen(k)) st[0] = s }
            k == tab -> if (st[0] == s || s == rootScreen(k)) reselect++ else st[0] = s
            s != st[0] && s != rootScreen(k) -> { st.clear(); st += s }
        }
        if (s is Screen.VodGrid) librarySeries = s.series
        tab = k
    }

    companion object {
        /** The tab a screen is the root of; null for screens that open on top of a tab. */
        fun tabOf(s: Screen): String? = when (s) {
            Screen.Login, Screen.Sync -> "start"
            Screen.Home -> "home"
            is Screen.Live -> "live"
            Screen.Guide -> "guide"
            is Screen.VodGrid -> if (s.series) "series" else "movies"
            Screen.Favourites -> "saved"
            Screen.Search -> "search"
            is Screen.Settings -> "settings"
            else -> null
        }
        fun rootScreen(tab: String): Screen = when (tab) {
            "live" -> Screen.Live(); "guide" -> Screen.Guide; "movies" -> Screen.VodGrid(false); "series" -> Screen.VodGrid(true)
            "saved" -> Screen.Favourites; "search" -> Screen.Search; "settings" -> Screen.Settings(); else -> Screen.Home
        }
    }
}
