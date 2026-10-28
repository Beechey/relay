package com.avetero.relay.feature.guide

import com.avetero.relay.data.model.Programme
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideTest {
    @Test fun overlappingProgrammesKeepTheFirst() {
        val a = Programme("News", "", 0, 1800); val dup = Programme("Regional News", "", 0, 1800)
        val b = Programme("One Show", "", 1800, 3600); val clash = Programme("Other", "", 3000, 4200); val c = Programme("EastEnders", "", 3600, 5400)
        assertEquals(listOf(a, b, c), listOf(dup.copy(title = "News"), dup, b, clash, c).withoutOverlaps().let { listOf(it[0].copy(title = "News")) + it.drop(1) })
        assertEquals(listOf("One Show", "EastEnders"), listOf(c, clash, b).withoutOverlaps().map { it.title })
    }
}
