package io.github.kemurixd.lurk.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class OutcomeTest {
    @Test
    fun successCarriesValue() =
        runTest {
            val outcome = outcomeOf { 42 }
            assertEquals(Outcome.Success(42), outcome)
            assertEquals(42, outcome.getOrNull())
            assertNull(outcome.errorOrNull())
        }

    @Test
    fun lurkExceptionBecomesFailure() =
        runTest {
            val outcome = outcomeOf<Int> { throw LurkException(LurkError.NotFound) }
            assertEquals(Outcome.Failure(LurkError.NotFound), outcome)
            assertEquals(LurkError.NotFound, outcome.errorOrNull())
            assertNull(outcome.getOrNull())
        }

    @Test
    fun serializationExceptionBecomesSchemaChanged() =
        runTest {
            val outcome = outcomeOf { Json.decodeFromString<Int>("\"x\"") }
            assertEquals(Outcome.Failure(LurkError.SchemaChanged), outcome)
        }

    @Test
    fun unexpectedExceptionIsRethrown() =
        runTest {
            assertFailsWith<IllegalStateException> { outcomeOf<Unit> { error("bug") } }
        }

    @Test
    fun cancellationIsRethrown() =
        runTest {
            assertFailsWith<CancellationException> { outcomeOf<Unit> { throw CancellationException("stop") } }
        }

    @Test
    fun mapTransformsSuccessAndKeepsFailure() {
        assertEquals(Outcome.Success(2), Outcome.Success(1).map { it + 1 })
        val failure: Outcome<Int> = Outcome.Failure(LurkError.Offline)
        assertEquals(Outcome.Failure(LurkError.Offline), failure.map { it + 1 })
    }

    @Test
    fun errorsCarryTheirDetails() {
        assertEquals(503, LurkError.Http(503).code)
        assertEquals(5.seconds, LurkError.RateLimited(5.seconds).retryAfter)
        assertEquals(LurkError.Timeout, LurkException(LurkError.Timeout).error)
    }
}
