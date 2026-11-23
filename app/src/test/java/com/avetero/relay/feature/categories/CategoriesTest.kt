package com.avetero.relay.feature.categories

import com.avetero.relay.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoriesTest {
    @Test fun mergeKeepsUnlistedSlots() {
        // b and d were listed and swapped by a drag; a, c, e keep their places
        assertEquals(listOf("a", "d", "c", "b", "e"), mergeOrder(listOf("a", "b", "c", "d", "e"), listOf("d", "b")))
        assertEquals(listOf("x", "y"), mergeOrder(listOf("x", "y"), emptyList()))
    }

    @Test fun filterTermsAndExclude() {
        val cats = listOf(Category("1", "UK | Sport"), Category("2", "UK | News"), Category("3", "Café Kids"))
        assertEquals(listOf("1", "3"), filterCats(cats, "sport, cafe", false).map { it.id })
        assertEquals(listOf("2"), filterCats(cats, "sport, cafe", true).map { it.id })
        assertEquals(cats, filterCats(cats, " , ", false))
    }
}
