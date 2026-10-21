package com.avetero.relay.ui.components

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
    Home(Icons.Home, "Home"), Live(Icons.Live, "Live TV"), Guide(Icons.Guide, "TV Guide"), Movies(Icons.Movies, "Movies"),
    Series(Icons.Series, "Series"), Favourites(Icons.Star, "Favourites"), Search(Icons.Search, "Search"), Settings(Icons.Settings, "Settings"),
    More(Icons.More, "More"), // phone bottom bar only
}

private val bottomTabs = listOf(Tab.Home, Tab.Live, Tab.Movies, Tab.Series, Tab.More)

/** Phone bottom tab bar; Guide, Favourites, Search and Settings live under More. */
@Composable
fun BottomBar(active: Tab, onSelect: (Tab) -> Unit) {
    val shown = if (active in bottomTabs) active else Tab.More
    Row(Modifier.fillMaxWidth().background(Color(0xFF080A0F)).navigationBarsPadding().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        bottomTabs.forEach { t ->
            FocusSurface({ onSelect(t) }, Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp, vertical = 6.dp), shape = RoundedCornerShape(12.dp), label = t.label) { f ->
                val color = when { f -> C.OnLight; t == shown -> C.Accent; else -> C.Muted }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Ico(t.icon, color, 22.dp); T(t.label, 11, Bold, color)
                }
            }
        }
    }
}

/** Nav rail (Rail.dc.html). */
@Composable
fun Rail(active: Tab, onSelect: (Tab) -> Unit) {
    Column(
        Modifier.width(72.dp).fillMaxHeight().background(Color(0xFF080A0F)).padding(vertical = 24.dp).focusRestorer(),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LogoMark(40.dp)
        Spacer(Modifier.height(18.dp))
        Tab.entries.filter { it != Tab.More }.forEach { t ->
            if (t == Tab.Settings) Spacer(Modifier.weight(1f))
            FocusSurface({ onSelect(t) }, Modifier.size(48.dp), selected = t == active, label = t.label) { f ->
                Ico(t.icon, when { f -> C.OnLight; t == active -> C.Accent; else -> C.Muted }, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/** Rail + content on TV; content + bottom tab bar on phones. The frame of most screens. */
@Composable
fun WithRail(active: Tab, onSelect: (Tab) -> Unit, content: @Composable RowScope.() -> Unit) =
    if (compact()) Column(Modifier.fillMaxSize().background(C.Ground).statusBarsPadding()) {
        Row(Modifier.weight(1f).fillMaxWidth(), content = content)
        BottomBar(active, onSelect)
    } else Row(Modifier.fillMaxSize().background(C.Ground)) { Rail(active, onSelect); content() }
