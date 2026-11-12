package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.playback.proxy.Route
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A session that should be proxied but plays direct as [ProxyStatus.Unavailable]: when it probes the proxies again,
 * how a probe uses a fresh grant, and how failures restart the cooldown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProxyRecoveryTest {
    private val live = fixture("twitch/media-live.m3u8")

    @Test
    fun unavailableRecoversAfterCooldown() =
        runTest {
            val rig = Rig(this)
            var proxyDown = false
            rig.script { route, _ -> if (proxyDown && route == via(A)) Reply.Offline else null }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A))
            val session = stream.session
            val events = record(session.events)
            proxyDown = true
            assertEquals(Outcome.Success(live), session.fetch(MEDIA))
            assertEquals(ProxyStatus.Unavailable, session.proxyStatus.value)

            proxyDown = false
            delay(2.minutes)
            // The refresh that notices goes direct as before; the proxies are probed beside it.
            assertEquals(Outcome.Success(live), session.fetch(MEDIA))
            assertEquals(listOf(MEDIA, MEDIA), rig.routes.urlsVia(Route.Direct))
            runCurrent()
            assertEquals(listOf<PlaybackEvent>(PlaybackEvent.Reload(MASTER)), events)
            assertEquals(via(A), session.route.value)
            assertEquals(ProxyStatus.Active(A.label), session.proxyStatus.value)
            repeat(3) {
                session.fetch(MEDIA)
                runCurrent()
            }
            assertEquals(listOf(MASTER, MEDIA, MASTER, MEDIA, MEDIA, MEDIA), rig.routes.urlsVia(via(A)))
            assertEquals(1, events.size)
            session.close()
        }

    @Test
    fun noRecoveryProbeBeforeCooldown() =
        runTest {
            val rig = Rig(this)
            var proxyDown = false
            rig.script { route, _ -> if (proxyDown && route == via(A)) Reply.Offline else null }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A))
            val session = stream.session
            proxyDown = true
            session.fetch(MEDIA)
            proxyDown = false
            repeat(59) {
                delay(2.seconds)
                session.fetch(MEDIA)
                runCurrent()
            }
            assertEquals(listOf(MASTER, MEDIA), rig.routes.urlsVia(via(A)))
            assertEquals(ProxyStatus.Unavailable, session.proxyStatus.value)
            session.close()
        }

    @Test
    fun failedRecoveryProbeStaysUnavailableAndWaitsAgain() =
        runTest {
            val rig = Rig(this)
            var proxyDown = false
            rig.script { route, _ -> if (proxyDown && route == via(A)) Reply.Offline else null }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A))
            val session = stream.session
            val events = record(session.events)
            val statuses = record(session.proxyStatus)
            proxyDown = true
            session.fetch(MEDIA)
            delay(2.minutes)
            session.fetch(MEDIA)
            runCurrent()
            assertEquals(listOf(MASTER, MEDIA, MASTER), rig.routes.urlsVia(via(A)), "one probe")
            repeat(59) {
                delay(2.seconds)
                session.fetch(MEDIA)
                runCurrent()
            }
            assertEquals(3, rig.routes.urlsVia(via(A)).size, "no probe loop inside the next cooldown")
            delay(2.seconds)
            session.fetch(MEDIA)
            runCurrent()
            assertEquals(listOf(MASTER, MEDIA, MASTER, MASTER), rig.routes.urlsVia(via(A)))
            assertEquals(ProxyStatus.Unavailable, session.proxyStatus.value)
            assertEquals(emptyList(), events)
            assertEquals(listOf<ProxyStatus>(ProxyStatus.Active(A.label), ProxyStatus.Unavailable), statuses)
            session.close()
        }

    @Test
    fun recoveryProbeWithAnAgedGrantTakesAFreshOne() =
        runTest {
            val rig = Rig(this)
            var proxyDown = false
            var grantExpired = false
            rig.script { route, url ->
                when {
                    route != via(A) -> null
                    proxyDown -> Reply.Offline
                    grantExpired && url == MASTER -> Reply.Status(403)
                    else -> null
                }
            }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A))
            val session = stream.session
            val events = record(session.events)
            proxyDown = true
            session.fetch(MEDIA)
            assertEquals(ProxyStatus.Unavailable, session.proxyStatus.value)

            // The proxy comes back, but by now the session's first grant has expired.
            proxyDown = false
            grantExpired = true
            delay(2.minutes)
            session.fetch(MEDIA)
            runCurrent()
            assertEquals(2, rig.source.calls)
            assertEquals(listOf<PlaybackEvent>(PlaybackEvent.Reload(MASTER_2)), events)
            assertEquals(via(A), session.route.value)
            assertEquals(ProxyStatus.Active(A.label), session.proxyStatus.value)
            repeat(3) {
                session.fetch(MEDIA)
                runCurrent()
            }
            assertEquals(1, events.size)
            session.close()
        }

    @Test
    fun recoveryProbeWhoseFreshGrantFailsTooWaitsAgain() =
        runTest {
            val rig = Rig(this)
            var proxyDown = false
            var mastersRefused = false
            rig.script { route, url ->
                when {
                    route != via(A) -> null
                    proxyDown -> Reply.Offline
                    mastersRefused && url.startsWith(MASTER) -> Reply.Status(403)
                    else -> null
                }
            }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A))
            val session = stream.session
            val events = record(session.events)
            proxyDown = true
            session.fetch(MEDIA)
            proxyDown = false
            mastersRefused = true
            delay(2.minutes)
            session.fetch(MEDIA)
            runCurrent()
            assertEquals(2, rig.source.calls)
            assertEquals(listOf(MASTER, MEDIA, MASTER, MASTER_2), rig.routes.urlsVia(via(A)))
            repeat(59) {
                delay(2.seconds)
                session.fetch(MEDIA)
                runCurrent()
            }
            assertEquals(2, rig.source.calls, "no grant per refresh inside the next cooldown")
            assertEquals(4, rig.routes.urlsVia(via(A)).size, "no probe loop inside the next cooldown")
            delay(2.seconds)
            session.fetch(MEDIA)
            runCurrent()
            assertEquals(3, rig.source.calls)
            assertEquals(emptyList(), events)
            assertEquals(Route.Direct, session.route.value)
            assertEquals(ProxyStatus.Unavailable, session.proxyStatus.value)
            session.close()
        }

    @Test
    fun recoveryProbeSurvivesAThrowingGrant() =
        runTest {
            val rig = Rig(this)
            rig.source.next = { call ->
                check(call == 0) { "grant failed unexpectedly" }
                Outcome.Success(PlaybackGrant(MASTER, headers = FakePlaybackSource.HEADERS))
            }
            var proxyDown = false
            var masterRefused = false
            rig.script { route, url ->
                when {
                    route != via(A) -> null
                    proxyDown -> Reply.Offline
                    masterRefused && url == MASTER -> Reply.Status(403)
                    else -> null
                }
            }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A))
            val session = stream.session
            val events = record(session.events)
            proxyDown = true
            session.fetch(MEDIA)
            proxyDown = false
            masterRefused = true
            delay(2.minutes)
            session.fetch(MEDIA)
            runCurrent()
            repeat(59) {
                delay(2.seconds)
                session.fetch(MEDIA)
                runCurrent()
            }
            assertEquals(listOf(MASTER, MEDIA, MASTER), rig.routes.urlsVia(via(A)), "one probe, no loop")
            assertEquals(emptyList(), events)
            assertEquals(Route.Direct, session.route.value)
            assertEquals(ProxyStatus.Unavailable, session.proxyStatus.value)
            session.close()
        }

    @Test
    fun failedWalkWhileUnavailableRestartsTheCooldown() =
        runTest {
            val rig = Rig(this)
            var proxyDown = false
            var forbidMedia = false
            rig.script { route, url ->
                when {
                    route == via(A) -> if (proxyDown) Reply.Offline else null
                    forbidMedia && url == MEDIA -> Reply.Status(403).also { forbidMedia = false }
                    else -> null
                }
            }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A))
            val session = stream.session
            val events = record(session.events)
            // Mid-session the proxy dies: the session goes Unavailable, here at t = 0.
            proxyDown = true
            session.fetch(MEDIA)
            assertEquals(ProxyStatus.Unavailable, session.proxyStatus.value)

            // At 110 s a 403 re-resolves, walks the proxies again and fails again.
            delay(110.seconds)
            forbidMedia = true
            session.fetch(MEDIA)
            runCurrent()
            assertEquals(listOf<PlaybackEvent>(PlaybackEvent.Reload(MASTER_2)), events)
            assertEquals(ProxyStatus.Unavailable, session.proxyStatus.value)
            val afterWalk = rig.routes.urlsVia(via(A)).size

            // 12 s after that walk the old mark has passed, but the walk restarted the cooldown.
            delay(12.seconds)
            session.fetch(MEDIA)
            runCurrent()
            assertEquals(afterWalk, rig.routes.urlsVia(via(A)).size, "no probe right after a failed walk")
            session.close()
        }
}
