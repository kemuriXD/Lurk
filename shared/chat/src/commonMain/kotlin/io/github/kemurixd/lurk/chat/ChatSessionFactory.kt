package io.github.kemurixd.lurk.chat

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.emotes.EmoteSnapshot
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock

/** Builds one [ChatSession] per open channel, sharing the stateless collaborators. */
class ChatSessionFactory(
    private val tokenizer: Tokenizer,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
) {
    @Suppress("LongParameterList")
    fun create(
        channel: ChatChannel,
        transport: ChatTransport,
        history: ChatHistorySource?,
        emotes: StateFlow<EmoteSnapshot>,
        preferences: StateFlow<ChatPreferences>,
        selfLogin: String?,
    ): ChatSession =
        ChatSession(
            channel = channel,
            transport = transport,
            history = history,
            emotes = emotes,
            preferences = preferences,
            selfLogin = selfLogin,
            tokenizer = tokenizer,
            dispatchers = dispatchers,
            clock = clock,
        )
}
