package com.avetero.relay.feature.categories

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.avetero.relay.app.AppState
import com.avetero.relay.data.model.Category
import com.avetero.relay.data.model.Move
import com.avetero.relay.data.model.moveIds
import java.text.Normalizer

private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

/** Comma-separated terms; "not containing" lists what matches none of them. */
internal fun filterCats(cats: List<Category>, q: String, exclude: Boolean): List<Category> {
    val terms = q.split(',').map { norm(it.trim()) }.filter { it.isNotEmpty() }
    return if (terms.isEmpty()) cats else cats.filter { c -> norm(c.name).let { n -> terms.any { n.contains(it) } } != exclude }
}

/** [full] with the ids in [part] put back in [part]'s order, in the slots they held; everything else keeps its place. */
internal fun mergeOrder(full: List<String>, part: List<String>): List<String> {
    val set = part.toSet()
    val it = part.iterator()
    return full.map { id -> if (id in set) it.next() else id }
}

internal val catTypes = listOf("live" to "Live TV", "vod" to "Movies", "series" to "Series")

/**
 * Manage categories, shared by phone and TV: order and visibility for Live TV, Movies and Series. Tick some (or filter
 * and tick all listed), then move, hide or show them together. TV adds a move mode the remote drives.
 */
internal class CategoriesModel(private val app: AppState, initialType: String) {
    var type by mutableStateOf(initialType.takeIf { it in app.parts } ?: catTypes.map { it.first }.firstOrNull { it in app.parts } ?: "live")
        private set
    var q by mutableStateOf("")
    var exclude by mutableStateOf(false)
    var picked by mutableStateOf(emptySet<String>())
    var moving by mutableStateOf(false)
        private set
    /** The order before a move started (for Back), and the row to focus once it ends. */
    private var before = emptyList<String>()
    var anchor by mutableStateOf<String?>(null)

    fun isHidden(c: Category) = "$type:${c.id}" in app.hidden
    val cats by derivedStateOf { app.ordered(type) }
    /** Positions count every category, hidden ones included, so they match the row numbers. */
    val position by derivedStateOf { cats.withIndex().associate { (i, c) -> c.id to i + 1 } }
    val counts by derivedStateOf {
        when (type) {
            "vod" -> app.movies.groupingBy { it.categoryId }.eachCount()
            "series" -> app.series.groupingBy { it.categoryId }.eachCount()
            else -> app.channels.groupingBy { it.categoryId }.eachCount()
        }
    }
    val shown by derivedStateOf { if (moving) cats.filterNot(::isHidden) else filterCats(cats, q, exclude) }
    val hiddenCount by derivedStateOf { cats.count(::isHidden) }
    val pickedCats by derivedStateOf { cats.filter { it.id in picked } }
    val custom get() = !app.orders[type].isNullOrEmpty()
    val unit get() = if (type == "live") "channels" else "titles"
    /** Where the ticked ones sit now, e.g. "12" or "12–14". */
    val range by derivedStateOf { pickedCats.mapNotNull { position[it.id] }.let { if (it.isEmpty()) "" else if (it.size == 1) "${it[0]}" else "${it.first()}–${it.last()}" } }

    fun pickType(t: String) { if (moving) return; type = t; q = ""; picked = emptySet() }
    fun tick(id: String) { if (!moving) picked = if (id in picked) picked - id else picked + id }
    fun tickAll() { picked = picked + shown.map { it.id } }
    fun clear() { picked = emptySet() }

    private fun plural(n: Int) = "$n ${if (n == 1) "category" else "categories"}"

    /** Hides or shows the ticked categories (or just [only]), with Undo. */
    fun setHidden(hide: Boolean, only: Category? = null) {
        val keys = (if (only != null) listOf(only) else pickedCats).filter { isHidden(it) != hide }.map { "$type:${it.id}" }
        if (keys.isEmpty()) return
        app.setCategoriesHidden(keys, hide)
        val what = only?.let { "“${it.name}”" } ?: plural(keys.size)
        app.say("${if (hide) "Hid" else "Showing"} $what") { app.setCategoriesHidden(keys, !hide) }
    }

    fun move(how: Move) {
        val t = type
        val old = app.orders[t].orEmpty()
        app.moveCategories(t, picked, how)
        app.say(if (how == Move.Top) "Moved to the top" else if (how == Move.Bottom) "Moved to the bottom" else "Moved") { app.setCategoryOrder(t, old) }
    }

    fun reset() { app.moveCategories(type, emptySet(), null); picked = emptySet() }

    /** Phones: a drag put the listed rows in [ids]' order; unlisted ones keep their slots. */
    fun reorderShown(ids: List<String>) = app.setCategoryOrder(type, mergeOrder(cats.map { it.id }, ids))

    /** TV: gather the ticked ones at the first one's place and let the remote carry them. Hidden ones keep their slots. */
    fun startMove() {
        picked = picked.filterNot { "$type:$it" in app.hidden }.toSet()
        if (picked.isEmpty()) return
        val ids = cats.map { it.id }
        before = ids
        val at = ids.indexOfFirst { it in picked }
        val rest = ids.filter { it !in picked }
        val insert = ids.take(at).count { it !in picked }
        app.setCategoryOrder(type, rest.take(insert) + ids.filter { it in picked } + rest.drop(insert))
        moving = true
    }

    fun step(how: Move, times: Int = 1) {
        val full = cats.map { it.id }
        var ids = cats.filterNot(::isHidden).map { it.id }
        repeat(times) { ids = moveIds(ids, picked, how) }
        app.setCategoryOrder(type, mergeOrder(full, ids))
    }

    private fun finish() { anchor = cats.firstOrNull { it.id in picked }?.id; moving = false }
    fun drop() { finish(); picked = emptySet(); app.say("Order saved") }
    fun cancel() { app.setCategoryOrder(type, before); finish() }
}
