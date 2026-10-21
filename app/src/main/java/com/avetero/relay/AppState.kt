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
    data class Live(val group: String? = null) : Screen
    data object Guide : Screen
    data class LivePlayer(val channel: Channel, val list: List<Channel>) : Screen
    data class VodGrid(val series: Boolean, val group: String? = null) : Screen
    data class MovieDetail(val vod: Vod) : Screen
    data class SeriesDetail(val series: Series) : Screen
    data class VodPlayer(val item: PlayItem, val next: List<PlayItem> = emptyList()) : Screen
    data object Search : Screen
    data object Favourites : Screen
    data object More : Screen
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
    /** True until the saved catalogue has been read back (it can be 100+ MB, so never on the main thread). */
    var libraryLoading by mutableStateOf(false)
    /** True while the activity is shown as a picture-in-picture window. */
    var inPip by mutableStateOf(false)
    val onPlayerScreen get() = screen is Screen.LivePlayer || screen is Screen.VodPlayer

    private val epgGate = Semaphore(4)
    private val epgFetched = HashMap<Int, Long>()

    val api get() = Xtream(account!!)

    init {
        val a = account
        stack += when {
            a == null -> Screen.Login
            hasCache(a) -> { libraryLoading = true; Screen.Home }
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

    private fun hasCache(a: Account) = store.cacheFile(a, "live").exists()

    suspend fun loadLibrary() {
        val a = account ?: return
        if (!libraryLoading) return
        withContext(Dispatchers.IO) { loadCache(a) }
        libraryLoading = false
    }

    private fun loadCache(a: Account) {
        fun <T> read(name: String, parse: (java.io.Reader) -> List<T>): List<T> =
            store.cacheFile(a, name).takeIf { it.exists() }?.bufferedReader()?.use(parse) ?: emptyList()
        runCatching {
            liveCats = parseCategories(store.readCache(a, "live_cats") ?: "[]")
            channels = read("live", ::parseChannels)
            vodCats = parseCategories(store.readCache(a, "vod_cats") ?: "[]")
            movies = read("vod", ::parseVods)
            seriesCats = parseCategories(store.readCache(a, "series_cats") ?: "[]")
            series = read("series", ::parseSeries)
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

        suspend fun step(i: Int, name: String, cats: () -> List<Category>, download: (java.io.File) -> Unit, apply: (List<Category>, java.io.Reader) -> Int) {
            set(i, StepState.Busy, "Downloading…")
            try {
                val file = store.cacheFile(a, name)
                val c = withContext(Dispatchers.IO) {
                    val c = cats()
                    download(file)
                    store.writeCache(a, name + "_cats", org.json.JSONArray(c.map { org.json.JSONObject().put("category_id", it.id).put("category_name", it.name) }).toString())
                    c
                }
                set(i, StepState.Busy, "Reading…")
                val n = withContext(Dispatchers.IO) { file.bufferedReader().use { apply(c, it) } }
                set(i, StepState.Done, "%,d".format(n))
            } catch (e: OutOfMemoryError) {
                set(i, StepState.Failed, "Too large for this device")
            } catch (e: Exception) {
                set(i, StepState.Failed, e.message ?: "Failed")
            }
        }
        listOf(
            async { step(1, "live", x::liveCategories, x::downloadChannels) { c, r -> liveCats = c; channels = parseChannels(r); channels.size } },
            async { step(2, "vod", x::vodCategories, x::downloadVods) { c, r -> vodCats = c; movies = parseVods(r); movies.size } },
            async { step(3, "series", x::seriesCategories, x::downloadSeries) { c, r -> seriesCats = c; series = parseSeries(r); series.size } },
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
        if (hasCache(a)) { libraryLoading = true; go(Screen.Home) } else go(Screen.Sync)
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
