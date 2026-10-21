package com.avetero.relay.data.epg

import com.avetero.relay.data.model.Programme
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** The full guide: programmes per XMLTV channel id (a channel's epg_channel_id), sorted by start. */
typealias Guide = Map<String, List<Programme>>

/**
 * Streams an XMLTV document (plain or gzip), keeping only programmes for [wanted] channel ids that overlap [from]..[to]
 * (epoch seconds). The file can be hundreds of MB, so nothing is held but what's kept.
 */
fun parseXmltv(input: InputStream, wanted: Set<String>, from: Long, to: Long, onCount: (Int) -> Unit = {}): Guide {
    val raw = BufferedInputStream(input, 64 * 1024)
    raw.mark(2)
    val gzip = raw.read() == 0x1f && raw.read() == 0x8b
    raw.reset()
    val stream = if (gzip) GZIPInputStream(raw, 64 * 1024) else raw

    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(stream, null)
    val out = HashMap<String, ArrayList<Programme>>()
    val strings = HashMap<String, String>() // titles repeat endlessly; share one copy each
    var kept = 0

    var channel: String? = null
    var start = 0L
    var end = 0L
    var title = ""
    var desc = ""
    var field: String? = null
    while (true) {
        when (parser.next()) {
            XmlPullParser.END_DOCUMENT -> break
            XmlPullParser.START_TAG -> when (parser.name) {
                "programme" -> {
                    val ch = parser.getAttributeValue(null, "channel")
                    start = xmltvTime(parser.getAttributeValue(null, "start"))
                    end = xmltvTime(parser.getAttributeValue(null, "stop"))
                    channel = if (ch != null && ch in wanted && end > from && start < to && end > start) ch else null
                    title = ""; desc = ""
                }
                "title", "desc" -> if (channel != null) field = parser.name
            }
            XmlPullParser.TEXT -> when (field) {
                "title" -> if (title.isEmpty()) title = parser.text.trim()
                "desc" -> if (desc.isEmpty()) desc = parser.text.trim().take(300)
            }
            XmlPullParser.END_TAG -> when (parser.name) {
                "title", "desc" -> field = null
                "programme" -> {
                    channel?.let { ch ->
                        val t = strings.getOrPut(title) { title }
                        out.getOrPut(ch) { ArrayList() }.add(Programme(t, desc, start, end))
                        if (++kept % 500 == 0) onCount(kept)
                    }
                    channel = null
                }
            }
        }
    }
    onCount(kept)
    return out.mapValues { (_, list) -> list.sortedBy { it.start } }
}

private val xmltvFormats = ThreadLocal.withInitial {
    listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss").map { SimpleDateFormat(it, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false } }
}

/** "20261021200000 +0100" (offset optional, UTC if missing) to epoch seconds; 0 if unreadable. */
internal fun xmltvTime(s: String?): Long {
    if (s.isNullOrBlank()) return 0
    val v = s.trim()
    for (f in xmltvFormats.get()!!) {
        val pos = ParsePosition(0)
        val d = f.parse(v, pos) ?: continue
        if (pos.index > 0) return d.time / 1000
    }
    return 0
}
