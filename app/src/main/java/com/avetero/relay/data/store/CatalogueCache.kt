package com.avetero.relay.data.store

import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.Series
import com.avetero.relay.data.model.Vod
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Compact binary copies of the catalogue: only the fields Relay uses, read back in seconds instead of re-parsing
 * the provider's JSON (often 100+ MB) on every start. Written after each download; the JSON stays as the source.
 */
object CatalogueCache {
    private const val VERSION = 1

    fun writeChannels(f: File, list: List<Channel>) = write(f, list) { c -> writeInt(c.id); writeInt(c.num); writeUTF(c.name); writeUTF(c.categoryId); writeUTF(c.icon); writeBoolean(c.archive); writeUTF(c.epgId) }
    fun readChannels(f: File) = read(f) { s -> Channel(readInt(), readInt(), readUTF(), s(readUTF()), readUTF(), readBoolean(), s(readUTF())) }

    fun writeVods(f: File, list: List<Vod>) = write(f, list) { v -> writeInt(v.id); writeUTF(v.name); writeUTF(v.icon); writeDouble(v.rating); writeUTF(v.categoryId); writeUTF(v.ext); writeLong(v.added) }
    fun readVods(f: File) = read(f) { s -> Vod(readInt(), readUTF(), readUTF(), readDouble(), s(readUTF()), s(readUTF()), readLong()) }

    fun writeSeries(f: File, list: List<Series>) = write(f, list) { v -> writeInt(v.id); writeUTF(v.name); writeUTF(v.cover); writeDouble(v.rating); writeUTF(v.categoryId); writeUTF(v.plot); writeUTF(v.year) }
    fun readSeries(f: File) = read(f) { s -> Series(readInt(), readUTF(), readUTF(), readDouble(), s(readUTF()), readUTF(), s(readUTF())) }

    private fun <T> write(f: File, list: List<T>, item: DataOutputStream.(T) -> Unit) {
        val tmp = File(f.path + ".part")
        DataOutputStream(tmp.outputStream().buffered(256 * 1024)).use { o ->
            o.writeInt(VERSION); o.writeInt(list.size)
            list.forEach { o.item(it) } // a field over 64 KB fails the write; the caller then keeps using the JSON
        }
        tmp.renameTo(f)
    }

    /** Null when the file is missing, from an older format, or unreadable: the caller falls back to the JSON. */
    private fun <T> read(f: File, item: DataInputStream.((String) -> String) -> T): List<T>? = runCatching {
        DataInputStream(f.inputStream().buffered(256 * 1024)).use { i ->
            if (i.readInt() != VERSION) return null
            val n = i.readInt()
            val shared = HashMap<String, String>() // category ids and extensions repeat thousands of times
            val intern = { s: String -> shared.getOrPut(s) { s } }
            ArrayList<T>(n).apply { repeat(n) { add(i.item(intern)) } }
        }
    }.getOrNull()
}
