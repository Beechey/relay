package com.avetero.relay

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import com.avetero.relay.app.App
import com.avetero.relay.app.AppState
import com.avetero.relay.ui.theme.DesignScale

class MainActivity : FragmentActivity() {
    private lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = AppState(applicationContext)
        setContent { DesignScale { androidx.compose.runtime.CompositionLocalProvider(com.avetero.relay.ui.components.LocalParts provides app.shownParts) { App(app, onExit = ::finish) } } }
    }

    /** Phones and tablets only: TVs keep fullscreen playback. */
    private val canPip get() = Build.VERSION.SDK_INT >= 26 &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
        !packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    /** The video fills the window while a player is showing; telling Android lets it animate from there. */
    private fun pipParams(autoEnter: Boolean? = null): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        window.decorView.let { v -> if (v.width > 0) b.setSourceRectHint(Rect(0, 0, v.width, v.height)) }
        if (Build.VERSION.SDK_INT >= 31 && autoEnter != null) b.setAutoEnterEnabled(autoEnter).setSeamlessResizeEnabled(true)
        return b.build()
    }

    fun enterPip() {
        if (!canPip || app.player.url == null || isInPictureInPictureMode) return
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    /**
     * Android 12+: let the system enter PiP itself while a player is showing, so the swipe-home
     * gesture animates straight into the floating window.
     */
    fun setAutoPip(enabled: Boolean) {
        if (!canPip || Build.VERSION.SDK_INT < 31) return
        runCatching { setPictureInPictureParams(pipParams(autoEnter = enabled)) }
    }

    // Home, swipe-home or recent apps while a video is playing: shrink it to a floating window.
    // Also on Android 12+, where auto-enter can be skipped (e.g. fullscreen with the bars hidden).
    // a screen can take the remote over wholesale (Category order's move mode), whatever has focus
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::app.isInitialized) return super.dispatchKeyEvent(event)
        // the remote's Guide button opens the guide from anywhere once a playlist is loaded
        if (event.keyCode == KeyEvent.KEYCODE_GUIDE && app.account != null && "live" in app.parts &&
            app.screen != com.avetero.relay.app.Screen.Login && app.screen != com.avetero.relay.app.Screen.Sync) {
            if (event.action == KeyEvent.ACTION_UP && app.screen != com.avetero.relay.app.Screen.Guide) app.go(com.avetero.relay.app.Screen.Guide)
            return true
        }
        return app.keyOverride?.invoke(event) == true || super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (app.pipEligible) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        app.inPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        app.player.player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        app.player.player.release()
    }
}
