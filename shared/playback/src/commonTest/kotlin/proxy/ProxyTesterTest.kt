package io.github.kemurixd.lurk.playback.proxy

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.playback.A
import io.github.kemurixd.lurk.playback.FakePlaybackSource
import io.github.kemurixd.lurk.playback.FakeRoutes
import io.github.kemurixd.lurk.playback.MARKERS
import io.github.kemurixd.lurk.playback.MASTER
import io.github.kemurixd.lurk.playback.ProxyEndpoint
import io.github.kemurixd.lurk.playback.Reply
import io.github.kemurixd.lurk.playback.RollType
import io.github.kemurixd.lurk.playback.TWITCH
import io.github.kemurixd.lurk.playback.ads.AdDetector
import io.github.kemurixd.lurk.playback.fixture
import io.github.kemurixd.lurk.playback.hls.MasterPlaylist
import io.github.kemurixd.lurk.playback.via
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ProxyTesterTest {
    private class Setup(
        test: TestScope,
        val source: FakePlaybackSource = FakePlaybackSource(),
    ) {
        val routes = FakeRoutes(StandardTestDispatcher(test.testScheduler))
        val tester = ProxyTester(source, routes, AdDetector { MARKERS }, test.testScheduler.timeSource)

        init {
            test.backgroundScope.coroutineContext[Job]!!.invokeOnCompletion { routes.close() }
        }
    }

    @Test
    fun passesWithCleanMedia() =
        runTest {
            val s = Setup(this)
            s.routes.reply = { _, url ->
                if (url == MASTER) delay(40.milliseconds)
                Reply.Text(s.routes.serve(url))
            }
            val result = s.tester.test(A, TWITCH)
            assertEquals(ProxyTestResult.Passed(40.milliseconds, mediaThroughProxy = true), result)
            val lowest = s.routes.requests.last()
            assertEquals(via(A), lowest.route)
            assertTrue(lowest.url.startsWith("https://video-weaver.example/"))
            assertTrue(s.routes.requests.all { it.route == via(A) })
        }

    @Test
    fun leakDetected() =
        runTest {
            val s = Setup(this)
            s.routes.media = fixture("twitch/media-stitched-preroll.m3u8")
            assertEquals(ProxyTestResult.AdsLeaked(RollType.PREROLL), s.tester.test(A, TWITCH))
        }

    @Test
    fun deadProxyUnreachable() =
        runTest {
            val s = Setup(this)
            s.routes.reply = { _, _ -> Reply.Offline }
            val result = s.tester.test(A, TWITCH)
            assertIs<ProxyTestResult.Unreachable>(result)
            assertEquals(LurkError.Offline, result.error)
        }

    @Test
    fun deadProxyOnMediaIsUnreachable() =
        runTest {
            val s = Setup(this)
            s.routes.reply = { _, url ->
                if (url.startsWith("https://video-weaver.example/")) Reply.Timeout else Reply.Text(s.routes.serve(url))
            }
            assertEquals(ProxyTestResult.Unreachable(LurkError.Timeout), s.tester.test(A, TWITCH))
        }

    @Test
    fun streamErrorFromMasterIsStreamFailed() =
        runTest {
            val s = Setup(this)
            s.routes.reply = { _, _ -> Reply.Status(404) }
            assertEquals(ProxyTestResult.StreamFailed(LurkError.NotFound), s.tester.test(A, TWITCH))
        }

    @Test
    fun templateReportsMediaNotProxied() =
        runTest {
            val s = Setup(this)
            val template = ProxyEndpoint.UrlTemplate("https://lol.example/live/{channel}?x=1")
            val result = s.tester.test(template, TWITCH)
            assertEquals(ProxyTestResult.Passed(0.milliseconds, mediaThroughProxy = false), result)
            val master = s.routes.requests.first()
            assertEquals(
                "https://lol.example/live/testchannel?x=1&allow_source=true&allow_audio_only=true" +
                    "&fast_bread=true&include_unavailable=true",
                master.url,
            )
            assertFalse(master.headers.containsKey("X-Test-Header"))
            val media = s.routes.requests.last()
            assertEquals(Route.Direct, media.route)
            assertEquals("1", media.headers["X-Test-Header"])
        }

    @Test
    fun offlineChannelIsStreamFailed() =
        runTest {
            val s = Setup(this)
            s.source.next = { Outcome.Failure(LurkError.NotFound) }
            assertEquals(ProxyTestResult.StreamFailed(LurkError.NotFound), s.tester.test(A, TWITCH))
            assertTrue(s.routes.requests.isEmpty())
        }

    @Test
    fun masterWithoutVariantsIsStreamFailed() =
        runTest {
            val s = Setup(this)
            s.routes.master = "#EXTM3U\n"
            assertEquals(ProxyTestResult.StreamFailed(LurkError.SchemaChanged), s.tester.test(A, TWITCH))
        }

    @Test
    fun picksLowestBandwidthVideoVariant() =
        runTest {
            val s = Setup(this)
            s.tester.test(A, TWITCH)
            val variants =
                MasterPlaylist
                    .variants(s.routes.master, MASTER)
                    .filter { !it.audioOnly }
            assertEquals(
                variants.minBy { it.bandwidth }.url,
                s.routes.requests
                    .last()
                    .url,
            )
        }

    @Test
    fun cueOnlyMediaPasses() =
        runTest {
            val s = Setup(this)
            s.routes.media = fixture("twitch/media-cue.m3u8")
            assertEquals(ProxyTestResult.Passed(0.milliseconds, mediaThroughProxy = true), s.tester.test(A, TWITCH))
        }

    @Test
    fun templateMediaTimeoutBlamesTheStreamNotTheProxy() =
        runTest {
            val s = Setup(this)
            s.routes.reply = { _, url ->
                if (url.startsWith("https://video-weaver.example/")) Reply.Timeout else Reply.Text(s.routes.serve(url))
            }
            val template = ProxyEndpoint.UrlTemplate("https://lol.example/live/{channel}")
            assertEquals(ProxyTestResult.StreamFailed(LurkError.Timeout), s.tester.test(template, TWITCH))
        }

    @Test
    fun templateMediaGetsSourceHeadersOffTheTemplateHost() =
        runTest {
            val s = Setup(this)
            val template = ProxyEndpoint.UrlTemplate("https://lol.example/live/{channel}")
            s.tester.test(template, TWITCH)
            assertEquals(
                "1",
                s.routes.requests
                    .last()
                    .headers["X-Test-Header"],
            )
        }

    @Test
    fun templateMediaOnTheTemplateHostGetsNoHeaders() =
        runTest {
            val s = Setup(this)
            s.routes.master =
                fixture("twitch/master.m3u8").replace("https://video-weaver.example/", "https://lol.example/")
            s.routes.reply =
                {
                    _,
                    url,
                    ->
                    Reply.Text(if (url.startsWith("https://lol.example/v1/")) s.routes.media else s.routes.master)
                }
            val template = ProxyEndpoint.UrlTemplate("https://lol.example/live/{channel}")
            assertIs<ProxyTestResult.Passed>(s.tester.test(template, TWITCH))
            val media = s.routes.requests.last()
            assertTrue(media.url.startsWith("https://lol.example/v1/"))
            assertFalse(media.headers.containsKey("X-Test-Header"))
        }

    @Test
    fun malformedTemplateIsUnreachableNotThrown() =
        runTest {
            val s = Setup(this)
            val template = ProxyEndpoint.UrlTemplate("https://lol.example:notaport/live/{channel}")
            assertEquals(ProxyTestResult.Unreachable(LurkError.ProxyFailed), s.tester.test(template, TWITCH))
        }
}
