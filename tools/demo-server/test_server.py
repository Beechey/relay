"""python3 -m unittest test_server   (from tools/demo-server; no media needed)"""
import json
import os
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from http.server import ThreadingHTTPServer

import server


class DemoServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.media = tempfile.mkdtemp()
        with open(os.path.join(cls.media, "clip.mp4"), "wb") as f:
            f.write(bytes(range(256)) * 4)
        server.MEDIA = cls.media
        cls.httpd = ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        threading.Thread(target=cls.httpd.serve_forever, daemon=True).start()
        cls.base = f"http://127.0.0.1:{cls.httpd.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()

    def get(self, path, headers=None):
        with urllib.request.urlopen(urllib.request.Request(self.base + path, headers=headers or {})) as r:
            return r.status, r.read()

    def api(self, query="", user="demo"):
        return json.loads(self.get(f"/player_api.php?username={user}&password=demo{query}")[1])

    def test_login(self):
        self.assertEqual(self.api()["user_info"]["auth"], 1)
        self.assertEqual(self.api(user="nope")["user_info"]["auth"], 0)

    def test_lists_and_details(self):
        self.assertEqual(len(self.api("&action=get_live_streams")), 40)
        self.assertEqual(len(self.api("&action=get_live_streams&category_id=1")), 6)
        self.assertEqual(self.api("&action=get_vod_info&vod_id=1004")["info"]["name"], "Northern Line")
        episodes = self.api("&action=get_series_info&series_id=2001")["episodes"]
        self.assertEqual([len(e) for e in episodes.values()], [3, 3])
        self.assertTrue(self.api("&action=get_short_epg&stream_id=101&limit=3")["epg_listings"])

    def test_guide_is_valid_xmltv(self):
        tv = ET.fromstring(self.get("/xmltv.php?username=demo&password=demo")[1])
        self.assertEqual(len(tv.findall("channel")), 40)
        self.assertGreater(len(tv.findall("programme")), 40 * 24)

    def test_streams_support_ranges(self):
        status, body = self.get("/movie/demo/demo/1001.mp4", {"Range": "bytes=10-19"})
        self.assertEqual((status, body), (206, bytes(range(10, 20))))

    def test_no_files_outside_media(self):
        with self.assertRaises(urllib.error.HTTPError):
            self.get("/media/../server.py")
        with self.assertRaises(urllib.error.HTTPError):
            self.get("/movie/demo/wrong/1001.mp4")


if __name__ == "__main__":
    unittest.main()
