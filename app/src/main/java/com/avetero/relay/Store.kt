package com.avetero.relay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A "continue watching" entry. key is m<vodId> or e<episodeId>. */
data class Resume(
    val key: String, val title: String, val meta: String, val image: String,
    val pos: Long, val dur: Long, val vodId: Int = 0, val seriesId: Int = 0,
)

/**
 * SharedPreferences + raw JSON cache files.
 * shortcut: password in plain app-private prefs (as TiviMate does); move to Keystore if we store anything more sensitive.
 */
class Store(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    // ---- playlists ----
    fun playlists(): List<Account> = JSONArray(prefs.getString("playlists", "[]")).let { a ->
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            Account(it.getString("name"), it.getString("server"), it.getString("user"), it.getString("pass"))
        }
    }

    private fun savePlaylists(list: List<Account>) = prefs.edit().putString("playlists", JSONArray(list.map {
        JSONObject().put("name", it.name).put("server", it.server).put("user", it.user).put("pass", it.pass)
    }).toString()).apply()

    var activeIndex: Int
        get() = prefs.getInt("active", 0)
        set(v) = prefs.edit().putInt("active", v).apply()

    fun active(): Account? = playlists().getOrNull(activeIndex)

    fun addPlaylist(a: Account) {
        val list = playlists().filterNot { it.server == a.server && it.user == a.user } + a
        savePlaylists(list)
        activeIndex = list.lastIndex
    }

    fun removePlaylist(a: Account) {
        savePlaylists(playlists() - a)
        cacheDir(a).deleteRecursively()
        activeIndex = 0
    }

    // ---- catalog cache (raw API responses) ----
    private fun cacheDir(a: Account) = File(ctx.filesDir, "cache_" + (a.server + a.user).hashCode().toUInt())
    fun readCache(a: Account, name: String): String? = File(cacheDir(a), "$name.json").takeIf { it.exists() }?.readText()
    fun writeCache(a: Account, name: String, json: String) = cacheDir(a).run { mkdirs(); File(this, "$name.json").writeText(json) }
    fun cacheTime(a: Account): Long = File(cacheDir(a), "live.json").lastModified()

    // ---- per-playlist lists ----
    private fun key(a: Account?, k: String) = "$k:" + (a?.server + a?.user).hashCode()

    fun favourites(a: Account?): List<String> =
        prefs.getString(key(a, "favs"), "")!!.split(',').filter { it.isNotEmpty() }

    fun setFavourites(a: Account?, list: List<String>) = prefs.edit().putString(key(a, "favs"), list.joinToString(",")).apply()

    fun recentChannels(a: Account?): List<Int> =
        prefs.getString(key(a, "recent"), "")!!.split(',').mapNotNull { it.toIntOrNull() }

    fun addRecent(a: Account?, id: Int) = prefs.edit()
        .putString(key(a, "recent"), (listOf(id) + recentChannels(a).filter { it != id }).take(12).joinToString(",")).apply()

    fun resumes(a: Account?): List<Resume> = JSONArray(prefs.getString(key(a, "resume"), "[]")).let { arr ->
        (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            Resume(it.getString("key"), it.getString("title"), it.getString("meta"), it.getString("image"),
                it.getLong("pos"), it.getLong("dur"), it.optInt("vodId"), it.optInt("seriesId"))
        }
    }

    fun resumeAt(a: Account?, key: String): Long = resumes(a).firstOrNull { it.key == key }?.pos ?: 0

    /** Saves progress; past 95% the item counts as watched and leaves Continue watching. */
    fun saveResume(a: Account?, r: Resume) {
        val watched = r.dur > 0 && r.pos > r.dur * 0.95
        val rest = resumes(a).filter { it.key != r.key }
        val list = (if (watched || r.pos < 10_000) rest else listOf(r) + rest).take(30)
        if (watched) prefs.edit().putBoolean(key(a, "w" + r.key), true).apply()
        prefs.edit().putString(key(a, "resume"), JSONArray(list.map {
            JSONObject().put("key", it.key).put("title", it.title).put("meta", it.meta).put("image", it.image)
                .put("pos", it.pos).put("dur", it.dur).put("vodId", it.vodId).put("seriesId", it.seriesId)
        }).toString()).apply()
    }

    fun watched(a: Account?, key: String) = prefs.getBoolean(key(a, "w$key"), false)

    // ---- settings ----
    var hls: Boolean
        get() = prefs.getBoolean("hls", false)
        set(v) = prefs.edit().putBoolean("hls", v).apply()
    var buffer: String
        get() = prefs.getString("buffer", "Medium")!!
        set(v) = prefs.edit().putString("buffer", v).apply()
    var autoplayNext: Boolean
        get() = prefs.getBoolean("autoplay", true)
        set(v) = prefs.edit().putBoolean("autoplay", v).apply()
    var subtitlesOn: Boolean
        get() = prefs.getBoolean("subs", false)
        set(v) = prefs.edit().putBoolean("subs", v).apply()
    var refreshOnStart: Boolean
        get() = prefs.getBoolean("refreshOnStart", true)
        set(v) = prefs.edit().putBoolean("refreshOnStart", v).apply()
    var checkUpdates: Boolean
        get() = prefs.getBoolean("checkUpdates", true)
        set(v) = prefs.edit().putBoolean("checkUpdates", v).apply()
    var betaUpdates: Boolean
        get() = prefs.getBoolean("beta", false)
        set(v) = prefs.edit().putBoolean("beta", v).apply()
    var skippedVersion: String
        get() = prefs.getString("skipped", "")!!
        set(v) = prefs.edit().putString("skipped", v).apply()
}
