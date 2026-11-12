package io.github.kemurixd.lurk

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartupScopeTest {
    @Test
    fun uncaughtFailureIsReportedAndDoesNotCancelTheScope() =
        runBlocking {
            val reported = CompletableDeferred<Throwable>()
            val scope = startupScope(onFailure = { reported.complete(it) })

            scope.launch { error("unexpected") }
            assertEquals("unexpected", withTimeout(TIMEOUT_MS) { reported.await() }.message)

            val survivor = CompletableDeferred<Unit>()
            scope.launch { survivor.complete(Unit) }
            withTimeout(TIMEOUT_MS) { survivor.await() }
            assertTrue(survivor.isCompleted)
        }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
