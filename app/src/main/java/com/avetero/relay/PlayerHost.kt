package com.avetero.relay

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** The one ExoPlayer; screens attach a PlayerSurface to show it. Buffer setting applies on next app start. */
@OptIn(UnstableApi::class)
class PlayerHost(ctx: Context, private val store: Store) {
    private val bufferMs = when (store.buffer) { "Small" -> 15_000; "Large" -> 60_000; else -> 30_000 }

    // the FFmpeg extension decodes Dolby (AC-3/E-AC-3), DTS and TrueHD where the device has no decoder for them
    val player: ExoPlayer = ExoPlayer.Builder(ctx, DefaultRenderersFactory(ctx)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON).setEnableDecoderFallback(true))
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx,
            DefaultHttpDataSource.Factory().setUserAgent("IPTVSmartersPlayer").setAllowCrossProtocolRedirects(true))))
        .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(minOf(15_000, bufferMs), bufferMs, 1_500, 3_000).build())
        .build()

    var url by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    /** A stream is loaded and meant to be playing (true through buffering pauses). */
    var active by mutableStateOf(false)
        private set
    var tracks by mutableStateOf(Tracks.EMPTY)
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error = explain(e) }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { active = playWhenReady && url != null }
            override fun onTracksChanged(t: Tracks) { tracks = t }
        })
    }

    fun play(url: String, startMs: Long = 0, subtitles: Boolean = false) {
        if (url == this.url && error == null) return
        this.url = url
        error = null
        // fresh choices per video: preferred languages from Settings, no leftover overrides from the last one
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setPreferredAudioLanguage(store.audioLang.ifEmpty { null })
            .setPreferredTextLanguage(store.subLang.ifEmpty { null })
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitles).build()
        // release the decoders first: reusing one across a resolution change leaves the old picture showing around the new one
        player.stop()
        player.setMediaItem(MediaItem.fromUri(url), startMs)
        player.prepare()
        player.playWhenReady = true
        active = true
    }

    /** A sentence the viewer can act on, rather than ExoPlayer's error code. */
    private fun explain(e: PlaybackException): String {
        val f = (e as? androidx.media3.exoplayer.ExoPlaybackException)?.takeIf { it.type == androidx.media3.exoplayer.ExoPlaybackException.TYPE_RENDERER }?.rendererFormat
        if (f != null && f.sampleMimeType?.startsWith("video/") == true) {
            val codec = when {
                f.sampleMimeType == androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
                f.sampleMimeType == androidx.media3.common.MimeTypes.VIDEO_H265 -> "HEVC"
                else -> f.sampleMimeType!!.removePrefix("video/").uppercase()
            }
            val size = if (f.height >= 2000) "4K " else if (f.height > 0) "${f.height}p " else ""
            return "This device can't decode this $size$codec video. Try the HD version of the channel."
        }
        return when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                "The channel didn't respond. It may be off air (event channels only stream during the event)."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "Your provider refused this stream. It may be offline, or your connection limit is reached."
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "The stream fell behind. Retry to jump back to live."
            else -> "The stream stopped or the format isn't supported (" + e.errorCodeName.removePrefix("ERROR_CODE_").replace('_', ' ').lowercase() + ")."
        }
    }

    fun retry() = url?.let { u -> url = null; play(u) }

    fun stop() {
        player.stop()
        url = null
        active = false
    }

    data class TrackOption(val label: String, val selected: Boolean, val pick: () -> Unit)

    /** Choices for one track type in the playing stream: video quality, audio or subtitles. Empty when there's nothing to choose. */
    fun options(type: Int): List<TrackOption> {
        val params = player.trackSelectionParameters
        val groups = tracks.groups.filter { it.type == type }
        val overridden = groups.any { params.overrides.containsKey(it.mediaTrackGroup) }
        val out = ArrayList<TrackOption>()
        fun select(g: Tracks.Group, i: Int) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(type, false).clearOverridesOfType(type)
                .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
        }
        when (type) {
            C.TRACK_TYPE_VIDEO -> {
                out += TrackOption("Auto", !overridden) { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type).build() }
                groups.forEach { g ->
                    (0 until g.length).filter { g.isTrackSupported(it) }.sortedByDescending { g.getTrackFormat(it).height }.forEach { i ->
                        val f = g.getTrackFormat(i)
                        val label = listOfNotNull(f.height.takeIf { it > 0 }?.let { "${it}p" }, f.bitrate.takeIf { it > 0 }?.let { "%.1f Mbps".format(it / 1e6) }).joinToString(" · ").ifEmpty { "Track ${i + 1}" }
                        out += TrackOption(label, overridden && g.isTrackSelected(i)) { select(g, i) }
                    }
                }
                if (out.size <= 2) return emptyList() // a single quality: nothing to choose
            }
            else -> {
                val textOff = type == C.TRACK_TYPE_TEXT && params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                if (type == C.TRACK_TYPE_TEXT) out += TrackOption("Off", textOff || groups.none { it.isSelected }) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type, true).build()
                }
                groups.forEach { g ->
                    (0 until g.length).filter { g.isTrackSupported(it) }.forEach { i ->
                        val f = g.getTrackFormat(i)
                        val lang = f.language?.let { java.util.Locale.forLanguageTag(it).displayLanguage.ifEmpty { it } }
                        val extra = if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) when (f.channelCount) { 1 -> "mono"; 2 -> "stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "${f.channelCount} ch" } else null
                        val label = listOfNotNull(f.label ?: lang, extra).joinToString(" · ").ifEmpty { "Track ${out.size + 1}" }
                        out += TrackOption(label, !textOff && g.isTrackSelected(i)) { select(g, i) }
                    }
                }
            }
        }
        return out
    }

    /** Cycles to the next track of a type (audio/text); for text, "off" is part of the cycle. Returns its label. */
    fun cycleTrack(type: Int): String {
        val groups = tracks.groups.filter { it.type == type && it.isSupported }
        val current = groups.indexOfFirst { it.isSelected }
        val textOff = type == C.TRACK_TYPE_TEXT && player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val next = if (textOff) 0 else current + 1
        val b = player.trackSelectionParameters.buildUpon()
        if (next >= groups.size) {
            if (type == C.TRACK_TYPE_TEXT) { player.trackSelectionParameters = b.setTrackTypeDisabled(type, true).build(); return "Off" }
            if (groups.isEmpty()) return "Default"
        }
        val g = groups[next % groups.size]
        player.trackSelectionParameters = b.setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
        return g.getTrackFormat(0).let { it.label ?: it.language ?: "Track ${next + 1}" }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(player: Player, modifier: Modifier = Modifier, zoom: Boolean = false) = AndroidView(
    factory = {
        PlayerView(it).apply {
            useController = false
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setShutterBackgroundColor(android.graphics.Color.BLACK)
            this.player = player
        }
    },
    update = { it.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
    onRelease = { it.player = null },
    modifier = modifier,
)

/**
 * While a player is on screen: hide status/navigation bars (swipe from an edge brings them back), draw under the notch,
 * keep the screen awake, and turn phones to landscape. Everything is restored when the player closes.
 */
@Composable
fun FullscreenVideo(inPip: Boolean = false) {
    val activity = LocalContext.current as? Activity ?: return
    val phone = com.avetero.relay.ui.compact()
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
