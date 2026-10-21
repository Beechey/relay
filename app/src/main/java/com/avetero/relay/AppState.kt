package com.avetero.relay

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Login : Screen
    data object Sync : Screen
    data object Home : Screen
    data object Live : Screen
    data object Guide : Screen
    data class LivePlayer(val channel: Channel, val list: List<Channel>) : Screen
    data class VodGrid(val series: Boolean) : Screen
    data class MovieDetail(val vod: Vod) : Screen
    data class SeriesDetail(val series: Series) : Screen
    data class VodPlayer(val item: PlayItem, val next: List<PlayItem> = emptyList()) : Screen
    data object Search : Screen
    data object Favourites : Screen
    data class Settings(val section: String = "playlists") : Screen
}

/** Something the VOD player can play and save progress for. */
data class PlayItem(val url: String, val title: String, val meta: String, val image: String, val resumeKey: String, val vodId: Int = 0, val seriesId: Int = 0)

enum class StepState { Todo, Busy, Done, Failed }
data class SyncStep(val label: String, val state: StepState = StepState.Todo, val detail: String = "")

class AppState(val ctx: Context) {
    val store = Store(ctx)
    val player = PlayerHost(ctx, store)
    val stack = mutableStateListOf<Screen>()
    val screen get() = stack.last()

    var account by mutableStateOf(store.active())
    var info by mutableStateOf<AccountInfo?>(null)
    var liveCats by mutableStateOf(emptyList<Category>())
    var channels by mutableStateOf(emptyList<Channel>())
    var vodCats by mutableStateOf(emptyList<Category>())
    var movies by mutableStateOf(emptyList<Vod>())
    var seriesCats by mutableStateOf(emptyList<Category>())
    var series by mutableStateOf(emptyList<Series>())
    val epg = mutableStateMapOf<Int, List<Programme>>()
    var favourites by mutableStateOf(store.favourites(account))
    var resumes by mutableStateOf(store.resumes(account))
    var syncSteps = mutableStateListOf<SyncStep>()
    var update by mutableStateOf<Release?>(null)
    var message by mutableStateOf<String?>(null)
    var hls by mutableStateOf(store.hls)

    private val epgGate = Semaphore(4)
    private val epgFetched = HashMap<Int, Long>()

    val api get() = Xtream(account!!)

    init {
        val a = account
        stack += when {
            a == null -> Screen.Login
            store.readCache(a, "live") != null -> { loadCache(a); Screen.Home }
            else -> Screen.Sync
        }
    }

    fun push(s: Screen) { stack += s }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    /** Rail navigation: replaces the stack. */
    fun go(s: Screen) { stack.clear(); stack += s }

    fun channelsIn(groupId: String): List<Channel> = when (groupId) {
        FAVS -> favourites.mapNotNull { k -> if (k.startsWith("c")) channels.firstOrNull { it.id == k.drop(1).toIntOrNull() } else null }
        ALL -> channels
        else -> channels.filter { it.categoryId == groupId }
    }

    fun isFav(key: String) = key in favourites
    fun toggleFav(key: String) {
        favourites = if (isFav(key)) favourites - key else favourites + key
        store.setFavourites(account, favourites)
        message = if (isFav(key)) "Added to favourites" else "Removed from favourites"
    }

    fun liveUrl(c: Channel) = api.streamUrl(c, hls)

    fun saveResume(r: Resume) { store.saveResume(account, r); resumes = store.resumes(account) }

    private fun loadCache(a: Account) {
        runCatching {
            liveCats = parseCategories(store.readCache(a, "live_cats") ?: "[]")
            channels = parseChannels(store.readCache(a, "live") ?: "[]")
            vodCats = parseCategories(store.readCache(a, "vod_cats") ?: "[]")
            movies = parseVods(store.readCache(a, "vod") ?: "[]")
            seriesCats = parseCategories(store.readCache(a, "series_cats") ?: "[]")
            series = parseSeries(store.readCache(a, "series") ?: "[]")
        }
    }

    /** Signs in and downloads the catalogue; steps drive board 02. Throws on login failure. */
    suspend fun sync(): Unit = coroutineScope {
        val a = account ?: return@coroutineScope
        val x = api
        syncSteps.clear()
        syncSteps += listOf(SyncStep("Signing in"), SyncStep("Live channels"), SyncStep("Movies"), SyncStep("Series"))
        fun set(i: Int, s: StepState, d: String = "") { syncSteps[i] = syncSteps[i].copy(state = s, detail = d) }

        set(0, StepState.Busy)
        info = withContext(Dispatchers.IO) { x.login() }
        set(0, StepState.Done, info?.expires?.let { "Active · expires " + fmtDate(it) } ?: "Active")

        suspend fun step(i: Int, catsName: String, name: String, cats: () -> List<Category>, items: () -> String, apply: (List<Category>, String) -> Int) {
            set(i, StepState.Busy)
            try {
                val (c, json) = withContext(Dispatchers.IO) {
                    val c = cats()
                    val json = items()
                    c to json
                }
                val n = withContext(Dispatchers.Default) { apply(c, json) }
                withContext(Dispatchers.IO) {
                    store.writeCache(a, catsName, org.json.JSONArray(c.map { org.json.JSONObject().put("category_id", it.id).put("category_name", it.name) }).toString())
                    store.writeCache(a, name, json)
                }
                set(i, StepState.Done, "%,d".format(n))
            } catch (e: Exception) {
                set(i, StepState.Failed, e.message ?: "Failed")
            }
        }
        listOf(
            async { step(1, "live_cats", "live", x::liveCategories, x::channelsJson) { c, j -> liveCats = c; channels = parseChannels(j); channels.size } },
            async { step(2, "vod_cats", "vod", x::vodCategories, x::vodJson) { c, j -> vodCats = c; movies = parseVods(j); movies.size } },
            async { step(3, "series_cats", "series", x::seriesCategories, x::seriesJson) { c, j -> seriesCats = c; series = parseSeries(j); series.size } },
        ).forEach { it.await() }
    }

    /** Short EPG for a channel, fetched at most every 30 min. */
    suspend fun loadEpg(id: Int) {
        val now = System.currentTimeMillis()
        val last = epgFetched[id]
        val list = epg[id]
        if (last != null && now - last < 30 * 60_000 && (list.isNullOrEmpty() || list.last().end * 1000 > now)) return
        epgFetched[id] = now
        val fresh = runCatching { epgGate.withPermit { withContext(Dispatchers.IO) { api.shortEpg(id) } } }.getOrNull() ?: return
        epg[id] = fresh
    }

    fun signIn(a: Account) {
        store.addPlaylist(a)
        switchTo(a)
    }

    fun switchTo(a: Account) {
        player.stop()
        store.activeIndex = store.playlists().indexOf(a).coerceAtLeast(0)
        account = a
        epg.clear(); epgFetched.clear()
        favourites = store.favourites(a)
        resumes = store.resumes(a)
        liveCats = emptyList(); channels = emptyList(); vodCats = emptyList(); movies = emptyList(); seriesCats = emptyList(); series = emptyList()
        if (store.readCache(a, "live") != null) { loadCache(a); go(Screen.Home) } else go(Screen.Sync)
    }

    fun remove(a: Account) {
        store.removePlaylist(a)
        val next = store.active()
        if (next == null) { player.stop(); account = null; go(Screen.Login) } else switchTo(next)
    }

    companion object {
        const val FAVS = "__favs"
        const val ALL = "__all"
    }
}

fun fmtDate(epochSec: Long): String =
    java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(epochSec * 1000))

fun fmtTime(epochSec: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epochSec * 1000))
