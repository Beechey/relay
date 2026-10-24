package com.avetero.relay.player

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.avetero.relay.data.store.Store
import com.google.android.gms.cast.framework.CastContext
import java.util.Locale

/**
 * The one ExoPlayer; screens attach a PlayerSurface to show it. Buffer setting applies on next app start.
 * When a Chromecast session starts, playback moves to the CastPlayer and back again when it ends.
 */
@OptIn(UnstableApi::class)
class PlayerHost(private val ctx: Context, private val store: Store) {
    private val bufferMs = when (store.buffer) { "Small" -> 15_000; "Large" -> 60_000; else -> 30_000 }

    // the FFmpeg extension decodes Dolby (AC-3/E-AC-3), DTS and TrueHD where the device has no decoder for them
    val player: ExoPlayer = ExoPlayer.Builder(ctx, DefaultRenderersFactory(ctx)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON).setEnableDecoderFallback(true))
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx,
            DefaultHttpDataSource.Factory().setUserAgent(UA).setAllowCrossProtocolRedirects(true))))
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
    var muted by mutableStateOf(false)
        private set
    fun toggleMute() { muted = !muted; player.volume = if (muted) 0f else 1f }
    var tracks by mutableStateOf(Tracks.EMPTY)
        private set

    // Chromecast needs Google Play services, so it's null on Fire TV; TVs don't cast, they're cast to
    private val cast: CastPlayer? = if (ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) null
        else runCatching { CastPlayer(CastContext.getSharedInstance(ctx)) }.getOrNull()
    val canCast get() = cast != null
    /** Name of the Chromecast playing right now, or null when playing on this device. */
    var castDevice by mutableStateOf<String?>(null)
        private set
    private var title: String? = null
    /** What the TV is waiting for while a live channel is being prepared for it, or null. */
    var castStatus by mutableStateOf<String?>(null)
        private set
    private val relay = CastRelay()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    /** The player the controls drive: the Chromecast while casting, otherwise this device. */
    val current: Player get() = cast?.takeIf { castDevice != null } ?: player

    init {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error = explain(e) }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { active = playWhenReady && url != null }
            override fun onTracksChanged(t: Tracks) { tracks = t }
        }
        player.addListener(listener)
        cast?.addListener(listener)
        cast?.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                castDevice = CastContext.getSharedInstance()?.sessionManager?.currentCastSession?.castDevice?.friendlyName ?: "Chromecast"
                val at = if (player.isCurrentMediaItemLive) 0 else player.currentPosition
                // stop, not pause: providers often allow one connection, and the TV's stream needs it
                player.stop()
                url?.let { load(cast, it, at) }
            }
            override fun onCastSessionUnavailable() {
                val at = if (cast.isCurrentMediaItemLive) 0 else cast.currentPosition
                stopRelay()
                castDevice = null; castStatus = null
                url?.let { player.setMediaItem(MediaItem.fromUri(it), at); player.prepare(); player.play() }
            }
        })
    }

    /**
     * Sends a stream to the Chromecast. Live channels (raw MPEG-TS) go through the phone's CastRelay, which turns them
     * into HLS the receiver accepts; movies and episodes go straight to the TV.
     */
    private fun load(cast: CastPlayer, url: String, startMs: Long) {
        stopRelay()
        if ("/live/" in url) {
            castStatus = "Getting the channel ready for your TV…"
            // the relay reads the raw stream even when live TV is set to HLS (the provider's HLS only wraps it)
            CastRelayService.start(ctx, title)
            relay.start(url.removeSuffix(".m3u8").removeSuffix(".ts") + ".ts", UA, onReady = { local -> main.post {
                if (this.url != url || castDevice == null) return@post
                castStatus = null
                send(cast, local, MimeTypes.APPLICATION_M3U8, C.TIME_UNSET)
            } }, onError = { reason -> main.post { if (this.url == url) { stopRelay(); castStatus = null; error = reason } } })
            return
        }
        val mime = when (url.substringAfterLast('.').lowercase()) {
            "m3u8" -> MimeTypes.APPLICATION_M3U8
            "mkv" -> MimeTypes.VIDEO_MATROSKA
            "webm" -> MimeTypes.VIDEO_WEBM
            else -> MimeTypes.VIDEO_MP4
        }
        send(cast, url, mime, startMs)
    }

    private fun stopRelay() { relay.stop(); CastRelayService.stop(ctx) }

    private fun send(cast: CastPlayer, url: String, mime: String, startMs: Long) {
        cast.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(mime)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build(), startMs)
        cast.prepare()
        cast.playWhenReady = true
    }

    fun play(url: String, startMs: Long = 0, subtitles: Boolean = false, title: String? = null) {
        if (url == this.url && error == null) return
        this.url = url
        this.title = title
        error = null
        if (castDevice != null && cast != null) { player.stop(); load(cast, url, startMs); active = true; return }
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
        val f = (e as? ExoPlaybackException)?.takeIf { it.type == ExoPlaybackException.TYPE_RENDERER }?.rendererFormat
        if (f != null && f.sampleMimeType?.startsWith("video/") == true) {
            val codec = when {
                f.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
                f.sampleMimeType == MimeTypes.VIDEO_H265 -> "HEVC"
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
        stopRelay(); castStatus = null
        if (castDevice != null) cast?.stop()
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
                        val lang = f.language?.let { Locale.forLanguageTag(it).displayLanguage.ifEmpty { it } }
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

    private companion object {
        /** Some panels reject unknown user agents. */
        const val UA = "IPTVSmartersPlayer"
    }
}
