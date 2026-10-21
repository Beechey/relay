package com.avetero.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

class EpgTest {
    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="news.uk"><display-name>News</display-name></channel>
  <programme start="20261021200000 +0100" stop="20261021210000 +0100" channel="news.uk"><title>Evening News</title><desc>Headlines</desc></programme>
  <programme start="20261021210000 +0100" stop="20261021213000 +0100" channel="news.uk"><title lang="en">Late &amp; Live</title></programme>
  <programme start="20261020100000 +0000" stop="20261020110000 +0000" channel="news.uk"><title>Too old</title></programme>
  <programme start="20261021200000 +0000" stop="20261021210000 +0000" channel="other.uk"><title>Not wanted</title></programme>
</tv>"""

    // 2026-10-21 19:00 UTC
    private val t19 = xmltvTime("20261021190000 +0000")

    @Test fun times() {
        assertEquals(t19, xmltvTime("20261021200000 +0100"))
        assertEquals(t19, xmltvTime("20261021190000"))
        assertEquals(0, xmltvTime("garbage"))
        assertEquals(0, xmltvTime(null))
    }

    @Test fun parsesWantedChannelsInWindow() {
        val g = parseXmltv(xml.byteInputStream(), setOf("news.uk"), t19 - 3600, t19 + 24 * 3600)
        assertEquals(setOf("news.uk"), g.keys)
        assertEquals(listOf("Evening News", "Late & Live"), g["news.uk"]!!.map { it.title })
        assertEquals("Headlines", g["news.uk"]!![0].description)
        assertEquals(t19, g["news.uk"]!![0].start)
    }

    @Test fun readsGzip() {
        val bytes = ByteArrayOutputStream().also { GZIPOutputStream(it).use { z -> z.write(xml.toByteArray()) } }.toByteArray()
        val g = parseXmltv(bytes.inputStream(), setOf("news.uk"), t19 - 3600, t19 + 24 * 3600)
        assertEquals(2, g["news.uk"]!!.size)
    }

    @Test fun cacheRoundTrip() {
        val g = parseXmltv(xml.byteInputStream(), setOf("news.uk"), t19 - 3600, t19 + 24 * 3600)
        val f = File.createTempFile("guide", ".bin").apply { deleteOnExit() }
        writeGuide(f, g)
        val back = readGuide(f, t19)
        assertEquals(g, back)
        assertTrue(readGuide(f, t19 + 48 * 3600).isEmpty()) // everything has ended by then
    }
}
