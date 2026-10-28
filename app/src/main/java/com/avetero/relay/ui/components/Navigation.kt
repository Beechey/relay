package com.avetero.relay.ui.components

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.avetero.relay.ui.theme.Bold
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Ico
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.LogoMark
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.compact

enum class Tab(val icon: ImageVector, val label: String) {
    Home(Icons.Home, "For you"), Live(Icons.Live, "Live"), Guide(Icons.Guide, "Guide"), Movies(Icons.Movies, "Movies"),
    Series(Icons.Series, "Series"), Favourites(Icons.Star, "Favourites"), Search(Icons.Search, "Search"), Settings(Icons.Settings, "Settings"),
}

/** TV top bar: words for the places you browse, icons for the tools. Favourites live inside Live and For you. */
private val dockTabs = listOf(Tab.Home, Tab.Live, Tab.Guide, Tab.Movies, Tab.Series)
private val dockTools = listOf(Tab.Search, Tab.Settings)

/** Phone bottom bar; Series sits beside Movies under Browse, Settings behind the For you header. */
private val bottomTabs = listOf(Tab.Home, Tab.Live, Tab.Guide, Tab.Movies, Tab.Search)
private fun bottomLabel(t: Tab) = if (t == Tab.Movies) "Browse" else t.label

/** Phone: floating glass tab bar. */
@Composable
fun BottomBar(active: Tab, onSelect: (Tab) -> Unit) {
    val shown = if (active == Tab.Series) Tab.Movies else active
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 10.dp).height(66.dp)
        .clip(RoundedCornerShape(24.dp)).background(C.Glass), verticalAlignment = Alignment.CenterVertically) {
        bottomTabs.forEach { t ->
            FocusSurface({ onSelect(t) }, Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp, vertical = 6.dp), shape = RoundedCornerShape(18.dp), label = bottomLabel(t)) { f ->
                val color = when { f -> C.OnLight; t == shown -> C.Accent; else -> C.Muted }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Ico(t.icon, color, 22.dp); T(bottomLabel(t), 10, Bold, if (t == shown && !f) C.Text else color)
                }
            }
        }
    }
}

/** TV: the floating glass bar along the top. The current place gets a raised pill; focus takes the light fill. */
@Composable
fun Dock(active: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) =
    Row(modifier.height(56.dp).clip(RoundedCornerShape(28.dp)).background(C.Glass).padding(6.dp).focusRestorer(),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LogoMark(30.dp, Modifier.padding(horizontal = 8.dp))
        dockTabs.forEach { t ->
            FocusSurface({ onSelect(t) }, Modifier.height(44.dp), selected = t == active, shape = RoundedCornerShape(22.dp), label = t.label, lift = 1.06f) { f ->
                T(t.label, 15, Bold, when { f -> C.OnLight; t == active -> C.Text; else -> C.Soft }, Modifier.align(Alignment.Center).padding(horizontal = 18.dp))
            }
        }
        Spacer(Modifier.width(6.dp))
        dockTools.forEach { t ->
            FocusSurface({ onSelect(t) }, Modifier.size(44.dp), selected = t == active, shape = RoundedCornerShape(22.dp), label = t.label, lift = 1.08f) { f ->
                Ico(t.icon, when { f -> C.OnLight; t == active -> C.Accent; else -> C.Soft }, 20.dp, Modifier.align(Alignment.Center))
            }
        }
    }

/** Height the TV top bar takes, for screens that don't put artwork behind it. */
val DockSpace = 96.dp

/**
 * The frame of every browsing screen. TV: content full width under the floating top bar ([behindDock] lets a
 * screen run artwork up behind the bar). Phones: content above the floating bottom bar.
 */
@Composable
fun WithRail(active: Tab, onSelect: (Tab) -> Unit, behindDock: Boolean = false, content: @Composable RowScope.() -> Unit) =
    if (compact()) Column(Modifier.fillMaxSize().background(C.Ground).statusBarsPadding()) {
        Row(Modifier.weight(1f).fillMaxWidth(), content = content)
        BottomBar(active, onSelect)
    } else Box(Modifier.fillMaxSize().background(C.Ground)) {
        Row(Modifier.fillMaxSize().padding(top = if (behindDock) 0.dp else DockSpace), content = content)
        Dock(active, onSelect, Modifier.align(Alignment.TopCenter).padding(top = 22.dp))
    }
