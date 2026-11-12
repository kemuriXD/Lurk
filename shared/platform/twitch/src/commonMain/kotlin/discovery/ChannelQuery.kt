package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.LastStream
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val CHANNEL_OPERATION = "ChannelProfile"

internal const val CHANNEL_QUERY =
    "query ChannelProfile(\$login: String!) { user(login: \$login) { " +
        "id login displayName description profileImageURL(width: 300) bannerImageURL " +
        "followers { totalCount } stream { $STREAM_FIELDS } " +
        "lastBroadcast { title startedAt game { id name boxArtURL } } } }"

/** A channel's profile, with its stream when it is live. A login Twitch does not know is `NotFound`. */
internal suspend fun GqlClient.channel(
    sessions: SessionRepository,
    ref: ChannelRef,
): Outcome<Channel> =
    sessions.twitchCall { token ->
        val variables = buildJsonObject { put("login", ref.login) }
        when (val result = query(CHANNEL_OPERATION, CHANNEL_QUERY, variables, token, ::decodeChannel)) {
            is Outcome.Failure -> result
            is Outcome.Success -> result.value?.let { Outcome.Success(it) } ?: Outcome.Failure(LurkError.NotFound)
        }
    }

/** Null when `user` is null, which is how Twitch answers for a login it does not know. */
internal fun decodeChannel(data: JsonObject): Channel? {
    val user = data.getValue("user")
    if (user is JsonNull) return null
    return channelOf(user as JsonObject)
}

/** A user as a [Channel], or null when it has no ID or login. The chat ID on Twitch is the login. */
internal fun channelOf(user: JsonObject): Channel? {
    val id = user.nonBlank("id")
    val login = loginOrNull(user)
    return if (id == null || login == null) {
        null
    } else {
        Channel(
            ref = ChannelRef(Platform.TWITCH, login),
            id = id,
            chatId = login,
            displayName = user.text("displayName") ?: login,
            avatarUrl = user.text("profileImageURL"),
            bannerUrl = user.text("bannerImageURL"),
            bio = user.text("description"),
            followers = user.obj("followers")?.number("totalCount"),
            live = user.obj("stream")?.let { streamOf(it, fallback = user) },
            lastStream = user.obj("lastBroadcast")?.let(::lastStreamOf),
        )
    }
}

private fun lastStreamOf(broadcast: JsonObject): LastStream =
    LastStream(
        title = broadcast.text("title"),
        category = categoryOf(broadcast.obj("game")),
        startedAt = instantOrNull(broadcast.text("startedAt")),
    )
