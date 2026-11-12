package io.github.kemurixd.lurk.playback.proxy

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.playback.ProxyEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

class ProxyChainTest {
    private val time = TestTimeSource()
    private val health = ProxyHealth(time)
    private val chain = ProxyChain(health, time)
    private val a: ProxyEndpoint = ProxyEndpoint.Http("127.0.0.1", 8080)
    private val b: ProxyEndpoint = ProxyEndpoint.Http("127.0.0.1", 8081)
    private val c: ProxyEndpoint = ProxyEndpoint.Http("127.0.0.1", 8082)
    private val calls = mutableListOf<ProxyEndpoint>()

    private fun fail(error: LurkError) = Outcome.Failure(error)

    private suspend fun run(
        endpoints: List<ProxyEndpoint>,
        skip: Set<ProxyEndpoint> = emptySet(),
        script: (ProxyEndpoint) -> Outcome<String>,
    ): ChainResult<String> =
        chain.first(endpoints, skip) {
            calls += it
            script(it)
        }

    @Test
    fun triesInOrder() =
        runTest {
            val result =
                chain.first<String>(listOf(a, b)) {
                    calls += it
                    if (it == a) {
                        fail(LurkError.Timeout)
                    } else {
                        time += 120.milliseconds
                        Outcome.Success("ok")
                    }
                }
            assertEquals<ChainResult<String>>(ChainResult.Success("ok", b), result)
            assertEquals(listOf(a, b), calls)
            assertFalse(health.isHealthy(a))
            assertEquals(120.milliseconds, health.stats(b).lastLatency)
            assertTrue(health.isHealthy(b))
        }

    @Test
    fun unhealthySkipped() =
        runTest {
            run(listOf(a, b)) { if (it == a) fail(LurkError.Timeout) else Outcome.Success("ok") }
            calls.clear()
            val second = run(listOf(a, b)) { Outcome.Success("ok") }
            assertEquals<ChainResult<String>>(ChainResult.Success("ok", b), second)
            assertEquals(listOf(b), calls)

            time += 2.minutes
            calls.clear()
            run(listOf(a, b)) { Outcome.Success("ok") }
            assertEquals(listOf(a), calls)
        }

    @Test
    fun streamErrorsDoNotBlameProxy() =
        runTest {
            val result = run(listOf(a, b)) { fail(LurkError.Http(403)) }
            assertEquals<ChainResult<String>>(ChainResult.AllFailed(LurkError.Http(403)), result)
            assertTrue(health.isHealthy(a))
            assertEquals(listOf(a), calls)
        }

    @Test
    fun notFoundIsAStreamError() =
        runTest {
            val result = run(listOf(a, b)) { fail(LurkError.NotFound) }
            assertEquals<ChainResult<String>>(ChainResult.AllFailed(LurkError.NotFound), result)
            assertTrue(health.isHealthy(a))
            assertEquals(listOf(a), calls)
        }

    @Test
    fun proxyErrorsAreRecordedAsFailures() =
        runTest {
            val errors =
                listOf(
                    LurkError.Offline,
                    LurkError.Timeout,
                    LurkError.ProxyFailed,
                    LurkError.Http(407),
                    LurkError.Http(500),
                    LurkError.Http(503),
                    LurkError.Http(599),
                    LurkError.Blocked,
                )
            for (error in errors) {
                val fresh = ProxyHealth(time)
                val result = ProxyChain(fresh, time).first(listOf(a)) { fail(error) }
                assertEquals<ChainResult<String>>(ChainResult.AllFailed(error), result, "$error")
                assertFalse(fresh.isHealthy(a), "$error")
            }
        }

    @Test
    fun otherHttpCodesAreStreamErrors() =
        runTest {
            for (code in listOf(400, 404, 429, 499, 600)) {
                val fresh = ProxyHealth(time)
                val result = ProxyChain(fresh, time).first(listOf(a, b)) { fail(LurkError.Http(code)) }
                assertEquals<ChainResult<String>>(ChainResult.AllFailed(LurkError.Http(code)), result, "$code")
                assertTrue(fresh.isHealthy(a), "$code")
            }
        }

    @Test
    fun allFailedCarriesLastProxyError() =
        runTest {
            val result = run(listOf(a, b)) { if (it == a) fail(LurkError.Timeout) else fail(LurkError.ProxyFailed) }
            assertEquals<ChainResult<String>>(ChainResult.AllFailed(LurkError.ProxyFailed), result)
            assertEquals(listOf(a, b), calls)
        }

    @Test
    fun allUnhealthyStillTried() =
        runTest {
            health.recordFailure(a)
            health.recordFailure(b)
            val result = run(listOf(a, b)) { if (it == a) fail(LurkError.Timeout) else Outcome.Success("ok") }
            assertEquals<ChainResult<String>>(ChainResult.Success("ok", b), result)
            assertEquals(listOf(a, b), calls)
        }

    @Test
    fun skipHonoured() =
        runTest {
            val result = run(listOf(a, b, c), skip = setOf(a)) { Outcome.Success("ok") }
            assertEquals<ChainResult<String>>(ChainResult.Success("ok", b), result)
            assertEquals(listOf(b), calls)
        }

    @Test
    fun skipHonouredWhenAllUnhealthy() =
        runTest {
            health.recordFailure(a)
            health.recordFailure(b)
            run(listOf(a, b), skip = setOf(a)) { Outcome.Success("ok") }
            assertEquals(listOf(b), calls)
        }

    @Test
    fun nothingLeftToTryGivesAllFailedWithoutError() =
        runTest {
            assertEquals<ChainResult<String>>(ChainResult.AllFailed(null), run(emptyList()) { Outcome.Success("ok") })
            assertEquals<ChainResult<String>>(
                ChainResult.AllFailed(null),
                run(listOf(a), skip = setOf(a)) {
                    Outcome.Success("ok")
                },
            )
            assertTrue(calls.isEmpty())
        }

    @Test
    fun cancellationPropagatesWithoutBlamingProxy() =
        runTest {
            assertFailsWith<CancellationException> {
                chain.first<String>(listOf(a)) { throw CancellationException("stop") }
            }
            assertTrue(health.isHealthy(a))
        }

    @Test
    fun otherExceptionsCountAsProxyFailure() =
        runTest {
            val result =
                chain.first<String>(listOf(a, b)) {
                    calls += it
                    if (it == a) error("boom") else Outcome.Success("ok")
                }
            assertEquals<ChainResult<String>>(ChainResult.Success("ok", b), result)
            assertFalse(health.isHealthy(a))
        }

    @Test
    fun successLeavesNoFailureRecorded() =
        runTest {
            run(listOf(a)) { Outcome.Success("ok") }
            assertNull(health.stats(a).lastFailure)
        }
}
