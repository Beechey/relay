package com.avetero.relay.player

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import kotlin.concurrent.thread

/**
 * Lets a Chromecast play a live MPEG-TS channel. Google's receiver only plays HLS, insists on CORS headers and can't
 * follow this kind of provider's https → http redirects, so the phone reads the stream itself, cuts it into short HLS
 * segments at keyframes, and serves them on the local network for the TV to fetch.
 */
class CastRelay {
    private val segmenter = TsSegmenter()
    private var server: ServerSocket? = null
    @Volatile private var running = false

    /** Starts reading [source]; [onReady] gets the playlist URL once a few segments exist, [onError] a reason if it fails. */
    fun start(source: String, ua: String, onReady: (String) -> Unit, onError: (String) -> Unit) {
        stop()
        val ip = lanAddress() ?: return onError("Connect your phone to the same Wi-Fi as the Chromecast.")
        val socket = ServerSocket(0).also { server = it }
        running = true
        segmenter.reset()
        thread(name = "cast-relay-server", isDaemon = true) { serve(socket) }
        thread(name = "cast-relay-reader", isDaemon = true) {
            var announced = false
            try {
                openFollowingRedirects(source, ua).use { input ->
                    segmenter.read(input, { running }) {
                        if (!announced && segmenter.count() >= READY_SEGMENTS) {
                            announced = true
                            onReady("http://${ip.hostAddress}:${socket.localPort}/live.m3u8")
                        }
                    }
                }
                if (running) onError("The channel stopped sending video.")
            } catch (e: Exception) {
                if (running) onError("Couldn't read the channel for the TV (${e.message}).")
            }
        }
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
    }

    private fun serve(socket: ServerSocket) {
        while (running) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            thread(isDaemon = true) { client.use { answer(it) } }
        }
    }

    private fun answer(client: Socket) {
        val request = client.getInputStream().bufferedReader().readLine() ?: return
        val (method, path) = request.split(' ').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        val out = client.getOutputStream()
        fun reply(status: String, type: String, body: ByteArray) {
            out.write(("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: *\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n").toByteArray())
            if (method != "HEAD") out.write(body)
            out.flush()
        }
        when {
            method == "OPTIONS" -> reply("204 No Content", "text/plain", ByteArray(0))
            path.startsWith("/live.m3u8") -> reply("200 OK", "application/vnd.apple.mpegurl", segmenter.playlist().toByteArray())
            path.startsWith("/seg/") -> segmenter.segment(path.removePrefix("/seg/").substringBefore(".ts").toLongOrNull())
                ?.let { reply("200 OK", "video/mp2t", it) } ?: reply("404 Not Found", "text/plain", ByteArray(0))
            else -> reply("404 Not Found", "text/plain", ByteArray(0))
        }
    }

    companion object {
        private const val READY_SEGMENTS = 3

        /** HttpURLConnection won't follow an https → http redirect on its own, and providers use exactly that. */
        fun openFollowingRedirects(url: String, ua: String): InputStream {
            var current = url
            repeat(6) {
                val c = URL(current).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", ua)
                if (c.responseCode in 300..399) {
                    current = URL(URL(current), c.getHeaderField("Location")).toString(); c.disconnect(); return@repeat
                }
                if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
                return c.inputStream
            }
            throw java.io.IOException("too many redirects")
        }

        /** The phone's address on the local network, where the Chromecast can reach it. */
        fun lanAddress(): InetAddress? = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.name.startsWith("rmnet") && !it.name.startsWith("tun") }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
    }
}

/**
 * Cuts a live MPEG-TS stream into HLS segments of about [targetMs] at video keyframes. Each segment starts with the
 * latest PAT and PMT so the TV can decode it on its own. Keeps the last [keep] segments in memory.
 */
class TsSegmenter(private val targetMs: Long = 4_000, private val keep: Int = 8, private val clock: () -> Long = System::currentTimeMillis) {
    private class Segment(val seq: Long, val durationSec: Double, val bytes: ByteArray)
    private val segments = ArrayDeque<Segment>()
    private var seq = 0L
    private var pat: ByteArray? = null
    private var pmt: ByteArray? = null
    private var pmtPid = -1
    private var videoPid = -1
    private var current = ByteArrayOutputStream()
    private var startedAt = 0L
    private var startPts = -1L

    @Synchronized fun reset() { segments.clear(); seq = 0; pat = null; pmt = null; pmtPid = -1; videoPid = -1; current = ByteArrayOutputStream(); startedAt = 0; startPts = -1 }
    @Synchronized fun count() = segments.size
    @Synchronized fun segment(n: Long?) = segments.firstOrNull { it.seq == n }?.bytes

    @Synchronized fun playlist(): String = buildString {
        append("#EXTM3U\n#EXT-X-VERSION:3\n")
        append("#EXT-X-TARGETDURATION:${(segments.maxOfOrNull { it.durationSec } ?: 4.0).let { Math.ceil(it).toInt() }}\n")
        append("#EXT-X-MEDIA-SEQUENCE:${segments.firstOrNull()?.seq ?: 0}\n")
        segments.forEach { append("#EXTINF:%.3f,\nseg/%d.ts\n".format(java.util.Locale.US, it.durationSec, it.seq)) }
    }

    /** Reads 188-byte packets until [keepGoing] turns false or the stream ends; [onSegment] runs after each new segment. */
    fun read(input: InputStream, keepGoing: () -> Boolean, onSegment: () -> Unit) {
        val stream = BufferedInputStream(input, 188 * 512)
        val p = ByteArray(188)
        while (keepGoing()) {
            val first = stream.read()
            if (first < 0) return
            if (first != 0x47) continue // lost sync: skip until the next sync byte
            p[0] = 0x47
            if (!readFully(stream, p, 1, 187)) return
            if (accept(p)) onSegment()
        }
    }

    /** Takes one packet; returns true when it closed a segment. */
    @Synchronized fun accept(p: ByteArray): Boolean {
        val pid = ((p[1].toInt() and 0x1F) shl 8) or (p[2].toInt() and 0xFF)
        val start = p[1].toInt() and 0x40 != 0
        when {
            pid == 0 && start -> { pat = p.copyOf(); parsePat(p) }
            pid == pmtPid && start -> { pmt = p.copyOf(); parsePmt(p) }
        }
        if (pid == 0 || pid == pmtPid) return false // prepended to every segment instead
        // segment lengths come from the video's own clock (PTS): a provider sends a burst at first, faster than real time
        val pts = if (start && pid == videoPid) pts(p) else -1L
        val keyframe = start && pid == videoPid && randomAccess(p)
        val now = clock()
        var closed = false
        if (current.size() > 0) {
            val byPts = pts >= 0 && startPts >= 0
            val elapsedMs = if (byPts) ((pts - startPts) and PTS_MASK) / 90 else now - startedAt
            if ((keyframe && elapsedMs >= targetMs) || now - startedAt >= targetMs * 4) {
                segments.addLast(Segment(seq++, elapsedMs.coerceAtLeast(1) / 1000.0, current.toByteArray()))
                while (segments.size > keep) segments.removeFirst()
                current = ByteArrayOutputStream()
                closed = true
            }
        }
        if (current.size() == 0) {
            // a segment only begins at a keyframe, once the stream's tables are known, so the TV can decode it on its own
            if (pat == null || pmt == null || (videoPid >= 0 && !keyframe)) return closed
            current.write(pat!!); current.write(pmt!!)
            startedAt = now; startPts = pts
        }
        current.write(p)
        return closed
    }

    /** Presentation timestamp (90 kHz) of the PES packet starting in [p], or -1. */
    private fun pts(p: ByteArray): Long {
        val i = payloadStart(p)
        if (i + 14 > 188 || p[i].toInt() != 0 || p[i + 1].toInt() != 0 || p[i + 2].toInt() != 1) return -1
        if ((p[i + 7].toInt() and 0x80) == 0) return -1
        val b = { k: Int -> (p[i + 9 + k].toLong() and 0xFF) }
        return ((b(0) shr 1) and 0x07 shl 30) or (b(1) shl 22) or ((b(2) shr 1) shl 15) or (b(3) shl 7) or (b(4) shr 1)
    }

    private fun payloadStart(p: ByteArray): Int {
        val afc = (p[3].toInt() shr 4) and 0x3
        return if (afc and 0x2 != 0) 5 + (p[4].toInt() and 0xFF) else 4
    }

    private fun randomAccess(p: ByteArray) = ((p[3].toInt() shr 4) and 0x2) != 0 && (p[4].toInt() and 0xFF) > 0 && (p[5].toInt() and 0x40) != 0

    private fun parsePat(p: ByteArray) {
        val s = payloadStart(p) + 1 + (p[payloadStart(p)].toInt() and 0xFF) // skip pointer field
        val len = ((p[s + 1].toInt() and 0x0F) shl 8) or (p[s + 2].toInt() and 0xFF)
        var i = s + 8
        while (i + 4 <= minOf(s + 3 + len - 4, 188)) {
            val program = ((p[i].toInt() and 0xFF) shl 8) or (p[i + 1].toInt() and 0xFF)
            if (program != 0) { pmtPid = ((p[i + 2].toInt() and 0x1F) shl 8) or (p[i + 3].toInt() and 0xFF); return }
            i += 4
        }
    }

    private fun parsePmt(p: ByteArray) {
        val s = payloadStart(p) + 1 + (p[payloadStart(p)].toInt() and 0xFF)
        val len = ((p[s + 1].toInt() and 0x0F) shl 8) or (p[s + 2].toInt() and 0xFF)
        val end = minOf(s + 3 + len - 4, 188)
        var i = s + 12 + (((p[s + 10].toInt() and 0x0F) shl 8) or (p[s + 11].toInt() and 0xFF))
        while (i + 5 <= end) {
            val type = p[i].toInt() and 0xFF
            val pid = ((p[i + 1].toInt() and 0x1F) shl 8) or (p[i + 2].toInt() and 0xFF)
            if (type in VIDEO_TYPES) { videoPid = pid; return }
            i += 5 + (((p[i + 3].toInt() and 0x0F) shl 8) or (p[i + 4].toInt() and 0xFF))
        }
    }

    private fun readFully(s: InputStream, b: ByteArray, off: Int, len: Int): Boolean {
        var n = 0
        while (n < len) { val r = s.read(b, off + n, len - n); if (r < 0) return false; n += r }
        return true
    }

    private companion object {
        val VIDEO_TYPES = setOf(0x01, 0x02, 0x1B, 0x24) // MPEG-1/2, H.264, HEVC
        const val PTS_MASK = 0x1FFFFFFFFL // 33-bit, wraps every ~26 h
    }
}
