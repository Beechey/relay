package com.avetero.relay

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

data class Account(val name: String, val server: String, val user: String, val pass: String)
data class AccountInfo(val status: String, val expires: Long?, val maxConnections: Int, val activeConnections: Int)
data class Category(val id: String, val name: String)
data class Channel(val id: Int, val num: Int, val name: String, val categoryId: String, val icon: String = "", val archive: Boolean = false)
data class Vod(val id: Int, val name: String, val icon: String, val rating: Double, val categoryId: String, val ext: String, val added: Long)
data class Series(val id: Int, val name: String, val cover: String, val rating: Double, val categoryId: String, val plot: String, val year: String)
data class VodInfo(val plot: String, val cast: String, val director: String, val genre: String, val year: String, val duration: String, val backdrop: String)
data class Episode(val id: Int, val season: Int, val num: Int, val title: String, val ext: String, val plot: String, val duration: String, val image: String)
data class SeriesInfo(val plot: String, val genre: String, val year: String, val cover: String, val episodes: List<Episode>)

/** start/end are epoch seconds. */
data class Programme(val title: String, val description: String, val start: Long, val end: Long)

/** Xtream Codes player_api.php client. Blocking: call from Dispatchers.IO. */
class Xtream(private val account: Account) {
    private val base = normaliseServer(account.server)
    private val creds = "${enc(account.user)}/${enc(account.pass)}"

    fun apiUrl(action: String? = null, extra: String = "") =
        "$base/player_api.php?username=${enc(account.user)}&password=${enc(account.pass)}" +
            (action?.let { "&action=$it" } ?: "") + extra

    fun streamUrl(c: Channel, hls: Boolean = false) = "$base/live/$creds/${c.id}.${if (hls) "m3u8" else "ts"}"
    fun vodUrl(v: Vod) = "$base/movie/$creds/${v.id}.${v.ext}"
    fun episodeUrl(e: Episode) = "$base/series/$creds/${e.id}.${e.ext}"

    fun login(): AccountInfo {
        val info = runCatching { JSONObject(get(apiUrl())).optJSONObject("user_info") }.getOrNull()
            ?: throw IOException("That doesn't look like an Xtream server")
        if (info.optInt("auth") != 1) throw IOException("Wrong username or password")
        val status = info.optString("status", "Active")
        if (!status.equals("Active", ignoreCase = true)) throw IOException("Account is $status")
        return AccountInfo(
            status = status,
            expires = info.optString("exp_date").toLongOrNull(),
            maxConnections = info.optInt("max_connections"),
            activeConnections = info.optInt("active_cons"),
        )
    }

    fun liveCategories() = parseCategories(get(apiUrl("get_live_categories")))
    fun channelsJson() = get(apiUrl("get_live_streams"))
    fun vodCategories() = parseCategories(get(apiUrl("get_vod_categories")))
    fun vodJson() = get(apiUrl("get_vod_streams"))
    fun seriesCategories() = parseCategories(get(apiUrl("get_series_categories")))
    fun seriesJson() = get(apiUrl("get_series"))
    fun vodInfo(id: Int) = parseVodInfo(get(apiUrl("get_vod_info", "&vod_id=$id")))
    fun seriesInfo(id: Int) = parseSeriesInfo(get(apiUrl("get_series_info", "&series_id=$id")))
    fun shortEpg(streamId: Int, limit: Int = 8) =
        parseEpg(get(apiUrl("get_short_epg", "&stream_id=$streamId&limit=$limit")))
}

internal fun normaliseServer(s: String) = s.trim().trimEnd('/').let { if ("://" in it) it else "http://$it" }

internal fun get(url: String, ua: String = "IPTVSmartersPlayer"): String = (URL(url).openConnection() as HttpURLConnection).run {
    connectTimeout = 15_000
    readTimeout = 60_000
    // some panels reject unknown user agents
    setRequestProperty("User-Agent", ua)
    try {
        if (responseCode !in 200..299) throw IOException("Server returned HTTP $responseCode")
        inputStream.bufferedReader().use { it.readText() }
    } finally {
        disconnect()
    }
}

// URLEncoder is form encoding; %20 keeps it valid in paths too
internal fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }

// org.json returns "null" for JSON null
private fun JSONObject.str(key: String) = if (isNull(key)) "" else optString(key).trim()

internal fun parseCategories(json: String): List<Category> =
    JSONArray(json).objects().map { Category(it.str("category_id"), it.str("category_name")) }

internal fun parseChannels(json: String): List<Channel> = JSONArray(json).objects().map {
    Channel(
        id = it.optInt("stream_id"),
        num = it.optInt("num"),
        name = it.str("name"),
        categoryId = it.str("category_id"),
        icon = it.str("stream_icon"),
        archive = it.optInt("tv_archive") == 1,
    )
}.filter { it.id != 0 }

internal fun parseVods(json: String): List<Vod> = JSONArray(json).objects().map {
    Vod(
        id = it.optInt("stream_id"),
        name = it.str("name"),
        icon = it.str("stream_icon"),
        rating = it.str("rating").toDoubleOrNull() ?: 0.0,
        categoryId = it.str("category_id"),
        ext = it.str("container_extension").ifEmpty { "mp4" },
        added = it.str("added").toLongOrNull() ?: 0,
    )
}.filter { it.id != 0 }

internal fun parseSeries(json: String): List<Series> = JSONArray(json).objects().map {
    Series(
        id = it.optInt("series_id"),
        name = it.str("name"),
        cover = it.str("cover"),
        rating = it.str("rating").toDoubleOrNull() ?: 0.0,
        categoryId = it.str("category_id"),
        plot = it.str("plot"),
        year = it.str("releaseDate").take(4),
    )
}.filter { it.id != 0 }

internal fun parseVodInfo(json: String): VodInfo {
    val i = JSONObject(json).optJSONObject("info") ?: JSONObject()
    val backdrop = i.optJSONArray("backdrop_path")?.optString(0) ?: i.str("backdrop_path")
    return VodInfo(
        plot = i.str("plot").ifEmpty { i.str("description") },
        cast = i.str("cast").ifEmpty { i.str("actors") },
        director = i.str("director"),
        genre = i.str("genre"),
        year = i.str("releasedate").take(4),
        duration = i.str("duration"),
        backdrop = backdrop,
    )
}

internal fun parseSeriesInfo(json: String): SeriesInfo {
    val root = JSONObject(json)
    val info = root.optJSONObject("info") ?: JSONObject()
    // episodes is {"1":[...],"2":[...]} on most panels, a list of lists on some
    val groups = when (val e = root.opt("episodes")) {
        is JSONObject -> e.keys().asSequence().mapNotNull { e.optJSONArray(it) }.toList()
        is JSONArray -> (0 until e.length()).mapNotNull { e.optJSONArray(it) }
        else -> emptyList()
    }
    val episodes = groups.flatMap { it.objects() }.map {
        val ei = it.optJSONObject("info") ?: JSONObject()
        Episode(
            id = it.str("id").toIntOrNull() ?: 0,
            season = it.optInt("season"),
            num = it.optInt("episode_num"),
            title = it.str("title"),
            ext = it.str("container_extension").ifEmpty { "mp4" },
            plot = ei.str("plot"),
            duration = ei.str("duration"),
            image = ei.str("movie_image"),
        )
    }.filter { it.id != 0 }.sortedWith(compareBy({ it.season }, { it.num }))
    return SeriesInfo(info.str("plot"), info.str("genre"), info.str("releaseDate").take(4), info.str("cover"), episodes)
}

internal fun parseEpg(json: String): List<Programme> {
    val a = JSONObject(json).optJSONArray("epg_listings") ?: return emptyList()
    return a.objects().map {
        Programme(
            title = b64(it.optString("title")),
            description = b64(it.optString("description")),
            start = it.optString("start_timestamp").toLongOrNull() ?: 0,
            end = it.optString("stop_timestamp").toLongOrNull() ?: 0,
        )
    }.filter { it.end > it.start }.sortedBy { it.start }
}

// Xtream sends EPG text base64-encoded; a few panels don't
private fun b64(s: String): String =
    runCatching { String(Base64.getDecoder().decode(s), Charsets.UTF_8) }.getOrDefault(s)
