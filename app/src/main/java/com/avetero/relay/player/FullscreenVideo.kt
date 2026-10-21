package com.avetero.relay.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.avetero.relay.ui.theme.compact

/**
 * While a player is on screen: hide status/navigation bars (swipe from an edge brings them back), draw under the notch,
 * keep the screen awake, and turn phones to landscape. Everything is restored when the player closes.
 */
@Composable
fun FullscreenVideo(inPip: Boolean = false) {
    val activity = LocalContext.current as? Activity ?: return
    val phone = compact()
    // re-applied when leaving picture-in-picture, which drops the landscape lock and hidden bars
    DisposableEffect(inPip) {
        val win = activity.window
        val ctl = WindowCompat.getInsetsController(win, win.decorView)
        val orientation = activity.requestedOrientation
        val cutout = win.attributes.layoutInDisplayCutoutMode
        WindowCompat.setDecorFitsSystemWindows(win, false)
        win.attributes = win.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl.hide(WindowInsetsCompat.Type.systemBars())
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (phone) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            ctl.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(win, true)
            win.attributes = win.attributes.apply { layoutInDisplayCutoutMode = cutout }
            win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.requestedOrientation = orientation
        }
    }
}
