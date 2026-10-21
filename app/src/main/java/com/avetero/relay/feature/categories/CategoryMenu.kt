package com.avetero.relay.feature.categories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.window.Dialog
import com.avetero.relay.app.AppState
import com.avetero.relay.app.Screen
import com.avetero.relay.data.model.Move
import com.avetero.relay.ui.components.Group
import com.avetero.relay.ui.components.MenuItem
import com.avetero.relay.ui.components.Panel
import com.avetero.relay.ui.theme.Heavy
import com.avetero.relay.ui.theme.Icons
import com.avetero.relay.ui.theme.T

/** Hold-OK actions for a category in Live TV, Movies or Series: move a single one up or down, or move it to Settings → Category order. */
fun categoryMover(app: AppState, type: String): (Group, Move?) -> Unit = { g, m ->
    if (m == null) app.push(Screen.Settings("categories:$type"))
    else { app.moveCategories(type, setOf(g.id), m); app.message = "Moved “${g.name}”" }
}

/** Hold-OK menu on a category: move it, open the bulk reorder screen ([onMove] with null), or hide it. */
@Composable
fun CategoryMenu(name: String, onMove: (Move?) -> Unit, onHide: (() -> Unit)?, onDismiss: () -> Unit) = Dialog(onDismiss) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Panel(360) {
        T(name, 18, Heavy, maxLines = 2)
        MenuItem("Move to top", Icons.Top, { onMove(Move.Top) }, first)
        MenuItem("Move up one", Icons.Up, { onMove(Move.Up) })
        MenuItem("Move down one", Icons.Down, { onMove(Move.Down) })
        MenuItem("Move to bottom", Icons.Bottom, { onMove(Move.Bottom) })
        MenuItem("Reorder several…", Icons.More, { onMove(null) })
        if (onHide != null) MenuItem("Hide category", Icons.Warn, onHide)
    }
}
