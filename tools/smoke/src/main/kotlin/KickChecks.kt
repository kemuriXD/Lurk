package io.github.kemurixd.lurk.smoke

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import io.github.kemurixd.lurk.playback.PlaybackSource
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.seconds

private val LIVE_PUSH_LIMIT = 30.seconds
private val CONNECT_LIMIT = 10.seconds

/** The Kick half of the smoke run. Prints statuses and counts only. */
internal class KickChecks(
    graph: SmokeGraph,
    private val config: SmokeConfig,
    private val report: SmokeReport,
) {
    private val koin = graph.koin
    private val source = koin.getAll<PlaybackSource>().first { it.platform == Platform.KICK }
    private val platform = koin.getAll<StreamingPlatform>().first { it.id == Platform.KICK }
    private val sessions = koin.get<SessionRepository>()

    suspend fun run() {
        val signedIn = report.guarded("kick.profile") { signIn() } ?: false
        if (signedIn) report.guarded("kick.followed") { followed() } else report.skip("kick.followed", "not signed in")
        val ref = config.kickChannel?.let { ChannelRef(Platform.KICK, it) }
        if (ref == null) {
            listOf("kick.grant", "kick.realtime").forEach(report::skipNoChannel)
        } else {
            val live = report.guarded("kick.grant") { grant(ref) } ?: false
            report.guarded("kick.realtime") { realtime(ref, live) }
        }
        val slug = config.kickFollowTest
        when {
            slug == null -> Unit
            !signedIn -> report.skip("kick.followTest", "not signed in")
            else -> report.guarded("kick.followTest") { runFollowTest(ChannelRef(Platform.KICK, slug), ops, report) }
        }
    }

    private suspend fun signIn(): Boolean {
        val token = config.kickToken
        if (token == null) {
            report.skip("kick.profile", "no token")
            return false
        }
        return when (val account = sessions.signIn(Platform.KICK, token)) {
            is Outcome.Success -> {
                report.pass("kick.profile", "signedIn")
                true
            }

            is Outcome.Failure -> {
                report.fail("kick.profile", account.error.detail())
                false
            }
        }
    }

    private suspend fun followed() {
        when (val result = platform.followed()) {
            is Outcome.Success -> {
                report.pass("kick.followed", "live=${result.value.live.size} offline=${result.value.offline.size}")
            }

            is Outcome.Failure -> {
                report.fail("kick.followed", result.error.detail())
            }
        }
    }

    /** Prints the grant check and returns whether the channel is live. */
    private suspend fun grant(ref: ChannelRef): Boolean =
        when (val result = source.grant(ref)) {
            is Outcome.Success -> {
                report.pass("kick.grant", "live")
                true
            }

            is Outcome.Failure -> {
                if (result.error == LurkError.NotFound) {
                    report.skip("kick.grant", "offline")
                } else {
                    report.fail("kick.grant", result.error.detail())
                }
                false
            }
        }

    /** A live channel must push a frame within 30 s; an offline one only has to answer the connect within 10 s. */
    private suspend fun realtime(
        ref: ChannelRef,
        live: Boolean,
    ) {
        val channel = channel(ref)
        if (channel == null) {
            report.fail("kick.realtime", "channel lookup failed")
            return
        }
        val limit = if (live) LIVE_PUSH_LIMIT else CONNECT_LIMIT
        val what = if (live) "push" else "frame"
        val result =
            try {
                firstFrame(platform.chat(channel).transport, limit) { !live || "\"push\"" in it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LurkException) {
                Outcome.Failure(e.error)
            }
        report.frameResult("kick.realtime", result, what, limit)
    }

    private suspend fun channel(ref: ChannelRef): Channel? = (platform.channel(ref) as? Outcome.Success)?.value

    private val ops =
        object : FollowOps {
            override suspend fun channel(ref: ChannelRef): Channel? = this@KickChecks.channel(ref)

            override suspend fun followed(): Followed? = (platform.followed() as? Outcome.Success)?.value

            override suspend fun setFollow(
                channel: Channel,
                follow: Boolean,
            ): Boolean = platform.setFollow(channel, follow) is Outcome.Success
        }
}
