# Chat fixtures

Test data for the Twitch and Kick chat parsers. Everything here is anonymised: user names are `user_NNNN`, numeric ids come from a fake range (about 1,000,000 and up), message ids are made-up UUIDs, and chat text is neutral filler written for these files. Emote names that belonged to a particular channel are renamed to `chanHype`, `chanWave` and similar, with matching fake ids. Twitch global emotes (`LUL`, `TehePelo`, `ResidentSleeper`) and the Kick emote `KEKW` keep their real names and ids.

Tests read these files with `fixture("<path>")`, which resolves against `src/commonTest/fixtures`.

## twitch/chat.irc

One IRC line per line, LF terminated (the live server uses CRLF, so split on `\n` and trim a trailing `\r`). The `/me` line keeps its two 0x01 control characters.

Taken from a live capture, with text, badges, system messages and channel names rewritten, and `emotes=` positions recomputed in Unicode code points:

- JOIN, ROOMSTATE, 353, 366
- plain PRIVMSG, one emote, an emote-only message, one emote repeated three times, a channel emote with a v2 id, two emote ids in one message
- an emoji before an emote (the emoji is one code point, so `LUL` starts at offset 3)
- `/me` ACTION, replies (`reply-parent-*` and `reply-thread-parent-*` tags), `bits=100` with `Cheer100`, `first-msg=1`, `msg-id=highlighted-message`
- CLEARCHAT with a duration and with a target but no duration (permanent ban), CLEARMSG, NOTICE, a partial ROOMSTATE
- USERNOTICE: sub, resub (with and without a message), subgift, submysterygift, announcement (two colours), raid, viewermilestone, primepaidupgrade

The capture tool had overwritten some tag keys with a placeholder. They are restored here (`reply-thread-parent-display-name`, `reply-thread-parent-user-login`, `msg-param-recipient-display-name`). The CLEARMSG `target-msg-id` and the CLEARCHAT target match a PRIVMSG earlier in the file, and one reply's parent id matches the `first-msg=1` line.

Hand-built lines (no live capture):

- `PING :tmi.twitch.tv`
- `:tmi.twitch.tv RECONNECT`
- a PRIVMSG with `msg-id=skip-subs-mode-message`
- a PRIVMSG with `msg-id=gigantified-emote-message`
- a PRIVMSG with `msg-id=animated-message` and `animation-id=rainbow-eclipse`
- a PRIVMSG whose `emotes=` has a second range (`50-54`) past the end of the text
- CLEARCHAT with no target (clears the whole chat)
- the permanent-ban CLEARCHAT (a live line with the `ban-duration` tag removed)

## twitch/recent-messages.json

The shape of the recent-messages service: `{"messages": [...], "error": null, "error_code": null}`. Hand-built from five PRIVMSG lines of `chat.irc` with `historical=1` and `rm-received-ts=` added.

## kick/frames.jsonl

One WebSocket frame per line. The layouts follow live captures; ids, names, text and channel strings are rewritten. Chat room 1000100 and channel 1000101 are fake, and every `channel` field uses them.

Anonymised rebuilds of live frames (the live frames already carry `badges_v2` and `message_ref`, so no extra hand-building was needed for them):

- connect reply, two subscribe replies, an empty `{}` ping
- ChatMessageEvent: plain, with emote tokens (`[emote:id:name]`), `type:"reply"` and `type:"celebration"`. Each has `sender.identity.badges` next to `badges_v2` and a 13-character `metadata.message_ref`. `image_url` values are `…` stubs.
- MessageDeletedEvent, UserBannedEvent (timeout and permanent), SubscriptionEvent, KicksGifted, StreamHostEvent, RewardRedeemedEvent
- KicksLeaderboardUpdated and ChatMessageSentEvent, which the parser should ignore

Hand-built, field names and nesting **unconfirmed** (written from public Kick client code, never seen on the wire by us):

- GiftedSubscriptionsEvent
- LuckyUsersWhoGotGiftSubscriptionsEvent
- PinnedMessageCreatedEvent
- PinnedMessageDeletedEvent
- ChatMoveToSupportedChannelEvent
- ChatroomClearEvent

Other hand-built lines:

- a frame that carries two push replies separated by a newline. A JSONL line cannot contain a raw newline, so this frame is stored as one JSON string. Decode the string to get the frame text, then split on `\n`.
- a ChatMessageEvent whose inner `data` string is truncated, broken JSON. The outer line is valid JSON; only the inner payload is bad.

## kick/history.json

The shape of the chat history endpoint (`data.messages`, `cursor`, `pinned_message`). Cut to five messages, one with an emote token, with new ids and neutral text.
