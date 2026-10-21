package com.avetero.relay.data.xtream

import com.avetero.relay.data.model.Category
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.Episode
import com.avetero.relay.data.model.Programme
import com.avetero.relay.data.model.Series
import com.avetero.relay.data.model.SeriesInfo
import com.avetero.relay.data.model.Vod
import com.avetero.relay.data.model.VodInfo
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Reader
import java.io.StringReader
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal fun <T> readObjects(r: Reader, onCount: (Int) -> Unit = {}, item: (Map<String, String>) -> T?): List<T> = JsonReader(r).use { j ->
    if (j.peek() != JsonToken.BEGIN_ARRAY) return emptyList()
    val out = ArrayList<T>()
    val fields = HashMap<String, String>()
    j.beginArray()
    while (j.hasNext()) {
        if (j.peek() != JsonToken.BEGIN_OBJECT) { j.skipValue(); continue }
        fields.clear()
        j.beginObject()
        while (j.hasNext()) {
            val name = j.nextName()
            when (j.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> fields[name] = j.nextString().trim()
                JsonToken.BOOLEAN -> fields[name] = j.nextBoolean().toString()
                else -> j.skipValue()
            }
        }
        j.endObject()
        item(fields)?.let(out::add)
        if (out.size % 250 == 0) onCount(out.size)
    }
    j.endArray()
    onCount(out.size)
    out
}

private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }

// org.json returns "null" for JSON null
internal fun JSONObject.str(key: String) = if (isNull(key)) "" else optString(key).trim()

internal fun parseCategories(json: String): List<Category> =
    JSONArray(json).objects().map { Category(it.str("category_id"), it.str("category_name")) }

internal fun parseChannels(json: String) = parseChannels(StringReader(json))

internal fun parseChannels(r: Reader, onCount: (Int) -> Unit = {}): List<Channel> = readObjects(r, onCount) {
    Channel(
        id = it["stream_id"]?.toIntOrNull() ?: return@readObjects null,
        num = it["num"]?.toIntOrNull() ?: 0,
        name = it["name"].orEmpty(),
        categoryId = it["category_id"].orEmpty(),
        icon = it["stream_icon"].orEmpty(),
        archive = it["tv_archive"] == "1",
        epgId = it["epg_channel_id"].orEmpty(),
    )
}

internal fun parseVods(json: String) = parseVods(StringReader(json))

internal fun parseVods(r: Reader, onCount: (Int) -> Unit = {}): List<Vod> = readObjects(r, onCount) {
    Vod(
        id = it["stream_id"]?.toIntOrNull() ?: return@readObjects null,
        name = it["name"].orEmpty(),
        icon = it["stream_icon"].orEmpty(),
        rating = it["rating"]?.toDoubleOrNull() ?: 0.0,
        categoryId = it["category_id"].orEmpty(),
        ext = it["container_extension"].orEmpty().ifEmpty { "mp4" },
        added = it["added"]?.toLongOrNull() ?: 0,
    )
}

internal fun parseSeries(json: String) = parseSeries(StringReader(json))

internal fun parseSeries(r: Reader, onCount: (Int) -> Unit = {}): List<Series> = readObjects(r, onCount) {
    Series(
        id = it["series_id"]?.toIntOrNull() ?: return@readObjects null,
        name = it["name"].orEmpty(),
        cover = it["cover"].orEmpty(),
        rating = it["rating"]?.toDoubleOrNull() ?: 0.0,
        categoryId = it["category_id"].orEmpty(),
        plot = "", // shortcut: plot dropped from the list to save memory on huge catalogues; detail page loads it
        year = it["releaseDate"].orEmpty().take(4),
    )
}

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
