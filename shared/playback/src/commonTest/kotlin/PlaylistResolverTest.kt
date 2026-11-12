package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.playback.proxy.Route
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistResolverTest {
    @Test
    fun noSourceForPlatformIsNotFound() =
        runTest {
            val rig = Rig(this)
            val result = rig.resolver.resolve(KICK, PlaybackPrefs(), backgroundScope)
            assertEquals(Outcome.Failure(LurkError.NotFound), result)
            assertEquals(0, rig.source.calls)
        }

    @Test
    fun grantFailureIsReturnedAsIs() =
        runTest {
            val rig = Rig(this)
            rig.source.next = { Outcome.Failure(LurkError.IntegrityRequired) }
            val result = rig.resolver.resolve(TWITCH, prefs(ProxyMode.ALWAYS, A), backgroundScope)
            assertEquals(Outcome.Failure(LurkError.IntegrityRequired), result)
            assertEquals(emptyList(), rig.routes.requests)
        }

    @Test
    fun tokenIsAlwaysDirect() =
        runTest {
            val rig = Rig(this)
            var requestsBeforeGrant = -1
            rig.source.onGrant = { requestsBeforeGrant = rig.routes.requests.size }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A))
            repeat(3) { stream.session.fetch(MEDIA) }
            assertEquals(1, rig.source.calls)
            // The grant is taken before any playlist request and never goes through a route client.
            assertEquals(0, requestsBeforeGrant)
            assertEquals(listOf(MASTER, MEDIA, MEDIA, MEDIA), rig.routes.urlsVia(via(A)))
            assertEquals(emptyList(), rig.routes.urlsVia(Route.Direct))
            stream.session.close()
        }

    @Test
    fun adFreeSkipsProxy() =
        runTest {
            val rig = Rig(this)
            rig.source.next = {
                Outcome.Success(
                    PlaybackGrant(MASTER, tokenFlags = TokenFlags(subscriber = true, turbo = false, showAds = true)),
                )
            }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A))
            assertEquals(Route.Direct, stream.session.route.value)
            assertEquals(ProxyStatus.SkippedAdFree, stream.session.proxyStatus.value)

            rig.routes.media = fixture("twitch/media-cue.m3u8")
            stream.session.fetch(MEDIA)
            runCurrent()
            val state = assertIs<AdState.Avoided>(stream.adState.value)
            assertFalse(state.viaProxy)
            assertTrue(rig.routes.requests.all { it.route == Route.Direct })
            stream.session.close()
        }

    @Test
    fun offOrEmptyListIsDirect() =
        runTest {
            val cases =
                listOf(
                    prefs(ProxyMode.OFF, A, B),
                    prefs(ProxyMode.ALWAYS),
                    prefs(ProxyMode.WHEN_ADS_DETECTED),
                    PlaybackPrefs(),
                )
            for (p in cases) {
                val rig = Rig(this)
                val stream = rig.resolve(p)
                stream.session.fetch(MEDIA)
                assertEquals(Route.Direct, stream.session.route.value, "$p")
                assertEquals(ProxyStatus.NotUsed, stream.session.proxyStatus.value, "$p")
                assertEquals(listOf(MASTER, MEDIA), rig.routes.urlsVia(Route.Direct), "$p")
                assertEquals(2, rig.routes.requests.size, "$p")
                stream.session.close()
            }
        }

    @Test
    fun firstProxyDeadSecondUsed() =
        runTest {
            val rig = Rig(this)
            rig.routes.reply =
                { route, url -> if (route == via(A)) Reply.Timeout else Reply.Text(rig.routes.serve(url)) }
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, A, B))
            assertEquals(ProxyStatus.Active(B.label), stream.session.proxyStatus.value)
            assertEquals(via(B), stream.session.route.value)
            assertEquals(MASTER, stream.masterUrl)
            assertEquals(7, stream.variants.size)
            assertEquals("1080p60", stream.variants.first().name)
            assertEquals(listOf(MASTER), rig.routes.urlsVia(via(A)))
            assertEquals(listOf(MASTER), rig.routes.urlsVia(via(B)))
            assertEquals(emptyList(), rig.routes.urlsVia(Route.Direct))
            stream.session.close()
        }

    @Test
    fun streamErrorNotRetriedOnProxies() =
        runTest {
            val rig = Rig(this)
            rig.routes.reply = { _, _ -> Reply.Status(404) }
            val result = rig.resolver.resolve(TWITCH, prefs(ProxyMode.ALWAYS, A, B), backgroundScope)
            assertEquals(Outcome.Failure(LurkError.NotFound), result)
            assertEquals(listOf(MASTER), rig.routes.urlsVia(via(A)))
            assertEquals(1, rig.routes.requests.size)
        }

    @Test
    fun templateRewritesMasterOnly() =
        runTest {
            val rig = Rig(this)
            val template = ProxyEndpoint.UrlTemplate("https://lol.example/live/{channel}?x=1")
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, template))
            val expected =
                "https://lol.example/live/testchannel?x=1" +
                    "&allow_source=true&allow_audio_only=true&fast_bread=true&include_unavailable=true"
            assertEquals(expected, stream.masterUrl)
            assertEquals(ProxyStatus.Active("lol.example"), stream.session.proxyStatus.value)
            assertEquals(via(template), stream.session.route.value)
            repeat(3) { stream.session.fetch(MEDIA) }
            assertEquals(listOf(expected), rig.routes.urlsVia(via(template)))
            assertEquals(listOf(MEDIA, MEDIA, MEDIA), rig.routes.urlsVia(Route.Direct))
            // The template service is a third party: the source's headers are not sent to it.
            val masterRequest = rig.routes.requests.first()
            assertFalse(FakePlaybackSource.HEADERS.keys.any { it in masterRequest.headers })
            assertTrue(
                rig.routes.requests
                    .drop(1)
                    .all { it.headers["X-Test-Header"] == "1" },
            )
            stream.session.close()

            val own = Rig(this)
            val keeps = ProxyEndpoint.UrlTemplate("https://lol.example/live/{channel}?allow_source=false")
            val second = own.resolve(prefs(ProxyMode.ALWAYS, keeps))
            assertEquals(
                "https://lol.example/live/testchannel?allow_source=false" +
                    "&allow_audio_only=true&fast_bread=true&include_unavailable=true",
                second.masterUrl,
            )
            second.session.close()

            val bare = Rig(this)
            val noQuery = ProxyEndpoint.UrlTemplate("https://lol.example/live/\$channel.m3u8")
            val third = bare.resolve(prefs(ProxyMode.ALWAYS, noQuery))
            assertEquals(
                "https://lol.example/live/testchannel.m3u8" +
                    "?allow_source=true&allow_audio_only=true&fast_bread=true&include_unavailable=true",
                third.masterUrl,
            )
            third.session.close()
        }

    @Test
    fun lowLatencyAndHeadersComeFromTheGrant() =
        runTest {
            val rig = Rig(this)
            rig.source.next = {
                Outcome.Success(PlaybackGrant(MASTER, headers = FakePlaybackSource.HEADERS, lowLatencyCapable = true))
            }
            val stream = rig.resolve(PlaybackPrefs())
            assertTrue(stream.lowLatencyCapable)
            assertEquals(FakePlaybackSource.HEADERS, stream.headers)
            assertEquals(TWITCH, stream.channel)
            assertEquals(
                "1",
                rig.routes.requests
                    .single()
                    .headers["X-Test-Header"],
            )
            stream.session.close()
        }

    @Test
    fun resolvedStreamToStringHidesHeadersAndQuery() =
        runTest {
            val rig = Rig(this)
            rig.source.next = {
                Outcome.Success(
                    PlaybackGrant(
                        "$MASTER?sig=sigvalue1&token=tokenvalue1",
                        headers =
                            mapOf("Authorization" to "OAuth headervalue1"),
                    ),
                )
            }
            val stream = rig.resolve(PlaybackPrefs())
            val text = stream.toString()
            listOf("sigvalue1", "tokenvalue1", "headervalue1", "Authorization").forEach {
                assertFalse(it in text, "$it leaked: $text")
            }
            assertTrue("masterUrl=https://usher.example," in text, text)
            assertFalse("/api/" in text, text)
            stream.session.close()
        }

    @Test
    fun resolvedStreamToStringHidesTheTemplatePath() =
        runTest {
            val rig = Rig(this)
            val template = ProxyEndpoint.UrlTemplate("https://lol.example/keyvalue1/live/{channel}")
            val stream = rig.resolve(prefs(ProxyMode.ALWAYS, template))
            val text = stream.toString()
            listOf("keyvalue1", "/live/").forEach { assertFalse(it in text, "$it leaked: $text") }
            assertTrue("masterUrl=https://lol.example," in text, text)
            stream.session.close()
        }
}
