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
- `top-streams.json`: the shape of a signed-out raw `TopStreams` response (`streams` edges with a `cursor`, a stream node and `pageInfo`), rebuilt with stand-in values and trimmed to 3 items. **Hand-edited:** the second node has `broadcaster: null` and the third has `game: null`.
- `games.json`: the shape of a `TopGames` response (`games` edges, each game with a box-art template and `viewersCount`), stand-in values, 3 items.
- `game-streams.json`: the shape of a `GameStreams` response (`game { streams }`), stand-in values, 3 streams, `hasNextPage` false.
- `search.json`: the shape of a `SearchFor` response (`searchFor` with `channels` and `games` edges, each under `item`), stand-in values, 3 channels and 2 games. **Hand-ordered:** the offline channel comes first and the two live ones after it.
- `channel-live.json` and `channel-offline.json`: the shape of a `ChannelProfile` response, stand-in values. The offline one differs from the live one only in a null `stream`. **Hand-built** from the recorded shape.
- `channel-unknown.json`: a `ChannelProfile` response for a login that does not exist (`user` is null), as recorded, with the request ID replaced.

## Anonymising

The probed channel's login and ID became `streamer1` and `1000001`, and the request ID became `request-1`. In the playback tokens the signature is all zeros, the viewer IP is `192.0.2.1` (a documentation address), and the hand-built signed-in user is `1000002`. The web Client-ID is a public value that lives in `config/remote.json`; it appears nowhere in these files.

In the discovery fixtures every login, display name, ID, title, bio and image path is a stand-in (`streamer1`, `Streamer One`, `1000001`, `Stream title 1`, `https://cdn.example/a/1.png`), and the follower and viewer counts are invented. Image URLs keep the `{width}x{height}` template the API returns.
- `current-user.json`: the shape of a signed-in `CurrentUser` response, recorded once, with the account replaced by `viewer1` and `Viewer One`. `current-user-null.json` is **hand-built**: `currentUser` is null, as for a rejected token.
- `follows.json`: the shape of a signed-in `Follows` response (`currentUser.follows` edges with a user node and its `stream`), rebuilt with stand-in values, 3 items. **Hand-built:** one offline entry and two live ones, listed in an order that needs sorting.
- `follows-page1.json` and `follows-page2.json`: **hand-built** two-page variant (`hasNextPage` true, then false), used to test paging.
- `emotes.json`: the shape of a signed-out `ChannelEmotes` response (`user(id:) { subscriptionProducts { emotes { id token } } }`), stand-in IDs and tokens, trimmed to 3 emotes. **Hand-edited:** a second, empty product was added to show that products are merged.
- `emotes-unknown.json`: **hand-built.** `user` is null, as for an ID Twitch does not know.
- `emotes-bad-item.json`: **hand-built** from `emotes.json`. One emote has no token, one has no ID, and the second product's `emotes` is null.
- `badges.json`: the shape of a signed-out `Badges` response (global `badges` plus `user(id:) { broadcastBadges }`, each with `setID version title imageURL`), stand-in titles and image URLs. **Hand-edited:** the channel list holds a `subscriber/0` badge that also exists globally, so the two images differ.
- `badges-unknown.json`: **hand-built.** `user` is null, with the global list still present.
- `badges-bad-item.json`: **hand-built.** One badge has no set ID, one has no image, and `broadcastBadges` is null.
- `cheermotes.json`: the shape of a signed-out `Cheermotes` response (`cheerConfig` with `displayConfig.colors` and `groups { templateURL nodes { prefix tiers { bits } } }`, plus `user(id:) { cheer { cheerGroups } }`), stand-in prefixes and template URLs, 2 prefixes. **Hand-edited:** the tiers are listed out of order. The recorded tier lists carry no images, so a tier's image is built from its group's `templateURL`.
- `cheermotes-unknown.json`: **hand-built.** `user` is null, with the global config still present.
- `cheermotes-bad-item.json`: **hand-built.** A tier with no `bits`, a prefix with no tiers, a node with no `prefix`, and `cheerGroups` null.
- The asset fixtures (`emotes*`, `badges*`, `cheermotes*`) use stand-in emote IDs (`emotesv2_stand1`), tokens (`streamer1OK`), prefixes, badge and template URLs on `cdn.example`, and the request ID `request-1`. Cheermote colours are the public display-config values.
