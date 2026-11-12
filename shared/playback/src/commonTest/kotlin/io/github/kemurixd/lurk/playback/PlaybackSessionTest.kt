package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.playback.proxy.Route
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionTest {
    private val live = fixture("twitch/media-live.m3u8")
    private val cue = fixture("twitch/media-cue.m3u8")
    private val preroll = fixture("twitch/media-stitched-preroll.m3u8")

    /** Serves the fixtures, but [fail] decides first; null lets the request through. */
    private fun Rig.script(fail: (Route, String) -> Reply?) {
        routes.reply = { route, url -> fail(route, url) ?: Reply.Text(routes.serve(url)) }
    }

    @Test
    fun alwaysRoutesMasterAndEveryRefreshViaProxy() =
        runTest {
            val rig = Rig(this)
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A))
            val session = stream.session
            repeat(5) {
                assertEquals(Outcome.Success(live), session.fetch(MEDIA))
                delay(2.seconds)
            }
            assertEquals(listOf(MASTER) + List(5) { MEDIA }, rig.routes.urlsVia(via(A)))
            assertEquals(emptyList(), rig.routes.urlsVia(Route.Direct))
            assertEquals(ProxyStatus.Active(A.label), session.proxyStatus.value)
            assertEquals(via(A), session.route.value)
            assertEquals(2.seconds, session.typicalSegment.value)
            session.close()
        }

    @Test
    fun midSessionProxyFailureMovesToNext() =
        runTest {
            val rig = Rig(this)
            var mediaOnA = 0
            rig.script { route, url -> if (route == via(A) && url == MEDIA && ++mediaOnA == 6) Reply.Offline else null }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A, B))
            val session = stream.session
            val statuses = record(session.proxyStatus)
            repeat(5) { assertEquals(Outcome.Success(live), session.fetch(MEDIA)) }
            assertEquals(Outcome.Success(live), session.fetch(MEDIA))
            assertEquals(via(B), session.route.value)
            assertEquals(listOf(MEDIA), rig.routes.urlsVia(via(B)))
            assertEquals(Outcome.Success(live), session.fetch(MEDIA))
            assertEquals(listOf(MEDIA, MEDIA), rig.routes.urlsVia(via(B)))
            assertEquals(listOf(MASTER) + List(6) { MEDIA }, rig.routes.urlsVia(via(A)))
            assertEquals(emptyList(), rig.routes.urlsVia(Route.Direct))
            assertEquals(listOf<ProxyStatus>(ProxyStatus.Active(A.label), ProxyStatus.Active(B.label)), statuses)
            session.close()
        }

    @Test
    fun allProxiesFailGoesDirectVisibly() =
        runTest {
            // Both fail at resolve.
            val atStart = Rig(this)
            atStart.script { route, _ -> if (route != Route.Direct) Reply.Offline else null }
            val first = atStart.resolve(prefs(ProxyMode.ALWAYS, A, B))
            assertEquals(Route.Direct, first.session.route.value)
            assertEquals(ProxyStatus.Unavailable, first.session.proxyStatus.value)
            assertEquals(7, first.variants.size)
            assertEquals(Outcome.Success(live), first.session.fetch(MEDIA))
            assertEquals(listOf(MASTER, MEDIA), atStart.routes.urlsVia(Route.Direct))
            first.session.close()

            // Both fail mid-session.
            val later = Rig(this)
            var proxiesDown = false
            later.script { route, _ -> if (proxiesDown && route != Route.Direct) Reply.Timeout else null }
            val second = later.resolve(prefs(ProxyMode.ALWAYS, A, B))
            val statuses = record(second.session.proxyStatus)
            assertEquals(Outcome.Success(live), second.session.fetch(MEDIA))
            proxiesDown = true
            assertEquals(Outcome.Success(live), second.session.fetch(MEDIA))
            assertEquals(Route.Direct, second.session.route.value)
            assertEquals(ProxyStatus.Unavailable, second.session.proxyStatus.value)
            assertEquals(Outcome.Success(live), second.session.fetch(MEDIA))
            assertEquals(listOf(MEDIA, MEDIA), later.routes.urlsVia(Route.Direct))
            assertEquals(listOf(MEDIA), later.routes.urlsVia(via(B)))
            assertEquals(listOf<ProxyStatus>(ProxyStatus.Active(A.label), ProxyStatus.Unavailable), statuses)
            assertFalse(ProxyStatus.NotUsed in statuses)
            second.session.close()
        }

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
    fun playlist403ReResolves() =
        runTest {
            val rig = Rig(this)
            var forbidden = 0
            rig.script { route, url ->
                if (route == via(A) && url == MEDIA &&
                    forbidden++ == 0
                ) {
                    Reply.Status(403)
                } else {
                    null
                }
            }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A))
            val session = stream.session
            val events = record(session.events)
            assertEquals(Outcome.Failure(LurkError.Http(403)), session.fetch(MEDIA))
            assertEquals(2, rig.source.calls)
            assertEquals(listOf<PlaybackEvent>(PlaybackEvent.Reload(MASTER_2)), events)
            assertEquals(listOf(MASTER, MEDIA, MASTER_2), rig.routes.urlsVia(via(A)))
            assertEquals(via(A), session.route.value)
            assertEquals(ProxyStatus.Active(A.label), session.proxyStatus.value)
            assertEquals(Outcome.Success(live), session.fetch(MEDIA))
            session.close()
        }

    @Test
    fun repeated403DoesNotLoop() =
        runTest {
            val rig = Rig(this)
            val stream = rig.resolve(PlaybackPrefs())
            val session = stream.session
            val events = record(session.events)
            rig.routes.reply = { _, _ -> Reply.Status(403) }
            repeat(60) {
                assertEquals(Outcome.Failure(LurkError.Http(403)), session.fetch(MEDIA))
                delay(2.seconds)
            }
            val reGrants = rig.source.calls - 1
            assertEquals(4, reGrants, "one re-resolve per 30 s over 2 minutes")
            // The initial master, one request per fetch, and one master per re-resolve: no retry loop.
            assertEquals(1 + 60 + reGrants, rig.routes.requests.size)
            assertEquals(emptyList(), events)
            session.close()
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
    fun kickIsNeverInspectedOrProxied() =
        runTest {
            val rig = Rig(this, FakePlaybackSource(Platform.KICK, AdPolicy.UNTOUCHED))
            rig.routes.media = preroll
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A), channel = KICK)
            val session = stream.session
            val states = record(session.adState)
            repeat(150) {
                val text = assertIs<Outcome.Success<String>>(session.fetch(MEDIA)).value
                assertTrue(text.encodeToByteArray().contentEquals(preroll.encodeToByteArray()))
                delay(2.seconds)
            }
            assertEquals(5.minutes.inWholeMilliseconds, currentTime)
            assertEquals(listOf<AdState>(AdState.None), states)
            assertEquals(ProxyStatus.NotUsed, session.proxyStatus.value)
            assertEquals(Route.Direct, session.route.value)
            assertTrue(rig.routes.requests.all { it.route == Route.Direct })
            assertNull(session.typicalSegment.value)
            session.close()
        }

    @Test
    fun closeStopsTicker() =
        runTest {
            val rig = Rig(this)
            val scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A), scope = scope)
            rig.routes.media = cue
            stream.session.fetch(MEDIA)
            runCurrent()
            assertIs<AdState.Avoided>(stream.adState.value)
            // caller scope -> session job -> tracker job -> ticker: the countdown is running.
            assertEquals(1, scope.activeAt(depth = 3), "the ticker runs while the cue lasts")
            val states = record(stream.adState)

            stream.session.close()
            runCurrent()
            assertEquals(0, scope.activeAt(depth = 3), "the ticker is cancelled")
            assertEquals(0, scope.activeChildren())
            val seen = states.size
            advanceTimeBy(2.minutes)
            assertEquals(seen, states.size, "nothing ticks after close")
            assertEquals(AdState.None, stream.adState.value)
            assertTrue(scope.isActive, "the caller's scope is not cancelled")
        }

    @Test
    fun concurrentProxyFailuresFailOverOnce() =
        runTest {
            val rig = Rig(this)
            val gate = CompletableDeferred<Unit>()
            var down = false
            rig.routes.reply = { route, url ->
                if (down && route == via(A)) {
                    gate.await()
                    Reply.Offline
                } else {
                    Reply.Text(rig.routes.serve(url))
                }
            }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A, B, C))
            val session = stream.session
            val statuses = record(session.proxyStatus)
            down = true
            val fetches = listOf(MEDIA, MEDIA_480).map { url -> async { session.fetch(url) } }
            runCurrent()
            assertEquals(listOf(MASTER, MEDIA, MEDIA_480), rig.routes.urlsVia(via(A)))
            gate.complete(Unit)
            assertEquals(listOf(Outcome.Success(live), Outcome.Success(live)), fetches.awaitAll())
            assertEquals(via(B), session.route.value)
            assertEquals(setOf(MEDIA, MEDIA_480), rig.routes.urlsVia(via(B)).toSet())
            assertEquals(2, rig.routes.urlsVia(via(B)).size)
            assertEquals(emptyList(), rig.routes.urlsVia(via(C)))
            assertEquals(listOf<ProxyStatus>(ProxyStatus.Active(A.label), ProxyStatus.Active(B.label)), statuses)
            session.close()
        }

    @Test
    fun concurrent403sReResolveOnce() =
        runTest {
            val rig = Rig(this)
            val gate = CompletableDeferred<Unit>()
            rig.routes.reply = { _, url ->
                if (url.startsWith("https://video-weaver.example/")) {
                    gate.await()
                    Reply.Status(403)
                } else {
                    Reply.Text(rig.routes.master)
                }
            }
            val stream = rig.resolve(PlaybackPrefs())
            val events = record(stream.session.events)
            val fetches = listOf(MEDIA, MEDIA_480).map { url -> async { stream.session.fetch(url) } }
            runCurrent()
            gate.complete(Unit)
            fetches.awaitAll().forEach { assertEquals(Outcome.Failure(LurkError.Http(403)), it) }
            assertEquals(2, rig.source.calls)
            assertEquals(listOf<PlaybackEvent>(PlaybackEvent.Reload(MASTER_2)), events)
            stream.session.close()
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

    private fun CoroutineScope.activeChildren(): Int = coroutineContext.job.children.count { it.isActive }

    /** Active jobs [depth] levels below this scope's job. */
    private fun CoroutineScope.activeAt(depth: Int): Int {
        var level = sequenceOf(coroutineContext.job)
        repeat(depth) { level = level.flatMap { it.children } }
        return level.count { it.isActive }
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
    fun reResolveToAdFreeGrantSkipsProxy() =
        runTest {
            val rig = Rig(this)
            rig.source.next = { call ->
                Outcome.Success(
                    if (call == 0) {
                        PlaybackGrant(MASTER)
                    } else {
                        PlaybackGrant(
                            MASTER_2,
                            tokenFlags = TokenFlags(subscriber = true, turbo = false, showAds = true),
                        )
                    },
                )
            }
            var forbidden = 0
            rig.script { route, url ->
                if (route == via(A) && url == MEDIA &&
                    forbidden++ == 0
                ) {
                    Reply.Status(403)
                } else {
                    null
                }
            }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A))
            val session = stream.session
            val events = record(session.events)
            assertEquals(Outcome.Failure(LurkError.Http(403)), session.fetch(MEDIA))
            assertEquals(listOf<PlaybackEvent>(PlaybackEvent.Reload(MASTER_2)), events)
            assertEquals(Route.Direct, session.route.value)
            assertEquals(ProxyStatus.SkippedAdFree, session.proxyStatus.value)
            assertEquals(listOf(MASTER_2), rig.routes.urlsVia(Route.Direct))
            session.fetch(MEDIA)
            assertEquals(listOf(MASTER_2, MEDIA), rig.routes.urlsVia(Route.Direct))
            session.close()
        }

    @Test
    fun templateMasterFailureReopensOnNextProxy() =
        runTest {
            val rig = Rig(this)
            var templateDown = false
            rig.script { route, _ -> if (templateDown && route == via(TEMPLATE)) Reply.Offline else null }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, TEMPLATE, B))
            val session = stream.session
            val events = record(session.events)
            assertEquals(via(TEMPLATE), session.route.value)
            templateDown = true
            assertEquals(Outcome.Success(rig.routes.master), session.fetch(stream.masterUrl))
            assertEquals(listOf<PlaybackEvent>(PlaybackEvent.Reload(MASTER)), events)
            assertEquals(via(B), session.route.value)
            assertEquals(ProxyStatus.Active(B.label), session.proxyStatus.value)
            // B carries the source's own master, never the template URL.
            assertEquals(listOf(MASTER), rig.routes.urlsVia(via(B)))
            assertEquals(emptyList(), rig.routes.urlsVia(Route.Direct))
            // A stale request for the old template master afterwards is not sent through B either.
            session.fetch(stream.masterUrl)
            assertEquals(listOf(MASTER), rig.routes.urlsVia(via(B)))
            assertNoGrantHeadersToTemplateHost(rig)
            session.close()
        }

    @Test
    fun templateMasterFailureWithNoProxyLeftGoesDirectVisibly() =
        runTest {
            val rig = Rig(this)
            var templateDown = false
            rig.script { route, _ -> if (templateDown && route == via(TEMPLATE)) Reply.Offline else null }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, TEMPLATE))
            val session = stream.session
            val events = record(session.events)
            templateDown = true
            assertEquals(Outcome.Success(rig.routes.master), session.fetch(stream.masterUrl))
            assertEquals(listOf<PlaybackEvent>(PlaybackEvent.Reload(MASTER)), events)
            assertEquals(Route.Direct, session.route.value)
            assertEquals(ProxyStatus.Unavailable, session.proxyStatus.value)
            assertEquals(listOf(MASTER), rig.routes.urlsVia(Route.Direct))
            assertEquals(
                "1",
                rig.routes.requests
                    .last()
                    .headers["X-Test-Header"],
            )
            assertNoGrantHeadersToTemplateHost(rig)
            session.close()
        }

    @Test
    fun templateRelativeMediaGetsNoGrantHeaders() =
        runTest {
            val rig = Rig(this)
            val relativeMaster = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000,RESOLUTION=1280x720\nv1/720p.m3u8\n"
            rig.routes.reply = { _, url -> Reply.Text(if (url.contains("/v1/")) live else relativeMaster) }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, TEMPLATE))
            val mediaUrl = stream.variants.single().url
            assertTrue(mediaUrl.startsWith("https://lol.example/"), mediaUrl)
            assertEquals(Outcome.Success(live), stream.session.fetch(mediaUrl))
            assertEquals(listOf(mediaUrl), rig.routes.urlsVia(Route.Direct))
            assertNoGrantHeadersToTemplateHost(rig)
            stream.session.close()
        }

    private fun assertNoGrantHeadersToTemplateHost(rig: Rig) {
        val toTemplate = rig.routes.requests.filter { it.url.startsWith("https://lol.example/") }
        assertTrue(toTemplate.isNotEmpty())
        toTemplate.forEach { assertNull(it.headers["X-Test-Header"], it.url) }
    }

    private companion object {
        val TEMPLATE = ProxyEndpoint.UrlTemplate("https://lol.example/live/{channel}?x=1")
    }
}
