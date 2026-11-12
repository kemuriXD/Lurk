package io.github.kemurixd.lurk.platform.twitch.playback

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.platform.api.authed
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import io.github.kemurixd.lurk.playback.AdPolicy
import io.github.kemurixd.lurk.playback.PlaybackGrant
import io.github.kemurixd.lurk.playback.PlaybackSource
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Twitch master playlists from usher, signed with a `site`/`web` playback token. The token is the signed-in
 * viewer's when there is one, so its flags say whether that viewer is ad-free. An expired session or a failed
 * integrity check falls back to an anonymous token. Each grant, retries included, is bounded by [grantTimeout].
 */
class TwitchPlaybackSource(
    private val gql: GqlClient,
    private val sessions: SessionRepository,
    private val config: StateFlow<RemoteConfig>,
    private val random: Random = Random.Default,
    private val grantTimeout: Duration = 10.seconds,
) : PlaybackSource {
    override val platform: Platform = Platform.TWITCH
    override val adPolicy: AdPolicy = AdPolicy.TWITCH_DETECT_AND_PROXY

    override suspend fun grant(channel: ChannelRef): Outcome<PlaybackGrant> =
        withTimeoutOrNull(grantTimeout) {
            val login = channel.login
            when (val result = sessions.authed(Platform.TWITCH, anonymousFallback = true) { token(login, it) }) {
                is Outcome.Failure -> {
                    result
                }

                is Outcome.Success -> {
                    result.value?.let { Outcome.Success(grantFor(login, it)) } ?: Outcome.Failure(LurkError.NotFound)
                }
            }
        } ?: Outcome.Failure(LurkError.Timeout)

    /** Fetches a token. A signed-in request that fails the integrity check is tried once more signed out. */
    private suspend fun token(
        login: String,
        userToken: String?,
    ): Outcome<PlaybackToken?> {
        val result = fetch(login, userToken)
        val integrity = result is Outcome.Failure && result.error == LurkError.IntegrityRequired
        return if (userToken != null && integrity) fetch(login, null) else result
    }

    private suspend fun fetch(
        login: String,
        userToken: String?,
    ): Outcome<PlaybackToken?> =
        gql.query(
            PLAYBACK_TOKEN_OPERATION,
            PLAYBACK_TOKEN_QUERY,
            playbackTokenVariables(login),
            userToken,
            ::decodePlaybackToken,
        )

    private fun grantFor(
        login: String,
        token: PlaybackToken,
    ): PlaybackGrant =
        PlaybackGrant(
            masterUrl = usherUrl(config.value.twitch.usherBase, login, token, random),
            tokenFlags = token.flags,
            lowLatencyCapable = true,
        )
}
