package com.avetero.relay.feature.settings

import androidx.compose.runtime.Composable
import com.avetero.relay.app.AppState
import com.avetero.relay.ui.components.Tab
import com.avetero.relay.ui.theme.isTv

/**
 * Settings. Phone: a home list ("home") and one pushed screen per section. TV: sections on the left, the chosen one
 * on the right. "categories:vod" opens Manage categories on Movies.
 */
@Composable
fun SettingsScreen(app: AppState, section: String, @Suppress("UNUSED_PARAMETER") nav: (Tab) -> Unit) =
    if (isTv()) SettingsTv(app, section) else SettingsPhone(app, section)
