# Demo server

A tiny fake Xtream Codes server for screenshots, demos and testing Relay without a real provider. It serves
40 made-up channels in 7 groups with a rolling TV guide, 9 films and 3 series, all invented. Every poster, still,
logo and the playback clip is drawn by `make_media.py`, so nothing in it belongs to anyone else: no real channels,
brands, films or footage.

Standard library only for the server; the media script needs [Pillow](https://pypi.org/project/pillow/) and
`ffmpeg`.

```bash
cd tools/demo-server
python3 make_media.py     # once: writes media/ (posters, stills, logos, clip.mp4, clip.ts)
python3 server.py         # http://0.0.0.0:8080, or: python3 server.py 8081
```

In Relay, add a playlist with:

| | |
|---|---|
| Server URL | `http://10.0.2.2:8080` from an Android emulator, or your computer's LAN address from a real device |
| Username | `demo` |
| Password | `demo` |

What it answers:

- `player_api.php`: login (`user_info` and `server_info`), `get_live_categories`, `get_live_streams`, `get_vod_categories`,
  `get_vod_streams`, `get_vod_info`, `get_series_categories`, `get_series`, `get_series_info` and `get_short_epg`;
  list actions honour `category_id`.
- `xmltv.php`: the full guide, from two hours ago to a day ahead, regenerated on each request.
- Streams: `/live/…/<id>.ts` and `.m3u8`, `/timeshift/…`, `/movie/…/<id>.<ext>` and `/series/…/<id>.<ext>` all play
  the same two-minute generated clip (with HTTP range support, so seeking works).

Run the self-check with `python3 -m unittest test_server` (no media needed).

Watch history in Relay is kept per server address and username, so to start a demo from a clean slate, run the
server on a different port and add the playlist again.
