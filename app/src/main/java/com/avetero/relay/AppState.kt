package com.avetero.relay

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
    data class VodPlayer(val item: PlayItem, val next: List<PlayItem> = emptyList(), val episodes: List<PlayItem> = emptyList()) : Screen
    data object Search : Screen
    data object Favourites : Screen
    data object More : Screen
    data class Settings(val section: String = "playlists") : Screen
}

/** Something the VOD player can play and save progress for. */
data class PlayItem(val url: String, val title: String, val meta: String, val image: String, val resumeKey: String, val vodId: Int = 0, val seriesId: Int = 0)

enum class StepState { Todo, Busy, Done, Failed }
data class SyncStep(val label: String, val state: StepState = StepState.Todo, val detail: String = "", val unit: String = "", val progress: StepProgress = StepProgress())

/** Live progress of one sync step; written from background threads while it downloads and reads. */
class StepProgress {
    /** 0..1, or -1 while the size isn't known */
    var fraction by mutableFloatStateOf(-1f)
    var bytes by mutableLongStateOf(0L)
    var count by mutableIntStateOf(0)
    var reading by mutableStateOf(false)
    fun onBytes(read: Long, total: Long) { bytes = read; fraction = if (total > 0) (read.toFloat() / total).coerceAtMost(1f) else -1f }
}

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
    /** Full guide from XMLTV, keyed by epg_channel_id; short EPG (epg) fills in where it has nothing. */
    var guide by mutableStateOf<Guide>(emptyMap())
    var guideStatus by mutableStateOf("")
    var favourites by mutableStateOf(store.favourites(account))
    var hidden by mutableStateOf(store.hidden(account))
    // what the user browses: everything except hidden categories
    val liveCatsVisible by derivedStateOf { ordered("live").filter { "live:${it.id}" !in hidden } }
    val vodCatsVisible by derivedStateOf { ordered("vod").filter { "vod:${it.id}" !in hidden } }
    val seriesCatsVisible by derivedStateOf { ordered("series").filter { "series:${it.id}" !in hidden } }
    var orders by mutableStateOf(loadOrders(account))
    private fun loadOrders(a: Account?) = listOf("live", "vod", "series").associateWith { store.order(a, it) }
    fun catsOf(type: String) = when (type) { "vod" -> vodCats; "series" -> seriesCats; else -> liveCats }

    /** A type's categories in the user's order; ones the order doesn't know (new from the provider) follow in provider order. */
    fun ordered(type: String): List<Category> {
        val order = orders[type].orEmpty()
        val cats = catsOf(type)
        if (order.isEmpty()) return cats
        val rank = order.withIndex().associate { (i, id) -> id to i }
        return cats.sortedBy { rank[it.id] ?: Int.MAX_VALUE } // stable, so unknown ones keep provider order
    }

    /** Moves the [ids] categories of [type] together, keeping their order among themselves. null resets to provider order. */
    fun setCategoryOrder(type: String, ids: List<String>) { store.setOrder(account, type, ids); orders = orders + (type to ids) }

    fun moveCategories(type: String, ids: Set<String>, how: Move?) {
        val next = if (how == null) emptyList() else moveIds(ordered(type).map { it.id }, ids, how)
        store.setOrder(account, type, next)
        orders = orders + (type to next)
    }
    val channelsVisible by derivedStateOf { if (hidden.none { it.startsWith("live:") }) channels else channels.filter { "live:${it.categoryId}" !in hidden } }
    val moviesVisible by derivedStateOf { if (hidden.none { it.startsWith("vod:") }) movies else movies.filter { "vod:${it.categoryId}" !in hidden } }
    val seriesVisible by derivedStateOf { if (hidden.none { it.startsWith("series:") }) series else series.filter { "series:${it.categoryId}" !in hidden } }
    var resumes by mutableStateOf(store.resumes(account))
    /** Set by a screen that needs every remote key (returns true when it handled one); see MainActivity.dispatchKeyEvent. */
    var keyOverride: ((android.view.KeyEvent) -> Boolean)? = null
    var recentChannels by mutableStateOf(store.recents(account, "recent"))
    // before recents were recorded, fall back to what's in Continue watching
    var recentMovies by mutableStateOf(store.recents(account, "rmovie").ifEmpty { resumes.map { it.vodId }.filter { it != 0 }.distinct() })
    var recentSeries by mutableStateOf(store.recents(account, "rseries").ifEmpty { resumes.map { it.seriesId }.filter { it != 0 }.distinct() })
    fun addRecentChannel(id: Int) { store.addRecent(account, "recent", id); recentChannels = store.recents(account, "recent") }
    fun addRecentVod(item: PlayItem) {
        if (item.seriesId != 0) { store.addRecent(account, "rseries", item.seriesId); recentSeries = store.recents(account, "rseries") }
        else if (item.vodId != 0) { store.addRecent(account, "rmovie", item.vodId); recentMovies = store.recents(account, "rmovie") }
    }
    var syncSteps = mutableStateListOf<SyncStep>()
    var update by mutableStateOf<Release?>(null)
    var message by mutableStateOf<String?>(null)
    var hls by mutableStateOf(store.hls)
    /** True until the saved catalogue has been read back (it can be 100+ MB, so never on the main thread). */
    var libraryLoading by mutableStateOf(false)
    /** True while the activity is shown as a picture-in-picture window. */
    var inPip by mutableStateOf(false)
    val onPlayerScreen get() = screen is Screen.LivePlayer || screen is Screen.VodPlayer
    /** Screens where leaving the app should keep the video going in a floating window: the players, and Live TV's preview. */
    val pipEligible get() = store.pipOnLeave && player.active && (onPlayerScreen || screen is Screen.Live)

    /** Bumped when an appearance setting changes so screens redraw (e.g. the clock format). */
    var appearanceTick by mutableStateOf(0)

    private val epgGate = Semaphore(4)
    private val epgFetched = HashMap<Int, Long>()

    val api get() = Xtream(account!!)

    init {
        clock24 = store.clock24
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
        RECENT -> recentChannels.mapNotNull { id -> channels.firstOrNull { it.id == id } }
        ALL -> channelsVisible
        else -> channels.filter { it.categoryId == groupId }
    }

    fun isFav(key: String) = key in favourites
    fun toggleFav(key: String) {
        favourites = if (isFav(key)) favourites - key else favourites + key
        store.setFavourites(account, favourites)
        message = if (isFav(key)) "Added to favourites" else "Removed from favourites"
    }

    fun setCategoriesHidden(keys: Collection<String>, hide: Boolean) {
        hidden = if (hide) hidden + keys else hidden - keys.toSet()
        store.setHidden(account, hidden)
    }

    fun setCategoryHidden(key: String, hide: Boolean) {
        hidden = if (hide) hidden + key else hidden - key
        store.setHidden(account, hidden)
    }

    fun clearCacheAndReload() {
        val a = account ?: return
        player.stop(); store.clearCache(a); guide = emptyMap(); epg.clear(); epgFetched.clear(); go(Screen.Sync)
    }

    fun liveUrl(c: Channel) = api.streamUrl(c, hls)

    fun saveResume(r: Resume, upNext: Boolean = false) { store.saveResume(account, r, upNext); resumes = store.resumes(account) }
    fun removeResume(key: String) { store.removeResume(account, key); resumes = store.resumes(account); message = "Removed from Continue watching" }

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
        syncSteps += listOf(SyncStep("Signing in"), SyncStep("Live channels", unit = "channels"), SyncStep("Movies", unit = "movies"),
            SyncStep("Series", unit = "series"), SyncStep("TV guide", unit = "programmes"))
        fun set(i: Int, s: StepState, d: String = "") { syncSteps[i] = syncSteps[i].copy(state = s, detail = d) }

        set(0, StepState.Busy)
        info = withContext(Dispatchers.IO) { x.login() }
        set(0, StepState.Done, info?.expires?.let { "Active · expires " + fmtDate(it) } ?: "Active")

        suspend fun step(i: Int, name: String, cats: () -> List<Category>, download: (java.io.File, (Long, Long) -> Unit) -> Unit,
                         apply: (List<Category>, java.io.Reader, (Int) -> Unit) -> Int) {
            set(i, StepState.Busy, "Downloading…")
            val p = syncSteps[i].progress
            try {
                val file = store.cacheFile(a, name)
                val c = withContext(Dispatchers.IO) {
                    val c = cats()
                    download(file, p::onBytes)
                    store.writeCache(a, name + "_cats", org.json.JSONArray(c.map { org.json.JSONObject().put("category_id", it.id).put("category_name", it.name) }).toString())
                    c
                }
                set(i, StepState.Busy, "Reading…")
                p.reading = true; p.fraction = 0f
                val size = file.length()
                val n = withContext(Dispatchers.IO) {
                    Counting(file.inputStream(), size, p::onBytes).bufferedReader().use { apply(c, it) { n -> p.count = n } }
                }
                p.count = n; p.fraction = 1f
                set(i, StepState.Done, "%,d".format(n))
            } catch (e: OutOfMemoryError) {
                set(i, StepState.Failed, "Too large for this device")
            } catch (e: Exception) {
                set(i, StepState.Failed, e.message ?: "Failed")
            }
        }
        listOf(
            async { step(1, "live", x::liveCategories, x::downloadChannels) { c, r, n -> liveCats = c; channels = parseChannels(r, n); channels.size } },
            async { step(2, "vod", x::vodCategories, x::downloadVods) { c, r, n -> vodCats = c; movies = parseVods(r, n); movies.size } },
            async { step(3, "series", x::seriesCategories, x::downloadSeries) { c, r, n -> seriesCats = c; series = parseSeries(r, n); series.size } },
        ).forEach { it.await() }
        // the guide maps onto channels, so it comes last
        set(4, StepState.Busy, "Downloading…")
        val n = refreshGuide(force = true, syncSteps[4].progress)
        syncSteps[4].progress.fraction = 1f
        set(4, if (n > 0) StepState.Done else StepState.Failed, if (n > 0) "%,d channels".format(n) else guideStatus)
    }

    fun programmesFor(c: Channel): List<Programme>? =
        c.epgId.takeIf { it.isNotEmpty() }?.let { guide[it] }?.takeIf { it.isNotEmpty() } ?: epg[c.id]

    /** Short EPG for a channel the full guide doesn't cover, fetched at most every 30 min. */
    suspend fun loadEpg(c: Channel) {
        if (c.epgId.isNotEmpty() && !guide[c.epgId].isNullOrEmpty()) return
        val id = c.id
        val now = System.currentTimeMillis()
        val last = epgFetched[id]
        val list = epg[id]
        if (last != null && now - last < 30 * 60_000 && (list.isNullOrEmpty() || list.last().end * 1000 > now)) return
        epgFetched[id] = now
        val fresh = runCatching { epgGate.withPermit { withContext(Dispatchers.IO) { api.shortEpg(id) } } }.getOrNull() ?: return
        epg[id] = fresh
    }

    /**
     * Full TV guide: read from the cache when it's under 12 h old, otherwise download the provider's XMLTV
     * and keep 2 h back to 24 h ahead. Failure leaves whatever guide we had.
     */
    suspend fun refreshGuide(force: Boolean = false, progress: StepProgress? = null): Int {
        val a = account ?: return 0
        val file = store.cacheFile(a, "guide")
        val now = System.currentTimeMillis() / 1000
        val fresh = store.guideAfterMs()?.let { System.currentTimeMillis() - file.lastModified() < it } ?: true
        if (!force && file.exists() && fresh) {
            if (guide.isEmpty()) runCatching { guide = withContext(Dispatchers.IO) { readGuide(file, now) } }
            guideStatus = "Updated " + fmtTime(file.lastModified() / 1000) + " · ${"%,d".format(guide.size)} channels"
            return guide.size
        }
        val wanted = channels.mapNotNullTo(HashSet()) { it.epgId.takeIf { id -> id.isNotEmpty() } }
        if (wanted.isEmpty()) { guideStatus = "Your provider doesn't link channels to a guide"; return 0 }
        guideStatus = "Downloading…"
        return try {
            val g = withContext(Dispatchers.IO) { api.guide(wanted, now - 2 * 3600, now + 24 * 3600, progress?.let { it::onBytes }) { n -> progress?.apply { reading = true; count = n } }.also { writeGuide(file, it) } }
            guide = g
            guideStatus = "Updated " + fmtTime(now) + " · ${"%,d".format(g.size)} channels"
            g.size
        } catch (e: OutOfMemoryError) {
            guideStatus = "Too large for this device"; 0
        } catch (e: Exception) {
            guideStatus = "Couldn't download: ${e.message}"; 0
        }
    }

    fun signIn(a: Account) {
        store.addPlaylist(a)
        switchTo(a)
    }

    fun switchTo(a: Account) {
        player.stop()
        store.activeIndex = store.playlists().indexOf(a).coerceAtLeast(0)
        account = a
        epg.clear(); epgFetched.clear(); guide = emptyMap(); guideStatus = ""
        favourites = store.favourites(a)
        hidden = store.hidden(a)
        orders = loadOrders(a)
        resumes = store.resumes(a)
        recentChannels = store.recents(a, "recent"); recentMovies = store.recents(a, "rmovie").ifEmpty { resumes.map { it.vodId }.filter { it != 0 }.distinct() }
        recentSeries = store.recents(a, "rseries").ifEmpty { resumes.map { it.seriesId }.filter { it != 0 }.distinct() }
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
        const val RECENT = "__recent"
    }
}

fun fmtDate(epochSec: Long): String =
    java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(epochSec * 1000))

/** 24-hour or 12-hour clock, from Settings → Appearance. */
var clock24 = true

fun fmtTime(epochSec: Long): String =
    java.text.SimpleDateFormat(if (clock24) "HH:mm" else "h:mm a", java.util.Locale.getDefault()).format(java.util.Date(epochSec * 1000))

enum class Move { Top, Up, Down, Bottom }

/** Moves the selected ids in [list]: Top/Bottom gather them at that end; Up/Down shift each one place past an unselected neighbour. */
internal fun moveIds(list: List<String>, sel: Set<String>, how: Move): List<String> = when (how) {
    Move.Top -> list.filter { it in sel } + list.filter { it !in sel }
    Move.Bottom -> list.filter { it !in sel } + list.filter { it in sel }
    Move.Up -> list.toMutableList().also { l -> for (i in 1 until l.size) if (l[i] in sel && l[i - 1] !in sel) java.util.Collections.swap(l, i, i - 1) }
    Move.Down -> list.toMutableList().also { l -> for (i in l.size - 2 downTo 0) if (l[i] in sel && l[i + 1] !in sel) java.util.Collections.swap(l, i, i + 1) }
}
