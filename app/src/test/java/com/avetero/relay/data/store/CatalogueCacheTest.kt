package com.avetero.relay.data.store

import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.Series
import com.avetero.relay.data.model.Vod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class CatalogueCacheTest {
    @Test fun roundTripsEveryList() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        val ch = listOf(Channel(1, 101, "UK: Sky News HD", "12", "http://x/logo.png", true, "sky.news.uk"), Channel(2, 0, "Äl Jazeera", "12"))
        val vod = listOf(Vod(7, "The Wood (1999)", "http://x/p.jpg", 6.7, "3", "mkv", 1_700_000_000))
        val ser = listOf(Series(9, "Fringe", "http://x/c.jpg", 8.0, "5", "", "2008"))
        CatalogueCache.writeChannels(File(dir, "live.bin"), ch)
        CatalogueCache.writeVods(File(dir, "vod.bin"), vod)
        CatalogueCache.writeSeries(File(dir, "series.bin"), ser)
        assertEquals(ch, CatalogueCache.readChannels(File(dir, "live.bin")))
        assertEquals(vod, CatalogueCache.readVods(File(dir, "vod.bin")))
        assertEquals(ser, CatalogueCache.readSeries(File(dir, "series.bin")))
    }

    @Test fun unreadableFileFallsBack() {
        val f = File(kotlin.io.path.createTempDirectory().toFile(), "live.bin").apply { writeBytes(byteArrayOf(0, 0, 0, 99)) } // another version
        assertNull(CatalogueCache.readChannels(f))
        assertNull(CatalogueCache.readChannels(File("/nonexistent/live.bin")))
    }
}
