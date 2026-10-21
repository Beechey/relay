package com.avetero.relay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long, val date: String)

/** GitHub Releases updater. Repo and optional token come from gradle properties (see docs/06-github-updates.md). */
object Updater {
    val repo: String = BuildConfig.UPDATE_REPO
    private val token: String = BuildConfig.UPDATE_TOKEN
    val enabled get() = repo.isNotBlank()

    /** Newest release with an APK asset, or null. Blocking. */
    fun latest(beta: Boolean): Release? {
        val body = api(if (beta) "releases?per_page=10" else "releases/latest")
        val candidates = if (beta) JSONArray(body).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } else listOf(JSONObject(body))
        return candidates.filterNot { it.optBoolean("draft") }.firstNotNullOfOrNull(::parseRelease)
    }

    fun isNewer(r: Release) = compareVersions(r.version, BuildConfig.VERSION_NAME) > 0

    private fun api(path: String): String {
        val c = URL("https://api.github.com/repos/$repo/$path").openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (token.isNotBlank()) c.setRequestProperty("Authorization", "Bearer $token")
        try {
            if (c.responseCode !in 200..299) throw IOException("GitHub returned HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Downloads the APK into cache/updates; onProgress gets 0..1. Blocking. */
    fun download(ctx: Context, r: Release, onProgress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "update-${r.version}.apk")
        var c = URL(r.apkUrl).openConnection() as HttpURLConnection
        // private repos: asset API url + octet-stream accept, token required
        c.setRequestProperty("Accept", "application/octet-stream")
        if (token.isNotBlank()) c.setRequestProperty("Authorization", "Bearer $token")
        // GitHub redirects to a signed storage URL that rejects our Authorization header, so follow it by hand
        c.instanceFollowRedirects = false
        if (c.responseCode in 300..399) {
            val next = c.getHeaderField("Location")
            c.disconnect()
            c = URL(next).openConnection() as HttpURLConnection
        }
        try {
            if (c.responseCode !in 200..299) throw IOException("Download failed: HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        return out
    }

    /** Hands the APK to the system installer, or opens the "install unknown apps" screen first. */
    fun install(ctx: Context, apk: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

internal fun parseRelease(o: JSONObject): Release? {
    val assets = o.optJSONArray("assets") ?: return null
    val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
        .firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
    return Release(
        version = o.optString("tag_name").removePrefix("v"),
        notes = o.optString("body"),
        // api url works for private repos too (with token); public repos accept it as well
        apkUrl = apk.optString("url").ifEmpty { apk.optString("browser_download_url") },
        size = apk.optLong("size"),
        date = o.optString("published_at").take(10),
    )
}

/** Semver-ish compare: 1.10.0 > 1.9.0, and 1.0.0 > 1.0.0-beta.2. */
internal fun compareVersions(a: String, b: String): Int {
    fun split(v: String) = v.removePrefix("v").split('-', limit = 2).let { it[0] to it.getOrNull(1) }
    val (na, pa) = split(a)
    val (nb, pb) = split(b)
    val xa = na.split('.').map { it.toIntOrNull() ?: 0 }
    val xb = nb.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(xa.size, xb.size)) {
        val d = (xa.getOrElse(i) { 0 }).compareTo(xb.getOrElse(i) { 0 })
        if (d != 0) return d
    }
    return when {
        pa == pb -> 0
        pa == null -> 1
        pb == null -> -1
        else -> pa.compareTo(pb)
    }
}
