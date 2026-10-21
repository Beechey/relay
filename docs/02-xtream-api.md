# 02 — Xtream Codes API

All calls are `GET {server}/player_api.php?username={u}&password={p}[&action=…]`. The server is usually `http://host:port`, so cleartext must be allowed. URL-encode user and pass (`enc()` in `Xtream.kt` does this, using `%20` rather than `+` so the result is also valid in paths). Send `User-Agent: IPTVSmartersPlayer`, because some panels block unknown agents.

## Auth / account — no action

```json
{
  "user_info": {
    "username": "u", "password": "p", "auth": 1, "status": "Active",
    "exp_date": "1804982400", "is_trial": "0",
    "active_cons": "1", "max_connections": "2",
    "allowed_output_formats": ["m3u8", "ts"]
  },
  "server_info": {
    "url": "provider.example", "port": "8080", "https_port": "8443",
    "server_protocol": "http", "timezone": "Europe/London",
    "timestamp_now": 1792609200, "time_now": "2026-10-21 20:40:00"
  }
}
```

- `auth` is `0` or `1`. It can be an int or a string, so use `optInt`.
- `status` is `Active`, `Expired`, `Banned` or `Disabled`.
- `exp_date` is epoch seconds as a string, or `null` for an account that never expires. Shown in boards 03 and 14.
- `active_cons` / `max_connections` → Settings "Connections 1 of 2" (14).
- `allowed_output_formats` picks the default live format: prefer `ts` and fall back to `m3u8`.
- `server_info.timestamp_now` gives the clock skew. Use it to correct EPG "now" when the device clock is off.
- An HTML or non-JSON response means it isn't an Xtream server. A missing `user_info` means wrong credentials on some panels.

## Live

| Action | Returns |
|---|---|
| `get_live_categories` | `[{"category_id":"2","category_name":"News","parent_id":0}]` |
| `get_live_streams` | all channels (add `&category_id=2` to filter, which we don't need) |

```json
{"num": 101, "name": "News 24", "stream_type": "live", "stream_id": 7,
 "stream_icon": "http://…/logo.png", "epg_channel_id": "news24.uk",
 "added": "1700000000", "category_id": "2", "category_ids": [2],
 "tv_archive": 1, "tv_archive_duration": "7", "direct_source": ""}
```

- **Stream URL:** `{server}/live/{u}/{p}/{stream_id}.ts`, or `.m3u8` for HLS (Settings → Live stream format).
- `tv_archive == 1` means catch-up is available for `tv_archive_duration` days. Show the "Catch-up" badge on board 05.
- `num` and `stream_id` can be strings. `stream_icon` can be `null`, `""` or a broken URL. Fall back to the text logo badge from the design.
- `direct_source`, when non-empty, is a direct URL that some panels expect you to use. Ignore it unless the normal URL fails.

## EPG

### Short EPG — `get_short_epg&stream_id=7&limit=6`

```json
{"epg_listings": [
  {"id": "1", "epg_id": "1", "title": "RXZlbmluZyBIZWFkbGluZXM=", "lang": "en",
   "start": "2026-10-21 20:00:00", "end": "2026-10-21 21:00:00",
   "description": "VGhlIGRheSdzIHRvcCBzdG9yaWVz…", "channel_id": "news24.uk",
   "start_timestamp": "1792605600", "stop_timestamp": "1792609200"}
]}
```

- `title` and `description` are **base64**. Decode with strict `Base64.getDecoder()` and keep the raw string if decoding fails (`parseEpg` already does this).
- Use `start_timestamp`/`stop_timestamp` (UTC epoch). **Do not** use `start`/`end`, which are in the server's timezone.
- `get_simple_data_table&stream_id=` returns the full day for one channel. It's an alternative when xmltv is too large.

### Full guide — `{server}/xmltv.php?username={u}&password={p}`

Standard XMLTV: `<channel id>` plus `<programme start="20261021200000 +0000" stop=… channel="news24.uk"><title>…</title><desc>…</desc></programme>`. Map programmes to streams through `epg_channel_id`. The file can be 50–300 MB, so stream-parse it and never load it whole.

### Catch-up / timeshift URL

```
{server}/timeshift/{u}/{p}/{duration_minutes}/{start:yyyy-MM-dd:HH-mm}/{stream_id}.ts
```

`start` is in **server timezone** (`server_info.timezone`). Some panels use `/streaming/timeshift.php?username=&password=&stream=&start=&duration=` instead, so try the path form first.

## Movies (VOD)

| Action | Returns |
|---|---|
| `get_vod_categories` | same shape as live categories |
| `get_vod_streams` | all movies |
| `get_vod_info&vod_id=12` | detail for board 08 |

```json
{"num": 1, "name": "The Long Road Home", "stream_type": "movie", "stream_id": 12,
 "stream_icon": "http://…/poster.jpg", "rating": "7.8", "rating_5based": 3.9,
 "added": "1792000000", "category_id": "40", "container_extension": "mkv"}
```

`get_vod_info`:

```json
{"info": {"name": "…", "plot": "…", "cast": "…", "director": "…", "genre": "Drama, Adventure",
          "releasedate": "2023-05-01", "duration_secs": 7440, "duration": "02:04:00",
          "backdrop_path": ["http://…"], "movie_image": "http://…", "rating": "7.8",
          "youtube_trailer": "abc123", "audio": {…}, "video": {…}},
 "movie_data": {"stream_id": 12, "name": "…", "container_extension": "mkv", "added": "…"}}
```

- **Stream URL:** `{server}/movie/{u}/{p}/{stream_id}.{container_extension}`
- `backdrop_path` can be an array or a string. `rating` can be `""`. `info` can be `[]` (an empty array, not an object) when the panel has no metadata, so handle that.
- Use `youtube_trailer` for the "Trailer" button on 08: open it with `Intent.ACTION_VIEW https://www.youtube.com/watch?v=…`. Hide the button if it's empty.

## Series

| Action | Returns |
|---|---|
| `get_series_categories` | categories |
| `get_series` | all shows |
| `get_series_info&series_id=5` | seasons + episodes for board 11 |

```json
{"num": 1, "name": "Harbour Lights", "series_id": 5, "cover": "http://…",
 "plot": "…", "genre": "Crime", "releaseDate": "2021-03-01", "rating": "8.2",
 "backdrop_path": ["…"], "category_id": "60", "last_modified": "1791000000"}
```

`get_series_info`:

```json
{"seasons": [{"season_number": 1, "name": "Season 1", "episode_count": "8", "cover": "…"}],
 "info": {"name": "Harbour Lights", "plot": "…", "cast": "…", "genre": "Crime", "rating": "8.2"},
 "episodes": {"1": [{"id": "9001", "episode_num": 1, "title": "Slack Water",
                     "container_extension": "mp4", "season": 1,
                     "info": {"plot": "…", "duration_secs": 2880, "movie_image": "…"}}]}}
```

- **Stream URL:** `{server}/series/{u}/{p}/{episode id}.{container_extension}`
- `episodes` is an object keyed by season number **as a string**, and on some panels a JSON **array** instead. Handle both.
- `seasons` can be empty while `episodes` has data. If so, build the season tabs from the `episodes` keys.
- Episode `id` is a string.

## General quirks checklist

- Every numeric field may arrive as a string. Always use `optInt`/`optLong`/`toLongOrNull`.
- `null` fields: `org.json` `optString` returns `"null"`, so check `isNull(key)` first.
- Names may have leading or trailing spaces and prefixes like `UK: `. Trim them and keep the prefix.
- Empty catalogs come back as `[]`, not as errors.
- HTTP 403/401 → credentials changed. 512/513/884 → provider-specific "max connections reached", so show that text.
- Redirects: `HttpURLConnection` follows same-protocol redirects only. Handle a 301/302 from http to https by hand if needed.
- Timeouts: 15 s connect, 60 s read. `get_vod_streams` is slow on big panels.
