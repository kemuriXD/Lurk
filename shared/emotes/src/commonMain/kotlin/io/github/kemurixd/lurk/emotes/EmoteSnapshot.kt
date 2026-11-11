package io.github.kemurixd.lurk.emotes

import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope

/** One provider's emotes for one scope (global or channel). */
data class ProviderSet(
    val provider: EmoteProvider,
    val scope: EmoteScope,
    val emotes: List<Emote>,
)

internal data class EmoteKey(
    val provider: EmoteProvider,
    val scope: EmoteScope,
)

/** An immutable view of a channel's emotes. Lookups are map reads; the maps are built once per change. */
class EmoteSnapshot internal constructor(
    val generation: Int,
    internal val sources: Map<EmoteKey, List<Emote>>,
    internal val personalLists: Map<String, List<Emote>>,
    private val merged: Map<String, Emote>,
    private val personal: Map<String, Map<String, Emote>>,
    private val pickerEmotes: Map<EmoteProvider, List<Emote>>,
) {
    /**
     * Resolves [name] with an exact, case-sensitive match. Order: the sender's personal emotes, then channel 7TV,
     * BTTV, FFZ, then global 7TV, BTTV, FFZ, then native (Twitch, Kick). Chatterino checks FFZ, BTTV, 7TV within
     * each scope; Lurk deliberately puts 7TV first.
     */
    fun find(
        name: String,
        senderId: String? = null,
    ): Emote? = senderId?.let { personal[it]?.get(name) } ?: merged[name]

    /** Emotes for the picker, by provider, sorted by name ignoring case. Hidden and personal emotes are left out. */
    fun picker(): Map<EmoteProvider, List<Emote>> = pickerEmotes

    companion object {
        val EMPTY = EmoteSnapshot(0, emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())
    }
}
