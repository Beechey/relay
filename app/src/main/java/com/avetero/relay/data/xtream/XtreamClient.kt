package com.avetero.relay.data.xtream

import com.avetero.relay.data.epg.Guide
import com.avetero.relay.data.epg.parseXmltv
import com.avetero.relay.data.model.Account
import com.avetero.relay.data.model.AccountInfo
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.Episode
import com.avetero.relay.data.model.Vod
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import org.json.JSONObject

/** Xtream Codes player_api.php client. Blocking: call from Dispatchers.IO. */
class Xtream(private val account: Account) {
    private val base = normaliseServer(account.server)
    private val creds = "${enc(account.user)}/${enc(account.pass)}"

    fun apiUrl(action: String? = null, extra: String = "") =
        "$base/player_api.php?username=${enc(account.user)}&password=${enc(account.pass)}" +
            (action?.let { "&action=$it" } ?: "") + extra

    fun streamUrl(c: Channel, hls: Boolean = false) = "$base/live/$creds/${c.id}.${if (hls) "m3u8" else "ts"}"
    /**
     * Catch-up (timeshift) for a past or running programme on a channel with an archive: the provider wants the
     * start in its own time zone as yyyy-MM-dd:HH-mm and the length in minutes.
     */
    fun catchupUrl(c: Channel, startSec: Long, minutes: Int, timezone: String): String {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone(timezone) }
        return "$base/timeshift/$creds/$minutes/${f.format(java.util.Date(startSec * 1000))}/${c.id}.ts"
    }
    fun vodUrl(v: Vod) = "$base/movie/$creds/${v.id}.${v.ext}"
    fun episodeUrl(e: Episode) = "$base/series/$creds/${e.id}.${e.ext}"

    fun login(): AccountInfo {
        val root = runCatching { JSONObject(fetchText(apiUrl())) }.getOrNull()
        val info = root?.optJSONObject("user_info") ?: throw IOException("That doesn't look like an Xtream server")
        if (info.optInt("auth") != 1) throw IOException("Wrong username or password")
        val status = info.optString("status", "Active")
        if (!status.equals("Active", ignoreCase = true)) throw IOException("Account is $status")
        return AccountInfo(
            status = status,
            expires = info.optString("exp_date").toLongOrNull(),
            maxConnections = info.optInt("max_connections"),
            activeConnections = info.optInt("active_cons"),
            timezone = root.optJSONObject("server_info")?.optString("timezone")?.ifBlank { null } ?: "UTC",
        )
    }

    fun liveCategories() = parseCategories(fetchText(apiUrl("get_live_categories")))
    // catalogue lists can be hundreds of MB: stream them to disk, never into a String
    fun downloadChannels(to: File, progress: (Long, Long) -> Unit) = download(apiUrl("get_live_streams"), to, progress)
    fun vodCategories() = parseCategories(fetchText(apiUrl("get_vod_categories")))
    fun downloadVods(to: File, progress: (Long, Long) -> Unit) = download(apiUrl("get_vod_streams"), to, progress)
    fun seriesCategories() = parseCategories(fetchText(apiUrl("get_series_categories")))
    fun downloadSeries(to: File, progress: (Long, Long) -> Unit) = download(apiUrl("get_series"), to, progress)
    fun vodInfo(id: Int) = parseVodInfo(fetchText(apiUrl("get_vod_info", "&vod_id=$id")))
    fun seriesInfo(id: Int) = parseSeriesInfo(fetchText(apiUrl("get_series_info", "&series_id=$id")))
    /** Full guide (XMLTV), stream-parsed straight off the network; only [wanted] channels in [from]..[to] are kept. */
    fun guide(wanted: Set<String>, from: Long, to: Long, progress: ((Long, Long) -> Unit)? = null, onCount: (Int) -> Unit = {}): Guide =
        request("$base/xmltv.php?username=${enc(account.user)}&password=${enc(account.pass)}", progress = progress) { parseXmltv(it, wanted, from, to, onCount) }

    fun shortEpg(streamId: Int, limit: Int = 8) =
        parseEpg(fetchText(apiUrl("get_short_epg", "&stream_id=$streamId&limit=$limit")))
}

internal fun normaliseServer(s: String) = s.trim().trimEnd('/').let { if ("://" in it) it else "http://$it" }

// URLEncoder is form encoding; %20 keeps it valid in paths too
internal fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
