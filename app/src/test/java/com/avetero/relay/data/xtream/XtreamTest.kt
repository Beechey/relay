package com.avetero.relay.data.xtream

import com.avetero.relay.data.model.Account
import com.avetero.relay.data.model.Channel
import com.avetero.relay.data.model.Episode
import com.avetero.relay.data.model.Programme
import com.avetero.relay.data.model.Vod
import com.avetero.relay.data.update.Release
import com.avetero.relay.data.update.compareVersions
import com.avetero.relay.data.update.parseRelease
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamTest {
    private val x = Xtream(Account("p", "provider.example:8080/", "us er", "p&ss"))

    @Test fun urls() {
        assertEquals(
            "http://provider.example:8080/player_api.php?username=us%20er&password=p%26ss&action=get_live_streams",
            x.apiUrl("get_live_streams"),
        )
        assertEquals("http://provider.example:8080/live/us%20er/p%26ss/42.ts", x.streamUrl(Channel(42, 1, "", "")))
        assertEquals("http://provider.example:8080/live/us%20er/p%26ss/42.m3u8", x.streamUrl(Channel(42, 1, "", ""), hls = true))
        assertEquals("http://provider.example:8080/movie/us%20er/p%26ss/9.mkv", x.vodUrl(Vod(9, "", "", 0.0, "", "mkv", 0)))
        assertEquals("http://provider.example:8080/series/us%20er/p%26ss/5.mp4", x.episodeUrl(Episode(5, 1, 1, "", "mp4", "", "", "")))
    }

    @Test fun channels() {
        val list = parseChannels("""[{"num":"3","name":" News ","stream_id":7,"category_id":"2","stream_icon":null,"tv_archive":1},{"name":"bad"}]""")
        assertEquals(listOf(Channel(7, 3, "News", "2", "", true)), list)
    }

    @Test fun vods() {
        val list = parseVods("""[{"stream_id":"11","name":"Film","stream_icon":"http://i/p.jpg","rating":"7.5","category_id":3,"container_extension":"mkv","added":"1700000000"},
            {"stream_id":12,"name":"NoExt","rating":"","container_extension":null}]""")
        assertEquals(Vod(11, "Film", "http://i/p.jpg", 7.5, "3", "mkv", 1700000000), list[0])
        assertEquals("mp4", list[1].ext)
        assertEquals(0.0, list[1].rating, 0.0)
    }

    @Test fun seriesEpisodesObjectOrArray() {
        val ep = """{"id":"101","episode_num":2,"title":"Two","container_extension":"mkv","season":1,"info":{"plot":"p","duration":"00:45:00"}}"""
        val ep1 = """{"id":"100","episode_num":1,"title":"One","container_extension":"mp4","season":1,"info":[]}"""
        val asObject = parseSeriesInfo("""{"info":{"plot":"x","releaseDate":"2021-01-01"},"episodes":{"1":[$ep,$ep1]}}""")
        val asArray = parseSeriesInfo("""{"info":{},"episodes":[[$ep1,$ep]]}""")
        assertEquals(listOf(100, 101), asObject.episodes.map { it.id })
        assertEquals(asObject.episodes, asArray.episodes)
        assertEquals("2021", asObject.year)
    }

    @Test fun epg() {
        // "SGVsbG8=" = "Hello"
        val list = parseEpg(
            """{"epg_listings":[
              {"title":"Plain text!","description":"","start_timestamp":"200","stop_timestamp":"300"},
              {"title":"SGVsbG8=","description":"","start_timestamp":"100","stop_timestamp":"200"}]}"""
        )
        assertEquals(listOf("Hello", "Plain text!"), list.map { it.title })
        assertEquals(emptyList<Programme>(), parseEpg("""{"epg_listings":[]}"""))
    }

    @Test fun versions() {
        assertTrue(compareVersions("0.10.0", "0.9.0") > 0)
        assertTrue(compareVersions("v1.0.0", "1.0.0-beta.2") > 0)
        assertEquals(0, compareVersions("1.2", "1.2.0"))
        assertTrue(compareVersions("0.1.0", "0.1.1") < 0)
    }

    @Test fun release() {
        val r = parseRelease(JSONObject("""{"tag_name":"v0.2.0","body":"notes","published_at":"2026-10-19T10:00:00Z",
            "assets":[{"name":"notes.txt","url":"u0"},{"name":"app-release.apk","url":"https://api/asset/1","browser_download_url":"https://dl","size":14200000}]}"""))!!
        assertEquals(Release("0.2.0", "notes", "https://api/asset/1", 14200000, "2026-10-19"), r)
    }

    @Test fun progressCounts() {
        val json = (1..600).joinToString(",", "[", "]") { """{"stream_id":"$it","name":"C$it"}""" }
        val counts = mutableListOf<Int>()
        assertEquals(600, parseChannels(java.io.StringReader(json)) { counts += it }.size)
        assertEquals(listOf(250, 500, 600), counts)

        val bytes = ByteArray(600 * 1024)
        var last = 0L
        Counting(bytes.inputStream(), bytes.size.toLong()) { read, total -> last = read; assertEquals(bytes.size.toLong(), total) }.readBytes()
        assertTrue(last >= 512 * 1024)
    }

    @Test fun catchupUrlUsesProviderTimeZone() {
        val x = Xtream(Account("p", "http://host:8080", "u", "p w"))
        val start = 1793215800L // 2026-10-28 19:30 UTC
        assertEquals("http://host:8080/timeshift/u/p%20w/30/2026-10-28:19-30/7.ts", x.catchupUrl(Channel(7, 1, "C", "1"), start, 30, "UTC"))
        assertTrue(x.catchupUrl(Channel(7, 1, "C", "1"), start, 30, "Europe/Berlin").contains("/2026-10-28:20-30/"))
    }

    @Test fun downloadsOnlyTheKeptCategoriesAsOneList() {
        // a bare-bones HTTP server: one canned JSON reply per category_id
        val server = java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        Thread {
            while (!server.isClosed) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                Thread {
                    sock.use {
                        val line = it.getInputStream().bufferedReader().readLine() ?: return@use
                        val cat = line.substringAfter("category_id=", "").substringBefore('&').substringBefore(' ')
                        val body = when (cat) {
                            "1" -> """[{"stream_id":10,"num":1,"name":"BBC One","category_id":"1"},{"stream_id":11,"num":2,"name":"BBC Two","category_id":"1"}]"""
                            "2" -> "[]"
                            "3" -> """[{"stream_id":30,"num":3,"name":"Sky News","category_id":"3"}]"""
                            else -> """{"user_info":{"auth":0}}"""
                        }.toByteArray()
                        it.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(body); flush()
                        }
                    }
                }.start()
            }
        }.start()
        try {
            val out = java.io.File.createTempFile("live", ".json")
            val x = Xtream(Account("t", "http://127.0.0.1:${server.localPort}", "u", "p"))
            x.downloadCategories("get_live_streams", listOf("1", "2", "3", "9"), out) { _, _ -> }
            assertEquals(listOf(10, 11, 30), parseChannels(out.readText()).map { it.id })
        } finally { server.close() }
    }

    @Test fun arrayBodyIgnoresErrorObjects() {
        assertEquals("", arrayBody("""{"error":"x"}"""))
        assertEquals("", arrayBody(" [ ] "))
        assertEquals("{\"a\":1}", arrayBody("[{\"a\":1}]"))
    }
}
