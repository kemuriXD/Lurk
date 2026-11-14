package io.github.kemurixd.lurk

import io.github.kemurixd.lurk.login.CookieJar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun leftoverLoginCookiesAreClearedWithoutHoldingTheRestore() =
        runBlocking {
            val clearStarted = CompletableDeferred<Unit>()
            val clearFinishes = CompletableDeferred<Unit>()
            val jar =
                object : CookieJar {
                    override fun read(url: String): String? = null

                    override suspend fun clearAll() {
                        clearStarted.complete(Unit)
                        clearFinishes.await()
                    }
                }
            val restored = CompletableDeferred<Unit>()
            val scope = startupScope()

            scope.startSessions(cookies = jar, restore = {}, onRestored = { restored.complete(Unit) })

            withTimeout(TIMEOUT_MS) { clearStarted.await() }
            withTimeout(TIMEOUT_MS) { restored.await() }
            assertFalse(clearFinishes.isCompleted)
            scope.cancel()
        }

    @Test
    fun leftoverLoginCookiesAreClearedOnlyAfterTheRestore() =
        runBlocking {
            val restoreFinishes = CompletableDeferred<Unit>()
            val clearStarted = CompletableDeferred<Unit>()
            val jar =
                object : CookieJar {
                    override fun read(url: String): String? = null

                    override suspend fun clearAll() {
                        clearStarted.complete(Unit)
                    }
                }
            val scope = startupScope()

            scope.startSessions(cookies = jar, restore = { restoreFinishes.await() }, onRestored = {})
            delay(SETTLE_MS)
            assertFalse(clearStarted.isCompleted)

            restoreFinishes.complete(Unit)
            withTimeout(TIMEOUT_MS) { clearStarted.await() }
            scope.cancel()
        }

    @Test
    fun aFailingRestoreStillReportsRestored() =
        runBlocking {
            val restored = CompletableDeferred<Unit>()
            val scope = startupScope(onFailure = {})

            scope.startSessions(
                cookies = NoCookies,
                restore = { error("restore failed") },
                onRestored = { restored.complete(Unit) },
            )

            withTimeout(TIMEOUT_MS) { restored.await() }
            scope.cancel()
        }

    private object NoCookies : CookieJar {
        override fun read(url: String): String? = null

        override suspend fun clearAll() = Unit
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 200L
    }
}
