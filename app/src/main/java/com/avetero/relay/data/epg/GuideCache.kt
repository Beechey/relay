package com.avetero.relay.data.epg

import com.avetero.relay.data.model.Programme
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** Compact binary cache so the guide loads quickly on the next start. */
fun writeGuide(file: File, guide: Guide) {
    val tmp = File(file.path + ".part")
    DataOutputStream(tmp.outputStream().buffered(64 * 1024)).use { o ->
        o.writeInt(guide.size)
        guide.forEach { (id, list) ->
            o.writeUTF(id); o.writeInt(list.size)
            list.forEach { p -> o.writeLong(p.start); o.writeLong(p.end); o.writeUTF(p.title.take(500)); o.writeUTF(p.description) }
        }
    }
    tmp.renameTo(file)
}

fun readGuide(file: File, now: Long): Guide = DataInputStream(file.inputStream().buffered(64 * 1024)).use { i ->
    val strings = HashMap<String, String>()
    val n = i.readInt()
    val out = HashMap<String, List<Programme>>(n * 2)
    repeat(n) {
        val id = i.readUTF()
        val count = i.readInt()
        val list = ArrayList<Programme>(count)
        repeat(count) {
            val start = i.readLong(); val end = i.readLong(); val t = i.readUTF(); val d = i.readUTF()
            if (end > now - 2 * 3600) list.add(Programme(strings.getOrPut(t) { t }, d, start, end))
        }
        if (list.isNotEmpty()) out[id] = list
    }
    out
}
