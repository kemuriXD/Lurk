package io.github.kemurixd.lurk.emotes

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.outcomeOf
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.Backoff
import io.github.kemurixd.lurk.core.storage.CacheStore
import io.github.kemurixd.lurk.core.storage.CachedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val CACHE_TTL = 30.minutes
private const val MAX_RETRIES = 5
private val emoteListJson = Json { ignoreUnknownKeys = true }
private val emoteListSerializer = ListSerializer(Emote.serializer())

/** How one [source] went: the number of emotes it supplied, or why it failed. */
data class SourceResult(
    val source: EmoteSource,
    val outcome: Outcome<Int>,
)

/**
 * Loads a channel's emote sets into [registry]. Sources run in parallel and fail on their own. Each is cached for
 * 30 minutes; a failed source falls back to a stale cache entry and is retried in the background.
 */
class EmoteLoader(
    private val api: EmoteApi,
    private val cache: CacheStore,
    private val registry: EmoteRegistry,
    private val config: StateFlow<RemoteConfig>,
    private val clock: Clock,
    private val native: NativeEmoteSource?,
    private val backoff: Backoff = Backoff(base = 2.seconds, max = 60.seconds),
) {
    private val log = Logger.withTag("EmoteLoader")
    private val setId = MutableStateFlow<String?>(null)

    /** The channel's 7TV emote set id, once the channel's 7TV user has loaded (from cache or network). */
    val sevenTvSetId: StateFlow<String?> = setId

    suspend fun load(
        channel: EmoteChannel,
        enabled: Set<EmoteProvider>,
        retryScope: CoroutineScope,
    ): List<SourceResult> {
        val sources = EmoteSource.entries.filter { applies(it, channel, enabled) }
        return coroutineScope {
            sources.map { source -> async { SourceResult(source, loadSource(source, channel, retryScope)) } }.awaitAll()
        }
    }

    private fun applies(
        source: EmoteSource,
        channel: EmoteChannel,
        enabled: Set<EmoteProvider>,
    ): Boolean =
        when {
            source == EmoteSource.NATIVE -> native != null
            source.twitchOnly && channel.platform != Platform.TWITCH -> false
            else -> source.provider in enabled
        }

    private class Fetched(
        val emotes: List<Emote>,
        val setId: String? = null,
    )

    private suspend fun loadSource(
        source: EmoteSource,
        channel: EmoteChannel,
        retryScope: CoroutineScope,
    ): Outcome<Int> {
        val entry = cache.get(cacheKey(source, channel))
        val cached = entry?.let { decode(it) }
        val cachedSetId = if (source == EmoteSource.SEVENTV_CHANNEL) cache.get(setKey(channel))?.value else null
        if (entry != null && cached != null && isFresh(entry, clock.now())) {
            apply(source, channel, cached, cachedSetId)
            return Outcome.Success(cached.size)
        }
        return when (val outcome = outcomeOf { fetch(source, channel) }) {
            is Outcome.Success -> {
                store(source, channel, outcome.value)
                Outcome.Success(outcome.value.emotes.size)
            }

            is Outcome.Failure -> {
                log.w { "Emote source $source failed: ${outcome.error}" }
                if (cached != null) apply(source, channel, cached, cachedSetId)
                retryScope.launch { retry(source, channel) }
                outcome
            }
        }
    }

    private suspend fun retry(
        source: EmoteSource,
        channel: EmoteChannel,
    ) {
        repeat(MAX_RETRIES) { attempt ->
            delay(backoff.delayFor(attempt))
            val outcome = outcomeOf { fetch(source, channel) }
            if (outcome is Outcome.Success) {
                store(source, channel, outcome.value)
                return
            }
        }
        log.w { "Giving up on emote source $source after $MAX_RETRIES retries" }
    }

    private suspend fun fetch(
        source: EmoteSource,
        channel: EmoteChannel,
    ): Fetched {
        val zeroWidth =
            config.value.emotes.bttvZeroWidth
                .toSet()
        return when (source) {
            EmoteSource.SEVENTV_GLOBAL -> {
                Fetched(api.sevenTvGlobal())
            }

            EmoteSource.SEVENTV_CHANNEL -> {
                api.sevenTvUser(channel.platform, channel.channelId).let { Fetched(it.emotes, it.emoteSetId) }
            }

            EmoteSource.BTTV_GLOBAL -> {
                Fetched(api.bttvGlobal(zeroWidth))
            }

            EmoteSource.BTTV_CHANNEL -> {
                Fetched(api.bttvUser(channel.channelId, zeroWidth))
            }

            EmoteSource.FFZ_GLOBAL -> {
                Fetched(api.ffzGlobal())
            }

            EmoteSource.FFZ_CHANNEL -> {
                Fetched(api.ffzRoom(channel.channelId))
            }

            EmoteSource.NATIVE -> {
                Fetched(requireNotNull(native).load(channel))
            }
        }
    }

    private suspend fun store(
        source: EmoteSource,
        channel: EmoteChannel,
        fetched: Fetched,
    ) {
        val now = clock.now()
        if (source == EmoteSource.SEVENTV_CHANNEL) {
            // A missing set id is stored as an empty string, so an old id never outlives a channel that lost its set.
            cache.put(setKey(channel), fetched.setId.orEmpty(), now)
        }
        cache.put(cacheKey(source, channel), emoteListJson.encodeToString(emoteListSerializer, fetched.emotes), now)
        apply(source, channel, fetched.emotes, fetched.setId)
    }

    private fun apply(
        source: EmoteSource,
        channel: EmoteChannel,
        emotes: List<Emote>,
        sevenTvSetId: String?,
    ) {
        val provider = if (source == EmoteSource.NATIVE) nativeProvider(channel.platform) else source.provider
        registry.put(ProviderSet(provider, source.scope, emotes))
        if (source == EmoteSource.SEVENTV_CHANNEL) setId.value = sevenTvSetId?.takeIf { it.isNotEmpty() }
    }
}

private fun nativeProvider(platform: Platform): EmoteProvider =
    when (platform) {
        Platform.TWITCH -> EmoteProvider.TWITCH
        Platform.KICK -> EmoteProvider.KICK
    }

/** Younger than the TTL, and not stamped in the future (a wrong clock must not pin stale data). */
private fun isFresh(
    entry: CachedValue,
    now: Instant,
): Boolean {
    val age = now - entry.fetchedAt
    return age >= Duration.ZERO && age < CACHE_TTL
}

private fun decode(entry: CachedValue): List<Emote>? =
    try {
        emoteListJson.decodeFromString(emoteListSerializer, entry.value)
    } catch (
        @Suppress("SwallowedException") e: SerializationException,
    ) {
        null
    } catch (
        @Suppress("SwallowedException") e: IllegalArgumentException,
    ) {
        null
    }

private fun cacheKey(
    source: EmoteSource,
    channel: EmoteChannel,
): String {
    val id = if (source.scope == EmoteScope.GLOBAL) "global" else channel.channelId
    return "emotes:v1:${source.name}:${channel.platform.id}:$id"
}

private fun setKey(channel: EmoteChannel): String =
    "emotes:v1:SEVENTV_CHANNEL_SET:${channel.platform.id}:${channel.channelId}"
