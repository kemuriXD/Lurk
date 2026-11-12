package io.github.kemurixd.lurk.chat

import io.github.kemurixd.lurk.core.model.HighlightType

/**
 * The chat settings a [ChatSession] applies. [blockedUsers] holds lowercase logins. [blockedWords] match whole
 * space-separated words, ignoring case and surrounding punctuation. [recentMessages] turns the history fetch on join
 * (and the Twitch back-fill after a reconnect) on or off.
 */
data class ChatPreferences(
    val highlights: Set<HighlightType> = HighlightType.DEFAULTS,
    val blockedUsers: Set<String> = emptySet(),
    val blockedWords: Set<String> = emptySet(),
    val ffzEffects: Boolean = true,
    val recentMessages: Boolean = true,
)
