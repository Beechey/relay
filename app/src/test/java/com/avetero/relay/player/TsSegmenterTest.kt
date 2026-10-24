package com.avetero.relay.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TsSegmenterTest {
    private fun packet(pid: Int, start: Boolean, body: (ByteArray) -> Unit = {}): ByteArray {
        val p = ByteArray(188) { 0xFF.toByte() }
        p[0] = 0x47; p[1] = ((if (start) 0x40 else 0) or (pid shr 8)).toByte(); p[2] = pid.toByte(); p[3] = 0x10
        body(p); return p
    }

    private val pat = packet(0, true) { p ->
        // pointer, table 0, length 13, ts id, version, section, last, program 1 -> PMT pid 0x1000
        intArrayOf(0x00, 0x00, 0xB0, 0x0D, 0x00, 0x01, 0xC1, 0x00, 0x00, 0x00, 0x01, 0xF0, 0x00).forEachIndexed { i, v -> p[4 + i] = v.toByte() }
    }
    private val pmt = packet(0x1000, true) { p ->
        // pointer, table 2, length 18, program 1, ..., PCR pid 0x100, no program info, H.264 on pid 0x100
        intArrayOf(0x00, 0x02, 0xB0, 0x12, 0x00, 0x01, 0xC1, 0x00, 0x00, 0xE1, 0x00, 0xF0, 0x00, 0x1B, 0xE1, 0x00, 0xF0, 0x00).forEachIndexed { i, v -> p[4 + i] = v.toByte() }
    }

    /** Video PES start; [key] sets the random access flag, [ms] is the PTS in milliseconds. */
    private fun video(ms: Long, key: Boolean) = packet(0x100, true) { p ->
        p[3] = 0x30; p[4] = 7; p[5] = (if (key) 0x40 else 0).toByte() // adaptation field of 7 bytes
        val i = 12
        val pts = ms * 90
        intArrayOf(0, 0, 1, 0xE0, 0, 0, 0x80, 0x80, 5).forEachIndexed { k, v -> p[i + k] = v.toByte() }
        p[i + 9] = (0x21 or ((pts shr 29) and 0x0E).toInt()).toByte(); p[i + 10] = (pts shr 22).toByte()
        p[i + 11] = (((pts shr 14) and 0xFE) or 1).toByte(); p[i + 12] = (pts shr 7).toByte(); p[i + 13] = (((pts shl 1) and 0xFE) or 1).toByte()
    }

    @Test fun cutsAtKeyframesWithTablesUpFront() {
        val seg = TsSegmenter(targetMs = 4_000, clock = { 0L })
        seg.accept(pat); seg.accept(pmt)
        seg.accept(video(0, key = false))             // before the first keyframe: dropped
        var closed = 0
        for (t in 0L..12_000L step 1_000L) {          // keyframe every 2 s, a frame every second
            if (seg.accept(video(t, key = t % 2_000 == 0L))) closed++
        }
        assertEquals(3, closed)                       // at 4, 8 and 12 s
        val list = seg.playlist()
        assertTrue(list, list.contains("#EXTINF:4.000,\nseg/0.ts") && list.contains("seg/1.ts"))
        val first = seg.segment(0)!!
        assertEquals(0, first[2].toInt())                 // PAT first
        assertEquals(0x00, first[188 + 2].toInt())        // then PMT (pid 0x1000)
        assertEquals(0x10, first[188 + 1].toInt() and 0x1F)
        assertEquals(0x40, first[2 * 188 + 5].toInt())    // then a keyframe
    }
}
