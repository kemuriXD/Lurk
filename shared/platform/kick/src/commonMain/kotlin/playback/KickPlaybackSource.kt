package io.github.kemurixd.lurk.platform.kick.playback

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.common.outcomeOf
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.platform.api.authed
import io.github.kemurixd.lurk.platform.kick.api.KickApi
import io.github.kemurixd.lurk.platform.kick.api.KickBase
import io.github.kemurixd.lurk.platform.kick.api.asPathSegment
import io.github.kemurixd.lurk.platform.kick.channel.decodeKickChannel
import io.github.kemurixd.lurk.playback.AdPolicy
import io.github.kemurixd.lurk.playback.PlaybackGrant
import io.github.kemurixd.lurk.playback.PlaybackSource
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Kick master playlists, taken from the channel reply's `playback_url`. A channel that is not live (no livestream,
 * or one marked as finished), or has no playback URL, is [LurkError.NotFound]. Kick streams are left alone: nothing
 * here reads ad data. Each grant is bounded by [grantTimeout].
 */
class KickPlaybackSource(
    private val api: KickApi,
    private val sessions: SessionRepository,
    private val grantTimeout: Duration = 10.seconds,
) : PlaybackSource {
    override val platform: Platform = Platform.KICK
    override val adPolicy: AdPolicy = AdPolicy.UNTOUCHED

    override suspend fun grant(channel: ChannelRef): Outcome<PlaybackGrant> =
        withTimeoutOrNull(grantTimeout) {
            val slug = channel.login
            when (val result = sessions.authed(Platform.KICK, anonymousFallback = true) { fetch(slug, it) }) {
                is Outcome.Failure -> result
                is Outcome.Success -> result.value?.let { Outcome.Success(it) } ?: Outcome.Failure(LurkError.NotFound)
            }
        } ?: Outcome.Failure(LurkError.Timeout)

    /** The grant for a live channel, or null when it is offline or has no playback URL. */
    private suspend fun fetch(
        slug: String,
        token: String?,
    ): Outcome<PlaybackGrant?> =
        when (val reply = api.get(KickBase.API, "v2/channels/${slug.asPathSegment()}", token)) {
            is Outcome.Failure -> {
                reply
            }

            is Outcome.Success -> {
                outcomeOf {
                    val json = decodeKickChannel(reply.value)
                    val url = json.playbackUrl
                    if (json.livestream == null || json.livestream.isLive == false || url.isNullOrBlank()) {
                        null
                    } else {
                        PlaybackGrant(masterUrl = url, tokenFlags = null, lowLatencyCapable = true)
                    }
                }
            }
        }
}
