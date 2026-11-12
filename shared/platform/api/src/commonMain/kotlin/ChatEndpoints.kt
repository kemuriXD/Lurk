package io.github.kemurixd.lurk.platform.api

import io.github.kemurixd.lurk.chat.ChatChannel
import io.github.kemurixd.lurk.chat.ChatHistorySource
import io.github.kemurixd.lurk.chat.ChatSender
import io.github.kemurixd.lurk.chat.ChatTransport

/** Everything a chat session needs for one channel. [sender] is null when signed out. */
data class ChatEndpoints(
    val channel: ChatChannel,
    val transport: ChatTransport,
    val history: ChatHistorySource?,
    val sender: ChatSender?,
)
