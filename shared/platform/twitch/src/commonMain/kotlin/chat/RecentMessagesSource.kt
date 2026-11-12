package io.github.kemurixd.lurk.platform.twitch.chat

import io.github.kemurixd.lurk.chat.ChatHistorySource
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.StateFlow

/**
 * Recent chat history of one channel from the recent-messages service. Non-2xx responses surface as a
 * [io.github.kemurixd.lurk.core.common.LurkException] from the shared client's response validator.
 */
class RecentMessagesSource(
    private val http: HttpClient,
    private val config: StateFlow<RemoteConfig>,
    private val login: String,
) : ChatHistorySource {
    override suspend fun fetch(): String =
        http
            .get(
                config.value.twitch.recentMessagesUrl
                    .replace("{channel}", login.lowercase()),
            ).bodyAsText()
}
