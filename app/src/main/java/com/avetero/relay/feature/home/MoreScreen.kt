package com.avetero.relay.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avetero.relay.app.AppState
import com.avetero.relay.ui.components.FocusSurface
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.components.WithRail
import com.avetero.relay.ui.theme.C
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Ico
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.T
import com.avetero.relay.ui.theme.fg
import com.avetero.relay.ui.theme.sub
import com.avetero.relay.util.fmtDate

/** Phone "More" tab: the rail destinations that don't fit in the bottom bar. */
@Composable
fun MoreScreen(app: AppState, nav: (Tab) -> Unit) = WithRail(Tab.More, nav) {
    Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("More", 26, Heavy, modifier = Modifier.padding(bottom = 6.dp))
        listOf(Tab.Guide to "Now and next across your channels", Tab.Favourites to "Channels, movies and series you've starred",
            Tab.Search to "Find channels, movies and series", Tab.Settings to "Playlists, player and updates").forEach { (t, desc) ->
            FocusSurface({ nav(t) }, Modifier.fillMaxWidth().height(72.dp), idle = C.Surface, shape = RoundedCornerShape(14.dp)) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Ico(t.icon, if (f) C.OnLight else C.Accent, 24.dp)
                    Column(Modifier.weight(1f)) { T(t.label, 16, Heavy, fg(f)); T(desc, 13, color = sub(f)) }
                    Ico(Icons.Chevron, sub(f), 16.dp)
                }
            }
        }
        T(listOfNotNull(app.account?.name, app.info?.expires?.let { "expires " + fmtDate(it) }).joinToString(" · "), 13, color = C.Muted, modifier = Modifier.padding(top = 10.dp))
    }
}
