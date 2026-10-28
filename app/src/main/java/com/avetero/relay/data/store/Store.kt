package com.avetero.relay.data.store

import android.content.Context
import com.avetero.relay.data.model.Account
import com.avetero.relay.data.model.Resume
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

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
    fun cacheFile(a: Account, name: String) = File(cacheDir(a).apply { mkdirs() }, "$name.json")
    fun cacheTime(a: Account): Long = File(cacheDir(a), "live.json").lastModified()

    // ---- per-playlist lists ----
    private fun key(a: Account?, k: String) = "$k:" + (a?.server + a?.user).hashCode()

    fun favourites(a: Account?): List<String> =
        prefs.getString(key(a, "favs"), "")!!.split(',').filter { it.isNotEmpty() }

    fun setFavourites(a: Account?, list: List<String>) = prefs.edit().putString(key(a, "favs"), list.joinToString(",")).apply()

    /** Hidden categories, keyed "live:<id>", "vod:<id>" or "series:<id>". */
    fun hidden(a: Account?): Set<String> = prefs.getStringSet(key(a, "hidden"), emptySet())!!.toSet()
    fun setHidden(a: Account?, set: Set<String>) = prefs.edit().putStringSet(key(a, "hidden"), set).apply()

    /** Custom category order for "live", "vod" or "series" (category ids, first to last); empty = provider order. */
    fun order(a: Account?, type: String): List<String> =
        JSONArray(prefs.getString(key(a, "order_$type"), "[]")).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
    fun setOrder(a: Account?, type: String, ids: List<String>) = prefs.edit().putString(key(a, "order_$type"), JSONArray(ids).toString()).apply()

    /** Recently played ids, newest first. kind is "recent" (channels), "rmovie" or "rseries". */
    /** Programmes to be reminded about, as "channelId|startSec|title". */
    fun reminders(a: Account?): Set<String> = prefs.getStringSet(key(a, "reminders"), emptySet())!!.toSet()
    fun setReminders(a: Account?, set: Set<String>) = prefs.edit().putStringSet(key(a, "reminders"), set).apply()

    // per device: what this person has searched for, newest first
    fun recentSearches(): List<String> = prefs.getString("recentSearches", "")!!.split('\n').filter { it.isNotBlank() }
    fun addRecentSearch(q: String) = prefs.edit().putString("recentSearches", (listOf(q) + recentSearches().filterNot { it.equals(q, true) }).take(8).joinToString("\n")).apply()

    fun recents(a: Account?, kind: String): List<Int> =
        prefs.getString(key(a, kind), "")!!.split(',').mapNotNull { it.toIntOrNull() }

    fun addRecent(a: Account?, kind: String, id: Int) = prefs.edit()
        .putString(key(a, kind), (listOf(id) + recents(a, kind).filter { it != id }).take(20).joinToString(",")).apply()

    fun resumes(a: Account?): List<Resume> = JSONArray(prefs.getString(key(a, "resume"), "[]")).let { arr ->
        (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            Resume(it.getString("key"), it.getString("title"), it.getString("meta"), it.getString("image"),
                it.getLong("pos"), it.getLong("dur"), it.optInt("vodId"), it.optInt("seriesId"))
        }
    }

    fun resumeAt(a: Account?, key: String): Long = resumes(a).firstOrNull { it.key == key }?.pos ?: 0

    /** Saves progress; past 95% the item counts as watched and leaves Continue watching. */
    /**
     * Saves progress; past 95% the item counts as watched and leaves Continue watching.
     * A series keeps one entry (its latest episode); upNext keeps an unstarted next episode there.
     */
    fun saveResume(a: Account?, r: Resume, upNext: Boolean = false) {
        val watched = r.dur > 0 && r.pos > r.dur * 0.95
        val rest = resumes(a).filter { it.key != r.key && (r.seriesId == 0 || it.seriesId != r.seriesId) }
        val keep = upNext || (!watched && r.pos >= 10_000)
        val list = (if (keep) listOf(r) + rest else rest).take(30)
        if (watched) prefs.edit().putBoolean(key(a, "w" + r.key), true).apply()
        prefs.edit().putString(key(a, "resume"), JSONArray(list.map {
            JSONObject().put("key", it.key).put("title", it.title).put("meta", it.meta).put("image", it.image)
                .put("pos", it.pos).put("dur", it.dur).put("vodId", it.vodId).put("seriesId", it.seriesId)
        }).toString()).apply()
    }

    fun removeResume(a: Account?, key: String) {
        val list = resumes(a).filter { it.key != key }
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
    // what a playlist downloads: "live", "vod", "series"; never empty
    fun parts(a: Account?): Set<String> = prefs.getStringSet(key(a, "parts"), null)?.toSet()?.takeIf { it.isNotEmpty() } ?: ALL_PARTS
    fun setParts(a: Account?, set: Set<String>) = prefs.edit().putStringSet(key(a, "parts"), set).apply()

    // off: hidden categories aren't downloaded at all
    var downloadHidden: Boolean get() = bool("downloadHidden", true); set(v) = put("downloadHidden", v)
    var buffer: String
        get() = prefs.getString("buffer", "Medium")!!
        set(v) = prefs.edit().putString("buffer", v).apply()
    var autoplayNext: Boolean
        get() = prefs.getBoolean("autoplay", true)
        set(v) = prefs.edit().putBoolean("autoplay", v).apply()
    var subtitlesOn: Boolean
        get() = prefs.getBoolean("subs", false)
        set(v) = prefs.edit().putBoolean("subs", v).apply()
    private fun str(k: String, d: String) = prefs.getString(k, d)!!
    private fun put(k: String, v: String) = prefs.edit().putString(k, v).apply()
    private fun bool(k: String, d: Boolean) = prefs.getBoolean(k, d)
    private fun put(k: String, v: Boolean) = prefs.edit().putBoolean(k, v).apply()

    /** Playlist refresh: "Every start", "Daily", "Weekly", "Monthly" or "Manually". */
    var refreshEvery: String get() = str("refreshEvery", "Daily"); set(v) = put("refreshEvery", v)
    /** TV guide refresh: "6 hours", "12 hours", "Daily" or "Manually". */
    var guideEvery: String get() = str("guideEvery", "12 hours"); set(v) = put("guideEvery", v)
    /** Live TV opens on "All channels" or the "Last category" you used. */
    var liveStart: String get() = str("liveStart", "All channels"); set(v) = put("liveStart", v)
    var lastLiveGroup: String get() = str("lastLiveGroup", ""); set(v) = put("lastLiveGroup", v)
    var resumeLastChannel: Boolean get() = bool("resumeLastChannel", false); set(v) = put("resumeLastChannel", v)
    var showNumbers: Boolean get() = bool("showNumbers", true); set(v) = put("showNumbers", v)
    /** Preferred track languages as ISO codes, "" = the stream's default. */
    var audioLang: String get() = str("audioLang", ""); set(v) = put("audioLang", v)
    var subLang: String get() = str("subLang", ""); set(v) = put("subLang", v)
    var skipBackSec: Int get() = prefs.getInt("skipBack", 10); set(v) = prefs.edit().putInt("skipBack", v).apply()
    var skipFwdSec: Int get() = prefs.getInt("skipFwd", 30); set(v) = prefs.edit().putInt("skipFwd", v).apply()
    var pipOnLeave: Boolean get() = bool("pipOnLeave", true); set(v) = put("pipOnLeave", v)
    var zoomDefault: Boolean get() = bool("zoomDefault", false); set(v) = put("zoomDefault", v)
    var clock24: Boolean get() = bool("clock24", true); set(v) = put("clock24", v)

    /** Milliseconds a cached catalogue stays fresh for the chosen schedule (null = never refresh on its own). */
    fun refreshAfterMs(): Long? = when (refreshEvery) {
        "Every start" -> 0; "Weekly" -> 7 * 86_400_000L; "Monthly" -> 30 * 86_400_000L; "Manually" -> null; else -> 86_400_000L
    }

    fun guideAfterMs(): Long? = when (guideEvery) { "6 hours" -> 6 * 3600_000L; "Daily" -> 86_400_000L; "Manually" -> null; else -> 12 * 3600_000L }

    /** Drops the saved catalogue and guide for a playlist; the next start downloads them again. */
    fun clearCache(a: Account) = cacheDir(a).deleteRecursively()
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

val ALL_PARTS = setOf("live", "vod", "series")
