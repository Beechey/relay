package com.avetero.relay.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Keeps the phone working at full speed while it relays a live channel to the Chromecast: without it Android slows
 * Wi-Fi and background work when the screen goes off, and the TV picture stutters.
 */
class CastRelayService : Service() {
    private var wifi: WifiManager.WifiLock? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Casting", NotificationManager.IMPORTANCE_LOW))
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Casting to your TV")
            .setContentText(intent?.getStringExtra(EXTRA_TITLE) ?: "Live TV")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(1, n)
        if (wifi == null) {
            @Suppress("DEPRECATION") val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifi = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(mode, "relay:cast").apply { acquire() }
            wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "relay:cast").apply { acquire() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wifi?.takeIf { it.isHeld }?.release()
        wake?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "casting"
        private const val EXTRA_TITLE = "title"
        fun start(ctx: Context, title: String?) {
            val i = Intent(ctx, CastRelayService::class.java).putExtra(EXTRA_TITLE, title)
            runCatching { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i) }
        }
        fun stop(ctx: Context) { runCatching { ctx.stopService(Intent(ctx, CastRelayService::class.java)) } }
    }
}
