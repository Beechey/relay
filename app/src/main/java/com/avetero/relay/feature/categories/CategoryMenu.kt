package com.avetero.relay.feature.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Move
import com.avetero.relay.ui.components.Group
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.m3.RelaySheet
import com.avetero.relay.ui.theme.isTv
import com.avetero.relay.ui.tv.TvDialog
import com.avetero.relay.ui.tv.TvSurface

/** Hold-OK actions for a category in Live TV, Movies or Series: move a single one up or down, or open Manage categories. */
fun categoryMover(app: AppState, type: String): (Group, Move?) -> Unit = { g, m ->
    if (m == null) app.push(Screen.Settings("categories:$type"))
    else { app.moveCategories(type, setOf(g.id), m); app.message = "Moved “${g.name}”" }
}

/** Hold-OK / long-press menu on a category: move it, open the bulk reorder screen ([onMove] with null), or hide it. */
@Composable
fun CategoryMenu(name: String, onMove: (Move?) -> Unit, onHide: (() -> Unit)?, onDismiss: () -> Unit) {
    val items = listOfNotNull(
        Triple("Move to top", Icons.Rounded.VerticalAlignTop) { onMove(Move.Top) },
        Triple("Move up one", Icons.Rounded.ArrowUpward) { onMove(Move.Up) },
        Triple("Move down one", Icons.Rounded.ArrowDownward) { onMove(Move.Down) },
        Triple("Move to bottom", Icons.Rounded.VerticalAlignBottom) { onMove(Move.Bottom) },
        Triple<String, ImageVector, () -> Unit>("Reorder several…", Icons.Rounded.Reorder) { onMove(null) },
        onHide?.let { Triple("Hide category", Icons.Rounded.VisibilityOff, it) },
    )
    if (isTv()) TvDialog(onDismiss, width = 420.dp) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { first.settle() }
        androidx.tv.material3.Text(name, style = androidx.tv.material3.MaterialTheme.typography.headlineSmall, maxLines = 2)
        items.forEachIndexed { i, (label, icon, action) ->
            TvSurface(action, Modifier.fillMaxWidth().height(52.dp), focusRequester = if (i == 0) first else null) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    androidx.tv.material3.Icon(icon, null)
                    androidx.tv.material3.Text(label, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
                }
            }
        }
    } else RelaySheet(onDismiss) {
        Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 2, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
        items.forEach { (label, icon, action) ->
            ListItem({ Text(label) }, Modifier.clickable(onClick = action), leadingContent = { Icon(icon, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
    }
}
