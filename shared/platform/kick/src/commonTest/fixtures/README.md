# Kick fixtures

Responses from Kick's web API, anonymised. Nothing here can be fetched, and no account, viewer or channel is named.

## Files

`api/`

- `channel-live.json`: the shape of a signed-out `GET /api/v2/channels/{slug}` response for a live channel, with stand-in slug, IDs, titles, user and image paths. The `playback_url` is a placeholder with `token=0`. Lists are trimmed to 3 items or fewer.
- `channel-offline.json`: **hand-built** from the live one. `livestream` is null and `playback_url` has no host.
- `challenge.html`: **hand-built.** A minimal Cloudflare interstitial page containing the `Just a moment` title and a challenge-platform script reference.
