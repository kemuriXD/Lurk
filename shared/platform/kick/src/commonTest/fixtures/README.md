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
