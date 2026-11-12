package io.github.kemurixd.lurk.platform.kick.api

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.network.BrowserProfile
import io.github.kemurixd.lurk.core.network.ChallengeKind
import io.github.kemurixd.lurk.core.network.ChallengeResult
import io.github.kemurixd.lurk.platform.kick.fixture
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class KickApiTest {
    @Test
    fun sendsBrowserAndPlatformHeaders() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val rig =
                Rig(
                    engine =
                        mockEngine {
                            seen += it
                            json(fixture("api/channel-live.json"))
                        },
                )
            val result = rig.api.get(KickBase.API, "v2/channels/streamer1", token = null, query = mapOf("a" to "b"))

            val body = assertIs<Outcome.Success<JsonElement>>(result).value
            assertEquals("streamer1", body.jsonObject["slug"]!!.jsonPrimitive.content)
            val request = seen.single()
            assertEquals("https://kick.example/api/v2/channels/streamer1?a=b", request.url.toString())
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("application/json", request.headers[HttpHeaders.Accept])
            assertEquals("web", request.headers["x-app-platform"])
            assertEquals(BrowserProfile.USER_AGENT, request.headers[HttpHeaders.UserAgent])
            assertEquals(BrowserProfile.headers["sec-ch-ua"], request.headers["sec-ch-ua"])
        }

    @Test
    fun webBaseUsesTheWebRoot() =
        runTest {
            var url = ""
            val rig =
                Rig(
                    engine =
                        mockEngine {
                            url = it.url.toString()
                            json("{}")
                        },
                )
            rig.api.get(KickBase.WEB, "/livestreams", token = null)
            assertEquals("https://web.kick.example/api/v1/livestreams", url)
        }

    @Test
    fun bearerOnlyWhenSignedIn() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val rig =
                Rig(
                    engine =
                        mockEngine {
                            seen += it
                            json("{}")
                        },
                )
            rig.api.get(KickBase.API, "v2/x", token = "tok-1")
            rig.api.get(KickBase.API, "v2/x", token = null)

            assertEquals("Bearer tok-1", seen[0].headers[HttpHeaders.Authorization])
            assertNull(seen[1].headers[HttpHeaders.Authorization])
        }

    @Test
    fun emptyBodyIsNull() =
        runTest {
            val rig = Rig(engine = mockEngine { respond("", HttpStatusCode.NoContent) })
            val result = rig.api.send(HttpMethod.Delete, KickBase.API, "v2/x", token = "t", body = null)
            assertEquals(Outcome.Success(null), result)
        }

    @Test
    fun sendPostsTheJsonBody() =
        runTest {
            var sent: HttpRequestData? = null
            val rig =
                Rig(
                    engine =
                        mockEngine {
                            sent = it
                            json("""{"ok":true}""")
                        },
                )
            val payload = buildJsonObject { put("content", "hi") }
            rig.api.send(HttpMethod.Post, KickBase.API, "v2/x", token = "t", body = payload)

            val request = sent!!
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("""{"content":"hi"}""", (request.body as TextContent).text)
            assertEquals(
                "application/json",
                request.body.contentType
                    ?.withoutParameters()
                    ?.toString(),
            )
        }

    @Test
    fun malformedBodyIsSchemaChanged() =
        runTest {
            val rig = Rig(engine = mockEngine { json("{nope") })
            assertEquals(Outcome.Failure(LurkError.SchemaChanged), rig.api.get(KickBase.API, "v2/x", null))
        }

    @Test
    fun unauthorizedIsAuthExpired() =
        runTest {
            val rig = Rig(engine = mockEngine { json("{}", HttpStatusCode.Unauthorized) })
            assertEquals(Outcome.Failure(LurkError.AuthExpired), rig.api.get(KickBase.API, "v2/x", "t"))
        }

    @Test
    fun challengeSolvedRetriesOnceWithCookies() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val solves = mutableListOf<Pair<ChallengeKind, String>>()
            val rig =
                Rig(
                    solver =
                        { kind, url ->
                            solves += kind to url
                            ChallengeResult.Solved(mapOf("cf_clearance" to "c1"), userAgent = null)
                        },
                    engine =
                        mockEngine {
                            seen += it
                            if (it.headers[HttpHeaders.Cookie].orEmpty().contains("cf_clearance=c1")) {
                                json(fixture("api/channel-live.json"))
                            } else {
                                cloudflareBlock(fixture("api/challenge.html"))
                            }
                        },
                )
            val result = rig.api.get(KickBase.API, "v2/channels/streamer1", token = null)

            assertIs<Outcome.Success<JsonElement>>(result)
            assertEquals(2, seen.size)
            assertEquals(listOf(ChallengeKind.CLOUDFLARE to "https://kick.example/api/v2/channels/streamer1"), solves)
        }

    @Test
    fun stillBlockedAfterRetryGivesUp() =
        runTest {
            var calls = 0
            val rig =
                Rig(
                    solver = { _, _ -> ChallengeResult.Solved(mapOf("cf_clearance" to "c1"), null) },
                    engine =
                        mockEngine {
                            calls++
                            cloudflareBlock(fixture("api/challenge.html"))
                        },
                )
            assertEquals(Outcome.Failure(LurkError.Blocked), rig.api.get(KickBase.API, "v2/x", null))
            assertEquals(2, calls)
        }

    @Test
    fun apiToStringHasNoToken() =
        runTest {
            val rig = Rig(engine = mockEngine { json("{}", HttpStatusCode.Forbidden) })
            val failure = rig.api.get(KickBase.API, "v2/x", token = "secret-token")
            assertFalse(rig.api.toString().contains("secret-token"))
            assertFalse(failure.toString().contains("secret-token"))
        }
}
