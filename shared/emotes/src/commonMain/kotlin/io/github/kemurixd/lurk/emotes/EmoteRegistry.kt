package io.github.kemurixd.lurk.emotes

import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

private typealias Sources = Map<EmoteKey, List<Emote>>

private typealias Personal = Map<String, List<Emote>>

private val SCOPES = listOf(EmoteScope.CHANNEL, EmoteScope.GLOBAL)
private val THIRD_PARTY = listOf(EmoteProvider.SEVENTV, EmoteProvider.BTTV, EmoteProvider.FFZ)
private val NATIVE = listOf(EmoteProvider.TWITCH, EmoteProvider.KICK)

/** Highest priority first: channel then global for third parties, then the platform's own emotes. */
private val PRIORITY: List<EmoteKey> =
    SCOPES.flatMap { scope -> THIRD_PARTY.map { EmoteKey(it, scope) } } +
        SCOPES.flatMap { scope -> NATIVE.map { EmoteKey(it, scope) } }

private fun build(
    generation: Int,
    sources: Map<EmoteKey, List<Emote>>,
    personal: Map<String, List<Emote>>,
): EmoteSnapshot {
    val merged = HashMap<String, Emote>()
    for (key in PRIORITY) {
        sources[key]?.forEach { merged.putIfAbsent(it.name, it) }
    }
    val picker =
        sources.values
            .flatten()
            .filter { !it.hiddenInPicker && it.scope != EmoteScope.PERSONAL }
            .groupBy { it.provider }
            .mapValues { (_, list) -> list.sortedBy { it.name.lowercase() } }
    return EmoteSnapshot(
        generation = generation,
        sources = sources,
        personalLists = personal,
        merged = merged,
        personal = personal.mapValues { (_, list) -> list.associateBy { it.name } },
        pickerEmotes = picker,
    )
}

/**
 * The emotes of one channel. Every mutation runs through [MutableStateFlow.update] over immutable data, so
 * concurrent callers never lose an update and no lock is needed. Each one publishes a new [EmoteSnapshot] with a
 * higher generation and a freshly merged lookup map.
 */
class EmoteRegistry {
    private val state = MutableStateFlow(EmoteSnapshot.EMPTY)
    val snapshot: StateFlow<EmoteSnapshot> = state

    private fun mutate(change: (Sources, Personal) -> Pair<Sources, Personal>) {
        state.update { current ->
            val (sources, personal) = change(current.sources, current.personalLists)
            build(current.generation + 1, sources, personal)
        }
    }

    /** Replaces everything held for [ProviderSet.provider] in [ProviderSet.scope]. */
    fun put(set: ProviderSet) = mutate { s, p -> (s + (EmoteKey(set.provider, set.scope) to set.emotes)) to p }

    fun addEmote(
        provider: EmoteProvider,
        scope: EmoteScope,
        emote: Emote,
    ) = mutate { s, p ->
        val key = EmoteKey(provider, scope)
        (s + (key to (s[key].orEmpty().filterNot { it.id == emote.id } + emote))) to p
    }

    fun removeEmote(
        provider: EmoteProvider,
        scope: EmoteScope,
        emoteId: String,
    ) = mutate { s, p ->
        val key = EmoteKey(provider, scope)
        (s + (key to s[key].orEmpty().filterNot { it.id == emoteId })) to p
    }

    fun putPersonal(
        userId: String,
        emotes: List<Emote>,
    ) = mutate { s, p -> s to (p + (userId to emotes)) }

    fun removePersonal(userId: String) = mutate { s, p -> s to (p - userId) }
}
