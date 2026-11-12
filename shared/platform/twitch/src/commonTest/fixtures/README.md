# Twitch fixtures

Responses from Twitch's web GraphQL endpoint, anonymised. Nothing here can be fetched, and no account, viewer or channel is named.

## Files

`gql/`

- `user.json`: a signed-out raw-query response for `query { user(login: ...) { id login } }`, sent with the web Client-ID. This confirmed that the endpoint accepts raw queries without a persisted-query hash.
- `error-schema.json`: the response to the same query with a field that does not exist. It carries `errors` and no `data`.
- `playback-token-anon.json`: a signed-out raw `PlaybackAccessToken_Template` response for a live channel, live branch only, with `platform: "web"`, `playerBackend: "mediaplayer"` and `playerType: "site"`. The token `value` is itself a JSON string; it carries `subscriber:false`, `turbo:false` and `show_ads:true`.
- `playback-token-sub.json`: **hand-built** from the anonymous token, with `subscriber:true` and a stand-in `user_id`.
- `playback-token-turbo.json`: **hand-built** from the anonymous token, with `turbo:true`, `show_ads:false` and a stand-in `user_id`.
- `playback-token-unknown.json`: **hand-built.** `streamPlaybackAccessToken` is null, as for a login that does not exist.
- `error-integrity.json`: **hand-built.** It follows the shape of a recorded `failed integrity check` response to a signed-in follow mutation: an `errors` entry with `extensions.code` of `IntegrityCheckFailed`, a null mutation result under `data`, and `extensions.challenge.type` of `integrity`.

## Anonymising

The probed channel's login and ID became `streamer1` and `1000001`, and the request ID became `request-1`. In the playback tokens the signature is all zeros, the viewer IP is `192.0.2.1` (a documentation address), and the hand-built signed-in user is `1000002`. The web Client-ID is a public value that lives in `config/remote.json`; it appears nowhere in these files.
