# Kick fixtures

Responses from Kick's web API, anonymised. Nothing here can be fetched, and no account, viewer or channel is named.

## Files

`api/`

- `channel-live.json`: the shape of a signed-out `GET /api/v2/channels/{slug}` response for a live channel, with stand-in slug, IDs, titles, user and image paths. The `playback_url` is a placeholder with `token=0`. Lists are trimmed to 3 items or fewer.
- `channel-offline.json`: **hand-built** from the live one. `livestream` is null and `playback_url` has no host.
- `challenge.html`: **hand-built.** A minimal Cloudflare interstitial page containing the `Just a moment` title and a challenge-platform script reference.

`discovery/`

- `top-live.json`: the shape of a signed-out `GET web.kick.com/api/v1/livestreams` reply, trimmed to 3 items. The second item is **hand-broken** (`channel` is a number) to prove a bad item is skipped. The third has null optional fields.
- `top-live-last.json`: **hand-built** last page, with a null `next_cursor`.
- `categories-page1.json`, `categories-page2.json`: the shape of `GET /api/v1/subcategories` pages (`current_page` and `last_page` drive the cursor). `category_id` is deliberately different from `id`: it is the parent group. Page 2 is **hand-built**.
- `search.json`: the shape of `GET search.kick.com/api/v1/search/enriched`, trimmed. One live channel has a matching `livestreams` entry, the other does not.

`account/`

- `followed-page1.json`, `followed-page2.json`: the shape of the signed-in `GET /api/v2/channels/followed` pages. Page 2 is **hand-built** (no `nextCursor`, and its last item leaves optional fields out).
- `user.json`: the shape of `GET /api/v1/user`, cut down to the fields that matter plus a few ignored ones. The email is a stand-in.
- `follow-ok.json`: **hand-built.** A plausible 200 body for the follow call, which the probe never sends. Only its method, path and bearer header are checked.

`realtime/`

- `realtime-connection.json`, `realtime-chat-connection.json`: the shape of the signed-out `POST web.kick.com/api/v1/realtime/connection` and `.../realtime/channels/{channelId}/chat/connection` replies. The server picked `centrifugo`; the socket host is `realtime.example`.
- `realtime-auth.json`: the shape of the signed-out `POST .../realtime/auth/connection` reply. The connection token is the stand-in `eyJ0.e30.sig`.
- `centrifugo-frames.txt`: one recorded frame per line from that socket: the connect reply, the five subscribe replies and the first three pushes. The client id, IDs, usernames, message text, colours, image URLs and timestamps are stand-ins.

`chat/`

- `history.json`: the shape of the signed-out `GET web.kick.com/api/v1/chat/{channelId}/history` reply (`chat_id` is the channel ID, not the chatroom ID), cut to 3 messages, newest first, with stand-in IDs, names, text, colours, image paths and timestamps. One message carries an `[emote:id:name]` token.
- `send-ok.json`: **hand-built.** A plausible 200 body for the send call, which the probe never makes. Only the request (method, path, bearer header, body) is checked.

`assets/`

- `emotes.json`: the shape of the signed-out `GET kick.com/emotes/{slug}` reply: an array whose first entry is the channel (with its `emotes`) followed by the `Global` and `Emojis` sets. Stand-in IDs and names; each set is cut to 3 emotes or fewer.
