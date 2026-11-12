package io.github.kemurixd.lurk.core.model

/** A tokenised piece of a chat message. [source] is the original text, kept for copy and accessibility. */
sealed interface MessagePart {
    data class Text(
        val text: String,
    ) : MessagePart

    data class EmotePart(
        val emote: Emote,
        val overlays: List<Emote> = emptyList(),
        val effects: Set<FfzEffect> = emptySet(),
        val source: String,
    ) : MessagePart

    data class Mention(
        val login: String,
        val source: String,
    ) : MessagePart

    data class Link(
        val url: String,
        val source: String,
    ) : MessagePart

    data class Cheer(
        val prefix: String,
        val bits: Int,
        val source: String,
    ) : MessagePart

    data class Emoji(
        val text: String,
    ) : MessagePart
}
