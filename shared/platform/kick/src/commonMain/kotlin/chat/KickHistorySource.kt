package io.github.kemurixd.lurk.platform.kick.chat

import io.github.kemurixd.lurk.chat.ChatHistorySource
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.platform.kick.api.KickApi
import io.github.kemurixd.lurk.platform.kick.api.KickBase
import io.github.kemurixd.lurk.platform.kick.api.asPathSegment

/**
 * The recent chat of one channel, signed out, as the raw reply body for `KickHistoryParser`. [channelId] is the
 * channel ID (the history's `chat_id`), not the chatroom ID. A failure is thrown as a [LurkException].
 */
class KickHistorySource(
    private val api: KickApi,
    private val channelId: String,
) : ChatHistorySource {
    override suspend fun fetch(): String =
        when (val reply = api.get(KickBase.WEB, "chat/${channelId.asPathSegment()}/history", token = null)) {
            is Outcome.Success -> reply.value.toString()
            is Outcome.Failure -> throw LurkException(reply.error)
        }
}
