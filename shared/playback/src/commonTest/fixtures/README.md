# Playlist fixtures

HLS playlists for the playback tests. They were captured from live Twitch streams and then anonymised. Nothing here can be fetched or traced back to a stream, account or viewer.

## Files

`twitch/`

- `master.m3u8`: a master playlist with the six captured variants, 1080p60 down to 160p30, plus an `audio_only` variant. The last two lines (the `audio_only` `#EXT-X-STREAM-INF` and its URL) are hand-added, because the captured masters had no audio-only rendition.
- `media-live.m3u8`: a media playlist with no ad markers.
- `media-cue.m3u8`: a media playlist carrying a `twitch-maf-ad` cue.
- `cue-break/`: 19 consecutive refreshes around one cue break. The first two and the last two have no cue. The file name is `NNN-<ms>.m3u8`, where `<ms>` is the original fetch time in milliseconds, so a replay test can keep the real spacing (about 6 s).
- `media-stitched-preroll.m3u8`: a signed-in playlist with a stitched `PREROLL`, followed by live segments.
- `media-stitched-midroll-pod.m3u8`: a signed-in playlist with a `MIDROLL` pod of two ads (`X-TV-TWITCH-AD-POD-LENGTH="2"`), followed by live segments.
- `media-stitched-title-only.m3u8`: **hand-built.** It is `media-live.m3u8` with three segment titles changed to `Amazon|0000000000`. It has no stitched `DATERANGE`, so only the segment titles mark the ads.

`broken/`

- `truncated.m3u8`: a media playlist cut in the middle of a line.
- `html.m3u8`: an HTML error page.
- `empty.m3u8`: an empty file.

The two stitched playlists came from signed-in captures made without the proxy. The others came from direct fetches while signed out.

## Anonymising

All playlists went through the project's capture anonymiser first, which masks IP addresses, session values and long token-like strings. A second pass applied these rules:

- `#EXT-X-SESSION-DATA` keeps only `NODE` (set to `video-edge.example`), `SERVER-TIME`, `USER-COUNTRY` (set to `GB`) and `FUTURE`. Every other line is dropped.
- `#EXT-X-DATERANGE` keeps `ID`, `CLASS`, `START-DATE`, `DURATION`, `PLANNED-DURATION`, `END-ON-NEXT`, `X-SERVER-TIME`, `X-TV-TWITCH-AD-ROLL-TYPE`, `X-TV-TWITCH-AD-POD-LENGTH`, `X-TV-TWITCH-AD-POD-POSITION` and `X-TV-TWITCH-STREAM-SOURCE`. Every other `X-*` attribute keeps its name and gets the value `"0"`. Attributes without an `X-` prefix are dropped.
- Numbers of nine or more digits in an `ID` become `1000000000` and up. The same original number gets the same stand-in in every file. Where the anonymiser had already blanked an `ID`, a unique one was made up (`stitched-ad-…`).
- Segment URLs become `https://video-edge.example/v1/segment/<n>.ts`, where `<n>` is the media sequence number plus the position in the file, so a segment keeps its number from one refresh to the next. Variant URLs become `https://video-weaver.example/v1/playlist/<variant>.m3u8`. No URL has a query string.
- Segment titles of the form `Amazon|<id>` become `Amazon|0000000000`.

The attribute names for tokens, click tracking and session IDs are still there, each with the value `"0"`. That is on purpose: a parser should skip them.

Playlists are plain text. Add new ones only after running both passes and the leak scan.
