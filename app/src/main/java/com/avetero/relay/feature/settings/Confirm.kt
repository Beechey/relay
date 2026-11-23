package com.avetero.relay.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.avetero.relay.ui.components.settle
import com.avetero.relay.ui.theme.isTv
import com.avetero.relay.ui.tv.TvButton
import com.avetero.relay.ui.tv.TvDialog

/** A yes/no question: M3 AlertDialog on phones, [TvDialog] on TV (focus starts on Cancel, so a stray OK does nothing). */
@Composable
internal fun Confirm(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (isTv()) TvDialog(onDismiss) {
        val cancel = remember { FocusRequester() }
        LaunchedEffect(Unit) { cancel.settle() }
        androidx.tv.material3.Text(title, style = androidx.tv.material3.MaterialTheme.typography.headlineSmall)
        androidx.tv.material3.Text(message, style = androidx.tv.material3.MaterialTheme.typography.bodyLarge, color = androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.End)) {
            TvButton("Cancel", onDismiss, focusRequester = cancel)
            TvButton(confirm, onConfirm, primary = true)
        }
    } else AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { TextButton(onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
