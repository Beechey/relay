package com.avetero.relay.app

import com.avetero.relay.data.model.Vod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {
    private val movie = Screen.MovieDetail(Vod(7, "Film", "", 6.7, "3", "mkv", 0))
    private fun signedIn() = Navigator().apply { go(Screen.Home) }

    @Test fun startsOnSignIn() {
        val n = Navigator()
        assertEquals("start", n.tab); assertEquals(Screen.Login, n.screen)
    }

    @Test fun eachTabKeepsItsHistory() {
        val n = signedIn()
        n.go(Screen.VodGrid(false)); n.push(movie)
        n.go(Screen.Live())
        assertEquals(Screen.Live(), n.screen)
        n.go(Screen.VodGrid(false))
        assertEquals(movie, n.screen)
        assertEquals(NavKind.Tab, n.navKind)
    }

    @Test fun currentTabAgainGoesToItsRootThenScrollsToTop() {
        val n = signedIn()
        n.go(Screen.VodGrid(false)); n.push(movie)
        n.go(Screen.VodGrid(false))
        assertEquals(listOf<Screen>(Screen.VodGrid(false)), n.stack.toList())
        val before = n.reselect
        n.go(Screen.VodGrid(false))
        assertEquals(before + 1, n.reselect)
    }

    @Test fun specificRootReplacesTheTabsHistory() {
        val n = signedIn()
        n.go(Screen.Live()); n.push(movie)
        n.go(Screen.Home)
        n.go(Screen.Live("sports"))
        assertEquals(listOf<Screen>(Screen.Live("sports")), n.stack.toList())
    }

    @Test fun backWalksTheTabThenGoesToForYouThenStops() {
        val n = signedIn()
        n.go(Screen.Guide); n.push(movie)
        assertTrue(n.back()); assertEquals(Screen.Guide, n.screen)
        assertTrue(n.back()); assertEquals("home", n.tab)
        assertFalse(n.back())
    }

    @Test fun signInScreensForgetEveryTab() {
        val n = signedIn()
        n.go(Screen.Live()); n.go(Screen.Sync)
        assertEquals(setOf("start"), n.stacks.keys)
        n.go(Screen.Home)
        assertEquals(setOf("home"), n.stacks.keys)
    }

    @Test fun libraryRemembersMoviesOrSeries() {
        val n = signedIn()
        n.go(Screen.VodGrid(true))
        assertTrue(n.librarySeries)
    }
}
