# Twitch fixtures

Responses from Twitch's web GraphQL endpoint, anonymised. Nothing here can be fetched, and no account, viewer or channel is named.

## Files

`gql/`

- `user.json`: a signed-out raw-query response for `query { user(login: ...) { id login } }`, sent with the web Client-ID. This confirmed that the endpoint accepts raw queries without a persisted-query hash.
- `error-schema.json`: the response to the same query with a field that does not exist. It carries `errors` and no `data`.
- `error-integrity.json`: **hand-built.** It follows the shape of a recorded `failed integrity check` response to a signed-in follow mutation: an `errors` entry with `extensions.code` of `IntegrityCheckFailed`, a null mutation result under `data`, and `extensions.challenge.type` of `integrity`.

## Anonymising

The probed channel's login and ID became `streamer1` and `1000001`, and the request ID became `request-1`. The web Client-ID is a public value that lives in `config/remote.json`; it appears nowhere in these files.
