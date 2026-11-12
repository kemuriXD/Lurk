package io.github.kemurixd.lurk.core.model

/** Declared in precedence order: the first enabled match wins. */
enum class HighlightType(
    val defaultOn: Boolean,
) {
    TWITCH_SUB(true),
    TWITCH_ANNOUNCEMENT(true),
    KICK_SUB(true),
    KICKS_GIFT(true),
    KICK_HOST(true),
    KICK_REWARD(false),
    TWITCH_CHEER(true),
    CHANNEL_POINTS_HIGHLIGHT(false),
    MESSAGE_EFFECT(false),
    GIGANTIFIED_EMOTE(false),
    MENTION(true),
    FIRST_MESSAGE(false),
    SUB_ONLY_MODE(false),
    KICK_PINNED(true),
    ;

    companion object {
        val DEFAULTS: Set<HighlightType> = entries.filterTo(mutableSetOf()) { it.defaultOn }
    }
}
