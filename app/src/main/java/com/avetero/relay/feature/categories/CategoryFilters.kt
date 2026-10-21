package com.avetero.relay.feature.categories

import androidx.compose.runtime.Composable
import com.avetero.relay.data.model.Category
import com.avetero.relay.ui.components.SegmentedTabs
import com.avetero.relay.ui.components.norm

/** Comma-separated terms; "not containing" lists what matches none of them. */
internal fun filterCats(cats: List<Category>, q: String, exclude: Boolean): List<Category> {
    val terms = q.split(',').map { norm(it.trim()) }.filter { it.isNotEmpty() }
    return if (terms.isEmpty()) cats else cats.filter { c -> norm(c.name).let { n -> terms.any { n.contains(it) } } != exclude }
}

@Composable
internal fun TypeTabs(type: String, onType: (String) -> Unit) =
    SegmentedTabs(listOf("live" to "Live TV", "vod" to "Movies", "series" to "Series"), type, onType)

@Composable
internal fun ExcludeTabs(exclude: Boolean, onExclude: (Boolean) -> Unit) =
    SegmentedTabs(listOf(false to "Containing", true to "Not containing"), exclude, onExclude, small = true)
