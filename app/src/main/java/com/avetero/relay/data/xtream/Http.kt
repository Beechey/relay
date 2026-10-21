package com.avetero.relay.data.xtream

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** [progress] gets (bytes read, total bytes or -1 when the server doesn't say), at most every 256 KB. */
internal fun <T> request(url: String, ua: String = "IPTVSmartersPlayer", progress: ((Long, Long) -> Unit)? = null, read: (InputStream) -> T): T = (URL(url).openConnection() as HttpURLConnection).run {
    connectTimeout = 15_000
    readTimeout = 120_000
    // some panels reject unknown user agents
    setRequestProperty("User-Agent", ua)
    try {
        if (responseCode !in 200..299) throw IOException("Server returned HTTP $responseCode")
        inputStream.use { input -> read(if (progress == null) input else Counting(input, contentLengthLong, progress)) }
    } finally {
        disconnect()
    }
}

internal fun fetchText(url: String): String = request(url) { it.bufferedReader().readText() }

/** Streams to a temp file, then renames, so a failed download never replaces a good cache. */
internal fun download(url: String, to: File, progress: ((Long, Long) -> Unit)? = null) {
    val tmp = File(to.path + ".part")
    request(url, progress = progress) { input -> tmp.outputStream().use { input.copyTo(it, 64 * 1024) } }
    if (!tmp.renameTo(to)) throw IOException("Couldn't save ${to.name}")
}

/**
 * Streams a top-level JSON array of objects, handing each object's scalar fields (as strings) to [item].
 * Nested values are skipped. Anything that isn't an array (panel error objects) yields an empty list.
 */
/** Counts bytes as they're read, for progress bars. */
internal class Counting(input: InputStream, private val total: Long, private val progress: (Long, Long) -> Unit) : FilterInputStream(input) {
    private var read = 0L
    private var reported = 0L
    private fun add(n: Int) { if (n > 0) { read += n; if (read - reported >= 256 * 1024) { reported = read; progress(read, total) } } }
    override fun read(): Int = super.read().also { if (it >= 0) add(1) }
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { add(it) }
}
