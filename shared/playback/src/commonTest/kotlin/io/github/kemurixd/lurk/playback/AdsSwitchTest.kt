package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.playback.proxy.Route
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The [ProxyMode.WHEN_ADS_DETECTED] switch of a [PlaybackSession]: stitched ads only, once, and never lost. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdsSwitchTest {
    private val preroll = fixture("twitch/media-stitched-preroll.m3u8")
    private val cue = fixture("twitch/media-cue.m3u8")

    @Test
    fun whenAdsDetectedSwitchesOnStitchedOnly() =
        runTest {
            val rig = Rig(this)
            val stream = rig.resolve(prefs(ProxyMode.WHEN_ADS_DETECTED, A))
            val session = stream.session
            val events = record(session.events)
            assertEquals(Route.Direct, session.route.value)
            assertEquals(ProxyStatus.NotUsed, session.proxyStatus.value)

            rig.routes.media = cue
            repeat(5) {
                session.fetch(MEDIA)
                delay(2.seconds)
            }
            runCurrent()
            assertEquals(emptyList(), events)
            assertEquals(emptyList(), rig.routes.urlsVia(via(A)))

            rig.routes.media = preroll
            assertEquals(Outcome.Success(preroll), session.fetch(MEDIA))
            runCurrent()
            assertEquals(listOf<PlaybackEvent>(PlaybackEvent.Reload(MASTER)), events)
            assertEquals(listOf(MASTER), rig.routes.urlsVia(via(A)))
            assertEquals(via(A), session.route.value)
            assertEquals(ProxyStatus.Active(A.label), session.proxyStatus.value)

            repeat(3) { session.fetch(MEDIA) }
            runCurrent()
            assertEquals(listOf(MASTER, MEDIA, MEDIA, MEDIA), rig.routes.urlsVia(via(A)))
            assertEquals(listOf(MASTER) + List(6) { MEDIA }, rig.routes.urlsVia(Route.Direct))
            assertEquals(1, events.size)
            session.close()
        }

    @Test
    fun cueDoesNotTriggerProxy() =
        runTest {
            val rig = Rig(this)
            val stream = rig.resolve(prefs(ProxyMode.WHEN_ADS_DETECTED, A))
            val session = stream.session
            val events = record(session.events)
            val states = record(session.adState)
            val refreshes = fixtureDir("twitch/cue-break").map { it.second }
            repeat(30) { i ->
                rig.routes.media = refreshes[i % refreshes.size]
                session.fetch(MEDIA)
                delay(6.seconds)
            }
            assertTrue(states.any { it is AdState.Avoided }, "the cue was seen")
            assertTrue(states.none { it is AdState.Playing })
            assertTrue(rig.routes.requests.all { it.route == Route.Direct })
            assertEquals(31, rig.routes.requests.size)
            assertEquals(emptyList(), events)
            assertEquals(ProxyStatus.NotUsed, session.proxyStatus.value)
            session.close()
        }

    @Test
    fun adFreeNeverSwitchesOnStitched() =
        runTest {
            val rig = Rig(this)
            rig.source.next = {
                Outcome.Success(
                    PlaybackGrant(MASTER, tokenFlags = TokenFlags(subscriber = false, turbo = true, showAds = true)),
                )
            }
            val stream = rig.resolve(prefs(ProxyMode.WHEN_ADS_DETECTED, A))
            val events = record(stream.session.events)
            rig.routes.media = preroll
            repeat(3) { stream.session.fetch(MEDIA) }
            runCurrent()
            assertEquals(emptyList(), events)
            assertTrue(rig.routes.requests.all { it.route == Route.Direct })
            assertEquals(ProxyStatus.SkippedAdFree, stream.session.proxyStatus.value)
            stream.session.close()
        }

    @Test
    fun reResolveAfterAdSwitchStaysProxied() =
        runTest {
            val rig = Rig(this)
            var forbidden = false
            rig.script { route, url -> if (forbidden && route == via(A) && url == MEDIA) Reply.Status(403) else null }
            val stream = rig.resolve(prefs(ProxyMode.WHEN_ADS_DETECTED, A))
            val session = stream.session
            val events = record(session.events)
            rig.routes.media = preroll
            session.fetch(MEDIA)
            runCurrent()
            assertEquals(via(A), session.route.value)
            forbidden = true
            session.fetch(MEDIA)
            assertEquals(listOf(PlaybackEvent.Reload(MASTER), PlaybackEvent.Reload(MASTER_2)), events)
            assertEquals(listOf(MASTER, MEDIA, MASTER_2), rig.routes.urlsVia(via(A)))
            assertEquals(via(A), session.route.value)
            assertEquals(ProxyStatus.Active(A.label), session.proxyStatus.value)
            session.close()
        }

    @Test
    fun concurrentStitchedSwitchesOnce() =
        runTest {
            val rig = Rig(this)
            val gate = CompletableDeferred<Unit>()
            rig.routes.media = preroll
            rig.routes.reply = { _, url ->
                if (url.startsWith("https://video-weaver.example/")) gate.await()
                Reply.Text(rig.routes.serve(url))
            }
            val stream = rig.resolve(prefs(ProxyMode.WHEN_ADS_DETECTED, A))
            val events = record(stream.session.events)
            val fetches = listOf(MEDIA, MEDIA_480).map { url -> async { stream.session.fetch(url) } }
            runCurrent()
            gate.complete(Unit)
            fetches.awaitAll().forEach { assertEquals(Outcome.Success(preroll), it) }
            runCurrent()
            assertEquals(listOf<PlaybackEvent>(PlaybackEvent.Reload(MASTER)), events)
            assertEquals(listOf(MASTER), rig.routes.urlsVia(via(A)))
            stream.session.close()
        }

    @Test
    fun adsSwitchSurvivesCancelledFetch() =
        runTest {
            val rig = Rig(this)
            val gate = CompletableDeferred<Unit>()
            rig.routes.reply = { route, url ->
                if (route == via(A)) gate.await()
                Reply.Text(rig.routes.serve(url))
            }
            val stream = rig.resolve(prefs(ProxyMode.WHEN_ADS_DETECTED, A))
            val events = record(stream.session.events)
            rig.routes.media = preroll
            val delivering = launch { stream.session.fetch(MEDIA) }
            runCurrent()
            assertEquals(listOf(MASTER), rig.routes.urlsVia(via(A)), "the switch is in flight")
            delivering.cancel()
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf<PlaybackEvent>(PlaybackEvent.Reload(MASTER)), events)
            assertEquals(via(A), stream.session.route.value)
            assertEquals(ProxyStatus.Active(A.label), stream.session.proxyStatus.value)
            stream.session.close()
        }

    @Test
    fun adsSwitchDoesNotCommitAfterClose() =
        runTest {
            val rig = Rig(this)
            val gate = CompletableDeferred<Unit>()
            rig.routes.reply = { route, url ->
                if (route == via(A)) gate.await()
                Reply.Text(rig.routes.serve(url))
            }
            val stream = rig.resolve(prefs(ProxyMode.WHEN_ADS_DETECTED, A))
            val events = record(stream.session.events)
            rig.routes.media = preroll
            val delivering = launch { stream.session.fetch(MEDIA) }
            runCurrent()
            assertEquals(listOf(MASTER), rig.routes.urlsVia(via(A)))
            stream.session.close()
            gate.complete(Unit)
            runCurrent()
            delivering.join()
            assertEquals(emptyList(), events)
            assertEquals(Route.Direct, stream.session.route.value)
            assertEquals(ProxyStatus.NotUsed, stream.session.proxyStatus.value)
        }

    @Test
    fun adsSwitchOnDeadProxiesIsUnavailableAndKeepsPlaying() =
        runTest {
            val rig = Rig(this)
            rig.script { route, _ -> if (route == via(A)) Reply.Offline else null }
            val stream = rig.resolve(prefs(ProxyMode.WHEN_ADS_DETECTED, A))
            val session = stream.session
            val events = record(session.events)
            rig.routes.media = preroll
            assertEquals(Outcome.Success(preroll), session.fetch(MEDIA))
            runCurrent()
            assertEquals(ProxyStatus.Unavailable, session.proxyStatus.value)
            assertEquals(Route.Direct, session.route.value)
            assertEquals(emptyList(), events)
            assertEquals(Outcome.Success(preroll), session.fetch(MEDIA))
            assertEquals(listOf(MASTER, MEDIA, MEDIA), rig.routes.urlsVia(Route.Direct))
            assertEquals(listOf(MASTER), rig.routes.urlsVia(via(A)))
            session.close()
        }

    @Test
    fun adsSwitchRetriesWithFreshGrantOnStreamError() =
        runTest {
            val rig = Rig(this)
            // The proxy works, but the first grant's master has expired by the time the ad shows up.
            rig.script { route, url -> if (route == via(A) && url == MASTER) Reply.Status(403) else null }
            val stream = rig.resolve(prefs(ProxyMode.WHEN_ADS_DETECTED, A))
            val session = stream.session
            val events = record(session.events)
            rig.routes.media = preroll
            session.fetch(MEDIA)
            runCurrent()
            assertEquals(2, rig.source.calls)
            assertEquals(listOf<PlaybackEvent>(PlaybackEvent.Reload(MASTER_2)), events)
            assertEquals(listOf(MASTER, MASTER_2), rig.routes.urlsVia(via(A)))
            assertEquals(via(A), session.route.value)
            assertEquals(ProxyStatus.Active(A.label), session.proxyStatus.value)
            session.close()
        }

    @Test
    fun adsSwitchInsideThrottleWindowRetriesAfterIt() =
        runTest {
            val rig = Rig(this)

            fun master(call: Int) = if (call == 0) MASTER else "$MASTER?renewed=$call"
            rig.source.next =
                { call -> Outcome.Success(PlaybackGrant(master(call), headers = FakePlaybackSource.HEADERS)) }
            var mediaRefreshes = 0
            rig.script { route, url ->
                val firstMedia = route == Route.Direct && url == MEDIA && mediaRefreshes++ == 0
                // The proxy refuses the first renewed master as a stream error, so the switch needs a fresh grant.
                if (firstMedia || (route == via(A) && url == master(1))) Reply.Status(403) else null
            }
            val stream = rig.resolve(prefs(ProxyMode.WHEN_ADS_DETECTED, A))
            val session = stream.session
            val events = record(session.events)
            // t = 0: a playlist 403 re-resolves, which starts the 30 s throttle window.
            assertEquals(Outcome.Failure(LurkError.Http(403)), session.fetch(MEDIA))
            assertEquals(listOf<PlaybackEvent>(PlaybackEvent.Reload(master(1))), events)

            rig.routes.media = preroll
            delay(10.seconds)
            repeat(5) {
                session.fetch(MEDIA)
                runCurrent()
                delay(2.seconds)
            }
            // One proxied master attempt for the whole window, not one per stitched refresh.
            assertEquals(listOf(master(1)), rig.routes.urlsVia(via(A)))
            assertEquals(2, rig.source.calls)
            assertEquals(Route.Direct, session.route.value)
            assertEquals(ProxyStatus.NotUsed, session.proxyStatus.value)

            delay(12.seconds)
            session.fetch(MEDIA)
            runCurrent()
            assertEquals(3, rig.source.calls)
            assertEquals(listOf(master(1), master(1), master(2)), rig.routes.urlsVia(via(A)))
            assertEquals(via(A), session.route.value)
            assertEquals(ProxyStatus.Active(A.label), session.proxyStatus.value)
            repeat(3) {
                session.fetch(MEDIA)
                runCurrent()
            }
            assertEquals(
                listOf<PlaybackEvent>(PlaybackEvent.Reload(master(1)), PlaybackEvent.Reload(master(2))),
                events,
            )
            session.close()
        }

    @Test
    fun adsSwitchWhoseFreshGrantAlsoFailsRetriesLater() =
        runTest {
            val rig = Rig(this)
            var proxyRefusesMasters = true
            rig.script { route, url ->
                if (proxyRefusesMasters && route == via(A) && url.startsWith(MASTER)) Reply.Status(403) else null
            }
            val stream = rig.resolve(prefs(ProxyMode.WHEN_ADS_DETECTED, A))
            val session = stream.session
            val events = record(session.events)
            rig.routes.media = preroll
            session.fetch(MEDIA)
            runCurrent()
            // The old master and the fresh grant's master both fail with a stream error: nothing is committed.
            assertEquals(2, rig.source.calls)
            assertEquals(listOf(MASTER, MASTER_2), rig.routes.urlsVia(via(A)))
            assertEquals(emptyList(), events)
            assertEquals(ProxyStatus.NotUsed, session.proxyStatus.value)

            repeat(10) {
                delay(2.seconds)
                session.fetch(MEDIA)
                runCurrent()
            }
            assertEquals(listOf(MASTER, MASTER_2), rig.routes.urlsVia(via(A)), "no walk per refresh in the window")

            proxyRefusesMasters = false
            delay(12.seconds)
            session.fetch(MEDIA)
            runCurrent()
            assertEquals(listOf<PlaybackEvent>(PlaybackEvent.Reload(MASTER)), events)
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
    fun adsSwitchDropsTheDirectStitchedWindow() =
        runTest {
            val rig = Rig(this)
            val stream = rig.resolve(prefs(ProxyMode.WHEN_ADS_DETECTED, A))
            val session = stream.session
            val states = record(session.adState)
            rig.routes.media = stitchedMidAd()
            session.fetch(MEDIA)
            runCurrent()
            assertTrue(states.any { it is AdState.Playing }, "the direct playlist's ad was seen")
            assertEquals(via(A), session.route.value)

            // The proxied refresh covers the same span with no ad in it.
            rig.routes.media = cleanMidAd()
            session.fetch(MEDIA)
            runCurrent()
            assertEquals(listOf(MASTER, MEDIA), rig.routes.urlsVia(via(A)))
            assertEquals(AdState.None, session.adState.value)
            session.close()
        }

    @Test
    fun masterAfterTheSwitchComesThroughTheProxy() =
        runTest {
            val rig = Rig(this)
            val stream = rig.resolve(prefs(ProxyMode.WHEN_ADS_DETECTED, A))
            val session = stream.session
            rig.routes.media = preroll
            session.fetch(MEDIA)
            runCurrent()
            assertEquals(via(A), session.route.value)
            // The Reload names the same master URL; the resolve's direct copy of it is not served.
            session.fetch(MASTER)
            assertEquals(listOf(MASTER, MASTER), rig.routes.urlsVia(via(A)))
            session.close()
        }

    @Test
    fun adsSwitchOnDeadProxiesRecoversAfterCooldown() =
        runTest {
            val rig = Rig(this)
            var proxyDown = true
            rig.script { route, _ -> if (proxyDown && route == via(A)) Reply.Offline else null }
            val stream = rig.resolve(prefs(ProxyMode.WHEN_ADS_DETECTED, A))
            val session = stream.session
            val events = record(session.events)
            rig.routes.media = preroll
            session.fetch(MEDIA)
            runCurrent()
            assertEquals(ProxyStatus.Unavailable, session.proxyStatus.value)

            proxyDown = false
            delay(2.minutes)
            assertEquals(Outcome.Success(preroll), session.fetch(MEDIA))
            runCurrent()
            assertEquals(listOf<PlaybackEvent>(PlaybackEvent.Reload(MASTER)), events)
            assertEquals(via(A), session.route.value)
            assertEquals(ProxyStatus.Active(A.label), session.proxyStatus.value)
            assertEquals(listOf(MASTER, MASTER), rig.routes.urlsVia(via(A)))
            session.close()
        }
}
