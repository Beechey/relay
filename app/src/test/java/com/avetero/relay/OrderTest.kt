package com.avetero.relay

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderTest {
    private val l = listOf("a", "b", "c", "d", "e")

    @Test fun topAndBottomKeepRelativeOrder() {
        assertEquals(listOf("b", "d", "a", "c", "e"), moveIds(l, setOf("d", "b"), Move.Top))
        assertEquals(listOf("a", "c", "e", "b", "d"), moveIds(l, setOf("d", "b"), Move.Bottom))
    }

    @Test fun upAndDownStepPastUnselected() {
        assertEquals(listOf("b", "a", "d", "c", "e"), moveIds(l, setOf("b", "d"), Move.Up))
        assertEquals(listOf("a", "b", "c", "d", "e"), moveIds(l, setOf("a", "b"), Move.Up)) // already at the top
        assertEquals(listOf("a", "c", "b", "e", "d"), moveIds(l, setOf("b", "d"), Move.Down))
    }
}
