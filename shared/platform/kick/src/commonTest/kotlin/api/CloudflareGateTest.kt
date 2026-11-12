package io.github.kemurixd.lurk.platform.kick.api

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.network.ChallengeResult
import io.github.kemurixd.lurk.platform.kick.fixture
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudflareGateTest {
    private val blockedPage get() = fixture("api/challenge.html")

    private fun MockRequestHandleScope.reply(request: HttpRequestData) =
        if (request.headers[HttpHeaders.Cookie].orEmpty().contains("cf_clearance=c1")) {
            json("""{"ok":true}""")
        } else {
            cloudflareBlock(blockedPage)
        }

    @Test
    fun concurrentChallengesSolveOnce() =
        runTest {
            var solves = 0
            val rig =
                Rig(
                    solver =
                        { _, _ ->
                            solves++
                            delay(1_000)
                            ChallengeResult.Solved(mapOf("cf_clearance" to "c1"), null)
                        },
                    engine = mockEngine { reply(it) },
                )
            val results = (1..5).map { async { rig.api.get(KickBase.API, "v2/x", null) } }.awaitAll()

            assertEquals(1, solves)
            assertTrue(results.all { it is Outcome.Success }, results.toString())
        }

    @Test
    fun solvedCookiesAreStoredForTheHost() =
        runTest {
            val rig =
                Rig(
                    solver = { _, _ -> ChallengeResult.Solved(mapOf("cf_clearance" to "c1"), null) },
                    engine = mockEngine { reply(it) },
                )
            rig.api.get(KickBase.API, "v2/x", null)
            val stored = rig.cookies.get(Url("https://kick.example/api/v2/x"))
            assertEquals(listOf("c1"), stored.filter { it.name == "cf_clearance" }.map { it.value })
        }

    @Test
    fun noSolverIsBlocked() =
        runTest {
            var calls = 0
            val rig =
                Rig(
                    engine =
                        mockEngine {
                            calls++
                            cloudflareBlock(blockedPage)
                        },
                )
            assertEquals(Outcome.Failure(LurkError.Blocked), rig.api.get(KickBase.API, "v2/x", null))
            assertEquals(1, calls)
        }

    @Test
    fun needsUserIsBlocked() =
        runTest {
            var calls = 0
            val rig =
                Rig(
                    solver = { _, _ -> ChallengeResult.NeedsUser },
                    engine =
                        mockEngine {
                            calls++
                            cloudflareBlock(blockedPage)
                        },
                )
            assertEquals(Outcome.Failure(LurkError.Blocked), rig.api.get(KickBase.API, "v2/x", null))
            assertEquals(1, calls)
        }

    @Test
    fun failedSolveIsBlocked() =
        runTest {
            val rig =
                Rig(
                    solver = { _, _ -> ChallengeResult.Failed },
                    engine = mockEngine { cloudflareBlock(blockedPage) },
                )
            assertEquals(Outcome.Failure(LurkError.Blocked), rig.api.get(KickBase.API, "v2/x", null))
        }

    @Test
    fun otherFailuresSkipTheSolver() =
        runTest {
            var solves = 0
            val rig =
                Rig(
                    solver =
                        { _, _ ->
                            solves++
                            ChallengeResult.Failed
                        },
                    engine = mockEngine { respond("", HttpStatusCode.NotFound) },
                )
            assertEquals(Outcome.Failure(LurkError.NotFound), rig.api.get(KickBase.API, "v2/x", null))
            assertEquals(0, solves)
        }

    @Test
    fun concurrentApiAndWebBlocksSolveOnce() =
        runTest {
            var solves = 0
            val rig =
                Rig(
                    solver =
                        { _, _ ->
                            solves++
                            delay(1_000)
                            ChallengeResult.Solved(mapOf("cf_clearance" to "c1"), null)
                        },
                    engine = mockEngine { reply(it) },
                )
            val results =
                listOf(
                    async { rig.api.get(KickBase.WEB, "livestreams", null) },
                    async { rig.api.get(KickBase.API, "v2/x", null) },
                    async { rig.api.get(KickBase.WEB, "livestreams", null) },
                ).awaitAll()

            assertEquals(1, solves)
            assertTrue(results.all { it is Outcome.Success }, results.toString())
        }

    @Test
    fun throwingSolverIsBlocked() =
        runTest {
            var solves = 0
            val rig =
                Rig(
                    solver =
                        { _, _ ->
                            solves++
                            error("boom")
                        },
                    engine = mockEngine { cloudflareBlock(blockedPage) },
                )
            val results = (1..3).map { async { rig.api.get(KickBase.API, "v2/x", null) } }.awaitAll()

            assertTrue(results.all { it == Outcome.Failure(LurkError.Blocked) }, results.toString())
            assertEquals(1, solves)
        }
}
