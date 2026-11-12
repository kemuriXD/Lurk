package io.github.kemurixd.lurk.smoke

import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.config.RemoteConfigRepository
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.WebSocketConnector
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import io.github.kemurixd.lurk.platform.twitch.chat.IrcTransport
import io.github.kemurixd.lurk.playback.PlaybackPrefs
import io.github.kemurixd.lurk.playback.PlaybackSource
import io.github.kemurixd.lurk.playback.PlaylistResolver
import io.github.kemurixd.lurk.playback.ProxyEndpoint
import io.github.kemurixd.lurk.playback.ProxyMode
import io.github.kemurixd.lurk.playback.ProxySettings
import kotlin.time.Duration.Companion.seconds

private val IRC_LIMIT = 10.seconds

/** The Twitch half of the smoke run. Prints statuses, flags and counts only. */
internal class TwitchChecks(
    private val graph: SmokeGraph,
    private val config: SmokeConfig,
    private val report: SmokeReport,
) {
    private val koin get() = graph.koin
    private val source = koin.getAll<PlaybackSource>().first { it.platform == Platform.TWITCH }
    private val platform = koin.getAll<StreamingPlatform>().first { it.id == Platform.TWITCH }
    private val sessions = koin.get<SessionRepository>()

    suspend fun run() {
        val ref = config.twitchChannel?.let { ChannelRef(Platform.TWITCH, it) }
        if (ref == null) {
            listOf("twitch.grant.anon", "twitch.irc").forEach(report::skipNoChannel)
        } else {
            report.guarded("twitch.grant.anon") { grant("twitch.grant.anon", ref) }
            report.guarded("twitch.irc") { irc(ref) }
        }
        val signedIn = report.guarded("twitch.profile") { signIn() } ?: false
        val channelChecks = listOf("twitch.grant.signedIn", "twitch.resolve")
        when {
            ref == null -> {
                channelChecks.forEach(report::skipNoChannel)
            }

            !signedIn -> {
                channelChecks.forEach { report.skip(it, "not signed in") }
            }

            else -> {
                report.guarded("twitch.grant.signedIn") { grant("twitch.grant.signedIn", ref) }
                report.guarded("twitch.resolve") { resolve(ref) }
            }
        }
        if (signedIn) {
            report.guarded(
                "twitch.followed",
            ) { followed() }
        } else {
            report.skip("twitch.followed", "not signed in")
        }
    }

    private suspend fun grant(
        check: String,
        ref: ChannelRef,
    ) {
        when (val result = source.grant(ref)) {
            is Outcome.Success -> report.pass(check, result.value.flagsDetail())
            is Outcome.Failure -> report.fail(check, result.error.detail())
        }
    }

    private suspend fun irc(ref: ChannelRef) {
        val config = koin.get<RemoteConfigRepository>().config
        val transport = IrcTransport(koin.get<WebSocketConnector>(), config, ref.login, token = { null })
        report.frameResult("twitch.irc", firstFrame(transport, IRC_LIMIT), "frame", IRC_LIMIT)
    }

    private suspend fun signIn(): Boolean {
        val token = config.twitchToken
        if (token == null) {
            report.skip("twitch.profile", "no token")
            return false
        }
        return when (val account = sessions.signIn(Platform.TWITCH, token)) {
            is Outcome.Success -> {
                report.pass("twitch.profile", "signedIn")
                true
            }

            is Outcome.Failure -> {
                report.fail("twitch.profile", account.error.detail())
                false
            }
        }
    }

    private suspend fun resolve(ref: ChannelRef) {
        val proxy = ProxySettings(ProxyMode.ALWAYS, listOf(ProxyEndpoint.UrlTemplate("http://127.0.0.1:9/{channel}")))
        when (val result = koin.get<PlaylistResolver>().resolve(ref, PlaybackPrefs(proxy = proxy), graph.scope)) {
            is Outcome.Failure -> {
                if (result.error == LurkError.NotFound) {
                    report.skip("twitch.resolve", "offline")
                } else {
                    report.fail("twitch.resolve", result.error.detail())
                }
            }

            is Outcome.Success -> {
                val stream = result.value
                val status = stream.session.proxyStatus.value::class.simpleName
                val variants = stream.variants.size
                stream.session.close()
                if (variants == 0) {
                    report.fail("twitch.resolve", "status=$status variants=0")
                } else {
                    report.pass("twitch.resolve", "status=$status variants=$variants")
                }
            }
        }
    }

    private suspend fun followed() {
        when (val result = platform.followed()) {
            is Outcome.Success -> {
                val follows = result.value
                report.pass("twitch.followed", "live=${follows.live.size} offline=${follows.offline.size}")
            }

            is Outcome.Failure -> {
                report.fail("twitch.followed", result.error.detail())
            }
        }
    }
}
