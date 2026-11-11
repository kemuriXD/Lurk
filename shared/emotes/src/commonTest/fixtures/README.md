# Emote fixtures

Test data for the 7TV, BetterTTV and FrankerFaceZ parsers. User, channel and emote-set ids, user names and emote owners are fake and consistent across files (Twitch channel id 1000500, Kick channel id 1000600, user `chan_one`, owner `emote_owner`). Emote ids, shared-pool emote names and image URLs on `cdn.7tv.app`, `cdn.betterttv.net` and `cdn.frankerfacez.com` are the public ones. Channel emotes that carried a channel name are renamed (`chanPls`, `chanDance`, `chanHappyLeft`). No URL has a query string.

Tests read these files with `fixture("<path>")`, which resolves against `src/commonTest/fixtures`.

## Responses from the public APIs

Each endpoint was fetched once without authentication for a large public Twitch channel, then cut to about six emotes that cover the cases below.

| File | Endpoint |
| --- | --- |
| `seventv/global.json` | `7tv.io/v3/emote-sets/global` |
| `seventv/user-twitch.json` | `7tv.io/v3/users/twitch/<id>` |
| `seventv/user-kick.json` | `7tv.io/v3/users/kick/<id>` (a Kick account with a 7TV profile) |
| `seventv/emote-set.json` | `7tv.io/v3/emote-sets/<id>` |
| `bttv/global.json` | `api.betterttv.net/3/cached/emotes/global` |
| `bttv/user.json` | `api.betterttv.net/3/cached/users/twitch/<id>` |
| `ffz/global.json` | `api.frankerfacez.com/v1/set/global` |
| `ffz/room.json` | `api.frankerfacez.com/v1/room/id/<id>` |

Edits made to the responses, besides the ids above:

- Emote owners, the set owner and the user profile are replaced with fake objects. Editors, roles and avatars are dropped.
- 7TV `host.files` keeps only the WebP files, and `data.tags` is emptied.
- In `seventv/user-twitch.json`, the last emote (`Thinking`) has `data.flags` set to 65536 by hand. The live responses carried no content flag. All other flags are as served.
- BTTV and FFZ uploader fields are replaced with fake users.

Coverage:

- animated and static emotes
- 7TV zero-width emotes (`flags & 1`, with `data.flags` 256)
- 7TV `listed: false`
- the 7TV content flag (hand-edited, see above)
- BTTV zero-width names `cvMask` and `cvHazmat` (the list is in `config/remote.json`)
- FFZ modifier emotes (`ffzHyper`, `ffzRainbow`, `ffzW`, with `modifier_flags`)
- an FFZ `hidden` emote (`ZrehplaR`)

## seventv/eventapi

Messages from the 7TV EventAPI v3 (`wss://events.7tv.io/v3`).

Captured from a five minute session subscribed to several large Twitch channels, then anonymised:

- `hello.json`, `heartbeat.json`
- `dispatch-cosmetic-paint.json`, `dispatch-cosmetic-badge.json`
- `dispatch-entitlement-paint.json`, `dispatch-entitlement-emote-set.json`
- the `pushed` half of `dispatch-emote-set-update.json`

Hand-built from the published EventAPI opcode documentation, not captured:

- `ack-resume.json`, `reconnect.json`, `end-of-stream.json`
- `dispatch-entitlement-delete.json` (the paint entitlement with the type changed to `entitlement.delete`)
- the `pulled` half of `dispatch-emote-set-update.json` (the capture had no `pulled` entries)

## broken

- `seventv-one-bad-emote.json`: a valid emote set where the second emote has no `data` field.
- `html.json`: an HTML error page, as returned by a failing proxy.
